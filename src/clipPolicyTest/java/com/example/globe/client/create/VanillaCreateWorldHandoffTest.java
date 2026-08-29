package com.example.globe.client.create;

import java.util.Optional;

/**
 * The handoff is a one-shot, key-matched, expiring payload. Each of those three properties is the
 * only thing standing between "carry the name and seed to the vanilla screen" and "silently
 * rename some unrelated world the player creates later", so each is pinned here.
 */
public final class VanillaCreateWorldHandoffTest {
    private static int assertions;

    private VanillaCreateWorldHandoffTest() {
    }

    public static void run() {
        deliversPayloadToTheMatchingKey();
        refusesAForeignKeyAndKeepsThePayload();
        claimIsOneShot();
        expiredPayloadIsNotDelivered();
        cancelDropsThePayload();
        rejectsNonPositiveTtl();
        System.out.println("PASS VanillaCreateWorldHandoffTest assertions=" + assertions);
    }

    private static VanillaCreateWorldHandoff handoff(long[] clock, long ttlNanos) {
        return new VanillaCreateWorldHandoff(() -> clock[0], ttlNanos);
    }

    private static void deliversPayloadToTheMatchingKey() {
        long[] clock = {0L};
        VanillaCreateWorldHandoff subject = handoff(clock, 1_000L);
        Object key = new Object();
        subject.arm(key, "Basecamp", "12345", () -> { });
        Optional<VanillaCreateWorldHandoff.Payload> claimed = subject.claim(key);
        expect(true, claimed.isPresent(), "matching key claims");
        expect(true, "Basecamp".equals(claimed.orElseThrow().worldName()), "world name survives");
        expect(true, "12345".equals(claimed.orElseThrow().seed()), "seed survives");
    }

    private static void refusesAForeignKeyAndKeepsThePayload() {
        long[] clock = {0L};
        VanillaCreateWorldHandoff subject = handoff(clock, 1_000L);
        Object armed = new Object();
        subject.arm(armed, "Basecamp", "12345", () -> { });
        expect(false, subject.claim(new Object()).isPresent(), "foreign key is refused");
        // The rightful screen may not have opened yet; a wrong claimant must not consume it.
        expect(true, subject.claim(armed).isPresent(), "payload survives a foreign claim");
    }

    private static void claimIsOneShot() {
        long[] clock = {0L};
        VanillaCreateWorldHandoff subject = handoff(clock, 1_000L);
        Object key = new Object();
        subject.arm(key, "Basecamp", "12345", () -> { });
        expect(true, subject.claim(key).isPresent(), "first claim");
        // init() runs again on every window resize; a second delivery would re-stamp the name and
        // seed over whatever the player has typed since.
        expect(false, subject.claim(key).isPresent(), "second claim is empty");
    }

    private static void expiredPayloadIsNotDelivered() {
        long[] clock = {0L};
        VanillaCreateWorldHandoff subject = handoff(clock, 1_000L);
        Object key = new Object();
        subject.arm(key, "Basecamp", "12345", () -> { });
        clock[0] = 1_000L;
        expect(false, subject.claim(key).isPresent(), "expired at exactly the ttl");
        clock[0] = 0L;
        subject.arm(key, "Basecamp", "12345", () -> { });
        clock[0] = 999L;
        expect(true, subject.claim(key).isPresent(), "still live one nanosecond inside the ttl");
    }

    private static void cancelDropsThePayload() {
        long[] clock = {0L};
        VanillaCreateWorldHandoff subject = handoff(clock, 1_000L);
        Object key = new Object();
        subject.arm(key, "Basecamp", "12345", () -> { });
        subject.cancel();
        expect(false, subject.claim(key).isPresent(), "cancelled payload is gone");
    }

    private static void rejectsNonPositiveTtl() {
        boolean threw = false;
        try {
            new VanillaCreateWorldHandoff(() -> 0L, 0L);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        expect(true, threw, "non-positive ttl is rejected");
    }

    private static void expect(boolean expected, boolean actual, String label) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }
}
