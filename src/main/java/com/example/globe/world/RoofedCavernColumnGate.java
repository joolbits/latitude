package com.example.globe.world;

import java.util.function.IntUnaryOperator;

/** Dependency-free vertical-column predicate used by {@link RoofedCavernPlacement}. */
final class RoofedCavernColumnGate {
    static final int AIR_OR_WATER = 1;
    static final int STURDY_UNDERSIDE = 2;
    static final int REJECT_ORIGIN_NOT_AIR_WATER = -2;
    static final int REJECT_NO_ROOF_WITHIN_BOUND = -3;
    static final int NO_ROOF = -1;

    enum Outcome {
        ACCEPT,
        REJECT_ORIGIN_NOT_AIR_WATER,
        REJECT_NO_ROOF_WITHIN_BOUND
    }

    private RoofedCavernColumnGate() {
    }

    /**
     * A valid column begins in air/water, stays there, and returns its first sturdy ceiling distance.
     * Rejections use distinct negative sentinels so the hot placement path does not allocate a result.
     */
    static int findRoof(int originY, int maximumRoofY, IntUnaryOperator columnFlagsAtY) {
        if ((columnFlagsAtY.applyAsInt(originY) & AIR_OR_WATER) == 0) {
            return REJECT_ORIGIN_NOT_AIR_WATER;
        }
        if (maximumRoofY <= originY) {
            return REJECT_NO_ROOF_WITHIN_BOUND;
        }
        for (int y = originY + 1; y <= maximumRoofY; y++) {
            int flags = columnFlagsAtY.applyAsInt(y);
            if ((flags & STURDY_UNDERSIDE) != 0) {
                return y - originY;
            }
            if ((flags & AIR_OR_WATER) == 0) {
                return REJECT_NO_ROOF_WITHIN_BOUND;
            }
        }
        return REJECT_NO_ROOF_WITHIN_BOUND;
    }

    static boolean isRoofed(int result) {
        return result > 0;
    }

    static Outcome outcome(int result) {
        if (isRoofed(result)) {
            return Outcome.ACCEPT;
        }
        return switch (result) {
            case REJECT_ORIGIN_NOT_AIR_WATER -> Outcome.REJECT_ORIGIN_NOT_AIR_WATER;
            case REJECT_NO_ROOF_WITHIN_BOUND -> Outcome.REJECT_NO_ROOF_WITHIN_BOUND;
            default -> throw new IllegalArgumentException("unknown roof-column result: " + result);
        };
    }

    static int roofDistance(int result) {
        outcome(result); // Fail closed on an unknown primitive encoding.
        return isRoofed(result) ? result : NO_ROOF;
    }
}
