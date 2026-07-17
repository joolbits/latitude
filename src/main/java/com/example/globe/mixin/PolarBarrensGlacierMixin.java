package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarBarrensBand;
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
 *       at the 86-deg frayed edge, 24-36 blocks at/above 88; fray-consistent with the barrens band, same
 *       latitude+fray decision, so the glacier never outruns the biome) wobbled by a dedicated coherent
 *       depth field.</li>
 *   <li><b>The living underground stays alive</b> (the law that bounds the glacier's floor): below the sole
 *       nothing is touched -- native stone/dirt resumes, and ore features (which run AFTER this, at the
 *       features stage, and never target packed_ice) populate the column under the ice exactly as anywhere
 *       else. AIR is never replaced (noise-stage caves thread the ice body = the free Glacial-Caves preview)
 *       and FLUIDS are never replaced (aquifer water/lava pockets stay), bedrock untouched.</li>
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
        // Chunk-level early-out: the chunk's most-poleward row must reach the barrens onset at all.
        int chunkMaxAbsZ = Math.max(Math.abs(cp.getMinBlockZ()), Math.abs(cp.getMaxBlockZ()));
        if ((double) chunkMaxAbsZ * 90.0 / radius < PolarBarrensBand.ONSET_DEG) {
            return;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinY();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int blockX = cp.getMinBlockX() + lx;
                int blockZ = cp.getMinBlockZ() + lz;
                int iceBlocks = LatitudeBiomes.polarBarrensGlacierIceBlocks(blockX, blockZ, radius);
                if (iceBlocks <= 0) {
                    continue; // not a barrens column (flag/band/fray) -- bitwise untouched.
                }
                int worldSurfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz);
                int oceanFloorY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, lx, lz);
                if (worldSurfaceY - oceanFloorY > 1) {
                    continue; // fluid column: the frozen sea belongs to the water-freeze law, not the glacier.
                }
                int capBottomY = worldSurfaceY - PolarBarrensBand.GLACIER_SNOW_CAP_BLOCKS;
                int soleY = Math.max(minY + 1, capBottomY - iceBlocks);
                for (int y = worldSurfaceY; y > soleY; y--) {
                    cursor.set(blockX, y, blockZ);
                    BlockState current = chunk.getBlockState(cursor);
                    // Never replace air (noise caves thread the glacier = ice caves), fluids (aquifers), or
                    // bedrock. In the cap, also keep everything already glacier-family so the skin mixin's
                    // powder pockets / ice patches survive on top.
                    if (current.isAir() || !current.getFluidState().isEmpty()) {
                        continue;
                    }
                    Block block = current.getBlock();
                    if (block == Blocks.BEDROCK || block == Blocks.PACKED_ICE) {
                        continue;
                    }
                    if (y > capBottomY) {
                        if (block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW
                                || block == Blocks.ICE || block == Blocks.SNOW) {
                            continue; // the surface skin's dressing is already right -- preserve it.
                        }
                        chunk.setBlockState(cursor, GLOBE_GLACIER_SNOW_BLOCK);
                    } else {
                        chunk.setBlockState(cursor, GLOBE_GLACIER_PACKED_ICE);
                    }
                }
            }
        }
    }
}
