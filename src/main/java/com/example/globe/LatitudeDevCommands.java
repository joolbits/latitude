package com.example.globe;

import com.example.globe.adapter.geo.GeoAuthorityProvider;
import com.example.globe.adapter.geo.GeoSummaryProvider;
import com.example.globe.core.CrevasseLocator;
import com.example.globe.core.GeoSurveyNarrator;
import com.example.globe.core.GlacialBlend;
import com.example.globe.core.GlacialMarkScan;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PowderRoofTrap;
import com.example.globe.core.SurveyGroundTruth;
import com.example.globe.core.climate.ClimateAuthority;
import com.example.globe.core.climate.ClimateSummary;
import com.example.globe.core.geo.GeoAuthority;
import com.example.globe.core.geo.GeoSummary;
import com.example.globe.mixin.ChunkGeneratorAccessor;
import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeBiomes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;

/**
 * Shippable subset of the dev `/latdev` command — band/edge teleport + here/probe readouts, so testers can jump
 * between latitude bands and to the E/W edge without the heavy dev toolchain (the full
 * {@code dev.LatitudeDevCommand} pulls in the seam auditor + PNG exporter and is stripped from the release jar).
 *
 * <p>Registration policy (never in a dev environment — there the full command owns {@code /latdev}):
 * <ul>
 *   <li>Auto-ON for pre-release builds (version contains beta/alpha/rc/pre/snapshot), so testers always have the
 *       teleport/readout tools without touching launch args.</li>
 *   <li>Auto-OFF for stable releases, so normal players never see it.</li>
 *   <li>Explicit override wins either way: {@code -Dlatitude.devCommands=true} force-enables (e.g. on a stable
 *       jar), {@code -Dlatitude.devCommands=false} force-disables (e.g. on a beta jar).</li>
 * </ul>
 * The commands still require command permission (cheats/op). All latitude math uses the Z (latitude) radius, so
 * it is correct on Mercator.
 */
public final class LatitudeDevCommands {
    private LatitudeDevCommands() {}

    public static void registerIfEnabled(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            // S31 (dev/shippable split fix, logged S27 finding (c)): the full dev.LatitudeDevCommand owns
            // /latdev in dev, but the shippable tree carries tools the dev tree lacks (locateCrevasse,
            // markGlacial, tpxz...) that dev-lane/headless diagnosis needs. Register it under /latdev2.
            register(dispatcher, "latdev2");
            return;
        }
        if (!devCommandsEnabled()) {
            return;
        }
        register(dispatcher, "latdev");
    }

    private static boolean devCommandsEnabled() {
        String explicit = System.getProperty("latitude.devCommands");
        if (explicit != null) {
            return Boolean.parseBoolean(explicit); // explicit -Dlatitude.devCommands=true/false always wins
        }
        return isPrereleaseBuild(); // otherwise on for beta/alpha/rc builds, off for stable
    }

    private static boolean isPrereleaseBuild() {
        return FabricLoader.getInstance().getModContainer("globe")
                .map(c -> c.getMetadata().getVersion().getFriendlyString().toLowerCase(Locale.ROOT))
                .map(v -> v.contains("beta") || v.contains("alpha") || v.contains("-rc")
                        || v.contains("-pre") || v.contains("snapshot"))
                .orElse(false);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String rootName) {
        dispatcher.register(Commands.literal(rootName)
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
                .then(Commands.literal("tphemi")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.List.of("ns", "ew", "zero"), b))
                                .executes(ctx -> tpHemi(ctx, null))
                                .then(Commands.argument("side", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.List.of("n", "s", "e", "w"), b))
                                        .executes(ctx -> tpHemi(ctx, StringArgumentType.getString(ctx, "side"))))))
                .then(Commands.literal("tppole")
                        .then(Commands.argument("hemi", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.List.of("n", "s"), b))
                                .executes(ctx -> tpPole(ctx, 84.0))
                                .then(Commands.argument("deg", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 90.0))
                                        .executes(ctx -> tpPole(ctx, com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "deg"))))))
                .then(Commands.literal("probe").executes(LatitudeDevCommands::probe))
                .then(Commands.literal("survey").executes(LatitudeDevCommands::survey))
                .then(Commands.literal("locateCrevasse")
                        .executes(ctx -> locateGlacialCarver(ctx, false, CrevasseLocator.DEFAULT_SEARCH_RADIUS_CHUNKS))
                        .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(1, CrevasseLocator.DEFAULT_SEARCH_RADIUS_CHUNKS))
                                .executes(ctx -> locateGlacialCarver(ctx, false, IntegerArgumentType.getInteger(ctx, "radiusChunks")))))
                .then(Commands.literal("locateTunnel")
                        .executes(ctx -> locateGlacialCarver(ctx, true, CrevasseLocator.DEFAULT_SEARCH_RADIUS_CHUNKS))
                        .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(1, CrevasseLocator.DEFAULT_SEARCH_RADIUS_CHUNKS))
                                .executes(ctx -> locateGlacialCarver(ctx, true, IntegerArgumentType.getInteger(ctx, "radiusChunks")))))
                .then(Commands.literal("markGlacial")
                        .executes(ctx -> markGlacial(ctx, DEFAULT_MARK_RADIUS_CHUNKS))
                        .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(1, MAX_MARK_RADIUS_CHUNKS))
                                .executes(ctx -> markGlacial(ctx, IntegerArgumentType.getInteger(ctx, "radiusChunks")))
                                // S31 coordinate form: scan around explicit block coords instead of the caller --
                                // works from the DEDICATED-SERVER CONSOLE (no player) and for remote spot checks.
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> markGlacialAt(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "radiusChunks"),
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "z")))))))
                .then(Commands.literal("tpxz")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(LatitudeDevCommands::tpXz)))));
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
                "[latdev] here | probe | survey | tpband <band> [center|low|high] | tpedge <west|east> [frac]"
                        + " | tphemi <ns|ew|zero> [n|s|e|w] | tppole <n|s> [deg]"
                        + " | locateCrevasse [radiusChunks] | locateTunnel [radiusChunks]"
                        + " | markGlacial [radiusChunks [x z]] | tpxz <x> <z>"), false);
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

    /** Teleports to the equator (ns), the prime meridian (ew), or exactly (0,0) (zero), so a tester can walk
     * across the line and trigger the hemisphere title. {@code side} lands ~40 blocks on one side of the line
     * instead of exactly on it (default: south of the equator / west of the meridian) so walking the other way
     * crosses it; {@code zero} ignores {@code side} and lands on the exact corner. */
    private static int tpHemi(CommandContext<CommandSourceStack> ctx, String side) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel world = src.getLevel();
            String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(Locale.ROOT);
            String s = side == null ? "" : side.toLowerCase(Locale.ROOT);
            final int offset = 40;
            int targetX;
            int targetZ;
            switch (mode) {
                case "ns" -> {
                    targetX = Mth.floor(player.getX());
                    targetZ = s.startsWith("n") ? -offset : offset; // default: south of z=0
                }
                case "ew" -> {
                    targetZ = Mth.floor(player.getZ());
                    targetX = s.startsWith("e") ? offset : -offset; // default: west of x=0
                }
                case "zero" -> {
                    targetX = 0;
                    targetZ = 0;
                }
                default -> {
                    src.sendFailure(Component.literal("[latdev] tphemi mode must be one of: ns|ew|zero"));
                    return 0;
                }
            }

            world.getChunkSource().getChunk(Math.floorDiv(targetX, 16), Math.floorDiv(targetZ, 16), ChunkStatus.FULL, true);
            int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
            int targetY = Mth.clamp(topY + 1, world.getMinY() + 1, world.getMinY() + world.getHeight() - 1);
            player.teleportTo(world, targetX + 0.5, targetY, targetZ + 0.5,
                    EnumSet.noneOf(Relative.class), player.getYRot(), player.getXRot(), true);

            int zRadius = latitudeRadius(world);
            int xr0 = LatitudeBiomes.getActiveXRadiusBlocks();
            int xRadius = xr0 > 0 ? xr0 : zRadius;
            double lonDeg = xRadius > 0 ? Mth.clamp((double) targetX / xRadius * 180.0, -180.0, 180.0) : 0.0;
            double latDeg = zRadius > 0 ? Mth.clamp((double) targetZ / zRadius * 90.0, -90.0, 90.0) : 0.0;
            String biome = biomeId(world, new BlockPos(targetX, targetY, targetZ));
            String modeF = mode;
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] -> hemi %s  lat=%.1f° lon=%.1f°  x=%d y=%d z=%d  biome=%s",
                    modeF, latDeg, lonDeg, targetX, targetY, targetZ, biome)), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] tphemi failed: " + e.getMessage()));
            return 0;
        }
    }

    /** Teleports toward a pole at the given latitude (default 84°, just before the 85° snow onset) so a tester
     * can walk poleward into the 85->90° polar hazard/whiteout ramp; keeps the player's current X. */
    private static int tpPole(CommandContext<CommandSourceStack> ctx, double deg) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel world = src.getLevel();
            String hemi = StringArgumentType.getString(ctx, "hemi").toLowerCase(Locale.ROOT);
            int sign;
            if (hemi.startsWith("n")) {
                sign = -1; // North = -Z
            } else if (hemi.startsWith("s")) {
                sign = 1;
            } else {
                src.sendFailure(Component.literal("[latdev] tppole hemi must be one of: n|s"));
                return 0;
            }
            double clampedDeg = Mth.clamp(deg, 0.0, 90.0);
            int zRadius = latitudeRadius(world);
            int targetZ = sign * (int) Math.round(clampedDeg / 90.0 * zRadius);
            int targetX = Mth.floor(player.getX());

            world.getChunkSource().getChunk(Math.floorDiv(targetX, 16), Math.floorDiv(targetZ, 16), ChunkStatus.FULL, true);
            int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
            int targetY = Mth.clamp(topY + 1, world.getMinY() + 1, world.getMinY() + world.getHeight() - 1);
            player.teleportTo(world, targetX + 0.5, targetY, targetZ + 0.5,
                    EnumSet.noneOf(Relative.class), player.getYRot(), player.getXRot(), true);

            String biome = biomeId(world, new BlockPos(targetX, targetY, targetZ));
            String hemiLabel = sign < 0 ? "N" : "S";
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] -> %.1f°%s pole  x=%d y=%d z=%d  biome=%s  (walk poleward into the 85->90° ramp)",
                    clampedDeg, hemiLabel, targetX, targetY, targetZ, biome)), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] tppole failed: " + e.getMessage()));
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

    /**
     * Prints a short plain-language geography briefing for the player's current column, sampling the
     * live GeoAuthority (reused from the terrain provider) plus a ClimateAuthority derived from it, and
     * a small 4-point ring ~200 blocks out for range/coast context. All phrasing lives in the pure
     * {@link GeoSurveyNarrator}; this method only fetches the numbers and prints the lines.
     */
    private static int survey(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel world = src.getLevel();

            // The geography brain is only "on" when geoV2 armed the real GeoAuthority provider; otherwise
            // the terrain the player sees is vanilla/Terralith, and explaining GeoAuthority intent would lie.
            GeoSummaryProvider geoProvider = LatitudeBiomes.geoProviderForTerrain();
            if (!(geoProvider instanceof GeoAuthorityProvider geoAuthProvider)) {
                src.sendFailure(Component.literal(
                        "[latdev] survey unavailable — the geography brain isn't active in this world "
                                + "(start a fresh 2.0 world with geoV2 enabled to explain the terrain here)."));
                return 0;
            }
            GeoAuthority geo = geoAuthProvider.authority();
            ClimateAuthority climate = new ClimateAuthority(geo);

            int x = Mth.floor(player.getX());
            int z = Mth.floor(player.getZ());
            int zRadius = latitudeRadius(world);

            GeoSummary g = geo.sample(x, z);
            ClimateSummary c = climate.sample(x, z, g);

            // S25 addendum (owner screenshot: "over open water... nearest land roughly 576 blocks off" at
            // 78N on solid snowy plains): "where you are" must be a REALIZED fact, not the brain's intent --
            // on geoV2 worlds intent vs realized terrain diverge in wide bands (the standing calibration
            // finding). Realized = the surface block actually holds water (heightmap top; MOTION_BLOCKING
            // counts fluids, so top.below() is the surface block) OR the LIVE biome is ocean-family. The
            // deep-story inputs (plates/arcs/winds/currents) stay intent -- legitimately the brain's domain;
            // pure law + table: SurveyGroundTruth.
            BlockPos surfaceTop = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
            boolean surfaceIsWater = world.getFluidState(surfaceTop.below())
                    .is(net.minecraft.tags.FluidTags.WATER);
            boolean biomeIsOceanFamily = world.getBiome(surfaceTop).is(net.minecraft.tags.BiomeTags.IS_OCEAN);
            boolean realizedOcean = SurveyGroundTruth.realizedOcean(surfaceIsWater, biomeIsOceanFamily);
            boolean dropCoastDistanceLine =
                    SurveyGroundTruth.dropCoastDistanceLine(g.isOceanIntent(), realizedOcean);

            // Small context ring: 4 probes ~200 blocks out (N/E/S/W).
            final int ring = 200;
            int[][] offsets = {{0, -ring}, {ring, 0}, {0, ring}, {-ring, 0}};
            double ringMtnMax = 0.0;
            int oceanCount = 0;
            for (int[] o : offsets) {
                GeoSummary rg = geo.sample(x + o[0], z + o[1]);
                ringMtnMax = Math.max(ringMtnMax, rg.mountainIntent01());
                if (rg.isOceanIntent()) {
                    oceanCount++;
                }
            }
            double ringOceanFraction = oceanCount / (double) offsets.length;

            double absDeg = Mth.clamp(Math.abs((double) z) / Math.max(1, zRadius) * 90.0, 0.0, 90.0);
            GeoSurveyNarrator.Input in = new GeoSurveyNarrator.Input(
                    absDeg,
                    z < 0, // North = -Z
                    c.band() == null ? null : c.band().name(),
                    c.climateClass(),
                    g.land01(),
                    realizedOcean, // S25: "where you are" is the realized fact (headline + terrain branch)
                    g.coastDistanceBlocks(),
                    g.mountainIntent01(),
                    g.ruggednessIntent01(),
                    g.islandArc01(),
                    g.shelf01(),
                    g.archipelago01(),
                    c.temperature01(),
                    c.precipitation01(),
                    c.continentality01(),
                    c.prevailingWindX(),
                    c.prevailingWindZ(),
                    c.upwindOceanFetchBlocks(),
                    c.windwardLift01(),
                    c.rainShadow01(),
                    c.altitudeCooling01(),
                    c.currentModifierSigned(),
                    zRadius,
                    ringMtnMax,
                    ringOceanFraction);

            for (String line : GeoSurveyNarrator.narrate(in)) {
                // S25 addendum: the traveler's-note distance is computed FROM the intent coast field; on
                // intent-vs-realized divergence the number describes a coastline that is not there (the
                // owner's "576 blocks to land" while standing on it) -- drop the line, keep the narrator
                // unchanged.
                if (dropCoastDistanceLine && line.startsWith("Traveler's note:")) {
                    continue;
                }
                src.sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] survey failed: " + e.getMessage()));
            return 0;
        }
    }

    // --- S25(A) /latdev locateCrevasse | locateTunnel (Peetsa 2026-07-20, TEST 117: "I still can't find any
    // --- crevasses. Can we add a lat dev locate command?") ------------------------------------------------

    /** The legacy globe settings key ({@code stable(globe:overworld)} = the pre-2.0 15000-radius line); on it
     *  the B-9 legacy strip empties every raw carver list poleward of the polar cap, so the appended glacial
     *  pair sit at list indices 0/1 rather than after the raw biome's own carvers. Mirrors
     *  {@code NoiseChunkGeneratorCarveMixin.GLOBE_SETTINGS_KEY}. */
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_LEGACY_SETTINGS_KEY = ResourceKey.create(
            Registries.NOISE_SETTINGS, Identifier.fromNamespaceAndPath("globe", "overworld"));

    /** The B-9 carver keys, mirroring {@code NoiseChunkGeneratorCarveMixin}. */
    private static final ResourceKey<ConfiguredWorldCarver<?>> GLOBE_CREVASSE_KEY = ResourceKey.create(
            Registries.CONFIGURED_CARVER, Identifier.fromNamespaceAndPath("globe", "crevasse"));
    private static final ResourceKey<ConfiguredWorldCarver<?>> GLOBE_GLACIAL_TUNNELS_KEY = ResourceKey.create(
            Registries.CONFIGURED_CARVER, Identifier.fromNamespaceAndPath("globe", "glacial_tunnels"));

    /** The carve seam's sea-level probe height, mirrored from {@code NoiseChunkGeneratorCarveMixin}. */
    private static final int GLOBE_SEA_LEVEL_PROBE_Y = 63;

    /**
     * Predicts the nearest seed chunk that STARTS a {@code globe:crevasse} (or, {@code tunnels},
     * {@code globe:glacial_tunnels}) arc, by replaying the carve stage's per-seed-chunk decision WITHOUT
     * loading chunks -- the exact gates of {@code NoiseChunkGeneratorCarveMixin.globe$glacialCarversForSeedChunk}
     * (flag, armed radius, barrens-band fray at the min corner, raw-source sea probe at Y63) plus the vanilla
     * seeded start roll ({@link CrevasseLocator#carverStartsAt}: {@code setLargeFeatureSeed(worldSeed + listIndex,
     * cx, cz)} then {@code nextFloat() <= probability} -- 26.2 bytecode ground truth in
     * {@link CrevasseLocator}'s javadoc). The list index is resolved PER CANDIDATE from the raw biome's own
     * carver count (the append lands AFTER the raw list, so crevasse = rawCount, tunnels = rawCount + 1);
     * on the LEGACY settings key the strip empties the raw list at these latitudes (the barrens band sits deep
     * inside the polar cap), so the pair sit at 0/1 there. The probability is read from the live carver
     * registry ({@code config().probability} -- no pinned constant to drift from the JSON).
     *
     * <p><b>Accuracy (stated in the output):</b> this predicts START chunks; the carved arc extends up to
     * {@link CrevasseLocator#CARVER_ARC_REACH_CHUNKS 8} chunks from the start, and local terrain can pinch an
     * arc, so the visible opening may be anywhere along (or occasionally absent from) the predicted arc.
     */
    private static int locateGlacialCarver(CommandContext<CommandSourceStack> ctx, boolean tunnels, int radiusChunks) {
        CommandSourceStack src = ctx.getSource();
        String noun = tunnels ? "glacial tunnel" : "crevasse";
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel world = src.getLevel();
            if (!LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
                src.sendFailure(Component.literal(
                        "[latdev] glacial caves are OFF in this session (-Dlatitude.glacialCavesV1=true to arm) — nothing to locate."));
                return 0;
            }
            final int radius = LatitudeBiomes.getActiveRadiusBlocks();
            if (radius <= 0) {
                src.sendFailure(Component.literal("[latdev] not an armed globe world — no carver prediction here."));
                return 0;
            }
            if (!(world.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator gen)
                    || !(gen.getBiomeSource() instanceof LatitudeBiomeSource)) {
                src.sendFailure(Component.literal(
                        "[latdev] this dimension is not the globe overworld — glacial carvers only append there."));
                return 0;
            }
            var carvers = world.registryAccess().lookupOrThrow(Registries.CONFIGURED_CARVER);
            Optional<Holder.Reference<ConfiguredWorldCarver<?>>> holder =
                    carvers.get(tunnels ? GLOBE_GLACIAL_TUNNELS_KEY : GLOBE_CREVASSE_KEY);
            if (holder.isEmpty()) {
                src.sendFailure(Component.literal("[latdev] the globe:" + (tunnels ? "glacial_tunnels" : "crevasse")
                        + " carver is missing from the registry (broken datapack?)."));
                return 0;
            }
            // Runtime probability read — the same value isStartChunk compares (0.14 crevasse / 0.12 tunnels
            // from the JSON today, but read live so a data retune can never silently split from prediction).
            final float probability = holder.get().value().config().probability;
            final long worldSeed = world.getSeed();
            final boolean legacy = gen.stable(GLOBE_LEGACY_SETTINGS_KEY);
            final BiomeSource rawSource = ((ChunkGeneratorAccessor) (Object) gen).globe$getRawBiomeSource();
            final Climate.Sampler sampler = world.getChunkSource().randomState().sampler();
            final int indexOffset = tunnels ? 1 : 0;

            CrevasseLocator.StartChunkPredicate predicate = (cx, cz) -> {
                int minBlockX = cx << 4;
                int minBlockZ = cz << 4;
                // Gate order = cheap first, exactly the seam's decisions: pure-math blend-onset exit, the
                // SHARED underground glacial blend (S28 -- the exact LatitudeBiomes.glacialBlendColumnApplies
                // the biome swap and carver append ride, one 640-block region field), then the seeded roll,
                // and the (priciest) raw-source sea probe only for roll-winning chunks. AND-chain,
                // order-independent. Swapped OFF the old 64-block surface barrens fray so the locator agrees
                // with the seam the crevasses actually append on (Peetsa 2026-07-20 "a transition").
                double absLatDeg = Math.abs((double) minBlockZ) * 90.0 / radius;
                if (absLatDeg <= GlacialBlend.BLEND_ONSET_DEG) {
                    return false;
                }
                if (!LatitudeBiomes.glacialBlendColumnApplies(minBlockX, minBlockZ, radius)) {
                    return false;
                }
                // The carver-biome lambda's exact sample: raw source field, min-corner quart, quart-Y 0.
                Holder<Biome> carverBiome = rawSource.getNoiseBiome(
                        QuartPos.fromBlock(minBlockX), 0, QuartPos.fromBlock(minBlockZ), sampler);
                int rawCount = 0;
                for (@SuppressWarnings("unused") Holder<ConfiguredWorldCarver<?>> ignored
                        : gen.getBiomeGenerationSettings(carverBiome).getCarvers()) {
                    rawCount++;
                }
                int baseIndex;
                if (legacy) {
                    // S25 sweep REQUIRED-FIX: the legacy strip is CENTER-chunk-keyed at |z| >= POLAR_CAP_START
                    // (14500 = ~87 deg on the legacy radius), NOT band-keyed -- for a seed chunk whose +/-8
                    // center neighborhood reaches equatorward of the cap, those centers carve with the UNSTRIPPED
                    // raw list, so the glacial pair sits at rawCount there (the modern index). Only a seed chunk
                    // whose ENTIRE center neighborhood is poleward of the cap is guaranteed the stripped 0 index.
                    int nearestCenterMinZ = (Math.abs(cz) - 8) * 16; // closest-to-equator center's |minBlockZ|
                    boolean wholeNeighborhoodInCap =
                            Math.abs(nearestCenterMinZ + 8) >= com.example.globe.GlobeRegions.POLAR_CAP_START;
                    baseIndex = wholeNeighborhoodInCap ? 0 : rawCount;
                } else {
                    baseIndex = rawCount; // append order: crevasse = rawCount, tunnels = rawCount + 1
                }
                if (!CrevasseLocator.carverStartsAt(worldSeed, baseIndex + indexOffset, cx, cz, probability)) {
                    return false;
                }
                Holder<Biome> seaProbe = rawSource.getNoiseBiome(
                        QuartPos.fromBlock(minBlockX), QuartPos.fromBlock(GLOBE_SEA_LEVEL_PROBE_Y),
                        QuartPos.fromBlock(minBlockZ), sampler);
                return !seaProbe.is(BiomeTags.IS_OCEAN);
            };

            CrevasseLocator.Hit hit = CrevasseLocator.findNearest(player.getX(), player.getZ(), radiusChunks, predicate);
            if (hit == null) {
                src.sendFailure(Component.literal(String.format(Locale.ROOT,
                        "[latdev] no %s START chunk predicted within %d chunks (~%d blocks). The band lives at 82°+ "
                                + "(barrens country) — tppole first, then locate again.",
                        noun, radiusChunks, radiusChunks * 16)));
                return 0;
            }
            String tpCommand = "/latdev tpxz " + hit.blockX() + " " + hit.blockZ();
            MutableComponent line = Component.literal(String.format(Locale.ROOT,
                    "[latdev] nearest %s START: chunk (%d, %d), block x=%d z=%d — %d blocks away. ",
                    noun, hit.chunkX(), hit.chunkZ(), hit.blockX(), hit.blockZ(), Math.round(hit.distanceBlocks())))
                    .append(Component.literal("[teleport]").withStyle(style -> style
                            .withClickEvent(new ClickEvent.RunCommand(tpCommand))
                            .withUnderlined(true)));
            src.sendSuccess(() -> line, false);
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] (predicts START chunks — the carved arc extends up to %d chunks from the start; "
                            + "walk/dig the area if the opening isn't at the marker)",
                    CrevasseLocator.CARVER_ARC_REACH_CHUNKS)), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] locate " + noun + " failed: " + e.getMessage()));
            return 0;
        }
    }

    /** Surface-teleport to an (x, z) — the clickable target of the locate lines, using the exact house
     *  chunk-load + MOTION_BLOCKING_NO_LEAVES + clamp idiom of {@link #tpBand}/{@link #tpPole}. */
    private static int tpXz(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel world = src.getLevel();
            int targetX = IntegerArgumentType.getInteger(ctx, "x");
            int targetZ = IntegerArgumentType.getInteger(ctx, "z");

            world.getChunkSource().getChunk(Math.floorDiv(targetX, 16), Math.floorDiv(targetZ, 16), ChunkStatus.FULL, true);
            int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
            int targetY = Mth.clamp(topY + 1, world.getMinY() + 1, world.getMinY() + world.getHeight() - 1);
            player.teleportTo(world, targetX + 0.5, targetY, targetZ + 0.5,
                    EnumSet.noneOf(Relative.class), player.getYRot(), player.getXRot(), true);

            String biome = biomeId(world, new BlockPos(targetX, targetY, targetZ));
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] -> x=%d y=%d z=%d  biome=%s", targetX, targetY, targetZ, biome)), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] tpxz failed: " + e.getMessage()));
            return 0;
        }
    }

    private static String biomeId(ServerLevel world, BlockPos pos) {
        return world.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
    }

    // --- S29 /latdev markGlacial (Peetsa 2026-07-20, verbatim: "None of this is working. Locate crevasse and
    // --- teleport just puts me in the same spot... there is no falling through the snow... To make it easier
    // --- just for dev, can you turn on a simple color filter for the trap crevasses -- maybe typing a command
    // --- causes them to glow green?") -------------------------------------------------------------------------

    /** Default scan radius (chunks) -- ~64 blocks each way, a comfortable look around the player. */
    private static final int DEFAULT_MARK_RADIUS_CHUNKS = 4;
    /** Hard cap on the scan radius (chunks) -- ~128 blocks each way (17x17 chunks) to bound the column count. */
    private static final int MAX_MARK_RADIUS_CHUNKS = 8;
    /** Cap on green beacons drawn PER SIGNAL, so a big crevasse field is not a particle storm -- the reported
     *  counts stay the true totals; beyond this we still count but stop drawing. */
    private static final int MARK_MARKER_CAP = 200;
    /** Cap on per-column coordinate chat lines PER SIGNAL (the summary always carries the real totals). */
    private static final int MARK_CHAT_CAP = 10;
    /** TEST128 escape route/tail geometry requires this much measured horizontal context around a cover. */
    private static final int MARK_PHYSICAL_HALO = 3;
    /** Honest safety cap for pathological player-built powder fields; skipped candidates remain reported. */
    private static final int MARK_PHYSICAL_CANDIDATE_SCAN_CAP = 128;
    /** Authored traps carry 27 or 36 covers; this generous cap prevents an unbounded local volume allocation. */
    private static final int MARK_PHYSICAL_COMPONENT_COLUMN_CAP = 128;
    /** Same allocation guard for a sparse but extremely long connected powder component. */
    private static final int MARK_PHYSICAL_COMPONENT_SPAN_CAP = 16;
    /** Tallest open-slot beacon (blocks) -- a deep canyon's blue plume is clipped here to bound particles. */
    private static final int MARK_BEACON_MAX_HEIGHT = 24;

    /**
     * TEST128 ground-truth scan over real, already-loaded blocks. GREEN means a complete physical trap:
     * irregular low-relief powder cover, a deep clear fall and matching cushions under every cover column,
     * safe support, and one block-proven command-free escape route with an intact mining tail and surface plug.
     * No generator plan, legacy bridge shape, or debug counter participates. BLUE remains the independent
     * heightmap signal for a genuinely open crevasse.
     *
     * <p>The broad scan discovers surface-powder components once. Each candidate is then verified in its own
     * bounded full-height block volume with a three-cell halo, so adjacent components are not double-counted
     * and the command does not allocate the entire 256-chunk vertical world at once. Unloaded chunks and scan
     * edges reject conservatively; the command never force-generates proof terrain.
     */
    private static int markGlacial(CommandContext<CommandSourceStack> ctx, int radiusChunks) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            return markGlacialCore(src, src.getLevel(), player.getX(), player.getZ(), radiusChunks);
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] markGlacial failed: " + e.getMessage()));
            return 0;
        }
    }

    /** S31 coordinate form -- no player needed, so the dedicated-server CONSOLE can run ground-truth scans. */
    private static int markGlacialAt(CommandContext<CommandSourceStack> ctx, int radiusChunks, int x, int z) {
        CommandSourceStack src = ctx.getSource();
        try {
            return markGlacialCore(src, src.getLevel(), x, z, radiusChunks);
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] markGlacial failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int markGlacialCore(CommandSourceStack src, ServerLevel world, double centerX, double centerZ,
            int radiusChunks) {
        try {
            if (!LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
                src.sendFailure(Component.literal(
                        "[latdev] glacial caves are OFF in this session (-Dlatitude.glacialCavesV1=true to arm) — nothing to mark."));
                return 0;
            }
            final int radius = LatitudeBiomes.getActiveRadiusBlocks();
            if (radius <= 0) {
                src.sendFailure(Component.literal("[latdev] not an armed globe world — no latitude to scan for glacial features."));
                return 0;
            }
            int r = Mth.clamp(radiusChunks, 1, MAX_MARK_RADIUS_CHUNKS);
            clearGreenMarkers(); // each scan starts a fresh 60 s marker set -- stale glows never mislead
            int centerChunkX = Math.floorDiv(Mth.floor(centerX), 16);
            int centerChunkZ = Math.floorDiv(Mth.floor(centerZ), 16);
            int minChunkX = centerChunkX - r;
            int minChunkZ = centerChunkZ - r;
            int chunksPerSide = 2 * r + 1;
            int originBlockX = minChunkX << 4;
            int originBlockZ = minChunkZ << 4;
            int span = chunksPerSide * 16;

            // Pass 1: read WORLD_SURFACE off already-loaded chunks into a continuous grid (UNLOADED elsewhere).
            int[][] surface = new int[span][span];
            for (int[] row : surface) {
                java.util.Arrays.fill(row, GlacialMarkScan.UNLOADED);
            }
            var chunkSource = world.getChunkSource();
            long scannedColumns = 0L;
            long skippedColumns = 0L;
            int loadedChunks = 0;
            int unloadedChunks = 0;
            for (int ccx = minChunkX; ccx < minChunkX + chunksPerSide; ccx++) {
                for (int ccz = minChunkZ; ccz < minChunkZ + chunksPerSide; ccz++) {
                    LevelChunk chunk = chunkSource.getChunkNow(ccx, ccz);
                    if (chunk == null) {
                        unloadedChunks++;
                        skippedColumns += 256L; // the 16x16 columns we deliberately did NOT force-generate
                        continue;
                    }
                    loadedChunks++;
                    scannedColumns += 256L;
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            int wx = (ccx << 4) + lx;
                            int wz = (ccz << 4) + lz;
                            // S31 off-by-one fix (headless-proven on real sandwiches): ChunkAccess.getHeight
                            // returns the TOP BLOCK Y, one below Level.getHeight's first-air convention this
                            // grid documents -- +1 restores firstAir, so the roof probe starts AT the snow cap
                            // (it used to start on the powder marker below it and walk away downward).
                            surface[wx - originBlockX][wz - originBlockZ] =
                                    chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) + 1;
                        }
                    }
                }
            }

            // Pass 2: discover surface powder once and keep the independent open-crevasse signal.
            int openSlotCount = 0;
            int slotMarkers = 0;
            int slotLines = 0;
            boolean[][] powderSurface = new boolean[span][span];
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int gx = 0; gx < span; gx++) {
                for (int gz = 0; gz < span; gz++) {
                    int own = surface[gx][gz];
                    if (own == GlacialMarkScan.UNLOADED) {
                        continue;
                    }
                    int wx = originBlockX + gx;
                    int wz = originBlockZ + gz;
                    cursor.set(wx, own - 1, wz);
                    powderSurface[gx][gz] =
                            world.getBlockState(cursor).is(Blocks.POWDER_SNOW);
                    int reference = GlacialMarkScan.windowedMax(
                            surface, gx, gz, PowderRoofTrap.REFERENCE_WINDOW_RADIUS);
                    if (!powderSurface[gx][gz]
                            && PowderRoofTrap.isTrapCandidate(own, reference)) {
                        openSlotCount++;
                        int depth = reference - own;
                        int floorY = own - 1;
                        if (slotMarkers < SLOT_MARKER_CAP) {
                            blueSlotMark(world, wx, own, wz);
                            slotMarkers++;
                        }
                        if (slotLines < MARK_CHAT_CAP) {
                            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                                    "[latdev]   BLUE open crevasse (no roof): "
                                            + "x=%d y=%d z=%d (%d deep)",
                                    wx, floorY, wz, depth)), false);
                            slotLines++;
                        }
                    }
                }
            }

            // Pass 3: one global connected component -> one bounded, anchored physical proof.
            List<List<int[]>> powderComponents =
                    markPhysicalPowderComponents(powderSurface, surface);
            PhysicalMarkCensus physical = new PhysicalMarkCensus();
            List<PhysicalMarkedTrap> traps = new ArrayList<>();
            int scannedCandidates = 0;
            for (List<int[]> component : powderComponents) {
                if (scannedCandidates >= MARK_PHYSICAL_CANDIDATE_SCAN_CAP) {
                    physical.reject("CANDIDATE_SCAN_LIMIT");
                    continue;
                }
                scannedCandidates++;
                GlacialMarkScan.PhysicalScanReport report = scanPhysicalComponent(
                        world, surface, originBlockX, originBlockZ, component);
                physical.add(report);
                if (report.validTraps() != 1) {
                    continue;
                }
                int[] representative = GlacialMarkScan.centreRepresentative(component);
                if (representative == null) {
                    continue;
                }
                int roofY = surface[representative[0]][representative[1]] - 1;
                traps.add(new PhysicalMarkedTrap(
                        originBlockX + representative[0],
                        roofY,
                        originBlockZ + representative[1],
                        report.coverColumns(),
                        report.cushionMatches()));
            }

            int roofMarkers = 0;
            int roofLines = 0;
            for (PhysicalMarkedTrap trap : traps) {
                if (roofMarkers < MARK_MARKER_CAP) {
                    greenBeacon(world, trap.x(), trap.roofY(),
                            trap.roofY() + TRAP_PILLAR_HEIGHT, trap.z());
                    roofMarkers++;
                }
                if (roofLines < MARK_CHAT_CAP) {
                    String tpCommand = "/latdev tpxz " + trap.x() + " " + trap.z();
                    MutableComponent trapLine = Component.literal(String.format(Locale.ROOT,
                            "[latdev]   GREEN PHYSICAL TRAP "
                                    + "(covers=%d, cushions=%d, escape=verified): x=%d y=%d z=%d ",
                            trap.coverColumns(), trap.cushionMatches(),
                            trap.x(), trap.roofY(), trap.z()))
                            .append(Component.literal("[teleport]").withStyle(style -> style
                                    .withClickEvent(new ClickEvent.RunCommand(tpCommand))
                                    .withUnderlined(true)));
                    src.sendSuccess(() -> trapLine, false);
                    roofLines++;
                }
            }

            final int fSlot = openSlotCount;
            final long fScanned = scannedColumns;
            final long fSkipped = skippedColumns;
            final int fLoaded = loadedChunks;
            final int fUnloaded = unloadedChunks;
            final int fr = r;
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] markGlacial PHYSICAL TEST128 (r=%d): "
                            + "candidates=%d | valid traps=%d encounters=%d | "
                            + "covers=%d cushions=%d escapeRoutes=%d | partial=%d unsafe=%d | "
                            + "BLUE open crevasses=%d | scanned %d cols (%d chunks), "
                            + "skipped %d cols (%d unloaded)",
                    fr, physical.candidates, physical.validTraps, physical.encounters,
                    physical.coverColumns, physical.cushionMatches,
                    physical.validEscapeRoutes, physical.partialComponents,
                    physical.unsafeComponents, fSlot,
                    fScanned, fLoaded, fSkipped, fUnloaded)), false);
            if (!physical.rejectionReasons.isEmpty()) {
                String rejectionSummary =
                        markPhysicalRejectionSummary(physical.rejectionReasons);
                src.sendSuccess(() -> Component.literal(
                        "[latdev] physical rejection reasons: " + rejectionSummary), false);
            }
            if (physical.validTraps > 0) {
                src.sendSuccess(() -> Component.literal(
                        "[latdev] GREEN is block-proven: deep clear fall, every cushion, "
                                + "safe support, and a command-free exit all verified."), false);
            } else if (physical.candidates > 0) {
                src.sendSuccess(() -> Component.literal(
                        "[latdev] surface-powder candidates exist, but none passed the full physical proof; "
                                + "see the named reasons above."), false);
            } else if (openSlotCount > 0) {
                src.sendSuccess(() -> Component.literal(
                        "[latdev] no surface-powder trap candidates in range; BLUE marks open crevasses only."),
                        false);
            }
            if (roofMarkers >= MARK_MARKER_CAP || slotMarkers >= SLOT_MARKER_CAP) {
                src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "[latdev] (drew at most %d GREEN and %d BLUE markers "
                                + "— the counts above are the real totals)",
                        MARK_MARKER_CAP, SLOT_MARKER_CAP)), false);
            }
            if (physical.candidates == 0 && openSlotCount == 0) {
                double absDeg = Mth.clamp(Math.abs(centerZ) / radius * 90.0, 0.0, 90.0);
                if (scannedColumns == 0L) {
                    src.sendFailure(Component.literal(
                            "[latdev] nothing was LOADED to scan here — walk/fly the area to load chunks, then run markGlacial again."));
                } else if (absDeg <= GlacialBlend.BLEND_ONSET_DEG) {
                    src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                            "[latdev] you're at %.1f° — equatorward of the glacial blend onset (%.0f°); the underground only "
                                    + "turns glacial poleward of there (full by %.0f°). Try /latdev tppole n|s, then markGlacial.",
                            absDeg, GlacialBlend.BLEND_ONSET_DEG, GlacialBlend.BLEND_FULL_DEG)), false);
                } else {
                    src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                            "[latdev] you're at %.1f° (in-band; glacial blend %.0f°->%.0f°) — no safe structural fit "
                                    + "was found in the loaded glacial terrain scanned here. Try a larger radius, "
                                    + "or move to another glacial area and scan again.",
                            absDeg, GlacialBlend.BLEND_ONSET_DEG, GlacialBlend.BLEND_FULL_DEG)), false);
                }
            }
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] markGlacial failed: " + e.getMessage()));
            return 0;
        }
    }

    private record PhysicalMarkedTrap(
            int x, int roofY, int z, int coverColumns, int cushionMatches) {
    }

    private static final class PhysicalMarkCensus {
        private int candidates;
        private int validTraps;
        private int encounters;
        private int coverColumns;
        private int cushionMatches;
        private int validEscapeRoutes;
        private int partialComponents;
        private int unsafeComponents;
        private final Map<String, Integer> rejectionReasons = new LinkedHashMap<>();

        void add(GlacialMarkScan.PhysicalScanReport report) {
            candidates += report.candidates();
            validTraps += report.validTraps();
            encounters += report.encounters();
            coverColumns += report.coverColumns();
            cushionMatches += report.cushionMatches();
            validEscapeRoutes += report.validEscapeRoutes();
            partialComponents += report.partialComponents();
            unsafeComponents += report.unsafeComponents();
            report.rejectionReasons().forEach(
                    (reason, count) -> rejectionReasons.merge(reason, count, Integer::sum));
        }

        void reject(String reason) {
            candidates++;
            partialComponents++;
            rejectionReasons.merge(reason, 1, Integer::sum);
        }
    }

    /** Deterministic cardinal surface-powder components; adjacent covers may differ by one block. */
    private static List<List<int[]>> markPhysicalPowderComponents(
            boolean[][] powderSurface, int[][] firstAir) {
        List<List<int[]>> components = new ArrayList<>();
        boolean[][] seen = new boolean[powderSurface.length][];
        for (int x = 0; x < powderSurface.length; x++) {
            seen[x] = new boolean[powderSurface[x].length];
        }
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = 0; x < powderSurface.length; x++) {
            for (int z = 0; z < powderSurface[x].length; z++) {
                if (!powderSurface[x][z] || seen[x][z]) {
                    continue;
                }
                List<int[]> component = new ArrayList<>();
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.addLast(new int[]{x, z});
                seen[x][z] = true;
                while (!queue.isEmpty()) {
                    int[] cell = queue.removeFirst();
                    component.add(cell);
                    for (int[] direction : directions) {
                        int nx = cell[0] + direction[0];
                        int nz = cell[1] + direction[1];
                        if (nx < 0 || nx >= powderSurface.length
                                || nz < 0 || nz >= powderSurface[nx].length
                                || seen[nx][nz] || !powderSurface[nx][nz]
                                || Math.abs(firstAir[nx][nz] - firstAir[cell[0]][cell[1]]) > 1) {
                            continue;
                        }
                        seen[nx][nz] = true;
                        queue.addLast(new int[]{nx, nz});
                    }
                }
                components.add(List.copyOf(component));
            }
        }
        return List.copyOf(components);
    }

    private static GlacialMarkScan.PhysicalScanReport scanPhysicalComponent(
            ServerLevel world,
            int[][] surface,
            int originBlockX,
            int originBlockZ,
            List<int[]> component) {
        int minX = component.stream().mapToInt(cell -> cell[0]).min().orElse(0);
        int maxX = component.stream().mapToInt(cell -> cell[0]).max().orElse(0);
        int minZ = component.stream().mapToInt(cell -> cell[1]).min().orElse(0);
        int maxZ = component.stream().mapToInt(cell -> cell[1]).max().orElse(0);
        int spanX = maxX - minX + 1;
        int spanZ = maxZ - minZ + 1;
        if (component.size() > MARK_PHYSICAL_COMPONENT_COLUMN_CAP
                || spanX > MARK_PHYSICAL_COMPONENT_SPAN_CAP
                || spanZ > MARK_PHYSICAL_COMPONENT_SPAN_CAP) {
            return rejectedPhysicalReport("COMPONENT_TOO_LARGE");
        }
        if (minX < MARK_PHYSICAL_HALO || minZ < MARK_PHYSICAL_HALO
                || maxX + MARK_PHYSICAL_HALO >= surface.length
                || maxZ + MARK_PHYSICAL_HALO >= surface[0].length) {
            return rejectedPhysicalReport("UNLOADED_OR_SCAN_BOUNDARY");
        }

        int minCoverY = component.stream()
                .mapToInt(cell -> surface[cell[0]][cell[1]] - 1).min().orElse(world.getMinY());
        int maxCoverY = component.stream()
                .mapToInt(cell -> surface[cell[0]][cell[1]] - 1).max().orElse(world.getMinY());
        int sampleMinY = Math.max(world.getMinY(),
                minCoverY - PowderRoofTrap.MAX_SHAFT_DEPTH_BLOCKS - 2);
        int sampleMaxY =
                GlacialMarkScan.physicalSampleMaxYInclusive(maxCoverY, world.getMaxY());
        if (sampleMaxY - sampleMinY < PowderRoofTrap.MIN_SHAFT_DEPTH_BLOCKS + 3) {
            return rejectedPhysicalReport("UNLOADED_OR_SCAN_BOUNDARY");
        }

        int sampleMinX = minX - MARK_PHYSICAL_HALO;
        int sampleMaxX = maxX + MARK_PHYSICAL_HALO;
        int sampleMinZ = minZ - MARK_PHYSICAL_HALO;
        int sampleMaxZ = maxZ + MARK_PHYSICAL_HALO;
        int width = sampleMaxX - sampleMinX + 1;
        int depth = sampleMaxZ - sampleMinZ + 1;
        int height = sampleMaxY - sampleMinY + 1;
        GlacialMarkScan.PhysicalCellKind[][][] cells =
                new GlacialMarkScan.PhysicalCellKind[width][height][depth];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        var chunkSource = world.getChunkSource();
        boolean unknownColumn = false;
        for (int lx = 0; lx < width; lx++) {
            int gx = sampleMinX + lx;
            int worldX = originBlockX + gx;
            for (int lz = 0; lz < depth; lz++) {
                int gz = sampleMinZ + lz;
                int worldZ = originBlockZ + gz;
                boolean loaded = surface[gx][gz] != GlacialMarkScan.UNLOADED
                        && chunkSource.getChunkNow(
                                Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16)) != null;
                unknownColumn |= !loaded;
                for (int ly = 0; ly < height; ly++) {
                    if (!loaded) {
                        cells[lx][ly][lz] =
                                GlacialMarkScan.PhysicalCellKind.UNLOADED;
                        continue;
                    }
                    cursor.set(worldX, sampleMinY + ly, worldZ);
                    BlockState state = world.getBlockState(cursor);
                    cells[lx][ly][lz] = markPhysicalCell(world, cursor, state);
                }
            }
        }
        if (unknownColumn) {
            return rejectedPhysicalReport("UNLOADED_OR_SCAN_BOUNDARY");
        }

        int[] anchor = component.get(0);
        GlacialMarkScan.PhysicalScanReport report =
                GlacialMarkScan.scanPhysicalTrapVolumeAt(
                        cells,
                        PowderRoofTrap.MIN_SHAFT_DEPTH_BLOCKS,
                        anchor[0] - sampleMinX,
                        anchor[1] - sampleMinZ);
        return report.candidates() == 0
                ? rejectedPhysicalReport("ADAPTER_ANCHOR_MISSING") : report;
    }

    private static GlacialMarkScan.PhysicalCellKind markPhysicalCell(
            ServerLevel world, BlockPos pos, BlockState state) {
        if (world.getBlockEntity(pos) != null) {
            return GlacialMarkScan.PhysicalCellKind.BLOCK_ENTITY;
        }
        if (!state.getFluidState().isEmpty()) {
            return GlacialMarkScan.PhysicalCellKind.FLUID;
        }
        if (state.is(Blocks.POWDER_SNOW)) {
            return GlacialMarkScan.PhysicalCellKind.POWDER_SNOW;
        }
        if (state.is(Blocks.SNOW_BLOCK)) {
            return GlacialMarkScan.PhysicalCellKind.SNOW_BLOCK;
        }
        if (state.is(Blocks.SNOW)) {
            return GlacialMarkScan.PhysicalCellKind.SNOW_LAYER;
        }
        if (state.isAir()) {
            return GlacialMarkScan.PhysicalCellKind.AIR;
        }
        if (state.getBlock() instanceof FallingBlock) {
            return GlacialMarkScan.PhysicalCellKind.GRAVITY_SOLID;
        }
        if (state.getCollisionShape(world, pos).isEmpty()) {
            return GlacialMarkScan.PhysicalCellKind.PASSABLE_DRY;
        }
        if (state.isCollisionShapeFullBlock(world, pos)) {
            return GlacialMarkScan.PhysicalCellKind.DRY_SOLID;
        }
        return GlacialMarkScan.PhysicalCellKind.DRY_UNSTABLE;
    }

    private static GlacialMarkScan.PhysicalScanReport rejectedPhysicalReport(String reason) {
        return new GlacialMarkScan.PhysicalScanReport(
                1, 0, 0, 0, 0, 0, 1, 0, Map.of(reason, 1));
    }

    private static String markPhysicalRejectionSummary(Map<String, Integer> reasons) {
        return reasons.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(" "));
    }

    /**
     * Spawns a short vertical stack of bright-green {@code ParticleTypes.HAPPY_VILLAGER} particles (at the low,
     * mid, and high Y) centred on a column -- the "glow green" marker the owner asked for (Peetsa 2026-07-20).
     * {@link ServerLevel#sendParticles} (the {@code <T extends ParticleOptions> int sendParticles(T, double,
     * double, double, int, double, double, double, double)} overload, javap-verified on the 26.2 merged jar)
     * broadcasts to every player tracking the position; {@code HAPPY_VILLAGER} is a {@code SimpleParticleType}
     * (which implements {@code ParticleOptions}), inherently green, so there is no colour argument to get
     * wrong. Bounded to three points regardless of shaft depth, so a deep canyon never becomes a particle
     * fountain.
     */
    /** S33: height (blocks) of the GREEN trap pillar -- tall enough to spot across a snowfield. */
    private static final int TRAP_PILLAR_HEIGHT = 8;
    /** S33: open crevasses outnumber traps ~1000:1, so they are marked sparsely; they are context, not the goal. */
    private static final int SLOT_MARKER_CAP = 40;

    /** S33: a BLUE (soul-flame) short mark for an open crevasse -- unmistakably not the green trap pillar. */
    private static void blueSlotMark(ServerLevel world, int x, int surfaceY, int z) {
        synchronized (GREEN_MARKS) {
            if (GREEN_MARKS.size() < MARKER_QUEUE_CAP) {
                GREEN_MARKS.add(new int[]{x, surfaceY, surfaceY + 2, z, 0, MARK_KIND_SLOT});
            }
        }
        emitMark(world, x, surfaceY, surfaceY + 2, z, MARK_KIND_SLOT);
    }

    private static void greenBeacon(ServerLevel world, int x, int yLo, int yHi, int z) {
        int lo = Math.min(yLo, yHi);
        int hi = Math.max(yLo, yHi);
        // S32 (Peetsa 2026-07-21, TEST 122: markGlacial "located" roofs but "not showing on the world"): a
        // one-shot particle burst fades in ~1 s -- gone before the chat is even closed. Enqueue the column
        // instead; tickGreenMarkers re-emits it every MARKER_REEMIT_TICKS for MARKER_LIFETIME_TICKS, so the
        // green glow LINGERS long enough to walk toward. Emit once immediately for instant feedback.
        synchronized (GREEN_MARKS) {
            if (GREEN_MARKS.size() < MARKER_QUEUE_CAP) {
                GREEN_MARKS.add(new int[]{x, lo, hi, z, 0, MARK_KIND_TRAP});
            }
        }
        emitMark(world, x, lo, hi, z, MARK_KIND_TRAP);
    }

    /** Marker kinds: GREEN = a real trap roof (the goal), BLUE = an open crevasse (context). */
    private static final int MARK_KIND_TRAP = 0;
    private static final int MARK_KIND_SLOT = 1;

    /** Live markers: {x, yLo, yHi, z, ageTicks, kind}. Bounded by {@link #MARKER_QUEUE_CAP}; a new markGlacial
     *  run clears the previous set (see {@code markGlacialCore}) so stale marks never mislead. */
    private static final java.util.List<int[]> GREEN_MARKS = new java.util.ArrayList<>();
    private static final int MARKER_QUEUE_CAP = 400;
    /** How long a marker keeps re-emitting: 1200 ticks = 60 s -- time to close chat, look around, and walk. */
    private static final int MARKER_LIFETIME_TICKS = 1200;
    /** Re-emit cadence. 10 ticks = twice a second -- a steady glow without a particle storm. */
    private static final int MARKER_REEMIT_TICKS = 10;

    /**
     * S32 marker heartbeat -- called from {@code GlobeMod}'s END_SERVER_TICK path (beside the collapse
     * scheduler). Re-emits every live green marker on the {@link #MARKER_REEMIT_TICKS} cadence and retires it
     * after {@link #MARKER_LIFETIME_TICKS}. No-op (one synchronized isEmpty) when no scan has run.
     */
    public static void tickGreenMarkers(ServerLevel world, long gameTime) {
        synchronized (GREEN_MARKS) {
            if (GREEN_MARKS.isEmpty()) {
                return;
            }
            boolean emit = gameTime % MARKER_REEMIT_TICKS == 0L;
            var it = GREEN_MARKS.iterator();
            while (it.hasNext()) {
                int[] m = it.next();
                m[4] += 1;
                if (m[4] > MARKER_LIFETIME_TICKS) {
                    it.remove();
                    continue;
                }
                if (emit) {
                    emitMark(world, m[0], m[1], m[2], m[3], m[5]);
                }
            }
        }
    }

    /** Clear all live markers (each fresh markGlacial run starts clean). */
    private static void clearGreenMarkers() {
        synchronized (GREEN_MARKS) {
            GREEN_MARKS.clear();
        }
    }

    private static void emitMark(ServerLevel world, int x, int lo, int hi, int z, int kind) {
        double cx = x + 0.5;
        double cz = z + 0.5;
        // S34 (Peetsa 2026-07-21, TEST 124: "I'm not seeing any green sparkles" while the scan CHAT listed 66
        // roofs): the plain sendParticles overload only renders to players within ~32 blocks, but an r=8 scan
        // finds traps up to ~136 blocks out — so the rare green pillars were real and invisible. The
        // (overrideLimiter=true, alwaysVisible=true) overload broadcasts at long range like vanilla's
        // force-rendered particles; markers now carry to the whole scan radius.
        if (kind == MARK_KIND_SLOT) {
            // Open crevasse: a short BLUE soul-flame mark at the rim. Sparse and low -- context, not a target.
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, cx, lo + 0.5, cz, 2, 0.2, 0.2, 0.2, 0.0);
            return;
        }
        // Trap roof: a solid GREEN pillar every block from the roof up, so it reads as one column from afar.
        for (int y = lo; y <= hi; y++) {
            world.sendParticles(ParticleTypes.HAPPY_VILLAGER, true, true, cx, y + 0.5, cz, 4, 0.15, 0.15, 0.15, 0.0);
        }
    }
}
