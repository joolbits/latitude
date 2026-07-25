package com.example.globe.core;

/**
 * S44 IN-CAVE DROP TRAPS (Peetsa 2026-07-25, B-9 punchlist item 1: "I want to add traps inside caves that
 * drop the player down to a deeper layer of the cave"). Pure decision math -- zero Minecraft imports (Core
 * Logic layer, plain-JVM testable). The world-side wiring is {@code world.CaveDropTrapFeature}.
 *
 * <p><b>The trap.</b> Inside the glacial underground, some cave-gallery FLOORS are only a thin shell over a
 * deeper gallery. Where the shell is thin enough and the space below deep enough, the shell's walking
 * surface becomes {@code powder_snow} (the S35 cover fiction: vanilla sink-through IS the trigger, the
 * powder-vs-solid texture is the learnable tell) and the rest of the shell is punched out, so a walker
 * sinks through the floor and drops to the layer below -- onto a powder cushion (the S35 fall law:
 * the fall itself never kills; the cost is position, cold, and finding the way back up).
 *
 * <p><b>Laws carried over from the surface traps (S35):</b> cushion at EVERY drop cell's landing; a
 * water landing is never trapped (the skinned-pond law -- gen-time "safe water" can grow an ice skin
 * later and break the cushion guarantee, so water-floored cells are excluded outright); the landing must
 * be horizontally traversable (the entombment law -- a sealed pocket with no exit is the one outlawed
 * outcome); deterministic seeded rolls, no new noise (Art VI).
 */
public final class CaveDropTrap {

    private CaveDropTrap() {
    }

    /** Minimum standing room (air blocks) ABOVE the shell for the gallery to be walkable trap ground --
     *  a player is ~1.8 blocks; 2 is vanilla walking clearance. */
    public static final int MIN_GALLERY_AIR = 2;

    /** Maximum shell thickness (solid blocks) between the upper gallery floor and the void below. 1-3:
     *  a THIN false floor. Thicker floors are honest ground -- the trap never mines real mass. */
    public static final int MAX_SHELL_THICKNESS = 3;

    /** Minimum air run BELOW the shell for the drop to be a real between-layers fall ("drop the player
     *  down to a deeper layer" -- not a crawlspace). 8 blocks ~= 3 hearts uncushioned; the cushion
     *  forgives it, the displacement is the price. */
    public static final int MIN_DROP_AIR = 8;

    /** Maximum patch area (cells) a single drop trap may cover -- a floor panel, not a floor deletion.
     *  Smaller than the surface traps' 48: in-cave galleries are tighter spaces. */
    public static final int PATCH_MAX_AREA = 12;

    /** Deterministic fraction of eligible patches that become traps (per-patch roll {@code <} this).
     *  Census-calibrated (rig 2026-07-25): the ice cathedrals are so layered that 0.35 produced 1341
     *  covers/256 chunks (~5 per chunk -- every floor a lie). 0.10 with the one-patch-per-chunk cap lands
     *  the density at a lurking occasional hazard instead of a minefield. */
    public static final float TRAP_FRACTION = 0.10f;

    /** At most this many patches fire per chunk (the census cap): a chunk may HIDE one false floor; a
     *  chunk riddled with them reads as broken terrain, not danger. */
    public static final int MAX_PATCHES_PER_CHUNK = 1;

    /**
     * Does a column's vertical profile qualify for a drop cell? All inputs are counts measured by the
     * feature at one (x,z): standing air ABOVE the shell top, the shell's solid thickness, and the air
     * run immediately BELOW the shell.
     */
    public static boolean cellQualifies(int galleryAirAbove, int shellThickness, int dropAirBelow) {
        return galleryAirAbove >= MIN_GALLERY_AIR
                && shellThickness >= 1 && shellThickness <= MAX_SHELL_THICKNESS
                && dropAirBelow >= MIN_DROP_AIR;
    }

    /** The per-patch fraction gate: {@code 0 <= roll01 < }{@link #TRAP_FRACTION}; out-of-range never traps
     *  (the safe direction is "leave the floor honest"). */
    public static boolean shouldTrapPatch(float roll01) {
        return roll01 >= 0.0f && roll01 < TRAP_FRACTION;
    }

    /** May two vertically-nearby drop cells join one patch? Their shell tops must sit within one block --
     *  a single coherent floor panel, never a stair-stepped diagonal tear across gallery levels. */
    public static boolean cellsJoinPatch(int shellTopYa, int shellTopYb) {
        return Math.abs(shellTopYa - shellTopYb) <= 1;
    }
}
