package com.example.globe.mixin.client;

import com.example.globe.client.GlobeWorldSize;
import com.example.globe.client.GlobeWorldSizeSelection;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {

    @Shadow
    public abstract WorldCreator getWorldCreator();

    @Inject(method = "createLevel", at = @At("HEAD"))
    private void globe$applySelectedSizeToWorldPreset(CallbackInfo ci) {
        WorldCreator creator = this.getWorldCreator();
        if (creator == null) {
            return;
        }

        WorldCreator.WorldType type = creator.getWorldType();
        if (type == null || type.preset() == null) {
            return;
        }

        Identifier presetId = type.preset()
                .getKey()
                .map(RegistryKey::getValue)
                .orElse(null);

        if (presetId == null || !presetId.getNamespace().equals("globe")) {
            return;
        }

        GlobeWorldSize size = GlobeWorldSizeSelection.get();

        Registry<WorldPreset> presets = creator.getGeneratorOptionsHolder()
                .getCombinedRegistryManager()
                .get(RegistryKeys.WORLD_PRESET);

        RegistryKey<WorldPreset> key = RegistryKey.of(RegistryKeys.WORLD_PRESET, size.worldPresetId);
        presets.getEntry(key).ifPresent(entry -> creator.setWorldType(new WorldCreator.WorldType(entry)));
    }
}
