package com.example.globe.mixin;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the RAW {@code ChunkGenerator.biomeSource} FIELD -- deliberately NOT the public
 * {@code getBiomeSource()} getter, which {@code ChunkGeneratorBiomeSourceMixin} wraps into a
 * {@code LatitudeBiomeSource} on globe worlds. The B-9 carver seam
 * ({@code NoiseChunkGeneratorCarveMixin}) needs the raw field because its sea probe must mirror the
 * EXACT source {@code applyCarvers} itself resolves carver biomes from (design ground truth 3: the
 * carver-biome lambda reads the raw field, quart-sampled at the seed chunk's min corner), and
 * because the field is {@code protected} on the superclass a {@code NoiseBasedChunkGenerator}-target
 * mixin cannot {@code @Shadow} it directly.
 */
@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorAccessor {
    @Accessor("biomeSource")
    BiomeSource globe$getRawBiomeSource();
}
