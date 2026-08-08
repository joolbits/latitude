package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.BiomeDescriptorLedger;
import com.example.globe.world.LatitudeWorldgenScope;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorGenerateFeaturesBiomeSetMixin {
    private static final boolean LATITUDE_DEBUG_CUSTOM_RETAINALL_GATES =
            Boolean.getBoolean("latitude.debugCustomBiomeRetainAll")
                    || Boolean.getBoolean("latitude.debugBopRetainAll");
    private static final int LATITUDE_DEBUG_CUSTOM_RETAINALL_LOG_LIMIT =
            Integer.getInteger("latitude.debugCustomBiomeRetainAll.logLimit",
                    Integer.getInteger("latitude.debugBopRetainAll.logLimit", 20));
    private static final String LATITUDE_CUSTOM_RETAINALL_CLASSIFICATION =
            "LATITUDE_TAGGED_CUSTOM_FEATURES_RETAINALL_GUARD";
    @Unique
    private static final AtomicInteger LATITUDE_DEBUG_CUSTOM_RETAINALL_LOGS =
            new AtomicInteger();

    @Unique
    private static final AtomicBoolean LATITUDE_DEBUG_CUSTOM_INDEX_AUDIT_DONE =
            new AtomicBoolean();

    @Shadow
    @Final
    @Mutable
    private Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep;

    @Shadow
    @Final
    private Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter;

    @Shadow
    @Final
    private BiomeSource biomeSource;

    @Unique
    private volatile boolean globe$customBiomeFeaturesIndexed;

    @Unique
    private volatile boolean globe$customBiomeIndexSafe;

    @Unique
    private volatile int globe$customBiomePolicyCount;

    @Unique
    private volatile int globe$customBiomeFeatureCount;

    @Unique
    private volatile int globe$customBiomeIndexedCount;

    @Unique
    private volatile Set<Identifier> globe$customBiomeRetainIds;

    @Inject(
            method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            at = @At("HEAD")
    )
    private void globe$indexLatitudeTaggedCustomBiomeFeatures(
            WorldGenLevel world, ChunkAccess chunk, StructureManager structureAccessor, CallbackInfo ci) {
        if (!LatitudeWorldgenScope.isActive()) {
            return;
        }
        if (this.globe$customBiomeFeaturesIndexed) {
            return;
        }
        synchronized (this) {
            if (this.globe$customBiomeFeaturesIndexed) {
                return;
            }
            try {
                Registry<Biome> biomeRegistry = world.registryAccess().lookupOrThrow(Registries.BIOME);
                List<Holder<Biome>> policyBiomes = latitude$taggedCustomPolicyBiomes(biomeRegistry);
                this.globe$customBiomePolicyCount = policyBiomes.size();
                List<Holder<Biome>> expandedBiomes = new ArrayList<>(this.biomeSource.possibleBiomes());
                latitude$appendMissingPolicyBiomes(expandedBiomes, policyBiomes);
                List<FeatureSorter.StepFeatureData> expandedIndex = FeatureSorter.buildFeaturesPerStep(
                        expandedBiomes,
                        this::latitude$featuresForScopedIndex,
                        true
                );
                int[] expandedCounts = latitude$countIndexedFeatures(policyBiomes, expandedIndex);
                this.globe$customBiomeFeatureCount = expandedCounts[0];
                this.globe$customBiomeIndexedCount = expandedCounts[1];
                this.globe$customBiomeIndexSafe = expandedCounts[0] == expandedCounts[1];
                // Always install the scoped index: even when no custom biome expansion is needed,
                // this preserves the pre-1.5 feature ordering after frozen-river vegetation is
                // removed from Latitude only rather than from the global biome registry.
                this.featuresPerStep = () -> expandedIndex;
                if (this.globe$customBiomeIndexSafe) {
                    this.globe$customBiomeRetainIds = latitude$biomeIds(policyBiomes);
                } else {
                    this.globe$customBiomeRetainIds = Set.of();
                }
                latitude$logIndexResult(
                        "scoped_index_rebuilt",
                        policyBiomes.size(),
                        expandedCounts[0],
                        expandedCounts[1],
                        true,
                        this.globe$customBiomeIndexSafe);
            } catch (Exception e) {
                this.globe$customBiomeIndexSafe = false;
                this.globe$customBiomeRetainIds = Set.of();
                if (LATITUDE_DEBUG_CUSTOM_RETAINALL_GATES) {
                    GlobeMod.LOGGER.warn("[LAT][CUSTOM_RETAINALL] indexExpansion result=blocked exception={}", e.getMessage());
                }
            } finally {
                this.globe$customBiomeFeaturesIndexed = true;
            }
        }
    }

    @WrapOperation(
            method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/HolderSet;stream()Ljava/util/stream/Stream;"))
    private Stream<Holder<PlacedFeature>> globe$omitFrozenRiverVegetationContribution(
            HolderSet<PlacedFeature> features,
            Operation<Stream<Holder<PlacedFeature>>> original,
            @Local(name = "biome") Holder<Biome> biome,
            @Local(name = "stepIndex") int stepIndex) {
        if (LatitudeWorldgenScope.isActive()
                && biome.is(Biomes.FROZEN_RIVER)
                && stepIndex == GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()) {
            return Stream.empty();
        }
        return original.call(features);
    }

    @Unique
    private List<HolderSet<PlacedFeature>> latitude$featuresForScopedIndex(Holder<Biome> biome) {
        List<HolderSet<PlacedFeature>> features = this.generationSettingsGetter.apply(biome).features();
        int vegetalStep = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
        if (!biome.is(Biomes.FROZEN_RIVER) || vegetalStep >= features.size()) {
            return features;
        }
        List<HolderSet<PlacedFeature>> filtered = new ArrayList<>(features);
        filtered.set(vegetalStep, HolderSet.empty());
        return filtered;
    }

    @Redirect(
            method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Set;retainAll(Ljava/util/Collection;)Z"
            )
    )
    private boolean globe$logRetainAll(Set<?> biomes, Collection<?> retainSet) {
        if (!LatitudeWorldgenScope.isActive()) {
            return biomes.retainAll(retainSet);
        }
        Set<Identifier> retainIds = latitude$customBiomeRetainIds();
        List<Holder<Biome>> beforePolicyHolders = latitude$policyCustomHoldersInSet(biomes, retainIds);
        boolean before = !beforePolicyHolders.isEmpty();
        int beforeSize = biomes.size();

        List<Holder<Biome>> auditHolders = (before && !LATITUDE_DEBUG_CUSTOM_INDEX_AUDIT_DONE.get())
                ? beforePolicyHolders : List.of();

        boolean changed = biomes.retainAll(retainSet);
        int preservedCustom = 0;
        if (this.globe$customBiomeIndexSafe && !beforePolicyHolders.isEmpty()) {
            for (Holder<Biome> holder : beforePolicyHolders) {
                Identifier id = latitude$biomeId(holder);
                if (id != null
                        && retainIds.contains(id)
                        && !latitude$hasBiomeId(biomes, id)
                        && latitude$addPolicyCustomBiome(biomes, holder)) {
                    preservedCustom++;
                    changed = true;
                }
            }
        }
        boolean after = latitude$hasPolicyCustomBiome(biomes, retainIds);

        if (LATITUDE_DEBUG_CUSTOM_RETAINALL_GATES
                && LATITUDE_DEBUG_CUSTOM_RETAINALL_LOGS.getAndIncrement() < LATITUDE_DEBUG_CUSTOM_RETAINALL_LOG_LIMIT
                && (before || after)) {
            GlobeMod.LOGGER.info("[LAT][CUSTOM_RETAINALL] classification={} beforeSize={} afterSize={} beforePolicyCustom={} afterPolicyCustom={} retainAllChanged={} preservedCustom={} indexSafe={} policyCustomBiomes={} featureTotal={} featureInIndex={}",
                    LATITUDE_CUSTOM_RETAINALL_CLASSIFICATION, beforeSize, biomes.size(), before, after, changed,
                    preservedCustom, this.globe$customBiomeIndexSafe, this.globe$customBiomePolicyCount,
                    this.globe$customBiomeFeatureCount, this.globe$customBiomeIndexedCount);
        }

        if (LATITUDE_DEBUG_CUSTOM_RETAINALL_GATES
                && !auditHolders.isEmpty()
                && LATITUDE_DEBUG_CUSTOM_INDEX_AUDIT_DONE.compareAndSet(false, true)) {
            latitude$auditCustomIndexedFeatures(auditHolders);
        }

        return changed;
    }

    @Unique
    private Set<Identifier> latitude$customBiomeRetainIds() {
        Set<Identifier> retainIds = this.globe$customBiomeRetainIds;
        return retainIds != null ? retainIds : Set.of();
    }

    @Unique
    private void latitude$logIndexResult(String result, int policyCount, int featureTotal, int featureInIndex, boolean expanded, boolean indexSafe) {
        if (!LATITUDE_DEBUG_CUSTOM_RETAINALL_GATES) {
            return;
        }
        GlobeMod.LOGGER.info(
                "[LAT][CUSTOM_RETAINALL] indexExpansion result={} expanded={} policyCustomBiomes={} featureTotal={} featureInIndex={} retainSafe={} indexSafe={}",
                result, expanded, policyCount, featureTotal, featureInIndex, featureTotal == featureInIndex, indexSafe
        );
    }

    @Unique
    private void latitude$auditCustomIndexedFeatures(Collection<Holder<Biome>> holders) {
        try {
            List<FeatureSorter.StepFeatureData> indexed = featuresPerStep.get();
            int[] counts = latitude$countIndexedFeatures(holders, indexed);

            GlobeMod.LOGGER.info(
                    "[LAT][CUSTOM_RETAINALL] indexAudit policyCustomBiomes={} featureTotal={} featureInIndex={} safeBiomes={} indexedSteps={} retainSafe={}",
                    holders.size(), counts[0], counts[1], counts[3], indexed.size(), counts[0] == counts[1]
            );
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[LAT][CUSTOM_RETAINALL] indexAudit exception={}", e.getMessage());
        }
    }

    @Unique
    private int[] latitude$countIndexedFeatures(Collection<Holder<Biome>> holders, List<FeatureSorter.StepFeatureData> indexed) {
        int total = 0;
        int inIndex = 0;
        int safeBiomes = 0;
        for (Holder<Biome> holder : holders) {
            int[] counts = latitude$countIndexedFeatures(holder, indexed);
            total += counts[0];
            inIndex += counts[1];
            if (counts[0] == counts[1]) {
                safeBiomes++;
            }
        }
        return new int[] {total, inIndex, holders.size(), safeBiomes};
    }

    @Unique
    private int[] latitude$countIndexedFeatures(Holder<Biome> holder, List<FeatureSorter.StepFeatureData> indexed) {
        BiomeGenerationSettings settings = generationSettingsGetter.apply(holder);
        List<HolderSet<PlacedFeature>> featuresByStep = settings.features();

        int total = 0;
        int inIndex = 0;
        for (int step = 0; step < featuresByStep.size(); step++) {
            for (Holder<PlacedFeature> pfHolder : featuresByStep.get(step)) {
                total++;
                if (step < indexed.size()) {
                    int idx = indexed.get(step).indexMapping().applyAsInt(pfHolder.value());
                    if (idx >= 0) inIndex++;
                }
            }
        }
        return new int[] {total, inIndex, featuresByStep.size(), indexed.size()};
    }

    @Unique
    private static List<Holder<Biome>> latitude$taggedCustomPolicyBiomes(Registry<Biome> biomeRegistry) {
        // Shared with LatitudeBiomeSource's /locate biome candidate pool — both need "everything
        // Latitude could have placed here." See LatitudePaintableCustomBiomes.
        return com.example.globe.world.LatitudePaintableCustomBiomes.allPaintableCustomBiomes(biomeRegistry);
    }

    @Unique
    private static void latitude$appendMissingPolicyBiomes(List<Holder<Biome>> expandedBiomes, Collection<Holder<Biome>> policyBiomes) {
        Set<Identifier> seen = latitude$biomeIds(expandedBiomes);
        for (Holder<Biome> holder : policyBiomes) {
            Identifier id = latitude$biomeId(holder);
            if (id != null && seen.add(id)) {
                expandedBiomes.add(holder);
            }
        }
    }

    @Unique
    private static Set<Identifier> latitude$biomeIds(Collection<Holder<Biome>> holders) {
        Set<Identifier> out = new HashSet<>();
        for (Holder<Biome> holder : holders) {
            Identifier id = latitude$biomeId(holder);
            if (id != null) out.add(id);
        }
        return out;
    }

    @Unique
    private static Identifier latitude$biomeId(Holder<?> holder) {
        return holder.unwrapKey().map(key -> key.identifier()).orElse(null);
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static List<Holder<Biome>> latitude$policyCustomHoldersInSet(Set<?> biomes, Set<Identifier> policyIds) {
        List<Holder<Biome>> out = new ArrayList<>();
        if (policyIds.isEmpty()) {
            return out;
        }
        for (Object biome : biomes) {
            if (biome instanceof Holder<?> holder) {
                Identifier id = latitude$biomeId(holder);
                if (id != null && policyIds.contains(id)) {
                    out.add((Holder<Biome>) holder);
                }
            }
        }
        return out;
    }

    @Unique
    private static boolean latitude$hasPolicyCustomBiome(Set<?> biomes, Set<Identifier> policyIds) {
        return !latitude$policyCustomHoldersInSet(biomes, policyIds).isEmpty();
    }

    @Unique
    private static boolean latitude$hasBiomeId(Set<?> biomes, Identifier target) {
        for (Object biome : biomes) {
            if (biome instanceof Holder<?> holder && target.equals(latitude$biomeId(holder))) {
                return true;
            }
        }
        return false;
    }

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean latitude$addPolicyCustomBiome(Set<?> biomes, Holder<Biome> holder) {
        return ((Set) biomes).add(holder);
    }
}
