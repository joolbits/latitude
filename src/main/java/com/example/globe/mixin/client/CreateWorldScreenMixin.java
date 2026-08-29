package com.example.globe.mixin.client;

import com.example.globe.client.create.RecreatedWorldPresetCarrier;
import com.example.globe.client.create.VanillaCreateWorldUiStateCarrier;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin
        implements RecreatedWorldPresetCarrier, VanillaCreateWorldUiStateCarrier {
    @Unique
    private String globe$recreatedWorldPresetId;

    @Shadow
    public abstract WorldCreationUiState getUiState();

    @Accessor("recreated")
    public abstract boolean globe$isRecreated();

    @Override
    public void globe$setRecreatedWorldPresetId(String presetId) {
        this.globe$recreatedWorldPresetId = presetId;
    }

    @Override
    public String globe$getRecreatedWorldPresetId() {
        return this.globe$recreatedWorldPresetId;
    }

    /**
     * Reaches vanilla's live creation state from ordinary code. The shadowed getUiState() above is
     * reachable from inside a mixin, but a cast to THIS class from a non-mixin file is a runtime
     * IllegalClassLoadError, so the escape hatch goes through the interface instead.
     */
    @Override
    public WorldCreationUiState globe$getVanillaUiState() {
        return this.getUiState();
    }

}
