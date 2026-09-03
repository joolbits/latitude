package com.example.globe.mixin;

import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Raises and materializes only the V2-reserved Mushroom Fields province as an ocean island. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseChunkMushroomIslandDensityMixin {
    @ModifyArgs(
            method = "doFill(Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/Aquifer;computeSubstance(IIID)Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 1)
    private void globe$raiseReservedMushroomIslandDuringFill(Args args) {
        globe$raiseReservedMushroomIsland(args);
    }

    @ModifyArgs(
            method = "iterateNoiseColumn(Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;IILorg/apache/commons/lang3/mutable/MutableObject;Ljava/util/function/Predicate;)Ljava/util/OptionalInt;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/Aquifer;computeSubstance(IIID)Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 1)
    private void globe$raiseReservedMushroomIslandDuringHeightQuery(Args args) {
        globe$raiseReservedMushroomIsland(args);
    }

    private static void globe$raiseReservedMushroomIsland(Args args) {
        if (!LatitudeWorldgenScope.isActive()) {
            return;
        }
        int x = args.get(0);
        int y = args.get(1);
        int z = args.get(2);
        double original = args.get(3);
        args.set(3, LatitudeBiomes.mushroomIslandDensity(original, x, y, z));
    }
}
