package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import com.example.globe.client.LatitudeLoadingPane;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    @Unique private static final Logger GLOBE_LOGGER = LoggerFactory.getLogger("LatitudeLoadingOverlay");

    static {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!LatitudeClientState.isLatitudeWorldLoading()) {
                return;
            }
            long sinceExpedition = LatitudeClientState.elapsedSinceExpeditionMs();
            boolean firstMark = LatitudeClientState.markClientReadyObserved();
            if (firstMark) {
                GLOBE_LOGGER.info("[Latitude lifecycle] client game join callback — {}ms since beginExpedition",
                        sinceExpedition);
            }

            ClientLevel world = client.level;
            LocalPlayer player = client.player;
            if (world == null || player == null) {
                return;
            }
        });
    }

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
            GLOBE_LOGGER.info("[LAT][LOADUI] bespoke overlay first render — {}ms since beginExpedition",
                    LatitudeClientState.elapsedSinceExpeditionMs());
        } else if (LatitudeClientState.elapsedSinceExpeditionMs() >= FAIL_SAFE_CLEAR_MS) {
            globe$clearLoadingFlagNow(true);
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
            long clearedAt = LatitudeClientState.clearLatitudeLoadingState();
            GLOBE_LOGGER.info("[LAT][LOADUI] loading close released without a playable world — {}ms since beginExpedition",
                    clearedAt);
        }
        long sinceExpedition = LatitudeClientState.elapsedSinceExpeditionMs();
        if (sinceExpedition < 0L) {
            sinceExpedition = LatitudeClientState.lastLifecycleClearElapsedMs();
        }
        GLOBE_LOGGER.info("[LAT][LOADUI] loading screen closed — {}ms since beginExpedition",
                sinceExpedition);
        LatitudeLoadingPane.reset();
    }

    @Unique
    private void globe$clearLoadingFlagNow(boolean failSafe) {
        long sinceExpedition = LatitudeClientState.clearLatitudeLoadingState();
        if (failSafe) {
            GLOBE_LOGGER.info("[LAT][LOADUI] bespoke overlay cleared by fail-safe — {}ms since beginExpedition",
                    sinceExpedition);
        } else {
            GLOBE_LOGGER.info("[LAT][LOADUI] bespoke overlay cleared by normal client-ready path — {}ms since beginExpedition",
                    sinceExpedition);
        }
        LatitudeLoadingPane.reset();
    }

}

@Mixin(Minecraft.class)
class LatitudeLoadingClientTickMixin {
    @Unique
    private static final Logger GLOBE_LOGGER = LoggerFactory.getLogger("LatitudeLoadingOverlay");
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
    @Unique
    private long globe$lastReadinessWaitLogTick = Long.MIN_VALUE;

    @Shadow
    public ClientLevel level;

    @Shadow
    public LocalPlayer player;

    /**
     * Names every screen vanilla shows while Latitude owns a world load, and says whether the pane
     * covers it.
     *
     * <p>This is the instrument the loading chain was missing. Every overlay hook here is
     * {@code require = 0} by policy (GitHub #7: a moved target must degrade to the vanilla screen,
     * never crash), which also means a screen nobody hooks is completely silent — the only symptom
     * is a maintainer noticing "it went vanilla in the middle" and no way to tell which screen did
     * it. {@code setScreen} is the single chokepoint every screen in the chain passes through
     * ({@code setScreenAndShow} is {@code setScreen} plus a forced render), so one hook here
     * enumerates the whole chain in the log, in order, for whatever load actually ran on whatever
     * machine — including the error branches this pane deliberately does not paint.
     *
     * <p>The pane is deliberately <b>not</b> painted over unrecognised screens. The rest of this
     * chain is {@code BackupConfirmScreen}, {@code RecoverWorldDataScreen},
     * {@code DatapackLoadFailureScreen}, {@code AlertScreen} and {@code ConfirmScreen} — all
     * interactive, all needing a click to proceed. Covering them would hide the button and hard-lock
     * the load, so vanilla-through is the correct outcome and this line is how it stays visible.
     */
    @Inject(method = "setScreen", at = @At("HEAD"), require = 0, expect = 1)
    private void globe$logWorldOpenScreen(Screen screen, CallbackInfo ci) {
        if (!LatitudeClientState.isLatitudeWorldLoading()) {
            return;
        }
        boolean covered = screen instanceof LevelLoadingScreen
                || screen instanceof GenericMessageScreen
                || screen instanceof ProgressScreen;
        GLOBE_LOGGER.info("[LAT][LOADUI] world-open screen: {} (latitudePaneCovers={})",
                screen == null ? "<none>" : screen.getClass().getName(), covered);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void globe$clearLoadingOnClientReadyTick(CallbackInfo ci) {
        if (!LatitudeClientState.isLatitudeWorldLoading()) {
            globe$clientReadyObservedAtMs = Long.MIN_VALUE;
            globe$lastReadinessWaitLogTick = Long.MIN_VALUE;
            return;
        }

        long sinceExpedition = LatitudeClientState.elapsedSinceExpeditionMs();
        if (sinceExpedition >= FAIL_SAFE_CLEAR_MS) {
            long clearedAt = LatitudeClientState.clearLatitudeLoadingState();
            GLOBE_LOGGER.info("[Latitude lifecycle] bespoke overlay cleared by fail-safe — {}ms since beginExpedition",
                    clearedAt);
            globe$clientReadyObservedAtMs = Long.MIN_VALUE;
            globe$lastReadinessWaitLogTick = Long.MIN_VALUE;
            return;
        }

        if (this.level == null || this.player == null) {
            return;
        }

        if (LatitudeClientState.markClientReadyObserved()) {
            GLOBE_LOGGER.info("[Latitude lifecycle] player/world became client-ready — {}ms since beginExpedition",
                    LatitudeClientState.elapsedSinceExpeditionMs());
        }
        if (globe$clientReadyObservedAtMs == Long.MIN_VALUE) {
            globe$clientReadyObservedAtMs = Util.getMillis();
        }

        Minecraft client = (Minecraft) (Object) this;
        boolean loadingScreenVisible = client.screen instanceof LevelLoadingScreen;
        boolean playerSettled = this.player.tickCount >= PLAYABLE_READY_MIN_PLAYER_TICKS;
        boolean spawnChunksReady = globe$clientSpawnChunkRingReady();
        RenderReadiness renderReadiness = globe$clientRenderReadiness(client);
        long clientReadyHoldMs = Math.max(0L, Util.getMillis() - globe$clientReadyObservedAtMs);
        boolean renderWarmupElapsed = clientReadyHoldMs >= PLAYABLE_READY_MIN_RENDER_HOLD_MS;
        boolean renderSignalTimedOut = clientReadyHoldMs >= PLAYABLE_READY_RENDER_SIGNAL_MAX_HOLD_MS;
        boolean renderReady = renderReadiness.ready() || renderSignalTimedOut;
        boolean readinessTimedOut = clientReadyHoldMs >= PLAYABLE_READY_MAX_HOLD_MS;
        if ((!playerSettled || !spawnChunksReady || !renderWarmupElapsed || !renderReady) && !readinessTimedOut) {
            globe$logReadinessWait(playerSettled, spawnChunksReady, renderWarmupElapsed, renderReady,
                    renderSignalTimedOut, loadingScreenVisible, clientReadyHoldMs, renderReadiness);
            return;
        }
        GLOBE_LOGGER.info("[Latitude lifecycle] first safe playable tick — {}ms since beginExpedition (playerAge={}, loadingScreenVisible={}, spawnChunksReady={}, renderWarmupElapsed={}, renderReady={}, renderSignalTimedOut={}, readyHoldMs={}, timedOut={}, renderedSections={}, renderQueueEmpty={}, playerSectionVisible={}, feetSectionVisible={})",
                LatitudeClientState.elapsedSinceExpeditionMs(),
                this.player.tickCount,
                loadingScreenVisible,
                spawnChunksReady,
                renderWarmupElapsed,
                renderReady,
                renderSignalTimedOut,
                clientReadyHoldMs,
                readinessTimedOut,
                renderReadiness.renderedSections(),
                renderReadiness.renderQueueEmpty(),
                renderReadiness.playerSectionVisible(),
                renderReadiness.feetSectionVisible());

        long clearedAt = LatitudeClientState.clearLatitudeLoadingState();
        GLOBE_LOGGER.info("[Latitude lifecycle] bespoke overlay cleared by normal client-ready path — {}ms since beginExpedition",
                clearedAt);
        globe$clientReadyObservedAtMs = Long.MIN_VALUE;
        globe$lastReadinessWaitLogTick = Long.MIN_VALUE;
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
    private RenderReadiness globe$clientRenderReadiness(Minecraft client) {
        if (client.levelRenderer == null || this.player == null) {
            return RenderReadiness.unavailable();
        }
        boolean renderQueueEmpty = client.levelRenderer.hasRenderedAllSections();
        int renderedSections = client.levelRenderer.getVisibleSections().size();
        boolean playerSectionVisible = client.levelRenderer.isSectionCompiledAndVisible(this.player.blockPosition());
        net.minecraft.core.BlockPos feetPos = net.minecraft.core.BlockPos.containing(
                this.player.getX(), this.player.getY() - 1.0, this.player.getZ());
        boolean feetSectionVisible = client.levelRenderer.isSectionCompiledAndVisible(feetPos);
        boolean ready = (playerSectionVisible || feetSectionVisible)
                || (renderQueueEmpty && renderedSections > 0);
        return new RenderReadiness(ready, renderedSections, renderQueueEmpty, playerSectionVisible, feetSectionVisible);
    }

    @Unique
    private void globe$logReadinessWait(boolean playerSettled,
                                        boolean spawnChunksReady,
                                        boolean renderWarmupElapsed,
                                        boolean renderReady,
                                        boolean renderSignalTimedOut,
                                        boolean loadingScreenVisible,
                                        long clientReadyHoldMs,
                                        RenderReadiness renderReadiness) {
        long gameTime = this.level.getGameTime();
        if (globe$lastReadinessWaitLogTick != Long.MIN_VALUE
                && gameTime - globe$lastReadinessWaitLogTick < 20L) {
            return;
        }
        globe$lastReadinessWaitLogTick = gameTime;
        GLOBE_LOGGER.info("[Latitude lifecycle] waiting for playable entry — {}ms since beginExpedition (playerAge={}, playerSettled={}, spawnChunksReady={}, renderWarmupElapsed={}, renderReady={}, renderSignalTimedOut={}, loadingScreenVisible={}, readyHoldMs={}, renderedSections={}, renderQueueEmpty={}, playerSectionVisible={}, feetSectionVisible={})",
                LatitudeClientState.elapsedSinceExpeditionMs(),
                this.player.tickCount,
                playerSettled,
                spawnChunksReady,
                renderWarmupElapsed,
                renderReady,
                renderSignalTimedOut,
                loadingScreenVisible,
                clientReadyHoldMs,
                renderReadiness.renderedSections(),
                renderReadiness.renderQueueEmpty(),
                renderReadiness.playerSectionVisible(),
                renderReadiness.feetSectionVisible());
    }

    @Unique
    private record RenderReadiness(boolean ready,
                                   int renderedSections,
                                   boolean renderQueueEmpty,
                                   boolean playerSectionVisible,
                                   boolean feetSectionVisible) {
        static RenderReadiness unavailable() {
            return new RenderReadiness(false, -1, false, false, false);
        }
    }
}
