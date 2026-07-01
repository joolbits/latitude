package com.example.globe;

import net.fabricmc.api.ModInitializer;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.BiomeFeatureStripping;
import com.example.globe.world.LatitudeWorldState;
import com.example.globe.util.BiomeSamplerTools;
import com.example.globe.util.BiomeSamplerTools.SamplerTemplate;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.util.EnumSet;
import java.util.Optional;
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
    // The E/W sandstorm band onset is FRACTIONAL: particles/warning start at progress
    // POLAR_STAGE_1_PROGRESS (0.94) of the X radius, i.e. within ~6% of the border. A fixed block margin
    // (500+64) therefore never clears it — on a Tiny world (xRadius 10000) it left spawn AT the onset, and on
    // a large world it left spawn deep inside the sandstorm. Spawn must land below this fraction of xRadius.
    private static final double EW_SPAWN_SAFE_MARGIN_FRAC = 0.08;
    private static final long SPAWN_SALT = 0x7A3E21B5D4C1F7A9L;

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

    private static PolarCapScrubber POLAR_SCRUBBER;

    private static final boolean ENABLE_POLAR_SCRUBBER = false;


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

    @Override
    public void onInitialize() {
        LOGGER.info("{} initialized. Use the globe:globe world preset for deterministic terrain.", MOD_ID);

        logBuildMetadata("server");

        GlobeNet.registerPayloads();
        BiomeFeatureStripping.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("flyspeed")
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                int level = IntegerArgumentType.getInteger(ctx, "level");
                                float speed = 0.05f * (float) level;
                                player.getAbilities().setFlyingSpeed(speed);
                                player.onUpdateAbilities();
                                ctx.getSource().sendSuccess(() -> Component.literal("Fly speed set to " + level), false);
                                return 1;
                            })));

            registerDevOnlyCommand(dispatcher);
            // Shippable /latdev subset (band teleport + here-readout) for testers, opt-in via
            // -Dlatitude.devCommands=true; no-op in dev (the full command owns /latdev) or without the flag.
            LatitudeDevCommands.registerIfEnabled(dispatcher);
        });

        // Initialize province authority at world-load time, before spawn-chunk generation fires
        // for brand-new worlds. SERVER_STARTED fires too late (after spawn chunks are pregenerated).
        ServerLevelEvents.LOAD.register(GlobeMod::initLatitudeBiomesForWorld);
        ServerLifecycleEvents.SERVER_STARTED.register(GlobeMod::applyWorldBorder);
        registerDevOnlyHeadlessRunner();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            POLAR_SCRUBBER = null;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerLevel overworld = server.overworld();
            if (overworld == null) {
                return;
            }

            boolean isGlobe = isGlobeOverworld(overworld);
            int latitudeZRadius = isGlobe ? LatitudeBiomes.getActiveRadiusBlocks() : 0;
            LOGGER.info("JOIN: player={}, isGlobeOverworld={}, latitudeZRadius={}",
                    handler.player.getName().getString(), isGlobe, latitudeZRadius);
            ServerPlayNetworking.send(handler.player, new GlobeNet.GlobeStatePayload(isGlobe, latitudeZRadius));

            LatitudeWorldState worldState = LatitudeWorldState.get(overworld);
            boolean isBrandNewWorld = overworld.getGameTime() < 100L;
            boolean spawnAlreadyChosen = handler.player.entityTags().contains(SPAWN_CHOSEN_TAG);

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
                // Legacy post-load spawn picker path is no longer used. Apply a spawn choice immediately
                // (pending value from bespoke flow when present, otherwise fall back to TEMPERATE) and
                // mark the picker dismissed so the old menu cannot reopen on first load or crash recovery.
                String zoneToApply = pendingZone != null ? pendingZone : "TEMPERATE";

                if (pendingZone == null) {
                    LOGGER.info("No pending spawn zone from bespoke flow; defaulting to TEMPERATE and suppressing legacy picker");
                }

                applySpawnChoice(handler.player, zoneToApply);
                worldState.setSpawnPickerDismissed(true);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(GlobeNet.SetSpawnPickerPayload.ID, (payload, context) -> {
            context.server().execute(() -> applySpawnChoice(context.player(), payload.zoneId()));
        });

        ServerTickEvents.END_SERVER_TICK.register(GlobeMod::borderUxTick);
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

        LatitudeWorldState worldState = LatitudeWorldState.get(world);
        int pendingRadius = GlobePending.pendingGlobeRadius;
        GlobePending.pendingGlobeRadius = 0;
        if (worldState.getGlobeRadius() <= 0 && pendingRadius > 0 && world.getGameTime() < 100L) {
            worldState.setGlobeRadius(pendingRadius);
            LOGGER.info("[Latitude] Recorded Globe world: border radius {} (from create-world selection)", pendingRadius);
        }

        // World shape stamping. The bespoke create-world screen is the ONLY way to make a Globe world, and it
        // ALWAYS sets GlobePending.pendingGlobeShape (Mercator by default, or Legacy 1:1 if the toggle chose
        // it). So a non-null pendingShape is the reliable "brand-new world" signal; existing/legacy saves never
        // set it and are never re-stamped. We also require the persisted shape to be genuinely unset
        // (hasGlobeShape() == false) so a stale pending value can't overwrite an already-stamped world.
        //
        // This replaces the old `gameTime < 100 && "classic".equals(getGlobeShape())` guard, which could
        // silently flip an existing SQUARE (Classic/pre-2.0) save to Mercator: an absent globe_shape field used
        // to deserialize to the concrete "classic", so a legacy save was indistinguishable from an explicitly-
        // Legacy one, and any existing world that happened to load with gameTime < 100 got re-stamped to
        // Mercator — enlarging its border from 2*zRadius to 4*zRadius (bug-catcher #1). globe_shape now has a
        // real unset sentinel (Optional, no default), so legacy saves stay Classic.
        String pendingShape = GlobePending.pendingGlobeShape;
        GlobePending.pendingGlobeShape = null;
        if (pendingShape != null && !worldState.hasGlobeShape()) {
            worldState.setGlobeShape(pendingShape);
            LOGGER.info("[Latitude] New Globe world: shape={} (from create-world selection)", pendingShape);
        }
        // Ensure the live cache reflects the persisted shape before the border is sized (covers existing worlds).
        LatitudeBiomes.setGlobeShape(LatitudeBiomes.shapeFromString(worldState.getGlobeShape()));

        if (!isGlobeOverworld(world)) {
            return;
        }
        long seed = server.getWorldGenSettings().options().seed();
        int radius = borderRadiusForGlobeOverworld(world);
        // Radius first: ensures rebuildProvinceAuthority() has a valid radius when the seed fires.
        LatitudeBiomes.setRadius(radius);
        LatitudeBiomes.setWorldSeed(seed);
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

        LatitudeWorldState.get(overworld);

        int borderRadiusBlocks = borderRadiusForGlobeOverworld(overworld);
        // Radius must be set before seed so rebuildProvinceAuthority() builds the authority
        // atomically the moment the seed is available (not on the next setRadius call).
        LatitudeBiomes.setRadius(borderRadiusBlocks);

        long seed = overworld.getServer().getWorldGenSettings().options().seed();
        LatitudeBiomes.setWorldSeed(seed);

        setGlobeBorder(overworld, borderRadiusBlocks);
    }

    private static void setGlobeBorder(ServerLevel overworld, int borderRadiusBlocks) {
        WorldBorder border = overworld.getWorldBorder();
        // borderRadiusBlocks is the Z (latitude) radius, e.g. 3750 / 5000 / 7500.
        int zRadius = borderRadiusBlocks;
        // Mercator worlds are 2:1: the playable X extent is ASPECT * the Z radius. Minecraft's WorldBorder
        // is square-only, so we size the square border to the WIDER (X) axis; the N/S poles stay interior
        // at |Z| = zRadius (enforced by the pole hazard band), and the E-W storm wall lands at the X edge.
        int xRadius = LatitudeBiomes.isMercator()
                ? (int) Math.round(zRadius * LatitudeBiomes.MERCATOR_ASPECT)
                : zRadius;
        double diameter = xRadius * 2.0;
        border.setCenter(0.0, 0.0);
        border.setSize(diameter);

        // Latitude authority stays the Z radius (poles at |Z| = zRadius, NOT at the X border edge).
        LatitudeBiomes.setRadius(zRadius);
        LatitudeBiomes.setActiveRadiusBlocks(zRadius);
        // Tell the latitude math the Z radius so HUD/zone/pole calcs divide Z by zRadius (not the X border half).
        com.example.globe.util.LatitudeMath.setLatitudeZRadius(zRadius);
        LOGGER.info("[Latitude] Radius Sync: shape={} zRadius={} xRadius={} borderDiameter={} ACTIVE_RADIUS_BLOCKS={}",
                LatitudeBiomes.getGlobeShape(), zRadius, xRadius, diameter, LatitudeBiomes.getActiveRadiusBlocks());

        // Pole hazard band starts at a fraction of the Z radius (the geographic pole), interior to the border.
        activePoleBandStartAbsZ = (int) Math.round(zRadius * com.example.globe.util.LatitudeMath.POLAR_START_FRAC);
        POLAR_SCRUBBER = ENABLE_POLAR_SCRUBBER ? new PolarCapScrubber(zRadius, activePoleBandStartAbsZ) : null;

        GlobeMod.LOGGER.info("[Latitude] WorldBorder set: shape={} zRadius={} xRadius={} diameter={} center=0,0 polarStart={}",
                LatitudeBiomes.getGlobeShape(), zRadius, xRadius, diameter, activePoleBandStartAbsZ);
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
        if ((worldTime % 10L) != 0L) {
            return;
        }

        WorldBorder border = overworld.getWorldBorder();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != overworld) {
                continue;
            }

            double progressZ = com.example.globe.util.LatitudeMath.hazardProgressZ(border, player.getZ());
            int stageIndex = com.example.globe.util.LatitudeMath.hazardStageIndex(border, player.getZ(), progressZ);

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
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, ambient, showParticles, showIcon));

                int max = 140;
                int target = (int) Math.floor(max * 0.85);
                if (target < 1) {
                    target = 1;
                }
                player.setTicksFrozen(Math.max(player.getTicksFrozen(), target));
            }
        }
    }

    private static void applyContinuousBlindness(ServerPlayer player, boolean inFinalWhiteout) {
        if (!inFinalWhiteout) {
            return;
        }

        MobEffectInstance cur = player.getEffect(MobEffects.BLINDNESS);
        if (cur == null || cur.getDuration() < 80) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0, true, false, false));
        }
    }

    private static boolean isGlobeOverworld(ServerLevel world) {
        if (LatitudeWorldState.get(world).getGlobeRadius() > 0) {
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

    private static boolean hasInlineSettings(NoiseBasedChunkGenerator noise) {
        Holder<NoiseGeneratorSettings> settings = noise != null ? noise.generatorSettings() : null;
        return settings != null && settings.unwrapKey().isEmpty();
    }

    public static boolean shouldApplyLatitudeWorldgen(NoiseBasedChunkGenerator noise) {
        if (isGlobeNoiseGenerator(noise)) {
            return true;
        }
        return LatitudeBiomes.getActiveRadiusBlocks() > 0 && hasInlineSettings(noise);
    }

    private static int borderRadiusForGlobeOverworld(ServerLevel world) {
        int persisted = LatitudeWorldState.get(world).getGlobeRadius();
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
        if (hasInlineSettings(noise) && LatitudeBiomes.getActiveRadiusBlocks() > 0) {
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
            SpawnChoice spawnChoice = resolveSpawnChoice(world, pendingZone);
            BlockPos spawnPos = spawnChoice.pos();
            if (loadListener != null) {
                loadListener.start(LevelLoadListener.Stage.PREPARE_GLOBAL_SPAWN, 0);
                loadListener.updateFocus(world.dimension(), ChunkPos.containing(spawnPos));
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
            LOGGER.warn("[Latitude] Early initial spawn failed; falling back to vanilla initial spawn", e);
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
        if (player.entityTags().contains(SPAWN_CHOSEN_TAG)) {
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

        SpawnChoice spawnChoice = resolveSpawnChoice(world, id);
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

    private static SpawnChoice resolveSpawnChoice(ServerLevel world, String id) {
        String zoneId = id;
        long seed = world.getServer().getWorldGenSettings().options().seed();
        if (zoneId != null && zoneId.equals("RANDOM")) {
            zoneId = resolveSpawnZoneId(zoneId, seed);
            LOGGER.info("Resolved RANDOM spawn zone: seed={}, chosen={}", seed, zoneId);
        }

        if (zoneId == null) {
            zoneId = "TEMPERATE";
        }

        int radius = LatitudeBiomes.getActiveRadiusBlocks();   // Z (latitude) radius
        if (radius <= 0) {
            WorldBorder border = world.getWorldBorder();
            radius = (int) Math.round(com.example.globe.util.LatitudeMath.halfSize(border));
        }
        int xRadius = LatitudeBiomes.getActiveXRadiusBlocks();  // X (E-W) radius: wider in Mercator
        if (xRadius <= 0) xRadius = radius;

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
            spawnPos = findLandSpawn(world, template, sampler, xRadius, radius, targetZ, seed);
        } catch (Exception e) {
            LOGGER.warn("[Latitude] Biome probe failed, using fallback spawn", e);
            spawnPos = null;
        }

        if (spawnPos == null) {
            LOGGER.warn("[Latitude] Could not find land spawn for zone={} targetZ={}. Falling back to (0, seaLevel+2).", zoneId, targetZ);
            spawnPos = new BlockPos(0, world.getSeaLevel() + 2, targetZ);
        }

        return new SpawnChoice(zoneId, clampSpawnAwayFromEwWarning(spawnPos, xRadius), radius);
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

    private static BlockPos clampSpawnAwayFromEwWarning(BlockPos spawnPos, int xRadiusBlocks) {
        if (spawnPos == null || xRadiusBlocks <= 0) {
            return spawnPos;
        }

        int absX = Math.abs(spawnPos.getX());
        // Keep spawn a safe FRACTION inside the sandstorm onset (0.94 of xRadius), not just a fixed block
        // margin — the onset scales with world width, so a fixed margin fails on every size. Take the more
        // restrictive of the fractional-safe cap and the legacy fixed cap.
        double safeFrac = Math.max(0.0, com.example.globe.util.LatitudeMath.POLAR_STAGE_1_PROGRESS - EW_SPAWN_SAFE_MARGIN_FRAC);
        int fractionalSafe = (int) Math.round(safeFrac * xRadiusBlocks);
        int fixedSafe = Math.max(0, xRadiusBlocks - EW_WARNING_DISTANCE_BLOCKS - EW_SPAWN_PADDING_BLOCKS);
        int safeMaxAbsX = Math.min(fractionalSafe, fixedSafe);
        if (absX <= safeMaxAbsX) {
            return spawnPos;
        }

        int clampedX = spawnPos.getX() >= 0 ? safeMaxAbsX : -safeMaxAbsX;
        return new BlockPos(clampedX, spawnPos.getY(), spawnPos.getZ());
    }

    private static BlockPos findLandSpawn(ServerLevel world, SamplerTemplate template,
                                          Climate.Sampler sampler,
                                          int xRadius, int zRadius, int targetZ, long seed) {
        final int margin = 320;
        // X is drawn over the (wider, in Mercator) E-W extent; Z-jitter is clamped to the latitude extent.
        final int maxX = Math.max(0, xRadius - margin);
        final int maxZ = Math.max(0, zRadius - margin);

        final int samplesPerPass = 16;
        final int zJitter = 96;

        // Size-invariance: active radius (Z) is source of truth for the latitude probe; zRadius is fallback.
        int radiusBlocks = LatitudeBiomes.getActiveRadiusBlocks();
        if (radiusBlocks <= 0) radiusBlocks = zRadius;
        int classifyY = LatitudeBiomes.SURFACE_CLASSIFY_Y;

        LatitudeBiomes.setWorldSeed(seed);

        RandomSource rng = RandomSource.create(seed ^ 0x9E3779B97F4A7C15L ^ (long) targetZ);

        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < samplesPerPass; i++) {
                int x = rng.nextIntBetweenInclusive(-maxX, maxX);
                int z = pass == 0
                        ? targetZ
                        : Mth.clamp(targetZ + rng.nextIntBetweenInclusive(-zJitter, zJitter), -maxZ, maxZ);

                if (!isLandBiome(template, sampler, x, z, classifyY, radiusBlocks)) {
                    continue;
                }

                BlockPos candidate = placeSafeY(world, x, z);
                if (candidate != null) {
                    return candidate;
                }
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
     * Generates exactly ONE chunk to get a safe spawn Y via heightmap.
     * Returns a valid spawn BlockPos, or null if the terrain fails validation.
     */
    private static BlockPos placeSafeY(ServerLevel world, int x, int z) {
        int loadedChunks = loadSpawnTargetChunkRing(world, x, z);

        BlockPos ground = world.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, world.getMinY(), z));
        BlockPos spawn = ground.above();

        // Same validation as the old tryLandAt
        if (!world.getFluidState(spawn).isEmpty()) return null;
        if (!world.getFluidState(spawn.above()).isEmpty()) return null;
        if (!world.getBlockState(spawn).isAir()) return null;
        if (!world.getBlockState(spawn.above()).isAir()) return null;
        if (!world.getFluidState(ground).isEmpty()) return null;

        LOGGER.info("[Latitude] Prepared spawn target surface: x={} y={} z={} loadedTeleportChunks={}",
                x, spawn.getY(), z, loadedChunks);
        return spawn;
    }

    private static int loadSpawnTargetChunkRing(ServerLevel world, int x, int z) {
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        int loadedChunks = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
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
                    if (containsCompass(inside.create(), depth + 1)) return true;
                }
            }
        }

        return false;
    }
}
