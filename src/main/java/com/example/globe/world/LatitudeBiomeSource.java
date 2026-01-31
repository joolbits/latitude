package com.example.globe.world;

import com.example.globe.mixin.BiomeSourceAccessor;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.Collection;
import java.util.stream.Stream;

public final class LatitudeBiomeSource extends BiomeSource {
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
        RegistryEntry<Biome> base = original.getBiome(x, 0, z, sampler);
        int blockX = x << 2;
        int blockZ = z << 2;
        return LatitudeBiomes.pick(biomes, base, blockX, blockZ, borderRadiusBlocks, sampler);
    }
}
