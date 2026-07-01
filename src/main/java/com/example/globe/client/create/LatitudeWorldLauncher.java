package com.example.globe.client.create;

import com.example.globe.GlobePending;
import com.example.globe.client.GlobeWorldSize;
import com.example.globe.client.GlobeWorldSizeSelection;
import com.example.globe.client.LatitudeClientState;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomes;
import com.mojang.serialization.Lifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

/**
 * Phase 5B: World launch logic for the bespoke Latitude create-world screen.
 * Replicates CreateWorldScreen.createLevel() + startServer() without requiring
 * a CreateWorldScreen instance.
 */
public final class LatitudeWorldLauncher {

    private static final Logger LOGGER = LoggerFactory.getLogger("LatitudeWorldLauncher");

    private LatitudeWorldLauncher() {}

    /**
     * Launch a new Latitude world. Called from "Begin Expedition" button.
     *
     * <p>Ordering invariant: Latitude pending state (GlobePending, GlobeWorldSizeSelection)
     * is written as late as possible — after session creation succeeds (step 9) and
     * immediately before startNewWorld (step 11). If any step 1–9 throws, no Latitude
     * state was written. If step 10 succeeds but step 11 throws, state is rolled back.</p>
     */
    public static void beginExpedition(Minecraft client, LatitudeCreateWorldScreen screen,
                                       WorldCreationContext holder,
                                       String worldName, String seed,
                                       GlobeWorldSize size, LatitudeBands.Band spawnZone,
                                       LatitudeBiomes.GlobeShape worldShape,
                                       GameType gameMode, boolean hardcore,
                                       Difficulty difficulty, boolean allowCommands,
                                       boolean startWithCompass, boolean bonusChest,
                                       boolean generateStructures,
                                       GameRules gameRules, int worldTypeIdx) {
        LOGGER.info("[LAT][CWPATH] LatitudeWorldLauncher.beginExpedition screen={} worldTypeIdx={} worldName={}",
                screen.getClass().getName(), worldTypeIdx, worldName);
        // worldTypeIdx: 0=Latitude, 1=Vanilla, 2=Vanilla Superflat
        boolean isLatitude = worldTypeIdx == 0;
        long t0 = System.currentTimeMillis();
        if (isLatitude) {
            LatitudeClientState.beginExpedition(t0);
        }
        LOGGER.info("[Latitude lifecycle] begin expedition — type={}, size={}, zone={}, {}ms since beginExpedition",
                isLatitude ? "latitude" : worldTypeIdx == 1 ? "vanilla" : "superflat",
                size.name(), spawnZone.id(), LatitudeClientState.elapsedSinceExpeditionMs());
        try {
            // ── 1. Size preset resolution ──
            net.minecraft.resources.Identifier presetId;
            if (worldTypeIdx == 2) {
                presetId = net.minecraft.resources.Identifier.withDefaultNamespace("flat");
            } else if (worldTypeIdx == 1) {
                presetId = net.minecraft.resources.Identifier.withDefaultNamespace("normal");
            } else {
                presetId = size.worldPresetId;
            }
            ResourceKey<WorldPreset> presetKey = ResourceKey.create(Registries.WORLD_PRESET, presetId);

            // ── 2. Create WorldCreator ──
            WorldCreationUiState wc = new WorldCreationUiState(
                    client.getLevelSource().getBaseDir(),
                    holder,
                    Optional.of(presetKey),
                    OptionalLong.empty());

            // ── 3. Set world name ──
            wc.setName(worldName);

            // ── 4. Set seed (raw — vanilla's GeneratorOptions.parseSeed() trims internally) ──
            wc.setSeed(seed);

            // Apply the preset via setWorldType to ensure dimensions are updated
            Registry<WorldPreset> presetRegistry = holder.worldgenLoadContext()
                    .lookupOrThrow(Registries.WORLD_PRESET);
            Holder<WorldPreset> presetEntry = presetRegistry.get(presetKey).orElse(null);
            if (presetEntry == null) {
                LOGGER.error("Latitude world preset '{}' was not present in the loaded registry; enabled packs={}",
                        presetKey.identifier(), holder.dataConfiguration().dataPacks().getEnabled());
                if (isLatitude) {
                    LatitudeClientState.clearLatitudeLoadingState();
                }
                client.setScreen(screen);
                return;
            }
            wc.setWorldType(new WorldCreationUiState.WorldTypeEntry(presetEntry));

            // ── 5. Apply game settings ──
            WorldCreationUiState.SelectedGameMode wcMode = hardcore ? WorldCreationUiState.SelectedGameMode.HARDCORE
                    : gameMode == GameType.CREATIVE ? WorldCreationUiState.SelectedGameMode.CREATIVE
                    : WorldCreationUiState.SelectedGameMode.SURVIVAL;
            wc.setGameMode(wcMode);
            wc.setAllowCommands(allowCommands);
            wc.setDifficulty(difficulty);
            wc.setBonusChest(bonusChest);
            wc.setGenerateStructures(generateStructures);

            // ── 6. Sync structures/bonus into holder ──
            wc.onChanged();

            // WorldCreator.update() can rebuild settings from generic defaults, so
            // reassert the selected Latitude preset before extracting the final holder.
            WorldCreationContext goh = wc.getSettings();
            Registry<WorldPreset> updatedPresetRegistry = goh.worldgenLoadContext()
                    .lookupOrThrow(Registries.WORLD_PRESET);
            Holder<WorldPreset> updatedPresetEntry = updatedPresetRegistry.get(presetKey).orElse(null);
            if (updatedPresetEntry == null) {
                LOGGER.error("Latitude world preset '{}' disappeared after WorldCreator.update(); enabled packs={}",
                        presetKey.identifier(), goh.dataConfiguration().dataPacks().getEnabled());
                if (isLatitude) {
                    LatitudeClientState.clearLatitudeLoadingState();
                }
                client.setScreen(screen);
                return;
            }
            wc.setWorldType(new WorldCreationUiState.WorldTypeEntry(updatedPresetEntry));

            // ── 6. Extract updated holder ──
            goh = wc.getSettings();

            // ── 7. Build level metadata (replicates CreateWorldScreen.createLevel lines 284-298) ──
            WorldDimensions launchDimensions;
            WorldDimensions.Complete dimensionsConfig;
            if (isLatitude) {
                launchDimensions = forceLatitudeOverworld(goh, updatedPresetEntry);
                Registry<LevelStem> noDatapackOverride = emptyLevelStemRegistry();
                dimensionsConfig = launchDimensions.bake(noDatapackOverride);
            } else {
                launchDimensions = goh.selectedDimensions();
                dimensionsConfig = launchDimensions.bake(goh.datapackDimensions());
            }

            LayeredRegistryAccess<RegistryLayer> combinedDynamicRegistries =
                    goh.worldgenRegistries()
                            .replaceFrom(RegistryLayer.DIMENSIONS, dimensionsConfig.dimensionsRegistryAccess());

            Lifecycle lifecycle = FeatureFlags.isExperimental(goh.dataConfiguration().enabledFeatures())
                    ? Lifecycle.experimental() : Lifecycle.stable();
            Lifecycle lifecycle2 = combinedDynamicRegistries.compositeAccess().allRegistriesLifecycle();
            Lifecycle lifecycle3 = lifecycle2.add(lifecycle);

            LevelSettings levelInfo = new LevelSettings(
                    wc.getName().trim(),
                    gameMode,
                    new LevelSettings.DifficultySettings(difficulty, hardcore, false),
                    allowCommands,
                    goh.dataConfiguration());

            PrimaryLevelData levelProperties = new PrimaryLevelData(
                    levelInfo,
                    dimensionsConfig.specialWorldProperty(),
                    lifecycle3);

            final WorldCreationContext launchHolder = goh;
            final WorldDimensions launchWorldDimensions = launchDimensions;
            final LayeredRegistryAccess<RegistryLayer> launchCombinedDynamicRegistries = combinedDynamicRegistries;
            final PrimaryLevelData launchLevelProperties = levelProperties;
            final GameRules launchGameRules = gameRules;

            // ── 8. Show "Preparing..." ──
            client.setScreenAndShow(new GenericMessageScreen(Component.translatable("createWorld.preparing")));
            CompletableFuture.runAsync(() -> {
                LevelStorageSource.LevelStorageAccess session;
                try {
                    session = client.getLevelSource().createAccess(wc.getTargetFolder());
                } catch (Exception e) {
                    client.execute(() -> {
                        LOGGER.error("Failed to create world session for '{}'", wc.getTargetFolder(), e);
                        if (isLatitude) {
                            LatitudeClientState.clearLatitudeLoadingState();
                        }
                        client.setScreen(screen);
                    });
                    return;
                }

                if (isLatitude) {
                    GlobePending.pendingGlobeRadius = size.borderRadiusBlocks;
                    GlobeWorldSizeSelection.set(size);
                    GlobePending.set(spawnZone.id().toUpperCase(java.util.Locale.ROOT));
                    GlobePending.startWithCompass = startWithCompass;
                    GlobePending.pendingGlobeShape = LatitudeBiomes.shapeToString(worldShape);
                    LatitudeClientState.activateLatitudeLoading();
                    LOGGER.info("[Latitude lifecycle] bespoke overlay activated — {}ms since beginExpedition",
                            LatitudeClientState.elapsedSinceExpeditionMs());
                }

                client.execute(() -> {
                    try {
                        client.createWorldOpenFlows()
                                .createLevelFromExistingSettings(session, launchHolder.dataPackResources(), launchCombinedDynamicRegistries,
                                        new LevelDataAndDimensions.WorldDataAndGenSettings(
                                                launchLevelProperties,
                                                // Build WorldOptions explicitly so the bonus-chest + generate-structures
                                                // choices actually reach the created world. launchHolder.options() does
                                                // NOT carry them (they live on the UI state), so using it directly drops
                                                // both flags — that is why the bonus chest stopped generating.
                                                new net.minecraft.world.level.levelgen.WorldGenSettings(
                                                        new net.minecraft.world.level.levelgen.WorldOptions(
                                                                launchHolder.options().seed(), generateStructures, bonusChest),
                                                        launchWorldDimensions)),
                                        Optional.ofNullable(launchGameRules));
                    } catch (Exception e) {
                        LOGGER.error("Failed to start new world", e);
                        GlobePending.consume();
                        GlobePending.pendingGlobeRadius = 0;
                        GlobePending.pendingGlobeShape = null;
                        GlobeWorldSizeSelection.set(GlobeWorldSize.REGULAR);
                        if (isLatitude) {
                            LatitudeClientState.clearLatitudeLoadingState();
                        }
                        try {
                            session.close();
                        } catch (Exception closeEx) {
                            LOGGER.warn("Failed to close session after launch failure", closeEx);
                        }
                        client.setScreen(screen);
                    }
                });
            }, Util.backgroundExecutor());
        } catch (Exception e) {
            LOGGER.error("Unexpected error in beginExpedition", e);
            // Steps 1-9 failed — no Latitude state was written
            if (isLatitude) {
                LatitudeClientState.clearLatitudeLoadingState();
            }
            client.setScreen(screen);
        }
    }

    private static WorldDimensions forceLatitudeOverworld(WorldCreationContext context, Holder<WorldPreset> presetEntry) {
        WorldDimensions presetDimensions = presetEntry.value().createWorldDimensions();
        ChunkGenerator globeOverworldGen = presetDimensions.overworld();
        Map<ResourceKey<LevelStem>, LevelStem> mergedDimensions =
                new LinkedHashMap<>(presetDimensions.dimensions());
        context.datapackDimensions().entrySet().forEach(entry -> {
            if (!LevelStem.OVERWORLD.equals(entry.getKey())) {
                mergedDimensions.put(entry.getKey(), entry.getValue());
            }
        });

        WorldDimensions launchDimensions = new WorldDimensions(mergedDimensions)
                .replaceOverworldGenerator(context.worldgenLoadContext(), globeOverworldGen);
        LOGGER.info("[Latitude] create-world: forced overworld to Latitude preset generator (preset={} class={} settings={}); presetDimensions={}, datapackDimensions={}, launchDimensions={}",
                presetEntry.unwrapKey().map(key -> key.identifier().toString()).orElse("<unbound>"),
                globeOverworldGen.getClass().getSimpleName(),
                noiseSettingsId(globeOverworldGen),
                presetDimensions.dimensions().size(),
                context.datapackDimensions().entrySet().size(),
                launchDimensions.dimensions().size());
        return launchDimensions;
    }

    private static String noiseSettingsId(ChunkGenerator generator) {
        if (generator instanceof NoiseBasedChunkGenerator noise) {
            return noise.generatorSettings()
                    .unwrapKey()
                    .map(key -> key.identifier().toString())
                    .orElse("<inline>");
        }
        return "<non-noise>";
    }

    private static Registry<LevelStem> emptyLevelStemRegistry() {
        return new MappedRegistry<LevelStem>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze();
    }
}
