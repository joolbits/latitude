package com.example.globe.mixin.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("globe");

    @Shadow
    public abstract WorldCreationUiState getUiState();

    @Inject(method = "init", at = @At("HEAD"))
    private void globe$logCreateWorldInit(CallbackInfo ci) {
        LOGGER.info("[LAT][CWPATH] CreateWorldScreenMixin.init screen={}", this.getClass().getName());
    }
}
