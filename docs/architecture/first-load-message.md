# First world load message (client invariant)

## Invariant (must always hold)
- On **new world creation**, set `LatitudeClientState.firstWorldLoad = true` and `LatitudeClientState.firstWorldLoadStartMs = 0L` (gated by config).
- During **LevelLoading/Downloading Terrain** screen, render a 2-line message with fade-in:
  1. "Latitude is preparing your world for the first time."
  2. "Subsequent loads will be much faster."
- On **screen close**, clear the flag (`firstWorldLoad = false`, `firstWorldLoadStartMs = 0`).

## Authority files
- `src/main/java/com/example/globe/mixin/client/CreateWorldScreenMixin.java`
- `src/main/java/com/example/globe/mixin/client/DownloadingTerrainScreenFirstLoadMessageMixin.java`
- `src/main/java/com/example/globe/client/LatitudeClientState.java`
- `src/main/java/com/example/globe/client/LatitudeClientConfig.java`
- `src/main/resources/globe.mixins.json` (client list contains both mixins)

## User-facing text (exact)
- "Latitude is preparing your world for the first time."
- "Subsequent loads will be much faster."

## Config gate
- `LatitudeClientConfig.showFirstLoadMessage` (default true). If false, overlay is suppressed.
