package com.example.globe.client;

import com.example.globe.GlobeNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class SpawnZoneScreen extends Screen {
    public SpawnZoneScreen() {
        super(Component.literal("Choose Starting Latitude"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 60;

        addZoneButton(cx, y, rainbowRandomText(), "RANDOM");
        y += 22;
        addZoneButton(cx, y, "Tropical", "TROPICAL");
        y += 22;
        addZoneButton(cx, y, "Subtropical", "SUBTROPICAL");
        y += 22;
        addZoneButton(cx, y, "Temperate", "TEMPERATE");
        y += 22;
        addZoneButton(cx, y, "Subpolar", "SUBPOLAR");
        y += 22;
        addZoneButton(cx, y, "Polar", "POLAR");
        y += 30;

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cx - 50, y, 100, 20)
                .build());
    }

    private void addZoneButton(int cx, int y, String label, String id) {
        this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    ClientPlayNetworking.send(new GlobeNet.SetSpawnPickerPayload(id));
                    onClose();
                })
                .bounds(cx - 90, y, 180, 20)
                .build());
    }

    private void addZoneButton(int cx, int y, Component label, String id) {
        this.addRenderableWidget(Button.builder(label, b -> {
                    ClientPlayNetworking.send(new GlobeNet.SetSpawnPickerPayload(id));
                    onClose();
                })
                .bounds(cx - 90, y, 180, 20)
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
            this.minecraft.setScreen(null);
        }
    }
}
