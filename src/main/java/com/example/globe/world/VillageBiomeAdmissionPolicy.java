package com.example.globe.world;

import java.util.Locale;

/**
 * Dependency-free badlands structure-admission boundary.
 *
 * <p>Badlands country is intentionally desolate: villages, desert-declared surface structures,
 * and surface outposts do not begin there (maintainer ruling, 2026-08-15). Matching uses the biome
 * path instead of the broad arid descriptor family because desert shares that family and keeps its
 * normal structures.
 */
public final class VillageBiomeAdmissionPolicy {

    private VillageBiomeAdmissionPolicy() {
    }

    /** True when the biome is badlands country, including reviewed provider variants. */
    public static boolean isVillageFreeBiome(String biomeId) {
        if (biomeId == null) {
            return false;
        }
        String id = biomeId.toLowerCase(Locale.ROOT);
        int namespaceEnd = id.indexOf(':');
        String path = namespaceEnd >= 0 ? id.substring(namespaceEnd + 1) : id;
        return path.contains("badlands") || path.contains("mesa");
    }

    /** True when a structure identifies itself as a desert surface structure. */
    public static boolean isDesertDeclaredStructure(String structurePath) {
        return structurePath != null
                && structurePath.toLowerCase(Locale.ROOT).contains("desert");
    }

    /** Surface pillager outposts are covered; underground or mining outposts are not. */
    public static boolean isSurfaceOutpostStructure(String structurePath) {
        if (structurePath == null) {
            return false;
        }
        String path = structurePath.toLowerCase(Locale.ROOT);
        return path.contains("outpost")
                && !path.contains("underground")
                && !path.contains("mining");
    }

    /** True when the named structure conflicts with the badlands desolation rule. */
    public static boolean shouldRefuseStructureInVillageFreeBiome(
            String structurePath,
            String biomeId) {
        return isVillageFreeBiome(biomeId)
                && (isDesertDeclaredStructure(structurePath)
                        || isSurfaceOutpostStructure(structurePath));
    }
}
