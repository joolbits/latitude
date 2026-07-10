package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-JVM tests for {@link PolarWarningEpisode} (Phase 5 Slice B-4 item 3): the episodic polar warning
 * ladder -- each tier fires ONCE when first crossed, going deeper fires each next tier once, and the whole
 * ladder re-arms only on a full retreat below {@link PolarWarningEpisode#RETREAT_REARM_DEG} (84 deg).
 */
class PolarWarningEpisodeTest {

    @Test
    void tierForLatMapsEachThresholdToItsTierIndex() {
        assertEquals(0, PolarWarningEpisode.tierForLat(84.9));
        assertEquals(1, PolarWarningEpisode.tierForLat(85.0));
        assertEquals(1, PolarWarningEpisode.tierForLat(86.9));
        assertEquals(2, PolarWarningEpisode.tierForLat(87.0));
        assertEquals(3, PolarWarningEpisode.tierForLat(89.0));
        assertEquals(4, PolarWarningEpisode.tierForLat(89.7));
        assertEquals(4, PolarWarningEpisode.tierForLat(90.0));
    }

    @Test
    void firstEntryIntoTier1Fires() {
        PolarWarningEpisode.Step s = PolarWarningEpisode.evaluate(85.5, 0);
        assertEquals(1, s.fireTier());
        assertEquals(1, s.nextHighestFired());
    }

    @Test
    void stayingInsideTheSameTierDoesNotRefire() {
        // WHY: a tier fires once; sitting at 85.5 with tier 1 already fired must stay silent.
        PolarWarningEpisode.Step s = PolarWarningEpisode.evaluate(85.5, 1);
        assertEquals(0, s.fireTier());
        assertEquals(1, s.nextHighestFired());
    }

    @Test
    void goingDeeperFiresEachNextTierOnce() {
        int highest = 0;
        PolarWarningEpisode.Step s1 = PolarWarningEpisode.evaluate(85.5, highest);
        assertEquals(1, s1.fireTier());
        highest = s1.nextHighestFired();

        PolarWarningEpisode.Step s2 = PolarWarningEpisode.evaluate(87.5, highest);
        assertEquals(2, s2.fireTier());
        highest = s2.nextHighestFired();

        PolarWarningEpisode.Step s3 = PolarWarningEpisode.evaluate(89.1, highest);
        assertEquals(3, s3.fireTier());
        highest = s3.nextHighestFired();

        PolarWarningEpisode.Step s4 = PolarWarningEpisode.evaluate(89.8, highest);
        assertEquals(4, s4.fireTier());
    }

    @Test
    void partialRetreatAboveReArmThresholdDoesNotReArmOrRefire() {
        // WHY: "re-arms ONLY when the player retreats below 84". Dropping 89 -> 85 (still >= 84) keeps the
        // ladder spent, so climbing back to 89 must NOT repeat tier 3.
        int highest = 3; // tiers 1..3 already fired
        PolarWarningEpisode.Step retreat = PolarWarningEpisode.evaluate(85.0, highest);
        assertEquals(0, retreat.fireTier(), "no fire while retreating within the hazard region");
        assertEquals(3, retreat.nextHighestFired(), "no partial-retreat re-arm above 84 deg");

        PolarWarningEpisode.Step climbBack = PolarWarningEpisode.evaluate(89.1, retreat.nextHighestFired());
        assertEquals(0, climbBack.fireTier(), "already-fired tier must not repeat without a full retreat");
    }

    @Test
    void fullRetreatBelowThresholdReArmsTheWholeLadder() {
        // WHY: dropping below 84 deg resets highestFired to 0; the next approach re-announces from tier 1.
        PolarWarningEpisode.Step reset = PolarWarningEpisode.evaluate(83.9, 4);
        assertEquals(0, reset.fireTier(), "leaving the hazard region never fires");
        assertEquals(0, reset.nextHighestFired(), "full retreat re-arms the ladder");

        PolarWarningEpisode.Step reentry = PolarWarningEpisode.evaluate(85.2, reset.nextHighestFired());
        assertEquals(1, reentry.fireTier(), "re-entry after a full retreat announces tier 1 again");
    }

    @Test
    void teleportPastSeveralTiersFiresOnlyTheDeepestOnce() {
        // WHY: a jump straight to the pole fires just the deepest (most severe) tier once, not a burst.
        PolarWarningEpisode.Step s = PolarWarningEpisode.evaluate(90.0, 0);
        assertEquals(4, s.fireTier());
        assertEquals(4, s.nextHighestFired());
    }
}
