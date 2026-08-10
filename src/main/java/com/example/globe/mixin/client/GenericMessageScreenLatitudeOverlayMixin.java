package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import com.example.globe.client.LatitudeLoadingPane;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends the Latitude loading pane over the plain message screens a resumed world passes through
 * before the real loading screen exists.
 *
 * <p>Resuming a save runs {@code WorldOpenFlows.openWorld}, which shows <b>three</b> sequential
 * {@link GenericMessageScreen}s — at its own head, in {@code openWorldLoadLevelData}, and in
 * {@code openWorldLoadLevelStem} — before {@code doWorldLoad} ever reaches
 * {@code LevelLoadingScreen}. The overlay only painted that final screen, so every reload showed
 * several seconds of vanilla panorama first and then snapped to Latitude's pane: the
 * "vanilla loading screen, last minute switches to bespoke" reported live on 2026-08-09. Creating
 * a world hid the same gap because {@code createFreshLevel} passes one such screen with no level
 * data to read and no stem to resolve, so it is gone in a frame or two.
 *
 * <p>{@code GenericMessageScreen} inherits {@code render} from {@code Screen} rather than
 * declaring it, so the hook is {@code renderBackground} — which the class does declare, and which
 * runs before vanilla's own text widget draws. Painting there and hiding that widget hands the
 * screen wholly to the pane rather than stacking Latitude's compass under vanilla's
 * "Reading world data" line.
 *
 * <p>Fail-soft like every overlay hook in this lifecycle ({@code require = 0}): a missed target
 * just restores the previous behaviour of branding only the final screen, never a crash. The
 * widget is explicitly restored whenever the pane is not active, so a message screen shown for any
 * other reason — and the same screen after an aborted load clears the flag — keeps its own text.
 */
@Mixin(GenericMessageScreen.class)
public abstract class GenericMessageScreenLatitudeOverlayMixin {

    @Shadow
    private FocusableTextWidget textWidget;

    @Inject(method = "renderBackground", at = @At("TAIL"), require = 0, expect = 1)
    private void globe$paintLatitudeLoadingPane(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        boolean loading = LatitudeClientState.isLatitudeWorldLoading();
        if (textWidget != null) {
            // Only ever suppressed while Latitude owns the screen; restored on every other frame so
            // this can never silently blank an unrelated message screen.
            textWidget.visible = !loading;
        }
        if (!loading) {
            return;
        }
        long now = Util.getMillis();
        LatitudeLoadingPane.start(now);
        // No chunk progress exists this early — the track draws empty so the pane's geometry does
        // not shift when LevelLoadingScreen takes over and starts filling it.
        LatitudeLoadingPane.render(context, net.minecraft.client.Minecraft.getInstance().font,
                delta, LatitudeLoadingPane.NO_PROGRESS, now);
    }
}
