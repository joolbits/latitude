package com.example.globe.dev;

import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Dependency-free policy shared by Latitude's development-only human-testing tools.
 */
public final class DevToolPolicy {
    public static final int MAX_TOKEN_LENGTH = 64;
    public static final double MOVEMENT_EPSILON_DEGREES = 1.0e-6;

    private DevToolPolicy() {
    }

    public enum MovementDirection {
        INITIAL,
        POLEWARD,
        EQUATORWARD,
        STATIONARY
    }

    public enum TraceContextAction {
        INITIAL,
        CONTINUE,
        CLOCK_RESYNC,
        DIMENSION_RESET
    }

    /**
     * Converts dimension-local game time into a monotonic policy clock for presentation traces.
     * A raw tick rollback without a dimension change is a clock resync, not a new movement or
     * warning episode.
     */
    public static final class TraceClock {
        private boolean initialized;
        private String lastDimension;
        private long lastRawTick;
        private long policyTick;

        public Update update(String dimension, long rawTick) {
            if (dimension == null || dimension.isBlank()) {
                throw new IllegalArgumentException("dimension is required");
            }

            String previousDimension = lastDimension;
            long previousRawTick = lastRawTick;
            TraceContextAction action;
            if (!initialized) {
                initialized = true;
                policyTick = rawTick;
                action = TraceContextAction.INITIAL;
            } else if (!lastDimension.equals(dimension)) {
                policyTick = rawTick;
                action = TraceContextAction.DIMENSION_RESET;
            } else if (rawTick < lastRawTick) {
                policyTick = saturatingAdd(policyTick, 1L);
                action = TraceContextAction.CLOCK_RESYNC;
            } else {
                policyTick = saturatingAdd(policyTick, rawTick - lastRawTick);
                action = TraceContextAction.CONTINUE;
            }

            lastDimension = dimension;
            lastRawTick = rawTick;
            return new Update(
                    action,
                    previousDimension,
                    dimension,
                    previousRawTick,
                    rawTick,
                    policyTick);
        }

        private static long saturatingAdd(long current, long delta) {
            if (delta > 0L && current > Long.MAX_VALUE - delta) {
                return Long.MAX_VALUE;
            }
            return current + delta;
        }

        public record Update(
                TraceContextAction action,
                String previousDimension,
                String dimension,
                long previousRawTick,
                long rawTick,
                long policyTick
        ) {
        }
    }

    public enum CloseState {
        PASS,
        FAIL,
        HOLD;

        public static CloseState parse(String raw) {
            if (raw == null) {
                throw new IllegalArgumentException("close state is required");
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "pass" -> PASS;
                case "fail" -> FAIL;
                case "hold" -> HOLD;
                default -> throw new IllegalArgumentException("close state must be pass, fail, or hold");
            };
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record LatitudeTarget(
            double requestedDegrees,
            int blockZ,
            double actualDegrees
    ) {
    }

    public record TraceTransition(
            MovementDirection direction,
            int stageRank,
            int fogBucket,
            boolean directionChanged,
            boolean stageChanged,
            boolean fogChanged
    ) {
        public boolean shouldRecord() {
            return directionChanged || stageChanged || fogChanged;
        }
    }

    public static String sanitizeToken(String raw, String fallback) {
        String source = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(Math.min(MAX_TOKEN_LENGTH, source.length()));
        boolean pendingDash = false;
        for (int i = 0; i < source.length() && out.length() < MAX_TOKEN_LENGTH; i++) {
            char c = source.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (pendingDash && out.length() > 0 && out.length() < MAX_TOKEN_LENGTH) {
                    out.append('-');
                }
                pendingDash = false;
                out.append(c);
            } else {
                pendingDash = true;
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
            out.setLength(out.length() - 1);
        }
        if (out.length() > 0) {
            return out.toString();
        }
        String safeFallback = fallback == null ? "item" : fallback.trim().toLowerCase(Locale.ROOT);
        if (!safeFallback.matches("[a-z0-9][a-z0-9-]{0,63}")) {
            return "item";
        }
        return safeFallback;
    }

    public static Path resolveContained(Path root, String... segments) {
        if (root == null) {
            throw new IllegalArgumentException("root is required");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path candidate = normalizedRoot;
        if (segments != null) {
            for (String segment : segments) {
                if (segment == null || segment.isBlank()) {
                    throw new IllegalArgumentException("path segment is required");
                }
                candidate = candidate.resolve(segment);
            }
        }
        candidate = candidate.toAbsolutePath().normalize();
        if (!candidate.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("path escapes the development evidence root");
        }
        return candidate;
    }

    public static LatitudeTarget latitudeTarget(
            double requestedDegrees,
            double centerZ,
            double latitudeRadius,
            double borderMinZ,
            double borderMaxZ,
            double safetyPadding
    ) {
        requireFinite(requestedDegrees, "latitude");
        requireFinite(centerZ, "border center");
        requireFinite(latitudeRadius, "latitude radius");
        requireFinite(borderMinZ, "border minimum");
        requireFinite(borderMaxZ, "border maximum");
        requireFinite(safetyPadding, "safety padding");
        if (requestedDegrees < -90.0 || requestedDegrees > 90.0) {
            throw new IllegalArgumentException("latitude must be within [-90..90]");
        }
        if (!(latitudeRadius > 0.0)) {
            throw new IllegalArgumentException("latitude radius must be positive");
        }
        if (!(borderMaxZ > borderMinZ)) {
            throw new IllegalArgumentException("world border bounds are invalid");
        }
        if (safetyPadding < 0.0) {
            throw new IllegalArgumentException("safety padding cannot be negative");
        }

        long rounded = Math.round(centerZ + (requestedDegrees / 90.0) * latitudeRadius);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("latitude target exceeds block-coordinate range");
        }
        int blockZ = (int) rounded;
        if (!isSafelyInside(blockZ + 0.5, borderMinZ, borderMaxZ, safetyPadding)) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "latitude %.6f\u00b0 is not safely reachable inside this world border",
                    requestedDegrees));
        }
        double actualDegrees = signedLatitudeDegrees(blockZ + 0.5, centerZ, latitudeRadius);
        return new LatitudeTarget(requestedDegrees, blockZ, actualDegrees);
    }

    public static int safeHorizontalBlock(
            double requested,
            double borderMin,
            double borderMax,
            double safetyPadding
    ) {
        requireFinite(requested, "horizontal coordinate");
        requireFinite(borderMin, "border minimum");
        requireFinite(borderMax, "border maximum");
        requireFinite(safetyPadding, "safety padding");
        if (!(borderMax > borderMin) || safetyPadding < 0.0) {
            throw new IllegalArgumentException("world border bounds are invalid");
        }
        long floored = (long) Math.floor(requested);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("horizontal coordinate exceeds block-coordinate range");
        }
        int block = (int) floored;
        if (!isSafelyInside(block + 0.5, borderMin, borderMax, safetyPadding)) {
            throw new IllegalArgumentException("horizontal coordinate is outside the safe world border");
        }
        return block;
    }

    public static double signedLatitudeDegrees(double z, double centerZ, double latitudeRadius) {
        requireFinite(z, "z");
        requireFinite(centerZ, "border center");
        requireFinite(latitudeRadius, "latitude radius");
        if (!(latitudeRadius > 0.0)) {
            throw new IllegalArgumentException("latitude radius must be positive");
        }
        return ((z - centerZ) / latitudeRadius) * 90.0;
    }

    public static MovementDirection movementDirection(double previousAbsoluteDegrees, double currentAbsoluteDegrees) {
        requireFinite(currentAbsoluteDegrees, "current latitude");
        if (Double.isNaN(previousAbsoluteDegrees)) {
            return MovementDirection.INITIAL;
        }
        requireFinite(previousAbsoluteDegrees, "previous latitude");
        double delta = currentAbsoluteDegrees - previousAbsoluteDegrees;
        if (delta > MOVEMENT_EPSILON_DEGREES) {
            return MovementDirection.POLEWARD;
        }
        if (delta < -MOVEMENT_EPSILON_DEGREES) {
            return MovementDirection.EQUATORWARD;
        }
        return MovementDirection.STATIONARY;
    }

    public static int fogBucket(float fogIntensity) {
        if (!Float.isFinite(fogIntensity)) {
            throw new IllegalArgumentException("fog intensity must be finite");
        }
        double bounded = Math.max(0.0, Math.min(1.0, fogIntensity));
        return (int) Math.round(bounded * 10.0);
    }

    public static TraceTransition traceTransition(
            double previousAbsoluteDegrees,
            double currentAbsoluteDegrees,
            MovementDirection previousDirection,
            int previousStageRank,
            int currentStageRank,
            int previousFogBucket,
            float currentFogIntensity
    ) {
        MovementDirection direction = movementDirection(previousAbsoluteDegrees, currentAbsoluteDegrees);
        int bucket = fogBucket(currentFogIntensity);
        boolean first = previousDirection == null;
        return new TraceTransition(
                direction,
                Math.max(0, currentStageRank),
                bucket,
                first || direction != previousDirection,
                first || Math.max(0, currentStageRank) != Math.max(0, previousStageRank),
                first || bucket != previousFogBucket);
    }

    public static boolean devToolingAllowed(
            boolean developmentEnvironment,
            boolean validPackagedTestIdentity
    ) {
        return developmentEnvironment || validPackagedTestIdentity;
    }

    public static boolean packagedTestIdentityValid(
            boolean fabricTestMarker,
            String metadataVersion,
            String manifestRole,
            int manifestSequence,
            String manifestVersion,
            String artifactVersion,
            String gitCommit,
            String gitBranch,
            String buildDirty,
            String buildTime
    ) {
        if (!fabricTestMarker
                || !"TEST".equals(manifestRole)
                || manifestSequence <= 0
                || isBlank(metadataVersion)
                || isBlank(manifestVersion)
                || isBlank(artifactVersion)
                || !metadataVersion.equals(manifestVersion)
                || !metadataVersion.equals(artifactVersion)
                || !metadataVersion.endsWith("-test." + manifestSequence)
                || gitCommit == null
                || !gitCommit.matches("[0-9a-f]{40}")
                || isBlank(gitBranch)
                || "unknown".equalsIgnoreCase(gitBranch)
                || (!"true".equals(buildDirty) && !"false".equals(buildDirty))
                || isBlank(buildTime)) {
            return false;
        }
        try {
            Instant.parse(buildTime);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    public static boolean autoCreateWorldProbeEnabled(
            boolean developmentEnvironment,
            boolean validPackagedTestIdentity,
            String explicitSetting,
            boolean disabled
    ) {
        if (!devToolingAllowed(developmentEnvironment, validPackagedTestIdentity)) {
            return false;
        }
        if (explicitSetting != null) {
            return Boolean.parseBoolean(explicitSetting);
        }
        return developmentEnvironment && !disabled;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isSafelyInside(double coordinate, double min, double max, double padding) {
        return coordinate >= min + padding && coordinate < max - padding;
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
