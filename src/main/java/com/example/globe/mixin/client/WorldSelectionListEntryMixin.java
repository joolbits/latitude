package com.example.globe.mixin.client;

import com.example.globe.client.create.RecreatedWorldMetadata;
import com.example.globe.client.create.RecreatedWorldPresetCarrier;
import com.example.globe.util.LatitudeBands;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
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

    @Shadow
    public abstract int getContentX();

    @Shadow
    public abstract int getContentY();

    @Shadow
    public abstract int getContentWidth();

    @Unique
    private String globe$recreatedWorldPresetId;

    // Tri-state: unread (both false/null) vs. read-and-absent (loaded=true, label=null) vs.
    // read-and-present. Computed once per entry on first render, never re-read per frame.
    @Unique
    private boolean globe$lastKnownBandLoaded;
    @Unique
    private String globe$lastKnownBandLabel;

    /**
     * Draws the save's last-known climate zone, read directly from the same on-disk state file
     * the resumed-world loading screen reads, right-aligned on the row's existing info line.
     * Silently absent for non-Latitude saves and for saves that predate this field.
     */
    @Inject(method = "extractContent", at = @At("TAIL"))
    private void globe$drawLastKnownZoneBadge(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                               boolean hovered, float partialTick, CallbackInfo ci) {
        if (!this.globe$lastKnownBandLoaded) {
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
        }
        if (this.globe$lastKnownBandLabel == null) {
            return;
        }
        Font font = this.minecraft.font;
        int textWidth = font.width(this.globe$lastKnownBandLabel);
        int rightEdge = this.getContentX() + this.getContentWidth();
        int y = this.getContentY() + 9 + 9 + 3; // same row as the vanilla info line
        graphics.text(font, this.globe$lastKnownBandLabel, rightEdge - textWidth, y, GLOBE_LAST_ZONE_BADGE_COLOR, false);
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
                GLOBE_LOGGER.warn("[LAT][CWPATH] could not read saved Latitude Re-Create identity", e);
            }
        }

        CreateWorldScreen screen = CreateWorldScreen.createFromExisting(
                client, onClose, levelSettings, context, tempDataPackDir);
        ((RecreatedWorldPresetCarrier) screen)
                .globe$setRecreatedWorldPresetId(this.globe$recreatedWorldPresetId);
        GLOBE_LOGGER.info(
                "[LAT][CWPATH] carried persisted Re-Create preset={} world={}",
                this.globe$recreatedWorldPresetId,
                this.summary.getLevelId());
        return screen;
    }
}
