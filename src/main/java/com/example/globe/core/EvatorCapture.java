package com.example.globe.core;

/**
 * Phase 5 Slice B-6 (Teleport-Evator) -- the PURE resolution rules for the per-world evator capture. Zero
 * Minecraft imports (Core Logic layer, unit-testable in a plain JVM); the {@code SavedData} plumbing that
 * persists the captured value lives in {@link com.example.globe.world.LatitudeWorldState}, which calls these.
 *
 * <h2>Why capture per-world (design amendment 7)</h2>
 * The evator changes GENERATION (the mirror band). Flipping it on an EXISTING world would abut new (mirrored)
 * chunks against old (unmirrored) chunks inside the band -- a generation-frontier tear -- and, worse, a late
 * flip onto unmirrored on-disk chunks defeats the "invisible seam" the feature exists for. So a world captures
 * the evator decision at BIRTH (the {@code globeRadius}/{@code globeShape} precedent) and reads that persisted
 * value forever; a world born pre-evator reads {@code false} even if the project default later flips.
 *
 * <h2>The boundaryV2 antagonism (design amendment 9)</h2>
 * {@code evatorV2} and {@code boundaryV2} are antagonistic: the boundary ocean moat at the edge makes the
 * arrival's {@code placeSafeY} refuse the mirrored landing (it would drop into the moat). Until a dedicated
 * mirrored-ocean arrival exists they are mutually exclusive, so the capture REFUSES to arm the evator when
 * {@code boundaryV2} is active at birth -- {@link #captureAtBirth} returns {@code false} in that case and
 * {@link #refusedByBoundary} flags it so the caller can log WHY.
 */
public final class EvatorCapture {

    private EvatorCapture() {
    }

    /**
     * The value a brand-new world captures at birth: the evator arms only when its own flag is on AND
     * {@code boundaryV2} is NOT active on the world. The boundaryV2 veto is folded in here (not left to the
     * caller) so every capture site is consistent and the refusal is a pure, tested rule.
     *
     * @param evatorFlag      the global {@code latitude.evatorV2.enabled} default at creation time
     * @param boundaryV2Active whether {@code latitude.boundaryV2.enabled} is active on the world at creation
     * @return {@code true} iff the evator should arm for this world
     */
    public static boolean captureAtBirth(boolean evatorFlag, boolean boundaryV2Active) {
        return evatorFlag && !boundaryV2Active;
    }

    /**
     * {@code true} iff the evator flag WANTED to arm but was vetoed purely by an active {@code boundaryV2}
     * (i.e. the capture refused for the antagonism reason, not because the evator flag was simply off). The
     * capture site uses this to emit an honest "refused: boundaryV2 active" log; it never changes behavior.
     */
    public static boolean refusedByBoundary(boolean evatorFlag, boolean boundaryV2Active) {
        return evatorFlag && boundaryV2Active;
    }

    /**
     * The effective, server-side read of the captured value. A world that never stamped a capture
     * ({@code captured == null} -- an existing / pre-evator save) reads {@code false} FOREVER, independent of
     * any later change to the global default. A stamped world reads exactly what it captured at birth.
     */
    public static boolean resolveEffective(Boolean captured) {
        return captured != null && captured;
    }
}
