package com.example.globe.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math gate for the title's outline geometry and the one-shot GLIMMER shine-sweep (title-styling
 * overhaul 2026-07-11). Guards the properties the renderer relies on: the outline stamps a symmetric
 * 8-neighbour ring (crisp 1px halo, no doubled/missing directions); the glimmer is a SINGLE travelling
 * Gaussian crest that fires once over a bounded tick window right after the title appears and is fully off
 * outside that window (never loops); the shine-sweep transform ({@code glimmerShade}) reads on ANY fill --
 * including a near-white one -- via a bright crest against a briefly dimmed baseline, and is an EXACT no-op
 * outside the sweep; and the {@code brighten} primitive (now the loading-wave's tool, unchanged) still lerps
 * a letter's own channels toward white.
 */
class TitleStyleTest {

    private static final float TOL = 1e-4f;

    /** The outline offset set is exactly the 8 neighbours of the origin: all of {-1,0,1}^2 except (0,0),
     *  each once. That's what makes it a symmetric 1px outline rather than a lopsided smear. */
    @Test
    void outlineOffsetsAreTheEightNeighbours() {
        int[][] offs = TitleStyle.OUTLINE_OFFSETS_8;
        assertEquals(8, offs.length, "8-direction outline");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int[] o : offs) {
            assertTrue(o[0] >= -1 && o[0] <= 1 && o[1] >= -1 && o[1] <= 1, "unit offsets only");
            assertFalse(o[0] == 0 && o[1] == 0, "origin (0,0) is the main pass, not an outline stamp");
            assertTrue(seen.add(o[0] + "," + o[1]), "no duplicate direction");
        }
        assertEquals(8, seen.size(), "all 8 distinct neighbours present");
    }

    /** The faded drop shadow (2026-07-11, Peetsa "change the default glow to a faded drop shadow") is a
     *  DIRECTIONAL cast: every stamp offset is strictly down-right (positive dx AND dy), so the title reads as
     *  lit from the upper-left -- unlike the omnidirectional glow ring which stamps all 8 directions including
     *  negatives. There is one alpha per offset, all in (0,1), and they TAPER (each farther stamp is fainter)
     *  so the shadow is soft and faded rather than a hard black edge. */
    @Test
    void dropShadowOffsetsAreDirectionalDownRightWithTaperingBoundedAlpha() {
        int[][] offs = TitleStyle.DROP_SHADOW_OFFSETS_PX;
        float[] alpha = TitleStyle.DROP_SHADOW_ALPHA;
        assertTrue(offs.length >= 1, "at least one drop-shadow stamp");
        assertEquals(offs.length, alpha.length, "one alpha per offset (1:1 with the offsets)");
        for (int[] o : offs) {
            assertTrue(o[0] > 0 && o[1] > 0, "each stamp is strictly down-right (positive dx and dy)");
        }
        for (int i = 0; i < alpha.length; i++) {
            assertTrue(alpha[i] > 0f && alpha[i] < 1f, "alpha in (0,1): a low-opacity soft cast, not opaque");
            if (i > 0) {
                assertTrue(alpha[i] < alpha[i - 1], "alpha tapers (farther stamp fainter) -> faded, not hard");
                // farther offset too (Chebyshev distance grows), reinforcing the soft directional falloff
                assertTrue(Math.max(offs[i][0], offs[i][1]) > Math.max(offs[i - 1][0], offs[i - 1][1]),
                        "each successive stamp is farther out");
            }
        }
    }

    /** outlineOffsets(1) is the SAME SET as the classic 8 neighbours (order may differ; the renderer stamps
     *  all of them). Thickness clamps to [1, MAX] outside its range. */
    @Test
    void outlineOffsetsThicknessOneMatchesTheEightNeighbours() {
        java.util.Set<String> classic = new java.util.HashSet<>();
        for (int[] o : TitleStyle.OUTLINE_OFFSETS_8) classic.add(o[0] + "," + o[1]);
        java.util.Set<String> t1 = asSet(TitleStyle.outlineOffsets(1));
        assertEquals(classic, t1, "thickness 1 == the 8-neighbour ring");

        // Clamp: below 1 and above MAX collapse to the nearest valid thickness.
        assertEquals(t1, asSet(TitleStyle.outlineOffsets(0)), "thickness < 1 clamps to 1");
        assertEquals(t1, asSet(TitleStyle.outlineOffsets(-5)), "negative thickness clamps to 1");
        assertEquals(asSet(TitleStyle.outlineOffsets(TitleStyle.MAX_OUTLINE_THICKNESS)),
                asSet(TitleStyle.outlineOffsets(TitleStyle.MAX_OUTLINE_THICKNESS + 3)), "thickness > MAX clamps to MAX");
    }

    /** For thickness t the offset set is the full square [-t,t]^2 minus the origin: exactly (2t+1)^2 - 1
     *  distinct offsets, each with Chebyshev distance <= t, none the origin, no duplicates. The full square
     *  (not a hull) is what fills the diagonal holes MC's blocky font would otherwise show at t >= 2. */
    @Test
    void outlineOffsetsAreTheFullChebyshevSquareMinusOrigin() {
        for (int t = 1; t <= TitleStyle.MAX_OUTLINE_THICKNESS; t++) {
            int[][] offs = TitleStyle.outlineOffsets(t);
            int expected = (2 * t + 1) * (2 * t + 1) - 1;
            assertEquals(expected, offs.length, "full-square stamp count for thickness " + t);
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int[] o : offs) {
                assertTrue(Math.max(Math.abs(o[0]), Math.abs(o[1])) <= t, "Chebyshev distance <= t");
                assertFalse(o[0] == 0 && o[1] == 0, "origin is the fill, never an outline stamp");
                assertTrue(seen.add(o[0] + "," + o[1]), "no duplicate offset");
            }
            assertEquals(expected, seen.size(), "all offsets distinct");
        }
    }

    private static java.util.Set<String> asSet(int[][] offs) {
        java.util.Set<String> s = new java.util.HashSet<>();
        for (int[] o : offs) s.add(o[0] + "," + o[1]);
        return s;
    }

    /** glowRingAlpha scales each ring's base alpha by the intensity and clamps to [0, CAP]: gentle at the
     *  0.75 default, stronger (but still capped) at max, zero for a negative intensity or an out-of-range ring. */
    @Test
    void glowRingAlphaScalesClampsAndGuardsRings() {
        int rings = TitleStyle.GLOW_RING_ALPHA.length;
        // Intensity 1.0 is the identity (base alphas unchanged).
        for (int r = 0; r < rings; r++) {
            assertEquals(TitleStyle.GLOW_RING_ALPHA[r], TitleStyle.glowRingAlpha(r, 1.0), TOL);
        }
        // The gentle 0.75 default softens every ring below its base alpha.
        for (int r = 0; r < rings; r++) {
            assertEquals(TitleStyle.GLOW_RING_ALPHA[r] * 0.75f, TitleStyle.glowRingAlpha(r, 0.75), TOL);
            assertTrue(TitleStyle.glowRingAlpha(r, 0.75) < TitleStyle.GLOW_RING_ALPHA[r], "0.75 is gentler than base");
        }
        // Never exceeds the cap even at max intensity, and never goes negative.
        for (int r = 0; r < rings; r++) {
            assertTrue(TitleStyle.glowRingAlpha(r, 2.0) <= TitleStyle.GLOW_RING_ALPHA_CAP + TOL, "capped at max");
            assertTrue(TitleStyle.glowRingAlpha(r, 2.0) >= 0f, "never negative");
        }
        // Out-of-range ring indices and non-positive intensity return 0.
        assertEquals(0f, TitleStyle.glowRingAlpha(-1, 1.0), TOL);
        assertEquals(0f, TitleStyle.glowRingAlpha(rings, 1.0), TOL);
        assertEquals(0f, TitleStyle.glowRingAlpha(0, 0.0), TOL);
        assertEquals(0f, TitleStyle.glowRingAlpha(0, -3.0), TOL, "negative intensity floors at 0");

        // The cap actually clamps when a huge intensity would push a ring over CAP.
        assertEquals(TitleStyle.GLOW_RING_ALPHA_CAP, TitleStyle.glowRingAlpha(0, 999.0), TOL);
    }

    /** Glimmer is off (crest 0) whenever sweep progress is negative -- the "no glimmer" sentinel used for the
     *  toggle-off/solid case, Reduce Motion (the overlay maps both to progress < 0), and the static preview. */
    @Test
    void negativeProgressMeansNoGlimmer() {
        for (int i = 0; i < 8; i++) {
            assertEquals(0f, TitleStyle.glimmerGaussian(-1f, i, 8), TOL);
        }
    }

    /** The glimmer is a bounded ONE-SHOT window keyed on the title's age in ticks: nothing before it starts,
     *  a normalized [0,1] ramp while it runs, and nothing again once the single sweep has completed (it never
     *  re-arms / loops). Progress is monotonic increasing across the active window. */
    @Test
    void glimmerWindowIsBoundedOneShot() {
        int start = TitleStyle.GLIMMER_START_TICK;
        int span = TitleStyle.GLIMMER_SPAN_TICKS;
        assertTrue(start >= 0 && span > 0, "sane window constants");

        assertEquals(-1f, TitleStyle.glimmerProgress(0), TOL, "no glimmer the instant the title appears");
        assertEquals(-1f, TitleStyle.glimmerProgress(start - 1L), TOL, "no glimmer before the start tick");
        assertEquals(0f, TitleStyle.glimmerProgress(start), TOL, "sweep begins at progress 0 on the start tick");

        float prev = -1f;
        for (long age = start; age < start + span; age++) {
            float p = TitleStyle.glimmerProgress(age);
            assertTrue(p >= 0f && p < 1.0001f, "progress stays in [0,1) inside the window");
            assertTrue(p > prev, "progress advances monotonically across the window");
            prev = p;
        }
        assertEquals(-1f, TitleStyle.glimmerProgress(start + span), TOL, "one-shot: off once the sweep ends");
        assertEquals(-1f, TitleStyle.glimmerProgress(start + span + 50L), TOL, "stays off -- never loops");
    }

    /** The crest travels: the letter with the peak Gaussian height moves strictly left->right as the sweep
     *  progresses, and the raw crest height is always a normalized [0,1] (peak 1.0, never above). */
    @Test
    void crestTravelsLeftToRightAndStaysNormalized() {
        int count = 10;
        int prevPeak = -1;
        for (float progress = 0f; progress <= 1.0001f; progress += 0.1f) {
            int peakIdx = 0;
            float peakVal = -1f;
            for (int i = 0; i < count; i++) {
                float g = TitleStyle.glimmerGaussian(progress, i, count);
                assertTrue(g >= 0f && g <= 1f + TOL, "crest height stays normalized in [0,1]");
                if (g > peakVal) {
                    peakVal = g;
                    peakIdx = i;
                }
            }
            assertTrue(peakIdx >= prevPeak, "brightest letter should not move backwards as the sweep advances");
            prevPeak = peakIdx;
        }
    }

    /** Early in the sweep the crest sits at/near the FIRST letter; late in the sweep it sits at/near the LAST
     *  letter -- i.e. exactly one full left->right pass across the word, not a loop. The crest also enters and
     *  exits near-zero (the margin) so it never snaps on/off mid-word. Near the crest centre the raw Gaussian
     *  reaches ~1.0 (peak). */
    @Test
    void sweepSpansFirstToLastLetterAndFadesAtBothEnds() {
        int count = 12;
        float startFirst = TitleStyle.glimmerGaussian(0f, 0, count);
        float startLast = TitleStyle.glimmerGaussian(0f, count - 1, count);
        assertTrue(startFirst > startLast, "at sweep start the left edge is the brightest");

        float endFirst = TitleStyle.glimmerGaussian(1f, 0, count);
        float endLast = TitleStyle.glimmerGaussian(1f, count - 1, count);
        assertTrue(endLast > endFirst, "at sweep end the right edge is the brightest");

        // Wide margin => both edges are near-off at the sweep's start/end (clean fade-in / fade-out).
        assertTrue(startFirst < 0.15f, "left edge enters gently, not at full crest");
        assertTrue(endLast < 0.15f, "right edge exits gently, not at full crest");

        // Mid-sweep the crest centre is a full-height peak somewhere in the word.
        float peakMid = 0f;
        for (int i = 0; i < count; i++) peakMid = Math.max(peakMid, TitleStyle.glimmerGaussian(0.5f, i, count));
        assertTrue(peakMid > 0.9f, "the crest centre is a ~1.0 peak mid-sweep");
    }

    /** brighten() lerps each channel toward 255 by {@code boost} (channel + (255-channel)*boost), reaches
     *  pure white only at boost == 1.0, and is a no-op for boost <= 0. */
    @Test
    void brightenLerpsTowardWhite() {
        assertEquals(0x808080, TitleStyle.brighten(0x808080, 0f), "boost 0 is a no-op");
        assertEquals(0x808080, TitleStyle.brighten(0x808080, -0.5f), "negative boost is a no-op");

        // 100 + (255-100)*0.5 = 177.5 -> 178 per channel (halfway from the channel's value to white).
        int lifted = TitleStyle.brighten(0x646464, 0.5f);
        assertEquals(0xB2B2B2, lifted);

        // boost == 1.0 lands every channel exactly on 255 -- pure white -- for ANY starting color.
        int full = TitleStyle.brighten(0xF0F0F0, 1.0f);
        assertEquals(0xFFFFFF, full, "boost 1.0 converges to white; by construction no channel overshoots 255");

        assertEquals(0, TitleStyle.brighten(0x123456, 0.3f) & 0xFF000000, "no alpha bits introduced");
    }

    private static int lum(int rgb) {
        return ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
    }

    /** (c) The critical NO-OP: {@code glimmerShade(any, 0)} returns the base color EXACTLY. This is what makes
     *  the title render at its plain, undimmed color for the vast majority of its on-screen life (glimmerGaussian
     *  returns a hard 0 whenever the sweep isn't running). Also holds for a negative crest height. */
    @Test
    void glimmerShadeIsExactNoOpAtZeroCrest() {
        int[] colors = {0xF3ECDD, 0xFF0000, 0x000000, 0x123456, 0xFFFFFF, 0x808080};
        for (int c : colors) {
            assertEquals(c, TitleStyle.glimmerShade(c, 0f), "crest 0 must return the base color unchanged");
            assertEquals(c, TitleStyle.glimmerShade(c, -0.5f), "a negative crest is also a no-op");
        }
        assertEquals(0, TitleStyle.glimmerShade(0x123456, 0f) & 0xFF000000, "no alpha bits introduced");
    }

    /** (a) REGRESSION -- the near-white bug this fix targets. On the OFF_WHITE default (0xF3ECDD) the crest
     *  ({@code gaussian01=1.0}) must be clearly brighter/whiter than the plain base ({@code gaussian01=0}) by a
     *  LARGE margin. The old "lerp toward white" glimmer on an already near-white fill was imperceptible (white
     *  can't get whiter); the shine-sweep's white-pop plus the surrounding dim make the crest read. */
    @Test
    void offWhiteCrestIsClearlyBrighterThanBase() {
        int offWhite = com.example.globe.core.config.LatitudeConfigData.OFF_WHITE_RGB; // 0xF3ECDD = (243,236,221)
        int base = TitleStyle.glimmerShade(offWhite, 0f);   // no-op == plain base
        int crest = TitleStyle.glimmerShade(offWhite, 1.0f); // full crest glint
        assertEquals(offWhite, base, "gaussian01=0 is the plain base");
        assertTrue(lum(crest) - lum(base) >= 30,
                "the off-white crest is brighter than base by a large margin (the near-white regression)");
        assertTrue((crest & 0xFF) > (base & 0xFF) && ((crest >> 8) & 0xFF) > ((base >> 8) & 0xFF),
                "every channel of the crest is whiter than base -- a crisp near-white glint");
    }

    /** (b) The shine-sweep CONTRAST mechanism: a near-crest-edge letter (small positive crest height, e.g. a
     *  neighbour the crest hasn't reached yet) is DIMMED below the plain base -- factor < 1. This is the
     *  briefly-dimmed baseline the bright crest travels against, and it is what makes the moving shine visible
     *  even on a fill you cannot out-brighten. (Note gaussian01=0 exactly returns base; the dim exists only for
     *  gaussian01 > 0, i.e. while the sweep is actually running.) */
    @Test
    void smallCrestHeightDimsBelowBase() {
        int offWhite = com.example.globe.core.config.LatitudeConfigData.OFF_WHITE_RGB;
        int neighbour = TitleStyle.glimmerShade(offWhite, 0.1f); // a letter far from the crest during a sweep
        assertTrue(lum(neighbour) < lum(offWhite),
                "a small crest height dims the letter below its plain base (the sweep's dimmed baseline)");
        // And that dimmed neighbour is clearly darker than the crest letter -- the travelling shine's contrast.
        int crest = TitleStyle.glimmerShade(offWhite, 1.0f);
        assertTrue(lum(crest) - lum(neighbour) >= 100,
                "the crest letter pops well clear of its dimmed neighbours -- an obvious moving shine");
    }

    /** (d) A SATURATED fill also flashes bright at the crest: pure red's crest pops toward white (its zeroed
     *  channels lift well off 0), so a saturated title glimmers too -- against dimmed dark-red neighbours. */
    @Test
    void saturatedFillPopsBrightAtCrest() {
        int red = 0xFF0000;
        int crest = TitleStyle.glimmerShade(red, 1.0f);
        int r = (crest >> 16) & 0xFF, g = (crest >> 8) & 0xFF, b = crest & 0xFF;
        assertEquals(255, r, "the maxed red channel stays at 255 at the crest");
        assertTrue(g > 120 && b > 120, "the zeroed channels lift well off 0 -- a bright pink-white flash");
        assertTrue(r >= g && r >= b, "still red-dominant -- a bright glint on the color, not a hue-erasing white");
        int minChannelBase = 0; // red's min channel (G or B) is 0
        assertTrue(Math.min(g, b) > minChannelBase, "the crest's min channel is well above base -- visibly brighter");

        // Its dimmed neighbour is a darker red, so the crest clearly stands out.
        int neighbour = TitleStyle.glimmerShade(red, 0.1f);
        assertTrue(lum(crest) - lum(neighbour) >= 100, "bright crest vs dark-red neighbour -- a visible shine on red");
    }

    /** brighten() (now the loading-wave's own tool -- no longer the title glimmer) must lift a saturated/primary
     *  fill by a REAL amount. An earlier MULTIPLICATIVE brighten -- {@code channel * (1 + boost)} clamped to 255 --
     *  left pure primaries visually unchanged: a maxed channel (255) clamped straight back to 255 and a zeroed
     *  channel (0) stayed 0, so 0xFF0000 was a no-op at ANY boost and 0x000000 could never brighten. Every
     *  assertion below FAILS against that old formula (red stays 0xFF0000, black stays 0x000000); the
     *  lerp-toward-white formula lifts the zeroed channels off 0, keeping the loading wave's crest visible on
     *  any base color. */
    @Test
    void saturatedAndPrimaryFillsActuallyBrighten() {
        // Pure red: R stays maxed, G and B lift off zero toward white -> a bright pinkish-red flash.
        int red = 0xFF0000;
        int litRed = TitleStyle.brighten(red, 0.5f);
        assertTrue(litRed != red, "pure red MUST brighten (old multiplicative formula left it 0xFF0000)");
        int r = (litRed >> 16) & 0xFF, g = (litRed >> 8) & 0xFF, b = litRed & 0xFF;
        assertEquals(255, r, "the already-maxed channel stays at 255");
        assertTrue(g > 0 && b > 0, "the zeroed channels lift off 0 (old formula left them stuck at 0)");
        assertTrue(r > g && r > b, "still red-dominant -- a sheen on the color, a sliver of hue survives");
        assertEquals(0xFF8080, litRed, "255 stays 255; 0 + (255-0)*0.5 = 127.5 -> 128 on G and B");

        // Pure black: 0 * (1+boost) is 0 for the old formula at any boost; the lerp moves it toward grey.
        int litBlack = TitleStyle.brighten(0x000000, 0.5f);
        assertTrue(litBlack != 0x000000, "pure black MUST brighten toward grey (old formula left it 0x000000)");
        assertEquals(0x808080, litBlack, "black lerps to mid-grey at boost 0.5 (0 + 255*0.5 = 127.5 -> 128)");
    }

    /** WALL-CLOCK glimmer window (TEST 68 fix): {@code glimmerProgressMs} is the continuous, per-frame-smooth
     *  analogue of the integer-tick {@code glimmerProgress}. It opens/closes at the SAME real time as the tick
     *  window (start/span scaled by {@link TitleStyle#MS_PER_TICK}), ramps monotonically 0..1 inside, is off
     *  (-1) before it opens, and stays off forever once the single sweep completes (never loops). */
    @Test
    void glimmerProgressMsIsContinuousMonotonicOneShotAndMatchesTickWindow() {
        long startMs = TitleStyle.GLIMMER_START_TICK * TitleStyle.MS_PER_TICK;
        long spanMs = TitleStyle.GLIMMER_SPAN_TICKS * TitleStyle.MS_PER_TICK;

        assertEquals(-1f, TitleStyle.glimmerProgressMs(0L), TOL, "no glimmer the instant the title appears");
        assertEquals(-1f, TitleStyle.glimmerProgressMs(startMs - 1L), TOL, "no glimmer before the start ms");
        assertEquals(0f, TitleStyle.glimmerProgressMs(startMs), TOL, "sweep begins at progress 0 at the start ms");
        assertEquals(-1f, TitleStyle.glimmerProgressMs(startMs + spanMs), TOL, "one-shot: off once the sweep ends");
        assertEquals(-1f, TitleStyle.glimmerProgressMs(startMs + spanMs + 5000L), TOL, "stays off -- never loops");

        float prev = -1f;
        for (long ms = startMs; ms < startMs + spanMs; ms++) {
            float p = TitleStyle.glimmerProgressMs(ms);
            assertTrue(p >= 0f && p < 1.0001f, "progress stays in [0,1) inside the window");
            assertTrue(p > prev, "progress advances monotonically every ms");
            prev = p;
        }
    }

    /** ANTI-RUBBER-BAND: sampled at ~60 FPS (16 ms/frame) the wall-clock progress advances EVERY frame and
     *  visits far more distinct values than the {@link TitleStyle#GLIMMER_SPAN_TICKS}-step integer-tick version
     *  ever could -- the tick version changes only ~20x/s, so many frames share a value and the crest visibly
     *  steps (the reported "rubber-band"). This is the property the ms driver restores. */
    @Test
    void glimmerProgressMsAdvancesEveryFrameUnlikeTheTickVersion() {
        long startMs = TitleStyle.GLIMMER_START_TICK * TitleStyle.MS_PER_TICK;
        long spanMs = TitleStyle.GLIMMER_SPAN_TICKS * TitleStyle.MS_PER_TICK;
        java.util.Set<Float> distinct = new java.util.HashSet<>();
        float prev = -1f;
        for (long ms = startMs; ms < startMs + spanMs; ms += 16L) {
            float p = TitleStyle.glimmerProgressMs(ms);
            assertTrue(p > prev, "each 16 ms frame advances the crest (no frozen/stepped frames)");
            distinct.add(p);
            prev = p;
        }
        assertTrue(distinct.size() > TitleStyle.GLIMMER_SPAN_TICKS,
                "wall-clock progress has far finer resolution than the " + TitleStyle.GLIMMER_SPAN_TICKS
                        + "-step tick version");
    }

    /** DIM/FADE composition (TEST 68 pop fix): the new {@code dimScale} arg scales how deep the off-crest
     *  baseline dim goes. dimScale=1 reproduces the original fixed dim EXACTLY (backward compatible); dimScale=0
     *  removes the dim entirely so the sweep's start/end are continuous with the plain title (no brighten
     *  "pop"); the crest glint is independent of dimScale; and the dim is monotonic in dimScale. */
    @Test
    void glimmerShadeDimScaleEasesTheBaselineDim() {
        int offWhite = com.example.globe.core.config.LatitudeConfigData.OFF_WHITE_RGB;

        // dimScale == 1 is byte-identical to the original fixed-dim two-arg overload, for many colors/crests.
        float[] gs = {0.1f, 0.3f, 0.6f, 1.0f};
        int[] colors = {offWhite, 0xFF0000, 0x123456, 0x808080, 0x000000};
        for (int c : colors) {
            for (float g : gs) {
                assertEquals(TitleStyle.glimmerShade(c, g), TitleStyle.glimmerShade(c, g, 1f),
                        "dimScale=1 must equal the fixed-dim two-arg glimmerShade");
            }
        }

        // dimScale == 0 => no baseline dim: an off-crest neighbour (small g) is NOT dimmed below its base
        // (only the crest white-pop remains), which is what makes the sweep ends continuous with the plain
        // title and removes the pop. At full dim the same neighbour DOES sit below base.
        int neighbourFullDim = TitleStyle.glimmerShade(offWhite, 0.1f, 1f);
        int neighbourNoDim = TitleStyle.glimmerShade(offWhite, 0.1f, 0f);
        assertTrue(lum(neighbourFullDim) < lum(offWhite), "at full dim a near-crest neighbour sits below base");
        assertTrue(lum(neighbourNoDim) >= lum(offWhite), "at dimScale 0 it is never dimmed below base");

        // Monotonic: a deeper dimScale never brightens the off-crest baseline (fixed small crest height).
        int prev = Integer.MAX_VALUE;
        for (float ds = 0f; ds <= 1.0001f; ds += 0.25f) {
            int shaded = TitleStyle.glimmerShade(offWhite, 0.1f, ds);
            assertTrue(lum(shaded) <= prev, "more dim must not brighten the off-crest baseline");
            prev = lum(shaded);
        }

        // The crest letter (g=1) has factor 1.0 for ANY dimScale, so its glint is identical however the dim
        // is eased -- the moving shine reads the same at the sweep's dim-free ends as mid-sweep.
        assertEquals(TitleStyle.glimmerShade(offWhite, 1.0f, 0f), TitleStyle.glimmerShade(offWhite, 1.0f, 1f),
                "the crest letter is independent of dimScale");

        // dimScale is clamped to [0,1]; channels stay in range for out-of-range inputs too.
        for (float ds = -0.5f; ds <= 1.5f; ds += 0.5f) {
            int s = TitleStyle.glimmerShade(0x8090A0, 0.4f, ds);
            for (int shift : new int[]{16, 8, 0}) {
                int ch = (s >> shift) & 0xFF;
                assertTrue(ch >= 0 && ch <= 255, "channel in [0,255] for dimScale=" + ds);
            }
        }
        assertEquals(TitleStyle.glimmerShade(0x8090A0, 0.4f, 0f), TitleStyle.glimmerShade(0x8090A0, 0.4f, -3f),
                "dimScale below 0 clamps to 0");
        assertEquals(TitleStyle.glimmerShade(0x8090A0, 0.4f, 1f), TitleStyle.glimmerShade(0x8090A0, 0.4f, 9f),
                "dimScale above 1 clamps to 1");

        // The zero-crest no-op still holds regardless of dimScale.
        assertEquals(0x123456, TitleStyle.glimmerShade(0x123456, 0f, 0.5f), "crest 0 is a no-op for any dimScale");
    }
}
