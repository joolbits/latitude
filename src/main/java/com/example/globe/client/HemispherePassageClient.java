package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.example.globe.GlobeNet;
import com.example.globe.core.HemispherePassage;
import com.example.globe.core.LatitudeV2Flags;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Phase 5 Slice B-5-P2 (Hemisphere Passage) -- the CLIENT experience driver: the approach/prompt state machine
 * and the crossing curtain + arrival ceremony. All wall-clock timed (the title-family law: never tick-clock, so
 * the fades stay smooth through the tick stalls a teleport crossing causes). Entirely gated on
 * {@link LatitudeV2Flags#PASSAGE_V2_ENABLED} -- flag-off never touches a field, opens a screen, or draws a pixel.
 *
 * <p><b>Two machines, one tick.</b>
 * <ol>
 *   <li><b>Arm/prompt</b> ({@link #clientTick}): drives {@link HemispherePassage#evaluate} off the client-known
 *       border distance ({@link GlobeClientState#distanceToEwBorderBlocks(double)}) and opens the bespoke
 *       {@link HemispherePassagePromptScreen} exactly once when the player reaches the prompt band -- but only
 *       when no other screen is open (never stomp a container) and no curtain is up.</li>
 *   <li><b>Curtain</b> ({@link #driveCurtain}): after "Pass through" a full-screen opaque curtain masks the
 *       teleport + chunk load. It fades in, HOLDS opaque until the S2C {@link HemispherePassageClientState}
 *       arrival lands (consumed here), then runs the arrival ceremony (seed the arm state DISARMED-in-band per
 *       A9, fire the E/W arrival title, one-shot whoosh) and fades out. If no arrival lands within
 *       {@link #CURTAIN_TIMEOUT_MS} (server rejected the cross) it fades out anyway and leaves the player
 *       disarmed-in-band -- the player is NEVER stuck behind an opaque screen.</li>
 * </ol>
 */
public final class HemispherePassageClient {

    /** Curtain fade-in (ms): quick opaque wipe that masks the teleport. */
    private static final long CURTAIN_FADE_IN_MS = 300L;
    /** Curtain fade-out (ms): the slow "title-melt" dissolve back to the world after arrival. */
    private static final long CURTAIN_FADE_OUT_MS = 850L;
    /** Max ms to hold the curtain waiting for the S2C arrival before giving up (server rejected: cooldown /
     *  unsafe far shore / spoof guard). The player must never be trapped behind the opaque fill. */
    private static final long CURTAIN_TIMEOUT_MS = 5000L;

    /** Crossing whoosh (A6): a single existing vanilla wind-rush -- {@code ELYTRA_FLYING} played ONCE (not the
     *  looping polar wind bed) at a low pitch reads as a gust of passage. No loop, no new asset. */
    private static final float WHOOSH_VOLUME = 0.6f;
    private static final float WHOOSH_PITCH = 0.7f;

    /** Near-white opaque curtain colour (the storm-white endpoint shared with the whiteout/edge palettes). */
    private static final int CURTAIN_R = 240;
    private static final int CURTAIN_G = 243;
    private static final int CURTAIN_B = 248;

    private enum Curtain { NONE, RAISING_HOLD, FADING }

    // Client arm state (mirrors the pure core.HemispherePassage arm). Starts armed; reset on non-globe worlds.
    private static boolean armed = true;

    private static Curtain curtain = Curtain.NONE;
    private static long curtainRaisedMs;   // wall time the curtain was raised (fade-in + timeout reference)
    private static long curtainFadeOutMs;  // wall time the fade-out began; 0 while raising/holding

    private HemispherePassageClient() {
    }

    /** World-switch hygiene (called from the client DISCONNECT hook): drop any in-flight curtain and re-arm so a
     *  crossing can never leak across worlds. Paired with {@link HemispherePassageClientState#reset()}. */
    public static void reset() {
        armed = true;
        curtain = Curtain.NONE;
        curtainRaisedMs = 0L;
        curtainFadeOutMs = 0L;
    }

    /** Raise the crossing curtain. Called from {@link HemispherePassagePromptScreen} on "Pass through", right
     *  after the C2S answer is sent, so the opaque fill is already climbing when the server teleports. */
    static void raiseCurtain() {
        // Sweep LOW-1: drain any STALE arrival before this crossing starts. If a previous crossing's S2C landed
        // AFTER its 5s curtain timeout (laggy multiplayer link), pendingArrival would still be set and THIS
        // crossing would consume it ~1 tick after raising -- a premature ceremony/fade over the wrong teleport.
        HemispherePassageClientState.consumePendingArrival();
        curtain = Curtain.RAISING_HOLD;
        curtainRaisedMs = System.currentTimeMillis();
        curtainFadeOutMs = 0L;
    }

    /** Send the player's answer to the prompt (C2S). The server re-validates edge distance and ignores a spoof. */
    static void sendAnswer(boolean cross) {
        ClientPlayNetworking.send(new GlobeNet.PassageAnswerPayload(cross));
    }

    /** Per-client-tick driver. Registered in {@code GlobeModClient.onInitializeClient}. */
    public static void clientTick(Minecraft mc) {
        if (!LatitudeV2Flags.PASSAGE_V2_ENABLED) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        long now = System.currentTimeMillis();

        // Curtain machine runs first (a crossing in progress owns the screen; arm evaluation pauses under it).
        driveCurtain(mc, now);

        if (!GlobeClientState.isGlobeWorld()) {
            armed = true; // re-arm for the next globe world
            return;
        }
        // While the curtain is up, a crossing is mid-flight -- do not evaluate the arm or open a prompt.
        if (curtain != Curtain.NONE) {
            return;
        }
        // Item 2 (surface-only): underground the passage does not exist -- there is just the vanilla border
        // wall. FREEZE the arm machine (return without touching `armed` or evaluating), rather than treating a
        // cave dip as a walk-out: a player who briefly ducks into a cave near the edge keeps their armed state
        // and is prompted the instant they surface, and a disarmed player is never re-armed by the cave. The
        // prompt therefore never opens below ground.
        if (GlobeClientState.isDeepUnderground(mc)) {
            return;
        }

        double distToEdge = GlobeClientState.distanceToEwBorderBlocks(mc.player.getX());
        HemispherePassage.Decision d = HemispherePassage.evaluate(armed, distToEdge);

        if (d.openPrompt()) {
            // Sweep A10 (third option): commit the disarm transition ONLY when the prompt ACTUALLY opens. If a
            // container (or any other screen) is up at the arm tick, stay ARMED and retry next tick -- the prompt
            // opens the moment the container closes instead of being silently consumed. No machine-gun risk: a
            // turn-back can only follow a prompt that OPENED, and opening is exactly what disarms.
            if (mc.gui.screen() == null) {
                armed = d.nextArmed(); // disarm-on-open, committed with the open itself
                // Which hemisphere lies BEYOND the fog: the crossing mirrors X (targetX = -x), so from the western
                // half (x < 0) you arrive in the East, and vice-versa.
                boolean beyondEast = mc.player.getX() < mc.level.getWorldBorder().getCenterX();
                mc.setScreenAndShow(new HemispherePassagePromptScreen(beyondEast));
            }
            // else: blocked by an open screen -- keep `armed` as-is (true) and re-evaluate next tick.
        } else {
            armed = d.nextArmed(); // all other transitions (re-arm on walk-out etc.) commit as before
        }
    }

    private static void driveCurtain(Minecraft mc, long now) {
        if (curtain == Curtain.NONE) {
            return;
        }
        if (curtain == Curtain.RAISING_HOLD) {
            if (HemispherePassageClientState.consumePendingArrival()) {
                onArrival(mc, now);
            } else if (now - curtainRaisedMs >= CURTAIN_TIMEOUT_MS) {
                // No arrival: the server rejected the cross (cooldown / unsafe far shore / spoof guard). Recover
                // gracefully -- disarm in-band (they stay put, no re-prompt until they leave & return) and melt
                // the curtain so they are never stuck behind it.
                GlobeMod.LOGGER.warn("[Latitude][Passage] Crossing curtain timed out ({}ms) with no arrival; recovering",
                        CURTAIN_TIMEOUT_MS);
                armed = false;
                // Sweep LOW-1 (other half): clear any arrival that lands BETWEEN this timeout decision and the
                // next crossing, so a late S2C can never linger into (and prematurely end) a future curtain.
                HemispherePassageClientState.reset();
                beginFadeOut(now);
            }
        } else { // FADING
            if (now - curtainFadeOutMs >= CURTAIN_FADE_OUT_MS) {
                curtain = Curtain.NONE;
                curtainFadeOutMs = 0L;
            }
        }
    }

    private static void onArrival(Minecraft mc, long now) {
        // A9: the far side sits at the IDENTICAL border distance, so seed the arm DISARMED-in-band or the prompt
        // would re-open on the very next tick forever. The pure class already supports external disarmed seeding.
        armed = false;
        boolean east = HemispherePassageClientState.arrivedEast();
        fireArrivalTitle(east);
        playCrossingWhoosh(mc);
        beginFadeOut(now);
    }

    private static void beginFadeOut(long now) {
        curtain = Curtain.FADING;
        curtainFadeOutMs = now;
    }

    /** Fire the "EASTERN/WESTERN HEMISPHERE" arrival title on the existing shared E/W hemisphere channel, using
     *  the same duration/scale the natural prime-meridian crossing uses (GlobeWarningOverlay#fireHemisphereLine). */
    private static void fireArrivalTitle(boolean east) {
        String line = east ? "Eastern Hemisphere" : "Western Hemisphere";
        int durationTicks = (int) Math.round(clamp(LatitudeConfig.zoneEnterTitleSeconds, 2.0, 10.0) * 20.0);
        double scale = clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);
        HemisphereTitleOverlay.trigger(true, line, durationTicks, scale);
    }

    private static void playCrossingWhoosh(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, WHOOSH_VOLUME, WHOOSH_PITCH, false);
    }

    // ---- render (called from InGameHudMixin, on top of everything so it masks the crossing) ----

    /** Current curtain opacity in {@code [0,1]}, wall-clock driven. 0 = nothing to draw. */
    private static float curtainAlpha(long now) {
        if (curtain == Curtain.NONE) {
            return 0.0f;
        }
        if (curtain == Curtain.RAISING_HOLD) {
            // Quick opaque wipe, then HOLD at 1. pow(0.65) ease (the PolarWhiteoutOverlayHud idiom).
            float t = clamp01((float) (now - curtainRaisedMs) / (float) CURTAIN_FADE_IN_MS);
            return (float) Math.pow(t, 0.65);
        }
        // FADING: cubic smoothstep dissolve from 1 back to 0 (the title-melt feel).
        float t = clamp01((float) (now - curtainFadeOutMs) / (float) CURTAIN_FADE_OUT_MS);
        float s = t * t * (3.0f - 2.0f * t); // smoothstep (a cubic)
        return 1.0f - s;
    }

    /** Draw the opaque crossing curtain (full-screen fill). No-op unless a crossing is in flight. */
    public static void renderCurtain(GuiGraphicsExtractor ctx) {
        if (!LatitudeV2Flags.PASSAGE_V2_ENABLED || curtain == Curtain.NONE) {
            return;
        }
        float a = curtainAlpha(System.currentTimeMillis());
        if (a <= 0.001f) {
            return;
        }
        int alpha = (int) (Math.min(1.0f, a) * 255.0f);
        int argb = (alpha << 24) | (CURTAIN_R << 16) | (CURTAIN_G << 8) | CURTAIN_B;
        ctx.fill(0, 0, ctx.guiWidth(), ctx.guiHeight(), argb);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }
}
