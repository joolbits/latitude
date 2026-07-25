package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.example.globe.core.PolarPrecipitationRule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Correctness fix: NEVER render rain at the poles -- force SNOW at extreme latitudes.
 *
 * <p>Peetsa's bug: standing at 90 deg over ice-spike terrain, vanilla RAIN streaks fell. The cause
 * is latitude-blind columns: vanilla's noise router places {@code river}/{@code ocean} (temperature
 * 0.5, precipitation RAIN) anywhere including the polar cap, and {@code LatitudeBiomeSource} never
 * re-classifies them, so a polar river/ocean column reports RAIN. The weather renderer samples a
 * grid of columns around the player, so even standing on snowy ice spikes a nearby polar river
 * renders rain. (Our {@code ClientLevelStormSkyMixin} lifts the client rain LEVEL over 85-90 deg for
 * the polar storm sky, which is what makes any precipitation render even in "clear" weather -- so at
 * the pole the wrong-typed columns are especially visible.)
 *
 * <p><b>Why this chokepoint.</b> {@link ClientLevel#getPrecipitationAt(BlockPos)} is the SINGLE
 * per-column RAIN-vs-SNOW decision that vanilla consults for ALL three rain surfaces:
 * <ul>
 *   <li>the falling rain STREAKS ({@code WeatherEffectRenderer.extractRenderState} branches on
 *       {@code getPrecipitationAt(pos) == RAIN} -> rain columns, {@code == SNOW} -> snow columns);</li>
 *   <li>the ground SPLASH particles ({@code ClientLevel.doAnimateTick} spawns {@code ParticleTypes.RAIN}
 *       only when {@code getPrecipitationAt(pos) == RAIN});</li>
 *   <li>the rain SOUND -- the SAME branch plays {@code SoundEvents.WEATHER_RAIN}/{@code WEATHER_RAIN_ABOVE}.</li>
 * </ul>
 * Overriding RAIN -> SNOW here fixes streaks, splash particles AND sound in one place; a
 * renderer-only patch would leave polar rain splashes and rain sound wrong.
 *
 * <p><b>Client-only, biome-independent, non-destructive.</b> {@code getPrecipitationAt} is a
 * ClientLevel method (no server/worldgen effect; snow ACCUMULATION is a separate server system
 * driven by biome and is out of scope). We only convert RAIN -> SNOW; NONE stays NONE (we never
 * fabricate precipitation where vanilla has none). Gated to globe worlds and {@code |lat| >= 75 deg}
 * via the pure {@link PolarPrecipitationRule}; zero effect on non-globe worlds or below 75 deg.
 * Latitude is read from the QUERIED COLUMN's Z (not the player's), so each column is judged on its
 * own latitude.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelPolarSnowMixin {

    @Inject(method = "getPrecipitationAt", at = @At("RETURN"), cancellable = true)
    private void globe$forcePolarSnow(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (cir.getReturnValue() != Biome.Precipitation.RAIN) {
            return; // only rain is wrong at the pole; leave NONE and SNOW untouched
        }
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }
        ClientLevel self = (ClientLevel) (Object) this;
        double absLatDeg = com.example.globe.util.LatitudeMath.absLatDegExact(
                self.getWorldBorder(), pos.getZ());
        if (PolarPrecipitationRule.forcesSnow(true, absLatDeg)) {
            cir.setReturnValue(Biome.Precipitation.SNOW);
        }
    }
}
