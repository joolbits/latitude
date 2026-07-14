package com.example.globe;

import com.example.globe.core.EdgeGeometry;
import com.example.globe.core.HemispherePassage;
import com.example.globe.core.PoleArrivalSearch;
import com.example.globe.core.PoleGeometry;
import com.example.globe.util.BiomeSamplerTools;
import com.example.globe.util.BiomeSamplerTools.SamplerTemplate;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.RandomState;

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

    /** B-7 pole last-resort: equatorward Z-nudge step (blocks), toward the border center (the pole mirror of
     *  B-5's inland X-nudge). */
    static final int Z_EQUATOR_NUDGE_STEP = 128;
    /** B-7 pole last-resort: max equatorward Z-nudge (blocks) before giving up. */
    static final int Z_EQUATOR_NUDGE_MAX = 1024;

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

    // ---- B-7 (Pole Passage): the OVER-THE-POLE crossing, the Z-axis sibling of the E/W crossing above ----

    /**
     * Perform the pole crossing for an ALREADY-VALIDATED, in-band, alive, online {@link ServerPlayer}. Callers
     * own the guards (flag on, globe overworld, pole-distance re-validation, alive/online, surface/not-in-water);
     * this trusts them and only does the safe mechanical move. Returns the arrival position, or {@code null} if
     * no safe column was found (in which case the player was NOT moved).
     *
     * <p><b>Over-the-pole continuity (design §3.3).</b> The player emerges on the FAR MERIDIAN (mirror X), on the
     * SAME pole side (Z sign kept, pulled to the 89.5 deg S5 arrival line -- deep in the far blizzard), heading REVERSED
     * ({@code yaw + 180}, wrapped) -- walking north over the pole comes out walking south. This absolute-yaw
     * override is the ONLY delta vs the EW crossing (which keeps yaw); pitch is preserved and momentum zeroed for
     * ceremony parity, and the dismount is kept (no vehicle-carry across the seam).
     */
    static BlockPos crossPole(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        BlockPos arrival = resolvePoleArrival(world, player.getX(), player.getZ());
        if (arrival == null) {
            GlobeMod.LOGGER.warn("[Latitude][PolePassage] No safe far-meridian column for {} at x={} z={} within {} probes; crossing aborted (staying put)",
                    player.getName().getString(), player.getX(), player.getZ(), PROBE_BUDGET);
            return null;
        }
        // Heading reverses across the pole: a straight line over the pole flips both horizontal components, which
        // the mirror-X + far-side re-entry realises exactly as yaw+180 (absolute, same mechanism B-5 uses for its
        // yaw pass-through). Wrapped to [-180,180].
        float arrivalYaw = Mth.wrapDegrees(player.getYRot() + 180.0f);
        // Dismount first: no mount-carry across the seam, no ghost vehicle at the origin.
        player.stopRiding();
        player.teleportTo(world, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5,
                EnumSet.noneOf(Relative.class), arrivalYaw, player.getXRot(), true);
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.fallDistance = 0.0F;
        GlobeMod.LOGGER.info("[Latitude][PolePassage] Crossed {} over the pole to x={} y={} z={} yaw={}",
                player.getName().getString(), arrival.getX(), arrival.getY(), arrival.getZ(), arrivalYaw);
        return arrival;
    }

    /**
     * Resolve the safe over-the-pole arrival column for a player at ({@code playerX},{@code playerZ}), or
     * {@code null} if none was found within {@link #PROBE_BUDGET} probes. The target is the far meridian
     * ({@code mirrorX}) at the SAME pole side pulled to the 89.5 deg S5 arrival latitude; the primary search walks the
     * arrival PARALLEL (+/-X at the fixed arrival Z), bounded EVERY candidate by the corner X-clamp (A2:
     * {@link PoleArrivalSearch#xClampAbs}) so a corner crossing can never land in the EW passage band; the
     * last-resort nudge steps equatorward along Z toward center. A best-effort surface-class preference tries
     * SAME-medium (land-vs-ocean) columns first, then any-safe (mismatch beats no-op; safety is law). Every
     * arrival column additionally rejects a powder-snow landing (A4). Total {@code placeSafeY} probes are
     * hard-capped by the shared {@link #PROBE_BUDGET}, so this can never stall the server thread.
     */
    static BlockPos resolvePoleArrival(ServerLevel world, double playerX, double playerZ) {
        WorldBorder border = world.getWorldBorder();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        int zRadius = poleLatitudeRadius(world);
        double xRadiusIntended = LatitudeMath.intendedXRadius(border);

        // Target: far meridian (mirror X), SAME pole side, pulled to the 89.5 deg S5 arrival line (the escape
        // trek is the arrival experience; the post-crossing cold grace covers the curtain window).
        double sign = (playerZ - centerZ) >= 0.0 ? 1.0 : -1.0;
        int arrivalAbsZ = LatitudeMath.zForLatitudeDeg(PoleGeometry.ARRIVAL_DEG_POLE, zRadius);
        int targetZ = (int) Math.round(centerZ) + (int) Math.round(sign * arrivalAbsZ);

        // A2 corner X-clamp: bound the mirrored target AND every +/-X candidate equatorward of the EW band.
        double xClampAbs = PoleArrivalSearch.xClampAbs(xRadiusIntended);
        int mirroredX = (int) Math.round(HemispherePassage.mirrorX(playerX, centerX));
        int baseX = PoleArrivalSearch.clampX(mirroredX, centerX, xClampAbs);
        int[] xs = PoleArrivalSearch.candidateXs(baseX, centerX, xClampAbs,
                PoleArrivalSearch.ARRIVAL_SEARCH_STEP, PROBE_BUDGET);

        // Best-effort surface-class preference. If the probe can't be built or a column can't be classified, we
        // silently fall back to a class-agnostic search -- NEVER fail a crossing over medium (safety is law).
        ClassProbe probe = buildClassProbe(world, zRadius);
        Boolean departureIsLand = probe == null ? null
                : isLandProbe(probe, (int) Math.round(playerX), (int) Math.round(playerZ));
        boolean canClassify = probe != null && departureIsLand != null;

        boolean[] candMatches = null;
        if (canClassify) {
            candMatches = new boolean[xs.length];
            for (int i = 0; i < xs.length; i++) {
                Boolean land = isLandProbe(probe, xs[i], targetZ);
                candMatches[i] = land != null && land.booleanValue() == departureIsLand.booleanValue();
            }
        }

        int budget = PROBE_BUDGET;

        // Pass A -- SAME medium as departure, capped at the preferred sub-budget so an all-mismatch parallel
        // still leaves probes for pass B / the Z nudge.
        if (canClassify) {
            int reserveForB = PROBE_BUDGET - Math.min(PoleArrivalSearch.PREFERRED_PROBE_BUDGET, PROBE_BUDGET);
            for (int i = 0; i < xs.length && budget > reserveForB; i++) {
                if (!candMatches[i]) {
                    continue;
                }
                budget--;
                BlockPos safe = placeSafePoleColumn(world, xs[i], targetZ);
                if (safe != null) {
                    return safe;
                }
            }
        }

        // Pass B -- any remaining safe column on the arrival parallel (medium mismatch beats a failed crossing).
        for (int i = 0; i < xs.length && budget > 0; i++) {
            if (canClassify && candMatches[i]) {
                continue; // already tried in pass A
            }
            budget--;
            BlockPos safe = placeSafePoleColumn(world, xs[i], targetZ);
            if (safe != null) {
                return safe;
            }
        }

        // Pass C -- last resort: nudge equatorward along Z (toward center) at the clamped mirror X, never
        // crossing the center into the far hemisphere.
        for (int nudge = Z_EQUATOR_NUDGE_STEP; nudge <= Z_EQUATOR_NUDGE_MAX && budget > 0; nudge += Z_EQUATOR_NUDGE_STEP) {
            int zEq = targetZ - (int) Math.round(sign * nudge);
            if ((sign > 0.0 && zEq < centerZ) || (sign < 0.0 && zEq > centerZ)) {
                break;
            }
            budget--;
            BlockPos safe = placeSafePoleColumn(world, baseX, zEq);
            if (safe != null) {
                return safe;
            }
        }

        // Budget exhausted (or bounds ran out) with no safe column: no-op the crossing, player stays put.
        return null; // never teleport into fluid/void
    }

    /** The Z (latitude) radius for pole geometry: the active radius if armed, else the synced latitude radius
     *  (both lerp-immune -- never the raw live border half). */
    private static int poleLatitudeRadius(ServerLevel world) {
        int zRadius = LatitudeBiomes.getActiveRadiusBlocks();
        if (zRadius <= 0) {
            zRadius = (int) Math.round(LatitudeMath.latitudeRadius(world.getWorldBorder()));
        }
        return zRadius;
    }

    /** {@link GlobeMod#placeSafeY} plus the A4 powder-snow rejection: a safe, non-fluid, non-powder-snow column. */
    private static BlockPos placeSafePoleColumn(ServerLevel world, int x, int z) {
        BlockPos safe = GlobeMod.placeSafeY(world, x, z);
        if (safe == null) {
            return null;
        }
        if (isPowderSnowLanding(world, safe)) {
            return null; // A4: a player dropped onto powder snow sinks in and freezes faster -- step on.
        }
        return safe;
    }

    /** A4: reject a landing whose ground (or 1-2 below) is powder snow. Powder snow is neither a fluid (so
     *  {@code placeSafeY}'s fluid checks miss it) nor MOTION_BLOCKING (so a heightmap can top out on the solid
     *  block beneath a powder-snow column, leaving the standing block powder snow). {@code spawn} is the AIR the
     *  player occupies; {@code spawn.below()} is what they stand on. */
    private static boolean isPowderSnowLanding(ServerLevel world, BlockPos spawn) {
        return world.getBlockState(spawn.below()).getBlock() == Blocks.POWDER_SNOW
                || world.getBlockState(spawn.below(2)).getBlock() == Blocks.POWDER_SNOW;
    }

    /** The no-chunk-gen biome-source context for surface-class matching, built once per crossing. */
    private record ClassProbe(SamplerTemplate template, Climate.Sampler sampler, int radiusBlocks, int classifyY) {
    }

    /** Build the surface-class probe (best effort). Returns {@code null} on any failure -> class-agnostic search. */
    private static ClassProbe buildClassProbe(ServerLevel world, int zRadius) {
        try {
            SamplerTemplate template = BiomeSamplerTools.createTemplate(world);
            long seed = world.getServer().getWorldGenSettings().options().seed();
            RandomState noiseConfig = RandomState.create(
                    template.settings().value(), template.noiseParameters(), seed);
            Climate.Sampler sampler = noiseConfig.sampler();
            int radiusBlocks = LatitudeBiomes.getActiveRadiusBlocks();
            if (radiusBlocks <= 0) {
                radiusBlocks = zRadius;
            }
            return new ClassProbe(template, sampler, radiusBlocks, LatitudeBiomes.SURFACE_CLASSIFY_Y);
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[Latitude][PolePassage] Surface-class probe unavailable; arrival uses class-agnostic search", e);
            return null;
        }
    }

    /** Classify a column land (true) / ocean-family (false) via the shared no-chunk-gen probe; {@code null} if
     *  the probe throws for this column (treated as unknown = non-matching). */
    private static Boolean isLandProbe(ClassProbe p, int x, int z) {
        try {
            return GlobeMod.isLandBiome(p.template(), p.sampler(), x, z, p.classifyY(), p.radiusBlocks());
        } catch (Exception e) {
            return null;
        }
    }
}
