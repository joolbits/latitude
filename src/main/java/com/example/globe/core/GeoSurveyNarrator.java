package com.example.globe.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure, unit-testable narrator for the {@code /latdev survey} dev command: turns the numeric
 * GeoAuthority + ClimateAuthority field values for one column (plus a tiny neighbour-ring context)
 * into a short, plain-language geography briefing a non-programmer can read in chat.
 *
 * <p>Zero Minecraft imports and zero dependency on the authority classes themselves -- it takes a
 * flat {@link Input} of already-sampled scalars so the phrasing rules can be asserted directly in a
 * unit test with synthetic geography. The command layer ({@code LatitudeDevCommands.survey}) is the
 * only thing that fetches the real summaries and builds the {@link Input}.
 *
 * <p>Design intent (Peetsa, non-programmer): NO raw field names ever appear in the output; each line
 * is a cause-and-effect sentence ("this massif is here because ..."). Output is 5-9 short lines.
 */
public final class GeoSurveyNarrator {

    private GeoSurveyNarrator() {}

    // --- terrain thresholds -------------------------------------------------------------------
    private static final double MTN_YOUNG_BELT = 0.60;   // convergent, actively-rising belt
    private static final double MTN_OLD_MASSIF = 0.35;   // worn upland massif
    private static final double RUGGED_HILLS = 0.45;     // broken hill country w/o a real belt
    private static final double DROWNED_LAND01 = 0.35;   // land column barely above the waves
    private static final double ARC_STRONG = 0.35;       // volcanic island-arc / subduction signal
    private static final double SHELF_STRONG = 0.45;     // shallow continental shelf
    private static final double ARCHIPELAGO_STRONG = 0.40;

    // --- climate thresholds -------------------------------------------------------------------
    private static final double RAIN_SHADOW_STRONG = 0.35;
    private static final double WINDWARD_STRONG = 0.35;
    private static final double PRECIP_DRY = 0.30;
    private static final double PRECIP_WET = 0.60;
    private static final double CONTINENTAL_DEEP = 0.55;
    private static final double ALTITUDE_COLD = 0.35;
    private static final double FETCH_LONG_FRAC = 0.20;  // fetch >= 20% of the pole radius = "long ocean run"

    /**
     * Flat scalar snapshot of one column plus a small neighbour ring. All {@code *01} values are the
     * authorities' native [0,1] magnitudes; {@code coastDistanceBlocks} is signed (+ land / − ocean);
     * {@code currentSigned} is [-1,+1]. {@code zRadiusBlocks} scales the "long ocean run" test.
     */
    public record Input(
            double latitudeDeg,
            boolean hemisphereNorth,
            String bandName,
            String climateClassName,
            // geo
            double land01,
            boolean isOcean,
            double coastDistanceBlocks,
            double mountainIntent01,
            double ruggedness01,
            double islandArc01,
            double shelf01,
            double archipelago01,
            // climate
            double temperature01,
            double precipitation01,
            double continentality01,
            double windX,
            double windZ,
            double fetchBlocks,
            double windwardLift01,
            double rainShadow01,
            double altitudeCooling01,
            double currentSigned,
            int zRadiusBlocks,
            // neighbour ring context (aggregated ~200 blocks out)
            double ringMountainIntentMax,
            double ringOceanFraction
    ) {}

    /** Builds the 5-9 line briefing. Pure: same input always yields the same lines. */
    public static List<String> narrate(Input in) {
        List<String> lines = new ArrayList<>();
        lines.add(headline(in));
        lines.add(terrainStory(in));
        String context = contextLine(in);
        if (context != null) {
            lines.add(context);
        }
        lines.add(climateStory(in));
        lines.add(climateBelt(in));
        String alt = altitudeNote(in);
        if (alt != null) {
            lines.add(alt);
        }
        lines.add(travelersNote(in));
        return lines;
    }

    // ------------------------------------------------------------------------------------------

    private static String headline(Input in) {
        String belt = bandPlain(in.bandName());
        String hemi = in.hemisphereNorth() ? "northern" : "southern";
        String place = in.isOcean() ? "open water" : "land";
        return String.format(Locale.ROOT,
                "Survey: %.0f° in the %s hemisphere — the %s belt, over %s.",
                Math.abs(in.latitudeDeg()), hemi, belt, place);
    }

    private static String terrainStory(Input in) {
        if (in.isOcean()) {
            if (in.islandArc01() >= ARC_STRONG) {
                return "You're over a volcanic island arc — an ocean trench runs nearby where one "
                        + "plate dives beneath another, and the sinking crust melts and feeds a chain of islands.";
            }
            if (in.archipelago01() >= ARCHIPELAGO_STRONG) {
                return "This is a scattered archipelago — shallow banks and small islands dotting the sea.";
            }
            if (in.shelf01() >= SHELF_STRONG) {
                return "You're over a shallow continental shelf — the sea floor here is still the "
                        + "drowned skirt of the nearby land, not yet the deep.";
            }
            return "You're over a deep open-ocean basin, far from any continental shelf.";
        }
        // land
        if (in.land01() <= DROWNED_LAND01 && Math.abs(in.coastDistanceBlocks()) < 80) {
            return "This is barely-emerged land — a drowned continental shelf that the sea nearly "
                    + "reclaims; a small drop in the coastline would put it underwater.";
        }
        if (in.mountainIntent01() >= MTN_YOUNG_BELT) {
            String belt = in.ringMountainIntentMax() >= MTN_YOUNG_BELT
                    ? " The high ground carries on through the neighbouring country, so this is part of a long range, not a lone peak."
                    : "";
            return "That massif is here because two plates are converging right here — the crust is "
                    + "being squeezed and folded upward into a young, still-rising mountain belt." + belt;
        }
        if (in.mountainIntent01() >= MTN_OLD_MASSIF) {
            return "This upland is an old massif — high ground raised in an ancient plate collision "
                    + "and worn down since; the roots of once-taller mountains.";
        }
        if (in.ruggedness01() >= RUGGED_HILLS) {
            return "This is broken, rugged hill country — uneven ground without a single organising ridge.";
        }
        return "This is low, gently rolling ground well away from any active mountain-building.";
    }

    private static String contextLine(Input in) {
        if (in.isOcean()) {
            return null; // ocean terrain line already carries the setting
        }
        double dist = Math.abs(in.coastDistanceBlocks());
        if (in.continentality01() >= CONTINENTAL_DEEP) {
            return "You're deep in a continental interior, a long way from any coast.";
        }
        if (dist < 120) {
            return "The sea is close by — this is coastal country, within reach of ocean weather.";
        }
        return null;
    }

    private static String climateStory(Input in) {
        if (in.isOcean()) {
            return "The water keeps the air here mild and damp — the sea evens out the extremes of heat and cold.";
        }
        boolean longFetch = in.fetchBlocks() >= FETCH_LONG_FRAC * Math.max(1, in.zRadiusBlocks());
        if (in.rainShadow01() >= RAIN_SHADOW_STRONG && in.precipitation01() < PRECIP_WET) {
            return "This is a rain shadow: the ridge upwind wrings the moisture out of the air before it "
                    + "reaches here, so the land stays dry in the lee of the mountains.";
        }
        if (in.windwardLift01() >= WINDWARD_STRONG && in.precipitation01() >= PRECIP_WET) {
            return "Winds arrive soaked from a long ocean run and are forced to climb these slopes, "
                    + "cooling and dropping their rain right here on the windward side.";
        }
        if (in.continentality01() >= CONTINENTAL_DEEP && in.precipitation01() < PRECIP_DRY) {
            return "This is a parched continental interior — the sea's moisture rarely survives the "
                    + "long journey inland, so little rain falls.";
        }
        if (longFetch && in.precipitation01() >= PRECIP_WET) {
            return "Moist ocean air rides in on a long sea fetch and keeps this country reliably well-watered.";
        }
        if (in.precipitation01() < PRECIP_DRY) {
            return "Dry air dominates and little rain reaches the ground here.";
        }
        return "Rainfall here is moderate — neither a desert nor a soaking coast.";
    }

    private static String climateBelt(Input in) {
        String band = in.bandName() == null ? "" : in.bandName().toUpperCase(Locale.ROOT);
        return switch (band) {
            case "TROPICAL" -> "Sitting in the tropics, the sun stays high all year — it is reliably hot, with the seasons marked by wet and dry more than by warm and cold.";
            case "SUBTROPICAL" -> "In the subtropics the sun is strong for much of the year — hot summers with milder winters, and often a dry season.";
            case "TEMPERATE" -> "This is the temperate belt — four clear seasons, with the sun's angle swinging widely between summer and winter.";
            case "SUBPOLAR" -> "Up in the subpolar belt the sun rides low — long, hard winters and short, cool summers.";
            case "POLAR" -> "Near the pole the sun barely clears the horizon — bitterly cold, frozen for most or all of the year.";
            default -> "The latitude here sets a middling climate belt.";
        };
    }

    private static String altitudeNote(Input in) {
        if (!in.isOcean() && in.altitudeCooling01() >= ALTITUDE_COLD) {
            return "Altitude keeps it colder here than the latitude alone would — height steals warmth the way distance from the equator does.";
        }
        return null;
    }

    private static String travelersNote(Input in) {
        // Zonal wind dominates; moisture/seaward air comes from upwind (opposite the flow).
        String upwind = in.windX() >= 0.0 ? "west" : "east"; // windX>0 blows toward +X (east) => source is west
        if (in.isOcean()) {
            return String.format(Locale.ROOT,
                    "Traveler's note: the prevailing winds run toward the %s; the nearest land lies "
                            + "roughly %d blocks off.",
                    in.windX() >= 0.0 ? "east" : "west", (int) Math.round(Math.abs(in.coastDistanceBlocks())));
        }
        int coast = (int) Math.round(Math.abs(in.coastDistanceBlocks()));
        return String.format(Locale.ROOT,
                "Traveler's note: the wetter, seaward air arrives from the %s; the coast is roughly "
                        + "%d blocks that way at the nearest.",
                upwind, coast);
    }

    private static String bandPlain(String bandName) {
        if (bandName == null) {
            return "mid-latitude";
        }
        return switch (bandName.toUpperCase(Locale.ROOT)) {
            case "TROPICAL" -> "tropical";
            case "SUBTROPICAL" -> "subtropical";
            case "TEMPERATE" -> "temperate";
            case "SUBPOLAR" -> "subpolar";
            case "POLAR" -> "polar";
            default -> "mid-latitude";
        };
    }
}
