package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

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
    private final List<ClickableWidget> layoutWidgets = new ArrayList<>();
    private final List<Integer> layoutBaseYs = new ArrayList<>();
    private ButtonWidget doneButton;
    private ButtonWidget resetButton;
    private int footerY;
    private boolean compassExpanded = false;
    private int compassPreviewBaseY = -1;

    public LatitudeSettingsScreen(Screen parent) {
        super(Text.literal("Latitude Settings"));
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

        baseY = y;
        var wZoneTitle = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v ? "ON" : "OFF"), LatitudeConfig.zoneEnterTitleEnabled)
                .values(true, false)
                .build(columnX, y, w, h, Text.literal("Zone Enter Title"), (btn, value) -> LatitudeConfig.zoneEnterTitleEnabled = value));
        layoutWidgets.add(wZoneTitle);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wWarnText = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v ? "ON" : "OFF"), LatitudeConfig.showWarningMessages)
                .values(true, false)
                .build(columnX, y, w, h, Text.literal("Warning messages"), (btn, value) -> {
                    LatitudeConfig.showWarningMessages = value;
                    LatitudeConfig.saveCurrent();
                }));
        wWarnText.setTooltip(Tooltip.of(Text.literal("Show/hide on-screen warning text (fog/particles/effects still apply).")));
        layoutWidgets.add(wWarnText);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wCaptureClipboard = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v ? "ON" : "OFF"), LatitudeConfig.screenshotClipboardEnabled)
                .values(true, false)
                .build(columnX, y, w, h, Text.translatable("option.globe.capture_clipboard"), (btn, value) -> LatitudeConfig.screenshotClipboardEnabled = value));
        wCaptureClipboard.setTooltip(Tooltip.of(Text.translatable("option.globe.capture_clipboard.tooltip")));
        layoutWidgets.add(wCaptureClipboard);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wCapturePowerShell = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v ? "ON" : "OFF"), LatitudeConfig.screenshotClipboardWindowsPowerShell)
                .values(true, false)
                .build(columnX, y, w, h, Text.translatable("option.globe.capture_windows_powershell"), (btn, value) -> LatitudeConfig.screenshotClipboardWindowsPowerShell = value));
        wCapturePowerShell.setTooltip(Tooltip.of(Text.translatable("option.globe.capture_windows_powershell.tooltip")));
        layoutWidgets.add(wCapturePowerShell);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wCaptureAlsoSave = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v ? "ON" : "OFF"), LatitudeConfig.screenshotAlsoSaveToDisk)
                .values(true, false)
                .build(columnX, y, w, h, Text.translatable("option.globe.capture_save_disk"), (btn, value) -> LatitudeConfig.screenshotAlsoSaveToDisk = value));
        wCaptureAlsoSave.setTooltip(Tooltip.of(Text.translatable("option.globe.capture_save_disk.tooltip")));
        layoutWidgets.add(wCaptureAlsoSave);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wCaptureCsv = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v ? "ON" : "OFF"), LatitudeConfig.captureWriteCsv)
                .values(true, false)
                .build(columnX, y, w, h, Text.translatable("option.globe.capture_write_csv"), (btn, value) -> LatitudeConfig.captureWriteCsv = value));
        wCaptureCsv.setTooltip(Tooltip.of(Text.translatable("option.globe.capture_write_csv.tooltip")));
        layoutWidgets.add(wCaptureCsv);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wTitleSec = this.addDrawableChild(new StepSlider(columnX, y, w, h, Text.literal("Title Duration (seconds)"), 2.0, 10.0, 0.5, LatitudeConfig.zoneEnterTitleSeconds, v -> LatitudeConfig.zoneEnterTitleSeconds = v));
        layoutWidgets.add(wTitleSec);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wCompassExpander = this.addDrawableChild(ButtonWidget.builder(Text.literal(compassSummary(cfg)), b -> {
                    compassExpanded = !compassExpanded;
                    this.clearChildren();
                    this.init();
                })
                .dimensions(columnX, y, w, h)
                .build());
        layoutWidgets.add(wCompassExpander);
        layoutBaseYs.add(baseY);
        y += 24;

        if (compassExpanded) {
            baseY = y;
            var wCompassStyle = this.addDrawableChild(CyclingButtonWidget.<CompassHudConfig.CompassStyle>builder(v -> Text.literal(v == CompassHudConfig.CompassStyle.ANALOG ? "Analog" : "Digital"), () -> cfg.style)
                    .values(CompassHudConfig.CompassStyle.values())
                    .build(columnX, y, w, h, Text.literal("Style"), (btn, value) -> {
                        cfg.style = value;
                        CompassHudConfig.saveCurrent();
                        this.clearChildren();
                        this.init();
                    }));
            layoutWidgets.add(wCompassStyle);
            layoutBaseYs.add(baseY);
            y += 24;

            baseY = y;
            var wCompassCompact = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> cfg.compactHud)
                    .values(true, false)
                    .build(columnX, y, w, h, Text.literal("Compact"), (btn, value) -> cfg.compactHud = value));
            layoutWidgets.add(wCompassCompact);
            layoutBaseYs.add(baseY);
            y += 24;

            compassPreviewBaseY = y;
            y += 36;
        } else {
            compassPreviewBaseY = -1;
        }

        baseY = y;
        var wDisplayZone = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> cfg.displayZoneInHud)
                .values(true, false)
                .build(columnX, y, w, h, Text.literal("Zone Label"), (btn, value) -> cfg.displayZoneInHud = value));
        layoutWidgets.add(wDisplayZone);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wShowMode = this.addDrawableChild(CyclingButtonWidget.<CompassHudConfig.ShowMode>builder(this::showModeLabel, () -> cfg.showMode)
                .values(CompassHudConfig.ShowMode.values())
                .build(columnX, y, w, h, Text.literal("Show Mode"), (btn, value) -> cfg.showMode = value));
        layoutWidgets.add(wShowMode);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wHudStudio = this.addDrawableChild(ButtonWidget.builder(Text.literal("HUD Studio"), b -> {
                    MinecraftClient.getInstance().setScreen(new LatitudeHudStudioScreen(this));
                })
                .dimensions(columnX, y, w, 20)
                .build());
        layoutWidgets.add(wHudStudio);
        layoutBaseYs.add(baseY);
        y += 24;

        this.contentHeight = y - this.panelTop;

        int footerY = this.panelBottom - 26;
        int buttonWidth = 96;
        int buttonSpacing = 14;
        int footerTotal = buttonWidth * 2 + buttonSpacing;
        int footerX = this.panelX + (this.panelWidth - footerTotal) / 2;

        this.footerY = this.panelBottom - 28;

        this.doneButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> {
                    CompassHudConfig.saveCurrent();
                    LatitudeConfig.saveCurrent();
                    if (this.client != null) {
                        this.client.setScreen(this.parent);
                    }
                })
                .dimensions(footerX, footerY, buttonWidth, 20)
                .build());

        this.resetButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> {
                    applyDefaults(cfg);
                    applyDefaults(latCfg);
                    CompassHudConfig.saveCurrent();
                    LatitudeConfig.saveCurrent();
                    this.clearChildren();
                    this.init();
                })
                .dimensions(footerX + buttonWidth + buttonSpacing, footerY, buttonWidth, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, BG_COLOR);
        drawPanel(context);
        drawHeader(context);

        int maxScroll = Math.max(0, contentHeight - (this.footerY - this.panelTop));
        scrollY = MathHelper.clamp(scrollY, 0, maxScroll);

        for (int i = 0; i < layoutWidgets.size(); i++) {
            ClickableWidget w = layoutWidgets.get(i);
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
            if (previewY >= panelTop && previewY + 28 <= this.footerY) {
                drawCompassPreview(context, columnXCache, previewY, columnWCache, 28, CompassHudConfig.get());
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, contentHeight - (this.footerY - this.panelTop));
        scrollY -= (int) Math.signum(verticalAmount) * 18;
        scrollY = MathHelper.clamp(scrollY, 0, maxScroll);
        return true;
    }

    private Text showModeLabel(CompassHudConfig.ShowMode v) {
        if (v == null) return Text.literal("Always");
        return switch (v) {
            case COMPASS_PRESENT -> Text.literal("When compass is in inventory");
            case HOLDING_COMPASS -> Text.literal("When holding compass");
            case ALWAYS -> Text.literal("Always");
        };
    }

    private String compassSummary(CompassHudConfig cfg) {
        String style = cfg.style == CompassHudConfig.CompassStyle.ANALOG ? "Analog" : "Digital";
        String compact = cfg.compactHud ? "Compact: ON" : "Compact: OFF";
        return "Compass Style: " + style + " | " + compact;
    }

    private void drawCompassPreview(DrawContext ctx, int x, int y, int w, int h, CompassHudConfig cfg) {
        int boxColor = 0x33111111;
        ctx.fill(x, y, x + w, y + h, boxColor);
        int padding = 6;
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            int radius = Math.min(10, Math.min(w, h) / 3);
            int cx = x + padding + radius;
            int cy = y + h / 2;
            ctx.fill(cx - radius, cy - radius, cx + radius, cy + radius, 0xAA1A1410);
            ctx.fill(cx - radius, cy - radius, cx - radius + 1, cy + radius, 0xFFD4A74A);
            ctx.fill(cx + radius - 1, cy - radius, cx + radius, cy + radius, 0xFFD4A74A);
            ctx.fill(cx - radius, cy - radius, cx + radius, cy - radius + 1, 0xFFD4A74A);
            ctx.fill(cx - radius, cy + radius - 1, cx + radius, cy + radius, 0xFFD4A74A);
            ctx.fill(cx, cy - radius + 2, cx + 1, cy - radius + 6, 0xFFD4A74A);
            ctx.drawText(this.textRenderer, "N", cx - 1, cy - radius + 7, 0xFFCC3333, true);
            int textX = cx + radius + 6;
            ctx.drawText(this.textRenderer, "57\u00b0S" + (cfg.compactHud ? " Tropics" : " · Tropics"), textX, cy - this.textRenderer.fontHeight / 2, 0xFFFFFFFF, true);
        } else {
            String text = cfg.compactHud ? "NW 57\u00b0S Tropics" : "NW · 57\u00b0S · Tropics";
            ctx.drawText(this.textRenderer, text, x + padding, y + h / 2 - this.textRenderer.fontHeight / 2, 0xFFFFFFFF, true);
        }
    }


    private static void applyDefaults(CompassHudConfig cfg) {
        cfg.enabled = true;
        cfg.showMode = CompassHudConfig.ShowMode.COMPASS_PRESENT;
        cfg.style = CompassHudConfig.CompassStyle.DIGITAL;
        cfg.directionMode = CompassHudConfig.DirectionMode.CARDINAL_8;
        cfg.hAnchor = CompassHudConfig.HAnchor.CENTER;
        cfg.vAnchor = CompassHudConfig.VAnchor.TOP;
        cfg.offsetX = 0;
        cfg.offsetY = 0;
        cfg.scale = 1.0f;
        cfg.analogSize = 48.0f;
        cfg.analogInnerAlpha = 0.65f;
        cfg.padding = 3;
        cfg.showBackground = true;
        cfg.backgroundRgb = 0x000000;
        cfg.backgroundAlpha = 64;
        cfg.textRgb = 0xFFFFFF;
        cfg.textAlpha = 255;
        cfg.shadow = true;
        cfg.showLatitude = true;
        cfg.analogShowLatitude = true;
        cfg.latitudeDecimals = 0;
        cfg.attachToHotbarCompass = false;
        cfg.compactHud = false;
        cfg.displayZoneInHud = false;
        cfg.zoneFollowsCompass = true;
        cfg.zoneHAnchor = CompassHudConfig.HAnchor.CENTER;
        cfg.zoneVAnchor = CompassHudConfig.VAnchor.TOP;
        cfg.zoneOffsetX = 0;
        cfg.zoneOffsetY = 0;
    }

    private static void applyDefaults(LatitudeConfig cfg) {
        LatitudeConfig.zoneEnterTitleEnabled = true;
        LatitudeConfig.zoneEnterTitleSeconds = 6.0;
        LatitudeConfig.zoneEnterTitleScale = 1.8;
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

    private void drawPanel(DrawContext context) {
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

    private void drawHeader(DrawContext context) {
        int centerX = this.width / 2;
        int titleY = this.panelTop - 70;
        int subtitleY = titleY + 18;
        int helperY = subtitleY + 14;
        int sectionY = this.panelTop - 18;

        drawScaledCenteredText(context, Text.literal("LATITUDE"), centerX, titleY, 1.4f, GOLD, false);
        drawCenteredText(context, Text.literal("Settings"), centerX, subtitleY, WARM_WHITE, true);
        drawCenteredText(context, Text.literal("Configure HUD, capture, and alerts"), centerX, helperY, MUTED, false);
        drawCenteredText(context, Text.literal("Client"), centerX, sectionY, MUTED, true);
    }

    private void drawCenteredText(DrawContext context, Text text, int centerX, int y, int color, boolean shadow) {
        int w = this.textRenderer.getWidth(text);
        context.drawText(this.textRenderer, text, centerX - w / 2, y, color, shadow);
    }

    private void drawScaledCenteredText(DrawContext context, Text text, int centerX, int y, float scale, int color, boolean shadow) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) centerX, (float) y);
        matrices.scale(scale, scale);
        int w = this.textRenderer.getWidth(text);
        context.drawText(this.textRenderer, text, -w / 2, 0, color, shadow);
        matrices.popMatrix();
    }

    private void drawGridDecoration(DrawContext context, int x, int y, int w, int h) {
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

    private static final class StepSlider extends SliderWidget {
        private final Text label;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleConsumer onChange;

        private StepSlider(int x, int y, int width, int height, Text label, double min, double max, double step, double initial, DoubleConsumer onChange) {
            super(x, y, width, height, Text.empty(), toNorm(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal(label.getString() + ": " + format(getValue())));
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
