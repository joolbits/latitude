package com.example.globe.client;

import com.example.globe.core.config.LatitudeConfigData.AccessibilityMode;
import com.example.globe.core.ui.AccessibilityPalette;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A reusable "open-at-a-glance" picker to replace multi-state {@link net.minecraft.client.gui.components.CycleButton}s
 * (audit C1/H6 — blind cycling: reaching one of 13 color schemes could cost a dozen clicks, and overshooting
 * by one costs a whole lap back). Collapsed it looks like the screen's other rows (themed panel palette); a
 * click opens a labeled list BELOW it with every option visible at once, each with an optional color swatch,
 * the current pick highlighted, one click to choose. Click-outside or ESC closes with no change. Arrow keys +
 * Enter navigate while open (accessibility).
 *
 * <p>Because this screen renders heavily by hand (swatches, scrollbar, glyphs drawn after the widget batch),
 * the OPEN list can't just be a taller widget — it must paint on top of everything and grab input first. So
 * the collapsed button is a normal tracked widget (scrolls/animates with the sidebar), but the open list is
 * driven by the {@link Host} screen: it renders the list after its own widget pass and routes clicks/keys/
 * scroll to the single open dropdown before anything else sees them. Exactly one dropdown is open at a time.
 */
public final class SwatchDropdown extends AbstractWidget {

    // Palette mirrors LatitudeHudStudioScreen's so the control reads as part of the same themed system.
    private static final int GOLD = 0xFFD4A74A;
    private static final int WARM_WHITE = 0xFFEDE0D0;
    private static final int MUTED = 0xFF8C8078;
    private static final int PANEL_BORDER = 0xFF5C4A3A;
    private static final int PANEL_BG = 0xFF3A302A;
    private static final int PANEL_BG_DARK = 0xFF2A2420;

    // Accessibility (Peetsa 2026-07-11): the collapsed row and the open list obey the SAME shared palette as the
    // rest of the Studio/HUD (core.ui.AccessibilityPalette) -- ONE rulebook, read live each frame. HIGH_CONTRAST
    // lifts dim entry/label text to a legible floor, floors backgrounds near-solid, brightens borders, and adds
    // a stronger selection cue; STANDARD/COLORBLIND are the identity (this control has no red/green signal).
    private static AccessibilityMode a11yMode() {
        AccessibilityMode m = LatitudeConfig.accessibilityMode;
        return m == null ? AccessibilityMode.STANDARD : m;
    }

    private static boolean highContrast() {
        return a11yMode() == AccessibilityMode.HIGH_CONTRAST;
    }

    private static int a11yText(int argb) {
        return AccessibilityPalette.adjustPanelText(a11yMode(), argb);
    }

    private static int a11yMuted(int argb) {
        return AccessibilityPalette.adjustMuted(a11yMode(), argb);
    }

    private static int a11yBg(int argb) {
        int a = AccessibilityPalette.backgroundAlpha(a11yMode(), (argb >>> 24) & 0xFF);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static final int LIST_ROW_H = 13; // 9px font + breathing room
    private static final int TOP_MARGIN = 24;  // keep the list clear of the screen's top hint lane
    private static final int BOTTOM_MARGIN = 6;

    /** One selectable option. {@code swatchRgb} is the primary chip color; when {@code hasSplit}, {@code splitRgb}
     *  fills the chip's left half (e.g. a compass theme's face) and {@code swatchRgb} its right (the ring). */
    public static final class Entry {
        final String label;
        final boolean hasSwatch;
        final int swatchRgb;
        final boolean hasSplit;
        final int splitRgb;

        private Entry(String label, boolean hasSwatch, int swatchRgb, boolean hasSplit, int splitRgb) {
            this.label = label;
            this.hasSwatch = hasSwatch;
            this.swatchRgb = swatchRgb;
            this.hasSplit = hasSplit;
            this.splitRgb = splitRgb;
        }

        /** Text-only option (no color chip) — e.g. a compass look or a title-case mode. */
        public static Entry text(String label) {
            return new Entry(label, false, 0, false, 0);
        }

        /** Option with a single solid color chip — e.g. a text/background/title color. */
        public static Entry swatch(String label, int rgb) {
            return new Entry(label, true, 0xFF000000 | (rgb & 0xFFFFFF), false, 0);
        }

        /** Option with a split face/ring chip — e.g. a compass color scheme previewing both colors. */
        public static Entry split(String label, int faceRgb, int ringRgb) {
            return new Entry(label, true, 0xFF000000 | (ringRgb & 0xFFFFFF), true, 0xFF000000 | (faceRgb & 0xFFFFFF));
        }
    }

    /** Implemented by the owning screen: it holds the single open dropdown, renders its list, and routes input. */
    public interface Host {
        void dropdownOpened(SwatchDropdown d);

        void dropdownClosed(SwatchDropdown d);

        int hostScreenWidth();

        int hostScreenHeight();
    }

    private final Font font;
    private final List<Entry> entries;
    private final IntConsumer onSelect;
    private final Host host;
    private final String rowLabel;

    private int selectedIndex;
    private int highlightIndex;
    private boolean open;
    private int panelScroll; // in rows

    public SwatchDropdown(int x, int y, int width, int height, Font font, String rowLabel,
                          List<Entry> entries, int selectedIndex, IntConsumer onSelect, Host host) {
        super(x, y, width, height, Component.literal(rowLabel));
        this.font = font;
        this.entries = entries;
        this.rowLabel = rowLabel;
        this.selectedIndex = clampIndex(selectedIndex);
        this.highlightIndex = this.selectedIndex;
        this.onSelect = onSelect;
        this.host = host;
        refreshMessage();
    }

    private int clampIndex(int i) {
        if (entries.isEmpty()) return 0;
        return Math.max(0, Math.min(entries.size() - 1, i));
    }

    private void refreshMessage() {
        String value = entries.isEmpty() ? "" : entries.get(selectedIndex).label;
        this.setMessage(Component.literal(rowLabel + ": " + value));
    }

    public boolean isOpen() {
        return open;
    }

    private void openList() {
        if (open) return;
        open = true;
        highlightIndex = selectedIndex;
        ensureHighlightVisible();
        host.dropdownOpened(this);
    }

    /** Close with NO change. Idempotent (safe to call from the host while it re-points its open reference). */
    public void close() {
        if (!open) return;
        open = false;
        host.dropdownClosed(this);
    }

    private void choose(int index) {
        if (index < 0 || index >= entries.size()) return;
        selectedIndex = index;
        refreshMessage();
        this.playDownSound(Minecraft.getInstance().getSoundManager());
        close();
        onSelect.accept(index);
    }

    // ---- collapsed button ----

    @Override
    public void onClick(MouseButtonEvent click, boolean doubled) {
        if (open) {
            close();
        } else {
            openList();
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hot = open || isHoveredOrFocused();

        ctx.fill(x, y, x + w, y + h, a11yBg(hot ? PANEL_BG : PANEL_BG_DARK));
        int border = a11yMuted(hot ? GOLD : PANEL_BORDER);
        ctx.fill(x, y, x + w, y + 1, border);
        ctx.fill(x, y + h - 1, x + w, y + h, border);
        ctx.fill(x, y, x + 1, y + h, border);
        ctx.fill(x + w - 1, y, x + w, y + h, border);

        Entry sel = entries.isEmpty() ? null : entries.get(selectedIndex);
        int caretCx = x + w - 8;
        int caretCy = y + h / 2;
        drawCaret(ctx, caretCx, caretCy, open, hot ? a11yMuted(GOLD) : a11yMuted(MUTED));

        int rightPad = 16;
        if (sel != null && sel.hasSwatch) {
            int chipR = x + w - 15;
            int chipL = chipR - 10;
            drawSwatch(ctx, chipL, y + (h - 10) / 2, 10, 10, sel);
            rightPad = 30;
        }

        String text = getMessage().getString();
        int textRight = x + w - rightPad;
        ctx.enableScissor(x + 2, y, textRight, y + h);
        ctx.text(font, text, x + 6, y + (h - font.lineHeight) / 2, hot ? a11yText(WARM_WHITE) : a11yMuted(MUTED));
        ctx.disableScissor();
    }

    private void drawSwatch(GuiGraphicsExtractor ctx, int x, int y, int w, int h, Entry e) {
        if (e.hasSplit) {
            ctx.fill(x, y, x + w / 2, y + h, e.splitRgb);
            ctx.fill(x + w / 2, y, x + w, y + h, e.swatchRgb);
        } else {
            ctx.fill(x, y, x + w, y + h, e.swatchRgb);
        }
        int chipBorder = a11yMuted(PANEL_BORDER);
        ctx.fill(x, y, x + w, y + 1, chipBorder);
        ctx.fill(x, y + h - 1, x + w, y + h, chipBorder);
        ctx.fill(x, y, x + 1, y + h, chipBorder);
        ctx.fill(x + w - 1, y, x + w, y + h, chipBorder);
    }

    private static void drawCaret(GuiGraphicsExtractor ctx, int cx, int cy, boolean up, int color) {
        if (up) {
            ctx.fill(cx, cy - 1, cx + 1, cy, color);
            ctx.fill(cx - 1, cy, cx + 2, cy + 1, color);
            ctx.fill(cx - 2, cy + 1, cx + 3, cy + 2, color);
        } else {
            ctx.fill(cx - 2, cy - 1, cx + 3, cy, color);
            ctx.fill(cx - 1, cy, cx + 2, cy + 1, color);
            ctx.fill(cx, cy + 1, cx + 1, cy + 2, color);
        }
    }

    // ---- open list (driven by the host) ----

    private static final class Geom {
        final int x;
        final int top;
        final int w;
        final int visibleRows;
        final int maxScroll;

        Geom(int x, int top, int w, int visibleRows, int maxScroll) {
            this.x = x;
            this.top = top;
            this.w = w;
            this.visibleRows = visibleRows;
            this.maxScroll = maxScroll;
        }
    }

    private Geom computeGeom() {
        int x = getX();
        int w = getWidth();
        int screenH = host.hostScreenHeight();
        int belowTop = getY() + getHeight() + 1;
        int availBelow = Math.max(LIST_ROW_H, screenH - BOTTOM_MARGIN - belowTop);
        int availAbove = Math.max(LIST_ROW_H, (getY() - 1) - TOP_MARGIN);

        int n = entries.size();
        int wantH = n * LIST_ROW_H;
        boolean below = wantH <= availBelow || availBelow >= availAbove;
        int avail = below ? availBelow : availAbove;

        int visibleRows = Math.max(1, Math.min(n, avail / LIST_ROW_H));
        int panelH = visibleRows * LIST_ROW_H + 2;
        int top = below ? belowTop : (getY() - 1 - panelH);
        int maxScroll = Math.max(0, n - visibleRows);
        return new Geom(x, top, w, visibleRows, maxScroll);
    }

    private void ensureHighlightVisible() {
        Geom g = computeGeom();
        if (highlightIndex < panelScroll) {
            panelScroll = highlightIndex;
        } else if (highlightIndex > panelScroll + g.visibleRows - 1) {
            panelScroll = highlightIndex - g.visibleRows + 1;
        }
        panelScroll = Math.max(0, Math.min(g.maxScroll, panelScroll));
    }

    /** Painted by the host AFTER its widget pass so it sits on top of everything. */
    public void renderOpenList(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!open) return;
        Geom g = computeGeom();
        panelScroll = Math.max(0, Math.min(g.maxScroll, panelScroll));
        int panelH = g.visibleRows * LIST_ROW_H + 2;
        int left = g.x;
        int right = g.x + g.w;
        int top = g.top;
        int bottom = top + panelH;

        // Fully opaque panel (never see-through over the world) with a brightened frame under High Contrast.
        boolean hc = highContrast();
        ctx.fill(left, top, right, bottom, a11yMuted(PANEL_BORDER));
        ctx.fill(left + 1, top + 1, right - 1, bottom - 1, a11yBg(PANEL_BG));

        ctx.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
        for (int row = 0; row < g.visibleRows; row++) {
            int i = panelScroll + row;
            if (i >= entries.size()) break;
            Entry e = entries.get(i);
            int ry = top + 1 + row * LIST_ROW_H;
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= ry && mouseY < ry + LIST_ROW_H;
            boolean keyed = i == highlightIndex;
            if (hovered) {
                ctx.fill(left + 1, ry, right - 1, ry + LIST_ROW_H, 0x55D4A74A);
            } else if (keyed) {
                ctx.fill(left + 1, ry, right - 1, ry + LIST_ROW_H, 0x33D4A74A);
            }
            // High Contrast: frame the active row with a bright gold outline (shape cue that doesn't fight the
            // light entry text), so the current keyboard/hover target is unmistakable without an opaque fill.
            if (hc && (hovered || keyed)) {
                int oc = a11yMuted(GOLD);
                ctx.fill(left + 1, ry, right - 1, ry + 1, oc);
                ctx.fill(left + 1, ry + LIST_ROW_H - 1, right - 1, ry + LIST_ROW_H, oc);
                ctx.fill(left + 1, ry, left + 2, ry + LIST_ROW_H, oc);
                ctx.fill(right - 2, ry, right - 1, ry + LIST_ROW_H, oc);
            }
            if (i == selectedIndex) {
                // The selected-row bar: a wider, brighter gold stripe under High Contrast.
                int barW = hc ? 4 : 2;
                ctx.fill(left + 1, ry, left + 1 + barW, ry + LIST_ROW_H, a11yMuted(GOLD));
            }
            int textX = left + 6;
            if (e.hasSwatch) {
                drawSwatch(ctx, left + 5, ry + (LIST_ROW_H - 9) / 2, 9, 9, e);
                textX = left + 17;
            }
            int color = i == selectedIndex
                    ? a11yText(GOLD)
                    : (hovered || keyed ? a11yText(WARM_WHITE) : a11yMuted(MUTED));
            ctx.text(font, e.label, textX, ry + (LIST_ROW_H - font.lineHeight) / 2 + 1, color);
        }
        ctx.disableScissor();

        // Thin scrollbar when the list overflows.
        if (g.maxScroll > 0) {
            int trackX = right - 3;
            int trackH = panelH - 2;
            int thumbH = Math.max(6, trackH * g.visibleRows / entries.size());
            int thumbY = top + 1 + (trackH - thumbH) * panelScroll / g.maxScroll;
            ctx.fill(trackX, top + 1, trackX + 2, bottom - 1, a11yBg(0x55FFFFFF));
            ctx.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, a11yMuted(GOLD));
        }
    }

    /** Returns the entry index under the point, or -1 for "not on a row" (the host then closes with no change). */
    public int pickAt(double mx, double my) {
        if (!open) return -1;
        Geom g = computeGeom();
        int panelH = g.visibleRows * LIST_ROW_H + 2;
        if (mx < g.x || mx >= g.x + g.w || my < g.top || my >= g.top + panelH) {
            return -1;
        }
        int row = (int) ((my - (g.top + 1)) / LIST_ROW_H);
        int i = panelScroll + row;
        if (row < 0 || row >= g.visibleRows || i < 0 || i >= entries.size()) {
            return -1;
        }
        return i;
    }

    /** Host routes a click here while open. Returns true if it consumed the click (always, since it's modal). */
    public boolean handleClick(double mx, double my) {
        int i = pickAt(mx, my);
        if (i >= 0) {
            choose(i);
        } else {
            close();
        }
        return true;
    }

    /** Host routes key presses here while open. Returns true if handled. */
    public boolean handleKey(KeyEvent input) {
        if (!open) return false;
        if (input.isEscape()) {
            close();
            return true;
        }
        if (input.isSelection() || input.isConfirmation()) {
            choose(highlightIndex);
            return true;
        }
        if (input.isUp()) {
            highlightIndex = Math.max(0, highlightIndex - 1);
            ensureHighlightVisible();
            return true;
        }
        if (input.isDown()) {
            highlightIndex = Math.min(entries.size() - 1, highlightIndex + 1);
            ensureHighlightVisible();
            return true;
        }
        return false;
    }

    /** Host routes scroll here while open. */
    public boolean handleScroll(double amount) {
        if (!open) return false;
        Geom g = computeGeom();
        panelScroll -= (int) Math.signum(amount);
        panelScroll = Math.max(0, Math.min(g.maxScroll, panelScroll));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        this.defaultButtonNarrationText(builder);
    }
}
