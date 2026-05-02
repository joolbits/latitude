# Latitude Porting Bootstrap Kit

Purpose: make future Minecraft version ports faster by front-loading the known drift checks before any source edits.

## Port Phases

1. dependency/mapping scout
2. compile API drift
3. mixin descriptor audit
4. client UI hook audit
5. live biome hook proof
6. first-world-entry timing proof
7. `/latdev` smoke proof
8. artifact purity scan
9. release hard gate

## Client UI Parity Gates

- F9 must open the intended settings/front-door screen.
- Old capture, CSV, and dev controls must not appear in public F9.
- HUD Studio and settings screens must suppress vanilla blur or background leaks.
- Screen widgets, scrollbars, and previews must render crisp.
- Existing-world loading coverage is separate from new-world loading proof.
- Existing-save loading must show the bespoke Latitude overlay too.
- The overlay must clear when `LevelLoadingScreen` closes.
- EW haze HUD readability must be tested near the active world-size border, not only at regular-world coordinates.
- Use the active world radius for EW edge tests; the border check must be size-safe.
- Build success alone does not prove any of these gates.

## Hotfix Lesson: Separate Subsystems

- If a UI fix exposes a second subsystem, stop and split the work.
- Do not fold EW haze into F9 or loading hotfixes.
- Keep runtime proof, artifact purity, and UI parity separate when one gate can fail independently.
- If two attempts fail, bisect or runtime-probe instead of patching blindly.

## Stop Conditions

- non-canonical root
- dirty/staged tree ambiguity
- missing live biome hook proof
- jar provenance mismatch
- UI restored but runtime world entry stalls
- Atlas/direct preview disagrees with fresh runtime
- public jar contains dev/headless/tooling/process-launch classes
- existing-world loading shows vanilla-only behavior
- EW haze buries hotbar or warning text near the border
- F9 exposes capture/CSV/dev controls in the public front door
- HUD Studio or settings screens leak vanilla blur/background
- `build` passes but the session never reaches joined-game proof

## Rules Of Use

- Build success is not release proof.
- Atlas is evidence, not absolute truth; fresh runtime proof wins.
- Stop at the first clear blocker instead of widening the slice.
- Keep the bootstrap kit read-only until the port slice is intentionally chosen.
