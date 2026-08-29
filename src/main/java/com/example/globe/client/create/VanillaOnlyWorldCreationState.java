package com.example.globe.client.create;

import java.util.List;

/** Internal marker for a vanilla create-world session opened from Latitude. */
public interface VanillaOnlyWorldCreationState {
    boolean globe$isVanillaOnly();

    void globe$setVanillaOnly(boolean vanillaOnly);

    static <T> void removeFromBothPresetLists(List<T> normal, List<T> alternate, T excluded) {
        normal.removeIf(excluded::equals);
        alternate.removeIf(excluded::equals);
    }

    static <T> void ensureInBothPresetLists(List<T> normal, List<T> alternate, T required) {
        ensureAtPreferredIndex(normal, required);
        ensureAtPreferredIndex(alternate, required);
    }

    private static <T> void ensureAtPreferredIndex(List<T> presets, T required) {
        if (!presets.contains(required)) {
            presets.add(presets.isEmpty() ? 0 : 1, required);
        }
    }
}
