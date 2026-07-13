package com.example.globe;

import com.example.globe.core.EdgeGeometry;
import com.example.globe.core.HemispherePassage;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.border.WorldBorder;

import java.util.EnumSet;

/**
 * Phase 5 Slice B-5 (Hemisphere Passage) -- the mirror-teleport core.
 *
 * <p>A self-contained mechanical service: given a server-side {@link ServerPlayer}, put them safely on the
 * mirrored-X column of the far hemisphere with momentum and fall damage zeroed and no ghost mount left
 * behind. That move is ALL it knows; callers may wrap it in whatever surrounding experience they choose
 * (B-5 and the planned B-6 both drive this same core), and nothing here depends on any of that.
 *
 * <p><b>What it does (in order), per the design:</b>
 * <ol>
 *   <li>Mirror to the far hemisphere ({@link HemispherePassage#mirrorX}) to pick the SIDE, then pull the
 *       arrival INLAND to {@code EdgeGeometry.arrivalDist} -- 178 deg, half a degree POLEWARD of the fog onset,
 *       so the player emerges in the THINNING FOG EDGE (Peetsa: "right at the edge of the fog, even slightly
 *       inside"). Edge-flow rework: 178 deg now coincides EXACTLY with the crossing PROMPT line -- arrival lands
 *       on the prompt line, disarmed (the seeded state carries the post-arrival EDGE auto-re-prompt). Z is kept.
 *       This is the X border-half geometry (border centered at 0,0), NEVER the Z latitude radius, and is
 *       IDENTICAL in Classic and Mercator -- only the intended X radius (hence {@code |arrivalX|}) differs.</li>
 *   <li>3x3 FULL chunk ring + target safety: {@link GlobeMod#placeSafeY} force-loads the 3x3 ring around the
 *       target ({@code ChunkStatus.FULL}) and returns null on fluid/non-air, so we never arrive in water or
 *       void. On null we search outward along +/-Z at the mirrored X for the nearest safe column (latitude as
 *       close as possible), then, only if that whole search fails, nudge inland along X. The entire search is
 *       hard-capped at {@link #PROBE_BUDGET} probes so it can never stall the server thread.</li>
 *   <li>Dismount before teleport ({@code stopRiding}) -- no mount-carry, no ghost vehicle left at the origin.</li>
 *   <li>Teleport, then zero momentum and fall distance (the {@code applySpawnChoice} idiom).</li>
 * </ol>
 *
 * <p>Returns the arrival {@link BlockPos} on success, or {@code null} if no safe column was found within the
 * probe budget -- in which case the caller MUST leave the player where they are (never teleport into
 * fluid/void).
 */
public final class HemispherePassageService {

    /**
     * P1 sweep HIGH-1: hard cap on TOTAL {@link GlobeMod#placeSafeY} probes per crossing attempt. Every probe
     * synchronously force-loads a 3x3 FULL chunk ring on the server thread, so the uncapped worst case
     * (wholly-unsafe mirror strip: 1 mirror + 256 Z-search + 8 X-nudge = 265 probes) would be ~2,385
     * ring-cell loads -- a multi-second whole-server freeze. At 40 the hard ceiling is 40 * 9 = 360
     * ring-cell loads; and because consecutive Z probes step exactly one chunk (16 blocks), adjacent rings
     * overlap 6 of their 9 cells, so the worst-case DISTINCT chunks generated is ~130-150 (re-touched
     * already-FULL cells are chunk-source cache hits, not generations). When the budget exhausts without a
     * safe column, {@link #resolveArrival} returns null, the crossing no-ops (player stays put), and the
     * caller logs it server-side.
     */
    static final int PROBE_BUDGET = 40;

    /** Outward +/-Z search step (blocks). One chunk: matches the chunk-ring granularity of {@code placeSafeY}. */
    static final int Z_SEARCH_STEP = 16;
    /** Max +/-Z search offset (blocks) from the mirrored latitude before giving up on the Z search. With
     *  {@link #PROBE_BUDGET} = 40 the budget binds first (~19 offsets x 2 directions), so this is a
     *  belt-and-suspenders geometric bound, not the effective one. */
    static final int Z_SEARCH_MAX_OFFSET = 2048;
    /** Inland X-nudge step (blocks), toward the border center. */
    static final int X_NUDGE_STEP = 128;
    /** Max inland X-nudge (blocks): last-resort fallback if the whole Z strip is unsafe. */
    static final int X_NUDGE_MAX = 1024;

    private HemispherePassageService() {
    }

    /**
     * Resolve the safe arrival column for a player currently at ({@code playerX},{@code playerZ}), or
     * {@code null} if none was found within {@link #PROBE_BUDGET} probes. Safe to call from the server
     * thread; total synchronous chunk work is hard-capped by the budget.
     */
    static BlockPos resolveArrival(ServerLevel world, double playerX, double playerZ) {
        WorldBorder border = world.getWorldBorder();
        double centerX = border.getCenterX();
        // Mirror to the far hemisphere, then PULL INLAND to the arrival column (Peetsa's teleport ask). The SIDE
        // is taken from the mirror; the inland depth comes from the resolved geometry (EdgeGeometry.arrivalDist =
        // ARRIVAL_DEG 178 deg, ~2 deg from the wall, just INSIDE the fog onset). |arrivalX| is the same in both
        // hemispheres, so the far-side border distance is deterministic. Edge-flow rework: the arrival lands in
        // the thinning fog edge and EXACTLY on the crossing prompt line (ARRIVAL_DEG == PROMPT_DEG == 178) --
        // harmless because the S2C arrival seeds the arm DISARMED (SEEDED_DISARMED) and the prompt requires ARMED,
        // so there is no self-reprompt (see HemispherePassage).
        double mirroredX = HemispherePassage.mirrorX(playerX, centerX);
        double sign = mirroredX >= centerX ? 1.0 : -1.0;
        double xRadiusIntended = LatitudeMath.intendedXRadius(border);
        double arrivalAbsX = EdgeGeometry.resolve(xRadiusIntended).arrivalAbsX();
        int targetX = (int) Math.round(centerX + sign * arrivalAbsX);

        int maxAbsZ = latitudeSafeMaxAbsZ(world);
        int baseZ = Mth.clamp((int) Math.round(playerZ), -maxAbsZ, maxAbsZ);

        int budget = PROBE_BUDGET;

        // 1) The exact mirror column (3x3 FULL ring + fluid/air safety inside placeSafeY).
        budget--;
        BlockPos safe = GlobeMod.placeSafeY(world, targetX, baseZ);
        if (safe != null) {
            return safe;
        }

        // 2) Outward +/-Z at the mirrored X -- keep the same longitude, move latitude as little as possible.
        //    RESERVE the X-nudge probes out of the budget so stage 3 always gets its turn (a fully-unsafe Z
        //    strip must not starve the inland fallback).
        int xNudgeProbes = X_NUDGE_MAX / X_NUDGE_STEP; // 8
        for (int off = Z_SEARCH_STEP; off <= Z_SEARCH_MAX_OFFSET && budget > xNudgeProbes; off += Z_SEARCH_STEP) {
            int zUp = baseZ + off;
            if (zUp <= maxAbsZ) {
                budget--;
                safe = GlobeMod.placeSafeY(world, targetX, zUp);
                if (safe != null) {
                    return safe;
                }
            }
            int zDown = baseZ - off;
            if (zDown >= -maxAbsZ && budget > xNudgeProbes) {
                budget--;
                safe = GlobeMod.placeSafeY(world, targetX, zDown);
                if (safe != null) {
                    return safe;
                }
            }
        }

        // 3) Last resort: nudge inland along X (toward center) at the base latitude. Keeps us out of a fully
        //    ocean edge strip when boundaryV2 paints the whole mirror band as sea.
        int dir = targetX >= centerX ? -1 : 1;
        for (int nudge = X_NUDGE_STEP; nudge <= X_NUDGE_MAX && budget > 0; nudge += X_NUDGE_STEP) {
            budget--;
            safe = GlobeMod.placeSafeY(world, targetX + dir * nudge, baseZ);
            if (safe != null) {
                return safe;
            }
        }

        // Budget exhausted (or bounds ran out) with no safe column: no-op the crossing, player stays put.
        return null; // never teleport into fluid/void
    }

    /**
     * Perform the crossing for an ALREADY-VALIDATED, in-band, alive, online {@link ServerPlayer}. Callers own
     * the guards (flag on, globe overworld, edge-distance re-validation, alive/online) -- this trusts them and
     * only does the safe mechanical move. Returns the arrival position, or {@code null} if no safe column was
     * found (in which case the player was NOT moved).
     */
    static BlockPos crossHemisphere(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        BlockPos arrival = resolveArrival(world, player.getX(), player.getZ());
        if (arrival == null) {
            GlobeMod.LOGGER.warn("[Latitude][Passage] No safe mirror column for {} at x={} z={} within {} probes; crossing aborted (staying put)",
                    player.getName().getString(), player.getX(), player.getZ(), PROBE_BUDGET);
            return null;
        }
        // Dismount first: no mount-carry across the seam, no ghost vehicle at the origin.
        player.stopRiding();
        player.teleportTo(world, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5,
                EnumSet.noneOf(Relative.class), player.getYRot(), player.getXRot(), true);
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.fallDistance = 0.0F;
        GlobeMod.LOGGER.info("[Latitude][Passage] Crossed {} to mirror x={} y={} z={}",
                player.getName().getString(), arrival.getX(), arrival.getY(), arrival.getZ());
        return arrival;
    }

    /**
     * B-6 P2 -- the SILENT, momentum-preserving crossing for an ALREADY-VALIDATED evator trigger. Callers own
     * the guards ({@code EvatorMirror.active()}, alive/online, surface, cooldown, the {@link
     * com.example.globe.core.EvatorCrossing} one-shot); this trusts them and does only the mechanical move.
     * Returns the arrival position, or {@code null} if the mirror column is unsafe (player NOT moved).
     *
     * <p><b>How it differs from {@link #crossHemisphere} (B-5's ceremony move), per the design amendments:</b>
     * <ol>
     *   <li><b>Arrival AT the mirrored position</b> -- {@code targetX = mirrorX(x)}, {@code z} unchanged --
     *       NOT pulled inland to {@code EdgeGeometry.arrivalDist}. The mirror band (B-6 P1) makes the terrain
     *       there the proven near-mirror of where the player stands, so landing anywhere else would break the
     *       invisible seam. Exact doubles are preserved (no snap to block center): sub-block position carries
     *       through the seam.</li>
     *   <li><b>NO search, NO nudge (amendment 6).</b> If {@link GlobeMod#placeSafeY} refuses the mirror column
     *       (e.g. the player is swimming, so the mirror is water too), the crossing NO-OPS and the caller logs
     *       it -- the player stays at the wall (the border there is a damage boundary that halts progress, not
     *       solid collision; the machine's HELD one-shot, not the wall, is what guarantees the trigger). An
     *       inland fallback would silently violate "land in identical terrain"; correctness over availability.</li>
     *   <li><b>Momentum PRESERVED via the packet, not post-hoc (amendment 5).</b> {@code Relative.DELTA_X/Y/Z}
     *       ride inside the {@code teleportTo} relative-set, so the position packet itself carries
     *       "keep your velocity" ({@code PositionMoveRotation.apply}: with a DELTA flag the final velocity is
     *       {@code current + target(=0) = current}). A post-hoc {@code setDeltaMovement} LOSES to the position
     *       packet. Rotation is likewise preserved exactly by passing {@code X_ROT/Y_ROT} as relative with 0/0
     *       -- the player keeps facing precisely as they were, mid-stride.</li>
     *   <li><b>{@code fallDistance} NOT reset</b> -- they are mid-stride, not spawning; an elytra dive carries
     *       its physics through the seam.</li>
     *   <li><b>Dismount stays</b> ({@code stopRiding}) -- mount-carry remains out of scope, same as B-5; a
     *       mounted crossing is logged so the dismount is never a silent surprise.</li>
     * </ol>
     *
     * <p>The mirrored Y should be near-identical by the mirror (P1's proven twin wobble: typically +-2, max 9
     * blocks on steep gradients). The arrival Y is the player's own Y when the mirror column is probed FREE
     * there (the seamless norm), else {@code placeSafeY}'s validated surface -- see the probe in the body and
     * {@code EvatorCrossing.chooseArrivalY}.
     */
    static BlockPos crossHemisphereMomentum(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        WorldBorder border = world.getWorldBorder();
        double centerX = border.getCenterX();
        double targetX = HemispherePassage.mirrorX(player.getX(), centerX);
        double targetZ = player.getZ(); // z unchanged -- the reflection is X-only.

        // ONE probe, at the exact mirror column: 3x3 FULL ring + fluid/air safety. Null = no-op (caller logs).
        BlockPos safe = GlobeMod.placeSafeY(world, Mth.floor(targetX), Mth.floor(targetZ));
        if (safe == null) {
            return null;
        }
        // Arrival Y (P2 sweep refinement): the player keeps their EXACT Y iff it is actually FREE at the
        // mirror column -- feet AND head probed as loaded air (two block reads on the ring placeSafeY just
        // force-loaded; unloaded reads as not-free). Free covers both the airborne flyer (keeps altitude, no
        // ground-slam) and the common grounded case (the mirror makes matching heights the norm). Not free --
        // a solid non-leaf overhang mirrored overhead, or a flyer level with a mirrored floating island --
        // falls back to the VALIDATED safe surface: a visible but SAFE pop, only in those rare cases (the old
        // unprobed max(safeY, playerY) would have put an under-arch walker on the arch roof, or a flyer
        // inside unvalidated blocks). Pure decision: EvatorCrossing.chooseArrivalY.
        BlockPos feetAtMirror = BlockPos.containing(targetX, player.getY(), targetZ);
        boolean playerYFree = world.isLoaded(feetAtMirror)
                && world.getBlockState(feetAtMirror).isAir()
                && world.getBlockState(feetAtMirror.above()).isAir();
        double targetY = com.example.globe.core.EvatorCrossing.chooseArrivalY(playerYFree, safe.getY(), player.getY());

        boolean wasRiding = player.isPassenger();
        // Dismount first: no mount-carry across the seam, no ghost vehicle at the origin (B-5 scope decision).
        player.stopRiding();
        if (wasRiding) {
            GlobeMod.LOGGER.info("[Latitude][Evator] {} crossed while mounted; dismounted (mount-carry out of scope)",
                    player.getName().getString());
        }
        // Momentum + facing preserved THROUGH the teleport packet (amendment 5): DELTA_X/Y/Z keep velocity,
        // relative X_ROT/Y_ROT with 0/0 keep the exact yaw/pitch. fallDistance deliberately NOT reset.
        player.teleportTo(world, targetX, targetY, targetZ,
                EnumSet.of(Relative.DELTA_X, Relative.DELTA_Y, Relative.DELTA_Z, Relative.X_ROT, Relative.Y_ROT),
                0.0F, 0.0F, true);
        return BlockPos.containing(targetX, targetY, targetZ);
    }

    /**
     * The latitude-safe |Z| ceiling for arrival: stay equatorward of the pole warning band so a crossing can
     * never dump the player into the lethal polar cap. Mirrors {@code resolveSpawnChoice}'s {@code maxAbsZ}
     * discipline (warnStart - 500).
     */
    static int latitudeSafeMaxAbsZ(ServerLevel world) {
        int zRadius = LatitudeBiomes.getActiveRadiusBlocks();
        if (zRadius <= 0) {
            zRadius = (int) Math.round(LatitudeMath.halfSize(world.getWorldBorder()));
        }
        int warnStartZ = Math.max(0, zRadius - GlobeMod.POLE_WARNING_DISTANCE_BLOCKS);
        return Math.max(0, warnStartZ - 500);
    }
}
