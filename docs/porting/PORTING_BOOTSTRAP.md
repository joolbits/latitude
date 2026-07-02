# Latitude Porting Bootstrap Kit

> **2026-07-02 update:** Use `docs/porting/PORTING.md` as the current porting front door. For the Latitude
> 2.0 overhaul, read `docs/LATITUDE_2_0_OVERHAUL.md` first; the planned canonical target is Minecraft `26.2`
> after an isolated pivot/proof phase.

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

## Stop Conditions

- non-canonical root
- dirty/staged tree ambiguity
- missing live biome hook proof
- jar provenance mismatch
- UI restored but runtime world entry stalls
- Atlas/direct preview disagrees with fresh runtime
- public jar contains dev/headless/tooling/process-launch classes

## Rules Of Use

- Build success is not release proof.
- Atlas is evidence, not absolute truth; fresh runtime proof wins.
- Stop at the first clear blocker instead of widening the slice.
- Keep the bootstrap kit read-only until the port slice is intentionally chosen.
