package com.example.globe.client.create;

import java.util.concurrent.atomic.AtomicLong;

final class VanillaCreateWorldHandoffTest {
    private static int assertions;

    private VanillaCreateWorldHandoffTest() {
    }

    private static final Runnable NO_EXIT = () -> {
    };

    static int run() {
        assertions = 0;
        claimConsumesExactlyOnceAndCarriesWorldNameAndSeed();
        unrelatedScreenCannotClaimThePendingHandoff();
        expiredHandoffCannotBeClaimedLater();
        cancellingPreventsAnUnrelatedScreenFromClaimingTheHandoff();
        claimCarriesTheExitCallbackThatAbandonsTheWholeFlow();
        armRejectsAMissingExitCallback();
        aBareClaimCarriesNeitherInputsNorAWayBack();
        aBareClaimStillObeysTheClaimKeyAndConsumesOnce();
        return assertions;
    }

    private static void claimConsumesExactlyOnceAndCarriesWorldNameAndSeed() {
        AtomicLong now = new AtomicLong(1_000L);
        VanillaCreateWorldHandoff handoff = new VanillaCreateWorldHandoff(now::get, 100L);
        Object claimKey = new Object();

        handoff.arm(claimKey, "Modded World", "seed with spaces", NO_EXIT);

        expect(new VanillaCreateWorldHandoff.Payload("Modded World", "seed with spaces"),
                handoff.claim(claimKey).orElseThrow().payload(), "first claim carries the armed payload");
        expectTrue(handoff.claim(claimKey).isEmpty(), "a second screen must not inherit the bypass");
    }

    private static void unrelatedScreenCannotClaimThePendingHandoff() {
        VanillaCreateWorldHandoff handoff = new VanillaCreateWorldHandoff(() -> 0L, 50L);
        Object intendedScreen = new Object();
        handoff.arm(intendedScreen, "World", "seed", NO_EXIT);

        expectTrue(handoff.claim(new Object()).isEmpty(), "an unrelated claim key must not claim");
        expect(new VanillaCreateWorldHandoff.Payload("World", "seed"),
                handoff.claim(intendedScreen).orElseThrow().payload(), "the intended screen still can");
    }

    private static void expiredHandoffCannotBeClaimedLater() {
        AtomicLong now = new AtomicLong(10L);
        VanillaCreateWorldHandoff handoff = new VanillaCreateWorldHandoff(now::get, 50L);
        Object claimKey = new Object();
        handoff.arm(claimKey, "Old World", "old-seed", NO_EXIT);

        now.set(60L);

        expectTrue(handoff.claim(claimKey).isEmpty(), "an expired handoff cannot be claimed");
        expectTrue(handoff.claim(claimKey).isEmpty(), "expiry must also clear the pending handoff");
    }

    private static void cancellingPreventsAnUnrelatedScreenFromClaimingTheHandoff() {
        VanillaCreateWorldHandoff handoff = new VanillaCreateWorldHandoff(() -> 0L, 50L);
        Object claimKey = new Object();
        handoff.arm(claimKey, "World", "seed", NO_EXIT);

        handoff.cancel();

        expectTrue(handoff.claim(claimKey).isEmpty(), "a cancelled handoff cannot be claimed");
    }

    /**
     * The escape hatch's one-click exit is only possible if the abandon callback survives the trip.
     * Dropping it would silently return the player to Latitude instead of leaving the flow -- the
     * exact two-step annoyance this carries a callback to avoid.
     */
    private static void claimCarriesTheExitCallbackThatAbandonsTheWholeFlow() {
        VanillaCreateWorldHandoff handoff = new VanillaCreateWorldHandoff(() -> 0L, 50L);
        Object claimKey = new Object();
        AtomicLong exited = new AtomicLong();
        handoff.arm(claimKey, "World", "seed", exited::incrementAndGet);

        VanillaCreateWorldHandoff.Claim claim = handoff.claim(claimKey).orElseThrow();
        expectTrue(claim.exitCreateFlow() != null, "the claim must carry an exit callback");
        expectTrue(exited.get() == 0L, "claiming must not itself abandon the flow");
        claim.exitCreateFlow().run();
        expectTrue(exited.get() == 1L, "running the claimed callback abandons the flow exactly once");
    }

    private static void armRejectsAMissingExitCallback() {
        VanillaCreateWorldHandoff handoff = new VanillaCreateWorldHandoff(() -> 0L, 50L);
        try {
            handoff.arm(new Object(), "World", "seed", null);
            expectTrue(false, "arming without an exit callback must be rejected");
        } catch (NullPointerException expected) {
            expectTrue(true, "arming without an exit callback is rejected");
        }
    }

    /**
     * The Select World door arms this way. Both nulls are load-bearing, not laziness: a payload of
     * empty strings would BLANK vanilla's default name and seed rather than leave them alone, and a
     * non-null exit callback would make the escape-hatch footer relabel Cancel to "Back to Latitude"
     * on a screen with no Latitude behind it.
     */
    private static void aBareClaimCarriesNeitherInputsNorAWayBack() {
        VanillaCreateWorldHandoff handoff = new VanillaCreateWorldHandoff(() -> 0L, 50L);
        Object claimKey = new Object();
        handoff.armBare(claimKey);

        VanillaCreateWorldHandoff.Claim claim = handoff.claim(claimKey).orElseThrow();
        expectTrue(claim.payload() == null,
                "a bare claim must carry no payload -- empty strings would blank vanilla's defaults");
        expectTrue(claim.exitCreateFlow() == null,
                "a bare claim must carry no return callback -- there is no Latitude screen behind it");
    }

    private static void aBareClaimStillObeysTheClaimKeyAndConsumesOnce() {
        VanillaCreateWorldHandoff handoff = new VanillaCreateWorldHandoff(() -> 0L, 50L);
        Object intended = new Object();
        handoff.armBare(intended);

        expectTrue(handoff.claim(new Object()).isEmpty(),
                "an unrelated screen must not consume a bare claim either");
        expectTrue(handoff.claim(intended).isPresent(), "the intended screen still can");
        expectTrue(handoff.claim(intended).isEmpty(), "and only once");
    }

    private static void expect(Object expected, Object actual, String label) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void expectTrue(boolean condition, String label) {
        assertions++;
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
