package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B-4 item 4: STORM SKY at the poles. Peetsa's complaint -- near the pole the sky stayed blue with a
 * shining sun while snow fell, which reads wrong. Instead of hand-rolling sky/sun/precip overrides, we lift
 * the CLIENT level's rain level toward 1.0 over the polar ambient window [85,90] deg and let VANILLA do the
 * rest, consistently: {@code SkyRenderer.getRainLevel} greys the sky and fades the sun/moon, and
 * {@code WeatherEffectRenderer} (which gates on {@code getRainLevel} and picks RAIN vs SNOW per column via
 * {@code getPrecipitationAt}) renders real, dense snowfall in the snowy polar biomes -- ONE value driving
 * the whole storm look. This also carries the "snow isn't increasing" complaint (item 5): vanilla snowfall
 * is far denser/more legible than our ambient particle layer.
 *
 * <p><b>Client-only, weather-state read-only.</b> We target {@link Level#getRainLevel} (ClientLevel does
 * not override it) but override the RETURN only for the client's OWN {@link ClientLevel} instance -- the
 * {@code instanceof ClientLevel} guard excludes the integrated server's ServerLevel that shares this JVM in
 * singleplayer, so {@code setRainLevel}/the weather cycle/server weather are never touched. Gated to globe
 * worlds, sky-exposed, ambient window open (|lat| >= 85) via {@link GlobeClientState}; zero effect elsewhere.
 */
@Mixin(Level.class)
public abstract class ClientLevelStormSkyMixin {

    @Inject(method = "getRainLevel", at = @At("TAIL"), cancellable = true)
    private void globe$polarStormSky(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel self)) {
            return; // never the integrated server's ServerLevel in singleplayer
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level != self) {
            return; // only the level the client player is actually in
        }
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }
        GlobeClientState.Eval eval = GlobeClientState.evaluate(mc);
        if (!eval.active() || !eval.surfaceOk()) {
            return; // no whiteout deep underground / off a globe world
        }
        double absLatDeg = com.example.globe.util.LatitudeMath.absLatDegExact(
                self.getWorldBorder(), mc.player.getZ());
        // B-4 round 3 item 3: STEEPENED. The old lift used the linear 85->90 ambientProgress, so at 86 deg
        // it was only ~0.2 and the sun still shone (Peetsa's complaint). stormLevel reaches full overcast by
        // ~87.5 deg (0.4 already at 86 deg): the sky reads clearly stormy well before the pole, sun gone by 87.5.
        float target = com.example.globe.core.PolarHazardWindow.stormLevel(absLatDeg);
        if (target <= 0.0f) {
            return;
        }
        float original = cir.getReturnValueF();
        if (target > original) {
            cir.setReturnValue(target);
        }
    }
}
