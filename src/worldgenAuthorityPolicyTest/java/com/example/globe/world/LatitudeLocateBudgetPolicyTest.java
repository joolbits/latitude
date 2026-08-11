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

        System.out.println("LatitudeLocateBudgetPolicyTest PASS");
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
