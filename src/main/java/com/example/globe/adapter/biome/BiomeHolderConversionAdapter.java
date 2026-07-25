package com.example.globe.adapter.biome;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

/**
 * Platform Adapter shell for {@code Holder<Biome>} to/from {@code Identifier} conversion, per
 * {@code docs/porting/PORTABILITY_ARCHITECTURE.md}.
 */
public interface BiomeHolderConversionAdapter {

    Optional<Identifier> idOf(Holder<Biome> biome);
}
