# Client Render Subsystem Map (v1.2.5+1.21.11)

## Fog Geometry (start/end)
- Authority: `client.FogRendererMixin`
- Injection host: `net.minecraft.client.render.FogRenderer#applyFog` (Args modify)

## Fog Color Tint
- Authority: _none_ (no tint overrides in v1.2.5 baseline)
- Injection host: N/A

## Sky Tint
- Authority: _none_ (uses vanilla sky color pipeline)
- Injection host: N/A

## EW Storm Wall Overlay
- Authority: `com.example.globe.client.EwHazeOverlay`
- Injection host: `com.example.globe.client.GlobeClientState` (intensity) + HUD tail render hook

## World Border Hook
- Authority: `com.example.globe.client.GlobeClientState` (distance-to-border + fog end compute)
- Injection host: `client.FogRendererMixin` (reads camera-relative X from FogRenderer)

## HUD Layering
- Authority: `client.InGameHudMixin`
- Injection host: `net.minecraft.client.gui.hud.InGameHud#render` (TAIL)

## Sodium Compat Hook(s)
- Authority: `client.FogRendererMixin` (Sodium presence detection, no Sodium-specific calls)
- Injection host: `net.minecraft.client.render.FogRenderer#applyFog`
