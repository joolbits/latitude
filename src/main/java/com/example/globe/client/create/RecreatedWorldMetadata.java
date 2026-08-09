package com.example.globe.client.create;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.example.globe.world.LatitudeWorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;

/**
 * Reads fields out of Latitude's persisted per-world save state directly from disk, before the
 * world is loaded — used both to recover Latitude's identity before vanilla normalizes Re-Create
 * to Normal, and to show save-time information (like the player's last known climate zone) on
 * screens that list saves without starting a server.
 */
public final class RecreatedWorldMetadata {
    /**
     * Derived from {@link LatitudeWorldState#STATE_ID}, never re-spelled — the two drifted apart
     * during the 1.21.11 port and this reader silently found nothing on every world. On 1.21.11
     * the overworld's SavedData lives at {@code <world>/data/<id>.dat}; 26.2 nested it under
     * {@code dimensions/minecraft/overworld/data/} and split the Identifier's namespace into a
     * folder.
     */
    private static final Path LATITUDE_STATE =
            Path.of("data", LatitudeWorldState.STATE_ID + ".dat");

    private RecreatedWorldMetadata() {
    }

    @Nullable
    public static String latitudePresetId(Path worldRoot) throws IOException {
        CompoundTag data = readLatitudeStateData(worldRoot);
        if (data == null) {
            return null;
        }
        int radius = data.getIntOr("globe_radius", 0);
        return RecreatedWorldTypePolicy.presetIdForRadius(radius);
    }

    /** Canonical id (e.g. "temperate") of the band a player was last known to occupy, if recorded. */
    @Nullable
    public static String lastKnownBandId(Path worldRoot) throws IOException {
        CompoundTag data = readLatitudeStateData(worldRoot);
        if (data == null) {
            return null;
        }
        return data.getString("last_known_band").orElse(null);
    }

    @Nullable
    private static CompoundTag readLatitudeStateData(Path worldRoot) throws IOException {
        if (worldRoot == null) {
            return null;
        }
        Path statePath = worldRoot.resolve(LATITUDE_STATE);
        if (!Files.isRegularFile(statePath)) {
            return null;
        }
        CompoundTag root = NbtIo.readCompressed(statePath, NbtAccounter.unlimitedHeap());
        return root.getCompoundOrEmpty("data");
    }
}
