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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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
            com.example.globe.util.LatitudeMath.setIntendedXRadius(0);
            // U-D world-switch hygiene: per-world caches (HUD strings, compass presence, dial-texture
            // presence) and any in-flight zone title must not leak across worlds.
            com.example.globe.client.CompassHud.onWorldSwitch();
            com.example.globe.client.ZoneEnterTitleOverlay.reset();
            com.example.globe.client.HemisphereTitleOverlay.reset();
            com.example.globe.client.HemispherePassageClientState.reset();
            com.example.globe.client.HemispherePassageClient.reset();
            com.example.globe.client.LatitudeWhisperOverlay.reset();
            com.example.globe.client.PolarColdClient.reset();
            com.example.globe.client.PolarCuesClient.reset();
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
                // Mercator: latitude (Z) radius differs from the X-sized border half; drive HUD/zone/pole off it.
                com.example.globe.util.LatitudeMath.setLatitudeZRadius(payload.isGlobe() ? payload.latitudeZRadius() : 0);
                // Intended X (longitude) radius: anchors ALL E/W-edge feature geometry (fog/prompt/re-arm/
                // banner/arrival) so a lerping/vandalized live border can't slide those lines. (TEST 89 removed
                // the EW dust particles, so they are no longer among the consumers.)
                com.example.globe.util.LatitudeMath.setIntendedXRadius(payload.isGlobe() ? payload.intendedXRadius() : 0);
                GlobeMod.LOGGER.info("S2C globe state: isGlobe={} latitudeZRadius={} intendedXRadius={}",
                        payload.isGlobe(), payload.latitudeZRadius(), payload.intendedXRadius());
            });
        });

        // B-5/B-7 Hemisphere Passage arrival (S2C, per-crossing-player only). Records the axis + arrival (X,Z);
        // HemispherePassageClient consumes it on the next curtain tick to seed the RIGHT arm and fire the right
        // arrival title (E/W hemisphere, or "Beyond the North/South Pole"). B-7 P2 routes both axes here.
        ClientPlayNetworking.registerGlobalReceiver(GlobeNet.PassageArrivalPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                com.example.globe.client.HemispherePassageClientState.onArrival(
                        payload.axis(), payload.arrivalX(), payload.arrivalZ());
                GlobeMod.LOGGER.info("S2C passage arrival: axis={} arrivalX={} arrivalZ={}",
                        payload.axis(), payload.arrivalX(), payload.arrivalZ());
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
        // S10b (owner decision 2026-07-16, TEST 99): the striped forcefield plane is RETIRED -- "get rid of
        // the appearance of this diagonal wall... that's how 90 should feel." The near-total whiteout of the
        // fog law v2 (PolarFogLaw) IS the wall's appearance now; the clamp + chime + pack-ice actionbar +
        // frost particles remain as the touch feedback. PoleWallRenderer.register() is intentionally NOT
        // called (the class is kept dead-gated for a cheap revival if the owner ever reverses again).
        // S13(a) AURORA BOREALIS: the polar-night sky curtains (self-gated on SOLAR_TILT_V2_ENABLED +
        // AURORA_ENABLED, so byte-identical off). Registers its own COLLECT_SUBMITS sky-geometry hook.
        com.example.globe.client.AuroraRenderer.register();
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

        // B-7 cold cues, after the isPaused guard so the frost inherits the B-3b anti-backlog rule.
        // S6: the frozen-wounds whisper one-shot -- GLOBAL (design item 8; presents the un-gated heal lock;
        // the tint itself is server-driven via ticksFrozen, no client work). S2: sparse frost along the pole
        // wall plane when pressed against the clamp line -- gates ITSELF on POLE_PASSAGE_V2_ENABLED (sweep
        // INFO 2026-07-14: the frost presents the flag-gated wall, so flag-off it must not exist).
        com.example.globe.client.PolarCuesClient.frozenWoundsWhisperTick(client);
        com.example.globe.client.PolarCuesClient.poleClampFrostTick(client, exposure);

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
            // S13(a) -> S21(b) SNOW SPARKLE (GLINT v4): calm-cold snowfield glints in the 75-82 band, BELOW the
            // snowfall -- the storm's absence made visible. The glint cross-fades OUT across 80->82 exactly as the
            // ambient snow above crossfades IN, so the two never compete. Same spawn-tick cadence +
            // Particles/enclosure/reduce-snow scaling as the ambient snow; a tiny CLUSTER budget. Pure gate/budget
            // law in core.SnowSparkleLaw; also gated OFF the moment the blizzard builds (calm-weather jewelry).
            spawnSnowSparkle(client, absLatDeg, snowTier, exposure);
        }

        // TEST 89: the EW border DUST/sand storm particles are REMOVED entirely (Peetsa: "remove the dust
        // particles altogether"). The edge's presentation is now the depth fog + the single white advisory
        // banner -- no player-anchored EW particle spawn at all. The ambient POLAR snow above is untouched.
        // (enableWarningParticles used to gate ONLY these EW particles -- it never gated the polar snow -- so
        // with them gone the toggle no longer governs any particle path; the config field is left in place to
        // preserve save/HUD compatibility. See report.)
    }


    // S16(a)(ii) PER-POSITION COVER GATE (owner, TEST 106: "sideways snow penetrating indoors"). The per-tick
    // budget already scales with the PLAYER's graded enclosure (exposure01), but that is ONE value for the whole
    // spawn cloud around the player -- individual flake POSITIONS were cover-blind, so through a window / at a
    // wall (partial exposure) flakes still spawned on the INDOOR side. This gates each candidate POSITION: a
    // flake spawns only where its own column is sky-open at that height -- spawnY at/above the column's
    // MOTION_BLOCKING heightmap top (nothing solid -- roof or wall -- above it in that column). Cost: ONE
    // heightmap read per candidate flake (O(1); the loaded chunk keeps the heightmap live -- no world raycast).
    // Applied to the ambient MAIN + SECOND + DEEP passes. Outdoors the heightmap top ~ the ground, so the
    // open-air storm is unchanged apart from the sub-terrain low tail (flakes that would spawn INSIDE the
    // ground, which vanilla would not render anyway -- correctly dropped). The SPARKLE pass is exempt by
    // construction (it spawns 0.05-0.3 blocks ABOVE the sampled surface heightmap top -- the v3 near-ground
    // numbers -- so it can never sit under a roof; verified, no gate needed there).
    private static boolean flakeSkyOpen(net.minecraft.world.level.Level level, double x, double y, double z) {
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                (int) Math.floor(x), (int) Math.floor(z));
        return y >= (double) top;
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
        // HUD Studio round 10 item (i): the "Reduce Polar Snow Particles" accessibility comfort option scales the
        // FINAL per-tick budget down hard when the player opts in. A SINGLE pure multiply of the already-scaled
        // `count`; the second/deep passes and the gust surge all derive from `count`, so this one read reduces the
        // WHOLE storm (ambient + deep + gust) without touching any spawn curve. Default OFF => *1.0 => byte-identical.
        if (com.example.globe.client.LatitudeConfig.reducePolarSnowParticles) {
            count = (int) Math.round(count * REDUCE_POLAR_SNOW_FACTOR);
        }

        RandomSource random = client.player.getRandom();
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        // S10c WIND AS GUSTS (TEST 99): a slow squared-sine surge (period ~14 s, PolarFogLaw.gustFactor)
        // multiplies the spawn COUNT here -- the second/deep passes derive from `count`, so one multiply
        // gusts the whole storm -- and the DOWNWARD fall speed below. Strength scales with latitude depth
        // (0 at 82 -> full at 89; peak factor 1.5). NEVER the sideways wind (standing owner veto): windX is
        // untouched. A pure function of the wall clock + latitude -- deterministic per tick, no accumulator,
        // so the B-3b anti-backlog law holds (isPaused already prevents this method from running at all).
        double gust = com.example.globe.core.PolarFogLaw.gustFactor(System.currentTimeMillis(), absLatDeg);
        count = (int) Math.round(count * gust);

        // 0 at/below 87 deg (gentle approach flurries), 1 at the pole (driven blizzard).
        double blizz = com.example.globe.core.PolarHazardWindow.blizzardDrive(absLatDeg);
        // B-7 P3 DEEP-BLIZZARD ramp (owner order, TEST 97): 0 at/below DEEP_BLIZZARD_START_DEG (87), 1 at/past
        // DEEP_BLIZZARD_FULL_DEG (89) -- steeper than blizzardDrive so the whiteout is FULL by 89, not 90.
        double deepT = Math.min(1.0, Math.max(0.0,
                (absLatDeg - DEEP_BLIZZARD_START_DEG) / (DEEP_BLIZZARD_FULL_DEG - DEEP_BLIZZARD_START_DEG)));
        // S10c "keeps deepening toward the line": a second ramp over 89 -> 90 so the deep tier does NOT
        // plateau at 89 -- density and fall keep climbing right to the pole line.
        double finalT = Math.min(1.0, Math.max(0.0, absLatDeg - DEEP_BLIZZARD_FULL_DEG));
        // The deep band's faster DOWNWARD fall (owner: "greater falling speed"; downward, not sideways -- the
        // wind terms are untouched). 1.0 below 87 -> DEEP_BLIZZARD_FALL_MULT (1.75) at 89 -> keeps climbing to
        // DEEP_BLIZZARD_FALL_MULT_POLE (2.1) at the line (S10c), times the gust surge.
        double fallBoost = (1.0 + deepT * (DEEP_BLIZZARD_FALL_MULT - 1.0)
                + finalT * (DEEP_BLIZZARD_FALL_MULT_POLE - DEEP_BLIZZARD_FALL_MULT)) * gust;

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
            // vy carries the P3 deep-band fallBoost (1.0 below 87 -> byte-identical there).
            // S16(a)(ii): skip covered positions -- no flake spawns indoors / under a roof (near a window/wall).
            if (!flakeSkyOpen(client.level, px + ox, py + oy, pz + oz)) {
                continue;
            }

            double vx = windX + (random.nextDouble() - 0.5) * 0.06;
            double vy = -fall * fallBoost - random.nextDouble() * 0.05;
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
            // S16(a)(ii): cover gate -- ground-slab flakes must not blow indoors through a wall/window.
            if (!flakeSkyOpen(client.level, px + ox, py + oy, pz + oz)) {
                continue;
            }
            // Harder sideways drive, flatter trajectory -> reads as wind-whipped ground blizzard.
            double vx = windX * SNOW_SECOND_PASS_WIND_MULT + (random.nextDouble() - 0.5) * 0.08;
            double vy = -fall * 0.5 - random.nextDouble() * 0.03;
            double vz = (random.nextDouble() - 0.5) * 0.12;
            client.particleEngine.createParticle(ParticleTypes.SNOWFLAKE, px + ox, py + oy, pz + oz, vx, vy, vz);
        }

        // B-7 P3 DEEP-BLIZZARD THIRD pass (owner, TEST 97: "not enough snow. I want more of a whiteout, a VERY
        // heavy snowfall, like 10x this much, with greater falling speed"). A NEW deep tier layered ON TOP of
        // the untouched gentle band: zero at/below 87 deg (the 82-87 approach is byte-identical), ramping to a
        // budget of DEEP_BLIZZARD_EXTRA_MULT x today's TOTAL (main + second pass) by DEEP_BLIZZARD_FULL_DEG
        // (89) -- so the whole spawn is ~10x the pre-P3 density at 89+. Derived from the already tier+exposure
        // scaled `count`, so the vanilla Particles setting and the enclosure gate scale it identically (and
        // isPaused => this method is never called -- the B-3b anti-backlog law holds; still a FIXED per-tick
        // budget, never an accumulator). DEEP_BLIZZARD_HARD_CAP bounds the worst case (documented below).
        // Fills the SAME main-pass volume with the boosted DOWNWARD fall -- a wall of falling snow, not more
        // sideways streaking (the owner rejected sideways drift as the answer before).
        // S10c: the (1 + kick*finalT) term keeps the density CLIMBING past 89 to the line (+50% budget by 90)
        // instead of plateauing at the 89 full-ramp -- "denser + faster at 89+ than 87", all the way in.
        int deepExtra = (int) Math.min(DEEP_BLIZZARD_HARD_CAP,
                Math.round(deepT * DEEP_BLIZZARD_EXTRA_MULT
                        * (1.0 + DEEP_BLIZZARD_FINAL_DENSITY_KICK * finalT) * count * (1.0 + blizz)));
        for (int i = 0; i < deepExtra; i++) {
            double ox = (random.nextDouble() - 0.5) * SNOW_ENVELOPE;
            double tv = (random.nextDouble() + random.nextDouble()) * 0.5;
            double oy = SNOW_VOLUME_LOW + tv * (SNOW_VOLUME_HIGH - SNOW_VOLUME_LOW);
            double oz = (random.nextDouble() - 0.5) * SNOW_ENVELOPE;
            // S16(a)(ii): cover gate on the deep-blizzard tier too -- the heaviest pass must not penetrate indoors.
            if (!flakeSkyOpen(client.level, px + ox, py + oy, pz + oz)) {
                continue;
            }
            double vx = windX + (random.nextDouble() - 0.5) * 0.06;
            double vy = -fall * fallBoost - random.nextDouble() * 0.08; // the deep tier falls HARD
            double vz = (random.nextDouble() - 0.5) * 0.10;
            client.particleEngine.createParticle(ParticleTypes.SNOWFLAKE, px + ox, py + oy, pz + oz, vx, vy, vz);
        }
    }

    // ---- S13(a) SNOW SPARKLE (owner order, TEST 103 flight): calm-cold snowfield glints. ----
    // Peak per-spawn-tick budget BEFORE the shared Particles/enclosure/reduce-snow scaling. GLINT v5 (TEST 113)
    // halved the peak to 2 alongside the WAX_OFF -> FIREWORK swap and the twin retirement: at the shared
    // every-4th-tick cadence (~5 spawn-ticks/s) peak 2 x single-particle clusters lands as ~10 bright white
    // sparks/second at full band strength on ALL particles + open sky (vs the old ~40 dim lilac particles/s),
    // tapering with the band ramp + the snowfall window -- FIREWORK carries per-spark brightness, so fewer
    // sparks read cleaner, not dimmer. P4 dial (the pure ramp/window in SnowSparkleLaw handles the rest).
    private static final int SPARKLE_PEAK_BUDGET = com.example.globe.core.SnowSparkleLaw.DEFAULT_PEAK_BUDGET;
    /** Horizontal radius (blocks) around the player over which glints scatter on the nearby snow surface. */
    private static final double SPARKLE_RADIUS = 10.0;
    // S17(c)(ii) SPARKLE v3 NEAR-GROUND (owner, TEST 107: "so you can tell it's the snow, not like it's
    // floating in the air"). S14(d) had lifted the glint 0.5-1.5 blocks INTO the air to escape the old FIREWORK
    // "raindrop" look; the owner then read that floating glint as air-motes, not snow-glint. v3 drops it back
    // DOWN onto the snow (0.05-0.3 blocks above the sampled surface -- hugging the ground, never clipped INTO
    // it) so it unambiguously reads as THE SNOW glinting.
    /** Min/max height (blocks) ABOVE the sampled snow surface a glint spawns -- hugging the ground so it reads as
     *  the snow itself catching the light, not a mote hanging in the air (the S14(d) "floating" look this fixes).
     *  0.05 keeps it clear of z-fighting with the surface; 0.3 keeps the whole cloud at snow-glint height. */
    private static final double SPARKLE_Y_MIN = 0.05;
    private static final double SPARKLE_Y_MAX = 0.3;
    /** S17(c)(ii): a glint SITS, it does not wander (owner). The drift is removed -- the particle spawns with
     *  zero incoming velocity so it twinkles in place on the snow (the spark's own flicker + short life carry
     *  the shimmer; GLINT v5 note -- this zero-drift spawn is exactly what makes the FIREWORK spark safe from
     *  its old "raindrop" read). Kept as named 0.0 dials so a P4 pass can reintroduce a whisper of motion
     *  without touching the spawn loop. */
    private static final double SPARKLE_DRIFT_UP = 0.0;
    private static final double SPARKLE_DRIFT_LATERAL = 0.0;
    /**
     * GLINT v5 DE-PURPLE (owner flight TEST 113, 2026-07-19): the glint particle is {@code FIREWORK} -- a pure
     * WHITE flickering spark with NO colour tint.
     *
     * <p><b>Why WAX_OFF lost.</b> S14(d) picked {@code WAX_OFF} for its "amethyst sparkle" -- but its baked
     * colour {@code setColor(1.0, 0.9, 1.0)} + emissive glow (bytecode-verified against the 26.2
     * {@code GlowParticle} defs) is a LILAC, and on a white snowfield under the greying polar sky the owner read
     * exactly that lilac as "purple" (TEST 113 video: lavender stars, worst in the 76S village, one oversized
     * blob). The snow must glint WHITE; any tinted particle will always read as a foreign colour on it.
     *
     * <p><b>Why FIREWORK is safe now.</b> The original S14(d) complaint against FIREWORK ("raindrop/unclear")
     * was the AIRBORNE DRIFT of the old spawn -- glints floating 0.5-1.5 blocks up in the air, falling like
     * drizzle. The S17(c)(ii) v3 spawn already fixed that independently: glints now hug the sampled snow surface
     * (0.05-0.3 blocks) with ZERO incoming velocity, so a FIREWORK spark sits ON the snow and twinkles in place
     * -- it cannot read as rain any more. The S17(c)(iv) block on the old field pre-approved this exact one-line
     * swap ("the sharper original spark the owner floated"); TEST 113 is the flight verdict it was waiting for.
     *
     * <p>Alternatives on file for a future P4 pass: {@code ELECTRIC_SPARK} (lilac again -- now known-rejected
     * on snow), {@code END_ROD} (warm cream, floaty -- an ember, not frost). */
    private static final net.minecraft.core.particles.SimpleParticleType SPARKLE_PARTICLE = ParticleTypes.FIREWORK;

    /**
     * S21(b)(iii) -> GLINT v5: the twin micro-cluster is RETIRED (count 2 -> 1; owner flight TEST 113,
     * 2026-07-19). The twin existed for one reason only -- to FATTEN the dim WAX_OFF quad into a visible flash
     * (two lilac quads at a {@link #SPARKLE_TWIN_OFFSET} offset read as one brighter point). FIREWORK is a
     * bright flickering spark on its own; doubling it reads as NOISE (and helped build TEST 113's "oversized
     * blob"). The cluster loop + offset dial are kept in place (count 1 = a single spawn per accepted point,
     * offset inert) so a P4 pass can re-fatten a future dim particle without touching the spawn loop; the pure
     * {@link com.example.globe.core.SnowSparkleLaw} budget still counts CLUSTERS, unchanged.
     */
    private static final int SPARKLE_TWIN_COUNT = 1;
    private static final double SPARKLE_TWIN_OFFSET = 0.12;

    /**
     * S21(b)(ii) SURFACE CHECK -- the snow/ice block family a glint may land on. The owner saw bare STONE
     * glinting on a Stony Shore inside the glint band; v4 samples the actual surface block and only glints when
     * it is snow or ice (a full snow block or a thin snow LAYER dusting the ground, powder snow, or any ice
     * family member). Anything else -- stone, gravel, dirt, sand -- is skipped, so the glint reads unambiguously
     * as the SNOW catching the light, never bare rock. (A skipped point costs a cluster from the per-tick budget,
     * which is intended: the budget is the MAX clusters; bare-ground points thin it naturally.)
     *
     * <p><b>GLINT v5 WATER AUDIT (owner flight TEST 113, 2026-07-19: glints "apparently over open water" at
     * 75-76S).</b> Audited against water-top columns: the sampled {@code MOTION_BLOCKING_NO_LEAVES} heightmap
     * DOES include fluids (26.2 predicate: blocks-motion OR non-empty fluid), so over open ocean/lake the
     * heightmap top at {@code surfaceY-1} IS the surface water block -- but {@code WATER} is not in this
     * ALLOWLIST (nor is kelp/seagrass, whose states carry water), and no member of the list is waterloggable,
     * so a liquid column cannot pass the v4 check as written. The likeliest reading of the video is legitimate
     * sea-ICE glints (ICE/PACKED_ICE are in-list by design -- frozen ocean SHOULD glint) plus WAX_OFF's 2 s
     * lilac linger making shoreline points read as floating; still, "snow/ice only, never fluid" is now made
     * STRUCTURAL rather than incidental: {@link #isGlintSurface} rejects any state carrying a fluid, so a
     * future edit that adds a waterloggable block to this set cannot silently re-open the water hole.
     */
    private static final java.util.Set<Block> GLINT_SURFACE_BLOCKS = java.util.Set.of(
            Blocks.SNOW_BLOCK, Blocks.SNOW, Blocks.POWDER_SNOW,
            Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.FROSTED_ICE);

    /** True iff {@code state} is a snow/ice-family block a glint may sit on (see {@link #GLINT_SURFACE_BLOCKS}).
     *  GLINT v5: additionally rejects ANY state with a non-empty fluid (water, lava, waterlogged anything) --
     *  the structural "never fluid" backstop from the TEST 113 water audit above. Today no in-list block can
     *  carry a fluid, so this is belt-and-suspenders by construction, not a behaviour change. */
    private static boolean isGlintSurface(BlockState state) {
        return GLINT_SURFACE_BLOCKS.contains(state.getBlock()) && state.getFluidState().isEmpty();
    }

    // Calm-weather glints on the snowfields near the player (GLINT v4: the 75-82 band, crossfading OUT as the
    // ambient snowfall crossfades IN at 80-82 -- SnowSparkleLaw.glintWeight). A tiny, FIXED per-spawn-tick CLUSTER
    // budget from the pure SnowSparkleLaw (band ramp x snowfall crossfade x calm gate), scaled by the SAME vanilla
    // Particles tier + enclosure estimate + reduce-snow comfort option as the ambient snow, so it honors every
    // perf/accessibility knob without a second curve. No state/accumulator; the caller's isPaused/spawn-tick
    // guards (B-3b anti-backlog law) are untouched. S17(c)(ii): each glint hugs the sampled surface (0.05-0.3
    // above it) with zero drift; S21(b): only snow/ice surfaces glint (v5: never a fluid column); GLINT v5
    // (TEST 113): one bright FIREWORK spark per accepted point -- the twin cluster is retired.
    private static void spawnSnowSparkle(Minecraft client, double absLatDeg,
                                         com.example.globe.core.ParticleDensity.Tier tier, float exposure) {
        // The blizzard drive is the storm signal (0 across the calm 75-82 glint band; belt-and-suspenders per the law).
        double blizz = com.example.globe.core.PolarHazardWindow.blizzardDrive(absLatDeg);
        // GLINT CLOCK (S24): gather the two PURE inputs the law now needs -- the vanilla-synced overworld clock
        // (the day/night pulse; §7 zero-netcode -- MC already replicates it to every client) and whether this
        // latitude is in an around-the-clock solar band (polar night / midnight sun), which extends the glint's
        // equatorward onset from 75 to 60 so the day/night TELL exists everywhere the day-bright-under-dark-sky
        // confusion does. The law stays MC-free; this adapter reads MC and hands it primitives.
        long clock = client.level.getOverworldClockTime();
        boolean functionalBandActive = sparkleFunctionalBandActive(client, absLatDeg);
        int budget = com.example.globe.core.SnowSparkleLaw.sparkleBudget(
                absLatDeg, functionalBandActive, (double) clock, blizz, SPARKLE_PEAK_BUDGET);
        // The pure per-tick BUDGET (band trapezoid x calm gate x snowfall window), then scaled to the actual spawn
        // COUNT by the vanilla Particles tier + enclosure estimate + reduce-snow comfort option (the same knobs the
        // ambient snow honours). Kept apart so the S20 recorder can report budget-vs-spawned (a budget>0 with a
        // scaled count of 0 is the diagnostic signal "the perf/enclosure/reduce-snow scaling zeroed it").
        int count = budget;
        if (count > 0) {
            count = com.example.globe.core.ParticleDensity.scale(tier, count);
            count = com.example.globe.core.PolarExposure.particleBudget(count, exposure);
            if (com.example.globe.client.LatitudeConfig.reducePolarSnowParticles) {
                count = (int) Math.round(count * REDUCE_POLAR_SNOW_FACTOR);
            }
            if (count < 0) {
                count = 0;
            }
        }
        // S20 SPARKLE recorder (default off, static-final gate -> dead-code when the prop is unset): sample this
        // spawn-tick and flush the ~5s window line. Recorded on every path (incl. count == 0) so "sparkle invisible
        // live" is diagnosable: spawned=0 with budget>0 = scaling; both>0 yet nothing seen = a particle-render issue.
        if (com.example.globe.core.PolarInstrument.SPARKLE) {
            // GLINT v4 (S21b) + GLINT CLOCK (S24): band01 = the EFFECTIVE glint band ramp (75->76, or the
            // extended 60->61 when this latitude is in a polar-night/midnight-sun band); window01 = the
            // snowfall-gate factor (1 - snowfallRamp01), the (1 - snow) side of the 80->82 crossfade; clock01 =
            // the GLINT CLOCK day curve (1 at noon, 0 at clock-night). Their PRODUCT is SnowSparkleLaw.glintWeight
            // -- so a flight can read band x window x clock and prove exactly which term shuttered the pulse.
            double band01 = com.example.globe.core.SnowSparkleLaw.bandRamp01(absLatDeg, functionalBandActive);
            double window01 = 1.0 - com.example.globe.core.SnowSparkleLaw.snowfallRamp01(absLatDeg);
            double clock01 = com.example.globe.core.SnowSparkleLaw.clockDayCurve01((double) clock);
            com.example.globe.core.PolarInstrument.sparkleSample(budget, count, window01, band01, clock01);
            String line = com.example.globe.core.PolarInstrument.pollSparkleLine(client.level.getGameTime());
            if (line != null) {
                GlobeMod.LOGGER.info(line);
            }
        }
        if (count <= 0) {
            return;
        }
        RandomSource random = client.player.getRandom();
        double px = client.player.getX();
        double pz = client.player.getZ();
        for (int i = 0; i < count; i++) {
            int bx = (int) Math.floor(px + (random.nextDouble() - 0.5) * SPARKLE_RADIUS * 2.0);
            int bz = (int) Math.floor(pz + (random.nextDouble() - 0.5) * SPARKLE_RADIUS * 2.0);
            int surfaceY = client.level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            // S21(b)(ii) SURFACE CHECK: glint ONLY on snow/ice. The heightmap top is the block at surfaceY-1;
            // a thin snow LAYER dusting grass often sits ABOVE the motion-blocking top (at surfaceY), so accept
            // either. Bare stone/gravel (the Stony Shore the owner saw glinting) matches neither -> skip.
            BlockPos topPos = new BlockPos(bx, surfaceY - 1, bz);
            if (!isGlintSurface(client.level.getBlockState(topPos))
                    && !isGlintSurface(client.level.getBlockState(topPos.above()))) {
                continue;
            }
            double gx = bx + random.nextDouble();
            double gz = bz + random.nextDouble();
            // S17(c)(ii): hug the snow (0.05-0.3 above the sampled surface) so it reads as the snow glinting.
            double gy = surfaceY + SPARKLE_Y_MIN + random.nextDouble() * (SPARKLE_Y_MAX - SPARKLE_Y_MIN);
            // Zero incoming velocity -- the glint twinkles in place (the spark's own flicker + short life carry it).
            double vx = (random.nextDouble() - 0.5) * 2.0 * SPARKLE_DRIFT_LATERAL;
            double vy = SPARKLE_DRIFT_UP;
            double vz = (random.nextDouble() - 0.5) * 2.0 * SPARKLE_DRIFT_LATERAL;
            // S21(b)(iii) cluster loop -- GLINT v5 (TEST 113): SPARKLE_TWIN_COUNT is 1, so this spawns a single
            // bright FIREWORK spark per accepted point (the WAX_OFF-era twin fattening is retired; the loop +
            // offset dial stay so a future dim particle can be re-fattened without touching the spawn shape).
            // The cluster is ONE budget unit (the count loop already spent it) -- budget accounting unchanged.
            for (int j = 0; j < SPARKLE_TWIN_COUNT; j++) {
                double ox = (random.nextDouble() - 0.5) * SPARKLE_TWIN_OFFSET;
                double oy = (random.nextDouble() - 0.5) * SPARKLE_TWIN_OFFSET;
                double oz = (random.nextDouble() - 0.5) * SPARKLE_TWIN_OFFSET;
                client.particleEngine.createParticle(SPARKLE_PARTICLE, gx + ox, gy + oy, gz + oz, vx, vy, vz);
            }
        }
    }

    // GLINT CLOCK (S24) client adapter: is the observer's latitude in an around-the-clock solar band (polar
    // night / midnight sun) right now? Mirrors the established client evaluators (AuroraRenderer /
    // SkyRendererSolarTiltMixin): north-positive signed latitude from the border, the vanilla-synced clock's
    // declination, then the ONE SolarTilt.functionalBand evaluator -- the same one GlobeMod.solarFunctionalBand
    // runs server-side, with the 60-deg functional floor -- so the sky, the mob rules and this glint band can
    // never disagree (the one-evaluator law). Gated on the solar-tilt master flag + globe overworld: with tilt
    // off (or off-overworld) there is no polar night to confuse the clock, so the glint keeps its normal 75
    // onset (the clock day/night PULSE still applies planet-wide -- that is threaded separately, always).
    private static boolean sparkleFunctionalBandActive(Minecraft client, double absLatDeg) {
        // Cheap early-out: the extended onset (== the functional floor) is 60 deg; below that no band can be
        // active and the extension is a no-op, so skip the border/clock trig for the equatorial majority of ticks.
        if (Double.isNaN(absLatDeg)
                || absLatDeg < com.example.globe.core.SnowSparkleLaw.FUNCTIONAL_EXTENDED_ONSET_DEG) {
            return false;
        }
        if (!com.example.globe.core.LatitudeV2Flags.SOLAR_TILT_V2_ENABLED) {
            return false;
        }
        if (!client.level.dimension().identifier()
                .equals(net.minecraft.world.level.Level.OVERWORLD.identifier())) {
            return false; // the polar sky (and thus the day-bright confusion) exists only in the globe overworld.
        }
        double phi = -com.example.globe.util.LatitudeMath.degreesFromZ(
                client.level.getWorldBorder(), client.player.getZ());
        long clock = client.level.getOverworldClockTime();
        double delta = com.example.globe.core.SolarTilt.deltaDeg(
                com.example.globe.core.SolarTilt.dayCount(clock),
                com.example.globe.core.LatitudeV2Flags.SOLAR_TILT_DELTA_MAX_DEG,
                com.example.globe.core.LatitudeV2Flags.SOLAR_TILT_YEAR_LENGTH_DAYS,
                com.example.globe.core.LatitudeV2Flags.SOLAR_TILT_FROZEN_PHASE_DEG);
        return com.example.globe.core.SolarTilt.functionalBand(phi, delta,
                com.example.globe.core.LatitudeV2Flags.SOLAR_TILT_FUNCTIONAL_MIN_DEG)
                != com.example.globe.core.SolarTilt.FunctionalBand.NONE;
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

    // HUD Studio round 10 item (i): the "Reduce Polar Snow Particles" accessibility comfort scale. When the toggle
    // is ON, the FINAL per-tick snow budget (after the perf-tier + enclosure scaling) is multiplied by this once,
    // knocking the storm down to ~25% of normal -- the owner's "comfort option for users the blizzard might
    // discomfort" (~25%). A pure multiplicative scale, no state/accumulator, so the B-3b anti-backlog law holds.
    private static final double REDUCE_POLAR_SNOW_FACTOR = 0.25;

    // ---- B-7 P3 DEEP BLIZZARD (owner order, TEST 97 maiden pole flight): the 87->89 whiteout tier. ----
    // The 82-87 gentle band is untouched (deepT = 0 there -> byte-identical); from 87 deg a THIRD spawn pass
    // ramps in, reaching DEEP_BLIZZARD_EXTRA_MULT x the pre-P3 TOTAL (main + second pass) by 89 deg and holding
    // to the pole -- "a VERY heavy snowfall, like 10x this much" -- with a faster DOWNWARD fall (the wind terms
    // are untouched; the owner rejected more sideways drift). Every P4 tuning knob is one line here.
    /** Deep tier onset (deg): matches the blizzardDrive/second-pass onset so the whiteout deepens from the
     *  same latitude the gale begins. Below this the ambient snow is byte-identical to pre-P3. */
    private static final double DEEP_BLIZZARD_START_DEG = 87.0;
    /** Deep tier FULL (deg): the whiteout hits its 10x ceiling here (89, the DANGER rung) and holds to 90 --
     *  steeper than the blizzardDrive ramp, which reaches 1 only at the pole itself. */
    private static final double DEEP_BLIZZARD_FULL_DEG = 89.0;
    /** Deep-pass budget as a multiple of today's TOTAL (main + second pass): total = (1 + this) x pre-P3
     *  density at full ramp. 9.0 -> ~10x the TEST 97 look at 89+ (the owner's number). */
    private static final double DEEP_BLIZZARD_EXTRA_MULT = 9.0;
    /** HARD PERF CAP on the deep pass alone, per spawn-tick (every 4th tick), applied AFTER the vanilla
     *  Particles-tier, enclosure and gust scaling. S10c raised 600 -> 800 (the owner keeps ordering MORE and
     *  the density now climbs past 89): worst case (Particles=All, open sky, 90 deg, gust crest) ~90 main +
     *  ~90 second + 800 deep = ~980/spawn-tick = ~245 flakes/tick -- a deliberate whiteout, still bounded
     *  under the engine ceiling; the vanilla Particles setting scales it down two more notches. */
    private static final int DEEP_BLIZZARD_HARD_CAP = 800;
    /** Deep-band DOWNWARD fall-speed multiplier (owner: "greater falling speed"), ramped 1.0 -> this across
     *  87->89. 1.75 sits mid of the ordered 1.5-2x window; applied to the main + deep passes' vy only (never
     *  the wind). NB: SnowflakeParticle pins vertical speed toward a terminal ~0.081/tick, so the boost reads
     *  strongest in the first moments of each flake's fall -- P4 eyeballs whether 1.75 lands. */
    private static final double DEEP_BLIZZARD_FALL_MULT = 1.75;
    /** S10c: fall multiplier keeps climbing 89 -> 90 to this at the pole line ("keeps deepening toward the
     *  line" -- the last degree must feel WORSE than 89, not a plateau). */
    private static final double DEEP_BLIZZARD_FALL_MULT_POLE = 2.1;
    /** S10c: deep-pass BUDGET kick over 89 -> 90 (+50% by the line), same "no plateau" law as the fall. */
    private static final double DEEP_BLIZZARD_FINAL_DENSITY_KICK = 0.5;

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
