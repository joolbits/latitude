package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomes;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
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

    @Unique
    private static final Identifier DEEP_DARK_ID = Identifier.fromNamespaceAndPath("minecraft", "deep_dark");

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

    @Unique
    private static final int BORDER_RADIUS_XSMALL_BLOCKS = 3750;

    @Unique
    private static final int BORDER_RADIUS_SMALL_BLOCKS = 5000;

    @Unique
    private static final int BORDER_RADIUS_LARGE_BLOCKS = 10000;

    @Unique
    private static final int BORDER_RADIUS_MASSIVE_BLOCKS = 20000;

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
        return this.stable(GLOBE_SETTINGS_KEY)
                || this.stable(GLOBE_SETTINGS_XSMALL_KEY)
                || this.stable(GLOBE_SETTINGS_SMALL_KEY)
                || this.stable(GLOBE_SETTINGS_REGULAR_KEY)
                || this.stable(GLOBE_SETTINGS_LARGE_KEY)
                || this.stable(GLOBE_SETTINGS_MASSIVE_KEY);
    }

    @Unique
    private int globe$borderRadiusBlocks() {
        if (this.stable(GLOBE_SETTINGS_XSMALL_KEY)) {
            return BORDER_RADIUS_XSMALL_BLOCKS;
        }
        if (this.stable(GLOBE_SETTINGS_SMALL_KEY)) {
            return BORDER_RADIUS_SMALL_BLOCKS;
        }
        if (this.stable(GLOBE_SETTINGS_LARGE_KEY)) {
            return BORDER_RADIUS_LARGE_BLOCKS;
        }
        if (this.stable(GLOBE_SETTINGS_MASSIVE_KEY)) {
            return BORDER_RADIUS_MASSIVE_BLOCKS;
        }
        return GlobeMod.BORDER_RADIUS;
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
        int borderRadiusBlocks = this.globe$borderRadiusBlocks();
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator)(Object) this;
        RandomState noiseConfig = globe$noiseConfigTL.get();
        Long2LongOpenHashMap surfaceYCache = new Long2LongOpenHashMap();
        surfaceYCache.defaultReturnValue(Long.MIN_VALUE);
        logWorldgenPathOnce(chunk, borderRadiusBlocks, globe$matchedSettingsLabel());

        BiomeResolver wrapped = (x, y, z, ignoredSampler) -> {
            globe$logPopBio("LATITUDE_RESOLVER", "chunk=" + pos.x() + "," + pos.z() + " noise=" + x + "," + y + "," + z);
            // x/z are "noise biome coords" (4-block). Convert to block coords for your latitude math.
            int blockX = (x << 2) + 2;
            int blockZ = (z << 2) + 2;
            int blockY = (y << 2) + 2;
            
            Holder<Biome> current = originalSupplier.getNoiseBiome(x, y, z, sampler);
            Holder<Biome> base = originalSupplier.getNoiseBiome(x, LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2, z, sampler);
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
            if (caveCurrent) {
                return current;
            }

            Holder<Biome> picked = null;

            // BlockY is forwarded so LatitudeBiomes can compute the upland ramp while horizontal selection remains unchanged.
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
        };

        globe$logPopBio("ENTER", "installing Latitude resolver chunk=" + pos.x() + "," + pos.z() + " radius=" + borderRadiusBlocks);
        globe$populateBiomes(chunk, wrapped, sampler);
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
