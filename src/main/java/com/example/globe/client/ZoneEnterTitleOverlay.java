package com.example.globe.client;

import com.example.globe.core.config.LatitudeConfigData;
import com.example.globe.core.ui.OverlayLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ZoneEnterTitleOverlay {
    private static Component title;
    // WALL-CLOCK lifecycle (System.currentTimeMillis at trigger + duration in ms). The rendered fade alpha and
    // the glimmer sweep are driven from wall time, NOT client.level.getGameTime(): the game-tick clock stalls
    // and can even snap BACKWARDS during teleport chunk-gen (the integrated ClientLevel free-runs its tick
    // count while the server thread is blocked, then gets corrected to the server's lower authoritative value
    // on the next time sync), which made the title freeze at alpha 0 ("delay"), pop in, then vanish/return.
    // Wall-clock is strictly monotonic and advances every frame, so the fade + shine stay smooth through a
    // teleport hitch. Cross-world resurrection is handled by reset() on disconnect (wired in GlobeModClient).
    private static long startMs = Long.MIN_VALUE;
    private static long durationMs = 0L;
    private static float scale = 1.8f;

    private static final int FADE_TICKS = 10;
    /** Fade-in / fade-out ramp length in wall-clock ms (the tick count kept for parity with the old timing). */
    private static final long FADE_MS = FADE_TICKS * 50L;

    /** GUI-scale parity (audit H1/M1): per-side breathing room kept clear when fitting/clamping the title
     *  into the screen, and the fit-to-width floor below which the title is never shrunk (below this it's
     *  illegible; the clamp keeps it centered instead of shrinking further). */
    private static final int SIDE_MARGIN = 6;
    private static final double MIN_TITLE_SCALE = 0.5;

    /** Two-line lockup (creative-director rec D, approved 2026-07-11): when the zone title carries a degrees
     *  suffix (e.g. "Subpolar 66°N") it renders as TWO lines -- the zone NAME at the full Title Size on
     *  line 1, and the degrees token beneath it at {@link #LOCKUP_SUB_SCALE} of that scale on line 2, separated
     *  by {@link #LOCKUP_GAP_PX} gui-px (at scale 1, and scaling with Title Size). Titles with no degrees token
     *  (a single word, a degrees-off title, or a hemisphere title) render as a single line via the same path. */
    private static final double LOCKUP_SUB_SCALE = 0.55;
    private static final int LOCKUP_GAP_PX = 4;

    private ZoneEnterTitleOverlay() {
    }

    /** Clears any in-flight title on disconnect. Without this, a title started in one world could keep
     *  rendering after a world switch; on wall-clock timing this is the ONLY resurrection guard needed
     *  (ms never runs backwards, so there is no stale-tick-window race to defend against). */
    public static void reset() {
        title = null;
        startMs = Long.MIN_VALUE;
        durationMs = 0L;
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
        ZoneEnterTitleOverlay.startMs = System.currentTimeMillis();
        ZoneEnterTitleOverlay.durationMs = dt * 50L;
    }

    public static boolean isActive() {
        if (title == null || startMs == Long.MIN_VALUE) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - startMs;
        return elapsed >= 0L && elapsed < durationMs;
    }

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || title == null || startMs == Long.MIN_VALUE) {
            return;
        }

        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed < 0L || elapsed >= durationMs) {
            return;
        }

        float alpha = 1.0f;
        long remaining = durationMs - elapsed;

        if (elapsed < FADE_MS) {
            alpha = (float) elapsed / (float) FADE_MS;
        } else if (remaining < FADE_MS) {
            alpha = (float) remaining / (float) FADE_MS;
        }

        if (alpha <= 0.001f) {
            return;
        }

        int a = (int) (alpha * 255.0f);
        Font font = client.font;
        String raw = title.getString();
        com.example.globe.core.ui.TitleStyle.GlimmerFrame frame = glimmerFrame(elapsed);

        // The (possibly two-line) title box, in unscaled gui-px, so the fit/clamp below see the TRUE lockup
        // extent (a degrees title is taller than a single line, and its widest line may be the name OR the sub).
        TitleBox box = measure(font, raw);

        // M1 -- fit-to-width: a long biome title at a large scale on a small GUI-scale canvas would spill off
        // both edges. Shrink the effective scale just enough to fit (never below MIN_TITLE_SCALE); the H1
        // clamp below keeps it centered if even the floor is too wide. The BLOOM's 2% scale swell is folded
        // into the DESIRED scale BEFORE fitScale, so at the swell peak fitScale still shrinks the title back
        // inside the screen (no edge clipping), and halfW/halfH are measured from the swelled drawScale so the
        // clamp box breathes with it -- WYSIWYG at every frame of the swell.
        double desired = scale * (1.0 + frame.swell());
        double drawScale = OverlayLayout.fitScale(desired, (int) Math.ceil(box.contentW()),
                screenW - 2 * SIDE_MARGIN, MIN_TITLE_SCALE);
        int halfW = (int) Math.ceil(box.contentW() * drawScale / 2.0);
        int halfH = (int) Math.ceil(box.contentH() * drawScale / 2.0);

        // H1 -- re-clamp every frame: zoneEnterTitleOffsetX/Y is an ABSOLUTE pixel offset set by HUD Studio at
        // the EDIT resolution and never re-derived. Drag it near an edge on a large canvas, then switch to a
        // smaller GUI scale, and the raw center lands off-screen with no way back (unlike the fraction-based
        // compass, which survives the same sequence). Clamping the computed center to the styled title box
        // every frame keeps the title at least fully on-screen and draggable back. (A full fraction migration
        // would have to change how HUD Studio stores the drag -- out of this lane's scope -- so the render
        // clamp is the self-contained fix.)
        int cx = OverlayLayout.clampCenter((screenW / 2) + LatitudeConfig.zoneEnterTitleOffsetX, halfW, screenW);
        int cy = OverlayLayout.clampCenter((screenH / 2) + LatitudeConfig.zoneEnterTitleOffsetY, halfH, screenH);
        drawLockup(ctx, cx, cy, box, drawScale, a, frame);
    }

    /** The gated glimmer ENVELOPE frame for a title that appeared {@code elapsedMs} ago (wall-clock), or
     *  {@link com.example.globe.core.ui.TitleStyle.GlimmerFrame#INERT} when there's no glimmer this frame: the
     *  toggle is off or Reduce Motion is on. Wall-clock (not the game-tick age) so the crest/bloom advance
     *  smoothly per frame and never rubber-band when the server tick stalls during a teleport. The four-phase
     *  choreography (appear -> hero crest -> bloom -> melt) lives in
     *  {@link com.example.globe.core.ui.TitleStyle#glimmerFrame}; {@link #drawStyledTitle} turns one frame into
     *  the per-letter shade + whole-title bloom, so every caller of the shared draw path stays consistent. */
    static com.example.globe.core.ui.TitleStyle.GlimmerFrame glimmerFrame(long elapsedMs) {
        if (!LatitudeConfig.zoneEnterTitleGlimmer || LatitudeConfig.reduceMotion) {
            return com.example.globe.core.ui.TitleStyle.GlimmerFrame.INERT;
        }
        return com.example.globe.core.ui.TitleStyle.glimmerFrame(elapsedMs);
    }

    /**
     * Draws one title line, centered at ({@code cx},{@code cy}) in screen space, using the SAME case /
     * color / letter-spacing / shadow styling as the zone-enter title. Shared by the zone-enter title,
     * the HUD Studio preview, and the B-3c hemisphere-title channel so all title text is one visual
     * system (a hemisphere title reads exactly like a zone title, just in its own channel/position).
     */
    public static void drawTitleLineAt(GuiGraphicsExtractor ctx, int cx, int cy, String rawText, double scale, int alphaByte) {
        drawTitleLineAt(ctx, cx, cy, rawText, scale, alphaByte,
                com.example.globe.core.ui.TitleStyle.GlimmerFrame.INERT, true);
    }

    /**
     * As {@link #drawTitleLineAt(GuiGraphicsExtractor, int, int, String, double, int)}, plus a glimmer
     * {@code frame}, drawn as a HERO line (its letters carry the crest sweep). Hemisphere titles use this so
     * each hemisphere line glimmers coherently with zone titles.
     */
    public static void drawTitleLineAt(GuiGraphicsExtractor ctx, int cx, int cy, String rawText, double scale,
                                       int alphaByte, com.example.globe.core.ui.TitleStyle.GlimmerFrame frame) {
        drawTitleLineAt(ctx, cx, cy, rawText, scale, alphaByte, frame, true);
    }

    /**
     * As above, with an explicit {@code heroLine} flag. A HERO line's letters carry the one-shot crest sweep
     * (a bright crest against a briefly dimmed baseline, on each letter's OWN color -- solid, custom, rainbow or
     * aurora) plus the whole-title bloom lift; a NON-hero line (the degrees line of the two-line lockup) gets no
     * crest of its own but still shares the frame's baseline dim and bloom, so the lockup breathes together.
     * Pass {@link com.example.globe.core.ui.TitleStyle.GlimmerFrame#INERT} for no glimmer (toggle off, static
     * Studio preview at rest, Reduce Motion).
     */
    public static void drawTitleLineAt(GuiGraphicsExtractor ctx, int cx, int cy, String rawText, double scale,
                                       int alphaByte, com.example.globe.core.ui.TitleStyle.GlimmerFrame frame,
                                       boolean heroLine) {
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
            drawStyledTitle(ctx, tr, styled, alphaByte, (float) scale, frame, heroLine);
        } finally {
            m.popMatrix();
        }
    }

    /** Draws the (possibly two-line) title lockup centered at ({@code cx},{@code cy}). Single-line titles draw
     *  as one HERO line. Two-line lockups draw the NAME as the hero line (the crest sweeps its letters) at
     *  {@code drawScale}, and the degrees token beneath it at {@code drawScale * LOCKUP_SUB_SCALE} as a NON-hero
     *  line -- no crest of its own, but it shares the frame's baseline dim + bloom + (via the pose scale) swell
     *  so the whole lockup breathes together. {@code drawScale} is the already-fitted, already-swelled scale. */
    private static void drawLockup(GuiGraphicsExtractor ctx, int cx, int cy, TitleBox box, double drawScale,
                                   int alphaByte, com.example.globe.core.ui.TitleStyle.GlimmerFrame frame) {
        if (!box.twoLine()) {
            drawTitleLineAt(ctx, cx, cy, box.line1(), drawScale, alphaByte, frame, true);
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) {
            return;
        }
        int lineHeight = client.font.lineHeight;
        double line1H = lineHeight * drawScale;
        double line2Scale = drawScale * LOCKUP_SUB_SCALE;
        double line2H = lineHeight * line2Scale;
        double gap = LOCKUP_GAP_PX * drawScale;
        double totalH = line1H + gap + line2H;
        double top = cy - totalH / 2.0;
        int line1Cy = (int) Math.round(top + line1H / 2.0);
        int line2Cy = (int) Math.round(top + line1H + gap + line2H / 2.0);
        drawTitleLineAt(ctx, cx, line1Cy, box.line1(), drawScale, alphaByte, frame, true);
        drawTitleLineAt(ctx, cx, line2Cy, box.line2(), line2Scale, alphaByte, frame, false);
    }

    public static void renderStaticAt(GuiGraphicsExtractor ctx, int screenW, int screenH, String text, double scale, int offsetX, int offsetY) {
        renderStaticAt(ctx, screenW, screenH, text, scale, offsetX, offsetY,
                com.example.globe.core.ui.TitleStyle.GlimmerFrame.INERT);
    }

    /** As {@link #renderStaticAt(GuiGraphicsExtractor, int, int, String, double, int, int)}, but with an
     *  explicit glimmer {@code frame} so the HUD Studio can REPLAY the one-shot glimmer choreography as feedback
     *  (on Title-tab open / toggle-ON) even though the preview is otherwise static. Lockup-aware: a degrees
     *  sample renders as the two-line lockup, exactly like gameplay. Uses the RAW scale (no fitScale, matching
     *  the Studio backing plate); the BLOOM swell is still applied so the replayed swell breathes here too. */
    public static void renderStaticAt(GuiGraphicsExtractor ctx, int screenW, int screenH, String text, double scale,
                                      int offsetX, int offsetY, com.example.globe.core.ui.TitleStyle.GlimmerFrame frame) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) {
            return;
        }
        int cx = (screenW / 2) + offsetX;
        int cy = (screenH / 2) + offsetY;
        double drawScale = scale * (1.0 + frame.swell());
        TitleBox box = measure(client.font, text);
        drawLockup(ctx, cx, cy, box, drawScale, 0xFF, frame);
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

    /** The measured (possibly two-line) title box: unscaled content width/height in gui-px plus the split lines.
     *  {@code twoLine} is true only for a degrees-lockup title; otherwise {@code line2} is null and it's one
     *  line. Callers multiply the width/height by their draw scale for the on-screen box (fit, clamp, hit-test,
     *  Studio backing plate) so those all track the true two-line extent. */
    public record TitleBox(double contentW, double contentH, boolean twoLine, String line1, String line2) {
    }

    /** Measures the styled title into a {@link TitleBox}, honoring the two-line degrees lockup
     *  ({@link com.example.globe.core.ui.TitleStyle#splitLockup} on the RAW title). Single-line: width = styled
     *  width, height = one line. Two-line: width = the WIDER of the name (full scale) and the degrees line
     *  (pre-multiplied by {@link #LOCKUP_SUB_SCALE}), height = name line + {@link #LOCKUP_GAP_PX} gap + degrees
     *  line, all in unscaled gui-px so a single {@code * drawScale} gives the on-screen box. */
    public static TitleBox measure(Font font, String rawText) {
        String[] parts = com.example.globe.core.ui.TitleStyle.splitLockup(rawText);
        if (parts[1] == null) {
            return new TitleBox(styledWidth(font, parts[0]), font.lineHeight, false, parts[0], null);
        }
        int nameW = styledWidth(font, parts[0]);
        int degW = styledWidth(font, parts[1]);
        double contentW = Math.max(nameW, degW * LOCKUP_SUB_SCALE);
        double contentH = font.lineHeight * (1.0 + LOCKUP_SUB_SCALE) + LOCKUP_GAP_PX;
        return new TitleBox(contentW, contentH, true, parts[0], parts[1]);
    }

    /**
     * The single styled-title draw path (real gameplay, hemisphere channel, and Studio preview all reach
     * here). Draws, back-to-front: (1) the diffuse-shadow glow halo, (1b) the faded soft directional drop
     * shadow, (2) the crisp outline, (3) the main fill with the one-shot color-aware glimmer choreography.
     * {@code scale} is the pose's current scale, used to keep the outline/glow offsets a fixed size in SCREEN
     * pixels rather than fattening with Title Size. {@code frame} is the glimmer envelope frame this line renders
     * at (crest + dim + pop for a HERO line, plus the whole-title bloom lift for every line);
     * {@code heroLine} = whether this line's letters carry the travelling crest (line 1 / single-line titles) or
     * only share the baseline dim + bloom (the degrees line of a lockup). {@link
     * com.example.globe.core.ui.TitleStyle.GlimmerFrame#INERT} renders the plain title.
     */
    private static void drawStyledTitle(GuiGraphicsExtractor ctx, Font font, String text, int alphaByte,
                                        float scale, com.example.globe.core.ui.TitleStyle.GlimmerFrame frame,
                                        boolean heroLine) {
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
        // gradient) runs through the glimmer choreography. A HERO line's letters get the travelling SHINE-SWEEP
        // via TitleStyle.glimmerShade (a bright crest against a briefly, gently dimmed baseline, reading on ANY
        // fill including the near-white OFF_WHITE default); a NON-hero line (the degrees line of a lockup) gets
        // only the uniform baseline dim via dimToFloor so it sits at the same dimmed level the crest travels
        // against. AFTER the per-letter shade, the whole title is lifted uniformly toward white by the frame's
        // BLOOM amount (brighten) -- both lines share it, so the lockup blooms together. On an INERT frame the
        // crest is 0, the dim floor is 1.0, and bloom is 0, so this collapses to the plain colored fill.
        LatitudeConfigData.TitleColorPreset preset = LatitudeConfig.zoneEnterTitleColorPreset;
        int visibleCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') visibleCount++;
        }
        final int visible = Math.max(1, visibleCount);
        final com.example.globe.core.ui.TitleStyle.GlimmerFrame f = frame;
        final boolean hero = heroLine;
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
                    int shaded = hero
                            ? com.example.globe.core.ui.TitleStyle.glimmerShade(base,
                                    com.example.globe.core.ui.TitleStyle.glimmerGaussian(f.crestProgress(), idx, visible),
                                    f.dimScale(), f.pop())
                            : com.example.globe.core.ui.TitleStyle.dimToFloor(base, f.dimScale());
                    shaded = com.example.globe.core.ui.TitleStyle.brighten(shaded, f.bloom());
                    return alphaMask | (shaded & 0xFFFFFF);
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
