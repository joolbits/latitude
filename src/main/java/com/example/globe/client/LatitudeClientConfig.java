package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class LatitudeClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final LatitudeClientConfig INSTANCE = new LatitudeClientConfig();

    public boolean showFirstLoadMessage = true;

    private LatitudeClientConfig() {
    }

    public static LatitudeClientConfig get() {
        return INSTANCE;
    }

    public void loadOrCreate() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("latitude.json");
        if (!Files.exists(path)) {
            save();
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            LatitudeClientConfig loaded = GSON.fromJson(json, LatitudeClientConfig.class);
            if (loaded == null) {
                throw new JsonSyntaxException("null config");
            }
            this.showFirstLoadMessage = loaded.showFirstLoadMessage;
        } catch (IOException | JsonSyntaxException e) {
            GlobeMod.LOGGER.warn("[Latitude] Failed to load latitude.json, resetting to defaults: {}", e.toString());
            this.showFirstLoadMessage = true;
            save();
        }
    }

    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("latitude.json");
        try {
            String json = GSON.toJson(this);
            Files.createDirectories(path.getParent());
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            GlobeMod.LOGGER.warn("[Latitude] Failed to save latitude.json: {}", e.toString());
        }
    }
}
