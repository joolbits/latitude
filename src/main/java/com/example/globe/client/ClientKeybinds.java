package com.example.globe.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class ClientKeybinds {
    // 1.21.1 has no KeyMapping.Category registry: the category is the raw translation key.
    public static final String CATEGORY = "key.categories.globe";

    public static KeyMapping TOGGLE_COMPASS;
    public static KeyMapping OPEN_SETTINGS;

    private ClientKeybinds() {}

    public static void init() {
        TOGGLE_COMPASS = new KeyMapping(
                "key.globe.toggle_compass_hud",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_COMMA,
                CATEGORY
        );

        OPEN_SETTINGS = new KeyMapping(
                "key.globe.open_settings",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_F9,
                CATEGORY
        );
    }
}
