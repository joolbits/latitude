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

        // B-4 round 3 item 6: F1 (HUD hidden) must hide the compass / zone-biome-coords labels / zone &
        // hemisphere titles / whispers, but the world-atmosphere overlays (fog / whiteout / EW haze) must
        // STAY. Vanilla skips extractHotbarAndDecorations when the HUD is hidden, so the atmosphere that
        // normally rides that method (globe$renderEwHazeBeforeHotbar) is NOT drawn under F1 -- render it
        // here instead (this TAIL still runs when the HUD is hidden), then stop before any HUD chrome.
        boolean hudHidden = client != null && client.gui != null && client.gui.hud != null
                && client.gui.hud.isHidden();
        if (hudHidden) {
            EwSandstormOverlayHud.render(context, tickCounter);
            com.example.globe.client.PolarWhiteoutOverlayHud.render(context, tickCounter);
            // Keep the zone/hemisphere/pole tracking alive under F1; its own draw self-suppresses.
            GlobeWarningOverlay.render(context, tickCounter);
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
            // B-4 round 3 item 5: linger whispers (translucent italic) share the HUD layer, hidden by F1.
            com.example.globe.client.LatitudeWhisperOverlay.render(context, gw, gh);
        }
    }
}
