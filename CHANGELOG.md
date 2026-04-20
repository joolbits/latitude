# Changelog

## Latitude 1.3.0 (MC 1.21.11)
Latitude 1.3.0 is a major upgrade from the 1.2.x line. It makes the world read more like a coherent climate system, while also improving stability and the first-run player experience.

### Major worldgen improvements
- Reworked the climate-band model and related biome policy so latitude structure reads more clearly and more naturally.
- Reduced scattered confetti biome placement and improved biome contiguity across the world.
- Strengthened regional biome identity so key biomes feel more intentional and less thin, noisy, or patchy.
- Improved high-value cases like badlands and Pale Garden so they form more convincingly as real places.
- Restored missing or underrepresented biome outcomes in the regions where players would expect to find them.
- Smoothed climate handoff behavior so transitions feel less arbitrary and less visibly synthetic.

### Player-facing improvements
- Refined the create-world and loading flow so starting a world feels more intentional and less abrupt.
- Fixed the excessively long first-time world creation/loading delay from the 1.2.x line, so first entry now feels much closer to normal vanilla startup.
- Improved first-entry behavior after world creation and after upgrading an existing save.
- Improved the first-run experience so the mod feels smoother and more finished from the moment you create or open a world.

### Existing-world compatibility
- Existing 1.2.x worlds keep their legacy worldgen policy when opened in 1.3.0.
- The upgraded-save crash caused by a missing `worldgen_policy` bootstrap path was fixed.

### Validation
- Structural audits passed.
- Invariant scan passed.
- Release integrity checks passed.
- Live upgrade and save smoke checks passed.

## Latitude 1.2.5 (MC 1.21.x)
- Broadened declared compatibility to cover MC 1.21.0–1.21.11.
- Two jars provided: one for 1.21.0–1.21.3, one for 1.21.10–1.21.11.
- Hardened fog mixins with require=0 for cross-version stability on the compat jar.
- No gameplay or worldgen changes from 1.2.4.

## Latitude 1.2.4 (MC 1.21.11)
- EW storm intensity ramp tightened (shader-friendly haze works with Sodium + Iris) for a stronger wall near EW borders.
- Warm-band cold-biome clamp prevents snow/ice leakage in Equator/Tropics/Arid bands.
- HUD/overlay ordering preserved so warnings stay readable under the haze.
