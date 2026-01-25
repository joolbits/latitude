package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

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
        LatitudeConfig.get();

        this.layoutWidgets.clear();
        this.layoutBaseYs.clear();

        int y = 28;
        int w = 220;
        int h = 20;

        final int columnX = (this.width - w) / 2;

        int baseY;

        baseY = y;
        var wShowMode = this.addDrawableChild(CyclingButtonWidget.<CompassHudConfig.ShowMode>builder(this::showModeLabel, () -> cfg.showMode)
                .values(CompassHudConfig.ShowMode.values())
                .build(columnX, y, w, h, Text.literal("Show Mode"), (btn, value) -> cfg.showMode = value));
        layoutWidgets.add(wShowMode);
        layoutBaseYs.add(baseY);
        y += 24;

        baseY = y;
        var wStudio = this.addDrawableChild(ButtonWidget.builder(Text.literal("HUD Studio"), b -> {
                    MinecraftClient.getInstance().setScreen(new LatitudeHudStudioScreen(this));
                })
                .dimensions(columnX, y, w, 20)
                .build());
        layoutWidgets.add(wStudio);
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
                    applyDefaults(LatitudeConfig.get());
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

    private Text showModeLabel(CompassHudConfig.ShowMode v) {
        if (v == null) return Text.literal("Always");
        return switch (v) {
            case COMPASS_PRESENT -> Text.literal("When compass is in inventory");
            case HOLDING_COMPASS -> Text.literal("When holding compass");
            case ALWAYS -> Text.literal("Always");
        };
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
}
