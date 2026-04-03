# Warm Province Authority Rewrite Map (Design Only)

## Problem Statement
Warm-band identity (wet/medium/dry) is currently decided in multiple, loosely-coupled spots: the early tropical chooser, arid fallback, and late savanna/jungle/badlands clamps. These paths can disagree, producing stripe-like arid artifacts and fragile jungle recovery. We need one province authority surface for warm bands, with world-size invariance, that upstreams family ownership and prevents late-stage climate invention.

## Non-Negotiables (Doctrine)
- Latitude band-first remains intact.
- Humidity breaks stripes inside bands; no new band-layer bias.
- No late path may invent major climate identity; late stages only clean up within the chosen family.
- World-size invariance and block-space continuity; no chunk quantization or checkerboard hashes.
- Rivers, oceans, cold-side logic, validator/tooling, and UI remain untouched in this rewrite slice.

## Current Entangled Warm Decision Points
- pickTropicalGradient(...) – early chooser mixing jungle/sparse-jungle, savanna, desert without a unified province field.
- pickAridRegionFallback(...) – arid hotspot + badlands patch specialization (adds its own geometry).
- applyFinalSavannaClimateClamp(...) – late jungle clamp, badlands gate, savanna uplift; can override earlier identity.
- sanitizeLandBiome(...) – can still rewrite plains→savanna in tropical bands.
- Authority fields in play: aridHotspotHere(...), badlandsPatchHere(...) (no unified warm province class).

## Proposed New Authority Surface
- warmProvinceClass(blockX, blockZ, effectiveRadius) → one of: WARM_WET, WARM_MEDIUM, WARM_DRY.
- Applied only within tropical/subtropical land bands; other bands unchanged.
- Expected family ownership:
  - WARM_WET → jungle / bamboo_jungle / sparse_jungle
  - WARM_MEDIUM → savanna family
  - WARM_DRY → desert / badlands family
- Late clamps sanitize only within the already-authorized family (no cross-family invention).

## Functions That Must Be Aligned in the Same Future Slice
- pickTropicalGradient(...)
- pickAridRegionFallback(...)
- applyFinalSavannaClimateClamp(...)
- sanitizeLandBiome(...) (if it still rewrites warm identity)

## Functions That Must Stay Untouched in That Slice
- Rivers, oceans, cold-side logic, validator/tooling, UI/atlas plumbing, world border/sizing.

## Risks to Watch
- Jungle/bamboo_jungle collapse if WARM_WET area shrinks.
- Desert inheriting stripe look if province noise isn’t east–west breaking.
- Warm monoculture if WARM_MEDIUM/WARM_DRY aren’t well separated.
- Late-path contradiction if any clamp ignores the new province authority.

## Proof Plan
- First proof: Tiny atlas (R5000, step16, seed 4545797416759118936, y=64).
- Layers to compare: biomes, warm_province (debug mask to add in that slice), plus existing arid_hotspot, badlands_patch, badlands_overlap for regression checks.
- After Tiny, validate one non-regular size per constitution (e.g., Small or Large) using the same layers.

## Hard Judgment
The first truthful implementation slice is **multi-entry**, not single-entry. At minimum it must align:
- pickTropicalGradient(...)
- pickAridRegionFallback(...)
- applyFinalSavannaClimateClamp(...)
(And sanitizeLandBiome if it still rewrites warm identity.) This is an architecture slice, not a micro-fix.

## Helper/Retirement Outlook (planning only)
- Add: warmProvinceClass(...), optional debugWarmProvince(...) for masks.
- Retire/soften later: badlandsPatchHere(...) if superseded by province authority.
