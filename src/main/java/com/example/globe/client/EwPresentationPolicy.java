package com.example.globe.client;

/**
 * Dependency-free presentation policy for the east/west sandstorm approach.
 */
public final class EwPresentationPolicy {
    public static final double ADVISORY_DISTANCE_BLOCKS = 500.0;
    public static final double FOG_START_DISTANCE_BLOCKS = 400.0;
    public static final double DANGER_DISTANCE_BLOCKS = 100.0;
    public static final double FOG_FULL_DISTANCE_BLOCKS = 50.0;

    public static final float FOG_NEAR_START_BLOCKS = 0.5f;
    public static final float FOG_NEAR_END_BLOCKS = 12.0f;

    public static final int WARNING_FADE_IN_TICKS = 20;
    public static final int WARNING_HOLD_TICKS = 100;
    public static final int WARNING_FADE_OUT_TICKS = 20;
    public static final int WARNING_TOTAL_TICKS =
            WARNING_FADE_IN_TICKS + WARNING_HOLD_TICKS + WARNING_FADE_OUT_TICKS;

    public static final int SKY_SAMPLE_COUNT = 13;
    public static final int HIDDEN_CONFIRM_TICKS = 20;
    public static final int HIDDEN_FADE_TICKS = 20;
    public static final int VISIBLE_RESTORE_TICKS = 5;

    private static final int[][] OUTLINE_OFFSETS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0},             {1, 0},
            {-1, 1},  {0, 1},  {1, 1}
    };

    private EwPresentationPolicy() {
    }

    public static int warningStageRank(double distanceToBorder) {
        if (distanceToBorder <= DANGER_DISTANCE_BLOCKS) {
            return 2;
        }
        if (distanceToBorder <= ADVISORY_DISTANCE_BLOCKS) {
            return 1;
        }
        return 0;
    }

    public static double distanceToNearestBorder(double westBorder, double eastBorder, double x) {
        return Math.min(Math.abs(x - westBorder), Math.abs(eastBorder - x));
    }

    public static float fogIntensity(double distanceToBorder) {
        double t = (FOG_START_DISTANCE_BLOCKS - distanceToBorder)
                / (FOG_START_DISTANCE_BLOCKS - FOG_FULL_DISTANCE_BLOCKS);
        t = clamp01(t);
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    public static float fogEndDistance(double distanceToBorder, float baselineEnd) {
        return fogEndDistance(distanceToBorder, baselineEnd, 1.0f);
    }

    public static float fogEndDistance(
            double distanceToBorder,
            float baselineEnd,
            float presentationVisibility) {
        float intensity = fogIntensity(distanceToBorder)
                * (float) clamp01(presentationVisibility);
        if (intensity <= 0.0f) {
            return -1.0f;
        }
        return lerp(baselineEnd, FOG_NEAR_END_BLOCKS, intensity);
    }

    public static float fogStartDistance(double distanceToBorder, float baselineStart) {
        return fogStartDistance(distanceToBorder, baselineStart, 1.0f);
    }

    public static float fogStartDistance(
            double distanceToBorder,
            float baselineStart,
            float presentationVisibility) {
        float intensity = fogIntensity(distanceToBorder)
                * (float) clamp01(presentationVisibility);
        return lerp(baselineStart, FOG_NEAR_START_BLOCKS, intensity);
    }

    public static float exposureFraction(int visibleSkySamples, int totalSamples) {
        if (totalSamples <= 0) {
            return 1.0f;
        }
        int visible = Math.max(0, Math.min(totalSamples, visibleSkySamples));
        return (float) visible / (float) totalSamples;
    }

    public static boolean isHiddenCandidate(int playerY, int seaLevel, int visibleSkySamples) {
        return playerY < seaLevel - 2 && visibleSkySamples <= 0;
    }

    public static int particleBudget(int base, float visibility) {
        if (base <= 0) {
            return 0;
        }
        return Math.max(0, Math.round(base * (float) clamp01(visibility)));
    }

    public static float particleIntensity(double distanceToBorder, float presentationVisibility) {
        return fogIntensity(distanceToBorder) * (float) clamp01(presentationVisibility);
    }

    public static int particleBudget(
            int maximumBudget,
            double distanceToBorder,
            float presentationVisibility) {
        if (maximumBudget <= 0) {
            return 0;
        }
        return Math.max(0, Math.round(
                maximumBudget * particleIntensity(distanceToBorder, presentationVisibility)));
    }

    public static double windTowardInterior(
            double westBorder,
            double eastBorder,
            double x,
            double speed) {
        double boundedSpeed = Math.abs(speed);
        double westDistance = Math.abs(x - westBorder);
        double eastDistance = Math.abs(eastBorder - x);
        if (westDistance < eastDistance) {
            return boundedSpeed;
        }
        if (eastDistance < westDistance) {
            return -boundedSpeed;
        }
        return 0.0;
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
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Requires a full second of deep, completely enclosed evidence before presentation begins
     * fading. Ordinary trees, arches, and all near-surface positions restore at the fast path.
     */
    public static final class ShelterState {
        private int consecutiveHiddenTicks;
        private float visibility = 1.0f;
        private boolean confirmedHidden;

        public void update(int playerY, int seaLevel, int visibleSkySamples) {
            if (isHiddenCandidate(playerY, seaLevel, visibleSkySamples)) {
                consecutiveHiddenTicks++;
                if (consecutiveHiddenTicks >= HIDDEN_CONFIRM_TICKS) {
                    confirmedHidden = true;
                }
                if (consecutiveHiddenTicks > HIDDEN_CONFIRM_TICKS) {
                    visibility = Math.max(0.0f, visibility - 1.0f / HIDDEN_FADE_TICKS);
                }
                return;
            }

            consecutiveHiddenTicks = 0;
            confirmedHidden = false;
            visibility = Math.min(1.0f, visibility + 1.0f / VISIBLE_RESTORE_TICKS);
        }

        public float visibility() {
            return visibility;
        }

        public boolean pauseEpisode() {
            return confirmedHidden;
        }

        public void reset() {
            consecutiveHiddenTicks = 0;
            visibility = 1.0f;
            confirmedHidden = false;
        }
    }

    /**
     * One episode per border visit and stage. Only borderward entry or escalation can trigger;
     * retreat cannot replay a lower or already-seen stage. Moving beyond 500 blocks rearms both.
     */
    public static final class WarningEpisode {
        private double previousDistance = Double.NaN;
        private int previousStageRank;
        private int highestTriggeredStageRank;
        private int activeStageRank;
        private long activeStartTick = Long.MIN_VALUE;
        private long lastUpdateTick = Long.MIN_VALUE;

        public void update(int stageRank, double distanceToBorder, long worldTick, boolean paused) {
            int boundedStageRank = Math.max(0, Math.min(2, stageRank));

            if (distanceToBorder > ADVISORY_DISTANCE_BLOCKS) {
                rearm(distanceToBorder, worldTick);
                return;
            }

            if (paused) {
                if (activeStartTick != Long.MIN_VALUE
                        && lastUpdateTick != Long.MIN_VALUE
                        && worldTick > lastUpdateTick) {
                    activeStartTick += worldTick - lastUpdateTick;
                }
                lastUpdateTick = worldTick;
                return;
            }

            boolean hasPreviousSample = !Double.isNaN(previousDistance);
            boolean movingBorderward = hasPreviousSample
                    && distanceToBorder < previousDistance - 1.0e-6;
            boolean escalating = boundedStageRank > previousStageRank;

            if (boundedStageRank > 0
                    && movingBorderward
                    && escalating
                    && boundedStageRank > highestTriggeredStageRank) {
                highestTriggeredStageRank = boundedStageRank;
                activeStageRank = boundedStageRank;
                activeStartTick = worldTick;
            }

            previousDistance = distanceToBorder;
            previousStageRank = boundedStageRank;
            lastUpdateTick = worldTick;
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

        public void reset() {
            previousDistance = Double.NaN;
            previousStageRank = 0;
            highestTriggeredStageRank = 0;
            activeStageRank = 0;
            activeStartTick = Long.MIN_VALUE;
            lastUpdateTick = Long.MIN_VALUE;
        }

        private void rearm(double distanceToBorder, long worldTick) {
            reset();
            previousDistance = distanceToBorder;
            lastUpdateTick = worldTick;
        }
    }
}
