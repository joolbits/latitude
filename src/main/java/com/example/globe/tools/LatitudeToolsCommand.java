package com.example.globe.tools;

import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeStructureLocateService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * Latitude's shipping operator commands, rooted at {@code /latitude}.
 *
 * <p>This surface is packaged into public release artifacts, so every command here is
 * synchronous, emits only chat output, and can never arm itself. The operator tree requires
 * permission; the separate locate-teleport action is authorized by a player-bound one-time token.
 * It contains no recording, sentinel, or auto-harness behavior. See
 * {@code docs/release/artifact-content-policy.md}, which is project law.</p>
 *
 * <p>The development-only {@code /latdev} tree is a separate root in the dev package, which
 * {@code build.gradle} excludes from release artifacts. Nothing in this file may reference that
 * package: it would drag excluded classes into the public jar, and a class-level reference would
 * force-load a sentinel during static initialization.</p>
 */
public final class LatitudeToolsCommand {
    private static final List<String> BAND_NAMES = LatitudeBands.canonicalIds();
    private static final List<String> EDGE_NAMES = List.of("center", "low", "high");
    private static final int WINDSWEPT_RUGGED_THRESH = 8;
    private static final int WINDSWEPT_RUGGED_HYST = 2;

    private LatitudeToolsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("latitude_locate_teleport")
                        .then(Commands.argument("token", StringArgumentType.word())
                                .executes(context -> LatitudeStructureLocateService.runPendingTeleport(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "token")))));
        dispatcher.register(
                Commands.literal("latitude")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(LatitudeToolsCommand::help)
                        .then(Commands.literal("help").executes(LatitudeToolsCommand::help))
                        .then(Commands.literal("flyspeed")
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                        .executes(LatitudeToolsCommand::setFlySpeed)))
                        .then(Commands.literal("tpLat")
                                .then(Commands.argument("signedDegrees", DoubleArgumentType.doubleArg(-90.0, 90.0))
                                        .executes(ctx -> tpLat(ctx, false))
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> tpLat(ctx, true)))))
                        .then(Commands.literal("here").executes(LatitudeToolsCommand::here))
                        .then(Commands.literal("explainHere").executes(LatitudeToolsCommand::explainHere))
                        .then(Commands.literal("tpBand")
                                .then(Commands.argument("band", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(BAND_NAMES, builder))
                                        .executes(ctx -> tpBand(ctx, false))
                                        .then(Commands.argument("edge", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(EDGE_NAMES, builder))
                                                .executes(ctx -> tpBand(ctx, true)))))
                        .then(Commands.literal("probe")
                                .then(Commands.argument("radiusBlocks", IntegerArgumentType.integer())
                                        .then(Commands.argument("samples", IntegerArgumentType.integer())
                                                .executes(LatitudeToolsCommand::probe)))));
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal(String.join("\n",
                "[latitude] operator commands:",
                "  /latitude here                      - latitude, band, and terrain readout at your position",
                "  /latitude explainHere               - why this biome was chosen here",
                "  /latitude probe <radius> <samples>  - sample nearby biome and band distribution",
                "  /latitude tpLat <deg> [x]           - teleport to a signed latitude in degrees",
                "  /latitude tpBand <band> [edge]      - teleport to a latitude band",
                "  /latitude flyspeed <1-5>            - set your flying speed",
                "  /latitude help                      - this list")), false);
        return 1;
    }

    private static int setFlySpeed(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            int level = IntegerArgumentType.getInteger(ctx, "level");
            player.getAbilities().setFlyingSpeed(0.05f * (float) level);
            player.onUpdateAbilities();
            ctx.getSource().sendSuccess(
                    () -> Component.literal("[latitude] Fly speed set to " + level),
                    false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[latitude] flyspeed error: " + e.getMessage()));
            return 0;
        }
    }

    private static int tpLat(CommandContext<CommandSourceStack> ctx, boolean hasX) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel world = source.getLevel();
            WorldBorder border = world.getWorldBorder();
            double radius = LatitudeMath.worldRadiusBlocks(border);
            double requestedDegrees = DoubleArgumentType.getDouble(ctx, "signedDegrees");
            double requestedX = hasX ? DoubleArgumentType.getDouble(ctx, "x") : player.getX();

            LatitudeToolsMath.LatitudeTarget target = LatitudeToolsMath.latitudeTarget(
                    requestedDegrees,
                    border.getCenterZ(),
                    radius,
                    border.getMinZ(),
                    border.getMaxZ(),
                    1.0);
            int targetX = LatitudeToolsMath.safeHorizontalBlock(
                    requestedX,
                    border.getMinX(),
                    border.getMaxX(),
                    1.0);
            int targetZ = target.blockZ();

            world.getChunkSource().getChunk(
                    Math.floorDiv(targetX, 16),
                    Math.floorDiv(targetZ, 16),
                    ChunkStatus.FULL,
                    true);
            int topY = world.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    targetX,
                    targetZ);
            int worldMaxY = world.getMinY() + world.getHeight() - 1;
            int targetY = Mth.clamp(topY + 1, world.getMinY() + 1, worldMaxY);

            player.teleportTo(
                    world,
                    targetX + 0.5,
                    targetY,
                    targetZ + 0.5,
                    EnumSet.noneOf(Relative.class),
                    player.getYRot(),
                    player.getXRot(),
                    true);

            double achievedDegrees = LatitudeToolsMath.signedLatitudeDegrees(
                    targetZ + 0.5,
                    border.getCenterZ(),
                    radius);
            String biome = biomeId(world.getBiome(new BlockPos(targetX, targetY, targetZ)));
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latitude] tpLat requested=%+.6f° achieved=%+.6f° R=%.3f centerZ=%.3f -> x=%d y=%d z=%d biome=%s",
                    requestedDegrees,
                    achievedDegrees,
                    radius,
                    border.getCenterZ(),
                    targetX,
                    targetY,
                    targetZ,
                    biome)), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("[latitude] tpLat rejected: " + e.getMessage()));
            return 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[latitude] tpLat error: " + e.getMessage()));
            return 0;
        }
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

            BandTarget band = BandTarget.fromZ(radius, pos.getZ());
            int authorityBandIndex = LatitudeBiomes.authoritativeLandBandIndex(pos.getX(), pos.getZ(), radius);
            LatitudeBands.Band authorityBand = LatitudeBiomes.bandFromIndex(authorityBandIndex);
            String biomeId = biomeId(world.getBiome(pos));
            boolean mountainLike = isMountainLikeBiome(biomeId);
            double uplandT = LatitudeBiomes.uplandRampForY(pos.getY());
            double savUplandChance = Math.max(0.0, Math.min(1.0, uplandT));
            boolean savUplandActive = savUplandChance > 0.0;
            String savannaDebug = LatitudeBiomes.debugSavannaUplandDecision(pos.getX(), pos.getZ(), pos.getY());
            net.minecraft.world.level.levelgen.RandomState noiseConfig = world.getChunkSource().randomState();
            net.minecraft.world.level.biome.Climate.Sampler sampler = noiseConfig.createClimateSampler(
                    net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED);
            net.minecraft.world.level.chunk.ChunkGenerator cg = world.getChunkSource().getGenerator();
            net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator ng = cg instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator n ? n : null;
            String savannaRule = LatitudeBiomes.debugSavannaRule(sampler, ng, noiseConfig, world, pos.getX(), pos.getZ());
            RuggednessSensor.Measurement ruggedness = RuggednessSensor.measure(world, pos, 24);
            double bumpinessScore = ruggedness.robustDelta(); // robust second-highest delta

            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latitude] here x=%d y=%d z=%d deg=%.2f band=%s(idx=%d) authorityBand=%s(idx=%d) cut=%.2f..%.2f t=%.4f mtnLike=%s uplandT=%.3f savUpland=%s chance=%.3f biome=%s",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    deg,
                    band.argName,
                    band.ordinal(),
                    authorityBand.id(),
                    authorityBandIndex,
                    band.lowDeg,
                    band.highDeg,
                    t,
                    mountainLike,
                    uplandT,
                    savUplandActive,
                    savUplandChance,
                    biomeId)), false);
            source.sendSuccess(() -> Component.literal("[latitude] here savUplandDebug " + savannaDebug), false);
            source.sendSuccess(() -> Component.literal("[latitude] here savannaRule " + savannaRule), false);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latitude] here rugged x=%d z=%d ring=%d topY[c=%d n=%d s=%d e=%d w=%d ne=%d nw=%d se=%d sw=%d] dMax=%d dMean=%.2f axis=%.2f robust=%d",
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
                    "[latitude] dashboard bumpiness=%.2f robustDelta=%d dMax=%d dMean=%.2f thresh=%d hyst=%d → windswept_if>=%.0f",
                    bumpinessScore,
                    ruggedness.robustDelta(),
                    ruggedness.maxAbsDelta(),
                    ruggedness.meanAbsDelta(),
                    WINDSWEPT_RUGGED_THRESH,
                    WINDSWEPT_RUGGED_HYST,
                    (double) WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST)), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[latitude] here error: " + e.getMessage()));
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
            net.minecraft.world.level.biome.Climate.Sampler sampler = noiseConfig.createClimateSampler(
                    net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED);
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

            final String headerText = String.format(Locale.ROOT, "[latitude] explain @ x=%d z=%d", pos.getX(), pos.getZ());
            final String summaryText = "Summary: " + diag.summaryLine();
            final String driversText = "Drivers:\n" + diag.driversBlock();

            source.sendSuccess(() -> Component.literal(headerText), false);
            source.sendSuccess(() -> Component.literal(summaryText), false);
            source.sendSuccess(() -> Component.literal(driversText), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[latitude] explainHere error: " + e.getMessage()));
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
                source.sendFailure(Component.literal("[latitude] tpBand band must be one of: " + String.join("|", BAND_NAMES)));
                return 0;
            }

            EdgeMode edge = EdgeMode.fromArg(edgeArg);
            if (edge == null) {
                source.sendFailure(Component.literal("[latitude] tpBand edge must be one of: " + String.join("|", EDGE_NAMES)));
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
                    "[latitude] tpBand band=%s edge=%s deg=%.2f R=%d -> x=%d y=%d z=%d biome=%s",
                    band.argName,
                    edge.argName,
                    targetDeg,
                    radius,
                    targetX,
                    targetY,
                    targetZ,
                    biomeId)), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[latitude] tpBand error: " + e.getMessage()));
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
            EnumMap<BandTarget, Integer> authorityBandCounts = new EnumMap<>(BandTarget.class);
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
                int authorityBandIndex = LatitudeBiomes.authoritativeLandBandIndex(sampleX, sampleZ, latitudeRadius);
                BandTarget authorityBand = BandTarget.fromBand(LatitudeBiomes.bandFromIndex(authorityBandIndex));
                authorityBandCounts.merge(authorityBand, 1, Integer::sum);
            }

            int loaded = samples - unloaded;
            String biomeSummary = summarizeTopBiomes(biomeCounts, loaded, 10);
            String bandSummary = summarizeBands(bandCounts, loaded);
            String authorityBandSummary = summarizeBands(authorityBandCounts, loaded);
            int loadedCount = loaded;
            int unloadedCount = unloaded;
            int probeRadius = radiusBlocks;
            int sampleCount = samples;
            long sampleSeed = seed;

            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latitude] probe r=%d n=%d loaded=%d unloaded=%d seed=%d",
                    probeRadius,
                    sampleCount,
                    loadedCount,
                    unloadedCount,
                    sampleSeed)), false);
            source.sendSuccess(() -> Component.literal("[latitude] biomes: " + biomeSummary), false);
            source.sendSuccess(() -> Component.literal("[latitude] bands: " + bandSummary), false);
            source.sendSuccess(() -> Component.literal("[latitude] authorityBands: " + authorityBandSummary), false);
            return loaded > 0 ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[latitude] probe error: " + e.getMessage()));
            return 0;
        }
    }

    private static int authoritativeRadius(CommandSourceStack source) {
        return LatitudeToolsMath.productionLatitudeRadius(
                LatitudeBiomes.getActiveRadiusBlocks(),
                LatitudeMath.worldRadiusBlocks(source.getLevel().getWorldBorder()));
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
