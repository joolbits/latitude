package com.example.globe;

import com.example.globe.adapter.geo.GeoAuthorityProvider;
import com.example.globe.adapter.geo.GeoSummaryProvider;
import com.example.globe.core.CrevasseLocator;
import com.example.globe.core.CaveTrapEntranceScan;
import com.example.globe.core.GeoSurveyNarrator;
import com.example.globe.core.GlacialBlend;
import com.example.globe.core.GlacialMarkScan;
import com.example.globe.core.HiddenChamberPlan;
import com.example.globe.core.HiddenChamberScan;
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
import com.example.globe.world.IcicleBlocks;
import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.CaveTrapBlocks;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
                .then(Commands.literal("markCaveTraps")
                        .executes(ctx -> markCaveTraps(ctx, DEFAULT_CAVE_TRAP_RADIUS_CHUNKS))
                        .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(1, MAX_CAVE_TRAP_RADIUS_CHUNKS))
                                .executes(ctx -> markCaveTraps(
                                        ctx, IntegerArgumentType.getInteger(ctx, "radiusChunks")))))
                .then(Commands.literal("markChambers")
                        .executes(ctx -> markChambers(ctx, DEFAULT_CHAMBER_RADIUS_CHUNKS))
                        .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(1, MAX_CHAMBER_RADIUS_CHUNKS))
                                .executes(ctx -> markChambers(
                                        ctx, IntegerArgumentType.getInteger(ctx, "radiusChunks")))))
                .then(Commands.literal("locateChamber")
                        .executes(ctx -> locateChamber(ctx, DEFAULT_LOCATE_CHAMBER_RADIUS_CHUNKS))
                        // The literal is matched before the integer argument, so "cancel" is unambiguous.
                        .then(Commands.literal("cancel").executes(LatitudeDevCommands::locateChamberCancel))
                        .then(Commands.argument("radiusChunks",
                                        IntegerArgumentType.integer(1, MAX_LOCATE_CHAMBER_RADIUS_CHUNKS))
                                .executes(ctx -> locateChamber(
                                        ctx, IntegerArgumentType.getInteger(ctx, "radiusChunks")))))
                .then(Commands.literal("voidCensus")
                        .executes(ctx -> voidCensus(ctx, DEFAULT_VOID_RADIUS_CHUNKS))
                        .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(1, MAX_VOID_RADIUS_CHUNKS))
                                .executes(ctx -> voidCensus(ctx, IntegerArgumentType.getInteger(ctx, "radiusChunks")))
                                // Coordinate form, mirroring markGlacial's S31 pattern: scan around explicit
                                // block coords instead of the caller -- this is how the before/after taming
                                // proof actually runs, via RCON on a dedicated server with no player present.
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> voidCensusAt(ctx,
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
                        + " | markGlacial [radiusChunks [x z]] | markCaveTraps [radiusChunks]"
                        + " | markChambers [radiusChunks] | locateChamber [radiusChunks|cancel]"
                        + " | voidCensus [radiusChunks [x z]] | tpxz <x> <z>"), false);
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

    /** A full-height scan is more expensive than the surface marker; 9x9 loaded chunks stays responsive. */
    private static final int DEFAULT_CAVE_TRAP_RADIUS_CHUNKS = 2;
    private static final int MAX_CAVE_TRAP_RADIUS_CHUNKS = 4;
    private static final int CAVE_TRAP_CHAT_CAP = 10;
    private static final int CAVE_TRAP_MARKER_CAP = 200;

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

    // --- /latdev markCaveTraps -- direct locator for the inner-cave feature. Unlike locateCrevasse and the
    // surface markGlacial evidence, this scans only the unique worldgen-only block signature already present in
    // loaded FULL chunks. No generator seed/planner is consulted and it never asks the chunk source to create
    // terrain. --------------------------------------------------------------------------------------------------

    private static int markCaveTraps(CommandContext<CommandSourceStack> ctx, int radiusChunks) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            return markCaveTrapsCore(src, src.getLevel(), player, radiusChunks);
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] markCaveTraps failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int markCaveTrapsCore(
            CommandSourceStack src, ServerLevel world, ServerPlayer player, int radiusChunks) {
        if (CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW == null) {
            src.sendFailure(Component.literal(
                    "[latdev] the globe:cave_trap_powder_snow signature is not registered in this session."));
            return 0;
        }
        int r = Mth.clamp(radiusChunks, 1, MAX_CAVE_TRAP_RADIUS_CHUNKS);
        clearGreenMarkers(); // a new direct scan must never leave a prior entrance highlighted
        CollapseSweep sweep = sweepLoadedCollapseSignature(world, player.getX(), player.getZ(), r);
        List<CaveTrapEntranceScan.Cell> signatures = sweep.signatures();
        Set<LoadedChunk> loadedChunks = sweep.loadedChunks();
        int loaded = sweep.loadedChunkCount();
        int skipped = sweep.skippedChunkCount();

        List<CaveTrapEntranceScan.Patch> patches = CaveTrapEntranceScan.groupPatches(signatures);
        Map<HiddenChamberScan.Position, List<HiddenChamberScan.Position>> mouthByCell =
                chamberMouthsByCell(signatures);
        List<CaveTrapHit> hits = new ArrayList<>();
        int partialPatches = 0;
        int noSafeWaypoint = 0;
        for (CaveTrapEntranceScan.Patch patch : patches) {
            if (!patchHasLoadedBorder(patch, loadedChunks)) {
                partialPatches++;
                continue;
            }
            BlockPos waypoint = safeCaveTrapWaypoint(world, patch, loadedChunks);
            if (waypoint == null) {
                noSafeWaypoint++;
                continue;
            }
            double dx = patch.x() + 0.5 - player.getX();
            double dy = patch.y() + 0.5 - player.getY();
            double dz = patch.z() + 0.5 - player.getZ();
            hits.add(new CaveTrapHit(patch, waypoint, Math.sqrt(dx * dx + dy * dy + dz * dz)));
        }
        hits.sort(Comparator.comparingDouble(CaveTrapHit::distanceBlocks)
                .thenComparingInt(hit -> hit.patch().x())
                .thenComparingInt(hit -> hit.patch().y())
                .thenComparingInt(hit -> hit.patch().z()));

        int marked = 0;
        int lines = 0;
        String commandRoot = commandRootForEnvironment();
        for (CaveTrapHit hit : hits) {
            if (marked < CAVE_TRAP_MARKER_CAP) {
                greenBeacon(world, hit.patch().x(), hit.patch().y(),
                        hit.patch().y() + TRAP_PILLAR_HEIGHT, hit.patch().z());
                marked++;
            }
            if (lines < CAVE_TRAP_CHAT_CAP) {
                String tpCommand = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f",
                        hit.waypoint().getX() + 0.5, hit.waypoint().getY(), hit.waypoint().getZ() + 0.5);
                MutableComponent line = Component.literal(String.format(Locale.ROOT,
                        "[latdev]   %s: %d blocks away | entrance x=%d y=%d z=%d"
                                + " | %d signature blocks | safe arrival x=%d y=%d z=%d ",
                        caveTrapPatchLabel(world, hit.patch(), mouthByCell),
                        Math.round(hit.distanceBlocks()), hit.patch().x(), hit.patch().y(), hit.patch().z(),
                        hit.patch().blockCount(), hit.waypoint().getX(), hit.waypoint().getY(), hit.waypoint().getZ()))
                        .append(Component.literal("[teleport]").withStyle(style -> style
                                .withClickEvent(new ClickEvent.RunCommand(tpCommand))
                                .withUnderlined(true)));
                src.sendSuccess(() -> line, false);
                lines++;
            }
        }

        final int fLoaded = loaded;
        final int fSkipped = skipped;
        final int fPatchCount = patches.size();
        final int fPartial = partialPatches;
        final int fNoSafeWaypoint = noSafeWaypoint;
        final int fSignatures = signatures.size();
        final int fHits = hits.size();
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[latdev] markCaveTraps (r=%d chunks): %d exact signature blocks, %d entrance patches, "
                        + "%d teleportable traps | scanned %d FULL loaded chunks, skipped %d unloaded chunks"
                        + " | partial=%d, no-safe-arrival=%d",
                r, fSignatures, fPatchCount, fHits, fLoaded, fSkipped, fPartial, fNoSafeWaypoint)), false);
        if (fHits == 0) {
            if (fLoaded == 0) {
                src.sendFailure(Component.literal(
                        "[latdev] no FULL chunks were loaded to scan — load this cave area, then run "
                                + commandRoot + " markCaveTraps again."));
            } else if (fPatchCount == 0) {
                src.sendSuccess(() -> Component.literal(
                        "[latdev] no cave-trap signature was found in the loaded chunks; unloaded chunks were skipped, not searched."), false);
            } else {
                src.sendSuccess(() -> Component.literal(
                        "[latdev] exact trap signatures were found, but their patch or a safe adjacent cave arrival was incomplete; load the nearby chunks and rescan."), false);
            }
        }
        if (marked >= CAVE_TRAP_MARKER_CAP || lines >= CAVE_TRAP_CHAT_CAP) {
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] display capped at %d green markers and %d chat lines; the summary count is exact.",
                    CAVE_TRAP_MARKER_CAP, CAVE_TRAP_CHAT_CAP)), false);
        }
        return 1;
    }

    /** The clicked command must name the root that is actually registered in this runtime. */
    private static String commandRootForEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment() ? "/latdev2" : "/latdev";
    }

    /** A patch touching an unloaded neighbouring chunk could be only part of one wider entrance carpet. */
    private static boolean patchHasLoadedBorder(CaveTrapEntranceScan.Patch patch, Set<LoadedChunk> loadedChunks) {
        for (CaveTrapEntranceScan.Cell cell : patch.cells()) {
            int chunkX = Math.floorDiv(cell.x(), 16);
            int chunkZ = Math.floorDiv(cell.z(), 16);
            if ((Math.floorMod(cell.x(), 16) == 0 && !loadedChunks.contains(new LoadedChunk(chunkX - 1, chunkZ)))
                    || (Math.floorMod(cell.x(), 16) == 15 && !loadedChunks.contains(new LoadedChunk(chunkX + 1, chunkZ)))
                    || (Math.floorMod(cell.z(), 16) == 0 && !loadedChunks.contains(new LoadedChunk(chunkX, chunkZ - 1)))
                    || (Math.floorMod(cell.z(), 16) == 15 && !loadedChunks.contains(new LoadedChunk(chunkX, chunkZ + 1)))) {
                return false;
            }
        }
        return true;
    }

    /** Finds a two-block-high, non-falling, dry standing space directly beside the true underground entrance. */
    private static BlockPos safeCaveTrapWaypoint(
            ServerLevel world, CaveTrapEntranceScan.Patch patch, Set<LoadedChunk> loadedChunks) {
        for (CaveTrapEntranceScan.Cell cell : patch.cells()) {
            BlockPos arrival = safeArrivalBeside(world, cell.x(), cell.y(), cell.z(), loadedChunks);
            if (arrival != null) {
                return arrival;
            }
        }
        return null;
    }

    /**
     * The one arrival test, shared by every locator that offers a clickable teleport: of the four cardinal
     * columns beside a hole, take the first whose support block is loaded, full, dry, and not gravity-bound,
     * and which carries two blocks of air above it. Arriving ON the hole is arriving IN the trap.
     */
    private static BlockPos safeArrivalBeside(
            ServerLevel world, int cellX, int cellY, int cellZ, Set<LoadedChunk> loadedChunks) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            int x = cellX + direction[0];
            int z = cellZ + direction[1];
            if (!loadedChunks.contains(new LoadedChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)))) {
                continue;
            }
            BlockPos support = new BlockPos(x, cellY, z);
            BlockState supportState = world.getBlockState(support);
            if (supportState.is(CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW)
                    || !supportState.getFluidState().isEmpty()
                    || supportState.getBlock() instanceof FallingBlock
                    || !supportState.isCollisionShapeFullBlock(world, support)) {
                continue;
            }
            BlockPos feet = support.above();
            if (world.getBlockState(feet).isAir() && world.getBlockState(feet.above()).isAir()) {
                return feet;
            }
        }
        return null;
    }

    private record LoadedChunk(int x, int z) {
    }

    private record CaveTrapHit(CaveTrapEntranceScan.Patch patch, BlockPos waypoint, double distanceBlocks) {
    }

    /** One loaded-chunk signature sweep: the exact collapse cells found, and which chunks were readable. */
    private record CollapseSweep(List<CaveTrapEntranceScan.Cell> signatures,
                                 Set<LoadedChunk> loadedChunks,
                                 int loadedChunkCount,
                                 int skippedChunkCount) {
    }

    /**
     * Sweep the {@code (2r+1)}-square of chunks around a position for the worldgen-only collapse signature,
     * reading ONLY chunks that are already loaded. This is the shared floor under {@code markCaveTraps} and
     * {@code markChambers}: the section-palette prefilter rejects almost every cave section without touching
     * its 4096 cells, and an unloaded chunk is counted as skipped and never generated.
     */
    private static CollapseSweep sweepLoadedCollapseSignature(
            ServerLevel world, double centerX, double centerZ, int radiusChunks) {
        int centerChunkX = Math.floorDiv(Mth.floor(centerX), 16);
        int centerChunkZ = Math.floorDiv(Mth.floor(centerZ), 16);
        int chunksPerSide = 2 * radiusChunks + 1;
        int minChunkX = centerChunkX - radiusChunks;
        int minChunkZ = centerChunkZ - radiusChunks;
        var chunkSource = world.getChunkSource();
        List<CaveTrapEntranceScan.Cell> signatures = new ArrayList<>();
        Set<LoadedChunk> loadedChunks = new HashSet<>();
        int loaded = 0;
        int skipped = 0;
        for (int chunkX = minChunkX; chunkX < minChunkX + chunksPerSide; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ < minChunkZ + chunksPerSide; chunkZ++) {
                LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    skipped++;
                    continue;
                }
                loaded++;
                loadedChunks.add(new LoadedChunk(chunkX, chunkZ));
                collectCollapseCells(chunk, chunkX, chunkZ, signatures);
            }
        }
        return new CollapseSweep(signatures, loadedChunks, loaded, skipped);
    }

    /**
     * Append every {@code globe:cave_trap_powder_snow} cell in one chunk, in x/y/z order.
     *
     * <p>{@code maybeHas} asks the section's compact palette, not its 4096 cells. Almost every cave section is
     * rejected here, so a maximum-radius command never performs a blind full-world walk.
     */
    private static void collectCollapseCells(
            ChunkAccess chunk, int chunkX, int chunkZ, List<CaveTrapEntranceScan.Cell> out) {
        LevelChunkSection[] sections = chunk.getSections();
        boolean[] candidateSections = new boolean[sections.length];
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            candidateSections[sectionIndex] = sections[sectionIndex]
                    .maybeHas(state -> state.is(CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW));
        }
        for (int sectionIndex : CaveTrapEntranceScan.candidateSectionIndices(candidateSections)) {
            LevelChunkSection section = sections[sectionIndex];
            int sectionMinY = CaveTrapEntranceScan.sectionStartY(chunk.getMinY(), sectionIndex);
            for (int localX = 0; localX < 16; localX++) {
                for (int localY = 0; localY < 16; localY++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        if (section.getBlockState(localX, localY, localZ)
                                .is(CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW)) {
                            out.add(new CaveTrapEntranceScan.Cell(
                                    (chunkX << 4) + localX, sectionMinY + localY, (chunkZ << 4) + localZ));
                        }
                    }
                }
            }
        }
    }

    // --- /latdev markChambers + /latdev locateChamber -- physical locators for the globe:hidden_glacial_chamber
    // encounter. Both consume ONE reconstruction, HiddenChamberScan, over REAL blocks: no planner, no world
    // seed, no debug counter, so a chamber is reported complete only when its own blocks say so. markChambers
    // reads what is already loaded and answers immediately; locateChamber is the patient wide search and is
    // allowed to generate terrain, one bounded slice per tick. ---------------------------------------------------

    /** A chamber reconstruction reads a wide box per mouth, so the immediate scan stays at 5x5 loaded chunks. */
    private static final int DEFAULT_CHAMBER_RADIUS_CHUNKS = 2;
    private static final int MAX_CHAMBER_RADIUS_CHUNKS = 4;
    private static final int CHAMBER_CHAT_CAP = 10;
    private static final int CHAMBER_MARKER_CAP = 200;

    /** locateChamber's radius is a SEARCH distance, not a load promise -- the sweep may generate as it goes. */
    private static final int DEFAULT_LOCATE_CHAMBER_RADIUS_CHUNKS = 32;
    private static final int MAX_LOCATE_CHAMBER_RADIUS_CHUNKS = 64;
    /** Chunks per server tick. Two keeps a live server smooth; the wall-clock guard below is the real brake. */
    private static final int LOCATE_CHAMBER_CHUNKS_PER_TICK = 2;
    /** Wall-clock guard per tick (ns). Under a 50 ms tick this leaves the server most of its budget. */
    private static final long LOCATE_CHAMBER_TICK_BUDGET_NANOS = 15_000_000L;
    /** How often the search says it is still alive, in chunks visited. */
    private static final int LOCATE_CHAMBER_PROGRESS_INTERVAL = 64;

    /**
     * Immediate chamber readout over already-loaded chunks: every collapse mouth in range is reconstructed and
     * each complete chamber gets a green entrance pillar, a white exit column, and one chat line carrying its
     * three landmarks and a clickable teleport to a safe stance beside the mouth. Mouths that do not reconstruct
     * are counted with the stage they stopped at, so "nothing here" and "something here is broken" never look
     * alike. Unloaded chunks are skipped, never generated -- use {@code locateChamber} to search wider.
     */
    private static int markChambers(CommandContext<CommandSourceStack> ctx, int radiusChunks) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            return markChambersCore(src, src.getLevel(), player, radiusChunks);
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] markChambers failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int markChambersCore(
            CommandSourceStack src, ServerLevel world, ServerPlayer player, int radiusChunks) {
        if (CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW == null) {
            src.sendFailure(Component.literal(
                    "[latdev] the globe:cave_trap_powder_snow signature is not registered in this session."));
            return 0;
        }
        int r = Mth.clamp(radiusChunks, 1, MAX_CHAMBER_RADIUS_CHUNKS);
        clearGreenMarkers(); // a new scan must never leave a prior entrance or exit highlighted
        CollapseSweep sweep = sweepLoadedCollapseSignature(world, player.getX(), player.getZ(), r);
        List<HiddenChamberScan.Position> collapse = new ArrayList<>();
        for (CaveTrapEntranceScan.Cell cell : sweep.signatures()) {
            collapse.add(new HiddenChamberScan.Position(cell.x(), cell.y(), cell.z()));
        }
        HiddenChamberScan.ChamberScanReport report = HiddenChamberScan.classifyPatches(
                new ChamberCellReader(world, false),
                HiddenChamberScan.groupCollapsePatches(collapse),
                collapse.size());

        List<ChamberFind> finds = new ArrayList<>();
        for (HiddenChamberScan.Completed chamber : report.completed()) {
            finds.add(chamberFind(world, chamber, sweep.loadedChunks(),
                    player.getX(), player.getY(), player.getZ()));
        }
        finds.sort(Comparator.comparingDouble(ChamberFind::distanceBlocks)
                .thenComparing(find -> find.chamber().mouthCentroid()));

        int marked = 0;
        int lines = 0;
        for (ChamberFind find : finds) {
            if (marked < CHAMBER_MARKER_CAP) {
                markChamber(world, find.chamber());
                marked++;
            }
            if (lines < CHAMBER_CHAT_CAP) {
                MutableComponent line = chamberLine(find);
                src.sendSuccess(() -> line, false);
                lines++;
            }
        }

        final int fLoaded = sweep.loadedChunkCount();
        final int fSkipped = sweep.skippedChunkCount();
        final String fReasons = partialReasonSummary(report.partial());
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[latdev] markChambers (r=%d chunks): %d collapse blocks, %d mouths, %d complete chambers"
                        + " | partial=%d%s, legacy drop traps=%d"
                        + " | scanned %d FULL loaded chunks, skipped %d unloaded chunks",
                r, report.collapseCells(), report.patches(), report.completedCount(),
                report.partialCount(), fReasons, report.legacyCount(), fLoaded, fSkipped)), false);
        if (report.completedCount() == 0) {
            String commandRoot = commandRootForEnvironment();
            if (fLoaded == 0) {
                src.sendFailure(Component.literal(
                        "[latdev] no FULL chunks were loaded to scan — load this cave area, then run "
                                + commandRoot + " markChambers again."));
            } else if (report.patches() == 0) {
                src.sendSuccess(() -> Component.literal(
                        "[latdev] no collapse mouth was found in the loaded chunks; unloaded chunks were"
                                + " skipped, not searched. Use " + commandRoot
                                + " locateChamber to search outward from here."), false);
            } else {
                src.sendSuccess(() -> Component.literal(
                        "[latdev] collapse mouths were found, but none reconstructed into a complete chamber"
                                + " (see the partial reasons above); load the neighbouring chunks and rescan."), false);
            }
        }
        if (marked >= CHAMBER_MARKER_CAP || lines >= CHAMBER_CHAT_CAP) {
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] display capped at %d marker pairs and %d chat lines; the summary count is exact.",
                    CHAMBER_MARKER_CAP, CHAMBER_CHAT_CAP)), false);
        }
        return 1;
    }

    /**
     * ONE-CYCLE COMPATIBILITY. {@code markCaveTraps} predates the chamber encounter and is still the name a
     * tester in flight has in muscle memory, but both features now lay the same collapse block. A mouth of
     * {@link HiddenChamberPlan#MOUTH_MIN_CELLS}..{@link HiddenChamberPlan#MOUTH_MAX_CELLS} cells is a CHAMBER
     * and can never be the old six-cell drop-trap carpet, so it is run through the same physical
     * reconstruction {@code markChambers} uses and labelled for what it is. A mouth of
     * {@link HiddenChamberScan#LEGACY_MIN_CELLS} cells or more is the legacy inner-cave drop trap and keeps its
     * label and behaviour exactly as before -- no reconstruction is attempted and nothing on that path changes.
     *
     * <p>The size is read off the CHAMBER grouping, not the legacy patch's own {@code blockCount}: the legacy
     * grouping is cardinal and single-Y, so it shatters the authored diagonal mouth masks into loose single
     * cells and would call every one of them a one-block trap. One chamber can therefore contribute more than
     * one line here, each labelled correctly -- that redundancy is the price of leaving the legacy grouping
     * untouched, and it is why {@code markChambers} (one line, with landing, exit and both markers) is the
     * real readout. This branch is expected to retire with the next cycle of the locator tools.
     */
    private static String caveTrapPatchLabel(
            ServerLevel world, CaveTrapEntranceScan.Patch patch,
            Map<HiddenChamberScan.Position, List<HiddenChamberScan.Position>> mouthByCell) {
        CaveTrapEntranceScan.Cell head = patch.cells().get(0);
        List<HiddenChamberScan.Position> mouth =
                mouthByCell.get(new HiddenChamberScan.Position(head.x(), head.y(), head.z()));
        if (mouth == null || mouth.size() < HiddenChamberPlan.MOUTH_MIN_CELLS
                || mouth.size() > HiddenChamberPlan.MOUTH_MAX_CELLS) {
            return "GREEN CAVE TRAP";
        }
        HiddenChamberScan.PatchOutcome outcome =
                HiddenChamberScan.classifyPatch(new ChamberCellReader(world, false), mouth);
        if (outcome instanceof HiddenChamberScan.Completed chamber) {
            return "GREEN CAVE CHAMBER (" + themeWords(chamber.themeGuess()) + ")";
        }
        if (outcome instanceof HiddenChamberScan.Partial partial) {
            return "GREEN CAVE CHAMBER MOUTH (incomplete: " + partialWords(partial.reason()) + ")";
        }
        return "GREEN CAVE TRAP";
    }

    /**
     * Index the CHAMBER grouping law by cell, so a legacy trap patch can ask "is this block part of a chamber
     * mouth, and how big is that mouth?". The two groupings differ on purpose: a drop trap's carpet is a solid
     * cardinal block at one Y, while an authored chamber mouth may be a pure diagonal laid across a one-block
     * floor spread, which only the eight-connected chamber law holds together.
     */
    private static Map<HiddenChamberScan.Position, List<HiddenChamberScan.Position>> chamberMouthsByCell(
            List<CaveTrapEntranceScan.Cell> signatures) {
        List<HiddenChamberScan.Position> collapse = new ArrayList<>();
        for (CaveTrapEntranceScan.Cell cell : signatures) {
            collapse.add(new HiddenChamberScan.Position(cell.x(), cell.y(), cell.z()));
        }
        Map<HiddenChamberScan.Position, List<HiddenChamberScan.Position>> byCell = new HashMap<>();
        for (List<HiddenChamberScan.Position> mouth : HiddenChamberScan.groupCollapsePatches(collapse)) {
            for (HiddenChamberScan.Position cell : mouth) {
                byCell.put(cell, mouth);
            }
        }
        return byCell;
    }

    /** One complete chamber, with the distance to the caller and the safe stance to teleport to. */
    private record ChamberFind(HiddenChamberScan.Completed chamber, BlockPos arrival, double distanceBlocks) {
    }

    private static ChamberFind chamberFind(ServerLevel world, HiddenChamberScan.Completed chamber,
                                           Set<LoadedChunk> loadedChunks,
                                           double fromX, double fromY, double fromZ) {
        HiddenChamberScan.Position entrance = chamber.mouthCentroid();
        double dx = entrance.x() + 0.5 - fromX;
        double dy = entrance.y() + 0.5 - fromY;
        double dz = entrance.z() + 0.5 - fromZ;
        return new ChamberFind(chamber, safeChamberArrival(world, chamber, loadedChunks),
                Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /** The mouth is a hole; the arrival is beside it. Reuses the trap locator's exact standing-space test. */
    private static BlockPos safeChamberArrival(
            ServerLevel world, HiddenChamberScan.Completed chamber, Set<LoadedChunk> loadedChunks) {
        for (HiddenChamberScan.Position cell : chamber.mouthCells()) {
            BlockPos arrival = safeArrivalBeside(world, cell.x(), cell.y(), cell.z(), loadedChunks);
            if (arrival != null) {
                return arrival;
            }
        }
        return null;
    }

    /** GREEN pillar on the entrance you fall through, WHITE column on the second opening you climb out of. */
    private static void markChamber(ServerLevel world, HiddenChamberScan.Completed chamber) {
        HiddenChamberScan.Position entrance = chamber.mouthCentroid();
        HiddenChamberScan.Position exit = chamber.exitOpening();
        greenBeacon(world, entrance.x(), entrance.y(), entrance.y() + TRAP_PILLAR_HEIGHT, entrance.z());
        exitBeacon(world, exit.x(), exit.y(), exit.y() + TRAP_PILLAR_HEIGHT, exit.z());
    }

    /** The chat line every chamber report shares: what it is, its three landmarks, and a teleport to it. */
    private static MutableComponent chamberLine(ChamberFind find) {
        HiddenChamberScan.Completed chamber = find.chamber();
        HiddenChamberScan.Position entrance = chamber.mouthCentroid();
        HiddenChamberScan.Position landing = chamber.landing();
        HiddenChamberScan.Position exit = chamber.exitOpening();
        MutableComponent line = Component.literal(String.format(Locale.ROOT,
                "[latdev]   CHAMBER (%s): %d blocks away | entrance x=%d y=%d z=%d | landing x=%d y=%d z=%d"
                        + " | exit x=%d y=%d z=%d | %d-block fall, %d-block room, %d bends ",
                themeWords(chamber.themeGuess()), Math.round(find.distanceBlocks()),
                entrance.x(), entrance.y(), entrance.z(),
                landing.x(), landing.y(), landing.z(),
                exit.x(), exit.y(), exit.z(),
                chamber.drop(), chamber.voidVolume(), chamber.bends()));
        if (find.arrival() == null) {
            return line.append(Component.literal("| no safe arrival beside the entrance"));
        }
        String tpCommand = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f",
                find.arrival().getX() + 0.5, find.arrival().getY(), find.arrival().getZ() + 0.5);
        return line.append(Component.literal(String.format(Locale.ROOT,
                        "| safe arrival x=%d y=%d z=%d ",
                        find.arrival().getX(), find.arrival().getY(), find.arrival().getZ())))
                .append(Component.literal("[teleport]").withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand(tpCommand))
                        .withUnderlined(true)));
    }

    /** Themes and stages are reported in words, not enum spelling -- these lines are read in a chat box. */
    private static String themeWords(HiddenChamberScan.Theme theme) {
        return theme.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String partialWords(HiddenChamberScan.PartialReason reason) {
        return switch (reason) {
            case MOUTH_SIZE -> "a single collapse block, too small to judge";
            case NO_SHAFT -> "no clear fall under the mouth";
            case NO_CUSHION -> "no deep powder cushion at the bottom";
            case NO_SHELF -> "no firm shelf to climb out onto";
            case NO_CHAMBER_VOID -> "no carved room under the fall";
            case NO_EXIT -> "no second way out";
            case BOUNDARY_UNREADABLE -> "chunks around it are not loaded";
        };
    }

    /** " (no second way out=2, no firm shelf...=1)", or "" when nothing stopped short. */
    private static String partialReasonSummary(List<HiddenChamberScan.Partial> partials) {
        Map<HiddenChamberScan.PartialReason, Integer> tally =
                new EnumMap<>(HiddenChamberScan.PartialReason.class);
        for (HiddenChamberScan.Partial partial : partials) {
            tally.merge(partial.reason(), 1, Integer::sum);
        }
        return partialReasonSummary(tally);
    }

    private static String partialReasonSummary(Map<HiddenChamberScan.PartialReason, Integer> tally) {
        if (tally.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(" (");
        boolean first = true;
        for (Map.Entry<HiddenChamberScan.PartialReason, Integer> entry : tally.entrySet()) {
            if (!first) {
                out.append(", ");
            }
            out.append(partialWords(entry.getKey())).append('=').append(entry.getValue());
            first = false;
        }
        return out.append(')').toString();
    }

    /**
     * The block-to-{@link HiddenChamberScan.ScanCell} mapping: the ONE place the pure scanner's vocabulary
     * meets Minecraft. The scanner never sees a {@code BlockState} and this class never sees a chamber law, so
     * a palette change in {@code HiddenGlacialChamberFeature} is answered here and nowhere else.
     *
     * <p>Named chamber materials are tested first, because several of them (powder snow above all) would
     * otherwise be mistaken for empty space by a collision test. What is left resolves by physics: air is air,
     * a fluid is water or some other fluid, anything a player can walk through (a snow layer, a frost mote) is
     * clear space, and everything else is solid. A block this reader cannot see -- an unloaded chunk, a
     * coordinate outside the world -- answers {@link HiddenChamberScan.ScanCell#UNREADABLE}, never a guess,
     * which is what lets the scanner fail closed instead of inventing geometry.
     *
     * <p>{@code mayGenerate} separates the two callers. The immediate scans pass {@code false} and read only
     * what a player has already loaded. The wide search passes {@code true}: it is already generating the
     * terrain it sweeps, so refusing to read a chamber's own far wall would report every find as unreadable.
     */
    private static final class ChamberCellReader implements HiddenChamberScan.CellReader {
        private final ServerLevel world;
        private final boolean mayGenerate;
        private final int minY;
        private final int maxY;
        private final Map<Long, ChunkAccess> chunks = new HashMap<>();
        private final Set<Long> unavailable = new HashSet<>();
        /**
         * Asked before each NEW chunk this reader would force-generate. The immediate scans pass none; the
         * wide search passes its job's per-tick allowance, because the chamber box is wide enough to pull a
         * whole further ring of worldgen into one tick if nothing stops it.
         */
        private final java.util.function.BooleanSupplier stopGenerating;
        private boolean yieldedForBudget;

        private ChamberCellReader(ServerLevel world, boolean mayGenerate) {
            this(world, mayGenerate, null);
        }

        private ChamberCellReader(ServerLevel world, boolean mayGenerate,
                                  java.util.function.BooleanSupplier stopGenerating) {
            this.world = world;
            this.mayGenerate = mayGenerate;
            this.stopGenerating = stopGenerating;
            this.minY = world.getMinY();
            this.maxY = world.getMinY() + world.getHeight() - 1;
        }

        /**
         * True once this reader refused to generate a chunk because the tick's allowance was spent. Whatever
         * it answered after that is built on an UNREADABLE it invented, so the caller must throw the
         * reconstruction away and re-enter rather than record it.
         */
        private boolean yieldedForBudget() {
            return yieldedForBudget;
        }

        @Override
        public HiddenChamberScan.ScanCell cell(int worldX, int y, int worldZ) {
            if (y < minY || y > maxY) {
                return HiddenChamberScan.ScanCell.UNREADABLE;
            }
            ChunkAccess chunk = chunkAt(worldX >> 4, worldZ >> 4);
            if (chunk == null) {
                return HiddenChamberScan.ScanCell.UNREADABLE;
            }
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            return classify(chunk.getBlockState(pos), pos);
        }

        private ChunkAccess chunkAt(int chunkX, int chunkZ) {
            long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
            ChunkAccess cached = chunks.get(key);
            if (cached != null) {
                return cached;
            }
            if (unavailable.contains(key)) {
                return null;
            }
            ChunkAccess chunk = world.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null && mayGenerate) {
                if (stopGenerating != null && stopGenerating.getAsBoolean()) {
                    // The tick's allowance is spent. Answer "cannot read" rather than run another full
                    // worldgen pipeline; the caller sees yieldedForBudget() and re-enters next tick.
                    yieldedForBudget = true;
                    return null;
                }
                chunk = world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            }
            if (chunk == null) {
                unavailable.add(key);
                return null;
            }
            chunks.put(key, chunk);
            return chunk;
        }

        private HiddenChamberScan.ScanCell classify(BlockState state, BlockPos pos) {
            if (CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW != null
                    && state.is(CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW)) {
                return HiddenChamberScan.ScanCell.COLLAPSE_POWDER;
            }
            if (state.is(Blocks.POWDER_SNOW)) {
                return HiddenChamberScan.ScanCell.POWDER_SNOW;
            }
            if (state.is(Blocks.SNOW_BLOCK)) {
                return HiddenChamberScan.ScanCell.SNOW_FIRM;
            }
            if (state.is(Blocks.PACKED_ICE)) {
                return HiddenChamberScan.ScanCell.ICE_PACKED;
            }
            if (state.is(Blocks.BLUE_ICE)) {
                return HiddenChamberScan.ScanCell.ICE_BLUE;
            }
            if (state.is(Blocks.ICE)) {
                return HiddenChamberScan.ScanCell.ICE_PLAIN;
            }
            if (IcicleBlocks.ICICLE != null && state.is(IcicleBlocks.ICICLE)) {
                return HiddenChamberScan.ScanCell.ICICLE;
            }
            if (state.is(Blocks.BONE_BLOCK)) {
                return HiddenChamberScan.ScanCell.BONE;
            }
            if (state.is(Blocks.LANTERN)) {
                return HiddenChamberScan.ScanCell.LANTERN;
            }
            if (state.is(Blocks.CHEST)) {
                return HiddenChamberScan.ScanCell.CHEST;
            }
            if (state.is(Blocks.SPRUCE_FENCE)) {
                return HiddenChamberScan.ScanCell.WOOD_DEBRIS;
            }
            if (state.isAir()) {
                return HiddenChamberScan.ScanCell.AIR;
            }
            if (!state.getFluidState().isEmpty()) {
                return state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)
                        ? HiddenChamberScan.ScanCell.WATER
                        : HiddenChamberScan.ScanCell.OTHER_FLUID;
            }
            if (state.getCollisionShape(world, pos).isEmpty()) {
                return HiddenChamberScan.ScanCell.AIR; // walk-through dressing is clear space to a player
            }
            return HiddenChamberScan.ScanCell.OTHER_SOLID;
        }
    }

    // --- /latdev locateChamber -- the wide search. Self-contained on purpose: the internal dev package
    // (ChunkPregenerator and friends) is STRIPPED from shipped jars, so a shippable command may copy its
    // tick-budgeted job shape but must never import it. --------------------------------------------------------

    /** The one running search. Server-thread only: commands and END_SERVER_TICK both run there. */
    private static ChamberSearchJob activeChamberSearch;
    private static boolean chamberSearchTickHookRegistered;

    /**
     * Search outward for the nearest complete chamber, generating terrain as it goes, a couple of chunks per
     * tick so the server stays playable. The sweep is a Chebyshev ring walk from the caller's chunk: rings are
     * completed whole, so the chamber reported is the nearest one in the first ring that holds any, not merely
     * the first one stumbled over. It stops at the first find, at the radius, or at {@code cancel}.
     */
    private static int locateChamber(CommandContext<CommandSourceStack> ctx, int radiusChunks) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel world = src.getLevel();
            if (CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW == null) {
                src.sendFailure(Component.literal(
                        "[latdev] the globe:cave_trap_powder_snow signature is not registered in this session."));
                return 0;
            }
            ChamberSearchJob running = activeChamberSearch;
            if (running != null) {
                src.sendFailure(Component.literal(String.format(Locale.ROOT,
                        "[latdev] a chamber search is already running (%d chunks searched, ring %d/%d); stop it"
                                + " with %s locateChamber cancel.",
                        running.chunksVisited, Math.max(running.ringRadius, 0), running.radiusChunks,
                        commandRootForEnvironment())));
                return 0;
            }
            int r = Mth.clamp(radiusChunks, 1, MAX_LOCATE_CHAMBER_RADIUS_CHUNKS);
            ensureChamberSearchTickHook();
            clearGreenMarkers(); // the search's own find is the only thing that should be glowing when it ends
            activeChamberSearch = new ChamberSearchJob(src, world, player, r);
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] locateChamber: searching out to %d chunks, %d chunks per tick — this generates"
                            + " terrain and may take a while. Stop it with %s locateChamber cancel.",
                    r, LOCATE_CHAMBER_CHUNKS_PER_TICK, commandRootForEnvironment())), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] locateChamber failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int locateChamberCancel(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ChamberSearchJob job = activeChamberSearch;
        if (job == null) {
            src.sendSuccess(() -> Component.literal("[latdev] no chamber search is running."), false);
            return 0;
        }
        activeChamberSearch = null;
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[latdev] locateChamber cancelled at ring %d of %d | %d chunks searched (%d generated)"
                        + " | %d mouths, partial=%d%s, legacy drop traps=%d",
                Math.max(job.ringRadius, 0), job.radiusChunks, job.chunksVisited, job.chunksGenerated,
                job.mouthsSeen, job.partialCount, partialReasonSummary(job.partialReasons),
                job.legacyCount)), false);
        return 1;
    }

    /**
     * Lazy hook registration: nothing is attached to the server tick until a tester actually runs a search,
     * and a server shutdown drops the job instead of leaving it pointing at a dead world.
     */
    private static void ensureChamberSearchTickHook() {
        if (chamberSearchTickHookRegistered) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(LatitudeDevCommands::onChamberSearchTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ChamberSearchJob job = activeChamberSearch;
            if (job != null && job.server == server) {
                activeChamberSearch = null;
            }
        });
        chamberSearchTickHookRegistered = true;
    }

    private static void onChamberSearchTick(MinecraftServer server) {
        ChamberSearchJob job = activeChamberSearch;
        if (job == null) {
            return;
        }
        if (job.server != server || job.world.getServer() != server) {
            activeChamberSearch = null; // a different (or shutting-down) server: never touch a stale world
            return;
        }
        job.beginTick(System.nanoTime());
        int budget = LOCATE_CHAMBER_CHUNKS_PER_TICK;
        try {
            while (budget-- > 0) {
                if (job.tickBudgetSpent()) {
                    return; // the wall clock wins even when chunks are left in this tick's allowance
                }
                if (job.ringQueue.isEmpty()) {
                    // A ring is walked whole before anything is reported, so the answer is the NEAREST
                    // chamber in that ring rather than whichever chunk happened to be swept first.
                    if (job.best != null) {
                        finishChamberSearchWithFind(job);
                        return;
                    }
                    if (!job.advanceRing()) {
                        finishChamberSearchExhausted(job);
                        return;
                    }
                    continue;
                }
                job.visitNextChunk();
            }
        } catch (Exception e) {
            activeChamberSearch = null;
            job.source.sendFailure(Component.literal("[latdev] locateChamber failed: " + e.getMessage()));
        }
    }

    private static void finishChamberSearchWithFind(ChamberSearchJob job) {
        activeChamberSearch = null;
        ChamberFind find = job.best;
        markChamber(job.world, find.chamber());
        MutableComponent line = chamberLine(find);
        job.source.sendSuccess(() -> line, false);
        job.source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[latdev] locateChamber: found in ring %d of %d | %d chunks searched (%d generated)"
                        + " | %d mouths, partial=%d%s, legacy drop traps=%d",
                Math.max(job.ringRadius, 0), job.radiusChunks, job.chunksVisited, job.chunksGenerated,
                job.mouthsSeen, job.partialCount, partialReasonSummary(job.partialReasons),
                job.legacyCount)), false);
    }

    private static void finishChamberSearchExhausted(ChamberSearchJob job) {
        activeChamberSearch = null;
        job.source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[latdev] locateChamber: no complete chamber within %d chunks | %d chunks searched"
                        + " (%d generated) | %d mouths, partial=%d%s, legacy drop traps=%d",
                job.radiusChunks, job.chunksVisited, job.chunksGenerated, job.mouthsSeen,
                job.partialCount, partialReasonSummary(job.partialReasons), job.legacyCount)), false);
        String commandRoot = commandRootForEnvironment();
        if (job.mouthsSeen == 0) {
            job.source.sendSuccess(() -> Component.literal(
                    "[latdev] not one collapse mouth generated in that radius — chambers are rare and only sit"
                            + " in glacial cave floors. Try a wider radius (max " + MAX_LOCATE_CHAMBER_RADIUS_CHUNKS
                            + ") or fly to another polar cave field and search again."), false);
        } else {
            job.source.sendSuccess(() -> Component.literal(
                    "[latdev] mouths were found but none reconstructed — stand beside one and run "
                            + commandRoot + " markChambers to see which stage its blocks are missing."), false);
        }
    }

    /**
     * One outward search. It owns its own cursor (which ring, which chunks are left in it) so a tick can stop
     * anywhere and resume next tick without re-reading a chunk.
     */
    private static final class ChamberSearchJob {
        private static final int[][] CARDINAL_CHUNKS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        private final CommandSourceStack source;
        private final ServerLevel world;
        private final MinecraftServer server;
        private final int originChunkX;
        private final int originChunkZ;
        private final double originX;
        private final double originY;
        private final double originZ;
        private final int radiusChunks;
        private final ArrayDeque<int[]> ringQueue = new ArrayDeque<>();
        private final Map<HiddenChamberScan.PartialReason, Integer> partialReasons =
                new EnumMap<>(HiddenChamberScan.PartialReason.class);
        private int ringRadius = -1;
        private int chunksVisited;
        private int chunksGenerated;
        private int mouthsSeen;
        private int partialCount;
        private int legacyCount;
        private ChamberFind best;
        /** Start of the tick currently being served, so every budget check measures the same allowance. */
        private long tickStartNanos = System.nanoTime();
        /**
         * A chunk whose mouths were only half classified when the tick's allowance ran out. It goes back to
         * the FRONT of the ring queue: the classification is re-entered next tick rather than lost, so a
         * budget stop can never make the search silently skip a chamber.
         */
        private int[] requeued;

        private ChamberSearchJob(CommandSourceStack source, ServerLevel world, ServerPlayer player,
                                 int radiusChunks) {
            this.source = source;
            this.world = world;
            this.server = world.getServer();
            this.originX = player.getX();
            this.originY = player.getY();
            this.originZ = player.getZ();
            this.originChunkX = Math.floorDiv(Mth.floor(player.getX()), 16);
            this.originChunkZ = Math.floorDiv(Mth.floor(player.getZ()), 16);
            this.radiusChunks = radiusChunks;
        }

        /** Queue the next Chebyshev ring, in a fixed order. False when the radius is exhausted. */
        private boolean advanceRing() {
            ringRadius++;
            if (ringRadius > radiusChunks) {
                return false;
            }
            for (int dx = -ringRadius; dx <= ringRadius; dx++) {
                for (int dz = -ringRadius; dz <= ringRadius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ringRadius) {
                        ringQueue.addLast(new int[]{originChunkX + dx, originChunkZ + dz});
                    }
                }
            }
            return true;
        }

        /** One tick's wall-clock allowance, shared by the ring walk and by every chunk it generates below it. */
        private void beginTick(long nanos) {
            tickStartNanos = nanos;
        }

        /** True once this tick has used its share of the server's frame. */
        private boolean tickBudgetSpent() {
            return (System.nanoTime() - tickStartNanos) >= LOCATE_CHAMBER_TICK_BUDGET_NANOS;
        }

        private void visitNextChunk() {
            int[] next = ringQueue.removeFirst();
            int chunkX = next[0];
            int chunkZ = next[1];
            boolean revisit = requeued != null && requeued[0] == chunkX && requeued[1] == chunkZ;
            requeued = null;
            boolean wasLoaded = world.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
            // Deliberately the generating call: a search that only looked at loaded chunks would answer
            // "nothing here" for terrain nobody has visited yet, which is the whole point of the command.
            ChunkAccess chunk = world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            if (!revisit) {
                chunksVisited++;
                if (!wasLoaded) {
                    chunksGenerated++;
                }
            }
            if (chunk != null) {
                List<CaveTrapEntranceScan.Cell> cells = new ArrayList<>();
                collectCollapseCells(chunk, chunkX, chunkZ, cells);
                if (!cells.isEmpty()) {
                    classifyAround(chunkX, chunkZ, cells);
                }
            }
            if (chunksVisited % LOCATE_CHAMBER_PROGRESS_INTERVAL == 0) {
                int visited = chunksVisited;
                int generated = chunksGenerated;
                int ring = ringRadius;
                int mouths = mouthsSeen;
                source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "[latdev] locateChamber: %d chunks searched (%d generated), ring %d of %d, %d mouths"
                                + " so far…", visited, generated, ring, radiusChunks, mouths)), false);
            }
        }

        /**
         * Reconstruct the mouths this chunk owns. The four cardinal neighbours are brought to FULL first (a
         * mouth may straddle a chunk border, and half a mouth reads as a different encounter), and a patch is
         * CLAIMED by the chunk holding its first cell, so a mouth spanning two chunks is reconstructed once.
         *
         * <p>Budget: one ring chunk is not one chunk of WORK. This method force-generates four neighbours, and
         * the reconstruction below reads a box wide enough to pull in a further ring through its generating
         * reader -- up to a 7x7 block of full worldgen runs, all on the server thread, all inside the single
         * tick the ring walk's own check let through. So the allowance is re-checked after every chunk brought
         * to FULL here, and when it is spent the chunk goes back to the front of the ring queue and nothing
         * this call measured is committed. Next tick re-enters with every chunk already cached, so the repeat
         * is cheap and no mouth is ever skipped. The reconstruction READS that follow generation are allowed
         * to finish; only new generation yields.
         */
        private void classifyAround(int chunkX, int chunkZ, List<CaveTrapEntranceScan.Cell> ownCells) {
            List<CaveTrapEntranceScan.Cell> cells = new ArrayList<>(ownCells);
            Set<LoadedChunk> neighbourhood = new HashSet<>();
            neighbourhood.add(new LoadedChunk(chunkX, chunkZ));
            for (int[] step : CARDINAL_CHUNKS) {
                int neighbourX = chunkX + step[0];
                int neighbourZ = chunkZ + step[1];
                ChunkAccess neighbour =
                        world.getChunkSource().getChunk(neighbourX, neighbourZ, ChunkStatus.FULL, true);
                if (tickBudgetSpent()) {
                    requeue(chunkX, chunkZ);
                    return;
                }
                if (neighbour == null) {
                    continue;
                }
                neighbourhood.add(new LoadedChunk(neighbourX, neighbourZ));
                collectCollapseCells(neighbour, neighbourX, neighbourZ, cells);
            }
            List<HiddenChamberScan.Position> collapse = new ArrayList<>();
            for (CaveTrapEntranceScan.Cell cell : cells) {
                collapse.add(new HiddenChamberScan.Position(cell.x(), cell.y(), cell.z()));
            }
            ChamberCellReader reader = new ChamberCellReader(world, true, this::tickBudgetSpent);

            /* Nothing is committed until the whole chunk is classified, so a mid-way yield cannot leave half
             * a chunk's mouths counted and then count them again on the re-entry. */
            int mouths = 0;
            int partials = 0;
            int legacy = 0;
            Map<HiddenChamberScan.PartialReason, Integer> reasons =
                    new EnumMap<>(HiddenChamberScan.PartialReason.class);
            ChamberFind found = null;
            for (List<HiddenChamberScan.Position> patch : HiddenChamberScan.groupCollapsePatches(collapse)) {
                HiddenChamberScan.Position first = patch.get(0);
                if ((first.x() >> 4) != chunkX || (first.z() >> 4) != chunkZ) {
                    continue; // another chunk in the sweep owns this mouth
                }
                mouths++;
                HiddenChamberScan.PatchOutcome outcome = HiddenChamberScan.classifyPatch(reader, patch);
                if (reader.yieldedForBudget()) {
                    requeue(chunkX, chunkZ);
                    return;
                }
                if (outcome instanceof HiddenChamberScan.Completed chamber) {
                    ChamberFind find =
                            chamberFind(world, chamber, neighbourhood, originX, originY, originZ);
                    if (found == null || find.distanceBlocks() < found.distanceBlocks()) {
                        found = find;
                    }
                } else if (outcome instanceof HiddenChamberScan.Partial partial) {
                    partials++;
                    reasons.merge(partial.reason(), 1, Integer::sum);
                } else if (outcome instanceof HiddenChamberScan.Legacy) {
                    legacy++;
                }
            }
            mouthsSeen += mouths;
            partialCount += partials;
            legacyCount += legacy;
            reasons.forEach((reason, count) -> partialReasons.merge(reason, count, Integer::sum));
            if (found != null && (best == null || found.distanceBlocks() < best.distanceBlocks())) {
                best = found;
            }
        }

        /** Put a half-classified chunk back at the FRONT of the ring, to be re-entered next tick. */
        private void requeue(int chunkX, int chunkZ) {
            requeued = new int[] {chunkX, chunkZ};
            ringQueue.addFirst(requeued);
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

    /**
     * A WHITE end-rod column over a hidden chamber's SECOND opening -- the way back out. Deliberately the same
     * height as the green entrance pillar so the pair reads as one encounter from across a cave: green is the
     * hole you fall through, white is where you climb out, and neither can be confused with the low blue
     * crevasse mark.
     */
    private static void exitBeacon(ServerLevel world, int x, int yLo, int yHi, int z) {
        int lo = Math.min(yLo, yHi);
        int hi = Math.max(yLo, yHi);
        synchronized (GREEN_MARKS) {
            if (GREEN_MARKS.size() < MARKER_QUEUE_CAP) {
                GREEN_MARKS.add(new int[]{x, lo, hi, z, 0, MARK_KIND_EXIT});
            }
        }
        emitMark(world, x, lo, hi, z, MARK_KIND_EXIT);
    }

    /**
     * Marker kinds: GREEN = a real trap roof or chamber entrance (the goal), BLUE = an open crevasse (context),
     * WHITE = the second opening of a hidden chamber (the way out of the goal).
     */
    private static final int MARK_KIND_TRAP = 0;
    private static final int MARK_KIND_SLOT = 1;
    private static final int MARK_KIND_EXIT = 2;

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
        if (kind == MARK_KIND_EXIT) {
            // Chamber exit: a WHITE end-rod column. A different particle, not a different shade of the same one
            // -- green happy-villager and white end-rod stay apart at any distance and in any biome light.
            for (int y = lo; y <= hi; y++) {
                world.sendParticles(ParticleTypes.END_ROD, true, true, cx, y + 0.5, cz, 3, 0.12, 0.12, 0.12, 0.0);
            }
            return;
        }
        // Trap roof: a solid GREEN pillar every block from the roof up, so it reads as one column from afar.
        for (int y = lo; y <= hi; y++) {
            world.sendParticles(ParticleTypes.HAPPY_VILLAGER, true, true, cx, y + 0.5, cz, 4, 0.15, 0.15, 0.15, 0.0);
        }
        // A small cardinal ring at the exact source block distinguishes the entrance itself from the tall
        // waypoint pillar. It is re-emitted by the same 60-second heartbeat, not a one-shot decoration.
        for (double[] offset : new double[][] {{0.7, 0.0}, {-0.7, 0.0}, {0.0, 0.7}, {0.0, -0.7}}) {
            world.sendParticles(ParticleTypes.HAPPY_VILLAGER, true, true,
                    cx + offset[0], lo + 0.5, cz + offset[1], 3, 0.08, 0.08, 0.08, 0.0);
        }
    }

    // --- /latdev voidCensus -- the void-taming measurement instrument (S27 diagnostic: "VOID-TAMING
    // --- un-parked (83S Y35 = sky-breached mega-void, THE experience killer)") -- NUMBERS for the before/after
    // --- taming proof, runnable via RCON on a dedicated server with no player present. Deliberately carries NO
    // --- particles and touches NO markers -- this is a counter, not a beacon (contrast markGlacial above). ---

    /** Default scan radius (chunks), mirroring {@link #DEFAULT_MARK_RADIUS_CHUNKS} -- a comfortable look around
     *  a column. */
    private static final int DEFAULT_VOID_RADIUS_CHUNKS = 4;
    /** Hard cap on the scan radius (chunks), mirroring {@link #MAX_MARK_RADIUS_CHUNKS}, to bound the column and
     *  BFS cost. */
    private static final int MAX_VOID_RADIUS_CHUNKS = 8;
    /** Sky-breach MOUTH window: the Chebyshev radius, in blocks, the local terrain-max reference is taken over
     *  (fed straight into {@link GlacialMarkScan#windowedMax}). Three times {@code PowderRoofTrap}'s own
     *  {@code REFERENCE_WINDOW_RADIUS} (4) -- deliberately WIDE. The trap system's 9x9 candidacy is tuned to
     *  find ONE narrow, deep, roofed slot at a time; a census instead has to catch the WHOLE mouth of a wide,
     *  open mega-void (the S27 83S Y35 hollow), so its reference window has to see past a much bigger footprint
     *  before it can tell "this column is deep" from "this column sits in a genuinely sunken hollow". */
    private static final int VOID_MOUTH_WINDOW_RADIUS = 12;
    /** Sky-breach MOUTH depth gate: a column counts as a mouth once its surface sits this many blocks below the
     *  {@link #VOID_MOUTH_WINDOW_RADIUS}-windowed local terrain max. This intentionally also catches ordinary
     *  crevasse mouths, not only true mega-voids -- the summary's "largest" component (see {@link
     *  #labelMouthComponents}) is what tells the two apart: a mega-void is hundreds of connected columns, a lone
     *  crevasse is a handful. */
    private static final int VOID_MOUTH_MIN_DEPTH = 15;
    /** How many of the largest mouth components get their own reported line (with a clickable teleport); the
     *  summary line always carries the true totals regardless of this cap. */
    private static final int VOID_TOP_BREACH_COUNT = 5;

    /** Player form -- scans centred on the caller. Mirrors {@link #markGlacial}'s bare/radius forms. */
    private static int voidCensus(CommandContext<CommandSourceStack> ctx, int radiusChunks) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            return voidCensusCore(src, src.getLevel(), player.getX(), player.getZ(), radiusChunks);
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] voidCensus failed: " + e.getMessage()));
            return 0;
        }
    }

    /** Coordinate form -- no player needed, so the dedicated-server CONSOLE (RCON) can run the measurement the
     *  before/after taming proof actually needs. Mirrors {@link #markGlacialAt}. */
    private static int voidCensusAt(CommandContext<CommandSourceStack> ctx, int radiusChunks, int x, int z) {
        CommandSourceStack src = ctx.getSource();
        try {
            return voidCensusCore(src, src.getLevel(), x, z, radiusChunks);
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] voidCensus failed: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Ground-truth VOID CENSUS -- the measurement instrument for the void-taming pass (S27 diagnostic: giant
     * noise-cave hollows sometimes breach the polar surface outright, leaving a sky-visible pit at, e.g., 83S
     * Y35). A taming pass is being designed to close/soften these; the before/after proof needs NUMBERS, not a
     * screenshot. This reads REAL generated blocks -- never force-generates a chunk, exactly {@link
     * #markGlacialCore}'s discipline -- and reports three independent counts:
     * <ol>
     *   <li><b>Sky-breach mouths</b> -- 4-connected components (see {@link #labelMouthComponents}) of columns
     *       whose surface sits {@link #VOID_MOUTH_MIN_DEPTH}+ blocks below the {@link #VOID_MOUTH_WINDOW_RADIUS}
     *       windowed local terrain max. The "largest" component is the number a correct taming pass drives
     *       toward ~0; a scatter of small components are ordinary crevasses and are expected to remain.</li>
     *   <li><b>subAir48</b> -- the LABYRINTH-PRESERVATION guarantee, the protect-floor number. A sparse sample
     *       (every 4th column, Y 47 down to 8) of air below the surface cave network. Taming must make the
     *       sky-visible mouths disappear WITHOUT hollowing out the underground labyrinth those same caves feed
     *       into, so a correct pass moves this number by ~0 (or up, if it adds traversable space) -- never
     *       down.</li>
     *   <li><b>nearAir</b> -- the volume taming SHOULD shrink. A sparse sample of air strictly below each
     *       column's top-solid block (so the open sky above the surface is never counted) down to at most 40
     *       blocks below the surface, floored at Y 48 so it never overlaps {@code subAir48}'s domain -- the
     *       near-surface hollowness a taming pass closes.</li>
     * </ol>
     * Reads ONLY already-loaded chunks ({@code ServerChunkCache.getChunkNow}) -- unloaded chunks are skipped and
     * counted, exactly {@link #markGlacialCore}'s "found nothing" vs "nothing was loaded" discipline. Draws NO
     * particles and leaves NO markers -- a counter, not a beacon; the only output is the summary line plus up to
     * {@link #VOID_TOP_BREACH_COUNT} of the largest breach centroids (each with a clickable {@code [teleport]},
     * exactly {@link #markGlacialCore}'s trap-roof lines).
     */
    private static int voidCensusCore(CommandSourceStack src, ServerLevel world, double centerX, double centerZ,
            int radiusChunks) {
        try {
            int r = Mth.clamp(radiusChunks, 1, MAX_VOID_RADIUS_CHUNKS);
            int centerChunkX = Math.floorDiv(Mth.floor(centerX), 16);
            int centerChunkZ = Math.floorDiv(Mth.floor(centerZ), 16);
            int minChunkX = centerChunkX - r;
            int minChunkZ = centerChunkZ - r;
            int chunksPerSide = 2 * r + 1;
            int originBlockX = minChunkX << 4;
            int originBlockZ = minChunkZ << 4;
            int span = chunksPerSide * 16;

            // Pass 1: read WORLD_SURFACE off already-loaded chunks into a continuous grid (UNLOADED elsewhere)
            // -- identical in shape to markGlacialCore's own pass 1 (same firstAir "+1" convention, same
            // getChunkNow-only discipline), so the two commands' grids are directly comparable.
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
                            surface[wx - originBlockX][wz - originBlockZ] =
                                    chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) + 1;
                        }
                    }
                }
            }

            // Pass 2: SKY-BREACH MOUTH mask, then 4-connected component labeling (VOID_MOUTH_* javadoc above
            // explains the window-radius contrast with the trap system's own reference window).
            boolean[][] mouthMask = new boolean[span][span];
            for (int gx = 0; gx < span; gx++) {
                for (int gz = 0; gz < span; gz++) {
                    int own = surface[gx][gz];
                    if (own == GlacialMarkScan.UNLOADED) {
                        continue;
                    }
                    int reference = GlacialMarkScan.windowedMax(surface, gx, gz, VOID_MOUTH_WINDOW_RADIUS);
                    // reference is guaranteed real here (the window always includes this own loaded cell), but
                    // the check costs nothing and keeps this branch honest if that ever stops being true.
                    mouthMask[gx][gz] = reference != GlacialMarkScan.UNLOADED
                            && (reference - own) >= VOID_MOUTH_MIN_DEPTH;
                }
            }
            java.util.List<int[]> components = labelMouthComponents(mouthMask);
            int mouthColumns = 0;
            int largestArea = 0;
            for (int[] c : components) {
                mouthColumns += c[0];
                largestArea = Math.max(largestArea, c[0]);
            }

            // Pass 3: LABYRINTH (subAir48) + NEAR-SURFACE HOLLOWNESS (nearAir) -- every 4th column of every
            // loaded chunk, re-fetched via getChunkNow (still loaded, so this is a cheap map lookup, not a
            // second load). Both walks are guarded against the world's real build-height bounds.
            long subAir48 = 0L;
            long nearAir = 0L;
            int worldMinY = world.getMinY();
            int worldMaxY = world.getMaxY();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int ccx = minChunkX; ccx < minChunkX + chunksPerSide; ccx++) {
                for (int ccz = minChunkZ; ccz < minChunkZ + chunksPerSide; ccz++) {
                    LevelChunk chunk = chunkSource.getChunkNow(ccx, ccz);
                    if (chunk == null) {
                        continue;
                    }
                    for (int lx = 0; lx < 16; lx += 4) {
                        for (int lz = 0; lz < 16; lz += 4) {
                            int wx = (ccx << 4) + lx;
                            int wz = (ccz << 4) + lz;
                            int ownFirstAir = surface[wx - originBlockX][wz - originBlockZ];
                            if (ownFirstAir == GlacialMarkScan.UNLOADED) {
                                continue; // can't happen for a chunk just confirmed loaded -- never trust it blindly
                            }

                            // LABYRINTH: fixed Y 47..8, the protect-floor sample -- untouched by where the
                            // surface itself sits.
                            int subHiY = Math.min(47, worldMaxY);
                            int subLoY = Math.max(8, worldMinY);
                            for (int y = subHiY; y >= subLoY; y--) {
                                cursor.set(wx, y, wz);
                                if (chunk.getBlockState(cursor).isAir()) {
                                    subAir48++;
                                }
                            }

                            // NEAR-SURFACE HOLLOWNESS: from just below the top-solid block (firstAir - 2, so
                            // open sky above the surface is never counted) down at most 40 blocks, floored at
                            // Y 48 so this never overlaps subAir48's domain.
                            int nearHiY = Math.min(ownFirstAir - 2, worldMaxY);
                            int nearLoY = Math.max(48, Math.max(ownFirstAir - 40, worldMinY));
                            for (int y = nearHiY; y >= nearLoY; y--) {
                                cursor.set(wx, y, wz);
                                if (chunk.getBlockState(cursor).isAir()) {
                                    nearAir++;
                                }
                            }
                        }
                    }
                }
            }

            // Summary (always) -- the same "scanned/skipped" tail markGlacial reports, so the two commands read
            // as one family in the console log.
            final int fr = r;
            final int fComponents = components.size();
            final int fMouthColumns = mouthColumns;
            final int fLargest = largestArea;
            final long fNearAir = nearAir;
            final long fSubAir = subAir48;
            final long fScanned = scannedColumns;
            final long fSkipped = skippedColumns;
            final int fLoaded = loadedChunks;
            final int fUnloaded = unloadedChunks;
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[latdev] voidCensus (r=%d chunks): sky-breach mouths=%d components, columns=%d, largest=%d"
                            + " | nearAir(48..surf)=%d | subAir(<48)=%d"
                            + " | scanned %d cols (%d chunks), skipped %d cols (%d unloaded)",
                    fr, fComponents, fMouthColumns, fLargest, fNearAir, fSubAir,
                    fScanned, fLoaded, fSkipped, fUnloaded)), false);

            components.sort((a, b) -> Integer.compare(b[0], a[0]));
            int topCount = Math.min(VOID_TOP_BREACH_COUNT, components.size());
            for (int i = 0; i < topCount; i++) {
                int[] c = components.get(i);
                int area = c[0];
                int worldCx = originBlockX + c[1];
                int worldCz = originBlockZ + c[2];
                String tpCommand = "/latdev tpxz " + worldCx + " " + worldCz;
                MutableComponent line = Component.literal(String.format(Locale.ROOT,
                        "[latdev]   breach: ~x=%d z=%d area=%d columns ", worldCx, worldCz, area))
                        .append(Component.literal("[teleport]").withStyle(style -> style
                                .withClickEvent(new ClickEvent.RunCommand(tpCommand))
                                .withUnderlined(true)));
                src.sendSuccess(() -> line, false);
            }
            if (components.isEmpty() && scannedColumns > 0L) {
                src.sendSuccess(() -> Component.literal(
                        "[latdev] no sky-breach mouths found in this range -- either none generated here, or "
                                + "they're in unloaded chunks (fly the area, then rescan)."), false);
            }
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[latdev] voidCensus failed: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * 4-connected component labeling over a boolean mask (row-major {@code mask[x][z]}), via breadth-first
     * flood fill in deterministic row-major scan order -- the same order every run, so two scans of the same
     * (unchanged) world produce byte-identical component ordering, which the before/after taming proof depends
     * on. Returns one entry per component as {@code {areaColumns, centroidGx, centroidGz}} (the centroid
     * already averaged and rounded, so the caller does no further math); components are returned in discovery
     * order, NOT sorted by area -- the caller ranks them. Pure int/boolean-array math, no Minecraft types --
     * kept here rather than in {@link GlacialMarkScan} because it is single-purpose to this one command.
     */
    private static java.util.List<int[]> labelMouthComponents(boolean[][] mask) {
        int sizeX = mask.length;
        if (sizeX == 0) {
            return java.util.List.of();
        }
        int sizeZ = mask[0].length;
        boolean[][] visited = new boolean[sizeX][sizeZ];
        java.util.List<int[]> components = new java.util.ArrayList<>();
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                if (!mask[x][z] || visited[x][z]) {
                    continue;
                }
                int area = 0;
                long sumX = 0L;
                long sumZ = 0L;
                visited[x][z] = true;
                queue.add(new int[]{x, z});
                while (!queue.isEmpty()) {
                    int[] cell = queue.poll();
                    area++;
                    sumX += cell[0];
                    sumZ += cell[1];
                    for (int d = 0; d < 4; d++) {
                        int nx = cell[0] + dx[d];
                        int nz = cell[1] + dz[d];
                        if (nx < 0 || nx >= sizeX || nz < 0 || nz >= sizeZ) {
                            continue;
                        }
                        if (!mask[nx][nz] || visited[nx][nz]) {
                            continue;
                        }
                        visited[nx][nz] = true;
                        queue.add(new int[]{nx, nz});
                    }
                }
                int centroidGx = (int) Math.round((double) sumX / area);
                int centroidGz = (int) Math.round((double) sumZ / area);
                components.add(new int[]{area, centroidGx, centroidGz});
            }
        }
        return components;
    }
}
