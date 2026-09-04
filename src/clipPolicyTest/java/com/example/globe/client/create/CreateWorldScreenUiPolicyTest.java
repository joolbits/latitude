package com.example.globe.client.create;

import java.nio.file.Files;
import java.nio.file.Path;

final class CreateWorldScreenUiPolicyTest {
    private static int assertions;

    private CreateWorldScreenUiPolicyTest() {
    }

    static int run() throws Exception {
        assertions = 0;
        keyboardTabCycleVisitsEveryPanelInBothDirections();
        keyboardTabCycleRejectsANonPositivePanelCount();
        highScaleFrameKeepsTightMargins();
        bespokeBackgroundUsesSeventyFivePercentOpacity();
        tabClicksUseRealWidgetOwnership();
        return assertions;
    }

    private static void bespokeBackgroundUsesSeventyFivePercentOpacity() {
        int background = CreateWorldScreenUiPolicy.bespokeBackground(0x3A302A);
        expect(191, CreateWorldScreenUiPolicy.BESPOKE_BACKGROUND_ALPHA,
                "rounded 75 percent alpha");
        expect(191, background >>> 24, "bespoke background alpha channel");
        expect(0x3A302A, background & 0x00FFFFFF,
                "bespoke background keeps its brown color");
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

    private static void tabClicksUseRealWidgetOwnership() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java"));
        expectTrue(source.contains("class TabHitboxWidget extends AbstractWidget"),
                "tab hitboxes must be real screen widgets");
        expectTrue(source.contains("this.addRenderableWidget(hitbox)"),
                "tab hitboxes must be registered for Minecraft input dispatch");
        expectTrue(!source.contains("handleTabClick("),
                "manual tab click dispatch must not compete with widget ownership");
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
