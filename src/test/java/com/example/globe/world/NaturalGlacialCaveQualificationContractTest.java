package com.example.globe.world;

import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the shared audit/production cave law rather than allowing a dev-only approximation to drift. */
class NaturalGlacialCaveQualificationContractTest {

    @Test
    void sharedLowerCaveMaterialAndYLawMatchesTheProductionCompatibilitySurface() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        assertEquals(PowderCrevasseRoofFeature.lowerNaturalFloorYAllowed(1),
                NaturalGlacialCaveQualification.lowerNaturalFloorYAllowed(1));
        assertEquals(PowderCrevasseRoofFeature.lowerNaturalFloorYAllowed(0),
                NaturalGlacialCaveQualification.lowerNaturalFloorYAllowed(0));
        assertTrue(NaturalGlacialCaveQualification.lowerNaturalMaterialAllowed(
                Blocks.SNOW_BLOCK.defaultBlockState()));
        assertFalse(NaturalGlacialCaveQualification.lowerNaturalMaterialAllowed(
                Blocks.DEEPSLATE.defaultBlockState()));
        assertEquals(32, NaturalGlacialCaveQualification.CONTINUATION_SEARCH_CAP,
                "the audit uses the same bounded continuation search as production");
    }
}
