package com.example.globe.mixin;

import com.example.globe.GlobeRegions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseChunkGeneratorCarveMixin {
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_KEY = ResourceKey.create(
            Registries.NOISE_SETTINGS,
            Identifier.fromNamespaceAndPath("globe", "overworld")
    );

    @Inject(
            method = "applyCarvers(Lnet/minecraft/server/level/WorldGenRegion;JLnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void globe$disableCarversInPolarCap(WorldGenRegion chunkRegion, long seed, RandomState noiseConfig, BiomeManager biomeAccess,
                                               StructureManager structureAccessor, ChunkAccess chunk, CallbackInfo ci) {
        NoiseBasedChunkGenerator self = (NoiseBasedChunkGenerator) (Object) this;
        if (!self.stable(GLOBE_SETTINGS_KEY)) {
            return;
        }

        int centerZ = chunk.getPos().getMinBlockZ() + 8;
        if (Math.abs(centerZ) >= GlobeRegions.POLAR_CAP_START) {
            ci.cancel();
        }
    }
}
