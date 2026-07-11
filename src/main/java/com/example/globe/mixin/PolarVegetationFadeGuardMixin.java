package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarVegetationFade;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.BlockColumnFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SimpleBlockFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Polar small-vegetation fade (Peetsa 2026-07-10; {@code latitude.polarVegetationFade.enabled}).
 *
 * <p>Thins surface vegetation toward the pole so the extreme-polar cap reads as bare snow/ice rather
 * than grass/ferns/flowers/sugarcane at 84-86deg. 26.1 removed {@code RandomPatchFeature}; in 26.2 the
 * small-vegetation placements that used to flow through it are re-typed as {@code minecraft:simple_block}
 * ({@link SimpleBlockFeature} -- grass, ferns, single flowers, wildflower, bushes, leaf litter, dead bush)
 * and {@code minecraft:block_column} ({@link BlockColumnFeature} -- sugarcane). This guard reuses the exact
 * interception mechanism the tree guards use ({@code @Inject} HEAD-cancellable on
 * {@code Feature.place(FeaturePlaceContext)} reading {@code context.origin()}), just pointed at those two
 * feature classes. Trees are deliberately NOT included -- {@link TreeLineVegetationGuardMixin} and
 * {@link ExtremePolarVegetationGuardMixin} ({@code TreeFeature}) already own tree suppression.
 *
 * <p>Flag-off is byte-identical: the first line returns before touching anything. The latitude ramp +
 * coherent fray live in {@link LatitudeBiomes#polarVegetationFadeStrips(int, int)} (pure math in
 * {@link com.example.globe.core.PolarVegetationFade}), which also returns "keep" for any non-globe world
 * and for every column below the fade onset.
 *
 * <p><b>Collateral guards (short-circuit order):</b> both feature classes also back placements this fade
 * must NOT touch, so the strip decision is fenced by three gates, cheapest first:
 * <ol>
 *   <li><b>flag</b> -- first statement; flag-off returns before reading anything (byte-identity).</li>
 *   <li><b>dimension</b> -- overworld only. {@link SimpleBlockFeature} also backs nether patches
 *       (crimson_roots, patch_fire, sulfur_pool...); we bail before any latitude/heightmap work when the
 *       feature is generating in a non-overworld dimension.</li>
 *   <li><b>latitude + fray</b> -- {@link LatitudeBiomes#polarVegetationFadeStrips(int, int)}: pure
 *       arithmetic + coherent noise. Returns "keep" (and pays NO heightmap lookup) for every column below
 *       the 78deg onset, so sub-polar columns short-circuit here.</li>
 *   <li><b>surface proximity</b> -- only reached for a would-strip polar column. Both classes ALSO back
 *       lush-cave features (moss / pale moss / spore blossom / small+big dripleaf / cave vines+glow
 *       berries / cave mushrooms) which generate far below the surface; comparing the placement origin Y
 *       against the {@code WORLD_SURFACE_WG} heightmap ({@link PolarVegetationFade#nearSurface(int, int)},
 *       {@link PolarVegetationFade#SURFACE_MARGIN}) keeps the fade to the surface layer so an under-cap
 *       lush cave is never stripped bare.</li>
 * </ol>
 * {@code require = 1}: both {@code place(FeaturePlaceContext)Z} descriptors are present in the 26.2 merged
 * jar (both classes override {@code Feature.place}), so a future remap that drops either target fails loud
 * instead of silently no-opping the guard.
 */
@Mixin({SimpleBlockFeature.class, BlockColumnFeature.class})
public class PolarVegetationFadeGuardMixin {

    @Inject(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void globe$polarVegetationFade(FeaturePlaceContext<?> context,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!LatitudeV2Flags.POLAR_VEGETATION_FADE_ENABLED) {
            return; // flag-off: byte-identical, nothing read
        }
        WorldGenLevel level = context.level();
        // Overworld-only: SimpleBlockFeature also backs nether patches. Cheap early short-circuit.
        if (level.getLevel().dimension() != Level.OVERWORLD) {
            return;
        }
        BlockPos origin = context.origin();
        // Latitude + coherent fray. Pays no heightmap lookup below the 78deg onset (returns false there).
        if (!LatitudeBiomes.polarVegetationFadeStrips(origin.getX(), origin.getZ())) {
            return;
        }
        // Would-strip polar column: only mow placements AT THE LOCAL SURFACE so under-cap lush caves stay.
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        if (PolarVegetationFade.nearSurface(origin.getY(), surfaceY)) {
            cir.setReturnValue(false);
        }
    }
}
