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

    @Inject(method = "create(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("TAIL"))
    private static void globe$redirectToLatitudeScreen(MinecraftClient client, Screen parent, CallbackInfo ci) {
        if (client.currentScreen instanceof CreateWorldScreen createWorldScreen) {
            LatitudeCreateWorldScreen.openLoaded(
                    client,
                    () -> client.setScreen(parent),
                    parent,
                    ((CreateWorldScreenMixin) (Object) createWorldScreen).getWorldCreator().getGeneratorOptionsHolder());
        }
    }
}
