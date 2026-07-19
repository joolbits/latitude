#!/usr/bin/env python3
"""Structural and optional artifact proof for Latitude Phase 6 dev-only tooling."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
import zipfile


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require(source: str, needle: str, label: str, failures: list[str]) -> None:
    if needle not in source:
        failures.append(f"missing {label}: {needle!r}")


def forbid(source: str, needle: str, label: str, failures: list[str]) -> None:
    if needle in source:
        failures.append(f"forbidden {label}: {needle!r}")


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
                return source[start : index + 1]
    return ""


def verify_sources(failures: list[str]) -> None:
    globe = read("src/main/java/com/example/globe/GlobeMod.java")
    command = read("src/main/java/com/example/globe/dev/LatitudeDevCommand.java")
    capture = read("src/main/java/com/example/globe/dev/DevCaptureKeybind.java")
    trace = read("src/main/java/com/example/globe/dev/DevPresentationTrace.java")
    session = read("src/main/java/com/example/globe/dev/DevTestSession.java")
    build = read("build.gradle")

    forbid(globe, 'Commands.literal("flyspeed")', "public top-level flyspeed command", failures)
    require(
        command,
        ".requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))",
        "level-2 /latdev permission predicate",
        failures,
    )
    for literal in ('"flyspeed"', '"tpLat"', '"case"', '"presentationTrace"'):
        require(command, f"Commands.literal({literal})", f"/latdev {literal} wiring", failures)
    require(command, "DoubleArgumentType.doubleArg(-90.0, 90.0)", "bounded latitude parser", failures)
    require(command, "DevToolPolicy.latitudeTarget(", "center/border teleport policy", failures)
    require(command, "targetZ + 0.5", "achieved block-center latitude report", failures)
    require(command, "source.getServer().isDedicatedServer()", "integrated-client boundary", failures)
    require(command, '"coordinate_policy_only"', "dedicated trace truth label", failures)

    capture_method = method_body(capture, "private static void capture(")
    snapshot_index = capture_method.find("freezeSnapshot(client)")
    screenshot_index = capture_method.find("Screenshot.takeScreenshot(")
    if snapshot_index < 0 or screenshot_index < 0 or snapshot_index >= screenshot_index:
        failures.append("capture provenance is not frozen before Screenshot.takeScreenshot")
    handle_method = method_body(capture, "private static void handleCapturedImage(")
    forbid(handle_method, "client.player.get", "post-request player provenance sampling", failures)
    forbid(handle_method, "client.level.get", "post-request world provenance sampling", failures)
    for field in (
        "sessionSequence",
        "worldTick",
        "dimension",
        "seed",
        "biome",
        "signedLatitudeDegrees",
        "zone",
        "yaw",
        "pitch",
        "guiScale",
        "version",
        "commit",
    ):
        require(capture, field, f"capture provenance field {field}", failures)
    require(capture, '"captures-v2.csv"', "versioned rich-provenance CSV", failures)
    forbid(capture, 'resolve("captures.csv")', "writes to legacy five-column CSV", failures)
    require(capture, "sha256(capturePath)", "saved screenshot SHA-256", failures)
    require(capture, "DevPresentationTrace.clientTick(client)", "client-tick presentation trace", failures)
    require(capture, "boolean requestOwned = requestAlreadyRecorded", "keybind request ownership", failures)
    require(capture, "if (requestOwned)", "keybind failure ownership guard", failures)

    require(trace, "GlobeClientState.computePolarStage(", "production stage policy use", failures)
    require(trace, "PolarPresentationPolicy.fogIntensity(", "production fog policy use", failures)
    require(trace, "warningEpisode.update(", "production warning episode use", failures)
    require(
        trace,
        '"integrated_client_computed_presentation_policy"',
        "honest computed-policy trace mode label",
        failures,
    )
    forbid(
        trace,
        '"integrated_client_rendered_presentation"',
        "unproved rendered-presentation trace label",
        failures,
    )
    require(trace, "GlobeClientState.evaluate(client)", "production applicability evaluation", failures)
    require(trace, "LatitudeConfig.showWarningMessages", "warning config applicability", failures)
    require(trace, '"context_reset"', "world/dimension trace reset marker", failures)
    require(trace, "Trace candidate = new Trace(", "write-before-activate trace start", failures)

    for event in ('"capture_requested"', '"capture_completed"', '"capture_failed"'):
        require(session, event, f"case lifecycle event {event}", failures)
    require(session, 'row.put("sequence", eventSequence)', "monotonic sequence field", failures)
    require(session, "sequence = nextSequence", "post-append sequence mutation", failures)
    require(session, "pendingFinishState", "recoverable finish-summary state", failures)
    require(
        session,
        "case finish is already recorded; only summary recovery may continue",
        "post-finish append guard",
        failures,
    )
    require(session, 'row.put("world_tick", worldTick)', "request-time tick field", failures)
    require(session, "new TreeMap<>(fields)", "stable extension field ordering", failures)
    require(session, "pendingCaptureLabel != null", "pending capture close guard", failures)
    require(
        session,
        "wait for completion or failure before requesting another",
        "overlapping capture request guard",
        failures,
    )
    request_append = session.find('session.append("capture_requested"')
    request_pending = session.find("session.pendingCaptureLabel = captureLabel")
    completion_append = session.find('session.append("capture_completed"')
    completion_clear = session.find("session.pendingCaptureLabel = null", completion_append)
    failure_append = session.find('session.append("capture_failed"')
    failure_clear = session.find("session.pendingCaptureLabel = null", failure_append)
    if request_append < 0 or request_pending < request_append:
        failures.append("capture request mutates pending state before append succeeds")
    if completion_append < 0 or completion_clear < completion_append:
        failures.append("capture completion clears pending state before append succeeds")
    if failure_append < 0 or failure_clear < failure_append:
        failures.append("capture failure clears pending state before append succeeds")
    require(command, '"capture_marker"', "dedicated marker-only capture event", failures)
    require(command, "if (requestRecorded &&", "capture failure ownership guard", failures)
    require(command, '"latitude.dev.gitCommit"', "runtime case git identity", failures)
    require(command, '"run_mode"', "runtime case capability mode", failures)

    require(
        build,
        "path.startsWith('com/example/globe/dev/')",
        "public jar dev-package exclusion",
        failures,
    )
    require(
        build,
        "tasks.register('latitudeDevToolPolicyTest', JavaExec)",
        "dependency-free dev-tool regression task",
        failures,
    )
    for property_name in (
        "latitude.dev.gitCommit",
        "latitude.dev.gitBranch",
        "latitude.dev.buildDirty",
        "latitude.dev.buildTime",
    ):
        require(build, property_name, f"dev runtime identity property {property_name}", failures)
        require(capture, property_name, f"capture identity fallback {property_name}", failures)


def verify_jar(jar_path: Path, failures: list[str]) -> None:
    if not jar_path.is_file():
        failures.append(f"jar not found: {jar_path}")
        return
    with zipfile.ZipFile(jar_path) as archive:
        names = archive.namelist()
        dev_entries = [name for name in names if name.startswith("com/example/globe/dev/")]
        if dev_entries:
            failures.append(f"public jar contains dev classes: {dev_entries[:5]}")
        if any(name.startswith(("tools/", "tmp/")) for name in names):
            failures.append("public jar contains tool or task-evidence entries")
        payload = b"".join(
            archive.read(name)
            for name in names
            if name.endswith((".class", ".json", ".properties", ".mf", ".MF"))
        )

    # Known inert references are intentionally accepted:
    # - GlobeMod reflectively names LatitudeDevCommand/BiomePreviewHeadlessRunner behind the
    #   development-environment gate.
    # - GlobeModClient has established direct symbolic references to excluded dev client classes,
    #   also behind the development-environment gate.
    # What must be absent is executable Phase 6 command/action payload from the excluded classes.
    for denied in (
        b"flyspeed",
        b"tpLat",
        b"presentationTrace",
        b"case started",
        b"capture_requested",
        b"latitude-dev-case-v1",
    ):
        if denied in payload:
            failures.append(f"public jar contains Phase 6 action payload: {denied!r}")
    if b"<home>/" in payload:
        failures.append("public jar contains a local absolute path")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, help="also inspect the exact public jar")
    args = parser.parse_args()

    failures: list[str] = []
    verify_sources(failures)
    if args.jar:
        verify_jar(args.jar.resolve(), failures)

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}", file=sys.stderr)
        return 1
    print("PHASE6_DEV_TOOLING_VERIFY_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
