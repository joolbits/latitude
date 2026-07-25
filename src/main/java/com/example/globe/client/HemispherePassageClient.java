package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.example.globe.GlobeNet;
import com.example.globe.core.HemispherePassage;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PassageAxis;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Phase 5 Slice B-5-P2 / B-7-P2 (Hemisphere Passage) -- the CLIENT experience driver: the approach/prompt state
 * machine(s) and the crossing curtain + arrival ceremony. All wall-clock timed (the title-family law: never
 * tick-clock, so the fades stay smooth through the tick stalls a teleport crossing causes).
 *
 * <p><b>TWO arms, ONE curtain (B-7 P2).</b> The pure {@link HemispherePassage#evaluatePhase} machine is
 * instantiated TWICE -- an EW arm (the antimeridian, {@link LatitudeV2Flags#PASSAGE_V2_ENABLED}) and a POLE arm
 * (the N/S pole, {@link LatitudeV2Flags#POLE_PASSAGE_V2_ENABLED}) -- keyed on the same synced radii the server
 * uses. Each arm holds only its own {@link HemispherePassage.Phase}; the gesture edge-trackers + rate limit and
 * the CURTAIN are SHARED (one crossing at a time). Flag-off for an axis never touches that arm's field, opens a
 * screen, or sends an answer. The EW arm's behaviour is byte-identical to B-5.
 *
 * <p><b>Curtain.</b> After "Pass through" a full-screen opaque curtain masks the teleport + chunk load: fade in,
 * HOLD opaque until the S2C {@link HemispherePassageClientState} arrival lands, then run the arrival ceremony
 * (seed the crossing arm {@code SEEDED_DISARMED} on the prompt line, fire the arrival title, one-shot whoosh)
 * and fade out. If no arrival lands within {@link #CURTAIN_TIMEOUT_MS} it fades out anyway (server rejected) --
 * the player is NEVER stuck behind the opaque fill. The POLE fade-in is slightly faster (A1) so the yaw+180
 * camera whip is masked sooner.
 */
public final class HemispherePassageClient {

    /** Curtain fade-in (ms): quick opaque wipe that masks the teleport (EW). */
    private static final long CURTAIN_FADE_IN_MS = 300L;
    /** B-7 A1: a faster pole fade-in (~2x). The over-the-pole crossing whips the camera by yaw+180; a shorter
     *  semi-transparent wipe reaches the opaque HOLD sooner, so the whip stays fully behind the curtain. This
     *  is a per-raise value -- the EW fade-in is untouched. */
    private static final long CURTAIN_FADE_IN_POLE_MS = 150L;
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

    // Per-arm passage state (mirrors the pure core.HemispherePassage.Phase machine). Both start ARMED; reset on
    // non-globe worlds. The post-arrival phases (SEEDED_DISARMED / EDGE_PROMPTED) carry the edge-flow rework's
    // one-shot auto-re-prompt; ARMED/DISARMED are the ordinary approach cycle.
    private static HemispherePassage.Phase ewPhase = HemispherePassage.Phase.ARMED;
    private static HemispherePassage.Phase polePhase = HemispherePassage.Phase.ARMED;

    // TEST 92/93 click-at-the-wall re-prompt. Rising-edge detection on the USE key (right-click) AND the ATTACK
    // key (left-click) + a shared rate limit, so a disarmed player at the wall re-summons the crossing prompt
    // with a single click of EITHER button (facing the wall), without walking out and back. SHARED across both
    // arms (one physical click) -- the shared rate limit also serialises a Wide-corner double (design A5): the
    // first qualifying arm re-arms and consumes the window, the other waits for the next click. Reset with the
    // arms on world switch.
    private static boolean useWasDown = false;
    // 0L, NOT Long.MIN_VALUE: the rate guard computes (now - last), and now - MIN_VALUE OVERFLOWS to a huge
    // NEGATIVE value, which reads as "gestured an instant ago" and blocks the gesture FOREVER (sweeper BLOCKER,
    // proven in real Java). 0L makes the first gesture trivially pass.
    private static boolean attackWasDown = false;
    private static long lastRearmGestureMs = 0L;
    /** Rate limit for the re-prompt gesture (~20 ticks), shared across arms. */
    private static final long REARM_GESTURE_MIN_INTERVAL_MS = 1000L;

    private static Curtain curtain = Curtain.NONE;
    /** Which arm raised the current curtain (drives the timeout disarm + the per-raise fade-in + debug tags). */
    private static PassageAxis curtainAxis = PassageAxis.EW;
    private static long curtainRaisedMs;   // wall time the curtain was raised (fade-in + timeout reference)
    private static long curtainFadeOutMs;  // wall time the fade-out began; 0 while raising/holding
    private static long curtainFadeInMs = CURTAIN_FADE_IN_MS; // this crossing's fade-in duration (EW vs POLE)

    private HemispherePassageClient() {
    }

    private static HemispherePassage.Phase phaseFor(PassageAxis axis) {
        return axis == PassageAxis.POLE ? polePhase : ewPhase;
    }

    private static void setPhase(PassageAxis axis, HemispherePassage.Phase p) {
        if (axis == PassageAxis.POLE) {
            polePhase = p;
        } else {
            ewPhase = p;
        }
    }

    /** World-switch hygiene (called from the client DISCONNECT hook): drop any in-flight curtain and re-arm both
     *  arms so a crossing can never leak across worlds. Paired with {@link HemispherePassageClientState#reset()}. */
    public static void reset() {
        ewPhase = HemispherePassage.Phase.ARMED;
        polePhase = HemispherePassage.Phase.ARMED;
        useWasDown = false;
        attackWasDown = false;
        lastRearmGestureMs = 0L; // see field note: MIN_VALUE overflows the rate guard and bricks the gesture
        curtain = Curtain.NONE;
        curtainAxis = PassageAxis.EW;
        curtainRaisedMs = 0L;
        curtainFadeOutMs = 0L;
        curtainFadeInMs = CURTAIN_FADE_IN_MS;
        PassageDebug.reset();
    }

    /** Raise the crossing curtain for {@code axis}. Called from {@link HemispherePassagePromptScreen} on "Pass
     *  through", right after the C2S answer is sent, so the opaque fill is already climbing when the server
     *  teleports. */
    static void raiseCurtain(PassageAxis axis) {
        // Sweep LOW-1: drain any STALE arrival before this crossing starts, so a previous crossing's late S2C
        // can't be consumed ~1 tick after raising (a premature ceremony over the wrong teleport).
        if (HemispherePassageClientState.consumePendingArrival()) {
            PassageDebug.onStaleArrivalDrained();
        }
        curtainAxis = axis;
        curtainFadeInMs = axis == PassageAxis.POLE ? CURTAIN_FADE_IN_POLE_MS : CURTAIN_FADE_IN_MS;
        curtain = Curtain.RAISING_HOLD;
        curtainRaisedMs = System.currentTimeMillis();
        curtainFadeOutMs = 0L;
        PassageDebug.onCurtainRaised(axis);
    }

    /** Send the player's answer to the prompt (C2S) on {@code axis}. The server re-validates edge distance for
     *  that axis and ignores a spoof. HARD LAW (design item 2): this is only ever reachable from an OPEN prompt
     *  screen, which opens only when that arm's {@link HemispherePassage#evaluatePhase} returned openPrompt -- a
     *  SEEDED/DISARMED arm never answers. */
    static void sendAnswer(boolean cross, PassageAxis axis) {
        PassageDebug.onAnswer(axis, cross);
        ClientPlayNetworking.send(new GlobeNet.PassageAnswerPayload(cross, axis));
    }

    /** Per-client-tick driver. Registered in {@code GlobeModClient.onInitializeClient}. */
    public static void clientTick(Minecraft mc) {
        boolean ewOn = LatitudeV2Flags.PASSAGE_V2_ENABLED;
        boolean poleOn = LatitudeV2Flags.POLE_PASSAGE_V2_ENABLED;
        if (!ewOn && !poleOn) {
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
            ewPhase = HemispherePassage.Phase.ARMED;   // re-arm both for the next globe world
            polePhase = HemispherePassage.Phase.ARMED;
            return;
        }
        // While the curtain is up, a crossing is mid-flight -- do not evaluate the arms or open a prompt.
        if (curtain != Curtain.NONE) {
            if (ewOn) {
                PassageDebug.snapshotCurtainUp(PassageAxis.EW, mc, ewPhase,
                        GlobeClientState.distanceToEwBorderBlocks(mc.player.getX()), curtain.name());
            }
            if (poleOn) {
                PassageDebug.snapshotCurtainUp(PassageAxis.POLE, mc, polePhase,
                        GlobeClientState.distanceToPoleBlocks(mc.player.getZ()), curtain.name());
            }
            return;
        }

        // Shared re-prompt gesture rising-edge, computed ONCE per tick (both arms read the same physical keys).
        // Advance BOTH trackers every tick so a held button is not a repeat. Advanced BEFORE the per-arm config
        // gate so flipping borderRepromptGesture back on never treats a still-held button as a fresh edge.
        boolean useDown = mc.options.keyUse.isDown();
        boolean attackDown = mc.options.keyAttack.isDown();
        boolean risingEdge = (useDown && !useWasDown) || (attackDown && !attackWasDown);
        useWasDown = useDown;
        attackWasDown = attackDown;

        // Serialise the shared screen: if the EW arm opens a prompt this tick, the POLE arm must not also open
        // (the shared curtain/screen gate; the loser HOLDS armed and opens on a later tick -- design A5/Q2).
        boolean openedThisTick = false;
        if (ewOn) {
            openedThisTick = driveArm(mc, PassageAxis.EW, now, risingEdge, false);
        }
        if (poleOn) {
            driveArm(mc, PassageAxis.POLE, now, risingEdge, openedThisTick);
        }
    }

    /**
     * Evaluate ONE arm this tick: read its distance + geometry + surface gate, run the shared re-prompt gesture,
     * step the pure phase machine, and open the axis's prompt if it fires. Returns true iff it opened a prompt
     * this tick (so the caller can keep the other arm from also opening under the shared screen).
     */
    private static boolean driveArm(Minecraft mc, PassageAxis axis, long now, boolean risingEdge,
                                    boolean otherOpenedThisTick) {
        var border = mc.level.getWorldBorder();
        double distToEdge;
        double promptDist;
        double rearmDist;
        double edgeRepromptDist;
        boolean surfaceBlocked;
        if (axis == PassageAxis.POLE) {
            distToEdge = GlobeClientState.distanceToPoleBlocks(mc.player.getZ());
            com.example.globe.core.PoleGeometry.Resolved geo = GlobeClientState.poleGeometry(border);
            promptDist = geo.promptDist();
            rearmDist = geo.rearmDist();
            edgeRepromptDist = geo.edgeRepromptDist();
            surfaceBlocked = poleSurfaceBlocked(mc); // S2: in-water OR no-sky suppresses the pole prompt
        } else {
            distToEdge = GlobeClientState.distanceToEwBorderBlocks(mc.player.getX());
            com.example.globe.core.EdgeGeometry.Resolved geo = GlobeClientState.edgeGeometry(border);
            promptDist = geo.promptDist();
            rearmDist = geo.rearmDist();
            edgeRepromptDist = geo.edgeRepromptDist();
            surfaceBlocked = GlobeClientState.isDeepUnderground(mc);
        }

        // The prompt may only OPEN on the surface AND when no other screen is up (never stomp a container) AND
        // when the other arm did not just open one. This gates ONLY the open -- NOT the arm: re-arm is a pure
        // function of distance and MUST keep running through any terrain (the TEST 83 lesson).
        net.minecraft.client.gui.screens.Screen openScreen = mc.gui.screen();
        boolean canOpenPrompt = !surfaceBlocked && openScreen == null && !otherOpenedThisTick;

        // Click-at-the-wall re-prompt: runs BEFORE evaluatePhase so a re-arm takes effect the same tick.
        maybeRearmGesture(mc, axis, risingEdge, distToEdge, promptDist, canOpenPrompt, now);

        HemispherePassage.Phase prevPhase = phaseFor(axis);
        HemispherePassage.PhaseDecision d = HemispherePassage.evaluatePhase(prevPhase, distToEdge, canOpenPrompt,
                promptDist, rearmDist, edgeRepromptDist);
        setPhase(axis, d.nextPhase()); // persist unconditionally (open/disarm, sticky-hold, re-arm, EDGE one-shot)
        PassageDebug.snapshot(axis, mc, distToEdge, prevPhase, phaseFor(axis), d, surfaceBlocked, openScreen,
                curtain.name());

        if (d.openPrompt()) {
            if (axis == PassageAxis.POLE) {
                // S10d crossing legibility: the prompt names the DESTINATION meridian, computed from the
                // player's CURRENT x through the SAME shared paths the crossing will use --
                // PoleArrivalSearch.antipodalX (longitude L -> L+180) then farMeridianLabel (the arrival
                // subtitle's formatter) -- so the promise, the teleport and the arrival subtitle agree.
                double centerX = border.getCenterX();
                double xRadius = com.example.globe.util.LatitudeMath.intendedXRadius(border);
                String destination = HemispherePassage.farMeridianLabel(
                        com.example.globe.core.PoleArrivalSearch.antipodalX(mc.player.getX(), centerX, xRadius),
                        centerX, xRadius);
                PassageDebug.onPromptOpen(axis, distToEdge, destination);
                mc.setScreenAndShow(HemispherePassagePromptScreen.forPole(destination));
            } else {
                // The crossing mirrors X (targetX = -x), so from the western half (x < 0) you arrive in the East.
                boolean beyondEast = mc.player.getX() < border.getCenterX();
                PassageDebug.onPromptOpen(axis, distToEdge, beyondEast ? "East" : "West");
                mc.setScreenAndShow(new HemispherePassagePromptScreen(beyondEast));
            }
            return true;
        }
        return false;
    }

    /**
     * Re-prompt gesture glue for ONE arm: given the shared rising edge (computed once in {@link #clientTick}),
     * the player's facing, and the band, re-arm this axis when {@link HemispherePassage#rearmGestureArms}
     * approves. Only ever sets the phase to ARMED; the caller's {@link HemispherePassage#evaluatePhase} then
     * opens the prompt under the usual surface/no-screen gate. The facing test is axis-specific:
     * {@link HemispherePassage#facingOutwardX} toward the E/W wall for EW, {@link HemispherePassage#facingPolewardZ}
     * toward the nearer pole for POLE. Gated by {@link LatitudeConfig#borderRepromptGesture} and the SHARED rate
     * limit (which also serialises a Wide-corner double).
     */
    private static void maybeRearmGesture(Minecraft mc, PassageAxis axis, boolean risingEdge, double distToEdge,
                                          double promptDist, boolean canOpenPrompt, long now) {
        if (!LatitudeConfig.borderRepromptGesture) {
            return; // gesture disabled by the General-tab toggle (both buttons, both axes)
        }
        HemispherePassage.Phase phase = phaseFor(axis);
        boolean armed = phase == HemispherePassage.Phase.ARMED;
        if (!risingEdge || armed || !canOpenPrompt || mc.player == null || mc.level == null) {
            return;
        }
        if (now - lastRearmGestureMs < REARM_GESTURE_MIN_INTERVAL_MS) {
            return;
        }
        var border = mc.level.getWorldBorder();
        boolean facingWall;
        if (axis == PassageAxis.POLE) {
            facingWall = HemispherePassage.facingPolewardZ(mc.player.getZ(), border.getCenterZ(),
                    mc.player.getYRot(), HemispherePassage.REARM_GESTURE_FACING_MIN_COS);
        } else {
            facingWall = HemispherePassage.facingOutwardX(mc.player.getX(), border.getCenterX(),
                    mc.player.getYRot(), HemispherePassage.REARM_GESTURE_FACING_MIN_COS);
        }
        // B-7 P3 AIM GATE (owner, TEST 97): a click whose crosshair rests on a block (mining ice at the wall)
        // or an entity is an ordinary interaction, never a border ask -- only a MISS (air) click may re-arm.
        if (HemispherePassage.rearmGestureArms(armed, distToEdge, facingWall, promptDist, crosshairHitKind(mc))) {
            setPhase(axis, HemispherePassage.Phase.ARMED);
            lastRearmGestureMs = now;
            PassageDebug.armTransition(axis, phase, HemispherePassage.Phase.ARMED, distToEdge, "rearm-gesture");
        }
    }

    /** Map the client's current crosshair target ({@code Minecraft.hitResult}) onto the pure aim-gate kinds.
     *  {@code null} (no pick computed this frame) counts as MISS -- there is nothing targeted to interact with,
     *  so the click still reads as "asking the border". */
    private static int crosshairHitKind(Minecraft mc) {
        net.minecraft.world.phys.HitResult hr = mc.hitResult;
        if (hr == null || hr.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return HemispherePassage.GESTURE_HIT_MISS;
        }
        return hr.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? HemispherePassage.GESTURE_HIT_BLOCK : HemispherePassage.GESTURE_HIT_ENTITY;
    }

    private static void driveCurtain(Minecraft mc, long now) {
        if (curtain == Curtain.NONE) {
            return;
        }
        if (curtain == Curtain.RAISING_HOLD) {
            if (HemispherePassageClientState.consumePendingArrival()) {
                onArrival(mc, now);
            } else if (now - curtainRaisedMs >= CURTAIN_TIMEOUT_MS) {
                // No arrival: the server rejected the cross. Recover gracefully -- disarm the crossing arm
                // in-band (ordinary DISARMED, not SEEDED: no cross happened, no post-arrival EDGE one-shot) and
                // melt the curtain so the player is never stuck behind it.
                GlobeMod.LOGGER.warn("[Latitude][Passage] Crossing curtain timed out ({}ms) with no arrival; recovering",
                        CURTAIN_TIMEOUT_MS);
                HemispherePassage.Phase prevPhase = phaseFor(curtainAxis);
                setPhase(curtainAxis, HemispherePassage.Phase.DISARMED);
                double dist = curtainDist(mc, curtainAxis);
                PassageDebug.armTransition(curtainAxis, prevPhase, HemispherePassage.Phase.DISARMED, dist,
                        "curtain-timeout");
                PassageDebug.onCurtainFadeOut(curtainAxis, "timeout");
                // Clear any arrival that lands between this decision and the next crossing (late S2C can't linger).
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
        // Route by the ARRIVAL payload's axis (authoritative -- the server tells us which arm crossed). Seeding
        // SEEDED_DISARMED is LOAD-BEARING: the ordinary prompt requires ARMED, so a disarmed player on the
        // arrival line never self-prompts, and re-arm needs a strict walk-out. For the EW axis SEEDED_DISARMED
        // also carries the one-shot walk-to-the-wall EDGE re-prompt; for the POLE axis (S16(c)) that edge
        // re-prompt COLLAPSED into the wall prompt -- its distance is the disabled sentinel, so the seeded arm
        // simply HOLDS. (Pole arrival is AT the wall, distance 0 -- INSIDE the wall prompt and re-arm -- so the
        // seed holds on every world size; the "one wall prompt" regime.)
        PassageAxis axis = HemispherePassageClientState.lastArrivalAxis();
        HemispherePassage.Phase prevPhase = phaseFor(axis);
        setPhase(axis, HemispherePassage.Phase.SEEDED_DISARMED);
        double dist = curtainDist(mc, axis);
        if (axis == PassageAxis.POLE) {
            boolean north = HemispherePassageClientState.arrivedNorth();
            PassageDebug.onArrivalConsumed(axis, HemispherePassageClientState.lastArrivalX(),
                    HemispherePassageClientState.lastArrivalZ(), north ? "North" : "South");
            PassageDebug.armTransition(axis, prevPhase, HemispherePassage.Phase.SEEDED_DISARMED, dist, "arrival");
            firePoleArrivalTitle(mc, north);
        } else {
            boolean east = HemispherePassageClientState.arrivedEast();
            PassageDebug.onArrivalConsumed(axis, HemispherePassageClientState.lastArrivalX(),
                    HemispherePassageClientState.lastArrivalZ(), east ? "East" : "West");
            PassageDebug.armTransition(axis, prevPhase, HemispherePassage.Phase.SEEDED_DISARMED, dist, "arrival");
            fireArrivalTitle(east);
        }
        PassageDebug.onCurtainFadeOut(axis, "arrival");
        playCrossingWhoosh(mc);
        beginFadeOut(now);
    }

    /** Current distance-to-edge for {@code axis} (debug/seed only; NaN if the player is gone). */
    private static double curtainDist(Minecraft mc, PassageAxis axis) {
        if (mc.player == null) {
            return Double.NaN;
        }
        return axis == PassageAxis.POLE
                ? GlobeClientState.distanceToPoleBlocks(mc.player.getZ())
                : GlobeClientState.distanceToEwBorderBlocks(mc.player.getX());
    }

    private static void beginFadeOut(long now) {
        curtain = Curtain.FADING;
        curtainFadeOutMs = now;
    }

    /** Pole surface gate (S2, client twin of the server {@code isInWaterOrNoSky}): in water OR no sky overhead
     *  suppresses the pole prompt -- an under-ice swimmer / cave explorer must not be offered the crossing. */
    private static boolean poleSurfaceBlocked(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return true;
        }
        if (mc.player.isInWater()) {
            return true;
        }
        return !mc.level.canSeeSky(mc.player.blockPosition().above());
    }

    /** Fire the "EASTERN/WESTERN HEMISPHERE" arrival title on the shared E/W hemisphere channel (B-5). */
    private static void fireArrivalTitle(boolean east) {
        String line = east ? "Eastern Hemisphere" : "Western Hemisphere";
        int durationTicks = (int) Math.round(clamp(LatitudeConfig.zoneEnterTitleSeconds, 2.0, 10.0) * 20.0);
        double scale = clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);
        HemisphereTitleOverlay.trigger(true, line, durationTicks, scale);
    }

    /** Fire the "Beyond the North/South Pole" arrival title (design §7 copy T1) WITH the P3 arrival-legibility
     *  SUBTITLE naming the far meridian (owner, TEST 97: he couldn't tell the crossing took him to the far side
     *  -- the server crew is fixing the transform to the true antipodal meridian; this makes the result legible).
     *  Both lines share the ONE hemisphere-title channel and window: the title rides the N/S slot and the
     *  meridian line the E/W slot, so {@code HemisphereCrossing.composeLines} stacks them title-over-subtitle in
     *  the same styling family as B-5 arrivals (the 0-0 stacked-title idiom, no new render wiring). The meridian
     *  is computed from the ARRIVAL X against the intended X radius ({@code HemispherePassage.farMeridianLabel},
     *  the pure twin of the HUD's {@code formatLongitudeDeg}). */
    private static void firePoleArrivalTitle(Minecraft mc, boolean north) {
        String line = "Beyond the " + (north ? "North" : "South") + " Pole";
        int durationTicks = (int) Math.round(clamp(LatitudeConfig.zoneEnterTitleSeconds, 2.0, 10.0) * 20.0);
        double scale = clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);
        HemisphereTitleOverlay.trigger(false, line, durationTicks, scale); // N/S slot: the title (top line)
        if (mc.level != null) {
            var border = mc.level.getWorldBorder();
            String meridian = HemispherePassage.farMeridianLabel(
                    HemispherePassageClientState.lastArrivalX(), border.getCenterX(),
                    com.example.globe.util.LatitudeMath.intendedXRadius(border));
            HemisphereTitleOverlay.trigger(true, "The far meridian — " + meridian, durationTicks, scale);
        }
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
            // Quick opaque wipe (per-crossing fade-in duration -- EW 300 ms, POLE 150 ms), then HOLD at 1.
            float t = clamp01((float) (now - curtainRaisedMs) / (float) curtainFadeInMs);
            return (float) Math.pow(t, 0.65);
        }
        // FADING: cubic smoothstep dissolve from 1 back to 0 (the title-melt feel).
        float t = clamp01((float) (now - curtainFadeOutMs) / (float) CURTAIN_FADE_OUT_MS);
        float s = t * t * (3.0f - 2.0f * t); // smoothstep (a cubic)
        return 1.0f - s;
    }

    /** Draw the opaque crossing curtain (full-screen fill). No-op unless a crossing is in flight. */
    public static void renderCurtain(GuiGraphicsExtractor ctx) {
        if ((!LatitudeV2Flags.PASSAGE_V2_ENABLED && !LatitudeV2Flags.POLE_PASSAGE_V2_ENABLED)
                || curtain == Curtain.NONE) {
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
