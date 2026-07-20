package com.example.globe.core;

import java.util.Random;

/**
 * Phase 5 B-9 / S25(A) -- deterministic CARVER-START prediction for {@code /latdev locateCrevasse} /
 * {@code locateTunnel} (Peetsa 2026-07-20, TEST 117: "I still can't find any crevasses. Can we add a lat dev
 * locate command?"). Pure math, zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM); the
 * MC-coupled glue (raw-biome carver counts, the barrens-band + sea-probe gates, teleporting) lives in the
 * {@code LatitudeDevCommands} subcommand.
 *
 * <h2>Why the start decision is replayable WITHOUT loading chunks (26.2 bytecode-verified, 2026-07-20)</h2>
 * {@code NoiseBasedChunkGenerator.applyCarvers} decides "does seed chunk (x,z) START carver #i" with pure
 * seeded math, independent of any chunk content:
 * <pre>
 *   worldgenRandom.setLargeFeatureSeed(worldSeed + i, seedChunkX, seedChunkZ);   // i = index in the carver list
 *   if (configuredWorldCarver.isStartChunk(worldgenRandom)) { carve... }
 * </pre>
 * javap ground truth, each verified against the 26.2 merged jar:
 * <ul>
 *   <li>{@code WorldgenRandom.setLargeFeatureSeed(J I I)V}: {@code setSeed(seed); a=nextLong(); b=nextLong();
 *       setSeed(x*a ^ z*b ^ seed);} -- disassembly shows exactly this sequence.</li>
 *   <li>{@code CaveWorldCarver.isStartChunk} / {@code CanyonWorldCarver.isStartChunk} (the two carver types
 *       the B-9 pair uses -- {@code globe:glacial_tunnels} is {@code minecraft:cave},
 *       {@code globe:crevasse} is {@code minecraft:canyon}): {@code random.nextFloat() <= config.probability}
 *       ({@code fcmpg; ifgt} = true iff not greater).</li>
 *   <li>The {@code WorldgenRandom} in {@code applyCarvers} wraps a {@code LegacyRandomSource} (bytecode:
 *       {@code new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()))}; the unique
 *       initial seed is irrelevant -- {@code setLargeFeatureSeed} fully reseeds first). Every consumed op
 *       delegates to the wrapped source, and {@code LegacyRandomSource} is bit-for-bit
 *       {@link java.util.Random}: {@code setSeed = (s ^ 0x5DEECE66D) & (2^48-1)}, {@code next(bits) =
 *       (s*0x5DEECE66D + 0xB) >>> (48-bits)}, {@code nextLong = ((long)next(32) << 32) + next(32)},
 *       {@code nextFloat = next(24) * 2^-24} -- all four verified in the 26.2 disassembly. So a plain
 *       {@link java.util.Random} replays the roll exactly.</li>
 * </ul>
 *
 * <h2>The carver LIST INDEX (the subtle part)</h2>
 * The seed offset {@code i} is the carver's position in the list {@code applyCarvers} iterates for that seed
 * chunk -- the RAW biome's own carver list (resolved from the raw {@code biomeSource} field at the seed
 * chunk's min-corner quart, quart-Y 0) as filtered by {@code NoiseChunkGeneratorCarveMixin}/
 * {@code GlacialCarverLaw}. The B-9 pair is APPENDED after the raw list, so on modern (sized-key) worlds
 * {@code globe:crevasse} lands at index {@code rawCount} and {@code globe:glacial_tunnels} at
 * {@code rawCount + 1} -- and {@code rawCount} VARIES with the raw biome (vanilla overworld biomes carry 3
 * carvers; datapacked biomes can differ), so the caller must resolve it per candidate seed chunk. On LEGACY
 * ({@code stable(globe:overworld)}) worlds the legacy strip empties the raw list for every polar-cap center,
 * so the pair sit at indices 0/1 there (the caller passes the right base). The prediction must also replicate
 * the mixin's append GATES (flag, armed radius, barrens-band fray, sea probe) -- the caller owns those reads
 * and feeds this class only the final roll.
 *
 * <h2>Accuracy note (surfaced in the command output)</h2>
 * This predicts START chunks -- where a carver arc BEGINS. {@code applyCarvers} replays every seed chunk
 * within +-8 chunks of a center, so the carved arc extends up to {@link #CARVER_ARC_REACH_CHUNKS 8} chunks
 * from the start chunk, and the visible crevasse may be found anywhere along it (or pinched off by local
 * terrain/replaceable rules -- a start is necessary, not sufficient, for a visible opening at any one spot).
 */
public final class CrevasseLocator {

    /** Default spiral-search cap (chunks of Chebyshev radius) for the locate command -- 64 chunks = 1024
     *  blocks each way, a comfortable "is there one anywhere near me" sweep that still bounds the worst case
     *  (129x129 = 16,641 candidate chunks, each a few noise/biome samples). */
    public static final int DEFAULT_SEARCH_RADIUS_CHUNKS = 64;

    /** How far (chunks) a carver ARC can extend from its start chunk: vanilla's applyCarvers replays seed
     *  chunks over a +-8 window, so an arc reaches at most 8 chunks out. */
    public static final int CARVER_ARC_REACH_CHUNKS = 8;

    private CrevasseLocator() {
    }

    /**
     * The replayed vanilla start roll (see the class javadoc's bytecode ground truth): does the carver at
     * {@code carverListIndex} START in seed chunk ({@code chunkX}, {@code chunkZ})? Bit-exact replay of
     * {@code setLargeFeatureSeed(worldSeed + index, x, z)} + {@code nextFloat() <= probability} on
     * {@link java.util.Random} (= {@code LegacyRandomSource}). Deterministic; NaN/negative probabilities
     * return false (nextFloat is never NaN and never negative... a hostile probability just never matches),
     * probability >= 1 always starts.
     */
    public static boolean carverStartsAt(long worldSeed, int carverListIndex, int chunkX, int chunkZ,
                                         float probability) {
        long seed = worldSeed + (long) carverListIndex;
        Random random = new Random(seed);
        long a = random.nextLong();
        long b = random.nextLong();
        random.setSeed((long) chunkX * a ^ (long) chunkZ * b ^ seed);
        return random.nextFloat() <= probability;
    }

    /** The full per-seed-chunk decision the command's spiral asks; the implementation carries the mixin's
     *  append gates (flag/radius/band/fray/sea-probe) + the {@link #carverStartsAt} roll. */
    @FunctionalInterface
    public interface StartChunkPredicate {
        boolean isStartChunk(int chunkX, int chunkZ);
    }

    /** A found start chunk: its chunk coords, its center block coords, and the block distance from the search
     *  origin to that center. */
    public record Hit(int chunkX, int chunkZ, int blockX, int blockZ, double distanceBlocks) {
    }

    /**
     * Spiral-search outward from the origin over seed-chunk coords, ring by Chebyshev ring, and return the
     * hit whose CHUNK CENTER is nearest the origin in block distance (or {@code null} if no hit within
     * {@code maxRadiusChunks}). Because a Chebyshev ring {@code r} holds Euclidean distances in
     * {@code [r, r*sqrt(2)]} rings, the scan continues past the first hit until the ring lower bound exceeds
     * the best distance found -- so the returned hit is the true nearest, not just the first ring's. Pure:
     * the predicate owns all world reads; this method only orders the candidates.
     */
    public static Hit findNearest(double originBlockX, double originBlockZ, int maxRadiusChunks,
                                  StartChunkPredicate predicate) {
        int originChunkX = Math.floorDiv((int) Math.floor(originBlockX), 16);
        int originChunkZ = Math.floorDiv((int) Math.floor(originBlockZ), 16);
        Hit best = null;
        for (int r = 0; r <= maxRadiusChunks; r++) {
            if (best != null && (double) (r - 1) * 16.0 > best.distanceBlocks()) {
                break; // every remaining candidate is provably farther than the best hit
            }
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue; // ring cells only (interior was covered by smaller r)
                    }
                    int cx = originChunkX + dx;
                    int cz = originChunkZ + dz;
                    if (!predicate.isStartChunk(cx, cz)) {
                        continue;
                    }
                    int blockX = cx * 16 + 8;
                    int blockZ = cz * 16 + 8;
                    double dist = Math.hypot(blockX + 0.5 - originBlockX, blockZ + 0.5 - originBlockZ);
                    if (best == null || dist < best.distanceBlocks()) {
                        best = new Hit(cx, cz, blockX, blockZ, dist);
                    }
                }
            }
        }
        return best;
    }
}
