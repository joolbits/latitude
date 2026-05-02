package com.example.globe.world;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

public final class LatitudeWorldState extends PersistentState {
    private static final String SAVE_ID = "globe_latitude_world_state";
    private static final String SPAWN_PICKER_DISMISSED_KEY = "spawn_picker_dismissed";

    private boolean spawnPickerDismissed;

    public LatitudeWorldState() {
        this(false);
    }

    private LatitudeWorldState(boolean spawnPickerDismissed) {
        this.spawnPickerDismissed = spawnPickerDismissed;
    }

    public static LatitudeWorldState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(LatitudeWorldState::fromNbt, LatitudeWorldState::new, SAVE_ID);
    }

    private static LatitudeWorldState fromNbt(NbtCompound nbt) {
        return new LatitudeWorldState(nbt.getBoolean(SPAWN_PICKER_DISMISSED_KEY));
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
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
