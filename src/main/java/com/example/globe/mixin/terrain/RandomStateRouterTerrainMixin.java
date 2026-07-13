package com.example.globe.mixin.terrain;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.terrain.EvatorTerrainReflection;
import com.example.globe.terrain.GeoTerrainBiasFunction;
import com.example.globe.terrain.TerrainRouterWrapping;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 4 (Terrain Integration Spike) interception point for REAL GAMEPLAY chunk generation. Installs
 * {@link GeoTerrainBiasFunction} over the world's {@code finalDensity} (#12) via the shared
 * {@link TerrainRouterWrapping#installIfArmed(RandomState, NoiseBasedChunkGenerator)} helper, leaving the
 * other 14 {@code NoiseRouter} fields untouched.
 *
 * <p><b>Why {@code ChunkMap} and not {@code RandomState}'s constructor.</b> The design's primary target was
 * the {@code RandomState} constructor TAIL, but that site (verified against the 26.2 jar) receives only a
 * flattened {@code NoiseGeneratorSettings} value -- no {@code ServerLevel}, no {@code ChunkGenerator}, no
 * settings {@code ResourceKey} -- so there is no reliable positive globe check there (design §1.2 forbids a
 * process-global boolean-only gate). {@code ChunkMap}'s constructor is exactly where
 * {@code RandomState.create(...)} is actually called for real gameplay worlds (verified: offset 254 of the
 * {@code ChunkMap} ctor), AND it holds the fully-constructed {@code ChunkGenerator} (via its own
 * {@code WorldGenContext} field, populated directly from the constructor argument), so the design's §1.2
 * relocation fallback applies here.
 *
 * <p><b>Why the {@code NoiseBasedChunkGenerator} overload specifically, not the {@code ServerLevel} one.</b>
 * At {@code ChunkMap}'s own constructor TAIL, {@code this.level.getChunkSource()} is verified (via the 26.2
 * jar) to still be {@code null} -- {@code ChunkMap} is constructed BY {@code ServerChunkCache}'s own
 * constructor, which only assigns its {@code chunkMap} field (and thus makes itself visible as
 * {@code ServerLevel.chunkSource}) AFTER the {@code ChunkMap} constructor returns. So
 * {@code GlobeMod.isGlobeOverworld(ServerLevel)} -- which needs {@code getChunkSource()} for BOTH its
 * branches -- cannot be called from here; it throws every time (safely caught, but permanently unable to
 * arm). The generator, however, is directly available via this class's own {@code @Shadow}'d
 * {@code WorldGenContext} field with no {@code getChunkSource()} indirection, so the narrower
 * generator-settings-key check ({@link com.example.globe.GlobeMod#isGlobeNoiseGenerator}) is used here
 * instead. See {@link TerrainRouterWrapping}'s class javadoc for the full explanation of both overloads.
 *
 * <p><b>This is NOT the only call site (important correction, caught in review before this landed).</b>
 * {@code RandomState.create(...)} is also called directly by this mod's own dev/atlas tooling
 * ({@code com.example.globe.dev.BiomePreviewExporter}, two call sites) completely bypassing {@code ChunkMap}.
 * That path uses the OTHER {@code installIfArmed} overload (taking a {@code ServerLevel}), because its
 * {@code ServerLevel} is already fully constructed when it runs, AND because that tooling runs on a plain
 * {@code minecraft:normal} level-type and marks a world as "globe" purely via persisted
 * {@code LatitudeWorldState} radius -- the generator-settings-key check alone would never fire there.
 *
 * <p>The triple gate (design §1.2), the outer try/catch fallback (design §4.2), and the 15-arg router
 * rebuild all live in {@link TerrainRouterWrapping} so the logic is identical (and maintained in one place)
 * regardless of which call site triggered it.
 */
@Mixin(ChunkMap.class)
public abstract class RandomStateRouterTerrainMixin {

    @Shadow
    @Final
    private RandomState randomState;

    @Shadow
    @Final
    private WorldGenContext worldGenContext;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void globe$installTerrainBias(CallbackInfo ci) {
        // Cheap flag gates first, before touching any Minecraft object. (TerrainRouterWrapping re-checks
        // these too; the early return here just avoids an unnecessary instanceof/field read on the hot
        // world-load path when the feature is off, which is the default.)
        if (!LatitudeV2Flags.TERRAIN_V2_ENABLED || !LatitudeV2Flags.GEO_V2_ENABLED) {
            return;
        }
        RandomState rs = this.randomState;
        WorldGenContext ctx = this.worldGenContext;
        if (rs == null || ctx == null) {
            return;
        }
        ChunkGenerator generator = ctx.generator();
        if (!(generator instanceof NoiseBasedChunkGenerator noiseGen)) {
            return;
        }
        TerrainRouterWrapping.installIfArmed(rs, noiseGen);
    }

    /**
     * Phase 5 B-6 (Teleport-Evator) P1: install the leaf-level terrain MIRROR on the same freshly-constructed
     * {@code RandomState}. Independent of the Phase-4 terrain bias above (different flag, different feature), so
     * it lives in its own TAIL inject. Runs after the Phase-4 wrap when both are on (declaration order); for the
     * combo the geo-bias root reads true X while leaves read reflected X inside the band -- an accepted P1
     * limitation, {@code terrainV2} is default-off and the evator is flown terrainV2-off. Gated on the global
     * flag + the generator-settings-key globe check; the per-world capture is re-checked per sample inside the
     * reflector, so flag-off / captured-off worlds stay byte-identical. See {@link EvatorTerrainReflection}.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void globe$installEvatorTerrainMirror(CallbackInfo ci) {
        if (!LatitudeV2Flags.EVATOR_V2_ENABLED) {
            return;
        }
        RandomState rs = this.randomState;
        WorldGenContext ctx = this.worldGenContext;
        if (rs == null || ctx == null) {
            return;
        }
        if (!(ctx.generator() instanceof NoiseBasedChunkGenerator noiseGen)
                || !com.example.globe.GlobeMod.isGlobeNoiseGenerator(noiseGen)) {
            return;
        }
        EvatorTerrainReflection.install(rs);
    }
}
