# Latitude 1.5 Feature Manifest

`status: Phase 8 PASSED LOCALLY; TEST 14 compact location-text range is rendered green; release actions remain unauthorized` · `branch: codex/1.5-mini-launch-26.2` · `implementation savepoint: TEST 14 source commit plus acceptance record` · `recorded: 2026-07-26`

This is the implementation-side allowlist for the Latitude 1.5 pre-2.0 polish campaign. The campaign
roadmap lives in the docs root at:

`<home>/CascadeProjects/Latitude (Globe)/docs/LATITUDE_1_5_PRE_2_0_POLISH_ROADMAP.md`

## Post-TEST 10 HUD canvas and loading-label feedback

HUD Studio tabs now organize controls without restricting the preview canvas.
The title, unattached compass, and detached biome/zone detail remain draggable
from Compass, Title, and Settings. Existing attachment, follow, snap/free,
clamping, and persistence rules remain authoritative.

The small loading version label keeps its accepted scale, muted presentation,
right alignment, and compact-screen clamp. Its gap below the bespoke pane is
increased from 2 px to 4 px.

Both strengthened structural verifiers reproduced the old behavior as RED and
then passed. The loading check includes 26 hostile negative controls and an
immutable source hash independently reviewed before rebinding. The Java 25
22-task clean build/check/invariant gate also passed.

This is source/model proof only. TEST 10 remains the currently staged artifact
and predates these two changes. A separately authorized next numbered TEST is
required for rendered acceptance.

Canonical evidence:

`docs/binder/latitude-1-5-test10-ui-feedback-20260726.md`

## Post-TEST 11 title snapping and location text scale

HUD Studio title dragging now uses the selected placement mode while the
pointer is moving. SNAP visibly quantizes to the existing eight-pixel grid;
FREE remains pixel-by-pixel. Release, screen re-initialization, reopening, and
configuration reload preserve the same stored coordinates without a final
jump.

`Location Text Size` is a separate persisted 50%–125% control, defaulting to
100%. It scales latitude plus biome/zone detail in analog and digital HUDs,
including detached detail, without resizing the analog disc and north marker
or the digital direction. One shared production layout policy owns scale
sanitization, measured geometry, bounds, hit-testing, clamping, preview, and
title snap/free coordinates.

Normal gameplay bounds use the current rendered direction, latitude, and
provider-aware biome/zone text. HUD Studio alone retains deliberate sample
content. This keeps default detached-detail overlap avoidance exact even for a
long custom-provider label at 125%.

Focused numeric policy proof, the strengthened HUD Studio verifier, the Java
25 22-task clean build/check/invariant gate, exact diff review, UIX review, and
a fresh adversarial review pass. The first adversarial pass rejected
direction-only layout movement and test-only formula duplication; both were
corrected before this savepoint.

This manifest-bearing commit is the designated source for TEST 12. Rendered
SNAP/FREE drag feel and the 50%/100%/125% location-text matrix remain the
player-visible acceptance gate; no Minecraft launch, push, or release is
implied.

Canonical evidence:

`docs/binder/latitude-1-5-test11-title-snap-location-text-20260726.md`

## Post-TEST 12 biome-source preview example

Maintainer accepted TEST 12's title SNAP and independent location-text behavior.
Its remaining HUD Studio issue was demonstrability: turning `Show Biome Source`
ON did not change the deliberate vanilla `Plains` preview, even though the
option worked for custom runtime biomes.

The Studio-only preview now reads `Plains · VANILLA` while the toggle is ON and
returns to `Plains` while it is OFF. Normal runtime semantics remain unchanged:
vanilla biomes stay compact and unlabelled, while custom biomes show their real
provider when the option is enabled.

The focused policy test reproduced the missing preview owner as RED and now
passes. The structural HUD Studio verifier and Java compilation also pass.
This manifest-bearing commit is the designated source for TEST 13; only the
rendered ON/OFF preview transition remains for human confirmation.

Canonical evidence:

`docs/binder/latitude-1-5-test12-biome-source-preview-20260726.md`

## Post-TEST 13 compact location-text range

Maintainer confirmed the TEST 13 biome-source preview, then found the independent
location-text slider too lopsided: its 75% floor was not compact enough while
its 200% ceiling was impractically large. The default remains 100%, but the
persisted slider and its shared geometry policy now use 50%–125% in five-percent
steps. Existing saved values are safely clamped into that compact range.

Focused policy proof checks the new 50% and 125% boundaries, retains the
independent compass-size invariant, and verifies long provider labels against
the same current-content bounds used by runtime rendering. The structural HUD
Studio verifier and Java 25 build pass. TEST 14 was staged from source commit
`2502b54c60c50a5464c7395dedf096d9fa24b967`; Maintainer then confirmed the rendered
50%/100%/125% range is green. The player-visible gate for this compact-range
follow-up is therefore closed.

Canonical evidence:

`docs/binder/latitude-1-5-test14-location-text-range-acceptance-20260726.md`

## Post-TEST 9 annotated polish

the maintainer's annotated TEST 9 review admitted four bounded refinements after Phase 8:

- an opt-in inline custom-biome provider suffix in biome-bearing HUD modes, defaulting OFF;
- snowy Subpolar/Polar firefly-bush suppression from the canonical 50-degree boundary while
  preserving warm fireflies and the sweet-berry exemption;
- a restrained balance pass for the existing compact planisphere; and
- a HUD Studio SNAP/FREE control over the pre-existing eight-pixel placement grid.

The focused policy suites, structural HUD/create-screen verifiers, Java 25 22-task clean
build/check/invariant gate, and diff audit pass. The planisphere retains the 1.5 band/input model
and does not import Latitude 2.0 world-shape or create-screen behavior.

This is a local source/model candidate. Custom-source legibility, planisphere appearance,
SNAP/FREE drag feel and persistence, and a fresh snowy-provider firefly scene still require a
separately authorized rendered TEST. TEST 9 remains untouched, and no tag, push, profile stage,
upload, publication, or release is implied.

Canonical evidence:

`docs/binder/latitude-1-5-test9-annotated-polish-20260726.md`

## Post-TEST 2 findings acceptance

the maintainer's TEST 2 screenshots and `/latdev explain` evidence admitted five bounded
source corrections. Commits `3e195d7f`, `58bd9519`, and `78a09e2d` now:

- default fresh/reset HUD state to Analog at 32 px and expose a 16–72 px
  whole-pixel Studio range without rewriting explicit older saved sizes;
- render the loading-pane version label at 90% of its prior size while
  preserving the bottom-right inset;
- prevent the Subpolar pool, including BOP tundra, from owning canonical
  Temperate land below 50°;
- require established near-ocean authority before the base-biome beach
  shortcut can preserve a beach inland;
- cancel newly placed climate-named village variants in incompatible Latitude
  bands while failing open for neutral, non-village, non-Latitude, or
  unavailable-authority paths.

Focused RED/GREEN policy proofs, Java 25 compilation and invariants, existing
HUD/village regressions, impact scans, exact 26.2 descriptor inspection, and a
fresh adversarial review passed. The adversarial passes caught and repaired
three defects before savepoint: dishonest legacy slider display/geometry, an
early beach-path boundary bypass plus unsupported coast threshold, and a
small-border non-Latitude false positive.

This is a local source/model pass. Already-generated village buildings are not
rewritten, accepted old-save `/locate` ghosts remain accepted, the exact beach
screenshot coordinate is unavailable, and no new rendered-pixel claim is
made. The next numbered TEST staging and fresh disposable replay require
separate authorization.

Canonical evidence:

`docs/binder/latitude-1-5-test2-findings-20260720.md`

## Post-TEST 2 HUD Studio consolidation

The F9 settings flow is now one HUD Studio rather than a settings screen that
opens a second editor. The consolidated screen uses real focusable/narratable
top buttons for `Compass`, `Title`, and `Settings`; the selected tab keeps a
restrained gold underline. It preserves the existing analog fresh/reset
baseline of 32 px and the 16–72 px editing range without rewriting explicit
older saved sizes.

`Face Opacity` is shown as a percentage. The checkerboard preview is drawn
behind the analog face only while the slider is hovered or actively dragged;
it is not tied to sticky focus. Done and Esc share the same save-and-return
path, repeated F9 cannot nest another Studio, and clipped or semantically
hidden controls are inactive and release keyboard focus.

The retired standalone `LatitudeSettingsScreen` and its parallel reset path
are removed. `Reset All HUD` is deliberately limited to visible HUD, title,
location-detail, and warning settings; it does not change hidden capture,
debug, blending, or world-generation configuration.

The focused verifier recorded two explicit RED rounds before the final GREEN.
A fresh adversarial sweep found and closed scroll reactivation, stale focus,
and repeated-F9 nesting. Both focused verifiers and the Java 25 clean build
with all 17 build/check tasks passed. The exact TEST 3 artifact was certified
and staged as the only Latitude jar in the authorized 26.2 TEST profile. This
is not yet rendered-pixel, narration, or mouse-interaction acceptance; the
guarded live replay and the maintainer's visual review remain the next gate.

Canonical evidence:

`docs/binder/latitude-1-5-hud-studio-consolidation-20260720.md`

## TEST 7 zone-title and sand-haze presentation correction

the maintainer's TEST 7 replay accepted the improved storm-particle onset but exposed two
presentation failures: `POLAR` could fade in again without a true zone exit,
and east/west depth fog retained the ordinary atmospheric color instead of
reading as a sandstorm.

The repeated title shared a lifecycle cause. An ordinary backwards correction
of the same client level's raw game clock was treated as a new world entry,
clearing the already-shown zone key. The corrected path now shifts the active
zone-title and warning timelines across same-level clock resyncs without
forgetting the zone. A genuine level change or disconnect still clears the
zone, both warning episodes, and any title inherited from the prior level.

East/west depth fog now blends toward a restrained brown sand color through the
Tropical, Subtropical, and Temperate bands (`absolute latitude < 50°`). At the
canonical 50° Subpolar boundary and poleward, that warm blend is exactly zero,
leaving subpolar and polar atmosphere colors independent. The existing
400-to-50 distance ramp and shared shelter visibility drive the blend. TEST 7
particle budgets/types, warning copy/timing, wind, and shelter rules are
unchanged.

The initial candidate was adversarially rejected because a true world change
did not yet clear the active title payload. A symptom-specific RED proved that
hole; the corrected candidate received fresh adversarial ACCEPT. Focused clock,
latitude-boundary, color, and integration tests pass, as do the Java 25
19-task clean build/invariant scan and structure-climate guard.

This is source/model evidence. TEST 8 must still prove in the disposable profile
that `POLAR` appears once per genuine entry and that east/west storms look brown
below 50° without tinting Subpolar or Polar fog.

Canonical evidence:

`docs/binder/latitude-1-5-test7-presentation-feedback-20260720.md`

## Phase 8 release-candidate gauntlet

Phase 8 passed its bounded local acceptance gate after the final document-bearing commit passed its
immediate post-commit identity and protected-state readback. Starting from
`7df70c596251c72d9b960de684ad4e965142a4f7`, it completed the full post-Phase-5 audit, exact-jar
fresh/copied lifecycle, dedicated-server and Sodium checks, absent/Terralith/Tectonic/Biomes O'
Plenty provider rows, nine-row vanilla worldgen matrix, targeted beach/Meadow/50-degree/strict-80
runtime replay, HUD/config migration, loading-screen pixel proof, bounded performance
reconciliation, public-jar purity, and repeated adversarial review.

The final custom-biome representation audit found no exact release-blocking imbalance. All 22
admitted BOP IDs appeared; 16 of 18 admitted Terralith IDs appeared with the two absences explained
or held rather than speculatively tuned; both windswept target biomes returned; Pale Garden was
present inland across vanilla and provider rows; and all 51 tracked vanilla surface, shore, river,
and ocean IDs appeared somewhere in the matrix.

The final stale-code pass removed only demonstrably unreachable or inert remnants. Both registered
warm-band snow guards remain present, executable, scoped to Latitude generation, and covered by the
Java 25 build and policy tests.

The post-prune public jar used for bytecode and purity proof has SHA-256
`046c27b54e259454fe31ddd0c5c5954cac219c341ff254935f6b1596ec761973`, but its manifest records
commit `3aa77dea5fac2dbadcf0a816039680c20d082481` and `Build-Dirty: true`. It contains the
accepted snow-prune candidate bytes that were subsequently saved as `98b45b7b`, so it proves that
candidate's bytecode closure and public-jar purity. It is not a clean final artifact bound to
`98b45b7b` and is not the future release/profile-stage candidate.

Canonical contract, chronology, and closure:

- `docs/LATITUDE_1_5_PHASE8_RELEASE_CANDIDATE_GAUNTLET.md`
- `docs/binder/latitude-1-5-phase8-release-candidate-gauntlet-20260721.md`
- `docs/binder/latitude-1-5-phase8-closure-20260725.md`

Accepted old-save `/locate` ghosts, the irrecoverable exact 46-degree-south screenshot identity,
Terralith performance attribution, Promenade/combined-provider parity, physical all-block Pale
Garden contiguity, and non-attributable provider-distribution observations remain explicit HOLDs.
No 26.2 FPS/MSPT magnitude is claimed.

This is a local Phase 8 acceptance savepoint, not a release. The campaign checkout, Latitude
2.0/Pivot, real profiles/worlds, tag, push, upload, publication, profile staging, public-version
naming, release, and Phase 9 remain outside this lane.

## TEST 6 east/west advisory-to-particle correction

TEST 6 was the exact staged artifact from commit `76215c428bfc0bef850c029d462cfb2f6e4c328a`,
SHA-256 `37458a5a024d998c77ccf4ba640e12d533e6f4138bf195c7491817f294beece8`.
the maintainer's live report established that the seven-second first advisory was not encountered before the
final `Zero visibility ahead. Turn around.` message.

The initial TEST 6 source assumption was wrong: it tied the advisory to the 400-block smoothstep
endpoint, but that endpoint has zero intensity and integer particle rounding emitted no particle
there. The actual first emitted particle appeared only much later, allowing a slow approach to
consume the advisory before any visible storm cue.

The replacement policy defines the first visible-particle crossing as the first representable
distance inside the 400-block envelope and emits one sparse falling-sand particle there, without
also emitting haze. The first advisory is tied to that same crossing. This keeps the 400-to-50 fog ramp,
the 100-block final warning, the seven-second message episode, canopy/underground behavior, and
retreat semantics unchanged. Focused policy proof, Java 25 19-task clean build, and the
structure-climate guard passed. A fresh adversarial review specifically verified that the earlier
rounding gap had been closed.

This is source/model evidence only. TEST 6 remains a historic failing artifact for this exact
timing condition. TEST 7 must prove on both borders that one visible leading particle and the
first advisory begin on the same approach crossing, before the existing 100-block final message.

Canonical evidence:

`docs/binder/latitude-1-5-test6-advisory-particle-onset-20260720.md`

## Post-TEST 5 east/west presentation acceptance

the maintainer's TEST 5 recording admitted a bounded repair for early/abrupt east-west
fog, canopy-driven warning interruption, retreat replay, and inconsistent
presentation owners.

The accepted source candidate now:

- historically showed one non-bold, black-keylined seven-second advisory at the
  500-block borderward crossing: `Sandstorm on the horizon, consider turning back.`;
- preserves the final 100-block escalation
  `Zero visibility ahead. Turn around.` without downgrade or retreat replay;
- leaves live fog untouched through 400 blocks, then smoothsteps depth fog,
  particles, and Sodium culling to full strength at 50 blocks;
- removes the flat full-screen tan veil;
- shares one 13-sample confirmed-underground state across warning, fog,
  particles, and culling, so trees and arches do not interrupt presentation;
- requires authoritative synced Latitude-world identity before fog or Sodium
  changes, uses actual border geometry on both sides, and clears warning/world
  state directly on disconnect;
- arms direct initial polar lethal presentation at the actual stage-four
  threshold without replaying missed lower stages.

The Java 25 19-task clean build/invariant scan, structure-climate guard,
focused boundary/lifecycle tests, impact/diff/denylist audit, and repeated
fresh adversarial review passed. The reviewers blocked and forced repairs for
legacy percentage particle thresholds, non-authoritative world gates,
shifted-border wind, Sodium bypass, and disconnect lifecycle wiring before
returning final ACCEPT.

No village patch was admitted. Jungle and bamboo jungle are valid warm-zone
biomes, and the recording does not establish a fresh climate-wrong stored
structure start. A fresh desert village in BOP tundra remains an exact-ID
evidence gate. Accepted old-save `/locate` ghosts remain unchanged.

This is historical local source/model acceptance. TEST 6 exposed the later
advisory-to-visible-particle gap; the superseding TEST 6 correction above
controls the successor artifact and rendered replay.

Canonical evidence:

`docs/binder/latitude-1-5-test5-ew-presentation-20260720.md`

## TEST 4 boundary-feedback repairs and TEST 5 staging

Commits `a2babc2b` and `4f920096` close the two source/model failures reported
from the maintainer's TEST 4 polar and east/west boundary replay:

- the final polar zone now maintains vanilla freezing at 143 ticks every server
  tick for survival players, above the 140-tick vanilla damage threshold,
  instead of restoring 119 ticks every ten ticks and producing a no-damage
  thaw/refreeze sawtooth;
- east/west text selection now falls through from an expired finite polar
  episode to the first visibility warning, while preserving the order active
  polar lethal, east/west final, active nonlethal polar, east/west first;
- the first east/west copy is `Visibility is dropping ahead. Consider turning
  around.` and the final copy is exactly `Zero visibility ahead. Turn around.`;
- no third warning level, longitude/crossing system, direct damage call, or
  damage rebalance was added.

The prior strict foliage contract remains active without a duplicate patch:
ordinary tree and tagged simple-foliage origins strictly beyond 80 degrees are
suppressed in Latitude worlds, exactly 80 degrees remains eligible, and sweet
berry bushes remain exempt.

The focused frost and warning policy proofs, fresh foliage and village policy
reruns, all existing policy suites, invariant scan, and the Java 25 clean build
with 22 tasks passed. A fresh adversarial review independently checked the
vanilla freeze threshold/cadence and both east/west sides at the 500- and
100-block boundaries.

The exact TEST 5 artifact is staged as the authorized profile's only Latitude
jar with SHA-256
`aff403cc189d82aa5e41c451ce30be3e1b1eaf5a0d3d27ecf7ec0ca69063f9d0`.
It identifies version `1.5.0+26.2-test.5`, clean source commit
`4f920096969ada9a4602207691992160d7bf4fa8`, the correct branch, TEST role, and
Minecraft dependency `>=26.2 <26.3`.

No live-runtime claim follows from source, bytecode, or artifact proof. the maintainer's
next guarded replay must verify steady final-zone frost plus real vanilla
damage cadence, the first and final east/west messages, and the previously
pending TEST 4 rendered and fresh-terrain checks.

Canonical evidence:

`docs/binder/latitude-1-5-test4-boundary-feedback-20260720.md`

## TEST 3 live-feedback repairs and TEST 4 staging

Commits `d752477c` and `b064089b` close the bounded source/model findings from
the maintainer's TEST 3 replay:

- polar warnings use one non-bold, shadow-free fill over a styleless near-black
  keyline and remain visible for five seconds (`10 + 70 + 20` ticks);
- the `Press L...` helper occupies a reserved footer below the HUD Studio
  scroll viewport;
- the analog north label uses the Latitude 2.0 scale law
  `clamp(radius / 24, 0.4, 1.0)`;
- tree and simple-block foliage feature origins strictly beyond 80 degrees are
  rejected only in Latitude worlds, while sweet berry bushes remain allowed.

The foliage limit is independent of the 74.5-degree biome ecology cap and the
village policy. Both feature guards fail open in non-Latitude worlds. The
simple-block guard reuses the provider's existing sampled state rather than
consuming random state twice. Tree enforcement is origin-based: a canopy
rooted just inside 80 degrees may fringe across the line, and no per-block
canopy rewrite is claimed.

Focused RED/GREEN proofs, HUD verifiers, tag/registry validation, adversarial
review, and the Java 25 clean build with 22 tasks passed. The exact TEST 4
artifact is staged as the profile's only Latitude jar with SHA-256
`472a0a7271fdf3360f4619bd1cf320570a7dd9bf217971213e2919fcb3de10ae`.
Rendered warning weight/timing, HUD placement, compass scaling, and fresh
79.9/80/80.1-degree terrain remain live acceptance rather than inferred passes.

Canonical evidence:

`docs/binder/latitude-1-5-test3-live-feedback-20260720.md`

## Post-Phase-7 loading-copy refresh

Maintainer approved a copy-only refresh of loading-overlay entries 36–47. The exact 47-entry phrase
array remains intact; only those twelve strings changed. The refresh removes direct modpack and
"guest"/"visiting biome" framing, and uses "Following the contour lines..." rather than claiming
rain-shadow behavior. It changes neither overlay timing/rendering nor world-generation behavior.

The exact phrase invariant and banned-term check passed, followed by Java 25 `compileJava`.
This is a local source-copy savepoint only: it does not advance Phase 8 or authorize profiles,
worlds, tags, pushes, releases, uploads, or publication.

## Post-Phase-7 Meadow and TEST 2 acceptance

Maintainer approved a small, unobtrusive version label in the bespoke loading pane. Commit `cebd4732`
resolves `v<version>` from the active `globe` Fabric metadata and right-aligns it four pixels inside
the pane's bottom-right corner using the existing muted color and no shadow. Phrase cadence,
featured selection, compass, progress, and lifecycle behavior are unchanged. Structural proof,
the Java 25 clean build, and disposable TEST 2 live screenshots passed; the label displays
`v1.5.0+26.2-test.2` correctly anchored without competing with the pane content.

Meadow remains an upland/mountain biome. Commit `f9add4b9` removes exact `minecraft:meadow` only
from the late temperate warm-edge fallback pool and its validator, which were able to re-admit it
after the ordinary non-mountain filter. The Y>112 upland ramp, mountain tag, Registry/Collection
parity, and independent mountain promotion remain intact. The parent RED failed the two exact
fallback checks; the candidate passed 13 focused checks, Java 25 clean build/invariants,
provider-absent and Terralith strict climate, and a 55,225-cell impact scan. The scan changed 110
cells away from Meadow and zero into Meadow. Another 138 warm-edge Meadow cells remained; the
verifier separately confirms that independent upland/mountain routes remain wired, so latitude
alone cannot classify those cells as invalid. This does not import terrain shaping or any Latitude
2.0 system.

The `/latdev` commands were never removed from source. `L1.5-TEST-1.jar` was the normal dev-free
production artifact renamed for testing. Commit `211d8109` adds an explicit positive-sequence TEST
artifact that packages the Phase 6/7 developer toolkit separately while ordinary build, assemble,
sources, and publication paths remain dev-free. Post-commit artifacts:

- public `latitude-1.5.0+26.2.jar`:
  `0757762ec7dd08fd7c0fbaf773a83873dff747f5a516a96d6f305a894323cad1`;
- TEST `latitude-1.5.0+26.2-test.2.jar` and staged `L1.5-TEST-2.jar`:
  `73ca619f387d799e77e1f49c2e90cf5bb98bfdbc27cc3da01d4b2c8af26b9c33`.

The exact staged TEST 2 runtime reported version `1.5.0+26.2-test.2`, clean commit `211d8109…`,
the correct branch, and one common plus one client TEST initialization. In a disposable world
created with Latitude's `Commands: ON` rule, `/latdev help`, case, trace, marker, capture, and
finish all succeeded; the manifest-backed case closed `result=pass` with nine contiguous events.
The earlier unknown-command response occurred in a separate disposable world created with commands
off and is expected permission behavior.

Canonical evidence:

`docs/binder/latitude-1-5-meadow-test2-acceptance-20260719.md`

## Phase 0 safety result

- Implementation uses this clean worktree rather than re-anchoring the historical docs checkout.
- Base commit is pinned to `f26d5f586ed7fbf617b81cc366fac3255ab0bf1e`.
- The dirty 26.1.2 donor and 26.2 Pivot worktrees are read-only/protected.
- No Modrinth profile, jar, tag, push, publication surface, or protected dirty path is in scope.

## Included Phase 1 behavior

Each entry must either cherry-pick cleanly without excluded behavior or be reimplemented as a bounded
target-native patch.

| Evidence commit | Included behavior |
| --- | --- |
| `48e7f241` | Mushroom-island ocean gate, bounded polar diversification, savanna variety, and accepted alpine snow-line fixes. |
| `62658ff2` | Tropical-arid prohibition, bounded arid-belt rules, and poleward arid/frozen-river clamps. |
| `333e04db` | Temperate plains-on-steep terrain compatibility gate. |
| `bbde92b3` | Rename player-facing “Expedition” wording to “World.” |
| `7baa1acc` | Bonus chest and Generate Structures state truth. |
| `67e26ea5` | Six append-only analog compass themes. |
| `26e5dd9d` | CliffTree polar tag correction. |
| `0e806c07` | Keep flat wetlands off mountain terrain. |
| `1f6f278c` | HUD/HUD Studio sample zone and latitude truth. |
| `e8ad62bf` | Port only the loading-screen hunk that always starts with a Latitude feature phrase. The E/W warning hunk is excluded as crossing-adjacent. |
| `3c50c5d4` | Consolidate Pale Garden into one coherent region. |

## Included Phase 2 behavior

The following commits are **behavior evidence only**. Their source belongs to later APIs and/or
2.0-bearing history, so they must not be wholesale cherry-picked:

| Evidence commit | Required target behavior |
| --- | --- |
| `91eb6764` | Optional smooth scroll target/display separation after correctness. |
| `18a82995` | Spawn-zone rows render through a scissor while intersecting the viewport. |
| `b1e9a68b` | Rules controls render manually inside the Rules scissor. |
| `53ccd98a` | Rules layout is applied once per frame. |
| `0aa3d840` | Private Rules widget collection is cleared on every init. |
| `fbfbd8fa` | Fixed headings remain outside scrolling content. |
| `29a37881` | Remove the dead clip shelf under the Rules heading. |
| `10a61055` | One shared Rules clip boundary owns render, culling, hover, focus, and input. |

Target-native layout rule added from the maintainer's high-GUI-scale live review:

- GUI scale 3 or higher uses two tabs: World and Rules.
- The compact World tab places Spawn Zone choices beside the planisphere.
- The compact Rules tab uses a four-row, two-column control grid.
- Normal GUI scales also use tabs when the logical pane viewport is below 720 pixels.
- Three-column mode remains available only for normal-scale, genuinely roomy viewports.
- Any visible portion of a Spawn Zone row is selectable; the shared clip gate rejects only the
  off-page portion and fully hidden rows.

## Conditional only after an exact red

- Climate-mismatched structures (`7f0e5694`).
- Chunk-generation performance.
- `/locate` disagreement with actual Latitude biome.
- Additional distribution anomalies beyond the included bounded fixes.
- HUD reset/default drift or disappearing dropdown tooltips.

## Explicit holds

- Province wavelength (`6b834272`).
- New polar tree-line behavior (`611a307b`).
- Sparse-jungle-margin expansion (`aeb6bcb4`).
- Alpine-massif shaping (`6191f0fc`).
- Dev-command expansion (`4e92eb9d`, `db4dd160`).
- Mercator-specific size copy (`dc052342`).
- Mercator-width loading phrases (`ebcea32c`).

## Hard denylist

- `9a71caa1` Mercator world shape.
- `a476e86c` 2.0 version transition and every 2.0 product feature.
- Pole crossings or passage mechanics.
- East/west crossings or passage mechanics.
- Longitude or selectable world shapes.
- GeoAuthority, ClimateAuthority, Terrain V2, tectonics, geology, continents, or new ocean systems.
- Random Spawn Zone, the rejected book UI, later HUD/create-screen redesign, or atlas redesign.
- Wholesale merges/cherry-picks from the Pivot or a 2.0-bearing branch tip.

## Phase 0-2 result

| Phase | Status | Evidence |
| --- | --- | --- |
| 0. Safe foundation | **PASSED** | Clean worktree at `f26d5f58`; Java 25 baseline compile/invariant green; this manifest frozen; protected donor/Pivot dirt excluded. |
| 1. Canonical 26.1.x core | **PASSED** | Java 25 compile/invariant green; fixed-seed Regular/Itty/Ginormous exact-ID comparisons; strict band checks green; save-policy owner unchanged; final denylist audit green. |
| 2. World-creation anti-popping | **PASSED** | Maintainer live-confirmed anti-popping and the two-tab composition. The final clipped-row red has a shared production input policy, a dependency-free executable regression (`9` assertions), structural wiring proof, and a clean Java 25 build/invariant pass. This is a local pass; no post-fix screen retest was performed. |

Detailed chronology and metrics:

`docs/binder/latitude-1-5-phases-0-2-20260717.md`

These phase results are frozen by the single local implementation savepoint with parent
`f26d5f586ed7fbf617b81cc366fac3255ab0bf1e`. The resulting commit hash is verified from Git after
creation rather than embedded in its own contents. No tag, push, profile staging, upload,
publication, 26.2 port, or release authorization is included.

## Phase 3 result

| Gate | Status |
| --- | --- |
| Exact-ID cohesion and climate law | **PASSED** |
| Fresh/copy-old lifecycle and save policy | **PASSED** |
| Supported provider absent/present integrity | **PASSED** |
| Create-world and Re-create state truth | **PASSED** |
| HUD/HUD Studio truth and config persistence | **PASSED** |
| Fresh dedicated server and exact public-jar reload | **PASSED** |
| Public-jar purity and metadata | **PASSED** |

Phase 3 admitted two exact reds only: Re-create/seed state hydration and public-jar dev/debug
contamination. Their bounded fixes, mandatory proof matrix, conditional HOLD decisions, and evidence
paths are recorded in:

`docs/binder/latitude-1-5-phase3-20260717.md`

Phase 3 is frozen in local implementation savepoint
`3a79b608039c85e0ffca5f4f9a47e2e0935f7fa6`. It did not authorize the 26.2 port, profile staging,
tagging, pushing, release, upload, or publication.

## Phase 4 result

| Gate | Status | Evidence |
| --- | --- | --- |
| Lowest-target 26.1 clean build | **PASSED** | Java 25 clean build, invariant scan, and `9` clip-policy assertions. |
| Identical jar on 26.1 / 26.1.1 / 26.1.2 | **PASSED** | SHA-256 `c2c001e9a042d02d33b6f5a207ca1b3de35ab5191b8869b17ad1df370c8820a6` in every server, provider, and client row. |
| Fresh create, save/reload, and clean shutdown | **PASSED** | Three fresh dedicated-server worlds and three reloads; `30` NBT assertions. |
| Older-save carry-forward | **PASSED** | A disposable 26.1 world loaded under 26.1.1 and 26.1.2; `14` NBT assertions preserve seed, preset, and save policy. |
| Supported provider stacks | **PASSED** | Terralith/Lithostitched/Promenade/Biolith on all three targets; Biomes O' Plenty/GlitchCore/TerraBlender additionally on its supported 26.1.2 target; `74` assertions. |
| Exact public-jar client entry | **PASSED** | Three disposable clients joined task-owned servers, reached Latitude's first-safe playable tick, cleared the bespoke loading overlay, and left clean server stops; `42` assertions. |
| Candidate purity and metadata | **PASSED** | Entry/content denylist, mixin closure, metadata fence `>=26.1 <26.2`, and `12` exact candidate copies audited. |

Canonical Phase 4 evidence:

`docs/binder/latitude-1-5-phase4-20260717.md`

This local Phase 4 compatibility pass was committed as local savepoint
`858b815002ef82b20d55deadb0ac5ad970a9c823` (`Latitude 1.5: save 26.1.x candidate proof`). The
earlier frozen candidate correctly recorded `Build-Dirty: true` before that savepoint; that is
historical candidate metadata, not a current-tree claim. No real profile/world or screen/window was
accessed for Phase 4. At that historical boundary, Phase 5 authorization was a separate gate and the
26.2 port remained unauthorized. Profile staging, tagging, pushing, release, upload, and publication
remain unauthorized now.

## Post-Phase 4 local-polish record

The following commits are local, bounded follow-up work after the committed Phase 4 savepoint. They
do not extend the Phase 4 compatibility matrix or authorize Phase 5, a port, a release, a push, or
any profile/world action.

| Commit | Bounded local change | Current proof boundary |
| --- | --- | --- |
| `8fe97d051ca5b1a367916f2200d16c5ec31ad678` | Replaces polar blindness with 85–90° whiteout fog and finite, poleward warning episodes. | Live visual acceptance PASSED for fog density/color, warning outline/fade/direction, equatorward re-arm, and later poleward re-entry on the disposable 26.1.2 surface. |
| `d252770c258f9f4a7da6e8c11e98231cdd84233b` | Adds player-selectable HUD location detail modes: off, biome, zone, or biome plus zone. | Historical implementation commit; its live matrix exposed a pristine-default detached overlap that is superseded by `d2cdee02`. |
| `54b62988df85f8fc6ec5c8e882679bb82a6c91db` | Allows village origins through exactly 80° absolute latitude and vetoes only origins beyond it. | Historical placement-only implementation; its live matrix exposed stored-start `/locate` ghosts in fresh worlds and is superseded by `27327f20`. |
| `bf6e07281bb133977f25992b88136ced30cd6b6f` | Adds a lambda-local populated-biome column cache. | Base output, climate, and disposable create/save/reload proof passed locally; the exact Promenade and combined-provider output gate remains HOLD. |
| `188b2f52ed170b3c2a57937ea600ccbb10617607` | Caches constant biome IDs and immutable launch flags only. | Structural/fresh-JVM semantics and clean-build proof passed locally; no measured speed claim is made. |
| `0aacc55aebc1c74d5e71e73652052486ea5be024` | Batches analog-compass disc spans while retaining the existing visible-pixel model. | Exact raster/model parity, clean-build proof, and the live size/theme/transparency/preview/placement matrix PASSED on the disposable 26.1.2 surface. |
| `e6327c5715adc3acb295223d24963e74d682044c` | Rejects mountain-class temperate beach ridges while retaining low and rolling beaches. | Existing focused policy/model, accepted-output, climate, and build proof remains valid. Exact 46°S identity is irrecoverable, and three replacement proof harnesses were withheld from runtime after fresh QA false-green/safety findings; exact new BEACH runtime remains HOLD. |
| `d2cdee02051476e1681b7bb9620eac4aa5acbd80` | Separates pristine-default detached HUD detail from the compass while preserving explicit placement. | The 16-state digital/analog, Follow/Detach, Off/Biome/Zone/Biome+Zone live matrix, bounded-width representatives, real legacy-JSON migration, corrected default captures, and detail-only drag proof passed. |
| `27327f205f21f2b88625f60e49b614eed99278ee` | Prevents new beyond-80° village starts while retaining the existing placement shield. | The live allowed village at 79.104°, fresh-world `/locate`/bell/NBT checks, and `beyond80=0` passed; executable policy proof separately allows exact 80° and non-village paths. Copied old saves may retain stored-start `/locate` results while physical placement remains suppressed; Maintainer accepted that legacy compatibility residue for the pre-2.0 1.5 release on 2026-07-18. |

Pasture decision: rolling and upland terrain remains allowed. No pasture patch was made or is
implied by the beach-ridge guard.

### Exact partial/manual HOLDs

- Promenade exact parity remains HOLD: `8/55,225` sampled cells differ. Combined-provider exact
  parity also remains HOLD: `23/55,225` cells differ and the frozen environments are not exact
  matches. Matching palettes and stable provider presence do not turn either result into parity.
- The committed Phase 3/4 Terralith provider-compatibility matrix remains the authoritative pass.
  The July 18 correctness refresh is HOLD after three tooling candidates; the final closed-world
  candidate passed 56 pure tests but fresh QA found three remaining false-green paths. This does
  not revoke Phase 3/4 support and does not turn Promenade or combined-provider exact parity green.
- The controlled 56-pair Terralith performance harness/design is adversarially green, but execution
  has not started: fresh normalized non-benchmark CPU samples of `12.53%`, `14.47%`, `12.64%`,
  and `13.62%` exceeded the
  frozen `10.00%` ceiling. Protected Creative Cloud, WindowServer, Codex, OBS, and external Gradle
  activity remained untouched. The only Terralith performance result remains the prior
  inconclusive `2.5%` mean whose interval crosses zero. No FPS or MSPT claim is made.
- The accepted beach screenshot establishes `minecraft:beach` at 46°S but does not preserve seed,
  coordinates, profile, or jar identity. Exact-scene acceptance therefore remains identity-blocked
  HOLD. Three candidate/replacement harnesses were kept out of a new runtime after fresh QA found
  safety/provenance false-green paths; no substitute tuning is authorized.

Current acceptance chronology, exact candidate hashes, QA verdicts, and stop state:

`docs/binder/latitude-1-5-beach-terralith-acceptance-20260718.md`

### Accepted legacy behavior

- Copied old saves may retain beyond-80° stored village starts that `/locate` can return. Physical
  placement remains suppressed, and new worlds do not store new starts beyond 80°. Maintainer accepted
  this old-save locator residue for the pre-2.0 Latitude 1.5 polish release on 2026-07-18; no saved
  NBT deletion or migration is required for 1.5.

Polar presentation, the HUD 16-state/width/migration matrix, and the analog
size/theme/transparency/preview/placement matrix passed on the disposable 26.1.2 live surface.
The 26.2 port preserves those behaviors through focused policy, structural, config, and disposable
runtime proof; it does not claim a new 26.2 pixel-by-pixel visual review.

## Phase 5 result

The independent Minecraft 26.2 port passed all thirteen mandatory Phase 5 gates at implementation
and artifact commit `96c43b452027a2e89e7899b7f60227656dab151d`.

| Gate family | Status | Evidence boundary |
| --- | --- | --- |
| Java 25 build, invariants, and target-native impact audit | **PASSED** | Clean build plus all four executable policy suites. |
| Three-seed × three-size exact-ID and climate matrix | **PASSED** | Nine rows, no failures; summary SHA-256 `b563e1ef30c414bf70afe89c57b8b7e13250b543c170b3cb330dd49fed08cef7`. |
| Fresh and copied-old-save lifecycle/policy | **PASSED** | Fresh server/reload and copied 26.1-to-26.2 save migration; migration hash index SHA-256 `c4e4f56737a52ba30928383192bf5d2dcb98a71a185d4095783ffccd0ccc3739`. |
| Exact-jar client, create-world, HUD/config, and E/W barriers | **PASSED** | Exact candidate bytes loaded; fresh/reload first-safe-frame proof; create matrix `8ac866be…`; HUD matrix `03eab996…`; restart verdict `aee28c84…`; non-crossing barrier runtime proof. |
| Available providers and Sodium | **PASSED** | Absent, Terralith, Tectonic, and Biomes O' Plenty matrix SHA-256 `81c93856bd6a77eac19924e7247cfe444f185f5d4dbdae5e0ad1ad5a59d773b9`; Sodium `0.9.1+mc26.2` launch/world load. |
| Dedicated server, sulfur caves, and villages | **PASSED** | Fresh/reloaded server; exact sulfur-cave assertion at Y=2; villages through the allowed band and zero stored starts beyond 80° in the fresh proof world. |
| Metadata, purity, hash, and 2.0 denylist | **PASSED** | `1.5.0+26.2`, `>=26.2 <26.3`, `Build-Dirty: false`; jar SHA-256 `1753f8d038d3387b4c152a84623d85a2eaa0286a40ff510e2c00c6e30d793cc0`. |

The final docs exit savepoint, created after this text is reconciled, is intentionally distinct from
the implementation/artifact commit embedded in the proved jar.

## Phase 6 dev-only human-testing toolkit

Maintainer authorized a bounded Phase 6 tooling slice after the clean 26.2 port. It adds development-
environment-only commands for structured test cases, safe signed-latitude positioning, computed
polar-presentation policy traces, provenance-rich screenshots, and flight-speed control.

The public `/flyspeed` command was removed. Its replacement and every new Phase 6 command live
under permission-level-2 `/latdev`, whose implementation is excluded from the distributable jar.
The only production-source change is deletion of that public command and its unused imports.

Local proof passed:

- `55` dependency-free assertions cover path containment, nonzero border centers, representable
  latitude targets, unsafe ±90° rejection, movement transitions, append-only case ordering,
  overlapping-capture rejection, failed-I/O state preservation, and idempotent finish-summary
  recovery with no post-finish events.
- A disposable development dedicated server produced ordered case evidence, reported itself as
  `dedicated_server`, carried exact branch/commit/dirty identity, recorded marker-only capture
  semantics, and explicitly refused to call server coordinates rendered-presentation evidence.
- The Java 25 clean build, invariant scan, create-screen, polar, HUD-detail, and village policy
  suites passed.
- The public-jar verifier found no `com/example/globe/dev/**`, Phase 6 action payload, task
  evidence, or local absolute path.

The integrated-client screenshot and computed-policy trace paths are compiled, structurally
audited, request-time frozen, and development-gated, but were not driven in a live client during
this slice. No screenshot pixels, visible fog, warning rendering, or human aesthetic acceptance
is claimed by the tooling savepoint.

## Phase 7 integrated-client tooling validation

The Phase 6 toolkit was exercised in a new disposable Minecraft 26.2 integrated client and fresh
Latitude world at exact commit `e243d5bc6ee92bfcf7c4d39bf52c8107806c21ba`.

The case lifecycle, exact dev-build identity, gamemaster-source flight-speed command
acknowledgement, screenshot save and SHA-256, and computed fog/stage trace all produced usable
evidence. The screenshot SHA-256
is `fe97105a5760256c7efa546f86484dcd80884a86de88dfe3caeb558cb030cc9f`; its frozen row identifies
seed `26071915`, world position, biome, GUI scale, Latitude version, commit, branch, and clean build.

Phase 7 is **HOLD**, not passed. The live evidence exposed a developer-tool radius-authority
mismatch: at Z `9873.5`, `/latdev tpLat 89` reported `+89.003906°` using radius `9984`, while the
client presentation trace and screenshot context reported `+88.861500°` using the production
client radius. The presentation trace also recorded repeated same-dimension
`world_tick_rollback` resets around teleports, which interrupts longitudinal direction and
warning-episode evidence; the cause of those observed rollbacks is not yet established. These are
developer-tool evidence-integrity defects; no product behavior or source was changed in Phase 7.

No screenshot was treated as human aesthetic acceptance, and the computed trace still does not
prove final rendered pixels.

## Phase 7 repair result

Maintainer authorized one bounded repair for the two developer-tool evidence-integrity reds. The source
repair is frozen at local commit `c6b0131c93b4f6cc321caa5608ce84637c836a1a`.

- `/latdev tpLat` and its case context now use the same
  `LatitudeMath.worldRadiusBlocks(border)` authority as the production presentation path. In a
  fresh disposable 26.2 world, the command, trace, case marker, and captured screenshot context all
  agreed on `+89.005500°` at Z `9889.5` with radius `10000.000`. Exact `+90°` and `-90°` requests
  were honestly rejected by the existing border-safety margin.
- Same-dimension client game-time rollback now emits `clock_resync` and advances a monotonic private
  policy clock without clearing movement or warning-episode state. The live trace exercised seven
  such resynchronizations. The return from `+89.005500°` to `-88.996500°` remained
  `equatorward` with highest warning rank `2`; returning below 85° rearmed the episode.
- A real Overworld-to-Nether and Nether-to-Overworld transition produced exactly two
  `context_reset` rows with `reason=dimension_change`, each followed by an `initial` transition.
  This proves the repair preserved the reset that is actually required.
- `79` focused dev-policy assertions, all existing policy suites, the Java 25 clean build,
  structural verifier, public-jar purity audit, exact artifact identity, and a fresh adversarial
  review passed. Minecraft then saved all three dimensions and exited `BUILD SUCCESSFUL`.

The live replay ran Gradle's development client classpath from clean commit `c6b0131c…`; it did not
execute the distributable jar. The separately built public jar has SHA-256
`f8372dc84a650ef0445c4cd8966f1a7cd99ecc1c6526add849144996578c75df`, manifest
`Build-Dirty: false`, embedded commit `c6b0131c…`, and no developer payload. The identity-bound
case is `phase7repair`, seed `26071971`, under
`tmp/latitude-1.5-phase7-repair-20260719/`.

This closes the two Phase 7 tooling defects. It does not claim screenshot aesthetic acceptance or
final rendered-pixel equivalence, and it does not authorize a real profile/world, Phase 8, tag,
push, release, upload, or publication.

### Release boundary

Real profile/world staging, Phase 8, tagging, pushing, release packaging, upload, publication, and
every Latitude 2.0 behavior require separate Maintainer authorization.

Decision record:

`docs/binder/latitude-1-5-holds-accepted-phase5-authorized-20260718.md`

Phase 5 target manifest:

`docs/LATITUDE_1_5_PHASE5_PORT_MANIFEST.md`

Phase 5 chronology:

`docs/binder/latitude-1-5-phase5-20260718.md`

Phase 6 tooling chronology:

`docs/binder/latitude-1-5-phase6-tooling-20260719.md`

Phase 7 integrated tooling chronology:

`docs/binder/latitude-1-5-phase7-live-tooling-20260719.md`

Phase 7 repair closure:

`docs/binder/latitude-1-5-phase7-repair-20260719.md`
