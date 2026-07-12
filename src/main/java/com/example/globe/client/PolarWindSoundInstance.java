package com.example.globe.client;

import com.example.globe.core.PolarWindSound;
import com.example.globe.util.LatitudeMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Phase 5 (polar-experience v2, CD finding F2 / R2) -- the looping WIND SOUND BED for the polar approach.
 * The pole is the mod's emotional peak and was completely silent; this is the single highest-impact addition
 * the Creative Director review called for. It loops the vanilla {@code ELYTRA_FLYING} wind-rush sound (no new
 * asset) on the {@link SoundSource#WEATHER} category -- so it respects the player's Weather volume slider like
 * any other storm -- and ramps its volume from a breath at 85 deg to a howl near the pole, tracking the SAME
 * 85->90 latitude ramp the ambient snow + whiteout fog use ({@link PolarWindSound}). Pitch is lowered slightly
 * for a lower, more menacing wind than the elytra rush.
 *
 * <p><b>Lifecycle.</b> A single instance at a time. {@link #clientTick(Minecraft)} (called from the client
 * end-tick) starts one when the player crosses 85 deg on a globe world -- whether sky-exposed OR sheltered --
 * and the instance's own {@link #tick()} updates its volume every tick from the live latitude and STOPS
 * cleanly on retreat below 84.5 deg (a 0.5 deg hysteresis dead band so the loop never stutters at the
 * boundary), on leaving the globe world / dimension, or on the player being removed. Stepping under a roof
 * does NOT stop it -- shelter only MUFFLES the volume (real wind carries through walls), snapping back to full
 * on re-exposure. {@link #reset()} force-stops it on disconnect / world change. It is relative +
 * non-attenuated (a listener-locked ambient bed), so its loudness is governed purely by the latitude envelope
 * (and the shelter muffle), not by 3D distance.
 *
 * <p>No Reduce Motion interaction (it is audio, not motion); the vanilla per-category volume sliders are the
 * only mute, applied automatically by the {@link SoundSource#WEATHER} category.
 */
public final class PolarWindSoundInstance extends AbstractTickableSoundInstance {

    private static PolarWindSoundInstance current;

    // Re-arm cooldown (sweeper LOW note): if the sound engine culls a just-played instance immediately
    // (e.g. Weather volume muted, or a reclaimed channel right at the 85.0deg onset), isActive() stays
    // false and this manager would otherwise allocate a fresh instance EVERY tick — pure GC churn. A
    // short cooldown between creation attempts bounds that to ~once per second with zero effect on the
    // normal path (a live instance short-circuits on isActive before this is consulted).
    private static final int REARM_COOLDOWN_TICKS = 20;
    private static int rearmCooldown;

    private PolarWindSoundInstance() {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        // Listener-locked ambient bed: play relative to the listener with no distance attenuation, so only
        // the latitude envelope controls loudness (not the player's position within the pole region).
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.pitch = PolarWindSound.PITCH;
        this.volume = PolarWindSound.MIN_ALIVE_VOLUME; // starts inaudible; the first tick sets the real level
    }

    /**
     * Client end-tick hook: (re)start the wind loop when the player is poleward of 85 deg on a surface-exposed
     * globe world and no loop is currently active. The instance self-stops on retreat / world change, so this
     * only ever needs to (re)arm it; the 0.5 deg START/STOP hysteresis band prevents thrash at the boundary.
     */
    public static void clientTick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }
        var eval = GlobeClientState.evaluate(mc);
        if (!eval.active()) {
            return;
        }
        // NOT gated on eval.surfaceOk(): the wind bed arms whether the player is sky-exposed or sheltered --
        // real wind is audible from inside a shelter. The instance muffles itself while sheltered in its own
        // tick (see below) and stops only on true deactivation, so a player who logs in indoors at the pole
        // still hears the (muffled) gale.
        double absLatDeg = LatitudeMath.absLatDegExact(mc.level.getWorldBorder(), mc.player.getZ());
        if (!PolarWindSound.shouldStart(absLatDeg)) {
            return;
        }
        if (current != null && mc.getSoundManager().isActive(current)) {
            return; // already howling; the instance updates its own volume each tick.
        }
        if (rearmCooldown > 0) {
            rearmCooldown--;
            return;
        }
        rearmCooldown = REARM_COOLDOWN_TICKS;
        current = new PolarWindSoundInstance();
        mc.getSoundManager().play(current);
    }

    /** Force-stop and clear the loop (disconnect / world change). */
    public static void reset() {
        if (current != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.getSoundManager().stop(current);
            }
            current = null;
        }
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.player.isRemoved()) {
            stop();
            return;
        }
        if (!GlobeClientState.isGlobeWorld()) {
            stop();
            return;
        }
        // Only TRUE deactivation stops the loop: leaving the globe world / another dimension (active) or
        // retreating below the latitude floor (shouldStop). Being SHELTERED does NOT stop it -- unlike the
        // ambient snow / whiteout, the wind is audible through walls, so shelter only MUFFLES the volume.
        var eval = GlobeClientState.evaluate(mc);
        if (!eval.active()) {
            stop();
            return;
        }
        double absLatDeg = LatitudeMath.absLatDegExact(mc.level.getWorldBorder(), mc.player.getZ());
        if (PolarWindSound.shouldStop(absLatDeg)) {
            stop();
            return;
        }
        // Full volume when sky-exposed, muffled to SHELTERED_VOLUME_SCALE when under a roof / indoors, tracked
        // live each tick so it fades between the two as the player moves through a doorway.
        this.volume = PolarWindSound.liveVolume(absLatDeg, eval.surfaceOk());
    }
}
