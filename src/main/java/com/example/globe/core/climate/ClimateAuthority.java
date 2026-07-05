package com.example.globe.core.climate;

import com.example.globe.core.geo.GeoAuthority;
import com.example.globe.core.geo.GeoSummary;

import java.util.ArrayList;
import java.util.List;

import static com.example.globe.core.climate.ClimateAuthorityParams.*;
import static com.example.globe.core.geo.GeoNoise.clamp;
import static com.example.globe.core.geo.GeoNoise.clamp01;
import static com.example.globe.core.geo.GeoNoise.smoothstep;
import static com.example.globe.core.geo.GeoNoise.valueNoise;

/**
 * ClimateAuthority — deterministic earthlike climate ("Fetch &amp; Lift", Phase 3).
 *
 * <p>Pure Java, zero Minecraft imports (Core Logic layer). Latitude sets ONLY temperature and wind
 * DIRECTION; precipitation is TRANSPORTED — a bounded upwind ray of {@link GeoAuthority} probes measures
 * open-ocean fetch, so west-coast-wet and continental-interior-dry emerge from geometry, not a latitude
 * stripe. Consumes Phase 2's {@link GeoSummary}; adds no biome-pack dependency (climateClass names
 * vanilla families first — see {@link ClimateClass}). See
 * {@code docs/design/climateauthority-design-20260703.md}.
 *
 * <p>Hot path per column: 1 center GeoSummary (usually supplied) + up to 8 bounded GeoAuthority probes
 * (4 upwind fetch, 2 downwind orographic, 2 lateral current) + 1 value-noise (temperature dither). No
 * flood-fill, no unbounded loop, no real-height probe, no static mutable state.
 */
public final class ClimateAuthority {

    // diagnostic flag strings
    public static final String F_OROGRAPHIC = "OROGRAPHIC_DIAGNOSTIC";
    public static final String F_MONSOON = "MONSOON_SEASONAL";
    public static final String F_SHORT_FETCH = "SHORT_FETCH_BLOCKED";
    public static final String F_CURRENT_INDET = "CURRENT_INDETERMINATE";
    public static final String F_DRY_STEPPE = "DRY_STEPPE_VANILLA_APPROX";
    public static final String F_ALPINE = "ALPINE_MODIFIER";
    public static final String F_MED_APPROX = "MEDITERRANEAN_VANILLA_APPROX";

    private final GeoAuthority geo;
    private final long seed;
    private final int zRadius;

    public ClimateAuthority(GeoAuthority geo) {
        this.geo = geo;
        this.seed = geo.seed();
        this.zRadius = geo.zRadius();
    }

    public GeoAuthority geo() {
        return geo;
    }

    /** Full climate summary for a column (samples the center GeoSummary itself). */
    public ClimateSummary sample(int x, int z) {
        return sample(x, z, geo.sample(x, z));
    }

    /** Full climate summary reusing a caller-provided center GeoSummary (shared-summary path). */
    public ClimateSummary sample(int x, int z, GeoSummary center) {
        final int R = zRadius;
        double a = Math.abs((double) z) / R * 90.0;
        List<String> flags = new ArrayList<>();

        // Wind unit vector for the probe rays (upwind = opposite the flow).
        double[] w = windRaw(z);
        double windX = w[0], windZ = w[1];
        double wh = Math.hypot(windX, windZ);
        double uX, uZ;
        if (wh < 1e-9) { uX = -1; uZ = 0; } else { uX = windX / wh; uZ = windZ / wh; }

        // --- fetch: 4 upwind probes (all sampled up front; the run only truncates arithmetic) ---
        GeoSummary[] fp = new GeoSummary[4];
        for (int k = 0; k < 4; k++) {
            int px = (int) Math.round(x - uX * FETCH_OFFSETS_R[k] * R);
            int pz = (int) Math.round(z - uZ * FETCH_OFFSETS_R[k] * R);
            fp[k] = geo.sample(px, pz);
        }
        double fetch;
        boolean blocked = false;
        if (center.isOceanIntent()) {
            fetch = FETCH_OFFSETS_R[3] * R;
        } else {
            fetch = 0.0;
            double prev = 0.0;
            boolean run = true;
            for (int k = 0; k < 4; k++) {
                double d = FETCH_OFFSETS_R[k] * R;
                boolean oc = fp[k].isOceanIntent() && fp[k].shelf01() < OPEN_OCEAN_SHELF_CUTOFF;
                if (run && oc) { fetch += d - prev; prev = d; }
                else if (run) { run = false; blocked = true; }
            }
        }
        if (blocked) flags.add(F_SHORT_FETCH);

        // --- current: 2 lateral probes, only in gyre/drift latitudes on coastal land ---
        // Sweeper audit 2026-07-05 (finding #29): currentModifierFor's warm-boundary drift term
        // (bump(a, DRIFT_LO, DRIFT_HI)) is documented as extending "up the mid-latitudes" to
        // DRIFT_HI=62, but this call site used to gate on GYRE_HI=55, so a in [55,62) always got
        // curr=0 live -- the disclosed drift band was dead in production. Gate on DRIFT_HI so the
        // documented behavior is actually reachable.
        double curr = 0.0;
        boolean coastalLand = !center.isOceanIntent() && center.coastDistanceBlocks() < COASTAL_LAND_MAX_R * R;
        if (a < DRIFT_HI && coastalLand) {
            int lc = (int) Math.round(LCUR_R * R);
            GeoSummary gW = geo.sample(x - lc, z), gE = geo.sample(x + lc, z);
            boolean oW = gW.isOceanIntent(), oE = gE.isOceanIntent();
            curr = currentModifierFor(a, oW, oE);
            if (boundarySign(oW, oE) == 0.0) flags.add(F_CURRENT_INDET);
        }

        // --- orographic DIAGNOSTIC: windward reuses fetch probes 0,1; 2 new downwind probes ---
        double mC = center.mountainIntent01();
        double mUp = Math.max(fp[0].mountainIntent01(), fp[1].mountainIntent01());
        GeoSummary d1 = geo.sample((int) Math.round(x + uX * ORO_D_R * R), (int) Math.round(z + uZ * ORO_D_R * R));
        GeoSummary d2 = geo.sample((int) Math.round(x + uX * 2 * ORO_D_R * R), (int) Math.round(z + uZ * 2 * ORO_D_R * R));
        double mDown = Math.max(d1.mountainIntent01(), d2.mountainIntent01());
        double barrier = clamp01((Math.max(mUp, mC) - ORO_BARRIER_LO) / ORO_BARRIER_SPAN);
        double lift = clamp01((mUp - mC) * ORO_SLOPE) * barrier;
        double shadow = clamp01((mUp - mDown) * ORO_SLOPE) * barrier;
        if (lift > 0.05 || shadow > 0.05) flags.add(F_OROGRAPHIC);

        return assemble(x, z, center, fetch, curr, lift, shadow, flags);
    }

    /**
     * Pure climate assembly from already-gathered scalar inputs + the center GeoSummary. Split out so
     * the acceptance table can be asserted directly with synthetic geography (no probing).
     */
    ClimateSummary assemble(int x, int z, GeoSummary center, double fetchBlocks, double currentSigned,
                            double windwardLift, double rainShadow, List<String> probeFlags) {
        final int R = zRadius;
        double phi = Math.abs((double) z) / R * 90.0;
        double a = phi;
        LatitudeBand band = bandFor(a);
        List<String> flags = new ArrayList<>(probeFlags);

        double[] w = windRaw(z);
        double windX = w[0], windZ = w[1];

        double cont = center.isOceanIntent() ? 0.0
                : smoothstep(0.0, CONT_SCALE_R * R, center.coastDistanceBlocks()) * center.land01();

        // --- temperature ---
        double tbase = clamp01(TB_HOT * Math.pow(Math.cos(a * Math.PI / 180.0), INSOL_EXP));
        // Sweeper audit 2026-07-05 (finding #19): mountainIntent01 is a land-oriented orographic
        // INTENT signal (GeoAuthority has no real submarine-terrain concept); applying it to ocean
        // columns let a nonzero intent near a plate boundary crash equatorial ocean temperature
        // toward OCEAN_FROZEN with no physical basis. Zero it for ocean columns.
        double alt = center.isOceanIntent() ? 0.0 : clamp01(center.mountainIntent01() * ALT_GAIN); // altitudeCooling01
        double coastProx = center.isOceanIntent() ? 0.0 : clamp01(1.0 - center.coastDistanceBlocks() / (0.10 * R));
        double dither = (valueNoise(seed ^ S_TEMP, x, z, Math.max(1, (int) Math.round(TDITHER_SCALE_R * R))) - 0.5) * TDITHER_AMP;
        double T = clamp01(tbase - alt * K_ALT - CONT_COOL * cont * smoothstep(20.0, 50.0, a)
                + currentSigned * coastProx * CURR_TEMP + dither);

        // --- precipitation (10-step transport pipeline) ---
        double fetchSupply = smoothstep(0.0, FETCH_SAT_R * R, fetchBlocks);
        double lobes = P_FLOOR + P_ITCZ * bump(a, -6, 20) + P_STORM * bump(a, 32, 68);
        double trough = P_TROUGH * bump(a, 15, 36) * clamp01(0.35 + 0.65 * cont) * (1.0 - 0.7 * fetchSupply);
        double pbase = (lobes - trough) * (1.0 - POLAR_DRY_STR * bump(a, 66.5, 90));
        pbase = clamp(pbase, 0.0, 1.2);
        double p = pbase * (1.0 - W_CONT * cont);
        double coldAtten = 1.0 + Math.min(0.0, currentSigned);
        p += MOIST_ADD * fetchSupply * coldAtten;
        if (currentSigned > 0) p += EASTCOAST_WET * currentSigned * bump(a, 18, 42) * fetchSupply;
        p *= (currentSigned >= 0 ? 1.0 + CUR_PRECIP_WARM * currentSigned : 1.0 + CUR_PRECIP_COLD * currentSigned);
        p -= SUBS_DRY * bump(a, 15, 36) * (0.5 + 0.5 * (1.0 - cont));
        p = Math.max(p, CONT_FLOOR * bump(a, 40, 66) * smoothstep(0.4, 0.9, cont));
        if (a < ITCZ_FLOOR_LAT) {
            double ww = 1.0 - smoothstep(8.0, 12.0, a);
            p = Math.max(p, ITCZ_CONV_FLOOR * ww);
        }
        p *= (1.0 + ORO_W * (windwardLift - rainShadow));
        double P = clamp01(p);

        // --- seasonality + class ---
        SeasonalityClass seas = seasonality(a, band, cont, fetchBlocks, currentSigned, center, R);
        if (seas == SeasonalityClass.MONSOON) flags.add(F_MONSOON);
        ClimateClass cls = classifyBase(T, P, band, seas, center, currentSigned);
        if (alt >= ALPINE_ALT) {
            ClimateClass stepped = alpineStep(cls);
            if (stepped != cls) { cls = stepped; flags.add(F_ALPINE); }
        }
        if (cls == ClimateClass.COLD_STEPPE) flags.add(F_DRY_STEPPE);
        if (cls == ClimateClass.MEDITERRANEAN) flags.add(F_MED_APPROX);

        return new ClimateSummary(phi, band, T, alt, cont, windX, windZ, fetchBlocks, P, windwardLift,
                rainShadow, currentSigned, seas.key(), cls.name(), List.copyOf(flags));
    }

    // --- wind (pure function of signed z) ---
    private double[] windRaw(int z) {
        double a = Math.abs((double) z) / zRadius * 90.0;
        double sgnZ = z >= 0 ? 1.0 : -1.0;
        double wT = bump(a, 0, 30), wW = bump(a, 30, 60), wP = bump(a, 60, 90);
        double s = Math.max(1e-6, wT + wW + wP);
        double zonal = (wT * -0.9 + wW * 0.9 + wP * -0.6) / s;
        if (Math.abs(zonal) < ZONAL_FLOOR) {
            zonal = ((a < 30 || a >= 60) ? -1.0 : 1.0) * ZONAL_FLOOR;
        }
        double windX = zonal;
        double windZ = sgnZ * (-0.30 * wT + 0.20 * wW - 0.10 * wP);
        return new double[]{windX, windZ};
    }

    static LatitudeBand bandFor(double a) {
        if (a < 23.5) return LatitudeBand.TROPICAL;
        if (a < 35.0) return LatitudeBand.SUBTROPICAL;
        if (a < 50.0) return LatitudeBand.TEMPERATE;
        if (a < 66.5) return LatitudeBand.SUBPOLAR;
        return LatitudeBand.POLAR;
    }

    /**
     * Basin-relative ocean-current modifier, SIGNED [-1,+1]: + = warm western-boundary current (ocean
     * to the EAST), − = cold eastern-boundary current (ocean to the WEST), 0 = isthmus/interior. The
     * sign comes purely from which lateral side is ocean (geometry), NOT from {@code sign(x)}; it is
     * hemisphere-independent (a=|lat|). Peaks at gyre latitudes; a warm-boundary poleward drift extends
     * it up the mid-latitudes.
     */
    static double currentModifierFor(double a, boolean oceanWest, boolean oceanEast) {
        double bsign = boundarySign(oceanWest, oceanEast);
        double gyre = bump(a, GYRE_LO, GYRE_HI);
        double drift = (bsign > 0) ? bump(a, DRIFT_LO, DRIFT_HI) : 0.0;
        return clamp(bsign * (0.7 * gyre + 0.3 * drift) * CURRENT_STR, -1.0, 1.0);
    }

    private static double boundarySign(boolean oceanWest, boolean oceanEast) {
        return (oceanEast && !oceanWest) ? 1.0 : (oceanWest && !oceanEast) ? -1.0 : 0.0;
    }

    /** Polynomial three-cell membership hump; 0 outside (lo,hi), peak 1 at the midpoint. */
    static double bump(double a, double lo, double hi) {
        if (a <= lo || a >= hi) return 0.0;
        double t = (a - lo) / (hi - lo);
        return 4.0 * t * (1.0 - t);
    }

    private SeasonalityClass seasonality(double a, LatitudeBand band, double cont, double fetch,
                                         double curr, GeoSummary center, int R) {
        if (center.isOceanIntent()) return SeasonalityClass.MARITIME;
        if (band == LatitudeBand.TROPICAL && cont < 0.4 && fetch > 0.15 * R && a <= 12) {
            return SeasonalityClass.EQUATORIAL;
        }
        boolean bigContinent = center.continentId() != -1 && cont >= 0.15 && cont <= 0.70;
        if ((band == LatitudeBand.TROPICAL || band == LatitudeBand.SUBTROPICAL)
                && bigContinent && fetch > 0.15 * R && curr > 0.05 && a >= 5 && a <= 32) {
            return SeasonalityClass.MONSOON;
        }
        if (a >= 30 && a < 45 && (band == LatitudeBand.SUBTROPICAL || band == LatitudeBand.TEMPERATE)
                && curr < -0.10 && cont < 0.5) {
            return SeasonalityClass.MEDITERRANEAN;
        }
        if (band == LatitudeBand.TEMPERATE && cont < 0.35 && fetch > 0.2 * R) {
            return SeasonalityClass.OCEANIC;
        }
        if ((band == LatitudeBand.TEMPERATE || band == LatitudeBand.SUBPOLAR) && cont >= 0.40) {
            return SeasonalityClass.CONTINENTAL;
        }
        if (band == LatitudeBand.SUBPOLAR) return SeasonalityClass.SUBPOLAR;
        if (band == LatitudeBand.POLAR) return SeasonalityClass.POLAR;
        if (band == LatitudeBand.TROPICAL && a > 12) return SeasonalityClass.TROPICAL_WETDRY;
        return SeasonalityClass.SEASONAL;
    }

    /**
     * Sweeper audit 2026-07-05 (LESSONS L14): the previous version of this cascade gated the
     * TROPICAL and SUBTROPICAL productive branches on {@code T >= T_WARM}, so any column in either
     * band whose temperature was cooled into {@code [T_BOREAL_HI, T_WARM)} -- reachable from
     * altitude cooling alone, with no unusual precipitation -- matched no explicit case and fell
     * through to a band-blind default: {@code HUMID_CONTINENTAL} for TROPICAL (masked into BOREAL by
     * the alpine step only by numerical coincidence -- the confirmed 18N "snowy taiga next to
     * jungle" defect), or {@code COLD_STEPPE} (snowy_plains) for SUBTROPICAL, UNMASKED, since that
     * gap is reachable below the alpine-step threshold.
     *
     * <p>Fix: band is now the exhaustive driver for every {@code T >= T_BOREAL_HI} column (a
     * {@code switch} over the 5-value {@link LatitudeBand} enum, so a future 6th band is a compile
     * error, not a silent fallthrough). Temperature only distinguishes hot-vs-cool WITHIN a band's
     * desert branch, matching how the original tropical productive block already worked for
     * rainforest-vs-savanna (precipitation-driven, not temperature-gated). A cooled column now
     * classifies via its band's normal precipitation-driven logic, and the existing
     * {@link #alpineStep} (unchanged) demotes it appropriately -- this is what the design's own
     * acceptance-table comment for the equatorial-alpine case ("ALPINE step-down from rainforest")
     * already intended, but the T-gate prevented from happening.
     */
    // Package-private (not private), matching bump()/currentModifierFor()'s existing convention in
    // this file, so ClimateAuthorityTest can exercise the classification cascade exhaustively and
    // directly (T/P/band/seasonality) instead of reverse-engineering geography inputs that hit exact
    // (T,P) targets through the full assemble() pipeline (sweeper audit 2026-07-05, LESSONS L14).
    ClimateClass classifyBase(double T, double P, LatitudeBand band, SeasonalityClass seas,
                                      GeoSummary center, double curr) {
        if (center.isOceanIntent()) {
            if (T > OCEAN_WARM_T) return ClimateClass.OCEAN_WARM;
            if (T >= OCEAN_LUKEWARM_T) return ClimateClass.OCEAN_LUKEWARM;
            if (T >= OCEAN_COLD_T) return ClimateClass.OCEAN;
            return ClimateClass.OCEAN_FROZEN;
        }
        // Universal cold tiers: below T_BOREAL_HI a column is never tropical/warm-looking, in any band.
        if (T < T_ICE) return ClimateClass.ICE_CAP;
        if (T < T_TUNDRA) return ClimateClass.TUNDRA;
        if (T < T_BOREAL_HI) return P < P_STEPPE ? ClimateClass.COLD_STEPPE : ClimateClass.BOREAL;
        // seasonality() only ever assigns MEDITERRANEAN for SUBTROPICAL/TEMPERATE at 30<=a<45, so
        // this cannot misfire for TROPICAL/SUBPOLAR/POLAR columns.
        if (seas == SeasonalityClass.MEDITERRANEAN) return ClimateClass.MEDITERRANEAN;

        boolean hot = T >= T_HOT;
        if (P < P_DESERT) return hot ? ClimateClass.HOT_DESERT : ClimateClass.COOL_DESERT;

        // T >= T_BOREAL_HI, P >= P_DESERT, not Mediterranean: band is the exhaustive driver.
        return switch (band) {
            case TROPICAL -> {
                if (P < P_STEPPE) yield ClimateClass.TROPICAL_SAVANNA;
                if (P >= P_RAINFOREST) yield ClimateClass.TROPICAL_RAINFOREST;
                if (seas == SeasonalityClass.TROPICAL_WETDRY) yield ClimateClass.TROPICAL_SAVANNA;
                yield seas == SeasonalityClass.MONSOON ? ClimateClass.TROPICAL_MONSOON : ClimateClass.TROPICAL_RAINFOREST;
            }
            case SUBTROPICAL -> {
                if (P < P_STEPPE) yield hot ? ClimateClass.SAVANNA : ClimateClass.COLD_STEPPE;
                if (P >= P_WET) yield hot ? ClimateClass.HUMID_SUBTROPICAL : ClimateClass.TEMPERATE_OCEANIC;
                yield hot ? ClimateClass.TROPICAL_SAVANNA : ClimateClass.HUMID_CONTINENTAL;
            }
            case TEMPERATE -> {
                if (P < P_STEPPE) yield ClimateClass.COLD_STEPPE;
                if (seas == SeasonalityClass.OCEANIC && P >= P_WET) yield ClimateClass.TEMPERATE_OCEANIC;
                if (seas == SeasonalityClass.CONTINENTAL) yield ClimateClass.HUMID_CONTINENTAL;
                yield P >= P_WET ? ClimateClass.TEMPERATE_OCEANIC : ClimateClass.HUMID_CONTINENTAL;
            }
            case SUBPOLAR -> P >= P_STEPPE ? ClimateClass.BOREAL : ClimateClass.COLD_STEPPE;
            case POLAR -> ClimateClass.TUNDRA;
        };
    }

    private static ClimateClass alpineStep(ClimateClass c) {
        return switch (c) {
            case TROPICAL_RAINFOREST, TROPICAL_MONSOON, HUMID_SUBTROPICAL, MEDITERRANEAN -> ClimateClass.TEMPERATE_OCEANIC;
            case SAVANNA, TROPICAL_SAVANNA, COOL_DESERT -> ClimateClass.COLD_STEPPE;
            case HOT_DESERT -> ClimateClass.COOL_DESERT;
            case TEMPERATE_OCEANIC, HUMID_CONTINENTAL -> ClimateClass.BOREAL;
            case BOREAL, COLD_STEPPE -> ClimateClass.TUNDRA;
            case TUNDRA -> ClimateClass.ICE_CAP;
            default -> c; // ICE_CAP and ocean classes unchanged
        };
    }
}
