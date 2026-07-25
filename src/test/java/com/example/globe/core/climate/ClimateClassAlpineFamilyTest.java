package com.example.globe.core.climate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link ClimateClass#alpineFamily()} invariants for the P1-B fix (audit
 * {@code docs/binder/fable5-biome-geography-audit-20260707.md} §5): below 45&deg; latitude, a cold-class
 * repaint must resolve from alpineFamily() (mountain-slope biomes) rather than vanillaFamily(), whose
 * COLD_STEPPE/TUNDRA/ICE_CAP entries lead with the flat-polar {@code snowy_plains} -- the confirmed
 * "snowy_plains in the warm band" defect. Acceptance line: zero snowy_plains cells below 45&deg;.
 */
class ClimateClassAlpineFamilyTest {

    // THE INVARIANT THAT MATTERS (P1-B): no ClimateClass's alpineFamily() can ever hand the biome
    // consumer snowy_plains -- that flat-polar biome is exactly what alpineFamily() exists to keep out
    // of the warm band. Sweeps ClimateClass.values() (not hand-picked cases) so a future enum addition
    // that forgets to override alpineFamily() -- and whose vanillaFamily() happens to contain
    // snowy_plains -- fails this test instead of silently reintroducing the defect.
    @Test
    void alpineFamilyNeverContainsSnowyPlains() {
        for (ClimateClass c : ClimateClass.values()) {
            assertFalse(c.alpineFamily().contains("snowy_plains"),
                    c + ".alpineFamily() contains snowy_plains -- P1-B acceptance line violated");
        }
    }

    // alpineFamily() must be as complete as vanillaFamily() (never empty) and every entry must look
    // like a real biome short-name (vanillaFamily()'s own convention: lowercase, no "minecraft:"
    // namespace prefix), so the biome consumer never gets a blank or malformed resolution target.
    @Test
    void alpineFamilyNonEmptyAndWellFormedForEveryClass() {
        for (ClimateClass c : ClimateClass.values()) {
            List<String> family = c.alpineFamily();
            assertFalse(family.isEmpty(), c + ".alpineFamily() is empty");
            for (String biome : family) {
                assertFalse(biome == null || biome.isBlank(), c + ".alpineFamily() has a blank entry");
                assertEquals(biome.toLowerCase(), biome, c + ".alpineFamily() entry not lowercase: " + biome);
                assertFalse(biome.contains(":"), c + ".alpineFamily() entry carries a namespace prefix: " + biome);
            }
        }
    }

    // The three classes P1-B actually overrides: exact documented alpine lists (mountain-slope
    // biomes only, flat-polar member dropped).
    @Test
    void overriddenClassesReturnDocumentedAlpineLists() {
        assertEquals(List.of("grove", "snowy_slopes"), ClimateClass.COLD_STEPPE.alpineFamily());
        assertEquals(List.of("grove", "snowy_slopes"), ClimateClass.TUNDRA.alpineFamily());
        assertEquals(List.of("frozen_peaks", "snowy_slopes"), ClimateClass.ICE_CAP.alpineFamily());
    }

    // Fall-through contract: every class P1-B did NOT touch (including BOREAL, whose vanillaFamily()
    // is already a legitimate montane biome with no flat-polar member) must return its vanillaFamily()
    // unchanged from alpineFamily() -- ADDITIVE only, per the enum's javadoc.
    @Test
    void nonOverriddenClassesFallThroughToVanillaFamily() {
        for (ClimateClass c : ClimateClass.values()) {
            if (c == ClimateClass.COLD_STEPPE || c == ClimateClass.TUNDRA || c == ClimateClass.ICE_CAP) {
                continue;
            }
            assertEquals(c.vanillaFamily(), c.alpineFamily(),
                    c + " is not an overridden class but alpineFamily() != vanillaFamily()");
        }
    }

    // Guard rail: this pass is additive-only, so vanillaFamily() itself must be byte-identical to its
    // pre-P1-B data for the three overridden classes -- pins the known flat-polar-leading lists so an
    // accidental edit to the original enum data (rather than a new alpineFamily() override) fails loudly.
    @Test
    void vanillaFamilyUntouchedByThisPass() {
        assertEquals(List.of("snowy_plains", "windswept_gravelly_hills"), ClimateClass.COLD_STEPPE.vanillaFamily());
        assertEquals(List.of("snowy_plains", "snowy_slopes", "grove"), ClimateClass.TUNDRA.vanillaFamily());
        assertEquals(List.of("ice_spikes", "snowy_plains", "frozen_peaks"), ClimateClass.ICE_CAP.vanillaFamily());
    }
}
