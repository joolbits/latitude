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

    private static final Logger LOGGER = LoggerFactory.getLogger("LatitudeBiomes");

    private static final TagKey<Biome> LAT_EQUATOR = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_equator"));
    private static final TagKey<Biome> LAT_TROPICAL = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_tropical"));
    private static final TagKey<Biome> LAT_TEMPERATE = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_temperate"));
    private static final TagKey<Biome> LAT_SUBPOLAR = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_subpolar"));
    private static final TagKey<Biome> LAT_POLAR = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_polar"));

    private static final TagKey<Biome> LAT_ARID = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_arid"));
    private static final TagKey<Biome> LAT_TROPICS = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_tropics"));
    private static final TagKey<Biome> LAT_TRANS_ARID_TROPICS_1 = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_trans_arid_tropics_1"));
    private static final TagKey<Biome> LAT_TRANS_ARID_TROPICS_2 = TagKey.of(RegistryKeys.BIOME, Identifier.of("globe", "lat_trans_arid_tropics_2"));

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
        // Keep coastlines/rivers vanilla, but DO NOT keep ocean temperature vanilla.
        if (base.isIn(BiomeTags.IS_BEACH)) {
            return base;
        }

        if (borderRadiusBlocks <= 0) {
            return base;
        }

        int lat = Math.abs(blockZ);
        double t = (double) lat / (double) borderRadiusBlocks;

        int bandIndex = latitudeBandIndexWithBlend(blockX, blockZ, borderRadiusBlocks);

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
            return oceanByLatitudeBandOrBase(biomes, base, blockX, blockZ, bandIndex);
        }

        return switch (bandIndex) {
            case 0 -> pickTropicalGradient(biomes, base, blockX, blockZ, t);
            case 1 -> pickFromLandTagOrBase(biomes, LAT_EQUATOR, base, blockX, blockZ, 1);
            case 2 -> pickFromLandTagOrBase(biomes, LAT_TEMPERATE, base, blockX, blockZ, 2);
            case 3 -> pickFromLandTagOrBase(biomes, LAT_SUBPOLAR, base, blockX, blockZ, 3);
            default -> pickFromLandTagOrBase(biomes, LAT_POLAR, base, blockX, blockZ, 4);
        };
    }

    public static RegistryEntry<Biome> pick(Collection<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> base, int blockX, int blockZ, int borderRadiusBlocks) {
        // Keep coastlines/rivers vanilla, but DO NOT keep ocean temperature vanilla.
        if (base.isIn(BiomeTags.IS_BEACH)) {
            return base;
        }

        if (borderRadiusBlocks <= 0) {
            return base;
        }

        int lat = Math.abs(blockZ);
        double t = (double) lat / (double) borderRadiusBlocks;

        int bandIndex = latitudeBandIndexWithBlend(blockX, blockZ, borderRadiusBlocks);

        if (base.isIn(BiomeTags.IS_RIVER)) {
            if (t >= 0.80) {
                RegistryEntry<Biome> frozen = entryById(biomes, "minecraft:frozen_river");
                return frozen != null ? frozen : base;
            }
            RegistryEntry<Biome> river = entryById(biomes, "minecraft:river");
            return river != null ? river : base;
        }

        if (base.isIn(BiomeTags.IS_OCEAN)) {
            return oceanByLatitudeBandOrBase(biomes, base, blockX, blockZ, bandIndex);
        }

        return switch (bandIndex) {
            case 0 -> pickTropicalGradient(biomes, base, blockX, blockZ, t);
            case 1 -> pickFromLandTagOrBase(biomes, LAT_EQUATOR, base, blockX, blockZ, 1);
            case 2 -> pickFromLandTagOrBase(biomes, LAT_TEMPERATE, base, blockX, blockZ, 2);
            case 3 -> pickFromLandTagOrBase(biomes, LAT_SUBPOLAR, base, blockX, blockZ, 3);
            default -> pickFromLandTagOrBase(biomes, LAT_POLAR, base, blockX, blockZ, 4);
        };
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

        TagKey<Biome> chosen = switch (step) {
            case 1 -> LAT_TRANS_ARID_TROPICS_1;
            case 2 -> LAT_TRANS_ARID_TROPICS_2;
            case 3 -> LAT_TROPICS;
            default -> LAT_ARID;
        };

        return pickFromTagNoiseOrBase(biomes, chosen, base, blockX, blockZ, 100 + step);
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

        TagKey<Biome> chosen = switch (step) {
            case 1 -> LAT_TRANS_ARID_TROPICS_1;
            case 2 -> LAT_TRANS_ARID_TROPICS_2;
            case 3 -> LAT_TROPICS;
            default -> LAT_ARID;
        };

        return pickFromTagNoiseOrBase(biomes, chosen, base, blockX, blockZ, 100 + step);
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

    private static RegistryEntry<Biome> pickFromLandTagOrBase(Registry<Biome> biomes, TagKey<Biome> tag, RegistryEntry<Biome> base, int blockX, int blockZ, int bandIndex) {
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

    private static RegistryEntry<Biome> pickFromLandTagOrBase(Collection<RegistryEntry<Biome>> biomes, TagKey<Biome> tag, RegistryEntry<Biome> base, int blockX, int blockZ, int bandIndex) {
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

    private static boolean isRiver(RegistryEntry<Biome> biome) {
        return biome.getKey()
                .map(key -> key.getValue().getPath().contains("river"))
                .orElse(false);
    }
}
