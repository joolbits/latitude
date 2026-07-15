package com.example.globe.core;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- which world edge a hemisphere-passage event belongs to. Pure Java, no
 * Minecraft imports (Core Logic layer): shared by the netcode records ({@code GlobeNet.PassageAnswerPayload}
 * / {@code PassageArrivalPayload}), the server receiver routing ({@code GlobeMod.handlePassageAnswer}), the
 * turn-back nudge map, and the (P2) client arms.
 *
 * <p><b>Two axes, one machine.</b> B-5 shipped the E/W (antimeridian) crossing; B-7 adds the N/S pole
 * crossing. Both drive the SAME pure {@link HemispherePassage} phase machine and the same
 * {@code HemispherePassageService} probe machinery -- they differ only in the geometry class that feeds them
 * ({@link EdgeGeometry} vs {@link PoleGeometry}) and the teleport transform (EW: {@code mirrorX}; POLE:
 * {@code PoleArrivalSearch.antipodalX}). This enum is the single
 * discriminator so a shared server receiver, a shared cooldown, and a shared netcode pair never fork into two
 * drifting copies (design {@code docs/binder/phase5-b7-pole-passage-design-20260713.md} §5.2).
 *
 * <p><b>Wire id.</b> {@link #id()} is the stable VAR_INT sent on the wire (0 = EW, 1 = POLE). Client and server
 * ship in the same jar (Fabric custom payloads are mod-version-locked), so the encoding is lockstep-safe; a
 * value outside the known set decodes to {@link #EW} defensively (never throws mid-decode).
 */
public enum PassageAxis {
    /** The E/W antimeridian edge (B-5): {@code mirrorX}, kept yaw. */
    EW,
    /** The N/S pole (B-7): the ANTIPODAL meridian (longitude L -> L+180, {@code PoleArrivalSearch.antipodalX}
     *  -- [P3 fix 2026-07-14: antipodal meridian, not mirrorX]), same pole side, yaw + 180. */
    POLE;

    /** The stable wire id: {@code EW=0}, {@code POLE=1} (the enum ordinal, pinned by test). */
    public int id() {
        return ordinal();
    }

    /** Decode a wire id back to an axis; anything outside {@code {0,1}} degrades to {@link #EW} (never throws,
     *  so a malformed/rogue payload can never crash the receiver -- the flag gate + distance re-validation
     *  reject it downstream anyway). */
    public static PassageAxis fromId(int id) {
        return id == POLE.ordinal() ? POLE : EW;
    }
}
