package com.example.globe.mixin;

import com.example.globe.world.LatitudeWorldgenScope;
import com.example.globe.world.WindsweptSnowLinePolicy;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lowers the snow line for the windswept family in Latitude worlds — the placement half of
 * {@link WindsweptSnowLinePolicy} (the guard half is {@code ProtoChunkSnowBlockGuardMixin}).
 *
 * <p>{@code freeze_top_layer} decides snow per position via {@link Biome#shouldSnow}, whose only
 * temperature-dependent gate is {@code getPrecipitationAt}: a temperature-0.2 windswept column
 * below ~seaLevel+57 reports RAIN and never snows. This wrap keeps vanilla's answer whenever it
 * says yes, and otherwise re-asks the question with Latitude's lowered line — replicating
 * shouldSnow's own structural conditions exactly (inside build height, block light below 10,
 * position is air, snow can survive) so the override changes only the temperature verdict, never
 * placement validity. The feature's own code then places the layer AND sets the grass beneath
 * snowy, keeping the block pair coherent.
 */
@Mixin(SnowAndFreezeFeature.class)
public abstract class SnowAndFreezeWindsweptSnowLineMixin {

    @WrapOperation(
            method = "place",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean globe$lowerWindsweptSnowLine(
            Biome biome, LevelReader level, BlockPos pos, Operation<Boolean> original) {
        boolean vanilla = original.call(biome, level, pos);
        if (vanilla || !LatitudeWorldgenScope.isActive()) {
            return vanilla;
        }
        Holder<Biome> holder = level.getBiome(pos);
        String biomeId = holder.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
        if (!WindsweptSnowLinePolicy.appliesTo(biomeId, pos.getY(), level.getSeaLevel())) {
            return false;
        }
        // shouldSnow's structural conditions, minus its temperature gate.
        if (!level.isInsideBuildHeight(pos.getY())
                || level.getBrightness(LightLayer.BLOCK, pos) >= 10) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, pos);
    }
}
