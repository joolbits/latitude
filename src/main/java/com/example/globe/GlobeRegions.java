package com.example.globe;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public final class GlobeRegions {
    private GlobeRegions() {
    }

    public static final int HALF_SIZE = GlobeMod.BORDER_RADIUS;

    public static final int POLAR_WHITEOUT_COLOR_START = 14000;

    public static final int POLAR_UNEASE_START = POLAR_WHITEOUT_COLOR_START;
    public static final int POLAR_EFFECTS_1 = 14100;
    public static final int POLAR_EFFECTS_2 = 14300;
    public static final int POLAR_WHITEOUT_START = 14500;
    public static final int POLAR_LETHAL_START = 14650;
    public static final int POLAR_HOPELESS_START = 14800;
    public static final int BORDER = HALF_SIZE;

    public static final int POLAR_FOG_VISUAL_START = GlobeMod.POLE_BAND_START_ABS_Z;

    public static final int STORM_WARN_START = 12800;
    public static final int STORM_START = 13000;
    public static final int STORM_SEVERE_START = 14500;
    public static final int STORM_OPAQUE_START = 14900;

    public static final int STORM_OVERLAY_WARN_START = 14400;
    public static final int STORM_OVERLAY_DANGER_START = 14550;
    public static final int STORM_OVERLAY_EDGE_START = 14650;

    public static final int POLAR_FOG_START = POLAR_WHITEOUT_START;
    public static final int POLAR_DEBUFF_START = POLAR_EFFECTS_1;
    public static final int POLAR_DANGER_START = POLAR_HOPELESS_START;
    public static final int POLAR_DEADLY_WARN_START = POLAR_LETHAL_START;
    public static final int POLAR_CAP_START = POLAR_WHITEOUT_START;

    public static final int POOR_CONDITIONS_WARN_START = POLAR_UNEASE_START;
    public static final int POOR_CONDITIONS_EFFECTS_START = POLAR_EFFECTS_1;

    public static final int STORM_FOG_START = STORM_START;
    public static final int STORM_DEBUFF_START = STORM_START;

    public static double t(int value, int start, int end) {
        if (value <= start) {
            return 0.0;
        }
        if (value >= end) {
            return 1.0;
        }
        return (value - start) / (double) (end - start);
    }

    public static float polarFogSeverity(BlockPos pos) {
        return ramp(Math.abs(pos.getZ()), POLAR_WHITEOUT_START, HALF_SIZE);
    }

    public static float polarDebuffSeverity(BlockPos pos) {
        return ramp(Math.abs(pos.getZ()), POLAR_DEBUFF_START, HALF_SIZE);
    }

    public static float polarWhiteoutSeverity(BlockPos pos) {
        return ramp(Math.abs(pos.getZ()), POLAR_WHITEOUT_START, HALF_SIZE);
    }

    public static float polarDangerSeverity(BlockPos pos) {
        return ramp(Math.abs(pos.getZ()), POLAR_LETHAL_START, HALF_SIZE);
    }

    public static float stormFogSeverity(BlockPos pos) {
        return ramp(Math.abs(pos.getX()), STORM_START, HALF_SIZE);
    }

    public static float stormDebuffSeverity(BlockPos pos) {
        return ramp(Math.abs(pos.getX()), STORM_START, HALF_SIZE);
    }

    public static float stormSevereSeverity(BlockPos pos) {
        return ramp(Math.abs(pos.getX()), STORM_SEVERE_START, HALF_SIZE);
    }

    public static float stormOpaqueSeverity(BlockPos pos) {
        return ramp(Math.abs(pos.getX()), STORM_OPAQUE_START, HALF_SIZE);
    }

    private static float ramp(int v, int start, int end) {
        return MathHelper.clamp((v - start) / (float) (end - start), 0.0f, 1.0f);
    }

    public static boolean nearPolarWarning(BlockPos pos) {
        int z = Math.abs(pos.getZ());
        return z >= POLAR_UNEASE_START && z < POLAR_EFFECTS_1;
    }

    public static boolean nearWhiteoutWarning(BlockPos pos) {
        int z = Math.abs(pos.getZ());
        return z >= (POLAR_WHITEOUT_START - 256) && z < POLAR_WHITEOUT_START;
    }

    public static boolean nearStormWarning(BlockPos pos) {
        int x = Math.abs(pos.getX());
        return x >= STORM_WARN_START && x < STORM_START;
    }
}
