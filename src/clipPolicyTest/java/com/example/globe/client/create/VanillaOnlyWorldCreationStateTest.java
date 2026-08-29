package com.example.globe.client.create;

import java.util.ArrayList;
import java.util.List;

/**
 * The preset-list rules behind the vanilla escape hatch: a vanilla-only session must not be
 * offered Globe, and an ordinary session must still get it at the same position it has always
 * occupied. Both list mutations are idempotent, because {@code updatePresetLists} runs repeatedly.
 */
public final class VanillaOnlyWorldCreationStateTest {
    private static int assertions;

    private VanillaOnlyWorldCreationStateTest() {
    }

    public static void run() {
        insertsAtSecondPositionWhenListIsPopulated();
        insertsAtHeadWhenListIsEmpty();
        insertionIsIdempotent();
        removalTakesEveryCopyFromBothLists();
        removalIsIdempotent();
        System.out.println("PASS VanillaOnlyWorldCreationStateTest assertions=" + assertions);
    }

    private static void insertsAtSecondPositionWhenListIsPopulated() {
        List<String> normal = new ArrayList<>(List.of("default", "flat"));
        List<String> alternate = new ArrayList<>(List.of("amplified"));
        VanillaOnlyWorldCreationState.ensureInBothPresetLists(normal, alternate, "globe");
        // Index 1 keeps Globe directly under the default preset rather than displacing it.
        expect("[default, globe, flat]", normal.toString(), "normal list placement");
        expect("[amplified, globe]", alternate.toString(), "alternate list placement");
    }

    private static void insertsAtHeadWhenListIsEmpty() {
        List<String> normal = new ArrayList<>();
        List<String> alternate = new ArrayList<>();
        VanillaOnlyWorldCreationState.ensureInBothPresetLists(normal, alternate, "globe");
        expect("[globe]", normal.toString(), "empty normal list");
        expect("[globe]", alternate.toString(), "empty alternate list");
    }

    private static void insertionIsIdempotent() {
        List<String> normal = new ArrayList<>(List.of("default", "flat"));
        List<String> alternate = new ArrayList<>(List.of("default"));
        for (int i = 0; i < 3; i++) {
            VanillaOnlyWorldCreationState.ensureInBothPresetLists(normal, alternate, "globe");
        }
        expect("[default, globe, flat]", normal.toString(), "no duplicate in normal list");
        expect("[default, globe]", alternate.toString(), "no duplicate in alternate list");
    }

    private static void removalTakesEveryCopyFromBothLists() {
        List<String> normal = new ArrayList<>(List.of("default", "globe", "flat", "globe"));
        List<String> alternate = new ArrayList<>(List.of("globe", "amplified"));
        VanillaOnlyWorldCreationState.removeFromBothPresetLists(normal, alternate, "globe");
        expect("[default, flat]", normal.toString(), "every copy removed from normal list");
        expect("[amplified]", alternate.toString(), "removed from alternate list");
    }

    private static void removalIsIdempotent() {
        List<String> normal = new ArrayList<>(List.of("default", "flat"));
        List<String> alternate = new ArrayList<>(List.of("amplified"));
        VanillaOnlyWorldCreationState.removeFromBothPresetLists(normal, alternate, "globe");
        expect("[default, flat]", normal.toString(), "absent normal list is untouched");
        expect("[amplified]", alternate.toString(), "absent alternate list is untouched");
    }

    private static void expect(String expected, String actual, String label) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }
}
