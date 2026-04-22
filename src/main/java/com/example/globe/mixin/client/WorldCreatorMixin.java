package com.example.globe.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
public abstract class WorldCreatorMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("globe");
    private static final Identifier GLOBE_WORLD_PRESET_ID = Identifier.fromNamespaceAndPath("globe", "globe");

    @Shadow
    private WorldCreationContext settings;

    @Shadow
    public abstract java.util.List<WorldCreationUiState.WorldTypeEntry> getNormalPresetList();

    @Shadow
    public abstract java.util.List<WorldCreationUiState.WorldTypeEntry> getAltPresetList();

    @Inject(method = "updatePresetLists", at = @At("TAIL"))
    private void globe$ensureGlobePresetIsListed(CallbackInfo ci) {
        LOGGER.info("[LAT][CWPATH] WorldCreatorMixin.updatePresetLists settings={}", this.settings);
        Registry<WorldPreset> presets = this.settings
                .worldgenLoadContext()
                .lookupOrThrow(Registries.WORLD_PRESET);

        ResourceKey<WorldPreset> key = ResourceKey.create(Registries.WORLD_PRESET, GLOBE_WORLD_PRESET_ID);
        presets.get(key).ifPresent(entry -> {
            WorldCreationUiState.WorldTypeEntry globeType = new WorldCreationUiState.WorldTypeEntry((Holder<WorldPreset>) entry);

            var normalWorldTypes = this.getNormalPresetList();
            if (!normalWorldTypes.contains(globeType)) {
                int idx = normalWorldTypes.isEmpty() ? 0 : 1;
                normalWorldTypes.add(idx, globeType);
            }

            var altWorldTypes = this.getAltPresetList();
            if (!altWorldTypes.contains(globeType)) {
                int idx = altWorldTypes.isEmpty() ? 0 : 1;
                altWorldTypes.add(idx, globeType);
            }
        });
    }
}
