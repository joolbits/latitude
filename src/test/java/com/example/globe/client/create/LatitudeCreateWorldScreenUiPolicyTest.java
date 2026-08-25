package com.example.globe.client.create;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LatitudeCreateWorldScreenUiPolicyTest {
    @Test
    void keyboardTabCycleVisitsEveryPanelInBothDirections() {
        assertEquals(1, CreateWorldScreenUiPolicy.cyclePanel(0, 3, false));
        assertEquals(2, CreateWorldScreenUiPolicy.cyclePanel(1, 3, false));
        assertEquals(0, CreateWorldScreenUiPolicy.cyclePanel(2, 3, false));

        assertEquals(2, CreateWorldScreenUiPolicy.cyclePanel(0, 3, true));
        assertEquals(1, CreateWorldScreenUiPolicy.cyclePanel(2, 3, true));
        assertEquals(0, CreateWorldScreenUiPolicy.cyclePanel(1, 3, true));
    }

    @Test
    void clippedWidgetAcceptsOnlyItsActuallyVisiblePixels() {
        int widgetX = 10;
        int widgetY = 10;
        int widgetWidth = 100;
        int widgetHeight = 20;
        int clipLeft = 20;
        int clipTop = 15;
        int clipRight = 90;
        int clipBottom = 25;

        assertTrue(CreateWorldScreenUiPolicy.isInsideClip(
                20, 15, widgetX, widgetY, widgetWidth, widgetHeight, clipLeft, clipTop, clipRight, clipBottom));
        assertTrue(CreateWorldScreenUiPolicy.isInsideClip(
                89, 24, widgetX, widgetY, widgetWidth, widgetHeight, clipLeft, clipTop, clipRight, clipBottom));

        assertFalse(CreateWorldScreenUiPolicy.isInsideClip(
                19, 20, widgetX, widgetY, widgetWidth, widgetHeight, clipLeft, clipTop, clipRight, clipBottom));
        assertFalse(CreateWorldScreenUiPolicy.isInsideClip(
                50, 14, widgetX, widgetY, widgetWidth, widgetHeight, clipLeft, clipTop, clipRight, clipBottom));
        assertFalse(CreateWorldScreenUiPolicy.isInsideClip(
                90, 20, widgetX, widgetY, widgetWidth, widgetHeight, clipLeft, clipTop, clipRight, clipBottom));
        assertFalse(CreateWorldScreenUiPolicy.isInsideClip(
                50, 25, widgetX, widgetY, widgetWidth, widgetHeight, clipLeft, clipTop, clipRight, clipBottom));
    }
}
