package com.example.globe.world;

import com.example.globe.util.ValueNoise2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Selects a biome in two coherent stages: provider namespace first, then biome identity inside
 * that provider. Equal provider fields prevent a long optional-mod tag from crowding out a
 * smaller provider, while independent identity fields keep every admitted biome reachable.
 */
public final class BiomeProviderSelectionPolicy {
    static final int PROVIDER_COHERENCE_BLOCKS = 1536;
    static final int BIOME_COHERENCE_BLOCKS = 768;

    private static final long BAND_SALT = 0x9E3779B97F4A7C15L;
    private static final long PROVIDER_SALT = 0x50524F5649444552L; // "PROVIDER"
    private static final long BIOME_SALT = 0x42494F4D45504943L; // "BIOMEPIC"

    private BiomeProviderSelectionPolicy() {
    }

    public static Pool createPool(List<String> biomeIds) {
        if (biomeIds == null || biomeIds.isEmpty()) {
            throw new IllegalArgumentException("biomeIds must not be empty");
        }

        List<String> sorted = new ArrayList<>(biomeIds);
        sorted.sort(String::compareTo);
        List<String> ids = List.copyOf(sorted);
        Map<String, List<Integer>> byNamespace = new TreeMap<>();
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            byNamespace.computeIfAbsent(namespace(id), ignored -> new ArrayList<>()).add(index);
        }

        List<ProviderGroup> providers = new ArrayList<>(byNamespace.size());
        for (Map.Entry<String, List<Integer>> entry : byNamespace.entrySet()) {
            int[] indices = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
            providers.add(new ProviderGroup(entry.getKey(), indices));
        }
        return new Pool(ids, List.copyOf(providers));
    }

    public static int selectIndex(
            Pool pool,
            long worldSeed,
            int blockX,
            int blockZ,
            int bandIndex,
            String sourceKey,
            long extraSalt) {
        if (pool == null || pool.providers().isEmpty()) {
            throw new IllegalArgumentException("pool must contain at least one provider");
        }

        long bandAuthority = mix64(worldSeed ^ (BAND_SALT * (long) bandIndex));
        ProviderGroup selectedProvider = pool.providers().get(0);
        double bestProviderScore = Double.NEGATIVE_INFINITY;
        for (ProviderGroup provider : pool.providers()) {
            long fieldSeed = bandAuthority ^ PROVIDER_SALT ^ hash64(provider.namespace());
            double score = ValueNoise2D.sampleBlocks(
                    fieldSeed, blockX, blockZ, PROVIDER_COHERENCE_BLOCKS);
            if (score > bestProviderScore) {
                bestProviderScore = score;
                selectedProvider = provider;
            }
        }

        long identityAuthority = mix64(
                worldSeed
                        ^ (BAND_SALT * (long) bandIndex)
                        ^ hash64(sourceKey == null ? "" : sourceKey)
                        ^ extraSalt);
        int selectedIndex = selectedProvider.indices()[0];
        double bestBiomeScore = Double.NEGATIVE_INFINITY;
        for (int index : selectedProvider.indices()) {
            long fieldSeed = identityAuthority ^ BIOME_SALT ^ hash64(pool.ids().get(index));
            double score = ValueNoise2D.sampleBlocks(
                    fieldSeed, blockX, blockZ, BIOME_COHERENCE_BLOCKS);
            if (score > bestBiomeScore) {
                bestBiomeScore = score;
                selectedIndex = index;
            }
        }
        return selectedIndex;
    }

    public static String selectId(
            Pool pool,
            long worldSeed,
            int blockX,
            int blockZ,
            int bandIndex,
            String sourceKey,
            long extraSalt) {
        return pool.ids().get(selectIndex(
                pool, worldSeed, blockX, blockZ, bandIndex, sourceKey, extraSalt));
    }

    private static String namespace(String id) {
        int split = id.indexOf(':');
        return split > 0 ? id.substring(0, split) : "minecraft";
    }

    private static long hash64(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return mix64(hash);
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    public record Pool(List<String> ids, List<ProviderGroup> providers) {
    }

    public record ProviderGroup(String namespace, int[] indices) {
    }
}
