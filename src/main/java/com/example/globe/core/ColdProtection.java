package com.example.globe.core;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- Peetsa stipulation S1: cold protection from freeze-immune wearables.
 * Pure math, zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM). The MC-coupled part --
 * counting how many of the player's four armor slots carry an item in the vanilla {@code freeze_immune_wearables}
 * tag (leather by default, datapack-extensible per the vanilla-first law) -- lives in a thin server shim in
 * {@code GlobeMod}; this class only maps that COUNT (0-4) to behaviour.
 *
 * <p><b>What it scales, and what it does NOT.</b> The piece count scales ONLY the mod's latitude-driven freeze
 * DAMAGE amount ({@code PolarHazardWindow.freezeDamageAmount}); the freeze-damage INTERVAL, the frost visual
 * (blue hearts), and every polar SLOWNESS/weakness/mining-fatigue effect are untouched. So even a fully-armored
 * traveller still slows to a crawl and frosts over at the pole -- the cold still resists them -- they just do
 * not lose hearts to it. A full four-piece set NEGATES freeze damage (multiplier 0.0); partial sets scale it
 * linearly (see {@link #damageMultiplier}).
 *
 * <p><b>One evaluator, one truth (the honesty law).</b> {@link #protectionLevel} is the same count the (P2)
 * warning-text swap reads: a player whose freeze damage is negated must not be told by the LETHAL warning that
 * they "are freezing to death". P2 consumes {@link #protectionLevel}/{@link #negatesFreezeDamage} to swap that
 * one rung's copy; the verbatim DANGER line stays untouched. Keeping the damage scaling and the text swap on the
 * SAME evaluator is what prevents them from drifting into a lie.
 *
 * <p><b>Potions.</b> v1 is armor-tag only -- no vanilla cold-resistance status effect exists to read. A future
 * "expedition tonic" idea is noted in the design; it would slot in as an additional multiplier here.
 */
public final class ColdProtection {

    private ColdProtection() {
    }

    /** The number of armor slots consulted for cold protection (HEAD, CHEST, LEGS, FEET). A full set is this
     *  many freeze-immune pieces. */
    public static final int MAX_PIECES = 4;

    /**
     * Freeze-DAMAGE multiplier for a count of freeze-immune armor pieces, clamped to {@code [0, MAX_PIECES]}:
     * <pre>
     *   pieces:      0     1     2     3     4
     *   multiplier: 1.00  0.75  0.50  0.25  0.00
     * </pre>
     * i.e. {@code (MAX_PIECES - clamp(pieces)) / MAX_PIECES}. Each piece removes a quarter of the freeze damage;
     * a full set removes it entirely. Applied as a factor on the curve's per-hit HP amount (interval unchanged).
     */
    public static double damageMultiplier(int pieces) {
        int p = clampPieces(pieces);
        return (MAX_PIECES - p) / (double) MAX_PIECES;
    }

    /** True iff a full freeze-immune set is worn ({@code pieces >= MAX_PIECES}) -- freeze damage is fully
     *  negated. The predicate the (P2) LETHAL-line honesty swap keys on. */
    public static boolean negatesFreezeDamage(int pieces) {
        return clampPieces(pieces) >= MAX_PIECES;
    }

    /** The protection LEVEL exposed for the P2 warning-text swap: the clamped freeze-immune piece count
     *  {@code [0, MAX_PIECES]}. 0 = unprotected (the honest LETHAL line applies), MAX_PIECES = fully protected
     *  ("The bitter cold envelops you." replaces the LETHAL line). */
    public static int protectionLevel(int pieces) {
        return clampPieces(pieces);
    }

    private static int clampPieces(int pieces) {
        if (pieces < 0) {
            return 0;
        }
        return Math.min(MAX_PIECES, pieces);
    }
}
