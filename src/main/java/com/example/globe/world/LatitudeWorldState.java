package com.example.globe.world;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class LatitudeWorldState extends SavedData {
    public enum WorldgenPolicyVersion {
        LEGACY_1_2_X,
        MODERN_1_3
    }

    private static final Codec<WorldgenPolicyVersion> WORLDGEN_POLICY_CODEC = Codec.STRING.xmap(
            WorldgenPolicyVersion::valueOf,
            Enum::name
    );

    private static final SavedDataType<LatitudeWorldState> STATE_TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("globe", "latitude_world_state"),
            LatitudeWorldState::new,
            RecordCodecBuilder.<LatitudeWorldState>create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("spawn_picker_dismissed", false)
                            .forGetter(LatitudeWorldState::isSpawnPickerDismissed),
                    WORLDGEN_POLICY_CODEC.optionalFieldOf("worldgen_policy")
                            .forGetter((LatitudeWorldState state) -> Optional.ofNullable(state.worldgenPolicy)),
                    Codec.INT.optionalFieldOf("globe_radius", 0)
                            .forGetter(LatitudeWorldState::getGlobeRadius),
                    Codec.STRING.optionalFieldOf("globe_shape", "classic")
                            .forGetter(LatitudeWorldState::getGlobeShape)
            ).apply(instance, (spawnPickerDismissed, worldgenPolicy, globeRadius, globeShape) ->
                    new LatitudeWorldState(spawnPickerDismissed, normalizeWorldgenPolicy(worldgenPolicy), globeRadius, globeShape))),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private boolean spawnPickerDismissed;
    private WorldgenPolicyVersion worldgenPolicy;
    private int globeRadius;
    private String globeShape;

    public LatitudeWorldState() {
        this(false, Optional.empty(), 0, "classic");
    }

    private LatitudeWorldState(boolean spawnPickerDismissed, Optional<WorldgenPolicyVersion> worldgenPolicy, int globeRadius, String globeShape) {
        this.spawnPickerDismissed = spawnPickerDismissed;
        this.worldgenPolicy = normalizeWorldgenPolicy(worldgenPolicy).orElse(null);
        this.globeRadius = Math.max(0, globeRadius);
        this.globeShape = (globeShape == null || globeShape.isBlank()) ? "classic" : globeShape;
    }

    private static Optional<WorldgenPolicyVersion> normalizeWorldgenPolicy(Optional<WorldgenPolicyVersion> worldgenPolicy) {
        return worldgenPolicy == null ? Optional.empty() : worldgenPolicy;
    }

    public static LatitudeWorldState get(ServerLevel world) {
        LatitudeWorldState state = world.getDataStorage().computeIfAbsent(STATE_TYPE);
        state.ensureWorldgenPolicy(world);
        state.ensureGlobeShape();
        return state;
    }

    public boolean isSpawnPickerDismissed() {
        return spawnPickerDismissed;
    }

    public void setSpawnPickerDismissed(boolean spawnPickerDismissed) {
        if (this.spawnPickerDismissed != spawnPickerDismissed) {
            this.spawnPickerDismissed = spawnPickerDismissed;
            setDirty();
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
            setDirty();
        } else {
            LatitudeBiomes.setWorldgenPolicy(normalized);
        }
    }

    public int getGlobeRadius() {
        return globeRadius;
    }

    public void setGlobeRadius(int globeRadius) {
        int normalized = Math.max(0, globeRadius);
        if (this.globeRadius != normalized) {
            this.globeRadius = normalized;
            setDirty();
        }
    }

    public String getGlobeShape() {
        return (globeShape == null || globeShape.isBlank()) ? "classic" : globeShape;
    }

    public void setGlobeShape(String globeShape) {
        String normalized = (globeShape == null || globeShape.isBlank()) ? "classic" : globeShape;
        if (!normalized.equals(this.globeShape)) {
            this.globeShape = normalized;
            setDirty();
        }
        LatitudeBiomes.setGlobeShape(LatitudeBiomes.shapeFromString(normalized));
    }

    private void ensureGlobeShape() {
        LatitudeBiomes.setGlobeShape(LatitudeBiomes.shapeFromString(getGlobeShape()));
    }

    private void ensureWorldgenPolicy(ServerLevel world) {
        if (worldgenPolicy == null) {
            setWorldgenPolicy(inferWorldgenPolicy(world));
            return;
        }
        LatitudeBiomes.setWorldgenPolicy(worldgenPolicy);
    }

    private static WorldgenPolicyVersion inferWorldgenPolicy(ServerLevel world) {
        return world.getGameTime() < 100L
                ? WorldgenPolicyVersion.MODERN_1_3
                : WorldgenPolicyVersion.LEGACY_1_2_X;
    }
}
