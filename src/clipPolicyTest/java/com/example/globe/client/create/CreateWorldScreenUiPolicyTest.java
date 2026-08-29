package com.example.globe.client.create;

/**
 * Panel cycling for Ctrl+Tab / Ctrl+Shift+Tab. The wrap in both directions is the whole point:
 * a plain {@code %} sends a reverse step off panel zero to -1, which is not a panel.
 */
public final class CreateWorldScreenUiPolicyTest {
    private static int assertions;

    private CreateWorldScreenUiPolicyTest() {
    }

    public static void run() {
        cyclesForward();
        cyclesBackward();
        wrapsAtBothEnds();
        singlePanelStaysPut();
        rejectsNonPositivePanelCount();
        System.out.println("PASS CreateWorldScreenUiPolicyTest assertions=" + assertions);
    }

    private static void cyclesForward() {
        expect(1, CreateWorldScreenUiPolicy.cyclePanel(0, 3, false), "0 -> 1");
        expect(2, CreateWorldScreenUiPolicy.cyclePanel(1, 3, false), "1 -> 2");
    }

    private static void cyclesBackward() {
        expect(1, CreateWorldScreenUiPolicy.cyclePanel(2, 3, true), "2 -> 1");
        expect(0, CreateWorldScreenUiPolicy.cyclePanel(1, 3, true), "1 -> 0");
    }

    private static void wrapsAtBothEnds() {
        expect(0, CreateWorldScreenUiPolicy.cyclePanel(2, 3, false), "last wraps forward to first");
        expect(2, CreateWorldScreenUiPolicy.cyclePanel(0, 3, true), "first wraps backward to last");
    }

    private static void singlePanelStaysPut() {
        expect(0, CreateWorldScreenUiPolicy.cyclePanel(0, 1, false), "single panel forward");
        expect(0, CreateWorldScreenUiPolicy.cyclePanel(0, 1, true), "single panel backward");
    }

    private static void rejectsNonPositivePanelCount() {
        boolean threw = false;
        try {
            CreateWorldScreenUiPolicy.cyclePanel(0, 0, false);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assertions++;
        if (!threw) {
            throw new AssertionError("zero panel count must be rejected");
        }
    }

    private static void expect(int expected, int actual, String label) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }
}
