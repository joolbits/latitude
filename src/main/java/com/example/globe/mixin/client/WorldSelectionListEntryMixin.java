package com.example.globe.mixin.client;

import com.example.globe.client.WorldListZoneFitPolicy;
import com.example.globe.client.create.RecreatedWorldMetadata;
import com.example.globe.client.create.RecreatedWorldPresetCarrier;
import com.example.globe.util.LatitudeBands;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
     * Injected at HEAD, before vanilla's own renderContent body renders this widget, so the
     * combined text is correct from the very first frame.
     *
     * <p>That getWidth() diagnosis remains true, but it is no longer the reason right-alignment is
     * off the table: the row's true allotment is reachable via {@link StringWidgetMaxWidthAccessor}.
     * Single-draw-call stands on its own merits, and reading the allotment is what makes the
     * reservation below possible at all — without it the zone is simply clipped away, which is what
     * a live report showed on long rows ("… 10:12 PM) S…" instead of "Subtropical").</p>
     *
     * <p>The tooltip is rebuilt rather than inherited. Vanilla attaches one carrying the full text
     * in the entry's constructor, i.e. before this runs, so the inherited tooltip holds the
     * PRE-SUFFIX text — hovering a clipped row would not reveal the zone either, and the feature
     * would be invisible twice over.</p>
     */
    // GitHub #7 rule: fail soft -- a missed target costs the zone suffix / preset carry on the
    // world list, never a crash. expect=1 keeps dev boots loud under -Dmixin.debug.strict=true.
    @Inject(method = "renderContent", at = @At("HEAD"), require = 0, expect = 1)
    private void globe$appendLastKnownZoneToTimestamp(GuiGraphics graphics, int mouseX, int mouseY,
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
        Component suffix = Component.literal(" " + this.globe$lastKnownBandLabel)
                .withStyle(Style.EMPTY.withColor(GLOBE_LAST_ZONE_BADGE_COLOR));
        Component original = this.idAndLastPlayedText.getMessage();
        Font font = this.minecraft.font;
        int allotted = ((StringWidgetMaxWidthAccessor) (Object) this.idAndLastPlayedText)
                .globe$getMaxWidth();

        Component head = original;
        if (allotted > 0) {
            // Measure candidates exactly as they will draw -- same style, style-aware overload --
            // so the reservation cannot drift from what is rendered.
            Style style = original.getStyle();
            String originalText = original.getString();
            String headText = WorldListZoneFitPolicy.headText(
                    originalText,
                    text -> font.width(Component.literal(text).withStyle(style)),
                    CommonComponents.ELLIPSIS.getString(),
                    font.width(suffix),
                    allotted);
            if (!headText.equals(originalText)) {
                head = Component.literal(headText).withStyle(style);
                // Only a clipped row hides anything, so only a clipped row earns a tooltip; on a
                // row that fits, one would just repeat text already fully visible.
                this.idAndLastPlayedText.setTooltip(Tooltip.create(
                        Component.empty().append(original).append(suffix)));
            }
        }
        this.idAndLastPlayedText.setMessage(Component.empty().append(head).append(suffix));
    }

    // 26.2 named the two call sites "recreateWorld" and its lambda. On this target the second one
    // is a private method that Mojang's mappings do not name, so it remaps to the intermediary
    // `method_20165` -- a selector that would rot on the next version. The intent is simply "carry
    // the preset wherever this entry recreates a world", so match every call site in the class and
    // let the @At target do the selecting. Both sites are bytecode-confirmed present here.
    @Redirect(
            require = 0,
            expect = 1,
            method = "*",
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
                GLOBE_LOGGER.warn("[Latitude] could not read saved Re-Create identity", e);
            }
        }

        CreateWorldScreen screen = CreateWorldScreen.createFromExisting(
                client, onClose, levelSettings, context, tempDataPackDir);
        ((RecreatedWorldPresetCarrier) screen)
                .globe$setRecreatedWorldPresetId(this.globe$recreatedWorldPresetId);
        return screen;
    }
}
