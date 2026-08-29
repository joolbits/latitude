package com.example.globe.mixin.client;

import com.example.globe.client.WorldListZoneFitPolicy;
import com.example.globe.client.create.RecreatedWorldMetadata;
import com.example.globe.client.create.RecreatedWorldPresetCarrier;
import com.example.globe.util.LatitudeBands;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.WorldSelectionList$WorldListEntry")
public abstract class WorldSelectionListEntryMixin {
    @Unique
    private static final Logger GLOBE_LOGGER = LoggerFactory.getLogger("globe");
    @Unique
    private static final int GLOBE_LAST_ZONE_BADGE_COLOR = 0xFFD4A74A;
    // [LAT][CWPATH] fires on every ordinary create-screen open; opt-in only (maintainer ruling, 2026-08-18).
    @Unique
    private static final boolean DEBUG_CWPATH = Boolean.getBoolean("latitude.debugCwPath");

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private LevelSummary summary;

    // Declared directly on the target class, so it shadows safely without a refmap (unlike the
    // row's geometry accessors, which are only INHERITED here and failed to apply as a shadow).
    @Shadow
    @Final
    private StringWidget idAndLastPlayedText;

    @Unique
    private String globe$recreatedWorldPresetId;

    // Tri-state: unread (both false/null) vs. read-and-absent (loaded=true, label=null) vs.
    // read-and-present. Computed once per entry on first render, never re-read per frame.
    @Unique
    private boolean globe$lastKnownBandLoaded;
    @Unique
    private String globe$lastKnownBandLabel;

    /**
     * Appends the save's last-known climate zone onto the existing "(id (date))" line, e.g.
     * "world (8/5/26, 9:11 AM) Temperate", read from the same on-disk state file the
     * resumed-world loading screen reads. Silently absent for non-Latitude saves and for saves
     * that predate this field.
     *
     * <p>This edits the widget's own text rather than drawing a separate overlay, deliberately:
     * a prior attempt to right-align a second draw call over this row twice landed on top of
     * vanilla's own text (StringWidget.getWidth() reports the SHORT actual-text width, not the
     * row's allotted width, so "the far edge" kept resolving to a point right next to the visible
     * text instead of the row's true right margin). Editing the text directly sidesteps that
     * class of bug entirely: there is only ever one draw call, so nothing can land on top of it.
     * Injected at HEAD, before vanilla's own extractContent body renders this widget, so the
     * combined text is correct from the very first frame.
     *
     * <p>That getWidth() diagnosis remains true, but it is no longer the reason right-alignment is
     * off the table: the row's true allotment is reachable via {@link StringWidgetMaxWidthAccessor}.
     * Single-draw-call is now the reason on its own merits, and it is what makes the reserve below
     * possible at all.</p>
     *
     * <p><b>The zone is reserved, not appended and hoped for.</b> Vanilla caps this widget's width
     * and clips overflow with a trailing ellipsis, so a suffix added at the END was the first thing
     * cut -- longer rows rendered "... 10:12 PM) S" with the zone shorn away, and hovering did not
     * help because vanilla builds the row's tooltip in its constructor, long before this runs, so it
     * still held the pre-suffix text. The zone was invisible twice over. Now the suffix's width is
     * reserved first and the HEAD is clipped instead, sacrificing the level id -- which duplicates
     * the world name shown directly above it -- and the tooltip is rebuilt from the full untruncated
     * text (maintainer report, 2026-08-26).</p>
     */
    @Inject(method = "extractContent", at = @At("HEAD"))
    private void globe$appendLastKnownZoneToTimestamp(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                        boolean hovered, float partialTick, CallbackInfo ci) {
        if (this.globe$lastKnownBandLoaded) {
            return;
        }
        this.globe$lastKnownBandLoaded = true;
        try {
            java.nio.file.Path worldRoot = this.minecraft.getLevelSource()
                    .getBaseDir()
                    .resolve(this.summary.getLevelId());
            String bandId = RecreatedWorldMetadata.lastKnownBandId(worldRoot);
            LatitudeBands.Band band = LatitudeBands.fromCanonicalId(bandId);
            this.globe$lastKnownBandLabel = band != null ? band.displayName() : null;
        } catch (IOException e) {
            GLOBE_LOGGER.warn("[Latitude] could not read last-known band for world " + this.summary.getLevelId(), e);
            this.globe$lastKnownBandLabel = null;
        }
        if (this.globe$lastKnownBandLabel == null) {
            return;
        }
        Component original = this.idAndLastPlayedText.getMessage();
        Component suffix = Component.literal(" " + this.globe$lastKnownBandLabel)
                .withStyle(Style.EMPTY.withColor(GLOBE_LAST_ZONE_BADGE_COLOR));

        Font font = this.minecraft.font;
        int allotted = ((StringWidgetMaxWidthAccessor) (Object) this.idAndLastPlayedText).globe$getMaxWidth();
        int suffixWidth = font.width(suffix);

        Component head = original;
        if (!WorldListZoneFitPolicy.fitsWithoutClipping(font.width(original), suffixWidth, allotted)) {
            // Vanilla's own marker, taken from vanilla rather than reproduced, so a Latitude-clipped
            // row cannot drift from one of its own.
            int budget = WorldListZoneFitPolicy.headBudget(
                    suffixWidth, font.width(CommonComponents.ELLIPSIS), allotted);
            // Clip only on the overflow path. Clipping unconditionally would append an ellipsis to
            // every short row too, visibly damaging the rows this was meant to leave untouched.
            head = Component.literal(font.plainSubstrByWidth(original.getString(), budget))
                    .withStyle(original.getStyle())
                    .append(CommonComponents.ELLIPSIS);
        }
        this.idAndLastPlayedText.setMessage(Component.empty().append(head).append(suffix));

        // Rebuilt, not inherited: vanilla's constructor-time tooltip predates both the suffix and
        // any clipping, so leaving it would leave a clipped row with no way to read either.
        this.idAndLastPlayedText.setTooltip(
                Tooltip.create(Component.empty().append(original).append(suffix)));
    }

    @Redirect(
            method = {"recreateWorld", "lambda$recreateWorld$0"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;createFromExisting(Lnet/minecraft/client/Minecraft;Ljava/lang/Runnable;Lnet/minecraft/world/level/LevelSettings;Lnet/minecraft/client/gui/screens/worldselection/WorldCreationContext;Ljava/nio/file/Path;)Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;"))
    private CreateWorldScreen globe$carryPersistedLatitudePreset(
            Minecraft client,
            Runnable onClose,
            LevelSettings levelSettings,
            WorldCreationContext context,
            java.nio.file.Path tempDataPackDir) {
        if (this.globe$recreatedWorldPresetId == null) {
            java.nio.file.Path worldRoot = this.minecraft.getLevelSource()
                    .getBaseDir()
                    .resolve(this.summary.getLevelId());
            try {
                this.globe$recreatedWorldPresetId = RecreatedWorldMetadata.latitudePresetId(worldRoot);
            } catch (IOException e) {
                if (DEBUG_CWPATH) {
                    GLOBE_LOGGER.warn("[LAT][CWPATH] could not read saved Latitude Re-Create identity", e);
                }
            }
        }

        CreateWorldScreen screen = CreateWorldScreen.createFromExisting(
                client, onClose, levelSettings, context, tempDataPackDir);
        ((RecreatedWorldPresetCarrier) screen)
                .globe$setRecreatedWorldPresetId(this.globe$recreatedWorldPresetId);
        if (DEBUG_CWPATH) {
            GLOBE_LOGGER.info(
                    "[LAT][CWPATH] carried persisted Re-Create preset={} world={}",
                    this.globe$recreatedWorldPresetId,
                    this.summary.getLevelId());
        }
        return screen;
    }
}
