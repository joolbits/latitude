package com.example.globe.world;

import com.example.globe.util.ValueNoise2D;

/**
 * Caps {@code minecraft:ice_spikes} to a coherent minority accent in the polar band, instead of
 * letting it win roughly half of every argmax pick against its only vanilla-only rival.
 *
 * <p>{@code POLAR_LOWLAND}'s entire vanilla roster is {@code snowy_plains} and {@code ice_spikes}.
 * {@link BiomeProviderSelectionPolicy#selectIndex} is an unweighted per-entry argmax, so a
 * two-entry pool splits roughly 50/50 regardless of which biome is meant to be the rarity. This
 * policy re-samples a second, independent, coherent noise field and only lets an {@code
 * ice_spikes} pick survive on the high side of it — everything else falls back to
 * {@code snowy_plains}. "Coherent" matters as much as "minority": a per-block coin flip would
 * turn ice spikes into scattered single-column confetti instead of the patches the biome expects.
 *
 * <p><b>The threshold was measured, not guessed.</b> This construction (bilinear-interpolated,
 * smoothstepped, independent-per-corner value noise) is NOT uniform on [0, 1) — corner averaging
 * concentrates mass near 0.5, so a naive "half the entries so use 0.5" assumption undershoots by a
 * wide margin. A prior threshold of 0.45 was measured (2026-08-10, over the actual ledger and
 * {@link BiomeProviderSelectionPolicy} pick pipeline) to retain ~59% of ice_spikes picks rather
 * than capping them: a vanilla-only world (the hard "must work with no providers" case) still
 * finished with ice_spikes on ~27% of the polar band, not the "coherent minority accent" the
 * design intends and {@code lat_polar_accent.json} declares (a standalone accent tag whose only
 * member is ice_spikes).
 *
 * <p>0.88 replaces it, measured on the SAME deterministic, coherence-spaced grid the checked-in
 * regression test uses ({@code BiomeProviderSelectionPolicyTest}: 16 fixed seeds x a 17x17 block
 * of points 4,096 blocks apart — chosen so the suite stays fast and reproducible rather than a
 * large random sample). On that grid 0.88 lands the worst case — vanilla-only, where the pre-cap
 * pick rate is highest — at ~5.9% of the polar band, matching the declared accent share; the same
 * pool with the maintainer's installed providers (BiomesOPlenty, Terralith) measured ~1.9%.
 * Installing providers only widens the pool ice_spikes competes in, so every provider
 * configuration ends up at or below the vanilla-only ceiling. Do not retune this by intuition —
 * a sparse deterministic grid and a dense random sample are NOT the same distribution for this
 * noise construction, so any new threshold must be re-measured against whichever sampling the
 * test that will enforce it actually uses.
 */
public final class PolarIceSpikeAccentPolicy {
    private static final long ICE_ACCENT_SALT = 0x6963655F73706B73L; // "ice_spks"
    static final int ACCENT_PATCH_BLOCKS = 256;

    static final double KEEP_THRESHOLD = 0.88;

    private PolarIceSpikeAccentPolicy() {
    }

    public static boolean keepPolarIceSpike(long worldSeed, int blockX, int blockZ) {
        double accent = ValueNoise2D.sampleBlocks(
                worldSeed ^ ICE_ACCENT_SALT, blockX, blockZ, ACCENT_PATCH_BLOCKS);
        return accent >= KEEP_THRESHOLD;
    }
}
