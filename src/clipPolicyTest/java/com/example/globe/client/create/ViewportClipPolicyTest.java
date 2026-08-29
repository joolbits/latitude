package com.example.globe.client.create;

public final class ViewportClipPolicyTest {
    private static int assertions;

    private ViewportClipPolicyTest() {
    }

    public static void main(String[] args) throws Exception {
        acceptsFullyVisibleWidgetClick();
        acceptsVisiblePartOfTopClippedWidget();
        acceptsVisiblePartOfBottomClippedWidget();
        rejectsClippedOffPartOfPartialWidget();
        rejectsFullyHiddenWidget();
        honorsHalfOpenClipEdges();
        System.out.println("PASS ViewportClipPolicyTest assertions=" + assertions);

        // This source set is the create-screen policy suite (despite its name); every class in it
        // must run from this one registered entry point, or the check gate compiles a test that
        // never executes.
        int vanillaStateAssertions = VanillaOnlyWorldCreationStateTest.run();
        System.out.println("PASS VanillaOnlyWorldCreationStateTest assertions=" + vanillaStateAssertions);
        int handoffAssertions = VanillaCreateWorldHandoffTest.run();
        System.out.println("PASS VanillaCreateWorldHandoffTest assertions=" + handoffAssertions);
        int uiPolicyAssertions = CreateWorldScreenUiPolicyTest.run();
        System.out.println("PASS CreateWorldScreenUiPolicyTest assertions=" + uiPolicyAssertions);
        int footerAssertions = VanillaFooterLayoutPolicyTest.run();
        System.out.println("PASS VanillaFooterLayoutPolicyTest assertions=" + footerAssertions);
        int hatchAssertions = EscapeHatchSourcePolicyTest.run();
        System.out.println("PASS EscapeHatchSourcePolicyTest assertions=" + hatchAssertions);
    }

    private static void acceptsFullyVisibleWidgetClick() {
        expect(true, ViewportClipPolicy.acceptsClippedWidgetClick(
                50, 50,
                20, 30, 120, 70,
                10, 10, 130, 100
        ), "fully visible widget click");
    }

    private static void acceptsVisiblePartOfTopClippedWidget() {
        expect(true, ViewportClipPolicy.acceptsClippedWidgetClick(
                50, 42,
                20, 30, 120, 55,
                10, 40, 130, 100
        ), "top-clipped visible portion");
    }

    private static void acceptsVisiblePartOfBottomClippedWidget() {
        expect(true, ViewportClipPolicy.acceptsClippedWidgetClick(
                50, 92,
                20, 85, 120, 110,
                10, 10, 130, 100
        ), "bottom-clipped visible portion");
    }

    private static void rejectsClippedOffPartOfPartialWidget() {
        expect(false, ViewportClipPolicy.acceptsClippedWidgetClick(
                50, 35,
                20, 30, 120, 55,
                10, 40, 130, 100
        ), "top-clipped hidden portion");
        expect(false, ViewportClipPolicy.acceptsClippedWidgetClick(
                50, 105,
                20, 85, 120, 110,
                10, 10, 130, 100
        ), "bottom-clipped hidden portion");
    }

    private static void rejectsFullyHiddenWidget() {
        expect(false, ViewportClipPolicy.acceptsClippedWidgetClick(
                50, 25,
                20, 20, 120, 30,
                10, 40, 130, 100
        ), "fully hidden widget");
    }

    private static void honorsHalfOpenClipEdges() {
        expect(true, ViewportClipPolicy.acceptsClippedWidgetClick(
                10, 40,
                10, 40, 120, 70,
                10, 40, 130, 100
        ), "left and top edges");
        expect(false, ViewportClipPolicy.acceptsClippedWidgetClick(
                120, 50,
                10, 40, 120, 70,
                10, 40, 130, 100
        ), "widget right edge");
        expect(false, ViewportClipPolicy.acceptsClippedWidgetClick(
                50, 70,
                10, 40, 120, 70,
                10, 40, 130, 100
        ), "widget bottom edge");
    }

    private static void expect(boolean expected, boolean actual, String label) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }
}
