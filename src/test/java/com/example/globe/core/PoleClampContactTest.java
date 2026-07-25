package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PoleClampContact} (B-7, owner amendment) -- the tick-counter rate limit and the
 * in-water/blocked message-variant selection behind the Wide-world pole hard-stop's "presentable contact"
 * event.
 */
class PoleClampContactTest {

    @Test
    void firstContactEverAlwaysEmits() {
        assertTrue(PoleClampContact.shouldEmit(PoleClampContact.NEVER, 0L, PoleClampContact.CONTACT_COOLDOWN_TICKS));
        assertTrue(PoleClampContact.shouldEmit(PoleClampContact.NEVER, 12345L, PoleClampContact.CONTACT_COOLDOWN_TICKS));
    }

    @Test
    void rateLimitSuppressesWithinCooldownAndAllowsAtOrAfter() {
        long cd = PoleClampContact.CONTACT_COOLDOWN_TICKS;
        long last = 1000L;
        assertFalse(PoleClampContact.shouldEmit(last, last, cd), "same tick -> suppressed");
        assertFalse(PoleClampContact.shouldEmit(last, last + cd - 1, cd), "just inside the window -> suppressed");
        assertTrue(PoleClampContact.shouldEmit(last, last + cd, cd), "at the cooldown -> emits");
        assertTrue(PoleClampContact.shouldEmit(last, last + cd + 5, cd), "past the cooldown -> emits");
    }

    @Test
    void backwardsTimeNeverMutesTheWall() {
        // A world/time reset (now < last) must not permanently suppress the contact event.
        assertTrue(PoleClampContact.shouldEmit(1000L, 5L, PoleClampContact.CONTACT_COOLDOWN_TICKS));
    }

    @Test
    void messageVariantChosenByInWater() {
        assertEquals(PoleClampContact.MESSAGE_IN_WATER, PoleClampContact.message(true));
        assertEquals(PoleClampContact.MESSAGE_BLOCKED, PoleClampContact.message(false));
        assertFalse(PoleClampContact.MESSAGE_IN_WATER.equals(PoleClampContact.MESSAGE_BLOCKED));
    }
}
