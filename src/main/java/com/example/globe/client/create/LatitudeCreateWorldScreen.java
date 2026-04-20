package com.example.globe.client.create;

import com.example.globe.client.GlobeWorldSize;
import com.example.globe.client.LatitudeHudStudioScreen;
import com.example.globe.util.LatitudeBands;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.world.EditGameRulesScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.VanillaDataPackProvider;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.SaveLoading;
import net.minecraft.SharedConstants;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.rule.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.client.gui.screen.world.WorldCreationSettings;
import net.minecraft.world.level.WorldGenSettings;
import net.minecraft.client.world.GeneratorOptionsFactory;
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
    private static final double[] PREVIEW_LABEL_DEGREES = {0.0, 23.5, 35.0, 50.0, 66.5, 90.0};

    private static final GlobeWorldSize DEFAULT_SIZE = GlobeWorldSize.REGULAR;

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

    // ── Size short names (indexed by GlobeWorldSize.ordinal()) ──
    private static final String[] SIZE_SHORT_NAMES = {
            "Itty Bitty", "Tiny", "Small", "Regular", "Large", "Ginormous"
    };

    // ── Size descriptions (indexed by GlobeWorldSize.ordinal()) ──
    private static final String[] SIZE_DESCRIPTIONS = {
            "A pocket world. Every horizon feels close.",
            "Compact but complete. Good for focused journeys.",
            "Room to roam. Familiar landmarks within reach.",
            "The standard expedition. A full planet awaits.",
            "Vast distances. Bring supplies.",
            "A world that could take a lifetime to cross."
    };

    private static final Text SMALL_WORLD_WARNING = Text.literal(
            "Smaller world sizes are still being tuned and may distort biome distribution."
    ).formatted(Formatting.ITALIC, Formatting.GOLD);

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
    private final GeneratorOptionsHolder holder;

    // ── Local UI state (fresh each open) ──
    private GlobeWorldSize selectedSize = DEFAULT_SIZE;
    private LatitudeBands.Band selectedZone = LatitudeBands.Band.TEMPERATE;
    private int selectedModeIdx = 0;  // 0=Survival, 1=Hardcore, 2=Creative
    private boolean allowCommands = false;
    private boolean startWithCompass = true;
    private boolean bonusChest = false;
    private int worldTypeIdx = 0;  // 0=Latitude, 1=Vanilla, 2=Vanilla Superflat
    private GameRules gameRules;

    private String worldNameInput = "New World";
    private TextFieldWidget worldNameField;
    private TextFieldWidget seedField;
    private ButtonWidget sizePrevBtn;
    private ButtonWidget sizeNextBtn;
    private final List<ZoneRowWidget> zoneRows = new ArrayList<>();

    // ── Settings rail toggle buttons (need message updates) ──
    private ButtonWidget commandsBtn;
    private ButtonWidget compassBtn;
    private ButtonWidget bonusChestBtn;
    private ButtonWidget worldTypePrevBtn;
    private ButtonWidget worldTypeNextBtn;
    private ButtonWidget modePrevBtn;
    private ButtonWidget modeNextBtn;
    private ButtonWidget gameRulesBtn;
    private ButtonWidget hudStudioBtn;

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
    private int bonusChestRowY;
    private int gameRulesRowY;

    // ── Tabbed fallback mode (activates when 3-col doesn't fit) ──
    private boolean tabbedMode;
    private int activeTab; // 0=World, 1=Spawn Zone, 2=Rules
    private static final String[] TAB_LABELS = {"World", "Spawn Zone", "Rules"};
    private static final int TAB_H = 20;
    private static final int TAB_GAP = 4;
    private int tabStripY;
    private int tabPanelTop; // content area top (below tab strip)
    private long debugSwitchSampleDeadlineMs;
    private int debugSwitchSeq;

    private LatitudeCreateWorldScreen(Runnable onClose, @Nullable Screen parent, GeneratorOptionsHolder holder) {
        super(Text.literal("New Expedition"));
        this.onClose = onClose;
        this.parent = parent;
        this.holder = holder;
        this.gameRules = new GameRules(holder.dataConfiguration().enabledFeatures());
    }

    public static void openLoaded(MinecraftClient client, Runnable onClose, @Nullable Screen parent, GeneratorOptionsHolder holder) {
        client.setScreen(new LatitudeCreateWorldScreen(onClose, parent, holder));
    }

    /**
     * Phase 5A: Load datapacks (vanilla "Preparing..." screen), then open the bespoke screen.
     * Replicates CreateWorldScreen.show() lines 166-196.
     */
    public static void open(MinecraftClient client, Runnable onClose, @Nullable Screen parent) {
        // Show "Preparing..." message (vanilla pattern)
        client.setScreenAndRender(new MessageScreen(Text.translatable("createWorld.preparing")));

        try {
            // Build datapack configuration (replicates createServerConfig, lines 511-513)
            ResourcePackManager resourcePackManager = new ResourcePackManager(new VanillaDataPackProvider(client.getSymlinkFinder()));
            resourcePackManager.scanPacks();
            List<String> enabledPackIds = SharedConstants.isDevelopment
                    ? List.of("vanilla", "tests", "globe")
                    : List.of("vanilla", "globe");
            resourcePackManager.setEnabledProfiles(enabledPackIds);
            DataConfiguration dataConfiguration = SharedConstants.isDevelopment
                    ? new DataConfiguration(new DataPackSettings(enabledPackIds, List.of()), FeatureFlags.DEFAULT_ENABLED_FEATURES)
                    : new DataConfiguration(new DataPackSettings(enabledPackIds, List.of()), FeatureFlags.DEFAULT_ENABLED_FEATURES);
            SaveLoading.DataPacks dataPacks = new SaveLoading.DataPacks(resourcePackManager, dataConfiguration, false, true);
            SaveLoading.ServerConfig serverConfig = new SaveLoading.ServerConfig(
                    dataPacks, CommandManager.RegistrationEnvironment.INTEGRATED, LeveledPermissionPredicate.GAMEMASTERS);

            // Generator options factory (replicates lines 131-133)
            GeneratorOptionsFactory generatorOptionsFactory = (dataPackContents, dynamicRegistries, settings) ->
                    new GeneratorOptionsHolder(settings.worldGenSettings(), dynamicRegistries, dataPackContents, settings.dataConfiguration());

            // Load datapacks async, block render thread (replicates lines 180-192)
            CompletableFuture<GeneratorOptionsHolder> future = SaveLoading.load(
                    serverConfig,
                    context -> new SaveLoading.LoadContext<>(
                            new WorldCreationSettings(
                                    new WorldGenSettings(GeneratorOptions.createRandom(), WorldPresets.createDemoOptions(context.worldGenRegistryManager())),
                                    context.dataConfiguration()),
                            context.dimensionsRegistryManager()),
                    (resourceManager, dataPackContents, dynamicRegistries, settings) -> {
                        resourceManager.close();
                        return generatorOptionsFactory.apply(dataPackContents, dynamicRegistries, settings);
                    },
                    Util.getMainWorkerExecutor(),
                    client);

            client.runTasks(future::isDone);

            // Open the bespoke screen with the loaded holder
            client.setScreen(new LatitudeCreateWorldScreen(onClose, parent, future.join()));
        } catch (Exception e) {
            LOGGER.error("Failed to load datapacks for Latitude create-world screen", e);
            // 5A error path: return to caller screen, never show bespoke screen
            onClose.run();
            if (client.currentScreen == null || client.currentScreen instanceof MessageScreen) {
                client.setScreen(parent);
            }
        }
    }

    private boolean isCompact() {
        return this.width < 480;
    }

    @Override
    protected void init() {
        zoneRows.clear();
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
        int minThreeColWidth = MIN_LEFT_W + MIN_RIGHT_W + MIN_RAIL_W + paneGap * 2;
        tabbedMode = paneStripViewportWidth < minThreeColWidth;
        threeCol = !tabbedMode;
        if (tabbedMode) {
            tabStripY = panelTop;
            tabPanelTop = tabStripY + TAB_H + TAB_GAP;
            panelTop = tabPanelTop;
            leftW = paneStripContentWidth;
            rightW = paneStripContentWidth;
            railW = paneStripContentWidth;
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
        this.worldNameField = new TextFieldWidget(this.textRenderer, inputX, worldFieldY, inputW, fieldH, Text.literal("World Name"));
        this.worldNameField.setMaxLength(64);
        this.worldNameField.setText(worldNameInput);
        this.worldNameField.setChangedListener(text -> worldNameInput = text);
        this.addDrawableChild(this.worldNameField);

        // ── 2. Seed ──
        seedFieldY = worldFieldY + fieldGap1;
        this.seedField = new TextFieldWidget(this.textRenderer, inputX, seedFieldY, inputW, fieldH, Text.literal("Seed"));
        this.seedField.setMaxLength(64);
        this.seedField.setPlaceholder(Text.literal("Leave blank for random"));
        this.addDrawableChild(this.seedField);

        // ── 3. Size ◀ ──
        sizeFieldY = seedFieldY + fieldGap2;
        sizePrevBtn = ButtonWidget.builder(Text.literal("\u25C0"), b -> cycleSize(-1))
                .dimensions(inputX, sizeFieldY, stepperBtnW, btnH)
                .build();
        this.addDrawableChild(sizePrevBtn);

        // ── 4. Size ▶ ──
        sizeNextBtn = ButtonWidget.builder(Text.literal("\u25B6"), b -> cycleSize(1))
                .dimensions(inputX + inputW - stepperBtnW, sizeFieldY, stepperBtnW, btnH)
                .build();
        this.addDrawableChild(sizeNextBtn);
        updateLeftWidgets(inputX, inputW, fieldH, btnH, stepperBtnW);

        inputBottomY = Math.max(sizeFieldY + btnH, computeSizeLabelBottom(sizeFieldY - 1, inputW - 48)) + scaledUi(12);
        updateLeftLayout();

        zoneRowHeight = computeZoneRowHeight(rightW - 4);
        zoneRowStep = zoneRowHeight + scaledUi(6);
        for (LatitudeBands.Band band : LatitudeBands.Band.values()) {
            ZoneRowWidget row = new ZoneRowWidget(rightX + 2, panelTop, rightW - 4, zoneRowHeight, band);
            zoneRows.add(row);
            this.addDrawableChild(row);
        }
        updateRightLayout();

        {
            int settBtnW = railW - 8;
            int settBtnX = railX + 4;

            worldTypePrevBtn = ButtonWidget.builder(Text.literal("\u25C0"), b -> cycleWorldType(-1))
                    .dimensions(settBtnX, panelTop, 20, btnH)
                    .build();
            worldTypeNextBtn = ButtonWidget.builder(Text.literal("\u25B6"), b -> cycleWorldType(1))
                    .dimensions(settBtnX + settBtnW - 20, panelTop, 20, btnH)
                    .build();
            modePrevBtn = ButtonWidget.builder(Text.literal("\u25C0"), b -> cycleMode(-1))
                    .dimensions(settBtnX, panelTop, 20, btnH)
                    .build();
            modeNextBtn = ButtonWidget.builder(Text.literal("\u25B6"), b -> cycleMode(1))
                    .dimensions(settBtnX + settBtnW - 20, panelTop, 20, btnH)
                    .build();
            commandsBtn = ButtonWidget.builder(Text.literal(allowCommands ? "ON" : "OFF"), b -> {
                allowCommands = !allowCommands;
                b.setMessage(Text.literal(allowCommands ? "ON" : "OFF"));
            }).dimensions(settBtnX, panelTop, settBtnW, btnH).build();
            this.addDrawableChild(worldTypePrevBtn);
            this.addDrawableChild(worldTypeNextBtn);
            this.addDrawableChild(modePrevBtn);
            this.addDrawableChild(modeNextBtn);
            this.addDrawableChild(commandsBtn);

            compassBtn = ButtonWidget.builder(Text.literal(startWithCompass ? "ON" : "OFF"), b -> {
                startWithCompass = !startWithCompass;
                b.setMessage(Text.literal(startWithCompass ? "ON" : "OFF"));
            }).dimensions(settBtnX, panelTop, settBtnW, btnH).build();
            this.addDrawableChild(compassBtn);

            bonusChestBtn = ButtonWidget.builder(Text.literal(bonusChest ? "ON" : "OFF"), b -> {
                bonusChest = !bonusChest;
                b.setMessage(Text.literal(bonusChest ? "ON" : "OFF"));
            }).dimensions(settBtnX, panelTop, settBtnW, btnH).build();
            this.addDrawableChild(bonusChestBtn);

            gameRulesBtn = ButtonWidget.builder(Text.literal("Game Rules..."), b -> openGameRules())
                    .dimensions(settBtnX, panelTop, settBtnW, btnH)
                    .build();
            this.addDrawableChild(gameRulesBtn);

            hudStudioBtn = ButtonWidget.builder(Text.literal("HUD Studio"), b -> openHudStudio())
                    .dimensions(settBtnX, panelTop, settBtnW, btnH)
                    .build();
            this.addDrawableChild(hudStudioBtn);
            updateSettingsLayout();
        }

        if (tabbedMode) {
            applyTabbedVisibility();
        }

        // ── 17. Begin Expedition ──
        int btnSpacing = scaledUi(8);
        int beginW = Math.max(120, this.textRenderer.getWidth("Begin Expedition") + 20);
        int cancelW = Math.max(70, this.textRenderer.getWidth("Cancel") + 20);
        int totalBtnW = beginW + btnSpacing + cancelW;
        int btnStartX = cx - totalBtnW / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Begin Expedition"), b -> beginExpedition())
                .dimensions(btnStartX, bottomY, beginW, btnH)
                .build());

        // ── 18. Cancel ──
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(btnStartX + beginW + btnSpacing, bottomY, cancelW, btnH)
                .build());

        // ── Focus: pre-select world name text for immediate overwrite ──
        this.worldNameField.setFocused(true);
        this.setFocused(this.worldNameField);
        this.worldNameField.setCursorToEnd(false);
        this.worldNameField.setSelectionEnd(0);
    }

    // ── Size stepper ──

    private void cycleSize(int delta) {
        GlobeWorldSize[] sizes = GlobeWorldSize.values();
        int idx = selectedSize.ordinal() + delta;
        if (idx < 0) idx = sizes.length - 1;
        if (idx >= sizes.length) idx = 0;
        selectedSize = sizes[idx];
        if (this.worldNameField != null) {
            worldNameInput = this.worldNameField.getText();
        }
        this.clearAndInit();
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
        long t0 = Util.getMeasuringTimeMs();
        worldTypeIdx = (worldTypeIdx + delta + WORLD_TYPE_NAMES.length) % WORLD_TYPE_NAMES.length;
        updateSettingsButtons();
        updateRightLayout();
        if (DEBUG_UI_SWITCH_LAG) {
            debugSwitchSeq++;
            debugSwitchSampleDeadlineMs = t0 + 2_000L; // sample for 2s after switch
            long elapsed = Util.getMeasuringTimeMs() - t0;
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
        return this.textRenderer.fontHeight;
    }

    private int uiTextWidth(String text) {
        return this.textRenderer.getWidth(text);
    }

    private List<net.minecraft.text.StringVisitable> wrapUiLines(String text, int width) {
        return this.textRenderer.getTextHandler().wrapLines(text, Math.max(1, width), net.minecraft.text.Style.EMPTY);
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
        return wrapLineCount(SMALL_WORLD_WARNING.getString(), Math.max(40, width)) * uiFontHeight();
    }

    private int computeZoneListTop() {
        return panelTop + scaledUi(22) + wrappedTextHeight("Choose the climate where your journey begins", Math.max(80, rightW - scaledUi(20) - SCROLLBAR_GUTTER)) + scaledUi(10);
    }

    private int computeZoneRowHeight(int rowWidth) {
        int maxRangeW = 0;
        for (LatitudeBands.Band band : LatitudeBands.Band.values()) {
            String range = formatDegree(band.lowDeg()) + "–" + formatDegree(band.highDeg());
            maxRangeW = Math.max(maxRangeW, this.textRenderer.getWidth(range));
        }
        int helperWidth = Math.max(60, rowWidth - 12 - maxRangeW - 10);
        int maxHelperLines = 1;
        for (String helper : ZONE_HELPER) {
            maxHelperLines = Math.max(maxHelperLines, wrapLineCount(helper, helperWidth));
        }
        return scaledUi(4) + uiFontHeight() + scaledUi(2) + maxHelperLines * uiFontHeight() + scaledUi(4);
    }

    private int getPaneStripMaxScroll() {
        return Math.max(0, paneStripContentWidth - paneStripViewportWidth);
    }

    private void updatePaneStripLayout() {
        if (tabbedMode) {
            // All panels overlap at the same position in tabbed mode
            leftX = paneStripViewportLeft;
            rightX = paneStripViewportLeft;
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
            worldNameField.setDimensionsAndPosition(inputW, fieldH, inputX, worldFieldY);
            worldNameField.visible = true;
            worldNameField.active = true;
        }
        if (seedField != null) {
            seedField.setDimensionsAndPosition(inputW, fieldH, inputX, seedFieldY);
            seedField.visible = true;
            seedField.active = true;
        }
        if (sizePrevBtn != null) {
            sizePrevBtn.setDimensionsAndPosition(stepperBtnW, btnH, inputX, sizeFieldY);
            sizePrevBtn.visible = true;
            sizePrevBtn.active = true;
        }
        if (sizeNextBtn != null) {
            sizeNextBtn.setDimensionsAndPosition(stepperBtnW, btnH, inputX + inputW - stepperBtnW, sizeFieldY);
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
        int contentTop = panelTop + scaledUi(8);
        int labelFieldGap = scaledUi(22);
        int fieldGap1 = scaledUi(38);
        int fieldGap2 = scaledUi(40);
        int discGap = scaledUi(6);
        int previewHeight = Math.max(scaledUi(150), Math.min(leftW - scaledUi(20) - SCROLLBAR_GUTTER, Math.max(scaledUi(170), panelBottom - panelTop - scaledUi(80))));
        int baseWorldY = contentTop + labelFieldGap;
        int baseSeedY = baseWorldY + fieldGap1;
        int warningHeight = shouldShowSmallWorldWarning() ? getSmallWorldWarningHeight(inputW) : 0;
        int warningGap = warningHeight > 0 ? scaledUi(4) : 0;
        int baseSizeY = baseSeedY + fieldGap2 + warningHeight + warningGap;
        int baseInputBottom = Math.max(baseSizeY + btnH, computeSizeLabelBottom(baseSizeY - 1, inputW - stepperBtnW * 2 - scaledUi(8))) + scaledUi(12);
        int basePreviewTop = baseInputBottom + discGap + uiFontHeight();
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

    private void updateLeftWidgetVisibility(ClickableWidget widget) {
        if (widget == null) return;
        boolean visible = (!tabbedMode || activeTab == 0)
                && widget.getX() >= paneStripViewportLeft
                && widget.getX() + widget.getWidth() <= paneStripViewportRight
                && widget.getY() >= leftViewportTop
                && widget.getY() + widget.getHeight() <= leftViewportBottom;
        widget.visible = visible;
        widget.active = visible;
    }

    private void updateRightLayout() {
        int contentTop = panelTop + scaledUi(8);
        int titleBlockHeight = uiFontHeight() + scaledUi(4);
        int subtitleWidth = Math.max(80, rightW - scaledUi(28) - SCROLLBAR_GUTTER);
        int subtitleHeight = wrappedTextHeight("Choose the climate where your journey begins", subtitleWidth);
        int descTextWidth = Math.max(60, rightW - 16 - SCROLLBAR_GUTTER);
        String spawnLine = "You will spawn between " + formatDegree(selectedZone.lowDeg()) + "–" + formatDegree(selectedZone.highDeg()) + " latitude. " + ZONE_HELPER[selectedZone.ordinal()] + ".";
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
            row.setDimensionsAndPosition(rightW - 4 - SCROLLBAR_GUTTER, zoneRowHeight, rightX + 2, zoneY);
            boolean visible = isLatitudeWorld()
                    && (!tabbedMode || activeTab == 1)
                    && row.getX() >= paneStripViewportLeft
                    && row.getX() + row.getWidth() <= paneStripViewportRight
                    && zoneY >= rightViewportTop
                    && zoneY + zoneRowHeight <= rightViewportBottom;
            row.visible = visible;
            row.active = visible;
            zoneY += zoneRowStep;
        }
    }

    private void updateSettingsButtons() {
        if (commandsBtn != null) {
            commandsBtn.setMessage(Text.literal(allowCommands ? "ON" : "OFF"));
            commandsBtn.active = commandsBtn.visible;
        }
        if (compassBtn != null) {
            compassBtn.setMessage(Text.literal(startWithCompass ? "ON" : "OFF"));
            compassBtn.active = compassBtn.visible && isLatitudeWorld();
        }
        if (bonusChestBtn != null) {
            bonusChestBtn.setMessage(Text.literal(bonusChest ? "ON" : "OFF"));
            bonusChestBtn.active = !isLatitudeWorld() && bonusChestBtn.visible;
        }
        if (gameRulesBtn != null) {
            gameRulesBtn.active = gameRulesBtn.visible;
        }
        if (hudStudioBtn != null) {
            hudStudioBtn.active = hudStudioBtn.visible;
        }
    }

    private void updateSettingsLayout() {
        if (worldTypePrevBtn == null || worldTypeNextBtn == null || modePrevBtn == null || modeNextBtn == null || commandsBtn == null || compassBtn == null || bonusChestBtn == null || gameRulesBtn == null || hudStudioBtn == null) {
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
        settingsViewportTop = panelTop + scaledUi(36);
        settingsViewportBottom = panelBottom - scaledUi(8);
        int viewportHeight = Math.max(0, settingsViewportBottom - settingsViewportTop);
        int contentTop = settingsViewportTop + scaledUi(4);
        int blockHeight = labelGap + btnH;
        settingsContentHeight = blockHeight * 7 + rowGap * 6;
        int maxScroll = Math.max(0, settingsContentHeight - viewportHeight);
        if (settingsScroll < 0) settingsScroll = 0;
        if (settingsScroll > maxScroll) settingsScroll = maxScroll;

        int y = contentTop - settingsScroll + labelGap;
        worldTypeRowY = y;
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
        bonusChestRowY = y;
        positionSettingsButton(bonusChestBtn, settBtnX, settBtnW, y, btnH);
        y += btnH + rowGap + labelGap;
        gameRulesRowY = y;
        positionSettingsButton(gameRulesBtn, settBtnX, settBtnW, y, btnH);

        y += btnH + rowGap + labelGap;
        positionSettingsButton(hudStudioBtn, settBtnX, settBtnW, y, btnH);

        updateSettingsButtons();
    }

    private void applyTabbedVisibility() {
        if (!tabbedMode) return;
        // Tab 0 = World (left panel widgets)
        boolean showWorld = activeTab == 0;
        setTabbedWidgetVisible(worldNameField, showWorld);
        setTabbedWidgetVisible(seedField, showWorld);
        setTabbedWidgetVisible(sizePrevBtn, showWorld);
        setTabbedWidgetVisible(sizeNextBtn, showWorld);
        // Tab 1 = Spawn Zone (right panel widgets)
        boolean showZone = activeTab == 1 && isLatitudeWorld();
        for (ZoneRowWidget row : zoneRows) {
            setTabbedWidgetVisible(row, showZone);
        }
        // Tab 2 = Rules (settings rail widgets)
        boolean showRules = activeTab == 2;
        setTabbedWidgetVisible(worldTypePrevBtn, showRules);
        setTabbedWidgetVisible(worldTypeNextBtn, showRules);
        setTabbedWidgetVisible(modePrevBtn, showRules);
        setTabbedWidgetVisible(modeNextBtn, showRules);
        setTabbedWidgetVisible(commandsBtn, showRules);
        setTabbedWidgetVisible(compassBtn, showRules);
        setTabbedWidgetVisible(bonusChestBtn, showRules && !isLatitudeWorld());
        setTabbedWidgetVisible(gameRulesBtn, showRules);
        setTabbedWidgetVisible(hudStudioBtn, showRules);
    }

    private void setTabbedWidgetVisible(ClickableWidget widget, boolean visible) {
        if (widget == null) return;
        widget.visible = visible;
        widget.active = visible;
    }

    private void switchTab(int tab) {
        if (tab == activeTab) return;
        activeTab = tab;
        applyTabbedVisibility();
    }

    private void positionSettingsStepper(ButtonWidget left, ButtonWidget right, int x, int width, int y, int height) {
        int stepperW = left.getWidth();
        left.setDimensionsAndPosition(stepperW, height, x, y);
        right.setDimensionsAndPosition(stepperW, height, x + width - stepperW, y);
        boolean visible = (!tabbedMode || activeTab == 2)
                && left.getX() >= paneStripViewportLeft
                && right.getX() + right.getWidth() <= paneStripViewportRight
                && y >= settingsViewportTop
                && y + height <= settingsViewportBottom;
        left.visible = visible;
        right.visible = visible;
        left.active = visible;
        right.active = visible;
    }

    private void positionSettingsButton(ButtonWidget button, int x, int width, int y, int height) {
        button.setDimensionsAndPosition(width - SCROLLBAR_GUTTER, height, x, y);
        boolean visible = (!tabbedMode || activeTab == 2)
                && button.getX() >= paneStripViewportLeft
                && button.getX() + button.getWidth() <= paneStripViewportRight
                && y >= settingsViewportTop
                && y + height <= settingsViewportBottom;
        button.visible = visible;
        button.active = visible;
    }

    private void openGameRules() {
        if (this.client == null) return;
        this.client.setScreen(new EditGameRulesScreen(this.gameRules, optional -> {
            optional.ifPresent(rules -> this.gameRules = rules);
            this.client.setScreen(this);
        }));
    }

    private void openHudStudio() {
        if (this.client == null) return;
        this.client.setScreen(new LatitudeHudStudioScreen(this));
    }

    // ── Begin Expedition ──

    private void beginExpedition() {
        if (this.client == null) return;
        String worldName = this.worldNameField.getText().trim();
        if (worldName.isEmpty()) worldName = "New World";
        String seed = this.seedField.getText(); // raw — no client-side trim

        GameMode gameMode = selectedModeIdx == 2 ? GameMode.CREATIVE : GameMode.SURVIVAL;
        boolean hardcore = selectedModeIdx == 1;
        Difficulty difficulty = hardcore ? Difficulty.HARD : Difficulty.NORMAL;

        LatitudeWorldLauncher.beginExpedition(this.client, this, this.holder,
                worldName, seed, this.selectedSize, this.selectedZone,
                gameMode, hardcore, difficulty, allowCommands, startWithCompass, bonusChest,
                this.gameRules, this.worldTypeIdx);
    }

    // ── Close behavior ──

    @Override
    public void close() {
        this.onClose.run();
        if (this.client != null && (this.client.currentScreen == this || this.client.currentScreen == null)) {
            this.client.setScreen(this.parent);
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
        if ((!tabbedMode || activeTab == 1) && mouseX >= Math.max(rightX, paneStripViewportLeft) && mouseX < Math.min(rightX + rightW, paneStripViewportRight) && mouseY >= panelTop && mouseY < panelBottom) {
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
        if ((!tabbedMode || activeTab == 2) && mouseX >= Math.max(railX, paneStripViewportLeft) && mouseX < Math.min(railX + railW, paneStripViewportRight) && mouseY >= panelTop && mouseY < panelBottom) {
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
    public boolean mouseClicked(Click click, boolean doubled) {
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
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingPaneStripScrollbar && click.button() == 0) {
            setPaneStripScrollFromMouse(click.x());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int titlePaneX = threeCol ? rightX : 12;
        int titlePaneW = threeCol ? rightW : Math.max(1, this.width - 24);
        int headerBottom = tabbedMode ? tabStripY - 2 : panelTop;
        UiRect headerRect = new UiRect(titlePaneX, headerY, Math.max(1, titlePaneW), Math.max(1, headerBottom - headerY - 6));
        int headerLineY = headerRect.y;
        if (drawCenteredBoundedText(context, "LATITUDE", new UiRect(headerRect.x, headerLineY, headerRect.w, uiFontHeight()), GOLD, true, false)) {
            headerLineY += uiFontHeight() + scaledUi(6);
        }
        if (drawCenteredBoundedText(context, "New Expedition", new UiRect(headerRect.x, headerLineY, headerRect.w, uiFontHeight()), WARM_WHITE, true, false)) {
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
        if (leftClipRight > leftClipLeft) {
            context.enableScissor(leftClipLeft, leftViewportTop, leftClipRight, leftViewportBottom);
        if (threeCol) {
            drawInlineHeading(context, leftX, leftW, TAB_LABELS[0], GOLD);
        }
        int inputX = leftX + 4;
        int stepperBtnW = sizePrevBtn != null ? sizePrevBtn.getWidth() : 20;
        int labelColor = GOLD;
        int labelOff = scaledUi(10);
        int leftTextWidth = Math.max(24, leftW - 8 - SCROLLBAR_GUTTER);
        if (shouldShowSmallWorldWarning()) {
            int warningHeight = getSmallWorldWarningHeight(leftTextWidth);
            int warningY = sizeFieldY - labelOff - warningHeight - scaledUi(2);
            drawWrappedStyledTextBlock(context, SMALL_WORLD_WARNING, new UiRect(inputX, warningY, leftTextWidth, warningHeight), 0xFFF0A030, false, 2, true, true);
        }
        drawBoundedText(context, "World Name", new UiRect(inputX, worldFieldY - labelOff, leftTextWidth, uiFontHeight()), labelColor, false, false);
        drawBoundedText(context, "Seed", new UiRect(inputX, seedFieldY - labelOff, leftTextWidth, uiFontHeight()), labelColor, false, false);
        drawBoundedText(context, "World Size", new UiRect(inputX, sizeFieldY - labelOff, leftTextWidth, uiFontHeight()), labelColor, false, false);
        renderSizeLabel(context, inputX + stepperBtnW + scaledUi(4), sizeFieldY - 1, leftTextWidth - stepperBtnW * 2 - scaledUi(8));
        int separatorY = inputBottomY - 2;
        context.fill(leftX + 4, separatorY, leftX + leftW - 4 - SCROLLBAR_GUTTER, separatorY + 1, PANEL_BORDER);
        boolean latWorld = isLatitudeWorld();
        int planisphereLabelY = leftPreviewTopY - uiFontHeight() - scaledUi(6);
        drawCenteredBoundedText(context, "PLANISPHERE", new UiRect(leftX + 4, planisphereLabelY, leftTextWidth, uiFontHeight()), latWorld ? GOLD : DISABLED_COLOR, false, true);
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

        if (!tabbedMode || activeTab == 1) {
        drawViewportClippedPanel(context, rightX, panelTop, rightW, panelBottom - panelTop);
        int paneTitleY = panelTop + scaledUi(8);
        int rightClipLeft = Math.max(rightX + 1, paneStripViewportLeft);
        int rightClipRight = Math.min(rightX + rightW - 1, paneStripViewportRight);
        if (rightClipRight > rightClipLeft) {
        context.enableScissor(rightClipLeft, rightViewportTop, rightClipRight, rightViewportBottom);
        boolean latWorld = isLatitudeWorld();
        int rightTextWidth = Math.max(40, rightW - 8 - SCROLLBAR_GUTTER);
        if (threeCol) {
            drawInlineHeading(context, rightX, rightW, TAB_LABELS[1], latWorld ? GOLD : DISABLED_COLOR);
        } else {
        drawCenteredBoundedText(context, "Spawn Zone", new UiRect(rightX + 4, paneTitleY, rightTextWidth, uiFontHeight()), latWorld ? GOLD : DISABLED_COLOR, false, false);
        drawWrappedTextBlock(context, "Choose the climate where your journey begins", new UiRect(rightX + 4, rightSubtitleY, rightTextWidth, Math.max(uiFontHeight(), rightDividerY - rightSubtitleY - scaledUi(2))), latWorld ? MUTED : DISABLED_COLOR, false, 2, true, true);
        }
        if (latWorld) {
        int barInset = 4;
        int barTotalW = rightW - barInset * 2 - SCROLLBAR_GUTTER;
        LatitudeBands.Band[] allBands = LatitudeBands.Band.values();
        for (int i = 0; i < allBands.length; i++) {
            int segX = rightX + barInset + (barTotalW * i / allBands.length);
            int segXEnd = rightX + barInset + (barTotalW * (i + 1) / allBands.length);
            boolean sel = allBands[i] == selectedZone;
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
        String spawnLine = "You will spawn between " + formatDegree(selectedZone.lowDeg()) + "\u2013" + formatDegree(selectedZone.highDeg()) + " latitude. " + ZONE_HELPER[selectedZone.ordinal()] + ".";
        if (rightDescPanelH > scaledUi(24)) {
            context.fill(descPanelX, rightDescPanelY, descPanelX + descPanelW, rightDescPanelY + rightDescPanelH, PANEL_BG);
            int sideColor = BAND_COLORS[selectedZone.ordinal()];
            context.fill(descPanelX, rightDescPanelY, descPanelX + 2, rightDescPanelY + rightDescPanelH, sideColor);

            int textX = descPanelX + 6;
            int ty = rightDescPanelY + scaledUi(3);
            String zoneHeader = selectedZone.displayName() + " zone selected";
            drawBoundedText(context, zoneHeader, new UiRect(textX, ty, textMaxW, uiFontHeight()), GOLD, true, true);
            ty += uiFontHeight() + scaledUi(5);
            ty += drawWrappedTextBlock(context, spawnLine, new UiRect(textX, ty, textMaxW, Math.max(0, rightDescPanelY + rightDescPanelH - ty - uiFontHeight() - scaledUi(4))), WARM_WHITE, false, 3, false, true);
            ty += scaledUi(4);
            if (ty + uiFontHeight() <= rightDescPanelY + rightDescPanelH) {
                drawBoundedText(context, "Climate: " + ZONE_CLIMATE[selectedZone.ordinal()], new UiRect(textX, ty, textMaxW, uiFontHeight()), MUTED, false, true);
            }
        }
        } else {
            renderSpawnZoneDisabled(context);
        }
        context.disableScissor();
        }
        drawPaneScrollbar(context, rightX, rightW, rightViewportTop, rightViewportBottom, rightContentHeight, rightScroll);
        } // end tab 1 (Spawn Zone)

        if (!tabbedMode || activeTab == 2) {
            updateSettingsLayout();
            drawViewportClippedPanel(context, railX, panelTop, railW, panelBottom - panelTop);
            int settLabelX = railX + 4;
            int railClipLeft = Math.max(railX + 1, paneStripViewportLeft);
            int railClipRight = Math.min(railX + railW - 1, paneStripViewportRight);
            if (railClipRight > railClipLeft) {
            if (threeCol) {
                drawInlineHeading(context, railX, railW, TAB_LABELS[2], GOLD);
            }
            context.enableScissor(railClipLeft, settingsViewportTop, railClipRight, settingsViewportBottom);
            int railTextWidth = Math.max(40, railW - 8 - SCROLLBAR_GUTTER);
            if (!threeCol) {
                drawCenteredBoundedText(context, "EXPEDITION", new UiRect(railX + 4, panelTop + scaledUi(4), railTextWidth, uiFontHeight()), GOLD, false, true);
                drawCenteredBoundedText(context, "SETTINGS", new UiRect(railX + 4, panelTop + scaledUi(14), railTextWidth, uiFontHeight()), GOLD, false, true);
            }
            drawSettingsRowLabel(context, "World Type", settLabelX, worldTypeRowY, MUTED);
            drawSettingsStepperValue(context, WORLD_TYPE_NAMES[worldTypeIdx], WORLD_TYPE_COLORS[worldTypeIdx], worldTypeRowY);
            drawSettingsRowLabel(context, "Game Mode", settLabelX, modeRowY, MUTED);
            drawSettingsStepperValue(context, MODE_NAMES[selectedModeIdx], MODE_COLORS[selectedModeIdx], modeRowY);
            drawSettingsRowLabel(context, "Commands", settLabelX, commandsRowY, MUTED);
            drawSettingsRowLabel(context, "Starting Compass", settLabelX, compassRowY, isLatitudeWorld() ? MUTED : DISABLED_COLOR);
            drawSettingsRowLabel(context, "Bonus Chest", settLabelX, bonusChestRowY, isLatitudeWorld() ? DISABLED_COLOR : MUTED);
            drawSettingsRowLabel(context, "Game Rules", settLabelX, gameRulesRowY, MUTED);
            context.disableScissor();
            }
            drawPaneScrollbar(context, railX, railW, settingsViewportTop, settingsViewportBottom, settingsContentHeight, settingsScroll);
        } // end tab 2 (Rules)

        if (!tabbedMode) {
            drawHorizontalScrollbar(context);
        }

        if (isLatitudeWorld() && bonusChestBtn != null && !bonusChestBtn.active && bonusChestBtn.isMouseOver(mouseX, mouseY)) {
            context.drawTooltip(Text.literal("Bonus chest is not available in Latitude."), mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderSizeLabel(DrawContext context, int x, int y, int availW) {
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

    private void renderPlanispherePreview(DrawContext context, int areaLeft, int areaTop, int areaRight, int areaBottom) {
        long dbgStart = DEBUG_UI_SWITCH_LAG ? Util.getMeasuringTimeMs() : 0L;
        String caption = formatDiameter(selectedSize.borderRadiusBlocks * 2) + " blocks";
        float labelScale = previewLabelScale(selectedSize);
        float captionScale = previewCaptionScale(selectedSize);
        int labelHeight = scaledFontHeight(labelScale);
        int captionHeight = scaledFontHeight(captionScale);
        int maxLabelWidth = 0;
        for (double deg : PREVIEW_LABEL_DEGREES) {
            maxLabelWidth = Math.max(maxLabelWidth, scaledTextWidth(formatDegree(deg), labelScale));
        }

        int labelPad = isTinyPreview(selectedSize) ? scaledUi(10) : scaledUi(8);
        int rightPadding = scaledUi(8);
        int captionGap = Math.max(6, captionHeight / 2);
        int maxRadiusByWidth = Math.max(18, (areaRight - areaLeft - maxLabelWidth - labelPad - rightPadding) / 2);
        int maxRadiusByHeight = Math.max(18, (areaBottom - areaTop - captionHeight - captionGap) / 2);
        int radius = Math.round(Math.min(maxRadiusByWidth, maxRadiusByHeight) * previewDiscFill(selectedSize));
        radius = Math.max(18, radius);

        PreviewLayout layout = null;
        while (radius >= 18) {
            layout = computePreviewLayout(areaLeft, areaTop, areaRight, areaBottom, radius, labelScale, captionScale, caption);
            if (layout != null) {
                break;
            }
            radius -= 2;
        }
        if (layout == null) {
            return;
        }

        LatitudePlanisphereRenderer.renderCompact(context,
                layout.globeLeft,
                layout.globeTop,
                layout.globeDiameter,
                selectedZone);

        for (int i = 0; i < PREVIEW_LABEL_DEGREES.length; i++) {
            double deg = PREVIEW_LABEL_DEGREES[i];
            int color = isOnSelectedEdge(deg, selectedZone) ? GOLD : MUTED;
            drawScaledText(context, formatDegree(deg), layout.labelX, layout.labelYs[i], layout.labelScale, color, false);
        }
        drawScaledText(context, caption, layout.captionX, layout.captionY, layout.captionScale, MUTED, false);
        if (DEBUG_UI_SWITCH_LAG && Util.getMeasuringTimeMs() <= debugSwitchSampleDeadlineMs) {
            long elapsed = Util.getMeasuringTimeMs() - dbgStart;
            if (elapsed >= 1L) {
                LOGGER.info("[lat-ui] switchLag seq={} worldType={} section=planispherePreview ms={}", debugSwitchSeq, currentWorldTypeName(), elapsed);
            }
        }
    }

    private void renderSpawnZoneDisabled(DrawContext context) {
        long dbgStart = DEBUG_UI_SWITCH_LAG ? Util.getMeasuringTimeMs() : 0L;
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
        if (DEBUG_UI_SWITCH_LAG && Util.getMeasuringTimeMs() <= debugSwitchSampleDeadlineMs) {
            long elapsed = Util.getMeasuringTimeMs() - dbgStart;
            if (elapsed >= 1L) {
                LOGGER.info("[lat-ui] switchLag seq={} worldType={} section=spawnZoneDisabled ms={}", debugSwitchSeq, currentWorldTypeName(), elapsed);
            }
        }
    }

    private void renderPlanisphereDisabled(DrawContext context, int areaLeft, int areaTop, int areaRight, int areaBottom) {
        long dbgStart = DEBUG_UI_SWITCH_LAG ? Util.getMeasuringTimeMs() : 0L;
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
        if (DEBUG_UI_SWITCH_LAG && Util.getMeasuringTimeMs() <= debugSwitchSampleDeadlineMs) {
            long elapsed = Util.getMeasuringTimeMs() - dbgStart;
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
        int labelPad = Math.max(8, Math.round(radius * 0.10f));
        int rightPadding = 8;
        int compositionWidth = globeDiameter + labelPad + labelWidth + rightPadding;
        if (compositionWidth > areaRight - areaLeft) {
            return null;
        }

        int captionHeight = scaledFontHeight(captionScale);
        int captionGap = Math.max(6, Math.round(radius * 0.10f));
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
            case ITTY_BITTY -> 0.44f;
            case TINY -> 0.52f;
            case SMALL -> 0.62f;
            case REGULAR -> 0.72f;
            case LARGE -> 0.82f;
            case MASSIVE -> 0.90f;
        };
    }

    private int scaledTextWidth(String text, float scale) {
        return Math.round(this.textRenderer.getWidth(text) * scale);
    }

    private int scaledFontHeight(float scale) {
        return Math.max(5, Math.round(this.textRenderer.fontHeight * scale));
    }

    private void drawSettingsRowLabel(DrawContext context, String label, int x, int rowY, int color) {
        int labelY = rowY - scaledUi(10);
        if (labelY + uiFontHeight() <= settingsViewportTop || labelY >= settingsViewportBottom) {
            return;
        }
        drawBoundedText(context, label, new UiRect(x, labelY, Math.max(20, railW - 8 - SCROLLBAR_GUTTER), uiFontHeight()), color, false, true);
    }

    private void drawSettingsStepperValue(DrawContext context, String text, int color, int rowY) {
        if (rowY + uiFontHeight() <= settingsViewportTop || rowY >= settingsViewportBottom) {
            return;
        }
        int stepperW = worldTypePrevBtn != null ? worldTypePrevBtn.getWidth() : 20;
        int safeLeft = railX + 4 + stepperW + scaledUi(6);
        int safeRight = railX + railW - 4 - stepperW - scaledUi(6) - SCROLLBAR_GUTTER;
        int safeWidth = Math.max(20, safeRight - safeLeft);
        String fitted = ellipsizeToWidth(text, safeWidth);
        int textW = uiTextWidth(fitted);
        int btnH = worldTypePrevBtn != null ? worldTypePrevBtn.getHeight() : 20;
        int drawY = rowY + Math.max(0, (btnH - uiFontHeight()) / 2);
        drawBoundedText(context, fitted, new UiRect(safeLeft + Math.max(0, (safeWidth - textW) / 2), drawY, safeWidth, uiFontHeight()), color, true, true);
    }

    private void drawUiText(DrawContext context, String text, int x, int y, int color, boolean shadow) {
        context.drawText(this.textRenderer, text, x, y, color, shadow);
    }

    private void drawCenteredUiText(DrawContext context, String text, int cx, int y, int color, boolean shadow) {
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
        return this.textRenderer.trimToWidth(text, Math.max(1, width - ellipsisWidth)) + ellipsis;
    }

    private int clampToRect(int value, int contentSize, int min, int maxExclusive) {
        int max = Math.max(min, maxExclusive - contentSize);
        return Math.max(min, Math.min(max, value));
    }

    private boolean drawBoundedText(DrawContext context, String text, UiRect rect, int color, boolean shadow, boolean ellipsize) {
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

    private boolean drawBoundedStyledText(DrawContext context, Text text, UiRect rect, int color, boolean shadow, boolean ellipsize) {
        if (!fitsHeight(rect.h)) {
            return false;
        }
        String fitted = ellipsize ? ellipsizeToWidth(text.getString(), rect.w) : text.getString();
        if (fitted.isEmpty() || (!ellipsize && !fitsWidth(fitted, rect.w))) {
            return false;
        }
        int drawX = clampToRect(rect.x, uiTextWidth(fitted), rect.x, rect.right());
        int drawY = clampToRect(rect.y, uiFontHeight(), rect.y, rect.bottom());
        context.drawText(this.textRenderer, Text.literal(fitted).setStyle(text.getStyle().withItalic(true)), drawX, drawY, color, shadow);
        return true;
    }

    private int drawWrappedStyledTextBlock(DrawContext context, Text text, UiRect rect, int color, boolean shadow, int maxLines, boolean center, boolean optional) {
        if (rect.w <= 0 || rect.h < uiFontHeight()) {
            return 0;
        }
        int maxVisibleLines = Math.min(maxLines, Math.max(1, rect.h / uiFontHeight()));
        List<net.minecraft.text.StringVisitable> wrapped = wrapUiLines(text.getString(), rect.w);
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
            Text lineText = Text.literal(line).setStyle(text.getStyle());
            if (center) {
                drawCenteredBoundedText(context, lineText.getString(), new UiRect(rect.x, y, rect.w, uiFontHeight()), color, shadow, true);
            } else {
                drawBoundedStyledText(context, lineText, new UiRect(rect.x, y, rect.w, uiFontHeight()), color, shadow, true);
            }
            y += uiFontHeight();
        }
        return drawCount * uiFontHeight();
    }

    private boolean drawCenteredBoundedText(DrawContext context, String text, UiRect rect, int color, boolean shadow, boolean ellipsize) {
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

    private int drawWrappedTextBlock(DrawContext context, String text, UiRect rect, int color, boolean shadow, int maxLines, boolean center, boolean optional) {
        if (rect.w <= 0 || rect.h < uiFontHeight()) {
            return 0;
        }
        int maxVisibleLines = Math.min(maxLines, Math.max(1, rect.h / uiFontHeight()));
        List<net.minecraft.text.StringVisitable> wrapped = wrapUiLines(text, rect.w);
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

    private void drawScaledText(DrawContext context, String text, int x, int y, float scale, int color, boolean shadow) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        context.drawText(this.textRenderer, text, 0, 0, color, shadow);
        matrices.popMatrix();
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

    private void drawPanel(DrawContext context, int x, int y, int w, int h) {
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

    private static void drawGridDecoration(DrawContext context, int x, int y, int w, int h) {
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

    private void drawCenteredString(DrawContext context, String text, int cx, int y, int color, boolean shadow) {
        int textW = this.textRenderer.getWidth(text);
        context.drawText(this.textRenderer, text, cx - textW / 2, y, color, shadow);
    }

    private void drawViewportClippedPanel(DrawContext context, int x, int y, int w, int h) {
        int clipLeft = Math.max(x, paneStripViewportLeft);
        int clipRight = Math.min(x + w, paneStripViewportRight);
        if (clipRight <= clipLeft) {
            return;
        }
        context.enableScissor(clipLeft, y, clipRight, y + h);
        drawPanel(context, x, y, w, h);
        context.disableScissor();
    }

    private void drawInlineHeading(DrawContext context, int paneX, int paneW, String label, int labelColor) {
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

    private void drawPaneScrollbar(DrawContext context, int paneX, int paneW, int viewportTop, int viewportBottom,
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

    private void drawTabStrip(DrawContext context, int mouseX, int mouseY) {
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

    private void drawHorizontalScrollbar(DrawContext context) {
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

    private class ZoneRowWidget extends ClickableWidget {
        private final LatitudeBands.Band band;

        ZoneRowWidget(int x, int y, int w, int h, LatitudeBands.Band band) {
            super(x, y, w, h, Text.literal(band.displayName()));
            this.band = band;
        }

        @Override
        public void onClick(net.minecraft.client.gui.Click click, boolean doubled) {
            selectedZone = this.band;
        }

        @Override
        public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
            if (!this.isInteractable()) return false;
            if (input.isEnterOrSpace()) {
                this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                selectedZone = this.band;
                return true;
            }
            return false;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            boolean selected = selectedZone == this.band;
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();

            if (selected) {
                // Warm gold background highlight
                context.fill(x, y, x + w, y + h, 0x40D4A74A);
                // Native band color left accent border (3px wide)
                int bandColor = BAND_COLORS[this.band.ordinal()];
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

            drawUiText(context, this.band.displayName(), textX, y + compactUi(2), textColor, selected);

            String range = formatDegree(this.band.lowDeg()) + "\u2013" + formatDegree(this.band.highDeg());
            int rangeW = uiTextWidth(range);
            int rangeX = x + w - rangeW - 4;
            drawUiText(context, range, rangeX, y + compactUi(2), selected ? WARM_WHITE : MUTED, false);

            String helper = ZONE_HELPER[this.band.ordinal()];
            int helperWidth = Math.max(40, rangeX - textX - 6);
            int helperY = y + compactUi(2) + uiFontHeight() + compactUi(2);
            for (net.minecraft.text.StringVisitable wrappedLine : wrapUiLines(helper, helperWidth)) {
                if (helperY + uiFontHeight() > y + h - compactUi(2)) break;
                drawUiText(context, wrappedLine.getString(), textX, helperY, MUTED, false);
                helperY += uiFontHeight();
            }

            this.setCursor(context);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            this.appendDefaultNarrations(builder);
        }
    }
}
