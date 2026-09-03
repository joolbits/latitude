package com.example.globe.world;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.List;
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
            Identifier.fromNamespaceAndPath("globe", "latitude_world_state"),
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
                    Codec.INT.optionalFieldOf("content_roster_revision", 0)
                            .forGetter(LatitudeWorldState::getContentRosterRevision),
                    Codec.STRING.listOf().optionalFieldOf("content_roster_additions", List.of())
                            .forGetter(LatitudeWorldState::getContentRosterAdditions),
                    Codec.STRING.optionalFieldOf("last_known_band")
                            .forGetter((LatitudeWorldState state) -> Optional.ofNullable(state.lastKnownBandId))
            ).apply(instance, (spawnPickerDismissed, worldgenPolicy, globeRadius, providerTicketProfile,
                                vanillaRepresentationProfile, caveRepresentationProfile,
                                contentRosterRevision, contentRosterAdditions, lastKnownBandId) ->
                    new LatitudeWorldState(spawnPickerDismissed, normalizeWorldgenPolicy(worldgenPolicy),
                            globeRadius, providerTicketProfile.orElse(null),
                            vanillaRepresentationProfile.orElse(null), caveRepresentationProfile.orElse(null),
                            contentRosterRevision, contentRosterAdditions,
                            lastKnownBandId.orElse(null)))),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private boolean spawnPickerDismissed;
    private WorldgenPolicyVersion worldgenPolicy;
    private int globeRadius;
    private String providerTicketProfile;
    private String vanillaRepresentationProfile;
    private String caveRepresentationProfile;
    private int contentRosterRevision;
    private List<String> contentRosterAdditions;
    private String lastKnownBandId;

    public LatitudeWorldState() {
        this(false, Optional.empty(), 0, null, null, null, 0, List.of(), null);
    }

    private LatitudeWorldState(boolean spawnPickerDismissed, Optional<WorldgenPolicyVersion> worldgenPolicy,
                               int globeRadius, String providerTicketProfile,
                               String vanillaRepresentationProfile, String caveRepresentationProfile,
                               int contentRosterRevision, List<String> contentRosterAdditions,
                               String lastKnownBandId) {
        this.spawnPickerDismissed = spawnPickerDismissed;
        this.worldgenPolicy = normalizeWorldgenPolicy(worldgenPolicy).orElse(null);
        this.globeRadius = Math.max(0, globeRadius);
        this.providerTicketProfile = providerTicketProfile;
        this.vanillaRepresentationProfile = vanillaRepresentationProfile;
        this.caveRepresentationProfile = caveRepresentationProfile;
        this.contentRosterRevision = contentRosterRevision;
        this.contentRosterAdditions = List.copyOf(
                contentRosterAdditions == null ? List.of() : contentRosterAdditions);
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

    public int getContentRosterRevision() {
        return contentRosterRevision;
    }

    public List<String> getContentRosterAdditions() {
        return contentRosterAdditions;
    }

    /** Fresh worlds already captured the current registry in their immutable birth roster. */
    public void markContentRosterCurrent() {
        setContentRosterStamp(ContentRosterUpgradePolicy.CURRENT_REVISION, List.of());
    }

    /**
     * Adds the current content stamp only to a structurally complete 26.2-style V4 world.
     * Missing or damaged state, older policies, and altered saved additions remain untouched.
     */
    public boolean tryUpgradeContentRoster(Collection<String> activeRegistryIds) {
        Optional<BiomeSelectionProfile> birthProfile = getProviderTicketProfile();
        boolean completeV4ProviderState = getWorldgenPolicy()
                == WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE
                && globeRadius > 0
                && birthProfile.isPresent()
                && getVanillaRepresentationProfile().isPresent()
                && getCaveRepresentationProfile().isPresent();
        boolean birthHasDappled = birthProfile
                .map(profile -> profile.contains(
                        BiomeRoute.TEMPERATE_LOWLAND,
                        DappledForestPlacementPolicy.BIOME_ID))
                .orElse(false);
        ContentRosterUpgradePolicy.Decision decision = ContentRosterUpgradePolicy.evaluate(
                contentRosterRevision,
                contentRosterAdditions,
                completeV4ProviderState,
                activeRegistryIds,
                birthHasDappled);
        if (!decision.changed()) return false;
        setContentRosterStamp(decision.revision(), decision.additions());
        return true;
    }

    /** Runtime-only roster; the encoded provider ticket remains the world's original birth roster. */
    public Optional<BiomeSelectionProfile> getRuntimeProviderTicketProfile(
            Collection<String> activeRegistryIds) {
        Optional<BiomeSelectionProfile> birthProfile = getProviderTicketProfile();
        if (birthProfile.isEmpty()) return Optional.empty();
        boolean completeV4ProviderState = getWorldgenPolicy()
                == WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE
                && globeRadius > 0
                && getVanillaRepresentationProfile().isPresent()
                && getCaveRepresentationProfile().isPresent();
        if (!completeV4ProviderState) return birthProfile;
        List<String> additions = ContentRosterUpgradePolicy.validRuntimeAdditions(
                contentRosterRevision, contentRosterAdditions, activeRegistryIds);
        return additions.isEmpty()
                ? birthProfile
                : Optional.of(birthProfile.get().withRuntimeAdditions(additions));
    }

    private void setContentRosterStamp(int revision, Collection<String> additions) {
        int normalizedRevision = Math.max(0, revision);
        List<String> normalizedAdditions = List.copyOf(
                additions == null ? List.of() : additions);
        if (contentRosterRevision != normalizedRevision
                || !contentRosterAdditions.equals(normalizedAdditions)) {
            contentRosterRevision = normalizedRevision;
            contentRosterAdditions = normalizedAdditions;
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
