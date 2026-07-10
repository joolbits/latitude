package com.example.globe.core.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link CarveAwareLabels} (Phase 5 carve-aware ocean labels,
 * {@code docs/binder/ocean-label-investigation-20260709.md}).
 *
 * <p>The load-bearing contract is INERTNESS: the carve oracle
 * ({@code GeoTerrainBiasFunction.carveTargetYOrMax}) reports {@code +Infinity} for every "no carve
 * applies" case (flag semantics: land-intent, S==0, r==0, NoOp provider, any failure), and
 * {@link CarveAwareLabels#carvedToOcean} must NEVER relabel on that sentinel — that is what makes
 * the {@code latitude.terrainV2.carveAwareLabels} flag byte-identical whenever terrain biasing
 * isn't actually carving. The structure classifier must stay a conservative inclusion list
 * (fail-open) so ocean-native structures keep generating in the carved sea the flag creates.
 */
class CarveAwareLabelsTest {

    private static final int SEA_LEVEL = 63;

    // ---- (1) Inertness: the +Infinity "no carve" sentinel never relabels ---------------------

    @Test
    void infinityCarveTargetNeverConvertsToOcean() {
        // WHY: +Infinity is the oracle's "no active bias / land-intent / failure" fallback; if this
        // ever converted, the flag would relabel columns with terrain biasing off entirely.
        assertFalse(CarveAwareLabels.carvedToOcean(Double.POSITIVE_INFINITY, SEA_LEVEL));
    }

    @Test
    void nanCarveTargetFailsOpenToLand() {
        // WHY: NaN should be impossible, but a defensive caller must degrade to "keep the land
        // label", never to a spurious ocean relabel (NaN comparisons are false — verify, don't hope).
        assertFalse(CarveAwareLabels.carvedToOcean(Double.NaN, SEA_LEVEL));
    }

    // ---- (2) The seaLevel - 2 threshold, boundary-exact ---------------------------------------

    @Test
    void thresholdMatchesMirrorVetoHysteresisExactly() {
        // WHY: the relabel must use the SAME "< seaLevel - 2" hysteresis as the existing sunk-land
        // mirror veto, so shorelines/shelf-apron columns (carve target at/near the waterline) keep
        // their land label. seaLevel-2 itself must NOT convert (strict less-than) ...
        assertFalse(CarveAwareLabels.carvedToOcean(SEA_LEVEL - 2, SEA_LEVEL));
        assertFalse(CarveAwareLabels.carvedToOcean(SEA_LEVEL, SEA_LEVEL));
        // ... while anything strictly below it does.
        assertTrue(CarveAwareLabels.carvedToOcean(Math.nextDown((double) (SEA_LEVEL - 2)), SEA_LEVEL));
        assertTrue(CarveAwareLabels.carvedToOcean(SEA_LEVEL - 3, SEA_LEVEL));
    }

    @Test
    void deepTrenchTargetsConvert() {
        // WHY: the investigation's motivating case — carved trench floors (~Y39-45 on the live
        // recipe) must read as ocean so villages/rivers/dripstone stop presenting as land there.
        assertTrue(CarveAwareLabels.carvedToOcean(39.0, SEA_LEVEL));
        assertTrue(CarveAwareLabels.carvedToOcean(45.0, SEA_LEVEL));
    }

    // ---- (3) Structure classifier: conservative inclusion list, fail-open ---------------------

    @Test
    void clearlyLandOnlyStructuresAreCancellable() {
        // WHY: the belt+suspenders guard may only ever cancel structures that are unambiguously
        // land-bound — the village-in-ocean class from TEST 48.
        assertTrue(CarveAwareLabels.structureClearlyLandOnly("village_plains"));
        assertTrue(CarveAwareLabels.structureClearlyLandOnly("village_savanna"));
        assertTrue(CarveAwareLabels.structureClearlyLandOnly("pillager_outpost"));
        assertTrue(CarveAwareLabels.structureClearlyLandOnly("desert_pyramid"));
        assertTrue(CarveAwareLabels.structureClearlyLandOnly("jungle_temple"));
        assertTrue(CarveAwareLabels.structureClearlyLandOnly("mansion"));
        assertTrue(CarveAwareLabels.structureClearlyLandOnly("igloo"));
        assertTrue(CarveAwareLabels.structureClearlyLandOnly("swamp_hut"));
    }

    @Test
    void oceanNativeAndNeutralStructuresFailOpen() {
        // WHY: canceling ocean-native structures over carved sea would empty the very ocean this
        // flag creates; unknown/modded ids must also fail open (no cancel), mirroring
        // structureClimateMismatch's doctrine. NB: "ruined_portal" contains "ruin" — the inclusion
        // list must not be tricked by substring lookalikes.
        assertFalse(CarveAwareLabels.structureClearlyLandOnly("ocean_ruin_warm"));
        assertFalse(CarveAwareLabels.structureClearlyLandOnly("shipwreck"));
        assertFalse(CarveAwareLabels.structureClearlyLandOnly("monument"));
        assertFalse(CarveAwareLabels.structureClearlyLandOnly("buried_treasure"));
        assertFalse(CarveAwareLabels.structureClearlyLandOnly("ruined_portal"));
        assertFalse(CarveAwareLabels.structureClearlyLandOnly("ancient_city"));
        assertFalse(CarveAwareLabels.structureClearlyLandOnly("mineshaft"));
        assertFalse(CarveAwareLabels.structureClearlyLandOnly("some:modded_thing"));
        assertFalse(CarveAwareLabels.structureClearlyLandOnly(null));
    }
}
