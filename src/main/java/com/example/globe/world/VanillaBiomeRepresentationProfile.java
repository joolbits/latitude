package com.example.globe.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Birth-locked V3 contract for representing vanilla Overworld biome roles in a finite world.
 *
 * <p>Large worlds retain every exact V2 coverage target. Compact worlds retain exact identities
 * with unique gameplay authority, then guarantee one descriptor-verified representative for each
 * climate/terrain role. A representative may be vanilla or custom, but it must already own the
 * exact Latitude route and family in {@link BiomeDescriptorLedger}; names and tags are never used
 * as evidence. Omitted exact IDs and their representatives are saved for truthful Atlas/support
 * reporting.</p>
 */
public final class VanillaBiomeRepresentationProfile {
    public static final String FORMAT = "vanilla_representation_v2";

    public enum WorldSize {
        ITTY_BITTY(3_750, 16), TINY(5_000, 22), SMALL(7_500, 28),
        REGULAR(10_000, Integer.MAX_VALUE), LARGE(15_000, Integer.MAX_VALUE),
        MASSIVE(20_000, Integer.MAX_VALUE);

        private final int radius;
        private final int compactLandBudget;

        WorldSize(int radius, int compactLandBudget) {
            this.radius = radius;
            this.compactLandBudget = compactLandBudget;
        }

        public int radius() { return radius; }
        public int compactLandBudget() { return compactLandBudget; }
        public boolean compact() { return compactLandBudget != Integer.MAX_VALUE; }

        public static WorldSize forRadius(int radius) {
            WorldSize nearest = ITTY_BITTY;
            for (WorldSize size : values()) {
                if (Math.abs(size.radius - radius) < Math.abs(nearest.radius - radius)) nearest = size;
            }
            return nearest;
        }
    }

    public enum Tier { MANDATORY_EXACT, REPRESENTATION_FAMILY, OPTIONAL_EXACT }

    public enum Compromise { APPROVED_GAMEPLAY_COUNTERPART, REPRESENTATION_FAMILY }

    public record Omission(String representativeId, Compromise compromise) {
        public Omission {
            if (representativeId == null || representativeId.isBlank() || compromise == null) {
                throw new IllegalArgumentException("invalid omission");
            }
        }
    }

    /**
     * Closed equivalence groups. Candidate IDs are explicit: sharing a descriptor family alone is
     * never enough to become a replacement. In particular, BOP Lush Savanna may represent the
     * lowland savanna landscape, but a separate vanilla upland-savanna group preserves acacia.
     */
    private enum Role {
        TROPICAL_JUNGLE(BiomeRoute.TROPICAL_HUMID_LOWLAND, Compromise.REPRESENTATION_FAMILY,
                ids("minecraft:bamboo_jungle", "minecraft:jungle", "minecraft:sparse_jungle")),
        TEMPERATE_BIRCH(BiomeRoute.TEMPERATE_LOWLAND, Compromise.APPROVED_GAMEPLAY_COUNTERPART,
                ids("minecraft:birch_forest", "minecraft:old_growth_birch_forest")),
        TEMPERATE_FOREST(BiomeRoute.TEMPERATE_LOWLAND, Compromise.REPRESENTATION_FAMILY,
                ids("minecraft:dark_forest", "minecraft:flower_forest", "minecraft:forest")),
        TEMPERATE_PLAINS(BiomeRoute.TEMPERATE_LOWLAND, Compromise.REPRESENTATION_FAMILY,
                ids("minecraft:plains", "minecraft:sunflower_plains"),
                ids("biomesoplenty:pasture", "biomesoplenty:prairie")),
        TEMPERATE_TAIGA(BiomeRoute.TEMPERATE_LOWLAND, Compromise.APPROVED_GAMEPLAY_COUNTERPART,
                ids("minecraft:old_growth_pine_taiga", "minecraft:taiga")),
        TEMPERATE_WETLAND(BiomeRoute.TEMPERATE_WETLAND, Compromise.REPRESENTATION_FAMILY,
                ids("minecraft:swamp")),
        TEMPERATE_UPLAND(BiomeRoute.TEMPERATE_UPLAND, Compromise.REPRESENTATION_FAMILY,
                ids("minecraft:grove", "minecraft:meadow",
                        "minecraft:stony_peaks", "minecraft:windswept_forest",
                        "minecraft:windswept_gravelly_hills", "minecraft:windswept_hills")),
        WARM_SAVANNA(BiomeRoute.WARM_TRANSITION, Compromise.REPRESENTATION_FAMILY,
                ids("minecraft:savanna"), ids("biomesoplenty:lush_savanna")),
        WARM_SAVANNA_VARIANTS(BiomeRoute.WARM_TRANSITION, Compromise.REPRESENTATION_FAMILY,
                ids("minecraft:savanna_plateau", "minecraft:windswept_savanna"),
                ids("minecraft:savanna", "biomesoplenty:lush_savanna"), true),
        BADLANDS_LOWLAND(BiomeRoute.ARID_LOWLAND, Compromise.APPROVED_GAMEPLAY_COUNTERPART,
                ids("minecraft:badlands", "minecraft:wooded_badlands")),
        BADLANDS_UPLAND_VARIANT(BiomeRoute.ARID_LOWLAND, Compromise.REPRESENTATION_FAMILY,
                ids("minecraft:eroded_badlands"),
                ids("minecraft:badlands", "minecraft:wooded_badlands"), true),
        DESERT(BiomeRoute.ARID_LOWLAND, Compromise.REPRESENTATION_FAMILY,
                ids("minecraft:desert")),
        SUBPOLAR_TAIGA(BiomeRoute.SUBPOLAR_LOWLAND, Compromise.APPROVED_GAMEPLAY_COUNTERPART,
                ids("minecraft:old_growth_spruce_taiga", "minecraft:snowy_taiga")),
        POLAR_LOWLAND(BiomeRoute.POLAR_LOWLAND, Compromise.REPRESENTATION_FAMILY,
                ids("minecraft:ice_spikes", "minecraft:snowy_plains")),
        COLD_UPLAND(BiomeRoute.COLD_UPLAND, Compromise.REPRESENTATION_FAMILY,
                ids("minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:snowy_slopes"));

        private final BiomeRoute route;
        private final Compromise compromise;
        private final Set<String> vanillaMembers;
        private final Set<String> candidates;

        Role(BiomeRoute route, Compromise compromise, Set<String> vanillaMembers) {
            this(route, compromise, vanillaMembers, Set.of());
        }

        Role(BiomeRoute route, Compromise compromise, Set<String> vanillaMembers,
             Set<String> customCandidates) {
            this.route = route;
            this.compromise = compromise;
            this.vanillaMembers = vanillaMembers;
            LinkedHashSet<String> all = new LinkedHashSet<>(vanillaMembers);
            all.addAll(customCandidates);
            this.candidates = Set.copyOf(all);
        }

        Role(BiomeRoute route, Compromise compromise, Set<String> vanillaMembers,
             Set<String> explicitCandidates, boolean useExplicitCandidates) {
            this.route = route;
            this.compromise = compromise;
            this.vanillaMembers = vanillaMembers;
            if (!useExplicitCandidates) throw new IllegalArgumentException("explicit candidate flag required");
            this.candidates = Set.copyOf(explicitCandidates);
        }
    }

    /** Exact identities whose unique resource/mechanic promise has no approved replacement. */
    private static final Set<String> MANDATORY_LAND = Set.of(
            "minecraft:bamboo_jungle", "minecraft:dark_forest",
            "minecraft:desert", "minecraft:flower_forest", "minecraft:snowy_plains",
            "minecraft:snowy_slopes", "minecraft:swamp");

    /**
     * Verified gameplay counterparts for Cherry Grove in compact worlds. Terralith 2.6.4's
     * Sakura Grove and Sakura Valley biome definitions both place its sakura/cherry_trees
     * features, whose configured trees use minecraft:cherry_log and minecraft:cherry_leaves.
     */
    private static final Map<String, BiomeRoute> CHERRY_GAMEPLAY_COUNTERPARTS = Map.of(
            "terralith:sakura_grove", BiomeRoute.TEMPERATE_LOWLAND,
            "terralith:sakura_valley", BiomeRoute.TEMPERATE_LOWLAND);

    /** Native underground authority is classified, but deliberately outside this surface slice. */
    private static final Set<String> NATIVE_UNDERGROUND = Set.of(
            "minecraft:deep_dark", "minecraft:dripstone_caves", "minecraft:lush_caves");

    private final WorldSize worldSize;
    private final Map<String, BiomeRoute> landTargets;
    private final Map<String, VanillaSurfaceWaterCoveragePlan.Route> surfaceWaterTargets;
    private final Map<String, Omission> omittedExactIds;
    private final Map<String, Tier> targetTiers;

    private VanillaBiomeRepresentationProfile(
            WorldSize worldSize,
            Map<String, BiomeRoute> landTargets,
            Map<String, VanillaSurfaceWaterCoveragePlan.Route> surfaceWaterTargets,
            Map<String, Omission> omittedExactIds,
            Map<String, Tier> targetTiers) {
        this.worldSize = worldSize;
        this.landTargets = sortedCopy(landTargets);
        this.surfaceWaterTargets = sortedCopy(surfaceWaterTargets);
        this.omittedExactIds = sortedCopy(omittedExactIds);
        this.targetTiers = sortedCopy(targetTiers);
        validate();
    }

    public static VanillaBiomeRepresentationProfile capture(
            int radius, long worldSeed, BiomeSelectionProfile providerProfile) {
        if (providerProfile == null) throw new IllegalArgumentException("missing provider profile");
        WorldSize size = WorldSize.forRadius(radius);
        Map<String, BiomeRoute> fullLand = VanillaBiomeCoveragePlan.requiredRoutes();
        if (!size.compact()) {
            Map<String, Tier> tiers = new LinkedHashMap<>();
            for (String id : fullLand.keySet()) {
                tiers.put(id, MANDATORY_LAND.contains(id) ? Tier.MANDATORY_EXACT : Tier.OPTIONAL_EXACT);
            }
            return new VanillaBiomeRepresentationProfile(size, fullLand,
                    VanillaSurfaceWaterCoveragePlan.requirements(), Map.of(), tiers);
        }

        Map<String, BiomeRoute> targets = new LinkedHashMap<>();
        Map<String, Tier> tiers = new LinkedHashMap<>();
        for (String id : sorted(MANDATORY_LAND)) {
            BiomeRoute route = fullLand.get(id);
            if (route == null || !providerProfile.contains(route, id)) {
                throw new IllegalArgumentException("mandatory exact biome unavailable: " + id);
            }
            targets.put(id, route);
            tiers.put(id, Tier.MANDATORY_EXACT);
        }

        String cherryRepresentative = chooseCherryRepresentative(providerProfile, worldSeed);
        if (cherryRepresentative == null) {
            BiomeRoute route = fullLand.get("minecraft:cherry_grove");
            if (route == null || !providerProfile.contains(route, "minecraft:cherry_grove")) {
                throw new IllegalArgumentException("Cherry Grove gameplay identity unavailable");
            }
            cherryRepresentative = "minecraft:cherry_grove";
            targets.put(cherryRepresentative, route);
            tiers.put(cherryRepresentative, Tier.MANDATORY_EXACT);
        } else {
            targets.put(cherryRepresentative, CHERRY_GAMEPLAY_COUNTERPARTS.get(cherryRepresentative));
            tiers.put(cherryRepresentative, Tier.REPRESENTATION_FAMILY);
        }

        Map<Role, String> representatives = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            String representative = chooseRepresentative(providerProfile, role, worldSeed, targets);
            if (representative == null) {
                throw new IllegalArgumentException("no descriptor-verified representative for " + role);
            }
            representatives.put(role, representative);
            targets.putIfAbsent(representative, role.route);
            tiers.putIfAbsent(representative, Tier.REPRESENTATION_FAMILY);
        }

        Map<String, Omission> omitted = new LinkedHashMap<>();
        for (Map.Entry<String, BiomeRoute> exact : fullLand.entrySet()) {
            if (targets.containsKey(exact.getKey())) continue;
            if ("minecraft:cherry_grove".equals(exact.getKey())) {
                omitted.put(exact.getKey(), new Omission(cherryRepresentative,
                        Compromise.APPROVED_GAMEPLAY_COUNTERPART));
                continue;
            }
            Role role = roleFor(exact.getKey());
            String representative = role == null ? null : representatives.get(role);
            if (representative == null) {
                throw new IllegalStateException("unclassified compact omission: " + exact.getKey());
            }
            omitted.put(exact.getKey(), new Omission(representative, role.compromise));
        }
        return new VanillaBiomeRepresentationProfile(size, targets,
                VanillaSurfaceWaterCoveragePlan.requirements(), omitted, tiers);
    }

    public WorldSize worldSize() { return worldSize; }
    public Map<String, BiomeRoute> landTargets() { return landTargets; }
    public Map<String, VanillaSurfaceWaterCoveragePlan.Route> surfaceWaterTargets() {
        return surfaceWaterTargets;
    }
    public Map<String, Omission> omittedExactIds() { return omittedExactIds; }
    public Map<String, Tier> targetTiers() { return targetTiers; }
    public static Set<String> mandatoryLandIds() { return MANDATORY_LAND; }
    public static Set<String> nativeUndergroundIds() { return NATIVE_UNDERGROUND; }

    public boolean hasCherryGameplayRepresentation() {
        if (landTargets.containsKey("minecraft:cherry_grove")) return true;
        Omission omission = omittedExactIds.get("minecraft:cherry_grove");
        return omission != null && omission.compromise() == Compromise.APPROVED_GAMEPLAY_COUNTERPART
                && CHERRY_GAMEPLAY_COUNTERPARTS.get(omission.representativeId())
                == landTargets.get(omission.representativeId());
    }

    public String encode() {
        StringBuilder out = new StringBuilder(FORMAT).append('\n').append("SIZE|").append(worldSize.name());
        for (Map.Entry<String, BiomeRoute> entry : landTargets.entrySet()) {
            out.append('\n').append("LAND|").append(entry.getValue().name()).append('|')
                    .append(targetTiers.get(entry.getKey()).name()).append('|').append(entry.getKey());
        }
        for (Map.Entry<String, VanillaSurfaceWaterCoveragePlan.Route> entry : surfaceWaterTargets.entrySet()) {
            out.append('\n').append("WATER|").append(entry.getValue().name()).append('|').append(entry.getKey());
        }
        for (Map.Entry<String, Omission> entry : omittedExactIds.entrySet()) {
            out.append('\n').append("OMIT|").append(entry.getValue().compromise().name())
                    .append('|').append(entry.getKey()).append('|')
                    .append(entry.getValue().representativeId());
        }
        return out.toString();
    }

    public static VanillaBiomeRepresentationProfile decode(String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("missing representation profile");
        String[] lines = encoded.split("\\n", -1);
        if (lines.length < 2 || !FORMAT.equals(lines[0])) {
            throw new IllegalArgumentException("unsupported representation profile");
        }
        String[] sizeRow = lines[1].split("\\|", -1);
        if (sizeRow.length != 2 || !"SIZE".equals(sizeRow[0])) {
            throw new IllegalArgumentException("missing representation size");
        }
        WorldSize size = WorldSize.valueOf(sizeRow[1]);
        Map<String, BiomeRoute> land = new LinkedHashMap<>();
        Map<String, VanillaSurfaceWaterCoveragePlan.Route> water = new LinkedHashMap<>();
        Map<String, Omission> omitted = new LinkedHashMap<>();
        Map<String, Tier> tiers = new LinkedHashMap<>();
        for (int i = 2; i < lines.length; i++) {
            String[] parts = lines[i].split("\\|", -1);
            switch (parts[0]) {
                case "LAND" -> {
                    if (parts.length != 4) throw new IllegalArgumentException("malformed LAND row");
                    putUnique(land, parts[3], BiomeRoute.valueOf(parts[1]));
                    putUnique(tiers, parts[3], Tier.valueOf(parts[2]));
                }
                case "WATER" -> {
                    if (parts.length != 3) throw new IllegalArgumentException("malformed WATER row");
                    putUnique(water, parts[2], VanillaSurfaceWaterCoveragePlan.Route.valueOf(parts[1]));
                }
                case "OMIT" -> {
                    if (parts.length != 4) throw new IllegalArgumentException("malformed OMIT row");
                    putUnique(omitted, parts[2],
                            new Omission(parts[3], Compromise.valueOf(parts[1])));
                }
                default -> throw new IllegalArgumentException("unknown representation row");
            }
        }
        return new VanillaBiomeRepresentationProfile(size, land, water, omitted, tiers);
    }

    private void validate() {
        if (!surfaceWaterTargets.equals(VanillaSurfaceWaterCoveragePlan.requirements())) {
            throw new IllegalArgumentException("surface/water hard authorities must remain exact");
        }
        for (Map.Entry<String, BiomeRoute> entry : landTargets.entrySet()) {
            BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(entry.getKey());
            if (descriptor == null || !descriptor.routes().contains(entry.getValue())) {
                throw new IllegalArgumentException("invalid land representative: " + entry.getKey());
            }
            if (!targetTiers.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("missing target tier: " + entry.getKey());
            }
        }
        if (!landTargets.keySet().containsAll(MANDATORY_LAND)) {
            throw new IllegalArgumentException("mandatory exact land identity omitted");
        }
        if (!hasCherryGameplayRepresentation()) {
            throw new IllegalArgumentException("Cherry Grove gameplay identity is unrepresented");
        }
        for (Map.Entry<String, Omission> omission : omittedExactIds.entrySet()) {
            if ("minecraft:cherry_grove".equals(omission.getKey())) {
                if (omission.getValue().compromise() != Compromise.APPROVED_GAMEPLAY_COUNTERPART
                        || CHERRY_GAMEPLAY_COUNTERPARTS.get(omission.getValue().representativeId())
                        != landTargets.get(omission.getValue().representativeId())) {
                    throw new IllegalArgumentException("invalid Cherry Grove counterpart");
                }
                continue;
            }
            Role role = roleFor(omission.getKey());
            if (!VanillaBiomeCoveragePlan.requiredRoutes().containsKey(omission.getKey())
                    || landTargets.containsKey(omission.getKey())
                    || role == null
                    || omission.getValue().compromise() != role.compromise
                    || !role.candidates.contains(omission.getValue().representativeId())
                    || !landTargets.containsKey(omission.getValue().representativeId())
                    || landTargets.get(omission.getValue().representativeId()) != role.route) {
                throw new IllegalArgumentException("invalid compact omission: " + omission.getKey());
            }
        }
        Set<String> accounted = new LinkedHashSet<>(landTargets.keySet());
        accounted.addAll(omittedExactIds.keySet());
        if (!accounted.containsAll(VanillaBiomeCoveragePlan.requiredRoutes().keySet())) {
            throw new IllegalArgumentException("unclassified vanilla surface identity");
        }
    }

    private static String chooseRepresentative(BiomeSelectionProfile profile, Role role, long seed,
                                               Map<String, BiomeRoute> occupiedTargets) {
        List<String> candidates = role.candidates.stream()
                .filter(id -> profile.contains(role.route, id))
                .filter(id -> {
                    BiomeRoute occupiedRoute = occupiedTargets.get(id);
                    return occupiedRoute == null || occupiedRoute == role.route;
                })
                .sorted()
                .toList();
        if (candidates.isEmpty()) return null;
        for (String candidate : candidates) {
            if (occupiedTargets.get(candidate) == role.route) return candidate;
        }
        // Reuse a mandatory exact province when it already satisfies the role. This keeps the
        // compact budget honest instead of reserving a second biome merely to relabel the role.
        for (String candidate : candidates) {
            if (MANDATORY_LAND.contains(candidate)) return candidate;
        }
        BiomeProviderSelectionPolicy.Pool pool = BiomeProviderSelectionPolicy.createPool(candidates);
        return BiomeProviderSelectionPolicy.selectId(pool, seed, role.ordinal() * 8_192,
                role.ordinal() * -12_288, 2, "coverage_" + role.name(), 0x7f4a7c159e3779b9L);
    }

    private static Role roleFor(String id) {
        for (Role role : Role.values()) {
            if (role.vanillaMembers.contains(id)) return role;
        }
        return null;
    }

    public static boolean areApprovedEquivalents(String omittedId, Omission omission) {
        if ("minecraft:cherry_grove".equals(omittedId)) {
            return omission != null
                    && omission.compromise() == Compromise.APPROVED_GAMEPLAY_COUNTERPART
                    && CHERRY_GAMEPLAY_COUNTERPARTS.containsKey(omission.representativeId());
        }
        Role role = roleFor(omittedId);
        return role != null && omission != null && omission.compromise() == role.compromise
                && role.candidates.contains(omission.representativeId());
    }

    private static long stableOrder(long seed, WorldSize size, String id) {
        long value = seed ^ ((long) size.ordinal() << 56) ^ id.hashCode() * 0x9e3779b97f4a7c15L;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static String chooseCherryRepresentative(BiomeSelectionProfile profile, long seed) {
        List<String> candidates = CHERRY_GAMEPLAY_COUNTERPARTS.entrySet().stream()
                .filter(entry -> profile.contains(entry.getValue(), entry.getKey()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (candidates.isEmpty()) return null;
        BiomeProviderSelectionPolicy.Pool pool = BiomeProviderSelectionPolicy.createPool(candidates);
        return BiomeProviderSelectionPolicy.selectId(pool, seed, 0x43484552, -0x52594752,
                2, "coverage_CHERRY_GAMEPLAY", 0x243f6a8885a308d3L);
    }

    private static <T> Map<String, T> sortedCopy(Map<String, T> values) {
        Map<String, T> sorted = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(sorted);
    }

    private static List<String> sorted(Set<String> values) {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    private static Set<String> ids(String... values) { return Set.of(values); }

    private static <T> void putUnique(Map<String, T> map, String key, T value) {
        if (map.putIfAbsent(key, value) != null) throw new IllegalArgumentException("duplicate profile row: " + key);
    }
}
