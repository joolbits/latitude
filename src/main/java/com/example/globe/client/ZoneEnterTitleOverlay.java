package com.example.globe.client;

import com.example.globe.core.config.LatitudeConfigData;
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
        int cx = (screenW / 2) + LatitudeConfig.zoneEnterTitleOffsetX;
        int cy = (screenH / 2) + LatitudeConfig.zoneEnterTitleOffsetY;
        drawTitleLineAt(ctx, cx, cy, title.getString(), scale, a);
    }

    /**
     * Draws one title line, centered at ({@code cx},{@code cy}) in screen space, using the SAME case /
     * color / letter-spacing / shadow styling as the zone-enter title. Shared by the zone-enter title,
     * the HUD Studio preview, and the B-3c hemisphere-title channel so all title text is one visual
     * system (a hemisphere title reads exactly like a zone title, just in its own channel/position).
     */
    public static void drawTitleLineAt(GuiGraphicsExtractor ctx, int cx, int cy, String rawText, double scale, int alphaByte) {
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
            drawStyledTitle(ctx, tr, styled, alphaByte);
        } finally {
            m.popMatrix();
        }
    }

    public static void renderStaticAt(GuiGraphicsExtractor ctx, int screenW, int screenH, String text, double scale, int offsetX, int offsetY) {
        int cx = (screenW / 2) + offsetX;
        int cy = (screenH / 2) + offsetY;
        drawTitleLineAt(ctx, cx, cy, text, scale, 0xFF);
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

    private static void drawStyledTitle(GuiGraphicsExtractor ctx, Font font, String text, int alphaByte) {
        int spacing = LatitudeConfig.zoneEnterTitleLetterSpacing;
        int alphaMask = (alphaByte & 0xFF) << 24;
        if (LatitudeConfig.zoneEnterTitleColorPreset == LatitudeConfigData.TitleColorPreset.RAINBOW) {
            int visibleCount = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) != ' ') visibleCount++;
            }
            final int visible = visibleCount;
            drawSpacedText(ctx, font, text, 0, 0, true, spacing,
                    idx -> alphaMask | RainbowText.flowingColor(idx, visible,
                            com.example.globe.core.ui.FlowingGradient.DEFAULT_CYCLE_SECONDS));
            return;
        }
        int argb = alphaMask | (titleColorRgb(LatitudeConfig.zoneEnterTitleColorPreset) & 0xFFFFFF);
        drawSpacedText(ctx, font, text, 0, 0, true, spacing, idx -> argb);
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
            case WHITE, RAINBOW -> 0xFFFFFF; // RAINBOW never reaches here -- handled above.
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
