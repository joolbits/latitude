package com.example.globe.mixin.client;

import com.example.globe.client.create.LatitudeCreateWorldScreen;
import com.example.globe.mixin.client.CreateWorldScreenMixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenRedirectMixin extends Screen {

    @Shadow @Final @Nullable private Screen parent;

    @Unique
    private static boolean globe$redirecting = false;

    protected CreateWorldScreenRedirectMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void globe$redirectToLatitude(CallbackInfo ci) {
        if (globe$redirecting) {
            return;
        }
        globe$redirecting = true;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            LatitudeCreateWorldScreen.openLoaded(
                    client,
                    () -> client.setScreen(this.parent),
                    this.parent,
                    ((CreateWorldScreenMixin) (Object) this).getWorldCreator().getGeneratorOptionsHolder());
            ci.cancel();
        } finally {
            globe$redirecting = false;
        }
    }
}
