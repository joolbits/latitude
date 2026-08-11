package com.example.globe.mixin;

import com.example.globe.world.LatitudeBiomeLocateService;
import com.example.globe.world.LatitudeStructureLocateService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocateCommand.class)
public abstract class LocateCommandMixin {
    @Inject(method = "locateBiome", at = @At("HEAD"), cancellable = true, require = 1)
    private static void globe$runLatitudeWetlandLocateAcrossTicks(
            CommandSourceStack source,
            ResourceOrTagArgument.Result<Biome> target,
            CallbackInfoReturnable<Integer> cir) {
        if (LatitudeBiomeLocateService.beginIfLatitudeBiome(source, target)) {
            cir.setReturnValue(1);
        }
    }

    @Inject(method = "locateStructure", at = @At("HEAD"), cancellable = true, require = 1)
    private static void globe$runLatitudeStructureLocate(
            CommandSourceStack source,
            ResourceOrTagKeyArgument.Result<Structure> target,
            CallbackInfoReturnable<Integer> cir) {
        if (LatitudeStructureLocateService.beginIfApplicable(source, target)) {
            cir.setReturnValue(1);
        }
    }
}
