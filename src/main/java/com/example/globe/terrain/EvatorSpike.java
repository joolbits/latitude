package com.example.globe.terrain;

import com.example.globe.mixin.terrain.RandomStateAccessor;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * ============================ SPIKE — THROWAWAY PROOF CODE (B-6 P1 STEP 0) ============================
 *
 * <p>Install helper for the B-6 terrain-mirror mechanism spike. Lives in the {@code terrain} package (not
 * {@code dev}) for the same reason {@link TerrainRouterWrapping} does: it must reference
 * {@link RandomStateAccessor} (in the mixin package), which ordinary {@code dev} code may not touch
 * directly (Fabric/Mixin {@code IllegalClassLoadError}). The probe in {@code com.example.globe.dev} calls
 * into here.
 *
 * <p>Rebuilds the 15-field {@link NoiseRouter} wrapping ONLY {@code finalDensity} (#12) with
 * {@link EvatorMirrorSpikeFunction} — the identical single-field-rebuild shape
 * {@link TerrainRouterWrapping} uses for {@link GeoTerrainBiasFunction}, so the mechanism under test is a
 * faithful stand-in for the shipped install seam. Gated on {@code -Dlatitude.evatorSpike=true}. No
 * {@code LatitudeV2Flags} entry; delete with the rest of the spike.
 */
public final class EvatorSpike {

    private EvatorSpike() {
    }

    public static boolean armed() {
        return Boolean.parseBoolean(System.getProperty("latitude.evatorSpike", "false"));
    }

    /**
     * Wrap {@code randomState}'s {@code finalDensity} with the mirror reflection. Returns the wrapped
     * finalDensity for direct-compute probing, or the original if not armed / on error.
     */
    public static DensityFunction installMirror(RandomState randomState) {
        NoiseRouter original = randomState.router();
        DensityFunction originalFinalDensity = original.finalDensity();
        NoiseRouter rebuilt = new NoiseRouter(
                original.barrierNoise(),               // 1
                original.fluidLevelFloodednessNoise(), // 2
                original.fluidLevelSpreadNoise(),      // 3
                original.lavaNoise(),                  // 4
                original.temperature(),                // 5
                original.vegetation(),                 // 6
                original.continents(),                 // 7
                original.erosion(),                    // 8
                original.depth(),                      // 9
                original.ridges(),                     // 10
                original.preliminarySurfaceLevel(),    // 11 (left untouched for the spike)
                new EvatorMirrorSpikeFunction(originalFinalDensity), // 12
                original.veinToggle(),                 // 13
                original.veinRidged(),                 // 14
                original.veinGap());                   // 15
        ((RandomStateAccessor) (Object) randomState).globe$setRouter(rebuilt);
        return rebuilt.finalDensity();
    }
}
