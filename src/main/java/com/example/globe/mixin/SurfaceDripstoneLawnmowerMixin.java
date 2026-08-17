package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.BiomeDescriptorLedger;
import com.example.globe.world.LatitudeWorldgenScope;
import com.example.globe.world.SulfurSurfaceExpressionPolicy;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LargeDripstoneFeature;
import net.minecraft.world.level.levelgen.feature.SpeleothemClusterFeature;
import net.minecraft.world.level.levelgen.feature.SpeleothemFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LargeDripstoneFeature.class, SpeleothemClusterFeature.class, SpeleothemFeature.class})
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
    private static final Identifier SULFUR_SPIKE_ID =
            Identifier.fromNamespaceAndPath("minecraft", "sulfur_spike");

    @Unique
    private static final Identifier SULFUR_SPIKE_CLUSTER_ID =
            Identifier.fromNamespaceAndPath("minecraft", "sulfur_spike_cluster");

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
        if (!LatitudeWorldgenScope.isActive()
                || !(context.chunkGenerator() instanceof NoiseBasedChunkGenerator noise)
                || !GlobeMod.shouldApplyLatitudeWorldgen(noise)) {
            return;
        }

        BlockPos origin = context.origin();
        int seaLevel = context.level().getSeaLevel();
        int surfaceY = context.level().getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        boolean nearSurfaceByHeightmap = origin.getY() >= surfaceY - DRIPSTONE_SURFACE_BUFFER;
        boolean openToSky = context.level().canSeeSky(origin)
                || context.level().canSeeSky(origin.above(2));
        boolean skyVisible = origin.getY() > seaLevel && openToSky;
        Identifier placedFeatureId = LatitudeWorldgenScope.currentPlacedFeatureId();
        boolean sulfurSpike = SULFUR_SPIKE_ID.equals(placedFeatureId)
                || SULFUR_SPIKE_CLUSTER_ID.equals(placedFeatureId);
        boolean cancel;
        if (sulfurSpike) {
            boolean surfaceVisible = openToSky || origin.getY() >= surfaceY - 1;
            BlockPos surfacePos = new BlockPos(origin.getX(), surfaceY, origin.getZ());
            Registry<Biome> biomes =
                    context.level().registryAccess().lookupOrThrow(Registries.BIOME);
            Identifier surfaceBiomeId =
                    biomes.getKey(context.level().getBiome(surfacePos).value());
            boolean surfaceCompatible = surfaceBiomeId != null
                    && BiomeDescriptorLedger.supportsSulfurSurfaceExpression(
                            surfaceBiomeId.toString());
            cancel = SulfurSurfaceExpressionPolicy.shouldSuppressSpike(
                    true,
                    surfaceVisible,
                    surfaceCompatible);
            if (DEBUG_DRIPSTONE_MOW) {
                LOGGER.info(
                        "[LAT][SULFUR_SPIKE_GUARD] placedFeature={} originX={} originY={} originZ={} worldSurfaceWgY={} surfaceDelta={} surfaceBiome={} surfaceVisible={} surfaceCompatible={} decision={}",
                        placedFeatureId,
                        origin.getX(),
                        origin.getY(),
                        origin.getZ(),
                        surfaceY,
                        surfaceY - origin.getY(),
                        surfaceBiomeId,
                        surfaceVisible,
                        surfaceCompatible,
                        cancel ? "CANCEL" : "ALLOW");
            }
        } else {
            cancel = nearSurfaceByHeightmap || skyVisible;
        }
        if (cancel) {
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
