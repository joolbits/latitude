package com.example.globe.core.ui;

/**
 * S33 (Peetsa 2026-07-21, TEST 123 loading-screen shot): "the top point of the compass rose should just be
 * shorter, pointing to a red N outlined in a shade-darker color. Apply the same rule to the in-game compass."
 *
 * <p>The mod hand-draws its compass rose TWICE — once for the live HUD dial ({@code client.CompassDialRenderer},
 * which needs a live {@code CompassHudConfig}) and once for the bespoke loading screen ({@code
 * mixin.client.LevelLoadingScreenLatitudeOverlayMixin}, which has no config to read). Those two implementations
 * have drifted apart before, so the north-glyph rule lives HERE as pure math both call: the shortened north arm
 * length, the north red, its darker outline tone, and the scale at which outlining is legible rather than blobby.
 *
 * <p><b>Why north is red regardless of theme.</b> A red north point is a cartographic convention older than any
 * of our colour schemes (it is how a magnetic needle is painted), so it is deliberately NOT theme-derived — the
 * same red reads as "north" on every theme, on the loading card, and on a repainted dial. Only the OUTLINE is
 * derived, by darkening that red, which is the "shade-darker" the owner asked for.
 *
 * <p>Zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM).
 */
public final class CompassNorth {

    private CompassNorth() {
    }

    /** The north glyph's red. Fixed across themes (cartographic convention -- see class javadoc). */
    public static final int RED = 0xFFE0362C;

    /** How far the outline tone is darkened from {@link #RED} (0 = same colour, 1 = black). */
    public static final float OUTLINE_DARKEN = 0.45f;

    /**
     * Minimum glyph scale at which the 4-way outline is drawn. Below this the glyph is only a few pixels tall
     * and an outline fills its own counters -- the exact "busy and messy / blobbed" failure the owner rejected
     * in TEST 119 -- so smaller dials fall back to a single drop shadow in the same darker tone.
     */
    public static final float OUTLINE_MIN_SCALE = 0.7f;

    /** Gap (px) kept between the shortened north arm's tip and the glyph above it, so the arm POINTS AT the
     *  letter instead of touching it. */
    public static final int ARM_GLYPH_GAP = 2;

    /** The outline/shadow tone: {@link #RED} darkened by {@link #OUTLINE_DARKEN}, alpha preserved. */
    public static int outlineColor() {
        return darken(RED, OUTLINE_DARKEN);
    }

    /** Should the glyph at this scale get the 4-way outline (vs a plain drop shadow)? */
    public static boolean shouldOutline(float glyphScale) {
        return glyphScale >= OUTLINE_MIN_SCALE;
    }

    /**
     * Length of the SHORTENED north arm of the rose, in pixels from the dial centre. The other three cardinal
     * arms keep {@code fullArmLength}; north stops {@link #ARM_GLYPH_GAP} px below the bottom of the north
     * glyph, so it reads as an arrow pointing at the letter rather than running through it.
     *
     * @param fullArmLength the length the other arms use (typically {@code radius - 4})
     * @param glyphBottomFromCentre distance from the dial centre DOWN to the glyph's lowest drawn pixel
     *        (i.e. the glyph sits between this and the ring)
     * @return the north arm length, always at least 2 so the point never vanishes entirely
     */
    public static int northArmLength(int fullArmLength, int glyphBottomFromCentre) {
        int shortened = glyphBottomFromCentre - ARM_GLYPH_GAP;
        if (shortened > fullArmLength) {
            shortened = fullArmLength;
        }
        return Math.max(2, shortened);
    }

    /** Darken an ARGB colour toward black by {@code factor} in {@code [0,1]}, preserving alpha. */
    public static int darken(int argb, float factor) {
        float f = factor < 0f ? 0f : (factor > 1f ? 1f : factor);
        int a = (argb >>> 24) & 0xFF;
        int r = Math.round(((argb >> 16) & 0xFF) * (1f - f));
        int g = Math.round(((argb >> 8) & 0xFF) * (1f - f));
        int b = Math.round((argb & 0xFF) * (1f - f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
