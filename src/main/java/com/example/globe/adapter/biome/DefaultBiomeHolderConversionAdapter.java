package com.example.globe.adapter.biome;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

/**
 * Trivial pass-through to {@code Holder#unwrapKey()}. No algorithm.
 */
public final class DefaultBiomeHolderConversionAdapter implements BiomeHolderConversionAdapter {

    public static final DefaultBiomeHolderConversionAdapter INSTANCE = new DefaultBiomeHolderConversionAdapter();

    private DefaultBiomeHolderConversionAdapter() {
    }

    @Override
    public Optional<Identifier> idOf(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.identifier());
    }
}
