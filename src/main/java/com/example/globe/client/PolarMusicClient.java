package com.example.globe.client;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarMusicLaw;
import com.example.globe.util.LatitudeMath;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Phase 5 Slice B-9/B-7 -- Peetsa stipulation S26 (2026-07-20): MUSIC FADES OUT AT THE DAMAGE LINE. Client
 * state holder for the polar MUSIC volume multiplier. Owns a single live {@code factor01} in {@code [0,1]}
 * that {@link com.example.globe.mixin.client.MusicFadeVolumeMixin} multiplies onto the
 * {@code SoundSource.MUSIC} category volume, and eases it once per client tick from the pure
 * {@link PolarMusicLaw} using the player's latitude + shelter state.
 *
 * <p><b>Own client-tick registration (reported path).</b> The existing polar client ticks (the wind bed, the
 * cold cues) are registered from {@code GlobeModClient}, which is outside this crew's file set (the S26
 * lane must not touch it). So this class is its OWN {@link ClientModInitializer} entrypoint (appended to
 * {@code fabric.mod.json} "client") and registers its tick via the standard
 * {@link ClientTickEvents#END_CLIENT_TICK} idiom the codebase already uses -- no edit to the shared client
 * initializer. END_CLIENT_TICK fires at the fixed 20 TPS game tick (decoupled from render frames), so the
 * exponential ease is frame-rate independent with a constant {@code deltaTicks = 1.0} per tick.
 *
 * <p><b>Shelter predicate reuse.</b> The shelter boolean is read from {@link PolarColdClient#isSheltered}
 * (raw sky light at the eye {@code <= 3}, the B-7 trap-proof rule) -- the SAME predicate the cold-damage
 * path uses, not a reimplementation. So music resumes underground / in a sealed shelter exactly where cold
 * damage pauses.
 *
 * <p><b>Recovery / flag-off.</b> Off a globe world (main menu, other dimension) or with the damage-line flag
 * {@link LatitudeV2Flags#POLAR_BARRENS_ENABLED} off, the target is {@link PolarMusicLaw#FULL} -- so a factor
 * that was fading toward silence eases back UP to full music (never a hard cut), and menu music is never
 * left stuck quiet. Flag-off additionally pins the mixin to full (byte-identical audio).
 *
 * <p><b>V1 accepted limitation (documented):</b> with the factor at 0, vanilla's {@code MusicManager} may
 * still SCHEDULE and start tracks that then play silently (their volume multiplied to ~0). V1 does not touch
 * MusicManager scheduling; the audible result is correct (silence), only a hidden track counter advances.
 */
public final class PolarMusicClient implements ClientModInitializer {

    /** Live music-volume factor in {@code [0,1]}, read by the volume mixin. Volatile: written on the client
     *  thread each tick, read on the sound thread when the seam resolves a MUSIC instance's volume. */
    private static volatile float factor01 = PolarMusicLaw.FULL;

    /** END_CLIENT_TICK fires once per fixed 20 TPS game tick, so a full ease step is exactly one tick. */
    private static final double DELTA_TICKS_PER_CLIENT_TICK = 1.0;

    /** The live music-volume factor the {@code SoundSource.MUSIC} seam multiplies by (1.0 = full music). */
    public static float factor01() {
        return factor01;
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(PolarMusicClient::clientTick);
    }

    private static void clientTick(Minecraft mc) {
        // Flag off: pin to full and skip (byte-identical audio; the mixin also short-circuits on the flag).
        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED) {
            factor01 = PolarMusicLaw.FULL;
            return;
        }

        // Off-globe / no player (main menu, other dimension): ease back toward full so music never stays
        // stuck silent after leaving the pole -- a smooth fade-in, never a snap.
        if (mc == null || mc.player == null || mc.level == null || !GlobeClientState.isGlobeWorld()) {
            factor01 = PolarMusicLaw.ease(factor01, PolarMusicLaw.FULL, DELTA_TICKS_PER_CLIENT_TICK);
            return;
        }

        double absLatDeg = LatitudeMath.absLatDegExact(mc.level.getWorldBorder(), mc.player.getZ());
        boolean sheltered = PolarColdClient.isSheltered(mc);
        factor01 = PolarMusicLaw.musicFactor01(
                true, absLatDeg, sheltered, factor01, DELTA_TICKS_PER_CLIENT_TICK);
    }
}
