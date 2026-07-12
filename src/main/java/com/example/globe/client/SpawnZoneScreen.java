package com.example.globe.client;

import com.example.globe.GlobeNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * In-world spawn-zone picker (opened by the server's spawn-picker payload). U-E Chartroom restyle:
 * the screen now says what it is (gold title + hint — it previously showed seven bare buttons with no
 * heading), and each zone button carries its climate color and a what-you'll-find tooltip.
 */
public class SpawnZoneScreen extends Screen {
    private static final int GOLD = 0xFFE8B64A;
    private static final int MUTED = 0xFF8C8078;

    public SpawnZoneScreen() {
        super(Component.literal("Choose Starting Latitude"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 60;

        addZoneButton(cx, y, rainbowRandomText(), "RANDOM",
                "Throw the compass and see where it lands.");
        y += 22;
        // Degree ranges use the en-dash convention "low°–high°" to match the create screen
        // (LatitudeCreateWorldScreen.formatDegree drops the decimal on whole degrees: 35.0 -> "35°").
        // These endpoints mirror LatitudeBands.Band (0/23.5/35/50/66.5/90) but are still HARDCODED
        // literals here — formatDegree is package-private in client.create and unreachable from this
        // package, so a shared-source refactor is deferred to the future zone-copy consolidation pass.
        addZoneButton(cx, y, zoneLabel("Tropical", ChatFormatting.GREEN), "TROPICAL",
                "Jungles, bamboo, warm seas. 0°–23.5°.");
        y += 22;
        addZoneButton(cx, y, zoneLabel("Subtropical", ChatFormatting.YELLOW), "SUBTROPICAL",
                "Savannas, deserts, badlands. 23.5°–35°.");
        y += 22;
        addZoneButton(cx, y, zoneLabel("Temperate", ChatFormatting.DARK_AQUA), "TEMPERATE",
                "Forests, plains, birch and oak. 35°–50°.");
        y += 22;
        addZoneButton(cx, y, zoneLabel("Subpolar", ChatFormatting.AQUA), "SUBPOLAR",
                "Taiga, spruce, first snows. 50°–66.5°.");
        y += 22;
        addZoneButton(cx, y, zoneLabel("Polar", ChatFormatting.WHITE), "POLAR",
                "Ice, snowfields, frozen seas. 66.5°–90°.");
        y += 30;

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cx - 50, y, 100, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int titleY = this.height / 2 - 88;
        String title = "CHOOSE STARTING LATITUDE";
        ctx.text(this.font, title, cx - this.font.width(title) / 2, titleY, GOLD, true);
        String hint = "The climate band where your expedition begins";
        ctx.text(this.font, hint, cx - this.font.width(hint) / 2, titleY + 12, MUTED, false);
    }

    private static MutableComponent zoneLabel(String name, ChatFormatting color) {
        return Component.literal(name).withStyle(color);
    }

    private void addZoneButton(int cx, int y, Component label, String id, String tooltip) {
        this.addRenderableWidget(Button.builder(label, b -> {
                    ClientPlayNetworking.send(new GlobeNet.SetSpawnPickerPayload(id));
                    onClose();
                })
                .bounds(cx - 90, y, 180, 20)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build());
    }

    private static Component rainbowRandomText() {
        ChatFormatting[] colors = {
                ChatFormatting.RED,
                ChatFormatting.GOLD,
                ChatFormatting.YELLOW,
                ChatFormatting.GREEN,
                ChatFormatting.AQUA,
                ChatFormatting.BLUE,
                ChatFormatting.LIGHT_PURPLE
        };

        String s = "Random";
        MutableComponent out = Component.empty();
        for (int i = 0; i < s.length(); i++) {
            out.append(Component.literal(String.valueOf(s.charAt(i))).withStyle(colors[i % colors.length]));
        }
        return out.withStyle(ChatFormatting.ITALIC);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(null);
        }
    }
}
