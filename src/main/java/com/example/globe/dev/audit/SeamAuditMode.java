package com.example.globe.dev.audit;

import net.fabricmc.loader.api.FabricLoader;

/**
 * JVM-flag surface for the autonomous seam-audit harness.
 *
 * <p>All flags are read once at class-load; callers must pass the snapshot
 * (or reference the constants directly) into the audit pipeline. This class
 * deliberately exposes no mutators to keep audit mode an immutable, deterministic
 * boot switch rather than a runtime-toggled system.
 *
 * <p>Audit mode is dev-only: gated by {@link FabricLoader#isDevelopmentEnvironment()}.
 */
public final class SeamAuditMode {
    /** Master switch: {@code -Dlatitude.audit.seam=true} enables the harness. */
    public static final boolean ENABLED = Boolean.getBoolean("latitude.audit.seam");

    /**
     * Band pair in canonical form {@code <lower>-<higher>}, e.g. {@code temperate-subpolar}.
     * Must be an adjacent pair (validated at job start).
     */
    public static final String BAND_PAIR = System.getProperty("latitude.audit.bandPair", "temperate-subpolar");

    /** {@code center} | {@code low} | {@code high}. Synonyms accepted: seam/below/above. */
    public static final String EDGE = System.getProperty("latitude.audit.edge", "center");

    /** If true, the client schedules a stop after the bundle is written. */
    public static final boolean EXIT_WHEN_DONE = Boolean.getBoolean("latitude.audit.exitWhenDone");

    /** Samples per probe (small probe). Large probe doubles this, capped at 4000. */
    public static final int SAMPLES = Integer.getInteger("latitude.audit.samples", 300);

    /** Along-seam span in blocks (parallel to the boundary; X axis since seams are E-W). */
    public static final int ALONG_SEAM_SPAN = Integer.getInteger("latitude.audit.alongSeamSpan", 384);

    /** Cross-seam half-width in blocks (perpendicular to the boundary; Z axis). */
    public static final int CROSS_SEAM_HALF_WIDTH = Integer.getInteger("latitude.audit.crossSeamHalfWidth", 128);

    /** Readiness gate: minimum fraction of strip chunks at ChunkStatus.FULL before probes run. */
    public static final double LOADED_RATIO = parseDouble("latitude.audit.loadedRatio", 0.85);

    /** Client view-distance override during audit run. Restored on completion. */
    public static final int RENDER_DISTANCE = Integer.getInteger("latitude.audit.renderDistance", 12);

    /** Max server ticks to wait for readiness after strip prep queue drains. */
    public static final int READINESS_TIMEOUT_TICKS = Integer.getInteger("latitude.audit.readinessTimeoutTicks", 2400);

    /** Chunks force-loaded per server tick during strip prep. */
    public static final int CHUNKS_PER_TICK = Integer.getInteger("latitude.audit.chunksPerTick", 4);

    /** Ticks to wait after teleport before beginning strip prep (lets the player packet settle). */
    public static final int SETTLE_TICKS = Integer.getInteger("latitude.audit.settleTicks", 40);

    private SeamAuditMode() {
    }

    public static boolean isEnabled() {
        return ENABLED && FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    private static double parseDouble(String key, double fallback) {
        String v = System.getProperty(key);
        if (v == null) return fallback;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
