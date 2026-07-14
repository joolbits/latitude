package com.example.globe.core;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- the PURE decisions behind the Wide-world pole hard-stop's "presentable
 * contact" event (owner amendment, 2026-07-13: an invisible wall confuses an underwater swimmer or cave
 * explorer). Zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM). The MC-coupled part
 * (playing the ice-chime sound, sending the action-bar text, reading in-water/no-sky) lives in
 * {@code GlobeMod}; this class owns only what must be provable without a server:
 * <ul>
 *   <li>{@link #shouldEmit} -- the TICK-COUNTER rate limit (no wall-clock), so contact against the wall does
 *       not spam the chime/message every tick a player holds forward into it.</li>
 *   <li>{@link #message} -- which line the player sees, chosen by whether they are in water.</li>
 * </ul>
 * Richer presentation (particles, a keyline plane) is P2's; this is the minimal server-side "the wall is real,
 * here is why" feedback.
 */
public final class PoleClampContact {

    private PoleClampContact() {
    }

    /** Minimum server ticks between contact events for one player (~2 s at 20 tps). Rate-limited by comparing
     *  game-time ticks, never wall-clock (the B-3b anti-backlog discipline). */
    public static final long CONTACT_COOLDOWN_TICKS = 40L;

    /** Sentinel for "this player has never contacted the wall this session" -- always emits. */
    public static final long NEVER = Long.MIN_VALUE;

    /** The action-bar line for a swimmer pressed against the pole wall under water: the pack ice explains the
     *  invisible barrier. */
    public static final String MESSAGE_IN_WATER = "Pack ice, frozen to the seafloor, bars the way.";
    /** The action-bar line for a walker/spelunker at the wall (surface or cave). */
    public static final String MESSAGE_BLOCKED = "The ice of the world's end bars the way.";

    /**
     * True iff a contact event should fire this tick: the first contact ever ({@code lastContactTick == }
     * {@link #NEVER}) always fires, otherwise only once {@code cooldownTicks} have elapsed since the last one.
     * Defensive against a backwards/reset game-time ({@code now < last}) -- treats it as elapsed so a world/time
     * reset can never mute the wall permanently.
     */
    public static boolean shouldEmit(long lastContactTick, long now, long cooldownTicks) {
        if (lastContactTick == NEVER) {
            return true;
        }
        long since = now - lastContactTick;
        return since < 0 || since >= cooldownTicks;
    }

    /** The action-bar line for the contact, chosen by whether the player is in water (S2 groundwork mirrors the
     *  in-water / no-sky suppression sibling). */
    public static String message(boolean inWater) {
        return inWater ? MESSAGE_IN_WATER : MESSAGE_BLOCKED;
    }
}
