package com.example.globe.world;

import com.example.globe.mixin.BiomeSourceAccessor;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.Collection;
import java.util.stream.Stream;

public final class LatitudeBiomeSource extends BiomeSource {
    private static final int MAX_CAVE_BIOME_Y = Integer.getInteger("latitude.maxCaveBiomeY", 96);
    private static final int HARD_DECK_SURFACE_Y = Integer.getInteger("latitude.hardDeckSurfaceY", 20);
    private static final int DEEP_DARK_MAX_Y = -16;
    private static final Identifier LUSH_CAVES_ID = Identifier.of("minecraft", "lush_caves");
    private static final Identifier DRIPSTONE_CAVES_ID = Identifier.of("minecraft", "dripstone_caves");
    private static final Identifier DEEP_DARK_ID = Identifier.of("minecraft", "deep_dark");

    private final BiomeSource original;
    private final Collection<RegistryEntry<Biome>> biomes;
    private final int borderRadiusBlocks;

    public LatitudeBiomeSource(BiomeSource original, Collection<RegistryEntry<Biome>> biomes, int borderRadiusBlocks) {
        this.original = original;
        this.biomes = biomes;
        this.borderRadiusBlocks = borderRadiusBlocks;
    }

    public BiomeSource original() {
        return original;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BiomeSource> getCodec() {
        @SuppressWarnings("unchecked")
        MapCodec<BiomeSource> delegate = (MapCodec<BiomeSource>) ((BiomeSourceAccessor) original).globe$invokeGetCodec();
        return new MapCodec<>() {
            @Override
            public <T> RecordBuilder<T> encode(BiomeSource input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                BiomeSource toEncode = input instanceof LatitudeBiomeSource wrapper ? wrapper.original : input;
                return delegate.encode(toEncode, ops, prefix);
            }

            @Override
            public <T> DataResult<BiomeSource> decode(DynamicOps<T> ops, MapLike<T> input) {
                return delegate.decode(ops, input);
            }

            @Override
            public <T> java.util.stream.Stream<T> keys(DynamicOps<T> ops) {
                return delegate.keys(ops);
            }
        };
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return original.getBiomes().stream();
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler sampler) {
        RegistryEntry<Biome> current = original.getBiome(x, y, z, sampler);
        RegistryEntry<Biome> base = original.getBiome(x, LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2, z, sampler);
        int blockX = x << 2;
        int blockZ = z << 2;
        int blockY = y << 2;
        if (shouldPreserveCave(current, base, blockY)) {
            return current;
        }
        return LatitudeBiomes.pick(biomes, base, blockX, blockZ, blockY, borderRadiusBlocks, sampler, "SOURCE", null, null, null);
    }

    private static boolean shouldPreserveCave(RegistryEntry<Biome> current, RegistryEntry<Biome> surfaceBase, int blockY) {
        if (!isCaveBiome(current)) {
            return false;
        }
        if (blockY > MAX_CAVE_BIOME_Y) {
            return false;
        }
        if (isDeepDark(current) && blockY > DEEP_DARK_MAX_Y) {
            return false;
        }
        if (blockY > HARD_DECK_SURFACE_Y && isCaveBiome(surfaceBase)) {
            return false;
        }
        return true;
    }

    private static boolean isCaveBiome(RegistryEntry<Biome> entry) {
        Identifier id = biomeId(entry);
        if (id == null) {
            return false;
        }
        return id.equals(LUSH_CAVES_ID) || id.equals(DRIPSTONE_CAVES_ID) || id.equals(DEEP_DARK_ID);
    }

    private static boolean isDeepDark(RegistryEntry<Biome> entry) {
        Identifier id = biomeId(entry);
        return id != null && id.equals(DEEP_DARK_ID);
    }

    private static Identifier biomeId(RegistryEntry<Biome> entry) {
        if (entry == null) {
            return null;
        }
        return entry.getKey().map(key -> key.getValue()).orElse(null);
    }
}
