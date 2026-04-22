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
            "Aligning magnetic north..."
    };

    @Unique private static final long PHRASE_CYCLE_MS = 4800;
    @Unique private static final long FAIL_SAFE_CLEAR_MS = 10 * 60 * 1000L;
    @Unique private long globe$overlayStartMs = 0L;
    @Unique private float globe$displayProgress = 0f;
    @Unique private int globe$phraseSeedIdx = 0;

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

            long clearedAt = LatitudeClientState.clearLatitudeLoadingState();
            GLOBE_LOGGER.info("[Latitude lifecycle] bespoke overlay cleared at game-join — {}ms since beginExpedition",
                    clearedAt);

            if (client.screen instanceof LevelLoadingScreen screen) {
                screen.onClose();
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
            globe$phraseSeedIdx = (int) (Math.random() * PHRASES.length);
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
            // Keep this screen as the bespoke presentation until the normal client-ready clear.
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
        int phraseIdx = (globe$phraseSeedIdx + (int) ((elapsedMs / PHRASE_CYCLE_MS) % PHRASES.length)) % PHRASES.length;
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

    @Shadow
    public ClientLevel level;

    @Shadow
    public LocalPlayer player;

    @Inject(method = "tick", at = @At("TAIL"))
    private void globe$clearLoadingOnClientReadyTick(CallbackInfo ci) {
        if (!LatitudeClientState.isLatitudeWorldLoading()) {
            return;
        }

        long sinceExpedition = LatitudeClientState.elapsedSinceExpeditionMs();
        if (sinceExpedition >= FAIL_SAFE_CLEAR_MS) {
            long clearedAt = LatitudeClientState.clearLatitudeLoadingState();
            GLOBE_LOGGER.info("[Latitude lifecycle] bespoke overlay cleared by fail-safe — {}ms since beginExpedition",
                    clearedAt);
            return;
        }

        if (this.level == null || this.player == null) {
            return;
        }

        if (LatitudeClientState.markClientReadyObserved()) {
            GLOBE_LOGGER.info("[Latitude lifecycle] player/world became client-ready — {}ms since beginExpedition",
                    LatitudeClientState.elapsedSinceExpeditionMs());
        }

        Minecraft client = (Minecraft) (Object) this;
        boolean pastFirstPlayerTick = this.player.tickCount > 1;
        boolean noLongerLoadingScreen = !(client.screen instanceof LevelLoadingScreen);
        if (!pastFirstPlayerTick && !noLongerLoadingScreen) {
            return;
        }
        GLOBE_LOGGER.info("[Latitude lifecycle] first safe playable tick — {}ms since beginExpedition (playerAge={}, loadingScreenVisible={})",
                LatitudeClientState.elapsedSinceExpeditionMs(),
                this.player.tickCount,
                !noLongerLoadingScreen);

        long clearedAt = LatitudeClientState.clearLatitudeLoadingState();
        GLOBE_LOGGER.info("[Latitude lifecycle] bespoke overlay cleared by normal client-ready path — {}ms since beginExpedition",
                clearedAt);
    }
}
