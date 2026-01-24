package com.example.globe.client;

public final class GlobeWorldSizeSelection {
    private static GlobeWorldSize selected = GlobeWorldSize.REGULAR;

    private GlobeWorldSizeSelection() {
    }

    public static GlobeWorldSize get() {
        return selected;
    }

    public static void set(GlobeWorldSize size) {
        if (size != null) {
            selected = size;
        }
    }
}
