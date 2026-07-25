package com.example.globe.core;

/**
 * S25 addendum (Peetsa 2026-07-20, TEST 117 screenshot): the pure input-derivation law for the
 * {@code /latdev survey} gatherer. Standing at 78N on solid snowy plains, the survey told the owner he was
 * "over open water... deep open-ocean basin... nearest land roughly 576 blocks off."
 *
 * <p><b>Root cause.</b> The gatherer fed {@link GeoSurveyNarrator} the geography brain's INTENT
 * ({@code GeoAuthority.isOceanIntent} / {@code coastDistanceBlocks}); on geoV2-armed worlds intent and
 * REALIZED terrain disagree in wide bands (the standing open calibration finding -- the terrain-aware view's
 * ~77% ocean-intent vs ~39% realized). The survey's own flag-off guard already says explaining unrealized
 * intent "would lie" -- the same lie happens flag-ON wherever intent diverges.
 *
 * <p><b>The law</b> (consumed by the {@code LatitudeDevCommands.survey} gatherer; the NARRATOR is unchanged):
 * <ul>
 *   <li><b>"Where you are" is a REALIZED fact:</b> the player's column reads as ocean iff the realized world
 *       says so -- the surface block actually holds water, or the realized biome is ocean-family
 *       ({@link #realizedOcean}). This drives the narrator's {@code isOcean} input, so the headline
 *       ("over open water" vs "over land") and the terrain-story branch can never contradict the ground
 *       underfoot.</li>
 *   <li><b>The deep story stays the brain's domain:</b> plates, arcs, mountain intent, currents, winds --
 *       every other input keeps its GeoAuthority/ClimateAuthority source (legitimately intent).</li>
 *   <li><b>The intent-derived DISTANCE line drops on divergence</b> ({@link #dropCoastDistanceLine}): the
 *       traveler's-note distance ("nearest land / the coast roughly N blocks...") is computed FROM the
 *       intent coast field, so wherever intent and realized disagree about ocean-ness the number describes
 *       a coastline that is not there -- the owner was told 576 blocks to land while standing on it. The
 *       honest minimal fix is to drop that line entirely on ANY disagreement (both directions: intent-ocean/
 *       realized-land is the owner's screenshot; realized-ocean/intent-land would print the same field with
 *       the opposite meaning).</li>
 * </ul>
 * Zero Minecraft imports (Core Logic layer); the world reads (fluid at the heightmap top, ocean-family biome
 * tag) live in the command glue.
 */
public final class SurveyGroundTruth {

    private SurveyGroundTruth() {
    }

    /**
     * The realized "am I over water" fact for the player's column: TRUE iff the realized surface block holds
     * water ({@code surfaceIsWater} -- the fluid check at the heightmap top) OR the realized biome is
     * ocean-family ({@code biomeIsOceanFamily} -- {@code BiomeTags.IS_OCEAN} on the LIVE biome, not the
     * brain's intent). Either alone suffices: a frozen sea's surface block is ice (not water) but its biome
     * is ocean-family; an inland realized lake is water without an ocean biome -- both read honestly as
     * "over water" underfoot.
     */
    public static boolean realizedOcean(boolean surfaceIsWater, boolean biomeIsOceanFamily) {
        return surfaceIsWater || biomeIsOceanFamily;
    }

    /**
     * Should the traveler's-note DISTANCE line (whose number is computed from the geo-intent coast field) be
     * dropped? TRUE exactly when intent and realized disagree about ocean-ness -- in agreement the intent
     * coast distance describes a coastline the realized world corroborates and the line stands; in
     * divergence the number is a story about terrain that is not there (the owner's "576 blocks to land"
     * while standing on land) and no line is more honest than a wrong one.
     */
    public static boolean dropCoastDistanceLine(boolean intentOcean, boolean realizedOcean) {
        return intentOcean != realizedOcean;
    }
}
