# Live smoke checklist (run before staging/release a jar that touches live-only paths)

Many Latitude behaviors are **live-only** — they are NOT exercised by the headless atlas (which is a fixed-Y
biome map with synthetic terrain). Those silently break on reverts/refactors with nothing to catch them (e.g.
the bonus chest broke when a UI revert also reverted the launcher's `WorldOptions` construction). Run this set
in the Modrinth App (`Lat 1.4+26.1.2` staging profile — HARD RULE: never the Mojang launcher) before staging a
jar that changes world creation, the launcher, GlobeMod spawn/border, mixins, or the create screen.

## World creation flags
- [ ] **Bonus Chest ON** → a bonus chest generates at spawn.
- [ ] **Generate Structures OFF** → no villages / strongholds / etc. generate.
- [ ] **Generate Structures ON** (default) → structures generate normally.
- [ ] Create-world screen wording reads "New World" / "Create World" (not "Expedition").

## World shape / bounds (Mercator)
- [ ] New world is 2:1 (Mercator) by default; more biomes per latitude band E-W.
- [ ] Poles hard-stop at the geographic pole; HUD latitude reads correctly (pole ≈ 90°).
- [ ] An existing (pre-feature) save opens unchanged (Classic square), no border resize.

## Worldgen sanity (live eyeball; the atlas covers the biome map)
- [ ] Temperate plains read as relatively flat; steep temperate terrain is hills/peaks, not plains-on-cliffs.
- [ ] No badlands/desert patches in temperate; no frozen rivers (or Terralith ice spires) in mid-temperate.
- [ ] Arid belt (subtropical) still has its deserts/badlands as coherent regions.

## Headless gates (run alongside — fast, automated)
- [ ] `python3 tools/atlas/band_correctness_check.py <atlas-run-dir>` → PASS (no wrong-band biome leaks).
- [ ] `python3 tools/check-biome-tuning-policy.py` → PASS.
- [ ] `bash tools/check_tree_line_port.sh` → pass.
- [ ] Classic atlas identity (when a change is meant to be Classic-neutral): `biome_ids.png` SHA unchanged vs baseline.

> Tip: for terrain-correlated changes (plains/badlands gates), the standard atlas can't see them — use the
> terrain-aware atlas mode (`--terrainAware=true`, once implemented) or this live checklist.
