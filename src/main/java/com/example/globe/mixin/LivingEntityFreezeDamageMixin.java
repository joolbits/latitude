package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * TEST 77 -- keep vanilla's blue frozen-heart HUD cue WITHOUT double-dipping the mod's own polar freeze curve.
 *
 * <p><b>Background.</b> The polar hazard drives its OWN latitude-scaled freeze damage (see
 * {@code PolarHazardWindow.freezeDamageAmount}/{@code freezeDamageIntervalTicks}, applied in
 * {@code GlobeMod.borderUxTick}). To keep that the sole damage source, the previous round capped the frost
 * visual at 139 -- one tick below vanilla's fully-frozen threshold (140) -- so vanilla's own automatic freeze
 * damage never fired. Side effect: the HUD hearts only tint blue when {@code isFullyFrozen()} is true
 * ({@code Hud$HeartType.forPlayer} returns FROZEN iff {@code ticksFrozen >= getTicksRequiredToFreeze()}, 140),
 * so capping at 139 permanently disabled that cue. Peetsa (TEST 77): "the hearts aren't turning blue while I'm
 * taking damage."
 *
 * <p><b>Fix.</b> {@code frostVisualTicks} now CROSSES 140 at the ~88 deg damage onset, so the blue hearts and
 * every other vanilla freeze visual/state fire correctly off our set {@code ticksFrozen}. This mixin cancels
 * ONLY vanilla's own automatic freeze-DAMAGE call so it can't double-dip with our curve.
 *
 * <p><b>The chokepoint.</b> Vanilla's automatic freeze damage lives in {@code LivingEntity.aiStep()} (26.2),
 * inside the {@code "freezing"} profiler block, gated on the fully-frozen threshold:
 * <pre>
 *   if (this.tickCount % 40 == 0 &amp;&amp; this.isFullyFrozen() &amp;&amp; this.canFreeze()) {
 *       this.hurtServer(serverLevel, this.damageSources().freeze(), 1.0F);   // &lt;-- redirected here
 *   }
 * </pre>
 * That single {@code hurtServer(..., freeze(), 1.0F)} invocation is the only place vanilla deals its fixed
 * 1 HP/40-tick freeze damage. {@code aiStep} has a SECOND {@code hurtServer} call further down (non-freeze),
 * so we bind precisely with a {@link Slice} from the {@code DamageSources.freeze()} call plus {@code ordinal = 0}
 * -- the first (and only, within the slice) {@code hurtServer} after {@code freeze()}, i.e. the freeze one.
 *
 * <p><b>Gate.</b> The redirect suppresses the damage ONLY for players in this mod's polar hazard band
 * ({@link GlobeMod#isInPolarFreezeDamageBand} -- the same in-band test {@code borderUxTick} uses to drive our
 * curve). For everything else -- real powder snow, other mobs, non-globe worlds, sub-hazard latitudes,
 * creative/spectator -- it calls the original {@code hurtServer} unchanged, so vanilla freezing stays 100%
 * intact. We touch ONLY this damage-dealing call; {@code ticksFrozen}, {@code isFullyFrozen()},
 * {@code getPercentFrozen()} and the heart render are all left untouched and driven off our set value.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFreezeDamageMixin {

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    ordinal = 0),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/damagesource/DamageSources;freeze()Lnet/minecraft/world/damagesource/DamageSource;")))
    private boolean globe$suppressVanillaPolarFreezeDamage(LivingEntity self, ServerLevel level, DamageSource source, float amount) {
        if (GlobeMod.isInPolarFreezeDamageBand(self)) {
            // In the mod's polar hazard band our latitude-scaled curve is the SOLE freeze-damage source --
            // swallow vanilla's fixed 1 HP/40-tick auto-damage so the two never stack (no double damage).
            return false;
        }
        // Anywhere else (real powder snow, non-globe, sub-hazard, creative/spectator, non-players): vanilla.
        return self.hurtServer(level, source, amount);
    }
}
