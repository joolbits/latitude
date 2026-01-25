package com.example.globe.client;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public enum GlobeWorldSize {
    SMALL("Small (7,500 x 7,500)",       Identifier.of("globe", "globe_xsmall"), 3750),
    REGULAR("Regular (10,000 x 10,000)", Identifier.of("globe", "globe_small"),  5000),
    LARGE("Large (15,000 x 15,000)",     Identifier.of("globe", "globe_regular"), 7500);

    public final Text label;
    public final Identifier worldPresetId;
    public final int borderRadiusBlocks;

    GlobeWorldSize(String label, Identifier worldPresetId, int borderRadiusBlocks) {
        this.label = Text.literal(label);
        this.worldPresetId = worldPresetId;
        this.borderRadiusBlocks = borderRadiusBlocks;
    }
}
