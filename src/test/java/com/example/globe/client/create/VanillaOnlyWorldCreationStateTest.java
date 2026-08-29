package com.example.globe.client.create;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VanillaOnlyWorldCreationStateTest {
    @Test
    void normalModeEnsuresGlobeExistsExactlyOnceWithoutDroppingOtherPresets() {
        List<String> normal = new ArrayList<>(List.of("normal", "modded"));
        List<String> alternate = new ArrayList<>(List.of("flat", "amplified"));

        VanillaOnlyWorldCreationState.ensureInBothPresetLists(normal, alternate, "globe");
        VanillaOnlyWorldCreationState.ensureInBothPresetLists(normal, alternate, "globe");

        assertEquals(List.of("normal", "globe", "modded"), normal);
        assertEquals(List.of("flat", "globe", "amplified"), alternate);
    }

    @Test
    void removesOnlyGlobeFromBothListsAndRemainsIdempotent() {
        List<String> normal = new ArrayList<>(List.of("normal", "globe", "modded"));
        List<String> alternate = new ArrayList<>(List.of("flat", "globe", "amplified"));

        VanillaOnlyWorldCreationState.removeFromBothPresetLists(normal, alternate, "globe");
        VanillaOnlyWorldCreationState.removeFromBothPresetLists(normal, alternate, "globe");

        assertEquals(List.of("normal", "modded"), normal);
        assertEquals(List.of("flat", "amplified"), alternate);
    }
}
