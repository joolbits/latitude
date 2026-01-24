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
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.WorldProperties;
import net.minecraft.network.packet.s2c.play.PositionFlag;
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
    public static final int POLE_LETHAL_WARNING_DISTANCE = 256;
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
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_KEY = RegistryKey.of(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_ID);
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_XSMALL_KEY = RegistryKey.of(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_XSMALL_ID);
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_SMALL_KEY = RegistryKey.of(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_SMALL_ID);
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_REGULAR_KEY = RegistryKey.of(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_REGULAR_ID);

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

        double T_UNEASE = radius / 4.0;
        double T_IMPAIR = radius / 6.0;
        double T_HOSTILE = radius / 10.0;
        double T_WHITEOUT = radius / 16.0;
        double T_LETHAL = radius / 24.0;
        double T_HOPELESS = radius / 32.0;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() != overworld) {
                continue;
            }

            double absZ = Math.abs(player.getZ());
            double distToPole = radius - absZ;

            PolarStage stage =
                    distToPole <= T_HOPELESS ? PolarStage.HOPELESS :
                    distToPole <= T_LETHAL ? PolarStage.LETHAL :
                    distToPole <= T_WHITEOUT ? PolarStage.WHITEOUT :
                    distToPole <= T_HOSTILE ? PolarStage.HOSTILE :
                    distToPole <= T_IMPAIR ? PolarStage.IMPAIR :
                    distToPole <= T_UNEASE ? PolarStage.UNEASE :
                    PolarStage.NONE;

            int duration = 40;
            boolean ambient = true;
            boolean showParticles = false;
            boolean showIcon = false;

            if (stage == PolarStage.IMPAIR) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 0, ambient, showParticles, showIcon));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, duration, 0, ambient, showParticles, showIcon));
            } else if (stage == PolarStage.HOSTILE || stage == PolarStage.WHITEOUT) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 1, ambient, showParticles, showIcon));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, duration, 0, ambient, showParticles, showIcon));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, duration, 0, ambient, showParticles, showIcon));
            } else if (stage == PolarStage.LETHAL || stage == PolarStage.HOPELESS) {
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

    private static boolean isGlobeOverworld(ServerWorld overworld) {
        if (!overworld.getRegistryKey().getValue().equals(World.OVERWORLD.getValue())) {
            return false;
        }

        ChunkGenerator generator = overworld.getChunkManager().getChunkGenerator();
        if (!(generator instanceof NoiseChunkGenerator noise)) {
            return false;
        }

        return noise.matchesSettings(GLOBE_SETTINGS_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_XSMALL_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_SMALL_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_REGULAR_KEY);
    }

    private static int borderRadiusForGlobeOverworld(ServerWorld overworld) {
        ChunkGenerator generator = overworld.getChunkManager().getChunkGenerator();
        if (!(generator instanceof NoiseChunkGenerator noise)) {
            return BORDER_RADIUS;
        }

        if (noise.matchesSettings(GLOBE_SETTINGS_XSMALL_KEY)) {
            return 3750;
        }
        if (noise.matchesSettings(GLOBE_SETTINGS_SMALL_KEY)) {
            return 5000;
        }
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

        LOGGER.info("Applying spawn choice: player={}, zoneId={}", player.getName().getString(), id);

        int z = switch (id) {
            case "EQUATOR" -> 0;
            case "TROPICAL" -> 3000;
            case "SUBTROPICAL" -> 6000;
            case "TEMPERATE" -> 9000;
            case "SUBPOLAR" -> 11000;
            case "POLAR" -> 12500;
            default -> 0;
        };

        int x = 0;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        world.getChunk(chunkX, chunkZ);

        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        y = Math.max(y, world.getBottomY() + 2);

        BlockPos p = new BlockPos(x, y, z);
        int top = world.getTopYInclusive() - 2;
        for (int i = 0; i < 32 && p.getY() < top; i++) {
            if (world.isAir(p) && world.isAir(p.up())) {
                break;
            }
            p = p.up();
        }
        BlockPos spawn = p;

        world.setSpawnPoint(WorldProperties.SpawnPoint.create(world.getRegistryKey(), spawn, 0.0f, 0.0f));
        player.teleport(world, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, EnumSet.noneOf(PositionFlag.class), player.getYaw(), player.getPitch(), true);
        player.addCommandTag(SPAWN_CHOSEN_TAG);
    }
}
