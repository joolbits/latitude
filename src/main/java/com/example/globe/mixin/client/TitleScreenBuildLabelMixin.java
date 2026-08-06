package com.example.globe.mixin.client;

import com.example.globe.GlobeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Upper-left build watermark on the title screen, requested for live-flight testing: a tester
 * should be able to see at a glance which exact build they launched without checking file names or
 * hashing jars by hand. Reuses {@link GlobeMod#buildLabel()} so the on-screen text is the same
 * source the jar manifest itself reports, never a second copy that can drift.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenBuildLabelMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void globe$drawBuildLabel(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) {
            return;
        }
        context.text(client.font, GlobeMod.buildLabel(), 4, 4, 0xFFFFFF00, true);
    }
}
