package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
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
    private static final boolean LATITUDE_DEBUG_BOP_RETAINALL_GATES = Boolean.getBoolean("latitude.debugBopRetainAll");
    private static final int LATITUDE_DEBUG_BOP_RETAINALL_LOG_LIMIT = Integer.getInteger("latitude.debugBopRetainAll.logLimit", 20);
    private static final String LATITUDE_SEASONAL_FOREST_ID = "biomesoplenty:seasonal_forest";
    private static final ResourceKey<Biome> LATITUDE_SEASONAL_FOREST_KEY =
            ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("biomesoplenty", "seasonal_forest"));
    private static final String LATITUDE_BOP_RETAINALL_CLASSIFICATION =
            "BOP_FEATURES_NOT_SCHEDULED_RETAINALL_PRUNES_DYNAMIC_BOP_BIOME";

    @Unique
    private static final AtomicInteger LATITUDE_DEBUG_BOP_RETAINALL_LOGS =
            new AtomicInteger();

    @Unique
    private static final AtomicBoolean LATITUDE_DEBUG_BOP_INDEX_AUDIT_DONE =
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
    private final Object globe$bopSeasonalForestIndexLock = new Object();

    @Unique
    private volatile boolean globe$bopSeasonalForestFeaturesIndexed;

    @Unique
    private volatile boolean globe$bopSeasonalForestIndexSafe;

    @Unique
    private volatile int globe$bopSeasonalForestFeatureCount;

    @Unique
    private volatile int globe$bopSeasonalForestIndexedCount;

    @Inject(
            method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            at = @At("HEAD")
    )
    private void globe$indexBopSeasonalForestFeatures(
            WorldGenLevel world, ChunkAccess chunk, StructureManager structureAccessor, CallbackInfo ci) {
        if (this.globe$bopSeasonalForestFeaturesIndexed) {
            return;
        }
        synchronized (this.globe$bopSeasonalForestIndexLock) {
            if (this.globe$bopSeasonalForestFeaturesIndexed) {
                return;
            }
            try {
                Registry<Biome> biomeRegistry = world.registryAccess().lookupOrThrow(Registries.BIOME);
                Holder<Biome> seasonalForest = biomeRegistry.getOptional(LATITUDE_SEASONAL_FOREST_KEY)
                        .map(biomeRegistry::wrapAsHolder)
                        .orElse(null);
                if (seasonalForest == null) {
                    latitude$logIndexResult("absent", 0, 0, false, false);
                    return;
                }

                List<FeatureSorter.StepFeatureData> currentIndex = this.featuresPerStep.get();
                int[] currentCounts = latitude$countIndexedFeatures(seasonalForest, currentIndex);
                this.globe$bopSeasonalForestFeatureCount = currentCounts[0];
                this.globe$bopSeasonalForestIndexedCount = currentCounts[1];
                if (currentCounts[0] == currentCounts[1]) {
                    this.globe$bopSeasonalForestIndexSafe = true;
                    latitude$logIndexResult("already_safe", currentCounts[0], currentCounts[1], false, true);
                    return;
                }

                List<Holder<Biome>> expandedBiomes = new ArrayList<>(this.biomeSource.possibleBiomes());
                if (!latitude$containsSeasonalForest(expandedBiomes)) {
                    expandedBiomes.add(seasonalForest);
                }
                List<FeatureSorter.StepFeatureData> expandedIndex = FeatureSorter.buildFeaturesPerStep(
                        expandedBiomes,
                        biome -> this.generationSettingsGetter.apply(biome).features(),
                        true
                );
                int[] expandedCounts = latitude$countIndexedFeatures(seasonalForest, expandedIndex);
                this.globe$bopSeasonalForestFeatureCount = expandedCounts[0];
                this.globe$bopSeasonalForestIndexedCount = expandedCounts[1];
                this.globe$bopSeasonalForestIndexSafe = expandedCounts[0] == expandedCounts[1];
                if (this.globe$bopSeasonalForestIndexSafe) {
                    this.featuresPerStep = () -> expandedIndex;
                }
                latitude$logIndexResult("expanded_once", expandedCounts[0], expandedCounts[1], true, this.globe$bopSeasonalForestIndexSafe);
            } catch (Exception e) {
                this.globe$bopSeasonalForestIndexSafe = false;
                if (LATITUDE_DEBUG_BOP_RETAINALL_GATES) {
                    GlobeMod.LOGGER.warn("[LAT][BOP_RETAINALL] sfIndexExpansion result=blocked exception={}", e.getMessage());
                }
            } finally {
                this.globe$bopSeasonalForestFeaturesIndexed = true;
            }
        }
    }

    @Redirect(
            method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Set;retainAll(Ljava/util/Collection;)Z"
            )
    )
    private boolean globe$logRetainAll(Set<?> biomes, Collection<?> retainSet) {
        boolean before = latitude$hasSeasonalForest(biomes);
        int beforeSize = biomes.size();

        Holder<Biome> sfHolderPre = before ? latitude$getSeasonalForestHolder(biomes) : null;
        Holder<Biome> auditHolder = (sfHolderPre != null && !LATITUDE_DEBUG_BOP_INDEX_AUDIT_DONE.get())
                ? sfHolderPre : null;

        boolean changed = biomes.retainAll(retainSet);
        boolean preservedSeasonalForest = false;
        if (sfHolderPre != null && this.globe$bopSeasonalForestIndexSafe && !latitude$hasSeasonalForest(biomes)) {
            preservedSeasonalForest = latitude$addSeasonalForest(biomes, sfHolderPre);
            changed |= preservedSeasonalForest;
        }
        boolean after = latitude$hasSeasonalForest(biomes);

        if (LATITUDE_DEBUG_BOP_RETAINALL_GATES
                && LATITUDE_DEBUG_BOP_RETAINALL_LOGS.getAndIncrement() < LATITUDE_DEBUG_BOP_RETAINALL_LOG_LIMIT
                && (before || after)) {
            GlobeMod.LOGGER.info("[LAT][BOP_RETAINALL] classification={} beforeSize={} afterSize={} beforeSeasonalForest={} afterSeasonalForest={} retainAllChanged={} preservedSeasonalForest={} indexSafe={} sfTotal={} sfInIndex={}",
                    LATITUDE_BOP_RETAINALL_CLASSIFICATION, beforeSize, biomes.size(), before, after, changed,
                    preservedSeasonalForest, this.globe$bopSeasonalForestIndexSafe,
                    this.globe$bopSeasonalForestFeatureCount, this.globe$bopSeasonalForestIndexedCount);
        }

        if (LATITUDE_DEBUG_BOP_RETAINALL_GATES
                && auditHolder != null
                && LATITUDE_DEBUG_BOP_INDEX_AUDIT_DONE.compareAndSet(false, true)) {
            latitude$auditSfIndexedFeatures(auditHolder);
        }

        return changed;
    }

    @Unique
    private void latitude$logIndexResult(String result, int sfTotal, int sfInIndex, boolean expanded, boolean indexSafe) {
        if (!LATITUDE_DEBUG_BOP_RETAINALL_GATES) {
            return;
        }
        GlobeMod.LOGGER.info(
                "[LAT][BOP_RETAINALL] sfIndexExpansion result={} expanded={} sfTotal={} sfInIndex={} retainAloneSafe={} indexSafe={}",
                result, expanded, sfTotal, sfInIndex, sfTotal == sfInIndex, indexSafe
        );
    }

    // One-shot audit: reports how many of seasonal_forest's placed features appear in the
    // current featuresPerStep index. If sfInIndex < sfTotal, retaining seasonal_forest in
    // the biome set alone would cause list.get(-1) -> IndexOutOfBoundsException in
    // applyBiomeDecoration. Both fixes are required: retainAll bypass + featuresPerStep expansion.
    @Unique
    private void latitude$auditSfIndexedFeatures(Holder<Biome> sfHolder) {
        try {
            List<FeatureSorter.StepFeatureData> indexed = featuresPerStep.get();
            BiomeGenerationSettings settings = generationSettingsGetter.apply(sfHolder);
            List<HolderSet<PlacedFeature>> sfFeaturesByStep = settings.features();

            int sfTotal = 0;
            int sfInIndex = 0;
            for (int step = 0; step < sfFeaturesByStep.size(); step++) {
                for (Holder<PlacedFeature> pfHolder : sfFeaturesByStep.get(step)) {
                    sfTotal++;
                    if (step < indexed.size()) {
                        int idx = indexed.get(step).indexMapping().applyAsInt(pfHolder.value());
                        if (idx >= 0) sfInIndex++;
                    }
                }
            }

            GlobeMod.LOGGER.info(
                    "[LAT][BOP_RETAINALL] sfIndexAudit sfTotal={} sfInIndex={} sfSteps={} indexedSteps={}"
                    + " retainAloneSafe={}",
                    sfTotal, sfInIndex, sfFeaturesByStep.size(), indexed.size(),
                    sfInIndex == sfTotal
            );
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[LAT][BOP_RETAINALL] sfIndexAudit exception: {}", e.getMessage());
        }
    }

    @Unique
    private int[] latitude$countIndexedFeatures(Holder<Biome> sfHolder, List<FeatureSorter.StepFeatureData> indexed) {
        BiomeGenerationSettings settings = generationSettingsGetter.apply(sfHolder);
        List<HolderSet<PlacedFeature>> sfFeaturesByStep = settings.features();

        int sfTotal = 0;
        int sfInIndex = 0;
        for (int step = 0; step < sfFeaturesByStep.size(); step++) {
            for (Holder<PlacedFeature> pfHolder : sfFeaturesByStep.get(step)) {
                sfTotal++;
                if (step < indexed.size()) {
                    int idx = indexed.get(step).indexMapping().applyAsInt(pfHolder.value());
                    if (idx >= 0) sfInIndex++;
                }
            }
        }
        return new int[] {sfTotal, sfInIndex, sfFeaturesByStep.size(), indexed.size()};
    }

    @Unique
    private static boolean latitude$containsSeasonalForest(Collection<Holder<Biome>> biomes) {
        for (Holder<Biome> biome : biomes) {
            if (latitude$isSeasonalForest(biome)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static Holder<Biome> latitude$getSeasonalForestHolder(Set<?> biomes) {
        for (Object biome : biomes) {
            if (biome instanceof Holder<?> holder
                    && holder.unwrapKey().map(key -> LATITUDE_SEASONAL_FOREST_ID.equals(key.identifier().toString())).orElse(false)) {
                return (Holder<Biome>) holder;
            }
        }
        return null;
    }

    private static boolean latitude$hasSeasonalForest(Set<?> biomes) {
        for (Object biome : biomes) {
            if (biome instanceof Holder<?> holder && latitude$isSeasonalForest(holder)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean latitude$addSeasonalForest(Set<?> biomes, Holder<Biome> seasonalForest) {
        return ((Set) biomes).add(seasonalForest);
    }

    @Unique
    private static boolean latitude$isSeasonalForest(Holder<?> holder) {
        return holder.unwrapKey().map(key -> LATITUDE_SEASONAL_FOREST_ID.equals(key.identifier().toString())).orElse(false);
    }
}
