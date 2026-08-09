package com.example.globe.mixin.client;

import com.example.globe.GlobeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
 *
 * <p>Harvested from the 26.1.2 port and reversed onto this target's render API:
 * {@code GuiGraphicsExtractor} → {@code GuiGraphics}, {@code extractRenderState} → {@code render},
 * {@code text} → {@code drawString}, per the mapping table in the Hour-1 spike record.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenBuildLabelMixin {
    // GitHub #7 rule: fail soft -- a missed target costs the tester-facing watermark, never a
    // crash. expect=1 keeps dev boots loud under -Dmixin.debug.strict=true.
    @Inject(method = "render", at = @At("TAIL"), require = 0, expect = 1)
    private void globe$drawBuildLabel(GuiGraphics context, int mouseX, int mouseY, float partialTick,
                                      CallbackInfo ci) {
        // Tester-facing only: never draw on a public release build. Every player's title screen
        // otherwise carries a yellow git-commit string.
        if (!GlobeMod.isTestOrDevBuild()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) {
            return;
        }
        context.drawString(client.font, GlobeMod.buildLabel(), 4, 4, 0xFFFFFF00, true);
    }
}
