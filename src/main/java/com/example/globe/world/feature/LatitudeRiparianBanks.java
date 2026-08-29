package com.example.globe.world.feature;

import com.example.globe.GlobeMod;
import com.example.globe.world.BiomeDescriptorLedger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * "Lush riverbanks in the desert" - the ground beside fresh water inside an arid biome sometimes
 * grows green instead of running bare sand to the waterline (maintainer ruling, 2026-08-19).
 *
 * <p>This is planting only, and the ruling was explicit about that. The biome stays desert or
 * badlands: nothing here participates in biome choice, biome shares, mob spawning or structure
 * eligibility. Two placed features are appended to the arid biomes' vegetal decoration step - the
 * ground patch first, then the plants that need it - and everything else about those biomes is
 * left exactly as the registry defines it.</p>
 *
 * <p>Arid identity is read from {@link BiomeDescriptorLedger}, Latitude's own closed authority, so
 * a biome the ledger cannot describe simply gets no banks rather than being matched by name.</p>
 */
public final class LatitudeRiparianBanks {
    /**
     * Default on. Set {@code -Dlatitude.riparianDesertBanks=false} to return every desert and
     * badlands waterline to bare ground.
     */
    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.riparianDesertBanks", "true"));

    private static final Identifier RIPARIAN_TYPE_ID =
            Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "riparian");

    /**
     * Order is load-bearing: the ground patch has to exist before the plants that stand on it, and
     * this list is the order the features are appended to the vegetal step in.
     */
    private static final List<Identifier> ORDERED_FEATURE_IDS = List.of(
            Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "riparian/desert_bank_soil"),
            Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "riparian/desert_bank_plants"));

    /** Ledger-described arid surface land: vanilla desert and the badlands family, plus any
     *  optional-mod identity the ledger routes the same way. */
    private static final Set<Identifier> ARID_LAND_IDS = aridLandIds();

    /** The placement modifier type behind {@code "type": "globe:riparian"} in placed-feature JSON. */
    public static final PlacementModifierType<RiparianPlacement> RIPARIAN = () -> RiparianPlacement.CODEC;

    private static volatile boolean registered;

    private LatitudeRiparianBanks() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Registers the placement modifier type. Deliberately unconditional: the type has to resolve
     * whenever the datapack is read, or the placed-feature JSON fails to parse when the feature is
     * merely switched off rather than removed.
     */
    public static synchronized void registerPlacementType() {
        if (registered) {
            return;
        }
        registered = true;
        Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, RIPARIAN_TYPE_ID, RIPARIAN);
    }

    /** True only for biomes the ledger describes as Latitude-owned arid surface land. */
    public static boolean isAridLand(Holder<Biome> biome) {
        if (biome == null) {
            return false;
        }
        return biome.unwrapKey()
                .map(key -> ARID_LAND_IDS.contains(key.identifier()))
                .orElse(Boolean.FALSE);
    }

    /**
     * Resolves the bank features in load order, or an empty list when the feature is switched off
     * or either entry is missing. Both or neither: plants without their ground would be planted on
     * sand and die on the first block update.
     */
    public static List<Holder<PlacedFeature>> resolvePlacedFeatures(RegistryAccess registryAccess) {
        if (!ENABLED || registryAccess == null) {
            return List.of();
        }
        Optional<Registry<PlacedFeature>> registry = registryAccess.lookup(Registries.PLACED_FEATURE);
        if (registry.isEmpty()) {
            return List.of();
        }
        List<Holder<PlacedFeature>> resolved = new ArrayList<>(ORDERED_FEATURE_IDS.size());
        for (Identifier id : ORDERED_FEATURE_IDS) {
            Optional<Holder.Reference<PlacedFeature>> holder = registry.get().get(id);
            if (holder.isEmpty()) {
                GlobeMod.LOGGER.warn("[LAT][RIPARIAN] missing placed feature {}; desert banks stay bare", id);
                return List.of();
            }
            resolved.add(holder.get());
        }
        return List.copyOf(resolved);
    }

    private static Set<Identifier> aridLandIds() {
        Set<Identifier> ids = new HashSet<>();
        for (BiomeDescriptorLedger.Descriptor descriptor : BiomeDescriptorLedger.descriptors()) {
            if (BiomeDescriptorLedger.isAridSurfaceLand(descriptor.biomeId())) {
                ids.add(Identifier.parse(descriptor.biomeId()));
            }
        }
        return Set.copyOf(ids);
    }
}
