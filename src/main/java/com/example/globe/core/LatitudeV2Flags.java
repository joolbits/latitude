package com.example.globe.core;

/**
 * Feature flags gating the Phase 2 (GeoAuthority), Phase 3 (ClimateAuthority), and Biome Consumer
 * slices of the Latitude 2.0 overhaul. All default to {@code false}.
 *
 * <p>{@link #GEO_V2_ENABLED} / {@link #CLIMATE_V2_ENABLED} gate whether each authority is
 * constructed and sampled at all (Phase 2/3: computed-and-discarded). {@link #BIOME_CONSUMER_V2_ENABLED}
 * is a separate, later flag: it gates whether the computed summaries actually CHANGE biome selection.
 * Kept distinct on purpose -- the geoV2/climateV2 flags have an established, tested contract from
 * Phases 2-3 ("computed and discarded, zero biome change"); overloading them to also mean "and drives
 * biomes" would silently break that contract. The consumer flag depends on its authority's flag also
 * being on (if geoV2 is off, there is no GeoSummary to consume no matter what this flag says).
 *
 * <p>{@link #BIOME_CONSUMER_V2_OCEAN_AUTHORITY_ENABLED} is a further, independently-gated sub-flag
 * (2026-07-04). The Biome Consumer slice's proof gate found that letting GeoAuthority's
 * {@code isOceanIntent} replace {@code OceanDistanceField} collapses live land fraction from
 * GeoAuthority's own calibrated ~39% to ~13% (see docs/binder/biome-consumer-slice-20260704.md) --
 * a terrain-integration gap (Phase 4), not a GeoAuthority miscalibration. Rather than gate that known-bad
 * behavior behind a code comment saying "don't flip this yet," it gets its OWN flag: with
 * {@code BIOME_CONSUMER_V2_ENABLED=true} alone, only the proven-safe ClimateAuthority land-family reroll
 * is active; the ocean-authority swap additionally requires this flag, so it cannot be enabled by
 * accident and stays clearly walled off until Phase 4 (or an explicit decision to revisit the ocean
 * composition logic) makes it safe to turn on by default.
 *
 * <p>Pure Java, no Minecraft imports -- this class belongs to the Core Logic layer per
 * {@code docs/porting/PORTABILITY_ARCHITECTURE.md}.
 *
 * <p><b>Testing note (sweeper audit #2 finding #27, 2026-07-05):</b> every flag below is
 * {@code static final}, read from {@code System.getProperty} exactly once at class-init -- correct
 * and zero-cost for the shipping default-off path (a real JVM {@code -D} at launch), but it means
 * {@code System.setProperty(...)} called AFTER this class has already loaded has no effect. A test
 * that sets a property post-load and expects the flag to flip will silently keep exercising the
 * flag-off path and can pass for the wrong reason -- exactly the failure class this sweep targets.
 * Flag-on proof/tests must set the property before this class is first touched (e.g. via a forked JVM
 * with {@code -D}, as the headless atlas runner does), not via {@code setProperty} mid-test.
 */
public final class LatitudeV2Flags {

    public static final boolean GEO_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.geoV2.enabled", "false"));

    public static final boolean CLIMATE_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.climateV2.enabled", "false"));

    public static final boolean BIOME_CONSUMER_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.biomeConsumerV2.enabled", "false"));

    public static final boolean BIOME_CONSUMER_V2_OCEAN_AUTHORITY_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.biomeConsumerV2.oceanAuthority.enabled", "false"));

    // --- Phase 4 (Terrain Integration Spike) flags -------------------------------------------------
    // These gate the one narrow density-function wrapper (GeoTerrainBiasFunction) that biases terrain
    // surface height toward GeoAuthority's continuous land/ocean field. Design:
    // docs/design/terrain-wrapper-design-20260705.md (locked r2). All default to the true no-op:
    // ENABLED=false means the wrapper is never installed (byte-identical flag-off); STRENGTH=0.0 is a
    // second belt-and-suspenders no-op (biased == base); OCEAN_STRENGTH_RATIO=1.0 is the symmetric form.
    //
    // The doubles are parsed defensively: a malformed -D degrades to the no-op default rather than
    // throwing at class-init (a class-init failure here would take down all worldgen). See design §3.

    /** Phase 4 install gate. Default false -> the terrain-bias wrapper is never installed. */
    public static final boolean TERRAIN_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.terrainV2.enabled", "false"));

    /** Phase 4 primary strength knob (the live-tuning knob). Default 0.0 -> installed-but-no-op. */
    public static final double TERRAIN_V2_STRENGTH =
            parseDoubleOrDefault(System.getProperty("latitude.terrainV2.strength"), 0.0);

    /** Phase 4 optional ocean-side asymmetry knob. Default 1.0 -> symmetric (land push == ocean push). */
    public static final double TERRAIN_V2_OCEAN_STRENGTH_RATIO =
            parseDoubleOrDefault(System.getProperty("latitude.terrainV2.oceanStrengthRatio"), 1.0);

    /**
     * Defensive double parse for the Phase 4 knobs: a {@code null} (property unset) or malformed value
     * degrades to {@code fallback} instead of throwing {@link NumberFormatException} at class-init.
     */
    private static double parseDoubleOrDefault(String raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private LatitudeV2Flags() {
    }
}
