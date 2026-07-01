package com.example.globe.mixin.client;

import com.example.globe.client.create.LatitudeCreateWorldScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenShowMixin {

    @Inject(method = "show(Lnet/minecraft/client/MinecraftClient;Ljava/lang/Runnable;)V", at = @At("TAIL"))
    private static void globe$redirectToLatitudeScreen(Minecraft client, Runnable onClose, CallbackInfo ci) {
        Screen parent = client.screen;
        if (client.screen instanceof CreateWorldScreen createWorldScreen) {
            LatitudeCreateWorldScreen.openLoaded(
                    client,
                    onClose,
                    parent,
                    ((CreateWorldScreenMixin) (Object) createWorldScreen).getUiState().getSettings(),
                    ((CreateWorldScreenMixin) (Object) createWorldScreen).getUiState().getSeed());
        }
    }
}
