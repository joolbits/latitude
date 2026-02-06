package com.example.globe.client;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import net.minecraft.util.Formatting;

public enum GlobeWorldSize {
    ITTY_BITTY(Text.literal("Itty Bitty (7,500 x 7,500)"), new Identifier("globe", "globe_xsmall"), 3750),
    TINY(Text.literal("Tiny (10,000 x 10,000)"),           new Identifier("globe", "globe_small"), 5000),
    SMALL(Text.literal("Small (15,000 x 15,000)"),         new Identifier("globe", "globe_regular"), 7500),

    REGULAR(Text.literal("Regular (20,000 x 20,000)"),     new Identifier("globe", "globe_large"), 10000),
    LARGE(Text.literal("Large (30,000 x 30,000)"),         new Identifier("globe", "globe"), 15000),
    MASSIVE(
            Text.literal("Ginormous! (40,000 x 40,000)").formatted(Formatting.ITALIC),
            new Identifier("globe", "globe_massive"),
            20000
    );

    public final Text label;
    public final Identifier worldPresetId;
    public final int borderRadiusBlocks;

    GlobeWorldSize(Text label, Identifier worldPresetId, int borderRadiusBlocks) {
        this.label = label;
        this.worldPresetId = worldPresetId;
        this.borderRadiusBlocks = borderRadiusBlocks;
    }
}
