package com.example.globe;

import net.fabricmc.api.ModInitializer;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.BiomeFeatureStripping;
import com.example.globe.world.LatitudeWorldState;
import com.example.globe.dev.BiomePreviewHeadlessRunner;
import com.example.globe.dev.BiomeSamplerTools;
import com.example.globe.dev.BiomeSamplerTools.SamplerTemplate;
import com.example.globe.dev.LatitudeDevCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
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
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelData;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.util.EnumSet;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

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
    private static final int EW_SPAWN_PADDING_BLOCKS = 64;
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

            LatitudeDevCommand.register(dispatcher);
        });

        // Initialize province authority at world-load time, before spawn-chunk generation fires
        // for brand-new worlds. SERVER_STARTED fires too late (after spawn chunks are pregenerated).
        ServerWorldEvents.LOAD.register(GlobeMod::initLatitudeBiomesForWorld);
        ServerLifecycleEvents.SERVER_STARTED.register(GlobeMod::applyWorldBorder);
        BiomePreviewHeadlessRunner.register();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            POLAR_SCRUBBER = null;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerLevel overworld = server.overworld();
            if (overworld == null) {
                return;
            }

            boolean isGlobe = isGlobeOverworld(overworld);
            LOGGER.info("JOIN: player={}, isGlobeOverworld={}", handler.player.getName().getString(), isGlobe);
            ServerPlayNetworking.send(handler.player, new GlobeNet.GlobeStatePayload(isGlobe));

            LatitudeWorldState worldState = LatitudeWorldState.get(overworld);
            boolean isBrandNewWorld = overworld.getGameTime() < 100L;
            boolean spawnAlreadyChosen = handler.player.getCommandTags().contains(SPAWN_CHOSEN_TAG);

            String pendingZone = server.isDedicatedServer() ? null : GlobePending.consume();

            boolean startWithCompass = !server.isDedicatedServer() && GlobePending.startWithCompass;
            if (isGlobe && !server.isDedicatedServer() && !StartCompass.hasReceived(handler.player)) {
                if (!startWithCompass) {
                    StartCompass.markReceived(handler.player);
                } else if (hasCompassAnywhere(handler.player)) {
                    StartCompass.markReceived(handler.player);
                } else {
                    boolean given = handler.player.giveItemStack(new ItemStack(Items.COMPASS));
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
        if (!isGlobeOverworld(world)) {
            return;
        }
        long seed = server.getWorldData().worldGenOptions().seed();
        int radius = borderRadiusForGlobeOverworld(world);
        // Radius first: ensures rebuildProvinceAuthority() has a valid radius when the seed fires.
        LatitudeBiomes.setRadius(radius);
        LatitudeBiomes.setWorldSeed(seed);
        LOGGER.info("[Latitude] Early init: province authority seeded before spawn-chunk generation (seed={} radius={})", seed, radius);
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

        long seed = overworld.getServer().getWorldData().worldGenOptions().seed();
        LatitudeBiomes.setWorldSeed(seed);

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
        POLAR_SCRUBBER = ENABLE_POLAR_SCRUBBER ? new PolarCapScrubber(activeRadius, activePoleBandStartAbsZ) : null;

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
        if ((worldTime % 10L) != 0L) {
            return;
        }

        WorldBorder border = overworld.getWorldBorder();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != overworld) {
                continue;
            }

            double progressZ = com.example.globe.util.LatitudeMath.hazardProgress(border, player.getZ());
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
        ChunkGenerator gen = world.getChunkSource().getGenerator();
        if (!(gen instanceof NoiseBasedChunkGenerator noise)) return false;

        return noise.stable(GLOBE_SETTINGS_KEY)
                || noise.stable(GLOBE_SETTINGS_XSMALL_KEY)
                || noise.stable(GLOBE_SETTINGS_SMALL_KEY)
                || noise.stable(GLOBE_SETTINGS_REGULAR_KEY)
                || noise.stable(GLOBE_SETTINGS_LARGE_KEY)
                || noise.stable(GLOBE_SETTINGS_MASSIVE_KEY);
    }

    private static int borderRadiusForGlobeOverworld(ServerLevel world) {
        ChunkGenerator gen = world.getChunkSource().getGenerator();
        if (!(gen instanceof NoiseBasedChunkGenerator noise)) return BORDER_RADIUS;

        if (noise.stable(GLOBE_SETTINGS_KEY)) return 15000;
        if (noise.stable(GLOBE_SETTINGS_XSMALL_KEY)) return 3750;
        if (noise.stable(GLOBE_SETTINGS_SMALL_KEY)) return 5000;
        if (noise.stable(GLOBE_SETTINGS_REGULAR_KEY)) return BORDER_RADIUS;
        if (noise.stable(GLOBE_SETTINGS_LARGE_KEY)) return 10000;
        if (noise.stable(GLOBE_SETTINGS_MASSIVE_KEY)) return 20000;

        return BORDER_RADIUS;
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

        String zoneId = id;
        if (zoneId != null && zoneId.equals("RANDOM")) {
            long seed = world.getServer().getWorldData().worldGenOptions().seed();
            zoneId = resolveSpawnZoneId(zoneId, seed);
            LOGGER.info("Resolved RANDOM spawn zone: player={}, seed={}, chosen={}", player.getName().getString(), seed, zoneId);
        }

        if (zoneId == null) {
            zoneId = "TEMPERATE";
        }

        LOGGER.info("Applying spawn choice: player={}, zoneId={}", player.getName().getString(), zoneId);

        int radius = LatitudeBiomes.getActiveRadiusBlocks();
        if (radius <= 0) {
            WorldBorder border = world.getWorldBorder();
            radius = (int) Math.round(com.example.globe.util.LatitudeMath.halfSize(border));
        }

        long seed = world.getServer().getWorldData().worldGenOptions().seed();
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

        // Biome-probe spawn finding: sample noise to find land X,Z (no chunk gen),
        // then generate only the chosen chunk for safe Y placement.
        BlockPos spawnPos;
        try {
            SamplerTemplate template = BiomeSamplerTools.createTemplate(world);
            RandomState noiseConfig = RandomState.create(
                    template.settings().value(), template.noiseParameters(), seed);
            Climate.Sampler sampler = noiseConfig.sampler();
            spawnPos = findLandSpawn(world, template, sampler, radius, targetZ, seed);
        } catch (Exception e) {
            LOGGER.warn("[Latitude] Biome probe failed, using fallback spawn", e);
            spawnPos = null;
        }

        if (spawnPos == null) {
            LOGGER.warn("[Latitude] Could not find land spawn for zone={} targetZ={}. Falling back to (0, seaLevel+2).", zoneId, targetZ);
            spawnPos = new BlockPos(0, world.getSeaLevel() + 2, targetZ);
        }

        BlockPos clampedSpawnPos = clampSpawnAwayFromEwWarning(spawnPos, radius);
        world.setRespawnData(LevelData.RespawnData.of(world.dimension(), clampedSpawnPos, 0.0f, 0.0f));

        BlockPos teleportPos = clampSpawnAwayFromEwWarning(clampedSpawnPos, radius);
        player.teleportTo(world, teleportPos.getX() + 0.5, teleportPos.getY(), teleportPos.getZ() + 0.5, EnumSet.noneOf(Relative.class), player.getYRot(), player.getXRot(), true);
        player.addTag(SPAWN_CHOSEN_TAG);
        LatitudeWorldState.get(world).setSpawnPickerDismissed(true);
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

    private static BlockPos clampSpawnAwayFromEwWarning(BlockPos spawnPos, int radiusBlocks) {
        if (spawnPos == null || radiusBlocks <= 0) {
            return spawnPos;
        }

        int warningStartX = (int) Math.round(radiusBlocks * com.example.globe.util.LatitudeMath.POLAR_STAGE_1_PROGRESS);
        if (warningStartX <= 0) {
            return spawnPos;
        }

        int absX = Math.abs(spawnPos.getX());
        if (absX < warningStartX) {
            return spawnPos;
        }

        int clampedAbsX = Math.max(0, warningStartX - EW_SPAWN_PADDING_BLOCKS);
        int clampedX = spawnPos.getX() >= 0 ? clampedAbsX : -clampedAbsX;
        return new BlockPos(clampedX, spawnPos.getY(), spawnPos.getZ());
    }

    private static BlockPos findLandSpawn(ServerLevel world, SamplerTemplate template,
                                          Climate.Sampler sampler,
                                          int borderHalf, int targetZ, long seed) {
        final int margin = 320;
        final int max = Math.max(0, borderHalf - margin);

        // First pass: vary X only (stay exactly in the selected latitude line)
        final int attemptsXOnly = 96;

        // Second pass: if still failing, allow small Z jitter while staying near the band
        final int attemptsWithZJitter = 96;
        final int zJitter = 96;

        // Size-invariance: active radius is source of truth, borderHalf is fallback only.
        int radiusBlocks = LatitudeBiomes.getActiveRadiusBlocks();
        if (radiusBlocks <= 0) radiusBlocks = borderHalf;
        int classifyY = LatitudeBiomes.SURFACE_CLASSIFY_Y;

        LatitudeBiomes.setWorldSeed(seed);

        RandomSource rng = RandomSource.create(seed ^ 0x9E3779B97F4A7C15L ^ (long) targetZ);

        // Pass 1: X-only — biome probe then single-chunk Y placement
        for (int i = 0; i < attemptsXOnly; i++) {
            int x = rng.nextIntBetweenInclusive(-max, max);
            int z = targetZ;

            if (!isLandBiome(template, sampler, x, z, classifyY, radiusBlocks)) continue;
            BlockPos candidate = placeSafeY(world, x, z);
            if (candidate != null) return candidate;
        }

        // Pass 2: X + small Z jitter
        for (int i = 0; i < attemptsWithZJitter; i++) {
            int x = rng.nextIntBetweenInclusive(-max, max);
            int z = Mth.clamp(targetZ + rng.nextIntBetweenInclusive(-zJitter, zJitter), -max, max);

            if (!isLandBiome(template, sampler, x, z, classifyY, radiusBlocks)) continue;
            BlockPos candidate = placeSafeY(world, x, z);
            if (candidate != null) return candidate;
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
        world.getChunk(x >> 4, z >> 4);

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

        return spawn;
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
                for (ItemStack inside : contents.items()) {
                    if (containsCompass(inside, depth + 1)) return true;
                }
            }
        }

        return false;
    }
}
