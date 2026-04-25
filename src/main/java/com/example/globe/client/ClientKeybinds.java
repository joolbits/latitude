package com.example.globe.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class ClientKeybinds {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("globe", "globe"));

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
