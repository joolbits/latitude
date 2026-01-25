package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class LatitudeHudStudioScreen extends Screen {
    private final Screen parent;

    private boolean sidebarVisible = true;
    private int sidebarWidth = 180;

    private enum Target { COMPASS, TITLE, BOTH }
    private Target target = Target.COMPASS;

    private boolean draggingTitle = false;
    private boolean draggingCompass = false;
    private double lastMouseX;
    private double lastMouseY;

    private int compassGrabDx;
    private int compassGrabDy;

    private boolean wasLDown = false;

    private ClickableWidget targetButton;

    private ClickableWidget compassScale;
    private ClickableWidget compassTransparency;
    private ClickableWidget compassBackground;
    private ClickableWidget compassBackgroundColor;
    private ClickableWidget compassTextColor;
    private ClickableWidget compassShowLatitude;
    private ClickableWidget compassCompactHud;

    private ClickableWidget titleEnabled;
    private ClickableWidget titleDuration;
    private ClickableWidget titleScale;

    public LatitudeHudStudioScreen(Screen parent) {
        super(Text.literal("HUD Studio"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearChildren();

        int panelX = 8;
        int panelY = 28;
        int panelW = Math.min(sidebarWidth - 16, 220);
        int rowH = 20;
        int rowGap = 4;

        var compassCfg = CompassHudConfig.get();

        int y = panelY;

        this.targetButton = this.addDrawableChild(ButtonWidget.builder(targetLabel(), b -> {
                    target = switch (target) {
                        case COMPASS -> Target.TITLE;
                        case TITLE -> Target.BOTH;
                        case BOTH -> Target.COMPASS;
                    };
                    b.setMessage(targetLabel());
                    updateSidebarVisibility();
                })
                .dimensions(panelX, y, panelW, rowH)
                .build());

        y += rowH + rowGap;

        this.compassScale = this.addDrawableChild(new FloatSlider(panelX, y, panelW, rowH, Text.literal("Scale"), 0.5f, 3.0f, compassCfg.scale, v -> compassCfg.scale = v));
        y += rowH + rowGap;

        this.compassTransparency = this.addDrawableChild(new IntSlider(panelX, y, panelW, rowH, Text.literal("Transparency"), 0, 255, compassCfg.backgroundAlpha, v -> compassCfg.backgroundAlpha = v));
        y += rowH + rowGap;

        this.compassBackground = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> compassCfg.showBackground)
                .values(true, false)
                .build(panelX, y, panelW, rowH, Text.literal("Background"), (btn, value) -> compassCfg.showBackground = value));
        y += rowH + rowGap;

        this.compassBackgroundColor = this.addDrawableChild(CyclingButtonWidget.<String>builder(this::bgColorLabel, () -> bgColorName(compassCfg.backgroundRgb))
                .values("BLACK", "DARK_GRAY", "BLUE", "WHITE")
                .build(panelX, y, panelW, rowH, Text.literal("Background Color"), (btn, value) -> compassCfg.backgroundRgb = bgColorRgb(value)));
        y += rowH + rowGap;

        this.compassTextColor = this.addDrawableChild(CyclingButtonWidget.<String>builder(this::textColorLabel, () -> textColorName(compassCfg.textRgb))
                .values("WHITE", "BLACK", "YELLOW", "RED", "CYAN")
                .build(panelX, y, panelW, rowH, Text.literal("Text Color"), (btn, value) -> compassCfg.textRgb = textColorRgb(value)));
        y += rowH + rowGap;

        this.compassShowLatitude = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> Boolean.TRUE.equals(compassCfg.showLatitude))
                .values(true, false)
                .build(panelX, y, panelW, rowH, Text.literal("Show Latitude"), (btn, value) -> compassCfg.showLatitude = value));
        y += rowH + rowGap;

        this.compassCompactHud = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> compassCfg.compactHud)
                .values(true, false)
                .build(panelX, y, panelW, rowH, Text.literal("Compact HUD"), (btn, value) -> compassCfg.compactHud = value));
        y += rowH + rowGap;

        y += 6;

        this.titleEnabled = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> LatitudeConfig.zoneEnterTitleEnabled)
                .values(true, false)
                .build(panelX, y, panelW, rowH, Text.literal("Zone Enter Title"), (btn, value) -> LatitudeConfig.zoneEnterTitleEnabled = value));
        y += rowH + rowGap;

        this.titleDuration = this.addDrawableChild(new StepSlider(panelX, y, panelW, rowH, Text.literal("Title Duration (seconds)"), 2.0, 10.0, 0.5, LatitudeConfig.zoneEnterTitleSeconds, v -> LatitudeConfig.zoneEnterTitleSeconds = v));
        y += rowH + rowGap;

        this.titleScale = this.addDrawableChild(new StepSlider(panelX, y, panelW, rowH, Text.literal("Title Size"), 1.0, 3.0, 0.1, LatitudeConfig.zoneEnterTitleScale, v -> LatitudeConfig.zoneEnterTitleScale = v));

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> {
                    CompassHudConfig.saveCurrent();
                    LatitudeConfig.saveCurrent();
                    if (this.client != null) {
                        this.client.setScreen(this.parent);
                    }
                })
                .dimensions((this.width - 200) / 2, this.height - 28, 200, 20)
                .build());

        updateSidebarVisibility();
    }

    @Override
    public void tick() {
        super.tick();
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        boolean lDown = InputUtil.isKeyPressed(mc.getWindow(), InputUtil.GLFW_KEY_L);
        if (lDown && !wasLDown) {
            sidebarVisible = !sidebarVisible;
            updateSidebarVisibility();
        }
        wasLDown = lDown;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderInGameBackground(ctx);
        ctx.fill(0, 0, this.width, this.height, 0x66000000);

        if (sidebarVisible) {
            int px = 0;
            int py = 0;
            int pw = Math.min(sidebarWidth, this.width);
            int ph = this.height;
            ctx.fill(px, py, px + pw, py + ph, 0xAA000000);
            ctx.drawTextWithShadow(this.textRenderer, "HUD Studio", px + 8, py + 8, 0xFFFFFFFF);
            ctx.drawTextWithShadow(this.textRenderer, "Press L to hide", px + 8, py + 20, 0xFFCCCCCC);
        } else {
            ctx.drawTextWithShadow(this.textRenderer, "Press L to show settings", 8, 8, 0xFFCCCCCC);
        }

        renderPreview(ctx);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderPreview(DrawContext ctx) {
        var mc = MinecraftClient.getInstance();
        if (mc == null) return;

        double z = 0.0;
        var border = mc.world != null ? mc.world.getWorldBorder() : null;
        if (mc.player != null) {
            z = mc.player.getZ();
        }

        String degText = (border != null) ? LatitudeMath.formatLatitudeDeg(z, border) : "0\u00b0";
        String sampleTitle = "EQUATOR " + degText;

        ZoneEnterTitleOverlay.renderStaticAt(
                ctx,
                this.width,
                this.height,
                sampleTitle,
                LatitudeConfig.zoneEnterTitleScale,
                LatitudeConfig.zoneEnterTitleOffsetX,
                LatitudeConfig.zoneEnterTitleOffsetY);

        CompassHud.renderAdjustPreview(ctx, this.width, this.height);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) {
            return true;
        }

        double mx = click.x();
        double my = click.y();
        int button = click.button();

        if (button != 0) {
            return false;
        }

        if (sidebarVisible && mx < sidebarWidth) {
            return false;
        }

        if ((target == Target.TITLE || target == Target.BOTH) && isMouseOverTitle(mx, my)) {
            draggingTitle = true;
            lastMouseX = mx;
            lastMouseY = my;
            return true;
        }

        if ((target == Target.COMPASS || target == Target.BOTH) && isMouseOverCompass(mx, my)) {
            draggingCompass = true;
            lastMouseX = mx;
            lastMouseY = my;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (super.mouseDragged(click, deltaX, deltaY)) {
            return true;
        }

        double mx = click.x();
        double my = click.y();
        int button = click.button();

        if (button != 0) {
            return false;
        }

        if (draggingTitle) {
            LatitudeConfig.zoneEnterTitleOffsetX += (int) Math.round(mx - lastMouseX);
            LatitudeConfig.zoneEnterTitleOffsetY += (int) Math.round(my - lastMouseY);
            lastMouseX = mx;
            lastMouseY = my;

            if (LatitudeConfig.hudSnapEnabled) {
                LatitudeConfig.zoneEnterTitleOffsetX = snap(LatitudeConfig.zoneEnterTitleOffsetX, LatitudeConfig.hudSnapPixels);
                LatitudeConfig.zoneEnterTitleOffsetY = snap(LatitudeConfig.zoneEnterTitleOffsetY, LatitudeConfig.hudSnapPixels);
            }
            return true;
        }

        if (draggingCompass) {
            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) {
                return true;
            }

            var cfg = CompassHudConfig.get();
            if (cfg.attachToHotbarCompass) {
                return true;
            }

            int screenW = mc.getWindow().getScaledWidth();
            int screenH = mc.getWindow().getScaledHeight();

            int targetX = (int) Math.round(mx) - compassGrabDx;
            int targetY = (int) Math.round(my) - compassGrabDy;

            var b = CompassHud.computeBounds(mc, cfg);
            int boxW = b.w();
            int boxH = b.h();

            targetX = clamp(targetX, 0, Math.max(0, screenW - boxW));
            targetY = clamp(targetY, 0, Math.max(0, screenH - boxH));

            var base = CompassHud.computeBasePosition(mc, cfg);
            cfg.offsetX = targetX - base.x();
            cfg.offsetY = targetY - base.y();

            if (LatitudeConfig.hudSnapEnabled) {
                cfg.offsetX = snap(cfg.offsetX, LatitudeConfig.hudSnapPixels);
                cfg.offsetY = snap(cfg.offsetY, LatitudeConfig.hudSnapPixels);
            }

            lastMouseX = mx;
            lastMouseY = my;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            draggingTitle = false;
            if (draggingCompass) {
                draggingCompass = false;
                CompassHudConfig.saveCurrent();
            }
        }
        return super.mouseReleased(click);
    }

    private boolean isMouseOverCompass(double mx, double my) {
        var mc = MinecraftClient.getInstance();
        if (mc == null) {
            return false;
        }
        var cfg = CompassHudConfig.get();
        if (cfg.attachToHotbarCompass) {
            return false;
        }
        var b = CompassHud.computeBounds(mc, cfg);
        if (b == null) {
            return false;
        }
        if (!b.contains(mx, my)) {
            return false;
        }
        compassGrabDx = (int) Math.round(mx) - b.x();
        compassGrabDy = (int) Math.round(my) - b.y();
        return true;
    }

    private boolean isMouseOverTitle(double mx, double my) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) {
            return false;
        }

        double z = 0.0;
        var border = mc.world != null ? mc.world.getWorldBorder() : null;
        if (mc.player != null) {
            z = mc.player.getZ();
        }

        String degText = (border != null) ? LatitudeMath.formatLatitudeDeg(z, border) : "0\u00b0";
        String s = "EQUATOR " + degText;

        int w = this.textRenderer.getWidth(s);
        int h = this.textRenderer.fontHeight;

        double scale = MathHelper.clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);

        int cx = (this.width / 2) + LatitudeConfig.zoneEnterTitleOffsetX;
        int cy = (this.height / 2) + LatitudeConfig.zoneEnterTitleOffsetY;

        double halfW = (w * scale) / 2.0;
        double halfH = (h * scale) / 2.0;
        double pad = 6.0;

        return mx >= (cx - halfW - pad)
                && mx <= (cx + halfW + pad)
                && my >= (cy - halfH - pad)
                && my <= (cy + halfH + pad);
    }

    private void updateSidebarVisibility() {
        setVisible(targetButton, sidebarVisible);

        boolean compassActive = sidebarVisible && (target == Target.COMPASS || target == Target.BOTH);
        setVisible(compassScale, compassActive);
        setVisible(compassTransparency, compassActive);
        setVisible(compassBackground, compassActive);
        setVisible(compassBackgroundColor, compassActive);
        setVisible(compassTextColor, compassActive);
        setVisible(compassShowLatitude, compassActive);
        setVisible(compassCompactHud, compassActive);

        boolean titleActive = sidebarVisible && (target == Target.TITLE || target == Target.BOTH);
        setVisible(titleEnabled, titleActive);
        setVisible(titleDuration, titleActive);
        setVisible(titleScale, titleActive);
    }

    private static void setVisible(ClickableWidget w, boolean v) {
        if (w == null) return;
        w.visible = v;
        w.active = v;
    }

    private Text targetLabel() {
        return switch (target) {
            case COMPASS -> Text.literal("Target: Compass");
            case TITLE -> Text.literal("Target: Title");
            case BOTH -> Text.literal("Target: Both");
        };
    }

    private Text textColorLabel(String v) {
        return Text.literal(v);
    }

    private static String textColorName(int rgb) {
        int c = rgb & 0xFFFFFF;
        if (c == 0x000000) return "BLACK";
        if (c == 0xFFFF00) return "YELLOW";
        if (c == 0xFF0000) return "RED";
        if (c == 0x00FFFF) return "CYAN";
        return "WHITE";
    }

    private static int textColorRgb(String name) {
        return switch (name) {
            case "BLACK" -> 0x000000;
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
        if (c == 0xFFFFFF) return "WHITE";
        if (c == 0x111111) return "DARK_GRAY";
        if (c == 0x0B1B3A) return "BLUE";
        return "BLACK";
    }

    private static int bgColorRgb(String name) {
        return switch (name) {
            case "WHITE" -> 0xFFFFFF;
            case "DARK_GRAY" -> 0x111111;
            case "BLUE" -> 0x0B1B3A;
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

    private static int snap(int v, int step) {
        if (step <= 1) return v;
        return Math.round(v / (float) step) * step;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
