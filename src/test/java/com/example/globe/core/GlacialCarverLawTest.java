package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 B-9 P1: pins the pure carver-list filter law ({@link GlacialCarverLaw}) that
 * {@code NoiseChunkGeneratorCarveMixin} adapts at the single {@code getCarvers()} call site inside
 * {@code applyCarvers}. These tests carry the flag-off byte-identity claim's unit-test share (design
 * law: gate-1 cannot see the carver seam -- the atlas never executes {@code applyCarvers} -- so
 * identity rests on THIS law + the standing gate-1 class-load re-proof + the self-fly).
 *
 * <p>Entries are plain strings ("minecraft:cave" etc.) with the same namespace predicate shape the
 * mixin uses on registry keys -- the law is generic and never sees Minecraft types, which is exactly
 * why it is testable in this plain JVM.
 *
 * <p>The legacy threshold is the literal 14500 here: production passes
 * {@code GlobeRegions.POLAR_CAP_START} (== 14500, = POLAR_WHITEOUT_START), but importing
 * {@code GlobeRegions} would chain {@code GlobeMod}'s static init into a plain test JVM, so the law
 * takes the threshold as a parameter and this test pins the law's math against the known value.
 */
class GlacialCarverLawTest {

    private static final int POLAR_CAP_START = 14500; // GlobeRegions.POLAR_CAP_START (see class javadoc)

    /** Mirrors the mixin's GLOBE_LEGACY_STRIP_TARGET: everything that is not ours strips (the
     *  retired HEAD cancel suppressed ALL carving, incl. datapack + unkeyed entries -- sweep
     *  2026-07-19 finding 2), so cancel-identity = strip everything non-{@code globe:*}. */
    private static final Predicate<String> LEGACY_STRIP_TARGET = entry -> !entry.startsWith("globe:");

    /** The vanilla trio in vanilla order -- what every legacy-world biome actually carries. */
    private static final List<String> VANILLA_TRIO =
            List.of("minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon");

    private static final List<String> GLOBE_PAIR = List.of("globe:crevasse", "globe:glacial_tunnels");

    // --- (a) legacy strip removes everything non-globe and preserves order ------------------------

    @Test
    void legacyStripRemovesEverythingNonGlobeAndPreservesOrder() {
        // Datapack carvers (somepack:/otherpack:) strip too -- the retired cancel killed them, so
        // cancel-identity demands it. Only globe:* survives (hypothetical here; real raw lists
        // never carry globe entries -- this pins the pure law's order preservation).
        List<String> raw = List.of(
                "minecraft:cave", "globe:crevasse", "somepack:crystal_carver",
                "otherpack:worm_tunnel", "globe:glacial_tunnels");
        List<String> out = GlacialCarverLaw.filter(raw, true, false, List.of(), LEGACY_STRIP_TARGET);
        assertEquals(List.of("globe:crevasse", "globe:glacial_tunnels"), out,
                "Legacy strip must drop every non-globe entry (vanilla AND datapack) in order");
    }

    @Test
    void legacyStripOnAllVanillaListYieldsEmptyListTheCancelEquivalence() {
        List<String> out = GlacialCarverLaw.filter(VANILLA_TRIO, true, false, List.of(), LEGACY_STRIP_TARGET);
        assertTrue(out.isEmpty(),
                "A legacy-world biome's all-vanilla carver list must strip to EMPTY -- the "
                        + "block-level equivalence of the retired HEAD cancel (nothing carves)");
    }

    // --- (b) legacy strip only under the legacy conditions ----------------------------------------

    @Test
    void legacyConditionRequiresTheLegacySettingsKey() {
        assertFalse(GlacialCarverLaw.legacyStripApplies(false, -20000, POLAR_CAP_START),
                "Without stable(globe:overworld) the strip must NEVER apply, no matter how deep the "
                        + "|z| (Massive worlds keep vanilla carving poleward of 65 deg -- sweep finding 6)");
        assertFalse(GlacialCarverLaw.legacyStripApplies(false, 20000, POLAR_CAP_START),
                "Key-scoping must hold in the northern hemisphere too");
    }

    @Test
    void legacyConditionUsesTheCenterChunkPlusEightIdiomAtTheExactThreshold() {
        // The old cancel read chunk.getPos().getMinBlockZ() + 8 (the chunk's z-center column) and
        // compared |value| >= 14500. Pin the exact boundary on both hemispheres.
        assertTrue(GlacialCarverLaw.legacyStripApplies(true, 14492, POLAR_CAP_START),
                "minBlockZ 14492 -> center 14500: exactly at the cap start, strip applies");
        assertFalse(GlacialCarverLaw.legacyStripApplies(true, 14491, POLAR_CAP_START),
                "minBlockZ 14491 -> center 14499: one block short, no strip");
        assertTrue(GlacialCarverLaw.legacyStripApplies(true, -14508, POLAR_CAP_START),
                "minBlockZ -14508 -> center -14500: southern cap boundary, strip applies");
        assertFalse(GlacialCarverLaw.legacyStripApplies(true, -14507, POLAR_CAP_START),
                "minBlockZ -14507 -> center -14499: one block short south, no strip");
        assertFalse(GlacialCarverLaw.legacyStripApplies(true, 0, POLAR_CAP_START),
                "The equator never strips");
    }

    // --- (c) B-9 append: exactly the two globe entries at tail when on; nothing when off ----------

    @Test
    void appendAddsExactlyTheGlobePairAtTheTail() {
        List<String> out = GlacialCarverLaw.filter(VANILLA_TRIO, false, true, GLOBE_PAIR, LEGACY_STRIP_TARGET);
        assertEquals(List.of("minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon",
                        "globe:crevasse", "globe:glacial_tunnels"), out,
                "The B-9 leg must append crevasse then glacial_tunnels AFTER the untouched raw list");
    }

    @Test
    void appendOffLeavesTheListIdenticalTheFlagOffByteIdentityShare() {
        // Flag off / out of band / sea seed chunk all reach the law as appendGlobe=false: the mixin
        // additionally fast-paths this case to vanilla's own Iterable without calling the law at all,
        // so the law's contract here is belt-and-braces.
        List<String> out = GlacialCarverLaw.filter(VANILLA_TRIO, false, false, List.of(), LEGACY_STRIP_TARGET);
        assertEquals(VANILLA_TRIO, out,
                "With both legs dormant the output must be order-identical to the input");
    }

    @Test
    void appendOnEmptyRawListYieldsJustTheGlobePair() {
        List<String> out = GlacialCarverLaw.filter(List.of(), false, true, GLOBE_PAIR, LEGACY_STRIP_TARGET);
        assertEquals(GLOBE_PAIR, out,
                "A biome with no carvers of its own still gets the glacial pair (indices 0..1)");
    }

    // --- (d) order + index stability: vanilla prefix untouched in every configuration -------------

    @Test
    void vanillaPrefixKeepsExactIndicesInEveryConfiguration() {
        // Vanilla seeds each carver as seed + listIndex, so index stability IS RNG stability.
        for (boolean strip : new boolean[]{false, true}) {
            for (boolean append : new boolean[]{false, true}) {
                List<String> out = GlacialCarverLaw.filter(
                        VANILLA_TRIO, strip, append, append ? GLOBE_PAIR : List.of(), LEGACY_STRIP_TARGET);
                List<String> expectedPrefix = strip ? List.of() : VANILLA_TRIO;
                assertEquals(expectedPrefix, out.subList(0, expectedPrefix.size()),
                        "Surviving vanilla entries must keep their exact original indices "
                                + "(strip=" + strip + ", append=" + append + ")");
                if (!strip) {
                    for (int i = 0; i < VANILLA_TRIO.size(); i++) {
                        assertEquals(VANILLA_TRIO.get(i), out.get(i),
                                "Vanilla carver at index " + i + " must be untouched so its "
                                        + "seed + index stream is byte-identical");
                    }
                }
            }
        }
    }

    // --- (e) both legs compose ---------------------------------------------------------------------

    @Test
    void bothLegsComposeStripThenAppend() {
        List<String> raw = List.of(
                "minecraft:cave", "somepack:crystal_carver", "minecraft:cave_extra_underground",
                "minecraft:canyon");
        List<String> out = GlacialCarverLaw.filter(raw, true, true, GLOBE_PAIR, LEGACY_STRIP_TARGET);
        assertEquals(GLOBE_PAIR, out,
                "Legacy strip and B-9 append must compose: EVERYTHING non-globe stripped "
                        + "(vanilla and datapack alike), glacial pair appended -- on a legacy cap "
                        + "the glacial carvers are the ONLY carving, at indices 0..1");
    }

    @Test
    void bothLegsOnAllVanillaLegacyCapGivesOnlyTheGlacialPair() {
        List<String> out = GlacialCarverLaw.filter(VANILLA_TRIO, true, true, GLOBE_PAIR, LEGACY_STRIP_TARGET);
        assertEquals(GLOBE_PAIR, out,
                "A legacy-cap barrens-land seed chunk with the flag on carves ONLY the glacial pair "
                        + "(the legacy cap never had vanilla carving to preserve)");
    }

    // --- Unkeyed/direct holders strip too (the cancel killed them) ---------------------------------

    @Test
    void unkeyedHoldersStripOnLegacyCapsTheCancelKilledThemToo() {
        // The mixin's predicate treats unkeyed (direct/inline) holders as strip targets: the
        // retired HEAD cancel suppressed them along with everything else, so cancel-identity
        // requires they strip.
        List<String> raw = List.of("minecraft:cave", "direct-inline-holder");
        List<String> out = GlacialCarverLaw.filter(raw, true, false, List.of(), LEGACY_STRIP_TARGET);
        assertTrue(out.isEmpty(),
                "Unkeyed direct holders must strip on legacy caps -- the old cancel killed ALL carving");
    }
}
