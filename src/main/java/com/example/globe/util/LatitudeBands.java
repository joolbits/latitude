package com.example.globe.util;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.util.Mth;

public final class LatitudeBands {
    private static final List<String> CANONICAL_IDS = List.of(
            Band.TROPICAL.id(),
            Band.SUBTROPICAL.id(),
            Band.TEMPERATE.id(),
            Band.SUBPOLAR.id(),
            Band.POLAR.id());
    private static final Set<String> CANONICAL_ID_SET = Set.copyOf(CANONICAL_IDS);

    private LatitudeBands() {
    }

    public enum Band {
        TROPICAL("tropical", 0.0, 23.5),
        SUBTROPICAL("subtropical", 23.5, 35.0),
        TEMPERATE("temperate", 35.0, 50.0),
        SUBPOLAR("subpolar", 50.0, 66.5),
        POLAR("polar", 66.5, 90.0);

        private final String id;
        private final double lowDeg;
        private final double highDeg;

        Band(String id, double lowDeg, double highDeg) {
            this.id = id;
            this.lowDeg = lowDeg;
            this.highDeg = highDeg;
        }

        public String id() {
            return id;
        }

        public double lowDeg() {
            return lowDeg;
        }

        public double highDeg() {
            return highDeg;
        }

        public String displayName() {
            if (this == POLAR) return "Polar";
            if (this == SUBPOLAR) return "Subpolar";
            if (this == SUBTROPICAL) return "Subtropical";
            return Character.toUpperCase(id.charAt(0)) + id.substring(1);
        }
    }

    public static Band fromAbsoluteLatitudeDeg(double absLatDeg) {
        double clamped = Mth.clamp(absLatDeg, 0.0, 90.0);
        if (clamped < Band.SUBTROPICAL.lowDeg()) return Band.TROPICAL;
        if (clamped < Band.TEMPERATE.lowDeg()) return Band.SUBTROPICAL;
        if (clamped < Band.SUBPOLAR.lowDeg()) return Band.TEMPERATE;
        if (clamped < Band.POLAR.lowDeg()) return Band.SUBPOLAR;
        return Band.POLAR;
    }

    public static Band fromCanonicalId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (Band band : Band.values()) {
            if (band.id.equals(normalized)) {
                return band;
            }
        }
        return null;
    }

    public static boolean isCanonicalBandId(String raw) {
        if (raw == null) {
            return false;
        }
        return CANONICAL_ID_SET.contains(raw.trim().toLowerCase(Locale.ROOT));
    }

    public static List<String> canonicalIds() {
        return CANONICAL_IDS;
    }

    /**
     * SINGLE canonical source for the player-facing zone word, shared by every surface that shows a zone
     * name (zone-enter title, persistent compass HUD, HUD Studio preview, etc). Before this existed, the
     * title path (GlobeWarningOverlay) and the compass HUD (CompassHud) each hardcoded their own word list
     * and had drifted apart ("Tropical"/"Subtropical" on the title vs "Tropics"/"Subtropics" on the HUD,
     * confirmed live via screenshot -- e.g. the zone-enter title reads "Subpolar 50°S" and the
     * compass HUD reads "50°S, 164°E Subpolar", so those two already agreed; only TROPICAL and
     * SUBTROPICAL had diverged). The title's vocabulary wins: Tropical / Subtropical / Temperate /
     * Subpolar / Polar -- i.e. exactly {@link Band#displayName()}.
     *
     * <p>Accepts the raw canonical zone key as used across the codebase: a {@link Band} enum name
     * ("TROPICAL", "SUBTROPICAL", ...), OR the display-only "EQUATOR" sub-zone (a live/HUD-only refinement
     * of Tropical that worldgen and the zone-enter title never distinguish from Tropical -- see
     * {@code LatitudeMath.LatitudeZone}). Unknown/null keys fall back to Temperate, matching prior
     * behavior of both duplicated switches this replaces.
     */
    public static String displayNameForZoneKey(String canonicalKey) {
        if (canonicalKey == null) {
            return Band.TEMPERATE.displayName();
        }
        String normalized = "EQUATOR".equals(canonicalKey) ? Band.TROPICAL.name() : canonicalKey;
        try {
            return Band.valueOf(normalized).displayName();
        } catch (IllegalArgumentException e) {
            return canonicalKey;
        }
    }
}
