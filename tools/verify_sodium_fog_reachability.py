#!/usr/bin/env python3
"""Verify Latitude's supported Sodium 0.8.13 fog-culling integration from exact class bytes.

Target-specific. Each Minecraft line needs its own edition of this verifier, because both Minecraft
and Sodium change shape between them:

* 26.2 ships Minecraft deobfuscated; 1.21.11 and 1.21.1 are obfuscated, so Sodium ships remapped to
  intermediary there.
* On 1.21.11 Sodium 0.8.13 SNAPSHOTS the fog: its core FogRendererMixin captures the FogData local
  inside setupFog at the updateBuffer call, so everything Latitude does had to happen before that
  instruction. On 1.21.1 there is no such mixin at all -- the only FogRenderer mixin Sodium ships
  is the unrelated sky one -- and the culling distance is read LIVE from RenderSystem instead:

      RenderSectionManager.createTerrainRenderList
          -> getSearchDistance()                       (consults useFogOcclusion)
              -> getEffectiveRenderDistance()
                  -> RenderSystem.getShaderFogEnd()    <- the value Latitude writes
          -> RemovableMultiForest.traverse(...)

  That makes the reachability requirement simpler and stronger here: Latitude writes the tightened
  fog end into RenderSystem at the TAIL of vanilla's own setupFog, and Sodium reads that same
  RenderSystem state later in the frame. There is no snapshot to get ahead of, but the write must
  still land after vanilla's own -- hence the TAIL pin asserted on the Latitude side below.
"""

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


EXPECTED_SODIUM_SHA256 = "3d43c14985a4deb19c654aad3393b454cf57e1ebcbba86c4ae6a98dfc60eca2d"
EXPECTED_SODIUM_VERSION = "0.8.13+mc1.21.1"
DEAD_MIXIN = "client.compat.sodium.RenderSectionManagerVisibilityMixin"
DEAD_SOURCE = Path("src/main/java/com/example/globe/mixin/client/compat/sodium/RenderSectionManagerVisibilityMixin.java")
COLOR_SOURCE = Path("src/main/java/com/example/globe/mixin/client/FogRendererEwMixin.java")
# Distances used to live in AtmosphericFogEnvironmentMixin. That hook ran at the
# FogEnvironment.setupFog call, three instructions before vanilla overwrote
# renderDistanceStart/End, so the far fog wall never rendered. Both passes now share the
# FogRenderer mixin; this constant guards against the environment hook coming back.
RETIRED_DISTANCE_SOURCE = Path(
    "src/main/java/com/example/globe/mixin/client/AtmosphericFogEnvironmentMixin.java")
MIXIN_CONFIG = Path("src/main/resources/globe.mixins.json")
FOG_PRESENTATION = Path("src/main/java/com/example/globe/client/LatitudeFogPresentation.java")


def read_source_or_empty(root: Path, relative: Path) -> str:
    candidate = root / relative
    return candidate.read_text() if candidate.is_file() else ""

# The live read Latitude's fog write has to reach.
SODIUM_FOG_READ = "com/mojang/blaze3d/systems/RenderSystem.getShaderFogEnd:()F"
# 0.8.13's traversal entry point; 0.9.1 called this SectionTree.traverse.
SODIUM_TRAVERSE = "RemovableMultiForest.traverse:"
SODIUM_SEARCH = "getSearchDistance:()F"


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
    # 1.21.1's Sodium ships no core FogRenderer mixin: the only one is the sky mixin, which does not
    # touch the culling distance. Assert that, so a future Sodium that starts snapshotting again
    # cannot slip past a verifier written for the live-read world.
    require("sodium$storeFogParameters" not in fog_mixin,
            "Sodium has started snapshotting fog parameters on this line; the live-read "
            "reachability argument no longer holds and this verifier must be rewritten")

    effective_method = method_section(renderer, "private float getEffectiveRenderDistance();")
    require(SODIUM_FOG_READ in effective_method,
            "Sodium no longer derives its effective distance from the live RenderSystem fog end")
    require("getRenderDistance:()F" in effective_method,
            "Sodium no longer clamps the fog-derived distance against the render distance")

    search_method = method_section(renderer, "private float getSearchDistance();")
    require("useFogOcclusion:Z" in search_method,
            "Sodium fog-occlusion user setting is no longer consulted")
    require("getEffectiveRenderDistance:()F" in search_method,
            "Sodium no longer derives effective distance from the fog end")
    require("getRenderDistance:()F" in search_method,
            "Sodium no longer preserves uncapped distance when fog occlusion is disabled")

    traversal_method = method_section(renderer, "private boolean createTerrainRenderList(")
    require(SODIUM_SEARCH in traversal_method,
            "the fog-derived search distance no longer reaches RenderSectionManager traversal")
    require(SODIUM_TRAVERSE in traversal_method,
            "RenderSectionManager no longer reaches the forest traversal")
    require(traversal_method.index(SODIUM_SEARCH) < traversal_method.index(SODIUM_TRAVERSE),
            "the search distance is not computed before the forest traversal")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sodium-jar", required=True, type=Path)
    parser.add_argument("--source-root", required=True, type=Path)
    args = parser.parse_args()

    jar = args.sodium_jar.resolve()
    root = args.source_root.resolve()
    require(jar.is_file(), f"missing Sodium artifact: {jar}")
    require(sha256(jar) == EXPECTED_SODIUM_SHA256,
            f"Sodium artifact SHA-256 differs from supported {EXPECTED_SODIUM_VERSION} bytes")

    with zipfile.ZipFile(jar) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        require(metadata.get("version") == EXPECTED_SODIUM_VERSION,
                "unexpected Sodium version metadata")

    javap_bin = shutil.which("javap")
    require(javap_bin is not None, "javap is unavailable")
    # The sky mixin is the only FogRenderer mixin this Sodium ships; reading it is how the
    # "no snapshot on this line" claim is checked rather than assumed.
    fog_mixin = javap(
        javap_bin,
        jar,
        "net.caffeinemc.mods.sodium.mixin.features.render.world.sky.FogRendererMixin",
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
        verify_sodium_chain(fog_mixin, renderer.replace(SODIUM_TRAVERSE, "RemovableMultiForest.driftedTraverse:"))
    except VerificationError:
        pass
    else:
        raise VerificationError("negative control failed to detect a drifted traverse target")

    mixins = json.loads((root / MIXIN_CONFIG).read_text())
    client_mixins = mixins.get("client", [])
    # Latitude deliberately does not hook RenderSectionManager.isSectionVisible: fog occlusion via
    # the live RenderSystem fog end is the supported mechanism, and the placement-time visibility
    # mixin stays retired.
    require(DEAD_MIXIN not in client_mixins, "dead Sodium isSectionVisible mixin remains registered")
    require(not (root / DEAD_SOURCE).exists(), "dead Sodium isSectionVisible mixin source remains present")

    # Both fog passes live in the FogRenderer mixin and must outrank Sodium's default-priority
    # mixin, which snapshots at setupFog's updateBuffer call.
    fog_source = (root / COLOR_SOURCE).read_text()
    require("@Mixin(value = FogRenderer.class, priority = 900)" in fog_source,
            "Latitude fog mixin lacks its explicit priority 900")
    require(not (root / RETIRED_DISTANCE_SOURCE).exists(),
            "the retired AtmosphericFogEnvironment distance hook is back; it applies before "
            "vanilla overwrites renderDistanceStart/End, silently removing the far fog wall")
    require('@Inject(method = "setupColor"' in fog_source,
            "Latitude fog colour must be blended in FogRenderer.setupColor; 1.21.1 publishes no "
            "computeFogColor and setupColor is what uploads the colour")
    require('@Inject(method = "setupFog"' in fog_source,
            "Latitude fog distances must be applied in FogRenderer.setupFog")
    # Both passes must sit after vanilla's own RenderSystem writes -- the last thing each method
    # does. Anything earlier is overwritten by vanilla a few instructions later and never reaches
    # either the shaders or Sodium's later read.
    require(fog_source.count('at = @At("TAIL")') == 2,
            "both Latitude fog hooks must fire at TAIL, after vanilla's own RenderSystem writes")
    require("RenderSystem.setShaderFogEnd(" in read_source_or_empty(root, FOG_PRESENTATION)
            and "RenderSystem.getShaderFogEnd()" in read_source_or_empty(root, FOG_PRESENTATION),
            "Latitude must read and write the same RenderSystem fog end Sodium later reads")

    print(f"SODIUM_FOG_REACHABILITY_PASS jar={jar.name} sha256={EXPECTED_SODIUM_SHA256}")
    print(" chain=RenderSystem.getShaderFogEnd->getEffectiveRenderDistance->getSearchDistance"
          "->RemovableMultiForest.traverse")
    print(" snapshot=none(live-read) latitudeWritesAt=setupFog/setupColor TAIL")
    print(" userSetting=useFogOcclusion-preserved negativeControl=drift-detected")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except VerificationError as error:
        print(f"SODIUM_FOG_REACHABILITY_FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
