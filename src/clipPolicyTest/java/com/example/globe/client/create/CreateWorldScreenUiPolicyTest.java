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
        bespokeBackgroundUsesFixedEightyPercentOpacity();
        stillBackgroundControlStaysOnTheMainScreenAndPersists();
        stillBackgroundMouseFocusClearsAfterRelease();
        stillBackgroundWaitsForTheIntroReveal();
        selectedClimateDescriptionUsesReadableContrast();
        accessibilityFooterAvoidsTheCreateButtons();
        tabClicksUseRealWidgetOwnership();
        return assertions;
    }

    private static void bespokeBackgroundUsesFixedEightyPercentOpacity() {
        int background = CreateWorldScreenUiPolicy.bespokeBackground(0x3A302A);
        expect(80, CreateWorldScreenUiPolicy.BESPOKE_BACKGROUND_OPACITY_PERCENT,
                "fixed background opacity percentage");
        expect(204, background >>> 24, "exact 80 percent alpha channel");
        expect(0x3A302A, background & 0x00FFFFFF,
                "bespoke background keeps its brown color");
    }

    private static void stillBackgroundControlStaysOnTheMainScreenAndPersists() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java"));
        String config = Files.readString(Path.of(
                "src/main/java/com/example/globe/client/LatitudeConfig.java"));
        expectTrue(!screen.contains("BackgroundOpacitySlider"),
                "world creation must not carry a one-use opacity slider");
        expectTrue(screen.contains("this.addRenderableWidget(this.stillBackgroundBtn)"),
                "still-background control must belong to the main screen");
        expectTrue(screen.contains("LatitudeConfig.saveCurrent()"),
                "accessibility changes must be remembered immediately");
        expectTrue(!config.contains("createWorldPanelOpacity"),
                "panel opacity must remain a fixed design value, not saved configuration");
        expectTrue(config.contains("private Boolean createWorldStillBackgroundValue = false;"),
                "older configs must retain the scenic background by default");
    }

    private static void stillBackgroundMouseFocusClearsAfterRelease() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java"));
        int release = screen.indexOf("boolean handled = super.mouseReleased(click);");
        int clear = screen.indexOf("clearStillButtonMouseFocus();", release);
        int returned = screen.indexOf("return handled;", clear);
        expectTrue(release >= 0 && clear > release && returned > clear,
                "mouse release must clear the Still button focus after widget dispatch");
        expectTrue(screen.contains("stillBackgroundBtn.isFocused()"),
                "focus clearing must trust the Still button's own focus state");
        expectTrue(screen.contains("stillBackgroundBtn.setFocused(false)"),
                "the Still button must not retain its click highlight");
        expectTrue(screen.contains("this.setFocused(null)"),
                "the screen must release its matching focus owner");
        expectTrue(screen.contains("stillButtonMouseInteraction"),
                "mouse focus clearing must not erase ordinary keyboard focus");
        expectTrue(screen.contains("!stillBackgroundBtn.isMouseOver(mouseX, mouseY)"),
                "moving the pointer away must clear any late retained mouse focus");
        int clicked = screen.indexOf("boolean handled = super.mouseClicked(click, doubled);");
        int immediateClear = screen.indexOf("clearStillButtonMouseFocus();", clicked);
        expectTrue(clicked >= 0 && immediateClear > clicked,
                "mouse-down focus must be cleared immediately after widget dispatch");
        int frameCoordinates = screen.indexOf("!stillBackgroundBtn.isMouseOver(mouseX, mouseY)",
                screen.indexOf("public void extractRenderState"));
        expectTrue(frameCoordinates >= 0,
                "rendered pointer coordinates must clear focus when SDL skips mouseMoved");
    }

    private static void stillBackgroundWaitsForTheIntroReveal() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java"));
        expectTrue(screen.contains("setTabbedWidgetVisible(stillBackgroundBtn, true)"),
                "Still must appear with the rest of the create-world UI");
        expectTrue(screen.contains("setTabbedWidgetVisible(stillBackgroundBtn, false)"),
                "Still must remain hidden during the Latitude title intro");
    }

    private static void selectedClimateDescriptionUsesReadableContrast() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java"));
        expectTrue(screen.contains("selected ? WARM_WHITE : MUTED, false"),
                "the selected climate description must brighten while other rows stay muted");
    }

    private static void accessibilityFooterAvoidsTheCreateButtons() {
        expectTrue(!CreateWorldScreenUiPolicy.accessibilityControlsNeedOwnRow(300, 96),
                "wide screens keep one compact footer row");
        expectTrue(CreateWorldScreenUiPolicy.accessibilityControlsNeedOwnRow(90, 96),
                "narrow screens give accessibility controls their own row");
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
