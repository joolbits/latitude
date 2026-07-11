package com.example.globe.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Probes for the loading-screen "reading light" wave (Peetsa 2026-07-11). Pure math, no Minecraft: verifies the
 * crest loops continuously, exactly one segment dominates at a time, adjacent segments get a strictly smaller
 * whisper of spillover, the amplitude is bounded and gentle, and segmentation splits on · while leaving the
 * separators out (the caller re-inserts them in the base color). Reduce-motion is the caller's job, so it isn't
 * exercised here beyond documenting that the boost math itself is always well-formed.
 */
class LoadingWaveTest {

    private static final int COUNT = 4; // "Itty Bitty · Square 1:1 · 7,500 × 7,500 · subpolar start"

    @Test
    void boostIsBoundedAndGentleAcrossAWholeCycle() {
        long cycle = LoadingWave.SEGMENT_PERIOD_MS * COUNT;
        for (long t = 0; t <= cycle; t += 37) {
            for (int i = 0; i < COUNT; i++) {
                float b = LoadingWave.boost(i, COUNT, t);
                assertTrue(b >= 0f, "boost must never go negative, was " + b);
                assertTrue(b <= LoadingWave.AMPLITUDE + 1e-6f, "boost must be bounded by AMPLITUDE, was " + b);
            }
        }
        // "Gentle": the peak lift is a modest caress, not the title glimmer's brilliance.
        assertTrue(LoadingWave.AMPLITUDE <= 0.35f, "amplitude should stay a gentle lift");
    }

    @Test
    void loopsContinuously() {
        long cycle = LoadingWave.SEGMENT_PERIOD_MS * COUNT;
        for (long t = 0; t < cycle; t += 53) {
            for (int i = 0; i < COUNT; i++) {
                float now = LoadingWave.boost(i, COUNT, t);
                float nextCycle = LoadingWave.boost(i, COUNT, t + cycle);
                assertEquals(now, nextCycle, 1e-4f, "wave must repeat exactly one cycle later");
            }
        }
    }

    @Test
    void oneDominantSegmentAtATime() {
        // Sample the moment the crest sits squarely on a segment: that segment must be the unique maximum.
        for (int crest = 0; crest < COUNT; crest++) {
            long t = (long) crest * LoadingWave.SEGMENT_PERIOD_MS;
            int argmax = -1;
            float max = -1f;
            for (int i = 0; i < COUNT; i++) {
                float b = LoadingWave.boost(i, COUNT, t);
                if (b > max) {
                    max = b;
                    argmax = i;
                }
            }
            assertEquals(crest, argmax, "the segment under the crest must be the brightest");
            // And it must be strictly brighter than every other segment (a real dominant, no ties).
            for (int i = 0; i < COUNT; i++) {
                if (i != crest) {
                    assertTrue(LoadingWave.boost(i, COUNT, t) < max,
                            "segment " + i + " must be dimmer than the dominant " + crest);
                }
            }
            // The dominant should be near the full amplitude when the crest is exactly on it.
            assertTrue(max > LoadingWave.AMPLITUDE * 0.99f, "crest segment should reach ~full amplitude");
        }
    }

    @Test
    void adjacentSpilloverIsAWhisperBelowTheDominant() {
        long t = LoadingWave.SEGMENT_PERIOD_MS; // crest exactly on segment 1
        float dominant = LoadingWave.boost(1, COUNT, t);
        float leftNeighbour = LoadingWave.boost(0, COUNT, t);
        float rightNeighbour = LoadingWave.boost(2, COUNT, t);
        float farSegment = LoadingWave.boost(3, COUNT, t);
        // Spillover exists (it's what makes it read as a wave)...
        assertTrue(leftNeighbour > 0f && rightNeighbour > 0f, "neighbours should catch a whisper");
        // ...but is strictly, clearly smaller than the dominant...
        assertTrue(leftNeighbour < dominant * 0.5f, "adjacent spillover must be well under the dominant");
        assertTrue(rightNeighbour < dominant * 0.5f, "adjacent spillover must be well under the dominant");
        // ...and the far segment is dimmer still (monotone falloff with distance).
        assertTrue(farSegment < leftNeighbour + 1e-6f, "far segment must be no brighter than an adjacent one");
    }

    @Test
    void crestTravelsForwardOverTime() {
        // Over one cycle the argmax should visit each segment index in order (a travelling wave, not random).
        int prev = -1;
        int transitions = 0;
        long cycle = LoadingWave.SEGMENT_PERIOD_MS * COUNT;
        for (long t = 0; t < cycle; t += 25) {
            int argmax = 0;
            float max = -1f;
            for (int i = 0; i < COUNT; i++) {
                float b = LoadingWave.boost(i, COUNT, t);
                if (b > max) {
                    max = b;
                    argmax = i;
                }
            }
            if (argmax != prev) {
                if (prev != -1) {
                    assertEquals((prev + 1) % COUNT, argmax, "crest must advance to the next segment, wrapping");
                    transitions++;
                }
                prev = argmax;
            }
        }
        // Over a full cycle the crest steps onto each segment once and then wraps back toward the first, so the
        // number of forward transitions equals the segment count (the wrap 3→0 is the loop point).
        assertEquals(COUNT, transitions, "crest should step through every segment and wrap once per cycle");
    }

    @Test
    void singleSegmentBreathesInsteadOfBlinking() {
        // A lone segment has no neighbour; it should get a smooth non-negative lift, bounded, that isn't a hard
        // on/off. Sample a full breathe cycle and confirm it both rises above 0 and returns to ~0.
        float min = Float.MAX_VALUE, max = -1f;
        long cycle = LoadingWave.SEGMENT_PERIOD_MS * 2L;
        for (long t = 0; t <= cycle; t += 20) {
            float b = LoadingWave.boost(0, 1, t);
            assertTrue(b >= 0f && b <= LoadingWave.AMPLITUDE + 1e-6f, "single-segment boost must stay bounded");
            min = Math.min(min, b);
            max = Math.max(max, b);
        }
        assertTrue(min < 0.01f, "breathe should ease down to near zero");
        assertTrue(max > LoadingWave.AMPLITUDE * 0.9f, "breathe should rise to near full amplitude");
    }

    @Test
    void segmentsSplitOnSeparatorWithoutReturningIt() {
        String summary = "Itty Bitty · Square 1:1 · 7,500 × 7,500 · subpolar start";
        assertArrayEquals(
                new String[]{"Itty Bitty", "Square 1:1", "7,500 × 7,500", "subpolar start"},
                LoadingWave.segments(summary));
    }

    @Test
    void segmentsHandlesDegenerateInputs() {
        assertEquals(0, LoadingWave.segments(null).length);
        assertEquals(0, LoadingWave.segments("").length);
        assertArrayEquals(new String[]{"NoSeparators"}, LoadingWave.segments("NoSeparators"));
    }

    @Test
    void reassemblingSegmentsWithSeparatorReproducesTheLine() {
        // Layout-preservation invariant the renderer relies on: segments joined by the SEPARATOR are exactly the
        // original string, so drawing token-by-token at running x offsets reproduces the same total width.
        String summary = "A · BB · CCC";
        assertEquals(summary, String.join(LoadingWave.SEPARATOR, LoadingWave.segments(summary)));
    }
}
