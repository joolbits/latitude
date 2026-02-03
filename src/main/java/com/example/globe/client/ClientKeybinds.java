package com.example.globe.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class ClientKeybinds {
    public static final String CATEGORY = "key.categories.globe";

    public static KeyBinding TOGGLE_COMPASS;
    public static KeyBinding OPEN_SETTINGS;

    private ClientKeybinds() {}

    public static void init() {
        TOGGLE_COMPASS = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.globe.toggle_compass_hud",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_COMMA,
                CATEGORY
        ));

        OPEN_SETTINGS = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.globe.open_settings",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_F9,
                CATEGORY
        ));
    }
}
