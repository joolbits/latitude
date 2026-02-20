package com.example.globe.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.border.WorldBorder;

public final class LatitudeDevCommand {
    private LatitudeDevCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }

        dispatcher.register(
                CommandManager.literal("latdev")
                        .then(CommandManager.literal("transect")
                                .then(CommandManager.argument("zStart", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("zEnd", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("xHalfWidthChunks", IntegerArgumentType.integer())
                                                        .then(CommandManager.argument("chunksPerTick", IntegerArgumentType.integer())
                                                                .executes(LatitudeDevCommand::startTransectRaw))))))
                        .then(CommandManager.literal("transectDeg")
                                .then(CommandManager.argument("degStart", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("degEnd", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("xHalfWidthChunks", IntegerArgumentType.integer())
                                                        .then(CommandManager.argument("chunksPerTick", IntegerArgumentType.integer())
                                                                .executes(LatitudeDevCommand::startTransectDeg))))))
                        .then(CommandManager.literal("pause").executes(LatitudeDevCommand::pauseTransect))
                        .then(CommandManager.literal("resume").executes(LatitudeDevCommand::resumeTransect))
                        .then(CommandManager.literal("stop").executes(LatitudeDevCommand::stopTransect))
                        .then(CommandManager.literal("status").executes(LatitudeDevCommand::statusTransect))
        );
    }

    private static int startTransectRaw(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        int zStart = Math.abs(IntegerArgumentType.getInteger(ctx, "zStart"));
        int zEnd = Math.abs(IntegerArgumentType.getInteger(ctx, "zEnd"));
        int xHalfWidthChunks = IntegerArgumentType.getInteger(ctx, "xHalfWidthChunks");
        int chunksPerTick = IntegerArgumentType.getInteger(ctx, "chunksPerTick");

        if (zStart > zEnd) {
            int tmp = zStart;
            zStart = zEnd;
            zEnd = tmp;
        }

        xHalfWidthChunks = MathHelper.clamp(xHalfWidthChunks, 0, 64);
        chunksPerTick = MathHelper.clamp(chunksPerTick, 1, 50);

        int maxAbsZ = maxAbsZFromBorder(source);
        zStart = MathHelper.clamp(zStart, 0, maxAbsZ);
        zEnd = MathHelper.clamp(zEnd, 0, maxAbsZ);

        if (zStart > zEnd) {
            int tmp = zStart;
            zStart = zEnd;
            zEnd = tmp;
        }

        return ChunkPregenerator.startTransect(source.getServer(), source, zStart, zEnd, xHalfWidthChunks, chunksPerTick);
    }

    private static int startTransectDeg(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        int degStart = Math.abs(IntegerArgumentType.getInteger(ctx, "degStart"));
        int degEnd = Math.abs(IntegerArgumentType.getInteger(ctx, "degEnd"));
        int xHalfWidthChunks = IntegerArgumentType.getInteger(ctx, "xHalfWidthChunks");
        int chunksPerTick = IntegerArgumentType.getInteger(ctx, "chunksPerTick");

        if (degStart > degEnd) {
            int tmp = degStart;
            degStart = degEnd;
            degEnd = tmp;
        }

        degStart = MathHelper.clamp(degStart, 0, 90);
        degEnd = MathHelper.clamp(degEnd, 0, 90);
        xHalfWidthChunks = MathHelper.clamp(xHalfWidthChunks, 0, 64);
        chunksPerTick = MathHelper.clamp(chunksPerTick, 1, 50);

        double radius = source.getWorld().getWorldBorder().getSize() * 0.5;
        int maxAbsZ = maxAbsZFromBorder(source);
        int zStart = MathHelper.clamp((int) Math.round((degStart / 90.0) * radius), 0, maxAbsZ);
        int zEnd = MathHelper.clamp((int) Math.round((degEnd / 90.0) * radius), 0, maxAbsZ);

        if (zStart > zEnd) {
            int tmp = zStart;
            zStart = zEnd;
            zEnd = tmp;
        }

        return ChunkPregenerator.startTransect(source.getServer(), source, zStart, zEnd, xHalfWidthChunks, chunksPerTick);
    }

    private static int pauseTransect(CommandContext<ServerCommandSource> ctx) {
        return ChunkPregenerator.pauseJob(ctx.getSource());
    }

    private static int resumeTransect(CommandContext<ServerCommandSource> ctx) {
        return ChunkPregenerator.resumeJob(ctx.getSource());
    }

    private static int stopTransect(CommandContext<ServerCommandSource> ctx) {
        return ChunkPregenerator.stopJob(ctx.getSource());
    }

    private static int statusTransect(CommandContext<ServerCommandSource> ctx) {
        return ChunkPregenerator.status(ctx.getSource());
    }

    private static int maxAbsZFromBorder(ServerCommandSource source) {
        WorldBorder border = source.getWorld().getWorldBorder();
        int radius = (int) Math.floor(border.getSize() * 0.5);
        return Math.max(0, radius - 16);
    }
}
