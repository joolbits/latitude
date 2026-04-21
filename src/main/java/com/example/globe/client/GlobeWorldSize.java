package com.example.globe.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public enum GlobeWorldSize {
    ITTY_BITTY(Component.literal("Itty Bitty (7,500 x 7,500)"), Identifier.fromNamespaceAndPath("globe", "globe_xsmall"), 3750),
    TINY(Component.literal("Tiny (10,000 x 10,000)"),           Identifier.fromNamespaceAndPath("globe", "globe_small"), 5000),
    SMALL(Component.literal("Small (15,000 x 15,000)"),         Identifier.fromNamespaceAndPath("globe", "globe_regular"), 7500),

    REGULAR(Component.literal("Regular (20,000 x 20,000)"),     Identifier.fromNamespaceAndPath("globe", "globe_large"), 10000),
    LARGE(Component.literal("Large (30,000 x 30,000)"),         Identifier.fromNamespaceAndPath("globe", "globe"), 15000),
    MASSIVE(
            Component.literal("Ginormous! (40,000 x 40,000)").withStyle(ChatFormatting.ITALIC),
            Identifier.fromNamespaceAndPath("globe", "globe_massive"),
            20000
    );

    public final Component label;
    public final Identifier worldPresetId;
    public final int borderRadiusBlocks;

    GlobeWorldSize(Component label, Identifier worldPresetId, int borderRadiusBlocks) {
        this.label = label;
        this.worldPresetId = worldPresetId;
        this.borderRadiusBlocks = borderRadiusBlocks;
    }
}
