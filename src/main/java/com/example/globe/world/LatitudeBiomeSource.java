package com.example.globe.world;

import com.example.globe.mixin.BiomeSourceAccessor;
import com.example.globe.terrain.EvatorMirror;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.Collection;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

public final class LatitudeBiomeSource extends BiomeSource {
    private static final int MAX_CAVE_BIOME_Y = Integer.getInteger("latitude.maxCaveBiomeY", 96);
    private static final int HARD_DECK_SURFACE_Y = Integer.getInteger("latitude.hardDeckSurfaceY", 20);
    private static final int DEEP_DARK_MAX_Y = -16;
    private static final Identifier LUSH_CAVES_ID = Identifier.fromNamespaceAndPath("minecraft", "lush_caves");
    private static final Identifier DRIPSTONE_CAVES_ID = Identifier.fromNamespaceAndPath("minecraft", "dripstone_caves");
    // 26.2 "Chaos Cubed" cave biome — preserve underground like the others.
    private static final Identifier SULFUR_CAVES_ID = Identifier.fromNamespaceAndPath("minecraft", "sulfur_caves");
    private static final Identifier DEEP_DARK_ID = Identifier.fromNamespaceAndPath("minecraft", "deep_dark");

    private final BiomeSource original;
    private final Collection<Holder<Biome>> biomes;
    private final Registry<Biome> biomeRegistry;
    private final int borderRadiusBlocks;
    private final NoiseBasedChunkGenerator generator;
    private final RandomState noiseConfig;
    private final LevelHeightAccessor heightView;
    private final String callerContext;

    public LatitudeBiomeSource(BiomeSource original, Collection<Holder<Biome>> biomes, int borderRadiusBlocks) {
        this(original, biomes, null, borderRadiusBlocks, null, null, null, "SOURCE");
    }

    public static LatitudeBiomeSource forLocate(BiomeSource original,
                                                Registry<Biome> biomeRegistry,
                                                int borderRadiusBlocks,
                                                NoiseBasedChunkGenerator generator,
                                                RandomState noiseConfig,
                                                LevelHeightAccessor heightView) {
        return new LatitudeBiomeSource(original, original.possibleBiomes(), biomeRegistry,
                borderRadiusBlocks, generator, noiseConfig, heightView, "MIXIN");
    }

    private LatitudeBiomeSource(BiomeSource original,
                                Collection<Holder<Biome>> biomes,
                                Registry<Biome> biomeRegistry,
                                int borderRadiusBlocks,
                                NoiseBasedChunkGenerator generator,
                                RandomState noiseConfig,
                                LevelHeightAccessor heightView,
                                String callerContext) {
        this.original = original;
        this.biomes = biomes;
        this.biomeRegistry = biomeRegistry;
        this.borderRadiusBlocks = borderRadiusBlocks;
        this.generator = generator;
        this.noiseConfig = noiseConfig;
        this.heightView = heightView;
        this.callerContext = callerContext == null || callerContext.isBlank() ? "SOURCE" : callerContext;
    }

    public BiomeSource original() {
        return original;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BiomeSource> codec() {
        @SuppressWarnings("unchecked")
        MapCodec<BiomeSource> delegate = (MapCodec<BiomeSource>) ((BiomeSourceAccessor) original).globe$invokeCodec();
        return new MapCodec<>() {
            @Override
            public <T> RecordBuilder<T> encode(BiomeSource input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                BiomeSource toEncode = input instanceof LatitudeBiomeSource wrapper ? wrapper.original : input;
                return delegate.encode(toEncode, ops, prefix);
            }

            @Override
            public <T> DataResult<BiomeSource> decode(DynamicOps<T> ops, MapLike<T> input) {
                return delegate.decode(ops, input);
            }

            @Override
            public <T> java.util.stream.Stream<T> keys(DynamicOps<T> ops) {
                return delegate.keys(ops);
            }
        };
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return LatitudeBiomes.expandSourceCandidatePool(original.possibleBiomes()).stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        // Phase 5 B-6 (Teleport-Evator) P1 -- the biome half of the mirror. When this quart column is in the
        // EAST mirror band of a live-evator world, remap X to its canonical WEST reflection ONCE, here, at the
        // top of the sole biome chokepoint. Everything downstream (the vanilla climate sampler read below AND
        // every X-keyed noise inside pick -- province/variant/humidity/ValueNoise2D) then produces the west
        // band's biome automatically, giving column-identity with the reflected terrain. Z is untouched
        // (reflection is about X only). Gated + band-limited by EvatorMirror; a non-evator world reflects
        // nothing (sx == x) and is byte-identical. See EvatorMirror / EvatorReflectLeafFunction.
        int sx = EvatorMirror.reflectEastQuart(x) ? EvatorMirror.reflectQuartX(x) : x;
        Holder<Biome> current = original.getNoiseBiome(sx, y, z, sampler);
        Holder<Biome> base = original.getNoiseBiome(sx, LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2, z, sampler);
        int blockX = sx << 2;
        int blockZ = z << 2;
        int blockY = y << 2;
        if (shouldPreserveCave(current, base, blockY)) {
            return current;
        }
        if (biomeRegistry != null) {
            return LatitudeBiomes.pick(biomeRegistry, base, blockX, blockZ, blockY, borderRadiusBlocks, sampler,
                    callerContext, generator, noiseConfig, heightView);
        }
        Collection<Holder<Biome>> sourceCandidates = LatitudeBiomes.expandSourceCandidatePool(biomes);
        return LatitudeBiomes.pick(sourceCandidates, base, blockX, blockZ, blockY, borderRadiusBlocks, sampler,
                callerContext, generator, noiseConfig, heightView);
    }

    private static boolean shouldPreserveCave(Holder<Biome> current, Holder<Biome> surfaceBase, int blockY) {
        if (!isCaveBiome(current)) {
            return false;
        }
        if (blockY > MAX_CAVE_BIOME_Y) {
            return false;
        }
        if (isDeepDark(current) && blockY > DEEP_DARK_MAX_Y) {
            return false;
        }
        if (blockY > HARD_DECK_SURFACE_Y && isCaveBiome(surfaceBase)) {
            return false;
        }
        return true;
    }

    private static boolean isCaveBiome(Holder<Biome> entry) {
        Identifier id = biomeId(entry);
        if (id == null) {
            return false;
        }
        return id.equals(LUSH_CAVES_ID) || id.equals(DRIPSTONE_CAVES_ID) || id.equals(SULFUR_CAVES_ID) || id.equals(DEEP_DARK_ID);
    }

    private static boolean isDeepDark(Holder<Biome> entry) {
        Identifier id = biomeId(entry);
        return id != null && id.equals(DEEP_DARK_ID);
    }

    private static Identifier biomeId(Holder<Biome> entry) {
        if (entry == null) {
            return null;
        }
        return entry.unwrapKey().map(key -> key.identifier()).orElse(null);
    }
}
