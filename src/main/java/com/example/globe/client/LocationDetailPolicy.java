package com.example.globe.client;

import java.util.Locale;

/**
 * Dependency-free policy for the optional biome/zone detail rendered as one HUD unit.
 */
public final class LocationDetailPolicy {
    public static final Mode DEFAULT_MODE = Mode.OFF;
    public static final String COMBINED_SEPARATOR = " \u00b7 ";

    public enum Mode {
        OFF("Off", false, false),
        BIOME("Biome", true, false),
        ZONE("Zone", false, true),
        BIOME_AND_ZONE("Biome + Zone", true, true);

        private final String label;
        private final boolean biome;
        private final boolean zone;

        Mode(String label, boolean biome, boolean zone) {
            this.label = label;
            this.biome = biome;
            this.zone = zone;
        }

        public String label() {
            return label;
        }

        public boolean includesBiome() {
            return biome;
        }

        public boolean includesZone() {
            return zone;
        }
    }

    private LocationDetailPolicy() {
    }

    /**
     * Derives the four-state mode from the two persisted booleans. This keeps old configs compatible:
     * their existing zone flag still maps false to Off and true to Zone because the new biome flag
     * defaults to false when absent.
     */
    public static Mode fromPersistedFlags(boolean displayBiome, boolean displayZone) {
        if (displayBiome && displayZone) {
            return Mode.BIOME_AND_ZONE;
        }
        if (displayBiome) {
            return Mode.BIOME;
        }
        if (displayZone) {
            return Mode.ZONE;
        }
        return DEFAULT_MODE;
    }

    /**
     * Produces the selected detail as one string so render, bounds, follow, and detach paths cannot
     * split biome and zone into independently positioned elements.
     */
    public static String compose(Mode mode, String biomeLabel, String zoneLabel) {
        Mode selected = mode == null ? DEFAULT_MODE : mode;
        return switch (selected) {
            case OFF -> null;
            case BIOME -> usableLabel(biomeLabel);
            case ZONE -> usableLabel(zoneLabel);
            case BIOME_AND_ZONE ->
                    usableLabel(biomeLabel) + COMBINED_SEPARATOR + usableLabel(zoneLabel);
        };
    }

    /**
     * Converts a namespaced biome id into deterministic player-facing title case.
     */
    public static String titleCaseBiomeId(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return "Unknown";
        }

        String trimmed = biomeId.trim();
        int namespaceSeparator = trimmed.indexOf(':');
        String path = namespaceSeparator >= 0 ? trimmed.substring(namespaceSeparator + 1) : trimmed;
        String[] words = path.split("[_\\-/\\s]+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            String normalized = word.toLowerCase(Locale.ROOT);
            result.append(Character.toUpperCase(normalized.charAt(0)));
            if (normalized.length() > 1) {
                result.append(normalized.substring(1));
            }
        }
        return result.isEmpty() ? "Unknown" : result.toString();
    }

    public static String biomeLabel(String biomeId, boolean showCustomSource) {
        String biome = titleCaseBiomeId(biomeId);
        if (!showCustomSource) {
            return biome;
        }
        String provider = customProviderLabel(biomeId);
        return provider == null ? biome : biome + COMBINED_SEPARATOR + provider;
    }

    /**
     * Makes the Studio's coherent vanilla sample demonstrate the source toggle without changing
     * normal runtime labels, where vanilla remains intentionally compact and unlabelled.
     */
    public static String studioPreviewBiomeLabel(String biomeId, boolean showSource) {
        String biome = biomeLabel(biomeId, showSource);
        if (!showSource || biomeId == null) {
            return biome;
        }
        String trimmed = biomeId.trim();
        int separator = trimmed.indexOf(':');
        String namespace = separator <= 0
                ? ""
                : trimmed.substring(0, separator).toLowerCase(Locale.ROOT);
        return "minecraft".equals(namespace)
                ? biome + COMBINED_SEPARATOR + "VANILLA"
                : biome;
    }

    public static String customProviderLabel(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return null;
        }
        String trimmed = biomeId.trim();
        int separator = trimmed.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        String namespace = trimmed.substring(0, separator).toLowerCase(Locale.ROOT);
        if ("minecraft".equals(namespace)) {
            return null;
        }
        return switch (namespace) {
            case "biomesoplenty" -> "BIOMES O' PLENTY";
            case "terralith" -> "TERRALITH";
            case "promenade" -> "PROMENADE";
            case "regions_unexplored", "regionsunexplored" -> "REGIONS UNEXPLORED";
            default -> titleCaseBiomeId(namespace).toUpperCase(Locale.ROOT);
        };
    }

    private static String usableLabel(String label) {
        return label == null || label.isBlank() ? "Unknown" : label;
    }
}
