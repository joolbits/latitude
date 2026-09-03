package com.example.globe.world;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Closed, additive content-roster policy for existing provider-ticket worlds. */
public final class ContentRosterUpgradePolicy {
    public static final int CURRENT_REVISION = 1;
    private static final List<String> DAPPLED_ADDITION =
            List.of(DappledForestPlacementPolicy.BIOME_ID);

    private ContentRosterUpgradePolicy() {
    }

    public record Decision(int revision, List<String> additions, boolean changed) {
        public Decision {
            additions = List.copyOf(additions == null ? List.of() : additions);
        }
    }

    public static Decision evaluate(
            int savedRevision,
            Collection<String> savedAdditions,
            boolean completeV4ProviderState,
            Collection<String> activeRegistryIds,
            boolean birthRosterAlreadyContainsDappled) {
        List<String> existing = List.copyOf(
                savedAdditions == null ? List.of() : savedAdditions);
        if (savedRevision != 0 || !existing.isEmpty()
                || !completeV4ProviderState || activeRegistryIds == null
                || !Set.copyOf(activeRegistryIds).contains(
                        DappledForestPlacementPolicy.BIOME_ID)) {
            return new Decision(savedRevision, existing, false);
        }
        return birthRosterAlreadyContainsDappled
                ? new Decision(CURRENT_REVISION, List.of(), true)
                : new Decision(CURRENT_REVISION, DAPPLED_ADDITION, true);
    }

    public static List<String> validRuntimeAdditions(
            int savedRevision,
            Collection<String> savedAdditions,
            Collection<String> activeRegistryIds) {
        List<String> existing = List.copyOf(
                savedAdditions == null ? List.of() : savedAdditions);
        if (savedRevision != CURRENT_REVISION
                || !existing.equals(DAPPLED_ADDITION)
                || activeRegistryIds == null
                || !Set.copyOf(activeRegistryIds).contains(
                        DappledForestPlacementPolicy.BIOME_ID)) {
            return List.of();
        }
        return DAPPLED_ADDITION;
    }
}
