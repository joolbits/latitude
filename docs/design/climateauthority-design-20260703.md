# ClimateAuthority design — "Fetch & Lift" (Phase 3)

`status: implemented behind latitude.climateV2.enabled (default off)` · `date: 2026-07-03`
`layer: Core Logic (com.example.globe.core.climate)` · `zero Minecraft imports`

The locked earthlike climate model for Latitude 2.0, produced by a 4-design adversarial judge-panel
(earthlike-realism / rules+hot-path / adversarial lenses) and synthesized into one spec whose 14-row
acceptance table was **executed** (14/14) against the locked formulas, not asserted. Consumes Phase 2's
`GeoSummary`; **no biome-pack dependency** ([[vanilla-first-overhaul-constraint]]) — every `climateClass`
names a vanilla biome family first.

## Core idea

**Latitude sets only temperature and wind direction; precipitation is transported.** A bounded upwind
ray of GeoAuthority probes measures open-ocean **fetch**, so wet-west-coast vs dry-continental-interior
*emerge from geometry*, defeating the classic "latitude stripe" failure.

## The fields (exact per `ClimateSummary` field — full formulas in the binder note / source)

- **wind** (`prevailingWindX/Z`): three-cell (tropical easterlies / mid-lat westerlies / polar easterlies)
  as a pure function of signed latitude, via polynomial `bump(a,lo,hi)=4t(1-t)` humps. A `ZONAL_FLOOR=0.15`
  removes the 30°/60° zero-vector singularity (all four candidate designs shared that bug — it NaN'd the
  fetch ray).
- **temperature01**: insolation base `1.02·cos(a)^1.30` − altitude cooling (`mountainIntent01` proxy,
  weight `K_ALT=0.60`) − continental winter depression (mid/high lat only) + a coastal current nudge + a
  tiny dither. Poles freeze (ICE_CAP), boreal sits at 55–66°.
- **continentality01**: `smoothstep(0, 0.14R, coastDistanceBlocks)·land01` — maritime coast → deep interior.
- **precipitation01** (10-step transport pipeline): latitudinal lobes (ITCZ peak, mid-lat storm track) −
  a **continentality-and-fetch-gated** subtropical dry trough (wet windward coasts escape the arid belt;
  dry interiors keep it) − continental drying + **additive onshore-fetch moisture** (the wet-coast engine)
  + warm-western-boundary east-coast convergence + a current multiplier + a subsidence nudge, then a
  continental-interior floor and an **ITCZ convective floor** (protects Amazon/Congo), and finally a capped
  ±15% orographic **diagnostic** trim.
- **upwindOceanFetchBlocks**: exactly 4 GeoAuthority probes along −windUnit; the contiguous leading
  open-ocean run from the coast (land blocks fetch → dries interiors/lees). Flag `SHORT_FETCH_BLOCKED`.
- **windwardLift01 / rainShadow01**: bounded probes (windward reuses fetch probes 0–1; 2 new downwind),
  DIAGNOSTIC (`mountainIntent` is intent, not measured height) — flagged `OROGRAPHIC_DIAGNOSTIC`, enters
  precip only via the ±15% cap, never hard-overrides `climateClass`.
- **currentModifier01**: schematic, **basin-relative** — 2 lateral ocean probes give a `boundarySign`
  (ocean to the east → warm western-boundary +; ocean to the west → cold eastern-boundary −; both/neither
  → 0). NO `sign(x)` stripe; hemisphere-independent; cold eastern-boundary + short fetch = coastal deserts
  (Atacama/Namib), warm western-boundary = humid subtropical / mild-wet oceanic.
- **seasonalityClass**: `equatorial / monsoon / mediterranean / oceanic / continental / subpolar / polar /
  tropical_wetdry / seasonal / maritime`. **Monsoon is a class, not a wetness boost** — it has zero
  annual-mean precip term (hard rule 2).
- **climateClass**: a band-guarded Köppen-like cascade (a cold column can never reach a hot class), with an
  ALPINE step-down modifier for strongly-cooled highlands. See `ClimateClass` for the vanilla family per class.

## Hard rules (all honored)

1. Rain-shadow is DIAGNOSTIC (flagged, ±15% capped, never flips a class).
2. Monsoon is a seasonality class with no precip term.
3. Currents are basin-relative (geometry, not E/W stripes) — mirror-symmetry tested.
4. Hot path bounded: ≤ 9 GeoAuthority probes (1 center usually supplied + 4 fetch + 2 orographic + 2
   current) + 1 value-noise; no flood-fill, no real-height probe, no static state.

## Acceptance (measured on real geography, offline `climateAtlas`)

Earth-like distribution confirmed: tropical = rainforest 42% + savanna 35%; subtropical = **hot+cool
desert ~60% (the arid belt)**; temperate = humid-continental 24% vs temperate-oceanic 22% (interior/coast
split); subpolar = boreal 41%; polar = ice cap 82%.

## Residual risks (disclosed, tunable)

1. **Aggressive alpine cooling** — `mountainIntent01`-driven `altitudeCooling` pushes ~20% of tropical
   land into cold classes (boreal/tundra/steppe), more than Earth. Tunable via `-Dlatitude.climateV2.kAlt`
   (lower = less). The deeper fix is real terrain height (Phase 4), which retires the intent proxy.
2. **Orographic + rain-shadow are advisory** (intent, not measured height) — bounded + flagged; consumers
   must not promote them to hard terrain truth until Phase 4.
3. **Currents resolve cleanly only on N–S-trending coasts**; E–W coasts degrade to neutral
   (`CURRENT_INDETERMINATE`).
4. **Fetch resolution floor** ~0.06R (sub-15-block straits invisible on small worlds).
5. **cos/pow in temperature** are the only non-polynomial ops; tabulate if strict cross-JVM parity is ever
   demanded (as `GeoNoiseParityTest` enforces for noise).

## Where this lives

- `core/climate/ClimateClass.java` + `SeasonalityClass.java` — vanilla-first taxonomies.
- `core/climate/ClimateAuthorityParams.java` — the locked parameter table + overrides.
- `core/climate/ClimateAuthority.java` — the model (`sample` gathers probes; `assemble` is the pure,
  test-driven derivation).
- `adapter/climate/ClimateAuthorityProvider.java` — real `ClimateSummaryProvider`, wired flag-gated in
  `LatitudeBiomes` (`CLIMATE_V2_PROVIDER`); the interface was refined from Phase 0's `(latitudeDeg,band)`
  to `(blockX,blockZ)`.
- `dev/ClimateAtlas.java` — pure-Java offline proof tool (`climateAtlas` Gradle task).
