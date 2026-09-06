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
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Final;
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

    // 1.21.1's LevelLoadingScreen keeps no smoothed progress field: the raw percentage lives on
    // the listener it was constructed with. Reading it directly gives the same 0..1 value, without
    // the newer lines' inter-frame smoothing, which this screen does not have to publish.
    @Shadow
    @Final
    private StoringChunkProgressListener progressListener;

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
        float rawProgress = Mth.clamp(this.progressListener.getProgress() / 100.0F, 0f, 1f);
        LatitudeClientState.latitudeLoadingProgress = rawProgress;
        LatitudeLoadingPane.render(context, this.font, delta, rawProgress, now);
    }

    /**
     * 1.21.1's LevelLoadingScreen declares no {@code onClose} -- it inherits Screen's, and
     * {@code shouldCloseOnEsc} is false, so the player cannot trigger it at all. {@code removed} is
     * the equivalent "this screen is going away" point that the class does declare. It cannot be
     * cancelled, but the cancelling half was only ever there to hold the overlay while a playable
     * world exists; that case is simply left alone here and the Minecraft-tick mixin below stays the
     * fail-open release, exactly as it is on the newer lines.
     */
    @Inject(method = "removed", at = @At("HEAD"), require = 0, expect = 1)
    private void globe$clearLoadingFlag(CallbackInfo ci) {
        if (LatitudeClientState.isLatitudeWorldLoading()) {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null && client.player != null) {
                // The render-warmup handoff: a playable world is up, so the tick mixin owns the
                // release and the overlay must not be torn down here.
                return;
            }
            // The loading screen is going away without a playable client world. This is an
            // abort/error transition, so release the flag rather than trapping the player behind the
            // overlay until the ten-minute fail-safe.
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
        // 1.21.1's LevelRenderer publishes no per-section visibility query and no visible-section
        // list -- isSectionCompiledAndVisible and getVisibleSections are both later additions. The
        // coarse pair it does publish carries the same "the world is drawn" signal the overlay needs;
        // the per-position shortcut is simply unavailable on this target, so readiness waits for the
        // whole queue instead of just the player's own section.
        boolean renderQueueEmpty = client.levelRenderer.hasRenderedAllSections();
        int renderedSections = client.levelRenderer.countRenderedSections();
        return renderQueueEmpty && renderedSections > 0;
    }
}
