# Latitude Handoff

`scope: Latitude (Globe) main docs/history repo` · `status: historical pointer — active line is the 26.2 pivot (see banner)` · `branch: main` · `updated: 2026-07-06`

> **What this doc is.** The single live "start here / resume here" pointer for `<home>/CascadeProjects/Latitude (Globe)`. It states the current repo state, the active source-of-truth split, and the next Maintainer-owned gate. It is meant to be overwritten as current truth changes. Durable lessons live in `docs/LESSONS.md`; port/backport operating rules live in `docs/porting/PORTING.md`; the chronological running log lives in `docs/binder/`.

---

## 2.0 line — resume on the 26.2 pivot (updated 2026-07-06; the 2026-07-01 banner below-referenced line is itself now historical)

## 2026-07-26 — PR #10 proof-parity follow-up (source/configuration proof only)

The current-main follow-up candidate aligns ordinary client and headless-atlas defaults with the already
settled shipping defaults for Solar Tilt, Pole Passage, Polar Barrens, and Glacial Caves. It also records an
atlas run's aspect and explicit `--sysprop` values, so a later ruggedness rerun can replay the same
configuration. Automated source/configuration proof is green; no live Minecraft, profile, artifact staging,
commit, push, pull request, merge, or release action was performed. See
`docs/binder/pr10-proof-parity-20260726.md` for the exact scope and verification.

> **This file is the `main`/history pointer, and it is a pre-2.0 lineage.** Active development is the
> **Latitude 2.0 overhaul on the 26.2 pivot root**:
> `<home>/CascadeProjects/Latitude-2.0-26.2-pivot`, branch `port/canonical-26.2-pivot`
> (version `2.0-beta.1+26.2`). Start there:
> - `docs/binder/fable5-overhaul-audit-report-20260706.md` — current truth + path forward (slices A–E,
>   gates G1–G3). Phase 4 is mechanically closed, live-blocked on a Y-taper prerequisite.
> - `docs/binder/index.md` — dated sections through the present.
> - `docs/LATITUDE_2_0_OVERHAUL.md` — overhaul front door (its Phase-4 section carries an EXECUTED status block).
>
> The previous (2026-07-01) version of this banner pointed at the 26.1.2 feature worktree
> (`Latitude-custom-biome-expansion-26.1.2` @ `30db22fc`); that line is now a prior-era reference and its
> binder resume notes carry supersession banners. See `docs/LESSONS.md` **L10** for the two-worktree doc
> split. Everything below this banner — including "Current state" and "Immediate next" — describes this
> `main` history checkout's pre-2.0 era and is **HISTORICAL**: do not resume candidate hardening from it.

## Current state

`Latitude (Globe)` is the main docs/history checkout on `main` at `fe660b50` (`docs: save scenic drive green checklist`). It is ahead of `origin/main` by 2 and currently carries local documentation/release-surface edits that point this historical 1.21.11 checkout at the active canonical 26.1.2 release-readiness truth.

This checkout is **not** the active Latitude 1.4 / Minecraft 26.1.2 source root. Its `gradle.properties` still describes the historical `1.21.11` / `1.3.0` line and is intentionally fenced as historical metadata. Current Latitude 1.4 candidate truth lives in:

- Canonical source root: `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2`
- Canonical branch / HEAD: `feat/custom-biome-expansion-26.1.2` / `e5d092ca7f09a397afc413137f62ea409566e1e7`
- Candidate/profile jar SHA-256: `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458`
- Profile proof settings: `renderDistance=16`, `simulationDistance=8`; prior `32/12` settings are preserved as a timestamped options backup in the Modrinth profile.
- Canonical release checklist: `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/release/checklist.md`
- Local release front door: `docs/release/checklist.md`
- Local scenic rerun checklist: `docs/release/scenic-drive-green-checklist.md`

## Current release-readiness truth

The 26.1.2 candidate is recorded as **partial, release not authorized**. The prior `972159d1...` pre-release-ready state was invalidated by the 2026-06-20 Itty render/load, worldgen stall, high-column terrain, palm-rendering, and performance/shutdown findings. The code-red fixes were staged and proved on `d51eace9...` for existing-save overlay/render and palm tint, then the latest local build `e09ea003...` was staged into the Modrinth test profile with cleaner render/simulation settings. Current `e09ea003...` proof is now green for exact Java-window launch, fresh SMALL `New Expedition` entry, render-gated overlay order, playable terrain, save/quit shutdown, non-live Itty Atlas diversity/no-collapse for seed `220220260619002`, and repeatable source/jar classification of the pasted post-1.4.0 findings.

The current evidence folders recorded by the release checklist are:

- Historical completed live route: `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/tmp/readiness-1.4-candidate-20260618-live-20260618-211834`
- Code-red `d51eace9...` live evidence: `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/tmp/code-red-deep-dig-20260620-203501`
- Current `e09ea003...` profile-stage plus SMALL smoke/shutdown evidence: `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/tmp/post-140-hardening-continuation-20260620-220852`
- Current `e09ea003...` Itty headless Atlas/live-lock continuation evidence: `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/tmp/post-140-hardening-continuation-20260620-220852/live-continue-20260621`

The remaining release-readiness proof is not green yet. Itty-specific live fresh/existing load and terrain proof, scenic delta, direct palm-canopy visual proof, and clean single-client non-teleport movement soak/performance must be rerun on profile jar SHA `e09ea003...` or explicitly deferred with evidence. The fresh SMALL smoke and save/quit shutdown proof are recorded; the headless Itty Atlas run does not show a one-biome/one-provider collapse; and `tools/lat-post140-findings-classifier` proves the report's concrete source findings are fixed/present in the active source/profile jar. None of those close the live Itty or scenic gates. The latest live continuation hit a macOS locked-session gate, so future live proof must start by re-proving exact Java-window capture after unlock.

## Maintainer-owned gates still open

The following are **not** completed by the readiness proof and remain separate Maintainer-owned actions:

- savepoint / commit / tag decisions
- push / upload / publication
- final public version name and public release copy
- future 26.2 evaluation
- future 1.21.11 or older-version backports

Do not treat `pre-release-ready proof` as permission to release.

## Read this next

1. `docs/release/checklist.md` for the local release-readiness front door and candidate truth table.
2. `docs/release/scenic-drive-green-checklist.md` before any future live scenic rerun.
3. `docs/binder/pre-release-readiness-doc-sync-20260619.md` for the dated docs-coherence update.
4. `docs/LESSONS.md` for durable "do not relearn this" rules.
5. `docs/porting/PORTING.md` before any port/backport or version-family carryover work.
6. `docs/binder/canonical-26-1-2-decision-20260618.md` for the maintainer's decision that 26.1.2 is the current canonical line.

## Active boundary

Do not resume savepoint/release/upload work, 1.21.11 backport work, or 26.2 planning from this handoff unless Maintainer explicitly reactivates that lane. The current state is: **Latitude 1.4 / 26.1.2 is partial on current SHA `e09ea003...`; fresh SMALL smoke and shutdown are green, Itty/scenic/performance remain open/partial, and publication/savepoint actions are still separate Maintainer decisions.**

## Immediate next (HISTORICAL — superseded 2026-07-06; see the 2.0 banner at the top; do not resume this)

Continue candidate hardening on the canonical root. First re-anchor both this docs/history root and the canonical 26.1.2 root, verify the candidate/profile jar SHA still matches `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458`, confirm exact Java-window capture works after the screen is unlocked, then run a clean single-client Itty/scenic/palm/movement-soak proof before any savepoint, tag, push, upload, or publication step.
