package com.example.globe.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.example.globe.util.ValueNoise2D;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

public final class LatitudeBiomes {
    private LatitudeBiomes() {
    }

    private static RegistryEntry<Biome> pickTropicalGradientNoSwamp(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        long seed = WORLD_SEED;

        double bandStart = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
        double bandEnd = LatitudeBands.Band.TEMPERATE.lowDeg() / 90.0;
        double u = clamp((t - bandStart) / (bandEnd - bandStart), 0.0, 1.0);
        double ladderT = 1.0 - u;

        double jitterN = (blobNoise01(seed, chunkX, chunkZ, 8, 0xBADC0FFEE0DDF00DL) * 2.0) - 1.0;
        double tJitter = ladderT + (jitterN * 0.12);
        tJitter = clamp(tJitter, 0.0, 1.0);
        tJitter = smoothstep(tJitter);

        double stepFloat = tJitter * 4.0;
        int baseStep = clampInt((int) Math.floor(stepFloat), 0, 3);
        double stepFrac = stepFloat - baseStep;
        int step = applyTropicalStepDither(seed, blockX, blockZ, baseStep, stepFrac);

        // Humidity-biased per-step diversion: humid patches within each ladder step
        double humidity = subtropicalHumidityNoise(blockX, blockZ);
        double humidThreshold = subtropicalHumidityThreshold(step);
        if (humidity < humidThreshold) {
            return pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 110 + step, 0x5B70 + step,
                    LAT_SUBTROPICAL_HUMID_PRIMARY, LAT_SUBTROPICAL_HUMID_SECONDARY, LAT_SUBTROPICAL_HUMID_ACCENT);
        }
        boolean coldShoulderArid = step == 0 && u >= SUBTROPICAL_ARID_SHOULDER_U;

        RegistryEntry<Biome> pick = switch (step) {
            case 1 -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT);
            case 2 -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 102, 0x7A22,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            case 3 -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 103, 0x7A33,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            default -> coldShoulderArid
                    ? pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT)
                    : pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 100, 0x7A00,
                    LAT_ARID_PRIMARY, LAT_ARID_SECONDARY, LAT_ARID_ACCENT);
        };
        RegistryEntry<Biome> out = softenSubtropicalBadlands(biomes, base, pick);
        recordWarmDryPath("TROPICAL_GRADIENT", base, out, blockX, blockZ, BAND_SUBTROPICAL, warmProvinceClass(blockX, blockZ, BAND_SUBTROPICAL));
        return out;
    }

    private static RegistryEntry<Biome> pickTropicalGradientNoSwamp(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        long seed = WORLD_SEED;

        double bandStart = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
        double bandEnd = LatitudeBands.Band.TEMPERATE.lowDeg() / 90.0;
        double u = clamp((t - bandStart) / (bandEnd - bandStart), 0.0, 1.0);
        double ladderT = 1.0 - u;

        double jitterN = (blobNoise01(seed, chunkX, chunkZ, 8, 0xBADC0FFEE0DDF00DL) * 2.0) - 1.0;
        double tJitter = ladderT + (jitterN * 0.12);
        tJitter = clamp(tJitter, 0.0, 1.0);
        tJitter = smoothstep(tJitter);

        double stepFloat = tJitter * 4.0;
        int baseStep = clampInt((int) Math.floor(stepFloat), 0, 3);
        double stepFrac = stepFloat - baseStep;
        int step = applyTropicalStepDither(seed, blockX, blockZ, baseStep, stepFrac);

        // Humidity-biased per-step diversion: humid patches within each ladder step
        double humidity = subtropicalHumidityNoise(blockX, blockZ);
        double humidThreshold = subtropicalHumidityThreshold(step);
        if (humidity < humidThreshold) {
            return pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 110 + step, 0x5B70 + step,
                    LAT_SUBTROPICAL_HUMID_PRIMARY, LAT_SUBTROPICAL_HUMID_SECONDARY, LAT_SUBTROPICAL_HUMID_ACCENT);
        }
        boolean coldShoulderArid = step == 0 && u >= SUBTROPICAL_ARID_SHOULDER_U;

        RegistryEntry<Biome> pick = switch (step) {
            case 1 -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT);
            case 2 -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 102, 0x7A22,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            case 3 -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 103, 0x7A33,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            default -> coldShoulderArid
                    ? pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT)
                    : pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 100, 0x7A00,
                    LAT_ARID_PRIMARY, LAT_ARID_SECONDARY, LAT_ARID_ACCENT);
        };
        RegistryEntry<Biome> out = softenSubtropicalBadlands(biomes, base, pick);
        recordWarmDryPath("TROPICAL_GRADIENT", base, out, blockX, blockZ, BAND_SUBTROPICAL, warmProvinceClass(blockX, blockZ, BAND_SUBTROPICAL));
        return out;
    }

    private static final int BAND_TROPICAL = 0;
    private static final int BAND_SUBTROPICAL = 1;
    private static final int BAND_TEMPERATE = 2;
    private static final int BAND_SUBPOLAR = 3;
    private static final int BAND_POLAR = 4;
    private static final double SUBTROPICAL_ARID_SHOULDER_U = 0.92;

    private static int bandIndexForBand(LatitudeBands.Band band) {
        return switch (band) {
            case TROPICAL -> BAND_TROPICAL;
            case SUBTROPICAL -> BAND_SUBTROPICAL;
            case TEMPERATE -> BAND_TEMPERATE;
            case SUBPOLAR -> BAND_SUBPOLAR;
            case POLAR -> BAND_POLAR;
        };
    }

    private static LatitudeBands.Band bandForIndex(int bandIndex) {
        return switch (bandIndex) {
            case BAND_TROPICAL -> LatitudeBands.Band.TROPICAL;
            case BAND_SUBTROPICAL -> LatitudeBands.Band.SUBTROPICAL;
            case BAND_TEMPERATE -> LatitudeBands.Band.TEMPERATE;
            case BAND_SUBPOLAR -> LatitudeBands.Band.SUBPOLAR;
            case BAND_POLAR -> LatitudeBands.Band.POLAR;
            default -> LatitudeBands.Band.TROPICAL;
        };
    }

    public static LatitudeBands.Band bandFromIndex(int bandIndex) {
        return bandForIndex(bandIndex);
    }

    /**
     * Continuous snow-paint authority for surface painters (no spatial jitter).
    * Uses the same boundary jitter as band blending to avoid ruler-straight thresholds.
     *
     * @param blockX world X (blocks)
     * @param blockZ world Z (blocks)
     * @param borderRadiusBlocks caller border radius (falls back to ACTIVE_RADIUS or 1)
     * @return alpha in [0,1]; higher means stronger permission to paint snow/ice
     */
    public static double snowPaintAlpha(int blockX, int blockZ, int borderRadiusBlocks) {
        int effectiveRadius = ACTIVE_RADIUS_BLOCKS > 0 ? ACTIVE_RADIUS_BLOCKS : borderRadiusBlocks;
        if (effectiveRadius <= 0) effectiveRadius = 1;
        int lat = Math.abs(blockZ);
        double tBase = (double) lat / (double) effectiveRadius;
        double t = applyBoundaryJitter(blockX, blockZ, effectiveRadius, tBase);
        double deg = LatitudeMath.clamp(t * 90.0, 0.0, 90.0);

        // Shoulder window for painter activation: block fully ≤50°, allow fully ≥56°.
        double start = 50.0;
        double end = 56.0;
        if (deg <= start) return 0.0;
        if (deg >= end) return 1.0;
        double u = (deg - start) / (end - start);
        return smoothstep(u);
    }

    /**
     * Compute the authoritative land band index using the same jitter/blend path as the picker,
     * including the active radius override logic. This is exported for atlas/report so overlays
     * stay aligned with the picker’s decision.
     */
    public static int authoritativeLandBandIndex(int blockX, int blockZ, int borderRadiusBlocks) {
        int activeRadius = ACTIVE_RADIUS_BLOCKS;
        boolean overrideDisabled = Boolean.getBoolean("latitude.disableRadiusOverride");
        int effectiveRadius = (!overrideDisabled && activeRadius > 0) ? activeRadius : borderRadiusBlocks;
        if (effectiveRadius <= 0) {
            return BAND_TROPICAL;
        }
        int lat = Math.abs(blockZ);
        double tBase = (double) lat / (double) effectiveRadius;
        double t = applyBoundaryJitter(blockX, blockZ, effectiveRadius, tBase);
        LatitudeBands.Band band = bandForAbsLatFraction(tBase);
        return latitudeBandIndexWithBlend(blockX, blockZ, effectiveRadius, band, t);
    }

    /**
     * Diagnostic-only accessor for atlas/export tooling: returns the pre-rewrite band choice
     * from the blend comparator (chosenBandIndex) before the subtropical->temperate constitutional
     * rewrite is applied.
     */
    public static int authoritativeChosenBandIndex(int blockX, int blockZ, int borderRadiusBlocks) {
        int activeRadius = ACTIVE_RADIUS_BLOCKS;
        boolean overrideDisabled = Boolean.getBoolean("latitude.disableRadiusOverride");
        int effectiveRadius = (!overrideDisabled && activeRadius > 0) ? activeRadius : borderRadiusBlocks;
        if (effectiveRadius <= 0) {
            return BAND_TROPICAL;
        }
        int lat = Math.abs(blockZ);
        double tBase = (double) lat / (double) effectiveRadius;
        double t = applyBoundaryJitter(blockX, blockZ, effectiveRadius, tBase);
        LatitudeBands.Band band = bandForAbsLatFraction(tBase);
        return latitudeBandChosenIndexWithBlend(blockX, blockZ, effectiveRadius, band, t);
    }

    private static RegistryEntry<Biome> pickBeachForBand(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, int bandIndex) {
        if (bandIndex <= 2) {
            try {
                return biome(biomes, "minecraft:beach");
            } catch (Throwable ignored) {
                return base;
            }
        }

        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long roll = hash64(chunkX, chunkZ, 0xBEEFBEEF);
        boolean snowy = Long.remainderUnsigned(roll, 100L) < 70L;

        String target = snowy ? "minecraft:snowy_beach" : "minecraft:stony_shore";
        try {
            return biome(biomes, target);
        } catch (Throwable ignored) {
            return base;
        }
    }

    private static boolean shouldSkipSavannaGate(String callerContext) {
        if (callerContext == null) {
            return false;
        }
        String ctx = callerContext.trim().toUpperCase(java.util.Locale.ROOT);
        if (DEBUG_SKIP_SAVANNA_GATE && ("SOURCE".equals(ctx) || "MIXIN".equals(ctx))) {
            return true;
        }
        if (DEBUG_SKIP_SAVANNA_GATE_MIXIN && "MIXIN".equals(ctx)) {
            return true;
        }
        return false;
    }

    private static void logSavannaGateSkip(String callerContext,
                                           int blockX,
                                           int blockZ,
                                           String incomingBiomeId,
                                           int landBandIndex,
                                           int robustDelta) {
        if (!DEBUG_SKIP_SAVANNA_GATE && !DEBUG_SKIP_SAVANNA_GATE_MIXIN) {
            return;
        }
        int skips = SAVANNA_GATE_DEBUG_SKIPS.incrementAndGet();
        if (skips <= 10 || skips % 2000 == 0) {
            LOGGER.info("[Latitude][SpawnGate] SKIPPED total={} x={} z={} band={} incoming={} robust={} context={}",
                    skips,
                    blockX,
                    blockZ,
                    landBandIndex,
                    incomingBiomeId,
                    robustDelta,
                    callerContext);
        }
    }

    private static RegistryEntry<Biome> pickTemperateUplandBiome(Collection<RegistryEntry<Biome>> biomes, int blockX, int blockZ) {
        int poolSize = TEMPERATE_UPLAND_BIOMES.length;
        if (poolSize == 0) {
            return null;
        }
        double n = ValueNoise2D.sampleBlocks(WORLD_SEED ^ UPLAND_POOL_SALT, blockX, blockZ, UPLAND_SCALE_BLOCKS);
        int idx = (int) Math.floor(n * (double) poolSize);
        if (idx < 0) {
            idx = 0;
        } else if (idx >= poolSize) {
            idx = poolSize - 1;
        }
        return entryById(biomes, TEMPERATE_UPLAND_BIOMES[idx]);
    }

    private static RegistryEntry<Biome> pickBeachForBand(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, int bandIndex) {
        if (bandIndex <= 2) {
            RegistryEntry<Biome> entry = entryById(biomes, "minecraft:beach");
            return entry != null ? entry : base;
        }

        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long roll = hash64(chunkX, chunkZ, 0xBEEFBEEF);
        boolean snowy = Long.remainderUnsigned(roll, 100L) < 70L;

        String target = snowy ? "minecraft:snowy_beach" : "minecraft:stony_shore";
        RegistryEntry<Biome> entry = entryById(biomes, target);
        return entry != null ? entry : base;
    }

    private static boolean allowBeachShortcut(NoiseChunkGenerator generator,
                                              int surfaceY) {
        int seaLevel = previewSeaLevel(generator);
        int seaLevelDelta = surfaceY - seaLevel;
        if (seaLevelDelta > BEACH_SHORTCUT_MAX_SEA_LEVEL_DELTA) {
            return false;
        }
        if (uplandT(surfaceY) > BEACH_SHORTCUT_MAX_UPLAND_T) {
            return false;
        }
        return true;
    }

    private static RegistryEntry<Biome> applyLandOverrides(Registry<Biome> biomes, RegistryEntry<Biome> pick, int blockX, int blockZ, int bandIndex) {
        return pick;
    }

    private static RegistryEntry<Biome> applyLandOverrides(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> pick, int blockX, int blockZ, int bandIndex) {
        return pick;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("LatitudeBiomes");
    private static final boolean DEBUG_BIOMES = Boolean.getBoolean("latitude.debugBiomes")
            || Boolean.getBoolean("latitude.debugBiomePick");
    private static final boolean DEBUG_SUBTROPICAL_JUNGLE = Boolean.getBoolean("latitude.debug.subtropicalJungle");
    private static final boolean DEBUG_BLEND = Boolean.getBoolean("latitude.debugBlend");
    private static final boolean DEBUG_LEAK = Boolean.getBoolean("latitude.debugLeak");
    private static final boolean DEBUG_FINAL_SANITIZE = Boolean.getBoolean("latitude.debugFinalSanitize");
    private static final boolean DEBUG_MANGROVE_DENIAL = Boolean.getBoolean("latitude.debugMangroveDenial");
    private static final boolean DEBUG_MANGROVE_ORIGIN = Boolean.getBoolean("latitude.debugMangroveOrigin");
    private static final boolean DEBUG_OCEAN_DIST = Boolean.getBoolean("latitude.debugOceanDist");
    private static final boolean DEBUG_MANGROVE_INVITE = Boolean.getBoolean("latitude.debugMangroveInvite");
    private static final boolean DEBUG_SPARSE_JUNGLE_AUDIT = Boolean.getBoolean("latitude.debug.sparseJungleAudit");
    private static final boolean DEBUG_SAVANNA_GATE_AUDIT = Boolean.getBoolean("latitude.debug.savannaGateAudit");
    private static final boolean DEBUG_SAVANNA_SPAWN_GATE = Boolean.getBoolean("latitude.debugSpawnGate");
    private static final boolean DEBUG_SKIP_SAVANNA_GATE = Boolean.getBoolean("latitude.debugSkipSavannaGate");
    private static final boolean DEBUG_SKIP_SAVANNA_GATE_MIXIN = Boolean.getBoolean("latitude.debugSkipSavannaGateMixin");
    private static final boolean DEBUG_WARM_WINDSWEPT_LATE_PATH = Boolean.getBoolean("latitude.debugWarmWindsweptLatePath");
    private static final boolean DEBUG_WARM_POOL_MEMBERSHIP = Boolean.getBoolean("latitude.debugWarmPoolMembership");
    private static final boolean DEBUG_SUBTROPICAL_SWAMP_SOURCE_TRACE = Boolean.getBoolean("latitude.debugSubtropicalSwampSourceTrace");
    private static final boolean DEBUG_WARM_POOL_AUDIT = Boolean.getBoolean("latitude.debug.warmPoolAudit")
            || "true".equalsIgnoreCase(System.getenv("LATITUDE_DEBUG_WARM_POOL_AUDIT"));
    private static final boolean DEBUG_WARM_DRY_PATHS = Boolean.getBoolean("latitude.debugWarmDryPaths");
    private static final boolean DEBUG_WETLANDS = Boolean.getBoolean("latitude.debugWetlands");
    private static final long WARM_POOL_AUDIT_LOG_EVERY = Long.getLong("latitude.warmPoolAudit.logEvery", 8192L);
    private static final int WARM_DRY_PATH_AUDIT_SUMMARY_EVERY = Integer.getInteger("latitude.warmDryPaths.summaryEvery", 50000);
    private static final int SPARSE_JUNGLE_AUDIT_LOG_LIMIT = Integer.getInteger("latitude.sparseJungleAudit.maxLogs", 200);
    private static final int SAVANNA_GATE_AUDIT_LOG_LIMIT = Integer.getInteger("latitude.savannaGateAudit.maxLogs", 200);
    private static final int SAVANNA_GATE_AUDIT_SUMMARY_EVERY = Integer.getInteger("latitude.savannaGateAudit.summaryEvery", 50000);
    private static final int DEBUG_LIMIT = Integer.getInteger("latitude.debugBiomes.limit", 200);
    private static volatile long WORLD_SEED = 0L;
    public static volatile int ACTIVE_RADIUS_BLOCKS = 0;
    private static OceanDistanceField OCEAN_DISTANCE_FIELD = null;
    private static final AtomicInteger DEBUG_COUNT = new AtomicInteger();
    private static final AtomicInteger BLEND_DEBUG_COUNT = new AtomicInteger();
    private static final AtomicInteger LEAK_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger MANGROVE_OCEAN_DIST_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_TOTAL = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_IN_SAVANNA = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_IN_PLATEAU = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_IN_WINDSWEPT = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_IN_OTHER = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_OUT_SAVANNA = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_OUT_WINDSWEPT = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_OUT_OTHER = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_REASON_HIGH = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_REASON_LOW = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_REASON_DEADBAND = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_DEBUG_TOTAL = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_DEBUG_SOURCE = new AtomicInteger();
    private static final AtomicInteger SAVANNA_GATE_DEBUG_SKIPS = new AtomicInteger();
    private static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap SAVANNA_GATE_SEEN = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    private static final AtomicInteger MANGROVE_EVAL_AUDIT_COUNT = new AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger MANGROVE_EVAL_AUDIT_N =
            new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicLong MANGROVE_INVITE_LOG_COUNT = new java.util.concurrent.atomic.AtomicLong();
    private static final AtomicLong SPARSE_TAG_PICK_COUNT = new AtomicLong();
    private static final AtomicLong SPARSE_SANITIZE_REWRITE_COUNT = new AtomicLong();
    private static final AtomicLong SPARSE_CANOPY_FALLBACK_COUNT = new AtomicLong();
    private static final AtomicLong SPARSE_WARM_FALLBACK_COUNT = new AtomicLong();
    private static final AtomicLong SPARSE_FINAL_SAVANNA_COUNT = new AtomicLong();
    private static final AtomicLong SPARSE_LATITUDE_FALLBACK_COUNT = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_TOTAL = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_ENTER_TROPICAL_OPEN = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_OPEN_JUNGLE_BRANCH_ENTER = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_OPEN_STRONG_BRANCH_ENTER = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_PICK_SAVANNA = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_PICK_PLATEAU = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_PICK_WSAV = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_PICK_DESERT = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_PICK_JUNGLE = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_PICK_SPARSE = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_PICK_PLAINS = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_PICK_OTHER = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_REROUTE = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_NS_ENTER = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_NS_RETURN_SAVANNA = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_NS_RETURN_DESERT = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_NS_RETURN_BASE = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_NS_RETURN_PLAINS_ATTEMPT = new AtomicLong();
    private static final AtomicLong WARM_POOL_AUDIT_NS_RETURN_OTHER = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_TOTAL = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_WARM_DRY_SELECTOR = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_TROPICAL_GRADIENT = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_OPEN_TROPICAL_FALLBACK = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_PICK_WARM_FALLBACK = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_FINAL_SAVANNA_CLAMP = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_ENFORCE_LAND_BAND_POOL_REWRITE = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_SANITIZE_REWRITE = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_DIRECT_POOL_PICK = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_SAVANNA = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_SAVANNA_PLATEAU = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_WINDSWEPT_SAVANNA = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_DESERT = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_BADLANDS = new AtomicLong();
    private static final AtomicLong WARM_DRY_PATH_OTHER = new AtomicLong();
    private static final AtomicReference<String> WARM_DRY_PATH_LAST_SOURCE = new AtomicReference<>("");
    private static final AtomicReference<String> WARM_DRY_PATH_LAST_FROM = new AtomicReference<>("");
    private static final AtomicReference<String> WARM_DRY_PATH_LAST_TO = new AtomicReference<>("");
    private static final AtomicReference<String> WARM_POOL_AUDIT_LAST_SOURCE = new AtomicReference<>("");
    private static final AtomicReference<String> WARM_POOL_AUDIT_LAST_PICK = new AtomicReference<>("");
    private static final AtomicReference<String> WARM_POOL_AUDIT_LAST_BUCKET = new AtomicReference<>("");
    private static final long[] WARM_OPEN_BUCKET_COUNTS = new long[60];
    private static final long[] WARM_OPEN_NS_SAVANNA_BUCKETS = new long[60];
    private static final long[] WARM_OPEN_NS_DESERT_BUCKETS = new long[60];

    // --- Polar atlas/headless parity instrumentation (latitude.debugPolarAtlas) ---
    private static final boolean DEBUG_POLAR_ATLAS = Boolean.getBoolean("latitude.debugPolarAtlas");
    private static final AtomicLong PAR_SAMPLES            = new AtomicLong();
    private static final AtomicLong PAR_NOISE_MOUNTAIN     = new AtomicLong();
    private static final AtomicLong PAR_NONZERO_HEIGHT     = new AtomicLong();
    private static final AtomicLong PAR_NONZERO_DELTA      = new AtomicLong();
    private static final AtomicLong PAR_MOUNTAIN_AUTHORITY = new AtomicLong();
    private static final AtomicLong PAR_INITIAL_ALPINE     = new AtomicLong();
    private static final AtomicLong PAR_FINAL_ALPINE       = new AtomicLong();
    private static final AtomicLong PAR_REWRITTEN_SNOWY    = new AtomicLong();
    private static final AtomicInteger PAR_PARITY_HIT_LOG  = new AtomicInteger();

    // --- Polar cap live trace (latitude.debugPolarCapTrace) ---
    private static final boolean DEBUG_POLAR_CAP_TRACE = Boolean.getBoolean("latitude.debugPolarCapTrace");

    private static boolean isTemperateForestFamily(RegistryEntry<Biome> biome) {
        return biome != null && (
                isBiomeId(biome, "minecraft:dark_forest")
                        || isBiomeId(biome, "minecraft:forest")
                || isBiomeId(biome, "minecraft:birch_forest")
                || isBiomeId(biome, "minecraft:old_growth_birch_forest")
                || isBiomeId(biome, "minecraft:flower_forest")
                || isBiomeId(biome, "minecraft:pale_garden"));
    }
    private static final long[] WARM_OPEN_NS_BASE_BUCKETS = new long[60];
    private static final long[] WARM_OPEN_NS_PLAINS_BUCKETS = new long[60];
    private static final long[] WARM_OPEN_NS_OTHER_BUCKETS = new long[60];
    private static final AtomicLong SAVANNA_AUDIT_TOTAL = new AtomicLong();
    private static final AtomicLong SAVANNA_AUDIT_LOGGED = new AtomicLong();
    private static final AtomicLong SAVANNA_AUDIT_ENTER = new AtomicLong();
    private static final AtomicLong SAVANNA_AUDIT_NOT_SAVANNA = new AtomicLong();
    private static final AtomicLong SAVANNA_AUDIT_FAIL_LOW = new AtomicLong();
    private static final AtomicLong SAVANNA_AUDIT_FAIL_DEADBAND = new AtomicLong();
    private static final AtomicLong SAVANNA_AUDIT_PASS = new AtomicLong();
    private static final AtomicLong SAVANNA_AUDIT_SELECTED = new AtomicLong();
    private static final AtomicLong SAVANNA_AUDIT_UPLAND = new AtomicLong();
    private static final AtomicLong SAVANNA_AUDIT_REAL_PREVIEW = new AtomicLong();
    private static final AtomicLong SAVANNA_AUDIT_PREVIEW_MISSING = new AtomicLong();
    private static final AtomicLong SPARSE_AUDIT_TOTAL = new AtomicLong();
    private static final AtomicLong SPARSE_AUDIT_LOGGED = new AtomicLong();
    private static final AtomicBoolean RADIUS_MISMATCH_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean SUBPOLAR_JUNGLE_TRACE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean SURFACE_Y_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean PREVIEW_TERRAIN_SKIP_LOGGED = new AtomicBoolean(false);
    // Surface classification is column-stable. Never use caller Y for these.
    public static final int SURFACE_CLASSIFY_Y = 96; // constant sampling layer
    private static final int LEAK_LOG_LIMIT = Integer.getInteger("latitude.leakLogLimit", 200);
    private static final int SAVANNA_GATE_LOG_EVERY = Integer.getInteger("latitude.savannaGateLogEvery", 2048);
    private static final ThreadLocal<String> LAST_SELECTION_PATH = new ThreadLocal<>();
    private static final ThreadLocal<WarmPoolMembershipSnapshot> LAST_WARM_POOL_MEMBERSHIP_SNAPSHOT = new ThreadLocal<>();
    private static final String PATH_TAG_PICK = "tag-based pick";
    private static final String PATH_FALLBACK_PICK = "explicit fallback list pick";
    private static final String PATH_RETURN_BASE = "return base";
    private static boolean TAG_LOGGED = false;

    private static void logMangroveDenial(String reason) {
        if (DEBUG_MANGROVE_DENIAL) {
            LOGGER.info("[latdev] mangroveDenied reason={}", reason);
        }
    }

    public static boolean isSparseJungleAuditEnabled() {
        return DEBUG_SPARSE_JUNGLE_AUDIT;
    }

    public static String biomeIdPublic(RegistryEntry<Biome> entry) {
        return biomeId(entry);
    }

    public static boolean isBiomeIdPublic(RegistryEntry<Biome> entry, String id) {
        return isBiomeId(entry, id);
    }

    public static void auditSparseJungleExternal(String bucket, int blockX, int blockZ, int landBandIndex, String detail, RegistryEntry<Biome> pre, RegistryEntry<Biome> post) {
        auditSparseJungle(bucket, blockX, blockZ, landBandIndex, detail, biomeId(pre), biomeId(post));
    }

    private static void auditSparseJungle(String bucket, int blockX, int blockZ, int landBandIndex, String detail, String preBiomeId, String postBiomeId) {
        if (!DEBUG_SPARSE_JUNGLE_AUDIT) {
            return;
        }
        AtomicLong counter = switch (bucket) {
            case "TAG_PICK_DIRECT" -> SPARSE_TAG_PICK_COUNT;
            case "SANITIZE_REWRITE" -> SPARSE_SANITIZE_REWRITE_COUNT;
            case "CANOPY_FALLBACK" -> SPARSE_CANOPY_FALLBACK_COUNT;
            case "WARM_SAFETY_FALLBACK" -> SPARSE_WARM_FALLBACK_COUNT;
            case "FINAL_SAVANNA_CLAMP" -> SPARSE_FINAL_SAVANNA_COUNT;
            case "LATITUDE_FALLBACK" -> SPARSE_LATITUDE_FALLBACK_COUNT;
            default -> null;
        };
        if (counter == null) {
            return;
        }
        counter.incrementAndGet();
        long total = SPARSE_AUDIT_TOTAL.incrementAndGet();
        long logged = SPARSE_AUDIT_LOGGED.get();
        if (logged < SPARSE_JUNGLE_AUDIT_LOG_LIMIT) {
            if (SPARSE_AUDIT_LOGGED.incrementAndGet() <= SPARSE_JUNGLE_AUDIT_LOG_LIMIT) {
                LOGGER.info("[LAT][SPARSE_AUDIT] bucket={} band={} x={} z={} pre={} post={} detail={}",
                        bucket, landBandIndex, blockX, blockZ, preBiomeId, postBiomeId, detail);
            }
        }
        if (total == SPARSE_JUNGLE_AUDIT_LOG_LIMIT || total % 50 == 0) {
            logSparseAuditSummary(total);
        }
    }

    private static void logSparseAuditSummary(long total) {
        LOGGER.info("[LAT][SPARSE_AUDIT_SUMMARY] total={} tagPick={} sanitize={} canopy={} warmFallback={} finalSavanna={} latitudeFallback={} logged={} limit={}",
                total,
                SPARSE_TAG_PICK_COUNT.get(),
                SPARSE_SANITIZE_REWRITE_COUNT.get(),
                SPARSE_CANOPY_FALLBACK_COUNT.get(),
                SPARSE_WARM_FALLBACK_COUNT.get(),
                SPARSE_FINAL_SAVANNA_COUNT.get(),
                SPARSE_LATITUDE_FALLBACK_COUNT.get(),
                SPARSE_AUDIT_LOGGED.get(),
                SPARSE_JUNGLE_AUDIT_LOG_LIMIT);
    }

    public static void setWorldSeed(long seed) {
        WORLD_SEED = seed;
        OCEAN_DISTANCE_FIELD = new OceanDistanceField(seed);
        rebuildProvinceAuthority();
    }

    public static void setRadius(int radius) {
        ACTIVE_RADIUS_BLOCKS = radius;
        rebuildProvinceAuthority();
    }

    public static void setActiveRadiusBlocks(int radiusBlocks) {
        ACTIVE_RADIUS_BLOCKS = Math.max(0, radiusBlocks);
        rebuildProvinceAuthority();
    }

    public static int getActiveRadiusBlocks() {
        return ACTIVE_RADIUS_BLOCKS;
    }

    // --- Province authority (scaffolding) ---

    private static volatile ProvinceAuthority PROVINCE_AUTHORITY = null;
    private static final boolean DEBUG_PROVINCE = Boolean.getBoolean("latitude.debugProvince");
    private static final AtomicInteger PROVINCE_DEBUG_COUNT = new AtomicInteger();
    private static final int PROVINCE_DEBUG_LIMIT = Integer.getInteger("latitude.debugProvince.limit", 200);

    private static void rebuildProvinceAuthority() {
        long seed = WORLD_SEED;
        int radius = ACTIVE_RADIUS_BLOCKS;
        if (seed != 0L && radius > 0) {
            PROVINCE_AUTHORITY = new ProvinceAuthority(seed, radius);
            if (DEBUG_PROVINCE) {
                LOGGER.info("[LAT][PROVINCE] rebuilt authority seed={} radius={}", seed, radius);
            }
        }
    }

    /**
     * Returns the current province authority instance, or null if seed/radius
     * have not yet been initialized.
     */
    public static ProvinceAuthority getProvinceAuthority() {
        return PROVINCE_AUTHORITY;
    }

    /**
     * Classifies the given block position into a coarse humidity/moisture province.
     * Returns null if the province authority has not been initialized.
     *
     * <p>This method is the shared entrypoint for worldgen, atlas/headless, and
     * /latdev diagnostics — all paths resolve through the same authority instance.
     *
     * @param blockX world X (blocks)
     * @param blockZ world Z (blocks)
     * @return province classification, or null if not yet initialized
     */
    public static ProvinceAuthority.Province classifyProvince(int blockX, int blockZ) {
        ProvinceAuthority authority = PROVINCE_AUTHORITY;
        if (authority == null) {
            return null;
        }
        ProvinceAuthority.Province province = authority.classify(blockX, blockZ);
        if (DEBUG_PROVINCE) {
            int count = PROVINCE_DEBUG_COUNT.incrementAndGet();
            if (count <= PROVINCE_DEBUG_LIMIT || count % 10000 == 0) {
                LOGGER.info("[LAT][PROVINCE] x={} z={} province={} band={}",
                        blockX, blockZ, province, authority.bandIndex(blockX, blockZ));
            }
        }
        return province;
    }

    public static int oceanDistanceBlocks(int blockX, int blockZ, MultiNoiseUtil.MultiNoiseSampler sampler) {
        if (OCEAN_DISTANCE_FIELD == null) {
            return Integer.MAX_VALUE;
        }
        int dist = OCEAN_DISTANCE_FIELD.oceanDistanceBlocks(blockX, blockZ, sampler);
        if (DEBUG_OCEAN_DIST) {
            long n = MANGROVE_OCEAN_DIST_LOG_COUNT.incrementAndGet();
            if (n <= 50 || n % 50000L == 0L) {
                LOGGER.info("[latdev] oceanDist x={} z={} dist={} blocks", blockX, blockZ, dist);
            }
        }
        return dist;
    }

    public static double uplandRampForY(int blockY) {
        return uplandT(blockY);
    }

    public static String debugSavannaUplandDecision(int blockX, int blockZ, int blockY) {
        return String.format(java.util.Locale.ROOT,
                "savanna upland gate: x=%d z=%d y=%d ruggedThresh=%d hyst=%d",
                blockX, blockZ, blockY, WINDSWEPT_RUGGED_THRESH, WINDSWEPT_RUGGED_HYST);
    }

    public static String debugSavannaRule(MultiNoiseUtil.MultiNoiseSampler sampler,
                                          NoiseChunkGenerator generator,
                                          NoiseConfig noiseConfig,
                                          HeightLimitView heightView,
                                          int blockX, int blockZ) {
        boolean noiseMountain = isMountainLike(sampler, blockX, blockZ);
        PreviewTerrain preview = previewTerrain(generator, noiseConfig, heightView, blockX, blockZ);
        int seaLevel = previewSeaLevel(generator);
        boolean previewHeightHigh = preview.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS);
        boolean previewRuggedHigh = preview.robustDelta >= WINDSWEPT_RUGGED_THRESH;
        boolean mountainLike = noiseMountain && (previewHeightHigh || previewRuggedHigh);
        String incoming = "minecraft:savanna";
        String selected = savannaGateBiomeId(incoming, preview.robustDelta);
        String reason = savannaGateReason(preview.robustDelta);
        return String.format(java.util.Locale.ROOT,
                "mtnLike(noise)=%s previewHeight=%d sea=%d previewRobust=%d mtnLike(final)=%s incoming=%s selected=%s reason=%s",
                noiseMountain, preview.centerHeight, seaLevel, preview.robustDelta, mountainLike,
                incoming, selected, reason);
    }

    // ---- Biome explainer diagnostics ----

    /**
     * Immutable diagnostic snapshot of the signals that drive biome selection at a given position.
     * All fields use sentinel values (Double.NaN, Integer.MIN_VALUE, -1) rather than zero when a
     * signal was unavailable, to avoid confusing "not sampled" with a legitimate low reading.
     */
    public record BiomeDiagnostics(
            int blockX,
            int blockY,
            int blockZ,
            String finalBiomeId,
            double latDeg,
            int bandIndex,
            String bandLabel,
            double humidity,
            double openness,
            double compositionBias,
            double continentalness,
            double erosion,
            double weirdness,
            int oceanDist,
            boolean coastalHint,
            boolean mountainNoiseLike,
            boolean mountainLike,
            boolean polarMountainAuthority,
            boolean terrainPreviewAvailable,
            int centerHeight,
            int robustDelta,
            double uplandT,
            boolean surfaceTruthAvailable,
            String surfaceBlock,
            String surfaceFluid,
            boolean isWaterSurface,
            int surfaceY,
            int seaLevelDelta,
            boolean isSeaLevelSurface,
            boolean isFlatSurface,
            boolean isMountainCandidate,
            boolean isNearOcean,
            String decisionPath,
            boolean enteredLandLogic,
            boolean pickLifecycleAvailable,
            String initialPick,
            boolean poolRejected,
            boolean sanitizeApplied,
            String reasonSummary,
            String summaryLine,
            String driversBlock,
            String province) {
    }

    /**
     * Samples all observable signals at (blockX, blockZ, blockY) and returns a {@link BiomeDiagnostics}
     * that explains the major drivers of biome selection. This method is read-only; it does not
     * run the biome picker or change any selection logic.
     */
    public static BiomeDiagnostics explainBiomeAt(
            String finalBiomeId,
            int blockX, int blockZ, int blockY,
            int borderRadius,
            MultiNoiseUtil.MultiNoiseSampler sampler,
            NoiseChunkGenerator generator,
            NoiseConfig noiseConfig,
            HeightLimitView heightView,
            boolean surfaceTruthAvailable,
            String surfaceBlock,
            String surfaceFluid,
            boolean isWaterSurface,
            int surfaceY) {

        // --- latitude / band ---
        int activeRadius = ACTIVE_RADIUS_BLOCKS;
        boolean overrideDisabled = Boolean.getBoolean("latitude.disableRadiusOverride");
        int effectiveRadius = (!overrideDisabled && activeRadius > 0) ? activeRadius : borderRadius;
        if (effectiveRadius <= 0) effectiveRadius = 1;

        int lat = Math.abs(blockZ);
        double tBase = (double) lat / (double) effectiveRadius;
        double t = applyBoundaryJitter(blockX, blockZ, effectiveRadius, tBase);
        com.example.globe.util.LatitudeBands.Band band = bandForAbsLatFraction(t);
        int bandIndex = latitudeBandIndexWithBlend(blockX, blockZ, effectiveRadius, band, t);
        double latDeg = tBase * 90.0;
        String bandLabel = com.example.globe.util.LatitudeBands.Band.values()[
                Math.max(0, Math.min(4, bandIndex))].displayName();

        // --- tropical/subtropical climate signals (band-conditional) ---
        double humidity = Double.NaN;
        double openness = Double.NaN;
        double compositionBias = Double.NaN;
        if (bandIndex == BAND_SUBTROPICAL) {
            humidity = subtropicalHumidityNoise(blockX, blockZ);
        }
        if (bandIndex <= BAND_SUBTROPICAL) {
            openness = tropicalOpennessNoise(blockX, blockZ);
            compositionBias = tropicalCompositionBias(WORLD_SEED, blockX, blockZ);
        }

        // --- vanilla noise (sampler-conditional) ---
        double continentalness = Double.NaN;
        double erosion = Double.NaN;
        double weirdness = Double.NaN;
        if (sampler != null) {
            int noiseX = blockX >> 2;
            int noiseZ = blockZ >> 2;
            MultiNoiseUtil.NoiseValuePoint p = sampler.sample(noiseX, SURFACE_CLASSIFY_Y >> 2, noiseZ);
            continentalness = MultiNoiseUtil.toFloat(p.continentalnessNoise());
            erosion = MultiNoiseUtil.toFloat(p.erosionNoise());
            weirdness = MultiNoiseUtil.toFloat(p.weirdnessNoise());
        }

        // --- ocean distance ---
        int oceanDist = sampler != null ? oceanDistanceBlocks(blockX, blockZ, sampler) : -1;
        boolean coastalHint = oceanDist >= 0 && oceanDist <= MANGROVE_COASTAL_MAX_BLOCKS;

        // --- terrain preview (generator-conditional) ---
        boolean terrainPreviewAvailable = generator != null && noiseConfig != null && heightView != null;
        int centerHeight = Integer.MIN_VALUE;
        int robustDelta = Integer.MIN_VALUE;
        if (terrainPreviewAvailable) {
            PreviewTerrain preview = previewTerrain(generator, noiseConfig, heightView, blockX, blockZ);
            centerHeight = preview.centerHeight;
            robustDelta = preview.robustDelta;
        }

        // --- mountain signals ---
        boolean mountainNoiseLike = sampler != null && isMountainLike(sampler, blockX, blockZ);
        boolean mountainLike;
        int seaLevel = previewSeaLevel(generator);
        if (terrainPreviewAvailable) {
            mountainLike = mountainNoiseLike
                    && (centerHeight >= seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS / 2
                        || robustDelta >= WINDSWEPT_RUGGED_THRESH);
        } else {
            mountainLike = false;
        }
        boolean polarMountainAuthorityActive = polarMountainAuthority(robustDelta, centerHeight, bandIndex);
        boolean isSeaLevelSurface = surfaceTruthAvailable && surfaceY != Integer.MIN_VALUE && surfaceY == seaLevel;
        int seaLevelDelta = surfaceTruthAvailable && surfaceY != Integer.MIN_VALUE ? (surfaceY - seaLevel) : Integer.MIN_VALUE;
        boolean isFlatSurface = terrainPreviewAvailable && robustDelta == 0;
        boolean isNearOcean = oceanDist >= 0 && oceanDist <= MANGROVE_COASTAL_MAX_BLOCKS;
        boolean isMountainCandidate = mountainNoiseLike || mountainLike;
        boolean activeWaterSurfaceAuthority = oceanDist == 0;
        // Mirror the raised-land veto from pick() so the explain path matches actual worldgen
        if (activeWaterSurfaceAuthority && terrainPreviewAvailable && centerHeight >= seaLevel) {
            activeWaterSurfaceAuthority = false;
        }
        String decisionPath = inferDecisionPath(finalBiomeId, bandIndex, activeWaterSurfaceAuthority);
        boolean enteredLandLogic = "LAND".equals(decisionPath)
                || "POLAR_PICK".equals(decisionPath)
                || "SANITIZE".equals(decisionPath)
                || "FALLBACK".equals(decisionPath);
        boolean pickLifecycleAvailable = false;
        String initialPick = "n/a(explain-only)";
        boolean poolRejected = false;
        boolean sanitizeApplied = false;
        String reasonSummary = buildReasonSummary(
                decisionPath,
                bandLabel,
                finalBiomeId,
                initialPick,
                isWaterSurface,
                isSeaLevelSurface,
                isFlatSurface,
                isNearOcean);

        // --- upland ---
        double uplandT = uplandRampForY(blockY);

        // --- summary line ---
        StringBuilder summary = new StringBuilder();
        switch (bandIndex) {
            case BAND_TROPICAL -> {
                if (!Double.isNaN(openness)) {
                    if (openness >= 0.90) {
                        summary.append("Very open tropical conditions; terrain signals strongly favor open-ground biomes over closed canopy.");
                    } else if (openness >= 0.76) {
                        summary.append("Mixed tropical canopy structure; signals support a blend of open and wooded tropical biomes.");
                    } else {
                        summary.append("Closed tropical conditions; low openness signal favors denser canopy outcomes.");
                    }
                } else {
                    summary.append("Tropical conditions; openness signal unavailable.");
                }
            }
            case BAND_SUBTROPICAL -> {
                if (!Double.isNaN(humidity)) {
                    if (humidity >= 0.40) {
                        summary.append("More moisture-supported subtropical conditions; less arid biome outcomes are favored here.");
                    } else {
                        summary.append("Drier subtropical conditions; arid or semi-arid biome pressure is stronger here.");
                    }
                } else {
                    summary.append("Subtropical conditions; humidity signal unavailable.");
                }
            }
            case BAND_TEMPERATE -> {
                if (mountainLike) {
                    summary.append("Rugged temperate terrain; elevation and relief push this area toward hill, mountain, or wind-exposed outcomes.");
                } else if (terrainPreviewAvailable && robustDelta >= 6) {
                    summary.append("Hilly temperate terrain; moderate relief is influencing the biome outcome.");
                } else {
                    summary.append("Gentler temperate terrain; standard wooded/open temperate outcomes are more likely here.");
                }
            }
            case BAND_SUBPOLAR -> summary.append("Cold shoulder conditions; boreal and subpolar biome pressure is active here.");
            default -> summary.append("Polar conditions dominate here; frozen biomes are strongly favored.");
        }
        if (coastalHint) {
            summary.append(" Coastal proximity is likely influencing this spot.");
        }
        if (mountainLike && bandIndex != BAND_TEMPERATE) {
            summary.append(" Nearby relief is a major factor.");
        }
        String summaryLine = summary.toString();

        // --- drivers block ---
        String naPreview = "n/a(preview)";
        String naBand = "n/a(band)";
        String centerHeightStr = terrainPreviewAvailable ? Integer.toString(centerHeight) : naPreview;
        String robustDeltaStr = terrainPreviewAvailable ? Integer.toString(robustDelta) : naPreview;
        String mountainLikeStr = terrainPreviewAvailable ? Boolean.toString(mountainLike) : naPreview;
        String surfaceYStr = surfaceTruthAvailable && surfaceY != Integer.MIN_VALUE ? Integer.toString(surfaceY) : "n/a(surface)";
        String seaLevelDeltaStr = surfaceTruthAvailable && surfaceY != Integer.MIN_VALUE ? Integer.toString(seaLevelDelta) : "n/a(surface)";
        String seaLevelSurfaceStr = surfaceTruthAvailable ? Boolean.toString(isSeaLevelSurface) : "n/a(surface)";
        String waterSurfaceStr = surfaceTruthAvailable ? Boolean.toString(isWaterSurface) : "n/a(surface)";
        String surfaceBlockStr = surfaceTruthAvailable ? safeString(surfaceBlock, "minecraft:air") : "n/a(surface)";
        String surfaceFluidStr = surfaceTruthAvailable ? safeString(surfaceFluid, "minecraft:empty") : "n/a(surface)";
        String humidityStr = !Double.isNaN(humidity) ? String.format(java.util.Locale.ROOT, "%.3f", humidity) : naBand;
        String opennessStr = !Double.isNaN(openness) ? String.format(java.util.Locale.ROOT, "%.3f", openness) : naBand;
        String compositionBiasStr = !Double.isNaN(compositionBias) ? String.format(java.util.Locale.ROOT, "%.3f", compositionBias) : naBand;
        String contStr = !Double.isNaN(continentalness) ? String.format(java.util.Locale.ROOT, "%.3f", continentalness) : "n/a";
        String eroStr = !Double.isNaN(erosion) ? String.format(java.util.Locale.ROOT, "%.3f", erosion) : "n/a";
        String weirdStr = !Double.isNaN(weirdness) ? String.format(java.util.Locale.ROOT, "%.3f", weirdness) : "n/a";
        String oceanDistStr = oceanDist >= 0 ? Integer.toString(oceanDist) : "n/a";
        ProvinceAuthority.Province provinceResult = classifyProvince(blockX, blockZ);
        String provinceStr = provinceResult != null ? provinceResult.name() : "n/a(not-initialized)";
        String driversBlock = String.format(java.util.Locale.ROOT,
                "  pos=%d,%d,%d  finalBiome=%s%n"
                + "  latDeg=%.2f  band=%s(idx=%d)%n"
                + "  oceanDist=%s  coastalHint=%s(<=MANGROVE_COASTAL_MAX_BLOCKS)%n"
                + "  cont=%s  ero=%s  weird=%s%n"
                + "  terrainPreview=%s  centerHeight=%s  robustDelta=%s%n"
                + "  mountainNoiseLike=%s  mountainLike=%s%n"
                + "  polarMountainAuthority=%s%n"
                + "  surfaceTruthAvailable=%s  surfaceBlock=%s  surfaceFluid=%s%n"
                + "  isWaterSurface=%s  surfaceY=%s  seaLevelDelta=%s%n"
                + "  isSeaLevelSurface=%s  isFlatSurface=%s  isMountainCandidate=%s  isNearOcean=%s%n"
                + "  decisionPath=%s  enteredLandLogic=%s%n"
                + "  initialPick=%s  poolRejected=%s  sanitizeApplied=%s  finalBiome=%s%n"
                + "  pickLifecycleAvailable=%s%n"
                + "  reasonSummary=%s%n"
                + "  humidity=%s  openness=%s  compositionBias=%s%n"
                + "  uplandT=%.3f%n"
                + "  province=%s",
                blockX, blockY, blockZ, finalBiomeId,
                latDeg, bandLabel, bandIndex,
                oceanDistStr, coastalHint,
                contStr, eroStr, weirdStr,
                terrainPreviewAvailable ? "available" : "unavailable", centerHeightStr, robustDeltaStr,
                mountainNoiseLike, mountainLikeStr,
                polarMountainAuthorityActive,
                surfaceTruthAvailable, surfaceBlockStr, surfaceFluidStr,
                waterSurfaceStr, surfaceYStr, seaLevelDeltaStr,
                seaLevelSurfaceStr, isFlatSurface, isMountainCandidate, isNearOcean,
                decisionPath, enteredLandLogic,
                initialPick, poolRejected, sanitizeApplied, finalBiomeId,
                pickLifecycleAvailable,
                reasonSummary,
                humidityStr, opennessStr, compositionBiasStr,
                uplandT,
                provinceStr);

        return new BiomeDiagnostics(
                blockX, blockY, blockZ,
                finalBiomeId,
                latDeg,
                bandIndex,
                bandLabel,
                humidity,
                openness,
                compositionBias,
                continentalness,
                erosion,
                weirdness,
                oceanDist,
                coastalHint,
                mountainNoiseLike,
                mountainLike,
                polarMountainAuthorityActive,
                terrainPreviewAvailable,
                centerHeight,
                robustDelta,
                uplandT,
                surfaceTruthAvailable,
                surfaceBlockStr,
                surfaceFluidStr,
                isWaterSurface,
                surfaceY,
                seaLevelDelta,
                isSeaLevelSurface,
                isFlatSurface,
                isMountainCandidate,
                isNearOcean,
                decisionPath,
                enteredLandLogic,
                pickLifecycleAvailable,
                initialPick,
                poolRejected,
                sanitizeApplied,
                reasonSummary,
                summaryLine,
                driversBlock,
                provinceStr);
    }

    private static String inferDecisionPath(String finalBiomeId, int bandIndex, boolean activeWaterSurfaceAuthority) {
        if (activeWaterSurfaceAuthority) {
            return "ACTIVE_OCEAN";
        }
        if (isBeachId(finalBiomeId)) {
            return "BEACH";
        }
        if (isRiverId(finalBiomeId)) {
            return "RIVER";
        }
        if (isOceanId(finalBiomeId)) {
            return "PASSIVE_OCEAN";
        }
        if (bandIndex >= BAND_POLAR) {
            return "POLAR_PICK";
        }
        return "LAND";
    }

    private static String buildReasonSummary(String decisionPath,
                                             String bandLabel,
                                             String finalBiomeId,
                                             String initialPick,
                                             boolean isWaterSurface,
                                             boolean isSeaLevelSurface,
                                             boolean isFlatSurface,
                                             boolean isNearOcean) {
        return String.format(java.util.Locale.ROOT,
                "Entered %s -> %s -> waterSurface=%s seaLevel=%s flat=%s nearOcean=%s -> initial %s -> final %s",
                decisionPath,
                bandLabel,
                isWaterSurface,
                isSeaLevelSurface,
                isFlatSurface,
                isNearOcean,
                initialPick,
                finalBiomeId);
    }

    private static boolean isBeachId(String biomeId) {
        return biomeId != null && (biomeId.contains("beach") || biomeId.contains("shore"));
    }

    private static boolean isRiverId(String biomeId) {
        return biomeId != null && biomeId.contains("river");
    }

    private static boolean isOceanId(String biomeId) {
        return biomeId != null && biomeId.contains("ocean");
    }

    private static String safeString(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    // ---- end biome explainer diagnostics ----

    private static PreviewTerrain previewTerrain(NoiseChunkGenerator generator, NoiseConfig noiseConfig, HeightLimitView heightView,
                                                 int blockX, int blockZ) {
        if (generator == null || noiseConfig == null || heightView == null) {
            return new PreviewTerrain(0, 0);
        }
        int ring = SAVANNA_RUGGED_RING_BLOCKS;
        int sampleX = blockX & ~3;
        int sampleZ = blockZ & ~3;
        int c = previewHeight(generator, noiseConfig, heightView, sampleX, sampleZ);
        int n = previewHeight(generator, noiseConfig, heightView, sampleX, sampleZ - ring);
        int s = previewHeight(generator, noiseConfig, heightView, sampleX, sampleZ + ring);
        int e = previewHeight(generator, noiseConfig, heightView, sampleX + ring, sampleZ);
        int w = previewHeight(generator, noiseConfig, heightView, sampleX - ring, sampleZ);
        int ne = previewHeight(generator, noiseConfig, heightView, sampleX + ring, sampleZ - ring);
        int nw = previewHeight(generator, noiseConfig, heightView, sampleX - ring, sampleZ - ring);
        int se = previewHeight(generator, noiseConfig, heightView, sampleX + ring, sampleZ + ring);
        int sw = previewHeight(generator, noiseConfig, heightView, sampleX - ring, sampleZ + ring);
        int[] ys = {n, s, e, w, ne, nw, se, sw};
        int[] deltas = new int[ys.length];
        for (int i = 0; i < ys.length; i++) {
            deltas[i] = Math.abs(ys[i] - c);
        }
        java.util.Arrays.sort(deltas);
        return new PreviewTerrain(c, deltas[deltas.length - 2]);
    }

    /**
     * Public accessor used by the atlas exporter.
     * Returns the robustDelta (second-highest neighbour height delta) without loading chunks.
     * Returns 0 if any required input is null.
     */
    public static int previewRobustDelta(
            net.minecraft.world.gen.chunk.NoiseChunkGenerator generator,
            NoiseConfig noiseConfig,
            HeightLimitView heightView,
            int blockX, int blockZ) {
        if (generator == null || noiseConfig == null || heightView == null) return 0;
        return previewTerrain(generator, noiseConfig, heightView, blockX, blockZ).robustDelta();
    }

    private static boolean shouldSkipPreviewTerrain(String callerContext) {
        if (callerContext == null) {
            return false;
        }
        String normalized = callerContext.trim().toUpperCase(java.util.Locale.ROOT);
        if ("BIOME_PNG".equals(normalized)
                || "SOURCE".equals(normalized)
                || "ATLAS_SAMPLER".equals(normalized)) {
            return Boolean.parseBoolean(System.getProperty("latitude.skipPreviewHeightForBiomePng", "true"));
        }
        if ("MIXIN".equals(normalized) || "CAVE_CLAMP".equals(normalized)) {
            return Boolean.parseBoolean(System.getProperty("latitude.skipPreviewHeightForWorldgen", "true"));
        }
        return false;
    }

    /**
     * True for caller contexts that never provide noiseConfig/heightView:
     * atlas export ("SOURCE"), headless sampler tools ("ATLAS_SAMPLER").
     * Used to gate polar terrain authority fallback to noise when real terrain probes are absent.
     * Proven (call-site audit): only SOURCE and ATLAS_SAMPLER callers pass null for
     * noiseConfig/heightView; MIXIN and CAVE_CLAMP always pass real inputs.
     */
    private static boolean isAtlasHeadlessContext(String callerContext) {
        if (callerContext == null) return false;
        String n = callerContext.trim().toUpperCase(java.util.Locale.ROOT);
        return "SOURCE".equals(n) || "ATLAS_SAMPLER".equals(n);
    }

    /** Coordinate gate for polar cap live trace — matches x=0/z=9702, x=14/z=9668, x=2133/z=9722 (±4 blocks). */
    private static boolean isPolarCapTraceCoord(int blockX, int blockZ) {
        int az = Math.abs(blockZ);
        return (Math.abs(blockX) <= 4          && Math.abs(az - 9702) <= 4)
            || (Math.abs(blockX - 14) <= 4     && Math.abs(az - 9668) <= 4)
            || (Math.abs(blockX - 2133) <= 4   && Math.abs(az - 9722) <= 4);
    }

    private static PreviewTerrain syntheticPreviewTerrain(boolean mountainNoiseLike, NoiseChunkGenerator generator) {
        int seaLevel = previewSeaLevel(generator);
        int centerHeight = mountainNoiseLike ? (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS + 1) : (seaLevel - 1);
        int robustDelta = mountainNoiseLike
                ? (WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST)
                : 0; // flat/unknown-safe: do not fabricate raised-land roughness for skipped-preview ocean shelf
        return new PreviewTerrain(centerHeight, robustDelta);
    }

    private static int previewHeight(NoiseChunkGenerator generator, NoiseConfig noiseConfig, HeightLimitView heightView,
                                     int blockX, int blockZ) {
        long chunkKey = net.minecraft.util.math.ChunkPos.toLong(blockX >> 4, blockZ >> 4);
        long cachedChunk = PREVIEW_HEIGHT_CACHE_CHUNK.get();
        Long2IntOpenHashMap cache = PREVIEW_HEIGHT_CACHE.get();
        if (chunkKey != cachedChunk) {
            cache.clear();
            PREVIEW_HEIGHT_CACHE_CHUNK.set(chunkKey);
        }
        long key = (((long) blockX) << 32) ^ (blockZ & 0xffffffffL);
        int cached = cache.getOrDefault(key, Integer.MIN_VALUE);
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }
        int value = generator.getHeight(blockX, blockZ, Heightmap.Type.WORLD_SURFACE_WG, heightView, noiseConfig);
        cache.put(key, value);
        return value;
    }

    private static int previewSeaLevel(NoiseChunkGenerator generator) {
        return generator == null ? 63 : generator.getSeaLevel();
    }

    private static int surfaceDecisionY(NoiseChunkGenerator generator,
                                        NoiseConfig noiseConfig,
                                        HeightLimitView heightView,
                                        int blockX,
                                        int blockZ) {
        if (generator == null || noiseConfig == null || heightView == null) {
            // Keep column decisions stable when we cannot resolve a real top surface.
            return SURFACE_CLASSIFY_Y;
        }
        int sampleX = blockX & ~3;
        int sampleZ = blockZ & ~3;
        return previewHeight(generator, noiseConfig, heightView, sampleX, sampleZ);
    }

    private static String savannaIncomingBiomeId(RegistryEntry<Biome> entry) {
        if (isBiomeId(entry, "minecraft:windswept_savanna")) {
            return "minecraft:windswept_savanna";
        }
        if (isBiomeId(entry, "minecraft:savanna_plateau")) {
            return "minecraft:savanna_plateau";
        }
        return "minecraft:savanna";
    }

    private static String savannaGateBiomeId(String incomingBiomeId, int robustDelta) {
        if (robustDelta >= WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST) {
            return "minecraft:windswept_savanna";
        }
        if (robustDelta < WINDSWEPT_RUGGED_THRESH) {
            return "minecraft:savanna";
        }
        return incomingBiomeId;
    }

    private static String savannaGateReason(int robustDelta) {
        if (robustDelta >= WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST) {
            return "robust_high";
        }
        if (robustDelta < WINDSWEPT_RUGGED_THRESH) {
            return "robust_low";
        }
        return "deadband_keep";
    }

    private static boolean isWarmLandWindsweptBiome(RegistryEntry<Biome> biome) {
        return isBiomeId(biome, "minecraft:windswept_hills")
                || isBiomeId(biome, "minecraft:windswept_forest")
                || isBiomeId(biome, "minecraft:windswept_gravelly_hills");
    }

    private static String warmWindsweptTraceBiomeId(RegistryEntry<Biome> biome) {
        return biome == null ? "null" : biomeId(biome);
    }

    private static boolean sameBiomeId(RegistryEntry<Biome> a, RegistryEntry<Biome> b) {
        return java.util.Objects.equals(warmWindsweptTraceBiomeId(a), warmWindsweptTraceBiomeId(b));
    }

    private record WarmPoolMembershipSnapshot(int blockX,
                                              int blockZ,
                                              int bandIndex,
                                              String incomingBeforeEnforce,
                                              String outgoingAfterEnforce,
                                              String preFilterPoolIds,
                                              String postFilterPoolIds,
                                              boolean mountainFamilyRemovedByFilter,
                                              boolean coldFamilyRemovedByFilter,
                                              boolean postFilterContainsTaigaFamily,
                                              boolean postFilterContainsWindsweptFamily) {
    }

    private static boolean isWarmPoolMembershipTargetBiome(RegistryEntry<Biome> biome) {
        return isTaigaFamilyBiome(biome);
    }

    private static boolean poolContainsMountainFamily(List<RegistryEntry<Biome>> pool) {
        for (RegistryEntry<Biome> entry : pool) {
            if (isTemperateMountainFamilyBiome(entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean poolContainsColdFamily(List<RegistryEntry<Biome>> pool) {
        for (RegistryEntry<Biome> entry : pool) {
            if (isTaigaFamilyBiome(entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean poolContainsWindsweptFamily(List<RegistryEntry<Biome>> pool) {
        for (RegistryEntry<Biome> entry : pool) {
            if (isWarmLandWindsweptBiome(entry)) {
                return true;
            }
        }
        return false;
    }

    private static String poolBiomeIds(List<RegistryEntry<Biome>> pool) {
        if (pool == null || pool.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < pool.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(biomeId(pool.get(i)));
        }
        builder.append(']');
        return builder.toString();
    }

    private static void stashWarmPoolMembershipSnapshot(int blockX,
                                                        int blockZ,
                                                        int bandIndex,
                                                        RegistryEntry<Biome> incomingBeforeEnforce,
                                                        RegistryEntry<Biome> outgoingAfterEnforce,
                                                        List<RegistryEntry<Biome>> preFilterPool,
                                                        List<RegistryEntry<Biome>> postFilterPool) {
        if (!DEBUG_WARM_POOL_MEMBERSHIP) {
            return;
        }
        boolean preHasMountainFamily = poolContainsMountainFamily(preFilterPool);
        boolean postHasMountainFamily = poolContainsMountainFamily(postFilterPool);
        boolean preHasColdFamily = poolContainsColdFamily(preFilterPool);
        boolean postHasColdFamily = poolContainsColdFamily(postFilterPool);

        LAST_WARM_POOL_MEMBERSHIP_SNAPSHOT.set(new WarmPoolMembershipSnapshot(
                blockX,
                blockZ,
                bandIndex,
                warmWindsweptTraceBiomeId(incomingBeforeEnforce),
                warmWindsweptTraceBiomeId(outgoingAfterEnforce),
                poolBiomeIds(preFilterPool),
                poolBiomeIds(postFilterPool),
                preHasMountainFamily && !postHasMountainFamily,
                preHasColdFamily && !postHasColdFamily,
                postHasColdFamily,
                poolContainsWindsweptFamily(postFilterPool)));
    }

    private static void logWarmPoolMembershipFinalPoint(String stage,
                                                        RegistryEntry<Biome> base,
                                                        int blockX,
                                                        int blockZ,
                                                        double t,
                                                        int landBandIndex,
                                                        boolean mountainLikeBeforeFinalTruth,
                                                        boolean mountainLikeAfterFinalTruth,
                                                        RegistryEntry<Biome> beforeSanitize,
                                                        RegistryEntry<Biome> afterSanitize,
                                                        RegistryEntry<Biome> beforeEnforceLandBandPool,
                                                        RegistryEntry<Biome> afterEnforceLandBandPool,
                                                        boolean swampCandidateAfterEnforce,
                                                        boolean swampValidationFailed,
                                                        boolean swampFallbackCalled,
                                                        RegistryEntry<Biome> swampFallbackReturned,
                                                        RegistryEntry<Biome> finalBiome) {
        if (!DEBUG_WARM_POOL_MEMBERSHIP || !isWarmPoolMembershipTargetBiome(finalBiome)) {
            return;
        }
        WarmPoolMembershipSnapshot snapshot = LAST_WARM_POOL_MEMBERSHIP_SNAPSHOT.get();
        LAST_WARM_POOL_MEMBERSHIP_SNAPSHOT.remove();
        boolean snapshotMatches = snapshot != null
                && snapshot.blockX == blockX
                && snapshot.blockZ == blockZ
                && snapshot.bandIndex == landBandIndex;
        String poolIncoming = snapshotMatches ? snapshot.incomingBeforeEnforce : "snapshot-missing";
        String poolOutgoing = snapshotMatches ? snapshot.outgoingAfterEnforce : "snapshot-missing";
        String preFilterPoolIds = snapshotMatches ? snapshot.preFilterPoolIds : "snapshot-missing";
        String postFilterPoolIds = snapshotMatches ? snapshot.postFilterPoolIds : "snapshot-missing";
        boolean mountainFamilyRemovedByFilter = snapshotMatches && snapshot.mountainFamilyRemovedByFilter;
        boolean coldFamilyRemovedByFilter = snapshotMatches && snapshot.coldFamilyRemovedByFilter;
        boolean postFilterHasTaigaFamily = snapshotMatches && snapshot.postFilterContainsTaigaFamily;
        boolean postFilterHasWindsweptFamily = snapshotMatches && snapshot.postFilterContainsWindsweptFamily;
        boolean sanitizeChanged = !sameBiomeId(beforeSanitize, afterSanitize);
        double latDeg = clamp(t, 0.0, 1.0) * 90.0;
        ProvinceAuthority.Province warmProvince = warmProvinceClass(blockX, blockZ, landBandIndex);
        String humiditySummary = landBandIndex == BAND_SUBTROPICAL
                ? String.format(java.util.Locale.ROOT, "%.3f", subtropicalHumidityNoise(blockX, blockZ))
                : "n/a";

        LOGGER.warn("[LAT][WARM_POOL_MEMBERSHIP] stage={} x={} z={} latDeg={} bandIndex={} province={} subtropicalHumidity={} mtnLikeBefore={} mtnLikeAfter={} beforeSanitize={} afterSanitize={} sanitizeChanged={} beforeEnforce={} afterEnforce={} swampCandidateAfterEnforce={} swampValidationFailed={} swampFallbackCalled={} swampFallbackReturned={} finalBiome={} selectionPath={} poolSnapshotMatch={} poolIncoming={} poolOutgoing={} preFilterPool={} postFilterPool={} mountainFamilyRemovedByFilter={} coldFamilyRemovedByFilter={} postFilterHasTaigaFamily={} postFilterHasWindsweptFamily={}",
                stage,
                blockX,
                blockZ,
                String.format(java.util.Locale.ROOT, "%.2f", latDeg),
                landBandIndex,
                warmProvince,
                humiditySummary,
                mountainLikeBeforeFinalTruth,
                mountainLikeAfterFinalTruth,
                warmWindsweptTraceBiomeId(beforeSanitize),
                warmWindsweptTraceBiomeId(afterSanitize),
                sanitizeChanged,
                warmWindsweptTraceBiomeId(beforeEnforceLandBandPool),
                warmWindsweptTraceBiomeId(afterEnforceLandBandPool),
                swampCandidateAfterEnforce,
                swampValidationFailed,
                swampFallbackCalled,
                warmWindsweptTraceBiomeId(swampFallbackReturned),
                warmWindsweptTraceBiomeId(finalBiome),
                selectionPathForTrace(base, finalBiome),
                snapshotMatches,
                poolIncoming,
                poolOutgoing,
                preFilterPoolIds,
                postFilterPoolIds,
                mountainFamilyRemovedByFilter,
                coldFamilyRemovedByFilter,
                postFilterHasTaigaFamily,
                postFilterHasWindsweptFamily);
    }

    private static void logSubtropicalSwampTrace(int blockX,
                                                 int blockZ,
                                                 int landBandIndex,
                                                 boolean mountainLike,
                                                 String source,
                                                 RegistryEntry<Biome> chosenBiome,
                                                 RegistryEntry<Biome> postSanitize,
                                                 RegistryEntry<Biome> preEnforce,
                                                 RegistryEntry<Biome> postEnforce,
                                                 RegistryEntry<Biome> finalBiome,
                                                 Boolean evaluateAllow,
                                                 Boolean postEnforceAllow) {
        if (!DEBUG_SUBTROPICAL_SWAMP_SOURCE_TRACE) {
            return;
        }
        if (landBandIndex != BAND_SUBTROPICAL || mountainLike) {
            return;
        }
        boolean anySwamp = isSwampCandidate(chosenBiome)
                || isSwampCandidate(postSanitize)
                || isSwampCandidate(preEnforce)
                || isSwampCandidate(postEnforce)
                || isSwampCandidate(finalBiome);
        if (!anySwamp) {
            return;
        }
        LOGGER.info("[LAT][SUBTROPICAL_SWAMP_TRACE] x={} z={} source={} chosenBiome={} postSanitize={} preEnforce={} postEnforce={} finalBiome={} evaluateAllow={} postEnforceAllow={}",
                blockX,
                blockZ,
                source == null ? "unknown" : source,
                biomeId(chosenBiome),
                biomeId(postSanitize),
                biomeId(preEnforce),
                biomeId(postEnforce),
                biomeId(finalBiome),
                evaluateAllow == null ? "n/a" : evaluateAllow.toString(),
                postEnforceAllow == null ? "n/a" : postEnforceAllow.toString());
    }

    private static void logWarmWindsweptLatePath(String stage,
                                                 RegistryEntry<Biome> base,
                                                 int blockX,
                                                 int blockZ,
                                                 int landBandIndex,
                                                 boolean mountainLikeBeforeFinalTruth,
                                                 boolean mountainLikeAfterFinalTruth,
                                                 boolean temperateMountainRewriteRan,
                                                 RegistryEntry<Biome> beforeSanitize,
                                                 RegistryEntry<Biome> afterSanitize,
                                                 RegistryEntry<Biome> beforeEnforceLandBandPool,
                                                 RegistryEntry<Biome> afterEnforceLandBandPool,
                                                 boolean finalSavannaClampRan,
                                                 RegistryEntry<Biome> beforeFinalSavannaClamp,
                                                 RegistryEntry<Biome> afterFinalSavannaClamp,
                                                 RegistryEntry<Biome> finalBiome) {
        if (!DEBUG_WARM_WINDSWEPT_LATE_PATH || !isWarmLandWindsweptBiome(finalBiome)) {
            return;
        }
        boolean sanitizeChanged = !sameBiomeId(beforeSanitize, afterSanitize);
        boolean enforceChanged = !sameBiomeId(beforeEnforceLandBandPool, afterEnforceLandBandPool);
        boolean finalClampChanged = finalSavannaClampRan && !sameBiomeId(beforeFinalSavannaClamp, afterFinalSavannaClamp);
        LOGGER.warn("[LAT][WARM_WINDSWEPT_LATE] stage={} x={} z={} bandIndex={} mtnLikeBefore={} mtnLikeAfter={} temperateMountainRewriteRan={} beforeSanitize={} afterSanitize={} sanitizeChanged={} beforeEnforce={} afterEnforce={} enforceChanged={} finalClampRan={} beforeFinalClamp={} afterFinalClamp={} finalClampChanged={} finalBiome={} selectionPath={}",
                stage,
                blockX,
                blockZ,
                landBandIndex,
                mountainLikeBeforeFinalTruth,
                mountainLikeAfterFinalTruth,
                temperateMountainRewriteRan,
                warmWindsweptTraceBiomeId(beforeSanitize),
                warmWindsweptTraceBiomeId(afterSanitize),
                sanitizeChanged,
                warmWindsweptTraceBiomeId(beforeEnforceLandBandPool),
                warmWindsweptTraceBiomeId(afterEnforceLandBandPool),
                enforceChanged,
                finalSavannaClampRan,
                warmWindsweptTraceBiomeId(beforeFinalSavannaClamp),
                warmWindsweptTraceBiomeId(afterFinalSavannaClamp),
                finalClampChanged,
                warmWindsweptTraceBiomeId(finalBiome),
                selectionPathForTrace(base, finalBiome));
    }

    private static void logWetlandAudit(String stage,
                                        String callerContext,
                                        RegistryEntry<Biome> base,
                                        int blockX,
                                        int blockZ,
                                        int landBandIndex,
                                        double t,
                                        double latDeg,
                                        boolean mountainLike,
                                        int robustDelta,
                                        int oceanDistance,
                                        boolean skipPreview,
                                        RegistryEntry<Biome> preEnforce,
                                        RegistryEntry<Biome> postEnforce,
                                        RegistryEntry<Biome> sanitizeResult,
                                        RegistryEntry<Biome> finalBiome,
                                        boolean swampFallbackCalled,
                                        RegistryEntry<Biome> swampFallbackReturned,
                                        boolean mangroveFallbackCalled,
                                        RegistryEntry<Biome> mangroveFallbackReturned) {
        if (!DEBUG_WETLANDS) {
            return;
        }
        if (!(isBiomeId(finalBiome, "minecraft:swamp") || isBiomeId(finalBiome, "minecraft:mangrove_swamp"))) {
            return;
        }
        LOGGER.warn("[WETLAND_AUDIT] stage={} caller={} x={} z={} landBandIndex={} latDeg={} t={} preEnforce={} postEnforce={} sanitize={} finalBiome={} decisionPath={} mountainLike={} robustDelta={} oceanDist={} swampFallbackCalled={} swampFallbackReturned={} mangroveFallbackCalled={} mangroveFallbackReturned={} skipPreview={} atlasFastPathSkipPreview={}",
                stage,
                callerContext,
                blockX,
                blockZ,
                landBandIndex,
                String.format(java.util.Locale.ROOT, "%.2f", latDeg),
                String.format(java.util.Locale.ROOT, "%.4f", t),
                biomeId(preEnforce),
                biomeId(postEnforce),
                biomeId(sanitizeResult),
                biomeId(finalBiome),
                selectionPathForTrace(base, finalBiome),
                mountainLike,
                robustDelta,
                oceanDistance,
                swampFallbackCalled,
                biomeId(swampFallbackReturned),
                mangroveFallbackCalled,
                biomeId(mangroveFallbackReturned),
                skipPreview,
                skipPreview && isAtlasHeadlessContext(callerContext));
    }

    private static String mangroveOrigin(boolean registryPath, boolean collectionPath) {
        if (registryPath) return "registry";
        if (collectionPath) return "collection";
        return "unknown";
    }

    private static String savannaTierByY(int blockY) {
        if (blockY >= WINDSWEPT_MIN_Y) {
            return "minecraft:windswept_savanna";
        }
        if (blockY >= SAVANNA_PLATEAU_MIN_Y) {
            return "minecraft:savanna_plateau";
        }
        return "minecraft:savanna";
    }

    private static boolean preserveSavannaPlateauAtSanitize(RegistryEntry<Biome> entry, int blockX, int blockZ) {
        if (!isBiomeId(entry, "minecraft:savanna_plateau")) {
            return false;
        }
        double localUpland = ValueNoise2D.sampleBlocks(WORLD_SEED ^ UPLAND_POOL_SALT, blockX, blockZ, UPLAND_SCALE_BLOCKS);
        return localUpland >= 0.58;
    }

    private static void incrementSavannaIncomingCounter(String biomeId) {
        if ("minecraft:savanna".equals(biomeId)) {
            SAVANNA_GATE_IN_SAVANNA.incrementAndGet();
        } else if ("minecraft:savanna_plateau".equals(biomeId)) {
            SAVANNA_GATE_IN_PLATEAU.incrementAndGet();
        } else if ("minecraft:windswept_savanna".equals(biomeId)) {
            SAVANNA_GATE_IN_WINDSWEPT.incrementAndGet();
        } else {
            SAVANNA_GATE_IN_OTHER.incrementAndGet();
        }
    }

    private static void incrementSavannaOutgoingCounter(String biomeId) {
        if ("minecraft:savanna".equals(biomeId)) {
            SAVANNA_GATE_OUT_SAVANNA.incrementAndGet();
        } else if ("minecraft:windswept_savanna".equals(biomeId)) {
            SAVANNA_GATE_OUT_WINDSWEPT.incrementAndGet();
        } else {
            SAVANNA_GATE_OUT_OTHER.incrementAndGet();
        }
    }

    private static void incrementSavannaReasonCounter(String reason) {
        if ("robust_high".equals(reason)) {
            SAVANNA_GATE_REASON_HIGH.incrementAndGet();
        } else if ("robust_low".equals(reason)) {
            SAVANNA_GATE_REASON_LOW.incrementAndGet();
        } else {
            SAVANNA_GATE_REASON_DEADBAND.incrementAndGet();
        }
    }

    private static void logSavannaGateCounters(String incomingBiomeId, String outgoingBiomeId, String reason) {
        int total = SAVANNA_GATE_TOTAL.incrementAndGet();
        incrementSavannaIncomingCounter(incomingBiomeId);
        incrementSavannaOutgoingCounter(outgoingBiomeId);
        incrementSavannaReasonCounter(reason);
        if (SAVANNA_GATE_LOG_EVERY > 0 && (total % SAVANNA_GATE_LOG_EVERY == 0)) {
            LOGGER.info("[Latitude][SavannaGate] total={} in[savanna={},plateau={},windswept={},other={}] out[savanna={},windswept={},other={}] reason[robust_high={},robust_low={},deadband_keep={}] last[in={},out={},reason={}]",
                    total,
                    SAVANNA_GATE_IN_SAVANNA.get(),
                    SAVANNA_GATE_IN_PLATEAU.get(),
                    SAVANNA_GATE_IN_WINDSWEPT.get(),
                    SAVANNA_GATE_IN_OTHER.get(),
                    SAVANNA_GATE_OUT_SAVANNA.get(),
                    SAVANNA_GATE_OUT_WINDSWEPT.get(),
                    SAVANNA_GATE_OUT_OTHER.get(),
                    SAVANNA_GATE_REASON_HIGH.get(),
                    SAVANNA_GATE_REASON_LOW.get(),
                    SAVANNA_GATE_REASON_DEADBAND.get(),
                    incomingBiomeId,
                    outgoingBiomeId,
                    reason);
        }
    }

    private static void logSavannaGateAuditSummary(long total) {
        LOGGER.info("[LAT][SAV_GATE_AUDIT_SUMMARY] total={} enter={} notSavanna={} pass={} failLow={} failDeadband={} selectedWsav={} upland={} realPreview={} previewMissing={}",
                total,
                SAVANNA_AUDIT_ENTER.get(),
                SAVANNA_AUDIT_NOT_SAVANNA.get(),
                SAVANNA_AUDIT_PASS.get(),
                SAVANNA_AUDIT_FAIL_LOW.get(),
                SAVANNA_AUDIT_FAIL_DEADBAND.get(),
                SAVANNA_AUDIT_SELECTED.get(),
                SAVANNA_AUDIT_UPLAND.get(),
                SAVANNA_AUDIT_REAL_PREVIEW.get(),
                SAVANNA_AUDIT_PREVIEW_MISSING.get());
    }

    private static void auditSavannaGate(int robustDelta,
                                         boolean upland,
                                         boolean savannaFamily,
                                         String incomingBiomeId,
                                         String outgoingBiomeId) {
        if (!DEBUG_SAVANNA_GATE_AUDIT) {
            return;
        }
        long total = SAVANNA_AUDIT_TOTAL.incrementAndGet();
        if (savannaFamily) {
            SAVANNA_AUDIT_ENTER.incrementAndGet();
            if (robustDelta < WINDSWEPT_RUGGED_THRESH) {
                SAVANNA_AUDIT_FAIL_LOW.incrementAndGet();
            } else if (robustDelta < WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST) {
                SAVANNA_AUDIT_FAIL_DEADBAND.incrementAndGet();
            } else {
                SAVANNA_AUDIT_PASS.incrementAndGet();
            }
            if (upland) {
                SAVANNA_AUDIT_UPLAND.incrementAndGet();
            }
        } else {
            SAVANNA_AUDIT_NOT_SAVANNA.incrementAndGet();
        }
        if ("minecraft:windswept_savanna".equals(outgoingBiomeId)) {
            SAVANNA_AUDIT_SELECTED.incrementAndGet();
        }
        long logged = SAVANNA_AUDIT_LOGGED.get();
        if (logged < SAVANNA_GATE_AUDIT_LOG_LIMIT) {
            if (SAVANNA_AUDIT_LOGGED.incrementAndGet() <= SAVANNA_GATE_AUDIT_LOG_LIMIT) {
                LOGGER.info("[LAT][SAV_GATE_AUDIT] robust={} upland={} savFamily={} incoming={} outgoing={}",
                        robustDelta, upland, savannaFamily, incomingBiomeId, outgoingBiomeId);
            }
        }
        if (total == SAVANNA_GATE_AUDIT_LOG_LIMIT
                || (SAVANNA_GATE_AUDIT_SUMMARY_EVERY > 0 && (total % SAVANNA_GATE_AUDIT_SUMMARY_EVERY) == 0)) {
            logSavannaGateAuditSummary(total);
        }
    }

    private static void logSavannaSpawnGateDebug(String callerContext,
                                                 int blockX,
                                                 int blockZ,
                                                 String incomingBiomeId,
                                                 String outgoingBiomeId,
                                                 int landBandIndex,
                                                 String reason) {
        if (!DEBUG_SAVANNA_SPAWN_GATE) {
            return;
        }
        int total = SAVANNA_GATE_DEBUG_TOTAL.incrementAndGet();
        boolean sourceCtx = callerContext != null && "SOURCE".equalsIgnoreCase(callerContext);
        if (sourceCtx) {
            SAVANNA_GATE_DEBUG_SOURCE.incrementAndGet();
        }
        long key = ((long) blockX << 32) ^ (blockZ & 0xffffffffL);
        int repeats = SAVANNA_GATE_SEEN.getOrDefault(key, 0) + 1;
        SAVANNA_GATE_SEEN.put(key, repeats);
        if (total <= 25 || total % 2000 == 0) {
            LOGGER.info("[Latitude][SpawnGate] total={} source={} repeat={} x={} z={} band={} incoming={} outgoing={} reason={} context={}",
                    total,
                    SAVANNA_GATE_DEBUG_SOURCE.get(),
                    repeats,
                    blockX,
                    blockZ,
                    landBandIndex,
                    incomingBiomeId,
                    outgoingBiomeId,
                    reason,
                    callerContext);
        }
    }

    private static RegistryEntry<Biome> applySavannaWindsweptGate(Registry<Biome> biomes,
                                                                   RegistryEntry<Biome> out,
                                                                   int robustDelta,
                                                                   boolean upland,
                                                                   int blockX,
                                                                   int blockZ,
                                                                   String callerContext,
                                                                   int landBandIndex) {
        int cutoff = WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST;
        boolean savannaFamily = isSavannaFamily(out);
        String incomingBiomeId = savannaFamily ? savannaIncomingBiomeId(out) : biomeId(out);
        if (shouldSkipSavannaGate(callerContext)) {
            logSavannaGateSkip(callerContext, blockX, blockZ, incomingBiomeId, landBandIndex, robustDelta);
            return out;
        }
        if (!savannaFamily || robustDelta < cutoff) {
            auditSavannaGate(robustDelta, upland, savannaFamily, incomingBiomeId, incomingBiomeId);
            return out;
        }
        String reason = savannaGateReason(robustDelta);
        String selectedBiomeId = savannaGateBiomeId(incomingBiomeId, robustDelta);
        logSavannaGateCounters(incomingBiomeId, selectedBiomeId, reason);
        logSavannaSpawnGateDebug(callerContext, blockX, blockZ, incomingBiomeId, selectedBiomeId, landBandIndex, reason);
        auditSavannaGate(robustDelta, upland, true, incomingBiomeId, selectedBiomeId);
        return biome(biomes, selectedBiomeId);
    }

    private static RegistryEntry<Biome> applySavannaWindsweptGate(Collection<RegistryEntry<Biome>> biomes,
                                                                   RegistryEntry<Biome> out,
                                                                   int robustDelta,
                                                                   boolean upland,
                                                                   int blockX,
                                                                   int blockZ,
                                                                   String callerContext,
                                                                   int landBandIndex) {
        int cutoff = WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST;
        boolean savannaFamily = isSavannaFamily(out);
        String incomingBiomeId = savannaFamily ? savannaIncomingBiomeId(out) : biomeId(out);
        if (shouldSkipSavannaGate(callerContext)) {
            logSavannaGateSkip(callerContext, blockX, blockZ, incomingBiomeId, landBandIndex, robustDelta);
            return out;
        }
        if (!savannaFamily || robustDelta < cutoff) {
            auditSavannaGate(robustDelta, upland, savannaFamily, incomingBiomeId, incomingBiomeId);
            return out;
        }
        String reason = savannaGateReason(robustDelta);
        String selectedBiomeId = savannaGateBiomeId(incomingBiomeId, robustDelta);
        RegistryEntry<Biome> selected = entryById(biomes, selectedBiomeId);
        RegistryEntry<Biome> returning = selected != null ? selected : out;
        String outgoing = selected != null ? selectedBiomeId : incomingBiomeId;
        logSavannaGateCounters(incomingBiomeId, outgoing, reason);
        logSavannaSpawnGateDebug(callerContext, blockX, blockZ, incomingBiomeId, outgoing, landBandIndex, reason);
        auditSavannaGate(robustDelta, upland, true, incomingBiomeId, outgoing);
        return returning;
    }

    private static boolean isSavannaFamily(RegistryEntry<Biome> entry) {
        return isBiomeId(entry, "minecraft:savanna")
                || isBiomeId(entry, "minecraft:savanna_plateau")
                || isBiomeId(entry, "minecraft:windswept_savanna");
    }

    private static boolean isBadlandsFamily(RegistryEntry<Biome> entry) {
        return isBiomeId(entry, "minecraft:badlands")
                || isBiomeId(entry, "minecraft:wooded_badlands")
                || isBiomeId(entry, "minecraft:eroded_badlands");
    }

    private static String warmDryPathFamily(RegistryEntry<Biome> entry) {
        if (entry == null) {
            return "other";
        }
        if (isBiomeId(entry, "minecraft:savanna")) {
            return "savanna";
        }
        if (isBiomeId(entry, "minecraft:savanna_plateau")) {
            return "plateau";
        }
        if (isBiomeId(entry, "minecraft:windswept_savanna")) {
            return "windswept";
        }
        if (isBiomeId(entry, "minecraft:desert")) {
            return "desert";
        }
        if (isBadlandsFamily(entry)) {
            return "badlands";
        }
        return "other";
    }

    private static boolean warmDryPathFamilyRelevant(RegistryEntry<Biome> entry) {
        return isSavannaFamily(entry) || isBiomeId(entry, "minecraft:desert") || isBadlandsFamily(entry);
    }

    private static void recordWarmDryPath(String source,
                                          RegistryEntry<Biome> before,
                                          RegistryEntry<Biome> after,
                                          int blockX,
                                          int blockZ,
                                          int bandIndex,
                                          ProvinceAuthority.Province province) {
        if (!DEBUG_WARM_DRY_PATHS || after == null || !warmDryPathFamilyRelevant(after)) {
            return;
        }

        AtomicLong sourceCounter = switch (source) {
            case "WARM_DRY_SELECTOR" -> WARM_DRY_PATH_WARM_DRY_SELECTOR;
            case "TROPICAL_GRADIENT" -> WARM_DRY_PATH_TROPICAL_GRADIENT;
            case "OPEN_TROPICAL_FALLBACK" -> WARM_DRY_PATH_OPEN_TROPICAL_FALLBACK;
            case "PICK_WARM_FALLBACK" -> WARM_DRY_PATH_PICK_WARM_FALLBACK;
            case "FINAL_SAVANNA_CLAMP" -> WARM_DRY_PATH_FINAL_SAVANNA_CLAMP;
            case "ENFORCE_LAND_BAND_POOL_REWRITE" -> WARM_DRY_PATH_ENFORCE_LAND_BAND_POOL_REWRITE;
            case "SANITIZE_REWRITE" -> WARM_DRY_PATH_SANITIZE_REWRITE;
            case "DIRECT_POOL_PICK" -> WARM_DRY_PATH_DIRECT_POOL_PICK;
            default -> null;
        };
        if (sourceCounter == null) {
            return;
        }

        sourceCounter.incrementAndGet();
        WARM_DRY_PATH_TOTAL.incrementAndGet();
        switch (warmDryPathFamily(after)) {
            case "savanna" -> WARM_DRY_PATH_SAVANNA.incrementAndGet();
            case "plateau" -> WARM_DRY_PATH_SAVANNA_PLATEAU.incrementAndGet();
            case "windswept" -> WARM_DRY_PATH_WINDSWEPT_SAVANNA.incrementAndGet();
            case "desert" -> WARM_DRY_PATH_DESERT.incrementAndGet();
            case "badlands" -> WARM_DRY_PATH_BADLANDS.incrementAndGet();
            default -> WARM_DRY_PATH_OTHER.incrementAndGet();
        }
        WARM_DRY_PATH_LAST_SOURCE.set(source);
        WARM_DRY_PATH_LAST_FROM.set(biomeId(before));
        WARM_DRY_PATH_LAST_TO.set(biomeId(after));

        long total = WARM_DRY_PATH_TOTAL.get();
        if (WARM_DRY_PATH_AUDIT_SUMMARY_EVERY > 0 && total % WARM_DRY_PATH_AUDIT_SUMMARY_EVERY == 0) {
            LOGGER.info("[LAT][WARM_DRY_PATHS] total={} source[sel={} trop={} open={} warm={} clamp={} pool={} sanitize={} direct={}] family[savanna={} plateau={} windswept={} desert={} badlands={} other={}] last[src={} from={} to={} band={} province={} x={} z={}]",
                    total,
                    WARM_DRY_PATH_WARM_DRY_SELECTOR.get(),
                    WARM_DRY_PATH_TROPICAL_GRADIENT.get(),
                    WARM_DRY_PATH_OPEN_TROPICAL_FALLBACK.get(),
                    WARM_DRY_PATH_PICK_WARM_FALLBACK.get(),
                    WARM_DRY_PATH_FINAL_SAVANNA_CLAMP.get(),
                    WARM_DRY_PATH_ENFORCE_LAND_BAND_POOL_REWRITE.get(),
                    WARM_DRY_PATH_SANITIZE_REWRITE.get(),
                    WARM_DRY_PATH_DIRECT_POOL_PICK.get(),
                    WARM_DRY_PATH_SAVANNA.get(),
                    WARM_DRY_PATH_SAVANNA_PLATEAU.get(),
                    WARM_DRY_PATH_WINDSWEPT_SAVANNA.get(),
                    WARM_DRY_PATH_DESERT.get(),
                    WARM_DRY_PATH_BADLANDS.get(),
                    WARM_DRY_PATH_OTHER.get(),
                    source,
                    WARM_DRY_PATH_LAST_FROM.get(),
                    WARM_DRY_PATH_LAST_TO.get(),
                    bandIndex,
                    province,
                    blockX,
                    blockZ);
        }
    }

    private static RegistryEntry<Biome> softenSubtropicalBadlands(Registry<Biome> biomes, RegistryEntry<Biome> base, RegistryEntry<Biome> pick) {
        if (!isBadlandsFamily(pick)) {
            return pick;
        }
        return pick;
    }

    private static RegistryEntry<Biome> softenSubtropicalBadlands(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, RegistryEntry<Biome> pick) {
        return pick;
    }

    private static boolean badlandsProvinceAuthorityHit(long worldSeed, int blockX, int blockZ, int effectiveRadiusHint) {
        int radius = effectiveRadiusHint > 0 ? effectiveRadiusHint : ACTIVE_RADIUS_BLOCKS;
        if (radius <= 0) {
            radius = REFERENCE_DIAMETER_BLOCKS / 2;
        }
        radius = Math.max(1, radius);

        ProvinceAuthority.Province province = warmProvinceClass(
                blockX,
                blockZ,
                authoritativeLandBandIndex(blockX, blockZ, radius));
        if (province != ProvinceAuthority.Province.WARM_DRY) {
            return false;
        }

        int minRadius = Math.min(radius - 1, Math.max(BADLANDS_REGION_MIN_RADIUS_BLOCKS, (int) Math.round(radius * BADLANDS_REGION_RADIUS_FRAC)));
        if (minRadius <= 0) {
            return false;
        }

        int xInset = Math.max(384, (int) Math.round(radius * BADLANDS_REGION_X_INSET_FRAC));
        int minAnchorX = -radius + xInset;
        int maxAnchorX = radius - xInset;
        if (maxAnchorX <= minAnchorX) {
            minAnchorX = -radius / 3;
            maxAnchorX = radius / 3;
        }
        int anchorX = minAnchorX
                + (int) Math.floor(toUnitDouble(mix64(worldSeed ^ BADLANDS_REGION_ANCHOR_X_SALT))
                * (double) (Math.max(1, maxAnchorX - minAnchorX + 1)));

        int dryMinAbsZ = bandBoundaryBlocks(1, radius);
        int dryMaxAbsZ = bandBoundaryBlocks(2, radius);
        if (dryMaxAbsZ <= dryMinAbsZ) {
            return false;
        }
        int drySpan = dryMaxAbsZ - dryMinAbsZ;
        int dryInset = Math.max(64, (int) Math.round(drySpan * 0.20));
        int minAnchorAbsZ = Math.min(dryMaxAbsZ - 1, dryMinAbsZ + dryInset);
        int maxAnchorAbsZ = Math.max(minAnchorAbsZ, dryMaxAbsZ - dryInset);
        int anchorAbsZ = minAnchorAbsZ
                + (int) Math.floor(toUnitDouble(mix64(worldSeed ^ BADLANDS_REGION_ANCHOR_Z_SALT))
                * (double) (Math.max(1, maxAnchorAbsZ - minAnchorAbsZ + 1)));
        double dx = (double) blockX - (double) anchorX;
        double dz = Math.abs((double) blockZ) - (double) anchorAbsZ;
        double theta = Math.atan2(dz, dx);
        int shapeX = (int) Math.round(Math.cos(theta) * BADLANDS_REGION_ANGLE_SAMPLE_BLOCKS);
        int shapeZ = (int) Math.round(Math.sin(theta) * BADLANDS_REGION_ANGLE_SAMPLE_BLOCKS);
        double shapeNoise = ValueNoise2D.sampleBlocks(
                worldSeed ^ BADLANDS_REGION_SHAPE_SALT,
                shapeX,
                shapeZ,
                BADLANDS_REGION_WOBBLE_SCALE_BLOCKS);
        double shapeSigned = (shapeNoise * 2.0) - 1.0;
        double baseRadius = Math.max(minRadius, radius * BADLANDS_REGION_RADIUS_FRAC);
        double regionRadius = baseRadius * (1.0 + shapeSigned * BADLANDS_REGION_WOBBLE_FRAC);
        regionRadius = Math.max(baseRadius * (1.0 - BADLANDS_REGION_WOBBLE_FRAC), regionRadius);
        return (dx * dx + dz * dz) <= (regionRadius * regionRadius);
    }

    private static boolean badlandsProvinceCoreHit(long worldSeed, int blockX, int blockZ, int effectiveRadiusHint) {
        int radius = effectiveRadiusHint > 0 ? effectiveRadiusHint : ACTIVE_RADIUS_BLOCKS;
        if (radius <= 0) {
            radius = REFERENCE_DIAMETER_BLOCKS / 2;
        }
        radius = Math.max(1, radius);
        if (!badlandsProvinceAuthorityHit(worldSeed, blockX, blockZ, radius)) {
            return false;
        }

        double coreNoise = ValueNoise2D.sampleBlocks(worldSeed ^ BADLANDS_REGION_CORE_SHAPE_SALT, blockX, blockZ, Math.max(512, radius / 3));
        return coreNoise < BADLANDS_REGION_CORE_WOBBLE_FRAC;
    }

    private static boolean badlandsProvinceWobbleHit(long worldSeed, int blockX, int blockZ, int effectiveRadiusHint) {
        int radius = effectiveRadiusHint > 0 ? effectiveRadiusHint : ACTIVE_RADIUS_BLOCKS;
        if (radius <= 0) {
            radius = REFERENCE_DIAMETER_BLOCKS / 2;
        }
        radius = Math.max(1, radius);
        double wobble = ValueNoise2D.sampleBlocks(worldSeed ^ BADLANDS_REGION_CORE_SHAPE_SALT, blockX, blockZ, Math.max(512, radius / 4));
        return wobble > 0.72;
    }

    private static boolean aridHotspotHere(long worldSeed, int blockX, int blockZ) {
        // Coarse, world-size-aware arid province membership. Restrict to warm/subtropical bands and
        // demand a low-noise hit so only a few large provinces form instead of thin seams.
        int radius = ACTIVE_RADIUS_BLOCKS > 0 ? ACTIVE_RADIUS_BLOCKS : REFERENCE_DIAMETER_BLOCKS / 2;
        if (radius <= 0) {
            return false;
        }
        double latFrac = Math.abs(blockZ) / (double) radius; // 0 at equator, 1 at pole
        if (latFrac < 0.18 || latFrac > 0.58) {
            return false; // keep arid provinces in the warm/subtropical belt
        }

        int scale = Math.max(ARID_REGION_MIN_SCALE_BLOCKS, (int) Math.round(radius * 0.60));
        double primary = ValueNoise2D.sampleBlocks(worldSeed ^ ARID_REGION_SALT, blockX, blockZ, scale);
        if (primary >= 0.18) {
            return false; // only the lowest 18% of the coarse noise become arid members
        }

        // Secondary softness gate to avoid jagged edges and peppering.
        double secondary = ValueNoise2D.sampleBlocks(worldSeed ^ (ARID_REGION_SALT ^ 0x5A11A11DL), blockX, blockZ, Math.max(scale / 2, ARID_REGION_MIN_SCALE_BLOCKS));
        return secondary < 0.46;
    }

    private record PreviewTerrain(int centerHeight, int robustDelta) {
    }

    private static final String MANGROVE_ID = "minecraft:mangrove_swamp";
    private static final String SWAMP_ID = "minecraft:swamp";
    private static final String BADLANDS_ID = "minecraft:badlands";
    private static final long ARID_REGION_SALT = 0xA11D9110L;
    private static final int ARID_REGION_MIN_SCALE_BLOCKS = 2048;
    private static final int MANGROVE_PATCH_CELL_BLOCKS = 1024;
    private static final int MANGROVE_PATCH_PERCENT = 20;
    private static final int MANGROVE_PATCH_SALT = 0x2F7A3B1C;
    private static final long MANGROVE_FALLBACK_SALT = 0x6D2B79F5L;
    private static final long SWAMP_FALLBACK_SALT = 0x7A1D9E0BL;

    // Wetland gating for swamp patches near water (Kakadu-style: patchy, tropical-biased)
    private static final long WETLAND_SALT = 0x6A6B_7765_746C_616EL; // "jkwetlan" -> just a stable salt
    private static final long TROPICAL_CANOPY_SALT = 0x7472_6F70_6361_6E79L; // "tropcany"
    private static final long TROPICAL_COMPOSITION_SALT = 0x7472_6F70_636F_6D70L; // "tropcomp"
    private static final long TROPICAL_MICRO_COMPOSITION_SALT = 0x7472_6F70_6D69_6372L; // "tropmicr"
    private static final long TROPICAL_OPENNESS_SALT = 0x7472_6F70_6F70_656EL; // "tropopen"
    private static final double WETLAND_FREQ = 1.0 / 1200.0; // low frequency => broad patches
    private static final int WETLAND_SCALE_BLOCKS = 1200; // matches WETLAND_FREQ

    // Pale Garden uses one deterministic world-scale temperate region per world.
    // The outer region is a dark-forest authority container; inside it a smaller nested
    // core is the Pale Garden blob. Outside the outer region pale_garden is blocked.
    private static final long PALE_GARDEN_REGION_ANCHOR_X_SALT = 0x7061_6C65_5F61_6E63L; // "pale_anc"
    private static final long PALE_GARDEN_REGION_ANCHOR_Z_SALT = 0x7061_6C65_5F61_7A7AL; // "pale_azz"
    private static final long PALE_GARDEN_REGION_HEMI_SALT = 0x7061_6C65_5F68_656DL; // "pale_hem"
    private static final long PALE_GARDEN_REGION_SHAPE_SALT = 0x7061_6C65_5F73_6861L; // "pale_sha"
    private static final double PALE_GARDEN_REGION_RADIUS_FRAC = 0.18;
    private static final int PALE_GARDEN_REGION_MIN_RADIUS_BLOCKS = 720;
    private static final double PALE_GARDEN_REGION_WOBBLE_FRAC = 0.18;
    private static final int PALE_GARDEN_REGION_ANGLE_SAMPLE_BLOCKS = 2048;
    private static final int PALE_GARDEN_REGION_WOBBLE_SCALE_BLOCKS = 640;
    private static final double PALE_GARDEN_REGION_X_INSET_FRAC = 0.20;
    private static final double PALE_GARDEN_REGION_TEMPERATE_INSET_FRAC = 0.18;
    // Inner core (pale_garden blob) — same anchor center, fraction of outer base radius.
    private static final long PALE_GARDEN_CORE_SHAPE_SALT = 0x7061_6C65_5F63_6F72L; // "pale_cor"
    private static final double PALE_GARDEN_CORE_RADIUS_FRAC = 0.50; // fraction of outer base radius
    private static final double PALE_GARDEN_CORE_WOBBLE_FRAC = 0.12;

    // dark_forest restoration density cap: outside the Pale Garden container,
    // restrict guard restorations to a noise-defined fraction of shoulder cells
    // to prevent overrepresentation of dark_forest outside the container region.
    private static final long DARK_FOREST_RESTORE_DENSITY_SALT = 0xD4F0657DB100DL;
    private static final int DARK_FOREST_RESTORE_DENSITY_SCALE = 2048;
    private static final double DARK_FOREST_RESTORE_DENSITY_THRESHOLD = 0.55;

    private static final int BADLANDS_PATCH_SIZE_BLOCKS = 65536;
    private static final double BADLANDS_PATCH_CHANCE = 0.42;
    private static final long BADLANDS_PATCH_SALT = 0xBADD1A2DL;
    private static final long BADLANDS_REGION_ANCHOR_X_SALT = 0x6261_646C_5F61_6E63L; // "badl_anc"
    private static final long BADLANDS_REGION_ANCHOR_Z_SALT = 0x6261_646C_5F61_7A7AL; // "badl_azz"
    private static final long BADLANDS_REGION_SHAPE_SALT = 0x6261_646C_5F736861L; // "badl_sha"
    private static final long BADLANDS_REGION_CORE_SHAPE_SALT = 0x6261_646C_5F636F72L; // "badl_cor"
    private static final long BADLANDS_OUTSIDE_PROVINCE_SALT = 0x6261_646C_5F6F7574L; // "badl_out"
    private static final double BADLANDS_OUTSIDE_PROVINCE_THRESHOLD = 0.22;
    private static final double BADLANDS_REGION_RADIUS_FRAC = 0.30;
    private static final int BADLANDS_REGION_MIN_RADIUS_BLOCKS = 960;
    private static final double BADLANDS_REGION_WOBBLE_FRAC = 0.16;
    private static final int BADLANDS_REGION_ANGLE_SAMPLE_BLOCKS = 2048;
    private static final int BADLANDS_REGION_WOBBLE_SCALE_BLOCKS = 720;
    private static final double BADLANDS_REGION_X_INSET_FRAC = 0.18;
    private static final double BADLANDS_REGION_CORE_RADIUS_FRAC = 0.52;
    private static final double BADLANDS_REGION_CORE_WOBBLE_FRAC = 0.10;

    private static final int SWAMP_PATCH_SIZE_BLOCKS = 1024;
    private static final double SWAMP_PATCH_CHANCE = 0.66;
    private static final long SWAMP_PATCH_SALT = 0x53A95A4DL;
    private static final int SWAMP_SUBTROPICAL_PATCH_MAX_OCEAN_DISTANCE = 192;

    private static final TagKey<Biome> LAT_EQUATOR_PRIMARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_equator_primary"));
    private static final TagKey<Biome> LAT_EQUATOR_SECONDARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_equator_secondary"));
    private static final TagKey<Biome> LAT_EQUATOR_ACCENT = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_equator_accent"));

    private static final TagKey<Biome> LAT_TROPICS_PRIMARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_tropics_primary"));
    private static final TagKey<Biome> LAT_TROPICS_SECONDARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_tropics_secondary"));
    private static final TagKey<Biome> LAT_TROPICS_ACCENT = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_tropics_accent"));

    private static final TagKey<Biome> LAT_ARID_PRIMARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_arid_primary"));
    private static final TagKey<Biome> LAT_ARID_SECONDARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_arid_secondary"));
    private static final TagKey<Biome> LAT_ARID_ACCENT = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_arid_accent"));

    private static final TagKey<Biome> LAT_TRANS_ARID_TROPICS_1_PRIMARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_trans_arid_tropics_1_primary"));
    private static final TagKey<Biome> LAT_TRANS_ARID_TROPICS_1_SECONDARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_trans_arid_tropics_1_secondary"));
    private static final TagKey<Biome> LAT_TRANS_ARID_TROPICS_1_ACCENT = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_trans_arid_tropics_1_accent"));

    private static final TagKey<Biome> LAT_TRANS_ARID_TROPICS_2_PRIMARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_trans_arid_tropics_2_primary"));
    private static final TagKey<Biome> LAT_TRANS_ARID_TROPICS_2_SECONDARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_trans_arid_tropics_2_secondary"));
    private static final TagKey<Biome> LAT_TRANS_ARID_TROPICS_2_ACCENT = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_trans_arid_tropics_2_accent"));

    private static final TagKey<Biome> LAT_SUBTROPICAL_HUMID_PRIMARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_subtropical_humid_primary"));
    private static final TagKey<Biome> LAT_SUBTROPICAL_HUMID_SECONDARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_subtropical_humid_secondary"));
    private static final TagKey<Biome> LAT_SUBTROPICAL_HUMID_ACCENT = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_subtropical_humid_accent"));

    private static final TagKey<Biome> LAT_TEMPERATE_PRIMARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_temperate_primary"));
    private static final TagKey<Biome> LAT_TEMPERATE_SECONDARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_temperate_secondary"));
    private static final TagKey<Biome> LAT_TEMPERATE_ACCENT = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_temperate_accent"));
    private static final TagKey<Biome> LAT_TEMPERATE_MOUNTAIN = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_temperate_mountain"));

    private static final TagKey<Biome> LAT_SUBPOLAR_PRIMARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_subpolar_primary"));
    private static final TagKey<Biome> LAT_SUBPOLAR_SECONDARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_subpolar_secondary"));
    private static final TagKey<Biome> LAT_SUBPOLAR_ACCENT = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_subpolar_accent"));

    private static final TagKey<Biome> LAT_POLAR_PRIMARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_polar_primary"));
    private static final TagKey<Biome> LAT_POLAR_SECONDARY = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_polar_secondary"));
    private static final TagKey<Biome> LAT_POLAR_ACCENT = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_polar_accent"));

    private static final TagKey<Biome> LAT_OCEAN_TROPICAL = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_ocean_tropical"));
    private static final TagKey<Biome> LAT_OCEAN_TEMPERATE = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_ocean_temperate"));
    private static final TagKey<Biome> LAT_OCEAN_SUBPOLAR = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_ocean_subpolar"));
    private static final TagKey<Biome> LAT_OCEAN_POLAR = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_ocean_polar"));

    private enum TransitionMode {
        SMOOTH_WARP,
        CELLHASH_PATCHES,
        OFF
    }

    private static final TransitionMode TRANSITION_MODE = TransitionMode.SMOOTH_WARP;
    private static final boolean DISABLE_GRID_DITHER = Boolean.parseBoolean(
            System.getProperty("latitude.disableGridDither", "true"));

    private static final int REFERENCE_DIAMETER_BLOCKS = 20000;

    private static final int VARIANT_CELL_SIZE_BLOCKS = 38;
    private static final int BLEND_TRANSITION_WIDTH_BLOCKS = 1408;
    private static final int BLEND_DITHER_SCALE_BLOCKS = 512;
    private static final int BLEND_NOISE_PATCH_CHUNKS = 10;
    private static final int WARP_NOISE_PATCH_CHUNKS = 12;
    private static final double BAND_JITTER_FRAC = 0.03;
    private static final int BAND_JITTER_MIN_BLOCKS = 120;
    private static final int BAND_JITTER_MAX_BLOCKS = 700;
    private static final double BAND_JITTER_WAVELENGTH_FRAC = 0.45;
    private static final int BAND_JITTER_WAVELENGTH_MIN_BLOCKS = 2600;
    private static final int BAND_JITTER_WAVELENGTH_MAX_BLOCKS = 12000;
    private static final int DITHER_SCALE_BLOCKS = 144;
    private static final int TROPICAL_STEP_PATCH_BLOCKS = 352;
    private static final int WARP_AMPLITUDE_BLOCKS = 420;
    private static final int WARP_SCALE_BLOCKS = 4096;
    private static final long JITTER_NOISE_SALT = -6795153568590067944L;
    private static final long DITHER_NOISE_SALT = 1161981756646125696L;
    private static final long BLEND_NOISE_SALT = 0x53EED5EEDL;
    private static final long WARP_NOISE_SALT = 0x5A7A5EED0F00D123L;
    private static final long TROPICAL_DITHER_SALT = 0x5EEDBEEF5EEDBEEFL;
    private static final long SUBPOLAR_RAMP_SALT = 0x5EED5B09A5EEDL;
    private static final long SNOWY_RAMP_SALT = 0x5EEDB17A5EEDL;
    private static final double SNOWY_RAMP_START_DEG = 54.0;
    private static final double SNOWY_RAMP_FULL_DEG = 68.0;
    private static final double GROVE_MIN_DEG = 54.0;
    private static final double EXTREME_POLAR_CAP_MIN_DEG = 74.5;
    private static final int SUBPOLAR_RAMP_PATCH_BLOCKS = 224;
    private static final int SNOWY_RAMP_PATCH_BLOCKS = 288;

    private static final Set<String> SURFACE_CAVE_DENYLIST = Set.of(
            "minecraft:dripstone_caves",
            "minecraft:lush_caves",
            "minecraft:deep_dark"
    );

    private static final Set<String> WARM_BIOME_BLOCKLIST = Set.of(
            "minecraft:jungle",
            "minecraft:sparse_jungle",
            "minecraft:bamboo_jungle",
            "minecraft:savanna",
            "minecraft:savanna_plateau",
            "minecraft:desert",
            "minecraft:badlands",
            "minecraft:wooded_badlands",
            "minecraft:eroded_badlands",
            "minecraft:mangrove_swamp"
    );

    private static final int UPLAND_MIN_Y = 112;
    private static final int UPLAND_FULL_Y = 145;
    private static final int UPLAND_SCALE_BLOCKS = 2048;
    private static final int SAVANNA_UPLAND_CLAMP_Y = 90;
    private static final int SAVANNA_PLATEAU_MIN_Y = 100;
    private static final int WINDSWEPT_MIN_Y = 120;
    private static final int BEACH_SHORTCUT_MAX_SEA_LEVEL_DELTA = 16;
    private static final double BEACH_SHORTCUT_MAX_UPLAND_T = 0.25;
    private static final int MANGROVE_MAX_Y_ABOVE_SEA = 2;
    private static final double MANGROVE_MAX_ABS_LAT_DEG = 25.0;
    private static final int MANGROVE_COASTAL_MAX_BLOCKS = 384;
    private static final int MANGROVE_WATER_SCAN_RADIUS_BLOCKS = 2;
    private static final int MANGROVE_MAX_ROBUST_DELTA = 3;
    private static final int MANGROVE_MIN_WATER_SAMPLES = 6;
    private static final int MANGROVE_MIN_SHALLOW_WATER_SAMPLES = 2;
    private static final double MANGROVE_MIN_WATER_FRACTION = 0.15;
    private static final double MANGROVE_MAX_WATER_FRACTION = 0.65;
    private static final double MANGROVE_CONTINENTALNESS_MAX = 0.015;
    private static final double MANGROVE_MIN_EROSION = 0.08;
    private static final double MANGROVE_MAX_ABS_WEIRDNESS = 0.10;
    private static final int MANGROVE_TRUE_COAST_RECOVERY_MAX_BLOCKS = 32;
    private static final int MANGROVE_PRIMARY_INVITE_COAST_MAX_BLOCKS = 96;
    private static final int MANGROVE_SECONDARY_COAST_MAX_BLOCKS = 64;
    private static final double MANGROVE_SECONDARY_MIN_EROSION = 0.15;
    private static final double MANGROVE_SECONDARY_MAX_ABS_WEIRDNESS = 0.70;

    private static final int SAVANNA_RUGGED_RING_BLOCKS = 24;
    private static final int WINDSWEPT_RUGGED_THRESH = 8;
    private static final int WINDSWEPT_RUGGED_HYST = 2;
    private static final int PREVIEW_HEIGHT_MARGIN_BLOCKS = 25;

    private static final ThreadLocal<Long2IntOpenHashMap> PREVIEW_HEIGHT_CACHE =
            ThreadLocal.withInitial(Long2IntOpenHashMap::new);
    private static final ThreadLocal<Long> PREVIEW_HEIGHT_CACHE_CHUNK =
            ThreadLocal.withInitial(() -> Long.MIN_VALUE);
    private static final long UPLAND_ROLL_SALT = 0x1CEB0D03L;
    private static final long UPLAND_POOL_SALT = 0x1CEB0D04L;
    private static final String[] TEMPERATE_UPLAND_BIOMES = {
            "minecraft:meadow",
            "minecraft:windswept_hills",
            "minecraft:windswept_forest"
    };
    private static final double TEMPERATE_WARM_EDGE_SHOULDER_FRAC = 0.18;
    private static final int TEMPERATE_WARM_EDGE_SHOULDER_MIN_BLOCKS = 96;
    private static final int TEMPERATE_WARM_EDGE_SHOULDER_MAX_BLOCKS = 320;
    private static final double TEMPERATE_WARM_EDGE_LAT_MIN_DEG = 35.0;
    private static final double TEMPERATE_WARM_EDGE_LAT_MAX_DEG = 43.0;
    private static final long TEMPERATE_WARM_EDGE_ROLL_SALT = 0x74EAD9E54B0AL;
    private static final String[] TEMPERATE_WARM_EDGE_TRANSITION_BIOMES = {
            "minecraft:plains",
            "minecraft:sunflower_plains",
            "minecraft:meadow",
            "minecraft:flower_forest",
            "minecraft:birch_forest",
            "minecraft:old_growth_birch_forest"
    };

    // --- Blend noise helpers (chunk-stable, 2D, smooth "blobs") ---

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static double hash01(long seed, int x, int z, long salt) {
        long h = seed ^ salt;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        h = mix64(h);
        return ((h >>> 11) * (1.0 / (1L << 53)));
    }

    private static double cellHash01(long seed, int cellX, int cellZ) {
        long x = seed;
        x ^= 0x9E3779B97F4A7C15L * (long) cellX;
        x ^= 0xC2B2AE3D27D4EB4FL * (long) cellZ;
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        x *= 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        return ((x >>> 11) * (1.0 / (1L << 53)));
    }

    private static double smoothstep(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static int applyTropicalStepDither(long seed, int blockX, int blockZ, int baseStep, double stepFrac) {
        if (baseStep >= 3) {
            return baseStep;
        }
        int patchBlocks = DISABLE_GRID_DITHER ? TROPICAL_STEP_PATCH_BLOCKS : DITHER_SCALE_BLOCKS;
        double dither = ValueNoise2D.sampleBlocks(seed ^ TROPICAL_DITHER_SALT, blockX, blockZ, patchBlocks);
        return dither < stepFrac ? baseStep + 1 : baseStep;
    }

    private static double blobNoise01(long seed, int chunkX, int chunkZ, int patchSizeChunks, long salt) {
        int gx = Math.floorDiv(chunkX, patchSizeChunks);
        int gz = Math.floorDiv(chunkZ, patchSizeChunks);

        int x0 = gx * patchSizeChunks;
        int z0 = gz * patchSizeChunks;
        int x1 = x0 + patchSizeChunks;
        int z1 = z0 + patchSizeChunks;

        double fx = (chunkX - x0) / (double) patchSizeChunks;
        double fz = (chunkZ - z0) / (double) patchSizeChunks;

        double u = smoothstep(fx);
        double v = smoothstep(fz);

        double n00 = hash01(seed, x0, z0, salt);
        double n10 = hash01(seed, x1, z0, salt);
        double n01 = hash01(seed, x0, z1, salt);
        double n11 = hash01(seed, x1, z1, salt);

        double nx0 = n00 + (n10 - n00) * u;
        double nx1 = n01 + (n11 - n01) * u;
        return nx0 + (nx1 - nx0) * v;
    }

    /** Block-space version for swamp patch mask; avoids chunk/block mismatch. */
    private static double blobNoise01Blocks(long seed, int blockX, int blockZ, int patchSizeBlocks, long salt) {
        int gx = Math.floorDiv(blockX, patchSizeBlocks);
        int gz = Math.floorDiv(blockZ, patchSizeBlocks);
        int x0 = gx * patchSizeBlocks;
        int z0 = gz * patchSizeBlocks;
        int x1 = x0 + patchSizeBlocks;
        int z1 = z0 + patchSizeBlocks;
        double fx = (blockX - x0) / (double) patchSizeBlocks;
        double fz = (blockZ - z0) / (double) patchSizeBlocks;
        double u = smoothstep(fx);
        double v = smoothstep(fz);
        double n00 = hash01(seed, x0, z0, salt);
        double n10 = hash01(seed, x1, z0, salt);
        double n01 = hash01(seed, x0, z1, salt);
        double n11 = hash01(seed, x1, z1, salt);
        double nx0 = n00 + (n10 - n00) * u;
        double nx1 = n01 + (n11 - n01) * u;
        return nx0 + (nx1 - nx0) * v;
    }

    public static RegistryEntry<Biome> pick(Registry<Biome> biomeRegistry, RegistryEntry<Biome> base, int blockX, int blockZ, int blockY, int borderRadiusBlocks,
                                            MultiNoiseUtil.MultiNoiseSampler sampler, String callerContext) {
        return pick(biomeRegistry, base, blockX, blockZ, blockY, borderRadiusBlocks, sampler, callerContext, null, null, null);
    }

    public static RegistryEntry<Biome> pick(Registry<Biome> biomeRegistry, RegistryEntry<Biome> base, int blockX, int blockZ, int blockY, int borderRadiusBlocks,
                                            MultiNoiseUtil.MultiNoiseSampler sampler, String callerContext,
                                            NoiseChunkGenerator generator, NoiseConfig noiseConfig, HeightLimitView heightView) {
        int columnDecisionY = surfaceDecisionY(generator, noiseConfig, heightView, blockX, blockZ);
        int biomeY = (blockY < columnDecisionY - 16) ? blockY : columnDecisionY;
        assertSurfaceY(biomeY);
        int activeRadius = ACTIVE_RADIUS_BLOCKS;
        boolean overrideDisabled = Boolean.getBoolean("latitude.disableRadiusOverride");

        if (activeRadius > 0 && borderRadiusBlocks != activeRadius && RADIUS_MISMATCH_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn("[Latitude] RADIUS MISMATCH detected from {}! Arg: {}, Active: {}", callerContext, borderRadiusBlocks, activeRadius);
        }

        int effectiveRadius = (!overrideDisabled && activeRadius > 0) ? activeRadius : borderRadiusBlocks;
        if (effectiveRadius <= 0) {
            return base;
        }
        LAST_SELECTION_PATH.remove();

        // Sparse jungle audit flags for final source classification.
        boolean auditTagPick = false;
        boolean auditSanitize = false;
        boolean auditCanopy = false;
        boolean auditWarmFallback = false;
        boolean auditFinalSavanna = false;

        int lat = Math.abs(blockZ);
        double tBase = (double) lat / (double) effectiveRadius;
        double t = applyBoundaryJitter(blockX, blockZ, effectiveRadius, tBase);
        LatitudeBands.Band band = bandForAbsLatFraction(t);
        int bandIndex = bandIndexForBand(band);

        if (isBeachLike(base) && allowBeachShortcut(generator, columnDecisionY)) {
            RegistryEntry<Biome> out = pickBeachForBand(biomeRegistry, base, blockX, blockZ, bandIndex);
            debugPick(blockX, blockZ, effectiveRadius, t, band, base, out, true, false, null);
            return out;
        }

        // Compute blended band index once; shared by river, ocean, and land so all three
        // use the same 1408-block stochastic transition zone instead of a hard integer snap.
        int blendedBandIndex = latitudeBandIndexWithBlend(blockX, blockZ, effectiveRadius, band, t);

        int landBandIndex = blendedBandIndex;
        boolean mountainNoiseLike = landBandIndex == BAND_TEMPERATE && isMountainLike(sampler, blockX, blockZ);
        boolean skipPreview = shouldSkipPreviewTerrain(callerContext);
        boolean hasReliableSurface = !skipPreview && generator != null && noiseConfig != null && heightView != null;
        // True only when the caller supplied all three preview probe inputs (MIXIN, CAVE_CLAMP).
        // False for atlas/headless callers (SOURCE, ATLAS_SAMPLER) that null out noiseConfig/heightView.
        boolean hasPreviewTerrainInputs = generator != null && noiseConfig != null && heightView != null;
        boolean allowSurfaceGates = hasReliableSurface;
        PreviewTerrain preview = skipPreview
                ? syntheticPreviewTerrain(mountainNoiseLike, generator)
                : previewTerrain(generator, noiseConfig, heightView, blockX, blockZ);
        int seaLevel = previewSeaLevel(generator);
        boolean previewHeightHigh = preview.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS);
        boolean previewRuggedHigh = preview.robustDelta >= WINDSWEPT_RUGGED_THRESH;
        boolean previewHeightModerate = preview.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS / 2);
        // Temperate terrain-authority probe: syntheticPreviewTerrain is noise-circular for temperate —
        // it sets height/ruggedness high when mountainNoiseLike=true, making mountainLike trivially
        // equal to mountainNoiseLike (no actual terrain confirmation).  When real terrain inputs are
        // available (MIXIN/CAVE_CLAMP), do a targeted probe so terrain must confirm before admission,
        // mirroring the polar probe below.  Atlas/headless callers (hasPreviewTerrainInputs=false)
        // remain noise-only, matching polar in atlas context.
        if (skipPreview && landBandIndex == BAND_TEMPERATE && mountainNoiseLike && hasPreviewTerrainInputs) {
            PreviewTerrain temperateProbe = previewTerrain(generator, noiseConfig, heightView, blockX, blockZ);
            previewHeightHigh     = temperateProbe.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS);
            previewRuggedHigh     = temperateProbe.robustDelta >= WINDSWEPT_RUGGED_THRESH;
            previewHeightModerate = temperateProbe.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS / 2);
        }
        boolean mountainLike = mountainNoiseLike && (previewHeightHigh || previewHeightModerate || previewRuggedHigh);
        boolean polarMountainNoiseLike = sampler != null && isMountainLike(sampler, blockX, blockZ);
        // Atlas/headless parity: when real terrain probes are absent, allow the noise signal to
        // satisfy the terrain gate as a substitute for the missing preview terrain inputs.
        // Double-gated: !hasPreviewTerrainInputs (only SOURCE/ATLAS_SAMPLER paths, per call-site audit)
        // AND isAtlasHeadlessContext (explicit context gate to prevent silent future breakage).
        //
        // Live-worldgen bridge (skipPreview && hasPreviewTerrainInputs && polar noise-mountain):
        // syntheticPreviewTerrain uses temperate-gated mountainNoiseLike, which is always false
        // for polar band, producing flat placeholder values that suppress polarTerrainMountainLike.
        // When the noise says "mountain" in a polar cell, do a targeted real-terrain probe
        // (same pattern as the post-pick terrain gate at the subpolar/polar skipPreview block)
        // so that actual mountain terrain can earn polar mountain authority.
        int polarProbeHeight = preview.centerHeight;
        int polarProbeDelta  = preview.robustDelta;
        if (skipPreview && landBandIndex >= BAND_POLAR && polarMountainNoiseLike && hasPreviewTerrainInputs) {
            PreviewTerrain polarProbe = previewTerrain(generator, noiseConfig, heightView, blockX, blockZ);
            polarProbeHeight = polarProbe.centerHeight;
            polarProbeDelta  = polarProbe.robustDelta;
        }
        boolean polarTerrainMountainLike = (!hasPreviewTerrainInputs && isAtlasHeadlessContext(callerContext) && polarMountainNoiseLike)
                || (polarProbeDelta >= 12)
                || (polarProbeHeight >= seaLevel + 20);
        boolean polarMountainLikeFinal = polarMountainNoiseLike && polarTerrainMountainLike;
        if (landBandIndex >= BAND_POLAR && polarMountainLikeFinal) {
            mountainLike = true;
        }
        int oceanDistance = oceanDistanceBlocks(blockX, blockZ, sampler);
        boolean nearOcean = oceanDistance <= MANGROVE_COASTAL_MAX_BLOCKS;
        boolean oceanAuthority = oceanDistance == 0;
        // Veto coarse ODF ocean authority when real terrain is clearly raised land
        if (oceanAuthority && !base.isIn(BiomeTags.IS_OCEAN)
                && generator != null && noiseConfig != null && heightView != null) {
            int realHeight = previewHeight(generator, noiseConfig, heightView, blockX & ~3, blockZ & ~3);
            if (realHeight >= seaLevel) {
                oceanAuthority = false;
            }
        }

        if (base.isIn(BiomeTags.IS_RIVER)) {
            if (blendedBandIndex >= 3) {
                try {
                    RegistryEntry<Biome> out = biome(biomeRegistry, "minecraft:frozen_river");
                    debugPick(blockX, blockZ, effectiveRadius, t, band, base, out, false, false, null);
                    return out;
                } catch (Throwable ignored) {
                    debugPick(blockX, blockZ, effectiveRadius, t, band, base, base, false, false, null);
                    return base;
                }
            } else {
                try {
                    RegistryEntry<Biome> out = biome(biomeRegistry, "minecraft:river");
                    debugPick(blockX, blockZ, effectiveRadius, t, band, base, out, false, false, null);
                    return out;
                } catch (Throwable ignored) {
                    debugPick(blockX, blockZ, effectiveRadius, t, band, base, base, false, false, null);
                    return base;
                }
            }
        }

        if (base.isIn(BiomeTags.IS_OCEAN) || oceanAuthority) {
            RegistryEntry<Biome> oceanBase;
            if (base.isIn(BiomeTags.IS_OCEAN)) {
                oceanBase = base;
            } else {
                try {
                    oceanBase = biome(biomeRegistry, "minecraft:ocean");
                } catch (Throwable ignored) {
                    oceanBase = base;
                }
            }
            RegistryEntry<Biome> oceanPick = oceanByLatitudeBandOrBase(biomeRegistry, oceanBase, blockX, blockZ, blendedBandIndex);
            if (oceanPick == null || !oceanPick.isIn(BiomeTags.IS_OCEAN)) {
                oceanPick = firstPresentOcean(biomeRegistry);
            }
            RegistryEntry<Biome> out = mushroomIslandOverride(biomeRegistry, oceanPick, blockX, blockZ);
            debugPick(blockX, blockZ, effectiveRadius, t, band, base, out, false, false, null);
            return out;
        }

        if (skipPreview && PREVIEW_TERRAIN_SKIP_LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[Latitude] skipping previewHeight() for callerContext={} (atlas fast-path enabled)", callerContext);
        }
        boolean forcedBadlands = false;
        RegistryEntry<Biome> chosen = null;
        String subtropicalSwampSource = null;
        Boolean subtropicalSwampEvaluateAllow = null;
        Boolean subtropicalPostEnforceSwampAllow = null;
        if (chosen == null && (landBandIndex == BAND_TROPICAL || landBandIndex == BAND_SUBTROPICAL) && sampler != null) {
            int noiseX = blockX >> 2;
            int noiseZ = blockZ >> 2;
            MultiNoiseUtil.NoiseValuePoint p = sampler.sample(noiseX, SURFACE_CLASSIFY_Y >> 2, noiseZ);
            double cont = MultiNoiseUtil.toFloat(p.continentalnessNoise());
            double erosion = MultiNoiseUtil.toFloat(p.erosionNoise());
            double weird = MultiNoiseUtil.toFloat(p.weirdnessNoise());
            boolean aridBlocked = isAridTropicalStepSymmetric(blockX, blockZ, t);
            boolean swampPatch = swampPatchHere(WORLD_SEED, blockX, blockZ);
            boolean swampPatchOk = swampOkInPatchScaled(cont, erosion, weird);
            double wetlandNoise = wetlandNoiseSymmetric(WORLD_SEED, blockX, blockZ);
            double wetlandThreshold = scaledWetlandThresholdForBand(bandIndex, t);
            boolean subtropicalCoastalOk = landBandIndex != BAND_SUBTROPICAL
                    || oceanDistance <= SWAMP_SUBTROPICAL_PATCH_MAX_OCEAN_DISTANCE;
            if (!aridBlocked
            && subtropicalCoastalOk
            && swampPatch
            && swampPatchOk
            && wetlandNoise < wetlandThreshold) {
                try {
                    chosen = biome(biomeRegistry, SWAMP_ID);
                    if (landBandIndex == BAND_SUBTROPICAL && !mountainLike && isSwampCandidate(chosen)) {
                        subtropicalSwampSource = "prepassSwampPatch";
                    }
                } catch (Throwable ignored) {
                    // keep null to fall through
                }
            }
        }
        if (chosen == null) {
            chosen = switch (landBandIndex) {
                case BAND_TROPICAL -> pickFromWeightedTags(biomeRegistry, base, blockX, blockZ, BAND_TROPICAL, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
                case BAND_SUBTROPICAL -> {
                    RegistryEntry<Biome> subtropicalPick = pickTropicalGradient(biomeRegistry, base, blockX, blockZ, t);
                    if (!mountainLike && isSwampCandidate(subtropicalPick)) {
                        subtropicalSwampSource = "pickTropicalGradient";
                    }
                    yield subtropicalPick;
                }
                case BAND_TEMPERATE -> pickTemperateLand(biomeRegistry, blockX, blockZ, columnDecisionY,
                        () -> pickFromWeightedTags(biomeRegistry, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT),
                        mountainLike);
                case BAND_SUBPOLAR -> pickSubpolarWithRamp(biomeRegistry, base, blockX, blockZ, t, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
                default -> pickPolarWithFrontShoulder(
                        biomeRegistry,
                        base,
                        blockX,
                        blockZ,
                        t,
                        polarMountainLikeFinal,
                        preview.centerHeight,
                        preview.robustDelta,
                        seaLevel,
                        polarMountainLikeFinal,
                        mountainLike,
                        oceanDistance);
            };
        }
        if (landBandIndex == BAND_TEMPERATE
                && chosen != null
                && isBiomeId(base, "minecraft:dark_forest")
                && !isBiomeId(chosen, "minecraft:dark_forest")
                && !isBiomeId(chosen, "minecraft:pale_garden") // pale_garden is a valid dark_forest replacement
                && isTemperateForestFamily(chosen)
                && (paleGardenRegionHit(WORLD_SEED, blockX, blockZ, effectiveRadius)
                    || ValueNoise2D.sampleBlocks(WORLD_SEED ^ DARK_FOREST_RESTORE_DENSITY_SALT, blockX, blockZ, DARK_FOREST_RESTORE_DENSITY_SCALE) < DARK_FOREST_RESTORE_DENSITY_THRESHOLD)) {
            chosen = base;
        }
        if (landBandIndex == BAND_TEMPERATE
                && !mountainLike
                && chosen != null
                && isTemperateForestFamily(base)
                && !isTemperateForestFamily(chosen)
                && (isBiomeId(chosen, "minecraft:plains")
                || isBiomeId(chosen, "minecraft:taiga")
                || isBiomeId(chosen, "minecraft:old_growth_pine_taiga"))) {
            chosen = base;
        }
        if (skipPreview && landBandIndex >= BAND_SUBPOLAR && chosen != null) {
            int gateHeight = preview.centerHeight;
            int gateDelta = preview.robustDelta;
            if (generator != null && noiseConfig != null && heightView != null) {
                gateHeight = previewHeight(generator, noiseConfig, heightView, blockX & ~3, blockZ & ~3);
            }
            chosen = applyTerrainCompatibilityGate(
                    biomeRegistry,
                    chosen,
                    landBandIndex,
                    blockX,
                    blockZ,
                    gateHeight,
                    gateDelta,
                    seaLevel,
                    oceanDistance,
                    mountainNoiseLike,
                    mountainLike);
        } else if (!skipPreview && landBandIndex >= BAND_SUBPOLAR && chosen != null) {
            chosen = applyTerrainCompatibilityGate(
                    biomeRegistry,
                    chosen,
                    landBandIndex,
                    blockX,
                    blockZ,
                    preview.centerHeight,
                    preview.robustDelta,
                    seaLevel,
                    oceanDistance,
                    mountainNoiseLike,
                    mountainLike);
        }
        String mangroveDecision = null;
        if (DEBUG_SPARSE_JUNGLE_AUDIT && chosen != null && isBiomeId(chosen, "minecraft:sparse_jungle")
                && PATH_TAG_PICK.equals(selectionPathForTrace(base, chosen))) {
            auditTagPick = true;
        }
        RegistryEntry<Biome> sanitized = chosen;
        RegistryEntry<Biome> safe = chosen;
        RegistryEntry<Biome> out = chosen;
        boolean temperateMountainRewriteRan = false;
        boolean finalSavannaRegion = false;
        boolean invitedMangrove = false;
        boolean sourceContext = "SOURCE".equalsIgnoreCase(callerContext);
        if (!forcedBadlands) {
            boolean oceanChosen = chosen != null && chosen.isIn(BiomeTags.IS_OCEAN);
            if (oceanChosen) {
                // Do not allow swamp/mangrove overrides on ocean picks.
                sanitized = chosen;
                safe = chosen;
                out = chosen;
            } else {
                if (!sourceContext && shouldTryMangroveOverride(chosen, landBandIndex)) {
                    MangroveDecision decision = evaluateMangroveWithSurface(blockX, blockZ, columnDecisionY, preview, seaLevel, sampler, nearOcean, hasReliableSurface, hasPreviewTerrainInputs, heightView);
                    mangroveDecision = decision.logLabel();
                    if (decision.allow()) {
                        try {
                            chosen = biome(biomeRegistry, MANGROVE_ID);
                            if (DEBUG_MANGROVE_ORIGIN) {
                                LOGGER.info("[latdev] mangroveSelected x={} z={} surfaceY={} sea={} robustDelta={} origin={} reason={}",
                                        blockX, blockZ, preview.centerHeight, seaLevel, preview.robustDelta,
                                        mangroveOrigin(true, false), mangroveDecision);
                            }
                        } catch (Throwable ignored) {
                            // keep current choice
                        }
                    }
                } else if (!sourceContext && isMangroveCandidate(chosen)) {
                    MangroveDecision decision = evaluateMangroveWithSurface(blockX, blockZ, columnDecisionY, preview, seaLevel, sampler, nearOcean, hasReliableSurface, hasPreviewTerrainInputs, heightView);
                    mangroveDecision = decision.logLabel();
                    if (!decision.allow()) {
                        chosen = pickMangroveFallback(biomeRegistry, base, blockX, blockZ, t, landBandIndex);
                    }
                }
                if (isSwampCandidate(chosen)) {
                    if (subtropicalSwampSource == null && landBandIndex == BAND_SUBTROPICAL && !mountainLike) {
                        subtropicalSwampSource = "evaluateSwampRetention";
                    }
                    SwampDecision swampDecision = evaluateSwamp(blockX, blockZ, sampler);
                    subtropicalSwampEvaluateAllow = swampDecision.allow();
                    if (!swampDecision.allow()) {
                        chosen = pickSwampFallback(biomeRegistry, base, blockX, blockZ, t, landBandIndex);
                    }
                }
                if (!sourceContext && !isMangroveCandidate(chosen) && shouldInviteMangrove(blockX, columnDecisionY, blockZ, bandIndex, sampler, nearOcean)) {
                    invitedMangrove = true;
                    MangroveDecision decision = evaluateMangroveWithSurface(blockX, blockZ, columnDecisionY, preview, seaLevel, sampler, nearOcean, hasReliableSurface, hasPreviewTerrainInputs, heightView);
                    mangroveDecision = decision.logLabel();
                    if (decision.allow()) {
                        try {
                            chosen = biome(biomeRegistry, MANGROVE_ID);
                            if (DEBUG_MANGROVE_INVITE) {
                                LOGGER.info("[latdev] mangroveInvite ACCEPT x={} z={} oceanDist={} decision={}", blockX, blockZ, oceanDistance, mangroveDecision);
                            }
                        } catch (Throwable ignored) {
                            // keep chosen
                        }
                    } else if (DEBUG_MANGROVE_INVITE) {
                        LOGGER.info("[latdev] mangroveInvite REJECT x={} z={} oceanDist={} decision={}", blockX, blockZ, oceanDistance, mangroveDecision);
                    }
                }
                // Guard: polar land has its own mountain picker (pickPolarWithFrontShoulder).
                // Firing the temperate-mountain override there overwrites the polar result and
                // routes it through enforceLandBandPool with a fixed-seed noise pattern that
                // makes frozen_peaks inaccessible in atlas runs.  Only run this block for
                // bands below POLAR (i.e. temperate and subpolar, where mountainLike can
                // legitimately be true via the terrain-preview path).
                boolean mountainPromotion = mountainLike
                        && landBandIndex < BAND_POLAR
                        && (preview.robustDelta >= (WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST)
                        || preview.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS));
                if (mountainPromotion) {
                    temperateMountainRewriteRan = true;
                    chosen = pickFromTagNoiseOrBase(biomeRegistry, LAT_TEMPERATE_MOUNTAIN, base, blockX, blockZ, landBandIndex);
                    if (isBiomeId(chosen, "minecraft:cherry_grove") && landBandIndex < BAND_POLAR) {
                        return chosen;
                    }
                }
                sanitized = sanitizeLandBiome(biomeRegistry, chosen, landBandIndex, blockX, blockZ);
                if (DEBUG_POLAR_CAP_TRACE && landBandIndex == BAND_POLAR && isPolarCapTraceCoord(blockX, blockZ)) {
                    LOGGER.info("[LAT][POLAR_CAP_TRACE][sanitize] ctx={} x={} z={} chosen={} -> sanitized={}",
                            callerContext, blockX, blockZ, biomeId(chosen), biomeId(sanitized));
                }
                if (DEBUG_SPARSE_JUNGLE_AUDIT && !isBiomeId(chosen, "minecraft:sparse_jungle") && isBiomeId(sanitized, "minecraft:sparse_jungle")) {
                    auditSanitize = true;
                }
                safe = repickIfSurfaceCave(biomeRegistry, base, sanitized, blockX, blockZ, t, landBandIndex);
                out = applyLandOverrides(biomeRegistry, safe, blockX, blockZ, landBandIndex);
                if (landBandIndex == BAND_TROPICAL && isJungleFamily(out) && !allowWetTropicalCanopy(blockX, blockZ, t, out)) {
                    out = pickOpenTropicalFallback(biomeRegistry, out, blockX, blockZ, t);
                    if (DEBUG_SPARSE_JUNGLE_AUDIT && isBiomeId(out, "minecraft:sparse_jungle")) {
                        auditCanopy = true;
                    }
                }
                boolean savannaGateInput = isSavannaFamily(out);
                int savannaRobustDelta = preview.robustDelta;
                boolean savannaUpland = previewHeightHigh;
                // MIXIN and CAVE_CLAMP must not re-enter real previewTerrain here — stay on the synthetic path.
                boolean forceSyntheticTerrain = skipPreview && ("MIXIN".equalsIgnoreCase(callerContext) || "CAVE_CLAMP".equalsIgnoreCase(callerContext));
                if (skipPreview && savannaGateInput && generator != null && noiseConfig != null && heightView != null && !forceSyntheticTerrain) {
                    PreviewTerrain wsavPreview = previewTerrain(generator, noiseConfig, heightView, blockX, blockZ);
                    savannaRobustDelta = wsavPreview.robustDelta;
                    savannaUpland = wsavPreview.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS);
                    if (DEBUG_SAVANNA_GATE_AUDIT) {
                        SAVANNA_AUDIT_REAL_PREVIEW.incrementAndGet();
                    }
                } else if (skipPreview && savannaGateInput && sampler != null) {
                    boolean ruggedNoise = isMountainLike(sampler, blockX, blockZ);
                    if (ruggedNoise) {
                        savannaRobustDelta = WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST;
                    }
                    if (DEBUG_SAVANNA_GATE_AUDIT) {
                        SAVANNA_AUDIT_PREVIEW_MISSING.incrementAndGet();
                    }
                } else if (skipPreview && savannaGateInput && DEBUG_SAVANNA_GATE_AUDIT) {
                    SAVANNA_AUDIT_PREVIEW_MISSING.incrementAndGet();
                }
                out = applySavannaWindsweptGate(biomeRegistry, out, savannaRobustDelta, savannaUpland, blockX, blockZ, callerContext, landBandIndex);
                if (landBandIndex == BAND_SUBTROPICAL && isJungleFamily(out)) {
                    out = pickDryWarmFallback(biomeRegistry, out);
                }
                finalSavannaRegion = isSavannaFamily(base) || savannaGateInput || isSavannaFamily(out);
            }
        }
        if (landBandIndex == BAND_TROPICAL || landBandIndex == BAND_SUBTROPICAL) {
            if (isColdBiome(out)) {
                out = pickWarmFallback(biomeRegistry, landBandIndex);
                if (DEBUG_SPARSE_JUNGLE_AUDIT && isBiomeId(out, "minecraft:sparse_jungle")) {
                    auditWarmFallback = true;
                }
            }
        }
        out = enforceSnowyLatitudeRamp(biomeRegistry, out, base, blockX, blockZ, effectiveRadius, landBandIndex);
        out = clampWarmInColdZone(biomeRegistry, base, out, band, blockX, blockZ);
        out = applySubpolarSwampGuard(biomeRegistry, base, out, band);
        RegistryEntry<Biome> preBandEnforce = out;
        if (landBandIndex >= BAND_SUBPOLAR && isJungleFamily(out)) {
            out = pickColdFallback(biomeRegistry, base, blockX, blockZ, landBandIndex);
        }
        if (DEBUG_WARM_POOL_MEMBERSHIP) {
            LAST_WARM_POOL_MEMBERSHIP_SNAPSHOT.remove();
        }
        out = enforceLandBandPool(biomeRegistry, out, blockX, blockZ, t, landBandIndex, mountainLike);
        RegistryEntry<Biome> postPoolEnforce = out;
        boolean swampCandidateAfterEnforce = isSwampCandidate(out);
        boolean swampValidationFailed = false;
        boolean swampFallbackCalled = false;
        RegistryEntry<Biome> swampFallbackReturned = null;
        boolean mangroveFallbackCalled = false;
        RegistryEntry<Biome> mangroveFallbackReturned = null;
        if (swampCandidateAfterEnforce) {
            SwampDecision poolSwampDecision = evaluateSwamp(blockX, blockZ, sampler);
            subtropicalPostEnforceSwampAllow = poolSwampDecision.allow();
            if (!poolSwampDecision.allow()) {
                swampValidationFailed = true;
                swampFallbackCalled = true;
                out = pickSwampFallback(biomeRegistry, base, blockX, blockZ, t, landBandIndex);
                swampFallbackReturned = out;
            }
        }
        // Mangrove guard: enforceLandBandPool can re-introduce mangrove (MANGROVE_ID is in the
        // BAND_SUBTROPICAL allowed-extras list). Re-evaluate and reject if surface/coastal gates fail.
        if (isMangroveCandidate(out)) {
            MangroveDecision poolMangroveDecision = evaluateMangroveWithSurface(blockX, blockZ, columnDecisionY, preview, seaLevel, sampler, nearOcean, hasReliableSurface, hasPreviewTerrainInputs, heightView);
            if (!poolMangroveDecision.allow()) {
                mangroveFallbackCalled = true;
                out = pickMangroveFallback(biomeRegistry, base, blockX, blockZ, t, landBandIndex);
                mangroveFallbackReturned = out;
            }
        }
        if (isSnowyVariant(out)) {
            if (landBandIndex == BAND_SUBTROPICAL && !mountainLike) {
                RegistryEntry<Biome> warmFallback = pickWarmFallback(biomeRegistry, landBandIndex);
                if (warmFallback != null) {
                    out = warmFallback;
                }
            } else {
                double _bgDeg = latitudeDegreesFromRadius(blockZ, effectiveRadius);
                double _bgAlpha = snowyRampAlpha(_bgDeg);
                double _bgR = ValueNoise2D.sampleBlocks(WORLD_SEED ^ SNOWY_RAMP_SALT, blockX, blockZ, SNOWY_RAMP_PATCH_BLOCKS);
                if (_bgR > _bgAlpha) {
                    try {
                        out = biome(biomeRegistry, "minecraft:taiga");
                    } catch (Throwable ignored) {
                        // keep current pick
                    }
                }
            }
        }
        out = enforcePaleGardenRegion(biomeRegistry, out, base, blockX, blockZ, landBandIndex, effectiveRadius);
        out = softenTemperateWarmEdgeTaigaJump(biomeRegistry, base, out, blockX, blockZ, effectiveRadius, bandIndex, landBandIndex, mountainLike);
        RegistryEntry<Biome> postBandEnforce = out;
        if (DEBUG_BIOMES && isMangroveCandidate(out)) {
            LOGGER.warn("[Latitude][MangroveLeak] mangrove escaped into land pool result (registry path) at x={} z={} bandIndex={} y={}",
                    blockX, blockZ, landBandIndex, columnDecisionY);
        }
        if (landBandIndex == BAND_TROPICAL && tropicalBaseStep(blockX, Math.abs(blockZ), t) <= 1 && isJungleFamily(out)) {
            out = pickOpenTropicalFallback(biomeRegistry, out, blockX, blockZ, t);
        }
        RegistryEntry<Biome> beforeFinalSavannaClamp = out;
        boolean finalSavannaClampRan = false;
        if (landBandIndex <= BAND_SUBTROPICAL) {
            finalSavannaClampRan = true;
            RegistryEntry<Biome> beforeClamp = out;
            out = applyFinalSavannaClimateClamp(biomeRegistry, out, finalSavannaRegion, landBandIndex, columnDecisionY, blockX, blockZ);
            if (DEBUG_SPARSE_JUNGLE_AUDIT && out != beforeClamp && isBiomeId(out, "minecraft:sparse_jungle")) {
                auditFinalSavanna = true;
            }
        }
        RegistryEntry<Biome> postFinalSavannaClamp = out;
        logSubtropicalSwampTrace(
                blockX,
                blockZ,
                landBandIndex,
                mountainLike,
                subtropicalSwampSource,
                chosen,
                sanitized,
                preBandEnforce,
                postPoolEnforce,
                postFinalSavannaClamp,
                subtropicalSwampEvaluateAllow,
                subtropicalPostEnforceSwampAllow);
        RegistryEntry<Biome> postFinalClamp = out;
        int overlayBandIndex = authoritativeLandBandIndex(blockX, blockZ, effectiveRadius);
        logSubtropicalJungleReturn("pick-registry", blockX, blockZ, t, landBandIndex, base, chosen, sanitized, preBandEnforce, postBandEnforce, postFinalClamp, out);
        logAtlasViewportJungleReturn("pick-registry", callerContext, blockX, blockZ, t, landBandIndex, overlayBandIndex, base, chosen, sanitized, preBandEnforce, postBandEnforce, postFinalClamp, out);
        traceSubpolarJunglePick(blockX, blockZ, effectiveRadius, landBandIndex, base, out);
        if (DEBUG_SPARSE_JUNGLE_AUDIT && isBiomeId(out, "minecraft:sparse_jungle")) {
            String bucket = "TAG_PICK_DIRECT";
            if (auditFinalSavanna) {
                bucket = "FINAL_SAVANNA_CLAMP";
            } else if (auditCanopy) {
                bucket = "CANOPY_FALLBACK";
            } else if (auditSanitize) {
                bucket = "SANITIZE_REWRITE";
            } else if (auditWarmFallback) {
                bucket = "WARM_SAFETY_FALLBACK";
            } else if (auditTagPick) {
                bucket = "TAG_PICK_DIRECT";
            }
            String detail = "path=" + selectionPathForTrace(base, out) + " auditFlags{tag=" + auditTagPick + ",sanitize=" + auditSanitize + ",canopy=" + auditCanopy + ",warm=" + auditWarmFallback + ",finalSavanna=" + auditFinalSavanna + "}";
            auditSparseJungle(bucket, blockX, blockZ, landBandIndex, detail, biomeId(preBandEnforce), biomeId(out));
        }
        // Atlas/headless parity: when terrain probes are absent, synthesize authority values
        // that satisfy polarMountainAuthority() for noise-confirmed mountain cells.
        // POLAR_AUTHORITY_PARITY_DELTA / _HEIGHT match the existing authority thresholds exactly.
        // Double-gated: same conditions as the polarTerrainMountainLike fix above.
        boolean polarAtlasMountainParity = !hasPreviewTerrainInputs
                && isAtlasHeadlessContext(callerContext)
                && polarMountainNoiseLike;
        int effectivePolarHeight = polarAtlasMountainParity ? POLAR_AUTHORITY_PARITY_HEIGHT : polarProbeHeight;
        int effectivePolarDelta  = polarAtlasMountainParity ? POLAR_AUTHORITY_PARITY_DELTA  : polarProbeDelta;
        // Capture pre-clamp state so instrumentation comparison is unambiguous.
        RegistryEntry<Biome> preClampOut = out;
        if (DEBUG_POLAR_CAP_TRACE && landBandIndex == BAND_POLAR && isPolarCapTraceCoord(blockX, blockZ)) {
            double traceLatDeg = latitudeDegreesFromRadius(blockZ, effectiveRadius);
            LOGGER.info("[LAT][POLAR_CAP_TRACE][preClamp] ctx={} x={} z={} latDeg={} preClamp={} extremeCap={} softLeak={} isAlpine={} mtnAuth={} effH={} effD={}",
                    callerContext, blockX, blockZ, String.format("%.1f", traceLatDeg), biomeId(out),
                    isExtremePolarCap(traceLatDeg), isExtremePolarSoftColdLeak(out),
                    isPolarAlpineBiome(out), polarMountainAuthority(effectivePolarDelta, effectivePolarHeight, landBandIndex),
                    effectivePolarHeight, effectivePolarDelta);
        }
        if (DEBUG_POLAR_ATLAS && landBandIndex == BAND_POLAR && isAtlasHeadlessContext(callerContext)) {
            PAR_SAMPLES.incrementAndGet();
            if (polarMountainNoiseLike)       PAR_NOISE_MOUNTAIN.incrementAndGet();
            if (preview.centerHeight > 0)     PAR_NONZERO_HEIGHT.incrementAndGet();
            if (preview.robustDelta > 0)      PAR_NONZERO_DELTA.incrementAndGet();
            if (polarMountainAuthority(effectivePolarDelta, effectivePolarHeight, landBandIndex))
                                              PAR_MOUNTAIN_AUTHORITY.incrementAndGet();
            if (isPolarAlpineBiome(out))      PAR_INITIAL_ALPINE.incrementAndGet();
            if (polarAtlasMountainParity && PAR_PARITY_HIT_LOG.incrementAndGet() <= 5) {
                LOGGER.info("[LAT][POLAR_ATLAS_PARITY_HIT] ctx={} x={} z={} initialBiome={} effectiveH={} effectiveD={}",
                        callerContext, blockX, blockZ, biomeId(out), effectivePolarHeight, effectivePolarDelta);
            }
        }
        double finalLatDeg = latitudeDegreesFromRadius(blockZ, effectiveRadius);
        out = clampFinalPolarNonMountainAlpineOutput(biomeRegistry, out, landBandIndex,
                finalLatDeg,
                effectivePolarHeight,
                effectivePolarDelta);
        if (DEBUG_POLAR_CAP_TRACE && landBandIndex == BAND_POLAR && isPolarCapTraceCoord(blockX, blockZ)) {
            LOGGER.info("[LAT][POLAR_CAP_TRACE][postClamp] ctx={} x={} z={} result={}",
                    callerContext, blockX, blockZ, biomeId(out));
        }
        if (DEBUG_POLAR_ATLAS && landBandIndex == BAND_POLAR && isAtlasHeadlessContext(callerContext)) {
            if (isPolarAlpineBiome(out))      PAR_FINAL_ALPINE.incrementAndGet();
            if (isBiomeId(out, "minecraft:snowy_plains") && !isBiomeId(preClampOut, "minecraft:snowy_plains"))
                                              PAR_REWRITTEN_SNOWY.incrementAndGet();
            long n = PAR_SAMPLES.get();
            if (n == 1 || (n & 8191L) == 0) {
                LOGGER.info("[LAT][POLAR_ATLAS_REPORT] ctx={} n={} noiseMtn={} nonzeroH={} nonzeroD={} authority={} initAlpine={} finalAlpine={} rewroteSnowy={}",
                        callerContext, n, PAR_NOISE_MOUNTAIN.get(), PAR_NONZERO_HEIGHT.get(), PAR_NONZERO_DELTA.get(),
                        PAR_MOUNTAIN_AUTHORITY.get(), PAR_INITIAL_ALPINE.get(), PAR_FINAL_ALPINE.get(),
                        PAR_REWRITTEN_SNOWY.get());
            }
        }
        out = gateWarmJungleSurvival(biomeRegistry, out, landBandIndex, blockX, blockZ);
        out = gateDryWarmIdentity(biomeRegistry, out, landBandIndex, blockX, blockZ);
        out = gatePolarTaigaSurvival(biomeRegistry, out, landBandIndex, finalLatDeg, blockX, blockZ);
        RegistryEntry<Biome> beforeLateWetlandClamp = out;
        out = clampLateWetlandSurvival(biomeRegistry, out, base, blockX, blockZ, t, landBandIndex, mountainLike, oceanDistance);
        if (!sameBiomeId(beforeLateWetlandClamp, out)) {
            if (isSwampCandidate(beforeLateWetlandClamp)) {
                swampFallbackCalled = true;
                swampFallbackReturned = out;
            }
            if (isMangroveCandidate(beforeLateWetlandClamp)) {
                mangroveFallbackCalled = true;
                mangroveFallbackReturned = out;
            }
        }
        logWetlandAudit("pick-registry-late",
                callerContext,
                base,
                blockX,
                blockZ,
                landBandIndex,
                t,
                finalLatDeg,
                mountainLike,
                preview.robustDelta,
                oceanDistance,
                skipPreview,
                preBandEnforce,
                postPoolEnforce,
                sanitized,
                out,
                swampFallbackCalled,
                swampFallbackReturned,
                mangroveFallbackCalled,
                mangroveFallbackReturned);
        boolean mountainLikeAfterFinalTruth = isMountainLike(sampler, blockX, blockZ);
        logWarmWindsweptLatePath("pick-registry-late",
                base,
                blockX,
                blockZ,
                landBandIndex,
                mountainLike,
                mountainLikeAfterFinalTruth,
                temperateMountainRewriteRan,
                chosen,
                sanitized,
                preBandEnforce,
                postPoolEnforce,
                finalSavannaClampRan,
                beforeFinalSavannaClamp,
                postFinalSavannaClamp,
                out);
        logWarmPoolMembershipFinalPoint("pick-registry-late",
                base,
                blockX,
                blockZ,
                t,
                landBandIndex,
                mountainLike,
                mountainLikeAfterFinalTruth,
                chosen,
                sanitized,
                preBandEnforce,
                postPoolEnforce,
                swampCandidateAfterEnforce,
                swampValidationFailed,
                swampFallbackCalled,
                swampFallbackReturned,
                out);
        debugPick(blockX, blockZ, effectiveRadius, t, band, base, out, false, out != sanitized, mangroveDecision);
        return out;
    }

    public static RegistryEntry<Biome> pick(Collection<RegistryEntry<Biome>> biomePool, RegistryEntry<Biome> base, int blockX, int blockZ, int blockY, int borderRadiusBlocks,
                                            MultiNoiseUtil.MultiNoiseSampler sampler, String callerContext) {
        return pick(biomePool, base, blockX, blockZ, blockY, borderRadiusBlocks, sampler, callerContext, null, null, null);
    }

    public static RegistryEntry<Biome> pick(Collection<RegistryEntry<Biome>> biomePool, RegistryEntry<Biome> base, int blockX, int blockZ, int blockY, int borderRadiusBlocks,
                                            MultiNoiseUtil.MultiNoiseSampler sampler, String callerContext,
                                            NoiseChunkGenerator generator, NoiseConfig noiseConfig, HeightLimitView heightView) {
        int columnDecisionY = surfaceDecisionY(generator, noiseConfig, heightView, blockX, blockZ);
        int biomeY = (blockY < columnDecisionY - 16) ? blockY : columnDecisionY;
        assertSurfaceY(biomeY);
        int activeRadius = ACTIVE_RADIUS_BLOCKS;
        boolean overrideDisabled = Boolean.getBoolean("latitude.disableRadiusOverride");

        if (activeRadius > 0 && borderRadiusBlocks != activeRadius && RADIUS_MISMATCH_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn("[Latitude] RADIUS MISMATCH detected from {}! Arg: {}, Active: {}", callerContext, borderRadiusBlocks, activeRadius);
        }

        int effectiveRadius = (!overrideDisabled && activeRadius > 0) ? activeRadius : borderRadiusBlocks;
        if (effectiveRadius <= 0) {
            return base;
        }

        LAST_SELECTION_PATH.remove();
        logTagPools(biomePool);

        int lat = Math.abs(blockZ);
        double tBase = (double) lat / (double) effectiveRadius;
        double t = applyBoundaryJitter(blockX, blockZ, effectiveRadius, tBase);
        LatitudeBands.Band band = bandForAbsLatFraction(t);
        int bandIndex = bandIndexForBand(band);

        if (isBeachLike(base) && allowBeachShortcut(generator, columnDecisionY)) {
            RegistryEntry<Biome> out = pickBeachForBand(biomePool, base, blockX, blockZ, bandIndex);
            debugPick(blockX, blockZ, effectiveRadius, t, band, base, out, true, false, null);
            return out;
        }

        // Compute blended band index once; shared by river, ocean, and land so all three
        // use the same 1408-block stochastic transition zone instead of a hard integer snap.
        int blendedBandIndex = latitudeBandIndexWithBlend(blockX, blockZ, effectiveRadius, band, t);

        int landBandIndex = blendedBandIndex;
        boolean mountainNoiseLike = landBandIndex == BAND_TEMPERATE && isMountainLike(sampler, blockX, blockZ);
        boolean skipPreview = shouldSkipPreviewTerrain(callerContext);
        boolean hasReliableSurface = !skipPreview && generator != null && noiseConfig != null && heightView != null;
        // True only when the caller supplied all three preview probe inputs (MIXIN, CAVE_CLAMP).
        // False for atlas/headless callers (SOURCE, ATLAS_SAMPLER) that null out noiseConfig/heightView.
        boolean hasPreviewTerrainInputs = generator != null && noiseConfig != null && heightView != null;
        boolean allowSurfaceGates = hasReliableSurface;
        PreviewTerrain preview = skipPreview
                ? syntheticPreviewTerrain(mountainNoiseLike, generator)
                : previewTerrain(generator, noiseConfig, heightView, blockX, blockZ);
        int seaLevel = previewSeaLevel(generator);
        boolean previewHeightHigh = preview.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS);
        boolean previewRuggedHigh = preview.robustDelta >= WINDSWEPT_RUGGED_THRESH;
        boolean previewHeightModerate = preview.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS / 2);
        // Temperate terrain-authority probe: syntheticPreviewTerrain is noise-circular for temperate —
        // it sets height/ruggedness high when mountainNoiseLike=true, making mountainLike trivially
        // equal to mountainNoiseLike (no actual terrain confirmation).  When real terrain inputs are
        // available (MIXIN/CAVE_CLAMP), do a targeted probe so terrain must confirm before admission,
        // mirroring the polar probe below.  Atlas/headless callers (hasPreviewTerrainInputs=false)
        // remain noise-only, matching polar in atlas context.
        if (skipPreview && landBandIndex == BAND_TEMPERATE && mountainNoiseLike && hasPreviewTerrainInputs) {
            PreviewTerrain temperateProbe = previewTerrain(generator, noiseConfig, heightView, blockX, blockZ);
            previewHeightHigh     = temperateProbe.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS);
            previewRuggedHigh     = temperateProbe.robustDelta >= WINDSWEPT_RUGGED_THRESH;
            previewHeightModerate = temperateProbe.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS / 2);
        }
        boolean mountainLike = mountainNoiseLike && (previewHeightHigh || previewHeightModerate || previewRuggedHigh);
        boolean polarMountainNoiseLike = sampler != null && isMountainLike(sampler, blockX, blockZ);
        // Atlas/headless parity: when real terrain probes are absent, allow the noise signal to
        // satisfy the terrain gate as a substitute for the missing preview terrain inputs.
        // Double-gated: !hasPreviewTerrainInputs (only SOURCE/ATLAS_SAMPLER paths, per call-site audit)
        // AND isAtlasHeadlessContext (explicit context gate to prevent silent future breakage).
        //
        // Live-worldgen bridge (skipPreview && hasPreviewTerrainInputs && polar noise-mountain):
        // syntheticPreviewTerrain uses temperate-gated mountainNoiseLike, which is always false
        // for polar band, producing flat placeholder values that suppress polarTerrainMountainLike.
        // When the noise says "mountain" in a polar cell, do a targeted real-terrain probe
        // (same pattern as the post-pick terrain gate at the subpolar/polar skipPreview block)
        // so that actual mountain terrain can earn polar mountain authority.
        int polarProbeHeight = preview.centerHeight;
        int polarProbeDelta  = preview.robustDelta;
        if (skipPreview && landBandIndex >= BAND_POLAR && polarMountainNoiseLike && hasPreviewTerrainInputs) {
            PreviewTerrain polarProbe = previewTerrain(generator, noiseConfig, heightView, blockX, blockZ);
            polarProbeHeight = polarProbe.centerHeight;
            polarProbeDelta  = polarProbe.robustDelta;
        }
        boolean polarTerrainMountainLike = (!hasPreviewTerrainInputs && isAtlasHeadlessContext(callerContext) && polarMountainNoiseLike)
                || (polarProbeDelta >= 12)
                || (polarProbeHeight >= seaLevel + 20);
        boolean polarMountainLikeFinal = polarMountainNoiseLike && polarTerrainMountainLike;
        if (landBandIndex >= BAND_POLAR && polarMountainLikeFinal) {
            mountainLike = true;
        }
        int oceanDistance = oceanDistanceBlocks(blockX, blockZ, sampler);
        boolean nearOcean = oceanDistance <= MANGROVE_COASTAL_MAX_BLOCKS;
        boolean oceanAuthority = oceanDistance == 0;
        // Veto coarse ODF ocean authority when real terrain is clearly raised land
        if (oceanAuthority && !base.isIn(BiomeTags.IS_OCEAN)
                && generator != null && noiseConfig != null && heightView != null) {
            int realHeight = previewHeight(generator, noiseConfig, heightView, blockX & ~3, blockZ & ~3);
            if (realHeight >= seaLevel) {
                oceanAuthority = false;
            }
        }

        if (base.isIn(BiomeTags.IS_RIVER)) {
            if (blendedBandIndex >= 3) {
                RegistryEntry<Biome> frozen = entryById(biomePool, "minecraft:frozen_river");
                RegistryEntry<Biome> out = frozen != null ? frozen : base;
                debugPick(blockX, blockZ, effectiveRadius, t, band, base, out, false, false, null);
                return out;
            }
            RegistryEntry<Biome> river = entryById(biomePool, "minecraft:river");
            RegistryEntry<Biome> out = river != null ? river : base;
            debugPick(blockX, blockZ, effectiveRadius, t, band, base, out, false, false, null);
            return out;
        }

        if (base.isIn(BiomeTags.IS_OCEAN) || oceanAuthority) {
            RegistryEntry<Biome> oceanBase = base.isIn(BiomeTags.IS_OCEAN)
                    ? base
                    : entryById(biomePool, "minecraft:ocean");
            if (oceanBase == null) {
                oceanBase = base;
            }
            RegistryEntry<Biome> oceanPick = oceanByLatitudeBandOrBase(biomePool, oceanBase, blockX, blockZ, blendedBandIndex);
            if (oceanPick == null || !oceanPick.isIn(BiomeTags.IS_OCEAN)) {
                oceanPick = firstPresentOcean(biomePool);
            }
            RegistryEntry<Biome> out = mushroomIslandOverride(biomePool, oceanPick, blockX, blockZ);
            debugPick(blockX, blockZ, effectiveRadius, t, band, base, out, false, false, null);
            return out;
        }

        if (skipPreview && PREVIEW_TERRAIN_SKIP_LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[Latitude] skipping previewHeight() for callerContext={} (atlas fast-path enabled)", callerContext);
        }
        boolean forcedBadlands = false;
        RegistryEntry<Biome> chosen = null;
        if (chosen == null && (landBandIndex == BAND_TROPICAL || landBandIndex == BAND_SUBTROPICAL) && sampler != null) {
            int noiseX = blockX >> 2;
            int noiseZ = blockZ >> 2;
            MultiNoiseUtil.NoiseValuePoint p = sampler.sample(noiseX, SURFACE_CLASSIFY_Y >> 2, noiseZ);
            double cont = MultiNoiseUtil.toFloat(p.continentalnessNoise());
            double erosion = MultiNoiseUtil.toFloat(p.erosionNoise());
            double weird = MultiNoiseUtil.toFloat(p.weirdnessNoise());
            boolean aridBlocked = isAridTropicalStepSymmetric(blockX, blockZ, t);
            boolean swampPatch = swampPatchHere(WORLD_SEED, blockX, blockZ);
            boolean swampPatchOk = swampOkInPatchScaled(cont, erosion, weird);
            double wetlandNoise = wetlandNoiseSymmetric(WORLD_SEED, blockX, blockZ);
            double wetlandThreshold = scaledWetlandThresholdForBand(bandIndex, t);
            boolean subtropicalCoastalOk = landBandIndex != BAND_SUBTROPICAL
                    || oceanDistance <= SWAMP_SUBTROPICAL_PATCH_MAX_OCEAN_DISTANCE;
            if (!aridBlocked
            && subtropicalCoastalOk
            && swampPatch
            && swampPatchOk
            && wetlandNoise < wetlandThreshold) {
                chosen = entryById(biomePool, SWAMP_ID);
            }
        }
        if (chosen == null) {
            chosen = switch (landBandIndex) {
                case BAND_TROPICAL -> pickFromWeightedTags(biomePool, base, blockX, blockZ, BAND_TROPICAL, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
                case BAND_SUBTROPICAL -> pickTropicalGradient(biomePool, base, blockX, blockZ, t);
                case BAND_TEMPERATE -> pickTemperateLand(biomePool, blockX, blockZ, columnDecisionY,
                        () -> pickFromWeightedTags(biomePool, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT),
                        mountainLike);
                case BAND_SUBPOLAR -> pickSubpolarWithRamp(biomePool, base, blockX, blockZ, t, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
                default -> pickPolarWithFrontShoulder(
                        biomePool,
                        base,
                        blockX,
                        blockZ,
                        t,
                        polarMountainLikeFinal,
                        preview.centerHeight,
                        preview.robustDelta,
                        seaLevel,
                        polarMountainLikeFinal,
                        mountainLike,
                        oceanDistance);
            };
        }
        if (landBandIndex == BAND_TEMPERATE
                && chosen != null
                && isBiomeId(base, "minecraft:dark_forest")
                && !isBiomeId(chosen, "minecraft:dark_forest")
                && !isBiomeId(chosen, "minecraft:pale_garden") // pale_garden is a valid dark_forest replacement
                && isTemperateForestFamily(chosen)
                && (paleGardenRegionHit(WORLD_SEED, blockX, blockZ, effectiveRadius)
                    || ValueNoise2D.sampleBlocks(WORLD_SEED ^ DARK_FOREST_RESTORE_DENSITY_SALT, blockX, blockZ, DARK_FOREST_RESTORE_DENSITY_SCALE) < DARK_FOREST_RESTORE_DENSITY_THRESHOLD)) {
            chosen = base;
        }
        if (landBandIndex == BAND_TEMPERATE
                && !mountainLike
                && chosen != null
                && isTemperateForestFamily(base)
                && !isTemperateForestFamily(chosen)
                && (isBiomeId(chosen, "minecraft:plains")
                || isBiomeId(chosen, "minecraft:taiga")
                || isBiomeId(chosen, "minecraft:old_growth_pine_taiga"))) {
            chosen = base;
        }
        if (skipPreview && landBandIndex >= BAND_SUBPOLAR && chosen != null) {
            int gateHeight = preview.centerHeight;
            int gateDelta = preview.robustDelta;
            if (generator != null && noiseConfig != null && heightView != null) {
                gateHeight = previewHeight(generator, noiseConfig, heightView, blockX & ~3, blockZ & ~3);
            }
            chosen = applyTerrainCompatibilityGate(
                    biomePool,
                    chosen,
                    landBandIndex,
                    blockX,
                    blockZ,
                    gateHeight,
                    gateDelta,
                    seaLevel,
                    oceanDistance,
                    mountainNoiseLike,
                    mountainLike);
        } else if (!skipPreview && landBandIndex >= BAND_SUBPOLAR && chosen != null) {
            chosen = applyTerrainCompatibilityGate(
                    biomePool,
                    chosen,
                    landBandIndex,
                    blockX,
                    blockZ,
                    preview.centerHeight,
                    preview.robustDelta,
                    seaLevel,
                    oceanDistance,
                    mountainNoiseLike,
                    mountainLike);
        }
        String mangroveDecision = null;
        RegistryEntry<Biome> sanitized = chosen;
        RegistryEntry<Biome> safe = chosen;
        RegistryEntry<Biome> out = chosen;
        boolean temperateMountainRewriteRan = false;
        boolean finalSavannaRegion = false;
        boolean invitedMangrove = false;
        if (!forcedBadlands) {
            boolean oceanChosen = chosen != null && chosen.isIn(BiomeTags.IS_OCEAN);
            if (oceanChosen) {
                sanitized = chosen;
                safe = chosen;
                out = chosen;
            } else {
            if (shouldTryMangroveOverride(chosen, landBandIndex)) {
                MangroveDecision decision = evaluateMangroveWithSurface(blockX, blockZ, columnDecisionY, preview, seaLevel, sampler, nearOcean, hasReliableSurface, hasPreviewTerrainInputs, heightView);
                if (Boolean.getBoolean("latitude.audit.mangroveFinal")) {
                    LOGGER.info("[mangrove-final-live] x={} z={} resultBiome={}",
                            blockX, blockZ, biomeId(chosen));
                }
                mangroveDecision = decision.logLabel();
                if (decision.allow()) {
                    RegistryEntry<Biome> mangrove = entryById(biomePool, MANGROVE_ID);
                    if (mangrove != null) {
                        RegistryEntry<Biome> before = chosen;
                        chosen = mangrove;
                        if (Boolean.getBoolean("latitude.audit.mangroveFinal")) {
                            LOGGER.info("[mangrove-rewrite-live] x={} z={} old={} new={}",
                                    blockX, blockZ, biomeId(before), biomeId(chosen));
                        }
                        if (DEBUG_MANGROVE_ORIGIN) {
                            LOGGER.info("[latdev] mangroveSelected x={} z={} surfaceY={} sea={} robustDelta={} origin={} reason={}",
                                    blockX, blockZ, preview.centerHeight, seaLevel, preview.robustDelta,
                                    mangroveOrigin(false, true), mangroveDecision);
                        }
                    }
                }
            } else if (isMangroveCandidate(chosen)) {
                MangroveDecision decision = evaluateMangroveWithSurface(blockX, blockZ, columnDecisionY, preview, seaLevel, sampler, nearOcean, hasReliableSurface, hasPreviewTerrainInputs, heightView);
                if (Boolean.getBoolean("latitude.audit.mangroveFinal")) {
                    LOGGER.info("[mangrove-final-live] x={} z={} resultBiome={}",
                            blockX, blockZ, biomeId(chosen));
                }
                mangroveDecision = decision.logLabel();
                if (!decision.allow()) {
                    RegistryEntry<Biome> before = chosen;
                    chosen = pickMangroveFallback(biomePool, base, blockX, blockZ, t, landBandIndex);
                    if (Boolean.getBoolean("latitude.audit.mangroveFinal")) {
                        LOGGER.info("[mangrove-rewrite-live] x={} z={} old={} new={}",
                                blockX, blockZ, biomeId(before), biomeId(chosen));
                    }
                }
            }
            if (isSwampCandidate(chosen)) {
                SwampDecision decision = evaluateSwamp(blockX, blockZ, sampler);
                if (!decision.allow()) {
                    chosen = pickSwampFallback(biomePool, base, blockX, blockZ, t, landBandIndex);
                }
            }
            if (!isMangroveCandidate(chosen) && shouldInviteMangrove(blockX, columnDecisionY, blockZ, bandIndex, sampler, nearOcean)) {
                invitedMangrove = true;
                MangroveDecision decision = evaluateMangroveWithSurface(blockX, blockZ, columnDecisionY, preview, seaLevel, sampler, nearOcean, hasReliableSurface, hasPreviewTerrainInputs, heightView);
                if (Boolean.getBoolean("latitude.audit.mangroveFinal")) {
                    LOGGER.info("[mangrove-final-live] x={} z={} resultBiome={}",
                            blockX, blockZ, biomeId(chosen));
                }
                mangroveDecision = decision.logLabel();
                if (decision.allow()) {
                    RegistryEntry<Biome> mangrove = entryById(biomePool, MANGROVE_ID);
                    if (mangrove != null) {
                        RegistryEntry<Biome> before = chosen;
                        chosen = mangrove;
                        if (Boolean.getBoolean("latitude.audit.mangroveFinal")) {
                            LOGGER.info("[mangrove-rewrite-live] x={} z={} old={} new={}",
                                    blockX, blockZ, biomeId(before), biomeId(chosen));
                        }
                        if (DEBUG_MANGROVE_INVITE) {
                            LOGGER.info("[latdev] mangroveInvite ACCEPT x={} z={} oceanDist={} decision={}", blockX, blockZ, oceanDistance, mangroveDecision);
                        }
                    }
                } else if (DEBUG_MANGROVE_INVITE) {
                    LOGGER.info("[latdev] mangroveInvite REJECT x={} z={} oceanDist={} decision={}", blockX, blockZ, oceanDistance, mangroveDecision);
                }
            }
            // Guard: polar land has its own mountain picker (pickPolarWithFrontShoulder).
            // See parallel Registry<Biome> overload for full rationale.
            boolean mountainPromotion = mountainLike
                    && landBandIndex < BAND_POLAR
                    && (preview.robustDelta >= (WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST)
                    || preview.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS));
            if (mountainPromotion) {
                temperateMountainRewriteRan = true;
                chosen = pickFromTagNoiseOrBase(biomePool, LAT_TEMPERATE_MOUNTAIN, base, blockX, blockZ, landBandIndex);
                if (isBiomeId(chosen, "minecraft:cherry_grove") && landBandIndex < BAND_POLAR) {
                    return chosen;
                }
            }
            sanitized = sanitizeLandBiome(biomePool, chosen, landBandIndex, blockX, blockZ);
            if (DEBUG_POLAR_CAP_TRACE && landBandIndex == BAND_POLAR && isPolarCapTraceCoord(blockX, blockZ)) {
                LOGGER.info("[LAT][POLAR_CAP_TRACE][sanitize] ctx={} x={} z={} chosen={} -> sanitized={}",
                        callerContext, blockX, blockZ, biomeId(chosen), biomeId(sanitized));
            }
            safe = repickIfSurfaceCave(biomePool, base, sanitized, blockX, blockZ, t, landBandIndex);
            out = applyLandOverrides(biomePool, safe, blockX, blockZ, landBandIndex);
            if (landBandIndex == BAND_TROPICAL && isJungleFamily(out) && !allowWetTropicalCanopy(blockX, blockZ, t, out)) {
                out = pickOpenTropicalFallback(biomePool, out, blockX, blockZ, t);
            }
            boolean savannaGateInput = isSavannaFamily(out);
            int savannaRobustDelta = preview.robustDelta;
            boolean savannaUpland = previewHeightHigh;
            // MIXIN and CAVE_CLAMP must not re-enter real previewTerrain here — stay on the synthetic path.
            boolean forceSyntheticTerrain = skipPreview && ("MIXIN".equalsIgnoreCase(callerContext) || "CAVE_CLAMP".equalsIgnoreCase(callerContext));
            if (skipPreview && savannaGateInput && generator != null && noiseConfig != null && heightView != null && !forceSyntheticTerrain) {
                PreviewTerrain wsavPreview = previewTerrain(generator, noiseConfig, heightView, blockX, blockZ);
                savannaRobustDelta = wsavPreview.robustDelta;
                savannaUpland = wsavPreview.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS);
                if (DEBUG_SAVANNA_GATE_AUDIT) {
                    SAVANNA_AUDIT_REAL_PREVIEW.incrementAndGet();
                }
            } else if (skipPreview && savannaGateInput && sampler != null) {
                boolean ruggedNoise = isMountainLike(sampler, blockX, blockZ);
                if (ruggedNoise) {
                    savannaRobustDelta = WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST;
                }
                if (DEBUG_SAVANNA_GATE_AUDIT) {
                    SAVANNA_AUDIT_PREVIEW_MISSING.incrementAndGet();
                }
            } else if (skipPreview && savannaGateInput && DEBUG_SAVANNA_GATE_AUDIT) {
                SAVANNA_AUDIT_PREVIEW_MISSING.incrementAndGet();
            }
            out = applySavannaWindsweptGate(biomePool, out, savannaRobustDelta, savannaUpland, blockX, blockZ, callerContext, landBandIndex);
            if (landBandIndex == BAND_SUBTROPICAL && isJungleFamily(out)) {
                out = pickDryWarmFallback(biomePool, out);
            }
            finalSavannaRegion = isSavannaFamily(base) || savannaGateInput || isSavannaFamily(out);
            }
        }
        if (landBandIndex == BAND_TROPICAL || landBandIndex == BAND_SUBTROPICAL) {
            if (isColdBiome(out)) {
                out = pickWarmFallback(biomePool, landBandIndex);
            }
        }
        out = enforceSnowyLatitudeRamp(biomePool, out, base, blockX, blockZ, effectiveRadius, landBandIndex);
        out = clampWarmInColdZone(biomePool, base, out, band, blockX, blockZ);
        out = applySubpolarSwampGuard(biomePool, base, out, band);
        RegistryEntry<Biome> preBandEnforce = out;
        if (landBandIndex >= BAND_SUBPOLAR && isJungleFamily(out)) {
            out = pickColdFallback(biomePool, base, blockX, blockZ, landBandIndex);
        }
        if (DEBUG_WARM_POOL_MEMBERSHIP) {
            LAST_WARM_POOL_MEMBERSHIP_SNAPSHOT.remove();
        }
        out = enforceLandBandPool(biomePool, out, blockX, blockZ, t, landBandIndex, mountainLike);
        RegistryEntry<Biome> postPoolEnforce = out;
        boolean swampCandidateAfterEnforce = isSwampCandidate(out);
        boolean swampValidationFailed = false;
        boolean swampFallbackCalled = false;
        RegistryEntry<Biome> swampFallbackReturned = null;
        boolean mangroveFallbackCalled = false;
        RegistryEntry<Biome> mangroveFallbackReturned = null;
        if (swampCandidateAfterEnforce) {
            SwampDecision poolSwampDecision = evaluateSwamp(blockX, blockZ, sampler);
            if (!poolSwampDecision.allow()) {
                swampValidationFailed = true;
                swampFallbackCalled = true;
                out = pickSwampFallback(biomePool, base, blockX, blockZ, t, landBandIndex);
                swampFallbackReturned = out;
            }
        }
        // Mangrove guard: enforceLandBandPool can re-introduce mangrove (MANGROVE_ID is in the
        // BAND_SUBTROPICAL allowed-extras list). Re-evaluate and reject if surface/coastal gates fail.
        if (isMangroveCandidate(out)) {
            MangroveDecision poolMangroveDecision = evaluateMangroveWithSurface(blockX, blockZ, columnDecisionY, preview, seaLevel, sampler, nearOcean, hasReliableSurface, hasPreviewTerrainInputs, heightView);
            if (!poolMangroveDecision.allow()) {
                mangroveFallbackCalled = true;
                out = pickMangroveFallback(biomePool, base, blockX, blockZ, t, landBandIndex);
                mangroveFallbackReturned = out;
            }
        }
        if (isSnowyVariant(out)) {
            if (landBandIndex == BAND_SUBTROPICAL && !mountainLike) {
                RegistryEntry<Biome> warmFallback = pickWarmFallback(biomePool, landBandIndex);
                if (warmFallback != null) {
                    out = warmFallback;
                }
            } else {
                double _bgDeg = latitudeDegreesFromRadius(blockZ, effectiveRadius);
                double _bgAlpha = snowyRampAlpha(_bgDeg);
                double _bgR = ValueNoise2D.sampleBlocks(WORLD_SEED ^ SNOWY_RAMP_SALT, blockX, blockZ, SNOWY_RAMP_PATCH_BLOCKS);
                if (_bgR > _bgAlpha) {
                    RegistryEntry<Biome> taigaFallback = entryById(biomePool, "minecraft:taiga");
                    if (taigaFallback != null) {
                        out = taigaFallback;
                    }
                }
            }
        }
        out = enforcePaleGardenRegion(biomePool, out, base, blockX, blockZ, landBandIndex, effectiveRadius);
        out = softenTemperateWarmEdgeTaigaJump(biomePool, base, out, blockX, blockZ, effectiveRadius, bandIndex, landBandIndex, mountainLike);
        RegistryEntry<Biome> postBandEnforce = out;
        if (DEBUG_BIOMES && isMangroveCandidate(out)) {
            LOGGER.warn("[Latitude][MangroveLeak] mangrove escaped into land pool result (collection path) at x={} z={} bandIndex={} y={}",
                    blockX, blockZ, landBandIndex, columnDecisionY);
        }
        if (landBandIndex == BAND_TROPICAL && tropicalBaseStep(blockX, Math.abs(blockZ), t) <= 1 && isJungleFamily(out)) {
            out = pickOpenTropicalFallback(biomePool, out, blockX, blockZ, t);
        }
        RegistryEntry<Biome> beforeFinalSavannaClamp = out;
        boolean finalSavannaClampRan = false;
        if (landBandIndex <= BAND_SUBTROPICAL) {
            finalSavannaClampRan = true;
            out = applyFinalSavannaClimateClamp(biomePool, out, finalSavannaRegion, landBandIndex, columnDecisionY, blockX, blockZ);
        }
        RegistryEntry<Biome> postFinalSavannaClamp = out;
        RegistryEntry<Biome> postFinalClamp = out;
        int overlayBandIndex = authoritativeLandBandIndex(blockX, blockZ, effectiveRadius);
        logSubtropicalJungleReturn("pick-collection", blockX, blockZ, t, landBandIndex, base, chosen, sanitized, preBandEnforce, postBandEnforce, postFinalClamp, out);
        logAtlasViewportJungleReturn("pick-collection", callerContext, blockX, blockZ, t, landBandIndex, overlayBandIndex, base, chosen, sanitized, preBandEnforce, postBandEnforce, postFinalClamp, out);
        traceSubpolarJunglePick(blockX, blockZ, effectiveRadius, landBandIndex, base, out);
        // Atlas/headless parity: when terrain probes are absent, synthesize authority values
        // that satisfy polarMountainAuthority() for noise-confirmed mountain cells.
        // POLAR_AUTHORITY_PARITY_DELTA / _HEIGHT match the existing authority thresholds exactly.
        // Double-gated: same conditions as the polarTerrainMountainLike fix above.
        boolean polarAtlasMountainParity = !hasPreviewTerrainInputs
                && isAtlasHeadlessContext(callerContext)
                && polarMountainNoiseLike;
        int effectivePolarHeight = polarAtlasMountainParity ? POLAR_AUTHORITY_PARITY_HEIGHT : polarProbeHeight;
        int effectivePolarDelta  = polarAtlasMountainParity ? POLAR_AUTHORITY_PARITY_DELTA  : polarProbeDelta;
        // Capture pre-clamp state so instrumentation comparison is unambiguous.
        RegistryEntry<Biome> preClampOut = out;
        if (DEBUG_POLAR_CAP_TRACE && landBandIndex == BAND_POLAR && isPolarCapTraceCoord(blockX, blockZ)) {
            double traceLatDeg = latitudeDegreesFromRadius(blockZ, effectiveRadius);
            LOGGER.info("[LAT][POLAR_CAP_TRACE][preClamp] ctx={} x={} z={} latDeg={} preClamp={} extremeCap={} softLeak={} isAlpine={} mtnAuth={} effH={} effD={}",
                    callerContext, blockX, blockZ, String.format("%.1f", traceLatDeg), biomeId(out),
                    isExtremePolarCap(traceLatDeg), isExtremePolarSoftColdLeak(out),
                    isPolarAlpineBiome(out), polarMountainAuthority(effectivePolarDelta, effectivePolarHeight, landBandIndex),
                    effectivePolarHeight, effectivePolarDelta);
        }
        if (DEBUG_POLAR_ATLAS && landBandIndex == BAND_POLAR && isAtlasHeadlessContext(callerContext)) {
            PAR_SAMPLES.incrementAndGet();
            if (polarMountainNoiseLike)       PAR_NOISE_MOUNTAIN.incrementAndGet();
            if (preview.centerHeight > 0)     PAR_NONZERO_HEIGHT.incrementAndGet();
            if (preview.robustDelta > 0)      PAR_NONZERO_DELTA.incrementAndGet();
            if (polarMountainAuthority(effectivePolarDelta, effectivePolarHeight, landBandIndex))
                                              PAR_MOUNTAIN_AUTHORITY.incrementAndGet();
            if (isPolarAlpineBiome(out))      PAR_INITIAL_ALPINE.incrementAndGet();
            if (polarAtlasMountainParity && PAR_PARITY_HIT_LOG.incrementAndGet() <= 5) {
                LOGGER.info("[LAT][POLAR_ATLAS_PARITY_HIT] ctx={} x={} z={} initialBiome={} effectiveH={} effectiveD={}",
                        callerContext, blockX, blockZ, biomeId(out), effectivePolarHeight, effectivePolarDelta);
            }
        }
        double finalLatDeg = latitudeDegreesFromRadius(blockZ, effectiveRadius);
        out = clampFinalPolarNonMountainAlpineOutput(biomePool, out, landBandIndex,
                finalLatDeg,
                effectivePolarHeight,
                effectivePolarDelta);
        if (DEBUG_POLAR_CAP_TRACE && landBandIndex == BAND_POLAR && isPolarCapTraceCoord(blockX, blockZ)) {
            LOGGER.info("[LAT][POLAR_CAP_TRACE][postClamp] ctx={} x={} z={} result={}",
                    callerContext, blockX, blockZ, biomeId(out));
        }
        if (DEBUG_POLAR_ATLAS && landBandIndex == BAND_POLAR && isAtlasHeadlessContext(callerContext)) {
            if (isPolarAlpineBiome(out))      PAR_FINAL_ALPINE.incrementAndGet();
            if (isBiomeId(out, "minecraft:snowy_plains") && !isBiomeId(preClampOut, "minecraft:snowy_plains"))
                                              PAR_REWRITTEN_SNOWY.incrementAndGet();
            long n = PAR_SAMPLES.get();
            if (n == 1 || (n & 8191L) == 0) {
                LOGGER.info("[LAT][POLAR_ATLAS_REPORT] ctx={} n={} noiseMtn={} nonzeroH={} nonzeroD={} authority={} initAlpine={} finalAlpine={} rewroteSnowy={}",
                        callerContext, n, PAR_NOISE_MOUNTAIN.get(), PAR_NONZERO_HEIGHT.get(), PAR_NONZERO_DELTA.get(),
                        PAR_MOUNTAIN_AUTHORITY.get(), PAR_INITIAL_ALPINE.get(), PAR_FINAL_ALPINE.get(),
                        PAR_REWRITTEN_SNOWY.get());
            }
        }
        out = gateWarmJungleSurvival(biomePool, out, landBandIndex, blockX, blockZ);
        out = gateDryWarmIdentity(biomePool, out, landBandIndex, blockX, blockZ);
        out = gatePolarTaigaSurvival(biomePool, out, landBandIndex, finalLatDeg, blockX, blockZ);
        RegistryEntry<Biome> beforeLateWetlandClamp = out;
        out = clampLateWetlandSurvival(biomePool, out, base, blockX, blockZ, t, landBandIndex, mountainLike, oceanDistance);
        if (!sameBiomeId(beforeLateWetlandClamp, out)) {
            if (isSwampCandidate(beforeLateWetlandClamp)) {
                swampFallbackCalled = true;
                swampFallbackReturned = out;
            }
            if (isMangroveCandidate(beforeLateWetlandClamp)) {
                mangroveFallbackCalled = true;
                mangroveFallbackReturned = out;
            }
        }
        logWetlandAudit("pick-collection-late",
                callerContext,
                base,
                blockX,
                blockZ,
                landBandIndex,
                t,
                finalLatDeg,
                mountainLike,
                preview.robustDelta,
                oceanDistance,
                skipPreview,
                preBandEnforce,
                postPoolEnforce,
                sanitized,
                out,
                swampFallbackCalled,
                swampFallbackReturned,
                mangroveFallbackCalled,
                mangroveFallbackReturned);
        boolean mountainLikeAfterFinalTruth = isMountainLike(sampler, blockX, blockZ);
        logWarmWindsweptLatePath("pick-collection-late",
                base,
                blockX,
                blockZ,
                landBandIndex,
                mountainLike,
                mountainLikeAfterFinalTruth,
                temperateMountainRewriteRan,
                chosen,
                sanitized,
                preBandEnforce,
                postPoolEnforce,
                finalSavannaClampRan,
                beforeFinalSavannaClamp,
                postFinalSavannaClamp,
                out);
        logWarmPoolMembershipFinalPoint("pick-collection-late",
                base,
                blockX,
                blockZ,
                t,
                landBandIndex,
                mountainLike,
                mountainLikeAfterFinalTruth,
                chosen,
                sanitized,
                preBandEnforce,
                postPoolEnforce,
                swampCandidateAfterEnforce,
                swampValidationFailed,
                swampFallbackCalled,
                swampFallbackReturned,
                out);
        debugPick(blockX, blockZ, effectiveRadius, t, band, base, out, false, out != sanitized, mangroveDecision);
        return out;
    }

    private static RegistryEntry<Biome> pickTropicalGradient(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        boolean mountainLike = false;

        long seed = WORLD_SEED;
        ProvinceAuthority.Province warmProvince = warmProvinceClass(blockX, blockZ, BAND_SUBTROPICAL);
        if (warmProvince == ProvinceAuthority.Province.WARM_DRY) {
            return pickAridRegionFallback(biomes, base, blockX, blockZ);
        }

        // Subtropical band grades from wetter low latitudes to drier high latitudes.
        double bandStart = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
        double bandEnd = LatitudeBands.Band.TEMPERATE.lowDeg() / 90.0;
        double u = clamp((t - bandStart) / (bandEnd - bandStart), 0.0, 1.0);
        double ladderT = 1.0 - u;

        double tJitter = softenedTropicalLadderT(seed, blockX, blockZ, ladderT);

        double stepFloat = tJitter * 4.0;
        int baseStep = clampInt((int) Math.floor(stepFloat), 0, 3);
        double stepFrac = stepFloat - baseStep;
        int step = applyTropicalStepDither(seed, blockX, blockZ, baseStep, stepFrac);

        // Humidity-biased per-step diversion: humid patches within each ladder step
        double humidity = subtropicalHumidityNoise(blockX, blockZ);
        double humidThreshold = subtropicalHumidityThreshold(step);
        if (humidity < humidThreshold) {
            RegistryEntry<Biome> humidPick = pickFromWeightedTags(biomes, base, blockX, blockZ, 110 + step, 0x5B70 + step,
                    LAT_SUBTROPICAL_HUMID_PRIMARY, LAT_SUBTROPICAL_HUMID_SECONDARY, LAT_SUBTROPICAL_HUMID_ACCENT);
            RegistryEntry<Biome> humidOut = enforceWarmProvinceFamily(biomes, humidPick, warmProvince);
            return blockNewSubtropicalNonMountainWindswept(base, humidOut, mountainLike, BAND_SUBTROPICAL);
        }
        boolean coldShoulderArid = step == 0 && u >= SUBTROPICAL_ARID_SHOULDER_U;

        boolean plateauLike = step == 2 && t <= 0.325 && stepFrac >= 0.75;

        if (step == 2) {
            int roll = weightedRoll(blockX, blockZ, 0x7A22);
            TagKey<Biome> tag = weightedTagForRoll(102, roll,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            List<RegistryEntry<Biome>> candidates = new ArrayList<>();
            for (RegistryEntry<Biome> entry : biomes.iterateEntries(tag)) {
                candidates.add(entry);
            }
            RegistryEntry<Biome> forced = maybePickWsavStep2SecondaryOverride(biomes, step, plateauLike, candidates);
            if (forced != null) {
                return blockNewSubtropicalNonMountainWindswept(base, forced, mountainLike, BAND_SUBTROPICAL);
            }
        }

        RegistryEntry<Biome> pick = switch (step) {
            case 1 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT);
            case 2 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 102, 0x7A22,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            case 3 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 103, 0x7A33,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            default -> coldShoulderArid
                    ? pickFromWeightedTags(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT)
                    : pickFromWeightedTags(biomes, base, blockX, blockZ, 100, 0x7A00,
                    LAT_ARID_PRIMARY, LAT_ARID_SECONDARY, LAT_ARID_ACCENT);
        };
        RegistryEntry<Biome> softened = softenSubtropicalBadlands(biomes, base, pick);
        RegistryEntry<Biome> out = enforceWarmProvinceFamily(biomes, softened, warmProvince);
        recordWarmDryPath("TROPICAL_GRADIENT", base, out, blockX, blockZ, BAND_SUBTROPICAL, warmProvince);
        return blockNewSubtropicalNonMountainWindswept(base, out, mountainLike, BAND_SUBTROPICAL);
    }

    private static boolean isAridTropicalStep(int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        long seed = WORLD_SEED;

        double bandStart = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
        double bandEnd = LatitudeBands.Band.TEMPERATE.lowDeg() / 90.0;
        double u = clamp((t - bandStart) / (bandEnd - bandStart), 0.0, 1.0);
        double ladderT = 1.0 - u;

        double tJitter = softenedTropicalLadderT(seed, blockX, blockZ, ladderT);

        double stepFloat = tJitter * 4.0;
        int baseStep = clampInt((int) Math.floor(stepFloat), 0, 3);
        double stepFrac = stepFloat - baseStep;
        int step = applyTropicalStepDither(seed, blockX, blockZ, baseStep, stepFrac);

        return step == 0;
    }

    private static boolean isAridTropicalStepSymmetric(int blockX, int blockZ, double t) {
        int absZ = Math.abs(blockZ);
        return isAridTropicalStep(blockX, absZ, t);
    }

    // Returns the pre-dither tropical step (0-3) using only the 8-chunk jitter, ignoring fine dithering.
    // baseStep <= 1 means this location is in savanna/arid macro-territory.
    private static int tropicalBaseStep(int blockX, int absZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = absZ >> 4;
        long seed = WORLD_SEED;
        double bandStart = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
        double bandEnd = LatitudeBands.Band.TEMPERATE.lowDeg() / 90.0;
        double u = clamp((t - bandStart) / (bandEnd - bandStart), 0.0, 1.0);
        double ladderT = 1.0 - u;
        double tJitter = softenedTropicalLadderT(seed, blockX, absZ, ladderT);
        return clampInt((int) Math.floor(tJitter * 4.0), 0, 3);
    }

    private static RegistryEntry<Biome> pickTropicalGradient(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        boolean mountainLike = false;

        long seed = WORLD_SEED;
        ProvinceAuthority.Province warmProvince = warmProvinceClass(blockX, blockZ, BAND_SUBTROPICAL);
        if (warmProvince == ProvinceAuthority.Province.WARM_DRY) {
            return pickAridRegionFallback(biomes, base, blockX, blockZ);
        }

        // Subtropical band grades from wetter low latitudes to drier high latitudes.
        double bandStart = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
        double bandEnd = LatitudeBands.Band.TEMPERATE.lowDeg() / 90.0;
        double u = clamp((t - bandStart) / (bandEnd - bandStart), 0.0, 1.0);
        double ladderT = 1.0 - u;

        double tJitter = softenedTropicalLadderT(seed, blockX, blockZ, ladderT);

        double stepFloat = tJitter * 4.0;
        int baseStep = clampInt((int) Math.floor(stepFloat), 0, 3);
        double stepFrac = stepFloat - baseStep;
        int step = applyTropicalStepDither(seed, blockX, blockZ, baseStep, stepFrac);

        // Humidity-biased per-step diversion: humid patches within each ladder step
        double humidity = subtropicalHumidityNoise(blockX, blockZ);
        double humidThreshold = subtropicalHumidityThreshold(step);
        if (humidity < humidThreshold) {
            RegistryEntry<Biome> humidPick = pickFromWeightedTags(biomes, base, blockX, blockZ, 110 + step, 0x5B70 + step,
                    LAT_SUBTROPICAL_HUMID_PRIMARY, LAT_SUBTROPICAL_HUMID_SECONDARY, LAT_SUBTROPICAL_HUMID_ACCENT);
            RegistryEntry<Biome> humidOut = enforceWarmProvinceFamily(biomes, humidPick, warmProvince);
            return blockNewSubtropicalNonMountainWindswept(base, humidOut, mountainLike, BAND_SUBTROPICAL);
        }
        boolean coldShoulderArid = step == 0 && u >= SUBTROPICAL_ARID_SHOULDER_U;

        boolean plateauLike = step == 2 && t <= 0.325 && stepFrac >= 0.75;

        if (step == 2) {
            int roll = weightedRoll(blockX, blockZ, 0x7A22);
            TagKey<Biome> tag = weightedTagForRoll(102, roll,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            List<RegistryEntry<Biome>> candidates = entriesForTag(biomes, tag);
            RegistryEntry<Biome> forced = maybePickWsavStep2SecondaryOverride(biomes, step, plateauLike, candidates);
            if (forced != null) {
                return blockNewSubtropicalNonMountainWindswept(base, forced, mountainLike, BAND_SUBTROPICAL);
            }
        }

        RegistryEntry<Biome> pick = switch (step) {
            case 1 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT);
            case 2 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 102, 0x7A22,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            case 3 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 103, 0x7A33,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            default -> coldShoulderArid
                    ? pickFromWeightedTags(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT)
                    : pickFromWeightedTags(biomes, base, blockX, blockZ, 100, 0x7A00,
                    LAT_ARID_PRIMARY, LAT_ARID_SECONDARY, LAT_ARID_ACCENT);
        };
        RegistryEntry<Biome> softened = softenSubtropicalBadlands(biomes, base, pick);
        RegistryEntry<Biome> out = enforceWarmProvinceFamily(biomes, softened, warmProvince);
        recordWarmDryPath("TROPICAL_GRADIENT", base, out, blockX, blockZ, BAND_SUBTROPICAL, warmProvince);
        return blockNewSubtropicalNonMountainWindswept(base, out, mountainLike, BAND_SUBTROPICAL);
    }

    private static RegistryEntry<Biome> blockNewSubtropicalNonMountainWindswept(RegistryEntry<Biome> incoming,
                                                                                  RegistryEntry<Biome> candidate,
                                                                                  boolean mountainLike,
                                                                                  int bandIndex) {
        if (candidate == null) {
            return incoming;
        }
        if (bandIndex == BAND_SUBTROPICAL
                && !mountainLike
                && isBiomeId(candidate, "minecraft:windswept_savanna")
                && !isBiomeId(incoming, "minecraft:windswept_savanna")) {
            return incoming;
        }
        return candidate;
    }

    private static RegistryEntry<Biome> oceanByLatitudeBandOrBase(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, int bandIndex) {
        if (bandIndex == 0) {
            if (isDeepOcean(base)) {
                try {
                    setSelectionPath(PATH_FALLBACK_PICK);
                    return biome(biomes, "minecraft:deep_lukewarm_ocean");
                } catch (Throwable ignored) {
                    return base;
                }
            }
            return pickShallowTropicalOcean(biomes, blockX, blockZ);
        }
        if (bandIndex == 1 || bandIndex == 2) {
            return pickFromTagNoiseOrFallback(biomes, LAT_OCEAN_TEMPERATE, blockX, blockZ, 21,
                    "minecraft:ocean",
                    "minecraft:deep_ocean");
        }
        if (bandIndex == 3) {
            return pickFromTagNoiseOrFallback(biomes, LAT_OCEAN_SUBPOLAR, blockX, blockZ, 22,
                    "minecraft:cold_ocean",
                    "minecraft:deep_cold_ocean");
        }
        return pickFromTagNoiseOrFallback(biomes, LAT_OCEAN_POLAR, blockX, blockZ, 23,
                "minecraft:frozen_ocean",
                "minecraft:deep_frozen_ocean");
    }

    private static RegistryEntry<Biome> oceanByLatitudeBandOrBase(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, int bandIndex) {
        if (bandIndex == 0) {
            if (isDeepOcean(base)) {
                RegistryEntry<Biome> deep = entryById(biomes, "minecraft:deep_lukewarm_ocean");
                if (deep != null) {
                    setSelectionPath(PATH_FALLBACK_PICK);
                    return deep;
                }
                return base;
            }
            return pickShallowTropicalOcean(biomes, blockX, blockZ);
        }
        if (bandIndex == 1 || bandIndex == 2) {
            return pickFromTagNoiseOrFallback(biomes, base, LAT_OCEAN_TEMPERATE, blockX, blockZ, 21,
                    "minecraft:ocean",
                    "minecraft:deep_ocean");
        }
        if (bandIndex == 3) {
            return pickFromTagNoiseOrFallback(biomes, base, LAT_OCEAN_SUBPOLAR, blockX, blockZ, 22,
                    "minecraft:cold_ocean",
                    "minecraft:deep_cold_ocean");
        }
        return pickFromTagNoiseOrFallback(biomes, base, LAT_OCEAN_POLAR, blockX, blockZ, 23,
                "minecraft:frozen_ocean",
                "minecraft:deep_frozen_ocean");
    }

    private static RegistryEntry<Biome> pickShallowTropicalOcean(Registry<Biome> biomes, int blockX, int blockZ) {
        List<RegistryEntry<Biome>> entries = new ArrayList<>();
        for (RegistryEntry<Biome> entry : biomes.iterateEntries(LAT_OCEAN_TROPICAL)) {
            if (!isDeepOcean(entry)) {
                entries.add(entry);
            }
        }

        entries.sort(Comparator.comparing(entry -> entry.getKey()
                .map(key -> key.getValue().toString())
                .orElse("")));

        int size = entries.size();
        if (size <= 0) {
            setSelectionPath(PATH_FALLBACK_PICK);
            return pickFrom(biomes, blockX, blockZ, 20,
                    "minecraft:warm_ocean",
                    "minecraft:lukewarm_ocean");
        }

        int scaleBlocks = 2048;
        long seed = 0L;
        long salted = seed ^ (0x9E3779B97F4A7C15L * 20L);
        double n = ValueNoise2D.sampleBlocks(salted, blockX, blockZ, scaleBlocks);
        int idx = (int) Math.floor(n * (double) size);
        if (idx >= size) {
            idx = size - 1;
        }
        setSelectionPath(PATH_TAG_PICK);
        return entries.get(idx);
    }

    private static RegistryEntry<Biome> pickShallowTropicalOcean(Collection<RegistryEntry<Biome>> biomes, int blockX, int blockZ) {
        List<RegistryEntry<Biome>> entries = entriesForTag(biomes, LAT_OCEAN_TROPICAL).stream()
                .filter(entry -> !isDeepOcean(entry))
                .sorted(Comparator.comparing(entry -> entry.getKey()
                        .map(key -> key.getValue().toString())
                        .orElse("")))
                .toList();

        int size = entries.size();
        if (size <= 0) {
            setSelectionPath(PATH_FALLBACK_PICK);
            return pickFromFallbacks(biomes, entryById(biomes, "minecraft:warm_ocean"), "minecraft:warm_ocean", "minecraft:lukewarm_ocean");
        }

        int scaleBlocks = 2048;
        long seed = 0L;
        long salted = seed ^ (0x9E3779B97F4A7C15L * 20L);
        double n = ValueNoise2D.sampleBlocks(salted, blockX, blockZ, scaleBlocks);
        int idx = (int) Math.floor(n * (double) size);
        if (idx >= size) {
            idx = size - 1;
        }
        setSelectionPath(PATH_TAG_PICK);
        return entries.get(idx);
    }

    private static RegistryEntry<Biome> mushroomIslandOverride(Registry<Biome> biomes, RegistryEntry<Biome> oceanPick, int blockX, int blockZ) {
        if (!isDeepOcean(oceanPick)) {
            return oceanPick;
        }

        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long roll = hash64(chunkX, chunkZ, 0x5F3759DF);
        if (Long.remainderUnsigned(roll, 2000L) != 0L) {
            return oceanPick;
        }

        try {
            return biome(biomes, "minecraft:mushroom_fields");
        } catch (Throwable ignored) {
            return oceanPick;
        }
    }

    private static RegistryEntry<Biome> firstPresentOcean(Registry<Biome> biomes) {
        String[] ids = new String[]{
                "minecraft:frozen_ocean",
                "minecraft:deep_frozen_ocean",
                "minecraft:deep_cold_ocean",
                "minecraft:cold_ocean",
                "minecraft:ocean"};
        for (String id : ids) {
            try {
                RegistryEntry<Biome> entry = biome(biomes, id);
                if (entry != null && entry.isIn(BiomeTags.IS_OCEAN)) {
                    return entry;
                }
            } catch (Throwable ignored) {
                // continue
            }
        }
        try {
            return biome(biomes, "minecraft:ocean");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static RegistryEntry<Biome> polarShelfOceanFallback(Registry<Biome> biomes) {
        return firstPresentOcean(biomes);
    }

    private static RegistryEntry<Biome> mushroomIslandOverride(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> oceanPick, int blockX, int blockZ) {
        if (!isDeepOcean(oceanPick)) {
            return oceanPick;
        }

        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long roll = hash64(chunkX, chunkZ, 0x5F3759DF);
        if (Long.remainderUnsigned(roll, 2000L) != 0L) {
            return oceanPick;
        }

        RegistryEntry<Biome> entry = entryById(biomes, "minecraft:mushroom_fields");
        return entry != null ? entry : oceanPick;
    }

    private static RegistryEntry<Biome> polarShelfOceanFallback(Collection<RegistryEntry<Biome>> biomes) {
        return firstPresentOcean(biomes);
    }

    private static RegistryEntry<Biome> firstPresentOcean(Collection<RegistryEntry<Biome>> biomes) {
        String[] ids = new String[]{
                "minecraft:frozen_ocean",
                "minecraft:deep_frozen_ocean",
                "minecraft:deep_cold_ocean",
                "minecraft:cold_ocean",
                "minecraft:ocean"};
        for (String id : ids) {
            RegistryEntry<Biome> entry = entryById(biomes, id);
            if (entry != null && entry.isIn(BiomeTags.IS_OCEAN)) {
                return entry;
            }
        }
        return entryById(biomes, "minecraft:ocean");
    }


    private static int latitudeBandIndexWithBlend(int blockX, int blockZ, int radius, LatitudeBands.Band band, double t) {
        if (radius <= 0) {
            return bandIndexForBand(band);
        }

        double latNorm = clamp(t, 0.0, 1.0);
        int bandIndex = crispBandIndex(latNorm);

        if (TRANSITION_MODE == TransitionMode.OFF) {
            return bandIndex;
        }

        int absZ = Math.abs(blockZ);
        int lowerBandIndex;
        int upperBandIndex;
        int boundaryBlocks;
        if (bandIndex <= BAND_TROPICAL) {
            lowerBandIndex = BAND_TROPICAL;
            upperBandIndex = BAND_SUBTROPICAL;
            boundaryBlocks = bandBoundaryBlocks(0, radius);
        } else if (bandIndex >= BAND_POLAR) {
            lowerBandIndex = BAND_SUBPOLAR;
            upperBandIndex = BAND_POLAR;
            boundaryBlocks = bandBoundaryBlocks(3, radius);
        } else {
            int loBoundary = bandBoundaryBlocks(bandIndex - 1, radius);
            int hiBoundary = bandBoundaryBlocks(bandIndex, radius);
            int dLo = Math.abs(absZ - loBoundary);
            int dHi = Math.abs(absZ - hiBoundary);

            lowerBandIndex = bandIndex - 1;
            upperBandIndex = bandIndex;
            boundaryBlocks = loBoundary;
            if (dHi < dLo) {
                lowerBandIndex = bandIndex;
                upperBandIndex = bandIndex + 1;
                boundaryBlocks = hiBoundary;
            }
        }

        double halfWidthBlocks = BLEND_TRANSITION_WIDTH_BLOCKS * 0.5;
        if (!(halfWidthBlocks > 0.0)) {
            return bandIndex;
        }

        double diameter = radius * 2.0;
        double noiseScale = diameter > 0.0 ? (REFERENCE_DIAMETER_BLOCKS / diameter) : 1.0;
        double warpPatchBlocks = scaledPatchBlocks(WARP_NOISE_PATCH_CHUNKS, noiseScale);

        long warpSeed = WORLD_SEED ^ WARP_NOISE_SALT;
        double warpNoise = (blobNoise01ScaledBlocks(warpSeed, blockX, blockZ, warpPatchBlocks, WARP_NOISE_SALT) * 2.0) - 1.0;
        double maxWarp = Math.min(WARP_AMPLITUDE_BLOCKS, halfWidthBlocks);
        double boundaryWarp = warpNoise * maxWarp;
        double effectiveBoundary = boundaryBlocks + boundaryWarp;

        double delta = absZ - effectiveBoundary;
        if (Math.abs(delta) > halfWidthBlocks) {
            return bandIndex;
        }

        double blendT = (delta + halfWidthBlocks) / (2.0 * halfWidthBlocks);
        blendT = clamp(blendT, 0.0, 1.0);
        blendT = smoothstep(blendT);

        double blendPatchBlocks = scaledPatchBlocks(BLEND_NOISE_PATCH_CHUNKS, noiseScale);
        double blendNoise = blobNoise01ScaledBlocks(WORLD_SEED, blockX, blockZ, blendPatchBlocks, BLEND_NOISE_SALT);

        int chosenBandIndex = blendNoise < blendT ? upperBandIndex : lowerBandIndex;
        int resolvedBandIndex = chosenBandIndex;
        double latDegFromRadius = latitudeDegreesFromRadius(blockZ, radius);
        if (lowerBandIndex == BAND_SUBTROPICAL
                && upperBandIndex == BAND_TEMPERATE
                && resolvedBandIndex == BAND_SUBTROPICAL
                && latDegFromRadius >= LatitudeBands.Band.TEMPERATE.lowDeg()) {
            resolvedBandIndex = BAND_TEMPERATE;
        }

        if (DEBUG_BLEND
                && (blockX & 15) == 0
                && (blockZ & 15) == 0
                && resolvedBandIndex != bandIndex
                && BLEND_DEBUG_COUNT.incrementAndGet() <= DEBUG_LIMIT) {
            LOGGER.info("[LAT_BLEND] mode={} x={} z={} lat={} baseBand={} lower={} upper={} chosen={} boundary={} effectiveBoundary={} delta={} transitionWidth={} warpAmp={} warpPatchBlocks={} blendPatchBlocks={} t={} noise={}",
                    TRANSITION_MODE,
                    blockX,
                    blockZ,
                    absZ,
                    bandIndex,
                    lowerBandIndex,
                    upperBandIndex,
                    resolvedBandIndex,
                    boundaryBlocks,
                    String.format(java.util.Locale.ROOT, "%.2f", effectiveBoundary),
                    String.format(java.util.Locale.ROOT, "%.2f", delta),
                    BLEND_TRANSITION_WIDTH_BLOCKS,
                    maxWarp,
                    String.format(java.util.Locale.ROOT, "%.1f", warpPatchBlocks),
                    String.format(java.util.Locale.ROOT, "%.1f", scaledPatchBlocks(BLEND_NOISE_PATCH_CHUNKS, noiseScale)),
                    String.format(java.util.Locale.ROOT, "%.3f", blendT),
                    String.format(java.util.Locale.ROOT, "%.3f", blendNoise));
        }

        return resolvedBandIndex;
    }

    private static int latitudeBandChosenIndexWithBlend(int blockX, int blockZ, int radius, LatitudeBands.Band band, double t) {
        if (radius <= 0) {
            return bandIndexForBand(band);
        }
        double latNorm = clamp(t, 0.0, 1.0);
        int bandIndex = crispBandIndex(latNorm);
        if (TRANSITION_MODE == TransitionMode.OFF) {
            return bandIndex;
        }
        int absZ = Math.abs(blockZ);
        int lowerBandIndex;
        int upperBandIndex;
        int boundaryBlocks;
        if (bandIndex <= BAND_TROPICAL) {
            lowerBandIndex = BAND_TROPICAL;
            upperBandIndex = BAND_SUBTROPICAL;
            boundaryBlocks = bandBoundaryBlocks(0, radius);
        } else if (bandIndex >= BAND_POLAR) {
            lowerBandIndex = BAND_SUBPOLAR;
            upperBandIndex = BAND_POLAR;
            boundaryBlocks = bandBoundaryBlocks(3, radius);
        } else {
            int loBoundary = bandBoundaryBlocks(bandIndex - 1, radius);
            int hiBoundary = bandBoundaryBlocks(bandIndex, radius);
            int dLo = Math.abs(absZ - loBoundary);
            int dHi = Math.abs(absZ - hiBoundary);
            lowerBandIndex = bandIndex - 1;
            upperBandIndex = bandIndex;
            boundaryBlocks = loBoundary;
            if (dHi < dLo) {
                lowerBandIndex = bandIndex;
                upperBandIndex = bandIndex + 1;
                boundaryBlocks = hiBoundary;
            }
        }
        double halfWidthBlocks = BLEND_TRANSITION_WIDTH_BLOCKS * 0.5;
        if (!(halfWidthBlocks > 0.0)) {
            return bandIndex;
        }
        double diameter = radius * 2.0;
        double noiseScale = diameter > 0.0 ? (REFERENCE_DIAMETER_BLOCKS / diameter) : 1.0;
        double warpPatchBlocks = scaledPatchBlocks(WARP_NOISE_PATCH_CHUNKS, noiseScale);
        long warpSeed = WORLD_SEED ^ WARP_NOISE_SALT;
        double warpNoise = (blobNoise01ScaledBlocks(warpSeed, blockX, blockZ, warpPatchBlocks, WARP_NOISE_SALT) * 2.0) - 1.0;
        double maxWarp = Math.min(WARP_AMPLITUDE_BLOCKS, halfWidthBlocks);
        double boundaryWarp = warpNoise * maxWarp;
        double effectiveBoundary = boundaryBlocks + boundaryWarp;
        double delta = absZ - effectiveBoundary;
        if (Math.abs(delta) > halfWidthBlocks) {
            return bandIndex;
        }
        double blendT = (delta + halfWidthBlocks) / (2.0 * halfWidthBlocks);
        blendT = clamp(blendT, 0.0, 1.0);
        blendT = smoothstep(blendT);
        double blendPatchBlocks = scaledPatchBlocks(BLEND_NOISE_PATCH_CHUNKS, noiseScale);
        double blendNoise = blobNoise01ScaledBlocks(WORLD_SEED, blockX, blockZ, blendPatchBlocks, BLEND_NOISE_SALT);
        return blendNoise < blendT ? upperBandIndex : lowerBandIndex;
    }

    private static int crispBandIndex(double t) {
        double absLatDeg = clamp(t, 0.0, 1.0) * 90.0;
        return bandIndexForBand(LatitudeBands.fromAbsoluteLatitudeDeg(absLatDeg));
    }

    private static LatitudeBands.Band bandForAbsLatFraction(double t) {
        double absLatDeg = clamp(t, 0.0, 1.0) * 90.0;
        return LatitudeBands.fromAbsoluteLatitudeDeg(absLatDeg);
    }

    private static double applyBoundaryJitter(int blockX, int blockZ, int radius, double baseT) {
        if (radius <= 0) {
            return MathHelper.clamp(baseT, 0.0, 1.0);
        }
        double jitterBlocks = clamp(radius * BAND_JITTER_FRAC, BAND_JITTER_MIN_BLOCKS, BAND_JITTER_MAX_BLOCKS);
        double wavelengthBlocks = clamp(radius * BAND_JITTER_WAVELENGTH_FRAC,
                BAND_JITTER_WAVELENGTH_MIN_BLOCKS,
                BAND_JITTER_WAVELENGTH_MAX_BLOCKS);
        int noiseScale = Math.max(1, (int) Math.round(wavelengthBlocks));
        double noise01 = ValueNoise2D.sampleBlocks(WORLD_SEED ^ JITTER_NOISE_SALT, blockX, blockZ, noiseScale);
        double signedNoise = (noise01 * 2.0) - 1.0;
        double jitterT = signedNoise * (jitterBlocks / (double) radius);
        double t = baseT + jitterT;
        t = MathHelper.clamp(t, 0.0, 1.0);
        return t;
    }

    private static int bandBoundaryBlocks(int boundaryIndex, int radius) {
        return switch (boundaryIndex) {
            case 0 -> LatitudeMath.zForLatitudeDeg(LatitudeBands.Band.SUBTROPICAL.lowDeg(), radius);
            case 1 -> LatitudeMath.zForLatitudeDeg(LatitudeBands.Band.TEMPERATE.lowDeg(), radius);
            case 2 -> LatitudeMath.zForLatitudeDeg(LatitudeBands.Band.SUBPOLAR.lowDeg(), radius);
            default -> LatitudeMath.zForLatitudeDeg(LatitudeBands.Band.POLAR.lowDeg(), radius);
        };
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double uplandT(int blockY) {
        if (UPLAND_FULL_Y <= UPLAND_MIN_Y) {
            return blockY >= UPLAND_FULL_Y ? 1.0 : 0.0;
        }
        double raw = (double) (blockY - UPLAND_MIN_Y) / (double) (UPLAND_FULL_Y - UPLAND_MIN_Y);
        raw = clamp(raw, 0.0, 1.0);
        return smoothstep(raw);
    }

    private static void assertSurfaceY(int blockY) {
        if (DEBUG_BIOMES && blockY != SURFACE_CLASSIFY_Y && SURFACE_Y_LOGGED.compareAndSet(false, true)) {
            LOGGER.debug("[Latitude] surface pick ignoring callerY={} (using {})", blockY, SURFACE_CLASSIFY_Y);
        }
    }

    private static double latitudeDegreesFromRadius(int blockZ, int radius) {
        if (radius <= 0) {
            return 0.0;
        }
        double abs = Math.abs((double) blockZ);
        double deg = (abs / (double) radius) * 90.0;
        return clamp(deg, 0.0, 90.0);
    }

    private static RegistryEntry<Biome> biome(Registry<Biome> biomes, String id) {
        Identifier ident = Identifier.of(id);
        return biomes.getEntry(ident).orElseThrow();
    }

    private static RegistryEntry<Biome> pickFrom(Registry<Biome> biomes, int blockX, int blockZ, int bandIndex, String... options) {
        int cellX = Math.floorDiv(blockX, VARIANT_CELL_SIZE_BLOCKS);
        int cellZ = Math.floorDiv(blockZ, VARIANT_CELL_SIZE_BLOCKS);
        int idx = (int) Long.remainderUnsigned(hash64(cellX, cellZ, bandIndex), options.length);
        setSelectionPath(PATH_FALLBACK_PICK);
        return biome(biomes, options[idx]);
    }

    private static TagKey<Biome> weightedTagForRoll(int bandIndex, int roll, TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        if (bandIndex == BAND_TROPICAL) {
            if (roll < 34) return primary;
            if (roll < 86) return secondary;
            return accent;
        }
        if (roll < 70) return primary;
        if (roll < 95) return secondary;
        return accent;
    }

    private static void warmPoolAuditRecord(String sourceTag, RegistryEntry<Biome> choice, boolean rerouted) {
        if (!DEBUG_WARM_POOL_AUDIT || choice == null) {
            return;
        }

        long total = WARM_POOL_AUDIT_TOTAL.incrementAndGet();
        if ("tropical_open".equals(sourceTag)) {
            WARM_POOL_AUDIT_ENTER_TROPICAL_OPEN.incrementAndGet();
        }

        String id = biomeId(choice);
        switch (id) {
            case "minecraft:savanna" -> WARM_POOL_AUDIT_PICK_SAVANNA.incrementAndGet();
            case "minecraft:savanna_plateau" -> WARM_POOL_AUDIT_PICK_PLATEAU.incrementAndGet();
            case "minecraft:windswept_savanna" -> WARM_POOL_AUDIT_PICK_WSAV.incrementAndGet();
            case "minecraft:desert" -> WARM_POOL_AUDIT_PICK_DESERT.incrementAndGet();
            case "minecraft:jungle" -> WARM_POOL_AUDIT_PICK_JUNGLE.incrementAndGet();
            case "minecraft:sparse_jungle" -> WARM_POOL_AUDIT_PICK_SPARSE.incrementAndGet();
            case "minecraft:plains" -> WARM_POOL_AUDIT_PICK_PLAINS.incrementAndGet();
            default -> WARM_POOL_AUDIT_PICK_OTHER.incrementAndGet();
        }

        if (rerouted) {
            WARM_POOL_AUDIT_REROUTE.incrementAndGet();
        }

        WARM_POOL_AUDIT_LAST_SOURCE.set(sourceTag);
        WARM_POOL_AUDIT_LAST_PICK.set(id);
    }

    private static RegistryEntry<Biome> warmPoolAuditReturn(String sourceTag, RegistryEntry<Biome> choice, boolean rerouted) {
        warmPoolAuditRecord(sourceTag, choice, rerouted);

        if (!DEBUG_WARM_POOL_AUDIT) {
            return choice;
        }

        long total = WARM_POOL_AUDIT_TOTAL.get();
        if (WARM_POOL_AUDIT_LOG_EVERY > 0 && total % WARM_POOL_AUDIT_LOG_EVERY == 0) {
            String bucketSummary = WARM_POOL_AUDIT_LAST_BUCKET.get();
            String topBucket = topWarmOpenBucket();
            LOGGER.info("[Latitude][WarmPoolAudit] total={} open_enter={} branch[jungle={},strong={}] pick[savanna={},plateau={},windswept={},desert={},jungle={},sparse_jungle={},plains={},other={}] reroute={} last[source={},pick={},bucket={}] topBucket={}",
                    total,
                    WARM_POOL_AUDIT_ENTER_TROPICAL_OPEN.get(),
                    WARM_POOL_AUDIT_OPEN_JUNGLE_BRANCH_ENTER.get(),
                    WARM_POOL_AUDIT_OPEN_STRONG_BRANCH_ENTER.get(),
                    WARM_POOL_AUDIT_PICK_SAVANNA.get(),
                    WARM_POOL_AUDIT_PICK_PLATEAU.get(),
                    WARM_POOL_AUDIT_PICK_WSAV.get(),
                    WARM_POOL_AUDIT_PICK_DESERT.get(),
                    WARM_POOL_AUDIT_PICK_JUNGLE.get(),
                    WARM_POOL_AUDIT_PICK_SPARSE.get(),
                    WARM_POOL_AUDIT_PICK_PLAINS.get(),
                    WARM_POOL_AUDIT_PICK_OTHER.get(),
                    WARM_POOL_AUDIT_REROUTE.get(),
                    WARM_POOL_AUDIT_LAST_SOURCE.get(),
                    WARM_POOL_AUDIT_LAST_PICK.get(),
                    bucketSummary,
                    topBucket);
            LOGGER.info("[Latitude][WarmPoolAudit][NS] enter={} ret[savanna={},desert={},base={},plains_attempt={},other={}] top[savanna={},desert={},base={},plains={},other={}]",
                    WARM_POOL_AUDIT_NS_ENTER.get(),
                    WARM_POOL_AUDIT_NS_RETURN_SAVANNA.get(),
                    WARM_POOL_AUDIT_NS_RETURN_DESERT.get(),
                    WARM_POOL_AUDIT_NS_RETURN_BASE.get(),
                    WARM_POOL_AUDIT_NS_RETURN_PLAINS_ATTEMPT.get(),
                    WARM_POOL_AUDIT_NS_RETURN_OTHER.get(),
                    topBucket(WARM_OPEN_NS_SAVANNA_BUCKETS),
                    topBucket(WARM_OPEN_NS_DESERT_BUCKETS),
                    topBucket(WARM_OPEN_NS_BASE_BUCKETS),
                    topBucket(WARM_OPEN_NS_PLAINS_BUCKETS),
                    topBucket(WARM_OPEN_NS_OTHER_BUCKETS));
        }

        return choice;
    }

    private static void warmOpenBranchEnter(String branch) {
        if (!DEBUG_WARM_POOL_AUDIT) {
            return;
        }
        if ("open_jungle_family_branch_enter".equals(branch)) {
            WARM_POOL_AUDIT_OPEN_JUNGLE_BRANCH_ENTER.incrementAndGet();
        } else if ("open_strong_open_branch_enter".equals(branch)) {
            WARM_POOL_AUDIT_OPEN_STRONG_BRANCH_ENTER.incrementAndGet();
        }
    }

    private static RegistryEntry<Biome> warmOpenAuditReturn(String branch,
                                                            RegistryEntry<Biome> choice,
                                                            boolean rerouted,
                                                            double compositionBias,
                                                            double openness,
                                                            boolean strongOpen) {
        if (DEBUG_WARM_POOL_AUDIT) {
            warmOpenBranchReturn(branch, choice, compositionBias, openness, strongOpen);
        }
        return warmPoolAuditReturn("tropical_open", choice, rerouted);
    }

    private static void warmOpenBranchReturn(String branch,
                                             RegistryEntry<Biome> choice,
                                             double compositionBias,
                                             double openness,
                                             boolean strongOpen) {
        recordWarmOpenBuckets(compositionBias, openness, strongOpen);
        WARM_POOL_AUDIT_LAST_BUCKET.set(bucketLabel(compositionBias, openness, strongOpen));
    }

    private static void recordWarmOpenBuckets(double compositionBias, double openness, boolean strongOpen) {
        int biasBucket = biasBucket(compositionBias);
        int openBucket = opennessBucket(openness);
        int idx = (biasBucket * 10) + (openBucket * 2) + (strongOpen ? 1 : 0);
        if (idx >= 0 && idx < WARM_OPEN_BUCKET_COUNTS.length) {
            WARM_OPEN_BUCKET_COUNTS[idx] += 1L;
        }
    }

    private static void recordWarmOpenNsBuckets(String kind, double compositionBias, double openness, boolean strongOpen) {
        int biasBucket = biasBucket(compositionBias);
        int openBucket = opennessBucket(openness);
        int idx = (biasBucket * 10) + (openBucket * 2) + (strongOpen ? 1 : 0);
        if (idx < 0 || idx >= WARM_OPEN_BUCKET_COUNTS.length) {
            return;
        }
        switch (kind) {
            case "savanna" -> WARM_OPEN_NS_SAVANNA_BUCKETS[idx] += 1L;
            case "desert" -> WARM_OPEN_NS_DESERT_BUCKETS[idx] += 1L;
            case "base" -> WARM_OPEN_NS_BASE_BUCKETS[idx] += 1L;
            case "plains_attempt" -> WARM_OPEN_NS_PLAINS_BUCKETS[idx] += 1L;
            default -> WARM_OPEN_NS_OTHER_BUCKETS[idx] += 1L;
        }
    }

    private static int biasBucket(double v) {
        if (v < -0.4) return 0;
        if (v < -0.2) return 1;
        if (v < 0.0) return 2;
        if (v < 0.2) return 3;
        if (v < 0.4) return 4;
        return 5;
    }

    private static int opennessBucket(double v) {
        if (v < 0.2) return 0;
        if (v < 0.4) return 1;
        if (v < 0.6) return 2;
        if (v < 0.8) return 3;
        return 4;
    }

    private static String bucketLabel(double compositionBias, double openness, boolean strongOpen) {
        String[] biasLabels = new String[]{"<-0.4", "-0.4..-0.2", "-0.2..0", "0..0.2", "0.2..0.4", ">=0.4"};
        String[] openLabels = new String[]{"<0.2", "0.2..0.4", "0.4..0.6", "0.6..0.8", ">=0.8"};
        return "bias=" + biasLabels[biasBucket(compositionBias)] + ",open=" + openLabels[opennessBucket(openness)] + ",strong=" + strongOpen;
    }

    private static String topWarmOpenBucket() {
        long bestCount = 0;
        int bestIdx = -1;
        for (int i = 0; i < WARM_OPEN_BUCKET_COUNTS.length; i++) {
            long c = WARM_OPEN_BUCKET_COUNTS[i];
            if (c > bestCount) {
                bestCount = c;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) {
            return "none";
        }
        int biasBucket = bestIdx / 10;
        int openBucket = (bestIdx % 10) / 2;
        boolean strongOpen = (bestIdx % 2) == 1;
        String[] biasLabels = new String[]{"<-0.4", "-0.4..-0.2", "-0.2..0", "0..0.2", "0.2..0.4", ">=0.4"};
        String[] openLabels = new String[]{"<0.2", "0.2..0.4", "0.4..0.6", "0.6..0.8", ">=0.8"};
        return "bias=" + biasLabels[biasBucket] + ",open=" + openLabels[openBucket] + ",strong=" + strongOpen + ",count=" + bestCount;
    }

    private static String topBucket(long[] buckets) {
        long bestCount = 0;
        int bestIdx = -1;
        for (int i = 0; i < buckets.length; i++) {
            long c = buckets[i];
            if (c > bestCount) {
                bestCount = c;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) {
            return "none";
        }
        int biasBucket = bestIdx / 10;
        int openBucket = (bestIdx % 10) / 2;
        boolean strongOpen = (bestIdx % 2) == 1;
        String[] biasLabels = new String[]{"<-0.4", "-0.4..-0.2", "-0.2..0", "0..0.2", "0.2..0.4", ">=0.4"};
        String[] openLabels = new String[]{"<0.2", "0.2..0.4", "0.4..0.6", "0.6..0.8", ">=0.8"};
        return "bias=" + biasLabels[biasBucket] + ",open=" + openLabels[openBucket] + ",strong=" + strongOpen + ",count=" + bestCount;
    }

    private static TagKey<Biome> subpolarTagForRoll(int roll, boolean snowyPool, TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        if (roll >= 88) {
            return accent;
        }
        return snowyPool ? primary : secondary;
    }

    private static double subpolarSnowProbability(double absLatFraction) {
        double subpolarStart = LatitudeBands.Band.SUBPOLAR.lowDeg() / 90.0;
        double polarStart = LatitudeBands.Band.POLAR.lowDeg() / 90.0;
        double t = 0.0;
        if (polarStart > subpolarStart) {
            t = (absLatFraction - subpolarStart) / (polarStart - subpolarStart);
        }
        t = LatitudeMath.clamp(t, 0.0, 1.0);

        // Ease in the snowy pool a little later and over a wider span so the lower
        // subpolar shoulder stays patchy (taiga/forest) before fully committing to snow.
        // Delay and soften the snowy pool onset so early subpolar stays patchy (taiga/forest)
        // before fully committing to snowy pressure deeper into subpolar.
        double tw = LatitudeMath.clamp((t - 0.45) / 0.50, 0.0, 1.0);
        double pSnow = tw * tw * (3.0 - 2.0 * tw);

        if (t > 0.95) pSnow = 1.0;
        if (t < 0.20) pSnow = 0.0;

        return pSnow;
    }

    private static boolean useSubpolarSnowyPool(double absLatFraction, int blockX, int blockZ) {
        double pSnow = subpolarSnowProbability(absLatFraction);
        double r;
        if (DISABLE_GRID_DITHER) {
            r = ValueNoise2D.sampleBlocks(WORLD_SEED ^ SUBPOLAR_RAMP_SALT, blockX, blockZ, SUBPOLAR_RAMP_PATCH_BLOCKS);
        } else {
            int cellX = Math.floorDiv(blockX, VARIANT_CELL_SIZE_BLOCKS);
            int cellZ = Math.floorDiv(blockZ, VARIANT_CELL_SIZE_BLOCKS);
            r = LatitudeMath.hash01(WORLD_SEED, cellX, cellZ, (int) SUBPOLAR_RAMP_SALT);
        }
        return r < pSnow;
    }

    private static RegistryEntry<Biome> pickSubpolarWithRamp(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                             double absLatFraction, int bandIndex, int weightSalt,
                                                             TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt);
        boolean snowyPool = useSubpolarSnowyPool(absLatFraction, blockX, blockZ);
        TagKey<Biome> tag = subpolarTagForRoll(roll, snowyPool, primary, secondary, accent);
        RegistryEntry<Biome> pick = pickFromTagNoiseOrBase(biomes, tag, base, blockX, blockZ, bandIndex);
        double deg = LatitudeMath.clamp(absLatFraction * 90.0, 0.0, 90.0);
        if (deg < 60.0 && isBiomeId(pick, "minecraft:snowy_taiga")) {
            RegistryEntry<Biome> fallback = pickFrom(biomes, blockX, blockZ, BAND_SUBPOLAR,
                    "minecraft:taiga",
                    "minecraft:old_growth_spruce_taiga",
                    "minecraft:snowy_plains");
            if (fallback != null) {
                return fallback;
            }
        }
        return pick;
    }

    private static RegistryEntry<Biome> pickPolarWithFrontShoulder(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                                   double absLatFraction, boolean coldMountainLike,
                                                                   int centerHeight, int robustDelta, int seaLevel,
                                                                   boolean mountainNoiseLike, boolean mountainLike, int oceanDistance) {
        boolean flatPolarShelf = isFlatPolarShelf(centerHeight, robustDelta, seaLevel, mountainNoiseLike, mountainLike);
        boolean nearShelf = oceanDistance >= 0 && oceanDistance <= 64;
        if (flatPolarShelf && nearShelf && !mountainLike && !mountainNoiseLike) {
            RegistryEntry<Biome> shelf = polarShelfOceanFallback(biomes);
            if (shelf != null) {
                return shelf;
            }
        }
        if (flatPolarShelf) {
            RegistryEntry<Biome> shelfPick = pickDeterministicFromPool(
                    flatPolarShelfPool(allowedLandPool(biomes, BAND_POLAR)),
                    blockX,
                    blockZ,
                    BAND_POLAR,
                    TERRAIN_CLASS_FLAT_SHELF,
                    0x4D54);
            if (shelfPick != null) {
                return shelfPick;
            }
        }
        RegistryEntry<Biome> pick = pickFromWeightedTags(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        double deg = LatitudeMath.clamp(absLatFraction * 90.0, 0.0, 90.0);
        double shoulderMaxDeg = LatitudeBands.Band.POLAR.lowDeg() + 8.0;
        if (coldMountainLike) {
            RegistryEntry<Biome> mountain = flatPolarShelf ? null : pickFrom(biomes, blockX, blockZ, BAND_POLAR,
                    "minecraft:snowy_slopes",
                    "minecraft:frozen_peaks",
                    "minecraft:jagged_peaks");
            if (mountain != null) pick = mountain;
        } else if (isBiomeId(pick, "minecraft:snowy_slopes")) {
            RegistryEntry<Biome> fallback = pickFrom(biomes, blockX, blockZ, BAND_POLAR,
                    flatPolarShelf ? "minecraft:snowy_plains" : "minecraft:snowy_slopes",
                    "minecraft:snowy_plains",
                    "minecraft:snowy_taiga",
                    "minecraft:grove");
            if (fallback != null) pick = fallback;
        }
        if (flatPolarShelf && isFlatPolarShelfBannedMountainPick(pick)) {
            RegistryEntry<Biome> shelfFallback = pickFrom(biomes, blockX, blockZ, BAND_POLAR,
                    "minecraft:snowy_plains",
                    "minecraft:snowy_taiga",
                    "minecraft:grove");
            if (shelfFallback != null) {
                pick = shelfFallback;
            }
        }
        if (!coldMountainLike && deg <= shoulderMaxDeg && isBiomeId(pick, "minecraft:snowy_slopes")) {
            return pickSubpolarWithRamp(biomes, base, blockX, blockZ, absLatFraction, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
        }
        // Block alpine outputs on ALL non-mountain polar land
        if (!coldMountainLike && !mountainLike && !mountainNoiseLike
                && isFlatPolarShelfBannedMountainPick(pick)) {
            RegistryEntry<Biome> fallback = pickFrom(biomes, blockX, blockZ, BAND_POLAR,
                    "minecraft:snowy_plains",
                    "minecraft:snowy_taiga",
                    "minecraft:grove");
            if (fallback != null) {
                pick = fallback;
            }
        }
        return pick;
    }

    private static RegistryEntry<Biome> pickPolarWithFrontShoulder(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                                   double absLatFraction, boolean coldMountainLike,
                                                                   int centerHeight, int robustDelta, int seaLevel,
                                                                   boolean mountainNoiseLike, int oceanDistance, boolean mountainLike) {
        return pickPolarWithFrontShoulder(
                biomes,
                base,
                blockX,
                blockZ,
                absLatFraction,
                coldMountainLike,
                centerHeight,
                robustDelta,
                seaLevel,
                mountainNoiseLike,
                mountainLike,
                oceanDistance);
    }

    private static double scaledPatchBlocks(int basePatchChunks, double noiseScale) {
        double basePatchBlocks = basePatchChunks * 16.0;
        double scaled = basePatchBlocks * noiseScale;
        return Math.max(16.0, scaled);
    }

    private static double tropicalCompositionBias(long seed, int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = Math.abs(blockZ) >> 4;
        double broad = (blobNoise01(seed ^ TROPICAL_COMPOSITION_SALT, chunkX, chunkZ, 24, TROPICAL_COMPOSITION_SALT) * 2.0) - 1.0;
        double medium = (blobNoise01(seed ^ TROPICAL_MICRO_COMPOSITION_SALT, chunkX, chunkZ, 7, TROPICAL_MICRO_COMPOSITION_SALT) * 2.0) - 1.0;
        return (broad * 0.18) + (medium * 0.10);
    }

    private static double tropicalOpennessNoise(int blockX, int blockZ) {
        return ValueNoise2D.sampleBlocks(WORLD_SEED ^ TROPICAL_OPENNESS_SALT, blockX, blockZ, 1792);
    }

    private static final long SUBTROPICAL_HUMIDITY_SALT = 0xDECAF_50B7_0001L;

    private static double subtropicalHumidityNoise(int blockX, int blockZ) {
        return ValueNoise2D.sampleBlocks(WORLD_SEED ^ SUBTROPICAL_HUMIDITY_SALT, blockX, blockZ, 1536);
    }

    private static double subtropicalHumidityThreshold(int step) {
        return switch (step) {
            case 1 -> 0.40;
            case 2 -> 0.45;
            case 3 -> 0.35;
            default -> 0.0; // step 0 = desert core, always arid
        };
    }

    private static double softenedTropicalLadderT(long seed, int blockX, int blockZ, double ladderT) {
        int chunkX = blockX >> 4;
        int chunkZ = Math.abs(blockZ) >> 4;
        double jitterN = (blobNoise01(seed, chunkX, chunkZ, 8, 0xBADC0FFEE0DDF00DL) * 2.0) - 1.0;
        double compositionBias = tropicalCompositionBias(seed, blockX, blockZ);
        double tJitter = clamp(ladderT + (jitterN * 0.10) + compositionBias, 0.0, 1.0);
        return smoothstep(tJitter);
    }

    private static double blobNoise01ScaledBlocks(long seed, int blockX, int blockZ, double patchBlocks, long salt) {
        double safePatchBlocks = Math.max(16.0, patchBlocks);
        double sx = blockX / safePatchBlocks;
        double sz = blockZ / safePatchBlocks;

        int gx = (int) Math.floor(sx);
        int gz = (int) Math.floor(sz);
        int x1 = gx + 1;
        int z1 = gz + 1;

        double fx = sx - gx;
        double fz = sz - gz;
        double u = smoothstep(fx);
        double v = smoothstep(fz);

        double n00 = hash01(seed, gx, gz, salt);
        double n10 = hash01(seed, x1, gz, salt);
        double n01 = hash01(seed, gx, z1, salt);
        double n11 = hash01(seed, x1, z1, salt);

        double nx0 = n00 + (n10 - n00) * u;
        double nx1 = n01 + (n11 - n01) * u;
        return nx0 + (nx1 - nx0) * v;
    }

    private static RegistryEntry<Biome> pickSubpolarWithRamp(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                             double absLatFraction, int bandIndex, int weightSalt,
                                                             TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt);
        boolean snowyPool = useSubpolarSnowyPool(absLatFraction, blockX, blockZ);
        TagKey<Biome> tag = subpolarTagForRoll(roll, snowyPool, primary, secondary, accent);
        return pickFromTagNoiseOrBase(biomes, tag, base, blockX, blockZ, bandIndex);
    }

    private static RegistryEntry<Biome> pickPolarWithFrontShoulder(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                                   double absLatFraction, boolean coldMountainLike,
                                                                   int centerHeight, int robustDelta, int seaLevel,
                                                                   boolean mountainNoiseLike, boolean mountainLike, int oceanDistance) {
        boolean flatPolarShelf = isFlatPolarShelf(centerHeight, robustDelta, seaLevel, mountainNoiseLike, mountainLike);
        boolean nearShelf = oceanDistance >= 0 && oceanDistance <= 64;
        if (flatPolarShelf && nearShelf && !mountainLike && !mountainNoiseLike) {
            RegistryEntry<Biome> shelf = polarShelfOceanFallback(biomes);
            if (shelf != null) {
                return shelf;
            }
        }
        if (flatPolarShelf) {
            RegistryEntry<Biome> shelfPick = pickDeterministicFromPool(
                    flatPolarShelfPool(allowedLandPool(biomes, BAND_POLAR)),
                    blockX,
                    blockZ,
                    BAND_POLAR,
                    TERRAIN_CLASS_FLAT_SHELF,
                    0x4D54);
            if (shelfPick != null) {
                return shelfPick;
            }
        }
        RegistryEntry<Biome> pick = pickFromWeightedTags(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        double deg = LatitudeMath.clamp(absLatFraction * 90.0, 0.0, 90.0);
        double shoulderMaxDeg = LatitudeBands.Band.POLAR.lowDeg() + 8.0;
        if (coldMountainLike) {
            List<RegistryEntry<Biome>> options = new ArrayList<>();
            RegistryEntry<Biome> slope = entryById(biomes, "minecraft:snowy_slopes");
            RegistryEntry<Biome> frozen = entryById(biomes, "minecraft:frozen_peaks");
            RegistryEntry<Biome> jagged = entryById(biomes, "minecraft:jagged_peaks");
            if (slope != null) options.add(slope);
            if (frozen != null) options.add(frozen);
            if (jagged != null) options.add(jagged);
            if (flatPolarShelf) {
                options.removeIf(LatitudeBiomes::isFlatPolarShelfBannedMountainPick);
            }
            if (!options.isEmpty()) {
                double n = ValueNoise2D.sampleBlocks(WORLD_SEED ^ 0x5EEDC0DEL, blockX, blockZ, 2048);
                int idx = (int) Math.floor(n * (double) options.size());
                if (idx >= options.size()) idx = options.size() - 1;
                pick = options.get(idx);
            }
        } else if (isBiomeId(pick, "minecraft:snowy_slopes")) {
            List<RegistryEntry<Biome>> options = new ArrayList<>();
            RegistryEntry<Biome> slopes = entryById(biomes, "minecraft:snowy_slopes");
            RegistryEntry<Biome> plains = entryById(biomes, "minecraft:snowy_plains");
            RegistryEntry<Biome> taiga = entryById(biomes, "minecraft:snowy_taiga");
            RegistryEntry<Biome> grove = entryById(biomes, "minecraft:grove");
            if (slopes != null) options.add(slopes);
            if (plains != null) options.add(plains);
            if (taiga != null) options.add(taiga);
            if (grove != null) options.add(grove);
            if (flatPolarShelf) {
                options.removeIf(LatitudeBiomes::isFlatPolarShelfBannedMountainPick);
            }
            if (!options.isEmpty()) {
                double n = ValueNoise2D.sampleBlocks(WORLD_SEED ^ 0x5EEDC0DEL, blockX, blockZ, 2048);
                int idx = (int) Math.floor(n * (double) options.size());
                if (idx >= options.size()) idx = options.size() - 1;
                pick = options.get(idx);
            }
        }
        if (flatPolarShelf && isFlatPolarShelfBannedMountainPick(pick)) {
            List<RegistryEntry<Biome>> shelfFallbacks = new ArrayList<>();
            RegistryEntry<Biome> plains = entryById(biomes, "minecraft:snowy_plains");
            RegistryEntry<Biome> taiga = entryById(biomes, "minecraft:snowy_taiga");
            RegistryEntry<Biome> grove = entryById(biomes, "minecraft:grove");
            if (plains != null) shelfFallbacks.add(plains);
            if (taiga != null) shelfFallbacks.add(taiga);
            if (grove != null) shelfFallbacks.add(grove);
            if (!shelfFallbacks.isEmpty()) {
                double n = ValueNoise2D.sampleBlocks(WORLD_SEED ^ 0x5EEDC0DEL, blockX, blockZ, 2048);
                int idx = (int) Math.floor(n * (double) shelfFallbacks.size());
                if (idx >= shelfFallbacks.size()) idx = shelfFallbacks.size() - 1;
                pick = shelfFallbacks.get(idx);
            }
        }
        if (!coldMountainLike && deg <= shoulderMaxDeg && isBiomeId(pick, "minecraft:snowy_slopes")) {
            return pickSubpolarWithRamp(biomes, base, blockX, blockZ, absLatFraction, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
        }
        // Block alpine outputs on ALL non-mountain polar land
        if (!coldMountainLike && !mountainLike && !mountainNoiseLike
                && isFlatPolarShelfBannedMountainPick(pick)) {
            List<RegistryEntry<Biome>> nearOceanFallbacks = new ArrayList<>();
            RegistryEntry<Biome> plains = entryById(biomes, "minecraft:snowy_plains");
            RegistryEntry<Biome> taiga = entryById(biomes, "minecraft:snowy_taiga");
            RegistryEntry<Biome> grove = entryById(biomes, "minecraft:grove");
            if (plains != null) nearOceanFallbacks.add(plains);
            if (taiga != null) nearOceanFallbacks.add(taiga);
            if (grove != null) nearOceanFallbacks.add(grove);
            if (!nearOceanFallbacks.isEmpty()) {
                double n = ValueNoise2D.sampleBlocks(WORLD_SEED ^ 0x5EEDC0DEL, blockX, blockZ, 2048);
                int idx = (int) Math.floor(n * (double) nearOceanFallbacks.size());
                if (idx >= nearOceanFallbacks.size()) idx = nearOceanFallbacks.size() - 1;
                pick = nearOceanFallbacks.get(idx);
            }
        }
        return pick;
    }

    private static int weightedRoll(int blockX, int blockZ, int salt) {
        double blob;
        if (DISABLE_GRID_DITHER) {
            blob = blobNoise01Blocks(WORLD_SEED, blockX, blockZ, Math.max(16, VARIANT_CELL_SIZE_BLOCKS), salt);
        } else {
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            int patchSizeChunks = Math.max(1, VARIANT_CELL_SIZE_BLOCKS >> 4);
            blob = blobNoise01(WORLD_SEED, chunkX, chunkZ, patchSizeChunks, salt);
        }
        int roll = (int) Math.floor(blob * 100.0);
        return clampInt(roll, 0, 99);
    }

    private static RegistryEntry<Biome> pickFromWeightedTags(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                             int bandIndex, int weightSalt,
                                                             TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt);
        TagKey<Biome> tag = weightedTagForRoll(bandIndex, roll, primary, secondary, accent);
        return pickFromTagNoiseOrBase(biomes, tag, base, blockX, blockZ, bandIndex);
    }

    private static RegistryEntry<Biome> pickFromWeightedTagsNoMangrove(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                                       int bandIndex, int weightSalt,
                                                                       TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt + (int) MANGROVE_FALLBACK_SALT);
        TagKey<Biome> tag = weightedTagForRoll(bandIndex, roll, primary, secondary, accent);
        return pickFromTagNoiseOrBaseFiltered(biomes, tag, base, blockX, blockZ, bandIndex, MANGROVE_FALLBACK_SALT, true);
    }

    private static RegistryEntry<Biome> pickFromWeightedTagsNoSwamp(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                                    int bandIndex, int weightSalt,
                                                                    TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt + (int) SWAMP_FALLBACK_SALT);
        TagKey<Biome> tag = weightedTagForRoll(bandIndex, roll, primary, secondary, accent);
        return pickFromTagNoiseOrBaseFilteredSwamp(biomes, tag, base, blockX, blockZ, bandIndex, SWAMP_FALLBACK_SALT, true);
    }

    private static RegistryEntry<Biome> pickFromWeightedTags(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                             int bandIndex, int weightSalt,
                                                             TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt);
        TagKey<Biome> tag = weightedTagForRoll(bandIndex, roll, primary, secondary, accent);
        return pickFromTagNoiseOrBase(biomes, tag, base, blockX, blockZ, bandIndex);
    }

    private static RegistryEntry<Biome> pickFromWeightedTagsNoMangrove(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                                       int bandIndex, int weightSalt,
                                                                       TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt + (int) MANGROVE_FALLBACK_SALT);
        TagKey<Biome> tag = weightedTagForRoll(bandIndex, roll, primary, secondary, accent);
        return pickFromTagNoiseOrBaseFiltered(biomes, tag, base, blockX, blockZ, bandIndex, MANGROVE_FALLBACK_SALT, true);
    }

    private static RegistryEntry<Biome> pickFromWeightedTagsNoSwamp(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                                    int bandIndex, int weightSalt,
                                                                    TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt + (int) SWAMP_FALLBACK_SALT);
        TagKey<Biome> tag = weightedTagForRoll(bandIndex, roll, primary, secondary, accent);
        return pickFromTagNoiseOrBaseFilteredSwamp(biomes, tag, base, blockX, blockZ, bandIndex, SWAMP_FALLBACK_SALT, true);
    }

    private static RegistryEntry<Biome> pickFromTagNoiseOrFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, TagKey<Biome> tag, int blockX, int blockZ, int bandIndex, String... fallbackOptions) {
        List<RegistryEntry<Biome>> entries = entriesForTag(biomes, tag);
        int size = entries.size();
        if (size <= 0) {
            return pickFromFallbacks(biomes, base, fallbackOptions);
        }

        int scaleBlocks = 2048;
        long seed = 0L;
        long salted = seed ^ (0x9E3779B97F4A7C15L * (long) bandIndex);
        double n = ValueNoise2D.sampleBlocks(salted, blockX, blockZ, scaleBlocks);
        int idx = (int) Math.floor(n * (double) size);
        if (idx >= size) {
            idx = size - 1;
        }
        setSelectionPath(PATH_TAG_PICK);
        return entries.get(idx);
    }

    private static RegistryEntry<Biome> pickFromTagNoiseOrBaseFilteredSwamp(Collection<RegistryEntry<Biome>> biomes, TagKey<Biome> tag, RegistryEntry<Biome> base,
                                                                            int blockX, int blockZ, int bandIndex, long extraSalt, boolean disallowSwamp) {
        List<RegistryEntry<Biome>> entries = entriesForTag(biomes, tag);
        if (disallowSwamp) {
            entries = filterSwamp(entries);
        }
        int size = entries.size();
        if (size <= 0) {
            setSelectionPath(PATH_RETURN_BASE);
            return base;
        }

        int scaleBlocks = 2048;
        long seed = 0L;
        long salted = seed ^ (0x9E3779B97F4A7C15L * (long) bandIndex) ^ extraSalt;
        double n = ValueNoise2D.sampleBlocks(salted, blockX, blockZ, scaleBlocks);
        int idx = (int) Math.floor(n * (double) size);
        if (idx >= size) {
            idx = size - 1;
        }
        setSelectionPath(PATH_TAG_PICK);
        return entries.get(idx);
    }

    private static RegistryEntry<Biome> pickFromTagNoiseOrBaseFilteredSwamp(Registry<Biome> biomes, TagKey<Biome> tag, RegistryEntry<Biome> base,
                                                                            int blockX, int blockZ, int bandIndex, long extraSalt, boolean disallowSwamp) {
        List<RegistryEntry<Biome>> entries = new ArrayList<>();
        for (RegistryEntry<Biome> entry : biomes.iterateEntries(tag)) {
            entries.add(entry);
        }

        entries.sort(Comparator.comparing(entry -> entry.getKey()
                .map(key -> key.getValue().toString())
                .orElse("")));

        if (disallowSwamp) {
            entries = filterSwamp(entries);
        }

        int size = entries.size();
        if (size <= 0) {
            setSelectionPath(PATH_RETURN_BASE);
            return base;
        }

        int scaleBlocks = 2048;
        long seed = 0L;
        long salted = seed ^ (0x9E3779B97F4A7C15L * (long) bandIndex) ^ extraSalt;
        double n = ValueNoise2D.sampleBlocks(salted, blockX, blockZ, scaleBlocks);
        int idx = (int) Math.floor(n * (double) size);
        if (idx >= size) {
            idx = size - 1;
        }
        setSelectionPath(PATH_TAG_PICK);
        return entries.get(idx);
    }

    private static RegistryEntry<Biome> pickFromTagNoiseOrBaseFiltered(Registry<Biome> biomes, TagKey<Biome> tag, RegistryEntry<Biome> base,
                                                                       int blockX, int blockZ, int bandIndex, long extraSalt, boolean disallowMangrove) {
        List<RegistryEntry<Biome>> entries = new ArrayList<>();
        for (RegistryEntry<Biome> entry : biomes.iterateEntries(tag)) {
            entries.add(entry);
        }

        entries.sort(Comparator.comparing(entry -> entry.getKey()
                .map(key -> key.getValue().toString())
                .orElse("")));

        if (disallowMangrove) {
            entries = filterMangrove(entries);
        }

        int size = entries.size();
        if (size <= 0) {
            setSelectionPath(PATH_RETURN_BASE);
            return base;
        }

        int scaleBlocks = 2048;
        long seed = 0L;
        long salted = seed ^ (0x9E3779B97F4A7C15L * (long) bandIndex) ^ extraSalt;
        double n = ValueNoise2D.sampleBlocks(salted, blockX, blockZ, scaleBlocks);
        int idx = (int) Math.floor(n * (double) size);
        if (idx >= size) {
            idx = size - 1;
        }
        setSelectionPath(PATH_TAG_PICK);
        return entries.get(idx);
    }

    private static RegistryEntry<Biome> pickTemperateLand(Registry<Biome> biomes,
                                                          int blockX, int blockZ, int blockY,
                                                          Supplier<RegistryEntry<Biome>> defaultPick,
                                                          boolean mountainLike) {
        double ramp = uplandT(blockY);
        if (mountainLike || ramp <= 0.0) {
            return defaultPick.get();
        }
        double roll = ValueNoise2D.sampleBlocks(WORLD_SEED ^ UPLAND_ROLL_SALT, blockX, blockZ, UPLAND_SCALE_BLOCKS);
        if (roll < ramp) {
            RegistryEntry<Biome> upland = pickTemperateUplandBiome(biomes, blockX, blockZ);
            if (upland != null) {
                return upland;
            }
        }
        return defaultPick.get();
    }

    private static RegistryEntry<Biome> pickTemperateLand(Collection<RegistryEntry<Biome>> biomes,
                                                          int blockX, int blockZ, int blockY,
                                                          Supplier<RegistryEntry<Biome>> defaultPick,
                                                          boolean mountainLike) {
        double ramp = uplandT(blockY);
        if (mountainLike || ramp <= 0.0) {
            return defaultPick.get();
        }
        double roll = ValueNoise2D.sampleBlocks(WORLD_SEED ^ UPLAND_ROLL_SALT, blockX, blockZ, UPLAND_SCALE_BLOCKS);
        if (roll < ramp) {
            RegistryEntry<Biome> upland = pickTemperateUplandBiome(biomes, blockX, blockZ);
            if (upland != null) {
                return upland;
            }
        }
        return defaultPick.get();
    }

    private static RegistryEntry<Biome> pickTemperateUplandBiome(Registry<Biome> biomes, int blockX, int blockZ) {
        int poolSize = TEMPERATE_UPLAND_BIOMES.length;
        if (poolSize == 0) {
            return null;
        }
        double n = ValueNoise2D.sampleBlocks(WORLD_SEED ^ UPLAND_POOL_SALT, blockX, blockZ, UPLAND_SCALE_BLOCKS);
        int idx = (int) Math.floor(n * (double) poolSize);
        if (idx < 0) {
            idx = 0;
        } else if (idx >= poolSize) {
            idx = poolSize - 1;
        }
        try {
            return biome(biomes, TEMPERATE_UPLAND_BIOMES[idx]);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static RegistryEntry<Biome> pickFromTagNoiseOrBaseFiltered(Collection<RegistryEntry<Biome>> biomes, TagKey<Biome> tag, RegistryEntry<Biome> base,
                                                                       int blockX, int blockZ, int bandIndex, long extraSalt, boolean disallowMangrove) {
        List<RegistryEntry<Biome>> entries = entriesForTag(biomes, tag);
        if (disallowMangrove) {
            entries = filterMangrove(entries);
        }
        int size = entries.size();
        if (size <= 0) {
            setSelectionPath(PATH_RETURN_BASE);
            return base;
        }

        int scaleBlocks = 2048;
        long seed = 0L;
        long salted = seed ^ (0x9E3779B97F4A7C15L * (long) bandIndex) ^ extraSalt;
        double n = ValueNoise2D.sampleBlocks(salted, blockX, blockZ, scaleBlocks);
        int idx = (int) Math.floor(n * (double) size);
        if (idx >= size) {
            idx = size - 1;
        }
        setSelectionPath(PATH_TAG_PICK);
        return entries.get(idx);
    }

    private static RegistryEntry<Biome> pickFromTagNoiseOrBase(Collection<RegistryEntry<Biome>> biomes, TagKey<Biome> tag, RegistryEntry<Biome> base, int blockX, int blockZ, int bandIndex) {
        List<RegistryEntry<Biome>> entries = entriesForTag(biomes, tag);
        int size = entries.size();
        if (size <= 0) {
            setSelectionPath(PATH_RETURN_BASE);
            return base;
        }

        int scaleBlocks = 2048;
        long seed = 0L;
        long salted = seed ^ (0x9E3779B97F4A7C15L * (long) bandIndex);
        double n = ValueNoise2D.sampleBlocks(salted, blockX, blockZ, scaleBlocks);
        int idx = (int) Math.floor(n * (double) size);
        if (idx >= size) {
            idx = size - 1;
        }
        setSelectionPath(PATH_TAG_PICK);
        RegistryEntry<Biome> pick = entries.get(idx);
        if (bandIndex == BAND_TROPICAL && isBiomeId(pick, "minecraft:sparse_jungle")) {
            double openness = tropicalOpennessNoise(blockX, blockZ);
            double compositionBias = tropicalCompositionBias(WORLD_SEED, blockX, blockZ);
            if (openness >= 0.55 || compositionBias <= 0.16) {
                RegistryEntry<Biome> reroute = openness >= 0.20
                        ? entryById(biomes, "minecraft:savanna")
                        : entryById(biomes, "minecraft:jungle");
                if (reroute != null) {
                    return reroute;
                }
            }
        }
        return pick;
    }

    private static RegistryEntry<Biome> pickFromFallbacks(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, String... fallbackOptions) {
        for (String fallback : fallbackOptions) {
            RegistryEntry<Biome> entry = entryById(biomes, fallback);
            if (entry != null) {
                setSelectionPath(PATH_FALLBACK_PICK);
                return entry;
            }
        }
        setSelectionPath(PATH_RETURN_BASE);
        return base;
    }

    private static List<RegistryEntry<Biome>> entriesForTag(Collection<RegistryEntry<Biome>> biomes, TagKey<Biome> tag) {
        List<RegistryEntry<Biome>> entries = new ArrayList<>();
        for (RegistryEntry<Biome> entry : biomes) {
            if (entry.isIn(tag)) {
                entries.add(entry);
            }
        }

        entries.sort(Comparator.comparing(entry -> entry.getKey()
                .map(key -> key.getValue().toString())
                .orElse("")));
        return entries;
    }

    private static List<TagKey<Biome>> landBandTags(int bandIndex) {
        return switch (bandIndex) {
            case BAND_TROPICAL -> List.of(
                    LAT_EQUATOR_PRIMARY,
                    LAT_EQUATOR_SECONDARY,
                    LAT_EQUATOR_ACCENT);
            case BAND_SUBTROPICAL -> List.of(
                    LAT_ARID_PRIMARY,
                    LAT_ARID_SECONDARY,
                    LAT_ARID_ACCENT,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY,
                    LAT_TRANS_ARID_TROPICS_1_SECONDARY,
                    LAT_TRANS_ARID_TROPICS_1_ACCENT,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY,
                    LAT_TRANS_ARID_TROPICS_2_SECONDARY,
                    LAT_TRANS_ARID_TROPICS_2_ACCENT,
                    LAT_SUBTROPICAL_HUMID_PRIMARY,
                    LAT_SUBTROPICAL_HUMID_SECONDARY,
                    LAT_SUBTROPICAL_HUMID_ACCENT);
            case BAND_TEMPERATE -> List.of(
                    LAT_TEMPERATE_PRIMARY,
                    LAT_TEMPERATE_SECONDARY,
                    LAT_TEMPERATE_ACCENT,
                    LAT_TEMPERATE_MOUNTAIN);
            case BAND_SUBPOLAR -> List.of(
                    LAT_SUBPOLAR_PRIMARY,
                    LAT_SUBPOLAR_SECONDARY,
                    LAT_SUBPOLAR_ACCENT);
            default -> List.of(
                    LAT_POLAR_PRIMARY,
                    LAT_POLAR_SECONDARY,
                    LAT_POLAR_ACCENT);
        };
    }

    private static List<String> allowedExtraBiomeIdsForBand(int bandIndex) {
        return switch (bandIndex) {
            case BAND_TROPICAL -> List.of(
                    SWAMP_ID);
            case BAND_SUBTROPICAL -> List.of(
                    SWAMP_ID,
                    MANGROVE_ID);
            case BAND_TEMPERATE -> List.of(
                    "minecraft:sunflower_plains",
                    "minecraft:pale_garden",
                    "minecraft:stony_peaks");
            case BAND_POLAR -> List.of(
                    "minecraft:ice_spikes",
                    "minecraft:snowy_plains");
            default -> List.of();
        };
    }

    private static List<RegistryEntry<Biome>> allowedLandPool(Registry<Biome> biomes, int bandIndex) {
        List<RegistryEntry<Biome>> allowed = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>();
        for (TagKey<Biome> tag : landBandTags(bandIndex)) {
            for (RegistryEntry<Biome> entry : biomes.iterateEntries(tag)) {
                addAllowedEntry(allowed, seen, entry);
            }
        }
        for (String id : allowedExtraBiomeIdsForBand(bandIndex)) {
            try {
                addAllowedEntry(allowed, seen, biome(biomes, id));
            } catch (Throwable ignored) {
                // Optional biome not present in current registry/datapack set.
            }
        }
        allowed.sort(Comparator.comparing(LatitudeBiomes::biomeId));
        return allowed;
    }

    private static List<RegistryEntry<Biome>> allowedLandPool(Collection<RegistryEntry<Biome>> biomes, int bandIndex) {
        List<RegistryEntry<Biome>> allowed = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>();
        for (TagKey<Biome> tag : landBandTags(bandIndex)) {
            for (RegistryEntry<Biome> entry : entriesForTag(biomes, tag)) {
                addAllowedEntry(allowed, seen, entry);
            }
        }
        for (String id : allowedExtraBiomeIdsForBand(bandIndex)) {
            RegistryEntry<Biome> entry = entryById(biomes, id);
            if (entry != null) {
                addAllowedEntry(allowed, seen, entry);
            }
        }
        allowed.sort(Comparator.comparing(LatitudeBiomes::biomeId));
        return allowed;
    }

    private static void addAllowedEntry(List<RegistryEntry<Biome>> allowed, Set<Identifier> seen, RegistryEntry<Biome> entry) {
        Identifier id = entry.getKey().map(key -> key.getValue()).orElse(null);
        if (id == null || !seen.add(id)) {
            return;
        }
        allowed.add(entry);
    }

    private static boolean isInAllowedLandPool(List<RegistryEntry<Biome>> allowedPool, RegistryEntry<Biome> candidate) {
        Identifier candidateId = candidate.getKey().map(key -> key.getValue()).orElse(null);
        if (candidateId == null) {
            return false;
        }
        for (RegistryEntry<Biome> allowed : allowedPool) {
            Identifier allowedId = allowed.getKey().map(key -> key.getValue()).orElse(null);
            if (candidateId.equals(allowedId)) {
                return true;
            }
        }
        return false;
    }

    private static RegistryEntry<Biome> enforceLandBandPool(Registry<Biome> biomes,
                                                            RegistryEntry<Biome> candidate,
                                                            int blockX,
                                                            int blockZ,
                                                            double t,
                                                            int bandIndex,
                                                            boolean mountainLike) {
        List<RegistryEntry<Biome>> preFilterPool = allowedLandPool(biomes, bandIndex);
        List<RegistryEntry<Biome>> allowedPool = preFilterPool;
        if (bandIndex == BAND_TEMPERATE && !mountainLike) {
            allowedPool = removeTemperateMountainFamily(allowedPool);
        }
        if (bandIndex == BAND_SUBTROPICAL && !mountainLike) {
            allowedPool = removeSubtropicalNonMountainWindsweptFamily(allowedPool);
        }
        if (bandIndex == BAND_POLAR) {
            List<RegistryEntry<Biome>> polarNoTaiga = removePolarTaigaFamily(allowedPool);
            if (!polarNoTaiga.isEmpty()) {
                allowedPool = polarNoTaiga;
            }
        }
        RegistryEntry<Biome> out = candidate;
        if (!allowedPool.isEmpty() && !isInAllowedLandPool(allowedPool, candidate)) {
            maybeLogBandLeak(blockX, blockZ, t, bandIndex, candidate);
            List<RegistryEntry<Biome>> rerollPool = allowedPool;
            if (bandIndex == BAND_SUBTROPICAL && !mountainLike) {
                List<RegistryEntry<Biome>> subtropicalNoForest = removeSubtropicalNonMountainForestFamily(rerollPool);
                if (!subtropicalNoForest.isEmpty()) {
                    rerollPool = subtropicalNoForest;
                }
            }
            out = pickFromAllowedLandPool(rerollPool, blockX, blockZ, bandIndex);
            recordWarmDryPath("DIRECT_POOL_PICK", candidate, out, blockX, blockZ, bandIndex, warmProvinceClass(blockX, blockZ, bandIndex));
        }
        stashWarmPoolMembershipSnapshot(blockX, blockZ, bandIndex, candidate, out, preFilterPool, allowedPool);
        return out;
    }

    private static RegistryEntry<Biome> enforceLandBandPool(Collection<RegistryEntry<Biome>> biomes,
                                                            RegistryEntry<Biome> candidate,
                                                            int blockX,
                                                            int blockZ,
                                                            double t,
                                                            int bandIndex,
                                                            boolean mountainLike) {
        List<RegistryEntry<Biome>> preFilterPool = allowedLandPool(biomes, bandIndex);
        List<RegistryEntry<Biome>> allowedPool = preFilterPool;
        if (bandIndex == BAND_TEMPERATE && !mountainLike) {
            allowedPool = removeTemperateMountainFamily(allowedPool);
        }
        if (bandIndex == BAND_SUBTROPICAL && !mountainLike) {
            allowedPool = removeSubtropicalNonMountainWindsweptFamily(allowedPool);
        }
        if (bandIndex == BAND_POLAR) {
            List<RegistryEntry<Biome>> polarNoTaiga = removePolarTaigaFamily(allowedPool);
            if (!polarNoTaiga.isEmpty()) {
                allowedPool = polarNoTaiga;
            }
        }
        RegistryEntry<Biome> out = candidate;
        if (!allowedPool.isEmpty() && !isInAllowedLandPool(allowedPool, candidate)) {
            maybeLogBandLeak(blockX, blockZ, t, bandIndex, candidate);
            List<RegistryEntry<Biome>> rerollPool = allowedPool;
            if (bandIndex == BAND_SUBTROPICAL && !mountainLike) {
                List<RegistryEntry<Biome>> subtropicalNoForest = removeSubtropicalNonMountainForestFamily(rerollPool);
                if (!subtropicalNoForest.isEmpty()) {
                    rerollPool = subtropicalNoForest;
                }
            }
            out = pickFromAllowedLandPool(rerollPool, blockX, blockZ, bandIndex);
            recordWarmDryPath("DIRECT_POOL_PICK", candidate, out, blockX, blockZ, bandIndex, warmProvinceClass(blockX, blockZ, bandIndex));
        }
        stashWarmPoolMembershipSnapshot(blockX, blockZ, bandIndex, candidate, out, preFilterPool, allowedPool);
        return out;
    }

    private static RegistryEntry<Biome> enforcePaleGardenRegion(Registry<Biome> biomes,
                                                                RegistryEntry<Biome> candidate,
                                                                RegistryEntry<Biome> base,
                                                                int blockX,
                                                                int blockZ,
                                                                int bandIndex,
                                                                int effectiveRadius) {
        if (bandIndex != BAND_TEMPERATE) {
            return candidate;
        }
        boolean inOuter = paleGardenRegionHit(WORLD_SEED, blockX, blockZ, effectiveRadius);
        if (!inOuter) {
            // Outside dark-forest container: suppress any stray pale_garden.
            if (isBiomeId(candidate, "minecraft:pale_garden")) {
                if (isBiomeId(base, "minecraft:dark_forest")) {
                    return base;
                }
                try {
                    return biome(biomes, "minecraft:dark_forest");
                } catch (Throwable ignored) {
                    try {
                        return biome(biomes, "minecraft:forest");
                    } catch (Throwable ignoredAgain) {
                        return isBiomeId(base, "minecraft:pale_garden") ? candidate : base;
                    }
                }
            }
            return candidate;
        }
        // Inside dark-forest container: inner core => pale_garden, ring => dark_forest.
        boolean inCore = paleGardenCoreHit(WORLD_SEED, blockX, blockZ, effectiveRadius);
        if (inCore) {
            try {
                return biome(biomes, "minecraft:pale_garden");
            } catch (Throwable ignored) {
                return candidate;
            }
        }
        try {
            return biome(biomes, "minecraft:dark_forest");
        } catch (Throwable ignored) {
            return candidate;
        }
    }

    private static RegistryEntry<Biome> enforcePaleGardenRegion(Collection<RegistryEntry<Biome>> biomes,
                                                                RegistryEntry<Biome> candidate,
                                                                RegistryEntry<Biome> base,
                                                                int blockX,
                                                                int blockZ,
                                                                int bandIndex,
                                                                int effectiveRadius) {
        if (bandIndex != BAND_TEMPERATE) {
            return candidate;
        }
        boolean inOuter = paleGardenRegionHit(WORLD_SEED, blockX, blockZ, effectiveRadius);
        if (!inOuter) {
            // Outside dark-forest container: suppress any stray pale_garden.
            if (isBiomeId(candidate, "minecraft:pale_garden")) {
                if (isBiomeId(base, "minecraft:dark_forest")) {
                    return base;
                }
                RegistryEntry<Biome> darkForest = entryById(biomes, "minecraft:dark_forest");
                if (darkForest != null) {
                    return darkForest;
                }
                RegistryEntry<Biome> forest = entryById(biomes, "minecraft:forest");
                if (forest != null) {
                    return forest;
                }
                return isBiomeId(base, "minecraft:pale_garden") ? candidate : base;
            }
            return candidate;
        }
        // Inside dark-forest container: inner core => pale_garden, ring => dark_forest.
        boolean inCore = paleGardenCoreHit(WORLD_SEED, blockX, blockZ, effectiveRadius);
        if (inCore) {
            RegistryEntry<Biome> paleGarden = entryById(biomes, "minecraft:pale_garden");
            return paleGarden != null ? paleGarden : candidate;
        }
        RegistryEntry<Biome> darkForest = entryById(biomes, "minecraft:dark_forest");
        return darkForest != null ? darkForest : candidate;
    }

    private static List<RegistryEntry<Biome>> removeTemperateMountainFamily(List<RegistryEntry<Biome>> pool) {
        List<RegistryEntry<Biome>> filtered = new ArrayList<>(pool.size());
        for (RegistryEntry<Biome> entry : pool) {
            if (!isTemperateMountainFamilyBiome(entry)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private static List<RegistryEntry<Biome>> removeSubtropicalNonMountainWindsweptFamily(List<RegistryEntry<Biome>> pool) {
        List<RegistryEntry<Biome>> filtered = new ArrayList<>(pool.size());
        for (RegistryEntry<Biome> entry : pool) {
            if (!isBiomeId(entry, "minecraft:windswept_savanna")
                    && !isBiomeId(entry, "minecraft:meadow")) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private static List<RegistryEntry<Biome>> removeSubtropicalNonMountainForestFamily(List<RegistryEntry<Biome>> pool) {
        List<RegistryEntry<Biome>> filtered = new ArrayList<>(pool.size());
        for (RegistryEntry<Biome> entry : pool) {
            if (!isBiomeId(entry, "minecraft:forest")
                    && !isBiomeId(entry, "minecraft:birch_forest")
                    && !isBiomeId(entry, "minecraft:flower_forest")
                    && !isBiomeId(entry, "minecraft:dark_forest")
                    && !isBiomeId(entry, "minecraft:pale_garden")
                    && !isBiomeId(entry, "minecraft:old_growth_birch_forest")) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private static List<RegistryEntry<Biome>> removePolarTaigaFamily(List<RegistryEntry<Biome>> pool) {
        List<RegistryEntry<Biome>> filtered = new ArrayList<>(pool.size());
        for (RegistryEntry<Biome> entry : pool) {
            if (!isTaigaFamilyBiome(entry)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private static boolean isTemperateMountainFamilyBiome(RegistryEntry<Biome> entry) {
        return isBiomeId(entry, "minecraft:stony_peaks")
                || isBiomeId(entry, "minecraft:grove")
                || isBiomeId(entry, "minecraft:meadow")
                || isBiomeId(entry, "minecraft:windswept_hills")
                || isBiomeId(entry, "minecraft:windswept_forest")
                || isBiomeId(entry, "minecraft:windswept_gravelly_hills")
                || isBiomeId(entry, "minecraft:cherry_grove");
    }

    private static RegistryEntry<Biome> pickFromAllowedLandPool(List<RegistryEntry<Biome>> allowedPool,
                                                                int blockX,
                                                                int blockZ,
                                                                int bandIndex) {
        int size = allowedPool.size();
        if (size <= 0) {
            throw new IllegalStateException("allowedPool must not be empty");
        }

        int scaleBlocks = 2048;
        long seed = 0L;
        long salted = seed ^ (0x9E3779B97F4A7C15L * (long) bandIndex);
        double n = ValueNoise2D.sampleBlocks(salted, blockX, blockZ, scaleBlocks);
        int idx = (int) Math.floor(n * (double) size);
        if (idx >= size) {
            idx = size - 1;
        }
        return allowedPool.get(idx);
    }

    private static void maybeLogBandLeak(int blockX, int blockZ, double t, int bandIndex, RegistryEntry<Biome> candidate) {
        if (!DEBUG_LEAK) {
            return;
        }
        int count = LEAK_LOG_COUNT.incrementAndGet();
        if (count > LEAK_LOG_LIMIT) {
            return;
        }
        double latDeg = clamp(t, 0.0, 1.0) * 90.0;
        LOGGER.warn("LAT_LEAK baseBiome={} band={}({}) latDeg={} x={} z={} -> replacing",
                biomeId(candidate),
                bandName(bandIndex),
                bandIndex,
                String.format(java.util.Locale.ROOT, "%.2f", latDeg),
                blockX,
                blockZ);
    }

    private static final int TERRAIN_CLASS_FLAT_SHELF = 0;
    private static final int TERRAIN_CLASS_FLAT_LOWLAND = 1;
    private static final int TERRAIN_CLASS_RAISED_SHOULDER = 2;
    private static final int TERRAIN_CLASS_MOUNTAIN = 3;
    private static final int TERRAIN_POOL_SELECTION_SCALE_BLOCKS = 1152;
    private static final int COLD_SIBLING_SELECTION_SCALE_BLOCKS = 1536;

    private static RegistryEntry<Biome> applyTerrainCompatibilityGate(Registry<Biome> biomes,
                                                                      RegistryEntry<Biome> chosen,
                                                                      int bandIndex,
                                                                      int blockX,
                                                                      int blockZ,
                                                                      int centerHeight,
                                                                      int robustDelta,
                                                                      int seaLevel,
                                                                      int oceanDistance,
                                                                      boolean mountainNoiseLike,
                                                                      boolean mountainLike) {
        return rerollTerrainCompatibleCandidate(
                chosen,
                allowedLandPool(biomes, bandIndex),
                bandIndex,
                blockX,
                blockZ,
                centerHeight,
                robustDelta,
                seaLevel,
                oceanDistance,
                mountainNoiseLike,
                mountainLike);
    }

    private static RegistryEntry<Biome> applyTerrainCompatibilityGate(Collection<RegistryEntry<Biome>> biomes,
                                                                      RegistryEntry<Biome> chosen,
                                                                      int bandIndex,
                                                                      int blockX,
                                                                      int blockZ,
                                                                      int centerHeight,
                                                                      int robustDelta,
                                                                      int seaLevel,
                                                                      int oceanDistance,
                                                                      boolean mountainNoiseLike,
                                                                      boolean mountainLike) {
        return rerollTerrainCompatibleCandidate(
                chosen,
                allowedLandPool(biomes, bandIndex),
                bandIndex,
                blockX,
                blockZ,
                centerHeight,
                robustDelta,
                seaLevel,
                oceanDistance,
                mountainNoiseLike,
                mountainLike);
    }

    private static RegistryEntry<Biome> rerollTerrainCompatibleCandidate(RegistryEntry<Biome> chosen,
                                                                         List<RegistryEntry<Biome>> pool,
                                                                         int bandIndex,
                                                                         int blockX,
                                                                         int blockZ,
                                                                         int centerHeight,
                                                                         int robustDelta,
                                                                         int seaLevel,
                                                                         int oceanDistance,
                                                                         boolean mountainNoiseLike,
                                                                         boolean mountainLike) {
        if (chosen == null || pool.isEmpty()) {
            return chosen;
        }
        int terrainClass = terrainClassForSelection(centerHeight, robustDelta, seaLevel, oceanDistance, mountainNoiseLike, mountainLike);
        if (isBiomeCompatibleWithTerrain(chosen, terrainClass, mountainNoiseLike, mountainLike)) {
            return applyColdSiblingCoherence(chosen, pool, bandIndex, blockX, blockZ, terrainClass);
        }
        int size = pool.size();
        int start = continuousSelectionIndex(
                size,
                blockX,
                blockZ,
                bandIndex,
                terrainClass,
                0x54A1,
                TERRAIN_POOL_SELECTION_SCALE_BLOCKS);
        for (int i = 0; i < size; i++) {
            RegistryEntry<Biome> candidate = pool.get((start + i) % size);
            // Polar non-mountain: do not reroll into alpine — preserve the picker's guard
            if (bandIndex == BAND_POLAR && !mountainLike && !mountainNoiseLike
                    && (isBiomeId(candidate, "minecraft:snowy_slopes")
                        || isBiomeId(candidate, "minecraft:frozen_peaks")
                        || isBiomeId(candidate, "minecraft:jagged_peaks")
                        || isBiomeId(candidate, "minecraft:ice_spikes"))) {
                continue;
            }
            if (isBiomeCompatibleWithTerrain(candidate, terrainClass, mountainNoiseLike, mountainLike)) {
                return applyColdSiblingCoherence(candidate, pool, bandIndex, blockX, blockZ, terrainClass);
            }
        }
        if (bandIndex >= BAND_POLAR && terrainClass == TERRAIN_CLASS_FLAT_SHELF && isFlatPolarShelfBannedMountainPick(chosen)) {
            for (int i = 0; i < size; i++) {
                RegistryEntry<Biome> fallback = pool.get((start + i) % size);
                if (!isFlatPolarShelfBannedMountainPick(fallback)) {
                    return applyColdSiblingCoherence(fallback, pool, bandIndex, blockX, blockZ, terrainClass);
                }
            }
        }
        return chosen;
    }

    private static int continuousSelectionIndex(int size,
                                                int blockX,
                                                int blockZ,
                                                int bandIndex,
                                                int terrainClass,
                                                int salt,
                                                int scaleBlocks) {
        if (size <= 1) {
            return 0;
        }
        long mix = ((long) (bandIndex ^ (terrainClass << 8) ^ salt)) * 0x9E3779B97F4A7C15L;
        long salted = WORLD_SEED ^ mix;
        double n = ValueNoise2D.sampleBlocks(salted, blockX, blockZ, scaleBlocks);
        n = smoothstep(clamp(n, 0.0, 1.0));
        int idx = (int) Math.floor(n * (double) size);
        if (idx >= size) {
            idx = size - 1;
        }
        return idx;
    }

    private static RegistryEntry<Biome> pickDeterministicFromPool(List<RegistryEntry<Biome>> pool,
                                                                   int blockX,
                                                                   int blockZ,
                                                                   int bandIndex,
                                                                   int terrainClass,
                                                                   int salt) {
        if (pool.isEmpty()) {
            return null;
        }
        int idx = continuousSelectionIndex(
                pool.size(),
                blockX,
                blockZ,
                bandIndex,
                terrainClass,
                salt,
                TERRAIN_POOL_SELECTION_SCALE_BLOCKS);
        return pool.get(idx);
    }

    private static List<RegistryEntry<Biome>> flatPolarShelfPool(List<RegistryEntry<Biome>> pool) {
        List<RegistryEntry<Biome>> filtered = new ArrayList<>(pool.size());
        for (RegistryEntry<Biome> entry : pool) {
            if (!isFlatPolarShelfBannedMountainPick(entry)) {
                filtered.add(entry);
            }
        }
        filtered.sort(Comparator.comparing(LatitudeBiomes::biomeId));
        return filtered;
    }

    private static RegistryEntry<Biome> applyColdSiblingCoherence(RegistryEntry<Biome> candidate,
                                                                   List<RegistryEntry<Biome>> pool,
                                                                   int bandIndex,
                                                                   int blockX,
                                                                   int blockZ,
                                                                   int terrainClass) {
        if (candidate == null || bandIndex < BAND_SUBPOLAR || !isColdSiblingBiome(candidate) || terrainClass >= TERRAIN_CLASS_MOUNTAIN) {
            return candidate;
        }
        List<RegistryEntry<Biome>> siblings = new ArrayList<>();
        for (RegistryEntry<Biome> entry : pool) {
            if (isColdSiblingBiome(entry) && isColdSiblingTerrainCompatible(entry, terrainClass)) {
                siblings.add(entry);
            }
        }
        if (siblings.isEmpty()) {
            return candidate;
        }
        siblings.sort(Comparator.comparing(LatitudeBiomes::biomeId));
        int idx = continuousSelectionIndex(
                siblings.size(),
                blockX,
                blockZ,
                bandIndex,
                terrainClass,
                0x2A11,
                COLD_SIBLING_SELECTION_SCALE_BLOCKS);
        return siblings.get(idx);
    }

    private static boolean isColdSiblingBiome(RegistryEntry<Biome> candidate) {
        return isBiomeId(candidate, "minecraft:taiga")
                || isBiomeId(candidate, "minecraft:snowy_taiga")
                || isBiomeId(candidate, "minecraft:grove")
                || isBiomeId(candidate, "minecraft:old_growth_spruce_taiga")
                || isBiomeId(candidate, "minecraft:old_growth_pine_taiga");
    }

    private static boolean isColdSiblingTerrainCompatible(RegistryEntry<Biome> candidate, int terrainClass) {
        boolean taigaBase = isBiomeId(candidate, "minecraft:taiga")
                || isBiomeId(candidate, "minecraft:old_growth_spruce_taiga")
                || isBiomeId(candidate, "minecraft:old_growth_pine_taiga");
        if (terrainClass >= TERRAIN_CLASS_RAISED_SHOULDER) {
            return !taigaBase;
        }
        if (terrainClass == TERRAIN_CLASS_FLAT_SHELF || terrainClass == TERRAIN_CLASS_FLAT_LOWLAND) {
            return !isBiomeId(candidate, "minecraft:grove");
        }
        return true;
    }

    private static int terrainClassForSelection(int centerHeight,
                                                int robustDelta,
                                                int seaLevel,
                                                int oceanDistance,
                                                boolean mountainNoiseLike,
                                                boolean mountainLike) {
        boolean nearSeaLevel = centerHeight <= seaLevel + 1;
        boolean flat = robustDelta <= 1;
        boolean oceanAdjacentShelf = oceanDistance >= 0 && oceanDistance <= 64;
        if (nearSeaLevel && flat && oceanAdjacentShelf) {
            return TERRAIN_CLASS_FLAT_SHELF;
        }
        if (mountainLike || mountainNoiseLike || robustDelta >= (WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST)) {
            return TERRAIN_CLASS_MOUNTAIN;
        }
        if (centerHeight >= seaLevel + 4 || robustDelta >= 3) {
            return TERRAIN_CLASS_RAISED_SHOULDER;
        }
        return TERRAIN_CLASS_FLAT_LOWLAND;
    }

    private static boolean isBiomeCompatibleWithTerrain(RegistryEntry<Biome> candidate,
                                                        int terrainClass,
                                                        boolean mountainNoiseLike,
                                                        boolean mountainLike) {
        boolean mountainPick = isMountainCodedColdPick(candidate);
        boolean plainsPick = isPlainsFamily(candidate);
        if (terrainClass == TERRAIN_CLASS_FLAT_SHELF && mountainPick) {
            return false;
        }
        if ((terrainClass == TERRAIN_CLASS_RAISED_SHOULDER || terrainClass == TERRAIN_CLASS_MOUNTAIN) && plainsPick) {
            return false;
        }
        if (mountainPick && !(mountainLike || mountainNoiseLike || terrainClass >= TERRAIN_CLASS_RAISED_SHOULDER)) {
            return false;
        }
        return true;
    }

    private static boolean isFlatPolarShelf(int centerHeight,
                                            int robustDelta,
                                            int seaLevel,
                                            boolean mountainNoiseLike,
                                            boolean mountainLike) {
        return centerHeight <= seaLevel + 1
                && robustDelta <= 1
                && !mountainLike
                && !mountainNoiseLike;
    }

    // Synthetic authority values used when real terrain probes are absent (atlas/headless).
    // Chosen to exactly satisfy the existing polarMountainAuthority() thresholds
    // (robustDelta >= 18, centerHeight >= 110), producing parity with a confirmed-mountain cell.
    private static final int POLAR_AUTHORITY_PARITY_DELTA  = 18;
    private static final int POLAR_AUTHORITY_PARITY_HEIGHT = 111;

    private static boolean polarMountainAuthority(int robustDelta, int centerHeight, int landBandIndex) {
        if (landBandIndex != BAND_POLAR) {
            return false;
        }
        boolean robustKnown = robustDelta != Integer.MIN_VALUE;
        boolean heightKnown = centerHeight != Integer.MIN_VALUE;
        if (!robustKnown && !heightKnown) {
            return false;
        }
        if (robustKnown && robustDelta >= 18) {
            return true;
        }
        return heightKnown && centerHeight >= 110;
    }

    private static boolean isExtremePolarCap(double latDeg) {
        return latDeg >= EXTREME_POLAR_CAP_MIN_DEG;
    }

    /**
     * Public entry point for extreme-polar-cap detection, usable from mixins.
     * Uses the active radius override (if set) or the border radius.
     * World-size-safe: degree threshold, not hardcoded block offsets.
     */
    /**
     * Checks extreme-polar-cap membership for a given blockZ.
     * Requires a borderRadius fallback because ACTIVE_RADIUS_BLOCKS may be 0
     * during early worldgen before any pick() call sets it.
     */
    public static boolean isBlockInExtremePolarCap(int blockZ, int borderRadiusFallback) {
        int radius = getActiveRadiusBlocks();
        if (radius <= 0) radius = borderRadiusFallback;
        double latDeg = Math.abs((double) blockZ) * 90.0 / Math.max(1, radius);
        return latDeg >= EXTREME_POLAR_CAP_MIN_DEG;
    }

    private static boolean isFlatPolarShelfBannedMountainPick(RegistryEntry<Biome> candidate) {
        return isBiomeId(candidate, "minecraft:jagged_peaks")
                || isBiomeId(candidate, "minecraft:frozen_peaks")
                || isBiomeId(candidate, "minecraft:snowy_slopes");
    }

    /** Alpine biomes requiring polar mountain authority to survive the non-mountain clamp. */
    private static boolean isPolarAlpineBiome(RegistryEntry<Biome> candidate) {
        return isBiomeId(candidate, "minecraft:jagged_peaks")
                || isBiomeId(candidate, "minecraft:frozen_peaks")
                || isBiomeId(candidate, "minecraft:snowy_slopes")
                || isBiomeId(candidate, "minecraft:ice_spikes");
    }

    private static boolean isExtremePolarGroveLeak(RegistryEntry<Biome> candidate) {
        return isBiomeId(candidate, "minecraft:grove")
                || isBiomeId(candidate, "minecraft:cherry_grove");
    }

    /**
     * True for biomes that must not appear in the extreme polar cap (≥85°).
     * These are soft, vegetated, or village-friendly relative to 85°+ polar ecology.
     *
     * Authority: used exclusively by clampExtremePolarCapOutput, which fires before
     * the alpine-authority check. Alpine biomes (jagged_peaks, frozen_peaks, snowy_slopes,
     * ice_spikes) are intentionally NOT listed here — they are preserved or removed by
     * the downstream polarMountainAuthority check in clampFinalPolarNonMountainAlpineOutput.
     *
     * Uses explicit isBiomeId() checks (not path-string matching) to avoid silent
     * false-negatives when registry-key resolution returns Optional.empty().
     * Path-based catch-all is kept as secondary safety net only.
     */
    private static boolean isExtremePolarSoftColdLeak(RegistryEntry<Biome> candidate) {
        // Explicit primary checks — all biomes that are ecologically invalid at 85°+.
        if (isBiomeId(candidate, "minecraft:grove")
                || isBiomeId(candidate, "minecraft:cherry_grove")
                || isBiomeId(candidate, "minecraft:snowy_taiga")
                || isBiomeId(candidate, "minecraft:taiga")
                || isBiomeId(candidate, "minecraft:old_growth_spruce_taiga")
                || isBiomeId(candidate, "minecraft:old_growth_pine_taiga")
                || isBiomeId(candidate, "minecraft:dark_forest")
                || isBiomeId(candidate, "minecraft:forest")
                || isBiomeId(candidate, "minecraft:flower_forest")
                || isBiomeId(candidate, "minecraft:birch_forest")
                || isBiomeId(candidate, "minecraft:old_growth_birch_forest")
                || isBiomeId(candidate, "minecraft:windswept_forest")) {
            return true;
        }
        // Path-based catch-all for any unlisted biome whose ID path contains "forest" or "taiga".
        String path = candidate.getKey().map(key -> key.getValue().getPath()).orElse("");
        return path.contains("forest") || path.contains("taiga");
    }

    private static boolean isMountainCodedColdPick(RegistryEntry<Biome> candidate) {
        return isFlatPolarShelfBannedMountainPick(candidate);
    }

    private static boolean isPlainsFamily(RegistryEntry<Biome> candidate) {
        return isBiomeId(candidate, "minecraft:plains")
                || isBiomeId(candidate, "minecraft:snowy_plains");
    }

    // Final-return clamp: polar non-mountain cells must never output alpine biomes.
    // This fires AFTER all upstream guards, sanitize, enforcement, and post-processing.
    private static RegistryEntry<Biome> clampExtremePolarCapOutput(Registry<Biome> biomes,
                                                                    RegistryEntry<Biome> out,
                                                                    int landBandIndex,
                                                                    double latDeg) {
        if (landBandIndex != BAND_POLAR || !isExtremePolarCap(latDeg) || !isExtremePolarSoftColdLeak(out)) {
            return out;
        }
        try {
            return biome(biomes, "minecraft:snowy_plains");
        } catch (Throwable ignored) {
            return out;
        }
    }

    private static RegistryEntry<Biome> clampFinalPolarNonMountainAlpineOutput(Registry<Biome> biomes,
                                                                                RegistryEntry<Biome> out, int landBandIndex,
                                                                                double latDeg,
                                                                                int centerHeight,
                                                                                int robustDelta) {
        out = clampExtremePolarCapOutput(biomes, out, landBandIndex, latDeg);
        boolean polarAuthority = polarMountainAuthority(robustDelta, centerHeight, landBandIndex);
        if (landBandIndex != BAND_POLAR || polarAuthority) {
            return out;
        }
        if (!isFlatPolarShelfBannedMountainPick(out)) {
            return out;
        }
        try {
            return biome(biomes, "minecraft:snowy_plains");
        } catch (Throwable ignored) {
            return out;
        }
    }

    private static RegistryEntry<Biome> clampExtremePolarCapOutput(Collection<RegistryEntry<Biome>> biomes,
                                                                    RegistryEntry<Biome> out,
                                                                    int landBandIndex,
                                                                    double latDeg) {
        if (landBandIndex != BAND_POLAR || !isExtremePolarCap(latDeg) || !isExtremePolarSoftColdLeak(out)) {
            return out;
        }
        RegistryEntry<Biome> safe = entryById(biomes, "minecraft:snowy_plains");
        return safe != null ? safe : out;
    }

    private static RegistryEntry<Biome> clampFinalPolarNonMountainAlpineOutput(Collection<RegistryEntry<Biome>> biomes,
                                                                                RegistryEntry<Biome> out, int landBandIndex,
                                                                                double latDeg,
                                                                                int centerHeight,
                                                                                int robustDelta) {
        out = clampExtremePolarCapOutput(biomes, out, landBandIndex, latDeg);
        boolean polarAuthority = polarMountainAuthority(robustDelta, centerHeight, landBandIndex);
        if (landBandIndex != BAND_POLAR || polarAuthority) {
            return out;
        }
        if (!isFlatPolarShelfBannedMountainPick(out)) {
            return out;
        }
        RegistryEntry<Biome> safe = entryById(biomes, "minecraft:snowy_plains");
        return safe != null ? safe : out;
    }

    private static RegistryEntry<Biome> gatePolarTaigaSurvival(Registry<Biome> biomes,
                                                                RegistryEntry<Biome> out,
                                                                int landBandIndex,
                                                                double latDeg,
                                                                int blockX, int blockZ) {
        boolean polarRange = landBandIndex >= BAND_POLAR || latDeg >= LatitudeBands.Band.POLAR.lowDeg();
        if (!polarRange || !isTaigaFamilyBiome(out)) {
            return out;
        }
        if (DEBUG_PROVINCE) {
            int count = PROVINCE_DEBUG_COUNT.get();
            if (count <= PROVINCE_DEBUG_LIMIT) {
                LOGGER.info("[LAT][POLAR_TAIGA_GATE] x={} z={} band=POLAR biome={} -> snowy_plains",
                        blockX, blockZ, biomeId(out));
            }
        }
        try {
            return biome(biomes, "minecraft:snowy_plains");
        } catch (Throwable ignored) {
            return out;
        }
    }

    private static RegistryEntry<Biome> gatePolarTaigaSurvival(Collection<RegistryEntry<Biome>> biomes,
                                                                RegistryEntry<Biome> out,
                                                                int landBandIndex,
                                                                double latDeg,
                                                                int blockX, int blockZ) {
        boolean polarRange = landBandIndex >= BAND_POLAR || latDeg >= LatitudeBands.Band.POLAR.lowDeg();
        if (!polarRange || !isTaigaFamilyBiome(out)) {
            return out;
        }
        if (DEBUG_PROVINCE) {
            int count = PROVINCE_DEBUG_COUNT.get();
            if (count <= PROVINCE_DEBUG_LIMIT) {
                LOGGER.info("[LAT][POLAR_TAIGA_GATE] x={} z={} band=POLAR biome={} -> snowy_plains",
                        blockX, blockZ, biomeId(out));
            }
        }
        RegistryEntry<Biome> safe = entryById(biomes, "minecraft:snowy_plains");
        return safe != null ? safe : out;
    }

    private static RegistryEntry<Biome> gateWarmJungleSurvival(Registry<Biome> biomes,
                                                               RegistryEntry<Biome> out,
                                                               int landBandIndex,
                                                               int blockX, int blockZ) {
        if (landBandIndex > BAND_SUBTROPICAL || !isJungleFamily(out)) {
            return out;
        }
        ProvinceAuthority.Province province = classifyProvince(blockX, blockZ);
        if (province == null || province == ProvinceAuthority.Province.WARM_WET) {
            return out;
        }
        if (province == ProvinceAuthority.Province.WARM_MEDIUM) {
            if (isBiomeId(out, "minecraft:sparse_jungle")) {
                return out;
            }
            if (DEBUG_PROVINCE) {
                int count = PROVINCE_DEBUG_COUNT.get();
                if (count <= PROVINCE_DEBUG_LIMIT) {
                    LOGGER.info("[LAT][PROVINCE][WARM_JUNGLE_GATE] x={} z={} province={} biome={} -> sparse_jungle",
                            blockX, blockZ, province, biomeId(out));
                }
            }
            try {
                return biome(biomes, "minecraft:sparse_jungle");
            } catch (Throwable ignored) {
                return out;
            }
        }
        if (DEBUG_PROVINCE) {
            int count = PROVINCE_DEBUG_COUNT.get();
            if (count <= PROVINCE_DEBUG_LIMIT) {
                LOGGER.info("[LAT][PROVINCE][WARM_JUNGLE_GATE] x={} z={} province={} biome={} -> dryWarmFallback",
                        blockX, blockZ, province, biomeId(out));
            }
        }
        return pickDryWarmFallback(biomes, out);
    }

    private static RegistryEntry<Biome> gateWarmJungleSurvival(Collection<RegistryEntry<Biome>> biomes,
                                                               RegistryEntry<Biome> out,
                                                               int landBandIndex,
                                                               int blockX, int blockZ) {
        if (landBandIndex > BAND_SUBTROPICAL || !isJungleFamily(out)) {
            return out;
        }
        ProvinceAuthority.Province province = classifyProvince(blockX, blockZ);
        if (province == null || province == ProvinceAuthority.Province.WARM_WET) {
            return out;
        }
        if (province == ProvinceAuthority.Province.WARM_MEDIUM) {
            if (isBiomeId(out, "minecraft:sparse_jungle")) {
                return out;
            }
            if (DEBUG_PROVINCE) {
                int count = PROVINCE_DEBUG_COUNT.get();
                if (count <= PROVINCE_DEBUG_LIMIT) {
                    LOGGER.info("[LAT][PROVINCE][WARM_JUNGLE_GATE] x={} z={} province={} biome={} -> sparse_jungle",
                            blockX, blockZ, province, biomeId(out));
                }
            }
            RegistryEntry<Biome> sparse = entryById(biomes, "minecraft:sparse_jungle");
            return sparse != null ? sparse : out;
        }
        if (DEBUG_PROVINCE) {
            int count = PROVINCE_DEBUG_COUNT.get();
            if (count <= PROVINCE_DEBUG_LIMIT) {
                LOGGER.info("[LAT][PROVINCE][WARM_JUNGLE_GATE] x={} z={} province={} biome={} -> dryWarmFallback",
                        blockX, blockZ, province, biomeId(out));
            }
        }
        return pickDryWarmFallback(biomes, out);
    }

    private static boolean isDryWarmIdentity(RegistryEntry<Biome> entry) {
        if (entry == null) return false;
        return isSavannaFamily(entry)
                || isBadlandsFamily(entry)
                || isBiomeId(entry, "minecraft:desert");
    }

    private static RegistryEntry<Biome> gateDryWarmIdentity(Registry<Biome> biomes,
                                                             RegistryEntry<Biome> out,
                                                             int landBandIndex,
                                                             int blockX, int blockZ) {
        if (landBandIndex > BAND_SUBTROPICAL || isDryWarmIdentity(out)) {
            return out;
        }
        ProvinceAuthority.Province province = classifyProvince(blockX, blockZ);
        if (province != ProvinceAuthority.Province.WARM_DRY) {
            return out;
        }
        if (DEBUG_PROVINCE) {
            int count = PROVINCE_DEBUG_COUNT.get();
            if (count <= PROVINCE_DEBUG_LIMIT) {
                LOGGER.info("[LAT][PROVINCE][DRY_WARM_GATE] x={} z={} province={} biome={} -> dryWarmFallback",
                        blockX, blockZ, province, biomeId(out));
            }
        }
        try {
            return biome(biomes, "minecraft:savanna");
        } catch (Throwable ignored) {
            return out;
        }
    }

    private static RegistryEntry<Biome> gateDryWarmIdentity(Collection<RegistryEntry<Biome>> biomes,
                                                             RegistryEntry<Biome> out,
                                                             int landBandIndex,
                                                             int blockX, int blockZ) {
        if (landBandIndex > BAND_SUBTROPICAL || isDryWarmIdentity(out)) {
            return out;
        }
        ProvinceAuthority.Province province = classifyProvince(blockX, blockZ);
        if (province != ProvinceAuthority.Province.WARM_DRY) {
            return out;
        }
        if (DEBUG_PROVINCE) {
            int count = PROVINCE_DEBUG_COUNT.get();
            if (count <= PROVINCE_DEBUG_LIMIT) {
                LOGGER.info("[LAT][PROVINCE][DRY_WARM_GATE] x={} z={} province={} biome={} -> dryWarmFallback",
                        blockX, blockZ, province, biomeId(out));
            }
        }
        RegistryEntry<Biome> safe = entryById(biomes, "minecraft:savanna");
        return safe != null ? safe : out;
    }

    private static String bandName(int bandIndex) {
        return switch (bandIndex) {
            case BAND_TROPICAL -> "TROPICAL";
            case BAND_SUBTROPICAL -> "SUBTROPICAL";
            case BAND_TEMPERATE -> "TEMPERATE";
            case BAND_SUBPOLAR -> "SUBPOLAR";
            default -> "POLAR";
        };
    }

    private static List<RegistryEntry<Biome>> filterMangrove(List<RegistryEntry<Biome>> entries) {
        if (entries.isEmpty()) {
            return entries;
        }
        List<RegistryEntry<Biome>> filtered = new ArrayList<>(entries.size());
        for (RegistryEntry<Biome> entry : entries) {
            if (!isMangroveCandidate(entry)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private static List<RegistryEntry<Biome>> filterSwamp(List<RegistryEntry<Biome>> entries) {
        if (entries.isEmpty()) {
            return entries;
        }
        List<RegistryEntry<Biome>> filtered = new ArrayList<>(entries.size());
        for (RegistryEntry<Biome> entry : entries) {
            if (!isSwampCandidate(entry)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private static RegistryEntry<Biome> entryById(Collection<RegistryEntry<Biome>> biomes, String id) {
        Identifier target = Identifier.of(id);
        for (RegistryEntry<Biome> entry : biomes) {
            var key = entry.getKey();
            if (key.isPresent() && key.get().getValue().equals(target)) {
                return entry;
            }
        }
        return null;
    }

    private static boolean nearRiverLike(int blockX, int blockZ, MultiNoiseUtil.MultiNoiseSampler sampler) {
        if (sampler == null) {
            return false;
        }
        int noiseX = blockX >> 2;
        int noiseZ = blockZ >> 2;
        MultiNoiseUtil.NoiseValuePoint point = sampler.sample(noiseX, SURFACE_CLASSIFY_Y >> 2, noiseZ);
        double erosion = MultiNoiseUtil.toFloat(point.erosionNoise());
        double weird = MultiNoiseUtil.toFloat(point.weirdnessNoise());
        // Rivers tend to follow flat, low-weirdness corridors.
        return erosion > 0.25 && Math.abs(weird) < 0.08;
    }

    private static boolean shouldInviteMangrove(int blockX, int blockY, int blockZ, int bandIndex,
                                                MultiNoiseUtil.MultiNoiseSampler sampler, boolean nearOcean) {
        if (!nearOcean) {
            return false;
        }
        if (bandIndex > BAND_SUBTROPICAL) {
            return false;
        }
        if (sampler == null) {
            return false;
        }
        int noiseX = blockX >> 2;
        int noiseZ = blockZ >> 2;
        MultiNoiseUtil.NoiseValuePoint p = sampler.sample(noiseX, SURFACE_CLASSIFY_Y >> 2, noiseZ);
        double cont = MultiNoiseUtil.toFloat(p.continentalnessNoise());
        double erosion = MultiNoiseUtil.toFloat(p.erosionNoise());
        double weird = MultiNoiseUtil.toFloat(p.weirdnessNoise());
        int oceanDist = oceanDistanceBlocks(blockX, blockZ, sampler);

        boolean coastal = cont < MANGROVE_CONTINENTALNESS_MAX;
        boolean invitePrimary = coastal
                && oceanDist <= MANGROVE_PRIMARY_INVITE_COAST_MAX_BLOCKS
                && erosion > 0.25
                && Math.abs(weird) < 0.40;

        boolean coastalSecondary = coastal
                && oceanDist <= MANGROVE_SECONDARY_COAST_MAX_BLOCKS
                && erosion > MANGROVE_SECONDARY_MIN_EROSION
                && Math.abs(weird) < MANGROVE_SECONDARY_MAX_ABS_WEIRDNESS;

        boolean trueCoastRecovery = coastal
                && oceanDist <= MANGROVE_TRUE_COAST_RECOVERY_MAX_BLOCKS
                && erosion > MANGROVE_MIN_EROSION
                && Math.abs(weird) < MANGROVE_MAX_ABS_WEIRDNESS;

        boolean invite = invitePrimary || coastalSecondary || trueCoastRecovery;
        if (DEBUG_MANGROVE_INVITE) {
            long n = MANGROVE_INVITE_LOG_COUNT.incrementAndGet();
            if (invite || n <= 50 || n % 50000L == 0L) {
                String path = invitePrimary ? "primary" : (coastalSecondary ? "secondary" : (trueCoastRecovery ? "recovery" : "none"));
                LOGGER.info("[latdev] mangroveInviteProbe x={} z={} cont={}; erosion={}; weird={}; nearOcean={}; oceanDist={}; invite={}; path={} count={}",
                        blockX, blockZ,
                        String.format(java.util.Locale.ROOT, "%.3f", cont),
                        String.format(java.util.Locale.ROOT, "%.3f", erosion),
                        String.format(java.util.Locale.ROOT, "%.3f", weird),
                        nearOcean,
                        oceanDist,
                        invite,
                        path,
                        n);
            }
        }
        return invite;
    }

    private static boolean rollChance(int blockX, int blockZ, int salt, long denominator) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long roll = hash64(chunkX, chunkZ, salt);
        return Long.remainderUnsigned(roll, denominator) == 0L;
    }

    private static boolean isBiomeId(RegistryEntry<Biome> entry, String id) {
        if (entry == null) {
            return false;
        }
        Identifier target = Identifier.of(id);
        return entry.getKey()
                .map(key -> key.getValue().equals(target))
                .orElse(false);
    }

    private static String biomeId(RegistryEntry<Biome> entry) {
        if (entry == null) {
            return "null";
        }
        return entry.getKey().map(key -> key.getValue().toString()).orElse("?");
    }

    private static boolean isColdBiome(RegistryEntry<Biome> entry) {
        if (entry == null) {
            return false;
        }
        String path = entry.getKey().map(key -> key.getValue().getPath()).orElse("");
        return path.contains("snow") || path.contains("ice") || path.contains("frozen");
    }

    private static boolean isSnowyVariant(RegistryEntry<Biome> entry) {
        if (entry == null) {
            return false;
        }
        String path = entry.getKey().map(key -> key.getValue().getPath()).orElse("");
        return path.contains("snow") || path.contains("ice") || path.contains("frozen");
    }

    private static boolean isGroveBiome(RegistryEntry<Biome> entry) {
        return isBiomeId(entry, "minecraft:grove");
    }

    private static boolean isTaigaFamilyBiome(RegistryEntry<Biome> entry) {
        if (entry == null) return false;
        return isBiomeId(entry, "minecraft:taiga")
                || isBiomeId(entry, "minecraft:snowy_taiga")
                || isBiomeId(entry, "minecraft:old_growth_pine_taiga")
                || isBiomeId(entry, "minecraft:old_growth_spruce_taiga");
    }

    private static int temperateWarmEdgeShoulderBlocks(int effectiveRadius) {
        if (effectiveRadius <= 0) {
            return TEMPERATE_WARM_EDGE_SHOULDER_MIN_BLOCKS;
        }
        int temperateStart = bandBoundaryBlocks(1, effectiveRadius);
        int temperateEnd = bandBoundaryBlocks(2, effectiveRadius);
        int temperateSpan = Math.max(1, temperateEnd - temperateStart);
        int shoulder = (int) Math.round(temperateSpan * TEMPERATE_WARM_EDGE_SHOULDER_FRAC);
        return clampInt(shoulder, TEMPERATE_WARM_EDGE_SHOULDER_MIN_BLOCKS, TEMPERATE_WARM_EDGE_SHOULDER_MAX_BLOCKS);
    }

    private static double subtropicalTemperateBoundaryDeltaBlocks(int blockX, int blockZ, int effectiveRadius) {
        if (effectiveRadius <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        int boundaryBlocks = bandBoundaryBlocks(1, effectiveRadius);
        double halfWidthBlocks = BLEND_TRANSITION_WIDTH_BLOCKS * 0.5;
        if (!(halfWidthBlocks > 0.0)) {
            return Math.abs(blockZ) - boundaryBlocks;
        }

        double diameter = effectiveRadius * 2.0;
        double noiseScale = diameter > 0.0 ? (REFERENCE_DIAMETER_BLOCKS / diameter) : 1.0;
        double warpPatchBlocks = scaledPatchBlocks(WARP_NOISE_PATCH_CHUNKS, noiseScale);

        long warpSeed = WORLD_SEED ^ WARP_NOISE_SALT;
        double warpNoise = (blobNoise01ScaledBlocks(warpSeed, blockX, blockZ, warpPatchBlocks, WARP_NOISE_SALT) * 2.0) - 1.0;
        double maxWarp = Math.min(WARP_AMPLITUDE_BLOCKS, halfWidthBlocks);
        double effectiveBoundary = boundaryBlocks + (warpNoise * maxWarp);
        return Math.abs(blockZ) - effectiveBoundary;
    }

    private static boolean isTemperateWarmEdgeShoulderCell(int blockX, int blockZ,
                                                            int effectiveRadius, int sourceBandIndex,
                                                            int landBandIndex, boolean mountainLike) {
        if (landBandIndex != BAND_TEMPERATE || mountainLike || effectiveRadius <= 0) {
            return false;
        }
        double deltaBlocks = subtropicalTemperateBoundaryDeltaBlocks(blockX, blockZ, effectiveRadius);
        int shoulderBlocks = temperateWarmEdgeShoulderBlocks(effectiveRadius);
        if (sourceBandIndex <= BAND_SUBTROPICAL) {
            // Blend-elevated subtropical cell: crisp band is subtropical but resolved to temperate
            // by stochastic blending. The block is geographically equatorward of the warped boundary
            // so deltaBlocks is negative and latDeg < TEMPERATE_WARM_EDGE_LAT_MIN_DEG — both gates
            // that follow would reject it. Skip the lat gate here; abs(deltaBlocks) <= shoulderBlocks
            // already bounds the spatial reach to ±shoulderBlocks of the warped boundary.
            return Math.abs(deltaBlocks) <= shoulderBlocks;
        }
        // Crisp-temperate cell: apply lat gate then check poleward-only shoulder (existing behavior).
        double latDeg = latitudeDegreesFromRadius(blockZ, effectiveRadius);
        if (latDeg < TEMPERATE_WARM_EDGE_LAT_MIN_DEG || latDeg > TEMPERATE_WARM_EDGE_LAT_MAX_DEG) {
            return false;
        }
        return deltaBlocks >= 0.0 && deltaBlocks <= shoulderBlocks;
    }

    private static boolean isTemperateWarmEdgeTransitionBiome(RegistryEntry<Biome> biome) {
        if (biome == null) {
            return false;
        }
        return isBiomeId(biome, "minecraft:plains")
                || isBiomeId(biome, "minecraft:sunflower_plains")
                || isBiomeId(biome, "minecraft:meadow")
                || isBiomeId(biome, "minecraft:flower_forest")
                || isBiomeId(biome, "minecraft:birch_forest")
                || isBiomeId(biome, "minecraft:old_growth_birch_forest");
    }

    private static boolean isTemperateShoulderHeavyBiome(RegistryEntry<Biome> biome) {
        if (biome == null) {
            return false;
        }
        return isBiomeId(biome, "minecraft:forest")
                || isBiomeId(biome, "minecraft:dark_forest")
                || isBiomeId(biome, "minecraft:windswept_forest")
                || isTaigaFamilyBiome(biome);
    }

    private static int temperateWarmEdgeFallbackStartIndex(int blockX, int blockZ, int size) {
        if (size <= 1) {
            return 0;
        }
        double n = ValueNoise2D.sampleBlocks(WORLD_SEED ^ TEMPERATE_WARM_EDGE_ROLL_SALT, blockX, blockZ, 2048);
        int idx = (int) Math.floor(n * (double) size);
        return clampInt(idx, 0, size - 1);
    }

    private static RegistryEntry<Biome> pickTemperateWarmEdgeTransitionFallback(Registry<Biome> biomes, RegistryEntry<Biome> base,
                                                                                int blockX, int blockZ) {
        int size = TEMPERATE_WARM_EDGE_TRANSITION_BIOMES.length;
        int start = temperateWarmEdgeFallbackStartIndex(blockX, blockZ, size);
        for (int i = 0; i < size; i++) {
            String option = TEMPERATE_WARM_EDGE_TRANSITION_BIOMES[(start + i) % size];
            try {
                RegistryEntry<Biome> candidate = biome(biomes, option);
                if (isTemperateWarmEdgeTransitionBiome(candidate)) {
                    return candidate;
                }
            } catch (Throwable ignored) {
                // try next option
            }
        }
        return isTemperateWarmEdgeTransitionBiome(base) ? base : null;
    }

    private static RegistryEntry<Biome> pickTemperateWarmEdgeTransitionFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base,
                                                                                int blockX, int blockZ) {
        int size = TEMPERATE_WARM_EDGE_TRANSITION_BIOMES.length;
        int start = temperateWarmEdgeFallbackStartIndex(blockX, blockZ, size);
        for (int i = 0; i < size; i++) {
            String option = TEMPERATE_WARM_EDGE_TRANSITION_BIOMES[(start + i) % size];
            RegistryEntry<Biome> candidate = entryById(biomes, option);
            if (isTemperateWarmEdgeTransitionBiome(candidate)) {
                return candidate;
            }
        }
        return isTemperateWarmEdgeTransitionBiome(base) ? base : null;
    }

    private static RegistryEntry<Biome> softenTemperateWarmEdgeTaigaJump(Registry<Biome> biomes, RegistryEntry<Biome> base,
                                                                          RegistryEntry<Biome> out,
                                                                          int blockX, int blockZ, int effectiveRadius,
                                                                          int sourceBandIndex, int landBandIndex, boolean mountainLike) {
        if (!isTemperateShoulderHeavyBiome(out)) {
            return out;
        }
        if (!isTemperateWarmEdgeShoulderCell(blockX, blockZ, effectiveRadius, sourceBandIndex, landBandIndex, mountainLike)) {
            return out;
        }
        RegistryEntry<Biome> fallback = pickTemperateWarmEdgeTransitionFallback(biomes, base, blockX, blockZ);
        return fallback != null ? fallback : out;
    }

    private static RegistryEntry<Biome> softenTemperateWarmEdgeTaigaJump(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base,
                                                                          RegistryEntry<Biome> out,
                                                                          int blockX, int blockZ, int effectiveRadius,
                                                                          int sourceBandIndex, int landBandIndex, boolean mountainLike) {
        if (!isTemperateShoulderHeavyBiome(out)) {
            return out;
        }
        if (!isTemperateWarmEdgeShoulderCell(blockX, blockZ, effectiveRadius, sourceBandIndex, landBandIndex, mountainLike)) {
            return out;
        }
        RegistryEntry<Biome> fallback = pickTemperateWarmEdgeTransitionFallback(biomes, base, blockX, blockZ);
        return fallback != null ? fallback : out;
    }

    private static double snowyRampAlpha(double deg) {
        if (deg <= SNOWY_RAMP_START_DEG) {
            return 0.0;
        }
        if (deg >= SNOWY_RAMP_FULL_DEG) {
            return 1.0;
        }
        double t = clamp((deg - SNOWY_RAMP_START_DEG) / (SNOWY_RAMP_FULL_DEG - SNOWY_RAMP_START_DEG), 0.0, 1.0);
        return smoothstep(t);
    }

    private static RegistryEntry<Biome> pickNonSnowyFallback(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, int bandIndex) {
        if (base != null && !isSnowyVariant(base) && !isGroveBiome(base)) {
            return base;
        }
        String[] options = bandIndex <= BAND_TEMPERATE
                ? new String[]{"minecraft:taiga", "minecraft:forest", "minecraft:plains", "minecraft:meadow"}
                : new String[]{"minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:meadow", "minecraft:forest", "minecraft:plains"};
        for (String option : options) {
            try {
                RegistryEntry<Biome> entry = biome(biomes, option);
                if (!isSnowyVariant(entry) && !isGroveBiome(entry)) {
                    return entry;
                }
            } catch (Throwable ignored) {
            }
        }
        return base;
    }

    private static RegistryEntry<Biome> pickNonSnowyFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int bandIndex) {
        if (base != null && !isSnowyVariant(base) && !isGroveBiome(base)) {
            return base;
        }
        String[] options = bandIndex <= BAND_TEMPERATE
                ? new String[]{"minecraft:taiga", "minecraft:forest", "minecraft:plains", "minecraft:meadow"}
                : new String[]{"minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:meadow", "minecraft:forest", "minecraft:plains"};
        for (String option : options) {
            RegistryEntry<Biome> entry = entryById(biomes, option);
            if (entry != null && !isSnowyVariant(entry) && !isGroveBiome(entry)) {
                return entry;
            }
        }
        return base;
    }

    private static RegistryEntry<Biome> pickSnowyTaigaRampFallback(Registry<Biome> biomes, RegistryEntry<Biome> base, int bandIndex) {
        if (base != null
                && !isSnowyVariant(base)
                && !isGroveBiome(base)
                && !isWarmBiome(base)
                && !isBiomeId(base, "minecraft:taiga")) {
            return base;
        }
        String[] options = bandIndex <= BAND_TEMPERATE
                ? new String[]{"minecraft:forest", "minecraft:plains", "minecraft:taiga", "minecraft:meadow"}
                : new String[]{"minecraft:forest", "minecraft:old_growth_pine_taiga", "minecraft:taiga", "minecraft:meadow", "minecraft:plains"};
        for (String option : options) {
            try {
                RegistryEntry<Biome> entry = biome(biomes, option);
                if (!isSnowyVariant(entry) && !isGroveBiome(entry) && !isWarmBiome(entry)) {
                    return entry;
                }
            } catch (Throwable ignored) {
                // try next
            }
        }
        return base;
    }

    private static RegistryEntry<Biome> pickSnowyTaigaRampFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int bandIndex) {
        if (base != null
                && !isSnowyVariant(base)
                && !isGroveBiome(base)
                && !isWarmBiome(base)
                && !isBiomeId(base, "minecraft:taiga")) {
            return base;
        }
        String[] options = bandIndex <= BAND_TEMPERATE
                ? new String[]{"minecraft:forest", "minecraft:plains", "minecraft:taiga", "minecraft:meadow"}
                : new String[]{"minecraft:forest", "minecraft:old_growth_pine_taiga", "minecraft:taiga", "minecraft:meadow", "minecraft:plains"};
        for (String option : options) {
            RegistryEntry<Biome> entry = entryById(biomes, option);
            if (entry != null && !isSnowyVariant(entry) && !isGroveBiome(entry) && !isWarmBiome(entry)) {
                return entry;
            }
        }
        return base;
    }

    private static RegistryEntry<Biome> enforceSnowyLatitudeRamp(Registry<Biome> biomes, RegistryEntry<Biome> pick, RegistryEntry<Biome> base,
                                                                 int blockX, int blockZ, int radius, int bandIndex) {
        double deg = latitudeDegreesFromRadius(blockZ, radius);
        if (isGroveBiome(pick) && deg < GROVE_MIN_DEG) {
            return pickNonSnowyFallback(biomes, base, blockX, blockZ, bandIndex);
        }
        if (!isSnowyVariant(pick)) {
            return pick;
        }
        double alpha = snowyRampAlpha(deg);
        double r;
        if (DISABLE_GRID_DITHER) {
            r = ValueNoise2D.sampleBlocks(WORLD_SEED ^ SNOWY_RAMP_SALT, blockX, blockZ, SNOWY_RAMP_PATCH_BLOCKS);
        } else {
            int cellX = Math.floorDiv(blockX, VARIANT_CELL_SIZE_BLOCKS);
            int cellZ = Math.floorDiv(blockZ, VARIANT_CELL_SIZE_BLOCKS);
            r = cellHash01(WORLD_SEED ^ SNOWY_RAMP_SALT, cellX, cellZ);
        }
        if (r < alpha) {
            return pick;
        }
        if (isBiomeId(pick, "minecraft:snowy_taiga")) {
            return pickSnowyTaigaRampFallback(biomes, base, bandIndex);
        }
        return pickNonSnowyFallback(biomes, base, blockX, blockZ, bandIndex);
    }

    private static RegistryEntry<Biome> enforceSnowyLatitudeRamp(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> pick, RegistryEntry<Biome> base,
                                                                 int blockX, int blockZ, int radius, int bandIndex) {
        double deg = latitudeDegreesFromRadius(blockZ, radius);
        if (isGroveBiome(pick) && deg < GROVE_MIN_DEG) {
            return pickNonSnowyFallback(biomes, base, bandIndex);
        }
        if (!isSnowyVariant(pick)) {
            return pick;
        }
        double alpha = snowyRampAlpha(deg);
        double r;
        if (DISABLE_GRID_DITHER) {
            r = ValueNoise2D.sampleBlocks(WORLD_SEED ^ SNOWY_RAMP_SALT, blockX, blockZ, SNOWY_RAMP_PATCH_BLOCKS);
        } else {
            int cellX = Math.floorDiv(blockX, VARIANT_CELL_SIZE_BLOCKS);
            int cellZ = Math.floorDiv(blockZ, VARIANT_CELL_SIZE_BLOCKS);
            r = cellHash01(WORLD_SEED ^ SNOWY_RAMP_SALT, cellX, cellZ);
        }
        if (r < alpha) {
            return pick;
        }
        if (isBiomeId(pick, "minecraft:snowy_taiga")) {
            return pickSnowyTaigaRampFallback(biomes, base, bandIndex);
        }
        return pickNonSnowyFallback(biomes, base, bandIndex);
    }

    private static RegistryEntry<Biome> clampWarmInColdZone(Registry<Biome> biomes, RegistryEntry<Biome> base,
                                                            RegistryEntry<Biome> pick, LatitudeBands.Band band,
                                                            int blockX, int blockZ) {
        if (pick == null) {
            return base;
        }
        if (band != LatitudeBands.Band.SUBPOLAR && band != LatitudeBands.Band.POLAR) {
            return pick;
        }
        if (!isWarmBiome(pick)) {
            return pick;
        }
        return pickSnowyFallback(biomes, base);
    }

    private static RegistryEntry<Biome> clampWarmInColdZone(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base,
                                                            RegistryEntry<Biome> pick, LatitudeBands.Band band,
                                                            int blockX, int blockZ) {
        if (pick == null) {
            return base;
        }
        if (band != LatitudeBands.Band.SUBPOLAR && band != LatitudeBands.Band.POLAR) {
            return pick;
        }
        if (!isWarmBiome(pick)) {
            return pick;
        }
        return pickSnowyFallback(biomes, base);
    }

    private static RegistryEntry<Biome> applySubpolarSwampGuard(Registry<Biome> biomes, RegistryEntry<Biome> base,
                                                                 RegistryEntry<Biome> pick, LatitudeBands.Band band) {
        if (band != LatitudeBands.Band.SUBPOLAR || !isSubpolarDisallowedWetBiome(pick)) {
            return pick;
        }
        return pickSubpolarSwampFallback(biomes, base);
    }

    private static RegistryEntry<Biome> applySubpolarSwampGuard(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base,
                                                                 RegistryEntry<Biome> pick, LatitudeBands.Band band) {
        if (band != LatitudeBands.Band.SUBPOLAR || !isSubpolarDisallowedWetBiome(pick)) {
            return pick;
        }
        return pickSubpolarSwampFallback(biomes, base);
    }

    private static boolean isSubpolarDisallowedWetBiome(RegistryEntry<Biome> biome) {
        return isBiomeId(biome, SWAMP_ID);
    }

    private static RegistryEntry<Biome> pickSubpolarSwampFallback(Registry<Biome> biomes, RegistryEntry<Biome> base) {
        if (!isSubpolarDisallowedWetBiome(base)) {
            return base;
        }
        try {
            return biome(biomes, "minecraft:snowy_plains");
        } catch (Throwable ignored) {
            return base;
        }
    }

    private static RegistryEntry<Biome> pickSubpolarSwampFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base) {
        if (!isSubpolarDisallowedWetBiome(base)) {
            return base;
        }
        RegistryEntry<Biome> snowyPlains = entryById(biomes, "minecraft:snowy_plains");
        return snowyPlains != null ? snowyPlains : base;
    }

    private static boolean isWarmBiome(RegistryEntry<Biome> entry) {
        if (entry == null) {
            return false;
        }
        return entry.getKey()
                .map(key -> WARM_BIOME_BLOCKLIST.contains(key.getValue().toString()))
                .orElse(false);
    }

    private static RegistryEntry<Biome> pickSnowyFallback(Registry<Biome> biomes, RegistryEntry<Biome> base) {
        String[] options = new String[]{"minecraft:snowy_taiga", "minecraft:snowy_plains"};
        for (String option : options) {
            try {
                return biome(biomes, option);
            } catch (Throwable ignored) {
                // try next
            }
        }
        return base;
    }

    private static RegistryEntry<Biome> pickSnowyFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base) {
        String[] options = new String[]{"minecraft:snowy_taiga", "minecraft:snowy_plains"};
        for (String option : options) {
            RegistryEntry<Biome> entry = entryById(biomes, option);
            if (entry != null) {
                return entry;
            }
        }
        return base;
    }

    private static RegistryEntry<Biome> pickSubpolarForestSanitizeFallback(Registry<Biome> biomes, RegistryEntry<Biome> pick) {
        String[] options = new String[]{
                "minecraft:snowy_taiga",
                "minecraft:taiga",
                "minecraft:old_growth_spruce_taiga",
                "minecraft:snowy_plains"
        };
        for (String option : options) {
            try {
                return biome(biomes, option);
            } catch (Throwable ignored) {
                // try next
            }
        }
        return pick;
    }

    private static RegistryEntry<Biome> pickSubpolarForestSanitizeFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> pick) {
        String[] options = new String[]{
                "minecraft:snowy_taiga",
                "minecraft:taiga",
                "minecraft:old_growth_spruce_taiga",
                "minecraft:snowy_plains"
        };
        for (String option : options) {
            RegistryEntry<Biome> entry = entryById(biomes, option);
            if (entry != null) {
                return entry;
            }
        }
        return pick;
    }

    private static RegistryEntry<Biome> pickColdFallback(Registry<Biome> biomes, RegistryEntry<Biome> base,
                                                         int blockX, int blockZ, int bandIndex) {
        if (bandIndex >= BAND_POLAR && rollChance(blockX, blockZ, 0x5EEDC0DE, 40L)) {
            try {
                return biome(biomes, "minecraft:ice_spikes");
            } catch (Throwable ignored) {
                // fall through
            }
        }
        String[] options = bandIndex >= BAND_POLAR
                ? new String[]{"minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:taiga"}
                : new String[]{"minecraft:snowy_taiga", "minecraft:snowy_plains", "minecraft:taiga"};
        for (String option : options) {
            try {
                return biome(biomes, option);
            } catch (Throwable ignored) {
                // try next
            }
        }
        return base;
    }

    private static RegistryEntry<Biome> pickColdFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base,
                                                         int blockX, int blockZ, int bandIndex) {
        if (bandIndex >= BAND_POLAR && rollChance(blockX, blockZ, 0x5EEDC0DE, 40L)) {
            RegistryEntry<Biome> spikes = entryById(biomes, "minecraft:ice_spikes");
            if (spikes != null) {
                return spikes;
            }
        }
        String[] options = bandIndex >= BAND_POLAR
                ? new String[]{"minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:taiga"}
                : new String[]{"minecraft:snowy_taiga", "minecraft:snowy_plains", "minecraft:taiga"};
        for (String option : options) {
            RegistryEntry<Biome> entry = entryById(biomes, option);
            if (entry != null) {
                return entry;
            }
        }
        return base;
    }

    private static RegistryEntry<Biome> pickWarmFallback(Registry<Biome> biomes, int bandIndex) {
        String primary = bandIndex == BAND_TROPICAL ? "minecraft:sparse_jungle" : "minecraft:savanna";
        String secondary = bandIndex == BAND_TROPICAL ? "minecraft:jungle" : "minecraft:plains";
        RegistryEntry<Biome> out = null;
        try {
            out = biome(biomes, primary);
        } catch (Throwable ignored) {
            try {
                out = biome(biomes, secondary);
            } catch (Throwable ignoredAgain) {
                try {
                    out = biome(biomes, "minecraft:forest");
                } catch (Throwable ignoredLast) {
                    out = null;
                }
            }
        }
        recordWarmDryPath("PICK_WARM_FALLBACK", null, out, 0, 0, bandIndex, null);
        return out;
    }

    private static RegistryEntry<Biome> pickWarmFallback(Collection<RegistryEntry<Biome>> biomes, int bandIndex) {
        String primary = bandIndex == BAND_TROPICAL ? "minecraft:sparse_jungle" : "minecraft:savanna";
        String secondary = bandIndex == BAND_TROPICAL ? "minecraft:jungle" : "minecraft:plains";
        RegistryEntry<Biome> out = entryById(biomes, primary);
        if (out == null) {
            out = entryById(biomes, secondary);
        }
        if (out == null) {
            out = entryById(biomes, "minecraft:forest");
        }
        if (out == null) {
            out = biomes.stream().findFirst().orElse(null);
        }
        recordWarmDryPath("PICK_WARM_FALLBACK", null, out, 0, 0, bandIndex, null);
        return out;
    }

    private static boolean allowWetTropicalCanopy(int blockX, int blockZ, double t, RegistryEntry<Biome> candidate) {
        double tropicalEnd = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
        double u = tropicalEnd > 0.0 ? clamp(t / tropicalEnd, 0.0, 1.0) : 1.0;
        double latitudePenalty = 0.28 * u;
        double compositionBias = tropicalCompositionBias(WORLD_SEED, blockX, blockZ);
        double opennessNoise = tropicalOpennessNoise(blockX, blockZ);
        double opennessPenalty = Math.max(0.0, opennessNoise - 0.48) * 0.30;
        double baseChance;
        if (isBiomeId(candidate, "minecraft:jungle")) {
            baseChance = 0.68;
        } else if (isBiomeId(candidate, "minecraft:bamboo_jungle")) {
            baseChance = 0.52;
        } else if (isBiomeId(candidate, "minecraft:sparse_jungle")) {
            baseChance = 0.48;
        } else {
            baseChance = 0.26;
        }
        double threshold = clamp(baseChance - latitudePenalty - opennessPenalty + compositionBias, 0.08, 0.88);
        double canopyNoise = ValueNoise2D.sampleBlocks(WORLD_SEED ^ TROPICAL_CANOPY_SALT, blockX, blockZ, 1536);
        return canopyNoise < threshold;
    }

    private static RegistryEntry<Biome> pickOpenTropicalFallback(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        if (!DEBUG_WARM_POOL_AUDIT) {
            double tropicalEnd = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
            double u = tropicalEnd > 0.0 ? clamp(t / tropicalEnd, 0.0, 1.0) : 1.0;
            double opennessNoise = tropicalOpennessNoise(blockX, blockZ);
            double compositionBias = tropicalCompositionBias(WORLD_SEED, blockX, blockZ);
            ProvinceAuthority.Province province = classifyProvince(blockX, blockZ);
            boolean strongOpen = opennessNoise >= 0.76 || (u > 0.82 && opennessNoise >= 0.68);
            if (province == ProvinceAuthority.Province.WARM_DRY) {
                if ((u > 0.86 || opennessNoise > 0.82) && compositionBias < -0.06) {
                    try {
                        return biome(biomes, "minecraft:desert");
                    } catch (Throwable ignored) {
                        // fall through
                    }
                }
                if (isJungleFamily(base)) {
                    return pickDryWarmFallback(biomes, base);
                }
                if (u > 0.72 || strongOpen || compositionBias < 0.10) {
                    try {
                        return biome(biomes, "minecraft:desert");
                    } catch (Throwable ignored) {
                        // fall through
                    }
                }
                return pickDryWarmFallback(biomes, base);
            }
            if ((u > 0.86 || opennessNoise > 0.82) && compositionBias < -0.06) {
                try {
                    return biome(biomes, "minecraft:desert");
                } catch (Throwable ignored) {
                    // fall through
                }
            }
            if (isBiomeId(base, "minecraft:bamboo_jungle") && compositionBias > 0.32 && opennessNoise < 0.18) {
                try {
                    return biome(biomes, "minecraft:sparse_jungle");
                } catch (Throwable ignored) {
                    // fall through
                }
            }
            if (province == ProvinceAuthority.Province.WARM_WET) {
                if (compositionBias > 0.42 && opennessNoise < 0.18) {
                    try {
                        return biome(biomes, "minecraft:sparse_jungle");
                    } catch (Throwable ignoredSparse) {
                        try {
                            return biome(biomes, "minecraft:jungle");
                        } catch (Throwable ignoredJungle) {
                            return base;
                        }
                    }
                }
                if (!strongOpen) {
                    if (isJungleFamily(base)) {
                        return base;
                    }
                    if (compositionBias > 0.16 && opennessNoise < 0.48) {
                        RegistryEntry<Biome> wetWarm = pickWarmFallback(biomes, BAND_TROPICAL);
                        return wetWarm != null ? wetWarm : base;
                    }
                    return base;
                }
                try {
                    return biome(biomes, "minecraft:sparse_jungle");
                } catch (Throwable ignoredSparse) {
                    RegistryEntry<Biome> wetWarm = pickWarmFallback(biomes, BAND_TROPICAL);
                    return wetWarm != null ? wetWarm : base;
                }
            }
            if (compositionBias > 0.42 && opennessNoise < 0.18) {
                try {
                    RegistryEntry<Biome> pick = biome(biomes, "minecraft:savanna");
                    recordWarmDryPath("OPEN_TROPICAL_FALLBACK", base, pick, blockX, blockZ, BAND_TROPICAL, province);
                    return pick;
                } catch (Throwable ignoredSavanna) {
                    // fall through
                }
            }
            if (u < 0.42 && opennessNoise >= 0.54 && opennessNoise < 0.74 && compositionBias > 0.08) {
                try {
                    return biome(biomes, "minecraft:plains");
                } catch (Throwable ignored) {
                    // fall through
                }
            }
            if (!strongOpen) {
                if (isJungleFamily(base)) {
                    RegistryEntry<Biome> wetWarm = pickWarmFallback(biomes, BAND_TROPICAL);
                    if (wetWarm != null && !isSavannaFamily(wetWarm)) {
                        return wetWarm;
                    }
                    try {
                        return biome(biomes, "minecraft:savanna");
                    } catch (Throwable ignoredSavanna) {
                        try {
                            return biome(biomes, "minecraft:desert");
                        } catch (Throwable ignoredDesert) {
                            return base;
                        }
                    }
                }
                return base;
            }
            try {
                return biome(biomes, "minecraft:savanna");
            } catch (Throwable ignored) {
                try {
                    return biome(biomes, "minecraft:desert");
                } catch (Throwable ignoredAgain) {
                    return base;
                }
            }
        }

        double tropicalEnd = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
        double u = tropicalEnd > 0.0 ? clamp(t / tropicalEnd, 0.0, 1.0) : 1.0;
        double opennessNoise = tropicalOpennessNoise(blockX, blockZ);
        double compositionBias = tropicalCompositionBias(WORLD_SEED, blockX, blockZ);
        ProvinceAuthority.Province province = classifyProvince(blockX, blockZ);
        boolean strongOpen = opennessNoise >= 0.76 || (u > 0.82 && opennessNoise >= 0.68);
        if (province == ProvinceAuthority.Province.WARM_DRY) {
            if ((u > 0.86 || opennessNoise > 0.82) && compositionBias < -0.06) {
                try {
                    RegistryEntry<Biome> pick = biome(biomes, "minecraft:desert");
                    return warmOpenAuditReturn("open_province_dry_desert_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
                } catch (Throwable ignored) {
                    // fall through
                }
            }
            if (isJungleFamily(base)) {
                RegistryEntry<Biome> pick = pickDryWarmFallback(biomes, base);
                return warmOpenAuditReturn("open_province_dry_fallback_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
            }
            if (u > 0.72 || strongOpen || compositionBias < 0.10) {
                try {
                    RegistryEntry<Biome> pick = biome(biomes, "minecraft:desert");
                    return warmOpenAuditReturn("open_province_dry_desert_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
                } catch (Throwable ignored) {
                    // fall through
                }
            }
            RegistryEntry<Biome> pick = pickDryWarmFallback(biomes, base);
            return warmOpenAuditReturn("open_province_dry_fallback_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
        }
        if ((u > 0.86 || opennessNoise > 0.82) && compositionBias < -0.06) {
            try {
                RegistryEntry<Biome> pick = biome(biomes, "minecraft:desert");
                return warmOpenAuditReturn("open_desert_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
            } catch (Throwable ignored) {
                // fall through
            }
        }
        if (isBiomeId(base, "minecraft:bamboo_jungle") && compositionBias > 0.32 && opennessNoise < 0.18) {
            try {
                RegistryEntry<Biome> pick = biome(biomes, "minecraft:sparse_jungle");
                return warmOpenAuditReturn("open_sparse_jungle_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
            } catch (Throwable ignored) {
                // fall through
            }
        }
        if (province == ProvinceAuthority.Province.WARM_WET) {
            if (compositionBias > 0.42 && opennessNoise < 0.18) {
                try {
                    RegistryEntry<Biome> pick = biome(biomes, "minecraft:sparse_jungle");
                    return warmOpenAuditReturn("open_province_wet_sparse_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
                } catch (Throwable ignoredSparse) {
                    try {
                        RegistryEntry<Biome> pick = biome(biomes, "minecraft:jungle");
                        return warmOpenAuditReturn("open_province_wet_jungle_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
                    } catch (Throwable ignoredJungle) {
                        return warmOpenAuditReturn("open_jungle_return", base, false, compositionBias, opennessNoise, strongOpen);
                    }
                }
            }
            if (!strongOpen) {
                if (isJungleFamily(base)) {
                    return warmOpenAuditReturn("open_jungle_return", base, false, compositionBias, opennessNoise, strongOpen);
                }
                if (compositionBias > 0.16 && opennessNoise < 0.48) {
                    RegistryEntry<Biome> pick = pickWarmFallback(biomes, BAND_TROPICAL);
                    RegistryEntry<Biome> out = pick != null ? pick : base;
                    return warmOpenAuditReturn("open_province_wet_warm_return", out, out != base, compositionBias, opennessNoise, strongOpen);
                }
                return warmOpenAuditReturn("open_other_return", base, false, compositionBias, opennessNoise, strongOpen);
            }
            try {
                RegistryEntry<Biome> pick = biome(biomes, "minecraft:sparse_jungle");
                return warmOpenAuditReturn("open_province_wet_sparse_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
            } catch (Throwable ignoredSparse) {
                RegistryEntry<Biome> pick = pickWarmFallback(biomes, BAND_TROPICAL);
                RegistryEntry<Biome> out = pick != null ? pick : base;
                return warmOpenAuditReturn("open_province_wet_warm_return", out, out != base, compositionBias, opennessNoise, strongOpen);
            }
        }
        if (compositionBias > 0.42 && opennessNoise < 0.18) {
            try {
                RegistryEntry<Biome> pick = biome(biomes, "minecraft:savanna");
                return warmOpenAuditReturn("open_savanna_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
            } catch (Throwable ignoredSavanna) {
                // fall through
            }
        }
        if (u < 0.42 && opennessNoise >= 0.54 && opennessNoise < 0.74 && compositionBias > 0.08) {
            try {
                RegistryEntry<Biome> pick = biome(biomes, "minecraft:plains");
                return warmOpenAuditReturn("open_other_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
            } catch (Throwable ignored) {
                // fall through
            }
        }
        if (!strongOpen) {
            if (isJungleFamily(base)) {
                warmOpenBranchEnter("open_jungle_family_branch_enter");
                WARM_POOL_AUDIT_NS_ENTER.incrementAndGet();
                int roll = (int) Long.remainderUnsigned(hash64(blockX, blockZ, 0x5A1E5A1E), 100);
                if (roll < 20) {
                    try {
                        RegistryEntry<Biome> plains = biome(biomes, "minecraft:plains");
                        WARM_POOL_AUDIT_NS_RETURN_PLAINS_ATTEMPT.incrementAndGet();
                        recordWarmOpenNsBuckets("plains_attempt", compositionBias, opennessNoise, strongOpen);
                        return warmOpenAuditReturn("open_plains_return", plains, plains != base, compositionBias, opennessNoise, strongOpen);
                    } catch (Throwable ignoredPlains) {
                        // fall through
                    }
                }
                RegistryEntry<Biome> wetWarm = pickWarmFallback(biomes, BAND_TROPICAL);
                if (wetWarm != null && !isSavannaFamily(wetWarm)) {
                    WARM_POOL_AUDIT_NS_RETURN_OTHER.incrementAndGet();
                    recordWarmOpenNsBuckets("other", compositionBias, opennessNoise, strongOpen);
                    return warmOpenAuditReturn("open_province_wet_warm_return", wetWarm, wetWarm != base, compositionBias, opennessNoise, strongOpen);
                }
                try {
                    RegistryEntry<Biome> pick = biome(biomes, "minecraft:savanna");
                    WARM_POOL_AUDIT_NS_RETURN_SAVANNA.incrementAndGet();
                    recordWarmOpenNsBuckets("savanna", compositionBias, opennessNoise, strongOpen);
                    return warmOpenAuditReturn("open_savanna_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
                } catch (Throwable ignoredSavanna) {
                    try {
                        RegistryEntry<Biome> desert = biome(biomes, "minecraft:desert");
                        WARM_POOL_AUDIT_NS_RETURN_DESERT.incrementAndGet();
                        recordWarmOpenNsBuckets("desert", compositionBias, opennessNoise, strongOpen);
                        return warmOpenAuditReturn("open_desert_return", desert, desert != base, compositionBias, opennessNoise, strongOpen);
                    } catch (Throwable ignoredDesert) {
                        WARM_POOL_AUDIT_NS_RETURN_BASE.incrementAndGet();
                        recordWarmOpenNsBuckets("base", compositionBias, opennessNoise, strongOpen);
                        return warmOpenAuditReturn("open_jungle_return", base, false, compositionBias, opennessNoise, strongOpen);
                    }
                }
            }
            WARM_POOL_AUDIT_NS_RETURN_OTHER.incrementAndGet();
            recordWarmOpenNsBuckets("other", compositionBias, opennessNoise, strongOpen);
            return warmOpenAuditReturn("open_other_return", base, false, compositionBias, opennessNoise, strongOpen);
        }
        warmOpenBranchEnter("open_strong_open_branch_enter");
        try {
            RegistryEntry<Biome> pick = biome(biomes, "minecraft:savanna");
            return warmOpenAuditReturn("open_savanna_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
        } catch (Throwable ignored) {
            try {
                RegistryEntry<Biome> pick = biome(biomes, "minecraft:desert");
                return warmOpenAuditReturn("open_desert_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
            } catch (Throwable ignoredAgain) {
                return warmOpenAuditReturn("open_other_return", base, false, compositionBias, opennessNoise, strongOpen);
            }
        }
    }

    private static RegistryEntry<Biome> pickOpenTropicalFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        if (!DEBUG_WARM_POOL_AUDIT) {
            double tropicalEnd = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
            double u = tropicalEnd > 0.0 ? clamp(t / tropicalEnd, 0.0, 1.0) : 1.0;
            double opennessNoise = tropicalOpennessNoise(blockX, blockZ);
            double compositionBias = tropicalCompositionBias(WORLD_SEED, blockX, blockZ);
            ProvinceAuthority.Province province = classifyProvince(blockX, blockZ);
            boolean strongOpen = opennessNoise >= 0.76 || (u > 0.82 && opennessNoise >= 0.68);
            if (province == ProvinceAuthority.Province.WARM_DRY) {
                if ((u > 0.86 || opennessNoise > 0.82) && compositionBias < -0.06) {
                    RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
                    if (desert != null) {
                        return desert;
                    }
                }
                if (isJungleFamily(base)) {
                    return pickDryWarmFallback(biomes, base);
                }
                if (u > 0.72 || strongOpen || compositionBias < 0.10) {
                    RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
                    if (desert != null) {
                        return desert;
                    }
                }
                RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
                if (desert != null) {
                    return desert;
                }
                return pickDryWarmFallback(biomes, base);
            }
            if ((u > 0.86 || opennessNoise > 0.82) && compositionBias < -0.06) {
                RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
                if (desert != null) {
                    return desert;
                }
            }
            if (isBiomeId(base, "minecraft:bamboo_jungle") && compositionBias > 0.32 && opennessNoise < 0.18) {
                RegistryEntry<Biome> sparseJungle = entryById(biomes, "minecraft:sparse_jungle");
                if (sparseJungle != null) {
                    return sparseJungle;
                }
            }
            if (province == ProvinceAuthority.Province.WARM_WET) {
                if (compositionBias > 0.42 && opennessNoise < 0.18) {
                    RegistryEntry<Biome> sparseJungle = entryById(biomes, "minecraft:sparse_jungle");
                    if (sparseJungle != null) {
                        return sparseJungle;
                    }
                    RegistryEntry<Biome> jungle = entryById(biomes, "minecraft:jungle");
                    if (jungle != null) {
                        return jungle;
                    }
                    return base;
                }
                if (!strongOpen) {
                    if (isJungleFamily(base)) {
                        return base;
                    }
                    if (compositionBias > 0.16 && opennessNoise < 0.48) {
                        RegistryEntry<Biome> wetWarm = pickWarmFallback(biomes, BAND_TROPICAL);
                        return wetWarm != null ? wetWarm : base;
                    }
                    return base;
                }
                RegistryEntry<Biome> sparseJungle = entryById(biomes, "minecraft:sparse_jungle");
                if (sparseJungle != null) {
                    return sparseJungle;
                }
                RegistryEntry<Biome> wetWarm = pickWarmFallback(biomes, BAND_TROPICAL);
                return wetWarm != null ? wetWarm : base;
            }
            if (compositionBias > 0.42 && opennessNoise < 0.18) {
                RegistryEntry<Biome> savanna = entryById(biomes, "minecraft:savanna");
                if (savanna != null) {
                    return savanna;
                }
            }
            if (u < 0.42 && opennessNoise >= 0.54 && opennessNoise < 0.74 && compositionBias > 0.08) {
                RegistryEntry<Biome> plains = entryById(biomes, "minecraft:plains");
                if (plains != null) {
                    return plains;
                }
            }
            if (!strongOpen) {
                if (isJungleFamily(base)) {
                    RegistryEntry<Biome> savanna = entryById(biomes, "minecraft:savanna");
                    if (savanna != null) {
                        return savanna;
                    }
                    RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
                    if (desert != null) {
                        return desert;
                    }
                }
                return base;
            }
            RegistryEntry<Biome> savanna = entryById(biomes, "minecraft:savanna");
            if (savanna != null) {
                return savanna;
            }
            RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
            return desert != null ? desert : base;
        }

        double tropicalEnd = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
        double u = tropicalEnd > 0.0 ? clamp(t / tropicalEnd, 0.0, 1.0) : 1.0;
        double opennessNoise = tropicalOpennessNoise(blockX, blockZ);
        double compositionBias = tropicalCompositionBias(WORLD_SEED, blockX, blockZ);
        ProvinceAuthority.Province province = classifyProvince(blockX, blockZ);
        boolean strongOpen = opennessNoise >= 0.76 || (u > 0.82 && opennessNoise >= 0.68);
        if (province == ProvinceAuthority.Province.WARM_DRY) {
            if ((u > 0.86 || opennessNoise > 0.82) && compositionBias < -0.06) {
                RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
                if (desert != null) {
                    return warmOpenAuditReturn("open_province_dry_desert_return", desert, desert != base, compositionBias, opennessNoise, strongOpen);
                }
            }
            if (isJungleFamily(base)) {
                RegistryEntry<Biome> pick = pickDryWarmFallback(biomes, base);
                return warmOpenAuditReturn("open_province_dry_fallback_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
            }
            if (u > 0.72 || strongOpen || compositionBias < 0.10) {
                RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
                if (desert != null) {
                    return warmOpenAuditReturn("open_province_dry_desert_return", desert, desert != base, compositionBias, opennessNoise, strongOpen);
                }
            }
            RegistryEntry<Biome> pick = entryById(biomes, "minecraft:desert");
            if (pick == null) {
                pick = pickDryWarmFallback(biomes, base);
            }
            return warmOpenAuditReturn("open_province_dry_fallback_return", pick, pick != base, compositionBias, opennessNoise, strongOpen);
        }
        if ((u > 0.86 || opennessNoise > 0.82) && compositionBias < -0.06) {
            RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
            if (desert != null) {
                return warmOpenAuditReturn("open_desert_return", desert, desert != base, compositionBias, opennessNoise, strongOpen);
            }
        }
        if (isBiomeId(base, "minecraft:bamboo_jungle") && compositionBias > 0.32 && opennessNoise < 0.18) {
            RegistryEntry<Biome> sparseJungle = entryById(biomes, "minecraft:sparse_jungle");
            if (sparseJungle != null) {
                return warmOpenAuditReturn("open_sparse_jungle_return", sparseJungle, sparseJungle != base, compositionBias, opennessNoise, strongOpen);
            }
        }
        if (province == ProvinceAuthority.Province.WARM_WET) {
            if (compositionBias > 0.42 && opennessNoise < 0.18) {
                RegistryEntry<Biome> sparseJungle = entryById(biomes, "minecraft:sparse_jungle");
                if (sparseJungle != null) {
                    return warmOpenAuditReturn("open_province_wet_sparse_return", sparseJungle, sparseJungle != base, compositionBias, opennessNoise, strongOpen);
                }
                RegistryEntry<Biome> jungle = entryById(biomes, "minecraft:jungle");
                if (jungle != null) {
                    return warmOpenAuditReturn("open_province_wet_jungle_return", jungle, jungle != base, compositionBias, opennessNoise, strongOpen);
                }
                return warmOpenAuditReturn("open_jungle_return", base, false, compositionBias, opennessNoise, strongOpen);
            }
            if (!strongOpen) {
                if (isJungleFamily(base)) {
                    return warmOpenAuditReturn("open_jungle_return", base, false, compositionBias, opennessNoise, strongOpen);
                }
                if (compositionBias > 0.16 && opennessNoise < 0.48) {
                    RegistryEntry<Biome> pick = pickWarmFallback(biomes, BAND_TROPICAL);
                    RegistryEntry<Biome> out = pick != null ? pick : base;
                    return warmOpenAuditReturn("open_province_wet_warm_return", out, out != base, compositionBias, opennessNoise, strongOpen);
                }
                return warmOpenAuditReturn("open_other_return", base, false, compositionBias, opennessNoise, strongOpen);
            }
            RegistryEntry<Biome> sparseJungle = entryById(biomes, "minecraft:sparse_jungle");
            if (sparseJungle != null) {
                return warmOpenAuditReturn("open_province_wet_sparse_return", sparseJungle, sparseJungle != base, compositionBias, opennessNoise, strongOpen);
            }
            RegistryEntry<Biome> pick = pickWarmFallback(biomes, BAND_TROPICAL);
            RegistryEntry<Biome> out = pick != null ? pick : base;
            return warmOpenAuditReturn("open_province_wet_warm_return", out, out != base, compositionBias, opennessNoise, strongOpen);
        }
        if (compositionBias > 0.42 && opennessNoise < 0.18) {
            RegistryEntry<Biome> savanna = entryById(biomes, "minecraft:savanna");
            if (savanna != null) {
                return warmOpenAuditReturn("open_savanna_return", savanna, savanna != base, compositionBias, opennessNoise, strongOpen);
            }
        }
        if (u < 0.42 && opennessNoise >= 0.54 && opennessNoise < 0.74 && compositionBias > 0.08) {
            RegistryEntry<Biome> plains = entryById(biomes, "minecraft:plains");
            if (plains != null) {
                return warmOpenAuditReturn("open_other_return", plains, plains != base, compositionBias, opennessNoise, strongOpen);
            }
        }
        if (!strongOpen) {
            if (isJungleFamily(base)) {
                warmOpenBranchEnter("open_jungle_family_branch_enter");
                WARM_POOL_AUDIT_NS_ENTER.incrementAndGet();
                int roll = (int) Long.remainderUnsigned(hash64(blockX, blockZ, 0x5A1E5A1E), 100);
                if (roll < 20) {
                    RegistryEntry<Biome> plains = entryById(biomes, "minecraft:plains");
                    if (plains != null) {
                        WARM_POOL_AUDIT_NS_RETURN_PLAINS_ATTEMPT.incrementAndGet();
                        recordWarmOpenNsBuckets("plains_attempt", compositionBias, opennessNoise, strongOpen);
                        return warmOpenAuditReturn("open_plains_return", plains, plains != base, compositionBias, opennessNoise, strongOpen);
                    }
                }
                RegistryEntry<Biome> savanna = entryById(biomes, "minecraft:savanna");
                if (savanna != null) {
                    WARM_POOL_AUDIT_NS_RETURN_SAVANNA.incrementAndGet();
                    recordWarmOpenNsBuckets("savanna", compositionBias, opennessNoise, strongOpen);
                    recordWarmDryPath("OPEN_TROPICAL_FALLBACK", base, savanna, blockX, blockZ, BAND_TROPICAL, province);
                    return warmOpenAuditReturn("open_savanna_return", savanna, savanna != base, compositionBias, opennessNoise, strongOpen);
                }
                RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
                if (desert != null) {
                    WARM_POOL_AUDIT_NS_RETURN_DESERT.incrementAndGet();
                    recordWarmOpenNsBuckets("desert", compositionBias, opennessNoise, strongOpen);
                    return warmOpenAuditReturn("open_desert_return", desert, desert != base, compositionBias, opennessNoise, strongOpen);
                }
                WARM_POOL_AUDIT_NS_RETURN_BASE.incrementAndGet();
                recordWarmOpenNsBuckets("base", compositionBias, opennessNoise, strongOpen);
            }
            WARM_POOL_AUDIT_NS_RETURN_OTHER.incrementAndGet();
            recordWarmOpenNsBuckets("other", compositionBias, opennessNoise, strongOpen);
            return warmOpenAuditReturn("open_jungle_return", base, false, compositionBias, opennessNoise, strongOpen);
        }
        warmOpenBranchEnter("open_strong_open_branch_enter");
        RegistryEntry<Biome> savanna = entryById(biomes, "minecraft:savanna");
        if (savanna != null) {
            recordWarmDryPath("OPEN_TROPICAL_FALLBACK", base, savanna, blockX, blockZ, BAND_TROPICAL, province);
            return warmOpenAuditReturn("open_savanna_return", savanna, savanna != base, compositionBias, opennessNoise, strongOpen);
        }
        RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
        RegistryEntry<Biome> pick = desert != null ? desert : base;
        String branch = desert != null ? "open_desert_return" : "open_other_return";
        return warmOpenAuditReturn(branch, pick, pick != base, compositionBias, opennessNoise, strongOpen);
    }

    private static RegistryEntry<Biome> pickDryWarmFallback(Registry<Biome> biomes, RegistryEntry<Biome> base) {
        RegistryEntry<Biome> out;
        try {
            out = biome(biomes, "minecraft:desert");
        } catch (Throwable ignored) {
            try {
                out = biome(biomes, "minecraft:savanna");
            } catch (Throwable ignoredAgain) {
                out = base;
            }
        }
        recordWarmDryPath("WARM_DRY_SELECTOR", base, out, 0, 0, BAND_SUBTROPICAL, warmProvinceClass(0, 0, BAND_SUBTROPICAL));
        return out;
    }

    private static RegistryEntry<Biome> pickDryWarmFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base) {
        RegistryEntry<Biome> out = entryById(biomes, "minecraft:desert");
        if (out == null) {
            out = entryById(biomes, "minecraft:savanna");
        }
        if (out == null) {
            out = base;
        }
        recordWarmDryPath("WARM_DRY_SELECTOR", base, out, 0, 0, BAND_SUBTROPICAL, warmProvinceClass(0, 0, BAND_SUBTROPICAL));
        return out;
    }

    private static RegistryEntry<Biome> chooseBadlandsVariant(Registry<Biome> biomes, int blockX, int blockZ) {
        RegistryEntry<Biome> badlands = null;
        RegistryEntry<Biome> wooded = null;
        RegistryEntry<Biome> eroded = null;
        try {
            badlands = biome(biomes, "minecraft:badlands");
        } catch (Throwable ignored) {
        }
        try {
            wooded = biome(biomes, "minecraft:wooded_badlands");
        } catch (Throwable ignored) {
        }
        try {
            eroded = biome(biomes, "minecraft:eroded_badlands");
        } catch (Throwable ignored) {
        }
        // Deterministic uniform split so rare variants can actually win.
        int salt = (int) (BADLANDS_PATCH_SALT ^ 0xB11AD5L);
        long h = hash64(blockX, blockZ, salt);
        double roll = (double) Long.remainderUnsigned(h, 10_000_000L) / 10_000_000.0; // [0,1)
        if (roll < 0.90 && badlands != null) {
            return badlands;                 // dominant default
        }
        if (roll < 0.99 && wooded != null) {
            return wooded;                   // uncommon
        }
        if (eroded != null) {
            return eroded;                   // very rare
        }
        if (wooded != null) {
            return wooded;
        }
        return badlands != null ? badlands : eroded;
    }

    private static RegistryEntry<Biome> chooseBadlandsVariant(Collection<RegistryEntry<Biome>> biomes, int blockX, int blockZ) {
        RegistryEntry<Biome> badlands = entryById(biomes, "minecraft:badlands");
        RegistryEntry<Biome> wooded = entryById(biomes, "minecraft:wooded_badlands");
        RegistryEntry<Biome> eroded = entryById(biomes, "minecraft:eroded_badlands");
        int radiusHint = ACTIVE_RADIUS_BLOCKS > 0 ? ACTIVE_RADIUS_BLOCKS : (REFERENCE_DIAMETER_BLOCKS / 2);
        if (!badlandsProvinceAuthorityHit(WORLD_SEED, blockX, blockZ, radiusHint)) {
            return badlands != null ? badlands : (wooded != null ? wooded : eroded);
        }
        if (badlandsProvinceCoreHit(WORLD_SEED, blockX, blockZ, radiusHint) && eroded != null) {
            return eroded;
        }
        if (wooded != null && badlandsProvinceWobbleHit(WORLD_SEED, blockX, blockZ, radiusHint)) {
            return wooded;
        }
        return badlands != null ? badlands : (wooded != null ? wooded : eroded);
    }

    private static RegistryEntry<Biome> pickAridRegionFallback(Registry<Biome> biomes,
                                                                RegistryEntry<Biome> base,
                                                                int blockX,
                                                                int blockZ) {
        int radiusHint = ACTIVE_RADIUS_BLOCKS > 0 ? ACTIVE_RADIUS_BLOCKS : (REFERENCE_DIAMETER_BLOCKS / 2);
        ProvinceAuthority.Province warmProvince = warmProvinceClass(
                blockX,
                blockZ,
                authoritativeLandBandIndex(blockX, blockZ, radiusHint));
        if (warmProvince != ProvinceAuthority.Province.WARM_DRY) {
            return base;
        }
        if (!badlandsProvinceAuthorityHit(WORLD_SEED, blockX, blockZ, radiusHint)) {
            int outsideScale = Math.max(ARID_REGION_MIN_SCALE_BLOCKS, (int) Math.round(radiusHint * 0.45));
            double outsideNoise = ValueNoise2D.sampleBlocks(
                    WORLD_SEED ^ BADLANDS_OUTSIDE_PROVINCE_SALT, blockX, blockZ, outsideScale);
            if (outsideNoise < BADLANDS_OUTSIDE_PROVINCE_THRESHOLD) {
                RegistryEntry<Biome> outsideVariant = chooseBadlandsVariant(biomes, blockX, blockZ);
                if (outsideVariant != null) {
                    return outsideVariant;
                }
            }
            return enforceWarmProvinceFamily(biomes, base, warmProvince);
        }
        RegistryEntry<Biome> variant = chooseBadlandsVariant(biomes, blockX, blockZ);
        if (variant != null) {
            return variant;
        }
        if (!aridHotspotHere(WORLD_SEED, blockX, blockZ)) {
            return enforceWarmProvinceFamily(biomes, base, warmProvince);
        }
        try {
            return biome(biomes, "minecraft:desert");
        } catch (Throwable ignored) {
            return enforceWarmProvinceFamily(biomes, base, warmProvince);
        }
    }

    private static RegistryEntry<Biome> pickAridRegionFallback(Collection<RegistryEntry<Biome>> biomes,
                                                                RegistryEntry<Biome> base,
                                                                int blockX,
                                                                int blockZ) {
        int radiusHint = ACTIVE_RADIUS_BLOCKS > 0 ? ACTIVE_RADIUS_BLOCKS : (REFERENCE_DIAMETER_BLOCKS / 2);
        ProvinceAuthority.Province warmProvince = warmProvinceClass(
                blockX,
                blockZ,
                authoritativeLandBandIndex(blockX, blockZ, radiusHint));
        if (warmProvince != ProvinceAuthority.Province.WARM_DRY) {
            return base;
        }
        if (!badlandsProvinceAuthorityHit(WORLD_SEED, blockX, blockZ, radiusHint)) {
            int outsideScale = Math.max(ARID_REGION_MIN_SCALE_BLOCKS, (int) Math.round(radiusHint * 0.45));
            double outsideNoise = ValueNoise2D.sampleBlocks(
                    WORLD_SEED ^ BADLANDS_OUTSIDE_PROVINCE_SALT, blockX, blockZ, outsideScale);
            if (outsideNoise < BADLANDS_OUTSIDE_PROVINCE_THRESHOLD) {
                RegistryEntry<Biome> outsideVariant = chooseBadlandsVariant(biomes, blockX, blockZ);
                if (outsideVariant != null) {
                    return outsideVariant;
                }
            }
            return enforceWarmProvinceFamily(biomes, base, warmProvince);
        }
        RegistryEntry<Biome> variant = chooseBadlandsVariant(biomes, blockX, blockZ);
        if (variant != null) {
            return variant;
        }
        if (!aridHotspotHere(WORLD_SEED, blockX, blockZ)) {
            return enforceWarmProvinceFamily(biomes, base, warmProvince);
        }
        RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
        return desert != null ? desert : enforceWarmProvinceFamily(biomes, base, warmProvince);
    }

    private static void logSubtropicalJungleReturn(String pathLabel,
                                                   int blockX,
                                                   int blockZ,
                                                   double t,
                                                   int landBandIndex,
                                                   RegistryEntry<Biome> base,
                                                   RegistryEntry<Biome> chosen,
                                                   RegistryEntry<Biome> sanitized,
                                                   RegistryEntry<Biome> preEnforce,
                                                   RegistryEntry<Biome> postEnforce,
                                                   RegistryEntry<Biome> postClamp,
                                                   RegistryEntry<Biome> finalOut) {
        if (!DEBUG_SUBTROPICAL_JUNGLE || landBandIndex != BAND_SUBTROPICAL || finalOut == null || !isJungleFamily(finalOut)) {
            return;
        }
        double latDeg = clamp(t, 0.0, 1.0) * 90.0;
        LOGGER.info("[LAT][SUBTROPICAL_JUNGLE] path={} x={} z={} bandIndex={} latDeg={} base={} chosen={} sanitized={} preEnforce={} postEnforce={} postClamp={} final={}",
                pathLabel,
                blockX,
                blockZ,
                landBandIndex,
                String.format(java.util.Locale.ROOT, "%.3f", latDeg),
                biomeId(base),
                biomeId(chosen),
                biomeId(sanitized),
                biomeId(preEnforce),
                biomeId(postEnforce),
                biomeId(postClamp),
                biomeId(finalOut));
    }

    private static void logAtlasViewportJungleReturn(String pathLabel,
                                                     String callerContext,
                                                     int blockX,
                                                     int blockZ,
                                                     double t,
                                                     int landBandIndex,
                                                     int overlayBandIndex,
                                                     RegistryEntry<Biome> base,
                                                     RegistryEntry<Biome> chosen,
                                                     RegistryEntry<Biome> sanitized,
                                                     RegistryEntry<Biome> preEnforce,
                                                     RegistryEntry<Biome> postEnforce,
                                                     RegistryEntry<Biome> postClamp,
                                                     RegistryEntry<Biome> finalOut) {
        if (!DEBUG_SUBTROPICAL_JUNGLE || finalOut == null || !isJungleFamily(finalOut)) {
            return;
        }
        if (!"SOURCE".equalsIgnoreCase(callerContext) && !"ATLAS_SAMPLER".equalsIgnoreCase(callerContext)) {
            return;
        }
        if (overlayBandIndex != BAND_SUBTROPICAL) {
            return;
        }
        double latDeg = clamp(t, 0.0, 1.0) * 90.0;
        LOGGER.info("[LAT][ATLAS_SUBTROPICAL_JUNGLE] path={} caller={} x={} z={} bandIndex={} overlayBandIndex={} latDeg={} base={} chosen={} sanitized={} preEnforce={} postEnforce={} postClamp={} final={}",
                pathLabel,
                callerContext,
                blockX,
                blockZ,
                landBandIndex,
                overlayBandIndex,
                String.format(java.util.Locale.ROOT, "%.3f", latDeg),
                biomeId(base),
                biomeId(chosen),
                biomeId(sanitized),
                biomeId(preEnforce),
                biomeId(postEnforce),
                biomeId(postClamp),
                biomeId(finalOut));
    }

    private static void setSelectionPath(String path) {
        LAST_SELECTION_PATH.set(path);
    }

    private static boolean isJungleFamily(RegistryEntry<Biome> entry) {
        return isBiomeId(entry, "minecraft:jungle")
                || isBiomeId(entry, "minecraft:bamboo_jungle")
                || isBiomeId(entry, "minecraft:sparse_jungle");
    }

    private static String selectionPathForTrace(RegistryEntry<Biome> base, RegistryEntry<Biome> picked) {
        String path = LAST_SELECTION_PATH.get();
        if (path != null && !path.isBlank()) {
            return path;
        }
        return base == picked ? PATH_RETURN_BASE : PATH_TAG_PICK;
    }

    private static @org.jetbrains.annotations.Nullable RegistryEntry<Biome> maybePickWsavStep2SecondaryOverride(Registry<Biome> biomes,
                                                                                                                  int step,
                                                                                                                  boolean plateauLike,
                                                                                                                  List<RegistryEntry<Biome>> candidates) {
        if (step != 2 || !plateauLike || candidates.size() != 2) {
            return null;
        }
        boolean hasSavannaPlateau = candidates.stream().anyMatch(entry -> isBiomeId(entry, "minecraft:savanna_plateau"));
        boolean hasWsav = candidates.stream().anyMatch(entry -> isBiomeId(entry, "minecraft:windswept_savanna"));
        if (!(hasSavannaPlateau && hasWsav)) {
            return null;
        }
        return biome(biomes, "minecraft:windswept_savanna");
    }

    private static @org.jetbrains.annotations.Nullable RegistryEntry<Biome> maybePickWsavStep2SecondaryOverride(Collection<RegistryEntry<Biome>> biomes,
                                                                                                                  int step,
                                                                                                                  boolean plateauLike,
                                                                                                                  List<RegistryEntry<Biome>> candidates) {
        if (step != 2 || !plateauLike || candidates.size() != 2) {
            return null;
        }
        boolean hasSavannaPlateau = candidates.stream().anyMatch(entry -> isBiomeId(entry, "minecraft:savanna_plateau"));
        boolean hasWsav = candidates.stream().anyMatch(entry -> isBiomeId(entry, "minecraft:windswept_savanna"));
        if (!(hasSavannaPlateau && hasWsav)) {
            return null;
        }
        return entryById(biomes, "minecraft:windswept_savanna");
    }

    private static void traceSubpolarJunglePick(int blockX, int blockZ, int radius, int bandIndex,
                                                RegistryEntry<Biome> base, RegistryEntry<Biome> picked) {
        if (bandIndex < BAND_SUBPOLAR || !isJungleFamily(picked)) {
            return;
        }
        if (!SUBPOLAR_JUNGLE_TRACE_LOGGED.compareAndSet(false, true)) {
            return;
        }
        double deg = latitudeDegreesFromRadius(blockZ, radius);
        String baseId = base != null ? biomeId(base) : "null";
        String pickedId = picked != null ? biomeId(picked) : "null";
        LOGGER.warn("[Latitude] subpolar jungle trace: bandIndex={} deg={} x={} z={} picked={} base={} path={}",
                bandIndex,
                String.format(java.util.Locale.ROOT, "%.3f", deg),
                blockX,
                blockZ,
                pickedId,
                baseId,
                selectionPathForTrace(base, picked));
    }

    private static void debugPick(int blockX, int blockZ, int borderRadiusBlocks, double t, LatitudeBands.Band band,
                                  RegistryEntry<Biome> base, RegistryEntry<Biome> out, boolean beachOverride, boolean rareOverride, String mangroveDecision) {
        if (!DEBUG_BIOMES) return;
        if (DEBUG_COUNT.incrementAndGet() > DEBUG_LIMIT) return;
        String decision = mangroveDecision != null ? mangroveDecision : "none";
        LOGGER.info("[LAT_PICK] x={} z={} absZ={} radius={} t={} zone={} base={} out={} beachOverride={} rareOverride={} {}",
                blockX,
                blockZ,
                Math.abs(blockZ),
                borderRadiusBlocks,
                String.format(java.util.Locale.ROOT, "%.3f", t),
                band.id(),
                biomeId(base),
                biomeId(out),
                beachOverride,
                rareOverride,
                decision);
    }

    private static boolean isMangroveCandidate(RegistryEntry<Biome> entry) {
        return isBiomeId(entry, MANGROVE_ID);
    }

    private static boolean isSwampCandidate(RegistryEntry<Biome> entry) {
        return isBiomeId(entry, SWAMP_ID);
    }

    private static boolean shouldTryMangroveOverride(RegistryEntry<Biome> entry, int bandIndex) {
        if (bandIndex > BAND_SUBTROPICAL) {
            return false;
        }
        return isBiomeId(entry, "minecraft:jungle") || isBiomeId(entry, "minecraft:sparse_jungle");
    }

    private static boolean isMountainLike(MultiNoiseUtil.MultiNoiseSampler sampler, int blockX, int blockZ) {
        if (sampler == null) {
            return false;
        }
        int noiseX = blockX >> 2;
        int noiseZ = blockZ >> 2;
        MultiNoiseUtil.NoiseValuePoint point = sampler.sample(noiseX, SURFACE_CLASSIFY_Y >> 2, noiseZ);
        double cont = MultiNoiseUtil.toFloat(point.continentalnessNoise());
        double erosion = MultiNoiseUtil.toFloat(point.erosionNoise());
        double weirdness = MultiNoiseUtil.toFloat(point.weirdnessNoise());
        return cont > 0.10 && erosion < -0.25 && Math.abs(weirdness) > 0.25;
    }

    private static RegistryEntry<Biome> mangroveOverride(Registry<Biome> biomes, RegistryEntry<Biome> fallback) {
        try {
            return biome(biomes, MANGROVE_ID);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    /**
     * Wrapper around {@link #evaluateMangrove} that recovers mangrove surface-gate authority
     * when the general preview-terrain pass was skipped for performance (MIXIN/CAVE_CLAMP).
     *
     * <p>When {@code hasReliableSurface} is false but {@code hasPreviewTerrainInputs} is true
     * (the live-worldgen MIXIN path), {@code columnDecisionY} — already computed via the single
     * cached {@code surfaceDecisionY} call at method entry — is used as the surface elevation.
     * No additional {@code getHeight} calls are made; calling {@link #previewTerrain} from
     * within the MIXIN biome-pick path causes a worldgen deadlock.
     *
     * <p>When no terrain probe inputs are available (atlas / SOURCE / ATLAS_SAMPLER contexts),
     * falls back to {@code fallbackPreview} with gates disabled, preserving prior atlas behavior.
     */
    private static MangroveDecision evaluateMangroveWithSurface(
            int blockX, int blockZ,
            int columnDecisionY,
            PreviewTerrain fallbackPreview,
            int seaLevel,
            MultiNoiseUtil.MultiNoiseSampler sampler,
            boolean nearOcean,
            boolean hasReliableSurface,
            boolean hasPreviewTerrainInputs,
            HeightLimitView heightView) {
        int surfaceY;
        int robustDelta;
        boolean allowMangroveGates;
        if (hasReliableSurface) {
            surfaceY = fallbackPreview.centerHeight;
            robustDelta = fallbackPreview.robustDelta;
            allowMangroveGates = true;
        } else if (hasPreviewTerrainInputs) {
            // columnDecisionY is already computed (one cached getHeight call at method entry).
            // Do NOT call previewTerrain() — 9 getHeight calls from within MIXIN deadlock worldgen.
            // robustDelta is unavailable without a blocking ring probe; height gate alone is enough.
            surfaceY = columnDecisionY;
            robustDelta = 0;
            allowMangroveGates = true;
        } else {
            surfaceY = fallbackPreview.centerHeight;
            robustDelta = fallbackPreview.robustDelta;
            allowMangroveGates = false;
        }
        return evaluateMangrove(blockX, blockZ, surfaceY, seaLevel, robustDelta,
                sampler, nearOcean, allowMangroveGates, heightView);
    }

    private static MangroveDecision evaluateMangrove(int blockX,
                                                     int blockZ,
                                                     int surfaceY,
                                                     int seaLevel,
                                                     int robustDelta,
                                                     MultiNoiseUtil.MultiNoiseSampler sampler,
                                                     boolean nearOcean,
                                                     boolean allowSurfaceGates,
                                                     HeightLimitView heightView) {
        // Utility to emit throttled audit logs for every exit path.
        final java.util.function.Consumer<MangroveDecision> audit = decision -> {
            if (!Boolean.getBoolean("latitude.audit.mangrove")) return;
            int n = MANGROVE_EVAL_AUDIT_N.getAndIncrement();
            if ((n & 0xFF) != 0) return; // throttle to 1/256
            LOGGER.info("[mangrove-eval] n={} x={} z={} oceanDist={} surfaceY={} sea={} robustDelta={} cont={} ero={} weird={} suitable={} patch={} allow={}",
                    n, blockX, blockZ, decision.oceanDistance, surfaceY, seaLevel, robustDelta,
                    String.format(java.util.Locale.ROOT, "%.3f", decision.continentalness),
                    String.format(java.util.Locale.ROOT, "%.3f", decision.erosion),
                    String.format(java.util.Locale.ROOT, "%.3f", decision.weirdness),
                    decision.suitable, decision.patch, decision.allow);
        };
        final java.util.function.BiConsumer<MangroveDecision, String> auditFinal = (decision, decisionLabel) -> {
            if (!Boolean.getBoolean("latitude.audit.mangroveFinal")) return;
            int n = MANGROVE_EVAL_AUDIT_N.incrementAndGet();
            if (n <= 50 || (n % 2000) == 0) {
                LOGGER.info("[mangrove-eval] n={} x={} z={} sea={} surfY={} oceanDist={} cont={} ero={} weird={} robust={} decision={}",
                        n, blockX, blockZ, seaLevel, surfaceY, decision.oceanDistance,
                        String.format(java.util.Locale.ROOT, "%.3f", decision.continentalness),
                        String.format(java.util.Locale.ROOT, "%.3f", decision.erosion),
                        String.format(java.util.Locale.ROOT, "%.3f", decision.weirdness),
                        robustDelta, decisionLabel);
            }
        };

        double cont = 0.0;
        double erosion = 0.0;
        double weirdness = 0.0;
        int oceanDist = -1;
        int radiusHint = ACTIVE_RADIUS_BLOCKS > 0 ? ACTIVE_RADIUS_BLOCKS : (REFERENCE_DIAMETER_BLOCKS / 2);
        double absLatDeg = latitudeDegreesFromRadius(blockZ, Math.max(1, radiusHint));

        if (absLatDeg > MANGROVE_MAX_ABS_LAT_DEG) {
            logMangroveDenial("latitude");
            MangroveDecision d = new MangroveDecision(false, cont, erosion, weirdness, false, false, oceanDist);
            audit.accept(d);
            auditFinal.accept(d, "REJECT(latitude)");
            return d;
        }

        if (sampler == null) {
            logMangroveDenial("no_surface_data");
            MangroveDecision d = new MangroveDecision(false, cont, erosion, weirdness, false, false, oceanDist);
            audit.accept(d);
            auditFinal.accept(d, "REJECT(no_surface_data)");
            return d;
        }
        oceanDist = oceanDistanceBlocks(blockX, blockZ, sampler);
        boolean coastalOk = oceanDist <= MANGROVE_COASTAL_MAX_BLOCKS;
        if (!coastalOk) {
            logMangroveDenial("coastal");
            MangroveDecision d = new MangroveDecision(false, cont, erosion, weirdness, false, false, oceanDist);
            audit.accept(d);
            auditFinal.accept(d, "REJECT(coastal)");
            return d;
        }
        int noiseX = blockX >> 2;
        int noiseZ = blockZ >> 2;
        MultiNoiseUtil.NoiseValuePoint point = sampler.sample(noiseX, SURFACE_CLASSIFY_Y >> 2, noiseZ);
        cont = MultiNoiseUtil.toFloat(point.continentalnessNoise());
        erosion = MultiNoiseUtil.toFloat(point.erosionNoise());
        weirdness = MultiNoiseUtil.toFloat(point.weirdnessNoise());
        if (allowSurfaceGates) {
            int mangroveMaxY = seaLevel + MANGROVE_MAX_Y_ABOVE_SEA;
            if (surfaceY > mangroveMaxY) {
                logMangroveDenial("height");
                MangroveDecision d = new MangroveDecision(false, cont, erosion, weirdness, false, false, oceanDist);
                audit.accept(d);
                auditFinal.accept(d, "REJECT(height)");
                return d;
            }
            if (robustDelta > MANGROVE_MAX_ROBUST_DELTA) {
                logMangroveDenial("rugged");
                MangroveDecision d = new MangroveDecision(false, cont, erosion, weirdness, false, false, oceanDist);
                audit.accept(d);
                auditFinal.accept(d, "REJECT(rugged)");
                return d;
            }
        }
        boolean coastal = cont < MANGROVE_CONTINENTALNESS_MAX;
        boolean floodplain = erosion > 0.12 && Math.abs(weirdness) < 0.40;
        boolean suitable = coastal && floodplain;
        boolean patch;
        if (oceanDist <= 32) {
            patch = true; // true-coast bypass to force first ACCEPTs
        } else {
            patch = allowMangrovePatch(blockX, blockZ);
        }
        boolean allow = suitable && patch;
        // Tiny true-coast invite: if we are extremely coastal, reuse the same suitability but bypass the patch mask.
        if (!allow && oceanDist <= 64 && coastal && floodplain) {
            allow = true;
        }
        if (!allow) {
            logMangroveDenial(!suitable ? "terrain" : "patch");
        }
        MangroveDecision d = new MangroveDecision(allow, cont, erosion, weirdness, suitable, patch, oceanDist);
        audit.accept(d);
        auditFinal.accept(d, d.allow ? "ACCEPT" : "REJECT");
        return d;
    }

    private static ShorelineScan scanShorelineByBiome(HeightLimitView heightView,
                                                      int blockX,
                                                      int blockZ,
                                                      int seaLevel,
                                                      int radius) {
        if (!(heightView instanceof Chunk chunk)) {
            return ShorelineScan.invalid();
        }
        int minX = chunk.getPos().getStartX();
        int maxX = chunk.getPos().getEndX();
        int minZ = chunk.getPos().getStartZ();
        int maxZ = chunk.getPos().getEndZ();
        int waterCount = 0;
        int landCount = 0;
        int shallowWaterCount = 0;
        int sampled = 0;
        // Sample a simple 8-point ring plus center for stability.
        int[][] offsets = {
                {0, 0},
                {radius, 0}, {-radius, 0}, {0, radius}, {0, -radius},
                {radius, radius}, {radius, -radius}, {-radius, radius}, {-radius, -radius}
        };
        for (int[] off : offsets) {
            int x = blockX + off[0];
            int z = blockZ + off[1];
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                continue;
            }
            sampled++;
            int quartX = x >> 2;
            int quartZ = z >> 2;
            int quartY = seaLevel >> 2;
            RegistryEntry<Biome> biome;
            try {
                biome = chunk.getBiomeForNoiseGen(quartX, quartY, quartZ);
            } catch (IllegalStateException ex) {
                return ShorelineScan.invalid();
            }
            boolean isWater = biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_RIVER);
            // Treat mangrove itself as land for the scan to avoid self-justification.
            boolean isMangrove = biome.getKey().map(k -> k.getValue().equals(Identifier.of(MANGROVE_ID))).orElse(false);
            if (isWater && !isMangrove) {
                waterCount++;
                shallowWaterCount++; // biome-based scan cannot tell depth; count as shallow
            } else {
                landCount++;
            }
        }
        if (sampled == 0) {
            return ShorelineScan.invalid();
        }
        return new ShorelineScan(waterCount, landCount, shallowWaterCount);
    }

    private record ShorelineScan(int waterCount, int landCount, int shallowWaterCount) {
        static ShorelineScan invalid() {
            return new ShorelineScan(0, 0, 0);
        }

        boolean isValid() {
            return (waterCount + landCount) > 0;
        }

        double waterFraction() {
            int total = waterCount + landCount;
            return total <= 0 ? 0.0 : (double) waterCount / (double) total;
        }
    }

    private static boolean swampOkStrict(double cont, double erosion, double weirdness) {
        return cont > -0.20 && cont < 0.55
            && erosion > -0.20
            && Math.abs(weirdness) < 0.16;
    }

    private static boolean swampOkInPatch(double cont, double erosion, double weirdness) {
        return cont > -0.25 && cont < 0.70
            && erosion > -0.35
            && Math.abs(weirdness) < 0.35;
    }

    /**
     * World-size-aware terrain gate for the evaluateSwamp decision (post-pick validation).
     * swampOkStrict was designed for large worlds where the tropical/temperate bands are wide
     * and favorable terrain is common. On small worlds the bands are narrow, meaning
     * fewer positions land in the favored noise windows.
     * We widen contMax, erosionMin, and weirdnessMax proportionally with world-size slack.
     * Caps prevent placing swamp in genuinely extreme terrain even at minimum size.
     * Regular+ worlds use the baseline swampOkStrict thresholds unchanged.
     */
    private static boolean swampOkForSize(double cont, double erosion, double weirdness) {
        double scale = rarePatchWorldScale();
        if (scale >= 1.0) return swampOkStrict(cont, erosion, weirdness);
        double slack = (1.0 - scale) * 0.40; // 0.20 at R5000, 0.10 at R7500
        double contMax    = Math.min(0.55 + slack * 0.50, 0.75); // 0.65 at R5000
        double erosionMin = Math.max(-0.20 - slack * 0.50, -0.40); // -0.30 at R5000
        double weirdMax   = Math.min(0.16 + slack * 0.60, 0.32);   // 0.28 at R5000
        return cont > -0.20 && cont < contMax
            && erosion > erosionMin
            && Math.abs(weirdness) < weirdMax;
    }

    /**
     * World-size-aware swamp terrain gate for the triple-gate pre-filter.
     * Small worlds use wider cont and erosion bands because the same block-space noise
     * distribution is compressed into narrower latitude band strips, under-sampling
     * favorable terrain regions. Weirdness kept fixed to avoid mountain swamps.
     * Regular+ worlds fall back to the baseline swampOkInPatch filter unchanged.
     */
    private static boolean swampOkInPatchScaled(double cont, double erosion, double weirdness) {
        double scale = rarePatchWorldScale();
        if (scale >= 1.0) return swampOkInPatch(cont, erosion, weirdness);
        double slack = (1.0 - scale) * 0.40; // 0.20 slack at R5000, 0.10 at R7500
        double contMax = Math.min(0.70 + slack, 0.90);
        double erosionMin = Math.max(-0.35 - slack, -0.55);
        return cont > -0.25 && cont < contMax
            && erosion > erosionMin
            && Math.abs(weirdness) < 0.35;
    }

    private static SwampDecision evaluateSwamp(int blockX, int blockZ, MultiNoiseUtil.MultiNoiseSampler sampler) {
        if (sampler == null) {
            return new SwampDecision(false, 0.0, 0.0, 0.0, false);
        }
        int noiseX = blockX >> 2;
        int noiseZ = blockZ >> 2;
        MultiNoiseUtil.NoiseValuePoint point = sampler.sample(noiseX, SURFACE_CLASSIFY_Y >> 2, noiseZ);
        double cont = MultiNoiseUtil.toFloat(point.continentalnessNoise());
        double erosion = MultiNoiseUtil.toFloat(point.erosionNoise());
        double weirdness = MultiNoiseUtil.toFloat(point.weirdnessNoise());
        boolean swampOk = swampOkForSize(cont, erosion, weirdness);
        return new SwampDecision(swampOk, cont, erosion, weirdness, swampOk);
    }

    private static double rarePatchWorldScale() {
        int radius = ACTIVE_RADIUS_BLOCKS;
        if (radius <= 0) {
            radius = REFERENCE_DIAMETER_BLOCKS / 2;
        }
        double baseline = (double) REFERENCE_DIAMETER_BLOCKS / 2.0;
        if (baseline <= 0.0) {
            return 1.0;
        }
        // Do not upscale beyond regular; only shrink to help small worlds keep rare biomes visible.
        return MathHelper.clamp(radius / baseline, 0.25, 1.0);
    }

    /**
     * Scales the swamp blob-patch acceptance chance with active world radius.
     * Small worlds get a higher chance so that the fixed-size patch grid covers more area
     * within each narrow latitude band without rearranging blob positions.
     * Cap at 0.85 to keep patches meaningfully bounded. Regular+ worlds use the base chance.
     */
    private static double scaledSwampPatchChance() {
        double scale = rarePatchWorldScale();
        if (scale >= 1.0) return SWAMP_PATCH_CHANCE;
        double boost = (1.0 - scale) * 0.30; // +0.15 at R5000, +0.075 at R7500
        return Math.min(SWAMP_PATCH_CHANCE + boost, 0.85);
    }

    private static boolean allowMangrovePatch(int blockX, int blockZ) {
        int cellX = Math.floorDiv(blockX, MANGROVE_PATCH_CELL_BLOCKS);
        int cellZ = Math.floorDiv(blockZ, MANGROVE_PATCH_CELL_BLOCKS);
        long roll = hash64(cellX, cellZ, MANGROVE_PATCH_SALT);
        return Long.remainderUnsigned(roll, 100L) < MANGROVE_PATCH_PERCENT;
    }

    private static boolean badlandsPatchHere(long seed, int blockX, int blockZ) {
        double n = blobNoise01(seed ^ BADLANDS_PATCH_SALT, blockX, blockZ, BADLANDS_PATCH_SIZE_BLOCKS, 0);
        return n < BADLANDS_PATCH_CHANCE;
    }

    // Diagnostic helpers (read-only)
    public static boolean debugAridHotspot(int blockX, int blockZ) {
        return aridHotspotHere(WORLD_SEED, blockX, blockZ);
    }

    public static boolean debugBadlandsPatch(int blockX, int blockZ) {
        return badlandsPatchHere(WORLD_SEED, blockX, blockZ);
    }

    private static ProvinceAuthority.Province warmProvinceClass(int blockX, int blockZ, int bandIndex) {
        if (bandIndex != BAND_TROPICAL && bandIndex != BAND_SUBTROPICAL) {
            return null;
        }
        ProvinceAuthority.Province province = classifyProvince(blockX, blockZ);
        return switch (province) {
            case WARM_WET, WARM_MEDIUM, WARM_DRY -> province;
            default -> null;
        };
    }

    private static boolean isDesertFamily(RegistryEntry<Biome> biome) {
        return isBiomeId(biome, "minecraft:desert");
    }

    private static RegistryEntry<Biome> enforceWarmProvinceFamily(Registry<Biome> biomes,
                                                                  RegistryEntry<Biome> pick,
                                                                  ProvinceAuthority.Province province) {
        if (province == null || pick == null) {
            return pick;
        }
        switch (province) {
            case WARM_WET -> {
                if (isJungleFamily(pick)) return pick;
                // Jungle is the WARM_WET core identity; sparse_jungle is the edge/shoulder.
                // Default the late rewrite to jungle so non-jungle picks become jungle cores
                // instead of being silently converted into sparse_jungle here.
                try {
                    return biome(biomes, "minecraft:jungle");
                } catch (Throwable ignored) {
                }
                try {
                    return biome(biomes, "minecraft:bamboo_jungle");
                } catch (Throwable ignored) {
                }
                try {
                    return biome(biomes, "minecraft:sparse_jungle");
                } catch (Throwable ignored) {
                }
                return pick;
            }
            case WARM_MEDIUM -> {
                if (isSavannaFamily(pick)) return pick;
                try {
                    return biome(biomes, "minecraft:savanna");
                } catch (Throwable ignored) {
                }
                try {
                    return biome(biomes, "minecraft:savanna_plateau");
                } catch (Throwable ignored) {
                }
                try {
                    return biome(biomes, "minecraft:windswept_savanna");
                } catch (Throwable ignored) {
                    return pick;
                }
            }
            case WARM_DRY -> {
                if (isDesertFamily(pick) || isBadlandsFamily(pick)) return pick;
                try {
                    return biome(biomes, "minecraft:desert");
                } catch (Throwable ignored) {
                }
                try {
                    return biome(biomes, "minecraft:badlands");
                } catch (Throwable ignored) {
                    return pick;
                }
            }
            default -> {
                return pick;
            }
        }
    }

    private static RegistryEntry<Biome> enforceWarmProvinceFamily(Collection<RegistryEntry<Biome>> biomes,
                                                                  RegistryEntry<Biome> pick,
                                                                  ProvinceAuthority.Province province) {
        if (province == null || pick == null) {
            return pick;
        }
        switch (province) {
            case WARM_WET -> {
                if (isJungleFamily(pick)) return pick;
                // Jungle is the WARM_WET core identity; sparse_jungle is the edge/shoulder.
                // Default the late rewrite to jungle so non-jungle picks become jungle cores
                // instead of being silently converted into sparse_jungle here.
                RegistryEntry<Biome> jungle = entryById(biomes, "minecraft:jungle");
                if (jungle != null) return jungle;
                RegistryEntry<Biome> bamboo = entryById(biomes, "minecraft:bamboo_jungle");
                if (bamboo != null) return bamboo;
                RegistryEntry<Biome> sparse = entryById(biomes, "minecraft:sparse_jungle");
                if (sparse != null) return sparse;
                return pick;
            }
            case WARM_MEDIUM -> {
                if (isSavannaFamily(pick)) return pick;
                RegistryEntry<Biome> savanna = entryById(biomes, "minecraft:savanna");
                if (savanna != null) return savanna;
                RegistryEntry<Biome> plateau = entryById(biomes, "minecraft:savanna_plateau");
                if (plateau != null) return plateau;
                RegistryEntry<Biome> windswept = entryById(biomes, "minecraft:windswept_savanna");
                return windswept != null ? windswept : pick;
            }
            case WARM_DRY -> {
                if (isDesertFamily(pick) || isBadlandsFamily(pick)) return pick;
                RegistryEntry<Biome> desert = entryById(biomes, "minecraft:desert");
                if (desert != null) return desert;
                RegistryEntry<Biome> badlands = entryById(biomes, "minecraft:badlands");
                return badlands != null ? badlands : pick;
            }
            default -> {
                return pick;
            }
        }
    }

    
    private static double toUnitDouble(long h) {
        // Map 53 random bits to [0,1)
        return ((h >>> 11) & ((1L << 53) - 1L)) * (1.0 / (1L << 53));
    }

    private static double wetlandNoiseSymmetric(long worldSeed, int blockX, int blockZ) {
        // Symmetric N/S: use abs(z) so wetland eligibility doesn't differ between hemispheres.
        int z = Math.abs(blockZ);

        // Spatially correlated noise at the declared WETLAND_SCALE_BLOCKS (1200 blocks).
        // Previous per-block hash produced white noise, fragmenting swamp patches into confetti.
        return ValueNoise2D.sampleBlocks(worldSeed ^ WETLAND_SALT, blockX, z, WETLAND_SCALE_BLOCKS);
    }

    private static boolean paleGardenRegionHit(long worldSeed, int blockX, int blockZ, int effectiveRadiusHint) {
        int radius = effectiveRadiusHint > 0 ? effectiveRadiusHint : ACTIVE_RADIUS_BLOCKS;
        if (radius <= 0) {
            radius = REFERENCE_DIAMETER_BLOCKS / 2;
        }
        radius = Math.max(1, radius);

        int temperateMinAbsZ = bandBoundaryBlocks(1, radius);
        int temperateMaxAbsZ = bandBoundaryBlocks(2, radius);
        if (temperateMaxAbsZ <= temperateMinAbsZ) {
            return false;
        }

        int temperateSpan = temperateMaxAbsZ - temperateMinAbsZ;
        int temperateInset = Math.max(64, (int) Math.round(temperateSpan * PALE_GARDEN_REGION_TEMPERATE_INSET_FRAC));
        int minAnchorAbsZ = Math.min(temperateMaxAbsZ - 1, temperateMinAbsZ + temperateInset);
        int maxAnchorAbsZ = Math.max(minAnchorAbsZ, temperateMaxAbsZ - temperateInset);
        int anchorAbsZ = minAnchorAbsZ
                + (int) Math.floor(toUnitDouble(mix64(worldSeed ^ PALE_GARDEN_REGION_ANCHOR_Z_SALT))
                * (double) (maxAnchorAbsZ - minAnchorAbsZ + 1));
        int hemisphereSign = (mix64(worldSeed ^ PALE_GARDEN_REGION_HEMI_SALT) & 1L) == 0L ? 1 : -1;
        int anchorZ = anchorAbsZ * hemisphereSign;

        int xInset = Math.max(512, (int) Math.round(radius * PALE_GARDEN_REGION_X_INSET_FRAC));
        int minAnchorX = -radius + xInset;
        int maxAnchorX = radius - xInset;
        if (maxAnchorX <= minAnchorX) {
            minAnchorX = -radius / 3;
            maxAnchorX = radius / 3;
        }
        int anchorX = minAnchorX
                + (int) Math.floor(toUnitDouble(mix64(worldSeed ^ PALE_GARDEN_REGION_ANCHOR_X_SALT))
                * (double) (Math.max(1, maxAnchorX - minAnchorX + 1)));

        double dx = (double) blockX - (double) anchorX;
        double dz = (double) blockZ - (double) anchorZ;
        double theta = Math.atan2(dz, dx);
        int shapeX = (int) Math.round(Math.cos(theta) * PALE_GARDEN_REGION_ANGLE_SAMPLE_BLOCKS);
        int shapeZ = (int) Math.round(Math.sin(theta) * PALE_GARDEN_REGION_ANGLE_SAMPLE_BLOCKS);
        double shapeNoise = ValueNoise2D.sampleBlocks(
                worldSeed ^ PALE_GARDEN_REGION_SHAPE_SALT,
                shapeX,
                shapeZ,
                PALE_GARDEN_REGION_WOBBLE_SCALE_BLOCKS);
        double shapeSigned = (shapeNoise * 2.0) - 1.0;
        double baseRadius = Math.max(PALE_GARDEN_REGION_MIN_RADIUS_BLOCKS, radius * PALE_GARDEN_REGION_RADIUS_FRAC);
        double regionRadius = baseRadius * (1.0 + shapeSigned * PALE_GARDEN_REGION_WOBBLE_FRAC);
        regionRadius = Math.max(baseRadius * (1.0 - PALE_GARDEN_REGION_WOBBLE_FRAC), regionRadius);

        return (dx * dx + dz * dz) <= (regionRadius * regionRadius);
    }

    // Tests whether the given block is inside the pale_garden INNER CORE, which is
    // nested at the same anchor center as the outer dark-forest container but uses
    // a proportionally smaller radius and lighter wobble amplitude.
    private static boolean paleGardenCoreHit(long worldSeed, int blockX, int blockZ, int effectiveRadiusHint) {
        int radius = effectiveRadiusHint > 0 ? effectiveRadiusHint : ACTIVE_RADIUS_BLOCKS;
        if (radius <= 0) {
            radius = REFERENCE_DIAMETER_BLOCKS / 2;
        }
        radius = Math.max(1, radius);

        int temperateMinAbsZ = bandBoundaryBlocks(1, radius);
        int temperateMaxAbsZ = bandBoundaryBlocks(2, radius);
        if (temperateMaxAbsZ <= temperateMinAbsZ) {
            return false;
        }

        int temperateSpan = temperateMaxAbsZ - temperateMinAbsZ;
        int temperateInset = Math.max(64, (int) Math.round(temperateSpan * PALE_GARDEN_REGION_TEMPERATE_INSET_FRAC));
        int minAnchorAbsZ = Math.min(temperateMaxAbsZ - 1, temperateMinAbsZ + temperateInset);
        int maxAnchorAbsZ = Math.max(minAnchorAbsZ, temperateMaxAbsZ - temperateInset);
        int anchorAbsZ = minAnchorAbsZ
                + (int) Math.floor(toUnitDouble(mix64(worldSeed ^ PALE_GARDEN_REGION_ANCHOR_Z_SALT))
                * (double) (maxAnchorAbsZ - minAnchorAbsZ + 1));
        int hemisphereSign = (mix64(worldSeed ^ PALE_GARDEN_REGION_HEMI_SALT) & 1L) == 0L ? 1 : -1;
        int anchorZ = anchorAbsZ * hemisphereSign;

        int xInset = Math.max(512, (int) Math.round(radius * PALE_GARDEN_REGION_X_INSET_FRAC));
        int minAnchorX = -radius + xInset;
        int maxAnchorX = radius - xInset;
        if (maxAnchorX <= minAnchorX) {
            minAnchorX = -radius / 3;
            maxAnchorX = radius / 3;
        }
        int anchorX = minAnchorX
                + (int) Math.floor(toUnitDouble(mix64(worldSeed ^ PALE_GARDEN_REGION_ANCHOR_X_SALT))
                * (double) (Math.max(1, maxAnchorX - minAnchorX + 1)));

        double dx = (double) blockX - (double) anchorX;
        double dz = (double) blockZ - (double) anchorZ;
        double theta = Math.atan2(dz, dx);
        int shapeX = (int) Math.round(Math.cos(theta) * PALE_GARDEN_REGION_ANGLE_SAMPLE_BLOCKS);
        int shapeZ = (int) Math.round(Math.sin(theta) * PALE_GARDEN_REGION_ANGLE_SAMPLE_BLOCKS);
        double shapeNoise = ValueNoise2D.sampleBlocks(
                worldSeed ^ PALE_GARDEN_CORE_SHAPE_SALT,
                shapeX,
                shapeZ,
                PALE_GARDEN_REGION_WOBBLE_SCALE_BLOCKS);
        double shapeSigned = (shapeNoise * 2.0) - 1.0;
        double outerBaseRadius = Math.max(PALE_GARDEN_REGION_MIN_RADIUS_BLOCKS, radius * PALE_GARDEN_REGION_RADIUS_FRAC);
        double coreBaseRadius = outerBaseRadius * PALE_GARDEN_CORE_RADIUS_FRAC;
        double coreRadius = coreBaseRadius * (1.0 + shapeSigned * PALE_GARDEN_CORE_WOBBLE_FRAC);
        coreRadius = Math.max(coreBaseRadius * (1.0 - PALE_GARDEN_CORE_WOBBLE_FRAC), coreRadius);

        return (dx * dx + dz * dz) <= (coreRadius * coreRadius);
    }

    private static double wetlandThresholdForBand(int bandIndex, double t) {
        // Lower threshold => more wetlands. Tropical-biased, subtropical less frequent.
        if (bandIndex == BAND_TROPICAL) return 0.20;
        if (bandIndex == BAND_SUBTROPICAL) return 0.20;
        return 0.45;
    }

    /**
     * World-size-aware wetland acceptance threshold. On small worlds the band strips are narrow,
     * so we relax the threshold to preserve wet-lowland opportunity within each valid patch cell.
     * Regular+ worlds use the base threshold unchanged.
     * Tropical cap (0.35) and temperate cap (0.62) prevent over-swamping even at minimum size.
     */
    private static double scaledWetlandThresholdForBand(int bandIndex, double t) {
        double base = wetlandThresholdForBand(bandIndex, t);
        double scale = rarePatchWorldScale();
        if (scale >= 1.0) return base; // regular+ worlds: no change
        // Boost: up to +60% relative at R5000, proportionally less at R7500.
        // Tropical: 0.20 -> 0.32 at R5000. Temperate: 0.45 -> 0.54 at R5000.
        double boost = 1.0 + (1.0 - scale) * 1.20;
        double max = (bandIndex == BAND_TROPICAL) ? 0.35 : 0.62;
        return Math.min(base * boost, max);
    }

    private static boolean swampPatchHere(long seed, int blockX, int blockZ) {
        double n = blobNoise01Blocks(seed ^ SWAMP_PATCH_SALT, blockX, blockZ, SWAMP_PATCH_SIZE_BLOCKS, SWAMP_PATCH_SALT);
        return n < scaledSwampPatchChance();
    }

    private static RegistryEntry<Biome> pickMangroveFallback(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t, int bandIndex) {
        return switch (bandIndex) {
            case BAND_TROPICAL -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_TROPICAL, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_SUBTROPICAL -> pickTropicalGradientNoMangrove(biomes, base, blockX, blockZ, t);
            case BAND_TEMPERATE -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickSubpolarWithRamp(biomes, base, blockX, blockZ, t, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
    }

    private static RegistryEntry<Biome> pickMangroveFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t, int bandIndex) {
        return switch (bandIndex) {
            case BAND_TROPICAL -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_TROPICAL, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_SUBTROPICAL -> pickTropicalGradientNoMangrove(biomes, base, blockX, blockZ, t);
            case BAND_TEMPERATE -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickSubpolarWithRamp(biomes, base, blockX, blockZ, t, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
    }

    private static RegistryEntry<Biome> pickSwampFallback(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t, int bandIndex) {
        return switch (bandIndex) {
            case BAND_TROPICAL -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_TROPICAL, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_SUBTROPICAL -> sanitizeSubtropicalSwampFallback(biomes, pickTropicalGradientNoSwamp(biomes, base, blockX, blockZ, t));
            case BAND_TEMPERATE -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickSubpolarWithRamp(biomes, base, blockX, blockZ, t, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
    }

    private static RegistryEntry<Biome> pickSwampFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t, int bandIndex) {
        return switch (bandIndex) {
            case BAND_TROPICAL -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_TROPICAL, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_SUBTROPICAL -> sanitizeSubtropicalSwampFallback(biomes, pickTropicalGradientNoSwamp(biomes, base, blockX, blockZ, t));
            case BAND_TEMPERATE -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickSubpolarWithRamp(biomes, base, blockX, blockZ, t, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
    }

    private static RegistryEntry<Biome> clampLateWetlandSurvival(Registry<Biome> biomes,
                                                                  RegistryEntry<Biome> candidate,
                                                                  RegistryEntry<Biome> base,
                                                                  int blockX,
                                                                  int blockZ,
                                                                  double t,
                                                                  int bandIndex,
                                                                  boolean mountainLike,
                                                                  int oceanDistance) {
        RegistryEntry<Biome> out = candidate;
        if (isMangroveCandidate(out)) {
            boolean inlandMangrove = oceanDistance < 0 || oceanDistance > MANGROVE_COASTAL_MAX_BLOCKS;
            if (mountainLike || inlandMangrove) {
                out = pickMangroveFallback(biomes, base, blockX, blockZ, t, bandIndex);
            }
        }
        if (isSwampCandidate(out)) {
            boolean inlandSwamp = oceanDistance < 0 || oceanDistance > SWAMP_SUBTROPICAL_PATCH_MAX_OCEAN_DISTANCE;
            if (mountainLike || inlandSwamp) {
                out = pickSwampFallback(biomes, base, blockX, blockZ, t, bandIndex);
            }
        }
        return out;
    }

    private static RegistryEntry<Biome> clampLateWetlandSurvival(Collection<RegistryEntry<Biome>> biomes,
                                                                  RegistryEntry<Biome> candidate,
                                                                  RegistryEntry<Biome> base,
                                                                  int blockX,
                                                                  int blockZ,
                                                                  double t,
                                                                  int bandIndex,
                                                                  boolean mountainLike,
                                                                  int oceanDistance) {
        RegistryEntry<Biome> out = candidate;
        if (isMangroveCandidate(out)) {
            boolean inlandMangrove = oceanDistance < 0 || oceanDistance > MANGROVE_COASTAL_MAX_BLOCKS;
            if (mountainLike || inlandMangrove) {
                out = pickMangroveFallback(biomes, base, blockX, blockZ, t, bandIndex);
            }
        }
        if (isSwampCandidate(out)) {
            boolean inlandSwamp = oceanDistance < 0 || oceanDistance > SWAMP_SUBTROPICAL_PATCH_MAX_OCEAN_DISTANCE;
            if (mountainLike || inlandSwamp) {
                out = pickSwampFallback(biomes, base, blockX, blockZ, t, bandIndex);
            }
        }
        return out;
    }

    private static RegistryEntry<Biome> sanitizeSubtropicalSwampFallback(Registry<Biome> biomes, RegistryEntry<Biome> pick) {
        if (!isTaigaFamilyBiome(pick)) {
            return pick;
        }
        RegistryEntry<Biome> warmFallback = pickWarmFallback(biomes, BAND_SUBTROPICAL);
        return warmFallback != null ? warmFallback : pick;
    }

    private static RegistryEntry<Biome> sanitizeSubtropicalSwampFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> pick) {
        if (!isTaigaFamilyBiome(pick)) {
            return pick;
        }
        RegistryEntry<Biome> warmFallback = pickWarmFallback(biomes, BAND_SUBTROPICAL);
        return warmFallback != null ? warmFallback : pick;
    }

    private static RegistryEntry<Biome> repickIfSurfaceCave(Registry<Biome> biomes, RegistryEntry<Biome> base, RegistryEntry<Biome> pick,
                                                             int blockX, int blockZ, double t, int bandIndex) {
        RegistryKey<Biome> key = biomes.getKey(pick.value()).orElse(null);
        if (key == null) {
            return pick;
        }

        if (!SURFACE_CAVE_DENYLIST.contains(key.getValue().toString())) {
            return pick;
        }

        RegistryEntry<Biome> fallback = pickMangroveFallback(biomes, base, blockX, blockZ, t, bandIndex);
        return fallback != null ? fallback : pick;
    }

    private static RegistryEntry<Biome> repickIfSurfaceCave(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, RegistryEntry<Biome> pick,
                                                             int blockX, int blockZ, double t, int bandIndex) {
        Identifier id = pick.getKey().map(key -> key.getValue()).orElse(null);
        if (id == null) {
            return pick;
        }

        if (!SURFACE_CAVE_DENYLIST.contains(id.toString())) {
            return pick;
        }

        RegistryEntry<Biome> fallback = pickMangroveFallback(biomes, base, blockX, blockZ, t, bandIndex);
        return fallback != null ? fallback : pick;
    }

    private static RegistryEntry<Biome> pickTropicalGradientNoMangrove(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        long seed = WORLD_SEED;

        double bandStart = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
        double bandEnd = LatitudeBands.Band.TEMPERATE.lowDeg() / 90.0;
        double u = clamp((t - bandStart) / (bandEnd - bandStart), 0.0, 1.0);
        double ladderT = 1.0 - u;

        double jitterN = (blobNoise01(seed, chunkX, chunkZ, 8, 0xBADC0FFEE0DDF00DL) * 2.0) - 1.0;
        double tJitter = ladderT + (jitterN * 0.12);
        tJitter = clamp(tJitter, 0.0, 1.0);
        tJitter = smoothstep(tJitter);

        double stepFloat = tJitter * 4.0;
        int baseStep = clampInt((int) Math.floor(stepFloat), 0, 3);
        double stepFrac = stepFloat - baseStep;
        int step = applyTropicalStepDither(seed, blockX, blockZ, baseStep, stepFrac);
        boolean coldShoulderArid = step == 0 && u >= SUBTROPICAL_ARID_SHOULDER_U;

        RegistryEntry<Biome> pick = switch (step) {
            case 1 -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT);
            case 2 -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 102, 0x7A22,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            case 3 -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 103, 0x7A33,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            default -> coldShoulderArid
                    ? pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT)
                    : pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 100, 0x7A00,
                    LAT_ARID_PRIMARY, LAT_ARID_SECONDARY, LAT_ARID_ACCENT);
        };
        RegistryEntry<Biome> out = softenSubtropicalBadlands(biomes, base, pick);
        recordWarmDryPath("TROPICAL_GRADIENT", base, out, blockX, blockZ, BAND_SUBTROPICAL, warmProvinceClass(blockX, blockZ, BAND_SUBTROPICAL));
        return out;
    }

    private static RegistryEntry<Biome> pickTropicalGradientNoMangrove(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        long seed = WORLD_SEED;

        double bandStart = LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0;
        double bandEnd = LatitudeBands.Band.TEMPERATE.lowDeg() / 90.0;
        double u = clamp((t - bandStart) / (bandEnd - bandStart), 0.0, 1.0);
        double ladderT = 1.0 - u;

        double jitterN = (blobNoise01(seed, chunkX, chunkZ, 8, 0xBADC0FFEE0DDF00DL) * 2.0) - 1.0;
        double tJitter = ladderT + (jitterN * 0.12);
        tJitter = clamp(tJitter, 0.0, 1.0);
        tJitter = smoothstep(tJitter);

        double stepFloat = tJitter * 4.0;
        int baseStep = clampInt((int) Math.floor(stepFloat), 0, 3);
        double stepFrac = stepFloat - baseStep;
        int step = applyTropicalStepDither(seed, blockX, blockZ, baseStep, stepFrac);
        boolean coldShoulderArid = step == 0 && u >= SUBTROPICAL_ARID_SHOULDER_U;

        RegistryEntry<Biome> pick = switch (step) {
            case 1 -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT);
            case 2 -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 102, 0x7A22,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            case 3 -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 103, 0x7A33,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            default -> coldShoulderArid
                    ? pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT)
                    : pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 100, 0x7A00,
                    LAT_ARID_PRIMARY, LAT_ARID_SECONDARY, LAT_ARID_ACCENT);
        };
        RegistryEntry<Biome> out = softenSubtropicalBadlands(biomes, base, pick);
        recordWarmDryPath("TROPICAL_GRADIENT", base, out, blockX, blockZ, BAND_SUBTROPICAL, warmProvinceClass(blockX, blockZ, BAND_SUBTROPICAL));
        return out;
    }

    private record MangroveDecision(boolean allow,
                                    double continentalness,
                                    double erosion,
                                    double weirdness,
                                    boolean suitable,
                                    boolean patch,
                                    int oceanDistance) {
        private String logLabel() {
            String status = allow ? "ACCEPT" : "REJECT";
            String reason = "";
            if (!allow) {
                if (!suitable) {
                    reason = "terrain";
                }
                if (!patch) {
                    reason = reason.isEmpty() ? "patch" : reason + "|patch";
                }
            }
            String note = reason.isEmpty() ? status : status + "(" + reason + ")";
            return String.format(java.util.Locale.ROOT, "mangroveDecision=%s cont=%.3f ero=%.3f weird=%.3f", note, continentalness, erosion, weirdness);
        }
    }

    private record SwampDecision(boolean allow, double continentalness, double erosion, double weirdness, boolean suitable) {
    }

    private static boolean isWarmFamily(RegistryEntry<Biome> biome) {
        return isBiomeId(biome, "minecraft:desert")
                || isBiomeId(biome, "minecraft:badlands")
                || isBiomeId(biome, "minecraft:wooded_badlands")
                || isBiomeId(biome, "minecraft:eroded_badlands")
                || isBiomeId(biome, "minecraft:savanna")
                || isBiomeId(biome, "minecraft:windswept_savanna")
                || isBiomeId(biome, "minecraft:jungle")
                || isBiomeId(biome, "minecraft:bamboo_jungle")
                || isBiomeId(biome, "minecraft:sparse_jungle")
                || isBiomeId(biome, SWAMP_ID)
                || isBiomeId(biome, "minecraft:mangrove_swamp");
    }

    private static RegistryEntry<Biome> sanitizeLandBiome(Registry<Biome> biomes, RegistryEntry<Biome> pick, int bandIndex, int blockX, int blockZ) {
        if (bandIndex == BAND_TROPICAL) {
            ProvinceAuthority.Province warmProvince = warmProvinceClass(blockX, blockZ, bandIndex);
            if (isWarmFamily(pick)) {
                return enforceWarmProvinceFamily(biomes, pick, warmProvince);
            }
            if (isBiomeId(pick, "minecraft:plains")
                    || isBiomeId(pick, "minecraft:forest")
                    || isBiomeId(pick, "minecraft:birch_forest")
                    || isBiomeId(pick, "minecraft:old_growth_birch_forest")
                    || isBiomeId(pick, "minecraft:flower_forest")) {
                try {
                    double openness = tropicalOpennessNoise(blockX, blockZ);
                    double compositionBias = tropicalCompositionBias(WORLD_SEED, blockX, blockZ);
                    // Coarse, rare promotion only when both signals are strongly open/wet.
                    if (openness < 0.92 || compositionBias <= 0.20) {
                        return pick; // keep temperate winner; avoid speckle repaint
                    }
                    RegistryEntry<Biome> promoted;
                    if (openness >= 0.96 && compositionBias > 0.28) {
                        promoted = biome(biomes, "minecraft:savanna");
                    } else if (compositionBias > 0.32) {
                        promoted = biome(biomes, SWAMP_ID);
                    } else {
                        promoted = biome(biomes, "minecraft:sparse_jungle");
                    }
                    return enforceWarmProvinceFamily(biomes, promoted, warmProvince);
                } catch (Throwable ignored) {
                    return pick;
                }
            }
        }

        if (bandIndex == BAND_SUBPOLAR) {
            if (isBiomeId(pick, "minecraft:plains")
                    || isBiomeId(pick, "minecraft:forest")
                    || isBiomeId(pick, "minecraft:birch_forest")
                    || isBiomeId(pick, "minecraft:old_growth_birch_forest")
                    || isBiomeId(pick, "minecraft:flower_forest")) {
                return pickSubpolarForestSanitizeFallback(biomes, pick);
            }
        }

        if (bandIndex >= BAND_POLAR) {
            String path = pick.getKey().map(key -> key.getValue().getPath()).orElse("");
            if (path.contains("forest") || path.contains("taiga") || isBiomeId(pick, "minecraft:cherry_grove")) {
                try {
                    return biome(biomes, "minecraft:ice_spikes");
                } catch (Throwable ignored) {
                    return pick;
                }
            }
        }

        return pick;
    }

    private static RegistryEntry<Biome> applyFinalSavannaClimateClamp(Registry<Biome> biomes,
                                                                       RegistryEntry<Biome> pick,
                                                                       boolean inSavannaRegion,
                                                                       int bandIndex,
                                                                       int blockY,
                                                                       int blockX,
                                                                       int blockZ) {
        RegistryEntry<Biome> out = pick;
        double openness = tropicalOpennessNoise(blockX, blockZ);
        boolean warmOpen = openness >= 0.50;
        boolean tropicalBand = bandIndex == BAND_TROPICAL;
        ProvinceAuthority.Province warmProvince = warmProvinceClass(blockX, blockZ, bandIndex);
        String incomingId = biomeId(pick);
        if (inSavannaRegion) {
            boolean jungleClamped = false;
            if (isJungleFamily(out)) {
                if (warmProvince == ProvinceAuthority.Province.WARM_WET) {
                    return out; // let medium-warm survive instead of being savanna-clamped
                }
                RegistryEntry<Biome> softened = warmProvince == ProvinceAuthority.Province.WARM_DRY
                        ? pickAridRegionFallback(biomes, out, blockX, blockZ)
                        : (tropicalBand && warmOpen
                        ? pickOpenTropicalFallback(biomes, out, blockX, blockZ, LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0)
                        : pickDryWarmFallback(biomes, out));
                jungleClamped = softened != out;
                out = softened;
            } else if (isBadlandsFamily(out) && warmProvince == ProvinceAuthority.Province.WARM_WET) {
                // Outside a true arid province, drop badlands to the narrow dry fallback to avoid seam leakage.
                out = pickDryWarmFallback(biomes, out);
            } else if (warmProvince == ProvinceAuthority.Province.WARM_MEDIUM
                    && openness >= 0.66
                    && (isBiomeId(out, "minecraft:plains") || isBiomeId(out, "minecraft:sunflower_plains"))
                    && blockY >= (SAVANNA_UPLAND_CLAMP_Y + 8)) {
                try {
                    out = tropicalBand && openness < 0.40
                            ? biome(biomes, "minecraft:sparse_jungle")
                            : biome(biomes, "minecraft:savanna");
                } catch (Throwable ignored) {
                    out = pick;
                }
            }

            if (DEBUG_FINAL_SANITIZE && jungleClamped) {
                LOGGER.info("[LAT][FINAL_SANITIZE] jungle_clamp y={} in={} out={} x={} z={}",
                        blockY, incomingId, biomeId(out), blockX, blockZ);
            }
        }
        if (bandIndex == BAND_SUBTROPICAL
                && isBadlandsFamily(out)
                && warmProvince == ProvinceAuthority.Province.WARM_WET
                && blockY >= (SAVANNA_UPLAND_CLAMP_Y + 8)) {
            out = pickDryWarmFallback(biomes, out);
        }
        if (!tropicalBand && isJungleFamily(out) && warmProvince != ProvinceAuthority.Province.WARM_WET) {
            out = pickDryWarmFallback(biomes, out);
        }
        boolean allowWarmMediumSavannaClamp = warmProvince != ProvinceAuthority.Province.WARM_MEDIUM
                || isSavannaFamily(out)
                || isBiomeId(out, "minecraft:plains")
                || isBiomeId(out, "minecraft:sunflower_plains");

        boolean preserveSubtropicalBadlands = bandIndex == BAND_SUBTROPICAL && isBadlandsFamily(out);
        boolean allowProvinceFamilyRewrite = allowWarmMediumSavannaClamp
                && (tropicalBand || warmProvince != ProvinceAuthority.Province.WARM_WET);
        if (preserveSubtropicalBadlands) {
            allowProvinceFamilyRewrite = false;
        }
        if (allowProvinceFamilyRewrite) {
            out = enforceWarmProvinceFamily(biomes, out, warmProvince);
        }
        if (isSavannaFamily(out)) {
            try {
                if (!isBiomeId(out, "minecraft:windswept_savanna")) {
                    String targetId = savannaTierByY(blockY);
                    if ("minecraft:savanna".equals(targetId) && preserveSavannaPlateauAtSanitize(out, blockX, blockZ)) {
                        targetId = "minecraft:savanna_plateau";
                    }
                    out = biome(biomes, targetId);
                }
            } catch (Throwable ignored) {
                // keep current biome
            }
        }
        if (DEBUG_FINAL_SANITIZE && inSavannaRegion) {
            LOGGER.info("[LAT][FINAL_SANITIZE] inSavannaRegion={} y={} in={} outBefore={} outAfter={} x={} z={}",
                    inSavannaRegion, blockY, incomingId, incomingId, biomeId(out), blockX, blockZ);
        }
        return out;
    }

    private static RegistryEntry<Biome> sanitizeLandBiome(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> pick, int bandIndex, int blockX, int blockZ) {
        if (bandIndex == BAND_TROPICAL) {
            ProvinceAuthority.Province warmProvince = warmProvinceClass(blockX, blockZ, bandIndex);
            if (isWarmFamily(pick)) {
                return enforceWarmProvinceFamily(biomes, pick, warmProvince);
            }
            if (isBiomeId(pick, "minecraft:plains")
                    || isBiomeId(pick, "minecraft:forest")
                    || isBiomeId(pick, "minecraft:birch_forest")
                    || isBiomeId(pick, "minecraft:old_growth_birch_forest")
                    || isBiomeId(pick, "minecraft:flower_forest")) {
                double openness = tropicalOpennessNoise(blockX, blockZ);
                double compositionBias = tropicalCompositionBias(WORLD_SEED, blockX, blockZ);
                if (openness < 0.76 || compositionBias <= 0.06) {
                    return pick; // neutral/marginal openness → keep the original temperate winner
                }
                RegistryEntry<Biome> entry = openness >= 0.90
                        ? entryById(biomes, "minecraft:savanna")
                        : (openness >= 0.78 && compositionBias > 0.12
                        ? entryById(biomes, SWAMP_ID)
                        : entryById(biomes, "minecraft:sparse_jungle"));
                if (entry == null) entry = entryById(biomes, "minecraft:sparse_jungle");
                if (entry == null) entry = entryById(biomes, "minecraft:jungle");
                RegistryEntry<Biome> out = enforceWarmProvinceFamily(biomes, entry != null ? entry : pick, warmProvince);
                recordWarmDryPath("SANITIZE_REWRITE", pick, out, blockX, blockZ, bandIndex, warmProvince);
                return out;
            }
        }

        if (bandIndex == 3) {
            if (isBiomeId(pick, "minecraft:plains")
                    || isBiomeId(pick, "minecraft:forest")
                    || isBiomeId(pick, "minecraft:birch_forest")
                    || isBiomeId(pick, "minecraft:old_growth_birch_forest")
                    || isBiomeId(pick, "minecraft:flower_forest")) {
                return pickSubpolarForestSanitizeFallback(biomes, pick);
            }
        }

        if (bandIndex >= BAND_POLAR) {
            String path = pick.getKey().map(key -> key.getValue().getPath()).orElse("");
            if (path.contains("forest") || path.contains("taiga") || isBiomeId(pick, "minecraft:cherry_grove")) {
                RegistryEntry<Biome> entry = entryById(biomes, "minecraft:ice_spikes");
                return entry != null ? entry : pick;
            }
        }

        return pick;
    }

    private static RegistryEntry<Biome> applyFinalSavannaClimateClamp(Collection<RegistryEntry<Biome>> biomes,
                                                                       RegistryEntry<Biome> pick,
                                                                       boolean inSavannaRegion,
                                                                       int bandIndex,
                                                                       int blockY,
                                                                       int blockX,
                                                                       int blockZ) {
        RegistryEntry<Biome> out = pick;
        double openness = tropicalOpennessNoise(blockX, blockZ);
        boolean warmOpen = openness >= 0.50;
        boolean tropicalBand = bandIndex == BAND_TROPICAL;
        ProvinceAuthority.Province warmProvince = warmProvinceClass(blockX, blockZ, bandIndex);
        String incomingId = biomeId(pick);
        if (inSavannaRegion) {
            boolean jungleClamped = false;
            if (isJungleFamily(out)) {
                if (warmProvince == ProvinceAuthority.Province.WARM_WET) {
                    return out; // let medium-warm survive instead of being savanna-clamped
                }
                RegistryEntry<Biome> softened = warmProvince == ProvinceAuthority.Province.WARM_DRY
                        ? pickAridRegionFallback(biomes, out, blockX, blockZ)
                        : (tropicalBand && warmOpen
                        ? pickOpenTropicalFallback(biomes, out, blockX, blockZ, LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0)
                        : pickDryWarmFallback(biomes, out));
                jungleClamped = softened != out;
                out = softened;
            } else if (isBadlandsFamily(out) && warmProvince == ProvinceAuthority.Province.WARM_WET) {
                RegistryEntry<Biome> softened = pickDryWarmFallback(biomes, out);
                out = softened != null ? softened : out;
            } else if (warmProvince == ProvinceAuthority.Province.WARM_MEDIUM
                    && openness >= 0.66
                    && (isBiomeId(out, "minecraft:plains") || isBiomeId(out, "minecraft:sunflower_plains"))
                    && blockY >= (SAVANNA_UPLAND_CLAMP_Y + 8)) {
                RegistryEntry<Biome> softened = tropicalBand && openness < 0.40
                        ? entryById(biomes, "minecraft:sparse_jungle")
                        : entryById(biomes, "minecraft:savanna");
                if (softened != null) {
                    out = softened;
                }
            }

            if (DEBUG_FINAL_SANITIZE && jungleClamped) {
                LOGGER.info("[LAT][FINAL_SANITIZE] jungle_clamp y={} in={} out={} x={} z={}",
                        blockY, incomingId, biomeId(out), blockX, blockZ);
            }
        }
        if (bandIndex == BAND_SUBTROPICAL
                && isBadlandsFamily(out)
                && warmProvince == ProvinceAuthority.Province.WARM_WET
                && blockY >= (SAVANNA_UPLAND_CLAMP_Y + 8)) {
            out = pickDryWarmFallback(biomes, out);
        }
        if (!tropicalBand && isJungleFamily(out) && warmProvince != ProvinceAuthority.Province.WARM_WET) {
            out = pickDryWarmFallback(biomes, out);
        }
        boolean allowWarmMediumSavannaClamp = warmProvince != ProvinceAuthority.Province.WARM_MEDIUM
                || isSavannaFamily(out)
                || isBiomeId(out, "minecraft:plains")
                || isBiomeId(out, "minecraft:sunflower_plains");

        boolean preserveSubtropicalBadlands = bandIndex == BAND_SUBTROPICAL && isBadlandsFamily(out);
        boolean allowProvinceFamilyRewrite = allowWarmMediumSavannaClamp
                && (tropicalBand || warmProvince != ProvinceAuthority.Province.WARM_WET);
        if (preserveSubtropicalBadlands) {
            allowProvinceFamilyRewrite = false;
        }
        if (allowProvinceFamilyRewrite) {
            out = enforceWarmProvinceFamily(biomes, out, warmProvince);
        }
        if (isSavannaFamily(out)) {
            if (!isBiomeId(out, "minecraft:windswept_savanna")) {
                String targetId = savannaTierByY(blockY);
                if ("minecraft:savanna".equals(targetId) && preserveSavannaPlateauAtSanitize(out, blockX, blockZ)) {
                    targetId = "minecraft:savanna_plateau";
                }
                RegistryEntry<Biome> tier = entryById(biomes, targetId);
                if (tier != null) {
                    out = tier;
                }
            }
        }
        recordWarmDryPath("FINAL_SAVANNA_CLAMP", pick, out, blockX, blockZ, bandIndex, warmProvince);
        if (DEBUG_FINAL_SANITIZE && inSavannaRegion) {
            LOGGER.info("[LAT][FINAL_SANITIZE] inSavannaRegion={} y={} in={} outBefore={} outAfter={} x={} z={}",
                    inSavannaRegion, blockY, incomingId, incomingId, biomeId(out), blockX, blockZ);
        }
        return out;
    }

    private static void logTagPools(Collection<RegistryEntry<Biome>> biomes) {
        if (TAG_LOGGED) return;
        TAG_LOGGED = true;

        logTagPool(biomes, LAT_EQUATOR_PRIMARY);
        logTagPool(biomes, LAT_EQUATOR_SECONDARY);
        logTagPool(biomes, LAT_EQUATOR_ACCENT);
        logTagPool(biomes, LAT_TEMPERATE_MOUNTAIN);
        logTagPool(biomes, LAT_SUBPOLAR_PRIMARY);
        logTagPool(biomes, LAT_SUBPOLAR_SECONDARY);
        logTagPool(biomes, LAT_SUBPOLAR_ACCENT);
        logTagPool(biomes, LAT_POLAR_PRIMARY);
        logTagPool(biomes, LAT_POLAR_SECONDARY);
        logTagPool(biomes, LAT_POLAR_ACCENT);
    }

    private static void logTagPool(Collection<RegistryEntry<Biome>> biomes, TagKey<Biome> tag) {
        List<RegistryEntry<Biome>> entries = entriesForTag(biomes, tag);
        int size = entries.size();
        StringBuilder sample = new StringBuilder();
        for (int i = 0; i < Math.min(10, size); i++) {
            String key = entries.get(i).getKey().map(k -> k.getValue().toString()).orElse("?");
            if (i > 0) sample.append(", ");
            sample.append(key);
        }
        LOGGER.info("Tag {} size={} [{}]", tag.id(), size, sample);
    }

    private static boolean isBeachLike(RegistryEntry<Biome> biome) {
        if (biome.isIn(BiomeTags.IS_BEACH)) {
            return true;
        }
        return biome.getKey()
                .map(key -> {
                    String path = key.getValue().getPath();
                    return path.contains("beach") || path.contains("shore");
                })
                .orElse(false);
    }

    private static RegistryEntry<Biome> pickFromTagNoiseOrFallback(Registry<Biome> biomes, TagKey<Biome> tag, int blockX, int blockZ, int bandIndex, String... fallbackOptions) {
        List<RegistryEntry<Biome>> entries = new ArrayList<>();
        for (RegistryEntry<Biome> entry : biomes.iterateEntries(tag)) {
            entries.add(entry);
        }

        entries.sort(Comparator.comparing(entry -> entry.getKey()
                .map(key -> key.getValue().toString())
                .orElse("")));

        int size = entries.size();
        if (size <= 0) {
            setSelectionPath(PATH_FALLBACK_PICK);
            return pickFrom(biomes, blockX, blockZ, bandIndex, fallbackOptions);
        }

        int scaleBlocks = 2048;
        long seed = 0L;
        long salted = seed ^ (0x9E3779B97F4A7C15L * (long) bandIndex);
        double n = ValueNoise2D.sampleBlocks(salted, blockX, blockZ, scaleBlocks);
        int idx = (int) Math.floor(n * (double) size);
        if (idx >= size) {
            idx = size - 1;
        }
        setSelectionPath(PATH_TAG_PICK);
        return entries.get(idx);
    }

    private static RegistryEntry<Biome> pickFromTagNoiseOrBase(Registry<Biome> biomes, TagKey<Biome> tag, RegistryEntry<Biome> base, int blockX, int blockZ, int bandIndex) {
        List<RegistryEntry<Biome>> entries = new ArrayList<>();
        for (RegistryEntry<Biome> entry : biomes.iterateEntries(tag)) {
            entries.add(entry);
        }

        entries.sort(Comparator.comparing(entry -> entry.getKey()
                .map(key -> key.getValue().toString())
                .orElse("")));

        int size = entries.size();
        if (size <= 0) {
            setSelectionPath(PATH_RETURN_BASE);
            return base;
        }

        int scaleBlocks = 2048;
        long seed = 0L;
        long salted = seed ^ (0x9E3779B97F4A7C15L * (long) bandIndex);
        double n = ValueNoise2D.sampleBlocks(salted, blockX, blockZ, scaleBlocks);
        int idx = (int) Math.floor(n * (double) size);
        if (idx >= size) {
            idx = size - 1;
        }
        setSelectionPath(PATH_TAG_PICK);
        RegistryEntry<Biome> pick = entries.get(idx);
        if (bandIndex == BAND_TROPICAL && isBiomeId(pick, "minecraft:sparse_jungle")) {
            double openness = tropicalOpennessNoise(blockX, blockZ);
            double compositionBias = tropicalCompositionBias(WORLD_SEED, blockX, blockZ);
            if (openness >= 0.55 || compositionBias <= 0.16) {
                try {
                    return openness >= 0.20
                            ? biome(biomes, "minecraft:savanna")
                            : biome(biomes, "minecraft:jungle");
                } catch (Throwable ignored) {
                    // keep the original sparse jungle pick if the reroute target is unavailable
                }
            }
        }
        return pick;
    }

    private static long hash64(int x, int z, int bandIndex) {
        long h = 0xcbf29ce484222325L;
        h = fnv1a64(h, x);
        h = fnv1a64(h, z);
        h = fnv1a64(h, bandIndex);
        return mix64(h);
    }

    private static long fnv1a64(long h, long v) {
        h ^= v;
        h *= 0x100000001b3L;
        return h;
    }

    private static boolean isOcean(RegistryEntry<Biome> biome) {
        return biome.getKey()
                .map(key -> key.getValue().getPath().contains("ocean"))
                .orElse(false);
    }

    private static boolean isDeepOcean(RegistryEntry<Biome> biome) {
        return biome.getKey()
                .map(key -> {
                    String path = key.getValue().getPath();
                    return path.contains("ocean") && path.contains("deep");
                })
                .orElse(false);
    }

    private static boolean isRiver(RegistryEntry<Biome> biome) {
        return biome.getKey()
                .map(key -> key.getValue().getPath().contains("river"))
                .orElse(false);
    }
}
