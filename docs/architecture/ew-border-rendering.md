# EW Border Rendering Authority Spec

## Render Order Contract
1. World (vanilla terrain + modded world draw)
2. EW haze (border-specific atmospheric wall)
3. HUD (including compass, overlays)

## Global Fog Safety
- **No global fog mutation**: do not patch or rebind global fog parameters used by unrelated renderers; operate on border-local uniforms only.

## Camera-Relative Math
- All distance and direction calculations must be camera-relative in view space; avoid world-origin drift.

## Depth Mask / Blend Policy
- Depth mask off for the haze quad/volume to avoid clipping into terrain.
- Use additive or alpha blend that preserves depth-tested world content; no depth writes.

## Shader-Agnostic Constraints (Iris-safe)
- Avoid shaderpack-specific hooks; use standard Fabric/Iris-safe mixin targets and uniforms.
- No reliance on core shader replacements; fall back to vanilla pipelines when shaderpacks are present.

## Required Mixins (names only)
- client.FogRendererMixin
- client.InGameHudMixin

## Validation Tags
- v1.2.5+1.21.11
