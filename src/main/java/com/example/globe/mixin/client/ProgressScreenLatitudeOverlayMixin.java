package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import com.example.globe.client.LatitudeLoadingPane;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Closes the last hole in the world-open screen chain: the one-frame {@link ProgressScreen} that
 * stands between the message screens and the real loading screen.
 *
 * <p>{@code Minecraft.doWorldLoad}'s very first instruction is
 * {@code disconnectWithProgressScreen()}, which builds {@code new ProgressScreen(true)} and hands it
 * to {@code disconnect(...)}; that path ends in {@code setScreenAndShow}, and {@code setScreenAndShow}
 * is {@code setScreen} <i>plus a forced {@code runTick}</i> — so this screen is guaranteed to be
 * painted, not merely assigned, on every single world open. Only a handful of instructions later the
 * same method installs {@code LevelLoadingScreen}, so it is on screen for one frame. That is exactly
 * long enough to read as a flash of vanilla between two branded frames, which is the class of defect
 * this whole chain of hooks exists to remove.
 *
 * <p>Nothing of vanilla's needs hiding here. {@code ProgressScreen.render} draws its {@code header}
 * at y=70 and its {@code stage} line at y=90, but both are null on a screen that was constructed and
 * shown without ever receiving a {@code progressStart}/{@code progressStage} call — which is this
 * one. Painting at TAIL therefore lands the pane over an otherwise empty vanilla background, with no
 * widget to suppress and nothing to restore.
 *
 * <p>Deliberately a per-class hook rather than a blanket {@code Screen.render} one. The screens this
 * chain can also show — {@code BackupConfirmScreen}, {@code RecoverWorldDataScreen},
 * {@code DatapackLoadFailureScreen}, {@code AlertScreen}, {@code ConfirmScreen} — are all
 * <b>interactive</b>: an opaque branded pane over any of them hides the button the player has to
 * press and hard-locks the world open. Falling through to vanilla on an unrecognised screen is the
 * correct behaviour, not a miss. The guard against a genuinely missed screen is the enumeration
 * logger in {@code LatitudeLoadingClientTickMixin}, which names every screen shown during a Latitude
 * load and says whether the pane covers it.
 *
 * <p>Fail-soft ({@code require = 0}) like every other overlay hook in this lifecycle, per GitHub #7.
 */
@Mixin(ProgressScreen.class)
public abstract class ProgressScreenLatitudeOverlayMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 0, expect = 1)
    private void globe$paintLatitudeLoadingPane(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LatitudeClientState.isLatitudeWorldLoading()) {
            return;
        }
        long now = Util.getMillis();
        LatitudeLoadingPane.start(now);
        // No chunk progress exists at this point in doWorldLoad — the track draws empty so the
        // pane's geometry does not shift when LevelLoadingScreen takes over and starts filling it.
        LatitudeLoadingPane.render(context, Minecraft.getInstance().font,
                delta, LatitudeLoadingPane.NO_PROGRESS, now);
    }
}
