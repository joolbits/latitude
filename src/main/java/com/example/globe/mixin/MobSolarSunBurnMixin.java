package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.SolarTilt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Solar Tilt functional layer (P1) — undead sun-burn rule in the polar bands (§8a/§8b of
 * {@code docs/binder/solar-tilt-design-20260716.md}).
 *
 * <p>Intercepts {@code Mob.isSunBurnTick()} — the private gate {@code burnUndead()} consults. In 26.2 vanilla
 * routes "is it sunlit here" through {@code Level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN,
 * position())} (plus {@code getLightLevelDependentMagicValue()} / {@code isInWaterOrRain()}); that whole
 * evaluation keys off the GLOBAL clock, so at the summer pole's midnight sun it wrongly reads "not sunlit" at
 * global night, and at the winter pole's polar night it can read "sunlit" at global noon. This mixin fixes
 * both by overriding the return in-band, from the SAME {@link SolarTilt} evaluator the spawn rule and the sky
 * (P2) read (§8 one-evaluator law):
 *
 * <ul>
 *   <li><b>POLAR NIGHT</b>: force {@code false} — there is no sun overhead to burn undead, so zombies /
 *       skeletons roam the twilight around the clock without igniting.</li>
 *   <li><b>MIDNIGHT SUN</b>: force {@code true} for SKY-EXPOSED, DRY undead — the visible midnight sun burns
 *       them even at global midnight. Cave / roofed undead ({@code !canSeeSky}) are left to vanilla (they must
 *       not ignite underground), the consistent read of "the sun is up, so it burns" (§8b, owner recommend
 *       YES). <b>Wet guard (sweep FIX-1):</b> the force-true branch additionally requires
 *       {@code !isInWaterOrRain()} — the EXACT wet check vanilla's own {@code isSunBurnTick} consults
 *       internally (javap bytecode receipt: {@code invokevirtual Entity.isInWaterOrRain()Z}; 26.2 has no
 *       {@code isInWaterRainOrBubble}) — because a HEAD force-true would otherwise bypass vanilla's wet
 *       suppression and ignite a swimming zombie/drowned in the 74.5–90° liquid sea (ignite/extinguish
 *       flicker + helmet durability drain). No unit test for the guard itself: it is a one-term delegation to
 *       vanilla's live entity predicate with no extractable pure math (the pure band/elevation side is fully
 *       covered by {@code SolarTiltTest}); its live proof rides the §12 functional checklist.</li>
 * </ul>
 *
 * <p><b>Gate.</b> Byte-identical flag-off (first line returns when {@link LatitudeV2Flags#SOLAR_TILT_V2_ENABLED}
 * is false). Server-only ({@code level() instanceof ServerLevel}); the band is {@code NONE} (→ no-op) below the
 * A2 functional floor (74.5°), at the equinox, or off-globe. Helmet protection etc. is untouched — this only
 * decides the sun-burn TICK; {@code sunProtectionSlot()} still saves a helmeted mob downstream. No persistent
 * state (§8d).
 *
 * <p><b>Honesty line (sweep FIX-2).</b> The injection target ({@code Mob.isSunBurnTick()Z}, private) is
 * signature-verified via javap receipts against the 26.2 loom jar, but mixin APPLICATION is unproven until
 * P2's first live run — §12 functional checklist item 6 is the gate (repo precedent:
 * {@code FogRendererPolarSetupMixin}'s no-live-launch note).
 */
@Mixin(Mob.class)
public abstract class MobSolarSunBurnMixin {

    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void globe$solarPolarSunBurn(CallbackInfoReturnable<Boolean> cir) {
        if (!LatitudeV2Flags.SOLAR_TILT_V2_ENABLED) {
            return; // byte-identical flag-off
        }
        Entity self = (Entity) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) {
            return; // client copies / non-server levels: leave vanilla alone
        }
        BlockPos pos = self.blockPosition();
        SolarTilt.FunctionalBand band = GlobeMod.solarFunctionalBand(level, pos.getZ());
        if (band == SolarTilt.FunctionalBand.POLAR_NIGHT) {
            cir.setReturnValue(false); // no sun to burn in — suppress
        } else if (band == SolarTilt.FunctionalBand.MIDNIGHT_SUN && level.canSeeSky(pos)
                && !self.isInWaterOrRain()) {
            // Visible midnight sun on the exposed, DRY surface — force burn. The wet guard (FIX-1) mirrors
            // vanilla's own isInWaterOrRain() suppression, which a HEAD force-true would otherwise bypass
            // (a swimming drowned/zombie in the polar sea must NOT flicker-ignite).
            cir.setReturnValue(true);
        }
        // NONE, midnight-sun not sky-exposed, or midnight-sun wet → fall through to vanilla.
    }
}
