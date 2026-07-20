package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarBarrensBand;
import com.example.globe.core.PolarWaterFreezeRule;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Correctness fix: at the poles ALL exposed water freezes -- kill the biome-temperature veto that lets
 * latitude-blind rivers/oceans stay liquid at 89 deg. Server-side sibling of the client-only
 * {@code ClientLevelPolarSnowMixin} (no rain at the poles).
 *
 * <p>Peetsa's bug: standing at 89 deg S in a full blizzard, a pool of liquid water sat in his doorway.
 * Vanilla's per-column freeze decision {@code Biome.shouldFreeze(LevelReader, BlockPos, boolean)} opens with
 * {@code if (this.warmEnoughToRain(pos, seaLevel)) return false;}. Vanilla's noise router places
 * {@code river}/{@code ocean} (base temperature 0.5) anywhere including the polar cap and
 * {@code LatitudeBiomeSource} never re-classifies them, so a polar river/ocean column is "warm enough to
 * rain" and its water never freezes; player-placed water on such a column has the same problem.
 *
 * <p><b>Why this chokepoint.</b> {@code Biome.shouldFreeze} is the SINGLE method every water-&gt;ice path
 * funnels through: the ongoing {@code ServerLevel.tickPrecipitation} random-tick freeze (which runs BEFORE
 * its {@code isRaining()} gate, so it freezes in clear weather too), plus the worldgen {@code LakeFeature}
 * and {@code SnowAndFreezeFeature} paths. Hooking it once covers ongoing AND at-generation freezing.
 *
 * <p><b>Why a redirect of just {@code warmEnoughToRain}, not a forced return.</b> The ONLY latitude-blind
 * part of {@code shouldFreeze} is that first temperature veto. Everything after it -- inside build height,
 * block light &lt; 10, fluid is water, block is a {@code LiquidBlock}, and (for the neighbour-checked call)
 * "not surrounded by water on all four sides" -- is genuine and correct. So we surgically neutralise ONLY
 * the veto: {@link Redirect} the {@code this.warmEnoughToRain(pos, seaLevel)} invocation inside
 * {@code shouldFreeze} and, for a polar globe column, return {@code false} ("not warm, i.e. cold enough").
 * Vanilla then runs its OWN genuine-water/light/edge logic and freezes exactly the exposed water it would
 * freeze in a naturally-cold biome, inheriting vanilla's edge-inward, over-time cadence (hence no latitude
 * fade is needed). A blunt "force {@code shouldFreeze} -&gt; true" was rejected: it cannot tell "warm biome"
 * from "not water / too bright / surrounded", so it would place ice on non-water blocks.
 *
 * <p><b>Gated, biome-independent, non-destructive to non-polar/non-globe worlds.</b> Gated to armed globe
 * worlds ({@code LatitudeBiomes.getActiveRadiusBlocks() > 0}) and {@code |lat| >= 85 deg} via the pure
 * {@link PolarWaterFreezeRule}, behind {@link LatitudeV2Flags#POLAR_WATER_FREEZE_ENABLED} (default on).
 * Flag-off, non-globe, or below 85 deg all return vanilla's unmodified {@code warmEnoughToRain}, so those
 * paths are byte-identical. Latitude is read from the FROZEN COLUMN's Z (not any player), so each column is
 * judged on its own latitude, and via {@link LatitudeMath#absLatDegExact} it is correct on both Classic and
 * Mercator worlds. {@code warmEnoughToRain} elsewhere ({@code coldEnoughToSnow}/{@code getPrecipitationAt}/
 * {@code shouldSnow}) is untouched -- a {@code Redirect} only rewrites this one call site inside
 * {@code shouldFreeze}, so snow behaviour is unchanged; this fix is scoped to liquid-water freezing only.
 *
 * <p><b>S22 WATER v6 -- the WIDENED TICK FRONT (owner flight TEST 113, 2026-07-19).</b> The Barrens band and
 * the B-9 crevasses start at 82 deg, but every tick-time freeze consumer bailed below ~84 -- the owner poured
 * water at 84S and NOTHING froze. On the live server ({@code level instanceof ServerLevel} -- the same gate
 * the S21 sweep fix used, which worldgen never passes) a NON-OCEAN column's front is now the SHARED barrens
 * band decision ({@code PolarWaterFreezeRule.tickFrontFreezes}: ONSET 82 -> FULL 84 on the coherent barrens
 * fray -- one decision with the biome/glacier/carvers, Art VI). Worldgen paths and ocean columns keep the
 * unmodified 85 law ({@code FREEZE_ALL_DEG} -- the approved sea-ice line), so gen output is byte-identical
 * and the pack-ice edge does not move.
 *
 * <p><b>S21(d) SOURCE-FREEZES-LAST.</b> This one redirect is the seam every source freeze funnels through, so it
 * also carries the source-last veto: when the column WOULD freeze but this source block still touches live
 * {@code FLOWING_WATER} (any of its six neighbours), it reports "warm" to POSTPONE the freeze -- the source
 * outlives its fall and freezes only once the connected flow has all iced (or drained). This stops the still-water
 * surface from claiming the source first and beheading a running waterfall (the owner's TEST-111 finding). The
 * postpone is inside the same in-zone/flag/globe gate, so flag-off / non-globe / below-front stay byte-identical;
 * see {@link PolarWaterFreezeRule#sourceFreezePostponed}. The roofed descent's source branch inherits it for free
 * (it calls {@code biome.shouldFreeze}).
 */
@Mixin(Biome.class)
public abstract class BiomePolarWaterFreezeMixin {

    @Redirect(
            method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;warmEnoughToRain(Lnet/minecraft/core/BlockPos;I)Z"
            ),
            require = 1
    )
    private boolean globe$forcePolarWaterFreeze(Biome self, BlockPos pos, int seaLevel, LevelReader level) {
        boolean warm = self.warmEnoughToRain(pos, seaLevel);
        // Vanilla behaviour (byte-identical) whenever there is nothing to correct: the biome is already cold
        // enough to freeze, the fix is switched off, or this is not an armed globe world. The non-globe check
        // also short-circuits the common vanilla-survival case before any latitude math runs.
        if (!warm || !LatitudeV2Flags.POLAR_WATER_FREEZE_ENABLED || LatitudeBiomes.getActiveRadiusBlocks() <= 0) {
            return warm;
        }
        double absLatDeg = LatitudeMath.absLatDegExact(level.getWorldBorder(), pos.getZ());
        boolean wouldFreeze;
        if (level instanceof net.minecraft.server.level.ServerLevel
                && LatitudeV2Flags.POLAR_BARRENS_ENABLED
                && absLatDeg >= PolarWaterFreezeRule.TICK_FRONT_ONSET_DEG
                && !level.getBiome(pos).is(BiomeTags.IS_OCEAN)) {
            // S22 WATER v6 (a) -- the WIDENED TICK FRONT, TICK-TIME + LAND ONLY (owner flight TEST 113,
            // 2026-07-19: an 84S pour froze NOTHING -- the 82-84 Barrens/crevasse band had no machinery).
            // On the live server, a NON-OCEAN column's freeze front is the SAME shared barrens band decision
            // (ONSET 82 -> FULL 84 on the coherent barrens fray noise -- one decision with the biome placement,
            // glacier body, and B-9 crevasse carvers; Art VI) instead of the 85 law. This is a strict SUPERSET
            // of the 85-frayed front for land columns (at/above 84 the band fraction is 1.0, so everything the
            // old front froze still freezes), so no live column loses its ice.
            //   * ServerLevel gate (the S21-sweep precedent, see globe$sourceFreezePostponed below): WORLDGEN
            //     paths (LakeFeature/SnowAndFreezeFeature pass a WorldGenLevel, never a ServerLevel) fall to
            //     the else-branches and still see the UNMODIFIED 85 law -- gen output byte-identical.
            //   * OCEAN columns keep the 85 law even at tick time: the sea-ice line at 85 is the APPROVED
            //     worldgen seam, and letting the live tick creep pack ice to 82 would visibly split the frozen
            //     sea from what gen produced. Land water is the TEST-113 complaint; the sea already works.
            //   * The fray sample is skipped at/above FULL_DEG (84) where the band fraction is 1.0 and any
            //     sample passes -- only the 82-84 fray strip pays for noise.
            double barrensFray = absLatDeg < PolarBarrensBand.FULL_DEG
                    ? LatitudeBiomes.polarBarrensFrayNoise(pos.getX(), pos.getZ())
                    : 0.0;
            wouldFreeze = PolarWaterFreezeRule.tickFrontFreezes(true, absLatDeg, barrensFray);
        } else if (LatitudeV2Flags.POLAR_BARRENS_ENABLED && PolarWaterFreezeRule.inFreezeFrayBand(absLatDeg)) {
            // B-9a SEA-FREEZE FRAY (flag-gated in the barrens family): inside the 85 +/- 1 strip the freeze
            // FRONT wanders on a coherent per-column fray sample instead of the razor line the owner
            // screenshotted. Outside the strip the frayed predicate equals the razor by construction, so the
            // noise is only sampled where it can matter; barrens-flag-off (or outside the strip) is the
            // untouched razor path, byte-identical. Deterministic per column, so worldgen and tick freezing
            // always agree. (v6: this branch is now the WORLDGEN + OCEAN law -- land tick columns take the
            // widened front above.)
            wouldFreeze = PolarWaterFreezeRule.freezesWaterFrayed(true, absLatDeg,
                    LatitudeBiomes.polarSeaFreezeFrayNoise(pos.getX(), pos.getZ()));
        } else {
            // At/above the polar threshold freezesWater is true; below it, false so we keep the warm veto.
            wouldFreeze = PolarWaterFreezeRule.freezesWater(true, absLatDeg);
        }
        if (!wouldFreeze) {
            return warm; // equatorward of the front: the untouched warm veto (byte-identical; warm is true here)
        }
        // S21(d) SOURCE-FREEZES-LAST: this column WOULD freeze, but a SOURCE block that still touches live
        // flowing water must not freeze yet -- the owner watched the still-water surface claim his SOURCE first
        // and behead a running waterfall. Report "warm" (postpone) so vanilla's shouldFreeze returns false and
        // the source stays liquid this pass; it freezes only once its connected flow is all ice (no adjacent
        // flowing left). This ONE seam covers both source-freeze paths -- ongoing tickPrecipitation AND the
        // roofed descent's source branch (ServerLevelRoofedWaterFreezeMixin calls biome.shouldFreeze).
        if (globe$sourceFreezePostponed(level, pos)) {
            return true; // pretend warm -> shouldFreeze returns false -> the source outlives its fall
        }
        // In-zone and not postponed: report "not warm" so vanilla's own genuine-water/light/edge logic in
        // shouldFreeze runs and freezes this (latitude-blind, warm-biome) exposed source water column.
        return false;
    }

    /**
     * S21(d): is {@code pos} a SOURCE water block with at least one adjacent live flowing-water neighbour, so its
     * freeze must be postponed (source-freezes-last)? Gated on {@code pos} actually being a {@code Fluids.WATER}
     * source -- vanilla's {@code shouldFreeze} only ever freezes sources, so a non-source {@code pos} needs no
     * postpone (and pays no neighbour scan). The touch set is all SIX neighbours (below + four horizontals +
     * ABOVE): ABOVE is included because a source directly under a live fall is the beheading case too (see
     * {@link PolarWaterFreezeRule#sourceFreezePostponed}). Reads only fluid states (no chunk gen); {@code below()}
     * etc. on a mutable {@code pos} return fresh immutable positions, so the caller's cursor is never disturbed.
     */
    private static boolean globe$sourceFreezePostponed(LevelReader level, BlockPos pos) {
        // S21 sweep REQUIRED FIX: source-freezes-last is a TICK-time law only — at worldgen the
        // neighbor reads would break byte-identity (and chunk-order determinism) on the always-on
        // freeze family. Both tick consumers pass a real ServerLevel; worldgen paths do not.
        if (!(level instanceof net.minecraft.server.level.ServerLevel)) {
            return false;
        }
        if (level.getFluidState(pos).getType() != Fluids.WATER) {
            return false; // not a source -> shouldFreeze wouldn't freeze it anyway; no postpone, no scan
        }
        boolean adjacentHasFlowing =
                globe$isFlowingWater(level, pos.below())
                || globe$isFlowingWater(level, pos.above())
                || globe$isFlowingWater(level, pos.north())
                || globe$isFlowingWater(level, pos.south())
                || globe$isFlowingWater(level, pos.east())
                || globe$isFlowingWater(level, pos.west());
        return PolarWaterFreezeRule.sourceFreezePostponed(adjacentHasFlowing);
    }

    /** True iff {@code pos} holds live flowing (non-source) water. Lava/other fluids and source water are not. */
    private static boolean globe$isFlowingWater(LevelReader level, BlockPos pos) {
        return level.getFluidState(pos).getType() == Fluids.FLOWING_WATER;
    }
}
