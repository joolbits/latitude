package com.example.globe.core.climate;

/**
 * The locked "Fetch &amp; Lift" ClimateAuthority parameter table (Phase 3). Spatial scales are
 * radius-relative to R = zRadius (size-invariant across itty..massive, matching GeoAuthorityParams).
 * Defaults are the simulation-validated values (14/14 acceptance rows) from the design synthesis
 * (docs/design/climateauthority-design-20260703.md). A few tuning knobs accept
 * {@code -Dlatitude.climateV2.*} overrides.
 */
public final class ClimateAuthorityParams {

    private ClimateAuthorityParams() {
    }

    // --- temperature ---
    public static final double INSOL_EXP = ratio("latitude.climateV2.insolExp", 1.30);
    public static final double TB_HOT = 1.02;
    public static final double ALT_GAIN = 0.85;
    public static final double K_ALT = ratio("latitude.climateV2.kAlt", 0.60);
    public static final double ALPINE_ALT = 0.45;
    public static final double CONT_COOL = 0.14;
    public static final double CURR_TEMP = 0.10;
    public static final double TDITHER_AMP = 0.03;
    public static final double TDITHER_SCALE_R = 0.25;

    // --- continentality ---
    public static final double CONT_SCALE_R = 0.14;

    // --- wind ---
    public static final double ZONAL_FLOOR = 0.15;

    // --- precipitation ---
    public static final double P_FLOOR = 0.15;
    public static final double P_ITCZ = 0.80;
    public static final double P_STORM = 0.62;
    public static final double P_TROUGH = 0.55;
    public static final double POLAR_DRY_STR = 0.55;
    public static final double W_CONT = ratio("latitude.climateV2.wCont", 0.42);
    public static final double MOIST_ADD = 0.42;
    public static final double FETCH_SAT_R = 0.30;
    public static final double EASTCOAST_WET = 0.26;
    public static final double CUR_PRECIP_WARM = 0.18;
    public static final double CUR_PRECIP_COLD = 0.30;
    public static final double SUBS_DRY = 0.12;
    public static final double CONT_FLOOR = 0.28;
    public static final double ITCZ_FLOOR_LAT = 12.0;
    public static final double ITCZ_CONV_FLOOR = 0.58;
    public static final double ORO_W = 0.15;

    // --- fetch probes ---
    public static final double[] FETCH_OFFSETS_R = {0.06, 0.14, 0.26, 0.42};
    public static final double OPEN_OCEAN_SHELF_CUTOFF = 0.85;

    // --- orographic ---
    public static final double ORO_D_R = 0.05;
    public static final double ORO_SLOPE = 1.5;
    public static final double ORO_BARRIER_LO = 0.30;
    public static final double ORO_BARRIER_SPAN = 0.30;

    // --- current ---
    public static final double LCUR_R = 0.10;
    public static final double GYRE_LO = 12.0;
    public static final double GYRE_HI = 55.0;
    public static final double DRIFT_LO = 40.0;
    public static final double DRIFT_HI = 62.0;
    public static final double CURRENT_STR = 0.85;
    public static final double COASTAL_LAND_MAX_R = 0.12; // coastDistance < this*R => coastal

    // --- class thresholds ---
    // (T_COOL=0.48 from the original design synthesis was never wired into classifyBase -- removed
    // 2026-07-05 sweeper audit finding #24 rather than left as dead/misleading code. T_WARM=0.60 was
    // the old classifyBase's T-gate for the TROPICAL/SUBTROPICAL productive branches -- the root
    // cause of LESSONS L14's fallthrough bug -- and is no longer referenced now that band is the
    // exhaustive driver for T >= T_BOREAL_HI; removed for the same never-truly-load-bearing reason.)
    public static final double T_ICE = 0.10, T_TUNDRA = 0.20, T_BOREAL_HI = 0.42, T_HOT = 0.70;
    public static final double P_DESERT = 0.24, P_STEPPE = 0.38, P_WET = 0.50, P_RAINFOREST = 0.62;

    // --- ocean temp cutoffs (for OCEAN_* subclass) ---
    public static final double OCEAN_WARM_T = 0.75, OCEAN_LUKEWARM_T = 0.50, OCEAN_COLD_T = 0.25;

    // --- salt ---
    public static final long S_TEMP = 0x636C696D5F74656DL; // "clim_tem"

    private static double ratio(String prop, double def) {
        try {
            String v = System.getProperty(prop);
            return v == null ? def : Double.parseDouble(v);
        } catch (RuntimeException e) {
            return def;
        }
    }
}
