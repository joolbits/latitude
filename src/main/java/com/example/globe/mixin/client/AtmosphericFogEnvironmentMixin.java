package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeFogPresentation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Distance half of Latitude's fog (E/W sandstorm walls and polar closing-in).
 *
 * <p>26.2 mutated {@code FogData} straight off {@code FogRenderer.setupFog}'s return value. Here
 * {@code setupFog} returns the fog colour and keeps its {@code FogData} local, populating it through
 * a {@code FogEnvironment}. The atmospheric environment is the correct target: it is the one that
 * runs when the camera is not submerged, which is exactly the case Latitude's own gate already
 * requires.
 */
@Mixin(value = AtmosphericFogEnvironment.class, priority = 900)
public class AtmosphericFogEnvironmentMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void latitude$applyFogDistances(
            FogData fogData,
            Camera camera,
            ClientLevel level,
            float viewDistance,
            DeltaTracker deltaTracker,
            CallbackInfo ci) {
        LatitudeFogPresentation.applyDistances(fogData, camera);
    }
}
