package com.example.globe.mixin.client;

import com.example.globe.client.CompassHud;
import com.example.globe.client.EwSandstormOverlayHud;
import com.example.globe.client.GlobeWarningOverlay;
import com.example.globe.client.LatitudeHudStudioScreen;
import com.example.globe.client.ZoneEnterTitleOverlay;
import com.example.globe.debug.WarmSnowTrapStats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void globe$renderEwHazeFirst(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        // Draw haze beneath all HUD elements
        EwSandstormOverlayHud.render(context, tickCounter);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void globe$renderOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null
                && client.currentScreen != null
                && !(client.currentScreen instanceof LatitudeHudStudioScreen)) {
            return;
        }
        GlobeWarningOverlay.render(context, tickCounter);
        CompassHud.render(context, tickCounter);
        if (client != null && client.getWindow() != null) {
            ZoneEnterTitleOverlay.render(context, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        }

        if (WarmSnowTrapStats.DEBUG_WARM_SNOW_STATS && client != null) {
            BlockPos lp = WarmSnowTrapStats.lastPos;
            String lb = WarmSnowTrapStats.lastBlock;
            double lt = WarmSnowTrapStats.lastT;
            String lastInfo = (lp != null && lb != null)
                    ? String.format(" last=%s @ %d %d %d t=%.3f", lb, lp.getX(), lp.getY(), lp.getZ(), lt)
                    : "";
            String line = String.format("WarmSnowTrap calls=%d hits=%d rewrites=%d%s",
                    WarmSnowTrapStats.calls, WarmSnowTrapStats.snowHits, WarmSnowTrapStats.rewrites, lastInfo);
            context.drawTextWithShadow(client.textRenderer, line, 2, 2, 0xFFFF55);
        }
    }
}
