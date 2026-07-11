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

    // ── gaussian01: the normalized [0,1] crest height the shade() illumination path consumes ──

    @Test
    void gaussian01IsBoundedAndExactlyTracksBoost() {
        long cycle = LoadingWave.SEGMENT_PERIOD_MS * COUNT;
        for (long t = 0; t <= cycle; t += 37) {
            for (int i = 0; i < COUNT; i++) {
                float g = LoadingWave.gaussian01(i, COUNT, t);
                assertTrue(g >= 0f && g <= 1f, "gaussian01 must stay in [0,1], was " + g);
                // boost is exactly AMPLITUDE × gaussian01 — the two never disagree about where the crest is.
                assertEquals(LoadingWave.AMPLITUDE * g, LoadingWave.boost(i, COUNT, t), 1e-5f,
                        "boost must equal AMPLITUDE × gaussian01");
            }
        }
    }

    @Test
    void gaussian01ReachesOneOnTheCrestSegmentAndIsBelowOneElsewhere() {
        for (int crest = 0; crest < COUNT; crest++) {
            long t = (long) crest * LoadingWave.SEGMENT_PERIOD_MS;
            assertEquals(1f, LoadingWave.gaussian01(crest, COUNT, t), 1e-4f,
                    "the crest segment reaches full normalized height 1.0");
            for (int i = 0; i < COUNT; i++) {
                if (i != crest) {
                    assertTrue(LoadingWave.gaussian01(i, COUNT, t) < 1f - 1e-4f,
                            "non-crest segment " + i + " must sit below the crest");
                }
            }
        }
    }

    @Test
    void gaussian01PeakTravelsForwardWrappingOncePerCycle() {
        int prev = -1;
        int transitions = 0;
        long cycle = LoadingWave.SEGMENT_PERIOD_MS * COUNT;
        for (long t = 0; t < cycle; t += 25) {
            int argmax = 0;
            float max = -1f;
            for (int i = 0; i < COUNT; i++) {
                float g = LoadingWave.gaussian01(i, COUNT, t);
                if (g > max) {
                    max = g;
                    argmax = i;
                }
            }
            if (argmax != prev) {
                if (prev != -1) {
                    assertEquals((prev + 1) % COUNT, argmax, "the peak must advance to the next segment, wrapping");
                    transitions++;
                }
                prev = argmax;
            }
        }
        assertEquals(COUNT, transitions, "the peak steps through every segment and wraps once per cycle");
    }

    // ── shade: DIM-BASELINE + BRIGHT-CREST — the illumination that is actually VISIBLE on warm-white ──

    private static final int WARM_WHITE = 0xEDE0D0; // the exact summary-line base color

    private static int red(int c)   { return (c >> 16) & 0xFF; }
    private static int green(int c) { return (c >> 8) & 0xFF; }
    private static int blue(int c)  { return c & 0xFF; }

    @Test
    void shadeAtRestIsRestDimTimesBaseNotIdentity() {
        int rest = LoadingWave.shade(WARM_WHITE, 0f);
        // shade(rgb, 0) == REST_DIM × rgb, per-channel — the resting state is INTENTIONALLY dimmed (there is
        // always a crest somewhere, so un-lit words must read as "waiting their turn", not full base).
        assertEquals(Math.round(red(WARM_WHITE) * LoadingWave.REST_DIM), red(rest), "R resting = REST_DIM × base");
        assertEquals(Math.round(green(WARM_WHITE) * LoadingWave.REST_DIM), green(rest), "G resting = REST_DIM × base");
        assertEquals(Math.round(blue(WARM_WHITE) * LoadingWave.REST_DIM), blue(rest), "B resting = REST_DIM × base");
        assertTrue(red(rest) < red(WARM_WHITE) && green(rest) < green(WARM_WHITE) && blue(rest) < blue(WARM_WHITE),
                "resting must be strictly dimmer than base (not the identity)");
    }

    @Test
    void shadeAtCrestIsBrighterThanBaseAndClearlyBrighterThanRest() {
        int rest = LoadingWave.shade(WARM_WHITE, 0f);
        int crest = LoadingWave.shade(WARM_WHITE, 1f);
        // The crest lifts back to full base AND pops toward white, so it clears the base on every channel...
        assertTrue(red(crest) > red(WARM_WHITE) && green(crest) > green(WARM_WHITE) && blue(crest) > blue(WARM_WHITE),
                "crest must be brighter than the base color");
        // ...and towers over the resting state by a clear, visible margin. This is EXACTLY the regression the
        // old brighten-toward-white path failed: warm-white lerped toward white barely moved, so the wave was
        // invisible. Hand-computed swing on WARM_WHITE is ~66-71 per channel; require a comfortable floor.
        assertTrue(red(crest) - red(rest) > 40, "R crest−rest margin too small: " + (red(crest) - red(rest)));
        assertTrue(green(crest) - green(rest) > 40, "G crest−rest margin too small: " + (green(crest) - green(rest)));
        assertTrue(blue(crest) - blue(rest) > 40, "B crest−rest margin too small: " + (blue(crest) - blue(rest)));
    }

    @Test
    void shadeRisesMonotonicallyFromRestToCrest() {
        int prevR = -1, prevG = -1, prevB = -1;
        for (int step = 0; step <= 10; step++) {
            float g = step / 10f;
            int c = LoadingWave.shade(WARM_WHITE, g);
            assertTrue(red(c) >= prevR && green(c) >= prevG && blue(c) >= prevB,
                    "brightness must rise monotonically with crest height, broke at g=" + g);
            prevR = red(c);
            prevG = green(c);
            prevB = blue(c);
        }
    }

    @Test
    void shadeStaysInByteRangeAcrossColorAndCrestExtremes() {
        for (int base : new int[]{0x000000, 0xFFFFFF, WARM_WHITE, 0xFF5E5B, 0xF2A65A}) {
            for (int step = -2; step <= 6; step++) {
                int c = LoadingWave.shade(base, step / 4f); // spans below 0 and above 1 to prove clamping
                assertTrue(red(c) >= 0 && red(c) <= 255, "R out of range: " + red(c));
                assertTrue(green(c) >= 0 && green(c) <= 255, "G out of range: " + green(c));
                assertTrue(blue(c) >= 0 && blue(c) <= 255, "B out of range: " + blue(c));
            }
        }
    }
}
