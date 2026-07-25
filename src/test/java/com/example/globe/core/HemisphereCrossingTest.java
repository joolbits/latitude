package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link HemisphereCrossing} (Phase 5 Slice B-3, P2: hemisphere-title
 * crossing/debounce/stack logic).
 *
 * <p>See {@code docs/binder/phase5-boundary-experience-plan-20260709.md}, "Hemisphere titles
 * (B-3c)": one axis-agnostic {@code evaluate} drives BOTH the N/S title at the equator (Z vs
 * centerZ) and the E/W title at the prime meridian (X vs centerX), and the 0deg,0deg NON-OVERLAP
 * RULE requires a single shared title channel so an equator+meridian crossing on the same tick
 * renders ONE stacked two-line title, never two competing single-line titles ({@code
 * composeLines}). Debounce mirrors the pre-P2 in-overlay N/S logic (dead zone, genuine-crossing
 * requirement, teleport guard, per-axis cooldown) so equator behavior is unchanged and the new
 * meridian axis is identical-by-construction.
 */
class HemisphereCrossingTest {

    private static final double CENTER = 0.0;
    private static final double DEAD_ZONE = HemisphereCrossing.DEAD_ZONE_BLOCKS; // 64.0
    private static final double MAX_STEP = HemisphereCrossing.MAX_STEP_BLOCKS; // 256.0
    private static final long COOLDOWN = HemisphereCrossing.COOLDOWN_MS; // 15_000L

    // ---- (1) sideOf: -1/+1 outside dead zone, 0 inside; boundary exact at center+/-deadZone -----

    @Test
    void sideOfIsNegativeWellOutsideDeadZoneOnTheNegativeSide() {
        assertEquals(-1, HemisphereCrossing.sideOf(CENTER - DEAD_ZONE - 1.0, CENTER, DEAD_ZONE));
    }

    @Test
    void sideOfIsPositiveWellOutsideDeadZoneOnThePositiveSide() {
        assertEquals(1, HemisphereCrossing.sideOf(CENTER + DEAD_ZONE + 1.0, CENTER, DEAD_ZONE));
    }

    @Test
    void sideOfIsZeroAtCenter() {
        assertEquals(0, HemisphereCrossing.sideOf(CENTER, CENTER, DEAD_ZONE));
    }

    @Test
    void sideOfIsZeroJustInsideTheDeadZoneEdge() {
        // WHY: the class javadoc documents the dead zone as [center-deadZone, center+deadZone] with
        // sideOf==0 strictly inside it (Math.abs(d) < deadZone) -- a point 0.001 blocks shy of the
        // edge must still resolve as "on the line", not as a hemisphere.
        assertEquals(0, HemisphereCrossing.sideOf(CENTER + DEAD_ZONE - 0.001, CENTER, DEAD_ZONE));
        assertEquals(0, HemisphereCrossing.sideOf(CENTER - DEAD_ZONE + 0.001, CENTER, DEAD_ZONE));
    }

    @Test
    void sideOfResolvesOutwardExactlyAtTheDeadZoneBoundary() {
        // WHY: sideOf's guard is a strict "<" (Math.abs(d) < deadZone), so a coord exactly deadZone
        // away from center is NOT inside the dead band -- it already resolves to a side. This is a
        // deliberate boundary detail (the dead zone is a half-open band), not an off-by-one to fix.
        assertEquals(1, HemisphereCrossing.sideOf(CENTER + DEAD_ZONE, CENTER, DEAD_ZONE));
        assertEquals(-1, HemisphereCrossing.sideOf(CENTER - DEAD_ZONE, CENTER, DEAD_ZONE));
    }

    // ---- (2) evaluate FIRES on a genuine crossing, advancing stableSide -------------------------

    @Test
    void evaluateFiresOnGenuineCrossingAndAdvancesStableSide() {
        // WHY: plan intent -- "an actual center CROSSING is required", i.e. a confirmed stable side
        // (established south of the line) followed by a fresh sample confirmed north of the line,
        // is exactly the case a hemisphere title must announce.
        HemisphereCrossing.Result result = HemisphereCrossing.evaluate(
                /* coord */ 100.0, CENTER,
                /* lastObserved */ -100.0, /* lastStableSide */ -1,
                /* nowMs */ 1_000L, /* lastFireMs */ Long.MIN_VALUE,
                DEAD_ZONE, MAX_STEP, COOLDOWN);

        assertTrue(result.fire(), "expected a genuine crossing to fire");
        assertEquals(1, result.newStableSide());
        assertEquals(100.0, result.newObserved());
    }

    // ---- (3) NO fire while wobbling inside the dead zone (hovering on the meridian) ---------------

    @Test
    void evaluateNeverFiresWhileWobblingInsideDeadZone() {
        // WHY: dead-zone wobble must never resolve to a hemisphere tick-to-tick, so it can neither
        // flip the stable side nor fire -- "sitting on the line never resolves" per the class
        // javadoc. Stable side is first established firmly south of the line, then the player
        // hovers back and forth across the meridian but always inside +/-deadZone.
        double lastObserved = -100.0;
        int lastStableSide = -1;
        long lastFireMs = Long.MIN_VALUE;
        double[] wobbleCoords = {10.0, -10.0, 30.0, -30.0, 0.0, 63.9, -63.9, 5.0};

        for (int i = 0; i < wobbleCoords.length; i++) {
            long nowMs = 1_000L + i;
            HemisphereCrossing.Result result = HemisphereCrossing.evaluate(
                    wobbleCoords[i], CENTER, lastObserved, lastStableSide, nowMs, lastFireMs,
                    DEAD_ZONE, MAX_STEP, COOLDOWN);
            assertFalse(result.fire(), "wobble step " + i + " must not fire (coord=" + wobbleCoords[i] + ")");
            assertEquals(-1, result.newStableSide(), "stable side must not move while wobbling");
            // Observed sample must be held at the last confident (out-of-band) position.
            assertEquals(-100.0, result.newObserved());
            lastStableSide = result.newStableSide();
            lastFireMs = result.fire() ? nowMs : lastFireMs;
        }
    }

    // ---- (4) NO fire without a prior stable side (fresh world join on the far side) --------------

    @Test
    void evaluateDoesNotFireWithoutAPriorStableSide() {
        // WHY: "Unseeded stable side: seed it, no fire (we have nothing to have crossed from)" --
        // a player who joins fresh cannot have their very first resolved hemisphere sample treated
        // as a crossing, even if the jump from the last (dead-zone) sample to the far side is large.
        // First sample lands inside the dead zone -- observed seeds, but stableSide stays unseeded (0).
        HemisphereCrossing.Result seeded = HemisphereCrossing.evaluate(
                10.0, CENTER, Double.NaN, 0, 1_000L, Long.MIN_VALUE, DEAD_ZONE, MAX_STEP, COOLDOWN);
        assertFalse(seeded.fire());
        assertEquals(0, seeded.newStableSide());
        assertEquals(10.0, seeded.newObserved());

        // Second sample resolves to the far side, but there is still no PRIOR stable side to have
        // crossed from -- must seed the stable side, not fire.
        HemisphereCrossing.Result result = HemisphereCrossing.evaluate(
                200.0, CENTER, seeded.newObserved(), seeded.newStableSide(), 1_001L, Long.MIN_VALUE,
                DEAD_ZONE, MAX_STEP, COOLDOWN);
        assertFalse(result.fire(), "no prior stable side means nothing to have crossed from");
        assertEquals(1, result.newStableSide());
        assertEquals(200.0, result.newObserved());
    }

    // ---- (5) Teleport guard: a step > MAX_STEP_BLOCKS does not fire and re-bases state -----------

    @Test
    void evaluateTeleportGuardSuppressesFireAndRebasesState() {
        // WHY: "MAX_STEP_BLOCKS teleport guard: a jump larger than this re-seeds without firing" --
        // a /tp or dimension change can jump the player across the meridian in one sample; that must
        // not be read as a walked crossing, but the tracker still re-bases (newStableSide/newObserved
        // reflect where the player actually is now) so the NEXT genuine step is measured correctly.
        double step = MAX_STEP + 1.0; // > 256
        double lastObserved = -100.0;
        double coord = lastObserved + step; // 300.0, far outside the dead zone, opposite side

        HemisphereCrossing.Result result = HemisphereCrossing.evaluate(
                coord, CENTER, lastObserved, -1, 1_000L, Long.MIN_VALUE, DEAD_ZONE, MAX_STEP, COOLDOWN);

        assertFalse(result.fire(), "a teleport-sized step must never fire a title");
        assertEquals(1, result.newStableSide(), "state re-bases to the side actually observed");
        assertEquals(coord, result.newObserved());
    }

    @Test
    void evaluateStepExactlyAtMaxStepIsNotATeleportAndCanFire() {
        // WHY: the guard is "larger than" maxStep (step > maxStep), so a step of exactly
        // MAX_STEP_BLOCKS is still a legitimate walked crossing, not a teleport.
        double coord = CENTER + (MAX_STEP / 2.0); // step from lastObserved is exactly MAX_STEP
        double lastObserved = coord - MAX_STEP;
        assertTrue(lastObserved < CENTER, "fixture sanity: lastObserved must start negative of center");

        HemisphereCrossing.Result result = HemisphereCrossing.evaluate(
                coord, CENTER, lastObserved, -1, 1_000L, Long.MIN_VALUE, DEAD_ZONE, MAX_STEP, COOLDOWN);

        assertTrue(result.fire(), "a step of exactly MAX_STEP_BLOCKS is not a teleport");
        assertEquals(1, result.newStableSide());
    }

    // ---- (6) Cooldown: a second genuine crossing within COOLDOWN_MS is suppressed, then re-arms ---

    @Test
    void evaluateSuppressesRefireWithinCooldownThenRearmsAfter() {
        // WHY: "COOLDOWN_MS per-axis re-arm so hovering across the line can't machine-gun titles."
        // evaluate takes time as an explicit parameter (nowMs/lastFireMs) so this is driven with
        // fixed millisecond values -- deterministic, no wall-clock dependency in the test.
        long firstCrossMs = 1_000L;
        HemisphereCrossing.Result first = HemisphereCrossing.evaluate(
                100.0, CENTER, -100.0, -1, firstCrossMs, Long.MIN_VALUE, DEAD_ZONE, MAX_STEP, COOLDOWN);
        assertTrue(first.fire(), "first crossing establishes the fire baseline");
        assertEquals(1, first.newStableSide());
        long lastFireMs = firstCrossMs; // caller only updates lastFireMs when a title actually fired

        // A second genuine crossing (back to the negative side) 1ms shy of the cooldown boundary:
        // the side genuinely flips back (changed==true) but must not fire because it is not cooled.
        long withinCooldownMs = firstCrossMs + COOLDOWN - 1;
        HemisphereCrossing.Result second = HemisphereCrossing.evaluate(
                -100.0, CENTER, first.newObserved(), first.newStableSide(), withinCooldownMs, lastFireMs,
                DEAD_ZONE, MAX_STEP, COOLDOWN);
        assertFalse(second.fire(), "re-crossing within the cooldown window must not fire");
        // Per evaluate's contract, the tracked stable side still reflects the real crossing even
        // though the fire was suppressed -- only the title is debounced, not the tracking state.
        assertEquals(-1, second.newStableSide());
        assertEquals(-100.0, second.newObserved());
        // lastFireMs is untouched because the title did not fire.

        // A third genuine crossing (back to the positive side) exactly at the cooldown boundary
        // (>= cooldownMs) is cooled and must fire.
        long atCooldownBoundaryMs = firstCrossMs + COOLDOWN;
        HemisphereCrossing.Result third = HemisphereCrossing.evaluate(
                100.0, CENTER, second.newObserved(), second.newStableSide(), atCooldownBoundaryMs,
                lastFireMs, DEAD_ZONE, MAX_STEP, COOLDOWN);
        assertTrue(third.fire(), "a genuine crossing at/after the cooldown boundary must re-arm and fire");
        assertEquals(1, third.newStableSide());
    }

    // ---- (7) composeLines: N/S first when both present, single line when one, null/empty drop ----

    @Test
    void composeLinesStacksNorthSouthFirstThenEastWestWhenBothPresent() {
        // WHY: the 0deg,0deg non-overlap rule -- "N/S first, then E/W" -- exists so a simultaneous
        // equator+meridian crossing renders ONE stacked title with a fixed, predictable line order.
        assertArrayEquals(new String[] {"NORTHERN HEMISPHERE", "EASTERN HEMISPHERE"},
                HemisphereCrossing.composeLines("NORTHERN HEMISPHERE", "EASTERN HEMISPHERE"));
    }

    @Test
    void composeLinesReturnsSingleLineWhenOnlyNorthSouthPresent() {
        assertArrayEquals(new String[] {"SOUTHERN HEMISPHERE"},
                HemisphereCrossing.composeLines("SOUTHERN HEMISPHERE", null));
    }

    @Test
    void composeLinesReturnsSingleLineWhenOnlyEastWestPresent() {
        assertArrayEquals(new String[] {"WESTERN HEMISPHERE"},
                HemisphereCrossing.composeLines(null, "WESTERN HEMISPHERE"));
    }

    @Test
    void composeLinesReturnsEmptyWhenBothAreNull() {
        // WHY: "Zero lines -> empty (nothing to render)" -- null-drop, not a two-element array of
        // nulls that a renderer would have to null-check itself.
        assertArrayEquals(new String[0], HemisphereCrossing.composeLines(null, null));
    }

    @Test
    void composeLinesTreatsEmptyStringsAsAbsentJustLikeNull() {
        // WHY: composeLines's hasNs/hasEw checks reject both null AND empty-string lines, so a
        // caller that clears a slot with "" gets the same drop behavior as passing null.
        assertArrayEquals(new String[] {"EASTERN HEMISPHERE"},
                HemisphereCrossing.composeLines("", "EASTERN HEMISPHERE"));
        assertArrayEquals(new String[] {"NORTHERN HEMISPHERE"},
                HemisphereCrossing.composeLines("NORTHERN HEMISPHERE", ""));
        assertArrayEquals(new String[0], HemisphereCrossing.composeLines("", null));
        assertArrayEquals(new String[0], HemisphereCrossing.composeLines(null, ""));
    }

    // ---- (8) Axis-agnostic symmetry: the same evaluate drives Z (N/S) and X (E/W) identically -----

    @Test
    void evaluateIsTranslationInvariantSoTheSameFunctionServesBothAxes() {
        // WHY: the class is documented as ONE axis-agnostic function driving both the equator
        // (Z vs centerZ) and the prime meridian (X vs centerX). It must have no hidden knowledge of
        // "which axis" or "where zero is" -- shifting coord/center/lastObserved by the same constant
        // offset (simulating a non-zero centerX vs the centerZ==0 equator) must produce an identical
        // fire/newStableSide decision and an identically-shifted newObserved.
        double offset = 1_000.0; // e.g. an arbitrary meridian center, unlike the equator's center 0

        HemisphereCrossing.Result equatorStyle = HemisphereCrossing.evaluate(
                100.0, 0.0, -100.0, -1, 5_000L, Long.MIN_VALUE, DEAD_ZONE, MAX_STEP, COOLDOWN);
        HemisphereCrossing.Result meridianStyle = HemisphereCrossing.evaluate(
                100.0 + offset, 0.0 + offset, -100.0 + offset, -1, 5_000L, Long.MIN_VALUE,
                DEAD_ZONE, MAX_STEP, COOLDOWN);

        assertEquals(equatorStyle.fire(), meridianStyle.fire());
        assertEquals(equatorStyle.newStableSide(), meridianStyle.newStableSide());
        assertEquals(equatorStyle.newObserved() + offset, meridianStyle.newObserved());
    }

    @Test
    void evaluateIsTranslationInvariantThroughAFullCrossingCooldownSequence() {
        // WHY: same as above, but exercised across the full stateful sequence (genuine crossing,
        // suppressed refire, re-armed refire) to confirm no axis-specific behavior sneaks in once
        // cooldown/time bookkeeping is involved -- the E/W meridian channel must debounce exactly
        // like the already-proven N/S equator channel.
        double offset = -500.0;

        HemisphereCrossing.Result firstZ = HemisphereCrossing.evaluate(
                100.0, 0.0, -100.0, -1, 1_000L, Long.MIN_VALUE, DEAD_ZONE, MAX_STEP, COOLDOWN);
        HemisphereCrossing.Result firstX = HemisphereCrossing.evaluate(
                100.0 + offset, 0.0 + offset, -100.0 + offset, -1, 1_000L, Long.MIN_VALUE,
                DEAD_ZONE, MAX_STEP, COOLDOWN);
        assertEquals(firstZ.fire(), firstX.fire());
        assertEquals(firstZ.newStableSide(), firstX.newStableSide());

        long withinCooldownMs = 1_000L + COOLDOWN - 1;
        HemisphereCrossing.Result secondZ = HemisphereCrossing.evaluate(
                -100.0, 0.0, firstZ.newObserved(), firstZ.newStableSide(), withinCooldownMs, 1_000L,
                DEAD_ZONE, MAX_STEP, COOLDOWN);
        HemisphereCrossing.Result secondX = HemisphereCrossing.evaluate(
                -100.0 + offset, 0.0 + offset, firstX.newObserved(), firstX.newStableSide(),
                withinCooldownMs, 1_000L, DEAD_ZONE, MAX_STEP, COOLDOWN);
        assertFalse(secondZ.fire());
        assertEquals(secondZ.fire(), secondX.fire());
        assertEquals(secondZ.newStableSide(), secondX.newStableSide());

        long atCooldownBoundaryMs = 1_000L + COOLDOWN;
        HemisphereCrossing.Result thirdZ = HemisphereCrossing.evaluate(
                100.0, 0.0, secondZ.newObserved(), secondZ.newStableSide(), atCooldownBoundaryMs, 1_000L,
                DEAD_ZONE, MAX_STEP, COOLDOWN);
        HemisphereCrossing.Result thirdX = HemisphereCrossing.evaluate(
                100.0 + offset, 0.0 + offset, secondX.newObserved(), secondX.newStableSide(),
                atCooldownBoundaryMs, 1_000L, DEAD_ZONE, MAX_STEP, COOLDOWN);
        assertTrue(thirdZ.fire());
        assertEquals(thirdZ.fire(), thirdX.fire());
        assertEquals(thirdZ.newStableSide(), thirdX.newStableSide());
    }

    // ---- (9) B-4 round-2 PER-HEMISPHERE FULL titles: each side gets FULL once per visit-episode ---------

    private static final double BAND = 1_000.0; // >> the +/-100 crossing coords, so re-crosses stay "within band"

    @Test
    void evaluateBandedFiresFullOnTheFirstCrossingIntoAnUnannouncedSide() {
        // WHY: crossing into a side that has not yet been FULL-announced this episode returns FULL and marks
        // ONLY that side announced. Here we cross to the POSITIVE side (S/E) with both sides un-announced.
        HemisphereCrossing.BandedResult r = HemisphereCrossing.evaluateBanded(
                /* coord */ 100.0, CENTER, /* lastObserved */ -100.0, /* lastRawObserved */ -100.0,
                /* lastStableSide */ -1, /* negAnnounced */ false, /* posAnnounced */ false,
                /* nowMs */ 1_000L, /* lastFireMs */ Long.MIN_VALUE, DEAD_ZONE, BAND, MAX_STEP, COOLDOWN);

        assertEquals(HemisphereCrossing.Fire.FULL, r.fire());
        assertEquals(1, r.newStableSide());
        assertTrue(r.nextPosSideAnnounced(), "the side we crossed into is now announced");
        assertFalse(r.nextNegSideAnnounced(), "the other side stays un-announced -- it gets its own FULL");
    }

    @Test
    void evaluateBandedGivesEachHemisphereItsOwnFullThenSmallOnRepeatThenReArmsOnLeave() {
        // WHY: Peetsa's round-2 sequence -- cross -> FULL(neg/N side), re-cross -> FULL(pos/S side, its FIRST
        // visit!), third cross -> SMALL (neg already announced), leave band -> both re-arm. This is the exact
        // bug fix: round 1's single flag gave the second hemisphere only a SMALL; now each side gets a FULL.
        // BAND >> 100 so every re-cross stays within the band (no accidental leave-band re-arm mid-sequence).

        // Cross to the NEGATIVE side (North): its first visit -> FULL.
        HemisphereCrossing.BandedResult a = HemisphereCrossing.evaluateBanded(
                -100.0, CENTER, 100.0, 100.0, +1, false, false,
                1_000L, Long.MIN_VALUE, DEAD_ZONE, BAND, MAX_STEP, COOLDOWN);
        assertEquals(HemisphereCrossing.Fire.FULL, a.fire());
        assertEquals(-1, a.newStableSide());
        assertTrue(a.nextNegSideAnnounced());
        assertFalse(a.nextPosSideAnnounced());

        // Re-cross to the POSITIVE side (South): FIRST visit to this side this episode -> FULL (the fix!).
        long t2 = 1_000L + COOLDOWN;
        HemisphereCrossing.BandedResult b = HemisphereCrossing.evaluateBanded(
                100.0, CENTER, a.newObserved(), a.newRawObserved(), a.newStableSide(),
                a.nextNegSideAnnounced(), a.nextPosSideAnnounced(), t2, 1_000L,
                DEAD_ZONE, BAND, MAX_STEP, COOLDOWN);
        assertEquals(HemisphereCrossing.Fire.FULL, b.fire(), "the second hemisphere's first visit is FULL, not SMALL");
        assertEquals(1, b.newStableSide());
        assertTrue(b.nextNegSideAnnounced());
        assertTrue(b.nextPosSideAnnounced());

        // Third cross, back to the NEGATIVE side (already announced this episode) -> SMALL.
        long t3 = t2 + COOLDOWN;
        HemisphereCrossing.BandedResult c = HemisphereCrossing.evaluateBanded(
                -100.0, CENTER, b.newObserved(), b.newRawObserved(), b.newStableSide(),
                b.nextNegSideAnnounced(), b.nextPosSideAnnounced(), t3, t2,
                DEAD_ZONE, BAND, MAX_STEP, COOLDOWN);
        assertEquals(HemisphereCrossing.Fire.SMALL, c.fire(), "re-entering an already-announced side => small");
        assertEquals(-1, c.newStableSide());

        // Leave the band (>= BAND from center): both sides re-arm for the next visit-episode.
        HemisphereCrossing.BandedResult d = HemisphereCrossing.evaluateBanded(
                BAND + 200.0, CENTER, BAND + 100.0, BAND + 100.0, +1,
                c.nextNegSideAnnounced(), c.nextPosSideAnnounced(), t3 + COOLDOWN, t3,
                DEAD_ZONE, BAND, MAX_STEP, COOLDOWN);
        assertEquals(HemisphereCrossing.Fire.NONE, d.fire(), "no crossing on this leave-the-band sample");
        assertFalse(d.nextNegSideAnnounced(), "leaving the band re-arms both sides");
        assertFalse(d.nextPosSideAnnounced());
    }

    // ---- (10) B-4 item 2 regression: the teleport guard must use the RAW per-tick step, not the held ----
    //          confident observed, so a genuinely WALKED crossing across a wide dead band still fires. ----

    @Test
    void evaluateBandedWalkedCrossingWithFarHeldObservedStillFiresViaRawGuard() {
        // WHY: root cause of "E/W hemisphere title never fired". On the WIDE 2:1 world the caller HOLDS the
        // confident observed at the last out-of-dead-zone position while the player walks through the dead
        // band. A player confident at +333 (3degE) who walks back across the meridian resolves on the far
        // side while lastObserved is still +333 -- a >MAX_STEP "step" that mis-trips the teleport guard. The
        // fix threads a RAW per-tick reference (here -60, the previous tick just before resolving at -70), so
        // the guard sees the true one-tick step (10 blocks) and the walked crossing FIRES.
        HemisphereCrossing.BandedResult r = HemisphereCrossing.evaluateBanded(
                /* coord */ -70.0, CENTER, /* lastObserved (held) */ 333.0, /* lastRawObserved */ -60.0,
                /* lastStableSide */ +1, /* negAnnounced */ false, /* posAnnounced */ false,
                /* nowMs */ 1_000L, /* lastFireMs */ Long.MIN_VALUE, DEAD_ZONE, BAND, MAX_STEP, COOLDOWN);

        assertEquals(HemisphereCrossing.Fire.FULL, r.fire(),
                "a walked crossing must fire even though the held observed is > MAX_STEP away");
        assertEquals(-1, r.newStableSide());
        assertEquals(-70.0, r.newRawObserved(), "raw reference advances to the current coord for next tick");

        // CONTRAST: the legacy single-reference evaluate (raw == held) DOES mis-trip the guard on these same
        // numbers -- documenting exactly the bug the raw reference fixes.
        HemisphereCrossing.Result legacy = HemisphereCrossing.evaluate(
                -70.0, CENTER, 333.0, +1, 1_000L, Long.MIN_VALUE, DEAD_ZONE, MAX_STEP, COOLDOWN);
        assertFalse(legacy.fire(), "single-reference guard mis-reads the held +333 -> -70 as a teleport");
    }

    // ---- (11) TEST 92 degree-first dead zone: clamp(0.75 deg * bpd, floor 16, cap 64) per axis ----------

    @Test
    void deadZoneBlocksIsDegreeFirstClampedToFloorAndCap() {
        // 0.75 deg * blocksPerDegree, clamped to [DEAD_ZONE_FLOOR_BLOCKS (16), DEAD_ZONE_BLOCKS (64)].
        // xRadius 3750 longitude bpd = 20.833: 0.75*20.833 = 15.625 -> floored up to 16.
        assertEquals(16.0, HemisphereCrossing.deadZoneBlocks(3750.0 / 180.0), 1e-9);
        // xRadius 15000 longitude bpd = 83.333: 0.75*83.333 = 62.5 -> in range.
        assertEquals(62.5, HemisphereCrossing.deadZoneBlocks(15000.0 / 180.0), 1e-9);
        // xRadius 20000 longitude bpd = 111.111: 0.75*111.111 = 83.33 -> capped to 64.
        assertEquals(64.0, HemisphereCrossing.deadZoneBlocks(20000.0 / 180.0), 1e-9);
    }

    @Test
    void deadZoneBlocksFiresTitleWithinAboutOneDegreeOnEveryRealWorld() {
        // The title resolves a side (and can fire) once the player is deadZone blocks past the line; expressed in
        // DEGREES that must be <= ~1 deg on every real world size (the whole point of TEST 92 -- the old flat 64
        // was ~3 deg of longitude on xRadius 3750). Meridian axis (longitude, bpd = xRadius/180):
        for (double xRadius : new double[] {3750.0, 5000.0, 7500.0, 15000.0, 20000.0, 40000.0}) {
            double bpdLon = xRadius / 180.0;
            double degAtFire = HemisphereCrossing.deadZoneBlocks(bpdLon) / bpdLon;
            assertTrue(degAtFire <= 1.0 + 1e-9,
                    "meridian title fires within ~1 deg @ xRadius " + xRadius + " (deg=" + degAtFire + ")");
        }
        // Equator axis (latitude, bpd = zRadius/90):
        for (double zRadius : new double[] {3750.0, 7500.0, 10000.0}) {
            double bpdLat = zRadius / 90.0;
            double degAtFire = HemisphereCrossing.deadZoneBlocks(bpdLat) / bpdLat;
            assertTrue(degAtFire <= 1.0 + 1e-9,
                    "equator title fires within ~1 deg @ zRadius " + zRadius + " (deg=" + degAtFire + ")");
        }
    }

    @Test
    void deadZoneBlocksNeverBelowJitterFloorNorAboveCap() {
        assertEquals(HemisphereCrossing.DEAD_ZONE_FLOOR_BLOCKS, HemisphereCrossing.deadZoneBlocks(0.0), 1e-9);
        assertEquals(HemisphereCrossing.DEAD_ZONE_FLOOR_BLOCKS, HemisphereCrossing.deadZoneBlocks(-5.0), 1e-9);
        assertEquals(HemisphereCrossing.DEAD_ZONE_FLOOR_BLOCKS, HemisphereCrossing.deadZoneBlocks(Double.NaN), 1e-9);
        assertEquals(HemisphereCrossing.DEAD_ZONE_BLOCKS, HemisphereCrossing.deadZoneBlocks(100_000.0), 1e-9);
        assertTrue(HemisphereCrossing.DEAD_ZONE_FLOOR_BLOCKS >= 16.0, "floor kills jitter (>> a per-tick step)");
        assertTrue(HemisphereCrossing.DEAD_ZONE_FLOOR_BLOCKS < HemisphereCrossing.DEAD_ZONE_BLOCKS, "floor < cap");
    }

    @Test
    void deadZoneBlocksResolvesSideAtExactlyTheBandEdge() {
        // Integration with sideOf: a coord exactly deadZone past center resolves to a side (the band is
        // half-open), so the fire distance is exactly deadZoneBlocks(bpd) -- on the smallest world, the 16-block floor.
        double dz = HemisphereCrossing.deadZoneBlocks(3750.0 / 180.0);
        assertEquals(16.0, dz, 1e-9);
        assertEquals(1, HemisphereCrossing.sideOf(CENTER + dz, CENTER, dz), "exactly at the edge resolves outward");
        assertEquals(0, HemisphereCrossing.sideOf(CENTER + dz - 0.001, CENTER, dz), "just inside is still the dead band");
    }
}
