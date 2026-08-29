package com.example.globe.client.create;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * Exposes the vanilla create-world screen's UI state to ordinary code.
 *
 * <p>{@code CreateWorldScreenMixin} already shadows {@code getUiState()}, but ordinary (non-mixin)
 * code cannot cast a real {@code CreateWorldScreen} instance to a {@code @Mixin}-annotated class at
 * runtime -- Mixin's transformer rejects it with {@code IllegalClassLoadError}, even though the same
 * cast compiles cleanly, because the mixin class itself is never meant to be loaded as a real class.
 * A plain interface the mixin implements, cast to from outside, is the correct and only working form
 * -- the same pattern {@link RecreatedWorldPresetCarrier} already establishes in this codebase.</p>
 *
 * <p>Caught live rather than assumed: the escape hatch's return-leg state sync crashed the dev client
 * with exactly that error the first time it ran, despite compiling without complaint.</p>
 */
public interface VanillaCreateWorldUiStateCarrier {
    WorldCreationUiState globe$getUiState();
}
