package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class GlobeClientState {
    public static boolean DEBUG_DISABLE_WARNINGS = false;

    private static boolean globeWorld;

    private static final double STAGE_WARN_PROGRESS = 0.88;
    private static final double STAGE_DANGER_PROGRESS = 0.91;
    private static final double STAGE_WHITEOUT_PROGRESS = 0.93;
    private static final double STAGE_LETHAL_PROGRESS = 0.98;

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

    private static double borderProgress(net.minecraft.world.border.WorldBorder border, double coord, boolean isX) {
        double center = isX ? border.getCenterX() : border.getCenterZ();
        double half = com.example.globe.util.LatitudeMath.halfSize(border);
        if (half <= 0.0) return 1.0;
        return clamp(Math.abs(coord - center) / half, 0.0, 1.0);
    }

    private static PolarStage polarStageForProgress(double progress) {
        if (progress >= STAGE_LETHAL_PROGRESS) return PolarStage.LETHAL;
        if (progress >= STAGE_WHITEOUT_PROGRESS) return PolarStage.DANGER;
        if (progress >= STAGE_DANGER_PROGRESS) return PolarStage.WARN_2;
        if (progress >= STAGE_WARN_PROGRESS) return PolarStage.WARN_1;
        return PolarStage.NONE;
    }

    private static EwStormStage ewStageForProgress(double progress) {
        if (progress >= STAGE_DANGER_PROGRESS) return EwStormStage.LEVEL_2;
        if (progress >= STAGE_WARN_PROGRESS) return EwStormStage.LEVEL_1;
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

    public static WarningState computeWarningState(ClientWorld world, PlayerEntity player) {
        if (DEBUG_DISABLE_WARNINGS) {
            return WarningState.NONE;
        }

        var border = world.getWorldBorder();

        double progressX = borderProgress(border, player.getX(), true);
        double progressZ = borderProgress(border, player.getZ(), false);

        PolarStage polar = polarStageForProgress(progressZ);
        EwStormStage ewVisual = ewStageForProgress(progressX);

        boolean ewTextWarn = progressX >= STAGE_WARN_PROGRESS;
        boolean ewTextDanger = progressX >= STAGE_DANGER_PROGRESS;
        EwStormStage ewTextStage = ewTextDanger ? EwStormStage.LEVEL_2 : (ewTextWarn ? EwStormStage.LEVEL_1 : EwStormStage.NONE);

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
        double progressZ = borderProgress(border, player.getZ(), false);
        return polarStageForProgress(progressZ);
    }

    public static EwStormStage computeEwStormStage(ClientWorld world, PlayerEntity player) {
        var border = world.getWorldBorder();
        double progressX = borderProgress(border, player.getX(), true);
        return ewStageForProgress(progressX);
    }

    public static float computeEwFogEnd(double x) {
        if (DEBUG_DISABLE_WARNINGS) {
            return -1.0f;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return -1.0f;
        }

        EwStormStage stage = computeEwStormStage(client.world, client.player);
        return switch (stage) {
            case LEVEL_1 -> 48.0f;
            case LEVEL_2 -> 10.0f;
            default -> -1.0f;
        };
    }

    private static float polarWhiteoutIntensity(ClientWorld world, PlayerEntity player) {
        var border = world.getWorldBorder();
        double progressZ = borderProgress(border, player.getZ(), false);
        PolarStage stage = polarStageForProgress(progressZ);

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

        double progressX = borderProgress(border, x, true);
        double progressZ = borderProgress(border, z, false);

        PolarStage polarStage = polarStageForProgress(progressZ);
        EwStormStage stormStage = ewStageForProgress(progressX);

        float poleSeverity = polarIntensityForStage(polarStage);
        float stormSeverity = stormIntensityForStage(stormStage);

        boolean poleCritical = progressZ >= STAGE_LETHAL_PROGRESS;
        boolean stormCritical = progressX >= STAGE_LETHAL_PROGRESS;

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

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
