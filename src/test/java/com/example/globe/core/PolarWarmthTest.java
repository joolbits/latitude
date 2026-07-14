package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarWarmth} (B-7 S6) -- the warmth-source truth table behind the frozen-wounds
 * heal lock. Every warm block asserted warm; every named not-warm block asserted cold (the soul-campfire and
 * torch cases the addendum names explicitly, plus the rest of the v1 exclusion list).
 */
class PolarWarmthTest {

    private static boolean warm(String path, boolean lit) {
        return PolarWarmth.isWarmBlock("minecraft", path, lit);
    }

    @Test
    void warmTable_everyWarmSourceIsWarm() {
        assertTrue(warm("campfire", true), "lit campfire = the ritual hearth");
        assertTrue(warm("fire", true), "fire block (inherently lit)");
        assertTrue(warm("lava", true), "lava");
        assertTrue(warm("lava_cauldron", true), "lava cauldron");
        assertTrue(warm("furnace", true), "LIT furnace");
        assertTrue(warm("blast_furnace", true), "LIT blast furnace");
        assertTrue(warm("smoker", true), "LIT smoker");
    }

    @Test
    void litnessMatters_unlitHearthsAreCold() {
        assertFalse(warm("campfire", false), "an unlit/doused campfire gives no warmth");
        assertFalse(warm("furnace", false), "an idle furnace gives no warmth");
        assertFalse(warm("blast_furnace", false));
        assertFalse(warm("smoker", false));
    }

    @Test
    void soulFlameGivesNoWarmth_explicitCases() {
        // The addendum's named case: soul campfire (even LIT) is story-cold.
        assertFalse(warm("soul_campfire", true), "SOUL campfire: no warmth (story detail)");
        assertFalse(warm("soul_fire", true), "SOUL fire: no warmth");
    }

    @Test
    void lightIsNotHeat_explicitCases() {
        // The addendum's named case: torches. Plus the rest of the light-not-heat family.
        assertFalse(warm("torch", true), "torch: light, not heat");
        assertFalse(warm("wall_torch", true));
        assertFalse(warm("soul_torch", true));
        assertFalse(warm("lantern", true), "lantern: light, not heat");
        assertFalse(warm("soul_lantern", true));
        assertFalse(warm("candle", true), "a LIT candle is still light, not heat");
        assertFalse(warm("magma_block", true), "magma block: not warm in v1");
    }

    @Test
    void nonVanillaNamespaceAndNullsAreCold() {
        assertFalse(PolarWarmth.isWarmBlock("somemod", "campfire", true),
                "v1 warm set is closed to the vanilla namespace");
        assertFalse(PolarWarmth.isWarmBlock(null, "campfire", true));
        assertFalse(PolarWarmth.isWarmBlock("minecraft", null, true));
        assertFalse(warm("stone", true));
    }
}
