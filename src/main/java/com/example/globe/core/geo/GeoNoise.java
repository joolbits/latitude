package com.example.globe.core.geo;

/**
 * Pure noise/hash primitives for the Core Logic layer (zero Minecraft imports).
 *
 * <p>{@link #valueNoise} and {@link #hash01} are <b>verbatim</b> copies of
 * {@code com.example.globe.util.ValueNoise2D#sampleBlocks} and
 * {@code com.example.globe.util.LatitudeMath#hash01}. They are duplicated here on purpose: the util
 * versions live in a package that also imports Minecraft types, and the Core Logic layer must not
 * depend on Minecraft. GeoAuthority's calibration was validated against these exact formulas, so any
 * drift would silently move land fractions / component counts — {@code GeoNoiseParityTest} asserts
 * these stay byte-identical to the util originals.
 */
public final class GeoNoise {

    private GeoNoise() {
    }

    // --- 64-bit finalizer (== ValueNoise2D.mix64) ---
    public static long mix64(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }

    /** Non-negative 31-bit int from a mixed long; stable id helper. */
    public static int mix64toInt(long v) {
        return (int) (mix64(v) & 0x7fffffffL);
    }

    /**
     * Stable component id keyed by a canonical cell key, with land/ocean namespaces made <b>provably
     * disjoint by parity</b> (land = even, ocean = odd). Encoding the type as a XOR into the hash
     * input does NOT guarantee disjoint outputs (two different keys can collide across namespaces
     * after mixing); parity does.
     */
    public static int typedCompId(long key, boolean land) {
        int base = mix64toInt(key);
        return land ? (base & ~1) : (base | 1);
    }

    private static double toUnit(long h) {
        return ((h >>> 11) * 0x1.0p-53);
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double valueAt(long seed, int x, int z) {
        long h = seed ^ (((long) x) << 32) ^ (z & 0xffffffffL);
        return toUnit(mix64(h));
    }

    /** Verbatim copy of ValueNoise2D.sampleBlocks: smooth single-octave value noise in [0,1]. */
    public static double valueNoise(long seed, int blockX, int blockZ, int scaleBlocks) {
        double x = blockX / (double) scaleBlocks;
        double z = blockZ / (double) scaleBlocks;

        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);

        double tx = smooth(x - x0);
        double tz = smooth(z - z0);

        double v00 = valueAt(seed, x0, z0);
        double v10 = valueAt(seed, x0 + 1, z0);
        double v01 = valueAt(seed, x0, z0 + 1);
        double v11 = valueAt(seed, x0 + 1, z0 + 1);

        return lerp(lerp(v00, v10, tx), lerp(v01, v11, tx), tz);
    }

    /** Verbatim copy of LatitudeMath.hash01: deterministic hash to [0,1]. */
    public static double hash01(long seed, int x, int z, int salt) {
        long h = seed ^ ((long) x * 312289L) ^ ((long) z * 420559L) ^ (long) salt;
        h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
        h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
        h = h ^ (h >>> 33);
        return (h & Long.MAX_VALUE) / (double) Long.MAX_VALUE;
    }

    /** Packs two int cell coords into a long key (also fed to mix64 for ids). */
    public static long packCell(int ci, int cj) {
        return (((long) ci) << 32) ^ (cj & 0xffffffffL);
    }

    // --- small scalar helpers (pure) ---
    public static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** smoothstep(a,b,t): 0 below a, 1 above b, smooth Hermite between. */
    public static double smoothstep(double a, double b, double t) {
        if (a == b) {
            return t < a ? 0.0 : 1.0;
        }
        double u = clamp01((t - a) / (b - a));
        return u * u * (3.0 - 2.0 * u);
    }
}
