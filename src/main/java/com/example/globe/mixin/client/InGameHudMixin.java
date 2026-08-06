package com.example.globe.mixin.client;

import com.example.globe.client.CompassHud;
import com.example.globe.client.EwSandstormOverlayHud;
import com.example.globe.client.GlobeWarningOverlay;
import com.example.globe.client.LatitudeHudStudioScreen;
import com.example.globe.client.ZoneEnterTitleOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {
    // GitHub #7 rule: presentation mixins fail soft. A missed target means a vanilla HUD,
    // never a crash. expect=1 keeps dev boots loud under -Dmixin.debug.strict=true.
    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), require = 0, expect = 1)
    private void globe$renderEwHazeBeforeHotbar(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client != null
                && client.screen != null
                && !(client.screen instanceof LatitudeHudStudioScreen)) {
            return;
        }
        EwSandstormOverlayHud.render(context, tickCounter);
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0, expect = 1)
    private void globe$renderOverlay(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
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
