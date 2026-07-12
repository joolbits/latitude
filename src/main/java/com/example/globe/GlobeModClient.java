package com.example.globe;

import java.util.ArrayList;
import java.util.List;

import com.example.globe.client.LatitudeConfig;
import com.example.globe.client.GlobeClientState;
import com.example.globe.client.CompassHud;
import com.example.globe.client.CompassHudConfig;
import com.example.globe.client.ClientKeybinds;
import com.example.globe.client.GlobeWarningOverlay;
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
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

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
            com.example.globe.util.LatitudeMath.setLatitudeZRadius(0);
            // U-D world-switch hygiene: per-world caches (HUD strings, compass presence, dial-texture
            // presence) and any in-flight zone title must not leak across worlds.
            com.example.globe.client.CompassHud.onWorldSwitch();
            com.example.globe.client.ZoneEnterTitleOverlay.reset();
            com.example.globe.client.HemisphereTitleOverlay.reset();
            com.example.globe.client.LatitudeWhisperOverlay.reset();
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
                // Mercator: latitude (Z) radius differs from the X-sized border half; drive HUD/zone/pole off it.
                com.example.globe.util.LatitudeMath.setLatitudeZRadius(payload.isGlobe() ? payload.latitudeZRadius() : 0);
                GlobeMod.LOGGER.info("S2C globe state: isGlobe={} latitudeZRadius={}", payload.isGlobe(), payload.latitudeZRadius());
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

        if (!eval.surfaceOk()) {
            return;
        }

        // B-3b anti-backlog HARD REQUIREMENT: while the game is paused the client tick keeps firing but the
        // particle engine does not step existing particles -- spawning here would pile them at the spawn
        // point to all animate in a burst on unpause. Paused => spawn NOTHING; resume is clean. Guards ALL
        // particle spawning below (ambient snow AND the EW storm), so no path can accrue a paused backlog.
        if (client.isPaused()) {
            return;
        }

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
            // 89 deg -> 64, 90 deg -> 80 with the old max; the counts were always correct -- the miss was
            // VISIBILITY (tiny flakes lost in the white fog), now carried by real vanilla snowfall (item 4).
            // Logs both the raw latitude budget and the tier-scaled budget (the REAL per-tick spawn count).
            if (Boolean.getBoolean("latitude.debugPolarSnow") && (client.level.getGameTime() % 40L) == 0L) {
                int scaledSnow = com.example.globe.core.ParticleDensity.scale(snowTier, snowCount);
                GlobeMod.LOGGER.info("[LAT][POLAR_SNOW] absLatDeg={} count={} tier={} scaled={}",
                        absLatDeg, snowCount, snowTier, scaledSnow);
            }
            if (snowCount > 0) {
                spawnAmbientPolarSnow(client, snowCount, absLatDeg, snowTier);
            }
        }

        // WARNING-intensity particles (the EW border storm) stay behind enableWarningParticles -- its
        // meaning is unchanged (it governs only the warning particles it already governed).
        if (!LatitudeConfig.enableWarningParticles) {
            return;
        }

        GlobeClientState.EwStormStage ewStage = GlobeClientState.computeEwStormStage(client.level, client.player);
        if (ewStage != GlobeClientState.EwStormStage.NONE && spawnTick) {
            ewStormClientTick(client, ewStage);
        }
    }

    // B-4 storm-snow: widened envelope (10->16) + a steady horizontal wind drift so the flakes streak
    // sideways and READ as a blizzard, not gentle flurries (Peetsa saw no increase near the pole). The
    // per-tick BUDGET (count) and the caller's isPaused/spawn-tick anti-backlog guards are UNCHANGED --
    // this only changes how each spawned flake looks/moves, never how many spawn or when.
    private static final double SNOW_ENVELOPE = 16.0;

    // B-4 round 3 item 2 -- BLIZZARD. Beyond the base gentle-flurry velocities, a blizzard drive (0 at the
    // 87 deg hazard onset, 1 at the pole) ramps the sideways wind and the fall speed toward a driven gale and
    // gates a dense low SECOND pass. All still a FIXED per-tick function of latitude -- the anti-backlog law
    // (fixed budget, isPaused=>nothing) is untouched; only the look/motion and a fixed extra count change.
    private static final double SNOW_WIND_BASE = 0.09;     // gentle-flurry sideways wind at/below 87 deg
    private static final double SNOW_WIND_GALE = 0.34;     // added sideways wind at the pole (driven blizzard)
    private static final double SNOW_FALL_BASE = 0.04;     // gentle-flurry base fall speed
    private static final double SNOW_FALL_GALE = 0.11;     // added fall speed at the pole (fast, driven flakes)

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

    private static void spawnAmbientPolarSnow(Minecraft client, int count, double absLatDeg,
                                              com.example.globe.core.ParticleDensity.Tier tier) {
        // Perf-scaling: reduce the FIXED per-tick budget by the vanilla Particles setting BEFORE any
        // flake spawns. Applied ONCE here; the dense second pass (extra = blizz * count) derives from
        // this now-scaled count, so it scales proportionally without a second, independent reduction.
        // Pure function of (tier, count) -- no state, no accumulator; the anti-backlog law holds. If the
        // scale collapses count to 0, both loops simply run 0 iterations (no special-casing needed).
        count = com.example.globe.core.ParticleDensity.scale(tier, count);

        RandomSource random = client.player.getRandom();
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        // 0 at/below 87 deg (gentle approach flurries), 1 at the pole (driven blizzard).
        double blizz = com.example.globe.core.PolarHazardWindow.blizzardDrive(absLatDeg);

        // Steady wind direction for this spawn burst (sign flips by which side of center the player is on,
        // like the EW storm) so the snowfall has a coherent slant instead of drifting symmetrically. The
        // wind MAGNITUDE ramps with the blizzard drive: a gentle slant near 87 deg, a hard sideways drive
        // at the pole.
        double windMag = SNOW_WIND_BASE + SNOW_WIND_GALE * blizz;
        double windX = (client.player.getX() >= 0.0 ? -1.0 : 1.0) * windMag;
        double fall = SNOW_FALL_BASE + SNOW_FALL_GALE * blizz;

        for (int i = 0; i < count; i++) {
            double ox = (random.nextDouble() - 0.5) * SNOW_ENVELOPE;
            double oy = random.nextDouble() * 6.0;
            double oz = (random.nextDouble() - 0.5) * SNOW_ENVELOPE;

            // Wind-blown drift: steady horizontal wind (ramped) + per-flake jitter -> visible sideways streaking.
            double vx = windX + (random.nextDouble() - 0.5) * 0.06;
            double vy = -fall - random.nextDouble() * 0.05;
            double vz = (random.nextDouble() - 0.5) * 0.10;

            client.particleEngine.createParticle(ParticleTypes.SNOWFLAKE, px + ox, py + 2.0 + oy, pz + oz, vx, vy, vz);
        }

        // Dense SECOND pass inside the hazard band: a fixed extra budget (blizz * count, deterministic per
        // tick -- NOT an accumulator) of low, hard-driven flakes streaking near eye level, so the pole reads
        // as a whiteout gale rather than heavier vertical snowfall. Zero below 87 deg (blizz == 0).
        int extra = (int) Math.round(blizz * count);
        for (int i = 0; i < extra; i++) {
            double ox = (random.nextDouble() - 0.5) * SNOW_ENVELOPE;
            double oy = random.nextDouble() * 3.0;                 // low, near eye level
            double oz = (random.nextDouble() - 0.5) * SNOW_ENVELOPE;
            // Harder sideways drive, flatter trajectory -> reads as wind-whipped ground blizzard.
            double vx = windX * 1.6 + (random.nextDouble() - 0.5) * 0.08;
            double vy = -fall * 0.5 - random.nextDouble() * 0.03;
            double vz = (random.nextDouble() - 0.5) * 0.12;
            client.particleEngine.createParticle(ParticleTypes.SNOWFLAKE, px + ox, py + 1.0 + oy, pz + oz, vx, vy, vz);
        }
    }

    private static void ewStormClientTick(Minecraft client, GlobeClientState.EwStormStage stage) {
        int base = switch (stage) {
            case LEVEL_1 -> 6;
            case LEVEL_2 -> 20;
            default -> 0;
        };
        if (base <= 0) {
            return;
        }

        RandomSource random = client.player.getRandom();
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        double vx = client.player.getX() >= 0.0 ? -0.10 : 0.10;

        int mainCount = base;
        int hazeCount = Math.max(1, base / 3);

        // Climate-aware E/W storm (TEST 1 E1): in the cold bands (subpolar/polar) the border storm is a SNOW
        // blizzard — snowflakes + pale haze building toward whiteout — instead of a desert sandstorm.
        var border = client.level.getWorldBorder();
        double absDeg = Math.abs(com.example.globe.util.LatitudeMath.degreesFromZ(border, pz));
        com.example.globe.util.LatitudeBands.Band band =
                com.example.globe.util.LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
        boolean cold = band == com.example.globe.util.LatitudeBands.Band.SUBPOLAR
                || band == com.example.globe.util.LatitudeBands.Band.POLAR;

        if (cold) {
            spawnCloudRing(client, ParticleTypes.SNOWFLAKE, mainCount, random, px, py, pz, vx);
        } else {
            BlockParticleOption sand = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
            spawnCloudRing(client, sand, mainCount, random, px, py, pz, vx);
        }
        spawnCloudRing(client, ParticleTypes.CLOUD, hazeCount, random, px, py, pz, vx * 0.6);
    }

    private static void spawnCloudRing(Minecraft client, ParticleOptions particle, int count, RandomSource random,
                                       double px, double py, double pz, double vx) {
        for (int i = 0; i < count; i++) {
            double ox = (random.nextDouble() - 0.5) * 16.0;
            double oy = 1.0 + random.nextDouble() * 6.0;
            double oz = (random.nextDouble() - 0.5) * 16.0;
            client.particleEngine.createParticle(particle, px + ox, py + oy, pz + oz, vx, 0.01, 0.0);
        }
    }

    private static boolean isWarningParticleActive(Minecraft client) {
        if (client.player == null || client.level == null) {
            return false;
        }

        GlobeClientState.PolarStage polarStage = GlobeClientState.computePolarStage(client.level, client.player);
        GlobeClientState.EwStormStage ewStage = GlobeClientState.computeEwStormStage(client.level, client.player);
        return polarStage != GlobeClientState.PolarStage.NONE || ewStage != GlobeClientState.EwStormStage.NONE;
    }
}
