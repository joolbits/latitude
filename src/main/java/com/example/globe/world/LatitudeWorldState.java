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
                            .forGetter(LatitudeWorldState::isSpawnPickerDismissed)
            ).apply(instance, LatitudeWorldState::new)),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private boolean spawnPickerDismissed;

    public LatitudeWorldState() {
        this(false);
    }

    private LatitudeWorldState(boolean spawnPickerDismissed) {
        this.spawnPickerDismissed = spawnPickerDismissed;
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
}
