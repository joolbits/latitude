package com.example.globe.client;

import com.example.globe.client.ui.ZoneTitleOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ZoneEntryNotifier {
    private ZoneEntryNotifier() {
    }

    public static void onZoneEntered(Minecraft client, String zoneKey) {
        if (client == null) {
            return;
        }

        LatitudeConfig.ZoneEntryNotifyMode mode = LatitudeConfig.zoneEntryNotifyMode;
        if (mode == LatitudeConfig.ZoneEntryNotifyMode.OFF) {
            return;
        }

        if (client.level == null || client.player == null) {
            return;
        }

        if (mode == LatitudeConfig.ZoneEntryNotifyMode.TITLE) {
            String zoneName = zoneDisplayName(zoneKey).toUpperCase();
            Component title = Component.literal(zoneName);

            Component subtitle = null;
            if (LatitudeConfig.showLatitudeDegrees) {
                String degText = LatitudeMath.formatLatitudeDeg(client.player.getZ(), client.level.getWorldBorder());
                subtitle = Component.literal(degText);
            }

            ZoneTitleOverlay.show(title, subtitle);
        }
    }

    private static String zoneDisplayName(String zoneKey) {
        return switch (zoneKey) {
            case "EQUATOR", "TROPICAL" -> "Tropics";
            case "SUBTROPICAL" -> "Subtropics";
            case "TEMPERATE" -> "Temperate";
            case "SUBPOLAR" -> "Subpolar";
            case "POLAR" -> "Polar";
            default -> zoneKey;
        };
    }
}
