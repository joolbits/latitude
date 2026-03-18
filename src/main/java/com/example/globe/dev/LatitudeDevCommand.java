package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public final class LatitudeDevCommand {
    private static final List<String> TP_BAND_NAMES = LatitudeBands.canonicalIds();
    private static final List<String> TP_EDGE_NAMES = List.of("center", "low", "high");
    private static final int WINDSWEPT_RUGGED_THRESH = 8;
    private static final int WINDSWEPT_RUGGED_HYST = 2;

    private LatitudeDevCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("latdev")
                        .executes(LatitudeDevCommand::help)
                        .then(CommandManager.literal("help").executes(LatitudeDevCommand::help))
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
                        .then(CommandManager.literal("slicePoleNS")
                                .then(CommandManager.argument("centerX", IntegerArgumentType.integer())
                                        .executes(LatitudeDevCommand::startSlicePoleNS)
                                        .then(CommandManager.argument("widthChunks", IntegerArgumentType.integer(1, 64))
                                                .executes(LatitudeDevCommand::startSlicePoleNS)
                                                .then(CommandManager.argument("chunksPerTick", IntegerArgumentType.integer(1, 2000))
                                                        .executes(LatitudeDevCommand::startSlicePoleNS)))))
                        .then(CommandManager.literal("here").executes(LatitudeDevCommand::here))
                        .then(CommandManager.literal("explainHere").executes(LatitudeDevCommand::explainHere))
                        .then(CommandManager.literal("tpBand")
                                .then(CommandManager.argument("band", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(TP_BAND_NAMES, builder))
                                        .executes(ctx -> tpBand(ctx, false))
                                        .then(CommandManager.argument("edge", StringArgumentType.word())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(TP_EDGE_NAMES, builder))
                                                .executes(ctx -> tpBand(ctx, true)))))
                        .then(CommandManager.literal("probe")
                                .then(CommandManager.argument("radiusBlocks", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("samples", IntegerArgumentType.integer())
                                                .executes(LatitudeDevCommand::probe))))
                        .then(CommandManager.literal("biomePng")
                                .executes(LatitudeDevCommand::biomePngDefault)
                                .then(CommandManager.argument("stepBlocks", IntegerArgumentType.integer(8, 512))
                                        .executes(LatitudeDevCommand::biomePngWithStep)
                                        .then(CommandManager.argument("y", IntegerArgumentType.integer(0, 320))
                                                .executes(LatitudeDevCommand::biomePngWithStepAndY))))
                        .then(regenLiteral("regen"))
                        .then(regenLiteral("regenChunk"))
                        .then(CommandManager.literal("pause").executes(LatitudeDevCommand::pauseTransect))
                        .then(CommandManager.literal("resume").executes(LatitudeDevCommand::resumeTransect))
                        .then(CommandManager.literal("stop").executes(LatitudeDevCommand::stopTransect))
                        .then(CommandManager.literal("status").executes(LatitudeDevCommand::statusTransect))
                        .then(CommandManager.literal("budgetMs")
                                .then(CommandManager.argument("ms", IntegerArgumentType.integer())
                                        .executes(LatitudeDevCommand::setBudgetMs)))
                        .then(CommandManager.literal("budgetAuto")
                                .then(CommandManager.literal("on").executes(LatitudeDevCommand::setBudgetAutoOn))
                                .then(CommandManager.literal("off").executes(LatitudeDevCommand::setBudgetAutoOff)))
        );
    }

    private static int help(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        source.sendFeedback(() -> Text.literal("[latdev] commands: here | explainHere | tpBand <tropical|subtropical|temperate|subpolar|polar> [center|low|high] | probe <radiusBlocks> <samples> | biomePng [stepBlocks] [y] | regen|regenChunk [radiusChunks] [biomes] [seed] | transect | transectDeg | slicePoleNS | pause | resume | stop | status | budgetMs | budgetAuto <on|off>"), false);
        return 1;
    }

    private static int here(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerCommandSource source = ctx.getSource();
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = source.getWorld();
            BlockPos pos = player.getBlockPos();
            int radius = authoritativeRadius(source);
            double deg = LatitudeMath.absLatDegExact(world.getWorldBorder(), player.getZ());
            double t = MathHelper.clamp(Math.abs(player.getZ()) / (double) radius, 0.0, 1.0);

            BandTarget band = BandTarget.fromZ(radius, player.getZ());
            String biomeId = biomeId(world.getBiome(pos));
            boolean mountainLike = isMountainLikeBiome(biomeId);
            double uplandT = LatitudeBiomes.uplandRampForY(pos.getY());
            double savUplandChance = Math.max(0.0, Math.min(1.0, uplandT));
            boolean savUplandActive = savUplandChance > 0.0;
            String savannaDebug = LatitudeBiomes.debugSavannaUplandDecision(pos.getX(), pos.getZ(), pos.getY());
            net.minecraft.world.gen.noise.NoiseConfig noiseConfig = world.getChunkManager().getNoiseConfig();
            net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler sampler = noiseConfig.getMultiNoiseSampler();
            net.minecraft.world.gen.chunk.ChunkGenerator cg = world.getChunkManager().getChunkGenerator();
            net.minecraft.world.gen.chunk.NoiseChunkGenerator ng = cg instanceof net.minecraft.world.gen.chunk.NoiseChunkGenerator n ? n : null;
            String savannaRule = LatitudeBiomes.debugSavannaRule(sampler, ng, noiseConfig, world, pos.getX(), pos.getZ());
            RuggednessSensor.Measurement ruggedness = RuggednessSensor.measure(world, pos, 24);
            double bumpinessScore = ruggedness.robustDelta(); // robust second-highest delta

            source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                    "[latdev] here x=%d y=%d z=%d deg=%.2f band=%s(idx=%d) cut=%.2f..%.2f t=%.4f mtnLike=%s uplandT=%.3f savUpland=%s chance=%.3f biome=%s",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    deg,
                    band.argName,
                    band.ordinal(),
                    band.lowDeg,
                    band.highDeg,
                    t,
                    mountainLike,
                    uplandT,
                    savUplandActive,
                    savUplandChance,
                    biomeId)), false);
            source.sendFeedback(() -> Text.literal("[latdev] here savUplandDebug " + savannaDebug), false);
            source.sendFeedback(() -> Text.literal("[latdev] here savannaRule " + savannaRule), false);
            source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                    "[latdev] here rugged x=%d z=%d ring=%d topY[c=%d n=%d s=%d e=%d w=%d ne=%d nw=%d se=%d sw=%d] dMax=%d dMean=%.2f axis=%.2f robust=%d",
                    pos.getX(),
                    pos.getZ(),
                    ruggedness.ringBlocks(),
                    ruggedness.centerY(),
                    ruggedness.northY(),
                    ruggedness.southY(),
                    ruggedness.eastY(),
                    ruggedness.westY(),
                    ruggedness.northEastY(),
                    ruggedness.northWestY(),
                    ruggedness.southEastY(),
                    ruggedness.southWestY(),
                    ruggedness.maxAbsDelta(),
                    ruggedness.meanAbsDelta(),
                    ruggedness.axisGradient(),
                    ruggedness.robustDelta())), false);
            source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                    "[latdev] dashboard bumpiness=%.2f robustDelta=%d dMax=%d dMean=%.2f thresh=%d hyst=%d → windswept_if>=%.0f",
                    bumpinessScore,
                    ruggedness.robustDelta(),
                    ruggedness.maxAbsDelta(),
                    ruggedness.meanAbsDelta(),
                    WINDSWEPT_RUGGED_THRESH,
                    WINDSWEPT_RUGGED_HYST,
                    (double) WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST)), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("[latdev] here error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int explainHere(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerCommandSource source = ctx.getSource();
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = source.getWorld();
            BlockPos pos = player.getBlockPos();
            int radius = authoritativeRadius(source);
            net.minecraft.world.gen.noise.NoiseConfig noiseConfig = world.getChunkManager().getNoiseConfig();
            net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler sampler = noiseConfig.getMultiNoiseSampler();
            net.minecraft.world.gen.chunk.ChunkGenerator cg = world.getChunkManager().getChunkGenerator();
            net.minecraft.world.gen.chunk.NoiseChunkGenerator ng = cg instanceof net.minecraft.world.gen.chunk.NoiseChunkGenerator n ? n : null;
            String finalBiomeId = biomeId(world.getBiome(pos));

            LatitudeBiomes.BiomeDiagnostics diag = LatitudeBiomes.explainBiomeAt(
                    finalBiomeId,
                    pos.getX(), pos.getZ(), pos.getY(),
                    radius,
                    sampler,
                    ng,
                    noiseConfig,
                    world);

            final String headerText = String.format(Locale.ROOT, "[latdev] explain @ x=%d z=%d", pos.getX(), pos.getZ());
            final String summaryText = "Summary: " + diag.summaryLine();
            final String driversText = "Drivers:\n" + diag.driversBlock();

            source.sendFeedback(() -> Text.literal(headerText), false);
            source.sendFeedback(() -> Text.literal(summaryText), false);
            source.sendFeedback(() -> Text.literal(driversText), false);

            String logPath = writeExplainLog(source, headerText, summaryText, driversText, pos.getX(), pos.getZ());
            if (logPath != null) {
                source.sendFeedback(() -> Text.literal("[latdev] explain log → " + logPath), false);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("[latdev] explainHere error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int tpBand(CommandContext<ServerCommandSource> ctx, boolean hasEdgeArg) {
        try {
            ServerCommandSource source = ctx.getSource();
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = source.getWorld();

            String bandArg = StringArgumentType.getString(ctx, "band");
            String edgeArg = hasEdgeArg ? StringArgumentType.getString(ctx, "edge") : "center";

            BandTarget band = BandTarget.fromArg(bandArg);
            if (band == null) {
                source.sendError(Text.literal("[latdev] tpBand band must be one of: " + String.join("|", TP_BAND_NAMES)));
                return 0;
            }

            EdgeMode edge = EdgeMode.fromArg(edgeArg);
            if (edge == null) {
                source.sendError(Text.literal("[latdev] tpBand edge must be one of: " + String.join("|", TP_EDGE_NAMES)));
                return 0;
            }

            int radius = authoritativeRadius(source);
            double targetDeg = edge.pickDeg(band.lowDeg, band.highDeg);
            int absTargetZ = LatitudeMath.zForLatitudeDeg(targetDeg, radius);
            int sign = player.getZ() < 0.0 ? -1 : 1;
            int targetZ = sign * absTargetZ;
            int targetX = MathHelper.floor(player.getX());

            world.getChunkManager().getChunk(Math.floorDiv(targetX, 16), Math.floorDiv(targetZ, 16), ChunkStatus.FULL, true);
            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
            int worldMaxY = world.getBottomY() + world.getHeight() - 1;
            int targetY = MathHelper.clamp(topY + 1, world.getBottomY() + 1, worldMaxY);

            player.teleport(world,
                    targetX + 0.5,
                    targetY,
                    targetZ + 0.5,
                    EnumSet.noneOf(PositionFlag.class),
                    player.getYaw(),
                    player.getPitch(),
                    true);

            String biomeId = biomeId(world.getBiome(new BlockPos(targetX, targetY, targetZ)));
            source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                    "[latdev] tpBand band=%s edge=%s deg=%.2f R=%d -> x=%d y=%d z=%d biome=%s",
                    band.argName,
                    edge.argName,
                    targetDeg,
                    radius,
                    targetX,
                    targetY,
                    targetZ,
                    biomeId)), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("[latdev] tpBand error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int probe(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerCommandSource source = ctx.getSource();
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = source.getWorld();

            int requestedRadius = IntegerArgumentType.getInteger(ctx, "radiusBlocks");
            int requestedSamples = IntegerArgumentType.getInteger(ctx, "samples");
            int radiusBlocks = MathHelper.clamp(requestedRadius, 32, 8192);
            int samples = MathHelper.clamp(requestedSamples, 10, 5000);
            int latitudeRadius = authoritativeRadius(source);

            int centerX = player.getBlockX();
            int centerZ = player.getBlockZ();
            int worldMaxY = world.getBottomY() + world.getHeight() - 1;
            int sampleY = MathHelper.clamp(player.getBlockY(), world.getBottomY() + 1, worldMaxY);
            long seed = world.getSeed() ^ mix64(player.getBlockPos().asLong());
            Random rng = new Random(seed);

            Map<String, Integer> biomeCounts = new HashMap<>();
            EnumMap<BandTarget, Integer> bandCounts = new EnumMap<>(BandTarget.class);
            int unloaded = 0;

            for (int i = 0; i < samples; i++) {
                double r = Math.sqrt(rng.nextDouble()) * radiusBlocks;
                double theta = rng.nextDouble() * (Math.PI * 2.0);
                int dx = (int) Math.round(r * Math.cos(theta));
                int dz = (int) Math.round(r * Math.sin(theta));

                int sampleX = centerX + dx;
                int sampleZ = centerZ + dz;
                int chunkX = Math.floorDiv(sampleX, 16);
                int chunkZ = Math.floorDiv(sampleZ, 16);

                if (world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.BIOMES, false) == null) {
                    unloaded++;
                    continue;
                }

                BlockPos samplePos = new BlockPos(sampleX, sampleY, sampleZ);
                String biomeId = biomeId(world.getBiome(samplePos));
                biomeCounts.merge(biomeId, 1, Integer::sum);

                BandTarget band = BandTarget.fromZ(latitudeRadius, sampleZ);
                bandCounts.merge(band, 1, Integer::sum);
            }

            int loaded = samples - unloaded;
            String biomeSummary = summarizeTopBiomes(biomeCounts, loaded, 10);
            String bandSummary = summarizeBands(bandCounts, loaded);
            int loadedCount = loaded;
            int unloadedCount = unloaded;
            int probeRadius = radiusBlocks;
            int sampleCount = samples;
            long sampleSeed = seed;

            source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                    "[latdev] probe r=%d n=%d loaded=%d unloaded=%d seed=%d",
                    probeRadius,
                    sampleCount,
                    loadedCount,
                    unloadedCount,
                    sampleSeed)), false);
            source.sendFeedback(() -> Text.literal("[latdev] biomes: " + biomeSummary), false);
            source.sendFeedback(() -> Text.literal("[latdev] bands: " + bandSummary), false);
            return loaded > 0 ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("[latdev] probe error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
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

    private static int startSlicePoleNS(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        int centerXChunk = IntegerArgumentType.getInteger(ctx, "centerX");
        int widthChunks = ctx.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("widthChunks"))
                ? IntegerArgumentType.getInteger(ctx, "widthChunks")
                : 1;
        int chunksPerTick = ctx.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("chunksPerTick"))
                ? IntegerArgumentType.getInteger(ctx, "chunksPerTick")
                : 200;

        widthChunks = MathHelper.clamp(widthChunks, 1, 64);
        chunksPerTick = MathHelper.clamp(chunksPerTick, 1, 2000);

        int radiusBlocks = maxAbsZFromBorder(source);
        return ChunkPregenerator.startSliceNS(source, centerXChunk, -radiusBlocks, radiusBlocks, widthChunks, chunksPerTick);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> regenLiteral(String name) {
        return CommandManager.literal(name)
                .executes(ctx -> regenChunk(ctx, 0, false, OptionalLong.empty()))
                .then(CommandManager.argument("radiusChunks", IntegerArgumentType.integer(0, 8))
                        .executes(ctx -> regenChunk(
                                ctx,
                                IntegerArgumentType.getInteger(ctx, "radiusChunks"),
                                false,
                                OptionalLong.empty()))
                        .then(CommandManager.argument("biomes", BoolArgumentType.bool())
                                .executes(ctx -> regenChunk(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "radiusChunks"),
                                        BoolArgumentType.getBool(ctx, "biomes"),
                                        OptionalLong.empty()))
                                .then(CommandManager.argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> regenChunk(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, "radiusChunks"),
                                                BoolArgumentType.getBool(ctx, "biomes"),
                                                OptionalLong.of(LongArgumentType.getLong(ctx, "seed")))))));
    }

    private static int regenChunk(CommandContext<ServerCommandSource> ctx, int requestedRadius, boolean biomes, OptionalLong seedOverride) {
        try {
            ServerCommandSource source = ctx.getSource();
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = source.getWorld();
            ChunkPos center = player.getChunkPos();
            int radius = MathHelper.clamp(requestedRadius, 0, 8);
            return ChunkRegenerator.regenSquare(world, center.x, center.z, radius, biomes, seedOverride, source);
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("[latdev] regenChunk error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int biomePngDefault(CommandContext<ServerCommandSource> ctx) {
        return startBiomePngExport(ctx.getSource(), 64, 64);
    }

    private static int biomePngWithStep(CommandContext<ServerCommandSource> ctx) {
        int stepBlocks = IntegerArgumentType.getInteger(ctx, "stepBlocks");
        return startBiomePngExport(ctx.getSource(), stepBlocks, 64);
    }

    private static int biomePngWithStepAndY(CommandContext<ServerCommandSource> ctx) {
        int stepBlocks = IntegerArgumentType.getInteger(ctx, "stepBlocks");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        return startBiomePngExport(ctx.getSource(), stepBlocks, y);
    }

    private static int startBiomePngExport(ServerCommandSource source, int stepBlocks, int y) {
        int clampedStep = MathHelper.clamp(stepBlocks, 8, 512);
        int clampedY = MathHelper.clamp(y, 0, 320);
        int radiusBlocks = authoritativeRadius(source);
        if (radiusBlocks <= 0) {
            source.sendError(Text.literal("[latdev] biomePng failed: invalid radius " + radiusBlocks));
            return 0;
        }

        long seed = source.getWorld().getSeed();
        source.sendFeedback(() -> Text.literal("[latdev] biomePng start seed=" + seed
                + " R=" + radiusBlocks
                + " step=" + clampedStep
                + " y=" + clampedY), false);

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return BiomePreviewExporter.export(
                                source.getWorld(),
                                radiusBlocks,
                                clampedStep,
                                clampedY,
                                source.getServer().getRunDirectory());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .whenComplete((result, throwable) -> source.getServer().execute(() -> {
                    if (throwable != null) {
                        Throwable cause = throwable instanceof RuntimeException && throwable.getCause() != null
                                ? throwable.getCause()
                                : throwable;
                        source.sendError(Text.literal("[latdev] biomePng failed: " + cause.getMessage()));
                        return;
                    }
                    source.sendFeedback(() -> Text.literal("[latdev] biomePng done file="
                            + result.pngPath()
                            + " sidecar="
                            + result.txtPath()
                            + " image="
                            + result.width()
                            + "x"
                            + result.height()
                            + " samples="
                            + result.totalSamples()
                            + " durationMs="
                            + result.durationMs()), false);
                }));
        return 1;
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

    private static int setBudgetMs(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        int ms = IntegerArgumentType.getInteger(ctx, "ms");
        if (ms < 1 || ms > 50) {
            source.sendError(Text.literal("[latdev] budgetMs must be in [1..50]"));
            return 0;
        }
        return ChunkPregenerator.setDefaultBudgetMs(source, ms);
    }

    private static int setBudgetAutoOn(CommandContext<ServerCommandSource> ctx) {
        return ChunkPregenerator.setAutoBudget(ctx.getSource(), true);
    }

    private static int setBudgetAutoOff(CommandContext<ServerCommandSource> ctx) {
        return ChunkPregenerator.setAutoBudget(ctx.getSource(), false);
    }

    private static final DateTimeFormatter EXPLAIN_TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** Writes explain output to run/latdev/explain/<timestamp>_x<X>_z<Z>.txt and latest.txt. Returns relative path string for user feedback, or null on failure. */
    private static String writeExplainLog(ServerCommandSource source, String header, String summary, String drivers, int x, int z) {
        try {
            Path explainDir = source.getServer().getRunDirectory().resolve("latdev").resolve("explain");
            Files.createDirectories(explainDir);

            String timestamp = EXPLAIN_TIMESTAMP_FMT.format(LocalDateTime.now());
            String filename = timestamp + "_x" + x + "_z" + z + ".txt";
            String content = header + "\n" + summary + "\n" + drivers + "\n";

            Files.writeString(explainDir.resolve(filename), content, StandardCharsets.UTF_8);
            Files.writeString(explainDir.resolve("latest.txt"), content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return "latdev/explain/latest.txt  (also " + filename + ")";
        } catch (IOException e) {
            GlobeMod.LOGGER.warn("[latdev] explainHere log write failed", e);
            return null;
        }
    }

    private static int authoritativeRadius(ServerCommandSource source) {
        int borderRadius = maxAbsZFromBorder(source);
        int activeRadius = LatitudeBiomes.getActiveRadiusBlocks();
        if (activeRadius > 0) {
            return MathHelper.clamp(activeRadius, 1, Math.max(1, borderRadius));
        }
        return Math.max(1, borderRadius);
    }

    private static int maxAbsZFromBorder(ServerCommandSource source) {
        WorldBorder border = source.getWorld().getWorldBorder();
        int radius = (int) Math.floor(border.getSize() * 0.5);
        return Math.max(0, radius - 16);
    }

    private static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static boolean isMountainLikeBiome(String biomeId) {
        String id = biomeId.toLowerCase(Locale.ROOT);
        return id.contains("mountain")
                || id.contains("peak")
                || id.contains("hills")
                || id.contains("ridge")
                || id.contains("windswept");
    }

    private static String biomeId(RegistryEntry<Biome> biome) {
        return biome.getKey().map(key -> key.getValue().toString()).orElse("?");
    }

    private static String summarizeTopBiomes(Map<String, Integer> biomeCounts, int loaded, int limit) {
        if (loaded <= 0 || biomeCounts.isEmpty()) {
            return "none";
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(biomeCounts.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue()).reversed());

        int count = Math.min(limit, entries.size());
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            if (i > 0) {
                out.append(", ");
            }
            out.append(shortBiomeName(entry.getKey()))
                    .append(" ")
                    .append(formatPercent(entry.getValue(), loaded))
                    .append("(")
                    .append(entry.getValue())
                    .append(")");
        }
        return out.toString();
    }

    private static String summarizeBands(EnumMap<BandTarget, Integer> bandCounts, int loaded) {
        if (loaded <= 0) {
            return "none";
        }

        StringBuilder out = new StringBuilder();
        for (BandTarget band : BandTarget.values()) {
            int count = bandCounts.getOrDefault(band, 0);
            if (count <= 0) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(band.argName)
                    .append(" ")
                    .append(formatPercent(count, loaded))
                    .append("(")
                    .append(count)
                    .append(")");
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    private static String shortBiomeName(String biomeId) {
        int split = biomeId.indexOf(':');
        return split >= 0 ? biomeId.substring(split + 1) : biomeId;
    }

    private static String formatPercent(int count, int total) {
        if (total <= 0) {
            return "0.0%";
        }
        return String.format(Locale.ROOT, "%.1f%%", (count * 100.0) / total);
    }

    private enum EdgeMode {
        CENTER("center"),
        LOW("low"),
        HIGH("high");

        private final String argName;

        EdgeMode(String argName) {
            this.argName = argName;
        }

        private static EdgeMode fromArg(String raw) {
            if (raw == null) {
                return CENTER;
            }
            String normalized = raw.toLowerCase(Locale.ROOT);
            for (EdgeMode mode : values()) {
                if (mode.argName.equals(normalized)) {
                    return mode;
                }
            }
            return null;
        }

        private double pickDeg(double lowDeg, double highDeg) {
            double span = Math.max(0.0, highDeg - lowDeg);
            return switch (this) {
                case LOW -> lowDeg + (span * 0.10);
                case HIGH -> lowDeg + (span * 0.90);
                default -> lowDeg + (span * 0.50);
            };
        }
    }

    private enum BandTarget {
        TROPICAL(LatitudeBands.Band.TROPICAL),
        SUBTROPICAL(LatitudeBands.Band.SUBTROPICAL),
        TEMPERATE(LatitudeBands.Band.TEMPERATE),
        SUBPOLAR(LatitudeBands.Band.SUBPOLAR),
        POLAR(LatitudeBands.Band.POLAR);

        private final String argName;
        private final double lowDeg;
        private final double highDeg;
        private final LatitudeBands.Band band;

        BandTarget(LatitudeBands.Band band) {
            this.argName = band.id();
            this.lowDeg = band.lowDeg();
            this.highDeg = band.highDeg();
            this.band = band;
        }

        private static BandTarget fromArg(String raw) {
            if (raw == null) {
                return null;
            }
            String normalized = switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "tropics" -> "tropical";
                case "arid", "subtropics" -> "subtropical";
                default -> raw.trim().toLowerCase(Locale.ROOT);
            };
            for (BandTarget band : values()) {
                if (band.argName.equals(normalized)) {
                    return band;
                }
            }
            return null;
        }

        private static BandTarget fromBand(LatitudeBands.Band canonicalBand) {
            for (BandTarget target : values()) {
                if (target.band == canonicalBand) {
                    return target;
                }
            }
            return TROPICAL;
        }

        private static BandTarget fromZ(int radius, double z) {
            double absLatDeg = radius <= 0 ? 0.0 : Math.abs(z) * 90.0 / (double) Math.max(1, radius);
            return fromBand(LatitudeBands.fromAbsoluteLatitudeDeg(absLatDeg));
        }
    }
}
