package com.example.globe.dev;

import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeBiomes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class BiomeSamplerTools {
    private static final int ATLAS_HEARTBEAT_ROWS = 32;
    private static final long DEFAULT_INVENTORY_BATCH_MS = 25L;

    private BiomeSamplerTools() {
    }

    public static SamplerTemplate createTemplate(ServerLevel world) {
        ChunkGenerator generator = world.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            throw new IllegalStateException("Sampler search requires a NoiseChunkGenerator");
        }

        BiomeSource biomeSource = generator.getBiomeSource();
        BiomeSource baseSource = biomeSource instanceof LatitudeBiomeSource latitudeSource
                ? latitudeSource.original()
                : biomeSource;
        Registry<Biome> biomeRegistry = world.registryAccess().lookupOrThrow(Registries.BIOME);
        HolderGetter<NormalNoise.NoiseParameters> noiseParameters =
                world.registryAccess().lookupOrThrow(Registries.NOISE);

        return new SamplerTemplate(
                biomeRegistry,
                baseSource,
                noiseGenerator.generatorSettings(),
                noiseParameters,
                world.getSeed());
    }

    public static InventoryReport discoverInventory(ServerLevel world,
                                                    int radiusBlocks,
                                                    int stepBlocks,
                                                    int y) {
        SamplerTemplate template = createTemplate(world);
        return discoverInventory(template, world.getSeed(), radiusBlocks, stepBlocks, y);
    }

    public static InventoryReport discoverInventory(SamplerTemplate template,
                                                    long seed,
                                                    int radiusBlocks,
                                                    int stepBlocks,
                                                    int y) {
        InventoryScanProcessor processor = createInventoryScanProcessor(template, seed, radiusBlocks, stepBlocks, y);
        return processor.processAll();
    }

    public static InventoryScanProcessor createInventoryScanProcessor(SamplerTemplate template,
                                                                      long seed,
                                                                      int radiusBlocks,
                                                                      int stepBlocks,
                                                                      int y) {
        return new InventoryScanProcessor(template, seed, radiusBlocks, Math.max(1, stepBlocks), y);
    }

    public static SearchReport searchSeeds(ServerLevel world,
                                           SearchOptions options,
                                           String branch,
                                           String commit,
                                           Instant generatedAt) {
        SamplerTemplate template = createTemplate(world);
        int radiusBlocks = Math.max(1, options.radiusBlocks());
        int stepBlocks = Math.max(1, options.stepBlocks());
        int y = options.y();
        Set<String> targets = normalizeTargets(options.targetBiomes());
        List<SearchMatch> matches = new ArrayList<>();

        long originalSeed = template.templateSeed();
        try {
            for (int offset = 0; offset < options.seedCount() && matches.size() < options.maxResults(); offset++) {
                long seed = options.seedStart() + offset;
                RandomState noiseConfig = RandomState.create(template.settings().value(), template.noiseParameters(), seed);
                Climate.Sampler sampler = noiseConfig.sampler();
                Map<String, SearchHitAccumulator> hits = new LinkedHashMap<>();

                scanGrid(template, seed, radiusBlocks, stepBlocks, y, (blockX, blockZ, biomeId) -> {
                    if (!targets.contains(biomeId)) {
                        return;
                    }
                    SearchHitAccumulator acc = hits.get(biomeId);
                    if (acc == null) {
                        acc = new SearchHitAccumulator(
                                biomeId,
                                biomeDisplayName(biomeId),
                                blockX,
                                blockZ,
                                latitudeLabel(radiusBlocks, blockZ),
                                0);
                        hits.put(biomeId, acc);
                    }
                    acc.hitCount++;
                }, sampler);

                boolean matched = options.requireAll()
                        ? hits.keySet().containsAll(targets)
                        : !hits.isEmpty();
                if (!matched) {
                    continue;
                }

                List<SearchHit> hitList = hits.values().stream()
                        .sorted(Comparator.comparing(SearchHitAccumulator::biomeId))
                        .map(hit -> new SearchHit(
                                hit.biomeId,
                                hit.biomeName,
                                hit.firstHitX,
                                hit.firstHitZ,
                                hit.latitudeLabel,
                                hit.hitCount))
                        .toList();
                List<String> matchedBiomes = hitList.stream().map(SearchHit::biomeId).toList();
                matches.add(new SearchMatch(
                        seed,
                        matchedBiomes,
                        hitList,
                        radiusBlocks,
                        stepBlocks,
                        branch,
                        commit,
                        DateTimeFormatter.ISO_INSTANT.format(generatedAt)));
            }
        } finally {
            LatitudeBiomes.setWorldSeed(originalSeed);
        }

        return new SearchReport(
                DateTimeFormatter.ISO_INSTANT.format(generatedAt),
                branch,
                commit,
                options.seedStart(),
                options.seedCount(),
                radiusBlocks,
                stepBlocks,
                y,
                new ArrayList<>(targets),
                options.requireAll(),
                options.maxResults(),
                matches);
    }

    public static BandAuditReport bandAudit(ServerLevel world,
                                              long seed,
                                              int radiusBlocks,
                                              int stepBlocks,
                                              int y,
                                              double minDeg,
                                              double maxDeg,
                                              Set<String> watchedBiomes,
                                              Set<String> controlBiomes) {
        SamplerTemplate template = createTemplate(world);
        RandomState noiseConfig = RandomState.create(template.settings().value(), template.noiseParameters(), seed);
        Climate.Sampler sampler = noiseConfig.sampler();

        Set<String> allTracked = new LinkedHashSet<>();
        allTracked.addAll(watchedBiomes);
        allTracked.addAll(controlBiomes);

        Map<String, BandAuditAccumulator> accumulators = new LinkedHashMap<>();
        int[] totalInWindow = {0};

        long originalSeed = template.templateSeed();
        try {
            scanGrid(template, seed, radiusBlocks, Math.max(1, stepBlocks), y, (blockX, blockZ, biomeId) -> {
                double latDeg = radiusBlocks <= 0 ? 0.0 : (Math.abs(blockZ) * 90.0) / (double) radiusBlocks;
                if (latDeg < minDeg || latDeg > maxDeg) {
                    return;
                }
                totalInWindow[0]++;
                if (!allTracked.contains(biomeId)) {
                    return;
                }
                BandAuditAccumulator acc = accumulators.get(biomeId);
                if (acc == null) {
                    acc = new BandAuditAccumulator(biomeId, blockX, blockZ, latDeg);
                    accumulators.put(biomeId, acc);
                }
                acc.hitCount++;
            }, sampler);
        } finally {
            LatitudeBiomes.setWorldSeed(originalSeed);
        }

        List<BandAuditHit> hits = new ArrayList<>();
        for (String biomeId : allTracked) {
            BandAuditAccumulator acc = accumulators.get(biomeId);
            if (acc != null) {
                double pct = totalInWindow[0] > 0 ? (acc.hitCount * 100.0) / totalInWindow[0] : 0.0;
                hits.add(new BandAuditHit(biomeId, acc.hitCount, pct, acc.firstX, acc.firstZ, acc.firstLatDeg,
                        watchedBiomes.contains(biomeId)));
            } else {
                hits.add(new BandAuditHit(biomeId, 0, 0.0, 0, 0, 0.0, watchedBiomes.contains(biomeId)));
            }
        }

        return new BandAuditReport(seed, radiusBlocks, stepBlocks, y, minDeg, maxDeg, totalInWindow[0], hits);
    }

    public static void writeBandAuditReport(Path outputPath, BandAuditReport report) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "=== Band Audit: %.1f-%.1f deg ===%n", report.minDeg(), report.maxDeg()));
        out.append(String.format(Locale.ROOT, "seed=%d  radius=%d  step=%d  y=%d%n",
                report.seed(), report.radiusBlocks(), report.stepBlocks(), report.y()));
        out.append(String.format(Locale.ROOT, "totalSamplesInWindow=%d%n%n", report.totalSamplesInWindow()));

        out.append(String.format(Locale.ROOT, "%-40s %8s %8s %8s %8s %10s %s%n",
                "biome_id", "hits", "pct", "first_x", "first_z", "first_lat", "type"));
        out.append(String.format(Locale.ROOT, "%-40s %8s %8s %8s %8s %10s %s%n",
                "--------", "----", "---", "-------", "-------", "---------", "----"));

        for (BandAuditHit hit : report.hits()) {
            out.append(String.format(Locale.ROOT, "%-40s %8d %7.2f%% %8d %8d %9.2f° %s%n",
                    hit.biomeId(), hit.hitCount(), hit.percentage(),
                    hit.firstSeenX(), hit.firstSeenZ(), hit.firstSeenLatDeg(),
                    hit.watched() ? "WATCHED" : "control"));
        }
        Files.writeString(outputPath, out.toString());
    }

    public static void writeInventoryJson(Path outputPath, InventoryReport report) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"seed\": ").append(report.seed()).append(",\n");
        out.append("  \"radiusBlocks\": ").append(report.radiusBlocks()).append(",\n");
        out.append("  \"discoveryStepUsed\": ").append(report.discoveryStepUsed()).append(",\n");
        out.append("  \"y\": ").append(report.y()).append(",\n");
        out.append("  \"biomes\": [\n");
        for (int i = 0; i < report.biomes().size(); i++) {
            InventoryBiome biome = report.biomes().get(i);
            out.append("    {")
                    .append("\"biome_id\":\"").append(jsonEscape(biome.biomeId())).append("\",")
                    .append("\"biome_name\":\"").append(jsonEscape(biome.biomeName())).append("\",")
                    .append("\"displayColor\":\"").append(hexColor(biome.displayColor())).append("\",")
                    .append("\"present_in_world\":true,")
                    .append("\"first_seen_x\":").append(biome.firstSeenX()).append(",")
                    .append("\"first_seen_z\":").append(biome.firstSeenZ()).append(",")
                    .append("\"placement_band\":\"").append(jsonEscape(biome.firstSeenChosenBand())).append("\",")
                    .append("\"first_seen_chosen_band\":\"").append(jsonEscape(biome.firstSeenChosenBand())).append("\",")
                    .append("\"first_seen_land_band\":\"").append(jsonEscape(biome.firstSeenLandBand())).append("\",")
                    .append("\"first_seen_raw_band\":\"").append(jsonEscape(biome.firstSeenRawBand())).append("\",")
                    .append("\"latitude_label\":\"").append(jsonEscape(biome.latitudeLabel())).append("\",")
                    .append("\"first_seen_band_note\":\"placement_band (=first_seen_chosen_band) is the biome placement authority; latitude_label and first_seen_raw_band are geometric annotations only\",")
                    .append("\"discovery_step_used\":").append(biome.discoveryStepUsed()).append(",")
                    .append("\"discovery_hits\":").append(biome.discoveryHits())
                    .append("}");
            if (i + 1 < report.biomes().size()) {
                out.append(",");
            }
            out.append("\n");
        }
        out.append("  ]\n");
        out.append("}\n");
        Files.writeString(outputPath, out.toString());
    }

    public static void writeSearchReportFiles(Path outputDir, SearchReport report) throws IOException {
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("results.json"), toSearchJson(report));
        Files.writeString(outputDir.resolve("results.txt"), toSearchText(report));
    }

    private static String toSearchJson(SearchReport report) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"generated_at\": \"").append(jsonEscape(report.generatedAt())).append("\",\n");
        out.append("  \"branch\": \"").append(jsonEscape(report.branch())).append("\",\n");
        out.append("  \"commit\": \"").append(jsonEscape(report.commit())).append("\",\n");
        out.append("  \"seed_start\": ").append(report.seedStart()).append(",\n");
        out.append("  \"seed_count\": ").append(report.seedCount()).append(",\n");
        out.append("  \"radius\": ").append(report.radiusBlocks()).append(",\n");
        out.append("  \"step\": ").append(report.stepBlocks()).append(",\n");
        out.append("  \"y\": ").append(report.y()).append(",\n");
        out.append("  \"target_biomes\": [");
        for (int i = 0; i < report.targetBiomes().size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append("\"").append(jsonEscape(report.targetBiomes().get(i))).append("\"");
        }
        out.append("],\n");
        out.append("  \"require_all\": ").append(report.requireAll()).append(",\n");
        out.append("  \"max_results\": ").append(report.maxResults()).append(",\n");
        out.append("  \"results\": [\n");
        for (int i = 0; i < report.results().size(); i++) {
            SearchMatch match = report.results().get(i);
            out.append("    {\n");
            out.append("      \"seed\": ").append(match.seed()).append(",\n");
            out.append("      \"matched_biomes\": [");
            for (int j = 0; j < match.matchedBiomes().size(); j++) {
                if (j > 0) {
                    out.append(", ");
                }
                out.append("\"").append(jsonEscape(match.matchedBiomes().get(j))).append("\"");
            }
            out.append("],\n");
            out.append("      \"hit_locations\": [\n");
            for (int j = 0; j < match.hitLocations().size(); j++) {
                SearchHit hit = match.hitLocations().get(j);
                out.append("        {")
                        .append("\"biome_id\":\"").append(jsonEscape(hit.biomeId())).append("\",")
                        .append("\"biome_name\":\"").append(jsonEscape(hit.biomeName())).append("\",")
                        .append("\"first_hit_x\":").append(hit.firstHitX()).append(",")
                        .append("\"first_hit_z\":").append(hit.firstHitZ()).append(",")
                        .append("\"latitude_label\":\"").append(jsonEscape(hit.latitudeLabel())).append("\",")
                        .append("\"hit_count_on_scan_grid\":").append(hit.hitCountOnScanGrid())
                        .append("}");
                if (j + 1 < match.hitLocations().size()) {
                    out.append(",");
                }
                out.append("\n");
            }
            out.append("      ],\n");
            out.append("      \"radius\": ").append(match.radiusBlocks()).append(",\n");
            out.append("      \"step\": ").append(match.stepBlocks()).append(",\n");
            out.append("      \"branch\": \"").append(jsonEscape(match.branch())).append("\",\n");
            out.append("      \"commit\": \"").append(jsonEscape(match.commit())).append("\",\n");
            out.append("      \"generated_at\": \"").append(jsonEscape(match.generatedAt())).append("\"\n");
            out.append("    }");
            if (i + 1 < report.results().size()) {
                out.append(",");
            }
            out.append("\n");
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    private static String toSearchText(SearchReport report) {
        StringBuilder out = new StringBuilder();
        out.append("generatedAt=").append(report.generatedAt()).append('\n');
        out.append("branch=").append(report.branch()).append('\n');
        out.append("commit=").append(report.commit()).append('\n');
        out.append("seedStart=").append(report.seedStart()).append('\n');
        out.append("seedCount=").append(report.seedCount()).append('\n');
        out.append("radius=").append(report.radiusBlocks()).append('\n');
        out.append("step=").append(report.stepBlocks()).append('\n');
        out.append("y=").append(report.y()).append('\n');
        out.append("targetBiomes=").append(String.join(",", report.targetBiomes())).append('\n');
        out.append("requireAll=").append(report.requireAll()).append('\n');
        out.append("results:\n");
        for (SearchMatch match : report.results()) {
            out.append("seed=").append(match.seed())
                    .append(" matched=").append(String.join(",", match.matchedBiomes()))
                    .append('\n');
            for (SearchHit hit : match.hitLocations()) {
                out.append("  - ")
                        .append(hit.biomeId())
                        .append(" @ x=").append(hit.firstHitX())
                        .append(" z=").append(hit.firstHitZ())
                        .append(" lat=").append(hit.latitudeLabel())
                        .append(" hits=").append(hit.hitCountOnScanGrid())
                        .append('\n');
            }
        }
        return out.toString();
    }

    private static void scanGrid(SamplerTemplate template,
                                 long seed,
                                 int radiusBlocks,
                                 int stepBlocks,
                                 int y,
                                 SampleConsumer consumer,
                                 Climate.Sampler sampler) {
        LatitudeBiomes.setWorldSeed(seed);
        int noiseY = Math.floorDiv(y, 4);
        for (int blockZ = -radiusBlocks; blockZ <= radiusBlocks; blockZ += stepBlocks) {
            int noiseZ = Math.floorDiv(blockZ, 4);
            for (int blockX = -radiusBlocks; blockX <= radiusBlocks; blockX += stepBlocks) {
                consumer.accept(blockX, blockZ, sampleBiomeId(template, radiusBlocks, y, blockX, blockZ, noiseY, noiseZ, sampler));
            }
        }
    }

    private static String sampleBiomeId(SamplerTemplate template,
                                        int radiusBlocks,
                                        int y,
                                        int blockX,
                                        int blockZ,
                                        int noiseY,
                                        int noiseZ,
                                        Climate.Sampler sampler) {
        int noiseX = Math.floorDiv(blockX, 4);
        Holder<Biome> base = template.baseSource().getNoiseBiome(noiseX, noiseY, noiseZ, sampler);
        Holder<Biome> picked = LatitudeBiomes.pick(
                template.biomeRegistry(),
                base,
                blockX,
                blockZ,
                y,
                radiusBlocks,
                sampler,
                "ATLAS_SAMPLER",
                null,
                null,
                null);
        Holder<Biome> out = picked != null ? picked : base;
        return biomeId(template.biomeRegistry(), out);
    }

    public static final class InventoryScanProcessor {
        private final SamplerTemplate template;
        private final long seed;
        private final int radiusBlocks;
        private final int stepBlocks;
        private final int y;
        private final RandomState noiseConfig;
        private final Climate.Sampler sampler;
        private final Map<String, InventoryAccumulator> found = new LinkedHashMap<>();
        private final long originalSeed;
        private final long startNanos = System.nanoTime();
        private final int noiseY;
        private int blockZ;
        private int blockX;
        private int rowCount;
        private boolean started;
        private boolean complete;
        private boolean seedActive;
        private boolean seedRestored;

        private InventoryScanProcessor(SamplerTemplate template,
                                       long seed,
                                       int radiusBlocks,
                                       int stepBlocks,
                                       int y) {
            this.template = template;
            this.seed = seed;
            this.radiusBlocks = radiusBlocks;
            this.stepBlocks = stepBlocks;
            this.y = y;
            this.noiseConfig = RandomState.create(template.settings().value(), template.noiseParameters(), seed);
            this.sampler = noiseConfig.sampler();
            this.originalSeed = template.templateSeed();
            this.noiseY = Math.floorDiv(y, 4);
            this.blockZ = -radiusBlocks;
            this.blockX = -radiusBlocks;
        }

        public InventoryReport processBudget(long budgetMs) {
            if (complete) {
                return buildReport();
            }
            if (!started) {
                started = true;
                LatitudeBiomes.setWorldSeed(seed);
                seedActive = true;
                atlasTiming(String.format(
                        Locale.ROOT,
                        "phase=scan-inventory-start seed=%d radius=%d step=%d y=%d",
                        seed,
                        radiusBlocks,
                        stepBlocks,
                        y));
            }

            boolean unbounded = budgetMs <= 0L || budgetMs == Long.MAX_VALUE;
            long deadline = unbounded ? Long.MAX_VALUE : safeDeadlineNanos(budgetMs);
            try {
                while (blockZ <= radiusBlocks && hasBudget(unbounded, deadline)) {
                    int noiseZ = Math.floorDiv(blockZ, 4);
                    while (blockX <= radiusBlocks && hasBudget(unbounded, deadline)) {
                        String biomeId = sampleBiomeId(template, radiusBlocks, y, blockX, blockZ, noiseY, noiseZ, sampler);
                        InventoryAccumulator acc = found.get(biomeId);
                        if (acc == null) {
                            int rawBandIndex = bandIndexForBlockZ(radiusBlocks, blockZ);
                            int chosenBandIndex = LatitudeBiomes.authoritativeChosenBandIndex(blockX, blockZ, radiusBlocks);
                            int landBandIndex = LatitudeBiomes.authoritativeLandBandIndex(blockX, blockZ, radiusBlocks);
                            acc = new InventoryAccumulator(
                                    biomeId,
                                    biomeDisplayName(biomeId),
                                    BiomePreviewExporter.stableColorForBiomeId(biomeId),
                                    blockX,
                                    blockZ,
                                    latitudeLabel(radiusBlocks, blockZ),
                                    bandIdForIndex(rawBandIndex),
                                    bandIdForIndex(chosenBandIndex),
                                    bandIdForIndex(landBandIndex),
                                    0);
                            found.put(biomeId, acc);
                        }
                        acc.hitCount++;
                        blockX += stepBlocks;
                    }

                    if (blockX > radiusBlocks) {
                        rowCount++;
                        if (rowCount % ATLAS_HEARTBEAT_ROWS == 0) {
                            atlasTiming(String.format(
                                    Locale.ROOT,
                                    "phase=scan-heartbeat rows=%d blockZ=%d elapsedMs=%d",
                                    rowCount,
                                    blockZ,
                                    elapsedMs(startNanos)));
                        }
                        blockZ += stepBlocks;
                        blockX = -radiusBlocks;
                    }
                }

                if (blockZ > radiusBlocks) {
                    complete = true;
                    close();
                    atlasTiming(String.format(
                            Locale.ROOT,
                            "phase=scan-complete rows=%d elapsedMs=%d",
                            rowCount,
                            elapsedMs(startNanos)));
                    return buildReport();
                }

                atlasTiming(String.format(
                        Locale.ROOT,
                        "phase=scan-yield rows=%d blockZ=%d blockX=%d elapsedMs=%d",
                        rowCount,
                        blockZ,
                        blockX,
                        elapsedMs(startNanos)));
                return null;
            } catch (RuntimeException | Error e) {
                close();
                throw e;
            }
        }

        public InventoryReport processAll() {
            try {
                InventoryReport report = processBudget(Long.MAX_VALUE);
                if (report == null) {
                    throw new IllegalStateException("Unbounded inventory scan yielded before completion");
                }
                return report;
            } finally {
                close();
            }
        }

        public boolean isComplete() {
            return complete;
        }

        public void close() {
            if (seedActive && !seedRestored) {
                restoreWorldSeed();
                seedRestored = true;
                seedActive = false;
            }
        }

        private static boolean hasBudget(boolean unbounded, long deadlineNanos) {
            return unbounded || System.nanoTime() <= deadlineNanos;
        }

        private static long safeDeadlineNanos(long budgetMs) {
            long safeBudgetMs = Math.max(1L, budgetMs);
            long budgetNanos = safeBudgetMs >= Long.MAX_VALUE / 1_000_000L
                    ? Long.MAX_VALUE
                    : safeBudgetMs * 1_000_000L;
            long now = System.nanoTime();
            return Long.MAX_VALUE - now <= budgetNanos ? Long.MAX_VALUE : now + budgetNanos;
        }

        private void restoreWorldSeed() {
            LatitudeBiomes.setWorldSeed(originalSeed);
        }

        private InventoryReport buildReport() {
            List<InventoryBiome> biomes = found.values().stream()
                    .map(acc -> new InventoryBiome(
                            acc.biomeId,
                            acc.biomeName,
                            acc.displayColor,
                            true,
                            acc.firstSeenX,
                            acc.firstSeenZ,
                            acc.latitudeLabel,
                            acc.firstSeenRawBand,
                            acc.firstSeenChosenBand,
                            acc.firstSeenLandBand,
                            stepBlocks,
                            acc.hitCount))
                    .toList();
            return new InventoryReport(seed, radiusBlocks, stepBlocks, y, biomes);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static void atlasTiming(String message) {
        System.out.println("[LAT][ATLAS_TIMING] " + message);
    }

    private static Set<String> normalizeTargets(Collection<String> raw) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (raw == null) {
            return normalized;
        }
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            String id = item.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) {
                normalized.add(id);
            }
        }
        return normalized;
    }

    private static String biomeId(Registry<Biome> biomeRegistry, Holder<Biome> biome) {
        Identifier id = biomeRegistry.getKey(biome.value());
        if (id != null) {
            return id.toString();
        }
        return biome.unwrapKey().map(key -> key.identifier().toString()).orElse("minecraft:plains");
    }

    private static String biomeDisplayName(String biomeId) {
        String raw = (biomeId == null ? "unknown" : biomeId).split(":")[biomeId != null && biomeId.contains(":") ? 1 : 0];
        String[] parts = raw.replace('_', ' ').trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.isEmpty() ? "Unknown" : out.toString();
    }

    private static String latitudeLabel(int radiusBlocks, int blockZ) {
        int deg = radiusBlocks <= 0
                ? 0
                : Mth.clamp((int) Math.round((Math.abs(blockZ) * 90.0) / (double) radiusBlocks), 0, 90);
        LatitudeBands.Band band = radiusBlocks <= 0
                ? LatitudeBands.Band.TROPICAL
                : LatitudeBands.fromAbsoluteLatitudeDeg(Math.abs((double) blockZ) * 90.0 / (double) radiusBlocks);
        String zoneLabel = band.displayName();
        if (deg == 0) {
            return zoneLabel + " 0";
        }
        return zoneLabel + " " + deg + (blockZ < 0 ? "N" : "S");
    }

    private static int bandIndexForBlockZ(int radiusBlocks, int blockZ) {
        if (radiusBlocks <= 0) {
            return 0;
        }
        double absLatDeg = Math.abs((double) blockZ) * 90.0 / (double) radiusBlocks;
        return switch (LatitudeBands.fromAbsoluteLatitudeDeg(absLatDeg)) {
            case TROPICAL -> 0;
            case SUBTROPICAL -> 1;
            case TEMPERATE -> 2;
            case SUBPOLAR -> 3;
            case POLAR -> 4;
        };
    }

    private static String bandIdForIndex(int bandIndex) {
        return switch (bandIndex) {
            case 0 -> LatitudeBands.Band.TROPICAL.id();
            case 1 -> LatitudeBands.Band.SUBTROPICAL.id();
            case 2 -> LatitudeBands.Band.TEMPERATE.id();
            case 3 -> LatitudeBands.Band.SUBPOLAR.id();
            case 4 -> LatitudeBands.Band.POLAR.id();
            default -> "unknown";
        };
    }

    private static String hexColor(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0x00FFFFFF);
    }

    private static String jsonEscape(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private interface SampleConsumer {
        void accept(int blockX, int blockZ, String biomeId);
    }

    private static final class InventoryAccumulator {
        private final String biomeId;
        private final String biomeName;
        private final int displayColor;
        private final int firstSeenX;
        private final int firstSeenZ;
        private final String latitudeLabel;
        private final String firstSeenRawBand;
        private final String firstSeenChosenBand;
        private final String firstSeenLandBand;
        private int hitCount;

        private InventoryAccumulator(String biomeId,
                                     String biomeName,
                                     int displayColor,
                                     int firstSeenX,
                                     int firstSeenZ,
                                     String latitudeLabel,
                                     String firstSeenRawBand,
                                     String firstSeenChosenBand,
                                     String firstSeenLandBand,
                                     int hitCount) {
            this.biomeId = biomeId;
            this.biomeName = biomeName;
            this.displayColor = displayColor;
            this.firstSeenX = firstSeenX;
            this.firstSeenZ = firstSeenZ;
            this.latitudeLabel = latitudeLabel;
            this.firstSeenRawBand = firstSeenRawBand;
            this.firstSeenChosenBand = firstSeenChosenBand;
            this.firstSeenLandBand = firstSeenLandBand;
            this.hitCount = hitCount;
        }
    }

    private static final class SearchHitAccumulator {
        private final String biomeId;
        private final String biomeName;
        private final int firstHitX;
        private final int firstHitZ;
        private final String latitudeLabel;
        private int hitCount;

        private SearchHitAccumulator(String biomeId,
                                     String biomeName,
                                     int firstHitX,
                                     int firstHitZ,
                                     String latitudeLabel,
                                     int hitCount) {
            this.biomeId = biomeId;
            this.biomeName = biomeName;
            this.firstHitX = firstHitX;
            this.firstHitZ = firstHitZ;
            this.latitudeLabel = latitudeLabel;
            this.hitCount = hitCount;
        }

        private String biomeId() {
            return biomeId;
        }
    }

    public record SamplerTemplate(Registry<Biome> biomeRegistry,
                                  BiomeSource baseSource,
                                  Holder<NoiseGeneratorSettings> settings,
                                  HolderGetter<NormalNoise.NoiseParameters> noiseParameters,
                                  long templateSeed) {
    }

    public record InventoryBiome(String biomeId,
                                 String biomeName,
                                 int displayColor,
                                 boolean presentInWorld,
                                 int firstSeenX,
                                 int firstSeenZ,
                                 String latitudeLabel,
                                 String firstSeenRawBand,
                                 String firstSeenChosenBand,
                                 String firstSeenLandBand,
                                 int discoveryStepUsed,
                                 int discoveryHits) {
    }

    public record InventoryReport(long seed,
                                  int radiusBlocks,
                                  int discoveryStepUsed,
                                  int y,
                                  List<InventoryBiome> biomes) {
    }

    public record SearchOptions(long seedStart,
                                int seedCount,
                                List<String> targetBiomes,
                                boolean requireAll,
                                int radiusBlocks,
                                int stepBlocks,
                                int y,
                                int maxResults) {
    }

    public record SearchHit(String biomeId,
                            String biomeName,
                            int firstHitX,
                            int firstHitZ,
                            String latitudeLabel,
                            int hitCountOnScanGrid) {
    }

    public record SearchMatch(long seed,
                              List<String> matchedBiomes,
                              List<SearchHit> hitLocations,
                              int radiusBlocks,
                              int stepBlocks,
                              String branch,
                              String commit,
                              String generatedAt) {
    }

    public record SearchReport(String generatedAt,
                               String branch,
                               String commit,
                               long seedStart,
                               int seedCount,
                               int radiusBlocks,
                               int stepBlocks,
                               int y,
                               List<String> targetBiomes,
                               boolean requireAll,
                               int maxResults,
                               List<SearchMatch> results) {
    }

    public record BandAuditHit(String biomeId,
                                int hitCount,
                                double percentage,
                                int firstSeenX,
                                int firstSeenZ,
                                double firstSeenLatDeg,
                                boolean watched) {
    }

    public record BandAuditReport(long seed,
                                   int radiusBlocks,
                                   int stepBlocks,
                                   int y,
                                   double minDeg,
                                   double maxDeg,
                                   int totalSamplesInWindow,
                                   List<BandAuditHit> hits) {
    }

    private static final class BandAuditAccumulator {
        private final String biomeId;
        private final int firstX;
        private final int firstZ;
        private final double firstLatDeg;
        private int hitCount;

        private BandAuditAccumulator(String biomeId, int firstX, int firstZ, double firstLatDeg) {
            this.biomeId = biomeId;
            this.firstX = firstX;
            this.firstZ = firstZ;
            this.firstLatDeg = firstLatDeg;
            this.hitCount = 0;
        }
    }
}
