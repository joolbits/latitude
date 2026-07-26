#!/usr/bin/env python3
"""Verify the bounded Latitude 1.5 one-front-door HUD Studio contract."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "src/main/java/com/example/globe/GlobeModClient.java"
CONFIG = ROOT / "src/main/java/com/example/globe/client/CompassHudConfig.java"
HUD = ROOT / "src/main/java/com/example/globe/client/CompassHud.java"
STUDIO = ROOT / "src/main/java/com/example/globe/client/LatitudeHudStudioScreen.java"
NUMERIC_POLICY = ROOT / "src/main/java/com/example/globe/client/HudTextLayoutPolicy.java"
DETAIL_POLICY = ROOT / "src/main/java/com/example/globe/client/LocationDetailPolicy.java"
LEGACY_SETTINGS = ROOT / "src/main/java/com/example/globe/client/LatitudeSettingsScreen.java"


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def source_slice(source: str, start: str, end: str) -> str:
    start_index = source.find(start)
    end_index = source.find(end, start_index + len(start)) if start_index >= 0 else -1
    if start_index < 0 or end_index < 0:
        return ""
    return source[start_index:end_index]


def main() -> int:
    client = CLIENT.read_text()
    config = CONFIG.read_text()
    hud = HUD.read_text()
    studio = STUDIO.read_text()
    numeric_policy = NUMERIC_POLICY.read_text()
    detail_policy = DETAIL_POLICY.read_text()
    current_digital_content = source_slice(
        hud,
        "private static DigitalContent currentDigitalContent(",
        "private static String currentDirectionText(",
    )
    compute_bounds = source_slice(
        hud,
        "public static HudBounds computeBounds(Minecraft client, CompassHudConfig cfg)",
        "public static HudPoint computeBasePosition(",
    )
    failures: list[str] = []

    require(
        "import com.example.globe.client.LatitudeHudStudioScreen;" in client
        and "new LatitudeHudStudioScreen(" in client,
        "F9 must open LatitudeHudStudioScreen directly",
        failures,
    )
    require(
        "if (client.gui.screen() instanceof LatitudeHudStudioScreen)" in client,
        "repeated F9 input must not nest a second Studio over the first",
        failures,
    )
    require(
        "LatitudeSettingsScreen" not in client and not LEGACY_SETTINGS.exists(),
        "the separate legacy LatitudeSettingsScreen must be removed",
        failures,
    )

    require(
        'TAB_NAMES = {"Compass", "Title", "Settings"}' in studio
        and "TAB_COMPASS" in studio
        and "TAB_TITLE" in studio
        and "TAB_SETTINGS" in studio,
        "HUD Studio must expose top-level Compass, Title, and Settings tabs",
        failures,
    )
    require(
        "switchTab(" in studio and "activeTab" in studio,
        "tab selection must rebuild the active control set",
        failures,
    )
    require(
        "drawActiveTabUnderline(ctx);" in studio
        and "private void drawActiveTabUnderline(" in studio
        and "activeTab" in studio,
        "the selected tab must retain a visible non-text active marker",
        failures,
    )

    for label in (
        "Show HUD",
        "Display When",
        "Zone Enter Title",
        "Warning Messages",
        "Title Duration (seconds)",
        "Title Size",
    ):
        require(
            f'Component.literal("{label}")' in studio,
            f"the correct tab must contain {label}",
            failures,
        )

    for label in ("Compass Style", "Compact HUD", "Location Detail"):
        require(
            studio.count(f'Component.literal("{label}")') == 1,
            f"{label} must have one authoritative Studio control",
            failures,
        )
    require(
        "Target: Compass" not in studio
        and "Target: Title" not in studio
        and "Target: Both" not in studio,
        "element tabs must replace the old Target cycler",
        failures,
    )
    require(
        "w.active = visible;" in studio,
        "clipped controls must be inactive as well as invisible",
        failures,
    )
    require(
        "isSidebarWidgetEligible(w)" in studio
        and "w == wCompassAttachHotbar" in studio
        and "w == wLocationFollow" in studio,
        "viewport scrolling must preserve mode-specific semantic hiding",
        failures,
    )
    require(
        "if (!visible && this.getFocused() == w)" in studio
        and "this.setFocused(null);" in studio,
        "a control that becomes hidden or clipped must release keyboard focus",
        failures,
    )
    require(
        '"Press L to hide panel"' in studio and '"Press L to show panel"' in studio,
        "L-key helper copy must distinguish the panel from the Settings tab",
        failures,
    )
    require(
        "int helperY = this.height - 66;" in studio
        and 'ctx.text(this.font, "Press L to hide panel", 8, helperY, 0xAA8C8078);' in studio
        and "int hiddenHelperY = this.height - this.font.lineHeight - 6;" in studio
        and 'ctx.text(this.font, "Press L to show panel", 8, hiddenHelperY, 0x888C8078);' in studio,
        "L-key helper must use muted bottom placement above Reset All HUD or at bottom-left",
        failures,
    )
    require(
        "this.sidebarViewportBottom = Math.max(panelY + 24, this.height - 70);" in studio,
        "the sidebar scroll viewport must reserve a footer lane above the L-key helper",
        failures,
    )
    mouse_clicked = re.search(
        r"public boolean mouseClicked\(MouseButtonEvent click, boolean doubleClick\)"
        r"\s*\{(?P<body>.*?)\n\s*\}\n\n\s*@Override\n\s*public boolean mouseDragged",
        studio,
        re.DOTALL,
    )
    mouse_clicked_body = mouse_clicked.group("body") if mouse_clicked else ""
    require(
        mouse_clicked is not None
        and "activeTab == TAB_SETTINGS" not in mouse_clicked_body
        and "activeTab == TAB_TITLE" not in mouse_clicked_body
        and "activeTab == TAB_COMPASS" not in mouse_clicked_body
        and "isMouseOverTitle(mx, my)" in mouse_clicked_body
        and "isMouseOverCompass(mx, my)" in mouse_clicked_body
        and "isMouseOverLocationDetail(mx, my)" in mouse_clicked_body,
        "every visible HUD preview element must be draggable from every Studio tab",
        failures,
    )
    title_dragged = re.search(
        r"if \(dragElement == DragElement\.TITLE\)\s*\{(?P<body>.*?)"
        r"\n\s*return true;\n\s*\}",
        studio,
        re.DOTALL,
    )
    title_dragged_body = title_dragged.group("body") if title_dragged else ""
    require(
        title_dragged is not None
        and title_dragged_body.count("HudTextLayoutPolicy.titleDragCoordinate(") == 2
        and "LatitudeConfig.hudSnapEnabled" in title_dragged_body,
        "SNAP mode must visibly constrain title movement during the drag, not only on release",
        failures,
    )

    require(
        'Component.literal("Face Opacity")' in studio,
        "analog alpha control must be named Face Opacity",
        failures,
    )
    require(
        re.search(
            r'Component\.literal\("Face Opacity"\),\s*0\.0f,\s*1\.0f,\s*'
            r"cfg\.analogInnerAlpha,\s*true,",
            studio,
        )
        is not None
        and "Math.round(getValue() * 100.0f) + \"%\"" in studio,
        "Face Opacity must display a player-readable percentage",
        failures,
    )
    require(
        "public boolean faceOpacityAdjustActive()" in studio
        and "isDragging()" in studio
        and "isMouseOver(lastMouseX, lastMouseY)" in studio,
        "face-opacity aid must be derived from current hover/drag state",
        failures,
    )
    active_method = re.search(
        r"public boolean faceOpacityAdjustActive\(\)\s*\{(?P<body>.*?)\n\s*\}",
        studio,
        re.DOTALL,
    )
    require(
        active_method is not None and "isFocused()" not in active_method.group("body"),
        "sticky keyboard focus must not latch the checkerboard",
        failures,
    )
    require(
        "studio.faceOpacityAdjustActive()" in hud
        and "drawTransparencyCheckerboard(" in hud,
        "analog preview must draw its checkerboard through the active-only Studio gate",
        failures,
    )
    require(
        "@Override\n    public void onClose()" in studio
        and "CompassHudConfig.saveCurrent();" in studio
        and "LatitudeConfig.saveCurrent();" in studio
        and "this.minecraft.gui.setScreen(this.parent);" in studio
        and re.search(
            r'Button\.builder\(Component\.literal\("Done"\),\s*btn\s*->\s*'
            r"this\.onClose\(\)\)",
            studio,
        )
        is not None,
        "Done and Esc must share one save-and-return path to the captured parent",
        failures,
    )

    require(
        "DEFAULT_COMPASS_STYLE = CompassStyle.ANALOG" in config
        and "DEFAULT_ANALOG_SIZE = 32.0f" in config,
        "fresh config must default to the compact 2.0 analog baseline",
        failures,
    )
    require(
        "DEFAULT_LOCATION_TEXT_SCALE = 1.0f" in numeric_policy
        and "LOCATION_TEXT_SCALE_MIN = 0.75f" in numeric_policy
        and "LOCATION_TEXT_SCALE_MAX = 2.0f" in numeric_policy
        and "public float locationTextScale = DEFAULT_LOCATION_TEXT_SCALE;" in config
        and "HudTextLayoutPolicy.sanitizeLocationTextScale(locationTextScale)" in config
        and "Float.isFinite(value)" in numeric_policy
        and "Math.round(value * 20.0f) / 20.0f" in numeric_policy,
        "location text size must default safely and sanitize to 75%-200% in five-percent steps",
        failures,
    )
    require(
        'Component.literal("Location Text Size")' in studio
        and "CompassHudConfig.LOCATION_TEXT_SCALE_MIN * 100.0f" in studio
        and "CompassHudConfig.LOCATION_TEXT_SCALE_MAX * 100.0f" in studio
        and '\"%\",' in studio
        and "v -> cfg.locationTextScale = v / 100.0f" in studio
        and "setVisible(wLocationTextScale, showCompassControls);" in studio,
        "Compass tab must expose the independent location-text percentage slider in both styles",
        failures,
    )
    require(
        "scaledTextWidth(client, content.direction(), cfg.scale)" in hud
        and "scaledTextWidth(client, content.latitudeSegment(), cfg.locationTextScale)" in hud
        and "scaledTextWidth(client, content.detailSegment(), cfg.locationTextScale)" in hud
        and "content.latitudeSegment() != null || content.detailSegment() != null" in hud
        and "HudTextLayoutPolicy.combinedTextHeight(" in hud
        and "content.direction()," in hud
        and "detailBounds.x," in hud
        and hud.count("drawScaledText(") >= 7,
        "direction must keep compass scale while FOLLOW/DETACH latitude and detail use location text scale",
        failures,
    )
    require(
        "currentDirectionText(client, cfg)" in current_digital_content
        and "latitudeText(client, cfg)" in current_digital_content
        and "locationDetailLabel(client, cfg, true)" in current_digital_content
        and "sampleLocationDetail" not in current_digital_content
        and "sampleDigitalContent" not in current_digital_content
        and "studioPreview ? sampleDigitalContent(cfg) : currentDigitalContent(client, cfg)" in compute_bounds
        and "HudBounds compassBounds = computeBounds(client, cfg);" in hud
        and "HudTextLayoutPolicy.digitalBoxWidth(" in hud
        and "HudTextLayoutPolicy.movePristineDetachedY(" in hud,
        "runtime overlap must use live rendered compass content while Studio alone keeps samples",
        failures,
    )
    sample_location_detail = source_slice(
        hud,
        "private static String sampleLocationDetail(",
        "private static String analogLatitudeText(",
    )
    require(
        'LocationDetailPolicy.studioPreviewBiomeLabel(' in sample_location_detail
        and '"minecraft:plains"' in sample_location_detail
        and "cfg.showCustomBiomeSource" in sample_location_detail
        and "public static String studioPreviewBiomeLabel(" in detail_policy
        and 'COMBINED_SEPARATOR + "VANILLA"' in detail_policy,
        "HUD Studio preview must visibly demonstrate the biome-source toggle with its vanilla sample",
        failures,
    )
    require(
        re.search(
            r"resetToDefaults\(\).*?style\s*=\s*DEFAULT_COMPASS_STYLE;.*?"
            r"analogSize\s*=\s*DEFAULT_ANALOG_SIZE;",
            config,
            re.DOTALL,
        )
        is not None,
        "Reset HUD must use the same analog style and size defaults",
        failures,
    )
    require(
        "if (style == null) style = CompassStyle.DIGITAL;" in config,
        "legacy JSON without a style field must retain the donor compatibility fallback",
        failures,
    )
    require(
        "float nScale = Mth.clamp(radius / 24.0f, 0.4f, 1.0f);" in hud
        and "pose.scale(nScale, nScale);" in hud,
        "analog north label must scale with the dial using the 2.0 clamp",
        failures,
    )

    if failures:
        print("HUD Studio consolidation verifier: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("HUD Studio consolidation verifier: PASS")
    print("- F9 has one front door")
    print("- repeated F9 cannot nest another Studio")
    print("- top tabs: Compass / Title / Settings")
    print("- legacy settings are folded without duplicate or semantically reactivated controls")
    print("- hidden and clipped controls release keyboard focus")
    print("- selected tab has a persistent visual marker")
    print("- title, compass, and detached location detail remain draggable from every tab")
    print("- L-key helper is muted and bottom-aligned")
    print("- sidebar scrolling reserves a non-overlapping helper footer")
    print("- Face Opacity uses percentage copy and a hover/drag-only checkerboard")
    print("- analog north label scales with the dial")
    print("- location text size is independent across analog/digital and FOLLOW/DETACH")
    print("- runtime detached overlap uses live content; Studio preview retains samples")
    print("- Done and Esc share the same save-and-return path")
    print("- fresh/reset analog baseline: 32 px; legacy null fallback preserved")
    return 0


if __name__ == "__main__":
    sys.exit(main())
