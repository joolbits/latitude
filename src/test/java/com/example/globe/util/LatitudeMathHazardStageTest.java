package com.example.globe.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM boundary tests for {@link LatitudeMath#hazardStageIndex} after the B-3-P3 polar warning
 * ladder re-anchor. The four thresholds ({@code POLAR_STAGE_{1,2,3,LETHAL}_PROGRESS}) were re-anchored
 * to P1's PolarHazardWindow milestones (progress = |z|/latitudeRadius = deg/90):
 * <ul>
 *   <li>STAGE_1 = 0.9444 -> 85 deg (snow onset)</li>
 *   <li>STAGE_2 = 0.9667 -> 87 deg (hazard/slowness onset)</li>
 *   <li>STAGE_3 = 0.9889 -> 89 deg (~blindness; ~1 deg lead before freeze death at 90)</li>
 *   <li>LETHAL  = 0.9967 -> 89.7 deg (freeze near-max)</li>
 * </ul>
 *
 * <p>{@code hazardStageIndex(border, z, progress)} decides purely on {@code progress} (the border/z
 * params are unused by the current body), so this runs in a plain JVM with a {@code null} border.
 *
 * <p>NB: the constants are 4-decimal and do NOT land bit-exactly on {@code deg/90} at every boundary
 * (e.g. {@code 87/90 = 0.96666} is a hair BELOW the {@code 0.9667} STAGE_2 constant). Exact-boundary
 * assertions therefore key off the constants themselves; the degree-band assertions use samples chosen
 * safely INSIDE each band so this documented rounding never flips them.
 */
class LatitudeMathHazardStageTest {

    private static int stage(double progress) {
        // border + z are ignored by hazardStageIndex's body; a null border is safe in a pure JVM.
        return LatitudeMath.hazardStageIndex(null, 0.0, progress);
    }

    private static int stageForDeg(double deg) {
        return stage(deg / 90.0);
    }

    // ---- (1) Exact threshold boundaries, keyed off the re-anchored constants -------------------

    @Test
    void belowStage1IsZero() {
        assertEquals(0, stage(0.0));
        assertEquals(0, stage(0.90));
        assertEquals(0, stage(LatitudeMath.POLAR_STAGE_1_PROGRESS - 1e-6));
    }

    @Test
    void atStage1IsOne() {
        assertEquals(1, stage(LatitudeMath.POLAR_STAGE_1_PROGRESS));
        assertEquals(1, stage(LatitudeMath.POLAR_STAGE_2_PROGRESS - 1e-6));
    }

    @Test
    void atStage2IsTwo() {
        assertEquals(2, stage(LatitudeMath.POLAR_STAGE_2_PROGRESS));
        assertEquals(2, stage(LatitudeMath.POLAR_STAGE_3_PROGRESS - 1e-6));
    }

    @Test
    void atStage3IsThree() {
        assertEquals(3, stage(LatitudeMath.POLAR_STAGE_3_PROGRESS));
        assertEquals(3, stage(LatitudeMath.POLAR_STAGE_LETHAL_PROGRESS - 1e-6));
    }

    @Test
    void atLethalIsFour() {
        assertEquals(4, stage(LatitudeMath.POLAR_STAGE_LETHAL_PROGRESS));
        assertEquals(4, stage(1.0));
    }

    // ---- (2) Degree-band mapping, sampled safely inside each band ------------------------------

    @Test
    void degreeBandsMapToExpectedStages() {
        assertEquals(0, stageForDeg(80.0)); // well below snow onset -> fully explorable
        assertEquals(0, stageForDeg(84.0)); // just below 85 deg snow onset
        assertEquals(1, stageForDeg(86.0)); // 85-87: snow, no hazard yet
        assertEquals(2, stageForDeg(88.0)); // 87-89: slowness band
        assertEquals(3, stageForDeg(89.4)); // 89-89.7: danger (blindness), pre-freeze-death
        assertEquals(4, stageForDeg(89.9)); // >=89.7: freeze near-max
        assertEquals(4, stageForDeg(90.0)); // the pole
    }

    // ---- (3) Monotonic, no skipped stage across the approach -----------------------------------

    @Test
    void stageIsMonotonicNonDecreasingAndNeverSkipsAcrossApproach() {
        int previous = stageForDeg(80.0);
        for (int i = 1; i <= 1200; i++) {
            double deg = 80.0 + i * (12.0 / 1200.0); // 0.01 deg steps up to 92
            int current = stageForDeg(deg);
            assertTrue(current >= previous, "stage decreased at deg=" + deg);
            assertTrue(current - previous <= 1, "stage skipped a tier at deg=" + deg);
            previous = current;
        }
        assertEquals(4, previous); // clamps at the lethal tier past 90 deg
    }
}
