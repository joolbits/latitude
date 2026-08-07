package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import com.example.globe.client.create.RecreatedWorldMetadata;
import com.example.globe.util.LatitudeBands;
import java.nio.file.Path;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Activates the Latitude loading overlay before vanilla shows its own first screen for a resumed
 * world, not after.
 *
 * <p>{@code WorldOpenFlows.openWorld}'s very first action — before any file is even opened — is
 * {@code Minecraft.setScreenAndShow(new GenericMessageScreen("selectWorld.data_read"))}. Only much
 * later, after the save's registries and world stem are fully resolved, does
 * {@code Minecraft.doWorldLoad} run — the point {@link MinecraftClientStartIntegratedMixin} already
 * hooks to activate the overlay, because that is the earliest point a live {@code WorldStem} exists
 * to confirm "is this actually a Latitude world." Between those two points, every resumed world
 * shows several seconds of unbranded vanilla screens before the swap: exactly the "vanilla first,
 * then bespoke" Maintainer reported live on a reload, which the earlier hook could not have prevented no
 * matter how early inside {@code doWorldLoad} it ran, because {@code doWorldLoad} itself starts
 * well after the flash-prone screens are already up.
 *
 * <p>{@link RecreatedWorldMetadata} already solves the "identify a Latitude world before it loads"
 * problem for a different purpose — reading {@code latitude_world_state.dat} straight off disk to
 * recover a save's identity and last-known band before starting a server. This applies the same
 * read at the true first opportunity: {@code openWorld}'s own head, using only the level id and
 * {@code LevelStorageSource.getLevelPath(String)} (a plain path resolve, no I/O) to find the save
 * without needing an opened {@code LevelStorageAccess} at all.
 *
 * <p>Fails soft like every other overlay hook in this lifecycle: a missing target, an unreadable or
 * absent save file, or any other exception here just means the overlay activates at its old, later
 * point instead — never a crash, never a wrongly-shown overlay for a real vanilla world (the
 * doWorldLoad hook's own {@code globe$isLatitudeWorld} check still runs afterward and clears the
 * flag if this guessed wrong).
 */
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsEarlyLatitudeActivationMixin {
    @Unique private static final Logger GLOBE_LOGGER = LoggerFactory.getLogger("LatitudeLoadingOverlay");

    @Shadow
    private LevelStorageSource levelSource;

    @Inject(method = "openWorld", at = @At("HEAD"), require = 0, expect = 1)
    private void globe$activateEarlyForResumedLatitudeWorld(String levelId, Runnable onFail, CallbackInfo ci) {
        try {
            Path worldRoot = levelSource.getLevelPath(levelId);
            String presetId = RecreatedWorldMetadata.latitudePresetId(worldRoot);
            if (presetId == null) {
                return;
            }
            LatitudeClientState.beginExpedition(System.currentTimeMillis());
            LatitudeClientState.activateLatitudeLoading();
            LatitudeBands.Band band =
                    LatitudeBands.fromCanonicalId(RecreatedWorldMetadata.lastKnownBandId(worldRoot));
            if (band != null) {
                LatitudeClientState.setLoadingZoneLabel(band.displayName());
            }
            GLOBE_LOGGER.info(
                    "[Latitude lifecycle] pre-activated loading overlay before vanilla's first "
                            + "world-open screen (levelId={} presetId={})",
                    levelId, presetId);
        } catch (Exception e) {
            GLOBE_LOGGER.warn(
                    "[Latitude lifecycle] could not pre-check saved world for early overlay "
                            + "activation; overlay will activate at its later, doWorldLoad fallback point",
                    e);
        }
    }
}
