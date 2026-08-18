package com.example.globe.world;

import com.example.globe.util.ValueNoise2D;

/**
 * Deterministic, world-size-safe authority for coarse land-province classification.
 *
 * <p>Province authority operates <b>inside</b> latitude bands — it does not redefine bands.
 * Band selection occurs first (via {@link LatitudeBiomes#authoritativeLandBandIndex}),
 * then this helper classifies the block into a coarse humidity/moisture province
 * within that band.
 *
 * <p>All sampling is block-space continuous via {@link ValueNoise2D}, deterministic
 * from seed and coordinates, and uses the effective world radius (never hardcoded
 * regular-world constants). No chunk quantization, cell hashing, or grid dither.
 *
 * <p>This class is scaffolding only — it does not perform biome selection.
 */
public final class ProvinceAuthority {

    /**
     * Coarse humidity/moisture province categories.
     * Warm-side categories apply to tropical and subtropical bands.
     * Cold-side categories apply to temperate, subpolar, and polar bands.
     */
    public enum Province {
        WARM_DRY,
        WARM_MEDIUM,
        WARM_WET,
        COLD_DRY,
        COLD_MEDIUM,
        COLD_WET
    }

    // Band index constants (mirror LatitudeBiomes package-private values)
    private static final int BAND_SUBTROPICAL = 1;

    // Warm-side noise: re-uses the same salts/scales as LatitudeBiomes so province
    // boundaries align with existing picker climate signals.
    static final long WARM_OPENNESS_SALT = 0x7472_6F70_6F70_656EL;   // same as TROPICAL_OPENNESS_SALT
    static final int  WARM_OPENNESS_SCALE_BLOCKS = 1792;              // same as tropicalOpennessNoise scale
    static final long WARM_HUMIDITY_SALT = 0xDECAF_50B7_0001L;        // same as SUBTROPICAL_HUMIDITY_SALT
    static final int  WARM_HUMIDITY_SCALE_BLOCKS = 1536;              // same as subtropicalHumidityNoise scale

    // Cold-side moisture: new dedicated noise layer at comparable scale.
    // Uses a fresh salt so it does not alias with any existing noise field.
    static final long COLD_MOISTURE_SALT = 0x636F_6C64_6D6F_6973L;   // "coldmois"
    static final int  COLD_MOISTURE_SCALE_BLOCKS = 1600;

    // Province thresholds (tuned to produce three roughly equal-width bands in noise space).
    // The two warm thresholds are package-private ONLY so the policy suite can state the dry-fringe
    // truth table in the code's own numbers instead of copying 0.38 into a test and letting the two
    // drift. Values unchanged.
    static final double WARM_DRY_THRESHOLD = 0.38;
    static final double WARM_WET_THRESHOLD = 0.62;
    private static final double COLD_DRY_THRESHOLD = 0.38;
    private static final double COLD_WET_THRESHOLD = 0.62;

    // Earth-analog latitude wet-bias for the warm side.
    //
    // The raw moisture field above is latitude-independent, so WARM_DRY desert pockets
    // scatter uniformly across the whole warm zone — including the equator. That is
    // physically backwards: the equatorial ITCZ is the wettest place on Earth, and the
    // arid belt sits in the subtropics (~15-30deg). This bias adds moisture in the deep
    // tropics, fading smoothly to zero by the tropical/subtropical boundary, so the
    // equator reads "mostly humid, rare arid" while subtropical desert/badlands behaviour
    // is left untouched. Only the driest coherent noise pockets still punch through to
    // WARM_DRY near the equator, which yields rare, large, coherent arid pockets rather
    // than a desert-choked equator. Tune TROPICAL_WET_BIAS to trade equatorial arid share.
    static final double TROPICAL_LAT_END_DEG = 23.5; // == LatitudeBands.Band.SUBTROPICAL.lowDeg()
    static final double TROPICAL_WET_BIAS = 0.20;    // moisture units added at the equator

    // The dry fringe of the warm-medium belt (maintainer ruling, 2026-08-18: savanna is both
    // COUNTRIES and the ARID FRINGE).
    //
    // Savanna's real-world identity is the TRANSITION between arid and forest, and until the
    // savanna-country system landed it was the natural buffer standing between mesa/desert country
    // and the lush belt. Making WARM_MEDIUM resolve to minecraft:forest outside a country removed
    // savanna from exactly that position: measured over three vanilla seeds, lush neighbours of the
    // badlands family rose 156->350 / 189->288 / 33->131 while dry-transition neighbours fell
    // 894->658 / 788->454 / 619->343. Lush forest pressed straight against badlands.
    //
    // This width restores the buffer WITHOUT sampling neighbours and without a new noise field —
    // Article VI clean. Moisture is a smooth field and a WARM_DRY province is exactly the region
    // below WARM_DRY_THRESHOLD, so "effective moisture within WARM_DRY_FRINGE_WIDTH of that
    // threshold" already means "the shell immediately outside an arid province". The bias-inclusive
    // moisture is deliberately the one measured, so the fringe narrows by itself at the deep
    // equator and the "mostly humid equator" directive survives untouched.
    //
    // ATLAS-TUNED starting value. To retune, measure (a) badlands-family border adjacency and
    // (b) tropical savanna share, then move this one constant: WIDEN toward 0.10 if lush neighbours
    // still outnumber dry-transition neighbours at arid borders; NARROW toward 0.03 if tropical
    // savanna climbs past roughly a third of tropical land. Desk-derived scale at radius 10000:
    // 0.06 is a savanna belt roughly 300 blocks deep around each arid province, 0.04 roughly 200.
    // Nothing else moves with it — the fringe is additive on the MEDIUM side only, and WARM_DRY and
    // WARM_WET are untouched by construction.
    static final double WARM_DRY_FRINGE_WIDTH = 0.06;

    private final long seed;
    private final int effectiveRadius;

    /**
     * Constructs a province authority for a specific world.
     *
     * @param seed            world seed
     * @param effectiveRadius active world radius in blocks (must be &gt; 0)
     */
    public ProvinceAuthority(long seed, int effectiveRadius) {
        this.seed = seed;
        this.effectiveRadius = Math.max(1, effectiveRadius);
    }

    /**
     * Returns the world seed this authority was constructed with.
     */
    public long seed() {
        return seed;
    }

    /**
     * Returns the effective radius this authority was constructed with.
     */
    public int effectiveRadius() {
        return effectiveRadius;
    }

    /**
     * Classifies a block position into a coarse humidity/moisture province.
     *
     * <p>Pipeline order:
     * <ol>
     *   <li>Determine latitude band via {@link LatitudeBiomes#authoritativeLandBandIndex}</li>
     *   <li>Sample moisture/humidity noise (block-space continuous)</li>
     *   <li>Map to warm or cold province based on band</li>
     * </ol>
     *
     * @param blockX world X (blocks)
     * @param blockZ world Z (blocks)
     * @return the coarse province classification
     */
    public Province classify(int blockX, int blockZ) {
        int bandIndex = LatitudeBiomes.authoritativeLandBandIndex(blockX, blockZ, effectiveRadius);
        return classifyForBand(bandIndex, blockX, blockZ);
    }

    /**
     * Classifies using a pre-computed band index. Useful when the caller already
     * knows the band and wants to avoid re-computing it.
     *
     * @param bandIndex 0=tropical, 1=subtropical, 2=temperate, 3=subpolar, 4=polar
     * @param blockX    world X (blocks)
     * @param blockZ    world Z (blocks)
     * @return the coarse province classification
     */
    public Province classifyForBand(int bandIndex, int blockX, int blockZ) {
        if (bandIndex <= BAND_SUBTROPICAL) {
            return classifyWarm(blockX, blockZ);
        }
        return classifyCold(blockX, blockZ);
    }

    /**
     * Returns the band index for the given position, delegating to the authoritative
     * band computation. Exposed for callers that need both band and province.
     */
    public int bandIndex(int blockX, int blockZ) {
        return LatitudeBiomes.authoritativeLandBandIndex(blockX, blockZ, effectiveRadius);
    }

    // --- Warm-side province classification ---

    private Province classifyWarm(int blockX, int blockZ) {
        double moisture = warmMoisture(blockX, blockZ);

        if (moisture < WARM_DRY_THRESHOLD) {
            return Province.WARM_DRY;
        }
        if (moisture > WARM_WET_THRESHOLD) {
            return Province.WARM_WET;
        }
        return Province.WARM_MEDIUM;
    }

    /**
     * The EFFECTIVE warm-side moisture at a column: the composite noise signal plus the equatorial
     * wet-bias, i.e. exactly the number {@link #classifyWarm} thresholds against.
     *
     * <p>Extracted rather than duplicated on purpose. {@link #warmDryFringeForBand} has to know how
     * close a column sits to {@link #WARM_DRY_THRESHOLD}, and a second copy of this arithmetic —
     * even a correct one — is a province classification and a fringe classification that can drift
     * apart under one careless edit. There is one formula and both callers read it.
     *
     * <p>Package-private so the policy suite can build a truth table around the fringe width instead
     * of guessing where the fringe is.
     */
    double warmMoisture(int blockX, int blockZ) {
        // Combine openness and humidity into a single moisture signal.
        // High openness + low humidity → dry; low openness + high humidity → wet.
        double openness = ValueNoise2D.sampleBlocks(seed ^ WARM_OPENNESS_SALT, blockX, blockZ, WARM_OPENNESS_SCALE_BLOCKS);
        double humidity = ValueNoise2D.sampleBlocks(seed ^ WARM_HUMIDITY_SALT, blockX, blockZ, WARM_HUMIDITY_SCALE_BLOCKS);

        // Composite moisture signal: humidity pulls toward wet, openness pulls toward dry.
        // Both are in [0,1]. Average gives a smooth combined signal in [0,1].
        double moisture = (humidity + (1.0 - openness)) * 0.5;

        // Earth-analog latitude wet-bias: wettest at the equator, fading to neutral by the
        // tropical/subtropical boundary (see TROPICAL_WET_BIAS doc above).
        double latDeg = Math.min(90.0, Math.abs((double) blockZ) / (double) effectiveRadius * 90.0);
        if (latDeg < TROPICAL_LAT_END_DEG) {
            double wetFrac = smoothstep(1.0 - latDeg / TROPICAL_LAT_END_DEG);
            moisture += TROPICAL_WET_BIAS * wetFrac;
        }
        return moisture;
    }

    /**
     * Is this column in the DRY FRINGE of the warm-medium belt — the shell of WARM_MEDIUM that
     * hugs an arid province?
     *
     * <p>True only for a column this authority would classify {@link Province#WARM_MEDIUM} whose
     * effective moisture is below {@code WARM_DRY_THRESHOLD + WARM_DRY_FRINGE_WIDTH}. WARM_DRY is
     * false because it is not MEDIUM; WARM_WET is false for the same reason and is untouched by
     * this predicate in every direction. See {@link #WARM_DRY_FRINGE_WIDTH} for why savanna claims
     * this shell regardless of country membership (maintainer ruling, 2026-08-18).
     *
     * @param blockX world X (blocks)
     * @param blockZ world Z (blocks)
     */
    public boolean warmDryFringe(int blockX, int blockZ) {
        int bandIndex = LatitudeBiomes.authoritativeLandBandIndex(blockX, blockZ, effectiveRadius);
        return warmDryFringeForBand(bandIndex, blockX, blockZ);
    }

    /**
     * {@link #warmDryFringe} with a pre-computed band index, mirroring
     * {@link #classifyForBand(int, int, int)} so a caller that already knows the band never pays
     * for it twice.
     *
     * @param bandIndex 0=tropical, 1=subtropical, 2=temperate, 3=subpolar, 4=polar
     */
    public boolean warmDryFringeForBand(int bandIndex, int blockX, int blockZ) {
        if (bandIndex > BAND_SUBTROPICAL) {
            return false; // cold side has no warm province at all
        }
        double moisture = warmMoisture(blockX, blockZ);
        if (moisture < WARM_DRY_THRESHOLD || moisture > WARM_WET_THRESHOLD) {
            return false; // WARM_DRY / WARM_WET — the fringe is additive on the MEDIUM side only
        }
        return moisture < WARM_DRY_THRESHOLD + WARM_DRY_FRINGE_WIDTH;
    }

    /** Hermite smoothstep on [0,1]; used for the latitude wet-bias ramp. */
    private static double smoothstep(double t) {
        if (t <= 0.0) {
            return 0.0;
        }
        if (t >= 1.0) {
            return 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }

    // --- Cold-side province classification ---

    private Province classifyCold(int blockX, int blockZ) {
        double moisture = ValueNoise2D.sampleBlocks(seed ^ COLD_MOISTURE_SALT, blockX, blockZ, COLD_MOISTURE_SCALE_BLOCKS);

        if (moisture < COLD_DRY_THRESHOLD) {
            return Province.COLD_DRY;
        }
        if (moisture > COLD_WET_THRESHOLD) {
            return Province.COLD_WET;
        }
        return Province.COLD_MEDIUM;
    }
}
