package com.example.globe.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class BiomeColorUtil {
    private static final Map<String, Integer> PALETTE_OVERRIDES = loadPaletteOverrides();

    private BiomeColorUtil() {
    }

    public static int stableColorForBiomeId(String biomeId) {
        String id = biomeId.toLowerCase(Locale.ROOT);
        Integer override = paletteOverrideFor(id);
        if (override != null) {
            return override;
        }
        if (id.contains("snowy_beach")) {
            return 0xE9E1CC;
        }
        if (id.contains("stony_shore")) {
            return 0x9A9A9A;
        }
        if (id.contains("beach") || id.contains("shore") || id.contains("coast")) {
            return 0xE7D7A5;
        }
        if (id.contains("ocean") || id.contains("river")) {
            return 0x2F6FA8;
        }
        if (id.contains("snow") || id.contains("frozen") || id.contains("ice")) {
            return 0xE6F4FF;
        }
        if (id.contains("desert") || id.contains("badlands")) {
            return 0xD39B4D;
        }
        if (id.contains("swamp") || id.contains("mangrove")) {
            return 0x3C6B43;
        }
        if (id.contains("jungle")) {
            return 0x2E8A57;
        }
        if (id.contains("taiga") || id.contains("forest") || id.contains("grove")) {
            return 0x4A7B4D;
        }
        if (id.contains("plains") || id.contains("savanna") || id.contains("meadow")) {
            return 0x8FBF63;
        }
        if (id.contains("mountain") || id.contains("peak") || id.contains("hills")) {
            return 0x7A7A7A;
        }

        return 0x8A8A8A;
    }

    private static Integer paletteOverrideFor(String biomeId) {
        if (biomeId == null) {
            return null;
        }
        String normalized = biomeId.toLowerCase(Locale.ROOT);
        Integer override = PALETTE_OVERRIDES.get(normalized);
        if (override != null) {
            return override;
        }
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            String shortId = normalized.substring(colon + 1);
            override = PALETTE_OVERRIDES.get(shortId);
            if (override != null) {
                return override;
            }
        }
        return null;
    }

    private static Map<String, Integer> loadPaletteOverrides() {
        Map<String, Integer> overrides = new HashMap<>();
        Path candidate = Path.of("tools", "atlas", "palette_authority.json");
        if (Files.exists(candidate)) {
            try (Reader reader = Files.newBufferedReader(candidate)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed != null && parsed.isJsonObject()) {
                    JsonObject root = parsed.getAsJsonObject();
                    JsonObject biomes = root.getAsJsonObject("biomes");
                    if (biomes != null) {
                        for (Map.Entry<String, JsonElement> entry : biomes.entrySet()) {
                            JsonElement val = entry.getValue();
                            if (val != null && val.isJsonPrimitive()) {
                                Integer parsedColor = parseHexColor(val.getAsString());
                                if (parsedColor != null) {
                                    overrides.put(entry.getKey().toLowerCase(Locale.ROOT), parsedColor);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[LAT][ATLAS] palette_authority.json parse failed: " + e.getMessage());
            }
        }

        overrides.putIfAbsent("minecraft:beach", 0xE0C097);
        overrides.putIfAbsent("minecraft:snowy_beach", 0xE9E1CC);
        overrides.putIfAbsent("minecraft:stony_shore", 0x9A9A9A);
        overrides.putIfAbsent("minecraft:badlands", 0xD47F34);
        overrides.putIfAbsent("minecraft:wooded_badlands", 0xB86832);
        overrides.putIfAbsent("minecraft:eroded_badlands", 0xE3B35A);

        return Collections.unmodifiableMap(overrides);
    }

    private static Integer parseHexColor(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        try {
            int rgb = Integer.parseInt(cleaned, 16);
            if ((rgb & 0xFF000000) != 0) {
                rgb = rgb & 0x00FFFFFF;
            }
            return rgb;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
