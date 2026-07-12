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
    @Unique private static final int GOLD = 0xFFE8B64A; // Chartroom latitude gold (Pillar 6 token)
    @Unique private static final int WARM_WHITE = 0xFFEDE0D0;
    @Unique private static final int MUTED = 0xFF8C8078;
    // Faint mechanics-note tint for the F9 hint (Peetsa TEST 79: "make the F9 line a little bit fainter to
    // reduce clashing" with the progress bar + stage line jammed under it). Same muted rose-brown RGB as
    // MUTED but dropped to ~60% alpha (0xFF -> 0x99) so it reads as a quiet aside, a step below the stage
    // line (which stays full-alpha MUTED). Still legible over the dark PANE_BG at 60%.
    @Unique private static final int F9_HINT = 0x998C8078;
    // Faint map graticule — a whisper of gold/parchment (review §4 R5 / F5): nudged from 8% neutral grey
    // (0x14504840) to ~12% warm gold-tinted (0x1E5A4A38) so the grid reads as chart paper, not grey static.
    @Unique private static final int GRID_COLOR = 0x1E5A4A38;
    @Unique private static final int GRID_STEP = 16;

    // ── Loading compass = the new DEFAULT compass look: SUNSET scheme + ROSE shape ──
    // SOURCE OF TRUTH: CompassHud.java:1520 — SUNSET DialColors(face=0x261712, ring=0xFFF2A65A,
    // muted=0xFFB07E62, needle=0xFFFF5E5B). This bespoke loading compass is hand-drawn (it can't share
    // CompassDialRenderer, which needs a live CompassHudConfig), so if that Sunset line ever changes,
    // update the four SUNSET_* constants below to match — that's the whole drift surface, one place.
    // (SUNSET_NEEDLE_TAIL is loading-screen-only, not part of the shared Sunset dial line.)
    @Unique private static final int SUNSET_RING = 0xFFF2A65A;   // amber ring (frames the compass)
    @Unique private static final int SUNSET_FACE = 0xFF261712;   // deep-plum face (0x261712 + full alpha) + needle-pivot dot
    @Unique private static final int SUNSET_MUTED = 0xFFB07E62;  // muted rose-brown (S/E/W ticks + the 8-point rose star, toned down so it backs the needle instead of rivalling it)
    @Unique private static final int SUNSET_NEEDLE = 0xFFFF5E5B; // coral needle NORTH half + N accent (the brightest, most saturated element)
    // The needle's SOUTH (tail) half is a warm off-white — it exists SOLELY to separate the wandering needle
    // from the amber-family rose behind it. Amber-on-amber (the old SUNSET_RING tail) vanished into the rose
    // star, so you couldn't tell which way the needle pointed; a pale tail reads instantly against amber.
    @Unique private static final int SUNSET_NEEDLE_TAIL = 0xFFF0EAE0; // warm off-white needle tail

    // ── Loading phrases ──
    // Ordered in TWO blocks: the FRONT block (indices 0..featuredStart-1) is texture/levity —
    // vegetation, geology filler, whimsical peak lines, and the guest-biome-hosting jokes — which the
    // seed/next-phrase logic below shows LESS often. The FEATURED tail (the last FEATURED_PHRASE_COUNT
    // entries) is the mod's signature voice: latitude/cartography/compass/climate + the GeoAuthority &
    // ClimateAuthority geology lines (rain shadows, windward/leeward, island arcs, faults, massifs,
    // continentality). The card always OPENS on a featured line and each swap is ~70% biased back into
    // the tail, so the featured block carries the brand. See the creative-director copy pass
    // docs/binder/loading-text-and-whisper-review-20260711.md (2026-07-11) for the per-phrase verdicts.
    // ANY new signature/feature line must go in the TAIL (and be counted into FEATURED_PHRASE_COUNT) or
    // it will almost never appear.
    @Unique private static final String[] PHRASES = {
            // ── FRONT block: texture / levity (shown less; NOT featured) ──
            "Packing snow boots...",
            "Planting bamboo groves...",
            "Planting spruce forests...",
            "Sprinkling wildflowers...",
            "Hiding ancient ruins...",
            "Dusting off badlands...",
            "Filling oceans carefully...",
            "Stacking tectonic plates...",
            "Folding mountain ranges...",
            "Nudging continents apart...",
            "Carving river valleys...",
            "Laying down riverbeds...",
            "Trimming the treeline...",
            "Talking trees down from the peaks...",
            "Frosting the highest peaks...",
            "Minding the alpine line...",
            "Welcoming the guest biomes...",
            "Evicting a few biome squatters...",
            "Filling out the biome bands...",
            "Untangling the guest biomes...",
            // ── FEATURED tail: signature latitude / cartography / compass / climate ──
            "Charting the frontier...",
            "Following the compass...",
            "Crossing climate bands...",
            "Calibrating the equator...",
            "Warming the tropics...",
            "Freezing the poles...",
            "Surveying the horizon...",
            "Stretching the horizon...",
            "Teaching villagers cartography...",
            "Polishing compass glass...",
            "Mapping trade winds...",
            "Tuning the jet stream...",
            "Rotating the planet...",
            "Aligning magnetic north...",
            "Sorting biomes by latitude...",
            "Unrolling more map...",
            "Giving the compass more to point at...",
            "Making room for two more oceans...",
            // navigate-by-latitude romance (new, on-brand)
            "Ruling in the parallels...",
            "Taking a sun-sighting...",
            "Boxing the compass...",
            "Setting the prime meridian...",
            "Marking the tropic lines...",
            "Inking the coastlines...",
            "Unfurling the parchment...",
            "Plotting your heading...",
            // GeoAuthority / ClimateAuthority geology (new — the mod's real simulation)
            "Casting rain shadows...",
            "Soaking the windward coasts...",
            "Drying the leeward slopes...",
            "Bending island arcs...",
            "Settling the ancient faults...",
            "Wearing down the old massifs...",
            "Pulling moisture off the sea...",
            "Deepening the continental interiors..."
    };

    // The Latitude-feature splashes are the last FEATURED_PHRASE_COUNT entries of PHRASES. Bias the
    // starting point into that block most of the time so the signature latitude/cartography/geology
    // phrases usually lead. Count = the whole featured tail above (signature 18 + 8 navigation + 8
    // geology). If you add/remove a tail line, update this so featuredStart still lands exactly on the
    // "Charting the frontier..." row (the first signature line).
    @Unique private static final int FEATURED_PHRASE_COUNT = 34;

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
        // Keywords cover every surviving peak/mountain line after the 2026-07-11 copy pass:
        // "Trimming the treeline", "Talking trees down from the peaks", "Frosting the highest peaks",
        // "Minding the alpine line", "Folding mountain ranges". (The old "summit"/"powder" lines were
        // cut, so those keywords were dropped.)
        return s.contains("peak") || s.contains("alpine")
                || s.contains("treeline") || s.contains("mountain");
    }

    @Unique private static final long PHRASE_CYCLE_MS = 4800;
    @Unique private static final long FAIL_SAFE_CLEAR_MS = 10 * 60 * 1000L;
    @Unique private long globe$overlayStartMs = 0L;
    // -1 = "not yet picked this overlay lifetime" (a real Unique field default of 0 would collide with a
    // legitimately-picked index of 0 -- unreachable today given the current PHRASES/FEATURED_PHRASE_COUNT
    // sizes, but not guaranteed to stay that way, and -1 is never a valid pickSeedIndex() result either way).
    @Unique private int globe$phraseSeedIdx = -1;
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
            return;
        }

        long now = Util.getMillis();
        if (globe$overlayStartMs == 0L) {
            globe$overlayStartMs = now;
            globe$lastDirectionChangeMs = now;
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

        // Chartroom frame (Pillar 6): the same vanilla parchment map frame the create screen's planisphere
        // uses, drawn just outside the pane so the loading card and the create screen read as one family.
        com.example.globe.client.create.LatitudePlanisphereRenderer.drawAtlasFrame(
                context, paneX - 10, paneY - 10, paneW + 20, paneH + 20);
        // Border
        context.fill(paneX - 1, paneY - 1, paneX + paneW + 1, paneY + paneH + 1, PANE_BORDER);
        // Fill
        context.fill(paneX, paneY, paneX + paneW, paneY + paneH, PANE_BG);

        // ── Grid decoration ──
        globe$drawGrid(context, paneX, paneY, paneW, paneH);

        // ── Title: shared LATITUDE wordmark (creative-director loading-look review 2026-07-11 §4 R1) ──
        // The mod's most-seen surface now leads with the SAME glowing, letterpressed, rule-flanked nameplate
        // the create screen uses (LatitudeWordmark) instead of plain gold text — quieter here (scale 1.4,
        // sparkles cut 4->2) since this card already animates the compass needle, phrase fade and word-wave.
        // Falls back to the plain gold line if the pane is too tight. Unified wordmark gold lives in the helper.
        int cx = sw / 2;
        int titleTop = paneY + 10;
        int wordmarkH = com.example.globe.client.create.LatitudeWordmark.draw(
                context, this.font, paneX, titleTop, paneW, 20,
                1.4f, 2, com.example.globe.client.LatitudeConfig.reduceMotion);
        int summaryY;
        if (wordmarkH > 0) {
            summaryY = titleTop + wordmarkH + 6;
        } else {
            globe$drawCentered(context, "LATITUDE", cx, paneY + 12, GOLD, true);
            summaryY = paneY + 12 + 12;
        }

        // ── World summary (set at beginExpedition; truthful — straight from the chosen options) ──
        // Passport gilding (review §4 R2): shape + zone tokens read gold, size + numerals stay warm-white.
        String summary = LatitudeClientState.loadingSummary;
        if (summary != null) {
            globe$drawSummaryWave(context, summary, cx, summaryY, WARM_WHITE, elapsed);
        }

        // ── F9 hint: moved back up under the title/summary (Peetsa TEST 81: "move the F9 line to under
        //    Latitude so it's less cluttered" — it had been living in the bottom mechanics zone crowding
        //    the bar/stage stack; that zone is calmer without it). Still faint (F9_HINT ~60% alpha) and
        //    small (0.75x, TEST 80) — a true footnote sitting in the open space below the identity block,
        //    whether or not a summary line is present.
        {
            float f9Scale = 0.75f;
            int f9Y = summaryY + 12;
            var f9m = context.pose();
            f9m.pushMatrix();
            f9m.translate(cx, (float) f9Y);
            f9m.scale(f9Scale, f9Scale);
            f9m.translate(-cx, (float) -f9Y);
            globe$drawCentered(context, "Press F9 in-game for HUD options", cx, f9Y, F9_HINT, false);
            f9m.popMatrix();
        }

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
        float progress = rawProgress;
        context.fill(barX, barY, barX + barW, barY + 3, 0xFF1A1410);
        int fillW = Math.round(progress * barW);
        if (fillW > 0) {
            context.fill(barX, barY, barX + fillW, barY + 3, GOLD);
        }

        // ── Truthful stage line (bound to the readiness gate's REAL booleans; see
        // LatitudeLoadingClientTickMixin below — never a made-up phase) ──
        String stage = LatitudeClientState.loadingStageLabel;
        if (stage != null) {
            // barY+8: a touch of air below the bar; leaves ~4px bottom margin inside the pane at paneH=200.
            globe$drawCentered(context, stage, cx, barY + 8, MUTED, false);
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
    }

    // ════════════════════════════════════════════
    // Drawing helpers
    // ════════════════════════════════════════════

    @Unique
    private void globe$drawCentered(GuiGraphicsExtractor context, String text, int cx, int y, int color, boolean shadow) {
        int w = this.font.width(text);
        context.text(this.font, text, cx - w / 2, y, color, shadow);
    }

    /**
     * Draws the world-summary "passport" line ("Itty Bitty · Square 1:1 · 7,500 × 7,500 · subpolar start") with
     * two treatments layered together:
     *
     * <ul>
     *   <li><b>Gilding</b> (review §4 R2 / F3): the QUALITATIVE tokens read gold, the MEASUREMENTS read
     *       warm-white — colour-coding "what kind of world" vs "how big". The summary shape is
     *       "&lt;size&gt; · &lt;shape&gt; · &lt;W × H&gt; · &lt;zone&gt; start", so segment 1 (shape) and the
     *       LAST segment (zone) are the world's character → gold ({@link #GOLD}); segment 0 (size) and the
     *       middle numerals stay warm-white ({@link #WARM_WHITE}). Separators stay neutral, drawn at the
     *       warm-white resting dim so they never out-shine a resting word.</li>
     *   <li><b>Reading-light wave</b> (Peetsa 2026-07-11): a gentle looping crest illuminates one segment at a
     *       time, now operating on EACH segment's own base colour (gold segments wave in gold-space, white in
     *       white-space) via {@link com.example.globe.core.ui.LoadingWave#shade}.</li>
     * </ul>
     *
     * <p>Layout is byte-for-byte the original centered line (full string measured once for centering, each token
     * drawn at its running x). Under Reduce Motion the wave freezes but the STATIC gold/white split is still
     * drawn (each segment at its plain base colour), per the helper's motion-policy-agnostic contract.
     */
    @Unique
    private void globe$drawSummaryWave(GuiGraphicsExtractor context, String summary, int cx, int y, int baseColor, long elapsed) {
        boolean reduceMotion = com.example.globe.client.LatitudeConfig.reduceMotion;
        String[] segments = com.example.globe.core.ui.LoadingWave.segments(summary);
        if (segments.length <= 1) {
            // No · separators (or empty) — no tokens to gild/wave across; draw plain so layout is untouched.
            globe$drawCentered(context, summary, cx, y, baseColor, false);
            return;
        }
        int lastIdx = segments.length - 1;
        int sepAlpha = WARM_WHITE & 0xFF000000;
        int sepRestRgb = com.example.globe.core.ui.LoadingWave.shade(WARM_WHITE & 0xFFFFFF, 0f);
        int fullW = this.font.width(summary);
        int x = cx - fullW / 2;
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                context.text(this.font, com.example.globe.core.ui.LoadingWave.SEPARATOR, x, y, sepAlpha | sepRestRgb, false);
                x += this.font.width(com.example.globe.core.ui.LoadingWave.SEPARATOR);
            }
            String seg = segments[i];
            // Gild the world's CHARACTER (shape = segment 1, zone = last segment); leave its MEASUREMENTS
            // (size = segment 0, numerals = the middle) warm-white.
            boolean gild = (i == 1 || i == lastIdx);
            int segBase = gild ? GOLD : WARM_WHITE;
            int alpha = segBase & 0xFF000000;
            int baseRgb = segBase & 0xFFFFFF;
            int rgb;
            if (reduceMotion) {
                rgb = baseRgb; // static gold/white split, no wave
            } else {
                // DIM-BASELINE + BRIGHT-CREST on this segment's OWN base colour (a lerp-toward-white "brighten"
                // was invisible on warm-white — the near-white-headroom trap the title glimmer hit).
                float g = com.example.globe.core.ui.LoadingWave.gaussian01(i, segments.length, elapsed);
                rgb = com.example.globe.core.ui.LoadingWave.shade(baseRgb, g);
            }
            context.text(this.font, seg, x, y, alpha | rgb, false);
            x += this.font.width(seg);
        }
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
        // The new DEFAULT compass look: SUNSET colours + ROSE shape (see the SUNSET_* constants, sourced from
        // CompassHud.java:1520). Span-batched face + amber ring (one fill per row segment; same technique as
        // CompassDialRenderer, ~2·diameter fills instead of πr² per-pixel fills), an amber 8-point rose star
        // over the plum face, and the KEPT wandering-needle animation restyled to coral. This is a hand-drawn
        // mirror of CompassDialRenderer.drawRose (which needs a live CompassHudConfig we don't have here).
        int rIn = radius - 2;
        for (int dy = -radius; dy <= radius; dy++) {
            int rem = radius * radius - dy * dy;
            if (rem < 0) continue;
            int half = (int) Math.sqrt(rem);
            int py = cy + dy;
            int remIn = rIn * rIn - dy * dy;
            int halfIn = remIn < 0 ? -1 : (int) Math.sqrt(remIn);
            if (halfIn < 0) {
                context.fill(cx - half, py, cx + half + 1, py + 1, SUNSET_RING);
            } else {
                context.fill(cx - half, py, cx - halfIn, py + 1, SUNSET_RING);
                context.fill(cx + halfIn + 1, py, cx + half + 1, py + 1, SUNSET_RING);
                context.fill(cx - halfIn, py, cx + halfIn + 1, py + 1, SUNSET_FACE);
            }
        }

        // ── ROSE star (mirrors CompassDialRenderer.drawRose so the loading compass reads as the default ROSE
        //    look): 4 long tapering cardinal diamonds + 4 short diagonal spokes. Drawn in the MUTED rose-brown
        //    (not amber) so it reads as a quiet backdrop the bright coral/off-white needle sits ON TOP of —
        //    an amber star was the same amber family as the needle's old tail and center dot, so the needle
        //    dissolved into it. The amber is reserved for the ring, which frames the compass. ──
        int starLen = radius - 4;
        if (starLen > 0) {
            int baseHalf = Math.max(1, radius / 8);
            for (int i = 0; i <= starLen; i++) {
                int h = Math.max(0, Math.round(baseHalf * (1.0f - i / (float) starLen)));
                context.fill(cx - h, cy - i, cx + h + 1, cy - i + 1, SUNSET_MUTED); // N
                context.fill(cx - h, cy + i, cx + h + 1, cy + i + 1, SUNSET_MUTED); // S
                context.fill(cx - i, cy - h, cx - i + 1, cy + h + 1, SUNSET_MUTED); // W
                context.fill(cx + i, cy - h, cx + i + 1, cy + h + 1, SUNSET_MUTED); // E
            }
            int diag = (int) Math.round(starLen * 0.55 / Math.sqrt(2));
            for (int s = -1; s <= 1; s += 2) {
                for (int t = -1; t <= 1; t += 2) {
                    globe$drawLine(context, cx, cy, cx + s * diag, cy + t * diag, SUNSET_MUTED);
                }
            }
        }

        // Cardinal tick marks (N/S/E/W). N gets the coral accent (matches the needle + N label); S/E/W stay
        // muted rose-brown, exactly as CompassDialRenderer.drawCardinalTicks colours them.
        int tickLen = Math.max(2, radius / 6);
        context.fill(cx, cy - radius + 2, cx + 1, cy - radius + 2 + tickLen, SUNSET_NEEDLE);       // N
        context.fill(cx, cy + radius - 2 - tickLen, cx + 1, cy + radius - 2, SUNSET_MUTED);        // S
        context.fill(cx + radius - 2 - tickLen, cy, cx + radius - 2, cy + 1, SUNSET_MUTED);        // E
        context.fill(cx - radius + 2, cy, cx - radius + 2 + tickLen, cy + 1, SUNSET_MUTED);        // W

        // Coral 'N' label at north
        String nLabel = "N";
        int nW = this.font.width(nLabel);
        context.text(this.font, nLabel, cx - nW / 2 + 1, cy - radius + 2 + tickLen + 1, SUNSET_NEEDLE, true);

        // Wandering needle (animation KEPT — globe$updateNeedle still drives it; only the colours change)
        double angle = globe$needleAngle;
        int needleLen = radius - 4;

        // Coral north half — the brightest, most saturated element; it's what the eye locks onto
        int nx = cx + (int) Math.round(Math.sin(angle) * needleLen);
        int ny = cy - (int) Math.round(Math.cos(angle) * needleLen);
        globe$drawLine(context, cx, cy, nx, ny, SUNSET_NEEDLE);

        // Off-white south half (shorter) — pale tail reads clearly against the amber-family rose (was amber,
        // which vanished into the rose star; this is the change that stops the needle competing with the rose)
        int sx = cx - (int) Math.round(Math.sin(angle) * (needleLen * 0.6));
        int sy = cy + (int) Math.round(Math.cos(angle) * (needleLen * 0.6));
        globe$drawLine(context, cx, cy, sx, sy, SUNSET_NEEDLE_TAIL);

        // Center pivot dot — deep plum, so the needle's hub separates from the (now muted) rose center
        // instead of merging with an amber blob at the middle
        context.fill(cx - 1, cy - 1, cx + 2, cy + 2, SUNSET_FACE);
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
                globe$currentPhraseIdx = globe$phraseSeedIdx >= 0 ? globe$phraseSeedIdx : globe$pickSeedIndex();
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
            globe$notifyFailSafe(clearedAt);
            globe$clientReadyObservedAtMs = Long.MIN_VALUE;
            globe$lastReadinessWaitLogTick = Long.MIN_VALUE;
            return;
        }

        if (this.level == null || this.player == null) {
            // Server is still building the dimension — the only stage where no client world exists yet.
            LatitudeClientState.loadingStageLabel = "Shaping the world...";
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
        boolean loadingScreenVisible = client.gui.screen() instanceof LevelLoadingScreen;
        boolean playerSettled = this.player.tickCount >= PLAYABLE_READY_MIN_PLAYER_TICKS;
        boolean spawnChunksReady = globe$clientSpawnChunkRingReady();
        RenderReadiness renderReadiness = globe$clientRenderReadiness(client);
        long clientReadyHoldMs = Math.max(0L, Util.getMillis() - globe$clientReadyObservedAtMs);
        boolean renderWarmupElapsed = clientReadyHoldMs >= PLAYABLE_READY_MIN_RENDER_HOLD_MS;
        boolean renderSignalTimedOut = clientReadyHoldMs >= PLAYABLE_READY_RENDER_SIGNAL_MAX_HOLD_MS;
        boolean renderReady = renderReadiness.ready() || renderSignalTimedOut;
        boolean readinessTimedOut = clientReadyHoldMs >= PLAYABLE_READY_MAX_HOLD_MS;
        if ((!playerSettled || !spawnChunksReady || !renderWarmupElapsed || !renderReady) && !readinessTimedOut) {
            // Truthful stage: named after the FIRST gate condition actually blocking, in gate order.
            LatitudeClientState.loadingStageLabel = !playerSettled ? "Placing you on the map..."
                    : !spawnChunksReady ? "Laying out the nearby land..."
                    : "Painting the horizon...";
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
    private void globe$notifyFailSafe(long sinceExpeditionMs) {
        // Plain-language chat line (Peetsa: non-programmer-facing text, keep jargon in the binder/logs).
        // The technical detail (exact elapsed ms, "10-minute fail-safe") stays in the GLOBE_LOGGER.info
        // line right before this is called -- the player only needs to know the screen closed and why.
        try {
            if (this.player != null) {
                this.player.sendSystemMessage(Component.literal(
                        "Latitude: the loading screen took too long and was closed. Your world may still be finishing up in the background."));
            }
        } catch (Throwable t) {
            GLOBE_LOGGER.warn("[Latitude lifecycle] fail-safe chat notice failed", t);
        }
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
        int renderedSections = client.levelRenderer.visibleSections().size();
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
