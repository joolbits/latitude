package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.SaveLoader;
import net.minecraft.world.level.storage.LevelStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class ExistingWorldLoadingOverlayStartMixin {
    @Unique
    private static final Logger GLOBE_LOGGER = LoggerFactory.getLogger("LatitudeLoadingOverlay");

    @Inject(
            method = "startIntegratedServer(Lnet/minecraft/world/level/storage/LevelStorage$Session;Lnet/minecraft/resource/ResourcePackManager;Lnet/minecraft/server/SaveLoader;Z)V",
            at = @At("HEAD"))
    private void globe$activateLatitudeOverlayForExistingWorld(LevelStorage.Session session,
                                                               ResourcePackManager dataPackManager,
                                                               SaveLoader saveLoader,
                                                               boolean newWorld,
                                                               CallbackInfo ci) {
        if (newWorld || LatitudeClientState.isLatitudeWorldLoading()) {
            return;
        }
        long now = System.currentTimeMillis();
        LatitudeClientState.beginExpedition(now);
        LatitudeClientState.activateLatitudeLoading();
        GLOBE_LOGGER.info("[Latitude lifecycle] bespoke overlay armed for existing save — 0ms since beginExpedition");
    }
}
