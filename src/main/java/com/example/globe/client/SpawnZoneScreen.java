package com.example.globe.client;

import com.example.globe.GlobeNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class SpawnZoneScreen extends Screen {
    public SpawnZoneScreen() {
        super(Text.literal("Choose Starting Latitude"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 60;

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
                    ClientPlayNetworking.send(new GlobeNet.SetSpawnPickerPayload(id));
                    close();
                })
                .dimensions(cx - 90, y, 180, 20)
                .build());
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }
}
