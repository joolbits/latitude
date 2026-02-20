package com.example.globe.client;

import com.example.globe.util.LatitudeMath;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.border.WorldBorder;

import java.util.List;
import java.util.Locale;

public final class UlOverlayTextBuilder {
    private static final int[] PRESET_DIAMETERS = {7500, 10000, 15000, 20000, 30000, 40000};
    private static final String[] PRESET_NAMES = {"Itty Bitty", "Tiny", "Small", "Regular", "Large", "Ginormous"};

    private UlOverlayTextBuilder() {
    }

    public static List<String> buildLines(MinecraftClient client) {
        var player = client.player;
        var world = client.world;
        if (player == null || world == null) {
            return List.of(
                    "seed: ?",
                    "xyz: ?",
                    "chunk: ?",
                    "lat: ?",
                    "band: ?",
                    "world: ?",
                    "biome: ?",
                    "version: " + resolveModVersion()
            );
        }

        BlockPos pos = player.getBlockPos();
        WorldBorder border = world.getWorldBorder();

        String seed = resolveSeed(client);
        String biomeId = world.getBiome(pos)
                .getKey()
                .map(key -> key.getValue().toString())
                .orElse("?");

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        double latSignedDeg = LatitudeMath.degreesFromZ(border, pos.getZ());
        double latAbsDeg = Math.abs(latSignedDeg);
        String hemi = latAbsDeg < 0.0001 ? "" : String.valueOf(LatitudeMath.hemisphere(border, pos.getZ()));

        String band = LatitudeMath.zoneFor(border, pos.getZ()).name();

        double diameter = border.getSize();
        double radius = LatitudeMath.halfSize(border);
        String preset = nearestPresetName((int) Math.round(diameter));

        String version = resolveModVersion();

        return List.of(
                "seed: " + seed,
                "xyz: " + pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                "chunk: " + chunkX + "," + chunkZ,
                String.format(Locale.ROOT, "lat: %.2f deg%s", latAbsDeg, hemi),
                "band: " + band,
                String.format(Locale.ROOT, "world: %s D=%d R=%d", preset, Math.round(diameter), Math.round(radius)),
                "biome: " + biomeId,
                "version: " + version
        );
    }

    private static String resolveSeed(MinecraftClient client) {
        var server = client.getServer();
        if (server != null && server.getOverworld() != null) {
            return Long.toString(server.getOverworld().getSeed());
        }
        return "?";
    }

    private static String resolveModVersion() {
        return FabricLoader.getInstance()
                .getModContainer("globe")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    private static String nearestPresetName(int diameter) {
        int bestIndex = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < PRESET_DIAMETERS.length; i++) {
            int dist = Math.abs(PRESET_DIAMETERS[i] - diameter);
            if (dist < bestDistance) {
                bestDistance = dist;
                bestIndex = i;
            }
        }
        return PRESET_NAMES[bestIndex];
    }
}
