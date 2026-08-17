package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.BiomeDescriptorLedger;
import com.example.globe.world.LatitudeWorldgenScope;
import com.example.globe.world.SulfurSurfaceExpressionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SequenceFeature;
import net.minecraft.world.level.levelgen.feature.configurations.CompositeFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents the vanilla sulfur-pool sequence from reaching an incompatible
 * surface without removing the sulfur-cave biome or its underground features.
 */
@Mixin(SequenceFeature.class)
public class SulfurPoolSurfaceGuardMixin {
    @Unique
    private static final Identifier SULFUR_POOL_ID =
            Identifier.fromNamespaceAndPath("minecraft", "sulfur_pool");

    @Unique
    private static final boolean DEBUG_SULFUR_SURFACE_GUARD =
            Boolean.getBoolean("latitude.debugSulfurSurfaceGuard");

    @Inject(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void globe$guardSulfurPoolSurface(
            FeaturePlaceContext<CompositeFeatureConfiguration> context,
            CallbackInfoReturnable<Boolean> cir) {
        if (!LatitudeWorldgenScope.isFeatureActive()
                || !(context.chunkGenerator() instanceof NoiseBasedChunkGenerator noise)
                || !GlobeMod.shouldApplyLatitudeWorldgen(noise)) {
            return;
        }

        Identifier topFeatureId = LatitudeWorldgenScope.currentPlacedFeatureId();
        boolean sulfurPool = SULFUR_POOL_ID.equals(topFeatureId);
        if (!sulfurPool) {
            return;
        }

        BlockPos origin = context.origin();
        int surfaceY = context.level().getHeight(
                Heightmap.Types.WORLD_SURFACE_WG,
                origin.getX(),
                origin.getZ());
        BlockPos surfacePos = new BlockPos(origin.getX(), surfaceY, origin.getZ());
        Registry<Biome> biomes = context.level().registryAccess().lookupOrThrow(Registries.BIOME);
        Identifier surfaceBiomeId = biomes.getKey(context.level().getBiome(surfacePos).value());
        boolean surfaceCompatible = surfaceBiomeId != null
                && BiomeDescriptorLedger.supportsSulfurSurfaceExpression(surfaceBiomeId.toString());
        boolean suppress = SulfurSurfaceExpressionPolicy.shouldSuppressPool(
                true,
                origin.getY(),
                surfaceY,
                surfaceCompatible);

        if (DEBUG_SULFUR_SURFACE_GUARD) {
            GlobeMod.LOGGER.info(
                    "[LAT][SULFUR_SURFACE_GUARD] topFeature={} originX={} originY={} originZ={} worldSurfaceWgY={} surfaceDelta={} surfaceBiome={} surfaceCompatible={} decision={}",
                    topFeatureId,
                    origin.getX(),
                    origin.getY(),
                    origin.getZ(),
                    surfaceY,
                    surfaceY - origin.getY(),
                    surfaceBiomeId,
                    surfaceCompatible,
                    suppress ? "CANCEL" : "ALLOW");
        }
        if (suppress) {
            cir.setReturnValue(false);
        }
    }
}
