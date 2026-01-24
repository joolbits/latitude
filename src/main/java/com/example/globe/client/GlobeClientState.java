package com.example.globe.client;

import com.example.globe.GlobeRegions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class GlobeClientState {
    public static boolean DEBUG_DISABLE_WARNINGS = false;

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
        UNEASE,
        EFFECTS_I,
        EFFECTS_II,
        WHITEOUT_APPROACH,
        LETHAL,
        HOPELESS
    }

    public enum StormStage {
        NONE,
        WARNING,
        DANGER,
        EDGE_ABSOLUTE
    }

    public record WarningState(WarningType type, Enum<?> stage, int severityRank) {
        public static final WarningState NONE = new WarningState(WarningType.NONE, PolarStage.NONE, 0);
    }

    private static double axisDistanceInsideBorder(net.minecraft.world.border.WorldBorder border, double coord, boolean isX) {
        double center = isX ? border.getCenterX() : border.getCenterZ();
        double radius = border.getSize() * 0.5;
        return radius - Math.abs(coord - center);
    }

    private static int borderRadiusBlocks(ClientWorld world) {
        return (int) Math.round(world.getWorldBorder().getSize() / 2.0);
    }

    private static PolarStage polarStageFor(int radius, int distToZPole) {
        int unease = (int) Math.round(radius / 4.0);
        int e1 = (int) Math.round(radius / 6.0);
        int e2 = (int) Math.round(radius / 10.0);
        int whiteout = (int) Math.round(radius / 16.0);
        int lethal = (int) Math.round(radius / 24.0);
        int hopeless = (int) Math.round(radius / 32.0);

        if (distToZPole <= hopeless) return PolarStage.HOPELESS;
        if (distToZPole <= lethal) return PolarStage.LETHAL;
        if (distToZPole <= whiteout) return PolarStage.WHITEOUT_APPROACH;
        if (distToZPole <= e2) return PolarStage.EFFECTS_II;
        if (distToZPole <= e1) return PolarStage.EFFECTS_I;
        if (distToZPole <= unease) return PolarStage.UNEASE;
        return PolarStage.NONE;
    }

    private static StormStage stormStageFor(int radius, int distToXEdge) {
        int warn = (int) Math.round(radius / 5.0);
        int danger = (int) Math.round(radius / 12.0);
        int edge = (int) Math.round(radius / 30.0);

        if (distToXEdge <= edge) return StormStage.EDGE_ABSOLUTE;
        if (distToXEdge <= danger) return StormStage.DANGER;
        if (distToXEdge <= warn) return StormStage.WARNING;
        return StormStage.NONE;
    }

    private static int polarRank(PolarStage stage) {
        return switch (stage) {
            case NONE -> 0;
            case UNEASE -> 1;
            case EFFECTS_I -> 2;
            case EFFECTS_II -> 3;
            case WHITEOUT_APPROACH -> 4;
            case LETHAL -> 5;
            case HOPELESS -> 6;
        };
    }

    private static int stormRank(StormStage stage) {
        return switch (stage) {
            case NONE -> 0;
            case WARNING -> 1;
            case DANGER -> 5;
            case EDGE_ABSOLUTE -> 6;
        };
    }

    public static WarningState computeWarningState(ClientWorld world, PlayerEntity player) {
        if (DEBUG_DISABLE_WARNINGS) {
            return WarningState.NONE;
        }

        int radius = borderRadiusBlocks(world);
        int absX = (int) Math.floor(Math.abs(player.getX()));
        int absZ = (int) Math.floor(Math.abs(player.getZ()));

        int distToXEdge = radius - absX;
        int distToZPole = radius - absZ;

        PolarStage polar = polarStageFor(radius, distToZPole);
        StormStage storm = stormStageFor(radius, distToXEdge);

        int pr = polarRank(polar);
        int sr = stormRank(storm);

        if (pr <= 0 && sr <= 0) {
            return WarningState.NONE;
        }

        if (pr > sr) {
            return new WarningState(WarningType.POLAR, polar, pr);
        }
        if (sr > pr) {
            return new WarningState(WarningType.STORM, storm, sr);
        }

        if (distToZPole <= distToXEdge) {
            return new WarningState(WarningType.POLAR, polar, pr);
        }
        return new WarningState(WarningType.STORM, storm, sr);
    }

    public static PolarStage computePolarStage(ClientWorld world, PlayerEntity player) {
        int radius = borderRadiusBlocks(world);
        int absZ = (int) Math.floor(Math.abs(player.getZ()));
        int distToZPole = radius - absZ;
        return polarStageFor(radius, distToZPole);
    }

    public static StormStage computeStormStage(ClientWorld world, PlayerEntity player) {
        int radius = borderRadiusBlocks(world);
        int absX = (int) Math.floor(Math.abs(player.getX()));
        int distToXEdge = radius - absX;
        return stormStageFor(radius, distToXEdge);
    }

    private static float polarWhiteoutIntensity(ClientWorld world, PlayerEntity player) {
        int radius = borderRadiusBlocks(world);
        int absZ = (int) Math.floor(Math.abs(player.getZ()));
        int distToZPole = radius - absZ;

        int whiteout = (int) Math.round(radius / 16.0);
        int lethal = (int) Math.round(radius / 24.0);
        int hopeless = (int) Math.round(radius / 32.0);

        if (distToZPole > whiteout) {
            return 0.0f;
        }
        if (distToZPole <= hopeless) {
            return 1.0f;
        }
        if (distToZPole <= lethal) {
            float t = (lethal - distToZPole) / (float) (lethal - hopeless);
            t = Math.max(0.0f, Math.min(1.0f, t));
            return 0.5f + 0.5f * t;
        }

        float t = (whiteout - distToZPole) / (float) (whiteout - lethal);
        t = Math.max(0.0f, Math.min(1.0f, t));
        return 0.5f * t;
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
            double size = client.world.getWorldBorder().getSize();
            active = Math.abs(size - 7500.0) < 1.0 || Math.abs(size - 10000.0) < 1.0 || Math.abs(size - 15000.0) < 1.0;
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

        double distToXBorder = axisDistanceInsideBorder(border, x, true);
        double distToZBorder = axisDistanceInsideBorder(border, z, false);

        double radius = border.getSize() * 0.5;
        double warnStart = Math.min(1500.0, Math.max(300.0, radius / 8.0));
        double warnCritical = Math.min(750.0, Math.max(150.0, radius / 16.0));

        float poleSeverity = distToZBorder <= warnStart ? (float) (1.0 - (distToZBorder / warnStart)) : 0f;
        float stormSeverity = distToXBorder <= warnStart ? (float) (1.0 - (distToXBorder / warnStart)) : 0f;

        poleSeverity = Math.max(0f, Math.min(1f, poleSeverity));
        stormSeverity = Math.max(0f, Math.min(1f, stormSeverity));

        boolean poleCritical = distToZBorder <= warnCritical;
        boolean stormCritical = distToXBorder <= warnCritical;

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

        PolarStage stage = computePolarStage(client.world, client.player);
        if (stage != PolarStage.WHITEOUT_APPROACH && stage != PolarStage.LETHAL && stage != PolarStage.HOPELESS) {
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
        double radius = border.getSize() * 0.5;
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

        PolarStage stage = computePolarStage(client.world, client.player);
        if (stage != PolarStage.WHITEOUT_APPROACH && stage != PolarStage.LETHAL && stage != PolarStage.HOPELESS) {
            return 0.0f;
        }

        float intensity = polarWhiteoutIntensity(client.world, client.player);
        intensity = Math.max(0.0f, Math.min(1.0f, intensity));
        if (intensity <= 0.001f) {
            return 0.0f;
        }

        return intensity;
    }
}
