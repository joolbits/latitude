package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientConfig;
import com.example.globe.client.LatitudeClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DownloadingTerrainScreen.class)
public abstract class DownloadingTerrainScreenFirstLoadMessageMixin {
    private static final Text MSG_LINE_1 = Text.literal("Latitude is preparing your world for the first time.");
    private static final Text MSG_LINE_2 = Text.literal("Subsequent loads will be much faster.");

    @Inject(method = "render", at = @At("TAIL"))
    private void latitude$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LatitudeClientState.firstWorldLoad) {
            return;
        }
        if (!LatitudeClientConfig.get().showFirstLoadMessage) {
            return;
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        int cx = w / 2;
        int cy = h / 2;

        int mw1 = tr.getWidth(MSG_LINE_1);
        int mw2 = tr.getWidth(MSG_LINE_2);
        int baseY = cy + 30;
        int shadowColor = 0x99000000;
        int line1Color = 0xFFD0D0D0;
        int line2Color = 0xFFA0A0A0;
        context.drawText(tr, MSG_LINE_1, cx - mw1 / 2 + 1, baseY + 1, shadowColor, false);
        context.drawText(tr, MSG_LINE_1, cx - mw1 / 2, baseY, line1Color, false);
        context.drawText(tr, MSG_LINE_2, cx - mw2 / 2 + 1, baseY + 11, shadowColor, false);
        context.drawText(tr, MSG_LINE_2, cx - mw2 / 2, baseY + 10, line2Color, false);
    }
}
