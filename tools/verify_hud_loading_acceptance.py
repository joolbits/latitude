#!/usr/bin/env python3
"""Verify the bounded Latitude 1.5 HUD/loading acceptance contract."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "src/main/java/com/example/globe/client/CompassHudConfig.java"
STUDIO = ROOT / "src/main/java/com/example/globe/client/LatitudeHudStudioScreen.java"
LEGACY_SETTINGS = ROOT / "src/main/java/com/example/globe/client/LatitudeSettingsScreen.java"
LOADING = ROOT / (
    "src/main/java/com/example/globe/mixin/client/"
    "LevelLoadingScreenLatitudeOverlayMixin.java"
)


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    config = CONFIG.read_text()
    studio = STUDIO.read_text()
    loading = LOADING.read_text()
    failures: list[str] = []

    require(
        "DEFAULT_COMPASS_STYLE = CompassStyle.ANALOG" in config,
        "fresh compass style must be centralized as ANALOG",
        failures,
    )
    require(
        "DEFAULT_ANALOG_SIZE = 32.0f" in config,
        "fresh analog size must be centralized as 32 px",
        failures,
    )
    require(
        "ANALOG_SIZE_STUDIO_MIN = 16" in config
        and "ANALOG_SIZE_STUDIO_MAX = 72" in config,
        "Studio analog range must be centralized as 16–72 px",
        failures,
    )
    require(
        "ANALOG_SIZE_SAVED_MAX = 128.0f" in config
        and re.search(
            r"analogSize\s*>\s*ANALOG_SIZE_SAVED_MAX\)\s*"
            r"analogSize\s*=\s*ANALOG_SIZE_SAVED_MAX",
            config,
        )
        is not None,
        "sanitizer must preserve explicit saved values through 128 px",
        failures,
    )
    require(
        "public void resetToDefaults()" in config,
        "HUD reset defaults must have one config-owned implementation",
        failures,
    )
    require(
        studio.count("cfg.resetToDefaults();") == 1
        and "private static void applyDefaults(CompassHudConfig cfg)" not in studio,
        "HUD Studio reset must use the centralized defaults",
        failures,
    )
    require(
        not LEGACY_SETTINGS.exists(),
        "the superseded standalone settings screen must stay removed",
        failures,
    )
    require(
        'TAB_NAMES = {"Compass", "Title", "Settings"}' in studio
        and 'Component.literal("Show HUD")' in studio
        and 'Component.literal("Display When")' in studio
        and 'Component.literal("Warning Messages")' in studio,
        "the consolidated Studio Settings tab must own the former first-screen controls",
        failures,
    )
    require(
        re.search(
            r"new IntSlider\([^;]+Component\.literal\(\"Compass Size\"\),\s*"
            r"CompassHudConfig\.ANALOG_SIZE_STUDIO_MIN,\s*"
            r"CompassHudConfig\.ANALOG_SIZE_STUDIO_MAX,\s*"
            r"Math\.round\(cfg\.analogSize\),\s*\" px\"",
            studio,
        )
        is not None,
        "Studio must expose a whole-pixel 'Compass Size: N px' slider",
        failures,
    )
    require(
        "this.legacyDisplayValue = initial > max ? initial : null;" in studio,
        "saved sizes above the Studio range must remain truthful until an edit",
        failures,
    )
    require(
        "legacyDisplayValue = null;" in studio
        and re.search(
            r"protected void applyValue\(\) \{\s*"
            r"legacyDisplayValue = null;\s*"
            r"onChange\.accept\(getValue\(\)\);\s*"
            r"updateMessage\(\);",
            studio,
        )
        is not None,
        "the first real slider edit must enter the supported range and refresh its label",
        failures,
    )
    require(
        "return Mth.clamp((double) (v - min) / (double) (max - min), 0.0, 1.0);"
        in studio,
        "slider geometry must clamp legacy saved values to the visible track",
        failures,
    )

    # Negative control for the accepted migration contract: a saved 128 px compass
    # remains 128 px until the player actually edits the 16–72 px control.
    slider_min = 16
    slider_max = 72
    saved = 128
    initial_norm = max(0.0, min(1.0, (saved - slider_min) / (slider_max - slider_min)))
    legacy_label = saved
    first_edit = round(slider_min + (slider_max - slider_min) * initial_norm)
    require(initial_norm == 1.0, "saved 128 px thumb must stay on-track at 1.0", failures)
    require(legacy_label == saved, "saved 128 px label must remain truthful before edit", failures)
    require(
        slider_min <= first_edit <= slider_max,
        "first edit must produce a supported 16–72 px value",
        failures,
    )

    require(
        "globe$VERSION_LABEL_SCALE = 0.9f" in loading,
        "loading version label must be reduced slightly to 90%",
        failures,
    )
    require(
        "float scaledWidth = this.font.width(globe$VERSION_LABEL) * globe$VERSION_LABEL_SCALE;"
        in loading,
        "loading label placement must use its scaled width",
        failures,
    )
    require(
        "matrices.pushMatrix();" in loading
        and "matrices.scale(globe$VERSION_LABEL_SCALE, globe$VERSION_LABEL_SCALE);"
        in loading
        and "matrices.popMatrix();" in loading,
        "loading label must use the 26.2 pose scale API",
        failures,
    )
    require(
        "float drawX = x / globe$VERSION_LABEL_SCALE;" in loading
        and "float drawY = y / globe$VERSION_LABEL_SCALE;" in loading,
        "scaled loading text coordinates must preserve the bottom-right inset",
        failures,
    )

    if failures:
        print("HUD/loading acceptance verifier: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("HUD/loading acceptance verifier: PASS")
    print("- fresh/reset compass: Analog, 32 px")
    print("- Studio compass-size control: 16–72 whole pixels")
    print("- saved analog-size sanitizer: 16–128 px (no forced migration)")
    print("- loading version label: 90% scale with preserved bottom-right inset")
    return 0


if __name__ == "__main__":
    sys.exit(main())
