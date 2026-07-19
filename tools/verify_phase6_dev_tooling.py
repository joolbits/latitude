#!/usr/bin/env python3
"""Structural and optional artifact proof for Latitude Phase 6 dev-only tooling."""

from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
from pathlib import PurePosixPath
import re
import sys
import tempfile
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
    globe_client = read("src/main/java/com/example/globe/GlobeModClient.java")
    command = read("src/main/java/com/example/globe/dev/LatitudeDevCommand.java")
    capture = read("src/main/java/com/example/globe/dev/DevCaptureKeybind.java")
    runtime = read("src/main/java/com/example/globe/dev/LatitudeDevRuntime.java")
    auto_probe = read("src/main/java/com/example/globe/dev/AutoCreateWorldProbe.java")
    seam_mode = read("src/main/java/com/example/globe/dev/audit/SeamAuditMode.java")
    seam_bridge = read("src/main/java/com/example/globe/dev/client/SeamAuditClientBridge.java")
    seam_harness = read("src/main/java/com/example/globe/dev/client/audit/SeamAuditHarness.java")
    test_common = read("src/latitudeTest/java/com/example/globe/dev/LatitudeDevTestEntrypoint.java")
    test_client = read("src/latitudeTest/java/com/example/globe/dev/LatitudeDevTestClientEntrypoint.java")
    trace = read("src/main/java/com/example/globe/dev/DevPresentationTrace.java")
    session = read("src/main/java/com/example/globe/dev/DevTestSession.java")
    build = read("build.gradle")
    production_metadata = read("src/main/resources/fabric.mod.json")

    forbid(globe, 'Commands.literal("flyspeed")', "public top-level flyspeed command", failures)
    require(
        globe,
        "if (!FabricLoader.getInstance().isDevelopmentEnvironment())",
        "unchanged public common development guard",
        failures,
    )
    require(
        globe_client,
        "if (FabricLoader.getInstance().isDevelopmentEnvironment())",
        "unchanged public client development guard",
        failures,
    )
    forbid(production_metadata, "latitude:test_artifact", "production TEST marker", failures)
    forbid(production_metadata, "LatitudeDevTestEntrypoint", "production TEST entrypoint", failures)
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
    tp_lat_method = method_body(command, "private static int tpLat(")
    case_context_method = method_body(command, "private static Map<String, String> caseContext(")
    require(
        tp_lat_method,
        "LatitudeMath.worldRadiusBlocks(border)",
        "tpLat production border-half-size authority",
        failures,
    )
    require(
        case_context_method,
        "LatitudeMath.worldRadiusBlocks(border)",
        "case context production border-half-size authority",
        failures,
    )
    forbid(
        tp_lat_method,
        "authoritativeRadius(source)",
        "tpLat padded audit-radius authority",
        failures,
    )
    forbid(
        case_context_method,
        "authoritativeRadius(source)",
        "case context padded audit-radius authority",
        failures,
    )
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
    require(capture, '"captures-v3.csv"', "versioned TEST-identity provenance CSV", failures)
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
    require(trace, '"clock_resync"', "same-dimension clock resync marker", failures)
    require(
        trace,
        "TraceContextAction.CLOCK_RESYNC",
        "same-dimension rollback continuity branch",
        failures,
    )
    require(
        trace,
        "TraceContextAction.DIMENSION_RESET",
        "real dimension reset branch",
        failures,
    )
    require(trace, '"policy_tick"', "monotonic warning policy tick evidence", failures)
    forbid(
        trace,
        "!active.lastDimension.equals(dimension) || worldTick < active.lastWorldTick",
        "rollback folded into context reset",
        failures,
    )
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
    require(command, "LatitudeDevRuntime.identity()", "shared runtime case identity", failures)
    for identity_field in (
        '"artifact_role"',
        '"build_dirty"',
        '"build_time"',
        '"git_branch"',
        '"git_commit"',
        '"mod_version"',
        '"test_sequence"',
    ):
        require(command, identity_field, f"case identity field {identity_field}", failures)
    forbid(command, 'System.getProperty("latitude.dev.', "ambient packaged case identity", failures)
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
    require(build, "latitudeTest {", "dedicated latitudeTest source set", failures)
    require(
        build,
        "tasks.register('latitudeTestJar', Jar)",
        "explicit TEST artifact task",
        failures,
    )
    require(
        build,
        "latitudeTestSequence is required for TEST artifact tasks",
        "positive sequence requirement",
        failures,
    )
    require(
        build,
        "inputs.property('latitudeTestSequence', latitudeTestSequenceInput)",
        "sequence-aware TEST task cache key",
        failures,
    )
    require(
        build,
        "inputs.property('latitudeTestVersion', latitudeTestVersionInput)",
        "version-aware TEST task cache key",
        failures,
    )
    require(
        build,
        "metadata.custom = ['latitude:test_artifact': true]",
        "isolated TEST metadata marker",
        failures,
    )
    require(
        build,
        "zipTree(tasks.named('jar', Jar).get().archiveFile.get().asFile)",
        "public byte-base reuse",
        failures,
    )
    require(
        build,
        "publicManifest.mainAttributes",
        "TEST manifest identity inherited from public byte base",
        failures,
    )
    forbid(
        build,
        "dependsOn tasks.named('latitudeTestJar')",
        "TEST artifact wired into another task",
        failures,
    )

    for needle, label in (
        ("DevToolPolicy.packagedTestIdentityValid(", "fail-closed packaged identity"),
        ("CustomValue.CvType.BOOLEAN", "boolean Fabric marker"),
        ('attribute(manifest, "Latitude-Artifact-Role")', "manifest role"),
        ('attribute(manifest, "Latitude-Test-Sequence")', "manifest sequence"),
        ('attribute(manifest, "Latitude-Artifact-Version")', "manifest artifact version"),
        ("private static final BuildIdentity IDENTITY = resolve();", "immutable shared identity"),
    ):
        require(runtime, needle, label, failures)

    for source, label in (
        (capture, "capture keybind"),
        (seam_mode, "seam mode"),
        (seam_bridge, "seam bridge"),
        (seam_harness, "seam harness"),
    ):
        require(
            source,
            "LatitudeDevRuntime.isToolingEnabled()",
            f"{label} shared runtime guard",
            failures,
        )
    require(
        auto_probe,
        "DevToolPolicy.autoCreateWorldProbeEnabled(",
        "auto-create explicit packaged policy",
        failures,
    )
    require(
        auto_probe,
        "LatitudeDevRuntime.isPackagedTestArtifact()",
        "auto-create packaged TEST identity",
        failures,
    )
    require(capture, "LatitudeDevRuntime.identity()", "shared capture identity", failures)
    for field in ('"artifact_role"', '"test_sequence"'):
        require(capture, field, f"capture identity field {field}", failures)

    for entrypoint, method, label in (
        (test_common, "AtomicBoolean INITIALIZED", "common"),
        (test_client, "AtomicBoolean INITIALIZED", "client"),
    ):
        require(entrypoint, method, f"{label} once-only entrypoint", failures)
        require(
            entrypoint,
            "LatitudeDevRuntime.isDevelopmentEnvironment()",
            f"{label} Loom duplicate guard",
            failures,
        )
        require(
            entrypoint,
            "LatitudeDevRuntime.isPackagedTestArtifact()",
            f"{label} packaged identity gate",
            failures,
        )
    require(test_common, "LatitudeDevCommand.register(dispatcher)", "TEST command registration", failures)
    require(test_common, "BiomePreviewHeadlessRunner.register()", "TEST headless registration", failures)
    for client_symbol in (
        "net.minecraft.client",
        "ClientModInitializer",
        "DevCaptureKeybind",
        "SeamAuditClientBridge",
        "SeamAuditHarness",
        "AutoCreateWorldProbe",
    ):
        forbid(test_common, client_symbol, f"common entrypoint client symbol {client_symbol}", failures)
    for client_init in (
        "DevCaptureKeybind.init()",
        "SeamAuditClientBridge.init()",
        "SeamAuditHarness.init()",
        "AutoCreateWorldProbe.maybeRegister()",
    ):
        require(test_client, client_init, f"TEST client initialization {client_init}", failures)

    for property_name in (
        "latitude.dev.gitCommit",
        "latitude.dev.gitBranch",
        "latitude.dev.buildDirty",
        "latitude.dev.buildTime",
    ):
        require(build, property_name, f"dev runtime identity property {property_name}", failures)


PUBLIC_MAIN_ENTRYPOINT = "com.example.globe.GlobeMod"
PUBLIC_CLIENT_ENTRYPOINT = "com.example.globe.GlobeModClient"
TEST_MAIN_ENTRYPOINT = "com.example.globe.dev.LatitudeDevTestEntrypoint"
TEST_CLIENT_ENTRYPOINT = "com.example.globe.dev.LatitudeDevTestClientEntrypoint"
TEST_MARKER_KEY = "latitude:test_artifact"
IDENTITY_ENTRIES = {"META-INF/MANIFEST.MF", "fabric.mod.json"}


def load_zip_entries(
    jar_path: Path,
    label: str,
    failures: list[str],
) -> tuple[dict[str, bytes], list[str]]:
    if not jar_path.is_file():
        failures.append(f"{label} jar not found: {jar_path}")
        return {}, []
    try:
        with zipfile.ZipFile(jar_path) as archive:
            infos = archive.infolist()
            names = [info.filename for info in infos]
            duplicates = sorted(name for name, count in Counter(names).items() if count != 1)
            if duplicates:
                failures.append(f"{label} duplicate ZIP entries: {duplicates[:5]}")
            for name in names:
                parts = PurePosixPath(name).parts
                if (
                    not name
                    or name.startswith(("/", "\\"))
                    or "\\" in name
                    or ".." in parts
                    or name.startswith("./")
                    or "//" in name
                ):
                    failures.append(f"{label} path traversal or noncanonical ZIP entry: {name!r}")
            entries: dict[str, bytes] = {}
            for info in infos:
                if not info.is_dir() and info.filename not in entries:
                    entries[info.filename] = archive.read(info)
            return entries, names
    except (OSError, zipfile.BadZipFile) as error:
        failures.append(f"{label} unreadable jar: {error}")
        return {}, []


def parse_manifest(payload: bytes, label: str, failures: list[str]) -> dict[str, str]:
    try:
        raw_lines = payload.decode("utf-8").replace("\r\n", "\n").split("\n")
    except UnicodeDecodeError as error:
        failures.append(f"{label} manifest is not UTF-8: {error}")
        return {}
    lines: list[str] = []
    for line in raw_lines:
        if line.startswith(" ") and lines:
            lines[-1] += line[1:]
        elif line:
            lines.append(line)
    values: dict[str, str] = {}
    for line in lines:
        if ": " not in line:
            failures.append(f"{label} malformed manifest line: {line!r}")
            continue
        key, value = line.split(": ", 1)
        if key in values:
            failures.append(f"{label} duplicate manifest attribute: {key}")
        values[key] = value
    return values


def serialize_manifest(values: dict[str, str]) -> bytes:
    ordered = ["Manifest-Version"] + sorted(key for key in values if key != "Manifest-Version")
    return (
        "\r\n".join(f"{key}: {values[key]}" for key in ordered if key in values)
        + "\r\n\r\n"
    ).encode("utf-8")


def parse_metadata(payload: bytes, label: str, failures: list[str]) -> dict[str, object]:
    try:
        value = json.loads(payload)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        failures.append(f"{label} invalid fabric.mod.json: {error}")
        return {}
    if not isinstance(value, dict):
        failures.append(f"{label} fabric.mod.json root is not an object")
        return {}
    return value


def reject_local_paths(entries: dict[str, bytes], label: str, failures: list[str]) -> None:
    local_markers = (
        b"<home>/",
        b"/" + b"Users" + b"/" + b"example" + b"/",
        b"C:" + b"\\" + b"Users" + b"\\" + b"example" + b"\\",
        str(ROOT).encode("utf-8"),
    )
    for name, payload in entries.items():
        if any(marker in payload for marker in local_markers):
            failures.append(f"{label} contains a local absolute path in {name}")
            return


def verify_public_entries(
    entries: dict[str, bytes],
    jar_path: Path,
    failures: list[str],
) -> tuple[dict[str, object], dict[str, str]]:
    if not entries:
        return {}, {}
    dev_entries = sorted(
        name
        for name in entries
        if name.startswith(("com/example/globe/dev/", "com/example/globe/debug/"))
        or name.startswith("com/example/globe/client/ClipboardImageWriter")
    )
    if dev_entries:
        failures.append(f"public jar contains dev classes: {dev_entries[:5]}")
    if any(name.startswith(("tools/", "tmp/")) for name in entries):
        failures.append("public jar contains tool or task-evidence entries")
    reject_local_paths(entries, "public jar", failures)

    metadata = parse_metadata(entries.get("fabric.mod.json", b""), "public", failures)
    manifest = parse_manifest(entries.get("META-INF/MANIFEST.MF", b""), "public", failures)
    entrypoints = metadata.get("entrypoints")
    if entrypoints != {
        "main": [PUBLIC_MAIN_ENTRYPOINT],
        "client": [PUBLIC_CLIENT_ENTRYPOINT],
    }:
        failures.append(f"public entrypoints are not exact: {entrypoints!r}")
    custom = metadata.get("custom")
    if isinstance(custom, dict) and TEST_MARKER_KEY in custom:
        failures.append("public metadata contains TEST marker")
    if "-test." in str(metadata.get("version", "")).lower():
        failures.append("public metadata version contains TEST sequence")
    for key in (
        "Latitude-Artifact-Role",
        "Latitude-Test-Sequence",
        "Latitude-Artifact-Version",
    ):
        if key in manifest:
            failures.append(f"public manifest contains TEST identity attribute: {key}")

    payload = b"".join(
        value
        for name, value in entries.items()
        if name.endswith((".class", ".json", ".properties", ".mf", ".MF"))
    )
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
    if not jar_path.name.endswith(".jar"):
        failures.append(f"public artifact is not a jar: {jar_path.name}")
    return metadata, manifest


def expected_test_extras(main_classes: Path, test_classes: Path) -> dict[str, bytes]:
    extras: dict[str, bytes] = {}
    if main_classes.is_dir():
        for path in main_classes.rglob("*.class"):
            relative = path.relative_to(main_classes).as_posix()
            if (
                relative.startswith(("com/example/globe/dev/", "com/example/globe/debug/"))
                or relative.startswith("com/example/globe/client/ClipboardImageWriter")
            ):
                extras[relative] = path.read_bytes()
    if test_classes.is_dir():
        for path in test_classes.rglob("*.class"):
            extras[path.relative_to(test_classes).as_posix()] = path.read_bytes()
    return extras


def verify_pair(
    public_jar: Path,
    test_jar: Path,
    sequence: int,
    main_classes: Path,
    test_classes: Path,
    failures: list[str],
) -> None:
    public_entries, _ = load_zip_entries(public_jar, "public", failures)
    test_entries, _ = load_zip_entries(test_jar, "TEST", failures)
    public_metadata, public_manifest = verify_public_entries(
        public_entries, public_jar, failures
    )
    if not public_entries or not test_entries:
        return
    reject_local_paths(test_entries, "TEST jar", failures)

    missing_shared = sorted(set(public_entries) - set(test_entries) - IDENTITY_ENTRIES)
    if missing_shared:
        failures.append(f"TEST jar is missing public entries: {missing_shared[:5]}")
    changed_shared = sorted(
        name
        for name in set(public_entries) & set(test_entries) - IDENTITY_ENTRIES
        if public_entries[name] != test_entries[name]
    )
    if changed_shared:
        failures.append(f"shared public/TEST entries are not byte-identical: {changed_shared[:5]}")

    expected_extra_payloads = expected_test_extras(main_classes, test_classes)
    expected_extras = set(expected_extra_payloads)
    actual_extras = set(test_entries) - set(public_entries) - IDENTITY_ENTRIES
    missing_extras = sorted(expected_extras - actual_extras)
    unexpected_extras = sorted(actual_extras - expected_extras)
    if missing_extras:
        failures.append(f"TEST jar missing exact compiled extras: {missing_extras[:5]}")
    if unexpected_extras:
        failures.append(f"TEST jar has unexpected extras: {unexpected_extras[:5]}")
    changed_extras = sorted(
        name
        for name in expected_extras & actual_extras
        if test_entries[name] != expected_extra_payloads[name]
    )
    if changed_extras:
        failures.append(f"TEST extra bytes differ from compiled classes: {changed_extras[:5]}")

    metadata = parse_metadata(test_entries.get("fabric.mod.json", b""), "TEST", failures)
    manifest = parse_manifest(test_entries.get("META-INF/MANIFEST.MF", b""), "TEST", failures)
    public_version = str(public_metadata.get("version", ""))
    expected_version = f"{public_version}-test.{sequence}"
    if metadata.get("version") != expected_version:
        failures.append(
            f"TEST metadata version mismatch: expected={expected_version!r} "
            f"actual={metadata.get('version')!r}"
        )
    expected_entrypoints = {
        "main": [PUBLIC_MAIN_ENTRYPOINT, TEST_MAIN_ENTRYPOINT],
        "client": [PUBLIC_CLIENT_ENTRYPOINT, TEST_CLIENT_ENTRYPOINT],
    }
    if metadata.get("entrypoints") != expected_entrypoints:
        failures.append(f"TEST entrypoints are not exact: {metadata.get('entrypoints')!r}")
    if metadata.get("custom") != {TEST_MARKER_KEY: True}:
        failures.append(f"TEST marker is not exact and boolean: {metadata.get('custom')!r}")
    if metadata.get("jars") != public_metadata.get("jars"):
        failures.append("public/TEST Fabric nested-jar metadata differs")
    expected_metadata = json.loads(json.dumps(public_metadata))
    expected_metadata["version"] = expected_version
    expected_metadata["entrypoints"] = expected_entrypoints
    expected_metadata["custom"] = {TEST_MARKER_KEY: True}
    if metadata != expected_metadata:
        failures.append("TEST metadata differs from authorized public identity delta")

    expected_manifest = {
        "Implementation-Version": expected_version,
        "Latitude-Artifact-Role": "TEST",
        "Latitude-Test-Sequence": str(sequence),
        "Latitude-Artifact-Version": expected_version,
        "Fabric-Mapping-Namespace": "official",
    }
    for key, expected in expected_manifest.items():
        if manifest.get(key) != expected:
            failures.append(
                f"TEST manifest identity mismatch {key}: "
                f"expected={expected!r} actual={manifest.get(key)!r}"
            )
    for key in ("Git-Commit", "Git-Branch", "Build-Dirty", "Build-Time", "Minecraft-Version"):
        if manifest.get(key) != public_manifest.get(key):
            failures.append(f"public/TEST immutable manifest identity differs: {key}")
    if not re.fullmatch(r"[0-9a-f]{40}", manifest.get("Git-Commit", "")):
        failures.append("TEST manifest commit is not an immutable full hash")
    if not manifest.get("Git-Branch") or manifest.get("Git-Branch") == "unknown":
        failures.append("TEST manifest branch is missing or unknown")
    if manifest.get("Build-Dirty") not in {"true", "false"}:
        failures.append("TEST manifest dirty state is not boolean")
    if not re.fullmatch(
        r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z",
        manifest.get("Build-Time", ""),
    ):
        failures.append("TEST manifest build time is not immutable UTC")
    if f"-test.{sequence}" not in test_jar.name:
        failures.append("TEST filename does not agree with sequence")

    public_nested = sorted(
        name for name in public_entries if name.startswith("META-INF/jars/") and name.endswith(".jar")
    )
    test_nested = sorted(
        name for name in test_entries if name.startswith("META-INF/jars/") and name.endswith(".jar")
    )
    if len(public_nested) != 1 or "mixinextras" not in public_nested[0].lower():
        failures.append(f"public nested MixinExtras set is not exact: {public_nested}")
    if test_nested != public_nested:
        failures.append(f"TEST nested MixinExtras set differs: {test_nested}")
    elif public_nested and test_entries[public_nested[0]] != public_entries[public_nested[0]]:
        failures.append("TEST nested MixinExtras bytes differ from public jar")


def write_modified_jar(
    source: Path,
    destination: Path,
    replacements: dict[str, bytes] | None = None,
    removals: set[str] | None = None,
    additions: dict[str, bytes] | None = None,
    duplicate_name: str | None = None,
) -> None:
    replacements = replacements or {}
    removals = removals or set()
    additions = additions or {}
    with zipfile.ZipFile(source) as incoming, zipfile.ZipFile(
        destination, "w", compression=zipfile.ZIP_DEFLATED
    ) as outgoing:
        for info in incoming.infolist():
            if info.filename in removals:
                continue
            outgoing.writestr(info, replacements.get(info.filename, incoming.read(info)))
        for name, payload in additions.items():
            outgoing.writestr(name, payload)
        if duplicate_name is not None:
            outgoing.writestr(duplicate_name, incoming.read(duplicate_name))


def verify_negative_fixtures(
    public_jar: Path,
    test_jar: Path,
    sequence: int,
    main_classes: Path,
    test_classes: Path,
    failures: list[str],
) -> None:
    with tempfile.TemporaryDirectory(prefix="latitude-test-verifier-") as raw:
        root = Path(raw)

        def expect_rejected(
            label: str,
            needle: str,
            public_candidate: Path,
            test_candidate: Path,
        ) -> None:
            observed: list[str] = []
            verify_pair(
                public_candidate,
                test_candidate,
                sequence,
                main_classes,
                test_classes,
                observed,
            )
            if not any(needle in failure for failure in observed):
                failures.append(
                    f"negative corruption fixture was not rejected as {label}: {observed}"
                )

        duplicate = root / "duplicate.jar"
        write_modified_jar(test_jar, duplicate, duplicate_name="fabric.mod.json")
        expect_rejected("duplicate", "duplicate ZIP entries", public_jar, duplicate)

        traversal = root / "traversal.jar"
        write_modified_jar(test_jar, traversal, additions={"../escape.class": b"x"})
        expect_rejected("path traversal", "path traversal", public_jar, traversal)

        test_entries, _ = load_zip_entries(test_jar, "fixture-source", failures)
        metadata = json.loads(test_entries["fabric.mod.json"])
        metadata["custom"][TEST_MARKER_KEY] = False
        marker = root / "marker.jar"
        write_modified_jar(
            test_jar,
            marker,
            replacements={"fabric.mod.json": json.dumps(metadata).encode("utf-8")},
        )
        expect_rejected("marker", "TEST marker", public_jar, marker)

        metadata = json.loads(test_entries["fabric.mod.json"])
        metadata["entrypoints"]["client"].remove(TEST_CLIENT_ENTRYPOINT)
        missing_entrypoint = root / "missing-entrypoint.jar"
        write_modified_jar(
            test_jar,
            missing_entrypoint,
            replacements={"fabric.mod.json": json.dumps(metadata).encode("utf-8")},
        )
        expect_rejected(
            "missing entrypoint", "TEST entrypoints are not exact", public_jar, missing_entrypoint
        )

        manifest = parse_manifest(test_entries["META-INF/MANIFEST.MF"], "fixture", failures)
        manifest["Latitude-Test-Sequence"] = str(sequence + 1)
        sequence_mismatch = root / "sequence.jar"
        write_modified_jar(
            test_jar,
            sequence_mismatch,
            replacements={"META-INF/MANIFEST.MF": serialize_manifest(manifest)},
        )
        expect_rejected(
            "sequence mismatch", "Latitude-Test-Sequence", public_jar, sequence_mismatch
        )

        nested = next(
            name
            for name in test_entries
            if name.startswith("META-INF/jars/") and name.endswith(".jar")
        )
        nested_mismatch = root / "nested.jar"
        write_modified_jar(
            test_jar,
            nested_mismatch,
            replacements={nested: test_entries[nested] + b"corrupt"},
        )
        expect_rejected(
            "nested dependency mismatch",
            "nested MixinExtras bytes differ",
            public_jar,
            nested_mismatch,
        )

        runtime_class = "com/example/globe/dev/LatitudeDevRuntime.class"
        corrupt_extra = root / "corrupt-extra.jar"
        write_modified_jar(
            test_jar,
            corrupt_extra,
            replacements={runtime_class: test_entries[runtime_class] + b"corrupt"},
        )
        expect_rejected(
            "corrupt TEST class",
            "TEST extra bytes differ from compiled classes",
            public_jar,
            corrupt_extra,
        )

        metadata = json.loads(test_entries["fabric.mod.json"])
        metadata["id"] = "forged-globe"
        forged_metadata = root / "forged-metadata.jar"
        write_modified_jar(
            test_jar,
            forged_metadata,
            replacements={"fabric.mod.json": json.dumps(metadata).encode("utf-8")},
        )
        expect_rejected(
            "forged metadata",
            "TEST metadata differs from authorized public identity delta",
            public_jar,
            forged_metadata,
        )

        public_contamination = root / "public-contamination.jar"
        write_modified_jar(
            public_jar,
            public_contamination,
            additions={"com/example/globe/dev/Forged.class": b"x"},
        )
        expect_rejected(
            "public contamination",
            "public jar contains dev classes",
            public_contamination,
            test_jar,
        )

        local_path = root / "local-path.jar"
        write_modified_jar(
            test_jar,
            local_path,
            additions={"local-path.txt": b"<home>/forged"},
        )
        expect_rejected("local path", "local absolute path", public_jar, local_path)


def verify_negative_task_graph(path: Path, failures: list[str]) -> None:
    if not path.is_file():
        failures.append(f"negative task graph log not found: {path}")
        return
    text = path.read_text(encoding="utf-8")
    for forbidden in (
        ":generateLatitudeTestMetadata",
        ":compileLatitudeTestJava",
        ":latitudeTestClasses",
        ":latitudeTestJar",
    ):
        if forbidden in text:
            failures.append(f"normal/public task graph contains TEST task: {forbidden}")
    for required in (":clean", ":jar", ":sourcesJar", ":build"):
        if required not in text:
            failures.append(f"negative task graph did not exercise expected task: {required}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, help="compatibility alias for --public-jar")
    parser.add_argument("--public-jar", type=Path)
    parser.add_argument("--test-jar", type=Path)
    parser.add_argument("--sequence", type=int)
    parser.add_argument(
        "--main-classes",
        type=Path,
        default=ROOT / "build/classes/java/main",
    )
    parser.add_argument(
        "--test-classes",
        type=Path,
        default=ROOT / "build/classes/java/latitudeTest",
    )
    parser.add_argument("--negative-task-graph", type=Path)
    parser.add_argument("--negative-fixtures", action="store_true")
    args = parser.parse_args()

    failures: list[str] = []
    verify_sources(failures)
    public_jar = args.public_jar or args.jar
    if args.test_jar and not public_jar:
        failures.append("--test-jar requires --public-jar")
    elif args.test_jar:
        if args.sequence is None or args.sequence <= 0:
            failures.append("--test-jar requires a positive --sequence")
        else:
            verify_pair(
                public_jar.resolve(),
                args.test_jar.resolve(),
                args.sequence,
                args.main_classes.resolve(),
                args.test_classes.resolve(),
                failures,
            )
            if args.negative_fixtures and not failures:
                verify_negative_fixtures(
                    public_jar.resolve(),
                    args.test_jar.resolve(),
                    args.sequence,
                    args.main_classes.resolve(),
                    args.test_classes.resolve(),
                    failures,
                )
    elif public_jar:
        entries, _ = load_zip_entries(public_jar.resolve(), "public", failures)
        verify_public_entries(entries, public_jar.resolve(), failures)
    if args.negative_task_graph:
        verify_negative_task_graph(args.negative_task_graph.resolve(), failures)

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}", file=sys.stderr)
        return 1
    print("PHASE6_DEV_TOOLING_VERIFY_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
