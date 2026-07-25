package com.example.globe.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Phase 5 B-9 P1 "Glacial Caves &amp; Crevasses" -- the pure carver-list filter law
 * ({@code latitude.glacialCavesV1}). Peetsa (TEST 110): polar "caverns are giant voids" -- the future
 * crevasses should be "narrow and winding ice labyrinths". P1 delivers them as two vanilla-type
 * configured carvers ({@code globe:crevasse}, canyon type; {@code globe:glacial_tunnels}, cave type)
 * attached CODE-SIDE at the one {@code getCarvers()} call site inside
 * {@code NoiseBasedChunkGenerator.applyCarvers} -- attachment through the biome JSON is DEAD WIRING
 * (design ground truth 3: {@code applyCarvers} resolves each seed-chunk's carver list from the raw
 * multi_noise {@code biomeSource} field, which can never emit a {@code globe:} biome, so a carver
 * listed on {@code globe:polar_barrens} would never fire).
 *
 * <p>This class holds ONLY the list law (mirroring {@link PolarBarrensBand}: zero Minecraft imports,
 * Core Logic layer, unit-testable in a plain JVM). The mixin
 * ({@code com.example.globe.mixin.NoiseChunkGeneratorCarveMixin}) is a thin adapter that gathers the
 * booleans (settings key, latitude band + fray, sea probe, flag) and the resolved globe carver
 * holders, then calls {@link #filter}; entry identity ("is this a {@code minecraft:*} carver?") comes
 * in as a {@link Predicate} so this class never touches registry types.
 *
 * <h2>The two legs (design: "The attachment seam", mechanism (b))</h2>
 * <ul>
 *   <li><b>LEGACY LEG</b> ({@link #legacyStripApplies}): on {@code stable(globe:overworld)} worlds
 *       (the legacy 15000-radius settings KEY -- and ONLY that key; Massive worlds keep vanilla
 *       carving poleward of 65 deg, sweep finding 6) with the applyCarvers CENTER chunk at
 *       {@code |z| >= POLAR_CAP_START} (14500), every {@code minecraft:*} entry is stripped. For the
 *       vanilla/datapack biomes those lists are entirely {@code minecraft:*}, so the filtered list is
 *       EMPTY -- and the design sweep verified empty-list is block-identical to the old HEAD blanket
 *       cancel this leg replaces (the only difference is an empty serialized carving_mask in
 *       mid-generation proto-chunk NBT, invisible at FULL status, accepted).</li>
 *   <li><b>B-9 LEG</b>: when {@code latitude.glacialCavesV1} is on and the SEED chunk is
 *       barrens-band LAND (the mixin's decision, riding the exact shared
 *       {@link PolarBarrensBand#isBarrens} latitude+fray decision the biome/glacier use), the two
 *       globe holders are APPENDED after the raw list. Appending is load-bearing for RNG stability:
 *       vanilla seeds each carver as {@code seed + listIndex} ({@code setLargeFeatureSeed}), so a
 *       tail append leaves every vanilla carver at its exact index 0..n-1 with its exact stream, and
 *       flag-off (no append, no strip) returns the raw list unchanged -- byte-identical.</li>
 * </ul>
 *
 * <h2>Why the legacy leg keys on the CENTER chunk, not the seed chunk (bytecode-reasoned)</h2>
 * The old mixin cancelled ALL of {@code applyCarvers} for a center chunk with
 * {@code |minBlockZ + 8| >= 14500}. Block truth of that behavior: (1) a poleward center chunk
 * receives NO carved blocks from ANY seed chunk (all 17x17 seed arcs suppressed in that chunk);
 * (2) an equatorward center chunk receives FULL vanilla carving INCLUDING arcs whose seed chunks lie
 * poleward of 14500 (the cancel never looked at seed chunks). The new filter runs per seed-chunk
 * list resolution, so keying the strip on the SEED chunk would diverge in BOTH directions: poleward
 * seed arcs would vanish from equatorward chunks (blocks that used to be carved, now solid), and
 * equatorward seed arcs would newly carve into poleward chunks (blocks that used to be solid, now
 * air). Keying on the CENTER chunk reproduces the old behavior exactly: for a poleward center chunk
 * every one of the 289 list resolutions inside that {@code applyCarvers} call strips (empty for
 * vanilla biomes = nothing carves = cancel-identical), and for an equatorward center chunk none do.
 * Behavior-identity for existing legacy worlds is the requirement; the {@code minBlockZ + 8} center
 * idiom is carried over verbatim from the retired cancel.
 *
 * <p>The two legs COMPOSE (legacy strip + B-9 append can both fire in one resolution): a legacy
 * world's polar cap with the flag on gets ONLY the glacial carvers -- vanilla carving stays retired
 * there, and the glacial pair land at indices 0..1 of the filtered list. That is wanted: the legacy
 * cap never had vanilla carving to preserve, and B-9 is the first carving that belongs there.
 */
public final class GlacialCarverLaw {

    private GlacialCarverLaw() {
    }

    /**
     * The legacy leg's condition, verbatim semantics of the retired HEAD cancel: the world's noise
     * settings must be the legacy {@code globe:overworld} KEY (the caller resolves
     * {@code stable(globe:overworld)}; settings-KEY-scoped, deliberately NOT any-globe -- sweep
     * finding 6) and the applyCarvers CENTER chunk's z-center must sit at/past the polar cap start.
     *
     * @param legacyGlobeSettingsKey  {@code stable(globe:overworld)} -- true ONLY for the legacy
     *                                15000-radius settings key, never for the sized keys
     *                                (xsmall/small/regular/large/massive)
     * @param centerChunkMinBlockZ    {@code chunk.getPos().getMinBlockZ()} of the CENTER chunk of
     *                                {@code applyCarvers} (see class javadoc for why center, not seed)
     * @param polarCapStartBlocks     {@code GlobeRegions.POLAR_CAP_START} (14500) -- passed in because
     *                                this layer imports no world classes
     * @return true iff the {@code minecraft:*} strip applies to every seed-chunk list resolved for
     *         this center chunk
     */
    public static boolean legacyStripApplies(boolean legacyGlobeSettingsKey,
                                             int centerChunkMinBlockZ,
                                             int polarCapStartBlocks) {
        if (!legacyGlobeSettingsKey) {
            return false;
        }
        // The old cancel's exact center idiom: chunk-min block z + 8 = the chunk's z-center column.
        return Math.abs(centerChunkMinBlockZ + 8) >= polarCapStartBlocks;
    }

    /**
     * The order-preserving carver-list filter. Pure: same inputs, same output list, no hidden state.
     * <ul>
     *   <li>{@code legacyStripVanilla} drops exactly the entries {@code isVanillaEntry} accepts,
     *       preserving the relative order of survivors (non-{@code minecraft:*} datapack carvers,
     *       which the old blanket cancel also suppressed, now legally survive -- the design defines
     *       the legacy leg as the {@code minecraft:*} strip, and the swept block-identity claim is
     *       about the real legacy worlds, whose biome carver lists are entirely vanilla).</li>
     *   <li>{@code appendGlobe} appends {@code globeCarvers} AFTER the (possibly stripped) raw list,
     *       in the given order -- vanilla survivors keep their original indices, so their
     *       {@code seed + index} carver streams are untouched.</li>
     *   <li>Both false: the output is an order-identical copy of the input (the mixin fast-paths this
     *       case and returns vanilla's own Iterable without ever calling here; the law still keeps
     *       the contract honest for tests).</li>
     * </ul>
     *
     * @param rawCarvers      the seed-chunk biome's carver list as vanilla resolved it
     * @param legacyStripVanilla  the {@link #legacyStripApplies} decision for this center chunk
     * @param appendGlobe     the B-9 leg's decision for this SEED chunk (flag on + barrens-band land)
     * @param globeCarvers    the resolved {@code globe:crevasse} + {@code globe:glacial_tunnels}
     *                        holders, in that order (empty when {@code appendGlobe} is false)
     * @param isVanillaEntry  true for entries whose registry key namespace is {@code minecraft}
     *                        (unkeyed/direct holders are NOT vanilla entries and never strip)
     * @return a new list; never null, never the input instance
     */
    public static <T> List<T> filter(Iterable<T> rawCarvers,
                                     boolean legacyStripVanilla,
                                     boolean appendGlobe,
                                     List<T> globeCarvers,
                                     Predicate<T> isVanillaEntry) {
        ArrayList<T> out = new ArrayList<>();
        for (T entry : rawCarvers) {
            if (legacyStripVanilla && isVanillaEntry.test(entry)) {
                continue;
            }
            out.add(entry);
        }
        if (appendGlobe) {
            out.addAll(globeCarvers);
        }
        return out;
    }
}
