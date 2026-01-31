package com.example.globe.world;

import com.example.globe.client.LatitudeConfig;
import com.example.globe.util.ValueNoise2D;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LatitudeBiomes {
    private LatitudeBiomes() {
    }

    private static RegistryEntry<Biome> pickBeachForBand(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, int bandIndex) {
        if (bandIndex < 3) {
            return base;
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
        if (bandIndex < 3) {
            return base;
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
        if (bandIndex == 1 || bandIndex == 2) {
            if (isBiomeId(pick, "minecraft:plains") && rollChance(blockX, blockZ, 0x7F4A7C15, 25L)) {
                try {
                    pick = biome(biomes, "minecraft:sunflower_plains");
                } catch (Throwable ignored) {
                    // Keep original pick.
                }
            }
        }

        if (bandIndex == 2) {
            if (isBiomeId(pick, "minecraft:dark_forest") && rollChance(blockX, blockZ, 0x51ED270B, 4000L)) {
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
        if (bandIndex == 1 || bandIndex == 2) {
            if (isBiomeId(pick, "minecraft:plains") && rollChance(blockX, blockZ, 0x7F4A7C15, 25L)) {
                RegistryEntry<Biome> entry = entryById(biomes, "minecraft:sunflower_plains");
                if (entry != null) {
                    pick = entry;
                }
            }
        }

        if (bandIndex == 2) {
            if (isBiomeId(pick, "minecraft:dark_forest") && rollChance(blockX, blockZ, 0x51ED270B, 4000L)) {
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

    private static final int VARIANT_CELL_SIZE_BLOCKS = 1024;

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

    public static RegistryEntry<Biome> pick(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, int borderRadiusBlocks) {
        if (borderRadiusBlocks <= 0) {
            return base;
        }

        int lat = Math.abs(blockZ);
        double t = (double) lat / (double) borderRadiusBlocks;

        int bandIndex = latitudeBandIndexWithBlend(blockX, blockZ, borderRadiusBlocks);

        if (base.isIn(BiomeTags.IS_BEACH)) {
            return pickBeachForBand(biomes, base, blockX, blockZ, bandIndex);
        }

        if (base.isIn(BiomeTags.IS_RIVER)) {
            if (t >= 0.80) {
                try {
                    return biome(biomes, "minecraft:frozen_river");
                } catch (Throwable ignored) {
                    return base;
                }
            } else {
                try {
                    return biome(biomes, "minecraft:river");
                } catch (Throwable ignored) {
                    return base;
                }
            }
        }

        if (base.isIn(BiomeTags.IS_OCEAN)) {
            RegistryEntry<Biome> oceanPick = oceanByLatitudeBandOrBase(biomes, base, blockX, blockZ, bandIndex);
            return mushroomIslandOverride(biomes, oceanPick, blockX, blockZ);
        }

        RegistryEntry<Biome> chosen = switch (bandIndex) {
            case 0 -> pickTropicalGradient(biomes, base, blockX, blockZ, t);
            case 1 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 1, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case 2 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 2, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case 3 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 3, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTags(biomes, base, blockX, blockZ, 4, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
        return applyLandOverrides(biomes, chosen, blockX, blockZ, bandIndex);
    }

    public static RegistryEntry<Biome> pick(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, int borderRadiusBlocks) {
        if (borderRadiusBlocks <= 0) {
            return base;
        }

        int lat = Math.abs(blockZ);
        double t = (double) lat / (double) borderRadiusBlocks;

        int bandIndex = latitudeBandIndexWithBlend(blockX, blockZ, borderRadiusBlocks);

        if (base.isIn(BiomeTags.IS_BEACH)) {
            return pickBeachForBand(biomes, base, blockX, blockZ, bandIndex);
        }

        if (base.isIn(BiomeTags.IS_RIVER)) {
            if (t >= 0.80) {
                RegistryEntry<Biome> frozen = entryById(biomes, "minecraft:frozen_river");
                return frozen != null ? frozen : base;
            }
            RegistryEntry<Biome> river = entryById(biomes, "minecraft:river");
            return river != null ? river : base;
        }

        if (base.isIn(BiomeTags.IS_OCEAN)) {
            RegistryEntry<Biome> oceanPick = oceanByLatitudeBandOrBase(biomes, base, blockX, blockZ, bandIndex);
            return mushroomIslandOverride(biomes, oceanPick, blockX, blockZ);
        }

        RegistryEntry<Biome> chosen = switch (bandIndex) {
            case 0 -> pickTropicalGradient(biomes, base, blockX, blockZ, t);
            case 1 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 1, 0x1A21, LAT_EQUATOR_PRIMARY, LAT_EQUATOR_SECONDARY, LAT_EQUATOR_ACCENT);
            case 2 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 2, 0x2B32, LAT_TEMPERATE_PRIMARY, LAT_TEMPERATE_SECONDARY, LAT_TEMPERATE_ACCENT);
            case 3 -> pickFromWeightedTags(biomes, base, blockX, blockZ, 3, 0x3C43, LAT_SUBPOLAR_PRIMARY, LAT_SUBPOLAR_SECONDARY, LAT_SUBPOLAR_ACCENT);
            default -> pickFromWeightedTags(biomes, base, blockX, blockZ, 4, 0x4D54, LAT_POLAR_PRIMARY, LAT_POLAR_SECONDARY, LAT_POLAR_ACCENT);
        };
        return applyLandOverrides(biomes, chosen, blockX, blockZ, bandIndex);
    }

    private static RegistryEntry<Biome> pickTropicalGradient(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, double t) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        long seed = 0L;

        // Tropical band is [0.00..0.18]. We want wet near equator (t=0), arid near the edge (t=0.18).
        double u = clamp(t / 0.18, 0.0, 1.0);
        double ladderT = 1.0 - u;

        double jitterN = (blobNoise01(seed, chunkX, chunkZ, 8, 0xBADC0FFEE0DDF00DL) * 2.0) - 1.0;
        double tJitter = ladderT + (jitterN * 0.12);
        tJitter = clamp(tJitter, 0.0, 1.0);
        tJitter = smoothstep(tJitter);

        int step = clampInt((int) Math.floor(tJitter * 4.0), 0, 3);

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

        long seed = 0L;

        // Tropical band is [0.00..0.18]. We want wet near equator (t=0), arid near the edge (t=0.18).
        double u = clamp(t / 0.18, 0.0, 1.0);
        double ladderT = 1.0 - u;

        double jitterN = (blobNoise01(seed, chunkX, chunkZ, 8, 0xBADC0FFEE0DDF00DL) * 2.0) - 1.0;
        double tJitter = ladderT + (jitterN * 0.12);
        tJitter = clamp(tJitter, 0.0, 1.0);
        tJitter = smoothstep(tJitter);

        int step = clampInt((int) Math.floor(tJitter * 4.0), 0, 3);

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


    private static int latitudeBandIndexWithBlend(int blockX, int blockZ, int radius) {
        if (radius <= 0) {
            return 0;
        }

        int absZ = Math.abs(blockZ);
        double t = (double) absZ / (double) radius;
        int bandIndex = crispBandIndex(t);

        if (!LatitudeConfig.latitudeBandBlendingEnabled) {
            return bandIndex;
        }

        double blendWidth = clamp((double) radius * LatitudeConfig.latitudeBandBlendWidthFrac, 120.0, 700.0);
        double warpMax = clamp((double) radius * LatitudeConfig.latitudeBandBoundaryWarpFrac, 0.0, blendWidth);
        if (blendWidth <= 0.0) {
            return bandIndex;
        }

        int lowerBandIndex;
        int upperBandIndex;
        int boundary;
        if (bandIndex <= 0) {
            lowerBandIndex = 0;
            upperBandIndex = 1;
            boundary = bandBoundaryBlocks(0, radius);
        } else if (bandIndex >= 4) {
            lowerBandIndex = 3;
            upperBandIndex = 4;
            boundary = bandBoundaryBlocks(3, radius);
        } else {
            int loBoundary = bandBoundaryBlocks(bandIndex - 1, radius);
            int hiBoundary = bandBoundaryBlocks(bandIndex, radius);
            int dLo = Math.abs(absZ - loBoundary);
            int dHi = Math.abs(absZ - hiBoundary);
            if (dLo <= dHi) {
                lowerBandIndex = bandIndex - 1;
                upperBandIndex = bandIndex;
                boundary = loBoundary;
            } else {
                lowerBandIndex = bandIndex;
                upperBandIndex = bandIndex + 1;
                boundary = hiBoundary;
            }
        }

        double signedDistToBoundary = (double) absZ - (double) boundary;
        if (Math.abs(signedDistToBoundary) > blendWidth) {
            return bandIndex;
        }

        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        final int PATCH_SIZE_CHUNKS = 6;
        final long SALT_BLEND = 0xA1B2C3D4E5F60718L;
        final long SALT_WARP = 0x1020304050607080L;

        long seed = 0L;
        double blendNoise01 = blobNoise01(seed, chunkX, chunkZ, PATCH_SIZE_CHUNKS, SALT_BLEND);
        double warpNoise01 = blobNoise01(seed, chunkX, chunkZ, 8, SALT_WARP);
        double warpSigned = (warpNoise01 * 2.0) - 1.0;

        double warp = clamp(warpSigned * warpMax, -blendWidth, blendWidth);
        double dist = signedDistToBoundary + warp;

        double tt = (dist / blendWidth) * 0.5 + 0.5;
        tt = smoothstep(tt);

        boolean chooseUpper = blendNoise01 < tt;
        int chosenBandIndex = chooseUpper ? upperBandIndex : lowerBandIndex;

        if (LatitudeConfig.debugLatitudeBlend && Math.abs(dist) <= blendWidth && (blockX & 15) == 0 && (blockZ & 15) == 0) {
            LOGGER.info("[LAT_BLEND] cx={} cz={} t={} noise={} pick={}",
                    chunkX, chunkZ,
                    String.format(java.util.Locale.ROOT, "%.2f", tt),
                    String.format(java.util.Locale.ROOT, "%.2f", blendNoise01),
                    chooseUpper ? "UPPER" : "LOWER");
        }

        return chosenBandIndex;
    }

    private static int crispBandIndex(double t) {
        if (t < 0.18) return 0;
        if (t < 0.60) return 1;
        if (t < 0.80) return 2;
        if (t < 0.93) return 3;
        return 4;
    }

    private static int bandBoundaryBlocks(int boundaryIndex, int radius) {
        return switch (boundaryIndex) {
            case 0 -> (int) Math.round(0.18 * (double) radius);
            case 1 -> (int) Math.round(0.60 * (double) radius);
            case 2 -> (int) Math.round(0.80 * (double) radius);
            default -> (int) Math.round(0.93 * (double) radius);
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
        int cellX = Math.floorDiv(blockX, VARIANT_CELL_SIZE_BLOCKS);
        int cellZ = Math.floorDiv(blockZ, VARIANT_CELL_SIZE_BLOCKS);
        long roll = hash64(cellX, cellZ, salt);
        return (int) Long.remainderUnsigned(roll, 100L);
    }

    private static RegistryEntry<Biome> pickFromWeightedTags(Registry<Biome> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                             int bandIndex, int weightSalt,
                                                             TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt);
        TagKey<Biome> tag = weightedTagForRoll(roll, primary, secondary, accent);
        return pickFromTagNoiseOrBase(biomes, tag, base, blockX, blockZ, bandIndex);
    }

    private static RegistryEntry<Biome> pickFromWeightedTags(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ,
                                                             int bandIndex, int weightSalt,
                                                             TagKey<Biome> primary, TagKey<Biome> secondary, TagKey<Biome> accent) {
        int roll = weightedRoll(blockX, blockZ, weightSalt);
        TagKey<Biome> tag = weightedTagForRoll(roll, primary, secondary, accent);
        return pickFromTagNoiseOrBase(biomes, tag, base, blockX, blockZ, bandIndex);
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
