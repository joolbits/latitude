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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

public final class LatitudeDevCommand {
    private static final List<String> TP_BAND_NAMES = LatitudeBands.canonicalIds();
    private static final List<String> TP_EDGE_NAMES = List.of("center", "low", "high");
    private static final List<String> SEAM_EDGE_NAMES = SeamAuditCoordinator.EdgeChoice.argNames();
    private static final int WINDSWEPT_RUGGED_THRESH = 8;
    private static final int WINDSWEPT_RUGGED_HYST = 2;
    private static final int SEAM_AUDIT_DEFAULT_SAMPLES = 300;
    private static final int SEAM_AUDIT_DEFAULT_WAIT_TICKS = 60;

    private LatitudeDevCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("latdev")
                        .executes(LatitudeDevCommand::help)
                        .then(Commands.literal("help").executes(LatitudeDevCommand::help))
                        .then(Commands.literal("transect")
                                .then(Commands.argument("zStart", IntegerArgumentType.integer())
                                        .then(Commands.argument("zEnd", IntegerArgumentType.integer())
                                                .then(Commands.argument("xHalfWidthChunks", IntegerArgumentType.integer())
                                                        .then(Commands.argument("chunksPerTick", IntegerArgumentType.integer())
                                                                .executes(LatitudeDevCommand::startTransectRaw))))))
                        .then(Commands.literal("transectDeg")
                                .then(Commands.argument("degStart", IntegerArgumentType.integer())
                                        .then(Commands.argument("degEnd", IntegerArgumentType.integer())
                                                .then(Commands.argument("xHalfWidthChunks", IntegerArgumentType.integer())
                                                        .then(Commands.argument("chunksPerTick", IntegerArgumentType.integer())
                                                                .executes(LatitudeDevCommand::startTransectDeg))))))
                        .then(Commands.literal("slicePoleNS")
                                .then(Commands.argument("centerX", IntegerArgumentType.integer())
                                        .executes(LatitudeDevCommand::startSlicePoleNS)
                                        .then(Commands.argument("widthChunks", IntegerArgumentType.integer(1, 64))
                                                .executes(LatitudeDevCommand::startSlicePoleNS)
                                                .then(Commands.argument("chunksPerTick", IntegerArgumentType.integer(1, 2000))
                                                        .executes(LatitudeDevCommand::startSlicePoleNS)))))
                        .then(Commands.literal("here").executes(LatitudeDevCommand::here))
                        .then(Commands.literal("explainHere").executes(LatitudeDevCommand::explainHere))
                        .then(Commands.literal("tpBand")
                                .then(Commands.argument("band", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(TP_BAND_NAMES, builder))
                                        .executes(ctx -> tpBand(ctx, false))
                                        .then(Commands.argument("edge", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(TP_EDGE_NAMES, builder))
                                                .executes(ctx -> tpBand(ctx, true)))))
                        .then(Commands.literal("probe")
                                .then(Commands.argument("radiusBlocks", IntegerArgumentType.integer())
                                        .then(Commands.argument("samples", IntegerArgumentType.integer())
                                                .executes(LatitudeDevCommand::probe))))
                        .then(Commands.literal("seamAudit")
                                .then(Commands.argument("bandA", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(TP_BAND_NAMES, builder))
                                        .then(Commands.argument("bandB", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(TP_BAND_NAMES, builder))
                                                .executes(ctx -> seamAudit(ctx, false, false, false))
                                                .then(Commands.argument("edge", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(SEAM_EDGE_NAMES, builder))
                                                        .executes(ctx -> seamAudit(ctx, true, false, false))
                                                        .then(Commands.argument("samples", IntegerArgumentType.integer(50, 4000))
                                                                .executes(ctx -> seamAudit(ctx, true, true, false))
                                                                .then(Commands.argument("waitTicks", IntegerArgumentType.integer(0, 600))
                                                                        .executes(ctx -> seamAudit(ctx, true, true, true))))))))
                        .then(Commands.literal("biomePng")
                                .executes(LatitudeDevCommand::biomePngDefault)
                                .then(Commands.argument("stepBlocks", IntegerArgumentType.integer(8, 512))
                                        .executes(LatitudeDevCommand::biomePngWithStep)
                                        .then(Commands.argument("y", IntegerArgumentType.integer(0, 320))
                                                .executes(LatitudeDevCommand::biomePngWithStepAndY))))
                        .then(Commands.literal("biomePngY")
                                .executes(LatitudeDevCommand::biomePngYDefault)
                                .then(Commands.argument("y", IntegerArgumentType.integer(0, 320))
                                        .executes(LatitudeDevCommand::biomePngYWithY)))
                        .then(regenLiteral("regen"))
                        .then(regenLiteral("regenChunk"))
                        .then(Commands.literal("pause").executes(LatitudeDevCommand::pauseTransect))
                        .then(Commands.literal("resume").executes(LatitudeDevCommand::resumeTransect))
                        .then(Commands.literal("stop").executes(LatitudeDevCommand::stopTransect))
                        .then(Commands.literal("status").executes(LatitudeDevCommand::statusTransect))
                        .then(Commands.literal("budgetMs")
                                .then(Commands.argument("ms", IntegerArgumentType.integer())
                                        .executes(LatitudeDevCommand::setBudgetMs)))
                        .then(Commands.literal("budgetAuto")
                                .then(Commands.literal("on").executes(LatitudeDevCommand::setBudgetAutoOn))
                                .then(Commands.literal("off").executes(LatitudeDevCommand::setBudgetAutoOff)))
        );
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("[latdev] commands: here | explainHere | tpBand <tropical|subtropical|temperate|subpolar|polar> [center|low|high] | probe <radiusBlocks> <samples> | seamAudit <bandA> <bandB> [center|low|high] [samples] [waitTicks] | biomePng [stepBlocks] [y] | biomePngY [y] | regen|regenChunk [radiusChunks] [biomes] [seed] | transect | transectDeg | slicePoleNS | pause | resume | stop | status | budgetMs | budgetAuto <on|off>"), false);
        return 1;
    }

    private static int here(CommandContext<CommandSourceStack> ctx) {
        try {
            CommandSourceStack source = ctx.getSource();
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel world = source.getLevel();
            BlockPos pos = player.blockPosition();
            int radius = authoritativeRadius(source);
            double deg = LatitudeMath.absLatDegExact(world.getWorldBorder(), player.getZ());
            double t = Mth.clamp(Math.abs(player.getZ()) / (double) radius, 0.0, 1.0);

            BandTarget band = BandTarget.fromZ(radius, player.getZ());
            String biomeId = biomeId(world.getBiome(pos));
            boolean mountainLike = isMountainLikeBiome(biomeId);
            double uplandT = LatitudeBiomes.uplandRampForY(pos.getY());
            double savUplandChance = Math.max(0.0, Math.min(1.0, uplandT));
            boolean savUplandActive = savUplandChance > 0.0;
            String savannaDebug = LatitudeBiomes.debugSavannaUplandDecision(pos.getX(), pos.getZ(), pos.getY());
            net.minecraft.world.level.levelgen.RandomState noiseConfig = world.getChunkSource().randomState();
            net.minecraft.world.level.biome.Climate.Sampler sampler = noiseConfig.sampler();
            net.minecraft.world.level.chunk.ChunkGenerator cg = world.getChunkSource().getGenerator();
            net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator ng = cg instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator n ? n : null;
            String savannaRule = LatitudeBiomes.debugSavannaRule(sampler, ng, noiseConfig, world, pos.getX(), pos.getZ());
            RuggednessSensor.Measurement ruggedness = RuggednessSensor.measure(world, pos, 24);
            double bumpinessScore = ruggedness.robustDelta(); // robust second-highest delta

            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
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
            source.sendSuccess(() -> Component.literal("[latdev] here savUplandDebug " + savannaDebug), false);
            source.sendSuccess(() -> Component.literal("[latdev] here savannaRule " + savannaRule), false);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
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
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
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
            ctx.getSource().sendFailure(Component.literal("[latdev] here error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int explainHere(CommandContext<CommandSourceStack> ctx) {
        try {
            CommandSourceStack source = ctx.getSource();
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel world = source.getLevel();
            BlockPos pos = player.blockPosition();
            int radius = authoritativeRadius(source);
            net.minecraft.world.level.levelgen.RandomState noiseConfig = world.getChunkSource().randomState();
            net.minecraft.world.level.biome.Climate.Sampler sampler = noiseConfig.sampler();
            net.minecraft.world.level.chunk.ChunkGenerator cg = world.getChunkSource().getGenerator();
            net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator ng = cg instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator n ? n : null;
            String finalBiomeId = biomeId(world.getBiome(pos));
            SurfaceTruth surfaceTruth = resolveSurfaceTruth(world, pos.getX(), pos.getZ());

            LatitudeBiomes.BiomeDiagnostics diag = LatitudeBiomes.explainBiomeAt(
                    finalBiomeId,
                    pos.getX(), pos.getZ(), pos.getY(),
                    radius,
                    sampler,
                    ng,
                    noiseConfig,
                    world,
                    surfaceTruth.available(),
                    surfaceTruth.surfaceBlockId(),
                    surfaceTruth.surfaceFluidId(),
                    surfaceTruth.waterSurface(),
                    surfaceTruth.surfaceY());

            final String headerText = String.format(Locale.ROOT, "[latdev] explain @ x=%d z=%d", pos.getX(), pos.getZ());
            final String summaryText = "Summary: " + diag.summaryLine();
            final String driversText = "Drivers:\n" + diag.driversBlock();

            source.sendSuccess(() -> Component.literal(headerText), false);
            source.sendSuccess(() -> Component.literal(summaryText), false);
            source.sendSuccess(() -> Component.literal(driversText), false);

            String logPath = writeExplainLog(source, headerText, summaryText, driversText, pos.getX(), pos.getZ());
            if (logPath != null) {
                source.sendSuccess(() -> Component.literal("[latdev] explain log → " + logPath), false);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[latdev] explainHere error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int tpBand(CommandContext<CommandSourceStack> ctx, boolean hasEdgeArg) {
        try {
            CommandSourceStack source = ctx.getSource();
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel world = source.getLevel();

            String bandArg = StringArgumentType.getString(ctx, "band");
            String edgeArg = hasEdgeArg ? StringArgumentType.getString(ctx, "edge") : "center";

            BandTarget band = BandTarget.fromArg(bandArg);
            if (band == null) {
                source.sendFailure(Component.literal("[latdev] tpBand band must be one of: " + String.join("|", TP_BAND_NAMES)));
                return 0;
            }

            EdgeMode edge = EdgeMode.fromArg(edgeArg);
            if (edge == null) {
                source.sendFailure(Component.literal("[latdev] tpBand edge must be one of: " + String.join("|", TP_EDGE_NAMES)));
                return 0;
            }

            int radius = authoritativeRadius(source);
            double targetDeg = edge.pickDeg(band.lowDeg, band.highDeg);
            int absTargetZ = LatitudeMath.zForLatitudeDeg(targetDeg, radius);
            int sign = player.getZ() < 0.0 ? -1 : 1;
            int targetZ = sign * absTargetZ;
            int targetX = Mth.floor(player.getX());

            world.getChunkSource().getChunk(Math.floorDiv(targetX, 16), Math.floorDiv(targetZ, 16), ChunkStatus.FULL, true);
            int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
            int worldMaxY = world.getMinY() + world.getHeight() - 1;
            int targetY = Mth.clamp(topY + 1, world.getMinY() + 1, worldMaxY);

            player.teleportTo(world,
                    targetX + 0.5,
                    targetY,
                    targetZ + 0.5,
                    EnumSet.noneOf(Relative.class),
                    player.getYRot(),
                    player.getXRot(),
                    true);

            String biomeId = biomeId(world.getBiome(new BlockPos(targetX, targetY, targetZ)));
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
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
            ctx.getSource().sendFailure(Component.literal("[latdev] tpBand error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int probe(CommandContext<CommandSourceStack> ctx) {
        try {
            CommandSourceStack source = ctx.getSource();
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel world = source.getLevel();

            int requestedRadius = IntegerArgumentType.getInteger(ctx, "radiusBlocks");
            int requestedSamples = IntegerArgumentType.getInteger(ctx, "samples");
            int radiusBlocks = Mth.clamp(requestedRadius, 32, 8192);
            int samples = Mth.clamp(requestedSamples, 10, 5000);
            int latitudeRadius = authoritativeRadius(source);

            int centerX = player.getBlockX();
            int centerZ = player.getBlockZ();
            int worldMaxY = world.getMinY() + world.getHeight() - 1;
            int sampleY = Mth.clamp(player.getBlockY(), world.getMinY() + 1, worldMaxY);
            long seed = world.getSeed() ^ mix64(player.blockPosition().asLong());
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

                if (world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.BIOMES, false) == null) {
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

            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] probe r=%d n=%d loaded=%d unloaded=%d seed=%d",
                    probeRadius,
                    sampleCount,
                    loadedCount,
                    unloadedCount,
                    sampleSeed)), false);
            source.sendSuccess(() -> Component.literal("[latdev] biomes: " + biomeSummary), false);
            source.sendSuccess(() -> Component.literal("[latdev] bands: " + bandSummary), false);
            return loaded > 0 ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[latdev] probe error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int seamAudit(CommandContext<CommandSourceStack> ctx,
                                 boolean hasEdge,
                                 boolean hasSamples,
                                 boolean hasWaitTicks) {
        try {
            CommandSourceStack source = ctx.getSource();
            String bandA = StringArgumentType.getString(ctx, "bandA");
            String bandB = StringArgumentType.getString(ctx, "bandB");
            String edge = hasEdge ? StringArgumentType.getString(ctx, "edge") : "center";
            int samples = hasSamples ? IntegerArgumentType.getInteger(ctx, "samples") : SEAM_AUDIT_DEFAULT_SAMPLES;
            int waitTicks = hasWaitTicks ? IntegerArgumentType.getInteger(ctx, "waitTicks") : SEAM_AUDIT_DEFAULT_WAIT_TICKS;
            int radius = authoritativeRadius(source);
            return SeamAuditCoordinator.start(source, bandA, bandB, edge, samples, waitTicks, radius);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[latdev] seamAudit error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int startTransectRaw(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        int zStart = Math.abs(IntegerArgumentType.getInteger(ctx, "zStart"));
        int zEnd = Math.abs(IntegerArgumentType.getInteger(ctx, "zEnd"));
        int xHalfWidthChunks = IntegerArgumentType.getInteger(ctx, "xHalfWidthChunks");
        int chunksPerTick = IntegerArgumentType.getInteger(ctx, "chunksPerTick");

        if (zStart > zEnd) {
            int tmp = zStart;
            zStart = zEnd;
            zEnd = tmp;
        }

        xHalfWidthChunks = Mth.clamp(xHalfWidthChunks, 0, 64);
        chunksPerTick = Mth.clamp(chunksPerTick, 1, 50);

        int maxAbsZ = maxAbsZFromBorder(source);
        zStart = Mth.clamp(zStart, 0, maxAbsZ);
        zEnd = Mth.clamp(zEnd, 0, maxAbsZ);

        if (zStart > zEnd) {
            int tmp = zStart;
            zStart = zEnd;
            zEnd = tmp;
        }

        return ChunkPregenerator.startTransect(source.getServer(), source, zStart, zEnd, xHalfWidthChunks, chunksPerTick);
    }

    private static int startTransectDeg(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        int degStart = Math.abs(IntegerArgumentType.getInteger(ctx, "degStart"));
        int degEnd = Math.abs(IntegerArgumentType.getInteger(ctx, "degEnd"));
        int xHalfWidthChunks = IntegerArgumentType.getInteger(ctx, "xHalfWidthChunks");
        int chunksPerTick = IntegerArgumentType.getInteger(ctx, "chunksPerTick");

        if (degStart > degEnd) {
            int tmp = degStart;
            degStart = degEnd;
            degEnd = tmp;
        }

        degStart = Mth.clamp(degStart, 0, 90);
        degEnd = Mth.clamp(degEnd, 0, 90);
        xHalfWidthChunks = Mth.clamp(xHalfWidthChunks, 0, 64);
        chunksPerTick = Mth.clamp(chunksPerTick, 1, 50);

        double radius = source.getLevel().getWorldBorder().getSize() * 0.5;
        int maxAbsZ = maxAbsZFromBorder(source);
        int zStart = Mth.clamp((int) Math.round((degStart / 90.0) * radius), 0, maxAbsZ);
        int zEnd = Mth.clamp((int) Math.round((degEnd / 90.0) * radius), 0, maxAbsZ);

        if (zStart > zEnd) {
            int tmp = zStart;
            zStart = zEnd;
            zEnd = tmp;
        }

        return ChunkPregenerator.startTransect(source.getServer(), source, zStart, zEnd, xHalfWidthChunks, chunksPerTick);
    }

    private static int startSlicePoleNS(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        int centerXChunk = IntegerArgumentType.getInteger(ctx, "centerX");
        int widthChunks = ctx.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("widthChunks"))
                ? IntegerArgumentType.getInteger(ctx, "widthChunks")
                : 1;
        int chunksPerTick = ctx.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("chunksPerTick"))
                ? IntegerArgumentType.getInteger(ctx, "chunksPerTick")
                : 200;

        widthChunks = Mth.clamp(widthChunks, 1, 64);
        chunksPerTick = Mth.clamp(chunksPerTick, 1, 2000);

        int radiusBlocks = maxAbsZFromBorder(source);
        return ChunkPregenerator.startSliceNS(source, centerXChunk, -radiusBlocks, radiusBlocks, widthChunks, chunksPerTick);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> regenLiteral(String name) {
        return Commands.literal(name)
                .executes(ctx -> regenChunk(ctx, 0, false, OptionalLong.empty()))
                .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(0, 8))
                        .executes(ctx -> regenChunk(
                                ctx,
                                IntegerArgumentType.getInteger(ctx, "radiusChunks"),
                                false,
                                OptionalLong.empty()))
                        .then(Commands.argument("biomes", BoolArgumentType.bool())
                                .executes(ctx -> regenChunk(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "radiusChunks"),
                                        BoolArgumentType.getBool(ctx, "biomes"),
                                        OptionalLong.empty()))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> regenChunk(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, "radiusChunks"),
                                                BoolArgumentType.getBool(ctx, "biomes"),
                                                OptionalLong.of(LongArgumentType.getLong(ctx, "seed")))))));
    }

    private static int regenChunk(CommandContext<CommandSourceStack> ctx, int requestedRadius, boolean biomes, OptionalLong seedOverride) {
        try {
            CommandSourceStack source = ctx.getSource();
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel world = source.getLevel();
            ChunkPos center = player.chunkPosition();
            int radius = Mth.clamp(requestedRadius, 0, 8);
            return ChunkRegenerator.regenSquare(world, center.x(), center.z(), radius, biomes, seedOverride, source);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[latdev] regenChunk error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int biomePngDefault(CommandContext<CommandSourceStack> ctx) {
        return startBiomePngExport(ctx.getSource(), 64, 64);
    }

    private static int biomePngWithStep(CommandContext<CommandSourceStack> ctx) {
        int stepBlocks = IntegerArgumentType.getInteger(ctx, "stepBlocks");
        return startBiomePngExport(ctx.getSource(), stepBlocks, 64);
    }

    private static int biomePngWithStepAndY(CommandContext<CommandSourceStack> ctx) {
        int stepBlocks = IntegerArgumentType.getInteger(ctx, "stepBlocks");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        return startBiomePngExport(ctx.getSource(), stepBlocks, y);
    }

    private static int biomePngYDefault(CommandContext<CommandSourceStack> ctx) {
        return startBiomePngYExport(ctx.getSource(), 32);
    }

    private static int biomePngYWithY(CommandContext<CommandSourceStack> ctx) {
        int y = IntegerArgumentType.getInteger(ctx, "y");
        return startBiomePngYExport(ctx.getSource(), y);
    }

    private static int startBiomePngExport(CommandSourceStack source, int stepBlocks, int y) {
        int clampedStep = Mth.clamp(stepBlocks, 8, 512);
        int clampedY = Mth.clamp(y, 0, 320);
        int radiusBlocks = authoritativeRadius(source);
        if (radiusBlocks <= 0) {
            source.sendFailure(Component.literal("[latdev] biomePng failed: invalid radius " + radiusBlocks));
            return 0;
        }

        long seed = source.getLevel().getSeed();
        source.sendSuccess(() -> Component.literal("[latdev] biomePng start seed=" + seed
                + " R=" + radiusBlocks
                + " step=" + clampedStep
                + " y=" + clampedY), false);

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return BiomePreviewExporter.export(
                                source.getLevel(),
                                radiusBlocks,
                                clampedStep,
                                clampedY,
                                source.getServer().getServerDirectory());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .whenComplete((result, throwable) -> source.getServer().execute(() -> {
                    if (throwable != null) {
                        Throwable cause = throwable instanceof RuntimeException && throwable.getCause() != null
                                ? throwable.getCause()
                                : throwable;
                        source.sendFailure(Component.literal("[latdev] biomePng failed: " + cause.getMessage()));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal("[latdev] biomePng done file="
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

    private static int startBiomePngYExport(CommandSourceStack source, int y) {
        int clampedY = Mth.clamp(y, 0, 320);
        int radiusBlocks = authoritativeRadius(source);
        if (radiusBlocks <= 0) {
            source.sendFailure(Component.literal("[latdev] biomePngY failed: invalid radius " + radiusBlocks));
            return 0;
        }

        long seed = source.getLevel().getSeed();
        source.sendSuccess(() -> Component.literal("[latdev] biomePngY start seed=" + seed
                + " R=" + radiusBlocks
                + " step=64"
                + " y=" + clampedY), false);

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return BiomePreviewExporter.export(
                                source.getLevel(),
                                radiusBlocks,
                                64,
                                clampedY,
                                source.getServer().getServerDirectory());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .whenComplete((result, throwable) -> source.getServer().execute(() -> {
                    if (throwable != null) {
                        Throwable cause = throwable instanceof RuntimeException && throwable.getCause() != null
                                ? throwable.getCause()
                                : throwable;
                        source.sendFailure(Component.literal("[latdev] biomePngY failed: " + cause.getMessage()));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal("[latdev] biomePngY done file="
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

    private static int pauseTransect(CommandContext<CommandSourceStack> ctx) {
        return ChunkPregenerator.pauseJob(ctx.getSource());
    }

    private static int resumeTransect(CommandContext<CommandSourceStack> ctx) {
        return ChunkPregenerator.resumeJob(ctx.getSource());
    }

    private static int stopTransect(CommandContext<CommandSourceStack> ctx) {
        return ChunkPregenerator.stopJob(ctx.getSource());
    }

    private static int statusTransect(CommandContext<CommandSourceStack> ctx) {
        return ChunkPregenerator.status(ctx.getSource());
    }

    private static int setBudgetMs(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int ms = IntegerArgumentType.getInteger(ctx, "ms");
        if (ms < 1 || ms > 50) {
            source.sendFailure(Component.literal("[latdev] budgetMs must be in [1..50]"));
            return 0;
        }
        return ChunkPregenerator.setDefaultBudgetMs(source, ms);
    }

    private static int setBudgetAutoOn(CommandContext<CommandSourceStack> ctx) {
        return ChunkPregenerator.setAutoBudget(ctx.getSource(), true);
    }

    private static int setBudgetAutoOff(CommandContext<CommandSourceStack> ctx) {
        return ChunkPregenerator.setAutoBudget(ctx.getSource(), false);
    }

    private static final DateTimeFormatter EXPLAIN_TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** Writes explain output to run/latdev/explain/<timestamp>_x<X>_z<Z>.txt and latest.txt. Returns relative path string for user feedback, or null on failure. */
    private static String writeExplainLog(CommandSourceStack source, String header, String summary, String drivers, int x, int z) {
        try {
            Path explainDir = source.getServer().getServerDirectory().resolve("latdev").resolve("explain");
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

    private static int authoritativeRadius(CommandSourceStack source) {
        int borderRadius = maxAbsZFromBorder(source);
        int activeRadius = LatitudeBiomes.getActiveRadiusBlocks();
        if (activeRadius > 0) {
            return Mth.clamp(activeRadius, 1, Math.max(1, borderRadius));
        }
        return Math.max(1, borderRadius);
    }

    private static int maxAbsZFromBorder(CommandSourceStack source) {
        WorldBorder border = source.getLevel().getWorldBorder();
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

    private static String biomeId(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.identifier().toString()).orElse("?");
    }

    private static SurfaceTruth resolveSurfaceTruth(ServerLevel world, int x, int z) {
        int top = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (top < world.getMinY()) {
            return new SurfaceTruth(false, "n/a(surface)", "n/a(surface)", false, Integer.MIN_VALUE);
        }
        BlockPos surfacePos = new BlockPos(x, top, z);
        BlockState blockState = world.getBlockState(surfacePos);
        FluidState fluidState = world.getFluidState(surfacePos);
        String surfaceBlockId = blockId(world, blockState);
        String surfaceFluidId = fluidId(world, fluidState);
        boolean isWaterSurface = fluidState.is(FluidTags.WATER);
        return new SurfaceTruth(true, surfaceBlockId, surfaceFluidId, isWaterSurface, top);
    }

    private static String blockId(ServerLevel world, BlockState state) {
        Identifier id = world.registryAccess().lookupOrThrow(Registries.BLOCK).getKey(state.getBlock());
        return id != null ? id.toString() : "minecraft:air";
    }

    private static String fluidId(ServerLevel world, FluidState state) {
        Identifier id = world.registryAccess().lookupOrThrow(Registries.FLUID).getKey(state.getType());
        return id != null ? id.toString() : "minecraft:empty";
    }

    private record SurfaceTruth(
            boolean available,
            String surfaceBlockId,
            String surfaceFluidId,
            boolean waterSurface,
            int surfaceY) {
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
