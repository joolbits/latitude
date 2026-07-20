package com.example.globe.client.create;

import com.example.globe.client.GlobeWorldSize;
import com.example.globe.client.LatitudeConfig;
import com.example.globe.client.LatitudeHudStudioScreen;
import com.example.globe.client.RainbowText;
import com.example.globe.core.config.LatitudeConfigData.AccessibilityMode;
import com.example.globe.core.ui.AccessibilityPalette;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.DataPackReloadCookie;
import net.minecraft.client.gui.screens.worldselection.WorldCreationGameRulesScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContextMapper;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
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
    // Minimum GUI-scaled width to use the three-column layout instead of tabs. Deliberately well above
    // MIN_LEFT_W+MIN_RIGHT_W+MIN_RAIL_W+gaps (~384, the point where columns merely *fit*): three columns need
    // breathing room to *read well*. Without this, a mid GUI scale on a small screen (e.g. GUI 4 on a 13"
    // laptop, ~456px viewport) squeezes three columns into a cramped, heavily-wrapping mess. At ~530 GUI 3
    // (~616px) stays three-column while GUI 4 drops to the tabbed layout, and every column has comfortable room.
    private static final int COMFORTABLE_THREE_COL_W = 530;
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
            "Savannas, dry uplands, and warm frontier climates",
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

    // ── World shape constants (2.0 "Longitude" release) ──
    // Index 0 = Mercator/Wide (the current forced default for every new world; MUST stay index 0 so
    // worldShapeIdx=0 preserves today's behavior for anyone who never touches this toggle).
    // Display names renamed per Peetsa 2026-07-10: "Mercator 2:1"/"Legacy 1:1" -> "Wide 2:1"/"Square 1:1"
    // (user-facing only; the underlying LatitudeBiomes.GlobeShape enum stays MERCATOR/CLASSIC).
    private static final String[] WORLD_SHAPE_NAMES = { "Wide 2:1", "Square 1:1" };
    private static final int[] WORLD_SHAPE_COLORS = { GOLD, MUTED };
    private static final int DISABLED_COLOR = 0xFF605850;

    // Selected spawn-zone "alive" polish (UI round 13): the picked band's name letters bounce on a gentle
    // per-letter-phased sine wave, and its left accent tab shimmers in brightness. Both are deliberately
    // subtler than the Atlas band's traveling Gaussian crest (see LatitudePlanisphereRenderer) -- this reads
    // as "alive", not "jumping". Wall-clock driven, same System.currentTimeMillis() idiom as every other
    // animation on this screen.
    // Motion-sickness tone-down (Peetsa, 2026-07-11 -- "a teensy smidge" calmer so the wave can't nauseate anyone):
    // amplitude 1.8 -> 1.4 (smaller bob) and period 0.95 -> 1.1s (slower = less oscillation energy). Still alive, just
    // gentler; letter phase is unchanged so the wave still TRAVELS across the word rather than pulsing in place.
    private static final double ZONE_BOUNCE_PERIOD_SEC = 1.1;   // one full bob; slowed a touch to bleed off oscillation energy (motion-sickness tone-down) -- still a lively ripple, not a slow heave
    private static final double ZONE_BOUNCE_AMPLITUDE_PX = 1.4; // peak vertical offset (drawn at sub-pixel precision via pose translate); trimmed 1.8->1.4 so the bob is gentler on motion-sensitive players
    private static final double ZONE_BOUNCE_LETTER_PHASE = 0.6; // radians of phase lag per letter -> a soft travelling wave (unchanged: the wave must still travel)
    private static final double ZONE_TAB_SHIMMER_PERIOD_SEC = 2.4; // slower than the Atlas crest's 2.6s sweep
    private static final double ZONE_TAB_SHIMMER_AMPLITUDE = 0.20; // +/-20% brightness
    // Accessibility (Peetsa 2026-07-11): the selected zone row's name/subtitle were barely brighter than an
    // unselected row's. These lift multipliers (fed through the existing liftBrightness helper, same idiom as
    // the wordmark's gold breath) push the selected name to a distinctly brighter gold and the selected
    // subtitle to a distinctly brighter warm grey, while leaving unselected rows (GOLD/MUTED) untouched.
    private static final float ZONE_SELECTED_NAME_LIFT = 1.35f;     // selected name: brighter gold
    private static final float ZONE_SELECTED_SUBTITLE_LIFT = 1.75f; // selected subtitle: brighter grey
    // LATITUDE wordmark delight (UI round 13): slow warm bloom pulse + gold breath + twinkling sparkle motes,
    // all frozen under Reduce Motion. The drawing (and its TITLE_*/hash32 constants) now lives in the shared
    // LatitudeWordmark helper so the bespoke loading overlay renders the identical nameplate.
    private static final boolean DEBUG_UI_SWITCH_LAG = Boolean.getBoolean("latitude.debug.uiSwitchLag");

    private final Runnable onClose;
    @Nullable
    private final Screen parent;
    private final WorldCreationContext holder;

    // ── Local UI state (fresh each open) ──
    private GlobeWorldSize selectedSize = DEFAULT_SIZE;
    private LatitudeBands.Band selectedZone = LatitudeBands.Band.TEMPERATE;
    // "Random" spawn-zone row (TEST 28 request): when true, selectedZone is ignored in the UI (no band
    // highlighted anywhere) and beginExpedition rolls a concrete band at create time.
    private boolean randomZone = false;
    private static final String RANDOM_ZONE_HELPER = "Sealed orders — your starting climate is drawn when the expedition begins";
    private int selectedModeIdx = 0;  // 0=Survival, 1=Hardcore, 2=Creative
    private boolean allowCommands = false;
    private boolean startWithCompass = true;
    private boolean bonusChest = false;
    private boolean generateStructures = true;
    private int worldTypeIdx = 0;  // 0=Latitude, 1=Vanilla, 2=Vanilla Superflat
    private int worldShapeIdx = 0;  // 0=Wide 2:1 (default, Mercator internally), 1=Square 1:1 (Classic internally)
    private GameRules gameRules;

    private String worldNameInput = "New World";
    private String seedInput = "";
    private Button seedRandomBtn;
    private Button seedCopyBtn;
    private EditBox worldNameField;
    private EditBox seedField;
    private Button sizePrevBtn;
    private Button sizeNextBtn;
    private final List<ZoneRowWidget> zoneRows = new ArrayList<>();
    // Rules-panel buttons are added via addWidget (input/focus only, NOT auto-rendered by super) and rendered
    // manually inside the panel's scissor in extractRenderState, so they clip into partial "half buttons" at
    // the scroll edges (like vanilla option lists) instead of popping. 26.2 sealed Button's own render
    // (final extractWidgetRenderState + package-private extractContents), so a self-clipping subclass isn't
    // possible -- manual scissor rendering is the way vanilla itself does scrollable lists.
    private final List<AbstractWidget> settingsScrollWidgets = new ArrayList<>();

    // ── Settings rail rows (UI round 13: iconography-based icon+label+state rows, not plain MC buttons) ──
    // Commands / Starting Compass / Generate Structures / Bonus Chest are illuminated on/off toggles;
    // HUD Studio / Game Rules are action rows (open a screen). Each draws its own code-drawn icon, short
    // label, On/Off word (toggles), tooltip, lit-vs-dim state, and plays the click sound on select.
    private RulesIconRow commandsBtn;
    private RulesIconRow compassBtn;
    private RulesIconRow bonusChestBtn;
    private RulesIconRow structuresBtn;
    private Button worldTypePrevBtn;
    private Button worldTypeNextBtn;
    private Button worldShapePrevBtn;
    private Button worldShapeNextBtn;
    private Button modePrevBtn;
    private Button modeNextBtn;
    private RulesIconRow gameRulesBtn;
    private RulesIconRow hudStudioBtn;

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
    private int worldShapeFieldY;
    private int sizeFieldY;
    private int inputBottomY;
    private int leftScroll;
    // Smooth-scroll: *Scroll is the target set by wheel/scrollbar; *ScrollDisplay eases toward it each frame so
    // the panel content (and its widgets) glides instead of snapping. *MaxScroll caches the clamp bound so the
    // per-frame easing can clamp the display without recomputing layout. See advanceScrollAnimation().
    private float leftScrollDisplay;
    private float rightScrollDisplay;
    private float settingsScrollDisplay;
    private int leftMaxScroll;
    private int rightMaxScroll;
    private int settingsMaxScroll;
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

    public static void openLoaded(Minecraft client, Runnable onClose, @Nullable Screen parent, WorldCreationContext holder,
                                  @Nullable String seed, @Nullable String worldName) {
        LOGGER.info("[LAT][CWPATH] LatitudeCreateWorldScreen.openLoaded parent={} holder={} seedPreset={} namePreset={}",
                parent == null ? "null" : parent.getClass().getName(),
                holder,
                seed != null && !seed.isBlank(),
                worldName != null && !worldName.isBlank());
        LatitudeCreateWorldScreen screen = new LatitudeCreateWorldScreen(onClose, parent, holder);
        client.setScreenAndShow(screen);
        // Carry over the seed AND world name vanilla was holding. On "Re-create" both come from the source
        // world (seed via getSeed(), name via getName()); on a fresh create they are the defaults. This makes
        // recreate pre-fill both fields instead of resetting them (TEST 1 A5 + bug-catcher #2: recreate used to
        // drop the source name and always create "New World"). probeSetWorldInputs no-ops on a blank seed/name,
        // so fresh create still starts empty/default.
        boolean hasSeed = seed != null && !seed.isBlank();
        boolean hasName = worldName != null && !worldName.isBlank();
        if (hasSeed || hasName) {
            screen.probeSetWorldInputs(hasName ? worldName : null, hasSeed ? seed : null, null);
        }
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
                        if (client.gui.screen() == null || client.gui.screen() instanceof GenericMessageScreen) {
                            client.setScreenAndShow(parent);
                        }
                        return;
                    }

                    // Open the bespoke screen with the loaded holder.
                    client.setScreenAndShow(new LatitudeCreateWorldScreen(onClose, parent, loadedHolder));
                });
            });
        } catch (Exception e) {
            LOGGER.error("Failed to load datapacks for Latitude create-world screen", e);
            // 5A error path: return to caller screen, never show bespoke screen
            onClose.run();
            if (client.gui.screen() == null || client.gui.screen() instanceof GenericMessageScreen) {
                client.setScreenAndShow(parent);
            }
        }
    }

    @Override
    protected void init() {
        LOGGER.info("[LAT][CWPATH] LatitudeCreateWorldScreen.init screen={} holder={}",
                this.getClass().getName(), this.holder);
        zoneRows.clear();
        // The Rules-panel widgets are tracked in this custom list AND drawn manually by
        // renderSettingsScrollWidgets(). The harness's clearWidgets() (run before every init) empties
        // children/renderables/narratables but NOT this list, so re-init (window resize, or returning from
        // the HUD Studio / Game Rules sub-screens) would otherwise APPEND a second set of rows on top of the
        // stale ones. The stale widgets drop out of children (so updateSettingsLayout no longer repositions
        // them) but linger here, frozen at their last positions -- renderSettingsScrollWidgets then paints them
        // as a second, non-scrolling "ghost" layer interleaved with the live rows (the reported scroll-ghost
        // bug, Rules-only because only this panel uses a manually-drawn, un-harness-managed widget list). Clear
        // it here, exactly like zoneRows above, so each init rebuilds one coherent layer. See addWidget vs
        // addRenderableWidget note at the field declaration.
        settingsScrollWidgets.clear();
        int headerGap = 10;
        int headerToPanel = 42;
        int bottomMargin = 40;
        int btnBottomOffset = 30;
        int fieldGap1 = 38;
        int fieldGap2 = 40;
        int labelFieldGap = 22;
        int fieldH = Math.max(16, 16);
        int btnH = Math.max(18, 20);
        int stepperBtnW = 20;

        headerY = headerGap;
        int bottomY = this.height - btnBottomOffset;
        panelTop = headerY + headerToPanel;
        panelBottom = this.height - bottomMargin;
        int cx = this.width / 2;
        paneGap = 8;
        paneStripViewportLeft = 12;
        paneStripViewportRight = Math.max(paneStripViewportLeft + 1, this.width - 12);
        paneStripViewportWidth = Math.max(1, paneStripViewportRight - paneStripViewportLeft);
        paneStripContentWidth = paneStripViewportWidth;
        paneStripScrollbarX = paneStripViewportLeft;
        paneStripScrollbarW = paneStripViewportWidth;
        paneStripScrollbarY = panelBottom + 2;
        paneStripScrollbarH = Math.max(4, Math.min(Math.max(4, 6), Math.max(4, bottomY - paneStripScrollbarY - 2)));
        // Use the comfortable threshold, not the bare fit-width, so cramped mid-GUI-scale cases go tabbed.
        // This is the screen's real scale/DPI adaptation: everything below keys off the already-gui-scaled
        // this.width/height, so spacing is plain gui-pixel literals (no separate per-pixel scaling layer).
        int minThreeColWidth = Math.max(COMFORTABLE_THREE_COL_W, MIN_LEFT_W + MIN_RIGHT_W + MIN_RAIL_W + paneGap * 2);
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
            // Rail gets its minimum first; left/right split the remainder. Left (World + Atlas) is widened and
            // the middle (Spawn Zone) narrowed vs the original 43:57 split, per live feedback — a wider left pane
            // gives the 2:1 atlas more width budget (maxRadiusByWidth) so it renders larger and the latitude
            // labels fit, and lets the size descriptions wrap to two lines instead of three.
            railW = Math.max(MIN_RAIL_W, (int) (paneStripContentWidth * 0.26f));
            int rem = paneStripContentWidth - railW - paneGap * 2;
            leftW = Math.max(MIN_LEFT_W, (int) (rem * 0.48f)); // left:right ~48:52 (was 43:57)
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

        // ── 2. Seed (with dice-reroll and copy affordances, U-E) ──
        seedFieldY = worldFieldY + fieldGap1;
        int seedBtnW = fieldH; // square buttons, field-height sized
        this.seedField = new EditBox(this.font, inputX, seedFieldY, inputW - 2 * (seedBtnW + 2), fieldH, Component.literal("Seed"));
        this.seedField.setMaxLength(64);
        this.seedField.setHint(Component.literal("Leave blank for random"));
        this.seedField.setValue(seedInput);
        this.seedField.setResponder(text -> seedInput = text);
        this.addRenderableWidget(this.seedField);

        this.seedRandomBtn = Button.builder(Component.literal("\u2684"), b -> {
                    String rolled = Long.toString(java.util.concurrent.ThreadLocalRandom.current().nextLong());
                    seedInput = rolled;
                    if (seedField != null) seedField.setValue(rolled);
                })
                .bounds(inputX + inputW - 2 * seedBtnW - 2, seedFieldY, seedBtnW, fieldH)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Roll a random seed")))
                .build();
        this.addRenderableWidget(this.seedRandomBtn);

        this.seedCopyBtn = Button.builder(Component.literal("\u29c9"), b -> {
                    if (this.minecraft != null && !seedInput.isBlank()) {
                        this.minecraft.keyboardHandler.setClipboard(seedInput);
                    }
                })
                .bounds(inputX + inputW - seedBtnW, seedFieldY, seedBtnW, fieldH)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Copy seed to clipboard")))
                .build();
        this.addRenderableWidget(this.seedCopyBtn);

        // ── 3. World Shape ◀▶ (moved here from the Rules panel per live feedback -- it's a "what is
        // this world" property, so it reads more naturally alongside World Size than buried in Rules) ──
        worldShapeFieldY = seedFieldY + fieldGap2;
        worldShapePrevBtn = Button.builder(Component.literal("\u25C0"), b -> cycleWorldShape(-1))
                .bounds(inputX, worldShapeFieldY, stepperBtnW, btnH)
                .build();
        this.addRenderableWidget(worldShapePrevBtn);
        worldShapeNextBtn = Button.builder(Component.literal("\u25B6"), b -> cycleWorldShape(1))
                .bounds(inputX + inputW - stepperBtnW, worldShapeFieldY, stepperBtnW, btnH)
                .build();
        this.addRenderableWidget(worldShapeNextBtn);

        // ── 4. Size ◀ ──
        sizeFieldY = worldShapeFieldY + fieldGap2;
        sizePrevBtn = Button.builder(Component.literal("\u25C0"), b -> cycleSize(-1))
                .bounds(inputX, sizeFieldY, stepperBtnW, btnH)
                .build();
        this.addRenderableWidget(sizePrevBtn);

        // ── 5. Size ▶ ──
        sizeNextBtn = Button.builder(Component.literal("\u25B6"), b -> cycleSize(1))
                .bounds(inputX + inputW - stepperBtnW, sizeFieldY, stepperBtnW, btnH)
                .build();
        this.addRenderableWidget(sizeNextBtn);
        updateLeftWidgets(inputX, inputW, fieldH, btnH, stepperBtnW);

        inputBottomY = Math.max(sizeFieldY + btnH, computeSizeLabelBottom(sizeFieldY - 1, inputW - 48)) + 12;
        updateLeftLayout();

        zoneRowHeight = computeZoneRowHeight(rightW - 4);
        zoneRowStep = zoneRowHeight + 6;
        for (LatitudeBands.Band band : LatitudeBands.Band.values()) {
            ZoneRowWidget row = new ZoneRowWidget(rightX + 2, panelTop, rightW - 4, zoneRowHeight, band);
            zoneRows.add(row);
            this.addRenderableWidget(row);
        }
        // The sixth row: Random — a null-band ZoneRowWidget; the concrete band is rolled at create time.
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
            // ── Iconography rows (UI round 13 pass B) ──
            // Each RulesIconRow draws a code-drawn icon + short label + On/Off word + tooltip and plays the
            // vanilla click on select. Toggles read/write their backing boolean live via getter/toggle;
            // action rows run an open-a-screen callback. Compass is gated to Latitude worlds (dim + inert on
            // Vanilla). Labels are short (the plain-language explanation rides the tooltip).
            commandsBtn = toggleRow(RulesIconRow.Kind.COMMANDS, "Commands",
                    "Lets you use cheat commands (like /gamemode or /time) in this world.",
                    () -> allowCommands, () -> allowCommands = !allowCommands, () -> true);
            compassBtn = toggleRow(RulesIconRow.Kind.COMPASS, "Compass",
                    "Start your expedition with a compass already in your pack. Latitude worlds only.",
                    () -> startWithCompass, () -> startWithCompass = !startWithCompass, this::isLatitudeWorld);
            structuresBtn = toggleRow(RulesIconRow.Kind.STRUCTURES, "Structures",
                    "Generate villages, temples, mineshafts and other structures across the world.",
                    () -> generateStructures, () -> generateStructures = !generateStructures, () -> true);
            bonusChestBtn = toggleRow(RulesIconRow.Kind.BONUS_CHEST, "Bonus Chest",
                    "Place a small starter chest of supplies near your spawn.",
                    () -> bonusChest, () -> bonusChest = !bonusChest, () -> true);
            hudStudioBtn = actionRow(RulesIconRow.Kind.HUD_STUDIO, "HUD Studio",
                    "Open HUD Studio to customize your compass, labels and on-screen readouts.",
                    this::openHudStudio, true);
            gameRulesBtn = actionRow(RulesIconRow.Kind.GAME_RULES, "Game Rules",
                    "Fine-tune the world's rules (mob spawning, daylight cycle, keep inventory, and more).",
                    this::openGameRules, false);

            // addWidget (not addRenderableWidget): these get input/focus but are NOT auto-rendered by super --
            // renderSettingsScrollWidgets() draws them inside the Rules-panel scissor so they clip when scrolled.
            addSettingsScrollWidget(worldTypePrevBtn);
            addSettingsScrollWidget(worldTypeNextBtn);
            addSettingsScrollWidget(modePrevBtn);
            addSettingsScrollWidget(modeNextBtn);
            addSettingsScrollWidget(commandsBtn);
            addSettingsScrollWidget(compassBtn);
            addSettingsScrollWidget(structuresBtn);
            addSettingsScrollWidget(bonusChestBtn);
            addSettingsScrollWidget(gameRulesBtn);
            addSettingsScrollWidget(hudStudioBtn);
            updateSettingsLayout();
        }

        if (tabbedMode) {
            applyTabbedVisibility();
        }

        // ── 17. Create World ──
        int btnSpacing = 8;
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

        // ── Focus: pre-select world name text for immediate overwrite ──
        this.worldNameField.setFocused(true);
        this.setFocused(this.worldNameField);
        this.worldNameField.moveCursorToEnd(false);
        this.worldNameField.setHighlightPos(0);
    }

    // ── Size stepper ──

    private void cycleSize(int delta) {
        GlobeWorldSize[] sizes = GlobeWorldSize.values();
        int idx = selectedSize.ordinal() + delta;
        if (idx < 0) idx = sizes.length - 1;
        if (idx >= sizes.length) idx = 0;
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

    private void cycleWorldShape(int delta) {
        worldShapeIdx = (worldShapeIdx + delta + WORLD_SHAPE_NAMES.length) % WORLD_SHAPE_NAMES.length;
        // Unlike cycleMode, this MUST re-run the World-pane layout: changing shape changes worldDimsLabel()'s
        // output and the atlas preview's aspect (circle vs rectangle), both computed by updateLeftLayout().
        updateLeftLayout();
    }

    private boolean isLatitudeWorld() {
        return worldTypeIdx == 0;
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

    /**
     * Reserved to the WORST-CASE wrapped line count across every size's description, not just the
     * selected one (TEST 30: "the atlas bounces around... when I change the size" — cycling sizes
     * changes which description wraps to how many lines, which fed straight into baseInputBottom and
     * therefore leftPreviewTopY, moving the ATLAS heading and map every time the description's line
     * count changed). Reserving the max means the block below (the atlas section) never moves when
     * size changes; a short description just leaves blank space under it, same pattern as the compass
     * HUD's reservedTextWidth.
     */
    private int computeSizeLabelBottom(int y, int availW) {
        int maxLines = 1;
        for (String desc : SIZE_DESCRIPTIONS) {
            maxLines = Math.max(maxLines, wrapLineCount(desc, Math.max(40, availW)));
        }
        return y + 22 + maxLines * uiFontHeight();
    }

    private int getSmallWorldWarningHeight(int width) {
        return wrapLineCount(SMALL_WORLD_WARNING.getString(), Math.max(40, width)) * uiFontHeight();
    }

    private int computeZoneListTop() {
        return panelTop + 22 + wrappedTextHeight("Choose the climate where your journey begins", Math.max(80, rightW - 20 - SCROLLBAR_GUTTER)) + 10;
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
        return 4 + uiFontHeight() + 2 + maxHelperLines * uiFontHeight() + 4;
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
            worldNameField.setRectangle(inputW, fieldH, inputX, worldFieldY);
            worldNameField.visible = true;
            worldNameField.active = true;
        }
        if (seedField != null) {
            int seedBtnW = fieldH;
            seedField.setRectangle(inputW - 2 * (seedBtnW + 2), fieldH, inputX, seedFieldY);
            seedField.visible = true;
            seedField.active = true;
            if (seedRandomBtn != null) {
                seedRandomBtn.setRectangle(seedBtnW, fieldH, inputX + inputW - 2 * seedBtnW - 2, seedFieldY);
                seedRandomBtn.visible = true;
                seedRandomBtn.active = true;
            }
            if (seedCopyBtn != null) {
                seedCopyBtn.setRectangle(seedBtnW, fieldH, inputX + inputW - seedBtnW, seedFieldY);
                seedCopyBtn.visible = true;
                seedCopyBtn.active = true;
            }
        }
        if (worldShapePrevBtn != null) {
            worldShapePrevBtn.setRectangle(stepperBtnW, btnH, inputX, worldShapeFieldY);
            worldShapePrevBtn.visible = true;
            worldShapePrevBtn.active = true;
        }
        if (worldShapeNextBtn != null) {
            worldShapeNextBtn.setRectangle(stepperBtnW, btnH, inputX + inputW - stepperBtnW, worldShapeFieldY);
            worldShapeNextBtn.visible = true;
            worldShapeNextBtn.active = true;
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
        int fieldH = worldNameField != null ? worldNameField.getHeight() : Math.max(16, 16);
        int btnH = sizePrevBtn != null ? sizePrevBtn.getHeight() : Math.max(18, 20);
        int stepperBtnW = sizePrevBtn != null ? sizePrevBtn.getWidth() : 20;
        int contentTop = panelTop + 8;
        int labelFieldGap = 22;
        int fieldGap1 = 38;
        int fieldGap2 = 40;
        int discGap = 6;
        int previewHeight = Math.max(150, Math.min(leftW - 20 - SCROLLBAR_GUTTER, Math.max(170, panelBottom - panelTop - 80)));
        int baseWorldY = contentTop + labelFieldGap;
        int baseSeedY = baseWorldY + fieldGap1;
        int baseWorldShapeY = baseSeedY + fieldGap2;
        int warningHeight = shouldShowSmallWorldWarning() ? getSmallWorldWarningHeight(inputW) : 0;
        int warningGap = warningHeight > 0 ? 4 : 0;
        int baseSizeY = baseWorldShapeY + fieldGap2 + warningHeight + warningGap;
        int baseInputBottom = Math.max(baseSizeY + btnH, computeSizeLabelBottom(baseSizeY - 1, inputW - stepperBtnW * 2 - 8)) + 12;
        int basePreviewTop = baseInputBottom + discGap + uiFontHeight();
        int basePreviewBottom = basePreviewTop + previewHeight;
        leftViewportTop = panelTop + 4;
        leftViewportBottom = panelBottom - 4;
        int viewportHeight = Math.max(0, leftViewportBottom - leftViewportTop);
        leftContentHeight = basePreviewBottom - contentTop + 8;
        int maxScroll = Math.max(0, leftContentHeight - viewportHeight);
        if (leftScroll < 0) leftScroll = 0;
        if (leftScroll > maxScroll) leftScroll = maxScroll;
        leftMaxScroll = maxScroll;
        int vis = Math.round(leftScrollDisplay);

        worldFieldY = baseWorldY - vis;
        seedFieldY = baseSeedY - vis;
        worldShapeFieldY = baseWorldShapeY - vis;
        sizeFieldY = baseSizeY - vis;
        inputBottomY = baseInputBottom - vis;
        leftPreviewTopY = basePreviewTop - vis;
        leftPreviewBottomY = basePreviewBottom - vis;

        updateLeftWidgets(inputX, inputW, fieldH, btnH, stepperBtnW);
        updateLeftWidgetVisibility(worldNameField);
        updateLeftWidgetVisibility(seedField);
        updateLeftWidgetVisibility(seedRandomBtn);
        updateLeftWidgetVisibility(seedCopyBtn);
        updateLeftWidgetVisibility(worldShapePrevBtn);
        updateLeftWidgetVisibility(worldShapeNextBtn);
        updateLeftWidgetVisibility(sizePrevBtn);
        updateLeftWidgetVisibility(sizeNextBtn);
        // World Shape and World Size only apply to Latitude worlds — grey the steppers out for Vanilla /
        // Vanilla Superflat (Peetsa TEST 7: "mercator/regular should be greyed out for vanilla, not just vanilla
        // superflat" -- World Shape moved here from the Rules panel keeps the same gating it had there).
        if (!isLatitudeWorld()) {
            if (worldShapePrevBtn != null) worldShapePrevBtn.active = false;
            if (worldShapeNextBtn != null) worldShapeNextBtn.active = false;
            if (sizePrevBtn != null) sizePrevBtn.active = false;
            if (sizeNextBtn != null) sizeNextBtn.active = false;
        }
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

    /** The spawn-zone description line — one source for the layout measure AND the render, so the
     *  panel can never be sized for a different string than it draws (random-aware). */
    private String spawnZoneDescription() {
        if (randomZone) {
            return "Sealed orders — your starting climate stays secret, drawn anywhere from the Tropics to the Poles when the expedition begins.";
        }
        return "You will spawn between " + formatDegree(selectedZone.lowDeg()) + "–" + formatDegree(selectedZone.highDeg())
                + " latitude. " + ZONE_HELPER[selectedZone.ordinal()] + ".";
    }

    private void updateRightLayout() {
        int contentTop = panelTop + 8;
        // threeCol draws an inline heading that needs this reserved strip; tabbed mode skips the redundant
        // "Spawn Zone" title (the tab strip labels it), so zero the strip there and let content start at the top.
        int titleBlockHeight = threeCol ? (uiFontHeight() + 4) : 0;
        int subtitleWidth = Math.max(80, rightW - 28 - SCROLLBAR_GUTTER);
        int subtitleHeight = wrappedTextHeight("Choose the climate where your journey begins", subtitleWidth);
        int descTextWidth = Math.max(60, rightW - 16 - SCROLLBAR_GUTTER);
        String spawnLine = spawnZoneDescription();
        int descHeight = 6 + uiFontHeight() + 5 + wrappedTextHeight(spawnLine, descTextWidth) + 4 + uiFontHeight() + 6;
        int baseSubtitleY = contentTop + titleBlockHeight;
        int baseDividerY = baseSubtitleY + subtitleHeight + 2;
        int baseZoneListTop = baseDividerY + 8;
        int baseZoneListBottom = baseZoneListTop + zoneRows.size() * zoneRowStep - 6;
        int baseBarY = baseZoneListBottom + 8;
        int baseBarH = Math.max(4, 6);
        int baseDescY = baseBarY + baseBarH + 12;
        rightViewportTop = panelTop + 4;
        rightViewportBottom = panelBottom - 4;
        int viewportHeight = Math.max(0, rightViewportBottom - rightViewportTop);
        rightContentHeight = baseDescY + descHeight - contentTop + 8;
        int maxScroll = Math.max(0, rightContentHeight - viewportHeight);
        if (rightScroll < 0) rightScroll = 0;
        if (rightScroll > maxScroll) rightScroll = maxScroll;
        rightMaxScroll = maxScroll;
        int vis = Math.round(rightScrollDisplay);

        rightSubtitleY = baseSubtitleY - vis;
        rightDividerY = baseDividerY - vis;
        zoneListTopY = baseZoneListTop - vis;
        zoneListBottomY = baseZoneListBottom - vis;
        rightBarY = baseBarY - vis;
        rightBarH = baseBarH;
        rightDescPanelY = baseDescY - vis;
        rightDescPanelH = descHeight;

        int zoneY = zoneListTopY;
        for (ZoneRowWidget row : zoneRows) {
            row.setRectangle(rightW - 4 - SCROLLBAR_GUTTER, zoneRowHeight, rightX + 2, zoneY);
            // Vertical gate is INTERSECT (not fully-contained) so a row stays visible while it's partway past the
            // top/bottom edge; extractWidgetRenderState scissors it to the viewport, so it slides and clips (a
            // "half row" at the edge) instead of popping in/out. Horizontal stays fully-contained (that's the
            // separate pane-strip axis).
            boolean visible = isLatitudeWorld()
                    && (!tabbedMode || activeTab == 1)
                    && row.getX() >= paneStripViewportLeft
                    && row.getX() + row.getWidth() <= paneStripViewportRight
                    && zoneY + zoneRowHeight > rightViewportTop
                    && zoneY < rightViewportBottom;
            row.visible = visible;
            // H-fix (2026-07-19, Peetsa live report): active must match the render gate (intersect), not a
            // fully-contained gate -- a row scrolled partway past the top/bottom edge still draws its visible
            // sliver (see extractWidgetRenderState's scissor) and must accept clicks on that sliver. The old
            // fully-contained gate silently dropped clicks on any row not ENTIRELY inside the viewport, which
            // read as "can't select Polar until it's fully scrolled into view" -- reproducible on any row, top
            // or bottom edge alike. ZoneRowWidget#isMouseOver clamps the actual click point to the same clip
            // rect used for rendering, so a click landing in the row's off-screen (unclipped, undrawn) half is
            // still correctly rejected.
            row.active = visible;
            zoneY += zoneRowStep;
        }
    }

    private void updateSettingsButtons() {
        // Icon rows read their backing boolean live each frame, so no message to update here. Interaction
        // gating is handled per-row: positionSettingsButton already set active = visible; the Starting
        // Compass row is additionally inert on non-Latitude worlds (it draws dim and ignores clicks there).
        if (commandsBtn != null) commandsBtn.active = commandsBtn.visible;
        if (compassBtn != null) compassBtn.active = compassBtn.visible && isLatitudeWorld();
        if (structuresBtn != null) structuresBtn.active = structuresBtn.visible;
        if (bonusChestBtn != null) bonusChestBtn.active = bonusChestBtn.visible;
        if (gameRulesBtn != null) gameRulesBtn.active = gameRulesBtn.visible;
        if (hudStudioBtn != null) hudStudioBtn.active = hudStudioBtn.visible;
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
        int labelGap = 10;
        int rowGap = 10;
        // threeCol reserves a top strip for the "World Settings" inline heading. Tabbed mode has the tab strip
        // instead, so drop that reserved strip — otherwise content scrolls up into a blank ~36px "invisible
        // header bar" (the old WORLD/SETTINGS header was drawn inside the scissor above its own top edge, so it
        // was clipped away and just left dead space). Matches the panel-2 Spawn Zone tabbed-mode fix.
        settingsViewportTop = threeCol ? (panelTop + 36) : (panelTop + 8);
        settingsViewportBottom = panelBottom - 8;
        int viewportHeight = Math.max(0, settingsViewportBottom - settingsViewportTop);
        int contentTop = settingsViewportTop + 4;
        int blockHeight = labelGap + btnH;
        // Leave a little trailing room so the HUD Studio row can scroll fully into view
        // on short windows instead of sitting flush against the viewport edge.
        // 8 rows: World Type, Game Mode, Commands, Starting Compass, Generate Structures,
        // Bonus Chest, Game Rules, HUD Studio. (World Shape moved to the World panel per live feedback.)
        settingsContentHeight = blockHeight * 8 + rowGap * 7 + 12;
        int maxScroll = Math.max(0, settingsContentHeight - viewportHeight);
        if (settingsScroll < 0) settingsScroll = 0;
        if (settingsScroll > maxScroll) settingsScroll = maxScroll;
        settingsMaxScroll = maxScroll;

        int y = contentTop - Math.round(settingsScrollDisplay) + labelGap;
        worldTypeRowY = y;
        positionSettingsStepper(worldTypePrevBtn, worldTypeNextBtn, settBtnX, settBtnW, y, btnH);

        y += btnH + rowGap + labelGap;
        modeRowY = y;
        positionSettingsStepper(modePrevBtn, modeNextBtn, settBtnX, settBtnW, y, btnH);

        // Icon rows draw their own inline label, so they position directly off the running y (no stored
        // *RowY needed the way the two stepper rows still need theirs for the label-above draw).
        y += btnH + rowGap + labelGap;
        positionSettingsButton(commandsBtn, settBtnX, settBtnW, y, btnH);

        y += btnH + rowGap + labelGap;
        positionSettingsButton(compassBtn, settBtnX, settBtnW, y, btnH);

        // HUD Studio right after Starting Compass, per live feedback (this screen only -- the pause-menu
        // Latitude Settings screen keeps HUD Studio first, unchanged from the prior round).
        y += btnH + rowGap + labelGap;
        positionSettingsButton(hudStudioBtn, settBtnX, settBtnW, y, btnH);

        y += btnH + rowGap + labelGap;
        positionSettingsButton(structuresBtn, settBtnX, settBtnW, y, btnH);

        y += btnH + rowGap + labelGap;
        positionSettingsButton(bonusChestBtn, settBtnX, settBtnW, y, btnH);
        y += btnH + rowGap + labelGap;
        positionSettingsButton(gameRulesBtn, settBtnX, settBtnW, y, btnH);

        updateSettingsButtons();
    }

    private void applyTabbedVisibility() {
        if (!tabbedMode) return;
        // Tab 0 = World (left panel widgets)
        boolean showWorld = activeTab == 0;
        setTabbedWidgetVisible(worldNameField, showWorld);
        setTabbedWidgetVisible(seedField, showWorld);
        setTabbedWidgetVisible(worldShapePrevBtn, showWorld);
        setTabbedWidgetVisible(worldShapeNextBtn, showWorld);
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
        setTabbedWidgetVisible(structuresBtn, showRules);
        setTabbedWidgetVisible(bonusChestBtn, showRules);
        setTabbedWidgetVisible(gameRulesBtn, showRules);
        setTabbedWidgetVisible(hudStudioBtn, showRules);
    }

    private void setTabbedWidgetVisible(AbstractWidget widget, boolean visible) {
        if (widget == null) return;
        widget.visible = visible;
        widget.active = visible;
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
        // INTERSECT gate (see positionSettingsButton): keep the pair visible while partway past the edge so they
        // render clipped instead of popping; hidden only when fully off-viewport or on another tab.
        boolean visible = (!tabbedMode || activeTab == 2)
                && left.getX() < paneStripViewportRight
                && right.getX() + right.getWidth() > paneStripViewportLeft
                && y + height > settingsClipTop()
                && y < settingsViewportBottom;
        left.visible = visible;
        right.visible = visible;
        left.active = visible;
        right.active = visible;
    }

    private void positionSettingsButton(AbstractWidget button, int x, int width, int y, int height) {
        button.setRectangle(width - SCROLLBAR_GUTTER, height, x, y);
        // INTERSECT gate (not fully-contained): a button that's partway past the top/bottom edge stays visible so
        // renderSettingsScrollWidgets can draw it clipped ("half button"). Fully off-viewport rows are hidden so
        // they don't render or take clicks.
        boolean visible = (!tabbedMode || activeTab == 2)
                && button.getX() < paneStripViewportRight
                && button.getX() + button.getWidth() > paneStripViewportLeft
                && y + height > settingsClipTop()
                && y < settingsViewportBottom;
        button.visible = visible;
        button.active = visible;
    }

    private void addSettingsScrollWidget(AbstractWidget widget) {
        if (widget == null) return;
        this.addWidget(widget);          // input + focus + narration, but NOT auto-rendered by super
        settingsScrollWidgets.add(widget);
    }

    // Draw the addWidget'd Rules buttons manually; the caller wraps this in the Rules-panel scissor so they clip
    // at the viewport edges. Skips hidden (fully off-viewport / wrong-tab) widgets; their own extractRenderState
    // also no-ops when !visible, so this is belt-and-suspenders.
    private void renderSettingsScrollWidgets(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        for (AbstractWidget w : settingsScrollWidgets) {
            if (w != null && w.visible) {
                w.extractRenderState(context, mouseX, mouseY, delta);
            }
        }
    }

    private void openGameRules() {
        if (this.minecraft == null) return;
        this.minecraft.setScreenAndShow(new WorldCreationGameRulesScreen(this.gameRules, optional -> {
            optional.ifPresent(rules -> this.gameRules = rules);
            this.minecraft.setScreenAndShow(this);
        }));
    }

    private void openHudStudio() {
        if (this.minecraft == null) return;
        this.minecraft.setScreenAndShow(new LatitudeHudStudioScreen(this));
    }

    // ── Begin Expedition ──

    private void beginExpedition() {
        if (this.minecraft == null) return;
        String worldName = this.worldNameField.getValue().trim();
        if (worldName.isEmpty()) worldName = "New World";
        String seed = this.seedField.getValue(); // raw — no client-side trim

        GameType gameMode = selectedModeIdx == 2 ? GameType.CREATIVE : GameType.SURVIVAL;
        boolean hardcore = selectedModeIdx == 1;
        Difficulty difficulty = hardcore ? Difficulty.HARD : Difficulty.NORMAL;

        // Always log the selection state at the create click — TEST 29 produced a world whose zone
        // didn't match what the player believed was selected, and the log had nothing to distinguish
        // "user re-clicked a band" from "selection state bug". Now it always says.
        LOGGER.info("[lat-ui] create-world spawn zone selection: random={} selectedZone={}", randomZone, selectedZone.name());

        // Random spawn zone resolves to a concrete band HERE, so everything downstream (launcher,
        // server spawn placement, saved state) keeps its existing single-band contract.
        LatitudeBands.Band spawnZone = this.selectedZone;
        if (randomZone) {
            LatitudeBands.Band[] all = LatitudeBands.Band.values();
            spawnZone = all[new java.util.Random().nextInt(all.length)];
            LOGGER.info("[lat-ui] Random spawn zone rolled: {}", spawnZone.name());
        }

        LatitudeWorldLauncher.beginExpedition(this.minecraft, this, this.holder,
                worldName, seed, this.selectedSize, spawnZone, currentWorldShape(),
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
            this.seedInput = seed.trim();
            this.seedField.setValue(this.seedInput);
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
        if (this.minecraft != null && (this.minecraft.gui.screen() == this || this.minecraft.gui.screen() == null)) {
            this.minecraft.setScreenAndShow(this.parent);
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
            applyPaneStripScroll(paneStripScroll - (int) Math.signum(horizontalAmount) * 28);
            return true;
        }
        if ((!tabbedMode || activeTab == 0) && mouseX >= Math.max(leftX, paneStripViewportLeft) && mouseX < Math.min(leftX + leftW, paneStripViewportRight) && mouseY >= panelTop && mouseY < panelBottom) {
            int viewportHeight = Math.max(0, leftViewportBottom - leftViewportTop);
            int maxScroll = Math.max(0, leftContentHeight - viewportHeight);
            if (maxScroll > 0 && verticalAmount != 0.0D) {
                leftScroll -= (int) Math.signum(verticalAmount) * 18;
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
                rightScroll -= (int) Math.signum(verticalAmount) * 18;
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
                settingsScroll -= (int) Math.signum(verticalAmount) * 18;
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
            applyPaneStripScroll(paneStripScroll - (int) Math.signum(verticalAmount) * 28);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
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

    // Ease each panel's display scroll toward its target once per rendered frame, then recompute all three
    // layouts from the advanced display so content (and its widgets) glides instead of snapping. delta is the
    // frame partial-tick, so the glide runs at a consistent speed regardless of framerate.
    private void advanceScrollAnimation(float delta) {
        leftScrollDisplay = easeScroll(leftScrollDisplay, leftScroll, leftMaxScroll, delta);
        rightScrollDisplay = easeScroll(rightScrollDisplay, rightScroll, rightMaxScroll, delta);
        settingsScrollDisplay = easeScroll(settingsScrollDisplay, settingsScroll, settingsMaxScroll, delta);
    }

    private static float easeScroll(float display, int target, int maxScroll, float delta) {
        float clampedTarget = Math.max(0f, Math.min(target, maxScroll));
        float d = display;
        if (d < 0f) d = 0f;
        if (d > maxScroll) d = maxScroll;
        float diff = clampedTarget - d;
        if (Math.abs(diff) < 0.5f) {
            return clampedTarget;
        }
        // Exponential ease-out; factor derived from delta so speed is framerate-independent. delta is ~1.0 per
        // tick, so at 60fps (~0.83 tick/frame) this settles in a few frames. Clamp the factor to [0,1].
        float factor = 1f - (float) Math.exp(-0.45f * Math.max(0.001f, delta) * 3f);
        if (factor < 0f) factor = 0f;
        if (factor > 1f) factor = 1f;
        return d + diff * factor;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        advanceScrollAnimation(delta);
        updateLeftLayout();
        updateRightLayout();
        updateSettingsLayout();
        int titlePaneX = threeCol ? rightX : 12;
        int titlePaneW = threeCol ? rightW : Math.max(1, this.width - 24);
        int headerBottom = tabbedMode ? tabStripY - 2 : panelTop;
        UiRect headerRect = new UiRect(titlePaneX, headerY, Math.max(1, titlePaneW), Math.max(1, headerBottom - headerY - 6));
        int headerLineY = headerRect.y;
        int wordmarkH = drawLatitudeWordmark(context, new UiRect(headerRect.x, headerLineY, headerRect.w, headerRect.h));
        if (wordmarkH > 0) {
            headerLineY += wordmarkH + 6;
        } else if (drawCenteredBoundedText(context, "LATITUDE", new UiRect(headerRect.x, headerLineY, headerRect.w, uiFontHeight()), GOLD, true, false)) {
            headerLineY += uiFontHeight() + 6;
        }
        if (drawCenteredBoundedText(context, "New World", new UiRect(headerRect.x, headerLineY, headerRect.w, uiFontHeight()), WARM_WHITE, true, false)) {
            headerLineY += uiFontHeight() + 4;
        }
        drawWrappedTextBlock(context, "Prepare your journey across the globe", new UiRect(headerRect.x, headerLineY, headerRect.w, Math.max(0, headerRect.bottom() - headerLineY)), MUTED, false, 2, true, true);

        if (tabbedMode) {
            drawTabStrip(context, mouseX, mouseY);
        }

        if (!tabbedMode || activeTab == 0) {
        drawViewportClippedPanel(context, leftX, panelTop, leftW, panelBottom - panelTop);
        int leftClipLeft = Math.max(leftX + 1, paneStripViewportLeft);
        int leftClipRight = Math.min(leftX + leftW - 1, paneStripViewportRight);
        // Draw the section heading BEFORE (outside) the scroll scissor, and start the scroll clip BELOW the
        // reserved heading band, so scrolled fields / atlas preview / small-world warning can never bleed over
        // the "World" title. Mirrors the Spawn Zone panel fix.
        if (threeCol) {
            drawInlineHeading(context, leftX, leftW, TAB_LABELS[0], GOLD);
        }
        int leftClipTop = threeCol ? headerBandBottom() : leftViewportTop;
        if (leftClipRight > leftClipLeft) {
            context.enableScissor(leftClipLeft, leftClipTop, leftClipRight, leftViewportBottom);
        int inputX = leftX + 4;
        int stepperBtnW = sizePrevBtn != null ? sizePrevBtn.getWidth() : 20;
        int labelColor = GOLD;
        int labelOff = 10;
        int leftTextWidth = Math.max(24, leftW - 8 - SCROLLBAR_GUTTER);
        if (shouldShowSmallWorldWarning()) {
            int warningHeight = getSmallWorldWarningHeight(leftTextWidth);
            int warningY = sizeFieldY - labelOff - warningHeight - 2;
            drawWrappedStyledTextBlock(context, SMALL_WORLD_WARNING, new UiRect(inputX, warningY, leftTextWidth, warningHeight), 0xFFF0A030, false, 2, true, true);
        }
        drawBoundedText(context, "World Name", new UiRect(inputX, worldFieldY - labelOff, leftTextWidth, uiFontHeight()), labelColor, false, false);
        drawBoundedText(context, "Seed", new UiRect(inputX, seedFieldY - labelOff, leftTextWidth, uiFontHeight()), labelColor, false, false);
        boolean shapeEnabled = isLatitudeWorld();
        drawBoundedText(context, "World Shape", new UiRect(inputX, worldShapeFieldY - labelOff, leftTextWidth, uiFontHeight()), shapeEnabled ? labelColor : DISABLED_COLOR, false, false);
        renderWorldShapeLabel(context, inputX + stepperBtnW + 4, worldShapeFieldY, leftTextWidth - stepperBtnW * 2 - 8, shapeEnabled);
        boolean sizeEnabled = isLatitudeWorld();
        drawBoundedText(context, "World Size", new UiRect(inputX, sizeFieldY - labelOff, leftTextWidth, uiFontHeight()), sizeEnabled ? labelColor : DISABLED_COLOR, false, false);
        renderSizeLabel(context, inputX + stepperBtnW + 4, sizeFieldY - 1, leftTextWidth - stepperBtnW * 2 - 8, sizeEnabled);
        int separatorY = inputBottomY - 2;
        context.fill(leftX + 4, separatorY, leftX + leftW - 4 - SCROLLBAR_GUTTER, separatorY + 1, PANEL_BORDER);
        boolean latWorld = isLatitudeWorld();
        int planisphereLabelY = leftPreviewTopY - uiFontHeight() - 6;
        drawCenteredBoundedText(context, "ATLAS", new UiRect(leftX + 4, planisphereLabelY, leftTextWidth, uiFontHeight()), latWorld ? GOLD : DISABLED_COLOR, false, true);
        if (leftPreviewBottomY - leftPreviewTopY >= 30) {
            if (latWorld) {
                renderPlanispherePreview(context, leftX + 4, leftPreviewTopY, leftX + leftW - 4 - SCROLLBAR_GUTTER, leftPreviewBottomY);
            } else {
                renderPlanisphereDisabled(context, leftX + 4, leftPreviewTopY, leftX + leftW - 4 - SCROLLBAR_GUTTER, leftPreviewBottomY);
            }
        }
        context.disableScissor();
        }
        drawPaneScrollbar(context, leftX, leftW, leftViewportTop, leftViewportBottom, leftContentHeight, Math.round(leftScrollDisplay));
        } // end tab 0 (World)

        if (!tabbedMode || activeTab == 1) {
        drawViewportClippedPanel(context, rightX, panelTop, rightW, panelBottom - panelTop);
        int paneTitleY = panelTop + 8;
        boolean latWorld = isLatitudeWorld();
        int rightTextWidth = Math.max(40, rightW - 8 - SCROLLBAR_GUTTER);
        // Draw the section heading BEFORE (outside) the scroll scissor, and start the scroll clip BELOW the
        // reserved heading band, so scrolling zone rows / description can never bleed over the "Spawn Zone" title.
        if (threeCol) {
            drawInlineHeading(context, rightX, rightW, TAB_LABELS[1], latWorld ? GOLD : DISABLED_COLOR);
        }
        int rightClipLeft = Math.max(rightX + 1, paneStripViewportLeft);
        int rightClipRight = Math.min(rightX + rightW - 1, paneStripViewportRight);
        int rightClipTop = threeCol ? headerBandBottom() : rightViewportTop;
        if (rightClipRight > rightClipLeft) {
        context.enableScissor(rightClipLeft, rightClipTop, rightClipRight, rightViewportBottom);
        if (!threeCol) {
        // Tabbed mode: the tab strip already labels this pane "Spawn Zone", so the in-panel title is redundant
        // and just eats vertical space — skip it (updateRightLayout zeroes its reserved height in tabbed mode so
        // the content moves up). Keep the instructional subtitle.
        drawWrappedTextBlock(context, "Choose the climate where your journey begins", new UiRect(rightX + 4, rightSubtitleY, rightTextWidth, Math.max(uiFontHeight(), rightDividerY - rightSubtitleY - 2)), latWorld ? MUTED : DISABLED_COLOR, false, 2, true, true);
        }
        if (latWorld) {
        // Reduce Motion holds the Atlas-style Gaussian crest / Random sweep at a static highlight (solid fill,
        // no travelling glow) instead of animating.
        boolean reduceMotion = com.example.globe.client.LatitudeConfig.reduceMotion;
        int barInset = 4;
        int barTotalW = rightW - barInset * 2 - SCROLLBAR_GUTTER;
        LatitudeBands.Band[] allBands = LatitudeBands.Band.values();
        for (int i = 0; i < allBands.length; i++) {
            int segX = rightX + barInset + (barTotalW * i / allBands.length);
            int segXEnd = rightX + barInset + (barTotalW * (i + 1) / allBands.length);
            boolean sel = !randomZone && allBands[i] == selectedZone;
            int bandColor = BAND_COLORS[i];
            if (sel) {
                // Selected zone: keep the gold edge, but fill the interior with the Atlas selected-band glow
                // crest sweeping left→right within this segment only (Peetsa: give the zone bar the same
                // Gaussian shimmer the Atlas selected band already has). Other segments stay static below.
                context.fill(segX, rightBarY, segXEnd, rightBarY + rightBarH, GOLD);
                if (reduceMotion) {
                    context.fill(segX + 1, rightBarY + 1, segXEnd - 1, rightBarY + rightBarH - 1, bandColor);
                } else {
                    LatitudePlanisphereRenderer.fillSelectedGlowSegment(context, segX + 1, rightBarY + 1, segXEnd - 1, rightBarY + rightBarH - 1, bandColor);
                }
            } else {
                int dimColor = (bandColor & 0x00FFFFFF) | (0x66 << 24);
                context.fill(segX, rightBarY, segXEnd, rightBarY + rightBarH, dimColor);
            }
        }
        // Random spawn zone: one crest travels the full bar left→right, taking on each segment's own color as it
        // passes (Peetsa; mirrors the Atlas Random sweep's per-band color pickup, here horizontal). Overlaid on
        // the dim segments drawn above, so segments the crest isn't over keep their exact static look.
        if (randomZone) {
            int barLeft = rightX + barInset;
            int barRight = rightX + barInset + barTotalW;
            if (reduceMotion) {
                // Static highlight: every segment shows its full, solid band color (no travelling crest).
                for (int i = 0; i < allBands.length; i++) {
                    int segX = rightX + barInset + (barTotalW * i / allBands.length);
                    int segXEnd = rightX + barInset + (barTotalW * (i + 1) / allBands.length);
                    context.fill(segX, rightBarY, segXEnd, rightBarY + rightBarH, BAND_COLORS[i]);
                }
            } else {
                LatitudePlanisphereRenderer.fillRandomGlowBar(context, barLeft, barRight, rightBarY, rightBarY + rightBarH, BAND_COLORS);
            }
        }

        int descPanelX = rightX + 2;
        int descPanelW = rightW - 4 - SCROLLBAR_GUTTER;
        int textMaxW = descPanelW - 12;
        String spawnLine = spawnZoneDescription();
        if (rightDescPanelH > 24) {
            context.fill(descPanelX, rightDescPanelY, descPanelX + descPanelW, rightDescPanelY + rightDescPanelH, PANEL_BG);
            int sideColor = randomZone ? 0xFF8C8078 : BAND_COLORS[selectedZone.ordinal()];
            context.fill(descPanelX, rightDescPanelY, descPanelX + 2, rightDescPanelY + rightDescPanelH, sideColor);

            int textX = descPanelX + 6;
            int ty = rightDescPanelY + 3;
            String zoneHeader = (randomZone ? "Random" : selectedZone.displayName()) + " zone selected";
            drawBoundedText(context, zoneHeader, new UiRect(textX, ty, textMaxW, uiFontHeight()), GOLD, true, true);
            ty += uiFontHeight() + 5;
            ty += drawWrappedTextBlock(context, spawnLine, new UiRect(textX, ty, textMaxW, Math.max(0, rightDescPanelY + rightDescPanelH - ty - uiFontHeight() - 4)), WARM_WHITE, false, 3, false, true);
            ty += 4;
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
        drawPaneScrollbar(context, rightX, rightW, rightViewportTop, rightViewportBottom, rightContentHeight, Math.round(rightScrollDisplay));
        } // end tab 1 (Spawn Zone)

        if (!tabbedMode || activeTab == 2) {
            // NOTE: updateSettingsLayout() already ran unconditionally at the top of extractRenderState (matching
            // how the World/Spawn Zone tabs only call their own layout once, at the top). A second call here was
            // redundant -- every Rules-panel widget's rectangle got set twice per extract, once via each call,
            // which is suspected to be the cause of the "two overlapping scrolling layers" glitch reported only
            // on this panel (the only one with this double-layout pattern).
            drawViewportClippedPanel(context, railX, panelTop, railW, panelBottom - panelTop);
            int settLabelX = railX + 4;
            int railClipLeft = Math.max(railX + 1, paneStripViewportLeft);
            int railClipRight = Math.min(railX + railW - 1, paneStripViewportRight);
            // Draw the section heading BEFORE (outside) the scroll scissor AND outside the horizontal
            // clip-width guard below, so the "Rules" title + divider draw EVERY FRAME, unconditionally,
            // independent of scroll position and of any scissor -- exactly like the World / Spawn Zone panels
            // above, which each draw drawInlineHeading before their own `if (clipRight > clipLeft)` guard.
            // Previously the rail's heading sat INSIDE that guard (nested one level deeper than the two siblings),
            // the lone structural deviation of the three panels; hoisting it out makes the rail heading's draw
            // path byte-for-byte parallel to the unaffected sibling headings. The World Type tooltip added below
            // (maybeDrawWorldTypeTooltip) is a deferred, hover-gated overlay drawn on a later stratum and never
            // touches this heading's draw or scissor state.
            if (threeCol) {
                drawInlineHeading(context, railX, railW, TAB_LABELS[2], GOLD);
            }
            // Clip content from just BELOW the heading band (headerBandBottom, ~panelTop+18) rather than from
            // settingsViewportTop (panelTop+36). settingsViewportTop reserves ~18px MORE than the one-line title
            // actually needs; clipping there left an invisible ~18px shelf under the "Rules" heading that
            // silently occluded any content scrolling up through it. The left/Spawn-Zone panels were already
            // fixed to clip at headerBandBottom() -- the rail was the one column that kept the stale +36 constant
            // for its clip, which is why the two prior "heading clip" passes didn't kill this. Layout / scroll /
            // hit-testing still use settingsViewportTop; only the visual clip moves up to hug the title.
            int railClipTop = settingsClipTop();
            if (railClipRight > railClipLeft) {
            context.enableScissor(railClipLeft, railClipTop, railClipRight, settingsViewportBottom);
            // (Tabbed mode: no in-panel "WORLD SETTINGS" header — the tab strip already labels this pane, and
            // drawing it here just clipped against the scissor and left the dead "invisible header bar".)
            drawSettingsRowLabel(context, "World Type", settLabelX, worldTypeRowY, MUTED);
            drawSettingsStepperValue(context, WORLD_TYPE_NAMES[worldTypeIdx], WORLD_TYPE_COLORS[worldTypeIdx], worldTypeRowY);
            maybeDrawWorldTypeTooltip(context, mouseX, mouseY);
            drawSettingsRowLabel(context, "Game Mode", settLabelX, modeRowY, MUTED);
            drawSettingsStepperValue(context, MODE_NAMES[selectedModeIdx], MODE_COLORS[selectedModeIdx], modeRowY);
            // World Type + Game Mode keep the classic label-above-stepper layout. The six rows below
            // (Commands / Compass / Structures / Bonus Chest / HUD Studio / Game Rules) are now iconography
            // rows that draw their OWN icon + label + On/Off state inline, so no separate row labels here.
            // Draw them INSIDE this same scissor so they clip at the viewport edges (partial "half rows")
            // exactly like the stepper labels above, instead of popping. They are addWidget'd (not auto-
            // rendered by super), so this is the only place they get drawn.
            renderSettingsScrollWidgets(context, mouseX, mouseY, delta);
            context.disableScissor();
            }
            drawPaneScrollbar(context, railX, railW, settingsViewportTop, settingsViewportBottom, settingsContentHeight, Math.round(settingsScrollDisplay));
        } // end tab 2 (Rules)

        if (!tabbedMode) {
            drawHorizontalScrollbar(context);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    // Single-line stepper value, vertically centered against the World Shape row's button height -- unlike
    // renderSizeLabel below (a multi-line block: name/dims/description), World Shape only ever shows one short
    // value ("Wide 2:1" / "Square 1:1"), so it's centered like the Rules-panel stepper rows instead of
    // top-anchored like the size block.
    private void renderWorldShapeLabel(GuiGraphicsExtractor context, int x, int rowY, int availW, boolean enabled) {
        String value = WORLD_SHAPE_NAMES[worldShapeIdx];
        int color = enabled ? WORLD_SHAPE_COLORS[worldShapeIdx] : DISABLED_COLOR;
        int btnH = worldShapePrevBtn != null ? worldShapePrevBtn.getHeight() : Math.max(18, 20);
        int drawY = rowY + Math.max(0, (btnH - uiFontHeight()) / 2);
        drawCenteredBoundedText(context, value, new UiRect(x, drawY, availW, uiFontHeight()), color, true, true);
    }

    private void renderSizeLabel(GuiGraphicsExtractor context, int x, int y, int availW, boolean enabled) {
        int idx = selectedSize.ordinal();
        String shortName = SIZE_SHORT_NAMES[idx];
        String diameter = worldDimsLabel();
        String desc = SIZE_DESCRIPTIONS[idx];

        int nameCol = enabled ? WARM_WHITE : DISABLED_COLOR;
        int subCol = enabled ? MUTED : DISABLED_COLOR;
        // Ginormous gets a little theatrical emphasis (Peetsa's ask): italic + an exclamation mark, since
        // it's the "a world that could take a lifetime to cross" biggest size.
        if (selectedSize == GlobeWorldSize.MASSIVE) {
            drawCenteredBoundedItalicText(context, shortName + "!", new UiRect(x, y, availW, uiFontHeight()), nameCol, true, true);
        } else {
            drawCenteredBoundedText(context, shortName, new UiRect(x, y, availW, uiFontHeight()), nameCol, true, true);
        }
        drawCenteredBoundedText(context, diameter, new UiRect(x, y + 11, availW, uiFontHeight()), subCol, false, true);
        drawWrappedTextBlock(context, desc, new UiRect(x, y + 22, availW, Math.max(uiFontHeight(), computeSizeLabelBottom(y, availW) - (y + 22))), subCol, false, 3, true, true);
    }

    private boolean shouldShowSmallWorldWarning() {
        // Retired: smaller worlds no longer carry a meaningful biome-diversity penalty (Mercator widens every
        // band + the representation work fills them), so the "fewer biome variants" warning is obsolete.
        return false;
    }

    private void renderPlanispherePreview(GuiGraphicsExtractor context, int areaLeft, int areaTop, int areaRight, int areaBottom) {
        long dbgStart = DEBUG_UI_SWITCH_LAG ? Util.getMillis() : 0L;
        String caption = worldDimsLabel();
        float labelScale = previewLabelScale(selectedSize);
        float captionScale = previewCaptionScale(selectedSize);
        int labelHeight = scaledFontHeight(labelScale);
        int captionHeight = scaledFontHeight(captionScale);
        int maxLabelWidth = 0;
        for (double deg : PREVIEW_LABEL_DEGREES) {
            maxLabelWidth = Math.max(maxLabelWidth, scaledTextWidth(formatDegree(deg), labelScale));
        }

        LatitudeBiomes.GlobeShape shape = currentWorldShape();
        // A Mercator globe is MERCATOR_ASPECT times as wide as it is tall for the same radius, so the
        // width-derived radius budget must shrink by that same factor (a Legacy square keeps the old /2).
        double widthDivisor = shape == LatitudeBiomes.GlobeShape.MERCATOR ? 2.0 * LatitudeBiomes.MERCATOR_ASPECT : 2.0;
        int labelPad = isTinyPreview(selectedSize) ? 10 : 8;
        int rightPadding = 8;
        int captionGap = Math.max(6, captionHeight / 2);
        int maxRadiusByWidth = Math.max(18, (int) ((areaRight - areaLeft - maxLabelWidth - labelPad - rightPadding) / widthDivisor));
        int maxRadiusByHeight = Math.max(18, (areaBottom - areaTop - captionHeight - captionGap) / 2);
        int radius = Math.round(Math.min(maxRadiusByWidth, maxRadiusByHeight) * previewDiscFill(selectedSize));
        // Classic (1:1) only, Mercator untouched (Peetsa 2026-07-08): a square's widthDivisor above is half
        // Mercator's (2.0 vs 4.0), so for the same panel Classic's width budget is roughly DOUBLE Mercator's --
        // in practice that makes Classic almost always height-bound (maxRadiusByHeight wins the min() above),
        // so previewDiscFill's per-size grading barely mattered and even Itty Bitty rendered near the height
        // ceiling (too large) with little headroom against real composition width, clipping at Tiny. This scales
        // the CLASSIC radius down after the shared budget/fill math, so Mercator's sizing is byte-identical.
        if (shape != LatitudeBiomes.GlobeShape.MERCATOR) {
            radius = Math.round(radius * CLASSIC_ATLAS_SCALE);
        }
        radius = Math.max(18, radius);

        PreviewLayout layout = null;
        while (radius >= 18) {
            layout = computePreviewLayout(areaLeft, areaTop, areaRight, areaBottom, radius, labelScale, captionScale, caption, shape);
            if (layout != null) {
                break;
            }
            radius -= 2;
        }
        if (layout == null) {
            return;
        }

        int frameBorder = previewFrameBorder(radius);
        LatitudePlanisphereRenderer.drawAtlasFrame(context,
                layout.globeLeft, layout.globeTop, layout.globeWidth, layout.globeHeight);
        LatitudePlanisphereRenderer.renderCompact(context,
                layout.globeLeft + frameBorder,
                layout.globeTop + frameBorder,
                layout.globeWidth - frameBorder * 2,
                layout.globeHeight - frameBorder * 2,
                randomZone ? null : selectedZone,
                shape);

        for (int i = 0; i < PREVIEW_LABEL_DEGREES.length; i++) {
            double deg = PREVIEW_LABEL_DEGREES[i];
            int color = !randomZone && isOnSelectedEdge(deg, selectedZone) ? GOLD : MUTED;
            drawScaledText(context, formatDegree(deg), layout.labelX, layout.labelYs[i], layout.labelScale, color, false);
        }
        drawScaledText(context, caption, layout.captionX, layout.captionY, layout.captionScale, MUTED, false);
        if (DEBUG_UI_SWITCH_LAG && Util.getMillis() <= debugSwitchSampleDeadlineMs) {
            long elapsed = Util.getMillis() - dbgStart;
            if (elapsed >= 1L) {
                LOGGER.info("[lat-ui] switchLag seq={} worldType={} section=planispherePreview ms={}", debugSwitchSeq, currentWorldTypeName(), elapsed);
            }
        }
    }

    private void renderSpawnZoneDisabled(GuiGraphicsExtractor context) {
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

    private void renderPlanisphereDisabled(GuiGraphicsExtractor context, int areaLeft, int areaTop, int areaRight, int areaBottom) {
        long dbgStart = DEBUG_UI_SWITCH_LAG ? Util.getMillis() : 0L;
        int areaW = Math.max(0, areaRight - areaLeft);
        int areaH = Math.max(0, areaBottom - areaTop);
        if (areaW <= 6 || areaH <= 6) return;

        int pad = 6;
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
        int accentInset = 4;
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

    // Width of the vanilla map-texture frame border that surrounds the atlas (also the amount the climate map
    // is inset inside the frame). Shared by the layout (label alignment) and the render (atlas inset + frame).
    private int previewFrameBorder(int radius) {
        return Math.max(4, Math.round(radius * 0.16f));
    }

    private PreviewLayout computePreviewLayout(int areaLeft, int areaTop, int areaRight, int areaBottom,
                                               int radius, float labelScale, float captionScale, String caption,
                                               LatitudeBiomes.GlobeShape shape) {
        // globeHeight (N-S) is always driven by radius, same meaning in both shapes. globeWidth (E-W) matches
        // it 1:1 for a Legacy square, or widens by MERCATOR_ASPECT for a Mercator rectangle — mirroring the
        // real world's own getActiveXRadiusBlocks() relationship between the X and Z radii.
        int globeHeight = radius * 2;
        int globeWidth = shape == LatitudeBiomes.GlobeShape.MERCATOR
                ? (int) Math.round(globeHeight * LatitudeBiomes.MERCATOR_ASPECT)
                : globeHeight;
        int labelWidth = 0;
        for (double deg : PREVIEW_LABEL_DEGREES) {
            labelWidth = Math.max(labelWidth, scaledTextWidth(formatDegree(deg), labelScale));
        }
        int labelPad = Math.max(8, Math.round(radius * 0.10f));
        int rightPadding = 8;
        int compositionWidth = globeWidth + labelPad + labelWidth + rightPadding;
        if (compositionWidth > areaRight - areaLeft) {
            return null;
        }

        int captionHeight = scaledFontHeight(captionScale);
        int captionGap = Math.max(6, Math.round(radius * 0.10f));
        int compositionHeight = globeHeight + captionGap + captionHeight;
        if (compositionHeight > areaBottom - areaTop) {
            return null;
        }

        // Center the MAP itself, not the map+label composition: centering the composition pushed the
        // atlas left of visual center by half the label column (TEST 28: "make the atlas a little more
        // centered"). The labels take the right-hand slack; when there is none, this degrades to the
        // old composition-centered position.
        int idealGlobeLeft = areaLeft + (areaRight - areaLeft - globeWidth) / 2;
        int maxGlobeLeft = areaRight - rightPadding - labelWidth - labelPad - globeWidth;
        int globeLeft = Math.max(areaLeft, Math.min(idealGlobeLeft, maxGlobeLeft));
        // Top-aligned, not v-centered: centering parked the map halfway down the panel with dead space
        // between it and the ATLAS label above (TEST 29: "atlas still is too low"). The fit loop already
        // guarantees the composition fits, so leftover space falls harmlessly below the caption.
        int globeTop = areaTop;
        int globeCenterY = globeTop + radius;
        int labelX = globeLeft + globeWidth + labelPad;
        int labelHeight = scaledFontHeight(labelScale);
        // The map frame insets the atlas content by previewFrameBorder(radius) on each side, so latitude labels
        // must map to the INNER atlas radius to stay aligned with the inset latitude lines.
        int[] labelYs = computePreviewLabelYs(globeCenterY, radius - previewFrameBorder(radius), labelHeight);
        int lastLabelBottom = labelYs[labelYs.length - 1] + labelHeight;
        int captionY = Math.max(globeTop + globeHeight + captionGap, lastLabelBottom + 4);
        if (captionY + captionHeight > areaBottom) {
            return null;
        }

        int captionX = globeLeft + (globeWidth - scaledTextWidth(caption, captionScale)) / 2;
        return new PreviewLayout(globeLeft, globeTop, globeWidth, globeHeight, labelX, labelYs, captionX, captionY, labelScale, captionScale);
    }

    private int[] computePreviewLabelYs(int globeCenterY, int radius, int labelHeight) {
        int n = PREVIEW_LABEL_DEGREES.length;
        int[] trueYs = new int[n];
        for (int i = 0; i < n; i++) {
            int yOff = (int) Math.round(radius * PREVIEW_LABEL_DEGREES[i] / 90.0);
            trueYs[i] = globeCenterY + yOff - labelHeight / 2;
        }

        // De-collide downward so labels never overlap, cascading forward from the 0° anchor...
        int comfortGap = isTinyPreview(selectedSize) ? Math.max(labelHeight + 1, 9) : Math.max(labelHeight, 8);
        int[] labelYs = new int[n];
        labelYs[0] = trueYs[0];
        for (int i = 1; i < n; i++) {
            labelYs[i] = Math.max(trueYs[i], labelYs[i - 1] + comfortGap);
        }

        // ...then, if the cascade pushed the bottom label past the atlas bottom edge (TEST 4 A3: "latitudes
        // are super crammed"), compress the CASCADE'S GAPS back toward the bare no-overlap floor instead of
        // sliding the whole column up. A uniform column-shift used to drag the 0° label away from its true
        // position (its own gridline) at small radii -- exactly small-world "0° almost at the pole" (TEST 32):
        // 0° starts at its true, correct spot and a big enough overflow shifted EVERY label, including it,
        // by the same amount. Compression instead only tightens the gaps that have slack above the hard
        // floor, and 0° -- gap-free by definition -- never moves.
        int atlasBottom = globeCenterY + radius;
        int overflow = (labelYs[n - 1] + labelHeight) - atlasBottom;
        if (overflow > 0) {
            int hardFloorGap = labelHeight;
            int[] gaps = new int[n];
            int totalSlack = 0;
            for (int i = 1; i < n; i++) {
                gaps[i] = labelYs[i] - labelYs[i - 1];
                totalSlack += Math.max(0, gaps[i] - hardFloorGap);
            }
            if (totalSlack > 0) {
                double shrink = Math.max(0.0, 1.0 - (double) overflow / totalSlack);
                for (int i = 1; i < n; i++) {
                    int slackPortion = Math.max(0, gaps[i] - hardFloorGap);
                    int newGap = hardFloorGap + (int) Math.round(slackPortion * shrink);
                    labelYs[i] = labelYs[i - 1] + newGap;
                }
            }
            // else: no slack left to give back (six labels genuinely don't fit even flush-packed at this
            // radius) -- accept the residual overflow rather than moving the 0° anchor off its gridline.
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

    // Classic (1:1 square) atlas radius multiplier, applied AFTER the shared width/height budget + disc-fill
    // math above -- Mercator (2:1) never reads this constant. See the comment at the radius computation in
    // renderPlanispherePreview for why Classic needed its own shrink (2026-07-08). The multiplier scales
    // EVERY size down by the same fraction, so it shrinks the absolute pixel gap between Itty Bitty and
    // Ginormous along with the overall size -- too aggressive a value (0.62 first try) made the whole
    // per-size range read as one bunched-together blob; 0.62 was too small, 1.0 was the original
    // oversized/clipping value this constant exists to fix. 0.82 is the current happy medium.
    private static final float CLASSIC_ATLAS_SCALE = 0.82f;

    private float previewDiscFill(GlobeWorldSize size) {
        // Fraction of the available area the atlas disc fills. Still graded by world size (bigger world = bigger
        // disc) so the preview conveys relative scale, but nudged up across the board per live feedback that the
        // atlas read too small. Safe to raise: renderPlanispherePreview's fit loop shrinks the radius until the
        // composition fits, so an over-large request can never overflow the panel.
        return switch (size) {
            case ITTY_BITTY -> 0.70f;
            case TINY -> 0.78f;
            case SMALL -> 0.84f;
            case REGULAR -> 0.89f;
            case LARGE -> 0.94f;
            case MASSIVE -> 0.99f;
        };
    }

    private int scaledTextWidth(String text, float scale) {
        return Math.round(this.font.width(text) * scale);
    }

    private int scaledFontHeight(float scale) {
        return Math.max(5, Math.round(this.font.lineHeight * scale));
    }

    private void drawSettingsRowLabel(GuiGraphicsExtractor context, String label, int x, int rowY, int color) {
        int labelY = rowY - 10;
        if (labelY + uiFontHeight() <= settingsClipTop() || labelY >= settingsViewportBottom) {
            return;
        }
        drawBoundedText(context, label, new UiRect(x, labelY, Math.max(20, railW - 8 - SCROLLBAR_GUTTER), uiFontHeight()), color, false, true);
    }

    private void drawSettingsStepperValue(GuiGraphicsExtractor context, String text, int color, int rowY) {
        if (rowY + uiFontHeight() <= settingsClipTop() || rowY >= settingsViewportBottom) {
            return;
        }
        int stepperW = worldTypePrevBtn != null ? worldTypePrevBtn.getWidth() : 20;
        int safeLeft = railX + 4 + stepperW + 6;
        int safeRight = railX + railW - 4 - stepperW - 6 - SCROLLBAR_GUTTER;
        int safeWidth = Math.max(20, safeRight - safeLeft);
        String fitted = ellipsizeToWidth(text, safeWidth);
        int textW = uiTextWidth(fitted);
        int btnH = worldTypePrevBtn != null ? worldTypePrevBtn.getHeight() : 20;
        int drawY = rowY + Math.max(0, (btnH - uiFontHeight()) / 2);
        drawBoundedText(context, fitted, new UiRect(safeLeft + Math.max(0, (safeWidth - textW) / 2), drawY, safeWidth, uiFontHeight()), color, true, true);
    }

    // Plain-language hover explainer for the Rules-rail "World Type" stepper. The stepper has no single
    // hoverable widget (only the two 20px arrow buttons are widgets; the label + value in between are drawn
    // text), so we hit-test the whole row rect ourselves and reuse the same SIDE-anchored tooltip idiom as
    // the RulesIconRow rows below it: open LEFT into the empty middle of the screen, fall back to the right,
    // then clamp on-screen -- never covering the hovered row or the rail. Deferred (setTooltipForNextFrame),
    // so it draws on top after scissors are disabled. Only fires while the row is inside the rail viewport.
    private void maybeDrawWorldTypeTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int btnH = worldTypePrevBtn != null ? worldTypePrevBtn.getHeight() : 20;
        final int rowX = railX + 4;
        final int rowW = Math.max(20, railW - 8 - SCROLLBAR_GUTTER);
        final int rowTop = worldTypeRowY - 10;                 // includes the label drawn one labelGap above
        final int rowBottom = worldTypeRowY + btnH;            // through the bottom of the arrow buttons
        boolean hovered = mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= rowTop && mouseY <= rowBottom;
        // Hover gate on the SAME line the row is clipped to (settingsClipTop), so the tooltip is available for
        // exactly the visible portion of the row -- never for a slice that's scrolled up under the heading.
        if (!hovered || rowBottom <= settingsClipTop() || rowTop >= settingsViewportBottom) {
            return;
        }
        Component tip = Component.literal(
                "Latitude builds this mod's world — climate zones that change with latitude, the compass, "
                        + "and the expedition HUD. Vanilla and Vanilla Superflat make standard Minecraft "
                        + "worlds with Latitude's features switched off.");
        int wrapW = Math.max(120, Math.min(220, this.width - 24));
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(tip, wrapW);
        final int anchorX = rowX;
        final int anchorW = rowW;
        final int anchorY = Math.max(settingsClipTop(), rowTop);
        net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner sidePositioner =
                (sw, sh, mx, my, tw, th) -> {
                    int gap = 6;
                    int px;
                    if (anchorX - gap - tw >= 0) {
                        px = anchorX - gap - tw;                 // room on the left: open into the middle
                    } else if (anchorX + anchorW + gap + tw <= sw) {
                        px = anchorX + anchorW + gap;            // else open to the right of the row
                    } else {
                        px = sw - tw;                            // no room either side: clamp to right edge
                    }
                    px = Math.max(0, Math.min(px, sw - tw));
                    int py = Math.max(0, Math.min(anchorY, sh - th));
                    return new org.joml.Vector2i(px, py);
                };
        context.setTooltipForNextFrame(this.font, lines, sidePositioner, mouseX, mouseY, true);
    }

    // ── Accessibility (Peetsa 2026-07-11) ──
    // The Accessibility dropdown (LatitudeConfig.accessibilityMode, read live each frame) biases the whole
    // create screen toward legibility. All the color/alpha math is the pure, unit-tested
    // core.ui.AccessibilityPalette; these are the thin applications. HIGH_CONTRAST brightens the muted
    // subtitles/labels/degree numbers, forces text opaque, and strengthens the panel borders + gives each
    // rule icon a dark backing plate. COLORBLIND leaves the words alone (they carry their own meaning) but
    // remaps the compass icon's red north needle (in RulesIcons) to a CVD-safe color.

    private static AccessibilityMode a11yMode() {
        AccessibilityMode m = LatitudeConfig.accessibilityMode;
        return m == null ? AccessibilityMode.STANDARD : m;
    }

    /** All panel/HUD body text is routed through here at the leaf draw helpers: HIGH_CONTRAST lifts dim
     *  greys to a legible luminance and forces full opacity; other modes are the identity. Idempotent. */
    private static int a11yText(int argb) {
        return AccessibilityPalette.adjustPanelText(a11yMode(), argb);
    }

    /** Panel borders / dividers: brightened under HIGH_CONTRAST so section framing reads clearly. */
    private static int a11yBorder(int argb) {
        return AccessibilityPalette.adjustMuted(a11yMode(), argb);
    }

    private void drawUiText(GuiGraphicsExtractor context, String text, int x, int y, int color, boolean shadow) {
        context.text(this.font, text, x, y, a11yText(color), shadow);
    }

    /**
     * The LATITUDE wordmark (TEST 29: "I thought 'LATITUDE' at the top was going to look a little more
     * special"): scaled-up, letter-spaced gold with a dark-bronze letterpress under-layer and flanking
     * rules with diamond tips — chartroom-brass, matching the panel-heading motif. Returns the height
     * consumed, or 0 when the rect can't fit it (caller falls back to the plain line).
     *
     * <p>The drawing now lives in the shared {@link LatitudeWordmark} helper so the bespoke loading overlay
     * renders the identical nameplate (creative-director loading-look review, 2026-07-11 §4 R1). The create
     * screen calls it at full strength (scale 1.5, 4 sparkles); Reduce Motion is honoured inside the helper.
     * NOTE: the shared helper carries the unified wordmark gold {@code 0xFFE8B64A} (that review's F2) — a
     * touch brighter than the screen-wide {@link #GOLD}; the screen-wide gold is unchanged everywhere else.
     */
    private int drawLatitudeWordmark(GuiGraphicsExtractor context, UiRect rect) {
        return LatitudeWordmark.draw(context, this.font, rect.x, rect.y, rect.w, rect.h,
                1.5f, 4, com.example.globe.client.LatitudeConfig.reduceMotion);
    }

    /** Smooth flowing rainbow gradient, italicized — the selected "Random" spawn-zone row's name treatment
     *  (TEST 29). Shares the one gradient helper with the compass/HUD-Studio rainbows so they match.
     *  {@code bounce} applies the same per-letter sine wave the other selected zone rows use (ZONE_BOUNCE_*
     *  constants), so the Random row's letters bob in lockstep with everyone else's while keeping its
     *  flowing gradient coloring and italics. */
    private void drawRainbowItalicUiText(GuiGraphicsExtractor context, String text, int x, int y, boolean bounce) {
        int cx = x;
        int visibleIdx = 0;
        int visibleCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') visibleCount++;
        }
        double now = System.currentTimeMillis() / 1000.0;
        double omega = 2.0 * Math.PI / ZONE_BOUNCE_PERIOD_SEC;
        boolean reduceMotion = com.example.globe.client.LatitudeConfig.reduceMotion;
        var m = context.pose();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Component ch = Component.literal(String.valueOf(c)).withStyle(net.minecraft.ChatFormatting.ITALIC);
            int color = 0xFF000000 | com.example.globe.client.RainbowText.flowingColor(
                    visibleIdx, visibleCount, com.example.globe.core.ui.FlowingGradient.DEFAULT_CYCLE_SECONDS);
            // Sub-pixel vertical offset: translate the pose per glyph instead of rounding dy to whole pixels,
            // so the bob glides continuously (integer rounding made 1.5px amplitude step between ~3 discrete
            // rows, which read as choppy/laggy). Only the selected Random row bounces, so the per-letter
            // push/pop cost is trivial.
            float dy = 0f;
            if (bounce && !reduceMotion && c != ' ') {
                double phase = now * omega - visibleIdx * ZONE_BOUNCE_LETTER_PHASE;
                dy = (float) (Math.sin(phase) * ZONE_BOUNCE_AMPLITUDE_PX);
            }
            if (dy != 0f) {
                m.pushMatrix();
                m.translate(0f, dy);
                context.text(this.font, ch, cx, y, a11yText(color), true);
                m.popMatrix();
            } else {
                context.text(this.font, ch, cx, y, a11yText(color), true);
            }
            if (c != ' ') visibleIdx++;
            cx += this.font.width(ch);
        }
    }

    /** Draws the selected zone's name with each letter offset vertically by a slow sine wave, phased by
     *  letter index so a soft crest travels through the word. Per-glyph x-advance uses the font's char
     *  widths, the same idiom the Random rainbow / atlas crest draws use. Subtle by design (see constants). */
    private void drawBouncingUiText(GuiGraphicsExtractor context, String text, int x, int y, int color, boolean shadow) {
        double now = System.currentTimeMillis() / 1000.0;
        double omega = 2.0 * Math.PI / ZONE_BOUNCE_PERIOD_SEC;
        boolean reduceMotion = com.example.globe.client.LatitudeConfig.reduceMotion;
        int cx = x;
        int letterIdx = 0;
        var m = context.pose();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String s = String.valueOf(c);
            if (c != ' ') {
                // Sub-pixel vertical offset via pose translate rather than rounding dy to whole pixels: at 1.8px
                // amplitude integer rounding stepped between only ~3 discrete rows and lingered on each (looked
                // laggy). Only one selected row bounces at a time, so the per-glyph push/pop is trivially cheap.
                float dy = reduceMotion ? 0f : (float) (Math.sin(now * omega - letterIdx * ZONE_BOUNCE_LETTER_PHASE) * ZONE_BOUNCE_AMPLITUDE_PX);
                if (dy != 0f) {
                    m.pushMatrix();
                    m.translate(0f, dy);
                    context.text(this.font, s, cx, y, a11yText(color), shadow);
                    m.popMatrix();
                } else {
                    context.text(this.font, s, cx, y, a11yText(color), shadow);
                }
                letterIdx++;
            }
            cx += this.font.width(s);
        }
    }

    /** Multiplies an ARGB color's RGB channels by {@code mult} (clamped to 255), preserving alpha. Used for the
     *  wordmark's gentle gold "breath" lift. */
    private static int liftBrightness(int argb, float mult) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * mult));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * mult));
        int b = Math.min(255, Math.round((argb & 0xFF) * mult));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Gentle brightness shimmer for the selected zone's left accent tab: a slow sine on the base color,
     *  +/-ZONE_TAB_SHIMMER_AMPLITUDE. Explicitly milder than the atlas band's Gaussian crest. Preserves alpha.
     *  Reduce Motion holds it at a steady mid brightness (mult 1.0). */
    private static int shimmerAccentColor(int argb) {
        double s = com.example.globe.client.LatitudeConfig.reduceMotion
                ? 0.0
                : Math.sin(System.currentTimeMillis() / 1000.0 * (2.0 * Math.PI / ZONE_TAB_SHIMMER_PERIOD_SEC));
        float mult = (float) (1.0 + ZONE_TAB_SHIMMER_AMPLITUDE * s);
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * mult));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * mult));
        int b = Math.min(255, Math.round((argb & 0xFF) * mult));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawCenteredUiText(GuiGraphicsExtractor context, String text, int cx, int y, int color, boolean shadow) {
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

    private boolean drawBoundedText(GuiGraphicsExtractor context, String text, UiRect rect, int color, boolean shadow, boolean ellipsize) {
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

    private boolean drawBoundedStyledText(GuiGraphicsExtractor context, Component text, UiRect rect, int color, boolean shadow, boolean ellipsize) {
        if (!fitsHeight(rect.h)) {
            return false;
        }
        String fitted = ellipsize ? ellipsizeToWidth(text.getString(), rect.w) : text.getString();
        if (fitted.isEmpty() || (!ellipsize && !fitsWidth(fitted, rect.w))) {
            return false;
        }
        int drawX = clampToRect(rect.x, uiTextWidth(fitted), rect.x, rect.right());
        int drawY = clampToRect(rect.y, uiFontHeight(), rect.y, rect.bottom());
        context.text(this.font, Component.literal(fitted).setStyle(text.getStyle().withItalic(true)), drawX, drawY, a11yText(color), shadow);
        return true;
    }

    private int drawWrappedStyledTextBlock(GuiGraphicsExtractor context, Component text, UiRect rect, int color, boolean shadow, int maxLines, boolean center, boolean optional) {
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
                    context.text(this.font, Component.literal(fitted).setStyle(lineText.getStyle().withItalic(true)), drawX, y, a11yText(color), shadow);
                }
            } else {
                drawBoundedStyledText(context, lineText, new UiRect(rect.x, y, rect.w, uiFontHeight()), color, shadow, true);
            }
            y += uiFontHeight();
        }
        return drawCount * uiFontHeight();
    }

    private boolean drawCenteredBoundedText(GuiGraphicsExtractor context, String text, UiRect rect, int color, boolean shadow, boolean ellipsize) {
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

    /** Same as {@link #drawCenteredBoundedText} but italicized (the "Ginormous!" size-name treatment). */
    private boolean drawCenteredBoundedItalicText(GuiGraphicsExtractor context, String text, UiRect rect, int color, boolean shadow, boolean ellipsize) {
        if (!fitsHeight(rect.h)) {
            return false;
        }
        String fitted = ellipsize ? ellipsizeToWidth(text, rect.w) : text;
        if (fitted.isEmpty() || (!ellipsize && !fitsWidth(fitted, rect.w))) {
            return false;
        }
        int drawX = rect.x + Math.max(0, (rect.w - uiTextWidth(fitted)) / 2);
        int drawY = clampToRect(rect.y, uiFontHeight(), rect.y, rect.bottom());
        context.text(this.font, Component.literal(fitted).withStyle(ChatFormatting.ITALIC), drawX, drawY, a11yText(color), shadow);
        return true;
    }

    private int drawWrappedTextBlock(GuiGraphicsExtractor context, String text, UiRect rect, int color, boolean shadow, int maxLines, boolean center, boolean optional) {
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

    private void drawScaledText(GuiGraphicsExtractor context, String text, int x, int y, float scale, int color, boolean shadow) {
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        context.text(this.font, text, 0, 0, a11yText(color), shadow);
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

    private void drawPanel(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        // Border (brightened under HIGH_CONTRAST so section framing reads clearly)
        int border = a11yBorder(PANEL_BORDER);
        context.fill(x, y, x + w, y + 1, border);
        context.fill(x, y + h - 1, x + w, y + h, border);
        context.fill(x, y, x + 1, y + h, border);
        context.fill(x + w - 1, y, x + w, y + h, border);
        // Fill
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_BG);
        // Subtle grid decoration
        drawGridDecoration(context, x + 1, y + 1, w - 2, h - 2);
    }

    private static void drawGridDecoration(GuiGraphicsExtractor context, int x, int y, int w, int h) {
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

    private void drawCenteredString(GuiGraphicsExtractor context, String text, int cx, int y, int color, boolean shadow) {
        int textW = this.font.width(text);
        context.text(this.font, text, cx - textW / 2, y, a11yText(color), shadow);
    }

    private void drawViewportClippedPanel(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        int clipLeft = Math.max(x, paneStripViewportLeft);
        int clipRight = Math.min(x + w, paneStripViewportRight);
        if (clipRight <= clipLeft) {
            return;
        }
        context.enableScissor(clipLeft, y, clipRight, y + h);
        drawPanel(context, x, y, w, h);
        context.disableScissor();
    }

    private void drawInlineHeading(GuiGraphicsExtractor context, int paneX, int paneW, String label, int labelColor) {
        int headingY = panelTop + 6;
        int availableW = paneW - 12;
        if (availableW <= 0) return;
        int textW = uiTextWidth(label);
        int lineGap = 6;
        int lineLen = Math.max(10, (availableW - textW - lineGap * 2) / 2);
        int centerX = paneX + paneW / 2;
        int lineY = headingY + uiFontHeight() / 2;
        int lineLeftStart = centerX - (textW / 2) - lineGap - lineLen;
        int lineRightStart = centerX + (textW / 2) + lineGap;

        context.fill(lineLeftStart, lineY, lineLeftStart + lineLen, lineY + 1, PANEL_BORDER);
        context.fill(lineRightStart, lineY, lineRightStart + lineLen, lineY + 1, PANEL_BORDER);

        drawCenteredBoundedText(context, label,
                new UiRect(paneX + 4, headingY, paneW - 8, uiFontHeight()),
                labelColor, true, true);
    }

    /** Bottom Y of the reserved, non-scrolling heading strip drawn by {@link #drawInlineHeading} (three-column
     *  mode). Scroll scissors start here so list/description content clips BELOW the section title + divider and
     *  can never bleed over it. Mirrors the Rules panel, which already reserves its own header band. */
    private int headerBandBottom() {
        // headingY = panelTop + 6; the title text is one line tall, so a few px of clearance below it.
        return panelTop + 6 + uiFontHeight() + 3;
    }

    /** THE single top-of-scroll boundary for the Rules rail. Everything in the rail's scroll path -- the outer
     *  visual scissor, the per-row scissor, every per-widget visibility gate, every drawn-label cull, and every
     *  hover/click hit-test -- MUST reference THIS one line so a row's label, its widgets, its plate and its hover
     *  all clip/disable at the SAME boundary, continuously, as it slides under the "Rules" heading. In three-column
     *  mode that boundary hugs the heading band (panelTop+18); the earlier code culled/gated at settingsViewportTop
     *  (panelTop+36) while the scissor already clipped at +18, so text clipped smoothly but the steppers/icon-rows
     *  POPPED in/out 18px later -- the "gobbling / popping" scroll. In tabbed mode the tab strip labels the pane, so
     *  there is no heading band and the boundary is simply settingsViewportTop (== panelTop+8, no gap, no pop). */
    private int settingsClipTop() {
        return threeCol ? headerBandBottom() : settingsViewportTop;
    }

    private void drawPaneScrollbar(GuiGraphicsExtractor context, int paneX, int paneW, int viewportTop, int viewportBottom,
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
        int thumbH = Math.max(18, viewportHeight * viewportHeight / Math.max(1, contentHeight));
        int thumbY = viewportTop + (viewportHeight - thumbH) * scrollAmount / maxScroll;
        int thumbLeft = Math.max(trackX - 1, paneStripViewportLeft);
        int thumbRight = Math.min(trackX + 2, paneStripViewportRight);
        if (thumbRight > thumbLeft) {
            context.fill(thumbLeft, thumbY, thumbRight, thumbY + thumbH, GOLD);
        }
    }

    private void drawTabStrip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
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

    private void drawHorizontalScrollbar(GuiGraphicsExtractor context) {
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

    /** Single source of truth for "which shape is this screen about to create," derived from worldShapeIdx.
     *  Every consumer (the dims label, the atlas preview layout/render, and the beginExpedition call) branches
     *  off this instead of duplicating the index-to-enum mapping. */
    private LatitudeBiomes.GlobeShape currentWorldShape() {
        return worldShapeIdx == 0 ? LatitudeBiomes.GlobeShape.MERCATOR : LatitudeBiomes.GlobeShape.CLASSIC;
    }

    /** Package-visible accessor so LatitudeWorldLauncher's loading-summary formatting can source the shape's
     *  display name ("Wide 2:1" / "Square 1:1") from this single WORLD_SHAPE_NAMES array instead of duplicating
     *  the literal — mirrors currentWorldShape() being the one place the index-to-enum mapping lives. */
    static String worldShapeDisplayName(LatitudeBiomes.GlobeShape shape) {
        return WORLD_SHAPE_NAMES[shape == LatitudeBiomes.GlobeShape.MERCATOR ? 0 : 1];
    }

    /** World extent label, honest about whichever shape is currently selected: Mercator's E-W spans 4x the
     *  latitude radius (the border = 2*xRadius = 4*zRadius) and N-S spans 2x, so a Tiny Mercator world reads
     *  "20,000 × 10,000 blocks" — matching the in-game "world border is 20000 wide" message. Legacy 1:1 is a
     *  true square, both axes = 2x the radius (matching getActiveXRadiusBlocks() == zRadius for CLASSIC). */
    private String worldDimsLabel() {
        int z = selectedSize.borderRadiusBlocks;
        if (currentWorldShape() == LatitudeBiomes.GlobeShape.MERCATOR) {
            return formatDiameter(z * 4) + " × " + formatDiameter(z * 2) + " blocks";
        }
        return formatDiameter(z * 2) + " × " + formatDiameter(z * 2) + " blocks";
    }

    // ══════════════════════════════════════════════════════════════
    // Zone Row Widget
    // ══════════════════════════════════════════════════════════════

    private static final class PreviewLayout {
        private final int globeLeft;
        private final int globeTop;
        private final int globeWidth;
        private final int globeHeight;
        private final int labelX;
        private final int[] labelYs;
        private final int captionX;
        private final int captionY;
        private final float labelScale;
        private final float captionScale;

        private PreviewLayout(int globeLeft, int globeTop, int globeWidth, int globeHeight,
                              int labelX, int[] labelYs, int captionX, int captionY,
                              float labelScale, float captionScale) {
            this.globeLeft = globeLeft;
            this.globeTop = globeTop;
            this.globeWidth = globeWidth;
            this.globeHeight = globeHeight;
            this.labelX = labelX;
            this.labelYs = labelYs;
            this.captionX = captionX;
            this.captionY = captionY;
            this.labelScale = labelScale;
            this.captionScale = captionScale;
        }
    }

    private class ZoneRowWidget extends AbstractWidget {
        /** The row's band, or null for the "Random" row (rolled to a concrete band at create time). */
        private final LatitudeBands.Band band;

        ZoneRowWidget(int x, int y, int w, int h, LatitudeBands.Band band) {
            super(x, y, w, h, Component.literal(band == null ? "Random" : band.displayName()));
            this.band = band;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            // Clamp the click point to the same vertical clip rect extractWidgetRenderState scissors to (see
            // there for the threeCol heading-band note), so a click only registers where the row is actually
            // drawn -- the row's full un-clipped bounding box (checked by super) can extend above/below that
            // into the title or the panel edge once it's mid-scroll.
            if (!super.isMouseOver(mouseX, mouseY)) return false;
            int rowClipTop = threeCol ? headerBandBottom() : rightViewportTop;
            return mouseY >= rowClipTop && mouseY < rightViewportBottom;
        }

        private void select() {
            randomZone = this.band == null;
            if (this.band != null) {
                selectedZone = this.band;
            }
        }

        @Override
        public void onClick(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
            // H5 fix (UI audit 2026-07-10): mouse-select was silent while keyboard-select clicked. Play the
            // same vanilla UI click here so picking a spawn zone with the mouse gives identical feedback.
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            select();
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
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            boolean selected = this.band == null ? randomZone : (!randomZone && selectedZone == this.band);
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();

            // Clip to the Spawn Zone panel viewport so a row scrolling past the top/bottom edge renders as a
            // partial "half row" (smooth) instead of the whole widget popping in/out. Rows extract during
            // super.extractRenderState() with no outer scissor active, so this enable/disable pair is balanced.
            int clipLeft = Math.max(rightX + 1, paneStripViewportLeft);
            int clipRight = Math.min(rightX + rightW - 1, paneStripViewportRight);
            // In three-column mode start the clip below the reserved heading band so a row scrolling up renders
            // as a partial "half row" beneath the "Spawn Zone" title instead of over it (matches the main-panel
            // scissor). Tabbed mode has no in-panel heading, so clip from the viewport top as before.
            int rowClipTop = threeCol ? headerBandBottom() : rightViewportTop;
            boolean clipped = clipRight > clipLeft && rightViewportBottom > rowClipTop;
            if (clipped) {
                context.enableScissor(clipLeft, rowClipTop, clipRight, rightViewportBottom);
            }

            if (selected) {
                // Warm gold background highlight
                context.fill(x, y, x + w, y + h, 0x40D4A74A);
                // Native band color left accent border (3px wide); neutral parchment-gray for Random.
                // A picked band's tab shimmers gently in brightness (UI round 13); Random keeps its static tab.
                int bandColor = this.band == null ? 0xFF8C8078 : shimmerAccentColor(BAND_COLORS[this.band.ordinal()]);
                context.fill(x, y, x + 3, y + h, bandColor);
            }

            // Focus indicator (thin gold outline when keyboard-focused)
            if (this.isFocused() && !selected) {
                context.fill(x, y, x + w, y + 1, GOLD);
                context.fill(x, y + h - 1, x + w, y + h, GOLD);
                context.fill(x, y, x + 1, y + h, GOLD);
                context.fill(x + w - 1, y, x + w, y + h, GOLD);
            }

            int textColor = selected ? liftBrightness(GOLD, ZONE_SELECTED_NAME_LIFT) : MUTED;
            int textX = x + 6;

            if (this.band == null && selected) {
                drawRainbowItalicUiText(context, "Random", textX, y + 2, true);
            } else if (selected) {
                // Selected concrete band: its name letters gently bounce so the pick reads as "alive".
                drawBouncingUiText(context, this.band.displayName(), textX, y + 2, textColor, true);
            } else {
                drawUiText(context, this.band == null ? "Random" : this.band.displayName(), textX, y + 2, textColor, selected);
            }

            String range = this.band == null
                    ? formatDegree(0.0) + "\u2013" + formatDegree(90.0)
                    : formatDegree(this.band.lowDeg()) + "\u2013" + formatDegree(this.band.highDeg());
            int rangeW = uiTextWidth(range);
            int rangeX = x + w - rangeW - 4;
            drawUiText(context, range, rangeX, y + 2, selected ? WARM_WHITE : MUTED, false);

            String helper = this.band == null ? RANDOM_ZONE_HELPER : ZONE_HELPER[this.band.ordinal()];
            int helperColor = selected ? liftBrightness(MUTED, ZONE_SELECTED_SUBTITLE_LIFT) : MUTED;
            int helperWidth = Math.max(40, rangeX - textX - 6);
            int helperY = y + 2 + uiFontHeight() + 2;
            for (net.minecraft.network.chat.FormattedText wrappedLine : wrapUiLines(helper, helperWidth)) {
                if (helperY + uiFontHeight() > y + h - 2) break;
                drawUiText(context, wrappedLine.getString(), textX, helperY, helperColor, false);
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

    // ── Rules-sidebar iconography row (UI round 13 pass B) ──

    private RulesIconRow toggleRow(RulesIconRow.Kind kind, String label, String tooltip,
                                   java.util.function.BooleanSupplier getter, Runnable flip,
                                   java.util.function.BooleanSupplier enabled) {
        return new RulesIconRow(kind, true, label, tooltip, getter, flip, enabled, false);
    }

    private RulesIconRow actionRow(RulesIconRow.Kind kind, String label, String tooltip,
                                   Runnable open, boolean rainbowLabel) {
        return new RulesIconRow(kind, false, label, tooltip, null, open, () -> true, rainbowLabel);
    }

    /**
     * One row of the Rules sidebar rendered as icon + short label + state, replacing the old plain MC
     * on/off buttons (Peetsa's iconography ask). Two flavors:
     * <ul>
     *   <li><b>Toggle</b> (Commands / Compass / Structures / Bonus Chest): reads and flips a backing
     *       boolean. ON = illuminated icon (full color + a gentle warm bloom) and the word "On"; OFF =
     *       a dim, desaturated icon and "Off". State is carried by icon shape/brightness AND text, so it
     *       survives color-blindness (WCAG 1.4.1).</li>
     *   <li><b>Action</b> (HUD Studio / Game Rules): runs an open-a-screen callback; no On/Off. HUD
     *       Studio keeps its flowing-rainbow wordmark; a ">" chevron hints "opens a screen".</li>
     * </ul>
     * Draws inside the Rules-panel scissor (clips into partial rows at the scroll edges); shows a
     * plain-language tooltip on hover; plays the vanilla UI click on select (mouse AND keyboard, so the
     * feedback matches -- the same consistency the H5 zone-row fix restores).
     */
    private final class RulesIconRow extends AbstractWidget {
        enum Kind { COMMANDS, COMPASS, STRUCTURES, BONUS_CHEST, HUD_STUDIO, GAME_RULES }

        private static final int ICON_SIZE = 16;
        private static final long FLASH_MS = 240L; // brief brighten right after a toggle (feedback)

        private final Kind kind;
        private final boolean isToggle;
        private final java.util.function.BooleanSupplier getter;   // toggle state (null for actions)
        private final Runnable onActivate;                          // flip a boolean, or open a screen
        private final java.util.function.BooleanSupplier enabled;   // interactable + lit-eligible
        private final String label;
        private final Component tooltip;
        private final boolean rainbowLabel;
        private long flashUntilMs = 0L;

        private RulesIconRow(Kind kind, boolean isToggle, String label, String tooltip,
                             java.util.function.BooleanSupplier getter, Runnable onActivate,
                             java.util.function.BooleanSupplier enabled, boolean rainbowLabel) {
            super(0, 0, 10, 10, Component.literal(label));
            this.kind = kind;
            this.isToggle = isToggle;
            this.label = label;
            this.tooltip = Component.literal(tooltip);
            this.getter = getter;
            this.onActivate = onActivate;
            this.enabled = enabled;
            this.rainbowLabel = rainbowLabel;
        }

        private boolean isEnabled() {
            return this.enabled == null || this.enabled.getAsBoolean();
        }

        private boolean isOn() {
            return this.isToggle && this.getter != null && this.getter.getAsBoolean();
        }

        private void activate() {
            if (!isEnabled()) return;
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            if (this.isToggle) this.flashUntilMs = System.currentTimeMillis() + FLASH_MS;
            if (this.onActivate != null) this.onActivate.run();
        }

        @Override
        public void onClick(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
            activate();
        }

        @Override
        public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
            if (!this.isActive() || !isEnabled()) return false;
            if (input.isSelection()) {
                activate();
                return true;
            }
            return false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();

            // Clip to the Rules-panel viewport so a row scrolling past an edge renders as a partial "half
            // row" instead of popping. Rows extract with no outer scissor active (super.extractRenderState),
            // so this enable/disable pair is balanced.
            int clipLeft = Math.max(railX + 1, paneStripViewportLeft);
            int clipRight = Math.min(railX + railW - 1, paneStripViewportRight);
            // Match the rail's outer scissor (extractRenderState): clip from just below the heading band, not
            // from settingsViewportTop, so a row scrolling up isn't swallowed by the ~18px invisible shelf under
            // the "Rules" title. Both the outer pass and this per-row pass must agree, since nested scissors
            // intersect -- if only one used the tighter top the row would still clip at the stale +36 line.
            // settingsClipTop() is that shared line; the hover hit-test + tooltip gate below reuse it too.
            int rowClipTop = settingsClipTop();
            boolean clipped = clipRight > clipLeft && settingsViewportBottom > rowClipTop;
            if (clipped) {
                context.enableScissor(clipLeft, rowClipTop, clipRight, settingsViewportBottom);
            }

            boolean enabled = isEnabled();
            boolean on = isOn();
            boolean lit = enabled && (this.isToggle ? on : true);
            long now = System.currentTimeMillis();
            // Hover hit-test clamped to the row's VISIBLE span: top at rowClipTop (the same clip line the row is
            // drawn to), so the half-clipped part under the heading is never hoverable-but-invisible.
            boolean hovered = enabled && mouseX >= x && mouseX < x + w
                    && mouseY >= Math.max(y, rowClipTop) && mouseY < Math.min(y + h, settingsViewportBottom);

            // Hover / focus backing highlight
            if (hovered || this.isFocused()) {
                context.fill(x, y, x + w, y + h, hovered ? 0x22D4A74A : 0x18D4A74A);
            }
            if (this.isFocused()) {
                context.fill(x, y, x + w, y + 1, GOLD);
                context.fill(x, y + h - 1, x + w, y + h, GOLD);
                context.fill(x, y, x + 1, y + h, GOLD);
                context.fill(x + w - 1, y, x + w, y + h, GOLD);
            }

            // Icon (left, vertically centered). Warm bloom under a lit icon, gently pulsing; a fresh toggle
            // flashes brighter for a moment. All motion is wall-clock driven and subtle.
            int iconX = x + 3;
            int iconY = y + (h - ICON_SIZE) / 2;
            int iconCx = iconX + ICON_SIZE / 2;
            int iconCy = iconY + ICON_SIZE / 2;
            // Reduce Motion (Pass C config) freezes the bloom pulse, the toggle flash, and the chest glitter
            // to a steady state; the illuminated look survives, just without animation.
            boolean reduceMotion = com.example.globe.client.LatitudeConfig.reduceMotion;
            // HIGH_CONTRAST: a dark backing plate + bright hairline behind the icon box so glyphs never wash
            // out against the panel, and a brighter illuminated (lit) state.
            int plateAlpha = AccessibilityPalette.outlineStrength(a11yMode());
            if (plateAlpha > 0) {
                int px0 = iconX - 1, py0 = iconY - 1, px1 = iconX + ICON_SIZE + 1, py1 = iconY + ICON_SIZE + 1;
                context.fill(px0, py0, px1, py1, (plateAlpha << 24));
                int rim = 0x66FFFFFF;
                context.fill(px0, py0, px1, py0 + 1, rim);
                context.fill(px0, py1 - 1, px1, py1, rim);
                context.fill(px0, py0, px0 + 1, py1, rim);
                context.fill(px1 - 1, py0, px1, py1, rim);
            }
            if (lit) {
                float base = plateAlpha > 0 ? 0.95f : 0.72f;
                float pulse = reduceMotion ? (plateAlpha > 0 ? 0.95f : 0.85f) : base + 0.28f * (float) Math.sin(now / 620.0);
                float flash = (!reduceMotion && now < this.flashUntilMs) ? 1.0f : 0.0f;
                RulesIcons.glow(context, iconCx, iconCy, ICON_SIZE * 3 / 4, Math.min(1.0f, pulse + flash * 0.6f));
            }
            switch (this.kind) {
                case COMMANDS -> RulesIcons.commands(context, iconX, iconY, ICON_SIZE, lit);
                case COMPASS -> RulesIcons.compass(context, iconX, iconY, ICON_SIZE, lit);
                case STRUCTURES -> RulesIcons.structures(context, iconX, iconY, ICON_SIZE, lit);
                case BONUS_CHEST -> RulesIcons.chest(context, iconX, iconY, ICON_SIZE, lit, now, !reduceMotion);
                case HUD_STUDIO -> RulesIcons.hudStudio(context, iconX, iconY, ICON_SIZE);
                case GAME_RULES -> RulesIcons.scroll(context, iconX, iconY, ICON_SIZE);
            }

            // Label (short) beside the icon, vertically centered.
            int labelX = iconX + ICON_SIZE + 6;
            int textY = y + (h - uiFontHeight()) / 2;
            int stateW = this.isToggle ? uiTextWidth("Off") + 6 : uiTextWidth(">") + 6;
            int labelRight = x + w - stateW - 4;
            int labelMaxW = Math.max(12, labelRight - labelX);
            if (this.rainbowLabel && enabled) {
                drawFlowingLabel(context, this.label, labelX, textY);
            } else {
                int labelColor = !enabled ? DISABLED_COLOR : (this.isToggle ? (on ? WARM_WHITE : MUTED) : WARM_WHITE);
                drawUiText(context, ellipsizeToWidth(this.label, labelMaxW), labelX, textY, labelColor, false);
            }

            // State / affordance on the right.
            if (this.isToggle) {
                String state = on ? "On" : "Off";
                int stateColor = !enabled ? DISABLED_COLOR : (on ? GOLD : MUTED);
                int sx = x + w - uiTextWidth(state) - 4;
                drawUiText(context, state, sx, textY, stateColor, false);
            } else {
                int cx = x + w - uiTextWidth(">") - 4;
                drawUiText(context, ">", cx, textY, enabled ? GOLD : DISABLED_COLOR, false);
            }

            if (clipped) {
                context.disableScissor();
            }

            // Tooltip: anchored to the SIDE of the row (not the cursor) so it never covers the hovered row or the
            // adjacent rail content. The rail sits on the right of the screen, so prefer opening LEFT into the
            // empty middle; fall back to the right, then clamp on-screen. Deferred -> drawn on top, after every
            // scissor is disabled. Only when this row's viewport actually shows it.
            if (hovered && y + h > rowClipTop && y < settingsViewportBottom) {
                int wrapW = Math.max(120, Math.min(220, LatitudeCreateWorldScreen.this.width - 24));
                java.util.List<net.minecraft.util.FormattedCharSequence> lines = font.split(this.tooltip, wrapW);
                final int anchorX = x;
                final int anchorW = w;
                final int anchorY = y;
                net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner sidePositioner =
                        (sw, sh, mx, my, tw, th) -> {
                            int gap = 6;
                            int px;
                            if (anchorX - gap - tw >= 0) {
                                px = anchorX - gap - tw;                 // room on the left: open into the middle
                            } else if (anchorX + anchorW + gap + tw <= sw) {
                                px = anchorX + anchorW + gap;            // else open to the right of the row
                            } else {
                                px = sw - tw;                            // no room either side: clamp to right edge
                            }
                            px = Math.max(0, Math.min(px, sw - tw));
                            int py = Math.max(0, Math.min(anchorY, sh - th));   // vertically aligned with the row
                            return new org.joml.Vector2i(px, py);
                        };
                context.setTooltipForNextFrame(font, lines, sidePositioner, mouseX, mouseY, true);
            }
            this.handleCursor(context);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }

    /** Left-aligned flowing-rainbow wordmark (HUD Studio row), matching the compass/HUD-Studio rainbow. */
    private void drawFlowingLabel(GuiGraphicsExtractor context, String text, int x, int y) {
        int cx = x;
        int visibleIdx = 0;
        int visibleCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') visibleCount++;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int color = 0xFF000000 | RainbowText.flowingColor(
                    visibleIdx, visibleCount, com.example.globe.core.ui.FlowingGradient.DEFAULT_CYCLE_SECONDS);
            drawUiText(context, String.valueOf(c), cx, y, color, true);
            if (c != ' ') visibleIdx++;
            cx += this.font.width(String.valueOf(c));
        }
    }
}
