package com.example.globe.world;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarBarrensBand;
import com.example.globe.core.SnowCollapseLaw;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Phase 5 Crew 9 / S30 THE REAL SNOW COLLAPSE -- the server RUNTIME behind the owner's collapse EVENT (Peetsa
 * 2026-07-20 eve sketch: "an unsuspecting player walks normal-looking snow; the snow beneath GIVES WAY ... SNOW
 * FALLS: the roof tumbles as falling chunks WITH the player into the void"). Pairs the MC-side wiring with the
 * pure {@link SnowCollapseLaw} (trigger signature, flood-fill, distance stagger, telegraph constant): this class
 * closes the law over a real {@link ServerLevel}, spawns the tumbling {@link FallingBlockEntity} chunks, and
 * plays the crack/whump audio + snow particles. It supersedes the S25b static powder LID: the gen feature
 * ({@link PowderCrevasseRoofFeature}) now lays the hidden snow_block/powder/void SANDWICH, and this runtime turns
 * a player standing on that sandwich into the collapse.
 *
 * <p><b>Where it hangs.</b> {@link #tickPlayer} is called once per survival/adventure polar player from {@code
 * GlobeMod.borderUxTick} (the existing END_SERVER_TICK per-player polar path where cold damage already ticks) --
 * chosen over a fresh mixin because that hook already iterates exactly the right players, already computes the
 * player's latitude, and already skips creative/spectator; a mixin would only re-derive all of it. {@link
 * #processScheduled} is called once per server tick from the same hook to drain the staggered collapse queue.
 *
 * <p><b>Flag-off byte-identical.</b> {@link #tickPlayer} early-returns unless {@link
 * LatitudeV2Flags#GLACIAL_CAVES_V1_ENABLED} AND the world is armed AND the player is in the barrens band, so no
 * step is ever scheduled flag-off; {@link #processScheduled} early-returns on an empty queue, so it is a pure
 * no-op flag-off. All work is server-side and gameplay-time (never worldgen), so there is no chunk byte to
 * protect -- only the placement gate above.
 *
 * <p><b>No RNG. Idempotent under re-trigger. Anti-entombment.</b> The trigger is a pure block-shape match and the
 * stagger is pure distance -- deterministic. The instant a span is claimed, its hidden powder markers are removed
 * FIRST (before any scheduling), which invalidates the {@link SnowCollapseLaw#matchesTriggerSignature signature}
 * for every claimed column: a second trigger (same player next tick, or a second player on an overlapping span in
 * the same tick) can never re-claim a column or double-spawn its falling block. The trigger column and its
 * orthogonal neighbours ({@link SnowCollapseLaw#isAntiEntombColumn}) BREAK to particles only -- never a falling
 * entity -- so tumbling snow can never land on and suffocate the player as they drop the shaft.
 */
public final class SnowCollapseRuntime {

    private SnowCollapseRuntime() {
    }

    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** How far below the powder marker the trigger probe scans for air (need &ge; {@link
     *  SnowCollapseLaw#TRIGGER_MIN_AIR_BELOW_MARKER}); a small ceiling keeps the per-tick block reads bounded. */
    private static final int AIR_SCAN_LIMIT = 12;

    /** Defensive global ceiling on queued steps -- a runaway (many players spamming spans) skips NEW scheduling
     *  rather than growing unbounded. A normal event is &le; 24 falls + 3 audio steps and drains in ~20 ticks. */
    private static final int MAX_QUEUED_STEPS = 4096;

    private static final int KIND_FALL = 0;   // convert the top snow_block into a tumbling FallingBlockEntity
    private static final int KIND_BREAK = 1;   // break the top snow_block to particles only (anti-entombment)
    private static final int KIND_CRACK = 2;   // telegraph: icy crack audio + a little snow sift
    private static final int KIND_WHUMP = 3;   // collapse-start deep whump

    /** One scheduled collapse action at an absolute server tick. Server-thread confined (see class javadoc). */
    private static final class Step {
        final long fireTick;
        final int x;
        final int y;
        final int z;
        final int kind;

        Step(long fireTick, int x, int y, int z, int kind) {
            this.fireTick = fireTick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
        }
    }

    private static final List<Step> STEPS = new ArrayList<>();

    /**
     * Per-player collapse check, called from the polar END_SERVER_TICK loop. Cheap-first gates (flag, armed
     * world, barrens band, block-shape signature at the player's feet); only a genuine sandwich stand triggers
     * the flood-fill + claim + schedule.
     *
     * @param level      the globe overworld (the caller has already confirmed {@code isGlobeOverworld})
     * @param player     the player being ticked
     * @param latDeg     the player's absolute latitude in degrees (already computed by the caller)
     * @param unaffected true for creative/spectator (skipped -- the sketch's "unsuspecting" victim is in survival)
     * @param worldTime  the current server game time (tick)
     */
    public static void tickPlayer(ServerLevel level, ServerPlayer player, double latDeg, boolean unaffected,
                                  long worldTime) {
        if (unaffected || !LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
            return;
        }
        if (com.example.globe.world.LatitudeBiomes.getActiveRadiusBlocks() <= 0) {
            return; // not an armed globe world -- no glacial sandwich exists to collapse
        }
        // Cheap band gate: the sandwich only generates in the barrens fray band, so below its onset there is
        // nothing to hit -- skip the block reads entirely for the common (non-polar) case.
        if (latDeg < PolarBarrensBand.ONSET_DEG) {
            return;
        }

        BlockPos feet = player.blockPosition();
        BlockPos standPos = feet.below();
        if (level.getBlockState(standPos).getBlock() != Blocks.SNOW_BLOCK) {
            return;
        }
        BlockPos markerPos = standPos.below();
        boolean markerIsPowder = level.getBlockState(markerPos).getBlock() == Blocks.POWDER_SNOW;
        int roofSnowY = standPos.getY();
        int airBelow = countAirBelow(level, standPos.getX(), roofSnowY - 2, standPos.getZ());
        if (!SnowCollapseLaw.matchesTriggerSignature(true, markerIsPowder, airBelow)) {
            return;
        }

        // A genuine collapse stand. Flood-fill the contiguous sandwich span at this flush roof height.
        final int triggerX = standPos.getX();
        final int triggerZ = standPos.getZ();
        SnowCollapseLaw.ColumnPredicate probe =
                (x, z) -> isSandwichColumn(level, x, roofSnowY, z);
        List<int[]> span = SnowCollapseLaw.floodFillSpan(probe, triggerX, triggerZ, SnowCollapseLaw.MAX_SPAN_COLUMNS);
        if (span.isEmpty()) {
            return; // race: the sandwich vanished between the feet check and the fill
        }

        // CLAIM FIRST: remove every hidden powder marker in the span, which invalidates the trigger signature for
        // all claimed columns (idempotency + no double-spawn under overlapping concurrent triggers).
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int[] col : span) {
            cursor.set(col[0], roofSnowY - 1, col[1]);
            if (level.getBlockState(cursor).getBlock() == Blocks.POWDER_SNOW) {
                level.setBlock(cursor, AIR, 3);
            }
        }

        if (STEPS.size() + span.size() + 3 > MAX_QUEUED_STEPS) {
            return; // defensive: drop the telegraph/schedule rather than grow the queue unbounded
        }

        // Telegraph: crack now + a second crack mid-window, whump at collapse start (end of telegraph).
        playCrack(level, triggerX, roofSnowY, triggerZ);
        STEPS.add(new Step(worldTime + SnowCollapseLaw.TELEGRAPH_TICKS / 2, triggerX, roofSnowY, triggerZ, KIND_CRACK));
        STEPS.add(new Step(worldTime + SnowCollapseLaw.TELEGRAPH_TICKS, triggerX, roofSnowY, triggerZ, KIND_WHUMP));

        // Schedule each column's conversion, staggered outward from the trigger after the telegraph.
        for (int[] col : span) {
            long fire = SnowCollapseLaw.fireTickFor(worldTime, col[0], col[1], triggerX, triggerZ);
            int kind = SnowCollapseLaw.isAntiEntombColumn(col[0], col[1], triggerX, triggerZ)
                    ? KIND_BREAK : KIND_FALL;
            STEPS.add(new Step(fire, col[0], roofSnowY, col[1], kind));
        }
    }

    /**
     * Drain the staggered collapse queue: fire every step whose tick has arrived. Called once per server tick.
     * A no-op (immediate return) when the queue is empty -- the flag-off / no-collapse common case.
     */
    public static void processScheduled(ServerLevel level, long worldTime) {
        if (STEPS.isEmpty()) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Iterator<Step> it = STEPS.iterator();
        while (it.hasNext()) {
            Step s = it.next();
            if (s.fireTick > worldTime) {
                continue;
            }
            it.remove();
            pos.set(s.x, s.y, s.z);
            switch (s.kind) {
                case KIND_FALL -> convertTopBlock(level, pos, true);
                case KIND_BREAK -> convertTopBlock(level, pos, false);
                case KIND_CRACK -> playCrack(level, s.x, s.y, s.z);
                case KIND_WHUMP -> playWhump(level, s.x, s.y, s.z);
                default -> {
                }
            }
        }
    }

    /**
     * Convert a claimed roof column's top block. Idempotent: only acts if a {@code snow_block} is still there
     * (a player may have mined it, or a prior step already fired). {@code asFallingEntity} true spawns the
     * tumbling {@link FallingBlockEntity} (which itself removes the source block and, on landing, places real
     * snow = the debris pile); false BREAKS it to particles only (the anti-entombment columns above the player).
     */
    private static void convertTopBlock(ServerLevel level, BlockPos.MutableBlockPos pos, boolean asFallingEntity) {
        BlockState here = level.getBlockState(pos);
        if (here.getBlock() != Blocks.SNOW_BLOCK) {
            return;
        }
        if (asFallingEntity) {
            FallingBlockEntity.fall(level, pos.immutable(), here); // removes the block + adds the entity (vanilla idiom)
        } else {
            level.setBlock(pos, AIR, 3);
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.2, 0.3, 0.02);
    }

    private static void playCrack(ServerLevel level, int x, int y, int z) {
        double cx = x + 0.5;
        double cy = y + 0.5;
        double cz = z + 0.5;
        level.playSound(null, cx, cy, cz, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.7f, 1.4f);
        level.playSound(null, cx, cy, cz, SoundEvents.SNOW_BREAK, SoundSource.BLOCKS, 0.5f, 0.9f);
        level.sendParticles(ParticleTypes.SNOWFLAKE, cx, cy, cz, 6, 0.35, 0.1, 0.35, 0.0);
    }

    private static void playWhump(ServerLevel level, int x, int y, int z) {
        double cx = x + 0.5;
        double cy = y + 0.5;
        double cz = z + 0.5;
        level.playSound(null, cx, cy, cz, SoundEvents.SNOW_BREAK, SoundSource.BLOCKS, 1.2f, 0.55f);
        level.playSound(null, cx, cy, cz, SoundEvents.POWDER_SNOW_HIT, SoundSource.BLOCKS, 1.0f, 0.7f);
        level.sendParticles(ParticleTypes.POOF, cx, cy, cz, 18, 0.5, 0.2, 0.5, 0.01);
    }

    /** The runtime's world-closed sandwich probe for {@link SnowCollapseLaw.ColumnPredicate}: snow_block at the
     *  shared flush {@code roofY}, powder_snow directly below, and &ge; the required air beneath the marker. */
    private static boolean isSandwichColumn(ServerLevel level, int x, int roofY, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, roofY, z);
        if (level.getBlockState(p).getBlock() != Blocks.SNOW_BLOCK) {
            return false;
        }
        p.set(x, roofY - 1, z);
        boolean powder = level.getBlockState(p).getBlock() == Blocks.POWDER_SNOW;
        int air = countAirBelow(level, x, roofY - 2, z);
        return SnowCollapseLaw.matchesTriggerSignature(true, powder, air);
    }

    /** Count contiguous air blocks from {@code (x, topY, z)} downward, up to {@link #AIR_SCAN_LIMIT}. */
    private static int countAirBelow(ServerLevel level, int x, int topY, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int count = 0;
        for (int i = 0; i < AIR_SCAN_LIMIT; i++) {
            p.set(x, topY - i, z);
            if (level.getBlockState(p).isAir()) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
