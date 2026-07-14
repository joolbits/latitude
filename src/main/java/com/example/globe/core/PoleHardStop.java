package com.example.globe.core;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- the PURE math of the S2 Wide-world pole hard-stop clamp. Zero Minecraft
 * imports (Core Logic layer, unit-testable in a plain JVM). The MC-coupled part ({@code GlobeMod
 * .applyPoleHardStop}: the Mercator/creative/crossing-tick gates, the position-correction packet, the F1
 * dismount, the contact chime) is a thin shim; the engagement decision, the clamped position, and the
 * outward-velocity kill live here so they are provable -- including that the SAME velocity law applies to a
 * dismounted player's VEHICLE (the F1 fix: a horse/boat must not sail on through the wall the rider just hit).
 */
public final class PoleHardStop {

    private PoleHardStop() {
    }

    /** Half-block tolerance so sub-block float jitter exactly at the pole line never triggers a clamp/packet. */
    public static final double CLAMP_EPSILON = 0.5;

    /** Outcome of {@link #evaluate}: whether the clamp engages, the Z to snap back to (the pole line), and the
     *  outward sign (+1 south pole, -1 north pole) for velocity kills. */
    public record Decision(boolean engaged, double clampedZ, double outwardSign) {
    }

    /**
     * Evaluate the hard stop for a position: engages iff {@code |z - centerZ| > zRadius + epsilon} (beyond the
     * pole line by more than jitter). When engaged, {@code clampedZ} is the pole line on that side
     * ({@code centerZ ± zRadius}) and {@code outwardSign} is the poleward direction sign for
     * {@link #killOutwardZ}. A non-positive {@code zRadius} never engages (radius not resolved -- no line).
     */
    public static Decision evaluate(double z, double centerZ, int zRadius, double epsilon) {
        if (zRadius <= 0) {
            return new Decision(false, z, 0.0);
        }
        double dz = z - centerZ;
        if (Double.isNaN(dz) || Math.abs(dz) <= zRadius + epsilon) {
            return new Decision(false, z, 0.0);
        }
        double sign = dz >= 0.0 ? 1.0 : -1.0;
        return new Decision(true, centerZ + sign * zRadius, sign);
    }

    /**
     * Kill the OUTWARD (poleward) component of a Z velocity, preserving inward/lateral motion: on the south
     * side ({@code outwardSign > 0}) positive vz is zeroed; on the north side negative vz is zeroed. Applied
     * to the clamped PLAYER and -- the F1 fix -- to the vehicle they were dismounted from, so momentum toward
     * the pole dies on both bodies under one law.
     */
    public static double killOutwardZ(double vz, double outwardSign) {
        return outwardSign > 0.0 ? Math.min(vz, 0.0) : Math.max(vz, 0.0);
    }
}
