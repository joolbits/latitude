package com.example.globe.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
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

    private enum DragElement { NONE, COMPASS, TITLE, ZONE }
    private DragElement dragElement = DragElement.NONE;

    private boolean wasLDown = false;

    private int compassGrabDx;
    private int compassGrabDy;
    private int zoneGrabDx;
    private int zoneGrabDy;

    private double titleOffsetXf;
    private double titleOffsetYf;
    private double titleGrabDx;
    private double titleGrabDy;

    private ClickableWidget wTarget;

    private ClickableWidget wCompassStyle;
    private ClickableWidget wCompassScale;
    private ClickableWidget wCompassAnalogSize;
    private ClickableWidget wCompassAnalogInnerAlpha;
    private ClickableWidget wCompassAnalogTheme;
    private ClickableWidget wCompassTransparency;
    private ClickableWidget wCompassBackground;
    private ClickableWidget wCompassBgColor;
    private ClickableWidget wCompassTextColor;
    private ClickableWidget wCompassShowLatitude;
    private ClickableWidget wCompassAnalogShowLatitude;
    private ClickableWidget wCompassCompact;
    private ClickableWidget wCompassAttachHotbar;
    private ClickableWidget wZoneDisplay;
    private ClickableWidget wZoneFollow;

    private ClickableWidget wTitleScale;

    private ClickableWidget wResetHud;

    private int sidebarScrollY = 0;
    private int sidebarViewportTop;
    private int sidebarViewportBottom;
    private int sidebarContentHeight;
    private final List<ClickableWidget> sidebarScrollWidgets = new ArrayList<>();
    private final List<Integer> sidebarScrollBaseYs = new ArrayList<>();
    private int sidebarWidgetW;
    private int sidebarBgY;

    public LatitudeHudStudioScreen(Screen parent) {
        super(Text.literal("HUD Studio"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearChildren();

        int hintLaneH = 20;         // 8px padding + ~9px font + 3px bottom margin
        int panelX = 8;
        int panelY = hintLaneH + 8;  // 28 — first widget top
        int panelW = sidebarWidth;
        int scrollGutter = 7;        // 2px gap + 3px bar + 2px right pad
        int widgetW = panelW - scrollGutter;
        this.sidebarWidgetW = widgetW;
        this.sidebarBgY = hintLaneH + 2; // 22 — sidebar bg rect top
        int rowH = 20;
        int rowGap = 4;

        var cfg = CompassHudConfig.get();
        boolean analog = cfg.style == CompassHudConfig.CompassStyle.ANALOG;

        this.wCompassStyle = null;
        this.wCompassScale = null;
        this.wCompassAnalogSize = null;
        this.wCompassAnalogInnerAlpha = null;
        this.wCompassAnalogTheme = null;
        this.wCompassTransparency = null;
        this.wCompassBackground = null;
        this.wCompassBgColor = null;
        this.wCompassTextColor = null;
        this.wCompassShowLatitude = null;
        this.wCompassAnalogShowLatitude = null;
        this.wCompassCompact = null;
        this.wCompassAttachHotbar = null;
        this.wZoneDisplay = null;
        this.wZoneFollow = null;

        this.titleOffsetXf = LatitudeConfig.zoneEnterTitleOffsetX;
        this.titleOffsetYf = LatitudeConfig.zoneEnterTitleOffsetY;

        this.sidebarScrollWidgets.clear();
        this.sidebarScrollBaseYs.clear();
        this.sidebarViewportTop = panelY;
        this.sidebarViewportBottom = Math.max(panelY + 24, this.height - 60);

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
                .dimensions(panelX, y, widgetW, rowH)
                .build());
        tooltip(this.wTarget, "Choose whether to adjust the compass, zone title, or both.");
        trackSidebarWidget(this.wTarget, y);
        y += rowH + rowGap;

        this.wCompassStyle = this.addDrawableChild(CyclingButtonWidget.<CompassHudConfig.CompassStyle>builder(v -> Text.literal(v == CompassHudConfig.CompassStyle.ANALOG ? "Analog" : "Digital"), () -> cfg.style)
                .values(CompassHudConfig.CompassStyle.values())
                .build(panelX, y, widgetW, rowH, Text.literal("Compass Style"), (btn, value) -> {
                    cfg.style = value;
                    CompassHudConfig.saveCurrent();
                    this.init();
                }));
        tooltip(this.wCompassStyle, "Switch between the digital bar and the analog round compass.");
        trackSidebarWidget(this.wCompassStyle, y);
        y += rowH + rowGap;

        if (analog) {
            this.wCompassAnalogSize = this.addDrawableChild(new FloatSlider(panelX, y, widgetW, rowH, Text.literal("Analog Size"), 32.0f, 128.0f, cfg.analogSize, v -> cfg.analogSize = v));
            tooltip(this.wCompassAnalogSize, "Sets the analog compass diameter.");
            trackSidebarWidget(this.wCompassAnalogSize, y);
            y += rowH + rowGap;
            this.wCompassAnalogInnerAlpha = this.addDrawableChild(new FloatSlider(panelX, y, widgetW, rowH, Text.literal("Inner Transparency"), 0.0f, 1.0f, cfg.analogInnerAlpha, v -> cfg.analogInnerAlpha = v));
            tooltip(this.wCompassAnalogInnerAlpha, "Controls how transparent the analog inner disc is.");
            trackSidebarWidget(this.wCompassAnalogInnerAlpha, y);
            y += rowH + rowGap;
            this.wCompassAnalogTheme = this.addDrawableChild(CyclingButtonWidget.<CompassHudConfig.AnalogCompassTheme>builder(v -> Text.literal(themeLabel(v)), () -> cfg.analogTheme)
                    .values(CompassHudConfig.AnalogCompassTheme.values())
                    .build(panelX, y, widgetW, rowH, Text.literal("Color Scheme"), (btn, value) -> {
                        cfg.analogTheme = value;
                        CompassHudConfig.saveCurrent();
                    }));
            tooltip(this.wCompassAnalogTheme, "Pick a preset color scheme for the analog compass.");
            trackSidebarWidget(this.wCompassAnalogTheme, y);
            y += rowH + rowGap;
        } else {
            this.wCompassScale = this.addDrawableChild(new FloatSlider(panelX, y, widgetW, rowH, Text.literal("Scale"), 0.5f, 3.0f, cfg.scale, v -> cfg.scale = v));
            tooltip(this.wCompassScale, "Changes the size of the digital compass text.");
            trackSidebarWidget(this.wCompassScale, y);
            y += rowH + rowGap;

            this.wCompassTransparency = this.addDrawableChild(new IntSlider(panelX, y, widgetW, rowH, Text.literal("Transparency"), 0, 255, cfg.backgroundAlpha, v -> cfg.backgroundAlpha = v));
            tooltip(this.wCompassTransparency, "Adjusts the opacity of the digital compass background bar.");
            trackSidebarWidget(this.wCompassTransparency, y);
            y += rowH + rowGap;

            this.wCompassBackground = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> cfg.showBackground)
                    .values(true, false)
                    .build(panelX, y, widgetW, rowH, Text.literal("Background"), (btn, value) -> cfg.showBackground = value));
            tooltip(this.wCompassBackground, "Toggles the digital compass background box.");
            trackSidebarWidget(this.wCompassBackground, y);
            y += rowH + rowGap;

            this.wCompassBgColor = this.addDrawableChild(CyclingButtonWidget.<String>builder(Text::literal, () -> bgColorName(cfg.backgroundRgb))
                    .values("BLACK", "WHITE", "DARK_GRAY", "BLUE")
                    .build(panelX, y, widgetW, rowH, Text.literal("Background Color"), (btn, value) -> cfg.backgroundRgb = bgColorRgb(value)));
            tooltip(this.wCompassBgColor, "Selects the background color for the digital compass.");
            trackSidebarWidget(this.wCompassBgColor, y);
            y += rowH + rowGap;
        }

        this.wCompassTextColor = this.addDrawableChild(CyclingButtonWidget.<String>builder(Text::literal, () -> textColorName(cfg.textRgb))
                .values("WHITE", "BLACK", "YELLOW", "RED", "CYAN")
                .build(panelX, y, widgetW, rowH, Text.literal("Text Color"), (btn, value) -> cfg.textRgb = textColorRgb(value)));
        tooltip(this.wCompassTextColor, "Selects the text color used for the compass and labels.");
        trackSidebarWidget(this.wCompassTextColor, y);
        y += rowH + rowGap;

        if (analog) {
            this.wCompassAnalogShowLatitude = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> Boolean.TRUE.equals(cfg.analogShowLatitude))
                    .values(true, false)
                    .build(panelX, y, widgetW, rowH, Text.literal("Analog Latitude"), (btn, value) -> cfg.analogShowLatitude = value));
            tooltip(this.wCompassAnalogShowLatitude, "Shows latitude next to the analog compass.");
            trackSidebarWidget(this.wCompassAnalogShowLatitude, y);
            y += rowH + rowGap;
        } else {
            this.wCompassShowLatitude = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> Boolean.TRUE.equals(cfg.showLatitude))
                    .values(true, false)
                    .build(panelX, y, widgetW, rowH, Text.literal("Show Latitude"), (btn, value) -> cfg.showLatitude = value));
            tooltip(this.wCompassShowLatitude, "Shows latitude inside the digital compass line.");
            trackSidebarWidget(this.wCompassShowLatitude, y);
            y += rowH + rowGap;

            this.wCompassCompact = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> cfg.compactHud)
                    .values(true, false)
                    .build(panelX, y, widgetW, rowH, Text.literal("Compact HUD"), (btn, value) -> cfg.compactHud = value));
            tooltip(this.wCompassCompact, "Uses a tighter layout with minimal spacing.");
            trackSidebarWidget(this.wCompassCompact, y);
            y += rowH + rowGap;
        }

        this.wCompassAttachHotbar = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> cfg.attachToHotbarCompass)
                .values(true, false)
                .build(panelX, y, widgetW, rowH, Text.literal("Attach to Hotbar"), (btn, value) -> {
                    cfg.attachToHotbarCompass = value;
                    CompassHudConfig.saveCurrent();
                }));
        tooltip(this.wCompassAttachHotbar, "Snaps the digital compass to the hotbar. Analog ignores this for now.");
        trackSidebarWidget(this.wCompassAttachHotbar, y);
        y += rowH + rowGap;

        this.wZoneDisplay = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "ON" : "OFF"), () -> cfg.displayZoneInHud)
                .values(true, false)
                .build(panelX, y, widgetW, rowH, Text.literal("Display Zone in HUD"), (btn, value) -> {
                    cfg.displayZoneInHud = value;
                    CompassHudConfig.saveCurrent();
                    updateSidebarVisibility();
                }));
        tooltip(this.wZoneDisplay, "Shows the current zone as small HUD text.");
        trackSidebarWidget(this.wZoneDisplay, y);
        y += rowH + rowGap;

        this.wZoneFollow = this.addDrawableChild(CyclingButtonWidget.<Boolean>builder(v -> Text.literal(v ? "FOLLOW" : "DETACH"), () -> cfg.zoneFollowsCompass)
                .values(true, false)
                .build(panelX, y, widgetW, rowH, Text.literal("Zone Placement"), (btn, value) -> {
                    cfg.zoneFollowsCompass = value;
                    CompassHudConfig.saveCurrent();
                    updateSidebarVisibility();
                }));
        tooltip(this.wZoneFollow, "Let the zone label ride with the compass or detach it for dragging.");
        trackSidebarWidget(this.wZoneFollow, y);
        y += rowH + rowGap;

        this.wTitleScale = this.addDrawableChild(new StepSlider(panelX, y, widgetW, rowH, Text.literal("Title Size"), 1.0, 3.0, 0.1, LatitudeConfig.zoneEnterTitleScale, v -> LatitudeConfig.zoneEnterTitleScale = v));
        tooltip(this.wTitleScale, "Scales the zone enter title preview.");
        trackSidebarWidget(this.wTitleScale, y);
        this.sidebarContentHeight = y + rowH - panelY;

        int resetY = this.height - 52;
        this.wResetHud = this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset HUD"), b -> {
                    resetHudDefaults();
                    dragElement = DragElement.NONE;
                    this.init();
                })
                .dimensions(panelX, resetY, widgetW, rowH)
                .build());
        tooltip(this.wResetHud, "Restore compass and zone HUD settings to defaults.");

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
        int sidebarY = this.sidebarBgY;

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

        String degText = (border != null) ? LatitudeMath.formatLatitudeDeg(z, border) : "0°";
        String sampleTitle = "TROPICAL " + degText;

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

        applySidebarScroll();
        drawSidebarScrollbar(ctx);
        super.render(ctx, mouseX, mouseY, delta);

        if (sidebarVisible) {
            ctx.drawTextWithShadow(this.textRenderer, "Press L to hide settings", 8, 8, 0xAAFFFFFF);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (sidebarVisible && mouseX < sidebarWidth + 10) {
            int viewportH = sidebarViewportBottom - sidebarViewportTop;
            int maxScroll = Math.max(0, sidebarContentHeight - viewportH);
            sidebarScrollY -= (int) Math.signum(verticalAmount) * 20;
            sidebarScrollY = MathHelper.clamp(sidebarScrollY, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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

            if (isMouseOverZone(mx, my)) {
                dragElement = DragElement.ZONE;
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

        if (dragElement == DragElement.ZONE) {
            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return true;
            var cfg = CompassHudConfig.get();
            if (!cfg.displayZoneInHud || cfg.zoneFollowsCompass) return true;

            int screenW = mc.getWindow().getScaledWidth();
            int screenH = mc.getWindow().getScaledHeight();
            var zb = CompassHud.computeZoneBounds(mc, cfg);
            if (zb == null) return true;

            int targetX = (int) Math.round(mx) - zoneGrabDx;
            int targetY = (int) Math.round(my) - zoneGrabDy;
            int boxW = zb.w();
            int boxH = zb.h();
            targetX = clamp(targetX, 0, Math.max(0, screenW - boxW));
            targetY = clamp(targetY, 0, Math.max(0, screenH - boxH));

            int baseX = anchoredZoneX(cfg, screenW, boxW);
            int baseY = anchoredZoneY(cfg, screenH, boxH);
            cfg.zoneOffsetX = targetX - baseX;
            cfg.zoneOffsetY = targetY - baseY;

            if (LatitudeConfig.hudSnapEnabled) {
                cfg.zoneOffsetX = snap(cfg.zoneOffsetX, LatitudeConfig.hudSnapPixels);
                cfg.zoneOffsetY = snap(cfg.zoneOffsetY, LatitudeConfig.hudSnapPixels);
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
            if (dragElement == DragElement.ZONE) {
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

    private void trackSidebarWidget(ClickableWidget w, int baseY) {
        sidebarScrollWidgets.add(w);
        sidebarScrollBaseYs.add(baseY);
    }

    private void applySidebarScroll() {
        if (!sidebarVisible) return;
        int viewportH = sidebarViewportBottom - sidebarViewportTop;
        int maxScroll = Math.max(0, sidebarContentHeight - viewportH);
        sidebarScrollY = MathHelper.clamp(sidebarScrollY, 0, maxScroll);
        for (int i = 0; i < sidebarScrollWidgets.size(); i++) {
            ClickableWidget w = sidebarScrollWidgets.get(i);
            if (w == null) continue;
            int baseY = sidebarScrollBaseYs.get(i);
            int drawY = baseY - sidebarScrollY;
            w.setY(drawY);
            w.visible = w.active && drawY >= sidebarViewportTop && drawY + w.getHeight() <= sidebarViewportBottom;
        }
    }

    private void drawSidebarScrollbar(DrawContext ctx) {
        if (!sidebarVisible) return;
        int viewportH = sidebarViewportBottom - sidebarViewportTop;
        int maxScroll = sidebarContentHeight - viewportH;
        if (maxScroll <= 0) return;
        int trackX = 8 + sidebarWidgetW + 2;  // panelX + widgetW + 2px gap
        int trackTop = sidebarViewportTop + 2;
        int trackBottom = sidebarViewportBottom - 2;
        int trackH = trackBottom - trackTop;
        if (trackH < 10) return;
        int thumbH = Math.max(8, trackH * viewportH / sidebarContentHeight);
        int thumbY = trackTop + (trackH - thumbH) * sidebarScrollY / maxScroll;
        ctx.fill(trackX, trackTop, trackX + 3, trackBottom, 0x55FFFFFF);
        ctx.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFFD4A74A);
    }

    private void updateSidebarVisibility() {
        setVisible(wTarget, sidebarVisible);

        boolean analog = CompassHudConfig.get().style == CompassHudConfig.CompassStyle.ANALOG;
        boolean showCompassControls = sidebarVisible && (target == Target.COMPASS || target == Target.BOTH);
        setVisible(wCompassStyle, showCompassControls);
        setVisible(wCompassScale, showCompassControls && !analog);
        setVisible(wCompassAnalogSize, showCompassControls && analog);
        setVisible(wCompassAnalogTheme, showCompassControls && analog);
        setVisible(wCompassAnalogInnerAlpha, showCompassControls && analog);
        setVisible(wCompassTransparency, showCompassControls && !analog);
        setVisible(wCompassBackground, showCompassControls && !analog);
        setVisible(wCompassBgColor, showCompassControls && !analog);
        setVisible(wCompassTextColor, showCompassControls);
        setVisible(wCompassShowLatitude, showCompassControls && !analog);
        setVisible(wCompassAnalogShowLatitude, showCompassControls && analog);
        setVisible(wCompassCompact, showCompassControls && !analog);
        setVisible(wCompassAttachHotbar, showCompassControls && !analog);
        setVisible(wZoneDisplay, showCompassControls);
        setVisible(wZoneFollow, showCompassControls && CompassHudConfig.get().displayZoneInHud);

        boolean showTitleControls = sidebarVisible && (target == Target.TITLE || target == Target.BOTH);
        setVisible(wTitleScale, showTitleControls);

        setVisible(wResetHud, sidebarVisible);
    }

    private boolean isMouseOverZone(double mx, double my) {
        var mc = MinecraftClient.getInstance();
        if (mc == null) return false;
        var cfg = CompassHudConfig.get();
        if (!cfg.displayZoneInHud || cfg.zoneFollowsCompass) return false;
        var b = CompassHud.computeZoneBounds(mc, cfg);
        if (b == null) return false;
        if (mx < b.x() || mx >= (b.x() + b.w()) || my < b.y() || my >= (b.y() + b.h())) return false;
        zoneGrabDx = (int) Math.round(mx) - b.x();
        zoneGrabDy = (int) Math.round(my) - b.y();
        return true;
    }

    private static void applyDefaults(CompassHudConfig cfg) {
        cfg.enabled = true;
        cfg.showMode = CompassHudConfig.ShowMode.COMPASS_PRESENT;
        cfg.directionMode = CompassHudConfig.DirectionMode.CARDINAL_8;
        cfg.style = CompassHudConfig.CompassStyle.DIGITAL;
        cfg.hAnchor = CompassHudConfig.HAnchor.CENTER;
        cfg.vAnchor = CompassHudConfig.VAnchor.TOP;
        cfg.offsetX = 0;
        cfg.offsetY = 0;
        cfg.scale = 1.0f;
        cfg.analogSize = 48.0f;
        cfg.analogInnerAlpha = 0.65f;
        cfg.analogTheme = CompassHudConfig.AnalogCompassTheme.CLASSIC_GOLD;
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

        String s = "TROPICAL 0°";
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

    private static int anchoredZoneX(CompassHudConfig cfg, int screenW, int boxW) {
        return switch (cfg.zoneHAnchor) {
            case LEFT -> 4;
            case CENTER -> (screenW - boxW) / 2;
            case RIGHT -> screenW - boxW - 4;
        };
    }

    private static int anchoredZoneY(CompassHudConfig cfg, int screenH, int boxH) {
        return switch (cfg.zoneVAnchor) {
            case TOP -> 4;
            case CENTER -> (screenH - boxH) / 2;
            case BOTTOM -> screenH - boxH - 4;
        };
    }

    private static String themeLabel(CompassHudConfig.AnalogCompassTheme theme) {
        return switch (theme) {
            case PALE_GOLD -> "Pale Gold";
            case RED_IVORY -> "Red & Ivory";
            case CYAN_STEEL -> "Cyan Steel";
            case MINT_BRASS -> "Mint Brass";
            case CLASSIC_GOLD -> "Classic Gold";
        };
    }

    private static void tooltip(ClickableWidget w, String text) {
        if (w != null) {
            w.setTooltip(Tooltip.of(Text.literal(text)));
        }
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
