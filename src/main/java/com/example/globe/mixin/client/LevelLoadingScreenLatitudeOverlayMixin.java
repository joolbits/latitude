package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    // ── Theme ──
    @Unique private static final int PANE_BG = 0xE62C2420;
    @Unique private static final int PANE_BORDER = 0xFF5C4A3A;
    @Unique private static final int GOLD = 0xFFD4A74A;
    @Unique private static final int WARM_WHITE = 0xFFEDE0D0;
    @Unique private static final int MUTED = 0xFF8C8078;
    @Unique private static final int GRID_COLOR = 0x14504840;
    @Unique private static final int GRID_STEP = 16;

    // ── Loading phrases ──
    @Unique private static final String[] PHRASES = {
            "Defusing creepers...",
            "Charting the frontier...",
            "Following the compass...",
            "Packing snow boots...",
            "Crossing climate bands...",
            "Calibrating the equator...",
            "Warming the tropics...",
            "Freezing the poles...",
            "Planting bamboo groves...",
            "Surveying the horizon...",
            "Stacking tectonic plates...",
            "Cloning sheep...",
            "Folding mountain ranges...",
            "Teaching villagers cartography...",
            "Nudging continents apart...",
            "Watering bamboo groves...",
            "Polishing compass glass...",
            "Freezing polar seas...",
            "Planting spruce forests...",
            "Stretching the horizon...",
            "Carving river valleys...",
            "Mapping trade winds...",
            "Herding cows inland...",
            "Waking up foxes...",
            "Hiding ancient ruins...",
            "Dusting off badlands...",
            "Tuning the jet stream...",
            "Training parrots...",
            "Filling oceans carefully...",
            "Sprinkling wildflowers...",
            "Convincing bees to pollinate...",
            "Laying down riverbeds...",
            "Rotating the planet...",
            "Aligning magnetic north...",
            "Plentifying biomes...",
            "Sorting biomes by latitude...",
            "Trimming the treeline...",
            "Talking trees down from the peaks...",
            "Giving the mountaintops a trim...",
            "Capping peaks with snow...",
            "Frosting the summits...",
            "Dusting the peaks with powder...",
            "Welcoming guest biomes...",
            "Untangling Terralith's roots...",
            "Evicting a few biome squatters...",
            "Finding homes for visiting biomes...",
            "Making room for everyone's biomes...",
            "Minding the alpine line...",
            "Unrolling more map...",
            "Giving the compass more to point at...",
            "Making room for two more oceans...",
            "Packing extra biomes for the road..."
    };

    // The Latitude-feature splashes are the last FEATURED_PHRASE_COUNT entries of PHRASES. Bias the
    // starting point into that block most of the time so the newer 1.4 phrases usually lead.
    @Unique private static final int FEATURED_PHRASE_COUNT = 19;

    @Unique
    private static int globe$pickSeedIndex() {
        int total = PHRASES.length;
        int featuredStart = Math.max(0, total - FEATURED_PHRASE_COUNT);
        // ALWAYS start in the Latitude-feature block (was gated behind a 70% roll, so on a fast Mercator load
        // the single shown phrase was often a generic one — the "feature messages disappeared" report). The
        // compass screen now always leads with a Latitude-feature line.
        if (featuredStart < total) {
            return featuredStart + (int) (Math.random() * (total - featuredStart));
        }
        return (int) (Math.random() * total);
    }

    // Once per phrase cycle, pick the next splash at RANDOM with a ~70% bias toward the newer
    // Latitude-feature block, avoiding an immediate repeat and back-to-back peak/mountain-themed lines.
    // The "3 peak messages in a row" report (TEST 1 B1) came from the OLD sequential walk through adjacent
    // array entries; this replaces that walk with a weighted random draw — "70% newer, but ultimately random."
    @Unique
    private int globe$pickNextPhrase() {
        int total = PHRASES.length;
        int featuredStart = Math.max(0, total - FEATURED_PHRASE_COUNT);
        int prev = globe$currentPhraseIdx;
        int idx = prev;
        for (int guard = 0; guard < 8; guard++) {
            boolean featured = featuredStart < total && Math.random() < 0.70;
            if (featured) {
                idx = featuredStart + (int) (Math.random() * (total - featuredStart));
            } else if (featuredStart > 0) {
                idx = (int) (Math.random() * featuredStart);
            } else {
                idx = (int) (Math.random() * total);
            }
            boolean sameAsPrev = (idx == prev);
            boolean peakClash = globe$isPeakThemed(idx) && globe$isPeakThemed(prev);
            if (!sameAsPrev && !peakClash) {
                break;
            }
        }
        return idx;
    }

    @Unique
    private static boolean globe$isPeakThemed(int idx) {
        if (idx < 0 || idx >= PHRASES.length) {
            return false;
        }
        String s = PHRASES[idx].toLowerCase(java.util.Locale.ROOT);
        return s.contains("peak") || s.contains("summit") || s.contains("alpine")
                || s.contains("treeline") || s.contains("mountain") || s.contains("powder");
    }

    @Unique private static final long PHRASE_CYCLE_MS = 4800;
    @Unique private static final long FAIL_SAFE_CLEAR_MS = 10 * 60 * 1000L;
    @Unique private long globe$overlayStartMs = 0L;
    @Unique private float globe$displayProgress = 0f;
    @Unique private int globe$phraseSeedIdx = 0;
    @Unique private long globe$lastCycleNo = -1L;
    @Unique private int globe$currentPhraseIdx = 0;

    // ── Compass needle animation state ──
    @Unique private double globe$needleAngle = 0.0;
    @Unique private double globe$needleTarget = Math.PI * 0.5;
    @Unique private long globe$lastDirectionChangeMs = 0L;
    @Unique private static final long DIRECTION_CHANGE_INTERVAL_MS = 2200;
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

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void globe$renderLatitudeOverlay(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LatitudeClientState.isLatitudeWorldLoading()) {
            globe$overlayStartMs = 0L;
            globe$displayProgress = 0f;
            return;
        }

        long now = Util.getMillis();
        if (globe$overlayStartMs == 0L) {
            globe$overlayStartMs = now;
            globe$lastDirectionChangeMs = now;
            globe$displayProgress = 0f;
            globe$phraseSeedIdx = globe$pickSeedIndex();
            GLOBE_LOGGER.info("[LAT][LOADUI] bespoke overlay first render — {}ms since beginExpedition",
                    LatitudeClientState.elapsedSinceExpeditionMs());
        } else if (LatitudeClientState.elapsedSinceExpeditionMs() >= FAIL_SAFE_CLEAR_MS) {
            globe$clearLoadingFlagNow(true);
            return;
        }
        long elapsed = now - globe$overlayStartMs;

        int sw = context.guiWidth();
        int sh = context.guiHeight();

        // ── Brown pane (centered, covers the vanilla chunk grid) ──
        int paneW = Math.min(sw - 40, 340);
        int paneH = Math.min(sh - 40, 200);
        int paneX = (sw - paneW) / 2;
        int paneY = (sh - paneH) / 2;

        // Border
        context.fill(paneX - 1, paneY - 1, paneX + paneW + 1, paneY + paneH + 1, PANE_BORDER);
        // Fill
        context.fill(paneX, paneY, paneX + paneW, paneY + paneH, PANE_BG);

        // ── Grid decoration ──
        globe$drawGrid(context, paneX, paneY, paneW, paneH);

        // ── Title ──
        int cx = sw / 2;
        int titleY = paneY + 12;
        globe$drawCentered(context, "LATITUDE", cx, titleY, GOLD, true);

        // ── Loading hint ──
        globe$drawCentered(context, "Press F9 in-game for HUD options", cx, titleY + 12, MUTED, false);

        // ── Compass with wandering needle ──
        int compassCY = paneY + paneH / 2 - 4;
        int compassR = Math.min(28, Math.min(paneW, paneH) / 5);
        globe$updateNeedle(now, delta);
        globe$drawCompass(context, cx, compassCY, compassR);

        // ── Rotating phrase with fade ──
        int phraseY = compassCY + compassR + 16;
        globe$drawPhrase(context, cx, phraseY, elapsed);

        // ── Progress bar (strictly bounded by vanilla progress) ──
        int barW = Math.min(160, paneW - 40);
        int barX = cx - barW / 2;
        int barY = paneY + paneH - 20;
        float rawProgress = Mth.clamp(this.smoothedProgress, 0f, 1f);
        LatitudeClientState.latitudeLoadingProgress = rawProgress;
        globe$displayProgress = rawProgress;
        float progress = rawProgress;
        context.fill(barX, barY, barX + barW, barY + 3, 0xFF1A1410);
        int fillW = Math.round(progress * barW);
        if (fillW > 0) {
            context.fill(barX, barY, barX + fillW, barY + 3, GOLD);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void globe$clearLoadingFlag(CallbackInfo ci) {
        if (LatitudeClientState.isLatitudeWorldLoading()) {
            ci.cancel();
            return;
        }
        long sinceExpedition = LatitudeClientState.elapsedSinceExpeditionMs();
        if (sinceExpedition < 0L) {
            sinceExpedition = LatitudeClientState.lastLifecycleClearElapsedMs();
        }
        GLOBE_LOGGER.info("[LAT][LOADUI] loading screen closed — {}ms since beginExpedition",
                sinceExpedition);
        globe$overlayStartMs = 0L;
        globe$displayProgress = 0f;
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
        globe$overlayStartMs = 0L;
        globe$displayProgress = 0f;
    }

    // ════════════════════════════════════════════
    // Drawing helpers
    // ════════════════════════════════════════════

    @Unique
    private void globe$drawCentered(GuiGraphicsExtractor context, String text, int cx, int y, int color, boolean shadow) {
        int w = this.font.width(text);
        context.text(this.font, text, cx - w / 2, y, color, shadow);
    }

    @Unique
    private void globe$updateNeedle(long now, float delta) {
        // Change target direction at intervals — random wandering
        if (now - globe$lastDirectionChangeMs > DIRECTION_CHANGE_INTERVAL_MS) {
            globe$lastDirectionChangeMs = now;
            // Pick a new random target angle (full 360°)
            globe$needleTarget += (Math.PI * 0.4) + (Math.random() * Math.PI * 1.2);
            // Randomly reverse direction sometimes
            if (Math.random() < 0.35) {
                globe$needleTarget = globe$needleAngle - (globe$needleTarget - globe$needleAngle);
            }
        }
        // Smooth interpolation toward target
        double diff = globe$needleTarget - globe$needleAngle;
        // Normalize diff to [-PI, PI]
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        globe$needleAngle += diff * 0.03 * delta;
    }

    @Unique
    private void globe$drawCompass(GuiGraphicsExtractor context, int cx, int cy, int radius) {
        // Compass face — dark circle with gold ring
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int dist2 = dx * dx + dy * dy;
                if (dist2 <= r2) {
                    int px = cx + dx;
                    int py = cy + dy;
                    if (dist2 > (radius - 2) * (radius - 2)) {
                        context.fill(px, py, px + 1, py + 1, GOLD);
                    } else {
                        context.fill(px, py, px + 1, py + 1, 0xFF1A1410);
                    }
                }
            }
        }

        // Cardinal tick marks (N/S/E/W)
        int tickLen = Math.max(2, radius / 6);
        // N tick — white
        context.fill(cx, cy - radius + 2, cx + 1, cy - radius + 2 + tickLen, WARM_WHITE);
        // S
        context.fill(cx, cy + radius - 2 - tickLen, cx + 1, cy + radius - 2, MUTED);
        // E
        context.fill(cx + radius - 2 - tickLen, cy, cx + radius - 2, cy + 1, MUTED);
        // W
        context.fill(cx - radius + 2, cy, cx - radius + 2 + tickLen, cy + 1, MUTED);

        // Red 'N' label at north
        String nLabel = "N";
        int nW = this.font.width(nLabel);
        context.text(this.font, nLabel, cx - nW / 2 + 1, cy - radius + 2 + tickLen + 1, 0xFFCC3333, true);

        // Wandering needle
        double angle = globe$needleAngle;
        int needleLen = radius - 4;

        // Red north half
        int nx = cx + (int) Math.round(Math.sin(angle) * needleLen);
        int ny = cy - (int) Math.round(Math.cos(angle) * needleLen);
        globe$drawLine(context, cx, cy, nx, ny, 0xFFCC3333);

        // White south half (shorter)
        int sx = cx - (int) Math.round(Math.sin(angle) * (needleLen * 0.6));
        int sy = cy + (int) Math.round(Math.cos(angle) * (needleLen * 0.6));
        globe$drawLine(context, cx, cy, sx, sy, WARM_WHITE);

        // Center dot
        context.fill(cx - 1, cy - 1, cx + 2, cy + 2, GOLD);
    }

    @Unique
    private void globe$drawLine(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            context.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    @Unique
    private void globe$drawPhrase(GuiGraphicsExtractor context, int cx, int y, long elapsedMs) {
        long cyclePos = elapsedMs % PHRASE_CYCLE_MS;
        long cycleNo = elapsedMs / PHRASE_CYCLE_MS;
        if (cycleNo != globe$lastCycleNo) {
            if (globe$lastCycleNo < 0) {
                // First shown phrase leads with a Latitude-feature line (preserves the fast-load property:
                // even a single-phrase load shows a newer 1.4 splash, not a generic one).
                globe$currentPhraseIdx = globe$phraseSeedIdx > 0 ? globe$phraseSeedIdx : globe$pickSeedIndex();
            } else {
                globe$currentPhraseIdx = globe$pickNextPhrase();
            }
            globe$lastCycleNo = cycleNo;
        }
        int phraseIdx = globe$currentPhraseIdx;
        String phrase = PHRASES[phraseIdx];

        // Fade: quick in (first 15%), steady (60%), quick out (last 25%)
        float t = (float) cyclePos / PHRASE_CYCLE_MS;
        float alpha;
        if (t < 0.15f) {
            alpha = t / 0.15f;
        } else if (t < 0.75f) {
            alpha = 1.0f;
        } else {
            alpha = 1.0f - (t - 0.75f) / 0.25f;
        }
        alpha = Mth.clamp(alpha, 0f, 1f);

        int a = Math.round(alpha * 255);
        if (a < 4) return;
        int color = (a << 24) | (WARM_WHITE & 0x00FFFFFF);

        int w = this.font.width(phrase);
        context.text(this.font, phrase, cx - w / 2, y, color, false);
    }

    @Unique
    private static void globe$drawGrid(GuiGraphicsExtractor context, int paneX, int paneY, int paneW, int paneH) {
        for (int gy = GRID_STEP; gy < paneH; gy += GRID_STEP) {
            context.fill(paneX, paneY + gy, paneX + paneW, paneY + gy + 1, GRID_COLOR);
        }
        for (int gx = GRID_STEP; gx < paneW; gx += GRID_STEP) {
            context.fill(paneX + gx, paneY, paneX + gx + 1, paneY + paneH, GRID_COLOR);
        }
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
        int renderedSections = client.levelRenderer.countRenderedSections();
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
