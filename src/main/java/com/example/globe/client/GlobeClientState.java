package com.example.globe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;

public final class GlobeClientState {
    public static final boolean SUPPRESS_VANILLA_EW_BORDER = true;
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
    private static final EwPresentationPolicy.ShelterState EW_SHELTER_STATE =
            new EwPresentationPolicy.ShelterState();
    private static long cachedExposureTick = Long.MIN_VALUE;
    private static long cachedExposurePos = Long.MIN_VALUE;
    private static int cachedVisibleSkySamples = EwPresentationPolicy.SKY_SAMPLE_COUNT;
    private static final int EXPOSURE_RECOMPUTE_TICKS = 5;
    private static final int[][] EXPOSURE_OFFSETS = {
            {0, 0},
            {3, 0}, {-3, 0}, {0, 3}, {0, -3},
            {3, 3}, {3, -3}, {-3, 3}, {-3, -3},
            {5, 0}, {-5, 0}, {0, 5}, {0, -5}
    };

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

    public static double absoluteLatitudeDegrees(WorldBorder border, double z) {
        if (border == null) {
            return 0.0;
        }
        double radius = com.example.globe.util.LatitudeMath.halfSize(border);
        double normalized = Math.abs(z - border.getCenterZ()) / radius;
        return Math.max(0.0, Math.min(90.0, normalized * 90.0));
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

    private static int polarRank(PolarStage stage) {
        return switch (stage) {
            case NONE -> 0;
            case WARN_1 -> 1;
            case WARN_2 -> 2;
            case DANGER -> 3;
            case LETHAL -> 4;
        };
    }

    private static int ewRank(EwStormStage stage) {
        return switch (stage) {
            case NONE -> 0;
            case LEVEL_1 -> 1;
            case LEVEL_2 -> 2;
        };
    }

    public static EwStormStage ewTextStageForDistance(double distanceToBorder) {
        return switch (EwPresentationPolicy.warningStageRank(distanceToBorder)) {
            case 2 -> EwStormStage.LEVEL_2;
            case 1 -> EwStormStage.LEVEL_1;
            default -> EwStormStage.NONE;
        };
    }

    public static EwStormStage computeEwTextStage(ClientLevel world, Player player) {
        return ewTextStageForDistance(distanceToEwBorderBlocks(world.getWorldBorder(), player.getX()));
    }

    public static WarningState arbitrateWarning(PolarStage activePolar, EwStormStage ewStage) {
        PolarStage polar = activePolar != null ? activePolar : PolarStage.NONE;
        EwStormStage ew = ewStage != null ? ewStage : EwStormStage.NONE;
        var selection = PolarPresentationPolicy.arbitrateWarning(polarRank(polar), ewRank(ew));
        if (selection.stageRank() <= 0) {
            return WarningState.NONE;
        }
        return selection.polar()
                ? new WarningState(WarningType.POLAR, polar, selection.stageRank())
                : new WarningState(WarningType.STORM, ew, selection.stageRank());
    }

    public static WarningState computeWarningState(ClientLevel world, Player player) {
        var border = world.getWorldBorder();

        double progressZ = com.example.globe.util.LatitudeMath.hazardProgress(border, player.getZ());
        PolarStage polar = polarStageForProgress(border, player.getZ(), progressZ);

        double distToBorder = Math.min(Math.abs(player.getX() - border.getMinX()), Math.abs(border.getMaxX() - player.getX()));

        EwStormStage ewTextStage = ewTextStageForDistance(distToBorder);
        return arbitrateWarning(polar, ewTextStage);
    }

    public static PolarStage computePolarStage(ClientLevel world, Player player) {
        var border = world.getWorldBorder();
        double progressZ = com.example.globe.util.LatitudeMath.hazardProgress(border, player.getZ());
        return polarStageForProgress(border, player.getZ(), progressZ);
    }

    public static EwStormStage computeEwStormStage(ClientLevel world, Player player) {
        var border = world.getWorldBorder();
        double progressX = com.example.globe.util.LatitudeMath.hazardProgress(border, player.getX());
        return ewStageForProgress(progressX);
    }

    private static double distanceToEwBorderBlocks(WorldBorder border, double camX) {
        return EwPresentationPolicy.distanceToNearestBorder(
                border.getMinX(),
                border.getMaxX(),
                camX);
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
        return EwPresentationPolicy.warningStageRank(d);
    }

    public static float ewIntensity01(double x) {
        double d = distanceToEwBorderBlocks(x);
        return EwPresentationPolicy.fogIntensity(d);
    }

    public static int ewRenderDistanceChunks(int originalChunks, double playerX) {
        return ewRenderDistanceChunks(originalChunks, playerX, ewPresentationVisibility());
    }

    public static int ewRenderDistanceChunks(
            int originalChunks,
            double playerX,
            float presentationVisibility) {
        double visibility = Math.max(0.0, Math.min(1.0, presentationVisibility));
        double i = ewIntensity01(playerX) * visibility;
        if (i <= 0.0) return originalChunks;

        int minChunks = Math.min(3, originalChunks);
        int target = (int) Math.round(originalChunks + (minChunks - originalChunks) * i);
        return Math.max(minChunks, Math.min(originalChunks, target));
    }

    public static double getDistanceToNearestEWBorder() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null) return Double.NaN;

        var cam = mc.gameRenderer.getMainCamera();
        if (cam == null) return Double.NaN;

        double x = cam.getPosition().x;

        double eastX = 3750.0;
        double westX = -3750.0;

        return Math.min(Math.abs(eastX - x), Math.abs(x - westX));
    }

    public static float computeEwFogEnd(double camX) {
        return computeEwFogEnd(camX, 64.0f);
    }

    public static float computeEwFogEnd(double camX, float baselineEnd) {
        return EwPresentationPolicy.fogEndDistance(
                distanceToEwBorderBlocks(camX),
                baselineEnd,
                ewPresentationVisibility());
    }

    public static float computeEwFogStart(double camX, float baselineStart) {
        return EwPresentationPolicy.fogStartDistance(
                distanceToEwBorderBlocks(camX),
                baselineStart,
                ewPresentationVisibility());
    }

    private static float polarWhiteoutIntensity(ClientLevel world, Player player) {
        var border = world.getWorldBorder();
        double progressZ = com.example.globe.util.LatitudeMath.hazardProgress(border, player.getZ());
        PolarStage stage = polarStageForProgress(border, player.getZ(), progressZ);

        if (stage == PolarStage.NONE) {
            return 0.0f;
        }
        if (stage == PolarStage.WARN_1) {
            return 0.2f;
        }
        if (stage == PolarStage.WARN_2) {
            return 0.5f;
        }
        if (stage == PolarStage.DANGER) {
            return 1.0f;
        }
        return 1.0f;
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
            resetEwPresentationState();
        }
    }

    public static void resetForDisconnect() {
        globeWorld = false;
        cachedEvalWorldTime = Long.MIN_VALUE;
        cachedEval = null;
        resetEwPresentationState();
    }

    public record Eval(boolean active, boolean surfaceOk, int absX, int absZ,
                      float polarFogSeverity, float polarWhiteoutSeverity,
                      float stormFogSeverity, float stormSevereSeverity, float stormOpaqueSeverity,
                      boolean poleCritical, boolean stormCritical) {
        public static final Eval INACTIVE = new Eval(false, false, 0, 0, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false, false);
    }

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
        updateEwShelterState(client, pos, worldTime);

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
        if (!globeWorld && !client.level.dimension().location().equals(Level.OVERWORLD.location())) {
            active = false;
        }

        if (!active) {
            cachedEval = new Eval(false, surfaceOk, absX, absZ, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false, false);
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
        double progressZ = com.example.globe.util.LatitudeMath.hazardProgress(border, z);

        PolarStage polarStage = polarStageForProgress(border, z, progressZ);
        EwStormStage stormStage = ewStageForProgress(progressX);

        float poleSeverity = polarIntensityForStage(polarStage);
        float stormSeverity = stormIntensityForStage(stormStage);

        boolean poleCritical = com.example.globe.util.LatitudeMath.hazardStageIndex(world.getWorldBorder(), player.getZ(), progressZ) >= 4;
        boolean stormCritical = com.example.globe.util.LatitudeMath.hazardStageIndexEW(progressX) >= 4;

        float polarFog = poleSeverity;
        float polarWhiteout = poleSeverity;

        float stormFog = stormSeverity;
        float stormSevere = stormSeverity;
        float stormOpaque = stormSeverity;

        cachedEval = new Eval(true, surfaceOk, absX, absZ, polarFog, polarWhiteout, stormFog, stormSevere, stormOpaque, poleCritical, stormCritical);
        return cachedEval;
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

    private static void updateEwShelterState(Minecraft client, BlockPos pos, long worldTime) {
        var world = client.level;
        if (world == null) {
            resetEwPresentationState();
            return;
        }
        if (distanceToEwBorderBlocks(world.getWorldBorder(), client.player.getX())
                > EwPresentationPolicy.ADVISORY_DISTANCE_BLOCKS) {
            resetEwPresentationState();
            return;
        }

        if (cachedExposureTick != Long.MIN_VALUE && worldTime < cachedExposureTick) {
            resetEwPresentationState();
        }

        long packed = pos.asLong();
        if (cachedExposureTick == Long.MIN_VALUE
                || packed != cachedExposurePos
                || worldTime - cachedExposureTick >= EXPOSURE_RECOMPUTE_TICKS) {
            cachedExposureTick = worldTime;
            cachedExposurePos = packed;
            cachedVisibleSkySamples = sampleVisibleSky(client, pos);
        }

        EW_SHELTER_STATE.update(pos.getY(), world.getSeaLevel(), cachedVisibleSkySamples);
    }

    private static int sampleVisibleSky(Minecraft client, BlockPos pos) {
        var world = client.level;
        if (world == null) {
            return EwPresentationPolicy.SKY_SAMPLE_COUNT;
        }
        BlockPos head = pos.above();
        int seen = 0;
        for (int[] offset : EXPOSURE_OFFSETS) {
            if (world.canSeeSky(head.offset(offset[0], 0, offset[1]))) {
                seen++;
            }
        }
        return seen;
    }

    public static float ewPresentationVisibility() {
        return EW_SHELTER_STATE.visibility();
    }

    public static boolean ewEpisodePaused() {
        return EW_SHELTER_STATE.pauseEpisode();
    }

    public static void resetEwPresentationState() {
        EW_SHELTER_STATE.reset();
        cachedExposureTick = Long.MIN_VALUE;
        cachedExposurePos = Long.MIN_VALUE;
        cachedVisibleSkySamples = EwPresentationPolicy.SKY_SAMPLE_COUNT;
    }

    public static float computePoleFogEnd(double z, float baselineEnd) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return -1.0f;
        }

        double absoluteLatitude = absoluteLatitudeDegrees(client.level.getWorldBorder(), z);
        return PolarPresentationPolicy.fogEndDistance(absoluteLatitude, baselineEnd);
    }

    public static float computePoleFogIntensity(double z) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return 0.0f;
        }

        double absoluteLatitude = absoluteLatitudeDegrees(client.level.getWorldBorder(), z);
        return PolarPresentationPolicy.fogIntensity(absoluteLatitude);
    }

    public static float computeEdgeFogEnd(double x) {
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
