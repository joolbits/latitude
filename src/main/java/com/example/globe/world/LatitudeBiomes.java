package com.example.globe.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

public final class LatitudeBiomes {
    private LatitudeBiomes() {
    }

    private static RegistryEntry<Biome> pickTropicalGradientNoSwamp(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        long seed = WORLD_SEED;

        double bandStart = LatitudeMath.EQUATOR_MAX_FRAC;
        double bandEnd = LatitudeMath.SUBTROPICAL_MAX_FRAC;
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

        return switch (step) {
            case 1 -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT);
            case 2 -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 102, 0x7A22,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            case 3 -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 103, 0x7A33,
                    LAT_TROPICS_PRIMARY, LAT_TROPICS_SECONDARY, LAT_TROPICS_ACCENT);
            default -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 100, 0x7A00,
                    LAT_ARID_PRIMARY, LAT_ARID_SECONDARY, LAT_ARID_ACCENT);
        };
    }

    private static RegistryEntry<Biome> pickTropicalGradientNoSwamp(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        long seed = WORLD_SEED;

        double bandStart = LatitudeMath.EQUATOR_MAX_FRAC;
        double bandEnd = LatitudeMath.SUBTROPICAL_MAX_FRAC;
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

        return switch (step) {
            case 1 -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT);
            case 2 -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 102, 0x7A22,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            case 3 -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 103, 0x7A33,
                    LAT_TROPICS_PRIMARY, LAT_TROPICS_SECONDARY, LAT_TROPICS_ACCENT);
            default -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, 100, 0x7A00,
                    LAT_ARID_PRIMARY, LAT_ARID_SECONDARY, LAT_ARID_ACCENT);
        };
    }

    private static final int BAND_EQUATOR = 0;
    private static final int BAND_TROPICAL = 1;
    private static final int BAND_TEMPERATE = 2;
    private static final int BAND_SUBPOLAR = 3;
    private static final int BAND_POLAR = 4;

    private static int bandIndexForZone(LatitudeMath.LatitudeZone zone) {
        return switch (zone) {
            case EQUATOR -> BAND_EQUATOR;
            case TROPICAL, SUBTROPICAL -> BAND_TROPICAL;
            case TEMPERATE -> BAND_TEMPERATE;
            case SUBPOLAR -> BAND_SUBPOLAR;
            case POLAR -> BAND_POLAR;
        };
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

    private static RegistryEntry<Biome> applyLandOverrides(Registry<Biome> biomes, RegistryEntry<Biome> pick, int blockX, int blockZ, int bandIndex) {
        if (bandIndex == BAND_EQUATOR || bandIndex == BAND_TEMPERATE) {
            if (isBiomeId(pick, "minecraft:plains") && rollChance(blockX, blockZ, 0x7F4A7C15, 60L)) {
                try {
                    pick = biome(biomes, "minecraft:sunflower_plains");
                } catch (Throwable ignored) {
                    // Keep original pick.
                }
            }
        }

        if (bandIndex == BAND_TEMPERATE) {
            if (isBiomeId(pick, "minecraft:dark_forest") && rollChance(blockX, blockZ, 0x51ED270B, 12000L)) {
                try {
                    pick = biome(biomes, "minecraft:pale_garden");
                } catch (Throwable ignored) {
                    // Keep original pick.
                }
            }

            if ((isBiomeId(pick, "minecraft:meadow") || isBiomeId(pick, "minecraft:windswept_hills"))
                    && rollChance(blockX, blockZ, 0x31415926, 120L)) {
                try {
                    pick = biome(biomes, "minecraft:stony_peaks");
                } catch (Throwable ignored) {
                    // Keep original pick.
                }
            }
        }

        return pick;
    }

    private static RegistryEntry<Biome> applyLandOverrides(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> pick, int blockX, int blockZ, int bandIndex) {
        if (bandIndex == BAND_EQUATOR || bandIndex == BAND_TEMPERATE) {
            if (isBiomeId(pick, "minecraft:plains") && rollChance(blockX, blockZ, 0x7F4A7C15, 60L)) {
                RegistryEntry<Biome> entry = entryById(biomes, "minecraft:sunflower_plains");
                if (entry != null) {
                    pick = entry;
                }
            }
        }

        if (bandIndex == BAND_TEMPERATE) {
            if (isBiomeId(pick, "minecraft:dark_forest") && rollChance(blockX, blockZ, 0x51ED270B, 12000L)) {
                RegistryEntry<Biome> entry = entryById(biomes, "minecraft:pale_garden");
                if (entry != null) {
                    pick = entry;
                }
            }

            if ((isBiomeId(pick, "minecraft:meadow") || isBiomeId(pick, "minecraft:windswept_hills"))
                    && rollChance(blockX, blockZ, 0x31415926, 120L)) {
                RegistryEntry<Biome> entry = entryById(biomes, "minecraft:stony_peaks");
                if (entry != null) {
                    pick = entry;
                }
            }
        }

        return pick;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("LatitudeBiomes");
    private static final boolean DEBUG_BIOMES = Boolean.getBoolean("latitude.debugBiomes")
            || Boolean.getBoolean("latitude.debugBiomePick");
    private static final boolean DEBUG_BLEND = Boolean.getBoolean("latitude.debugBlend");
    private static final boolean DEBUG_LEAK = Boolean.getBoolean("latitude.debugLeak");
    private static final int DEBUG_LIMIT = Integer.getInteger("latitude.debugBiomes.limit", 200);
    private static volatile long WORLD_SEED = 0L;
    public static volatile int ACTIVE_RADIUS_BLOCKS = 0;
    private static final AtomicInteger DEBUG_COUNT = new AtomicInteger();
    private static final AtomicInteger BLEND_DEBUG_COUNT = new AtomicInteger();
    private static final AtomicInteger LEAK_LOG_COUNT = new AtomicInteger();
    private static final AtomicBoolean RADIUS_MISMATCH_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean SUBPOLAR_JUNGLE_TRACE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean SURFACE_Y_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean PREVIEW_TERRAIN_SKIP_LOGGED = new AtomicBoolean(false);
    // Surface classification is column-stable. Never use caller Y for these.
    private static final int SURFACE_CLASSIFY_Y = 96; // constant sampling layer
    private static final int LEAK_LOG_LIMIT = Integer.getInteger("latitude.leakLogLimit", 200);
    private static final ThreadLocal<String> LAST_SELECTION_PATH = new ThreadLocal<>();
    private static final String PATH_TAG_PICK = "tag-based pick";
    private static final String PATH_FALLBACK_PICK = "explicit fallback list pick";
    private static final String PATH_RETURN_BASE = "return base";
    private static boolean TAG_LOGGED = false;

    public static void setWorldSeed(long seed) {
        WORLD_SEED = seed;
    }

    public static void setRadius(int radius) {
        ACTIVE_RADIUS_BLOCKS = radius;
    }

    public static void setActiveRadiusBlocks(int radiusBlocks) {
        ACTIVE_RADIUS_BLOCKS = Math.max(0, radiusBlocks);
    }

    public static int getActiveRadiusBlocks() {
        return ACTIVE_RADIUS_BLOCKS;
    }

    public static double uplandRampForY(int blockY) {
        return uplandT(SURFACE_CLASSIFY_Y);
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
        String candidate = previewHeightHigh ? "minecraft:savanna_plateau" : "minecraft:savanna";
        String selected = savannaGateBiomeId(preview.robustDelta, previewHeightHigh);
        String reason = savannaGateReason(preview.robustDelta, previewHeightHigh);
        return String.format(java.util.Locale.ROOT,
                "mtnLike(noise)=%s previewHeight=%d sea=%d previewRobust=%d mtnLike(final)=%s upland=%s candidate=%s selected=%s reason=%s",
                noiseMountain, preview.centerHeight, seaLevel, preview.robustDelta, mountainLike,
                previewHeightHigh, candidate, selected, reason);
    }

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

    private static boolean shouldSkipPreviewTerrain(String callerContext) {
        if (callerContext == null) {
            return false;
        }
        String normalized = callerContext.trim().toUpperCase(java.util.Locale.ROOT);
        if ("BIOME_PNG".equals(normalized)) {
            return Boolean.parseBoolean(System.getProperty("latitude.skipPreviewHeightForBiomePng", "true"));
        }
        if ("MIXIN".equals(normalized) || "CAVE_CLAMP".equals(normalized)) {
            return Boolean.parseBoolean(System.getProperty("latitude.skipPreviewHeightForWorldgen", "true"));
        }
        return false;
    }

    private static PreviewTerrain syntheticPreviewTerrain(boolean mountainNoiseLike, NoiseChunkGenerator generator) {
        int seaLevel = previewSeaLevel(generator);
        int centerHeight = mountainNoiseLike ? (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS + 1) : (seaLevel - 1);
        int robustDelta = mountainNoiseLike
                ? (WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST)
                : Math.max(0, WINDSWEPT_RUGGED_THRESH - 1);
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

    private static String savannaCandidateBiomeId(boolean upland) {
        return upland ? "minecraft:savanna_plateau" : "minecraft:savanna";
    }

    private static String savannaGateBiomeId(int robustDelta, boolean upland) {
        if (robustDelta >= WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST) {
            return "minecraft:windswept_savanna";
        }
        return savannaCandidateBiomeId(upland);
    }

    private static String savannaGateReason(int robustDelta, boolean upland) {
        if (robustDelta >= WINDSWEPT_RUGGED_THRESH + WINDSWEPT_RUGGED_HYST) {
            return "windswept:robust";
        }
        if (robustDelta < WINDSWEPT_RUGGED_THRESH) {
            return upland ? "plateau:upland" : "savanna:flat/default";
        }
        return upland ? "deadband:plateau_upland" : "deadband:savanna_flat/default";
    }

    private static RegistryEntry<Biome> applySavannaWindsweptGate(Registry<Biome> biomes, RegistryEntry<Biome> out, int robustDelta, boolean upland) {
        if (!isSavannaFamily(out)) {
            return out;
        }
        return biome(biomes, savannaGateBiomeId(robustDelta, upland));
    }

    private static RegistryEntry<Biome> applySavannaWindsweptGate(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> out, int robustDelta, boolean upland) {
        if (!isSavannaFamily(out)) {
            return out;
        }
        RegistryEntry<Biome> selected = entryById(biomes, savannaGateBiomeId(robustDelta, upland));
        return selected != null ? selected : out;
    }

    private static boolean isSavannaFamily(RegistryEntry<Biome> entry) {
        return isBiomeId(entry, "minecraft:savanna")
                || isBiomeId(entry, "minecraft:savanna_plateau")
                || isBiomeId(entry, "minecraft:windswept_savanna");
    }

    private record PreviewTerrain(int centerHeight, int robustDelta) {
    }

    private static final String MANGROVE_ID = "minecraft:mangrove_swamp";
    private static final String SWAMP_ID = "minecraft:swamp";
    private static final String BADLANDS_ID = "minecraft:badlands";
    private static final int MANGROVE_PATCH_CELL_BLOCKS = 1024;
    private static final int MANGROVE_PATCH_PERCENT = 20;
    private static final int MANGROVE_PATCH_SALT = 0x2F7A3B1C;
    private static final long MANGROVE_FALLBACK_SALT = 0x6D2B79F5L;
    private static final long SWAMP_FALLBACK_SALT = 0x7A1D9E0BL;

    // Wetland gating for swamp patches near water (Kakadu-style: patchy, tropical-biased)
    private static final long WETLAND_SALT = 0x6A6B_7765_746C_616EL; // "jkwetlan" -> just a stable salt
    private static final double WETLAND_FREQ = 1.0 / 1200.0; // low frequency => broad patches

    private static final int BADLANDS_PATCH_SIZE_BLOCKS = 65536;
    private static final double BADLANDS_PATCH_CHANCE = 0.42;
    private static final long BADLANDS_PATCH_SALT = 0xBADD1A2DL;

    private static final int SWAMP_PATCH_SIZE_BLOCKS = 1024;
    private static final double SWAMP_PATCH_CHANCE = 0.55;
    private static final long SWAMP_PATCH_SALT = 0x53A95A4DL;

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
    private static final int BLEND_TRANSITION_WIDTH_BLOCKS = 768;
    private static final int BLEND_DITHER_SCALE_BLOCKS = 512;
    private static final int BLEND_NOISE_PATCH_CHUNKS = 6;
    private static final int WARP_NOISE_PATCH_CHUNKS = 8;
    private static final double BAND_JITTER_FRAC = 0.02;
    private static final int BAND_JITTER_MIN_BLOCKS = 80;
    private static final int BAND_JITTER_MAX_BLOCKS = 450;
    private static final double BAND_JITTER_WAVELENGTH_FRAC = 0.35;
    private static final int BAND_JITTER_WAVELENGTH_MIN_BLOCKS = 1800;
    private static final int BAND_JITTER_WAVELENGTH_MAX_BLOCKS = 12000;
    private static final int DITHER_SCALE_BLOCKS = 144;
    private static final int WARP_AMPLITUDE_BLOCKS = 256;
    private static final int WARP_SCALE_BLOCKS = 4096;
    private static final long JITTER_NOISE_SALT = -6795153568590067944L;
    private static final long DITHER_NOISE_SALT = 1161981756646125696L;
    private static final long BLEND_NOISE_SALT = 0x53EED5EEDL;
    private static final long WARP_NOISE_SALT = 0x5A7A5EED0F00D123L;
    private static final long TROPICAL_DITHER_SALT = 0x5EEDBEEF5EEDBEEFL;
    private static final long SUBPOLAR_RAMP_SALT = 0x5EED5B09A5EEDL;
    private static final long SNOWY_RAMP_SALT = 0x5EEDB17A5EEDL;
    private static final double SNOWY_RAMP_START_DEG = 56.0;
    private static final double SNOWY_RAMP_FULL_DEG = 66.0;
    private static final double GROVE_MIN_DEG = 66.0;

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
        if (baseStep >= 3 || DISABLE_GRID_DITHER) {
            return baseStep;
        }
        double dither = ValueNoise2D.sampleBlocks(seed ^ TROPICAL_DITHER_SALT, blockX, blockZ, DITHER_SCALE_BLOCKS);
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
        assertSurfaceY(blockY);
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

        int lat = Math.abs(blockZ);
        double tBase = (double) lat / (double) effectiveRadius;
        double t = applyBoundaryJitter(blockX, blockZ, effectiveRadius, tBase);
        LatitudeMath.LatitudeZone zone = zoneForAbsLatFraction(t);
        int bandIndex = bandIndexForZone(zone);

        if (isBeachLike(base)) {
            RegistryEntry<Biome> out = pickBeachForBand(biomeRegistry, base, blockX, blockZ, bandIndex);
            debugPick(blockX, blockZ, effectiveRadius, t, zone, base, out, true, false, null);
            return out;
        }

        if (base.isIn(BiomeTags.IS_RIVER)) {
            if (bandIndex >= 3) {
                try {
                    RegistryEntry<Biome> out = biome(biomeRegistry, "minecraft:frozen_river");
                    debugPick(blockX, blockZ, effectiveRadius, t, zone, base, out, false, false, null);
                    return out;
                } catch (Throwable ignored) {
                    debugPick(blockX, blockZ, effectiveRadius, t, zone, base, base, false, false, null);
                    return base;
                }
            } else {
                try {
                    RegistryEntry<Biome> out = biome(biomeRegistry, "minecraft:river");
                    debugPick(blockX, blockZ, effectiveRadius, t, zone, base, out, false, false, null);
                    return out;
                } catch (Throwable ignored) {
                    debugPick(blockX, blockZ, effectiveRadius, t, zone, base, base, false, false, null);
                    return base;
                }
            }
        }

        if (base.isIn(BiomeTags.IS_OCEAN)) {
            RegistryEntry<Biome> oceanPick = oceanByLatitudeBandOrBase(biomeRegistry, base, blockX, blockZ, bandIndex);
            RegistryEntry<Biome> out = mushroomIslandOverride(biomeRegistry, oceanPick, blockX, blockZ);
            debugPick(blockX, blockZ, effectiveRadius, t, zone, base, out, false, false, null);
            return out;
        }

        int landBandIndex = latitudeBandIndexWithBlend(blockX, blockZ, effectiveRadius, zone, t);
        boolean mountainNoiseLike = landBandIndex == BAND_TEMPERATE && isMountainLike(sampler, blockX, blockZ);
        PreviewTerrain preview;
        if (shouldSkipPreviewTerrain(callerContext)) {
            preview = syntheticPreviewTerrain(mountainNoiseLike, generator);
            if (PREVIEW_TERRAIN_SKIP_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("[Latitude] skipping previewHeight() for callerContext={} (atlas fast-path enabled)", callerContext);
            }
        } else {
            preview = previewTerrain(generator, noiseConfig, heightView, blockX, blockZ);
        }
        int seaLevel = previewSeaLevel(generator);
        boolean previewHeightHigh = preview.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS);
        boolean previewRuggedHigh = preview.robustDelta >= WINDSWEPT_RUGGED_THRESH;
        boolean mountainLike = mountainNoiseLike && (previewHeightHigh || previewRuggedHigh);
        boolean forcedBadlands = false;
        RegistryEntry<Biome> chosen = null;
        if (landBandIndex == BAND_TROPICAL && isAridTropicalStep(blockX, blockZ, t) && badlandsPatchHere(WORLD_SEED, blockX, blockZ)) {
            try {
                chosen = biome(biomeRegistry, BADLANDS_ID);
                forcedBadlands = true;
            } catch (Throwable ignored) {
                // fall through to normal selection
            }
        }
        if (forcedBadlands) {
            debugPick(blockX, blockZ, effectiveRadius, t, zone, base, chosen, false, false, null);
            return chosen;
        }
        if (chosen == null && (landBandIndex == BAND_EQUATOR || landBandIndex == BAND_TROPICAL) && sampler != null) {
            int noiseX = blockX >> 2;
            int noiseZ = blockZ >> 2;
            MultiNoiseUtil.NoiseValuePoint p = sampler.sample(noiseX, SURFACE_CLASSIFY_Y >> 2, noiseZ);
            double cont = MultiNoiseUtil.toFloat(p.continentalnessNoise());
            double erosion = MultiNoiseUtil.toFloat(p.erosionNoise());
            double weird = MultiNoiseUtil.toFloat(p.weirdnessNoise());
            if (!isAridTropicalStepSymmetric(blockX, blockZ, t)
                    && swampPatchHere(WORLD_SEED, blockX, blockZ)
                    && swampOkInPatch(cont, erosion, weird)
            && wetlandNoiseSymmetric(WORLD_SEED, blockX, blockZ) > wetlandThresholdForBand(bandIndex, t)) {
                try {
                    chosen = biome(biomeRegistry, SWAMP_ID);
                } catch (Throwable ignored) {
                    // keep null to fall through
                }
            }
        }
        if (chosen == null) {
            chosen = switch (landBandIndex) {
                case BAND_EQUATOR -> pickFromWeightedTags(biomeRegistry, base, blockX, blockZ, BAND_EQUATOR, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
                case BAND_TROPICAL -> pickTropicalGradient(biomeRegistry, base, blockX, blockZ, t);
                case BAND_TEMPERATE -> pickTemperateLand(biomeRegistry, blockX, blockZ, blockY,
                        () -> pickFromWeightedTags(biomeRegistry, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT),
                        mountainLike);
                case BAND_SUBPOLAR -> pickSubpolarWithRamp(biomeRegistry, base, blockX, blockZ, t, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
                default -> pickFromWeightedTags(biomeRegistry, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
            };
        }
        String mangroveDecision = null;
        RegistryEntry<Biome> sanitized = chosen;
        RegistryEntry<Biome> safe = chosen;
        RegistryEntry<Biome> out = chosen;
        if (!forcedBadlands) {
            if (shouldTryMangroveOverride(chosen, landBandIndex)) {
                MangroveDecision decision = evaluateMangrove(blockX, blockZ, sampler);
                mangroveDecision = decision.logLabel();
                if (decision.allow()) {
                    try {
                        chosen = biome(biomeRegistry, MANGROVE_ID);
                    } catch (Throwable ignored) {
                        // keep current choice
                    }
                }
            } else if (isMangroveCandidate(chosen)) {
                MangroveDecision decision = evaluateMangrove(blockX, blockZ, sampler);
                mangroveDecision = decision.logLabel();
                if (!decision.allow()) {
                    chosen = pickMangroveFallback(biomeRegistry, base, blockX, blockZ, t, landBandIndex);
                }
            }
            if (isSwampCandidate(chosen)) {
                SwampDecision decision = evaluateSwamp(blockX, blockZ, sampler);
                if (!decision.allow()) {
                    chosen = pickSwampFallback(biomeRegistry, base, blockX, blockZ, t, landBandIndex);
                }
            }
            if (mountainLike) {
                chosen = pickFromTagNoiseOrBase(biomeRegistry, LAT_TEMPERATE_MOUNTAIN, base, blockX, blockZ, landBandIndex);
                if (isBiomeId(chosen, "minecraft:cherry_grove") && !rollChance(blockX, blockZ, 0xC7E22E55, 6L)) {
                    chosen = pickFrom(biomeRegistry, blockX, blockZ, landBandIndex,
                            "minecraft:meadow",
                            "minecraft:grove",
                            "minecraft:windswept_hills",
                            "minecraft:stony_peaks");
                }
            }
            sanitized = sanitizeLandBiome(biomeRegistry, chosen, landBandIndex);
            safe = repickIfSurfaceCave(biomeRegistry, base, sanitized, blockX, blockZ, t, landBandIndex);
            out = applyLandOverrides(biomeRegistry, safe, blockX, blockZ, landBandIndex);
            out = applySavannaWindsweptGate(biomeRegistry, out, preview.robustDelta, previewHeightHigh);
        }
        if (landBandIndex == BAND_EQUATOR || landBandIndex == BAND_TROPICAL) {
            if (isColdBiome(out)) {
                out = pickWarmFallback(biomeRegistry, landBandIndex);
            }
        }
        out = enforceSnowyLatitudeRamp(biomeRegistry, out, base, blockX, blockZ, effectiveRadius, landBandIndex);
        out = clampWarmInColdZone(biomeRegistry, base, out, zone, blockX, blockZ);
        out = applySubpolarSwampGuard(biomeRegistry, base, out, zone);
        if (landBandIndex >= BAND_SUBPOLAR && isJungleFamily(out)) {
            out = pickColdFallback(biomeRegistry, base, blockX, blockZ, landBandIndex);
        }
        out = enforceLandBandPool(biomeRegistry, out, blockX, blockZ, t, landBandIndex);
        traceSubpolarJunglePick(blockX, blockZ, effectiveRadius, landBandIndex, base, out);
        debugPick(blockX, blockZ, effectiveRadius, t, zone, base, out, false, out != sanitized, mangroveDecision);
        return out;
    }

    public static RegistryEntry<Biome> pick(Collection<RegistryEntry<Biome>> biomePool, RegistryEntry<Biome> base, int blockX, int blockZ, int blockY, int borderRadiusBlocks,
                                            MultiNoiseUtil.MultiNoiseSampler sampler, String callerContext) {
        return pick(biomePool, base, blockX, blockZ, blockY, borderRadiusBlocks, sampler, callerContext, null, null, null);
    }

    public static RegistryEntry<Biome> pick(Collection<RegistryEntry<Biome>> biomePool, RegistryEntry<Biome> base, int blockX, int blockZ, int blockY, int borderRadiusBlocks,
                                            MultiNoiseUtil.MultiNoiseSampler sampler, String callerContext,
                                            NoiseChunkGenerator generator, NoiseConfig noiseConfig, HeightLimitView heightView) {
        assertSurfaceY(blockY);
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
        LatitudeMath.LatitudeZone zone = zoneForAbsLatFraction(t);
        int bandIndex = bandIndexForZone(zone);

        if (isBeachLike(base)) {
            RegistryEntry<Biome> out = pickBeachForBand(biomePool, base, blockX, blockZ, bandIndex);
            debugPick(blockX, blockZ, effectiveRadius, t, zone, base, out, true, false, null);
            return out;
        }

        if (base.isIn(BiomeTags.IS_RIVER)) {
            if (bandIndex >= 3) {
                RegistryEntry<Biome> frozen = entryById(biomePool, "minecraft:frozen_river");
                RegistryEntry<Biome> out = frozen != null ? frozen : base;
                debugPick(blockX, blockZ, effectiveRadius, t, zone, base, out, false, false, null);
                return out;
            }
            RegistryEntry<Biome> river = entryById(biomePool, "minecraft:river");
            RegistryEntry<Biome> out = river != null ? river : base;
            debugPick(blockX, blockZ, effectiveRadius, t, zone, base, out, false, false, null);
            return out;
        }

        if (base.isIn(BiomeTags.IS_OCEAN)) {
            RegistryEntry<Biome> oceanPick = oceanByLatitudeBandOrBase(biomePool, base, blockX, blockZ, bandIndex);
            RegistryEntry<Biome> out = mushroomIslandOverride(biomePool, oceanPick, blockX, blockZ);
            debugPick(blockX, blockZ, effectiveRadius, t, zone, base, out, false, false, null);
            return out;
        }

        int landBandIndex = latitudeBandIndexWithBlend(blockX, blockZ, effectiveRadius, zone, t);
        boolean mountainNoiseLike = landBandIndex == BAND_TEMPERATE && isMountainLike(sampler, blockX, blockZ);
        PreviewTerrain preview;
        if (shouldSkipPreviewTerrain(callerContext)) {
            preview = syntheticPreviewTerrain(mountainNoiseLike, generator);
            if (PREVIEW_TERRAIN_SKIP_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("[Latitude] skipping previewHeight() for callerContext={} (atlas fast-path enabled)", callerContext);
            }
        } else {
            preview = previewTerrain(generator, noiseConfig, heightView, blockX, blockZ);
        }
        int seaLevel = previewSeaLevel(generator);
        boolean previewHeightHigh = preview.centerHeight >= (seaLevel + PREVIEW_HEIGHT_MARGIN_BLOCKS);
        boolean previewRuggedHigh = preview.robustDelta >= WINDSWEPT_RUGGED_THRESH;
        boolean mountainLike = mountainNoiseLike && (previewHeightHigh || previewRuggedHigh);
        boolean forcedBadlands = false;
        RegistryEntry<Biome> chosen = null;
        if (landBandIndex == BAND_TROPICAL && isAridTropicalStep(blockX, blockZ, t) && badlandsPatchHere(WORLD_SEED, blockX, blockZ)) {
            chosen = entryById(biomePool, BADLANDS_ID);
            forcedBadlands = chosen != null;
        }
        if (forcedBadlands) {
            debugPick(blockX, blockZ, effectiveRadius, t, zone, base, chosen, false, false, null);
            return chosen;
        }
        if (chosen == null && (landBandIndex == BAND_EQUATOR || landBandIndex == BAND_TROPICAL) && sampler != null) {
            int noiseX = blockX >> 2;
            int noiseZ = blockZ >> 2;
            MultiNoiseUtil.NoiseValuePoint p = sampler.sample(noiseX, SURFACE_CLASSIFY_Y >> 2, noiseZ);
            double cont = MultiNoiseUtil.toFloat(p.continentalnessNoise());
            double erosion = MultiNoiseUtil.toFloat(p.erosionNoise());
            double weird = MultiNoiseUtil.toFloat(p.weirdnessNoise());
            if (!isAridTropicalStepSymmetric(blockX, blockZ, t)
                    && swampPatchHere(WORLD_SEED, blockX, blockZ)
                    && swampOkInPatch(cont, erosion, weird)) {
                chosen = entryById(biomePool, SWAMP_ID);
            }
        }
        if (chosen == null) {
            chosen = switch (landBandIndex) {
                case BAND_EQUATOR -> pickFromWeightedTags(biomePool, base, blockX, blockZ, BAND_EQUATOR, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
                case BAND_TROPICAL -> pickTropicalGradient(biomePool, base, blockX, blockZ, t);
                case BAND_TEMPERATE -> pickTemperateLand(biomePool, blockX, blockZ, blockY,
                        () -> pickFromWeightedTags(biomePool, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT),
                        mountainLike);
                case BAND_SUBPOLAR -> pickSubpolarWithRamp(biomePool, base, blockX, blockZ, t, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
                default -> pickFromWeightedTags(biomePool, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
            };
        }
        String mangroveDecision = null;
        RegistryEntry<Biome> sanitized = chosen;
        RegistryEntry<Biome> safe = chosen;
        RegistryEntry<Biome> out = chosen;
        if (!forcedBadlands) {
            if (shouldTryMangroveOverride(chosen, landBandIndex)) {
                MangroveDecision decision = evaluateMangrove(blockX, blockZ, sampler);
                mangroveDecision = decision.logLabel();
                if (decision.allow()) {
                    RegistryEntry<Biome> mangrove = entryById(biomePool, MANGROVE_ID);
                    if (mangrove != null) {
                        chosen = mangrove;
                    }
                }
            } else if (isMangroveCandidate(chosen)) {
                MangroveDecision decision = evaluateMangrove(blockX, blockZ, sampler);
                mangroveDecision = decision.logLabel();
                if (!decision.allow()) {
                    chosen = pickMangroveFallback(biomePool, base, blockX, blockZ, t, landBandIndex);
                }
            }
            if (isSwampCandidate(chosen)) {
                SwampDecision decision = evaluateSwamp(blockX, blockZ, sampler);
                if (!decision.allow()) {
                    chosen = pickSwampFallback(biomePool, base, blockX, blockZ, t, landBandIndex);
                }
            }
            if (mountainLike) {
                chosen = pickFromTagNoiseOrBase(biomePool, LAT_TEMPERATE_MOUNTAIN, base, blockX, blockZ, landBandIndex);
                if (isBiomeId(chosen, "minecraft:cherry_grove") && !rollChance(blockX, blockZ, 0xC7E22E55, 6L)) {
                    chosen = pickFromFallbacks(biomePool, base,
                            "minecraft:meadow",
                            "minecraft:grove",
                            "minecraft:windswept_hills",
                            "minecraft:stony_peaks");
                }
            }
            sanitized = sanitizeLandBiome(biomePool, chosen, landBandIndex);
            safe = repickIfSurfaceCave(biomePool, base, sanitized, blockX, blockZ, t, landBandIndex);
            out = applyLandOverrides(biomePool, safe, blockX, blockZ, landBandIndex);
            out = applySavannaWindsweptGate(biomePool, out, preview.robustDelta, previewHeightHigh);
        }
        if (landBandIndex == BAND_EQUATOR || landBandIndex == BAND_TROPICAL) {
            if (isColdBiome(out)) {
                out = pickWarmFallback(biomePool, landBandIndex);
            }
        }
        out = enforceSnowyLatitudeRamp(biomePool, out, base, blockX, blockZ, effectiveRadius, landBandIndex);
        out = clampWarmInColdZone(biomePool, base, out, zone, blockX, blockZ);
        out = applySubpolarSwampGuard(biomePool, base, out, zone);
        if (landBandIndex >= BAND_SUBPOLAR && isJungleFamily(out)) {
            out = pickColdFallback(biomePool, base, blockX, blockZ, landBandIndex);
        }
        out = enforceLandBandPool(biomePool, out, blockX, blockZ, t, landBandIndex);
        traceSubpolarJunglePick(blockX, blockZ, effectiveRadius, landBandIndex, base, out);
        debugPick(blockX, blockZ, effectiveRadius, t, zone, base, out, false, out != sanitized, mangroveDecision);
        return out;
    }

    private static RegistryEntry<Biome> pickTropicalGradient(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        long seed = WORLD_SEED;

        // Tropical band is [EQUATOR_MAX_FRAC..SUBTROPICAL_MAX_FRAC]. Wet near equator, arid near the edge.
        double bandStart = LatitudeMath.EQUATOR_MAX_FRAC;
        double bandEnd = LatitudeMath.SUBTROPICAL_MAX_FRAC;
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

        return switch (step) {
            case 1 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT);
            case 2 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 102, 0x7A22,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            case 3 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 103, 0x7A33,
                    LAT_TROPICS_PRIMARY, LAT_TROPICS_SECONDARY, LAT_TROPICS_ACCENT);
            default -> pickFromWeightedTags(biomes, base, blockX, blockZ, 100, 0x7A00,
                    LAT_ARID_PRIMARY, LAT_ARID_SECONDARY, LAT_ARID_ACCENT);
        };
    }

    private static boolean isAridTropicalStep(int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        long seed = WORLD_SEED;

        double bandStart = LatitudeMath.EQUATOR_MAX_FRAC;
        double bandEnd = LatitudeMath.SUBTROPICAL_MAX_FRAC;
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

        return step == 0;
    }

    private static boolean isAridTropicalStepSymmetric(int blockX, int blockZ, double t) {
        int absZ = Math.abs(blockZ);
        return isAridTropicalStep(blockX, absZ, t);
    }

    private static RegistryEntry<Biome> pickTropicalGradient(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        long seed = WORLD_SEED;

        // Tropical band is [EQUATOR_MAX_FRAC..SUBTROPICAL_MAX_FRAC]. Wet near equator, arid near the edge.
        double bandStart = LatitudeMath.EQUATOR_MAX_FRAC;
        double bandEnd = LatitudeMath.SUBTROPICAL_MAX_FRAC;
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

        return switch (step) {
            case 1 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT);
            case 2 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 102, 0x7A22,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            case 3 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 103, 0x7A33,
                    LAT_TROPICS_PRIMARY, LAT_TROPICS_SECONDARY, LAT_TROPICS_ACCENT);
            default -> pickFromWeightedTags(biomes, base, blockX, blockZ, 100, 0x7A00,
                    LAT_ARID_PRIMARY, LAT_ARID_SECONDARY, LAT_ARID_ACCENT);
        };
    }

    private static RegistryEntry<Biome> oceanByLatitudeBandOrBase(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, int bandIndex) {
        if (bandIndex == 0) {
            return pickFromTagNoiseOrFallback(biomes, LAT_OCEAN_TROPICAL, blockX, blockZ, 20,
                    "minecraft:warm_ocean",
                    "minecraft:lukewarm_ocean",
                    "minecraft:deep_lukewarm_ocean");
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
            return pickFromTagNoiseOrFallback(biomes, base, LAT_OCEAN_TROPICAL, blockX, blockZ, 20,
                    "minecraft:warm_ocean",
                    "minecraft:lukewarm_ocean",
                    "minecraft:deep_lukewarm_ocean");
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


    private static int latitudeBandIndexWithBlend(int blockX, int blockZ, int radius, LatitudeMath.LatitudeZone zone, double t) {
        if (radius <= 0) {
            return bandIndexForZone(zone);
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
        if (bandIndex <= BAND_EQUATOR) {
            lowerBandIndex = BAND_EQUATOR;
            upperBandIndex = BAND_TROPICAL;
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

        if (DEBUG_BLEND
                && (blockX & 15) == 0
                && (blockZ & 15) == 0
                && chosenBandIndex != bandIndex
                && BLEND_DEBUG_COUNT.incrementAndGet() <= DEBUG_LIMIT) {
            LOGGER.info("[LAT_BLEND] mode={} x={} z={} lat={} baseBand={} lower={} upper={} chosen={} boundary={} effectiveBoundary={} delta={} transitionWidth={} warpAmp={} warpPatchBlocks={} blendPatchBlocks={} t={} noise={}",
                    TRANSITION_MODE,
                    blockX,
                    blockZ,
                    absZ,
                    bandIndex,
                    lowerBandIndex,
                    upperBandIndex,
                    chosenBandIndex,
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

        return chosenBandIndex;
    }

    private static int crispBandIndex(double t) {
        if (t < LatitudeMath.EQUATOR_MAX_FRAC) return BAND_EQUATOR;
        if (t < LatitudeMath.SUBTROPICAL_MAX_FRAC) return BAND_TROPICAL;
        if (t < LatitudeMath.TEMPERATE_MAX_FRAC) return BAND_TEMPERATE;
        if (t < LatitudeMath.SUBPOLAR_MAX_FRAC) return BAND_SUBPOLAR;
        return BAND_POLAR;
    }

    private static LatitudeMath.LatitudeZone zoneForAbsLatFraction(double t) {
        double clamped = clamp(t, 0.0, 1.0);
        if (clamped < LatitudeMath.EQUATOR_MAX_FRAC) return LatitudeMath.LatitudeZone.EQUATOR;
        if (clamped < LatitudeMath.TROPICAL_MAX_FRAC) return LatitudeMath.LatitudeZone.TROPICAL;
        if (clamped < LatitudeMath.SUBTROPICAL_MAX_FRAC) return LatitudeMath.LatitudeZone.SUBTROPICAL;
        if (clamped < LatitudeMath.TEMPERATE_MAX_FRAC) return LatitudeMath.LatitudeZone.TEMPERATE;
        if (clamped < LatitudeMath.SUBPOLAR_MAX_FRAC) return LatitudeMath.LatitudeZone.SUBPOLAR;
        return LatitudeMath.LatitudeZone.POLAR;
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
            case 0 -> (int) Math.round(LatitudeMath.EQUATOR_MAX_FRAC * (double) radius);
            case 1 -> (int) Math.round(LatitudeMath.SUBTROPICAL_MAX_FRAC * (double) radius);
            case 2 -> (int) Math.round(LatitudeMath.TEMPERATE_MAX_FRAC * (double) radius);
            default -> (int) Math.round(LatitudeMath.SUBPOLAR_MAX_FRAC * (double) radius);
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

    private static TagKey<Biome> weightedTagForRoll(int roll, TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        if (roll < 70) return primary;
        if (roll < 95) return secondary;
        return accent;
    }

    private static TagKey<Biome> subpolarTagForRoll(int roll, boolean snowyPool, TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        if (roll >= 95) {
            return accent;
        }
        return snowyPool ? primary : secondary;
    }

    private static double subpolarSnowProbability(double absLatFraction) {
        double subpolarStart = LatitudeMath.TEMPERATE_MAX_FRAC;
        double polarStart = LatitudeMath.SUBPOLAR_MAX_FRAC;
        double t = 0.0;
        if (polarStart > subpolarStart) {
            t = (absLatFraction - subpolarStart) / (polarStart - subpolarStart);
        }
        t = LatitudeMath.clamp(t, 0.0, 1.0);

        double tw = LatitudeMath.clamp((t - 0.25) / 0.50, 0.0, 1.0);
        double pSnow = tw * tw * (3.0 - 2.0 * tw);

        if (t > 0.90) pSnow = 1.0;
        if (t < 0.10) pSnow = 0.0;

        return pSnow;
    }

    private static boolean useSubpolarSnowyPool(double absLatFraction, int blockX, int blockZ) {
        double pSnow = subpolarSnowProbability(absLatFraction);
        double r;
        if (DISABLE_GRID_DITHER) {
            r = ValueNoise2D.sampleBlocks(WORLD_SEED ^ SUBPOLAR_RAMP_SALT, blockX, blockZ, Math.max(16, VARIANT_CELL_SIZE_BLOCKS));
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
        return pickFromTagNoiseOrBase(biomes, tag, base, blockX, blockZ, bandIndex);
    }

    private static double scaledPatchBlocks(int basePatchChunks, double noiseScale) {
        double basePatchBlocks = basePatchChunks * 16.0;
        double scaled = basePatchBlocks * noiseScale;
        return Math.max(16.0, scaled);
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
        TagKey<Biome> tag = weightedTagForRoll(roll, primary, secondary, accent);
        return pickFromTagNoiseOrBase(biomes, tag, base, blockX, blockZ, bandIndex);
    }

    private static RegistryEntry<Biome> pickFromWeightedTagsNoMangrove(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                                       int bandIndex, int weightSalt,
                                                                       TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt + (int) MANGROVE_FALLBACK_SALT);
        TagKey<Biome> tag = weightedTagForRoll(roll, primary, secondary, accent);
        return pickFromTagNoiseOrBaseFiltered(biomes, tag, base, blockX, blockZ, bandIndex, MANGROVE_FALLBACK_SALT, true);
    }

    private static RegistryEntry<Biome> pickFromWeightedTagsNoSwamp(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                                    int bandIndex, int weightSalt,
                                                                    TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt + (int) SWAMP_FALLBACK_SALT);
        TagKey<Biome> tag = weightedTagForRoll(roll, primary, secondary, accent);
        return pickFromTagNoiseOrBaseFilteredSwamp(biomes, tag, base, blockX, blockZ, bandIndex, SWAMP_FALLBACK_SALT, true);
    }

    private static RegistryEntry<Biome> pickFromWeightedTags(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                             int bandIndex, int weightSalt,
                                                             TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt);
        TagKey<Biome> tag = weightedTagForRoll(roll, primary, secondary, accent);
        return pickFromTagNoiseOrBase(biomes, tag, base, blockX, blockZ, bandIndex);
    }

    private static RegistryEntry<Biome> pickFromWeightedTagsNoMangrove(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                                       int bandIndex, int weightSalt,
                                                                       TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt + (int) MANGROVE_FALLBACK_SALT);
        TagKey<Biome> tag = weightedTagForRoll(roll, primary, secondary, accent);
        return pickFromTagNoiseOrBaseFiltered(biomes, tag, base, blockX, blockZ, bandIndex, MANGROVE_FALLBACK_SALT, true);
    }

    private static RegistryEntry<Biome> pickFromWeightedTagsNoSwamp(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                                    int bandIndex, int weightSalt,
                                                                    TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt + (int) SWAMP_FALLBACK_SALT);
        TagKey<Biome> tag = weightedTagForRoll(roll, primary, secondary, accent);
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
        double ramp = uplandT(SURFACE_CLASSIFY_Y);
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
        double ramp = uplandT(SURFACE_CLASSIFY_Y);
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
        return entries.get(idx);
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
            case BAND_EQUATOR -> List.of(
                    LAT_EQUATOR_PRIMARY,
                    LAT_EQUATOR_SECONDARY,
                    LAT_EQUATOR_ACCENT);
            case BAND_TROPICAL -> List.of(
                    LAT_ARID_PRIMARY,
                    LAT_ARID_SECONDARY,
                    LAT_ARID_ACCENT,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY,
                    LAT_TRANS_ARID_TROPICS_1_SECONDARY,
                    LAT_TRANS_ARID_TROPICS_1_ACCENT,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY,
                    LAT_TRANS_ARID_TROPICS_2_SECONDARY,
                    LAT_TRANS_ARID_TROPICS_2_ACCENT,
                    LAT_TROPICS_PRIMARY,
                    LAT_TROPICS_SECONDARY,
                    LAT_TROPICS_ACCENT);
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
            case BAND_EQUATOR -> List.of(
                    SWAMP_ID,
                    MANGROVE_ID,
                    "minecraft:sunflower_plains");
            case BAND_TROPICAL -> List.of(
                    SWAMP_ID,
                    MANGROVE_ID);
            case BAND_TEMPERATE -> List.of(
                    "minecraft:sunflower_plains",
                    "minecraft:pale_garden",
                    "minecraft:stony_peaks");
            case BAND_POLAR -> List.of(
                    "minecraft:ice_spikes");
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
                                                            int bandIndex) {
        List<RegistryEntry<Biome>> allowedPool = allowedLandPool(biomes, bandIndex);
        if (allowedPool.isEmpty() || isInAllowedLandPool(allowedPool, candidate)) {
            return candidate;
        }
        maybeLogBandLeak(blockX, blockZ, t, bandIndex, candidate);
        return pickFromAllowedLandPool(allowedPool, blockX, blockZ, bandIndex);
    }

    private static RegistryEntry<Biome> enforceLandBandPool(Collection<RegistryEntry<Biome>> biomes,
                                                            RegistryEntry<Biome> candidate,
                                                            int blockX,
                                                            int blockZ,
                                                            double t,
                                                            int bandIndex) {
        List<RegistryEntry<Biome>> allowedPool = allowedLandPool(biomes, bandIndex);
        if (allowedPool.isEmpty() || isInAllowedLandPool(allowedPool, candidate)) {
            return candidate;
        }
        maybeLogBandLeak(blockX, blockZ, t, bandIndex, candidate);
        return pickFromAllowedLandPool(allowedPool, blockX, blockZ, bandIndex);
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

    private static String bandName(int bandIndex) {
        return switch (bandIndex) {
            case BAND_EQUATOR -> "EQUATOR";
            case BAND_TROPICAL -> "TROPICAL";
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
            r = ValueNoise2D.sampleBlocks(WORLD_SEED ^ SNOWY_RAMP_SALT, blockX, blockZ, Math.max(16, VARIANT_CELL_SIZE_BLOCKS));
        } else {
            int cellX = Math.floorDiv(blockX, VARIANT_CELL_SIZE_BLOCKS);
            int cellZ = Math.floorDiv(blockZ, VARIANT_CELL_SIZE_BLOCKS);
            r = cellHash01(WORLD_SEED ^ SNOWY_RAMP_SALT, cellX, cellZ);
        }
        if (r < alpha) {
            return pick;
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
            r = ValueNoise2D.sampleBlocks(WORLD_SEED ^ SNOWY_RAMP_SALT, blockX, blockZ, Math.max(16, VARIANT_CELL_SIZE_BLOCKS));
        } else {
            int cellX = Math.floorDiv(blockX, VARIANT_CELL_SIZE_BLOCKS);
            int cellZ = Math.floorDiv(blockZ, VARIANT_CELL_SIZE_BLOCKS);
            r = cellHash01(WORLD_SEED ^ SNOWY_RAMP_SALT, cellX, cellZ);
        }
        if (r < alpha) {
            return pick;
        }
        return pickNonSnowyFallback(biomes, base, bandIndex);
    }

    private static RegistryEntry<Biome> clampWarmInColdZone(Registry<Biome> biomes, RegistryEntry<Biome> base,
                                                            RegistryEntry<Biome> pick, LatitudeMath.LatitudeZone zone,
                                                            int blockX, int blockZ) {
        if (pick == null) {
            return base;
        }
        if (zone != LatitudeMath.LatitudeZone.SUBPOLAR && zone != LatitudeMath.LatitudeZone.POLAR) {
            return pick;
        }
        if (!isWarmBiome(pick)) {
            return pick;
        }
        return pickSnowyFallback(biomes, base);
    }

    private static RegistryEntry<Biome> clampWarmInColdZone(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base,
                                                            RegistryEntry<Biome> pick, LatitudeMath.LatitudeZone zone,
                                                            int blockX, int blockZ) {
        if (pick == null) {
            return base;
        }
        if (zone != LatitudeMath.LatitudeZone.SUBPOLAR && zone != LatitudeMath.LatitudeZone.POLAR) {
            return pick;
        }
        if (!isWarmBiome(pick)) {
            return pick;
        }
        return pickSnowyFallback(biomes, base);
    }

    private static RegistryEntry<Biome> applySubpolarSwampGuard(Registry<Biome> biomes, RegistryEntry<Biome> base,
                                                                 RegistryEntry<Biome> pick, LatitudeMath.LatitudeZone zone) {
        if (zone != LatitudeMath.LatitudeZone.SUBPOLAR || !isSubpolarDisallowedWetBiome(pick)) {
            return pick;
        }
        return pickSubpolarSwampFallback(biomes, base);
    }

    private static RegistryEntry<Biome> applySubpolarSwampGuard(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base,
                                                                 RegistryEntry<Biome> pick, LatitudeMath.LatitudeZone zone) {
        if (zone != LatitudeMath.LatitudeZone.SUBPOLAR || !isSubpolarDisallowedWetBiome(pick)) {
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
        String target = bandIndex == BAND_EQUATOR ? "minecraft:jungle" : "minecraft:savanna";
        try {
            return biome(biomes, target);
        } catch (Throwable ignored) {
            try {
                return biome(biomes, "minecraft:desert");
            } catch (Throwable ignoredAgain) {
                return biome(biomes, "minecraft:jungle");
            }
        }
    }

    private static RegistryEntry<Biome> pickWarmFallback(Collection<RegistryEntry<Biome>> biomes, int bandIndex) {
        String target = bandIndex == BAND_EQUATOR ? "minecraft:jungle" : "minecraft:savanna";
        RegistryEntry<Biome> entry = entryById(biomes, target);
        if (entry != null) {
            return entry;
        }
        entry = entryById(biomes, "minecraft:desert");
        if (entry != null) {
            return entry;
        }
        entry = entryById(biomes, "minecraft:jungle");
        return entry != null ? entry : biomes.stream().findFirst().orElse(null);
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

    private static void debugPick(int blockX, int blockZ, int borderRadiusBlocks, double t, LatitudeMath.LatitudeZone zone,
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
                zone,
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
        if (bandIndex > BAND_TROPICAL) {
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

    private static MangroveDecision evaluateMangrove(int blockX, int blockZ, MultiNoiseUtil.MultiNoiseSampler sampler) {
        if (sampler == null) {
            return new MangroveDecision(true, 0.0, 0.0, 0.0, true, true);
        }
        int noiseX = blockX >> 2;
        int noiseZ = blockZ >> 2;
        MultiNoiseUtil.NoiseValuePoint point = sampler.sample(noiseX, SURFACE_CLASSIFY_Y >> 2, noiseZ);
        double cont = MultiNoiseUtil.toFloat(point.continentalnessNoise());
        double erosion = MultiNoiseUtil.toFloat(point.erosionNoise());
        double weirdness = MultiNoiseUtil.toFloat(point.weirdnessNoise());
        boolean lowland = cont < 0.12;
        boolean notRugged = erosion > 0.0;
        boolean notPeaks = Math.abs(weirdness) < 0.15;
        boolean suitable = lowland && notRugged && notPeaks;
        boolean patch = allowMangrovePatch(blockX, blockZ);
        return new MangroveDecision(suitable && patch, cont, erosion, weirdness, suitable, patch);
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

    private static SwampDecision evaluateSwamp(int blockX, int blockZ, MultiNoiseUtil.MultiNoiseSampler sampler) {
        if (sampler == null) {
            return new SwampDecision(true, 0.0, 0.0, 0.0, true);
        }
        int noiseX = blockX >> 2;
        int noiseZ = blockZ >> 2;
        MultiNoiseUtil.NoiseValuePoint point = sampler.sample(noiseX, SURFACE_CLASSIFY_Y >> 2, noiseZ);
        double cont = MultiNoiseUtil.toFloat(point.continentalnessNoise());
        double erosion = MultiNoiseUtil.toFloat(point.erosionNoise());
        double weirdness = MultiNoiseUtil.toFloat(point.weirdnessNoise());
        boolean swampOk = swampOkStrict(cont, erosion, weirdness);
        return new SwampDecision(swampOk, cont, erosion, weirdness, swampOk);
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

    
    private static double toUnitDouble(long h) {
        // Map 53 random bits to [0,1)
        return ((h >>> 11) & ((1L << 53) - 1L)) * (1.0 / (1L << 53));
    }

    private static double wetlandNoiseSymmetric(long worldSeed, int blockX, int blockZ) {
        // Symmetric N/S: use abs(z) so wetland eligibility doesn't differ between hemispheres.
        int z = Math.abs(blockZ);

        // Low-frequency feel comes from pairing this with swampPatchHere() clustering.
        // This gate just decides "is this patch wet enough?" in a broad, deterministic way.
        long h = mix64(worldSeed ^ WETLAND_SALT ^ (long) blockX * 341873128712L ^ (long) z * 132897987541L);
        return toUnitDouble(h);
    }

    private static double wetlandThresholdForBand(int bandIndex, double t) {
        // Lower threshold => more wetlands. Tropical-biased, arid less frequent.
        // bandIndex: tropics=1, arid=2 (per your logs)
        if (bandIndex == 1) return 0.20; // tropics: often, but not continuous
        if (bandIndex == 2) return 0.72; // arid/subtropics: rarer pockets
        return 0.45;
    }

private static boolean swampPatchHere(long seed, int blockX, int blockZ) {
        double n = blobNoise01Blocks(seed ^ SWAMP_PATCH_SALT, blockX, blockZ, SWAMP_PATCH_SIZE_BLOCKS, SWAMP_PATCH_SALT);
        return n < SWAMP_PATCH_CHANCE;
    }

    private static RegistryEntry<Biome> pickMangroveFallback(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t, int bandIndex) {
        return switch (bandIndex) {
            case BAND_EQUATOR -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_EQUATOR, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_TROPICAL -> pickTropicalGradientNoMangrove(biomes, base, blockX, blockZ, t);
            case BAND_TEMPERATE -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickSubpolarWithRamp(biomes, base, blockX, blockZ, t, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
    }

    private static RegistryEntry<Biome> pickMangroveFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t, int bandIndex) {
        return switch (bandIndex) {
            case BAND_EQUATOR -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_EQUATOR, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_TROPICAL -> pickTropicalGradientNoMangrove(biomes, base, blockX, blockZ, t);
            case BAND_TEMPERATE -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickSubpolarWithRamp(biomes, base, blockX, blockZ, t, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
    }

    private static RegistryEntry<Biome> pickSwampFallback(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t, int bandIndex) {
        return switch (bandIndex) {
            case BAND_EQUATOR -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_EQUATOR, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_TROPICAL -> pickTropicalGradientNoSwamp(biomes, base, blockX, blockZ, t);
            case BAND_TEMPERATE -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickSubpolarWithRamp(biomes, base, blockX, blockZ, t, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
    }

    private static RegistryEntry<Biome> pickSwampFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t, int bandIndex) {
        return switch (bandIndex) {
            case BAND_EQUATOR -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_EQUATOR, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_TROPICAL -> pickTropicalGradientNoSwamp(biomes, base, blockX, blockZ, t);
            case BAND_TEMPERATE -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickSubpolarWithRamp(biomes, base, blockX, blockZ, t, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
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

        double bandStart = LatitudeMath.EQUATOR_MAX_FRAC;
        double bandEnd = LatitudeMath.SUBTROPICAL_MAX_FRAC;
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

        return switch (step) {
            case 1 -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT);
            case 2 -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 102, 0x7A22,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            case 3 -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 103, 0x7A33,
                    LAT_TROPICS_PRIMARY, LAT_TROPICS_SECONDARY, LAT_TROPICS_ACCENT);
            default -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 100, 0x7A00,
                    LAT_ARID_PRIMARY, LAT_ARID_SECONDARY, LAT_ARID_ACCENT);
        };
    }

    private static RegistryEntry<Biome> pickTropicalGradientNoMangrove(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        long seed = WORLD_SEED;

        double bandStart = LatitudeMath.EQUATOR_MAX_FRAC;
        double bandEnd = LatitudeMath.SUBTROPICAL_MAX_FRAC;
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

        return switch (step) {
            case 1 -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 101, 0x7A11,
                    LAT_TRANS_ARID_TROPICS_1_PRIMARY, LAT_TRANS_ARID_TROPICS_1_SECONDARY, LAT_TRANS_ARID_TROPICS_1_ACCENT);
            case 2 -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 102, 0x7A22,
                    LAT_TRANS_ARID_TROPICS_2_PRIMARY, LAT_TRANS_ARID_TROPICS_2_SECONDARY, LAT_TRANS_ARID_TROPICS_2_ACCENT);
            case 3 -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 103, 0x7A33,
                    LAT_TROPICS_PRIMARY, LAT_TROPICS_SECONDARY, LAT_TROPICS_ACCENT);
            default -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, 100, 0x7A00,
                    LAT_ARID_PRIMARY, LAT_ARID_SECONDARY, LAT_ARID_ACCENT);
        };
    }

    private record MangroveDecision(boolean allow, double continentalness, double erosion, double weirdness, boolean suitable, boolean patch) {
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

    private static RegistryEntry<Biome> sanitizeLandBiome(Registry<Biome> biomes, RegistryEntry<Biome> pick, int bandIndex) {
        if (bandIndex == BAND_EQUATOR) {
            if (isBiomeId(pick, "minecraft:plains")
                    || isBiomeId(pick, "minecraft:forest")
                    || isBiomeId(pick, "minecraft:birch_forest")
                    || isBiomeId(pick, "minecraft:old_growth_birch_forest")
                    || isBiomeId(pick, "minecraft:flower_forest")) {
                try {
                    return biome(biomes, "minecraft:jungle");
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
                try {
                    return biome(biomes, "minecraft:snowy_plains");
                } catch (Throwable ignored) {
                    return pick;
                }
            }
        }

        if (bandIndex >= BAND_POLAR) {
            String path = pick.getKey().map(key -> key.getValue().getPath()).orElse("");
            if (path.contains("forest") || path.contains("taiga") || isBiomeId(pick, "minecraft:grove") || isBiomeId(pick, "minecraft:cherry_grove")) {
                try {
                    return biome(biomes, "minecraft:ice_spikes");
                } catch (Throwable ignored) {
                    return pick;
                }
            }
        }

        return pick;
    }

    private static RegistryEntry<Biome> sanitizeLandBiome(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> pick, int bandIndex) {
        if (bandIndex == BAND_EQUATOR) {
            if (isBiomeId(pick, "minecraft:plains")
                    || isBiomeId(pick, "minecraft:forest")
                    || isBiomeId(pick, "minecraft:birch_forest")
                    || isBiomeId(pick, "minecraft:old_growth_birch_forest")
                    || isBiomeId(pick, "minecraft:flower_forest")) {
                RegistryEntry<Biome> entry = entryById(biomes, "minecraft:jungle");
                return entry != null ? entry : pick;
            }
        }

        if (bandIndex == 3) {
            if (isBiomeId(pick, "minecraft:plains")
                    || isBiomeId(pick, "minecraft:forest")
                    || isBiomeId(pick, "minecraft:birch_forest")
                    || isBiomeId(pick, "minecraft:old_growth_birch_forest")
                    || isBiomeId(pick, "minecraft:flower_forest")) {
                RegistryEntry<Biome> entry = entryById(biomes, "minecraft:snowy_plains");
                return entry != null ? entry : pick;
            }
        }

        if (bandIndex >= BAND_POLAR) {
            String path = pick.getKey().map(key -> key.getValue().getPath()).orElse("");
            if (path.contains("forest") || path.contains("taiga") || isBiomeId(pick, "minecraft:grove") || isBiomeId(pick, "minecraft:cherry_grove")) {
                RegistryEntry<Biome> entry = entryById(biomes, "minecraft:ice_spikes");
                return entry != null ? entry : pick;
            }
        }

        return pick;
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
        return entries.get(idx);
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
