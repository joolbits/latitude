package com.example.globe.client;

import com.example.globe.core.config.LatitudeConfigData;
import com.example.globe.core.ui.OverlayLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ZoneEnterTitleOverlay {
    private static Component title;
    private static long startWorldTime = Long.MIN_VALUE;
    private static long endWorldTime = Long.MIN_VALUE;
    private static float scale = 1.8f;

    private static final int FADE_TICKS = 10;

    /** GUI-scale parity (audit H1/M1): per-side breathing room kept clear when fitting/clamping the title
     *  into the screen, and the fit-to-width floor below which the title is never shrunk (below this it's
     *  illegible; the clamp keeps it centered instead of shrinking further). */
    private static final int SIDE_MARGIN = 6;
    private static final double MIN_TITLE_SCALE = 0.5;

    private ZoneEnterTitleOverlay() {
    }

    /** Clears any in-flight title on disconnect. World-time keys are per-world; without this, a title
     *  started in one world could resurrect mid-fade in the next (whose game time may be behind). */
    public static void reset() {
        title = null;
        startWorldTime = Long.MIN_VALUE;
        endWorldTime = Long.MIN_VALUE;
    }

    public static void trigger(String titleText, int durationTicks, double scale) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return;
        }

        int dt = Math.max(1, durationTicks);
        float s = (float) scale;

        ZoneEnterTitleOverlay.title = Component.literal(titleText);
        ZoneEnterTitleOverlay.scale = s;
        ZoneEnterTitleOverlay.startWorldTime = client.level.getGameTime();
        ZoneEnterTitleOverlay.endWorldTime = ZoneEnterTitleOverlay.startWorldTime + dt;
    }

    public static boolean isActive() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || title == null) {
            return false;
        }

        long now = client.level.getGameTime();
        return now >= startWorldTime && now < endWorldTime;
    }

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || title == null) {
            return;
        }

        long now = client.level.getGameTime();
        if (now < startWorldTime || now >= endWorldTime) {
            return;
        }

        float alpha = 1.0f;
        long age = now - startWorldTime;
        long remaining = endWorldTime - now;

        if (age < FADE_TICKS) {
            alpha = (float) age / (float) FADE_TICKS;
        } else if (remaining < FADE_TICKS) {
            alpha = (float) remaining / (float) FADE_TICKS;
        }

        if (alpha <= 0.001f) {
            return;
        }

        int a = (int) (alpha * 255.0f);
        Font font = client.font;
        String raw = title.getString();
        int contentW = styledWidth(font, raw);

        // M1 -- fit-to-width: a long biome title at a large scale on a small GUI-scale canvas would spill off
        // both edges. Shrink the effective scale just enough to fit (never below MIN_TITLE_SCALE); the H1
        // clamp below keeps it centered if even the floor is too wide.
        double drawScale = OverlayLayout.fitScale(scale, contentW, screenW - 2 * SIDE_MARGIN, MIN_TITLE_SCALE);
        int halfW = (int) Math.ceil(contentW * drawScale / 2.0);
        int halfH = (int) Math.ceil(font.lineHeight * drawScale / 2.0);

        // H1 -- re-clamp every frame: zoneEnterTitleOffsetX/Y is an ABSOLUTE pixel offset set by HUD Studio at
        // the EDIT resolution and never re-derived. Drag it near an edge on a large canvas, then switch to a
        // smaller GUI scale, and the raw center lands off-screen with no way back (unlike the fraction-based
        // compass, which survives the same sequence). Clamping the computed center to the styled title box
        // every frame keeps the title at least fully on-screen and draggable back. (A full fraction migration
        // would have to change how HUD Studio stores the drag -- out of this lane's scope -- so the render
        // clamp is the self-contained fix.)
        int cx = OverlayLayout.clampCenter((screenW / 2) + LatitudeConfig.zoneEnterTitleOffsetX, halfW, screenW);
        int cy = OverlayLayout.clampCenter((screenH / 2) + LatitudeConfig.zoneEnterTitleOffsetY, halfH, screenH);
        drawTitleLineAt(ctx, cx, cy, raw, drawScale, a, glimmerProgress(age));
    }

    /** Normalized progress (0..1) through the one-shot glimmer sweep for a title of the given age in ticks,
     *  or -1 when there's no glimmer this frame: the toggle is off, Reduce Motion is on, or the age is outside
     *  the single-sweep window ({@link com.example.globe.core.ui.TitleStyle#glimmerProgress}). The shine-sweep
     *  transform (which works for ALL presets, not just rainbow/aurora) lives in {@link #drawStyledTitle} so
     *  every caller of the shared draw path stays consistent. */
    static float glimmerProgress(long age) {
        if (!LatitudeConfig.zoneEnterTitleGlimmer || LatitudeConfig.reduceMotion) {
            return -1f;
        }
        return com.example.globe.core.ui.TitleStyle.glimmerProgress(age);
    }

    /**
     * Draws one title line, centered at ({@code cx},{@code cy}) in screen space, using the SAME case /
     * color / letter-spacing / shadow styling as the zone-enter title. Shared by the zone-enter title,
     * the HUD Studio preview, and the B-3c hemisphere-title channel so all title text is one visual
     * system (a hemisphere title reads exactly like a zone title, just in its own channel/position).
     */
    public static void drawTitleLineAt(GuiGraphicsExtractor ctx, int cx, int cy, String rawText, double scale, int alphaByte) {
        drawTitleLineAt(ctx, cx, cy, rawText, scale, alphaByte, -1f);
    }

    /**
     * As {@link #drawTitleLineAt(GuiGraphicsExtractor, int, int, String, double, int)}, plus the
     * one-shot glimmer progress: {@code glimmerProgress} in [0,1] drives the single shine-sweep crest
     * as the title appears (a bright crest against a briefly dimmed baseline, on each letter's OWN color --
     * solid, custom, rainbow or aurora); pass {@code -1} for no glimmer (toggle off, static Studio preview,
     * Reduce Motion). Hemisphere titles pass
     * their own progress here so they glimmer coherently with zone titles.
     */
    public static void drawTitleLineAt(GuiGraphicsExtractor ctx, int cx, int cy, String rawText, double scale,
                                       int alphaByte, float glimmerProgress) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) {
            return;
        }
        Font tr = client.font;
        String styled = applyCase(rawText, LatitudeConfig.zoneEnterTitleCase);
        var m = ctx.pose();
        m.pushMatrix();
        try {
            m.translate(cx, cy);
            m.scale((float) scale, (float) scale);
            drawStyledTitle(ctx, tr, styled, alphaByte, (float) scale, glimmerProgress);
        } finally {
            m.popMatrix();
        }
    }

    public static void renderStaticAt(GuiGraphicsExtractor ctx, int screenW, int screenH, String text, double scale, int offsetX, int offsetY) {
        renderStaticAt(ctx, screenW, screenH, text, scale, offsetX, offsetY, -1f);
    }

    /** As {@link #renderStaticAt(GuiGraphicsExtractor, int, int, String, double, int, int)}, but with an
     *  explicit glimmer progress ([0,1], or -1 for none) so the HUD Studio can REPLAY the one-shot glimmer as
     *  feedback (on Title-tab open / toggle-ON) even though the preview is otherwise static. */
    public static void renderStaticAt(GuiGraphicsExtractor ctx, int screenW, int screenH, String text, double scale,
                                      int offsetX, int offsetY, float glimmerProgress) {
        int cx = (screenW / 2) + offsetX;
        int cy = (screenH / 2) + offsetY;
        drawTitleLineAt(ctx, cx, cy, text, scale, 0xFF, glimmerProgress);
    }

    // Shared by both render() (real gameplay) and renderStaticAt() (the HUD Studio live preview) so the two
    // paths can never drift out of sync on color/case/spacing styling. text is drawn centered at the local
    // (0,0) origin -- caller is expected to have already translated/scaled the pose matrix. Always goes through
    // the per-character drawSpacedText loop (even at spacing=0) rather than branching to ctx.centeredText, so
    // there is exactly one code path to keep in sync, not two.
    /** U-B: the styled title's real measured width (case + letter-spacing applied) — used by the Studio's
     *  drag hit-test so the grab box matches the letters actually drawn, at every case/spacing setting. */
    public static int styledWidth(Font font, String rawText) {
        String styled = applyCase(rawText, LatitudeConfig.zoneEnterTitleCase);
        int spacing = LatitudeConfig.zoneEnterTitleLetterSpacing;
        int n = styled.length();
        int totalWidth = 0;
        for (int i = 0; i < n; i++) {
            totalWidth += font.width(String.valueOf(styled.charAt(i)));
        }
        if (n > 1) totalWidth += spacing * (n - 1);
        return totalWidth;
    }

    /**
     * The single styled-title draw path (real gameplay, hemisphere channel, and Studio preview all reach
     * here). Draws, back-to-front: (1) the diffuse-shadow glow halo, (1b) the faded soft directional drop
     * shadow, (2) the crisp outline, (3) the main fill with the one-shot color-aware glimmer. {@code scale} is the
     * pose's current scale, used to keep the outline/glow offsets a fixed size in SCREEN pixels rather than
     * fattening with Title Size. {@code glimmerProgress} is the sweep progress in [0,1] (or -1 for none).
     */
    private static void drawStyledTitle(GuiGraphicsExtractor ctx, Font font, String text, int alphaByte,
                                        float scale, float glimmerProgress) {
        int spacing = LatitudeConfig.zoneEnterTitleLetterSpacing;
        int alphaMask = (alphaByte & 0xFF) << 24;
        float invScale = scale > 0f ? 1.0f / scale : 1.0f;

        // (1) Diffuse-shadow glow -- soft dark halo, drawn first so everything else sits on top of it.
        if (LatitudeConfig.zoneEnterTitleGlow) {
            for (int ring = 0; ring < com.example.globe.core.ui.TitleStyle.GLOW_RING_RADII_PX.length; ring++) {
                float radiusLocal = com.example.globe.core.ui.TitleStyle.GLOW_RING_RADII_PX[ring] * invScale;
                int ringAlpha = Math.round((alphaByte & 0xFF)
                        * com.example.globe.core.ui.TitleStyle.glowRingAlpha(ring,
                                LatitudeConfig.zoneEnterTitleGlowIntensity));
                if (ringAlpha <= 0) continue;
                int glowArgb = (ringAlpha << 24); // black halo (RGB = 0)
                for (int[] off : com.example.globe.core.ui.TitleStyle.OUTLINE_OFFSETS_8) {
                    drawOffsetPass(ctx, font, text, spacing, off[0] * radiusLocal, off[1] * radiusLocal, glowArgb);
                }
            }
        }

        // (1b) Faded drop shadow -- a soft DIRECTIONAL dark cast to the lower-right (title lit from the
        // upper-left), drawn behind the outline and fill. Distinct from BOTH the omnidirectional glow halo
        // above (which radiates in all 8 directions) AND MC's stark hard single-pixel vanilla shadow: two
        // low-alpha black stamps at tapering alpha (0.35 / 0.18 of the title alpha) make it soft + faded.
        if (LatitudeConfig.zoneEnterTitleDropShadow) {
            for (int s = 0; s < com.example.globe.core.ui.TitleStyle.DROP_SHADOW_OFFSETS_PX.length; s++) {
                int[] off = com.example.globe.core.ui.TitleStyle.DROP_SHADOW_OFFSETS_PX[s];
                int shadowAlpha = Math.round((alphaByte & 0xFF)
                        * com.example.globe.core.ui.TitleStyle.DROP_SHADOW_ALPHA[s]);
                if (shadowAlpha <= 0) continue;
                int shadowArgb = (shadowAlpha << 24); // black cast (RGB = 0)
                drawOffsetPass(ctx, font, text, spacing, off[0] * invScale, off[1] * invScale, shadowArgb);
            }
        }

        // (2) Outline -- crisp 1 screen-px stamp of the text in the outline color behind the fill.
        if (LatitudeConfig.zoneEnterTitleOutline) {
            int outlineArgb = alphaMask | (LatitudeConfig.zoneEnterTitleOutlineRgb & 0xFFFFFF);
            for (int[] off : com.example.globe.core.ui.TitleStyle.outlineOffsets(
                    LatitudeConfig.zoneEnterTitleOutlineThickness)) {
                drawOffsetPass(ctx, font, text, spacing, off[0] * invScale, off[1] * invScale, outlineArgb);
            }
        }

        // (3) Main fill. The Drop Shadow toggle now drives the FADED soft directional shadow drawn in pass (1b)
        // above, NOT MC's hard vanilla shadow -- so the shadow arg to the fill pass is always false (drawing it
        // here too would double-shadow: the crisp black vanilla offset stacked on the soft faded cast).
        // ONE unified fill path for every preset: each letter's base color (solid/custom, or the rainbow/aurora
        // gradient) is passed through the one-shot SHINE-SWEEP via TitleStyle.glimmerShade -- a bright crest
        // travelling against a briefly, gently dimmed baseline, so the shine reads on ANY fill including the
        // near-white OFF_WHITE default (you cannot out-brighten white, so the surroundings dim and the crest
        // pops). At glimmerProgress < 0 the crest is 0 for every letter and glimmerShade is an exact no-op, so
        // this collapses to the plain colored fill.
        LatitudeConfigData.TitleColorPreset preset = LatitudeConfig.zoneEnterTitleColorPreset;
        int visibleCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') visibleCount++;
        }
        final int visible = Math.max(1, visibleCount);
        final float glimmer = glimmerProgress;
        // RAINBOW = static ROYGBIV sweep across the letters (no drift); AURORA = the flowing/drifting gradient.
        final boolean gradient = preset == LatitudeConfigData.TitleColorPreset.RAINBOW
                || preset == LatitudeConfigData.TitleColorPreset.AURORA;
        final boolean flowing = preset == LatitudeConfigData.TitleColorPreset.AURORA;
        final int solidRgb = gradient ? 0 : (titleColorRgb(preset) & 0xFFFFFF);
        drawSpacedText(ctx, font, text, 0, 0, false, spacing,
                idx -> {
                    int base = gradient
                            ? (flowing
                                    ? RainbowText.flowingColor(idx, visible,
                                            com.example.globe.core.ui.FlowingGradient.DEFAULT_CYCLE_SECONDS)
                                    : com.example.globe.core.ui.FlowingGradient.staticColorFor(idx, visible))
                            : solidRgb;
                    base = com.example.globe.core.ui.TitleStyle.glimmerShade(base,
                            com.example.globe.core.ui.TitleStyle.glimmerGaussian(glimmer, idx, visible));
                    return alphaMask | base;
                });
    }

    // Draws the full styled string once in a single flat color, offset by (ox, oy) LOCAL units (the pose is
    // already scaled, so a local offset of 1/scale == 1 screen pixel), with no MC shadow. Used for the
    // outline and diffuse-glow passes. Pushes/pops the matrix so the offset never leaks into later passes.
    private static void drawOffsetPass(GuiGraphicsExtractor ctx, Font font, String text, int spacing,
                                       float ox, float oy, int argb) {
        var m = ctx.pose();
        m.pushMatrix();
        try {
            m.translate(ox, oy);
            drawSpacedText(ctx, font, text, 0, 0, false, spacing, idx -> argb);
        } finally {
            m.popMatrix();
        }
    }

    // Draws text centered at (centerX, centerY), inserting `spacing` extra pixels between adjacent characters
    // (negative tightens, positive widens). colorForVisibleIndex is called once per non-space character, in
    // order, so callers can either return one fixed color or cycle a palette (e.g. RainbowText's).
    private static void drawSpacedText(GuiGraphicsExtractor ctx, Font font, String text, int centerX, int centerY,
                                        boolean shadow, int spacing, java.util.function.IntUnaryOperator colorForVisibleIndex) {
        int n = text.length();
        int totalWidth = 0;
        for (int i = 0; i < n; i++) {
            totalWidth += font.width(String.valueOf(text.charAt(i)));
        }
        if (n > 1) {
            totalWidth += spacing * (n - 1);
        }

        int x = centerX - totalWidth / 2;
        int y = centerY - font.lineHeight / 2;
        int visibleIdx = 0;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            String s = String.valueOf(c);
            int charWidth = font.width(s);
            if (c != ' ') {
                ctx.text(font, s, x, y, colorForVisibleIndex.applyAsInt(visibleIdx), shadow);
                visibleIdx++;
            }
            x += charWidth;
            if (i < n - 1) {
                x += spacing;
            }
        }
    }

    private static int titleColorRgb(LatitudeConfigData.TitleColorPreset preset) {
        return switch (preset) {
            case GOLD -> 0xD4A74A;
            case RED -> 0xFF5555;
            case CYAN -> 0x55FFFF;
            case GREEN -> 0x55FF55;
            case CUSTOM -> LatitudeConfig.zoneEnterTitleRgb;
            case OFF_WHITE -> LatitudeConfigData.OFF_WHITE_RGB;
            case WHITE, RAINBOW, AURORA -> 0xFFFFFF; // RAINBOW/AURORA never reach here -- handled above.
        };
    }

    private static String applyCase(String text, LatitudeConfigData.TitleCaseMode mode) {
        return switch (mode) {
            case UPPERCASE -> text.toUpperCase(java.util.Locale.ROOT);
            case LOWERCASE -> text.toLowerCase(java.util.Locale.ROOT);
            case MOCKING -> mockingCase(text);
            case NORMAL -> text;
        };
    }

    // "mOcKiNg SpOnGeBoB" style: alternates lower/upper per LETTER (spaces/punctuation pass through untouched
    // without breaking the alternation), starting lowercase.
    private static String mockingCase(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean upper = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upper = !upper;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
