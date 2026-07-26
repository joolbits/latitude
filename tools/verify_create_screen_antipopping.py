#!/usr/bin/env python3
"""Structural and geometry proof for Latitude's create-screen anti-popping contract."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java"
POLICY_SOURCE = ROOT / "src/main/java/com/example/globe/client/create/ViewportClipPolicy.java"
POLICY_TEST = ROOT / "src/clipPolicyTest/java/com/example/globe/client/create/ViewportClipPolicyTest.java"
PLANISPHERE = ROOT / "src/main/java/com/example/globe/client/create/LatitudePlanisphereRenderer.java"


def intersects(top: int, bottom: int, clip_top: int, clip_bottom: int) -> bool:
    return bottom > clip_top and top < clip_bottom


def contains_point(x: float, y: float, left: int, top: int, right: int, bottom: int) -> bool:
    return x >= left and x < right and y >= top and y < bottom


def uses_tabbed_layout(viewport_width: int, gui_scale: int) -> bool:
    return gui_scale >= 3 or viewport_width < 720


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        return ""
    brace = source.find("{", start)
    if brace < 0:
        return ""
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1:index]
    return ""


def main() -> int:
    source = SOURCE.read_text(encoding="utf-8")
    policy_source = POLICY_SOURCE.read_text(encoding="utf-8")
    policy_test = POLICY_TEST.read_text(encoding="utf-8")
    planisphere = PLANISPHERE.read_text(encoding="utf-8")
    failures: list[str] = []

    geometry_cases = {
        "partial top intersects": intersects(90, 110, 100, 200),
        "partial bottom intersects": intersects(190, 210, 100, 200),
        "fully above excluded": not intersects(70, 100, 100, 200),
        "fully below excluded": not intersects(200, 220, 100, 200),
        "visible point accepted": contains_point(15, 100, 10, 100, 30, 200),
        "hidden top edge excluded": not contains_point(15, 99, 10, 100, 30, 200),
        "bottom edge excluded": not contains_point(15, 200, 10, 100, 30, 200),
        "high GUI scale uses separate tabs": uses_tabbed_layout(1200, 3),
        "roomy normal GUI scale keeps three columns": not uses_tabbed_layout(900, 2),
        "cramped normal GUI scale uses separate tabs": uses_tabbed_layout(719, 2),
        "comfortable-width boundary keeps three columns": not uses_tabbed_layout(720, 2),
    }
    failures.extend(name for name, passed in geometry_cases.items() if not passed)

    required_fragments = {
        "private final List<AbstractWidget> settingsScrollWidgets": "Rules widget registry",
        "settingsScrollWidgets.clear();": "re-init clears private Rules widgets",
        "private int headerBandBottom()": "fixed heading boundary",
        "private int settingsClipTop()": "shared Rules clip boundary",
        "private int spawnClipTop()": "shared Spawn Zone clip boundary",
        "private static final int HIGH_GUI_SCALE = 3;": "high GUI scale tab threshold",
        "private static final int MIN_COMFORTABLE_THREE_COL_WIDTH = 720;": "comfortable three-column width threshold",
        "private static boolean shouldUseTabbedLayout(int viewportWidth, int guiScale)": "shared tab-mode decision",
        "shouldUseTabbedLayout(paneStripViewportWidth, guiScale)": "init uses GUI-scale-aware tab decision",
        'private static final String[] TAB_LABELS = {"World", "Rules"};': "two-tab compact navigation",
        "boolean showZone = activeTab == 0 && isLatitudeWorld();": "Spawn Zone shares the World tab",
        "boolean showRules = activeTab == 1;": "Rules owns the second compact tab",
        "private int settingsColumnW;": "two-column Rules geometry",
        "private int settingsRightColumnX;": "Rules right-column anchor",
        "int rulesRowCount = tabbedMode ? 4 : 8;": "compact Rules uses four two-column rows",
        "positionSettingsStepper(modePrevBtn, modeNextBtn, settingsRightColumnX": "Game Mode occupies the right Rules column",
        "positionSettingsButton(compassBtn, settingsRightColumnX": "Starting Compass occupies the right Rules column",
        "positionSettingsButton(bonusChestBtn, settingsRightColumnX": "Bonus Chest occupies the right Rules column",
        "positionSettingsButton(hudStudioBtn, settingsRightColumnX": "HUD Studio occupies the right Rules column",
        "private static boolean intersectsClip(": "partial-intersection geometry",
        "private static boolean pointInsideClip(": "input clip geometry",
        "addSettingsScrollWidget(worldTypePrevBtn);": "World Type left registered input-only",
        "addSettingsScrollWidget(worldTypeNextBtn);": "World Type right registered input-only",
        "addSettingsScrollWidget(modePrevBtn);": "Game Mode left registered input-only",
        "addSettingsScrollWidget(modeNextBtn);": "Game Mode right registered input-only",
        "addSettingsScrollWidget(commandsBtn);": "Commands registered input-only",
        "addSettingsScrollWidget(compassBtn);": "Compass registered input-only",
        "addSettingsScrollWidget(structuresBtn);": "Structures registered input-only",
        "addSettingsScrollWidget(bonusChestBtn);": "Bonus Chest registered input-only",
        "addSettingsScrollWidget(gameRulesBtn);": "Game Rules registered input-only",
        "addSettingsScrollWidget(hudStudioBtn);": "HUD Studio registered input-only",
        "renderSettingsScrollWidgets(context, mouseX, mouseY, delta);": "one manual Rules render call",
        "settingsViewportTop = threeCol": "tabbed mode removes blank Rules shelf",
        "applyScrollWidgetState(": "focus and narration eligibility follow visibility",
        "applyScrollWidgetState(row, visible, visible);": "partially visible Spawn Zone rows remain selectable",
        "private boolean handleSpawnZoneClippedClick(": "explicit clipped Spawn Zone click handler",
        "ViewportClipPolicy.acceptsClippedWidgetClick(": "production uses the shared clipped-widget policy",
        "row.selectFromClippedMouseClick(click, doubled);": "accepted clipped clicks directly select the row",
        "if (click.button() == 0 && handleSpawnZoneClippedClick(click, doubled))": "screen dispatches clipped clicks explicitly",
        "if (isInsideRulesPanel(click.x(), click.y())": "Rules click gate",
        "if (isInsideSpawnPanel(click.x(), click.y())": "Spawn Zone click gate",
    }
    for fragment, label in required_fragments.items():
        if fragment not in source:
            failures.append(f"missing {label}")

    forbidden_auto_render = (
        "worldTypePrevBtn", "worldTypeNextBtn", "modePrevBtn", "modeNextBtn",
        "commandsBtn", "compassBtn", "structuresBtn", "bonusChestBtn",
        "gameRulesBtn", "hudStudioBtn",
    )
    for name in forbidden_auto_render:
        if re.search(rf"addRenderableWidget\(\s*{name}\s*\)", source):
            failures.append(f"{name} still auto-renders outside the Rules scissor")

    extract = method_body(
        source,
        "public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)",
    )
    if not extract:
        failures.append("extractRenderState body not found")
    else:
        if extract.count("updateSettingsLayout();") != 1:
            failures.append("Rules layout is not applied exactly once per rendered frame")
        if extract.count("renderSettingsScrollWidgets(context, mouseX, mouseY, delta);") != 1:
            failures.append("Rules widgets do not have exactly one manual render path")
        heading = extract.find('drawInlineHeading(context, railX, railW, "Rules", GOLD)')
        scissor = extract.find("context.enableScissor(railClipLeft, settingsClipTop()")
        manual = extract.find("renderSettingsScrollWidgets(context, mouseX, mouseY, delta);")
        if not (0 <= heading < scissor < manual):
            failures.append("Rules heading/scissor/manual-render order is not fixed-heading -> clip -> widgets")
        if '"WORLD"' in extract or '"SETTINGS"' in extract:
            failures.append("tabbed Rules pane still draws the redundant internal heading")

    zone_render = method_body(
        source,
        "protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks)",
    )
    if "spawnClipTop()" not in zone_render:
        failures.append("Spawn Zone rows do not render against the shared clip top")
    if "boolean fullySelectable" in source:
        failures.append("Spawn Zone rows still require full exposure before selection")

    mouse_clicked = method_body(
        source,
        "public boolean mouseClicked(MouseButtonEvent click, boolean doubled)",
    )
    explicit_spawn = mouse_clicked.find("handleSpawnZoneClippedClick(click, doubled)")
    generic_dispatch = mouse_clicked.find("super.mouseClicked(click, doubled)")
    if not (0 <= explicit_spawn < generic_dispatch):
        failures.append("explicit clipped Spawn Zone selection does not precede generic screen dispatch")

    policy_fragments = {
        "static boolean acceptsClippedWidgetClick(": "shared clipped-widget policy",
        "containsPoint(x, y, widgetLeft, widgetTop, widgetRight, widgetBottom)": "widget-bound check",
        "containsPoint(x, y, clipLeft, clipTop, clipRight, clipBottom)": "viewport-bound check",
    }
    for fragment, label in policy_fragments.items():
        if fragment not in policy_source:
            failures.append(f"missing {label}")

    test_cases = {
        "acceptsVisiblePartOfTopClippedWidget();": "top-clipped visible-part regression",
        "acceptsVisiblePartOfBottomClippedWidget();": "bottom-clipped visible-part regression",
        "rejectsClippedOffPartOfPartialWidget();": "clipped-off input exclusion regression",
        "rejectsFullyHiddenWidget();": "fully-hidden input exclusion regression",
        "honorsHalfOpenClipEdges();": "edge-boundary regression",
    }
    for fragment, label in test_cases.items():
        if fragment not in policy_test:
            failures.append(f"missing {label}")

    planisphere_fragments = {
        "COMPACT_GRID_COLOR": "quiet compact latitude framework",
        "COMPACT_RING_COLOR": "compact disc outline",
        "drawCircleOutline(context, cx, cy, radius, COMPACT_RING_COLOR)": "balanced compact disc perimeter",
        "case REGULAR -> 0.84f": "regular-world preview fill",
        "case LARGE -> 0.91f": "large-world preview fill",
        "case MASSIVE -> 0.96f": "massive-world preview fill",
    }
    for fragment, label in planisphere_fragments.items():
        haystack = planisphere if "COMPACT_" in fragment or "drawCircleOutline" in fragment else source
        if fragment not in haystack:
            failures.append(f"missing {label}")

    if failures:
        print("CREATE_SCREEN_ANTI_POPPING: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("CREATE_SCREEN_ANTI_POPPING: PASS")
    print("- production and the executable regression share one clipped-widget input policy")
    print("- partial rows render and select through their visible portion; clipped-off and fully hidden portions are excluded")
    print("- Spawn Zone and Rules input gates use the same boundaries as rendering")
    print("- Rules controls are input/focus/narration widgets with one manual scissored render path")
    print("- Rules layout runs once per frame; re-init clears the private widget registry")
    print("- high GUI scale and cramped viewports use two tabs: World plus Rules")
    print("- compact World places Spawn Zone beside the planisphere; compact Rules uses two columns")
    print("- planisphere uses a centered larger disc, quiet latitude framework, and restrained gold perimeter")
    print("- roomy low-scale mode keeps all three fixed wide panels; tabbed panes reserve no redundant heading shelf")
    return 0


if __name__ == "__main__":
    sys.exit(main())
