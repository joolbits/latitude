package com.example.globe.tools;

import java.util.Locale;

/**
 * Dependency-free latitude coordinate math shared by Latitude's shipping operator commands.
 *
 * <p>This is the single implementation of the production latitude laws. {@code DevToolPolicy}
 * delegates to it rather than keeping a parallel copy, so the development-only tooling and the
 * shipped operator commands cannot drift apart, and the existing {@code DevToolPolicyTest}
 * regression exercises the code that actually ships.</p>
 *
 * <p>This class lives outside the development-only package deliberately: it is packaged into
 * public release artifacts, so it must contain no recording, sentinel, or auto-harness behavior.
 * See {@code docs/release/artifact-content-policy.md}.</p>
 */
public final class LatitudeToolsMath {
    private LatitudeToolsMath() {
    }

    public record LatitudeTarget(
            double requestedDegrees,
            int blockZ,
            double actualDegrees
    ) {
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
                    "latitude %.6f° is not safely reachable inside this world border",
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

    /**
     * Resolves the same latitude radius used by production worldgen.
     *
     * <p>The active radius is authoritative once worldgen has established it. The world-border
     * half-size is only the pre-initialization fallback; traversal safety insets must not shrink
     * classification or evidence coordinates.</p>
     */
    public static int productionLatitudeRadius(int activeRadius, double worldBorderRadius) {
        requireFinite(worldBorderRadius, "world-border radius");
        if (activeRadius > 0) {
            return activeRadius;
        }
        if (!(worldBorderRadius >= 1.0) || worldBorderRadius > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("world-border radius is outside the supported block range");
        }
        return (int) Math.floor(worldBorderRadius);
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
