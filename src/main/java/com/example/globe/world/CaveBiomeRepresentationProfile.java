package com.example.globe.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Birth-locked underground roster for V4 worlds.
 *
 * <p>The four native cave identities are mandatory at every Globe size. Optional caves are
 * recorded only when their provider and explicit descriptor were present at world birth. This
 * keeps a later datapack or mod install from silently changing unexplored cave terrain.</p>
 */
public final class CaveBiomeRepresentationProfile {
    public static final String FORMAT = "cave_representation_v1";

    private static final Map<String, BiomeRoute> MANDATORY = Map.of(
            "minecraft:deep_dark", BiomeRoute.CAVE_DEEP,
            "minecraft:dripstone_caves", BiomeRoute.CAVE_SHALLOW,
            "minecraft:lush_caves", BiomeRoute.CAVE_SHALLOW);
    private static final List<BiomeRoute> CAVE_ROUTES = List.of(
            BiomeRoute.CAVE_SHALLOW, BiomeRoute.CAVE_DEEP);

    private final VanillaBiomeRepresentationProfile.WorldSize worldSize;
    private final Map<BiomeRoute, List<String>> entries;

    private CaveBiomeRepresentationProfile(VanillaBiomeRepresentationProfile.WorldSize worldSize,
                                           Map<BiomeRoute, List<String>> entries) {
        this.worldSize = worldSize;
        EnumMap<BiomeRoute, List<String>> normalized = new EnumMap<>(BiomeRoute.class);
        for (BiomeRoute route : CAVE_ROUTES) {
            List<String> ids = new ArrayList<>(entries.getOrDefault(route, List.of()));
            ids.sort(Comparator.naturalOrder());
            normalized.put(route, List.copyOf(ids));
        }
        this.entries = Map.copyOf(normalized);
        validate();
    }

    public static CaveBiomeRepresentationProfile capture(int radius, BiomeSelectionProfile providerProfile) {
        if (providerProfile == null) throw new IllegalArgumentException("missing provider ticket profile");
        EnumMap<BiomeRoute, List<String>> entries = new EnumMap<>(BiomeRoute.class);
        for (BiomeRoute route : CAVE_ROUTES) {
            entries.put(route, providerProfile.entries(route));
        }
        return new CaveBiomeRepresentationProfile(
                VanillaBiomeRepresentationProfile.WorldSize.forRadius(radius), entries);
    }

    public VanillaBiomeRepresentationProfile.WorldSize worldSize() { return worldSize; }
    public List<String> entries(BiomeRoute route) { return entries.getOrDefault(route, List.of()); }
    public boolean contains(BiomeRoute route, String id) { return entries(route).contains(id); }
    public static Map<String, BiomeRoute> mandatoryIds() { return MANDATORY; }

    /** One deterministic showcase target for each installed optional provider and cave route. */
    public Map<String, BiomeRoute> customShowcaseTargets(long seed) {
        Map<String, List<String>> byProviderRoute = new TreeMap<>();
        for (BiomeRoute route : CAVE_ROUTES) {
            for (String id : entries(route)) {
                BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(id);
                if (descriptor == null || "minecraft".equals(descriptor.provider())) continue;
                byProviderRoute.computeIfAbsent(route.name() + "|" + descriptor.provider(), ignored -> new ArrayList<>()).add(id);
            }
        }
        Map<String, BiomeRoute> targets = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : byProviderRoute.entrySet()) {
            List<String> ids = entry.getValue();
            ids.sort(Comparator.naturalOrder());
            long mixed = mix64(seed ^ entry.getKey().hashCode());
            String id = ids.get((int) Math.floorMod(mixed, ids.size()));
            BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(id);
            targets.put(id, descriptor.routes().contains(BiomeRoute.CAVE_DEEP)
                    ? BiomeRoute.CAVE_DEEP : BiomeRoute.CAVE_SHALLOW);
        }
        return Map.copyOf(targets);
    }

    public String encode() {
        StringBuilder out = new StringBuilder(FORMAT).append('\n').append("SIZE|").append(worldSize.name());
        for (BiomeRoute route : CAVE_ROUTES) {
            for (String id : entries(route)) out.append('\n').append("ENTRY|").append(route.name()).append('|').append(id);
        }
        return out.toString();
    }

    public static CaveBiomeRepresentationProfile decode(String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("missing cave representation profile");
        String[] lines = encoded.split("\\n", -1);
        if (lines.length < 2 || !FORMAT.equals(lines[0])) throw new IllegalArgumentException("unsupported cave representation profile");
        String[] size = lines[1].split("\\|", -1);
        if (size.length != 2 || !"SIZE".equals(size[0])) throw new IllegalArgumentException("missing cave profile size");
        VanillaBiomeRepresentationProfile.WorldSize worldSize = VanillaBiomeRepresentationProfile.WorldSize.valueOf(size[1]);
        EnumMap<BiomeRoute, List<String>> entries = new EnumMap<>(BiomeRoute.class);
        for (BiomeRoute route : CAVE_ROUTES) entries.put(route, new ArrayList<>());
        for (int i = 2; i < lines.length; i++) {
            String[] parts = lines[i].split("\\|", -1);
            if (parts.length != 3 || !"ENTRY".equals(parts[0])) throw new IllegalArgumentException("malformed cave profile row");
            BiomeRoute route = BiomeRoute.valueOf(parts[1]);
            if (!CAVE_ROUTES.contains(route)) throw new IllegalArgumentException("non-cave cave-profile route");
            BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(parts[2]);
            if (descriptor == null || descriptor.terrain() != BiomeDescriptorLedger.Terrain.CAVE
                    || !descriptor.routes().contains(route) || entries.get(route).contains(parts[2])) {
                throw new IllegalArgumentException("invalid cave profile row: " + lines[i]);
            }
            entries.get(route).add(parts[2]);
        }
        return new CaveBiomeRepresentationProfile(worldSize, entries);
    }

    private void validate() {
        for (Map.Entry<String, BiomeRoute> mandatory : MANDATORY.entrySet()) {
            if (!contains(mandatory.getValue(), mandatory.getKey())) {
                throw new IllegalArgumentException("mandatory cave identity unavailable: " + mandatory.getKey());
            }
        }
        for (BiomeRoute route : CAVE_ROUTES) {
            for (String id : entries(route)) {
                BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(id);
                if (descriptor == null || descriptor.terrain() != BiomeDescriptorLedger.Terrain.CAVE
                        || !descriptor.routes().contains(route)) {
                    throw new IllegalArgumentException("invalid cave descriptor entry: " + id);
                }
            }
        }
    }

    private static long mix64(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
