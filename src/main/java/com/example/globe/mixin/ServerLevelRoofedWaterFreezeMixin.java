package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarInstrument;
import com.example.globe.core.PolarWaterFreezeRule;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.GlacialTrapRuntimeGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S15/S16/S20 THE SETTLED SWEEP, re-hosted in <b>S22 WATER v6</b> (owner flight TEST 113, 2026-07-19 -- the
 * FOURTH live water failure) onto a DETERMINISTIC {@code ServerLevel.tickChunk} round-robin driver. The PRIMARY
 * polar water-freezer: in the tick front, water under a roof freezes, and any flowing column (pools,
 * post-generation flows, standing cascades) freezes to plain ice ONCE IT HAS SETTLED -- the game's own at-rest
 * definition (no pending scheduled fluid tick). Server-side; a sibling of {@code BiomePolarWaterFreezeMixin}
 * (which waives the temperature veto inside {@code Biome.shouldFreeze} and, since v6, widens the tick-time land
 * front to the barrens band) and of {@code FlowingFluidWaterfallFreezeMixin} (the flow-tick ice-touch hunter)
 * and {@code FlowingFluidSpreadConvertMixin} (the v6 convert-at-spread stopper).
 *
 * <p><b>v6 (b) -- WHY THE DRIVER MOVED OFF tickPrecipitation (the cadence hole).</b> The S20 sweep rode a HEAD
 * inject on {@code ServerLevel.tickPrecipitation}. Bytecode forensics (javap of {@code tickChunk}) showed what
 * that cadence really is: vanilla calls {@code tickPrecipitation} from {@code tickChunk} inside
 * {@code for (i < randomTickSpeed) if (random.nextInt(48) == 0)} -- an expected {@code randomTickSpeed/48 =
 * 3/48 = 1/16} calls per chunk-tick, aimed at ONE random column of the chunk's 256. A SPECIFIC column is
 * therefore visited every {@code 16 * 256 / 20 =} <b>~205 seconds</b> on average, while a pour's spread front
 * advances ~4 cells/s per front cell -- the sweep was 2-3 orders of magnitude behind the water, which is
 * exactly the owner's video: tens of blocks flooded in 12 s, 20-30% checkerboard at 36 s. The v6 driver
 * injects at {@code ServerLevel.tickChunk(LevelChunk, int)} (javap-verified signature) and sweeps
 * {@link PolarWaterFreezeRule#SWEEP_COLUMNS_PER_CHUNK_TICK K=8} columns per in-band chunk per tick, chosen
 * round-robin by {@link PolarWaterFreezeRule#sweepColumnIndex (gameTime*K + i) mod 256} -- NO RNG, and every
 * column of every in-band chunk is visited once per {@link PolarWaterFreezeRule#SWEEP_FULL_COVERAGE_TICKS 32
 * ticks} (1.6 s): ~128x the old cadence, deterministically, at a flat bounded cost (8 short column visits per
 * in-band chunk-tick; out-of-band chunks pay ONE latitude compare).
 *
 * <p><b>v6 (a2) -- THE UPWARD SCAN WAS DEAD CODE (deleted here).</b> The S17 upward scan assumed "water is not
 * motion-blocking terrain" so a cascade stood ABOVE the {@code MOTION_BLOCKING} heightmap top. FALSE: the 26.2
 * {@code MOTION_BLOCKING} predicate is javap-verified as {@code blocksMotion() || !getFluidState().isEmpty()}
 * -- FLUIDS COUNT. {@code getHeightmapPos(MOTION_BLOCKING)} sits exactly ONE block above the topmost water
 * (cascade or pool alike), so a loop starting at {@code top.getY() + 1} began TWO above the water and scanned
 * pure air; it never froze a block in any flight. Its real job belongs to the DOWNWARD descent -- the column
 * surface {@code top.below()} IS the cascade top -- which had its own hole: it stopped at the 16-block roof
 * reach and froze only landed blocks, so any fall taller than 16 was unreachable (the owner's tall pours). v6
 * deletes the scan and extends the descent: once flowing water is found within the roof reach, the walk
 * continues WHILE the blocks are flowing water, down to the fall's LANDED base, capped at
 * {@link PolarWaterFreezeRule#FLOWING_DESCENT_CAP_BLOCKS 48} below the top; the contiguous SETTLED run above
 * that base (up to {@link PolarWaterFreezeRule#SWEEP_MAX_PER_COLUMN 8} blocks) then freezes bottom-up --
 * CERTAIN, no dice.
 *
 * <p><b>v6 (a) -- THE TICK FRONT (82 then, 80 since S25).</b> The old gate ({@code FREEZE_ALL_DEG - FRAY = 84})
 * left the 82-84 Barrens/crevasse band -- where the B-9 crevasses expose water -- with NO machinery (the
 * owner's 84S pour froze nothing); v6 moved the front to the barrens band (82->84). S25 (owner TEST 117:
 * "should at least start from eighty degrees") decoupled it onto its own ramp: the driver rides
 * {@link PolarWaterFreezeRule#tickFrontFreezes} -- ONSET 80 -> FULL 82 (= the barrens onset, KEEP-SHARED) on
 * the SAME coherent barrens fray noise field the biome placement, glacier body, and crevasse carvers use --
 * one noise field, Art VI. Tick-time only; worldgen keeps the 85 law.
 *
 * <p><b>What one column visit does.</b> Only in the tick front, per round-robin slot:
 * <ol>
 *   <li>If the {@code MOTION_BLOCKING} surface ({@code top.below()}) is open SOURCE water: return -- vanilla's
 *       own {@code tickPrecipitation} freeze owns the open still-water skin (edge-inward cadence, and the
 *       S21(d) source-freezes-last veto rides its {@code shouldFreeze} call), byte-identical.</li>
 *   <li>Otherwise descend up to {@link PolarWaterFreezeRule#ROOFED_FREEZE_REACH_BLOCKS 16} blocks looking for
 *       the topmost water. A roofed SOURCE surface goes to vanilla's own {@code Biome.shouldFreeze}
 *       (genuine-water / light / edge checks intact, temperature veto waived in-zone, source-last veto
 *       included) -- the S15 sky waiver. Water sealed deeper than the reach stays liquid (the B-9
 *       reservoir).</li>
 *   <li>A FLOWING block starts the extended descent: walk down while flowing water (cap 48 below the top) to
 *       the landed base, then freeze the contiguous LANDED + SETTLED bottom-up run, up to 8 blocks -- the
 *       S21(d) pacing cap. "Settled" = no pending scheduled fluid tick under either water key (see
 *       {@link #globe$settled}); the run is discovered READ-ONLY first so this sweep's own
 *       {@code setBlockAndUpdate} reschedules can never pollute the settled reads (the S21 purity rule). A
 *       still-falling base (air/fluid below -- including a fall that out-runs the 48 cap) or a still-spreading
 *       block terminates the pass: falls run free, the spread is never frozen mid-motion.</li>
 * </ol>
 *
 * <p><b>Sacred exemptions, verbatim.</b> Ocean-family columns never flow-freeze
 * ({@link PolarWaterFreezeRule#tickFreezesFlowing} checks ocean FIRST -- liquid under the pack ice, the S7
 * immersion mechanic); blocks at/below the {@link PolarWaterFreezeRule#landWaterFreezeFloorY freeze floor}
 * (glacier sole, surface-40) are never frozen however deep the walk goes (the B-9 semi-ice cave reservoir);
 * source blocks are never frozen here (vanilla's {@code shouldFreeze} + the source-last veto own them).
 * {@code POLAR_WATER_FREEZE_ENABLED} off, a non-globe world, or an out-of-band chunk: this injector does
 * nothing. {@code POLAR_BARRENS_ENABLED} off: the FLOWING machinery and the 80 tick front are dormant (moving
 * water is vanilla's, the pre-v6 barrens-off behaviour) but the S15 roofed-SOURCE sky waiver still runs on
 * the razor 85 front, exactly as it always did. The injector NEVER cancels vanilla; it only adds ice.
 *
 * <p><b>Instrument heartbeat.</b> The {@code -Dlatitude.debugFreeze} recorder's {@code pollFreezeLine} moved
 * here with the driver (it fires every tick for every in-band chunk -- a stronger heartbeat than the old
 * random tickPrecipitation host; multiple polls per tick are fine, the window flushes at most once). The
 * counters ({@code sweptSettled}/{@code sweptFroze}) keep their meanings.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelRoofedWaterFreezeMixin {

    /**
     * Vanilla precipitation targets MOTION_BLOCKING, which can see through both S36 powder blocks and place a
     * snow layer inside the fall column or directly over the landing cushion. Cancel only a column whose full
     * authored roof/fall/cushion signature is currently intact; ordinary polar snowfall remains untouched.
     */
    @Inject(
            method = "tickPrecipitation(Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void globe$protectGlacialTrapFromPrecipitation(BlockPos pos, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        BlockPos target = self.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        if (GlacialTrapRuntimeGuard.protectsAuthoredCushion(self, target)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
            at = @At("HEAD"),
            require = 1
    )
    private void globe$sweepPolarWaterColumns(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        // Byte-identical fast bail: kill switch off or a non-globe world -> vanilla only, no latitude math.
        // (POLAR_BARRENS_ENABLED is NOT required here: with barrens off the sweep still owes the S15 roofed
        // SOURCE sky-waiver at the razor 85 front, exactly as pre-v6 -- only the FLOWING machinery is
        // barrens-gated, see globe$sweepColumn.)
        if (!LatitudeV2Flags.POLAR_WATER_FREEZE_ENABLED || LatitudeBiomes.getActiveRadiusBlocks() <= 0) {
            return;
        }
        ServerLevel self = (ServerLevel) (Object) this;
        ChunkPos chunkPos = chunk.getPos();
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        // CHEAP CHUNK GATE: the chunk's poleward-most edge decides whether any of its 256 columns can possibly
        // reach the active onset -- the 80 tick front (S25) with barrens on, the razor 85 with barrens off (the
        // pre-v6 sky-waiver front). tickChunk runs for every naturally-ticking chunk in the world every tick,
        // so everything equatorward pays exactly this one compare and nothing else.
        double chunkOnsetDeg = LatitudeV2Flags.POLAR_BARRENS_ENABLED
                ? PolarWaterFreezeRule.TICK_FRONT_ONSET_DEG
                : PolarWaterFreezeRule.FREEZE_ALL_DEG;
        double edgeLatA = LatitudeMath.absLatDegExact(self.getWorldBorder(), minBlockZ);
        double edgeLatB = LatitudeMath.absLatDegExact(self.getWorldBorder(), minBlockZ + 15);
        double chunkMaxAbsLat = Math.max(edgeLatA, edgeLatB);
        if (Double.isNaN(chunkMaxAbsLat) || chunkMaxAbsLat < chunkOnsetDeg) {
            return;
        }
        // S20 FREEZE recorder heartbeat (default off, static-final gate -> dead-code when unset). v6: hosted on
        // the driver -- fires every tick for every in-band chunk; pollFreezeLine flushes at most once per window.
        if (PolarInstrument.FREEZE) {
            String line = PolarInstrument.pollFreezeLine(self.getGameTime());
            if (line != null) {
                GlobeMod.LOGGER.info(line);
            }
        }
        // v6 (b) THE ROUND-ROBIN: K=8 columns per chunk per tick, index (gameTime*K + i) mod 256 -- pure tick
        // arithmetic (no RNG), full 16x16 coverage every 32 ticks (1.6 s), save/load-stable. Unpack the packed
        // index as x = idx & 15, z = idx >> 4.
        long gameTime = self.getGameTime();
        for (int i = 0; i < PolarWaterFreezeRule.SWEEP_COLUMNS_PER_CHUNK_TICK; i++) {
            int idx = PolarWaterFreezeRule.sweepColumnIndex(gameTime, i);
            globe$sweepColumn(self, minBlockX + (idx & 15), minBlockZ + (idx >> 4));
        }
    }

    /**
     * One column visit of the settled sweep (see the class javadoc's "what one column visit does"). Bounded:
     * one latitude compare + (in-front only) one heightmap read + a descent of at most
     * {@link PolarWaterFreezeRule#FLOWING_DESCENT_CAP_BLOCKS} blocks + at most
     * {@link PolarWaterFreezeRule#SWEEP_MAX_PER_COLUMN} block writes.
     */
    private static void globe$sweepColumn(ServerLevel self, int blockX, int blockZ) {
        // Per-COLUMN latitude gate: the chunk gate above used the poleward edge, so a band-straddling chunk
        // still filters its equatorward columns here. Barrens ON: the S25 TICK FRONT -- the dedicated
        // ONSET 80 -> FULL 82 ramp (full = the barrens onset, KEEP-SHARED; owner TEST 117 "should at least
        // start from eighty degrees") on the SAME coherent barrens fray field the biome/glacier/carvers use;
        // the fray sample is skipped at/above TICK_FRONT_FULL_DEG where the ramp is 1.0 (any sample
        // passes). Barrens OFF: the pre-v6 razor 85 sky-waiver front (freezeIgnoresSky), roofed-SOURCE only.
        boolean barrensOn = LatitudeV2Flags.POLAR_BARRENS_ENABLED;
        double absLatDeg = LatitudeMath.absLatDegExact(self.getWorldBorder(), blockZ);
        double barrensFray = 0.0;
        boolean frontActive;
        if (barrensOn) {
            if (Double.isNaN(absLatDeg) || absLatDeg < PolarWaterFreezeRule.TICK_FRONT_ONSET_DEG) {
                return;
            }
            barrensFray = absLatDeg < PolarWaterFreezeRule.TICK_FRONT_FULL_DEG
                    ? LatitudeBiomes.polarBarrensFrayNoise(blockX, blockZ)
                    : 0.0;
            frontActive = PolarWaterFreezeRule.tickFrontFreezes(true, absLatDeg, barrensFray);
        } else {
            frontActive = PolarWaterFreezeRule.freezeIgnoresSky(true, absLatDeg);
        }
        if (!frontActive) {
            return; // fray-losing 82-84 column (barrens on) or equatorward of 85 (barrens off): no machinery
        }
        // MOTION_BLOCKING counts fluids (v6-verified), so top.below() IS the topmost water block when the
        // column holds any -- a cascade's crest and a pool's skin alike.
        BlockPos top = self.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(blockX, 0, blockZ));
        BlockPos surface = top.below();
        if (self.getFluidState(surface).getType() == Fluids.WATER) {
            // Open SOURCE water surface. Pre-TEST-115 this deferred entirely to vanilla's tickPrecipitation
            // cadence (~205 s/column average) -- which read live as "the source NEVER freezes" (owner,
            // TEST 115 flight: "the source water always stays liquid. Can we make the source freeze after
            // all the other children of the source freeze?"). S23: the driver now CLAIMS an eligible open
            // source on its own 32-tick cadence -- but strictly LAST: the S21(d) postpone veto stands, so
            // while ANY of the six neighbours is live flowing water (children still unfrozen) the source
            // waits. Ocean columns never reach here (eligibility is ocean-exempt: the sea skin keeps
            // vanilla's cadence and the sacred under-ice liquid), and the settled probe keeps the claim
            // off actively-refilling sources. Vanilla's own edge-inward freeze continues in parallel --
            // same plain-ice outcome, ours is just deterministic and prompt.
            if (barrensOn && globe$openSourceFreezes(self, top, surface, absLatDeg, barrensFray)) {
                self.setBlockAndUpdate(surface.immutable(), Blocks.ICE.defaultBlockState());
                PolarInstrument.freezeSourceFroze();
            }
            return; // barrens off: open still-water skin stays vanilla's, byte-identical to pre-v6
        }
        int minY = self.getMinY();
        int reachFloorY = Math.max(minY, top.getY() - PolarWaterFreezeRule.ROOFED_FREEZE_REACH_BLOCKS);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(blockX, 0, blockZ);
        // PHASE 1 -- find the topmost water within the roof reach (the S15 sky waiver's shelter scale). Solids
        // and air are walked through; water sealed deeper than the reach stays liquid (the B-9 reservoir).
        int flowTopY = Integer.MIN_VALUE;
        for (int y = surface.getY(); y >= reachFloorY; y--) {
            cursor.setY(y);
            FluidState fs = self.getFluidState(cursor);
            if (fs.getType() == Fluids.WATER) {
                // Roofed SOURCE water surface (sky-waiver): let vanilla's own shouldFreeze decide -- which also
                // carries the S21(d) SOURCE-FREEZES-LAST veto (BiomePolarWaterFreezeMixin), so a source still
                // touching live flowing water postpones here too, for free.
                Biome biome = self.getBiome(cursor).value();
                if (biome.shouldFreeze(self, cursor)) {
                    self.setBlockAndUpdate(cursor.immutable(), Blocks.ICE.defaultBlockState());
                }
                return; // topmost water surface handled (a surface skin; the body below is S14/worldgen's job)
            }
            if (fs.getType() == Fluids.FLOWING_WATER) {
                flowTopY = y;
                break;
            }
        }
        if (flowTopY == Integer.MIN_VALUE) {
            return; // dry column (or water sealed below the roof reach -- the B-9 reservoir)
        }
        // v6 (a2) PHASE 2 -- THE EXTENDED DESCENT: from the flowing top, keep walking down WHILE the blocks are
        // flowing water, past the old 16-block wall, to the fall's landed base -- capped at 48 below the
        // heightmap top so the worst case stays bounded. (The old descent stopped at the roof reach, so falls
        // taller than 16 could never reach their landed base and never froze -- the TEST-113 (a2) hole.)
        int hardFloorY = Math.max(minY, top.getY() - PolarWaterFreezeRule.FLOWING_DESCENT_CAP_BLOCKS);
        int baseY = flowTopY;
        while (baseY - 1 >= hardFloorY) {
            cursor.setY(baseY - 1);
            if (self.getFluidState(cursor).getType() != Fluids.FLOWING_WATER) {
                break;
            }
            baseY--;
        }
        // S18 LANDED law at the base: air below = still falling (falls run free -- vanilla finishes the drop);
        // fluid below = pouring onto more water (a source pool's skin is vanilla's; a deeper flowing column past
        // the 48 cap waits for a later pass once its upper reaches settle and freeze). Note a cap-terminated
        // walk lands here naturally: the block below the cap boundary is flowing water -> not landed -> pass.
        cursor.setY(baseY - 1);
        BlockState belowBase = self.getBlockState(cursor);
        if (!PolarWaterFreezeRule.landedOnSupport(belowBase.isAir(), !belowBase.getFluidState().isEmpty())) {
            return;
        }
        // Per-column FLOWING eligibility -- barrens-gated (with barrens off, flowing water is exactly as
        // pre-v6: untouched by the sweep; only the roofed-source branch above ran). Then the ocean exemption
        // (FIRST, inside tickFreezesFlowing -- the sacred pin) + the freeze floor (glacier sole, surface-40):
        // the BASE must sit above the floor or the whole run is the B-9 deep reservoir's (run members are
        // higher than the base, so base-above-floor covers the run).
        if (!barrensOn) {
            return; // flowing machinery is the barrens family's; flag-off leaves moving water to vanilla
        }
        int worldSurfaceY = self.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ);
        int oceanFloorY = self.getHeight(Heightmap.Types.OCEAN_FLOOR, blockX, blockZ);
        boolean aboveFreezeFloor =
                baseY > PolarWaterFreezeRule.tickLandWaterFreezeFloorY(worldSurfaceY, oceanFloorY);
        boolean isOcean = self.getBiome(top).is(BiomeTags.IS_OCEAN);
        if (!PolarWaterFreezeRule.tickFreezesFlowing(true, isOcean, absLatDeg, barrensFray, aboveFreezeFloor)) {
            return; // ocean cascade / below-floor spring: liquid, verbatim exemptions
        }
        // PHASE 3 -- READ-ONLY run discovery (the S21 settled-purity rule): find the contiguous bottom-up run
        // of SETTLED flowing blocks BEFORE any write, because our own setBlockAndUpdate below reschedules fluid
        // ticks on neighbours and would make a later settled read lie. Run cap = SWEEP_MAX_PER_COLUMN (8), the
        // S21(d) pacing law; a still-spreading block (pending tick under either water key) terminates the run.
        int runLen = 0;
        for (int y = baseY;
             y <= flowTopY && runLen < PolarWaterFreezeRule.SWEEP_MAX_PER_COLUMN;
             y++) {
            cursor.setY(y);
            if (self.getFluidState(cursor).getType() != Fluids.FLOWING_WATER || !globe$settled(self, cursor)) {
                break;
            }
            if (PolarInstrument.FREEZE) {
                PolarInstrument.freezeSweptSettled();
            }
            runLen++;
        }
        if (runLen == 0) {
            return; // the base itself is still spreading -- the sweep never claims moving water
        }
        // PHASE 4 -- freeze the run bottom-up (certain, no dice). Each layer becomes the support -- and the
        // ice-touch contact -- for the water above it, so the hunter's zipper and the spread-converter take
        // over between sweep visits. The pure decision is sweepFreezesSettled with all three gates true by
        // construction here (eligibility checked above, base landed, run settled).
        if (!PolarWaterFreezeRule.sweepFreezesSettled(true, true, true)) {
            return; // structurally unreachable; kept so the pure decision stays the single authority
        }
        for (int k = 0; k < runLen; k++) {
            cursor.setY(baseY + k);
            self.setBlockAndUpdate(cursor.immutable(), Blocks.ICE.defaultBlockState()); // one climbing layer
            if (PolarInstrument.FREEZE) {
                PolarInstrument.freezeSweptFroze();
            }
        }
    }

    /**
     * S20 SETTLED gate: is this fluid block at rest -- i.e. has the game stopped scheduling its fluid tick? A
     * still-spreading fluid keeps RESCHEDULING its tick, so a pending tick means "still moving" and the sweep must
     * leave it alone. The 26.2 tick container matches a scheduled tick's fluid type by REFERENCE IDENTITY
     * ({@code ScheduledTick.UNIQUE_TICK_HASH}), and flowing water is scheduled under TWO different singletons
     * depending on the path: {@code Fluids.FLOWING_WATER} via {@code FlowingFluid.spreadTo}, and {@code Fluids.WATER}
     * via {@code LiquidBlock.onPlace} (the water {@code LiquidBlock} holds {@code Fluids.WATER}). So we probe BOTH
     * keys; SETTLED iff NEITHER has a pending tick. This can only ever make the sweep MORE conservative about
     * declaring a block at-rest, so it never freezes actively-spreading water. Both probes are O(1) hash lookups.
     */
    private static boolean globe$settled(ServerLevel self, BlockPos pos) {
        return !self.getFluidTicks().hasScheduledTick(pos, Fluids.FLOWING_WATER)
                && !self.getFluidTicks().hasScheduledTick(pos, Fluids.WATER);
    }

    /**
     * S23 OPEN-SOURCE claim eligibility (owner, TEST 115: "make the source freeze after all the other
     * children of the source freeze — so that it happens last"). The caller has already established: the
     * tick front is active, the flag family is on, and {@code surface} holds SOURCE water at the column's
     * MOTION_BLOCKING top. Three gates, in cheap-first order:
     * <ol>
     *   <li><b>Eligibility</b> — the same ocean-first + freeze-floor predicate the flowing machinery uses
     *       ({@link PolarWaterFreezeRule#tickFreezesFlowing}): the sacred SEA skin keeps vanilla's cadence
     *       (under-ice liquid untouched), and a source deeper than the tick floor is the B-9 reservoir's.</li>
     *   <li><b>The S21(d) LAST law</b> — the six-neighbour live-flowing scan (mirrors
     *       {@code BiomePolarWaterFreezeMixin#globe$sourceFreezePostponed}, the shared pure veto
     *       {@link PolarWaterFreezeRule#sourceFreezePostponed}): while ANY child still flows, the source
     *       waits. This is what makes the freeze read as "the fall dies from the bottom up, source last."</li>
     *   <li><b>Settled</b> — the dual-key pending-tick probe: an actively-refilling source (bucket play,
     *       re-route) is left alone until it rests.</li>
     * </ol>
     */
    private static boolean globe$openSourceFreezes(ServerLevel self, BlockPos top, BlockPos surface,
                                                   double absLatDeg, double barrensFray) {
        int worldSurfaceY = self.getHeight(Heightmap.Types.WORLD_SURFACE, surface.getX(), surface.getZ());
        int oceanFloorY = self.getHeight(Heightmap.Types.OCEAN_FLOOR, surface.getX(), surface.getZ());
        boolean aboveFreezeFloor =
                surface.getY() > PolarWaterFreezeRule.tickLandWaterFreezeFloorY(worldSurfaceY, oceanFloorY);
        boolean isOcean = self.getBiome(top).is(BiomeTags.IS_OCEAN);
        if (!PolarWaterFreezeRule.tickFreezesFlowing(true, isOcean, absLatDeg, barrensFray, aboveFreezeFloor)) {
            return false;
        }
        boolean adjacentHasFlowing = false;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            FluidState neighbour = self.getFluidState(surface.relative(dir));
            if (neighbour.getType() == Fluids.FLOWING_WATER) {
                adjacentHasFlowing = true;
                break;
            }
        }
        if (PolarWaterFreezeRule.sourceFreezePostponed(adjacentHasFlowing)) {
            return false;
        }
        return globe$settled(self, surface);
    }
}
