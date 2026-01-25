package com.example.globe.client;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public enum GlobeWorldSize {
    XSMALL("Small (7,500 × 7,500)",      Identifier.of("globe", "globe_xsmall"), 3750),
    SMALL("Default (10,000 × 10,000)",   Identifier.of("globe", "globe_small"),  5000),
    REGULAR("Large (15,000 × 15,000)",   Identifier.of("globe", "globe_regular"), 7500);

    public final Text label;
    public final Identifier worldPresetId;
    public final int borderRadiusBlocks;

    GlobeWorldSize(String label, Identifier worldPresetId, int borderRadiusBlocks) {
        this.label = Text.literal(label);
        this.worldPresetId = worldPresetId;
        this.borderRadiusBlocks = borderRadiusBlocks;
    }
}
