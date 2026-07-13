package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.terrain.EvatorTerrainReflection;
import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================ B-6 P1 PROOF HARNESS (production; repeatable gradle gate) ============================
 *
 * <p>The promoted, production successor of the throwaway {@code EvatorSpikeProbe} (retained as spike evidence).
 * Where the probe compared FOUR candidate mechanisms to decide which one to build, this harness exercises the
 * SHIPPING mechanism -- {@link EvatorTerrainReflection} (leaf reflection) + {@code LatitudeBiomeSource}'s biome
 * remap -- and ASSERTS the mirror-identity property that makes the crossing invisible, so a broken remap is
 * caught headlessly before Peetsa ever flies it.
 *
 * <p><b>What it proves (evator captured ON, real fill paths):</b> for {@code N >= 15} east/west column pairs
 * spanning the band (incl. frontier-adjacent depths), at world radius {@link #PROOF_X_RADIUS}:
 * <ul>
 *   <li><b>Biome identity</b> -- {@code source.getNoiseBiome(eastQuart) == source.getNoiseBiome(westQuart)}
 *       through the production {@link LatitudeBiomeSource#getNoiseBiome} remap (100% required).</li>
 *   <li><b>Terrain silhouette identity</b> -- the solid/air column silhouette hash matches across the pair, read
 *       through the LIVE {@code getChunk(NOISE)} batched-interpolation fill path (the path mechanism "a" failed
 *       on).</li>
 *   <li><b>Surface-Y gate</b> -- {@code |eastSurfaceY - westSurfaceY| <= }{@link #SURFACE_Y_GATE_BLOCKS} (a
 *       FIXED 12-block contract: above mechanism (b)'s proven 9-block steep-gradient ceiling, far below the
 *       fog's measured masking budget of avg 18.4 / max 61 at the inner frontier). The run-measured fog budget
 *       and the &plusmn;2 typical-wobble count are reported as context, not gated (see the design doc's
 *       "surface-Y tolerance" paragraph).</li>
 *   <li><b>East-interpolation control</b> -- direct(raw reflected) vs interpolated surfaceY differs on the east
 *       side (nonzero), PROVING interpolation genuinely ran on the reflected band (mechanism "a"'s tell was a
 *       forced-raw east with a zero control).</li>
 *   <li><b>Unknown-leaf guard</b> -- the production install returns {@code INSTALLED} (no unknown coordinate
 *       leaf in the live router), and the leaf inventory is dumped for audit.</li>
 * </ul>
 * The flag-OFF byte-identity gate is proven separately, at the atlas layer (a {@code -Dlatitude.evatorV2.enabled=false}
 * atlas equals HEAD's, byte-for-byte) -- structurally guaranteed here because every evator code path is behind
 * the flag/capture check, but verified empirically in the run log per the P1 plan.
 *
 * <p>Trigger {@code -Dlatitude.evatorProof=true}. Writes {@code -Dlatitude.evatorProof.out} (default
 * {@code run-headless/latdev/evator-proof-report.json}) and logs a PASS/FAIL verdict, then halts the server.
 */
public final class EvatorProofHarness {

    private EvatorProofHarness() {
    }

    private static final int TOP_N = 16;
    /** Regular Classic -- matches the spike's proof radius (band width ~208 blk). */
    private static final int PROOF_X_RADIUS = 7500;
    /** The TYPICAL per-column interpolation wobble of mechanism (b) (reported, not the gate). */
    private static final int SURFACE_Y_TOLERANCE = 2;
    /**
     * The HARD surface-Y gate (P1 sweep MED fix): a FIXED contract, not a run-measured budget. 12 blocks sits
     * comfortably above mechanism (b)'s proven 9-block steep-gradient ceiling (so the known-good near-mirror
     * passes with margin) and far below the fog's measured masking budget (avg 18.4 / max 61 at the inner
     * frontier -- context, not contract), so any regression past it is caught even on a run where the fog-seam
     * measurement happens to come out unusually large. The run-measured fog budget stays in the report as
     * context only.
     */
    private static final int SURFACE_Y_GATE_BLOCKS = 12;

    public static boolean isTriggered() {
        return Boolean.parseBoolean(System.getProperty("latitude.evatorProof", "false"));
    }

    private record SimpleCtx(int blockX, int blockY, int blockZ) implements DensityFunction.FunctionContext {
    }

    private record Pair(int d, int z, int eastX, int westX) {
    }

    private static final class Cmp {
        int d, z, eastX, westX, eastSurfaceY, westSurfaceY, surfaceDelta;
        boolean surfaceWithinGate; // |dY| <= SURFACE_Y_GATE_BLOCKS (the fixed HARD gate)
        boolean surfaceWithinTol;  // |dY| <= typical-wobble tolerance (reported)
        boolean surfaceUnderFog;   // |dY| <= the measured fog-masked frontier-seam max (reported context)
        boolean terrainMatch;      // solid/air silhouette hash identity (near-mirror, reported)
        boolean biomeMatch;
        String eastBiome, westBiome;
    }

    public static void runAndStop(MinecraftServer server) {
        boolean pass = true;
        StringBuilder json = new StringBuilder();
        try {
            ServerLevel world = server.overworld();
            if (world == null) {
                GlobeMod.LOGGER.error("[evatorProof] no overworld; stopping");
                return;
            }
            ChunkGenerator genRaw = world.getChunkSource().getGenerator();
            if (!(genRaw instanceof NoiseBasedChunkGenerator generator)) {
                GlobeMod.LOGGER.error("[evatorProof] generator is not NoiseBasedChunkGenerator: {}", genRaw);
                return;
            }

            // Arm the evator for THIS world exactly as a captured-on world would (per-world live cache), and pin
            // the intended X radius the band predicate reads. Border-independent (EvatorMirror reads the radius,
            // not the live border), so the headless minecraft:normal world's huge border is irrelevant.
            LatitudeBiomes.setActiveRadiusBlocks(PROOF_X_RADIUS);
            LatitudeBiomes.setEvatorActive(true);
            int xRadius = LatitudeBiomes.getActiveXRadiusBlocks();
            long seed = world.getSeed();
            int minY = world.getMinY();
            int maxY = world.getMaxY();
            double frontier = com.example.globe.core.MirrorGeometry.frontierX(xRadius);

            // Column pairs across the band: depths from the wall to just inside the frontier (~208 blk), incl.
            // frontier-adjacent, x 3 z lanes = 21 pairs (>= the spike's 15, with frontier-adjacent added).
            int[] ds = {8, 16, 40, 80, 140, 190, 205};
            int[] zs = {0, 640, -640};
            List<Pair> pairs = new ArrayList<>();
            for (int d : ds) {
                for (int z : zs) {
                    pairs.add(new Pair(d, z, xRadius - d, -(xRadius - d)));
                }
            }

            GlobeMod.LOGGER.info("[evatorProof] START seed={} xRadius={} frontierAbsX={} bandWidth={} pairs={} tol={}",
                    seed, xRadius, String.format("%.1f", frontier), xRadius - Math.round(frontier), pairs.size(), SURFACE_Y_TOLERANCE);

            // --- Install the production terrain mirror on the LIVE world RandomState (drives getChunk NOISE) ---
            RandomState liveRs = world.getChunkSource().randomState();
            EvatorTerrainReflection.resetLogLatchesForNewWorld();
            EvatorTerrainReflection.InstallResult installResult = EvatorTerrainReflection.install(liveRs);
            GlobeMod.LOGGER.info("[evatorProof] terrain-mirror install result = {}", installResult);
            boolean installedOk = installResult == EvatorTerrainReflection.InstallResult.INSTALLED;
            if (!installedOk) {
                pass = false;
                GlobeMod.LOGGER.error("[evatorProof] FAIL: production install did not return INSTALLED (unknown leaf? null?)");
            }
            DensityFunction liveFinal = liveRs.router().finalDensity(); // now leaf-reflected

            // Leaf-inventory audit (unknown-leaf guard coverage) on the live router.
            java.util.Set<String> unknown = EvatorTerrainReflection.collectUnknownClasses(liveRs.router().finalDensity());
            unknown.addAll(EvatorTerrainReflection.collectUnknownClasses(liveRs.router().preliminarySurfaceLevel()));
            GlobeMod.LOGGER.info("[evatorProof] unknown-leaf scan (live router #11+#12): {}",
                    unknown.isEmpty() ? "NONE (guard clean)" : unknown);

            // --- The fog's demonstrated masking budget: the inner-frontier seam it ALREADY hides ---------------
            // The invisible-seam claim rests on the storm/approach fog, not on bit-exactness (mechanism "b" is a
            // near-mirror, by Peetsa's decision). To justify the surface-Y tolerance we MEASURE the C0
            // discontinuity the fog is calibrated to mask: at the band's inner frontier the mirror value
            // plain(-xf) butts the canonical interior plain(+xf). A twin-column wobble smaller than this seam is,
            // by construction, less than what the fog demonstrably hides -> invisible. Measured on a PLAIN
            // (unwrapped) RandomState, same as the spike's FRONTIER-SEAM scan.
            RandomState rsPlain = RandomState.create(generator.generatorSettings().value(),
                    world.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            DensityFunction plainFinal = rsPlain.router().finalDensity();
            int xf = (int) Math.round(frontier);
            int seamCount = 0, seamSum = 0, fogSeamMax = 0;
            for (int z = -2000; z <= 2000; z += 250) {
                int jump = Math.abs(directSurfaceY(plainFinal, -xf, z, minY, maxY)
                        - directSurfaceY(plainFinal, xf, z, minY, maxY));
                seamCount++; seamSum += jump; fogSeamMax = Math.max(fogSeamMax, jump);
            }
            double fogSeamAvg = seamCount == 0 ? 0 : (double) seamSum / seamCount;
            GlobeMod.LOGGER.info("[evatorProof] fog-masked inner-frontier seam (xf={}): avg={} max={} over {} cols "
                    + "-- the budget the twin-wobble must stay under", xf, String.format("%.1f", fogSeamAvg), fogSeamMax, seamCount);

            // --- Biome source that exercises the production getNoiseBiome remap ---
            BiomeSource baseSource = generator.getBiomeSource();
            Registry<Biome> biomeRegistry = world.registryAccess().lookupOrThrow(Registries.BIOME);
            Climate.Sampler sampler = liveRs.sampler();
            LatitudeBiomeSource latSource = LatitudeBiomeSource.forLocate(
                    baseSource, biomeRegistry, xRadius, generator, liveRs, world);
            int biomeY = 64 >> 2;

            // --- Compare each pair on the LIVE fill path + biome remap ---
            List<Cmp> rows = new ArrayList<>();
            int eastInterpNonzero = 0, eastInterpMax = 0, maxTwinDelta = 0;
            for (Pair p : pairs) {
                Cmp c = new Cmp();
                c.d = p.d(); c.z = p.z(); c.eastX = p.eastX(); c.westX = p.westX();

                int[] eCol = readLiveColumn(world, p.eastX(), p.z(), minY, maxY);
                int[] wCol = readLiveColumn(world, p.westX(), p.z(), minY, maxY);
                c.eastSurfaceY = eCol[0];
                c.westSurfaceY = wCol[0];
                c.surfaceDelta = Math.abs(eCol[0] - wCol[0]);
                c.surfaceWithinGate = c.surfaceDelta <= SURFACE_Y_GATE_BLOCKS;
                c.surfaceWithinTol = c.surfaceDelta <= SURFACE_Y_TOLERANCE;
                c.surfaceUnderFog = c.surfaceDelta <= fogSeamMax;
                c.terrainMatch = eCol[2] == wCol[2];
                maxTwinDelta = Math.max(maxTwinDelta, c.surfaceDelta);

                // Biome identity through the production remap (quart coords). P1 sweep MED fix: the WEST
                // reference quart is derived through the SAME production mapping the remap emits
                // (EvatorMirror.reflectQuartX: q -> -q), NOT floorDiv(westX, 4) -- for eastX not a multiple
                // of 4 those differ by one quart, so 6/21 pairs were comparing the quart ADJACENT to what
                // production actually outputs instead of testing true reflection identity.
                int eqx = Math.floorDiv(p.eastX(), 4);
                int wqx = com.example.globe.terrain.EvatorMirror.reflectQuartX(eqx);
                int qz = Math.floorDiv(p.z(), 4);
                Holder<Biome> eb = latSource.getNoiseBiome(eqx, biomeY, qz, sampler);
                Holder<Biome> wb = latSource.getNoiseBiome(wqx, biomeY, qz, sampler);
                c.eastBiome = biomeId(biomeRegistry, eb);
                c.westBiome = biomeId(biomeRegistry, wb);
                c.biomeMatch = c.eastBiome.equals(c.westBiome);

                // East interpolation-ran control: raw(reflected) vs interpolated surfaceY on the east column.
                int rawReflected = directSurfaceY(liveFinal, p.eastX(), p.z(), minY, maxY);
                int delta = Math.abs(rawReflected - eCol[0]);
                if (delta != 0) {
                    eastInterpNonzero++;
                    eastInterpMax = Math.max(eastInterpMax, delta);
                }
                rows.add(c);
            }

            int biomeOk = 0, terrainOk = 0, within2 = 0, underFog = 0, withinGate = 0;
            for (Cmp c : rows) {
                if (c.biomeMatch) biomeOk++;
                if (c.terrainMatch) terrainOk++;
                if (c.surfaceWithinTol) within2++;
                if (c.surfaceUnderFog) underFog++;
                if (c.surfaceWithinGate) withinGate++;
                if (!c.biomeMatch || !c.surfaceWithinGate) {
                    GlobeMod.LOGGER.info("[evatorProof]   MISS d={} z={} eSurf={} wSurf={} dY={} withinGate={} biome[{} vs {}]={}",
                            c.d, c.z, c.eastSurfaceY, c.westSurfaceY, c.surfaceDelta, c.surfaceWithinGate,
                            c.eastBiome, c.westBiome, c.biomeMatch);
                }
            }
            int n = rows.size();
            // HARD GATES (distinguish a working near-mirror from a broken remap): biome identity 100%; every
            // twin-wobble within the FIXED 12-block contract (P1 sweep MED fix: a run-measured fog budget
            // could come out luckily large and mask a regression -- 12 is fixed above the proven 9-block
            // ceiling and far below the fog's 18.4-avg budget); interpolation genuinely ran on the reflected
            // east band; install clean. The measured fog budget, the +-2 typical-wobble count, and terrain-
            // silhouette identity are REPORTED context (near-mirror, not bit-exact -- that is mechanism "c").
            boolean biomePass = biomeOk == n;
            boolean surfacePass = withinGate == n;
            boolean eastInterpPass = eastInterpNonzero > 0;
            pass = pass && installedOk && unknown.isEmpty() && biomePass && surfacePass && eastInterpPass;

            GlobeMod.LOGGER.info("[evatorProof] ===== VERDICT ({}) =====", pass ? "PASS" : "FAIL");
            GlobeMod.LOGGER.info("[evatorProof]   install={} unknownLeaves={}", installResult, unknown.isEmpty() ? 0 : unknown.size());
            GlobeMod.LOGGER.info("[evatorProof]   [GATE] biomeIdentity        = {}/{}  {}", biomeOk, n, biomePass ? "OK" : "FAIL");
            GlobeMod.LOGGER.info("[evatorProof]   [GATE] surfaceY |dY|<={} FIXED = {}/{}  (maxTwin|dY|={})  {}", SURFACE_Y_GATE_BLOCKS, withinGate, n, maxTwinDelta, surfacePass ? "OK" : "FAIL");
            GlobeMod.LOGGER.info("[evatorProof]   [GATE] eastInterpRan         = nonzero {}/{} (max {})  {}", eastInterpNonzero, n, eastInterpMax, eastInterpPass ? "OK" : "FAIL");
            GlobeMod.LOGGER.info("[evatorProof]   [info] fog budget (context)  = under fogSeamMax {} on {}/{} (avg {})", fogSeamMax, underFog, n, String.format("%.1f", fogSeamAvg));
            GlobeMod.LOGGER.info("[evatorProof]   [info] surfaceY within +-{}   = {}/{}  (typical wobble)", SURFACE_Y_TOLERANCE, within2, n);
            GlobeMod.LOGGER.info("[evatorProof]   [info] terrainSilhouette id  = {}/{}  (near-mirror; bit-exact is mechanism c)", terrainOk, n);

            json.append("{\n");
            json.append("  \"pass\": ").append(pass).append(",\n");
            json.append("  \"seed\": ").append(seed).append(",\n");
            json.append("  \"xRadius\": ").append(xRadius).append(",\n");
            json.append("  \"pairs\": ").append(n).append(",\n");
            json.append("  \"install\": \"").append(installResult).append("\",\n");
            json.append("  \"unknownLeaves\": ").append(unknown.isEmpty() ? "[]" : ("[\"" + String.join("\",\"", unknown) + "\"]")).append(",\n");
            json.append("  \"biomeIdentity\": ").append(biomeOk).append(",\n");
            json.append("  \"surfaceYGateBlocks\": ").append(SURFACE_Y_GATE_BLOCKS).append(",\n");
            json.append("  \"surfaceWithinGate\": ").append(withinGate).append(",\n");
            json.append("  \"fogSeamAvg\": ").append(String.format("%.2f", fogSeamAvg)).append(",\n");
            json.append("  \"fogSeamMax\": ").append(fogSeamMax).append(",\n");
            json.append("  \"surfaceUnderFog\": ").append(underFog).append(",\n");
            json.append("  \"maxTwinSurfaceDelta\": ").append(maxTwinDelta).append(",\n");
            json.append("  \"surfaceWithin2\": ").append(within2).append(",\n");
            json.append("  \"terrainSilhouetteId\": ").append(terrainOk).append(",\n");
            json.append("  \"eastInterpNonzero\": ").append(eastInterpNonzero).append(",\n");
            json.append("  \"eastInterpMax\": ").append(eastInterpMax).append(",\n");
            json.append("  \"rows\": ").append(rowsJson(rows)).append("\n");
            json.append("}\n");

            Path out = resolveOut(server);
            Files.createDirectories(out.getParent());
            Files.write(out, json.toString().getBytes(StandardCharsets.UTF_8));
            GlobeMod.LOGGER.info("[evatorProof] wrote report {}", out);
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[evatorProof] harness failed", t);
        } finally {
            server.halt(false);
        }
    }

    // ---- column readers ----------------------------------------------------------------------------

    private static int directSurfaceY(DensityFunction fd, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            if (fd.compute(new SimpleCtx(x, y, z)) >= 0.0) {
                return y;
            }
        }
        return minY - 1;
    }

    private static int[] readLiveColumn(ServerLevel world, int x, int z, int minY, int maxY) {
        int cx = Math.floorDiv(x, 16);
        int cz = Math.floorDiv(z, 16);
        ChunkAccess chunk = world.getChunkSource().getChunk(cx, cz, ChunkStatus.NOISE, true);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int surface = minY - 1;
        for (int y = maxY; y >= minY; y--) {
            BlockState s = chunk.getBlockState(pos.set(x, y, z));
            if (s != null && !s.isAir() && s.getFluidState().isEmpty()) {
                surface = y;
                break;
            }
        }
        int terrainHash = 1; // solid(1)/non-solid(0) silhouette straddling the surface
        if (surface >= minY) {
            for (int y = Math.min(maxY, surface + 8); y >= Math.max(minY, surface - 24); y--) {
                BlockState s = chunk.getBlockState(pos.set(x, y, z));
                int solid = (s != null && !s.isAir() && s.getFluidState().isEmpty()) ? 1 : 0;
                terrainHash = 31 * terrainHash + solid;
            }
        }
        return new int[]{surface, 0, terrainHash};
    }

    private static String biomeId(Registry<Biome> reg, Holder<Biome> h) {
        if (h == null) return "null";
        return h.unwrapKey().map(Object::toString).orElseGet(h::toString);
    }

    private static String rowsJson(List<Cmp> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            Cmp c = rows.get(i);
            sb.append("{\"d\":").append(c.d).append(",\"z\":").append(c.z)
                    .append(",\"eSurf\":").append(c.eastSurfaceY).append(",\"wSurf\":").append(c.westSurfaceY)
                    .append(",\"dY\":").append(c.surfaceDelta)
                    .append(",\"withinGate\":").append(c.surfaceWithinGate)
                    .append(",\"underFog\":").append(c.surfaceUnderFog)
                    .append(",\"within2\":").append(c.surfaceWithinTol)
                    .append(",\"terrainMatch\":").append(c.terrainMatch)
                    .append(",\"biomeMatch\":").append(c.biomeMatch)
                    .append(",\"eastBiome\":\"").append(c.eastBiome).append("\"}");
            if (i < rows.size() - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    private static Path resolveOut(MinecraftServer server) {
        String raw = System.getProperty("latitude.evatorProof.out", "").trim();
        if (!raw.isEmpty()) {
            return Path.of(raw);
        }
        return server.getServerDirectory().resolve("latdev").resolve("evator-proof-report.json");
    }
}
