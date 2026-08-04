package com.example.globe.world;

/** Final geography law for distinguishing ordinary swamp from coastal mangrove habitat. */
public final class WetlandIdentityPolicy {
    private WetlandIdentityPolicy() {
    }

    public static boolean shouldUseMangrove(
            boolean finalBiomeIsSwamp,
            int bandIndex,
            int maximumMangroveBand,
            boolean mountainLike,
            int oceanDistance,
            int maximumOceanDistance,
            boolean lowlandTerrain) {
        return finalBiomeIsSwamp
                && bandIndex >= 0
                && bandIndex <= maximumMangroveBand
                && !mountainLike
                && oceanDistance >= 0
                && oceanDistance <= maximumOceanDistance
                && lowlandTerrain;
    }
}
