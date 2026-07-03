package com.example.globe.adapter.biome;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

/**
 * Platform Adapter shell for biome registry lookup, per
 * {@code docs/porting/PORTABILITY_ARCHITECTURE.md}. A future Minecraft port replaces the
 * implementation, not every call site that needs a biome by id.
 */
public interface BiomeRegistryAdapter {

    Optional<Holder<Biome>> lookup(Registry<Biome> registry, Identifier id);
}
