package com.example.globe.mixin.client;

import com.example.globe.client.create.RecreatedWorldPresetCarrier;
import com.example.globe.client.create.VanillaCreateWorldUiStateCarrier;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin implements RecreatedWorldPresetCarrier, VanillaCreateWorldUiStateCarrier {
    private static final Logger LOGGER = LoggerFactory.getLogger("globe");
    // [LAT][CWPATH] fires on every ordinary create-screen open; opt-in only (maintainer ruling, 2026-08-18).
    private static final boolean DEBUG_CWPATH = Boolean.getBoolean("latitude.debugCwPath");

    @Unique
    private String globe$recreatedWorldPresetId;

    @Shadow
    public abstract WorldCreationUiState getUiState();

    @Override
    public WorldCreationUiState globe$getUiState() {
        return getUiState();
    }

    @Accessor("recreated")
    public abstract boolean globe$isRecreated();

    @Override
    public void globe$setRecreatedWorldPresetId(String presetId) {
        this.globe$recreatedWorldPresetId = presetId;
    }

    @Override
    public String globe$getRecreatedWorldPresetId() {
        return this.globe$recreatedWorldPresetId;
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void globe$logCreateWorldInit(CallbackInfo ci) {
        if (DEBUG_CWPATH) {
            LOGGER.info("[LAT][CWPATH] CreateWorldScreenMixin.init screen={}", this.getClass().getName());
        }
    }
}
