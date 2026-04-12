package com.example.globe.mixin.client;

import com.example.globe.client.create.LatitudeCreateWorldScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenInitRedirectMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void globe$redirectRecreateSafely(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen != (Object) this) {
            return;
        }

        Screen parent = globe$getParentSafe((Object) this);
        Runnable onClose = () -> client.setScreen(parent);

        LatitudeCreateWorldScreen.openLoaded(
                client,
                onClose,
                parent,
                ((CreateWorldScreenMixin) (Object) this).getWorldCreator().getGeneratorOptionsHolder());
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
