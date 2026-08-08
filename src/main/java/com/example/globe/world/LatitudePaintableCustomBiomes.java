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
 * The full set of custom biomes Latitude can paint: the {@code lat_*} band tags UNION the
 * provider-ticket ledger ({@link BiomeDescriptorLedger}), resolved against a live biome registry.
 *
 * <p>Latitude admits custom biomes through two independent paths, and any mechanism that
 * enumerates "the custom biomes" from only one of them silently strands the other path's biomes.
 * Two consumers need the union rather than either half:
 *
 * <ul>
 *   <li>{@code ChunkGeneratorGenerateFeaturesBiomeSetMixin} — the decoration-index protection.
 *       Vanilla narrows each chunk's biome set with {@code retainAll(possibleBiomes())} and builds
 *       its feature index from the same raw list, so a biome Latitude painted but vanilla never
 *       expected is dropped and generates with no features at all.</li>
 *   <li>{@link LatitudeBiomeSource} — the {@code /locate biome} candidate pool, which gates on
 *       source membership before any search runs.</li>
 * </ul>
 *
 * <p>{@code minecraft:} entries are skipped: they are always in the raw source's own
 * {@code possibleBiomes()} already. Absent optional mods simply fail the registry lookup and are
 * skipped.
 *
 * <p>This class exists on the 26.2 line as the shared home for a helper that upstream (the
 * 1.21.11 port) placed on {@code LatitudeDecorationRetrofit}, because mixin classes must never be
 * referenced from ordinary code. That retrofit feature is deliberately not part of the 26.2 line,
 * so the helper lives here instead — same logic, no dependency on the unshipped feature.
 */
public final class LatitudePaintableCustomBiomes {

    /** The {@code globe:lat_*} band tag paths Latitude routes custom biomes through. */
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

    private LatitudePaintableCustomBiomes() {}

    /** Every non-vanilla biome Latitude can paint, tag-routed and ledger-routed alike. */
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
