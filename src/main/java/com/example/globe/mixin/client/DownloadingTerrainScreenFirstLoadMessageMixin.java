package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientConfig;
import com.example.globe.client.LatitudeClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.Util;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DownloadingTerrainScreen.class)
public abstract class DownloadingTerrainScreenFirstLoadMessageMixin {
    @Shadow @Final private long loadStartTime;
    @Shadow @Final private static long MIN_LOAD_TIME_MS;

    private static final Text LINE_1 = Text.literal("Latitude is preparing your world for the first time.");
    private static final Text LINE_2 = Text.literal("Subsequent loads will be much faster.");

    @Inject(method = "render", at = @At("TAIL"))
    private void latitude$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LatitudeClientConfig.get().showFirstLoadMessage) {
            return;
        }
        if (!LatitudeClientState.firstWorldLoad) {
            return;
        }

        long now = Util.getMeasuringTimeMs();
        float p = MathHelper.clamp((now - this.loadStartTime) / (float) MIN_LOAD_TIME_MS, 0.0f, 1.0f);
        float ease = 1.0f - (1.0f - p) * (1.0f - p) * (1.0f - p);

        int alpha = (int) Math.round(ease * 255.0f);
        int yOffset = (int) Math.round((1.0f - ease) * 10.0f);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int cx = context.getScaledWindowWidth() / 2;
        int cy = context.getScaledWindowHeight() / 2;
        int baseY = cy + 20 + yOffset;

        int shadowA = (int) Math.round(alpha * 0.6);
        int shadowColor = (shadowA << 24);
        int line1Color = (alpha << 24) | 0xD0D0D0;
        int line2Color = (alpha << 24) | 0xA0A0A0;

        int w1 = tr.getWidth(LINE_1);
        int w2 = tr.getWidth(LINE_2);

        context.drawText(tr, LINE_1, cx - w1 / 2 + 1, baseY + 1, shadowColor, false);
        context.drawText(tr, LINE_1, cx - w1 / 2, baseY, line1Color, false);

        int line2Y = baseY + 10;
        context.drawText(tr, LINE_2, cx - w2 / 2 + 1, line2Y + 1, shadowColor, false);
        context.drawText(tr, LINE_2, cx - w2 / 2, line2Y, line2Color, false);

    }

    @Inject(method = "close", at = @At("HEAD"))
    private void latitude$clearFlagOnClose(CallbackInfo ci) {
        LatitudeClientState.firstWorldLoad = false;
        LatitudeClientState.firstWorldLoadStartMs = 0L;
    }
}
