package com.example.globe.client;

import com.example.globe.util.LatitudeBands;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Guards the separation between zone IDENTITY and zone NOTIFICATION.
 *
 * <p>An earlier revision applied a two-degree sticky buffer to the segment identity itself, so the
 * reported zone and the entry title only changed once the reading was two degrees past the
 * boundary — producing transitions at 48 and 52 degrees instead of the real 50, and a reported zone
 * that disagreed with the world the player was standing in. These tests pin the corrected contract:
 * identity is exact at the boundary, and only repeat announcements while lingering are withheld.
 */
public final class ZoneTitlePolicyTest {

    private ZoneTitlePolicyTest() {
    }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("ZONE_TITLE_POLICY_TEST_PASS");
    }

    public static void runAll() throws Exception {
        identityChangesExactlyAtEveryBoundary();
        theFiftyDegreeBoundaryIsExactFromBothDirections();
        identityIsHemisphereIndependent();
        lingeringOnABoundaryIsAnnouncedOnceNotRepeatedly();
        leavingAndReturningAnnouncesAgain();
        cooldownExpiryAnnouncesAgainEvenWithoutMoving();
        teleportAlwaysAnnounces();
        firstZoneOfSessionAlwaysAnnounces();
        theOverlayUsesThePolicyAndKeepsNoStickyIdentity();
        noProcessAttributionInShippedSource();
    }

    /** Identity must flip on the exact boundary value, for every band edge — not near it. */
    private static void identityChangesExactlyAtEveryBoundary() {
        LatitudeBands.Band[] bands = LatitudeBands.Band.values();
        for (int i = 1; i < bands.length; i++) {
            double boundary = bands[i].lowDeg();
            assertEquals(i - 1, ZoneTitlePolicy.segmentFor(boundary - 0.0001),
                    "just below " + boundary + " is still the lower band");
            assertEquals(i, ZoneTitlePolicy.segmentFor(boundary),
                    "exactly " + boundary + " is already the upper band — the boundary is inclusive "
                            + "and the transition must not be deferred");
            assertEquals(i, ZoneTitlePolicy.segmentFor(boundary + 0.0001),
                    "just above " + boundary + " is the upper band");
        }
    }

    /**
     * The reported case, pinned explicitly: walking through 50 degrees must transition AT 50, not at
     * 48 going one way and 52 going the other.
     */
    private static void theFiftyDegreeBoundaryIsExactFromBothDirections() {
        int temperate = ZoneTitlePolicy.segmentFor(49.9);
        int subpolar = ZoneTitlePolicy.segmentFor(50.0);
        assertTrue(subpolar == temperate + 1, "50.0 crosses exactly one band boundary");

        // Northbound: every reading below 50 is the lower band, every reading at/above is the upper.
        for (double lat : new double[]{48.0, 49.0, 49.5, 49.99}) {
            assertEquals(temperate, ZoneTitlePolicy.segmentFor(lat),
                    "northbound " + lat + " must NOT have flipped early (the 48-degree defect)");
        }
        for (double lat : new double[]{50.0, 50.01, 50.5, 51.0, 52.0}) {
            assertEquals(subpolar, ZoneTitlePolicy.segmentFor(lat),
                    "northbound " + lat + " must already have flipped (the 52-degree defect)");
        }
        // Southbound is the same function of the same absolute latitude: no direction memory exists.
        for (double lat : new double[]{52.0, 51.0, 50.0}) {
            assertEquals(subpolar, ZoneTitlePolicy.segmentFor(lat), "southbound " + lat);
        }
        for (double lat : new double[]{49.99, 49.0, 48.0}) {
            assertEquals(temperate, ZoneTitlePolicy.segmentFor(lat), "southbound " + lat);
        }
    }

    /** Absolute latitude in, so both hemispheres resolve identically at the same distance. */
    private static void identityIsHemisphereIndependent() {
        for (double lat : new double[]{0.0, 23.5, 35.0, 49.9, 50.0, 66.5, 89.0}) {
            assertEquals(ZoneTitlePolicy.segmentFor(lat), ZoneTitlePolicy.segmentFor(Math.abs(-lat)),
                    "northern and southern readings at " + lat + " degrees resolve the same band");
        }
    }

    /**
     * Border wobble: repeatedly recrossing 50 degrees announces once, then stays quiet — while the
     * identity, checked above, keeps flipping correctly underneath.
     */
    private static void lingeringOnABoundaryIsAnnouncedOnceNotRepeatedly() {
        double announceLat = 50.0;
        long announceTime = 1_000L;
        int suppressed = 0;
        long t = announceTime;
        for (double lat : new double[]{49.9, 50.0, 49.95, 50.05, 49.99, 50.01}) {
            t += 5L; // a few ticks of jitter, well inside the cooldown
            boolean announce = ZoneTitlePolicy.shouldAnnounce(
                    lat, t, announceLat, announceTime, false, false);
            if (!announce) {
                suppressed++;
            }
        }
        assertEquals(6, suppressed,
                "every wobble sample within the buffer and cooldown must be suppressed — this is the "
                        + "whole reason the buffer exists");
    }

    /** Moving genuinely away from the boundary and coming back is a real re-entry, not a wobble. */
    private static void leavingAndReturningAnnouncesAgain() {
        double announceLat = 50.0;
        long announceTime = 1_000L;
        assertTrue(ZoneTitlePolicy.shouldAnnounce(
                        52.5, announceTime + 10L, announceLat, announceTime, false, false),
                "2.5 degrees away is outside the buffer — a real departure, announce");
        assertTrue(ZoneTitlePolicy.shouldAnnounce(
                        47.4, announceTime + 10L, announceLat, announceTime, false, false),
                "2.6 degrees away on the other side is equally a real departure");
    }

    /** Standing still past the cooldown is no longer "repeated" — the player gets told again. */
    private static void cooldownExpiryAnnouncesAgainEvenWithoutMoving() {
        double announceLat = 50.0;
        long announceTime = 1_000L;
        assertFalse(ZoneTitlePolicy.shouldAnnounce(
                        50.01, announceTime + ZoneTitlePolicy.REANNOUNCE_COOLDOWN_TICKS - 1,
                        announceLat, announceTime, false, false),
                "one tick before the cooldown expires, still suppressed");
        assertTrue(ZoneTitlePolicy.shouldAnnounce(
                        50.01, announceTime + ZoneTitlePolicy.REANNOUNCE_COOLDOWN_TICKS,
                        announceLat, announceTime, false, false),
                "at cooldown expiry the announcement is allowed again");
    }

    /** A teleport is a genuine arrival somewhere new and must never be rate-limited. */
    private static void teleportAlwaysAnnounces() {
        assertTrue(ZoneTitlePolicy.shouldAnnounce(50.01, 1_005L, 50.0, 1_000L, false, true),
                "a teleport announces even inside the buffer AND the cooldown");
        assertTrue(ZoneTitlePolicy.isTeleportStep(0.0, ZoneTitlePolicy.TELEPORT_STEP_BLOCKS + 1.0),
                "a jump beyond the step threshold is a teleport");
        assertFalse(ZoneTitlePolicy.isTeleportStep(0.0, ZoneTitlePolicy.TELEPORT_STEP_BLOCKS),
                "exactly the threshold is still travel, not a teleport");
        assertFalse(ZoneTitlePolicy.isTeleportStep(1_000.0, 1_004.0), "ordinary walking is not a teleport");
        assertTrue(ZoneTitlePolicy.isTeleportStep(1_000.0, -1_000.0),
                "a hemisphere-crossing teleport is detected by magnitude, not sign");
    }

    /** The first zone seen after joining is always announced, never withheld by a stale cooldown. */
    private static void firstZoneOfSessionAlwaysAnnounces() {
        assertTrue(ZoneTitlePolicy.shouldAnnounce(50.0, 0L, Double.NaN, Long.MIN_VALUE, true, false),
                "first zone of the session announces");
        assertTrue(ZoneTitlePolicy.shouldAnnounce(50.0, 5L, Double.NaN, Long.MIN_VALUE, false, false),
                "an unseeded announce-latitude also announces");
    }

    /**
     * The overlay must derive identity from the policy and keep NO sticky segment of its own — a
     * second, private copy of that state is exactly how the 48/52 defect was reintroduced.
     */
    private static void theOverlayUsesThePolicyAndKeepsNoStickyIdentity() throws Exception {
        String overlay = read("src/main/java/com/example/globe/client/GlobeWarningOverlay.java");
        assertTrue(overlay.contains("ZoneTitlePolicy.segmentFor(absLatDeg)"),
                "zone identity must come from the policy's exact, unbuffered resolver");
        assertTrue(overlay.contains("ZoneTitlePolicy.shouldAnnounce("),
                "the notification decision must come from the policy");
        assertFalse(overlay.contains("lastZoneSegment"),
                "no sticky segment field may survive — identity is recomputed exactly every sample");
        assertFalse(overlay.contains("resolveZoneSegmentHysteretic"),
                "the hysteretic identity resolver must be gone, not merely unused");
        // Identity must be computed UNCONDITIONALLY and stored before the announce decision.
        // Asserting the exact statement, not a substring: a teeth check that wrapped the same
        // expression in a `lastZoneKey != null ? lastZoneKey : ...` fallback still satisfied a
        // substring/ordering check while reintroducing the misreport. Fourth time this shape has
        // slipped through in this campaign.
        String exactAssignment =
                "String canonicalZoneKey = LatitudeBands.Band.values()"
                        + "[ZoneTitlePolicy.segmentFor(absLatDeg)].name();";
        assertTrue(overlay.contains(exactAssignment),
                "zone identity must be this exact unconditional assignment — any fallback or "
                        + "carry-forward of a previous key reintroduces the misreported zone");
        int identity = overlay.indexOf(exactAssignment);
        int store = overlay.indexOf("lastZoneKey = canonicalZoneKey;");
        int announce = overlay.indexOf("ZoneTitlePolicy.shouldAnnounce(");
        assertTrue(identity >= 0 && store > identity && announce > store,
                "order must be resolve -> store -> decide, so a suppressed announcement can never "
                        + "leave the zone misreported");
    }

    /** The public repo carries no process narrative or personal attribution in source comments. */
    private static void noProcessAttributionInShippedSource() throws Exception {
        for (String path : new String[]{
                "src/main/java/com/example/globe/client/GlobeWarningOverlay.java",
                "src/main/java/com/example/globe/client/ZoneTitlePolicy.java"}) {
            String source = read(path);
            assertFalse(source.contains("Peetsa"),
                    "no personal attribution in shipped source: " + path);
            assertFalse(source.contains(" ask)") || source.contains(" ask:"),
                    "no request/process attribution in shipped source: " + path);
        }
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }
}
