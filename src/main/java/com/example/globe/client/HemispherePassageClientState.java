package com.example.globe.client;

import com.example.globe.core.PassageAxis;

/**
 * Phase 5 Slice B-5/B-7 (Hemisphere Passage) -- CLIENT-side arrival hook point. Records the last S2C
 * {@code PassageArrivalPayload} (its axis + arrival {@code (X,Z)} + a one-shot "an arrival just happened" flag)
 * so {@link HemispherePassageClient} can, on the next curtain tick, (a) route the seed to the RIGHT arm
 * (EW vs POLE -- the mirror lands at the identical edge distance on that axis, so without the seed the client
 * would re-open the prompt forever) and (b) show the right arrival title (E/W hemisphere, or "Beyond the
 * North/South Pole"). B-5 shipped the EW half; B-7 P2 adds the axis + Z so the pole arm is routable.
 */
public final class HemispherePassageClientState {

    private static volatile boolean pendingArrival;
    private static volatile int arrivalX;
    private static volatile int arrivalZ;
    private static volatile PassageAxis arrivalAxis = PassageAxis.EW;

    private HemispherePassageClientState() {
    }

    /** Record a fresh arrival. Called from the S2C receiver (network thread; fields are volatile). */
    public static void onArrival(PassageAxis axis, int x, int z) {
        arrivalAxis = axis == null ? PassageAxis.EW : axis;
        arrivalX = x;
        arrivalZ = z;
        pendingArrival = true;
    }

    /** The axis of the last arrival (which arm to seed / which title to fire). */
    public static PassageAxis lastArrivalAxis() {
        return arrivalAxis;
    }

    /** True iff the last arrival landed in the eastern hemisphere ({@code arrivalX >= 0}; border centered at 0). */
    public static boolean arrivedEast() {
        return arrivalX >= 0;
    }

    /** True iff the last (pole) arrival landed on the NORTHERN pole side ({@code arrivalZ < 0}; {@code N = -Z},
     *  border centered at 0) -- selects "Beyond the North Pole" vs "Beyond the South Pole". */
    public static boolean arrivedNorth() {
        return arrivalZ < 0;
    }

    /** The last arrival's X (world blocks). */
    public static int lastArrivalX() {
        return arrivalX;
    }

    /** The last arrival's Z (world blocks). */
    public static int lastArrivalZ() {
        return arrivalZ;
    }

    /** Non-consuming read of the pending-arrival flag (for -Dlatitude.debugPassage logging only; does NOT
     *  clear it, so it never perturbs the one-shot {@link #consumePendingArrival()} the state machine drives). */
    public static boolean peekPendingArrival() {
        return pendingArrival;
    }

    /** P2 consumes the one-shot arrival: returns true exactly once per arrival, then clears the flag. */
    public static boolean consumePendingArrival() {
        if (!pendingArrival) {
            return false;
        }
        pendingArrival = false;
        return true;
    }

    /** World-switch hygiene: clear any stale arrival so it can't leak into the next world. */
    public static void reset() {
        pendingArrival = false;
        arrivalX = 0;
        arrivalZ = 0;
        arrivalAxis = PassageAxis.EW;
    }
}
