package com.example.globe.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

@Mixin(WorldCreationUiState.class)
public abstract class WorldCreatorMixin {
    private static final Identifier GLOBE_WORLD_PRESET_ID = Identifier.fromNamespaceAndPath("globe", "globe");

    @Shadow
    public abstract WorldCreationContext getGeneratorOptionsHolder();

    @Shadow
    @Final
    private List<WorldCreationUiState.WorldTypeEntry> normalWorldTypes;

    @Shadow
    @Final
    private List<WorldCreationUiState.WorldTypeEntry> extendedWorldTypes;

    @Inject(method = "updateWorldTypeLists", at = @At("TAIL"))
    private void globe$ensureGlobePresetIsListed(CallbackInfo ci) {
        Registry<WorldPreset> presets = this.getGeneratorOptionsHolder()
                .worldgenLoadContext()
                .lookupOrThrow(Registries.WORLD_PRESET);

        ResourceKey<WorldPreset> key = ResourceKey.create(Registries.WORLD_PRESET, GLOBE_WORLD_PRESET_ID);
        presets.get(key).ifPresent(entry -> {
            WorldCreationUiState.WorldTypeEntry globeType = new WorldCreationUiState.WorldTypeEntry((Holder<WorldPreset>) entry);

            if (!this.normalWorldTypes.contains(globeType)) {
                int idx = this.normalWorldTypes.isEmpty() ? 0 : 1;
                this.normalWorldTypes.add(idx, globeType);
            }

            if (!this.extendedWorldTypes.contains(globeType)) {
                int idx = this.extendedWorldTypes.isEmpty() ? 0 : 1;
                this.extendedWorldTypes.add(idx, globeType);
            }
        });
    }
}
