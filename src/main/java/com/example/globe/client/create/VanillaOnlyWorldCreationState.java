package com.example.globe.client.create;

import java.util.List;

/**
 * Internal marker for a vanilla create-world session opened from Latitude.
 *
 * <p>A session carrying this flag hides the Globe preset from both world-type lists. That is the
 * whole point of the escape hatch: the vanilla screen exists to reach the world types Latitude's
 * own screen does not offer, and offering Globe there would invite a Latitude world created
 * through a screen that never collected Latitude's settings.</p>
 */
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
