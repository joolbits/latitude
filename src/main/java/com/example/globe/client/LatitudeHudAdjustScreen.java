package com.example.globe.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class LatitudeHudAdjustScreen extends Screen {
    private final Screen parent;

    private enum Mode { TITLE, COMPASS, BOTH }
    private Mode mode = Mode.BOTH;

    private boolean draggingTitle = false;
    private boolean draggingCompass = false;
    private double lastMouseX;
    private double lastMouseY;

    private int compassGrabDx;
    private int compassGrabDy;

    private boolean showSettings = true;
    private AbstractWidget settingsScale;
    private AbstractWidget settingsTextColor;
    private AbstractWidget settingsBackgroundColor;
    private AbstractWidget settingsBackground;
    private AbstractWidget settingsTransparency;
    private AbstractWidget settingsShowLatitude;
    private AbstractWidget settingsShowLongitude;
    private AbstractWidget settingsCompactHud;

    private boolean wasLDown = false;

    public LatitudeHudAdjustScreen(Screen parent) {
        super(Component.literal("Adjust HUD Position"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int bw = 200;
        int bh = 20;
        int x = (this.width - bw) / 2;
        int y = this.height - 28;

        int panelX = 8;
        int panelY = 52;
        int panelW = 170;
        int rowH = 20;
        int rowGap = 4;

        var cfg = CompassHudConfig.get();

        int py = panelY;
        this.settingsScale = this.addRenderableWidget(new FloatSlider(panelX, py, panelW, rowH, Component.literal("Scale"), 0.5f, 3.0f, cfg.scale, v -> cfg.scale = v));
        py += rowH + rowGap;

        this.settingsTransparency = this.addRenderableWidget(new IntSlider(panelX, py, panelW, rowH, Component.literal("Transparency"), 0, 255, cfg.backgroundAlpha, v -> cfg.backgroundAlpha = v));
        py += rowH + rowGap;

        this.settingsBackground = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.showBackground)
                .withValues(true, false)
                .create(panelX, py, panelW, rowH, Component.literal("Background"), (btn, value) -> cfg.showBackground = value));
        py += rowH + rowGap;

        this.settingsShowLatitude = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> Boolean.TRUE.equals(cfg.showLatitude))
                .withValues(true, false)
                .create(panelX, py, panelW, rowH, Component.literal("Show Latitude"), (btn, value) -> cfg.showLatitude = value));
        py += rowH + rowGap;

        this.settingsShowLongitude = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> Boolean.TRUE.equals(cfg.showLongitude))
                .withValues(true, false)
                .create(panelX, py, panelW, rowH, Component.literal("Show Longitude"), (btn, value) -> cfg.showLongitude = value));
        py += rowH + rowGap;

        this.settingsCompactHud = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.compactHud)
                .withValues(true, false)
                .create(panelX, py, panelW, rowH, Component.literal("Compact HUD"), (btn, value) -> cfg.compactHud = value));
        py += rowH + rowGap;

        this.settingsBackgroundColor = this.addRenderableWidget(CycleButton.<String>builder(this::bgColorLabel, () -> bgColorName(cfg.backgroundRgb))
                .withValues("BLACK", "DARK_GRAY", "BLUE")
                .create(panelX, py, panelW, rowH, Component.literal("Background Color"), (btn, value) -> cfg.backgroundRgb = bgColorRgb(value)));
        py += rowH + rowGap;

        this.settingsTextColor = this.addRenderableWidget(CycleButton.<String>builder(this::textColorLabel, () -> textColorName(cfg.textRgb))
                .withValues("WHITE", "YELLOW", "RED", "CYAN")
                .create(panelX, py, panelW, rowH, Component.literal("Text Color"), (btn, value) -> cfg.textRgb = textColorRgb(value)));

        updateSettingsVisibility();

        this.addRenderableWidget(Button.builder(Component.literal("Adjust: BOTH"), btn -> {
                    mode = switch (mode) {
                        case BOTH -> Mode.TITLE;
                        case TITLE -> Mode.COMPASS;
                        case COMPASS -> Mode.BOTH;
                    };
                    btn.setMessage(Component.literal("Adjust: " + mode.name()));
                })
                .bounds(8, 28, 140, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
                    LatitudeConfig.saveCurrent();
                    CompassHudConfig.saveCurrent();
                    Minecraft.getInstance().setScreenAndShow(parent);
                })
                .bounds(x, y, bw, bh)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.extractTransparentBackground(ctx);
        ctx.fill(0, 0, this.width, this.height, 0x66000000);

        if (showSettings) {
            int px = 6;
            int py = 46;
            int pw = 174;
            int ph = 7 * 24 + 10;
            ctx.fill(px, py, px + pw, py + ph, 0xAA000000);
            ctx.text(this.font, "HUD Settings", px + 6, py + 6, 0xFFFFFFFF);
            ctx.text(this.font, "Press L to hide/show settings", px + 6, py + 18, 0xFFCCCCCC);
        }

        ctx.text(this.font,
                "Drag the zone title to reposition. Click Done when finished.",
                8,
                8,
                0xFFFFFF,
                true);

        var mc = Minecraft.getInstance();
        double z = 0.0;
        var border = mc.level != null ? mc.level.getWorldBorder() : null;
        if (mc.player != null) {
            z = mc.player.getZ();
        }

        String degText = (border != null) ? LatitudeMath.formatLatitudeDeg(z, border) : "0\u00b0";
        // Derive the zone word from the same latitude so it matches degText (was hardcoded "TROPICAL",
        // producing nonsense like "TROPICAL 60\u00b0S").
        String sampleTitle = ((border != null) ? com.example.globe.util.LatitudeMath.zoneKey(border, z).toUpperCase(java.util.Locale.ROOT) : "TROPICAL") + " " + degText;

        ZoneEnterTitleOverlay.renderStaticAt(
                ctx,
                this.width,
                this.height,
                sampleTitle,
                LatitudeConfig.zoneEnterTitleScale,
                LatitudeConfig.zoneEnterTitleOffsetX,
                LatitudeConfig.zoneEnterTitleOffsetY);

        if (mode == Mode.COMPASS || mode == Mode.BOTH) {
            CompassHud.renderAdjustPreview(ctx, this.width, this.height);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();
        var mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        boolean lDown = InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_L);
        if (lDown && !wasLDown) {
            showSettings = !showSettings;
            updateSettingsVisibility();
        }
        wasLDown = lDown;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) {
            return true;
        }

        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button == 0 && (mode == Mode.TITLE || mode == Mode.BOTH) && isMouseOverTitle(mouseX, mouseY)) {
            draggingTitle = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        if (button == 0 && (mode == Mode.COMPASS || mode == Mode.BOTH) && isMouseOverCompass(mouseX, mouseY)) {
            draggingCompass = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (super.mouseDragged(click, deltaX, deltaY)) {
            return true;
        }

        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (draggingTitle && button == 0) {
            LatitudeConfig.zoneEnterTitleOffsetX += (int) Math.round(mouseX - lastMouseX);
            LatitudeConfig.zoneEnterTitleOffsetY += (int) Math.round(mouseY - lastMouseY);
            lastMouseX = mouseX;
            lastMouseY = mouseY;

            if (LatitudeConfig.hudSnapEnabled) {
                LatitudeConfig.zoneEnterTitleOffsetX = snap(LatitudeConfig.zoneEnterTitleOffsetX, LatitudeConfig.hudSnapPixels);
                LatitudeConfig.zoneEnterTitleOffsetY = snap(LatitudeConfig.zoneEnterTitleOffsetY, LatitudeConfig.hudSnapPixels);
            }
            return true;
        }

        if (draggingCompass && button == 0) {
            var mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) {
                return true;
            }

            var cfg = CompassHudConfig.get();
            if (cfg.attachToHotbarCompass) {
                return true;
            }

            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();

            int targetX = (int) Math.round(mouseX) - compassGrabDx;
            int targetY = (int) Math.round(mouseY) - compassGrabDy;

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

            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    private void updateSettingsVisibility() {
        setVisible(settingsScale, showSettings);
        setVisible(settingsTextColor, showSettings);
        setVisible(settingsBackgroundColor, showSettings);
        setVisible(settingsBackground, showSettings);
        setVisible(settingsTransparency, showSettings);
        setVisible(settingsShowLatitude, showSettings);
        setVisible(settingsShowLongitude, showSettings);
        setVisible(settingsCompactHud, showSettings);
    }

    private static void setVisible(AbstractWidget w, boolean v) {
        if (w == null) return;
        w.visible = v;
        w.active = v;
    }

    private Component textColorLabel(String v) {
        return Component.literal(v);
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

    private Component bgColorLabel(String v) {
        return Component.literal(v);
    }

    private static String bgColorName(int rgb) {
        int c = rgb & 0xFFFFFF;
        if (c == 0x111111) return "DARK_GRAY";
        if (c == 0x0B1B3A) return "BLUE";
        return "BLACK";
    }

    private static int bgColorRgb(String name) {
        return switch (name) {
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

    private static final class IntSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final IntConsumer onChange;

        private IntSlider(int x, int y, int w, int h, Component label, int min, int max, int value, IntConsumer onChange) {
            super(x, y, w, h, label, 0.0);
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            setValue(value);
            updateMessage();
        }

        private void setValue(int v) {
            int clamped = Math.max(min, Math.min(max, v));
            this.value = (clamped - min) / (double) (max - min);
        }

        private int getIntValue() {
            return (int) Math.round(min + this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(this.getMessage().getString().split(":")[0] + ": " + getIntValue()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(getIntValue());
        }
    }

    private static final class FloatSlider extends AbstractSliderButton {
        private final float min;
        private final float max;
        private final FloatConsumer onChange;

        private FloatSlider(int x, int y, int w, int h, Component label, float min, float max, float value, FloatConsumer onChange) {
            super(x, y, w, h, label, 0.0);
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            setValue(value);
            updateMessage();
        }

        private void setValue(float v) {
            float clamped = Math.max(min, Math.min(max, v));
            this.value = (clamped - min) / (double) (max - min);
        }

        private float getFloatValue() {
            return (float) (min + this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            float v = getFloatValue();
            this.setMessage(Component.literal(this.getMessage().getString().split(":")[0] + ": " + String.format("%.2f", v)));
        }

        @Override
        protected void applyValue() {
            onChange.accept(getFloatValue());
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
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
        var mc = Minecraft.getInstance();
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

    private static int snap(int v, int step) {
        if (step <= 1) return v;
        return Math.round(v / (float) step) * step;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private boolean isMouseOverTitle(double mx, double my) {
        String s = "TROPICAL 0\u00b0";
        int w = this.font.width(s);
        int h = this.font.lineHeight;

        double scale = Mth.clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);

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
}
