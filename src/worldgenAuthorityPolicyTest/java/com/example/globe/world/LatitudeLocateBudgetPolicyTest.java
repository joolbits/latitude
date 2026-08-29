package com.example.globe.world;

public final class LatitudeLocateBudgetPolicyTest {
    private LatitudeLocateBudgetPolicyTest() {
    }

    public static void main(String[] args) {
        int surfaceStep = LatitudeLocateBudgetPolicy.surfaceHorizontalStep(6400, 32);
        assertEquals(128, surfaceStep, "default surface horizontal step");
        assertEquals(10_201,
                LatitudeLocateBudgetPolicy.worstCaseSamples(6400, surfaceStep, 1),
                "default surface sample bound");
        int exactFallbackStep = LatitudeLocateBudgetPolicy.surfaceExactFallbackHorizontalStep(6400, 32);
        assertEquals(400, exactFallbackStep, "default exact fallback horizontal step");
        int fallbackSamples = LatitudeLocateBudgetPolicy.worstCaseSamples(
                6400, exactFallbackStep, 1);
        assertEquals(1_089, fallbackSamples, "default exact fallback sample bound");
        assertTrue(
                fallbackSamples + LatitudeLocateBudgetPolicy.MAX_SURFACE_EXACT_VERIFICATIONS <= 1_345,
                "surface terrain-expensive checks exceeded their hard bound");

        for (int verticalSamples : new int[]{1, 6, 7, 8, 16, 384}) {
            int step = LatitudeLocateBudgetPolicy.threeDimensionalHorizontalStep(
                    6400, 32, verticalSamples);
            int samples = LatitudeLocateBudgetPolicy.worstCaseSamples(
                    6400, step, verticalSamples);
            assertTrue(samples <= LatitudeLocateBudgetPolicy.MAX_THREE_DIMENSIONAL_SAMPLES,
                    "3D budget exceeded for verticalSamples=" + verticalSamples + ": " + samples);
        }

        assertEquals(160_801,
                LatitudeLocateBudgetPolicy.worstCaseSamples(6_400, 32, 1),
                "tick-sliced wetland grid coverage");
        assertTrue(LatitudeLocateBudgetPolicy.MAX_WETLAND_LOCATE_TICK_NANOS == 8_000_000L,
                "wetland locate tick-time budget changed unexpectedly");
        assertEquals(4_096,
                LatitudeLocateBudgetPolicy.MAX_WETLAND_GRID_PROBES_PER_TICK,
                "wetland cheap-probe tick bound");
        assertEquals(1,
                LatitudeLocateBudgetPolicy.MAX_WETLAND_EXACT_PROBES_PER_TICK,
                "wetland terrain-probe tick bound");
        assertTrue(LatitudeLocateBudgetPolicy.MAX_BIOME_LOCATE_TICK_NANOS == 8_000_000L,
                "all-biome locate tick-time budget changed unexpectedly");
        assertEquals(4_096,
                LatitudeLocateBudgetPolicy.MAX_SURFACE_PREVIEW_PROBES_PER_TICK,
                "surface preview tick bound");
        assertEquals(8,
                LatitudeLocateBudgetPolicy.MAX_THREE_DIMENSIONAL_EXACT_PROBES_PER_TICK,
                "three-dimensional exact-probe tick bound");
        assertTrue(
                LatitudeLocateBudgetPolicy.worstCaseSamples(6_400, 128, 1)
                        + LatitudeLocateBudgetPolicy.worstCaseSamples(6_400, 400, 1)
                        == 11_290,
                "the surface progress route must cover its full finite preview and fallback plan");
        assertEquals(3_750,
                LatitudeLocateBudgetPolicy.fullWorldSearchRadius(0, 0, 3_750),
                "center-origin search reaches the complete playable radius");
        assertEquals(6_250,
                LatitudeLocateBudgetPolicy.fullWorldSearchRadius(2_500, -500, 3_750),
                "off-center search reaches the opposite playable edge");
        assertEquals(7_500,
                LatitudeLocateBudgetPolicy.fullWorldSearchRadius(-3_750, 3_750, 3_750),
                "edge-origin search reaches the far corner without becoming unbounded");

        // Nearest-result completion. A square spiral visits Chebyshev rings, so the first match is
        // not the nearest one; these pin the bound that turns first-match into nearest-match.
        assertEquals(0, LatitudeLocateBudgetPolicy.spiralRing(0, 0), "spiral origin is ring 0");
        assertEquals(3, LatitudeLocateBudgetPolicy.spiralRing(-3, 1), "ring is the Chebyshev radius");
        assertEquals(3, LatitudeLocateBudgetPolicy.spiralRing(2, -3), "ring ignores the shorter axis");

        // A ring-4 corner sits at 4*sqrt(2) ~= 5.66 rings, so rings 5 and 6 can still beat it and
        // must be scanned; this is exactly the sqrt(2) overshoot the completion pass removes.
        assertEquals(6,
                LatitudeLocateBudgetPolicy.nearestCompletionRingLimit(Math.sqrt(32.0) * 32.0, 32),
                "a ring-4 diagonal match must keep scanning through ring 6");
        assertEquals(5,
                LatitudeLocateBudgetPolicy.nearestCompletionRingLimit(128.0, 32),
                "an axis match at ring 4 still admits the quart-snap ring beyond it");
        assertEquals(1,
                LatitudeLocateBudgetPolicy.nearestCompletionRingLimit(0.0, 32),
                "a match on the origin ring still completes its own ring");
        assertTrue(
                LatitudeLocateBudgetPolicy.nearestCompletionRingLimit(2645.0, 32)
                        >= (int) Math.floor(2645.0 / 32.0),
                "the limit can never cut off a ring that could hold a nearer match");
        assertEquals(Integer.MAX_VALUE,
                LatitudeLocateBudgetPolicy.nearestCompletionRingLimit(100.0, 0),
                "a degenerate step must not bound the search");

        // The completion pass must stay finite: the bound is at most sqrt(2) rings beyond the
        // match, so it can never exceed roughly twice the scanned area.
        for (int ring : new int[]{1, 4, 37, 314}) {
            int diagonal = LatitudeLocateBudgetPolicy.nearestCompletionRingLimit(
                    Math.sqrt(2.0) * ring * 32.0, 32);
            assertTrue(diagonal <= (int) Math.ceil(ring * Math.sqrt(2.0)) + 1,
                    "completion limit must stay within sqrt(2) rings for ring " + ring
                            + " (was " + diagonal + ")");
        }

        // Sliver escape. While the only match in hand is a boundary sliver, the search may run
        // this bounded factor farther looking for a match that is genuinely inside its biome.
        assertTrue(LatitudeLocateBudgetPolicy.SLIVER_ESCAPE_DISTANCE_FACTOR == 2.0,
                "sliver escape window changed unexpectedly");
        assertEquals(
                LatitudeLocateBudgetPolicy.nearestCompletionRingLimit(1000.0, 32),
                LatitudeLocateBudgetPolicy.sliverEscapeRingLimit(500.0, 32),
                "a sliver's escape bound is the completion bound of twice its distance");
        assertTrue(
                LatitudeLocateBudgetPolicy.sliverEscapeRingLimit(500.0, 32)
                        >= LatitudeLocateBudgetPolicy.nearestCompletionRingLimit(500.0, 32),
                "the escape bound can never cut the search shorter than the sliver's own bound");
        assertEquals(Integer.MAX_VALUE,
                LatitudeLocateBudgetPolicy.sliverEscapeRingLimit(100.0, 0),
                "a degenerate step must not bound the escape window");
        assertEquals(1,
                LatitudeLocateBudgetPolicy.sliverEscapeRingLimit(0.0, 32),
                "an origin-cell sliver still completes its own ring");

        assertTrue(
                LatitudeLocateBudgetPolicy.allowsSwampProxyForTarget(true, false, 2, 1),
                "swamp-only locate must retain temperate swamp candidates");
        assertTrue(
                LatitudeLocateBudgetPolicy.allowsSwampProxyForTarget(false, true, 0, 1),
                "mangrove-only locate must retain tropical swamp-to-mangrove candidates");
        assertTrue(
                LatitudeLocateBudgetPolicy.allowsSwampProxyForTarget(false, true, 1, 1),
                "mangrove-only locate must retain subtropical swamp-to-mangrove candidates");
        assertFalse(
                LatitudeLocateBudgetPolicy.allowsSwampProxyForTarget(false, true, 2, 1),
                "mangrove-only locate must reject temperate swamp proxies");
        assertFalse(
                LatitudeLocateBudgetPolicy.allowsSwampProxyForTarget(false, true, 4, 1),
                "mangrove-only locate must reject polar swamp proxies");
        assertTrue(
                LatitudeLocateBudgetPolicy.allowsSwampProxyForTarget(true, true, 4, 1),
                "mixed swamp/mangrove locate must retain every swamp candidate");
        assertFalse(
                LatitudeLocateBudgetPolicy.allowsSwampProxyForTarget(false, false, 0, 1),
                "non-wetland targets cannot use the swamp proxy");

        assertTrue(
                LatitudeLocateBudgetPolicy.sourcePreviewCanBecomeWetland(
                        true, false, false, false, false, false),
                "a registry-preview swamp must receive exact terrain resolution");
        assertTrue(
                LatitudeLocateBudgetPolicy.sourcePreviewCanBecomeWetland(
                        false, true, false, false, false, false),
                "a registry-preview mangrove can resolve to either wetland identity");
        assertTrue(
                LatitudeLocateBudgetPolicy.sourcePreviewCanBecomeWetland(
                        false, false, true, false, false, false),
                "preview ocean must remain eligible because raised terrain can veto ocean authority");
        assertTrue(
                LatitudeLocateBudgetPolicy.sourcePreviewCanBecomeWetland(
                        false, false, false, true, false, false),
                "preview river must remain eligible because raised terrain can veto river authority");
        assertTrue(
                LatitudeLocateBudgetPolicy.sourcePreviewCanBecomeWetland(
                        false, false, false, false, true, false),
                "preview beach must remain eligible because surface height controls the beach shortcut");
        assertTrue(
                LatitudeLocateBudgetPolicy.sourcePreviewCanBecomeWetland(
                        false, false, false, false, false, true),
                "raw shoreline base must remain eligible even if the preview rewrites its biome identity");
        assertFalse(
                LatitudeLocateBudgetPolicy.sourcePreviewCanBecomeWetland(
                        false, false, false, false, false, false),
                "ordinary non-wetland land cannot become a wetland through the remaining terrain gates");

        int alreadyCoarse = LatitudeLocateBudgetPolicy.surfaceHorizontalStep(6400, 512);
        assertEquals(512, alreadyCoarse, "caller's coarser resolution must be preserved");
        assertEquals(625,
                LatitudeLocateBudgetPolicy.worstCaseSamples(6400, alreadyCoarse, 1),
                "coarser caller sample count");

        // Every spiral search must actually take part in nearest-result completion. Counting the
        // operations, not the identifiers, is what keeps a future job from quietly going back to
        // returning its first match.
        String locateSource;
        try {
            locateSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/example/globe/world/LatitudeBiomeLocateService.java"));
        } catch (Exception failure) {
            throw new AssertionError("unable to inspect the biome locate service", failure);
        }
        int spiralSites = occurrences(locateSource, "BlockPos.spiralAround(");
        assertEquals(4, spiralSites, "expected one spiral per locate phase");
        assertEquals(spiralSites + 1,
                occurrences(locateSource, "ringExceedsCandidateBound("),
                "every spiral phase must stop only once no nearer ring remains");
        assertEquals(spiralSites + 1,
                occurrences(locateSource, "beginConfirmation("),
                "every spiral phase must send its match through neighbourhood confirmation");
        assertEquals(3,
                occurrences(locateSource, "recordCandidate("),
                "a match may settle only through the confirmation resolver, as interior or sliver");
        assertEquals(3,
                occurrences(locateSource, "stepConfirmation();"),
                "every job must drain its pending confirmation through the budgeted step");
        assertEquals(0,
                occurrences(locateSource, "finish(Pair.of("),
                "a match must never be finished straight from the search loop");

        System.out.println("LatitudeLocateBudgetPolicyTest PASS");
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }
}
