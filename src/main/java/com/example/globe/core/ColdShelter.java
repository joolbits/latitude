package com.example.globe.core;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- Peetsa stipulation S4: the SHELTER RULE for cold damage. Pure threshold
 * classifier, zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM). The MC-coupled part --
 * reading RAW SKY LIGHT at the player's eye position ({@code LightLayer.SKY} via the level light engine) --
 * is a thin shim in {@code GlobeMod}; this class owns only the threshold decision.
 *
 * <p><b>The rule (one rule, one story: walls stop the bleeding).</b> Cold DAMAGE -- the frostbite band AND the
 * lethal core -- PAUSES while the player is genuinely sheltered. Slowness / Mining Fatigue / the whiteout
 * atmosphere are UNCHANGED (the cold seeps indoors; only the damage stops). The F3 frostbite frost cue pauses
 * with the damage (no bite = no cue) unless the S6 heal-lock holds it.
 *
 * <p><b>Why raw sky light, not {@code canSeeSky} (trap-proof, graded enclosure).</b> The single-overhead-log
 * trap -- Peetsa's explicit callout, the old warning-banner-under-a-tree bug class -- makes binary
 * {@code canSeeSky} lie: one block over the head reads "sheltered" while the player stands in a howling
 * blizzard. Raw sky light is GRADED: under one overhead block with open sides, diffuse sky light floods in
 * sideways and the eye position still reads ~11-13 (NOT sheltered); a sealed hut / cave / snow burrow reads
 * 0-2 (sheltered). Threshold {@link #SHELTERED_MAX_SKY_LIGHT} = 3 keeps near-sealed spaces (a doorway crack)
 * sheltered while any real exposure stays cold. Raw sky light is also time-of-day-independent (night darkness
 * comes from skyDarken, not the stored light), so a night camp behaves exactly like a day camp. This follows
 * {@code PolarWhiteoutOverlayHud}'s graded-exposure philosophy -- binary {@code canSeeSky} stays only where
 * conservative-DENY is correct (the crossing prompt gates).
 *
 * <p><b>Known accepted edge (P3 feel item):</b> a pure-glass roof passes sky light, so a glass igloo counts as
 * EXPOSED -- "the cold bites through the glass" -- story-defensible, rare at the poles, revisit only if live
 * feel demands.
 *
 * <p><b>Testing note:</b> a real light-engine test is not feasible in the pure-JVM suite (sky-light propagation
 * needs a running level). The MANDATORY log-trap case is therefore pinned at the classifier boundary with the
 * real-world values the light engine produces for that geometry (~11-13 -&gt; NOT sheltered), plus the sealed-box
 * (0-2 -&gt; sheltered) and exact threshold (3 / 4) cases; the shim is a one-line read wired to this classifier.
 */
public final class ColdShelter {

    private ColdShelter() {
    }

    /** Raw sky light (0-15) at/below which the player counts as genuinely sheltered. 3 keeps a sealed-but-
     *  cracked burrow sheltered; a single-overhead-block trap (~11-13 side-lit) stays exposed. */
    public static final int SHELTERED_MAX_SKY_LIGHT = 3;

    /** True iff a raw sky-light value (0-15) at the player's eye position counts as genuinely sheltered. */
    public static boolean isSheltered(int rawSkyLight) {
        return rawSkyLight <= SHELTERED_MAX_SKY_LIGHT;
    }
}
