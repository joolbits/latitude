package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.BiomeDescriptorLedger;
import com.example.globe.world.LatitudeWorldgenScope;
import com.example.globe.world.feature.LatitudeRiparianBanks;
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
import net.minecraft.resources.ResourceLocation;
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
    @Unique
    private static final boolean LATITUDE_DEBUG_CUSTOM_RETAINALL_GATES =
            Boolean.getBoolean("latitude.debugCustomBiomeRetainAll")
                    || Boolean.getBoolean("latitude.debugBopRetainAll");
    @Unique
    private static final int LATITUDE_DEBUG_CUSTOM_RETAINALL_LOG_LIMIT =
            Integer.getInteger("latitude.debugCustomBiomeRetainAll.logLimit",
                    Integer.getInteger("latitude.debugBopRetainAll.logLimit", 20));
    @Unique
    private static final String LATITUDE_CUSTOM_RETAINALL_CLASSIFICATION =
            "LATITUDE_TAGGED_CUSTOM_FEATURES_RETAINALL_GUARD";
    @Unique
    private static final AtomicInteger LATITUDE_DEBUG_CUSTOM_RETAINALL_LOGS =
            new AtomicInteger();

    @Unique
    private static final AtomicBoolean LATITUDE_DEBUG_CUSTOM_INDEX_AUDIT_DONE =
            new AtomicBoolean();

    @Unique
    private static final AtomicBoolean LATITUDE_RIPARIAN_UNINDEXED_WARNED = new AtomicBoolean(false);

    @Unique
    private static final AtomicBoolean LATITUDE_CUSTOM_INDEX_FAILURE_WARNED =
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
    private volatile Set<ResourceLocation> globe$customBiomeRetainIds;

    /**
     * Lush desert riverbank features, in load order, or empty when they are switched off, missing,
     * or could not be proven present in the scoped index. Empty means "append nothing anywhere",
     * which is the only safe fallback: a placed feature offered to the decoration loop without an
     * index entry maps to -1 and would fault the feature lookup.
     */
    @Unique
    private volatile List<Holder<PlacedFeature>> globe$riparianBankFeatures = List.of();

    @Inject(
            method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            at = @At("HEAD")
    )
    private void globe$indexLatitudeTaggedCustomBiomeFeatures(
            WorldGenLevel world, ChunkAccess chunk, StructureManager structureAccessor, CallbackInfo ci) {
        if (!LatitudeWorldgenScope.isActive()) {
            return;
        }
        // Every chunk decorated from here on was decorated under the ledger-complete feature
        // index; the persistent marker is what tells the opt-in retrofit engine this chunk can
        // never need repair.
        com.example.globe.world.LatitudeDecorationRetrofit.markDecoratedUnderFixedIndex(chunk);
        if (this.globe$customBiomeFeaturesIndexed) {
            return;
        }
        synchronized (this) {
            if (this.globe$customBiomeFeaturesIndexed) {
                return;
            }
            boolean scopedIndexInstalled = false;
            try {
                Registry<Biome> biomeRegistry = world.registryAccess().registryOrThrow(Registries.BIOME);
                List<Holder<Biome>> policyBiomes = latitude$taggedCustomPolicyBiomes(biomeRegistry);
                this.globe$customBiomePolicyCount = policyBiomes.size();
                List<Holder<Biome>> expandedBiomes = new ArrayList<>(this.biomeSource.possibleBiomes());
                latitude$appendMissingPolicyBiomes(expandedBiomes, policyBiomes);
                // Resolved before the index is built, because the scoped-index supplier below reads
                // this field while appending the bank features to each arid biome's vegetal step.
                this.globe$riparianBankFeatures =
                        LatitudeRiparianBanks.resolvePlacedFeatures(world.registryAccess());
                List<FeatureSorter.StepFeatureData> expandedIndex = FeatureSorter.buildFeaturesPerStep(
                        expandedBiomes,
                        this::latitude$featuresForScopedIndex,
                        true
                );
                // Only the INDEX-PROOF failure belongs to this message. An empty list here means the
                // banks were switched off by flag or their placed feature was missing -- both of
                // which already spoke for themselves at resolve time -- and blaming the index for
                // those would send the next person reading this log hunting a mixin bug.
                if (!this.globe$riparianBankFeatures.isEmpty()
                        && !latitude$riparianFeaturesIndexed(expandedIndex)) {
                    // Switching the banks off here is correct -- an unindexed placed feature maps to
                    // -1 and would fault the lookup -- but doing it silently makes a dead feature
                    // indistinguishable from a feature that simply had nothing to plant. Say it once.
                    this.globe$riparianBankFeatures = List.of();
                    if (LATITUDE_RIPARIAN_UNINDEXED_WARNED.compareAndSet(false, true)) {
                        GlobeMod.LOGGER.warn(
                                "[LAT][RIPARIAN] bank features are not present in the scoped index; "
                                        + "desert banks stay bare for this world");
                    }
                }
                int[] expandedCounts = latitude$countIndexedFeatures(policyBiomes, expandedIndex);
                this.globe$customBiomeFeatureCount = expandedCounts[0];
                this.globe$customBiomeIndexedCount = expandedCounts[1];
                this.globe$customBiomeIndexSafe = expandedCounts[0] == expandedCounts[1];
                // Always install the scoped index: even when no custom biome expansion is needed,
                // this preserves the pre-1.5 feature ordering after frozen-river vegetation is
                // removed from Latitude only rather than from the global biome registry.
                this.featuresPerStep = () -> expandedIndex;
                scopedIndexInstalled = true;
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
                // The scoped index was not installed, so the vanilla index is still in force and
                // knows nothing about the bank features. Appending them now would be a fault.
                this.globe$riparianBankFeatures = List.of();
                if (LATITUDE_CUSTOM_INDEX_FAILURE_WARNED.compareAndSet(false, true)) {
                    GlobeMod.LOGGER.warn(
                            "[LAT][CUSTOM_RETAINALL] indexExpansion result=blocked exceptionType={} exception={} policyBiomes={} featureTotal={} featureInIndex={}",
                            e.getClass().getName(),
                            e.getMessage(),
                            this.globe$customBiomePolicyCount,
                            this.globe$customBiomeFeatureCount,
                            this.globe$customBiomeIndexedCount);
                }
            } finally {
                // An Error (OOM, stack overflow) escapes the catch above, so the reset there is not
                // enough. The banks may only stay armed if the scoped index that carries them was
                // actually installed -- otherwise the vanilla index is in force, knows nothing about
                // them, and every arid chunk would map them to -1 and fault decoration for the rest
                // of the session.
                if (!scopedIndexInstalled) {
                    this.globe$riparianBankFeatures = List.of();
                }
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
            // Same trap as FrozenRiverVegetationGuardMixin: 26.2's local names do not exist here.
            // 1.21.11 names these `holder` and `k`. The Holder is the only one in scope at this
            // call so it matches by type; the step index is the third of four ints live at the
            // HolderSet.stream() call (slot 17, the one indexing the per-step feature list).
            @Local Holder<Biome> biome,
            @Local(ordinal = 2) int stepIndex) {
        if (LatitudeWorldgenScope.isActive()
                && stepIndex == GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()) {
            if (biome.is(Biomes.FROZEN_RIVER)) {
                return Stream.empty();
            }
            // The decoration loop collects the indices to run from the biome's REAL generation
            // settings, not from the scoped index, so an arid biome has to contribute its bank
            // features here as well as in latitude$featuresForScopedIndex below.
            List<Holder<PlacedFeature>> riparian = latitude$riparianBankFeaturesFor(biome);
            if (!riparian.isEmpty()) {
                return Stream.concat(original.call(features), riparian.stream());
            }
        }
        return original.call(features);
    }

    @Unique
    private List<HolderSet<PlacedFeature>> latitude$featuresForScopedIndex(Holder<Biome> biome) {
        List<HolderSet<PlacedFeature>> features = this.generationSettingsGetter.apply(biome).features();
        int vegetalStep = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
        if (vegetalStep >= features.size()) {
            return features;
        }
        boolean frozenRiver = biome.is(Biomes.FROZEN_RIVER);
        List<Holder<PlacedFeature>> riparian = latitude$riparianBankFeaturesFor(biome);
        if (!frozenRiver && riparian.isEmpty()) {
            return features;
        }
        List<HolderSet<PlacedFeature>> filtered = new ArrayList<>(features);
        if (frozenRiver) {
            filtered.set(vegetalStep, HolderSet.empty());
            return filtered;
        }
        // Appended last, ground before plants, and in the same relative order for every arid
        // biome - that is what keeps the feature sorter's cross-biome ordering acyclic.
        List<Holder<PlacedFeature>> vegetal = new ArrayList<>();
        for (Holder<PlacedFeature> existing : features.get(vegetalStep)) {
            vegetal.add(existing);
        }
        for (Holder<PlacedFeature> bank : riparian) {
            // Identity, not equals: the feature index is an identity lookup, and PlacedFeature is a
            // record, so two distinct features with matching contents would compare equal and one
            // of them would silently never be appended.
            if (!latitude$containsFeatureValue(vegetal, bank)) {
                vegetal.add(bank);
            }
        }
        filtered.set(vegetalStep, HolderSet.direct(vegetal));
        return filtered;
    }

    @Unique
    private static boolean latitude$containsFeatureValue(
            List<Holder<PlacedFeature>> features, Holder<PlacedFeature> candidate) {
        for (Holder<PlacedFeature> existing : features) {
            if (existing == candidate || existing.value() == candidate.value()) {
                return true;
            }
        }
        return false;
    }

    /** The bank features this biome should carry, or empty for anything that is not arid land. */
    @Unique
    private List<Holder<PlacedFeature>> latitude$riparianBankFeaturesFor(Holder<Biome> biome) {
        List<Holder<PlacedFeature>> riparian = this.globe$riparianBankFeatures;
        if (riparian == null || riparian.isEmpty() || !LatitudeRiparianBanks.isAridLand(biome)) {
            return List.of();
        }
        return riparian;
    }

    /**
     * Proves every bank feature reached the rebuilt index. If no arid biome was in the expansion
     * the features were never indexed, and offering them to the decoration loop would map to -1.
     */
    @Unique
    private boolean latitude$riparianFeaturesIndexed(List<FeatureSorter.StepFeatureData> indexed) {
        List<Holder<PlacedFeature>> riparian = this.globe$riparianBankFeatures;
        if (riparian == null || riparian.isEmpty()) {
            return false;
        }
        int vegetalStep = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
        if (vegetalStep >= indexed.size()) {
            return false;
        }
        FeatureSorter.StepFeatureData stepData = indexed.get(vegetalStep);
        for (Holder<PlacedFeature> bank : riparian) {
            if (stepData.indexMapping().applyAsInt(bank.value()) < 0) {
                return false;
            }
        }
        return true;
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
        boolean debugRetainAll = LATITUDE_DEBUG_CUSTOM_RETAINALL_GATES;
        Set<ResourceLocation> retainIds = latitude$customBiomeRetainIds();
        int beforeSize = debugRetainAll ? biomes.size() : 0;
        // Preserving a selected custom biome needs this snapshot. Ordinary release runs do not
        // need the same traversal merely to assemble a debug report when preservation is inactive.
        List<Holder<Biome>> beforePolicyHolders =
                (this.globe$customBiomeIndexSafe || debugRetainAll)
                        ? latitude$policyCustomHoldersInSet(biomes, retainIds)
                        : List.of();

        boolean changed = biomes.retainAll(retainSet);
        int preservedCustom = 0;
        if (this.globe$customBiomeIndexSafe && !beforePolicyHolders.isEmpty()) {
            for (Holder<Biome> holder : beforePolicyHolders) {
                ResourceLocation id = latitude$biomeId(holder);
                if (id != null
                        && retainIds.contains(id)
                        && !latitude$hasBiomeId(biomes, id)
                        && latitude$addPolicyCustomBiome(biomes, holder)) {
                    preservedCustom++;
                    changed = true;
                }
            }
        }

        if (debugRetainAll) {
            boolean before = !beforePolicyHolders.isEmpty();
            boolean after = latitude$hasPolicyCustomBiome(biomes, retainIds);
            if (LATITUDE_DEBUG_CUSTOM_RETAINALL_LOGS.getAndIncrement() < LATITUDE_DEBUG_CUSTOM_RETAINALL_LOG_LIMIT
                    && (before || after)) {
                GlobeMod.LOGGER.info("[LAT][CUSTOM_RETAINALL] classification={} beforeSize={} afterSize={} beforePolicyCustom={} afterPolicyCustom={} retainAllChanged={} preservedCustom={} indexSafe={} policyCustomBiomes={} featureTotal={} featureInIndex={}",
                        LATITUDE_CUSTOM_RETAINALL_CLASSIFICATION, beforeSize, biomes.size(), before, after, changed,
                        preservedCustom, this.globe$customBiomeIndexSafe, this.globe$customBiomePolicyCount,
                        this.globe$customBiomeFeatureCount, this.globe$customBiomeIndexedCount);
            }
            if (before && LATITUDE_DEBUG_CUSTOM_INDEX_AUDIT_DONE.compareAndSet(false, true)) {
                latitude$auditCustomIndexedFeatures(beforePolicyHolders);
            }
        }

        return changed;
    }

    @Unique
    private Set<ResourceLocation> latitude$customBiomeRetainIds() {
        Set<ResourceLocation> retainIds = this.globe$customBiomeRetainIds;
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
        // Latitude could have placed here." See LatitudeDecorationRetrofit.allPaintableCustomBiomes.
        return com.example.globe.world.LatitudeDecorationRetrofit.allPaintableCustomBiomes(biomeRegistry);
    }

    @Unique
    private static void latitude$appendMissingPolicyBiomes(List<Holder<Biome>> expandedBiomes, Collection<Holder<Biome>> policyBiomes) {
        Set<ResourceLocation> seen = latitude$biomeIds(expandedBiomes);
        for (Holder<Biome> holder : policyBiomes) {
            ResourceLocation id = latitude$biomeId(holder);
            if (id != null && seen.add(id)) {
                expandedBiomes.add(holder);
            }
        }
    }

    @Unique
    private static Set<ResourceLocation> latitude$biomeIds(Collection<Holder<Biome>> holders) {
        Set<ResourceLocation> out = new HashSet<>();
        for (Holder<Biome> holder : holders) {
            ResourceLocation id = latitude$biomeId(holder);
            if (id != null) out.add(id);
        }
        return out;
    }

    @Unique
    private static ResourceLocation latitude$biomeId(Holder<?> holder) {
        return holder.unwrapKey().map(key -> key.location()).orElse(null);
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static List<Holder<Biome>> latitude$policyCustomHoldersInSet(Set<?> biomes, Set<ResourceLocation> policyIds) {
        List<Holder<Biome>> out = new ArrayList<>();
        if (policyIds.isEmpty()) {
            return out;
        }
        for (Object biome : biomes) {
            if (biome instanceof Holder<?> holder) {
                ResourceLocation id = latitude$biomeId(holder);
                if (id != null && policyIds.contains(id)) {
                    out.add((Holder<Biome>) holder);
                }
            }
        }
        return out;
    }

    @Unique
    private static boolean latitude$hasPolicyCustomBiome(Set<?> biomes, Set<ResourceLocation> policyIds) {
        return !latitude$policyCustomHoldersInSet(biomes, policyIds).isEmpty();
    }

    @Unique
    private static boolean latitude$hasBiomeId(Set<?> biomes, ResourceLocation target) {
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
