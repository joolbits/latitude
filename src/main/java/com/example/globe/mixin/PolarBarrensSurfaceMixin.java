package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarBarrensBand;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Phase 5 Slice B-8 Polar Barrens surface ground ({@code latitude.polarBarrens.enabled}). Clone of
 * {@link AlpineSurfaceMixin}'s {@code @ModifyVariable} on {@code ProtoChunk.setBlockState}: inside a
 * barrens column it substitutes the exposed natural ground
 * (grass_block/dirt/coarse_dirt/podzol/mycelium/gravel) with {@code snow_block}, plus coherent pockets of
 * {@code powder_snow} and patches of {@code packed_ice} -- so there is zero dirt showing on the barrens
 * SURFACE, matching the biome's identity. It only SUBSTITUTES ground blocks; it never places the snow
 * CARPET (that is {@code freeze_top_layer}'s job, per design A3 -- a surface mixin cannot lay a carpet
 * layer).
 *
 * <p><b>Surface-skin confinement (coordinator order 2026-07-14: the underground must stay alive).</b>
 * The barrens biome carries snowy_plains' full underground feature subset (ores incl. ore_dirt/ore_gravel,
 * lava lakes, springs, geodes, disks -- only surface vegetation was dropped), and those features write
 * dirt/gravel through this very hook. Substitutions are therefore gated by
 * {@link PolarBarrensBand#isSurfaceSkin(int, int, int)} on the chunk's own worldgen heightmaps
 * ({@code WORLD_SURFACE_WG} / {@code OCEAN_FLOOR_WG} -- the WG pair every ProtoChunk maintains during
 * generation, primed at the end of the noise stage, before any dirt/grass write in the surface stage):
 * only writes within {@link PolarBarrensBand#SURFACE_SKIN_MARGIN_BLOCKS} of the world surface, in
 * NON-fluid columns, are converted. Underground ore_dirt/ore_gravel veins, cave dirt, and underwater
 * floors (ocean/river/pond surface-rule floors, disk_sand/clay/gravel) all stay native, so a player
 * tunneling under the pole finds the same living underground as anywhere else. (This replaces the
 * earlier "the biome has no underground features" confinement argument, which held only while the
 * biome's feature list was freeze_top_layer-only. {@link AlpineSurfaceMixin} needs no such gate because
 * its absolute {@code ALPINE_ROCK_Y} floor plays the same role.)
 *
 * <p><b>Byte-identical flag-off</b>: the first check returns immediately on the static default-off flag;
 * belt-and-suspenders, {@link LatitudeBiomes#polarBarrensSurfaceKind(int, int, int)} re-checks it and
 * returns {@link PolarBarrensBand#SURFACE_KIND_NONE} when the world is not an armed globe world or the
 * column is not barrens.
 *
 * <p><b>Alpine-before-Barrens mixin order -- ACCEPTED as-is (B-8 sweep FIX-3, 2026-07-14).</b> Both this
 * mixin and {@link AlpineSurfaceMixin} hook the same {@code ProtoChunk.setBlockState} argument, and mixin
 * {@code @ModifyVariable} chains apply in registration order, so on a high-altitude barrens column BOTH may
 * inspect the write: above the alpine rock line (~Y168+, {@code ALPINE_ROCK_Y} band) the ALPINE substitution
 * sees the dirt/grass first and converts it to {@code snow_block} -- by the time this mixin's check runs, the
 * state is no longer in its substitution set, so the alpine result WINS and the barrens {@code powder_snow}
 * pockets / {@code packed_ice} patches are simply ABSENT on those high plateaus. Accepted because the
 * outcome is indistinguishable in spirit: the plateau is all-white either way (alpine snow_block vs barrens
 * snow_block; only the rare pocket texture is lost), the overlap is a narrow slice of the 86+ band that also
 * climbs past Y168, and ordering-inverting the two mixins would risk the flight-tested alpine treeline for a
 * texture nicety. Revisit only if a P3 flight actually notices the missing pockets on a high plateau.
 */
@Mixin(ProtoChunk.class)
public abstract class PolarBarrensSurfaceMixin {

    @Unique
    private static final BlockState GLOBE_BARRENS_SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    @Unique
    private static final BlockState GLOBE_BARRENS_POWDER_SNOW = Blocks.POWDER_SNOW.defaultBlockState();
    @Unique
    private static final BlockState GLOBE_BARRENS_PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();

    @ModifyVariable(
            method = "setBlockState",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private BlockState globe$polarBarrensSurface(BlockState state, BlockPos pos) {
        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED || state == null) {
            return state;
        }
        int radius = LatitudeBiomes.ACTIVE_RADIUS_BLOCKS;
        if (radius <= 0) {
            return state;
        }

        Block block = state.getBlock();
        if (block != Blocks.GRASS_BLOCK && block != Blocks.DIRT && block != Blocks.COARSE_DIRT
                && block != Blocks.PODZOL && block != Blocks.MYCELIUM && block != Blocks.GRAVEL) {
            return state;
        }

        // Surface-skin gate BEFORE the noise fields: underground/underwater writes exit on two cheap
        // heightmap reads (see class javadoc -- ore_dirt/ore_gravel/disk writes must stay native).
        ProtoChunk self = (ProtoChunk) (Object) this;
        int worldSurfaceY = self.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ());
        int oceanFloorY = self.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, pos.getX(), pos.getZ());
        if (!PolarBarrensBand.isSurfaceSkin(pos.getY(), worldSurfaceY, oceanFloorY)) {
            return state;
        }

        int kind = LatitudeBiomes.polarBarrensSurfaceKind(pos.getX(), pos.getZ(), radius);
        return switch (kind) {
            case PolarBarrensBand.SURFACE_KIND_SNOW_BLOCK -> GLOBE_BARRENS_SNOW_BLOCK;
            case PolarBarrensBand.SURFACE_KIND_POWDER_SNOW -> GLOBE_BARRENS_POWDER_SNOW;
            case PolarBarrensBand.SURFACE_KIND_ICE -> GLOBE_BARRENS_PACKED_ICE;
            default -> state;
        };
    }
}
