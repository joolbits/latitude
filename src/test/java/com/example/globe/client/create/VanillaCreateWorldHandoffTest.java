package com.example.globe.client.create;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaCreateWorldHandoffTest {
    @Test
    void claimConsumesExactlyOnceAndCarriesWorldNameAndSeed() {
        AtomicLong now = new AtomicLong(1_000L);
        VanillaCreateWorldHandoff handoff = new VanillaCreateWorldHandoff(now::get, 100L);
        Object claimKey = new Object();

        handoff.arm(claimKey, "Modded World", "seed with spaces");

        assertEquals(new VanillaCreateWorldHandoff.Payload("Modded World", "seed with spaces"),
                handoff.claim(claimKey).orElseThrow());
        assertTrue(handoff.claim(claimKey).isEmpty(), "a second screen must not inherit the bypass");
    }

    @Test
    void unrelatedScreenCannotClaimThePendingHandoff() {
        VanillaCreateWorldHandoff handoff = new VanillaCreateWorldHandoff(() -> 0L, 50L);
        Object intendedScreen = new Object();
        handoff.arm(intendedScreen, "World", "seed");

        assertTrue(handoff.claim(new Object()).isEmpty());
        assertEquals(new VanillaCreateWorldHandoff.Payload("World", "seed"),
                handoff.claim(intendedScreen).orElseThrow());
    }

    @Test
    void expiredHandoffCannotBeClaimedLater() {
        AtomicLong now = new AtomicLong(10L);
        VanillaCreateWorldHandoff handoff = new VanillaCreateWorldHandoff(now::get, 50L);
        Object claimKey = new Object();
        handoff.arm(claimKey, "Old World", "old-seed");

        now.set(60L);

        assertTrue(handoff.claim(claimKey).isEmpty());
        assertTrue(handoff.claim(claimKey).isEmpty(), "expiry must also clear the pending handoff");
    }

    @Test
    void cancellingPreventsAnUnrelatedScreenFromClaimingTheHandoff() {
        VanillaCreateWorldHandoff handoff = new VanillaCreateWorldHandoff(() -> 0L, 50L);
        Object claimKey = new Object();
        handoff.arm(claimKey, "World", "seed");

        handoff.cancel();

        assertTrue(handoff.claim(claimKey).isEmpty());
    }
}
