package com.example.globe.mixin;

import com.example.globe.world.LatitudeBiomes;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.TreeFeature;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tree line: suppress tree / large-foliage placement above {@link LatitudeBiomes#TREE_LINE_Y} on the
 * Globe overworld, with a smoothstep fade band just below it.
 *
 * <p>This is the load-bearing half of a consistent alpine tree line — making the surface rocky alone
 * does NOT stop trees (they place on whatever grass/dirt exists at the top of vegetated upland biomes,
 * which is why peaks were inconsistently tree-capped). Vetoing the tree feature itself by altitude is
 * biome- and mod-agnostic.</p>
 *
 * <p>{@code require = 0} so a mapping/remap miss degrades gracefully (no tree line, no crash).</p>
 */
@Mixin(TreeFeature.class)
public class TreeLineVegetationGuardMixin {

    @Inject(
            method = "generate(Lnet/minecraft/world/gen/feature/util/FeatureContext;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void globe$treeLineGuard(FeatureContext<TreeFeatureConfig> context,
                                     CallbackInfoReturnable<Boolean> cir) {
        // Only when a Globe world is loaded (ACTIVE_RADIUS_BLOCKS is set at world load).
        if (LatitudeBiomes.ACTIVE_RADIUS_BLOCKS <= 0) {
            return;
        }
        // Overworld only — never trim trees in the Nether/End or modded dimensions.
        try {
            if (context.getWorld().toServerWorld().getRegistryKey() != World.OVERWORLD) {
                return;
            }
        } catch (Throwable ignored) {
            // toServerWorld() unavailable in this gen context — fall through (Globe is overworld-only).
        }

        double suppress = LatitudeBiomes.treeLineSuppression(context.getOrigin().getY());
        if (suppress <= 0.0) {
            return;
        }
        // Canon (Maintainer, 2026-06-08): NO trees above the tree line — cherry blossoms included.
        // The tree line is altitude law; cherry groves get no exemption.
        if (suppress >= 1.0 || context.getRandom().nextDouble() < suppress) {
            cir.setReturnValue(false);
        }
    }
}
