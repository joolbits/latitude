# Architectural Fix Ledger

## Entries

### EW Sandstorm / Border Haze Fix
- Problem: East/West border storms missing haze overlay when near world edge.
- Root cause: Fog end distance not clamped to border proximity; overlay not invoked in HUD tail.
- Structural fix: Add FogRenderer mixin to clamp fog end by border intensity and route HUD tail overlay render for EW haze.
- Files (authorities):
  - `src/main/java/com/example/globe/mixin/client/FogRendererMixin.java`
  - `src/main/java/com/example/globe/mixin/client/InGameHudMixin.java`
  - `src/main/java/com/example/globe/client/EwHazeOverlay.java`
- Guardrails: Camera-relative distance; no global fog mutation; depth mask off; HUD tail-only overlay; Sodium/Iris safe targets.
- Validated tag: v1.2.5+1.21.11
