package com.example.globe.client.create;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Panel cycling for Ctrl+Tab / Ctrl+Shift+Tab. The wrap in both directions is the whole point:
 * a plain {@code %} sends a reverse step off panel zero to -1, which is not a panel.
 *
 * <p>This suite also pins the create-world screen's frame geometry and the ownership of its tab
 * hitboxes, so a later layout retune cannot quietly loosen the margins or hand tab clicks back to
 * a hand-rolled dispatcher that competes with Minecraft's own widget input path.</p>
 */
public final class CreateWorldScreenUiPolicyTest {
    private static int assertions;

    private CreateWorldScreenUiPolicyTest() {
    }

    public static void run() throws IOException {
        cyclesForward();
        cyclesBackward();
        wrapsAtBothEnds();
        singlePanelStaysPut();
        rejectsNonPositivePanelCount();
        highScaleFrameKeepsTightMargins();
        tabClicksUseRealWidgetOwnership();
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

    private static void highScaleFrameKeepsTightMargins() {
        expect(4, CreateWorldScreenUiPolicy.EDGE_MARGIN, "screen-edge margin");
        expect(2, CreateWorldScreenUiPolicy.HEADER_GAP, "top margin");
        expect(2, CreateWorldScreenUiPolicy.PANE_GAP, "world-panel gap");
        expect(1, CreateWorldScreenUiPolicy.TAB_GAP, "tab gap");
        expect(4,
                CreateWorldScreenUiPolicy.PANEL_BOTTOM_MARGIN
                        - CreateWorldScreenUiPolicy.BUTTON_ROW_TOP_FROM_BOTTOM,
                "panel-to-button gap");
    }

    private static void tabClicksUseRealWidgetOwnership() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java"));
        expectTrue(source.contains("class TabHitboxWidget extends AbstractWidget"),
                "tab hitboxes must be real screen widgets");
        expectTrue(source.contains("this.addRenderableWidget(hitbox)"),
                "tab hitboxes must be registered for Minecraft input dispatch");
        expectTrue(!source.contains("handleTabClick("),
                "manual tab click dispatch must not compete with widget ownership");
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
