package com.example.globe.core.climate;

import com.example.globe.core.geo.GeoAuthority;
import com.example.globe.core.geo.GeoSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ClimateAuthority acceptance table + invariants (the locked design's self-validated 14/14 rows and
 * its testable invariants). The acceptance rows drive {@link ClimateAuthority#assemble} with synthetic
 * geography so climate correctness is proven independent of any real GeoAuthority run.
 */
class ClimateAuthorityTest {

    private static final long SEED = 2591890304012655616L;
    private static final int R = 10000;
    private final ClimateAuthority ca = new ClimateAuthority(new GeoAuthority(SEED, R, 2 * R));

    private static int zFor(double phi) {
        return (int) Math.round(phi / 90.0 * R);
    }

    /** land column: continentality is driven by coastDist (cont≈1 by ~0.14R inland). */
    private static GeoSummary land(double coastDist, double mtn) {
        return new GeoSummary(1.0, false, 1, -1, coastDist, 0, 0, 0, mtn, -1, 0, 0, -1, 0, -1);
    }

    private static GeoSummary ocean() {
        return new GeoSummary(0.0, true, -1, 1, -50.0, 0.0, 0, 0, 0, -1, 0, 0, -1, 0, -1);
    }

    /** ocean column near a plate boundary: mountainIntent01 is nonzero despite being open ocean. */
    private static GeoSummary oceanNearMountainIntent(double mtn) {
        return new GeoSummary(0.0, true, -1, 1, -50.0, 0.0, 0, 0, mtn, -1, 0, 0, -1, 0, -1);
    }

    private String cls(double phi, GeoSummary c, double fetch, double curr, double lift, double shadow) {
        return ca.assemble(0, zFor(phi), c, fetch * R, curr, lift, shadow, List.of()).climateClass();
    }

    // ---- Acceptance table: 14 (latitude, geography) -> expected climateClass ----
    @Test
    void acceptanceTable() {
        // 1 equatorial wet coast -> rainforest
        assertEquals("TROPICAL_RAINFOREST", cls(5, land(100, 0), 0.35, 0.30, 0, 0), "row1");
        // 2 open equatorial ocean -> warm ocean
        assertEquals("OCEAN_WARM", cls(2, ocean(), 0.42, 0, 0, 0), "row2");
        // 3 Amazon/Congo deep interior, fetch 0 -> rainforest (ITCZ floor)
        assertEquals("TROPICAL_RAINFOREST", cls(4, land(2500, 0), 0.0, 0, 0, 0), "row3");
        // 4 Sahara continental interior -> hot desert
        assertEquals("HOT_DESERT", cls(25, land(2500, 0), 0.0, 0, 0, 0), "row4");
        // 5 Atacama/Namib west coast, cold current -> hot desert
        assertEquals("HOT_DESERT", cls(24, land(100, 0), 0.30, -0.9, 0, 0), "row5");
        // 6 Pacific-NW/W-Europe west coast -> temperate oceanic
        assertEquals("TEMPERATE_OCEANIC", cls(45, land(100, 0), 0.35, 0.35, 0, 0), "row6");
        // 7 Great-Plains/Kazakhstan interior -> humid continental (not desert)
        assertEquals("HUMID_CONTINENTAL", cls(45, land(2500, 0), 0.0, 0, 0, 0), "row7");
        // 8 SE-US/China east coast, warm current, monsoon -> humid subtropical
        assertEquals("HUMID_SUBTROPICAL", cls(30, land(650, 0), 0.35, 0.9, 0, 0), "row8");
        // 9 Siberia/Canada mid-continent -> boreal
        assertEquals("BOREAL", cls(60, land(2500, 0), 0.10, 0, 0, 0), "row9");
        // 10 polar interior high -> ice cap
        assertEquals("ICE_CAP", cls(78, land(2500, 0.7), 0.0, 0, 0, 0), "row10");
        // 11 Mediterranean west coast, cool current -> mediterranean
        assertEquals("MEDITERRANEAN", cls(38, land(400, 0), 0.28, -0.5, 0, 0), "row11");
        // 12 mid-lat lee of mountains (rain shadow diagnostic) -> still oceanic (class not flipped)
        assertEquals("TEMPERATE_OCEANIC", cls(45, land(100, 0), 0.30, 0.30, 0, 0.9), "row12");
        // 13 tropical savanna, dry winter -> savanna
        assertEquals("TROPICAL_SAVANNA", cls(15, land(1000, 0), 0.10, 0, 0, 0), "row13");
        // 14 equatorial alpine (mountainIntent 1.0) -> rainforest alpine-stepped down one tier.
        // Pre-fix this asserted BOREAL, which matched the *old* classifyBase's fallthrough bug
        // (an uncooled-below-T_WARM tropical column fell through to a band-blind HUMID_CONTINENTAL
        // default, which alpineStep then demoted to BOREAL) -- not the design's actual intent. The
        // design comment already said "ALPINE step-down from rainforest"; TEMPERATE_OCEANIC is what
        // alpineStep(TROPICAL_RAINFOREST) actually yields (see LESSONS L14).
        assertEquals("TEMPERATE_OCEANIC", cls(3, land(100, 1.0), 0.35, 0, 0, 0), "row14");
    }

    // Row 12 paired control: rain shadow is a bounded diagnostic, cannot desertify.
    @Test
    void orographyIsBoundedDiagnostic() {
        double pShadow = ca.assemble(0, zFor(45), land(100, 0), 0.30 * R, 0.30, 0, 0.9, List.of()).precipitation01();
        double pNone = ca.assemble(0, zFor(45), land(100, 0), 0.30 * R, 0.30, 0, 0.0, List.of()).precipitation01();
        assertTrue(Math.abs(pNone - pShadow) <= 0.15, "orographic factor exceeded +/-15%: " + pNone + " vs " + pShadow);
        assertEquals("TEMPERATE_OCEANIC",
                ca.assemble(0, zFor(45), land(100, 0), 0.30 * R, 0.30, 0, 0.0, List.of()).climateClass(),
                "shadow flipped the class");
    }

    // ---- Sweeper audit 2026-07-05 (LESSONS L14) regressions ----

    // Finding #13/#20 shape: a SUBTROPICAL column cooled into the old T-gap ([T_BOREAL_HI, T_WARM) =
    // [0.42, 0.60), reachable from altitude cooling alone) with wet precipitation used to fall through
    // to a band-blind COLD_STEPPE (snowy_plains) default -- UNMASKED here, since T=0.50 is below the
    // alpine-step threshold so no alpineStep demotion runs to obscure it. Calls classifyBase directly
    // (now package-private for exactly this purpose) rather than reverse-engineering geography that
    // hits T=0.50 through the full assemble() pipeline.
    @Test
    void subtropicalCooledGapNoLongerFallsToColdSteppe() {
        ClimateClass c = ca.classifyBase(0.50, 0.55, LatitudeBand.SUBTROPICAL, SeasonalityClass.SEASONAL,
                land(500, 0), 0);
        assertEquals(ClimateClass.TEMPERATE_OCEANIC, c);
    }

    // Finding #19: mountainIntent01 is a land-oriented orographic INTENT signal (GeoAuthority has no
    // real submarine-terrain concept), but an ocean column near a plate boundary can still carry a
    // nonzero value. Applying altitude cooling to it used to crash equatorial ocean temperature toward
    // OCEAN_FROZEN with no physical basis; alt is now zeroed for ocean columns in assemble().
    @Test
    void oceanColumnNearMountainIntentStaysWarmAtEquator() {
        String c = cls(2, oceanNearMountainIntent(1.0), 0.42, 0, 0, 0);
        assertEquals("OCEAN_WARM", c, "ocean altitude-cooling bug: mountainIntent01 chilled an equatorial ocean column");
    }

    // L14 exhaustiveness sweep: classifyBase is called directly across every LatitudeBand and a
    // representative T/P grid spanning every threshold tier, so a future change that reintroduces a
    // silent gap (an uncovered T/P/band combination quietly matching some unrelated band's default)
    // fails this test loudly instead of only showing up as a plausible-looking wrong biome in play.
    @Test
    void classifyBaseNeverFallsThroughAcrossFullTPBandGrid() {
        double[] tSamples = {0.05, 0.15, 0.30, 0.45, 0.55, 0.65, 0.75, 0.95};
        double[] pSamples = {0.10, 0.30, 0.45, 0.55, 0.70};
        GeoSummary col = land(500, 0);
        for (LatitudeBand band : LatitudeBand.values()) {
            for (double T : tSamples) {
                for (double P : pSamples) {
                    ClimateClass c = ca.classifyBase(T, P, band, SeasonalityClass.SEASONAL, col, 0);
                    assertNotNull(c, "null classification at T=" + T + " P=" + P + " band=" + band);
                    // Universal cold tiers (T < T_BOREAL_HI) are band-independent by design; above
                    // that threshold a TROPICAL/SUBTROPICAL column must never resolve to a purely
                    // arctic class -- the exact shape of the pre-fix fallthrough bug.
                    if (T >= ClimateAuthorityParams.T_BOREAL_HI
                            && (band == LatitudeBand.TROPICAL || band == LatitudeBand.SUBTROPICAL)) {
                        assertNotEquals(ClimateClass.TUNDRA, c, "T=" + T + " P=" + P + " band=" + band);
                        assertNotEquals(ClimateClass.ICE_CAP, c, "T=" + T + " P=" + P + " band=" + band);
                    }
                }
            }
        }
    }

    // Sweeper finding #22 (PLAUSIBLE, not fixed -- pinned as documented, intentional behavior): the
    // SUBPOLAR classifyBase branch is temperature-blind (`P >= P_STEPPE ? BOREAL : COLD_STEPPE`), so a
    // mild current-warmed coastal column one band above TEMPERATE snaps straight to a cold taiga family
    // at the phi=50 band edge, where the same T/P one band below would give TEMPERATE_OCEANIC. This is
    // a real, previously-untested discontinuity, but "SUBPOLAR is always cool" is the band's actual
    // design intent (bandFor has no smoothing), not a fallthrough default -- unlike L14's bug, every
    // SUBPOLAR input hits an explicit, intended case. Pinning current behavior here so a future change
    // to this boundary is a deliberate design decision, not an accidental regression.
    @Test
    void subpolarBandBoundaryIsTemperatureBlindByDesign() {
        double T = 0.66, P = 0.89;
        assertEquals(ClimateClass.TEMPERATE_OCEANIC,
                ca.classifyBase(T, P, LatitudeBand.TEMPERATE, SeasonalityClass.SEASONAL, land(500, 0), 0.5),
                "TEMPERATE side of the phi=50 edge");
        assertEquals(ClimateClass.BOREAL,
                ca.classifyBase(T, P, LatitudeBand.SUBPOLAR, SeasonalityClass.SEASONAL, land(500, 0), 0.5),
                "SUBPOLAR side of the phi=50 edge -- same T/P, cold family by design");
    }

    // I1 — same latitude, different geography => different climate (no pure latitude stripe).
    @Test
    void sameLatitudeGeographySplit() {
        String coast = cls(45, land(100, 0), 0.35, 0.30, 0, 0);
        String interior = cls(45, land(2500, 0), 0.0, 0, 0, 0);
        assertNotEquals(coast, interior, "45deg coast and interior gave the same class (latitude stripe)");
    }

    // I2 — no desert in a wet place.
    @Test
    void noDesertInWetPlace() {
        String c = cls(25, land(100, 0), 0.35, 0.5, 0, 0);
        assertNotEquals("HOT_DESERT", c);
        assertNotEquals("COOL_DESERT", c);
    }

    // I3 — no rainforest in a dry interior.
    @Test
    void noRainforestInDesert() {
        // Sweeper audit #2 finding #7 (2026-07-05): this used to sit at phi=25 (SUBTROPICAL band, per
        // bandFor), where TROPICAL_RAINFOREST/TROPICAL_MONSOON are structurally unreachable regardless
        // of precipitation (classifyBase only ever produces them from the TROPICAL band case) -- the
        // assertion held by band alone, never actually exercising the dry-interior gate it claims to.
        // Moved into TROPICAL band (phi=15) so a regression that let a dry TROPICAL interior reach
        // rainforest/monsoon would actually be caught.
        String c = cls(15, land(2500, 0), 0.0, 0, 0, 0);
        assertNotEquals("TROPICAL_RAINFOREST", c);
        assertNotEquals("TROPICAL_MONSOON", c);
    }

    // I4 — temperature falls from equator to pole (earthlike curve; coarse checkpoints absorb dither).
    @Test
    void temperatureFallsTowardPoles() {
        double t0 = tempAt(0), t45 = tempAt(45), t80 = tempAt(80);
        assertTrue(t0 > t45 + 0.15, "equator not warmer than mid-lat: " + t0 + " vs " + t45);
        assertTrue(t45 > t80 + 0.15, "mid-lat not warmer than polar: " + t45 + " vs " + t80);
    }

    private double tempAt(double phi) {
        return ca.assemble(0, zFor(phi), land(100, 0), 0.2 * R, 0, 0, 0, List.of()).temperature01();
    }

    // I5 — temperature01 and precipitation01 stay in [0,1] over a full geography sweep.
    @Test
    void fieldsInRange() {
        for (double phi = 0; phi <= 90; phi += 5) {
            for (double coast : new double[]{100, 2500}) {
                for (double fetch : new double[]{0.0, 0.35}) {
                    for (double curr : new double[]{-0.9, 0, 0.9}) {
                        GeoSummary c = land(coast, 0);
                        var s = ca.assemble(0, zFor(phi), c, fetch * R, curr, 0, 0, List.of());
                        assertTrue(s.temperature01() >= 0 && s.temperature01() <= 1, "T out of range: " + s.temperature01());
                        assertTrue(s.precipitation01() >= 0 && s.precipitation01() <= 1, "P out of range: " + s.precipitation01());
                    }
                }
            }
        }
    }

    // I6 — wind vector never collapses (min |wind| > 0.10 over 0..90).
    @Test
    void windNonDegenerate() {
        for (double phi = 0; phi <= 90; phi += 1) {
            var s = ca.assemble(0, zFor(phi), land(100, 0), 0, 0, 0, 0, List.of());
            double h = Math.hypot(s.prevailingWindX(), s.prevailingWindZ());
            assertTrue(h > 0.10, "wind collapsed at phi=" + phi + " |wind|=" + h);
        }
    }

    // I7 — currents are basin-relative: swapping which side is ocean flips the sign; isthmus/interior = 0.
    @Test
    void currentBasinRelative() {
        double west = ClimateAuthority.currentModifierFor(30, true, false);  // ocean to WEST -> cold eastern boundary
        double east = ClimateAuthority.currentModifierFor(30, false, true);  // ocean to EAST -> warm western boundary
        assertTrue(west < 0, "ocean-to-west should be cold (negative): " + west);
        assertTrue(east > 0, "ocean-to-east should be warm (positive): " + east);
        assertEquals(-west, east, 1e-9, "swap did not flip sign symmetrically");
        assertEquals(0.0, ClimateAuthority.currentModifierFor(30, true, true), "isthmus (ocean both sides) not neutral");
        assertEquals(0.0, ClimateAuthority.currentModifierFor(30, false, false), "interior (ocean neither side) not neutral");
    }

    // I8 — current sign is hemisphere-independent (uses |lat|): east-facing coast is warm at |28|.
    @Test
    void currentHemisphereSymmetric() {
        assertTrue(ClimateAuthority.currentModifierFor(28, false, true) > 0,
                "east-facing coast should be a warm current in both hemispheres");
    }

    // I12 — every climateClass maps to a non-empty vanilla biome family (vanilla-only worlds classify fully).
    @Test
    void vanillaCompleteness() {
        for (ClimateClass c : ClimateClass.values()) {
            assertTrue(!c.vanillaFamily().isEmpty(), c + " has no vanilla biome family");
        }
    }

    // I13 — the temperature curve actually freezes the poles (ICE_CAP high interior, TUNDRA below it).
    @Test
    void polarFreezes() {
        assertEquals("ICE_CAP", cls(82, land(2500, 0), 0.0, 0, 0, 0), "deep polar interior not ICE_CAP");
        boolean tundraSeen = false;
        for (double phi = 67; phi <= 75; phi += 1) {
            if (cls(phi, land(2500, 0), 0.05, 0, 0, 0).equals("TUNDRA")) { tundraSeen = true; break; }
        }
        assertTrue(tundraSeen, "no TUNDRA band found between 67 and 75 deg");
    }

    // Determinism — assemble is a pure function (simulates a world reload).
    @Test
    void deterministic() {
        var a = ca.assemble(0, zFor(37), land(600, 0.2), 0.2 * R, -0.3, 0.1, 0.2, List.of());
        var b = ca.assemble(0, zFor(37), land(600, 0.2), 0.2 * R, -0.3, 0.1, 0.2, List.of());
        assertEquals(a, b);
    }
}
