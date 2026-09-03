package com.example.globe.mixin;

import com.example.globe.GlobeRegions;
import com.example.globe.world.LatitudeWorldgenScope;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
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
            method = "generateCarvers(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void globe$disableCarversInPolarCap(ChunkAccess chunk,
                                                Blender blender,
                                                NoiseChunk noiseChunk,
                                                RandomState randomState,
                                                BiomeManager biomeManager,
                                                WorldGenRegion world,
                                                MaterialRule materialRule,
                                                CallbackInfo ci) {
        if (!LatitudeWorldgenScope.isActive()) {
            return;
        }
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
