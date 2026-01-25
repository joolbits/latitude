package com.example.globe.client;

import com.example.globe.client.ui.ZoneTitleOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class ZoneEntryNotifier {
    private ZoneEntryNotifier() {
    }

    public static void onZoneEntered(MinecraftClient client, String zoneKey) {
        if (client == null) {
            return;
        }

        LatitudeConfig.ZoneEntryNotifyMode mode = LatitudeConfig.zoneEntryNotifyMode;
        if (mode == LatitudeConfig.ZoneEntryNotifyMode.OFF) {
            return;
        }

        if (client.world == null || client.player == null) {
            return;
        }

        if (mode == LatitudeConfig.ZoneEntryNotifyMode.TITLE) {
            String zoneName = zoneDisplayName(zoneKey).toUpperCase();
            Text title = Text.literal(zoneName);

            Text subtitle = null;
            if (LatitudeConfig.showLatitudeDegrees) {
                String degText = LatitudeMath.formatLatitudeDeg(client.player.getZ(), client.world.getWorldBorder());
                subtitle = Text.literal(degText);
            }

            ZoneTitleOverlay.show(title, subtitle);
        }
    }

    private static String zoneDisplayName(String zoneKey) {
        return switch (zoneKey) {
            case "EQUATOR" -> "Equator";
            case "TROPICAL" -> "Tropics";
            case "SUBTROPICAL" -> "Subtropics";
            case "TEMPERATE" -> "Temperate";
            case "SUBPOLAR" -> "Subpolar";
            case "POLAR" -> "Polar";
            default -> zoneKey;
        };
    }
}
