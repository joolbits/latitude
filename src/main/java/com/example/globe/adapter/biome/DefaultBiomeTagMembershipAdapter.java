package com.example.globe.adapter.biome;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Trivial pass-through to {@link Holder#is(TagKey)}. No algorithm.
 */
public final class DefaultBiomeTagMembershipAdapter implements BiomeTagMembershipAdapter {

    public static final DefaultBiomeTagMembershipAdapter INSTANCE = new DefaultBiomeTagMembershipAdapter();

    private DefaultBiomeTagMembershipAdapter() {
    }

    @Override
    public boolean isIn(Holder<Biome> biome, TagKey<Biome> tag) {
        return biome.is(tag);
    }
}
