package com.example.globe.core.geo;

/**
 * The locked GeoAuthority "Inverted-Plate Continentality" parameter table (Phase 2).
 *
 * <p>All wavelengths are RADIUS-RELATIVE to the latitude radius R (zRadius), so plate/id-cell COUNT
 * and coastline complexity are size-invariant across itty..massive worlds; only block dimensions
 * scale. Defaults are the simulation-validated values from the design synthesis
 * (docs/design/geoauthority-design-20260703.md). A few tuning-critical knobs accept
 * {@code -Dlatitude.geoV2.*} overrides, mirroring ProvinceAuthority's system-property convention.
 */
public final class GeoAuthorityParams {

    private GeoAuthorityParams() {
    }

    // --- continentality FBM ---
    public static final double LC_RATIO = ratio("latitude.geoV2.lcRatio", 0.42);
    public static final double FBM_W1 = 0.55, FBM_W2 = 0.27, FBM_W3 = 0.13, FBM_W4 = 0.05;

    // --- Jacobian-safe domain warp (warpAmp/Lwarp = 0.10 => provably no fold) ---
    public static final double LWARP_RATIO = 0.60;
    public static final double WARP_AMP_RATIO = 0.06;

    // --- land01 soft coast half-width (field units, not blocks) ---
    public static final double COAST_W = 0.06;

    // --- plate (Worley) + id-cell grids ---
    public static final double LPLATE_RATIO = ratio("latitude.geoV2.lplateRatio", 0.24); // ~209 plates any size
    public static final double LID_RATIO = 0.08;                                          // ~1404 id-cells any size
    public static final double PLATE_BIAS_W = ratio("latitude.geoV2.plateBiasW", 0.34);

    // --- THE land-fraction knob (higher => less land); hard-clamped so land stays ~[0.28,0.44] ---
    public static final double SEA_LEVEL = clampSea(ratio("latitude.geoV2.seaLevel", 0.04));

    // --- projection-edge + pole ocean bias ---
    public static final double EDGE_START = 0.80, EDGE_STR = 1.30;
    public static final double POLE_START = 0.92, POLE_STR = 0.75;

    // --- derived-field scales ---
    public static final double SHELF_W_RATIO = 0.03;
    public static final double ARC_WL_RATIO = 0.05;
    public static final double ARC_SEAM_W = 0.12; // in Lplate units
    public static final double MTN_WL_RATIO = 0.18;
    public static final double COAST_CLAMP_LC_FRAC = 0.5; // coastDistance trust radius = 0.5*Lc
    public static final double COAST_BAND_LID_FRAC = 0.5; // id-gating band = 0.5*Lid

    // --- salts (distinct; longs XOR into the noise seed, ints are hash01 salts) ---
    public static final long S_WX = 0x67656F5F7761785FL;       // "geo_wax_"
    public static final long S_WZ = 0x67656F5F77617A5FL;       // "geo_waz_"
    public static final long S_C1 = 0x67656F5F636F6E31L;       // "geo_con1"
    public static final long S_C2 = 0x67656F5F636F6E32L;       // "geo_con2"
    public static final long S_C3 = 0x67656F5F636F6E33L;       // "geo_con3"
    public static final long S_C4 = 0x67656F5F636F6E34L;       // "geo_con4"
    public static final long S_ARC = 0x67656F5F6172635FL;      // "geo_arc_"
    public static final long S_MTN = 0x67656F5F6D746E5FL;      // "geo_mtn_"
    public static final long S_OROG = 0x67656F5F6F726F67L;     // "geo_orog"
    public static final int S_PLATEJIT_X = 0x50_4A_58;          // "PJX"
    public static final int S_PLATEJIT_Z = 0x50_4A_5A;          // "PJZ"
    public static final int S_PLATECONT = 0x50_43_4E;           // "PCN"

    private static double ratio(String prop, double def) {
        try {
            String v = System.getProperty(prop);
            return v == null ? def : Double.parseDouble(v);
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static double clampSea(double v) {
        return v < 0.0 ? 0.0 : (v > 0.10 ? 0.10 : v);
    }
}
