package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.core.CaveDropTrap;
import com.example.globe.core.HiddenChamberPlan;
import com.example.globe.core.LatitudeV2Flags;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * World layer for {@code globe:hidden_glacial_chamber} -- the concealed collapse mouth in an upper glacial
 * cave floor, its drop onto a deep powder cushion, the authored irregular chamber below, and the winding
 * corridor that breaches back into the same upper cave.
 *
 * <p>All geometry, theming, and safety law live in the pure {@link HiddenChamberPlan} planner. This adapter
 * owns exactly three things: a {@link HiddenChamberPlan.TerrainProbe} over the real {@link WorldGenLevel},
 * the role-to-block vocabulary, and the atomic all-or-nothing apply. It makes no placement decision of its
 * own -- notably, the one and only rarity is the planner's own occurrence gate, consulted here exactly once
 * and only AFTER a plan has been accepted, so rarity can never change which chunk would have been buildable.
 *
 * <h2>Envelope</h2>
 * The planner authors into the owner chunk plus exactly one cardinal neighbour. The probe therefore answers
 * for owner-local columns whose chunk offset is the owner itself or one cardinal step away, and only while
 * that chunk is inside the region's cache; everything else reads {@code null}/{@code UNREADABLE} so the
 * planner rejects rather than guesses. Reads never touch a diagonal chunk, and the probe's readability test
 * is {@code WorldGenRegion.isWithinWriteZone} rather than {@link WorldGenLevel#ensureCanWrite(BlockPos)} --
 * the same predicate without the latter's {@code Util.logAndPauseIfInIde} side effect, which is a write
 * guard's business and not a reader's.
 *
 * <h2>Telemetry</h2>
 * One row per invocation under {@code -Dlatitude.debugGlacialDressing=true} (or the backward-compatible
 * {@code -Dlatitude.debugCollapse=true}), prefixed {@code [LAT][CHAMBER]}, carrying the outcome, the
 * rejection detail, the accepted geometry, and the process-cumulative counters. Unlike the old drop-trap
 * counters these rows sit on the SHIPPING path, not behind a dead early return. {@link WriteTelemetry}
 * additionally gives a fresh-generation audit a bounded apply-path evidence window.
 */
public final class HiddenGlacialChamberFeature extends Feature<NoneFeatureConfiguration> {

    public static Feature<NoneFeatureConfiguration> INSTANCE;

    /** One below the glacial-caves ceiling at Y48: the upper cave floor band the mouth may open in. */
    static final int COLUMN_SCAN_TOP_Y = 47;
    /** A cave floor is "roofed" when a solid ceiling stands within this many blocks of its walkable top. */
    static final int ROOF_SCAN_HEIGHT = 40;

    private static final int SET_BLOCK_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private static final Identifier GLACIAL_CAVES =
            Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "glacial_caves");

    private static final ResourceKey<LootTable> FROZEN_EXPEDITION_CACHE_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "chests/frozen_expedition_cache"));

    private static final boolean DEBUG = debugEnabled(
            Boolean.getBoolean("latitude.debugGlacialDressing"),
            Boolean.getBoolean("latitude.debugCollapse"));

    /** Development override for the hashed theme pick; unset in production. */
    private static final String FORCE_THEME_PROPERTY = "latitude.chamber.forceTheme";
    private static final HiddenChamberPlan.Theme FORCED_THEME = readForcedTheme();

    private static volatile boolean missingBlockWarned;

    /* ------------------------------------------------------------------------------------------------ */
    /* Process-cumulative counters                                                                       */
    /* ------------------------------------------------------------------------------------------------ */

    private static final AtomicLong noEntrance = new AtomicLong();
    private static final AtomicLong unsafeVolume = new AtomicLong();
    private static final AtomicLong noExit = new AtomicLong();
    private static final AtomicLong rarityRejected = new AtomicLong();
    private static final AtomicLong completedChamber = new AtomicLong();
    private static final AtomicLong abortedRolledBack = new AtomicLong();

    /** Immutable read of the six process-cumulative counters, for the audit lane and for tests. */
    public record Counters(long noEntrance, long unsafeVolume, long noExit,
                           long rarityRejected, long completedChamber, long abortedRolledBack) {
    }

    public static Counters countersSnapshot() {
        return new Counters(noEntrance.get(), unsafeVolume.get(), noExit.get(),
                rarityRejected.get(), completedChamber.get(), abortedRolledBack.get());
    }

    /** Test hook: zero every counter. Production never calls this. */
    public static void resetCounters() {
        noEntrance.set(0);
        unsafeVolume.set(0);
        noExit.set(0);
        rarityRejected.set(0);
        completedChamber.set(0);
        abortedRolledBack.set(0);
    }

    /** What one invocation did, as reported on the debug row. */
    public enum Outcome {
        COMPLETED,
        NO_ENTRANCE,
        UNSAFE_VOLUME,
        NO_EXIT,
        RARITY,
        ROLLED_BACK,
        GATE_OFF
    }

    /* ------------------------------------------------------------------------------------------------ */
    /* Bounded apply-path evidence for one explicitly opened fresh-generation audit interval             */
    /* ------------------------------------------------------------------------------------------------ */

    /** Session clone of {@code PowderCrevasseRoofFeature}'s generation write telemetry, keyed by name. */
    public static final class WriteTelemetry {

        private static final Object LOCK = new Object();
        private static String activeSessionId;
        private static long attempts;
        private static long appliedSuccesses;
        private static long failedWriteBatches;
        private static long rolledBackWriteBatches;

        private WriteTelemetry() {
        }

        /** One closed audit interval; {@code available} is false when the id did not match an open session. */
        public record Session(boolean available, String sessionId, long attempts, long appliedSuccesses,
                              long failedWriteBatches, long rolledBackWriteBatches) {
        }

        public static void beginSession(String id) {
            synchronized (LOCK) {
                activeSessionId = id;
                attempts = 0;
                appliedSuccesses = 0;
                failedWriteBatches = 0;
                rolledBackWriteBatches = 0;
            }
        }

        public static Session endSession(String id) {
            synchronized (LOCK) {
                if (activeSessionId == null || !activeSessionId.equals(id)) {
                    return new Session(false, id, 0, 0, 0, 0);
                }
                Session closed = new Session(true, id, attempts, appliedSuccesses,
                        failedWriteBatches, rolledBackWriteBatches);
                activeSessionId = null;
                return closed;
            }
        }

        static void recordAttempt() {
            synchronized (LOCK) {
                if (activeSessionId != null) {
                    attempts++;
                }
            }
        }

        static void recordOutcome(boolean succeeded, boolean rollbackVerified) {
            synchronized (LOCK) {
                if (activeSessionId == null) {
                    return;
                }
                if (succeeded) {
                    appliedSuccesses++;
                } else {
                    failedWriteBatches++;
                    if (rollbackVerified) {
                        rolledBackWriteBatches++;
                    }
                }
            }
        }
    }

    public HiddenGlacialChamberFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Registered unconditionally at mod init (registry-consistency law), listed only in glacial_caves. */
    public static void register() {
        INSTANCE = Registry.register(
                BuiltInRegistries.FEATURE,
                Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "hidden_glacial_chamber"),
                new HiddenGlacialChamberFeature(NoneFeatureConfiguration.CODEC));
    }

    static boolean debugEnabled(boolean dedicated, boolean legacyCollapse) {
        return dedicated || legacyCollapse;
    }

    private static HiddenChamberPlan.Theme readForcedTheme() {
        String raw = System.getProperty(FORCE_THEME_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "ice_cathedral" -> HiddenChamberPlan.Theme.ICE_CATHEDRAL;
            case "frigid_lake" -> HiddenChamberPlan.Theme.FRIGID_LAKE;
            case "lost_expedition" -> HiddenChamberPlan.Theme.LOST_EXPEDITION;
            default -> {
                GlobeMod.LOGGER.warn("[LAT][CHAMBER] ignoring unknown -D{}={} "
                        + "(expected ice_cathedral|frigid_lake|lost_expedition)", FORCE_THEME_PROPERTY, raw);
                yield null;
            }
        };
    }

    /* ------------------------------------------------------------------------------------------------ */
    /* Role vocabulary                                                                                   */
    /* ------------------------------------------------------------------------------------------------ */

    private static volatile Map<HiddenChamberPlan.CellRole, BlockState> roleStates;

    /**
     * The role-to-block vocabulary, resolved LAZILY at place time: two of its entries are this mod's own
     * registered blocks, and a class-init read would race the registration order in {@code GlobeMod}.
     * The map is TOTAL over {@link HiddenChamberPlan.CellRole}; a new role with no entry fails loudly here
     * rather than silently writing nothing.
     */
    static Map<HiddenChamberPlan.CellRole, BlockState> roleStates() {
        Map<HiddenChamberPlan.CellRole, BlockState> cached = roleStates;
        if (cached != null) {
            return cached;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState packedIce = Blocks.PACKED_ICE.defaultBlockState();
        BlockState snowBlock = Blocks.SNOW_BLOCK.defaultBlockState();
        BlockState ice = Blocks.ICE.defaultBlockState();
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();

        Map<HiddenChamberPlan.CellRole, BlockState> built =
                new EnumMap<>(HiddenChamberPlan.CellRole.class);
        // The concealed false floor: the same worldgen-only powder the cave drop traps use.
        built.put(HiddenChamberPlan.CellRole.MOUTH_COLLAPSE,
                CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW.defaultBlockState());
        // The snow layer a lowered mouth cube displaces: cleared, so the cube's own top is the walking floor.
        built.put(HiddenChamberPlan.CellRole.MOUTH_CLEAR, air);
        built.put(HiddenChamberPlan.CellRole.SHAFT_CLEAR, air);
        built.put(HiddenChamberPlan.CellRole.CHAMBER_CLEAR, air);
        built.put(HiddenChamberPlan.CellRole.RECESS_CLEAR, air);
        built.put(HiddenChamberPlan.CellRole.CORRIDOR_CLEAR, air);
        built.put(HiddenChamberPlan.CellRole.EXIT_PORTAL_CLEAR, air);
        built.put(HiddenChamberPlan.CellRole.CUSHION_POWDER, Blocks.POWDER_SNOW.defaultBlockState());
        built.put(HiddenChamberPlan.CellRole.CUSHION_BASE, packedIce);
        built.put(HiddenChamberPlan.CellRole.SHELF_FIRM, snowBlock);
        built.put(HiddenChamberPlan.CellRole.CORRIDOR_FLOOR, snowBlock);
        built.put(HiddenChamberPlan.CellRole.LEDGE_FIRM, snowBlock);
        built.put(HiddenChamberPlan.CellRole.WALL_SEAL, packedIce);
        // Same material as the wall seal, different obligation: this one is the room's own floor.
        built.put(HiddenChamberPlan.CellRole.FLOOR_SEAL, packedIce);
        built.put(HiddenChamberPlan.CellRole.COLUMN_ICE_PACKED, packedIce);
        built.put(HiddenChamberPlan.CellRole.COLUMN_ICE_BLUE, Blocks.BLUE_ICE.defaultBlockState());
        // 26.2 speleothem behaviour (fall, merge, drip) is TAG-driven off #minecraft:speleothems, which
        // globe:icicle already joins -- so the chamber must reuse THAT block, never a look-alike.
        built.put(HiddenChamberPlan.CellRole.ICICLE_ICE, IcicleBlocks.ICICLE.defaultBlockState()
                .setValue(SpeleothemBlock.TIP_DIRECTION, Direction.DOWN)
                .setValue(SpeleothemBlock.THICKNESS, SpeleothemThickness.TIP)
                .setValue(SpeleothemBlock.WATERLOGGED, Boolean.FALSE));
        built.put(HiddenChamberPlan.CellRole.FOSSIL_BONE, bone);
        built.put(HiddenChamberPlan.CellRole.DEBRIS_BONE, bone);
        built.put(HiddenChamberPlan.CellRole.LAKE_WATER, Blocks.WATER.defaultBlockState());
        built.put(HiddenChamberPlan.CellRole.LAKE_BED, packedIce);
        built.put(HiddenChamberPlan.CellRole.SHORE_ICE, ice);
        built.put(HiddenChamberPlan.CellRole.FLOE_ICE, ice);
        // The default snow state is a single layer: frost, not a drift.
        built.put(HiddenChamberPlan.CellRole.FROST_MOTE, Blocks.SNOW.defaultBlockState());
        // The default lantern state is standing (hanging=false).
        built.put(HiddenChamberPlan.CellRole.LANTERN, Blocks.LANTERN.defaultBlockState());
        built.put(HiddenChamberPlan.CellRole.CACHE_CHEST, Blocks.CHEST.defaultBlockState());
        built.put(HiddenChamberPlan.CellRole.DEBRIS_WOOD, Blocks.SPRUCE_FENCE.defaultBlockState());

        for (HiddenChamberPlan.CellRole role : HiddenChamberPlan.CellRole.values()) {
            if (built.get(role) == null) {
                throw new IllegalStateException(
                        "the hidden-chamber role vocabulary must be total; missing " + role);
            }
        }
        Map<HiddenChamberPlan.CellRole, BlockState> total = Collections.unmodifiableMap(built);
        roleStates = total;
        return total;
    }

    static BlockState roleState(HiddenChamberPlan.CellRole role) {
        return roleStates().get(role);
    }

    /* ------------------------------------------------------------------------------------------------ */
    /* Production entry point                                                                            */
    /* ------------------------------------------------------------------------------------------------ */

    private record PlannedWrite(BlockPos position, BlockState expected, BlockState desired,
                                HiddenChamberPlan.CellRole role) {
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        int baseX = (ctx.origin().getX() >> 4) << 4;
        int baseZ = (ctx.origin().getZ() >> 4) << 4;
        int chunkX = baseX >> 4;
        int chunkZ = baseZ >> 4;

        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED || !LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
            emit(chunkX, chunkZ, Outcome.GATE_OFF, null, null, baseX, baseZ);
            return false;
        }
        if (CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW == null || IcicleBlocks.ICICLE == null) {
            if (!missingBlockWarned) {
                missingBlockWarned = true;
                GlobeMod.LOGGER.warn("[LAT][CHAMBER] globe:cave_trap_powder_snow / globe:icicle are not "
                        + "registered; the hidden glacial chamber cannot author its false floor or icicles");
            }
            emit(chunkX, chunkZ, Outcome.GATE_OFF, null, null, baseX, baseZ);
            return false;
        }

        long worldSeed = level.getSeed();
        WorldProbe probe = new WorldProbe(level, baseX, baseZ, chunkX, chunkZ);
        HiddenChamberPlan.PlanResult result =
                HiddenChamberPlan.plan(worldSeed, chunkX, chunkZ, probe, FORCED_THEME);

        if (!result.isAccepted()) {
            Outcome outcome = switch (result.rejection()) {
                case NO_ENTRANCE -> {
                    noEntrance.incrementAndGet();
                    yield Outcome.NO_ENTRANCE;
                }
                case UNSAFE_VOLUME -> {
                    unsafeVolume.incrementAndGet();
                    yield Outcome.UNSAFE_VOLUME;
                }
                case NO_EXIT -> {
                    noExit.incrementAndGet();
                    yield Outcome.NO_EXIT;
                }
            };
            emit(chunkX, chunkZ, outcome, result.detail(), null, baseX, baseZ);
            return false;
        }

        HiddenChamberPlan.Plan plan = result.accepted();
        // THE single rarity, consulted exactly once and only after acceptance so it can never change which
        // chunk would have been buildable.
        if (!HiddenChamberPlan.occurrenceGate(worldSeed, chunkX, chunkZ)) {
            rarityRejected.incrementAndGet();
            emit(chunkX, chunkZ, Outcome.RARITY, null, plan, baseX, baseZ);
            return false;
        }

        WriteTelemetry.recordAttempt();
        boolean applied;
        boolean rollbackVerified;
        /*
         * Everything from here to the transaction's return is inside the attempt this feature has already
         * counted. The build alone is roughly nineteen hundred getBlockState reads plus a role lookup, and a
         * RuntimeException anywhere in it used to (a) leave that attempt with no outcome recorded against it,
         * so the audit's arithmetic silently stopped adding up, and (b) propagate straight out into the chunk
         * decoration loop, which is a chunk-generation crash. Both are answered here: the attempt is closed
         * as a failure with rollback UNVERIFIED (nothing proved the world is clean), the abort is counted and
         * announced with its own PRE_APPLY marker so an audit can tell it from a rolled-back transaction, and
         * the feature answers "nothing here" instead of taking the chunk down with it.
         */
        try {
            List<PlannedWrite> writes = new ArrayList<>(plan.writes().size());
            for (HiddenChamberPlan.Cell cell : plan.writes()) {
                BlockPos pos = new BlockPos(baseX + cell.x(), cell.y(), baseZ + cell.z());
                writes.add(new PlannedWrite(pos, level.getBlockState(pos), roleState(cell.role()),
                        cell.role()));
            }

            applied = false;
            rollbackVerified = true;
            if (writesStillMatch(level, writes)) {
                CaveDropTrap.AtomicResult transaction = apply(level, writes, worldSeed, chunkX, chunkZ);
                applied = transaction.success();
                rollbackVerified = transaction.rollbackVerified();
            }
        } catch (RuntimeException aborted) {
            WriteTelemetry.recordOutcome(false, false);
            abortedRolledBack.incrementAndGet();
            GlobeMod.LOGGER.error("[LAT][CHAMBER] chunk=({},{}) PRE_APPLY ABORT -- the planned chamber threw "
                    + "before its transaction completed; no partial write is claimed to have been rolled "
                    + "back", chunkX, chunkZ, aborted);
            emit(chunkX, chunkZ, Outcome.ROLLED_BACK, null, plan, baseX, baseZ);
            return false;
        }

        WriteTelemetry.recordOutcome(applied, rollbackVerified);
        if (applied) {
            completedChamber.incrementAndGet();
            emit(chunkX, chunkZ, Outcome.COMPLETED, null, plan, baseX, baseZ);
            return true;
        }
        abortedRolledBack.incrementAndGet();
        if (!rollbackVerified) {
            GlobeMod.LOGGER.error("[LAT][CHAMBER] chunk=({},{}) ROLLBACK VERIFICATION FAILED -- the aborted "
                    + "chamber may have left partial writes in the world", chunkX, chunkZ);
        }
        emit(chunkX, chunkZ, Outcome.ROLLED_BACK, null, plan, baseX, baseZ);
        return false;
    }

    /** Every planned coordinate must still be unique, writable, block-entity-free, and unchanged. */
    private static boolean writesStillMatch(WorldGenLevel level, List<PlannedWrite> writes) {
        Set<BlockPos> unique = new HashSet<>();
        for (PlannedWrite write : writes) {
            BlockPos pos = write.position();
            if (!level.ensureCanWrite(pos)
                    || !unique.add(pos)
                    || !level.getBlockState(pos).equals(write.expected())
                    || level.getBlockEntity(pos) != null) {
                return false;
            }
        }
        return true;
    }

    private static CaveDropTrap.AtomicResult apply(
            WorldGenLevel level, List<PlannedWrite> writes, long worldSeed, int chunkX, int chunkZ) {
        List<CaveDropTrap.AtomicStateChange<BlockState>> changes = writes.stream()
                .map(write -> new CaveDropTrap.AtomicStateChange<>(write.expected(), write.desired()))
                .toList();
        BlockPos chestPosition = null;
        for (PlannedWrite write : writes) {
            if (write.role() == HiddenChamberPlan.CellRole.CACHE_CHEST) {
                chestPosition = write.position();
                break;
            }
        }
        BlockPos boundChest = chestPosition;
        long lootSeed = lootSeed(worldSeed, chunkX, chunkZ);
        CaveDropTrap.AtomicResult transaction = CaveDropTrap.applyAtomically(
                changes,
                new CaveDropTrap.AtomicStateAdapter<>() {
                    @Override
                    public BlockState read(int index) {
                        return level.getBlockState(writes.get(index).position());
                    }

                    @Override
                    public boolean write(int index, BlockState state) {
                        return level.setBlock(writes.get(index).position(), state, SET_BLOCK_FLAGS);
                    }
                },
                null,
                () -> bindCacheChest(level, boundChest, lootSeed) && postWritesMatch(level, writes));
        boolean rollbackVerified = transaction.rollbackVerified();
        if (!transaction.success() && rollbackVerified) {
            for (PlannedWrite write : writes) {
                rollbackVerified &= level.getBlockEntity(write.position()) == null;
            }
        }
        return new CaveDropTrap.AtomicResult(transaction.success(), rollbackVerified);
    }

    /** The lost-expedition cache is seeded, never pre-filled: the loot table resolves on first open. */
    private static boolean bindCacheChest(WorldGenLevel level, BlockPos chestPosition, long lootSeed) {
        if (chestPosition == null) {
            return true;
        }
        if (!(level.getBlockEntity(chestPosition) instanceof ChestBlockEntity chest)) {
            return false;
        }
        chest.setLootTable(FROZEN_EXPEDITION_CACHE_LOOT);
        chest.setLootTableSeed(lootSeed);
        return true;
    }

    private static boolean postWritesMatch(WorldGenLevel level, List<PlannedWrite> writes) {
        for (PlannedWrite write : writes) {
            if (!level.getBlockState(write.position()).equals(write.desired())) {
                return false;
            }
        }
        return true;
    }

    static long lootSeed(long worldSeed, int chunkX, int chunkZ) {
        long mixed = worldSeed ^ HiddenChamberPlan.SALT;
        mixed = mixed * 6364136223846793005L + chunkX * 341873128712L + chunkZ * 132897987541L;
        return mixed ^ (mixed >>> 29);
    }

    /* ------------------------------------------------------------------------------------------------ */
    /* Telemetry rows                                                                                    */
    /* ------------------------------------------------------------------------------------------------ */

    private static void emit(int chunkX, int chunkZ, Outcome outcome,
                             HiddenChamberPlan.RejectionDetail detail, HiddenChamberPlan.Plan plan,
                             int baseX, int baseZ) {
        if (DEBUG) {
            GlobeMod.LOGGER.info(debugRow(chunkX, chunkZ, outcome, detail, plan, baseX, baseZ,
                    countersSnapshot()));
        }
    }

    /** One machine-readable row. Positions are WORLD coordinates so the audit can fly straight to them. */
    static String debugRow(int chunkX, int chunkZ, Outcome outcome,
                           HiddenChamberPlan.RejectionDetail detail, HiddenChamberPlan.Plan plan,
                           int baseX, int baseZ, Counters counters) {
        return "[LAT][CHAMBER] chunk=(" + chunkX + "," + chunkZ + ")"
                + " result=" + outcome
                + " detail=" + (detail == null ? "-" : detail.name())
                + " theme=" + (plan == null ? "-" : plan.theme().name())
                + " variant=" + (plan == null ? "-" : plan.variant().name())
                + " direction=" + (plan == null ? "-" : plan.direction().name())
                + " mouth=" + position(plan == null ? null : plan.mouthCentroid(), baseX, baseZ)
                + " landing=" + position(plan == null ? null : plan.landing(), baseX, baseZ)
                + " exit=" + position(plan == null ? null : plan.exitTerminus(), baseX, baseZ)
                + " writes=" + (plan == null ? 0 : plan.writes().size())
                + " cumulative{noEntrance=" + counters.noEntrance()
                + " unsafeVolume=" + counters.unsafeVolume()
                + " noExit=" + counters.noExit()
                + " rarityRejected=" + counters.rarityRejected()
                + " completedChamber=" + counters.completedChamber()
                + " abortedRolledBack=" + counters.abortedRolledBack() + "}";
    }

    private static String position(HiddenChamberPlan.Position local, int baseX, int baseZ) {
        return local == null ? "-"
                : "(" + (baseX + local.x()) + "," + local.y() + "," + (baseZ + local.z()) + ")";
    }

    /* ------------------------------------------------------------------------------------------------ */
    /* The one and only terrain access                                                                   */
    /* ------------------------------------------------------------------------------------------------ */

    /**
     * Owner-local view of the real region. The planner's read shell is the owner chunk plus exactly one
     * cardinal neighbour, so anything with a diagonal or two-step chunk offset -- and anything the region
     * has not cached -- reads as unreadable and fails the plan closed.
     */
    static final class WorldProbe implements HiddenChamberPlan.TerrainProbe {

        private final WorldGenLevel level;
        private final int baseX;
        private final int baseZ;
        private final int chunkX;
        private final int chunkZ;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        WorldProbe(WorldGenLevel level, int baseX, int baseZ, int chunkX, int chunkZ) {
            this.level = level;
            this.baseX = baseX;
            this.baseZ = baseZ;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        /**
         * The readability half of {@code WorldGenRegion.ensureCanWrite}, with none of its side effects.
         *
         * <p>{@code ensureCanWrite} is a WRITE guard: in 26.2 its out-of-zone branch calls
         * {@code Util.logAndPauseIfInIde}, which logs an error every time -- and pauses outright in a dev IDE.
         * Used as a readability predicate it fires on every probe read the planner legitimately makes outside
         * the write radius, which on a legacy-chunk upgrade is an error line per read.
         * {@code isWithinWriteZone} is the identical predicate with nothing attached to it. A level that is
         * not a {@code WorldGenRegion} has no write zone at all, and {@code WorldGenLevel}'s own default
         * {@code ensureCanWrite} says so by answering true.
         */
        private boolean withinWriteZone(BlockPos pos) {
            return !(level instanceof WorldGenRegion region) || region.isWithinWriteZone(pos);
        }

        private boolean readable(int localX, int localZ) {
            int chunkDx = Math.floorDiv(localX, 16);
            int chunkDz = Math.floorDiv(localZ, 16);
            if (Math.abs(chunkDx) + Math.abs(chunkDz) > 1) {
                return false;
            }
            return level.hasChunk(chunkX + chunkDx, chunkZ + chunkDz);
        }

        @Override
        public HiddenChamberPlan.ColumnInfo column(int localX, int localZ) {
            if (!readable(localX, localZ)) {
                return null;
            }
            int worldX = baseX + localX;
            int worldZ = baseZ + localZ;
            int ceiling = Math.min(COLUMN_SCAN_TOP_Y, level.getMaxY() - 1);
            int bottom = level.getMinY() + 1;
            for (int y = ceiling; y >= bottom; y--) {
                cursor.set(worldX, y, worldZ);
                BlockState floor = level.getBlockState(cursor);
                if (!isFloorTop(floor)) {
                    continue;
                }
                int firstOpenY = y + 1;
                if (firstOpenY > level.getMaxY()) {
                    continue;
                }
                cursor.set(worldX, firstOpenY, worldZ);
                if (!level.getBlockState(cursor).isAir()) {
                    continue;
                }
                int headroom = 0;
                boolean roofed = false;
                int scanTop = Math.min(firstOpenY + ROOF_SCAN_HEIGHT - 1, level.getMaxY());
                for (int scan = firstOpenY; scan <= scanTop; scan++) {
                    cursor.set(worldX, scan, worldZ);
                    if (!level.getBlockState(cursor).isAir()) {
                        roofed = true;
                        break;
                    }
                    headroom++;
                }
                return new HiddenChamberPlan.ColumnInfo(y, isSnowFamily(floor), roofed, headroom);
            }
            return null;
        }

        @Override
        public HiddenChamberPlan.CellKind cell(int localX, int y, int localZ) {
            if (!readable(localX, localZ) || y < level.getMinY() || y > level.getMaxY()) {
                return HiddenChamberPlan.CellKind.UNREADABLE;
            }
            BlockPos pos = new BlockPos(baseX + localX, y, baseZ + localZ);
            if (!withinWriteZone(pos)) {
                return HiddenChamberPlan.CellKind.UNREADABLE;
            }
            BlockState state = level.getBlockState(pos);
            if (state.hasBlockEntity() || level.getBlockEntity(pos) != null) {
                return HiddenChamberPlan.CellKind.BLOCK_ENTITY;
            }
            if (state.is(Blocks.BEDROCK)) {
                return HiddenChamberPlan.CellKind.BEDROCK;
            }
            if (!state.getFluidState().isEmpty()) {
                return HiddenChamberPlan.CellKind.FLUID;
            }
            if (state.getBlock() instanceof FallingBlock) {
                return HiddenChamberPlan.CellKind.GRAVITY;
            }
            if (isProtectedSolid(state)) {
                return HiddenChamberPlan.CellKind.SOLID_UNSAFE;
            }
            if (state.isAir()) {
                return HiddenChamberPlan.CellKind.AIR;
            }
            return isCarveSafe(state)
                    ? HiddenChamberPlan.CellKind.SOLID_SAFE
                    : HiddenChamberPlan.CellKind.SOLID_UNSAFE;
        }

        @Override
        public boolean savedGlacialQuart(int quartLocalX, int quartY, int quartLocalZ) {
            try {
                return level.getNoiseBiome(
                                QuartPos.fromBlock(baseX) + quartLocalX,
                                quartY,
                                QuartPos.fromBlock(baseZ) + quartLocalZ)
                        .unwrapKey().map(ResourceKey::identifier)
                        .map(GLACIAL_CAVES::equals)
                        .orElse(false);
            } catch (RuntimeException unreadable) {
                // Fail closed: a quart the region cannot answer for is not proof of glacial country.
                return false;
            }
        }
    }

    /**
     * The walkable top of a cave floor. Snow LAYERS are included deliberately: vanilla marks them
     * non-motion-blocking, but the layer is what a player stands on and what the collapse mouth must
     * replace, so it is the floor here rather than a fixture sitting above one.
     */
    static boolean isFloorTop(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty()
                && (state.blocksMotion() || isSnowFamily(state));
    }

    static boolean isSnowFamily(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
    }

    /** Magma, ore, and the deepslate country the S49 cellar ruling keeps off-limits. */
    private static boolean isProtectedSolid(BlockState state) {
        if (state.is(Blocks.MAGMA_BLOCK)) {
            return true;
        }
        if (state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
            return true;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && id.getPath().endsWith("_ore");
    }

    /** Everything a carver may legally eat, plus the glacial ice/snow vocabulary the chamber lives in. */
    private static boolean isCarveSafe(BlockState state) {
        return state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES)
                || isSnowFamily(state)
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.ICE);
    }
}
