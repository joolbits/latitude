package com.example.globe.mixin.client;

import com.example.globe.client.create.LatitudeCreateWorldScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenInitRedirectMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("globe");

    @Shadow
    private boolean recreated;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void globe$redirectRecreateSafely(CallbackInfo ci) {
        LOGGER.info("[LAT][CWPATH] CreateWorldScreenInitRedirectMixin.init screen={}", this.getClass().getName());
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.screen != (Object) this) {
            return;
        }

        Screen parent = globe$getParentSafe((Object) this);
        Runnable onClose = () -> client.setScreen(parent);

        WorldCreationUiState initialState = ((CreateWorldScreenMixin) (Object) this).getUiState();
        if (!LatitudeCreateWorldScreen.canRepresent(initialState, this.recreated)) {
            LOGGER.info("[LAT][CWPATH] leaving unsupported create-world preset on vanilla screen: {}",
                    initialState.getWorldType());
            return;
        }

        LatitudeCreateWorldScreen.openLoaded(client, onClose, parent, initialState, this.recreated);
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
