package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import com.example.globe.client.create.RecreatedWorldMetadata;
import com.example.globe.util.LatitudeBands;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

    // 26.2's doWorldLoad(LevelStorageAccess, PackRepository, WorldStem, Optional<GameRules>,
    // boolean) carries an Optional<GameRules> parameter that 1.21.11's does not: this target's
    // signature is (LevelStorageAccess, PackRepository, WorldStem, boolean). Mixin does not
    // validate an @Inject handler's own parameter list against the target's real descriptor until
    // runtime bytecode weaving, so a stale handler signature compiles cleanly and only fails when
    // this class is actually loaded -- which is CLIENT-ONLY and neither the static verifier nor a
    // dedicated-server boot proof ever exercises it.
    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void globe$beginExistingLatitudeWorldLoading(LevelStorageSource.LevelStorageAccess session,
                                                         PackRepository packRepository,
                                                         WorldStem worldStem,
                                                         boolean safeMode,
                                                         CallbackInfo ci) {
        boolean detectedLatitudeWorld = globe$isLatitudeWorld(worldStem);
        if (!detectedLatitudeWorld) {
            // Existing vanilla/superflat worlds use Minecraft's normal loading lifecycle. Clear any stale
            // Latitude flag from a prior failed launch so our overlay can never delay or trap their screen.
            if (LatitudeClientState.isLatitudeWorldLoading()) {
                LatitudeClientState.clearLatitudeLoadingState();
            }
            GLOBE_LOGGER.info("[Latitude lifecycle] integrated-world loading overlay skipped (latitudeWorldDetected=false)");
            return;
        }

        if (!LatitudeClientState.isLatitudeWorldLoading()) {
            LatitudeClientState.beginExpedition(System.currentTimeMillis());
            LatitudeClientState.activateLatitudeLoading();
            globe$applyResumedZoneLabel(session);
            GLOBE_LOGGER.info("[Latitude lifecycle] integrated-world loading overlay activated — {}ms since beginExpedition (latitudeWorldDetected={})",
                    LatitudeClientState.elapsedSinceExpeditionMs(), detectedLatitudeWorld);
        }
    }

    /**
     * Reads the last-known band straight off disk, before the integrated server has even started,
     * so a resumed world's loading screen can show it without waiting on any network round trip.
     * Absent for a save that predates this field, or one that was never actually entered.
     */
    @Unique
    private static void globe$applyResumedZoneLabel(LevelStorageSource.LevelStorageAccess session) {
        try {
            String bandId = RecreatedWorldMetadata.lastKnownBandId(session.getLevelPath(LevelResource.ROOT));
            LatitudeBands.Band band = LatitudeBands.fromCanonicalId(bandId);
            if (band != null) {
                LatitudeClientState.setLoadingZoneLabel(band.displayName());
            }
        } catch (Exception e) {
            GLOBE_LOGGER.warn("[Latitude lifecycle] could not read last-known band for the loading screen", e);
        }
    }

    @Unique
    private static boolean globe$isLatitudeWorld(WorldStem worldStem) {
        // 26.2 reaches the overworld generator via WorldStem.worldDataAndGenSettings().
        // 1.21.11's WorldStem has no gen-settings accessor at all: dimensions live in the
        // LEVEL_STEM registry, so the generator is read through the stem's registry access.
        if (worldStem == null || worldStem.registries() == null) {
            return false;
        }

        LevelStem overworldStem = worldStem.registries().compositeAccess()
                .lookupOrThrow(Registries.LEVEL_STEM)
                .getValue(LevelStem.OVERWORLD);
        if (overworldStem == null) {
            return false;
        }

        ChunkGenerator generator = overworldStem.generator();
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
