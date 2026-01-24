package com.example.globe.client;

import com.example.globe.client.ui.ZoneTitleOverlay;
import com.example.globe.util.LatitudeMath;
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
                int deg = LatitudeMath.latitudeDegrees(client.world.getWorldBorder(), client.player.getZ());
                if (deg == 0) {
                    subtitle = Text.literal("0\u00b0");
                } else {
                    char hemi = LatitudeMath.hemisphere(client.world.getWorldBorder(), client.player.getZ());
                    subtitle = Text.literal(deg + "\u00b0" + hemi);
                }
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
