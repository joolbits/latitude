package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.MirrorGeometry;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 5 Slice B-6 (Teleport-Evator) P1-PREP: the SYMMETRIC carver strip. Carvers are per-chunk world-coord
 * seeded and will NOT reflect (same class of problem as feature decoration), and a surface-breaching ravine at
 * the mirrored landing is the top visible betrayer of the seam -- so the sweep pulled the carver strip INTO P1
 * scope (design amendment 3). Veto carvers in BOTH mirror bands; the underground mismatch below the surface is
 * invisible during the crossing and stays unmirrored.
 *
 * <p><b>The seam.</b> Same {@code applyCarvers} chokepoint the shipping {@link NoiseChunkGeneratorCarveMixin}
 * already binds (bytecode-verified: that mixin loads under {@code required:true}, so the descriptor is live),
 * and re-uses its exact globe-only gate ({@code self.stable(GLOBE_SETTINGS_KEY)}) so a non-globe dimension is
 * never touched. Whole-chunk veto at HEAD, cancellable.
 *
 * <p><b>Gated on the PER-WORLD captured evator ({@link LatitudeBiomes#isEvatorActive()}), NOT the raw global
 * flag</b> -- a non-evator world is byte-identical even with {@code latitude.evatorV2.enabled} on. Anchored to
 * the intended X radius (immune to a lerping border). Fails OPEN on any uncertainty (allow carvers). Flag-off /
 * capture-off is byte-identical: the first check returns immediately.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class EdgeCarverVetoMixin {

    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_KEY = ResourceKey.create(
            Registries.NOISE_SETTINGS,
            Identifier.fromNamespaceAndPath("globe", "overworld")
    );

    @Inject(
            method = "applyCarvers(Lnet/minecraft/server/level/WorldGenRegion;JLnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void globe$stripMirrorBandCarvers(WorldGenRegion chunkRegion, long seed, RandomState noiseConfig,
                                              BiomeManager biomeAccess, StructureManager structureAccessor,
                                              ChunkAccess chunk, CallbackInfo ci) {
        if (!LatitudeV2Flags.EVATOR_V2_ENABLED || !LatitudeBiomes.isEvatorActive()) {
            return; // per-world capture off (or global flag off): byte-identical.
        }
        NoiseBasedChunkGenerator self = (NoiseBasedChunkGenerator) (Object) this;
        if (!self.stable(GLOBE_SETTINGS_KEY)) {
            return; // non-globe dimension: leave carvers alone (matches NoiseChunkGeneratorCarveMixin).
        }
        try {
            WorldBorder border = chunkRegion.getWorldBorder();
            double xRadius = com.example.globe.util.LatitudeMath.intendedXRadius(border);
            if (!(xRadius > 0.0 && xRadius < 1_000_000.0)) {
                return;
            }
            int centerBlockX = chunk.getPos().getMiddleBlockX();
            if (MirrorGeometry.inBand(centerBlockX, border.getCenterX(), xRadius)) {
                ci.cancel(); // no surface-breaching ravines to betray the mirror at the landing.
            }
        } catch (Throwable ignored) {
            // Border unavailable -- fail open (allow carvers).
        }
    }
}
