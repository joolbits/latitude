package com.example.globe.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class LatitudeSettingsScreen extends Screen {
    // ── Theme constants shared with bespoke world-creation UI ──
    private static final int BG_COLOR = 0xFF2C2420;
    private static final int GOLD = 0xFFD4A74A;
    private static final int WARM_WHITE = 0xFFEDE0D0;
    private static final int MUTED = 0xFF8C8078;
    private static final int PANEL_BORDER = 0xFF5C4A3A;
    private static final int PANEL_BG = 0xFF3A302A;
    private static final int GRID_COLOR = 0x14504840;
    private static final int GRID_STEP = 16;

    private final Screen parent;

    private int scrollY = 0;
    private int contentHeight = 0;
    private int panelX;
    private int panelWidth;
    private int panelTop;
    private int panelBottom;
    private int columnXCache;
    private int columnWCache;
    private final List<AbstractWidget> layoutWidgets = new ArrayList<>();
    private AbstractWidget wHudStudio; // rainbow-lettered in render() (blank label on the button itself)
    private final List<Integer> layoutBaseYs = new ArrayList<>();
    private Button doneButton;
    private Button resetButton;
    private int footerY;
    private boolean compassExpanded = false;
    private int compassPreviewBaseY = -1;

    public LatitudeSettingsScreen(Screen parent) {
        super(Component.literal("Latitude Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var cfg = CompassHudConfig.get();
        var latCfg = LatitudeConfig.get();

        this.layoutWidgets.clear();
        this.layoutBaseYs.clear();
        this.doneButton = null;
        this.resetButton = null;

        int gutter = Math.max(28, this.width / 16);
        this.panelWidth = Math.min(420, this.width - gutter * 2);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelTop = Math.max(74, this.height / 6 + 38);
        this.panelBottom = this.height - 26;
        int columnX = this.panelX + 30;
        int w = this.panelWidth - 60;
        int h = 20;
        this.columnXCache = columnX;
        this.columnWCache = w;

        int y = this.panelTop + 24;

        int baseY;

        // HUD Studio first, per live feedback: it's the editor entry (not a plain option toggle), so it gets
        // top billing instead of being buried at the bottom of the scroll list. Message is blank -- vanilla
        // buttons force their label white (Button.extractContents/extractWidgetRenderState are sealed in 26.2,
        // so we can't recolor them directly), so RainbowText draws the "HUD Studio" lettering ourselves on top
        // of the button in render(), one color per letter, after the button's own (blank) extraction.
        baseY = y;
        this.wHudStudio = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
                    Minecraft.getInstance().setScreenAndShow(new LatitudeHudStudioScreen(this));
                })
                .bounds(columnX, y, w, 20)
                .build());
        layoutWidgets.add(wHudStudio);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wZoneTitle = this.addRenderableWidget(CycleButton.builder(v -> Component.literal(v ? "ON" : "OFF"), LatitudeConfig.zoneEnterTitleEnabled)
                .withValues(true, false)
                .create(columnX, y, w, h, Component.literal("Zone Enter Title"), (btn, value) -> LatitudeConfig.zoneEnterTitleEnabled = value));
        layoutWidgets.add(wZoneTitle);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wWarnText = this.addRenderableWidget(CycleButton.builder(v -> Component.literal(v ? "ON" : "OFF"), LatitudeConfig.showWarningMessages)
                .withValues(true, false)
                .create(columnX, y, w, h, Component.literal("Warning messages"), (btn, value) -> {
                    LatitudeConfig.showWarningMessages = value;
                    LatitudeConfig.saveCurrent();
                }));
        wWarnText.setTooltip(Tooltip.create(Component.literal("Show/hide on-screen warning text (fog/particles/effects still apply).")));
        layoutWidgets.add(wWarnText);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wTitleSec = this.addRenderableWidget(new StepSlider(columnX, y, w, h, Component.literal("Title Duration (seconds)"), 2.0, 10.0, 0.5, LatitudeConfig.zoneEnterTitleSeconds, v -> LatitudeConfig.zoneEnterTitleSeconds = v));
        layoutWidgets.add(wTitleSec);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wCompassExpander = this.addRenderableWidget(Button.builder(Component.literal(compassSummary(cfg)), b -> {
                    compassExpanded = !compassExpanded;
                    this.clearWidgets();
                    this.init();
                })
                .bounds(columnX, y, w, h)
                .build());
        layoutWidgets.add(wCompassExpander);
        layoutBaseYs.add(baseY);
        y += 24;

        if (compassExpanded) {
            baseY = y;
            var wCompassStyle = this.addRenderableWidget(CycleButton.<CompassHudConfig.CompassStyle>builder(v -> Component.literal(v == CompassHudConfig.CompassStyle.ANALOG ? "Analog" : "Digital"), () -> cfg.style)
                    .withValues(CompassHudConfig.CompassStyle.values())
                    .create(columnX, y, w, h, Component.literal("Style"), (btn, value) -> {
                        cfg.style = value;
                        CompassHudConfig.saveCurrent();
                        this.clearWidgets();
                        this.init();
                    }));
            layoutWidgets.add(wCompassStyle);
            layoutBaseYs.add(baseY);
            y += 24;

            baseY = y;
            var wCompassCompact = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.compactHud)
                    .withValues(true, false)
                    .create(columnX, y, w, h, Component.literal("Compact"), (btn, value) -> cfg.compactHud = value));
            layoutWidgets.add(wCompassCompact);
            layoutBaseYs.add(baseY);
            y += 24;

            compassPreviewBaseY = y;
            y += compassPreviewHeight(cfg);
        } else {
            compassPreviewBaseY = -1;
        }

        baseY = y;
        var wDisplayZone = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.displayZoneInHud)
                .withValues(true, false)
                .create(columnX, y, w, h, Component.literal("Zone Label"), (btn, value) -> cfg.displayZoneInHud = value));
        layoutWidgets.add(wDisplayZone);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wShowMode = this.addRenderableWidget(CycleButton.<CompassHudConfig.ShowMode>builder(this::showModeLabel, () -> cfg.showMode)
                .withValues(CompassHudConfig.ShowMode.values())
                .create(columnX, y, w, h, Component.literal("Show Mode"), (btn, value) -> cfg.showMode = value));
        layoutWidgets.add(wShowMode);
        layoutBaseYs.add(baseY);
        y += 24;

        this.contentHeight = y - this.panelTop;

        int footerY = this.panelBottom - 26;
        int buttonWidth = 96;
        int buttonSpacing = 14;
        int footerTotal = buttonWidth * 2 + buttonSpacing;
        int footerX = this.panelX + (this.panelWidth - footerTotal) / 2;

        this.footerY = this.panelBottom - 28;

        this.doneButton = this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
                    CompassHudConfig.saveCurrent();
                    LatitudeConfig.saveCurrent();
                    if (this.minecraft != null) {
                        this.minecraft.setScreenAndShow(this.parent);
                    }
                })
                .bounds(footerX, footerY, buttonWidth, 20)
                .build());

        this.resetButton = this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
                    applyDefaults(cfg);
                    applyDefaults(latCfg);
                    CompassHudConfig.saveCurrent();
                    LatitudeConfig.saveCurrent();
                    this.clearWidgets();
                    this.init();
                })
                .bounds(footerX + buttonWidth + buttonSpacing, footerY, buttonWidth, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, BG_COLOR);
        drawPanel(context);
        drawHeader(context);

        int maxScroll = Math.max(0, contentHeight - (this.footerY - this.panelTop));
        scrollY = Mth.clamp(scrollY, 0, maxScroll);

        for (int i = 0; i < layoutWidgets.size(); i++) {
            AbstractWidget w = layoutWidgets.get(i);
            int baseY = layoutBaseYs.get(i);
            int drawY = baseY - scrollY;
            w.setY(drawY);

            boolean visible = drawY >= panelTop && drawY + w.getHeight() <= this.footerY;
            w.visible = visible;
            w.active = visible;
        }

        if (doneButton != null) {
            doneButton.setX(this.panelX + (this.panelWidth - (doneButton.getWidth() + resetButton.getWidth() + 12)) / 2);
            doneButton.setY(this.panelBottom - 28);
            resetButton.setX(doneButton.getX() + doneButton.getWidth() + 12);
            resetButton.setY(doneButton.getY());
        }

        if (compassExpanded && compassPreviewBaseY >= 0) {
            int previewY = compassPreviewBaseY - scrollY;
            int previewH = compassPreviewHeight(CompassHudConfig.get());
            if (previewY >= panelTop && previewY + previewH <= this.footerY) {
                drawCompassPreview(context, columnXCache, previewY, columnWCache, previewH, CompassHudConfig.get());
            }
        }

        drawScrollbar(context, maxScroll);
        super.extractRenderState(context, mouseX, mouseY, delta);

        // HUD Studio's label is drawn ourselves (blank Component on the button itself) so it can be rainbow
        // lettered instead of forced white. Drawn after super so it sits on top of the button; only when the
        // button is actually in the scroll viewport.
        if (wHudStudio != null && wHudStudio.visible) {
            int centerX = wHudStudio.getX() + wHudStudio.getWidth() / 2;
            int centerY = wHudStudio.getY() + wHudStudio.getHeight() / 2;
            RainbowText.drawCentered(context, this.font, "HUD Studio", centerX, centerY, true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, contentHeight - (this.footerY - this.panelTop));
        scrollY -= (int) Math.signum(verticalAmount) * 18;
        scrollY = Mth.clamp(scrollY, 0, maxScroll);
        return true;
    }

    private Component showModeLabel(CompassHudConfig.ShowMode v) {
        if (v == null) return Component.literal("Always");
        return switch (v) {
            case COMPASS_PRESENT -> Component.literal("When compass is in inventory");
            case HOLDING_COMPASS -> Component.literal("When holding compass");
            case ALWAYS -> Component.literal("Always");
        };
    }

    private String compassSummary(CompassHudConfig cfg) {
        String style = cfg.style == CompassHudConfig.CompassStyle.ANALOG ? "Analog" : "Digital";
        String compact = cfg.compactHud ? "Compact: ON" : "Compact: OFF";
        return "Compass Style: " + style + " | " + compact;
    }

    private void drawScrollbar(GuiGraphicsExtractor context, int maxScroll) {
        if (maxScroll <= 0) return;
        int trackX = panelX + panelWidth - 5;
        int trackTop = panelTop + 4;
        int trackBottom = footerY - 4;
        int trackH = trackBottom - trackTop;
        if (trackH < 10) return;
        int viewportH = footerY - panelTop;
        int totalH = viewportH + maxScroll;
        int thumbH = Math.max(10, trackH * viewportH / totalH);
        int thumbY = trackTop + (trackH - thumbH) * scrollY / maxScroll;
        context.fill(trackX, trackTop, trackX + 3, trackBottom, PANEL_BORDER);
        context.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, GOLD);
    }

    private void drawCompassPreview(GuiGraphicsExtractor ctx, int x, int y, int w, int h, CompassHudConfig cfg) {
        int boxColor = 0x33111111;
        ctx.fill(x, y, x + w, y + h, boxColor);

        var mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return;
        }

        var bounds = CompassHud.computeBounds(mc, cfg);
        int renderW = bounds.w();
        int renderH = bounds.h();
        int renderX = x + Math.max(6, (w - renderW) / 2);
        int renderY = y + Math.max(6, (h - renderH) / 2);
        CompassHud.renderPreview(ctx, mc, cfg, renderX, renderY);
    }

    private int compassPreviewHeight(CompassHudConfig cfg) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return 36;
        }
        var bounds = CompassHud.computeBounds(mc, cfg);
        return Math.max(36, bounds.h() + 12);
    }


    // Copies every field from a fresh CompassHudConfig so this screen's "Reset" always matches CompassHudConfig's
    // own field-initializer defaults -- there was previously a second hand-duplicated copy of the defaults here
    // (independent of the one in LatitudeHudStudioScreen), which had silently drifted to the old DIGITAL/48.0/
    // 0.65 compass instead of the current ANALOG/32.0/0.50 default. See CompassHudConfig.fresh().
    private static void applyDefaults(CompassHudConfig cfg) {
        CompassHudConfig fresh = CompassHudConfig.fresh();
        cfg.enabled = fresh.enabled;
        cfg.showMode = fresh.showMode;
        cfg.style = fresh.style;
        cfg.directionMode = fresh.directionMode;
        cfg.hAnchor = fresh.hAnchor;
        cfg.vAnchor = fresh.vAnchor;
        cfg.offsetX = fresh.offsetX;
        cfg.offsetY = fresh.offsetY;
        cfg.scale = fresh.scale;
        cfg.analogSize = fresh.analogSize;
        cfg.analogInnerAlpha = fresh.analogInnerAlpha;
        cfg.analogTheme = fresh.analogTheme;
        cfg.padding = fresh.padding;
        cfg.showBackground = fresh.showBackground;
        cfg.backgroundRgb = fresh.backgroundRgb;
        cfg.backgroundAlpha = fresh.backgroundAlpha;
        cfg.textRgb = fresh.textRgb;
        cfg.textAlpha = fresh.textAlpha;
        cfg.shadow = fresh.shadow;
        cfg.showLatitude = fresh.showLatitude;
        cfg.analogShowLatitude = fresh.analogShowLatitude;
        cfg.latitudeDecimals = fresh.latitudeDecimals;
        cfg.showLongitude = fresh.showLongitude;
        cfg.analogShowLongitude = fresh.analogShowLongitude;
        cfg.attachToHotbarCompass = fresh.attachToHotbarCompass;
        cfg.compactHud = fresh.compactHud;
        cfg.displayZoneInHud = fresh.displayZoneInHud;
        cfg.zoneFollowsCompass = fresh.zoneFollowsCompass;
        cfg.zoneHAnchor = fresh.zoneHAnchor;
        cfg.zoneVAnchor = fresh.zoneVAnchor;
        cfg.zoneOffsetX = fresh.zoneOffsetX;
        cfg.zoneOffsetY = fresh.zoneOffsetY;
        cfg.displayBiomeInHud = fresh.displayBiomeInHud;
        cfg.biomeFollowsCompass = fresh.biomeFollowsCompass;
        cfg.biomeHAnchor = fresh.biomeHAnchor;
        cfg.biomeVAnchor = fresh.biomeVAnchor;
        cfg.biomeOffsetX = fresh.biomeOffsetX;
        cfg.biomeOffsetY = fresh.biomeOffsetY;
        cfg.biomeBeforeZone = fresh.biomeBeforeZone;
        cfg.coordsFollowsCompass = fresh.coordsFollowsCompass;
        cfg.coordsHAnchor = fresh.coordsHAnchor;
        cfg.coordsVAnchor = fresh.coordsVAnchor;
        cfg.coordsOffsetX = fresh.coordsOffsetX;
        cfg.coordsOffsetY = fresh.coordsOffsetY;
        cfg.customFaceRgb = fresh.customFaceRgb;
        cfg.customRingArgb = fresh.customRingArgb;
        cfg.customMutedArgb = fresh.customMutedArgb;
        cfg.customNeedleArgb = fresh.customNeedleArgb;
    }

    private static void applyDefaults(LatitudeConfig cfg) {
        LatitudeConfig.zoneEnterTitleEnabled = true;
        LatitudeConfig.zoneEnterTitleSeconds = 6.0;
        LatitudeConfig.zoneEnterTitleScale = 1.8;
        LatitudeConfig.zoneEnterTitleColorPreset = LatitudeConfig.TitleColorPreset.WHITE;
        LatitudeConfig.zoneEnterTitleRgb = 0xFFFFFF;
        LatitudeConfig.zoneEnterTitleCase = LatitudeConfig.TitleCaseMode.NORMAL;
        LatitudeConfig.zoneEnterTitleLetterSpacing = 0;
        LatitudeConfig.zoneEnterTitleOffsetX = 0;
        LatitudeConfig.zoneEnterTitleOffsetY = -40;
        LatitudeConfig.zoneEnterTitleDraggable = true;
        LatitudeConfig.hudSnapEnabled = true;
        LatitudeConfig.hudSnapPixels = 8;
        LatitudeConfig.showLatitudeDegreesOnCompass = false;
        LatitudeConfig.showZoneBaseDegreesOnTitle = true;
        LatitudeConfig.latitudeBandBlendingEnabled = true;
        LatitudeConfig.latitudeBandBlendWidthFrac = 0.08;
        LatitudeConfig.latitudeBandBoundaryWarpFrac = 0.06;
        LatitudeConfig.debugLatitudeBlend = false;
        LatitudeConfig.showWarningMessages = true;
        LatitudeConfig.screenshotClipboardEnabled = true;
        LatitudeConfig.screenshotClipboardFallbackToDisk = true;
        LatitudeConfig.screenshotAlsoSaveToDisk = true;
        LatitudeConfig.screenshotClipboardWindowsPowerShell = isWindows();
        LatitudeConfig.captureWriteCsv = false;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private void drawPanel(GuiGraphicsExtractor context) {
        int inset = 10;
        int x0 = panelX - inset;
        int y0 = panelTop - inset - 16;
        int x1 = panelX + panelWidth + inset;
        int y1 = panelBottom + inset + 12;

        // Outer glow
        context.fill(x0, y0, x1, y1, 0x55000000);

        // Panel shell
        context.fill(panelX, panelTop - 14, panelX + panelWidth, panelBottom + 10, PANEL_BORDER);
        context.fill(panelX + 1, panelTop - 13, panelX + panelWidth - 1, panelBottom + 9, PANEL_BG);

        // Grid detail
        drawGridDecoration(context, panelX + 2, panelTop - 12, panelWidth - 4, (panelBottom + 8) - (panelTop - 12));

        // Inner frame accent
        context.fill(panelX + 2, panelTop - 12, panelX + panelWidth - 2, panelTop - 11, GOLD & 0x66FFFFFF);
        context.fill(panelX + 2, panelBottom + 7, panelX + panelWidth - 2, panelBottom + 8, GOLD & 0x66FFFFFF);
    }

    private void drawHeader(GuiGraphicsExtractor context) {
        int centerX = this.width / 2;
        int titleY = this.panelTop - 70;
        int subtitleY = titleY + 18;
        int helperY = subtitleY + 14;
        int sectionY = this.panelTop - 26;

        drawScaledCenteredText(context, Component.literal("LATITUDE"), centerX, titleY, 1.4f, GOLD, false);
        drawCenteredText(context, Component.literal("Settings"), centerX, subtitleY, WARM_WHITE, true);
        drawCenteredText(context, Component.literal("Configure HUD, capture, and alerts"), centerX, helperY, MUTED, false);
        drawCenteredText(context, Component.literal("Client"), centerX, sectionY, MUTED, true);
    }

    private void drawCenteredText(GuiGraphicsExtractor context, Component text, int centerX, int y, int color, boolean shadow) {
        int w = this.font.width(text);
        context.text(this.font, text, centerX - w / 2, y, color, shadow);
    }

    private void drawScaledCenteredText(GuiGraphicsExtractor context, Component text, int centerX, int y, float scale, int color, boolean shadow) {
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate((float) centerX, (float) y);
        matrices.scale(scale, scale);
        int w = this.font.width(text);
        context.text(this.font, text, -w / 2, 0, color, shadow);
        matrices.popMatrix();
    }

    private void drawGridDecoration(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        if (h < 20 || w < 20) return;
        for (int gy = GRID_STEP; gy < h; gy += GRID_STEP) {
            context.fill(x, y + gy, x + w, y + gy + 1, GRID_COLOR);
        }
        for (int gx = GRID_STEP; gx < w; gx += GRID_STEP) {
            context.fill(x + gx, y, x + gx + 1, y + h, GRID_COLOR);
        }
    }

    private interface DoubleConsumer {
        void accept(double v);
    }

    private static final class StepSlider extends AbstractSliderButton {
        private final Component label;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleConsumer onChange;

        private StepSlider(int x, int y, int width, int height, Component label, double min, double max, double step, double initial, DoubleConsumer onChange) {
            super(x, y, width, height, Component.empty(), toNorm(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(label.getString() + ": " + format(getValue())));
        }

        @Override
        protected void applyValue() {
            onChange.accept(getValue());
        }

        private double getValue() {
            if (max <= min) return min;
            double raw = min + (max - min) * this.value;
            double q = step > 0.0 ? Math.round(raw / step) * step : raw;
            if (q < min) q = min;
            if (q > max) q = max;
            return q;
        }

        private static double toNorm(double v, double min, double max) {
            if (max == min) return 0.0;
            return (v - min) / (max - min);
        }

        private static String format(double v) {
            return String.format(java.util.Locale.ROOT, "%.1f", v);
        }
    }
}
