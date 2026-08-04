package com.example.globe.client.create;

import org.jetbrains.annotations.Nullable;

/** Carries source-world identity across vanilla's Re-Create screen construction. */
public interface RecreatedWorldPresetCarrier {
    void globe$setRecreatedWorldPresetId(@Nullable String presetId);

    @Nullable
    String globe$getRecreatedWorldPresetId();
}
