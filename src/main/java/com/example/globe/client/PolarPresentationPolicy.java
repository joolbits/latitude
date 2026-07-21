package com.example.globe.client;

/**
 * Dependency-free presentation policy for the 85-90 degree polar approach.
 */
public final class PolarPresentationPolicy {
    public static final double FOG_START_LATITUDE_DEGREES = 85.0;
    public static final double FOG_FULL_LATITUDE_DEGREES = 90.0;

    public static final float FOG_FAR_END_BLOCKS = 96.0f;
    public static final float FOG_NEAR_START_BLOCKS = 0.5f;
    public static final float FOG_NEAR_END_BLOCKS = 2.0f;

    public static final float FOG_TARGET_RED = 0.92f;
    public static final float FOG_TARGET_GREEN = 0.96f;
    public static final float FOG_TARGET_BLUE = 1.0f;

    public static final int WARNING_FADE_IN_TICKS = 10;
    public static final int WARNING_HOLD_TICKS = 70;
    public static final int WARNING_FADE_OUT_TICKS = 20;
    public static final int WARNING_TOTAL_TICKS =
            WARNING_FADE_IN_TICKS + WARNING_HOLD_TICKS + WARNING_FADE_OUT_TICKS;

    private static final int[][] OUTLINE_OFFSETS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0},             {1, 0},
            {-1, 1},  {0, 1},  {1, 1}
    };

    private PolarPresentationPolicy() {
    }

    public record WarningSelection(boolean polar, int stageRank) {
        public static final WarningSelection NONE = new WarningSelection(false, 0);
    }

    public static int ewTextStageRank(double distanceToBorder) {
        if (distanceToBorder <= 100.0) {
            return 2;
        }
        if (distanceToBorder <= 500.0) {
            return 1;
        }
        return 0;
    }

    public static WarningSelection arbitrateWarning(int activePolarStageRank, int ewStageRank) {
        int polar = Math.max(0, Math.min(4, activePolarStageRank));
        int ew = Math.max(0, Math.min(2, ewStageRank));

        if (polar == 4) {
            return new WarningSelection(true, polar);
        }
        if (ew == 2) {
            return new WarningSelection(false, ew);
        }
        if (polar > 0) {
            return new WarningSelection(true, polar);
        }
        if (ew == 1) {
            return new WarningSelection(false, ew);
        }
        return WarningSelection.NONE;
    }

    public static float fogIntensity(double absoluteLatitudeDegrees) {
        double t = (absoluteLatitudeDegrees - FOG_START_LATITUDE_DEGREES)
                / (FOG_FULL_LATITUDE_DEGREES - FOG_START_LATITUDE_DEGREES);
        t = clamp01(t);
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    public static float fogEndDistance(double absoluteLatitudeDegrees) {
        return fogEndDistance(absoluteLatitudeDegrees, FOG_FAR_END_BLOCKS);
    }

    public static float fogEndDistance(double absoluteLatitudeDegrees, float baselineEnd) {
        float intensity = fogIntensity(absoluteLatitudeDegrees);
        if (intensity <= 0.0f) {
            return -1.0f;
        }
        return lerp(baselineEnd, FOG_NEAR_END_BLOCKS, intensity);
    }

    public static float polarFogStart(float baselineStart, float intensity) {
        return lerp(baselineStart, FOG_NEAR_START_BLOCKS, (float) clamp01(intensity));
    }

    public static float blendFogColorChannel(float current, float target, float intensity) {
        return lerp(current, target, (float) clamp01(intensity));
    }

    public static int[][] outlineOffsets() {
        int[][] copy = new int[OUTLINE_OFFSETS.length][2];
        for (int i = 0; i < OUTLINE_OFFSETS.length; i++) {
            copy[i][0] = OUTLINE_OFFSETS[i][0];
            copy[i][1] = OUTLINE_OFFSETS[i][1];
        }
        return copy;
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * One warning family per visit to the 85-90 degree envelope. Each higher stage can fire once,
     * only while moving poleward. Dropping below 85 degrees rearms the family.
     */
    public static final class PolarWarningEpisode {
        private double previousAbsoluteLatitude = Double.NaN;
        private int previousStageRank;
        private int highestTriggeredStageRank;
        private int activeStageRank;
        private long activeStartTick = Long.MIN_VALUE;

        public void update(int stageRank, double absoluteLatitudeDegrees, long worldTick) {
            int boundedStageRank = Math.max(0, stageRank);

            if (absoluteLatitudeDegrees < FOG_START_LATITUDE_DEGREES) {
                rearm(absoluteLatitudeDegrees);
                return;
            }

            boolean hasPreviousSample = !Double.isNaN(previousAbsoluteLatitude);
            if (!hasPreviousSample && boundedStageRank == 4) {
                highestTriggeredStageRank = boundedStageRank;
                activeStageRank = boundedStageRank;
                activeStartTick = worldTick;
            }
            boolean movingPoleward = hasPreviousSample
                    && absoluteLatitudeDegrees > previousAbsoluteLatitude + 1.0e-6;
            boolean escalating = boundedStageRank > previousStageRank;

            if (boundedStageRank > 0
                    && escalating
                    && movingPoleward
                    && boundedStageRank > highestTriggeredStageRank) {
                highestTriggeredStageRank = boundedStageRank;
                activeStageRank = boundedStageRank;
                activeStartTick = worldTick;
            }

            previousAbsoluteLatitude = absoluteLatitudeDegrees;
            previousStageRank = boundedStageRank;
        }

        public int activeStageRank(long worldTick) {
            return alpha(worldTick) > 0.0f ? activeStageRank : 0;
        }

        public float alpha(long worldTick) {
            if (activeStageRank <= 0 || activeStartTick == Long.MIN_VALUE) {
                return 0.0f;
            }

            long age = worldTick - activeStartTick;
            if (age < 0L || age >= WARNING_TOTAL_TICKS) {
                return 0.0f;
            }
            if (age < WARNING_FADE_IN_TICKS) {
                return (float) age / (float) WARNING_FADE_IN_TICKS;
            }

            long fadeOutStart = WARNING_FADE_IN_TICKS + WARNING_HOLD_TICKS;
            if (age < fadeOutStart) {
                return 1.0f;
            }

            return 1.0f - (float) (age - fadeOutStart) / (float) WARNING_FADE_OUT_TICKS;
        }

        public int highestTriggeredStageRank() {
            return highestTriggeredStageRank;
        }

        /**
         * Keeps the episode at the same visual age when the same client level
         * resynchronizes its raw game clock backwards.
         */
        public void shiftClock(long deltaTicks) {
            if (activeStartTick != Long.MIN_VALUE) {
                activeStartTick += deltaTicks;
            }
        }

        public void reset() {
            previousAbsoluteLatitude = Double.NaN;
            previousStageRank = 0;
            highestTriggeredStageRank = 0;
            activeStageRank = 0;
            activeStartTick = Long.MIN_VALUE;
        }

        private void rearm(double absoluteLatitudeDegrees) {
            previousAbsoluteLatitude = absoluteLatitudeDegrees;
            previousStageRank = 0;
            highestTriggeredStageRank = 0;
            activeStageRank = 0;
            activeStartTick = Long.MIN_VALUE;
        }
    }
}
