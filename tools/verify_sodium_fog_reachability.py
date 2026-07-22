#!/usr/bin/env python3
"""Verify Latitude's supported Sodium 0.9.1 fog-culling integration from exact class bytes."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path


EXPECTED_SODIUM_SHA256 = "de406c7a0ca5e748dfbe44740278400882a44e3109e2584b243ec02d4003344b"
DEAD_MIXIN = "client.compat.sodium.RenderSectionManagerVisibilityMixin"
DEAD_SOURCE = Path("src/main/java/com/example/globe/mixin/client/compat/sodium/RenderSectionManagerVisibilityMixin.java")
FOG_SOURCE = Path("src/main/java/com/example/globe/mixin/client/FogRendererEwMixin.java")
MIXIN_CONFIG = Path("src/main/resources/globe.mixins.json")


class VerificationError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise VerificationError(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def javap(javap_bin: str, jar: Path, class_name: str, *, verbose: bool = False) -> str:
    flags = ["-v", "-p"] if verbose else ["-c", "-p"]
    result = subprocess.run(
        [javap_bin, "-classpath", str(jar), *flags, class_name],
        check=False,
        capture_output=True,
        text=True,
    )
    require(result.returncode == 0, f"javap failed for {class_name}: {result.stderr.strip()}")
    return result.stdout


def method_section(bytecode: str, signature: str) -> str:
    start = bytecode.find(signature)
    require(start >= 0, f"missing expected method: {signature}")
    next_declaration = re.search(r"^  \S.*;$", bytecode[start + len(signature):], re.MULTILINE)
    if next_declaration is not None:
        return bytecode[start:start + len(signature) + next_declaration.start()]
    return bytecode[start:]


def verify_sodium_chain(fog_mixin: str, renderer: str) -> None:
    for token in (
        "sodium$storeFogParameters",
        "net/minecraft/client/renderer/fog/FogData.environmentalStart",
        "net/minecraft/client/renderer/fog/FogData.environmentalEnd",
        "net/minecraft/client/renderer/fog/FogData.renderDistanceStart",
        "net/minecraft/client/renderer/fog/FogData.renderDistanceEnd",
        'method=["setupFog"]',
        'value="RETURN"',
    ):
        require(token in fog_mixin, f"Sodium FogData snapshot drifted or is missing: {token}")

    search = "getSearchDistance:(Lnet/caffeinemc/mods/sodium/client/util/FogParameters;)F"
    traverse = "SectionTree.traverse:"
    search_method = method_section(renderer, "private float getSearchDistance(")
    traversal_method = method_section(renderer, "private void readRenderListFromTree(")
    require("useFogOcclusion:Z" in search_method, "Sodium fog-occlusion user setting is no longer consulted")
    require("getEffectiveRenderDistance:(Lnet/caffeinemc/mods/sodium/client/util/FogParameters;)F" in search_method,
            "Sodium no longer derives effective distance from FogParameters")
    require("getRenderDistance:()F" in search_method,
            "Sodium no longer preserves uncapped distance when fog occlusion is disabled")
    require(search in traversal_method, "FogParameters no longer reach RenderSectionManager.getSearchDistance")
    require(traverse in traversal_method, "RenderSectionManager no longer reaches SectionTree.traverse")
    require(traversal_method.index(search) < traversal_method.index(traverse),
            "FogParameters search distance does not precede SectionTree.traverse")
    require("isSectionVisible(int, int, int)" not in renderer,
            "obsolete isSectionVisible target unexpectedly exists; compatibility design must be re-audited")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sodium-jar", required=True, type=Path)
    parser.add_argument("--source-root", required=True, type=Path)
    args = parser.parse_args()

    jar = args.sodium_jar.resolve()
    root = args.source_root.resolve()
    require(jar.is_file(), f"missing Sodium artifact: {jar}")
    require(sha256(jar) == EXPECTED_SODIUM_SHA256, "Sodium artifact SHA-256 differs from supported 0.9.1 bytes")

    with zipfile.ZipFile(jar) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        require(metadata.get("version") == "0.9.1+mc26.2", "unexpected Sodium version metadata")

    javap_bin = shutil.which("javap")
    require(javap_bin is not None, "javap is unavailable")
    fog_mixin = javap(
        javap_bin,
        jar,
        "net.caffeinemc.mods.sodium.mixin.core.render.world.FogRendererMixin",
        verbose=True,
    )
    renderer = javap(
        javap_bin,
        jar,
        "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager",
    )
    verify_sodium_chain(fog_mixin, renderer)

    # Negative control: the same verifier must reject a drifted traversal target.
    try:
        verify_sodium_chain(fog_mixin, renderer.replace("SectionTree.traverse:", "SectionTree.driftedTraverse:"))
    except VerificationError:
        pass
    else:
        raise VerificationError("negative control failed to detect a drifted traverse target")

    mixins = json.loads((root / MIXIN_CONFIG).read_text())
    client_mixins = mixins.get("client", [])
    require(DEAD_MIXIN not in client_mixins, "dead Sodium isSectionVisible mixin remains registered")
    require(not (root / DEAD_SOURCE).exists(), "dead Sodium isSectionVisible mixin source remains present")

    fog_source = (root / FOG_SOURCE).read_text()
    require("@Mixin(value = FogRenderer.class, priority = 900)" in fog_source,
            "Latitude fog mixin lacks explicit priority 900 before Sodium's default-priority snapshot")

    print(f"SODIUM_FOG_REACHABILITY_PASS jar={jar.name} sha256={EXPECTED_SODIUM_SHA256}")
    print(" chain=FogData->FogParameters->getSearchDistance->SectionTree.traverse")
    print(" userSetting=useFogOcclusion-preserved negativeControl=drift-detected")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except VerificationError as error:
        print(f"SODIUM_FOG_REACHABILITY_FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
