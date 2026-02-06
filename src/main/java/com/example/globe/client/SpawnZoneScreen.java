package com.example.globe.client;

import com.example.globe.GlobeNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;

public class SpawnZoneScreen extends Screen {
    public SpawnZoneScreen() {
        super(Text.literal("Choose Starting Latitude"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 60;

        addZoneButton(cx, y, rainbowRandomText(), "RANDOM");
        y += 22;
        addZoneButton(cx, y, "Equatorial", "EQUATOR");
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

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(cx - 50, y, 100, 20)
                .build());
    }

    private void addZoneButton(int cx, int y, String label, String id) {
        this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                    ClientPlayNetworking.send(GlobeNet.C2S_SET_SPAWN_PICKER, new GlobeNet.SetSpawnPickerPayload(id).write());
                    close();
                })
                .dimensions(cx - 90, y, 180, 20)
                .build());
    }

    private void addZoneButton(int cx, int y, Text label, String id) {
        this.addDrawableChild(ButtonWidget.builder(label, b -> {
                    ClientPlayNetworking.send(GlobeNet.C2S_SET_SPAWN_PICKER, new GlobeNet.SetSpawnPickerPayload(id).write());
                    close();
                })
                .dimensions(cx - 90, y, 180, 20)
                .build());
    }

    private static Text rainbowRandomText() {
        Formatting[] colors = {
                Formatting.RED,
                Formatting.GOLD,
                Formatting.YELLOW,
                Formatting.GREEN,
                Formatting.AQUA,
                Formatting.BLUE,
                Formatting.LIGHT_PURPLE
        };

        String s = "Random";
        MutableText out = Text.empty();
        for (int i = 0; i < s.length(); i++) {
            out.append(Text.literal(String.valueOf(s.charAt(i))).formatted(colors[i % colors.length]));
        }
        return out.formatted(Formatting.ITALIC);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }
}
