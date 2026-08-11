package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import com.example.globe.client.LatitudeLoadingPane;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a Latitude-branded overlay on the loading screen for Latitude worlds.
 * Uses TAIL injection — vanilla lifecycle runs fully, we just paint on top.
 */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenLatitudeOverlayMixin extends Screen {

    @Shadow
    private float smoothedProgress;

    // Theme, phrases, drawing and animation state all live in LatitudeLoadingPane now — this
    // screen is one of several the pane is painted on. See that class's javadoc.
    @Unique private static final long FAIL_SAFE_CLEAR_MS = 10 * 60 * 1000L;

    protected LevelLoadingScreenLatitudeOverlayMixin(Component title) {
        super(title);
    }

    // GitHub #7 rule: a missed loading-overlay target means the vanilla loading screen, never
    // a crash. The Minecraft-tick clear mixin below stays STRICT: it is the fail-open safety
    // that releases the loading hold, and must never be softened with the overlay.
    @Inject(method = "render", at = @At("TAIL"), require = 0, expect = 1)
    private void globe$renderLatitudeOverlay(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LatitudeClientState.isLatitudeWorldLoading()) {
            LatitudeLoadingPane.reset();
            return;
        }

        long now = Util.getMillis();
        if (!LatitudeLoadingPane.hasStarted()) {
            LatitudeLoadingPane.start(now);
        } else if (LatitudeClientState.elapsedSinceExpeditionMs() >= FAIL_SAFE_CLEAR_MS) {
            globe$clearLoadingFlagNow();
            return;
        }

        // This is the only screen in the load chain with real chunk progress to report, so it is
        // the only one that publishes it.
        float rawProgress = Mth.clamp(this.smoothedProgress, 0f, 1f);
        LatitudeClientState.latitudeLoadingProgress = rawProgress;
        LatitudeLoadingPane.render(context, this.font, delta, rawProgress, now);
    }

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true, require = 0, expect = 1)
    private void globe$clearLoadingFlag(CallbackInfo ci) {
        if (LatitudeClientState.isLatitudeWorldLoading()) {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null && client.player != null) {
                ci.cancel();
                return;
            }
            // Vanilla is closing the loading screen without a playable client world. This is an abort/error
            // transition, not the render-warmup handoff, so release our flag and let vanilla show its next
            // screen instead of trapping the player behind the overlay until the ten-minute fail-safe.
            LatitudeClientState.clearLatitudeLoadingState();
        }
        LatitudeLoadingPane.reset();
    }

    @Unique
    private void globe$clearLoadingFlagNow() {
        LatitudeClientState.clearLatitudeLoadingState();
        LatitudeLoadingPane.reset();
    }

}

@Mixin(Minecraft.class)
class LatitudeLoadingClientTickMixin {
    @Unique
    private static final long FAIL_SAFE_CLEAR_MS = 10 * 60 * 1000L;
    @Unique
    private static final long PLAYABLE_READY_MAX_HOLD_MS = 15_000L;
    @Unique
    private static final long PLAYABLE_READY_MIN_RENDER_HOLD_MS = 2_500L;
    @Unique
    private static final long PLAYABLE_READY_RENDER_SIGNAL_MAX_HOLD_MS = 6_000L;
    @Unique
    private static final int PLAYABLE_READY_MIN_PLAYER_TICKS = 20;
    @Unique
    private static final int PLAYABLE_READY_CHUNK_RADIUS = 1;
    @Unique
    private long globe$clientReadyObservedAtMs = Long.MIN_VALUE;

    @Shadow
    public ClientLevel level;

    @Shadow
    public LocalPlayer player;

    @Inject(method = "tick", at = @At("TAIL"))
    private void globe$clearLoadingOnClientReadyTick(CallbackInfo ci) {
        if (!LatitudeClientState.isLatitudeWorldLoading()) {
            globe$clientReadyObservedAtMs = Long.MIN_VALUE;
            return;
        }

        long sinceExpedition = LatitudeClientState.elapsedSinceExpeditionMs();
        if (sinceExpedition >= FAIL_SAFE_CLEAR_MS) {
            LatitudeClientState.clearLatitudeLoadingState();
            globe$clientReadyObservedAtMs = Long.MIN_VALUE;
            return;
        }

        if (this.level == null || this.player == null) {
            return;
        }

        if (globe$clientReadyObservedAtMs == Long.MIN_VALUE) {
            globe$clientReadyObservedAtMs = Util.getMillis();
        }

        Minecraft client = (Minecraft) (Object) this;
        boolean playerSettled = this.player.tickCount >= PLAYABLE_READY_MIN_PLAYER_TICKS;
        boolean spawnChunksReady = globe$clientSpawnChunkRingReady();
        long clientReadyHoldMs = Math.max(0L, Util.getMillis() - globe$clientReadyObservedAtMs);
        boolean renderWarmupElapsed = clientReadyHoldMs >= PLAYABLE_READY_MIN_RENDER_HOLD_MS;
        boolean renderSignalTimedOut = clientReadyHoldMs >= PLAYABLE_READY_RENDER_SIGNAL_MAX_HOLD_MS;
        boolean renderReady = globe$clientRenderReady(client) || renderSignalTimedOut;
        boolean readinessTimedOut = clientReadyHoldMs >= PLAYABLE_READY_MAX_HOLD_MS;
        if ((!playerSettled || !spawnChunksReady || !renderWarmupElapsed || !renderReady) && !readinessTimedOut) {
            return;
        }
        LatitudeClientState.clearLatitudeLoadingState();
        globe$clientReadyObservedAtMs = Long.MIN_VALUE;
    }

    @Unique
    private boolean globe$clientSpawnChunkRingReady() {
        int chunkX = Math.floorDiv(Mth.floor(this.player.getX()), 16);
        int chunkZ = Math.floorDiv(Mth.floor(this.player.getZ()), 16);
        for (int dz = -PLAYABLE_READY_CHUNK_RADIUS; dz <= PLAYABLE_READY_CHUNK_RADIUS; dz++) {
            for (int dx = -PLAYABLE_READY_CHUNK_RADIUS; dx <= PLAYABLE_READY_CHUNK_RADIUS; dx++) {
                if (!this.level.hasChunk(chunkX + dx, chunkZ + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Unique
    private boolean globe$clientRenderReady(Minecraft client) {
        if (client.levelRenderer == null || this.player == null) {
            return false;
        }
        boolean renderQueueEmpty = client.levelRenderer.hasRenderedAllSections();
        int renderedSections = client.levelRenderer.getVisibleSections().size();
        boolean playerSectionVisible = client.levelRenderer.isSectionCompiledAndVisible(this.player.blockPosition());
        net.minecraft.core.BlockPos feetPos = net.minecraft.core.BlockPos.containing(
                this.player.getX(), this.player.getY() - 1.0, this.player.getZ());
        boolean feetSectionVisible = client.levelRenderer.isSectionCompiledAndVisible(feetPos);
        return (playerSectionVisible || feetSectionVisible)
                || (renderQueueEmpty && renderedSections > 0);
    }
}
