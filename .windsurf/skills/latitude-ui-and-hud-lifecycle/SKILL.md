---
name: latitude-ui-and-hud-lifecycle
description: Rules for when Latitude UI screens and HUD overlays may render or open. Prevents screens auto-opening in-world, enforces debug gating, and codifies “world creation only” flows.
---

# Latitude — UI & HUD Lifecycle (Authoritative)

This skill prevents UI regressions such as:
- spawn/band picker screens opening automatically after joining a world
- debug screens appearing without explicit user action
- HUD overlays rendering over menus/inventories unintentionally

---

## Screen-opening rules (absolute)
1) **No Latitude config screen may auto-open in-world** after joining/spawning.
2) Screens that configure worldgen (spawn band picker, presets, etc.) are **World Creation only**.
3) The only allowed in-world screen opens are:
   - explicit keybind action by the player, or
   - explicit debug flag enabled (dev-only), or
   - explicit command-driven open (if the mod provides commands).

Default must be **OFF** for any auto-open behavior.

---

## Approved gates for dev-only auto-open
If a developer convenience auto-open exists, it must be gated by a JVM property:
- `-Dlatitude.debugOpenSpawnPicker=true` (default false)

Rules:
- Must never be enabled by default.
- Must be clearly labeled “debug” in code comments.

---

## HUD overlay rendering rules
1) HUD overlays must not render when a GUI screen is open unless explicitly intended.
2) Default: if `MinecraftClient.currentScreen != null`, skip overlay rendering.
3) Exceptions must be explicit (e.g., a dedicated “HUD Studio” preview screen).

---

## Event lifecycle rules (client)
Do not open screens from:
- join world callbacks
- first tick hooks
- network handler init
- world load events

If a screen must be shown on world creation, it must be launched from:
- world creation UI integration points only

---

## Required assistant behavior
When asked to add a screen or overlay, the assistant must:
1) state the allowed opening triggers
2) specify the exact gate (keybind / debug property / world creation)
3) confirm “default OFF” for any auto-open
4) include a rollback plan

---

## Forbidden behaviors
- “Open this screen on join for convenience” unless gated behind a debug flag
- Rendering overlays over inventory/menus by default
- Shipping releases with debug auto-open enabled
