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

    private enum DragElement { NONE, COMPASS, TITLE }
    private DragElement dragElement = DragElement.NONE;

    private boolean wasLDown = false;

    private int compassGrabDx;
    private int compassGrabDy;

    private double titleOffsetXf;
    private double titleOffsetYf;
    private double titleGrabDx;
    private double titleGrabDy;

    private ClickableWidget wTarget;

    private ClickableWidget wCompassScale;
    private ClickableWidget wCompassTransparency;
    private ClickableWidget wCompassBackground;
    private ClickableWidget wCompassBgColor;
    private ClickableWidget wCompassTextColor;
    private ClickableWidget wCompassShowLatitude;
    private ClickableWidget wCompassCompact;
    private ClickableWidget wCompassAttachHotbar;

    private ClickableWidget wTitleScale;

    private ClickableWidget wResetHud;

    private int sidebarHintY;

    public LatitudeHudStudioScreen(Screen parent) {
        super(Text.literal("HUD Studio"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearChildren();

        int panelX = 8;
        int panelY = 28;
        int panelW = sidebarWidth;
        int rowH = 20;
        int rowGap = 4;

        var cfg = CompassHudConfig.get();

        this.titleOffsetXf = LatitudeConfig.zoneEnterTitleOffsetX;
        this.titleOffsetYf = LatitudeConfig.zoneEnterTitleOffsetY;

        int y = panelY;

        this.wTarget = this.addDrawableChild(ButtonWidget.builder(targetLabel(), b -> {
                    this.target = switch (this.target) {
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

        this.wCompassScale = this.addDrawableChild(new FloatSlider(panelX, y, panelW, rowH, Text.literal("Scale"), 0.5f, 3.0f, cfg.scale, v -> cfg.scale = v));
        y += rowH + rowGap;

        this.wCompassTransparency = this.addDrawableChild(new IntSlider(panelX, y, panelW, rowH, Text.literal("Transparency"), 0, 255, cfg.backgroundAlpha, v -> cfg.backgroundAlpha = v));
        y += rowH + rowGap;

        this.wCompassBackground = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> cfg.showBackground)
                .values(true, false)
                .build(panelX, y, panelW, rowH, Text.literal("Background"), (btn, value) -> cfg.showBackground = value));
        y += rowH + rowGap;

        this.wCompassBgColor = this.addDrawableChild(CyclingButtonWidget.<String>builder(Text::literal, () -> bgColorName(cfg.backgroundRgb))
                .values("BLACK", "WHITE", "DARK_GRAY", "BLUE")
                .build(panelX, y, panelW, rowH, Text.literal("Background Color"), (btn, value) -> cfg.backgroundRgb = bgColorRgb(value)));
        y += rowH + rowGap;

        this.wCompassTextColor = this.addDrawableChild(CyclingButtonWidget.<String>builder(Text::literal, () -> textColorName(cfg.textRgb))
                .values("WHITE", "BLACK", "YELLOW", "RED", "CYAN")
                .build(panelX, y, panelW, rowH, Text.literal("Text Color"), (btn, value) -> cfg.textRgb = textColorRgb(value)));
        y += rowH + rowGap;

        this.wCompassShowLatitude = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> Boolean.TRUE.equals(cfg.showLatitude))
                .values(true, false)
                .build(panelX, y, panelW, rowH, Text.literal("Show Latitude"), (btn, value) -> cfg.showLatitude = value));
        y += rowH + rowGap;

        this.wCompassCompact = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> cfg.compactHud)
                .values(true, false)
                .build(panelX, y, panelW, rowH, Text.literal("Compact HUD"), (btn, value) -> cfg.compactHud = value));
        y += rowH + rowGap;

        this.wCompassAttachHotbar = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> cfg.attachToHotbarCompass)
                .values(true, false)
                .build(panelX, y, panelW, rowH, Text.literal("Attach to Hotbar"), (btn, value) -> {
                    cfg.attachToHotbarCompass = value;
                    CompassHudConfig.saveCurrent();
                }));
        y += rowH + rowGap;

        this.wTitleScale = this.addDrawableChild(new StepSlider(panelX, y, panelW, rowH, Text.literal("Title Size"), 1.0, 3.0, 0.1, LatitudeConfig.zoneEnterTitleScale, v -> LatitudeConfig.zoneEnterTitleScale = v));

        int resetY = this.height - 52;
        this.wResetHud = this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset HUD"), b -> {
                    resetHudDefaults();
                    dragElement = DragElement.NONE;
                    this.init();
                })
                .dimensions(panelX, resetY, panelW, rowH)
                .build());

        int bw = 200;
        int bh = 20;
        int doneX = (this.width - bw) / 2;
        int doneY = this.height - 28;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> {
                    CompassHudConfig.saveCurrent();
                    LatitudeConfig.saveCurrent();
                    MinecraftClient.getInstance().setScreen(parent);
                })
                .dimensions(doneX, doneY, bw, bh)
                .build());

        updateSidebarVisibility();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderInGameBackground(ctx);
        ctx.fill(0, 0, this.width, this.height, 0x66000000);

        int sidebarX = 6;
        int sidebarY = 22;

        if (sidebarVisible) {
            int px = sidebarX;
            int py = sidebarY;
            int pw = sidebarWidth + 4;
            int ph = this.height - 44;
            ctx.fill(px, py, px + pw, py + ph, 0xAA000000);
        }

        var mc = MinecraftClient.getInstance();
        double z = 0.0;
        var border = mc.world != null ? mc.world.getWorldBorder() : null;
        if (mc.player != null) {
            z = mc.player.getZ();
        }

        String degText = (border != null) ? LatitudeMath.formatLatitudeDeg(z, border) : "0\u00b0";
        String sampleTitle = "EQUATOR " + degText;

        int titleOffsetX = (dragElement == DragElement.TITLE) ? (int) Math.round(titleOffsetXf) : LatitudeConfig.zoneEnterTitleOffsetX;
        int titleOffsetY = (dragElement == DragElement.TITLE) ? (int) Math.round(titleOffsetYf) : LatitudeConfig.zoneEnterTitleOffsetY;

        ZoneEnterTitleOverlay.renderStaticAt(
                ctx,
                this.width,
                this.height,
                sampleTitle,
                LatitudeConfig.zoneEnterTitleScale,
                titleOffsetX,
                titleOffsetY);

        CompassHud.renderAdjustPreview(ctx, this.width, this.height);

        super.render(ctx, mouseX, mouseY, delta);

        if (sidebarVisible) {
            ctx.drawTextWithShadow(this.textRenderer, "Press L to hide settings", sidebarX + 8, sidebarHintY, 0xFFFFFFFF);
        } else {
            ctx.drawTextWithShadow(this.textRenderer, "Press L to show settings", 8, 8, 0xFFFFFFFF);
        }
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
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) {
            return true;
        }

        double mx = click.x();
        double my = click.y();

        if (click.button() == 0) {
            if (LatitudeConfig.zoneEnterTitleDraggable && isMouseOverTitle(mx, my)) {
                dragElement = DragElement.TITLE;
                int cx = (this.width / 2) + LatitudeConfig.zoneEnterTitleOffsetX;
                int cy = (this.height / 2) + LatitudeConfig.zoneEnterTitleOffsetY;
                titleGrabDx = mx - (double) cx;
                titleGrabDy = my - (double) cy;
                titleOffsetXf = LatitudeConfig.zoneEnterTitleOffsetX;
                titleOffsetYf = LatitudeConfig.zoneEnterTitleOffsetY;
                return true;
            }

            if (isMouseOverCompass(mx, my)) {
                dragElement = DragElement.COMPASS;
                return true;
            }
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

        if (click.button() != 0) {
            return false;
        }

        if (dragElement == DragElement.TITLE) {
            double newCx = mx - titleGrabDx;
            double newCy = my - titleGrabDy;
            titleOffsetXf = newCx - (this.width / 2.0);
            titleOffsetYf = newCy - (this.height / 2.0);
            return true;
        }

        if (dragElement == DragElement.COMPASS) {
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
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            if (dragElement == DragElement.TITLE) {
                int x = (int) Math.round(titleOffsetXf);
                int y = (int) Math.round(titleOffsetYf);
                if (LatitudeConfig.hudSnapEnabled) {
                    x = snap(x, LatitudeConfig.hudSnapPixels);
                    y = snap(y, LatitudeConfig.hudSnapPixels);
                }
                LatitudeConfig.zoneEnterTitleOffsetX = x;
                LatitudeConfig.zoneEnterTitleOffsetY = y;
                LatitudeConfig.saveCurrent();
            }
            if (dragElement == DragElement.COMPASS) {
                CompassHudConfig.saveCurrent();
            }
            dragElement = DragElement.NONE;
        }
        return super.mouseReleased(click);
    }

    private static void resetHudDefaults() {
        var cfg = CompassHudConfig.get();
        applyDefaults(cfg);
        CompassHudConfig.saveCurrent();

        LatitudeConfig.zoneEnterTitleScale = 1.8;
        LatitudeConfig.zoneEnterTitleOffsetX = 0;
        LatitudeConfig.zoneEnterTitleOffsetY = -40;
        LatitudeConfig.saveCurrent();
    }

    private void updateSidebarVisibility() {
        setVisible(wTarget, sidebarVisible);

        boolean showCompassControls = sidebarVisible && (target == Target.COMPASS || target == Target.BOTH);
        setVisible(wCompassScale, showCompassControls);
        setVisible(wCompassTransparency, showCompassControls);
        setVisible(wCompassBackground, showCompassControls);
        setVisible(wCompassBgColor, showCompassControls);
        setVisible(wCompassTextColor, showCompassControls);
        setVisible(wCompassShowLatitude, showCompassControls);
        setVisible(wCompassCompact, showCompassControls);
        setVisible(wCompassAttachHotbar, showCompassControls);

        boolean showTitleControls = sidebarVisible && (target == Target.TITLE || target == Target.BOTH);
        setVisible(wTitleScale, showTitleControls);

        setVisible(wResetHud, sidebarVisible);

        this.sidebarHintY = computeSidebarHintY();
    }

    private int computeSidebarHintY() {
        if (!sidebarVisible) {
            return 8;
        }

        int bottom = 0;
        bottom = Math.max(bottom, bottomYIfVisible(wTarget));
        bottom = Math.max(bottom, bottomYIfVisible(wCompassScale));
        bottom = Math.max(bottom, bottomYIfVisible(wCompassTransparency));
        bottom = Math.max(bottom, bottomYIfVisible(wCompassBackground));
        bottom = Math.max(bottom, bottomYIfVisible(wCompassBgColor));
        bottom = Math.max(bottom, bottomYIfVisible(wCompassTextColor));
        bottom = Math.max(bottom, bottomYIfVisible(wCompassShowLatitude));
        bottom = Math.max(bottom, bottomYIfVisible(wCompassCompact));
        bottom = Math.max(bottom, bottomYIfVisible(wCompassAttachHotbar));
        bottom = Math.max(bottom, bottomYIfVisible(wTitleScale));
        if (bottom <= 0) {
            return 8;
        }

        int hintY = bottom + 6;
        if (wResetHud != null && wResetHud.visible) {
            hintY = Math.min(hintY, wResetHud.getY() - 14);
        }
        hintY = Math.max(hintY, 22);
        hintY = Math.min(hintY, Math.max(0, this.height - 18));
        return hintY;
    }

    private static int bottomYIfVisible(ClickableWidget w) {
        if (w == null || !w.visible) return 0;
        return w.getY() + w.getHeight();
    }

    private static void applyDefaults(CompassHudConfig cfg) {
        cfg.enabled = true;
        cfg.showMode = CompassHudConfig.ShowMode.COMPASS_PRESENT;
        cfg.directionMode = CompassHudConfig.DirectionMode.CARDINAL_8;
        cfg.hAnchor = CompassHudConfig.HAnchor.CENTER;
        cfg.vAnchor = CompassHudConfig.VAnchor.TOP;
        cfg.offsetX = 0;
        cfg.offsetY = 0;
        cfg.scale = 1.0f;
        cfg.padding = 3;
        cfg.showBackground = true;
        cfg.backgroundRgb = 0x000000;
        cfg.backgroundAlpha = 64;
        cfg.textRgb = 0xFFFFFF;
        cfg.textAlpha = 255;
        cfg.shadow = true;
        cfg.showLatitude = true;
        cfg.latitudeDecimals = 0;
        cfg.attachToHotbarCompass = false;
        cfg.compactHud = false;
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
        if (mx < b.x() || mx >= (b.x() + b.w()) || my < b.y() || my >= (b.y() + b.h())) {
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

        String s = "EQUATOR 0\u00b0";
        int w = mc.textRenderer.getWidth(s);
        int h = mc.textRenderer.fontHeight;

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

    private static int snap(int v, int step) {
        if (step <= 1) return v;
        return Math.round(v / (float) step) * step;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
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
