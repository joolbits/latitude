package com.example.globe.client.create;

final class CreateWorldScreenUiPolicyTest {
    private static int assertions;

    private CreateWorldScreenUiPolicyTest() {
    }

    static int run() {
        assertions = 0;
        keyboardTabCycleVisitsEveryPanelInBothDirections();
        keyboardTabCycleRejectsANonPositivePanelCount();
        return assertions;
    }

    private static void keyboardTabCycleVisitsEveryPanelInBothDirections() {
        expect(1, CreateWorldScreenUiPolicy.cyclePanel(0, 3, false), "forward from 0");
        expect(2, CreateWorldScreenUiPolicy.cyclePanel(1, 3, false), "forward from 1");
        expect(0, CreateWorldScreenUiPolicy.cyclePanel(2, 3, false), "forward wraps from the last panel");

        expect(2, CreateWorldScreenUiPolicy.cyclePanel(0, 3, true), "reverse wraps from the first panel");
        expect(1, CreateWorldScreenUiPolicy.cyclePanel(2, 3, true), "reverse from 2");
        expect(0, CreateWorldScreenUiPolicy.cyclePanel(1, 3, true), "reverse from 1");
    }

    private static void keyboardTabCycleRejectsANonPositivePanelCount() {
        boolean threw = false;
        try {
            CreateWorldScreenUiPolicy.cyclePanel(0, 0, false);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        expectTrue(threw, "zero panels must be rejected, not silently wrapped");
    }

    private static void expect(int expected, int actual, String label) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void expectTrue(boolean condition, String label) {
        assertions++;
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
