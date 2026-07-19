package com.example.globe.dev;

import java.nio.file.Path;
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

    private static boolean isSafelyInside(double coordinate, double min, double max, double padding) {
        return coordinate >= min + padding && coordinate < max - padding;
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
