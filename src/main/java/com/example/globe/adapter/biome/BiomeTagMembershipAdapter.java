package com.example.globe.adapter.biome;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Platform Adapter shell for tag/provider membership checks, per
 * {@code docs/porting/PORTABILITY_ARCHITECTURE.md}.
 */
public interface BiomeTagMembershipAdapter {

    boolean isIn(Holder<Biome> biome, TagKey<Biome> tag);
}
