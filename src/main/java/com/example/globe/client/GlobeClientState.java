package com.example.globe.client;

import com.example.globe.GlobeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;

public final class GlobeClientState {
    public static boolean DEBUG_EW_WALL = true;
    public static boolean DEBUG_EW_SUPPRESS_VANILLA_BORDER = true;
    public static boolean DEBUG_EW_FOG = Boolean.parseBoolean(System.getProperty("latitude.debugEwFog", "false"));
    public static boolean DEBUG_EW_WALL_LINES = true;
    public static int DEBUG_TICK = 0;
    public static int DEBUG_TICK2 = 0;
    public static final boolean DEBUG_DISABLE_WARNINGS = Boolean.getBoolean("latitude.debugDisableWarnings");
    public static final boolean DEBUG_DISABLE_FOG = Boolean.getBoolean("latitude.debugDisableFog");
    // --- TEMP EW DIST DEBUG (remove after) ---
    private static long globe$ewLastLogMs = 0L;
    // -----------------------------------------

    private static long lastEwFogLogTick = Long.MIN_VALUE;
    private static long lastEwStateLogTick = Long.MIN_VALUE;
    private static int baseViewDistanceChunks = -1;
    private static int lastAppliedViewDistanceChunks = -1;
    private static float currentViewDistanceF = -1f;
    private static long lastViewDistanceApplyMs = 0L;
    private static boolean ewClampActive = false;

    private static final float EW_FOG_WARN_END = 96.0f;
    private static final float EW_FOG_DANGER_END = 64.0f;
    private static final float EW_FOG_SEVERE_END = 48.0f;
    private static final float EW_FOG_BLACKOUT_END = 32.0f;

    private static boolean globeWorld;

    private static long cachedEvalWorldTime = Long.MIN_VALUE;
    private static Eval cachedEval;

    public enum WarningType {
        NONE,
        POLAR,
        STORM
    }

    public enum PolarStage {
        NONE,
        WARN_1,
        WARN_2,
        DANGER,
        LETHAL
    }

    public enum EwStormStage {
        NONE,
        LEVEL_1,
        LEVEL_2
    }

    public record WarningState(WarningType type, Enum<?> stage, int severityRank) {
        public static final WarningState NONE = new WarningState(WarningType.NONE, PolarStage.NONE, 0);
    }

    private static double axisDistanceInsideBorder(net.minecraft.world.level.border.WorldBorder border, double coord, boolean isX) {
        double center = isX ? border.getCenterX() : border.getCenterZ();
        double radius = com.example.globe.util.LatitudeMath.halfSize(border);
        return radius - Math.abs(coord - center);
    }

    private static int borderRadiusBlocks(ClientLevel world) {
        return (int) Math.round(com.example.globe.util.LatitudeMath.halfSize(world.getWorldBorder()));
    }

    private static PolarStage polarStageForProgress(WorldBorder border, double z, double progress) {
        int stageIndex = com.example.globe.util.LatitudeMath.hazardStageIndex(border, z, progress);
        return switch (stageIndex) {
            case 1 -> PolarStage.WARN_1;
            case 2 -> PolarStage.WARN_2;
            case 3 -> PolarStage.DANGER;
            case 4 -> PolarStage.LETHAL;
            default -> PolarStage.NONE;
        };
    }

    private static EwStormStage ewStageForProgress(double progress) {
        int stageIndex = com.example.globe.util.LatitudeMath.hazardStageIndexEW(progress);
        if (stageIndex >= 2) return EwStormStage.LEVEL_2;
        if (stageIndex >= 1) return EwStormStage.LEVEL_1;
        return EwStormStage.NONE;
    }

    public static void debugLogEwFogOncePerSec(String hook, float ewEnd, double camX) {
        if (!DEBUG_EW_FOG) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        long time = client.level.getGameTime();
        if (time - lastEwFogLogTick < 20L) {
            return;
        }

        lastEwFogLogTick = time;
        var border = client.level.getWorldBorder();
        double progress = com.example.globe.util.LatitudeMath.hazardProgress(border, camX);
        EwStormStage stage = ewStageForProgress(progress);
        GlobeMod.LOGGER.info("[LAT_EW_FOG] hook={} camX={} stage={} progress={} ewEnd={}",
                hook, camX, stage, progress, ewEnd);
    }

    public static void debugLogEwFogStateOncePerSec(double camX) {
        if (!DEBUG_EW_FOG) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        if (!client.level.dimension().identifier().equals(Level.OVERWORLD.identifier())) {
            return;
        }

        long time = client.level.getGameTime();
        if (time - lastEwStateLogTick < 20L) {
            return;
        }
        lastEwStateLogTick = time;

        var border = client.level.getWorldBorder();
        double half = com.example.globe.util.LatitudeMath.halfSize(border);
        double dist = half - Math.abs(camX);
        double progress = com.example.globe.util.LatitudeMath.hazardProgress(border, camX);
        EwStormStage stage = ewStageForProgress(progress);
        float ewEnd = computeEwFogEnd(camX);

        GlobeMod.LOGGER.info("[LAT_EW_FOG_STATE] x={} radius={} dist={} stage={} progress={} ewEnd={}", camX, half, dist, stage, progress, ewEnd);
    }

    /**
     * Clamp client-side view distance during EW storms (Sodium-proof). Only tightens; restores when inactive.
     */
    public static void clampEwViewDistance(Minecraft client) {
        // Tripwire: no view-distance mutations allowed. Enable with -Dlatitude.debugEwClampTripwire=true if needed.
        if (Boolean.getBoolean("latitude.debugEwClampTripwire")) {
            GlobeMod.LOGGER.error("EW DISTANCE MUTATION PATH HIT");
        }
    }

    private static int polarRank(PolarStage stage) {
        return switch (stage) {
            case NONE -> 0;
            case WARN_1 -> 1;
            case WARN_2 -> 2;
            case DANGER -> 3;
            case LETHAL -> 4;
        };
    }

    private static double distanceToEwBorderBlocks(WorldBorder border, double camX) {
        // Anchored to the mod's INTENDED X radius (synced from the server), NOT the live border half -- so a
        // lerping / vandalized border can never slide the fog/prompt/re-arm/banner lines that read this (TEST
        // 86 finding). Falls back to the live half only before the handshake arrives (byte-identical Classic).
        return com.example.globe.util.LatitudeMath.distanceToEwEdgeIntended(border, camX);
    }

    /** The resolved per-world E/W-edge block geometry for the CURRENT client world (fog onset, prompt, re-arm,
     *  banner tiers, particle onset) -- all degree-anchored to the intended X radius. The one place the client
     *  turns "which world am I in" into the block distances every edge feature reads. */
    public static com.example.globe.core.EdgeGeometry.Resolved edgeGeometry(WorldBorder border) {
        return com.example.globe.core.EdgeGeometry.resolve(
                com.example.globe.util.LatitudeMath.intendedXRadius(border));
    }

    public static double ewWestX() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return Double.POSITIVE_INFINITY;
        var border = mc.level.getWorldBorder();
        double center = border.getCenterX();
        double radius = com.example.globe.util.LatitudeMath.halfSize(border);
        return center - radius;
    }

    public static double ewEastX() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return Double.POSITIVE_INFINITY;
        var border = mc.level.getWorldBorder();
        double center = border.getCenterX();
        double radius = com.example.globe.util.LatitudeMath.halfSize(border);
        return center + radius;
    }

    public static double ewDistToBorder(double camX) {
        double west = ewWestX();
        double east = ewEastX();
        if (Double.isInfinite(west) || Double.isInfinite(east)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.min(Math.abs(camX - west), Math.abs(east - camX));
    }

    public static double distanceToEwBorderBlocks(double x) {
        var client = Minecraft.getInstance();
        if (client == null || client.level == null) return Double.POSITIVE_INFINITY;
        return distanceToEwBorderBlocks(client.level.getWorldBorder(), x);
    }

    public static int ewWarningStage(double x) {
        double d = distanceToEwBorderBlocks(x);
        int stage;
        if (d <= 100.0) {
            stage = 2;
        } else if (d <= 175.0) {
            stage = 1;
        } else {
            stage = 0;
        }

        if (Boolean.getBoolean("latitude.debugEwWarn")) {
            GlobeMod.LOGGER.info("[LAT_EW_WARN] stage={} d={}", stage, d);
        }
        return stage;
    }

    public static float ewIntensity01(double x) {
        var client = Minecraft.getInstance();
        if (client == null || client.level == null) return 0.0f;
        var border = client.level.getWorldBorder();
        // Redesign 2026-07-12: the EW haze intensity (drives the render-distance reduction near the edge) now
        // ramps over the degree-anchored fog band -- onset at rampStartDist (~177.5 deg), full at the prompt
        // line -- instead of the old fixed 500->100 blocks. One geometry for fog, particles, banner and this.
        com.example.globe.core.EdgeGeometry.Resolved g = edgeGeometry(border);
        double d = distanceToEwBorderBlocks(border, x);
        double span = g.rampStartDist() - g.fogClimaxDist();
        if (!(span > 0.0) || d > g.rampStartDist()) return 0.0f;

        float t = (float) ((g.rampStartDist() - d) / span); // 0..1
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;

        // steeper right after the onset
        return (float) Math.pow(t, 0.55);
    }

    public static int ewRenderDistanceChunks(int originalChunks, double playerX) {
        double i = ewIntensity01(playerX);
        if (i <= 0.0) return originalChunks;

        int minChunks = 3;
        int target = (int) Math.round(originalChunks + (minChunks - originalChunks) * i);
        return Math.max(minChunks, Math.min(originalChunks, target));
    }

        public static float computeEwFogEnd(double camX) {
        if (DEBUG_DISABLE_WARNINGS) {
            return -1.0f;
        }
        float a = ewIntensity01(camX);
        if (a <= 0.0f) return -1.0f;

        float endFar = 64f;
        float endNear = 12f;
        return endFar + (endNear - endFar) * a;
    }

    private static float polarWhiteoutIntensity(ClientLevel world, Player player) {
        // B-3b: fog/whiteout intensity ramps CONTINUOUSLY over the ambient window [85,90] -- the same
        // 85->90 progress the ambient snow budget uses, so fog density thickens with the snowfall instead
        // of stepping through the stage ladder. 1.0 at 90 deg preserves the deep-end whiteout magnitude
        // the old DANGER/LETHAL stages produced at the pole. Consumers: computePoleWhiteoutFactor drives
        // the VISIBLE polar whiteout (PolarWhiteoutOverlayHud's HUD screen fill); computePoleFogEnd is
        // currently UNCONSUMED (retuned for future wiring only -- volumetric fog-renderer hookup is a
        // B-4 decision).
        var border = world.getWorldBorder();
        double absLatDeg = com.example.globe.util.LatitudeMath.absLatDegExact(border, player.getZ());
        return com.example.globe.core.PolarHazardWindow.fogIntensity(absLatDeg);
    }

    private GlobeClientState() {
    }

    public static boolean isGlobeWorld() {
        return globeWorld;
    }

    public static void setGlobeWorld(boolean value) {
        if (globeWorld != value) {
            globeWorld = value;
            cachedEvalWorldTime = Long.MIN_VALUE;
            cachedEval = null;
        }
    }

    public record Eval(boolean active, boolean surfaceOk, int absX, int absZ,
                      float polarFogSeverity, float polarWhiteoutSeverity,
                      float stormFogSeverity, float stormSevereSeverity, float stormOpaqueSeverity,
                      boolean poleCritical, boolean stormCritical, float exposure01) {
        public static final Eval INACTIVE = new Eval(false, false, 0, 0, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false, false, 0.0f);
    }

    // --- TEST 78: continuous enclosure estimate (exposure01) cache ---
    // exposure01 replaces the binary surfaceOk bit for PRESENTATION systems (wind muffle, whiteout alpha,
    // ambient particle budget). canSeeSky is a cheap heightmap lookup, but 13 of them every tick is wasteful,
    // so this recomputes only when the player's block position changes OR every EXPOSURE_RECOMPUTE_TICKS ticks.
    private static long cachedExposureTick = Long.MIN_VALUE;
    private static long cachedExposurePos = Long.MIN_VALUE;
    private static float cachedExposure01;
    private static final int EXPOSURE_RECOMPUTE_TICKS = 5;
    // 13 sky samples around the player's head: the center column + a ring at radius 3 (8 points) + the 4
    // cardinals at radius 5. Under a small overhead (Peetsa's flat lintel) the center is blocked but the ring
    // sees sky -> exposure ~0.9; in a sealed room all 13 are blocked -> 0; at a doorway some see sky -> partial.
    private static final int[][] EXPOSURE_OFFSETS = {
        {0, 0},
        {3, 0}, {-3, 0}, {0, 3}, {0, -3},
        {3, 3}, {3, -3}, {-3, 3}, {-3, -3},
        {5, 0}, {-5, 0}, {0, 5}, {0, -5}
    };

    public static Eval evaluate(Minecraft client) {
        if (client.player == null || client.level == null) {
            cachedEvalWorldTime = Long.MIN_VALUE;
            cachedEval = null;
            return Eval.INACTIVE;
        }

        long worldTime = client.level.getGameTime();
        if (cachedEval != null && cachedEvalWorldTime == worldTime) {
            return cachedEval;
        }

        cachedEvalWorldTime = worldTime;

        BlockPos pos = client.player.blockPosition();
        int absX = (int) Math.floor(Math.abs(client.player.getX()));
        int absZ = (int) Math.floor(Math.abs(client.player.getZ()));

        boolean surfaceOk = isSurfaceOk(client, pos);
        float exposure01 = computeExposure01(client, pos);

        boolean active = globeWorld;
        if (!active) {
            double half = com.example.globe.util.LatitudeMath.halfSize(client.level.getWorldBorder());
            active = Math.abs(half - 3750.0) < 1.0
                    || Math.abs(half - 5000.0) < 1.0
                    || Math.abs(half - 7500.0) < 1.0
                    || Math.abs(half - 10000.0) < 1.0
                    || Math.abs(half - 15000.0) < 1.0
                    || Math.abs(half - 20000.0) < 1.0;
        }

        // If server says it's a globe world, trust it explicitly and ignore client-side registry key quirks.
        if (!globeWorld && !client.level.dimension().identifier().equals(Level.OVERWORLD.identifier())) {
            active = false;
        }

        if (!active) {
            cachedEval = new Eval(false, surfaceOk, absX, absZ, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false, false, exposure01);
            return cachedEval;
        }

        var world = client.level;
        var player = client.player;
        if (world == null || player == null) {
            return Eval.INACTIVE;
        }

        var border = world.getWorldBorder();

        double x = player.getX();
        double z = player.getZ();

        double progressX = com.example.globe.util.LatitudeMath.hazardProgress(border, x);
        double progressZ = com.example.globe.util.LatitudeMath.hazardProgressZ(border, z);

        PolarStage polarStage = polarStageForProgress(border, z, progressZ);
        EwStormStage stormStage = ewStageForProgress(progressX);

        float poleSeverity = polarIntensityForStage(polarStage);
        float stormSeverity = stormIntensityForStage(stormStage);

        boolean poleCritical = com.example.globe.util.LatitudeMath.hazardStageIndex(world.getWorldBorder(), player.getZ(), progressZ) >= 4;
        boolean stormCritical = com.example.globe.util.LatitudeMath.hazardStageIndexEW(progressX) >= 4;

        if (DEBUG_DISABLE_WARNINGS) {
            cachedEval = new Eval(true, surfaceOk, absX, absZ, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false, false, exposure01);
            return cachedEval;
        }

        float polarFog = poleSeverity;
        float polarWhiteout = poleSeverity;

        float stormFog = stormSeverity;
        float stormSevere = stormSeverity;
        float stormOpaque = stormSeverity;

        cachedEval = new Eval(true, surfaceOk, absX, absZ, polarFog, polarWhiteout, stormFog, stormSevere, stormOpaque, poleCritical, stormCritical, exposure01);
        return cachedEval;
    }

    /**
     * B-5 item 2 (surface-only passage) + item 3 (no storm banners in a cave): is the player GENUINELY
     * underground -- below the surface layer AND with no sky overhead? Reuses the EXACT two ingredients the
     * enclosure sampler already keys on: {@link com.example.globe.core.PolarExposure#isBelowSurface} (the same
     * {@code seaLevel - 2} depth cut {@code sampleExposure01}/{@code isSurfaceOk} use) AND a sky check
     * ({@code canSeeSky(pos.above())}, the sampler's center sample). AND-ed so open low-lying terrain a couple
     * blocks under sea level (a shore, a shallow dip) is NOT mistaken for a cave -- only a genuinely roofed,
     * below-surface column counts. Under a tree/arch at the edge the player is at the surface (Y not below
     * sea-2) so this is false -- still the full experience, exactly as Peetsa asked.
     */
    public static boolean isDeepUnderground(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return false;
        }
        var world = client.level;
        BlockPos pos = client.player.blockPosition();
        if (!com.example.globe.core.PolarExposure.isBelowSurface(pos.getY(), world.getSeaLevel())) {
            return false;
        }
        return !world.canSeeSky(pos.above());
    }

    private static boolean isSurfaceOk(Minecraft client, BlockPos pos) {
        var world = client.level;
        if (world == null) {
            return false;
        }

        int sea = world.getSeaLevel();
        if (pos.getY() < sea - 2) {
            return false;
        }

        // Reliable surface check: must be exposed to the sky.
        // Using sky visibility avoids false-negatives from nearby blocks and is stable across time-of-day.
        return world.canSeeSky(pos.above());
    }

    /**
     * TEST 78: graded enclosure estimate in {@code [0,1]} for the PRESENTATION systems (wind muffle, whiteout
     * alpha, ambient particle budget) -- NOT the server hazard mechanics, which are untouched. Replaces the
     * binary {@code surfaceOk} single-column check so Peetsa's open freestanding arch (a flat lintel over open
     * terrain) reads as ~outdoors instead of fully "inside". Cached: recomputed only when the player's block
     * position changes or every {@link #EXPOSURE_RECOMPUTE_TICKS} ticks (13 cheap heightmap lookups, but not
     * every frame).
     */
    private static float computeExposure01(Minecraft client, BlockPos pos) {
        var world = client.level;
        if (world == null) {
            return 0.0f;
        }
        long now = world.getGameTime();
        long packed = pos.asLong();
        if (cachedExposureTick != Long.MIN_VALUE
                && packed == cachedExposurePos
                && (now - cachedExposureTick) < EXPOSURE_RECOMPUTE_TICKS) {
            return cachedExposure01;
        }
        cachedExposureTick = now;
        cachedExposurePos = packed;
        cachedExposure01 = sampleExposure01(client, pos);
        return cachedExposure01;
    }

    private static float sampleExposure01(Minecraft client, BlockPos pos) {
        var world = client.level;
        if (world == null) {
            return 0.0f;
        }
        // Deep underground: no surface storm presentation (mirrors isSurfaceOk's sea-level guard). All samples
        // would fail anyway, but this short-circuits the 13 lookups and keeps a lit near-surface cave shaft
        // from leaking a partial exposure.
        int sea = world.getSeaLevel();
        if (pos.getY() < sea - 2) {
            return 0.0f;
        }
        BlockPos head = pos.above();
        int seen = 0;
        for (int[] o : EXPOSURE_OFFSETS) {
            if (world.canSeeSky(head.offset(o[0], 0, o[1]))) {
                seen++;
            }
        }
        return com.example.globe.core.PolarExposure.fraction(seen, EXPOSURE_OFFSETS.length);
    }

    public static float computePoleFogEnd(double z) {
        if (DEBUG_DISABLE_WARNINGS) {
            return -1.0f;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return -1.0f;
        }

        float intensity = polarWhiteoutIntensity(client.level, client.player);
        intensity = Math.max(0.0f, Math.min(1.0f, intensity));
        if (intensity <= 0.001f) {
            return -1.0f;
        }

        float e = intensity * intensity;

        float startEnd = 96.0f;
        float endEnd = 2.0f;
        return startEnd + (endEnd - startEnd) * e;
    }

    public static float computeEdgeFogEnd(double x) {
        if (DEBUG_DISABLE_WARNINGS) {
            return -1.0f;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return -1.0f;
        }

        var border = client.level.getWorldBorder();
        double radius = com.example.globe.util.LatitudeMath.halfSize(border);
        double warnStart = Math.min(1500.0, Math.max(300.0, radius / 8.0));

        double distX = axisDistanceInsideBorder(border, x, true);
        if (distX > warnStart) {
            return -1.0f;
        }

        float t = (float) ((warnStart - distX) / warnStart);
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        if (t <= 0.001f) {
            return -1.0f;
        }
        float e = t * t;

        float startEnd = 96.0f;
        float endEnd = 2.0f;
        return startEnd + (endEnd - startEnd) * e;
    }

    public static float computePoleWhiteoutFactor(double z) {
        if (DEBUG_DISABLE_WARNINGS) {
            return 0.0f;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return 0.0f;
        }

        float intensity = polarWhiteoutIntensity(client.level, client.player);
        intensity = Math.max(0.0f, Math.min(1.0f, intensity));
        if (intensity <= 0.001f) {
            return 0.0f;
        }

        return intensity;
    }

    private static float polarIntensityForStage(PolarStage stage) {
        return switch (stage) {
            case WARN_1 -> 0.2f;
            case WARN_2 -> 0.5f;
            case DANGER -> 1.0f;
            case LETHAL -> 1.0f;
            default -> 0.0f;
        };
    }

    private static float stormIntensityForStage(EwStormStage stage) {
        return switch (stage) {
            case LEVEL_1 -> 0.45f;
            case LEVEL_2 -> 0.9f;
            default -> 0.0f;
        };
    }

}
