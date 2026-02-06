package com.example.globe.world;

import com.example.globe.mixin.BiomeSourceAccessor;
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
    protected com.mojang.serialization.Codec<? extends BiomeSource> getCodec() {
        // In 1.20.1, BiomeSource.getCodec() returns Codec, not MapCodec.
        // Delegate to the original source's codec so serialization round-trips correctly.
        return ((BiomeSourceAccessor) original).globe$invokeGetCodec();
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
        return LatitudeBiomes.pick(biomes, base, blockX, blockZ, borderRadiusBlocks, sampler, "SOURCE");
    }
}
