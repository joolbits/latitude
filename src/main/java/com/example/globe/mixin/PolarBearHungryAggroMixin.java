package com.example.globe.mixin;

import com.example.globe.content.HungryPolarBearTargetGoal;
import com.example.globe.core.LatitudeV2Flags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S25(B-behavior) HUNGRY BEARS -- the ONE seam (Peetsa 2026-07-20, TEST 117 round: "I don't see any polar
 * bears... in polar storm country" -- Crew 1 owns the spawns; this mixin owns the BEHAVIOR: in the
 * food-scarce Barrens a polar bear is not neutral, it hunts players within ~16 blocks, warning roar and all).
 *
 * <p><b>The seam.</b> A TAIL inject on {@code PolarBear.registerGoals()} (26.2 javap-verified: vanilla
 * registers goal-selector 0/1/1/4/5/6/7 and target-selector 1 hurt-by, 2 cub-defense, 3 player-anger NATG,
 * 4 fox hunt, 5 reset-anger) that appends ONE extra target goal at priority 5 --
 * {@link HungryPolarBearTargetGoal}, a plain (non-anonymous -- mixin merge safety, see its javadoc) subclass
 * of vanilla's own {@code NearestAttackableTargetGoal} carrying the hunt gates: barrens flag family, armed
 * globe world, bear latitude &gt;= 80 (the polar-country rung, KEEP-SHARED with
 * {@code PolarHazardWindow.AMBIENT_ONSET_DEG}), not Peaceful, adult bears, 16-block pin. All vanilla goals
 * keep their exact priorities and objects -- hurt-by/cub-defense/anger always outrank the hunger -- and the
 * vanilla warning roar is preserved by construction (it lives in the melee goal, downstream of ANY target
 * source; ground truth in the goal's javadoc).
 *
 * <p><b>Flag-off is byte-identical:</b> {@code POLAR_BARRENS_ENABLED} is a static-final launch flag, so with
 * the family off this inject registers NOTHING and the vanilla goal tree is untouched (the goal itself also
 * re-checks the flag, belt-and-braces). Registration happens in the {@code Mob} constructor exactly where
 * vanilla registers its own targeting -- no level reads occur here (the goal reads latitude/difficulty
 * per-poll at canUse time, where the bear is live and positioned).
 *
 * <p>Extends {@code Mob} so the inherited protected {@code targetSelector} is directly accessible (the house
 * accessor-free idiom for protected superclass members).
 */
@Mixin(PolarBear.class)
public abstract class PolarBearHungryAggroMixin extends Mob {

    protected PolarBearHungryAggroMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "registerGoals()V", at = @At("TAIL"), require = 1)
    private void globe$addHungryBarrensTargeting(CallbackInfo ci) {
        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED) {
            return; // flag-off: no goal registered at all -- vanilla bear byte-identical
        }
        PolarBear self = (PolarBear) (Object) this;
        this.targetSelector.addGoal(5, new HungryPolarBearTargetGoal(self));
    }
}
