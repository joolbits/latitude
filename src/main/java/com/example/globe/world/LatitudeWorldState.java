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
                    Codec.STRING.optionalFieldOf("globe_shape")
                            .forGetter(LatitudeWorldState::getGlobeShapeOptional)
            ).apply(instance, (spawnPickerDismissed, worldgenPolicy, globeRadius, globeShape) ->
                    new LatitudeWorldState(spawnPickerDismissed, normalizeWorldgenPolicy(worldgenPolicy), globeRadius, globeShape))),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private boolean spawnPickerDismissed;
    private WorldgenPolicyVersion worldgenPolicy;
    private int globeRadius;
    private String globeShape;

    public LatitudeWorldState() {
        this(false, Optional.empty(), 0, Optional.empty());
    }

    private LatitudeWorldState(boolean spawnPickerDismissed, Optional<WorldgenPolicyVersion> worldgenPolicy, int globeRadius, Optional<String> globeShape) {
        this.spawnPickerDismissed = spawnPickerDismissed;
        this.worldgenPolicy = normalizeWorldgenPolicy(worldgenPolicy).orElse(null);
        this.globeRadius = Math.max(0, globeRadius);
        // null = "never stamped" (unset). Kept DISTINCT from an explicit "classic" so an existing/legacy save
        // (absent globe_shape field) is never mistaken for a brand-new world and re-stamped to Mercator
        // (bug-catcher #1: silent Classic->Mercator flip + world-border regression).
        this.globeShape = globeShape.filter(s -> !s.isBlank()).orElse(null);
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

    /** Effective shape for live rendering / border math: "classic" when never stamped. */
    public String getGlobeShape() {
        return (globeShape == null || globeShape.isBlank()) ? "classic" : globeShape;
    }

    /** Raw persisted shape: empty when never stamped (absent field / legacy save), distinct from "classic". */
    public Optional<String> getGlobeShapeOptional() {
        return (globeShape == null || globeShape.isBlank()) ? Optional.empty() : Optional.of(globeShape);
    }

    /** True once a shape has been explicitly stamped (a brand-new world). False for legacy / pre-2.0 saves. */
    public boolean hasGlobeShape() {
        return globeShape != null && !globeShape.isBlank();
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
