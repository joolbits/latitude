package com.example.globe.core;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- Peetsa stipulation S6 (FROZEN WOUNDS): the WARMTH-SOURCE classifier.
 * Pure Java, zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM): the block-state facts
 * arrive as primitives (registry namespace + path + the LIT property where the block has one), so the warm-set
 * truth table is provable without a registry bootstrap. The MC-coupled part -- the ~4-block box scan
 * (9x5x9 around the player, cached verdict on a ~20-tick cadence) that feeds each block state through this
 * classifier -- is a thin shim in {@code GlobeMod}.
 *
 * <p><b>WARM (v1, vanilla-only): heat, not light.</b>
 * <ul>
 *   <li>LIT campfire (a lit NON-SOUL campfire -- the snow-cave campfire ritual IS the mechanic)</li>
 *   <li>fire (the block; inherently lit)</li>
 *   <li>lava + lava cauldron (inherently hot)</li>
 *   <li>LIT furnace / blast furnace / smoker (a working hearth)</li>
 * </ul>
 * <b>Explicitly NOT warm:</b> torches, lanterns, candles (light, not heat); SOUL fire / SOUL campfire (story
 * detail: soul flame gives no warmth); magma block (v1 -- may be revisited); anything unlit; anything from a
 * non-vanilla namespace (v1 keeps the warm set closed -- datapack/mod extension is a future decision, not an
 * accident of string matching).
 */
public final class PolarWarmth {

    private PolarWarmth() {
    }

    /** The vanilla namespace -- the only one whose blocks can be warm in v1. */
    public static final String VANILLA_NAMESPACE = "minecraft";

    /**
     * True iff a block, described by its registry id ({@code namespace:path}) and its LIT property, is a
     * warmth source. {@code lit} is the block's {@code BlockStateProperties.LIT} value where the block HAS
     * that property; for property-less blocks the shim passes {@code true} (fire/lava are inherently "lit" --
     * the classifier still ignores it for anything not in the warm set, so a "lit" torch stays cold).
     */
    public static boolean isWarmBlock(String namespace, String path, boolean lit) {
        if (namespace == null || path == null || !VANILLA_NAMESPACE.equals(namespace)) {
            return false;
        }
        return switch (path) {
            // The campfire ritual: warm ONLY while lit, and ONLY the non-soul one ("soul_campfire" falls to
            // default). Dousing the fire (rain can't reach a shelter, but a shovel can) removes the warmth.
            case "campfire" -> lit;
            // Inherently hot, no LIT property: the shim passes lit=true and it is ignored.
            case "fire", "lava", "lava_cauldron" -> true;
            // A working hearth: warm only while actually smelting (LIT).
            case "furnace", "blast_furnace", "smoker" -> lit;
            // torch / soul_torch / lantern / soul_lantern / candle (lit or not) / soul_fire / soul_campfire /
            // magma_block / everything else: light or story-cold, never warmth (v1).
            default -> false;
        };
    }
}
