package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

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

    public LatitudeHudAdjustScreen(Screen parent) {
        super(Text.literal("Adjust HUD Position"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int bw = 200;
        int bh = 20;
        int x = (this.width - bw) / 2;
        int y = this.height - 28;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Adjust: BOTH"), btn -> {
                    mode = switch (mode) {
                        case BOTH -> Mode.TITLE;
                        case TITLE -> Mode.COMPASS;
                        case COMPASS -> Mode.BOTH;
                    };
                    btn.setMessage(Text.literal("Adjust: " + mode.name()));
                })
                .dimensions(8, 28, 140, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> {
                    LatitudeConfig.saveCurrent();
                    CompassHudConfig.saveCurrent();
                    MinecraftClient.getInstance().setScreen(parent);
                })
                .dimensions(x, y, bw, bh)
                .build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderInGameBackground(ctx);
        ctx.fill(0, 0, this.width, this.height, 0x66000000);

        ctx.drawText(this.textRenderer,
                "Drag the zone title to reposition. Click Done when finished.",
                8,
                8,
                0xFFFFFF,
                true);

        var mc = MinecraftClient.getInstance();
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

        if (mode == Mode.COMPASS || mode == Mode.BOTH) {
            CompassHud.renderAdjustPreview(ctx, this.width, this.height);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
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
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
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
        String s = "EQUATOR 0\u00b0";
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
}
