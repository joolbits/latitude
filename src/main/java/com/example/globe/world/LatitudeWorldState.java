package com.example.globe.world;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.Optional;

public final class LatitudeWorldState extends PersistentState {
    public enum WorldgenPolicyVersion {
        LEGACY_1_2_X,
        MODERN_1_3
    }

    private static final Codec<WorldgenPolicyVersion> WORLDGEN_POLICY_CODEC = Codec.STRING.xmap(
            WorldgenPolicyVersion::valueOf,
            Enum::name
    );

    private static final PersistentStateType<LatitudeWorldState> STATE_TYPE = new PersistentStateType<>(
            "globe_latitude_world_state",
            LatitudeWorldState::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("spawn_picker_dismissed", false)
                            .forGetter(LatitudeWorldState::isSpawnPickerDismissed),
                    WORLDGEN_POLICY_CODEC.optionalFieldOf("worldgen_policy")
                            .forGetter(state -> Optional.ofNullable(state.worldgenPolicy))
            ).apply(instance, (spawnPickerDismissed, worldgenPolicy) ->
                    new LatitudeWorldState(spawnPickerDismissed, normalizeWorldgenPolicy(worldgenPolicy)))),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private boolean spawnPickerDismissed;
    private WorldgenPolicyVersion worldgenPolicy;

    public LatitudeWorldState() {
        this(false, Optional.empty());
    }

    private LatitudeWorldState(boolean spawnPickerDismissed, Optional<WorldgenPolicyVersion> worldgenPolicy) {
        this.spawnPickerDismissed = spawnPickerDismissed;
        this.worldgenPolicy = normalizeWorldgenPolicy(worldgenPolicy).orElse(null);
    }

    private static Optional<WorldgenPolicyVersion> normalizeWorldgenPolicy(Optional<WorldgenPolicyVersion> worldgenPolicy) {
        return worldgenPolicy == null ? Optional.empty() : worldgenPolicy;
    }

    public static LatitudeWorldState get(ServerWorld world) {
        LatitudeWorldState state = world.getPersistentStateManager().getOrCreate(STATE_TYPE);
        state.ensureWorldgenPolicy(world);
        return state;
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

    public WorldgenPolicyVersion getWorldgenPolicy() {
        return worldgenPolicy != null ? worldgenPolicy : WorldgenPolicyVersion.MODERN_1_3;
    }

    public void setWorldgenPolicy(WorldgenPolicyVersion worldgenPolicy) {
        WorldgenPolicyVersion normalized = worldgenPolicy != null ? worldgenPolicy : WorldgenPolicyVersion.MODERN_1_3;
        if (this.worldgenPolicy != normalized) {
            this.worldgenPolicy = normalized;
            LatitudeBiomes.setWorldgenPolicy(normalized);
            markDirty();
        } else {
            LatitudeBiomes.setWorldgenPolicy(normalized);
        }
    }

    private void ensureWorldgenPolicy(ServerWorld world) {
        if (worldgenPolicy == null) {
            setWorldgenPolicy(inferWorldgenPolicy(world));
            return;
        }
        LatitudeBiomes.setWorldgenPolicy(worldgenPolicy);
    }

    private static WorldgenPolicyVersion inferWorldgenPolicy(ServerWorld world) {
        return world.getTime() < 100L
                ? WorldgenPolicyVersion.MODERN_1_3
                : WorldgenPolicyVersion.LEGACY_1_2_X;
    }
}
