package com.example.globe.mixin.client;

import com.example.globe.client.CompassHud;
import com.example.globe.client.GlobeWarningOverlay;
import com.example.globe.client.EwHazeOverlay;
import com.example.globe.client.ZoneEnterTitleOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void globe$renderOverlay(DrawContext context, float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return;
        }
        EwHazeOverlay.render(context, tickDelta);
        GlobeWarningOverlay.render(context, tickDelta);
        CompassHud.render(context, tickDelta);
        if (client != null && client.getWindow() != null) {
            ZoneEnterTitleOverlay.render(context, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        }
    }
}
