package com.example.globe.world;

/**
 * Keeps the process-global Latitude context attached to the exact loaded overworld generator.
 * Inline noise settings do not carry a registry key, so authorizing every inline generator would
 * also repaint unrelated custom dimensions in the same save.
 */
public final class WorldgenGeneratorAuthorityPolicy {
    private WorldgenGeneratorAuthorityPolicy() {
    }

    public static boolean shouldApply(
            boolean registeredGlobeSettings,
            boolean activeWorldgenAuthority,
            Object candidateGenerator,
            Object activeOverworldGenerator) {
        return registeredGlobeSettings
                || isExactActiveOverworld(
                        activeWorldgenAuthority,
                        candidateGenerator,
                        activeOverworldGenerator);
    }

    public static boolean isExactActiveOverworld(
            boolean activeWorldgenAuthority,
            Object candidateGenerator,
            Object activeOverworldGenerator) {
        return activeWorldgenAuthority
                && candidateGenerator != null
                && candidateGenerator == activeOverworldGenerator;
    }
}
