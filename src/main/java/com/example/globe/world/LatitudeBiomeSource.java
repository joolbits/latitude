package com.example.globe.world;

import com.example.globe.mixin.BiomeSourceAccessor;
import com.mojang.serialization.Codec;
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
    protected Codec<? extends BiomeSource> getCodec() {
        @SuppressWarnings("unchecked")
        Codec<? extends BiomeSource> delegate = ((BiomeSourceAccessor) original).globe$invokeGetCodec();
        return delegate;
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
        int blockY = y << 2;
        return LatitudeBiomes.pick(biomes, base, blockX, blockZ, blockY, borderRadiusBlocks, sampler, "SOURCE", null, null, null);
    }
}
