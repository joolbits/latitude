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

### 2.3 Tier Coherence And Fallback Coherence
- A64 savepoint: commit `e19fc1cc`, tag `save/tier-coherence-a64`, branch `feat/1.3.1-cohesive-horizons-26.1.2`.
- `LatitudeBiomes.weightedRoll` uses `TIER_COHERENCE_BLOCKS = 64` for tier roll coherence. The A64 source change was limited to `src/main/java/com/example/globe/world/LatitudeBiomes.java`: add the constant and route only the two weighted roll scale uses through it.
- Phase 1 second-seed rare-accent watch item is cleared on seed `7382045119866712340`: `windswept_forest` stayed present at regular `5 -> 4` and true large `8 -> 2`; no inventory adds/removes; `snowy_beach 0 -> 0`.
- True Large atlas invocation must use `size=large` for R10000. Numeric `size=10000` falls back/coerces to regular R7500; this was resolved by invocation, not code.
- Comparator used: `<home>/.codex/worktrees/afe1/Latitude (Globe)/tmp/wild-lab/compare_atlas_runs.py`.
- Metrics caveat: A64 benefit stayed positive but weaker on seed 2 than the first-seed ~25-30% expectation. Regular components `18,441 -> 15,840` (`-14.10%`), offenders `14 -> 14`, inventory `50 -> 50`, tiny share `11.116% -> 10.501%`. True Large components `33,261 -> 28,868` (`-13.21%`), offenders `15 -> 15`, inventory `51 -> 51`, tiny share `11.313% -> 10.682%`.
- Phase 2 status: A64 source was already canonicalized at `e19fc1cc` / `save/tier-coherence-a64`; no source replay was needed.
- Option B savepoint: `FALLBACK_COHERENCE_BLOCKS = 64` applies only to the explicit fallback-list picker `pickFrom(Registry<Biome>, ...)`, leaving tier rolls, tags, resources, province logic, snowy/subpolar ramp gates, tooling, and generated artifacts untouched.
- Option B proof on seed `7382045119866712340`: A64 -> Option B regular components `15,840 -> 15,508` (`-2.10%`) and true Large components `28,868 -> 28,094` (`-2.68%`); non-excluded components improved `3,779 -> 3,546` (`-6.17%`) and `7,199 -> 6,752` (`-6.21%`). Offenders stayed flat (`14 -> 14`, `15 -> 15`), inventory stayed stable (`50 -> 50`, `51 -> 51`), `windswept_forest` stayed present (`4 -> 4`, `2 -> 2`), and `snowy_beach` stayed `0 -> 0`.
- Baseline -> Option B total components are regular `18,441 -> 15,508` (`-15.90%`) and true Large `33,261 -> 28,094` (`-15.53%`) on seed 2. The improvement remains lower than the first-seed A64 magnitude, but Option B is incremental, low-scope, and positive.

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
