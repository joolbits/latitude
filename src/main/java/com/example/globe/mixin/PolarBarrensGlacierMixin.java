package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarBarrensBand;
import com.example.globe.core.PolarWaterFreezeRule;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * B-9a GLACIER BODY ({@code latitude.polarBarrens.enabled} family, worldgen, NEW CHUNKS ONLY) -- Peetsa
 * 2026-07-16 (TEST 99): the Barrens ground read as "~3 blocks of dressing over granite"; it "should be a
 * very very very thick layer of ice under like 10 blocks at least of snow." This TAIL hook on the surface
 * stage rebuilds every barrens LAND column top-down into a real glacier:
 * <ul>
 *   <li><b>Snow cap</b>: the top {@link PolarBarrensBand#GLACIER_SNOW_CAP_BLOCKS} (10) blocks become
 *       {@code snow_block} -- EXCEPT blocks that are already glacier-family (snow_block / the skin's
 *       {@code powder_snow} pockets / {@code packed_ice}-{@code ice} patches / snow layers), so the
 *       {@code PolarBarrensSurfaceMixin} surface dressing is preserved on top (A4's powder pockets stay).</li>
 *   <li><b>Ice body</b> (S37, Peetsa 2026-07-23, TEST 127: "not nearly enough ice ... caverns almost all
 *       ice until sub-Y0"): below the cap the whole solid column is ice ALL THE WAY DOWN to
 *       {@link PolarBarrensBand#ICE_BODY_FLOOR_Y} (Y0) -- a uniform {@code packed_ice} slab (the carvable
 *       base) with a BOUNDED {@code blue_ice} heart seam ({@link PolarBarrensBand#BLUE_ICE_HEART_THICKNESS_BLOCKS}
 *       thick) tracking the existing wobble-warped blue line
 *       ({@link LatitudeBiomes#polarBarrensBlueIceStartDepthBlocks}, 12-18 blocks below the cap, riding the
 *       SAME depth-wobble field), so deep hearts still read blue as an accent while packed ice dominates.
 *       The seam is bounded ON PURPOSE: {@code blue_ice} is NOT in {@code #overworld_carver_replaceables} but
 *       {@code packed_ice} IS, so an all-blue body would wall the crevasse/tunnel carvers out; the packed
 *       slab keeps them cutting. {@link LatitudeBiomes#polarBarrensGlacierIceBlocks} is retained as the
 *       barrens-column GATE (>0 = barrens) and the blue-line/wobble anchor -- the body's floor is now Y0, not
 *       that thickness. Flag-ON output only, new chunks only.</li>
 *   <li><b>Sub-Y0 diffusion band</b> (S37 -- the S24 permafrost stratum RELOCATED below Y0 now that the body
 *       reaches Y0): a ~{@link PolarBarrensBand#PERMAFROST_BAND_BLOCKS}-block band below Y0 where
 *       stone/deepslate are fingered with {@code packed_ice} -- a per-column reach
 *       ({@link LatitudeBiomes#polarBarrensPermafrostDepthBelowY0}, riding the SAME glacier depth-wobble
 *       field, Art VI) fading from 100% areal density at Y0 to 0 at the band bottom (Y-10), so the deep
 *       caverns read glacial in their walls rather than resuming plain stone. Below the band native rock
 *       resumes, so ore features keep homes -- see the in-method comment for the ore-home trade.</li>
 *   <li><b>The living underground stays alive</b> (the law that bounds the glacier's floor): below the
 *       diffusion band nothing is touched -- native stone/dirt resumes, and ore features (which run AFTER
 *       this, at the features stage, and never target packed_ice) populate the column under the ice exactly
 *       as anywhere else. AIR is never replaced (noise-stage caves thread the ice body = the ice-caves
 *       cathedral) and FLUIDS are never replaced (aquifer water/lava pockets stay), bedrock untouched.</li>
 *   <li><b>Fluid columns skipped</b> entirely (the {@code isSurfaceSkin} land-column clause: WORLD_SURFACE
 *       vs OCEAN_FLOOR split &gt; 1): the frozen SEA is the water-freeze law's domain, not the glacier's.</li>
 * </ul>
 *
 * <p><b>Why a buildSurface TAIL pass and not the {@code setBlockState} substitution hook:</b> the glacier
 * body must convert NOISE-stage stone tens of blocks down, and those blocks were written straight into the
 * chunk sections during noise fill -- they never pass through {@code ProtoChunk.setBlockState}, so the
 * existing {@code PolarBarrensSurfaceMixin} @ModifyVariable structurally cannot reach them. Running at the
 * END of the surface stage sees the finished surface (heightmaps primed, skin substitutions already applied)
 * and still runs BEFORE carvers (ravines/caves carve THROUGH the finished ice -- correct) and features.
 *
 * <p><b>Cost/flag honesty:</b> flag-off returns on the first static check (byte-identical). Flag-on, a whole
 * chunk is skipped by one degree comparison unless it reaches the 86-deg band; in-band the work is one
 * column decision + one top-down pass from the surface to Y0 (S37: ~70-90 block writes per barrens column,
 * up from ~46 -- the body now reaches Y0) plus a &lt;=10-block sub-Y0 diffusion pass. Still one single pass,
 * no per-block allocation (the mutable cursor is reused), paid once per NEW chunk only (existing chunks are
 * never rewritten; the legacy-worldgen pin holds).
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class PolarBarrensGlacierMixin {

    @Unique
    private static final BlockState GLOBE_GLACIER_SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    @Unique
    private static final BlockState GLOBE_GLACIER_PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    @Unique
    private static final BlockState GLOBE_RIVER_SURFACE_ICE = Blocks.ICE.defaultBlockState();
    @Unique
    private static final BlockState GLOBE_GLACIER_BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();

    @Inject(
            method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At("TAIL")
    )
    private void globe$polarBarrensGlacierBody(WorldGenRegion region, StructureManager structureManager,
                                               RandomState randomState, ChunkAccess chunk, CallbackInfo ci) {
        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED) {
            return;
        }
        int radius = LatitudeBiomes.getActiveRadiusBlocks();
        if (radius <= 0) {
            return;
        }
        ChunkPos cp = chunk.getPos();
        // Chunk-level early-out: the chunk's most-poleward row must reach the NEARER of the two onsets this
        // pass serves -- the barrens glacier (86) or the S11(b) solid-river zone (85 minus the 1-deg fray).
        int chunkMaxAbsZ = Math.max(Math.abs(cp.getMinBlockZ()), Math.abs(cp.getMaxBlockZ()));
        double passOnsetDeg = Math.min(PolarBarrensBand.ONSET_DEG,
                PolarWaterFreezeRule.FREEZE_ALL_DEG - PolarWaterFreezeRule.FRAY_HALF_WIDTH_DEG);
        if ((double) chunkMaxAbsZ * 90.0 / radius < passOnsetDeg) {
            return;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinY();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int blockX = cp.getMinBlockX() + lx;
                int blockZ = cp.getMinBlockZ() + lz;
                int worldSurfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz);
                int oceanFloorY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, lx, lz);
                if (worldSurfaceY - oceanFloorY > 1) {
                    // Fluid column. S14(b) UNIVERSAL FREEZE: EVERY non-ocean land-family water column (river,
                    // lake, pond, aquifer exposure) freezes SOLID -- surface down to the glacier-sole freeze
                    // floor, ice family, no surface water left (the owner's "liquid under a frozen lake at 89"
                    // fix). The SEA stays exempt (surface-ice-over-liquid is load-bearing: under-ice swim / the
                    // pole wall / S7 immersion), and cave water BELOW the floor stays liquid for B-9's semi-ice
                    // lakes.
                    globe$freezeLandWaterColumn(chunk, cursor, blockX, blockZ, lx, lz,
                            worldSurfaceY, oceanFloorY, radius);
                    continue;
                }
                int iceBlocks = LatitudeBiomes.polarBarrensGlacierIceBlocks(blockX, blockZ, radius);
                if (iceBlocks <= 0) {
                    continue; // not a barrens column (flag/band/fray) -- bitwise untouched.
                }
                // BLUE-ICE HEART SEAM (B-9 P2 Crew C, TEST 113; S37 BOUNDED): the blue line starts a
                // noise-warped 12-18 blocks INTO the body (LatitudeBiomes.polarBarrensBlueIceStartDepthBlocks,
                // REUSING the exact glacier depth-wobble field that undulates the body -- no new noise,
                // Art VI). S37 caps the blue at BLUE_ICE_HEART_THICKNESS_BLOCKS: it is a compression-band
                // ACCENT, not the whole deep body, so a crevasse/tunnel wall reads snow cap -> packed ice ->
                // blue heart seam -> packed ice down to Y0. CARVER INTERACTION (verified against the 26.2
                // jar's tag JSON): blue_ice is NOT in #minecraft:overworld_carver_replaceables (the tag lists
                // packed_ice + #minecraft:snow; plain ice AND blue ice are absent) but packed_ice IS -- so
                // KEEPING the body predominantly packed_ice is load-bearing: the carvers cut the packed slab
                // and merely deflect off the thin blue seam (an all-blue body would wall them out entirely).
                // Noise-stage caves are UNAFFECTED (air before this pass; air is never replaced), so hollows
                // still thread the ice. BYTE-IDENTITY HONESTY: FLAG-ON output only (latitude.polarBarrens
                // family), NEW CHUNKS ONLY; flag-off exits at the top of this method untouched.
                int blueIceStartDepth = LatitudeBiomes.polarBarrensBlueIceStartDepthBlocks(blockX, blockZ);
                // S14(b) WATERFALLS: a barrens column is land (never ocean), so its flowing-water freeze
                // eligibility is ONE per-column decision on the SAME sea-freeze fray front (freezesFlowing's
                // aboveFreezeFloor arm passed true -- a barrens column is a freeze column). The body loop now
                // scans to Y0 (S37), so a FLOWING (non-source) water block anywhere in the body freezes to an
                // ice cascade; SOURCE water (aquifer pools / the B-9 semi-ice reservoir) is preserved at every
                // depth, so "underground springs untouched" still holds for the sources that matter.
                double columnAbsLatDeg = Math.abs((double) blockZ) * 90.0 / radius;
                boolean columnFlowFreezes = PolarWaterFreezeRule.freezesFlowing(
                        LatitudeV2Flags.POLAR_BARRENS_ENABLED, false, columnAbsLatDeg,
                        LatitudeBiomes.polarSeaFreezeFrayNoise(blockX, blockZ),
                        false, true);
                int capBottomY = worldSurfaceY - PolarBarrensBand.GLACIER_SNOW_CAP_BLOCKS;
                // S37 (Peetsa 2026-07-23, TEST 127: "not nearly enough ice ... caverns almost all ice until
                // sub-Y0"): the ice BODY reaches ALL THE WAY DOWN to Y0 (PolarBarrensBand.ICE_BODY_FLOOR_Y),
                // no longer stopping at a shallow noise-wobbled sole. Below the snow cap the whole solid
                // column becomes a uniform packed_ice slab (the carvable base -- packed_ice is in
                // #overworld_carver_replaceables so crevasses/tunnels still cut it) with a BOUNDED blue_ice
                // heart seam where the existing wobble-warped blue line runs; blue is capped in thickness ON
                // PURPOSE (blue_ice is NOT carver-replaceable, so an all-blue body would wall out the carvers).
                int iceFloorY = Math.max(minY + 1, PolarBarrensBand.ICE_BODY_FLOOR_Y);
                for (int y = worldSurfaceY; y >= iceFloorY; y--) {
                    cursor.set(blockX, y, blockZ);
                    BlockState current = chunk.getBlockState(cursor);
                    // Never replace air (noise caves thread the glacier = ice caves) or bedrock. Fluids:
                    // preserve SOURCE water (aquifer pockets stay liquid), but a FLOWING (non-source) water
                    // block in the freeze zone freezes to a plain-ice cascade (S14(b) waterfalls). In the cap,
                    // also keep everything already glacier-family so the skin mixin's powder pockets / ice
                    // patches survive on top.
                    if (current.isAir()) {
                        continue;
                    }
                    if (!current.getFluidState().isEmpty()) {
                        if (columnFlowFreezes && current.getBlock() == Blocks.WATER
                                && !current.getFluidState().isSource()) {
                            chunk.setBlockState(cursor, GLOBE_RIVER_SURFACE_ICE); // frozen cascade = plain ice
                        }
                        continue; // source water (aquifers) / lava / other fluids stay
                    }
                    Block block = current.getBlock();
                    if (block == Blocks.BEDROCK || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) {
                        continue;
                    }
                    if (y > capBottomY) {
                        if (block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW
                                || block == Blocks.ICE || block == Blocks.SNOW) {
                            continue; // the surface skin's dressing is already right -- preserve it.
                        }
                        chunk.setBlockState(cursor, GLOBE_GLACIER_SNOW_BLOCK);
                    } else {
                        // S37 body: packed_ice base wall-to-wall down to Y0, with a BOUNDED blue_ice heart
                        // seam of BLUE_ICE_HEART_THICKNESS_BLOCKS starting at the wobble-warped blue line
                        // (depth is 0-based at the first body block under the cap). The old open-ended
                        // "blue below the line" law would turn the whole deep body blue -> uncarvable; the
                        // cap keeps blue an accent and packed ice the carvable majority. A pre-existing
                        // packed/blue block was skipped above -- idempotent, vanishingly rare here.
                        int depthBelowCap = capBottomY - y;
                        boolean blueHeart = depthBelowCap >= blueIceStartDepth
                                && depthBelowCap < blueIceStartDepth + PolarBarrensBand.BLUE_ICE_HEART_THICKNESS_BLOCKS;
                        // S38 SPECKLE (Peetsa 2026-07-23, TEST 128: "notice how uniform everything looks"): the
                        // non-heart body is no longer a monolithic packed slab -- a deterministic per-block hash
                        // (world seed + salt, Art VI) flecks it with ~7% snow_block pockets and ~5% blue_ice
                        // glints. Both speckle materials keep the carve story honest: snow_block IS carver-
                        // replaceable, and blue flecks are bounded % just like the heart seam -- packed_ice
                        // stays the overwhelming carvable majority.
                        BlockState bodyState;
                        if (blueHeart) {
                            bodyState = GLOBE_GLACIER_BLUE_ICE;
                        } else {
                            double speck = LatitudeBiomes.polarBarrensBodySpeckle01(blockX, y, blockZ);
                            if (speck < 0.05) {
                                bodyState = GLOBE_GLACIER_BLUE_ICE;
                            } else if (speck < 0.12) {
                                bodyState = GLOBE_GLACIER_SNOW_BLOCK;
                            } else {
                                bodyState = GLOBE_GLACIER_PACKED_ICE;
                            }
                        }
                        chunk.setBlockState(cursor, bodyState);
                    }
                }
                // S37 SUB-Y0 ICE DIFFUSION (Peetsa 2026-07-23: "sub-Y0, where there should be about a 10 block
                // diffusion of the ice into stone/deepslate"): the S24 permafrost stratum RELOCATED -- with the
                // body now solid to Y0, the transition band hangs below Y0. From Y-1 down it grafts packed-ice
                // fingering onto the stone/deepslate matrix, its per-column REACH riding the SAME glacier
                // depth-wobble field (LatitudeBiomes.polarBarrensPermafrostDepthBelowY0 -> the
                // POLAR_BARRENS_GLACIER_SALT sample, Art VI: NO new noise), the areal ice fraction fading from
                // 100% at Y0 to 0 at Y-10 -- irregular fingers of varying depth (deepest where the glacier is
                // thickest), never a flat slab. STRICTLY REPLACE-SOLID-ONLY: AIR (caves thread the diffusion =
                // ice-walled caverns) and FLUIDS (aquifer pockets / the B-9 semi-ice cave-lake reservoir) are
                // NEVER replaced; only stone-family walls (globe$isPermafrostReplaceable, which covers
                // deepslate) turn to ice; bedrock is not in the block set. ORE-HOME TRADE: packed_ice is not
                // #minecraft:base_stone_overworld, so ore features (features stage, later, target base stone)
                // cannot home in the ice; every ore Y-range BELOW the ~10-block band is untouched native rock,
                // and within the band high-noise columns give shallow reach. Flag-ON output only (same
                // latitude.polarBarrens family + isBarrens gate as the body), NEW CHUNKS ONLY; flag-off exits
                // at the top of the method, byte-identical.
                int diffusionReach = LatitudeBiomes.polarBarrensPermafrostDepthBelowY0(blockX, blockZ);
                if (diffusionReach > 0) {
                    int diffusionBottomY = Math.max(minY + 1, PolarBarrensBand.ICE_BODY_FLOOR_Y - diffusionReach);
                    for (int y = PolarBarrensBand.ICE_BODY_FLOOR_Y - 1; y >= diffusionBottomY; y--) {
                        cursor.set(blockX, y, blockZ);
                        BlockState current = chunk.getBlockState(cursor);
                        if (current.isAir() || !current.getFluidState().isEmpty()) {
                            continue; // caves thread the diffusion; aquifer/reservoir water stays liquid
                        }
                        if (globe$isPermafrostReplaceable(current.getBlock())) {
                            chunk.setBlockState(cursor, GLOBE_GLACIER_PACKED_ICE);
                        }
                    }
                }
            }
        }
    }

    /**
     * S24/S37: is {@code block} a native stone-family matrix block the sub-Y0 ice-diffusion band may finger
     * into packed ice? The {@code #minecraft:base_stone_overworld} set the noise router fills at these depths
     * (stone / deepslate / the three igneous variants / tuff) plus the surface rule's dirt and gravel. S37
     * moved the band below Y0, squarely in the deepslate zone -- {@code deepslate} is already in the set, so
     * the diffusion fingers deepslate walls too. Deliberately EXCLUDES bedrock, glacier ice ({@code packed_ice}
     * / {@code blue_ice}, already the body), and everything else -- and ores are not yet present at this
     * (surface-tail) stage, so none are ever overwritten.
     */
    @Unique
    private static boolean globe$isPermafrostReplaceable(Block block) {
        return block == Blocks.STONE || block == Blocks.DEEPSLATE || block == Blocks.GRANITE
                || block == Blocks.DIORITE || block == Blocks.ANDESITE || block == Blocks.TUFF
                || block == Blocks.DIRT || block == Blocks.GRAVEL;
    }

    /**
     * S14(b) UNIVERSAL FREEZE -- the generalisation of the S11(b) solid-river pass to ALL land-family water
     * (river, lake, pond, aquifer exposure). Classifies the fluid column by its POPULATED chunk biome (the
     * biomes stage runs before noise/surface, so {@code chunk.getNoiseBiome} is authoritative here) at the
     * water-surface quart: only {@code BiomeTags.IS_OCEAN} is EXEMPT now (the sea keeps surface-ice-over-
     * liquid; every other fluid column freezes). The decision is the pure
     * {@link PolarWaterFreezeRule#freezesLandWaterSolid} (ocean-wins FIRST), zone = the SAME
     * {@code freezesWaterFrayed} front as the surface-ice law, so solid lakes/rivers and the frozen-sea edge
     * agree.
     *
     * <p>Replaces ONLY water blocks in {@code (floorY, worldSurfaceY]} where
     * {@code floorY = }{@link PolarWaterFreezeRule#landWaterFreezeFloorY}: a shallow pond/river freezes to
     * its bed (bed wins -- IDENTICAL to the S13 river freeze, which ran to {@code oceanFloorY}), a deep
     * aquifer exposure stops at the glacier-sole depth cap so cave water below stays LIQUID (the B-9
     * reservation). The top frozen block becomes plain {@code ice} (the familiar frozen-river skin),
     * everything deeper {@code packed_ice} ({@link PolarWaterFreezeRule#riverIceIsPacked} documents the
     * shared solid-freeze ice-kind choice). Non-water states (gravel bed bumps, air gaps, the liquid below
     * the floor) are untouched; the later vanilla freeze feature finds no exposed water and no-ops.
     */
    @Unique
    private static void globe$freezeLandWaterColumn(ChunkAccess chunk, BlockPos.MutableBlockPos cursor,
                                                    int blockX, int blockZ, int lx, int lz,
                                                    int worldSurfaceY, int oceanFloorY, int radius) {
        double absLatDeg = Math.abs((double) blockZ) * 90.0 / radius;
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> columnBiome =
                chunk.getNoiseBiome(blockX >> 2, (worldSurfaceY - 1) >> 2, blockZ >> 2);
        boolean isOcean = columnBiome.is(net.minecraft.tags.BiomeTags.IS_OCEAN);
        if (!PolarWaterFreezeRule.freezesLandWaterSolid(LatitudeV2Flags.POLAR_BARRENS_ENABLED,
                isOcean, absLatDeg,
                LatitudeBiomes.polarSeaFreezeFrayNoise(blockX, blockZ))) {
            return;
        }
        // The water body occupies (oceanFloorY, worldSurfaceY]. Solid-freeze it from the top down to the
        // freeze floor; water at/below the floor (a deep aquifer exposure past the glacier-sole cap) stays
        // liquid, reserved for B-9's semi-ice cave lakes.
        int floorY = PolarWaterFreezeRule.landWaterFreezeFloorY(worldSurfaceY, oceanFloorY);
        for (int y = worldSurfaceY; y > floorY; y--) {
            cursor.set(blockX, y, blockZ);
            BlockState current = chunk.getBlockState(cursor);
            if (current.getBlock() != Blocks.WATER) {
                continue; // only water becomes ice; bed bumps/air pockets stay native.
            }
            chunk.setBlockState(cursor, PolarWaterFreezeRule.riverIceIsPacked(worldSurfaceY - y)
                    ? GLOBE_GLACIER_PACKED_ICE
                    : GLOBE_RIVER_SURFACE_ICE);
        }
    }
}
