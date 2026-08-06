package com.example.globe.mixin;

import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Materializes only the V2-reserved Mushroom Fields province as an ocean island.
 *
 * <p>One hook, deliberately. 26.2 carried a second {@code @ModifyReturnValue} on
 * {@code getInterpolatedDensity()D}, whose source comment claimed it served height queries. That
 * claim was wrong, verified exhaustively during this port: on 26.2 the only class in the entire jar
 * referencing {@code getInterpolatedNoiseValue} (the sole caller of {@code getInterpolatedDensity}
 * outside {@code NoiseChunk}) is {@code NoiseBasedChunkGenerator}, whose sole internal caller is
 * {@code addDebugScreenInfo} — the F3 overlay's noise readout. Height queries never touched it.
 *
 * <p>Height queries ({@code getBaseHeight}/{@code getBaseColumn} → {@code iterateNoiseColumn}) read
 * {@code getInterpolatedState()} per cell on <em>both</em> versions, and both substitute the
 * generator's default block when it returns null — bytecode-identical at offsets 313/320/335. So
 * this single state hook makes the island solid to block writing ({@code doFill}) and to height
 * prediction alike. 1.21.11 has neither {@code getInterpolatedDensity} nor
 * {@code getInterpolatedNoiseValue}, so the deleted hook has nothing to attach to and nothing to
 * serve; its loss is an unmodified F3 readout on a version whose F3 path no longer exists.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMushroomIslandDensityMixin {
    @ModifyReturnValue(
            method = "getInterpolatedState()Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"))
    private BlockState globe$materializeReservedMushroomIsland(BlockState original) {
        if (!LatitudeWorldgenScope.isActive()) return original;
        NoiseChunk self = (NoiseChunk) (Object) this;
        // Null is the "use the configured default solid block" marker for every consumer of this
        // method: doFill substitutes it when writing blocks, and iterateNoiseColumn substitutes it
        // when answering height queries.
        return LatitudeBiomes.isMushroomIslandSolid(
                self.blockX(), self.blockY(), self.blockZ()) ? null : original;
    }
}
