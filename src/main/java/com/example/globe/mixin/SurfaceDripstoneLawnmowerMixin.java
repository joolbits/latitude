package com.example.globe.mixin;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.DripstoneClusterFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LargeDripstoneFeature;
import net.minecraft.world.level.levelgen.feature.PointedDripstoneFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LargeDripstoneFeature.class, DripstoneClusterFeature.class, PointedDripstoneFeature.class})
public class SurfaceDripstoneLawnmowerMixin {

    @Unique
    private static final boolean LATITUDE_FIX_SURFACE_DRIPSTONE =
            Boolean.parseBoolean(System.getProperty("latitude.fixSurfaceDripstone", "true"));

    @Unique
    private static final boolean DEBUG_DRIPSTONE_MOW =
            Boolean.getBoolean("latitude.debugDripstoneLawnmower")
                    || Boolean.getBoolean("latitude.debugDripstoneMow");

    @Unique
    private static final int DRIPSTONE_SURFACE_BUFFER =
            Integer.getInteger("latitude.dripstoneSurfaceBuffer", 32);

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("LatitudeBiomes");

    @Unique
    private static final Long2LongOpenHashMap LOGGED_CHUNKS = new Long2LongOpenHashMap();

    static {
        LOGGED_CHUNKS.defaultReturnValue(Long.MIN_VALUE);
    }

    @Inject(method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z", at = @At("HEAD"), cancellable = true)
    private void latitude$cancelSurfaceDripstone(FeaturePlaceContext<?> context, CallbackInfoReturnable<Boolean> cir) {
        if (!LATITUDE_FIX_SURFACE_DRIPSTONE) {
            return;
        }

        BlockPos origin = context.origin();
        int seaLevel = context.level().getSeaLevel();
        int surfaceY = context.level().getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        boolean nearSurfaceByHeightmap = origin.getY() >= surfaceY - DRIPSTONE_SURFACE_BUFFER;
        boolean skyVisible = origin.getY() > seaLevel
                && (context.level().canSeeSky(origin)
                || context.level().canSeeSky(origin.above(2)));
        if (nearSurfaceByHeightmap || skyVisible) {
            if (DEBUG_DRIPSTONE_MOW) {
                logOncePerChunk(origin);
            }
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static void logOncePerChunk(BlockPos origin) {
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFF_FFFFL);
        if (LOGGED_CHUNKS.putIfAbsent(key, System.nanoTime()) != Long.MIN_VALUE) {
            return;
        }
        LOGGER.info("[Latitude] Dripstone mow at x={} y={} z={}", origin.getX(), origin.getY(), origin.getZ());
    }
}
