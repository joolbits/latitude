package com.example.globe.world;

import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;

/**
 * Prepared Minecraft 26.3 biome resolver that keeps Latitude context outside the per-coordinate
 * query. The delegate is the resolver Minecraft already created for the operation or chunk, so
 * wrapping it must not discard target-version caching or interpolation.
 */
public final class LatitudeBiomeResolver implements BiomeResolver {
    @FunctionalInterface
    public interface Resolution {
        Holder<Biome> resolve(BiomeResolver delegate, int quartX, int quartY, int quartZ);
    }

    private final BiomeResolver delegate;
    private final Resolution resolution;

    public LatitudeBiomeResolver(BiomeResolver delegate, Resolution resolution) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.resolution = Objects.requireNonNull(resolution, "resolution");
    }

    public BiomeResolver delegate() {
        return delegate;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        return resolution.resolve(delegate, quartX, quartY, quartZ);
    }
}
