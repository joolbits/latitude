package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Mixin(Minecraft.class)
public abstract class MinecraftClientStartIntegratedMixin {
    @Unique private static final Logger GLOBE_LOGGER = LoggerFactory.getLogger("LatitudeLoadingOverlay");
    @Unique private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_KEY =
            globe$noiseSettingsKey("overworld");
    @Unique private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_XSMALL_KEY =
            globe$noiseSettingsKey("overworld_xsmall");
    @Unique private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_SMALL_KEY =
            globe$noiseSettingsKey("overworld_small");
    @Unique private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_REGULAR_KEY =
            globe$noiseSettingsKey("overworld_regular");
    @Unique private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_LARGE_KEY =
            globe$noiseSettingsKey("overworld_large");
    @Unique private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_MASSIVE_KEY =
            globe$noiseSettingsKey("overworld_massive");

    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void globe$beginExistingLatitudeWorldLoading(LevelStorageSource.LevelStorageAccess session,
                                                         PackRepository packRepository,
                                                         WorldStem worldStem,
                                                         Optional<GameRules> gameRules,
                                                         boolean safeMode,
                                                         CallbackInfo ci) {
        if (!globe$isLatitudeWorld(worldStem)) {
            return;
        }

        if (!LatitudeClientState.isLatitudeWorldLoading()) {
            LatitudeClientState.beginExpedition(System.currentTimeMillis());
            LatitudeClientState.activateLatitudeLoading();
            GLOBE_LOGGER.info("[Latitude lifecycle] existing Latitude save loading overlay activated — {}ms since beginExpedition",
                    LatitudeClientState.elapsedSinceExpeditionMs());
        }
    }

    @Unique
    private static boolean globe$isLatitudeWorld(WorldStem worldStem) {
        if (worldStem == null || worldStem.worldDataAndGenSettings() == null
                || worldStem.worldDataAndGenSettings().genSettings() == null
                || worldStem.worldDataAndGenSettings().genSettings().dimensions() == null) {
            return false;
        }

        ChunkGenerator generator = worldStem.worldDataAndGenSettings().genSettings().dimensions().overworld();
        if (!(generator instanceof NoiseBasedChunkGenerator noise)) {
            return false;
        }

        return noise.stable(GLOBE_SETTINGS_KEY)
                || noise.stable(GLOBE_SETTINGS_XSMALL_KEY)
                || noise.stable(GLOBE_SETTINGS_SMALL_KEY)
                || noise.stable(GLOBE_SETTINGS_REGULAR_KEY)
                || noise.stable(GLOBE_SETTINGS_LARGE_KEY)
                || noise.stable(GLOBE_SETTINGS_MASSIVE_KEY);
    }

    @Unique
    private static ResourceKey<NoiseGeneratorSettings> globe$noiseSettingsKey(String path) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.NOISE_SETTINGS,
                Identifier.fromNamespaceAndPath("globe", path));
    }
}
