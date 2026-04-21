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
}
