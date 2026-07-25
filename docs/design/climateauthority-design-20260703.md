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
  pre-scaled by `ALT_GAIN=0.85` then weighted `K_ALT=0.60` — the effective coefficient on
  `mountainIntent01` is `0.85·0.60=0.51`, not `0.60`; corrected 2026-07-05 sweeper finding #27, which
  found the pre-scale undocumented) − continental winter depression (mid/high lat only) + a coastal
  current nudge + a tiny dither. Poles freeze (ICE_CAP), boreal sits at 55–66°. The same pre-scaled `alt`
  also gates the ALPINE step (below): the true `mountainIntent01` threshold for alpine-stepping is
  `ALPINE_ALT/ALT_GAIN = 0.45/0.85 ≈ 0.529`, not the raw `ALPINE_ALT=0.45`. Ocean columns zero `alt`
  entirely (finding #19 fix, 2026-07-05) — `mountainIntent01` is a land-oriented intent signal with no
  submarine-terrain meaning.
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
  precip only via the ±15% cap, never hard-overrides `climateClass`. **This "never hard-overrides" rule is
  specific to lift/shadow.** The ALPINE step below is driven by the same `mountainIntent01` intent signal
  but *is* a hard, unconditional `climateClass` rewrite (corrected 2026-07-05 sweeper finding #28, which
  found the doc's "orographic signals are advisory" framing read as covering alpine too) — see residual
  risk 2.
- **currentModifierSigned** (renamed 2026-07-05 from `currentModifier01`, sweeper audit #2 finding #8 —
  SIGNED [-1,+1], unlike every other `*01`-suffixed field above): schematic, **basin-relative** — 2
  lateral ocean probes give a `boundarySign`
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
   **Corrected 2026-07-05 (sweeper findings #1-#12, #14-#15, #23; LESSONS L14):** the pre-fix
   `classifyBase` gated its TROPICAL/SUBTROPICAL productive branches on `T >= T_WARM(0.60)`, so a cooled
   column landing in `[T_BOREAL_HI, T_WARM)` didn't gently alpine-step down at all — it fell through the
   band switch entirely into a band-blind default (`HUMID_CONTINENTAL`, further alpine-stepped to
   `BOREAL`), a discontinuous jump the doc never disclosed. Fixed: `classifyBase` now switches
   exhaustively over `LatitudeBand` for every `T >= T_BOREAL_HI` column, so a cooled column classifies via
   its own band's normal precipitation logic and *then* the alpine step (below) demotes it one tier — the
   smooth, monotone behavior this risk always claimed. `mountainIntent01` remaining a land-oriented,
   terrain-decoupled proxy (finding #18) is unchanged and is still this risk's real substance; only the
   silent-fallthrough discontinuity is resolved.
2. **Orographic + rain-shadow are advisory** (intent, not measured height) — bounded + flagged; consumers
   must not promote them to hard terrain truth until Phase 4. **This applies to lift/shadow only.** The
   ALPINE class step-down is driven by the same `mountainIntent01` proxy but *is* promoted to a hard
   `climateClass` rewrite today, ahead of Phase 4's real terrain — the same terrain-decoupling risk this
   item discloses for lift/shadow applies to it too (sweeper finding #18, disclosed here 2026-07-05, not
   yet fixed: doing so requires Phase 4 real terrain height, not a Biome Consumer-slice-sized change).
3. **Currents resolve cleanly only on N–S-trending coasts**; E–W coasts degrade to neutral
   (`CURRENT_INDETERMINATE`).
4. **Fetch resolution floor** ~0.06R (sub-15-block straits invisible on small worlds).
5. **cos/pow in temperature, plus `Math.hypot` in wind-unit normalization** (corrected 2026-07-05,
   sweeper audit #2 finding #21 -- the prior wording said cos/pow were the *only* non-polynomial ops,
   omitting `sample()`'s `Math.hypot` call, which per the Java spec is likewise only guaranteed within
   1 ulp, not correctly rounded, the same cross-JVM parity hazard as cos/pow) are the non-polynomial
   ops; tabulate if strict cross-JVM parity is ever demanded (as `GeoNoiseParityTest` enforces for
   noise). `Math.sqrt`/`Math.round` elsewhere in both authorities are IEEE-754 correctly-rounded and
   are not a hazard.
6. **`seasonality()` falls through to the generic `SEASONAL` class for a near-equator TROPICAL column
   with high continentality or short fetch** (added 2026-07-05, sweeper audit #2 finding #17): the
   `EQUATORIAL` gate requires `cont<0.4 && fetch>0.15R && a<=12`; a TROPICAL column at `a<=12` failing
   that (via `cont>=0.4` or `fetch<=0.15R`) skips every other named branch and lands on the same
   `SEASONAL` default a TEMPERATE/SUBTROPICAL column with no special rhythm would get. Currently
   harmless -- `classifyBase`'s TROPICAL case does not gate on `seas==EQUATORIAL`, and no consumer reads
   `seasonalityClass` yet (same latent-field status as sweep #1 finding #30) -- but a future consumer
   keying behavior on "is this column equatorial-rhythm" would silently misclassify these columns.
   Flagged for whichever future phase starts consuming `seasonalityClass`, alongside finding #30.

## Where this lives

- `core/climate/ClimateClass.java` + `SeasonalityClass.java` — vanilla-first taxonomies.
- `core/climate/ClimateAuthorityParams.java` — the locked parameter table + overrides.
- `core/climate/ClimateAuthority.java` — the model (`sample` gathers probes; `assemble` is the pure,
  test-driven derivation).
- `adapter/climate/ClimateAuthorityProvider.java` — real `ClimateSummaryProvider`, wired flag-gated in
  `LatitudeBiomes` (`CLIMATE_V2_PROVIDER`); the interface was refined from Phase 0's `(latitudeDeg,band)`
  to `(blockX,blockZ)`.
- `dev/ClimateAtlas.java` — pure-Java offline proof tool (`climateAtlas` Gradle task).
