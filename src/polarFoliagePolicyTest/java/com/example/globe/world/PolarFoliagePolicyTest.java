package com.example.globe.world;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PolarFoliagePolicyTest {
    private static final double EPSILON = 0.0000001;

    public static void main(String[] args) throws Exception {
        boundaryIsStrictAndSymmetric();
        activeRadiusOverridesFallback();
        foliageClassificationPreservesBerriesAndFailsOpen();
        snowySubpolarFirefliesAreRejectedWithoutChangingWarmFireflies();
        staticIntegrationProofsHold();
        treeLineIsStrictAndBelowTheFoliageLimit();
        woodyCompletionBufferDoesNotMoveTheTreeStartLine();
        blockLevelGuardClosesTheFeatureClassBypass();
        theWoodyAndFoliageTagsDoNotOverlap();
        windsweptSnowLineDescendsPolewardAndCannotOrphanSnowyGrass();
        alpineSnowLineCutsVegetationAtTheWarpLine();
        capClampDiscoversTreedBiomesFromTheirOwnFeatures();
        strippedSupportCannotStrandFloorPlants();
        System.out.println("POLAR_FOLIAGE_POLICY_TEST_PASS");
    }

    private static void boundaryIsStrictAndSymmetric() {
        for (int radius : new int[]{3_750, 5_000, 7_500, 10_000, 15_000, 20_000}) {
            for (int hemisphere : new int[]{-1, 1}) {
                double z79Point9 = hemisphere * radius * 79.9 / 90.0;
                double z80 = hemisphere * radius * 80.0 / 90.0;
                double z80Point1 = hemisphere * radius * 80.1 / 90.0;

                assertFalse(
                        PolarFoliagePolicy.isBeyondLimit(z79Point9, radius, 1),
                        "79.9 degrees retains foliage");
                assertFalse(
                        PolarFoliagePolicy.isBeyondLimit(z80, radius, 1),
                        "exactly 80 degrees retains foliage");
                assertTrue(
                        PolarFoliagePolicy.isBeyondLimit(z80Point1, radius, 1),
                        "80.1 degrees rejects foliage");
                assertNear(
                        80.1,
                        PolarFoliagePolicy.absoluteLatitudeDegrees(z80Point1, radius, 1),
                        "reported latitude matches the boundary input");
            }
        }
    }

    private static void activeRadiusOverridesFallback() {
        assertFalse(
                PolarFoliagePolicy.isBeyondLimit(4_000.0, 5_000, 3_750),
                "active radius wins over fallback");
        assertTrue(
                PolarFoliagePolicy.isBeyondLimit(4_000.0, 0, 3_750),
                "fallback covers early worldgen");
    }

    private static void foliageClassificationPreservesBerriesAndFailsOpen() {
        assertFalse(
                PolarFoliagePolicy.shouldSuppressSimpleBlock(true, false, false, true, true),
                "sweet berry bushes remain eligible beyond 80 degrees");
        assertTrue(
                PolarFoliagePolicy.shouldSuppressSimpleBlock(true, false, false, true, false),
                "ordinary foliage is rejected beyond 80 degrees");
        assertFalse(
                PolarFoliagePolicy.shouldSuppressSimpleBlock(true, false, false, false, false),
                "non-foliage simple-block features fail open");
        assertFalse(
                PolarFoliagePolicy.shouldSuppressSimpleBlock(false, false, false, true, false),
                "foliage remains eligible through exactly 80 degrees");
    }

    private static void snowySubpolarFirefliesAreRejectedWithoutChangingWarmFireflies() {
        assertFalse(
                PolarFoliagePolicy.isSubpolarOrPolar(49.9, 90, 90),
                "49.9 degrees remains temperate");
        assertTrue(
                PolarFoliagePolicy.isSubpolarOrPolar(50.0, 90, 90),
                "the subpolar boundary starts at exactly 50 degrees");
        assertTrue(
                PolarFoliagePolicy.shouldSuppressSimpleBlock(false, true, true, true, false),
                "firefly bushes are rejected in snowy subpolar and polar climates");
        assertFalse(
                PolarFoliagePolicy.shouldSuppressSimpleBlock(false, false, true, true, false),
                "firefly bushes remain eligible outside snowy subpolar and polar climates");
        assertFalse(
                PolarFoliagePolicy.shouldSuppressSimpleBlock(false, true, false, true, false),
                "other foliage remains eligible below the strict 80-degree foliage limit");
        assertFalse(
                PolarFoliagePolicy.shouldSuppressSimpleBlock(true, true, true, true, true),
                "the explicit sweet-berry exemption remains authoritative");
    }

    private static void staticIntegrationProofsHold() throws Exception {
        String biomes = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        assertTrue(
                biomes.contains("PolarFoliagePolicy.isBeyondLimit("),
                "LatitudeBiomes exposes the dedicated foliage boundary");
        assertTrue(
                biomes.contains("EXTREME_POLAR_CAP_MIN_DEG = 74.5"),
                "the separate 74.5-degree biome ecology clamp remains unchanged");

        String treeGuard = normalize(read(
                "src/main/java/com/example/globe/mixin/ExtremePolarVegetationGuardMixin.java"));
        assertTrue(
                treeGuard.contains("LatitudeBiomes.isBlockBeyondPolarWoodyLimit("),
                "tree generation uses the dedicated 72-degree TREE LINE, not the 80-degree ground"
                        + " vegetation limit — this assertion previously pinned the strict-80"
                        + " boundary and was updated deliberately when the two tiers were split"
                        + " (maintainer ruling, 2026-08-10: earthlike treeline at 70-72)");
        assertGeneratorGatePrecedesSuppression(treeGuard, "tree");

        String simpleGuard = normalize(read(
                "src/main/java/com/example/globe/mixin/ExtremePolarSimpleFoliageGuardMixin.java"));
        // 26.2 filtered by returning null from BlockStateProvider.getOptionalState. That method and
        // its nullable contract do not exist here -- 1.21.11 has only getState(RandomSource,
        // BlockPos), whose result feeds straight into canSurvive, so null would NPE instead of
        // skipping. The guard wraps canSurvive instead, which place() is bytecode-verified to treat
        // as "return false, place nothing".
        assertTrue(
                simpleGuard.contains("@Mixin(SimpleBlockFeature.class)")
                        && simpleGuard.contains("@WrapOperation(")
                        && simpleGuard.contains("BlockState;canSurvive"),
                "simple foliage is filtered at canSurvive, after the single provider sample");
        // The RNG guarantee is now directly assertable: the guard never touches the provider, so
        // getState runs exactly once, from vanilla, and worldgen stays deterministic.
        assertTrue(
                !simpleGuard.contains("toPlace()") && !simpleGuard.contains(".getState("),
                "simple foliage guard never re-samples the state provider, so RNG advances once");
        assertTrue(
                !simpleGuard.contains("@Redirect(")
                        && occurrences(simpleGuard, "getOptionalState") == 1,
                "simple foliage modifies exactly the sampled provider result instead of redirecting or resampling");
        assertTrue(
                simpleGuard.contains("Blocks.SWEET_BERRY_BUSH")
                        && simpleGuard.contains("Blocks.FIREFLY_BUSH")
                        && simpleGuard.contains("coldEnoughToSnow(")
                        && simpleGuard.contains("PolarFoliagePolicy.isSubpolarOrPolar(")
                        && simpleGuard.contains("PolarFoliagePolicy.shouldSuppressSimpleBlock("),
                "simple-block interception preserves berries and rejects fireflies only in snowy subpolar/polar climates");
        assertTrue(
                simpleGuard.contains("POLAR_FOLIAGE")
                        && simpleGuard.contains("sampledState.is(POLAR_FOLIAGE)"),
                "runtime classification delegates to the extensible custom foliage tag");
        assertGeneratorGatePrecedesSuppression(simpleGuard, "simple foliage");

        // The two tiers PARTITION this content: woody/tree-derived at the 72-degree tree line,
        // ground vegetation at the 80-degree limit. Assert against the UNION so the split cannot
        // silently drop an entry — losing one here reopens a leak in the band it belonged to.
        String foliageTag = read(
                "src/main/resources/data/globe/tags/block/polar_foliage.json");
        String woodyTag = read(
                "src/main/resources/data/globe/tags/block/polar_woody.json");
        String guardedContent = foliageTag + woodyTag;
        for (String nestedTag : new String[]{
                "#minecraft:flowers",
                "#minecraft:saplings",
                "#minecraft:leaves",
                "#minecraft:logs",
                "#minecraft:crops"}) {
            assertTrue(guardedContent.contains("\"" + nestedTag + "\""),
                    "the guarded-content tiers together must still nest " + nestedTag);
        }
        for (String block : new String[]{
                "minecraft:short_grass",
                "minecraft:tall_grass",
                "minecraft:short_dry_grass",
                "minecraft:tall_dry_grass",
                "minecraft:fern",
                "minecraft:large_fern",
                "minecraft:vine",
                "minecraft:wildflowers",
                "minecraft:bush",
                "minecraft:brown_mushroom",
                "minecraft:hanging_roots",
                "minecraft:crimson_roots",
                "minecraft:warped_roots",
                "minecraft:nether_sprouts",
                "minecraft:moss_carpet",
                "minecraft:small_dripleaf",
                "minecraft:lily_pad",
                "minecraft:leaf_litter",
                "minecraft:pumpkin",
                "minecraft:melon"}) {
            assertTrue(guardedContent.contains("\"" + block + "\""),
                    "custom foliage tag includes " + block);
        }
        assertTrue(!foliageTag.contains("minecraft:sweet_berry_bush"),
                "sweet berry bush is not directly classified as suppressible polar foliage");

        String mixins = read("src/main/resources/globe.mixins.json");
        assertTrue(
                mixins.contains("\"ExtremePolarVegetationGuardMixin\"")
                        && mixins.contains("\"ExtremePolarSimpleFoliageGuardMixin\""),
                "tree and simple-foliage guards are both registered");
    }

    /**
     * The tree line is a SECOND, lower threshold — not a replacement for the foliage limit. A single
     * cliff for all vegetation would strip grass and flowers from 72 degrees to the pole, which is
     * the opposite of the earthlike tundra this models (maintainer ruling, 2026-08-10).
     */
    private static void treeLineIsStrictAndBelowTheFoliageLimit() {
        assertTrue(PolarFoliagePolicy.MAX_WOODY_ABSOLUTE_LATITUDE_DEGREES
                        < PolarFoliagePolicy.MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES,
                "the tree line must sit BELOW the ground-vegetation limit — if they invert, or are "
                        + "made equal, the polar band silently becomes a single bare cliff again");
        assertNear(72.0, PolarFoliagePolicy.MAX_WOODY_ABSOLUTE_LATITUDE_DEGREES,
                "tree line stays at Earth's outer Arctic treeline");

        for (int radius : new int[]{3_750, 7_500, 20_000}) {
            for (int hemisphere : new int[]{-1, 1}) {
                double z71Point9 = hemisphere * radius * 71.9 / 90.0;
                double z72 = hemisphere * radius * 72.0 / 90.0;
                double z72Point1 = hemisphere * radius * 72.1 / 90.0;
                assertFalse(PolarFoliagePolicy.isBeyondWoodyLimit(z71Point9, radius, 1),
                        "71.9 degrees keeps its trees");
                assertFalse(PolarFoliagePolicy.isBeyondWoodyLimit(z72, radius, 1),
                        "exactly 72 degrees keeps its trees — the bound is strict, like the 80 one");
                assertTrue(PolarFoliagePolicy.isBeyondWoodyLimit(z72Point1, radius, 1),
                        "72.1 degrees rejects trees");
                // The band between the two limits is the point of the split.
                assertFalse(PolarFoliagePolicy.isBeyondLimit(z72Point1, radius, 1),
                        "ground vegetation survives at 72.1 — only woody content stops there");
            }
        }
    }

    private static void woodyCompletionBufferDoesNotMoveTheTreeStartLine() {
        assertTrue(PolarFoliagePolicy.WOODY_COMPLETION_BUFFER_BLOCKS == 16,
                "the completion allowance stays a tiny, reviewable canopy-sized band");
        for (int radius : new int[]{3_750, 7_500, 20_000}) {
            double treeLineZ = radius * 72.0 / 90.0;
            for (int hemisphere : new int[]{-1, 1}) {
                double justPastTreeLine = hemisphere * (treeLineZ + 1.0);
                double insideCompletionBand = hemisphere * (treeLineZ + 15.9);
                double outsideCompletionBand = hemisphere * (treeLineZ + 16.1);
                assertTrue(PolarFoliagePolicy.isBeyondWoodyLimit(justPastTreeLine, radius, 1),
                        "trees still cannot start one block past 72 degrees");
                assertFalse(PolarFoliagePolicy.isBeyondWoodyCompletionLimit(
                                insideCompletionBand, radius, 1),
                        "a legal tree can finish its canopy across the boundary");
                assertTrue(PolarFoliagePolicy.isBeyondWoodyCompletionLimit(
                                outsideCompletionBand, radius, 1),
                        "the completion band remains finite");
            }
        }
    }

    /**
     * Guards the fix for the reported defect: trees forbidden at the pole while logs and mushrooms
     * appeared anyway. The cause was that {@code globe:polar_foliage} had exactly ONE reader, and
     * that reader only ever sees {@code SimpleBlockFeature} — so {@code minecraft:fallen_tree}
     * (a sibling of {@code TreeFeature}, not a subclass) and every raw-{@code Feature} pack
     * equivalent wrote freely. A tag edit alone cannot fix that, which is why this asserts the
     * block-level reader exists rather than asserting anything about tag contents.
     */
    private static void blockLevelGuardClosesTheFeatureClassBypass() throws Exception {
        String config = read("src/main/resources/globe.mixins.json");
        assertTrue(config.contains("\"ProtoChunkPolarVegetationGuardMixin\""),
                "the block-level polar guard must be registered — unregistered, the fallen-log and "
                        + "modded-shrub bypasses silently reopen");

        String guard = read(
                "src/main/java/com/example/globe/mixin/ProtoChunkPolarVegetationGuardMixin.java");
        assertTrue(guard.contains("method = \"setBlockState\""),
                "the guard must intercept the block WRITE — guarding feature classes is what left "
                        + "fallen trees, huge mushrooms, glow lichen and block_column cane outside");
        assertTrue(guard.contains("instanceof VegetationBlock"),
                "modded ground cover must be caught by inheritance from the vanilla plant base; a "
                        + "tag-only test fails OPEN on any block a pack never tagged, which left the "
                        + "polar cap greener than a correctly-guarded biome");
        assertTrue(guard.contains("LatitudeWorldgenScope.isFeatureActive()"),
                "the guard is decoration-only — structures, bonemeal, sapling growth and /place stay untouched");
        assertTrue(guard.contains("polar_woody") && guard.contains("polar_foliage"),
                "both tiers must be consulted, or the split collapses to one threshold");
        assertTrue(guard.contains("PolarFoliagePolicy.isBeyondWoodyCompletionLimit("),
                "the block-write seam permits only the bounded canopy completion band");
        assertTrue(guard.contains("woody && pos.getY() >= LatitudeBiomes.TREE_LINE_Y"),
                "raw woody features must obey the same high-alpine tree line as TreeFeature");
        assertTrue(!guard.contains("vegetation && pos.getY() >= LatitudeBiomes.TREE_LINE_Y")
                        && !guard.contains("foliage && pos.getY() >= LatitudeBiomes.TREE_LINE_Y"),
                "the alpine backstop must not invent a bare-alpine rule for grass and flowers");

        // Both tiers, and the berry exemption, at the pure-policy level.
        assertTrue(PolarFoliagePolicy.shouldSuppressPolarBlock(true, false, true, false, false, false),
                "woody content is suppressed above the tree line even below the foliage limit");
        assertFalse(PolarFoliagePolicy.shouldSuppressPolarBlock(true, false, false, true, false, false),
                "ground vegetation survives between the tree line and the foliage limit");
        assertTrue(PolarFoliagePolicy.shouldSuppressPolarBlock(true, true, false, true, false, false),
                "ground vegetation is suppressed above the foliage limit");
        assertTrue(PolarFoliagePolicy.shouldSuppressPolarBlock(true, true, false, false, true, false),
                "an UNTAGGED modded plant is still suppressed above the foliage limit — this is the "
                        + "fail-closed half; tag membership alone let BOP tundra shrubs through");
        assertFalse(PolarFoliagePolicy.shouldSuppressPolarBlock(true, true, true, true, true, true),
                "sweet berries keep their exemption at every latitude and against every tier");
        assertFalse(PolarFoliagePolicy.shouldSuppressPolarBlock(false, false, true, true, true, false),
                "below the tree line nothing is suppressed");
    }

    /**
     * The two tiers must partition, not overlap. An entry in both would be governed by whichever
     * test runs first, which is exactly the kind of silent precedence this split exists to remove.
     */
    private static void theWoodyAndFoliageTagsDoNotOverlap() throws Exception {
        java.util.Set<String> woody = tagValues("polar_woody");
        java.util.Set<String> foliage = tagValues("polar_foliage");
        assertTrue(!woody.isEmpty() && !foliage.isEmpty(), "both tier tags must exist and be populated");
        java.util.Set<String> both = new java.util.TreeSet<>(woody);
        both.retainAll(foliage);
        assertTrue(both.isEmpty(), "a block may belong to exactly one tier, found in both: " + both);
        assertTrue(woody.contains("#minecraft:logs"),
                "logs are tree-derived and belong to the tree-line tier");
        assertFalse(foliage.contains("#minecraft:logs"),
                "logs must NOT remain in the ground-vegetation tier");
        assertTrue(foliage.contains("minecraft:short_grass"),
                "grass is ground vegetation and must survive above the tree line");
        assertFalse(woody.contains("minecraft:short_grass"),
                "grass must not be pulled down to the tree line");
    }

    private static java.util.Set<String> tagValues(String name) throws Exception {
        String raw = read("src/main/resources/data/globe/tags/block/" + name + ".json");
        java.util.Set<String> out = new java.util.TreeSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"(#?[a-z0-9_.-]+:[a-z0-9_./-]+)\"").matcher(raw);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /**
     * Two defects, one root: a flat, latitude-blind windswept snow line.
     *
     * <p>At a constant sea+27 it carpeted TEMPERATE windswept in snow that does not belong at
     * 35-50 degrees. Reverting to vanilla's sea+57 everywhere brings back the older defect — and
     * that one is a RENDERING desync, not a taste question: {@code grass_block} carries
     * {@code SnowyDirtBlock.SNOWY}, which whitens the block's top edges. Worldgen writes states
     * directly rather than through neighbour updates, so stripping the snow layer above a
     * {@code SNOWY=true} grass block leaves white-edged grass with nothing on top. Previously
     * measured on this line at 159 snowy grass blocks against 13 surviving snow layers.
     *
     * <p>So the ramp is only safe BECAUSE the guard now also clears SNOWY on the same condition.
     * Assert both halves; either alone is a regression.
     */
    /**
     * Nothing grows in the alpine snow cap (maintainer ruling, 2026-08-15).
     *
     * <p>Reported from live play: grass and flowers standing on snow blocks near a mountain top.
     * The cap's own comment claimed this was impossible — that the snow zone leaves no grass block
     * for vegetation to root in, so nothing can poke through. Saved chunks disagreed: plants sat
     * directly on {@code snow_block} well above the height where the snow cap is unconditional,
     * meaning the ground was already snow when the vegetation feature placed on it. The cap could
     * not prevent that on its own, because it only rewrites the surface — it never had a say over
     * what decoration puts on top afterwards.
     *
     * <p>The guard is at the block WRITE for the same reason the polar one is: feature classes are
     * an unbounded set and enumerating them is what left gaps last time.
     */
    private static void alpineSnowLineCutsVegetationAtTheWarpLine() throws Exception {
        String config = read("src/main/resources/globe.mixins.json");
        assertTrue(config.contains("\"AlpineSnowVegetationGuardMixin\""),
                "the alpine snow vegetation guard must be registered — unregistered, vegetation "
                        + "keeps rooting in the snow cap");

        String guard = read(
                "src/main/java/com/example/globe/mixin/AlpineSnowVegetationGuardMixin.java");
        assertTrue(guard.contains("method = \"setBlockState\""),
                "the guard must intercept the block WRITE, not a named feature class");
        assertTrue(guard.contains("instanceof VegetationBlock"),
                "modded ground cover must be caught by inheritance from the vanilla plant base, "
                        + "exactly as the polar guard catches it");
        assertTrue(guard.contains("polar_foliage"),
                "the curated foliage tag is reused rather than a second, drifting list");
        assertTrue(guard.contains("LatitudeWorldgenScope.isActive()"),
                "the guard is worldgen-only — bonemeal and player planting stay the player's");
        assertTrue(guard.contains("LatitudeBiomes.alpineSurfaceKind("),
                "the guard must ask the SAME question the snow cap asks, so the line where snow "
                        + "begins and the line where vegetation stops cannot drift apart");
        assertTrue(guard.contains("AlpineVegetationPolicy.footingOffsetBlocks("),
                "the guard must judge the block a plant is ROOTED ON, not the plant's own "
                        + "position — judging its own position removed plants rooted on real "
                        + "ground one block below the line, and split tall plants in half");
        assertTrue(guard.contains("getBlockState(footing)"),
                "the guard must read the block actually present at the footing, so snow the cap "
                        + "never placed is left alone and farmland keeps its crops");

        // The cap and the guard agree on what kind 2 means. If the cap's mapping is ever renumbered
        // this assertion fails here rather than silently unblocking vegetation in the snow.
        String cap = read("src/main/java/com/example/globe/mixin/AlpineSurfaceMixin.java");
        assertTrue(cap.contains("case 2 -> GLOBE_ALPINE_SNOW"),
                "the alpine cap still paints snow for surface kind 2");
        assertTrue(
                AlpineVegetationPolicy.SNOW_SURFACE_KIND == 2,
                "the vegetation guard reads the same surface kind the cap paints snow for");

        final int snow = AlpineVegetationPolicy.SNOW_SURFACE_KIND;

        // 1. A plant standing directly on snow is refused. This is the reported defect.
        assertTrue(
                AlpineVegetationPolicy.shouldSuppressAlpineVegetation(snow, true, true, false),
                "tagged foliage rooted on snow is refused");
        assertTrue(
                AlpineVegetationPolicy.shouldSuppressAlpineVegetation(snow, true, false, true),
                "an UNTAGGED modded plant rooted on snow is refused too — the fail-closed half, "
                        + "mirroring the polar guard's reason for testing inheritance as well as "
                        + "the tag");
        assertFalse(
                AlpineVegetationPolicy.shouldSuppressAlpineVegetation(snow, true, false, false),
                "terrain, snow and everything that is not a plant is untouched");

        // 2. A plant rooted on real ground immediately below the transition survives. Judging the
        //    plant's own position removed these: the ground is grass, but the plant occupies the
        //    first height the cap calls snow.
        assertFalse(
                AlpineVegetationPolicy.shouldSuppressAlpineVegetation(snow, false, true, true),
                "a plant rooted on ground that is not snow survives even at a height the cap "
                        + "would paint — the shelf keeps its flowers right up to the line");
        for (int belowSnow : new int[]{0, 1}) {
            assertFalse(
                    AlpineVegetationPolicy.shouldSuppressAlpineVegetation(
                            belowSnow, true, true, true),
                    "below the snow line nothing is suppressed, whatever is underfoot");
        }

        // 3. Both halves of a tall plant reach ONE decision, because both resolve to the same
        //    footing block. The previous guard judged each half at its own position, so a tall
        //    plant straddling the line kept its lower half and lost its upper one.
        assertTrue(
                AlpineVegetationPolicy.footingOffsetBlocks(false) == 1
                        && AlpineVegetationPolicy.footingOffsetBlocks(true) == 2,
                "the upper half of a two-block plant looks one further down, past its own lower "
                        + "half, to find what it is really rooted on");
        for (int lowerHalfY = 160; lowerHalfY <= 200; lowerHalfY++) {
            int lowerFooting = lowerHalfY - AlpineVegetationPolicy.footingOffsetBlocks(false);
            int upperFooting = (lowerHalfY + 1) - AlpineVegetationPolicy.footingOffsetBlocks(true);
            assertTrue(
                    lowerFooting == upperFooting,
                    "both halves of a tall plant judge the SAME footing block at lower-half y="
                            + lowerHalfY + ", so one can never be removed without the other");
        }

        // 4. Village crops on farmland are deliberately PRESERVED: farmland is not snow, so a farm
        //    plot high on a mountain is not "grass visible in snow" and is left alone. The curated
        //    foliage tag includes #minecraft:crops, so without the real-block test the cap's
        //    theoretical snow height alone would have stripped them.
        assertFalse(
                AlpineVegetationPolicy.shouldSuppressAlpineVegetation(snow, false, true, false),
                "crops on farmland keep growing inside the cap's height range — only what is "
                        + "actually rooted in snow is refused");
    }

    private static void windsweptSnowLineDescendsPolewardAndCannotOrphanSnowyGrass() throws Exception {
        // Ramp: vanilla's line equatorward of temperate, fully lowered by subpolar, monotonic between.
        assertNear(WindsweptSnowLinePolicy.VANILLA_SNOW_LINE_OFFSET_ABOVE_SEA,
                WindsweptSnowLinePolicy.snowLineOffsetForLatitude(20.0),
                "below the temperate band the line stays at vanilla's");
        assertNear(WindsweptSnowLinePolicy.VANILLA_SNOW_LINE_OFFSET_ABOVE_SEA,
                WindsweptSnowLinePolicy.snowLineOffsetForLatitude(35.0),
                "at the temperate edge the line is still vanilla's");
        assertNear(WindsweptSnowLinePolicy.SNOW_LINE_OFFSET_ABOVE_SEA,
                WindsweptSnowLinePolicy.snowLineOffsetForLatitude(50.0),
                "by the subpolar edge the line is fully lowered");
        assertNear(WindsweptSnowLinePolicy.SNOW_LINE_OFFSET_ABOVE_SEA,
                WindsweptSnowLinePolicy.snowLineOffsetForLatitude(80.0),
                "it stays lowered poleward, never rising again");
        int previous = Integer.MAX_VALUE;
        for (int deg = 30; deg <= 60; deg++) {
            int offset = WindsweptSnowLinePolicy.snowLineOffsetForLatitude(deg);
            assertTrue(offset <= previous,
                    "the snow line must descend monotonically toward the pole, as Earth's does; "
                            + "a non-monotonic ramp puts a snow band above a bare band at " + deg);
            previous = offset;
        }

        // A mid-temperate windswept column must NOT be snowed at the old blanket height.
        assertFalse(WindsweptSnowLinePolicy.appliesTo(
                        "minecraft:windswept_forest", 63 + 27, 63, 40.0),
                "at 40 degrees the old blanket sea+27 line must no longer snow windswept");
        assertTrue(WindsweptSnowLinePolicy.appliesTo(
                        "minecraft:windswept_forest", 63 + 27, 63, 55.0),
                "at 55 degrees the lowered line still applies");
        assertFalse(WindsweptSnowLinePolicy.appliesTo(
                        "minecraft:windswept_savanna", 63 + 40, 63, 55.0),
                "windswept_savanna is a warm biome and never snows, at any latitude");

        // The other half: the guard must clear SNOWY, and must decide it with the SAME predicate
        // that strips the layer. Two copies of that test is how the orphan was produced before.
        String guard = read(
                "src/main/java/com/example/globe/mixin/ProtoChunkSnowBlockGuardMixin.java");
        // Assert the WRITE, not a mention. A contains("SnowyDirtBlock.SNOWY") check passed even
        // with the setValue deleted, because the property is also READ twice just above it — the
        // same weak-assertion trap that let a 0xBEEFBEEF guard pass with one overload gutted.
        assertTrue(guard.contains("SnowyDirtBlock.SNOWY, Boolean.FALSE"),
                "the guard must actually SET SnowyDirtBlock.SNOWY to false, not merely read it — "
                        + "otherwise a stripped snow layer leaves white-edged grass with nothing "
                        + "on top, which is the reported rendering defect");
        assertTrue(guard.contains("globe$columnKeepsSnow"),
                "strip and SNOWY-clear must share one predicate");
        assertTrue(occurrences(guard, "globe$columnKeepsSnow(pos)") == 2,
                "both the SNOWY clear and the snow strip must call the shared predicate — if only "
                        + "one does, they can disagree and the orphaned-grass defect returns");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ");
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static void assertGeneratorGatePrecedesSuppression(String guard, String label) {
        int generatorGate = guard.indexOf("context.chunkGenerator()");
        // Either tier: the tree guard gates on the 72-degree tree line, the simple-block guard on
        // the 80-degree ground-vegetation limit. Both must sit behind the generator gate.
        int suppression = guard.indexOf("LatitudeBiomes.isBlockBeyondPolarFoliageLimit(");
        if (suppression < 0) {
            suppression = guard.indexOf("LatitudeBiomes.isBlockBeyondPolarWoodyLimit(");
        }
        assertTrue(
                generatorGate >= 0
                        && guard.contains("instanceof NoiseBasedChunkGenerator noise")
                        && guard.contains("!GlobeMod.shouldApplyLatitudeWorldgen(noise)")
                        && suppression > generatorGate,
                label + " must fail open for non-Latitude generators before checking latitude");
    }


    /**
     * The extreme-polar-cap clamp must decide treed-ness from the biome's OWN generation
     * settings, not only from its name. Name enumeration failed twice: windswept identities the
     * "forest" catch-all missed (2026-08-18), then biomesoplenty:snowy_fir_clearing -- a fir
     * biome whose name says "clearing" -- standing intact at 79 degrees (2026-08-31). The pins
     * here assert the RELATIONSHIPS (discovery is consulted, derives from vegetal features,
     * honors the recorded roster ruling, and has a bounded cache lifetime), never the spelling
     * of any statement, so a correct rewrite passes and only removing the property fails.
     */
    private static void capClampDiscoversTreedBiomesFromTheirOwnFeatures() throws Exception {
        String biomes = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));

        String leak = methodSection(biomes, "private static boolean isExtremePolarSoftColdLeak(");
        assertTrue(leak.contains("hasTreedVegetalDecoration(candidate)"),
                "the soft-cold-leak matcher consults feature discovery, not names alone");

        String discovery = methodSection(biomes, "private static boolean hasTreedVegetalDecoration(");
        assertTrue(discovery.contains("VEGETAL_DECORATION"),
                "discovery reads the biome's vegetal decoration step");
        assertTrue(discovery.contains("instanceof")
                        && discovery.contains("TreeFeature"),
                "treed-ness is decided by the feature TYPE the biome actually places");
        assertTrue(discovery.contains("TREED_CAP_BIOMES_KEPT_BY_RULING"),
                "discovery cannot overturn the recorded tundra-family roster ruling");
        assertTrue(discovery.contains("POLAR_ROSTER_TAGS"),
                "discovery cannot evict the maintainer's own polar roster tags -- vanilla cap"
                        + " staples (snowy_plains, ice_spikes) place token snow spruces and must"
                        + " keep their cap identity");
        assertTrue(discovery.contains("IS_OCEAN") && discovery.contains("IS_RIVER"),
                "discovery exempts aquatic biomes -- frozen ocean and frozen river place"
                        + " minecraft:trees_water, and clamping them would delete water, not trees");
        assertTrue(discovery.contains("catch") && discovery.contains("return false"),
                "discovery fails open to the pre-discovery name-only behavior");

        for (String ruled : new String[]{
                "biomesoplenty:tundra",
                "biomesoplenty:auroral_garden",
                "biomesoplenty:wintry_origin_valley",
                "terralith:cold_shrubland",
                "terralith:wintry_lowlands"}) {
            assertTrue(biomes.contains("\"" + ruled + "\""),
                    "ruling keep-list carries " + ruled + " (maintainer roster ruling)");
        }

        String clear = methodSection(biomes, "public static synchronized void clearWorldgenContext(");
        assertTrue(clear.contains("TREED_VEGETAL_CACHE.clear()"),
                "the discovery cache dies with the worldgen context -- a new pack set re-derives");
    }

    /**
     * Per-block woody stripping must not strand the stripped block's dependents. Observed live:
     * biomesoplenty:fallen_fir_log's mushrooms floating where this guard turned the log to air
     * (2026-08-31). The law: wherever the guard can have removed support (woody latitude tier or
     * the elevation tree line), a floor plant being placed over air is refused -- and the check
     * is scoped to the floor-supported block CONTRACT (VegetationBlock), never the foliage tag,
     * which legitimately contains wall- and ceiling-attached blocks such as glow lichen.
     */
    private static void strippedSupportCannotStrandFloorPlants() throws Exception {
        String guard = normalize(read(
                "src/main/java/com/example/globe/mixin/ProtoChunkPolarVegetationGuardMixin.java"));
        String method = methodSection(guard, "private void globe$suppressPolarVegetation(");

        assertTrue(method.contains("pos.below()")
                        && method.contains("isAir()"),
                "the guard inspects the support block under a dependent placement");

        java.util.regex.Matcher supportGate = java.util.regex.Pattern
                .compile("if\\s*\\(([^{]*?)\\)\\s*\\{[^}]*pos\\.below\\(\\)")
                .matcher(method);
        boolean gatedOnVegetation = false;
        while (supportGate.find()) {
            String condition = supportGate.group(1);
            if (condition.contains("vegetation")) {
                gatedOnVegetation = true;
                assertFalse(condition.contains("foliage"),
                        "the support check keys on the floor-plant contract, not the foliage tag"
                                + " -- the tag carries glow lichen, for which air below is legal");
            }
        }
        assertTrue(gatedOnVegetation,
                "the support check is reachable only for VegetationBlock placements");

        int supportAt = method.indexOf("pos.below()");
        int woodyReturnAt = method.indexOf("if (!beyondWoody)");
        assertTrue(supportAt >= 0 && woodyReturnAt > supportAt,
                "support integrity runs before the below-woody-latitude early return, so the"
                        + " elevation tree line tier is covered too");
    }

    /**
     * Bounds a member's normalized source from its signature to the next member signature.
     * Works on normalize()d text, where line structure no longer exists.
     */
    private static String methodSection(String source, String signature) {
        String needle = normalize(signature).trim();
        int start = source.indexOf(needle);
        assertTrue(start >= 0, "expected to find " + signature);
        int end = source.length();
        for (String boundary : new String[]{" private static ", " public static ", " private final ", " /** "}) {
            int next = source.indexOf(boundary, start + needle.length());
            if (next >= 0 && next < end) {
                end = next;
            }
        }
        return source.substring(start, end);
    }

    private static void assertNear(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }
}
