---
name: latitude-rendering-compat-rules
description: Rendering rules for Sodium/Iris compatibility in Latitude (E/W storm walls, fog, overlays). Forbids raw GL state, mandates entry-based vertex emission, approved RenderLayers, and shader-safe debugging patterns.
---

# Latitude — Rendering Compatibility Rules (Authoritative)

This skill applies to:
- E/W storm wall rendering
- custom fog overlays
- any world-space debug geometry
- any rendering that must work with Sodium + Iris

Goal: avoid “works vanilla, breaks with shaders” regressions.

---

## Non-negotiable rules
1) No direct GL state calls in mod render code:
- No `RenderSystem.*` state toggles
- No `GL11.*` 
- No manual blend/depth toggles

2) Always use modern vertex emission:
- `VertexConsumer` with `MatrixStack.Entry` 
- prefer built-in `RenderLayer`s / `RenderLayers` helpers
- never rely on “implicit” GL state being correct

3) Rendering must be safe under:
- Fabric renderer
- Sodium renderer
- Iris shader pipeline

---

## Approved rendering approaches
### A) World-space geometry (storm wall)
- Subscribe via the proper world render callback (not HUD)
- Use `WorldRenderContext` matrices/consumers
- Emit vertices using `vertex(entry, x,y,z).color(...).texture(...).light(...).normal(...);` 
- Keep geometry stable and avoid Z-fighting (slight inset if needed)

### B) Debug lines/quads
- Provide a compile-time or JVM-toggle to swap geometry:
  - line-only layer for shader visibility testing
  - quad layer for final

All debug visuals must be behind a toggle and off by default.

---

## Debug toggles (recommended)
- `-Dlatitude.debugRenderEwWall=true` 
- `-Dlatitude.debugEwWallLines=true` (lines vs quads)
- `-Dlatitude.debugFog=true` 

Default OFF.

---

## Performance / safety constraints
- Avoid per-frame allocations in hot render paths.
- Avoid extremely dense geometry; scale step size with distance if needed.
- Never spam actionbar logs; use one-shot logs or counters.

---

## Required assistant procedure for render bugs
When a render bug is reported (“not visible with shaders”, “flicker”, etc.), the assistant must:
1) Identify the current RenderLayer and emission style
2) Switch to a minimal debug layer (lines) behind a toggle
3) Confirm callback is firing (single-shot ping)
4) Reduce z-fighting (slight inset, depth tweaks via layer choice only)
5) Only then adjust final geometry/layer

---

## Forbidden behaviors
- “Just disable shaders” as a solution
- Adding render fixes by toggling GL state directly
- Shipping with debug render toggles enabled by default
