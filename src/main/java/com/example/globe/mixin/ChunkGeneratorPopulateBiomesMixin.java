package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.terrain.GeoTerrainBiasFunction;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeBiomes;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class ChunkGeneratorPopulateBiomesMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("LatitudeBiomes");

    @Unique
    private static final boolean FIX_SURFACE_CAVE_BIOMES =
            Boolean.parseBoolean(System.getProperty("latitude.fixSurfaceCaveBiomes", "true"));

    @Unique
    private static final int MAX_CAVE_BIOME_Y =
            Integer.getInteger("latitude.maxCaveBiomeY", 96);

    @Unique
    private static final int HARD_DECK_SURFACE_Y =
            Integer.getInteger("latitude.hardDeckSurfaceY", 20);

    @Unique
    private static final int CAVE_SURFACE_MARGIN_BLOCKS =
            Integer.getInteger("latitude.caveSurfaceMarginBlocks", 8);

    @Unique
    private static final boolean DEBUG_CAVE_CLAMP =
            Boolean.getBoolean("latitude.debugCaveClamp");

    @Unique
    private static final boolean DEBUG_CAVE_DECK =
            Boolean.getBoolean("latitude.debugCaveDeck");

    @Unique
    private static final boolean DEBUG_WORLDGEN_PATH =
            Boolean.getBoolean("latitude.debugWorldgenPath");

    @Unique
    private static final boolean DEBUG_BIOME_PICK =
            Boolean.getBoolean("latitude.debugBiomePick");

    @Unique
    private static final java.util.concurrent.atomic.AtomicBoolean DEBUG_POPULATE_GATE_REJECT_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Unique
    private static final java.util.concurrent.atomic.AtomicBoolean DEBUG_POPULATE_NO_STRUCTURE_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Unique
    private static final java.util.concurrent.atomic.AtomicBoolean DEBUG_WORLDGEN_PATH_ONCE_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Unique
    private static final java.util.concurrent.atomic.AtomicInteger DEBUG_POPBIO_LOG_COUNT =
            new java.util.concurrent.atomic.AtomicInteger(0);

    @Unique
    private static final String GLOBE_SETTINGS_CHECKED =
            "globe:overworld|globe:overworld_xsmall|globe:overworld_small|globe:overworld_regular|globe:overworld_large|globe:overworld_massive";
    @Unique
    private static final ThreadLocal<RandomState> globe$noiseConfigTL = new ThreadLocal<>();

    // Only apply Latitude to your globe overworld settings (keeps Nether/End sane).
    @Unique
    private static final Identifier GLOBE_SETTINGS_ID = Identifier.fromNamespaceAndPath("globe", "overworld");

    @Unique
    private static final Identifier GLOBE_SETTINGS_XSMALL_ID = Identifier.fromNamespaceAndPath("globe", "overworld_xsmall");

    @Unique
    private static final Identifier GLOBE_SETTINGS_SMALL_ID = Identifier.fromNamespaceAndPath("globe", "overworld_small");

    @Unique
    private static final Identifier GLOBE_SETTINGS_REGULAR_ID = Identifier.fromNamespaceAndPath("globe", "overworld_regular");

    @Unique
    private static final Identifier GLOBE_SETTINGS_LARGE_ID = Identifier.fromNamespaceAndPath("globe", "overworld_large");

    @Unique
    private static final Identifier GLOBE_SETTINGS_MASSIVE_ID = Identifier.fromNamespaceAndPath("globe", "overworld_massive");

    @Unique
    private static final Identifier LUSH_CAVES_ID = Identifier.fromNamespaceAndPath("minecraft", "lush_caves");

    @Unique
    private static final Identifier DRIPSTONE_CAVES_ID = Identifier.fromNamespaceAndPath("minecraft", "dripstone_caves");

    // 26.2 "Chaos Cubed" added the sulfur_caves cave biome. Treat it like the other cave biomes so it is
    // preserved underground instead of being reclassified as a surface latitude biome (which would prevent it
    // from ever generating).
    @Unique
    private static final Identifier SULFUR_CAVES_ID = Identifier.fromNamespaceAndPath("minecraft", "sulfur_caves");

    @Unique
    private static final Identifier DEEP_DARK_ID = Identifier.fromNamespaceAndPath("minecraft", "deep_dark");

    // B-9 P2 KEYSTONE (Crew C, owner flight TEST 113 2026-07-19: polar underground was "generic dark
    // stone"): the globe:glacial_caves underground biome id, resolved once per chunk (see the swap in the
    // resolver below for the full law).
    @Unique
    private static final Identifier GLACIAL_CAVES_BIOME_ID = Identifier.fromNamespaceAndPath("globe", "glacial_caves");

    @Unique
    private static final java.util.concurrent.atomic.AtomicBoolean GLACIAL_CAVES_MISSING_WARNED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Unique
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, GLOBE_SETTINGS_ID);

    @Unique
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_XSMALL_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, GLOBE_SETTINGS_XSMALL_ID);

    @Unique
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_SMALL_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, GLOBE_SETTINGS_SMALL_ID);

    @Unique
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_REGULAR_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, GLOBE_SETTINGS_REGULAR_ID);

    @Unique
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_LARGE_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, GLOBE_SETTINGS_LARGE_ID);

    @Unique
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_SETTINGS_MASSIVE_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, GLOBE_SETTINGS_MASSIVE_ID);

    // Thread-local so the Redirect (which cannot see outer args) can still access StructureAccessor safely.
    @Unique
    private static final ThreadLocal<StructureManager> globe$structureAccessorTL = new ThreadLocal<>();

    @Unique
    private static final Long2LongOpenHashMap DEBUG_WORLDGEN_CHUNKS = new Long2LongOpenHashMap();

    @Unique
    private static final Long2LongOpenHashMap DEBUG_PICK_FAIL_COLUMNS = new Long2LongOpenHashMap();

    static {
        DEBUG_WORLDGEN_CHUNKS.defaultReturnValue(Long.MIN_VALUE);
        DEBUG_PICK_FAIL_COLUMNS.defaultReturnValue(Long.MIN_VALUE);
    }

    @Shadow
    public abstract boolean stable(ResourceKey<NoiseGeneratorSettings> settings);

    @Unique
    private boolean globe$isAnyGlobeSettings() {
        return GlobeMod.shouldApplyLatitudeWorldgen((NoiseBasedChunkGenerator) (Object) this);
    }

    @Unique
    private int globe$borderRadiusBlocks() {
        return GlobeMod.borderRadiusForNoiseGenerator((NoiseBasedChunkGenerator) (Object) this);
    }

    @Unique
    private static void globe$logPopBio(String phase, String message) {
        if (!DEBUG_WORLDGEN_PATH) {
            return;
        }
        if (DEBUG_POPBIO_LOG_COUNT.getAndIncrement() >= 20) {
            return;
        }
        LOGGER.info("[LAT][POPBIO][{}] {}", phase, message);
    }

    // Capture StructureAccessor for the duration of the private populateBiomes call.
    @Inject(
            method = "doCreateBiomes(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At("HEAD")
    )
    private void globe$captureStructureAccessor(Blender blender, RandomState noiseConfig, StructureManager structureAccessor, ChunkAccess chunk, CallbackInfo ci) {
        globe$structureAccessorTL.set(structureAccessor);
        globe$noiseConfigTL.set(noiseConfig);
    }

    @Inject(
            method = "doCreateBiomes(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At("RETURN")
    )
    private void globe$clearStructureAccessor(Blender blender, RandomState noiseConfig, StructureManager structureAccessor, ChunkAccess chunk, CallbackInfo ci) {
        globe$structureAccessorTL.remove();
        globe$noiseConfigTL.remove();
    }

    /**
     * Wrap the BiomeSupplier used by vanilla chunk biome population.
     * NOTE: require=0 so the game DOES NOT crash if mixin remapping/refmap is broken.
     * If this Redirect doesn’t apply, Latitude won’t affect worldgen — but you’ll boot and can fix refmap next.
     */
    @Redirect(
            method = "doCreateBiomes(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"
            ),
            require = 0
    )
    private void globe$wrapBiomeSupplier(ChunkAccess chunk, BiomeResolver originalSupplier, Climate.Sampler sampler) {
        var pos = chunk.getPos();
        globe$logPopBio("ENTER", "chunk=" + pos.x() + "," + pos.z() + " settings=" + globe$matchedSettingsLabel());
        // Gate: only apply to your globe overworld settings.
        if (!this.globe$isAnyGlobeSettings()) {
            globe$logPopBio("FALLBACK", "settings=" + globe$matchedSettingsLabel() + " action=vanilla populateBiomes");
            if (DEBUG_WORLDGEN_PATH && DEBUG_POPULATE_GATE_REJECT_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("[Latitude] populateBiomes gate reject: settings not Globe preset checked={} matched={} action=falling back to vanilla populateBiomes",
                        GLOBE_SETTINGS_CHECKED, globe$matchedSettingsLabel());
            }
            chunk.fillBiomesFromNoise(originalSupplier, sampler);
            return;
        }

        StructureManager structureAccessor = globe$structureAccessorTL.get();
        if (structureAccessor == null) {
            globe$logPopBio("FALLBACK", "settings=" + globe$matchedSettingsLabel() + " action=vanilla populateBiomes reason=missing_structure_accessor");
            if (DEBUG_WORLDGEN_PATH && DEBUG_POPULATE_NO_STRUCTURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("[Latitude] populateBiomes gate reject: StructureAccessor unavailable settings={} action=falling back to vanilla populateBiomes",
                        globe$matchedSettingsLabel());
            }
            chunk.fillBiomesFromNoise(originalSupplier, sampler);
            return;
        }

        Registry<Biome> biomes = structureAccessor.registryAccess().lookupOrThrow(Registries.BIOME);
        LatitudeBiomes.rememberSourcePolicyBiomeRegistry(biomes);
        // B-9 P2 KEYSTONE: resolve the globe:glacial_caves holder once per chunk (two map gets at most).
        // Missing from the registry (broken datapack) degrades to no-swap with a one-time warn rather
        // than crashing worldgen -- the biome JSON schema test and the boot-time datapack parse gate own
        // that failure class (the exact carver-holder degrade precedent).
        Holder<Biome> glacialCavesResolved = null;
        if (LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
            glacialCavesResolved = biomes.get(GLACIAL_CAVES_BIOME_ID).orElse(null);
            if (glacialCavesResolved == null && GLACIAL_CAVES_MISSING_WARNED.compareAndSet(false, true)) {
                LOGGER.warn("[Latitude] B-9 glacial_caves biome missing from the biome registry (id={}) "
                        + "action=skipping the underground biome swap", GLACIAL_CAVES_BIOME_ID);
            }
        }
        final Holder<Biome> glacialCaves = glacialCavesResolved;
        int borderRadiusBlocks = this.globe$borderRadiusBlocks();
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator)(Object) this;
        RandomState noiseConfig = globe$noiseConfigTL.get();
        Long2LongOpenHashMap surfaceYCache = new Long2LongOpenHashMap();
        surfaceYCache.defaultReturnValue(Long.MIN_VALUE);
        // Per-column memoization for the surface-and-above biome pick (TEST 1 C3 lag fix). This chunk's
        // resolver runs on one thread, so these plain maps need no synchronization.
        Long2IntOpenHashMap columnDecisionYCache = new Long2IntOpenHashMap();
        columnDecisionYCache.defaultReturnValue(Integer.MIN_VALUE);
        Long2ObjectOpenHashMap<Holder<Biome>> columnPickCache = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<Holder<Biome>> columnPickBase = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<Holder<Biome>> columnBaseCache = new Long2ObjectOpenHashMap<>();
        // B-9 P2: per-column memoization of the glacial-caves band+fray+ocean decision (1 = swap, 0 = no)
        // -- one fray-noise sample per column instead of one per deep quart cell, same idiom as the
        // caches above (single-threaded per chunk, no synchronization).
        Long2IntOpenHashMap glacialColumnCache = new Long2IntOpenHashMap();
        glacialColumnCache.defaultReturnValue(Integer.MIN_VALUE);
        logWorldgenPathOnce(chunk, borderRadiusBlocks, globe$matchedSettingsLabel());
        BiomeResolver sourceSupplier = originalSupplier instanceof LatitudeBiomeSource latitudeSource
                ? latitudeSource.original()
                : originalSupplier;

        BiomeResolver wrapped = (x, y, z, ignoredSampler) -> {
            globe$logPopBio("LATITUDE_RESOLVER", "chunk=" + pos.x() + "," + pos.z() + " noise=" + x + "," + y + "," + z);
            // x/z are "noise biome coords" (4-block). Convert to block coords for your latitude math.
            int blockX = (x << 2) + 2;
            int blockZ = (z << 2) + 2;
            int blockY = (y << 2) + 2;
            long colKey = (((long) x) << 32) ^ (z & 0xFFFF_FFFFL);

            Holder<Biome> current = sourceSupplier.getNoiseBiome(x, y, z, sampler);
            // `base` is sampled at a FIXED classify-Y, so it is identical for every cell in this (x,z) column —
            // cache it instead of re-sampling the (Terralith) source ~96x per column (TEST 7 C3 lag follow-up).
            Holder<Biome> base = columnBaseCache.get(colKey);
            if (base == null) {
                base = sourceSupplier.getNoiseBiome(x, LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2, z, sampler);
                columnBaseCache.put(colKey, base);
            }
            boolean caveCurrent = isCaveBiome(biomes, current);

            if (blockY > HARD_DECK_SURFACE_Y && isCaveBiome(biomes, base)) {
                Holder<Biome> plains = biomes.get(Identifier.fromNamespaceAndPath("minecraft", "plains")).orElse(null);
                if (DEBUG_CAVE_DECK) {
                    LOGGER.info("[LAT_CAVE_DECK] replaced {} at blockY={} x={} z={}",
                            biomeId(biomes, base), blockY, blockX, blockZ);
                }
                if (plains != null) {
                    base = plains;
                }
            }

            int surfaceY = Integer.MIN_VALUE;
            boolean nearSurface = false;
            boolean tooHigh = false;
            boolean deepDarkIllegal = false;
            if (FIX_SURFACE_CAVE_BIOMES && caveCurrent) {
                surfaceY = resolveSurfaceY(generator, noiseConfig, chunk, blockX, blockZ, surfaceYCache);
                nearSurface = blockY >= (surfaceY - CAVE_SURFACE_MARGIN_BLOCKS);
                tooHigh = blockY > MAX_CAVE_BIOME_Y;
                deepDarkIllegal = isDeepDark(biomes, current) && blockY > -16;
                if (nearSurface || tooHigh || deepDarkIllegal) {
                    Holder<Biome> replacement = pickSurfaceReplacement(
                            biomes, base, blockX, blockZ, blockY, borderRadiusBlocks, sampler,
                            generator, noiseConfig, chunk);
                    if (DEBUG_CAVE_CLAMP) {
                        LOGGER.info("[Latitude] Clamped {} at x={} y={} z={} (surfaceY={} margin={} maxY={} deepDarkIllegal={}) -> {}",
                                biomeId(biomes, current), blockX, blockY, blockZ,
                                surfaceY, CAVE_SURFACE_MARGIN_BLOCKS, MAX_CAVE_BIOME_Y,
                                deepDarkIllegal, biomeId(biomes, replacement));
                    }
                    return replacement;
                }
            }

            // B-9 P2 KEYSTONE (Crew C, owner TEST 113): beneath BARRENS-BAND LAND columns, every quart
            // cell below the fixed LatitudeBiomes.GLACIAL_CAVES_CEILING_Y (48) line resolves to
            // globe:glacial_caves -- the underground claims the slot the S11(a) veto reserved for B-9.
            // Ordering is deliberate: AFTER the near-surface/too-high/deep-dark cave clamps (their
            // outputs are bitwise-unchanged) and BEFORE the deep-cave pass-through and every pick()
            // path, so deep dripstone/sulfur/lush cells and deep non-cave cells all take the glacial
            // identity while ALL surface/upper quarts (blockY >= 48) keep today's pick order exactly
            // (the surface-quart identity pin, enforced by isBelowGlacialCaveCeiling). The column
            // decision is the EXACT shared UNDERGROUND BLEND law the crevasse/tunnel carvers and the
            // /latdev locator ride (S28, Peetsa 2026-07-20 "a transition, not a hard switch":
            // LatitudeBiomes.glacialCaveColumnApplies -> glacialBlendColumnApplies, a wide 78-86 deg band on
            // ONE 640-block coherent region field -- swapped OFF the old 64-block surface barrens fray so the
            // underground glacial identity onsets equatorward of the surface barrens and blends in gradually;
            // populate runs PRE-NOISE, so no heightmaps/blocks); ocean-family columns (judged from the SAME
            // memoized per-column source sample `base` the resolver classifies everything with) are
            // excluded -- the sacred sea keeps its vanilla underground. deep_dark cells pass through
            // untouched (ancient cities/sculk are biome-tied; "underground stays alive" means
            // reskin-plus, never stripping a vanilla underground landmark). Flag-off (or missing
            // registry entry) is byte-identical: glacialCaves stays null and this block never runs.
            if (glacialCaves != null && LatitudeBiomes.isBelowGlacialCaveCeiling(blockY)) {
                int glacialDecision = glacialColumnCache.get(colKey);
                if (glacialDecision == Integer.MIN_VALUE) {
                    glacialDecision = LatitudeBiomes.glacialCaveColumnApplies(
                            blockX, blockZ, borderRadiusBlocks,
                            base.is(net.minecraft.tags.BiomeTags.IS_OCEAN)) ? 1 : 0;
                    glacialColumnCache.put(colKey, glacialDecision);
                }
                if (glacialDecision == 1 && !isDeepDark(biomes, current)) {
                    return glacialCaves;
                }
            }

            if (caveCurrent) {
                // S11(a) LUSH-CAVE VETO: a legitimate DEEP cave cell (past the near-surface/too-high clamps
                // above) resolving to lush_caves inside the polar Barrens band remaps to the COLUMN'S SURFACE
                // biome -- plain caves until B-9 Glacial Caves takes the slot (owner: lush reads tropical;
                // dripstone/deep_dark deliberately pass through). Band-gated on the core onset, not the fray
                // (PolarBarrensBand.vetoesLushCaveCell documents why); the remap reuses the SAME per-column
                // memoized surface pick the surface cells get, so the cave column inherits exactly the biome
                // above it (fray-aware: polar_barrens or snowy_plains).
                if (com.example.globe.core.PolarBarrensBand.vetoesLushCaveCell(
                        LatitudeV2Flags.POLAR_BARRENS_ENABLED,
                        biomeIdEquals(biomes, current, LUSH_CAVES_ID),
                        borderRadiusBlocks > 0 ? Math.abs((double) blockZ) * 90.0 / borderRadiusBlocks : Double.NaN)) {
                    int lushColDecisionY = columnDecisionYCache.get(colKey);
                    if (lushColDecisionY == Integer.MIN_VALUE) {
                        lushColDecisionY = LatitudeBiomes.surfaceDecisionY(generator, noiseConfig, chunk, blockX, blockZ);
                        columnDecisionYCache.put(colKey, lushColDecisionY);
                    }
                    Holder<Biome> cachedSurfacePick = columnPickCache.get(colKey);
                    if (cachedSurfacePick != null && columnPickBase.get(colKey) == base) {
                        return cachedSurfacePick;
                    }
                    Holder<Biome> surfacePick = globe$pickOrFallback(biomes, base, blockX, blockZ,
                            lushColDecisionY, borderRadiusBlocks, sampler, generator, noiseConfig, chunk);
                    columnPickCache.put(colKey, surfacePick);
                    columnPickBase.put(colKey, base);
                    return surfacePick;
                }
                return current;
            }

            // Per-column memoization of the biome pick (TEST 1 C3 lag fix). pick() depends on blockY ONLY
            // through its internal biomeY = (blockY < columnDecisionY-16 ? blockY : columnDecisionY), so every
            // cell at or above (columnDecisionY-16) selects the SAME biome for a given column+base. But this
            // resolver runs for ~96 Y-quarts per column and pick() re-runs its expensive terrain/noise probes
            // each time. Compute pick() once per column for those surface-and-above cells and reuse it; only
            // the rarer deep cells (below the surface band, where biomeY genuinely varies) fall through to an
            // exact per-cell pick, so behavior is preserved everywhere.
            int colDecisionY = columnDecisionYCache.get(colKey);
            if (colDecisionY == Integer.MIN_VALUE) {
                colDecisionY = LatitudeBiomes.surfaceDecisionY(generator, noiseConfig, chunk, blockX, blockZ);
                columnDecisionYCache.put(colKey, colDecisionY);
            }

            if (blockY >= colDecisionY - 16) {
                Holder<Biome> cachedPick = columnPickCache.get(colKey);
                if (cachedPick != null && columnPickBase.get(colKey) == base) {
                    return cachedPick;
                }
                Holder<Biome> picked = globe$pickOrFallback(biomes, base, blockX, blockZ, blockY, borderRadiusBlocks,
                        sampler, generator, noiseConfig, chunk);
                columnPickCache.put(colKey, picked);
                columnPickBase.put(colKey, base);
                return picked;
            }
            return globe$pickOrFallback(biomes, base, blockX, blockZ, blockY, borderRadiusBlocks,
                    sampler, generator, noiseConfig, chunk);
        };

        globe$logPopBio("ENTER", "installing Latitude resolver chunk=" + pos.x() + "," + pos.z() + " radius=" + borderRadiusBlocks);
        globe$populateBiomes(chunk, wrapped, sampler);
    }

    // The pick() call plus its exception/null fallback, shared by the cached (surface-and-above) and the
    // uncached (deep-cell) paths of the resolver. blockY is forwarded so pick() can clamp its internal
    // decision Y; horizontal biome selection stays unchanged.
    @Unique
    private static Holder<Biome> globe$pickOrFallback(Registry<Biome> biomes, Holder<Biome> base,
                                                      int blockX, int blockZ, int blockY, int borderRadiusBlocks,
                                                      Climate.Sampler sampler, NoiseBasedChunkGenerator generator,
                                                      RandomState noiseConfig, ChunkAccess chunk) {
        Holder<Biome> picked = null;
        try {
            picked = LatitudeBiomes.pick(biomes, base, blockX, blockZ, blockY, borderRadiusBlocks, sampler, "MIXIN",
                    generator, noiseConfig, chunk);
        } catch (Throwable t) {
            globe$logPopBio("ERROR", t.getClass().getSimpleName() + ": " + t.getMessage());
            logPickFailOnce(blockX, blockZ, "exception", t.toString());
            if (DEBUG_BIOME_PICK) {
                LOGGER.debug("[Latitude] Biome pick exception", t);
            }
        }
        if (picked == null) {
            logPickFailOnce(blockX, blockZ, "null", null);
            if (DEBUG_BIOME_PICK) {
                LOGGER.debug("[Latitude] Biome pick returned null at x={} z={}", blockX, blockZ);
            }
            return pickSafeFallback(biomes, blockZ);
        }
        return picked;
    }

    /** S11(a): exact-id test for one biome holder (the lush-cave veto must match ONLY lush_caves --
     *  dripstone/sulfur/deep_dark pass through). Same id-resolution order as {@link #isCaveBiome}. */
    @Unique
    private static boolean biomeIdEquals(Registry<Biome> biomes, Holder<Biome> entry, Identifier id) {
        Identifier actual = biomes.getKey(entry.value());
        if (actual == null) {
            actual = entry.unwrapKey().map(key -> key.identifier()).orElse(null);
        }
        return id.equals(actual);
    }

    @Unique
    private static boolean isCaveBiome(Registry<Biome> biomes, Holder<Biome> entry) {
        Identifier actual = biomes.getKey(entry.value());
        if (actual == null) {
            actual = entry.unwrapKey().map(key -> key.identifier()).orElse(null);
        }
        if (actual == null) {
            return false;
        }
        return actual.equals(LUSH_CAVES_ID)
                || actual.equals(DRIPSTONE_CAVES_ID)
                || actual.equals(SULFUR_CAVES_ID)
                || actual.equals(DEEP_DARK_ID);
    }

    @Unique
    private static Holder<Biome> pickSurfaceReplacement(Registry<Biome> biomes, Holder<Biome> base,
                                                                int blockX, int blockZ, int blockY, int borderRadiusBlocks,
                                                                Climate.Sampler sampler,
                                                                NoiseBasedChunkGenerator generator, RandomState noiseConfig, ChunkAccess heightView) {
        Holder<Biome> pick;
        try {
            pick = LatitudeBiomes.pick(biomes, base, blockX, blockZ, blockY, borderRadiusBlocks, sampler, "CAVE_CLAMP",
                    generator, noiseConfig, heightView);
        } catch (Throwable t) {
            pick = null;
            logPickFailOnce(blockX, blockZ, "clamp_exception", t.toString());
            if (DEBUG_BIOME_PICK) {
                LOGGER.debug("[Latitude] Clamp pick exception", t);
            }
        }
        if (pick == null) {
            logPickFailOnce(blockX, blockZ, "clamp_null", null);
            pick = pickSafeFallback(biomes, blockZ);
        }
        if (!isCaveBiome(biomes, pick)) {
            return pick;
        }
        if (base != null && !isCaveBiome(biomes, base)) {
            return base;
        }
        return pickSafeFallback(biomes, blockZ);
    }

    @Unique
    private static int resolveSurfaceY(NoiseBasedChunkGenerator generator, RandomState noiseConfig, ChunkAccess heightView,
                                       int blockX, int blockZ, Long2LongOpenHashMap surfaceYCache) {
        long key = (((long) blockX) << 32) ^ (blockZ & 0xFFFF_FFFFL);
        long cached = surfaceYCache.get(key);
        if (cached != Long.MIN_VALUE) {
            return (int) cached;
        }

        int surfaceY;
        if (generator == null || noiseConfig == null || heightView == null) {
            surfaceY = HARD_DECK_SURFACE_Y;
        } else {
            surfaceY = generator.getBaseHeight(blockX, blockZ, Heightmap.Types.WORLD_SURFACE_WG, heightView, noiseConfig);
        }
        // Phase 5 carve-aware ocean labels, Touch 2 (dripstone in trenches): WORLD_SURFACE_WG reads the
        // waterline (Y63) over a carved sea, so a trench-floor cave biome (~Y45) is judged "deep" and
        // never clamped -- the carve strips the covering rock and EXPOSES it. Judge "near surface"
        // against min(estimate, carveTarget) instead: the carve target IS the trench floor, so cave
        // exposures at/above it get clamped to the latitude-correct ocean biome like any other
        // near-surface cave exposure. Flag-off (or no active bias) is byte-identical: the oracle
        // returns +Infinity whenever no carve applies, which can never lower surfaceY.
        if (LatitudeV2Flags.TERRAIN_V2_CARVE_AWARE_LABELS && LatitudeBiomes.terrainBiasActivelyBiasing()) {
            double carveTargetY = GeoTerrainBiasFunction.carveTargetYOrMax(blockX, blockZ);
            if (carveTargetY < surfaceY) {
                surfaceY = (int) Math.floor(carveTargetY);
            }
        }
        surfaceYCache.put(key, surfaceY);
        return surfaceY;
    }

    @Unique
    private static void globe$populateBiomes(ChunkAccess chunk, BiomeResolver supplier, Climate.Sampler sampler) {
        int minQuartY = chunk.getMinY() >> 2;
        int heightQuarts = chunk.getHeight() >> 2;
        int startQuartX = chunk.getPos().x() << 2;
        int startQuartZ = chunk.getPos().z() << 2;
        for (int localX = 0; localX < 4; localX++) {
            int quartX = startQuartX + localX;
            for (int localZ = 0; localZ < 4; localZ++) {
                int quartZ = startQuartZ + localZ;
                for (int localY = 0; localY < heightQuarts; localY++) {
                    int quartY = minQuartY + localY;
                    if (localY < 0 || localY >= heightQuarts) {
                        if (DEBUG_CAVE_CLAMP) {
                            LOGGER.warn("[Latitude] Skipping out-of-range biome Y quartY={} minQuartY={} localY={} heightQuarts={}",
                                    quartY, minQuartY, localY, heightQuarts);
                        }
                        continue;
                    }
                    Holder<Biome> biome = supplier.getNoiseBiome(quartX, quartY, quartZ, sampler);
                    int sectionIndex = localY >> 2;
                    int sectionLocalY = localY & 3;
                    LevelChunkSection section = chunk.getSection(sectionIndex);
                    PalettedContainer<Holder<Biome>> container =
                            (PalettedContainer<Holder<Biome>>) section.getBiomes();
                    container.getAndSet(localX, sectionLocalY, localZ, biome);
                }
            }
        }
    }

    @Unique
    private static boolean isDeepDark(Registry<Biome> biomes, Holder<Biome> entry) {
        Identifier actual = biomes.getKey(entry.value());
        if (actual == null) {
            actual = entry.unwrapKey().map(key -> key.identifier()).orElse(null);
        }
        return DEEP_DARK_ID.equals(actual);
    }

    @Unique
    private static void logWorldgenPathOnce(ChunkAccess chunk, int borderRadiusBlocks, String settingsLabel) {
        if (!DEBUG_WORLDGEN_PATH) {
            return;
        }
        if (!DEBUG_WORLDGEN_PATH_ONCE_LOGGED.compareAndSet(false, true)) {
            return;
        }
        LOGGER.info("[Latitude] Worldgen path active: overriding populateBiomes settings={} checked={} chunk={} radius={} writing=true",
                settingsLabel, GLOBE_SETTINGS_CHECKED, chunk.getPos(), borderRadiusBlocks);
    }

    @Unique
    private String globe$matchedSettingsLabel() {
        if (this.stable(GLOBE_SETTINGS_KEY)) {
            return "overworld";
        }
        if (this.stable(GLOBE_SETTINGS_XSMALL_KEY)) {
            return "overworld_xsmall";
        }
        if (this.stable(GLOBE_SETTINGS_SMALL_KEY)) {
            return "overworld_small";
        }
        if (this.stable(GLOBE_SETTINGS_REGULAR_KEY)) {
            return "overworld_regular";
        }
        if (this.stable(GLOBE_SETTINGS_LARGE_KEY)) {
            return "overworld_large";
        }
        if (this.stable(GLOBE_SETTINGS_MASSIVE_KEY)) {
            return "overworld_massive";
        }
        if (GlobeMod.shouldApplyLatitudeWorldgen((NoiseBasedChunkGenerator) (Object) this)) {
            return "inline_globe";
        }
        return "unknown";
    }

    @Unique
    private static void logPickFailOnce(int blockX, int blockZ, String reason, String detail) {
        long key = (((long) blockX) << 32) ^ (blockZ & 0xFFFF_FFFFL);
        synchronized (DEBUG_PICK_FAIL_COLUMNS) {
            if (DEBUG_PICK_FAIL_COLUMNS.putIfAbsent(key, System.nanoTime()) != Long.MIN_VALUE) {
                return;
            }
        }
        if (detail != null) {
            LOGGER.warn("[LAT_PICK_FAIL] x={} z={} reason={} detail={}", blockX, blockZ, reason, detail);
        } else {
            LOGGER.warn("[LAT_PICK_FAIL] x={} z={} reason={}", blockX, blockZ, reason);
        }
    }

    @Unique
    private static Holder<Biome> pickLatitudeFallback(Registry<Biome> biomes, Holder<Biome> base,
                                                             int blockX, int blockZ, int borderRadiusBlocks) {
        int radius = Math.max(1, borderRadiusBlocks);
        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(Math.abs((double) blockZ) * 90.0 / radius);
        return switch (band) {
            case SUBPOLAR, POLAR -> pickFallback(biomes, base, "minecraft:snowy_plains", "minecraft:taiga", "minecraft:snowy_taiga");
            case TEMPERATE -> pickFallback(biomes, base, "minecraft:plains", "minecraft:forest", "minecraft:birch_forest");
            case SUBTROPICAL -> pickFallback(biomes, base, "minecraft:savanna", "minecraft:sparse_jungle", "minecraft:jungle");
            case TROPICAL -> pickFallback(biomes, base, "minecraft:jungle", "minecraft:savanna", "minecraft:plains");
        };
    }

    @Unique
    private static Holder<Biome> pickSafeFallback(Registry<Biome> biomes, int blockZ) {
        boolean farNorth = Math.abs(blockZ) > 8000;
        Identifier id = Identifier.fromNamespaceAndPath("minecraft", farNorth ? "snowy_plains" : "plains");
        Holder<Biome> entry = biomes.get(id).orElse(null);
        if (entry != null) {
            return entry;
        }
        return biomes.get(Identifier.fromNamespaceAndPath("minecraft", "plains")).orElse(null);
    }

    @Unique
    private static Holder<Biome> pickFallback(Registry<Biome> biomes, Holder<Biome> base, String... ids) {
        for (String id : ids) {
            Holder<Biome> entry = biomes.get(Identifier.parse(id)).orElse(null);
            if (entry != null) {
                return entry;
            }
        }
        return base != null ? base : biomes.get(Identifier.fromNamespaceAndPath("minecraft", "plains")).orElse(null);
    }

    @Unique
    private static boolean isBiomeId(Registry<Biome> biomes, Holder<Biome> entry, String id) {
        Identifier target = Identifier.parse(id);
        Identifier actual = biomes.getKey(entry.value());
        if (actual != null) {
            return actual.equals(target);
        }
        return entry.unwrapKey().map(key -> key.identifier().equals(target)).orElse(false);
    }

    @Unique
    private static String biomeId(Registry<Biome> biomes, Holder<Biome> entry) {
        Identifier actual = biomes.getKey(entry.value());
        if (actual != null) {
            return actual.toString();
        }
        return entry.unwrapKey().map(key -> key.identifier().toString()).orElse("?");
    }
}
