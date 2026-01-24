package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LatitudeSettingsScreen extends Screen {
    private final Screen parent;

    private int scrollY = 0;
    private int contentHeight = 0;
    private final List<ClickableWidget> layoutWidgets = new ArrayList<>();
    private final List<Integer> layoutBaseYs = new ArrayList<>();

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

        int cx = this.width / 2;
        int y = 28;
        int w = 220;
        int h = 20;

        final int columnX = (this.width - w) / 2;

        int baseY;

        baseY = y;
        var wZoneTitle = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v ? "ON" : "OFF"), LatitudeConfig.zoneEnterTitleEnabled)
                .values(true, false)
                .build(columnX, y, w, h, Text.literal("Zone Enter Title"), (btn, value) -> LatitudeConfig.zoneEnterTitleEnabled = value));
        layoutWidgets.add(wZoneTitle);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wTitleSec = this.addDrawableChild(new StepSlider(columnX, y, w, h, Text.literal("Title Duration (seconds)"), 2.0, 10.0, 0.5, LatitudeConfig.zoneEnterTitleSeconds, v -> LatitudeConfig.zoneEnterTitleSeconds = v));
        layoutWidgets.add(wTitleSec);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wTitleScale = this.addDrawableChild(new StepSlider(columnX, y, w, h, Text.literal("Title Size"), 1.0, 3.0, 0.1, LatitudeConfig.zoneEnterTitleScale, v -> LatitudeConfig.zoneEnterTitleScale = v));
        layoutWidgets.add(wTitleScale);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wShowLatDeg = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v ? "ON" : "OFF"), LatitudeConfig.showLatitudeDegreesOnCompass)
                .values(true, false)
                .build(columnX, y, w, h, Text.literal("Show Latitude Degrees on Compass"), (btn, value) -> LatitudeConfig.showLatitudeDegreesOnCompass = value));
        layoutWidgets.add(wShowLatDeg);
        layoutBaseYs.add(baseY);
        y += 28;

        baseY = y;
        var wSnap = this.addDrawableChild(ButtonWidget.builder(Text.literal("HUD Snap: " + (LatitudeConfig.hudSnapEnabled ? "ON" : "OFF")), btn -> {
                    LatitudeConfig.hudSnapEnabled = !LatitudeConfig.hudSnapEnabled;
                    LatitudeConfig.saveCurrent();
                    btn.setMessage(Text.literal("HUD Snap: " + (LatitudeConfig.hudSnapEnabled ? "ON" : "OFF")));
                })
                .dimensions(columnX, y, w, 20)
                .build());
        layoutWidgets.add(wSnap);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wSnapPixels = this.addDrawableChild(ButtonWidget.builder(Text.literal("Snap Pixels: " + LatitudeConfig.hudSnapPixels), btn -> {
                    int[] opts = {4, 8, 12, 16};
                    int idx = 0;
                    for (int i = 0; i < opts.length; i++) {
                        if (opts[i] == LatitudeConfig.hudSnapPixels) idx = i;
                    }
                    LatitudeConfig.hudSnapPixels = opts[(idx + 1) % opts.length];
                    LatitudeConfig.saveCurrent();
                    btn.setMessage(Text.literal("Snap Pixels: " + LatitudeConfig.hudSnapPixels));
                })
                .dimensions(columnX, y, w, 20)
                .build());
        layoutWidgets.add(wSnapPixels);
        layoutBaseYs.add(baseY);
        y += 28;

        baseY = y;
        var wCompassEnabled = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v ? "ON" : "OFF"), cfg.enabled)
                .values(true, false)
                .build(columnX, y, w, h, Text.literal("Compass HUD"), (btn, value) -> cfg.enabled = value));
        layoutWidgets.add(wCompassEnabled);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wShowMode = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v.name()), cfg.showMode)
                .values(CompassHudConfig.ShowMode.values())
                .build(columnX, y, w, h, Text.literal("Show Mode"), (btn, value) -> cfg.showMode = value));
        layoutWidgets.add(wShowMode);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wDirMode = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v.name()), cfg.directionMode)
                .values(CompassHudConfig.DirectionMode.values())
                .build(columnX, y, w, h, Text.literal("Direction Mode"), (btn, value) -> cfg.directionMode = value));
        layoutWidgets.add(wDirMode);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wHAnchor = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v.name()), cfg.hAnchor)
                .values(CompassHudConfig.HAnchor.values())
                .build(columnX, y, w, h, Text.literal("H Anchor"), (btn, value) -> retargetCompassPosition(cfg, c -> c.hAnchor = value)));
        layoutWidgets.add(wHAnchor);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wVAnchor = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v.name()), cfg.vAnchor)
                .values(CompassHudConfig.VAnchor.values())
                .build(columnX, y, w, h, Text.literal("V Anchor"), (btn, value) -> retargetCompassPosition(cfg, c -> c.vAnchor = value)));
        layoutWidgets.add(wVAnchor);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wAttach = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v ? "ON" : "OFF"), cfg.attachToHotbarCompass)
                .values(true, false)
                .build(columnX, y, w, h, Text.literal("Attach To Hotbar Compass"), (btn, value) -> retargetCompassPosition(cfg, c -> c.attachToHotbarCompass = value)));
        layoutWidgets.add(wAttach);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wBg = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v ? "ON" : "OFF"), cfg.showBackground)
                .values(true, false)
                .build(columnX, y, w, h, Text.literal("Background"), (btn, value) -> cfg.showBackground = value));
        layoutWidgets.add(wBg);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wShadow = this.addDrawableChild(CyclingButtonWidget.builder(v -> Text.literal(v ? "ON" : "OFF"), cfg.shadow)
                .values(true, false)
                .build(columnX, y, w, h, Text.literal("Shadow"), (btn, value) -> cfg.shadow = value));
        layoutWidgets.add(wShadow);
        layoutBaseYs.add(baseY);
        y += 28;

        baseY = y;
        var wBgAlpha = this.addDrawableChild(new IntSlider(columnX, y, w, h, Text.literal("Background Alpha"), 0, 255, cfg.backgroundAlpha, v -> cfg.backgroundAlpha = v));
        layoutWidgets.add(wBgAlpha);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wScale = this.addDrawableChild(new FloatSlider(columnX, y, w, h, Text.literal("Scale"), 0.5f, 3.0f, cfg.scale, v -> cfg.scale = v));
        layoutWidgets.add(wScale);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wOffX = this.addDrawableChild(new IntSlider(columnX, y, w, h, Text.literal("Offset X"), -200, 200, cfg.offsetX, v -> cfg.offsetX = v));
        layoutWidgets.add(wOffX);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wOffY = this.addDrawableChild(new IntSlider(columnX, y, w, h, Text.literal("Offset Y"), -200, 200, cfg.offsetY, v -> cfg.offsetY = v));
        layoutWidgets.add(wOffY);
        layoutBaseYs.add(baseY);
        y += 28;

        baseY = y;
        var wTextColor = this.addDrawableChild(CyclingButtonWidget.builder(this::textColorLabel, textColorName(cfg.textRgb))
                .values("WHITE", "YELLOW", "RED", "CYAN")
                .build(columnX, y, w, h, Text.literal("Text Color"), (btn, value) -> cfg.textRgb = textColorRgb(value)));
        layoutWidgets.add(wTextColor);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wBgColor = this.addDrawableChild(CyclingButtonWidget.builder(this::bgColorLabel, bgColorName(cfg.backgroundRgb))
                .values("BLACK", "DARK_GRAY", "BLUE")
                .build(columnX, y, w, h, Text.literal("Background Color"), (btn, value) -> cfg.backgroundRgb = bgColorRgb(value)));
        layoutWidgets.add(wBgColor);
        layoutBaseYs.add(baseY);
        y += 34;

        baseY = y;
        var wAdjust = this.addDrawableChild(ButtonWidget.builder(Text.literal("Adjust HUD position..."), b -> {
                    MinecraftClient.getInstance().setScreen(new LatitudeHudAdjustScreen(this));
                })
                .dimensions(columnX, y, w, 20)
                .build());
        layoutWidgets.add(wAdjust);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wDone = this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> {
                    CompassHudConfig.saveCurrent();
                    LatitudeConfig.saveCurrent();
                    if (this.client != null) {
                        this.client.setScreen(this.parent);
                    }
                })
                .dimensions(columnX, y, 70, 20)
                .build());
        layoutWidgets.add(wDone);
        layoutBaseYs.add(baseY);

        baseY = y;
        var wReset = this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> {
                    applyDefaults(cfg);
                    applyDefaults(latCfg);
                    CompassHudConfig.saveCurrent();
                    LatitudeConfig.saveCurrent();
                    this.clearChildren();
                    this.init();
                })
                .dimensions(columnX + 150, y, 70, 20)
                .build());
        layoutWidgets.add(wReset);
        layoutBaseYs.add(baseY);

        y += 40;
        this.contentHeight = y;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderInGameBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);

        int maxScroll = Math.max(0, contentHeight - (this.height - 20));
        if (scrollY < 0) scrollY = 0;
        if (scrollY > maxScroll) scrollY = maxScroll;

        for (int i = 0; i < layoutWidgets.size(); i++) {
            ClickableWidget w = layoutWidgets.get(i);
            int baseY = layoutBaseYs.get(i);
            int drawY = baseY - scrollY;
            w.setY(drawY);

            boolean visible = drawY > -40 && drawY < (this.height + 40);
            w.visible = visible;
            w.active = visible;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, contentHeight - (this.height - 20));
        scrollY -= (int) Math.signum(verticalAmount) * 18;
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));
        return true;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void retargetCompassPosition(CompassHudConfig cfg, Consumer<CompassHudConfig> change) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            change.accept(cfg);
            return;
        }

        boolean wasAttached = cfg.attachToHotbarCompass;
        var before = CompassHud.computeBounds(mc, cfg);
        int absX = before.x();
        int absY = before.y();

        change.accept(cfg);

        boolean nowAttached = cfg.attachToHotbarCompass;
        if (!wasAttached && nowAttached) {
            cfg.offsetX = 0;
            cfg.offsetY = 0;
            return;
        }

        var base = CompassHud.computeBasePosition(mc, cfg);
        cfg.offsetX = absX - base.x();
        cfg.offsetY = absY - base.y();

        var after = CompassHud.computeBounds(mc, cfg);
        cfg.offsetX = after.x() - base.x();
        cfg.offsetY = after.y() - base.y();
    }

    private static void drawOutline(DrawContext ctx, int x, int y, int w, int h, int argb) {
        int x2 = x + w;
        int y2 = y + h;
        ctx.fill(x, y, x2, y + 1, argb);
        ctx.fill(x, y2 - 1, x2, y2, argb);
        ctx.fill(x, y, x + 1, y2, argb);
        ctx.fill(x2 - 1, y, x2, y2, argb);
    }

    private static void applyDefaults(CompassHudConfig cfg) {
        cfg.enabled = true;
        cfg.showMode = CompassHudConfig.ShowMode.COMPASS_PRESENT;
        cfg.directionMode = CompassHudConfig.DirectionMode.CARDINAL_8;
        cfg.hAnchor = CompassHudConfig.HAnchor.CENTER;
        cfg.vAnchor = CompassHudConfig.VAnchor.TOP;
        cfg.offsetX = 0;
        cfg.offsetY = 6;
        cfg.scale = 1.0f;
        cfg.padding = 3;
        cfg.showBackground = true;
        cfg.backgroundRgb = 0x000000;
        cfg.backgroundAlpha = 90;
        cfg.textRgb = 0xFFFFFF;
        cfg.textAlpha = 255;
        cfg.shadow = true;
        cfg.attachToHotbarCompass = false;
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
    }

    private Text textColorLabel(String v) {
        return Text.literal(v);
    }

    private static String textColorName(int rgb) {
        int c = rgb & 0xFFFFFF;
        if (c == 0xFFFF00) return "YELLOW";
        if (c == 0xFF0000) return "RED";
        if (c == 0x00FFFF) return "CYAN";
        return "WHITE";
    }

    private static int textColorRgb(String name) {
        return switch (name) {
            case "YELLOW" -> 0xFFFF00;
            case "RED" -> 0xFF0000;
            case "CYAN" -> 0x00FFFF;
            default -> 0xFFFFFF;
        };
    }

    private Text bgColorLabel(String v) {
        return Text.literal(v);
    }

    private static String bgColorName(int rgb) {
        int c = rgb & 0xFFFFFF;
        if (c == 0x202020) return "DARK_GRAY";
        if (c == 0x0000AA) return "BLUE";
        return "BLACK";
    }

    private static int bgColorRgb(String name) {
        return switch (name) {
            case "DARK_GRAY" -> 0x202020;
            case "BLUE" -> 0x0000AA;
            default -> 0x000000;
        };
    }

    private interface IntConsumer {
        void accept(int v);
    }

    private interface FloatConsumer {
        void accept(float v);
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

    private static final class IntSlider extends SliderWidget {
        private final Text label;
        private final int min;
        private final int max;
        private final IntConsumer onChange;

        private IntSlider(int x, int y, int width, int height, Text label, int min, int max, int initial, IntConsumer onChange) {
            super(x, y, width, height, Text.empty(), toNorm(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal(label.getString() + ": " + getValue()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(getValue());
        }

        private int getValue() {
            return MathHelper.clamp((int) Math.round(min + (max - min) * this.value), min, max);
        }

        private static double toNorm(int v, int min, int max) {
            if (max == min) return 0.0;
            return (double) (v - min) / (double) (max - min);
        }
    }

    private static final class FloatSlider extends SliderWidget {
        private final Text label;
        private final float min;
        private final float max;
        private final FloatConsumer onChange;

        private FloatSlider(int x, int y, int width, int height, Text label, float min, float max, float initial, FloatConsumer onChange) {
            super(x, y, width, height, Text.empty(), toNorm(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
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

        private float getValue() {
            float v = min + (max - min) * (float) this.value;
            return MathHelper.clamp(v, min, max);
        }

        private static double toNorm(float v, float min, float max) {
            if (max == min) return 0.0;
            return (v - min) / (max - min);
        }

        private static String format(float v) {
            return String.format(java.util.Locale.ROOT, "%.2f", v);
        }
    }
}
