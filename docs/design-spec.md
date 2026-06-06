# Design Specification: Latitude Hazard & Biome Tuning

## 1. Hazards Staging
Hazards must scale with normalized latitude progress to ensure consistent difficulty across all world sizes.

### 1.1 Progress Calculation
- `progress = clamp(abs(z) / halfSize, 0..1)`
- Computed in `LatitudeMath.hazardProgress`

### 1.2 Stage Thresholds
- **Stage 0:** `< 0.925` (Safe)
- **Stage 1:** `>= 0.925` (~83.25°) - Warning/Unease
- **Stage 2:** `>= 0.950` (~85.50°) - Danger/Hostile
- **Stage 3:** `>= 0.970` (~87.30°) - Critical/Whiteout
- **Lethal:** `>= 0.985` (~88.65°) - Death/Frozen

### 1.3 Visual Alignment
- Full whiteout particles and max fog strictly gated to **Stage 3**.
- Visual severity must never precede the corresponding stage text/effects.

## 2. Biome Weights & Composition
Land bands use a weighted selection system (70% Primary / 25% Secondary / 5% Accent) via deterministic tag-based pools.

### 2.1 Band Pools
- **Equator:** Neutral/Warm variety (Plains/Forest primary).
- **Tropics:** Jungle belt (Jungle primary).
- **Arid:** Dry belt (Desert/Badlands primary).
- **Temperate:** Seasonal belt (Forest/Plains/Birch primary; mountains as accent).
- **Subpolar:** Cold belt (Snowy Plains/Snowy Taiga/Grove primary).
- **Polar:** Ice belt (Snowy Slopes primary; no greenery/taiga).

### 2.2 Deterministic Picking
- Weights rolled using `LatitudeBiomes.weightedRoll` (cell-stable hashing).
- Prevents "endless jungle" or "infinite pale garden" clusters.

### 2.3 Tier And Fallback Coherence Savepoints
- A64 savepoint: commit `e19fc1cc`, tag `save/tier-coherence-a64`, branch `feat/1.3.1-cohesive-horizons-26.1.2`.
- `LatitudeBiomes.weightedRoll` uses `TIER_COHERENCE_BLOCKS = 64` for tier roll coherence. The committed source change was limited to `src/main/java/com/example/globe/world/LatitudeBiomes.java` (+6/-2): add the constant and route only the two weighted roll scale uses through it.
- At the A64 savepoint, `VARIANT_CELL_SIZE_BLOCKS` remained the fallback identity-pick scale. `pickFrom(...)`, fallback policy, province/sparse logic, tags, resources, atlas tooling, and generated artifacts were not part of A64.
- Canonical tree proof was compile-only: `env -u JAVA_TOOL_OPTIONS JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew --no-daemon --console plain compileJava` -> `BUILD SUCCESSFUL`. The full Small/Regular/Large matrix was accepted from the clean proof worktree `<home>/CascadeProjects/Latitude-tier-coherence-a64-proof` at seed `2591890304012655616`, not rerun in the canonical tree.
- Phase 1 second-seed rare-accent watch item is cleared on seed `7382045119866712340`: `windswept_forest` stayed present at regular `5 -> 4` and true large `8 -> 2`; no inventory adds/removes; `snowy_beach 0 -> 0`.
- R10000 atlas invocation must use `size=regular`; true Large must use `size=large` for R15000. Numeric `size=10000` is not a safe reproducer because non-preset values fall back to the authoritative world radius; this was resolved by invocation, not code.
- Comparator used: `<home>/.codex/worktrees/afe1/Latitude (Globe)/tmp/wild-lab/compare_atlas_runs.py`.
- Metrics caveat: A64 benefit stayed positive but weaker on seed 2 than the first-seed ~25-30% expectation. Regular components `18,441 -> 15,840` (`-14.10%`), offenders `14 -> 14`, inventory `50 -> 50`, tiny share `11.116% -> 10.501%`. True Large components `33,261 -> 28,868` (`-13.21%`), offenders `15 -> 15`, inventory `51 -> 51`, tiny share `11.313% -> 10.682%`.
- Phase 2 status: A64 source was already canonicalized at `e19fc1cc` / `save/tier-coherence-a64`; no source replay was needed.
- Option B savepoint: commit `b3a25a22`, tag `save/fallback-coherence-optionb`, branch `codex/fallback-coherence-optionb-savepoint`.
- `FALLBACK_COHERENCE_BLOCKS = 64` is applied only to the explicit fallback-list picker `pickFrom(Registry<Biome>, ...)`, leaving tier rolls, tags, resources, province logic, snowy/subpolar ramp gates, tooling, and generated artifacts untouched.
- Option B proof on seed `7382045119866712340`: A64 -> Option B regular components `15,840 -> 15,508` (`-2.10%`) and true Large components `28,868 -> 28,094` (`-2.68%`); non-excluded components improved `3,779 -> 3,546` (`-6.17%`) and `7,199 -> 6,752` (`-6.21%`). Offenders stayed flat (`14 -> 14`, `15 -> 15`), inventory stayed stable (`50 -> 50`, `51 -> 51`), `windswept_forest` stayed present (`4 -> 4`, `2 -> 2`), and `snowy_beach` stayed `0 -> 0`.
- Baseline -> Option B total components are now regular `18,441 -> 15,508` (`-15.90%`) and true Large `33,261 -> 28,094` (`-15.53%`) on seed 2. The improvement remains lower than the first-seed A64 magnitude, but Option B is incremental, low-scope, and positive.
- Option B proof artifacts: `<home>/CascadeProjects/Latitude-tier-coherence-a64-proof/tmp/a64-optionb/seed-7382045119866712340`.
- Roadmap closeout: A64 and Option B proof/savepoint gates are closed. Remaining canonical dirty WIP and generated/runtime artifacts are parked, unrelated to this roadmap, and should not be cleaned, reset, stashed, restored, or deleted automatically.

### 2.4 Latitude 1.4 "Cohesive Horizons" Worldgen Rebalance
- **Tropical wet-bias (root-cause fix for equatorial desert).** `ProvinceAuthority.classifyWarm` previously derived warm-side moisture from latitude-independent openness/humidity noise, so `WARM_DRY` desert pockets scattered uniformly across every warm latitude, including the equator. 1.4 adds an Earth-analog latitude wet-bias: `moisture += TROPICAL_WET_BIAS * smoothstep(1 - latDeg/23.5)` for `latDeg < 23.5`, with `TROPICAL_WET_BIAS = 0.20`. `effectiveRadius == ACTIVE_RADIUS_BLOCKS` (authority is rebuilt on `setActiveRadiusBlocks`), so the latitude term matches band math. The bias is zero at/above the tropical/subtropical boundary, so subtropical and cold-side provinces are untouched.
- **Anti-monoculture: WARM_WET variety preservation (Art. X).** An earlier `TROPICAL_WET_BIAS = 0.30` pushed almost the whole equator into the `WARM_WET` province, and `enforceWarmProvinceFamily(WARM_WET)` was collapsing every non-jungle pick — including admitted custom biomes like `biomesoplenty:tropics` and tropical wetlands — into plain jungle. The deep equator became 93% jungle with `biomesoplenty:tropics` disappearing entirely: a textbook Article X monoculture/biome-disappearance regression and a return of the pre-1.3 longitudinal blandness. Fix: the `WARM_WET` branch of `enforceWarmProvinceFamily` (both overloads) now preserves jungle-family **plus** any `isCustomBiome` pick **plus** swamp/mangrove, converting only out-of-place vanilla biomes to the jungle core. Combined with `TROPICAL_WET_BIAS = 0.20` (which lets some `WARM_MEDIUM` savanna clearings and rare `WARM_DRY` desert back at the equator), the inner equator (0-12°) goes from 5 distinct biomes / 68% jungle back to **10 distinct biomes / 34% jungle**, with `biomesoplenty:tropics` restored to ~13% — varied for both vanilla and custom-biome setups while staying jungle-dominant.
- **Coherence raised to moderate.** `TIER_COHERENCE_BLOCKS = 64 -> 160` and `FALLBACK_COHERENCE_BLOCKS = 64 -> 128` (supersedes the A64/Option-B values for 1.4). De-speckles secondary/accent tier picks into coherent patches.
- **Believable arid mix.** Badlands sub-province widened (`badlandsProvinceAuthorityHitModern` primary gate `0.40 -> 0.52`; `BADLANDS_OUTSIDE_PROVINCE_THRESHOLD 0.22 -> 0.34`), and the registry `chooseBadlandsVariant` now samples a coherent noise field (`BADLANDS_VARIANT_PATCH_SCALE_BLOCKS = 384`) instead of a per-block hash, so wooded/eroded badlands form coherent sub-regions rather than single-cell confetti.
- **Validation (band-aware analyzer `tools/atlas/band_balance_analyze.py` + `longitudinal_variety.py` + `compare_atlas_runs.py`; final wet-bias `0.20` + WARM_WET variety state).** Seed `2591890304012655616`: tropical jungle `31.4% -> 56.7%` (majority, not monoculture), tropical ARID `39.6% -> 20.8%`; inner equator (0-12°) `10 distinct biomes` — jungle `34%` / bamboo `24%` / savanna `18%` / `biomesoplenty:tropics 13%` / desert `10%` / swamp + sparse + badlands; jungle tiny-share `24.4% -> ~20%` (coherent province variety, not confetti — cross-province islands `70 -> ~30`), savanna components `248 -> ~100`, badlands verdict `borderline -> clean`; subpolar/polar jungle `0%`. **Scale-invariant** across tiny R5000 / small R7500 / regular R10000 (tropical jungle `57-68%`, inner-equator jungle `71-75%`, no drift). Generalization confirmed on seed `4048607749896891751` (equator `9 distinct`, `bop:tropics 18%`; subtropical desert/badlands mix). All runs include the BoP/Terralith/Promenade provider stack.
- **Confetti proof (rigorous method — `tools/atlas/distinct_render.py` + `tools/atlas/embedded_speck.py`).** Confetti MUST be judged from the exact `biome_ids.png` (distinct color per biome) at a fine step, never from the natural-color `biomes.png` (where jungle/bamboo/sparse are all green and hide intermixing). True embedded confetti (a ≤10px component fully enclosed by one other land biome), measured band-resolved at step16: **cross-family (objectionable) ≈ 0.45–0.47% of land**, uniform across bands (temperate/subpolar busiest at ~0.5–0.77% from legitimate BoP/Terralith forest/taiga richness, not marooned specks). The original-complaint pairs are negligible: `jungle`-in-arid 4–16 specks, `taiga`-in-plains/forest 10–19 specks, `old_growth_pine_taiga` specks `0`, across whole worlds. Dominant regions are coherent (jungle largest single region ~7,400px; badlands ~6 components).

### 2.5 Latitude 1.4 "Cohesive Horizons" — Custom Biomes, Art VI, Polar, World Entry

This section records the 1.4 work landed after the warm-band rebalance (atop `8c73beab`), each as a narrow per-subsystem savepoint with headless-atlas or live-probe proof.

- **Custom-biome source-wrap robustness (`save/custom-biome-sourcewrap-promenade`, `d133f63b`).** The `ChunkGenerator` biome-source-wrap `@Inject` constructors used Yarn-form descriptors (`world/biome/source/BiomeSource`) that never bind under this project's official Mojang mappings (`require=0` → silent no-op); corrected to `world/level/biome/BiomeSource`. Wrap is now getter-only (no `@Mutable` field reassignment), with deferred-wrap robustness: defer when `possibleBiomes()` throws on an unbound registry value or a Biolith biome lookup not ready during `WorldDimensions` stability/bake, and unwrap `LatitudeBiomeSource` to its `original()` in the populate path to avoid double-application. An inert source-candidate-pool seam (`expandSourceCandidatePool`, `rememberSourcePolicyBiomeRegistry`) is the documented hook for future source-side admission. Proof: biome map byte-identical to baseline (`authorityBandCounts` + every base biome count); the structure/surface wrap gate-rejects in headless preview (`settings_unresolved`) — machinery-correct without altering the terrain map.
- **Promenade biome admission (`save/custom-biome-sourcewrap-promenade`, `0965687c`).** `promenade:glacarian_taiga` added to `lat_subpolar_primary`; `promenade:blush_sakura_grove` + `promenade:cotton_sakura_grove` to `lat_temperate_secondary` (`required:false` → absent-mod-safe). Proof (atlas vs baseline, same seed/size): the three appear in their tagged bands (glacarian→subpolar, sakura→temperate), ~0.31% combined world share, displacing only thematic neighbors (snowy_plains/taiga for glacarian; plains/forest for sakura) — coherent regions, no monoculture, Art X clean.
- **Art VI: `pickFrom` coherent-noise selection (`save/artvi-pickfrom-coherent-noise`, `da1bc0dd`).** The subpolar/polar fallback-list pick used a `Math.floorDiv` cell-grid + `hash64(cellX,cellZ,bandIndex)` — a hard-edged cell-hash (Art VI violation, latent confetti source) that was also seed-independent. Replaced with argmax over N independent coherent `ValueNoise2D` fields (one per option) at `FALLBACK_COHERENCE_BLOCKS` scale, seeded by `WORLD_SEED` and band-differentiated. By symmetry each option still wins ~1/N of the area (uniform per-option share), so the change is distribution-neutral — proven by an atlas diff with byte-identical `authorityBandCounts` and per-biome shares — but the regions are coherent blobs, not grid cells.
- **Polar `ice_spikes` cap (`save/polar-ice-spikes-cap`, `3f8e0366`).** Base-source `ice_spikes` (preserved through polar selection) over-represented at ~9.08% of the world / ~34% of the polar band, over its ~6% accent cap. A source-agnostic coherent cap in `sanitizeLandBiome` keeps `ice_spikes` only where a coherent `ValueNoise2D` field is high (`POLAR_ICE_KEEP_THRESHOLD = 0.45` → coherent accent patches) and routes the rest to `snowy_plains`. Proof: `ice_spikes` `9.08% → 5.68%` (under cap, still present — Art X clean); the freed share flows to `snowy_plains` `6.06% → 9.46%`. snowy_plains becoming the dominant polar biome is **intended**: `gatePolarTaigaSurvival` + `removePolarTaigaFamily` deliberately exclude the taiga family (incl. `snowy_taiga`) from the polar band, so deep polar = snowy_plains primary + ice_spikes accent + mountains, no taiga.
- **World-entry render-gate + latitude early-spawn (`save/world-entry-render-gate-early-spawn`, `508c3231`).** `LevelLoadingScreenLatitudeOverlayMixin` now holds the bespoke loading overlay until the world is genuinely render-ready (player settled ≥20 ticks, spawn-chunk 3×3 ring loaded, render warmup ≥2.5s, sections compiled+visible, 15s fail-safe), avoiding a void/unrendered drop on entry. `MinecraftServerInitialSpawnMixin` + `GlobeMod.trySetInitialLatitudeSpawn` set a latitude-zone initial spawn before player-spawn pregen, clamped off the E/W edge-warning band. Live-verified via the dev auto-create-world probe: overlay holds through `renderedSections 0→27`, clears at the first safe playable tick (~13.2s); early spawn `zone=TEMPERATE`; no crash with the full provider stack.
- **Entry-title hemisphere authority fix (`save/entry-title-hemisphere-center-fix`, `c96a4eaf`).** `GlobeWarningOverlay` zone/hemisphere announcement titles now use the canonical `LatitudeMath.hemisphere(border, z)` (world-border-center-relative; North = −Z) instead of a divergent, inverted `z > 0 ? 'N'` test with the equator hardcoded at z=0, plus large-step (>256 block) suppression to avoid spurious crossing titles on teleports. Live-proven (`-Dlatitude.debugEntryTitles`): a northern spawn (`z=-4725`, `centerZ=0`) reads "TEMPERATE 43°N" with `canonicalHemisphere == stableHemisphere == N` (the old logic mislabeled it South).
- **Release / invariant hygiene.** The stale `DownloadingTerrainScreenFirstLoadMessageMixin` first-load-message feature — deliberately unregistered on the 26.1 port as a startup blocker but with its class/config/state/invariant-lock left orphaned — was discarded (`eec31d79`), so `./gradlew -PenableInvariantScan latitudeInvariantScan` passes again. The 26.1.x release jar is official-Mojang-mapped (no `remapJar`/refmap by design; that was a 1.20.1-intermediary-only concern); `clean build` → `latitude-1.4.0+26.1.2.jar`, dev tooling (`com/example/globe/dev/*`) excluded.

## 3. Rare Overrides
Specific biomes are removed from all tags and handled via ultra-rare post-pick overrides.

- **Pale Garden:** Temperate only, overrides `dark_forest` at 1/4000.
- **Sunflower Plains:** Equator/Temperate, overrides `plains` at 1/25.

## 4. Climate-Aware Shores
Beaches in cold bands must match the environment.
- **Subpolar/Polar:** Snowy Beach (70%) or Stony Shore (30%).
- **Warmer Bands:** Preserve vanilla beach selection.

## 5. HUD Render Invariant (EW Haze)
- EW haze must render inside `InGameHud#renderMainHud` **before** the `renderHotbar(...)` call so HUD elements (hotbar, crosshair, overlays) stay above haze.
- HudRenderCallback must **not** render EW haze; using both paths would double-draw and break z-order.

---

## Acceptance Tests (Release Gate)

### T1: Hazard Scaling
- [ ] In Itty Bitty (3750 radius), Stage 1 triggers at ~Z:3468.
- [ ] In Ginormous (20000 radius), Stage 1 triggers at ~Z:18500.
- [ ] Whiteout particles only appear when HUD shows Stage 3.

### T2: Biome Rarity
- [ ] `/locate biome minecraft:pale_garden` succeeds but is extremely distant.
- [ ] `/locate biome minecraft:sunflower_plains` shows them as small clusters in plains.

### T3: Cold Shores
- [ ] Beaches at >60° latitude are Snowy or Stony, never warm sand.

### T4: Arid Band
- [ ] Badlands are found within the Arid band (approx 30-45°).

### T5: Polar Integrity
- [ ] No taiga, meadow, or green forest biomes found at >70°.

## 6. Exact-ID Biome Coherence Gates
- `LatitudeBiomes` owns the late exact-family/province coherence gates that prevent tiny tag-valid biome families from surviving as biome-ID confetti after candidate selection.
- Sparse jungle may survive only when the warm province/base context still has jungle authority. Warm-wet/base and warm-medium explicit-tag residuals are rerouted through existing jungle/savanna-family fallbacks instead of being interpreted from atlas preview colors.
- Temperate taiga outputs use the same named-taiga classification across the non-shoulder interior gate and the warm-edge shoulder softener, so custom taiga IDs such as `terralith:birch_taiga` do not survive as tiny plains-contact fragments.
- Acceptance is an exact-ID gate over `step16_biome_ids.png` plus `step16_biome_palette.json`, not visual palette similarity. Current savepoint proof is `run-headless/latdev/atlas-runs/20260603-145101` and `tmp/latitude-confetti-exact-id-gate-930f7966/green-20260603-145101.json`: sparse-jungle/desert `0`, old-growth-pine-taiga/plains `0`, and Terralith birch-taiga/plains `0` contact edges.
