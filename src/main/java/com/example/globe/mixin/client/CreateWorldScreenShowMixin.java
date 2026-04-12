package com.example.globe.mixin.client;

import com.example.globe.client.create.LatitudeCreateWorldScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenShowMixin {

    @Inject(method = "show(Lnet/minecraft/client/MinecraftClient;Ljava/lang/Runnable;)V", at = @At("TAIL"))
    private static void globe$redirectToLatitudeScreen(MinecraftClient client, Runnable onClose, CallbackInfo ci) {
        Screen parent = client.currentScreen;
        if (client.currentScreen instanceof CreateWorldScreen createWorldScreen) {
            LatitudeCreateWorldScreen.openLoaded(
                    client,
                    onClose,
                    parent,
                    ((CreateWorldScreenMixin) (Object) createWorldScreen).getWorldCreator().getGeneratorOptionsHolder());
        }
    }
}
