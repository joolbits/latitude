package com.example.globe.world;

import com.example.globe.core.terrain.TerrainLawPolicy;
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

    // P2-8 terrain-law stamp (glue only; every DECISION lives in the pure TerrainLawPolicy core).
    private static final Codec<TerrainLawPolicy.TerrainLaw> TERRAIN_LAW_CODEC =
            RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("formula_version").forGetter(TerrainLawPolicy.TerrainLaw::formulaVersion),
                    Codec.BOOL.fieldOf("enabled").forGetter(TerrainLawPolicy.TerrainLaw::enabled),
                    Codec.DOUBLE.fieldOf("strength").forGetter(TerrainLawPolicy.TerrainLaw::strength),
                    Codec.DOUBLE.fieldOf("ocean_ratio").forGetter(TerrainLawPolicy.TerrainLaw::oceanRatio),
                    Codec.DOUBLE.fieldOf("grip_width").forGetter(TerrainLawPolicy.TerrainLaw::gripWidth)
            ).apply(i, TerrainLawPolicy.TerrainLaw::new));

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
                            .forGetter(LatitudeWorldState::getGlobeShapeOptional),
                    // Absent = NEVER STAMPED — the exact globe_shape Optional-no-default sentinel idiom
                    // (bug-catcher #1 precedent): a legacy save must never read as a stamped one.
                    TERRAIN_LAW_CODEC.optionalFieldOf("terrain_law")
                            .forGetter(LatitudeWorldState::getTerrainLawOptional),
                    Codec.BOOL.optionalFieldOf("terrain_law_inferred", false)
                            .forGetter(LatitudeWorldState::isTerrainLawInferred)
            ).apply(instance, (spawnPickerDismissed, worldgenPolicy, globeRadius, globeShape, terrainLaw, terrainLawInferred) ->
                    new LatitudeWorldState(spawnPickerDismissed, normalizeWorldgenPolicy(worldgenPolicy), globeRadius, globeShape, terrainLaw, terrainLawInferred))),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private boolean spawnPickerDismissed;
    private WorldgenPolicyVersion worldgenPolicy;
    private int globeRadius;
    private String globeShape;
    /** null = never stamped (P2-8 sentinel, same posture as {@link #globeShape}). */
    private TerrainLawPolicy.TerrainLaw terrainLaw;
    private boolean terrainLawInferred;

    public LatitudeWorldState() {
        this(false, Optional.empty(), 0, Optional.empty(), Optional.empty(), false);
    }

    private LatitudeWorldState(boolean spawnPickerDismissed, Optional<WorldgenPolicyVersion> worldgenPolicy, int globeRadius, Optional<String> globeShape,
                               Optional<TerrainLawPolicy.TerrainLaw> terrainLaw, boolean terrainLawInferred) {
        this.spawnPickerDismissed = spawnPickerDismissed;
        this.worldgenPolicy = normalizeWorldgenPolicy(worldgenPolicy).orElse(null);
        this.globeRadius = Math.max(0, globeRadius);
        // null = "never stamped" (unset). Kept DISTINCT from an explicit "classic" so an existing/legacy save
        // (absent globe_shape field) is never mistaken for a brand-new world and re-stamped to Mercator
        // (bug-catcher #1: silent Classic->Mercator flip + world-border regression).
        this.globeShape = globeShape.filter(s -> !s.isBlank()).orElse(null);
        this.terrainLaw = terrainLaw.orElse(null);
        this.terrainLawInferred = terrainLawInferred;
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

    /** Raw persisted terrain law: empty when never stamped (P2-8 sentinel; legacy save). */
    public Optional<TerrainLawPolicy.TerrainLaw> getTerrainLawOptional() {
        return Optional.ofNullable(terrainLaw);
    }

    /** True once a terrain law has been stamped (birth record or adopted/inferred stamp). */
    public boolean hasTerrainLaw() {
        return terrainLaw != null;
    }

    /** True when the stamp is a legacy RECONSTRUCTION (created pre-guard, treated as OFF), not a birth record. */
    public boolean isTerrainLawInferred() {
        return terrainLawInferred;
    }

    /** Stamp (or re-stamp) the world's terrain law. Compare-then-dirty, the house pattern. */
    public void setTerrainLaw(TerrainLawPolicy.TerrainLaw law, boolean inferred) {
        boolean same = law == null ? this.terrainLaw == null
                : (this.terrainLaw != null && this.terrainLaw.equals(law) && this.terrainLawInferred == inferred);
        if (!same) {
            this.terrainLaw = law;
            this.terrainLawInferred = inferred;
            setDirty();
        }
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
