package com.example.globe.world;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

public final class LatitudeWorldState extends PersistentState {
    private static final String SAVE_ID = "globe_latitude_world_state";
    private static final String SPAWN_PICKER_DISMISSED_KEY = "spawn_picker_dismissed";
    private static final Type<LatitudeWorldState> STATE_TYPE = new Type<>(
            LatitudeWorldState::new,
            LatitudeWorldState::fromNbt,
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
        return world.getPersistentStateManager().getOrCreate(STATE_TYPE, SAVE_ID);
    }

    private static LatitudeWorldState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        return new LatitudeWorldState(nbt.getBoolean(SPAWN_PICKER_DISMISSED_KEY));
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putBoolean(SPAWN_PICKER_DISMISSED_KEY, spawnPickerDismissed);
        return nbt;
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
