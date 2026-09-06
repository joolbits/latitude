package com.example.globe.mixin.client;

import com.example.globe.client.WorldListZoneFitPolicy;
import com.example.globe.client.create.RecreatedWorldMetadata;
import com.example.globe.client.create.RecreatedWorldPresetCarrier;
import com.example.globe.util.LatitudeBands;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
import org.spongepowered.asm.mixin.injection.Redirect;

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

    @Unique
    private String globe$recreatedWorldPresetId;

    // Tri-state: unread (both false/null) vs. read-and-absent (loaded=true, label=null) vs.
    // read-and-present. Computed once per entry on first render, never re-read per frame.
    @Unique
    private boolean globe$lastKnownBandLoaded;
    @Unique
    private String globe$lastKnownBandLabel;

    // The redirect below runs once per visible row per frame. Everything derived from the zone
    // label is constant for the entry, and the fitted head text only changes when the incoming
    // timestamp text or the row allotment changes, so both are memoized here to keep the per-frame
    // cost at two draw calls, matching the one-shot behaviour of the newer lines.
    @Unique
    private Component globe$zoneSuffix;
    @Unique
    private int globe$zoneSuffixWidth;
    @Unique
    private String globe$headSourceText;
    @Unique
    private int globe$headAllotted = -1;
    @Unique
    private String globe$headText;
    @Unique
    private int globe$headWidth;

    /**
     * Appends the save's last-known climate zone onto the existing "id (date)" line, e.g.
     * "world (8/5/26, 9:11 AM) Temperate", read from the same on-disk state file the resumed-world
     * loading screen reads. Silently absent for non-Latitude saves and for saves that predate this
     * field.
     *
     * <p>1.21.1 draws that line as a raw string inside the entry's own {@code render}, not through
     * a {@code StringWidget} the newer lines expose — there is no widget to shadow, no allotted
     * width to read off one, and no tooltip attached to it. The redirect therefore takes over the
     * second {@code drawString} call: it draws the (possibly ellipsised) timestamp itself and then
     * the gold zone label immediately after it, so there is still exactly one pass over that line
     * and nothing can land on top of anything else.
     *
     * <p>The allotment is computed from the row rectangle the enclosing {@code render} was handed,
     * which is the same quantity the widget's max width reported on the newer lines. The tooltip a
     * clipped row gets there has no equivalent here: 1.21.1 attaches none to this text at all, so a
     * clipped row simply shows the ellipsis.
     */
    // GitHub #7 rule: fail soft -- a missed target costs the zone suffix on the world list, never a
    // crash. expect=1 keeps dev boots loud under -Dmixin.debug.strict=true.
    @Redirect(
            require = 0,
            expect = 1,
            method = "render",
            at = @At(
                    value = "INVOKE",
                    ordinal = 1,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private int globe$appendLastKnownZoneToTimestamp(GuiGraphics graphics, Font font, String text,
                                                     int x, int y, int color, boolean dropShadow,
                                                     GuiGraphics outerGraphics, int index, int top,
                                                     int left, int width, int height, int mouseX,
                                                     int mouseY, boolean hovering, float partialTick) {
        String zone = globe$lastKnownZoneLabel();
        if (zone == null) {
            return graphics.drawString(font, text, x, y, color, dropShadow);
        }

        if (this.globe$zoneSuffix == null) {
            this.globe$zoneSuffix = Component.literal(" " + zone)
                    .withStyle(Style.EMPTY.withColor(GLOBE_LAST_ZONE_BADGE_COLOR));
            this.globe$zoneSuffixWidth = font.width(this.globe$zoneSuffix);
        }
        int allotted = Math.max(0, (left + width) - x - 4);
        if (this.globe$headText == null
                || allotted != this.globe$headAllotted
                || !text.equals(this.globe$headSourceText)) {
            this.globe$headSourceText = text;
            this.globe$headAllotted = allotted;
            this.globe$headText = WorldListZoneFitPolicy.headText(
                    text,
                    font::width,
                    CommonComponents.ELLIPSIS.getString(),
                    this.globe$zoneSuffixWidth,
                    allotted);
            this.globe$headWidth = font.width(this.globe$headText);
        }

        int result = graphics.drawString(font, this.globe$headText, x, y, color, dropShadow);
        graphics.drawString(font, this.globe$zoneSuffix, x + this.globe$headWidth, y,
                GLOBE_LAST_ZONE_BADGE_COLOR, dropShadow);
        return result;
    }

    /** Reads the save's last-known band once per entry, then serves the cached answer. */
    @Unique
    private String globe$lastKnownZoneLabel() {
        if (this.globe$lastKnownBandLoaded) {
            return this.globe$lastKnownBandLabel;
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
        return this.globe$lastKnownBandLabel;
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
                    target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;createFromExisting(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/world/level/LevelSettings;Lnet/minecraft/client/gui/screens/worldselection/WorldCreationContext;Ljava/nio/file/Path;)Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;"))
    private CreateWorldScreen globe$carryPersistedLatitudePreset(
            Minecraft client,
            Screen lastScreen,
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
                client, lastScreen, levelSettings, context, tempDataPackDir);
        ((RecreatedWorldPresetCarrier) screen)
                .globe$setRecreatedWorldPresetId(this.globe$recreatedWorldPresetId);
        return screen;
    }
}
