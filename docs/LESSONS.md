# Latitude Lessons

`scope: durable project lessons` · `status: active` · `updated: 2026-06-19`

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
- `docs/binder/agent-doc-upkeep-rule-20260619.md`
