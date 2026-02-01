package com.example.globe.world;

import com.example.globe.util.LatitudeMath;
import com.example.globe.util.ValueNoise2D;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        double dither = ValueNoise2D.sampleBlocks(seed ^ TROPICAL_DITHER_SALT, blockX, blockZ, DITHER_SCALE_BLOCKS);
        int step = baseStep;
        if (baseStep < 3 && dither < stepFrac) {
            step = baseStep + 1;
        }

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
        double dither = ValueNoise2D.sampleBlocks(seed ^ TROPICAL_DITHER_SALT, blockX, blockZ, DITHER_SCALE_BLOCKS);
        int step = baseStep;
        if (baseStep < 3 && dither < stepFrac) {
            step = baseStep + 1;
        }

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
    private static final boolean DEBUG_BIOMES = Boolean.getBoolean("latitude.debugBiomes");
    private static final boolean DEBUG_BLEND = Boolean.getBoolean("latitude.debugBlend");
    private static final int DEBUG_LIMIT = Integer.getInteger("latitude.debugBiomes.limit", 200);
    private static volatile long WORLD_SEED = 0L;
    private static final AtomicInteger DEBUG_COUNT = new AtomicInteger();
    private static final AtomicInteger BLEND_DEBUG_COUNT = new AtomicInteger();
    private static boolean TAG_LOGGED = false;

    public static void setWorldSeed(long seed) {
        WORLD_SEED = seed;
    }

    private static final String MANGROVE_ID = "minecraft:mangrove_swamp";
    private static final String SWAMP_ID = "minecraft:swamp";
    private static final int MANGROVE_PATCH_CELL_BLOCKS = 1024;
    private static final int MANGROVE_PATCH_PERCENT = 20;
    private static final int MANGROVE_PATCH_SALT = 0x2F7A3B1C;
    private static final long MANGROVE_FALLBACK_SALT = 0x6D2B79F5L;
    private static final long SWAMP_FALLBACK_SALT = 0x7A1D9E0BL;

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

    private static final int VARIANT_CELL_SIZE_BLOCKS = 1024;
    private static final int BLEND_TRANSITION_WIDTH_BLOCKS = 768;
    private static final int BLEND_DITHER_SCALE_BLOCKS = 512;
    private static final int BLEND_NOISE_PATCH_CHUNKS = 6;
    private static final int WARP_NOISE_PATCH_CHUNKS = 8;
    private static final int JITTER_AMPLITUDE_BLOCKS = 128;
    private static final int JITTER_SCALE_BLOCKS = 3072;
    private static final int DITHER_SCALE_BLOCKS = 144;
    private static final int WARP_AMPLITUDE_BLOCKS = 256;
    private static final int WARP_SCALE_BLOCKS = 4096;
    private static final long JITTER_NOISE_SALT = -6795153568590067944L;
    private static final long DITHER_NOISE_SALT = 1161981756646125696L;
    private static final long BLEND_NOISE_SALT = 0x53EED5EEDL;
    private static final long WARP_NOISE_SALT = 0x5A7A5EED0F00D123L;
    private static final long TROPICAL_DITHER_SALT = 0x5EEDBEEF5EEDBEEFL;

    private static final Set<String> SURFACE_CAVE_DENYLIST = Set.of(
            "minecraft:dripstone_caves",
            "minecraft:lush_caves",
            "minecraft:deep_dark"
    );

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

    public static RegistryEntry<Biome> pick(Registry<Biome> biomeRegistry, RegistryEntry<Biome> base, int blockX, int blockZ, int borderRadiusBlocks, MultiNoiseUtil.MultiNoiseSampler sampler) {
        if (borderRadiusBlocks <= 0) {
            return base;
        }

        int lat = Math.abs(blockZ);
        double t = (double) lat / (double) borderRadiusBlocks;
        LatitudeMath.LatitudeZone zone = LatitudeMath.zoneForRadius(borderRadiusBlocks, blockZ);
        int bandIndex = bandIndexForZone(zone);

        if (isBeachLike(base)) {
            RegistryEntry<Biome> out = pickBeachForBand(biomeRegistry, base, blockX, blockZ, bandIndex);
            debugPick(blockX, blockZ, borderRadiusBlocks, t, zone, base, out, true, false, null);
            return out;
        }

        if (base.isIn(BiomeTags.IS_RIVER)) {
            if (bandIndex >= 3) {
                try {
                    RegistryEntry<Biome> out = biome(biomeRegistry, "minecraft:frozen_river");
                    debugPick(blockX, blockZ, borderRadiusBlocks, t, zone, base, out, false, false, null);
                    return out;
                } catch (Throwable ignored) {
                    debugPick(blockX, blockZ, borderRadiusBlocks, t, zone, base, base, false, false, null);
                    return base;
                }
            } else {
                try {
                    RegistryEntry<Biome> out = biome(biomeRegistry, "minecraft:river");
                    debugPick(blockX, blockZ, borderRadiusBlocks, t, zone, base, out, false, false, null);
                    return out;
                } catch (Throwable ignored) {
                    debugPick(blockX, blockZ, borderRadiusBlocks, t, zone, base, base, false, false, null);
                    return base;
                }
            }
        }

        if (base.isIn(BiomeTags.IS_OCEAN)) {
            RegistryEntry<Biome> oceanPick = oceanByLatitudeBandOrBase(biomeRegistry, base, blockX, blockZ, bandIndex);
            RegistryEntry<Biome> out = mushroomIslandOverride(biomeRegistry, oceanPick, blockX, blockZ);
            debugPick(blockX, blockZ, borderRadiusBlocks, t, zone, base, out, false, false, null);
            return out;
        }

        int landBandIndex = latitudeBandIndexWithBlend(blockX, blockZ, borderRadiusBlocks, zone, t);
        RegistryEntry<Biome> chosen = switch (landBandIndex) {
            case BAND_EQUATOR -> pickFromWeightedTags(biomeRegistry, base, blockX, blockZ, BAND_EQUATOR, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_TROPICAL -> pickTropicalGradient(biomeRegistry, base, blockX, blockZ, t);
            case BAND_TEMPERATE -> pickFromWeightedTags(biomeRegistry, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickFromWeightedTags(biomeRegistry, base, blockX, blockZ, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTags(biomeRegistry, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
        String mangroveDecision = null;
        if (shouldTryMangroveOverride(chosen, landBandIndex)) {
            MangroveDecision decision = evaluateMangrove(blockX, blockZ, sampler);
            mangroveDecision = decision.logLabel();
            if (decision.allow()) {
                chosen = mangroveOverride(biomeRegistry, chosen);
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
        if (landBandIndex == BAND_TEMPERATE && isMountainLike(sampler, blockX, blockZ)) {
            chosen = pickFromTagNoiseOrBase(biomeRegistry, LAT_TEMPERATE_MOUNTAIN, base, blockX, blockZ, landBandIndex);
            if (isBiomeId(chosen, "minecraft:cherry_grove") && !rollChance(blockX, blockZ, 0xC7E22E55, 6L)) {
                chosen = pickFrom(biomeRegistry, blockX, blockZ, landBandIndex,
                        "minecraft:meadow",
                        "minecraft:grove",
                        "minecraft:windswept_hills",
                        "minecraft:stony_peaks");
            }
        }
        RegistryEntry<Biome> sanitized = sanitizeLandBiome(biomeRegistry, chosen, landBandIndex);
        RegistryEntry<Biome> safe = repickIfSurfaceCave(biomeRegistry, base, sanitized, blockX, blockZ, t, landBandIndex);
        RegistryEntry<Biome> out = applyLandOverrides(biomeRegistry, safe, blockX, blockZ, landBandIndex);
        debugPick(blockX, blockZ, borderRadiusBlocks, t, zone, base, out, false, out != sanitized, mangroveDecision);
        return out;
    }

    public static RegistryEntry<Biome> pick(Collection<RegistryEntry<Biome>> biomePool, RegistryEntry<Biome> base, int blockX, int blockZ, int borderRadiusBlocks, MultiNoiseUtil.MultiNoiseSampler sampler) {
        if (borderRadiusBlocks <= 0) {
            return base;
        }

        logTagPools(biomePool);

        int lat = Math.abs(blockZ);
        double t = (double) lat / (double) borderRadiusBlocks;
        LatitudeMath.LatitudeZone zone = LatitudeMath.zoneForRadius(borderRadiusBlocks, blockZ);
        int bandIndex = bandIndexForZone(zone);

        if (isBeachLike(base)) {
            RegistryEntry<Biome> out = pickBeachForBand(biomePool, base, blockX, blockZ, bandIndex);
            debugPick(blockX, blockZ, borderRadiusBlocks, t, zone, base, out, true, false, null);
            return out;
        }

        if (base.isIn(BiomeTags.IS_RIVER)) {
            if (bandIndex >= 3) {
                RegistryEntry<Biome> frozen = entryById(biomePool, "minecraft:frozen_river");
                RegistryEntry<Biome> out = frozen != null ? frozen : base;
                debugPick(blockX, blockZ, borderRadiusBlocks, t, zone, base, out, false, false, null);
                return out;
            }
            RegistryEntry<Biome> river = entryById(biomePool, "minecraft:river");
            RegistryEntry<Biome> out = river != null ? river : base;
            debugPick(blockX, blockZ, borderRadiusBlocks, t, zone, base, out, false, false, null);
            return out;
        }

        if (base.isIn(BiomeTags.IS_OCEAN)) {
            RegistryEntry<Biome> oceanPick = oceanByLatitudeBandOrBase(biomePool, base, blockX, blockZ, bandIndex);
            RegistryEntry<Biome> out = mushroomIslandOverride(biomePool, oceanPick, blockX, blockZ);
            debugPick(blockX, blockZ, borderRadiusBlocks, t, zone, base, out, false, false, null);
            return out;
        }

        int landBandIndex = latitudeBandIndexWithBlend(blockX, blockZ, borderRadiusBlocks, zone, t);
        RegistryEntry<Biome> chosen = switch (landBandIndex) {
            case BAND_EQUATOR -> pickFromWeightedTags(biomePool, base, blockX, blockZ, BAND_EQUATOR, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_TROPICAL -> pickTropicalGradient(biomePool, base, blockX, blockZ, t);
            case BAND_TEMPERATE -> pickFromWeightedTags(biomePool, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickFromWeightedTags(biomePool, base, blockX, blockZ, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTags(biomePool, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
        String mangroveDecision = null;
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
        if (landBandIndex == BAND_TEMPERATE && isMountainLike(sampler, blockX, blockZ)) {
            chosen = pickFromTagNoiseOrBase(biomePool, LAT_TEMPERATE_MOUNTAIN, base, blockX, blockZ, landBandIndex);
            if (isBiomeId(chosen, "minecraft:cherry_grove") && !rollChance(blockX, blockZ, 0xC7E22E55, 6L)) {
                chosen = pickFromFallbacks(biomePool, base,
                        "minecraft:meadow",
                        "minecraft:grove",
                        "minecraft:windswept_hills",
                        "minecraft:stony_peaks");
            }
        }
        RegistryEntry<Biome> sanitized = sanitizeLandBiome(biomePool, chosen, landBandIndex);
        RegistryEntry<Biome> safe = repickIfSurfaceCave(biomePool, base, sanitized, blockX, blockZ, t, landBandIndex);
        RegistryEntry<Biome> out = applyLandOverrides(biomePool, safe, blockX, blockZ, landBandIndex);
        debugPick(blockX, blockZ, borderRadiusBlocks, t, zone, base, out, false, out != sanitized, mangroveDecision);
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
        double dither = ValueNoise2D.sampleBlocks(seed ^ TROPICAL_DITHER_SALT, blockX, blockZ, DITHER_SCALE_BLOCKS);
        int step = baseStep;
        if (baseStep < 3 && dither < stepFrac) {
            step = baseStep + 1;
        }

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
        double dither = ValueNoise2D.sampleBlocks(seed ^ TROPICAL_DITHER_SALT, blockX, blockZ, DITHER_SCALE_BLOCKS);
        int step = baseStep;
        if (baseStep < 3 && dither < stepFrac) {
            step = baseStep + 1;
        }

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

        long warpSeed = WORLD_SEED ^ WARP_NOISE_SALT;
        double warpNoise = (blobNoise01(warpSeed, blockX >> 4, blockZ >> 4, WARP_NOISE_PATCH_CHUNKS, WARP_NOISE_SALT) * 2.0) - 1.0;
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

        double blendNoise;
        if (TRANSITION_MODE == TransitionMode.CELLHASH_PATCHES) {
            long ditherSeed = WORLD_SEED ^ DITHER_NOISE_SALT;
            int cellSize = BLEND_DITHER_SCALE_BLOCKS;
            int cellX = Math.floorDiv(blockX, cellSize);
            int cellZ = Math.floorDiv(blockZ, cellSize);
            blendNoise = cellHash01(ditherSeed, cellX, cellZ);
        } else {
            blendNoise = blobNoise01(WORLD_SEED, blockX >> 4, blockZ >> 4, BLEND_NOISE_PATCH_CHUNKS, BLEND_NOISE_SALT);
        }

        int chosenBandIndex = blendNoise < blendT ? upperBandIndex : lowerBandIndex;

        if (DEBUG_BLEND
                && (blockX & 15) == 0
                && (blockZ & 15) == 0
                && chosenBandIndex != bandIndex
                && BLEND_DEBUG_COUNT.incrementAndGet() <= DEBUG_LIMIT) {
            LOGGER.info("[LAT_BLEND] mode={} x={} z={} lat={} baseBand={} lower={} upper={} chosen={} boundary={} effectiveBoundary={} delta={} transitionWidth={} warpAmp={} warpPatchChunks={} blendPatchChunks={} t={} noise={}",
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
                    WARP_NOISE_PATCH_CHUNKS,
                    BLEND_NOISE_PATCH_CHUNKS,
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

    private static RegistryEntry<Biome> biome(Registry<Biome> biomes, String id) {
        Identifier ident = Identifier.of(id);
        return biomes.getEntry(ident).orElseThrow();
    }

    private static RegistryEntry<Biome> pickFrom(Registry<Biome> biomes, int blockX, int blockZ, int bandIndex, String... options) {
        int cellX = Math.floorDiv(blockX, VARIANT_CELL_SIZE_BLOCKS);
        int cellZ = Math.floorDiv(blockZ, VARIANT_CELL_SIZE_BLOCKS);
        int idx = (int) Long.remainderUnsigned(hash64(cellX, cellZ, bandIndex), options.length);
        return biome(biomes, options[idx]);
    }

    private static TagKey<Biome> weightedTagForRoll(int roll, TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        if (roll < 70) return primary;
        if (roll < 95) return secondary;
        return accent;
    }

    private static int weightedRoll(int blockX, int blockZ, int salt) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        int patchSizeChunks = Math.max(1, VARIANT_CELL_SIZE_BLOCKS >> 4);
        double blob = blobNoise01(WORLD_SEED, chunkX, chunkZ, patchSizeChunks, salt);
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
        return entries.get(idx);
    }

    private static RegistryEntry<Biome> pickFromTagNoiseOrBaseFiltered(Collection<RegistryEntry<Biome>> biomes, TagKey<Biome> tag, RegistryEntry<Biome> base,
                                                                       int blockX, int blockZ, int bandIndex, long extraSalt, boolean disallowMangrove) {
        List<RegistryEntry<Biome>> entries = entriesForTag(biomes, tag);
        if (disallowMangrove) {
            entries = filterMangrove(entries);
        }
        int size = entries.size();
        if (size <= 0) {
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
        return entries.get(idx);
    }

    private static RegistryEntry<Biome> pickFromTagNoiseOrBase(Collection<RegistryEntry<Biome>> biomes, TagKey<Biome> tag, RegistryEntry<Biome> base, int blockX, int blockZ, int bandIndex) {
        List<RegistryEntry<Biome>> entries = entriesForTag(biomes, tag);
        int size = entries.size();
        if (size <= 0) {
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
        return entries.get(idx);
    }

    private static RegistryEntry<Biome> pickFromFallbacks(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, String... fallbackOptions) {
        for (String fallback : fallbackOptions) {
            RegistryEntry<Biome> entry = entryById(biomes, fallback);
            if (entry != null) {
                return entry;
            }
        }
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
        Identifier target = Identifier.of(id);
        return entry.getKey()
                .map(key -> key.getValue().equals(target))
                .orElse(false);
    }

    private static String biomeId(RegistryEntry<Biome> entry) {
        return entry.getKey().map(key -> key.getValue().toString()).orElse("?");
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
        MultiNoiseUtil.NoiseValuePoint point = sampler.sample(noiseX, 0, noiseZ);
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
        MultiNoiseUtil.NoiseValuePoint point = sampler.sample(noiseX, 0, noiseZ);
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

    private static SwampDecision evaluateSwamp(int blockX, int blockZ, MultiNoiseUtil.MultiNoiseSampler sampler) {
        if (sampler == null) {
            return new SwampDecision(true, 0.0, 0.0, 0.0, true);
        }
        int noiseX = blockX >> 2;
        int noiseZ = blockZ >> 2;
        MultiNoiseUtil.NoiseValuePoint point = sampler.sample(noiseX, 0, noiseZ);
        double cont = MultiNoiseUtil.toFloat(point.continentalnessNoise());
        double erosion = MultiNoiseUtil.toFloat(point.erosionNoise());
        double weirdness = MultiNoiseUtil.toFloat(point.weirdnessNoise());
        // Swamps: lowland & not rugged; allow inland lowlands too (not just coastal slice)
        boolean swampOk =
            cont > -0.10 && cont < 0.45 &&
            erosion > -0.10 &&
            Math.abs(weirdness) < 0.18;
        return new SwampDecision(swampOk, cont, erosion, weirdness, swampOk);
    }

    private static boolean allowMangrovePatch(int blockX, int blockZ) {
        int cellX = Math.floorDiv(blockX, MANGROVE_PATCH_CELL_BLOCKS);
        int cellZ = Math.floorDiv(blockZ, MANGROVE_PATCH_CELL_BLOCKS);
        long roll = hash64(cellX, cellZ, MANGROVE_PATCH_SALT);
        return Long.remainderUnsigned(roll, 100L) < MANGROVE_PATCH_PERCENT;
    }

    private static RegistryEntry<Biome> pickMangroveFallback(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t, int bandIndex) {
        return switch (bandIndex) {
            case BAND_EQUATOR -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_EQUATOR, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_TROPICAL -> pickTropicalGradientNoMangrove(biomes, base, blockX, blockZ, t);
            case BAND_TEMPERATE -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
    }

    private static RegistryEntry<Biome> pickMangroveFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t, int bandIndex) {
        return switch (bandIndex) {
            case BAND_EQUATOR -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_EQUATOR, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_TROPICAL -> pickTropicalGradientNoMangrove(biomes, base, blockX, blockZ, t);
            case BAND_TEMPERATE -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTagsNoMangrove(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
    }

    private static RegistryEntry<Biome> pickSwampFallback(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t, int bandIndex) {
        return switch (bandIndex) {
            case BAND_EQUATOR -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_EQUATOR, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_TROPICAL -> pickTropicalGradientNoSwamp(biomes, base, blockX, blockZ, t);
            case BAND_TEMPERATE -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_POLAR, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
    }

    private static RegistryEntry<Biome> pickSwampFallback(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t, int bandIndex) {
        return switch (bandIndex) {
            case BAND_EQUATOR -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_EQUATOR, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case BAND_TROPICAL -> pickTropicalGradientNoSwamp(biomes, base, blockX, blockZ, t);
            case BAND_TEMPERATE -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_TEMPERATE, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case BAND_SUBPOLAR -> pickFromWeightedTagsNoSwamp(biomes, base, blockX, blockZ, BAND_SUBPOLAR, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
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
        double dither = ValueNoise2D.sampleBlocks(seed ^ TROPICAL_DITHER_SALT, blockX, blockZ, DITHER_SCALE_BLOCKS);
        int step = baseStep;
        if (baseStep < 3 && dither < stepFrac) {
            step = baseStep + 1;
        }

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
        double dither = ValueNoise2D.sampleBlocks(seed ^ TROPICAL_DITHER_SALT, blockX, blockZ, DITHER_SCALE_BLOCKS);
        int step = baseStep;
        if (baseStep < 3 && dither < stepFrac) {
            step = baseStep + 1;
        }

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
