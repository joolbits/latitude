#!/usr/bin/env python3
"""Verify Latitude's supported Sodium 0.8.13 fog-culling integration from exact class bytes.

Target-specific. The 26.2 edition of this verifier asserted a different chain, because both
Minecraft and Sodium changed shape between the two lines:

* 26.2 ships Minecraft deobfuscated, so Sodium's jar referenced real names
  (``net/minecraft/client/renderer/fog/FogData.environmentalStart``). 1.21.11 is obfuscated, so
  Sodium ships remapped to **intermediary** and the same references read ``class_7285.field_60582``.
* Sodium 0.9.1 hooked ``setupFog`` at ``RETURN``. Sodium 0.8.13 hooks ``setupFog`` at the
  ``INVOKE`` of ``updateBuffer`` and captures the local ``FogData`` and ``Vector4f`` there.

That second difference is the load-bearing one for Latitude. Sodium snapshots the fog state at the
buffer write, so everything Latitude does to the fog must happen *before* ``updateBuffer`` is
invoked. It is why Latitude's colour pass hooks ``computeFogColor`` rather than the return of
``setupFog``: blending into ``setupFog``'s return value would land after both the UBO upload and
Sodium's snapshot, compiling cleanly and changing nothing on screen.

Intermediary names asserted below, resolved against 1.21.11 intermediary-v2:
    class_758                    net.minecraft.client.renderer.fog.FogRenderer
    class_7285                   net.minecraft.client.renderer.fog.FogData
    method_3211  (Camera,int,DeltaTracker,float,ClientLevel)Vector4f      FogRenderer.setupFog
    method_71110 (ByteBuffer,int,Vector4f,float x6)V                      FogRenderer.updateBuffer
    field_60582  F   FogData.environmentalStart
    field_60583  F   FogData.renderDistanceStart
    field_60584  F   FogData.environmentalEnd
    field_60585  F   FogData.renderDistanceEnd
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


EXPECTED_SODIUM_SHA256 = "78d7b657406c2961e0a10d1d5c2703c519deb2030ec670163166f8a0a1152176"
EXPECTED_SODIUM_VERSION = "0.8.13+mc1.21.11"
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

# Sodium's own snapshot point. Latitude must have finished with the fog before this runs.
SODIUM_SNAPSHOT_TARGET = (
    'target="Lnet/minecraft/class_758;'
    'method_71110(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"'
)
# 0.8.13's traversal entry point; 0.9.1 called this SectionTree.traverse.
SODIUM_TRAVERSE = "RemovableMultiForest.traverse:"
SODIUM_SEARCH = "getSearchDistance:(Lnet/caffeinemc/mods/sodium/client/util/FogParameters;)F"


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
    # Sodium still snapshots FogData, and still does it inside setupFog at the buffer write.
    for token in (
        "sodium$storeFogParameters",
        "value=[class Lnet/minecraft/class_758;]",   # @Mixin(FogRenderer.class)
        'method=["method_3211"]',                    # setupFog
        'value="INVOKE"',
        SODIUM_SNAPSHOT_TARGET,                      # ... at the updateBuffer call
        "net/minecraft/class_7285",                  # FogData
        "field_60582",                               # environmentalStart
        "field_60583",                               # renderDistanceStart
        "field_60584",                               # environmentalEnd
        "field_60585",                               # renderDistanceEnd
    ):
        require(token in fog_mixin, f"Sodium FogData snapshot drifted or is missing: {token}")

    search_method = method_section(renderer, "private float getSearchDistance(")
    require("useFogOcclusion:Z" in search_method,
            "Sodium fog-occlusion user setting is no longer consulted")
    require("getEffectiveRenderDistance:(Lnet/caffeinemc/mods/sodium/client/util/FogParameters;)F"
            in search_method,
            "Sodium no longer derives effective distance from FogParameters")
    require("getRenderDistance:()F" in search_method,
            "Sodium no longer preserves uncapped distance when fog occlusion is disabled")

    traversal_method = method_section(renderer, "private boolean createTerrainRenderList(")
    require(SODIUM_SEARCH in traversal_method,
            "FogParameters no longer reach RenderSectionManager.getSearchDistance")
    require(SODIUM_TRAVERSE in traversal_method,
            "RenderSectionManager no longer reaches the forest traversal")
    require(traversal_method.index(SODIUM_SEARCH) < traversal_method.index(SODIUM_TRAVERSE),
            "FogParameters search distance does not precede the forest traversal")


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
        verify_sodium_chain(fog_mixin, renderer.replace(SODIUM_TRAVERSE, "RemovableMultiForest.driftedTraverse:"))
    except VerificationError:
        pass
    else:
        raise VerificationError("negative control failed to detect a drifted traverse target")

    mixins = json.loads((root / MIXIN_CONFIG).read_text())
    client_mixins = mixins.get("client", [])
    # Unlike 26.2, RenderSectionManager.isSectionVisible(int,int,int) still exists on this Sodium
    # line. Latitude deliberately does not hook it: fog occlusion via FogParameters is the
    # supported mechanism, and the placement-time visibility mixin stays retired.
    require(DEAD_MIXIN not in client_mixins, "dead Sodium isSectionVisible mixin remains registered")
    require(not (root / DEAD_SOURCE).exists(), "dead Sodium isSectionVisible mixin source remains present")

    # Both fog passes live in the FogRenderer mixin and must outrank Sodium's default-priority
    # mixin, which snapshots at setupFog's updateBuffer call.
    fog_source = (root / COLOR_SOURCE).read_text()
    require("@Mixin(value = FogRenderer.class, priority = 900)" in fog_source,
            "Latitude fog mixin lacks explicit priority 900 before Sodium's snapshot")
    require(not (root / RETIRED_DISTANCE_SOURCE).exists(),
            "the retired AtmosphericFogEnvironment distance hook is back; it applies before "
            "vanilla overwrites renderDistanceStart/End, silently removing the far fog wall")
    require('@Inject(method = "computeFogColor"' in fog_source,
            "Latitude fog colour must be blended in computeFogColor, before Sodium's snapshot at "
            "the updateBuffer call; blending setupFog's return value never reaches the screen")
    # The distance pass must sit after vanilla's last renderDistanceEnd write (bci 146) and
    # before the six fields are read into updateBuffer (bci 186+) -- which is also strictly
    # before Sodium's snapshot at that same call.
    require('Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F' in fog_source
            and "Opcodes.PUTFIELD" in fog_source
            and "At.Shift.AFTER" in fog_source,
            "Latitude fog distances must be applied after vanilla's last renderDistanceEnd write; "
            "any earlier and vanilla clobbers them")

    print(f"SODIUM_FOG_REACHABILITY_PASS jar={jar.name} sha256={EXPECTED_SODIUM_SHA256}")
    print(" chain=FogData->FogParameters->getSearchDistance->RemovableMultiForest.traverse")
    print(" snapshot=setupFog@INVOKE(updateBuffer) latitudeWritesBefore=colour+distances")
    print(" userSetting=useFogOcclusion-preserved negativeControl=drift-detected")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except VerificationError as error:
        print(f"SODIUM_FOG_REACHABILITY_FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
