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
        MODERN_1_3,
        PROVIDER_TICKET_V1,
        PROVIDER_TICKET_V2_COVERAGE,
        PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE,
        PROVIDER_TICKET_V4_CAVE_COVERAGE
    }

    private static final Codec<WorldgenPolicyVersion> WORLDGEN_POLICY_CODEC = Codec.STRING.xmap(
            LatitudeWorldState::decodeWorldgenPolicy,
            Enum::name
    );

    private static final SavedDataType<LatitudeWorldState> STATE_TYPE = new SavedDataType<>(
            // 26.2's SavedDataType takes an Identifier; 1.21.11's takes a plain String, and that
            // string IS the .dat filename under the world's data/ folder -- a persistence
            // contract, not a label. Deliberately matches the 1.4-era 1.21.11 port's filename so
            // a 1.4 world upgrading on this target keeps its globe radius and zone state instead
            // of silently regenerating as vanilla (the 18f2629f bug class).
            "globe_latitude_world_state",
            LatitudeWorldState::new,
            RecordCodecBuilder.<LatitudeWorldState>create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("spawn_picker_dismissed", false)
                            .forGetter(LatitudeWorldState::isSpawnPickerDismissed),
                    WORLDGEN_POLICY_CODEC.optionalFieldOf("worldgen_policy")
                            .forGetter((LatitudeWorldState state) -> Optional.ofNullable(state.worldgenPolicy)),
                    Codec.INT.optionalFieldOf("globe_radius", 0)
                            .forGetter(LatitudeWorldState::getGlobeRadius),
                    Codec.STRING.optionalFieldOf("provider_ticket_profile")
                            .forGetter((LatitudeWorldState state) -> Optional.ofNullable(state.providerTicketProfile)),
                    Codec.STRING.optionalFieldOf("vanilla_representation_profile")
                            .forGetter((LatitudeWorldState state) -> Optional.ofNullable(state.vanillaRepresentationProfile)),
                    Codec.STRING.optionalFieldOf("cave_representation_profile")
                            .forGetter((LatitudeWorldState state) -> Optional.ofNullable(state.caveRepresentationProfile)),
                    Codec.STRING.optionalFieldOf("last_known_band")
                            .forGetter((LatitudeWorldState state) -> Optional.ofNullable(state.lastKnownBandId))
            ).apply(instance, (spawnPickerDismissed, worldgenPolicy, globeRadius, providerTicketProfile,
                                vanillaRepresentationProfile, caveRepresentationProfile, lastKnownBandId) ->
                    new LatitudeWorldState(spawnPickerDismissed, normalizeWorldgenPolicy(worldgenPolicy),
                            globeRadius, providerTicketProfile.orElse(null),
                            vanillaRepresentationProfile.orElse(null), caveRepresentationProfile.orElse(null),
                            lastKnownBandId.orElse(null)))),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private boolean spawnPickerDismissed;
    private WorldgenPolicyVersion worldgenPolicy;
    private int globeRadius;
    private String providerTicketProfile;
    private String vanillaRepresentationProfile;
    private String caveRepresentationProfile;
    private String lastKnownBandId;

    public LatitudeWorldState() {
        this(false, Optional.empty(), 0, null, null, null, null);
    }

    private LatitudeWorldState(boolean spawnPickerDismissed, Optional<WorldgenPolicyVersion> worldgenPolicy,
                               int globeRadius, String providerTicketProfile,
                               String vanillaRepresentationProfile, String caveRepresentationProfile,
                               String lastKnownBandId) {
        this.spawnPickerDismissed = spawnPickerDismissed;
        this.worldgenPolicy = normalizeWorldgenPolicy(worldgenPolicy).orElse(null);
        this.globeRadius = Math.max(0, globeRadius);
        this.providerTicketProfile = providerTicketProfile;
        this.vanillaRepresentationProfile = vanillaRepresentationProfile;
        this.caveRepresentationProfile = caveRepresentationProfile;
        this.lastKnownBandId = lastKnownBandId;
    }

    private static Optional<WorldgenPolicyVersion> normalizeWorldgenPolicy(Optional<WorldgenPolicyVersion> worldgenPolicy) {
        return worldgenPolicy == null ? Optional.empty() : worldgenPolicy;
    }

    public static LatitudeWorldState get(ServerLevel world) {
        LatitudeWorldState state = world.getDataStorage().computeIfAbsent(STATE_TYPE);
        state.ensureWorldgenPolicy(world);
        return state;
    }

    /** Reads an existing Latitude state without creating or dirtying a vanilla save. */
    public static LatitudeWorldState getIfPresent(ServerLevel world) {
        return world.getDataStorage().get(STATE_TYPE);
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

    public Optional<BiomeSelectionProfile> getProviderTicketProfile() {
        if (!isProviderTicketPolicy(getWorldgenPolicy())) return Optional.empty();
        if (providerTicketProfile == null || providerTicketProfile.isBlank()) return Optional.empty();
        try {
            return Optional.of(BiomeSelectionProfile.decode(providerTicketProfile));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static boolean isProviderTicketPolicy(WorldgenPolicyVersion policy) {
        return policy == WorldgenPolicyVersion.PROVIDER_TICKET_V1
                || policy == WorldgenPolicyVersion.PROVIDER_TICKET_V2_COVERAGE
                || policy == WorldgenPolicyVersion.PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE
                || policy == WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE;
    }

    /** Captured once, only by the trusted fresh-world marker before spawn chunks exist. */
    public void setProviderTicketProfile(BiomeSelectionProfile profile) {
        String encoded = profile == null ? null : profile.encode();
        if (!java.util.Objects.equals(providerTicketProfile, encoded)) {
            providerTicketProfile = encoded;
            setDirty();
        }
    }

    public Optional<VanillaBiomeRepresentationProfile> getVanillaRepresentationProfile() {
        if ((getWorldgenPolicy() != WorldgenPolicyVersion.PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE
                && getWorldgenPolicy() != WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE)
                || vanillaRepresentationProfile == null || vanillaRepresentationProfile.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(VanillaBiomeRepresentationProfile.decode(vanillaRepresentationProfile));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /** Captured once with the provider roster before fresh-world spawn chunks generate. */
    public void setVanillaRepresentationProfile(VanillaBiomeRepresentationProfile profile) {
        String encoded = profile == null ? null : profile.encode();
        if (!java.util.Objects.equals(vanillaRepresentationProfile, encoded)) {
            vanillaRepresentationProfile = encoded;
            setDirty();
        }
    }

    public Optional<CaveBiomeRepresentationProfile> getCaveRepresentationProfile() {
        if (getWorldgenPolicy() != WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE
                || caveRepresentationProfile == null || caveRepresentationProfile.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(CaveBiomeRepresentationProfile.decode(caveRepresentationProfile));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /** Captured once with the provider roster before fresh-world spawn chunks generate. */
    public void setCaveRepresentationProfile(CaveBiomeRepresentationProfile profile) {
        String encoded = profile == null ? null : profile.encode();
        if (!java.util.Objects.equals(caveRepresentationProfile, encoded)) {
            caveRepresentationProfile = encoded;
            setDirty();
        }
    }

    /** Canonical id (e.g. "temperate") of the band a player was last known to occupy, if any. */
    public Optional<String> getLastKnownBandId() {
        return Optional.ofNullable(lastKnownBandId);
    }

    /** Updated periodically while a player is in the overworld, and once more on disconnect. */
    public void setLastKnownBandId(String bandId) {
        if (!java.util.Objects.equals(lastKnownBandId, bandId)) {
            lastKnownBandId = bandId;
            setDirty();
        }
    }

    private void ensureWorldgenPolicy(ServerLevel world) {
        if (worldgenPolicy == null) {
            setWorldgenPolicy(inferWorldgenPolicy(world));
            return;
        }
        LatitudeBiomes.setWorldgenPolicy(worldgenPolicy);
    }

    static WorldgenPolicyVersion decodeWorldgenPolicy(String encoded) {
        if (encoded == null) {
            return WorldgenPolicyVersion.LEGACY_1_2_X;
        }
        try {
            return WorldgenPolicyVersion.valueOf(encoded);
        } catch (IllegalArgumentException ignored) {
            return WorldgenPolicyVersion.LEGACY_1_2_X;
        }
    }

    private static WorldgenPolicyVersion inferWorldgenPolicy(ServerLevel world) {
        // Missing policy means the save predates the policy field. New UI-created Latitude worlds
        // are marked MODERN explicitly from GlobePending during their first overworld load.
        return WorldgenPolicyVersion.LEGACY_1_2_X;
    }
}
