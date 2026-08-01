package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.core.CaveDropTrap;
import com.example.globe.core.LatitudeV2Flags;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Cave-specific inset stumble carpets. The feature runs first in vegetal decoration, after ores and
 * springs have finalized the cave, and plans a whole owner-chunk scene before making any world write.
 */
public final class CaveDropTrapFeature extends Feature<NoneFeatureConfiguration> {

    public static Feature<NoneFeatureConfiguration> INSTANCE;

    private static final BlockState CUSHION_POWDER_SNOW = Blocks.POWDER_SNOW.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    static final Heightmap.Types FEATURE_STAGE_SURFACE_HEIGHTMAP = Heightmap.Types.WORLD_SURFACE;
    private static final int SCAN_TOP_Y = 100;
    private static final int SCAN_BOTTOM_Y = 8;
    private static final int MAX_DROP_SCAN = 96;
    private static final int MAX_AUTHORED_DROP = 12;
    private static final int MAX_ESCAPE_SEARCH_STATES = 16_384;
    private static final int STABLE_MASS_DEPTH = 4;
    private static final int LOWER_ROUTE_RADIUS = 8;
    private static final int LOWER_ROUTE_VERTICAL_RANGE = 5;
    private static final int FLOODED_GALLERY_CEILING = 4;
    private static final int FLOODED_POOL_DEPTH = 3;
    private static final int FLOODED_POOL_DILATION = 1;
    private static final int FLOODED_GALLERY_DILATION = 3;
    private static final int FLOODED_ORE_BUDGET = 16;
    private static final int LOST_PROSPECTOR_LOOT_BUDGET = 12;
    private static final int SET_BLOCK_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final boolean DEBUG = Boolean.getBoolean("latitude.debugCollapse");
    private static volatile Consumer<AuditEvent> auditCallback;
    /**
     * Default-off development seam for a no-write census of the exact production scan. A non-null
     * observer makes {@link #place(FeaturePlaceContext)} stop after materialization preflight, before
     * the fraction/reward/apply path. Playable generation leaves this {@code null}.
     */
    private static volatile Consumer<CensusSnapshot> censusCallback;
    /**
     * Default-off Pass-A observer for the future roofed-gallery throat. When installed it runs the
     * shared read-only predicate and returns before every production materialization or write.
     */
    private static volatile Consumer<PassACensusSnapshot> passACensusCallback;
    private static volatile IntPredicate writeFailureInjector;

    private static final ResourceKey<LootTable> LOST_PROSPECTOR_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "chests/collapsed_explorer_treasure"));

    public CaveDropTrapFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    public static void register() {
        INSTANCE = Registry.register(
                BuiltInRegistries.FEATURE,
                Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "cave_drop_trap"),
                new CaveDropTrapFeature(NoneFeatureConfiguration.CODEC));
    }

    private record DropCell(
            int lx, int lz, int floorY, int certifiedSolidDepth, int landingAirY, int dropDepth) {
    }

    private record StableFloor(int lx, int lz, int floorY) {
    }

    public enum AuthoredRole {
        ENTRANCE_POWDER,
        FIRM_CAMOUFLAGE,
        OPENED_SHAFT,
        POWDER_CUSHION,
        GALLERY_AIR,
        GALLERY_WATER,
        ORE_REWARD,
        TREASURE_CHEST,
        COLLAPSED_EXPLORER_FOOT,
        COLLAPSED_EXPLORER_HEAD
    }

    private record WorldWrite(
            BlockPos position,
            BlockState expected,
            BlockState desired,
            AuthoredRole role,
            boolean priorBlockEntityAbsent) {

        /** Explicit pre-write state name for the audit producer; {@code expected} remains the
         * transaction adapter's compatibility accessor. */
        BlockState priorState() {
            return expected;
        }
    }

    private record LandingRoute(
            BlockPos entranceTop,
            BlockPos landing,
            BlockPos dryExit,
            List<BlockPos> path) {
    }

    private record TrapGeometry(
            CaveDropTrap.InsetCarpetPlan carpet,
            List<DropCell> powder,
            List<StableFloor> regular,
            List<BlockPos> firstNaturalContinuation,
            List<BlockPos> secondNaturalContinuation,
            List<BlockPos> upperNaturalReturn,
            List<WorldWrite> upperWrites,
            List<WorldWrite> cushionWrites,
            List<LandingRoute> landingRoutes,
            ProspectorPlacement prospectorPlacement,
            BlockPos anchor,
            int fallDepth) {
    }

    private record ProspectorPlacement(
            BlockPos cushion,
            BlockPos foot,
            BlockPos head,
            BlockPos chest,
            BlockPos front,
            Direction entryDirection,
            Direction bodyFacing,
            Direction chestFacing) {
    }

    private record ScenePlan(
            CaveDropTrap.RewardKind kind,
            List<WorldWrite> writes,
            BlockPos anchor,
            CaveDropTrap.ApproachAxis axis,
            int powderCount,
            int regularCount,
            int fallDepth,
            List<BlockPos> waterPositions,
            List<BlockPos> shorePositions,
            List<BlockPos> galleryPositions,
            List<List<BlockPos>> shorePaths,
            List<BlockPos> floodedExitPath,
            BlockPos chestPosition,
            BlockPos chestFront,
            BlockPos remainsFootPosition,
            BlockPos remainsHeadPosition,
            List<List<BlockPos>> chestPaths,
            List<BlockPos> chestToExitPath,
            long lootSeed) {
    }

    public record EntryEvidence(
            BlockPos entranceTop,
            BlockPos landing,
            BlockPos dryExit,
            List<BlockPos> dryPath,
            BlockPos shore,
            List<BlockPos> shorePath,
            List<BlockPos> chestPath,
            List<BlockPos> shaftPositions) {

        public EntryEvidence {
            entranceTop = entranceTop.immutable();
            landing = landing.immutable();
            dryExit = dryExit == null ? null : dryExit.immutable();
            dryPath = immutablePositions(dryPath);
            shore = shore == null ? null : shore.immutable();
            shorePath = immutablePositions(shorePath);
            chestPath = immutablePositions(chestPath);
            shaftPositions = immutablePositions(shaftPositions);
        }

        public EntryEvidence(
                BlockPos entranceTop,
                BlockPos landing,
                BlockPos dryExit,
                List<BlockPos> dryPath,
                BlockPos shore,
                List<BlockPos> shorePath,
                List<BlockPos> chestPath) {
            this(
                    entranceTop, landing, dryExit, dryPath,
                    shore, shorePath, chestPath, List.of());
        }
    }

    public record AuthoredPosition(
            BlockPos position,
            AuthoredRole role,
            BlockState expected,
            BlockState observed,
            BlockState priorState,
            boolean priorBlockEntityAbsent) {

        public AuthoredPosition {
            position = position.immutable();
        }

        public AuthoredPosition(
                BlockPos position, AuthoredRole role, BlockState expected, BlockState observed) {
            this(position, role, expected, observed, expected, false);
        }
    }

    /**
     * Default-off proof seam. It carries only immutable JDK/Minecraft/production-core values and is
     * emitted after a complete successful scene, never during planning or a rolled-back attempt.
     */
    public record AuditEvent(
            BlockPos canonicalAnchor,
            int rewardRoll,
            CaveDropTrap.RewardKind resolvedKind,
            List<BlockPos> powderPositions,
            List<BlockPos> regularPositions,
            int landingMinY,
            int landingMaxY,
            int fallMin,
            int fallMax,
            List<BlockPos> waterPositions,
            List<BlockPos> orePositions,
            BlockPos chestPosition,
            BlockPos remainsFootPosition,
            BlockPos remainsHeadPosition,
            BlockPos sceneMin,
            BlockPos sceneMax,
            String fallbackReason,
            CaveDropTrap.ApproachAxis approachAxis,
            List<BlockPos> firstApproachBank,
            List<BlockPos> secondApproachBank,
            List<BlockPos> firmCamouflagePositions,
            List<EntryEvidence> entries,
            List<BlockPos> dryExitPositions,
            BlockPos chestFront,
            List<BlockPos> chestToExitPath,
            List<BlockPos> shorePositions,
            List<BlockPos> galleryPositions,
            List<BlockPos> floodedExitPath,
            List<AuthoredPosition> authoredPositions,
            long expectedFingerprint,
            long observedFingerprint,
            boolean postWriteVerified,
            List<BlockPos> upperBookendPositions,
            List<BlockPos> firstNaturalContinuation,
            List<BlockPos> secondNaturalContinuation,
            List<BlockPos> upperNaturalReturn) {

        public AuditEvent {
            canonicalAnchor = canonicalAnchor.immutable();
            powderPositions = immutablePositions(powderPositions);
            regularPositions = immutablePositions(regularPositions);
            waterPositions = immutablePositions(waterPositions);
            orePositions = immutablePositions(orePositions);
            chestPosition = chestPosition == null ? null : chestPosition.immutable();
            remainsFootPosition =
                    remainsFootPosition == null ? null : remainsFootPosition.immutable();
            remainsHeadPosition =
                    remainsHeadPosition == null ? null : remainsHeadPosition.immutable();
            sceneMin = sceneMin.immutable();
            sceneMax = sceneMax.immutable();
            firstApproachBank = immutablePositions(firstApproachBank);
            secondApproachBank = immutablePositions(secondApproachBank);
            firmCamouflagePositions = immutablePositions(firmCamouflagePositions);
            entries = List.copyOf(entries);
            dryExitPositions = immutablePositions(dryExitPositions);
            chestFront = chestFront == null ? null : chestFront.immutable();
            chestToExitPath = immutablePositions(chestToExitPath);
            shorePositions = immutablePositions(shorePositions);
            galleryPositions = immutablePositions(galleryPositions);
            floodedExitPath = immutablePositions(floodedExitPath);
            authoredPositions = List.copyOf(authoredPositions);
            upperBookendPositions = immutablePositions(upperBookendPositions);
            firstNaturalContinuation = immutablePositions(firstNaturalContinuation);
            secondNaturalContinuation = immutablePositions(secondNaturalContinuation);
            upperNaturalReturn = immutablePositions(upperNaturalReturn);
        }
    }

    /** One deterministic near-miss retained by the compact no-write candidate census. */
    public record CensusRepresentative(
            int stageRank,
            int chunkX,
            int chunkZ,
            CaveDropTrap.RibbonOrientation orientation,
            int originLx,
            int originLz,
            int floorY,
            int solidFailureCount,
            BlockPos firstSolidFailure,
            String firstSolidState,
            int airFailureCount,
            BlockPos firstAirFailure,
            String firstAirState,
            int enclosureMinimumRise,
            int enclosureFailingColumns,
            CensusDefectCounts solidDefects,
            int exposedGravityHazards,
            boolean stabilizationOnly) {

        public CensusRepresentative {
            firstSolidFailure = firstSolidFailure == null ? null : firstSolidFailure.immutable();
            firstAirFailure = firstAirFailure == null ? null : firstAirFailure.immutable();
            solidDefects = solidDefects == null ? CensusDefectCounts.empty() : solidDefects;
        }

        public boolean isBetterThan(CensusRepresentative other) {
            if (other == null || stageRank != other.stageRank) {
                return other == null || stageRank > other.stageRank;
            }
            if (floorY != other.floorY) {
                return floorY > other.floorY;
            }
            if (originLx != other.originLx) {
                return originLx < other.originLx;
            }
            if (originLz != other.originLz) {
                return originLz < other.originLz;
            }
            return orientation.ordinal() < other.orientation.ordinal();
        }
    }

    public enum CensusSolidDefect {
        NONE,
        FALLING_NATURAL_STABILIZABLE,
        VOID_OR_AIR,
        FLUID,
        ORE_OR_BLOCK_ENTITY,
        PROTECTED_BEDROCK_OR_MAGMA,
        OUTSIDE_OWNER,
        OTHER
    }

    /** Exact required-solid voxel defect census; counts occurrences, not distinct block states. */
    public record CensusDefectCounts(
            long fallingNaturalStabilizable,
            long voidOrAir,
            long fluid,
            long oreOrBlockEntity,
            long protectedBedrockOrMagma,
            long outsideOwner,
            long other) {

        public static CensusDefectCounts empty() {
            return new CensusDefectCounts(0, 0, 0, 0, 0, 0, 0);
        }

        public CensusDefectCounts plus(CensusDefectCounts right) {
            return new CensusDefectCounts(
                    fallingNaturalStabilizable + right.fallingNaturalStabilizable,
                    voidOrAir + right.voidOrAir,
                    fluid + right.fluid,
                    oreOrBlockEntity + right.oreOrBlockEntity,
                    protectedBedrockOrMagma + right.protectedBedrockOrMagma,
                    outsideOwner + right.outsideOwner,
                    other + right.other);
        }

        public long total() {
            return fallingNaturalStabilizable + voidOrAir + fluid + oreOrBlockEntity
                    + protectedBedrockOrMagma + outsideOwner + other;
        }

        public boolean stabilizationOnly() {
            return fallingNaturalStabilizable > 0
                    && total() == fallingNaturalStabilizable;
        }
    }

    /**
     * Per-owner-chunk counts from the no-write candidate census. The counters are deliberately a
     * closed flow so an audit can prove it did not lose slots between gate stages.
     */
    public record CensusSnapshot(
            int chunkX,
            int chunkZ,
            long scanSlots,
            long referenceFloorHeadroomPass,
            long referenceFloorHeadroomFail,
            long envelopePass,
            long envelopeSolidFail,
            long envelopeAirFail,
            long envelopeBothFail,
            long envelopeOtherFail,
            long stabilizationOnlyEnvelopePass,
            long enclosurePass,
            long enclosureFail,
            long stabilizationOnlyEnclosurePass,
            long stabilizationOnlyEnclosureFail,
            long gravityHazardPass,
            long gravityHazardFail,
            long stabilizationOnlyGravityHazardPass,
            long stabilizationOnlyGravityHazardFail,
            long materializationPass,
            long materializationFail,
            long stabilizationOnlyMaterializationNotModeled,
            CensusDefectCounts solidDefects,
            CensusRepresentative representative,
            List<CensusRepresentative> terminalRepresentatives) {

        public CensusSnapshot {
            solidDefects = solidDefects == null ? CensusDefectCounts.empty() : solidDefects;
            representative = representative == null ? null : representative;
            terminalRepresentatives = terminalRepresentatives == null
                    ? List.of() : List.copyOf(terminalRepresentatives);
        }

        public CensusSnapshot plus(CensusSnapshot other) {
            if (chunkX != other.chunkX || chunkZ != other.chunkZ) {
                throw new IllegalArgumentException("cannot merge census rows from different chunks");
            }
            CensusRepresentative best = representative;
            if (other.representative != null && other.representative.isBetterThan(best)) {
                best = other.representative;
            }
            List<CensusRepresentative> terminals =
                    mergeTerminalRepresentatives(terminalRepresentatives, other.terminalRepresentatives);
            return new CensusSnapshot(
                    chunkX, chunkZ,
                    scanSlots + other.scanSlots,
                    referenceFloorHeadroomPass + other.referenceFloorHeadroomPass,
                    referenceFloorHeadroomFail + other.referenceFloorHeadroomFail,
                    envelopePass + other.envelopePass,
                    envelopeSolidFail + other.envelopeSolidFail,
                    envelopeAirFail + other.envelopeAirFail,
                    envelopeBothFail + other.envelopeBothFail,
                    envelopeOtherFail + other.envelopeOtherFail,
                    stabilizationOnlyEnvelopePass + other.stabilizationOnlyEnvelopePass,
                    enclosurePass + other.enclosurePass,
                    enclosureFail + other.enclosureFail,
                    stabilizationOnlyEnclosurePass + other.stabilizationOnlyEnclosurePass,
                    stabilizationOnlyEnclosureFail + other.stabilizationOnlyEnclosureFail,
                    gravityHazardPass + other.gravityHazardPass,
                    gravityHazardFail + other.gravityHazardFail,
                    stabilizationOnlyGravityHazardPass
                            + other.stabilizationOnlyGravityHazardPass,
                    stabilizationOnlyGravityHazardFail
                            + other.stabilizationOnlyGravityHazardFail,
                    materializationPass + other.materializationPass,
                    materializationFail + other.materializationFail,
                    stabilizationOnlyMaterializationNotModeled
                            + other.stabilizationOnlyMaterializationNotModeled,
                    solidDefects.plus(other.solidDefects),
                    best,
                    terminals);
        }

        public boolean reconciles() {
            return scanSlots == referenceFloorHeadroomPass + referenceFloorHeadroomFail
                    && referenceFloorHeadroomPass == envelopePass + stabilizationOnlyEnvelopePass
                            + envelopeSolidFail + envelopeAirFail
                            + envelopeBothFail + envelopeOtherFail
                    && envelopePass + stabilizationOnlyEnvelopePass
                            == enclosurePass + enclosureFail
                    && stabilizationOnlyEnvelopePass
                            == stabilizationOnlyEnclosurePass
                                    + stabilizationOnlyEnclosureFail
                    && enclosurePass == gravityHazardPass + gravityHazardFail
                    && stabilizationOnlyEnclosurePass
                            == stabilizationOnlyGravityHazardPass
                                    + stabilizationOnlyGravityHazardFail
                    && gravityHazardPass
                            == materializationPass + materializationFail
                                    + stabilizationOnlyMaterializationNotModeled
                    && stabilizationOnlyGravityHazardPass
                            == stabilizationOnlyMaterializationNotModeled;
        }

        private static List<CensusRepresentative> mergeTerminalRepresentatives(
                List<CensusRepresentative> left, List<CensusRepresentative> right) {
            Map<Integer, CensusRepresentative> byStage = new HashMap<>();
            for (CensusRepresentative candidate : left) {
                byStage.merge(candidate.stageRank(), candidate,
                        (first, second) -> second.isBetterThan(first) ? second : first);
            }
            for (CensusRepresentative candidate : right) {
                byStage.merge(candidate.stageRank(), candidate,
                        (first, second) -> second.isBetterThan(first) ? second : first);
            }
            return byStage.values().stream()
                    .sorted(Comparator.comparingInt(CensusRepresentative::stageRank))
                    .toList();
        }
    }

    /** One compact, unique-anchor example for a terminal Pass-A census bucket. */
    public record PassACensusRepresentative(
            String stage,
            BlockPos ownerAnchor,
            CaveDropTrap.RibbonOrientation orientation,
            int floorY,
            CaveDropTrap.PassARejection rejection,
            CaveDropTrap.RewardFeasibilityFailure rewardFailure,
            int rewardRoll,
            CaveDropTrap.RewardKind requestedKind,
            CaveDropTrap.RewardKind resolvedKind,
            boolean fallback) {

        public PassACensusRepresentative {
            stage = Objects.requireNonNull(stage);
            ownerAnchor = ownerAnchor == null ? null : ownerAnchor.immutable();
            orientation = Objects.requireNonNull(orientation);
        }

        public boolean isBetterThan(PassACensusRepresentative other) {
            if (other == null || floorY != other.floorY) {
                return other == null || floorY > other.floorY;
            }
            int anchorOrder = Comparator
                    .comparingInt((BlockPos position) -> position.getX())
                    .thenComparingInt(position -> position.getY())
                    .thenComparingInt(position -> position.getZ())
                    .compare(ownerAnchor, other.ownerAnchor);
            return anchorOrder < 0
                    || (anchorOrder == 0 && orientation.ordinal() < other.orientation.ordinal());
        }
    }

    /** Coarse predicate stage for one exact first-return world witness. */
    public enum PassAPredicateRole {
        MOUTH_OR_CONTINUATION_FLOOR,
        COVER_FLOOR,
        HEADROOM,
        ROOF,
        AUTHORED_VOLUME_OR_OTHER,
        REWARD_PREFLIGHT,
        PASSED_STRUCTURAL,
        DUPLICATE
    }

    /** Whether a provenance row names a causal predicate query or only scan context. */
    public enum PassAProvenanceSource {
        PREDICATE_QUERY,
        REWARD_PREFLIGHT,
        NON_PREDICATE
    }

    /**
     * One bounded, concrete world witness for the call that immediately preceded the shared
     * predicate's return. Non-predicate terminals retain the owner anchor as an explicit witness.
     */
    public record PassAFirstWitness(
            String terminalBucket,
            PassAProvenanceSource source,
            PassAPredicateRole predicateRole,
            BlockPos worldPosition,
            String blockId,
            String blockKind,
            boolean actualAuthored,
            int floorY,
            int surfaceY,
            int surfaceOffset) {

        public PassAFirstWitness {
            terminalBucket = Objects.requireNonNull(terminalBucket);
            source = Objects.requireNonNull(source);
            predicateRole = Objects.requireNonNull(predicateRole);
            worldPosition = Objects.requireNonNull(worldPosition).immutable();
            blockId = Objects.requireNonNull(blockId);
            blockKind = Objects.requireNonNull(blockKind);
        }

        public PassAFirstWitness(
                String terminalBucket,
                PassAPredicateRole predicateRole,
                BlockPos worldPosition,
                String blockId,
                String blockKind,
                boolean actualAuthored,
                int floorY,
                int surfaceY,
                int surfaceOffset) {
            this(
                    terminalBucket,
                    PassAProvenanceSource.PREDICATE_QUERY,
                    predicateRole,
                    worldPosition,
                    blockId,
                    blockKind,
                    actualAuthored,
                    floorY,
                    surfaceY,
                    surfaceOffset);
        }

        public String provenanceKey() {
            return terminalBucket + "|" + source.name() + "|" + predicateRole.name() + "|"
                    + blockId + "|" + blockKind + "|" + actualAuthored;
        }

        public boolean isBetterThan(PassAFirstWitness other) {
            if (other == null || floorY != other.floorY) {
                return other == null || floorY > other.floorY;
            }
            return Comparator
                            .comparingInt((BlockPos position) -> position.getX())
                            .thenComparingInt(position -> position.getY())
                            .thenComparingInt(position -> position.getZ())
                            .compare(worldPosition, other.worldPosition)
                    < 0;
        }
    }

    /** Read-only world sample attached to one exact shared-predicate query. */
    public record PassAWorldSample(
            BlockPos worldPosition,
            String blockId,
            String blockKind,
            boolean actualAuthored,
            int surfaceY) {

        public PassAWorldSample {
            worldPosition = Objects.requireNonNull(worldPosition).immutable();
            blockId = Objects.requireNonNull(blockId);
            blockKind = Objects.requireNonNull(blockKind);
        }
    }

    /** Shared-predicate result and its exact first-return world witness. */
    public record PassATracedEvaluation(
            CaveDropTrap.RoofedThroatEvaluation evaluation,
            PassAFirstWitness firstWitness) {

        public PassATracedEvaluation {
            evaluation = Objects.requireNonNull(evaluation);
            firstWitness = Objects.requireNonNull(firstWitness);
        }
    }

    /** Exact count plus one bounded representative for a first-witness provenance key. */
    public record PassAProvenanceCount(
            String key,
            long count,
            PassAFirstWitness representative) {

        public PassAProvenanceCount {
            key = Objects.requireNonNull(key);
            representative = Objects.requireNonNull(representative);
            if (!key.equals(representative.provenanceKey())) {
                throw new IllegalArgumentException("provenance key does not match representative");
            }
        }
    }

    /** One orientation's build-height-safe floor range for the fixed Pass-A owner envelope. */
    public record NaturalCaveFloorBounds(
            CaveDropTrap.RibbonOrientation orientation,
            int minimumEnvelopeOffset,
            int maximumEnvelopeOffset,
            int minimumFloorY,
            int maximumFloorY) {

        public NaturalCaveFloorBounds {
            orientation = Objects.requireNonNull(orientation);
        }

        public long legalFloorCount() {
            return minimumFloorY > maximumFloorY
                    ? 0L : (long) maximumFloorY - minimumFloorY + 1L;
        }

        public boolean contains(int floorY) {
            return floorY >= minimumFloorY && floorY <= maximumFloorY;
        }
    }

    /** One qualified fixed-envelope candidate, before the unchanged shared predicate runs. */
    public record NaturalCaveAnchorCandidate(
            CaveDropTrap.RibbonOrientation orientation,
            int originLx,
            int originLz,
            int floorY,
            BlockPos ownerAnchor) {

        public NaturalCaveAnchorCandidate {
            orientation = Objects.requireNonNull(orientation);
            ownerAnchor = Objects.requireNonNull(ownerAnchor).immutable();
        }
    }

    /** Deterministic first-canonical-owner result used by the unchanged Pass-A duplicate funnel. */
    public record NaturalCaveAnchorDedup(
            List<NaturalCaveAnchorCandidate> uniqueCandidates,
            List<NaturalCaveAnchorCandidate> duplicateCandidates) {

        public NaturalCaveAnchorDedup {
            uniqueCandidates = List.copyOf(uniqueCandidates);
            duplicateCandidates = List.copyOf(duplicateCandidates);
        }
    }

    /**
     * Default-off Pass-A candidate source. It knows only build bounds and the existing block-kind
     * adapter, so it cannot acquire a surface, sea-level, biome, sky, RNG, or write dependency.
     */
    static final class NaturalCaveAnchorSource {
        private static final Comparator<NaturalCaveAnchorCandidate> CANONICAL_ORDER =
                Comparator
                        .comparingInt((NaturalCaveAnchorCandidate candidate) ->
                                candidate.orientation().ordinal())
                        .thenComparingInt(NaturalCaveAnchorCandidate::originLx)
                        .thenComparingInt(NaturalCaveAnchorCandidate::originLz)
                        .thenComparing(
                                NaturalCaveAnchorCandidate::floorY,
                                Comparator.reverseOrder());

        private NaturalCaveAnchorSource() {
        }

        static NaturalCaveFloorBounds floorBounds(
                int levelMinimumY,
                int levelMaximumYExclusive,
                CaveDropTrap.RibbonOrientation orientation) {
            CaveDropTrap.RoofedGalleryThroatPlan shape =
                    CaveDropTrap.planRoofedGalleryThroat(0, orientation);
            int minimumOffset = shape.ownerEnvelopeVoxels().stream()
                    .mapToInt(CaveDropTrap.Voxel::y).min().orElseThrow();
            int maximumOffset = shape.ownerEnvelopeVoxels().stream()
                    .mapToInt(CaveDropTrap.Voxel::y).max().orElseThrow();
            int minimumFloor = Math.subtractExact(levelMinimumY, minimumOffset);
            int maximumFloor = Math.subtractExact(
                    Math.subtractExact(levelMaximumYExclusive, 1),
                    maximumOffset);
            return new NaturalCaveFloorBounds(
                    orientation,
                    minimumOffset,
                    maximumOffset,
                    minimumFloor,
                    maximumFloor);
        }

        static long incrementSourceCounter(long current) {
            return Math.addExact(current, 1L);
        }

        static boolean qualifies(
                BlockPos ownerAnchor,
                Function<BlockPos, CaveDropTrap.ThroatBlockKind> blockKind) {
            Objects.requireNonNull(ownerAnchor);
            Objects.requireNonNull(blockKind);
            return blockKind.apply(ownerAnchor) == CaveDropTrap.ThroatBlockKind.SAFE_NATURAL
                    && blockKind.apply(ownerAnchor.above()) == CaveDropTrap.ThroatBlockKind.AIR
                    && blockKind.apply(ownerAnchor.above(2)) == CaveDropTrap.ThroatBlockKind.AIR;
        }

        static NaturalCaveAnchorDedup deduplicate(
                List<NaturalCaveAnchorCandidate> candidates) {
            List<NaturalCaveAnchorCandidate> ordered =
                    candidates.stream().sorted(CANONICAL_ORDER).toList();
            Map<BlockPos, NaturalCaveAnchorCandidate> firstByOwner = new LinkedHashMap<>();
            List<NaturalCaveAnchorCandidate> duplicates = new ArrayList<>();
            for (NaturalCaveAnchorCandidate candidate : ordered) {
                NaturalCaveAnchorCandidate first =
                        firstByOwner.putIfAbsent(candidate.ownerAnchor(), candidate);
                if (first != null) {
                    duplicates.add(candidate);
                }
            }
            return new NaturalCaveAnchorDedup(
                    List.copyOf(firstByOwner.values()),
                    duplicates);
        }
    }

    /** One conformal candidate: the canonical funnel identity plus its passage-fitted plan. */
    public record ConformalThroatCandidate(
            NaturalCaveAnchorCandidate identity,
            CaveDropTrap.RoofedGalleryThroatPlan plan) {

        public ConformalThroatCandidate {
            identity = Objects.requireNonNull(identity);
            plan = Objects.requireNonNull(plan);
        }
    }

    /** Proof-only conformal source result. Source rejection is a prefilter, never a gameplay decision. */
    public record ConformalPassageScan(
            long legalFloorCount,
            long auditDomainSlots,
            long caveAnchorQualified,
            long caveAnchorRejected,
            List<NaturalCaveFloorBounds> floorBounds,
            List<ConformalThroatCandidate> candidates) {

        public ConformalPassageScan {
            floorBounds = List.copyOf(floorBounds);
            candidates = List.copyOf(candidates);
        }

        public boolean reconciles() {
            long expectedDomain = checkedConformalProduct(
                    legalFloorCount, ConformalPassageThroatSource.ANCHOR_COLUMNS);
            long sourcePartition =
                    checkedConformalSum(caveAnchorQualified, caveAnchorRejected);
            return expectedDomain >= 0
                    && sourcePartition >= 0
                    && auditDomainSlots == expectedDomain
                    && auditDomainSlots == sourcePartition
                    && caveAnchorQualified == candidates.size();
        }

        private static long checkedConformalSum(long first, long second) {
            return first < 0 || second < 0 || first > Long.MAX_VALUE - second
                    ? -1L : first + second;
        }

        private static long checkedConformalProduct(long first, long second) {
            return first < 0 || second < 0
                            || (first != 0 && second > Long.MAX_VALUE / first)
                    ? -1L : first * second;
        }
    }

    /**
     * Default-off v6 Pass-A candidate source: the conformal passage throat. It starts from an
     * existing walkable cave column and traces the locked throat topology ALONG the passage --
     * corridor segments may shear laterally by one block per joint and both mouths stand on the
     * passage's own floor (at most one step up or down) -- instead of stamping the one flat
     * rectangle of the v5 source into arbitrary terrain. Every traced plan is re-certified by the
     * unchanged core topology validator and the unchanged shared world predicate. Like the v5
     * source it knows only build bounds and the existing block-kind adapter, so it cannot acquire
     * a surface, sea-level, biome, sky, RNG, or write dependency.
     */
    static final class ConformalPassageThroatSource {
        /** Every owner-inset column (local 1..14 on both axes) is one anchor column. */
        static final long ANCHOR_COLUMNS = 196L;

        /** Straight before bent, port before starboard: the deterministic joint offsets. */
        private static final int[] JOINT_OFFSET_TRIES = {0, -1, 1};
        /** Flat before down-step before up-step: the deterministic mouth floor steps. */
        private static final int[] MOUTH_STEP_TRIES = {0, -1, 1};
        /**
         * The return-side end column may not drift toward the stair shaft: a positive drift would
         * overlap the carved climb-out voxels and forge a self-authored witness.
         */
        private static final int[] RETURN_END_OFFSET_TRIES = {0, -1};

        /** One lateral lane per column of the compact throat, u -2..5 stored at index u+2. */
        static final int SHEAR_SLOTS = 8;

        /** Canonical mouth cells in plan list order: approach A first, then return B. */
        private static final int[][] MOUTH_CELLS = {
                {-2, 0}, {-2, 1}, {-1, 0}, {-1, 1},
                {4, -1}, {4, 0}, {5, -1}, {5, 0}};

        private ConformalPassageThroatSource() {
        }

        static NaturalCaveFloorBounds floorBounds(
                int levelMinimumY,
                int levelMaximumYExclusive,
                CaveDropTrap.RibbonOrientation orientation) {
            CaveDropTrap.RoofedGalleryThroatPlan shape =
                    buildPlan(0, orientation, new int[SHEAR_SLOTS], new int[8]);
            int minimumOffset = shape.ownerEnvelopeVoxels().stream()
                    .mapToInt(CaveDropTrap.Voxel::y).min().orElseThrow();
            int maximumOffset = shape.ownerEnvelopeVoxels().stream()
                    .mapToInt(CaveDropTrap.Voxel::y).max().orElseThrow();
            int minimumFloor = Math.subtractExact(levelMinimumY, minimumOffset);
            int maximumFloor = Math.subtractExact(
                    Math.subtractExact(levelMaximumYExclusive, 1),
                    maximumOffset);
            return new NaturalCaveFloorBounds(
                    orientation,
                    minimumOffset,
                    maximumOffset,
                    minimumFloor,
                    maximumFloor);
        }

        static ConformalPassageScan scan(
                int levelMinimumY,
                int levelMaximumYExclusive,
                int baseX,
                int baseZ,
                Function<BlockPos, CaveDropTrap.ThroatBlockKind> blockKind) {
            Objects.requireNonNull(blockKind);
            List<NaturalCaveFloorBounds> bounds = new ArrayList<>();
            long legalFloorCount = -1L;
            for (CaveDropTrap.RibbonOrientation orientation
                    : CaveDropTrap.RibbonOrientation.values()) {
                NaturalCaveFloorBounds orientationBounds =
                        floorBounds(levelMinimumY, levelMaximumYExclusive, orientation);
                bounds.add(orientationBounds);
                if (legalFloorCount < 0L) {
                    legalFloorCount = orientationBounds.legalFloorCount();
                } else if (legalFloorCount != orientationBounds.legalFloorCount()) {
                    throw new IllegalStateException(
                            "rotated conformal envelopes disagree on vertical extent");
                }
            }
            NaturalCaveFloorBounds reference = bounds.getFirst();
            List<ConformalThroatCandidate> candidates = new ArrayList<>();
            long auditDomainSlots = 0L;
            long caveAnchorQualified = 0L;
            long caveAnchorRejected = 0L;
            long anchorColumnCount = 0L;
            for (int anchorLx = 1; anchorLx <= 14; anchorLx++) {
                for (int anchorLz = 1; anchorLz <= 14; anchorLz++) {
                    anchorColumnCount++;
                    for (int floorY = reference.maximumFloorY();
                            floorY >= reference.minimumFloorY();
                            floorY--) {
                        auditDomainSlots =
                                NaturalCaveAnchorSource.incrementSourceCounter(auditDomainSlots);
                        BlockPos ownerAnchor = new BlockPos(
                                baseX + anchorLx, floorY, baseZ + anchorLz);
                        if (!NaturalCaveAnchorSource.qualifies(ownerAnchor, blockKind)) {
                            caveAnchorRejected = NaturalCaveAnchorSource
                                    .incrementSourceCounter(caveAnchorRejected);
                            continue;
                        }
                        CaveDropTrap.RoofedGalleryThroatPlan plan = trace(
                                anchorLx, anchorLz, floorY, baseX, baseZ, blockKind);
                        if (plan == null) {
                            caveAnchorRejected = NaturalCaveAnchorSource
                                    .incrementSourceCounter(caveAnchorRejected);
                            continue;
                        }
                        caveAnchorQualified = NaturalCaveAnchorSource
                                .incrementSourceCounter(caveAnchorQualified);
                        candidates.add(new ConformalThroatCandidate(
                                new NaturalCaveAnchorCandidate(
                                        plan.orientation(),
                                        anchorLx,
                                        anchorLz,
                                        floorY,
                                        ownerAnchor),
                                plan));
                    }
                }
            }
            if (anchorColumnCount != ANCHOR_COLUMNS) {
                throw new IllegalStateException(
                        "conformal owner inset produced " + anchorColumnCount
                                + " anchor columns, expected " + ANCHOR_COLUMNS);
            }
            ConformalPassageScan result = new ConformalPassageScan(
                    Math.max(legalFloorCount, 0L),
                    auditDomainSlots,
                    caveAnchorQualified,
                    caveAnchorRejected,
                    bounds,
                    candidates);
            if (!result.reconciles()) {
                throw new IllegalStateException(
                        "conformal passage source counters do not reconcile");
            }
            return result;
        }

        private static CaveDropTrap.RoofedGalleryThroatPlan trace(
                int anchorLx,
                int anchorLz,
                int floorY,
                int baseX,
                int baseZ,
                Function<BlockPos, CaveDropTrap.ThroatBlockKind> blockKind) {
            for (CaveDropTrap.RibbonOrientation orientation
                    : CaveDropTrap.RibbonOrientation.values()) {
                CaveDropTrap.RoofedGalleryThroatPlan plan = traceOriented(
                        anchorLx, anchorLz, floorY, orientation, baseX, baseZ, blockKind);
                if (plan != null) {
                    return plan;
                }
            }
            return null;
        }

        private static CaveDropTrap.RoofedGalleryThroatPlan traceOriented(
                int anchorLx,
                int anchorLz,
                int floorY,
                CaveDropTrap.RibbonOrientation orientation,
                int baseX,
                int baseZ,
                Function<BlockPos, CaveDropTrap.ThroatBlockKind> blockKind) {
            int[] shear = new int[SHEAR_SLOTS];
            // The anchor column carries the near firm bank and is pinned; the two powder
            // columns each bend one block relative to their neighbour; the far bank follows
            // the last powder column.
            if (!coverColumnFits(0, 0, anchorLx, anchorLz, floorY,
                    orientation, baseX, baseZ, blockKind)) {
                return null;
            }
            // Only the powder columns bend. The far bank spans columns 2 and 3, so a step
            // between them would split it into two components and break its role.
            for (int u = 1; u <= 2; u++) {
                int previous = shearAt(shear, u - 1);
                int fitted = Integer.MIN_VALUE;
                for (int offset : JOINT_OFFSET_TRIES) {
                    if (coverColumnFits(u, previous + offset, anchorLx, anchorLz,
                            floorY, orientation, baseX, baseZ, blockKind)) {
                        fitted = previous + offset;
                        break;
                    }
                }
                if (fitted == Integer.MIN_VALUE) {
                    return null;
                }
                shear[u + 2] = fitted;
            }
            if (!coverColumnFits(3, shearAt(shear, 2), anchorLx, anchorLz, floorY,
                    orientation, baseX, baseZ, blockKind)) {
                return null;
            }
            shear[5] = shearAt(shear, 2);

            int[] mouthSteps = new int[8];
            int[] mouthSpread = {Integer.MAX_VALUE, Integer.MIN_VALUE};
            int nearMouth = mouthColumnFit(-1, 0, JOINT_OFFSET_TRIES, mouthSteps,
                    mouthSpread, anchorLx, anchorLz, floorY,
                    orientation, baseX, baseZ, blockKind);
            if (nearMouth == Integer.MIN_VALUE) {
                return null;
            }
            shear[1] = nearMouth;
            int nearMouthEnd = mouthColumnFit(-2, nearMouth, JOINT_OFFSET_TRIES,
                    mouthSteps, mouthSpread, anchorLx, anchorLz, floorY,
                    orientation, baseX, baseZ, blockKind);
            if (nearMouthEnd == Integer.MIN_VALUE) {
                return null;
            }
            shear[0] = nearMouthEnd;

            // The return mouth is locked to the corridor lane so the climb-out stair, which
            // shares those columns, can never drift into the mouth it must open beside.
            mouthSpread[0] = Integer.MAX_VALUE;
            mouthSpread[1] = Integer.MIN_VALUE;
            int[] lockedFarMouth = {0};
            int farMouth = mouthColumnFit(4, shearAt(shear, 2), lockedFarMouth, mouthSteps,
                    mouthSpread, anchorLx, anchorLz, floorY,
                    orientation, baseX, baseZ, blockKind);
            if (farMouth == Integer.MIN_VALUE) {
                return null;
            }
            shear[6] = farMouth;
            int farMouthEnd = mouthColumnFit(5, farMouth, RETURN_END_OFFSET_TRIES,
                    mouthSteps, mouthSpread, anchorLx, anchorLz, floorY,
                    orientation, baseX, baseZ, blockKind);
            if (farMouthEnd == Integer.MIN_VALUE) {
                return null;
            }
            shear[7] = farMouthEnd;

            CaveDropTrap.RoofedGalleryThroatPlan plan =
                    buildPlan(floorY, orientation, shear, mouthSteps);
            for (CaveDropTrap.Voxel voxel : plan.ownerEnvelopeVoxels()) {
                int localX = anchorLx + voxel.x();
                int localZ = anchorLz + voxel.z();
                if (localX < 1 || localX > 14 || localZ < 1 || localZ > 14) {
                    return null;
                }
            }
            return plan;
        }

        private static boolean coverColumnFits(
                int u,
                int lateral,
                int anchorLx,
                int anchorLz,
                int floorY,
                CaveDropTrap.RibbonOrientation orientation,
                int baseX,
                int baseZ,
                Function<BlockPos, CaveDropTrap.ThroatBlockKind> blockKind) {
            for (int v : coverColumnCells(u)) {
                if (!cellIsWalkableFloor(u, v + lateral, floorY, anchorLx, anchorLz,
                        orientation, baseX, baseZ, blockKind)) {
                    return false;
                }
                // A carpet cell that will receive a bored shaft needs genuine solid mass under
                // it. Checking that while the bend is still being chosen lets the trace steer
                // along the passage to a stretch with rock beneath, instead of committing to a
                // thin shelf over an existing gallery and only discovering it downstream.
                if (isPowderCell(u, v)
                        && !dropVolumeIsSolid(u, v + lateral, floorY, anchorLx, anchorLz,
                                orientation, baseX, baseZ, blockKind)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isPowderCell(int u, int v) {
            return switch (u) {
                case 1 -> v >= -1 && v <= 1;
                case 2 -> v == 0 || v == 1;
                case 3 -> v == 1;
                default -> false;
            };
        }

        private static boolean dropVolumeIsSolid(
                int u,
                int v,
                int floorY,
                int anchorLx,
                int anchorLz,
                CaveDropTrap.RibbonOrientation orientation,
                int baseX,
                int baseZ,
                Function<BlockPos, CaveDropTrap.ThroatBlockKind> blockKind) {
            CaveDropTrap.Cell oriented = orientCell(u, v, orientation);
            int worldX = baseX + anchorLx + oriented.x();
            int worldZ = baseZ + anchorLz + oriented.z();
            for (int y = floorY - 1; y >= floorY - CaveDropTrap.MIN_DROP_AIR; y--) {
                CaveDropTrap.ThroatBlockKind kind =
                        blockKind.apply(new BlockPos(worldX, y, worldZ));
                if (kind == CaveDropTrap.ThroatBlockKind.AIR
                        || kind == CaveDropTrap.ThroatBlockKind.FLUID) {
                    return false;
                }
            }
            return true;
        }

        private static int mouthColumnFit(
                int u,
                int base,
                int[] offsetTries,
                int[] mouthSteps,
                int[] mouthSpread,
                int anchorLx,
                int anchorLz,
                int floorY,
                CaveDropTrap.RibbonOrientation orientation,
                int baseX,
                int baseZ,
                Function<BlockPos, CaveDropTrap.ThroatBlockKind> blockKind) {
            int[] columnCells = mouthColumnCells(u);
            for (int offset : offsetTries) {
                int lateral = base + offset;
                int candidateMinimum = mouthSpread[0];
                int candidateMaximum = mouthSpread[1];
                int[] candidateSteps = new int[columnCells.length];
                boolean fits = true;
                for (int index = 0; index < columnCells.length && fits; index++) {
                    int v = columnCells[index] + lateral;
                    int fittedStep = Integer.MIN_VALUE;
                    for (int step : MOUTH_STEP_TRIES) {
                        boolean spreadStarted = candidateMaximum != Integer.MIN_VALUE;
                        if (spreadStarted
                                && (step < candidateMaximum - 1
                                        || step > candidateMinimum + 1)) {
                            continue;
                        }
                        if (cellIsWalkableFloor(u, v, floorY + step, anchorLx, anchorLz,
                                orientation, baseX, baseZ, blockKind)) {
                            fittedStep = step;
                            break;
                        }
                    }
                    if (fittedStep == Integer.MIN_VALUE) {
                        fits = false;
                    } else {
                        candidateSteps[index] = fittedStep;
                        candidateMinimum = Math.min(candidateMinimum, fittedStep);
                        candidateMaximum = Math.max(candidateMaximum, fittedStep);
                    }
                }
                if (!fits) {
                    continue;
                }
                for (int index = 0; index < columnCells.length; index++) {
                    mouthSteps[mouthStepIndex(u, columnCells[index])] =
                            candidateSteps[index];
                }
                mouthSpread[0] = candidateMinimum;
                mouthSpread[1] = candidateMaximum;
                return lateral;
            }
            return Integer.MIN_VALUE;
        }

        private static boolean cellIsWalkableFloor(
                int u,
                int v,
                int standY,
                int anchorLx,
                int anchorLz,
                CaveDropTrap.RibbonOrientation orientation,
                int baseX,
                int baseZ,
                Function<BlockPos, CaveDropTrap.ThroatBlockKind> blockKind) {
            CaveDropTrap.Cell oriented = orientCell(u, v, orientation);
            int localX = anchorLx + oriented.x();
            int localZ = anchorLz + oriented.z();
            if (localX < 1 || localX > 14 || localZ < 1 || localZ > 14) {
                return false;
            }
            int worldX = baseX + localX;
            int worldZ = baseZ + localZ;
            return blockKind.apply(new BlockPos(worldX, standY, worldZ))
                            == CaveDropTrap.ThroatBlockKind.SAFE_NATURAL
                    && blockKind.apply(new BlockPos(worldX, standY + 1, worldZ))
                            == CaveDropTrap.ThroatBlockKind.AIR
                    && blockKind.apply(new BlockPos(worldX, standY + 2, worldZ))
                            == CaveDropTrap.ThroatBlockKind.AIR;
        }

        private static int[] coverColumnCells(int u) {
            return new int[] {-1, 0, 1};
        }

        private static int[] mouthColumnCells(int u) {
            return u < 0 ? new int[] {0, 1} : new int[] {-1, 0};
        }

        private static int mouthStepIndex(int u, int v) {
            for (int index = 0; index < MOUTH_CELLS.length; index++) {
                if (MOUTH_CELLS[index][0] == u && MOUTH_CELLS[index][1] == v) {
                    return index;
                }
            }
            throw new IllegalArgumentException("unknown mouth cell " + u + "," + v);
        }

        private static int shearAt(int[] shear, int u) {
            return shear[u + 2];
        }

        /** Mirrors the private core orientation transform exactly. */
        private static CaveDropTrap.Cell orientCell(
                int u, int v, CaveDropTrap.RibbonOrientation orientation) {
            return switch (orientation) {
                case NORTH -> new CaveDropTrap.Cell(u, v);
                case EAST -> new CaveDropTrap.Cell(-v, u);
                case SOUTH -> new CaveDropTrap.Cell(-u, -v);
                case WEST -> new CaveDropTrap.Cell(v, -u);
            };
        }

        private static List<CaveDropTrap.Cell> orientCells(
                List<CaveDropTrap.Cell> cells,
                CaveDropTrap.RibbonOrientation orientation) {
            return cells.stream()
                    .map(cell -> orientCell(cell.x(), cell.z(), orientation))
                    .toList();
        }

        /**
         * Builds one conformal throat plan. With an all-zero shear and all-zero mouth steps the
         * result is exactly {@link CaveDropTrap#planRoofedGalleryThroat}; the focused contract
         * test pins that equality so every law proven for the locked plan carries over.
         */
        static CaveDropTrap.RoofedGalleryThroatPlan buildPlan(
                int floorY,
                CaveDropTrap.RibbonOrientation orientation,
                int[] shear,
                int[] mouthSteps) {
            if (orientation == null) {
                return null;
            }
            int landingY = floorY - CaveDropTrap.MIN_DROP_AIR;
            List<CaveDropTrap.Cell> coverCanonical = new ArrayList<>();
            for (int u = 0; u <= 3; u++) {
                for (int v = -1; v <= 1; v++) {
                    coverCanonical.add(new CaveDropTrap.Cell(u, v + shearAt(shear, u)));
                }
            }
            List<CaveDropTrap.Cell> powderCanonical = shearedCells(shear, new int[][] {
                    {1, -1}, {1, 0}, {1, 1},
                    {2, 0}, {2, 1},
                    {3, 1}});
            List<CaveDropTrap.Cell> firstFirmCanonical = shearedCells(shear, new int[][] {
                    {0, -1}, {0, 0}, {0, 1}});
            List<CaveDropTrap.Cell> secondFirmCanonical = shearedCells(shear, new int[][] {
                    {2, -1}, {3, -1}, {3, 0}});
            List<CaveDropTrap.Cell> approachCanonical = shearedCells(shear, new int[][] {
                    {-2, 0}, {-2, 1},
                    {-1, 0}, {-1, 1}});
            List<CaveDropTrap.Cell> returnCanonical = shearedCells(shear, new int[][] {
                    {4, -1}, {4, 0},
                    {5, -1}, {5, 0}});

            List<CaveDropTrap.Cell> cover = orientCells(coverCanonical, orientation);
            List<CaveDropTrap.Cell> powder = orientCells(powderCanonical, orientation);
            List<CaveDropTrap.Cell> firstFirm = orientCells(firstFirmCanonical, orientation);
            List<CaveDropTrap.Cell> secondFirm = orientCells(secondFirmCanonical, orientation);
            List<CaveDropTrap.Cell> firm = new ArrayList<>(firstFirm);
            firm.addAll(secondFirm);
            List<CaveDropTrap.Cell> approach = orientCells(approachCanonical, orientation);
            List<CaveDropTrap.Cell> returned = orientCells(returnCanonical, orientation);
            List<CaveDropTrap.Cell> relevant = new ArrayList<>(cover);
            relevant.addAll(approach);
            relevant.addAll(returned);

            Map<CaveDropTrap.Cell, Integer> mouthFloorByCell = new LinkedHashMap<>();
            for (int index = 0; index < 4; index++) {
                mouthFloorByCell.put(approach.get(index), floorY + mouthSteps[index]);
            }
            for (int index = 0; index < 4; index++) {
                mouthFloorByCell.put(returned.get(index), floorY + mouthSteps[4 + index]);
            }

            LinkedHashSet<CaveDropTrap.Voxel> continuationFloor = new LinkedHashSet<>();
            for (CaveDropTrap.Cell cell : approach) {
                continuationFloor.add(cellVoxel(cell, mouthFloorByCell.get(cell)));
            }
            for (CaveDropTrap.Cell cell : returned) {
                continuationFloor.add(cellVoxel(cell, mouthFloorByCell.get(cell)));
            }
            LinkedHashSet<CaveDropTrap.Voxel> headroom = new LinkedHashSet<>();
            for (CaveDropTrap.Cell cell : relevant) {
                int base = mouthFloorByCell.getOrDefault(cell, floorY);
                headroom.add(cellVoxel(cell, base + 1));
                headroom.add(cellVoxel(cell, base + 2));
            }

            LinkedHashSet<CaveDropTrap.Voxel> ordinary = new LinkedHashSet<>();
            LinkedHashSet<CaveDropTrap.Voxel> carvedAir = new LinkedHashSet<>();
            for (CaveDropTrap.Cell cell : cover) {
                ordinary.add(cellVoxel(cell, floorY));
            }
            for (CaveDropTrap.Cell cell : powder) {
                for (int y = floorY - 1; y >= landingY; y--) {
                    CaveDropTrap.Voxel target = cellVoxel(cell, y);
                    ordinary.add(target);
                    if (y > landingY) {
                        carvedAir.add(target);
                    }
                }
            }

            int corridorLateral = shearAt(shear, 2);
            List<CaveDropTrap.Cell> lowerCorridorCanonical = new ArrayList<>();
            for (int[] cell : new int[][] {
                    {2, 2}, {3, 2}, {3, 3},
                    {3, 4}, {4, 2}}) {
                lowerCorridorCanonical.add(
                        new CaveDropTrap.Cell(cell[0], cell[1] + corridorLateral));
            }
            List<CaveDropTrap.Cell> lowerCorridor =
                    orientCells(lowerCorridorCanonical, orientation);
            LinkedHashSet<CaveDropTrap.Voxel> lowerStanding = new LinkedHashSet<>();
            for (CaveDropTrap.Cell cell : powder) {
                lowerStanding.add(cellVoxel(cell, landingY + 1));
            }
            for (CaveDropTrap.Cell cell : lowerCorridor) {
                CaveDropTrap.Voxel standing = cellVoxel(cell, landingY);
                lowerStanding.add(standing);
                carvedAir.add(standing);
                carvedAir.add(standing.above());
            }

            List<CaveDropTrap.Cell> stairCanonical = new ArrayList<>();
            for (int[] cell : new int[][] {
                    {5, 2}, {5, 1}, {4, 1},
                    {4, 2}, {5, 2}, {5, 1},
                    {4, 1}, {4, 2}, {5, 2},
                    {5, 1}, {4, 1}}) {
                stairCanonical.add(
                        new CaveDropTrap.Cell(cell[0], cell[1] + corridorLateral));
            }
            List<CaveDropTrap.Cell> stairCells = orientCells(stairCanonical, orientation);
            List<CaveDropTrap.Voxel> stairStanding = new ArrayList<>();
            for (int index = 0; index < stairCells.size(); index++) {
                CaveDropTrap.Voxel standing =
                        cellVoxel(stairCells.get(index), landingY + 1 + index);
                stairStanding.add(standing);
                lowerStanding.add(standing);
                carvedAir.add(standing);
                carvedAir.add(standing.above());
            }
            ordinary.addAll(carvedAir);

            LinkedHashSet<CaveDropTrap.Voxel> firmSupports = new LinkedHashSet<>();
            for (CaveDropTrap.Cell cell : firm) {
                for (int depth = 1;
                        depth <= CaveDropTrap.APPROACH_CONTINUATION_SUPPORT_DEPTH;
                        depth++) {
                    firmSupports.add(cellVoxel(cell, floorY - depth));
                }
            }
            Set<CaveDropTrap.Cell> powderSetForSupports = Set.copyOf(powder);
            for (CaveDropTrap.Voxel standing : lowerStanding) {
                if (!powderSetForSupports.contains(
                        new CaveDropTrap.Cell(standing.x(), standing.z()))) {
                    firmSupports.add(standing.below());
                }
            }
            LinkedHashSet<CaveDropTrap.Voxel> cushionSupports = new LinkedHashSet<>();
            for (CaveDropTrap.Cell cell : powder) {
                for (int depth = 1;
                        depth <= CaveDropTrap.APPROACH_CONTINUATION_SUPPORT_DEPTH;
                        depth++) {
                    cushionSupports.add(cellVoxel(cell, landingY - depth));
                }
            }

            Set<CaveDropTrap.Cell> mouthColumns = new HashSet<>(approach);
            mouthColumns.addAll(returned);
            LinkedHashSet<CaveDropTrap.Voxel> shell = new LinkedHashSet<>();
            Set<CaveDropTrap.Voxel> ordinarySet = Set.copyOf(ordinary);
            for (CaveDropTrap.Voxel target : ordinary) {
                if (target.y() > floorY) {
                    continue;
                }
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int distance = Math.abs(dx) + Math.abs(dz);
                        if (distance < 1 || distance > 2) {
                            continue;
                        }
                        CaveDropTrap.Voxel witness = new CaveDropTrap.Voxel(
                                target.x() + dx, target.y(), target.z() + dz);
                        if (!ordinarySet.contains(witness)
                                && !mouthColumns.contains(
                                        new CaveDropTrap.Cell(witness.x(), witness.z()))) {
                            shell.add(witness);
                        }
                    }
                }
            }

            LinkedHashSet<CaveDropTrap.Voxel> ownerEnvelope = new LinkedHashSet<>();
            ownerEnvelope.addAll(continuationFloor);
            ownerEnvelope.addAll(headroom);
            ownerEnvelope.addAll(ordinary);
            ownerEnvelope.addAll(firmSupports);
            ownerEnvelope.addAll(cushionSupports);
            ownerEnvelope.addAll(shell);
            for (CaveDropTrap.Cell cell : relevant) {
                for (int y = floorY + CaveDropTrap.MIN_ROOF_SEARCH_RISE;
                        y <= floorY + CaveDropTrap.MAX_ROOF_SEARCH_RISE
                                + CaveDropTrap.ROOF_CLEARANCE_ABOVE;
                        y++) {
                    ownerEnvelope.add(cellVoxel(cell, y));
                }
            }

            return new CaveDropTrap.RoofedGalleryThroatPlan(
                    orientation, floorY, landingY,
                    cover, powder, firm, List.of(firstFirm, secondFirm),
                    approach, returned, relevant,
                    List.copyOf(continuationFloor), List.copyOf(headroom),
                    List.copyOf(ordinary), List.copyOf(carvedAir),
                    List.copyOf(firmSupports), List.copyOf(cushionSupports),
                    List.copyOf(shell), List.copyOf(lowerStanding),
                    List.copyOf(stairStanding), List.copyOf(ownerEnvelope));
        }

        private static List<CaveDropTrap.Cell> shearedCells(int[] shear, int[][] cells) {
            List<CaveDropTrap.Cell> sheared = new ArrayList<>(cells.length);
            for (int[] cell : cells) {
                sheared.add(new CaveDropTrap.Cell(
                        cell[0], cell[1] + shearAt(shear, cell[0])));
            }
            return sheared;
        }

        private static CaveDropTrap.Voxel cellVoxel(CaveDropTrap.Cell cell, int y) {
            return new CaveDropTrap.Voxel(cell.x(), y, cell.z());
        }

        static NaturalCaveAnchorDedup deduplicate(
                List<ConformalThroatCandidate> candidates) {
            return NaturalCaveAnchorSource.deduplicate(
                    candidates.stream().map(ConformalThroatCandidate::identity).toList());
        }
    }

    /** One qualified tuple's final disposition before the selector is allowed to see RNG. */
    record PassASelectorSlot(
            NaturalCaveAnchorCandidate candidate,
            boolean duplicate,
            CaveDropTrap.RoofedGalleryThroatPlan plan,
            CaveDropTrap.RoofedThroatEvaluation evaluation,
            CaveDropTrap.RoofedThroatMeasurements measurements,
            PassAFirstWitness firstWitness) {

        PassASelectorSlot {
            candidate = Objects.requireNonNull(candidate);
            firstWitness = Objects.requireNonNull(firstWitness);
            if (duplicate) {
                if (plan != null || evaluation != null || measurements != null) {
                    throw new IllegalArgumentException(
                            "duplicate selector slot cannot carry structural results");
                }
            } else {
                plan = Objects.requireNonNull(plan);
                evaluation = Objects.requireNonNull(evaluation);
                measurements = Objects.requireNonNull(measurements);
            }
        }

        static PassASelectorSlot duplicate(
                NaturalCaveAnchorCandidate candidate,
                PassAFirstWitness firstWitness) {
            return new PassASelectorSlot(
                    candidate, true, null, null, null, firstWitness);
        }

        static PassASelectorSlot evaluated(
                NaturalCaveAnchorCandidate candidate,
                CaveDropTrap.RoofedGalleryThroatPlan plan,
                CaveDropTrap.RoofedThroatEvaluation evaluation,
                CaveDropTrap.RoofedThroatMeasurements measurements,
                PassAFirstWitness firstWitness) {
            return new PassASelectorSlot(
                    candidate, false, plan, evaluation, measurements, firstWitness);
        }
    }

    /** Exact selector roll and owner assignment emitted by the real diagnostic scan seam. */
    record PassASelectorAssignment(
            NaturalCaveAnchorCandidate candidate,
            float fractionRoll,
            int acceptedBefore,
            CaveDropTrap.PassAPrediction prediction) {

        PassASelectorAssignment {
            candidate = Objects.requireNonNull(candidate);
            prediction = Objects.requireNonNull(prediction);
        }
    }

    /** Test-visible trace plus the real closed census row returned to the audit driver. */
    record PassASelectorRun(
            PassACensusSnapshot snapshot,
            List<PassASelectorAssignment> assignments,
            int capAcceptedCount) {

        PassASelectorRun {
            snapshot = Objects.requireNonNull(snapshot);
            assignments = List.copyOf(assignments);
        }
    }

    /**
     * Closed Pass-A funnel. Every scan slot is either a duplicate or a unique owner anchor; every
     * unique anchor has one structural terminal, and every structurally feasible anchor continues
     * through both rare-feasibility branches and the real fraction/cap/reward selectors.
     */
    public record PassACensusSnapshot(
            int chunkX,
            int chunkZ,
            long legalFloorCount,
            long auditDomainSlots,
            long caveAnchorQualified,
            long caveAnchorRejected,
            long scanSlots,
            long uniqueOwnerAnchors,
            long duplicateReject,
            long sameDoorReject,
            long invalidOrSelfAuthoredWitnessReject,
            long exposedSkyWaterOrThinRoofReject,
            long seafloorOrFluidStandingReject,
            long firmBypassOrWrongRolesReject,
            long cavityAirOrFluidReject,
            long oreBlockEntityOrProtectedReject,
            long gravityOrPartialStabilizationReject,
            long shellOrSupportReject,
            long ownerReject,
            long disconnectedLowerOrRewardReject,
            long ordinaryFeasible,
            long floodedFeasible,
            long floodedFallback,
            long prospectorFeasible,
            long prospectorFallback,
            long fractionAccepted,
            long fractionRejected,
            long capAccepted,
            long capRejected,
            long resolvedOrdinary,
            long resolvedFlooded,
            long resolvedProspector,
            long resolvedFallback,
            List<Long> rewardRollCounts,
            long requestedOrdinary,
            long requestedFlooded,
            long requestedProspector,
            long floodedRequestFallback,
            long prospectorRequestFallback,
            CaveDropTrap.RoofedThroatMeasurements topologyMeasurements,
            List<PassACensusRepresentative> terminalRepresentatives,
            List<PassAProvenanceCount> provenanceCounts) {

        public PassACensusSnapshot {
            rewardRollCounts = rewardRollCounts == null
                    ? List.of() : List.copyOf(rewardRollCounts);
            terminalRepresentatives = terminalRepresentatives == null
                    ? List.of() : List.copyOf(terminalRepresentatives);
            provenanceCounts = provenanceCounts == null
                    ? List.of() : List.copyOf(provenanceCounts);
        }

        /** Focused-fixture constructor with explicit v6 source evidence. */
        public PassACensusSnapshot(
                int chunkX,
                int chunkZ,
                long legalFloorCount,
                long auditDomainSlots,
                long caveAnchorQualified,
                long caveAnchorRejected,
                long scanSlots,
                long uniqueOwnerAnchors,
                long duplicateReject,
                long sameDoorReject,
                long invalidOrSelfAuthoredWitnessReject,
                long exposedSkyWaterOrThinRoofReject,
                long seafloorOrFluidStandingReject,
                long firmBypassOrWrongRolesReject,
                long cavityAirOrFluidReject,
                long oreBlockEntityOrProtectedReject,
                long gravityOrPartialStabilizationReject,
                long shellOrSupportReject,
                long ownerReject,
                long disconnectedLowerOrRewardReject,
                long ordinaryFeasible,
                long floodedFeasible,
                long floodedFallback,
                long prospectorFeasible,
                long prospectorFallback,
                long fractionAccepted,
                long fractionRejected,
                long capAccepted,
                long capRejected,
                long resolvedOrdinary,
                long resolvedFlooded,
                long resolvedProspector,
                long resolvedFallback,
                List<Long> rewardRollCounts,
                long requestedOrdinary,
                long requestedFlooded,
                long requestedProspector,
                long floodedRequestFallback,
                long prospectorRequestFallback,
                CaveDropTrap.RoofedThroatMeasurements topologyMeasurements,
                List<PassACensusRepresentative> terminalRepresentatives) {
            this(
                    chunkX, chunkZ,
                    legalFloorCount,
                    auditDomainSlots,
                    caveAnchorQualified,
                    caveAnchorRejected,
                    scanSlots, uniqueOwnerAnchors, duplicateReject,
                    sameDoorReject, invalidOrSelfAuthoredWitnessReject,
                    exposedSkyWaterOrThinRoofReject, seafloorOrFluidStandingReject,
                    firmBypassOrWrongRolesReject, cavityAirOrFluidReject,
                    oreBlockEntityOrProtectedReject,
                    gravityOrPartialStabilizationReject, shellOrSupportReject,
                    ownerReject, disconnectedLowerOrRewardReject,
                    ordinaryFeasible,
                    floodedFeasible, floodedFallback,
                    prospectorFeasible, prospectorFallback,
                    fractionAccepted, fractionRejected,
                    capAccepted, capRejected,
                    resolvedOrdinary, resolvedFlooded, resolvedProspector,
                    resolvedFallback,
                    rewardRollCounts,
                    requestedOrdinary, requestedFlooded, requestedProspector,
                    floodedRequestFallback, prospectorRequestFallback,
                    topologyMeasurements, terminalRepresentatives,
                    syntheticProvenance(
                            chunkX, chunkZ,
                            duplicateReject,
                            sameDoorReject,
                            invalidOrSelfAuthoredWitnessReject,
                            exposedSkyWaterOrThinRoofReject,
                            seafloorOrFluidStandingReject,
                            firmBypassOrWrongRolesReject,
                            cavityAirOrFluidReject,
                            oreBlockEntityOrProtectedReject,
                            gravityOrPartialStabilizationReject,
                            shellOrSupportReject,
                            ownerReject,
                            disconnectedLowerOrRewardReject,
                            ordinaryFeasible));
        }

        public boolean candidateSourceReconciles() {
            long expectedAuditDomain = checkedNonNegativeProduct(
                    legalFloorCount, ConformalPassageThroatSource.ANCHOR_COLUMNS);
            long sourcePartition = checkedNonNegativeSum(
                    caveAnchorQualified, caveAnchorRejected);
            return expectedAuditDomain >= 0
                    && sourcePartition >= 0
                    && auditDomainSlots == expectedAuditDomain
                    && auditDomainSlots == sourcePartition
                    && caveAnchorQualified == scanSlots;
        }

        public static PassACensusSnapshot empty(int chunkX, int chunkZ) {
            return new PassACensusSnapshot(
                    chunkX, chunkZ,
                    0, 0, 0, 0,
                    0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0,
                    0, 0, 0, 0,
                    java.util.Collections.nCopies(16, 0L),
                    0, 0, 0, 0, 0,
                    null, List.of());
        }

        private static List<PassAProvenanceCount> syntheticProvenance(
                int chunkX,
                int chunkZ,
                long duplicateReject,
                long sameDoorReject,
                long invalidOrSelfAuthoredWitnessReject,
                long exposedSkyWaterOrThinRoofReject,
                long seafloorOrFluidStandingReject,
                long firmBypassOrWrongRolesReject,
                long cavityAirOrFluidReject,
                long oreBlockEntityOrProtectedReject,
                long gravityOrPartialStabilizationReject,
                long shellOrSupportReject,
                long ownerReject,
                long disconnectedLowerOrRewardReject,
                long ordinaryFeasible) {
            List<PassAProvenanceCount> counts = new ArrayList<>();
            addSyntheticProvenance(
                    counts, chunkX, chunkZ, "DUPLICATE",
                    PassAPredicateRole.DUPLICATE, duplicateReject);
            addSyntheticProvenance(
                    counts, chunkX, chunkZ, CaveDropTrap.PassARejection.SAME_DOOR.name(),
                    PassAPredicateRole.AUTHORED_VOLUME_OR_OTHER, sameDoorReject);
            addSyntheticProvenance(
                    counts, chunkX, chunkZ,
                    CaveDropTrap.PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS.name(),
                    PassAPredicateRole.AUTHORED_VOLUME_OR_OTHER,
                    invalidOrSelfAuthoredWitnessReject);
            addSyntheticProvenance(
                    counts, chunkX, chunkZ,
                    CaveDropTrap.PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF.name(),
                    PassAPredicateRole.ROOF, exposedSkyWaterOrThinRoofReject);
            addSyntheticProvenance(
                    counts, chunkX, chunkZ,
                    CaveDropTrap.PassARejection.SEAFLOOR_OR_FLUID_STANDING.name(),
                    PassAPredicateRole.MOUTH_OR_CONTINUATION_FLOOR,
                    seafloorOrFluidStandingReject);
            addSyntheticProvenance(
                    counts, chunkX, chunkZ,
                    CaveDropTrap.PassARejection.FIRM_BYPASS_OR_WRONG_ROLES.name(),
                    PassAPredicateRole.AUTHORED_VOLUME_OR_OTHER,
                    firmBypassOrWrongRolesReject);
            addSyntheticProvenance(
                    counts, chunkX, chunkZ,
                    CaveDropTrap.PassARejection.CAVITY_AIR_OR_FLUID.name(),
                    PassAPredicateRole.AUTHORED_VOLUME_OR_OTHER,
                    cavityAirOrFluidReject);
            addSyntheticProvenance(
                    counts, chunkX, chunkZ,
                    CaveDropTrap.PassARejection.ORE_BLOCK_ENTITY_OR_PROTECTED.name(),
                    PassAPredicateRole.AUTHORED_VOLUME_OR_OTHER,
                    oreBlockEntityOrProtectedReject);
            addSyntheticProvenance(
                    counts, chunkX, chunkZ,
                    CaveDropTrap.PassARejection.GRAVITY_OR_PARTIAL_STABILIZATION.name(),
                    PassAPredicateRole.AUTHORED_VOLUME_OR_OTHER,
                    gravityOrPartialStabilizationReject);
            addSyntheticProvenance(
                    counts, chunkX, chunkZ,
                    CaveDropTrap.PassARejection.SHELL_OR_SUPPORT.name(),
                    PassAPredicateRole.AUTHORED_VOLUME_OR_OTHER,
                    shellOrSupportReject);
            addSyntheticProvenance(
                    counts, chunkX, chunkZ, CaveDropTrap.PassARejection.OWNER.name(),
                    PassAPredicateRole.AUTHORED_VOLUME_OR_OTHER, ownerReject);
            addSyntheticProvenance(
                    counts, chunkX, chunkZ,
                    CaveDropTrap.PassARejection.DISCONNECTED_LOWER_OR_REWARD.name(),
                    PassAPredicateRole.AUTHORED_VOLUME_OR_OTHER,
                    disconnectedLowerOrRewardReject);
            addSyntheticProvenance(
                    counts, chunkX, chunkZ, CaveDropTrap.PassARejection.PASS.name(),
                    PassAPredicateRole.PASSED_STRUCTURAL, ordinaryFeasible);
            return List.copyOf(counts);
        }

        private static void addSyntheticProvenance(
                List<PassAProvenanceCount> counts,
                int chunkX,
                int chunkZ,
                String terminalBucket,
                PassAPredicateRole role,
                long count) {
            if (count == 0) {
                return;
            }
            PassAFirstWitness witness = new PassAFirstWitness(
                    terminalBucket,
                    PassAProvenanceSource.NON_PREDICATE,
                    role,
                    new BlockPos(chunkX << 4, 0, chunkZ << 4),
                    "none",
                    "NONE",
                    false,
                    0,
                    0,
                    0);
            counts.add(new PassAProvenanceCount(witness.provenanceKey(), count, witness));
        }

        public PassACensusSnapshot plus(PassACensusSnapshot other) {
            Map<String, PassAProvenanceCount> provenance = new LinkedHashMap<>();
            for (PassAProvenanceCount count : provenanceCounts) {
                provenance.put(count.key(), count);
            }
            for (PassAProvenanceCount count : other.provenanceCounts) {
                provenance.merge(
                        count.key(),
                        count,
                        (first, second) -> new PassAProvenanceCount(
                                first.key(),
                                checkedAggregateCount(first.count(), second.count()),
                                second.representative().isBetterThan(first.representative())
                                        ? second.representative()
                                        : first.representative()));
            }
            Map<String, PassACensusRepresentative> representatives = new LinkedHashMap<>();
            for (PassACensusRepresentative representative : terminalRepresentatives) {
                representatives.put(representative.stage(), representative);
            }
            for (PassACensusRepresentative representative : other.terminalRepresentatives) {
                representatives.merge(
                        representative.stage(), representative,
                        (first, second) -> second.isBetterThan(first) ? second : first);
            }
            List<Long> rolls = new ArrayList<>();
            for (int roll = 0; roll < 16; roll++) {
                rolls.add(checkedAggregateRollCount(
                        rewardRollCounts, other.rewardRollCounts, roll));
            }
            CaveDropTrap.RoofedThroatMeasurements measurements = topologyMeasurements;
            if (measurements == null) {
                measurements = other.topologyMeasurements;
            } else if (other.topologyMeasurements != null
                    && !measurements.equals(other.topologyMeasurements)) {
                measurements = null;
            }
            return new PassACensusSnapshot(
                    chunkX, chunkZ,
                    checkedAggregateCount(legalFloorCount, other.legalFloorCount),
                    checkedAggregateCount(auditDomainSlots, other.auditDomainSlots),
                    checkedAggregateCount(caveAnchorQualified, other.caveAnchorQualified),
                    checkedAggregateCount(caveAnchorRejected, other.caveAnchorRejected),
                    checkedAggregateCount(scanSlots, other.scanSlots),
                    checkedAggregateCount(uniqueOwnerAnchors, other.uniqueOwnerAnchors),
                    checkedAggregateCount(duplicateReject, other.duplicateReject),
                    checkedAggregateCount(sameDoorReject, other.sameDoorReject),
                    checkedAggregateCount(
                            invalidOrSelfAuthoredWitnessReject,
                            other.invalidOrSelfAuthoredWitnessReject),
                    checkedAggregateCount(
                            exposedSkyWaterOrThinRoofReject,
                            other.exposedSkyWaterOrThinRoofReject),
                    checkedAggregateCount(
                            seafloorOrFluidStandingReject,
                            other.seafloorOrFluidStandingReject),
                    checkedAggregateCount(
                            firmBypassOrWrongRolesReject,
                            other.firmBypassOrWrongRolesReject),
                    checkedAggregateCount(
                            cavityAirOrFluidReject,
                            other.cavityAirOrFluidReject),
                    checkedAggregateCount(
                            oreBlockEntityOrProtectedReject,
                            other.oreBlockEntityOrProtectedReject),
                    checkedAggregateCount(
                            gravityOrPartialStabilizationReject,
                            other.gravityOrPartialStabilizationReject),
                    checkedAggregateCount(shellOrSupportReject, other.shellOrSupportReject),
                    checkedAggregateCount(ownerReject, other.ownerReject),
                    checkedAggregateCount(
                            disconnectedLowerOrRewardReject,
                            other.disconnectedLowerOrRewardReject),
                    checkedAggregateCount(ordinaryFeasible, other.ordinaryFeasible),
                    checkedAggregateCount(floodedFeasible, other.floodedFeasible),
                    checkedAggregateCount(floodedFallback, other.floodedFallback),
                    checkedAggregateCount(prospectorFeasible, other.prospectorFeasible),
                    checkedAggregateCount(prospectorFallback, other.prospectorFallback),
                    checkedAggregateCount(fractionAccepted, other.fractionAccepted),
                    checkedAggregateCount(fractionRejected, other.fractionRejected),
                    checkedAggregateCount(capAccepted, other.capAccepted),
                    checkedAggregateCount(capRejected, other.capRejected),
                    checkedAggregateCount(resolvedOrdinary, other.resolvedOrdinary),
                    checkedAggregateCount(resolvedFlooded, other.resolvedFlooded),
                    checkedAggregateCount(resolvedProspector, other.resolvedProspector),
                    checkedAggregateCount(resolvedFallback, other.resolvedFallback),
                    rolls,
                    checkedAggregateCount(requestedOrdinary, other.requestedOrdinary),
                    checkedAggregateCount(requestedFlooded, other.requestedFlooded),
                    checkedAggregateCount(requestedProspector, other.requestedProspector),
                    checkedAggregateCount(
                            floodedRequestFallback,
                            other.floodedRequestFallback),
                    checkedAggregateCount(
                            prospectorRequestFallback,
                            other.prospectorRequestFallback),
                    measurements,
                    List.copyOf(representatives.values()),
                    provenance.values().stream()
                            .sorted(Comparator.comparing(PassAProvenanceCount::key))
                            .toList());
        }

        public boolean reconciles() {
            if (!allNonNegative(
                    legalFloorCount,
                    auditDomainSlots,
                    caveAnchorQualified,
                    caveAnchorRejected,
                    scanSlots,
                    uniqueOwnerAnchors,
                    duplicateReject,
                    sameDoorReject,
                    invalidOrSelfAuthoredWitnessReject,
                    exposedSkyWaterOrThinRoofReject,
                    seafloorOrFluidStandingReject,
                    firmBypassOrWrongRolesReject,
                    cavityAirOrFluidReject,
                    oreBlockEntityOrProtectedReject,
                    gravityOrPartialStabilizationReject,
                    shellOrSupportReject,
                    ownerReject,
                    disconnectedLowerOrRewardReject,
                    ordinaryFeasible,
                    floodedFeasible,
                    floodedFallback,
                    prospectorFeasible,
                    prospectorFallback,
                    fractionAccepted,
                    fractionRejected,
                    capAccepted,
                    capRejected,
                    resolvedOrdinary,
                    resolvedFlooded,
                    resolvedProspector,
                    resolvedFallback,
                    requestedOrdinary,
                    requestedFlooded,
                    requestedProspector,
                    floodedRequestFallback,
                    prospectorRequestFallback)
                    || rewardRollCounts.size() != 16) {
                return false;
            }
            long requestedOrdinaryByRoll = 0;
            for (int roll = 0; roll < 14; roll++) {
                Long count = rewardRollCounts.get(roll);
                if (count == null || count < 0
                        || requestedOrdinaryByRoll > Long.MAX_VALUE - count) {
                    return false;
                }
                requestedOrdinaryByRoll += count;
            }
            Long requestedFloodedByRoll = rewardRollCounts.get(14);
            Long requestedProspectorByRoll = rewardRollCounts.get(15);
            if (requestedFloodedByRoll == null || requestedFloodedByRoll < 0
                    || requestedProspectorByRoll == null || requestedProspectorByRoll < 0) {
                return false;
            }
            long structuralRejects = checkedNonNegativeSum(
                    sameDoorReject,
                    invalidOrSelfAuthoredWitnessReject,
                    exposedSkyWaterOrThinRoofReject,
                    seafloorOrFluidStandingReject,
                    firmBypassOrWrongRolesReject,
                    cavityAirOrFluidReject,
                    oreBlockEntityOrProtectedReject,
                    gravityOrPartialStabilizationReject,
                    shellOrSupportReject,
                    ownerReject,
                    disconnectedLowerOrRewardReject);
            long rewardRollTotal = checkedNonNegativeSum(
                    requestedOrdinaryByRoll,
                    requestedFloodedByRoll,
                    requestedProspectorByRoll);
            long requestedTotal = checkedNonNegativeSum(
                    requestedOrdinary, requestedFlooded, requestedProspector);
            long resolvedTotal = checkedNonNegativeSum(
                    resolvedOrdinary, resolvedFlooded, resolvedProspector);
            long rareFallbackTotal = checkedNonNegativeSum(
                    floodedRequestFallback, prospectorRequestFallback);
            long expectedResolvedOrdinary = checkedNonNegativeSum(
                    requestedOrdinary,
                    floodedRequestFallback,
                    prospectorRequestFallback);
            long expectedRequestedFlooded = checkedNonNegativeSum(
                    resolvedFlooded, floodedRequestFallback);
            long expectedRequestedProspector = checkedNonNegativeSum(
                    resolvedProspector, prospectorRequestFallback);
            long expectedAuditDomain = checkedNonNegativeProduct(
                    legalFloorCount, ConformalPassageThroatSource.ANCHOR_COLUMNS);
            long sourcePartition = checkedNonNegativeSum(
                    caveAnchorQualified, caveAnchorRejected);
            long scanPartition = checkedNonNegativeSum(
                    uniqueOwnerAnchors, duplicateReject);
            long structuralPartition = checkedNonNegativeSum(
                    structuralRejects, ordinaryFeasible);
            long floodedFeasibilityPartition = checkedNonNegativeSum(
                    floodedFeasible, floodedFallback);
            long prospectorFeasibilityPartition = checkedNonNegativeSum(
                    prospectorFeasible, prospectorFallback);
            long fractionPartition = checkedNonNegativeSum(
                    fractionAccepted, fractionRejected);
            long capPartition = checkedNonNegativeSum(capAccepted, capRejected);
            if (structuralRejects < 0
                    || rewardRollTotal < 0
                    || requestedTotal < 0
                    || resolvedTotal < 0
                    || rareFallbackTotal < 0
                    || expectedResolvedOrdinary < 0
                    || expectedRequestedFlooded < 0
                    || expectedRequestedProspector < 0
                    || expectedAuditDomain < 0
                    || sourcePartition < 0
                    || scanPartition < 0
                    || structuralPartition < 0
                    || floodedFeasibilityPartition < 0
                    || prospectorFeasibilityPartition < 0
                    || fractionPartition < 0
                    || capPartition < 0) {
                return false;
            }
            return candidateSourceReconciles()
                    && scanSlots == scanPartition
                    && uniqueOwnerAnchors == structuralPartition
                    && ordinaryFeasible == floodedFeasibilityPartition
                    && ordinaryFeasible == prospectorFeasibilityPartition
                    && ordinaryFeasible == fractionPartition
                    && fractionAccepted == capPartition
                    && capAccepted == resolvedTotal
                    && rewardRollTotal == capAccepted
                    && requestedTotal == capAccepted
                    && requestedOrdinary == requestedOrdinaryByRoll
                    && requestedFlooded == requestedFloodedByRoll
                    && requestedProspector == requestedProspectorByRoll
                    && requestedFlooded == expectedRequestedFlooded
                    && requestedProspector == expectedRequestedProspector
                    && resolvedOrdinary == expectedResolvedOrdinary
                    && resolvedFlooded <= requestedFlooded
                    && resolvedProspector <= requestedProspector
                    && resolvedFlooded <= floodedFeasible
                    && resolvedProspector <= prospectorFeasible
                    && floodedRequestFallback <= floodedFallback
                    && prospectorRequestFallback <= prospectorFallback
                    && resolvedFallback == rareFallbackTotal
                    && resolvedFallback <= capAccepted
                    && provenanceReconciles()
                    && (scanSlots == 0
                            ? topologyMeasurements == null
                            : topologyMeasurements != null
                                    && topologyMeasurements.lockedTopologyMatches());
        }

        public boolean provenanceReconciles() {
            Map<String, Long> terminals = new HashMap<>();
            Set<String> keys = new HashSet<>();
            long total = 0;
            for (PassAProvenanceCount provenance : provenanceCounts) {
                if (provenance == null
                        || provenance.count() < 0
                        || !keys.add(provenance.key())
                        || !provenance.key().equals(
                                provenance.representative().provenanceKey())
                        || total > Long.MAX_VALUE - provenance.count()) {
                    return false;
                }
                total += provenance.count();
                String terminal = provenance.representative().terminalBucket();
                long prior = terminals.getOrDefault(terminal, 0L);
                if (prior > Long.MAX_VALUE - provenance.count()) {
                    return false;
                }
                terminals.put(terminal, prior + provenance.count());
            }
            return total == scanSlots
                    && terminalCount(terminals, "DUPLICATE") == duplicateReject
                    && terminalCount(terminals, CaveDropTrap.PassARejection.SAME_DOOR.name())
                            == sameDoorReject
                    && terminalCount(
                                    terminals,
                                    CaveDropTrap.PassARejection
                                            .INVALID_OR_SELF_AUTHORED_WITNESS.name())
                            == invalidOrSelfAuthoredWitnessReject
                    && terminalCount(
                                    terminals,
                                    CaveDropTrap.PassARejection
                                            .EXPOSED_SKY_WATER_OR_THIN_ROOF.name())
                            == exposedSkyWaterOrThinRoofReject
                    && terminalCount(
                                    terminals,
                                    CaveDropTrap.PassARejection
                                            .SEAFLOOR_OR_FLUID_STANDING.name())
                            == seafloorOrFluidStandingReject
                    && terminalCount(
                                    terminals,
                                    CaveDropTrap.PassARejection
                                            .FIRM_BYPASS_OR_WRONG_ROLES.name())
                            == firmBypassOrWrongRolesReject
                    && terminalCount(
                                    terminals,
                                    CaveDropTrap.PassARejection.CAVITY_AIR_OR_FLUID.name())
                            == cavityAirOrFluidReject
                    && terminalCount(
                                    terminals,
                                    CaveDropTrap.PassARejection
                                            .ORE_BLOCK_ENTITY_OR_PROTECTED.name())
                            == oreBlockEntityOrProtectedReject
                    && terminalCount(
                                    terminals,
                                    CaveDropTrap.PassARejection
                                            .GRAVITY_OR_PARTIAL_STABILIZATION.name())
                            == gravityOrPartialStabilizationReject
                    && terminalCount(
                                    terminals,
                                    CaveDropTrap.PassARejection.SHELL_OR_SUPPORT.name())
                            == shellOrSupportReject
                    && terminalCount(terminals, CaveDropTrap.PassARejection.OWNER.name())
                            == ownerReject
                    && terminalCount(
                                    terminals,
                                    CaveDropTrap.PassARejection
                                            .DISCONNECTED_LOWER_OR_REWARD.name())
                            == disconnectedLowerOrRewardReject
                    && terminalCount(terminals, CaveDropTrap.PassARejection.PASS.name())
                            == ordinaryFeasible
                    && terminals.size() == expectedNonZeroTerminalCount();
        }

        private int expectedNonZeroTerminalCount() {
            int count = duplicateReject == 0 ? 0 : 1;
            for (long terminal : new long[] {
                    sameDoorReject,
                    invalidOrSelfAuthoredWitnessReject,
                    exposedSkyWaterOrThinRoofReject,
                    seafloorOrFluidStandingReject,
                    firmBypassOrWrongRolesReject,
                    cavityAirOrFluidReject,
                    oreBlockEntityOrProtectedReject,
                    gravityOrPartialStabilizationReject,
                    shellOrSupportReject,
                    ownerReject,
                    disconnectedLowerOrRewardReject,
                    ordinaryFeasible
            }) {
                if (terminal != 0) {
                    count++;
                }
            }
            return count;
        }

        private static long terminalCount(Map<String, Long> terminals, String terminal) {
            return terminals.getOrDefault(terminal, 0L);
        }

        private static boolean allNonNegative(long... counts) {
            for (long count : counts) {
                if (count < 0) {
                    return false;
                }
            }
            return true;
        }

        private static long checkedNonNegativeSum(long... counts) {
            long total = 0;
            for (long count : counts) {
                if (count < 0 || total > Long.MAX_VALUE - count) {
                    return -1;
                }
                total += count;
            }
            return total;
        }

        private static long checkedNonNegativeProduct(long first, long second) {
            return first < 0 || second < 0
                            || (first != 0 && second > Long.MAX_VALUE / first)
                    ? -1L : first * second;
        }

        /**
         * Aggregation stays serializable on corrupt or overflowing input. The negative sentinel is
         * deliberately sticky and is rejected by {@link #reconciles()}.
         */
        private static long checkedAggregateCount(long first, long second) {
            return checkedNonNegativeSum(first, second);
        }

        private static long checkedAggregateRollCount(
                List<Long> first,
                List<Long> second,
                int roll) {
            if (first.size() != 16 || second.size() != 16
                    || first.get(roll) == null || second.get(roll) == null) {
                return -1;
            }
            return checkedAggregateCount(first.get(roll), second.get(roll));
        }
    }

    /** Installs or clears the development-only observer. Production leaves this {@code null}. */
    public static void setAuditCallback(Consumer<AuditEvent> callback) {
        auditCallback = callback;
    }

    /** Installs or clears the default-off no-write candidate census observer. */
    public static void setCensusCallback(Consumer<CensusSnapshot> callback) {
        censusCallback = callback;
    }

    /** Installs or clears the default-off, no-write Pass-A throat census observer. */
    public static void setPassACensusCallback(Consumer<PassACensusSnapshot> callback) {
        passACensusCallback = callback;
    }

    /** Package-private deterministic failure seam for the focused transaction adapter test. */
    static void setWriteFailureInjectorForTests(IntPredicate injector) {
        writeFailureInjector = injector;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED || !LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
            return false;
        }

        WorldGenLevel level = ctx.level();
        int baseX = (ctx.origin().getX() >> 4) << 4;
        int baseZ = (ctx.origin().getZ() >> 4) << 4;
        Consumer<PassACensusSnapshot> passAObserver = passACensusCallback;
        if (passAObserver != null) {
            passAObserver.accept(scanPassAChunk(
                    level, baseX >> 4, baseZ >> 4, ctx.random()));
            return false;
        }
        Consumer<CensusSnapshot> censusObserver = censusCallback;
        if (censusObserver == null) {
            // Shipping placement is the conformal passage throat. The embedded-ribbon scan
            // below is retained only for the older read-only census harness, which still
            // measures that grammar.
            return placeConformalThroats(level, baseX, baseZ, ctx.random());
        }
        CensusTally census = new CensusTally(baseX >> 4, baseZ >> 4);
        Map<String, Integer> rejected = new HashMap<>();
        int candidateCount = 0;
        boolean placedAny = false;
        int placedCount = 0;
        int scanBottom = Math.max(
                level.getMinY() + CaveDropTrap.MIN_CERTIFIED_SOLID_DEPTH + 1,
                SCAN_BOTTOM_Y);

        search:
        for (CaveDropTrap.RibbonOrientation orientation
                : CaveDropTrap.RibbonOrientation.values()) {
            CaveDropTrap.EmbeddedRibbonPlan shape = CaveDropTrap.planEmbeddedRibbon(
                    0, orientation, ignored -> true, ignored -> true);
            if (shape == null) {
                reject(rejected, "template_definition");
                continue;
            }
            int minX = shape.requiredSolidVoxels().stream()
                    .mapToInt(CaveDropTrap.Voxel::x).min().orElseThrow();
            int maxX = shape.requiredSolidVoxels().stream()
                    .mapToInt(CaveDropTrap.Voxel::x).max().orElseThrow();
            int minZ = shape.requiredSolidVoxels().stream()
                    .mapToInt(CaveDropTrap.Voxel::z).min().orElseThrow();
            int maxZ = shape.requiredSolidVoxels().stream()
                    .mapToInt(CaveDropTrap.Voxel::z).max().orElseThrow();
            for (int originLx = 1 - minX; originLx <= 14 - maxX; originLx++) {
                for (int originLz = 1 - minZ; originLz <= 14 - maxZ; originLz++) {
                    for (int floorY = SCAN_TOP_Y; floorY >= scanBottom; floorY--) {
                        if (census != null) {
                            census.scanSlots++;
                        }
                        if (placedCount >= CaveDropTrap.MAX_PATCHES_PER_CHUNK) {
                            break search;
                        }
                        BlockPos reference = new BlockPos(
                                baseX + originLx, floorY, baseZ + originLz);
                        if (!safeNaturalSupport(
                                    level, reference, level.getBlockState(reference))
                                || !twoAir(
                                    level, reference.getX(), floorY + 1, reference.getZ())) {
                            if (census != null) {
                                census.referenceFloorHeadroomFail++;
                                census.consider(new CensusRepresentative(
                                        1, baseX >> 4, baseZ >> 4, orientation,
                                        originLx, originLz, floorY,
                                        0, reference, level.getBlockState(reference).toString(),
                                        0, null, null, 0, 0,
                                        CensusDefectCounts.empty(), 0, false));
                            }
                            continue;
                        }
                        if (census != null) {
                            census.referenceFloorHeadroomPass++;
                        }

                        int selectedOriginLx = originLx;
                        int selectedOriginLz = originLz;
                        CaveDropTrap.EmbeddedRibbonPlan plan =
                                CaveDropTrap.planEmbeddedRibbon(
                                        floorY,
                                        orientation,
                                        voxel -> {
                                            BlockPos position = ribbonWorldPosition(
                                                    voxel, baseX, baseZ,
                                                    selectedOriginLx, selectedOriginLz);
                                            return insideOwnerInset(position)
                                                    && safeNaturalSupport(
                                                        level, position,
                                                        level.getBlockState(position));
                                        },
                                        voxel -> {
                                            BlockPos position = ribbonWorldPosition(
                                                    voxel, baseX, baseZ,
                                                    selectedOriginLx, selectedOriginLz);
                                                    return insideOwnerInset(position)
                                                    && airWithoutEntity(level, position);
                                        });
                        boolean stabilizationOnly = false;
                        if (census != null) {
                            census.currentSolidDefects = CensusDefectCounts.empty();
                        }
                        if (plan == null && census != null) {
                            plan = census.evaluateHypotheticalEnvelope(
                                    shape, orientation, selectedOriginLx, selectedOriginLz,
                                    floorY, level, baseX, baseZ);
                            stabilizationOnly = plan != null;
                        }
                        if (plan == null) {
                            reject(rejected, "template_envelope");
                            continue;
                        }
                        if (census != null) {
                            if (!stabilizationOnly) {
                                census.envelopePass++;
                            }
                        }
                        EnclosureDiagnosis enclosure = census == null ? null
                                : enclosureDiagnosis(
                                        plan, level, baseX, baseZ,
                                        selectedOriginLx, selectedOriginLz);
                        if (!CaveDropTrap.caveEnclosureQualifies(
                                floorY,
                                plan.enclosureColumns(),
                                cell -> level.getHeight(
                                        FEATURE_STAGE_SURFACE_HEIGHTMAP,
                                        baseX + selectedOriginLx + cell.x(),
                                        baseZ + selectedOriginLz + cell.z()))) {
                            if (census != null) {
                                census.enclosureFail++;
                                if (stabilizationOnly) {
                                    census.stabilizationOnlyEnclosureFail++;
                                }
                                census.consider(new CensusRepresentative(
                                        3, baseX >> 4, baseZ >> 4, orientation,
                                        selectedOriginLx, selectedOriginLz, floorY,
                                        0, null, null, 0, null, null,
                                        enclosure.minimumRise(), enclosure.failingColumns(),
                                        census.currentSolidDefects, 0, stabilizationOnly));
                            }
                            reject(rejected, "cave_enclosure");
                            continue;
                        }
                        if (census != null) {
                            census.enclosurePass++;
                            if (stabilizationOnly) {
                                census.stabilizationOnlyEnclosurePass++;
                            }
                        }
                        CensusGravityProjection projection = census == null ? null
                                : censusGravityProjection(
                                        level, plan, baseX, baseZ,
                                        selectedOriginLx, selectedOriginLz);
                        if (census != null && projection.exposedGravityHazards() > 0) {
                            census.gravityHazardFail++;
                            if (stabilizationOnly) {
                                census.stabilizationOnlyGravityHazardFail++;
                            }
                            census.consider(new CensusRepresentative(
                                    4, baseX >> 4, baseZ >> 4, orientation,
                                    selectedOriginLx, selectedOriginLz, floorY,
                                    0, null, null, 0, null, null,
                                    enclosure.minimumRise(), enclosure.failingColumns(),
                                    census.currentSolidDefects,
                                    projection.exposedGravityHazards(), stabilizationOnly));
                            continue;
                        }
                        if (census != null) {
                            census.gravityHazardPass++;
                            if (stabilizationOnly) {
                                census.stabilizationOnlyGravityHazardPass++;
                            }
                        }
                        if (censusOnly(censusObserver) && stabilizationOnly) {
                            census.stabilizationOnlyMaterializationNotModeled++;
                            census.consider(new CensusRepresentative(
                                    5, baseX >> 4, baseZ >> 4, orientation,
                                    selectedOriginLx, selectedOriginLz, floorY,
                                    0, null, null, 0, null, null,
                                    enclosure.minimumRise(), enclosure.failingColumns(),
                                    census.currentSolidDefects, 0, true));
                            continue;
                        }
                        candidateCount++;
                        TrapGeometry geometry = null;
                        geometry = materializeRibbon(
                                level, plan, baseX, baseZ,
                                originLx, originLz, rejected);
                        boolean materializable = geometry != null;
                        if (!materializable) {
                            if (census != null) {
                                census.materializationFail++;
                                census.consider(new CensusRepresentative(
                                        6, baseX >> 4, baseZ >> 4, orientation,
                                        selectedOriginLx, selectedOriginLz, floorY,
                                        0, null, null, 0, null, null,
                                        enclosure.minimumRise(), enclosure.failingColumns(),
                                        census.currentSolidDefects, 0, false));
                            }
                            reject(rejected, "template_materialization");
                            continue;
                        }
                        if (censusOnly(censusObserver)) {
                            census.materializationPass++;
                            census.consider(new CensusRepresentative(
                                    7, baseX >> 4, baseZ >> 4, orientation,
                                    selectedOriginLx, selectedOriginLz, floorY,
                                    0, null, null, 0, null, null,
                                    enclosure.minimumRise(), enclosure.failingColumns(),
                                    census.currentSolidDefects, 0, false));
                            continue;
                        }
                        if (!CaveDropTrap.shouldTrapPatch(ctx.random().nextFloat())) {
                            reject(rejected, "fraction_gate");
                            continue;
                        }

                        int rewardRoll = CaveDropTrap.rewardRoll(
                                level.getSeed(),
                                geometry.anchor().getX(),
                                geometry.anchor().getY(),
                                geometry.anchor().getZ());
                        ScenePlan ordinary = ordinaryScene(geometry);
                        String fallbackReason = null;
                        ScenePlan requested = switch (rewardRoll) {
                            case 14 -> floodedScene(
                                    level, geometry, baseX, baseZ, rejected);
                            case 15 -> prospectorScene(
                                    level, geometry, ordinary, baseX, baseZ, rejected);
                            default -> ordinary;
                        };
                        if (requested == null) {
                            fallbackReason = rewardRoll == 14
                                    ? "flooded_preflight"
                                    : rewardRoll == 15 ? "prospector_preflight" : null;
                            requested = ordinary;
                        }

                        ApplyResult applied =
                                applyScene(level, requested, baseX, baseZ);
                        if (!applied.success()
                                && applied.rollbackVerified()
                                && requested.kind()
                                    != CaveDropTrap.RewardKind.ORDINARY) {
                            reject(rejected, "rare_apply_fallback");
                            applied = applyScene(
                                    level, ordinary, baseX, baseZ);
                            requested = ordinary;
                            fallbackReason = "rare_apply_rollback";
                        }
                        if (!applied.success()) {
                            if (!applied.rollbackVerified()) {
                                reject(rejected, "rollback_verification");
                            }
                            reject(rejected, "ordinary_apply");
                            continue;
                        }

                        placedAny = true;
                        placedCount++;
                        Consumer<AuditEvent> callback = auditCallback;
                        if (callback != null) {
                            emitAudit(
                                    level, callback, geometry, requested,
                                    rewardRoll, fallbackReason);
                        }
                        if (DEBUG) {
                            GlobeMod.LOGGER.info(
                                    "[LAT][CAVEDROP] chunk=({},{}) anchor=({},{},{}) "
                                            + "axis={} powder={} regular={} kind={} fallDepth={}",
                                    baseX >> 4, baseZ >> 4,
                                    requested.anchor().getX(),
                                    requested.anchor().getY(),
                                    requested.anchor().getZ(),
                                    requested.axis(), requested.powderCount(),
                                    requested.regularCount(),
                                    requested.kind(), requested.fallDepth());
                        }
                    }
                }
            }
        }

        if (censusOnly(censusObserver)) {
            censusObserver.accept(census.snapshot());
            return false;
        }

        if (DEBUG && (candidateCount > 0 || placedAny)) {
            GlobeMod.LOGGER.info(
                    "[LAT][CAVEDROP] chunk=({},{}) candidates={} accepted={} rejected={}",
                    baseX >> 4, baseZ >> 4, candidateCount, placedCount, rejected);
        }
        return placedAny;
    }

    /**
     * Shipping placement for the conformal passage throat. It walks the exact funnel the no-write
     * census predicts -- same canonical candidate order, same shared structural predicate, same
     * real reward planners, one fraction roll per structurally feasible candidate, same per-chunk
     * cap -- and then applies the chosen scene through the unchanged atomic writer. Any candidate
     * the census counts as a predicted trap is therefore the candidate this method places.
     */
    private static boolean placeConformalThroats(
            WorldGenLevel level, int baseX, int baseZ, RandomSource random) {
        ConformalPassageScan source = ConformalPassageThroatSource.scan(
                level.getMinY(),
                level.getMaxY(),
                baseX,
                baseZ,
                position -> passABlockKind(level, position, level.getBlockState(position)));
        Set<NaturalCaveAnchorCandidate> duplicates = new HashSet<>(
                ConformalPassageThroatSource.deduplicate(source.candidates())
                        .duplicateCandidates());
        boolean placedAny = false;
        int placedCount = 0;
        for (ConformalThroatCandidate conformal : source.candidates()) {
            NaturalCaveAnchorCandidate candidate = conformal.identity();
            if (duplicates.contains(candidate)) {
                continue;
            }
            int originLx = candidate.originLx();
            int originLz = candidate.originLz();
            CaveDropTrap.RoofedGalleryThroatPlan plan = conformal.plan();
            CaveDropTrap.RoofedThroatEvaluation evaluation =
                    CaveDropTrap.evaluateRoofedGalleryThroat(
                            plan,
                            passAWorldView(level, plan, baseX, baseZ, originLx, originLz));
            if (!evaluation.ordinaryFeasible()) {
                continue;
            }
            TrapGeometry geometry = materializePassAThroat(
                    level, plan, baseX, baseZ, originLx, originLz);
            if (geometry == null) {
                continue;
            }

            // RNG is consumed exactly where the census consumes it: once per structurally
            // feasible candidate, before the per-chunk cap is consulted.
            boolean fractionAccepted = CaveDropTrap.shouldTrapPatch(random.nextFloat());
            if (!fractionAccepted
                    || placedCount >= CaveDropTrap.MAX_PATCHES_PER_CHUNK) {
                continue;
            }

            int rewardRoll = CaveDropTrap.rewardRoll(
                    level.getSeed(),
                    geometry.anchor().getX(),
                    geometry.anchor().getY(),
                    geometry.anchor().getZ());
            ScenePlan ordinary = ordinaryScene(geometry);
            if (ordinary == null) {
                continue;
            }
            String fallbackReason = null;
            ScenePlan requested = switch (rewardRoll) {
                case 14 -> floodedScene(level, geometry, baseX, baseZ, new HashMap<>());
                case 15 -> prospectorScene(
                        level, geometry, ordinary, baseX, baseZ, new HashMap<>());
                default -> ordinary;
            };
            if (requested == null) {
                fallbackReason = rewardRoll == 14
                        ? "flooded_preflight"
                        : rewardRoll == 15 ? "prospector_preflight" : null;
                requested = ordinary;
            }

            ApplyResult applied = applyScene(level, requested, baseX, baseZ);
            if (!applied.success()
                    && applied.rollbackVerified()
                    && requested.kind() != CaveDropTrap.RewardKind.ORDINARY) {
                applied = applyScene(level, ordinary, baseX, baseZ);
                requested = ordinary;
                fallbackReason = "rare_apply_rollback";
            }
            if (!applied.success()) {
                continue;
            }

            placedAny = true;
            placedCount++;
            Consumer<AuditEvent> callback = auditCallback;
            if (callback != null) {
                emitAudit(level, callback, geometry, requested, rewardRoll, fallbackReason);
            }
            if (DEBUG) {
                GlobeMod.LOGGER.info(
                        "[LAT][CAVEDROP] conformal chunk=({},{}) anchor=({},{},{}) kind={}",
                        baseX >> 4, baseZ >> 4,
                        requested.anchor().getX(), requested.anchor().getY(),
                        requested.anchor().getZ(), requested.kind());
            }
        }
        return placedAny;
    }

    /**
     * Default-off, read-only scan of one exact owner chunk. Production callback mode and the audit
     * driver's coverage scan both call this method; only their supplied random source differs.
     */
    public static PassACensusSnapshot scanPassAChunk(
            WorldGenLevel level,
            int chunkX,
            int chunkZ,
            RandomSource random) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(random, "random");
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        ConformalPassageScan source = ConformalPassageThroatSource.scan(
                level.getMinY(),
                level.getMaxY(),
                baseX,
                baseZ,
                position -> passABlockKind(level, position, level.getBlockState(position)));
        NaturalCaveAnchorDedup deduplicated =
                ConformalPassageThroatSource.deduplicate(source.candidates());
        Set<NaturalCaveAnchorCandidate> duplicates =
                new HashSet<>(deduplicated.duplicateCandidates());
        List<PassASelectorSlot> selectorSlots =
                new ArrayList<>(source.candidates().size());
        for (ConformalThroatCandidate conformal : source.candidates()) {
            NaturalCaveAnchorCandidate candidate = conformal.identity();
            int originLx = candidate.originLx();
            int originLz = candidate.originLz();
            int floorY = candidate.floorY();
            BlockPos ownerAnchor = candidate.ownerAnchor();
            if (duplicates.contains(candidate)) {
                selectorSlots.add(PassASelectorSlot.duplicate(
                        candidate,
                        syntheticPassAWitness(
                                level,
                                "DUPLICATE",
                                PassAPredicateRole.DUPLICATE,
                                ownerAnchor,
                                floorY)));
                continue;
            }
            CaveDropTrap.RoofedGalleryThroatPlan plan = conformal.plan();
            CaveDropTrap.RoofedThroatWorldView world = passAWorldView(
                    level, plan, baseX, baseZ, originLx, originLz);
            PassATracedEvaluation traced = evaluatePassAWithTrace(
                    plan,
                    world,
                    voxel -> passAWorldSample(
                            level, voxel, baseX, baseZ, originLx, originLz),
                    ownerAnchor);
            CaveDropTrap.RoofedThroatEvaluation evaluation = traced.evaluation();
            PassARewardPreflight rewards = null;
            if (evaluation.ordinaryFeasible()) {
                rewards = passARewardPreflight(
                        level, plan, baseX, baseZ, originLx, originLz);
                if (rewards == null) {
                    evaluation = new CaveDropTrap.RoofedThroatEvaluation(
                            CaveDropTrap.PassARejection.DISCONNECTED_LOWER_OR_REWARD,
                            false, false,
                            CaveDropTrap.RewardFeasibilityFailure.REAL_PLANNER_REJECTED,
                            false,
                            CaveDropTrap.RewardFeasibilityFailure.REAL_PLANNER_REJECTED);
                } else {
                    evaluation = new CaveDropTrap.RoofedThroatEvaluation(
                            CaveDropTrap.PassARejection.PASS,
                            true,
                            rewards.flooded() != null,
                            rewards.flooded() == null
                                    ? CaveDropTrap.RewardFeasibilityFailure.REAL_PLANNER_REJECTED
                                    : CaveDropTrap.RewardFeasibilityFailure.NONE,
                            rewards.prospector() != null,
                            rewards.prospector() == null
                                    ? CaveDropTrap.RewardFeasibilityFailure.REAL_PLANNER_REJECTED
                                    : CaveDropTrap.RewardFeasibilityFailure.NONE);
                }
            }
            selectorSlots.add(PassASelectorSlot.evaluated(
                    candidate,
                    plan,
                    evaluation,
                    CaveDropTrap.measureRoofedGalleryThroat(plan),
                    passAFinalWitness(traced, evaluation, ownerAnchor, floorY)));
        }
        return runPassASelectorAssignments(
                chunkX,
                chunkZ,
                source,
                selectorSlots,
                random,
                level.getSeed(),
                level.getMinY()).snapshot();
    }

    /**
     * The one diagnostic selector-assignment seam. Source rejects never enter {@code slots};
     * duplicates and structural rejects enter but cannot consume RNG. Every final structurally
     * feasible slot consumes exactly one roll in the supplied canonical order.
     */
    static PassASelectorRun runPassASelectorAssignments(
            int chunkX,
            int chunkZ,
            ConformalPassageScan source,
            List<PassASelectorSlot> slots,
            RandomSource random,
            long worldSeed,
            int minimumBuildY) {
        Objects.requireNonNull(source, "source");
        slots = List.copyOf(slots);
        Objects.requireNonNull(random, "random");
        if (!source.reconciles()
                || source.candidates().size() != slots.size()
                || !source.candidates().stream()
                        .map(ConformalThroatCandidate::identity).toList()
                        .equals(slots.stream().map(PassASelectorSlot::candidate).toList())) {
            throw new IllegalArgumentException(
                    "selector slots must cover the canonical qualified source exactly");
        }

        PassACensusTally tally = new PassACensusTally(chunkX, chunkZ);
        tally.recordAnchorSource(source);
        List<PassASelectorAssignment> assignments = new ArrayList<>();
        int accepted = 0;
        for (PassASelectorSlot slot : slots) {
            NaturalCaveAnchorCandidate candidate = slot.candidate();
            BlockPos ownerAnchor = candidate.ownerAnchor();
            CaveDropTrap.RibbonOrientation orientation = candidate.orientation();
            int floorY = candidate.floorY();
            if (slot.duplicate()) {
                tally.duplicateReject++;
                tally.consider(new PassACensusRepresentative(
                        "DUPLICATE",
                        ownerAnchor,
                        orientation,
                        floorY,
                        null, null, -1, null, null, false));
                tally.recordProvenance(slot.firstWitness());
                continue;
            }
            CaveDropTrap.RoofedThroatEvaluation evaluation = slot.evaluation();
            tally.recordStructural(
                    ownerAnchor,
                    orientation,
                    floorY,
                    evaluation,
                    slot.measurements(),
                    slot.firstWitness());
            if (!evaluation.ordinaryFeasible()) {
                continue;
            }
            tally.recordRareFeasibility(ownerAnchor, orientation, floorY, evaluation);
            float fractionRoll = random.nextFloat();
            CaveDropTrap.PassAPrediction prediction =
                    CaveDropTrap.predictRoofedThroatOutcome(
                            fractionRoll,
                            accepted,
                            worldSeed,
                            ownerAnchor.getX(),
                            ownerAnchor.getY(),
                            ownerAnchor.getZ(),
                            slot.plan(),
                            evaluation,
                            minimumBuildY);
            assignments.add(new PassASelectorAssignment(
                    candidate, fractionRoll, accepted, prediction));
            tally.recordPrediction(ownerAnchor, orientation, floorY, prediction);
            if (prediction.capAccepted()) {
                accepted++;
            }
        }
        return new PassASelectorRun(tally.snapshot(), assignments, accepted);
    }

    /**
     * Calls the one shared structural predicate through a tracing adapter. The adapter never decides
     * acceptance; it only retains the final world query because the predicate returns immediately
     * after its first failure.
     */
    public static PassATracedEvaluation evaluatePassAWithTrace(
            CaveDropTrap.RoofedGalleryThroatPlan plan,
            CaveDropTrap.RoofedThroatWorldView world,
            Function<CaveDropTrap.Voxel, PassAWorldSample> sampler,
            BlockPos ownerAnchor) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(sampler, "sampler");
        Objects.requireNonNull(ownerAnchor, "ownerAnchor");
        PassATracingWorldView tracing =
                new PassATracingWorldView(plan, world, sampler);
        CaveDropTrap.RoofedThroatEvaluation evaluation =
                CaveDropTrap.evaluateRoofedGalleryThroat(plan, tracing);
        return new PassATracedEvaluation(
                evaluation,
                tracing.firstReturnWitness(evaluation, ownerAnchor));
    }

    private static final class PassATracingWorldView
            implements CaveDropTrap.RoofedThroatWorldView {
        private final CaveDropTrap.RoofedGalleryThroatPlan plan;
        private final CaveDropTrap.RoofedThroatWorldView delegate;
        private final Function<CaveDropTrap.Voxel, PassAWorldSample> sampler;
        private final Set<CaveDropTrap.Voxel> continuationFloor;
        private final Set<CaveDropTrap.Voxel> coverFloor;
        private final Set<CaveDropTrap.Voxel> headroom;
        private final Set<CaveDropTrap.Cell> roofColumns;
        private PassAWorldSample lastSample;
        private PassAPredicateRole lastRole;

        private PassATracingWorldView(
                CaveDropTrap.RoofedGalleryThroatPlan plan,
                CaveDropTrap.RoofedThroatWorldView delegate,
                Function<CaveDropTrap.Voxel, PassAWorldSample> sampler) {
            this.plan = plan;
            this.delegate = delegate;
            this.sampler = sampler;
            continuationFloor = Set.copyOf(plan.continuationFloorVoxels());
            LinkedHashSet<CaveDropTrap.Voxel> cover = new LinkedHashSet<>();
            for (CaveDropTrap.Cell cell : plan.coverCells()) {
                cover.add(new CaveDropTrap.Voxel(cell.x(), plan.floorY(), cell.z()));
            }
            coverFloor = Set.copyOf(cover);
            headroom = Set.copyOf(plan.headroomVoxels());
            roofColumns = Set.copyOf(plan.relevantFloorColumns());
        }

        @Override
        public CaveDropTrap.ThroatBlockKind blockKind(CaveDropTrap.Voxel voxel) {
            CaveDropTrap.ThroatBlockKind kind = delegate.blockKind(voxel);
            PassAWorldSample sampled = sampler.apply(voxel);
            observe(
                    voxel,
                    new PassAWorldSample(
                            sampled.worldPosition(),
                            sampled.blockId(),
                            kind.name(),
                            sampled.actualAuthored(),
                            sampled.surfaceY()));
            return kind;
        }

        @Override
        public boolean insideOwnerInset(CaveDropTrap.Voxel voxel) {
            boolean inside = delegate.insideOwnerInset(voxel);
            observe(voxel, sampler.apply(voxel));
            return inside;
        }

        @Override
        public boolean authored(CaveDropTrap.Voxel voxel) {
            boolean authored = delegate.authored(voxel);
            PassAWorldSample sampled = sampler.apply(voxel);
            observe(
                    voxel,
                    new PassAWorldSample(
                            sampled.worldPosition(),
                            sampled.blockId(),
                            sampled.blockKind(),
                            authored,
                            sampled.surfaceY()));
            return authored;
        }

        @Override
        public boolean seesSky(CaveDropTrap.Voxel voxel) {
            boolean seesSky = delegate.seesSky(voxel);
            observe(voxel, sampler.apply(voxel));
            return seesSky;
        }

        @Override
        public int minimumBuildY() {
            return delegate.minimumBuildY();
        }

        private void observe(CaveDropTrap.Voxel voxel, PassAWorldSample sample) {
            lastSample = Objects.requireNonNull(sample);
            lastRole = roleFor(voxel);
        }

        private PassAPredicateRole roleFor(CaveDropTrap.Voxel voxel) {
            if (continuationFloor.contains(voxel)) {
                return PassAPredicateRole.MOUTH_OR_CONTINUATION_FLOOR;
            }
            if (coverFloor.contains(voxel)) {
                return PassAPredicateRole.COVER_FLOOR;
            }
            if (headroom.contains(voxel)) {
                return PassAPredicateRole.HEADROOM;
            }
            CaveDropTrap.Cell column = new CaveDropTrap.Cell(voxel.x(), voxel.z());
            if (roofColumns.contains(column) && voxel.y() >= plan.floorY() + 3) {
                return PassAPredicateRole.ROOF;
            }
            return PassAPredicateRole.AUTHORED_VOLUME_OR_OTHER;
        }

        private PassAFirstWitness firstReturnWitness(
                CaveDropTrap.RoofedThroatEvaluation evaluation,
                BlockPos ownerAnchor) {
            if (lastSample == null) {
                return new PassAFirstWitness(
                        evaluation.rejection().name(),
                        PassAProvenanceSource.NON_PREDICATE,
                        evaluation.rejection() == CaveDropTrap.PassARejection.PASS
                                ? PassAPredicateRole.PASSED_STRUCTURAL
                                : PassAPredicateRole.AUTHORED_VOLUME_OR_OTHER,
                        ownerAnchor,
                        "none",
                        "NONE",
                        false,
                        plan.floorY(),
                        plan.floorY(),
                        0);
            }
            PassAPredicateRole role =
                    evaluation.rejection() == CaveDropTrap.PassARejection.PASS
                            ? PassAPredicateRole.PASSED_STRUCTURAL
                            : lastRole;
            return new PassAFirstWitness(
                    evaluation.rejection().name(),
                    PassAProvenanceSource.PREDICATE_QUERY,
                    role,
                    lastSample.worldPosition(),
                    lastSample.blockId(),
                    lastSample.blockKind(),
                    lastSample.actualAuthored(),
                    plan.floorY(),
                    lastSample.surfaceY(),
                    plan.floorY() - lastSample.surfaceY());
        }
    }

    static PassAFirstWitness passAFinalWitness(
            PassATracedEvaluation traced,
            CaveDropTrap.RoofedThroatEvaluation finalEvaluation,
            BlockPos ownerAnchor,
            int floorY) {
        Objects.requireNonNull(traced, "traced");
        Objects.requireNonNull(finalEvaluation, "finalEvaluation");
        Objects.requireNonNull(ownerAnchor, "ownerAnchor");
        if (traced.evaluation().rejection() == finalEvaluation.rejection()) {
            return traced.firstWitness();
        }
        if (traced.evaluation().rejection() != CaveDropTrap.PassARejection.PASS
                || finalEvaluation.rejection()
                        != CaveDropTrap.PassARejection.DISCONNECTED_LOWER_OR_REWARD) {
            throw new IllegalArgumentException(
                    "unsupported Pass-A provenance transition "
                            + traced.evaluation().rejection() + " -> "
                            + finalEvaluation.rejection());
        }
        return new PassAFirstWitness(
                finalEvaluation.rejection().name(),
                PassAProvenanceSource.REWARD_PREFLIGHT,
                PassAPredicateRole.REWARD_PREFLIGHT,
                ownerAnchor,
                "none",
                "NONE",
                false,
                floorY,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE);
    }

    private static PassAFirstWitness syntheticPassAWitness(
            WorldGenLevel level,
            String terminalBucket,
            PassAPredicateRole role,
            BlockPos position,
            int floorY) {
        PassAWorldSample sample = passAWorldSampleAtPosition(level, position);
        return new PassAFirstWitness(
                terminalBucket,
                PassAProvenanceSource.NON_PREDICATE,
                role,
                sample.worldPosition(),
                sample.blockId(),
                sample.blockKind(),
                sample.actualAuthored(),
                floorY,
                sample.surfaceY(),
                floorY - sample.surfaceY());
    }

    private static CaveDropTrap.RoofedThroatWorldView passAWorldView(
            WorldGenLevel level,
            CaveDropTrap.RoofedGalleryThroatPlan plan,
            int baseX,
            int baseZ,
            int originLx,
            int originLz) {
        return new CaveDropTrap.RoofedThroatWorldView() {
            @Override
            public CaveDropTrap.ThroatBlockKind blockKind(CaveDropTrap.Voxel voxel) {
                BlockPos position =
                        passAWorldPosition(voxel, baseX, baseZ, originLx, originLz);
                BlockState state = level.getBlockState(position);
                return passABlockKind(level, position, state);
            }

            @Override
            public boolean insideOwnerInset(CaveDropTrap.Voxel voxel) {
                BlockPos position =
                        passAWorldPosition(voxel, baseX, baseZ, originLx, originLz);
                return insideOwnerChunk(position, baseX, baseZ)
                        && CaveDropTrapFeature.insideOwnerInset(position);
            }

            @Override
            public boolean authored(CaveDropTrap.Voxel voxel) {
                BlockPos position =
                        passAWorldPosition(voxel, baseX, baseZ, originLx, originLz);
                BlockState state = level.getBlockState(position);
                return isPassAAuthored(state);
            }

            @Override
            public boolean seesSky(CaveDropTrap.Voxel voxel) {
                return level.canSeeSky(
                        passAWorldPosition(voxel, baseX, baseZ, originLx, originLz));
            }

            @Override
            public int minimumBuildY() {
                return level.getMinY();
            }
        };
    }

    private static BlockPos passAWorldPosition(
            CaveDropTrap.Voxel voxel,
            int baseX,
            int baseZ,
            int originLx,
            int originLz) {
        return new BlockPos(
                baseX + originLx + voxel.x(),
                voxel.y(),
                baseZ + originLz + voxel.z());
    }

    private static PassAWorldSample passAWorldSample(
            WorldGenLevel level,
            CaveDropTrap.Voxel voxel,
            int baseX,
            int baseZ,
            int originLx,
            int originLz) {
        return passAWorldSampleAtPosition(
                level,
                passAWorldPosition(voxel, baseX, baseZ, originLx, originLz));
    }

    private static PassAWorldSample passAWorldSampleAtPosition(
            WorldGenLevel level,
            BlockPos position) {
        BlockState state = level.getBlockState(position);
        CaveDropTrap.ThroatBlockKind kind = passABlockKind(level, position, state);
        int surfaceY = level.getHeight(
                FEATURE_STAGE_SURFACE_HEIGHTMAP, position.getX(), position.getZ());
        return new PassAWorldSample(
                position,
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                kind.name(),
                isPassAAuthored(state),
                surfaceY);
    }

    private static CaveDropTrap.ThroatBlockKind passABlockKind(
            WorldGenLevel level,
            BlockPos position,
            BlockState state) {
        if (state.isAir()) {
            return CaveDropTrap.ThroatBlockKind.AIR;
        }
        if (!state.getFluidState().isEmpty()) {
            return CaveDropTrap.ThroatBlockKind.FLUID;
        }
        if (level.getBlockEntity(position) != null || isOre(state)) {
            return CaveDropTrap.ThroatBlockKind.ORE_OR_BLOCK_ENTITY;
        }
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.MAGMA_BLOCK)) {
            return CaveDropTrap.ThroatBlockKind.PROTECTED;
        }
        if (state.is(Blocks.GRAVEL)) {
            return CaveDropTrap.ThroatBlockKind.GRAVEL;
        }
        if (safeNaturalSupport(level, position, state)) {
            return CaveDropTrap.ThroatBlockKind.SAFE_NATURAL;
        }
        return CaveDropTrap.ThroatBlockKind.OTHER;
    }

    private static boolean isPassAAuthored(BlockState state) {
        return CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW != null
                && state.is(CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW);
    }

    /** The exact existing reward planners' read-only results for one Pass-A geometry. */
    private record PassARewardPreflight(
            ScenePlan ordinary,
            ScenePlan flooded,
            ScenePlan prospector) {
    }

    private static PassARewardPreflight passARewardPreflight(
            WorldGenLevel level,
            CaveDropTrap.RoofedGalleryThroatPlan plan,
            int baseX,
            int baseZ,
            int originLx,
            int originLz) {
        TrapGeometry geometry =
                materializePassAThroat(level, plan, baseX, baseZ, originLx, originLz);
        if (geometry == null) {
            return null;
        }
        ScenePlan ordinary = ordinaryScene(geometry);
        ScenePlan flooded =
                floodedScene(level, geometry, baseX, baseZ, new HashMap<>());
        ScenePlan prospector =
                prospectorScene(level, geometry, ordinary, baseX, baseZ, new HashMap<>());
        return new PassARewardPreflight(ordinary, flooded, prospector);
    }

    /**
     * Adapts the future throat's measured geometry into the existing immutable production planning
     * model. It constructs expected writes only; {@link #applyScene} is never called from Pass A.
     */
    private static TrapGeometry materializePassAThroat(
            WorldGenLevel level,
            CaveDropTrap.RoofedGalleryThroatPlan plan,
            int baseX,
            int baseZ,
            int originLx,
            int originLz) {
        if (CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW == null) {
            return null;
        }
        BlockState entrancePowder = CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW.defaultBlockState();
        LinkedHashMap<BlockPos, WorldWrite> upper = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, WorldWrite> cushions = new LinkedHashMap<>();
        List<DropCell> powder = new ArrayList<>();
        List<StableFloor> regular = new ArrayList<>();
        for (CaveDropTrap.Cell cell : plan.powderCells()) {
            int lx = originLx + cell.x();
            int lz = originLz + cell.z();
            powder.add(new DropCell(
                    lx, lz, plan.floorY(),
                    CaveDropTrap.MIN_CERTIFIED_SOLID_DEPTH,
                    plan.landingY(), CaveDropTrap.MIN_DROP_AIR));
            if (!addPassAPlannedWrite(
                        level, upper,
                        new BlockPos(baseX + lx, plan.floorY(), baseZ + lz),
                        entrancePowder, AuthoredRole.ENTRANCE_POWDER)) {
                return null;
            }
            for (int y = plan.floorY() - 1; y > plan.landingY(); y--) {
                if (!addPassAPlannedWrite(
                            level, upper,
                            new BlockPos(baseX + lx, y, baseZ + lz),
                            AIR, AuthoredRole.OPENED_SHAFT)) {
                    return null;
                }
            }
            if (!addPassAPlannedWrite(
                        level, cushions,
                        new BlockPos(baseX + lx, plan.landingY(), baseZ + lz),
                        CUSHION_POWDER_SNOW, AuthoredRole.POWDER_CUSHION)) {
                return null;
            }
        }
        for (CaveDropTrap.Cell cell : plan.firmCells()) {
            int lx = originLx + cell.x();
            int lz = originLz + cell.z();
            regular.add(new StableFloor(lx, lz, plan.floorY()));
            if (!addPassAPlannedWrite(
                        level, upper,
                        new BlockPos(baseX + lx, plan.floorY(), baseZ + lz),
                        SNOW_BLOCK, AuthoredRole.FIRM_CAMOUFLAGE)) {
                return null;
            }
        }
        for (CaveDropTrap.Voxel voxel : plan.carvedAirVoxels()) {
            BlockPos position =
                    passAWorldPosition(voxel, baseX, baseZ, originLx, originLz);
            WorldWrite existing = upper.get(position);
            if (existing != null) {
                continue;
            }
            // Mirrors the shared cavity law: at and above the carpet plane the clearance volume
            // is the climb-out stair emerging into the passage, so a cell that is already open
            // cave air needs no write at all. Below the carpet the shaft must still be cut from
            // genuine solid mass, so those cells keep the strict planned-write requirement.
            if (voxel.y() >= plan.floorY()
                    && insideOwnerInset(position)
                    && airWithoutEntity(level, position)) {
                continue;
            }
            if (!addPassAPlannedWrite(
                    level, upper, position, AIR, AuthoredRole.GALLERY_AIR)) {
                return null;
            }
        }

        // Continuation floors stand at the passage's own height (the conformal mouths may sit
        // one step up or down), so the audit event must carry their real Y, not the carpet plane.
        Map<CaveDropTrap.Cell, Integer> continuationFloorY = new LinkedHashMap<>();
        for (CaveDropTrap.Voxel floor : plan.continuationFloorVoxels()) {
            continuationFloorY.put(
                    new CaveDropTrap.Cell(floor.x(), floor.z()), floor.y());
        }
        // The climber leaves the stair onto exactly one untouched return-mouth floor; that
        // single natural cell is the trap's upper natural return.
        CaveDropTrap.Voxel stairExitStanding = plan.stairStandingVoxels().getLast();
        CaveDropTrap.Cell upperReturnCell = plan.returnB().stream()
                .filter(cell -> Math.abs(cell.x() - stairExitStanding.x())
                        + Math.abs(cell.z() - stairExitStanding.z()) == 1)
                .findFirst()
                .orElse(null);
        if (upperReturnCell == null || !continuationFloorY.containsKey(upperReturnCell)) {
            return null;
        }
        BlockPos upperReturnFloor = new BlockPos(
                baseX + originLx + upperReturnCell.x(),
                continuationFloorY.get(upperReturnCell),
                baseZ + originLz + upperReturnCell.z());

        Set<CaveDropTrap.Voxel> routeStanding =
                Set.copyOf(plan.lowerRouteStandingVoxels());
        CaveDropTrap.Voxel dryExit = plan.stairStandingVoxels().getLast();
        List<LandingRoute> landingRoutes = new ArrayList<>();
        for (CaveDropTrap.Cell entry : plan.powderCells()) {
            CaveDropTrap.Voxel cushion =
                    new CaveDropTrap.Voxel(entry.x(), plan.landingY(), entry.z());
            CaveDropTrap.Voxel top =
                    new CaveDropTrap.Voxel(entry.x(), plan.landingY() + 1, entry.z());
            List<CaveDropTrap.Voxel> path =
                    passARoute(top, dryExit, routeStanding);
            if (path == null) {
                return null;
            }
            List<CaveDropTrap.Voxel> complete = new ArrayList<>();
            complete.add(cushion);
            complete.addAll(path);
            List<BlockPos> worldPath = new ArrayList<>();
            for (CaveDropTrap.Voxel voxel : complete) {
                worldPath.add(passAWorldPosition(
                        voxel, baseX, baseZ, originLx, originLz));
            }
            // The route's final step leaves the authored stair for the untouched mouth floor.
            worldPath.add(upperReturnFloor.above());
            landingRoutes.add(new LandingRoute(
                    new BlockPos(
                            baseX + originLx + entry.x(),
                            plan.floorY(),
                            baseZ + originLz + entry.z()),
                    worldPath.getFirst(),
                    worldPath.getLast(),
                    List.copyOf(worldPath)));
        }
        LinkedHashMap<BlockPos, WorldWrite> projected = new LinkedHashMap<>(upper);
        projected.putAll(cushions);
        if (!passADryRoutesRemainSupported(level, projected, landingRoutes)) {
            return null;
        }

        CaveDropTrap.InsetCarpetPlan carpet = new CaveDropTrap.InsetCarpetPlan(
                plan.powderCells().stream()
                        .map(cell -> new CaveDropTrap.Cell(
                                originLx + cell.x(), originLz + cell.z()))
                        .toList(),
                plan.firmCells().stream()
                        .map(cell -> new CaveDropTrap.Cell(
                                originLx + cell.x(), originLz + cell.z()))
                        .toList(),
                plan.firmBanks().get(0).stream()
                        .map(cell -> new CaveDropTrap.Cell(
                                originLx + cell.x(), originLz + cell.z()))
                        .toList(),
                plan.firmBanks().get(1).stream()
                        .map(cell -> new CaveDropTrap.Cell(
                                originLx + cell.x(), originLz + cell.z()))
                        .toList(),
                // The throat's walk axis runs along the template's u axis, which the
                // orientation transform maps PERPENDICULAR to the ribbon-era axis label.
                plan.orientation().axis() == CaveDropTrap.ApproachAxis.NORTH_SOUTH
                        ? CaveDropTrap.ApproachAxis.EAST_WEST
                        : CaveDropTrap.ApproachAxis.NORTH_SOUTH);
        List<BlockPos> approach = new ArrayList<>();
        for (CaveDropTrap.Cell cell : plan.approachA()) {
            approach.add(new BlockPos(
                    baseX + originLx + cell.x(),
                    continuationFloorY.get(cell),
                    baseZ + originLz + cell.z()));
        }
        List<BlockPos> returned = new ArrayList<>();
        for (CaveDropTrap.Cell cell : plan.returnB()) {
            returned.add(new BlockPos(
                    baseX + originLx + cell.x(),
                    continuationFloorY.get(cell),
                    baseZ + originLz + cell.z()));
        }

        // The prospector micro-scene lives on the corridor line, which the conformal source
        // may shift laterally with the passage; the locked factory plan recovers zero.
        int corridorLateral = passACorridorLateral(plan);
        CaveDropTrap.Cell cushionCell = passAOrient(
                new CaveDropTrap.Cell(2, 1 + corridorLateral), plan.orientation());
        CaveDropTrap.Cell footCell = passAOrient(
                new CaveDropTrap.Cell(2, 2 + corridorLateral), plan.orientation());
        CaveDropTrap.Cell headCell = passAOrient(
                new CaveDropTrap.Cell(3, 2 + corridorLateral), plan.orientation());
        CaveDropTrap.Cell chestCell = passAOrient(
                new CaveDropTrap.Cell(3, 3 + corridorLateral), plan.orientation());
        CaveDropTrap.Cell frontCell = passAOrient(
                new CaveDropTrap.Cell(3, 4 + corridorLateral), plan.orientation());
        BlockPos prospectorCushion = passAWorldPosition(
                new CaveDropTrap.Voxel(
                        cushionCell.x(), plan.landingY(), cushionCell.z()),
                baseX, baseZ, originLx, originLz);
        BlockPos prospectorFoot = passAWorldPosition(
                new CaveDropTrap.Voxel(footCell.x(), plan.landingY(), footCell.z()),
                baseX, baseZ, originLx, originLz);
        BlockPos prospectorHead = passAWorldPosition(
                new CaveDropTrap.Voxel(headCell.x(), plan.landingY(), headCell.z()),
                baseX, baseZ, originLx, originLz);
        BlockPos prospectorChest = passAWorldPosition(
                new CaveDropTrap.Voxel(chestCell.x(), plan.landingY(), chestCell.z()),
                baseX, baseZ, originLx, originLz);
        BlockPos prospectorFront = passAWorldPosition(
                new CaveDropTrap.Voxel(frontCell.x(), plan.landingY(), frontCell.z()),
                baseX, baseZ, originLx, originLz);
        Direction entryDirection = horizontalDirection(prospectorCushion, prospectorFoot);
        Direction bodyFacing = horizontalDirection(prospectorFoot, prospectorHead);
        Direction chestFacing = horizontalDirection(prospectorChest, prospectorFront);
        if (entryDirection == null || bodyFacing == null || chestFacing == null
                || entryDirection.getAxis() == bodyFacing.getAxis()
                || bodyFacing.getAxis() == chestFacing.getAxis()) {
            return null;
        }
        ProspectorPlacement placement = new ProspectorPlacement(
                prospectorCushion, prospectorFoot, prospectorHead,
                prospectorChest, prospectorFront,
                entryDirection, bodyFacing, chestFacing);
        DropCell anchorCell = powder.stream()
                .min(Comparator.comparingInt(DropCell::lx)
                        .thenComparingInt(DropCell::lz))
                .orElseThrow();
        BlockPos anchor = new BlockPos(
                baseX + anchorCell.lx(), anchorCell.floorY(), baseZ + anchorCell.lz());
        return new TrapGeometry(
                carpet, List.copyOf(powder), List.copyOf(regular),
                List.copyOf(approach), List.copyOf(returned),
                List.of(upperReturnFloor),
                List.copyOf(upper.values()), List.copyOf(cushions.values()),
                List.copyOf(landingRoutes), placement,
                anchor, CaveDropTrap.MIN_DROP_AIR);
    }

    private static boolean addPassAPlannedWrite(
            WorldGenLevel level,
            Map<BlockPos, WorldWrite> writes,
            BlockPos position,
            BlockState desired,
            AuthoredRole role) {
        if (!insideOwnerInset(position) || level.getBlockEntity(position) != null) {
            return false;
        }
        BlockState current = level.getBlockState(position);
        CensusSolidDefect defect = classifyCensusSolidDefect(true, current, false);
        return (defect == CensusSolidDefect.NONE
                        || defect == CensusSolidDefect.FALLING_NATURAL_STABILIZABLE)
                && putWrite(level, writes, position, desired, role);
    }

    private static CaveDropTrap.Cell passAOrient(
            CaveDropTrap.Cell cell, CaveDropTrap.RibbonOrientation orientation) {
        return switch (orientation) {
            case NORTH -> cell;
            case EAST -> new CaveDropTrap.Cell(-cell.z(), cell.x());
            case SOUTH -> new CaveDropTrap.Cell(-cell.x(), -cell.z());
            case WEST -> new CaveDropTrap.Cell(cell.z(), -cell.x());
        };
    }

    private static CaveDropTrap.Cell passAUnorient(
            CaveDropTrap.Cell cell, CaveDropTrap.RibbonOrientation orientation) {
        return switch (orientation) {
            case NORTH -> cell;
            case EAST -> new CaveDropTrap.Cell(cell.z(), -cell.x());
            case SOUTH -> new CaveDropTrap.Cell(-cell.x(), -cell.z());
            case WEST -> new CaveDropTrap.Cell(-cell.z(), cell.x());
        };
    }

    /**
     * Recovers the conformal corridor's lateral lane from a throat plan. The first stair
     * standing is canonically {@code (9, 2 + lateral)} in every plan this feature evaluates;
     * the locked factory plan always recovers zero.
     */
    static int passACorridorLateral(CaveDropTrap.RoofedGalleryThroatPlan plan) {
        CaveDropTrap.Voxel first = plan.stairStandingVoxels().getFirst();
        CaveDropTrap.Cell canonical = passAUnorient(
                new CaveDropTrap.Cell(first.x(), first.z()), plan.orientation());
        if (canonical.x() != 5) {
            throw new IllegalArgumentException(
                    "first stair standing is not on the canonical stair column: " + canonical);
        }
        return canonical.z() - 2;
    }

    private static List<CaveDropTrap.Voxel> passARoute(
            CaveDropTrap.Voxel start,
            CaveDropTrap.Voxel target,
            Set<CaveDropTrap.Voxel> allowed) {
        if (!allowed.contains(start) || !allowed.contains(target)) {
            return null;
        }
        Map<CaveDropTrap.Voxel, CaveDropTrap.Voxel> parents = new LinkedHashMap<>();
        ArrayDeque<CaveDropTrap.Voxel> queue = new ArrayDeque<>();
        parents.put(start, null);
        queue.add(start);
        while (!queue.isEmpty() && !parents.containsKey(target)) {
            CaveDropTrap.Voxel current = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) != 1) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        CaveDropTrap.Voxel next = new CaveDropTrap.Voxel(
                                current.x() + dx, current.y() + dy, current.z() + dz);
                        if (allowed.contains(next) && !parents.containsKey(next)) {
                            parents.put(next, current);
                            queue.addLast(next);
                        }
                    }
                }
            }
        }
        if (!parents.containsKey(target)) {
            return null;
        }
        ArrayDeque<CaveDropTrap.Voxel> reverse = new ArrayDeque<>();
        for (CaveDropTrap.Voxel cursor = target;
                cursor != null;
                cursor = parents.get(cursor)) {
            reverse.addFirst(cursor);
        }
        return List.copyOf(reverse);
    }

    private static final class PassACensusTally {
        private final int chunkX;
        private final int chunkZ;
        private long legalFloorCount;
        private long auditDomainSlots;
        private long caveAnchorQualified;
        private long caveAnchorRejected;
        private long scanSlots;
        private long uniqueOwnerAnchors;
        private long duplicateReject;
        private long sameDoorReject;
        private long invalidOrSelfAuthoredWitnessReject;
        private long exposedSkyWaterOrThinRoofReject;
        private long seafloorOrFluidStandingReject;
        private long firmBypassOrWrongRolesReject;
        private long cavityAirOrFluidReject;
        private long oreBlockEntityOrProtectedReject;
        private long gravityOrPartialStabilizationReject;
        private long shellOrSupportReject;
        private long ownerReject;
        private long disconnectedLowerOrRewardReject;
        private long ordinaryFeasible;
        private long floodedFeasible;
        private long floodedFallback;
        private long prospectorFeasible;
        private long prospectorFallback;
        private long fractionAccepted;
        private long fractionRejected;
        private long capAccepted;
        private long capRejected;
        private long resolvedOrdinary;
        private long resolvedFlooded;
        private long resolvedProspector;
        private long resolvedFallback;
        private final long[] rewardRollCounts = new long[16];
        private long requestedOrdinary;
        private long requestedFlooded;
        private long requestedProspector;
        private long floodedRequestFallback;
        private long prospectorRequestFallback;
        private CaveDropTrap.RoofedThroatMeasurements topologyMeasurements;
        private boolean topologyConflict;
        private final Map<String, PassACensusRepresentative> representatives =
                new LinkedHashMap<>();
        private final Map<String, PassAProvenanceCount> provenance =
                new LinkedHashMap<>();

        private PassACensusTally(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private void recordAnchorSource(ConformalPassageScan source) {
            if (!source.reconciles()
                    || legalFloorCount != 0L
                    || auditDomainSlots != 0L
                    || caveAnchorQualified != 0L
                    || caveAnchorRejected != 0L
                    || scanSlots != 0L) {
                throw new IllegalStateException(
                        "Pass-A conformal passage source was invalid or recorded twice");
            }
            legalFloorCount = source.legalFloorCount();
            auditDomainSlots = source.auditDomainSlots();
            caveAnchorQualified = source.caveAnchorQualified();
            caveAnchorRejected = source.caveAnchorRejected();
            scanSlots = source.caveAnchorQualified();
        }

        private void recordStructural(
                BlockPos anchor,
                CaveDropTrap.RibbonOrientation orientation,
                int floorY,
                CaveDropTrap.RoofedThroatEvaluation evaluation,
                CaveDropTrap.RoofedThroatMeasurements measurements,
                PassAFirstWitness firstWitness) {
            if (topologyMeasurements == null && !topologyConflict) {
                topologyMeasurements = measurements;
            } else if (!Objects.equals(topologyMeasurements, measurements)) {
                topologyMeasurements = null;
                topologyConflict = true;
            }
            uniqueOwnerAnchors++;
            CaveDropTrap.PassARejection rejection = evaluation.rejection();
            switch (rejection) {
                case PASS -> ordinaryFeasible++;
                case SAME_DOOR -> sameDoorReject++;
                case INVALID_OR_SELF_AUTHORED_WITNESS ->
                        invalidOrSelfAuthoredWitnessReject++;
                case EXPOSED_SKY_WATER_OR_THIN_ROOF ->
                        exposedSkyWaterOrThinRoofReject++;
                case SEAFLOOR_OR_FLUID_STANDING -> seafloorOrFluidStandingReject++;
                case FIRM_BYPASS_OR_WRONG_ROLES -> firmBypassOrWrongRolesReject++;
                case CAVITY_AIR_OR_FLUID -> cavityAirOrFluidReject++;
                case ORE_BLOCK_ENTITY_OR_PROTECTED -> oreBlockEntityOrProtectedReject++;
                case GRAVITY_OR_PARTIAL_STABILIZATION ->
                        gravityOrPartialStabilizationReject++;
                case SHELL_OR_SUPPORT -> shellOrSupportReject++;
                case OWNER -> ownerReject++;
                case DISCONNECTED_LOWER_OR_REWARD ->
                        disconnectedLowerOrRewardReject++;
            }
            if (rejection != CaveDropTrap.PassARejection.PASS) {
                consider(new PassACensusRepresentative(
                        rejection.name(), anchor, orientation, floorY,
                        rejection, null, -1, null, null, false));
            }
            recordProvenance(firstWitness);
        }

        private void recordRareFeasibility(
                BlockPos anchor,
                CaveDropTrap.RibbonOrientation orientation,
                int floorY,
                CaveDropTrap.RoofedThroatEvaluation evaluation) {
            if (evaluation.floodedFeasible()) {
                floodedFeasible++;
            } else {
                floodedFallback++;
                consider(new PassACensusRepresentative(
                        "FLOODED_" + evaluation.floodedFailure().name(),
                        anchor, orientation, floorY, null,
                        evaluation.floodedFailure(), -1, null,
                        CaveDropTrap.RewardKind.ORDINARY, true));
            }
            if (evaluation.prospectorFeasible()) {
                prospectorFeasible++;
            } else {
                prospectorFallback++;
                consider(new PassACensusRepresentative(
                        "PROSPECTOR_" + evaluation.prospectorFailure().name(),
                        anchor, orientation, floorY, null,
                        evaluation.prospectorFailure(), -1, null,
                        CaveDropTrap.RewardKind.ORDINARY, true));
            }
        }

        private void recordPrediction(
                BlockPos anchor,
                CaveDropTrap.RibbonOrientation orientation,
                int floorY,
                CaveDropTrap.PassAPrediction prediction) {
            if (!prediction.fractionAccepted()) {
                fractionRejected++;
                consider(new PassACensusRepresentative(
                        "FRACTION_REJECT", anchor, orientation, floorY,
                        null, null, -1, null, null, false));
                return;
            }
            fractionAccepted++;
            if (!prediction.capAccepted()) {
                capRejected++;
                consider(new PassACensusRepresentative(
                        "CAP_REJECT", anchor, orientation, floorY,
                        null, null, -1, null, null, false));
                return;
            }
            capAccepted++;
            rewardRollCounts[prediction.rewardRoll()]++;
            switch (prediction.requestedKind()) {
                case ORDINARY -> requestedOrdinary++;
                case FLOODED_ORE_GALLERY -> requestedFlooded++;
                case LOST_PROSPECTOR -> requestedProspector++;
            }
            if (prediction.ordinaryFallback()) {
                resolvedFallback++;
                if (prediction.requestedKind()
                        == CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY) {
                    floodedRequestFallback++;
                } else if (prediction.requestedKind()
                        == CaveDropTrap.RewardKind.LOST_PROSPECTOR) {
                    prospectorRequestFallback++;
                }
            }
            switch (prediction.resolvedKind()) {
                case ORDINARY -> resolvedOrdinary++;
                case FLOODED_ORE_GALLERY -> resolvedFlooded++;
                case LOST_PROSPECTOR -> resolvedProspector++;
            }
            consider(new PassACensusRepresentative(
                    "RESOLVED_" + prediction.resolvedKind().name(),
                    anchor, orientation, floorY, null, null,
                    prediction.rewardRoll(), prediction.requestedKind(),
                    prediction.resolvedKind(), prediction.ordinaryFallback()));
        }

        private void consider(PassACensusRepresentative candidate) {
            representatives.merge(
                    candidate.stage(), candidate,
                    (first, second) -> second.isBetterThan(first) ? second : first);
        }

        private void recordProvenance(PassAFirstWitness witness) {
            String key = witness.provenanceKey();
            provenance.merge(
                    key,
                    new PassAProvenanceCount(key, 1, witness),
                    (first, second) -> new PassAProvenanceCount(
                            key,
                            first.count() < 0 || first.count() == Long.MAX_VALUE
                                    ? -1 : first.count() + 1,
                            second.representative().isBetterThan(first.representative())
                                    ? second.representative()
                                    : first.representative()));
        }

        private PassACensusSnapshot snapshot() {
            List<Long> rolls = new ArrayList<>();
            for (long count : rewardRollCounts) {
                rolls.add(count);
            }
            return new PassACensusSnapshot(
                    chunkX, chunkZ,
                    legalFloorCount, auditDomainSlots,
                    caveAnchorQualified, caveAnchorRejected,
                    scanSlots, uniqueOwnerAnchors, duplicateReject,
                    sameDoorReject, invalidOrSelfAuthoredWitnessReject,
                    exposedSkyWaterOrThinRoofReject, seafloorOrFluidStandingReject,
                    firmBypassOrWrongRolesReject, cavityAirOrFluidReject,
                    oreBlockEntityOrProtectedReject,
                    gravityOrPartialStabilizationReject, shellOrSupportReject,
                    ownerReject, disconnectedLowerOrRewardReject,
                    ordinaryFeasible,
                    floodedFeasible, floodedFallback,
                    prospectorFeasible, prospectorFallback,
                    fractionAccepted, fractionRejected,
                    capAccepted, capRejected,
                    resolvedOrdinary, resolvedFlooded, resolvedProspector,
                    resolvedFallback,
                    rolls,
                    requestedOrdinary, requestedFlooded, requestedProspector,
                    floodedRequestFallback, prospectorRequestFallback,
                    topologyConflict ? null : topologyMeasurements,
                    representatives.values().stream()
                            .sorted(Comparator.comparing(
                                    PassACensusRepresentative::stage))
                            .toList(),
                    provenance.values().stream()
                            .sorted(Comparator.comparing(PassAProvenanceCount::key))
                            .toList());
        }
    }

    /** Exact dynamic predicate results for the immutable true-predicate production template. */
    private record EnvelopeDiagnosis(
            int solidFailures,
            BlockPos firstSolidFailure,
            String firstSolidState,
            int airFailures,
            BlockPos firstAirFailure,
            String firstAirState,
            CensusDefectCounts solidDefects) {
    }

    /** Exact maintained-surface measurements for a plan that already passed its envelope. */
    private record EnclosureDiagnosis(int minimumRise, int failingColumns) {
    }

    /** Exact count of falling blocks exposed directly above the nominal authored carve. */
    private record CensusGravityProjection(int exposedGravityHazards) {
    }

    private static final class CensusTally {
        private final int chunkX;
        private final int chunkZ;
        private long scanSlots;
        private long referenceFloorHeadroomPass;
        private long referenceFloorHeadroomFail;
        private long envelopePass;
        private long envelopeSolidFail;
        private long envelopeAirFail;
        private long envelopeBothFail;
        private long envelopeOtherFail;
        private long stabilizationOnlyEnvelopePass;
        private long enclosurePass;
        private long enclosureFail;
        private long stabilizationOnlyEnclosurePass;
        private long stabilizationOnlyEnclosureFail;
        private long gravityHazardPass;
        private long gravityHazardFail;
        private long stabilizationOnlyGravityHazardPass;
        private long stabilizationOnlyGravityHazardFail;
        private long materializationPass;
        private long materializationFail;
        private long stabilizationOnlyMaterializationNotModeled;
        private CensusDefectCounts solidDefects = CensusDefectCounts.empty();
        private CensusDefectCounts currentSolidDefects = CensusDefectCounts.empty();
        private CensusRepresentative representative;
        private final Map<Integer, CensusRepresentative> terminalRepresentatives = new HashMap<>();

        private CensusTally(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private CaveDropTrap.EmbeddedRibbonPlan evaluateHypotheticalEnvelope(
                CaveDropTrap.EmbeddedRibbonPlan shape,
                CaveDropTrap.RibbonOrientation orientation,
                int originLx,
                int originLz,
                int floorY,
                WorldGenLevel level,
                int baseX,
                int baseZ) {
            EnvelopeDiagnosis diagnosis = envelopeDiagnosis(
                    shape, floorY, level, baseX, baseZ, originLx, originLz);
            currentSolidDefects = diagnosis.solidDefects();
            solidDefects = solidDefects.plus(diagnosis.solidDefects());
            CaveDropTrap.EmbeddedRibbonPlan hypothetical = null;
            if (diagnosis.airFailures() == 0
                    && diagnosis.solidDefects().stabilizationOnly()) {
                hypothetical = CaveDropTrap.planEmbeddedRibbon(
                        floorY,
                        orientation,
                        voxel -> censusHypotheticalSolidQualifies(
                                level,
                                ribbonWorldPosition(
                                        voxel, baseX, baseZ, originLx, originLz)),
                        voxel -> {
                            BlockPos position = ribbonWorldPosition(
                                    voxel, baseX, baseZ, originLx, originLz);
                            return insideOwnerInset(position)
                                    && airWithoutEntity(level, position);
                        });
            }
            if (hypothetical != null) {
                stabilizationOnlyEnvelopePass++;
                return hypothetical;
            }
            if (diagnosis.solidFailures() > 0 && diagnosis.airFailures() > 0) {
                envelopeBothFail++;
            } else if (diagnosis.solidFailures() > 0) {
                envelopeSolidFail++;
            } else if (diagnosis.airFailures() > 0) {
                envelopeAirFail++;
            } else {
                envelopeOtherFail++;
            }
            consider(new CensusRepresentative(
                    2, chunkX, chunkZ, orientation, originLx, originLz, floorY,
                    diagnosis.solidFailures(), diagnosis.firstSolidFailure(),
                    diagnosis.firstSolidState(), diagnosis.airFailures(),
                    diagnosis.firstAirFailure(), diagnosis.firstAirState(), 0, 0,
                    diagnosis.solidDefects(), 0, false));
            return null;
        }

        private void consider(CensusRepresentative candidate) {
            if (candidate.isBetterThan(representative)) {
                representative = candidate;
            }
            terminalRepresentatives.merge(
                    candidate.stageRank(), candidate,
                    (first, second) -> second.isBetterThan(first) ? second : first);
        }

        private CensusSnapshot snapshot() {
            return new CensusSnapshot(
                    chunkX, chunkZ,
                    scanSlots, referenceFloorHeadroomPass, referenceFloorHeadroomFail,
                    envelopePass, envelopeSolidFail, envelopeAirFail, envelopeBothFail,
                    envelopeOtherFail, stabilizationOnlyEnvelopePass,
                    enclosurePass, enclosureFail,
                    stabilizationOnlyEnclosurePass, stabilizationOnlyEnclosureFail,
                    gravityHazardPass, gravityHazardFail,
                    stabilizationOnlyGravityHazardPass,
                    stabilizationOnlyGravityHazardFail,
                    materializationPass, materializationFail,
                    stabilizationOnlyMaterializationNotModeled, solidDefects,
                    representative,
                    terminalRepresentatives.values().stream()
                            .sorted(Comparator.comparingInt(CensusRepresentative::stageRank))
                            .toList());
        }
    }

    private static EnvelopeDiagnosis envelopeDiagnosis(
            CaveDropTrap.EmbeddedRibbonPlan shape,
            int candidateFloorY,
            WorldGenLevel level,
            int baseX,
            int baseZ,
            int originLx,
            int originLz) {
        int solidFailures = 0;
        BlockPos firstSolid = null;
        String firstSolidState = null;
        long fallingNatural = 0;
        long voidOrAir = 0;
        long fluid = 0;
        long oreOrBlockEntity = 0;
        long protectedMass = 0;
        long outsideOwner = 0;
        long other = 0;
        for (CaveDropTrap.Voxel voxel : shape.requiredSolidVoxels()) {
            BlockPos position = censusTemplatePosition(
                    shape, voxel, candidateFloorY, baseX, baseZ, originLx, originLz);
            BlockState state = level.getBlockState(position);
            CensusSolidDefect defect = classifyCensusSolidDefect(
                    insideOwnerInset(position), state, level.getBlockEntity(position) != null);
            if (defect != CensusSolidDefect.NONE) {
                solidFailures++;
                if (firstSolid == null) {
                    firstSolid = position.immutable();
                    firstSolidState = state.toString();
                }
                switch (defect) {
                    case FALLING_NATURAL_STABILIZABLE -> fallingNatural++;
                    case VOID_OR_AIR -> voidOrAir++;
                    case FLUID -> fluid++;
                    case ORE_OR_BLOCK_ENTITY -> oreOrBlockEntity++;
                    case PROTECTED_BEDROCK_OR_MAGMA -> protectedMass++;
                    case OUTSIDE_OWNER -> outsideOwner++;
                    case OTHER -> other++;
                    case NONE -> throw new IllegalStateException("non-defect entered defect switch");
                }
            }
        }
        int airFailures = 0;
        BlockPos firstAir = null;
        String firstAirState = null;
        for (CaveDropTrap.Voxel voxel : shape.requiredAirVoxels()) {
            BlockPos position = censusTemplatePosition(
                    shape, voxel, candidateFloorY, baseX, baseZ, originLx, originLz);
            BlockState state = level.getBlockState(position);
            if (!insideOwnerInset(position) || !airWithoutEntity(level, position)) {
                airFailures++;
                if (firstAir == null) {
                    firstAir = position.immutable();
                    firstAirState = state.toString();
                }
            }
        }
        return new EnvelopeDiagnosis(
                solidFailures, firstSolid, firstSolidState,
                airFailures, firstAir, firstAirState,
                new CensusDefectCounts(
                        fallingNatural, voidOrAir, fluid, oreOrBlockEntity,
                        protectedMass, outsideOwner, other));
    }

    static CensusSolidDefect classifyCensusSolidDefect(
            boolean insideOwner, BlockState state, boolean blockEntityPresent) {
        if (!insideOwner) {
            return CensusSolidDefect.OUTSIDE_OWNER;
        }
        if (state == null || state.isAir()) {
            return CensusSolidDefect.VOID_OR_AIR;
        }
        if (!state.getFluidState().isEmpty()) {
            return CensusSolidDefect.FLUID;
        }
        if (blockEntityPresent || isOre(state)) {
            return CensusSolidDefect.ORE_OR_BLOCK_ENTITY;
        }
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.MAGMA_BLOCK)) {
            return CensusSolidDefect.PROTECTED_BEDROCK_OR_MAGMA;
        }
        if (state.getBlock() instanceof FallingBlock
                && state.blocksMotion()
                && state.is(Blocks.GRAVEL)) {
            return CensusSolidDefect.FALLING_NATURAL_STABILIZABLE;
        }
        if (state.blocksMotion() && naturalMaterial(state)) {
            return CensusSolidDefect.NONE;
        }
        return CensusSolidDefect.OTHER;
    }

    private static boolean censusHypotheticalSolidQualifies(
            WorldGenLevel level, BlockPos position) {
        CensusSolidDefect defect = classifyCensusSolidDefect(
                insideOwnerInset(position),
                level.getBlockState(position),
                level.getBlockEntity(position) != null);
        return censusHypotheticalDefectAccepted(defect);
    }

    static boolean censusHypotheticalDefectAccepted(CensusSolidDefect defect) {
        return defect == CensusSolidDefect.NONE
                || defect == CensusSolidDefect.FALLING_NATURAL_STABILIZABLE;
    }

    static boolean censusExposedGravityHazard(
            BlockState state,
            boolean projectedReplacedOrStabilized,
            boolean belowProjectedAir) {
        return belowProjectedAir
                && !projectedReplacedOrStabilized
                && state != null
                && state.getBlock() instanceof FallingBlock;
    }

    private static CensusGravityProjection censusGravityProjection(
            WorldGenLevel level,
            CaveDropTrap.EmbeddedRibbonPlan plan,
            int baseX,
            int baseZ,
            int originLx,
            int originLz) {
        Set<BlockPos> projectedAir = new LinkedHashSet<>();
        Set<BlockPos> projectedReplacements = new LinkedHashSet<>();
        Set<BlockPos> stabilizedRequiredSolids = new LinkedHashSet<>();

        for (CaveDropTrap.Voxel voxel : plan.requiredSolidVoxels()) {
            BlockPos position = ribbonWorldPosition(
                    voxel, baseX, baseZ, originLx, originLz);
            if (classifyCensusSolidDefect(
                    insideOwnerInset(position),
                    level.getBlockState(position),
                    level.getBlockEntity(position) != null)
                    == CensusSolidDefect.FALLING_NATURAL_STABILIZABLE) {
                stabilizedRequiredSolids.add(position.immutable());
            }
        }

        for (CaveDropTrap.Cell cell : plan.powderCells()) {
            int x = baseX + originLx + cell.x();
            int z = baseZ + originLz + cell.z();
            projectedReplacements.add(new BlockPos(x, plan.floorY(), z));
            for (int y = plan.floorY() - 1; y > plan.landingY(); y--) {
                BlockPos air = new BlockPos(x, y, z);
                projectedAir.add(air);
                projectedReplacements.add(air);
            }
            projectedReplacements.add(new BlockPos(x, plan.landingY(), z));
        }
        for (CaveDropTrap.Cell cell : plan.firmRailCells()) {
            int x = baseX + originLx + cell.x();
            int z = baseZ + originLz + cell.z();
            projectedReplacements.add(new BlockPos(x, plan.floorY(), z));
            for (int y = plan.landingY(); y <= plan.landingY() + 1; y++) {
                BlockPos air = new BlockPos(x, y, z);
                projectedAir.add(air);
                projectedReplacements.add(air);
            }
        }
        for (int index = 1; index < plan.stairCells().size() - 1; index++) {
            CaveDropTrap.Cell cell = plan.stairCells().get(index);
            BlockPos standing = new BlockPos(
                    baseX + originLx + cell.x(),
                    plan.landingY() + index,
                    baseZ + originLz + cell.z());
            projectedAir.add(standing);
            projectedReplacements.add(standing);
            BlockPos head = standing.above();
            if (head.getY() <= plan.floorY()) {
                projectedAir.add(head);
                projectedReplacements.add(head);
            }
        }

        Set<BlockPos> hazards = new LinkedHashSet<>();
        for (BlockPos carvedAir : projectedAir) {
            BlockPos above = carvedAir.above();
            boolean replacedOrStabilized = projectedReplacements.contains(above)
                    || stabilizedRequiredSolids.contains(above);
            if (censusExposedGravityHazard(
                    level.getBlockState(above), replacedOrStabilized, true)) {
                hazards.add(above.immutable());
            }
        }
        return new CensusGravityProjection(hazards.size());
    }

    /** Maps a static template voxel into the real candidate-floor coordinate system used by production. */
    private static BlockPos censusTemplatePosition(
            CaveDropTrap.EmbeddedRibbonPlan shape,
            CaveDropTrap.Voxel templateVoxel,
            int candidateFloorY,
            int baseX,
            int baseZ,
            int originLx,
            int originLz) {
        int translatedY = templateVoxel.y() + candidateFloorY - shape.floorY();
        return ribbonWorldPosition(
                new CaveDropTrap.Voxel(templateVoxel.x(), translatedY, templateVoxel.z()),
                baseX, baseZ, originLx, originLz);
    }

    private static EnclosureDiagnosis enclosureDiagnosis(
            CaveDropTrap.EmbeddedRibbonPlan plan,
            WorldGenLevel level,
            int baseX,
            int baseZ,
            int originLx,
            int originLz) {
        int minimumRise = Integer.MAX_VALUE;
        int failingColumns = 0;
        for (CaveDropTrap.Cell cell : plan.enclosureColumns()) {
            int rise = level.getHeight(
                    FEATURE_STAGE_SURFACE_HEIGHTMAP,
                    baseX + originLx + cell.x(), baseZ + originLz + cell.z()) - plan.floorY();
            minimumRise = Math.min(minimumRise, rise);
            if (rise < CaveDropTrap.MIN_CAVE_ENCLOSURE_RISE) {
                failingColumns++;
            }
        }
        return new EnclosureDiagnosis(minimumRise, failingColumns);
    }

    private static boolean censusOnly(Consumer<CensusSnapshot> observer) {
        return observer != null;
    }

    private static TrapGeometry materializeRibbon(
            WorldGenLevel level,
            CaveDropTrap.EmbeddedRibbonPlan plan,
            int baseX,
            int baseZ,
            int originLx,
            int originLz,
            Map<String, Integer> rejected) {
        if (CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW == null) {
            reject(rejected, "trap_powder_content");
            return null;
        }
        BlockState entrancePowder = CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW.defaultBlockState();
        LinkedHashMap<BlockPos, WorldWrite> upper = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, WorldWrite> cushions = new LinkedHashMap<>();
        List<DropCell> powder = new ArrayList<>();
        List<StableFloor> regular = new ArrayList<>();

        for (CaveDropTrap.Cell cell : plan.powderCells()) {
            int lx = originLx + cell.x();
            int lz = originLz + cell.z();
            DropCell drop = new DropCell(
                    lx, lz, plan.floorY(),
                    CaveDropTrap.MIN_CERTIFIED_SOLID_DEPTH,
                    plan.landingY(), CaveDropTrap.MIN_DROP_AIR);
            powder.add(drop);
            BlockPos entrance = new BlockPos(
                    baseX + lx, plan.floorY(), baseZ + lz);
            if (!addNaturalWrite(
                    level, upper, entrance,
                    entrancePowder, AuthoredRole.ENTRANCE_POWDER)) {
                reject(rejected, "template_entrance_write");
                return null;
            }
            for (int y = plan.floorY() - 1; y > plan.landingY(); y--) {
                if (!addNaturalWrite(
                        level, upper,
                        new BlockPos(baseX + lx, y, baseZ + lz),
                        AIR, AuthoredRole.OPENED_SHAFT)) {
                    reject(rejected, "template_shaft_write");
                    return null;
                }
            }
            if (!addNaturalWrite(
                    level, cushions,
                    new BlockPos(baseX + lx, plan.landingY(), baseZ + lz),
                    CUSHION_POWDER_SNOW, AuthoredRole.POWDER_CUSHION)) {
                reject(rejected, "template_cushion_write");
                return null;
            }
        }

        for (CaveDropTrap.Cell cell : plan.firmRailCells()) {
            int lx = originLx + cell.x();
            int lz = originLz + cell.z();
            regular.add(new StableFloor(lx, lz, plan.floorY()));
            if (!addNaturalWrite(
                    level, upper,
                    new BlockPos(baseX + lx, plan.floorY(), baseZ + lz),
                    SNOW_BLOCK, AuthoredRole.FIRM_CAMOUFLAGE)
                    || !addNaturalWrite(
                    level, upper,
                    new BlockPos(baseX + lx, plan.landingY(), baseZ + lz),
                    AIR, AuthoredRole.GALLERY_AIR)
                    || !addNaturalWrite(
                    level, upper,
                    new BlockPos(baseX + lx, plan.landingY() + 1, baseZ + lz),
                    AIR, AuthoredRole.GALLERY_AIR)) {
                reject(rejected, "template_corridor_write");
                return null;
            }
        }

        for (int index = 1; index < plan.stairCells().size() - 1; index++) {
            CaveDropTrap.Cell cell = plan.stairCells().get(index);
            int standingY = plan.landingY() + index;
            BlockPos standing = new BlockPos(
                    baseX + originLx + cell.x(),
                    standingY,
                    baseZ + originLz + cell.z());
            if (!addNaturalWrite(
                    level, upper, standing,
                    AIR, AuthoredRole.GALLERY_AIR)) {
                reject(rejected, "template_stair_write");
                return null;
            }
            BlockPos head = standing.above();
            if (head.getY() <= plan.floorY()
                    && !addNaturalWrite(
                    level, upper, head,
                    AIR, AuthoredRole.GALLERY_AIR)) {
                reject(rejected, "template_stair_write");
                return null;
            }
        }

        List<LandingRoute> landingRoutes = new ArrayList<>();
        for (int index = 0; index < plan.entries().size(); index++) {
            CaveDropTrap.RibbonEntry entry = plan.entries().get(index);
            List<BlockPos> path = plan.dryRoutes().get(index).stream()
                    .map(voxel -> ribbonWorldPosition(
                            voxel, baseX, baseZ, originLx, originLz))
                    .toList();
            BlockPos entrance = ribbonWorldPosition(
                    new CaveDropTrap.Voxel(
                            entry.powder().x(), plan.floorY(), entry.powder().z()),
                    baseX, baseZ, originLx, originLz);
            landingRoutes.add(new LandingRoute(
                    entrance,
                    path.getFirst(),
                    path.getLast(),
                    path));
        }
        LinkedHashMap<BlockPos, WorldWrite> projected = new LinkedHashMap<>(upper);
        projected.putAll(cushions);
        if (!combinedDryRoutesRemainSupported(level, projected, landingRoutes)) {
            reject(rejected, "template_combined_route_projection");
            return null;
        }

        CaveDropTrap.InsetCarpetPlan carpet =
                new CaveDropTrap.InsetCarpetPlan(
                        plan.powderCells().stream()
                                .map(cell -> new CaveDropTrap.Cell(
                                        originLx + cell.x(), originLz + cell.z()))
                                .toList(),
                        plan.firmRailCells().stream()
                                .map(cell -> new CaveDropTrap.Cell(
                                        originLx + cell.x(), originLz + cell.z()))
                                .toList(),
                        plan.firstApproachBank().stream()
                                .map(cell -> new CaveDropTrap.Cell(
                                        originLx + cell.x(), originLz + cell.z()))
                                .toList(),
                        plan.secondApproachBank().stream()
                                .map(cell -> new CaveDropTrap.Cell(
                                        originLx + cell.x(), originLz + cell.z()))
                                .toList(),
                        plan.approachAxis());
        List<BlockPos> firstNaturalContinuation = naturalFloors(
                plan.firstNaturalContinuation(), plan.floorY(),
                baseX, baseZ, originLx, originLz);
        List<BlockPos> secondNaturalContinuation = naturalFloors(
                plan.secondNaturalContinuation(), plan.floorY(),
                baseX, baseZ, originLx, originLz);
        List<CaveDropTrap.Cell> upperNaturalReturnCells = new ArrayList<>();
        upperNaturalReturnCells.add(plan.stairCells().getLast());
        upperNaturalReturnCells.addAll(plan.upperReturnPathCells());
        List<BlockPos> upperNaturalReturn = naturalFloors(
                upperNaturalReturnCells, plan.floorY(),
                baseX, baseZ, originLx, originLz);
        CaveDropTrap.ProspectorLayout prospector = plan.prospectorLayout();
        BlockPos prospectorCushion = ribbonWorldPosition(
                new CaveDropTrap.Voxel(
                        prospector.cushion().x(), plan.landingY(), prospector.cushion().z()),
                baseX, baseZ, originLx, originLz);
        BlockPos prospectorFoot = ribbonWorldPosition(
                new CaveDropTrap.Voxel(
                        prospector.foot().x(), plan.landingY(), prospector.foot().z()),
                baseX, baseZ, originLx, originLz);
        BlockPos prospectorHead = ribbonWorldPosition(
                new CaveDropTrap.Voxel(
                        prospector.head().x(), plan.landingY(), prospector.head().z()),
                baseX, baseZ, originLx, originLz);
        BlockPos prospectorChest = ribbonWorldPosition(
                new CaveDropTrap.Voxel(
                        prospector.chest().x(), plan.landingY(), prospector.chest().z()),
                baseX, baseZ, originLx, originLz);
        BlockPos prospectorFront = ribbonWorldPosition(
                new CaveDropTrap.Voxel(
                        prospector.front().x(), plan.landingY(), prospector.front().z()),
                baseX, baseZ, originLx, originLz);
        Direction entryDirection = horizontalDirection(prospectorCushion, prospectorFoot);
        Direction bodyFacing = horizontalDirection(prospectorFoot, prospectorHead);
        Direction chestFacing = horizontalDirection(prospectorChest, prospectorFront);
        if (entryDirection == null || bodyFacing == null || chestFacing == null
                || entryDirection.getAxis() == bodyFacing.getAxis()
                || bodyFacing.getAxis() == chestFacing.getAxis()) {
            reject(rejected, "template_prospector_layout");
            return null;
        }
        ProspectorPlacement prospectorPlacement = new ProspectorPlacement(
                prospectorCushion,
                prospectorFoot,
                prospectorHead,
                prospectorChest,
                prospectorFront,
                entryDirection,
                bodyFacing,
                chestFacing);

        for (DropCell cell : powder) {
            if (!insideOwnerInset(new BlockPos(
                    baseX + cell.lx(), cell.floorY(), baseZ + cell.lz()))) {
                reject(rejected, "template_owner_inset");
                return null;
            }
        }

        DropCell anchorCell = powder.stream()
                .min(Comparator.comparingInt(DropCell::lx)
                        .thenComparingInt(DropCell::lz)
                        .thenComparingInt(DropCell::floorY))
                .orElseThrow();
        BlockPos anchor = new BlockPos(
                baseX + anchorCell.lx(), anchorCell.floorY(), baseZ + anchorCell.lz());
        return new TrapGeometry(
                carpet, List.copyOf(powder), List.copyOf(regular),
                firstNaturalContinuation, secondNaturalContinuation, upperNaturalReturn,
                List.copyOf(upper.values()), List.copyOf(cushions.values()),
                List.copyOf(landingRoutes), prospectorPlacement,
                anchor, CaveDropTrap.MIN_DROP_AIR);
    }

    private static BlockPos ribbonWorldPosition(
            CaveDropTrap.Voxel voxel,
            int baseX,
            int baseZ,
            int originLx,
            int originLz) {
        return new BlockPos(
                baseX + originLx + voxel.x(),
                voxel.y(),
                baseZ + originLz + voxel.z());
    }

    private static ScenePlan ordinaryScene(TrapGeometry geometry) {
        List<WorldWrite> writes = new ArrayList<>(geometry.upperWrites());
        writes.addAll(geometry.cushionWrites());
        return scene(
                geometry, CaveDropTrap.RewardKind.ORDINARY, writes,
                geometry.fallDepth(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, List.of(), List.of(), 0L);
    }

    private static ScenePlan floodedScene(
            WorldGenLevel level, TrapGeometry geometry,
            int baseX, int baseZ, Map<String, Integer> rejected) {
        int lowestEntranceY = geometry.powder().stream()
                .mapToInt(DropCell::floorY).min().orElseThrow();
        int highestEntranceY = geometry.powder().stream()
                .mapToInt(DropCell::floorY).max().orElseThrow();
        CaveDropTrap.DeepFloodedLanding deep = CaveDropTrap.planDeepFloodedLanding(
                lowestEntranceY, highestEntranceY, level.getMinY());
        if (deep == null) {
            reject(rejected, "flooded_depth_plan");
            return null;
        }
        int poolSurfaceY = deep.surfaceY();
        Set<CaveDropTrap.Voxel> protectedNaturalEvidence =
                protectedNaturalEvidence(geometry);
        if (protectedNaturalEvidence.isEmpty()) {
            reject(rejected, "flooded_natural_evidence");
            return null;
        }

        // A flooded reward is independently preflighted from the original cave floor. It keeps only
        // the visible upper carpet/bookends, then certifies and authors its own 32..96-block shafts.
        LinkedHashMap<BlockPos, WorldWrite> writes = new LinkedHashMap<>();
        for (WorldWrite write : geometry.upperWrites()) {
            if (write.role() == AuthoredRole.ENTRANCE_POWDER
                    || write.role() == AuthoredRole.FIRM_CAMOUFLAGE) {
                writes.put(write.position(), write);
            }
        }

        Set<CaveDropTrap.Cell> entranceFootprint = new LinkedHashSet<>();
        for (DropCell cell : geometry.powder()) {
            entranceFootprint.add(new CaveDropTrap.Cell(cell.lx(), cell.lz()));
            int fall = cell.floorY() - poolSurfaceY;
            if (fall < 32 || fall > MAX_DROP_SCAN) {
                reject(rejected, "flooded_fall");
                return null;
            }
            for (int y = cell.floorY() - 1; y > poolSurfaceY; y--) {
                if (!addNaturalWrite(
                        level, writes,
                        new BlockPos(baseX + cell.lx(), y, baseZ + cell.lz()),
                        AIR, AuthoredRole.OPENED_SHAFT)) {
                    reject(rejected, "flooded_shaft");
                    return null;
                }
            }
        }

        Set<CaveDropTrap.Cell> poolFootprint = dilateLocal(
                entranceFootprint, FLOODED_POOL_DILATION, 2, 13);
        Set<CaveDropTrap.Cell> galleryFootprint = dilateLocal(
                entranceFootprint, FLOODED_GALLERY_DILATION, 1, 14);
        if (poolFootprint == null || galleryFootprint == null
                || !CaveDropTrap.floodedGalleryQualifies(
                        entranceFootprint.size(), galleryFootprint.size())) {
            reject(rejected, "flooded_gallery_size");
            return null;
        }
        Set<CaveDropTrap.Cell> shoreFootprint = new LinkedHashSet<>(galleryFootprint);
        shoreFootprint.removeAll(poolFootprint);
        if (shoreFootprint.size() < 8) {
            reject(rejected, "flooded_shore_size");
            return null;
        }

        Set<BlockPos> protectedCells = new HashSet<>();
        List<BlockPos> poolWater = new ArrayList<>();
        List<BlockPos> galleryPositions = new ArrayList<>();
        List<BlockPos> shorePositions = new ArrayList<>();
        for (CaveDropTrap.Cell local : galleryFootprint) {
            int wx = baseX + local.x();
            int wz = baseZ + local.z();
            for (int height = 1; height <= FLOODED_GALLERY_CEILING; height++) {
                BlockPos cavity = new BlockPos(wx, poolSurfaceY + height, wz);
                if (!addGalleryAirWrite(level, writes, cavity)) {
                    reject(rejected, "flooded_gallery_carve");
                    return null;
                }
                protectedCells.add(cavity);
            }
            galleryPositions.add(new BlockPos(wx, poolSurfaceY + 1, wz));
        }
        for (CaveDropTrap.Cell local : shoreFootprint) {
            BlockPos floor = new BlockPos(baseX + local.x(), poolSurfaceY, baseZ + local.z());
            if (!supportedNaturalMass(level, floor, FLOODED_POOL_DEPTH + 1)) {
                reject(rejected, "flooded_shore_support");
                return null;
            }
            protectedCells.add(floor);
            shorePositions.add(floor.above());
        }
        for (CaveDropTrap.Cell local : poolFootprint) {
            int wx = baseX + local.x();
            int wz = baseZ + local.z();
            for (int depth = 0; depth < FLOODED_POOL_DEPTH; depth++) {
                BlockPos water = new BlockPos(wx, poolSurfaceY - depth, wz);
                if (!addGalleryWaterWrite(level, writes, water)) {
                    reject(rejected, "flooded_pool_write");
                    return null;
                }
                protectedCells.add(water);
                poolWater.add(water);
            }
            BlockPos poolFloor = new BlockPos(wx, poolSurfaceY - FLOODED_POOL_DEPTH, wz);
            if (!safeNaturalSupport(level, poolFloor, level.getBlockState(poolFloor))) {
                reject(rejected, "flooded_floor");
                return null;
            }
            protectedCells.add(poolFloor);
        }
        if (!poolHasSolidBoundary(level, poolFootprint, shoreFootprint, poolSurfaceY, baseX, baseZ)) {
            reject(rejected, "flooded_pool_boundary");
            return null;
        }

        FloodedEscape escape = planFloodedEscape(
                level, writes, geometry, galleryFootprint, shoreFootprint,
                poolSurfaceY, baseX, baseZ, protectedNaturalEvidence);
        if (escape == null) {
            reject(rejected, "flooded_authored_exit");
            return null;
        }
        writes = writesByPosition(escape.writes());
        if (writes.keySet().stream()
                .map(CaveDropTrapFeature::voxel)
                .anyMatch(protectedNaturalEvidence::contains)) {
            reject(rejected, "flooded_natural_evidence_overlap");
            return null;
        }
        List<List<BlockPos>> shorePaths = new ArrayList<>();
        for (DropCell entry : geometry.powder()) {
            List<BlockPos> lowerPath = floodedPath(
                    new CaveDropTrap.Cell(entry.lx(), entry.lz()),
                    poolFootprint, shoreFootprint, escape.lowerStart(),
                    poolSurfaceY, baseX, baseZ);
            if (lowerPath == null) {
                reject(rejected, "flooded_entry_route");
                return null;
            }
            List<BlockPos> completePath = new ArrayList<>(lowerPath);
            completePath.addAll(escape.path().subList(1, escape.path().size()));
            shorePaths.add(List.copyOf(completePath));
        }
        List<BlockPos> exitPath = escape.path();

        List<BlockPos> oreTargets = exposedOreTargets(
                level, writes, protectedCells);
        if (oreTargets.size() < FLOODED_ORE_BUDGET) {
            reject(rejected, "flooded_ore_budget");
            return null;
        }
        for (int index = 0; index < FLOODED_ORE_BUDGET; index++) {
            BlockState ore = switch (index) {
                case 0, 1 -> Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
                case 2, 3, 4, 5 -> Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
                case 6, 7, 8, 9, 10 -> Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
                default -> Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
            };
            if (!putWrite(
                    level, writes, oreTargets.get(index), ore, AuthoredRole.ORE_REWARD)) {
                reject(rejected, "flooded_ore_write");
                return null;
            }
        }
        if (writes.keySet().stream()
                .map(CaveDropTrapFeature::voxel)
                .anyMatch(protectedNaturalEvidence::contains)) {
            reject(rejected, "flooded_natural_evidence_overlap");
            return null;
        }

        CaveDropTrap.RewardPlan reward = CaveDropTrap.planReward(
                14,
                geometry.powder().getFirst().landingAirY(),
                poolSurfaceY,
                FLOODED_POOL_DEPTH,
                FLOODED_ORE_BUDGET);
        if (reward == null || reward.kind() != CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY) {
            return null;
        }
        int fallDepth = geometry.powder().stream()
                .mapToInt(cell -> cell.floorY() - poolSurfaceY)
                .min().orElseThrow();
        return scene(
                geometry, reward.kind(), List.copyOf(writes.values()),
                fallDepth, List.copyOf(poolWater), List.copyOf(shorePositions),
                List.copyOf(galleryPositions), List.copyOf(shorePaths), exitPath,
                null, null, null, null, List.of(), List.of(), 0L);
    }

    private static ScenePlan prospectorScene(
            WorldGenLevel level, TrapGeometry geometry, ScenePlan ordinary,
            int baseX, int baseZ, Map<String, Integer> rejected) {
        CaveDropTrap.RewardPlan reward = CaveDropTrap.planReward(
                15,
                geometry.powder().getFirst().landingAirY(),
                -1,
                0,
                0);
        if (reward == null
                || reward.kind() != CaveDropTrap.RewardKind.LOST_PROSPECTOR
                || reward.valuableLootBudget() != LOST_PROSPECTOR_LOOT_BUDGET
                || CollapsedExplorerBlocks.COLLAPSED_EXPLORER == null) {
            reject(rejected, "prospector_content");
            return null;
        }

        Set<BlockPos> cushion = new HashSet<>();
        geometry.cushionWrites().forEach(write -> cushion.add(write.position()));
        Shelf shelf = findProspectorShelf(
                level, geometry, ordinary.writes(), baseX, baseZ, cushion);
        if (shelf == null) {
            reject(rejected, "prospector_shelf");
            return null;
        }

        LinkedHashMap<BlockPos, WorldWrite> writes = writesByPosition(ordinary.writes());
        BlockState chest = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, shelf.chestFacing());
        BlockState remainsFoot = CollapsedExplorerBlocks.COLLAPSED_EXPLORER.defaultBlockState()
                .setValue(CollapsedExplorerBlock.FACING, shelf.bodyFacing())
                .setValue(CollapsedExplorerBlock.PART, BedPart.FOOT);
        BlockState remainsHead = CollapsedExplorerBlocks.COLLAPSED_EXPLORER.defaultBlockState()
                .setValue(CollapsedExplorerBlock.FACING, shelf.bodyFacing())
                .setValue(CollapsedExplorerBlock.PART, BedPart.HEAD);
        if (!replaceRequiredGalleryAirWrite(
                    writes, shelf.chest(), chest, AuthoredRole.TREASURE_CHEST)
                || !replaceRequiredGalleryAirWrite(
                        writes, shelf.foot(), remainsFoot,
                        AuthoredRole.COLLAPSED_EXPLORER_FOOT)
                || !replaceRequiredGalleryAirWrite(
                        writes, shelf.head(), remainsHead,
                        AuthoredRole.COLLAPSED_EXPLORER_HEAD)) {
            reject(rejected, "prospector_write");
            return null;
        }
        long lootSeed = stableLootSeed(level.getSeed(), geometry.anchor());
        return scene(
                geometry, reward.kind(), List.copyOf(writes.values()),
                geometry.fallDepth(), List.of(), List.of(), List.of(), List.of(), List.of(),
                shelf.chest(), shelf.front(), shelf.foot(), shelf.head(),
                shelf.entryPaths(), shelf.frontToExitPath(), lootSeed);
    }

    private record Shelf(
            BlockPos foot,
            BlockPos head,
            BlockPos chest,
            BlockPos front,
            Direction bodyFacing,
            Direction chestFacing,
            List<List<BlockPos>> entryPaths,
            List<BlockPos> frontToExitPath) {
    }

    private record FloodedEscape(
            CaveDropTrap.Cell lowerStart,
            List<BlockPos> path,
            List<WorldWrite> writes) {
    }

    private static Shelf findProspectorShelf(
            WorldGenLevel level,
            TrapGeometry geometry,
            List<WorldWrite> sceneWrites,
            int baseX,
            int baseZ,
            Set<BlockPos> cushion) {
        Map<BlockPos, WorldWrite> projected = writesByPosition(sceneWrites);
        ProspectorPlacement placement = geometry.prospectorPlacement();
        Set<BlockPos> distinctPositions = new HashSet<>(List.of(
                placement.cushion(), placement.foot(), placement.head(),
                placement.chest(), placement.front()));
        if (distinctPositions.size() != 5
                || !cushion.contains(placement.cushion())
                || cushion.contains(placement.foot())
                || cushion.contains(placement.head())
                || cushion.contains(placement.chest())
                || cushion.contains(placement.front())
                || !plannedGalleryStanding(level, projected, placement.foot())
                || !plannedGalleryStanding(level, projected, placement.head())
                || !plannedGalleryStanding(level, projected, placement.chest())
                || !plannedGalleryStanding(level, projected, placement.front())
                || adjacentChestOrUnsafe(level, placement.foot())
                || adjacentChestOrUnsafe(level, placement.head())
                || adjacentChestOrUnsafe(level, placement.chest())
                || adjacentChestOrUnsafe(level, placement.front())) {
            return null;
        }

        Set<BlockPos> authoredRouteStanding = new HashSet<>();
        for (WorldWrite write : projected.values()) {
            if (write.role() == AuthoredRole.GALLERY_AIR
                    && write.desired().isAir()
                    && dryWalkableStanding(level, projected, write.position())) {
                authoredRouteStanding.add(write.position());
            }
        }
        geometry.landingRoutes().forEach(
                route -> authoredRouteStanding.addAll(route.path()));
        authoredRouteStanding.addAll(cushion);
        authoredRouteStanding.add(placement.front());

        Set<BlockPos> bodyAndChest = Set.of(
                placement.foot(), placement.head(), placement.chest());
        List<List<BlockPos>> entryPaths = new ArrayList<>();
        for (LandingRoute entry : geometry.landingRoutes()) {
            List<BlockPos> path = findProjectedPath(
                    level, projected, entry.landing(), placement.front(),
                    bodyAndChest, cushion, authoredRouteStanding,
                    baseX, baseZ, LOWER_ROUTE_RADIUS, LOWER_ROUTE_VERTICAL_RANGE);
            if (path == null) {
                return null;
            }
            entryPaths.add(path);
        }

        Set<BlockPos> exitBlocked = new HashSet<>(cushion);
        exitBlocked.addAll(bodyAndChest);
        List<BlockPos> frontToExit = findProjectedPath(
                level, projected, placement.front(),
                geometry.landingRoutes().getFirst().dryExit(),
                exitBlocked, Set.of(), authoredRouteStanding,
                baseX, baseZ, LOWER_ROUTE_RADIUS * 2, MAX_AUTHORED_DROP + 2);
        if (frontToExit == null) {
            return null;
        }
        return new Shelf(
                placement.foot(), placement.head(), placement.chest(), placement.front(),
                placement.bodyFacing(), placement.chestFacing(),
                List.copyOf(entryPaths), frontToExit);
    }

    private static boolean plannedGalleryStanding(
            WorldGenLevel level, Map<BlockPos, WorldWrite> writes, BlockPos standing) {
        WorldWrite feet = writes.get(standing);
        WorldWrite head = writes.get(standing.above());
        return feet != null
                && head != null
                && feet.role() == AuthoredRole.GALLERY_AIR
                && head.role() == AuthoredRole.GALLERY_AIR
                && feet.desired().isAir()
                && head.desired().isAir()
                && feet.priorBlockEntityAbsent()
                && head.priorBlockEntityAbsent()
                && insideOwnerInset(standing)
                && supportedNaturalMass(
                        level, writes, standing.below(), STABLE_MASS_DEPTH);
    }

    private static boolean adjacentChestOrUnsafe(WorldGenLevel level, BlockPos position) {
        if (!insideOwnerInset(position)) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = position.relative(direction);
            BlockState state = level.getBlockState(adjacent);
            if (state.is(Blocks.CHEST) || !state.getFluidState().isEmpty()
                    || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)
                    || level.getBlockEntity(adjacent) != null) {
                return true;
            }
        }
        return false;
    }

    private static FloodedEscape planFloodedEscape(
            WorldGenLevel level,
            Map<BlockPos, WorldWrite> baseWrites,
            TrapGeometry geometry,
            Set<CaveDropTrap.Cell> gallery,
            Set<CaveDropTrap.Cell> shore,
            int waterY,
            int baseX,
            int baseZ,
            Set<CaveDropTrap.Voxel> protectedNaturalEvidence) {
        int approachFloorY = geometry.regular().getFirst().floorY();
        int lowerStandingY = waterY + 1;
        int riseSteps = approachFloorY + 1 - lowerStandingY;
        if (riseSteps < CaveDropTrap.MIN_DROP_AIR || riseSteps > MAX_DROP_SCAN) {
            return null;
        }

        Set<CaveDropTrap.Cell> blocked = new HashSet<>(gallery);
        geometry.powder().forEach(cell ->
                blocked.add(new CaveDropTrap.Cell(cell.lx(), cell.lz())));
        geometry.regular().forEach(cell ->
                blocked.add(new CaveDropTrap.Cell(cell.lx(), cell.lz())));
        List<CaveDropTrap.Cell> starts = shore.stream()
                .sorted(Comparator.comparingInt(CaveDropTrap.Cell::x)
                        .thenComparingInt(CaveDropTrap.Cell::z))
                .toList();
        List<CaveDropTrap.Cell> ends = new ArrayList<>();
        ends.addAll(geometry.carpet().firstApproachBank());
        ends.addAll(geometry.carpet().secondApproachBank());
        ends = ends.stream().distinct()
                .sorted(Comparator.comparingInt(CaveDropTrap.Cell::x)
                        .thenComparingInt(CaveDropTrap.Cell::z))
                .toList();

        for (CaveDropTrap.Cell start : starts) {
            for (CaveDropTrap.Cell end : ends) {
                Set<CaveDropTrap.Cell> pathBlocked = new HashSet<>(blocked);
                pathBlocked.remove(start);
                pathBlocked.remove(end);
                List<CaveDropTrap.Cell> local =
                        exactEscapePath(
                                start, end, riseSteps, pathBlocked,
                                lowerStandingY, protectedNaturalEvidence);
                if (local == null) {
                    continue;
                }
                LinkedHashMap<BlockPos, WorldWrite> writes =
                        new LinkedHashMap<>(baseWrites);
                List<BlockPos> path = new ArrayList<>();
                boolean valid = true;
                for (int index = 0; index < local.size() - 1; index++) {
                    CaveDropTrap.Cell cell = local.get(index);
                    BlockPos standing = new BlockPos(
                            baseX + cell.x(), lowerStandingY + index, baseZ + cell.z());
                    WorldWrite supportWrite = writes.get(standing.below());
                    if ((supportWrite != null && !supportWrite.desired().blocksMotion())
                            || !safeNaturalSupport(
                                    level, standing.below(),
                                    level.getBlockState(standing.below()))
                            || !addSafeAirWrite(
                                    level, writes, standing, AuthoredRole.GALLERY_AIR)
                            || !addSafeAirWrite(
                                    level, writes, standing.above(), AuthoredRole.GALLERY_AIR)) {
                        valid = false;
                        break;
                    }
                    path.add(standing);
                }
                BlockPos dryExit = new BlockPos(
                        baseX + end.x(), approachFloorY + 1, baseZ + end.z());
                if (!valid
                        || !twoAir(level, dryExit.getX(), dryExit.getY(), dryExit.getZ())
                        || !supportedNaturalMass(
                                level, dryExit.below(), STABLE_MASS_DEPTH)) {
                    continue;
                }
                path.add(dryExit);
                return new FloodedEscape(
                        start, List.copyOf(path), List.copyOf(writes.values()));
            }
        }
        return null;
    }

    private static Set<CaveDropTrap.Cell> dilateLocal(
            Set<CaveDropTrap.Cell> source, int distance, int minimum, int maximum) {
        Set<CaveDropTrap.Cell> result = new LinkedHashSet<>(source);
        for (int pass = 0; pass < distance; pass++) {
            Set<CaveDropTrap.Cell> expanded = new LinkedHashSet<>(result);
            for (CaveDropTrap.Cell cell : result) {
                for (Direction direction : HORIZONTAL) {
                    int x = cell.x() + direction.getStepX();
                    int z = cell.z() + direction.getStepZ();
                    if (x < minimum || x > maximum || z < minimum || z > maximum) {
                        return null;
                    }
                    expanded.add(new CaveDropTrap.Cell(x, z));
                }
            }
            result = expanded;
        }
        return result;
    }

    private static boolean poolHasSolidBoundary(
            WorldGenLevel level,
            Set<CaveDropTrap.Cell> pool,
            Set<CaveDropTrap.Cell> shore,
            int waterY,
            int baseX,
            int baseZ) {
        for (CaveDropTrap.Cell cell : pool) {
            for (Direction direction : HORIZONTAL) {
                CaveDropTrap.Cell neighbour = new CaveDropTrap.Cell(
                        cell.x() + direction.getStepX(), cell.z() + direction.getStepZ());
                if (pool.contains(neighbour)) {
                    continue;
                }
                if (!shore.contains(neighbour)) {
                    return false;
                }
                for (int depth = 0; depth < FLOODED_POOL_DEPTH; depth++) {
                    BlockPos wall = new BlockPos(
                            baseX + neighbour.x(), waterY - depth, baseZ + neighbour.z());
                    if (!safeNaturalSupport(level, wall, level.getBlockState(wall))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static CaveDropTrap.Cell findFloodedExit(
            WorldGenLevel level,
            Set<CaveDropTrap.Cell> gallery,
            Set<CaveDropTrap.Cell> shore,
            int waterY,
            int baseX,
            int baseZ) {
        return shore.stream()
                .sorted(Comparator.comparingInt(CaveDropTrap.Cell::x)
                        .thenComparingInt(CaveDropTrap.Cell::z))
                .flatMap(cell -> HORIZONTAL.stream().map(direction -> new CaveDropTrap.Cell(
                        cell.x() + direction.getStepX(), cell.z() + direction.getStepZ())))
                .filter(cell -> cell.x() >= 1 && cell.x() <= 14
                        && cell.z() >= 1 && cell.z() <= 14
                        && !gallery.contains(cell))
                .filter(cell -> dryWalkableStanding(
                        level,
                        new BlockPos(baseX + cell.x(), waterY + 1, baseZ + cell.z())))
                .findFirst().orElse(null);
    }

    private static List<BlockPos> floodedPath(
            CaveDropTrap.Cell start,
            Set<CaveDropTrap.Cell> pool,
            Set<CaveDropTrap.Cell> shore,
            CaveDropTrap.Cell exit,
            int waterY,
            int baseX,
            int baseZ) {
        Set<CaveDropTrap.Cell> allowed = new HashSet<>(pool);
        allowed.addAll(shore);
        allowed.add(exit);
        Map<CaveDropTrap.Cell, CaveDropTrap.Cell> parents = new LinkedHashMap<>();
        ArrayDeque<CaveDropTrap.Cell> queue = new ArrayDeque<>();
        parents.put(start, null);
        queue.add(start);
        while (!queue.isEmpty() && !parents.containsKey(exit)) {
            CaveDropTrap.Cell current = queue.removeFirst();
            for (Direction direction : HORIZONTAL) {
                CaveDropTrap.Cell next = new CaveDropTrap.Cell(
                        current.x() + direction.getStepX(), current.z() + direction.getStepZ());
                if (allowed.contains(next) && !parents.containsKey(next)) {
                    parents.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        if (!parents.containsKey(exit)) {
            return null;
        }
        ArrayDeque<CaveDropTrap.Cell> reverse = new ArrayDeque<>();
        for (CaveDropTrap.Cell cursor = exit; cursor != null; cursor = parents.get(cursor)) {
            reverse.addFirst(cursor);
        }
        List<BlockPos> path = new ArrayList<>();
        for (CaveDropTrap.Cell cell : reverse) {
            int y = pool.contains(cell) ? waterY : waterY + 1;
            path.add(new BlockPos(baseX + cell.x(), y, baseZ + cell.z()));
        }
        return List.copyOf(path);
    }

    private static List<BlockPos> exposedOreTargets(
            WorldGenLevel level,
            Map<BlockPos, WorldWrite> galleryWrites,
            Set<BlockPos> protectedCells) {
        Set<BlockPos> exposure = new HashSet<>();
        for (BlockPos protectedCell : protectedCells) {
            WorldWrite write = galleryWrites.get(protectedCell);
            if (write != null
                    && (write.role() == AuthoredRole.GALLERY_AIR
                            || write.role() == AuthoredRole.GALLERY_WATER)) {
                exposure.add(protectedCell);
            }
        }
        Set<BlockPos> unique = new HashSet<>();
        for (BlockPos cavity : exposure) {
            for (Direction direction : Direction.values()) {
                BlockPos target = cavity.relative(direction);
                BlockState state = level.getBlockState(target);
                if (insideOwnerInset(target)
                        && !protectedCells.contains(target)
                        && !galleryWrites.containsKey(target)
                        && state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                        && !isOre(state)
                        && safeNaturalTarget(level, target, state)) {
                    unique.add(target.immutable());
                }
            }
        }
        return unique.stream()
                .sorted(Comparator.comparingInt((BlockPos position) -> position.getX())
                        .thenComparingInt(position -> position.getY())
                        .thenComparingInt(position -> position.getZ()))
                .toList();
    }

    private record ApplyResult(boolean success, boolean rollbackVerified) {
    }

    private static ApplyResult applyScene(
            WorldGenLevel level, ScenePlan scene, int baseX, int baseZ) {
        if (!writesStillMatch(level, scene.writes(), baseX, baseZ)) {
            return new ApplyResult(false, true);
        }
        List<CaveDropTrap.AtomicStateChange<BlockState>> changes = scene.writes().stream()
                .map(write -> new CaveDropTrap.AtomicStateChange<>(
                        write.expected(), write.desired()))
                .toList();
        CaveDropTrap.AtomicResult transaction = CaveDropTrap.applyAtomically(
                changes,
                new CaveDropTrap.AtomicStateAdapter<>() {
                    @Override
                    public BlockState read(int index) {
                        return level.getBlockState(scene.writes().get(index).position());
                    }

                    @Override
                    public boolean write(int index, BlockState state) {
                        return level.setBlock(
                                scene.writes().get(index).position(), state, SET_BLOCK_FLAGS);
                    }
                },
                writeFailureInjector,
                () -> finalizeScene(level, scene));
        boolean rollbackVerified = transaction.rollbackVerified();
        if (!transaction.success() && rollbackVerified) {
            for (WorldWrite write : scene.writes()) {
                rollbackVerified &= level.getBlockEntity(write.position()) == null;
            }
        }
        return new ApplyResult(transaction.success(), rollbackVerified);
    }

    private static boolean finalizeScene(WorldGenLevel level, ScenePlan scene) {
        if (scene.chestPosition() == null) {
            return true;
        }
        if (!(level.getBlockEntity(scene.chestPosition()) instanceof ChestBlockEntity chest)) {
            return false;
        }
        chest.setLootTable(LOST_PROSPECTOR_LOOT);
        chest.setLootTableSeed(scene.lootSeed());
        return true;
    }

    private static boolean writesStillMatch(
            WorldGenLevel level, List<WorldWrite> writes, int baseX, int baseZ) {
        Set<BlockPos> unique = new HashSet<>();
        for (WorldWrite write : writes) {
            BlockPos pos = write.position();
            if (!insideOwnerChunk(pos, baseX, baseZ)
                    || !level.ensureCanWrite(pos)
                    || !unique.add(pos)
                    || !level.getBlockState(pos).equals(write.expected())
                    || level.getBlockEntity(pos) != null) {
                return false;
            }
        }
        return true;
    }

    private static boolean postWritesMatch(WorldGenLevel level, List<WorldWrite> writes) {
        for (WorldWrite write : writes) {
            if (!level.getBlockState(write.position()).equals(write.desired())) {
                return false;
            }
        }
        return true;
    }

    private static ScenePlan scene(
            TrapGeometry geometry, CaveDropTrap.RewardKind kind, List<WorldWrite> writes,
            int fallDepth,
            List<BlockPos> waterPositions,
            List<BlockPos> shorePositions,
            List<BlockPos> galleryPositions,
            List<List<BlockPos>> shorePaths,
            List<BlockPos> floodedExitPath,
            BlockPos chest,
            BlockPos chestFront,
            BlockPos remainsFoot,
            BlockPos remainsHead,
            List<List<BlockPos>> chestPaths,
            List<BlockPos> chestToExitPath,
            long lootSeed) {
        return new ScenePlan(
                kind,
                List.copyOf(writes),
                geometry.anchor(),
                geometry.carpet().approachAxis(),
                geometry.powder().size(),
                geometry.regular().size(),
                fallDepth,
                List.copyOf(waterPositions),
                List.copyOf(shorePositions),
                List.copyOf(galleryPositions),
                immutablePathLists(shorePaths),
                List.copyOf(floodedExitPath),
                chest,
                chestFront,
                remainsFoot,
                remainsHead,
                immutablePathLists(chestPaths),
                List.copyOf(chestToExitPath),
                lootSeed);
    }

    private static void emitAudit(
            WorldGenLevel level,
            Consumer<AuditEvent> callback, TrapGeometry geometry, ScenePlan scene,
            int rewardRoll, String fallbackReason) {
        try {
            DropCell anchorCell = geometry.powder().stream()
                    .min(Comparator.comparingInt(DropCell::lx)
                            .thenComparingInt(DropCell::lz)
                            .thenComparingInt(DropCell::floorY))
                    .orElseThrow();
            int baseX = geometry.anchor().getX() - anchorCell.lx();
            int baseZ = geometry.anchor().getZ() - anchorCell.lz();
            List<BlockPos> powder = geometry.powder().stream()
                    .map(cell -> new BlockPos(baseX + cell.lx(), cell.floorY(), baseZ + cell.lz()))
                    .toList();
            List<BlockPos> regular = geometry.regular().stream()
                    .map(cell -> new BlockPos(baseX + cell.lx(), cell.floorY(), baseZ + cell.lz()))
                    .toList();
            List<BlockPos> firstBank = geometry.carpet().firstApproachBank().stream()
                    .map(cell -> stableWorldPosition(geometry, cell, baseX, baseZ))
                    .toList();
            List<BlockPos> secondBank = geometry.carpet().secondApproachBank().stream()
                    .map(cell -> stableWorldPosition(geometry, cell, baseX, baseZ))
                    .toList();
            List<BlockPos> ores = scene.writes().stream()
                    .filter(write -> isOre(write.desired()))
                    .map(WorldWrite::position)
                    .toList();
            List<EntryEvidence> entries = new ArrayList<>();
            for (int index = 0; index < geometry.landingRoutes().size(); index++) {
                LandingRoute route = geometry.landingRoutes().get(index);
                List<BlockPos> shorePath = index < scene.shorePaths().size()
                        ? scene.shorePaths().get(index) : List.of();
                List<BlockPos> chestPath = index < scene.chestPaths().size()
                        ? scene.chestPaths().get(index) : List.of();
                BlockPos actualLanding = scene.waterPositions().isEmpty()
                        ? route.landing()
                        : highestWaterAt(scene.waterPositions(), route.entranceTop());
                BlockPos dryExit = shorePath.isEmpty() ? route.dryExit() : shorePath.getLast();
                BlockPos shore = shorePath.stream()
                        .filter(scene.shorePositions()::contains)
                        .findFirst().orElse(null);
                List<BlockPos> shaftPositions =
                        orderedShaftPositions(route.entranceTop(), actualLanding);
                entries.add(new EntryEvidence(
                        route.entranceTop(), actualLanding, dryExit,
                        shorePath.isEmpty() ? route.path() : shorePath,
                        shore, shorePath, chestPath, shaftPositions));
            }
            int landingMin;
            int landingMax;
            int fallMin;
            int fallMax;
            if (!scene.waterPositions().isEmpty()) {
                landingMin = scene.waterPositions().stream()
                        .mapToInt((BlockPos pos) -> pos.getY()).max().orElseThrow();
                landingMax = landingMin;
                fallMin = geometry.powder().stream()
                        .mapToInt(cell -> cell.floorY() - landingMax).min().orElseThrow();
                fallMax = geometry.powder().stream()
                        .mapToInt(cell -> cell.floorY() - landingMin).max().orElseThrow();
            } else {
                landingMin = geometry.powder().stream()
                        .mapToInt(DropCell::landingAirY).min().orElseThrow();
                landingMax = geometry.powder().stream()
                        .mapToInt(DropCell::landingAirY).max().orElseThrow();
                fallMin = geometry.powder().stream().mapToInt(DropCell::dropDepth).min().orElseThrow();
                fallMax = geometry.powder().stream().mapToInt(DropCell::dropDepth).max().orElseThrow();
            }
            BlockPos sceneMin = scene.writes().stream().map(WorldWrite::position)
                    .reduce((a, b) -> new BlockPos(
                            Math.min(a.getX(), b.getX()),
                            Math.min(a.getY(), b.getY()),
                            Math.min(a.getZ(), b.getZ()))).orElse(geometry.anchor());
            BlockPos sceneMax = scene.writes().stream().map(WorldWrite::position)
                    .reduce((a, b) -> new BlockPos(
                            Math.max(a.getX(), b.getX()),
                            Math.max(a.getY(), b.getY()),
                            Math.max(a.getZ(), b.getZ()))).orElse(geometry.anchor());
            List<AuthoredPosition> authored = scene.writes().stream()
                    .map(write -> new AuthoredPosition(
                            write.position(), write.role(), write.desired(),
                            level.getBlockState(write.position()),
                            write.priorState(), write.priorBlockEntityAbsent()))
                    .toList();
            Set<BlockPos> evidencedShaft = entries.stream()
                    .flatMap(entry -> entry.shaftPositions().stream())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Set<BlockPos> authoredShaft = scene.writes().stream()
                    .filter(write -> write.role() == AuthoredRole.OPENED_SHAFT)
                    .map(WorldWrite::position)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!evidencedShaft.equals(authoredShaft)) {
                return;
            }
            long expectedFingerprint = fingerprintExpected(scene.writes());
            long observedFingerprint = fingerprintObserved(level, scene.writes());
            boolean postWriteVerified = expectedFingerprint == observedFingerprint
                    && postWritesMatch(level, scene.writes());
            callback.accept(new AuditEvent(
                    geometry.anchor(), rewardRoll, scene.kind(), powder, regular,
                    landingMin, landingMax, fallMin, fallMax,
                    scene.waterPositions(), ores, scene.chestPosition(),
                    scene.remainsFootPosition(), scene.remainsHeadPosition(),
                    sceneMin, sceneMax, fallbackReason,
                    scene.axis(), firstBank, secondBank, regular, entries,
                    entries.stream().map(EntryEvidence::dryExit).distinct().toList(),
                    scene.chestFront(), scene.chestToExitPath(),
                    scene.shorePositions(), scene.galleryPositions(),
                    scene.floodedExitPath(), authored,
                    expectedFingerprint, observedFingerprint, postWriteVerified,
                    mergePositions(firstBank, secondBank),
                    geometry.firstNaturalContinuation(),
                    geometry.secondNaturalContinuation(),
                    geometry.upperNaturalReturn()));
        } catch (RuntimeException ignored) {
            // A proof observer must never alter successful production world generation.
        }
    }

    private static List<BlockPos> immutablePositions(List<BlockPos> positions) {
        return positions.stream().map(BlockPos::immutable).toList();
    }

    private static List<List<BlockPos>> immutablePathLists(List<List<BlockPos>> paths) {
        return paths.stream().map(CaveDropTrapFeature::immutablePositions).toList();
    }

    private static List<BlockPos> naturalFloors(
            List<CaveDropTrap.Cell> cells,
            int floorY,
            int baseX,
            int baseZ,
            int originLx,
            int originLz) {
        return cells.stream()
                .map(cell -> new BlockPos(
                        baseX + originLx + cell.x(),
                        floorY,
                        baseZ + originLz + cell.z()))
                .toList();
    }

    private static List<BlockPos> mergePositions(
            List<BlockPos> first, List<BlockPos> second) {
        LinkedHashSet<BlockPos> merged = new LinkedHashSet<>();
        first.forEach(position -> merged.add(position.immutable()));
        second.forEach(position -> merged.add(position.immutable()));
        return List.copyOf(merged);
    }

    private static List<BlockPos> orderedShaftPositions(
            BlockPos entranceTop, BlockPos landing) {
        List<BlockPos> shaft = new ArrayList<>();
        for (int y = entranceTop.getY() - 1; y > landing.getY(); y--) {
            shaft.add(new BlockPos(entranceTop.getX(), y, entranceTop.getZ()));
        }
        return List.copyOf(shaft);
    }

    private static BlockPos stableWorldPosition(
            TrapGeometry geometry, CaveDropTrap.Cell cell, int baseX, int baseZ) {
        StableFloor floor = geometry.regular().stream()
                .filter(candidate -> candidate.lx() == cell.x() && candidate.lz() == cell.z())
                .findFirst().orElseThrow();
        return new BlockPos(baseX + floor.lx(), floor.floorY(), baseZ + floor.lz());
    }

    private static BlockPos highestWaterAt(List<BlockPos> water, BlockPos entrance) {
        return water.stream()
                .filter(position -> position.getX() == entrance.getX()
                        && position.getZ() == entrance.getZ())
                .max(Comparator.comparingInt(BlockPos::getY))
                .orElseThrow();
    }

    private static long fingerprintExpected(List<WorldWrite> writes) {
        long fingerprint = 0x4354564552494659L;
        for (WorldWrite write : writes) {
            fingerprint = fingerprintState(fingerprint, write.position(), write.desired());
        }
        return fingerprint;
    }

    private static long fingerprintObserved(WorldGenLevel level, List<WorldWrite> writes) {
        long fingerprint = 0x4354564552494659L;
        for (WorldWrite write : writes) {
            fingerprint = fingerprintState(
                    fingerprint, write.position(), level.getBlockState(write.position()));
        }
        return fingerprint;
    }

    private static long fingerprintState(long prior, BlockPos position, BlockState state) {
        long value = prior;
        value = Long.rotateLeft(value ^ position.asLong(), 17);
        value = Long.rotateLeft(value ^ state.toString().hashCode(), 29);
        return value * 0x9e3779b97f4a7c15L;
    }

    static List<CaveDropTrap.Cell> exactEscapePath(
            CaveDropTrap.Cell start,
            CaveDropTrap.Cell end,
            int steps,
            Set<CaveDropTrap.Cell> blocked,
            int lowerStandingY,
            Set<CaveDropTrap.Voxel> protectedNaturalEvidence) {
        CaveDropTrap.Voxel firstStanding =
                new CaveDropTrap.Voxel(start.x(), lowerStandingY, start.z());
        if (!CaveDropTrap.escapeStandingPreservesEvidence(
                firstStanding, protectedNaturalEvidence)) {
            return null;
        }
        List<CaveDropTrap.Cell> path = new ArrayList<>();
        Set<CaveDropTrap.Cell> visited = new HashSet<>();
        int[] searchBudget = {MAX_ESCAPE_SEARCH_STATES};
        path.add(start);
        visited.add(start);
        return extendEscapePath(
                start, end, steps, blocked, visited, path, searchBudget,
                lowerStandingY, protectedNaturalEvidence)
                ? List.copyOf(path) : null;
    }

    private static boolean extendEscapePath(
            CaveDropTrap.Cell current,
            CaveDropTrap.Cell end,
            int remaining,
            Set<CaveDropTrap.Cell> blocked,
            Set<CaveDropTrap.Cell> visited,
            List<CaveDropTrap.Cell> path,
            int[] searchBudget,
            int lowerStandingY,
            Set<CaveDropTrap.Voxel> protectedNaturalEvidence) {
        if (searchBudget[0]-- <= 0) {
            return false;
        }
        int distance = manhattan(current, end);
        if (distance > remaining || ((remaining - distance) & 1) != 0) {
            return false;
        }
        if (remaining == 0) {
            return current.equals(end);
        }
        for (Direction direction : HORIZONTAL) {
            CaveDropTrap.Cell next = new CaveDropTrap.Cell(
                    current.x() + direction.getStepX(),
                    current.z() + direction.getStepZ());
            if (next.x() < 1 || next.x() > 14 || next.z() < 1 || next.z() > 14
                    || blocked.contains(next) || !visited.add(next)) {
                continue;
            }
            int stepIndex = path.size();
            if (remaining > 1
                    && !CaveDropTrap.escapeStandingPreservesEvidence(
                    new CaveDropTrap.Voxel(
                            next.x(), lowerStandingY + stepIndex, next.z()),
                    protectedNaturalEvidence)) {
                visited.remove(next);
                continue;
            }
            path.add(next);
            if (extendEscapePath(
                    next, end, remaining - 1, blocked, visited, path, searchBudget,
                    lowerStandingY, protectedNaturalEvidence)) {
                return true;
            }
            path.removeLast();
            visited.remove(next);
        }
        return false;
    }

    private static int manhattan(CaveDropTrap.Cell first, CaveDropTrap.Cell second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z());
    }

    private static List<LandingRoute> planLandingRoutes(
            WorldGenLevel level, List<DropCell> cells, int baseX, int baseZ) {
        Set<BlockPos> landings = new LinkedHashSet<>();
        Set<Long> footprint = new HashSet<>();
        for (DropCell cell : cells) {
            BlockPos landing =
                    new BlockPos(baseX + cell.lx(), cell.landingAirY(), baseZ + cell.lz());
            if (!insideOwnerInset(landing)
                    || !twoAir(level, landing.getX(), landing.getY(), landing.getZ())
                    || !supportedNaturalMass(level, landing.below(), STABLE_MASS_DEPTH)) {
                return null;
            }
            landings.add(landing);
            footprint.add(localKey(cell.lx(), cell.lz()));
        }

        BlockPos firstLanding = landings.iterator().next();
        Set<BlockPos> firstBlocked = new HashSet<>(landings);
        firstBlocked.remove(firstLanding);
        Map<BlockPos, BlockPos> firstParents = reachableDryNodes(
                level, firstLanding, firstBlocked, baseX, baseZ,
                LOWER_ROUTE_RADIUS, LOWER_ROUTE_VERTICAL_RANGE);
        int minimumComponent = Math.max(12, cells.size() + 4);
        if (firstParents.size() < minimumComponent) {
            return null;
        }

        List<BlockPos> exits = firstParents.keySet().stream()
                .filter(position -> farFromLandingFootprint(position, footprint, baseX, baseZ))
                .filter(position -> dryNeighbourCount(level, position) >= 2)
                .sorted(Comparator.comparingInt((BlockPos position) -> position.getY())
                        .thenComparingInt(position -> position.getX())
                        .thenComparingInt(position -> position.getZ()))
                .toList();
        for (BlockPos exit : exits) {
            List<LandingRoute> routes = new ArrayList<>();
            boolean allReach = true;
            for (DropCell cell : cells) {
                BlockPos landing =
                        new BlockPos(baseX + cell.lx(), cell.landingAirY(), baseZ + cell.lz());
                Set<BlockPos> blocked = new HashSet<>(landings);
                blocked.remove(landing);
                List<BlockPos> path = findDryPath(
                        level, landing, exit, blocked,
                        baseX, baseZ, LOWER_ROUTE_RADIUS, LOWER_ROUTE_VERTICAL_RANGE);
                if (path == null) {
                    allReach = false;
                    break;
                }
                routes.add(new LandingRoute(
                        new BlockPos(baseX + cell.lx(), cell.floorY(), baseZ + cell.lz()),
                        landing, exit, path));
            }
            if (allReach) {
                return List.copyOf(routes);
            }
        }
        return null;
    }

    private static Map<BlockPos, BlockPos> reachableDryNodes(
            WorldGenLevel level,
            BlockPos start,
            Set<BlockPos> blocked,
            int baseX,
            int baseZ,
            int radius,
            int verticalRange) {
        Map<BlockPos, BlockPos> parents = new LinkedHashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos immutableStart = start.immutable();
        parents.put(immutableStart, null);
        queue.add(immutableStart);
        while (!queue.isEmpty() && parents.size() <= 4096) {
            BlockPos current = queue.removeFirst();
            for (BlockPos next : walkingNeighbours(current)) {
                BlockPos immutable = next.immutable();
                if (!insideOwnerInset(immutable)
                        || Math.abs(immutable.getX() - start.getX())
                                + Math.abs(immutable.getZ() - start.getZ()) > radius
                        || Math.abs(immutable.getY() - start.getY()) > verticalRange
                        || blocked.contains(immutable)
                        || parents.containsKey(immutable)
                        || !dryWalkableStanding(level, immutable)) {
                    continue;
                }
                parents.put(immutable, current);
                queue.addLast(immutable);
            }
        }
        return parents;
    }

    private static List<BlockPos> findDryPath(
            WorldGenLevel level,
            BlockPos start,
            BlockPos target,
            Set<BlockPos> blocked,
            int baseX,
            int baseZ,
            int radius,
            int verticalRange) {
        Map<BlockPos, BlockPos> parents = reachableDryNodes(
                level, start, blocked, baseX, baseZ, radius, verticalRange);
        BlockPos immutableTarget = target.immutable();
        if (!parents.containsKey(immutableTarget)) {
            return null;
        }
        ArrayDeque<BlockPos> reverse = new ArrayDeque<>();
        for (BlockPos cursor = immutableTarget; cursor != null; cursor = parents.get(cursor)) {
            reverse.addFirst(cursor);
        }
        return List.copyOf(reverse);
    }

    private static List<BlockPos> findProjectedPath(
            WorldGenLevel level,
            Map<BlockPos, WorldWrite> writes,
            BlockPos start,
            BlockPos target,
            Set<BlockPos> blocked,
            Set<BlockPos> traversableCushions,
            Set<BlockPos> allowedStanding,
            int baseX,
            int baseZ,
            int radius,
            int verticalRange) {
        Map<BlockPos, BlockPos> parents = new LinkedHashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos immutableStart = start.immutable();
        BlockPos immutableTarget = target.immutable();
        parents.put(immutableStart, null);
        queue.add(immutableStart);
        while (!queue.isEmpty() && parents.size() <= 4096
                && !parents.containsKey(immutableTarget)) {
            BlockPos current = queue.removeFirst();
            for (BlockPos next : walkingNeighbours(current)) {
                BlockPos immutable = next.immutable();
                if (!insideOwnerInset(immutable)
                        || Math.abs(immutable.getX() - start.getX())
                                + Math.abs(immutable.getZ() - start.getZ()) > radius
                        || Math.abs(immutable.getY() - start.getY()) > verticalRange
                        || blocked.contains(immutable)
                        || !allowedStanding.contains(immutable)
                        || parents.containsKey(immutable)
                        || (!traversableCushions.contains(immutable)
                                && !dryWalkableStanding(level, writes, immutable))) {
                    continue;
                }
                parents.put(immutable, current);
                queue.addLast(immutable);
            }
        }
        if (!parents.containsKey(immutableTarget)) {
            return null;
        }
        ArrayDeque<BlockPos> reverse = new ArrayDeque<>();
        for (BlockPos cursor = immutableTarget; cursor != null; cursor = parents.get(cursor)) {
            reverse.addFirst(cursor);
        }
        return List.copyOf(reverse);
    }

    private static List<BlockPos> walkingNeighbours(BlockPos position) {
        List<BlockPos> neighbours = new ArrayList<>(12);
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = position.relative(direction);
            neighbours.add(horizontal);
            neighbours.add(horizontal.above());
            neighbours.add(horizontal.below());
        }
        return neighbours;
    }

    private static boolean farFromLandingFootprint(
            BlockPos position, Set<Long> footprint, int baseX, int baseZ) {
        int lx = position.getX() - baseX;
        int lz = position.getZ() - baseZ;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (footprint.contains(localKey(lx + dx, lz + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int dryNeighbourCount(WorldGenLevel level, BlockPos position) {
        int count = 0;
        for (Direction direction : HORIZONTAL) {
            BlockPos neighbour = position.relative(direction);
            for (int dy = -1; dy <= 1; dy++) {
                if (dryWalkableStanding(level, neighbour.above(dy))) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static boolean dryWalkableStanding(WorldGenLevel level, BlockPos standing) {
        BlockPos floor = standing.below();
        BlockState floorState = level.getBlockState(floor);
        return insideOwnerInset(standing)
                && twoAir(level, standing.getX(), standing.getY(), standing.getZ())
                && safeNaturalSupport(level, floor, floorState)
                && !floorState.is(Blocks.MAGMA_BLOCK);
    }

    private static boolean dryWalkableStanding(
            WorldGenLevel level, Map<BlockPos, WorldWrite> writes, BlockPos standing) {
        BlockState floorState = projectedState(level, writes, standing.below());
        return insideOwnerInset(standing)
                && airWithoutEntity(level, writes, standing)
                && airWithoutEntity(level, writes, standing.above())
                && floorState.blocksMotion()
                && floorState.getFluidState().isEmpty()
                && !floorState.is(Blocks.MAGMA_BLOCK)
                && !floorState.is(Blocks.BEDROCK);
    }

    private static boolean supportedNaturalMass(
            WorldGenLevel level, BlockPos top, int depth) {
        for (int offset = 0; offset < depth; offset++) {
            BlockPos pos = top.below(offset);
            if (!safeNaturalSupport(level, pos, level.getBlockState(pos))) {
                return false;
            }
        }
        return true;
    }

    private static boolean supportedNaturalMass(
            WorldGenLevel level,
            Map<BlockPos, WorldWrite> writes,
            BlockPos top,
            int depth) {
        for (int offset = 0; offset < depth; offset++) {
            BlockPos pos = top.below(offset);
            if (writes.containsKey(pos)
                    || !safeNaturalSupport(level, pos, level.getBlockState(pos))) {
                return false;
            }
        }
        return true;
    }

    private static boolean safeNaturalSupport(
            WorldGenLevel level, BlockPos position, BlockState state) {
        return safeNaturalTarget(level, position, state) && state.blocksMotion();
    }

    private static boolean safeNaturalTarget(
            WorldGenLevel level, BlockPos position, BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.MAGMA_BLOCK)
                && !(state.getBlock() instanceof FallingBlock)
                && level.getBlockEntity(position) == null
                && naturalMaterial(state)
                && !isOre(state);
    }

    private static boolean naturalMaterial(BlockState state) {
        return state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES)
                || state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.ICE);
    }

    private static boolean isOre(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && id.getPath().endsWith("_ore");
    }

    private static boolean addNaturalWrite(
            WorldGenLevel level, Map<BlockPos, WorldWrite> writes,
            BlockPos position, BlockState desired, AuthoredRole role) {
        BlockState current = level.getBlockState(position);
        return safeNaturalTarget(level, position, current)
                && putWrite(level, writes, position, desired, role);
    }

    private static boolean replaceRequiredGalleryAirWrite(
            Map<BlockPos, WorldWrite> writes,
            BlockPos position,
            BlockState desired,
            AuthoredRole role) {
        WorldWrite prior = writes.get(position);
        if (prior == null
                || prior.role() != AuthoredRole.GALLERY_AIR
                || !prior.desired().isAir()
                || !prior.priorBlockEntityAbsent()) {
            return false;
        }
        writes.put(position, new WorldWrite(
                prior.position(), prior.expected(), desired, role,
                prior.priorBlockEntityAbsent()));
        return true;
    }

    private static boolean addGalleryAirWrite(
            WorldGenLevel level, Map<BlockPos, WorldWrite> writes, BlockPos position) {
        BlockState current = level.getBlockState(position);
        return safeNaturalTarget(level, position, current)
                && putWrite(level, writes, position, AIR, AuthoredRole.GALLERY_AIR);
    }

    private static boolean addSafeAirWrite(
            WorldGenLevel level,
            Map<BlockPos, WorldWrite> writes,
            BlockPos position,
            AuthoredRole role) {
        BlockState current = level.getBlockState(position);
        if (current.isAir() && level.getBlockEntity(position) == null) {
            return true;
        }
        return safeNaturalTarget(level, position, current)
                && putWrite(level, writes, position, AIR, role);
    }

    private static boolean safeAirTarget(WorldGenLevel level, BlockPos position) {
        BlockState current = level.getBlockState(position);
        return (current.isAir() && level.getBlockEntity(position) == null)
                || safeNaturalTarget(level, position, current);
    }

    private static boolean addGalleryWaterWrite(
            WorldGenLevel level, Map<BlockPos, WorldWrite> writes, BlockPos position) {
        BlockState current = level.getBlockState(position);
        return safeNaturalTarget(level, position, current)
                && putWrite(level, writes, position, WATER, AuthoredRole.GALLERY_WATER);
    }

    private static boolean putWrite(
            WorldGenLevel level, Map<BlockPos, WorldWrite> writes,
            BlockPos position, BlockState desired, AuthoredRole role) {
        boolean priorBlockEntityAbsent = level.getBlockEntity(position) == null;
        if (!level.ensureCanWrite(position) || !priorBlockEntityAbsent) {
            return false;
        }
        WorldWrite prior = writes.get(position);
        if (prior != null) {
            return prior.desired().equals(desired);
        }
        if (level.getBlockState(position).equals(desired)) {
            return true;
        }
        writes.put(position, new WorldWrite(
                position.immutable(), level.getBlockState(position), desired, role,
                priorBlockEntityAbsent));
        return true;
    }

    private static LinkedHashMap<BlockPos, WorldWrite> writesByPosition(List<WorldWrite> source) {
        LinkedHashMap<BlockPos, WorldWrite> writes = new LinkedHashMap<>();
        for (WorldWrite write : source) {
            writes.put(write.position(), write);
        }
        return writes;
    }

    private static boolean airWithoutEntity(WorldGenLevel level, BlockPos position) {
        return level.getBlockState(position).isAir()
                && level.getBlockEntity(position) == null;
    }

    private static boolean airWithoutEntity(
            WorldGenLevel level, Map<BlockPos, WorldWrite> writes, BlockPos position) {
        WorldWrite planned = writes.get(position);
        if (planned != null) {
            return planned.desired().isAir() && planned.priorBlockEntityAbsent();
        }
        return airWithoutEntity(level, position);
    }

    private static BlockState projectedState(
            WorldGenLevel level, Map<BlockPos, WorldWrite> writes, BlockPos position) {
        WorldWrite planned = writes.get(position);
        return planned == null ? level.getBlockState(position) : planned.desired();
    }

    private static boolean combinedDryRoutesRemainSupported(
            WorldGenLevel level,
            Map<BlockPos, WorldWrite> writes,
            List<LandingRoute> routes) {
        List<List<CaveDropTrap.Voxel>> projectedRoutes = routes.stream()
                .map(route -> route.path().stream()
                        .map(CaveDropTrapFeature::voxel)
                        .toList())
                .toList();
        return CaveDropTrap.dryRoutesRemainSupported(
                projectedRoutes,
                cell -> airWithoutEntity(level, writes, blockPos(cell)),
                cell -> projectedDrySupport(level, writes, blockPos(cell)));
    }

    /**
     * Pass-A walk-out certification. Identical to the production check except that the planned
     * powder cushion counts as support for the one climb-out node standing in it: index zero of
     * the route is the cushion and index one is the victim's own landing position, so demanding
     * firm snow underneath would ask the deliberately soft landing to be solid. Every later node
     * still needs genuine projected support.
     */
    private static boolean passADryRoutesRemainSupported(
            WorldGenLevel level,
            Map<BlockPos, WorldWrite> writes,
            List<LandingRoute> routes) {
        List<List<CaveDropTrap.Voxel>> projectedRoutes = routes.stream()
                .map(route -> route.path().stream()
                        .map(CaveDropTrapFeature::voxel)
                        .toList())
                .toList();
        return CaveDropTrap.dryRoutesRemainSupported(
                projectedRoutes,
                cell -> airWithoutEntity(level, writes, blockPos(cell)),
                cell -> passADrySupport(level, writes, blockPos(cell)));
    }

    private static boolean passADrySupport(
            WorldGenLevel level,
            Map<BlockPos, WorldWrite> writes,
            BlockPos position) {
        WorldWrite planned = writes.get(position);
        if (planned != null && planned.role() == AuthoredRole.POWDER_CUSHION) {
            return true;
        }
        return projectedDrySupport(level, writes, position);
    }

    private static boolean projectedDrySupport(
            WorldGenLevel level,
            Map<BlockPos, WorldWrite> writes,
            BlockPos position) {
        WorldWrite planned = writes.get(position);
        if (planned == null) {
            return safeNaturalSupport(level, position, level.getBlockState(position));
        }
        BlockState state = planned.desired();
        return planned.role() == AuthoredRole.FIRM_CAMOUFLAGE
                && state.blocksMotion()
                && state.getFluidState().isEmpty()
                && !state.is(Blocks.MAGMA_BLOCK)
                && !state.is(Blocks.BEDROCK);
    }

    private static CaveDropTrap.Voxel voxel(BlockPos position) {
        return new CaveDropTrap.Voxel(
                position.getX(), position.getY(), position.getZ());
    }

    private static Set<CaveDropTrap.Voxel> protectedNaturalEvidence(
            TrapGeometry geometry) {
        List<CaveDropTrap.Voxel> floors = new ArrayList<>();
        geometry.firstNaturalContinuation().stream()
                .map(CaveDropTrapFeature::voxel)
                .forEach(floors::add);
        geometry.secondNaturalContinuation().stream()
                .map(CaveDropTrapFeature::voxel)
                .forEach(floors::add);
        geometry.upperNaturalReturn().stream()
                .map(CaveDropTrapFeature::voxel)
                .forEach(floors::add);
        return CaveDropTrap.naturalFloorEvidenceEnvelope(floors);
    }

    private static BlockPos blockPos(CaveDropTrap.Voxel cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private static boolean twoAir(WorldGenLevel level, int x, int y, int z) {
        BlockPos lower = new BlockPos(x, y, z);
        BlockPos upper = lower.above();
        return airWithoutEntity(level, lower) && airWithoutEntity(level, upper);
    }

    private static boolean insideChunk(int lx, int lz) {
        return lx >= 0 && lx < 16 && lz >= 0 && lz < 16;
    }

    private static boolean insideOwnerChunk(BlockPos position, int baseX, int baseZ) {
        return position.getX() >= baseX && position.getX() <= baseX + 15
                && position.getZ() >= baseZ && position.getZ() <= baseZ + 15;
    }

    private static boolean insideOwnerInset(BlockPos position) {
        int lx = Math.floorMod(position.getX(), 16);
        int lz = Math.floorMod(position.getZ(), 16);
        return lx >= 1 && lx <= 14 && lz >= 1 && lz <= 14;
    }

    private static long localKey(int lx, int lz) {
        return ((long) lx << 32) ^ (lz & 0xffffffffL);
    }

    private static long stableLootSeed(long worldSeed, BlockPos anchor) {
        long value = worldSeed ^ 0x4c4f535450524f53L;
        value ^= (long) anchor.getX() * 0x9e3779b97f4a7c15L;
        value ^= (long) anchor.getY() * 0xbf58476d1ce4e5b9L;
        value ^= (long) anchor.getZ() * 0x94d049bb133111ebL;
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static Direction horizontalDirection(BlockPos from, BlockPos to) {
        for (Direction direction : HORIZONTAL) {
            if (from.relative(direction).equals(to)) {
                return direction;
            }
        }
        return null;
    }

    private static void reject(Map<String, Integer> rejected, String reason) {
        rejected.merge(reason, 1, Integer::sum);
    }

    private static final List<Direction> HORIZONTAL =
            List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
}
