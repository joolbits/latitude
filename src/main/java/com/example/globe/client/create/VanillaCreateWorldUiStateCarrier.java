package com.example.globe.client.create;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * Exposes vanilla's live world-creation state to ordinary (non-mixin) code.
 *
 * <p>This interface exists rather than a direct cast for a reason that a clean compile will not
 * tell you: a class annotated {@code @Mixin} and registered in {@code globe.mixins.json} cannot be
 * classloaded, so casting to one from an ordinary class throws
 * {@code IllegalClassLoadError: Mixin is defined in globe.mixins.json and cannot be referenced
 * directly} the first time the line runs. The identical cast is legal INSIDE another mixin, which
 * makes the trap easy to walk into by copying a working line. Same pattern, same reason, as
 * {@link RecreatedWorldPresetCarrier}.</p>
 */
public interface VanillaCreateWorldUiStateCarrier {
    WorldCreationUiState globe$getVanillaUiState();
}
