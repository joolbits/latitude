package com.example.globe.client;

import com.example.globe.GlobeMod;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class GlobeClientState {
    public static boolean DEBUG_DISABLE_WARNINGS = false;
    public static final boolean DEBUG_EW_FOG = Boolean.getBoolean("latitude.debugEwFog");

    private static long lastEwFogLogMs = 0;
    private static long lastEwStateLogTick = Long.MIN_VALUE;

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

    private static double axisDistanceInsideBorder(net.minecraft.world.border.WorldBorder border, double coord, boolean isX) {
        double center = isX ? border.getCenterX() : border.getCenterZ();
        double radius = com.example.globe.util.LatitudeMath.halfSize(border);
        return radius - Math.abs(coord - center);
    }

    private static int borderRadiusBlocks(ClientWorld world) {
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

    private static EwStormStage ewStageForIndex(int stageIndex) {
        if (stageIndex >= 2) return EwStormStage.LEVEL_2;
        if (stageIndex >= 1) return EwStormStage.LEVEL_1;
        return EwStormStage.NONE;
    }

    public static void debugLogEwFogOncePerSec(String hook, float ewEnd, double camX) {
        if (!Boolean.getBoolean("latitude.debugEwFog")) return;
        long now = System.currentTimeMillis();
        if (now - lastEwFogLogMs < 1000) return;
        lastEwFogLogMs = now;
        System.out.println("[LAT_EW_FOG] hook=" + hook + " ewEnd=" + ewEnd + " camX=" + camX);
    }

    public static void debugLogEwFogStateOncePerSec(double camX) {
        if (!DEBUG_EW_FOG) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        if (!client.world.getRegistryKey().getValue().equals(World.OVERWORLD.getValue())) {
            return;
        }

        long time = client.world.getTime();
        if (time - lastEwStateLogTick < 20L) {
            return;
        }
        lastEwStateLogTick = time;

        var border = client.world.getWorldBorder();
        double dist = distanceToEwBorderBlocks(border, camX);
        int stageIndex = ewWarningStage(camX);
        EwStormStage stage = ewStageForIndex(stageIndex);
        float ewEnd = computeEwFogEnd(camX);

        GlobeMod.LOGGER.info("[LAT_EW_FOG_STATE] x={} dist={} stage={} ewEnd={}", camX, dist, stage, ewEnd);
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

    public static WarningState computeWarningState(ClientWorld world, PlayerEntity player) {
        if (DEBUG_DISABLE_WARNINGS) {
            return WarningState.NONE;
        }

        var border = world.getWorldBorder();

        PolarStage polar = polarStageForProgress(border, player.getZ(), com.example.globe.util.LatitudeMath.hazardProgress(border, player.getZ()));

        int ewStageIndex = ewWarningStage(player.getX());
        boolean ewTextDanger = ewStageIndex >= 2;
        EwStormStage ewTextStage = ewStageForIndex(ewStageIndex);

        EwStormStage ewVisual = ewTextStage;

        int pr = polarRank(polar);
        int er = ewRank(ewTextStage);

        if (pr <= 0 && er <= 0) {
            return WarningState.NONE;
        }

        // Corner precedence (stable):
        // 1) polar lethal
        // 2) ew level 2
        // 3) polar warn/danger
        // 4) ew level 1
        if (polar == PolarStage.LETHAL) {
            return new WarningState(WarningType.POLAR, polar, pr);
        }
        if (ewTextDanger) {
            return new WarningState(WarningType.STORM, ewTextStage, er);
        }
        if (polar != PolarStage.NONE) {
            return new WarningState(WarningType.POLAR, polar, pr);
        }

        // Visual stage (fog/particles) can be stronger than text stage.
        if (ewVisual != EwStormStage.NONE && ewTextStage == EwStormStage.NONE) {
            ewTextStage = EwStormStage.LEVEL_1;
            er = ewRank(ewTextStage);
        }

        return new WarningState(WarningType.STORM, ewTextStage, er);
    }

    public static PolarStage computePolarStage(ClientWorld world, PlayerEntity player) {
        var border = world.getWorldBorder();
        double progressZ = com.example.globe.util.LatitudeMath.hazardProgress(border, player.getZ());
        return polarStageForProgress(border, player.getZ(), progressZ);
    }

    public static EwStormStage computeEwStormStage(ClientWorld world, PlayerEntity player) {
        return ewStageForIndex(ewWarningStage(player.getX()));
    }

    private static double distanceToEwBorderBlocks(WorldBorder border, double camX) {
        double center = border.getCenterX();
        double radius = border.getSize() * 0.5;
        return Math.max(0.0, radius - Math.abs(camX - center));
    }

    public static double distanceToEwBorderBlocks(double x) {
        var client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return Double.POSITIVE_INFINITY;
        return distanceToEwBorderBlocks(client.world.getWorldBorder(), x);
    }

    public static int ewWarningStage(double x) {
        double d = distanceToEwBorderBlocks(x);
        int stage;
        if (d <= 100.0) {
            stage = 2;
        } else if (d <= 500.0) {
            stage = 1;
        } else {
            stage = 0;
        }

        if (Boolean.getBoolean("latitude.debugEwWarn")) {
            System.out.println("[LAT_EW_WARN] stage=" + stage + " d=" + d);
        }
        return stage;
    }

    public static float ewIntensity01(double x) {
        double d = distanceToEwBorderBlocks(x);
        if (d > 500.0) return 0.0f;

        float t = (float) ((500.0 - d) / 500.0); // 0..1
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;

        // gradual early, stronger late
        return (float) Math.pow(t, 2.6);
    }

    public static float computeEwFogEnd(double camX) {
        if (DEBUG_DISABLE_WARNINGS) {
            return -1.0f;
        }
        float a = ewIntensity01(camX);
        if (a <= 0.0f) return -1.0f;

        float endFar = 96f;
        float endNear = 4f;
        return endFar + (endNear - endFar) * a;
    }

    private static float polarWhiteoutIntensity(ClientWorld world, PlayerEntity player) {
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
        }
    }

    public record Eval(boolean active, boolean surfaceOk, int absX, int absZ,
                      float polarFogSeverity, float polarWhiteoutSeverity,
                      float stormFogSeverity, float stormSevereSeverity, float stormOpaqueSeverity,
                      boolean poleCritical, boolean stormCritical) {
        public static final Eval INACTIVE = new Eval(false, false, 0, 0, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false, false);
    }

    public static Eval evaluate(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            cachedEvalWorldTime = Long.MIN_VALUE;
            cachedEval = null;
            return Eval.INACTIVE;
        }

        long worldTime = client.world.getTime();
        if (cachedEval != null && cachedEvalWorldTime == worldTime) {
            return cachedEval;
        }

        cachedEvalWorldTime = worldTime;

        BlockPos pos = client.player.getBlockPos();
        int absX = (int) Math.floor(Math.abs(client.player.getX()));
        int absZ = (int) Math.floor(Math.abs(client.player.getZ()));

        boolean surfaceOk = isSurfaceOk(client, pos);

        boolean active = globeWorld;
        if (!active) {
            double half = com.example.globe.util.LatitudeMath.halfSize(client.world.getWorldBorder());
            active = Math.abs(half - 3750.0) < 1.0
                    || Math.abs(half - 5000.0) < 1.0
                    || Math.abs(half - 7500.0) < 1.0
                    || Math.abs(half - 10000.0) < 1.0
                    || Math.abs(half - 15000.0) < 1.0
                    || Math.abs(half - 20000.0) < 1.0;
        }

        // If server says it's a globe world, trust it explicitly and ignore client-side registry key quirks.
        if (!globeWorld && !client.world.getRegistryKey().getValue().equals(World.OVERWORLD.getValue())) {
            active = false;
        }

        if (!active) {
            cachedEval = new Eval(false, surfaceOk, absX, absZ, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false, false);
            return cachedEval;
        }

        var world = client.world;
        var player = client.player;
        if (world == null || player == null) {
            return Eval.INACTIVE;
        }

        var border = world.getWorldBorder();

        double x = player.getX();
        double z = player.getZ();

        double progressZ = com.example.globe.util.LatitudeMath.hazardProgress(border, z);

        PolarStage polarStage = polarStageForProgress(border, z, progressZ);
        EwStormStage stormStage = ewStageForIndex(ewWarningStage(x));

        float poleSeverity = polarIntensityForStage(polarStage);
        float stormSeverity = stormIntensityForStage(stormStage);

        boolean poleCritical = com.example.globe.util.LatitudeMath.hazardStageIndex(world.getWorldBorder(), player.getZ(), progressZ) >= 4;
        boolean stormCritical = ewWarningStage(x) >= 2;

        if (DEBUG_DISABLE_WARNINGS) {
            cachedEval = new Eval(true, surfaceOk, absX, absZ, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false, false);
            return cachedEval;
        }

        float polarFog = poleSeverity;
        float polarWhiteout = poleSeverity;

        float stormFog = stormSeverity;
        float stormSevere = stormSeverity;
        float stormOpaque = stormSeverity;

        cachedEval = new Eval(true, surfaceOk, absX, absZ, polarFog, polarWhiteout, stormFog, stormSevere, stormOpaque, poleCritical, stormCritical);
        return cachedEval;
    }

    private static boolean isSurfaceOk(MinecraftClient client, BlockPos pos) {
        var world = client.world;
        if (world == null) {
            return false;
        }

        int sea = world.getSeaLevel();
        if (pos.getY() < sea - 2) {
            return false;
        }

        // Reliable surface check: must be exposed to the sky.
        // Using sky visibility avoids false-negatives from nearby blocks and is stable across time-of-day.
        return world.isSkyVisible(pos.up());
    }

    public static float computePoleFogEnd(double z) {
        if (DEBUG_DISABLE_WARNINGS) {
            return -1.0f;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return -1.0f;
        }

        float intensity = polarWhiteoutIntensity(client.world, client.player);
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return -1.0f;
        }

        var border = client.world.getWorldBorder();
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return 0.0f;
        }

        float intensity = polarWhiteoutIntensity(client.world, client.player);
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
