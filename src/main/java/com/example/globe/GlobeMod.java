package com.example.globe;

import net.fabricmc.api.ModInitializer;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeWorldState;
import com.example.globe.world.BiomeSelectionProfile;
import com.example.globe.world.VanillaBiomeRepresentationProfile;
import com.example.globe.world.CaveBiomeRepresentationProfile;
import com.example.globe.world.SpawnSafetyPolicy;
import com.example.globe.world.WorldgenGeneratorAuthorityPolicy;
import com.example.globe.util.BiomeSamplerTools;
import com.example.globe.util.BiomeSamplerTools.SamplerTemplate;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.lang.reflect.Method;

import java.io.InputStream;

public class GlobeMod implements ModInitializer {
    public static final String MOD_ID = "globe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String SPAWN_CHOSEN_TAG = "globe_spawn_chosen";
    // Duplicates vanilla's own join logging once per player join; opt-in only (maintainer ruling, 2026-08-18).
    private static final boolean DEBUG_JOIN = Boolean.getBoolean("latitude.debugJoin");

    public static final int BORDER_RADIUS = 7500;
    public static final int POLE_BAND_START_ABS_Z = 12000;
    private static int activePoleBandStartAbsZ = POLE_BAND_START_ABS_Z;
    public static final int POLE_WARNING_DISTANCE_BLOCKS = 256;
    public static final int POLE_LETHAL_DISTANCE_BLOCKS = 96;
    public static final int POLE_LETHAL_WARNING_DISTANCE = POLE_WARNING_DISTANCE_BLOCKS;
    public static final int EFFECT_REFRESH_TICKS = 20;
    private static final int EW_WARNING_DISTANCE_BLOCKS = 500;
    private static final int EW_SPAWN_PADDING_BLOCKS = 64;
    private static final long SPAWN_SALT = 0x7A3E21B5D4C1F7A9L;
    private static final Set<String> PROVIDER_PROFILE_WARNINGS = ConcurrentHashMap.newKeySet();

    public static final class InitialSpawnSelectionException extends IllegalStateException {
        public InitialSpawnSelectionException(String message) {
            super(message);
        }

        public InitialSpawnSelectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final int POLE_START = 12000; // Legacy constant, use activePoleBandStartAbsZ for dynamic logic

    private enum PolarStage {
        NONE,
        UNEASE,
        IMPAIR,
        HOSTILE,
        WHITEOUT,
        LETHAL,
        HOPELESS
    }

    private static final Identifier GLOBE_SETTINGS_ID = Identifier.fromNamespaceAndPath(MOD_ID, "overworld");
    private static final Identifier GLOBE_SETTINGS_XSMALL_ID = Identifier.fromNamespaceAndPath(MOD_ID, "overworld_xsmall");
    private static final Identifier GLOBE_SETTINGS_SMALL_ID = Identifier.fromNamespaceAndPath(MOD_ID, "overworld_small");
    private static final Identifier GLOBE_SETTINGS_REGULAR_ID = Identifier.fromNamespaceAndPath(MOD_ID, "overworld_regular");

    private static final Identifier GLOBE_SETTINGS_LARGE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "overworld_large");
    private static final Identifier GLOBE_SETTINGS_MASSIVE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "overworld_massive");

    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_KEY = ResourceKey.create(net.minecraft.core.registries.Registries.NOISE_SETTINGS, GLOBE_SETTINGS_ID);
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_XSMALL_KEY = ResourceKey.create(net.minecraft.core.registries.Registries.NOISE_SETTINGS, GLOBE_SETTINGS_XSMALL_ID);
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_SMALL_KEY = ResourceKey.create(net.minecraft.core.registries.Registries.NOISE_SETTINGS, GLOBE_SETTINGS_SMALL_ID);
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_REGULAR_KEY = ResourceKey.create(net.minecraft.core.registries.Registries.NOISE_SETTINGS, GLOBE_SETTINGS_REGULAR_ID);

    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_LARGE_KEY = ResourceKey.create(net.minecraft.core.registries.Registries.NOISE_SETTINGS, GLOBE_SETTINGS_LARGE_ID);
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_MASSIVE_KEY = ResourceKey.create(net.minecraft.core.registries.Registries.NOISE_SETTINGS, GLOBE_SETTINGS_MASSIVE_ID);

    /** Exact overworld generator for the currently loaded Latitude server. */
    private static volatile NoiseBasedChunkGenerator activeLatitudeOverworldGenerator;

    @Override
    public void onInitialize() {
        LOGGER.info("{} initialized. Use the globe:globe world preset for deterministic terrain.", MOD_ID);

        logBuildMetadata("server");

        com.example.globe.compat.LatitudeTerraBlenderBridge.install();

        // Placement modifier behind Latitude's lush desert riverbanks. Registered unconditionally
        // so the placed-feature JSON still parses when the banks themselves are switched off.
        com.example.globe.world.feature.LatitudeRiparianBanks.registerPlacementType();

        GlobeNet.registerPayloads();
        com.example.globe.world.LatitudeDecorationRetrofit.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Shipping operator commands: unconditional and directly linked. Unlike the dev tree,
            // com.example.globe.tools is packaged into release artifacts, so a missing class is a
            // build error worth surfacing rather than something to swallow reflectively.
            com.example.globe.tools.LatitudeToolsCommand.register(dispatcher);
            registerDevOnlyCommand(dispatcher);
        });

        // Initialize province authority at world-load time, before spawn-chunk generation fires
        // for brand-new worlds. SERVER_STARTED fires too late (after spawn chunks are pregenerated).
        ServerWorldEvents.LOAD.register(GlobeMod::initLatitudeBiomesForWorld);
        ServerLifecycleEvents.SERVER_STARTED.register(GlobeMod::applyWorldBorder);
        registerDevOnlyHeadlessRunner();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            activeLatitudeOverworldGenerator = null;
            LatitudeBiomes.clearWorldgenContext();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerLevel overworld = server.overworld();
            if (overworld == null) {
                return;
            }

            boolean isGlobe = isGlobeOverworld(overworld);
            if (DEBUG_JOIN) {
                LOGGER.info("JOIN: player={}, isGlobeOverworld={}", handler.player.getName().getString(), isGlobe);
            }

            boolean isBrandNewWorld = overworld.getGameTime() < 100L;
            boolean spawnAlreadyChosen = handler.player.getTags().contains(SPAWN_CHOSEN_TAG);
            // On a brand-new world the player is still standing at vanilla's spawn -- Latitude's own
            // spawn selection runs further down THIS handler. Snapshotting here would record the
            // equatorial default and then ship it as the loading label, so a player who asked for
            // polar would be told "Equatorial" on the way in. No label beats a wrong one; the
            // throttled tick records the real band once the player is where they belong.
            boolean spawnStillToBeChosen = isBrandNewWorld && !spawnAlreadyChosen;

            LatitudeWorldState worldState = isGlobe ? LatitudeWorldState.get(overworld) : null;
            if (worldState != null && !spawnStillToBeChosen) {
                // A fresh world can cross the join boundary before borderUxTick's next throttled
                // pass, or a returning player can rejoin far from where they last quit. Snapshot the
                // actual band now so the loading-label payload just below, and the next world-list
                // row, both reflect where the player really is instead of stale or absent data.
                recordLastKnownBand(overworld, overworld.getWorldBorder(), handler.player);
            }
            // LatitudeWorldLauncher (fresh creation) and the resumed-world mixins already set the
            // loading label from LOCAL, network-free state -- but only on the HOST's own client,
            // which is the only client either of them ever runs on. Send the real id to every
            // client unconditionally; the receiver on the other end is the one responsible for
            // ignoring it on the host (see GlobeModClient), so a remote join -- dedicated or a
            // friend joining an opened-to-LAN world -- gets a label none of those three mechanisms
            // could ever have given it.
            String loadingBandId = worldState == null ? "" : worldState.getLastKnownBandId().orElse("");
            ServerPlayNetworking.send(handler.player, new GlobeNet.GlobeStatePayload(isGlobe, loadingBandId));

            String pendingZone = server.isDedicatedServer() ? null : GlobePending.consume();

            boolean startWithCompass = !server.isDedicatedServer() && GlobePending.startWithCompass;
            if (isGlobe && !server.isDedicatedServer() && !StartCompass.hasReceived(handler.player)) {
                if (!startWithCompass) {
                    StartCompass.markReceived(handler.player);
                } else if (hasCompassAnywhere(handler.player)) {
                    StartCompass.markReceived(handler.player);
                } else {
                    boolean given = handler.player.addItem(new ItemStack(Items.COMPASS));
                    if (given) {
                        StartCompass.markReceived(handler.player);
                    }
                }
            }

            if (isGlobe && !spawnAlreadyChosen && !worldState.isSpawnPickerDismissed() && isBrandNewWorld) {
                // Initial creation already set a terrain-validated Latitude spawn. Never repeat a
                // synchronous globe scan on first join: that can strand the loading overlay.
                LOGGER.info("[Latitude] Suppressing legacy post-load spawn relocation; retaining the initial safe spawn");
                worldState.setSpawnPickerDismissed(true);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(GlobeNet.SetSpawnPickerPayload.ID, (payload, context) -> {
            context.server().execute(() -> applySpawnChoice(context.player(), payload.zoneId()));
        });

        ServerTickEvents.END_SERVER_TICK.register(GlobeMod::borderUxTick);

        // Authoritative last-known-band capture at quit time, closing the gap the periodic tick in
        // borderUxTick leaves if a player quits within moments of crossing a band boundary.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            ServerLevel overworld = server.overworld();
            if (player == null || overworld == null || player.level() != overworld || !isGlobeOverworld(overworld)) {
                return;
            }
            recordLastKnownBand(overworld, overworld.getWorldBorder(), player);
        });
    }

    private static void registerDevOnlyCommand(Object dispatcher) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        invokeDevRegister("com.example.globe.dev.LatitudeDevCommand", dispatcher);
    }

    private static void registerDevOnlyHeadlessRunner() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        invokeDevRegister("com.example.globe.dev.BiomePreviewHeadlessRunner");
        invokeDevRegister("com.example.globe.dev.StructureAtlasExporter");
        invokeDevRegister("com.example.globe.dev.DistributionCensusExporter");
    }

    private static void invokeDevRegister(String className, Object... args) {
        try {
            Class<?> clazz = Class.forName(className);
            Method registerMethod = null;
            for (Method method : clazz.getMethods()) {
                if (!method.getName().equals("register") || method.getParameterCount() != args.length) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean matches = true;
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (args[i] != null && !parameterTypes[i].isAssignableFrom(args[i].getClass())) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    registerMethod = method;
                    break;
                }
            }
            if (registerMethod == null) {
                throw new NoSuchMethodException("No compatible register method found");
            }
            registerMethod.invoke(null, args);
        } catch (ClassNotFoundException e) {
            LOGGER.debug("[latdev] Skipping missing dev class {}", className);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[latdev] Failed to invoke {}.register", className, e);
        }
    }

    private static void captureProviderTicketProfile(ServerLevel world, LatitudeWorldState worldState,
                                                      long seed, int captureRadius, String origin) {
        BiomeSelectionProfile profile = BiomeSelectionProfile.capture(
                world.registryAccess().lookupOrThrow(Registries.BIOME).keySet().stream()
                        .map(Identifier::toString).toList());
        worldState.setProviderTicketProfile(profile);
        worldState.setVanillaRepresentationProfile(
                VanillaBiomeRepresentationProfile.capture(captureRadius, seed, profile));
        worldState.setCaveRepresentationProfile(
                CaveBiomeRepresentationProfile.capture(captureRadius, profile));
        worldState.setWorldgenPolicy(
                LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V5_FINAL_ADMISSION);
        LOGGER.info("[Latitude] Recorded Globe world: border radius {} ({})", captureRadius, origin);
    }

    /**
     * Explicit, operator-driven profile adoption for a world that never captured one — the
     * {@code /latitude retrofit} path. Unlike the fresh-world capture this runs OUTSIDE the
     * creation window, which is exactly the point: the operator has been warned that newly
     * generated chunks will differ from existing ones. Re-activates the worldgen context in place
     * so the adopted roster applies without a restart.
     */
    public static void adoptProviderTicketProfile(MinecraftServer server, ServerLevel world, String origin) {
        // Defence in depth. The caller (/latitude retrofit) refuses non-Latitude worlds up front,
        // but adoption is IRREVERSIBLE — it persists a globe radius, which makes isGlobeOverworld
        // return true forever after, arming Latitude's biome authority and a world border on a
        // world the player never asked to convert. Never let that happen from any future caller.
        if (!isLatitudeOverworld(world)) {
            LOGGER.warn("[Latitude] Refusing profile adoption on a non-Latitude overworld "
                    + "(dimension={} origin={}): this world was not generated by Latitude.",
                    world == null ? "null" : world.dimension().identifier(), origin);
            return;
        }
        LatitudeWorldState worldState = LatitudeWorldState.get(world);
        long seed = server.getWorldData().worldGenOptions().seed();
        int captureRadius = borderRadiusForGlobeOverworld(world);
        captureProviderTicketProfile(world, worldState, seed, captureRadius, origin);
        if (worldState.getGlobeRadius() <= 0) {
            worldState.setGlobeRadius(captureRadius);
        }
        ChunkGenerator generator = world.getChunkSource().getGenerator();
        activeLatitudeOverworldGenerator = generator instanceof NoiseBasedChunkGenerator noise
                ? noise
                : null;
        int radius = borderRadiusForGlobeOverworld(world);
        LatitudeBiomes.activateWorldgenContext(radius, seed, worldState.getWorldgenPolicy(),
                worldState.getProviderTicketProfile().orElse(null),
                worldState.getVanillaRepresentationProfile().orElse(null),
                worldState.getCaveRepresentationProfile().orElse(null),
                world.getChunkSource().randomState().sampler(),
                donorBiomeSource(generator),
                generator.getSeaLevel());
        LOGGER.info("[Latitude] Worldgen context re-activated after profile adoption (seed={} radius={})", seed, radius);
    }

    /**
     * Fires at world-load time — before Minecraft pre-generates spawn chunks for new worlds.
     * Seeds {@link LatitudeBiomes} with the world seed and radius so that province authority
     * is non-null when the first worldgen call arrives.
     *
     * <p>Only acts on the Globe overworld; other dimensions are ignored.
     */
    private static void initLatitudeBiomesForWorld(MinecraftServer server, ServerLevel world) {
        if (world != server.overworld()) {
            return;
        }

        int pendingRadius = GlobePending.pendingGlobeRadius;
        GlobePending.pendingGlobeRadius = 0;

        // The create screen's own launch is the primary authority on whether this is a Latitude
        // world, exactly as on the shipped 26.1x line: record the pending radius BEFORE consulting
        // the generator. isGlobeOverworld() then recognises the world through the persisted state.
        // The generator-holder check must NOT gate this: lithostitched legitimately swaps a patched
        // Direct settings holder into the live generator to inject dependent mods' worldgen
        // (CliffTree on 26.1.x), which breaks holder-key recognition while leaving the terrain
        // exactly ours. The launcher already verified the baked generator was a bound globe:
        // reference before launch, so the only thing still worth failing on is gross substitution —
        // a generator that is not even a NoiseBasedChunkGenerator.
        if (pendingRadius > 0 && world.getGameTime() < 100L
                && !(world.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator)) {
            throw new IllegalStateException(
                    "Latitude created this world, but the loaded overworld generator was replaced "
                    + "by a non-noise generator (" 
                    + world.getChunkSource().getGenerator().getClass().getName()
                    + "). Another world generation mod took over the overworld during world "
                    + "creation; the world would save as vanilla terrain and Latitude would stay "
                    + "disabled in it. Remove the conflicting world generation mod and create the "
                    + "world again. This world save is not a Latitude world and can be deleted.");
        }
        LatitudeWorldState earlyState = pendingRadius > 0 && world.getGameTime() < 100L
                ? LatitudeWorldState.get(world)
                : LatitudeWorldState.getIfPresent(world);
        if (earlyState != null && earlyState.getGlobeRadius() <= 0
                && pendingRadius > 0 && world.getGameTime() < 100L) {
            earlyState.setGlobeRadius(pendingRadius);
            LOGGER.info("[Latitude] Recorded Globe world radius {} ahead of generator recognition "
                    + "(create-screen authority)", pendingRadius);
        }

        if (!isGlobeOverworld(world)) {
            // ServerWorldEvents.LOAD fires for EVERY dimension. Only a non-Globe OVERWORLD may
            // disarm the process-global Latitude authority (a vanilla world opened after a Globe
            // one). The nether/end loading right after the overworld must never null the just-armed
            // generator identity: chunks generated in that window would silently fall back to
            // vanilla painting (found 2026-08-24 while tracing pack-world biome leaks).
            if (world.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                activeLatitudeOverworldGenerator = null;
                LatitudeBiomes.clearWorldgenContext();
            }
            return;
        }
        LatitudeWorldState worldState = LatitudeWorldState.get(world);
        long seed = server.getWorldData().worldGenOptions().seed();
        // Provider-ticket profile capture. pendingRadius > 0 was the only trigger here, which made
        // the capture a create-screen-only event: a dedicated server (or a vanilla create flow that
        // selects the globe preset without Latitude's screen) never has a pending radius, so its
        // worlds ran profile-less forever — MODERN_1_3 policy instead of V4, and every
        // ledger-routed provider biome silently absent (proven: a 390k-sample atlas placed all 26
        // tagged BoP biomes and zero of the 14 ledger-only ones). The capture now fires for ANY
        // fresh globe overworld inside the creation window, using the create screen's radius when
        // it exists and the settings-derived radius otherwise.
        //
        // The creation window (gameTime < 100) is what keeps existing worlds untouched: a world
        // created before this fix has lived past the window, stays profile-less, and keeps its
        // frozen legacy placement — capturing later would make new chunks disagree with old ones.
        //
        // -Dlatitude.providerProfileCapture.disable=true suppresses only the NEW non-create-screen
        // path. It exists for cross-version atlas parity: a pre-fix reference build can never
        // capture headlessly, so a like-for-like diff against one needs this build to abstain too.
        boolean creationWindow = world.getGameTime() < 100L;
        boolean clientCreated = pendingRadius > 0;
        boolean dedicatedCaptureDisabled = Boolean.getBoolean("latitude.providerProfileCapture.disable");
        if (worldState.getProviderTicketProfile().isEmpty() && creationWindow
                && (clientCreated || !dedicatedCaptureDisabled)) {
            int captureRadius = clientCreated ? pendingRadius : borderRadiusForGlobeOverworld(world);
            captureProviderTicketProfile(world, worldState, seed, captureRadius,
                    clientCreated ? "from create-world selection" : "fresh dedicated/vanilla-created world");
            if (!clientCreated && worldState.getGlobeRadius() <= 0) {
                // Same state-based recognition resilience 68716f22 gave client-created worlds:
                // captureRadius is borderRadiusForGlobeOverworld's own settings-derived answer
                // (state was empty), so persisting it is a fixpoint — and after this first load,
                // recognition no longer depends on the generator's settings holder surviving
                // in-place mutation by provider mods.
                worldState.setGlobeRadius(captureRadius);
            }
        }
        ChunkGenerator generator = world.getChunkSource().getGenerator();
        activeLatitudeOverworldGenerator = generator instanceof NoiseBasedChunkGenerator noise
                ? noise
                : null;
        int radius = borderRadiusForGlobeOverworld(world);
        warnForProviderProfileDrift(world, worldState);
        LatitudeBiomes.activateWorldgenContext(radius, seed, worldState.getWorldgenPolicy(),
                worldState.getProviderTicketProfile().orElse(null),
                worldState.getVanillaRepresentationProfile().orElse(null),
                worldState.getCaveRepresentationProfile().orElse(null),
                world.getChunkSource().randomState().sampler(),
                donorBiomeSource(generator),
                generator.getSeaLevel());
        LOGGER.info("[Latitude] Early init: province authority seeded before spawn-chunk generation (seed={} radius={})", seed, radius);
        setGlobeBorder(world, radius);
    }

    private static void applyWorldBorder(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        if (!isGlobeOverworld(overworld)) {
            return;
        }

        ChunkGenerator generator = overworld.getChunkSource().getGenerator();
        activeLatitudeOverworldGenerator = generator instanceof NoiseBasedChunkGenerator noise
                ? noise
                : null;

        LatitudeWorldState worldState = LatitudeWorldState.get(overworld);

        int borderRadiusBlocks = borderRadiusForGlobeOverworld(overworld);
        long seed = overworld.getServer().getWorldData().worldGenOptions().seed();
        warnForProviderProfileDrift(overworld, worldState);
        LatitudeBiomes.activateWorldgenContext(borderRadiusBlocks, seed, worldState.getWorldgenPolicy(),
                worldState.getProviderTicketProfile().orElse(null),
                worldState.getVanillaRepresentationProfile().orElse(null),
                worldState.getCaveRepresentationProfile().orElse(null),
                overworld.getChunkSource().randomState().sampler(),
                donorBiomeSource(generator),
                generator.getSeaLevel());

        setGlobeBorder(overworld, borderRadiusBlocks);
    }

    private static BiomeSource donorBiomeSource(ChunkGenerator generator) {
        BiomeSource source = generator.getBiomeSource();
        return source instanceof LatitudeBiomeSource latitude ? latitude.original() : source;
    }

    private static void warnForProviderProfileDrift(ServerLevel world, LatitudeWorldState state) {
        if (!LatitudeWorldState.isProviderTicketPolicy(state.getWorldgenPolicy())) return;
        Optional<BiomeSelectionProfile> profile = state.getProviderTicketProfile();
        if (profile.isEmpty()) {
            if (PROVIDER_PROFILE_WARNINGS.add("missing-profile:" + world.getServer().getWorldData().getLevelName())) {
                LOGGER.warn("[Latitude] Provider-ticket world has no valid birth profile; new terrain will use only non-provider fallback. Removing biome mods can still leave saved chunks unreadable.");
            }
            return;
        }
        java.util.List<String> activeIds = world.registryAccess().lookupOrThrow(Registries.BIOME).keySet().stream()
                .map(Identifier::toString).toList();
        java.util.List<String> missing = profile.get().missingIds(activeIds);
        if (!missing.isEmpty() && PROVIDER_PROFILE_WARNINGS.add(profile.get().encode() + missing)) {
            LOGGER.warn("[Latitude] Provider-ticket birth profile is missing {} locked biome IDs: {}. No new provider is substituted; removing biome mods can still leave saved chunks unreadable.", missing.size(), missing);
        }
    }

    private static void setGlobeBorder(ServerLevel overworld, int borderRadiusBlocks) {
        WorldBorder border = overworld.getWorldBorder();
        // radiusBlocks is e.g. 3750 / 5000 / 7500
        double diameter = borderRadiusBlocks * 2.0;
        border.setCenter(0.0, 0.0);
        border.setSize(diameter);

        int activeRadius = (int) (border.getSize() / 2);
        LatitudeBiomes.setRadius(activeRadius);
        LOGGER.info("[Latitude] Radius Sync: WorldBorder/2 = {}, ACTIVE_RADIUS_BLOCKS = {}", 
                activeRadius, LatitudeBiomes.getActiveRadiusBlocks());

        int activeRadiusForCheck = (int) Math.round(border.getSize() * 0.5);
        LatitudeBiomes.setActiveRadiusBlocks(activeRadiusForCheck);
        GlobeMod.LOGGER.info("[Latitude] Radius Sync: Border/2 = {}, ACTIVE_RADIUS_BLOCKS = {}",
                activeRadiusForCheck, LatitudeBiomes.getActiveRadiusBlocks());

        activePoleBandStartAbsZ = (int) Math.round(activeRadius * com.example.globe.util.LatitudeMath.POLAR_START_FRAC);

        GlobeMod.LOGGER.info("[Latitude] WorldBorder set: radius={} diameter={} center=0,0 polarStart={}",
                borderRadiusBlocks, diameter, activePoleBandStartAbsZ);
    }

    private static void borderUxTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        if (!isGlobeOverworld(overworld)) {
            return;
        }

        long worldTime = overworld.getGameTime();
        boolean effectsTick = (worldTime % 10L) == 0L;

        WorldBorder border = overworld.getWorldBorder();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != overworld) {
                continue;
            }

            double progressZ = com.example.globe.util.LatitudeMath.hazardProgress(border, player.getZ());
            int stageIndex = com.example.globe.util.LatitudeMath.hazardStageIndex(border, player.getZ(), progressZ);

            // Keep the persisted "last known band" fresh for every player, regardless of polar
            // status, so the loading screen and world-selection list can show it on next open.
            if (effectsTick) {
                recordLastKnownBand(overworld, border, player);
            }

            // Check if player is in the active polar band for effects
            if (Math.abs(player.getZ()) < activePoleBandStartAbsZ) {
                continue;
            }

            PolarStage stage = switch (stageIndex) {
                case 1 -> PolarStage.IMPAIR;
                case 2 -> PolarStage.HOSTILE;
                case 3 -> PolarStage.WHITEOUT;
                case 4 -> PolarStage.LETHAL;
                default -> PolarStage.NONE;
            };

            int duration = 40;
            boolean ambient = true;
            boolean showParticles = false;
            boolean showIcon = false;

            // Vanilla thaws ticksFrozen every tick outside powder snow. Maintain the
            // final-zone freeze every server tick, with a small decay margin, so its
            // frost stays steady and vanilla's fully-frozen damage can actually land.
            if ((stage == PolarStage.LETHAL || stage == PolarStage.HOPELESS)
                    && !(player.isCreative() || player.isSpectator())) {
                int max = 140;
                int target = max + 3;
                player.setTicksFrozen(Math.max(player.getTicksFrozen(), target));
            }

            if (!effectsTick) {
                continue;
            }

            if (stage == PolarStage.IMPAIR) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 0, ambient, showParticles, showIcon));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, ambient, showParticles, showIcon));
            } else if (stage == PolarStage.HOSTILE || stage == PolarStage.WHITEOUT) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 1, ambient, showParticles, showIcon));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, ambient, showParticles, showIcon));
                player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, duration, 0, ambient, showParticles, showIcon));
            } else if (stage == PolarStage.LETHAL || stage == PolarStage.HOPELESS) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 2, ambient, showParticles, showIcon));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 1, ambient, showParticles, showIcon));
            }
        }
    }

    /**
     * Persists the latitude band a player currently occupies, so the loading screen and the
     * world-selection list can show it the next time this save is opened. Called at JOIN, on a
     * throttled tick cadence while playing, and once more, authoritatively, on disconnect.
     */
    private static void recordLastKnownBand(ServerLevel overworld, WorldBorder border, ServerPlayer player) {
        double absLatDeg = com.example.globe.util.LatitudeMath.absLatDegExact(border, player.getZ());
        com.example.globe.util.LatitudeBands.Band band = com.example.globe.util.LatitudeBands.fromAbsoluteLatitudeDeg(absLatDeg);
        LatitudeWorldState.get(overworld).setLastKnownBandId(band.id());
    }

    /**
     * Public form of {@link #isGlobeOverworld(ServerLevel)} for operator-driven code paths that
     * must refuse to act on a world Latitude did not generate — notably
     * {@code /latitude retrofit}, whose adoption step is irreversible.
     */
    public static boolean isLatitudeOverworld(ServerLevel world) {
        return world != null && isGlobeOverworld(world);
    }

    private static boolean isGlobeOverworld(ServerLevel world) {
        LatitudeWorldState existingState = LatitudeWorldState.getIfPresent(world);
        if (existingState != null && existingState.getGlobeRadius() > 0) {
            return true;
        }
        ChunkGenerator gen = world.getChunkSource().getGenerator();
        if (!(gen instanceof NoiseBasedChunkGenerator noise)) return false;

        return isGlobeNoiseGenerator(noise);
    }

    private static boolean isGlobeNoiseGenerator(NoiseBasedChunkGenerator noise) {
        return noise != null && (noise.stable(GLOBE_SETTINGS_KEY)
                || noise.stable(GLOBE_SETTINGS_XSMALL_KEY)
                || noise.stable(GLOBE_SETTINGS_SMALL_KEY)
                || noise.stable(GLOBE_SETTINGS_REGULAR_KEY)
                || noise.stable(GLOBE_SETTINGS_LARGE_KEY)
                || noise.stable(GLOBE_SETTINGS_MASSIVE_KEY));
    }

    public static boolean shouldApplyLatitudeWorldgen(NoiseBasedChunkGenerator noise) {
        return WorldgenGeneratorAuthorityPolicy.shouldApply(
                isGlobeNoiseGenerator(noise),
                LatitudeBiomes.hasActiveWorldgenAuthority(),
                noise,
                activeLatitudeOverworldGenerator);
    }

    private static int borderRadiusForGlobeOverworld(ServerLevel world) {
        LatitudeWorldState existingState = LatitudeWorldState.getIfPresent(world);
        int persisted = existingState != null ? existingState.getGlobeRadius() : 0;
        if (persisted > 0) {
            return persisted;
        }
        ChunkGenerator gen = world.getChunkSource().getGenerator();
        if (!(gen instanceof NoiseBasedChunkGenerator noise)) return BORDER_RADIUS;
        return borderRadiusForNoiseGenerator(noise);
    }

    public static int borderRadiusForNoiseGenerator(NoiseBasedChunkGenerator noise) {
        if (noise == null) return BORDER_RADIUS;
        if (noise.stable(GLOBE_SETTINGS_KEY)) return 15000;
        if (noise.stable(GLOBE_SETTINGS_XSMALL_KEY)) return 3750;
        if (noise.stable(GLOBE_SETTINGS_SMALL_KEY)) return 5000;
        if (noise.stable(GLOBE_SETTINGS_REGULAR_KEY)) return BORDER_RADIUS;
        if (noise.stable(GLOBE_SETTINGS_LARGE_KEY)) return 10000;
        if (noise.stable(GLOBE_SETTINGS_MASSIVE_KEY)) return 20000;
        if (WorldgenGeneratorAuthorityPolicy.isExactActiveOverworld(
                LatitudeBiomes.hasActiveWorldgenAuthority(),
                noise,
                activeLatitudeOverworldGenerator)) {
            return LatitudeBiomes.getActiveRadiusBlocks();
        }

        return BORDER_RADIUS;
    }

    public static boolean trySetInitialLatitudeSpawn(ServerLevel world,
                                                     ServerLevelData levelData,
                                                     boolean generateBonusChest,
                                                     boolean debugWorld,
                                                     LevelLoadListener loadListener) {
        if (world == null || levelData == null || debugWorld || !isGlobeOverworld(world)) {
            return false;
        }
        String pendingZone = GlobePending.peek();
        if (pendingZone == null) {
            return false;
        }

        try {
            SpawnChoice spawnChoice = resolveInitialSpawnChoice(world, pendingZone);
            BlockPos spawnPos = spawnChoice.pos();
            if (loadListener != null) {
                loadListener.start(LevelLoadListener.Stage.PREPARE_GLOBAL_SPAWN, 0);
                loadListener.updateFocus(world.dimension(), new ChunkPos(spawnPos));
            }
            levelData.setSpawn(LevelData.RespawnData.of(world.dimension(), spawnPos, 0.0f, 0.0f));
            LatitudeWorldState.get(world).setSpawnPickerDismissed(true);
            // Bonus chest: vanilla setInitialSpawn places it at the vanilla spawn, but we cancel that
            // path and set the Latitude zone spawn instead — so place the bonus chest at OUR spawn.
            if (generateBonusChest) {
                placeLatitudeBonusChest(world, spawnPos);
            }
            LOGGER.info("[Latitude] Early initial spawn set before player-spawn pregen: zone={} x={} y={} z={} radius={} bonusChest={}",
                    spawnChoice.zoneId(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), spawnChoice.radius(), generateBonusChest);
            if (loadListener != null) {
                loadListener.finish(LevelLoadListener.Stage.PREPARE_GLOBAL_SPAWN);
            }
            return true;
        } catch (InitialSpawnSelectionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InitialSpawnSelectionException(
                    "Latitude could not establish a safe initial spawn in the selected climate zone.",
                    e);
        }
    }

    /**
     * Place the vanilla bonus chest at the Latitude globe spawn. We cancel vanilla setInitialSpawn
     * (which would place it at the vanilla-computed spawn), so this re-creates that placement at our
     * zone-based spawn. Mirrors vanilla MinecraftServer.setInitialSpawn's bonus-chest block.
     */
    private static void placeLatitudeBonusChest(ServerLevel world, BlockPos spawnPos) {
        try {
            // Ensure the spawn chunk is loaded so placement actually writes (vanilla's
            // getSpawnHeight loads it; our spawn path may not have).
            world.getChunk(spawnPos);

            // Vanilla's BONUS_CHEST feature is deliberately NOT used here. It shuffles every column
            // in the origin's chunk and places at the first MOTION_BLOCKING_NO_LEAVES position that
            // is air — which over water is the block *above the surface*, so in a wet spawn chunk the
            // chest ends up floating on the water and every torch fails canSurvive. Our spawn column
            // is already validated dry, sturdy land, so anchor the chest to it instead of re-rolling.
            BlockPos chestPos = findBonusChestSite(world, spawnPos);
            if (chestPos == null) {
                LOGGER.warn("[Latitude] No dry bonus-chest site near globe spawn x={} z={}; skipping chest",
                        spawnPos.getX(), spawnPos.getZ());
                return;
            }

            world.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
            RandomizableContainer.setBlockEntityLootTable(
                    world, world.getRandom(), chestPos, BuiltInLootTables.SPAWN_BONUS_CHEST);

            BlockState torch = Blocks.TORCH.defaultBlockState();
            int torches = 0;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos torchPos = chestPos.relative(direction);
                if (torch.canSurvive(world, torchPos) && world.getBlockState(torchPos).isAir()) {
                    world.setBlock(torchPos, torch, 2);
                    torches++;
                }
            }
            LOGGER.info("[Latitude] Placed bonus chest at globe spawn x={} y={} z={} torches={}",
                    chestPos.getX(), chestPos.getY(), chestPos.getZ(), torches);
        } catch (Throwable t) {
            LOGGER.warn("[Latitude] Failed to place bonus chest at globe spawn (continuing without)", t);
        }
    }

    /**
     * Nearest dry, solid-supported column to the spawn that can host the chest without burying the
     * player's own spawn block. Prefers a site where the torches can actually stand, so the chest
     * reads as a deliberate supply drop rather than something washed up.
     */
    private static BlockPos findBonusChestSite(ServerLevel world, BlockPos spawnPos) {
        BlockPos fallback = null;
        for (int radius = 1; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos candidate = world.getHeightmapPos(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            new BlockPos(spawnPos.getX() + dx, world.getMinY(), spawnPos.getZ() + dz));
                    if (!isDryChestSite(world, candidate)) {
                        continue;
                    }
                    if (countSurvivableTorchSides(world, candidate) >= 2) {
                        return candidate;
                    }
                    if (fallback == null) {
                        fallback = candidate;
                    }
                }
            }
        }
        return fallback;
    }

    private static boolean isDryChestSite(ServerLevel world, BlockPos pos) {
        if (!world.getBlockState(pos).isAir() || !world.getFluidState(pos).isEmpty()) {
            return false;
        }
        BlockPos support = pos.below();
        return world.getFluidState(support).isEmpty()
                && world.getBlockState(support).isFaceSturdy(world, support, Direction.UP);
    }

    private static int countSurvivableTorchSides(ServerLevel world, BlockPos chestPos) {
        BlockState torch = Blocks.TORCH.defaultBlockState();
        int count = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos torchPos = chestPos.relative(direction);
            if (torch.canSurvive(world, torchPos) && world.getBlockState(torchPos).isAir()) {
                count++;
            }
        }
        return count;
    }

    private static void applySpawnChoice(ServerPlayer player, String id) {
        if (player.getTags().contains(SPAWN_CHOSEN_TAG)) {
            return;
        }

        if (Boolean.getBoolean("latitude.disableSpawnTeleport")) {
            // DEBUG ONLY: avoid join hitch while diagnosing spawn teleport.
            LOGGER.info("Spawn teleport disabled by latitude.disableSpawnTeleport (debug only).");
            return;
        }

        ServerLevel world = (ServerLevel) player.level();
        if (!isGlobeOverworld(world)) {
            return;
        }

        SpawnChoice spawnChoice;
        try {
            spawnChoice = resolveSpawnChoice(world, id);
        } catch (RuntimeException e) {
            LOGGER.error(
                    "[Latitude] No terrain-validated spawn was available for zone={}; keeping the player's current safe position",
                    id,
                    e);
            return;
        }
        LOGGER.info("Applying spawn choice: player={}, zoneId={}", player.getName().getString(), spawnChoice.zoneId());

        BlockPos clampedSpawnPos = spawnChoice.pos();
        world.setRespawnData(LevelData.RespawnData.of(world.dimension(), clampedSpawnPos, 0.0f, 0.0f));

        BlockPos teleportPos = clampedSpawnPos;
        player.teleportTo(world, teleportPos.getX() + 0.5, teleportPos.getY(), teleportPos.getZ() + 0.5, EnumSet.noneOf(Relative.class), player.getYRot(), player.getXRot(), true);
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.fallDistance = 0.0F;
        player.addTag(SPAWN_CHOSEN_TAG);
        LatitudeWorldState.get(world).setSpawnPickerDismissed(true);
    }

    private static SpawnChoice resolveInitialSpawnChoice(ServerLevel world, String id) {
        String zoneId = id;
        long seed = world.getServer().getWorldData().worldGenOptions().seed();
        if (zoneId != null && zoneId.equals("RANDOM")) {
            zoneId = resolveSpawnZoneId(zoneId, seed);
            LOGGER.info("Resolved RANDOM initial spawn zone: seed={}, chosen={}", seed, zoneId);
        }
        if (zoneId == null) {
            zoneId = "TEMPERATE";
        }

        int radius = LatitudeBiomes.getActiveRadiusBlocks();
        if (radius <= 0) {
            WorldBorder border = world.getWorldBorder();
            radius = (int) Math.round(com.example.globe.util.LatitudeMath.halfSize(border));
        }

        double directionChoice = hash01(seed, 1, 0, SPAWN_SALT);
        double spawnAbsLatFrac = com.example.globe.util.LatitudeMath.spawnFracForZoneKey(zoneId);
        int targetZ = (int) Math.round(radius * spawnAbsLatFrac);
        if (directionChoice < 0.5) {
            targetZ = -targetZ;
        }
        int warnStartZ = Math.max(0, radius - POLE_WARNING_DISTANCE_BLOCKS);
        int maxAbsZ = Math.max(0, warnStartZ - 500);
        targetZ = Mth.clamp(targetZ, -maxAbsZ, maxAbsZ);

        final int terrainMargin = 320;
        SpawnSafetyPolicy.CompactCohort cohort;
        try {
            SamplerTemplate template = BiomeSamplerTools.createTemplate(world);
            RandomState noiseConfig = RandomState.create(
                    template.settings().value(), template.noiseParameters(), seed);
            Climate.Sampler sampler = noiseConfig.sampler();
            int radiusBlocks = LatitudeBiomes.getActiveRadiusBlocks();
            if (radiusBlocks <= 0) {
                radiusBlocks = radius;
            }
            int classifyY = LatitudeBiomes.SURFACE_CLASSIFY_Y;
            LatitudeBiomes.setWorldSeed(seed);
            int finalRadiusBlocks = radiusBlocks;
            cohort = SpawnSafetyPolicy.compactValidationCohort(
                    radius,
                    targetZ,
                    terrainMargin,
                    EW_WARNING_DISTANCE_BLOCKS,
                    EW_SPAWN_PADDING_BLOCKS,
                    (x, z) -> isLandBiome(
                            template, sampler, x, z, classifyY, finalRadiusBlocks));
            if (cohort.terrainOnlyFallback()) {
                LOGGER.warn("[Latitude] Initial spawn biome classification failed; using the central compact terrain-only cohort.");
            }
        } catch (RuntimeException samplerFailure) {
            LOGGER.warn(
                    "[Latitude] Initial spawn biome sampler unavailable; using the central compact terrain-only cohort.",
                    samplerFailure);
            cohort = SpawnSafetyPolicy.centralCompactValidationCohort(
                    radius,
                    targetZ,
                    terrainMargin,
                    EW_WARNING_DISTANCE_BLOCKS,
                    EW_SPAWN_PADDING_BLOCKS);
        }

        BlockPos spawnPos = SpawnSafetyPolicy.firstValidatedCandidate(
                cohort.validationCandidates(),
                candidate -> {
                    try {
                        return placeSafeY(world, candidate.x(), candidate.z(), false);
                    } catch (RuntimeException validationFailure) {
                        LOGGER.warn(
                                "[Latitude] Initial spawn terrain validation failed; continuing the bounded compact cohort.",
                                validationFailure);
                        return null;
                    }
                }).orElse(null);
        if (spawnPos == null) {
            throw new InitialSpawnSelectionException(
                    "Latitude could not find a safe initial spawn in the selected climate zone after nine bounded attempts.");
        }
        return new SpawnChoice(zoneId, spawnPos, radius);
    }

    private static SpawnChoice resolveSpawnChoice(ServerLevel world, String id) {
        return resolveSpawnChoice(world, id, Integer.MAX_VALUE, true, true);
    }

    private static SpawnChoice resolveSpawnChoice(
            ServerLevel world,
            String id,
            int terrainValidationBudget,
            boolean prepareTeleportNeighbors,
            boolean allowTerrainFallback) {
        String zoneId = id;
        long seed = world.getServer().getWorldData().worldGenOptions().seed();
        if (zoneId != null && zoneId.equals("RANDOM")) {
            zoneId = resolveSpawnZoneId(zoneId, seed);
            LOGGER.info("Resolved RANDOM spawn zone: seed={}, chosen={}", seed, zoneId);
        }

        if (zoneId == null) {
            zoneId = "TEMPERATE";
        }

        int radius = LatitudeBiomes.getActiveRadiusBlocks();
        if (radius <= 0) {
            WorldBorder border = world.getWorldBorder();
            radius = (int) Math.round(com.example.globe.util.LatitudeMath.halfSize(border));
        }

        double v = hash01(seed, 1, 0, SPAWN_SALT);

        double spawnAbsLatFrac = com.example.globe.util.LatitudeMath.spawnFracForZoneKey(zoneId);
        int z = (int) Math.round(radius * spawnAbsLatFrac);
        if (v < 0.5) {
            z = -z;
        }

        int warnStartZ = Math.max(0, radius - POLE_WARNING_DISTANCE_BLOCKS);
        int maxAbsZ = Math.max(0, warnStartZ - 500);
        z = Mth.clamp(z, -maxAbsZ, maxAbsZ);

        int targetZ = z;
        BlockPos spawnPos;
        try {
            SamplerTemplate template = BiomeSamplerTools.createTemplate(world);
            RandomState noiseConfig = RandomState.create(
                    template.settings().value(), template.noiseParameters(), seed);
            Climate.Sampler sampler = noiseConfig.sampler();
            spawnPos = findLandSpawn(
                    world,
                    template,
                    sampler,
                    radius,
                    targetZ,
                    seed,
                    terrainValidationBudget,
                    prepareTeleportNeighbors);
        } catch (Exception e) {
            LOGGER.warn("[Latitude] Biome probe failed, using fallback spawn", e);
            spawnPos = null;
        }

        if (spawnPos == null && allowTerrainFallback) {
            LOGGER.warn(
                    "[Latitude] Could not find a validated biome-targeted spawn for zone={} targetZ={}; trying bounded terrain-safe fallback columns.",
                    zoneId,
                    targetZ);
            spawnPos = findSafeFallbackSpawn(world, radius, targetZ, prepareTeleportNeighbors);
        }
        if (spawnPos == null) {
            throw new IllegalStateException(
                    "No terrain-validated Latitude spawn was available for zone="
                            + zoneId
                            + " targetZ="
                            + targetZ);
        }

        return new SpawnChoice(zoneId, spawnPos, radius);
    }

    public static void logBuildMetadata(String side) {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(MOD_ID);
        String version = mod.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        String commit = "?";
        String branch = "?";
        String time = "?";
        String dirty = "?";

        if (mod.isPresent()) {
            try (InputStream is = mod.get().findPath("META-INF/MANIFEST.MF").map(path -> {
                try {
                    return java.nio.file.Files.newInputStream(path);
                } catch (Exception e) {
                    return null;
                }
            }).orElse(null)) {
                if (is != null) {
                    Manifest mf = new Manifest(is);
                    Attributes attrs = mf.getMainAttributes();
                    commit = Optional.ofNullable(attrs.getValue("Git-Commit")).orElse(commit);
                    branch = Optional.ofNullable(attrs.getValue("Git-Branch")).orElse(branch);
                    time = Optional.ofNullable(attrs.getValue("Build-Time")).orElse(time);
                    dirty = Optional.ofNullable(attrs.getValue("Build-Dirty")).orElse(dirty);
                }
            } catch (Exception ignored) {
            }
        }

        // A dev run has no packaged manifest, so every field above stays "?" and the marker cannot
        // name the source it was built from. Loom passes the same identity as system properties;
        // a release jar carries the manifest and never sees them, so the manifest still wins.
        commit = devBuildProperty(commit, "latitude.dev.gitCommit");
        branch = devBuildProperty(branch, "latitude.dev.gitBranch");
        dirty = devBuildProperty(dirty, "latitude.dev.buildDirty");
        time = devBuildProperty(time, "latitude.dev.buildTime");

        LOGGER.info("[LAT][BUILD] side={} version={} commit={} branch={} dirty={} time={}", side, version, commit, branch, dirty, time);
    }

    private static String devBuildProperty(String current, String key) {
        if (!"?".equals(current)) {
            return current;
        }
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? current : value.trim();
    }


    private static BlockPos findLandSpawn(ServerLevel world, SamplerTemplate template,
                                          Climate.Sampler sampler,
                                          int borderHalf, int targetZ, long seed,
                                          int terrainValidationBudget,
                                          boolean prepareTeleportNeighbors) {
        final int margin = 320;
        final int maxAbsX = SpawnSafetyPolicy.safeSearchMaxAbsX(
                borderHalf,
                margin,
                EW_WARNING_DISTANCE_BLOCKS,
                EW_SPAWN_PADDING_BLOCKS);
        final int maxAbsZ = Math.max(0, borderHalf - margin);

        final int samplesPerPass = 16;
        final int zJitter = 96;

        // Size-invariance: active radius is source of truth, borderHalf is fallback only.
        int radiusBlocks = LatitudeBiomes.getActiveRadiusBlocks();
        if (radiusBlocks <= 0) radiusBlocks = borderHalf;
        int classifyY = LatitudeBiomes.SURFACE_CLASSIFY_Y;

        LatitudeBiomes.setWorldSeed(seed);

        RandomSource rng = RandomSource.create(seed ^ 0x9E3779B97F4A7C15L ^ (long) targetZ);
        int terrainValidationAttempts = 0;

        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < samplesPerPass; i++) {
                int x = rng.nextIntBetweenInclusive(-maxAbsX, maxAbsX);
                int z = pass == 0
                        ? targetZ
                        : Mth.clamp(
                                targetZ + rng.nextIntBetweenInclusive(-zJitter, zJitter),
                                -maxAbsZ,
                                maxAbsZ);

                if (!isLandBiome(template, sampler, x, z, classifyY, radiusBlocks)) {
                    continue;
                }

                if (terrainValidationAttempts >= Math.max(0, terrainValidationBudget)) {
                    return null;
                }
                terrainValidationAttempts++;
                BlockPos candidate = placeSafeY(world, x, z, prepareTeleportNeighbors);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Bounded last-resort search used after both a normal no-candidate result and a biome-probe
     * exception. It may relax the requested biome family, but never the physical safety checks.
     */
    private static BlockPos findSafeFallbackSpawn(
            ServerLevel world,
            int borderHalf,
            int targetZ,
            boolean prepareTeleportNeighbors) {
        final int terrainMargin = 320;
        for (SpawnSafetyPolicy.FallbackCandidate candidate :
                SpawnSafetyPolicy.safeFallbackCandidates(
                        borderHalf,
                        targetZ,
                        terrainMargin,
                        EW_WARNING_DISTANCE_BLOCKS,
                        EW_SPAWN_PADDING_BLOCKS,
                        SpawnSafetyPolicy.FALLBACK_STEP_BLOCKS,
                        SpawnSafetyPolicy.FALLBACK_MAX_RINGS)) {
            try {
                BlockPos validated = placeSafeY(
                        world,
                        candidate.x(),
                        candidate.z(),
                        prepareTeleportNeighbors);
                if (validated != null) {
                    return validated;
                }
            } catch (RuntimeException e) {
                LOGGER.warn(
                        "[Latitude] Safe fallback terrain probe failed at x={} z={}; continuing bounded search.",
                        candidate.x(),
                        candidate.z(),
                        e);
            }
        }
        return null;
    }

    /**
     * Pure biome-source probe — no chunk generation. Returns true if the biome
     * at (blockX, blockZ) is land (not ocean or river).
     */
    private static boolean isLandBiome(SamplerTemplate template,
                                        Climate.Sampler sampler,
                                        int blockX, int blockZ,
                                        int classifyY, int radiusBlocks) {
        int noiseX = Math.floorDiv(blockX, 4);
        int noiseZ = Math.floorDiv(blockZ, 4);
        int noiseY = Math.floorDiv(classifyY, 4);

        Holder<Biome> base = template.baseSource().getNoiseBiome(noiseX, noiseY, noiseZ, sampler);
        Holder<Biome> picked = LatitudeBiomes.pick(
                template.biomeRegistry(), base,
                blockX, blockZ, classifyY, radiusBlocks,
                sampler, "SPAWN_PROBE");
        Holder<Biome> resolved = picked != null ? picked : base;

        // Tag-based checks — safe against substring false positives
        return !resolved.is(BiomeTags.IS_OCEAN) && !resolved.is(BiomeTags.IS_RIVER);
    }

    /**
     * Generates exactly one candidate chunk to get a safe spawn Y via heightmap. Only after the
     * column passes every safety check does it prepare the eight neighboring teleport chunks.
     * Returns a valid spawn BlockPos, or null if the terrain fails validation.
     */
    private static BlockPos placeSafeY(
            ServerLevel world,
            int x,
            int z,
            boolean prepareTeleportNeighbors) {
        loadSpawnTargetChunk(world, x, z);

        BlockPos spawn = world.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, world.getMinY(), z));
        BlockPos ground = spawn.below();

        // Same validation as the old tryLandAt
        if (!world.getFluidState(spawn).isEmpty()) return null;
        if (!world.getFluidState(spawn.above()).isEmpty()) return null;
        if (!world.getBlockState(spawn).isAir()) return null;
        if (!world.getBlockState(spawn.above()).isAir()) return null;
        if (!world.getFluidState(ground).isEmpty()) return null;
        BlockState groundState = world.getBlockState(ground);
        Identifier groundBlockId = BuiltInRegistries.BLOCK.getKey(groundState.getBlock());
        if (groundBlockId == null
                || SpawnSafetyPolicy.isDangerousSurfaceId(groundBlockId.toString())) {
            return null;
        }
        if (!groundState.isFaceSturdy(world, ground, Direction.UP)) return null;

        int loadedNeighborChunks = prepareTeleportNeighbors
                ? loadSpawnTargetNeighborRing(world, x, z)
                : 0;
        LOGGER.info("[Latitude] Prepared spawn target surface: x={} y={} z={} loadedTeleportChunks={}",
                x, spawn.getY(), z, loadedNeighborChunks + 1);
        return spawn;
    }

    private static void loadSpawnTargetChunk(ServerLevel world, int x, int z) {
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
    }

    private static int loadSpawnTargetNeighborRing(ServerLevel world, int x, int z) {
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        int loadedChunks = 0;
        int radius = SpawnSafetyPolicy.SPAWN_PREPARATION_NEIGHBOR_RADIUS_CHUNKS;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                world.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.FULL, true);
                loadedChunks++;
            }
        }
        return loadedChunks;
    }

    private record SpawnChoice(String zoneId, BlockPos pos, int radius) {
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double hash01(long seed, int x, int z, long salt) {
        long h = seed ^ salt;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 27);
        h *= 0x3C79AC492BA7B653L;
        h ^= (h >>> 33);
        return ((h >>> 11) * (1.0 / (1L << 53)));
    }

    private static String resolveSpawnZoneId(String selected, long seed) {
        if (selected == null || !selected.equals("RANDOM")) {
            return selected;
        }

        String[] options = {"EQUATOR", "TROPICAL", "SUBTROPICAL", "TEMPERATE", "SUBPOLAR", "POLAR"};
        long mixed = seed ^ 0x9E3779B97F4A7C15L;
        int idx = Math.floorMod(mixed, options.length);
        return options[idx];
    }

    private static boolean hasCompassAnywhere(ServerPlayer player) {
        if (player == null) return false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (containsCompass(inv.getItem(i), 0)) return true;
        }
        return false;
    }

    private static boolean containsCompass(ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.COMPASS)) return true;

        if (depth >= 6) return false;

        if (stack.is(Items.BUNDLE)) {
            BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (contents != null) {
                for (var inside : contents.items()) {
                    if (containsCompass(inside, depth + 1)) return true;
                }
            }
        }

        return false;
    }
}
