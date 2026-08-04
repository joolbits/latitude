package com.example.globe.client.create;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;

/** Reads Latitude's persisted world identity before vanilla normalizes Re-Create to Normal. */
public final class RecreatedWorldMetadata {
    private static final Path LATITUDE_STATE = Path.of(
            "dimensions", "minecraft", "overworld", "data", "globe", "latitude_world_state.dat");

    private RecreatedWorldMetadata() {
    }

    @Nullable
    public static String latitudePresetId(Path worldRoot) throws IOException {
        if (worldRoot == null) {
            return null;
        }
        Path statePath = worldRoot.resolve(LATITUDE_STATE);
        if (!Files.isRegularFile(statePath)) {
            return null;
        }
        CompoundTag root = NbtIo.readCompressed(statePath, NbtAccounter.unlimitedHeap());
        int radius = root.getCompoundOrEmpty("data").getIntOr("globe_radius", 0);
        return RecreatedWorldTypePolicy.presetIdForRadius(radius);
    }
}
