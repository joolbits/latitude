package com.example.globe.mixin.client;

import com.example.globe.client.create.LatitudeCreateWorldScreen;
import com.example.globe.client.create.VanillaCreateWorldHandoff;
import com.example.globe.client.create.VanillaOnlyWorldCreationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenInitRedirectMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("globe");

    @Unique
    private boolean globe$vanillaSession;

    @Shadow
    @Final
    private Runnable onClose;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void globe$redirectRecreateSafely(CallbackInfo ci) {
        LOGGER.info("[LAT][CWPATH] CreateWorldScreenInitRedirectMixin.init screen={}", this.getClass().getName());
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui.screen() != (Object) this) {
            return;
        }

        CreateWorldScreenMixin self = (CreateWorldScreenMixin) (Object) this;
        if (this.globe$vanillaSession) {
            return;
        }

        var handoff = VanillaCreateWorldHandoff.claimNext(this.onClose);
        if (handoff.isPresent()) {
            var payload = handoff.orElseThrow();
            self.getUiState().setName(payload.worldName());
            self.getUiState().setSeed(payload.seed());
            ((VanillaOnlyWorldCreationState) (Object) self.getUiState()).globe$setVanillaOnly(true);
            this.globe$vanillaSession = true;
            LOGGER.info("[LAT][CWPATH] Claimed vanilla create-world handoff for screen={}", this.getClass().getName());
            return;
        }

        Screen parent = globe$getParentSafe((Object) this);
        Runnable onClose = () -> client.setScreenAndShow(parent);

        LatitudeCreateWorldScreen.openLoaded(
                client,
                onClose,
                parent,
                self.getUiState().getSettings(),
                self.getUiState().getSeed(),
                self.getUiState().getName());
        ci.cancel();
    }

    private static Screen globe$getParentSafe(Object self) {
        try {
            Field parentField = self.getClass().getDeclaredField("parent");
            parentField.setAccessible(true);
            Object value = parentField.get(self);
            return value instanceof Screen ? (Screen) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
