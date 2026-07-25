package com.example.globe.core.ui;

/**
 * Pin &amp; Grow HUD layout math — pure functions, zero Minecraft imports, unit-tested in the pure-JVM suite
 * (design: docs/design/hud-layout-overhaul-design-20260707.md, Pillar 1/1b).
 *
 * <p><b>The model.</b> Every HUD element stores a PIN (a 9-grid screen anchor + an offset expressed as a
 * FRACTION of the gui-scaled screen — so placements survive GUI-scale and window-size changes) and a GROW
 * (how the content box extends from the pin, per axis). The pin is a POINT; content width/height never
 * participate in where the pin is — only in how far the box extends from it. This is the structural fix
 * for the audit's root cause #1 (the compass sliding when a biome name got longer: the old model anchored
 * a content-measured box, so width fed back into position).
 *
 * <p><b>The hotbar dock</b> (Pillar 1b) is a computed pin at the hotbar's right edge — past the offhand
 * slot (which vanilla renders on the RIGHT for left-handed players) and the attack indicator when it's in
 * hotbar mode — with growth structurally AWAY from the hotbar and a deterministic fit ladder (beside →
 * stacked → shrunk → lifted above) keyed ONLY to screen geometry, never to text width. The historical
 * clipping bug (analog attach centered the dial ON the hotbar; disabled in 4778a5ed) becomes unreachable.
 */
public final class HudLayoutMath {

    private HudLayoutMath() {
    }

    /** Horizontal grow: how the box extends from the pin. LEFT = pin is the box's LEFT edge (grows right). */
    public enum GrowH { LEFT, CENTER, RIGHT }

    /** Vertical grow: TOP = pin is the box's TOP edge (grows down). */
    public enum GrowV { TOP, MIDDLE, BOTTOM }

    /** Screen-edge inset for the L/R and T/B pin grid bases (matches the legacy anchor margin). */
    public static final int EDGE_INSET = 4;

    // Vanilla HUD geometry (gui-scaled px). Hotbar is 182 wide (91 half), 22 tall, bottom-centered.
    public static final int HOTBAR_HALF_W = 91;
    public static final int HOTBAR_H = 22;
    public static final int OFFHAND_W = 29;   // offhand slot + its gap, rendered beside the hotbar
    public static final int ATTACK_INDICATOR_W = 22; // hotbar-mode attack indicator, right of the hotbar
    public static final int DOCK_GAP = 6;
    public static final int DOCK_MARGIN = 4;
    public static final int LIFT_CLEARANCE = 4; // gap between a lifted dock box and the hotbar top

    /** Pin base X for grid column 0/1/2 (L/C/R). A POINT — box width deliberately absent. */
    public static int pinBaseX(int gridCol, int screenW) {
        return switch (gridCol) {
            case 0 -> EDGE_INSET;
            case 2 -> screenW - EDGE_INSET;
            default -> screenW / 2;
        };
    }

    /** Pin base Y for grid row 0/1/2 (T/M/B). */
    public static int pinBaseY(int gridRow, int screenH) {
        return switch (gridRow) {
            case 0 -> EDGE_INSET;
            case 2 -> screenH - EDGE_INSET;
            default -> screenH / 2;
        };
    }

    /** Resolved pin X = grid base + fractional offset (fraction of screen width). */
    public static int pinX(int gridCol, double offXFrac, int screenW) {
        return pinBaseX(gridCol, screenW) + (int) Math.round(offXFrac * screenW);
    }

    /** Resolved pin Y = grid base + fractional offset (fraction of screen height). */
    public static int pinY(int gridRow, double offYFrac, int screenH) {
        return pinBaseY(gridRow, screenH) + (int) Math.round(offYFrac * screenH);
    }

    /** Convert a resolved pin X back to the fractional offset for its grid column (drag persistence). */
    public static double offXFracFor(int pinX, int gridCol, int screenW) {
        return screenW <= 0 ? 0.0 : (pinX - pinBaseX(gridCol, screenW)) / (double) screenW;
    }

    /** Convert a resolved pin Y back to the fractional offset for its grid row (drag persistence). */
    public static double offYFracFor(int pinY, int gridRow, int screenH) {
        return screenH <= 0 ? 0.0 : (pinY - pinBaseY(gridRow, screenH)) / (double) screenH;
    }

    /** Box left edge for a pin and grow. THE invariant: pinX is independent of boxW by construction. */
    public static int placeX(int pinX, int boxW, GrowH grow) {
        return switch (grow) {
            case LEFT -> pinX;
            case CENTER -> pinX - boxW / 2;
            case RIGHT -> pinX - boxW;
        };
    }

    /** Box top edge for a pin and grow. */
    public static int placeY(int pinY, int boxH, GrowV grow) {
        return switch (grow) {
            case TOP -> pinY;
            case MIDDLE -> pinY - boxH / 2;
            case BOTTOM -> pinY - boxH;
        };
    }

    /** The alignment point of an already-placed box (inverse of placeX) — used by legacy migration. */
    public static int alignPointX(int boxLeft, int boxW, GrowH grow) {
        return switch (grow) {
            case LEFT -> boxLeft;
            case CENTER -> boxLeft + boxW / 2;
            case RIGHT -> boxLeft + boxW;
        };
    }

    public static int alignPointY(int boxTop, int boxH, GrowV grow) {
        return switch (grow) {
            case TOP -> boxTop;
            case MIDDLE -> boxTop + boxH / 2;
            case BOTTOM -> boxTop + boxH;
        };
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Clamp a placed box into the screen (render-time safety; pins themselves are clamped at save). */
    public static int clampBoxX(int x, int boxW, int screenW) {
        return clamp(x, 0, Math.max(0, screenW - boxW));
    }

    public static int clampBoxY(int y, int boxH, int screenH) {
        return clamp(y, 0, Math.max(0, screenH - boxH));
    }

    // ------------------------------------------------------------------------------------------------
    // Hotbar dock
    // ------------------------------------------------------------------------------------------------

    /** Dock fit-ladder stages, in order. */
    public enum DockStage { BESIDE, STACKED, SHRUNK, LIFTED }

    /** Candidate box sizes for the ladder (computed by the caller from real content + font metrics). */
    public record DockBoxes(int besideW, int besideH, int stackedW, int stackedH, int shrunkW, int shrunkH) {
    }

    public record DockResult(DockStage stage, int x, int y, int boxW, int boxH) {
    }

    /** X of the first free pixel right of the hotbar + its right-side companions. */
    public static int dockOriginX(int screenW, boolean offhandOnRight, boolean attackIndicatorOnHotbar) {
        int x = screenW / 2 + HOTBAR_HALF_W;
        if (offhandOnRight) x += OFFHAND_W;
        if (attackIndicatorOnHotbar) x += ATTACK_INDICATOR_W;
        return x + DOCK_GAP;
    }

    /**
     * The deterministic fit ladder. Every rung is a pure function of screen geometry and the candidate
     * box sizes — biome-name width changes the box CONTENT, not the dock position, because callers pass
     * reserved/max text widths for the candidates. The result NEVER overlaps the hotbar band
     * (bottom HOTBAR_H rows within the hotbar+companion span): BESIDE/STACKED/SHRUNK sit strictly right
     * of the companions; LIFTED sits strictly above them.
     */
    public static DockResult dock(int screenW, int screenH, DockBoxes boxes,
                                  boolean offhandOnRight, boolean attackIndicatorOnHotbar) {
        int originX = dockOriginX(screenW, offhandOnRight, attackIndicatorOnHotbar);
        int avail = screenW - originX - DOCK_MARGIN;
        int hotbarTop = screenH - HOTBAR_H;

        if (boxes.besideW() <= avail) {
            int y = hotbarTop + (HOTBAR_H - boxes.besideH()) / 2; // centered on the hotbar row...
            y = clampBoxY(Math.min(y, screenH - 2 - boxes.besideH()), boxes.besideH(), screenH); // ...but never below screen
            return new DockResult(DockStage.BESIDE, originX, y, boxes.besideW(), boxes.besideH());
        }
        if (boxes.stackedW() <= avail) {
            int y = clampBoxY(screenH - 2 - boxes.stackedH(), boxes.stackedH(), screenH);
            return new DockResult(DockStage.STACKED, originX, y, boxes.stackedW(), boxes.stackedH());
        }
        if (boxes.shrunkW() <= avail) {
            int y = clampBoxY(screenH - 2 - boxes.shrunkH(), boxes.shrunkH(), screenH);
            return new DockResult(DockStage.SHRUNK, originX, y, boxes.shrunkW(), boxes.shrunkH());
        }
        // LIFTED: above the hotbar's top edge, right-aligned to the screen edge. Never overlaps the
        // hotbar band because bottom <= hotbarTop - LIFT_CLEARANCE.
        int x = clampBoxX(screenW - DOCK_MARGIN - boxes.shrunkW(), boxes.shrunkW(), screenW);
        int y = clampBoxY(hotbarTop - LIFT_CLEARANCE - boxes.shrunkH(), boxes.shrunkH(), screenH);
        return new DockResult(DockStage.LIFTED, x, y, boxes.shrunkW(), boxes.shrunkH());
    }

    /** Rect intersection helper for the dock's non-overlap invariant (and its unit tests). */
    public static boolean intersects(int ax0, int ay0, int ax1, int ay1, int bx0, int by0, int bx1, int by1) {
        return ax0 < bx1 && bx0 < ax1 && ay0 < by1 && by0 < ay1;
    }
}
