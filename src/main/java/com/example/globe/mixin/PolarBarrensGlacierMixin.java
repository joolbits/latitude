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
 *   <li><b>Ice body</b>: below the cap, {@code packed_ice} down to a noise-varied sole --
 *       {@link LatitudeBiomes#polarBarrensGlacierIceBlocks} = the band-fraction ramp (thin marginal glacier
 *       at the 82-deg frayed edge, 24-36 blocks at/above 84; fray-consistent with the barrens band, same
 *       latitude+fray decision, so the glacier never outruns the biome) wobbled by a dedicated coherent
 *       depth field. <b>B-9 P2 (Crew C, TEST 113):</b> below a noise-warped line 12-18 blocks into the
 *       body ({@link LatitudeBiomes#polarBarrensBlueIceStartDepthBlocks}, riding the SAME depth-wobble
 *       field as the sole) the body is {@code blue_ice} -- depth strata, thick glacier hearts only (the
 *       marginal 6-block body never reaches the line). Flag-ON output only, new chunks only; see the
 *       in-method comment for the carver-interaction consequences (blue ice is structurally uncarvable).</li>
 *   <li><b>Permafrost stratum</b> (S24, Peetsa 2026-07-20, TEST 115: "the ice doesn't extend down very
 *       far. It just becomes regular cave"): a transition band ~{@link PolarBarrensBand#PERMAFROST_BAND_BLOCKS}
 *       blocks below the sole where stone-family blocks are partially cemented to {@code packed_ice} veining
 *       -- a per-column reach ({@link LatitudeBiomes#polarBarrensPermafrostDepthBelowSole}, riding the SAME
 *       glacier depth-wobble field, Art VI) that fades from ~60% areal density at the sole to 0 at the band
 *       bottom, so the Glacial-Caves biome reads glacial in its deep walls rather than resuming plain stone.
 *       The band leaves native stone (partial density) so ore features keep homes -- see the in-method
 *       comment for the ore-home trade.</li>
 *   <li><b>The living underground stays alive</b> (the law that bounds the glacier's floor): below the
 *       permafrost band nothing is touched -- native stone/dirt resumes, and ore features (which run AFTER
 *       this, at the features stage, and never target packed_ice) populate the column under the ice exactly
 *       as anywhere else. AIR is never replaced (noise-stage caves thread the ice body = the free
 *       Glacial-Caves preview) and FLUIDS are never replaced (aquifer water/lava pockets stay), bedrock
 *       untouched.</li>
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
 * column decision + &lt;= ~46 block writes per barrens column -- the same order as the surface stage itself,
 * paid once per NEW chunk only (existing chunks are never rewritten; the legacy-worldgen pin holds).
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
                // B-9 P2 BLUE-ICE DEPTH STRATA (Crew C, owner TEST 113: the underground read as generic
                // stone -- the glacier body gets depth strata): below a noise-warped line 12-18 blocks
                // INTO the packed-ice body the body becomes blue_ice, so a crevasse/tunnel wall reads
                // snow cap -> packed ice -> blue-ice heart top-down, like real compression banding. The
                // line REUSES the exact glacier depth-wobble field that already undulates the sole
                // (LatitudeBiomes.polarBarrensBlueIceStartDepthBlocks -- no new noise, Art VI), so both
                // strata warp together. BYTE-IDENTITY HONESTY: this changes FLAG-ON output only (the
                // latitude.polarBarrens family, same as the glacier body itself) -- flag-off exits at the
                // top of this method untouched; flag-on worlds get blue ice in NEW CHUNKS ONLY.
                // CARVER INTERACTION (verified against the 26.2 jar's tag JSON): blue_ice is NOT in
                // #minecraft:overworld_carver_replaceables (the tag lists packed_ice + #minecraft:snow;
                // plain ice AND blue ice are both absent), so the B-9 carvers cannot cut the blue heart:
                // a crevasse descending past the blue line gets a BLUE-ICE FLOOR (real glaciology --
                // wanted) and glacial tunnels wind through the packed-ice upper body and the stone below
                // the sole but deflect off the heart. Noise-stage caves are UNAFFECTED (they are air
                // before this pass runs, and air is never replaced), so hollows still thread the blue
                // band. Accepted + flagged as owner taste at the P2 flight; the alternative (appending
                // blue_ice to the minecraft tag) would make every vanilla carver cut iceberg blue ice
                // world-wide, a vanilla-behavior change this pass must not smuggle in.
                int blueIceStartDepth = LatitudeBiomes.polarBarrensBlueIceStartDepthBlocks(blockX, blockZ);
                // S14(b) WATERFALLS: a barrens column is land (never ocean), so its flowing-water freeze
                // eligibility is ONE per-column decision on the SAME sea-freeze fray front. Every block the
                // glacier loop scans lies above the sole ((near the 40-block water freeze floor; the sole may sit up to 6 deeper via wobble)), so freezesFlowing's
                // aboveFreezeFloor arm holds by construction here; deep flowing springs below the sole are
                // never scanned and stay liquid ("underground springs below the floor untouched").
                double columnAbsLatDeg = Math.abs((double) blockZ) * 90.0 / radius;
                boolean columnFlowFreezes = PolarWaterFreezeRule.freezesFlowing(
                        LatitudeV2Flags.POLAR_BARRENS_ENABLED, false, columnAbsLatDeg,
                        LatitudeBiomes.polarSeaFreezeFrayNoise(blockX, blockZ),
                        false, true);
                int capBottomY = worldSurfaceY - PolarBarrensBand.GLACIER_SNOW_CAP_BLOCKS;
                int soleY = Math.max(minY + 1, capBottomY - iceBlocks);
                for (int y = worldSurfaceY; y > soleY; y--) {
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
                        // B-9 P2 depth strata: packed ice down to the noise-warped blue line, blue_ice
                        // below it (depth is 0-based at the first body block under the cap). A
                        // pre-existing packed_ice/blue_ice block was skipped above -- idempotent either
                        // way, and vanishingly rare at these depths (features run after this stage).
                        chunk.setBlockState(cursor, (capBottomY - y) >= blueIceStartDepth
                                ? GLOBE_GLACIER_BLUE_ICE
                                : GLOBE_GLACIER_PACKED_ICE);
                    }
                }
                // S24 PERMAFROST STRATUM (Peetsa 2026-07-20, TEST 115 video: "the ice doesn't extend down
                // very far. It just becomes regular cave"): below the glacier sole (the body loop above
                // stopped at soleY+1; soleY is the first native block) graft packed-ice VEINING onto the
                // stone matrix for a transition band, so the Glacial-Caves biome reads glacial well below
                // the body instead of resuming plain stone/dirt. The per-column REACH rides the SAME
                // glacier depth-wobble field (LatitudeBiomes.polarBarrensPermafrostDepthBelowSole -> the
                // POLAR_BARRENS_GLACIER_SALT sample, Art VI: NO new noise), faded from a ~60% areal density
                // AT the sole to 0 at the band bottom -- so the ice extends as irregular fingers of varying
                // depth (deepest where the glacier is thickest), never a flat slab. ORE-HOME TRADE: packed_ice
                // is not #minecraft:base_stone_overworld, so ore features (features stage, later, target base
                // stone) cannot home in the ice; the band leaves them native stone three ways -- ~40% of
                // columns get zero permafrost (their noise >= the top density), every ice column has stone
                // below its reach, and the whole ore Y-range under the ~14-block band is untouched. AIR
                // (noise caves thread the permafrost = ice-walled caverns) and FLUIDS (aquifer pockets /
                // the B-9 semi-ice cave-lake reservoir) are NEVER replaced; bedrock is not in the block set.
                // Flag-ON output only (same latitude.polarBarrens family + isBarrens gate as the body, via
                // polarBarrensPermafrostDepthBelowSole's shared decision), NEW CHUNKS ONLY; flag-off exits at
                // the top of the method, byte-identical.
                int permafrostReach = LatitudeBiomes.polarBarrensPermafrostDepthBelowSole(blockX, blockZ);
                if (permafrostReach > 0) {
                    int permafrostBottomY = Math.max(minY + 1, soleY - permafrostReach + 1);
                    for (int y = soleY; y >= permafrostBottomY; y--) {
                        cursor.set(blockX, y, blockZ);
                        BlockState current = chunk.getBlockState(cursor);
                        if (current.isAir() || !current.getFluidState().isEmpty()) {
                            continue; // caves thread the permafrost; aquifer/reservoir water stays liquid
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
     * S24: is {@code block} a native stone-family matrix block the permafrost stratum may cement into
     * packed ice? The {@code #minecraft:base_stone_overworld} set the noise router fills at these depths
     * (stone / deepslate / the three igneous variants / tuff) plus the surface rule's dirt and gravel that
     * can poke just below a barrens sole. Deliberately EXCLUDES bedrock, glacier ice ({@code packed_ice} /
     * {@code blue_ice}, already the body), and everything else -- and ores are not yet present at this
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
