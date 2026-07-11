package com.example.globe;

import com.example.globe.adapter.geo.GeoAuthorityProvider;
import com.example.globe.adapter.geo.GeoSummaryProvider;
import com.example.globe.core.GeoSurveyNarrator;
import com.example.globe.core.climate.ClimateAuthority;
import com.example.globe.core.climate.ClimateSummary;
import com.example.globe.core.geo.GeoAuthority;
import com.example.globe.core.geo.GeoSummary;
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
            return; // the full dev.LatitudeDevCommand already owns /latdev in dev
        }
        if (!devCommandsEnabled()) {
            return;
        }
        register(dispatcher);
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
                .then(Commands.literal("survey").executes(LatitudeDevCommands::survey)));
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
                        + " | tphemi <ns|ew|zero> [n|s|e|w] | tppole <n|s> [deg]"), false);
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
                    g.isOceanIntent(),
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
                src.sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] survey failed: " + e.getMessage()));
            return 0;
        }
    }

    private static String biomeId(ServerLevel world, BlockPos pos) {
        return world.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
    }
}
