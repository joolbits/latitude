package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomeSource;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class ServerLevelFindClosestBiomeMixin {
    private static final boolean DEBUG_WORLDGEN_PATH =
            Boolean.getBoolean("latitude.debugWorldgenPath");

    private static final AtomicBoolean DEBUG_LOCATE_WRAP_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean DEBUG_LOCATE_FALLBACK_LOGGED = new AtomicBoolean(false);

    @Redirect(
            method = "findClosestBiome3d(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;III)Lcom/mojang/datafixers/util/Pair;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getBiomeSource()Lnet/minecraft/world/level/biome/BiomeSource;"
            ),
            require = 1
    )
    private BiomeSource globe$useLatitudeBiomeSourceForLocate(ChunkGenerator generator) {
        BiomeSource source = generator.getBiomeSource();
        if (!(generator instanceof NoiseBasedChunkGenerator noise)
                || !GlobeMod.shouldApplyLatitudeWorldgen(noise)) {
            return source;
        }
        try {
            BiomeSource rawSource = source instanceof LatitudeBiomeSource latitudeSource
                    ? latitudeSource.original()
                    : source;
            int radius = GlobeMod.borderRadiusForNoiseGenerator(noise);
            ServerLevel world = (ServerLevel) (Object) this;
            RandomState noiseConfig = world.getChunkSource().randomState();
            Registry<Biome> biomeRegistry = world.registryAccess().registryOrThrow(Registries.BIOME);
            BiomeSource wrapped = LatitudeBiomeSource.forLocate(rawSource, biomeRegistry, radius,
                    noise, noiseConfig, world);
            if (DEBUG_WORLDGEN_PATH && DEBUG_LOCATE_WRAP_LOGGED.compareAndSet(false, true)) {
                GlobeMod.LOGGER.info("[Latitude] locate biome search using LatitudeBiomeSource source={} radius={} context=MIXIN",
                        rawSource.getClass().getName(), radius);
            }
            return wrapped;
        } catch (RuntimeException e) {
            if (DEBUG_WORLDGEN_PATH && DEBUG_LOCATE_FALLBACK_LOGGED.compareAndSet(false, true)) {
                GlobeMod.LOGGER.warn("[Latitude] locate biome search fell back to raw source source={} reason={}:{}",
                        source.getClass().getName(), e.getClass().getSimpleName(), e.getMessage());
            }
            return source;
        }
    }
}
