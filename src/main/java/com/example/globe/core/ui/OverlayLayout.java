package com.example.globe.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Pure fit/clamp/wrap math for the in-world signature overlays (zone-enter title, hemisphere title, pole /
 * storm warning line) — zero Minecraft imports, unit-tested in the pure-JVM suite. This is the overlay-side
 * counterpart to {@link HudLayoutMath}: the compass and its detached labels are fraction-anchored and
 * re-clamped every frame, but the title/warning overlays historically stored an absolute pixel offset (the
 * title) or drew one un-fitted centered string (the warning), so a GUI-scale change could push the title
 * fully off-screen or run a long warning off both edges (GUI-scale parity audit 2026-07-10, findings
 * H1 / M1 / M2). These three helpers give the render paths a resolution-independent way to keep every
 * overlay on-screen and legible at every effective GUI resolution, down to the vanilla 320×240 floor.
 */
public final class OverlayLayout {

    private OverlayLayout() {
    }

    /**
     * Reduce {@code desiredScale} just enough that a piece of text of unscaled width {@code contentW} fits
     * within {@code maxWidth}, never below {@code minScale}. If it already fits (or there's nothing to fit),
     * {@code desiredScale} is returned unchanged. Used to shrink an over-wide title so a long biome name at a
     * large scale on a small canvas doesn't spill off both edges (M1). The {@code minScale} floor keeps the
     * text readable; when even the floor overflows, the caller's {@link #clampCenter} keeps it centered.
     */
    public static double fitScale(double desiredScale, int contentW, int maxWidth, double minScale) {
        if (contentW <= 0 || maxWidth <= 0) {
            return desiredScale;
        }
        double scaledW = contentW * desiredScale;
        if (scaledW <= maxWidth) {
            return desiredScale;
        }
        double fit = (double) maxWidth / (double) contentW;
        return Math.max(minScale, fit);
    }

    /**
     * Clamp a box's CENTER coordinate so a box of half-extent {@code half} stays fully within {@code [0,
     * extent]}. When the box is wider than the axis ({@code 2*half >= extent}) it cannot fit at all, so it's
     * centered instead ({@code extent/2}) — both edges overflow equally and the important middle stays
     * visible. This is the render-frame safety net for the zone title's absolute-pixel offset (H1): the
     * offset is set by HUD Studio at edit resolution and never re-derived, so a later GUI-scale change can
     * push the computed center off-screen; re-clamping every frame keeps the title on-screen and draggable
     * back.
     */
    public static int clampCenter(int center, int half, int extent) {
        if (2 * half >= extent) {
            return extent / 2;
        }
        return Math.max(half, Math.min(extent - half, center));
    }

    /**
     * Greedy word-wrap {@code text} into lines each no wider than {@code maxWidth}, as measured by the
     * injected {@code width} function (injected so this stays pure and testable — the render caller passes a
     * font/style-aware measurer). Words are never split mid-word; a single word longer than {@code maxWidth}
     * gets its own (over-wide) line rather than being broken. Always returns at least one line. Used to wrap
     * a long pole/storm warning to fit a narrow screen instead of running off the right edge (M2); for the
     * common short warning it returns a single line, so the single-line render path is unchanged.
     */
    public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add(text == null ? "" : text);
            return lines;
        }
        if (maxWidth <= 0 || width.applyAsInt(text) <= maxWidth) {
            lines.add(text);
            return lines;
        }
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() == 0) {
                cur.append(word);
                continue;
            }
            String candidate = cur + " " + word;
            if (width.applyAsInt(candidate) <= maxWidth) {
                cur.append(' ').append(word);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        if (lines.isEmpty()) {
            lines.add(text);
        }
        return lines;
    }
}
