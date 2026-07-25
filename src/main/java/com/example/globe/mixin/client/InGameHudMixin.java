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
            // B-5-P2: the crossing curtain masks the teleport -- opaque, ON TOP of everything, even under F1.
            com.example.globe.client.HemispherePassageClient.renderCurtain(context);
            return;
        }

        // Understudy SWING: the DANGER/LETHAL polar-warning VIGNETTE. Punctuation for the warning TEXT, so it
        // rides the visible HUD layer (hidden under F1 with the text it accompanies) and draws UNDER the text
        // but OVER the whiteout fill (whiteout ran earlier in extractHotbarAndDecorations; text is next line).
        com.example.globe.client.PolarVignetteOverlayHud.render(context, tickCounter);
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
        // B-5-P2: the crossing curtain rides ABOVE all HUD chrome so it fully masks the teleport + chunk load.
        // Known accepted quirk (P2 sweep LOW-2, cosmetic): if the player opens a Screen mid-crossing (e.g. E for
        // inventory), this method's top screen-guard returns early, so that screen shows over the curtain for a
        // moment; the curtain resumes when it closes. Accepted -- do not fix.
        com.example.globe.client.HemispherePassageClient.renderCurtain(context);
    }
}
