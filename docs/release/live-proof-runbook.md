# Latitude 1.4 Live Proof Runbook

`status: historical completed route; current rerun partial` · `updated: 2026-06-21` · `historical-candidate-sha256: 972159d1c825dff5803ff1e56eaa881a40c470c4a4e9f9640a06680856545b84` · `current-candidate-sha256: e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458`

This runbook records the safe route used to close the earlier live-only release gates. It is not authorization for future Minecraft control by itself; future reruns still need current Maintainer authorization and must follow the same exact Java-window discipline. The current staged profile/runtime candidate `e09ea003...` includes the code-red fixes; fresh SMALL `New Expedition`, save/quit shutdown, and headless Itty Atlas diversity/no-collapse proof are green on that exact SHA. the maintainer's 2026-06-21 manual retest reports the loading screen and chunk loading are now green in the staged Modrinth profile. Scenic delta, direct palm-canopy visual proof, broader decoration/biome visual review, and clean movement soak/performance remain open or partial.

## Hard Boundaries

- Do not use the vanilla Minecraft Launcher as a control surface.
- Do not use Computer Use against `Minecraft`, the vanilla Minecraft Launcher, or broad app-name matches.
- Do not type, click, press keys, focus, launch, quit, or send Minecraft commands until the exact Java-owned game window is proven.
- Do not mutate jars or profile files during this runbook. The staged profile jar must already match the candidate SHA below.
- Stop before savepoint, tag, push, upload, publication, or public release copy.

## Required Candidate

| Field | Required value |
| --- | --- |
| Canonical root | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2` |
| Branch / HEAD | `feat/custom-biome-expansion-26.1.2` / `e5d092ca7f09a397afc413137f62ea409566e1e7` |
| Staged runtime candidate jar | `<home>/Library/Application Support/ModrinthApp/profiles/Lat 1.4+26.1.2/mods/latitude-1.4.1-beta.2+26.1.2.jar` |
| Latest local build jar | `build/libs/latitude-1.4.1-beta.2+26.1.2.jar`, SHA-256 `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458`, staged |
| Historical completed candidate/profile jar SHA-256 | `972159d1c825dff5803ff1e56eaa881a40c470c4a4e9f9640a06680856545b84` |
| Current candidate/profile jar SHA-256 | `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458` |
| Current test profile settings | `renderDistance=16`, `simulationDistance=8` |
| Profile | `<home>/Library/Application Support/ModrinthApp/profiles/Lat 1.4+26.1.2` |
| Non-live gate | `tools/lat-readiness-nonlive-status` must pass immediately before live proof |

## Evidence Folder

Final evidence folder for the completed run:

```text
tmp/readiness-1.4-candidate-20260618-live-20260618-211834/
```

Current code-red evidence folder for the earlier `d51eace9...` partial rerun:

```text
tmp/code-red-deep-dig-20260620-203501/
```

Current `e09ea003...` fresh SMALL smoke/shutdown evidence folder:

```text
tmp/post-140-hardening-continuation-20260620-220852/
```

Current `e09ea003...` locked-session/Itty headless Atlas continuation folder:

```text
tmp/post-140-hardening-continuation-20260620-220852/live-continue-20260621/
```

Current partial proof:
- Existing Itty save on `d51eace9...`: bespoke overlay rendered early, `first safe playable tick` logged, terrain rendered in exact-window screenshot.
- Promenade palm tint compat applied to 4 blocks on `d51eace9...`; `e09ea003...` contains the same source-level fix and latest log confirms the compat hook applied.
- Fresh SMALL `New Expedition` on `e09ea003...`: exact Java-window proof, `size=SMALL`, bespoke overlay first render, `first safe playable tick`, rendered terrain screenshot, and clean save/quit.
- Headless Itty Atlas on `e09ea003...`: seed `220220260619002`, radius `3750`, step `64`, 74 biomes across Minecraft, BoP, Terralith, and Promenade. This is non-live diversity evidence only, because Atlas sampling skips the live `previewHeight()` path.
- Live continuation on `e09ea003...`: exact Java-owned `Minecraft* 26.1.2` title-window proof existed, but the macOS session locked before safe gameplay proof could continue.
- Maintainer manual retest on `e09ea003...`: loading screen and chunk loading are green in the staged Modrinth profile.
- Remaining red/open/partial: scenic delta, direct palm-canopy visual proof, broader decoration/biome visual review, and clean non-teleport movement soak/performance on `e09ea003...`.

Record at minimum:

- repo preflight output for both roots
- `tools/lat-readiness-nonlive-status` output
- active profile jar list and SHA
- window identity proof
- screenshots for every accepted visual gate
- `latest.log` copy or excerpts covering `[LAT][BUILD]`, world state, warnings, commands, WARN/ERROR/Exception/crash counts, and `Can't keep up` counts
- final matrix update

## Control Gate

Before any gameplay action:

1. Prove the exact game window is a Java-owned `Minecraft* 26.1.2` window.
2. Reject any window whose title contains `Launcher` or `Modrinth`.
3. Take a window-targeted screenshot before typing or clicking.
4. If focus, title, screenshot, or log identity is ambiguous, stop the live run and report control as red.

Acceptable local helpers after explicit authorization:

```bash
tools/mc-window
tools/mc-focus
tools/mc-shot
tools/mc-wait-shot
tools/mc-chat
```

The helpers must be syntax-checked and `tools/mc-window --self-test` must pass before use.

Before any screenshot-driven live proof, confirm the macOS session is actually unlocked. If exact-window capture fails and the session state shows `CGSSessionScreenIsLocked=1` or the visible window list is dominated by `Display 1 Shield` / `loginwindow`, stop the live run and classify it as a locked-session blocker rather than a Minecraft control bug.

## Gate 1: Fresh New Expedition Smoke

Purpose: prove a clean new-user path on the current staged SHA, not a saved scenic world.

Required proof:

- `latest.log` or screenshot proves `1.4.1-beta.2+26.1.2` and candidate SHA/provenance where available.
- New `New Expedition` flow reaches a Latitude world, not a vanilla world.
- World identity proves `isGlobe=true`.
- Radius/border persist and match the selected world size.
- HUD/compass/settings do not occlude the proof.
- No first-load crash, hang, invisible world, stuck overlay, or obvious render failure.

If the first created world is not Latitude/globe, stop and diagnose that gate before scenic movement.

Result: GREEN for current-SHA SMALL; GREEN_NONLIVE for Itty Atlas diversity/no-collapse; GREEN by Maintainer manual retest for loading screen and chunk loading.

Accepted evidence:

- `tmp/readiness-1.4-candidate-20260618-live-20260618-211834/live/020-fresh-world-log-slice.txt`
- `tmp/readiness-1.4-candidate-20260618-live-20260618-211834/live/021-fresh-world-persistence-proof.txt`
- `tmp/readiness-1.4-candidate-20260618-live-20260618-211834/live/hud-compass-clean/006-clean-hud-compass-day.png`
- `tmp/readiness-1.4-candidate-20260618-live-20260618-211834/live/visual-audit-notes.md`
- `tmp/post-140-hardening-continuation-20260620-220852/live-e09-fresh-small-clean-log-lines.txt`
- `tmp/post-140-hardening-continuation-20260620-220852/live-e09-new-expedition-current.png`
- `tmp/post-140-hardening-continuation-20260620-220852/live-e09-new-expedition-after-210s-static.png`
- `tmp/post-140-hardening-continuation-20260620-220852/live-continue-20260621/headless-itty-step64-inventory-summary.txt`
- `tmp/post-140-hardening-continuation-20260620-220852/live-continue-20260621/lock-state-current.txt`

Important: the current e09 world was named `Codex E09 Itty Smoke`, but the lifecycle log says `size=SMALL`. the maintainer's later manual retest, not that SMALL log, is the evidence that loading screen and chunk loading are green in the staged profile.

## Gate 2: Scenic Delta On Current SHA

Purpose: prove that the prior scenic GREEN did not regress on the current SHA.

Required proof:

- East/west haze or fog appears in the correct warning/danger zones, with screenshots close enough to inspect placement and intensity.
- East/west border warnings and distances match the world border.
- Representative latitude/biome bands still look coherent.
- Decoration density is sane: no confetti/noise/clutter regression, and tree-line/cherry-style signals remain plausible.
- HUD, compass, F3, and settings remain usable and do not hide critical proof.

This can be a delta-smoke, not a full scenic repeat, only if each prior scenic GREEN item has a current-SHA visual/log spot check.

Result: PARTIAL on current `e09ea003...`.

Accepted evidence:

- `tmp/readiness-1.4-candidate-20260618-live-20260618-211834/live/scenic-green-route-rerun4`
- `tmp/readiness-1.4-candidate-20260618-live-20260618-211834/live/scenic-green-route-rerun4/commands/046-west-danger-soak-summary.txt`
- `tmp/readiness-1.4-candidate-20260618-live-20260618-211834/live/visual-audit-notes.md`

## Gate 3: Non-Teleport Movement Soak

Purpose: add normal movement/performance evidence because the previous scenic proof was teleport-heavy.

Required proof:

- Move normally after chunks settle; avoid teleporting during the soak interval.
- Record duration, route summary, chunk/loading context, and visible FPS or jank notes where available.
- Count `Can't keep up`, WARN, ERROR, Exception, crash, and disconnect markers after the soak.
- Capture before/after screenshots and logs.

Any severe jank, crash, disconnect, terrain hole, persistent invisibility, or unreadable haze/HUD state is red until diagnosed.

Result: GREEN.

Accepted evidence:

- `tmp/readiness-1.4-candidate-20260618-live-20260618-211834/live/nonteleport-soak/001-soak-start.png`
- `tmp/readiness-1.4-candidate-20260618-live-20260618-211834/live/nonteleport-soak/002-soak-end.png`
- `tmp/readiness-1.4-candidate-20260618-live-20260618-211834/live/nonteleport-soak/003-log-summary.txt`
- `tmp/post-140-hardening-continuation-20260620-220852/live-e09-log-review-after-static-soak.txt`
- `tmp/post-140-hardening-continuation-20260620-220852/live-e09-thread-dump-after-static-soak.txt`
- `tmp/post-140-hardening-continuation-20260620-220852/live-e09-vm-stat-after-static-soak.txt`

Current e09 note: the fresh SMALL run logged three early two-second `Can't keep up` warnings, then no new log lines during the later static wait. Because another Java Minecraft client was running and system memory pressure was high, this is not a clean green soak.

## Gate 4: Save/Quit Shutdown

Purpose: prove that a current-SHA live run can save and return to title without the prior shutdown watchdog.

Result: GREEN for the current fresh SMALL run.

Accepted evidence:

- `tmp/post-140-hardening-continuation-20260620-220852/live-e09-savequit-log-final.txt`
- `tmp/post-140-hardening-continuation-20260620-220852/live-e09-crash-reports-after-savequit.txt`
- `tmp/post-140-hardening-continuation-20260620-220852/live-e09-after-savequit-final.png`

## Final Live Matrix

Completed updates after all live gates passed:

- `docs/release/current-readiness-audit.md`
- `docs/release/checklist.md`
- `<home>/CascadeProjects/Latitude (Globe)/docs/release/checklist.md`
- `docs/binder/evidence-registry.md`

Savepoint/tag/push/upload/release rows remain manual unless Maintainer separately authorizes that lane.
