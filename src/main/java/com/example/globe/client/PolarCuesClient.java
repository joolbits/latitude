package com.example.globe.client;

import com.example.globe.core.HemispherePassage;
import com.example.globe.core.PolarColdCues;
import com.example.globe.util.LatitudeMath;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- P2 polar-cold client cues:
 * <ul>
 *   <li><b>S6 frozen-wounds whisper (GLOBAL</b>, design item 8 -- it presents the un-gated S6 heal lock) -- a
 *       one-shot "Your wounds are frozen. Only warmth can mend them." when the client-derived heal lock first
 *       bites a wounded player (the tint itself is server-driven via {@code ticksFrozen}, so it needs no
 *       client work).</li>
 *   <li><b>S2 pole-clamp frost (FLAG-GATED</b> on {@code POLE_PASSAGE_V2_ENABLED}; sweep INFO 2026-07-14,
 *       honesty law) -- sparse snowflake particles along the pole wall plane when the player is pressed
 *       against the pole line (client knows {@code |z|} vs the synced Z radius), matching the ambient polar
 *       snow idiom (cheap, exposure-scaled, rate-limited). Gated WITH the wall it presents: the S2 hard-stop
 *       clamp only exists flag-on, so flag-off frost would announce a barrier that isn't there (the server
 *       actionbar/chime already sit behind the same flag).</li>
 * </ul>
 * Driven from {@code GlobeModClient.polarCapClientTick} (after its {@code isPaused} guard, so the frost inherits
 * the B-3b anti-backlog discipline). Lives in the client package so it can tag the clamp-contact
 * {@link PassageDebug} event.
 */
public final class PolarCuesClient {

    private PolarCuesClient() {
    }

    // --- S6 frozen-wounds whisper one-shot state ---
    private static boolean healLockWhisperPrevActive = false;

    // --- S2 pole-clamp frost ---
    /** Within this many blocks of the pole line the player counts as pressed against the wall. */
    private static final double POLE_CLAMP_FROST_DIST = 1.5;
    /** Rate limit (ticks) between frost bursts, so holding into the wall does not spew particles. */
    private static final long POLE_CLAMP_FROST_INTERVAL = 4L;
    /** Sparse per-burst budget (before exposure scaling). */
    private static final int POLE_CLAMP_FROST_COUNT = 6;
    private static long lastPoleClampFrostTick = Long.MIN_VALUE;

    /** World-switch hygiene. */
    public static void reset() {
        healLockWhisperPrevActive = false;
        lastPoleClampFrostTick = Long.MIN_VALUE;
    }

    /**
     * S6: fire the frozen-wounds whisper ONCE when the heal lock first bites a wounded player. Re-arms when the
     * lock releases (warmth / exposure / zone-exit / heal-to-full) -- the falling edge of {@code active}.
     */
    public static void frozenWoundsWhisperTick(Minecraft client) {
        if (client.player == null) {
            healLockWhisperPrevActive = false;
            return;
        }
        boolean wounded = client.player.getHealth() < client.player.getMaxHealth();
        boolean active = wounded && PolarColdClient.isHealLocked(client);
        if (PolarColdCues.frozenWoundsWhisperFires(active, healLockWhisperPrevActive)) {
            LatitudeWhisperOverlay.trigger(PolarColdCues.FROZEN_WOUNDS_WHISPER_TEXT);
        }
        healLockWhisperPrevActive = active;
    }

    /**
     * S2: spawn a sparse frost curtain along the pole wall plane when the player is pressed against the pole
     * line and holding poleward (facing away -- back toward the interior -- stops it). Exposure-scaled and
     * rate-limited; a no-op when not at the wall. FLAG-GATED on {@code POLE_PASSAGE_V2_ENABLED} (sweep INFO,
     * honesty law): the hard-stop clamp this frost presents only exists flag-on -- with the flag off there is
     * no wall, and frost must not suggest one.
     */
    public static void poleClampFrostTick(Minecraft client, float exposure) {
        if (!com.example.globe.core.LatitudeV2Flags.POLE_PASSAGE_V2_ENABLED) {
            return; // no wall flag-off -> no frost may present one (the chime/actionbar share this gate server-side).
        }
        if (client.player == null || client.level == null) {
            return;
        }
        double z = client.player.getZ();
        double distToPole = GlobeClientState.distanceToPoleBlocks(z);
        if (Double.isNaN(distToPole) || distToPole > POLE_CLAMP_FROST_DIST) {
            return; // not at the wall.
        }
        double centerZ = client.level.getWorldBorder().getCenterZ();
        // Intent: facing has a poleward component (turning back to face the interior quiets the frost). minCos 0
        // = anywhere within 90 deg of straight-poleward.
        if (!HemispherePassage.facingPolewardZ(z, centerZ, client.player.getYRot(), 0.0)) {
            return;
        }
        long tick = client.level.getGameTime();
        if (lastPoleClampFrostTick != Long.MIN_VALUE && tick >= lastPoleClampFrostTick
                && (tick - lastPoleClampFrostTick) < POLE_CLAMP_FROST_INTERVAL) {
            return;
        }
        int count = Math.round(POLE_CLAMP_FROST_COUNT * exposure);
        if (count <= 0) {
            return; // sealed in -> no frost (matches the ambient snow exposure idiom).
        }
        lastPoleClampFrostTick = tick;
        spawnPoleClampFrost(client, centerZ, count);
        PassageDebug.onPoleClampContact(distToPole);
    }

    private static void spawnPoleClampFrost(Minecraft client, double centerZ, int count) {
        double zRadius = LatitudeMath.latitudeRadius(client.level.getWorldBorder());
        double polewardSign = client.player.getZ() >= centerZ ? 1.0 : -1.0;
        double wallZ = centerZ + polewardSign * zRadius; // the |z| = zRadius pole wall plane
        RandomSource random = client.player.getRandom();
        double px = client.player.getX();
        double py = client.player.getEyeY();

        for (int i = 0; i < count; i++) {
            // Spread along the wall plane (X + Y) right at the wall Z, a thin sparkling frost sheet.
            double ox = (random.nextDouble() - 0.5) * 5.0;
            double oy = -2.0 + random.nextDouble() * 4.0;
            double oz = (random.nextDouble() - 0.5) * 0.6; // hug the wall plane
            // Drift: settle downward, faint inward/sideways jitter.
            double vx = (random.nextDouble() - 0.5) * 0.04;
            double vy = -0.02 - random.nextDouble() * 0.03;
            double vz = -polewardSign * (random.nextDouble() * 0.02); // drift slightly back off the wall
            client.particleEngine.createParticle(ParticleTypes.SNOWFLAKE,
                    px + ox, py + oy, wallZ + oz, vx, vy, vz);
        }
    }
}
