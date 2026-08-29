package com.example.globe.client.create;

import java.util.ArrayList;
import java.util.List;

final class VanillaOnlyWorldCreationStateTest {
    private static int assertions;

    private VanillaOnlyWorldCreationStateTest() {
    }

    static int run() {
        assertions = 0;
        normalModeEnsuresGlobeExistsExactlyOnceWithoutDroppingOtherPresets();
        removesOnlyGlobeFromBothListsAndRemainsIdempotent();
        return assertions;
    }

    private static void normalModeEnsuresGlobeExistsExactlyOnceWithoutDroppingOtherPresets() {
        List<String> normal = new ArrayList<>(List.of("normal", "modded"));
        List<String> alternate = new ArrayList<>(List.of("flat", "amplified"));

        VanillaOnlyWorldCreationState.ensureInBothPresetLists(normal, alternate, "globe");
        VanillaOnlyWorldCreationState.ensureInBothPresetLists(normal, alternate, "globe");

        expect(List.of("normal", "globe", "modded"), normal, "normal list gains globe exactly once");
        expect(List.of("flat", "globe", "amplified"), alternate, "alternate list gains globe exactly once");
    }

    private static void removesOnlyGlobeFromBothListsAndRemainsIdempotent() {
        List<String> normal = new ArrayList<>(List.of("normal", "globe", "modded"));
        List<String> alternate = new ArrayList<>(List.of("flat", "globe", "amplified"));

        VanillaOnlyWorldCreationState.removeFromBothPresetLists(normal, alternate, "globe");
        VanillaOnlyWorldCreationState.removeFromBothPresetLists(normal, alternate, "globe");

        expect(List.of("normal", "modded"), normal, "normal list loses only globe");
        expect(List.of("flat", "amplified"), alternate, "alternate list loses only globe");
    }

    private static void expect(Object expected, Object actual, String label) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }
}
