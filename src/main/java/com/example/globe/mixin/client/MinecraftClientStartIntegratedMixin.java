package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.SaveLoader;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientStartIntegratedMixin {
    @Unique private static final Logger GLOBE_LOGGER = LoggerFactory.getLogger("LatitudeLoadingOverlay");
    @Unique private static final String LATITUDE_WORLD_STATE_FILE = "globe_latitude_world_state.dat";
    @Unique private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_KEY = globe$settingsKey("overworld");
    @Unique private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_XSMALL_KEY = globe$settingsKey("overworld_xsmall");
    @Unique private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_SMALL_KEY = globe$settingsKey("overworld_small");
    @Unique private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_REGULAR_KEY = globe$settingsKey("overworld_regular");
    @Unique private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_LARGE_KEY = globe$settingsKey("overworld_large");
    @Unique private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_MASSIVE_KEY = globe$settingsKey("overworld_massive");

    @Inject(method = "startIntegratedServer", at = @At("HEAD"))
    private void globe$markLatitudeReload(LevelStorage.Session session,
                                          ResourcePackManager dataPackManager,
                                          SaveLoader saveLoader,
                                          boolean newWorld,
                                          CallbackInfo ci) {
        if (newWorld || LatitudeClientState.isLatitudeWorldLoading()) {
            return;
        }

        int globeRadius = globe$readPersistedGlobeRadius(session);
        if (globeRadius <= 0 && !globe$hasLatitudeGenerator(saveLoader)) {
            return;
        }

        LatitudeClientState.beginExpedition(System.currentTimeMillis());
        LatitudeClientState.activateLatitudeLoading();
        LatitudeClientState.firstWorldLoad = false;
        GLOBE_LOGGER.info("[Latitude lifecycle] existing Latitude save loading overlay activated (globeRadius={}) - {}ms since beginExpedition",
                globeRadius,
                LatitudeClientState.elapsedSinceExpeditionMs());
    }

    @Unique
    private static int globe$readPersistedGlobeRadius(LevelStorage.Session session) {
        Path statePath = session.getDirectory(WorldSavePath.ROOT)
                .resolve("data")
                .resolve(LATITUDE_WORLD_STATE_FILE);
        if (!Files.exists(statePath)) {
            return 0;
        }

        try {
            NbtCompound root = NbtIo.readCompressed(statePath, NbtSizeTracker.ofUnlimitedBytes());
            return root.getCompoundOrEmpty("data").getInt("globe_radius", 0);
        } catch (Exception e) {
            GLOBE_LOGGER.warn("[Latitude lifecycle] failed to read existing Latitude save marker at {}", statePath, e);
            return 0;
        }
    }

    @Unique
    private static boolean globe$hasLatitudeGenerator(SaveLoader saveLoader) {
        try {
            Registry<DimensionOptions> dimensions = saveLoader.combinedDynamicRegistries()
                    .getCombinedRegistryManager()
                    .getOrThrow(RegistryKeys.DIMENSION);
            DimensionOptions overworld = dimensions.get(DimensionOptions.OVERWORLD);
            return overworld != null && globe$isLatitudeGenerator(overworld.chunkGenerator());
        } catch (Exception e) {
            GLOBE_LOGGER.warn("[Latitude lifecycle] existing-save loading overlay generator fallback failed", e);
            return false;
        }
    }

    @Unique
    private static boolean globe$isLatitudeGenerator(ChunkGenerator generator) {
        if (!(generator instanceof NoiseChunkGenerator noise) || noise.getSettings() == null) {
            return false;
        }

        return noise.matchesSettings(GLOBE_SETTINGS_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_XSMALL_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_SMALL_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_REGULAR_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_LARGE_KEY)
                || noise.matchesSettings(GLOBE_SETTINGS_MASSIVE_KEY);
    }

    @Unique
    private static RegistryKey<ChunkGeneratorSettings> globe$settingsKey(String path) {
        return RegistryKey.of(RegistryKeys.CHUNK_GENERATOR_SETTINGS, Identifier.of("globe", path));
    }
}
