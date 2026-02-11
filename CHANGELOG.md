# Changelog

## Latitude 1.2.6-beta.1 (MC 1.21.11)
- First-load “Loading terrain…” message for new worlds (Latitude first-gen can take longer; subsequent loads faster).
- Band-scaling fixes for 1.21.11 compatibility.
- Note: spawn-wait overlay intentionally omitted on 1.21.11 (not needed).

## Latitude 1.2.5 (MC 1.21.x)
- Broadened declared compatibility to cover MC 1.21.0–1.21.11.
- Two jars provided: one for 1.21.0–1.21.3, one for 1.21.10–1.21.11.
- Hardened fog mixins with require=0 for cross-version stability on the compat jar.
- No gameplay or worldgen changes from 1.2.4.

## Latitude 1.2.4 (MC 1.21.1)
- EW storm intensity ramp tightened (shader-friendly haze works with Sodium + Iris) for a stronger wall near EW borders.
- Warm-band cold-biome clamp prevents snow/ice leakage in Equator/Tropics/Arid bands.
- HUD/overlay ordering preserved so warnings stay readable under the haze.
