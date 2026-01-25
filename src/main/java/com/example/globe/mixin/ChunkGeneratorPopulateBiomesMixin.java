package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.worldgen.CoherenceSampler;
import com.example.globe.worldgen.LatitudeCoherenceRules;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunkGenerator.class)
public abstract class ChunkGeneratorPopulateBiomesMixin {

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
     private static final int BORDER_RADIUS_XSMALL_BLOCKS = 3750;

     @Unique
     private static final int BORDER_RADIUS_SMALL_BLOCKS = 5000;

    // Thread-local so the Redirect (which cannot see outer args) can still access StructureAccessor safely.
    @Unique
    private static final ThreadLocal<StructureAccessor> globe$structureAccessorTL = new ThreadLocal<>();

    @Shadow
    public abstract boolean matchesSettings(RegistryKey<ChunkGeneratorSettings> settings);

     @Unique
     private boolean globe$isAnyGlobeSettings() {
         return this.matchesSettings(GLOBE_SETTINGS_KEY)
                 || this.matchesSettings(GLOBE_SETTINGS_XSMALL_KEY)
                 || this.matchesSettings(GLOBE_SETTINGS_SMALL_KEY)
                 || this.matchesSettings(GLOBE_SETTINGS_REGULAR_KEY);
     }

     @Unique
     private int globe$borderRadiusBlocks() {
         if (this.matchesSettings(GLOBE_SETTINGS_XSMALL_KEY)) {
             return BORDER_RADIUS_XSMALL_BLOCKS;
         }
         if (this.matchesSettings(GLOBE_SETTINGS_SMALL_KEY)) {
             return BORDER_RADIUS_SMALL_BLOCKS;
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

        final RegistryEntry<Biome> plainsBase = biomeOrNull(biomes, "minecraft:plains");

        BiomeSupplier wrapped = (x, y, z, ignoredSampler) -> {
            // x/z are "noise biome coords" (4-block). Convert to block coords for your latitude math.
            int blockX = x << 2;
            int blockZ = z << 2;

            RegistryEntry<Biome> base = originalSupplier.getBiome(x, y, z, sampler);
            RegistryEntry<Biome> selected = LatitudeBiomes.pick(biomes, base, blockX, blockZ, borderRadiusBlocks);

            String selectedId = biomeId(selected);
            if (LatitudeCoherenceRules.ENABLE_ARID_RIVER_MASKING && LatitudeCoherenceRules.isRiver(selectedId) && plainsBase != null) {
                RegistryEntry<Biome> host = LatitudeBiomes.pick(biomes, plainsBase, blockX, blockZ, borderRadiusBlocks);
                String hostId = biomeId(host);
                String maskedId = LatitudeCoherenceRules.maskRiverInArid(selectedId, hostId);
                if (!maskedId.equals(selectedId)) {
                    selected = biomeOrFallback(biomes, maskedId, selected);
                    selectedId = maskedId;
                }
            }

            if (LatitudeCoherenceRules.ENABLE_BADLANDS_MIN_REGION && LatitudeCoherenceRules.isFeatureHeavyBadlands(selectedId)) {
                final int fy = y;
                CoherenceSampler.BiomeLookup lookup = (bx, bz) -> {
                    int nx = bx >> 2;
                    int nz = bz >> 2;
                    RegistryEntry<Biome> nb = originalSupplier.getBiome(nx, fy, nz, sampler);
                    RegistryEntry<Biome> nl = LatitudeBiomes.pick(biomes, nb, bx, bz, borderRadiusBlocks);
                    return biomeId(nl);
                };

                int matches = CoherenceSampler.countNeighborsMatching(
                        lookup,
                        blockX,
                        blockZ,
                        2,
                        LatitudeCoherenceRules::isBadlandsFamily);

                String gatedId = LatitudeCoherenceRules.gateBadlandsByMinRegion(selectedId, matches);
                if (!gatedId.equals(selectedId)) {
                    selected = biomeOrFallback(biomes, gatedId, selected);
                }
            }

            return selected;
        };

        chunk.populateBiomes(wrapped, sampler);
    }

    @Unique
    private static String biomeId(RegistryEntry<Biome> b) {
        if (b == null) return "";
        return b.getKey().map(key -> key.getValue().toString()).orElse("");
    }

    @Unique
    private static RegistryEntry<Biome> biomeOrFallback(Registry<Biome> biomes, String id, RegistryEntry<Biome> fallback) {
        try {
            return biomes.getEntry(Identifier.of(id)).map(e -> (RegistryEntry<Biome>) e).orElse(fallback);
        } catch (Throwable t) {
            return fallback;
        }
    }

    @Unique
    private static RegistryEntry<Biome> biomeOrNull(Registry<Biome> biomes, String id) {
        try {
            return biomes.getEntry(Identifier.of(id)).map(e -> (RegistryEntry<Biome>) e).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
