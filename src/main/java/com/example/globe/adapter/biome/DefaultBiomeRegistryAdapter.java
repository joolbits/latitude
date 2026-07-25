package com.example.globe.adapter.biome;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

/**
 * Trivial pass-through to {@link Registry#get(Identifier)}. No algorithm -- this exists so
 * callers depend on {@link BiomeRegistryAdapter} instead of {@code Registry} directly.
 */
public final class DefaultBiomeRegistryAdapter implements BiomeRegistryAdapter {

    public static final DefaultBiomeRegistryAdapter INSTANCE = new DefaultBiomeRegistryAdapter();

    private DefaultBiomeRegistryAdapter() {
    }

    @Override
    public Optional<Holder<Biome>> lookup(Registry<Biome> registry, Identifier id) {
        return registry.get(id).map(reference -> (Holder<Biome>) reference);
    }
}
