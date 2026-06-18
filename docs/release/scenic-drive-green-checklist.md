# Latitude Scenic-Drive GREEN Checklist

`scope: release-readiness live scenic proof` · `status: active` · `updated: 2026-06-18`

Use this checklist before calling a Latitude scenic pass, release-readiness scenic phase, or live world visual proof GREEN. This is a proof checklist, not a release authorization. Stop before commit, tag, push, upload, publication, cleanup, reset, stash, delete, or restore unless Maintainer explicitly authorizes that separate lane.

## Required Opening

1. Write the maintainer's six-line working card:
   - Objective:
   - Root/profile:
   - Allowed work:
   - Forbidden lanes:
   - Proof gate:
   - Stop condition:
2. Re-anchor repo state before mutation or live proof:
   - `git rev-parse --show-toplevel`
   - `git status -sb`
   - `git branch --show-current`
   - `git rev-parse --short HEAD`
   - `git tag --points-at HEAD`
3. Read the repo front-door docs:
   - `AGENTS.md`
   - `docs/HANDOFF.md`
   - `docs/LESSONS.md`
   - `docs/porting/PORTING.md` when profile, jar, version-family, biome/world-shape carryover, port, backport, or release state matters
   - relevant `docs/binder/*` notes for the active blocker or decision
4. Read the live-control instructions before driving Minecraft:
   - `<home>/.codex/skills/minecraft-live-control/SKILL.md`
5. Create one evidence folder for the run. Preserve prior evidence; do not delete or overwrite it.

## Runtime Truth Table

Record these before touring:

- Repo root, branch, HEAD, tag-at-HEAD, dirty state.
- Minecraft runtime window title and owner, proving the real `java` game window, not the launcher.
- Modrinth profile path.
- Active Latitude jar name, path, and SHA-256.
- Count of active `latitude-*.jar` files in the profile `mods/` folder.
- World name.
- World radius, border center, and border diameter.
- `isGlobe` / Latitude state from log, packet, F3/HUD, or equivalent proof surface.

## Control Gate

GREEN requires a reliable command/input route before the scenic checklist starts.

- Prove focus on the exact `Minecraft*` `java` window.
- Send one command at a time.
- Verify each important command in `latest.log`.
- If command entry is unreliable, repair or switch to a deterministic fallback before touring.
- Do not repeat no-op clicks, guessed coordinates, or raw gameplay typing loops.

Suggested proof:

- `/worldborder get`
- `/tp @s ...`
- `/time set noon`
- `/weather clear`
- screenshots before/after key control changes
- relevant fresh `latest.log` excerpts

## World Gate

GREEN requires the intended world or an explicitly justified replacement.

- Intended 30k Latitude/Globe world is open, or replacement is authorized and documented.
- `isGlobe=true` or equivalent current Latitude state is proven.
- Border diameter matches the intended world size.
- Radius and warning threshold interpretation are recorded.
- Save/world identity persists on disk.

For the 30k proof world, expected basics are:

- World: `Codex Cruise Large 30000`
- Radius: `15000`
- Border diameter: `30000`
- Center: `0,0`

## Scenic Route

Use representative stops across latitude bands and border-adjacent zones. Coordinates may be adjusted if terrain is obstructed, but each adjustment must keep the same proof intent.

For a 30k radius-15000 world:

| Stop | Intent | Example coordinate |
| --- | --- | --- |
| Equator | equatorial biome/HUD baseline | `0 160 0` |
| Tropical | low-latitude warm/dry behavior | `0 160 3000` |
| Subtropical | subtropical transition | `0 160 5000` |
| Temperate | temperate forest behavior | `0 160 7000` |
| Subpolar | tree-line/cold transition | `0 160 10000` |
| Polar | polar biome behavior | `0 160 12500` |
| South polar warning | polar warning stage | `0 160 14100` |
| South polar danger | polar danger/lethal warning | `0 160 14920` |
| North polar danger | opposite polar edge | `0 160 -14920` |
| East EW warning | E/W haze warning zone | `14520 160 0` |
| East EW danger | E/W danger zone | `14920 160 0` |
| West EW danger | opposite E/W danger zone | `-14920 160 0` |

Use F3 and clean/daylight screenshots as separate proof surfaces when useful. Aerial `/tp` with yaw/pitch is acceptable for visual overview if command logs prove the position.

## GREEN Criteria

Every row must be green or the pass is not complete.

| Area | GREEN means |
| --- | --- |
| Control | Live commands/input are reliable; no repeated no-op loops remain. |
| World | Correct Latitude/Globe world, profile, jar, radius, diameter, border, and persistence are proven. |
| EW haze/fog | Haze/fog appears in the correct E/W warning/danger zones, is visible, and is not absent, misplaced, too faint in danger, or overpowering outside danger. |
| Borders | East/west and polar warning text/direction/distance behavior are correct at warning and danger thresholds. |
| Latitude/biomes | Representative bands match expected latitude behavior; no obvious warm/cold/province mismatch is present. |
| Decoration | Terrain and vegetation are populated without confetti, random clutter, chunk holes, or obvious visual noise regression. |
| Tree-line/cherry/style signals | Cold/tree-line and special-style signals look appropriate for latitude and terrain. |
| HUD/settings | Compass/HUD/F3/settings behavior works and does not occlude critical proof. |
| Persistence | Save/world identity persists on disk with world name and Latitude/globe state evidence. |
| Performance/jank | Record stalls, `Can't keep up`, WARN/ERROR/Exception/crash lines, severe FPS/visibility issues, and obvious terrain bugs. Static checks should separate chunk-load teleport cost from stable-view jank. |
| Evidence | Screenshots, command logs, save proof, jar/profile hash, and final matrix are saved in the evidence folder. |

## Performance Gate

Do not classify performance from teleport chunk loading alone.

- Record route log warnings and `Can't keep up` counts.
- If the route has a stall, run a static soak in the relevant high-risk view after chunks load.
- GREEN requires no crashes, no severe visual breakage, and no ongoing static-view jank in the checked high-risk view.

## Final Report Template

Use this shape when reporting the result:

```text
Verdict: complete-green / partial / failed / blocked
Plain-English summary:
State:
  root:
  branch:
  HEAD:
  tag-at-HEAD:
  dirty state:
  profile/runtime/jar/world:
Checklist matrix:
  control:
  world:
  EW haze/fog:
  borders:
  latitude/biomes:
  decoration:
  HUD/settings:
  persistence:
  performance/jank:
  evidence:
Fixes made:
Evidence:
Residual risks:
Next recommended work:
Stop condition reached:
```

## 2026-06-18 Baseline Evidence

The checklist was first driven green against:

- Evidence folder: `<home>/.codex/tmp/latitude-scenic-green-20260618-172628/`
- Final verifier: `<home>/.codex/tmp/latitude-scenic-green-20260618-172628/commands/092-final-scenic-verifier.txt`
- Runtime/profile: `Minecraft* 26.1.2 - Singleplayer`, `Lat 1.4+26.1.2`
- Jar: `latitude-1.4.1-beta.2+26.1.2.jar`
- SHA-256: `2f794e16a331035738067a9983a7924a4da6171ee8c5a2b0dc9322127cb7a550`
- World: `Codex Cruise Large 30000`
- Border: radius `15000`, diameter `30000`

