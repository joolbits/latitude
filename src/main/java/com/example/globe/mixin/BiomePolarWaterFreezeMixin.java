package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarWaterFreezeRule;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
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
        // At/above the polar threshold, report "not warm" so vanilla's own genuine-water/light/edge logic in
        // shouldFreeze runs and freezes this (latitude-blind, warm-biome) exposed water column; below it,
        // freezesWater is false so we return the untouched warm veto.
        return !PolarWaterFreezeRule.freezesWater(true, absLatDeg);
    }
}
