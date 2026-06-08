package com.example.globe.world;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public final class LatitudeWorldState extends PersistentState {
    private static final PersistentStateType<LatitudeWorldState> STATE_TYPE = new PersistentStateType<>(
            "globe_latitude_world_state",
            LatitudeWorldState::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("spawn_picker_dismissed", false)
                            .forGetter(LatitudeWorldState::isSpawnPickerDismissed),
                    Codec.INT.optionalFieldOf("globe_radius", 0)
                            .forGetter(LatitudeWorldState::getGlobeRadius)
            ).apply(instance, LatitudeWorldState::new)),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private boolean spawnPickerDismissed;
    /** Globe overworld border radius (blocks). >0 marks this world as a Globe world; 0 = not Globe. */
    private int globeRadius;

    public LatitudeWorldState() {
        this(false, 0);
    }

    private LatitudeWorldState(boolean spawnPickerDismissed, int globeRadius) {
        this.spawnPickerDismissed = spawnPickerDismissed;
        this.globeRadius = globeRadius;
    }

    public static LatitudeWorldState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(STATE_TYPE);
    }

    public boolean isSpawnPickerDismissed() {
        return spawnPickerDismissed;
    }

    public void setSpawnPickerDismissed(boolean spawnPickerDismissed) {
        if (this.spawnPickerDismissed != spawnPickerDismissed) {
            this.spawnPickerDismissed = spawnPickerDismissed;
            markDirty();
        }
    }

    public int getGlobeRadius() {
        return globeRadius;
    }

    public void setGlobeRadius(int globeRadius) {
        if (this.globeRadius != globeRadius) {
            this.globeRadius = globeRadius;
            markDirty();
        }
    }
}
