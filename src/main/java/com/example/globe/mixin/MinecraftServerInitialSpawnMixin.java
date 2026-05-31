package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.level.ServerWorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.server.MinecraftServer.class)
public abstract class MinecraftServerInitialSpawnMixin {
    @Inject(method = "setupSpawn", at = @At("HEAD"), cancellable = true)
    private static void globe$setInitialLatitudeSpawn(
            ServerWorld world,
            ServerWorldProperties worldProperties,
            boolean bonusChest,
            boolean debugWorld,
            CallbackInfo ci
    ) {
        if (GlobeMod.trySetInitialLatitudeSpawn(world, worldProperties, bonusChest, debugWorld)) {
            ci.cancel();
        }
    }
}
