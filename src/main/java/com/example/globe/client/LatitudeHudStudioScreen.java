package com.example.globe.client;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class LatitudeHudStudioScreen extends Screen {
    private static final String[] TAB_NAMES = {"Compass", "Title", "Settings"};
    private static final int TAB_COMPASS = 0;
    private static final int TAB_TITLE = 1;
    private static final int TAB_SETTINGS = 2;

    private final Screen parent;

    private boolean sidebarVisible = true;
    private int sidebarWidth = 180;
    private int activeTab = TAB_COMPASS;
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    private enum DragElement { NONE, COMPASS, TITLE, LOCATION_DETAIL }
    private DragElement dragElement = DragElement.NONE;

    private boolean wasLDown = false;

    private int compassGrabDx;
    private int compassGrabDy;
    private int locationDetailGrabDx;
    private int locationDetailGrabDy;

    private double titleOffsetXf;
    private double titleOffsetYf;
    private double titleGrabDx;
    private double titleGrabDy;

    private AbstractWidget wTabCompass;
    private AbstractWidget wTabTitle;
    private AbstractWidget wTabSettings;

    private AbstractWidget wCompassStyle;
    private AbstractWidget wCompassScale;
    private AbstractWidget wCompassAnalogSize;
    private AbstractWidget wCompassAnalogInnerAlpha;
    private AbstractWidget wCompassAnalogTheme;
    private AbstractWidget wCompassTransparency;
    private AbstractWidget wCompassBackground;
    private AbstractWidget wCompassBgColor;
    private AbstractWidget wCompassTextColor;
    private AbstractWidget wCompassShowLatitude;
    private AbstractWidget wCompassAnalogShowLatitude;
    private AbstractWidget wCompassCompact;
    private AbstractWidget wCompassAttachHotbar;
    private AbstractWidget wLocationDetail;
    private AbstractWidget wLocationFollow;

    private AbstractWidget wTitleScale;
    private AbstractWidget wZoneEnterTitle;
    private AbstractWidget wTitleDuration;
    private AbstractWidget wShowHud;
    private AbstractWidget wDisplayWhen;
    private AbstractWidget wWarningMessages;

    private AbstractWidget wResetHud;

    private int sidebarScrollY = 0;
    private int sidebarViewportTop;
    private int sidebarViewportBottom;
    private int sidebarContentHeight;
    private final List<AbstractWidget> sidebarScrollWidgets = new ArrayList<>();
    private final List<Integer> sidebarScrollBaseYs = new ArrayList<>();
    private int sidebarWidgetW;
    private int sidebarBgY;

    public LatitudeHudStudioScreen(Screen parent) {
        super(Component.literal("HUD Studio"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int hintLaneH = 28;
        int panelX = 8;
        int panelY = hintLaneH + 8;
        int panelW = sidebarWidth;
        int scrollGutter = 7;        // 2px gap + 3px bar + 2px right pad
        int widgetW = panelW - scrollGutter;
        this.sidebarWidgetW = widgetW;
        this.sidebarBgY = hintLaneH + 2;
        int rowH = 20;
        int rowGap = 4;

        var cfg = CompassHudConfig.get();
        boolean analog = cfg.style == CompassHudConfig.CompassStyle.ANALOG;

        this.wTabCompass = null;
        this.wTabTitle = null;
        this.wTabSettings = null;
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
        this.wLocationDetail = null;
        this.wLocationFollow = null;
        this.wTitleScale = null;
        this.wZoneEnterTitle = null;
        this.wTitleDuration = null;
        this.wShowHud = null;
        this.wDisplayWhen = null;
        this.wWarningMessages = null;
        this.wResetHud = null;

        this.titleOffsetXf = LatitudeConfig.zoneEnterTitleOffsetX;
        this.titleOffsetYf = LatitudeConfig.zoneEnterTitleOffsetY;

        this.sidebarScrollWidgets.clear();
        this.sidebarScrollBaseYs.clear();
        this.sidebarViewportTop = panelY;
        this.sidebarViewportBottom = Math.max(panelY + 24, this.height - 60);

        int tabGap = 3;
        int tabW = (widgetW - tabGap * 2) / 3;
        this.wTabCompass = this.addRenderableWidget(Button.builder(
                        Component.literal(TAB_NAMES[TAB_COMPASS]),
                        b -> switchTab(TAB_COMPASS))
                .bounds(panelX, 4, tabW, rowH)
                .build());
        this.wTabTitle = this.addRenderableWidget(Button.builder(
                        Component.literal(TAB_NAMES[TAB_TITLE]),
                        b -> switchTab(TAB_TITLE))
                .bounds(panelX + tabW + tabGap, 4, tabW, rowH)
                .build());
        this.wTabSettings = this.addRenderableWidget(Button.builder(
                        Component.literal(TAB_NAMES[TAB_SETTINGS]),
                        b -> switchTab(TAB_SETTINGS))
                .bounds(panelX + (tabW + tabGap) * 2, 4, widgetW - tabW * 2 - tabGap * 2, rowH)
                .build());

        int y = panelY;

        if (activeTab == TAB_COMPASS) {

        this.wCompassStyle = this.addRenderableWidget(CycleButton.<CompassHudConfig.CompassStyle>builder(v -> Component.literal(v == CompassHudConfig.CompassStyle.ANALOG ? "Analog" : "Digital"), () -> cfg.style)
                .withValues(CompassHudConfig.CompassStyle.values())
                .create(panelX, y, widgetW, rowH, Component.literal("Compass Style"), (btn, value) -> {
                    cfg.style = value;
                    CompassHudConfig.saveCurrent();
                    this.init();
                }));
        tooltip(this.wCompassStyle, "Switch between the digital bar and the analog round compass.");
        trackSidebarWidget(this.wCompassStyle, y);
        y += rowH + rowGap;

        if (analog) {
            this.wCompassAnalogSize = this.addRenderableWidget(new IntSlider(
                    panelX,
                    y,
                    widgetW,
                    rowH,
                    Component.literal("Compass Size"),
                    CompassHudConfig.ANALOG_SIZE_STUDIO_MIN,
                    CompassHudConfig.ANALOG_SIZE_STUDIO_MAX,
                    Math.round(cfg.analogSize),
                    " px",
                    v -> cfg.analogSize = v));
            tooltip(
                    this.wCompassAnalogSize,
                    "Sets the analog compass diameter. Older saved sizes stay unchanged until you adjust this control.");
            trackSidebarWidget(this.wCompassAnalogSize, y);
            y += rowH + rowGap;
            this.wCompassAnalogInnerAlpha = this.addRenderableWidget(new FloatSlider(panelX, y, widgetW, rowH, Component.literal("Face Opacity"), 0.0f, 1.0f, cfg.analogInnerAlpha, true, v -> cfg.analogInnerAlpha = v));
            tooltip(this.wCompassAnalogInnerAlpha, "Controls how solid the compass face is. Lower values show more of the world behind it.");
            trackSidebarWidget(this.wCompassAnalogInnerAlpha, y);
            y += rowH + rowGap;
            this.wCompassAnalogTheme = this.addRenderableWidget(CycleButton.<CompassHudConfig.AnalogCompassTheme>builder(v -> Component.literal(themeLabel(v)), () -> cfg.analogTheme)
                    .withValues(CompassHudConfig.AnalogCompassTheme.values())
                    .create(panelX, y, widgetW, rowH, Component.literal("Color Scheme"), (btn, value) -> {
                        cfg.analogTheme = value;
                        CompassHudConfig.saveCurrent();
                    }));
            tooltip(this.wCompassAnalogTheme, "Pick a preset color scheme for the analog compass.");
            trackSidebarWidget(this.wCompassAnalogTheme, y);
            y += rowH + rowGap;
        } else {
            this.wCompassScale = this.addRenderableWidget(new FloatSlider(panelX, y, widgetW, rowH, Component.literal("Scale"), 0.5f, 3.0f, cfg.scale, v -> cfg.scale = v));
            tooltip(this.wCompassScale, "Changes the size of the digital compass text.");
            trackSidebarWidget(this.wCompassScale, y);
            y += rowH + rowGap;

            this.wCompassTransparency = this.addRenderableWidget(new IntSlider(panelX, y, widgetW, rowH, Component.literal("Transparency"), 0, 255, cfg.backgroundAlpha, v -> cfg.backgroundAlpha = v));
            tooltip(this.wCompassTransparency, "Adjusts the opacity of the digital compass background bar.");
            trackSidebarWidget(this.wCompassTransparency, y);
            y += rowH + rowGap;

            this.wCompassBackground = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.showBackground)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Background"), (btn, value) -> cfg.showBackground = value));
            tooltip(this.wCompassBackground, "Toggles the digital compass background box.");
            trackSidebarWidget(this.wCompassBackground, y);
            y += rowH + rowGap;

            this.wCompassBgColor = this.addRenderableWidget(CycleButton.<String>builder(Component::literal, () -> bgColorName(cfg.backgroundRgb))
                    .withValues("BLACK", "WHITE", "DARK_GRAY", "BLUE")
                    .create(panelX, y, widgetW, rowH, Component.literal("Background Color"), (btn, value) -> cfg.backgroundRgb = bgColorRgb(value)));
            tooltip(this.wCompassBgColor, "Selects the background color for the digital compass.");
            trackSidebarWidget(this.wCompassBgColor, y);
            y += rowH + rowGap;
        }

        this.wCompassTextColor = this.addRenderableWidget(CycleButton.<String>builder(Component::literal, () -> textColorName(cfg.textRgb))
                .withValues("WHITE", "BLACK", "YELLOW", "RED", "CYAN")
                .create(panelX, y, widgetW, rowH, Component.literal("Text Color"), (btn, value) -> cfg.textRgb = textColorRgb(value)));
        tooltip(this.wCompassTextColor, "Selects the text color used for the compass and labels.");
        trackSidebarWidget(this.wCompassTextColor, y);
        y += rowH + rowGap;

        if (analog) {
            this.wCompassAnalogShowLatitude = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> Boolean.TRUE.equals(cfg.analogShowLatitude))
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Analog Latitude"), (btn, value) -> cfg.analogShowLatitude = value));
            tooltip(this.wCompassAnalogShowLatitude, "Shows latitude next to the analog compass.");
            trackSidebarWidget(this.wCompassAnalogShowLatitude, y);
            y += rowH + rowGap;
        } else {
            this.wCompassShowLatitude = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> Boolean.TRUE.equals(cfg.showLatitude))
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Show Latitude"), (btn, value) -> cfg.showLatitude = value));
            tooltip(this.wCompassShowLatitude, "Shows latitude inside the digital compass line.");
            trackSidebarWidget(this.wCompassShowLatitude, y);
            y += rowH + rowGap;

            this.wCompassCompact = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.compactHud)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Compact HUD"), (btn, value) -> cfg.compactHud = value));
            tooltip(this.wCompassCompact, "Uses a tighter layout with minimal spacing.");
            trackSidebarWidget(this.wCompassCompact, y);
            y += rowH + rowGap;
        }

        this.wCompassAttachHotbar = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.attachToHotbarCompass)
                .withValues(true, false)
                .create(panelX, y, widgetW, rowH, Component.literal("Attach to Hotbar"), (btn, value) -> {
                    cfg.attachToHotbarCompass = value;
                    CompassHudConfig.saveCurrent();
                }));
        tooltip(this.wCompassAttachHotbar, "Snaps the digital compass to the hotbar. Analog ignores this for now.");
        trackSidebarWidget(this.wCompassAttachHotbar, y);
        y += rowH + rowGap;

        this.wLocationDetail = this.addRenderableWidget(CycleButton.<LocationDetailPolicy.Mode>builder(
                        value -> Component.literal(value.label()),
                        cfg::locationDetailMode)
                .withValues(LocationDetailPolicy.Mode.values())
                .create(panelX, y, widgetW, rowH, Component.literal("Location Detail"), (btn, value) -> {
                    cfg.setLocationDetailMode(value);
                    CompassHudConfig.saveCurrent();
                    updateSidebarVisibility();
                }));
        tooltip(
                this.wLocationDetail,
                "Shows the current biome, latitude zone, both together, or neither beside the compass.");
        trackSidebarWidget(this.wLocationDetail, y);
        y += rowH + rowGap;

        this.wLocationFollow = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "FOLLOW" : "DETACH"), () -> cfg.zoneFollowsCompass)
                .withValues(true, false)
                .create(panelX, y, widgetW, rowH, Component.literal("Location Placement"), (btn, value) -> {
                    cfg.zoneFollowsCompass = value;
                    CompassHudConfig.saveCurrent();
                    updateSidebarVisibility();
                }));
        tooltip(
                this.wLocationFollow,
                "Let the selected location detail ride with the compass or detach the whole unit for dragging.");
        trackSidebarWidget(this.wLocationFollow, y);
        y += rowH + rowGap;

        } else if (activeTab == TAB_TITLE) {
            this.wZoneEnterTitle = this.addRenderableWidget(CycleButton.<Boolean>builder(
                            v -> Component.literal(v ? "ON" : "OFF"),
                            () -> LatitudeConfig.zoneEnterTitleEnabled)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Zone Enter Title"), (btn, value) -> {
                        LatitudeConfig.zoneEnterTitleEnabled = value;
                        LatitudeConfig.saveCurrent();
                    }));
            tooltip(this.wZoneEnterTitle, "Show a short title when you enter a new latitude zone.");
            trackSidebarWidget(this.wZoneEnterTitle, y);
            y += rowH + rowGap;

            this.wTitleDuration = this.addRenderableWidget(new StepSlider(
                    panelX,
                    y,
                    widgetW,
                    rowH,
                    Component.literal("Title Duration (seconds)"),
                    2.0,
                    10.0,
                    0.5,
                    LatitudeConfig.zoneEnterTitleSeconds,
                    v -> LatitudeConfig.zoneEnterTitleSeconds = v));
            tooltip(this.wTitleDuration, "Set how long zone-entry titles remain on screen.");
            trackSidebarWidget(this.wTitleDuration, y);
            y += rowH + rowGap;

            this.wTitleScale = this.addRenderableWidget(new StepSlider(
                    panelX,
                    y,
                    widgetW,
                    rowH,
                    Component.literal("Title Size"),
                    1.0,
                    3.0,
                    0.1,
                    LatitudeConfig.zoneEnterTitleScale,
                    v -> LatitudeConfig.zoneEnterTitleScale = v));
            tooltip(this.wTitleScale, "Scales the zone enter title preview.");
            trackSidebarWidget(this.wTitleScale, y);
        } else {
            this.wShowHud = this.addRenderableWidget(CycleButton.<Boolean>builder(
                            v -> Component.literal(v ? "ON" : "OFF"),
                            () -> cfg.enabled)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Show HUD"), (btn, value) -> {
                        cfg.enabled = value;
                        CompassHudConfig.saveCurrent();
                    }));
            tooltip(this.wShowHud, "Turn the Latitude HUD on or off.");
            trackSidebarWidget(this.wShowHud, y);
            y += rowH + rowGap;

            this.wDisplayWhen = this.addRenderableWidget(CycleButton.<CompassHudConfig.ShowMode>builder(
                            LatitudeHudStudioScreen::showModeLabel,
                            () -> cfg.showMode)
                    .withValues(CompassHudConfig.ShowMode.values())
                    .create(panelX, y, widgetW, rowH, Component.literal("Display When"), (btn, value) -> {
                        cfg.showMode = value;
                        CompassHudConfig.saveCurrent();
                    }));
            tooltip(this.wDisplayWhen, "Choose when the Latitude HUD is visible.");
            trackSidebarWidget(this.wDisplayWhen, y);
            y += rowH + rowGap;

            this.wWarningMessages = this.addRenderableWidget(CycleButton.<Boolean>builder(
                            v -> Component.literal(v ? "ON" : "OFF"),
                            () -> LatitudeConfig.showWarningMessages)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Warning Messages"), (btn, value) -> {
                        LatitudeConfig.showWarningMessages = value;
                        LatitudeConfig.saveCurrent();
                    }));
            tooltip(this.wWarningMessages, "Show warning text near dangerous latitude boundaries.");
            trackSidebarWidget(this.wWarningMessages, y);
        }
        this.sidebarContentHeight = y + rowH - panelY;

        int resetY = this.height - 52;
        this.wResetHud = this.addRenderableWidget(Button.builder(Component.literal("Reset All HUD"), b -> {
                    resetHudDefaults();
                    dragElement = DragElement.NONE;
                    this.init();
                })
                .bounds(panelX, resetY, widgetW, rowH)
                .build());
        tooltip(this.wResetHud, "Restore the visible HUD, title, and warning settings to defaults.");

        int bw = 200;
        int bh = 20;
        int doneX = (this.width - bw) / 2;
        int doneY = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> this.onClose())
                .bounds(doneX, doneY, bw, bh)
                .build());

        updateSidebarVisibility();
    }

    @Override
    public void onClose() {
        CompassHudConfig.saveCurrent();
        LatitudeConfig.saveCurrent();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.extractTransparentBackground(ctx);
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

        var mc = Minecraft.getInstance();
        double z = 0.0;
        var border = mc.level != null ? mc.level.getWorldBorder() : null;
        if (mc.player != null) {
            z = mc.player.getZ();
        }

        // Derive BOTH the degree text and the zone word from the same latitude (Z) radius so the preview
        // is always climatically consistent. When there is no level (main-menu preview), use a sane sample
        // whose zone word matches its latitude rather than a hardcoded "TROPICAL <realDeg>" mismatch.
        String sampleTitle;
        if (border != null) {
            String degText = LatitudeMath.formatLatitudeDeg(z, border);
            String zoneWord = zoneTitleWord(com.example.globe.util.LatitudeMath.zoneKey(border, z));
            sampleTitle = zoneWord + " " + degText;
        } else {
            sampleTitle = "TROPICS 12\u00b0S";
        }

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
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        drawActiveTabUnderline(ctx);

        if (sidebarVisible) {
            ctx.text(this.font, "Press L to hide panel", sidebarWidth + 18, 8, 0xAAFFFFFF);
        } else {
            ctx.text(this.font, "Press L to show panel", 8, 8, 0xFFFFFFFF);
        }
    }

    private void drawActiveTabUnderline(GuiGraphicsExtractor ctx) {
        if (!sidebarVisible) {
            return;
        }
        int panelX = 8;
        int widgetW = sidebarWidth - 7;
        int tabGap = 3;
        int tabW = (widgetW - tabGap * 2) / 3;
        int tabX = panelX + activeTab * (tabW + tabGap);
        int activeWidth = activeTab == TAB_SETTINGS
                ? widgetW - tabW * 2 - tabGap * 2
                : tabW;
        ctx.fill(tabX + 3, 22, tabX + activeWidth - 3, 24, 0xFFD4A74A);
    }

    @Override
    public void tick() {
        super.tick();
        var mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        boolean lDown = InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_L);
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
            sidebarScrollY = Mth.clamp(sidebarScrollY, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) {
            return true;
        }

        if (activeTab == TAB_SETTINGS) {
            return false;
        }

        double mx = click.x();
        double my = click.y();

        if (click.button() == 0) {
            if (activeTab == TAB_TITLE
                    && LatitudeConfig.zoneEnterTitleDraggable
                    && isMouseOverTitle(mx, my)) {
                dragElement = DragElement.TITLE;
                int cx = (this.width / 2) + LatitudeConfig.zoneEnterTitleOffsetX;
                int cy = (this.height / 2) + LatitudeConfig.zoneEnterTitleOffsetY;
                titleGrabDx = mx - (double) cx;
                titleGrabDy = my - (double) cy;
                titleOffsetXf = LatitudeConfig.zoneEnterTitleOffsetX;
                titleOffsetYf = LatitudeConfig.zoneEnterTitleOffsetY;
                return true;
            }

            if (activeTab == TAB_COMPASS && isMouseOverCompass(mx, my)) {
                dragElement = DragElement.COMPASS;
                return true;
            }

            if (activeTab == TAB_COMPASS && isMouseOverLocationDetail(mx, my)) {
                dragElement = DragElement.LOCATION_DETAIL;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
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

        if (dragElement == DragElement.LOCATION_DETAIL) {
            var mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return true;
            var cfg = CompassHudConfig.get();
            if (!cfg.hasLocationDetail() || cfg.zoneFollowsCompass) return true;

            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            var detailBounds = CompassHud.computeLocationDetailBounds(mc, cfg);
            if (detailBounds == null) return true;

            int targetX = (int) Math.round(mx) - locationDetailGrabDx;
            int targetY = (int) Math.round(my) - locationDetailGrabDy;
            int boxW = detailBounds.w();
            int boxH = detailBounds.h();
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
    public boolean mouseReleased(MouseButtonEvent click) {
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
            if (dragElement == DragElement.LOCATION_DETAIL) {
                CompassHudConfig.saveCurrent();
            }
            dragElement = DragElement.NONE;
        }
        return super.mouseReleased(click);
    }

    private static void resetHudDefaults() {
        var cfg = CompassHudConfig.get();
        cfg.resetToDefaults();
        cfg.setLocationDetailMode(LocationDetailPolicy.DEFAULT_MODE);
        CompassHudConfig.saveCurrent();

        LatitudeConfig.zoneEnterTitleScale = 1.8;
        LatitudeConfig.zoneEnterTitleOffsetX = 0;
        LatitudeConfig.zoneEnterTitleOffsetY = -40;
        LatitudeConfig.zoneEnterTitleEnabled = true;
        LatitudeConfig.zoneEnterTitleSeconds = 6.0;
        LatitudeConfig.showWarningMessages = true;
        LatitudeConfig.saveCurrent();
    }

    private static Component showModeLabel(CompassHudConfig.ShowMode mode) {
        if (mode == null) {
            return Component.literal("When compass is in inventory");
        }
        return switch (mode) {
            case COMPASS_PRESENT -> Component.literal("When compass is in inventory");
            case HOLDING_COMPASS -> Component.literal("When holding compass");
            case ALWAYS -> Component.literal("Always");
        };
    }

    private void switchTab(int tab) {
        int next = Mth.clamp(tab, TAB_COMPASS, TAB_SETTINGS);
        if (activeTab == next) {
            return;
        }
        activeTab = next;
        sidebarScrollY = 0;
        dragElement = DragElement.NONE;
        this.setFocused(null);
        this.init();
    }

    public boolean faceOpacityAdjustActive() {
        if (wCompassAnalogInnerAlpha == null || !wCompassAnalogInnerAlpha.visible) {
            return false;
        }
        boolean dragging = wCompassAnalogInnerAlpha instanceof FloatSlider fs && fs.isDragging();
        return dragging || wCompassAnalogInnerAlpha.isMouseOver(lastMouseX, lastMouseY);
    }

    private void trackSidebarWidget(AbstractWidget w, int baseY) {
        sidebarScrollWidgets.add(w);
        sidebarScrollBaseYs.add(baseY);
    }

    private void applySidebarScroll() {
        if (!sidebarVisible) return;
        int viewportH = sidebarViewportBottom - sidebarViewportTop;
        int maxScroll = Math.max(0, sidebarContentHeight - viewportH);
        sidebarScrollY = Mth.clamp(sidebarScrollY, 0, maxScroll);
        for (int i = 0; i < sidebarScrollWidgets.size(); i++) {
            AbstractWidget w = sidebarScrollWidgets.get(i);
            if (w == null) continue;
            int baseY = sidebarScrollBaseYs.get(i);
            int drawY = baseY - sidebarScrollY;
            w.setY(drawY);
            boolean visible = isSidebarWidgetEligible(w)
                    && drawY >= sidebarViewportTop
                    && drawY + w.getHeight() <= sidebarViewportBottom;
            if (!visible && this.getFocused() == w) {
                this.setFocused(null);
            }
            w.visible = visible;
            w.active = visible;
        }
    }

    private boolean isSidebarWidgetEligible(AbstractWidget w) {
        if (!sidebarVisible) {
            return false;
        }
        var cfg = CompassHudConfig.get();
        if (w == wCompassAttachHotbar) {
            return cfg.style != CompassHudConfig.CompassStyle.ANALOG;
        }
        if (w == wLocationFollow) {
            return cfg.hasLocationDetail();
        }
        return true;
    }

    private void drawSidebarScrollbar(GuiGraphicsExtractor ctx) {
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
        setVisible(wTabCompass, sidebarVisible);
        setVisible(wTabTitle, sidebarVisible);
        setVisible(wTabSettings, sidebarVisible);

        boolean analog = CompassHudConfig.get().style == CompassHudConfig.CompassStyle.ANALOG;
        boolean showCompassControls = sidebarVisible && activeTab == TAB_COMPASS;
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
        setVisible(wLocationDetail, showCompassControls);
        setVisible(wLocationFollow, showCompassControls && CompassHudConfig.get().hasLocationDetail());

        boolean showTitleControls = sidebarVisible && activeTab == TAB_TITLE;
        setVisible(wZoneEnterTitle, showTitleControls);
        setVisible(wTitleDuration, showTitleControls);
        setVisible(wTitleScale, showTitleControls);

        boolean showSettingsControls = sidebarVisible && activeTab == TAB_SETTINGS;
        setVisible(wShowHud, showSettingsControls);
        setVisible(wDisplayWhen, showSettingsControls);
        setVisible(wWarningMessages, showSettingsControls);
        setVisible(wResetHud, sidebarVisible);
    }

    private boolean isMouseOverLocationDetail(double mx, double my) {
        var mc = Minecraft.getInstance();
        if (mc == null) return false;
        var cfg = CompassHudConfig.get();
        if (!cfg.hasLocationDetail() || cfg.zoneFollowsCompass) return false;
        var b = CompassHud.computeLocationDetailBounds(mc, cfg);
        if (b == null) return false;
        if (mx < b.x() || mx >= (b.x() + b.w()) || my < b.y() || my >= (b.y() + b.h())) return false;
        locationDetailGrabDx = (int) Math.round(mx) - b.x();
        locationDetailGrabDy = (int) Math.round(my) - b.y();
        return true;
    }

    private void setVisible(AbstractWidget w, boolean visible) {
        if (w == null) return;
        if (!visible && this.getFocused() == w) {
            this.setFocused(null);
        }
        w.visible = visible;
        w.active = visible;
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

    private boolean isMouseOverTitle(double mx, double my) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return false;
        }

        String s = "TROPICAL 0\u00b0";
        int w = mc.font.width(s);
        int h = mc.font.lineHeight;

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

    /** Uppercase zone title word, matching ZoneEntryNotifier's display names (so the preview reads like the real title). */
    private static String zoneTitleWord(String zoneKey) {
        String name = switch (zoneKey) {
            case "EQUATOR", "TROPICAL" -> "Tropics";
            case "SUBTROPICAL" -> "Subtropics";
            case "TEMPERATE" -> "Temperate";
            case "SUBPOLAR" -> "Subpolar";
            case "POLAR" -> "Polar";
            default -> zoneKey == null ? "Tropics" : zoneKey;
        };
        return name.toUpperCase(java.util.Locale.ROOT);
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
            case OBSIDIAN_RED -> "Obsidian & Red";
            case ARCTIC_BLUE -> "Arctic Blue";
            case EMERALD -> "Emerald";
            case ROYAL_PURPLE -> "Royal Purple";
            case SUNSET -> "Sunset";
            case MONOCHROME -> "Monochrome";
            case CLASSIC_GOLD -> "Classic Gold";
        };
    }

    private static void tooltip(AbstractWidget w, String text) {
        if (w != null) {
            w.setTooltip(Tooltip.create(Component.literal(text)));
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

    private static final class IntSlider extends AbstractSliderButton {
        private final Component label;
        private final int min;
        private final int max;
        private final String suffix;
        private final IntConsumer onChange;
        private Integer legacyDisplayValue;

        private IntSlider(int x, int y, int width, int height, Component label, int min, int max, int initial, IntConsumer onChange) {
            this(x, y, width, height, label, min, max, initial, "", onChange);
        }

        private IntSlider(int x, int y, int width, int height, Component label, int min, int max, int initial, String suffix, IntConsumer onChange) {
            super(x, y, width, height, Component.empty(), toNorm(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.suffix = suffix;
            this.onChange = onChange;
            this.legacyDisplayValue = initial > max ? initial : null;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int displayedValue = legacyDisplayValue != null ? legacyDisplayValue : getValue();
            this.setMessage(Component.literal(label.getString() + ": " + displayedValue + suffix));
        }

        @Override
        protected void applyValue() {
            legacyDisplayValue = null;
            onChange.accept(getValue());
            updateMessage();
        }

        private int getValue() {
            return Mth.clamp((int) Math.round(min + (max - min) * this.value), min, max);
        }

        private static double toNorm(int v, int min, int max) {
            if (max == min) return 0.0;
            return Mth.clamp((double) (v - min) / (double) (max - min), 0.0, 1.0);
        }
    }

    private static final class FloatSlider extends AbstractSliderButton {
        private final Component label;
        private final float min;
        private final float max;
        private final boolean percentage;
        private final FloatConsumer onChange;
        private boolean dragging;

        private FloatSlider(int x, int y, int width, int height, Component label, float min, float max, float initial, FloatConsumer onChange) {
            this(x, y, width, height, label, min, max, initial, false, onChange);
        }

        private FloatSlider(int x, int y, int width, int height, Component label, float min, float max, float initial, boolean percentage, FloatConsumer onChange) {
            super(x, y, width, height, Component.empty(), toNorm(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.percentage = percentage;
            this.onChange = onChange;
            updateMessage();
        }

        boolean isDragging() {
            return dragging;
        }

        @Override
        public void onClick(MouseButtonEvent click, boolean doubled) {
            dragging = true;
            super.onClick(click, doubled);
        }

        @Override
        public void onRelease(MouseButtonEvent click) {
            dragging = false;
            super.onRelease(click);
        }

        @Override
        protected void updateMessage() {
            String valueText = percentage
                    ? Math.round(getValue() * 100.0f) + "%"
                    : format(getValue());
            this.setMessage(Component.literal(label.getString() + ": " + valueText));
        }

        @Override
        protected void applyValue() {
            onChange.accept(getValue());
        }

        private float getValue() {
            float v = min + (max - min) * (float) this.value;
            return Mth.clamp(v, min, max);
        }

        private static double toNorm(float v, float min, float max) {
            if (max == min) return 0.0;
            return (v - min) / (max - min);
        }

        private static String format(float v) {
            return String.format(java.util.Locale.ROOT, "%.2f", v);
        }
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
