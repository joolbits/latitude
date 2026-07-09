# Latitude Lessons

`scope: durable project lessons` · `status: active` · `updated: 2026-07-08`

This is the short "do not relearn this" file for Latitude. It is not the session log. The dated session log stays in `docs/binder/`, and the live resume pointer stays in `docs/HANDOFF.md`.

Read this before any non-trivial Latitude implementation, proof, release, or port/backport work.

## How To Use This File

- Start with the current `docs/HANDOFF.md`, then read only the lesson sections that match the slice.
- Treat a lesson as a preflight check, not as history trivia.
- Add a new lesson when a real mistake, false start, or costly rediscovery would likely happen again.
- Keep entries short: trigger, lesson, required future behavior, and evidence pointer.
- Do not use this file for dated narration. Put chronology and proof details in `docs/binder/`.

## L1 - 26.1.2 Is Canon For Latitude 1.4

Trigger: any question about whether to continue 1.21.11, move to 26.2, or backport 1.4 behavior.

Lesson: Minecraft 26.1.2 is the current canonical Latitude 1.4 line. Minecraft 1.21.11, 1.21.1, and 1.20.1 are backport targets. Minecraft 26.2 is a later evaluation target, not the current source of truth.

Required future behavior:
- Finish and prove the 26.1.2 source of truth before ordinary older-version backports.
- Do not patch 1.21.11 as if it is primary unless Maintainer explicitly reopens that structure.
- Any older-version work needs a scoped backport plan and proof gate.

Evidence:
- `docs/HANDOFF.md`
- `docs/binder/canonical-26-1-2-decision-20260618.md`
- `docs/binder/1211-province-gap-and-structure-question-20260614.md`

## L2 - Backports Need A Subsystem Manifest

Trigger: any port/backport from 26.1.2 to 1.21.11, 1.21.1, or 1.20.1.

Lesson: A backport is not "copy the latest patch." Latitude features are made of systems that can diverge by version. The 1.21.11 quality gap happened because the port had some v1.4 behavior but not `ProvinceAuthority`.

Required future behavior:
- Before code changes, list the donor system and every target subsystem included or excluded.
- Check both directions: what canonical has that the port lacks, and what the port has that canonical lacks.
- Mark partial backports honestly. A documented partial is better than an ad-hoc "almost v1.4" port.

Minimum subsystem manifest:
- `ProvinceAuthority` and warm/province coherence
- equator dry-biome demotion
- cherry / sakura / pale garden gates
- tree-line / alpine / snow-cap behavior
- custom-biome source wrapping and tags
- `/latdev` and proof commands
- client HUD, warning, and loading-overlay behavior
- release/profile/jar metadata

Evidence:
- `docs/binder/1211-province-gap-and-structure-question-20260614.md`
- `<home>/CascadeProjects/Latitude-port-1.4.0-1.21.11/docs/porting/HANDOFF-1.21.11-state-and-province-gap-20260614.md`
- `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/porting/HANDOFF-1.4-beta-backport.md`

## L3 - Root, Branch, Profile, And Jar Are Part Of The Proof

Trigger: any build, live proof, release-readiness check, Modrinth profile change, or port validation.

Lesson: Latitude work has repeatedly lost time to stale assumptions about which checkout, branch, profile, or jar is active.

Required future behavior:
- Run repo preflight before modifying a repo.
- For live or release checks, record a truth table first:
  - repo root
  - branch and HEAD
  - Minecraft version
  - Modrinth profile path
  - active Latitude jar name/version/hash if relevant
  - whether the surface is development truth, candidate truth, or public-release truth
- Do not run proof against "whatever jar is in the profile."

Evidence:
- `docs/HANDOFF.md`
- `<home>/CascadeProjects/Latitude-port-1.4.0-1.21.11/CODEX_CRUISE_CONTROL.md`

## L4 - Proof Types Must Stay Separate

Trigger: any claim of green, done, release-ready, or port-complete.

Lesson: compile proof, atlas proof, branch-client launch, Modrinth live proof, and release publication are different gates. One does not imply the others.

Required future behavior:
- State exactly what the proof checks and what it does not check.
- Keep partial `runClient` or launch evidence partial until title screen, world entry, and intended observations exist.
- Use atlas/headless proof for biome distribution and structural worldgen checks.
- Use Modrinth/live proof for HUD, overlays, visual feel, render path, profile jar behavior, and anything the dev client cannot drive reliably.

Evidence:
- `<home>/CascadeProjects/Latitude-port-1.4.0-1.21.11/CODEX_CRUISE_CONTROL.md`
- `<home>/CascadeProjects/Latitude-port-1.4.0-1.21.11/docs/porting/beta26-palegarden-atlas-proof-20260610.md`

## L5 - Use Exact-ID Atlas Evidence For Confetti And Biome Mix

Trigger: any judgment about confetti, arid leakage, equatorial variety, missing biomes, or biome over/under-representation.

Lesson: natural-color map screenshots can mislead. Exact biome-ID outputs and band analyzers carry the real signal.

Required future behavior:
- Prefer `biome_ids.png`, `biome_palette.json`, distinct-ID renderers, and band metrics for biome-distribution claims.
- Use natural `biomes.png` as visual context, not final proof.
- Keep exact commands and output paths in binder evidence when a savepoint or release depends on the result.

Evidence:
- `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/porting/HANDOFF-1.4-beta-backport.md`
- `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/binder/atlas-viewer-modernization-brief-20260605.md`

## L6 - Do Not Move Altitude Rules Back Into Latitude Blindly

Trigger: any proposal to use height-above-local-surface, vertical ecology, raised cloud decks, krummholz, or Altitude-style terrain behavior in Latitude.

Lesson: Altitude and Latitude solve different problems. Altitude uses height-above-local-surface; Latitude's current tree-line canon is absolute-Y near the cloud deck.

Required future behavior:
- Keep Latitude's current cloud-relative tree-line semantics unless Maintainer explicitly reopens the product design.
- Share utilities or amplitude machinery only when the behavior boundary stays clear.
- Do not let Altitude identity leak into Latitude release scope.

Evidence:
- `<home>/CascadeProjects/Altitude/docs/binder/lessons-from-altitude-into-latitude-20260608.md`

## L7 - Use A Working Card Before Non-Trivial Work

Trigger: any non-trivial code, docs, proof, port, release, or live-client slice.

Lesson: broad requests are normal. Maintainer should not have to translate them into safe engineering slices. Codex must do that work before acting.

Required future behavior:
- Write or state a six-line working card before risky or multi-step work:
  - Objective:
  - Root/profile:
  - Allowed work:
  - Forbidden lanes:
  - Proof gate:
  - Stop condition:
- If the symptom or target changes, rewrite the working card before patching or delegating.

Evidence:
- Local workflow audit on 2026-06-18.

## L8 - Scenic GREEN Needs The Full Live Checklist

Trigger: any claim that Latitude scenic proof, live visual release-readiness, EW haze/fog, border warnings, or biome cohesion is GREEN.

Lesson: A partial scenic pass can prove the world is loaded without proving release-readiness. The scenic phase is only GREEN when control, world identity, EW haze/fog, border warnings, latitude/biome cohesion, decoration sanity, HUD/settings, persistence, performance, and evidence artifacts are all checked.

Required future behavior:
- Use `docs/release/scenic-drive-green-checklist.md` before declaring the scenic phase GREEN.
- Treat ordinary control failures, missing command routes, or incomplete screenshots as engineering work, not as a Maintainer decision gate.
- Stop before cleanup, savepoint, release, upload, publication, or public communication unless Maintainer explicitly authorizes that separate lane.

Evidence:
- `docs/release/scenic-drive-green-checklist.md`
- `<home>/.codex/tmp/latitude-scenic-green-20260618-172628/commands/092-final-scenic-verifier.txt`

## L9 - Docs Coherence Is Part Of Done

Trigger: any non-trivial Latitude code, proof, release-readiness, port/backport, live-client, or docs slice that changes current truth, evidence, blocker status, or future resume state.

Lesson: Reading docs first is not enough. If the work changes what future workers need to know, Codex must update the live handoff and binder surfaces before reporting done. Maintainer should not have to notice stale docs and ask for the cleanup pass.

Required future behavior:
- Update `docs/HANDOFF.md` whenever the resume point, release/readiness state, root/profile truth, blocker status, or next gate changed.
- Add or update a dated `docs/binder/` note for chronology, evidence, decisions, or proof that future workers need to reconstruct.
- Update `docs/binder/README.md` when adding a binder note.
- Update this lessons file only for durable rules or repeated mistakes, not for ordinary dated narration.
- Keep current truth, historical audit entries, and speculative next work clearly separate.

Evidence:
- `AGENTS.md`
- `docs/binder/agent-doc-upkeep-rule-20260619.md`
- 2026-07-01: the 2.0 line shipped the Longitude compass reading and the Atlas/World Shape toggle with **no binder note for either**, and accumulated the whole Mercator/custom-biome/2.0-rename era on the feature branch with no top-level handoff — Peetsa had to ask for the doc-sync pass. Repeat L9 violation; the fix pass is `Latitude-custom-biome-expansion-26.1.2/docs/binder/current-state-handoff-20260701.md` + `atlas-worldshape-longitude-20260701.md` + `test1-live-findings-20260701.md`.

## L10 - Latitude Docs Are Split Across Two Worktrees; Update The Right Surface

Trigger: any "update the docs / handoff / lessons / binder" instruction, or any non-trivial 2.0-era work on the canonical 26.1.2 root.

Lesson: The Latitude doc surfaces do not all live in one place. The `main` worktree (`<home>/CascadeProjects/Latitude (Globe)`, a pre-2.0 lineage) owns the canonical single-file surfaces: `docs/HANDOFF.md`, `docs/LESSONS.md`, and `docs/binder/README.md`. The canonical 26.1.2 feature branch (`<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2`, branch `feat/custom-biome-expansion-26.1.2`) owns the **active** `docs/binder/` — `index.md` plus all the dated 2.0-era notes — but has **no** top-level `docs/HANDOFF.md` or `docs/LESSONS.md`. "Update the docs" in only one worktree silently leaves the other stale, and it is easy to forget the feature branch entirely because that is where the code lives but not where HANDOFF/LESSONS are.

Required future behavior:
- Put the dated binder note + the `binder/index.md` (and `evidence-registry.md`) update on the **feature branch**, where the work and the active binder are.
- Put durable lessons and the general resume pointer on **main** (`docs/LESSONS.md`, `docs/HANDOFF.md`), and/or keep a dated `current-state-handoff-YYYYMMDD.md` on the feature branch to serve as its handoff until the branches reconcile.
- When you say "docs updated," name which worktree each file is in, and whether it is committed. Do not assume one edit makes both surfaces coherent.

Evidence:
- `Latitude-custom-biome-expansion-26.1.2/docs/binder/current-state-handoff-20260701.md`

## L11 - "Docs Updated" Means Every Required Surface, Not Just The Dated Note

Trigger: any slice that adds a dated `docs/binder/` note (design note, phase note, proof note).

Lesson: a dated binder note is necessary but not sufficient. This repo's own entrypoint rule says every
binder update must also land a row in `evidence-registry.md` and a topic entry in `index.md` (see the
"Entrypoint rule" at the bottom of `index.md` on the active worktree). During the Latitude 2.0
GeoAuthority/ClimateAuthority phases (0-3, `Latitude-2.0-26.2-pivot`), four dated binder notes and two
design notes were written and linked from the front-door doc, but `evidence-registry.md` and `index.md`
were not updated until Peetsa directly asked "did you update the binder, handoff, and lessons." A repeat
of the exact failure mode L9 already named.

Required future behavior:
- Whenever a dated binder note is added on any worktree, add its `evidence-registry.md` row and
  `index.md` topic entry in the SAME pass, not as a follow-up prompted by the user noticing.
- When asked "did you update docs," check all three surfaces explicitly (binder note + registry/index +
  this lessons file if a durable rule emerged) before answering yes.
- Treat "I linked it from the front-door doc" as necessary, not sufficient — the registry/index is the
  machine-checkable surface this project's own contract requires.

Evidence:
- `Latitude-2.0-26.2-pivot/docs/binder/evidence-registry.md` rows `20260703-phase0-portability-foundation`
  through `20260703-phase3-climateauthority` (added retroactively, 2026-07-03, after Peetsa asked).
- `Latitude-2.0-26.2-pivot/docs/binder/index.md` — "2026-07-03 additions (GeoAuthority/ClimateAuthority
  phases 0-3)" section (same retroactive pass).

## L12 - A Type Bit XORed Into A Hash Does Not Guarantee Disjoint Namespaces; Parity Does

Trigger: any scheme that assigns stable ids to two mutually-exclusive categories (land/ocean,
continent/basin, etc.) by mixing a "which category" bit into a hash before taking the result as an id.

Lesson: during Phase 2 (GeoAuthority), the synthesized design assigned `compId = mix64(key ^ typeBit)`
and asserted land ids and ocean ids would never collide because the type bit differed. A JVM test
(`idNamespacesDisjoint`) found a real collision: two different keys, one land-typed and one ocean-typed,
mixed to the same output value. XOR-then-hash does not preserve any structural separation — the hash
scrambles it away. The fix that actually guarantees disjointness is to force a parity bit on the FINAL
id (land ids even, ocean ids odd) after hashing, not to fold the type into the hash input.

Required future behavior:
- When two categories must never share an id space, enforce it structurally on the final value (parity,
  a reserved high bit, or two disjoint integer ranges) — never rely on "the hash of different inputs
  will probably differ."
- Write the adversarial test for this BEFORE trusting a design document's own disjointness claim, even
  (especially) when the claim came from a rigorous multi-agent design/judge process — a design panel can
  simulate the happy path and still miss a structural guarantee a plain unit test catches immediately.

Evidence:
- `Latitude-2.0-26.2-pivot/src/main/java/com/example/globe/core/geo/GeoNoise.java` (`typedCompId`,
  parity-based fix) and `GeoAuthority.java` (parity enforced on the final `compId` from the column's own
  type, not just the table-lookup path).
- `Latitude-2.0-26.2-pivot/docs/binder/phase2-geoauthority-20260703.md` ("Findings / decisions" section).
- `docs/binder/agent-doc-upkeep-rule-20260619.md`

## L13 - A New Independent Signal Can Silently Break An Old Composition That Assumed Overlap

Trigger: wiring a brand-new authority/signal into an existing decision that already OR's/unions together
multiple "is this X" signals from different sources.

Lesson: during the Biome Consumer slice (`Latitude-2.0-26.2-pivot`), `LatitudeBiomes.pick()`'s ocean
decision has always been `base.is(IS_OCEAN) || oceanAuthority` — a union, not a replacement. This was
safe for years because the old `oceanAuthority` (from `OceanDistanceField`) measured the same underlying
terrain-continentalness noise that `base` already reflected, so the two terms overlapped heavily and the
union stayed close to either one alone. Nobody wrote that overlap assumption down anywhere; it was just
quietly true by coincidence of how the two systems happened to be built. When `oceanAuthority` was
swapped to GeoAuthority — a deliberately INDEPENDENT noise field, which is exactly what makes it able to
draw coherent continents — the hidden overlap assumption broke, and the union of two largely-independent
~60%/~36% water fields produced ~87% water instead of ~36%. Both signals were individually correct and
proven in isolation; the combination was still wrong, and no amount of testing either signal alone would
have caught it.

Required future behavior:
- Before replacing one input to an existing OR/union (or AND/intersection) composition with a new,
  independently-computed signal, ask explicitly: did the old composition's behavior depend on its inputs
  overlapping/correlating in some way that was never written down? If yes, expect the composition's
  aggregate behavior to shift even though every individual input is still "correct."
- Prove a new signal in isolation first (as GeoAuthority/ClimateAuthority both were, Phases 2-3), but
  do NOT treat that as sufficient — run it combined with the real, pre-existing system it has to coexist
  with (a live/headless test, not just an offline proof tool) before calling a slice done.
- When a combination surprises you, prefer pulling the risky half back apart (its own flag/gate) over
  patching the old composition's assumption under time pressure — the old assumption may be load-bearing
  in ways not yet understood (see: the ocean-authority swap staying flagged off pending Phase 4 terrain
  integration, rather than a quick constant-tuning workaround).

Evidence:
- `Latitude-2.0-26.2-pivot/docs/binder/biome-consumer-slice-20260704.md` (land fraction 39%→13% finding,
  the `-Dlatitude.geoV2.seaLevel=0.0` diagnostic that ruled out a GeoAuthority-side fix, and the
  `latitude.biomeConsumerV2.oceanAuthority.enabled` flag split that resulted).
- `Latitude-2.0-26.2-pivot/src/main/java/com/example/globe/world/LatitudeBiomes.java` (the
  `base.is(BiomeTags.IS_OCEAN) || oceanAuthority` composition, both `pick()` overloads).

## L14 - A Silent Classification Fallthrough Fails Plausibly, Not Loudly; Aggregate Proof And Label-Only Tests Both Miss It

Trigger: any classification cascade (if/else or switch chain producing one label from several input
dimensions) that has a "return SOME_DEFAULT" catch-all for combinations not explicitly handled; any proof
gate for such a system that only checks aggregate/statistical output.

Lesson: the very first live in-game session with the Biome Consumer slice's ClimateAuthority reroll
enabled showed "Snowy Taiga" immediately next to visible jungle trees at 18°N (tropical latitude), plus
"windswept gravelly hills" (a cold-climate biome) at what was visibly flat, low, sea-level terrain.
Root-caused by direct reproduction (`ClimateAuthority.assemble` at latitude 18°, `mountainIntent01=1.0`):
`temperature01` came out to 0.45 — GeoAuthority's altitude-cooling proxy (itself decoupled from real
generated terrain per L13) crashed the temperature enough to drop below `classifyBase`'s
`band==TROPICAL && T>=T_WARM` threshold, but not below the cold-climate thresholds either. With no
explicit case for "tropical band, moderately cooled," the cascade silently fell through every check and
landed on the generic default `HUMID_CONTINENTAL`, which then got alpine-stepped colder still into
`BOREAL` (vanilla family: taiga/snowy_taiga) — a serious, visually obvious wrong-biome result that read
as a perfectly ordinary (if misplaced) snowy forest, not a crash or garbage output. TWO verification
layers had already been trusted and both missed it:
1. The unit test for this exact "equatorial alpine" scenario asserted the FINAL LABEL equaled
   `"BOREAL"` and passed — but for the wrong reason. The intended path (tropical rainforest, alpine-
   stepped down) and the actual path taken (fallthrough default, alpine-stepped down) both happen to
   produce the string `"BOREAL"`, so the test could not tell a correct classification from a coincidence.
2. The proof gate that called this piece "safe to enable" checked land fraction, vanilla-biome-
   completeness, and crash-freedom — all aggregate/statistical measures. None of them can ever catch one
   wrong biome at one location, however obvious that wrong biome would look to an actual player standing
   there.

Required future behavior:
- Any classification cascade over more than one or two input dimensions must have an explicit case for
  every combination it can actually receive from its own valid input ranges — not a silent
  "return SOME_DEFAULT" catch-all. If a combination is believed unreachable, assert/throw or log loudly
  so a wrong assumption about reachability fails fast instead of silently returning a plausible-looking
  wrong answer.
- A test asserting only the final output label is not sufficient for a classification cascade with a
  fallthrough default, because a bug in the fallthrough can coincidentally produce the same label the
  intended path would have produced. Either assert on which branch/diagnostic path was taken, or add
  adversarial inputs specifically chosen to make the fallthrough and the intended path disagree, so a
  regression in either one is visible.
- Aggregate/statistical proof (percentages, counts, crash-freedom) is necessary but NOT sufficient before
  calling a system safe for a human to look at directly. Before that claim, spot-check individually-
  described concrete scenarios (a real mountain in the tropics, a real coastline, a real polar interior)
  against what a player would actually see there — the same discipline `docs/design/*-20260703.md`'s own
  acceptance tables were meant to provide, but a passing test on a coincidentally-correct label does not
  actually exercise that discipline.

Evidence:
- `Latitude-2.0-26.2-pivot/src/main/java/com/example/globe/core/climate/ClimateAuthority.java`
  (`classifyBase`'s `TROPICAL`-band branch requiring `T>=T_WARM`, with no case for a cooled-but-not-cold
  tropical column; falls through to the final `return HUMID_CONTINENTAL`).
- `Latitude-2.0-26.2-pivot/src/test/java/com/example/globe/core/climate/ClimateAuthorityTest.java`
  (`acceptanceTable`'s row14 assertion, which passed via the fallthrough path rather than the intended
  alpine-stepped-rainforest path).
- Live screenshot, 2026-07-05: 18°N showing "Snowy Taiga" next to jungle foliage; reported by Peetsa
  directly from an in-game session run with `latitude.biomeConsumerV2.enabled=true`.

## L15 - A Fix's Own Blast Radius Needs The Same Adversarial Check As The Bug It Fixes, Especially For Renames

Trigger: fixing a sweeper-confirmed finding by renaming a field/method/constant that is documented in
prose (a design doc's field list, an architecture contract) rather than only referenced via compiler-
checked call sites.

Lesson: sweeper audit #2 (2026-07-05) confirmed `ClimateSummary.currentModifier01` was misleadingly named
— a SIGNED [-1,+1] value using the record's `*01` suffix convention that means [0,1] everywhere else —
and the fix was to rename it to `currentModifierSigned` before any consumer existed. Before committing,
`grep` was used to confirm zero remaining Java references to the old name, and the two doc files already
open in that editing pass (`PORTABILITY_ARCHITECTURE.md`, the design doc itself) were updated. That felt
complete. A separate adversarial-review pass (an independent agent given only the diff, tasked with
checking the FIX for correctness rather than re-checking the original finding) found **3 stale
references still calling it `currentModifier01`** in prose-only doc locations: `docs/
LATITUDE_2_0_OVERHAUL.md`'s field contract (an exact duplicate list of the one doc that WAS updated,
living in a different file), `docs/binder/longitude-earthlike-world-overhaul-20260702.md`'s field list,
and — most tellingly — a second, un-updated heading inside the SAME design-doc file that had already been
touched for an unrelated correction earlier in the same editing pass. A grep across `*.java` alone would
never have caught these; only a grep across `*.md` (or a genuinely independent second look) would.

Required future behavior:
- Any rename of a field, method, or named contract that appears in more than one file must be verified
  complete with a repo-wide grep across EVERY relevant extension (`*.java`, `*.py`, `*.md`, `*.json` —
  whatever this project's doc/code mix actually is), not just the language whose compiler would catch a
  miss. Docs don't fail to compile when a rename misses them.
- Do not assume a file is fully updated just because you already edited it once in the same pass for a
  different reason — a second, unrelated reference in that same file is exactly the kind of thing that
  slips past "I already handled that file."
- Per the sweeper-as-standing-practice discipline (`docs/binder/sweeper-audit-standing-practice-20260705.md`),
  run an adversarial check on your OWN fix batch, not only on the original findings — a reviewer told to check "is this fix
  correct and complete" catches a different class of mistake than one re-verifying "is the original bug
  real."

Evidence:
- `Latitude-2.0-26.2-pivot/docs/binder/sweeper-audit-2-phases-0-3-20260705.md` ("Adversarial verification
  of this fix batch itself" section, listing the 3 stale references and the fix commit that followed).
- `Latitude-2.0-26.2-pivot/src/main/java/com/example/globe/core/climate/ClimateSummary.java` (the rename)
  vs. `docs/LATITUDE_2_0_OVERHAUL.md`, `docs/binder/longitude-earthlike-world-overhaul-20260702.md`,
  `docs/design/climateauthority-design-20260703.md` (the 3 places that still said `currentModifier01`
  after the first editing pass).

## L16 - A Design That Cites Real, Verified Facts Can Still Be Wrong; Verification Of Facts Is Not Verification Of Reasoning

Trigger: locking the Phase 4 (Terrain Integration Spike) density-wrapper design. A judge-panel workflow's
intended multi-candidate fan-out failed outright (all 3 parallel design agents hit a structured-output
retry-cap and errored; the 3 judges correctly refused to score empty candidates) but the workflow's final
synthesis step recovered solo by decompiling the actual MC-26.2 dependency jar itself and writing a
design grounded in real, javap-verified facts (exact `NoiseRouter` field order, `RandomState` internals,
`DensityFunction` interface shape). That design read as trustworthy specifically because it cited
concrete, checkable facts rather than guesses — and it was still wrong in two load-bearing ways: (1) it
wrapped both `finalDensity` (a density value, ~[-1,+1] scale) and `preliminarySurfaceLevel` (a Y-block
level, hundreds scale) with the *same* additive bias term, which is unit-incommensurable and would have
desynced the rendered surface from the surface-rule/spawn estimate — justified by a stated rationale
("spawn/heightmap reads `preliminarySurfaceLevel`") that was itself false (spawn actually re-samples
`finalDensity`); (2) its gating design cited `GlobeMod.isGlobeWorld(RandomState)`, a method that does not
exist and cannot be implemented as described from inside a `RandomState` (which holds no `ServerLevel`/
`ChunkGenerator`/settings key to check against). Neither defect was a hallucinated fact in isolation — the
individual facts cited (field order, interface shape) were all real — the defect was in the *reasoning
that combined verified facts*, which a solo author re-reading its own verified citations did not catch.
A dedicated second pass — 4 independent adversarial lenses (mixin mechanics, safety/regression, biome
side-effects, scope/proof-honesty) each finding candidate problems, then independent skeptics verifying
each raw finding before it counted — caught both defects (plus 10 more, including that the geography
field the whole bias is keyed on is itself edge/pole-coupled, meaning a "no edge/seam revival" hard stop
was satisfied in letter but not fully in spirit until disclosed and dispositioned as an accepted residual).

Required future behavior:
- A design or fix that cites real, independently-checkable facts (decompiled bytecode, grep'd call
  sites, javap output) is not thereby proven correct — verifying that individual facts are true is a
  different act from verifying that the reasoning connecting them is sound. Do not let "this design did
  its own verification and cited exact evidence" substitute for a separate adversarial pass; the two
  catch different failure classes (per [[L14]], [[L15]]).
- When a workflow's intended fan-out/diversity step fails (errors, retry-cap, empty results) but a later
  synthesis/judge step still produces a plausible-looking final result by recovering solo, treat that as
  a signal the intended adversarial diversity never actually happened — check the workflow's own error
  log/agent count, do not just accept a clean-looking final artifact at face value. Route the
  single-author result through a genuine second, independent adversarial pass before treating it as
  locked, exactly as if no verification had been attempted yet.
- For any density-function/DF-graph design touching more than one field of a shared record (e.g. a
  `NoiseRouter`), explicitly verify the UNIT and SCALE of each field before assuming one bias term can
  apply to all of them identically — "these two fields both determine terrain height" does not mean they
  share units, and an identical additive term across mismatched units is a specific, checkable defect
  class (not a vague style concern) that a dedicated reviewer should always test for.
- When a design's stated rationale for touching field B is "consumer X reads field B," verify that claim
  against the actual consumer code path, not just the field's doc comment or its name's plausibility — a
  false premise stated confidently reads identically to a true one until someone traces the real call
  path.

Evidence:
- `Latitude-2.0-26.2-pivot/docs/design/terrain-wrapper-design-20260705.md` (revision r2, "Revision note"
  and "Change-Impact block" in §1, and the §9 Residual-Risk register) — the corrected design, its
  Change-Impact block explaining exactly what the r1 design got wrong and why, and the residual-risk
  register (R1-R6) disclosing what remains an accepted, guarded risk rather than silently dropped.
- Workflow runs `wf_f03af3e6-cca` (failed 3-candidate design fan-out, solo-recovered synthesis) and
  `wf_ccd1545c-362` (the 4-lens adversarial review + verify pass that caught the `preliminarySurfaceLevel`
  unit-mismatch and the nonexistent `isGlobeWorld(RandomState)` gate, both CONFIRMED on independent
  verification, plus 10 further survivor findings).

## L17 - A Flag Being "On" Does Not Mean The Thing It Guards Is Actually Live; Gate On The Real State, Not Its Proxy

Trigger: a Phase 4 (Latitude-2.0-26.2-pivot) sweeper audit found that `TerrainRouterWrapping`'s install
gate checked `LatitudeV2Flags.GEO_V2_ENABLED` (a `static final boolean`, true whenever the JVM was launched
with `-Dlatitude.geoV2.enabled=true`) as its proof that `GeoAuthority`'s land/ocean field was live and
meaningful. It is not the same thing. `LatitudeBiomes.rebuildGeoAuthority()` only builds a real
`GeoAuthorityProvider` when `seed != 0L && zRadius > 0`; if a world's seed happens to be exactly `0`
(a real, pickable seed — some players deliberately choose it), `GEO_V2_PROVIDER` stays
`NoOpGeoSummaryProvider.INSTANCE` (`land01 = 0.0` for every column) even though `GEO_V2_ENABLED` is `true`.
The terrain wrapper's gate only asked "is the flag on," never "is the provider actually the real one," so
on a seed-0 world with both flags armed, every column reads as 100%-ocean and the wrapper pushes the
*entire world* downward — a whole-world, silent, only-live-testable defect that a flag-off/flag-on
mechanical proof would never surface (both legs it actually ran used a non-zero seed).

The same sweeper also found the mechanical proof gate that was reported "34/34 PASS" one commit earlier
was not trustworthy: the harness's own non-globe/globe-gate-isolation leg crashed with an unguarded
`Integer.parseInt` on an empty-string property (Gradle's `-D` forwarding sends `""`, not "absent", when a
knob is unset, defeating the two-arg `getProperty` default) — and, separately, that leg's "non-globe"
world was actually booted as a real globe world (armed radius left over from a prior committed
`server.properties`), so the wrapper genuinely installed on the world the leg claimed was excluded. A
report full of these silent failures/mislabels can still look like data a comparator diffs to "PASS."

Required future behavior:
- When a feature depends on an authority/provider that has its OWN internal preconditions for going live
  (a seed check, a radius check, an initialization order), the feature's gate must check that the
  authority is *actually* live (e.g. read back a real value and sanity-check it, or expose an explicit
  "is this the real provider or the no-op" query) — not just that the flag which is SUPPOSED to arm it is
  set to true. A flag and the state it is meant to represent can silently diverge whenever the guarded
  system has its own independent condition for real activation.
- Seed `0` (and other seemingly-arbitrary "special" values: radius `0`, empty string, `NaN`) are exactly
  the edge cases most likely to be silently mishandled by a nearby unrelated `!= 0`/`> 0` guard written for
  a different purpose — enumerate them explicitly for any new gate that composes with existing authority
  code, don't assume "the flag is on" is the only precondition that matters.
- Before trusting a mechanical proof's "N/N PASS," verify AT LEAST ONCE that a leg which is supposed to
  test the negative/excluded case (here: "a non-globe world must not get the wrapper") actually executed
  against a genuinely negative-case input, not a same-labeled-but-actually-positive one left over from
  stale on-disk state (a committed `server.properties`, a leftover world save, a previous run's
  artifacts). A harness that silently mislabels its own test conditions produces evidence that looks
  identical to real proof.
- Property/argument forwarding through build tooling (Gradle `vmArg`, shell wrappers, CI env
  passthrough) commonly turns "the user didn't set this" into "an empty string was set" rather than
  "the property is absent" — any `System.getProperty(key, default)` fed by such forwarding needs an
  explicit `isEmpty()`/`isBlank()` check before parsing, not just the two-arg default, or the default
  silently never applies.

Evidence:
- `Latitude-2.0-26.2-pivot/src/main/java/com/example/globe/terrain/TerrainRouterWrapping.java` (the
  `GEO_V2_ENABLED`-only gate, seed-0 finding) and `LatitudeBiomes.java` `rebuildGeoAuthority()` (the
  `seed != 0L` precondition it doesn't cross-check).
- `Latitude-2.0-26.2-pivot/src/main/java/com/example/globe/dev/TerrainProofHarness.java` (the unguarded
  `Integer.parseInt` on an empty-string-forwarded property, and the non-globe leg that booted an actually-
  armed globe world) and `build.gradle` (the `vmArg` forwarding that turns "unset" into `""`).
- Sweeper audit workflow `wf_2de5fa57-e76` (6 lenses, 26 raw findings, 23 survived independent
  verification) — the highest-confirmation-rate sweep this project has run to date.

## L18 - A Proof Harness Sharing Production Code Can Still Have A Structural Blind Spot If It Can't Reproduce The Real Call's Timing

Trigger: after Phase 4's terrain wrapper passed a locked design, a full mechanical proof gate (byte-
identity, structural NoiseChunk checks, the seed-0 NoOp-provider defense from L17), AND a 6-lens sweeper
audit, Peetsa's first live create-world test showed the wrapper installed on **zero** worlds, regardless
of seed or strength. Root cause, confirmed directly from the client log: the real-gameplay install site
(`RandomStateRouterTerrainMixin` on `ChunkMap`'s constructor) fires and evaluates the "is GeoAuthority
actually real yet" safety check (added for L17) *before* `GlobeMod`'s create-world flow finishes rebuilding
`GEO_V2_PROVIDER` for that new world's seed/radius. The one-time install-time check therefore always saw
the still-uninitialized placeholder and permanently refused to install — on every freshly created world,
not just seed-0 ones. Every prior proof (mechanical AND sweeper) passed cleanly because none of them could
have caught this: `TerrainProofHarness` and the atlas/dev tooling build their `RandomState` through
`BiomePreviewExporter`, which only ever runs on an already-fully-loaded `ServerLevel` — `GEO_V2_PROVIDER`
is *already real* by the time those paths call `installIfArmed`, so the exact ordering hazard that broke
real gameplay structurally cannot occur on the harness's own call path. The harness was calling the real
production helper, at a real `RandomState`, with real flags — and still never touched the one thing that
was actually broken, because "which call site, in what order" was invisible to it by construction.

Required future behavior:
- When a fix or a new safety check depends on a *world-load ordering* assumption (a rebuild happens before
  X reads it, an initializer runs before Y checks it), state that assumption as an explicit, checkable fact
  ("check runs after the rebuild") and ask: does every real caller actually satisfy it, not just the one
  the design doc happened to describe first? Two structurally different callers of the same helper
  (`ChunkMap`'s constructor vs. dev-tooling's already-loaded `ServerLevel`) can have opposite answers.
  This project's own design doc even *disclosed* the two call sites existed (`TerrainRouterWrapping`'s own
  class javadoc) without independently checking whether both actually see the same provider-readiness
  timing — disclosure of a difference is not the same as verifying the difference doesn't matter.
  Ordering conditions are not symmetric between similar-looking call paths and must be checked as such.
- A proof harness inheriting a shared helper from production code is necessary but not sufficient for
  proving that helper is really exercised the way live gameplay exercises it — additionally ask "does MY
  harness's own construction path reach this exact ordering window, or does it structurally always land on
  one side of it?" If the harness's own bootstrap sequence is different from the real path (dev tool boots
  post-load; real gameplay boots mid-load), assume any load-order-dependent behavior is unverified by that
  harness until proven otherwise, and say so plainly in the harness's own documentation (which is what this
  project's `TerrainProofHarness` already did for a *different*, correctly-anticipated gap — see its
  "Honest coverage-gap statement" — but did not extend to this specific provider-timing question until
  after the live bug was found).
- The fix itself is a durable pattern: re-home a state-freshness check from a one-time snapshot at
  install/construction time into the place that's re-evaluated on every actual use (see
  `GeoTerrainBiasFunction.compute()`, checked per density-function call instead of once in
  `TerrainRouterWrapping`'s install gate). A install-time-only check is only as good as the guarantee that
  nothing relevant changes between install and first real use — when that guarantee doesn't hold (a
  world-load sequence where dependencies finish initializing progressively), move the check to the
  consumption site, not the construction site.

Evidence:
- `Latitude-2.0-26.2-pivot/src/main/java/com/example/globe/terrain/TerrainRouterWrapping.java` (the removed
  install-time check) and `GeoTerrainBiasFunction.java` (the re-homed per-call check, with the fix's
  reasoning recorded directly in both classes' javadoc).
- Peetsa's live client log, 2026-07-06: `"[Latitude] Phase 4 terrain bias NOT installed..."` appearing one
  line before the province-authority seed/radius log line that would have made the check pass.
- A related, same-session finding: once the wrapper actually started installing live, chunk generation
  slowed noticeably at any nonzero strength — `GeoAuthority.sample(x,z)` doesn't depend on Y but
  `compute()` is invoked once per vertical noise-lattice cell (tens of times per column), so the wrapper
  was redundantly repeating the same expensive sample every call. Fixed with a per-thread, single-column
  memo. This performance cost was also invisible to every prior proof: the Phase 4 Spark baseline was
  captured at `strength=0`, which short-circuits before the code path in question is ever reached.

## L19 - Manual Live-Tuning A Vertical Bias By Eye, Alone, Is Slow And Error-Prone; Use Real Geography Data To Pick The Test Point, Not The Displayed Biome Map

Trigger: after the Phase 4 terrain wrapper was confirmed working live, Peetsa reported flying around a
world at various `strength` values and not being able to tell whether they looked meaningfully different,
plus a genuine chunk-generation slowdown (see L18) making iteration painful. Picking a "coastline" from the
*displayed* biome map is unreliable here specifically because `GeoAuthority`'s land/ocean field is
independent of the legacy biome-placement system whenever `latitude.biomeConsumerV2.enabled` is off (the
default) — the visible coastline and the terrain wrapper's own idea of "coast" are two different,
unrelated fields until that flag is turned on. A test coordinate chosen by eye from the map can land
somewhere the bias has no reason to do anything at all.

Required future behavior:
- When a live-tunable numeric knob is hard to evaluate by wandering, compute a small number of concrete,
  good test coordinates directly from the authoritative field the knob reads (here: querying
  `GeoAuthority.sample(x,z).land01()` directly, as a tiny standalone Java program against the compiled
  `core` classes — no Minecraft bootstrap needed, since Core Logic is plain Java by construction) rather
  than reading it off a rendered map that may be driven by a different system entirely.
- Give the live tester ONE specific, reproducible before/after test recipe (fixed seed, fixed coordinate,
  note the exact spot, change one number, look at the same spot in fresh terrain) instead of leaving them to
  free-fly and try to remember what a different number looked like ten minutes ago.

Evidence: `Latitude-2.0-26.2-pivot` session scratchpad `FindTestSpot2.java` / `FullProfile.java` — a
graduated seed-0 coastline located directly via `GeoAuthority`, independent of the (unrelated, in this
configuration) displayed biome map. (NB: the seed-0 half of that first coordinate was itself a mistake —
see L20 — the corrected, durable coordinate lives in the pivot's `phase4-terrain-wrapper-20260705.md`
correction block: typed seed `2591890304012655616`, `x=-3300, z=-3636`.)

## L20 - A Mid-Session Correction Isn't Done Until Every Durable Record That Carried The Mistake Is Amended

Trigger: correcting any recorded value or protocol (a coordinate, a seed, a command, a threshold) that was
already written into a binder note, registry row, handoff, or cross-session memory before the correction
happened; recording any armed live-evidence row.

Lesson: during Phase 4 live tuning, a calibration coordinate derived at seed 0 was handed to Peetsa and
written into the binder handoff. Peetsa caught the seed-0 error live ("I thought you said not to do seed
0") and a corrected coordinate was derived at the pinned standard seed — but only the conversation got the
correction. The binder kept the seed-0 coordinate and remained the standing protocol; cross-session memory
carried it too. The Fable 5 audit later flagged it as a P0 (its synthesis critic's headline catch):
following the recorded handoff verbatim would reproduce the "wrapper does nothing" symptom (literal-seed-0
worlds never arm GeoAuthority) or, worse, silently exercise the stale-provider hazard — a full wasted live
session queued up inside the project's own documentation. A compounding record gap: the live evidence rows
paired "seed 0" with armed-terrainV2 observations (behaviorally impossible as written) because nobody
recorded the ACTUAL typed seed or the same-JVM world-load history — the two variables the seed-0-inert and
stale-provider failure classes hinge on.

Required future behavior:
- When a recorded value is corrected mid-session, grep binder + registry + handoffs + cross-session memory
  for the stale value in the SAME pass and amend every surface that carries it. L15's rename discipline
  applies to VALUES, not just identifiers — a stale number doesn't fail to compile either.
- Armed live-evidence records must pin the actual typed seed and the same-JVM world-load history (fresh
  JVM? which worlds loaded before this one?), not just the intended flag config.

Evidence:
- Pivot `docs/binder/fable5-overhaul-audit-report-20260706.md` (§1 P0-2, §8 repair map).
- Pivot `docs/binder/phase4-terrain-wrapper-20260705.md` (2026-07-06 correction block) and registry row
  `20260706-fable5-slice-a-truth-restore` (the annotation of rows :123/:124).

## L21 - Changing One Layer Of A Multi-Map System Makes The Other Layers' Pre-Existing Mismatches Newly Visible; That Reads As A Regression But Isn't

Trigger: modifying ONE map/layer of a system whose final output is composed from several independent
layers (here: terrain height, biome LABELS + surface + structures, and GeoAuthority land/ocean INTENT are
three separate maps), when the layers were already quietly disagreeing before your change.

Lesson: the Phase-4 ocean carve (a terrain-layer change) made the sea floor follow GeoAuthority's
geography. It did not touch the biome layer at all -- but the moment the terrain moved, every place where
the *legacy biome map* already disagreed with geography became visibly wrong: Forest biomes labelling
open water, shipwrecks on forested coast, a mountain-grove Meadow at the waterline, "Deep Ocean" over
knee-deep water (TEST 28/30). Peetsa reasonably read these as new bugs the carve introduced. They are
not -- they are the pre-existing three-map decoupling (the biome consumer is not wired, so labels/surface/
structures run on the un-carved map) that the terrain change simply *exposed*. Measured proof: on the
81-column grid the disagreement classes existed at r=0 too; r=1 only made them visible in-world.

Required future behavior:
- Before shipping a change to one layer of a composed system, enumerate the OTHER layers it will newly
  expose and say so out loud in the handoff, so a surfaced latent mismatch isn't misfiled as a regression.
- Distinguish "my change caused this" from "my change revealed this" with a same-seed before/after on the
  UNCHANGED layer (here: the biome disagreement classes were identical at r=0 and r=1).
- The real fix for a decoupling is to make the layers share one source (the consumer law-compliance slice),
  not to patch the visible symptom in the layer you happened to touch.

Evidence:
- Pivot `docs/binder/test28-deep-ocean-decoupling-20260707.md` (the 81-grid classification),
  `fable5-slice-c3-grip-20260707.md` (the TEST 30 live-confirmation block: three biome complaints, one
  decoupling root cause), registry rows `20260707-test28-geo-decoupling`, `20260708-c3-live-green`.

## L22 - For A Boundary-Triggered Transform, Grade The ONSET Across The Boundary, Not Just The Magnitude; And In A Clamp Regime Grade The Clamp TARGET, Never Blend The Clamped OUTPUT

Trigger: any transform that (a) switches on when an input crosses a threshold, and (b) has a magnitude
that you already vary smoothly -- especially a min()/max() clamp whose whole safety argument rests on
monotonicity.

Lesson, part 1 (the wall): the C-2 ocean carve graded its DEPTH smoothly with distance from shore, but
its ONSET was instant -- the instant a column crossed to ocean-intent it clamped everything above the
(still shallow) target to air. Where the coastline crossed tall land, that instant onset planed the hill
into a sheer vertical wall (TEST 29 "cursed wall"). Grading how-much is not enough; you must also grade
whether-it-applies-at-all across the boundary, or you get a cliff exactly where the input is large at the
threshold. Fix: the carve ceiling now descends from above the terrain envelope down to the target across a
coastal ramp (`gripWidth`).

Lesson, part 2 (the dead end): the FIRST attempt at that ramp blended the clamped OUTPUT densities
(`base + grip*(carved - base)`). The gap-delta tripwire immediately caught it re-creating the exact
hollowing class the clamp existed to prevent (marginal underground pockets sandwiched into voids at
partial grip). In a min()/max() clamp regime, grade the clamp TARGET (a height/threshold that keeps the
pure min() intact), never interpolate the clamped result -- blending breaks the monotonicity that made the
clamp safe.

Required future behavior:
- When a carve/clamp turns on at a boundary, design the onset ramp from the start (a linear carve-onset is
  a wall generator, same failure family as the coastal wall).
- Never blend a clamped output to soften it; move the softening into the clamp's target so `min()`/`max()`
  semantics are preserved, and keep the gap-delta/no-new-voids tripwire on every such change.

Evidence:
- Pivot `docs/binder/fable5-slice-c3-grip-20260707.md` (shipped design + the DEAD END block, gates 5/5),
  `test29-r1-look-and-ui-round2-20260707.md` (the wall diagnosis), registry row `20260707-fable5-slice-c3-grip`.

## L23 - When You Split A Previously-Unified Quantity Into Two, Re-Audit Every Consumer's Contract; The Call Sites Where The Two Are Still Equal Will Hide The Bug

Trigger: introducing a distinction between two quantities that used to be one value (here: an analog
compass look's DRAWN CONTENT box vs its LAYOUT/dial box -- equal for every look except TAPE, whose strip
is shorter than its diameter box).

Lesson: round 2 correctly made the Studio hitbox/bounds content-true (so Tape's grabbable region matched
what's drawn). But a render call site kept feeding that now-content-adjusted `y` into a function whose
contract still expected the BOX top-left, double-applying the adjustment and pushing the docked Tape strip
off-screen -- in the Studio preview only, because the live in-game path used a different position source.
Every other compass look has content == box, so the adjustment was a no-op there and the bug hid
everywhere except the one look where the two quantities differ. It read as an isolated "Tape is weird"
glitch when it was actually a systemic coordinate-contract mismatch.

Required future behavior:
- When one value becomes two (content vs box, logical vs physical, pre vs post), grep every consumer and
  classify which space each expects; do not assume the callers that "still work" are correct -- they may
  just be the equal case masking the defect.
- Reproduce such bugs in the case where the two quantities are MOST different (Tape), not the default.

Evidence:
- Pivot `docs/binder/ui-pass-round3-fixes-20260708.md` (finding 2) + `ui-pass-round1-fixes-20260707.md`
  (round-1/2 content-vs-box dock work), registry rows `20260708-ui-pass-round3-fixes`, `20260707-ui-pass-round1-fixes`.

## L24 - Vanilla's Own Estimators Can Silently Misreport Under A Custom Density Wrapper; Audit Modified Terrain Against The Ground-Truth Field, Not A Derived Estimate

Trigger: measuring or gating modified worldgen using a vanilla-provided *estimate* (getBaseHeight, the
`*_WG` worldgen heightmaps, any cell-resolution column sampler) rather than the field your wrapper actually
changed.

Lesson: auditing whether the carve made oceans shallower, `getBaseHeight(OCEAN_FLOOR_WG)` reported ~10
deeply-carved columns as floors at Y94-118 -- while the finalDensity ladder (the field that actually places
blocks) proved them carved to air/water below Y40. Vanilla's cell-resolution height estimator misfires
under the carve's sharp `CEIL_FLOOR = -0.5` clamp. Taken at face value the estimate would have become a
false "some oceans got SHALLOWER" regression report; the ground-truth density ladder showed the opposite
(the carve only ever deepens). The generated world is correct -- only the derived estimate lied.

Required future behavior:
- When a wrapper modifies a density/noise field, treat vanilla's derived estimators over that field as
  suspect; cross-check any surprising measurement against the ground-truth field (the density value at Y,
  or the actual generated column) before reporting a regression.
- Carries a live-consequence flag: if game systems (structure placement, spawn) consume the same `*_WG`
  estimate, the misestimate can misplace them -- a live hypothesis for "shipwrecks in a forest," to verify,
  not assume.

Evidence:
- Pivot `docs/binder/ocean-depth-anchor-20260708.md` (secondary findings A and B), registry row
  `20260708-ocean-depth-anchor`.

## L25 - A Framework's "Focused"/"Active" Flag May Be Sticky; To Gate On "Currently Interacting," Track The Interaction Lifecycle Yourself

Trigger: gating any behavior on a UI framework's built-in state flag (`isFocused()`, `isHovered()`,
selection state) as a proxy for "the user is doing this right now."

Lesson: the Studio transparency-preview checkerboard was gated on the slider's `isFocused()`. Vanilla
widget focus is STICKY -- `setFocused(true)` fires on click and nothing clears it until a *different*
widget takes focus. So one touch of the slider latched the aid on for the rest of the session; it only
ever "cleared" by switching tabs (which rebuilds every widget). `isFocused()` answers "was this last
focused," not "is this being interacted with now." Fix: track the actual click->release lifecycle
explicitly (an `isDragging()` flag set in `onClick`/`onRelease`), which cannot get stuck.

Required future behavior:
- Do not use a sticky framework state flag as a live-interaction signal. If you need "currently dragging/
  editing," own the lifecycle (set on press, clear on release) rather than reading focus/selection.

Evidence:
- Pivot `docs/binder/ui-pass-round3-fixes-20260708.md` (finding 3), registry row `20260708-ui-pass-round3-fixes`.

## L26 - A Framework Widget Can Have Its Own Internal Auto-Refresh Hook That Silently Overwrites State You Set Externally; Verify There's No Competing Mechanism Before Bolting On Cross-Cutting Behavior

Trigger: attaching cross-cutting UI behavior (tooltips, styling, state) to a framework widget via a
generic base-class API (`AbstractWidget.setTooltip(...)`) instead of that widget SUBCLASS's own
dedicated mechanism for the same thing, especially when the subclass reacts to user interaction
(clicks, drags, value changes).

Lesson: every dropdown-style control in the HUD Studio (~30 instances) is a vanilla `CycleButton`,
and every one of them had its tooltip attached via a shared external helper calling the generic
`AbstractWidget.setTooltip(...)`. `CycleButton` turned out to have its OWN private `tooltipSupplier`
field and a private `updateTooltip()` method that vanilla calls AUTOMATICALLY on every value change
(every click) -- with no null-check, unconditionally overwriting whatever tooltip was set. Since this
codebase never populated that field (the builder's own `.withTooltip(...)` was never called), the
field held a no-op default (returns null), so the FIRST click on any of these ~30 controls silently
wiped its tooltip to null, permanently, for the widget instance's lifetime -- "click a button, hover
over it later, tooltip never comes back." The bug was invisible in the constructor/attachment code
(which looked completely correct) and only surfaced on the SECOND interaction (hover, after a click),
which is exactly the kind of gap a mechanical proof or a first-hover check would never catch --
confirmed root cause only by decompiling the actual widget class's bytecode, not by reading this
codebase's own source.

Required future behavior:
- Before attaching cross-cutting state to a widget via a generic base-class setter, check whether the
  CONCRETE widget class has its own dedicated mechanism for that same concern, and whether that
  mechanism fires automatically on interaction. If it does, use IT (the framework's intended path),
  not the generic setter -- or explicitly patch/neutralize the competing mechanism, as done here.
  Assuming "the constructor set it, so it's set" is not enough when the widget can independently
  refresh that same piece of state later.
- When a symptom only appears after a SECOND interaction with the same widget (not the first), suspect
  an internal auto-refresh/reset hook overwriting state your own code set once at construction --
  greenfield state is rarely the point of failure; the change-in-response-to-interaction path is.
- Decompiling the actual framework class (not guessing from behavior or reading only this project's
  code) settled this in minutes once tried; prefer it early over prolonged behavioral speculation when
  a vendored/vanilla class's exact internals are in question and available to inspect.

Evidence:
- Pivot `docs/binder/ui-pass-round6-fixes-20260708.md`, registry row `20260708-ui-pass-round6-fixes`.
