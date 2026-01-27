package com.example.globe;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.WorldProperties;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.util.EnumSet;

public class GlobeMod implements ModInitializer {
    public static final String MOD_ID = "globe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String SPAWN_CHOSEN_TAG = "globe_spawn_chosen";

    public static final int BORDER_RADIUS = 7500;
    public static final int POLE_BAND_START_ABS_Z = 12000;
    public static final int POLE_WARNING_DISTANCE_BLOCKS = 256;
    public static final int POLE_LETHAL_DISTANCE_BLOCKS = 96;
    public static final int POLE_LETHAL_WARNING_DISTANCE = POLE_WARNING_DISTANCE_BLOCKS;
    public static final int EFFECT_REFRESH_TICKS = 20;

    public static final int POLE_START = POLE_BAND_START_ABS_Z;

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

    private static final Identifier GLOBE_SETTINGS_ID = Identifier.of(MOD_ID, "overworld");
    private static final Identifier GLOBE_SETTINGS_XSMALL_ID = Identifier.of(MOD_ID, "overworld_xsmall");
    private static final Identifier GLOBE_SETTINGS_SMALL_ID = Identifier.of(MOD_ID, "overworld_small");
    private static final Identifier GLOBE_SETTINGS_REGULAR_ID = Identifier.of(MOD_ID, "overworld_regular");

    private static final Identifier GLOBE_SETTINGS_LARGE_ID = Identifier.of(MOD_ID, "overworld_large");
    private static final Identifier GLOBE_SETTINGS_MASSIVE_ID = Identifier.of(MOD_ID, "overworld_massive");

    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_KEY = RegistryKey.of(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_ID);
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_XSMALL_KEY = RegistryKey.of(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_XSMALL_ID);
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_SMALL_KEY = RegistryKey.of(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_SMALL_ID);
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_REGULAR_KEY = RegistryKey.of(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_REGULAR_ID);

    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_LARGE_KEY = RegistryKey.of(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_LARGE_ID);
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_MASSIVE_KEY = RegistryKey.of(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_MASSIVE_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("{} initialized. Use the globe:globe world preset for deterministic terrain.", MOD_ID);

        GlobeNet.registerPayloads();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flyspeed")
                    .then(CommandManager.argument("level", IntegerArgumentType.integer(1, 5))
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                int level = IntegerArgumentType.getInteger(ctx, "level");
                                float speed = 0.05f * (float) level;
                                player.getAbilities().setFlySpeed(speed);
                                player.sendAbilitiesUpdate();
                                ctx.getSource().sendFeedback(() -> Text.literal("Fly speed set to " + level), false);
                                return 1;
                            })));
        });

        ServerLifecycleEvents.SERVER_STARTED.register(GlobeMod::applyWorldBorder);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            POLAR_SCRUBBER = null;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerWorld overworld = server.getOverworld();
            if (overworld == null) {
                return;
            }

            boolean isGlobe = isGlobeOverworld(overworld);
            LOGGER.info("JOIN: player={}, isGlobeOverworld={}", handler.player.getName().getString(), isGlobe);
            ServerPlayNetworking.send(handler.player, new GlobeNet.GlobeStatePayload(isGlobe));

            String pendingZone = server.isDedicated() ? null : GlobePending.consume();

            boolean startWithCompass = !server.isDedicated() && GlobePending.startWithCompass;
            if (isGlobe && !server.isDedicated() && !StartCompass.hasReceived(handler.player)) {
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

            if (isGlobe && !handler.player.getCommandTags().contains(SPAWN_CHOSEN_TAG)) {
                if (pendingZone != null) {
                    applySpawnChoice(handler.player, pendingZone);
                }

                if (!handler.player.getCommandTags().contains(SPAWN_CHOSEN_TAG)) {
                    LOGGER.info("Sending spawn picker open to player={}", handler.player.getName().getString());
                    ServerPlayNetworking.send(handler.player, new GlobeNet.OpenSpawnPickerPayload(true));
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(GlobeNet.SetSpawnPickerPayload.ID, (payload, context) -> {
            context.server().execute(() -> applySpawnChoice(context.player(), payload.zoneId()));
        });

        ServerTickEvents.END_SERVER_TICK.register(GlobeMod::borderUxTick);
    }

    private static void applyWorldBorder(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            return;
        }

        if (!isGlobeOverworld(overworld)) {
            return;
        }

        int borderRadiusBlocks = borderRadiusForGlobeOverworld(overworld);

        WorldBorder border = overworld.getWorldBorder();
        // radiusBlocks is e.g. 3750 / 5000 / 7500
        double diameter = borderRadiusBlocks * 2.0;
        border.setCenter(0.0, 0.0);
        border.setSize(diameter);

        POLAR_SCRUBBER = ENABLE_POLAR_SCRUBBER ? new PolarCapScrubber(borderRadiusBlocks, POLE_BAND_START_ABS_Z) : null;

        GlobeMod.LOGGER.info("[Latitude] WorldBorder set: radius={} diameter={} center=0,0",
                borderRadiusBlocks, diameter);
    }

    private static void borderUxTick(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            return;
        }

        if (!isGlobeOverworld(overworld)) {
            return;
        }

        long worldTime = overworld.getTime();
        if ((worldTime % 10L) != 0L) {
            return;
        }

        WorldBorder border = overworld.getWorldBorder();
        double radius = border.getSize() * 0.5;

        if (radius < 2000.0) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() != overworld) {
                continue;
            }

            double absZ = Math.abs(player.getZ());
            double distToPole = radius - absZ;

            PolarStage stage = distToPole <= (double) POLE_LETHAL_DISTANCE_BLOCKS ? PolarStage.LETHAL : PolarStage.NONE;

            int duration = 40;
            boolean ambient = true;
            boolean showParticles = false;
            boolean showIcon = false;

            if (stage == PolarStage.LETHAL || stage == PolarStage.HOPELESS) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 2, ambient, showParticles, showIcon));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, duration, 1, ambient, showParticles, showIcon));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, duration, 0, ambient, showParticles, showIcon));

                int max = 140;
                int target = (int) Math.floor(max * 0.85);
                if (target < 1) {
                    target = 1;
                }
                player.setFrozenTicks(Math.max(player.getFrozenTicks(), target));
            }
        }
    }

    private static void applyContinuousBlindness(ServerPlayerEntity player, boolean inFinalWhiteout) {
        if (!inFinalWhiteout) {
            return;
        }

        StatusEffectInstance cur = player.getStatusEffect(StatusEffects.BLINDNESS);
        if (cur == null || cur.getDuration() < 80) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 200, 0, true, false, false));
        }
    }

    private static boolean isGlobeOverworld(ServerWorld world) {
        ChunkGenerator gen = world.getChunkManager().getChunkGenerator();
        if (!(gen instanceof NoiseChunkGenerator noise)) return false;

        return noise.matchesSettings(GLOBE_SETTINGS_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_XSMALL_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_SMALL_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_REGULAR_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_LARGE_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_MASSIVE_KEY);
    }

    private static int borderRadiusForGlobeOverworld(ServerWorld world) {
        ChunkGenerator gen = world.getChunkManager().getChunkGenerator();
        if (!(gen instanceof NoiseChunkGenerator noise)) return BORDER_RADIUS;

        if (noise.matchesSettings(GLOBE_SETTINGS_KEY)) return 15000;
        if (noise.matchesSettings(GLOBE_SETTINGS_XSMALL_KEY)) return 3750;
        if (noise.matchesSettings(GLOBE_SETTINGS_SMALL_KEY)) return 5000;
        if (noise.matchesSettings(GLOBE_SETTINGS_REGULAR_KEY)) return BORDER_RADIUS;
        if (noise.matchesSettings(GLOBE_SETTINGS_LARGE_KEY)) return 10000;
        if (noise.matchesSettings(GLOBE_SETTINGS_MASSIVE_KEY)) return 20000;

        return BORDER_RADIUS;
    }

    private static void applySpawnChoice(ServerPlayerEntity player, String id) {
        if (player.getCommandTags().contains(SPAWN_CHOSEN_TAG)) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        if (!isGlobeOverworld(world)) {
            return;
        }

        String zoneId = id;
        if (zoneId != null && zoneId.equals("RANDOM")) {
            long seed = world.getServer().getSaveProperties().getGeneratorOptions().getSeed();
            zoneId = resolveSpawnZoneId(zoneId, seed);
            LOGGER.info("Resolved RANDOM spawn zone: player={}, seed={}, chosen={}", player.getName().getString(), seed, zoneId);
        }

        if (zoneId == null) {
            zoneId = "EQUATOR";
        }

        LOGGER.info("Applying spawn choice: player={}, zoneId={}", player.getName().getString(), zoneId);

        WorldBorder border = world.getWorldBorder();
        int radius = (int) Math.round(border.getSize() * 0.5);

        double t = com.example.globe.util.LatitudeMath.spawnFracForZoneKey(zoneId);

        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        int z = (int) Math.round(radius * t);

        int margin = POLE_WARNING_DISTANCE_BLOCKS + 64;
        int minZ = -radius + margin;
        int maxZ = radius - margin;
        if (minZ > maxZ) {
            minZ = 0;
            maxZ = 0;
        }
        if (z < minZ) z = minZ;
        if (z > maxZ) z = maxZ;

        int targetZ = z;
        long seed = world.getServer().getSaveProperties().getGeneratorOptions().getSeed();

        BlockPos spawnPos = findLandSpawn(world, radius, targetZ, seed);

        if (spawnPos == null) {
            LOGGER.warn("[Latitude] Could not find land spawn for zone={} targetZ={}. Falling back to (0, seaLevel+2).", zoneId, targetZ);
            spawnPos = new BlockPos(0, world.getSeaLevel() + 2, targetZ);
        }

        world.setSpawnPoint(WorldProperties.SpawnPoint.create(world.getRegistryKey(), spawnPos, 0.0f, 0.0f));
        player.teleport(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, EnumSet.noneOf(PositionFlag.class), player.getYaw(), player.getPitch(), true);
        player.addCommandTag(SPAWN_CHOSEN_TAG);
    }

    private static BlockPos findLandSpawn(ServerWorld world, int borderHalf, int targetZ, long seed) {
        final int margin = 320;
        final int max = Math.max(0, borderHalf - margin);

        // First pass: vary X only (stay exactly in the selected latitude line)
        final int attemptsXOnly = 96;

        // Second pass: if still failing, allow small Z jitter while staying near the band
        final int attemptsWithZJitter = 96;
        final int zJitter = 96;

        Random rng = Random.create(seed ^ 0x9E3779B97F4A7C15L ^ (long) targetZ);

        BlockPos best = null;
        int bestY = Integer.MIN_VALUE;

        // Pass 1: X-only
        for (int i = 0; i < attemptsXOnly; i++) {
            int x = rng.nextBetween(-max, max);
            int z = targetZ;

            BlockPos candidate = tryLandAt(world, x, z);
            if (candidate == null) continue;

            int y = candidate.getY();
            if (y > bestY) {
                bestY = y;
                best = candidate;
            }
        }
        if (best != null) return best;

        // Pass 2: X + small Z jitter
        for (int i = 0; i < attemptsWithZJitter; i++) {
            int x = rng.nextBetween(-max, max);
            int z = MathHelper.clamp(targetZ + rng.nextBetween(-zJitter, zJitter), -max, max);

            BlockPos candidate = tryLandAt(world, x, z);
            if (candidate == null) continue;

            int y = candidate.getY();
            if (y > bestY) {
                bestY = y;
                best = candidate;
            }
        }

        return best;
    }

    private static BlockPos tryLandAt(ServerWorld world, int x, int z) {
        // Ensure chunk exists
        world.getChunk(x >> 4, z >> 4);

        BlockPos ground = world.getTopPosition(
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, world.getBottomY(), z)
        );

        // Spawn is one block above ground
        BlockPos spawn = ground.up();

        // Reject if water column / fluid at spawn space
        if (!world.getFluidState(spawn).isEmpty()) return null;
        if (!world.getFluidState(spawn.up()).isEmpty()) return null;

        // Need 2-block headroom
        if (!world.getBlockState(spawn).isAir()) return null;
        if (!world.getBlockState(spawn.up()).isAir()) return null;

        // Reject "stand in water" edge cases (seafloor top can still be valid with water above)
        // MOTION_BLOCKING_NO_LEAVES usually avoids water surfaces, but this double-check is cheap.
        if (!world.getFluidState(ground).isEmpty()) return null;

        return spawn;
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

    private static boolean hasCompassAnywhere(ServerPlayerEntity player) {
        if (player == null) return false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (containsCompass(inv.getStack(i), 0)) return true;
        }
        return false;
    }

    private static boolean containsCompass(ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isOf(Items.COMPASS)) return true;

        if (depth >= 6) return false;

        if (stack.isOf(Items.BUNDLE)) {
            BundleContentsComponent contents = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
            if (contents != null) {
                for (ItemStack inside : contents.iterate()) {
                    if (containsCompass(inside, depth + 1)) return true;
                }
            }
        }

        return false;
    }
}
