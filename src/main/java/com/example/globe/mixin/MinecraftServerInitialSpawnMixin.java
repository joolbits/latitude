package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerInitialSpawnMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void globe$setInitialLatitudeSpawn(ServerLevel world,
                                                      ServerLevelData levelData,
                                                      boolean generateBonusChest,
                                                      boolean debugWorld,
                                                      LevelLoadListener loadListener,
                                                      CallbackInfo ci) {
        if (GlobeMod.trySetInitialLatitudeSpawn(world, levelData, generateBonusChest, debugWorld, loadListener)) {
            ci.cancel();
        }
    }
}
