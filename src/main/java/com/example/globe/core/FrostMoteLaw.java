package com.example.globe.core;

import java.util.Set;

/**
 * Phase 5 S25 SLUSH v1 (client half) -- the FROST MOTE budget/gate LAW for the glacial-caves water motes.
 * Pure Java, ZERO Minecraft imports (Core Logic layer, plain-JVM unit-testable) -- same discipline as
 * {@link SnowSparkleLaw} / {@link ParticleDensity}. Frost motes are a SUBTLE client particle effect drifting
 * just above {@code globe:glacial_caves} cave-pool surfaces -- delicate ice motes over near-freezing water
 * that "sell the slush", the water barely liquid (owner S25, TEST 117 flight 2026-07-20: "very small ice
 * blocks clustered together in the water to really show that it's cold... do you have any ideas?"). Crew 1
 * ships the WORLDGEN ice-floe speckles; this law is the CLIENT atmosphere half.
 *
 * <p><b>The ONLY intentional underground particle.</b> Every other Latitude particle system is a SURFACE /
 * sky-open phenomenon -- the ambient polar snowfall and the snow-glint sparkle both gate on the column being
 * sky-open (their per-position heightmap cover gate) precisely so nothing spawns under a roof (owner TEST 117
 * video: white glints falling inside caves at 83N was a BUG in the sparkle, now gated). Frost motes are the
 * deliberate exception: they exist BY DESIGN inside the roofed glacial caves, but only over WATER surfaces, so
 * they never read as the "snow indoors" the sky gates exist to prevent. Their placement is bounded three ways
 * -- the {@link #glacialCavesV1Enabled} flag, the {@link #isGlacialCavesBiome} biome gate, and the
 * {@link #isWaterMoteBlock} water-surface predicate -- none of which any other particle path shares.
 *
 * <p><b>The particle pick (SNOWFLAKE, the OPPOSITE call from the snow glint).</b> The glint forensics (GLINT
 * v5, TEST 113) rejected the near-white non-emissive {@code SNOWFLAKE} for the snowfields and swapped to the
 * bright emissive {@code FIREWORK} spark: on a WHITE snow field a dim near-white particle has almost no
 * contrast and reads as noise. The frost mote is the mirror-image case -- it drifts over DARK glacial-cave
 * water, where a near-white non-emissive {@code SNOWFLAKE} has HIGH contrast and reads as delicate frost, and
 * its faint gravity lets it settle gently onto the pool surface (exactly the "barely liquid" feel). So the
 * same contrast reasoning that made the glint pick FIREWORK makes the mote pick SNOWFLAKE. The client adapter
 * owns the actual MC particle type; this law only sizes the budget and gates placement.
 *
 * <p><b>No state / no accumulator.</b> {@link #moteBudget} is a pure function of its arguments -- a FIXED,
 * SPARSE per-spawn-tick budget (atmosphere, not weather), which the caller then scales by the vanilla
 * Particles setting exactly like the other ambient spawns, so the B-3b anti-backlog guard is untouched. Motes
 * are cave-interior by nature, so the caller does NOT apply the enclosure/exposure scale (that would zero them
 * in a sealed cave); this law is agnostic to that -- it only bounds the peak count.
 */
public final class FrostMoteLaw {

    private FrostMoteLaw() {
    }

    /** The target biome id the motes are gated to: {@code globe:glacial_caves}. Mirrors
     *  {@code LatitudeBiomes.GLACIAL_CAVES_ID} (that lives in the world layer, not importable from the pure
     *  core, so the string is mirrored here the same way {@link SnowSparkleLaw} mirrors its band constants; the
     *  alignment is pinned by test where the test classpath can load the world constant). */
    public static final String TARGET_BIOME_ID = "globe:glacial_caves";

    /** True iff {@code biomeId} is the glacial-caves biome the motes may drift in. Exact-match, NaN/null-safe
     *  (a null or foreign id yields false -> no motes, the conservative side). */
    public static boolean isGlacialCavesBiome(String biomeId) {
        return TARGET_BIOME_ID.equals(biomeId);
    }

    /** The water-surface block ids a mote may drift over -- the cave-pool SOURCE block. A glacial-cave pool
     *  surface is a {@code minecraft:water} block with air above (the client checks the air-above surface
     *  condition; this table is the block-family half). Crew 1's ice-floe speckles are ICE blocks, which are
     *  deliberately NOT in this set: motes drift over the WATER between the floes, not on the ice. */
    public static final Set<String> WATER_MOTE_BLOCKS = Set.of("minecraft:water");

    /** True iff {@code blockId} is a water-surface block a frost mote may drift over (see
     *  {@link #WATER_MOTE_BLOCKS}). Null/foreign -> false (no mote). */
    public static boolean isWaterMoteBlock(String blockId) {
        return blockId != null && WATER_MOTE_BLOCKS.contains(blockId);
    }

    /** Default SPARSE peak per-spawn-tick mote budget (in motes) BEFORE the caller's Particles scaling: a few
     *  at a time -- ATMOSPHERE, not weather (owner S25: "very small ice blocks... to show that it's cold").
     *  One-line dial (raise for denser). */
    public static final int DEFAULT_PEAK_BUDGET = 3;

    /** Horizontal + vertical sampling reach (blocks) around the player over which the client scatters mote
     *  candidates looking for a water surface -- "just above cave pool surfaces near the player". */
    public static final double SAMPLE_RADIUS = 12.0;
    /** Blocks BELOW the player's feet the client scans a candidate column for a water surface (cave pools sit
     *  at/below foot level). */
    public static final int SAMPLE_Y_BELOW = 6;
    /** Blocks ABOVE the player's feet the client scans a candidate column for a water surface (a pool a short
     *  step up). */
    public static final int SAMPLE_Y_ABOVE = 2;

    /** Min height (blocks) ABOVE the sampled water surface a mote spawns -- hugging the pool so it reads as
     *  drifting ON the water, not floating in the cave air. */
    public static final double MOTE_Y_MIN = 0.05;
    /** Max height (blocks) above the sampled water surface a mote spawns. A shallow 0.05-0.6 band keeps the
     *  whole cloud at pool-skim height. */
    public static final double MOTE_Y_MAX = 0.6;
    /** Faint lateral drift (blocks/tick, +-) -- the mote wanders slowly sideways; the SNOWFLAKE's own gentle
     *  gravity carries the downward settle onto the water, so no explicit downward term is needed. */
    public static final double MOTE_DRIFT_LATERAL = 0.01;

    /**
     * The FIXED, SPARSE per-spawn-tick mote budget (in motes): {@code peakBudget} when the glacial-caves flag
     * is on AND the observer is in the {@code globe:glacial_caves} biome, else 0. Clamped {@code >= 0}. A pure
     * function of its arguments -- no state / no accumulator; the caller scales this by the live Particles
     * setting before spawning (NOT by the enclosure estimate -- motes are cave-interior by design). This is a
     * flat gate, not a ramp: motes are steady cave atmosphere, so there is no latitude/weather curve here (the
     * biome itself only exists deep in the polar barrens band, which is the only latitude gate the effect
     * needs).
     *
     * @param glacialCavesFlagOn true iff {@code latitude.glacialCavesV1} is enabled (see
     *                           {@link #glacialCavesV1Enabled})
     * @param inGlacialCavesBiome true iff the observer's biome is {@link #TARGET_BIOME_ID}
     * @param peakBudget          the peak per-spawn-tick mote budget (see {@link #DEFAULT_PEAK_BUDGET})
     */
    public static int moteBudget(boolean glacialCavesFlagOn, boolean inGlacialCavesBiome, int peakBudget) {
        if (!glacialCavesFlagOn || !inGlacialCavesBiome || peakBudget <= 0) {
            return 0;
        }
        return peakBudget;
    }

    /** True iff the {@code latitude.glacialCavesV1} flag family is on -- the motes are gated on the same flag
     *  as the caves they inhabit ({@link LatitudeV2Flags#GLACIAL_CAVES_V1_ENABLED}), so flag-off there are no
     *  caves and, by construction, no motes. Read through this thin accessor so the pure law stays free of the
     *  flag-reading detail and the gate is unit-testable via {@link #moteBudget}. */
    public static boolean glacialCavesV1Enabled() {
        return LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED;
    }
}
