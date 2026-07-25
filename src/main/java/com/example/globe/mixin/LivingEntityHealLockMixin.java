package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * B-7 S6 (FROZEN WOUNDS, Peetsa 2026-07-13) -- the single heal chokepoint for the polar heal lock.
 *
 * <p><b>The rule.</b> While SHELTERED (the S4 raw-sky-light predicate) inside the polar cold zone
 * ({@code |lat| >= 85}) and NOT near warmth (a lit non-soul campfire / fire / lava / working furnace within
 * the {@code PolarWarmth} scan box), ALL healing is cancelled -- hearts stay where they are until the player
 * packs a fire or steps back into the cold ("only the warmth of a fire mends them"). Near warmth, exposed,
 * or below 85 deg: healing is 100% vanilla.
 *
 * <p><b>The chokepoint.</b> Every gameplay heal funnels through {@code LivingEntity.heal(float)} in 26.2 --
 * natural/saturated food regen ({@code FoodData.tick}), the Regeneration effect, Instant Health, the golden
 * apple's regen component. Cancelling at HEAD here is therefore the whole lock; no per-source patching, no
 * cumulative pool, no persistent state -- {@link GlobeMod#isPolarHealLocked} is a pure predicate evaluated at
 * heal time (its warmth scan is cached ~1 s server-side). Golden-apple ABSORPTION (the yellow bonus hearts) is
 * deliberately NOT blocked -- it is armor-over-wounds via {@code setAbsorptionAmount}, not mending -- and dev
 * {@code setHealth} writes bypass {@code heal()} by design.
 *
 * <p><b>Scope.</b> {@code isPolarHealLocked} returns false for everything that is not a survival/adventure
 * {@link net.minecraft.server.level.ServerPlayer} in a globe overworld's polar cold zone, so mobs, other
 * dimensions, non-globe worlds and creative/spectator are structurally untouched (the same gate discipline as
 * {@link LivingEntityFreezeDamageMixin}). Client presentation (the frozen-hearts tint persistence, the
 * "Your wounds are frozen." whisper, thaw feedback) is P2's; this mixin is the server truth only.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealLockMixin {

    @Inject(method = "heal(F)V", at = @At("HEAD"), cancellable = true)
    private void globe$polarFrozenWoundsHealLock(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (GlobeMod.isPolarHealLocked(self)) {
            ci.cancel(); // frozen wounds: the heal waits for warmth (S6).
        }
    }
}
