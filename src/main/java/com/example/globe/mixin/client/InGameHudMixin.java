package com.example.globe.mixin.client;

import com.example.globe.client.CompassHud;
import com.example.globe.client.EwSandstormOverlayHud;
import com.example.globe.client.GlobeWarningOverlay;
import com.example.globe.client.LatitudeHudStudioScreen;
import com.example.globe.client.ZoneEnterTitleOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.2 moved the whole extract*(GuiGraphicsExtractor, DeltaTracker) HUD-rendering method family (including
// extractHotbarAndDecorations and this 2-arg extractRenderState) off Gui and onto a new Hud class
// (Gui.hud.extractRenderState(...)). Same method names/signatures, different owning class -- retarget only.
@Mixin(Hud.class)
public class InGameHudMixin {
    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"))
    private void globe$renderEwHazeBeforeHotbar(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client != null
                && client.gui.screen() != null
                && !(client.gui.screen() instanceof LatitudeHudStudioScreen)) {
            return;
        }
        EwSandstormOverlayHud.render(context, tickCounter);
        // B-3b: polar whiteout fill, same layer as the EW haze -- alpha ramps continuously 85->90 deg.
        com.example.globe.client.PolarWhiteoutOverlayHud.render(context, tickCounter);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void globe$renderOverlay(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client != null
                && client.gui.screen() != null
                && !(client.gui.screen() instanceof LatitudeHudStudioScreen)) {
            return;
        }
        GlobeWarningOverlay.render(context, tickCounter);
        CompassHud.render(context, tickCounter);
        if (client != null && client.getWindow() != null) {
            int gw = client.getWindow().getGuiScaledWidth();
            int gh = client.getWindow().getGuiScaledHeight();
            ZoneEnterTitleOverlay.render(context, gw, gh);
            // B-3c: hemisphere titles ride their OWN channel/position (above center), never the zone slot.
            com.example.globe.client.HemisphereTitleOverlay.render(context, gw, gh);
        }
    }
}
