package com.example.globe.world;

/**
 * Final hard latitude law for arid-family surface biomes.
 *
 * <p>The normal selector uses noise-warped ramps near both edges of the subtropical arid belt.
 * This policy is the last line of defense after every late picker gate: it only handles the two
 * zones where those ramps already promise zero arid output.
 */
final class AridLatitudePolicy {
    enum Replacement {
        KEEP,
        SAVANNA,
        PLAINS
    }

    private AridLatitudePolicy() {
    }

    static Replacement replacementFor(
            boolean aridFamily,
            int blockZ,
            int effectiveRadius,
            double tropicalAridLimitDeg,
            double polewardAridLimitDeg) {
        if (!aridFamily || effectiveRadius <= 0) {
            return Replacement.KEEP;
        }
        double latitudeDeg = Math.min(
                90.0,
                Math.abs((double) blockZ) * 90.0 / (double) effectiveRadius);
        if (latitudeDeg < tropicalAridLimitDeg) {
            return Replacement.SAVANNA;
        }
        if (latitudeDeg >= polewardAridLimitDeg) {
            return Replacement.PLAINS;
        }
        return Replacement.KEEP;
    }
}
