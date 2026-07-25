package com.example.globe.adapter.climate;

import net.minecraft.world.level.biome.Climate;

/**
 * Platform Adapter shell for {@code Climate.Sampler} access, per
 * {@code docs/porting/PORTABILITY_ARCHITECTURE.md}. Coordinates are noise-space (each unit is
 * 4 blocks), matching the existing {@code sampler.sample(...)} call sites in
 * {@code LatitudeBiomes}.
 */
public interface ClimateSamplerAdapter {

    Climate.TargetPoint sample(Climate.Sampler sampler, int noiseX, int noiseY, int noiseZ);
}
