package com.example.globe;

import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.world.biome.Biome;

import java.util.EnumSet;
import java.util.Locale;

public final class LatitudeDevCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {

        dispatcher.register(CommandManager.literal("lattp")
            .then(CommandManager.literal("equator").executes(ctx -> tp(ctx, "EQUATOR", +1)))
            .then(CommandManager.literal("tropics").executes(ctx -> tp(ctx, "TROPICAL", +1)))
            .then(CommandManager.literal("subtropics").executes(ctx -> tp(ctx, "SUBTROPICAL", +1)))
            .then(CommandManager.literal("temperate").executes(ctx -> tp(ctx, "TEMPERATE", +1)))
            .then(CommandManager.literal("subpolar").executes(ctx -> tp(ctx, "SUBPOLAR", +1)))
            .then(CommandManager.literal("polar").executes(ctx -> tp(ctx, "POLAR", +1)))
        );

        dispatcher.register(CommandManager.literal("lattps")
            .then(CommandManager.literal("equator").executes(ctx -> tp(ctx, "EQUATOR", -1)))
            .then(CommandManager.literal("tropics").executes(ctx -> tp(ctx, "TROPICAL", -1)))
            .then(CommandManager.literal("subtropics").executes(ctx -> tp(ctx, "SUBTROPICAL", -1)))
            .then(CommandManager.literal("temperate").executes(ctx -> tp(ctx, "TEMPERATE", -1)))
            .then(CommandManager.literal("subpolar").executes(ctx -> tp(ctx, "SUBPOLAR", -1)))
            .then(CommandManager.literal("polar").executes(ctx -> tp(ctx, "POLAR", -1)))
        );
    }

    private static int tp(CommandContext<ServerCommandSource> ctx, String zoneKey, int hemiSign) {
        try {
            ServerCommandSource source = ctx.getSource();
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = source.getWorld();

            int radius = getAuthoritativeRadius(world);
            double frac = LatitudeMath.spawnFracForZoneKey(zoneKey);
            int targetZ = (int) Math.round(radius * frac) * hemiSign;

            source.sendFeedback(() -> Text.literal("[lattp] zone=" + zoneKey
                + " targetZ=" + targetZ
                + " hemi=" + (hemiSign > 0 ? "N" : "S")
                + " activeRadius=" + radius
                + " frac=" + String.format(Locale.ROOT, "%.3f", frac)
            ), false);

            BlockPos safe = findSafeLand(world, targetZ);
            if (safe == null) {
                source.sendError(Text.literal("[lattp] no land found near Z=" + targetZ));
                return 0;
            }

            // Ensure chunk is loaded
            world.getChunk(safe.getX() >> 4, safe.getZ() >> 4);

            player.teleport(world, safe.getX() + 0.5, (double)safe.getY(), safe.getZ() + 0.5, EnumSet.noneOf(PositionFlag.class), player.getYaw(), player.getPitch(), true);
            source.sendFeedback(() -> Text.literal("[lattp] teleported: " + safe.toShortString()
                + " topY=" + safe.getY()
                + " biome=" + world.getBiome(safe).getKey().map(k -> k.getValue().toString()).orElse("?")
            ), true);
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("[lattp] error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int getAuthoritativeRadius(ServerWorld world) {
        int active = LatitudeBiomes.ACTIVE_RADIUS_BLOCKS;
        if (active > 0) return active;
        return (int) Math.round(LatitudeMath.halfSize(world.getWorldBorder()));
    }

    private static BlockPos findSafeLand(ServerWorld world, int targetZ) {
        int searchX = 10000;
        int stepX = 64;
        int[] dzs = new int[] {0, 64, -64, 128, -128, 256, -256, 512, -512};

        for (int dz : dzs) {
            int z = targetZ + dz;
            for (int x = 0; x <= searchX; x += stepX) {
                BlockPos p = check(world, x, z);
                if (p != null) return p;
                if (x != 0) {
                    p = check(world, -x, z);
                    if (p != null) return p;
                }
            }
        }
        return null;
    }

    private static BlockPos check(ServerWorld world, int x, int z) {
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);

        RegistryEntry<Biome> biome = world.getBiome(pos);
        if (biome.isIn(BiomeTags.IS_OCEAN)) return null;

        return pos;
    }
}
