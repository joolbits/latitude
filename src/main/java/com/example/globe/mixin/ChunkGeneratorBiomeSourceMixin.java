package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomeSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorBiomeSourceMixin {
    private static final boolean DEBUG_WORLDGEN_PATH =
            Boolean.getBoolean("latitude.debugWorldgenPath");

    private static final java.util.concurrent.atomic.AtomicBoolean DEBUG_WRAP_GATE_REJECT_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static final java.util.concurrent.atomic.AtomicBoolean DEBUG_WRAP_SUCCESS_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static final String GLOBE_SETTINGS_CHECKED =
            "globe:overworld|globe:overworld_xsmall|globe:overworld_small|globe:overworld_regular|globe:overworld_large|globe:overworld_massive";

    private static final Identifier GLOBE_SETTINGS_ID = Identifier.fromNamespaceAndPath("globe", "overworld");
    private static final Identifier GLOBE_SETTINGS_XSMALL_ID = Identifier.fromNamespaceAndPath("globe", "overworld_xsmall");
    private static final Identifier GLOBE_SETTINGS_SMALL_ID = Identifier.fromNamespaceAndPath("globe", "overworld_small");
    private static final Identifier GLOBE_SETTINGS_REGULAR_ID = Identifier.fromNamespaceAndPath("globe", "overworld_regular");
    private static final Identifier GLOBE_SETTINGS_LARGE_ID = Identifier.fromNamespaceAndPath("globe", "overworld_large");
    private static final Identifier GLOBE_SETTINGS_MASSIVE_ID = Identifier.fromNamespaceAndPath("globe", "overworld_massive");

    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, GLOBE_SETTINGS_ID);
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_XSMALL_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, GLOBE_SETTINGS_XSMALL_ID);
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_SMALL_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, GLOBE_SETTINGS_SMALL_ID);
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_REGULAR_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, GLOBE_SETTINGS_REGULAR_ID);
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_LARGE_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, GLOBE_SETTINGS_LARGE_ID);
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_MASSIVE_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, GLOBE_SETTINGS_MASSIVE_ID);
    @Shadow
    @Final
    @Mutable
    private BiomeSource biomeSource;

    @org.spongepowered.asm.mixin.Unique
    private BiomeSource globe$wrappedBiomeSource;

    @Inject(method = "<init>(Lnet/minecraft/world/biome/source/BiomeSource;)V", at = @At("TAIL"), require = 0)
    private void globe$wrapBiomeSource(BiomeSource biomeSource, CallbackInfo ci) {
        globe$maybeWrapBiomeSource();
    }

    @Inject(method = "<init>(Lnet/minecraft/world/biome/source/BiomeSource;Ljava/util/function/Function;)V", at = @At("TAIL"), require = 0)
    private void globe$wrapBiomeSource(BiomeSource biomeSource, java.util.function.Function<?, ?> settingsLookup, CallbackInfo ci) {
        globe$maybeWrapBiomeSource();
    }

    @Inject(method = "getBiomeSource", at = @At("HEAD"), cancellable = true)
    private void globe$returnWrappedBiomeSource(CallbackInfoReturnable<BiomeSource> cir) {
        globe$maybeWrapBiomeSource();
        if (this.globe$wrappedBiomeSource != null) {
            cir.setReturnValue(this.globe$wrappedBiomeSource);
            cir.cancel();
        }
    }

    private void globe$maybeWrapBiomeSource() {
        if (this.biomeSource instanceof LatitudeBiomeSource || this.globe$wrappedBiomeSource instanceof LatitudeBiomeSource) {
            return;
        }
        if (!((Object) this instanceof NoiseBasedChunkGenerator)) {
            if (DEBUG_WORLDGEN_PATH && DEBUG_WRAP_GATE_REJECT_LOGGED.compareAndSet(false, true)) {
                GlobeMod.LOGGER.info("[Latitude] biomeSource wrap gate reject: generator is not NoiseChunkGenerator action=not wrapping biome source");
            }
            return;
        }
        if (!globe$isAnyGlobeSettings()) {
            if (DEBUG_WORLDGEN_PATH && DEBUG_WRAP_GATE_REJECT_LOGGED.compareAndSet(false, true)) {
                GlobeMod.LOGGER.info("[Latitude] biomeSource wrap gate reject: settings not Globe preset checked={} matched={} settingsReady={} action=not wrapping biome source",
                        GLOBE_SETTINGS_CHECKED, globe$matchedSettingsLabel(), globe$hasResolvedSettings());
            }
            return;
        }
        java.util.Collection<net.minecraft.core.Holder<Biome>> biomes = this.biomeSource.possibleBiomes();
        int borderRadiusBlocks = globe$borderRadiusBlocks();
        // Ensure structure placement and surface rules see the same Latitude biome override as terrain.
        this.globe$wrappedBiomeSource = new LatitudeBiomeSource(this.biomeSource, biomes, borderRadiusBlocks);
        if (DEBUG_WORLDGEN_PATH && DEBUG_WRAP_SUCCESS_LOGGED.compareAndSet(false, true)) {
            GlobeMod.LOGGER.info("[Latitude] Worldgen path active: wrapped ChunkGenerator biomeSource settings={} checked={} radius={} action=using LatitudeBiomeSource",
                    globe$matchedSettingsLabel(), GLOBE_SETTINGS_CHECKED, borderRadiusBlocks);
        }
    }

    private boolean globe$isAnyGlobeSettings() {
        if (!((Object) this instanceof NoiseBasedChunkGenerator noise)) {
            return false;
        }
        // ChunkGenerator/NoiseChunkGenerator settings are not initialized yet in some constructor paths.
        // Never call matchesSettings() until settings is non-null.
        if (!((Object) this instanceof NoiseChunkGeneratorAccessor accessor)) {
            return false;
        }
        if (accessor.globe$getSettings() == null) {
            return false;
        }
        return noise.stable(GLOBE_SETTINGS_KEY)
                || noise.stable(GLOBE_SETTINGS_XSMALL_KEY)
                || noise.stable(GLOBE_SETTINGS_SMALL_KEY)
                || noise.stable(GLOBE_SETTINGS_REGULAR_KEY)
                || noise.stable(GLOBE_SETTINGS_LARGE_KEY)
                || noise.stable(GLOBE_SETTINGS_MASSIVE_KEY);
    }

    private boolean globe$hasResolvedSettings() {
        if (!((Object) this instanceof NoiseChunkGeneratorAccessor accessor)) {
            return false;
        }
        return accessor.globe$getSettings() != null;
    }

    private String globe$matchedSettingsLabel() {
        if (!((Object) this instanceof NoiseBasedChunkGenerator noise)) {
            return "not_noise_generator";
        }
        if (!globe$hasResolvedSettings()) {
            return "settings_unresolved";
        }
        if (noise.stable(GLOBE_SETTINGS_KEY)) return "overworld";
        if (noise.stable(GLOBE_SETTINGS_XSMALL_KEY)) return "overworld_xsmall";
        if (noise.stable(GLOBE_SETTINGS_SMALL_KEY)) return "overworld_small";
        if (noise.stable(GLOBE_SETTINGS_REGULAR_KEY)) return "overworld_regular";
        if (noise.stable(GLOBE_SETTINGS_LARGE_KEY)) return "overworld_large";
        if (noise.stable(GLOBE_SETTINGS_MASSIVE_KEY)) return "overworld_massive";
        return "unknown";
    }

    private int globe$borderRadiusBlocks() {
        if (!((Object) this instanceof NoiseBasedChunkGenerator noise)) {
            return 7500;
        }
        if (noise.stable(GLOBE_SETTINGS_KEY)) return 15000;
        if (noise.stable(GLOBE_SETTINGS_XSMALL_KEY)) return 3750;
        if (noise.stable(GLOBE_SETTINGS_SMALL_KEY)) return 5000;
        if (noise.stable(GLOBE_SETTINGS_REGULAR_KEY)) return 7500;
        if (noise.stable(GLOBE_SETTINGS_LARGE_KEY)) return 10000;
        if (noise.stable(GLOBE_SETTINGS_MASSIVE_KEY)) return 20000;
        return 7500;
    }
}
