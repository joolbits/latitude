---
name: latitude-debug-instrumentation-rules
description: Rules for adding debug logs, counters, overlays, and sanity pings in Latitude. Enforces gated, minimal, removable instrumentation and forbids permanent spam in hot paths.
---

# Latitude — Debug Instrumentation Rules (Authoritative)

This skill is used whenever the assistant proposes:
- adding logs
- adding HUD/actionbar debug overlays
- adding counters
- adding “sanity ping” mixins
- adding temporary assertions/stack traces
- adding profiling toggles

Goal: make debugging **fast**, **low-noise**, and **safe to ship**.

---

## Non-negotiable rules
1) **All instrumentation is gated** behind a toggle (system property or in-mod debug flag).
2) Default for any debug toggle is **OFF**.
3) Never spam in hot paths (per-block/per-vertex/per-tick) without throttling.
4) Any debug change must be removable in one commit.
5) Every debug print must answer exactly **one question**.

## Debug flag naming standard (locked)

All debug instrumentation must follow this scheme:

- **Prefix:** `-Dlatitude.` 
- **Format:** `debug<FeatureName>` (camelCase)
- **Type:** boolean flags only (`true` enables)
- **Default:** OFF when not provided

Examples (canonical):
- `-Dlatitude.debugSnowGuard` 
- `-Dlatitude.debugWarmSnowTrap` 
- `-Dlatitude.debugOpenSpawnPicker` 
- `-Dlatitude.debugLatPick` 
- `-Dlatitude.debugRenderEwWall` 

Any new debug flag must conform to this naming scheme.

---

## Approved gating mechanisms (choose one)
Preferred: JVM system properties (dev-only)
- `-Dlatitude.debugSnowGuard=true` 
- `-Dlatitude.debugWarmSnowTrap=true` 
- `-Dlatitude.debugLatPick=true` 
- `-Dlatitude.debugRenderEwWall=true` 

Alternative: in-mod config keybind / config file (only if it already exists)

---

## Logging rules
### Throttle requirements
If a log is in a frequent path:
- print at most **once per N events** (e.g., once per 60 ticks or once per 256 calls)
- or “print only on first occurrence”
- or “print only when a match happens” (e.g., only when a snow block is seen)

### Content requirements
Logs must include:
- short tag in brackets: `[SNOWBLOCK_GUARD]`, `[FREEZE_GUARD]`, `[LAT_PICK]` 
- coords and/or chunk origin when relevant
- band/zone result when it is a latitude-related bug

Never log:
- full objects with huge toString output
- entire registry dumps
- per-biome spam across many chunks unless explicitly requested

---

## Counter/overlay rules (preferred over spam logs)
If the question is “is the hook firing?”:
- Use counters:
  - calls / matches / rewrites
- Show them via actionbar or HUD overlay
- Gate behind `-Dlatitude.debugWarmSnowTrap=true` 

Overlay update cadence:
- no more than once per 40 ticks (~2s) unless explicitly needed.

---

## Sanity ping rules
A sanity ping is allowed only when:
- a mixin is suspected not to apply
- a crash happens during early init
- environment mismatch is likely

Rules:
- print once, then stay silent
- must include the mixin name and target class
- must be removed or gated before release

---

## Stack trace / crash-once (nuclear) rules
Allowed only when:
- write path is unknown and must be identified
- symptom is rare and hard to reproduce

Rules:
- trigger only on the first match
- include an explicit comment: “REMOVE AFTER TRACE”
- must be behind a system property OR removed immediately after capture

---

## “Release safety” checklist (must pass before tagging)
Before creating a release tag:
- debug flags default OFF
- no ungated WARN spam in worldgen/render loops
- overlays disabled unless debug flag set
- `.gitignore` prevents debug-only artifacts from creeping into commits

---

## Expected assistant output
When proposing instrumentation, the assistant must output:
1) The exact question the instrumentation answers
2) The minimal hook point
3) The toggle name
4) The throttle strategy
5) The exact file(s) to edit
6) The removal plan (revert commit or flip flag)
