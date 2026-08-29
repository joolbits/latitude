package com.example.globe.mixin.client;

import com.example.globe.client.create.VanillaOnlyWorldCreationState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mixin(WorldCreationUiState.class)
public abstract class WorldCreatorMixin implements VanillaOnlyWorldCreationState {
    private static final Logger LOGGER = LoggerFactory.getLogger("globe");
    private static final Identifier GLOBE_WORLD_PRESET_ID = Identifier.fromNamespaceAndPath("globe", "globe");
    // [LAT][CWPATH] fires on every ordinary create-screen open; opt-in only (maintainer ruling, 2026-08-18).
    private static final boolean DEBUG_CWPATH = Boolean.getBoolean("latitude.debugCwPath");

    @Shadow
    private WorldCreationContext settings;

    @Shadow
    public abstract java.util.List<WorldCreationUiState.WorldTypeEntry> getNormalPresetList();

    @Shadow
    public abstract java.util.List<WorldCreationUiState.WorldTypeEntry> getAltPresetList();

    /**
     * Set for a create-world session the player reached through Latitude's own escape hatch. Such
     * a session must behave like plain Minecraft, so the Globe preset is withheld from it rather
     * than injected — otherwise the screen offered as "everything except Latitude" would still
     * list Latitude, and picking it there would bypass Latitude's own creation flow.
     */
    @Unique
    private boolean globe$vanillaOnly;

    @Override
    public boolean globe$isVanillaOnly() {
        return this.globe$vanillaOnly;
    }

    @Override
    public void globe$setVanillaOnly(boolean vanillaOnly) {
        this.globe$vanillaOnly = vanillaOnly;
        if (vanillaOnly) {
            globe$applyPresetPolicy();
        }
    }

    @Inject(method = "updatePresetLists", at = @At("TAIL"))
    private void globe$ensureGlobePresetIsListed(CallbackInfo ci) {
        if (DEBUG_CWPATH) {
            LOGGER.info("[LAT][CWPATH] WorldCreatorMixin.updatePresetLists settings={}", this.settings);
        }
        globe$applyPresetPolicy();
    }

    @Unique
    private void globe$applyPresetPolicy() {
        Registry<WorldPreset> presets = this.settings
                .worldgenLoadContext()
                .lookupOrThrow(Registries.WORLD_PRESET);

        ResourceKey<WorldPreset> key = ResourceKey.create(Registries.WORLD_PRESET, GLOBE_WORLD_PRESET_ID);
        presets.get(key).ifPresent(entry -> {
            WorldCreationUiState.WorldTypeEntry globeType = new WorldCreationUiState.WorldTypeEntry((Holder<WorldPreset>) entry);

            var normalWorldTypes = this.getNormalPresetList();
            var altWorldTypes = this.getAltPresetList();
            if (this.globe$vanillaOnly) {
                VanillaOnlyWorldCreationState.removeFromBothPresetLists(normalWorldTypes, altWorldTypes, globeType);
                return;
            }

            VanillaOnlyWorldCreationState.ensureInBothPresetLists(normalWorldTypes, altWorldTypes, globeType);
        });
    }
}
