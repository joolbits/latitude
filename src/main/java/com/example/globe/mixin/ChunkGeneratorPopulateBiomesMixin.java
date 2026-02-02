package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSupplier;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunkGenerator.class)
public abstract class ChunkGeneratorPopulateBiomesMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("LatitudeBiomes");

    @Unique
    private static final boolean FIX_SURFACE_CAVE_BIOMES =
            Boolean.parseBoolean(System.getProperty("latitude.fixSurfaceCaveBiomes", "true"));

    @Unique
    private static final int CAVE_CLAMP_BUFFER =
            Integer.getInteger("latitude.caveClampBuffer", 12);

    @Unique
    private static final int MAX_CAVE_BIOME_Y =
            Integer.getInteger("latitude.maxCaveBiomeY", 96);

    @Unique
    private static final int CAVE_CLAMP_HARD_DECK_Y =
            Integer.getInteger("latitude.caveClampHardDeckY", 0);

    @Unique
    private static final boolean DEBUG_CAVE_CLAMP =
            Boolean.getBoolean("latitude.debugCaveClamp");

    @Unique
    private static final boolean DEBUG_WORLDGEN_PATH =
            Boolean.getBoolean("latitude.debugWorldgenPath");

    @Unique
    private static final boolean DEBUG_BIOME_PICK =
            Boolean.getBoolean("latitude.debugBiomePick");

    // Only apply Latitude to your globe overworld settings (keeps Nether/End sane).
    @Unique
    private static final Identifier GLOBE_SETTINGS_ID = Identifier.of("globe", "overworld");

    @Unique
    private static final Identifier GLOBE_SETTINGS_XSMALL_ID = Identifier.of("globe", "overworld_xsmall");

    @Unique
    private static final Identifier GLOBE_SETTINGS_SMALL_ID = Identifier.of("globe", "overworld_small");

    @Unique
    private static final Identifier GLOBE_SETTINGS_REGULAR_ID = Identifier.of("globe", "overworld_regular");

    @Unique
    private static final Identifier GLOBE_SETTINGS_LARGE_ID = Identifier.of("globe", "overworld_large");

    @Unique
    private static final Identifier GLOBE_SETTINGS_MASSIVE_ID = Identifier.of("globe", "overworld_massive");

    @Unique
    private static final Identifier LUSH_CAVES_ID = Identifier.of("minecraft", "lush_caves");

    @Unique
    private static final Identifier DRIPSTONE_CAVES_ID = Identifier.of("minecraft", "dripstone_caves");

    @Unique
    private static final Identifier DEEP_DARK_ID = Identifier.of("minecraft", "deep_dark");

    @Unique
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_KEY =
            RegistryKey.of(RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_ID);

    @Unique
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_XSMALL_KEY =
            RegistryKey.of(RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_XSMALL_ID);

    @Unique
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_SMALL_KEY =
            RegistryKey.of(RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_SMALL_ID);

    @Unique
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_REGULAR_KEY =
            RegistryKey.of(RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_REGULAR_ID);

    @Unique
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_LARGE_KEY =
            RegistryKey.of(RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_LARGE_ID);

    @Unique
    private static final RegistryKey<ChunkGeneratorSettings> GLOBE_SETTINGS_MASSIVE_KEY =
            RegistryKey.of(RegistryKeys.CHUNK_GENERATOR_SETTINGS, GLOBE_SETTINGS_MASSIVE_ID);

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
    private static final ThreadLocal<StructureAccessor> globe$structureAccessorTL = new ThreadLocal<>();

    @Unique
    private static final Long2LongOpenHashMap DEBUG_WORLDGEN_CHUNKS = new Long2LongOpenHashMap();

    @Unique
    private static final Long2LongOpenHashMap DEBUG_PICK_FAIL_COLUMNS = new Long2LongOpenHashMap();

    static {
        DEBUG_WORLDGEN_CHUNKS.defaultReturnValue(Long.MIN_VALUE);
        DEBUG_PICK_FAIL_COLUMNS.defaultReturnValue(Long.MIN_VALUE);
    }

    @Shadow
    public abstract boolean matchesSettings(RegistryKey<ChunkGeneratorSettings> settings);

    @Unique
    private boolean globe$isAnyGlobeSettings() {
        return this.matchesSettings(GLOBE_SETTINGS_KEY)
                || this.matchesSettings(GLOBE_SETTINGS_XSMALL_KEY)
                || this.matchesSettings(GLOBE_SETTINGS_SMALL_KEY)
                || this.matchesSettings(GLOBE_SETTINGS_REGULAR_KEY)
                || this.matchesSettings(GLOBE_SETTINGS_LARGE_KEY)
                || this.matchesSettings(GLOBE_SETTINGS_MASSIVE_KEY);
    }

    @Unique
    private int globe$borderRadiusBlocks() {
        if (this.matchesSettings(GLOBE_SETTINGS_XSMALL_KEY)) {
            return BORDER_RADIUS_XSMALL_BLOCKS;
        }
        if (this.matchesSettings(GLOBE_SETTINGS_SMALL_KEY)) {
            return BORDER_RADIUS_SMALL_BLOCKS;
        }
        if (this.matchesSettings(GLOBE_SETTINGS_LARGE_KEY)) {
            return BORDER_RADIUS_LARGE_BLOCKS;
        }
        if (this.matchesSettings(GLOBE_SETTINGS_MASSIVE_KEY)) {
            return BORDER_RADIUS_MASSIVE_BLOCKS;
        }
        return GlobeMod.BORDER_RADIUS;
    }

    // Capture StructureAccessor for the duration of the private populateBiomes call.
    @Inject(
            method = "populateBiomes(Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)V",
            at = @At("HEAD")
    )
    private void globe$captureStructureAccessor(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk, CallbackInfo ci) {
        globe$structureAccessorTL.set(structureAccessor);
    }

    @Inject(
            method = "populateBiomes(Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)V",
            at = @At("RETURN")
    )
    private void globe$clearStructureAccessor(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk, CallbackInfo ci) {
        globe$structureAccessorTL.remove();
    }

    /**
     * Wrap the BiomeSupplier used by vanilla chunk biome population.
     * NOTE: require=0 so the game DOES NOT crash if mixin remapping/refmap is broken.
     * If this Redirect doesn’t apply, Latitude won’t affect worldgen — but you’ll boot and can fix refmap next.
     */
    @Redirect(
            method = "populateBiomes(Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;populateBiomes(Lnet/minecraft/world/biome/source/BiomeSupplier;Lnet/minecraft/world/biome/source/util/MultiNoiseUtil$MultiNoiseSampler;)V"
            ),
            require = 0
    )
    private void globe$wrapBiomeSupplier(Chunk chunk, BiomeSupplier originalSupplier, MultiNoiseUtil.MultiNoiseSampler sampler) {
        System.out.println(">>> LATITUDE MIXIN ALIVE (REDIRECT) <<<");
        
        // Gate: only apply to your globe overworld settings.
        if (!this.globe$isAnyGlobeSettings()) {
            chunk.populateBiomes(originalSupplier, sampler);
            return;
        }

        StructureAccessor structureAccessor = globe$structureAccessorTL.get();
        if (structureAccessor == null) {
            chunk.populateBiomes(originalSupplier, sampler);
            return;
        }

        Registry<Biome> biomes = structureAccessor.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
        int borderRadiusBlocks = this.globe$borderRadiusBlocks();
        logWorldgenPathOnce(chunk, borderRadiusBlocks, globe$matchedSettingsLabel());

        BiomeSupplier wrapped = (x, y, z, ignoredSampler) -> {
            // x/z are "noise biome coords" (4-block). Convert to block coords for your latitude math.
            int blockX = (x << 2) + 2;
            int blockZ = (z << 2) + 2;
            int blockY = (y << 2) + 2;
            
            if (x == 0 && y == 0 && z == 0) {
                System.out.println(">>> LATITUDE SUPPLIER ALIVE (x=0,y=0,z=0) <<<");
            }

            RegistryEntry<Biome> current = originalSupplier.getBiome(x, y, z, sampler);
            RegistryEntry<Biome> base = originalSupplier.getBiome(x, 0, z, sampler);

            if (FIX_SURFACE_CAVE_BIOMES && isCaveBiome(biomes, current)) {
                boolean hardDeck = blockY >= CAVE_CLAMP_HARD_DECK_Y;
                boolean tooHigh = blockY > MAX_CAVE_BIOME_Y;
                boolean deepDarkIllegal = isDeepDark(biomes, current) && blockY > -16;
                if (hardDeck || tooHigh || deepDarkIllegal) {
                    RegistryEntry<Biome> replacement = pickSurfaceReplacement(
                            biomes, base, blockX, blockZ, borderRadiusBlocks, sampler);
                    if (DEBUG_CAVE_CLAMP) {
                        LOGGER.info("[Latitude] Clamped {} at x={} y={} z={} (hardDeck={} maxY={} deepDarkIllegal={}) -> {}",
                                biomeId(biomes, current), blockX, blockY, blockZ, CAVE_CLAMP_HARD_DECK_Y,
                                MAX_CAVE_BIOME_Y, deepDarkIllegal, biomeId(biomes, replacement));
                    }
                    return replacement;
                }
            }

            RegistryEntry<Biome> picked = null;

            // IMPORTANT: force Y=0. Passing quartY reintroduces warm_ocean-on-land + harsh seams/infinite plains
            try {
                picked = LatitudeBiomes.pick(biomes, base, blockX, blockZ, borderRadiusBlocks, sampler, "MIXIN");
            } catch (Throwable t) {
                if (DEBUG_BIOME_PICK) {
                    logPickFailOnce(blockX, blockZ, "exception", t.toString());
                }
            }
            if (picked == null) {
                if (DEBUG_BIOME_PICK) {
                    logPickFailOnce(blockX, blockZ, "null", null);
                }
                return pickLatitudeFallback(biomes, base, blockX, blockZ, borderRadiusBlocks);
            }
            if (isCaveBiome(biomes, picked)) {
                LOGGER.warn("[Latitude] Cave biome chosen id={} decisionY={} quartY={}",
                        biomeId(biomes, picked), blockY, y);
            }
            return picked;
        };

        chunk.populateBiomes(wrapped, sampler);
    }

    @Unique
    private static boolean isCaveBiome(Registry<Biome> biomes, RegistryEntry<Biome> entry) {
        Identifier actual = biomes.getId(entry.value());
        if (actual == null) {
            actual = entry.getKey().map(key -> key.getValue()).orElse(null);
        }
        if (actual == null) {
            return false;
        }
        return actual.equals(LUSH_CAVES_ID)
                || actual.equals(DRIPSTONE_CAVES_ID)
                || actual.equals(DEEP_DARK_ID);
    }

    @Unique
    private static RegistryEntry<Biome> pickSurfaceReplacement(Registry<Biome> biomes, RegistryEntry<Biome> base,
                                                               int blockX, int blockZ, int borderRadiusBlocks,
                                                               MultiNoiseUtil.MultiNoiseSampler sampler) {
        RegistryEntry<Biome> pick = LatitudeBiomes.pick(biomes, base, blockX, blockZ, borderRadiusBlocks, sampler, "CAVE_CLAMP");
        if (!isCaveBiome(biomes, pick)) {
            return pick;
        }
        if (base != null && !isCaveBiome(biomes, base)) {
            return base;
        }
        return pickLatitudeFallback(biomes, base, blockX, blockZ, borderRadiusBlocks);
    }

    @Unique
    private static boolean isDeepDark(Registry<Biome> biomes, RegistryEntry<Biome> entry) {
        Identifier actual = biomes.getId(entry.value());
        if (actual == null) {
            actual = entry.getKey().map(key -> key.getValue()).orElse(null);
        }
        return DEEP_DARK_ID.equals(actual);
    }

    @Unique
    private static void logWorldgenPathOnce(Chunk chunk, int borderRadiusBlocks, String settingsLabel) {
        if (!DEBUG_WORLDGEN_PATH) {
            return;
        }
        long key = chunk.getPos().toLong();
        synchronized (DEBUG_WORLDGEN_CHUNKS) {
            if (DEBUG_WORLDGEN_CHUNKS.putIfAbsent(key, System.nanoTime()) != Long.MIN_VALUE) {
                return;
            }
        }
        LOGGER.info("[Latitude] Worldgen path active settings={} chunk={} radius={} writing=true",
                settingsLabel, chunk.getPos(), borderRadiusBlocks);
    }

    @Unique
    private String globe$matchedSettingsLabel() {
        if (this.matchesSettings(GLOBE_SETTINGS_KEY)) {
            return "overworld";
        }
        if (this.matchesSettings(GLOBE_SETTINGS_XSMALL_KEY)) {
            return "overworld_xsmall";
        }
        if (this.matchesSettings(GLOBE_SETTINGS_SMALL_KEY)) {
            return "overworld_small";
        }
        if (this.matchesSettings(GLOBE_SETTINGS_REGULAR_KEY)) {
            return "overworld_regular";
        }
        if (this.matchesSettings(GLOBE_SETTINGS_LARGE_KEY)) {
            return "overworld_large";
        }
        if (this.matchesSettings(GLOBE_SETTINGS_MASSIVE_KEY)) {
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
    private static RegistryEntry<Biome> pickLatitudeFallback(Registry<Biome> biomes, RegistryEntry<Biome> base,
                                                             int blockX, int blockZ, int borderRadiusBlocks) {
        int radius = Math.max(1, borderRadiusBlocks);
        LatitudeMath.LatitudeZone zone = LatitudeMath.zoneForRadius(radius, blockZ);
        return switch (zone) {
            case SUBPOLAR, POLAR -> pickFallback(biomes, base, "minecraft:snowy_plains", "minecraft:taiga", "minecraft:snowy_taiga");
            case TEMPERATE -> pickFallback(biomes, base, "minecraft:plains", "minecraft:forest", "minecraft:birch_forest");
            case TROPICAL, SUBTROPICAL -> pickFallback(biomes, base, "minecraft:savanna", "minecraft:sparse_jungle", "minecraft:jungle");
            case EQUATOR -> pickFallback(biomes, base, "minecraft:jungle", "minecraft:savanna", "minecraft:plains");
        };
    }

    @Unique
    private static RegistryEntry<Biome> pickFallback(Registry<Biome> biomes, RegistryEntry<Biome> base, String... ids) {
        for (String id : ids) {
            RegistryEntry<Biome> entry = biomes.getEntry(Identifier.of(id)).orElse(null);
            if (entry != null) {
                return entry;
            }
        }
        return base != null ? base : biomes.getEntry(Identifier.of("minecraft", "plains")).orElse(null);
    }

    @Unique
    private static boolean isBiomeId(Registry<Biome> biomes, RegistryEntry<Biome> entry, String id) {
        Identifier target = Identifier.of(id);
        Identifier actual = biomes.getId(entry.value());
        if (actual != null) {
            return actual.equals(target);
        }
        return entry.getKey().map(key -> key.getValue().equals(target)).orElse(false);
    }

    @Unique
    private static String biomeId(Registry<Biome> biomes, RegistryEntry<Biome> entry) {
        Identifier actual = biomes.getId(entry.value());
        if (actual != null) {
            return actual.toString();
        }
        return entry.getKey().map(key -> key.getValue().toString()).orElse("?");
    }
}
