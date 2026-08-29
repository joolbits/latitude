package com.example.globe.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Shared post-generation structure footprint rules for Latitude worlds. */
public final class StructureSitingPolicy {
    public static final int EAST_WEST_DANGER_DISTANCE_BLOCKS = 100;
    public static final int FOOTPRINT_SAMPLE_STEP_BLOCKS = 16;

    public record FootprintSample(int x, int z) {
    }

    private StructureSitingPolicy() {
    }

    /** True when any part of the generated footprint enters the east/west danger zone. */
    public static boolean intersectsEastWestDangerZone(int minX, int maxX, int worldRadius) {
        int radius = Math.max(0, worldRadius);
        int safeMaxAbsX = Math.max(0, radius - EAST_WEST_DANGER_DISTANCE_BLOCKS);
        int lowX = Math.min(minX, maxX);
        int highX = Math.max(minX, maxX);
        return lowX <= -safeMaxAbsX || highX >= safeMaxAbsX;
    }

    /** Structures covered by the badlands-desolation ruling must keep their whole footprint out. */
    public static boolean requiresBadlandsFreeFootprint(String structurePath, boolean village) {
        return village || VillageBiomeAdmissionPolicy.shouldRefuseStructureInVillageFreeBiome(
                structurePath, "minecraft:badlands");
    }

    public static boolean shouldRejectBadlandsFootprint(
            String structurePath,
            boolean village,
            Collection<String> sampledBiomeIds) {
        if (!requiresBadlandsFreeFootprint(structurePath, village) || sampledBiomeIds == null) {
            return false;
        }
        for (String biomeId : sampledBiomeIds) {
            if (VillageBiomeAdmissionPolicy.isVillageFreeBiome(biomeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A woodland mansion is the one structure whose siting Latitude restricts to vanilla biomes.
     *
     * <p>Its biome tag is the smallest of any surface structure (dark forest and pale garden), so
     * a third-party pack that adds its own biome to that tag hands the mansion a column whose
     * surface the pack owns and whose climate Latitude never classified. The mansion then lands
     * somewhere that reads as a placement bug rather than a rare discovery. Checking the biome's
     * namespace is deliberate: it admits every vanilla mansion biome, present and future, and
     * refuses only columns a pack introduced.
     *
     * <p>Gate first, decide second — the same shape as the badlands ruling above — so only the
     * covered structure pays for the extra final-biome sample.
     */
    public static boolean requiresVanillaBiomeSiting(String structureNamespace, String structurePath) {
        return "minecraft".equals(structureNamespace) && "mansion".equals(structurePath);
    }

    public static boolean shouldRejectCustomBiomeSiting(
            String structureNamespace,
            String structurePath,
            String biomeNamespace) {
        return requiresVanillaBiomeSiting(structureNamespace, structurePath)
                && biomeNamespace != null
                && !"minecraft".equals(biomeNamespace);
    }

    /**
     * Samples the complete bounding footprint on a chunk-scale grid, always including its edges
     * and center. This is biome-source math only; callers do not load chunks.
     */
    public static List<FootprintSample> footprintSamples(
            int minX,
            int maxX,
            int minZ,
            int maxZ) {
        List<Integer> xs = axisSamples(minX, maxX);
        List<Integer> zs = axisSamples(minZ, maxZ);
        LinkedHashSet<FootprintSample> samples = new LinkedHashSet<>();
        for (int x : xs) {
            for (int z : zs) {
                samples.add(new FootprintSample(x, z));
            }
        }
        return List.copyOf(samples);
    }

    private static List<Integer> axisSamples(int first, int second) {
        int minimum = Math.min(first, second);
        int maximum = Math.max(first, second);
        LinkedHashSet<Integer> samples = new LinkedHashSet<>();
        samples.add(minimum);
        for (long value = minimum;
                value <= maximum;
                value += FOOTPRINT_SAMPLE_STEP_BLOCKS) {
            samples.add((int) value);
            if (value > (long) maximum - FOOTPRINT_SAMPLE_STEP_BLOCKS) {
                break;
            }
        }
        samples.add((int) (((long) minimum + maximum) / 2L));
        samples.add(maximum);
        return new ArrayList<>(samples);
    }
}
