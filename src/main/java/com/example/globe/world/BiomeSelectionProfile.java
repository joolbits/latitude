package com.example.globe.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Immutable birth-time roster for PROVIDER_TICKET_V1. */
public final class BiomeSelectionProfile {
    public static final String FORMAT = "provider_ticket_v1";

    private final List<String> providers;
    private final Map<BiomeRoute, List<String>> entries;

    private BiomeSelectionProfile(List<String> providers, Map<BiomeRoute, List<String>> entries) {
        this.providers = List.copyOf(providers);
        this.entries = Map.copyOf(entries);
    }

    public static BiomeSelectionProfile capture(Collection<String> registryIds) {
        Set<String> active = Set.copyOf(registryIds);
        List<String> validation = BiomeDescriptorLedger.validate(active);
        if (!validation.isEmpty()) throw new IllegalArgumentException(String.join("; ", validation));
        EnumMap<BiomeRoute, List<String>> byRoute = new EnumMap<>(BiomeRoute.class);
        TreeSet<String> providers = new TreeSet<>();
        for (BiomeRoute route : BiomeRoute.values()) {
            List<String> ids = new ArrayList<>();
            for (BiomeDescriptorLedger.Descriptor d : BiomeDescriptorLedger.descriptors()) {
                if (active.contains(d.biomeId()) && d.routes().contains(route)) {
                    ids.add(d.biomeId());
                    providers.add(d.provider());
                }
            }
            ids.sort(Comparator.naturalOrder());
            byRoute.put(route, List.copyOf(ids));
        }
        return new BiomeSelectionProfile(List.copyOf(providers), byRoute);
    }

    public List<String> providers() { return providers; }
    public List<String> entries(BiomeRoute route) { return entries.getOrDefault(route, List.of()); }
    public boolean contains(BiomeRoute route, String id) { return entries(route).contains(id); }

    /** Ordered active providers for one final geography route. */
    public List<String> providers(BiomeRoute route) {
        TreeSet<String> routeProviders = new TreeSet<>();
        for (String id : entries(route)) {
            BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(id);
            if (descriptor != null) routeProviders.add(descriptor.provider());
        }
        return List.copyOf(routeProviders);
    }

    /** The saved roster is never rebuilt. This only reports what an altered modset removed. */
    public List<String> missingIds(Collection<String> availableIds) {
        Set<String> available = Set.copyOf(availableIds);
        List<String> missing = new ArrayList<>();
        for (BiomeRoute route : BiomeRoute.values()) {
            for (String id : entries(route)) if (!available.contains(id) && !missing.contains(id)) missing.add(id);
        }
        missing.sort(Comparator.naturalOrder());
        return List.copyOf(missing);
    }

    /** Canonical save encoding: sorted and independent of registry iteration order. */
    public String encode() {
        StringBuilder out = new StringBuilder(FORMAT);
        for (BiomeRoute route : BiomeRoute.values()) {
            for (String id : entries(route)) out.append('\n').append(route.name()).append('|').append(id);
        }
        return out.toString();
    }

    public static BiomeSelectionProfile decode(String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("missing provider ticket profile");
        String[] lines = encoded.split("\\n", -1);
        if (!FORMAT.equals(lines[0])) throw new IllegalArgumentException("unsupported provider ticket profile");
        EnumMap<BiomeRoute, List<String>> raw = new EnumMap<>(BiomeRoute.class);
        for (BiomeRoute route : BiomeRoute.values()) raw.put(route, new ArrayList<>());
        Set<String> seen = new LinkedHashSet<>();
        TreeSet<String> providers = new TreeSet<>();
        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException("malformed provider ticket row");
            BiomeRoute route = BiomeRoute.valueOf(parts[0]);
            BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(parts[1]);
            if (descriptor == null || !descriptor.routes().contains(route) || !seen.add(lines[i])) throw new IllegalArgumentException("invalid provider ticket row: " + lines[i]);
            raw.get(route).add(parts[1]);
            providers.add(descriptor.provider());
        }
        EnumMap<BiomeRoute, List<String>> sorted = new EnumMap<>(BiomeRoute.class);
        for (BiomeRoute route : BiomeRoute.values()) {
            List<String> ids = raw.get(route);
            ids.sort(Comparator.naturalOrder());
            sorted.put(route, List.copyOf(ids));
        }
        return new BiomeSelectionProfile(List.copyOf(providers), sorted);
    }
}
