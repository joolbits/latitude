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
 * linearly (see {@link #damageMultiplier}). <b>B-10 amendment (sweep A8):</b> this "effects always seep" rule
 * still holds on the LEGACY path and for leather / PARTIAL suits, but under the flag-ON unified law a COMPLETE
 * four-piece polar SUIT walks freely -- {@link #suppressesColdEffects} lifts the whole slowness/weakness/
 * mining-fatigue/immersion-staging family too, not just the damage.
 *
 * <p><b>One evaluator, one truth (the honesty law).</b> {@link #protectionLevel} is the same count the (P2)
 * warning-text swap reads: a player whose freeze damage is negated must not be told by the LETHAL warning that
 * they "are freezing to death". P2 consumes {@link #protectionLevel}/{@link #negatesFreezeDamage} to swap that
 * one rung's copy; the verbatim DANGER line stays untouched. Keeping the damage scaling and the text swap on the
 * SAME evaluator is what prevents them from drifting into a lie.
 *
 * <p><b>Potions.</b> v1 is armor-tag only -- no vanilla cold-resistance status effect exists to read. A future
 * "expedition tonic" idea is noted in the design; it would slot in as an additional multiplier here.
 *
 * <p><b>B-10 Polar Outfitting -- TWO paths, the flag chooses (sweep A3).</b> This class now exposes BOTH:
 * <ul>
 *   <li>the LEGACY single-count path ({@link #damageMultiplier(int)} / {@link #negatesFreezeDamage(int)} /
 *       {@link #protectionLevel(int)}) -- one 0.25-weighted count, full negation at 4. This is the
 *       {@code latitude.polarOutfitting.enabled}=OFF behaviour, BYTE-IDENTICAL to today's leather trick (no
 *       protection gap while the suit is unshipped);</li>
 *   <li>the UNIFIED weighted path ({@link #weightedMultiplier(int, int)} / {@link #fullyProtected(int)}) --
 *       a polar-SUIT piece (0.25) outweighs a leather / other freeze-immune piece (0.125, total capped 0.5),
 *       so ONLY a full four-piece suit reaches total protection. This is the flag-ON behaviour.</li>
 * </ul>
 * The shim (server + client) reads the flag and calls one path or the other; the pure core stays agnostic and
 * both paths are unit-provable in a plain JVM.
 */
public final class ColdProtection {

    private ColdProtection() {
    }

    /** The number of armor slots consulted for cold protection (HEAD, CHEST, LEGS, FEET). A full set is this
     *  many freeze-immune pieces. */
    public static final int MAX_PIECES = 4;

    /** B-10 unified path: weight of one polar-SUIT piece toward total protection (four = 1.0 = full). */
    public static final double SUIT_PIECE_WEIGHT = 0.25;
    /** B-10 unified path: weight of one leather (other freeze-immune) piece -- HALF a suit piece, so any leather
     *  substitution keeps the total strictly below 1.0 and only a full suit reaches total protection. */
    public static final double LEATHER_PIECE_WEIGHT = 0.125;
    /** B-10 unified path: hard cap on the leather CONTRIBUTION (four leather = exactly 0.5 already; the cap is
     *  belt-and-suspenders so a bad shim read -- {@code leatherPieces > 4} -- can never cross into 1.0). */
    public static final double LEATHER_WEIGHT_CAP = 0.5;

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

    // --- B-10 Polar Outfitting: the UNIFIED weighted path (design §3.2, sweep A2/A3) -------------------

    /**
     * B-10 unified freeze-DAMAGE multiplier over the four armor slots, weighting a polar-SUIT piece
     * ({@link #SUIT_PIECE_WEIGHT}, 0.25) above a plain leather / other {@code freeze_immune_wearables} piece
     * ({@link #LEATHER_PIECE_WEIGHT}, 0.125, contribution capped at {@link #LEATHER_WEIGHT_CAP}):
     * <pre>
     *   multiplier = 1 - clamp01( suitPieces*0.25 + min(leatherPieces*0.125, 0.5) )
     * </pre>
     * Worked (design §3.2): bare = 1.00; 4 leather = 0.50 (capped); 2 suit + 2 leather = 0.25;
     * 3 suit + 1 leather = 0.125; 4 suit = 0.00.
     *
     * <p><b>The subtraction contract (sweep A2 -- the one-evaluator law).</b> {@code leatherPieces} MUST be
     * {@code count(freeze_immune_wearables) - count(globe:polar_suit)}. A suit piece is ALSO a member of the
     * vanilla freeze-immune tag (so the suit still grants powder-snow immunity), so the caller subtracts the
     * suit count to avoid weighting a suit piece twice. Passing the RAW freeze-immune count as
     * {@code leatherPieces} would let e.g. 3-suit+1-leather sum to 0.75 + 0.125 + (the leather-double-counted
     * 3*0.125) and cross into damage-0 while the warning matrix still fires -- the exact evaluator lie the
     * design forbids. Both shims (server {@code GlobeMod} + client {@code PolarColdClient}) do the subtraction;
     * this pure core takes the two ALREADY-SEPARATED counts explicitly so the contract is testable in isolation.
     */
    public static double weightedMultiplier(int suitPieces, int leatherPieces) {
        double suit = clampPieces(suitPieces) * SUIT_PIECE_WEIGHT;
        double leather = Math.min(clampPieces(leatherPieces) * LEATHER_PIECE_WEIGHT, LEATHER_WEIGHT_CAP);
        return 1.0 - clamp01(suit + leather);
    }

    /**
     * B-10 total-protection predicate: true iff a FULL four-piece polar SUIT is worn ({@code suitPieces >= 4}).
     *
     * <p>Deliberately keyed on the SUIT count alone, never on {@code totalWeight >= 1.0} (design §3.2, sweep):
     * only four suit pieces can reach 1.0 (any leather substitution is half-weight and falls short), but pinning
     * the predicate to the suit count makes the "must complete the SET" rule impossible to satisfy with a lucky
     * weight sum, and gives all three total-protection effects -- freeze damage 0, warning silence, and the Cold
     * Protection status effect -- a single shared truth ("one evaluator, one truth").
     */
    public static boolean fullyProtected(int suitPieces) {
        return clampPieces(suitPieces) >= MAX_PIECES;
    }

    /**
     * B-10 full-suit COLD-EFFECTS exemption (sweep A8). Under the flag-ON unified law, a full four-piece polar
     * SUIT ({@link #fullyProtected}) suppresses the ENTIRE polar hazard-EFFECTS family -- slowness, weakness,
     * mining fatigue, AND the in-water immersion staging -- not merely freeze damage. Leather and PARTIAL suits
     * NEVER suppress the effects: the standing "slowness always seeps" law holds for everyone but the complete
     * set (only a full suit walks freely). This makes {@link #fullyProtected} (suit == 4, the sole full
     * predicate) the single MASTER exemption across the whole hazard-application family -- damage
     * ({@link #weightedMultiplier} -> 0), effects (here), warnings
     * ({@link PolarColdCues#evaluateLadderFullSuit}), and the heal lock
     * ({@link PolarWounds#healLocked(boolean, boolean, boolean, boolean)}): one evaluator, one truth.
     *
     * <p><b>Flag-OFF is unaffected / bit-identical.</b> Today this class only ever scaled DAMAGE; the polar
     * effects always apply regardless of armor (including full leather). The legacy path therefore never
     * consults this method -- effects stay always-on, exactly as today. The {@code GlobeMod} effects-tick shim
     * calls this only on the flag-ON path (P2).
     */
    public static boolean suppressesColdEffects(int suitPieces) {
        return fullyProtected(suitPieces);
    }

    private static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(1.0, v);
    }

    private static int clampPieces(int pieces) {
        if (pieces < 0) {
            return 0;
        }
        return Math.min(MAX_PIECES, pieces);
    }
}
