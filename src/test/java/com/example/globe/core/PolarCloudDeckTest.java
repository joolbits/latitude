package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarCloudDeck} (TEST 79 item 2 -- polar overcast cloud tint). Verifies the
 * seam-free contract below the ambient onset, monotonic darkening toward the storm grey over the [85,90]
 * envelope, opacity that only ever rises, and the exact pole endpoint.
 */
class PolarCloudDeckTest {

    // A representative bright-white vanilla cloud tint (opaque). ARGB8888.
    private static final int VANILLA_WHITE = 0xFFF0F0FF;

    private static int a(int argb) { return (argb >>> 24) & 0xFF; }
    private static int r(int argb) { return (argb >>> 16) & 0xFF; }
    private static int g(int argb) { return (argb >>> 8) & 0xFF; }
    private static int b(int argb) { return argb & 0xFF; }

    @Test
    void belowOnsetIsByteIdentical() {
        // ambientProgress <= 0 must return the input untouched (seam-free: below 85 deg / off-globe).
        assertEquals(VANILLA_WHITE, PolarCloudDeck.stormCloudColor(VANILLA_WHITE, 0.0));
        assertEquals(VANILLA_WHITE, PolarCloudDeck.stormCloudColor(VANILLA_WHITE, -0.5));
        assertEquals(0x8899AABB, PolarCloudDeck.stormCloudColor(0x8899AABB, 0.0));
        // Fed a real sub-onset latitude via the shared window (84 deg -> ambientProgress 0).
        double p84 = PolarHazardWindow.ambientProgress(84.0);
        assertEquals(VANILLA_WHITE, PolarCloudDeck.stormCloudColor(VANILLA_WHITE, p84));
    }

    @Test
    void darkensTowardStormGreyAsAmbientRises() {
        // Each channel of a bright white tint moves toward the (darker) storm grey as progress rises.
        int mid = PolarCloudDeck.stormCloudColor(VANILLA_WHITE, 0.5);
        assertTrue(r(mid) < r(VANILLA_WHITE), "red should darken");
        assertTrue(g(mid) < g(VANILLA_WHITE), "green should darken");
        assertTrue(b(mid) < b(VANILLA_WHITE), "blue should darken");
    }

    @Test
    void darkeningIsMonotonicOverEnvelope() {
        // Sweep 85..90 deg; overall luminance must be non-increasing (deck only gets stormier toward the pole).
        int prevLum = Integer.MAX_VALUE;
        for (double lat = 85.0; lat <= 90.0 + 1e-9; lat += 0.25) {
            double p = PolarHazardWindow.ambientProgress(lat);
            int c = PolarCloudDeck.stormCloudColor(VANILLA_WHITE, p);
            int lum = r(c) + g(c) + b(c);
            assertTrue(lum <= prevLum, "luminance must not increase at lat " + lat);
            prevLum = lum;
        }
    }

    @Test
    void poleReachesCappedStormBlend() {
        // At the pole (progress 1) each channel is a MAX_DARKEN lerp from vanilla toward the storm grey.
        int pole = PolarCloudDeck.stormCloudColor(VANILLA_WHITE, 1.0);
        int expR = PolarCloudDeck.lerp(r(VANILLA_WHITE), PolarCloudDeck.STORM_CLOUD_R, PolarCloudDeck.MAX_DARKEN);
        int expG = PolarCloudDeck.lerp(g(VANILLA_WHITE), PolarCloudDeck.STORM_CLOUD_G, PolarCloudDeck.MAX_DARKEN);
        int expB = PolarCloudDeck.lerp(b(VANILLA_WHITE), PolarCloudDeck.STORM_CLOUD_B, PolarCloudDeck.MAX_DARKEN);
        assertEquals(expR, r(pole));
        assertEquals(expG, g(pole));
        assertEquals(expB, b(pole));
        // Still darker than vanilla but not flattened to the raw storm grey (MAX_DARKEN < 1).
        assertTrue(r(pole) > PolarCloudDeck.STORM_CLOUD_R, "trace of vanilla luminance survives at the pole");
    }

    @Test
    void opacityOnlyEverRises() {
        // A translucent vanilla tint is pushed toward opaque; an already-opaque one stays opaque.
        int translucent = 0x80F0F0FF;
        assertTrue(a(PolarCloudDeck.stormCloudColor(translucent, 0.6)) > a(translucent), "alpha should rise");
        assertEquals(255, a(PolarCloudDeck.stormCloudColor(translucent, 1.0)), "alpha opaque at the pole");
        assertEquals(255, a(PolarCloudDeck.stormCloudColor(VANILLA_WHITE, 0.6)), "already opaque stays opaque");
    }

    @Test
    void darkenFractionCappedAtMaxDarken() {
        assertEquals(0.0f, PolarCloudDeck.darkenFraction(0.0), 1e-6f);
        assertEquals(PolarCloudDeck.MAX_DARKEN, PolarCloudDeck.darkenFraction(1.0), 1e-6f);
        assertEquals(PolarCloudDeck.MAX_DARKEN, PolarCloudDeck.darkenFraction(2.0), 1e-6f); // clamped
    }
}
