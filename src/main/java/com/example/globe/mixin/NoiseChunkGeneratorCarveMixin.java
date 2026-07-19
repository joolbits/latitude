package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.GlobeRegions;
import com.example.globe.core.GlacialCarverLaw;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarBarrensBand;
import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeBiomes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * The ONE carver seam (Phase 5 B-9 P1, mechanism (b) of the swept design
 * {@code docs/binder/phase5-b9-glacial-caves-design-20260719.md}): an order-preserving carver-list
 * filter wrapped around the single {@code BiomeGenerationSettings.getCarvers()} call site inside
 * {@code NoiseBasedChunkGenerator.applyCarvers} (site count verified against the 26.2 bytecode --
 * exactly one {@code invokevirtual getCarvers()Ljava/lang/Iterable;} in the class). This REPLACES the
 * old HEAD blanket-cancel of {@code applyCarvers}; the pure list law lives in
 * {@link GlacialCarverLaw} (Core Logic layer, unit-tested without mixin bootstrapping) and this mixin
 * is the thin adapter that gathers the booleans and holders.
 *
 * <h2>Why attachment is CODE-SIDE by necessity (design ground truth 3)</h2>
 * {@code applyCarvers} resolves each seed-chunk's carver list from the RAW {@code biomeSource} FIELD
 * ({@code lambda$applyCarvers$2}: {@code biomeSource.getNoiseBiome(QuartPos.fromBlock(minBlockX), 0,
 * QuartPos.fromBlock(minBlockZ), randomState.sampler())}) -- not the wrapped
 * {@code getBiomeSource()} getter, not the populate-time resolver, not chunk biome storage. The raw
 * multi_noise source can never emit a {@code globe:} biome, so carvers listed on
 * {@code globe:polar_barrens}' JSON would NEVER fire. The biome JSON therefore stays untouched (its
 * inherited vanilla carver trio is dead wiring at this seam) and the glacial carvers are appended
 * here, where the list actually flows.
 *
 * <h2>LEGACY LEG -- verbatim semantics, CENTER-chunk-keyed (bytecode-reasoned)</h2>
 * On {@code stable(globe:overworld)} worlds (the legacy 15000-radius settings KEY -- scoped to that
 * key ONLY; extending to the sized keys would silently retire vanilla carving poleward of 65 deg on
 * Massive worlds, sweep finding 6) with the applyCarvers CENTER chunk at
 * {@code |minBlockZ + 8| >= }{@link GlobeRegions#POLAR_CAP_START}, every {@code minecraft:*} entry is
 * stripped from every seed-chunk list. The old cancel keyed on the CENTER chunk (the method's
 * {@code chunk} argument), so a poleward center chunk received no arcs from ANY seed chunk while an
 * equatorward center chunk still received arcs FROM poleward seed chunks -- keying the strip on the
 * seed chunk would flip both of those and change blocks on existing legacy worlds. Center-keyed
 * stripping is block-identical to the cancel (empty list = nothing carves = cancel; sweep-verified at
 * block level, the only residue being an empty serialized carving_mask in mid-generation proto-chunk
 * NBT, invisible at FULL status). Full reasoning in {@link GlacialCarverLaw}'s javadoc.
 *
 * <h2>B-9 LEG -- seed-chunk barrens-band LAND append</h2>
 * When {@link LatitudeV2Flags#GLACIAL_CAVES_V1_ENABLED} is on and the SEED chunk (the chunk whose
 * carver list is being resolved -- carver placement is seed-chunk-owned, arcs reach +-8 chunks) is
 * barrens-band LAND, the {@code globe:crevasse} + {@code globe:glacial_tunnels} holders are appended
 * AFTER the raw list. Appending preserves vanilla's per-carver seeding ({@code setLargeFeatureSeed}
 * with {@code seed + listIndex}): vanilla carvers keep indices 0..n-1 and their exact streams; the
 * glacial pair take n, n+1. Decisions, in cheap-first order, all deterministic (same inputs, same
 * answer, every worldgen thread):
 * <ul>
 *   <li><b>Flag + armed radius</b>: static-final flag; {@code LatitudeBiomes.getActiveRadiusBlocks()}
 *       (0 on unarmed JVMs -- headless tools, non-globe sessions).</li>
 *   <li><b>Band</b>: {@code |lat| = |minBlockZ| * 90 / radius} at the seed chunk's MIN CORNER -- the
 *       same fixed deterministic position vanilla's carverBiome resolution samples -- against the
 *       EXACT shared decision the barrens biome/glacier use:
 *       {@link PolarBarrensBand#barrensFraction01} as the cheap early-out (whole world below 82 deg
 *       exits on pure math, no noise sample), then {@link PolarBarrensBand#isBarrens} fed by
 *       {@link LatitudeBiomes#polarBarrensFrayNoise} (one shared coherent fray field, Art VI clean,
 *       no new noise). One sample per seed chunk.</li>
 *   <li><b>Globe OVERWORLD generator only</b>: {@code getBiomeSource() instanceof LatitudeBiomeSource}
 *       reuses the wrap gate of {@code ChunkGeneratorBiomeSourceMixin} (settings-key checked there,
 *       single source of truth) -- the nether/end of a globe world run this same
 *       {@code NoiseBasedChunkGenerator.applyCarvers} with the same armed radius static, and a bare
 *       |z|-band test would append glacial carvers into the nether at deep |z|.</li>
 *   <li><b>LAND probe</b> (the design's explicit sea-column exclusion, findings 1+9): seed protochunks
 *       may be pre-noise (no heightmaps, no blocks, possibly no populated biomes), so the probe reads
 *       the RAW {@code biomeSource} field (via {@link ChunkGeneratorAccessor} -- the same source
 *       object and {@code randomState.sampler()} the carver-biome lambda uses, which vanilla already
 *       calls concurrently from every worldgen thread; a pure function of world seed + coords) at the
 *       seed chunk's min-corner quart with quart-Y of sea level 63 ({@code QuartPos.fromBlock(63)} --
 *       the design's surface-band quart, unlike the lambda's quart-Y 0, so the answer reflects
 *       sea-level ocean-ness, not deepslate-depth cave biomes). Ocean-family = {@code BiomeTags.IS_OCEAN}
 *       ONLY -- rivers are land-band water whose 1-block ice skin the {@code replaceable} tag already
 *       protects (the ice-bridge feature), and the same tag is the structural backstop wherever the
 *       raw-source probe disagrees with the populated map (accepted, design "replaceable decision").</li>
 *   <li><b>Holders</b>: resolved from {@code region.registryAccess()} per accepted seed chunk (two
 *       map gets; keys static-final). Both-or-nothing: if either is missing (broken datapack), the
 *       append is skipped with a one-time warn rather than crashing worldgen -- the JSON schema test
 *       and the boot-time datapack parse gate own that failure class.</li>
 * </ul>
 *
 * <p><b>Local capture honesty:</b> the CENTER chunk comes from the method's own {@code ChunkAccess}
 * argument (unambiguous by type among the args); the SEED chunk pos is {@code @Local(ordinal = 1)}
 * {@link ChunkPos} -- the method body allocates exactly two ChunkPos locals (slot 11 = center at
 * bytecode 50, slot 18 = seed at bytecode 170, verified against the 26.2 disassembly), and mixin
 * application fails loudly at load if that shape ever changes. Flag-off + non-legacy fast path
 * returns vanilla's own Iterable object untouched -- byte-identical by construction.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseChunkGeneratorCarveMixin {
    @Unique
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_KEY = ResourceKey.create(
            Registries.NOISE_SETTINGS,
            Identifier.fromNamespaceAndPath("globe", "overworld")
    );

    @Unique
    private static final ResourceKey<ConfiguredWorldCarver<?>> GLOBE_CREVASSE_KEY = ResourceKey.create(
            Registries.CONFIGURED_CARVER,
            Identifier.fromNamespaceAndPath("globe", "crevasse")
    );

    @Unique
    private static final ResourceKey<ConfiguredWorldCarver<?>> GLOBE_GLACIAL_TUNNELS_KEY = ResourceKey.create(
            Registries.CONFIGURED_CARVER,
            Identifier.fromNamespaceAndPath("globe", "glacial_tunnels")
    );

    /** The sea-level probe height (design: "quart-Y at ~sea-level 63"). */
    @Unique
    private static final int GLOBE_SEA_LEVEL_PROBE_Y = 63;

    /** A carver entry is a legacy-strip target iff it is NOT ours ({@code globe:*}). The retired
     *  HEAD cancel suppressed ALL carving on legacy caps -- including third-party datapack carvers
     *  (Terralith etc.) and unkeyed inline holders -- so cancel-identity requires the strip to
     *  remove everything non-globe, not just {@code minecraft:*} (sweep 2026-07-19 finding 2).
     *  Unkeyed holders have no namespace and therefore strip. */
    @Unique
    private static final Predicate<Holder<ConfiguredWorldCarver<?>>> GLOBE_LEGACY_STRIP_TARGET = holder ->
            holder.unwrapKey().map(key -> !"globe".equals(key.identifier().getNamespace())).orElse(true);

    @Unique
    private static final AtomicBoolean GLOBE_MISSING_CARVER_WARNED = new AtomicBoolean(false);

    @WrapOperation(
            method = "applyCarvers(Lnet/minecraft/server/level/WorldGenRegion;JLnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/BiomeGenerationSettings;getCarvers()Ljava/lang/Iterable;"
            )
    )
    private Iterable<Holder<ConfiguredWorldCarver<?>>> globe$filterSeedChunkCarverList(
            BiomeGenerationSettings settings,
            Operation<Iterable<Holder<ConfiguredWorldCarver<?>>>> original,
            @Local(argsOnly = true) WorldGenRegion region,
            @Local(argsOnly = true) RandomState randomState,
            @Local(argsOnly = true) ChunkAccess centerChunk,
            @Local(ordinal = 1) ChunkPos seedChunkPos) {
        Iterable<Holder<ConfiguredWorldCarver<?>>> raw = original.call(settings);
        NoiseBasedChunkGenerator self = (NoiseBasedChunkGenerator) (Object) this;

        boolean legacyStrip = GlacialCarverLaw.legacyStripApplies(
                self.stable(GLOBE_SETTINGS_KEY),
                centerChunk.getPos().getMinBlockZ(),
                GlobeRegions.POLAR_CAP_START);
        List<Holder<ConfiguredWorldCarver<?>>> glacial =
                globe$glacialCarversForSeedChunk(self, region, randomState, seedChunkPos);

        if (!legacyStrip && glacial.isEmpty()) {
            return raw; // both legs dormant: vanilla's own Iterable, untouched -- byte-identical.
        }
        return GlacialCarverLaw.filter(raw, legacyStrip, !glacial.isEmpty(), glacial, GLOBE_LEGACY_STRIP_TARGET);
    }

    /**
     * The B-9 leg's per-seed-chunk decision + holder resolution (see class javadoc for each gate's
     * why). Returns the {@code [crevasse, glacial_tunnels]} pair when the seed chunk is flag-on
     * barrens-band land on a globe overworld generator, else an empty list.
     */
    @Unique
    private List<Holder<ConfiguredWorldCarver<?>>> globe$glacialCarversForSeedChunk(
            NoiseBasedChunkGenerator self, WorldGenRegion region, RandomState randomState, ChunkPos seedChunkPos) {
        if (!LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
            return List.of();
        }
        int radius = LatitudeBiomes.getActiveRadiusBlocks();
        if (radius <= 0) {
            return List.of();
        }
        int minBlockX = seedChunkPos.getMinBlockX();
        int minBlockZ = seedChunkPos.getMinBlockZ();
        double absLatDeg = Math.abs((double) minBlockZ) * 90.0 / radius;
        if (PolarBarrensBand.barrensFraction01(absLatDeg) <= 0.0) {
            return List.of(); // below the band onset: pure-math exit, no noise sample, no probe.
        }
        if (!(self.getBiomeSource() instanceof LatitudeBiomeSource)) {
            return List.of(); // nether/end (or a non-globe generator with a stale armed radius).
        }
        if (!PolarBarrensBand.isBarrens(absLatDeg, LatitudeBiomes.polarBarrensFrayNoise(minBlockX, minBlockZ))) {
            return List.of(); // the shared fray decision said this seed chunk stays non-barrens.
        }
        Holder<Biome> seaProbe = ((ChunkGeneratorAccessor) (Object) self).globe$getRawBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(minBlockX),
                QuartPos.fromBlock(GLOBE_SEA_LEVEL_PROBE_Y),
                QuartPos.fromBlock(minBlockZ),
                randomState.sampler());
        if (seaProbe.is(BiomeTags.IS_OCEAN)) {
            return List.of(); // sea seed chunk: the frozen sea is sacred (and structurally uncarvable anyway).
        }
        Registry<ConfiguredWorldCarver<?>> carvers =
                region.registryAccess().lookupOrThrow(Registries.CONFIGURED_CARVER);
        Optional<Holder.Reference<ConfiguredWorldCarver<?>>> crevasse = carvers.get(GLOBE_CREVASSE_KEY);
        Optional<Holder.Reference<ConfiguredWorldCarver<?>>> tunnels = carvers.get(GLOBE_GLACIAL_TUNNELS_KEY);
        if (crevasse.isEmpty() || tunnels.isEmpty()) {
            if (GLOBE_MISSING_CARVER_WARNED.compareAndSet(false, true)) {
                GlobeMod.LOGGER.warn(
                        "[Latitude] B-9 glacial carvers missing from the configured_carver registry "
                                + "(crevasse present={}, glacial_tunnels present={}) action=skipping append",
                        crevasse.isPresent(), tunnels.isPresent());
            }
            return List.of();
        }
        return List.of(crevasse.get(), tunnels.get());
    }
}
