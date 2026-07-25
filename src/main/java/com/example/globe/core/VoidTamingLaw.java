package com.example.globe.core;

/**
 * S36 VOID TAMING -- pure gate/fill math for capping the SKY-BREACHING noise voids of the polar underground
 * (the S27 diagnostic's mega-void class: open sky visible from Y35 at 83S, bare-stone arches, no glacial
 * identity; census 2026-07-21 on fresh 82.8-85.1S country: 126 sky-breach components, largest 11,215
 * columns). Owner decisions (S36, binding): mechanism C = fill ONLY sky-connected hollows; onset 82 deg;
 * TAME not eliminate (a strength knob chokes partially; big caverns are legal if they don't breach);
 * the glacial-cave labyrinth + S35 trap deep-drop voids below the protect floor are untouchable.
 *
 * <p>Zero Minecraft imports (Core Logic layer, plain-JVM testable). The world-side consumer is
 * {@code terrain.VoidTamingFunction}, a {@code DensityFunction} wrapper on the Phase 4 terrain-wrapper
 * rails: it evaluates the router's own {@code depth} field (#9) as the underground/sky discriminator --
 * cave-noise-free, positive strictly below the smooth nominal surface, negative in open sky (verified
 * against the 26.2 jar: {@code depth = y_clamped_gradient(1.5@-64 -> -1.5@320) + offset}, slope exactly
 * -1/128 per block in vanilla) -- and feeds this class's gates.
 *
 * <p><b>The fill form.</b> {@code filled = base * (1 - K*g)} applied only where {@code base < 0} (air):
 * moves air density toward (and past, for {@code K*g > 1}) the solid threshold. Because density crosses to
 * solid only at 0, the fill BITES only where {@code K*g > 1} -- the usable strength range for visible
 * taming is therefore roughly {@code K in [1..2]}, with sub-1 values thinning the margin without
 * converting blocks (documented so a tuner does not misread K=0.5 as broken; sweep finding 8).
 *
 * <p><b>Band shape</b> (sweep-amended): the fill concentrates in the top of the underground -- full
 * strength from {@link #FEATHER_DV} to {@link #HOLD_DV} below the nominal surface, tapering to zero by
 * {@link #FADE_DV} -- so sky-necks cap while deep caverns survive ("tame"); a second feather approaches
 * the protect floor from above (sweep REQUIRED-FIX 4: without it, the hard Y-floor cut left a flat stone
 * shelf hanging at floor+1 inside any void spanning the floor). The band is defined in DEPTH-VALUE space
 * (dv), not block space (sweep RECOMMENDED 5): in vanilla {@code dv = blocks/128} exactly, and under a
 * datapack-overridden depth gradient (Terralith class) a differing slope merely rescales the band width
 * gently -- the sign split (the sky guarantee) is robust to any monotone depth.
 */
public final class VoidTamingLaw {

    private VoidTamingLaw() {
    }

    /** Vanilla depth slope: one block of Y = 1/128 depth-value. Used only to express the band constants in
     *  readable block terms; the law itself compares dv directly. */
    public static final double DV_PER_BLOCK = 1.0 / 128.0;

    /** Top feather: the first ~3 blocks below the nominal surface ramp 0 -> full, so a rebuilt ceiling
     *  merges into surrounding terrain instead of reading as a flush flat slab (architect (f)). */
    public static final double FEATHER_DV = 3.0 * DV_PER_BLOCK;

    /** Full-strength fill holds from the feather down to ~24 blocks below the nominal surface -- the
     *  sky-neck band (the breach throats the owner wants capped). */
    public static final double HOLD_DV = 24.0 * DV_PER_BLOCK;

    /** The fill fades to ZERO by ~40 blocks below the nominal surface: deep caverns keep their vaults --
     *  "tame, not eliminate" made geometric. */
    public static final double FADE_DV = 40.0 * DV_PER_BLOCK;

    /** Blocks of feather approaching the protect floor from above (sweep REQUIRED-FIX 4): the fill fades
     *  out over this many blocks above the floor instead of stopping dead in a horizontal plane. */
    public static final int FLOOR_FEATHER_BLOCKS = 10;

    /** Clamped smoothstep of {@code t} in [0,1] -- 0 below 0, 1 above 1, 3t^2-2t^3 between. */
    public static double smoothstep01(double t) {
        if (Double.isNaN(t) || t <= 0.0) {
            return 0.0;
        }
        if (t >= 1.0) {
            return 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Latitude gate in [0,1]: 0 at/below {@code onsetDeg}, smoothstep to 1 at/above {@code fullDeg}
     * (S28 GlacialBlend precedent: a density transition should read as coherent geography, never a
     * dead-straight E-W wall). A degenerate configuration ({@code fullDeg <= onsetDeg}) collapses to a
     * hard step at onset rather than dividing by zero.
     */
    public static double latGate(double absLatDeg, double onsetDeg, double fullDeg) {
        if (Double.isNaN(absLatDeg)) {
            return 0.0;
        }
        if (fullDeg <= onsetDeg) {
            return absLatDeg >= onsetDeg ? 1.0 : 0.0;
        }
        return smoothstep01((absLatDeg - onsetDeg) / (fullDeg - onsetDeg));
    }

    /**
     * Depth-band weight in [0,1] from the raw depth VALUE {@code dv} (router #9 at this cell). 0 at/above
     * the nominal surface ({@code dv <= 0} -- open sky is untouchable by construction), feathering in over
     * {@link #FEATHER_DV}, 1 through {@link #HOLD_DV}, fading to 0 by {@link #FADE_DV} (deep caverns
     * preserved).
     */
    public static double bandWeight(double dv) {
        if (Double.isNaN(dv) || dv <= 0.0 || dv >= FADE_DV) {
            return 0.0;
        }
        if (dv < FEATHER_DV) {
            return smoothstep01(dv / FEATHER_DV);
        }
        if (dv <= HOLD_DV) {
            return 1.0;
        }
        return smoothstep01((FADE_DV - dv) / (FADE_DV - HOLD_DV));
    }

    /**
     * Protect-floor feather in [0,1]: 0 at/below {@code protectFloorY} (the labyrinth is untouchable),
     * smoothstepping to 1 by {@code protectFloorY + featherBlocks} -- the sweep's REQUIRED-FIX 4, so the
     * fill tapers into the floor instead of planting a horizontal shelf one block above it.
     */
    public static double floorFeather(int blockY, int protectFloorY, int featherBlocks) {
        if (blockY <= protectFloorY) {
            return 0.0;
        }
        if (featherBlocks <= 0) {
            return 1.0;
        }
        return smoothstep01((blockY - protectFloorY) / (double) featherBlocks);
    }

    /**
     * The fill: {@code base * (1 - K*g)} for air ({@code base < 0}) with {@code g = latGate * bandWeight *
     * floorFeather} in [0,1] and {@code K = max(0, strength)}. Non-air, K<=0, or g<=0 return {@code base}
     * unchanged. The result is monotone toward solid: for K*g <= 1 it stays in {@code [base, 0]}; beyond
     * (K*g > 1) it goes positive -- actual stone -- bounded by {@code (K-1) * |base|}.
     */
    public static double fillDensity(double base, double strength, double gate01) {
        if (!(base < 0.0)) {
            return base; // solid (or NaN) -- the fill acts on air only
        }
        double k = Math.max(0.0, strength);
        if (k <= 0.0) {
            return base;
        }
        double g = gate01;
        if (Double.isNaN(g) || g <= 0.0) {
            return base;
        }
        if (g > 1.0) {
            g = 1.0;
        }
        return base * (1.0 - k * g);
    }
}
