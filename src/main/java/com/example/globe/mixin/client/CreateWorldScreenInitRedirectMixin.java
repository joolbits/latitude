package com.example.globe.mixin.client;

import com.example.globe.client.create.LatitudeCreateWorldScreen;
import com.example.globe.client.create.RecreatedWorldPresetCarrier;
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
    @Shadow
    private boolean recreated;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void globe$redirectRecreateSafely(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.screen != (Object) this) {
            return;
        }

        Screen parent = globe$getParentSafe((Object) this);
        Runnable onClose = () -> client.setScreen(parent);

        WorldCreationUiState initialState = ((CreateWorldScreenMixin) (Object) this).getUiState();
        String recreatedPresetId = ((RecreatedWorldPresetCarrier) this).globe$getRecreatedWorldPresetId();
        if (!LatitudeCreateWorldScreen.canRepresent(initialState, this.recreated, recreatedPresetId)) {
            return;
        }

        LatitudeCreateWorldScreen.openLoaded(
                client, onClose, parent, initialState, this.recreated, recreatedPresetId);
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
