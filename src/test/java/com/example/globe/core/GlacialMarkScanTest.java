package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pure contracts for the physical half of {@code /latdev markGlacial}.
 *
 * <p>The scanner is intentionally specified from measured block cells rather than generator plans. The
 * TEST128 trap is an irregular, low-relief powder-snow patch embedded in ordinary snowfield, not the obsolete
 * mixed powder/solid floating bridge. These tests therefore make no shoulder, rectangle, or authored-layout
 * assertion. The Minecraft-facing command may adapt real block states to {@code PhysicalCellKind}; validity
 * belongs to the pure scanner exercised here.
 */
class GlacialMarkScanTest {

    private static final int MINIMUM_DROP = 18;

    @Test
    void sentinelIsFarBelowAnyRealHeight() {
        assertEquals(Integer.MIN_VALUE, GlacialMarkScan.UNLOADED);
    }

    @Test
    void physicalSampleUpperBoundIncludesThinCapAirAndClampsWithoutIntegerOverflow() {
        assertEquals(98, physicalSampleMaxYInclusive(95, 320),
                "RED: cover top 95 needs inclusive support/layer/air sampling through Y=98");
        assertEquals(319, physicalSampleMaxYInclusive(318, 320),
                "the inclusive sample bound clamps to the world's highest legal block");
        assertEquals(319, physicalSampleMaxYInclusive(Integer.MAX_VALUE, 320),
                "coverTop + 3 must not overflow below the world when the cover input is extreme");
        assertEquals(Integer.MAX_VALUE - 1,
                physicalSampleMaxYInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE),
                "the world-exclusive ceiling wins even at the positive integer limit");
        assertEquals(Integer.MIN_VALUE,
                physicalSampleMaxYInclusive(0, Integer.MIN_VALUE),
                "an unrepresentable world-top subtraction saturates instead of wrapping positive");
    }

    @Test
    void plainLocalMaxOverTheWindow() {
        int[][] grid = {
            {60, 61, 62},
            {63, 70, 64},
            {65, 66, 67},
        };
        assertEquals(70, GlacialMarkScan.windowedMax(grid, 1, 1, 1));
    }

    @Test
    void radiusZeroReturnsTheCentreCell() {
        int[][] grid = {{60, 61}, {62, 63}};
        assertEquals(63, GlacialMarkScan.windowedMax(grid, 1, 1, 0));
    }

    @Test
    void aPeakOutsideTheRadiusIsNotCounted() {
        int[][] grid = new int[6][6];
        for (int[] row : grid) {
            Arrays.fill(row, 64);
        }
        grid[5][5] = 90;
        assertEquals(64, GlacialMarkScan.windowedMax(grid, 1, 1, 1));
        assertEquals(90, GlacialMarkScan.windowedMax(grid, 4, 4, 1));
    }

    @Test
    void unloadedCentreMayUseLoadedNeighboursWhenTheyAreInTheWindow() {
        int unloaded = GlacialMarkScan.UNLOADED;
        int[][] grid = {
            {80, 80, 80},
            {80, unloaded, 80},
            {80, 80, 80},
        };
        assertEquals(80, GlacialMarkScan.windowedMax(grid, 1, 1, 1));
    }

    @Test
    void allUnloadedWindowReturnsUnloaded() {
        int unloaded = GlacialMarkScan.UNLOADED;
        int[][] grid = {{unloaded, unloaded}, {unloaded, unloaded}};
        assertEquals(GlacialMarkScan.UNLOADED,
                GlacialMarkScan.windowedMax(grid, 0, 0, 1));
    }

    @Test
    void windowedMaxSkipsUnloadedAndClampsAtEdges() {
        int unloaded = GlacialMarkScan.UNLOADED;
        int[][] grid = {
            {unloaded, 51, 52},
            {53, unloaded, 55},
            {56, 57, 58},
        };
        assertEquals(58, GlacialMarkScan.windowedMax(grid, 0, 0, 4));
        assertEquals(GlacialMarkScan.UNLOADED, GlacialMarkScan.windowedMax(grid, 1, 1, 0),
                "an unloaded radius-zero centre cannot invent a neighbouring height");
    }

    @Test
    void emptyOrDegenerateWindowsDegradeToUnloaded() {
        assertEquals(GlacialMarkScan.UNLOADED, GlacialMarkScan.windowedMax(null, 0, 0, 1));
        assertEquals(GlacialMarkScan.UNLOADED,
                GlacialMarkScan.windowedMax(new int[0][0], 0, 0, 1));
        assertEquals(GlacialMarkScan.UNLOADED,
                GlacialMarkScan.windowedMax(new int[][]{{64}}, 0, 0, -1));
    }

    @Test
    void connectedComponentsRemainHonestEncounterPrimitives() {
        boolean[][] mask = new boolean[8][8];
        mask[1][1] = true;
        mask[1][2] = true;
        mask[2][2] = true;
        mask[5][5] = true;
        mask[6][5] = true;
        var components = GlacialMarkScan.connectedComponents(mask);
        assertEquals(2, components.size());
        assertEquals(3, components.get(0).size());
        assertEquals(2, components.get(1).size());
    }

    @Test
    void touchingCapsAtDifferentHeightsRemainSeparateEncounterPrimitives() {
        boolean[][] mask = new boolean[5][5];
        int[][] roofY = new int[5][5];
        mask[1][1] = true;
        mask[1][2] = true;
        roofY[1][1] = 90;
        roofY[1][2] = 90;
        mask[2][2] = true;
        mask[2][3] = true;
        roofY[2][2] = 91;
        roofY[2][3] = 91;

        var components = GlacialMarkScan.connectedComponentsByValue(mask, roofY);
        assertEquals(2, components.size());
        assertEquals(2, components.get(0).size());
        assertEquals(2, components.get(1).size());
    }

    @Test
    void missingOrUnloadedValuesAreNotInventedAsComponents() {
        boolean[][] mask = {{true, true, true}};
        int[][] roofY = {{90, GlacialMarkScan.UNLOADED}};
        var components = GlacialMarkScan.connectedComponentsByValue(mask, roofY);
        assertEquals(1, components.size());
        assertEquals(1, components.get(0).size());
    }

    @Test
    void centreRepresentativeStaysInsideTheMeasuredComponent() {
        var component = List.of(new int[]{1, 1}, new int[]{1, 2}, new int[]{2, 2});
        int[] representative = GlacialMarkScan.centreRepresentative(component);
        assertNotNull(representative);
        assertEquals(1, representative[0]);
        assertEquals(2, representative[1]);
        assertNull(GlacialMarkScan.centreRepresentative(List.of()));
    }

    @Test
    void red01_connectedPowderSnowfieldIsCandidateWithoutShouldersOrRectangle() {
        PhysicalFixture fixture = PhysicalFixture.validSnowfieldTrap();
        ScanView scan = scan(fixture, "RED-01");

        assertEquals(1, scan.count("candidates"),
                "RED-01: one connected irregular surface-powder component is one candidate");
        assertEquals(1, scan.count("validTraps"),
                "RED-01: a physically complete powder-only surface component is valid without solid shoulders");
        assertEquals(1, scan.count("encounters"),
                "RED-01: encounter count is component count, never cover-column count");
        assertEquals(27, scan.count("coverColumns"),
                "RED-01: the 75%-filled 6x6 silhouette contributes its exact 27 powder covers");
    }

    @Test
    void red02_everyCoverNeedsDeepClearFallMatchingCushionAndSafeSupport() {
        PhysicalFixture valid = PhysicalFixture.validSnowfieldTrap();
        ScanView green = scan(valid, "RED-02");
        assertEquals(27, green.count("cushionMatches"),
                "RED-02: every one of the 27 covers has its own matching cushion");

        assertRejected(valid.copy().set(5, 15, 5, "DRY_SOLID"),
                "OBSTRUCTED_SHAFT", "RED-02 obstructed shaft");
        assertRejected(valid.copy().set(5, 6, 5, "AIR"),
                "MISSING_CUSHION", "RED-02 missing matching cushion");
        assertRejected(valid.copy().set(5, 5, 5, "GRAVITY_SOLID"),
                "UNSAFE_CUSHION_SUPPORT", "RED-02 gravity support");
        assertRejected(valid.copy().set(5, 5, 5, "FLUID"),
                "UNSAFE_CUSHION_SUPPORT", "RED-02 wet support");

        PhysicalFixture shallow = valid.copy()
                .set(5, 6, 5, "AIR")
                .set(5, 9, 5, "SNOW_BLOCK")
                .set(5, 10, 5, "POWDER_SNOW");
        assertRejected(shallow, "DROP_TOO_SHALLOW", "RED-02 shallow fall");
    }

    @Test
    void red03_normalSnowGapsAndOneBlockReliefRemainLegalCamouflage() {
        PhysicalFixture fixture = PhysicalFixture.validSnowfieldTrap();
        ScanView flat = scan(fixture, "RED-03");
        assertEquals(1, flat.count("validTraps"),
                "RED-03: the nine normal-snow cells inside the 6x6 powder bounding box are intentional gaps");

        fixture.lowerCoverAndColumn(8, 6);
        fixture.lowerCoverAndColumn(8, 7);
        fixture.lowerCoverAndColumn(8, 8);
        ScanView lowRelief = scan(fixture, "RED-03");
        assertEquals(1, lowRelief.count("validTraps"),
                "RED-03: adjacent powder and ordinary snow may vary by one block like the surrounding field");
        assertEquals(27, lowRelief.count("coverColumns"));
        assertEquals(0, lowRelief.reason("MISSING_COVER"),
                "RED-03: ordinary snow gaps are camouflage, not missing authored cover");
    }

    @Test
    void red04_unknownUnsafeAndPartialComponentsNeverCountValid() {
        PhysicalFixture valid = PhysicalFixture.validSnowfieldTrap();
        Map<String, PhysicalFixture> cases = new LinkedHashMap<>();
        cases.put("UNLOADED_OR_SCAN_BOUNDARY", valid.copy().set(5, 12, 5, "UNLOADED"));
        cases.put("FLUID_IN_FALL", valid.copy().set(5, 12, 5, "FLUID"));
        cases.put("BLOCK_ENTITY_IN_FALL", valid.copy().set(5, 12, 5, "BLOCK_ENTITY"));
        cases.put("MISSING_CUSHION", valid.copy().set(5, 6, 5, "AIR"));
        cases.put("OBSTRUCTED_SHAFT", valid.copy().set(5, 12, 5, "DRY_SOLID"));

        for (Map.Entry<String, PhysicalFixture> entry : cases.entrySet()) {
            ScanView rejected = scan(entry.getValue(), "RED-04 " + entry.getKey());
            assertEquals(1, rejected.count("candidates"),
                    "RED-04: the measured surface component remains a candidate for diagnostics");
            assertEquals(0, rejected.count("validTraps"),
                    "RED-04: " + entry.getKey() + " cannot count as a valid trap");
            assertEquals(0, rejected.count("encounters"),
                    "RED-04: unsafe/partial candidates cannot inflate encounter incidence");
            assertTrue(rejected.reason(entry.getKey()) >= 1,
                    "RED-04: stable rejection counter required for " + entry.getKey());
            assertTrue(rejected.count("partialComponents") + rejected.count("unsafeComponents") >= 1,
                    "RED-04: rejected candidates remain visible as partial or unsafe");
        }
    }

    @Test
    void gravityInFallIsUnsafeAndKeepsItsDedicatedReason() {
        ScanView gravity = scan(
                PhysicalFixture.validSnowfieldTrap().set(5, 12, 5, "GRAVITY_SOLID"),
                "gravity in physical fall");

        assertEquals(1, gravity.count("candidates"));
        assertEquals(0, gravity.count("validTraps"));
        assertEquals(0, gravity.count("encounters"));
        assertEquals(1, gravity.reason("GRAVITY_IN_FALL"),
                "gravity must retain a truthful named rejection instead of ordinary obstruction");
        assertEquals(1, gravity.count("unsafeComponents"),
                "a falling-block collapse hazard contributes to the unsafe census");
        assertEquals(0, gravity.count("partialComponents"),
                "a known gravity hazard is not an incomplete structural fit");
        assertEquals(0, gravity.reason("OBSTRUCTED_SHAFT"),
                "gravity must not be collapsed into the ordinary obstruction bucket");
    }

    @Test
    void red05_escapeIsDiscoveredFromBlocksAndRejectsSealsOrFallShortcuts() {
        PhysicalFixture valid = PhysicalFixture.validSnowfieldTrap();
        ScanView green = scan(valid, "RED-05");
        assertEquals(1, green.count("validEscapeRoutes"),
                "RED-05: cardinal, two-high, one-step-rise route reaches a mineable snow tail and intact plug");

        PhysicalFixture sealed = valid.copy()
                .set(11, 6, 5, "DRY_SOLID")
                .set(11, 7, 5, "DRY_SOLID");
        assertRejected(sealed, "MISSING_ESCAPE", "RED-05 sealed landing chamber");

        PhysicalFixture noHeadroom = valid.copy().set(12, 7, 5, "DRY_SOLID");
        assertRejected(noHeadroom, "MISSING_ESCAPE", "RED-05 one-block-high tunnel");

        PhysicalFixture steepStep = valid.copy();
        int[] step = steepStep.escapeRoute.get(4);
        steepStep.set(step[0], step[1], step[2], "AIR");
        steepStep.set(step[0], step[1] + 1, step[2], "SNOW_BLOCK");
        steepStep.set(step[0], step[1] + 2, step[2], "AIR");
        steepStep.set(step[0], step[1] + 3, step[2], "AIR");
        assertRejected(steepStep, "MISSING_ESCAPE", "RED-05 two-block upward step");

        PhysicalFixture shortcut = valid.copy();
        int routeFloor = shortcut.routeFloorAt(12, 6);
        shortcut.set(11, routeFloor + 1, 6, "AIR");
        shortcut.set(11, routeFloor + 2, 6, "AIR");
        assertRejected(shortcut, "ESCAPE_SHORTCUT_TO_FALL",
                "RED-05 route reconnects to fall volume away from its landing doorway");

        PhysicalFixture missingTail = valid.copy()
                .set(13, 22, 6, "AIR")
                .set(13, 23, 6, "AIR")
                .set(13, 24, 6, "AIR");
        assertRejected(missingTail, "MISSING_ESCAPE_TAIL", "RED-05 missing snow-only mine tail");

        PhysicalFixture openPlug = valid.copy().set(13, 25, 7, "AIR");
        assertRejected(openPlug, "OPEN_SURFACE_PLUG", "RED-05 already-open surface plug");
    }

    @Test
    void red06_reportHasStableCensusCountersAndRejectionReasons() {
        ScanView valid = scan(PhysicalFixture.validSnowfieldTrap(), "RED-06");
        for (String counter : List.of(
                "candidates",
                "validTraps",
                "encounters",
                "coverColumns",
                "cushionMatches",
                "validEscapeRoutes",
                "partialComponents",
                "unsafeComponents")) {
            assertTrue(valid.hasCounter(counter), "RED-06: missing stable report counter " + counter);
        }
        assertEquals(1, valid.count("candidates"));
        assertEquals(1, valid.count("validTraps"));
        assertEquals(1, valid.count("encounters"));
        assertEquals(27, valid.count("coverColumns"));
        assertEquals(27, valid.count("cushionMatches"));
        assertEquals(1, valid.count("validEscapeRoutes"));
        assertEquals(0, valid.count("partialComponents"));
        assertEquals(0, valid.count("unsafeComponents"));
        assertNotNull(valid.reasons(), "RED-06: rejectionReasons must always be present, even when empty");

        ScanView rejected = scan(PhysicalFixture.validSnowfieldTrap().set(5, 12, 5, "FLUID"), "RED-06");
        assertEquals(1, rejected.reason("FLUID_IN_FALL"),
                "RED-06: reasons are stable named counts suitable for the fixed-seed census");
    }

    @Test
    void anchoredCommandScanCountsOnlyItsSelectedComponentButKeepsTheHaloPhysical() {
        PhysicalFixture fixture = PhysicalFixture.validSnowfieldTrap()
                .set(1, PhysicalFixture.SURFACE_LAYER_Y, 1, "POWDER_SNOW");
        ScanView broad = scan(fixture, "anchor broad census");
        assertEquals(2, broad.count("candidates"));

        Object report = GlacialMarkScan.scanPhysicalTrapVolumeAt(
                fixture.measuredCells(), MINIMUM_DROP, 5, 5);
        ScanView anchored = new ScanView(report, "anchor selected census");
        assertEquals(1, anchored.count("candidates"));
        assertEquals(1, anchored.count("validTraps"));
        assertEquals(27, anchored.count("coverColumns"));
    }

    @Test
    void firmFullSnowAndCertifiedThinSnowBothRemainCamouflage() {
        PhysicalFixture fixture = PhysicalFixture.validSnowfieldTrap()
                .set(5, PhysicalFixture.SURFACE_LAYER_Y, 4, "AIR")
                .set(5, PhysicalFixture.SURFACE_SUPPORT_Y, 8, "DRY_SOLID");
        ScanView scan = scan(fixture, "full and thin snow camouflage");
        assertEquals(1, scan.count("validTraps"));
        assertEquals(27, scan.count("coverColumns"));
    }

    @Test
    void supportedThinLayerUsesItsFirmSupportHeightForImmediateCamouflage() {
        PhysicalFixture fixture = PhysicalFixture.validSnowfieldTrap();
        fixture.setImmediateCollarEffectiveY((x, z) -> 27);

        ScanView scan = scan(fixture, "snow-layer effective height");
        assertEquals(1, scan.count("validTraps"),
                "a raw layer two blocks above powder has effective relief one when its firm support is +1: "
                        + scan.reasons());
        assertEquals(27, scan.count("coverColumns"));
    }

    @Test
    void immediateCollarAllowsEffectiveRangeThreeWhenEveryCardinalStepIsOne() {
        PhysicalFixture fixture = PhysicalFixture.validSnowfieldTrap();
        fixture.movePowderCoversAtXTo(5, 25);
        fixture.movePowderCoversAtXTo(6, 25);
        fixture.movePowderCoversAtXTo(7, 25);
        fixture.movePowderCoversAtXTo(8, 26);
        fixture.movePowderCoversAtXTo(9, 26);
        fixture.movePowderCoversAtXTo(10, 26);
        fixture.setImmediateCollarEffectiveY((x, z) -> {
            if (x == 4) {
                return 24;
            }
            if (x == 11) {
                return 27;
            }
            return x <= 7 ? 25 : 26;
        });

        ScanView scan = scan(fixture, "collar effective range three");
        assertEquals(1, scan.count("validTraps"), scan.reasons().toString());
        assertEquals(27, scan.count("coverColumns"));
    }

    @Test
    void excessivePowderReliefCollarRangeAndCardinalStepRejectByReliefLaw() {
        PhysicalFixture powderReliefTwo = PhysicalFixture.validSnowfieldTrap();
        powderReliefTwo.movePowderCoversAtXTo(5, 25);
        powderReliefTwo.movePowderCoversAtXTo(6, 25);
        powderReliefTwo.movePowderCoversAtXTo(7, 26);
        powderReliefTwo.movePowderCoversAtXTo(8, 26);
        powderReliefTwo.movePowderCoversAtXTo(9, 27);
        powderReliefTwo.movePowderCoversAtXTo(10, 27);
        assertRejected(powderReliefTwo, "SURFACE_RELIEF_TOO_HIGH",
                "powder component effective relief two");

        PhysicalFixture collarRangeFour = PhysicalFixture.validSnowfieldTrap();
        collarRangeFour.movePowderCoversAtXTo(5, 25);
        collarRangeFour.movePowderCoversAtXTo(6, 25);
        collarRangeFour.movePowderCoversAtXTo(7, 25);
        collarRangeFour.movePowderCoversAtXTo(8, 26);
        collarRangeFour.movePowderCoversAtXTo(9, 26);
        collarRangeFour.movePowderCoversAtXTo(10, 26);
        collarRangeFour.setImmediateCollarEffectiveY((x, z) -> {
            if (x == 4) {
                return 23;
            }
            if (x == 11) {
                return 27;
            }
            return x <= 7 ? 25 : 26;
        });
        assertRejected(collarRangeFour, "SURFACE_RELIEF_TOO_HIGH",
                "component plus collar effective range four");

        PhysicalFixture cardinalStepTwo = PhysicalFixture.validSnowfieldTrap();
        cardinalStepTwo.setBareFullSnowEffectiveY(4, 5, 28);
        assertRejected(cardinalStepTwo, "SURFACE_RELIEF_TOO_HIGH",
                "immediate powder-to-collar effective step two");
    }

    @Test
    void dramaticTerrainBeyondImmediateCollarDoesNotPoisonCamouflageOrRoute() {
        PhysicalFixture fixture = PhysicalFixture.validSnowfieldTrap();
        fixture.openCrevasseAt(12, 4, 10);
        fixture.openCrevasseAt(3, 12, 10);

        ScanView scan = scan(fixture, "distance-two and distance-three open crevasses");
        assertEquals(1, scan.count("validTraps"));
        assertEquals(1, scan.count("validEscapeRoutes"));
        assertEquals(27, scan.count("coverColumns"));
    }

    @Test
    void naturalSurfacePowderRemainsCandidateButCannotInflateValidIncidence() {
        PhysicalFixture fixture = PhysicalFixture.validSnowfieldTrap()
                .set(1, PhysicalFixture.SURFACE_LAYER_Y, 1, "POWDER_SNOW");

        ScanView scan = scan(fixture, "natural powder plus authored trap");
        assertEquals(2, scan.count("candidates"));
        assertEquals(1, scan.count("validTraps"));
        assertEquals(1, scan.count("encounters"));
        assertEquals(27, scan.count("coverColumns"));
        assertEquals(27, scan.count("cushionMatches"));
        assertEquals(1, scan.count("validEscapeRoutes"));
    }

    @Test
    void finalTailMayMeetBareFullSnowOrFullSnowUnderAThinLayer() {
        ScanView thinLayer = scan(
                PhysicalFixture.validSnowfieldTrap(), "thin layer over final tail head");
        assertEquals(1, thinLayer.count("validEscapeRoutes"));

        PhysicalFixture bareFullSnow = PhysicalFixture.validSnowfieldTrap()
                .set(13, PhysicalFixture.SURFACE_LAYER_Y, 7, "AIR");
        ScanView bare = scan(bareFullSnow, "bare full-snow final tail head");
        assertEquals(1, bare.count("validEscapeRoutes"));
    }

    @Test
    void thinCappedTailNeedsTheOneAirCellBeyondTheLayerAndNeverTreatsUnloadedAsAir() {
        PhysicalFixture fixture = PhysicalFixture.validThinCappedTailThreeAboveCover();
        int savedWorldYOffset = 71;
        assertEquals(27, fixture.powderCovers.size());
        assertEquals(95, fixture.powderCovers.stream()
                .mapToInt(cover -> cover[1] + savedWorldYOffset).max().orElseThrow());
        assertEquals(List.of("SNOW_BLOCK", "SNOW_BLOCK", "SNOW_BLOCK", "SNOW_LAYER", "AIR"),
                java.util.stream.IntStream.rangeClosed(94, 98)
                        .mapToObj(worldY -> fixture.cells[13][worldY - savedWorldYOffset][7])
                        .toList(),
                "saved tail Y=94..96, thin layer Y=97, and required air Y=98 are exact");

        ScanView extended = scanMeasured(
                fixture.measuredCellsThrough(98 - savedWorldYOffset),
                "saved trap extended through air at Y=98");
        assertEquals(1, extended.count("candidates"));
        assertEquals(1, extended.count("validTraps"), extended.reasons().toString());
        assertEquals(1, extended.count("encounters"));
        assertEquals(27, extended.count("coverColumns"));
        assertEquals(27, extended.count("cushionMatches"));
        assertEquals(1, extended.count("validEscapeRoutes"));

        ScanView truncated = scanMeasured(
                fixture.measuredCellsThrough(97 - savedWorldYOffset),
                "saved trap truncated at thin layer Y=97");
        assertEquals(0, truncated.count("validTraps"));
        assertEquals(0, truncated.count("encounters"));
        assertEquals(1, truncated.reason("MISSING_ESCAPE_TAIL"),
                "saved Y=98 (fixture-local Y=27) is UNLOADED past the layer; it must never be invented as air: "
                        + truncated.reasons());
    }

    @Test
    void realPhysicalComponentAdapterInvokesTheTestedInclusiveUpperBoundHelper() {
        byte[] bytes;
        try (var input = GlacialMarkScanTest.class.getClassLoader().getResourceAsStream(
                "com/example/globe/LatitudeDevCommands.class")) {
            assertNotNull(input, "compiled Latitude command adapter is available for wiring proof");
            bytes = input.readAllBytes();
        } catch (java.io.IOException exception) {
            throw new AssertionError("compiled Latitude command adapter must be readable", exception);
        }

        var classModel = java.lang.classfile.ClassFile.of().parse(bytes);
        var adapter = classModel.methods().stream()
                .filter(method -> method.methodName().equalsString("scanPhysicalComponent")
                        && method.code().isPresent())
                .findFirst().orElseThrow(() ->
                        new AssertionError("scanPhysicalComponent must remain the real command adapter"));
        long helperCalls = adapter.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .filter(instruction -> instruction.owner().asInternalName()
                        .equals("com/example/globe/core/GlacialMarkScan"))
                .filter(instruction -> instruction.name()
                        .equalsString("physicalSampleMaxYInclusive"))
                .count();
        assertEquals(1L, helperCalls,
                "RED: real scanPhysicalComponent must use the tested +3/clamped helper exactly once");
    }

    private static void assertRejected(PhysicalFixture fixture, String reason, String label) {
        ScanView scan = scan(fixture, label);
        assertEquals(0, scan.count("validTraps"), label + " must not count valid");
        assertEquals(0, scan.count("encounters"), label + " must not count as an encounter");
        assertTrue(scan.reason(reason) >= 1, label + " must report " + reason);
    }

    private static int physicalSampleMaxYInclusive(int maxCoverY, int worldMaxYExclusive) {
        try {
            Method helper = GlacialMarkScan.class.getMethod(
                    "physicalSampleMaxYInclusive", int.class, int.class);
            return (int) helper.invoke(null, maxCoverY, worldMaxYExclusive);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(
                    "RED: physical scanner needs one reusable overflow-safe inclusive upper-bound law",
                    exception);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new AssertionError("physical sample upper-bound law must be directly invocable", exception);
        }
    }

    private static ScanView scanMeasured(
            GlacialMarkScan.PhysicalCellKind[][][] cells, String label) {
        return new ScanView(
                GlacialMarkScan.scanPhysicalTrapVolume(cells, MINIMUM_DROP), label);
    }

    /**
     * Reflection keeps the RED contracts compile-valid while production still carries the obsolete bridge
     * scanner. The intended pure API is:
     *
     * <pre>
     * GlacialMarkScan.scanPhysicalTrapVolume(
     *     GlacialMarkScan.PhysicalCellKind[][][] cells, int minimumDrop) -> PhysicalScanReport
     * </pre>
     *
     * with enum values used by {@link PhysicalFixture} and report accessors read by {@link ScanView}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ScanView scan(PhysicalFixture fixture, String red) {
        try {
            Class<?> kindClass = Class.forName(
                    "com.example.globe.core.GlacialMarkScan$PhysicalCellKind");
            if (!kindClass.isEnum()) {
                return fail(red + ": PhysicalCellKind must be an enum");
            }
            Object volume = Array.newInstance(kindClass,
                    fixture.sizeX, fixture.sizeY, fixture.sizeZ);
            Class<? extends Enum> enumClass = (Class<? extends Enum>) kindClass.asSubclass(Enum.class);
            for (int x = 0; x < fixture.sizeX; x++) {
                Object ys = Array.get(volume, x);
                for (int y = 0; y < fixture.sizeY; y++) {
                    Object zs = Array.get(ys, y);
                    for (int z = 0; z < fixture.sizeZ; z++) {
                        Object kind = Enum.valueOf(enumClass, fixture.cells[x][y][z]);
                        Array.set(zs, z, kind);
                    }
                }
            }
            Method method = GlacialMarkScan.class.getMethod(
                    "scanPhysicalTrapVolume", volume.getClass(), int.class);
            Object report = method.invoke(null, volume, MINIMUM_DROP);
            return new ScanView(report, red);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return fail(red + ": missing pure physical scanner API; stale floating bridge scanner remains", e);
        } catch (IllegalArgumentException e) {
            return fail(red + ": PhysicalCellKind is missing a required measured-block value: "
                    + e.getMessage(), e);
        } catch (IllegalAccessException e) {
            return fail(red + ": physical scanner API must be public", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return fail(red + ": physical scanner threw " + cause, cause);
        }
    }

    private record ScanView(Object report, String red) {

        ScanView {
            assertNotNull(report, red + ": physical scanner returned null");
        }

        boolean hasCounter(String name) {
            try {
                Method accessor = report.getClass().getMethod(name);
                return accessor.getReturnType() == int.class
                        || Number.class.isAssignableFrom(accessor.getReturnType());
            } catch (NoSuchMethodException e) {
                return false;
            }
        }

        int count(String name) {
            try {
                Object value = report.getClass().getMethod(name).invoke(report);
                if (!(value instanceof Number number)) {
                    return fail(red + ": report accessor " + name + "() must return a number");
                }
                return number.intValue();
            } catch (NoSuchMethodException e) {
                return fail(red + ": report is missing stable counter " + name + "()", e);
            } catch (IllegalAccessException | InvocationTargetException e) {
                return fail(red + ": cannot read report counter " + name + "()", e);
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Integer> reasons() {
            try {
                Object value = report.getClass().getMethod("rejectionReasons").invoke(report);
                if (!(value instanceof Map<?, ?> map)) {
                    return fail(red + ": rejectionReasons() must return a map");
                }
                Map<String, Integer> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)
                            || !(entry.getValue() instanceof Number count)) {
                        return fail(red + ": rejectionReasons() must contain String -> number entries");
                    }
                    copy.put(key, count.intValue());
                }
                return copy;
            } catch (NoSuchMethodException e) {
                return fail(red + ": report is missing rejectionReasons()", e);
            } catch (IllegalAccessException | InvocationTargetException e) {
                return fail(red + ": cannot read rejectionReasons()", e);
            }
        }

        int reason(String name) {
            return reasons().getOrDefault(name, 0);
        }
    }

    /**
     * A compact measured-block volume. X/Z are horizontal; Y increases upward. The fixture deliberately has
     * no plan, phase, route, or authored-shape metadata for the scanner to trust.
     */
    private static final class PhysicalFixture {

        private static final int SURFACE_LAYER_Y = 26;
        private static final int SURFACE_SUPPORT_Y = 25;
        private static final int CUSHION_Y = 6;
        private static final int CUSHION_SUPPORT_Y = 5;

        private final int sizeX = 16;
        private final int sizeY = 30;
        private final int sizeZ = 16;
        private final String[][][] cells = new String[sizeX][sizeY][sizeZ];
        private final List<int[]> powderCovers = new ArrayList<>();
        private final List<int[]> escapeRoute = new ArrayList<>();

        private PhysicalFixture() {
            for (String[][] ys : cells) {
                for (String[] zs : ys) {
                    Arrays.fill(zs, "DRY_SOLID");
                }
            }
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int y = SURFACE_LAYER_Y + 1; y < sizeY; y++) {
                        set(x, y, z, "AIR");
                    }
                    set(x, SURFACE_SUPPORT_Y, z, "SNOW_BLOCK");
                    set(x, SURFACE_LAYER_Y, z, "SNOW_LAYER");
                }
            }
        }

        static PhysicalFixture validSnowfieldTrap() {
            PhysicalFixture fixture = new PhysicalFixture();
            int[][] spans = {
                {5, 7},
                {4, 9},
                {5, 7},
                {4, 9},
                {6, 8},
                {4, 9},
            };
            for (int station = 0; station < spans.length; station++) {
                int x = 5 + station;
                for (int z = spans[station][0]; z <= spans[station][1]; z++) {
                    fixture.installDropColumn(x, z);
                }
            }
            fixture.installEscape();
            return fixture;
        }

        static PhysicalFixture validThinCappedTailThreeAboveCover() {
            PhysicalFixture fixture = validSnowfieldTrap();
            for (int x = 5; x <= 10; x++) {
                fixture.movePowderCoversAtXTo(x, 24);
            }
            for (int[] cover : fixture.powderCovers) {
                fixture.set(cover[0], CUSHION_Y, cover[2], "AIR");
                fixture.set(cover[0], CUSHION_SUPPORT_Y, cover[2], "POWDER_SNOW");
                fixture.set(cover[0], CUSHION_SUPPORT_Y - 1, cover[2], "SNOW_BLOCK");
            }
            // Lower only the landing doorway floor with the cushions. The next existing route floor is one
            // block higher, so the same route remains cardinal, connected, and strictly one-step walkable.
            fixture.set(11, 5, 5, "SNOW_BLOCK");
            fixture.set(11, 6, 5, "AIR");
            fixture.set(11, 7, 5, "AIR");
            return fixture;
        }

        PhysicalFixture copy() {
            PhysicalFixture copy = new PhysicalFixture();
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    System.arraycopy(cells[x][y], 0, copy.cells[x][y], 0, sizeZ);
                }
            }
            copy.powderCovers.clear();
            for (int[] cover : powderCovers) {
                copy.powderCovers.add(cover.clone());
            }
            copy.escapeRoute.clear();
            for (int[] step : escapeRoute) {
                copy.escapeRoute.add(step.clone());
            }
            return copy;
        }

        PhysicalFixture set(int x, int y, int z, String kind) {
            cells[x][y][z] = kind;
            return this;
        }

        GlacialMarkScan.PhysicalCellKind[][][] measuredCells() {
            GlacialMarkScan.PhysicalCellKind[][][] measured =
                    new GlacialMarkScan.PhysicalCellKind[sizeX][sizeY][sizeZ];
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    for (int z = 0; z < sizeZ; z++) {
                        measured[x][y][z] =
                                GlacialMarkScan.PhysicalCellKind.valueOf(cells[x][y][z]);
                    }
                }
            }
            return measured;
        }

        GlacialMarkScan.PhysicalCellKind[][][] measuredCellsThrough(int maxYInclusive) {
            int measuredHeight = (int) Math.max(0L,
                    Math.min((long) sizeY, (long) maxYInclusive + 1L));
            GlacialMarkScan.PhysicalCellKind[][][] measured =
                    new GlacialMarkScan.PhysicalCellKind[sizeX][measuredHeight][sizeZ];
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < measuredHeight; y++) {
                    for (int z = 0; z < sizeZ; z++) {
                        measured[x][y][z] =
                                GlacialMarkScan.PhysicalCellKind.valueOf(cells[x][y][z]);
                    }
                }
            }
            return measured;
        }

        void lowerCoverAndColumn(int x, int z) {
            int[] cover = powderCovers.stream()
                    .filter(cell -> cell[0] == x && cell[2] == z)
                    .findFirst().orElseThrow();
            int oldCoverY = cover[1];
            set(x, oldCoverY, z, "AIR");
            set(x, oldCoverY - 1, z, "POWDER_SNOW");
            set(x, CUSHION_Y, z, "AIR");
            set(x, CUSHION_SUPPORT_Y, z, "POWDER_SNOW");
            set(x, CUSHION_SUPPORT_Y - 1, z, "SNOW_BLOCK");
            cover[1] = oldCoverY - 1;
        }

        void movePowderCoversAtXTo(int x, int newY) {
            for (int[] cover : powderCovers) {
                if (cover[0] == x) {
                    movePowderCoverTo(cover, newY);
                }
            }
        }

        void setImmediateCollarEffectiveY(java.util.function.IntBinaryOperator effectiveY) {
            Set<Long> powder = new HashSet<>();
            for (int[] cover : powderCovers) {
                powder.add(pack(cover[0], cover[2]));
            }
            Set<Long> collar = new HashSet<>();
            for (int[] cover : powderCovers) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        int x = cover[0] + dx;
                        int z = cover[2] + dz;
                        long key = pack(x, z);
                        if (!powder.contains(key)) {
                            collar.add(key);
                        }
                    }
                }
            }
            for (long key : collar) {
                int x = (int) (key >> 32);
                int z = (int) key;
                setThinSnowEffectiveY(x, z, effectiveY.applyAsInt(x, z));
            }
        }

        void setThinSnowEffectiveY(int x, int z, int effectiveY) {
            for (int y = effectiveY + 1; y < sizeY; y++) {
                set(x, y, z, "AIR");
            }
            set(x, effectiveY, z, "SNOW_BLOCK");
            set(x, effectiveY + 1, z, "SNOW_LAYER");
        }

        void setBareFullSnowEffectiveY(int x, int z, int effectiveY) {
            for (int y = effectiveY + 1; y < sizeY; y++) {
                set(x, y, z, "AIR");
            }
            set(x, effectiveY, z, "SNOW_BLOCK");
        }

        void openCrevasseAt(int x, int z, int floorY) {
            for (int y = floorY + 1; y < sizeY; y++) {
                set(x, y, z, "AIR");
            }
        }

        int routeFloorAt(int x, int z) {
            return escapeRoute.stream()
                    .filter(step -> step[0] == x && step[2] == z)
                    .mapToInt(step -> step[1])
                    .findFirst().orElseThrow();
        }

        private void movePowderCoverTo(int[] cover, int newY) {
            for (int y = newY + 1; y < sizeY; y++) {
                set(cover[0], y, cover[2], "AIR");
            }
            set(cover[0], newY, cover[2], "POWDER_SNOW");
            cover[1] = newY;
        }

        private static long pack(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }

        private void installDropColumn(int x, int z) {
            set(x, SURFACE_LAYER_Y, z, "POWDER_SNOW");
            for (int y = CUSHION_Y + 1; y < SURFACE_LAYER_Y; y++) {
                set(x, y, z, "AIR");
            }
            set(x, CUSHION_Y, z, "POWDER_SNOW");
            set(x, CUSHION_SUPPORT_Y, z, "SNOW_BLOCK");
            powderCovers.add(new int[]{x, SURFACE_LAYER_Y, z});
        }

        private void installEscape() {
            int[][] horizontal = {
                {11, 5}, {12, 5}, {12, 4}, {12, 3}, {12, 2},
                {11, 2}, {10, 2}, {9, 2}, {8, 2}, {7, 2}, {6, 2}, {5, 2}, {4, 2}, {3, 2},
                {3, 3},
                {3, 4}, {3, 5}, {3, 6}, {3, 7}, {3, 8}, {3, 9}, {3, 10}, {3, 11},
                {4, 11}, {5, 11}, {6, 11}, {7, 11}, {8, 11}, {9, 11}, {10, 11}, {11, 11},
                {12, 11}, {12, 10}, {12, 9}, {12, 8}, {12, 7}, {12, 6},
            };
            for (int i = 0; i < horizontal.length; i++) {
                int floorY = CUSHION_SUPPORT_Y + Math.max(1, Math.min(16, i / 2));
                int x = horizontal[i][0];
                int z = horizontal[i][1];
                set(x, floorY, z, "SNOW_BLOCK");
                set(x, floorY + 1, z, "AIR");
                set(x, floorY + 2, z, "AIR");
                escapeRoute.add(new int[]{x, floorY, z});
            }

            // Two rising three-high snow columns form the mineable tail. The final column's head is the
            // original full-snow surface support, with its thin surface layer still untouched above it.
            set(13, 22, 6, "SNOW_BLOCK");
            set(13, 23, 6, "SNOW_BLOCK");
            set(13, 24, 6, "SNOW_BLOCK");
            set(13, 23, 7, "SNOW_BLOCK");
            set(13, 24, 7, "SNOW_BLOCK");
            set(13, 25, 7, "SNOW_BLOCK");
        }
    }
}
