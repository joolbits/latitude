package com.example.globe.adapter.climate;

import net.minecraft.world.level.biome.Climate;

/**
 * Trivial pass-through to {@link Climate.Sampler#sample(int, int, int)}. No algorithm.
 */
public final class DefaultClimateSamplerAdapter implements ClimateSamplerAdapter {

    public static final DefaultClimateSamplerAdapter INSTANCE = new DefaultClimateSamplerAdapter();

    private DefaultClimateSamplerAdapter() {
    }

    @Override
    public Climate.TargetPoint sample(Climate.Sampler sampler, int noiseX, int noiseY, int noiseZ) {
        return sampler.sample(noiseX, noiseY, noiseZ);
    }
}
