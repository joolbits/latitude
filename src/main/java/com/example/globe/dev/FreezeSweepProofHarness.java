package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TEMP-DIAG (2026-07-20) -- headless reproduction rig for the settled-sweep ZERO-CLAIMS diagnosis (TEST
 * 114/115 OPEN FINDING). Boots the ordinary headless server, then -- entirely player-free -- rebuilds the
 * exact live failure scene: a walled virgin-snow basin at 86 deg N with ONE water source poured in the
 * middle, far from any ice. The water spreads, settles, and the settled sweep
 * ({@code ServerLevelRoofedWaterFreezeMixin}) SHOULD claim the sheet within seconds; run with
 * {@code -Dlatitude.debugFreeze=true} so the FREEZE recorder heartbeat + the TEMP {@code [LAT][SWEEPPROBE]}
 * per-condition counters say exactly which gate rejects if it does not.
 *
 * <p>Trigger: {@code -Dlatdev.freezeProof=true} (the {@code runFreezeProof} gradle task). The harness sets
 * the world border to the globe diameter (the latitude functions read the border) and arms
 * {@code LatitudeBiomes} statics the way every other headless harness does; the scratch headless world is
 * dev-only. Every ~5 s it logs a census of the basin (sources / flowing / ice / pending fluid ticks per
 * key -- the pending counts probe the S20 "settled = no scheduled tick" premise DIRECTLY from harness
 * side); after {@link #RUN_TICKS} it writes {@code run-headless/latdev/freeze-sweep-proof.txt} and halts.
 *
 * <p><b>DELETE THIS CLASS when the diagnosis round closes</b> (with {@code PolarSweepProbe} and the
 * {@code freezeProof} run config).
 */
public final class FreezeSweepProofHarness {

    private static final String TRIGGER_PROP = "latdev.freezeProof";
    private static final String RADIUS_PROP = "latdev.freezeProof.radius";
    /** Real-globe-world mode: do NOT arm radius/border (GlobeMod's own Radius Sync already did at world
     *  load) -- read the armed latitude radius instead. For running the rig on a COPY of a live world. */
    private static final String REAL_WORLD_PROP = "latdev.freezeProof.realWorld";
    private static final double TARGET_LAT_DEG = 86.0;
    private static final int BUILD_AT_TICK = 100;      // let the server settle ~5 s before building
    private static final int RUN_TICKS = 2400;          // then observe for ~2 min (sweep cadence is 1.6 s)
    private static final int CENSUS_EVERY_TICKS = 100;  // one census line per recorder window (~5 s)
    private static final int BASIN_HALF = 5;            // 11x11 floor, 9x9 water interior

    private static int ticks;
    private static boolean built;
    private static boolean done;
    private static int padX;
    private static int padY; // floor Y; water sits at padY + 1
    private static int padZ;
    private static int pourX; // scenario 2: a bare source poured on the NATURAL surface (the live case)
    private static int pourY; // the poured source block's Y
    private static int radius;
    private static final List<String> reportLines = new ArrayList<>();

    private FreezeSweepProofHarness() {
    }

    static boolean isTriggered() {
        return Boolean.parseBoolean(System.getProperty(TRIGGER_PROP, "false"));
    }

    static void start(MinecraftServer server) {
        GlobeMod.LOGGER.info("[latdev][freezeProof] armed; building the 86N basin at tick {}", BUILD_AT_TICK);
        ServerTickEvents.END_SERVER_TICK.register(FreezeSweepProofHarness::onEndTick);
    }

    private static void onEndTick(MinecraftServer server) {
        if (done) {
            return;
        }
        ticks++;
        ServerLevel world = server.overworld();
        if (world == null) {
            return;
        }
        try {
            if (!built && ticks >= BUILD_AT_TICK) {
                buildRig(world);
                built = true;
                return;
            }
            if (!built) {
                return;
            }
            int sinceBuild = ticks - BUILD_AT_TICK;
            if (sinceBuild % CENSUS_EVERY_TICKS == 0) {
                census(world, sinceBuild);
            }
            if (sinceBuild >= RUN_TICKS) {
                finish(server, world);
            }
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[latdev][freezeProof] failed", t);
            done = true;
            server.halt(false);
        }
    }

    private static void buildRig(ServerLevel world) {
        boolean realWorld = Boolean.parseBoolean(System.getProperty(REAL_WORLD_PROP, "false"));
        if (realWorld) {
            // A real globe world: GlobeMod's Radius Sync armed radius/border/Mercator override at load.
            radius = (int) Math.round(com.example.globe.util.LatitudeMath.latitudeRadius(world.getWorldBorder()));
            int active = LatitudeBiomes.getActiveRadiusBlocks();
            GlobeMod.LOGGER.info("[latdev][freezeProof] realWorld mode: latitudeRadius={} activeRadius={}",
                    radius, active);
            if (active <= 0 || radius <= 100 || radius > 1_000_000) {
                GlobeMod.LOGGER.error("[latdev][freezeProof] realWorld mode but the world is not an armed globe"
                        + " (activeRadius={}, latitudeRadius={}); aborting", active, radius);
                done = true;
                world.getServer().halt(false);
                return;
            }
        } else {
            radius = Integer.getInteger(RADIUS_PROP, 10000);
            int active = LatitudeBiomes.getActiveRadiusBlocks();
            if (active > 0) {
                radius = active;
            }
            LatitudeBiomes.setWorldSeed(world.getSeed());
            LatitudeBiomes.setActiveRadiusBlocks(radius);
            // The latitude functions read the WORLD BORDER (absLatDegExact = |z| / halfSize * 90): make the
            // scratch world's border a globe-sized one, exactly like real globe world creation does.
            world.getWorldBorder().setCenter(0.0, 0.0);
            world.getWorldBorder().setSize(2.0 * radius);
        }

        padZ = (int) Math.round(TARGET_LAT_DEG / 90.0 * radius);
        // Pick a non-ocean column (the sweep's only biome-dependent gate is the IS_OCEAN exemption);
        // prefer a globe:polar_barrens column so the rig sits on the same ground the live tests did.
        padX = 8;
        String biomeId = "?";
        int firstNonOceanX = Integer.MIN_VALUE;
        String firstNonOceanBiome = "?";
        for (int candidate = 8; candidate <= 8 + 12 * 160; candidate += 160) {
            BlockPos probe = new BlockPos(candidate, 150, padZ);
            var biome = world.getBiome(probe);
            String id = biome.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
            if (biome.is(BiomeTags.IS_OCEAN)) {
                continue;
            }
            if (firstNonOceanX == Integer.MIN_VALUE) {
                firstNonOceanX = candidate;
                firstNonOceanBiome = id;
            }
            if (id.endsWith("polar_barrens")) {
                firstNonOceanX = candidate;
                firstNonOceanBiome = id;
                break;
            }
        }
        if (firstNonOceanX != Integer.MIN_VALUE) {
            padX = firstNonOceanX;
            biomeId = firstNonOceanBiome;
        }
        int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, padX, padZ);
        padY = Math.max(120, Math.min(surfaceY + 24, world.getMaxY() - 8));

        // Forceload a 3x3 chunk ring around the pad so the chunks BLOCK-TICK with no player online.
        int chunkX = padX >> 4;
        int chunkZ = padZ >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setChunkForced(chunkX + dx, chunkZ + dz, true);
            }
        }

        // The basin: 11x11 virgin-snow floor, 1-high rim on the perimeter, one source in the middle.
        for (int dx = -BASIN_HALF; dx <= BASIN_HALF; dx++) {
            for (int dz = -BASIN_HALF; dz <= BASIN_HALF; dz++) {
                BlockPos floor = new BlockPos(padX + dx, padY, padZ + dz);
                world.setBlockAndUpdate(floor, Blocks.SNOW_BLOCK.defaultBlockState());
                boolean rim = Math.abs(dx) == BASIN_HALF || Math.abs(dz) == BASIN_HALF;
                BlockPos above = floor.above();
                world.setBlockAndUpdate(above, rim
                        ? Blocks.SNOW_BLOCK.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
                world.setBlockAndUpdate(above.above(), Blocks.AIR.defaultBlockState());
            }
        }
        world.setBlockAndUpdate(new BlockPos(padX, padY + 1, padZ), Blocks.WATER.defaultBlockState());

        double lat = Math.abs(padZ) / (double) radius * 90.0;
        String line = String.format(Locale.ROOT,
                "rig built: x=%d y=%d z=%d latDeg=%.3f radius=%d biome=%s (water source at center, %dx%d basin)",
                padX, padY, padZ, lat, radius, biomeId, 2 * BASIN_HALF - 1, 2 * BASIN_HALF - 1);
        GlobeMod.LOGGER.info("[latdev][freezeProof] {}", line);
        reportLines.add(line);

        // Scenario 2 -- THE LIVE CASE: one bare source poured on the NATURAL surface (no basin, free
        // runoff), 96 blocks east. Same latitude, same chunk-forcing treatment.
        pourX = padX + 96;
        int pourChunkX = pourX >> 4;
        int pourChunkZ = padZ >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setChunkForced(pourChunkX + dx, pourChunkZ + dz, true);
            }
        }
        pourY = world.getHeight(Heightmap.Types.WORLD_SURFACE, pourX, padZ);
        String pourBiome = world.getBiome(new BlockPos(pourX, pourY, padZ))
                .unwrapKey().map(k -> k.identifier().toString()).orElse("?");
        world.setBlockAndUpdate(new BlockPos(pourX, pourY, padZ), Blocks.WATER.defaultBlockState());
        String pourLine = String.format(Locale.ROOT,
                "pour placed: x=%d y=%d z=%d biome=%s (bare source on natural surface, free runoff)",
                pourX, pourY, padZ, pourBiome);
        GlobeMod.LOGGER.info("[latdev][freezeProof] {}", pourLine);
        reportLines.add(pourLine);
    }

    /** Count basin contents + pending fluid ticks under both water keys -- the settled premise, probed direct. */
    private static void census(ServerLevel world, int sinceBuild) {
        int sources = 0;
        int flowing = 0;
        int ice = 0;
        int pendFlowKey = 0;
        int pendWaterKey = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -BASIN_HALF + 1; dx <= BASIN_HALF - 1; dx++) {
            for (int dz = -BASIN_HALF + 1; dz <= BASIN_HALF - 1; dz++) {
                cursor.set(padX + dx, padY + 1, padZ + dz);
                var fluid = world.getFluidState(cursor);
                if (fluid.getType() == Fluids.WATER) {
                    sources++;
                } else if (fluid.getType() == Fluids.FLOWING_WATER) {
                    flowing++;
                }
                if (world.getBlockState(cursor).is(Blocks.ICE)) {
                    ice++;
                }
                if (world.getFluidTicks().hasScheduledTick(cursor, Fluids.FLOWING_WATER)) {
                    pendFlowKey++;
                }
                if (world.getFluidTicks().hasScheduledTick(cursor, Fluids.WATER)) {
                    pendWaterKey++;
                }
            }
        }
        String line = String.format(Locale.ROOT,
                "basin t=+%ds src=%d flow=%d ice=%d pendF=%d pendW=%d",
                sinceBuild / 20, sources, flowing, ice, pendFlowKey, pendWaterKey);
        GlobeMod.LOGGER.info("[latdev][freezeProof] {}", line);
        reportLines.add(line);

        // Scenario 2 census: a 17x17 x 9-deep box around the poured source (runoff can slide downhill).
        int pSources = 0;
        int pFlowing = 0;
        int pIce = 0;
        int pPendF = 0;
        int pPendW = 0;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = -6; dy <= 2; dy++) {
                    cursor.set(pourX + dx, pourY + dy, padZ + dz);
                    var fluid = world.getFluidState(cursor);
                    if (fluid.getType() == Fluids.WATER) {
                        pSources++;
                    } else if (fluid.getType() == Fluids.FLOWING_WATER) {
                        pFlowing++;
                    }
                    if (world.getBlockState(cursor).is(Blocks.ICE)) {
                        pIce++;
                    }
                    if (world.getFluidTicks().hasScheduledTick(cursor, Fluids.FLOWING_WATER)) {
                        pPendF++;
                    }
                    if (world.getFluidTicks().hasScheduledTick(cursor, Fluids.WATER)) {
                        pPendW++;
                    }
                }
            }
        }
        String pourLine = String.format(Locale.ROOT,
                "pour  t=+%ds src=%d flow=%d ice=%d pendF=%d pendW=%d",
                sinceBuild / 20, pSources, pFlowing, pIce, pPendF, pPendW);
        GlobeMod.LOGGER.info("[latdev][freezeProof] {}", pourLine);
        reportLines.add(pourLine);
    }

    private static void finish(MinecraftServer server, ServerLevel world) {
        done = true;
        try {
            census(world, RUN_TICKS);
            Path out = server.getServerDirectory().toAbsolutePath().normalize()
                    .resolve("latdev").resolve("freeze-sweep-proof.txt");
            Files.createDirectories(out.getParent());
            Files.write(out, (String.join(System.lineSeparator(), reportLines) + System.lineSeparator())
                    .getBytes(StandardCharsets.UTF_8));
            GlobeMod.LOGGER.info("[latdev][freezeProof] report written: {}", out);
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[latdev][freezeProof] report write failed", t);
        } finally {
            GlobeMod.LOGGER.info("[latdev][freezeProof] stopping server");
            server.halt(false);
        }
    }
}
