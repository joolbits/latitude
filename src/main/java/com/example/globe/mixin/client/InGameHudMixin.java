package com.example.globe.mixin.client;

import com.example.globe.client.CompassHud;
import com.example.globe.client.EwSandstormOverlayHud;
import com.example.globe.client.GlobeWarningOverlay;
import com.example.globe.client.LatitudeHudStudioScreen;
import com.example.globe.client.ZoneEnterTitleOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {
    @Inject(
        method = "renderMainHud",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"
        )
    )
    private void globe$renderEwHazeBeforeHotbar(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client != null
                && client.screen != null
                && !(client.screen instanceof LatitudeHudStudioScreen)) {
            return;
        }
        EwSandstormOverlayHud.render(context, tickCounter);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void globe$renderOverlay(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client != null
                && client.screen != null
                && !(client.screen instanceof LatitudeHudStudioScreen)) {
            return;
        }
        GlobeWarningOverlay.render(context, tickCounter);
        CompassHud.render(context, tickCounter);
        if (client != null && client.getWindow() != null) {
            ZoneEnterTitleOverlay.render(context, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        }
    }
}
