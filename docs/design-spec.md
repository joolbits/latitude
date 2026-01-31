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

## 3. Rare Overrides
Specific biomes are removed from all tags and handled via ultra-rare post-pick overrides.

- **Pale Garden:** Temperate only, overrides `dark_forest` at 1/4000.
- **Sunflower Plains:** Equator/Temperate, overrides `plains` at 1/25.

## 4. Climate-Aware Shores
Beaches in cold bands must match the environment.
- **Subpolar/Polar:** Snowy Beach (70%) or Stony Shore (30%).
- **Warmer Bands:** Preserve vanilla beach selection.

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
