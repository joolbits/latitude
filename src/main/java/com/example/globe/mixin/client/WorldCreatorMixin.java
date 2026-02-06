package com.example.globe.mixin.client;

import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.WorldPreset;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(WorldCreator.class)
public abstract class WorldCreatorMixin {
    private static final Identifier GLOBE_WORLD_PRESET_ID = new Identifier("globe", "globe");

    @Shadow
    public abstract GeneratorOptionsHolder getGeneratorOptionsHolder();

    @Shadow
    @Final
    private List<WorldCreator.WorldType> normalWorldTypes;

    @Shadow
    @Final
    private List<WorldCreator.WorldType> extendedWorldTypes;

    @Inject(method = "updateWorldTypeLists", at = @At("TAIL"))
    private void globe$ensureGlobePresetIsListed(CallbackInfo ci) {
        Registry<WorldPreset> presets = this.getGeneratorOptionsHolder()
                .getCombinedRegistryManager()
                .get(RegistryKeys.WORLD_PRESET);

        RegistryKey<WorldPreset> key = RegistryKey.of(RegistryKeys.WORLD_PRESET, GLOBE_WORLD_PRESET_ID);
        presets.getEntry(key).ifPresent(entry -> {
            WorldCreator.WorldType globeType = new WorldCreator.WorldType(entry);

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
