package com.example.globe.world;

import java.util.Locale;

/**
 * Dependency-free biome admission boundary for village structure origins.
 *
 * <p>Badlands country admits no village of any variety (maintainer ruling, 2026-08-15). Badlands
 * is meant to read as desolate, and every badlands variant shares the ARID descriptor family with
 * desert, so the named-variant comparison in {@code LatitudeBiomes.villageVariantVsBiomeMismatch}
 * could never separate them on its own: an arid-declared village matched arid terrain and was
 * admitted. This predicate is the separate, structure-agnostic authority, so a village is refused
 * whatever it calls itself — including a provider village whose name declares no variant at all.
 *
 * <p>Matching is by biome path rather than by descriptor family precisely because the family is
 * shared with desert, which keeps its villages. Provider biomes that name themselves badlands are
 * badlands country too.
 */
public final class VillageBiomeAdmissionPolicy {

    private VillageBiomeAdmissionPolicy() {
    }

    /** True when the biome is badlands country, which admits no village of any variety. */
    public static boolean isVillageFreeBiome(String biomeId) {
        if (biomeId == null) {
            return false;
        }
        String id = biomeId.toLowerCase(Locale.ROOT);
        int namespaceEnd = id.indexOf(':');
        String path = namespaceEnd >= 0 ? id.substring(namespaceEnd + 1) : id;
        return path.contains("badlands") || path.contains("mesa");
    }

    /**
     * A structure whose own name declares a desert identity — desert_pyramid, desert_outpost,
     * fortified_desert_village, rubble_desert, ruined_portal_desert. Every such structure in this
     * stack is desert-only by its own biome tag; one observed in badlands got there through the
     * start-height sampling divergence, never through a tag, so refusing it here restores what the
     * tags already promise.
     */
    public static boolean isDesertDeclaredStructure(String structurePath) {
        return structurePath != null
                && structurePath.toLowerCase(Locale.ROOT).contains("desert");
    }

    /**
     * Surface pillager civilization — the outpost family. Deliberately excludes underground
     * mining outposts, which are cave content and not part of the surface desolation ruling.
     */
    public static boolean isSurfaceOutpostStructure(String structurePath) {
        if (structurePath == null) {
            return false;
        }
        String path = structurePath.toLowerCase(Locale.ROOT);
        return path.contains("outpost")
                && !path.contains("underground")
                && !path.contains("mining");
    }

    /**
     * Badlands country stays desolate for non-village structures too (maintainer ruling,
     * 2026-08-16): desert-declared structures and surface outposts are refused when Latitude's
     * final surface biome at the start column is badlands. This covers both failure routes seen
     * live — desert-only structures arriving via sampling divergence, and vanilla pillager
     * outposts arriving legally through a provider tag that adds badlands. Everything else
     * (mesa mineshafts, plain ruined portals, badlands rubble, underground structures) keeps
     * its badlands placement.
     */
    public static boolean shouldRefuseStructureInVillageFreeBiome(
            String structurePath,
            String biomeId) {
        return isVillageFreeBiome(biomeId)
                && (isDesertDeclaredStructure(structurePath)
                        || isSurfaceOutpostStructure(structurePath));
    }
}
