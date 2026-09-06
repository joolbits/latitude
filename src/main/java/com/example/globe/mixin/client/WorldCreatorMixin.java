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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

@Mixin(WorldCreationUiState.class)
public abstract class WorldCreatorMixin implements VanillaOnlyWorldCreationState {
    private static final ResourceLocation GLOBE_WORLD_PRESET_ID = ResourceLocation.fromNamespaceAndPath("globe", "globe");

    @Shadow
    private WorldCreationContext settings;

    @Shadow
    public abstract java.util.List<WorldCreationUiState.WorldTypeEntry> getNormalPresetList();

    @Shadow
    public abstract java.util.List<WorldCreationUiState.WorldTypeEntry> getAltPresetList();

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
            // The lists were already built for a normal session by the time the handoff is
            // claimed, so flipping the flag has to re-apply the policy rather than wait for the
            // next updatePresetLists — otherwise Globe stays listed for this screen's lifetime.
            globe$applyPresetPolicy();
        }
    }

    @Inject(method = "updatePresetLists", at = @At("TAIL"))
    private void globe$ensureGlobePresetIsListed(CallbackInfo ci) {
        globe$applyPresetPolicy();
    }

    @Unique
    private void globe$applyPresetPolicy() {
        Registry<WorldPreset> presets = this.settings
                .worldgenLoadContext()
                .registryOrThrow(Registries.WORLD_PRESET);

        ResourceKey<WorldPreset> key = ResourceKey.create(Registries.WORLD_PRESET, GLOBE_WORLD_PRESET_ID);
        presets.getHolder(key).ifPresent(entry -> {
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
