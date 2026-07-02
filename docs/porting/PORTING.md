# Latitude Porting Front Door

`status: active front door`
`updated: 2026-07-02`
`current planning target: Minecraft 26.2`

Read this before any Minecraft-version pivot, backport, forward-port, or version-family carryover.

## Start Here

1. `docs/LATITUDE_2_0_OVERHAUL.md`
2. `docs/porting/VERSION_MATRIX.md`
3. `docs/porting/PORTABILITY_ARCHITECTURE.md`
4. `docs/porting/PORTING_BOOTSTRAP.md`
5. `docs/porting/PORTING_RISK_FILES.md`
6. Relevant dated handoff, especially `docs/porting/HANDOFF-1.4-beta-backport.md` for the older 1.4 backport lessons.

## Canonical Rule

Latitude 2.0 should pivot to Minecraft `26.2` before the earthlike-world overhaul begins. Do not build the large geography/climate rewrite on `26.1.2` and then port it later.

The current 26.1.2 line remains a reference/proven baseline until the 26.2 pivot compiles and passes deterministic proof.

## Porting Goal

Future ports should mostly touch:

- Build metadata.
- Platform adapters.
- Mixin targets.
- Version docs.

Future ports should not require hand-transplanting climate algorithms through `LatitudeBiomes.java`.

## Standard Port Start

1. Run repo preflight:

   ```bash
   git rev-parse --show-toplevel
   git status -sb
   git branch --show-current
   git rev-parse --short HEAD
   git tag --points-at HEAD
   ```

2. Confirm the target version in `VERSION_MATRIX.md`.
3. Create or switch to an isolated target branch/worktree.
4. Change build/version metadata only.
5. Compile.
6. Repair API/mixin drift with the smallest safe patches.
7. Run deterministic proof before profile staging:
   - compile/build proof
   - exact-ID Atlas proof
   - analyzer proof
   - flag-off regression proof when relevant
8. Stage a Modrinth profile jar only after deterministic proof is green.
9. Record binder evidence and update docs before savepoint/tag/push.

## 26.2 Pivot Scope

The 26.2 pivot is not the earthlike overhaul yet.

Allowed:

- Build metadata retarget.
- Loader/API/Loom/Gradle updates.
- Narrow mapping/API repairs.
- Mixin target repairs.
- Headless Atlas proof recovery.
- Docs and version matrix updates.

Forbidden until the pivot is green:

- New continent behavior.
- New climate behavior.
- Terrain-density rewiring.
- Boundary UX changes.
- Profile staging without deterministic proof.
- Release tags or public uploads.

## Stop Conditions

Stop and write a blocker note if:

- Upstream coordinates are unavailable or contradictory.
- The root/branch/HEAD is not the intended target.
- Dirty files overlap the port slice and are unexplained.
- Compile drift is broad after two repair attempts.
- A mixin target cannot prove it applies.
- Flag-off output changes.
- The exact profile jar cannot be proven after staging.
- A future worker starts copying algorithms by hand instead of using/adapting the portability spine.

## Required Documentation

For every port or pivot:

- Update `VERSION_MATRIX.md`.
- Add/update a binder evidence row.
- Link proof artifacts from the evidence row.
- Update the active handoff/resume pointer if the current truth changed.
- Update `PORTABILITY_ARCHITECTURE.md` when a new adapter/mixin boundary is created.

## Suggested Commit Shape

For the 26.2 pivot:

```text
build: retarget Latitude to Minecraft 26.2
port: repair 26.2 API drift
test: restore 26.2 atlas proof
docs: record canonical 26.2 pivot
```

For portability foundation:

```text
arch: add Latitude core summary contracts
port: add Minecraft adapter boundary
test: prove flag-off geography summaries
docs: record portability foundation
```
