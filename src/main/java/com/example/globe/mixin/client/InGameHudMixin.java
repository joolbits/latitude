package com.example.globe.mixin.client;

import com.example.globe.client.CompassHud;
import com.example.globe.client.EwSandstormOverlayHud;
import com.example.globe.client.GlobeWarningOverlay;
import com.example.globe.client.LatitudeHudAdjustScreen;
import com.example.globe.client.LatitudeHudStudioScreen;
import com.example.globe.client.LatitudeSettingsScreen;
import com.example.globe.client.ZoneEnterTitleOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHotbar(FLnet/minecraft/client/gui/DrawContext;)V"
        )
    )
    private void globe$renderEwHazeBeforeHotbar(DrawContext context, float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null
                && client.currentScreen != null
                && !(client.currentScreen instanceof LatitudeHudStudioScreen)) {
            return;
        }
        EwSandstormOverlayHud.render(context, new RenderTickCounter(tickDelta, System.currentTimeMillis()));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void globe$renderOverlay(DrawContext context, float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null
                && client.currentScreen != null
                && !(client.currentScreen instanceof LatitudeHudStudioScreen)) {
            return;
        }
        if (client != null && globe$isLatitudeHudConfigScreen(client.currentScreen)) {
            return;
        }
        RenderTickCounter tickCounter = new RenderTickCounter(tickDelta, System.currentTimeMillis());
        GlobeWarningOverlay.render(context, tickCounter);
        CompassHud.render(context, tickCounter);
        if (client != null && client.getWindow() != null) {
            ZoneEnterTitleOverlay.render(context, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        }
    }

    private static boolean globe$isLatitudeHudConfigScreen(Screen screen) {
        return screen instanceof LatitudeHudStudioScreen
                || screen instanceof LatitudeSettingsScreen
                || screen instanceof LatitudeHudAdjustScreen;
    }
}
