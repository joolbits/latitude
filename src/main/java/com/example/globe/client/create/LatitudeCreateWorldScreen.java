package com.example.globe.client.create;

import com.example.globe.GlobeMod;
import com.example.globe.client.GlobeWorldSize;
import com.example.globe.client.LatitudeHudStudioScreen;
import com.example.globe.util.LatitudeBands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.DataPackReloadCookie;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContextMapper;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class LatitudeCreateWorldScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("LatitudeCreateWorldScreen");

    // ── Theme constants ──
    private static final int BG_COLOR = 0xFF2C2420;
    private static final int GOLD = 0xFFD4A74A;
    private static final int WARM_WHITE = 0xFFEDE0D0;
    private static final int MUTED = 0xFF8C8078;
    private static final int PANEL_BORDER = 0xFF5C4A3A;
    private static final int PANEL_BG = 0xFF3A302A;
    private static final int SCROLLBAR_GUTTER = 6;
    private static final int MIN_LEFT_W = 108;   // World: text fields (leftW-8) + padding
    private static final int MIN_RIGHT_W = 130;  // Spawn Zone: zone rows + description
    private static final int MIN_RAIL_W = 130;   // Rules: enough for world-type label (safeWidth = railW-66 >= 64px)
    private static final int HIGH_GUI_SCALE = 3;
    private static final int MIN_COMFORTABLE_THREE_COL_WIDTH = 720;
    private static final double[] PREVIEW_LABEL_DEGREES = {0.0, 23.5, 35.0, 50.0, 66.5, 90.0};

    private static final GlobeWorldSize DEFAULT_SIZE = GlobeWorldSize.REGULAR;
    private static final long ATLAS_SIZE_TRANSITION_MS = 180L;
    /** A legible Regular-world reference, while Large and Ginormous still grow to the full preview bounds. */
    private static final float ATLAS_REGULAR_REFERENCE_FRACTION = 0.65f;
    /** Vanilla's nine-pixel UI font plus four points, with intentional tracking between letters. */
    private static final float CREATE_TITLE_SCALE = 13.0f / 9.0f;
    private static final String CREATE_TITLE_TEXT = "L A T I T U D E";
    private static final float CREATE_VERSION_LABEL_SCALE = 0.67f;
    private static final String CREATE_VERSION_LABEL = FabricLoader.getInstance()
            .getModContainer(GlobeMod.MOD_ID)
            .map(container -> "v" + container.getMetadata().getVersion().getFriendlyString())
            .orElse("");

    // ── Band native colors (ARGB, indexed by Band.ordinal()) ──
    private static final int[] BAND_COLORS = {
            0xFF1A6B3C, // tropical
            0xFF8B7332, // subtropical
            0xFF3D6B4A, // temperate
            0xFF4A6A7D, // subpolar
            0xFF6A8599  // polar
    };

    // ── Zone helper copy (indexed by Band.ordinal()) ──
    private static final String[] ZONE_HELPER = {
            "Dense jungles, warm rivers, and bamboo groves",
            "Warm frontier climates with savannas, dry uplands, and occasional wetter edges",
            "Forests, meadows, and open plains",
            "Taiga, cold forests, and the edge of winter",
            "Ice sheets and frozen peaks"
    };

    // ── Zone climate labels (indexed by Band.ordinal()) ──
    private static final String[] ZONE_CLIMATE = {
            "Hot", "Warm", "Mild", "Cool", "Frozen"
    };

    private static final String RANDOM_ZONE_HELPER =
            "Sealed orders — your starting climate is drawn when the expedition begins";
    private static final int[] RANDOM_TEXT_COLORS = {
            0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55,
            0xFF55FF55, 0xFF55FFFF, 0xFF5555FF
    };

    // ── Size short names (indexed by GlobeWorldSize.ordinal()) ──
    private static final String[] SIZE_SHORT_NAMES = {
            "Itty Bitty", "Tiny", "Small", "Regular", "Large", "Ginormous"
    };

    // ── Size descriptions (indexed by GlobeWorldSize.ordinal()) ──
    private static final String[] SIZE_DESCRIPTIONS = {
            "A pocket world. Every horizon feels close.",
            "Compact but complete. Good for focused journeys.",
            "Room to roam. Familiar landmarks within reach.",
            "The standard world. A full planet awaits.",
            "Vast distances. Bring supplies.",
            "A world that could take a lifetime to cross."
    };

    private static final Component SMALL_WORLD_WARNING = Component.literal(
            "Smaller worlds compress the journey and may include slightly fewer total biome variants."
    ).withStyle(ChatFormatting.ITALIC, ChatFormatting.GOLD);

    // ── Game mode constants ──
    private static final String[] MODE_NAMES = { "Survival", "Hardcore", "Creative" };
    private static final int[] MODE_COLORS = { WARM_WHITE, 0xFFFF5555, 0xFFFFAA00 };

    // ── World type constants ──
    private static final String[] WORLD_TYPE_NAMES = { "Latitude", "Vanilla", "Vanilla Superflat" };
    private static final int[] WORLD_TYPE_COLORS = { GOLD, WARM_WHITE, MUTED };
    private static final int DISABLED_COLOR = 0xFF605850;
    private static final boolean DEBUG_UI_SWITCH_LAG = Boolean.getBoolean("latitude.debug.uiSwitchLag");

    private final Runnable onClose;
    @Nullable
    private final Screen parent;
    private final WorldCreationContext holder;

    // ── Local UI state (fresh each open) ──
    private GlobeWorldSize selectedSize = DEFAULT_SIZE;
    /** The outgoing display diameter while the Atlas eases to a newly selected size; unset before a user change. */
    private int atlasTransitionFromDiameter = -1;
    private long atlasTransitionStartedMs = -1L;
    private LatitudeBands.Band selectedZone = LatitudeBands.Band.TEMPERATE;
    // UI-only new-world choice. Existing saves never read or persist this flag; the launcher forwards the
    // already-supported RANDOM token, and the server resolves it to a concrete zone from the world seed.
    private boolean randomZone;
    private int selectedModeIdx = 0;  // 0=Survival, 1=Hardcore, 2=Creative
    private boolean allowCommands = false;
    private boolean startWithCompass = true;
    private boolean bonusChest = false;
    private boolean generateStructures = true;
    private int worldTypeIdx = 0;  // 0=Latitude, 1=Vanilla, 2=Vanilla Superflat
    private GameRules gameRules;

    private String worldNameInput = "New World";
    private String seedInput = "";
    private Difficulty selectedDifficulty = Difficulty.NORMAL;
    private EditBox worldNameField;
    private EditBox seedField;
    private Button sizePrevBtn;
    private Button sizeNextBtn;
    private final List<ZoneRowWidget> zoneRows = new ArrayList<>();
    // Rules controls participate in input, focus, and narration through Screen.children(), but have one
    // manual render path inside the Rules scissor so partially visible controls clip instead of popping.
    private final List<AbstractWidget> settingsScrollWidgets = new ArrayList<>();

    // ── Settings rail toggle buttons (need message updates) ──
    private Button commandsBtn;
    private Button compassBtn;
    private Button bonusChestBtn;
    private Button structuresBtn;
    private Button worldTypePrevBtn;
    private Button worldTypeNextBtn;
    private Button modePrevBtn;
    private Button modeNextBtn;
    private Button gameRulesBtn;
    private Button hudStudioBtn;

    // ── Layout cache (computed in init, used in render) ──
    private int headerY;
    private int panelTop;
    private int panelBottom;
    private int paneGap;
    private int paneStripViewportLeft;
    private int paneStripViewportRight;
    private int paneStripViewportWidth;
    private int paneStripContentWidth;
    private int paneStripScroll;
    private int paneStripScrollbarX;
    private int paneStripScrollbarY;
    private int paneStripScrollbarW;
    private int paneStripScrollbarH;
    private boolean draggingPaneStripScrollbar;
    private int leftX, leftW;
    private int rightX, rightW;
    private int railX, railW;
    private boolean threeCol;
    private int worldFieldY;
    private int seedFieldY;
    private int sizeFieldY;
    private int inputBottomY;
    private int leftScroll;
    private int leftViewportTop;
    private int leftViewportBottom;
    private int leftContentHeight;
    private int leftPreviewTopY;
    private int leftPreviewBottomY;
    private int zoneListTopY;
    private int zoneRowHeight;
    private int zoneRowStep;
    private int zoneListBottomY;
    private int rightScroll;
    private int rightViewportTop;
    private int rightViewportBottom;
    private int rightContentHeight;
    private int rightSubtitleY;
    private int rightDividerY;
    private int rightBarY;
    private int rightBarH;
    private int rightDescPanelY;
    private int rightDescPanelH;
    private int settingsScroll;
    private int settingsViewportTop;
    private int settingsViewportBottom;
    private int settingsContentHeight;
    private int menuScaleRowY;
    private int worldTypeRowY;
    private int modeRowY;
    private int commandsRowY;
    private int compassRowY;
    private int structuresRowY;
    private int bonusChestRowY;
    private int gameRulesRowY;
    private int hudStudioRowY;
    private int settingsColumnW;
    private int settingsRightColumnX;

    // ── Tabbed fallback mode (activates when 3-col doesn't fit) ──
    private boolean tabbedMode;
    private int activeTab; // 0=World + Spawn Zone, 1=Rules
    private static final String[] TAB_LABELS = {"World", "Rules"};
    private static final int TAB_H = 20;
    private static final int TAB_GAP = 4;
    private int tabStripY;
    private int tabPanelTop; // content area top (below tab strip)
    private long debugSwitchSampleDeadlineMs;
    private int debugSwitchSeq;

    private LatitudeCreateWorldScreen(Runnable onClose, @Nullable Screen parent, WorldCreationContext holder) {
        super(Component.literal("New World"));
        LOGGER.info("[LAT][CWPATH] LatitudeCreateWorldScreen.<init> parent={} holder={}",
                parent == null ? "null" : parent.getClass().getName(),
                holder);
        this.onClose = onClose;
        this.parent = parent;
        this.holder = holder;
        this.gameRules = new GameRules(holder.dataConfiguration().enabledFeatures());
    }

    private LatitudeCreateWorldScreen(Runnable onClose, @Nullable Screen parent,
                                      WorldCreationUiState initialState, boolean recreated,
                                      @Nullable String recreatedPresetId) {
        this(onClose, parent, initialState.getSettings());
        if (recreated) {
            hydrateInitialState(initialState, recreated, recreatedPresetId);
        } else {
            LOGGER.info("[LAT][CWPATH] fresh create state keeps Latitude defaults");
        }
    }

    public static boolean canRepresent(WorldCreationUiState initialState, boolean recreated,
                                       @Nullable String recreatedPresetId) {
        return !recreated
                || (effectivePresetKey(initialState, recreated, recreatedPresetId) != null
                && worldTypeIndex(initialState, recreated, recreatedPresetId) >= 0);
    }

    public static boolean canRepresent(WorldCreationUiState initialState, boolean recreated) {
        return canRepresent(initialState, recreated, null);
    }

    public static void openLoaded(Minecraft client, Runnable onClose, @Nullable Screen parent,
                                  WorldCreationUiState initialState, boolean recreated,
                                  @Nullable String recreatedPresetId) {
        LOGGER.info("[LAT][CWPATH] LatitudeCreateWorldScreen.openLoaded parent={} recreated={} stateName={} seedSet={} holder={}",
                parent == null ? "null" : parent.getClass().getName(),
                recreated,
                initialState.getName(),
                initialState.getSeed() != null && !initialState.getSeed().isBlank(),
                initialState.getSettings());
        client.setScreen(new LatitudeCreateWorldScreen(
                onClose, parent, initialState, recreated, recreatedPresetId));
    }

    public static void openLoaded(Minecraft client, Runnable onClose, @Nullable Screen parent,
                                  WorldCreationUiState initialState, boolean recreated) {
        openLoaded(client, onClose, parent, initialState, recreated, null);
    }

    private void hydrateInitialState(WorldCreationUiState initialState, boolean recreated,
                                     @Nullable String recreatedPresetId) {
        this.worldNameInput = initialState.getName();
        this.seedInput = initialState.getSeed();
        this.allowCommands = initialState.isAllowCommands();
        this.selectedDifficulty = initialState.getDifficulty();
        this.bonusChest = initialState.isBonusChest();
        this.generateStructures = initialState.isGenerateStructures();
        this.gameRules = initialState.getGameRules();
        this.selectedModeIdx = switch (initialState.getGameMode()) {
            case HARDCORE -> 1;
            case CREATIVE -> 2;
            default -> 0;
        };

        int loadedWorldType = worldTypeIndex(initialState, recreated, recreatedPresetId);
        if (loadedWorldType < 0) {
            throw new IllegalArgumentException(
                    "Unsupported Re-create world preset: "
                            + effectivePresetKey(initialState, recreated, recreatedPresetId));
        }
        this.worldTypeIdx = loadedWorldType;

        if (loadedWorldType == 0) {
            var key = effectivePresetKey(initialState, recreated, recreatedPresetId);
            for (GlobeWorldSize size : GlobeWorldSize.values()) {
                if (size.worldPresetId.equals(key.identifier())) {
                    this.selectedSize = size;
                    break;
                }
            }
        }

        LOGGER.info(
                "[LAT][CWPATH] hydrated create state name={} seedSet={} mode={} commands={} difficulty={} bonusChest={} structures={} worldType={} size={}",
                this.worldNameInput,
                this.seedInput != null && !this.seedInput.isBlank(),
                MODE_NAMES[this.selectedModeIdx],
                this.allowCommands,
                this.selectedDifficulty,
                this.bonusChest,
                this.generateStructures,
                this.worldTypeIdx,
                this.selectedSize);
    }

    @Nullable
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> presetKey(
            WorldCreationUiState initialState) {
        if (initialState == null || initialState.getWorldType() == null
                || initialState.getWorldType().preset() == null) {
            return null;
        }
        return initialState.getWorldType().preset().unwrapKey().orElse(null);
    }

    @Nullable
    private static ResourceKey<WorldPreset> effectivePresetKey(
            WorldCreationUiState initialState,
            boolean recreated,
            @Nullable String recreatedPresetId) {
        ResourceKey<WorldPreset> selectedPreset = presetKey(initialState);
        if (selectedPreset == null) {
            return null;
        }
        String effectivePresetId = RecreatedWorldTypePolicy.effectivePresetId(
                recreated,
                selectedPreset.identifier().toString(),
                recreatedPresetId,
                overworldNoiseSettingsId(initialState));
        if (effectivePresetId == null
                || effectivePresetId.equals(selectedPreset.identifier().toString())) {
            return selectedPreset;
        }
        return ResourceKey.create(Registries.WORLD_PRESET, Identifier.parse(effectivePresetId));
    }

    @Nullable
    private static String overworldNoiseSettingsId(WorldCreationUiState initialState) {
        if (initialState == null || initialState.getSettings() == null
                || !(initialState.getSettings().selectedDimensions().overworld()
                instanceof NoiseBasedChunkGenerator noise)) {
            return null;
        }
        String keyedSettingsId = noise.generatorSettings()
                .unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse(null);
        if (keyedSettingsId != null) {
            return keyedSettingsId;
        }

        return null;
    }

    private static int worldTypeIndex(WorldCreationUiState initialState, boolean recreated,
                                      @Nullable String recreatedPresetId) {
        var key = effectivePresetKey(initialState, recreated, recreatedPresetId);
        if (key == null) {
            return -1;
        }
        if (WorldPresets.NORMAL.equals(key)) {
            return 1;
        }
        if (WorldPresets.FLAT.equals(key)) {
            return 2;
        }
        for (GlobeWorldSize size : GlobeWorldSize.values()) {
            if (size.worldPresetId.equals(key.identifier())) {
                return 0;
            }
        }
        return -1;
    }

    /**
     * Phase 5A: Load datapacks (vanilla "Preparing..." screen), then open the bespoke screen.
     * Replicates CreateWorldScreen.show() lines 166-196.
     */
    public static void open(Minecraft client, Runnable onClose, @Nullable Screen parent) {
        LOGGER.info("[LAT][CWPATH] LatitudeCreateWorldScreen.open parent={}",
                parent == null ? "null" : parent.getClass().getName());
        // Show "Preparing..." message (vanilla pattern)
        client.setScreenAndShow(new GenericMessageScreen(Component.translatable("createWorld.preparing")));

        try {
            // Build datapack configuration (replicates createServerConfig, lines 511-513)
            PackRepository resourcePackManager = new PackRepository(new ServerPacksSource(client.directoryValidator()));
            resourcePackManager.reload();
            List<String> enabledPackIds = SharedConstants.IS_RUNNING_IN_IDE
                    ? List.of("vanilla", "tests", "globe")
                    : List.of("vanilla", "globe");
            resourcePackManager.setSelected(enabledPackIds);
            WorldDataConfiguration dataConfiguration = SharedConstants.IS_RUNNING_IN_IDE
                    ? new WorldDataConfiguration(new DataPackConfig(enabledPackIds, List.of()), FeatureFlags.DEFAULT_FLAGS)
                    : new WorldDataConfiguration(new DataPackConfig(enabledPackIds, List.of()), FeatureFlags.DEFAULT_FLAGS);
            WorldLoader.PackConfig dataPacks = new WorldLoader.PackConfig(resourcePackManager, dataConfiguration, false, true);
            WorldLoader.InitConfig serverConfig = new WorldLoader.InitConfig(
                    dataPacks, Commands.CommandSelection.INTEGRATED, LevelBasedPermissionSet.GAMEMASTER);

            // Generator options factory (replicates lines 131-133)
            WorldCreationContextMapper generatorOptionsFactory = (dataPackContents, dynamicRegistries, settings) ->
                    new WorldCreationContext(settings.worldGenSettings(), dynamicRegistries, dataPackContents, settings.dataConfiguration());

            // Load datapacks asynchronously so the UI stays responsive while the
            // preparing screen is visible.
            CompletableFuture<WorldCreationContext> future = WorldLoader.load(
                    serverConfig,
                    context -> new WorldLoader.DataLoadOutput<>(
                            new DataPackReloadCookie(
                                    new WorldGenSettings(WorldOptions.defaultWithRandomSeed(), WorldPresets.createNormalWorldDimensions(context.datapackWorldgen())),
                                    context.dataConfiguration()),
                            context.datapackDimensions()),
                    (resourceManager, dataPackContents, dynamicRegistries, settings) -> {
                        resourceManager.close();
                        return generatorOptionsFactory.apply(dataPackContents, dynamicRegistries, settings);
                    },
                    Util.backgroundExecutor(),
                    client);

            future.whenComplete((loadedHolder, throwable) -> {
                client.execute(() -> {
                    if (throwable != null) {
                        LOGGER.error("Failed to load datapacks for Latitude create-world screen", throwable);
                        onClose.run();
                        if (client.screen == null || client.screen instanceof GenericMessageScreen) {
                            client.setScreen(parent);
                        }
                        return;
                    }

                    // Open the bespoke screen with the loaded holder.
                    client.setScreen(new LatitudeCreateWorldScreen(onClose, parent, loadedHolder));
                });
            });
        } catch (Exception e) {
            LOGGER.error("Failed to load datapacks for Latitude create-world screen", e);
            // 5A error path: return to caller screen, never show bespoke screen
            onClose.run();
            if (client.screen == null || client.screen instanceof GenericMessageScreen) {
                client.setScreen(parent);
            }
        }
    }

    private boolean isCompact() {
        return this.width < 480;
    }

    private static boolean shouldUseTabbedLayout(int viewportWidth, int guiScale) {
        return guiScale >= HIGH_GUI_SCALE || viewportWidth < MIN_COMFORTABLE_THREE_COL_WIDTH;
    }

    @Override
    protected void init() {
        LOGGER.info("[LAT][CWPATH] LatitudeCreateWorldScreen.init screen={} holder={}",
                this.getClass().getName(), this.holder);
        zoneRows.clear();
        // Screen.rebuildWidgets() clears Screen-owned collections, not this private render registry.
        // Clear it on every init so resize/sub-screen return cannot leave a frozen ghost layer.
        settingsScrollWidgets.clear();
        int headerGap = scaledUi(10);
        int headerToPanel = scaledUi(42);
        int bottomMargin = scaledUi(40);
        int btnBottomOffset = scaledUi(30);
        int fieldGap1 = scaledUi(38);
        int fieldGap2 = scaledUi(40);
        int labelFieldGap = scaledUi(22);
        int fieldH = Math.max(16, scaledUi(16));
        int btnH = Math.max(18, scaledUi(20));
        int stepperBtnW = 20;

        headerY = headerGap;
        int bottomY = this.height - btnBottomOffset;
        panelTop = headerY + headerToPanel;
        panelBottom = this.height - bottomMargin;
        int cx = this.width / 2;
        paneGap = scaledUi(8);
        paneStripViewportLeft = 12;
        paneStripViewportRight = Math.max(paneStripViewportLeft + 1, this.width - 12);
        paneStripViewportWidth = Math.max(1, paneStripViewportRight - paneStripViewportLeft);
        paneStripContentWidth = paneStripViewportWidth;
        paneStripScrollbarX = paneStripViewportLeft;
        paneStripScrollbarW = paneStripViewportWidth;
        paneStripScrollbarY = panelBottom + 2;
        paneStripScrollbarH = Math.max(4, Math.min(Math.max(4, scaledUi(6)), Math.max(4, bottomY - paneStripScrollbarY - 2)));
        int guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        // The hard panel minima only prevent outright overlap; they still produce the crammed three-column
        // layout visible at high GUI scale. High scale therefore always gets one pane per tab, while normal
        // scales keep three columns only when each pane has genuinely comfortable space.
        tabbedMode = shouldUseTabbedLayout(paneStripViewportWidth, guiScale);
        threeCol = !tabbedMode;
        if (tabbedMode) {
            tabStripY = panelTop;
            tabPanelTop = tabStripY + TAB_H + TAB_GAP;
            panelTop = tabPanelTop;
            int combinedWorldWidth = Math.max(2, paneStripContentWidth - paneGap);
            leftW = Math.max(1, Math.round(combinedWorldWidth * 0.58f));
            rightW = Math.max(1, combinedWorldWidth - leftW);
            railW = paneStripContentWidth;
            if (activeTab < 0 || activeTab >= TAB_LABELS.length) {
                activeTab = 0;
            }
        } else {
            tabStripY = 0;
            tabPanelTop = panelTop;
            // Rail gets its minimum first; left/right split remainder at original 32:42 ratio.
            railW = Math.max(MIN_RAIL_W, (int) (paneStripContentWidth * 0.26f));
            int rem = paneStripContentWidth - railW - paneGap * 2;
            leftW = Math.max(MIN_LEFT_W, (int) (rem * 0.432f)); // 32/(32+42)
            rightW = Math.max(MIN_RIGHT_W, rem - leftW);
        }
        int maxPaneStripScroll = getPaneStripMaxScroll();
        if (paneStripScroll < 0) paneStripScroll = 0;
        if (paneStripScroll > maxPaneStripScroll) paneStripScroll = maxPaneStripScroll;
        updatePaneStripLayout();

        // Input field area within left panel
        int inputX = leftX + 4;
        int inputW = leftW - 8;

        // ═══════════════════════════════════════════════
        // Frozen tab order — widgets added in exact sequence:
        // 1. World Name  2. Seed  3. Size ◀  4. Size ▶
        // 5–9. Zone rows (Tropical → Polar)
        // 10–18. Settings rail
        // 17. Begin Expedition  18. Cancel
        // ═══════════════════════════════════════════════

        // ── 1. World Name ──
        worldFieldY = panelTop + labelFieldGap;
        this.worldNameField = new EditBox(this.font, inputX, worldFieldY, inputW, fieldH, Component.literal("World Name"));
        this.worldNameField.setMaxLength(64);
        this.worldNameField.setValue(worldNameInput);
        this.worldNameField.setResponder(text -> worldNameInput = text);
        this.addRenderableWidget(this.worldNameField);

        // ── 2. Seed ──
        seedFieldY = worldFieldY + fieldGap1;
        this.seedField = new EditBox(this.font, inputX, seedFieldY, inputW, fieldH, Component.literal("Seed"));
        this.seedField.setMaxLength(64);
        this.seedField.setHint(Component.literal("Leave blank for random"));
        this.seedField.setValue(seedInput == null ? "" : seedInput);
        this.seedField.setResponder(text -> seedInput = text);
        this.addRenderableWidget(this.seedField);

        // ── 3. Size ◀ ──
        sizeFieldY = seedFieldY + fieldGap2;
        sizePrevBtn = Button.builder(Component.literal("\u25C0"), b -> cycleSize(-1))
                .bounds(inputX, sizeFieldY, stepperBtnW, btnH)
                .build();
        this.addRenderableWidget(sizePrevBtn);

        // ── 4. Size ▶ ──
        sizeNextBtn = Button.builder(Component.literal("\u25B6"), b -> cycleSize(1))
                .bounds(inputX + inputW - stepperBtnW, sizeFieldY, stepperBtnW, btnH)
                .build();
        this.addRenderableWidget(sizeNextBtn);
        updateLeftWidgets(inputX, inputW, fieldH, btnH, stepperBtnW);

        inputBottomY = Math.max(sizeFieldY + btnH, computeSizeLabelBottom(sizeFieldY - 1, inputW - 48)) + scaledUi(12);
        updateLeftLayout();

        zoneRowHeight = computeZoneRowHeight(rightW - 4);
        zoneRowStep = zoneRowHeight + scaledUi(6);
        for (LatitudeBands.Band band : LatitudeBands.Band.values()) {
            ZoneRowWidget row = new ZoneRowWidget(rightX + 2, panelTop, rightW - 4, zoneRowHeight, band);
            zoneRows.add(row);
            this.addRenderableWidget(row);
        }
        // Keep Random after Polar, matching Latitude 2.0. A null band denotes the UI-only random choice.
        ZoneRowWidget randomRow = new ZoneRowWidget(rightX + 2, panelTop, rightW - 4, zoneRowHeight, null);
        zoneRows.add(randomRow);
        this.addRenderableWidget(randomRow);
        updateRightLayout();

        {
            int settBtnW = railW - 8;
            int settBtnX = railX + 4;

            worldTypePrevBtn = Button.builder(Component.literal("\u25C0"), b -> cycleWorldType(-1))
                    .bounds(settBtnX, panelTop, 20, btnH)
                    .build();
            worldTypeNextBtn = Button.builder(Component.literal("\u25B6"), b -> cycleWorldType(1))
                    .bounds(settBtnX + settBtnW - 20, panelTop, 20, btnH)
                    .build();
            modePrevBtn = Button.builder(Component.literal("\u25C0"), b -> cycleMode(-1))
                    .bounds(settBtnX, panelTop, 20, btnH)
                    .build();
            modeNextBtn = Button.builder(Component.literal("\u25B6"), b -> cycleMode(1))
                    .bounds(settBtnX + settBtnW - 20, panelTop, 20, btnH)
                    .build();
            commandsBtn = Button.builder(Component.literal(allowCommands ? "ON" : "OFF"), b -> {
                allowCommands = !allowCommands;
                b.setMessage(Component.literal(allowCommands ? "ON" : "OFF"));
            }).bounds(settBtnX, panelTop, settBtnW, btnH).build();
            addSettingsScrollWidget(worldTypePrevBtn);
            addSettingsScrollWidget(worldTypeNextBtn);
            addSettingsScrollWidget(modePrevBtn);
            addSettingsScrollWidget(modeNextBtn);
            addSettingsScrollWidget(commandsBtn);

            compassBtn = Button.builder(Component.literal(startWithCompass ? "ON" : "OFF"), b -> {
                startWithCompass = !startWithCompass;
                b.setMessage(Component.literal(startWithCompass ? "ON" : "OFF"));
            }).bounds(settBtnX, panelTop, settBtnW, btnH).build();
            addSettingsScrollWidget(compassBtn);

            structuresBtn = Button.builder(Component.literal(generateStructures ? "ON" : "OFF"), b -> {
                generateStructures = !generateStructures;
                b.setMessage(Component.literal(generateStructures ? "ON" : "OFF"));
            }).bounds(settBtnX, panelTop, settBtnW, btnH).build();
            addSettingsScrollWidget(structuresBtn);

            bonusChestBtn = Button.builder(Component.literal(bonusChest ? "ON" : "OFF"), b -> {
                bonusChest = !bonusChest;
                b.setMessage(Component.literal(bonusChest ? "ON" : "OFF"));
            }).bounds(settBtnX, panelTop, settBtnW, btnH).build();
            addSettingsScrollWidget(bonusChestBtn);

            gameRulesBtn = Button.builder(Component.literal("Game Rules..."), b -> openGameRules())
                    .bounds(settBtnX, panelTop, settBtnW, btnH)
                    .build();
            addSettingsScrollWidget(gameRulesBtn);

            hudStudioBtn = Button.builder(Component.literal("HUD Studio"), b -> openHudStudio())
                    .bounds(settBtnX, panelTop, settBtnW, btnH)
                    .build();
            addSettingsScrollWidget(hudStudioBtn);
            updateSettingsLayout();
        }

        if (tabbedMode) {
            applyTabbedVisibility();
        }

        // ── 17. Create World ──
        int btnSpacing = scaledUi(8);
        int beginW = Math.max(120, this.font.width("Create World") + 20);
        int cancelW = Math.max(70, this.font.width("Cancel") + 20);
        int totalBtnW = beginW + btnSpacing + cancelW;
        int btnStartX = cx - totalBtnW / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Create World"), b -> beginExpedition())
                .bounds(btnStartX, bottomY, beginW, btnH)
                .build());

        // ── 18. Cancel ──
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(btnStartX + beginW + btnSpacing, bottomY, cancelW, btnH)
                .build());

        // ── Focus: pre-select world name only when the World surface is actually visible. ──
        if (!tabbedMode || activeTab == 0) {
            this.worldNameField.setFocused(true);
            this.setFocused(this.worldNameField);
            this.worldNameField.moveCursorToEnd(false);
            this.worldNameField.setHighlightPos(0);
        } else {
            this.worldNameField.setFocused(false);
            this.setFocused(null);
        }
    }

    // ── Size stepper ──

    private void cycleSize(int delta) {
        GlobeWorldSize[] sizes = GlobeWorldSize.values();
        int idx = selectedSize.ordinal() + delta;
        if (idx < 0) idx = sizes.length - 1;
        if (idx >= sizes.length) idx = 0;
        long now = Util.getMillis();
        atlasTransitionFromDiameter = animatedAtlasDiameter(now);
        atlasTransitionStartedMs = now;
        selectedSize = sizes[idx];
        if (this.worldNameField != null) {
            worldNameInput = this.worldNameField.getValue();
        }
        this.rebuildWidgets();
    }

    // ── Mode stepper ──

    private void cycleMode(int delta) {
        selectedModeIdx = (selectedModeIdx + delta + MODE_NAMES.length) % MODE_NAMES.length;
        if (selectedModeIdx == 2 && !allowCommands) {
            allowCommands = true;
            updateSettingsButtons();
        }
    }

    private void cycleWorldType(int delta) {
        long t0 = Util.getMillis();
        worldTypeIdx = (worldTypeIdx + delta + WORLD_TYPE_NAMES.length) % WORLD_TYPE_NAMES.length;
        updateSettingsButtons();
        updateRightLayout();
        if (DEBUG_UI_SWITCH_LAG) {
            debugSwitchSeq++;
            debugSwitchSampleDeadlineMs = t0 + 2_000L; // sample for 2s after switch
            long elapsed = Util.getMillis() - t0;
            LOGGER.info("[lat-ui] switchLag seq={} worldType={} handler_ms={}", debugSwitchSeq, currentWorldTypeName(), elapsed);
        }
    }

    private boolean isLatitudeWorld() {
        return worldTypeIdx == 0;
    }

    private int scaledUi(int px) {
        return px;
    }

    private int compactUi(int px) {
        return scaledUi(px);
    }

    private int uiFontHeight() {
        return this.font.lineHeight;
    }

    private int uiTextWidth(String text) {
        return this.font.width(text);
    }

    private List<net.minecraft.network.chat.FormattedText> wrapUiLines(String text, int width) {
        return this.font.getSplitter().splitLines(text, Math.max(1, width), net.minecraft.network.chat.Style.EMPTY);
    }

    private int wrapLineCount(String text, int width) {
        return Math.max(1, wrapUiLines(text, width).size());
    }

    private int wrappedTextHeight(String text, int width) {
        return wrapLineCount(text, width) * uiFontHeight();
    }

    private int computeSizeLabelBottom(int y, int availW) {
        return y + scaledUi(22) + wrapLineCount(SIZE_DESCRIPTIONS[selectedSize.ordinal()], Math.max(40, availW)) * uiFontHeight();
    }

    private int getSmallWorldWarningHeight(int width) {
        return wrapUiLines(SMALL_WORLD_WARNING.getString(), Math.max(40, Math.round(width / smallWorldWarningScale())))
                .size() * smallWorldWarningLineHeight();
    }

    private float smallWorldWarningScale() {
        return Math.max(0.5f, (uiFontHeight() - 2.0f) / uiFontHeight());
    }

    private int smallWorldWarningLineHeight() {
        return Math.max(1, Math.round(uiFontHeight() * smallWorldWarningScale()));
    }

    private int computeZoneListTop() {
        return panelTop + scaledUi(22) + wrappedTextHeight("Choose the climate where your journey begins", Math.max(80, rightW - scaledUi(20) - SCROLLBAR_GUTTER)) + scaledUi(10);
    }

    private int computeZoneRowHeight(int rowWidth) {
        int maxRangeW = 0;
        for (LatitudeBands.Band band : LatitudeBands.Band.values()) {
            String range = formatDegree(band.lowDeg()) + "–" + formatDegree(band.highDeg());
            maxRangeW = Math.max(maxRangeW, this.font.width(range));
        }
        int helperWidth = Math.max(60, rowWidth - 12 - maxRangeW - 10);
        int maxHelperLines = 1;
        for (String helper : ZONE_HELPER) {
            maxHelperLines = Math.max(maxHelperLines, wrapLineCount(helper, helperWidth));
        }
        maxHelperLines = Math.max(maxHelperLines, wrapLineCount(RANDOM_ZONE_HELPER, helperWidth));
        return scaledUi(4) + uiFontHeight() + scaledUi(2) + maxHelperLines * uiFontHeight() + scaledUi(4);
    }

    private int getPaneStripMaxScroll() {
        return Math.max(0, paneStripContentWidth - paneStripViewportWidth);
    }

    private void updatePaneStripLayout() {
        if (tabbedMode) {
            leftX = paneStripViewportLeft;
            rightX = leftX + leftW + paneGap;
            railX = paneStripViewportLeft;
            return;
        }
        int baseLeft = paneStripViewportLeft + Math.max(0, (paneStripViewportWidth - paneStripContentWidth) / 2);
        leftX = baseLeft - paneStripScroll;
        rightX = leftX + leftW + paneGap;
        railX = rightX + rightW + paneGap;
    }

    private void applyPaneStripScroll(int nextScroll) {
        int maxScroll = getPaneStripMaxScroll();
        if (nextScroll < 0) nextScroll = 0;
        if (nextScroll > maxScroll) nextScroll = maxScroll;
        if (nextScroll == paneStripScroll) return;
        paneStripScroll = nextScroll;
        updatePaneStripLayout();
        updateLeftLayout();
        updateRightLayout();
        updateSettingsLayout();
    }

    private void setPaneStripScrollFromMouse(double mouseX) {
        int maxScroll = getPaneStripMaxScroll();
        if (maxScroll <= 0) return;
        int thumbW = Math.max(24, paneStripScrollbarW * paneStripViewportWidth / Math.max(1, paneStripContentWidth));
        if (thumbW > paneStripScrollbarW) thumbW = paneStripScrollbarW;
        int trackRange = Math.max(1, paneStripScrollbarW - thumbW);
        int thumbLeft = (int) Math.round(mouseX) - paneStripScrollbarX - thumbW / 2;
        if (thumbLeft < 0) thumbLeft = 0;
        if (thumbLeft > trackRange) thumbLeft = trackRange;
        applyPaneStripScroll(thumbLeft * maxScroll / trackRange);
    }

    private void updateLeftWidgets(int inputX, int inputW, int fieldH, int btnH, int stepperBtnW) {
        if (worldNameField != null) {
            worldNameField.setRectangle(inputW, fieldH, inputX, worldFieldY);
            worldNameField.visible = true;
            worldNameField.active = true;
        }
        if (seedField != null) {
            seedField.setRectangle(inputW, fieldH, inputX, seedFieldY);
            seedField.visible = true;
            seedField.active = true;
        }
        if (sizePrevBtn != null) {
            sizePrevBtn.setRectangle(stepperBtnW, btnH, inputX, sizeFieldY);
            sizePrevBtn.visible = true;
            sizePrevBtn.active = true;
        }
        if (sizeNextBtn != null) {
            sizeNextBtn.setRectangle(stepperBtnW, btnH, inputX + inputW - stepperBtnW, sizeFieldY);
            sizeNextBtn.visible = true;
            sizeNextBtn.active = true;
        }
    }

    private void updateLeftLayout() {
        int inputX = leftX + 4;
        int inputW = leftW - 8 - SCROLLBAR_GUTTER;
        int fieldH = worldNameField != null ? worldNameField.getHeight() : Math.max(16, scaledUi(16));
        int btnH = sizePrevBtn != null ? sizePrevBtn.getHeight() : Math.max(18, scaledUi(20));
        int stepperBtnW = sizePrevBtn != null ? sizePrevBtn.getWidth() : 20;
        int contentTop = panelTop - scaledUi(4);
        int labelFieldGap = scaledUi(22);
        int fieldGap1 = scaledUi(38);
        int fieldGap2 = scaledUi(40);
        int discGap = scaledUi(6);
        int previewHeight = Math.max(scaledUi(150), Math.min(leftW - scaledUi(20) - SCROLLBAR_GUTTER, Math.max(scaledUi(170), panelBottom - panelTop - scaledUi(80))));
        int baseWorldY = contentTop + labelFieldGap;
        int baseSeedY = baseWorldY + fieldGap1;
        int baseSizeY = baseSeedY + fieldGap2;
        int baseInputBottom = Math.max(baseSizeY + btnH, computeSizeLabelBottom(baseSizeY - 1, inputW - stepperBtnW * 2 - scaledUi(8))) + scaledUi(12);
        // The compact-world note occupies the preview's existing breathing room. It never changes the Atlas anchor.
        int basePreviewTop = baseInputBottom + discGap;
        int basePreviewBottom = basePreviewTop + previewHeight;
        leftViewportTop = panelTop + 4;
        leftViewportBottom = panelBottom - 4;
        int viewportHeight = Math.max(0, leftViewportBottom - leftViewportTop);
        leftContentHeight = basePreviewBottom - contentTop + scaledUi(8);
        int maxScroll = Math.max(0, leftContentHeight - viewportHeight);
        if (leftScroll < 0) leftScroll = 0;
        if (leftScroll > maxScroll) leftScroll = maxScroll;

        worldFieldY = baseWorldY - leftScroll;
        seedFieldY = baseSeedY - leftScroll;
        sizeFieldY = baseSizeY - leftScroll;
        inputBottomY = baseInputBottom - leftScroll;
        leftPreviewTopY = basePreviewTop - leftScroll;
        leftPreviewBottomY = basePreviewBottom - leftScroll;

        updateLeftWidgets(inputX, inputW, fieldH, btnH, stepperBtnW);
        updateLeftWidgetVisibility(worldNameField);
        updateLeftWidgetVisibility(seedField);
        updateLeftWidgetVisibility(sizePrevBtn);
        updateLeftWidgetVisibility(sizeNextBtn);
    }

    private void updateLeftWidgetVisibility(AbstractWidget widget) {
        if (widget == null) return;
        boolean visible = (!tabbedMode || activeTab == 0)
                && widget.getX() >= paneStripViewportLeft
                && widget.getX() + widget.getWidth() <= paneStripViewportRight
                && widget.getY() >= leftViewportTop
                && widget.getY() + widget.getHeight() <= leftViewportBottom;
        widget.visible = visible;
        widget.active = visible;
    }

    private String spawnZoneDescription() {
        if (randomZone) {
            return "Your starting climate will be drawn from anywhere between the Equator and the Poles when the expedition begins.";
        }
        return "You will spawn between " + formatDegree(selectedZone.lowDeg()) + "–" + formatDegree(selectedZone.highDeg())
                + " latitude. " + ZONE_HELPER[selectedZone.ordinal()] + ".";
    }

    private void updateRightLayout() {
        int contentTop = panelTop + scaledUi(8);
        // Wide mode reserves a fixed heading strip. In tabbed mode the tab itself is the heading.
        int titleBlockHeight = threeCol ? (uiFontHeight() + scaledUi(4)) : 0;
        int subtitleWidth = Math.max(80, rightW - scaledUi(28) - SCROLLBAR_GUTTER);
        int subtitleHeight = wrappedTextHeight("Choose the climate where your journey begins", subtitleWidth);
        int descTextWidth = Math.max(60, rightW - 16 - SCROLLBAR_GUTTER);
        String spawnLine = spawnZoneDescription();
        int descHeight = scaledUi(6) + uiFontHeight() + scaledUi(5) + wrappedTextHeight(spawnLine, descTextWidth) + scaledUi(4) + uiFontHeight() + scaledUi(6);
        int baseSubtitleY = contentTop + titleBlockHeight;
        int baseDividerY = baseSubtitleY + subtitleHeight + scaledUi(2);
        int baseZoneListTop = baseDividerY + scaledUi(8);
        int baseZoneListBottom = baseZoneListTop + zoneRows.size() * zoneRowStep - scaledUi(6);
        int baseBarY = baseZoneListBottom + scaledUi(8);
        int baseBarH = Math.max(4, scaledUi(6));
        int baseDescY = baseBarY + baseBarH + scaledUi(12);
        rightViewportTop = panelTop + 4;
        rightViewportBottom = panelBottom - 4;
        int viewportHeight = Math.max(0, rightViewportBottom - rightViewportTop);
        rightContentHeight = baseDescY + descHeight - contentTop + scaledUi(8);
        int maxScroll = Math.max(0, rightContentHeight - viewportHeight);
        if (rightScroll < 0) rightScroll = 0;
        if (rightScroll > maxScroll) rightScroll = maxScroll;

        rightSubtitleY = baseSubtitleY - rightScroll;
        rightDividerY = baseDividerY - rightScroll;
        zoneListTopY = baseZoneListTop - rightScroll;
        zoneListBottomY = baseZoneListBottom - rightScroll;
        rightBarY = baseBarY - rightScroll;
        rightBarH = baseBarH;
        rightDescPanelY = baseDescY - rightScroll;
        rightDescPanelH = descHeight;

        int zoneY = zoneListTopY;
        for (ZoneRowWidget row : zoneRows) {
            row.setRectangle(rightW - 4 - SCROLLBAR_GUTTER, zoneRowHeight, rightX + 2, zoneY);
            boolean visible = isLatitudeWorld()
                    && (!tabbedMode || activeTab == 0)
                    && row.getX() >= paneStripViewportLeft
                    && row.getX() + row.getWidth() <= paneStripViewportRight
                    && intersectsClip(zoneY, zoneY + zoneRowHeight, spawnClipTop(), rightViewportBottom);
            // Any rendered portion is selectable; the screen-level clip gate consumes clicks against
            // the off-page portion of the widget rectangle. Fully hidden rows remain inactive.
            applyScrollWidgetState(row, visible, visible);
            zoneY += zoneRowStep;
        }
    }

    private void updateSettingsButtons() {
        if (commandsBtn != null) {
            commandsBtn.setMessage(Component.literal(allowCommands ? "ON" : "OFF"));
            commandsBtn.active = commandsBtn.visible;
        }
        if (compassBtn != null) {
            compassBtn.setMessage(Component.literal(startWithCompass ? "ON" : "OFF"));
            compassBtn.active = compassBtn.visible && isLatitudeWorld();
        }
        if (structuresBtn != null) {
            structuresBtn.setMessage(Component.literal(generateStructures ? "ON" : "OFF"));
            structuresBtn.active = structuresBtn.visible;
        }
        if (bonusChestBtn != null) {
            bonusChestBtn.setMessage(Component.literal(bonusChest ? "ON" : "OFF"));
            bonusChestBtn.active = bonusChestBtn.visible;
        }
        if (gameRulesBtn != null) {
            gameRulesBtn.active = gameRulesBtn.visible;
        }
        if (hudStudioBtn != null) {
            hudStudioBtn.active = hudStudioBtn.visible;
        }
    }

    private void updateSettingsLayout() {
        if (worldTypePrevBtn == null || worldTypeNextBtn == null || modePrevBtn == null || modeNextBtn == null || commandsBtn == null || compassBtn == null || structuresBtn == null || bonusChestBtn == null || gameRulesBtn == null || hudStudioBtn == null) {
            settingsViewportTop = 0;
            settingsViewportBottom = 0;
            settingsContentHeight = 0;
            return;
        }

        int settBtnW = railW - 8;
        int settBtnX = railX + 4;
        int btnH = worldTypePrevBtn.getHeight();
        int labelGap = scaledUi(10);
        int rowGap = scaledUi(10);
        int columnGap = scaledUi(8);
        settingsColumnW = settBtnW;
        settingsRightColumnX = settBtnX;
        if (tabbedMode) {
            settingsColumnW = Math.max(1, (settBtnW - columnGap) / 2);
            settingsRightColumnX = settBtnX + settingsColumnW + columnGap;
        }
        // Wide mode reserves the fixed Rules heading. Tabbed mode already has a tab label, so there is
        // no internal heading or blank opaque shelf.
        settingsViewportTop = threeCol ? panelTop + scaledUi(36) : panelTop + scaledUi(8);
        settingsViewportBottom = panelBottom - scaledUi(8);
        int viewportHeight = Math.max(0, settingsViewportBottom - settingsViewportTop);
        int contentTop = settingsViewportTop + scaledUi(4);
        int blockHeight = labelGap + btnH;
        int rulesRowCount = tabbedMode ? 4 : 8;
        // Leave a little trailing room so the HUD Studio row can scroll fully into view
        // on short windows instead of sitting flush against the viewport edge.
        settingsContentHeight = blockHeight * rulesRowCount + rowGap * (rulesRowCount - 1) + scaledUi(12);
        int maxScroll = Math.max(0, settingsContentHeight - viewportHeight);
        if (settingsScroll < 0) settingsScroll = 0;
        if (settingsScroll > maxScroll) settingsScroll = maxScroll;

        int y = contentTop - settingsScroll + labelGap;
        worldTypeRowY = y;
        if (tabbedMode) {
            modeRowY = y;
            positionSettingsStepper(worldTypePrevBtn, worldTypeNextBtn, settBtnX, settingsColumnW, y, btnH);
            positionSettingsStepper(modePrevBtn, modeNextBtn, settingsRightColumnX, settingsColumnW, y, btnH);

            y += btnH + rowGap + labelGap;
            commandsRowY = y;
            compassRowY = y;
            positionSettingsButton(commandsBtn, settBtnX, settingsColumnW, y, btnH);
            positionSettingsButton(compassBtn, settingsRightColumnX, settingsColumnW, y, btnH);

            y += btnH + rowGap + labelGap;
            structuresRowY = y;
            bonusChestRowY = y;
            positionSettingsButton(structuresBtn, settBtnX, settingsColumnW, y, btnH);
            positionSettingsButton(bonusChestBtn, settingsRightColumnX, settingsColumnW, y, btnH);

            y += btnH + rowGap + labelGap;
            gameRulesRowY = y;
            hudStudioRowY = y;
            positionSettingsButton(gameRulesBtn, settBtnX, settingsColumnW, y, btnH);
            positionSettingsButton(hudStudioBtn, settingsRightColumnX, settingsColumnW, y, btnH);
        } else {
            positionSettingsStepper(worldTypePrevBtn, worldTypeNextBtn, settBtnX, settBtnW, y, btnH);

            y += btnH + rowGap + labelGap;
            modeRowY = y;
            positionSettingsStepper(modePrevBtn, modeNextBtn, settBtnX, settBtnW, y, btnH);

            y += btnH + rowGap + labelGap;
            commandsRowY = y;
            positionSettingsButton(commandsBtn, settBtnX, settBtnW, y, btnH);

            y += btnH + rowGap + labelGap;
            compassRowY = y;
            positionSettingsButton(compassBtn, settBtnX, settBtnW, y, btnH);

            y += btnH + rowGap + labelGap;
            structuresRowY = y;
            positionSettingsButton(structuresBtn, settBtnX, settBtnW, y, btnH);

            y += btnH + rowGap + labelGap;
            bonusChestRowY = y;
            positionSettingsButton(bonusChestBtn, settBtnX, settBtnW, y, btnH);

            y += btnH + rowGap + labelGap;
            gameRulesRowY = y;
            positionSettingsButton(gameRulesBtn, settBtnX, settBtnW, y, btnH);

            y += btnH + rowGap + labelGap;
            hudStudioRowY = y;
            positionSettingsButton(hudStudioBtn, settBtnX, settBtnW, y, btnH);
        }

        updateSettingsButtons();
    }

    private void applyTabbedVisibility() {
        if (!tabbedMode) return;
        // Tab 0 = World + Spawn Zone.
        boolean showWorld = activeTab == 0;
        setTabbedWidgetVisible(worldNameField, showWorld);
        setTabbedWidgetVisible(seedField, showWorld);
        setTabbedWidgetVisible(sizePrevBtn, showWorld);
        setTabbedWidgetVisible(sizeNextBtn, showWorld);
        boolean showZone = activeTab == 0 && isLatitudeWorld();
        for (ZoneRowWidget row : zoneRows) {
            setTabbedWidgetVisible(row, showZone);
        }
        // Tab 1 = Rules.
        boolean showRules = activeTab == 1;
        setTabbedWidgetVisible(worldTypePrevBtn, showRules);
        setTabbedWidgetVisible(worldTypeNextBtn, showRules);
        setTabbedWidgetVisible(modePrevBtn, showRules);
        setTabbedWidgetVisible(modeNextBtn, showRules);
        setTabbedWidgetVisible(commandsBtn, showRules);
        setTabbedWidgetVisible(compassBtn, showRules);
        setTabbedWidgetVisible(structuresBtn, showRules);
        setTabbedWidgetVisible(bonusChestBtn, showRules);
        setTabbedWidgetVisible(gameRulesBtn, showRules);
        setTabbedWidgetVisible(hudStudioBtn, showRules);
    }

    private void setTabbedWidgetVisible(AbstractWidget widget, boolean visible) {
        if (widget == null) return;
        widget.visible = visible;
        widget.active = visible;
        if (!visible && widget.isFocused()) {
            widget.setFocused(false);
            if (this.getFocused() == widget) {
                this.setFocused(null);
            }
        }
    }

    private int headerBandBottom() {
        return panelTop + scaledUi(6) + uiFontHeight() + scaledUi(3);
    }

    private int settingsClipTop() {
        return threeCol ? headerBandBottom() : settingsViewportTop;
    }

    private int spawnClipTop() {
        return threeCol ? headerBandBottom() : rightViewportTop;
    }

    private static boolean intersectsClip(int top, int bottom, int clipTop, int clipBottom) {
        return ViewportClipPolicy.intersects(top, bottom, clipTop, clipBottom);
    }

    private static boolean pointInsideClip(double x, double y, int left, int top, int right, int bottom) {
        return ViewportClipPolicy.containsPoint(x, y, left, top, right, bottom);
    }

    private boolean isInsideRulesPanel(double x, double y) {
        if (tabbedMode && activeTab != 1) return false;
        int left = Math.max(railX + 1, paneStripViewportLeft);
        int right = Math.min(railX + railW - 1, paneStripViewportRight);
        return pointInsideClip(x, y, left, panelTop, right, panelBottom);
    }

    private boolean isInsideRulesClip(double x, double y) {
        int left = Math.max(railX + 1, paneStripViewportLeft);
        int right = Math.min(railX + railW - 1, paneStripViewportRight);
        return pointInsideClip(x, y, left, settingsClipTop(), right, settingsViewportBottom);
    }

    private boolean isInsideSpawnPanel(double x, double y) {
        if (tabbedMode && activeTab != 0) return false;
        int left = Math.max(rightX + 1, paneStripViewportLeft);
        int right = Math.min(rightX + rightW - 1, paneStripViewportRight);
        return pointInsideClip(x, y, left, panelTop, right, panelBottom);
    }

    private boolean isInsideSpawnClip(double x, double y) {
        int left = Math.max(rightX + 1, paneStripViewportLeft);
        int right = Math.min(rightX + rightW - 1, paneStripViewportRight);
        return pointInsideClip(x, y, left, spawnClipTop(), right, rightViewportBottom);
    }

    private boolean handleSpawnZoneClippedClick(MouseButtonEvent click, boolean doubled) {
        if (!isLatitudeWorld() || (tabbedMode && activeTab != 0)) {
            return false;
        }
        int clipLeft = Math.max(rightX + 1, paneStripViewportLeft);
        int clipRight = Math.min(rightX + rightW - 1, paneStripViewportRight);
        int clipTop = spawnClipTop();
        int clipBottom = rightViewportBottom;
        for (ZoneRowWidget row : zoneRows) {
            if (!row.visible || !row.active) {
                continue;
            }
            if (ViewportClipPolicy.acceptsClippedWidgetClick(
                    click.x(),
                    click.y(),
                    row.getX(),
                    row.getY(),
                    row.getX() + row.getWidth(),
                    row.getY() + row.getHeight(),
                    clipLeft,
                    clipTop,
                    clipRight,
                    clipBottom
            )) {
                row.selectFromClippedMouseClick(click, doubled);
                return true;
            }
        }
        return false;
    }

    private void switchTab(int tab) {
        if (tab == activeTab) return;
        activeTab = tab;
        applyTabbedVisibility();
    }

    private void positionSettingsStepper(Button left, Button right, int x, int width, int y, int height) {
        int stepperW = left.getWidth();
        left.setRectangle(stepperW, height, x, y);
        right.setRectangle(stepperW, height, x + width - stepperW, y);
        boolean visible = (!tabbedMode || activeTab == 1)
                && left.getX() < paneStripViewportRight
                && right.getX() + right.getWidth() > paneStripViewportLeft
                && intersectsClip(y, y + height, settingsClipTop(), settingsViewportBottom);
        applyScrollWidgetState(left, visible, visible);
        applyScrollWidgetState(right, visible, visible);
    }

    private void positionSettingsButton(AbstractWidget button, int x, int width, int y, int height) {
        button.setRectangle(width - SCROLLBAR_GUTTER, height, x, y);
        boolean visible = (!tabbedMode || activeTab == 1)
                && button.getX() < paneStripViewportRight
                && button.getX() + button.getWidth() > paneStripViewportLeft
                && intersectsClip(y, y + height, settingsClipTop(), settingsViewportBottom);
        applyScrollWidgetState(button, visible, visible);
    }

    private void applyScrollWidgetState(AbstractWidget widget, boolean visible, boolean active) {
        widget.visible = visible;
        widget.active = active;
        if (!visible && widget.isFocused()) {
            widget.setFocused(false);
            if (this.getFocused() == widget) {
                this.setFocused(null);
            }
        }
    }

    private void addSettingsScrollWidget(AbstractWidget widget) {
        if (widget == null) return;
        this.addWidget(widget);
        settingsScrollWidgets.add(widget);
    }

    private void renderSettingsScrollWidgets(GuiGraphics context, int mouseX, int mouseY, float delta) {
        for (AbstractWidget widget : settingsScrollWidgets) {
            if (widget != null && widget.visible) {
                widget.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void openGameRules() {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new EditGameRulesScreen(this.gameRules, optional -> {
            optional.ifPresent(rules -> this.gameRules = rules);
            this.minecraft.setScreen(this);
        }));
    }

    private void openHudStudio() {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new LatitudeHudStudioScreen(this));
    }

    // ── Begin Expedition ──

    private void beginExpedition() {
        if (this.minecraft == null) return;
        String worldName = this.worldNameField.getValue().trim();
        if (worldName.isEmpty()) worldName = "New World";
        String seed = this.seedField.getValue(); // raw — no client-side trim

        GameType gameMode = selectedModeIdx == 2 ? GameType.CREATIVE : GameType.SURVIVAL;
        boolean hardcore = selectedModeIdx == 1;
        Difficulty difficulty = hardcore ? Difficulty.HARD : selectedDifficulty;

        LatitudeWorldLauncher.beginExpedition(this.minecraft, this, this.holder,
                worldName, seed, this.selectedSize, this.selectedZone, this.randomZone,
                gameMode, hardcore, difficulty, allowCommands, startWithCompass, bonusChest,
                generateStructures, this.gameRules, this.worldTypeIdx);
    }

    public void probeAutoConfirmWorldCreation() {
        LOGGER.info("[LAT][CWPATH] LatitudeCreateWorldScreen.probeAutoConfirmWorldCreation screen={}",
                this.getClass().getName());
        this.beginExpedition();
    }

    public void probeSetWorldInputs(String worldName, String seed, GlobeWorldSize size) {
        if (worldName != null && !worldName.isBlank() && this.worldNameField != null) {
            String trimmed = worldName.trim();
            this.worldNameField.setValue(trimmed);
            this.worldNameInput = trimmed;
        }
        if (seed != null && !seed.isBlank() && this.seedField != null) {
            String trimmed = seed.trim();
            this.seedField.setValue(trimmed);
            this.seedInput = trimmed;
        }
        if (size != null) {
            this.selectedSize = size;
        }
        LOGGER.info("[LAT][CWPATH] LatitudeCreateWorldScreen.probeSetWorldInputs screen={} worldName={} seedSet={} size={}",
                this.getClass().getName(),
                this.worldNameField != null ? this.worldNameField.getValue() : "<missing>",
                seed != null && !seed.isBlank(),
                this.selectedSize);
    }

    public void probeSetCreativeMode() {
        this.selectedModeIdx = 2;
        this.allowCommands = true;
        LOGGER.info("[LAT][CWPATH] LatitudeCreateWorldScreen.probeSetCreativeMode screen={} mode={} allowCommands={}",
                this.getClass().getName(), MODE_NAMES[this.selectedModeIdx], this.allowCommands);
    }

    // ── Close behavior ──

    @Override
    public void onClose() {
        this.onClose.run();
        if (this.minecraft != null && (this.minecraft.screen == this || this.minecraft.screen == null)) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (getPaneStripMaxScroll() > 0
                && horizontalAmount != 0.0D
                && mouseX >= paneStripViewportLeft
                && mouseX < paneStripViewportRight
                && mouseY >= panelTop
                && mouseY < panelBottom) {
            applyPaneStripScroll(paneStripScroll - (int) Math.signum(horizontalAmount) * scaledUi(28));
            return true;
        }
        if ((!tabbedMode || activeTab == 0) && mouseX >= Math.max(leftX, paneStripViewportLeft) && mouseX < Math.min(leftX + leftW, paneStripViewportRight) && mouseY >= panelTop && mouseY < panelBottom) {
            int viewportHeight = Math.max(0, leftViewportBottom - leftViewportTop);
            int maxScroll = Math.max(0, leftContentHeight - viewportHeight);
            if (maxScroll > 0 && verticalAmount != 0.0D) {
                leftScroll -= (int) Math.signum(verticalAmount) * scaledUi(18);
                if (leftScroll < 0) leftScroll = 0;
                if (leftScroll > maxScroll) leftScroll = maxScroll;
                updateLeftLayout();
                return true;
            }
        }
        if ((!tabbedMode || activeTab == 0) && mouseX >= Math.max(rightX, paneStripViewportLeft) && mouseX < Math.min(rightX + rightW, paneStripViewportRight) && mouseY >= panelTop && mouseY < panelBottom) {
            int viewportHeight = Math.max(0, rightViewportBottom - rightViewportTop);
            int maxScroll = Math.max(0, rightContentHeight - viewportHeight);
            if (maxScroll > 0 && verticalAmount != 0.0D) {
                rightScroll -= (int) Math.signum(verticalAmount) * scaledUi(18);
                if (rightScroll < 0) rightScroll = 0;
                if (rightScroll > maxScroll) rightScroll = maxScroll;
                updateRightLayout();
                return true;
            }
        }
        if ((!tabbedMode || activeTab == 1) && mouseX >= Math.max(railX, paneStripViewportLeft) && mouseX < Math.min(railX + railW, paneStripViewportRight) && mouseY >= panelTop && mouseY < panelBottom) {
            int viewportHeight = Math.max(0, settingsViewportBottom - settingsViewportTop);
            int maxScroll = Math.max(0, settingsContentHeight - viewportHeight);
            if (maxScroll > 0 && verticalAmount != 0.0D) {
                settingsScroll -= (int) Math.signum(verticalAmount) * scaledUi(18);
                if (settingsScroll < 0) settingsScroll = 0;
                if (settingsScroll > maxScroll) settingsScroll = maxScroll;
                updateSettingsLayout();
                return true;
            }
        }
        if (getPaneStripMaxScroll() > 0
                && mouseX >= paneStripScrollbarX
                && mouseX < paneStripScrollbarX + paneStripScrollbarW
                && mouseY >= paneStripScrollbarY - 2
                && mouseY < paneStripScrollbarY + paneStripScrollbarH + 2
                && verticalAmount != 0.0D) {
            applyPaneStripScroll(paneStripScroll - (int) Math.signum(verticalAmount) * scaledUi(28));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        // Zone rows scroll under the panel edge but keep their full rectangle, so vanilla dispatches
        // against geometry the player cannot see: a click on Cancel went to whichever row overlapped
        // it, visibly selecting a climate instead of closing the screen. The overlap grows with GUI
        // scale, where the panel bottom crowds the button row -- which is why it only reproduced
        // there. A row has no claim on any point outside its own clip, so mute the rows for those
        // clicks and let the real widget underneath take them.
        if (!isInsideSpawnClip(click.x(), click.y())) {
            List<ZoneRowWidget> muted = new ArrayList<>();
            for (ZoneRowWidget row : zoneRows) {
                if (row.active) {
                    row.active = false;
                    muted.add(row);
                }
            }
            try {
                return globe$dispatchClick(click, doubled);
            } finally {
                for (ZoneRowWidget row : muted) {
                    row.active = true;
                }
            }
        }
        return globe$dispatchClick(click, doubled);
    }

    private boolean globe$dispatchClick(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0 && handleTabClick(click.x(), click.y())) {
            return true;
        }
        if (click.button() == 0
                && getPaneStripMaxScroll() > 0
                && click.x() >= paneStripScrollbarX
                && click.x() < paneStripScrollbarX + paneStripScrollbarW
                && click.y() >= paneStripScrollbarY - 2
                && click.y() < paneStripScrollbarY + paneStripScrollbarH + 2) {
            draggingPaneStripScrollbar = true;
            setPaneStripScrollFromMouse(click.x());
            return true;
        }
        if (click.button() == 0 && handleSpawnZoneClippedClick(click, doubled)) {
            return true;
        }
        // Widgets may intersect the viewport so their visible portion renders continuously. Consume clicks
        // in the clipped-off heading/footer area before Screen dispatches against the widget's full rectangle.
        if (isInsideRulesPanel(click.x(), click.y()) && !isInsideRulesClip(click.x(), click.y())) {
            return true;
        }
        if (isInsideSpawnPanel(click.x(), click.y()) && !isInsideSpawnClip(click.x(), click.y())) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingPaneStripScrollbar && click.button() == 0) {
            setPaneStripScrollFromMouse(click.x());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (draggingPaneStripScrollbar && click.button() == 0) {
            draggingPaneStripScrollbar = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    // ══════════════════════════════════════════════════════════════
    // Rendering
    // ══════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // One authoritative layout pass per rendered frame keeps rectangles, culling, focus, and narration
        // synchronized after scroll, resize, tab changes, world-size changes, or sub-screen return.
        updateLeftLayout();
        updateRightLayout();
        updateSettingsLayout();
        int titlePaneX = threeCol ? rightX : 12;
        int titlePaneW = threeCol ? rightW : Math.max(1, this.width - 24);
        int headerBottom = tabbedMode ? tabStripY - 2 : panelTop;
        UiRect headerRect = new UiRect(titlePaneX, headerY, Math.max(1, titlePaneW), Math.max(1, headerBottom - headerY - 6));
        int headerLineY = headerRect.y;
        int titleWidth = Math.round(uiTextWidth(CREATE_TITLE_TEXT) * CREATE_TITLE_SCALE);
        int titleHeight = Math.round(uiFontHeight() * CREATE_TITLE_SCALE);
        if (titleWidth <= headerRect.w && titleHeight <= headerRect.h) {
            drawScaledText(context, CREATE_TITLE_TEXT, headerRect.x + (headerRect.w - titleWidth) / 2, headerLineY,
                    CREATE_TITLE_SCALE, GOLD, true);
            headerLineY += titleHeight + scaledUi(5);
        } else if (drawCenteredBoundedText(context, "LATITUDE", new UiRect(headerRect.x, headerLineY, headerRect.w, uiFontHeight()), GOLD, true, false)) {
            headerLineY += uiFontHeight() + scaledUi(6);
        }
        if (drawCenteredBoundedText(context, "New World", new UiRect(headerRect.x, headerLineY, headerRect.w, uiFontHeight()), WARM_WHITE, true, false)) {
            headerLineY += uiFontHeight() + scaledUi(4);
        }
        drawWrappedTextBlock(context, "Prepare your journey across the globe", new UiRect(headerRect.x, headerLineY, headerRect.w, Math.max(0, headerRect.bottom() - headerLineY)), MUTED, false, 2, true, true);

        if (tabbedMode) {
            drawTabStrip(context, mouseX, mouseY);
        }

        if (!tabbedMode || activeTab == 0) {
        drawViewportClippedPanel(context, leftX, panelTop, leftW, panelBottom - panelTop);
        int leftClipLeft = Math.max(leftX + 1, paneStripViewportLeft);
        int leftClipRight = Math.min(leftX + leftW - 1, paneStripViewportRight);
        if (threeCol) {
            drawInlineHeading(context, leftX, leftW, "World", GOLD);
        }
        int leftClipTop = threeCol ? headerBandBottom() : leftViewportTop;
        if (leftClipRight > leftClipLeft) {
            context.enableScissor(leftClipLeft, leftClipTop, leftClipRight, leftViewportBottom);
        int inputX = leftX + 4;
        int stepperBtnW = sizePrevBtn != null ? sizePrevBtn.getWidth() : 20;
        int labelColor = GOLD;
        int labelOff = scaledUi(10);
        int leftTextWidth = Math.max(24, leftW - 8 - SCROLLBAR_GUTTER);
        drawBoundedText(context, "World Name", new UiRect(inputX, worldFieldY - labelOff, leftTextWidth, uiFontHeight()), labelColor, false, false);
        drawBoundedText(context, "Seed", new UiRect(inputX, seedFieldY - labelOff, leftTextWidth, uiFontHeight()), labelColor, false, false);
        drawBoundedText(context, "World Size", new UiRect(inputX, sizeFieldY - labelOff, leftTextWidth, uiFontHeight()), labelColor, false, false);
        renderSizeLabel(context, inputX + stepperBtnW + scaledUi(4), sizeFieldY - 1, leftTextWidth - stepperBtnW * 2 - scaledUi(8));
        if (shouldShowSmallWorldWarning()) {
            int warningHeight = getSmallWorldWarningHeight(leftTextWidth);
            drawSmallWorldWarning(context, new UiRect(inputX, inputBottomY + scaledUi(3), leftTextWidth, warningHeight));
        }
        boolean latWorld = isLatitudeWorld();
        if (leftPreviewBottomY - leftPreviewTopY >= 30) {
            if (latWorld) {
                renderPlanispherePreview(context, leftX + 4, leftPreviewTopY, leftX + leftW - 4 - SCROLLBAR_GUTTER, leftPreviewBottomY);
            } else {
                renderPlanisphereDisabled(context, leftX + 4, leftPreviewTopY, leftX + leftW - 4 - SCROLLBAR_GUTTER, leftPreviewBottomY);
            }
        }
        context.disableScissor();
        }
        drawPaneScrollbar(context, leftX, leftW, leftViewportTop, leftViewportBottom, leftContentHeight, leftScroll);
        } // end tab 0 (World)

        if (!tabbedMode || activeTab == 0) {
        drawViewportClippedPanel(context, rightX, panelTop, rightW, panelBottom - panelTop);
        boolean latWorld = isLatitudeWorld();
        int rightTextWidth = Math.max(40, rightW - 8 - SCROLLBAR_GUTTER);
        if (threeCol) {
            drawInlineHeading(context, rightX, rightW, "Spawn Zone", latWorld ? GOLD : DISABLED_COLOR);
        }
        int rightClipLeft = Math.max(rightX + 1, paneStripViewportLeft);
        int rightClipRight = Math.min(rightX + rightW - 1, paneStripViewportRight);
        if (rightClipRight > rightClipLeft) {
        context.enableScissor(rightClipLeft, spawnClipTop(), rightClipRight, rightViewportBottom);
        if (!threeCol) {
        drawWrappedTextBlock(context, "Choose the climate where your journey begins", new UiRect(rightX + 4, rightSubtitleY, rightTextWidth, Math.max(uiFontHeight(), rightDividerY - rightSubtitleY - scaledUi(2))), latWorld ? GOLD : DISABLED_COLOR, false, 2, true, true);
        context.fill(rightX + 4, rightDividerY, rightX + rightW - 4 - SCROLLBAR_GUTTER, rightDividerY + 1, PANEL_BORDER);
        }
        if (latWorld) {
        int barInset = 4;
        int barTotalW = rightW - barInset * 2 - SCROLLBAR_GUTTER;
        LatitudeBands.Band[] allBands = LatitudeBands.Band.values();
        for (int i = 0; i < allBands.length; i++) {
            int segX = rightX + barInset + (barTotalW * i / allBands.length);
            int segXEnd = rightX + barInset + (barTotalW * (i + 1) / allBands.length);
            boolean sel = !randomZone && allBands[i] == selectedZone;
            int bandColor = BAND_COLORS[i];
            if (sel) {
                context.fill(segX, rightBarY, segXEnd, rightBarY + rightBarH, GOLD);
                context.fill(segX + 1, rightBarY + 1, segXEnd - 1, rightBarY + rightBarH - 1, bandColor);
            } else {
                int dimColor = (bandColor & 0x00FFFFFF) | (0x66 << 24);
                context.fill(segX, rightBarY, segXEnd, rightBarY + rightBarH, dimColor);
            }
        }

        int descPanelX = rightX + 2;
        int descPanelW = rightW - 4 - SCROLLBAR_GUTTER;
        int textMaxW = descPanelW - 12;
        String spawnLine = spawnZoneDescription();
        if (rightDescPanelH > scaledUi(24)) {
            context.fill(descPanelX, rightDescPanelY, descPanelX + descPanelW, rightDescPanelY + rightDescPanelH, PANEL_BG);
            int sideColor = randomZone ? MUTED : BAND_COLORS[selectedZone.ordinal()];
            context.fill(descPanelX, rightDescPanelY, descPanelX + 2, rightDescPanelY + rightDescPanelH, sideColor);

            int textX = descPanelX + 6;
            int ty = rightDescPanelY + scaledUi(3);
            String zoneHeader = (randomZone ? "Random" : selectedZone.displayName()) + " zone selected";
            drawBoundedText(context, zoneHeader, new UiRect(textX, ty, textMaxW, uiFontHeight()), GOLD, true, true);
            ty += uiFontHeight() + scaledUi(5);
            ty += drawWrappedTextBlock(context, spawnLine, new UiRect(textX, ty, textMaxW, Math.max(0, rightDescPanelY + rightDescPanelH - ty - uiFontHeight() - scaledUi(4))), WARM_WHITE, false, 3, false, true);
            ty += scaledUi(4);
            if (ty + uiFontHeight() <= rightDescPanelY + rightDescPanelH) {
                String climate = randomZone ? "A surprise" : ZONE_CLIMATE[selectedZone.ordinal()];
                drawBoundedText(context, "Climate: " + climate, new UiRect(textX, ty, textMaxW, uiFontHeight()), MUTED, false, true);
            }
        }
        } else {
            renderSpawnZoneDisabled(context);
        }
        context.disableScissor();
        }
        drawPaneScrollbar(context, rightX, rightW, rightViewportTop, rightViewportBottom, rightContentHeight, rightScroll);
        } // end World-tab Spawn Zone pane

        if (!tabbedMode || activeTab == 1) {
            drawViewportClippedPanel(context, railX, panelTop, railW, panelBottom - panelTop);
            int settLabelX = railX + 4;
            int railClipLeft = Math.max(railX + 1, paneStripViewportLeft);
            int railClipRight = Math.min(railX + railW - 1, paneStripViewportRight);
            if (threeCol) {
                drawInlineHeading(context, railX, railW, "Rules", GOLD);
            }
            if (railClipRight > railClipLeft) {
            context.enableScissor(railClipLeft, settingsClipTop(), railClipRight, settingsViewportBottom);
            if (tabbedMode) {
                drawSettingsRowLabel(context, "World Type", settLabelX, settingsColumnW, worldTypeRowY, MUTED);
                drawSettingsStepperValue(context, WORLD_TYPE_NAMES[worldTypeIdx], WORLD_TYPE_COLORS[worldTypeIdx], worldTypeRowY, settLabelX, settingsColumnW);
                drawSettingsRowLabel(context, "Game Mode", settingsRightColumnX, settingsColumnW, modeRowY, MUTED);
                drawSettingsStepperValue(context, MODE_NAMES[selectedModeIdx], MODE_COLORS[selectedModeIdx], modeRowY, settingsRightColumnX, settingsColumnW);
                drawSettingsRowLabel(context, "Commands", settLabelX, settingsColumnW, commandsRowY, MUTED);
                drawSettingsRowLabel(context, "Starting Compass", settingsRightColumnX, settingsColumnW, compassRowY, isLatitudeWorld() ? MUTED : DISABLED_COLOR);
                drawSettingsRowLabel(context, "Generate Structures", settLabelX, settingsColumnW, structuresRowY, MUTED);
                drawSettingsRowLabel(context, "Bonus Chest", settingsRightColumnX, settingsColumnW, bonusChestRowY, MUTED);
                drawSettingsRowLabel(context, "Game Rules", settLabelX, settingsColumnW, gameRulesRowY, MUTED);
                drawSettingsRowLabel(context, "HUD Studio", settingsRightColumnX, settingsColumnW, hudStudioRowY, MUTED);
            } else {
                drawSettingsRowLabel(context, "World Type", settLabelX, settingsColumnW, worldTypeRowY, MUTED);
                drawSettingsStepperValue(context, WORLD_TYPE_NAMES[worldTypeIdx], WORLD_TYPE_COLORS[worldTypeIdx], worldTypeRowY, settLabelX, settingsColumnW);
                drawSettingsRowLabel(context, "Game Mode", settLabelX, settingsColumnW, modeRowY, MUTED);
                drawSettingsStepperValue(context, MODE_NAMES[selectedModeIdx], MODE_COLORS[selectedModeIdx], modeRowY, settLabelX, settingsColumnW);
                drawSettingsRowLabel(context, "Commands", settLabelX, settingsColumnW, commandsRowY, MUTED);
                drawSettingsRowLabel(context, "Starting Compass", settLabelX, settingsColumnW, compassRowY, isLatitudeWorld() ? MUTED : DISABLED_COLOR);
                drawSettingsRowLabel(context, "Generate Structures", settLabelX, settingsColumnW, structuresRowY, MUTED);
                drawSettingsRowLabel(context, "Bonus Chest", settLabelX, settingsColumnW, bonusChestRowY, MUTED);
                drawSettingsRowLabel(context, "Game Rules", settLabelX, settingsColumnW, gameRulesRowY, MUTED);
                drawSettingsRowLabel(context, "HUD Studio", settLabelX, settingsColumnW, hudStudioRowY, MUTED);
            }
            renderSettingsScrollWidgets(context, mouseX, mouseY, delta);
            context.disableScissor();
            }
            drawPaneScrollbar(context, railX, railW, settingsViewportTop, settingsViewportBottom, settingsContentHeight, settingsScroll);
        } // end Rules tab

        if (!tabbedMode) {
            drawHorizontalScrollbar(context);
        }

        renderCreateVersionLabel(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderSizeLabel(GuiGraphics context, int x, int y, int availW) {
        int idx = selectedSize.ordinal();
        String shortName = SIZE_SHORT_NAMES[idx];
        String diameter = formatDiameter(selectedSize.borderRadiusBlocks * 2) + " blocks";
        String desc = SIZE_DESCRIPTIONS[idx];

        drawCenteredBoundedText(context, shortName, new UiRect(x, y, availW, uiFontHeight()), WARM_WHITE, true, true);
        drawCenteredBoundedText(context, diameter, new UiRect(x, y + scaledUi(11), availW, uiFontHeight()), MUTED, false, true);
        drawWrappedTextBlock(context, desc, new UiRect(x, y + scaledUi(22), availW, Math.max(uiFontHeight(), computeSizeLabelBottom(y, availW) - (y + scaledUi(22)))), MUTED, false, 3, true, true);
    }

    private boolean shouldShowSmallWorldWarning() {
        return switch (selectedSize) {
            case ITTY_BITTY, TINY, SMALL -> true;
            default -> false;
        };
    }

    private void renderPlanispherePreview(GuiGraphics context, int areaLeft, int areaTop, int areaRight, int areaBottom) {
        long dbgStart = DEBUG_UI_SWITCH_LAG ? Util.getMillis() : 0L;
        // Regular is the visual reference size. Smaller worlds sit within its quiet underlay; larger worlds
        // grow outward from it and retain a darkened Regular map as an honest scale comparison. Size changes
        // interpolate their displayed diameter briefly so this relationship is visible instead of abrupt.
        int inset = scaledUi(6);
        int captionGap = scaledUi(9);
        int captionHeight = uiFontHeight() * 2 + captionGap;
        int availableWidth = areaRight - areaLeft - inset * 2;
        int availableHeight = areaBottom - areaTop - inset * 2 - captionHeight;
        int ginormousSide = Math.min(availableWidth, availableHeight);
        if (ginormousSide < 8) return;

        int selectedDiameter = selectedSize.borderRadiusBlocks * 2;
        int displayedDiameter = animatedAtlasDiameter(Util.getMillis());
        int regularDiameter = GlobeWorldSize.REGULAR.borderRadiusBlocks * 2;
        int ginormousDiameter = GlobeWorldSize.MASSIVE.borderRadiusBlocks * 2;
        int regularSide = Math.round(ginormousSide * ATLAS_REGULAR_REFERENCE_FRACTION);
        boolean smallerThanRegular = displayedDiameter < regularDiameter;
        boolean largerThanRegular = displayedDiameter > regularDiameter;
        int atlasSide = regularSide;
        if (largerThanRegular) {
            float beyondRegular = (displayedDiameter - regularDiameter) / (float) (ginormousDiameter - regularDiameter);
            atlasSide = Math.round(regularSide + (ginormousSide - regularSide) * beyondRegular);
        }
        int atlasLeft = areaLeft + (areaRight - areaLeft - atlasSide) / 2;
        int atlasTop = areaTop + inset + (availableHeight - atlasSide) / 2;
        LatitudeBands.Band previewZone = randomZone ? null : selectedZone;

        if (smallerThanRegular) {
            LatitudePlanisphereRenderer.renderRegularWorldUnderlay(context, atlasLeft, atlasTop, regularSide);
            int selectedSide = Math.round(regularSide * displayedDiameter / (float) regularDiameter);
            int selectedLeft = atlasLeft + (regularSide - selectedSide) / 2;
            int selectedTop = atlasTop + (regularSide - selectedSide) / 2;
            LatitudePlanisphereRenderer.renderCompact(context, selectedLeft, selectedTop, selectedSide, previewZone);
        } else {
            LatitudePlanisphereRenderer.renderCompact(context, atlasLeft, atlasTop, atlasSide, previewZone);
            if (largerThanRegular) {
                int referenceSide = Math.round(atlasSide * regularDiameter / (float) displayedDiameter);
                int referenceLeft = atlasLeft + (atlasSide - referenceSide) / 2;
                int referenceTop = atlasTop + (atlasSide - referenceSide) / 2;
                LatitudePlanisphereRenderer.renderDarkenedRegularReference(context, referenceLeft, referenceTop, referenceSide);
            }
        }

        int captionY = atlasTop + atlasSide + captionGap;
        String selectedCaption = SIZE_SHORT_NAMES[selectedSize.ordinal()] + " · " + formatDiameter(selectedDiameter) + " blocks";
        drawCenteredBoundedText(context, selectedCaption,
                new UiRect(areaLeft + inset, captionY, areaRight - areaLeft - inset * 2, uiFontHeight()), MUTED, false, true);
        if (selectedDiameter != regularDiameter) {
            drawCenteredBoundedText(context, "Regular reference · 20,000 blocks",
                    new UiRect(areaLeft + inset, captionY + uiFontHeight(), areaRight - areaLeft - inset * 2, uiFontHeight()),
                    smallerThanRegular ? 0x668C8078 : 0xAA8C8078, false, true);
        }
        if (DEBUG_UI_SWITCH_LAG && Util.getMillis() <= debugSwitchSampleDeadlineMs) {
            long elapsed = Util.getMillis() - dbgStart;
            if (elapsed >= 1L) {
                LOGGER.info("[lat-ui] switchLag seq={} worldType={} section=planispherePreview ms={}", debugSwitchSeq, currentWorldTypeName(), elapsed);
            }
        }
    }

    private int animatedAtlasDiameter(long nowMs) {
        int targetDiameter = selectedSize.borderRadiusBlocks * 2;
        if (atlasTransitionStartedMs < 0L || atlasTransitionFromDiameter < 0) {
            return targetDiameter;
        }
        float progress = Math.min(1.0f, Math.max(0.0f, (nowMs - atlasTransitionStartedMs) / (float) ATLAS_SIZE_TRANSITION_MS));
        if (progress >= 1.0f) {
            atlasTransitionStartedMs = -1L;
            atlasTransitionFromDiameter = -1;
            return targetDiameter;
        }
        float eased = 1.0f - (float) Math.pow(1.0f - progress, 3.0f);
        return Math.round(atlasTransitionFromDiameter + (targetDiameter - atlasTransitionFromDiameter) * eased);
    }

    private void renderSpawnZoneDisabled(GuiGraphics context) {
        long dbgStart = DEBUG_UI_SWITCH_LAG ? Util.getMillis() : 0L;
        int overlayTop = rightDividerY + 2;
        int overlayBottom = rightViewportBottom;
        if (overlayBottom <= overlayTop + 4) return;
        context.fill(rightX + 1, overlayTop, rightX + rightW - 1, overlayBottom, 0xCC1A1410);
        int textW = Math.max(40, rightW - 8 - SCROLLBAR_GUTTER);
        int midY = overlayTop + (overlayBottom - overlayTop) / 2;
        String line1 = "Not available for";
        String line2 = WORLD_TYPE_NAMES[worldTypeIdx];
        int gap = 3;
        int totalTextH = uiFontHeight() * 2 + gap;
        int ty = midY - totalTextH / 2;
        drawCenteredBoundedText(context, line1, new UiRect(rightX + 4, ty, textW, uiFontHeight()), DISABLED_COLOR, false, true);
        drawCenteredBoundedText(context, line2, new UiRect(rightX + 4, ty + uiFontHeight() + gap, textW, uiFontHeight()), MUTED, false, true);
        if (DEBUG_UI_SWITCH_LAG && Util.getMillis() <= debugSwitchSampleDeadlineMs) {
            long elapsed = Util.getMillis() - dbgStart;
            if (elapsed >= 1L) {
                LOGGER.info("[lat-ui] switchLag seq={} worldType={} section=spawnZoneDisabled ms={}", debugSwitchSeq, currentWorldTypeName(), elapsed);
            }
        }
    }

    private void renderPlanisphereDisabled(GuiGraphics context, int areaLeft, int areaTop, int areaRight, int areaBottom) {
        long dbgStart = DEBUG_UI_SWITCH_LAG ? Util.getMillis() : 0L;
        int areaW = Math.max(0, areaRight - areaLeft);
        int areaH = Math.max(0, areaBottom - areaTop);
        if (areaW <= 6 || areaH <= 6) return;

        int pad = scaledUi(6);
        int boxLeft = areaLeft + pad;
        int boxTop = areaTop + pad;
        int boxRight = areaRight - pad;
        int boxBottom = areaBottom - pad;
        if (boxRight <= boxLeft || boxBottom <= boxTop) return;

        // Soft, low-cost placeholder panel
        int overlay = 0x501A1410;
        int border = PANEL_BORDER;
        context.fill(boxLeft, boxTop, boxRight, boxBottom, overlay);
        context.fill(boxLeft, boxTop, boxRight, boxTop + 1, border);
        context.fill(boxLeft, boxBottom - 1, boxRight, boxBottom, border);
        context.fill(boxLeft, boxTop, boxLeft + 1, boxBottom, border);
        context.fill(boxRight - 1, boxTop, boxRight, boxBottom, border);

        // Simple inner accent lines
        int midY = boxTop + (boxBottom - boxTop) / 2;
        int accentInset = scaledUi(4);
        context.fill(boxLeft + accentInset, midY, boxRight - accentInset, midY + 1, 0x40FFFFFF & PANEL_BORDER);

        String label = "Preview available only for Latitude";
        drawCenteredBoundedText(context, label, new UiRect(boxLeft + pad, boxTop + pad, boxRight - boxLeft - pad * 2, uiFontHeight()), DISABLED_COLOR, false, true);
        if (DEBUG_UI_SWITCH_LAG && Util.getMillis() <= debugSwitchSampleDeadlineMs) {
            long elapsed = Util.getMillis() - dbgStart;
            if (elapsed >= 1L) {
                LOGGER.info("[lat-ui] switchLag seq={} worldType={} section=planisphereDisabled ms={}", debugSwitchSeq, currentWorldTypeName(), elapsed);
            }
        }
    }

    private PreviewLayout computePreviewLayout(int areaLeft, int areaTop, int areaRight, int areaBottom,
                                               int radius, float labelScale, float captionScale, String caption) {
        int globeDiameter = radius * 2;
        int labelWidth = 0;
        for (double deg : PREVIEW_LABEL_DEGREES) {
            labelWidth = Math.max(labelWidth, scaledTextWidth(formatDegree(deg), labelScale));
        }
        int labelPad = Math.max(6, Math.round(radius * 0.07f));
        int compositionWidth = globeDiameter + labelPad + labelWidth;
        if (compositionWidth > areaRight - areaLeft) {
            return null;
        }

        int captionHeight = scaledFontHeight(captionScale);
        int captionGap = Math.max(4, Math.round(radius * 0.06f));
        int compositionHeight = globeDiameter + captionGap + captionHeight;
        if (compositionHeight > areaBottom - areaTop) {
            return null;
        }

        int compositionLeft = areaLeft + (areaRight - areaLeft - compositionWidth) / 2;
        int globeLeft = compositionLeft;
        int globeTop = areaTop + (areaBottom - areaTop - compositionHeight) / 2;
        int globeCenterY = globeTop + radius;
        int labelX = globeLeft + globeDiameter + labelPad;
        int labelHeight = scaledFontHeight(labelScale);
        int[] labelYs = computePreviewLabelYs(globeCenterY, radius, labelHeight);
        int lastLabelBottom = labelYs[labelYs.length - 1] + labelHeight;
        int captionY = Math.max(globeTop + globeDiameter + captionGap, lastLabelBottom + 4);
        if (captionY + captionHeight > areaBottom) {
            return null;
        }

        int captionX = compositionLeft + (compositionWidth - scaledTextWidth(caption, captionScale)) / 2;
        return new PreviewLayout(globeLeft, globeTop, globeDiameter, labelX, labelYs, captionX, captionY, labelScale, captionScale);
    }

    private int[] computePreviewLabelYs(int globeCenterY, int radius, int labelHeight) {
        int[] labelYs = new int[PREVIEW_LABEL_DEGREES.length];
        for (int i = 0; i < PREVIEW_LABEL_DEGREES.length; i++) {
            double deg = PREVIEW_LABEL_DEGREES[i];
            int yOff = (int) Math.round(radius * deg / 90.0);
            labelYs[i] = globeCenterY + yOff - labelHeight / 2;
        }

        int minGap = isTinyPreview(selectedSize) ? Math.max(labelHeight, 9) : Math.max(labelHeight - 1, 7);
        for (int i = 1; i < labelYs.length; i++) {
            if (labelYs[i] < labelYs[i - 1] + minGap) {
                labelYs[i] = labelYs[i - 1] + minGap;
            }
        }
        return labelYs;
    }

    private boolean isTinyPreview(GlobeWorldSize size) {
        return size == GlobeWorldSize.ITTY_BITTY || size == GlobeWorldSize.TINY;
    }

    private float previewLabelScale(GlobeWorldSize size) {
        return switch (size) {
            case ITTY_BITTY -> 0.58f;
            case TINY -> 0.66f;
            case SMALL -> 0.78f;
            case REGULAR -> 0.88f;
            case LARGE -> 0.94f;
            case MASSIVE -> 0.96f;
        };
    }

    private float previewCaptionScale(GlobeWorldSize size) {
        return switch (size) {
            case ITTY_BITTY -> 0.62f;
            case TINY -> 0.70f;
            case SMALL -> 0.82f;
            case REGULAR -> 0.90f;
            case LARGE -> 0.94f;
            case MASSIVE -> 0.96f;
        };
    }

    private float previewDiscFill(GlobeWorldSize size) {
        return switch (size) {
            case ITTY_BITTY -> 0.48f;
            case TINY -> 0.58f;
            case SMALL -> 0.73f;
            case REGULAR -> 0.84f;
            case LARGE -> 0.91f;
            case MASSIVE -> 0.96f;
        };
    }

    private int scaledTextWidth(String text, float scale) {
        return Math.round(this.font.width(text) * scale);
    }

    private int scaledFontHeight(float scale) {
        return Math.max(5, Math.round(this.font.lineHeight * scale));
    }

    private void drawSettingsRowLabel(GuiGraphics context, String label, int x, int width, int rowY, int color) {
        int labelY = rowY - scaledUi(10);
        if (labelY + uiFontHeight() <= settingsClipTop() || labelY >= settingsViewportBottom) {
            return;
        }
        drawBoundedText(context, label, new UiRect(x, labelY, Math.max(20, width - SCROLLBAR_GUTTER), uiFontHeight()), color, false, true);
    }

    private void drawSettingsStepperValue(GuiGraphics context, String text, int color, int rowY, int cellX, int cellWidth) {
        if (rowY + uiFontHeight() <= settingsClipTop() || rowY >= settingsViewportBottom) {
            return;
        }
        int stepperW = worldTypePrevBtn != null ? worldTypePrevBtn.getWidth() : 20;
        int safeLeft = cellX + stepperW + scaledUi(6);
        int safeRight = cellX + cellWidth - stepperW - scaledUi(6) - SCROLLBAR_GUTTER;
        int safeWidth = Math.max(20, safeRight - safeLeft);
        String fitted = ellipsizeToWidth(text, safeWidth);
        int textW = uiTextWidth(fitted);
        int btnH = worldTypePrevBtn != null ? worldTypePrevBtn.getHeight() : 20;
        int drawY = rowY + Math.max(0, (btnH - uiFontHeight()) / 2);
        drawBoundedText(context, fitted, new UiRect(safeLeft + Math.max(0, (safeWidth - textW) / 2), drawY, safeWidth, uiFontHeight()), color, true, true);
    }

    private void drawUiText(GuiGraphics context, String text, int x, int y, int color, boolean shadow) {
        context.drawString(this.font, text, x, y, color, shadow);
    }

    private void drawRainbowItalicUiText(GuiGraphics context, String text, int x, int y) {
        int drawX = x;
        int visibleIndex = 0;
        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            Component glyph = Component.literal(String.valueOf(letter)).withStyle(ChatFormatting.ITALIC);
            int color = RANDOM_TEXT_COLORS[visibleIndex % RANDOM_TEXT_COLORS.length];
            context.drawString(this.font, glyph, drawX, y, color, true);
            drawX += this.font.width(glyph);
            if (letter != ' ') {
                visibleIndex++;
            }
        }
    }

    private void drawCenteredUiText(GuiGraphics context, String text, int cx, int y, int color, boolean shadow) {
        drawUiText(context, text, cx - uiTextWidth(text) / 2, y, color, shadow);
    }

    private boolean fitsWidth(String text, int width) {
        return uiTextWidth(text) <= Math.max(0, width);
    }

    private boolean fitsHeight(int height) {
        return height >= uiFontHeight();
    }

    private String ellipsizeToWidth(String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (fitsWidth(text, width)) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = uiTextWidth(ellipsis);
        if (ellipsisWidth > width) {
            return "";
        }
        return this.font.plainSubstrByWidth(text, Math.max(1, width - ellipsisWidth)) + ellipsis;
    }

    private int clampToRect(int value, int contentSize, int min, int maxExclusive) {
        int max = Math.max(min, maxExclusive - contentSize);
        return Math.max(min, Math.min(max, value));
    }

    private boolean drawBoundedText(GuiGraphics context, String text, UiRect rect, int color, boolean shadow, boolean ellipsize) {
        if (!fitsHeight(rect.h)) {
            return false;
        }
        String fitted = ellipsize ? ellipsizeToWidth(text, rect.w) : text;
        if (fitted.isEmpty() || (!ellipsize && !fitsWidth(fitted, rect.w))) {
            return false;
        }
        int drawX = clampToRect(rect.x, uiTextWidth(fitted), rect.x, rect.right());
        int drawY = clampToRect(rect.y, uiFontHeight(), rect.y, rect.bottom());
        drawUiText(context, fitted, drawX, drawY, color, shadow);
        return true;
    }

    private boolean drawBoundedStyledText(GuiGraphics context, Component text, UiRect rect, int color, boolean shadow, boolean ellipsize) {
        if (!fitsHeight(rect.h)) {
            return false;
        }
        String fitted = ellipsize ? ellipsizeToWidth(text.getString(), rect.w) : text.getString();
        if (fitted.isEmpty() || (!ellipsize && !fitsWidth(fitted, rect.w))) {
            return false;
        }
        int drawX = clampToRect(rect.x, uiTextWidth(fitted), rect.x, rect.right());
        int drawY = clampToRect(rect.y, uiFontHeight(), rect.y, rect.bottom());
        context.drawString(this.font, Component.literal(fitted).setStyle(text.getStyle().withItalic(true)), drawX, drawY, color, shadow);
        return true;
    }

    private int drawWrappedStyledTextBlock(GuiGraphics context, Component text, UiRect rect, int color, boolean shadow, int maxLines, boolean center, boolean optional) {
        if (rect.w <= 0 || rect.h < uiFontHeight()) {
            return 0;
        }
        int maxVisibleLines = Math.min(maxLines, Math.max(1, rect.h / uiFontHeight()));
        List<net.minecraft.network.chat.FormattedText> wrapped = wrapUiLines(text.getString(), rect.w);
        if (wrapped.isEmpty()) {
            return 0;
        }
        int drawCount = Math.min(maxVisibleLines, wrapped.size());
        if (optional && drawCount <= 0) {
            return 0;
        }
        int y = rect.y;
        for (int i = 0; i < drawCount; i++) {
            String line = wrapped.get(i).getString();
            if (i == drawCount - 1 && wrapped.size() > drawCount) {
                line = ellipsizeToWidth(line, rect.w);
            }
            Component lineText = Component.literal(line).setStyle(text.getStyle());
            if (center) {
                String fitted = ellipsizeToWidth(lineText.getString(), rect.w);
                if (!fitted.isEmpty()) {
                    int drawX = rect.x + Math.max(0, (rect.w - uiTextWidth(fitted)) / 2);
                    context.drawString(this.font, Component.literal(fitted).setStyle(lineText.getStyle().withItalic(true)), drawX, y, color, shadow);
                }
            } else {
                drawBoundedStyledText(context, lineText, new UiRect(rect.x, y, rect.w, uiFontHeight()), color, shadow, true);
            }
            y += uiFontHeight();
        }
        return drawCount * uiFontHeight();
    }

    private boolean drawCenteredBoundedText(GuiGraphics context, String text, UiRect rect, int color, boolean shadow, boolean ellipsize) {
        if (!fitsHeight(rect.h)) {
            return false;
        }
        String fitted = ellipsize ? ellipsizeToWidth(text, rect.w) : text;
        if (fitted.isEmpty() || (!ellipsize && !fitsWidth(fitted, rect.w))) {
            return false;
        }
        int drawX = rect.x + Math.max(0, (rect.w - uiTextWidth(fitted)) / 2);
        int drawY = clampToRect(rect.y, uiFontHeight(), rect.y, rect.bottom());
        drawUiText(context, fitted, drawX, drawY, color, shadow);
        return true;
    }

    private int drawWrappedTextBlock(GuiGraphics context, String text, UiRect rect, int color, boolean shadow, int maxLines, boolean center, boolean optional) {
        if (rect.w <= 0 || rect.h < uiFontHeight()) {
            return 0;
        }
        int maxVisibleLines = Math.min(maxLines, Math.max(1, rect.h / uiFontHeight()));
        List<net.minecraft.network.chat.FormattedText> wrapped = wrapUiLines(text, rect.w);
        if (wrapped.isEmpty()) {
            return 0;
        }
        int drawCount = Math.min(maxVisibleLines, wrapped.size());
        if (optional && drawCount <= 0) {
            return 0;
        }
        int y = rect.y;
        for (int i = 0; i < drawCount; i++) {
            String line = wrapped.get(i).getString();
            if (i == drawCount - 1 && wrapped.size() > drawCount) {
                line = ellipsizeToWidth(line, rect.w);
            }
            UiRect lineRect = new UiRect(rect.x, y, rect.w, uiFontHeight());
            if (center) {
                drawCenteredBoundedText(context, line, lineRect, color, shadow, true);
            } else {
                drawBoundedText(context, line, lineRect, color, shadow, true);
            }
            y += uiFontHeight();
        }
        return drawCount * uiFontHeight();
    }

    private static final class UiRect {
        private final int x;
        private final int y;
        private final int w;
        private final int h;

        private UiRect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = Math.max(0, w);
            this.h = Math.max(0, h);
        }

        private int right() {
            return x + w;
        }

        private int bottom() {
            return y + h;
        }
    }

    private void drawScaledText(GuiGraphics context, String text, int x, int y, float scale, int color, boolean shadow) {
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        context.drawString(this.font, text, 0, 0, color, shadow);
        matrices.popMatrix();
    }

    private void drawSmallWorldWarning(GuiGraphics context, UiRect rect) {
        float scale = smallWorldWarningScale();
        int lineHeight = smallWorldWarningLineHeight();
        List<net.minecraft.network.chat.FormattedText> lines = wrapUiLines(
                SMALL_WORLD_WARNING.getString(), Math.max(40, Math.round(rect.w / scale)));
        int maxLines = Math.min(lines.size(), Math.max(1, rect.h / lineHeight));
        for (int i = 0; i < maxLines; i++) {
            String line = lines.get(i).getString();
            int scaledWidth = Math.round(uiTextWidth(line) * scale);
            int x = rect.x + Math.max(0, (rect.w - scaledWidth) / 2);
            int y = rect.y + i * lineHeight;
            var matrices = context.pose();
            matrices.pushMatrix();
            matrices.translate((float) x, (float) y);
            matrices.scale(scale, scale);
            context.drawString(this.font, Component.literal(line).setStyle(SMALL_WORLD_WARNING.getStyle()), 0, 0, 0xFFF0A030, false);
            matrices.popMatrix();
        }
    }

    private void renderCreateVersionLabel(GuiGraphics context) {
        if (CREATE_VERSION_LABEL.isEmpty()) {
            return;
        }
        int width = Math.round(uiTextWidth(CREATE_VERSION_LABEL) * CREATE_VERSION_LABEL_SCALE);
        int height = Math.round(uiFontHeight() * CREATE_VERSION_LABEL_SCALE);
        int x = context.guiWidth() - width - scaledUi(5);
        int y = context.guiHeight() - height - scaledUi(5);
        drawScaledText(context, CREATE_VERSION_LABEL, x, y, CREATE_VERSION_LABEL_SCALE, MUTED, false);
    }

    private static boolean isOnSelectedEdge(double deg, LatitudeBands.Band band) {
        return Math.abs(deg - band.lowDeg()) < 0.01 || Math.abs(deg - band.highDeg()) < 0.01;
    }

    private String currentWorldTypeName() {
        return WORLD_TYPE_NAMES[Math.max(0, Math.min(worldTypeIdx, WORLD_TYPE_NAMES.length - 1))];
    }

    // ══════════════════════════════════════════════════════════════
    // Drawing helpers
    // ══════════════════════════════════════════════════════════════

    // ── Grid decoration ──
    private static final int GRID_COLOR = 0x14504840;
    private static final int GRID_STEP = 16;  // large-ish squares

    private void drawPanel(GuiGraphics context, int x, int y, int w, int h) {
        // Border
        context.fill(x, y, x + w, y + 1, PANEL_BORDER);
        context.fill(x, y + h - 1, x + w, y + h, PANEL_BORDER);
        context.fill(x, y, x + 1, y + h, PANEL_BORDER);
        context.fill(x + w - 1, y, x + w, y + h, PANEL_BORDER);
        // Fill
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_BG);
        // Subtle grid decoration
        drawGridDecoration(context, x + 1, y + 1, w - 2, h - 2);
    }

    private static void drawGridDecoration(GuiGraphics context, int x, int y, int w, int h) {
        if (h < 20 || w < 20) return;
        // Horizontal lines
        for (int gy = GRID_STEP; gy < h; gy += GRID_STEP) {
            context.fill(x, y + gy, x + w, y + gy + 1, GRID_COLOR);
        }
        // Vertical lines
        for (int gx = GRID_STEP; gx < w; gx += GRID_STEP) {
            context.fill(x + gx, y, x + gx + 1, y + h, GRID_COLOR);
        }
    }

    private void drawCenteredString(GuiGraphics context, String text, int cx, int y, int color, boolean shadow) {
        int textW = this.font.width(text);
        context.drawString(this.font, text, cx - textW / 2, y, color, shadow);
    }

    private void drawViewportClippedPanel(GuiGraphics context, int x, int y, int w, int h) {
        int clipLeft = Math.max(x, paneStripViewportLeft);
        int clipRight = Math.min(x + w, paneStripViewportRight);
        if (clipRight <= clipLeft) {
            return;
        }
        context.enableScissor(clipLeft, y, clipRight, y + h);
        drawPanel(context, x, y, w, h);
        context.disableScissor();
    }

    private void drawInlineHeading(GuiGraphics context, int paneX, int paneW, String label, int labelColor) {
        int headingY = panelTop + compactUi(6);
        int availableW = paneW - scaledUi(12);
        if (availableW <= 0) return;
        int textW = uiTextWidth(label);
        int lineGap = compactUi(6);
        int lineLen = Math.max(compactUi(10), (availableW - textW - lineGap * 2) / 2);
        int centerX = paneX + paneW / 2;
        int lineY = headingY + uiFontHeight() / 2;
        int lineLeftStart = centerX - (textW / 2) - lineGap - lineLen;
        int lineRightStart = centerX + (textW / 2) + lineGap;

        context.fill(lineLeftStart, lineY, lineLeftStart + lineLen, lineY + 1, PANEL_BORDER);
        context.fill(lineRightStart, lineY, lineRightStart + lineLen, lineY + 1, PANEL_BORDER);

        drawCenteredBoundedText(context, label,
                new UiRect(paneX + compactUi(4), headingY, paneW - compactUi(8), uiFontHeight()),
                labelColor, true, true);
    }

    private void drawPaneScrollbar(GuiGraphics context, int paneX, int paneW, int viewportTop, int viewportBottom,
                                   int contentHeight, int scrollAmount) {
        int viewportHeight = Math.max(0, viewportBottom - viewportTop);
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (maxScroll <= 0 || viewportHeight <= 0) {
            return;
        }

        int trackX = paneX + paneW - 4;
        int trackLeft = Math.max(trackX, paneStripViewportLeft);
        int trackRight = Math.min(trackX + 1, paneStripViewportRight);
        if (trackRight <= trackLeft) {
            return;
        }
        context.fill(trackLeft, viewportTop, trackRight, viewportBottom, PANEL_BORDER);
        int thumbH = Math.max(compactUi(18), viewportHeight * viewportHeight / Math.max(1, contentHeight));
        int thumbY = viewportTop + (viewportHeight - thumbH) * scrollAmount / maxScroll;
        int thumbLeft = Math.max(trackX - 1, paneStripViewportLeft);
        int thumbRight = Math.min(trackX + 2, paneStripViewportRight);
        if (thumbRight > thumbLeft) {
            context.fill(thumbLeft, thumbY, thumbRight, thumbY + thumbH, GOLD);
        }
    }

    private void drawTabStrip(GuiGraphics context, int mouseX, int mouseY) {
        int tabCount = TAB_LABELS.length;
        int totalW = paneStripViewportWidth;
        int tabW = (totalW - TAB_GAP * (tabCount - 1)) / tabCount;
        int x = paneStripViewportLeft;
        for (int i = 0; i < tabCount; i++) {
            boolean active = i == activeTab;
            boolean hovered = !active && mouseX >= x && mouseX < x + tabW && mouseY >= tabStripY && mouseY < tabStripY + TAB_H;
            int bg = active ? PANEL_BG : (hovered ? 0xFF3A302A : 0xFF2A2420);
            int border = active ? GOLD : PANEL_BORDER;
            // Tab background
            context.fill(x, tabStripY, x + tabW, tabStripY + TAB_H, bg);
            // Top + side borders
            context.fill(x, tabStripY, x + tabW, tabStripY + 1, border);
            context.fill(x, tabStripY, x + 1, tabStripY + TAB_H, border);
            context.fill(x + tabW - 1, tabStripY, x + tabW, tabStripY + TAB_H, border);
            if (active) {
                // Active tab: no bottom border (merges with panel)
                context.fill(x + 1, tabStripY + TAB_H - 1, x + tabW - 1, tabStripY + TAB_H, PANEL_BG);
            } else {
                // Inactive tab: bottom border
                context.fill(x, tabStripY + TAB_H - 1, x + tabW, tabStripY + TAB_H, PANEL_BORDER);
            }
            // Tab label
            int labelColor = active ? GOLD : (hovered ? WARM_WHITE : MUTED);
            String label = TAB_LABELS[i];
            int labelW = uiTextWidth(label);
            int labelX = x + (tabW - labelW) / 2;
            int labelY = tabStripY + (TAB_H - uiFontHeight()) / 2;
            drawUiText(context, label, labelX, labelY, labelColor, active);
            x += tabW + TAB_GAP;
        }
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        if (!tabbedMode) return false;
        if (mouseY < tabStripY || mouseY >= tabStripY + TAB_H) return false;
        int tabCount = TAB_LABELS.length;
        int totalW = paneStripViewportWidth;
        int tabW = (totalW - TAB_GAP * (tabCount - 1)) / tabCount;
        int x = paneStripViewportLeft;
        for (int i = 0; i < tabCount; i++) {
            if (mouseX >= x && mouseX < x + tabW) {
                switchTab(i);
                return true;
            }
            x += tabW + TAB_GAP;
        }
        return false;
    }

    private void drawHorizontalScrollbar(GuiGraphics context) {
        int maxScroll = getPaneStripMaxScroll();
        if (maxScroll <= 0 || paneStripScrollbarH <= 0) {
            return;
        }
        int trackBottom = paneStripScrollbarY + paneStripScrollbarH;
        context.fill(paneStripScrollbarX, paneStripScrollbarY, paneStripScrollbarX + paneStripScrollbarW, trackBottom, PANEL_BORDER);
        int thumbW = Math.max(24, paneStripScrollbarW * paneStripViewportWidth / Math.max(1, paneStripContentWidth));
        if (thumbW > paneStripScrollbarW) {
            thumbW = paneStripScrollbarW;
        }
        int range = Math.max(1, paneStripScrollbarW - thumbW);
        int thumbX = paneStripScrollbarX + range * paneStripScroll / maxScroll;
        context.fill(thumbX, paneStripScrollbarY - 1, thumbX + thumbW, trackBottom + 1, GOLD);
    }

    // ── Degree formatting (locked rule: whole → "35°", fractional → "23.5°") ──
    static String formatDegree(double deg) {
        if (deg == Math.floor(deg)) {
            return String.format(Locale.ROOT, "%.0f\u00B0", deg);
        }
        return String.format(Locale.ROOT, "%.1f\u00B0", deg);
    }

    // ── Diameter formatting: "7,500", "10,000", etc. ──
    private static String formatDiameter(int diameter) {
        return String.format(Locale.ROOT, "%,d", diameter);
    }

    // ══════════════════════════════════════════════════════════════
    // Zone Row Widget
    // ══════════════════════════════════════════════════════════════

    private static final class PreviewLayout {
        private final int globeLeft;
        private final int globeTop;
        private final int globeDiameter;
        private final int labelX;
        private final int[] labelYs;
        private final int captionX;
        private final int captionY;
        private final float labelScale;
        private final float captionScale;

        private PreviewLayout(int globeLeft, int globeTop, int globeDiameter,
                              int labelX, int[] labelYs, int captionX, int captionY,
                              float labelScale, float captionScale) {
            this.globeLeft = globeLeft;
            this.globeTop = globeTop;
            this.globeDiameter = globeDiameter;
            this.labelX = labelX;
            this.labelYs = labelYs;
            this.captionX = captionX;
            this.captionY = captionY;
            this.labelScale = labelScale;
            this.captionScale = captionScale;
        }
    }

    private class ZoneRowWidget extends AbstractWidget {
        @Nullable
        private final LatitudeBands.Band band;

        ZoneRowWidget(int x, int y, int w, int h, @Nullable LatitudeBands.Band band) {
            super(x, y, w, h, Component.literal(band == null ? "Random" : band.displayName()));
            this.band = band;
        }

        private void select() {
            randomZone = this.band == null;
            if (this.band != null) {
                selectedZone = this.band;
            }
        }

        @Override
        public void onClick(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
            select();
        }

        private void selectFromClippedMouseClick(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.onClick(click, doubled);
        }

        @Override
        public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
            if (!this.isActive()) return false;
            if (input.isSelection()) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                select();
                return true;
            }
            return false;
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
            boolean selected = this.band == null ? randomZone : (!randomZone && selectedZone == this.band);
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();

            int clipLeft = Math.max(rightX + 1, paneStripViewportLeft);
            int clipRight = Math.min(rightX + rightW - 1, paneStripViewportRight);
            boolean clipped = clipRight > clipLeft && rightViewportBottom > spawnClipTop();
            if (clipped) {
                context.enableScissor(clipLeft, spawnClipTop(), clipRight, rightViewportBottom);
            }

            if (selected) {
                // Warm gold background highlight
                context.fill(x, y, x + w, y + h, 0x40D4A74A);
                // Native band color left accent border (3px wide)
                int bandColor = this.band == null ? MUTED : BAND_COLORS[this.band.ordinal()];
                context.fill(x, y, x + 3, y + h, bandColor);
            }

            // Focus indicator (thin gold outline when keyboard-focused)
            if (this.isFocused() && !selected) {
                context.fill(x, y, x + w, y + 1, GOLD);
                context.fill(x, y + h - 1, x + w, y + h, GOLD);
                context.fill(x, y, x + 1, y + h, GOLD);
                context.fill(x + w - 1, y, x + w, y + h, GOLD);
            }

            int textColor = selected ? GOLD : MUTED;
            int textX = x + 6;

            if (this.band == null && selected) {
                drawRainbowItalicUiText(context, "Random", textX, y + compactUi(2));
            } else {
                drawUiText(context, this.band == null ? "Random" : this.band.displayName(), textX, y + compactUi(2), textColor, selected);
            }

            String range = this.band == null
                    ? formatDegree(0.0) + "\u2013" + formatDegree(90.0)
                    : formatDegree(this.band.lowDeg()) + "\u2013" + formatDegree(this.band.highDeg());
            int rangeW = uiTextWidth(range);
            int rangeX = x + w - rangeW - 4;
            drawUiText(context, range, rangeX, y + compactUi(2), selected ? WARM_WHITE : MUTED, false);

            String helper = this.band == null ? RANDOM_ZONE_HELPER : ZONE_HELPER[this.band.ordinal()];
            int helperWidth = Math.max(40, rangeX - textX - 6);
            int helperY = y + compactUi(2) + uiFontHeight() + compactUi(2);
            for (net.minecraft.network.chat.FormattedText wrappedLine : wrapUiLines(helper, helperWidth)) {
                if (helperY + uiFontHeight() > y + h - compactUi(2)) break;
                drawUiText(context, wrappedLine.getString(), textX, helperY, MUTED, false);
                helperY += uiFontHeight();
            }

            if (clipped) {
                context.disableScissor();
            }
            this.handleCursor(context);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }
}
