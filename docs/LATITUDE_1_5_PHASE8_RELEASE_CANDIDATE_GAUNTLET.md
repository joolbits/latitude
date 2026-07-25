# Latitude 1.5 Phase 8 — Release-Candidate Gauntlet

`status: PASSED LOCALLY` · `reasoning: HIGH` · `source line: 1.5 pre-2.0 polish` ·
`root: <home>/CascadeProjects/Latitude-1.5-26.2` ·
`branch: codex/1.5-mini-launch-26.2` ·
`starting HEAD: 7df70c596251c72d9b960de684ad4e965142a4f7` · `started: 2026-07-21`

## Working card

Objective: finish one bounded release-candidate gauntlet for Latitude 1.5 on Minecraft 26.2 and
produce an honest local acceptance packet.

Root/profile: implementation root above; the Modrinth profile
`<home>/Library/Application Support/ModrinthApp/profiles/Lat 1.5 - 26.2 - TEST`
may be used only for an explicitly identity-bound disposable live gate.

Obligations: reconcile the maintainer's TEST 8 acceptance; audit the post-Phase-5 production range; prove
build, public-jar purity, metadata, saves, dedicated server, supported providers, worldgen/climate,
structures, HUD/config, and bounded performance risk; admit only exact release-blocking reds; finish
with adversarial review, binder truth, hashes, limitations, and a proof-clean local savepoint.

Allowed work: read-only audit first; task-owned evidence under
`tmp/latitude-1.5-phase8-release-candidate-20260721/`; project-native offline/local proof; disposable
task-owned clients, servers, worlds, configs, and copied-save fixtures; exact-red source/test/tool
paths only after a path allowlist is frozen; implementation manifest/binder/release-packet truth;
focused proof-clean local commits.

Forbidden lanes: the dirty campaign checkout; Latitude 2.0 and every Pivot/donor worktree; real
Maintainer worlds or profiles other than the explicitly authorized TEST profile; unrelated cleanup;
accepted HOLD reopening without a new exact red; Phase 9; tag, push, merge, upload, publication, or
release.

Proof gate: every mandatory row below is PASS or an admissible, explicitly evidenced HOLD; every
accepted patch has symptom-specific RED/GREEN proof, impact/diff audit, and fresh adversarial
review; artifact/profile/runtime claims remain identity-separated.

Stop condition: a locally frozen Phase 8 decision packet and savepoint, before any public or remote
action. Stop earlier on identity drift, unknown dirt, protected-state change, unsafe external state,
unexplained worldgen regression, failed proof oracle, or a required product decision.

## Obligation ledger

| ID | Obligation | Exit status | Exit evidence |
| --- | --- | --- | --- |
| O1 | Record the maintainer's TEST 8 human verdict without overstating unrecorded pixels. | passed | TEST 8 remains identity-bound to its exact staged artifact; later source changes use separate proof classes and do not inherit unrecorded pixel acceptance. |
| O2 | Audit all production changes from Phase 5 artifact commit `96c43b45` through starting HEAD. | passed | Exact commit/path/stat audit plus independent correctness, runtime, performance, and presentation reviews; no surviving P0/P1 outside admitted repairs. |
| O3 | Prove Java 25 build, all applicable policy/invariant suites, and proof-oracle integrity. | passed | Clean Java 25 builds, focused policy suites, fail-closed runner repair, artifact-pair corruption controls, and V29 rotated-log hostile controls passed. |
| O4 | Prove public-jar metadata, purity, mixin closure, 2.0 denylist, and source provenance. | passed | Public-artifact inspectors, metadata/purity/denylist scans, corruption negatives, and post-prune jar inspection passed for the accepted source candidate. The jar records commit `3aa77dea` plus `Build-Dirty: true`; it proves candidate bytecode/purity, not a clean final artifact bound to `98b45b7b`. |
| O5 | Prove fresh and copied-save create/load/save/reload plus clean shutdown. | passed | Exact-candidate fresh lifecycle and copied 26.1 save/load/reload retained semantic identity and stopped cleanly. |
| O6 | Prove fresh dedicated server and supported provider/Sodium rows. | passed | Fresh/reload dedicated server, Sodium compatibility, absent/Terralith/Tectonic/BOP provider rows passed; Promenade remains named N/A/HOLD. |
| O7 | Re-run a bounded worldgen/climate/structure regression matrix. | passed | Nine vanilla rows, four provider rows, exact beach/inland, Meadow elevation, four 50-degree points, climate-compatible villages, and 12/12 strict-80 foliage cases passed. |
| O8 | Prove HUD/config/create-world/loading behavior at applicable widths and persistence boundaries. | passed | Structural/persistence checks, legacy HUD migration, TEST 8 human evidence, and the exact TEST9016 rendered loading screenshot passed on their distinct proof surfaces. |
| O9 | Reconcile the completed performance audit and current hot paths without unsupported FPS/MSPT claims. | passed | Hot-path tripwires passed; no exact 26.2 slowdown RED warranted a noisy timing run; no unsupported 26.2 magnitude claim is made. |
| O10 | Admit and repair only exact release-blocking reds. | passed | Every accepted change has RED/GREEN, impact/diff proof, and fresh adversarial acceptance; non-attributable findings remain HOLD. |
| O11 | Reconcile implementation manifest, binder/index/registry, limitations, hashes, and release packet. | passed | `docs/binder/latitude-1-5-phase8-closure-20260725.md`, the superseding evidence row, and the 2026-07-25 binder topic/freshness refresh contain final evidence classes, hashes, limitations, and reconstructible paths. |
| O12 | Create and verify one final proof-clean local Phase 8 savepoint. | post-commit gate | This document-bearing commit is the intended final Phase 8 documentation savepoint. O12 becomes passed only when immediate readback confirms parent `98b45b7b`, the exact six-file docs path list, unchanged branch/protected hashes, empty stage, and no tag or external action. A failed readback stops the task without a second commit. |
| C1 | Preserve campaign, Pivot, donor, real-world, and unrelated worktree state. | passed | Protected surfaces stayed outside the mutation lane; three pre-existing TEST-source paths retain their frozen hashes. |
| C2 | No tag, push, merge, upload, publication, release, or Phase 9 action. | passed | Final Git/action audit; Phase 8 stops locally. |
| A1 | the maintainer's statement that the build is now in good shape is the TEST 8 human verdict; exact artifact binding must still be reconstructed before recording it as identity-bound live proof. | passed | Exact TEST 8 artifact binding recorded; no later-candidate pixel equivalence inferred. |
| R1 | The canonical campaign roadmap is stale and is protected concurrent dirt. | intentionally not done | Campaign checkout was not edited; implementation-side closure is authoritative until separately reconciled. |
| R2 | Promenade/combined exact parity, Terralith performance attribution, accepted old-save `/locate` ghosts, and the unrecoverable exact 46-degree-south beach scene remain accepted HOLDs unless a new exact target red appears. | intentionally not done | Preserved as explicit HOLDs without speculative tuning or silent promotion. |

## Phase 8 proof matrix

| Slice | Required result |
| --- | --- |
| 8A. Identity and scope lock | Exact root/branch/HEAD/tag/stage/status, protected fingerprints, post-Phase-5 range, artifact/profile separation. |
| 8B. Adversarial static sweep | No surviving P0/P1 release blocker; every material plausible risk gets a narrow proof or HOLD. |
| 8C. Build and proof oracles | Clean Java 25 build; all applicable focused suites; negative controls for release-verdict tooling. |
| 8D. Artifact integrity | Public jar contains no dev toolkit/evidence/local paths; correct metadata, mixins, hashes, and 2.0 denylist. |
| 8E. Runtime lifecycle | Disposable fresh and copied save, reload, shutdown, and dedicated-server rows pass. |
| 8F. Compatibility | Frozen vanilla, Sodium, Terralith, Tectonic, and Biomes O' Plenty rows pass where officially applicable. |
| 8G. Worldgen/structures | Fixed matrix preserves exact-ID/climate laws, coastal beaches, upland Meadow, 50-degree boundary, climate-compatible villages, and strict-80 foliage origin rule with berries exempt. |
| 8H. UI/presentation | Create screen, HUD Studio/config migration, loading overlay/version, polar title/warnings/fog, and east/west advisory/particles/haze/shelter/retreat are accepted on their actual proof surfaces. |
| 8I. Performance | Current code is reconciled against the completed audit; any quantitative claim uses frozen baseline/candidate identity, warmup, raw runs, uncertainty, and attribution. |
| 8J. Release packet | Final PASS/HOLD/RED ledger, candidate hashes, known limitations, binder validation, protected-state proof, and local savepoint. |

## New-red admission rule

A finding enters source only when it reproduces against the exact Phase 8 target, is release-relevant,
stays inside an existing 1.5 subsystem, has a named narrow proof before editing, and survives an
independent contradiction pass. Two failed narrow fixes return the symptom to HOLD and require a
shared-cause decision. No finding is permission for cleanup or architecture work.

## Exit decision

Phase 8 passes locally on 2026-07-25 only when the document-bearing commit containing this statement
passes its immediate post-commit readback. The final evidence and limitation ledger is
`docs/binder/latitude-1-5-phase8-closure-20260725.md`. This closes the local gauntlet only. Tag,
push, upload, publication, profile staging, public-version naming, release, Phase 9, and Latitude
2.0 work remain unauthorized.
