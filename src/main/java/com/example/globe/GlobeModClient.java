package com.example.globe;

import java.util.ArrayList;
import java.util.List;

import com.example.globe.client.LatitudeConfig;
import com.example.globe.client.GlobeClientState;
import com.example.globe.client.CompassHud;
import com.example.globe.client.CompassHudConfig;
import com.example.globe.client.ClientKeybinds;
import com.example.globe.client.GlobeWarningOverlay;
import com.example.globe.client.PolarWindSoundInstance;
import com.example.globe.client.LatitudeClientState;
import com.example.globe.client.LatitudeHudStudioScreen;
import com.example.globe.client.EwStormWallRenderer;
import com.example.globe.dev.DevCaptureKeybind;
import com.example.globe.dev.client.SeamAuditClientBridge;
import com.example.globe.dev.client.audit.SeamAuditHarness;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

public class GlobeModClient implements ClientModInitializer {

    /**
     * Slice B (audit P1-2 / Lane 8): the Sodium E-W section-culling compat mixin
     * ({@code RenderSectionManagerVisibilityMixin}) targets Sodium's internal
     * {@code RenderSectionManager.isSectionVisible(III)Z}, which Sodium 0.9.0+mc26.2 removed; the injection
     * uses {@code require = 0} so a missing target degrades silently instead of crashing the client — but
     * "silently" meant NOTHING anywhere said the optimization was off (a future "why is E-W culling not
     * working" mystery). A mixin cannot log its own non-application, so this client-init reflection check
     * carries the warn: if Sodium is loaded and the target method is absent, say so once.
     */
    private static void warnIfSodiumCullHookInactive() {
        try {
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
                return;
            }
            Class<?> rsm = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager");
            rsm.getDeclaredMethod("isSectionVisible", int.class, int.class, int.class);
            // Method present -> the compat injection applied; the E-W culling optimization is active.
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            GlobeMod.LOGGER.warn(
                    "[Latitude] Sodium is installed but RenderSectionManager.isSectionVisible(III)Z is absent "
                            + "on this Sodium version -- Latitude's E-W section-culling render-distance "
                            + "optimization is INACTIVE (safely skipped; everything else unaffected). "
                            + "Expected on Sodium >= 0.9.0.");
        } catch (Throwable ignored) {
            // Reflection failure here must never affect client init; the check is purely informational.
        }
    }
    private static final int PROMENADE_PALM_LEAVES_OPAQUE_TINT = 0xFF7DB22E;
    private static final String[] PROMENADE_PALM_TINT_BLOCKS = {
            "promenade:palm_leaves",
            "promenade:snowy_palm_leaves",
            "promenade:palm_hanging_leaves",
            "promenade:palm_leaf_pile"
    };

    @Override
    public void onInitializeClient() {
        GlobeNet.registerPayloads();
        GlobeMod.LOGGER.info("Globe client init OK");
        if (GlobeClientState.DEBUG_EW_FOG) {
            GlobeMod.LOGGER.info("[Latitude] debugEwFog=true");
        }
        warnIfSodiumCullHookInactive();

        LatitudeConfig.get();
        ClientLifecycleEvents.CLIENT_STARTED.register(GlobeModClient::registerPromenadePalmTintCompat);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GlobeClientState.setGlobeWorld(false);
            GlobeClientState.setEvatorWorld(false); // B-6: never leak an evator gate across worlds.
            com.example.globe.util.LatitudeMath.setLatitudeZRadius(0);
            com.example.globe.util.LatitudeMath.setIntendedXRadius(0);
            // U-D world-switch hygiene: per-world caches (HUD strings, compass presence, dial-texture
            // presence) and any in-flight zone title must not leak across worlds.
            com.example.globe.client.CompassHud.onWorldSwitch();
            com.example.globe.client.ZoneEnterTitleOverlay.reset();
            com.example.globe.client.HemisphereTitleOverlay.reset();
            com.example.globe.client.HemispherePassageClientState.reset();
            com.example.globe.client.HemispherePassageClient.reset();
            com.example.globe.client.LatitudeWhisperOverlay.reset();
            com.example.globe.client.PolarWindSoundInstance.reset();
        });

        ClientPlayNetworking.registerGlobalReceiver(GlobeNet.GlobeStatePayload.ID, (payload, context) -> {
            if (payload.isGlobe()) {
                // Flip the bespoke loading flag as soon as the handshake packet arrives (network thread).
                LatitudeClientState.activateLatitudeLoading();
            } else if (LatitudeClientState.isLatitudeWorldLoading()) {
                LatitudeClientState.clearLatitudeLoadingState();
            }
            context.client().execute(() -> {
                GlobeClientState.setGlobeWorld(payload.isGlobe());
                // B-6: the authoritative evator bit -- the client B-5 prompt machine stands down when true.
                GlobeClientState.setEvatorWorld(payload.isGlobe() && payload.evatorActive());
                // Mercator: latitude (Z) radius differs from the X-sized border half; drive HUD/zone/pole off it.
                com.example.globe.util.LatitudeMath.setLatitudeZRadius(payload.isGlobe() ? payload.latitudeZRadius() : 0);
                // Intended X (longitude) radius: anchors ALL E/W-edge feature geometry (fog/prompt/re-arm/
                // banner/arrival) so a lerping/vandalized live border can't slide those lines. (TEST 89 removed
                // the EW dust particles, so they are no longer among the consumers.)
                com.example.globe.util.LatitudeMath.setIntendedXRadius(payload.isGlobe() ? payload.intendedXRadius() : 0);
                GlobeMod.LOGGER.info("S2C globe state: isGlobe={} latitudeZRadius={} intendedXRadius={} evatorActive={}",
                        payload.isGlobe(), payload.latitudeZRadius(), payload.intendedXRadius(), payload.evatorActive());
            });
        });

        // B-5 Hemisphere Passage arrival (S2C, per-crossing-player only). P1 STUB: log + record the arrival for
        // P2 to consume (arrival title + disarmed-in-band seed). No presentation here by design.
        ClientPlayNetworking.registerGlobalReceiver(GlobeNet.PassageArrivalPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                com.example.globe.client.HemispherePassageClientState.onArrival(payload.arrivalX());
                GlobeMod.LOGGER.info("S2C passage arrival: arrivalX={} (east={})",
                        payload.arrivalX(),
                        com.example.globe.client.HemispherePassageClientState.arrivedEast());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GlobeNet.OpenSpawnPickerPayload.ID, (payload, context) -> {
            // Legacy spawn picker is no longer part of the first-load flow; ignore any stale payloads.
            if (!payload.open()) {
                return;
            }

            context.client().execute(() ->
                    GlobeMod.LOGGER.info("Ignoring legacy open spawn picker payload"));
        });

        GlobeWarningOverlay.init();
        CompassHud.init();
        // Client-side `/latdev title` test command (previews the zone-enter title on demand). Registers only
        // outside dev and only for pre-release / -Dlatitude.devCommands builds; falls through to the server
        // /latdev tree for every other subcommand. See LatitudeClientDevCommands for the full rationale.
        com.example.globe.client.LatitudeClientDevCommands.registerIfEnabled();
        ClientTickEvents.END_CLIENT_TICK.register(GlobeModClient::polarCapClientTick);
        // B-5-P2 Hemisphere Passage: the approach/prompt + crossing-curtain state machine (flag-gated internally).
        ClientTickEvents.END_CLIENT_TICK.register(com.example.globe.client.HemispherePassageClient::clientTick);
        ClientKeybinds.init();
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            DevCaptureKeybind.init();
            SeamAuditClientBridge.init();
            SeamAuditHarness.init();
        }
        ClientTickEvents.END_CLIENT_TICK.register(GlobeModClient::clientKeybindTick);
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            com.example.globe.dev.AutoCreateWorldProbe.maybeRegister();
        }

        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(ctx -> {
            if (!GlobeClientState.DEBUG_EW_WALL) return;
            // Wall/overlay rendering happens in the HUD pass now to avoid POV seams.
            return;
        });
    }

    private static void registerPromenadePalmTintCompat(Minecraft client) {
        if (!FabricLoader.getInstance().isModLoaded("promenade")) {
            return;
        }

        List<Block> blocks = new ArrayList<>();
        for (String blockId : PROMENADE_PALM_TINT_BLOCKS) {
            Identifier id = Identifier.parse(blockId);
            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                blocks.add(BuiltInRegistries.BLOCK.getValue(id));
            }
        }

        if (blocks.isEmpty()) {
            GlobeMod.LOGGER.info("[Latitude] Promenade palm tint compat skipped; no palm leaf blocks found");
            return;
        }

        if (client.getBlockColors() == null) {
            GlobeMod.LOGGER.info("[Latitude] Promenade palm tint compat deferred; block colors not ready");
            return;
        }

        client.getBlockColors().register(
                List.of(BlockTintSources.constant(PROMENADE_PALM_LEAVES_OPAQUE_TINT)),
                blocks.toArray(Block[]::new)
        );
        GlobeMod.LOGGER.info("[Latitude] Promenade palm tint compat applied to {} block(s)", blocks.size());
    }

    private static void clientKeybindTick(Minecraft client) {
        while (ClientKeybinds.TOGGLE_COMPASS.consumeClick()) {
            var cfg = CompassHudConfig.get();
            cfg.enabled = !cfg.enabled;
            CompassHudConfig.saveCurrent();
        }

        while (ClientKeybinds.OPEN_SETTINGS.consumeClick()) {
            // U-B one-front-door: F9 opens the HUD Studio directly (the legacy LatitudeSettingsScreen was a
            // duplicated subset with different save semantics; its two unique fields moved to the Studio's
            // General tab).
            if (client.gui.screen() == null) {
                client.setScreenAndShow(new LatitudeHudStudioScreen(null));
            } else {
                client.setScreenAndShow(new LatitudeHudStudioScreen(client.gui.screen()));
            }
        }
    }

    private static void polarCapClientTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        // Clamp client-side view distance for EW storms (Sodium-proof fog wall).
        GlobeClientState.clampEwViewDistance(client);

        // Trust GlobeClientState (server-synced)
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }

        if (GlobeClientState.DEBUG_DISABLE_WARNINGS) {
            return;
        }

        var eval = GlobeClientState.evaluate(client);
        if (!eval.active()) {
            
            return;
        }

        // Polar WIND SOUND BED (CD F2/R2): a looping vanilla wind-rush that rises from a breath at 85 deg to
        // a howl near the pole, tracking the same 85->90 ramp as the snow/fog. Self-manages its lifecycle
        // (starts here, updates volume + stops itself in its own tick); the manager only (re)arms it. Armed
        // BEFORE the surface/pause gates below: the wind is audible from inside a shelter, so unlike the
        // player-anchored particle spawns it must NOT be silenced when sheltered -- it muffles its own volume
        // instead (see PolarWindSoundInstance). It is a single looping instance, not a per-tick spawn, so the
        // paused-backlog concern the isPaused guard addresses does not apply to it.
        PolarWindSoundInstance.clientTick(client);

        // B-3b anti-backlog HARD REQUIREMENT: while the game is paused the client tick keeps firing but the
        // particle engine does not step existing particles -- spawning here would pile them at the spawn
        // point to all animate in a burst on unpause. Paused => spawn NOTHING; resume is clean. Guards ALL
        // particle spawning below (ambient snow AND the EW storm), so no path can accrue a paused backlog.
        if (client.isPaused()) {
            return;
        }

        // Everything below is LOCAL, player-anchored particle spawning (the ambient polar snow + the EW border
        // storm). TEST 78: instead of the old BINARY surfaceOk gate (which killed all local particles the
        // instant a single block was over the player's head -- e.g. standing under Peetsa's open arch), the
        // per-tick budgets SCALE by the graded enclosure estimate exposure01: full storm out in the open,
        // proportional at a doorway, exactly zero in a sealed room (so nothing falls through a real roof).
        // The world-scale storm seen THROUGH a window -- greyed overcast + real vanilla snowfall on exterior
        // columns -- is driven by ClientLevelStormSkyMixin (world-space, vanilla-occluded by walls), not here.
        float exposure = eval.exposure01();

        // Throttle: spawn on every 4th tick (shared by ambient snow + EW storm). Fixed per-tick BUDGET on
        // each spawn tick -- never a "how many do I owe since last spawn" accumulator.
        boolean spawnTick = (client.level.getGameTime() & 3) == 0;

        // B-3b: AMBIENT polar snow + fog. Always-on for globe worlds in the polar approach band (85 deg+),
        // NOT gated by enableWarningParticles -- atmosphere, like the EW screen haze (which is also not
        // config-gated). Density is a FIXED per-tick budget from the 85->90 progress ramp (very heavy near
        // the pole). The matching FOG is rendered by PolarWhiteoutOverlayHud (a HUD screen fill reading
        // computePoleWhiteoutFactor) on the SAME 85->90 ramp; volumetric fog-renderer wiring is a B-4 decision.
        if (spawnTick) {
            double absLatDeg = com.example.globe.util.LatitudeMath.absLatDegExact(
                    client.level.getWorldBorder(), client.player.getZ());
            int snowCount = com.example.globe.core.PolarHazardWindow.snowCount(absLatDeg);
            // Perf-scaling (Peetsa): honor the LIVE vanilla Particles video setting so the pole storm
            // decreases in lock-step when a player turns particles down for performance. Read ONCE per
            // spawn-tick (cheap; re-read every tick so a mid-session settings change takes effect
            // immediately -- never cached long-term). Pure multiplicative scale of the FIXED per-tick
            // budget; introduces no state/accumulator, so the B-3b anti-backlog law is untouched.
            com.example.globe.core.ParticleDensity.Tier snowTier = polarSnowDensityTier(client);
            // B-4 item 5 evidence (permanently gated): confirm the ambient budget scales with latitude.
            // -Dlatitude.debugPolarSnow=true logs count vs |lat| every ~2 s. Verified: 87 deg -> 33,
            // 89 deg -> 64, 90 deg -> 80 with the old max (SNOW_MAX is 60 since TEST 78's volume fill;
            // at ALL tier expect 90 deg -> 60 base + the second pass); the counts were always correct -- the miss was
            // VISIBILITY (tiny flakes lost in the white fog), now carried by real vanilla snowfall (item 4).
            // Logs both the raw latitude budget and the tier-scaled budget (the REAL per-tick spawn count).
            if (Boolean.getBoolean("latitude.debugPolarSnow") && (client.level.getGameTime() % 40L) == 0L) {
                int scaledSnow = com.example.globe.core.ParticleDensity.scale(snowTier, snowCount);
                GlobeMod.LOGGER.info("[LAT][POLAR_SNOW] absLatDeg={} count={} tier={} scaled={}",
                        absLatDeg, snowCount, snowTier, scaledSnow);
            }
            if (snowCount > 0) {
                spawnAmbientPolarSnow(client, snowCount, absLatDeg, snowTier, exposure);
            }
        }

        // TEST 89: the EW border DUST/sand storm particles are REMOVED entirely (Peetsa: "remove the dust
        // particles altogether"). The edge's presentation is now the depth fog + the single white advisory
        // banner -- no player-anchored EW particle spawn at all. The ambient POLAR snow above is untouched.
        // (enableWarningParticles used to gate ONLY these EW particles -- it never gated the polar snow -- so
        // with them gone the toggle no longer governs any particle path; the config field is left in place to
        // preserve save/HUD compatibility. See report.)
    }


    private static void spawnAmbientPolarSnow(Minecraft client, int count, double absLatDeg,
                                              com.example.globe.core.ParticleDensity.Tier tier, float exposure) {
        // Perf-scaling: reduce the FIXED per-tick budget by the vanilla Particles setting BEFORE any
        // flake spawns. Applied ONCE here; the dense second pass (extra = blizz * count) derives from
        // this now-scaled count, so it scales proportionally without a second, independent reduction.
        // Pure function of (tier, count) -- no state, no accumulator; the anti-backlog law holds. If the
        // scale collapses count to 0, both loops simply run 0 iterations (no special-casing needed).
        count = com.example.globe.core.ParticleDensity.scale(tier, count);
        // TEST 78: then scale by the graded enclosure estimate -- full out in the open, 0 in a sealed room,
        // proportional at a doorway. Also a pure function (no accumulator); composes with the tier scale.
        count = com.example.globe.core.PolarExposure.particleBudget(count, exposure);

        RandomSource random = client.player.getRandom();
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        // 0 at/below 87 deg (gentle approach flurries), 1 at the pole (driven blizzard).
        double blizz = com.example.globe.core.PolarHazardWindow.blizzardDrive(absLatDeg);

        // Steady wind direction for this spawn burst (sign flips by which side of center the player is on,
        // like the EW storm) so the snowfall has a coherent slant instead of drifting symmetrically. The
        // wind MAGNITUDE ramps with the blizzard drive (pure, tested curves in PolarHazardWindow): a gentle
        // slant through the 85-87 approach, a hard sideways drive at the pole.
        double windMag = com.example.globe.core.PolarHazardWindow.blizzardWindMagnitude(absLatDeg);
        double windX = (client.player.getX() >= 0.0 ? -1.0 : 1.0) * windMag;
        double fall = com.example.globe.core.PolarHazardWindow.blizzardFallSpeed(absLatDeg);

        for (int i = 0; i < count; i++) {
            double ox = (random.nextDouble() - 0.5) * SNOW_ENVELOPE;
            // TEST 78: fill a real vertical VOLUME (py-2..py+14) instead of a thin overhead band. Triangular
            // pick ((r1+r2)/2, peak 0.5) weights density toward the middle of the column (~py+6, eye-to-above)
            // so the budget lands where it reads without wasting flakes at the extremes -- the snow fills the
            // air in every direction rather than hanging as a single diagonal sheet.
            double tv = (random.nextDouble() + random.nextDouble()) * 0.5;
            double oy = SNOW_VOLUME_LOW + tv * (SNOW_VOLUME_HIGH - SNOW_VOLUME_LOW);
            double oz = (random.nextDouble() - 0.5) * SNOW_ENVELOPE;

            // Wind-blown drift: steady horizontal wind (ramped) + per-flake jitter -> visible sideways streaking.
            double vx = windX + (random.nextDouble() - 0.5) * 0.06;
            double vy = -fall - random.nextDouble() * 0.05;
            double vz = (random.nextDouble() - 0.5) * 0.10;

            client.particleEngine.createParticle(ParticleTypes.SNOWFLAKE, px + ox, py + oy, pz + oz, vx, vy, vz);
        }

        // Dense SECOND pass inside the hazard band: a fixed extra budget (blizz * count, deterministic per
        // tick -- NOT an accumulator) of low, hard-driven flakes streaking through the near-ground band
        // (py-1..py+6), so the pole reads as a whiteout gale at eye/ground level rather than heavier vertical
        // snowfall. Zero below 87 deg (blizz == 0). Rebalanced from a thin py+1..py+4 band (which fed the old
        // single-sheet look) into a taller near-eye slab that composites with the main-pass volume.
        int extra = (int) Math.round(blizz * count);
        for (int i = 0; i < extra; i++) {
            double ox = (random.nextDouble() - 0.5) * SNOW_ENVELOPE;
            double oy = -1.0 + random.nextDouble() * 7.0;          // near-ground/eye slab: py-1..py+6
            double oz = (random.nextDouble() - 0.5) * SNOW_ENVELOPE;
            // Harder sideways drive, flatter trajectory -> reads as wind-whipped ground blizzard.
            double vx = windX * SNOW_SECOND_PASS_WIND_MULT + (random.nextDouble() - 0.5) * 0.08;
            double vy = -fall * 0.5 - random.nextDouble() * 0.03;
            double vz = (random.nextDouble() - 0.5) * 0.12;
            client.particleEngine.createParticle(ParticleTypes.SNOWFLAKE, px + ox, py + oy, pz + oz, vx, vy, vz);
        }
    }

    // B-4 storm-snow: widened envelope (10->16) + a steady horizontal wind drift so the flakes streak
    // sideways and READ as a blizzard, not gentle flurries (Peetsa saw no increase near the pole). The
    // per-tick BUDGET (count) and the caller's isPaused/spawn-tick anti-backlog guards are UNCHANGED --
    // this only changes how each spawned flake looks/moves, never how many spawn or when.
    private static final double SNOW_ENVELOPE = 16.0;

    // TEST 78 storm VOLUME: the main pass used to spawn in a thin ~6-block band ABOVE the head (py+2..py+8),
    // which read as a single diagonal SHEET (Peetsa: "only one thin layer that blows diagonally"). Now the main
    // pass fills a real vertical VOLUME from py-2 to py+14 (16 blocks tall) around the camera, weighted denser
    // near eye level by a triangular pick, so the snow visibly fills the AIR in every direction instead of
    // hanging as a curtain overhead. The dense low SECOND pass fills the near-ground band (py-1..py+6).
    private static final double SNOW_VOLUME_LOW = -2.0;   // bottom of the main-pass volume, relative to feet
    private static final double SNOW_VOLUME_HIGH = 14.0;  // top of the main-pass volume, relative to feet

    // B-4 round 3 item 2 -- BLIZZARD. Beyond the base gentle-flurry velocities, a blizzard drive (0 at the
    // 87 deg hazard onset, 1 at the pole) ramps the sideways wind and the fall speed toward a driven gale and
    // gates a dense low SECOND pass. All still a FIXED per-tick function of latitude -- the anti-backlog law
    // (fixed budget, isPaused=>nothing) is untouched; only the look/motion and a fixed extra count change.
    //
    // TEST 77 round 2: the wind/fall MAGNITUDE curves moved into the pure, tested core.PolarHazardWindow
    // (blizzardWindMagnitude / blizzardFallSpeed) and were cranked hard -- SnowflakeParticle damps horizontal
    // velocity ~5%/tick and pins vertical to a ~0.081/tick terminal, so the old ceilings decayed to a gentle
    // straight-down drift within a second (Peetsa: "slow, falls down, not sideways"). The second (low, hard-
    // driven) pass multiplies that already-larger wind so the pole reads as a wind-whipped ground blizzard.
    private static final double SNOW_SECOND_PASS_WIND_MULT = 1.9;  // low pass streaks even harder sideways

    // Perf-scaling glue (untested -- a trivial 1:1 mapping, not math; the scaling math is in the pure,
    // tested core.ParticleDensity). Reads the LIVE vanilla Particles video setting and maps it onto our
    // MC-neutral Tier. Vanilla's ParticleStatus has exactly THREE tiers (ALL/DECREASED/MINIMAL) -- there
    // is no "off" -- so MINIMAL is our lowest floor (a thin, still-snowy blizzard). Read fresh each call
    // so a mid-session settings change is honored immediately; never cached across ticks.
    private static com.example.globe.core.ParticleDensity.Tier polarSnowDensityTier(Minecraft client) {
        net.minecraft.server.level.ParticleStatus status = client.options.particles().get();
        return switch (status) {
            case ALL -> com.example.globe.core.ParticleDensity.Tier.FULL;
            case DECREASED -> com.example.globe.core.ParticleDensity.Tier.DECREASED;
            case MINIMAL -> com.example.globe.core.ParticleDensity.Tier.MINIMAL;
        };
    }

}
