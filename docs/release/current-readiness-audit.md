# Latitude 1.4 Current Readiness Audit

> Historical 1.4 audit retained for its evidence record. It is not current for 1.5.1 Beta 1. See
> [`beta1-checklist.md`](./beta1-checklist.md).

`status: live-proof green, release not authorized` · `updated: 2026-06-21` · `candidate: 1.4.1-beta.2+26.1.2` · `sha256: 1f50c5954cef3c91de1b071e78172ad6940a41abe5bace3e6febde5ac449a477`

This audit maps the release-readiness objective to current evidence. It is not a release authorization. The active Modrinth profile/runtime jar has now been rebuilt from savepoint commit `c9da0f93029f7f16c50a7bc89eb766c576a85b48` and staged into `Lat 1.4+26.1.2` as SHA `1f50c5954cef3c91de1b071e78172ad6940a41abe5bace3e6febde5ac449a477`. The prior biome-tuning runtime jar SHA `af1579b2...` is preserved as a timestamped backup. A final live attempt on 2026-06-21 proves the c9 candidate launches, loads a Globe world, accepts commands, locates and renders a real desert, survives a short non-teleport movement soak without new warnings, and save/quits cleanly. A narrow stony follow-up then resolved the only remaining live caveat: fresh `/locate biome minecraft:stony_peaks` returned `[1452,170,4201]`, the settled HUD at that coordinate read `minecraft:stony_peaks`, and the server-side `/execute if biome` predicate emitted `stony_peaks_here`.

## Source Of Truth

| Field | Current value |
| --- | --- |
| Canonical source root | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2` |
| Canonical branch / candidate savepoint | `feat/custom-biome-expansion-26.1.2` / `c9da0f93029f7f16c50a7bc89eb766c576a85b48` |
| Candidate tag | `save/biome-tuning-followup-26.1.2` points at `c9da0f93029f7f16c50a7bc89eb766c576a85b48` |
| Latest local build jar | `build/libs/latitude-1.4.1-beta.2+26.1.2.jar` |
| Latest local build SHA-256 | `1f50c5954cef3c91de1b071e78172ad6940a41abe5bace3e6febde5ac449a477` (staged) |
| Latest local build manifest provenance | `Git-Commit=c9da0f93029f7f16c50a7bc89eb766c576a85b48`, `Git-Branch=feat/custom-biome-expansion-26.1.2`, `Build-Dirty=true`, `Build-Time=2026-06-21T21:53:47Z` |
| Profile jar | `<home>/Library/Application Support/ModrinthApp/profiles/Lat 1.4+26.1.2/mods/latitude-1.4.1-beta.2+26.1.2.jar` |
| Profile jar SHA-256 | `1f50c5954cef3c91de1b071e78172ad6940a41abe5bace3e6febde5ac449a477` |
| Profile settings for current live retest | `renderDistance=16`, `simulationDistance=8`; previous `32/12` preserved in a timestamped options backup |
| Public version name | Undecided; likely `1.4.1+26.1.2` unless Maintainer chooses beta wording |
| Machine-readable gate manifest | `docs/release/current-gates.json` |
| Live proof runbook | `docs/release/live-proof-runbook.md` |
| Current closeout evidence folders | `tmp/closeout-1.4-20260621/rebuild-c9da0f93-175345`; `tmp/closeout-1.4-20260621/profile-stage-c9da0f93-175416`; `tmp/closeout-1.4-20260621/final-live-launch-blocker-181416`; `tmp/closeout-1.4-20260621/final-live-c9-192419`; `tmp/closeout-1.4-20260621/stony-followup-c9-195009` |
| Current live blocker | None known after the stony follow-up. Release/publication remains blocked on Maintainer-owned public version identity, branch/tag push, upload, and publication authorization. |

## Requirement Matrix

| Objective item | Status | Evidence | Remaining work |
| --- | --- | --- | --- |
| P0: Reconcile release source of truth | GREEN | `docs/release/checklist.md`; `docs/release/current-gates.json`; profile-stage evidence for SHA `1f50c595...` | None for local source-of-truth routing. |
| P0: Savepoint commit and tag exist locally | GREEN_LOCAL | Commit `c9da0f93`; tag `save/biome-tuning-followup-26.1.2` | Push branch and tag only after Maintainer authorizes the release closeout push. |
| P1: Build final candidate from savepoint source and prove it | GREEN | `tmp/closeout-1.4-20260621/rebuild-c9da0f93-175345/clean-build.log`; `build-jar.sha256`; `build-manifest.txt` | None for build provenance. |
| P1: Stage final candidate into Modrinth profile | GREEN | `tmp/closeout-1.4-20260621/profile-stage-c9da0f93-175416/profile-stage.log`; profile jar SHA `1f50c595...`; backup `latitude-1.4.1-beta.2+26.1.2.jar.pre-c9da0f93-20260621-175417` | Live proof target is now profile SHA `1f50c595...`. |
| P1/P2: Desert locate mismatch | GREEN | `tmp/biome-tuning-20260621/desert-locate-proof-live-start-after-registry-locate-20260621-1658`; `tmp/biome-tuning-20260621/live-registry-locate-after-stage-20260621-1703/desert-live-proof-summary.md`; savepoint commit includes the registry-backed locate wrapper | Reconfirm only as part of final cruise if a route uses `/locate`; no representation edit indicated. |
| P1/P2: Stony peaks and treeline | GREEN | `tmp/biome-tuning-20260621/live-registry-locate-after-stage-20260621-1703/stony-treeline-proof-summary.md`; Maintainer accepted the corrected stony angle; treeline constants `TREE_LINE_Y=168`, `TREE_LINE_FADE_BAND=28` | No further stony/treeline tuning planned for this release. |
| P1: Fresh/existing load sanity | GREEN_EXISTING_LOAD | `tmp/closeout-1.4-20260621/final-live-c9-192419/summary.md`; existing `New World` loaded with `isGlobe=true`, radius `10000`, border diameter `20000`, first safe playable tick, and clean overlay close | Fresh new-world creation was not rerun; existing load sanity is green. |
| P2: Scenic route plus palm/fronds/decoration visual check | GREEN | Desert locate/HUD proof is green; tropics/palm/decor screenshot is supportive; stony follow-up HUD and server predicate are green | None for the prior stony caveat. |
| P2: Clean non-teleport soak/perf pass | GREEN_WITH_PRIOR_WARNINGS | `logs/026-nonteleport-soak-summary.txt` shows 20s W-key movement produced zero new WARN/ERROR/crash lines | Desert locate and teleport chunk loads produced `Can't keep up` warnings before the soak; record as performance caveat, not soak red. |
| P2: Save/quit shutdown | GREEN | `logs/029-savequit-new-log.txt`; title screenshot `screens/029-after-save-and-quit-click.png` | None for shutdown. |

## Current Verdict

Latitude 1.4 is **live-proof green but not release-authorized** as of 2026-06-21. The release candidate has been rebuilt and staged from savepoint `c9da0f93`, with profile/runtime SHA `1f50c5954cef3c91de1b071e78172ad6940a41abe5bace3e6febde5ac449a477`. The launcher blocker is cleared, the final live attempt produced green evidence for c9 jar launch, existing Globe load, command control, 20,000 border, desert locate/HUD, short clean non-teleport soak, and clean save/quit, and the stony follow-up resolved the last live caveat.

This is still **not a publication authorization**. Public release version/filename, branch/tag push, GitHub release, Modrinth upload, CurseForge/manual publication, cleanup, and port/backport work remain blocked until Maintainer explicitly chooses the public release identity and authorizes the closeout actions.

## Safe Next Slice

Proceed to the release-decision slice: choose the public version/filename, then authorize branch/tag push and publication steps if Maintainer wants to cut the release now.
