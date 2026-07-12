package com.example.globe;

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
 *   <li>Mirror target: {@code targetX = 2*centerX - x} ({@link HemispherePassage#mirrorX}), keep Z. This is
 *       the X border-half geometry (border centered at 0,0), NEVER the Z latitude radius, and is IDENTICAL
 *       in Classic and Mercator -- only the valid X range differs.</li>
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
        int targetX = (int) Math.round(HemispherePassage.mirrorX(playerX, centerX));

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
