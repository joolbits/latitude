package com.example.globe;

import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.EnumSet;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Shippable subset of the dev `/latdev` command — just band teleport + a here-readout, so testers can jump
 * between latitude bands without the heavy dev toolchain (the full {@code dev.LatitudeDevCommand} pulls in the
 * seam auditor + PNG exporter and is stripped from the release jar). Registered only when NOT in a dev
 * environment (there the full command owns {@code /latdev}) AND {@code -Dlatitude.devCommands=true} is set, so
 * normal players never see it. All latitude math uses the Z (latitude) radius, so it is correct on Mercator.
 */
public final class LatitudeDevCommands {
    private LatitudeDevCommands() {}

    public static void registerIfEnabled(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return; // the full dev.LatitudeDevCommand already owns /latdev in dev
        }
        if (!Boolean.getBoolean("latitude.devCommands")) {
            return; // opt-in only: add -Dlatitude.devCommands=true to the launch JVM args
        }
        register(dispatcher);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("latdev")
                .executes(LatitudeDevCommands::here)
                .then(Commands.literal("help").executes(LatitudeDevCommands::help))
                .then(Commands.literal("here").executes(LatitudeDevCommands::here))
                .then(Commands.literal("tpband")
                        .then(Commands.argument("band", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(LatitudeBands.canonicalIds(), b))
                                .executes(ctx -> tpBand(ctx, "center"))
                                .then(Commands.argument("edge", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.List.of("center", "low", "high"), b))
                                        .executes(ctx -> tpBand(ctx, StringArgumentType.getString(ctx, "edge"))))))
                .then(Commands.literal("tpedge")
                        .then(Commands.argument("side", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.List.of("west", "east"), b))
                                .executes(ctx -> tpEdge(ctx, 0.99))
                                .then(Commands.argument("frac", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0))
                                        .executes(ctx -> tpEdge(ctx, com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "frac"))))))
                .then(Commands.literal("probe").executes(LatitudeDevCommands::probe)));
    }

    private static int latitudeRadius(ServerLevel world) {
        int active = LatitudeBiomes.getActiveRadiusBlocks();
        if (active > 0) {
            return active;
        }
        double half = LatitudeMath.latitudeRadius(world.getWorldBorder());
        return Math.max(1, (int) Math.round(half));
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[latdev] here | probe | tpband <band> [center|low|high] | tpedge <west|east> [frac]"), false);
        return 1;
    }

    private static int here(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel world = src.getLevel();
            int radius = latitudeRadius(world);
            double absDeg = Mth.clamp(Math.abs(player.getZ()) / radius * 90.0, 0.0, 90.0);
            LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
            String hemi = player.getZ() < 0 ? "N" : "S"; // North = -Z
            String biome = biomeId(world, player.blockPosition());
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] %.1f°%s  %s  (z=%d, R=%d)  biome=%s",
                    absDeg, hemi, band.displayName(), (int) player.getZ(), radius, biome)), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] here failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int tpBand(CommandContext<CommandSourceStack> ctx, String edge) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel world = src.getLevel();
            LatitudeBands.Band band = LatitudeBands.fromCanonicalId(StringArgumentType.getString(ctx, "band"));
            if (band == null) {
                src.sendFailure(Component.literal("[latdev] band must be one of: " + String.join("|", LatitudeBands.canonicalIds())));
                return 0;
            }
            double targetDeg = switch (edge.toLowerCase(Locale.ROOT)) {
                case "low" -> band.lowDeg();
                case "high" -> Math.min(89.5, band.highDeg());
                default -> (band.lowDeg() + band.highDeg()) * 0.5;
            };
            int radius = latitudeRadius(world);
            int sign = player.getZ() < 0.0 ? -1 : 1; // keep the player's current hemisphere
            int targetZ = sign * LatitudeMath.zForLatitudeDeg(targetDeg, radius);
            int targetX = Mth.floor(player.getX());

            world.getChunkSource().getChunk(Math.floorDiv(targetX, 16), Math.floorDiv(targetZ, 16), ChunkStatus.FULL, true);
            int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
            int targetY = Mth.clamp(topY + 1, world.getMinY() + 1, world.getMinY() + world.getHeight() - 1);

            player.teleportTo(world, targetX + 0.5, targetY, targetZ + 0.5,
                    EnumSet.noneOf(Relative.class), player.getYRot(), player.getXRot(), true);

            String biome = biomeId(world, new BlockPos(targetX, targetY, targetZ));
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] -> %s %s (%.1f°)  x=%d y=%d z=%d  biome=%s",
                    band.displayName(), edge, targetDeg, targetX, targetY, targetZ, biome)), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] tpband failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int tpEdge(CommandContext<CommandSourceStack> ctx, double frac) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel world = src.getLevel();
            String side = StringArgumentType.getString(ctx, "side").toLowerCase(Locale.ROOT);
            int xRadius = LatitudeBiomes.getActiveXRadiusBlocks();
            if (xRadius <= 0) {
                double half = LatitudeMath.halfSize(world.getWorldBorder());
                xRadius = Math.max(1, (int) Math.round(half));
            }
            int sign = side.startsWith("w") ? -1 : 1; // west = -X, east = +X
            int targetX = (int) Math.round(sign * xRadius * Mth.clamp(frac, 0.0, 1.0));
            int targetZ = Mth.floor(player.getZ());

            world.getChunkSource().getChunk(Math.floorDiv(targetX, 16), Math.floorDiv(targetZ, 16), ChunkStatus.FULL, true);
            int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
            int targetY = Mth.clamp(topY + 1, world.getMinY() + 1, world.getMinY() + world.getHeight() - 1);
            player.teleportTo(world, targetX + 0.5, targetY, targetZ + 0.5,
                    EnumSet.noneOf(Relative.class), player.getYRot(), player.getXRot(), true);

            int edgeDist = xRadius - Math.abs(targetX);
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] -> %s edge  x=%d z=%d  (%d blocks from the E/W border, xRadius=%d)",
                    side, targetX, targetZ, edgeDist, LatitudeBiomes.getActiveXRadiusBlocks())), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] tpedge failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int probe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel world = src.getLevel();
            int zRadius = latitudeRadius(world);
            int xr0 = LatitudeBiomes.getActiveXRadiusBlocks();
            final int xRadius = xr0 > 0 ? xr0 : zRadius;
            double absDeg = Mth.clamp(Math.abs(player.getZ()) / zRadius * 90.0, 0.0, 90.0);
            LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
            String hemi = player.getZ() < 0 ? "N" : "S";
            int ewDist = xRadius - Math.abs(Mth.floor(player.getX()));
            int nsDist = zRadius - Math.abs(Mth.floor(player.getZ()));
            String biome = biomeId(world, player.blockPosition());
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] %.1f°%s %s | biome=%s | E/W border in %d blocks (xR=%d) | N/S pole in %d blocks (zR=%d)",
                    absDeg, hemi, band.displayName(), biome, ewDist, xRadius, nsDist, zRadius)), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] probe failed: " + e.getMessage()));
            return 0;
        }
    }

    private static String biomeId(ServerLevel world, BlockPos pos) {
        return world.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
    }
}
