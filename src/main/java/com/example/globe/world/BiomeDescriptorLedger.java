package com.example.globe.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Latitude-owned, closed ledger of optional biome placement contracts.
 *
 * <p>A present registry entry is not eligibility.  Only an entry in this ledger can receive a
 * provider ticket.  This is intentionally code-owned for 1.5: datapacks may change Minecraft's
 * tags, but they cannot silently make an unreviewed custom biome a Latitude random-placement
 * candidate.</p>
 */
public final class BiomeDescriptorLedger {
    public enum Terrain { LOWLAND, UPLAND, WETLAND, ARID, CAVE }
    public enum Water { LAND, WETLAND, COASTAL_BRACKISH, UNDERGROUND }
    public enum Family { JUNGLE, FOREST, WETLAND, ARID, SAVANNA, UPLAND, TAIGA, POLAR, CAVE }

    public record Descriptor(
            String biomeId,
            String provider,
            Set<BiomeRoute> routes,
            Terrain terrain,
            Water water,
            Family family,
            boolean structureSensitive) {
        public Descriptor {
            biomeId = requireBiomeId(biomeId);
            provider = requireProvider(provider, biomeId);
            routes = Set.copyOf(routes == null ? Set.of() : routes);
            if (routes.isEmpty()) throw new IllegalArgumentException("descriptor needs at least one route: " + biomeId);
            if (terrain == null || water == null || family == null) throw new IllegalArgumentException("descriptor has null placement fields: " + biomeId);
            boolean caveRoute = routes.contains(BiomeRoute.CAVE_SHALLOW) || routes.contains(BiomeRoute.CAVE_DEEP);
            if (terrain == Terrain.CAVE) {
                if (water != Water.UNDERGROUND || !caveRoute || routes.stream().anyMatch(route -> route != BiomeRoute.CAVE_SHALLOW && route != BiomeRoute.CAVE_DEEP)) {
                    throw new IllegalArgumentException("cave descriptor must own only underground cave routes: " + biomeId);
                }
            } else if (caveRoute || water == Water.UNDERGROUND) {
                throw new IllegalArgumentException("surface descriptor cannot own underground cave routes: " + biomeId);
            }
            if (water != Water.LAND && water != Water.UNDERGROUND && terrain != Terrain.WETLAND) throw new IllegalArgumentException("water descriptor must be wetland terrain: " + biomeId);
            if (terrain == Terrain.WETLAND
                    && !routes.contains(BiomeRoute.TEMPERATE_WETLAND)
                    && !routes.contains(BiomeRoute.SUBPOLAR_WETLAND)) throw new IllegalArgumentException("wetland needs an owned wetland route: " + biomeId);
            if (terrain == Terrain.UPLAND
                    && !routes.contains(BiomeRoute.TEMPERATE_UPLAND)
                    && !routes.contains(BiomeRoute.COLD_UPLAND)) throw new IllegalArgumentException("upland needs an owned upland route: " + biomeId);
            if (terrain == Terrain.ARID && !routes.contains(BiomeRoute.ARID_LOWLAND)) throw new IllegalArgumentException("arid descriptor needs the owned arid-lowland route: " + biomeId);
        }
    }

    private static final List<Descriptor> DESCRIPTORS = List.of(
            // BOP: humid warm land
            d("biomesoplenty:rainforest", r(BiomeRoute.TROPICAL_HUMID_LOWLAND, BiomeRoute.SUBTROPICAL_HUMID_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.JUNGLE),
            d("biomesoplenty:tropics", r(BiomeRoute.TROPICAL_HUMID_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.JUNGLE),
            d("biomesoplenty:fungal_jungle", r(BiomeRoute.TROPICAL_HUMID_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.JUNGLE),
            d("biomesoplenty:subtropics", r(BiomeRoute.SUBTROPICAL_HUMID_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:redwood_forest", r(BiomeRoute.SUBTROPICAL_HUMID_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:mystic_grove", r(BiomeRoute.SUBTROPICAL_HUMID_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            // BOP: temperate land and verified flat wetlands
            d("biomesoplenty:seasonal_forest", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:maple_woods", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:woodland", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:old_growth_woodland", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:forested_field", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:orchard", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:pasture", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:prairie", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:lavender_field", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:overgrown_greens", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:marsh", r(BiomeRoute.TEMPERATE_WETLAND), Terrain.WETLAND, Water.WETLAND, Family.WETLAND),
            d("biomesoplenty:wetland", r(BiomeRoute.TEMPERATE_WETLAND), Terrain.WETLAND, Water.WETLAND, Family.WETLAND),
            // These are cool/temperate inland wetlands.  Warm bayou and floodplain remain
            // excluded until Latitude owns a separate warm-wetland route.
            d("biomesoplenty:bog", r(BiomeRoute.TEMPERATE_WETLAND), Terrain.WETLAND, Water.WETLAND, Family.WETLAND),
            d("biomesoplenty:muskeg", r(BiomeRoute.SUBPOLAR_WETLAND), Terrain.WETLAND, Water.WETLAND, Family.WETLAND),
            d("biomesoplenty:lush_savanna", r(BiomeRoute.WARM_TRANSITION), Terrain.LOWLAND, Water.LAND, Family.SAVANNA),
            d("biomesoplenty:mediterranean_forest", r(BiomeRoute.WARM_TRANSITION), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("biomesoplenty:scrubland", r(BiomeRoute.WARM_TRANSITION), Terrain.LOWLAND, Water.LAND, Family.ARID),
            d("biomesoplenty:shrubland", r(BiomeRoute.WARM_TRANSITION), Terrain.LOWLAND, Water.LAND, Family.ARID),
            d("biomesoplenty:rocky_shrubland", r(BiomeRoute.WARM_TRANSITION), Terrain.LOWLAND, Water.LAND, Family.ARID),
            d("biomesoplenty:dryland", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("biomesoplenty:wasteland", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("biomesoplenty:wasteland_steppe", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("biomesoplenty:lush_desert", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("biomesoplenty:field", r(BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("biomesoplenty:pumpkin_patch", r(BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("biomesoplenty:tundra", r(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("biomesoplenty:snowy_maple_woods", r(BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("biomesoplenty:coniferous_forest", r(BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("biomesoplenty:fir_clearing", r(BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("biomesoplenty:snowy_coniferous_forest", r(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("biomesoplenty:snowy_fir_clearing", r(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("biomesoplenty:auroral_garden", r(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.POLAR),
            d("biomesoplenty:snowblossom_grove", r(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.POLAR),
            d("biomesoplenty:wintry_origin_valley", r(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.POLAR),
            // Terralith: only identities with an explicit placement rationale.  Amethyst Canyon
            // is deliberately absent: it needs a warm-upland route that Latitude does not yet own.
            d("terralith:tropical_jungle", r(BiomeRoute.TROPICAL_HUMID_LOWLAND, BiomeRoute.SUBTROPICAL_HUMID_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.JUNGLE),
            d("terralith:amethyst_rainforest", r(BiomeRoute.TROPICAL_HUMID_LOWLAND, BiomeRoute.SUBTROPICAL_HUMID_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.JUNGLE),
            d("terralith:birch_taiga", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("terralith:lavender_forest", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("terralith:sakura_grove", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("terralith:sakura_valley", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("terralith:blooming_valley", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("terralith:lavender_valley", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("terralith:valley_clearing", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("terralith:moonlight_grove", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("terralith:moonlight_valley", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("terralith:orchid_swamp", r(BiomeRoute.TEMPERATE_WETLAND), Terrain.WETLAND, Water.WETLAND, Family.WETLAND),
            d("terralith:ice_marsh", r(BiomeRoute.SUBPOLAR_WETLAND), Terrain.WETLAND, Water.WETLAND, Family.WETLAND),
            d("terralith:caldera", r(BiomeRoute.TEMPERATE_UPLAND), Terrain.UPLAND, Water.LAND, Family.UPLAND),
            d("terralith:brushland", r(BiomeRoute.WARM_TRANSITION), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("terralith:hot_shrubland", r(BiomeRoute.WARM_TRANSITION), Terrain.LOWLAND, Water.LAND, Family.ARID),
            d("terralith:shrubland", r(BiomeRoute.WARM_TRANSITION), Terrain.LOWLAND, Water.LAND, Family.ARID),
            d("terralith:ancient_sands", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("terralith:lush_desert", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("terralith:sandstone_valley", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("terralith:lush_valley", r(BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("terralith:shield", r(BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("terralith:shield_clearing", r(BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("terralith:yosemite_lowlands", r(BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("terralith:siberian_taiga", r(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("terralith:wintry_forest", r(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("terralith:wintry_lowlands", r(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("terralith:siberian_grove", r(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("terralith:snowy_maple_forest", r(BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("terralith:cold_shrubland", r(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.POLAR),
            d("terralith:snowy_cherry_grove", r(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.POLAR),
            // CliffTree: admitted 2026-08-10 at the maintainer's request ("one of my favorite biome
            // mods"). Before this the mod was entirely inert -- its biomes appeared in five shipped
            // lat_* tag files but had no descriptor, and the ledger is authoritative on every world
            // this build creates, so installing CliffTree changed nothing about the land Latitude
            // painted. Every route below was chosen from the biome's REAL climate in the shipped
            // datapack JSON, not from its name; two of them contradict the name outright and are
            // called out where they sit.
            //
            // Only surface LAND lives here. CliffTree's oceans go through the lat_ocean_* tags (a
            // separate live authority), its caves through the CAVE_* routes below, and its three
            // rivers and two beaches have NO admission path in this build at all -- river selection
            // and pickBeachForBand are hard authorities that consult no tag, so no pack's river or
            // beach can be admitted by data alone. That gap is real and is deliberately left for a
            // follow-up slice; it is not specific to CliffTree.
            // CliffTree's three *_shore biomes are deliberately NOT here. Shores are a hard
            // authority in Latitude, not ledger land: vanilla minecraft:stony_shore, beach,
            // snowy_beach, river and frozen_river have no ledger route either, and Latitude's own
            // isBeachLike() matches any biome whose path contains "shore". Routing them as land
            // would have put coastal identities inland at random temperate/polar locations. They
            // are admitted through the lat_beach_* tags instead. (Corrected 2026-08-10: the prior
            // commit listed all three as LOWLAND land routes.)
            d("clifftree:bog", r(BiomeRoute.TEMPERATE_WETLAND), Terrain.WETLAND, Water.WETLAND, Family.WETLAND),
            d("clifftree:sparse_forest", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            // temperature 2.0 -- maximum heat, identical to vanilla badlands -- despite the
            // "coniferous" name. It is a petrified/fossil-forest-in-badlands aesthetic, not a cold
            // biome, and routing it by name would have put a desert-hot identity in the taiga band.
            d("clifftree:coniferous_badlands", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("clifftree:oasis", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("clifftree:shrubland", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("clifftree:desert_cliff", r(BiomeRoute.ARID_LOWLAND, BiomeRoute.ARID_UPLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("clifftree:glacier_valley", r(BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.POLAR),
                        // Corrected 2026-08-10: COLD_UPLAND already exists (band >= BAND_SUBPOLAR && mountain,
            // 50-90 degrees) and is already owned by snowy_slopes/frozen_peaks/jagged_peaks -- no
            // new route was needed. A "cliff" is rugged terrain by name, so the mountain gate this
            // route already carries is the right fit, not a workaround for it.
            d("clifftree:glacier_cliff", r(BiomeRoute.COLD_UPLAND), Terrain.UPLAND, Water.LAND, Family.UPLAND),
            d("clifftree:snowy_old_growth_taiga", r(BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("clifftree:tundra", r(BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.POLAR),
            // Underground is a separate, donor-cave-gated authority. These entries are never
            // admitted through a surface tag or terrain route.
            d("biomesoplenty:glowing_grotto", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("biomesoplenty:spider_nest", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("terralith:cave/andesite_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("terralith:cave/diorite_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("terralith:cave/fungal_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("terralith:cave/granite_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("terralith:cave/infested_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("terralith:cave/thermal_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            // CliffTree caves. Categorized from the mod's own tags/worldgen/biome/caves.json and
            // deep_caves.json, not guessed: inferno is grouped with minecraft:deep_dark by CliffTree
            // itself (not with its other caves), matching CAVE_DEEP's real Y<=-16 depth gate despite
            // inferno's surface-hot temperature (2.0) -- it never reaches the surface, so that
            // temperature is irrelevant to placement. dirt_caves is absent from CliffTree's own
            // "caves" tag (an omission in their data, not ours) but is unambiguously a cave by name,
            // content, and climate profile matching its shallow siblings, so it is included here.
            d("clifftree:caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("clifftree:warm_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("clifftree:lukewarm_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("clifftree:cold_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("clifftree:frozen_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("clifftree:mushroom_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("clifftree:dirt_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("clifftree:inferno", r(BiomeRoute.CAVE_DEEP), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("terralith:cave/underground_jungle", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("terralith:cave/deep_caves", r(BiomeRoute.CAVE_DEEP), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("terralith:cave/frostfire_caves", r(BiomeRoute.CAVE_DEEP), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("terralith:cave/mantle_caves", r(BiomeRoute.CAVE_DEEP), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("terralith:cave/tuff_caves", r(BiomeRoute.CAVE_DEEP), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            // Vanilla is also a provider ticket.  Its explicit entries make a route's provider
            // roster independent of whatever optional-mod tags happen to be installed.
            d("minecraft:jungle", r(BiomeRoute.TROPICAL_HUMID_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.JUNGLE),
            d("minecraft:sparse_jungle", r(BiomeRoute.TROPICAL_HUMID_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.JUNGLE),
            d("minecraft:bamboo_jungle", r(BiomeRoute.TROPICAL_HUMID_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.JUNGLE),
            d("minecraft:forest", r(BiomeRoute.SUBTROPICAL_HUMID_LOWLAND, BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("minecraft:birch_forest", r(BiomeRoute.SUBTROPICAL_HUMID_LOWLAND, BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("minecraft:dark_forest", r(BiomeRoute.SUBTROPICAL_HUMID_LOWLAND, BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("minecraft:flower_forest", r(BiomeRoute.SUBTROPICAL_HUMID_LOWLAND, BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("minecraft:plains", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("minecraft:sunflower_plains", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("minecraft:taiga", r(BiomeRoute.TEMPERATE_LOWLAND, BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("minecraft:old_growth_pine_taiga", r(BiomeRoute.TEMPERATE_LOWLAND, BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("minecraft:old_growth_birch_forest", r(BiomeRoute.TEMPERATE_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.FOREST),
            d("minecraft:swamp", r(BiomeRoute.TEMPERATE_WETLAND), Terrain.WETLAND, Water.WETLAND, Family.WETLAND),
            d("minecraft:meadow", r(BiomeRoute.TEMPERATE_UPLAND), Terrain.UPLAND, Water.LAND, Family.UPLAND),
            d("minecraft:cherry_grove", r(BiomeRoute.TEMPERATE_UPLAND), Terrain.UPLAND, Water.LAND, Family.UPLAND),
            d("minecraft:grove", r(BiomeRoute.TEMPERATE_UPLAND), Terrain.UPLAND, Water.LAND, Family.UPLAND),
            d("minecraft:windswept_hills", r(BiomeRoute.TEMPERATE_UPLAND), Terrain.UPLAND, Water.LAND, Family.UPLAND),
            d("minecraft:windswept_forest", r(BiomeRoute.TEMPERATE_UPLAND), Terrain.UPLAND, Water.LAND, Family.UPLAND),
            d("minecraft:windswept_gravelly_hills", r(BiomeRoute.TEMPERATE_UPLAND), Terrain.UPLAND, Water.LAND, Family.UPLAND),
            d("minecraft:stony_peaks", r(BiomeRoute.TEMPERATE_UPLAND), Terrain.UPLAND, Water.LAND, Family.UPLAND),
            d("minecraft:snowy_slopes", r(BiomeRoute.COLD_UPLAND), Terrain.UPLAND, Water.LAND, Family.UPLAND),
            d("minecraft:frozen_peaks", r(BiomeRoute.COLD_UPLAND), Terrain.UPLAND, Water.LAND, Family.UPLAND),
            d("minecraft:jagged_peaks", r(BiomeRoute.COLD_UPLAND), Terrain.UPLAND, Water.LAND, Family.UPLAND),
            d("minecraft:savanna", r(BiomeRoute.WARM_TRANSITION), Terrain.LOWLAND, Water.LAND, Family.SAVANNA),
            d("minecraft:savanna_plateau", r(BiomeRoute.WARM_TRANSITION, BiomeRoute.WARM_UPLAND), Terrain.LOWLAND, Water.LAND, Family.SAVANNA),
            d("minecraft:windswept_savanna", r(BiomeRoute.WARM_TRANSITION, BiomeRoute.WARM_UPLAND), Terrain.LOWLAND, Water.LAND, Family.SAVANNA),
            d("minecraft:desert", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("minecraft:badlands", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("minecraft:wooded_badlands", r(BiomeRoute.ARID_LOWLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("minecraft:eroded_badlands", r(BiomeRoute.ARID_LOWLAND, BiomeRoute.ARID_UPLAND), Terrain.ARID, Water.LAND, Family.ARID),
            d("minecraft:snowy_taiga", r(BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("minecraft:old_growth_spruce_taiga", r(BiomeRoute.SUBPOLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.TAIGA),
            d("minecraft:snowy_plains", r(BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.POLAR),
            d("minecraft:ice_spikes", r(BiomeRoute.POLAR_LOWLAND), Terrain.LOWLAND, Water.LAND, Family.POLAR),
            d("minecraft:lush_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            d("minecraft:dripstone_caves", r(BiomeRoute.CAVE_SHALLOW), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE),
            // minecraft:sulfur_caves is deliberately absent: it is a 26.x biome that does not exist
            // on Minecraft 1.21.11. Keeping it here was not inert -- the policy suite builds its
            // synthetic registry from this ledger, so a 26.2-shaped ledger made the cave-coverage
            // test pass against a biome the real game does not have, which is exactly why the suite
            // stayed green while world creation hard-crashed. Do not re-add when harvesting 26.2.
            d("minecraft:deep_dark", r(BiomeRoute.CAVE_DEEP), Terrain.CAVE, Water.UNDERGROUND, Family.CAVE)
    );

    private static final Map<String, Descriptor> BY_ID = buildIndex();

    private BiomeDescriptorLedger() {}

    public static Descriptor descriptor(String id) { return BY_ID.get(id); }
    public static Collection<Descriptor> descriptors() { return DESCRIPTORS; }
    public static boolean isSupportedProvider(String provider) { return DESCRIPTORS.stream().anyMatch(d -> d.provider().equals(provider)); }
    public static boolean isCaveDescriptor(String id) {
        Descriptor descriptor = descriptor(id);
        return descriptor != null && descriptor.terrain() == Terrain.CAVE;
    }

    public static List<String> validate(Collection<String> activeIds) {
        List<String> errors = new ArrayList<>();
        Set<String> active = Set.copyOf(activeIds);
        for (Descriptor descriptor : DESCRIPTORS) {
            if (!active.contains(descriptor.biomeId())) continue;
            if (!descriptor.biomeId().startsWith(descriptor.provider() + ":")) errors.add("namespace mismatch: " + descriptor.biomeId());
        }
        return List.copyOf(errors);
    }

    private static Map<String, Descriptor> buildIndex() {
        Map<String, Descriptor> out = new HashMap<>();
        for (Descriptor descriptor : DESCRIPTORS) {
            if (out.putIfAbsent(descriptor.biomeId(), descriptor) != null) throw new IllegalStateException("duplicate descriptor: " + descriptor.biomeId());
        }
        return Map.copyOf(out);
    }
    private static Descriptor d(String id, Set<BiomeRoute> routes, Terrain terrain, Water water, Family family) {
        return new Descriptor(id, id.substring(0, id.indexOf(':')), routes, terrain, water, family, false);
    }
    private static Set<BiomeRoute> r(BiomeRoute... routes) { return EnumSet.copyOf(List.of(routes)); }
    private static String requireBiomeId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("invalid biome id: " + id);
        return id;
    }
    private static String requireProvider(String provider, String id) {
        if (provider == null || provider.isBlank() || !id.startsWith(provider.toLowerCase(Locale.ROOT) + ":")) throw new IllegalArgumentException("provider mismatch: " + id);
        return provider;
    }
}
