package com.example.globe.client;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public enum GlobeWorldSize {
    ITTY_BITTY("Itty Bitty (7,500 x 7,500)", Identifier.of("globe", "globe_xsmall"), 3750),
    TINY("Tiny (10,000 x 10,000)",           Identifier.of("globe", "globe_small"), 5000),
    SMALL("Small (15,000 x 15,000)",         Identifier.of("globe", "globe_regular"), 7500),

    REGULAR("Regular (20,000 x 20,000)",     Identifier.of("globe", "globe_large"), 10000),
    LARGE("Large (30,000 x 30,000)",         Identifier.of("globe", "globe"), 15000),

    MASSIVE("Massive (40,000 x 40,000)",     Identifier.of("globe", "globe_massive"), 20000);

    public final Text label;
    public final Identifier worldPresetId;
    public final int borderRadiusBlocks;

    GlobeWorldSize(String label, Identifier worldPresetId, int borderRadiusBlocks) {
        this.label = Text.literal(label);
        this.worldPresetId = worldPresetId;
        this.borderRadiusBlocks = borderRadiusBlocks;
    }
}
