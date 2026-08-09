package com.example.globe.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * The full set of custom biomes Latitude can paint: the {@link #DECORATION_POLICY_TAG_PATHS} union
 * {@link BiomeDescriptorLedger}, registry-resolved.
 *
 * <p>Latitude admits custom biomes through TWO paths — the {@code lat_*} band tags and the
 * provider-ticket ledger — and a ledger route makes a biome paintable with no tag membership at
 * all. Any mechanism that enumerates "the custom biomes" from only one path silently strands the
 * other path's biomes, so both consumers below share this single answer:
 *
 * <ul>
 *   <li>{@code ChunkGeneratorGenerateFeaturesBiomeSetMixin} — the decoration-index protection.
 *   <li>{@link LatitudeBiomeSource} — the {@code /locate biome} candidate pool.
 * </ul>
 *
 * <p>This lives in ordinary code rather than in the mixin because mixin classes must never be
 * referenced from ordinary code.
 *
 * <p>{@code minecraft:} entries are skipped — they are always in the raw source's own
 * {@code possibleBiomes()} already; absent optional mods simply fail the registry lookup and are
 * skipped.
 */
public final class LatitudePaintableCustomBiomes {

    /** The {@code globe:lat_*} band tags — Latitude's tag-based custom-biome admission path. */
    public static final String[] DECORATION_POLICY_TAG_PATHS = {
            "lat_tropics_primary",
            "lat_tropics_secondary",
            "lat_tropics_accent",
            "lat_arid_primary",
            "lat_arid_secondary",
            "lat_arid_accent",
            "lat_trans_arid_tropics_1_primary",
            "lat_trans_arid_tropics_1_secondary",
            "lat_trans_arid_tropics_1_accent",
            "lat_trans_arid_tropics_2_primary",
            "lat_trans_arid_tropics_2_secondary",
            "lat_trans_arid_tropics_2_accent",
            "lat_subtropical_humid_primary",
            "lat_subtropical_humid_secondary",
            "lat_subtropical_humid_accent",
            "lat_temperate_primary",
            "lat_temperate_secondary",
            "lat_temperate_accent",
            "lat_temperate_mountain",
            "lat_subpolar_primary",
            "lat_subpolar_secondary",
            "lat_subpolar_accent",
            "lat_polar_primary",
            "lat_polar_secondary",
            "lat_polar_accent",
            "lat_ocean_tropical",
            "lat_ocean_temperate",
            "lat_ocean_subpolar",
            "lat_ocean_polar"
    };

    private LatitudePaintableCustomBiomes() {
    }

    /**
     * Everything Latitude could have placed in a world: tag-routed and ledger-routed custom biomes
     * alike. Both callers need this full producible set, not a subset of one admission path.
     */
    public static List<Holder<Biome>> allPaintableCustomBiomes(Registry<Biome> biomeRegistry) {
        Map<Identifier, Holder<Biome>> out = new LinkedHashMap<>();
        for (String tagPath : DECORATION_POLICY_TAG_PATHS) {
            TagKey<Biome> tag = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("globe", tagPath));
            for (Holder<Biome> holder : biomeRegistry.getTagOrEmpty(tag)) {
                holder.unwrapKey().ifPresent(key -> {
                    Identifier id = key.identifier();
                    if (!"minecraft".equals(id.getNamespace())) {
                        out.putIfAbsent(id, holder);
                    }
                });
            }
        }
        // The lat_* tags are only ONE of Latitude's two custom-biome admission paths. The other is
        // the provider-ticket ledger (BiomeDescriptorLedger): a ledger route makes a biome paintable
        // with no tag membership at all — biomesoplenty:overgrown_greens (TEMPERATE_LOWLAND, no tag)
        // was the maintainer's live find, painted bare because its features never made it into the
        // decoration index and retainAll dropped it from the per-chunk biome set.
        for (BiomeDescriptorLedger.Descriptor descriptor : BiomeDescriptorLedger.descriptors()) {
            Identifier id = Identifier.tryParse(descriptor.biomeId());
            if (id == null || "minecraft".equals(id.getNamespace()) || out.containsKey(id)) {
                continue;
            }
            biomeRegistry.get(id).ifPresent(holder -> out.putIfAbsent(id, holder));
        }
        return List.copyOf(out.values());
    }
}
