package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class DownloadingTerrainScreenFirstLoadMessageMixin {

    @Shadow
    private float loadProgress;

    private static final Component FIRST_LOAD_HELPER = Component.literal("Press F9 in-game for HUD options");

    @Inject(method = "render", at = @At("TAIL"))
    private void globe$renderFirstLoadMessage(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LatitudeClientState.isLatitudeWorldLoading()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) {
            return;
        }

        float p = Mth.clamp(this.loadProgress, 0.0f, 1.0f);
        float ease = 1.0f - (1.0f - p) * (1.0f - p) * (1.0f - p);
        int alpha = Math.round(ease * 255.0f);
        int yOffset = Math.round((1.0f - ease) * 10.0f);

        Font tr = client.font;
        int cx = context.guiWidth() / 2;
        int cy = context.guiHeight() / 2;
        int baseY = cy + 40 + yOffset;

        int shadowA = Math.round(alpha * 0.6f);
        int shadowColor = (shadowA << 24);
        int helperColor = (alpha << 24) | 0x00D0D0D0;

        int w = tr.width(FIRST_LOAD_HELPER);
        int x = cx - (w / 2);

        context.drawString(tr, FIRST_LOAD_HELPER, x + 1, baseY + 1, shadowColor, false);
        context.drawString(tr, FIRST_LOAD_HELPER, x, baseY, helperColor, false);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void globe$clearFirstLoadFlag(CallbackInfo ci) {
        LatitudeClientState.firstWorldLoad = false;
        LatitudeClientState.firstWorldLoadStartMs = 0L;
    }
}
