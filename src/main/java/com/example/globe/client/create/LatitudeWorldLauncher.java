package com.example.globe.client.create;

import com.example.globe.GlobePending;
import com.example.globe.client.GlobeWorldSize;
import com.example.globe.client.GlobeWorldSizeSelection;
import com.example.globe.client.LatitudeClientConfig;
import com.example.globe.client.LatitudeClientState;
import com.example.globe.util.LatitudeBands;
import com.mojang.serialization.Lifecycle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.integrated.IntegratedServerLoader;
import net.minecraft.text.Text;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.rule.GameRules;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.LevelProperties;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.registry.ServerDynamicRegistryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalLong;

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
    public static void beginExpedition(MinecraftClient client, LatitudeCreateWorldScreen screen,
                                       GeneratorOptionsHolder holder,
                                       String worldName, String seed,
                                       GlobeWorldSize size, LatitudeBands.Band spawnZone,
                                       GameMode gameMode, boolean hardcore,
                                       Difficulty difficulty, boolean allowCommands,
                                       boolean startWithCompass, boolean bonusChest,
                                       GameRules gameRules, int worldTypeIdx) {
        // worldTypeIdx: 0=Latitude, 1=Vanilla, 2=Vanilla Superflat
        boolean isLatitude = worldTypeIdx == 0;
        try {
            // ── 1. Size preset resolution ──
            net.minecraft.util.Identifier presetId;
            if (worldTypeIdx == 2) {
                presetId = net.minecraft.util.Identifier.ofVanilla("flat");
            } else if (worldTypeIdx == 1) {
                presetId = net.minecraft.util.Identifier.ofVanilla("normal");
            } else {
                presetId = size.worldPresetId;
            }
            RegistryKey<WorldPreset> presetKey = RegistryKey.of(RegistryKeys.WORLD_PRESET, presetId);

            // ── 2. Create WorldCreator ──
            WorldCreator wc = new WorldCreator(
                    client.getLevelStorage().getSavesDirectory(),
                    holder,
                    Optional.of(presetKey),
                    OptionalLong.empty());

            // ── 3. Set world name ──
            wc.setWorldName(worldName);

            // ── 4. Set seed (raw — vanilla's GeneratorOptions.parseSeed() trims internally) ──
            wc.setSeed(seed);

            // Apply the preset via setWorldType to ensure dimensions are updated
            Registry<WorldPreset> presetRegistry = holder.getCombinedRegistryManager()
                    .getOrThrow(RegistryKeys.WORLD_PRESET);
            RegistryEntry<WorldPreset> presetEntry = presetRegistry.getOptional(presetKey).orElse(null);
            if (presetEntry == null) {
                LOGGER.error("Latitude world preset '{}' was not present in the loaded registry; enabled packs={}",
                        presetKey.getValue(), holder.dataConfiguration().dataPacks().getEnabled());
                client.setScreen(screen);
                return;
            }
            wc.setWorldType(new WorldCreator.WorldType(presetEntry));

            // ── 5. Apply game settings ──
            WorldCreator.Mode wcMode = hardcore ? WorldCreator.Mode.HARDCORE
                    : gameMode == GameMode.CREATIVE ? WorldCreator.Mode.CREATIVE
                    : WorldCreator.Mode.SURVIVAL;
            wc.setGameMode(wcMode);
            wc.setCheatsEnabled(allowCommands);
            wc.setDifficulty(difficulty);
            wc.setBonusChestEnabled(bonusChest);

            // ── 6. Sync structures/bonus into holder ──
            wc.update();

            // WorldCreator.update() can rebuild settings from generic defaults, so
            // reassert the selected Latitude preset before extracting the final holder.
            GeneratorOptionsHolder goh = wc.getGeneratorOptionsHolder();
            Registry<WorldPreset> updatedPresetRegistry = goh.getCombinedRegistryManager()
                    .getOrThrow(RegistryKeys.WORLD_PRESET);
            RegistryEntry<WorldPreset> updatedPresetEntry = updatedPresetRegistry.getOptional(presetKey).orElse(null);
            if (updatedPresetEntry == null) {
                LOGGER.error("Latitude world preset '{}' disappeared after WorldCreator.update(); enabled packs={}",
                        presetKey.getValue(), goh.dataConfiguration().dataPacks().getEnabled());
                client.setScreen(screen);
                return;
            }
            wc.setWorldType(new WorldCreator.WorldType(updatedPresetEntry));

            // ── 6. Extract updated holder ──
            goh = wc.getGeneratorOptionsHolder();

            // ── 7. Build level metadata (replicates CreateWorldScreen.createLevel lines 284-298) ──
            DimensionOptionsRegistryHolder.DimensionsConfig dimensionsConfig =
                    goh.selectedDimensions().toConfig(goh.dimensionOptionsRegistry());

            CombinedDynamicRegistries<ServerDynamicRegistryType> combinedDynamicRegistries =
                    goh.combinedDynamicRegistries()
                            .with(ServerDynamicRegistryType.DIMENSIONS, dimensionsConfig.toDynamicRegistryManager());

            Lifecycle lifecycle = FeatureFlags.isNotVanilla(goh.dataConfiguration().enabledFeatures())
                    ? Lifecycle.experimental() : Lifecycle.stable();
            Lifecycle lifecycle2 = combinedDynamicRegistries.getCombinedRegistryManager().getLifecycle();
            Lifecycle lifecycle3 = lifecycle2.add(lifecycle);

            LevelInfo levelInfo = new LevelInfo(
                    wc.getWorldName().trim(),
                    gameMode,
                    hardcore,
                    difficulty,
                    allowCommands,
                    gameRules,
                    goh.dataConfiguration());

            LevelProperties levelProperties = new LevelProperties(
                    levelInfo,
                    goh.generatorOptions(),
                    dimensionsConfig.specialWorldProperty(),
                    lifecycle3);

            // ── 8. Show "Preparing..." ──
            client.setScreenAndRender(new MessageScreen(Text.translatable("createWorld.preparing")));

            // ── 9. Create session ──
            LevelStorage.Session session;
            try {
                session = client.getLevelStorage().createSessionWithoutSymlinkCheck(wc.getWorldDirectoryName());
            } catch (Exception e) {
                LOGGER.error("Failed to create world session for '{}'", wc.getWorldDirectoryName(), e);
                client.setScreen(screen);
                return;
            }

            // ── 10. Write Latitude state (latest safe point — after session, before launch) ──
            if (isLatitude) {
                GlobeWorldSizeSelection.set(size);
                GlobePending.set(spawnZone.id().toUpperCase(java.util.Locale.ROOT));
                GlobePending.startWithCompass = startWithCompass;
                LatitudeClientState.latitudeWorldLoading = true;
                if (LatitudeClientConfig.get().showFirstLoadMessage) {
                    LatitudeClientState.firstWorldLoad = true;
                }
            }

            // ── 11. Launch ──
            try {
                client.createIntegratedServerLoader()
                        .startNewWorld(session, goh.dataPackContents(), combinedDynamicRegistries, levelProperties);
            } catch (Exception e) {
                LOGGER.error("Failed to start new world", e);
                // Rollback Latitude state
                GlobePending.consume();
                GlobeWorldSizeSelection.set(GlobeWorldSize.REGULAR);
                try {
                    session.close();
                } catch (Exception closeEx) {
                    LOGGER.warn("Failed to close session after launch failure", closeEx);
                }
                client.setScreen(screen);
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error in beginExpedition", e);
            // Steps 1-9 failed — no Latitude state was written
            client.setScreen(screen);
        }
    }
}
