package com.example.globe.core.geo;

/**
 * Phase 5 carve-aware ocean labels (ocean-label investigation 2026-07-09): the small PURE decision
 * helpers shared by every consumer of the {@code latitude.terrainV2.carveAwareLabels} flag — the
 * pick() twins' label relabel, the surface-cave clamp's floor-aware "near surface" judgment, and the
 * structure-guard belt+suspenders. Pure Java, no Minecraft imports (Core Logic layer per
 * {@code docs/porting/PORTABILITY_ARCHITECTURE.md}), so the threshold and the land-only structure
 * classification are unit-testable without a game runtime.
 */
public final class CarveAwareLabels {

    /**
     * The ocean-label threshold: a column is carved-to-sea iff its carve target sits meaningfully
     * below sea level ({@code carveTargetY < seaLevel - 2} — the same 2-block hysteresis the existing
     * sunk-land mirror veto uses, so shorelines/shallow shelf-apron columns keep their land label).
     *
     * <p>Structurally inert on every "no carve" input: the oracle
     * ({@code GeoTerrainBiasFunction.carveTargetYOrMax}) returns {@code +Infinity} for land-intent,
     * S==0/r==0, or a not-(yet-)real provider, and {@code +Infinity < anything} is false. A NaN
     * (defensive, should be impossible) also compares false — fail-open to "land", never a relabel.
     */
    public static boolean carvedToOcean(double carveTargetY, int seaLevel) {
        return carveTargetY < seaLevel - 2;
    }

    /**
     * True only for structures that CLEARLY belong on land (village, outpost, pyramid/temple,
     * mansion, igloo, witch hut) — the set the carve-aware structure guard may cancel over carved
     * sea. Deliberately an INCLUSION list, mirroring {@code structureClimateMismatch}'s conservative
     * fail-open doctrine: anything not on the list (ocean ruins, shipwrecks, monuments, buried
     * treasure, ruined portals, ancient cities, modded/unknown ids) is left alone, so ocean-native
     * structures keep generating in the carved sea this flag creates.
     */
    public static boolean structureClearlyLandOnly(String structurePath) {
        if (structurePath == null) {
            return false;
        }
        String p = structurePath.toLowerCase(java.util.Locale.ROOT);
        return p.contains("village")
                || p.contains("outpost")
                || p.contains("pyramid")
                || p.contains("jungle_temple")
                || p.contains("mansion")
                || p.contains("igloo")
                || p.contains("swamp_hut");
    }

    private CarveAwareLabels() {
    }
}
