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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
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

        GlobeNet.registerPayloads();
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
            LOGGER.info("JOIN: player={}, isGlobeOverworld={}", handler.player.getName().getString(), isGlobe);
            ServerPlayNetworking.send(handler.player, new GlobeNet.GlobeStatePayload(isGlobe));

            LatitudeWorldState worldState = isGlobe ? LatitudeWorldState.get(overworld) : null;
            boolean isBrandNewWorld = overworld.getGameTime() < 100L;
            boolean spawnAlreadyChosen = handler.player.getTags().contains(SPAWN_CHOSEN_TAG);

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
                // Initial creation either set a terrain-validated Latitude spawn or intentionally
                // delegated to vanilla's safe-spawn routine. Never repeat a synchronous globe scan
                // on the first player join: that can strand the client on the loading overlay.
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

        if (!isGlobeOverworld(world)) {
            // pendingRadius > 0 means the Latitude create screen just launched this world as a
            // Latitude world — and yet the loaded overworld is not recognisable as one. This is the
            // silent-degradation case: some worldgen mod rebuilt the noise-settings registry during
            // vanilla's datapack reload inside createLevelFromExistingSettings, so the settings
            // holder no longer serialises as a globe: registry reference and the world saved as
            // vanilla terrain data (CliffTree 3.2.1 on 26.1.x does this). Every pre-launch check
            // passes — the breakage only becomes observable here. Fail loudly instead of handing
            // the player a vanilla world behind a Latitude loading screen.
            if (pendingRadius > 0) {
                throw new IllegalStateException(
                        "Latitude created this world, but the loaded overworld generator is not a "
                        + "Latitude globe generator. Another world generation mod rebuilt the "
                        + "noise-settings registry during world creation, so the world would save as "
                        + "vanilla terrain and Latitude would stay disabled in it. Remove or disable "
                        + "the conflicting world generation mod (known: CliffTree) and create the "
                        + "world again. This world save is not a Latitude world and can be deleted.");
            }
            activeLatitudeOverworldGenerator = null;
            LatitudeBiomes.clearWorldgenContext();
            return;
        }
        LatitudeWorldState worldState = LatitudeWorldState.get(world);
        long seed = server.getWorldData().worldGenOptions().seed();
        if (worldState.getGlobeRadius() <= 0 && pendingRadius > 0 && world.getGameTime() < 100L) {
            worldState.setGlobeRadius(pendingRadius);
            BiomeSelectionProfile profile = BiomeSelectionProfile.capture(
                    world.registryAccess().lookupOrThrow(Registries.BIOME).keySet().stream()
                            .map(Identifier::toString).toList());
            worldState.setProviderTicketProfile(profile);
            worldState.setVanillaRepresentationProfile(
                    VanillaBiomeRepresentationProfile.capture(pendingRadius, seed, profile));
            worldState.setCaveRepresentationProfile(
                    CaveBiomeRepresentationProfile.capture(pendingRadius, profile));
            worldState.setWorldgenPolicy(
                    LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE);
            LOGGER.info("[Latitude] Recorded Globe world: border radius {} (from create-world selection)", pendingRadius);
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
     * world-selection list can show it the next time this save is opened. Called on a throttled
     * tick cadence while playing and once more, authoritatively, on disconnect.
     */
    private static void recordLastKnownBand(ServerLevel overworld, WorldBorder border, ServerPlayer player) {
        double absLatDeg = com.example.globe.util.LatitudeMath.absLatDegExact(border, player.getZ());
        com.example.globe.util.LatitudeBands.Band band = com.example.globe.util.LatitudeBands.fromAbsoluteLatitudeDeg(absLatDeg);
        LatitudeWorldState.get(overworld).setLastKnownBandId(band.id());
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
        } catch (RuntimeException e) {
            // No unchecked Latitude coordinate is returned. Vanilla now performs its own normal
            // safe-spawn selection, and the first join must not retry the expensive globe search.
            LatitudeWorldState.get(world).setSpawnPickerDismissed(true);
            LOGGER.warn("[Latitude] Immediate terrain-validated initial spawn unavailable; delegating to vanilla safe spawn without post-join relocation", e);
            return false;
        }
    }

    /**
     * Place the vanilla bonus chest at the Latitude globe spawn. We cancel vanilla setInitialSpawn
     * (which would place it at the vanilla-computed spawn), so this re-creates that placement at our
     * zone-based spawn. Mirrors vanilla MinecraftServer.setInitialSpawn's bonus-chest block.
     */
    private static void placeLatitudeBonusChest(ServerLevel world, BlockPos spawnPos) {
        try {
            // Ensure the spawn chunk is loaded so feature placement actually writes (vanilla's
            // getSpawnHeight loads it; our spawn path may not have).
            world.getChunk(spawnPos);
            world.registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                    .get(net.minecraft.data.worldgen.features.MiscOverworldFeatures.BONUS_CHEST)
                    .ifPresent(ref -> ref.value().place(
                            world, world.getChunkSource().getGenerator(), world.getRandom(), spawnPos));
            LOGGER.info("[Latitude] Placed bonus chest at globe spawn x={} y={} z={}",
                    spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
        } catch (Throwable t) {
            LOGGER.warn("[Latitude] Failed to place bonus chest at globe spawn (continuing without)", t);
        }
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
        return resolveSpawnChoice(
                world,
                id,
                SpawnSafetyPolicy.INITIAL_SPAWN_TERRAIN_VALIDATION_BUDGET,
                false,
                true);
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

        LOGGER.info("[LAT][BUILD] side={} version={} commit={} branch={} dirty={} time={}", side, version, commit, branch, dirty, time);
    }

    /**
     * Short, human-readable build identity for on-screen display (title screen watermark) — the
     * same manifest fields {@link #logBuildMetadata} logs, so what a tester sees on screen and what
     * the jar actually reports never diverge. Absent manifest data (a dev-classpath run, not a
     * packaged jar) degrades to just the mod version rather than showing "?" placeholders.
     */
    public static String buildLabel() {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(MOD_ID);
        String version = mod.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        String commit = null;
        String dirty = null;

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
                    commit = attrs.getValue("Git-Commit");
                    dirty = attrs.getValue("Build-Dirty");
                }
            } catch (Exception ignored) {
            }
        }

        StringBuilder label = new StringBuilder("Latitude ").append(version);
        if (commit != null && commit.length() >= 7) {
            label.append(" (").append(commit, 0, 7);
            if ("true".equals(dirty)) {
                label.append(", dirty");
            }
            label.append(')');
        }
        return label.toString();
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
