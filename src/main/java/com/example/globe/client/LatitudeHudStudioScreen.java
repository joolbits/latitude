package com.example.globe.client;

import com.example.globe.core.config.LatitudeConfigData;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
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
    private static final int GOLD = 0xFFD4A74A;
    private static final int WARM_WHITE = 0xFFEDE0D0;
    private static final int MUTED = 0xFF8C8078;
    private static final int PANEL_BORDER = 0xFF5C4A3A;
    private static final int PANEL_BG = 0xFF3A302A;

    private static final String[] TAB_NAMES = {"Compass", "Labels", "Title", "Presets", "General"};
    private static final int TAB_COMPASS = 0;
    private static final int TAB_LABELS = 1;
    private static final int TAB_TITLE = 2;
    private static final int TAB_PRESETS = 3;
    private static final int TAB_GENERAL = 4;
    private static final int TAB_H = 20;
    private static final int TAB_GAP = 4;
    // Extra headroom on top of the wider sidebar so even "Placement" sits comfortably clear of its borders.
    private static final float TAB_LABEL_SCALE = 0.85f;

    private final Screen parent;

    // Cancel snapshot (UI round 13): the ENTIRE editable state as it was when the Studio opened. Every widget
    // in this screen mutates the live config singletons AND saves to disk immediately (live preview), so a
    // "Cancel" has to restore these captured values over the live singletons and re-save. Captured once in the
    // constructor -- init() re-runs on every resize/tab-switch/widget-rebuild, so capturing there would snapshot
    // already-edited state and defeat the whole point. Done keeps the live edits (they're already persisted);
    // ESC keeps its existing behavior (also keeps edits) -- only this explicit Cancel discards.
    private final CompassHudConfig hudSnapshot;
    private final LatitudeConfigData latitudeSnapshot;

    private boolean sidebarVisible = true;
    // Widened from 180 -- at 180 the 4-tab strip (~43px/tab) was too narrow for "Placement" to fit without
    // touching its button's borders.
    private int sidebarWidth = 208;

    private int activeTab = TAB_COMPASS;
    private int tabStripY;

    private enum DragElement { NONE, COMPASS, TITLE, ZONE, BIOME, COORDS }
    private DragElement dragElement = DragElement.NONE;

    private boolean wasLDown = false;

    private int compassGrabDx;
    private int compassGrabDy;
    private int zoneGrabDx;
    private int zoneGrabDy;
    private int biomeGrabDx;
    private int biomeGrabDy;
    private int coordsGrabDx;
    private int coordsGrabDy;

    private double titleOffsetXf;
    private double titleOffsetYf;
    private double titleGrabDx;
    private double titleGrabDy;

    private AbstractWidget wCompassStyle;
    private AbstractWidget wCompassScale;
    private AbstractWidget wCompassAnalogSize;
    private AbstractWidget wCompassAnalogInnerAlpha;
    private AbstractWidget wCompassAnalogTheme;
    private AbstractWidget wCompassRainbowSpeed;
    private AbstractWidget wCompassTransparency;
    private AbstractWidget wCompassBackground;
    private AbstractWidget wCompassBgColor;
    private AbstractWidget wCompassTextColor;
    private AbstractWidget wCompassTextAlpha;
    private AbstractWidget wCompassTextRainbow;
    private AbstractWidget wCompassShowLatitude;
    private AbstractWidget wCompassAnalogShowLatitude;
    private AbstractWidget wCompassShowLongitude;
    private AbstractWidget wCompassAnalogShowLongitude;
    private AbstractWidget wCompassCompact;
    private AbstractWidget wCompassAttachHotbar;
    private AbstractWidget wZoneDisplay;
    private AbstractWidget wZoneFollow;
    private AbstractWidget wZoneTextScale;
    private AbstractWidget wBiomeDisplay;
    private AbstractWidget wBiomeFollow;
    private AbstractWidget wBiomeTextScale;
    private AbstractWidget wZoneBiomeOrder;
    private AbstractWidget wCoordsFollow;
    private AbstractWidget wCoordsTextScale;
    private AbstractWidget wHudSnap;
    private AbstractWidget wHudSnapPixels;
    private AbstractWidget wTitleDraggable;

    private AbstractWidget wTitleScale;
    private AbstractWidget wTitleEnabled;
    private AbstractWidget wTitleDuration;
    private AbstractWidget wTitleShowBaseDegrees;
    private AbstractWidget wTitleColorPreset;
    private AbstractWidget wTitleCase;
    private AbstractWidget wTitleLetterSpacing;

    private AbstractWidget wResetHud;
    // Presets-tab undo/redo: empty-labelled buttons; the arrow glyph is drawn scaled on top (the vanilla
    // button message renders the unicode arrow tiny — Peetsa: "comically small").
    private AbstractWidget wUndoLoad;
    private AbstractWidget wRedoLoad;

    // RGB picker groups (Custom analog theme colors + text color + title color). Constructed only when relevant
    // to the active tab/style/theme, mirroring how digital-vs-analog widgets are already conditionally
    // constructed.
    private RgbPickerGroup rgbTextColor;
    private RgbPickerGroup rgbCustomFace;
    private RgbPickerGroup rgbCustomRing;
    private RgbPickerGroup rgbCustomMuted;
    private RgbPickerGroup rgbCustomNeedle;
    private RgbPickerGroup rgbTitleColor;
    private SwatchSlot swatchTextColor;
    private SwatchSlot swatchCustomFace;
    private SwatchSlot swatchCustomRing;
    private SwatchSlot swatchCustomMuted;
    private SwatchSlot swatchCustomNeedle;
    private SwatchSlot swatchTitleColor;
    private final List<SwatchSlot> sidebarSwatches = new ArrayList<>();

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
        // Snapshot the full editable state up front (see field docs) so Cancel can restore it exactly.
        this.hudSnapshot = CompassHudConfig.copyOf(CompassHudConfig.get());
        this.latitudeSnapshot = LatitudeConfig.snapshot();
    }

    /** Cancel: roll back every live-preview mutation (config values, element positions, title fields, snap
     *  settings) to the constructor snapshot, persist the rollback over the live-saved edits, and close to the
     *  parent screen. */
    private void cancelAndClose() {
        CompassHudConfig.get().copyFrom(hudSnapshot);
        CompassHudConfig.saveCurrent();
        LatitudeConfig.restore(latitudeSnapshot);
        LatitudeConfig.saveCurrent();
        CompassHudPreset.clearUndoRedo();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void init() {
        CompassDialRenderer.invalidateTextures(); // pack may have been toggled since last check

        this.clearWidgets();

        int hintLaneH = 20;         // 8px padding + ~9px font + 3px bottom margin
        int panelX = 8;
        int tabStripYLocal = hintLaneH + 2;              // 22 — tab strip top
        int cardTop = tabStripYLocal + TAB_H + 4;        // 46 — themed card top
        int headingH = this.font.lineHeight + 8;          // heading text + breathing room
        int panelY = cardTop + headingH + 4;              // first widget top
        int panelW = sidebarWidth;
        int scrollGutter = 7;        // 2px gap + 3px bar + 2px right pad
        int widgetW = panelW - scrollGutter;
        this.sidebarWidgetW = widgetW;
        this.sidebarBgY = cardTop;
        this.tabStripY = tabStripYLocal;
        int rowH = 20;
        int rowGap = 4;

        var cfg = CompassHudConfig.get();
        boolean analog = cfg.style == CompassHudConfig.CompassStyle.ANALOG;
        boolean customTheme = cfg.analogTheme == CompassHudConfig.AnalogCompassTheme.CUSTOM;
        boolean rainbowTheme = cfg.analogTheme == CompassHudConfig.AnalogCompassTheme.RAINBOW;

        this.wCompassStyle = null;
        this.wCompassScale = null;
        this.wCompassAnalogSize = null;
        this.wCompassAnalogInnerAlpha = null;
        this.wCompassAnalogTheme = null;
        this.wCompassRainbowSpeed = null;
        this.wCompassTransparency = null;
        this.wCompassBackground = null;
        this.wCompassBgColor = null;
        this.wCompassTextColor = null;
        this.wCompassTextAlpha = null;
        this.wCompassTextRainbow = null;
        this.wCompassShowLatitude = null;
        this.wCompassAnalogShowLatitude = null;
        this.wCompassShowLongitude = null;
        this.wCompassAnalogShowLongitude = null;
        this.wCompassCompact = null;
        this.wCompassAttachHotbar = null;
        this.wZoneDisplay = null;
        this.wZoneFollow = null;
        this.wZoneTextScale = null;
        this.wBiomeDisplay = null;
        this.wBiomeFollow = null;
        this.wBiomeTextScale = null;
        this.wZoneBiomeOrder = null;
        this.wCoordsFollow = null;
        this.wCoordsTextScale = null;
        this.wHudSnap = null;
        this.wHudSnapPixels = null;
        this.wTitleDraggable = null;
        this.wTitleScale = null;
        this.wTitleEnabled = null;
        this.wTitleDuration = null;
        this.wTitleShowBaseDegrees = null;
        this.wTitleColorPreset = null;
        this.wTitleCase = null;
        this.wTitleLetterSpacing = null;
        this.wUndoLoad = null;
        this.wRedoLoad = null;
        this.rgbTextColor = null;
        this.rgbCustomFace = null;
        this.rgbCustomRing = null;
        this.rgbCustomMuted = null;
        this.rgbCustomNeedle = null;
        this.rgbTitleColor = null;
        this.swatchTextColor = null;
        this.swatchCustomFace = null;
        this.swatchCustomRing = null;
        this.swatchCustomMuted = null;
        this.swatchCustomNeedle = null;
        this.swatchTitleColor = null;

        this.titleOffsetXf = LatitudeConfig.zoneEnterTitleOffsetX;
        this.titleOffsetYf = LatitudeConfig.zoneEnterTitleOffsetY;

        this.sidebarScrollWidgets.clear();
        this.sidebarScrollBaseYs.clear();
        this.sidebarSwatches.clear();
        this.sidebarViewportTop = panelY;
        this.sidebarViewportBottom = Math.max(panelY + 24, this.height - 60);

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

            // Direction Format only exists where it has a visible effect: the digital line's facing
            // segment, or the Tape look's heading labels (TEST 28: the button read as dead on the
            // dial looks, which show facing with their needle and have no formatted text to change).
            if (!analog || cfg.analogLook == CompassHudConfig.CompassLook.TAPE) {
                var wDirectionMode = this.addRenderableWidget(CycleButton.<CompassHudConfig.DirectionMode>builder(v -> Component.literal(switch (v) {
                            case CARDINAL_4 -> "N / E / S / W";
                            case CARDINAL_8 -> "8 winds (N, NE...)";
                            case DEGREES -> "Degrees (0-359\u00b0)";
                        }), () -> cfg.directionMode)
                        .withValues(CompassHudConfig.DirectionMode.values())
                        .create(panelX, y, widgetW, rowH, Component.literal("Direction Format"), (btn, value) -> {
                            cfg.directionMode = value;
                            CompassHudConfig.saveCurrent();
                        }));
                tooltip(wDirectionMode, "How the facing readout is written: four cardinals, eight winds, or exact degrees. Applies to the digital compass line and the Tape look's heading labels.");
                trackSidebarWidget(wDirectionMode, y);
                y += rowH + rowGap;
            }

            if (analog) {
                var wLook = this.addRenderableWidget(CycleButton.<CompassHudConfig.CompassLook>builder(v -> Component.literal(lookLabel(v)), () -> cfg.analogLook)
                        .withValues(CompassHudConfig.CompassLook.values())
                        .create(panelX, y, widgetW, rowH, Component.literal("Compass Look"), (btn, value) -> {
                            cfg.analogLook = value;
                            CompassHudConfig.saveCurrent();
                            // Re-init like the style/theme buttons: the Direction Format row exists
                            // only for TAPE, so the panel's row set changes with the look.
                            this.init();
                        }));
                tooltip(wLook, "The dial's shape: classic disc, open ring, compass rose, a linear heading tape, or a minimal needle. Resource packs can reskin any look (globe:textures/gui/compass/<look>.png).");
                trackSidebarWidget(wLook, y);
                y += rowH + rowGap;

                // Range narrowed from 32-128 per live feedback: 128 renders absurdly huge, and even ~30% along
                // that old range (~60px) already dwarfs the readout box, while 32 (the smallest the old range
                // allowed) was already the default. New range keeps the useful huge-vs-tiny span but adds real
                // room below the default to go smaller, instead of the default sitting pinned at the floor.
                // Tape raises its own minimum (TEST 32: below ~32 its heading labels squeeze illegible) --
                // the slider's displayed/draggable range matches CompassDialRenderer's floor exactly, so the
                // number shown is always what's actually rendered, never a lie.
                float analogSizeMin = cfg.analogLook == CompassHudConfig.CompassLook.TAPE
                        ? CompassDialRenderer.TAPE_MIN_DIAMETER : 16.0f;
                this.wCompassAnalogSize = this.addRenderableWidget(new FloatSlider(panelX, y, widgetW, rowH, Component.literal("Analog Size"), analogSizeMin, 72.0f, cfg.analogSize, v -> cfg.analogSize = v));
                tooltip(this.wCompassAnalogSize, "Sets how big the analog compass is.");
                trackSidebarWidget(this.wCompassAnalogSize, y);
                y += rowH + rowGap;
                this.wCompassAnalogInnerAlpha = this.addRenderableWidget(new FloatSlider(panelX, y, widgetW, rowH, Component.literal("Inner Transparency"), 0.0f, 1.0f, cfg.analogInnerAlpha, v -> cfg.analogInnerAlpha = v));
                tooltip(this.wCompassAnalogInnerAlpha, "The compass's inner face opacity. Left (lower) = more see-through, right (higher) = more solid.");
                trackSidebarWidget(this.wCompassAnalogInnerAlpha, y);
                y += rowH + rowGap;
                this.wCompassAnalogTheme = this.addRenderableWidget(CycleButton.<CompassHudConfig.AnalogCompassTheme>builder(v -> Component.literal(themeLabel(v)), () -> cfg.analogTheme)
                        .withValues(CompassHudConfig.AnalogCompassTheme.values())
                        .create(panelX, y, widgetW, rowH, Component.literal("Color Scheme"), (btn, value) -> {
                            cfg.analogTheme = value;
                            CompassHudConfig.saveCurrent();
                            // Custom needs its own RGB sliders constructed/torn down, so (like a style change)
                            // this requires a full re-init rather than just a visibility toggle.
                            this.init();
                        }));
                tooltip(this.wCompassAnalogTheme, "Pick a preset color scheme for the analog compass, or choose Custom to dial in exact colors below.");
                trackSidebarWidget(this.wCompassAnalogTheme, y);
                y += rowH + rowGap;

                if (rainbowTheme) {
                    this.wCompassRainbowSpeed = this.addRenderableWidget(new FloatSlider(panelX, y, widgetW, rowH, Component.literal("Color Cycle Speed"), 10.0f, 40.0f, cfg.rainbowCycleSeconds, v -> cfg.rainbowCycleSeconds = v));
                    tooltip(this.wCompassRainbowSpeed, "How fast Aurora's colors shift, in seconds per full color loop. Left = a bit faster, right = slower and calmer. The whole range is kept gentle on purpose -- a fast-cycling dial can read as strobing -- and narrow enough that every point on the slider makes a noticeable difference.");
                    trackSidebarWidget(this.wCompassRainbowSpeed, y);
                    y += rowH + rowGap;
                }

                if (customTheme) {
                    y = placeRgbPicker(panelX, y, widgetW, rowH, rowGap, "Face", cfg.customFaceRgb,
                            v -> cfg.customFaceRgb = v, g -> this.rgbCustomFace = g, s -> this.swatchCustomFace = s);
                    y = placeRgbPicker(panelX, y, widgetW, rowH, rowGap, "Ring", cfg.customRingArgb & 0xFFFFFF,
                            v -> cfg.customRingArgb = 0xFF000000 | v, g -> this.rgbCustomRing = g, s -> this.swatchCustomRing = s);
                    y = placeRgbPicker(panelX, y, widgetW, rowH, rowGap, "Muted", cfg.customMutedArgb & 0xFFFFFF,
                            v -> cfg.customMutedArgb = 0xFF000000 | v, g -> this.rgbCustomMuted = g, s -> this.swatchCustomMuted = s);
                    y = placeRgbPicker(panelX, y, widgetW, rowH, rowGap, "Needle", cfg.customNeedleArgb & 0xFFFFFF,
                            v -> cfg.customNeedleArgb = 0xFF000000 | v, g -> this.rgbCustomNeedle = g, s -> this.swatchCustomNeedle = s);
                }
            } else {
                this.wCompassScale = this.addRenderableWidget(new FloatSlider(panelX, y, widgetW, rowH, Component.literal("Scale"), 0.5f, 3.0f, cfg.scale, v -> cfg.scale = v));
                tooltip(this.wCompassScale, "Changes the size of the digital compass text.");
                trackSidebarWidget(this.wCompassScale, y);
                y += rowH + rowGap;

                this.wCompassTransparency = this.addRenderableWidget(new IntSlider(panelX, y, widgetW, rowH, Component.literal("Transparency"), 0, 255, cfg.backgroundAlpha, v -> cfg.backgroundAlpha = v));
                tooltip(this.wCompassTransparency, "Adjusts the opacity of the digital compass's background bar.");
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

            y = placeRgbPicker(panelX, y, widgetW, rowH, rowGap, "Text", cfg.textRgb,
                    v -> cfg.textRgb = v, g -> this.rgbTextColor = g, s -> this.swatchTextColor = s);

            this.wCompassTextAlpha = this.addRenderableWidget(new IntSlider(panelX, y, widgetW, rowH, Component.literal("Text Opacity"), 0, 255, cfg.textAlpha, v -> cfg.textAlpha = v));
            tooltip(this.wCompassTextAlpha, "Adjusts the opacity of the compass, zone, biome, and coordinate text.");
            trackSidebarWidget(this.wCompassTextAlpha, y);
            y += rowH + rowGap;

            this.wCompassTextRainbow = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.textRainbow)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Rainbow Text"), (btn, value) -> {
                        cfg.textRainbow = value;
                        CompassHudConfig.saveCurrent();
                    }));
            tooltip(this.wCompassTextRainbow, "Makes the compass, zone, biome, and coordinate text cycle through rainbow colors instead of using the Text Color above.");
            trackSidebarWidget(this.wCompassTextRainbow, y);
            y += rowH + rowGap;

            if (analog) {
                this.wCompassAnalogShowLatitude = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> Boolean.TRUE.equals(cfg.analogShowLatitude))
                        .withValues(true, false)
                        .create(panelX, y, widgetW, rowH, Component.literal("Analog Latitude"), (btn, value) -> cfg.analogShowLatitude = value));
                tooltip(this.wCompassAnalogShowLatitude, "Shows latitude next to the analog compass.");
                trackSidebarWidget(this.wCompassAnalogShowLatitude, y);
                y += rowH + rowGap;

                this.wCompassAnalogShowLongitude = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> Boolean.TRUE.equals(cfg.analogShowLongitude))
                        .withValues(true, false)
                        .create(panelX, y, widgetW, rowH, Component.literal("Analog Longitude"), (btn, value) -> cfg.analogShowLongitude = value));
                tooltip(this.wCompassAnalogShowLongitude, "Shows longitude next to the analog compass.");
                trackSidebarWidget(this.wCompassAnalogShowLongitude, y);
                y += rowH + rowGap;
            } else {
                this.wCompassShowLatitude = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> Boolean.TRUE.equals(cfg.showLatitude))
                        .withValues(true, false)
                        .create(panelX, y, widgetW, rowH, Component.literal("Show Latitude"), (btn, value) -> cfg.showLatitude = value));
                tooltip(this.wCompassShowLatitude, "Shows latitude inside the digital compass line.");
                trackSidebarWidget(this.wCompassShowLatitude, y);
                y += rowH + rowGap;

                this.wCompassShowLongitude = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> Boolean.TRUE.equals(cfg.showLongitude))
                        .withValues(true, false)
                        .create(panelX, y, widgetW, rowH, Component.literal("Show Longitude"), (btn, value) -> cfg.showLongitude = value));
                tooltip(this.wCompassShowLongitude, "Shows longitude inside the digital compass line.");
                trackSidebarWidget(this.wCompassShowLongitude, y);
                y += rowH + rowGap;

                this.wCompassCompact = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.compactHud)
                        .withValues(true, false)
                        .create(panelX, y, widgetW, rowH, Component.literal("Compact HUD"), (btn, value) -> cfg.compactHud = value));
                tooltip(this.wCompassCompact, "Packs the digital compass line closer together, with less space between parts.");
                trackSidebarWidget(this.wCompassCompact, y);
                y += rowH + rowGap;

            }

            this.wCompassAttachHotbar = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.dockMode == CompassHudConfig.DockMode.HOTBAR_RIGHT)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Attach to Hotbar"), (btn, value) -> {
                        cfg.dockMode = value ? CompassHudConfig.DockMode.HOTBAR_RIGHT : CompassHudConfig.DockMode.NONE;
                        cfg.attachToHotbarCompass = value; // legacy mirror for older jars reading this file
                        CompassHudConfig.saveCurrent();
                    }));
            tooltip(this.wCompassAttachHotbar, "Docks the compass next to your hotbar. It automatically shrinks or moves out of the way on narrow screens, so it can never overlap the hotbar or run off-screen.");
            trackSidebarWidget(this.wCompassAttachHotbar, y);
            y += rowH + rowGap;

            var wResetCompass = this.addRenderableWidget(Button.builder(Component.literal("Reset Compass"), b -> {
                        var fresh = CompassHudConfig.fresh();
                        cfg.style = fresh.style;
                        cfg.analogSize = fresh.analogSize;
                        cfg.analogInnerAlpha = fresh.analogInnerAlpha;
                        cfg.analogTheme = fresh.analogTheme;
                        cfg.rainbowCycleSeconds = fresh.rainbowCycleSeconds;
                        cfg.analogLook = fresh.analogLook;
                        cfg.scale = fresh.scale;
                        cfg.showBackground = fresh.showBackground;
                        cfg.backgroundRgb = fresh.backgroundRgb;
                        cfg.backgroundAlpha = fresh.backgroundAlpha;
                        cfg.textRgb = fresh.textRgb;
                        cfg.textAlpha = fresh.textAlpha;
                        cfg.textRainbow = fresh.textRainbow;
                        cfg.hAnchor = fresh.hAnchor;
                        cfg.vAnchor = fresh.vAnchor;
                        cfg.offXFrac = fresh.offXFrac;
                        cfg.offYFrac = fresh.offYFrac;
                        cfg.growH = fresh.growH;
                        cfg.growV = fresh.growV;
                        cfg.dockMode = fresh.dockMode;
                        cfg.attachToHotbarCompass = fresh.attachToHotbarCompass;
                        CompassHudConfig.saveCurrent();
                        this.init();
                    })
                    .bounds(panelX, y, widgetW, rowH)
                    .build());
            tooltip(wResetCompass, "Restore the compass section to its defaults: style, look, size, colors, and placement.");
            // Tracked like every other row (TEST 28: untracked, it neither scrolled with the panel
            // nor hid with the L toggle).
            trackSidebarWidget(wResetCompass, y);
            y += rowH + rowGap;
        } else if (activeTab == TAB_LABELS) {
            this.wZoneDisplay = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.displayZoneInHud)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Display Zone in HUD"), (btn, value) -> {
                        cfg.displayZoneInHud = value;
                        CompassHudConfig.saveCurrent();
                        updateSidebarVisibility();
                    }));
            tooltip(this.wZoneDisplay, "Shows the current zone as small HUD text.");
            trackSidebarWidget(this.wZoneDisplay, y);
            y += rowH + rowGap;

            this.wZoneFollow = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "FOLLOW" : "DETACH"), () -> cfg.zoneFollowsCompass)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Zone Placement"), (btn, value) -> {
                        cfg.zoneFollowsCompass = value;
                        CompassHudConfig.saveCurrent();
                        updateSidebarVisibility();
                    }));
            tooltip(this.wZoneFollow, "Let the zone label ride with the compass or detach it for dragging.");
            trackSidebarWidget(this.wZoneFollow, y);
            y += rowH + rowGap;

            this.wZoneTextScale = this.addRenderableWidget(new FloatSlider(panelX, y, widgetW, rowH, Component.literal("Zone Text Size"), 0.5f, 3.0f, cfg.zoneTextScale, v -> cfg.zoneTextScale = v));
            tooltip(this.wZoneTextScale, "How big the zone label's text is.");
            trackSidebarWidget(this.wZoneTextScale, y);
            y += rowH + rowGap;

            this.wBiomeDisplay = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.displayBiomeInHud)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Display Biome in HUD"), (btn, value) -> {
                        cfg.displayBiomeInHud = value;
                        CompassHudConfig.saveCurrent();
                        updateSidebarVisibility();
                    }));
            tooltip(this.wBiomeDisplay, "Shows the current biome (e.g. Plains, Jungle) as small HUD text.");
            trackSidebarWidget(this.wBiomeDisplay, y);
            y += rowH + rowGap;

            this.wBiomeFollow = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "FOLLOW" : "DETACH"), () -> cfg.biomeFollowsCompass)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Biome Placement"), (btn, value) -> {
                        cfg.biomeFollowsCompass = value;
                        CompassHudConfig.saveCurrent();
                        updateSidebarVisibility();
                    }));
            tooltip(this.wBiomeFollow, "Let the biome label ride with the compass or detach it for dragging.");
            trackSidebarWidget(this.wBiomeFollow, y);
            y += rowH + rowGap;

            this.wBiomeTextScale = this.addRenderableWidget(new FloatSlider(panelX, y, widgetW, rowH, Component.literal("Biome Text Size"), 0.5f, 3.0f, cfg.biomeTextScale, v -> cfg.biomeTextScale = v));
            tooltip(this.wBiomeTextScale, "How big the biome label's text is.");
            trackSidebarWidget(this.wBiomeTextScale, y);
            y += rowH + rowGap;

            this.wZoneBiomeOrder = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "Biome, Zone" : "Zone, Biome"), () -> cfg.biomeBeforeZone)
                    .withValues(false, true)
                    .create(panelX, y, widgetW, rowH, Component.literal("Zone/Biome Order"), (btn, value) -> {
                        cfg.biomeBeforeZone = value;
                        CompassHudConfig.saveCurrent();
                    }));
            tooltip(this.wZoneBiomeOrder, "If both the zone and biome are shown next to the compass, choose which one appears first.");
            trackSidebarWidget(this.wZoneBiomeOrder, y);
            y += rowH + rowGap;

            this.wCoordsFollow = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "FOLLOW" : "DETACH"), () -> cfg.coordsFollowsCompass)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Coords Placement"), (btn, value) -> {
                        cfg.coordsFollowsCompass = value;
                        CompassHudConfig.saveCurrent();
                        updateSidebarVisibility();
                    }));
            tooltip(this.wCoordsFollow, "Let the latitude/longitude readout ride with the compass or detach it for dragging.");
            trackSidebarWidget(this.wCoordsFollow, y);
            y += rowH + rowGap;

            this.wCoordsTextScale = this.addRenderableWidget(new FloatSlider(panelX, y, widgetW, rowH, Component.literal("Coords Text Size"), 0.5f, 3.0f, cfg.coordsTextScale, v -> cfg.coordsTextScale = v));
            tooltip(this.wCoordsTextScale, "How big the latitude/longitude text is.");
            trackSidebarWidget(this.wCoordsTextScale, y);
            y += rowH + rowGap;

            var growLabels = new java.util.LinkedHashMap<com.example.globe.core.ui.HudLayoutMath.GrowH, String>();
            growLabels.put(com.example.globe.core.ui.HudLayoutMath.GrowH.LEFT, "Grow Right");
            growLabels.put(com.example.globe.core.ui.HudLayoutMath.GrowH.CENTER, "Grow Both Ways");
            growLabels.put(com.example.globe.core.ui.HudLayoutMath.GrowH.RIGHT, "Grow Left");

            var wZoneGrow = this.addRenderableWidget(CycleButton.<com.example.globe.core.ui.HudLayoutMath.GrowH>builder(v -> Component.literal(growLabels.get(v)), () -> cfg.zoneGrowH)
                    .withValues(com.example.globe.core.ui.HudLayoutMath.GrowH.values())
                    .create(panelX, y, widgetW, rowH, Component.literal("Zone Text Grow"), (btn, value) -> {
                        cfg.zoneGrowH = value;
                        CompassHudConfig.saveCurrent();
                    }));
            tooltip(wZoneGrow, "If the zone name changes length (like \"Tropics\" vs \"Subtropics\"), pick which side the label grows from -- left, right, or evenly on both sides. Where you've placed the label on screen never moves, only which direction the text stretches from it.");
            trackSidebarWidget(wZoneGrow, y);
            y += rowH + rowGap;

            var wBiomeGrow = this.addRenderableWidget(CycleButton.<com.example.globe.core.ui.HudLayoutMath.GrowH>builder(v -> Component.literal(growLabels.get(v)), () -> cfg.biomeGrowH)
                    .withValues(com.example.globe.core.ui.HudLayoutMath.GrowH.values())
                    .create(panelX, y, widgetW, rowH, Component.literal("Biome Text Grow"), (btn, value) -> {
                        cfg.biomeGrowH = value;
                        CompassHudConfig.saveCurrent();
                    }));
            tooltip(wBiomeGrow, "If the biome name changes length (like \"Plains\" vs \"Windswept Gravelly Hills\"), pick which side the label grows from -- left, right, or evenly on both sides. Where you've placed the label on screen never moves, only which direction the text stretches from it.");
            trackSidebarWidget(wBiomeGrow, y);
            y += rowH + rowGap;

            var wReserved = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> cfg.reservedTextWidth)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Reserved Width"), (btn, value) -> {
                        cfg.reservedTextWidth = value;
                        CompassHudConfig.saveCurrent();
                    }));
            tooltip(wReserved, "Makes the text boxes as wide as this world's longest biome name, so their edges never shift as you move between biomes.");
            trackSidebarWidget(wReserved, y);
            y += rowH + rowGap;

            // Drag/snap controls live with the other placement controls (moved from General per TEST 29
            // feedback) — they govern how every draggable element moves, and Labels is where dragging
            // gets configured.
            this.wHudSnap = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "Snap to Grid" : "Free Move"), () -> LatitudeConfig.hudSnapEnabled)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Dragging"), (btn, value) -> {
                        LatitudeConfig.hudSnapEnabled = value;
                        LatitudeConfig.saveCurrent();
                        updateSidebarVisibility();
                    }));
            tooltip(this.wHudSnap, "Snap dragged HUD elements to a grid, or move them freely to any pixel.");
            trackSidebarWidget(this.wHudSnap, y);
            y += rowH + rowGap;

            this.wHudSnapPixels = this.addRenderableWidget(new IntSlider(panelX, y, widgetW, rowH, Component.literal("Grid Size"), 1, 32, LatitudeConfig.hudSnapPixels, v -> LatitudeConfig.hudSnapPixels = v));
            tooltip(this.wHudSnapPixels, "How far apart the grid lines are when Snap to Grid is on.");
            trackSidebarWidget(this.wHudSnapPixels, y);
            y += rowH + rowGap;

            var wResetLabels = this.addRenderableWidget(Button.builder(Component.literal("Reset Labels"), b -> {
                        var fresh = CompassHudConfig.fresh();
                        cfg.displayZoneInHud = fresh.displayZoneInHud;
                        cfg.zoneFollowsCompass = fresh.zoneFollowsCompass;
                        cfg.zoneHAnchor = fresh.zoneHAnchor;
                        cfg.zoneVAnchor = fresh.zoneVAnchor;
                        cfg.zoneOffXFrac = fresh.zoneOffXFrac;
                        cfg.zoneOffYFrac = fresh.zoneOffYFrac;
                        cfg.zoneGrowH = fresh.zoneGrowH;
                        cfg.zoneGrowV = fresh.zoneGrowV;
                        cfg.zoneTextScale = fresh.zoneTextScale;
                        cfg.displayBiomeInHud = fresh.displayBiomeInHud;
                        cfg.biomeFollowsCompass = fresh.biomeFollowsCompass;
                        cfg.biomeHAnchor = fresh.biomeHAnchor;
                        cfg.biomeVAnchor = fresh.biomeVAnchor;
                        cfg.biomeOffXFrac = fresh.biomeOffXFrac;
                        cfg.biomeOffYFrac = fresh.biomeOffYFrac;
                        cfg.biomeGrowH = fresh.biomeGrowH;
                        cfg.biomeGrowV = fresh.biomeGrowV;
                        cfg.biomeTextScale = fresh.biomeTextScale;
                        cfg.biomeBeforeZone = fresh.biomeBeforeZone;
                        cfg.coordsFollowsCompass = fresh.coordsFollowsCompass;
                        cfg.coordsHAnchor = fresh.coordsHAnchor;
                        cfg.coordsVAnchor = fresh.coordsVAnchor;
                        cfg.coordsOffXFrac = fresh.coordsOffXFrac;
                        cfg.coordsOffYFrac = fresh.coordsOffYFrac;
                        cfg.coordsGrowH = fresh.coordsGrowH;
                        cfg.coordsGrowV = fresh.coordsGrowV;
                        cfg.coordsTextScale = fresh.coordsTextScale;
                        cfg.reservedTextWidth = fresh.reservedTextWidth;
                        CompassHudConfig.saveCurrent();
                        this.init();
                    })
                    .bounds(panelX, y, widgetW, rowH)
                    .build());
            tooltip(wResetLabels, "Restore the zone/biome/coords label settings to their defaults.");
            trackSidebarWidget(wResetLabels, y);
            y += rowH + rowGap;
        } else if (activeTab == TAB_TITLE) {
            this.wTitleEnabled = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> LatitudeConfig.zoneEnterTitleEnabled)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Zone Title"), (btn, value) -> {
                        LatitudeConfig.zoneEnterTitleEnabled = value;
                        LatitudeConfig.saveCurrent();
                    }));
            tooltip(this.wTitleEnabled, "Master on/off for the big title that pops up when crossing a latitude zone.");
            trackSidebarWidget(this.wTitleEnabled, y);
            y += rowH + rowGap;

            this.wTitleScale = this.addRenderableWidget(new StepSlider(panelX, y, widgetW, rowH, Component.literal("Title Size"), 1.0, 3.0, 0.1, LatitudeConfig.zoneEnterTitleScale, v -> LatitudeConfig.zoneEnterTitleScale = v));
            tooltip(this.wTitleScale, "Changes how big the zone-entry title appears when it pops up.");
            trackSidebarWidget(this.wTitleScale, y);
            y += rowH + rowGap;

            this.wTitleDuration = this.addRenderableWidget(new StepSlider(panelX, y, widgetW, rowH, Component.literal("Title Duration"), 2.0, 10.0, 0.5, LatitudeConfig.zoneEnterTitleSeconds, v -> LatitudeConfig.zoneEnterTitleSeconds = v));
            tooltip(this.wTitleDuration, "How many seconds the zone-enter title stays on screen.");
            trackSidebarWidget(this.wTitleDuration, y);
            y += rowH + rowGap;

            this.wTitleShowBaseDegrees = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> LatitudeConfig.showZoneBaseDegreesOnTitle)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Show Degrees"), (btn, value) -> {
                        LatitudeConfig.showZoneBaseDegreesOnTitle = value;
                        LatitudeConfig.saveCurrent();
                    }));
            tooltip(this.wTitleShowBaseDegrees, "Adds your latitude in degrees to the title text (e.g. \"Tropics 12°S\" instead of just \"Tropics\").");
            trackSidebarWidget(this.wTitleShowBaseDegrees, y);
            y += rowH + rowGap;

            this.wTitleColorPreset = this.addRenderableWidget(CycleButton.<LatitudeConfigData.TitleColorPreset>builder(v -> Component.literal(titleColorLabel(v)), () -> LatitudeConfig.zoneEnterTitleColorPreset)
                    .withValues(LatitudeConfigData.TitleColorPreset.values())
                    .create(panelX, y, widgetW, rowH, Component.literal("Title Color"), (btn, value) -> {
                        LatitudeConfig.zoneEnterTitleColorPreset = value;
                        LatitudeConfig.saveCurrent();
                        // Custom needs its own RGB sliders constructed/torn down -- same reason the compass
                        // Color Scheme cycle button forces a full re-init when picking Custom.
                        this.init();
                    }));
            tooltip(this.wTitleColorPreset, "Pick a color for the zone-enter title, choose Custom to dial in exact colors, or Rainbow for a letter-by-letter cycle.");
            trackSidebarWidget(this.wTitleColorPreset, y);
            y += rowH + rowGap;

            if (LatitudeConfig.zoneEnterTitleColorPreset == LatitudeConfigData.TitleColorPreset.CUSTOM) {
                y = placeRgbPicker(panelX, y, widgetW, rowH, rowGap, "Title", LatitudeConfig.zoneEnterTitleRgb,
                        v -> LatitudeConfig.zoneEnterTitleRgb = v, g -> this.rgbTitleColor = g, s -> this.swatchTitleColor = s);
            }

            this.wTitleCase = this.addRenderableWidget(CycleButton.<LatitudeConfigData.TitleCaseMode>builder(v -> Component.literal(titleCaseLabel(v)), () -> LatitudeConfig.zoneEnterTitleCase)
                    .withValues(LatitudeConfigData.TitleCaseMode.values())
                    .create(panelX, y, widgetW, rowH, Component.literal("Title Case"), (btn, value) -> {
                        LatitudeConfig.zoneEnterTitleCase = value;
                        LatitudeConfig.saveCurrent();
                    }));
            tooltip(this.wTitleCase, "Changes how the title's letters are written: Normal, UPPERCASE, lowercase, or mOcKiNg.");
            trackSidebarWidget(this.wTitleCase, y);
            y += rowH + rowGap;

            this.wTitleLetterSpacing = this.addRenderableWidget(new IntSlider(panelX, y, widgetW, rowH, Component.literal("Letter Spacing"), -4, 16, LatitudeConfig.zoneEnterTitleLetterSpacing, v -> LatitudeConfig.zoneEnterTitleLetterSpacing = v));
            tooltip(this.wTitleLetterSpacing, "Adds (or removes) extra space between letters in the zone-enter title.");
            trackSidebarWidget(this.wTitleLetterSpacing, y);
            y += rowH + rowGap;

            this.wTitleDraggable = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> LatitudeConfig.zoneEnterTitleDraggable)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Title Draggable"), (btn, value) -> {
                        LatitudeConfig.zoneEnterTitleDraggable = value;
                        LatitudeConfig.saveCurrent();
                    }));
            tooltip(this.wTitleDraggable, "Allows the zone-enter title to be repositioned by dragging it in this editor.");
            trackSidebarWidget(this.wTitleDraggable, y);
            y += rowH + rowGap;

            var wResetTitle = this.addRenderableWidget(Button.builder(Component.literal("Reset Title"), b -> {
                        LatitudeConfig.zoneEnterTitleOffsetX = 0;
                        LatitudeConfig.zoneEnterTitleOffsetY = 0;
                        LatitudeConfig.zoneEnterTitleScale = 1.6;
                        LatitudeConfig.zoneEnterTitleSeconds = 4.0;
                        LatitudeConfig.saveCurrent();
                        this.titleOffsetXf = 0;
                        this.titleOffsetYf = 0;
                        this.init();
                    })
                    .bounds(panelX, y, widgetW, rowH)
                    .build());
            tooltip(wResetTitle, "Restore the zone-enter title settings to their defaults.");
            trackSidebarWidget(wResetTitle, y);
            y += rowH + rowGap;
        } else if (activeTab == TAB_PRESETS) {
            var wExport = this.addRenderableWidget(Button.builder(Component.literal("Export to Clipboard"), b -> {
                        Minecraft.getInstance().keyboardHandler.setClipboard(CompassHudPreset.captureCurrent().toJson());
                    })
                    .bounds(panelX, y, widgetW, rowH)
                    .build());
            tooltip(wExport, "Copies your current HUD look (compass + labels + title) to the clipboard so you can paste it somewhere to share or back up.");
            trackSidebarWidget(wExport, y);
            y += rowH + rowGap;

            var wImport = this.addRenderableWidget(Button.builder(Component.literal("Import from Clipboard"), b -> {
                        CompassHudPreset p = CompassHudPreset.fromJson(Minecraft.getInstance().keyboardHandler.getClipboard());
                        if (p != null) {
                            p.applyToLive();
                            this.init();
                        }
                    })
                    .bounds(panelX, y, widgetW, rowH)
                    .build());
            tooltip(wImport, "Reads a HUD look from the clipboard (something exported here, or shared by someone else) and applies it. Does nothing if the clipboard doesn't contain a valid HUD look.");
            trackSidebarWidget(wImport, y);
            y += rowH + rowGap;

            // Undo / redo as two compact arrow-icon buttons side by side (Peetsa: drop the verbose label).
            // Labels are empty here; the arrow glyph is drawn large in drawPresetHistoryIcons() because the
            // vanilla button message renders the unicode arrow far too small.
            int histGap = 3;
            int undoW = (widgetW - histGap) / 2;
            int redoW = widgetW - undoW - histGap;
            this.wUndoLoad = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
                        CompassHudPreset.undoLastLoad();
                        this.init();
                    })
                    .bounds(panelX, y, undoW, rowH)
                    .build());
            this.wUndoLoad.active = CompassHudPreset.hasUndo();
            tooltip(this.wUndoLoad, "Undo: restore your HUD look to whatever it was right before the last Load or Import -- for when you tap the wrong slot. Goes back one step.");
            trackSidebarWidget(this.wUndoLoad, y);

            this.wRedoLoad = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
                        CompassHudPreset.redoLastLoad();
                        this.init();
                    })
                    .bounds(panelX + undoW + histGap, y, redoW, rowH)
                    .build());
            this.wRedoLoad.active = CompassHudPreset.hasRedo();
            tooltip(this.wRedoLoad, "Redo: re-apply the Load or Import you just undid.");
            trackSidebarWidget(this.wRedoLoad, y);
            y += rowH + rowGap;

            int slotGap = 3;
            int clearW = 20;
            int saveW = 44;
            int loadW = widgetW - clearW - saveW - slotGap * 2;
            for (int slot = 0; slot < CompassHudPresetSlots.SLOT_COUNT; slot++) {
                final int s = slot;
                String label = (s + 1) + ": " + CompassHudPresetSlots.summarize(s);
                var wLoad = this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                            if (CompassHudPresetSlots.loadFrom(s)) this.init();
                        })
                        .bounds(panelX, y, loadW, rowH)
                        .build());
                wLoad.active = CompassHudPresetSlots.isOccupied(s);
                tooltip(wLoad, "Loads the HUD look saved in slot " + (s + 1) + ".");
                trackSidebarWidget(wLoad, y);

                var wSave = this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
                            CompassHudPresetSlots.saveCurrentInto(s);
                            this.init();
                        })
                        .bounds(panelX + loadW + slotGap, y, saveW, rowH)
                        .build());
                tooltip(wSave, "Saves your current HUD look into slot " + (s + 1) + ", overwriting whatever was there.");
                trackSidebarWidget(wSave, y);

                var wClear = this.addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                            CompassHudPresetSlots.clear(s);
                            this.init();
                        })
                        .bounds(panelX + loadW + slotGap + saveW + slotGap, y, clearW, rowH)
                        .build());
                wClear.active = CompassHudPresetSlots.isOccupied(s);
                tooltip(wClear, "Empties slot " + (s + 1) + ".");
                trackSidebarWidget(wClear, y);

                y += rowH + rowGap;
            }
        } else {
            var wShowMode = this.addRenderableWidget(CycleButton.<CompassHudConfig.ShowMode>builder(v -> Component.literal(switch (v) {
                        case ALWAYS -> "Always";
                        case COMPASS_PRESENT -> "Compass in inventory";
                        case HOLDING_COMPASS -> "Holding compass";
                    }), () -> cfg.showMode)
                    .withValues(CompassHudConfig.ShowMode.values())
                    .create(panelX, y, widgetW, rowH, Component.literal("Show HUD"), (btn, value) -> {
                        cfg.showMode = value;
                        CompassHudConfig.saveCurrent();
                    }));
            tooltip(wShowMode, "Controls when the compass appears while you're playing. This screen always shows it so you can edit it, but in-game it only shows under this rule.");
            trackSidebarWidget(wShowMode, y);
            y += rowH + rowGap;

            var wWarnings = this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF"), () -> LatitudeConfig.showWarningMessages)
                    .withValues(true, false)
                    .create(panelX, y, widgetW, rowH, Component.literal("Warning Messages"), (btn, value) -> {
                        LatitudeConfig.showWarningMessages = value;
                        LatitudeConfig.saveCurrent();
                    }));
            tooltip(wWarnings, "Shows on-screen warnings for things like nearing the world border or polar hazards.");
            trackSidebarWidget(wWarnings, y);
            y += rowH + rowGap;

            var wPreviewText = this.addRenderableWidget(CycleButton.<CompassHud.PreviewTextSource>builder(v -> Component.literal(switch (v) {
                        case SAMPLE -> "Short sample";
                        case LONGEST -> "Longest real text";
                        case LIVE -> "Live values";
                    }), () -> CompassHud.previewTextSource)
                    .withValues(CompassHud.PreviewTextSource.values())
                    .create(panelX, y, widgetW, rowH, Component.literal("Preview Text"), (btn, value) -> CompassHud.previewTextSource = value));
            tooltip(wPreviewText, "What sample text this screen uses to show you where things sit. \"Longest real text\" (default) uses the biggest it could realistically get, so where you place things here matches what you'll see in-game.");
            trackSidebarWidget(wPreviewText, y);
            y += rowH + rowGap;
        }

        this.sidebarContentHeight = Math.max(0, y - rowGap - panelY);

        int resetY = this.height - 52;
        this.wResetHud = this.addRenderableWidget(Button.builder(Component.literal("Reset HUD"), b -> {
                    resetHudDefaults();
                    dragElement = DragElement.NONE;
                    this.init();
                })
                .bounds(panelX, resetY, widgetW, rowH)
                .build());
        tooltip(this.wResetHud, "Restore compass and zone HUD settings to defaults.");

        int bw = 200;
        int bh = 20;
        int btnGap = 4;
        int halfW = (bw - btnGap) / 2;
        int groupX = (this.width - bw) / 2;
        int doneY = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
                    CompassHudConfig.saveCurrent();
                    LatitudeConfig.saveCurrent();
                    Minecraft.getInstance().setScreenAndShow(parent);
                })
                .bounds(groupX, doneY, halfW, bh)
                .build());
        Button cancelBtn = this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> cancelAndClose())
                .bounds(groupX + halfW + btnGap, doneY, bw - halfW - btnGap, bh)
                .build());
        tooltip(cancelBtn, "Discard every change made since opening HUD Studio and go back.");

        updateSidebarVisibility();
    }

    /** Last render-pass mouse position, for hover checks outside the widget event flow (the compass
     *  preview asks {@link #transparencyAdjustActive()} during its own draw). */
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    /** True while the player is plausibly adjusting Inner Transparency (slider hovered, or mid-drag).
     *  The checkerboard transparency aid draws only then — always-on it read as visual noise and made
     *  the compass graphic look bigger than it is (TEST 29). Deliberately does NOT check isFocused():
     *  vanilla keyboard focus is sticky (it persists after mouseUp until some other widget is clicked),
     *  which locked the checkerboard on permanently after the very first touch of the slider (TEST 30).
     *  FloatSlider.isDragging() tracks the actual click-drag-release lifecycle instead. */
    public boolean transparencyAdjustActive() {
        if (wCompassAnalogInnerAlpha == null || !wCompassAnalogInnerAlpha.visible) {
            return false;
        }
        boolean dragging = wCompassAnalogInnerAlpha instanceof FloatSlider fs && fs.isDragging();
        return dragging || wCompassAnalogInnerAlpha.isMouseOver(lastMouseX, lastMouseY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.extractTransparentBackground(ctx);
        ctx.fill(0, 0, this.width, this.height, 0x66000000);

        int sidebarX = 6;

        if (sidebarVisible) {
            drawTabStrip(ctx, mouseX, mouseY);

            int px = sidebarX;
            int py = this.sidebarBgY;
            int pw = sidebarWidth + 4;
            int cardBottom = this.height - 22;
            int ph = Math.max(24, cardBottom - py);

            // Themed card: border shell + fill + thin gold accent lines, matching the look already
            // established on the Settings and World Creation screens.
            ctx.fill(px, py, px + pw, py + ph, PANEL_BORDER);
            ctx.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, PANEL_BG);
            ctx.fill(px + 2, py + 2, px + pw - 2, py + 3, GOLD & 0x66FFFFFF);
            ctx.fill(px + 2, py + ph - 3, px + pw - 2, py + ph - 2, GOLD & 0x66FFFFFF);

            String heading = TAB_NAMES[activeTab];
            int headingW = this.font.width(heading);
            int headingX = px + (pw - headingW) / 2;
            int headingY = py + 6;
            int lineGap = 6;
            int lineLen = Math.max(10, (pw - headingW - lineGap * 2) / 2 - 4);
            int lineY = headingY + this.font.lineHeight / 2;
            ctx.fill(px + 4, lineY, px + 4 + lineLen, lineY + 1, PANEL_BORDER);
            ctx.fill(px + pw - 4 - lineLen, lineY, px + pw - 4, lineY + 1, PANEL_BORDER);
            ctx.text(this.font, heading, headingX, headingY, GOLD);
        }

        var mc = Minecraft.getInstance();
        // Shared with the drag hit-test (studioPreviewTitle) so the rendered text and the grabbable area
        // can never drift apart -- and so "Show Degrees" (TEST 32: reported as doing nothing) has exactly
        // one place to be honored instead of a second, easily-forgotten inline copy.
        String sampleTitle = studioPreviewTitle(mc);

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
        drawSidebarSwatches(ctx);
        drawSidebarScrollbar(ctx);
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // Drawn AFTER the widgets so the big glyphs sit on top of the (empty-labelled) undo/redo buttons.
        if (sidebarVisible && activeTab == TAB_PRESETS) {
            drawButtonGlyph(ctx, wUndoLoad, "↶");
            drawButtonGlyph(ctx, wRedoLoad, "↷");
        }

        if (sidebarVisible) {
            ctx.text(this.font, "Press L to hide settings", 8, 8, 0xAAFFFFFF);
        } else {
            ctx.text(this.font, "Press L to show settings", 8, 8, 0xFFFFFFFF);
        }
    }

    /** Draws a glyph scaled up and centered on a button — used for the undo/redo arrows, whose default
     *  (unicode-fallback) rendering is far too small at button size. Mirrors the tab-strip's scaled label
     *  draw. Only draws visible buttons (applySidebarScroll already hid disabled/scrolled-out ones). */
    private void drawButtonGlyph(GuiGraphicsExtractor ctx, AbstractWidget w, String glyph) {
        if (w == null || !w.visible) return;
        float scale = 1.9f;
        int cx = w.getX() + w.getWidth() / 2;
        int cy = w.getY() + w.getHeight() / 2;
        int glyphW = this.font.width(glyph);
        var m = ctx.pose();
        m.pushMatrix();
        try {
            m.translate(cx, cy);
            m.scale(scale, scale);
            ctx.text(this.font, glyph, -glyphW / 2, -this.font.lineHeight / 2, 0xFFFFFFFF);
        } finally {
            m.popMatrix();
        }
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

        double mx = click.x();
        double my = click.y();

        if (click.button() == 0) {
            if (handleTabClick(mx, my)) {
                return true;
            }

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
                var cfg = CompassHudConfig.get();
                if (cfg.dockMode == CompassHudConfig.DockMode.HOTBAR_RIGHT) {
                    // Grabbing a docked compass UNDOCKS it (TEST 29 request): the pin is seeded at the
                    // docked position first so nothing jumps before the first drag event, then the drag
                    // proceeds exactly like any detached drag. init() refreshes the Attach button to OFF.
                    var mc = Minecraft.getInstance();
                    var b = CompassHud.computeBounds(mc, cfg);
                    cfg.dockMode = CompassHudConfig.DockMode.NONE;
                    cfg.attachToHotbarCompass = false; // legacy mirror
                    if (b != null) {
                        CompassHud.applyCompassDrag(mc, cfg, b.x(), b.y());
                    }
                    CompassHudConfig.saveCurrent();
                    this.init();
                }
                dragElement = DragElement.COMPASS;
                return true;
            }

            if (isMouseOverZone(mx, my)) {
                dragElement = DragElement.ZONE;
                return true;
            }

            if (isMouseOverBiome(mx, my)) {
                dragElement = DragElement.BIOME;
                return true;
            }

            if (isMouseOverCoords(mx, my)) {
                dragElement = DragElement.COORDS;
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
            if (cfg.dockMode != CompassHudConfig.DockMode.NONE) {
                return true; // docked position is computed from the hotbar, not draggable
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

            // Pin & Grow v1: the drag moves the PIN (snap applies to the pin point inside the helper).
            CompassHud.applyCompassDrag(mc, cfg, targetX, targetY);
            return true;
        }

        if (dragElement == DragElement.ZONE) {
            var mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return true;
            var cfg = CompassHudConfig.get();
            if (!cfg.displayZoneInHud || cfg.zoneFollowsCompass) return true;

            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            var zb = CompassHud.computeZoneBounds(mc, cfg);
            if (zb == null) return true;

            int targetX = (int) Math.round(mx) - zoneGrabDx;
            int targetY = (int) Math.round(my) - zoneGrabDy;
            int boxW = zb.w();
            int boxH = zb.h();
            targetX = clamp(targetX, 0, Math.max(0, screenW - boxW));
            targetY = clamp(targetY, 0, Math.max(0, screenH - boxH));

            CompassHud.applyZoneDrag(mc, cfg, targetX, targetY, boxW, boxH);
            return true;
        }

        // Mirrors the ZONE block above, for the biome label.
        if (dragElement == DragElement.BIOME) {
            var mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return true;
            var cfg = CompassHudConfig.get();
            if (!cfg.displayBiomeInHud || cfg.biomeFollowsCompass) return true;

            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            var bb = CompassHud.computeBiomeBounds(mc, cfg);
            if (bb == null) return true;

            int targetX = (int) Math.round(mx) - biomeGrabDx;
            int targetY = (int) Math.round(my) - biomeGrabDy;
            int boxW = bb.w();
            int boxH = bb.h();
            targetX = clamp(targetX, 0, Math.max(0, screenW - boxW));
            targetY = clamp(targetY, 0, Math.max(0, screenH - boxH));

            CompassHud.applyBiomeDrag(mc, cfg, targetX, targetY, boxW, boxH);
            return true;
        }

        // Mirrors the ZONE block above, for the detached coords (lat/lon) label.
        if (dragElement == DragElement.COORDS) {
            var mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return true;
            var cfg = CompassHudConfig.get();
            if (cfg.coordsFollowsCompass) return true;

            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            var cb = CompassHud.computeCoordsBounds(mc, cfg);
            if (cb == null) return true;

            int targetX = (int) Math.round(mx) - coordsGrabDx;
            int targetY = (int) Math.round(my) - coordsGrabDy;
            int boxW = cb.w();
            int boxH = cb.h();
            targetX = clamp(targetX, 0, Math.max(0, screenW - boxW));
            targetY = clamp(targetY, 0, Math.max(0, screenH - boxH));

            CompassHud.applyCoordsDrag(mc, cfg, targetX, targetY, boxW, boxH);
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
            if (dragElement == DragElement.ZONE) {
                CompassHudConfig.saveCurrent();
            }
            if (dragElement == DragElement.BIOME) {
                CompassHudConfig.saveCurrent();
            }
            if (dragElement == DragElement.COORDS) {
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
        LatitudeConfig.zoneEnterTitleEnabled = true;
        LatitudeConfig.zoneEnterTitleSeconds = 6.0;
        LatitudeConfig.showZoneBaseDegreesOnTitle = true;
        LatitudeConfig.zoneEnterTitleColorPreset = LatitudeConfigData.TitleColorPreset.WHITE;
        LatitudeConfig.zoneEnterTitleRgb = 0xFFFFFF;
        LatitudeConfig.zoneEnterTitleCase = LatitudeConfigData.TitleCaseMode.NORMAL;
        LatitudeConfig.zoneEnterTitleLetterSpacing = 0;
        LatitudeConfig.zoneEnterTitleDraggable = true;
        // Matches LatitudeConfig's own field-initializer defaults (hudSnapEnabled=true, hudSnapPixels=8) --
        // there's no fresh()-style factory on LatitudeConfig (unlike CompassHudConfig), so this follows the same
        // hardcoded-literal pattern already used for the zoneEnterTitle* resets above.
        LatitudeConfig.hudSnapEnabled = true;
        LatitudeConfig.hudSnapPixels = 8;
        LatitudeConfig.saveCurrent();
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
            w.visible = w.active && drawY >= sidebarViewportTop && drawY + w.getHeight() <= sidebarViewportBottom;
        }
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

    private void drawSidebarSwatches(GuiGraphicsExtractor ctx) {
        if (!sidebarVisible) return;
        int panelX = 8;
        for (SwatchSlot s : sidebarSwatches) {
            int drawY = s.baseY - sidebarScrollY;
            if (drawY < sidebarViewportTop || drawY + s.height > sidebarViewportBottom) continue;
            int argb = 0xFF000000 | (s.color.getAsInt() & 0xFFFFFF);
            ctx.fill(panelX, drawY, panelX + sidebarWidgetW, drawY + s.height, argb);
            ctx.fill(panelX, drawY, panelX + sidebarWidgetW, drawY + 1, PANEL_BORDER);
            ctx.fill(panelX, drawY + s.height - 1, panelX + sidebarWidgetW, drawY + s.height, PANEL_BORDER);
        }
    }

    private void drawTabStrip(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int tabCount = TAB_NAMES.length;
        int totalW = sidebarWidth + 4;
        int tabW = (totalW - TAB_GAP * (tabCount - 1)) / tabCount;
        int x = 6;
        for (int i = 0; i < tabCount; i++) {
            boolean active = i == activeTab;
            boolean hovered = !active && mouseX >= x && mouseX < x + tabW && mouseY >= tabStripY && mouseY < tabStripY + TAB_H;
            int bg = active ? PANEL_BG : (hovered ? 0xFF3A302A : 0xFF2A2420);
            int border = active ? GOLD : PANEL_BORDER;
            ctx.fill(x, tabStripY, x + tabW, tabStripY + TAB_H, bg);
            ctx.fill(x, tabStripY, x + tabW, tabStripY + 1, border);
            ctx.fill(x, tabStripY, x + 1, tabStripY + TAB_H, border);
            ctx.fill(x + tabW - 1, tabStripY, x + tabW, tabStripY + TAB_H, border);
            if (active) {
                ctx.fill(x + 1, tabStripY + TAB_H - 1, x + tabW - 1, tabStripY + TAB_H, PANEL_BG);
            } else {
                ctx.fill(x, tabStripY + TAB_H - 1, x + tabW, tabStripY + TAB_H, PANEL_BORDER);
            }
            int labelColor = active ? GOLD : (hovered ? WARM_WHITE : MUTED);
            String label = TAB_NAMES[i];
            int labelW = this.font.width(label);
            int cx = x + tabW / 2;
            int cy = tabStripY + TAB_H / 2;
            var m = ctx.pose();
            m.pushMatrix();
            try {
                m.translate(cx, cy);
                m.scale(TAB_LABEL_SCALE, TAB_LABEL_SCALE);
                ctx.text(this.font, label, -labelW / 2, -this.font.lineHeight / 2, labelColor);
            } finally {
                m.popMatrix();
            }
            x += tabW + TAB_GAP;
        }
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        if (!sidebarVisible) return false;
        if (mouseY < tabStripY || mouseY >= tabStripY + TAB_H) return false;
        int tabCount = TAB_NAMES.length;
        int totalW = sidebarWidth + 4;
        int tabW = (totalW - TAB_GAP * (tabCount - 1)) / tabCount;
        int x = 6;
        for (int i = 0; i < tabCount; i++) {
            if (mouseX >= x && mouseX < x + tabW) {
                switchTab(i);
                return true;
            }
            x += tabW + TAB_GAP;
        }
        return false;
    }

    private void switchTab(int tab) {
        if (tab == activeTab) return;
        activeTab = tab;
        sidebarScrollY = 0;
        this.init();
    }

    // Places a labeled RGB picker (3 stacked 0-255 sliders + a color swatch row) starting at y, tracking its
    // sliders for sidebar scrolling and its swatch for the swatch-draw pass. Returns the next available row's Y.
    private int placeRgbPicker(int panelX, int y, int widgetW, int rowH, int rowGap, String label, int initialRgb,
                                IntConsumer onChangeRgb, Consumer<RgbPickerGroup> groupSink, Consumer<SwatchSlot> swatchSink) {
        RgbPickerGroup group = new RgbPickerGroup(panelX, y, widgetW, rowH, rowGap, label, initialRgb, onChangeRgb);
        AbstractWidget[] sliders = group.sliders();
        String[] channelNames = {"Red", "Green", "Blue"};
        for (int i = 0; i < sliders.length; i++) {
            this.addRenderableWidget(sliders[i]);
            trackSidebarWidget(sliders[i], y + i * (rowH + rowGap));
            tooltip(sliders[i], label + " color -- " + channelNames[i] + " channel (0-255).");
        }
        int swatchY = y + sliders.length * (rowH + rowGap);
        int swatchH = 10;
        SwatchSlot swatch = new SwatchSlot(swatchY, swatchH, group::color);
        sidebarSwatches.add(swatch);
        groupSink.accept(group);
        swatchSink.accept(swatch);
        return swatchY + swatchH + rowGap;
    }

    private void updateSidebarVisibility() {
        var cfg = CompassHudConfig.get();

        // Blanket pass first: EVERY tracked sidebar row follows the L toggle. The named calls below
        // then re-apply their conditional rules. Widgets created as locals (Direction Format, Compass
        // Look, the General tab's cyclers, Reset Compass) are only reachable through the tracker —
        // relying on the hand-maintained field list alone left them visible when hidden (TEST 28).
        for (AbstractWidget w : sidebarScrollWidgets) {
            setVisible(w, sidebarVisible);
        }

        setVisible(wCompassStyle, sidebarVisible);
        setVisible(wCompassScale, sidebarVisible);
        setVisible(wCompassAnalogSize, sidebarVisible);
        setVisible(wCompassAnalogInnerAlpha, sidebarVisible);
        setVisible(wCompassAnalogTheme, sidebarVisible);
        setVisible(wCompassTransparency, sidebarVisible);
        setVisible(wCompassBackground, sidebarVisible);
        setVisible(wCompassBgColor, sidebarVisible);
        setVisible(wCompassTextColor, sidebarVisible);
        setVisible(wCompassTextAlpha, sidebarVisible);
        setVisible(wCompassTextRainbow, sidebarVisible);
        setRgbGroupVisible(rgbTextColor, sidebarVisible);
        setRgbGroupVisible(rgbCustomFace, sidebarVisible);
        setRgbGroupVisible(rgbCustomRing, sidebarVisible);
        setRgbGroupVisible(rgbCustomMuted, sidebarVisible);
        setRgbGroupVisible(rgbCustomNeedle, sidebarVisible);
        setVisible(wCompassShowLatitude, sidebarVisible);
        setVisible(wCompassAnalogShowLatitude, sidebarVisible);
        setVisible(wCompassShowLongitude, sidebarVisible);
        setVisible(wCompassAnalogShowLongitude, sidebarVisible);
        setVisible(wCompassCompact, sidebarVisible);
        setVisible(wCompassAttachHotbar, sidebarVisible);

        setVisible(wZoneDisplay, sidebarVisible);
        setVisible(wZoneFollow, sidebarVisible && cfg.displayZoneInHud);
        setVisible(wZoneTextScale, sidebarVisible && cfg.displayZoneInHud);
        setVisible(wBiomeDisplay, sidebarVisible);
        setVisible(wBiomeFollow, sidebarVisible && cfg.displayBiomeInHud);
        setVisible(wBiomeTextScale, sidebarVisible && cfg.displayBiomeInHud);
        // Order only matters when zone AND biome are both attached to the SAME compass line.
        boolean bothAttached = cfg.displayZoneInHud && cfg.zoneFollowsCompass
                && cfg.displayBiomeInHud && cfg.biomeFollowsCompass;
        setVisible(wZoneBiomeOrder, sidebarVisible && bothAttached);
        setVisible(wCoordsFollow, sidebarVisible);

        setVisible(wTitleEnabled, sidebarVisible);
        setVisible(wTitleScale, sidebarVisible);
        setVisible(wTitleDuration, sidebarVisible);
        setVisible(wTitleShowBaseDegrees, sidebarVisible);
        setVisible(wTitleColorPreset, sidebarVisible);
        setRgbGroupVisible(rgbTitleColor, sidebarVisible);
        setVisible(wTitleCase, sidebarVisible);
        setVisible(wTitleLetterSpacing, sidebarVisible);

        setVisible(wHudSnap, sidebarVisible);
        setVisible(wHudSnapPixels, sidebarVisible && LatitudeConfig.hudSnapEnabled);
        setVisible(wTitleDraggable, sidebarVisible);

        setVisible(wResetHud, sidebarVisible);
    }

    private static void setRgbGroupVisible(RgbPickerGroup group, boolean v) {
        if (group == null) return;
        for (AbstractWidget w : group.sliders()) {
            setVisible(w, v);
        }
    }

    private boolean isMouseOverZone(double mx, double my) {
        var mc = Minecraft.getInstance();
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

    // Mirrors isMouseOverZone exactly, for the biome label.
    private boolean isMouseOverBiome(double mx, double my) {
        var mc = Minecraft.getInstance();
        if (mc == null) return false;
        var cfg = CompassHudConfig.get();
        if (!cfg.displayBiomeInHud || cfg.biomeFollowsCompass) return false;
        var b = CompassHud.computeBiomeBounds(mc, cfg);
        if (b == null) return false;
        if (mx < b.x() || mx >= (b.x() + b.w()) || my < b.y() || my >= (b.y() + b.h())) return false;
        biomeGrabDx = (int) Math.round(mx) - b.x();
        biomeGrabDy = (int) Math.round(my) - b.y();
        return true;
    }

    // Mirrors isMouseOverZone exactly, for the detached coords (lat/lon) label.
    private boolean isMouseOverCoords(double mx, double my) {
        var mc = Minecraft.getInstance();
        if (mc == null) return false;
        var cfg = CompassHudConfig.get();
        if (cfg.coordsFollowsCompass) return false;
        var b = CompassHud.computeCoordsBounds(mc, cfg);
        if (b == null) return false;
        if (mx < b.x() || mx >= (b.x() + b.w()) || my < b.y() || my >= (b.y() + b.h())) return false;
        coordsGrabDx = (int) Math.round(mx) - b.x();
        coordsGrabDy = (int) Math.round(my) - b.y();
        return true;
    }

    // Copies every field from a fresh CompassHudConfig instance onto the live (loaded) instance, so "Reset HUD"
    // always matches CompassHudConfig's own field initializers -- there is exactly one place that defines the
    // defaults now. (This used to be a hand-duplicated list of values here that silently drifted out of sync
    // with the class defaults, e.g. still resetting to the pre-2.0 DIGITAL/48.0/0.65 compass instead of the
    // current ANALOG/32.0/0.50 default.)
    private static void applyDefaults(CompassHudConfig cfg) {
        CompassHudConfig fresh = CompassHudConfig.fresh();
        cfg.enabled = fresh.enabled;
        cfg.showMode = fresh.showMode;
        cfg.directionMode = fresh.directionMode;
        cfg.style = fresh.style;
        cfg.analogLook = fresh.analogLook;
        cfg.hAnchor = fresh.hAnchor;
        cfg.vAnchor = fresh.vAnchor;
        cfg.offsetX = fresh.offsetX;
        cfg.offsetY = fresh.offsetY;
        cfg.offXFrac = fresh.offXFrac;
        cfg.offYFrac = fresh.offYFrac;
        cfg.growH = fresh.growH;
        cfg.growV = fresh.growV;
        cfg.dockMode = fresh.dockMode;
        cfg.reservedTextWidth = fresh.reservedTextWidth;
        cfg.layoutVersion = fresh.layoutVersion;
        cfg.scale = fresh.scale;
        cfg.analogSize = fresh.analogSize;
        cfg.analogInnerAlpha = fresh.analogInnerAlpha;
        cfg.analogTheme = fresh.analogTheme;
        cfg.rainbowCycleSeconds = fresh.rainbowCycleSeconds;
        cfg.padding = fresh.padding;
        cfg.showBackground = fresh.showBackground;
        cfg.backgroundRgb = fresh.backgroundRgb;
        cfg.backgroundAlpha = fresh.backgroundAlpha;
        cfg.textRgb = fresh.textRgb;
        cfg.textAlpha = fresh.textAlpha;
        cfg.textRainbow = fresh.textRainbow;
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
        cfg.zoneOffXFrac = fresh.zoneOffXFrac;
        cfg.zoneOffYFrac = fresh.zoneOffYFrac;
        cfg.zoneGrowH = fresh.zoneGrowH;
        cfg.zoneGrowV = fresh.zoneGrowV;
        cfg.zoneOffsetY = fresh.zoneOffsetY;
        cfg.zoneTextScale = fresh.zoneTextScale;
        cfg.displayBiomeInHud = fresh.displayBiomeInHud;
        cfg.biomeFollowsCompass = fresh.biomeFollowsCompass;
        cfg.biomeHAnchor = fresh.biomeHAnchor;
        cfg.biomeVAnchor = fresh.biomeVAnchor;
        cfg.biomeOffsetX = fresh.biomeOffsetX;
        cfg.biomeOffXFrac = fresh.biomeOffXFrac;
        cfg.biomeOffYFrac = fresh.biomeOffYFrac;
        cfg.biomeGrowH = fresh.biomeGrowH;
        cfg.biomeGrowV = fresh.biomeGrowV;
        cfg.biomeOffsetY = fresh.biomeOffsetY;
        cfg.biomeTextScale = fresh.biomeTextScale;
        cfg.biomeBeforeZone = fresh.biomeBeforeZone;
        cfg.coordsFollowsCompass = fresh.coordsFollowsCompass;
        cfg.coordsHAnchor = fresh.coordsHAnchor;
        cfg.coordsVAnchor = fresh.coordsVAnchor;
        cfg.coordsOffsetX = fresh.coordsOffsetX;
        cfg.coordsOffXFrac = fresh.coordsOffXFrac;
        cfg.coordsOffYFrac = fresh.coordsOffYFrac;
        cfg.coordsGrowH = fresh.coordsGrowH;
        cfg.coordsGrowV = fresh.coordsGrowV;
        cfg.coordsOffsetY = fresh.coordsOffsetY;
        cfg.coordsTextScale = fresh.coordsTextScale;
        cfg.customFaceRgb = fresh.customFaceRgb;
        cfg.customRingArgb = fresh.customRingArgb;
        cfg.customMutedArgb = fresh.customMutedArgb;
        cfg.customNeedleArgb = fresh.customNeedleArgb;
    }

    private static void setVisible(AbstractWidget w, boolean v) {
        if (w == null) return;
        w.visible = v;
        w.active = v;
    }

    private boolean isMouseOverCompass(double mx, double my) {
        var mc = Minecraft.getInstance();
        if (mc == null) {
            return false;
        }
        var cfg = CompassHudConfig.get();
        if (cfg.dockMode != CompassHudConfig.DockMode.NONE) {
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

        // U-B: measure the SAME string the preview draws, with case + letter-spacing applied, so the grab
        // box always matches the letters on screen (was a hardcoded "TROPICAL 0\u00b0" measurement).
        String s = studioPreviewTitle(mc);
        int w = ZoneEnterTitleOverlay.styledWidth(mc.font, s);
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

    /** The exact string the title preview renders (shared by the render path and the drag hit-test) --
     *  the single place "Show Degrees" is honored (TEST 32: previously duplicated inline at the render
     *  call site, which never checked the flag, so toggling it visibly did nothing in the Studio).
     *  Natural case ("Tropics", not "TROPICS") in BOTH branches, including the no-world fallback -- an
     *  ALL-CAPS fallback made "Normal" indistinguishable from "UPPERCASE" in exactly the no-world Studio
     *  preview Peetsa opens from the create-world screen (TEST 33: reported as "remove Normal, it's a
     *  duplicate of Uppercase" before he clarified he wants Normal/"Tropical" kept -- the real bug was
     *  this fallback's casing, not the option itself). */
    private static String studioPreviewTitle(Minecraft mc) {
        var level = mc.level;
        var player = mc.player;
        boolean showDegrees = LatitudeConfig.showZoneBaseDegreesOnTitle;
        if (level != null && player != null) {
            var border = level.getWorldBorder();
            String zoneWord = zoneTitleWord(com.example.globe.util.LatitudeMath.zoneKey(border, player.getZ()));
            if (!showDegrees) {
                return zoneWord;
            }
            String degText = com.example.globe.util.LatitudeMath.formatLatitudeDeg(border, player.getZ());
            return zoneWord + " " + degText;
        }
        return showDegrees ? "Tropics 12\u00b0S" : "Tropics";
    }

    /** Natural-case zone title word, matching GlobeWarningOverlay's real title text (so the preview reads like
     *  the real title). Left in natural case rather than forced uppercase so the "Normal" title-case option
     *  actually looks different from "UPPERCASE" -- ZoneEnterTitleOverlay's applyCase() is what controls the
     *  final displayed casing. */
    private static String zoneTitleWord(String zoneKey) {
        return switch (zoneKey) {
            case "EQUATOR", "TROPICAL" -> "Tropics";
            case "SUBTROPICAL" -> "Subtropics";
            case "TEMPERATE" -> "Temperate";
            case "SUBPOLAR" -> "Subpolar";
            case "POLAR" -> "Polar";
            default -> zoneKey == null ? "Tropics" : zoneKey;
        };
    }

    private static int snap(int v, int step) {
        if (step <= 1) return v;
        return Math.round(v / (float) step) * step;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String lookLabel(CompassHudConfig.CompassLook look) {
        return switch (look) {
            case DISC -> "Disc";
            case RING -> "Ring";
            case ROSE -> "Rose";
            case TAPE -> "Tape";
            case MINIMAL -> "Minimal";
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
            case RAINBOW -> "Aurora";
            case CUSTOM -> "Custom";
        };
    }

    private static String titleColorLabel(LatitudeConfigData.TitleColorPreset preset) {
        return switch (preset) {
            case WHITE -> "White";
            case GOLD -> "Gold";
            case RED -> "Red";
            case CYAN -> "Cyan";
            case GREEN -> "Green";
            case CUSTOM -> "Custom";
            case RAINBOW -> "Rainbow";
        };
    }

    // Each label is styled in its own case, so the button doubles as a live preview of the effect.
    private static String titleCaseLabel(LatitudeConfigData.TitleCaseMode mode) {
        return switch (mode) {
            case NORMAL -> "Normal";
            case UPPERCASE -> "UPPERCASE";
            case LOWERCASE -> "lowercase";
            case MOCKING -> "mOcKiNg";
        };
    }

    private static void tooltip(AbstractWidget w, String text) {
        if (w == null) return;
        Tooltip t = Tooltip.create(Component.literal(text));
        w.setTooltip(t);
        if (w instanceof CycleButton<?> cycle) {
            patchCycleButtonTooltip(cycle, t);
        }
    }

    /**
     * CycleButton has its OWN internal tooltip-refresh hook: a private {@code tooltipSupplier} field,
     * consulted by a private {@code updateTooltip()} that vanilla calls automatically on every value
     * change (i.e. every click/cycle) -- unconditionally, no null-check: {@code setTooltip(tooltipSupplier
     * .apply(value))}. This codebase attaches every tooltip externally via {@link #tooltip} instead of the
     * builder's {@code withTooltip(...)}, so that field is left at its no-op default (returns null) --
     * meaning the FIRST click on ANY CycleButton silently overwrites our tooltip with null, permanently
     * (TEST 34: "after you click on a button and go back and hover over it, the tooltip is gone and will
     * not come back" -- exactly this, on every dropdown-style control in the Studio, confirmed against the
     * decompiled 26.2 CycleButton class). Patches the field via reflection (CycleButton exposes no public
     * setter post-construction; the builder-time {@code withTooltip} would mean touching every one of the
     * ~30 call sites in this screen instead of this one shared helper) to a constant supplier returning
     * THIS tooltip, so the button's own auto-refresh re-applies it instead of wiping it. Every tooltip
     * here is static descriptive text (doesn't vary by the button's current value), so "always return the
     * same Tooltip" is exactly the desired behavior, not an approximation. Falls back to the (buggy-after-
     * one-click) external-only behavior if the field is ever renamed/retyped by a future mapping update,
     * rather than crashing.
     */
    private static void patchCycleButtonTooltip(CycleButton<?> cycle, Tooltip t) {
        try {
            java.lang.reflect.Field f = CycleButton.class.getDeclaredField("tooltipSupplier");
            f.setAccessible(true);
            f.set(cycle, (net.minecraft.client.OptionInstance.TooltipSupplier<Object>) v -> t);
        } catch (ReflectiveOperationException ignored) {
            // Field renamed/retyped upstream -- degrade to the pre-existing (still functional on first
            // hover, just not after a click) behavior rather than throw.
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
        private final IntConsumer onChange;

        private IntSlider(int x, int y, int width, int height, Component label, int min, int max, int initial, IntConsumer onChange) {
            super(x, y, width, height, Component.empty(), toNorm(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(label.getString() + ": " + getValue()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(getValue());
        }

        private int getValue() {
            return Mth.clamp((int) Math.round(min + (max - min) * this.value), min, max);
        }

        private static double toNorm(int v, int min, int max) {
            if (max == min) return 0.0;
            return (double) (v - min) / (double) (max - min);
        }
    }

    private static final class FloatSlider extends AbstractSliderButton {
        private final Component label;
        private final float min;
        private final float max;
        private final FloatConsumer onChange;
        // Explicit click-drag-release tracking (TEST 30): vanilla's own private `dragging` field has no
        // public getter, and its `isFocused()` is sticky past mouseUp -- unusable as a "currently being
        // adjusted" signal. This mirrors the click/release pair exactly.
        private boolean dragging;

        private FloatSlider(int x, int y, int width, int height, Component label, float min, float max, float initial, FloatConsumer onChange) {
            super(x, y, width, height, Component.empty(), toNorm(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        boolean isDragging() {
            return dragging;
        }

        @Override
        public void onClick(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
            dragging = true;
            super.onClick(click, doubled);
        }

        @Override
        public void onRelease(net.minecraft.client.input.MouseButtonEvent click) {
            dragging = false;
            super.onRelease(click);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(label.getString() + ": " + format(getValue())));
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

    // Three stacked 0-255 IntSliders (R/G/B) that recombine into a packed 0xRRGGBB int on every change. Reuses
    // IntSlider directly since this is a nested class of the same top-level type.
    private static final class RgbPickerGroup {
        private final IntSlider rSlider;
        private final IntSlider gSlider;
        private final IntSlider bSlider;
        private int r;
        private int g;
        private int b;

        private RgbPickerGroup(int x, int y, int width, int rowH, int rowGap, String label, int initialRgb, IntConsumer onChangeRgb) {
            this.r = (initialRgb >> 16) & 0xFF;
            this.g = (initialRgb >> 8) & 0xFF;
            this.b = initialRgb & 0xFF;
            this.rSlider = new IntSlider(x, y, width, rowH, Component.literal(label + " R"), 0, 255, r,
                    v -> { this.r = v; onChangeRgb.accept(pack()); });
            this.gSlider = new IntSlider(x, y + (rowH + rowGap), width, rowH, Component.literal(label + " G"), 0, 255, g,
                    v -> { this.g = v; onChangeRgb.accept(pack()); });
            this.bSlider = new IntSlider(x, y + 2 * (rowH + rowGap), width, rowH, Component.literal(label + " B"), 0, 255, b,
                    v -> { this.b = v; onChangeRgb.accept(pack()); });
        }

        private int pack() {
            return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        }

        private int color() {
            return pack();
        }

        private AbstractWidget[] sliders() {
            return new AbstractWidget[] { rSlider, gSlider, bSlider };
        }
    }

    // A non-widget color-preview row drawn via ctx.fill each frame, scrolled/clipped the same way tracked
    // widgets are (see drawSidebarSwatches). baseY matches the convention trackSidebarWidget() uses for widgets.
    private static final class SwatchSlot {
        private final int baseY;
        private final int height;
        private final IntSupplier color;

        private SwatchSlot(int baseY, int height, IntSupplier color) {
            this.baseY = baseY;
            this.height = height;
            this.color = color;
        }
    }
}
