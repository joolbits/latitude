package com.example.globe.client;

/**
 * Phase 5 Slice B-5 (Hemisphere Passage) -- CLIENT-side arrival hook point.
 *
 * <p><b>P1 STUB.</b> This is the defined seam P2 builds the arrival ceremony on: P1 only records the last
 * S2C {@code PassageArrivalPayload} (its {@code arrivalX}, and a one-shot "an arrival just happened" flag) so
 * P2 can (a) show the "EASTERN/WESTERN HEMISPHERE" arrival title off {@link #arrivedEast()} and (b) seed the
 * client passage arm state DISARMED-in-band (the mirror lands at the identical border distance, so without
 * this the client would re-open the prompt on the next tick forever). P1 deliberately does NO presentation.
 */
public final class HemispherePassageClientState {

    private static volatile boolean pendingArrival;
    private static volatile int arrivalX;

    private HemispherePassageClientState() {
    }

    /** Record a fresh arrival. Called from the S2C receiver (network thread; fields are volatile). */
    public static void onArrival(int x) {
        arrivalX = x;
        pendingArrival = true;
    }

    /** True iff the last arrival landed in the eastern hemisphere ({@code arrivalX >= 0}; border centered at 0). */
    public static boolean arrivedEast() {
        return arrivalX >= 0;
    }

    /** The last arrival's X (world blocks). */
    public static int lastArrivalX() {
        return arrivalX;
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
    }
}
