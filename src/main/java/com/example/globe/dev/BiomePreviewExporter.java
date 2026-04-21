package com.example.globe.dev;

import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeBiomes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javax.imageio.ImageIO;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.Reader;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class BiomePreviewExporter {
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final int TOP_BIOME_COUNT = 20;
    private static final int MASK_MATCH_COLOR = 0xF2F5F8;
    private static final int MASK_MISS_COLOR = 0x11161B;
    private static final long DEFAULT_BUDGET_MS = 10L;
    private static final double SEAM_LAT_MIN_DEG = 32.0;
    private static final double SEAM_LAT_MAX_DEG = 38.0;
    private static final double TEMPERATE_SHOULDER_MIN_DEG = 35.28;
    private static final double TEMPERATE_SHOULDER_MAX_DEG = 37.88;
    private static final int BAND_INDEX_TEMPERATE = 2;
    private static final List<String> TEMPERATE_SHOULDER_KEY_BIOMES = List.of(
            "minecraft:plains",
            "minecraft:sunflower_plains",
            "minecraft:meadow",
            "minecraft:flower_forest",
            "minecraft:birch_forest",
            "minecraft:forest",
            "minecraft:dark_forest",
            "minecraft:windswept_hills",
            "minecraft:stony_peaks");
    private static final int DEFAULT_INVENTORY_DISCOVERY_STEP = 32;
    private static final Identifier MANGROVE_SWAMP_BIOME_ID = Identifier.parse("minecraft:mangrove_swamp");
    private static final DateTimeFormatter RUN_LABEL_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withLocale(Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final Map<String, Integer> PALETTE_OVERRIDES = loadPaletteOverrides();

    private BiomePreviewExporter() {
    }

    public static ExportResult export(ServerLevel world,
                                      int radiusBlocks,
                                      int stepBlocks,
                                      int y,
                                      Path runDirectory) throws IOException {
        return export(world, radiusBlocks, stepBlocks, y, runDirectory, world.getSeed(), ExportOptions.singleBiome(), null);
    }

    public static ExportResult export(ServerLevel world,
                                      int radiusBlocks,
                                      int stepBlocks,
                                      int y,
                                      Path runDirectory,
                                      ExportOptions options) throws IOException {
        return export(world, radiusBlocks, stepBlocks, y, runDirectory, world.getSeed(), options, null);
    }

    public static ExportResult export(ServerLevel world,
                                      int radiusBlocks,
                                      int stepBlocks,
                                      int y,
                                      Path runDirectory,
                                      long atlasSeed,
                                      ExportOptions options,
                                      String runLabel) throws IOException {
        System.out.println("[LAT][ATLAS_TRACE] phase=export-start ruggednessMode=constant-bypass");

        long startNanos = System.nanoTime();
        ExportOptions effectiveOptions = options != null ? options : ExportOptions.singleBiome();
        EnumSet<Layer> layers = effectiveOptions.layers();
        EnumSet<Overlay> overlays = effectiveOptions.overlays();
        List<BiomeMaskLayer> maskTargets = effectiveOptions.maskLayers();
        boolean emitBiomeIndex = effectiveOptions.emitBiomeIndex();
        boolean emitHeight = effectiveOptions.emitHeight();
        String resolvedRunLabel = resolveRunLabel(runLabel);

        // Batched path for emitHeight to avoid long single ticks. Caller should drive processBudget() across ticks.
        if (emitHeight) {
            HeightStepProcessor processor = HeightStepProcessor.create(
                    world,
                    radiusBlocks,
                    stepBlocks,
                    y,
                    runDirectory,
                    atlasSeed,
                    effectiveOptions,
                    resolvedRunLabel);
            long budgetMs = Math.max(1L, Long.getLong("latitude.atlas.heightBudgetMs", DEFAULT_BUDGET_MS));
            ExportResult result = processor.processBudget(budgetMs);
            while (result == null) {
                result = processor.processBudget(budgetMs);
            }
            return result;
        }

        int xMin = -radiusBlocks;
        int xMax = radiusBlocks;
        int zMin = -radiusBlocks;
        int zMax = radiusBlocks;

        int chunkMinX = Math.floorDiv(xMin, BLOCKS_PER_CHUNK);
        int chunkMaxX = Math.floorDiv(xMax, BLOCKS_PER_CHUNK);
        int chunkMinZ = Math.floorDiv(zMin, BLOCKS_PER_CHUNK);
        int chunkMaxZ = Math.floorDiv(zMax, BLOCKS_PER_CHUNK);
        long totalChunks = (long) (chunkMaxX - chunkMinX + 1) * (chunkMaxZ - chunkMinZ + 1);

        int width = ((xMax - xMin) / stepBlocks) + 1;
        int height = ((zMax - zMin) / stepBlocks) + 1;

        EnumMap<Layer, BufferedImage> images = new EnumMap<>(Layer.class);
        for (Layer layer : layers) {
            images.put(layer, new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
        }
        BufferedImage biomeIndexImage = emitBiomeIndex ? new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB) : null;
        BufferedImage chosenBandsImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage landBandsImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Map<Integer, SeamRowSummary> seamRows = new TreeMap<>();
        Map<Integer, TemperateShoulderCompositionRow> temperateShoulderRows = new TreeMap<>();
        Map<BiomeMaskLayer, BufferedImage> maskImages = new LinkedHashMap<>();
        for (BiomeMaskLayer maskLayer : maskTargets) {
            maskImages.put(maskLayer, new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
        }

        Map<String, Integer> biomeCounts = new HashMap<>();
        Map<String, Integer> biomeColors = new HashMap<>();
        Map<String, Integer> biomeIndices = new LinkedHashMap<>();
        Map<String, Integer> bandCounts = new HashMap<>();

        boolean renderBiomes = layers.contains(Layer.BIOMES);
        boolean renderBands = layers.contains(Layer.BANDS);
        boolean renderTemperature = layers.contains(Layer.TEMPERATURE);
        boolean renderHumidity = layers.contains(Layer.HUMIDITY);
        boolean renderContinentalness = layers.contains(Layer.CONTINENTALNESS);
        boolean renderRuggedness = layers.contains(Layer.RUGGEDNESS);
        boolean renderBiomeMasks = !maskTargets.isEmpty();
        boolean needsBiomeSampling = renderBiomes || renderBiomeMasks || emitBiomeIndex;

        ChunkGenerator generator = world.getChunkSource().getGenerator();
        BiomeSource biomeSource = generator.getBiomeSource();
        BiomeSource baseSource = biomeSource instanceof LatitudeBiomeSource latitudeSource
                ? latitudeSource.original()
                : biomeSource;
        Registry<Biome> biomeRegistry = world.registryAccess().lookupOrThrow(Registries.BIOME);
        RandomState noiseConfig = RandomState.create(
                ((net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator) generator).generatorSettings().value(),
                world.registryAccess().lookupOrThrow(Registries.NOISE),
                atlasSeed);
        Climate.Sampler sampler = noiseConfig.sampler();
        net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noiseGen =
                generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator ng ? ng : null;
        // Lightweight surface stub for atlas mode: keep sea level from the generator,
        // but skip expensive height probing by leaving noiseConfig/heightView unset.
        net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator gatedNoiseGen = noiseGen;
        RandomState gatedNoiseConfig = null;
        net.minecraft.world.level.LevelHeightAccessor gatedHeightView = null;

        int noiseY = Math.floorDiv(y, 4);
        final String pickerContext = "ATLAS_SAMPLER";
        for (int imageZ = 0, blockZ = zMin; imageZ < height; imageZ++, blockZ += stepBlocks) {
            int noiseZ = Math.floorDiv(blockZ, 4);
            for (int imageX = 0, blockX = xMin; imageX < width; imageX++, blockX += stepBlocks) {
                int noiseX = Math.floorDiv(blockX, 4);

                String sampledBiomeId = null;
                if (needsBiomeSampling) {
                    Holder<Biome> base = baseSource.getNoiseBiome(noiseX, noiseY, noiseZ, sampler);
                    Holder<Biome> picked = LatitudeBiomes.pick(
                            biomeRegistry,
                            base,
                            blockX,
                            blockZ,
                            y,
                            radiusBlocks,
                            sampler,
                            pickerContext,
                            gatedNoiseGen,
                            gatedNoiseConfig,
                            gatedHeightView);
                    Holder<Biome> out = picked != null ? picked : base;
                    sampledBiomeId = biomeId(biomeRegistry, out);

                    boolean mangroveMaskHit = false;
                    if (renderBiomeMasks && sampledBiomeId != null) {
                        for (BiomeMaskLayer maskLayer : maskTargets) {
                            boolean maskMatch = maskLayer.matches(sampledBiomeId);
                            int maskColor = maskMatch ? MASK_MATCH_COLOR : MASK_MISS_COLOR;
                            maskImages.get(maskLayer).setRGB(imageX, imageZ, maskColor);
                            if (maskMatch && isMangroveMaskLayer(maskLayer)) {
                                mangroveMaskHit = true;
                            }
                        }
                    }

                    if (mangroveMaskHit) {
                        out = forceMangroveSwampForAtlas(biomeRegistry, out);
                        sampledBiomeId = biomeId(biomeRegistry, out);
                    }

                    biomeCounts.merge(sampledBiomeId, 1, Integer::sum);
                    if (emitBiomeIndex && biomeIndexImage != null) {
                        int biomeIndex = biomeIndices.computeIfAbsent(sampledBiomeId, ignored -> biomeIndices.size());
                        biomeIndexImage.setRGB(imageX, imageZ, encodeBiomeIndexColor(biomeIndex));
                    }
                    if (renderBiomes) {
                        int rgb = biomeColors.computeIfAbsent(sampledBiomeId, BiomePreviewExporter::stableColorForBiomeId);
                        images.get(Layer.BIOMES).setRGB(imageX, imageZ, rgb);
                    }
                }
                int chosenBandIndex = LatitudeBiomes.authoritativeChosenBandIndex(blockX, blockZ, radiusBlocks);
                int landBandIndex = LatitudeBiomes.authoritativeLandBandIndex(blockX, blockZ, radiusBlocks);
                chosenBandsImage.setRGB(imageX, imageZ, colorForBandIndex(chosenBandIndex));
                landBandsImage.setRGB(imageX, imageZ, colorForBandIndex(landBandIndex));
                double latDeg = latitudeDegreesForBlockZ(radiusBlocks, blockZ);
                if (latDeg >= SEAM_LAT_MIN_DEG && latDeg <= SEAM_LAT_MAX_DEG) {
                    SeamRowSummary row = seamRows.computeIfAbsent(blockZ, ignored -> new SeamRowSummary(latDeg));
                    row.addChosen(chosenBandIndex);
                    row.addLand(landBandIndex);
                    if (isWarmDryBiomeId(sampledBiomeId)) {
                        row.finalWarmDry++;
                    }
                    if (isTemperateBiomeId(sampledBiomeId)) {
                        row.finalTemperate++;
                    }
                }
                if (isTemperateShoulderProfileRow(latDeg, landBandIndex)) {
                    TemperateShoulderCompositionRow row = temperateShoulderRows.computeIfAbsent(blockZ, ignored -> new TemperateShoulderCompositionRow(latDeg));
                    row.addSample(sampledBiomeId);
                }

                if (renderBands) {
                    LatitudeBands.Band band = bandForBlockZ(radiusBlocks, blockZ);
                    images.get(Layer.BANDS).setRGB(imageX, imageZ, colorForBand(band));
                    bandCounts.merge(band.id(), 1, Integer::sum);
                }

                if (renderTemperature || renderHumidity || renderContinentalness) {
                    Climate.TargetPoint point = sampler.sample(noiseX, noiseY, noiseZ);
                    if (renderTemperature) {
                        double temperature01 = normalizeNoise(Climate.unquantizeCoord(point.temperature()));
                        images.get(Layer.TEMPERATURE).setRGB(imageX, imageZ, colorForTemperature(temperature01));
                    }
                    if (renderHumidity) {
                        double humidity01 = normalizeNoise(Climate.unquantizeCoord(point.humidity()));
                        images.get(Layer.HUMIDITY).setRGB(imageX, imageZ, colorForHumidity(humidity01));
                    }
                    if (renderContinentalness) {
                        double continentalness = Mth.clamp(Climate.unquantizeCoord(point.continentalness()), -1.0, 1.0);
                        images.get(Layer.CONTINENTALNESS).setRGB(imageX, imageZ, colorForContinentalness(continentalness));
                    }
                }

                if (renderRuggedness &&
                        noiseGen instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator) {
                    int delta = 0;
                    images.get(Layer.RUGGEDNESS).setRGB(imageX, imageZ, colorForRuggedness(delta));
                }
            }
        }

        if (!overlays.isEmpty()) {
            applyOverlays(images.values(), overlays, radiusBlocks, zMin, stepBlocks);
            applyOverlays(maskImages.values(), overlays, radiusBlocks, zMin, stepBlocks);
        }

        long seed = atlasSeed;
        Path outputDir = atlasStepDirectory(defaultAtlasRoot(runDirectory), seed, resolvedRunLabel, radiusBlocks, stepBlocks);
        Files.createDirectories(outputDir);

        EnumMap<Layer, Path> layerPaths = new EnumMap<>(Layer.class);
        Map<BiomeMaskLayer, Path> maskPaths = new LinkedHashMap<>();
        Path biomeIndexPath = null;
        for (Layer layer : layers) {
            Path pngPath = outputDir.resolve(layer.fileStem() + ".png");
            BufferedImage image = images.get(layer);
            boolean wrote = ImageIO.write(image, "png", pngPath.toFile());
            if (!wrote) {
                throw new IOException("PNG writer unavailable for layer " + layer.fileStem());
            }
            layerPaths.put(layer, pngPath);
        }
        for (BiomeMaskLayer maskLayer : maskTargets) {
            Path pngPath = outputDir.resolve(maskLayer.fileStem() + ".png");
            BufferedImage image = maskImages.get(maskLayer);
            boolean wrote = ImageIO.write(image, "png", pngPath.toFile());
            if (!wrote) {
                throw new IOException("PNG writer unavailable for layer " + maskLayer.fileStem());
            }
            maskPaths.put(maskLayer, pngPath);
        }
        Path chosenBandsPath = outputDir.resolve("chosen_bands.png");
        if (!ImageIO.write(chosenBandsImage, "png", chosenBandsPath.toFile())) {
            throw new IOException("PNG writer unavailable for chosen_bands");
        }
        Path landBandsPath = outputDir.resolve("land_bands.png");
        if (!ImageIO.write(landBandsImage, "png", landBandsPath.toFile())) {
            throw new IOException("PNG writer unavailable for land_bands");
        }
        writeSeamRowSummary(outputDir.resolve("seam_rows.txt"), seamRows);
        writeSeamCropArtifacts(
                outputDir,
                images.get(Layer.BIOMES),
                chosenBandsImage,
                landBandsImage,
                radiusBlocks,
                zMin,
                stepBlocks);
        writeTemperateShoulderComposition(outputDir.resolve("seam_temperate_composition.txt"), temperateShoulderRows);
        if (emitBiomeIndex && biomeIndexImage != null) {
            biomeIndexPath = outputDir.resolve("biome_ids.png");
            boolean wrote = ImageIO.write(biomeIndexImage, "png", biomeIndexPath.toFile());
            if (!wrote) {
                throw new IOException("PNG writer unavailable for biome_ids");
            }
            writeBiomePalette(outputDir.resolve("biome_palette.json"), biomeIndices);
            writePaletteAuthority(outputDir);
        }

        System.out.println("[LAT][ATLAS_TRACE] phase=export-complete ruggednessMode=constant-bypass");

        int inventoryDiscoveryStep = inventoryDiscoveryStep(stepBlocks);
        BiomeSamplerTools.InventoryReport inventoryReport = needsBiomeSampling
                ? BiomeSamplerTools.discoverInventory(BiomeSamplerTools.createTemplate(world), seed, radiusBlocks, inventoryDiscoveryStep, y)
                : new BiomeSamplerTools.InventoryReport(seed, radiusBlocks, inventoryDiscoveryStep, y, List.of());
        Path inventoryPath = outputDir.resolve("world_biome_inventory.json");
        BiomeSamplerTools.writeInventoryJson(inventoryPath, inventoryReport);

        Path summaryPath = outputDir.resolve("biomes.txt");
        long totalSamples = (long) width * height;
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        writeSummary(
                summaryPath,
                seed,
                radiusBlocks,
                stepBlocks,
                y,
                xMin,
                xMax,
                zMin,
                zMax,
                chunkMinX,
                chunkMaxX,
                chunkMinZ,
                chunkMaxZ,
                totalChunks,
                width,
                height,
                totalSamples,
                durationMs,
                biomeCounts,
                inventoryReport,
                inventoryPath);

        if (effectiveOptions.writeLegends()) {
            writeLegendFiles(
                    outputDir,
                    seed,
                    radiusBlocks,
                    stepBlocks,
                    y,
                    layers,
                    overlays,
                    maskTargets,
                    layerPaths,
                    maskPaths,
                    bandCounts,
                    totalSamples);
        }

        Path primaryPng = layerPaths.get(Layer.BIOMES);
        if (primaryPng == null && !layerPaths.isEmpty()) {
            primaryPng = layerPaths.values().iterator().next();
        }
        if (primaryPng == null && !maskPaths.isEmpty()) {
            primaryPng = maskPaths.values().iterator().next();
        }
        if (primaryPng == null && biomeIndexPath != null) {
            primaryPng = biomeIndexPath;
        }
        if (primaryPng == null) {
            throw new IOException("No export layers were generated");
        }
        return new ExportResult(primaryPng, summaryPath, seed, radiusBlocks, stepBlocks, width, height, totalSamples, durationMs);
    }

    /**
     * Stateful processor for height-enabled exports that can be advanced in small time-budgeted chunks.
     */
    public static final class HeightStepProcessor {
        private final ServerLevel world;
        private final int radiusBlocks;
        private final int stepBlocks;
        private final int y;
        private final Path runDirectory;
        private final long atlasSeed;
        private final ExportOptions options;
        private final String runLabel;

        private final EnumSet<Layer> layers;
        private final EnumSet<Overlay> overlays;
        private final List<BiomeMaskLayer> maskTargets;
        private final boolean emitBiomeIndex;

        private final int xMin;
        private final int zMin;
        private final int width;
        private final int height;
        private final int chunkMinX;
        private final int chunkMaxX;
        private final int chunkMinZ;
        private final int chunkMaxZ;

        private final EnumMap<Layer, BufferedImage> images = new EnumMap<>(Layer.class);
        private final Map<BiomeMaskLayer, BufferedImage> maskImages = new LinkedHashMap<>();
        private BufferedImage biomeIndexImage;
        private BufferedImage chosenBandsImage;
        private BufferedImage landBandsImage;

        private final Map<String, Integer> biomeCounts = new HashMap<>();
        private final Map<String, Integer> biomeColors = new HashMap<>();
        private final Map<String, Integer> biomeIndices = new LinkedHashMap<>();
        private final Map<String, Integer> bandCounts = new HashMap<>();
        private final Map<Integer, SeamRowSummary> seamRows = new TreeMap<>();
        private final Map<Integer, TemperateShoulderCompositionRow> temperateShoulderRows = new TreeMap<>();

        private final ChunkGenerator generator;
        private final BiomeSource baseSource;
        private final Registry<Biome> biomeRegistry;
        private final RandomState noiseConfig;
        private final Climate.Sampler sampler;
        private final net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator gatedNoiseGen;
        private final RandomState gatedNoiseConfig;
        private final net.minecraft.world.level.LevelHeightAccessor gatedHeightView;
        private final int noiseY;

        private int imageX = 0;
        private int imageZ = 0;
        private final long startNanos;
        private ExportResult result;

        private HeightStepProcessor(ServerLevel world,
                                     int radiusBlocks,
                                     int stepBlocks,
                                     int y,
                                     Path runDirectory,
                                     long atlasSeed,
                                     ExportOptions options,
                                     String runLabel) {
            this.world = world;
            this.radiusBlocks = radiusBlocks;
            this.stepBlocks = stepBlocks;
            this.y = y;
            this.runDirectory = runDirectory;
            this.atlasSeed = atlasSeed;
            this.options = options != null ? options : ExportOptions.singleBiome();
            this.runLabel = resolveRunLabel(runLabel);
            this.layers = this.options.layers();
            this.overlays = this.options.overlays();
            this.maskTargets = this.options.maskLayers();
            this.emitBiomeIndex = this.options.emitBiomeIndex();

            this.xMin = -radiusBlocks;
            this.zMin = -radiusBlocks;
            int xMax = radiusBlocks;
            int zMax = radiusBlocks;
            this.width = ((xMax - xMin) / stepBlocks) + 1;
            this.height = ((zMax - zMin) / stepBlocks) + 1;
            this.chunkMinX = Math.floorDiv(xMin, BLOCKS_PER_CHUNK);
            this.chunkMaxX = Math.floorDiv(xMax, BLOCKS_PER_CHUNK);
            this.chunkMinZ = Math.floorDiv(zMin, BLOCKS_PER_CHUNK);
            this.chunkMaxZ = Math.floorDiv(zMax, BLOCKS_PER_CHUNK);

            this.generator = world.getChunkSource().getGenerator();
            BiomeSource biomeSource = generator.getBiomeSource();
            this.baseSource = biomeSource instanceof LatitudeBiomeSource latitudeSource
                    ? latitudeSource.original()
                    : biomeSource;
            this.biomeRegistry = world.registryAccess().lookupOrThrow(Registries.BIOME);
            this.noiseConfig = RandomState.create(
                    ((net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator) this.generator).generatorSettings().value(),
                    world.registryAccess().lookupOrThrow(Registries.NOISE),
                    atlasSeed);
            this.sampler = noiseConfig.sampler();
            net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noiseGen =
                    generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator ng ? ng : null;
            this.gatedNoiseGen = noiseGen;
            this.gatedNoiseConfig = noiseConfig;
            this.gatedHeightView = world;
            this.noiseY = Math.floorDiv(y, 4);

            for (Layer layer : layers) {
                images.put(layer, new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
            }
            if (emitBiomeIndex) {
                biomeIndexImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            }
            chosenBandsImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            landBandsImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (BiomeMaskLayer maskLayer : maskTargets) {
                maskImages.put(maskLayer, new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
            }

            this.startNanos = System.nanoTime();
        }

        static HeightStepProcessor create(ServerLevel world,
                                          int radiusBlocks,
                                          int stepBlocks,
                                          int y,
                                          Path runDirectory,
                                          long atlasSeed,
                                          ExportOptions options,
                                          String runLabel) {
            return new HeightStepProcessor(world, radiusBlocks, stepBlocks, y, runDirectory, atlasSeed, options, runLabel);
        }

        /**
         * Process as many pixels as possible within the provided budget (milliseconds).
         * Returns ExportResult when the step is complete; otherwise null.
         */
        public ExportResult processBudget(long budgetMs) {
            if (result != null) {
                return result;
            }
            long budgetNanos = Math.max(1L, budgetMs) * 1_000_000L;
            long deadline = System.nanoTime() + budgetNanos;
            final String pickerContext = "ATLAS_SAMPLER";

            while (imageZ < height && System.nanoTime() <= deadline) {
                int blockZ = zMin + (imageZ * stepBlocks);
                int noiseZ = Math.floorDiv(blockZ, 4);
                int blockX = xMin + (imageX * stepBlocks);
                int noiseX = Math.floorDiv(blockX, 4);

                String sampledBiomeId = null;
                if (needsBiomeSampling()) {
                    Holder<Biome> base = baseSource.getNoiseBiome(noiseX, noiseY, noiseZ, sampler);
                    Holder<Biome> picked = LatitudeBiomes.pick(
                            biomeRegistry,
                            base,
                            blockX,
                            blockZ,
                            y,
                            radiusBlocks,
                            sampler,
                            pickerContext,
                            gatedNoiseGen,
                            gatedNoiseConfig,
                            gatedHeightView);
                    Holder<Biome> out = picked != null ? picked : base;
                    sampledBiomeId = biomeId(biomeRegistry, out);

                    boolean mangroveMaskHit = false;
                    if (!maskTargets.isEmpty() && sampledBiomeId != null) {
                        for (BiomeMaskLayer maskLayer : maskTargets) {
                            boolean maskMatch = maskLayer.matches(sampledBiomeId);
                            int maskColor = maskMatch ? MASK_MATCH_COLOR : MASK_MISS_COLOR;
                            maskImages.get(maskLayer).setRGB(imageX, imageZ, maskColor);
                            if (maskMatch && isMangroveMaskLayer(maskLayer)) {
                                mangroveMaskHit = true;
                            }
                        }
                    }

                    if (mangroveMaskHit) {
                        out = forceMangroveSwampForAtlas(biomeRegistry, out);
                        sampledBiomeId = biomeId(biomeRegistry, out);
                    }

                    biomeCounts.merge(sampledBiomeId, 1, Integer::sum);
                    if (emitBiomeIndex && biomeIndexImage != null) {
                        int biomeIndex = biomeIndices.computeIfAbsent(sampledBiomeId, ignored -> biomeIndices.size());
                        biomeIndexImage.setRGB(imageX, imageZ, encodeBiomeIndexColor(biomeIndex));
                    }
                    if (layers.contains(Layer.BIOMES)) {
                        int rgb = biomeColors.computeIfAbsent(sampledBiomeId, BiomePreviewExporter::stableColorForBiomeId);
                        images.get(Layer.BIOMES).setRGB(imageX, imageZ, rgb);
                    }
                }
                int chosenBandIndex = LatitudeBiomes.authoritativeChosenBandIndex(blockX, blockZ, radiusBlocks);
                int landBandIndex = LatitudeBiomes.authoritativeLandBandIndex(blockX, blockZ, radiusBlocks);
                chosenBandsImage.setRGB(imageX, imageZ, colorForBandIndex(chosenBandIndex));
                landBandsImage.setRGB(imageX, imageZ, colorForBandIndex(landBandIndex));
                double latDeg = latitudeDegreesForBlockZ(radiusBlocks, blockZ);
                if (latDeg >= SEAM_LAT_MIN_DEG && latDeg <= SEAM_LAT_MAX_DEG) {
                    SeamRowSummary row = seamRows.computeIfAbsent(blockZ, ignored -> new SeamRowSummary(latDeg));
                    row.addChosen(chosenBandIndex);
                    row.addLand(landBandIndex);
                    if (isWarmDryBiomeId(sampledBiomeId)) {
                        row.finalWarmDry++;
                    }
                    if (isTemperateBiomeId(sampledBiomeId)) {
                        row.finalTemperate++;
                    }
                }
                if (isTemperateShoulderProfileRow(latDeg, landBandIndex)) {
                    TemperateShoulderCompositionRow row = temperateShoulderRows.computeIfAbsent(blockZ, ignored -> new TemperateShoulderCompositionRow(latDeg));
                    row.addSample(sampledBiomeId);
                }

                if (layers.contains(Layer.BANDS)) {
                    LatitudeBands.Band band = bandForBlockZ(radiusBlocks, blockZ);
                    images.get(Layer.BANDS).setRGB(imageX, imageZ, colorForBand(band));
                    bandCounts.merge(band.id(), 1, Integer::sum);
                }

                if (layers.contains(Layer.TEMPERATURE) || layers.contains(Layer.HUMIDITY) || layers.contains(Layer.CONTINENTALNESS)) {
                    Climate.TargetPoint point = sampler.sample(noiseX, noiseY, noiseZ);
                    if (layers.contains(Layer.TEMPERATURE)) {
                        double temperature01 = normalizeNoise(Climate.unquantizeCoord(point.temperature()));
                        images.get(Layer.TEMPERATURE).setRGB(imageX, imageZ, colorForTemperature(temperature01));
                    }
                    if (layers.contains(Layer.HUMIDITY)) {
                        double humidity01 = normalizeNoise(Climate.unquantizeCoord(point.humidity()));
                        images.get(Layer.HUMIDITY).setRGB(imageX, imageZ, colorForHumidity(humidity01));
                    }
                    if (layers.contains(Layer.CONTINENTALNESS)) {
                        double continentalness = Mth.clamp(Climate.unquantizeCoord(point.continentalness()), -1.0, 1.0);
                        images.get(Layer.CONTINENTALNESS).setRGB(imageX, imageZ, colorForContinentalness(continentalness));
                    }
                }

                if (layers.contains(Layer.RUGGEDNESS) &&
                        gatedNoiseGen instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator) {
                    int delta = com.example.globe.world.LatitudeBiomes.previewRobustDelta(
                            gatedNoiseGen, gatedNoiseConfig, gatedHeightView, blockX, blockZ);
                    images.get(Layer.RUGGEDNESS).setRGB(imageX, imageZ, colorForRuggedness(delta));
                }

                advanceCursor();
            }

            if (imageZ >= height) {
                finalizeResult();
            }
            return result;
        }

        private void advanceCursor() {
            imageX++;
            if (imageX >= width) {
                imageX = 0;
                imageZ++;
            }
        }

        private boolean needsBiomeSampling() {
            return layers.contains(Layer.BIOMES) || !maskTargets.isEmpty() || emitBiomeIndex;
        }

        private void finalizeResult() {
            try {
                if (!overlays.isEmpty()) {
                    applyOverlays(images.values(), overlays, radiusBlocks, zMin, stepBlocks);
                    applyOverlays(maskImages.values(), overlays, radiusBlocks, zMin, stepBlocks);
                }

                long seed = atlasSeed;
                Path outputDir = atlasStepDirectory(defaultAtlasRoot(runDirectory), seed, runLabel, radiusBlocks, stepBlocks);
                Files.createDirectories(outputDir);

                EnumMap<Layer, Path> layerPaths = new EnumMap<>(Layer.class);
                Map<BiomeMaskLayer, Path> maskPaths = new LinkedHashMap<>();
                Path biomeIndexPath = null;
                for (Layer layer : layers) {
                    Path pngPath = outputDir.resolve(layer.fileStem() + ".png");
                    BufferedImage image = images.get(layer);
                    boolean wrote = ImageIO.write(image, "png", pngPath.toFile());
                    if (!wrote) {
                        throw new IOException("PNG writer unavailable for layer " + layer.fileStem());
                    }
                    layerPaths.put(layer, pngPath);
                }
                for (BiomeMaskLayer maskLayer : maskTargets) {
                    Path pngPath = outputDir.resolve(maskLayer.fileStem() + ".png");
                    BufferedImage image = maskImages.get(maskLayer);
                    boolean wrote = ImageIO.write(image, "png", pngPath.toFile());
                    if (!wrote) {
                        throw new IOException("PNG writer unavailable for layer " + maskLayer.fileStem());
                    }
                    maskPaths.put(maskLayer, pngPath);
                }
                Path chosenBandsPath = outputDir.resolve("chosen_bands.png");
                if (!ImageIO.write(chosenBandsImage, "png", chosenBandsPath.toFile())) {
                    throw new IOException("PNG writer unavailable for chosen_bands");
                }
                Path landBandsPath = outputDir.resolve("land_bands.png");
                if (!ImageIO.write(landBandsImage, "png", landBandsPath.toFile())) {
                    throw new IOException("PNG writer unavailable for land_bands");
                }
                writeSeamRowSummary(outputDir.resolve("seam_rows.txt"), seamRows);
                writeSeamCropArtifacts(
                        outputDir,
                        images.get(Layer.BIOMES),
                        chosenBandsImage,
                        landBandsImage,
                        radiusBlocks,
                        zMin,
                        stepBlocks);
                writeTemperateShoulderComposition(outputDir.resolve("seam_temperate_composition.txt"), temperateShoulderRows);
                if (emitBiomeIndex && biomeIndexImage != null) {
                    biomeIndexPath = outputDir.resolve("biome_ids.png");
                    boolean wrote = ImageIO.write(biomeIndexImage, "png", biomeIndexPath.toFile());
                    if (!wrote) {
                        throw new IOException("PNG writer unavailable for biome_ids");
                    }
                    writeBiomePalette(outputDir.resolve("biome_palette.json"), biomeIndices);
                    writePaletteAuthority(outputDir);
                }

                int inventoryDiscoveryStep = inventoryDiscoveryStep(stepBlocks);
                BiomeSamplerTools.InventoryReport inventoryReport = needsBiomeSampling()
                        ? BiomeSamplerTools.discoverInventory(BiomeSamplerTools.createTemplate(world), seed, radiusBlocks, inventoryDiscoveryStep, y)
                        : new BiomeSamplerTools.InventoryReport(seed, radiusBlocks, inventoryDiscoveryStep, y, List.of());
                Path inventoryPath = outputDir.resolve("world_biome_inventory.json");
                BiomeSamplerTools.writeInventoryJson(inventoryPath, inventoryReport);

                Path summaryPath = outputDir.resolve("biomes.txt");
                long totalSamples = (long) width * height;
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
                writeSummary(
                        summaryPath,
                        seed,
                        radiusBlocks,
                        stepBlocks,
                        y,
                        xMin,
                        xMin + (width - 1) * stepBlocks,
                        zMin,
                        zMin + (height - 1) * stepBlocks,
                        chunkMinX,
                        chunkMaxX,
                        chunkMinZ,
                        chunkMaxZ,
                        (long) width * height,
                        width,
                        height,
                        totalSamples,
                        durationMs,
                        biomeCounts,
                        inventoryReport,
                        inventoryPath);

                if (options.writeLegends()) {
                    writeLegendFiles(
                            outputDir,
                            seed,
                            radiusBlocks,
                            stepBlocks,
                            y,
                            layers,
                            overlays,
                            maskTargets,
                            layerPaths,
                            maskPaths,
                            bandCounts,
                            totalSamples);
                }

                this.result = new ExportResult(
                        summaryPath,
                        summaryPath,
                        seed,
                        radiusBlocks,
                        stepBlocks,
                        width,
                        height,
                        totalSamples,
                        durationMs);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static int encodeBiomeIndexColor(int index) {
        int channel = index & 0xFF;
        return (channel << 16) | (channel << 8) | channel;
    }

    private static void writeBiomePalette(Path palettePath, Map<String, Integer> biomeIndices) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"biomes\": [\n");

        int i = 0;
        int size = biomeIndices.size();
        for (Map.Entry<String, Integer> entry : biomeIndices.entrySet()) {
            String biomeId = entry.getKey();
            int index = entry.getValue();
            out.append("    {\"index\": ").append(index)
                    .append(", \"biome_id\": \"").append(jsonEscape(biomeId))
                    .append("\", \"displayColor\": \"").append(hexColor(stableColorForBiomeId(biomeId)))
                    .append("\"}");
            if (++i < size) {
                out.append(",");
            }
            out.append("\n");
        }

        out.append("  ]\n");
        out.append("}\n");
        Files.writeString(palettePath, out.toString());
    }

    private static void writePaletteAuthority(Path outputDir) throws IOException {
        Path authorityPath = outputDir.resolve("palette_authority.json");
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(PALETTE_OVERRIDES.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"biomes\": {\n");
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            out.append("    \"").append(jsonEscape(entry.getKey())).append("\": \"")
                    .append(hexColor(entry.getValue()))
                    .append("\"");
            if (i + 1 < entries.size()) {
                out.append(",");
            }
            out.append("\n");
        }
        out.append("  }\n");
        out.append("}\n");
        Files.writeString(authorityPath, out.toString());
    }

    private static Map<String, Integer> loadPaletteOverrides() {
        Map<String, Integer> overrides = new HashMap<>();
        Path candidate = Path.of("tools", "atlas", "palette_authority.json");
        if (Files.exists(candidate)) {
            try (Reader reader = Files.newBufferedReader(candidate)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed != null && parsed.isJsonObject()) {
                    JsonObject root = parsed.getAsJsonObject();
                    JsonObject biomes = root.getAsJsonObject("biomes");
                    if (biomes != null) {
                        for (Map.Entry<String, JsonElement> entry : biomes.entrySet()) {
                            JsonElement val = entry.getValue();
                            if (val != null && val.isJsonPrimitive()) {
                                Integer parsedColor = parseHexColor(val.getAsString());
                                if (parsedColor != null) {
                                    overrides.put(entry.getKey().toLowerCase(Locale.ROOT), parsedColor);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[LAT][ATLAS] palette_authority.json parse failed: " + e.getMessage());
            }
        }

        // Built-in defaults to keep outputs deterministic even if the JSON file is missing.
        overrides.putIfAbsent("minecraft:beach", 0xE0C097);
        overrides.putIfAbsent("minecraft:snowy_beach", 0xE9E1CC);
        overrides.putIfAbsent("minecraft:stony_shore", 0x9A9A9A);
        overrides.putIfAbsent("minecraft:badlands", 0xD47F34);
        overrides.putIfAbsent("minecraft:wooded_badlands", 0xB86832);
        overrides.putIfAbsent("minecraft:eroded_badlands", 0xE3B35A);

        return Collections.unmodifiableMap(overrides);
    }

    private static Integer parseHexColor(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        try {
            int rgb = Integer.parseInt(cleaned, 16);
            if ((rgb & 0xFF000000) != 0) {
                rgb = rgb & 0x00FFFFFF;
            }
            return rgb;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String jsonEscape(String input) {
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

    public static Path defaultAtlasRoot(Path runDirectory) {
        return runDirectory.toAbsolutePath().normalize().resolve("latdev").resolve("atlas");
    }

    public static Path atlasRunDirectory(Path atlasRoot, long seed, String runLabel) {
        return atlasRoot
                .resolve("seed_" + seed)
                .resolve("Run_" + resolveRunLabel(runLabel));
    }

    public static Path atlasStepDirectory(Path atlasRoot, long seed, String runLabel, int radiusBlocks, int stepBlocks) {
        return atlasRunDirectory(atlasRoot, seed, runLabel)
                .resolve("R" + radiusBlocks)
                .resolve("step" + stepBlocks);
    }

    public static String resolveRunLabel(String runLabel) {
        String candidate = normalizeRunLabel(runLabel);
        if (!candidate.isEmpty()) {
            return candidate;
        }

        String commit = normalizeRunLabel(currentGitCommit());
        if (!commit.isEmpty()) {
            return commit;
        }

        return RUN_LABEL_TIMESTAMP.format(Instant.now());
    }

    private static String normalizeRunLabel(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String cleaned = trimmed.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() ? "" : cleaned;
    }

    private static String currentGitCommit() {
        try {
            Process process = new ProcessBuilder().command("git", "rev-parse", "--short", "HEAD").start();
            byte[] out = process.getInputStream().readAllBytes();
            int exit = process.waitFor();
            if (exit == 0) {
                String text = new String(out).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static void writeSummary(Path txtPath,
                                     long seed,
                                     int radiusBlocks,
                                     int stepBlocks,
                                     int y,
                                     int xMin,
                                     int xMax,
                                     int zMin,
                                     int zMax,
                                     int chunkMinX,
                                     int chunkMaxX,
                                     int chunkMinZ,
                                     int chunkMaxZ,
                                     long totalChunks,
                                     int width,
                                     int height,
                                     long totalSamples,
                                     long durationMs,
                                     Map<String, Integer> biomeCounts,
                                     BiomeSamplerTools.InventoryReport inventoryReport,
                                     Path inventoryPath) throws IOException {
        List<Map.Entry<String, Integer>> top = new ArrayList<>(biomeCounts.entrySet());
        top.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());

        StringBuilder out = new StringBuilder();
        out.append("seed=").append(seed).append('\n');
        out.append("radiusBlocks=").append(radiusBlocks).append('\n');
        out.append("stepBlocks=").append(stepBlocks).append('\n');
        out.append("y=").append(y).append('\n');
        out.append("blockBounds=x[").append(xMin).append("..").append(xMax)
                .append("],z[").append(zMin).append("..").append(zMax).append("]\n");
        out.append("chunkBounds=x[").append(chunkMinX).append("..").append(chunkMaxX)
                .append("],z[").append(chunkMinZ).append("..").append(chunkMaxZ).append("]\n");
        out.append("totalChunks=").append(totalChunks).append('\n');
        out.append("image=").append(width).append('x').append(height).append('\n');
        out.append("totalSamples=").append(totalSamples).append('\n');
        out.append("durationMs=").append(durationMs).append('\n');
        if (inventoryReport != null) {
            out.append("worldBiomeInventoryCount=").append(inventoryReport.biomes().size()).append('\n');
            out.append("worldBiomeInventoryStep=").append(inventoryReport.discoveryStepUsed()).append('\n');
        }
        if (inventoryPath != null) {
            out.append("worldBiomeInventoryFile=").append(inventoryPath.getFileName()).append('\n');
        }
        out.append("topBiomes:\n");

        int limit = Math.min(TOP_BIOME_COUNT, top.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> entry = top.get(i);
            double percent = totalSamples <= 0 ? 0.0 : (entry.getValue() * 100.0) / totalSamples;
            out.append(String.format(Locale.ROOT, "%2d. %s = %d (%.2f%%)%n",
                    i + 1,
                    entry.getKey(),
                    entry.getValue(),
                    percent));
        }

        Files.writeString(txtPath, out.toString());
    }

    private static void writeLegendFiles(Path outputDir,
                                         long seed,
                                         int radiusBlocks,
                                         int stepBlocks,
                                         int y,
                                         EnumSet<Layer> layers,
                                         EnumSet<Overlay> overlays,
                                         List<BiomeMaskLayer> maskTargets,
                                         Map<Layer, Path> layerPaths,
                                         Map<BiomeMaskLayer, Path> maskPaths,
                                         Map<String, Integer> bandCounts,
                                         long totalSamples) throws IOException {
        writeLegendTxt(outputDir.resolve("legend.txt"), seed, radiusBlocks, stepBlocks, y, layers, overlays, maskTargets, layerPaths, maskPaths, bandCounts, totalSamples);
        writeLegendJson(outputDir.resolve("legend.json"), seed, radiusBlocks, stepBlocks, y, layers, overlays, maskTargets, layerPaths, maskPaths);
    }

    private static void writeLegendTxt(Path legendPath,
                                       long seed,
                                       int radiusBlocks,
                                       int stepBlocks,
                                       int y,
                                       EnumSet<Layer> layers,
                                       EnumSet<Overlay> overlays,
                                       List<BiomeMaskLayer> maskTargets,
                                       Map<Layer, Path> layerPaths,
                                       Map<BiomeMaskLayer, Path> maskPaths,
                                       Map<String, Integer> bandCounts,
                                       long totalSamples) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("seed=").append(seed).append('\n');
        out.append("radiusBlocks=").append(radiusBlocks).append('\n');
        out.append("stepBlocks=").append(stepBlocks).append('\n');
        out.append("y=").append(y).append('\n');
        out.append("layers=").append(joinLayers(layers, maskTargets)).append('\n');
        if (!maskTargets.isEmpty()) {
            out.append("maskTargets=");
            int maskIndex = 0;
            for (BiomeMaskLayer maskLayer : maskTargets) {
                if (maskIndex++ > 0) {
                    out.append(",");
                }
                out.append(maskLayer.normalizedId());
            }
            out.append('\n');
        }
        out.append("overlays=").append(joinOverlays(overlays)).append('\n');
        out.append("files:\n");
        for (Layer layer : layers) {
            Path path = layerPaths.get(layer);
            out.append("  - ").append(layer.fileStem()).append(": ")
                    .append(path != null ? path.getFileName() : "<missing>").append('\n');
        }
        for (BiomeMaskLayer maskLayer : maskTargets) {
            Path path = maskPaths.get(maskLayer);
            out.append("  - ").append(maskLayer.fileStem()).append(": ")
                    .append(path != null ? path.getFileName() : "<missing>")
                    .append(" (match=").append(maskLayer.normalizedId()).append(")\n");
        }

        if (layers.contains(Layer.BANDS)) {
            out.append("bands:\n");
            for (LatitudeBands.Band band : LatitudeBands.Band.values()) {
                appendBandLegendTxt(out, band, band.lowDeg() / 90.0, band.highDeg() / 90.0, bandCounts, totalSamples);
            }
        }

        if (layers.contains(Layer.TEMPERATURE)) {
            out.append("temperatureScale=-1.0..1.0 (blue -> cream -> red)\n");
        }
        if (layers.contains(Layer.HUMIDITY)) {
            out.append("humidityScale=-1.0..1.0 (tan -> pale -> teal)\n");
        }
        if (layers.contains(Layer.CONTINENTALNESS)) {
            out.append("continentalnessScale=-1.0..1.0 (deep ocean -> coast -> inland)\n");
        }
        if (layers.contains(Layer.RUGGEDNESS)) {
            out.append("ruggednessScale=robustDelta blocks (0-5 flat, 6-12 rolling, 13-20 hilly, 21+ rugged/mountain)\n");
        }
        if (!overlays.isEmpty()) {
            out.append("overlayStyles:\n");
            for (Overlay overlay : overlays) {
                out.append("  - ").append(overlay.token())
                        .append(" color=").append(hexColor(overlayColor(overlay)))
                        .append(" style=dashed\n");
            }
        }

        Files.writeString(legendPath, out.toString());
    }

    private static void appendBandLegendTxt(StringBuilder out,
                                            LatitudeBands.Band band,
                                            double minFrac,
                                            double maxFrac,
                                            Map<String, Integer> bandCounts,
                                            long totalSamples) {
        String key = band.id();
        int count = bandCounts.getOrDefault(key, 0);
        double pct = totalSamples <= 0 ? 0.0 : (count * 100.0) / totalSamples;
        out.append(String.format(
                Locale.ROOT,
                "  - %-11s color=%s frac=[%.3f..%.3f) count=%d (%.2f%%)%n",
                key,
                hexColor(colorForBand(band)),
                minFrac,
                maxFrac,
                count,
                pct));
    }

    private static void writeLegendJson(Path legendPath,
                                        long seed,
                                        int radiusBlocks,
                                        int stepBlocks,
                                        int y,
                                        EnumSet<Layer> layers,
                                        EnumSet<Overlay> overlays,
                                        List<BiomeMaskLayer> maskTargets,
                                        Map<Layer, Path> layerPaths,
                                        Map<BiomeMaskLayer, Path> maskPaths) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"seed\": ").append(seed).append(",\n");
        json.append("  \"radiusBlocks\": ").append(radiusBlocks).append(",\n");
        json.append("  \"stepBlocks\": ").append(stepBlocks).append(",\n");
        json.append("  \"y\": ").append(y).append(",\n");
        json.append("  \"layers\": [");
        int idx = 0;
        for (Layer layer : layers) {
            if (idx++ > 0) {
                json.append(", ");
            }
            json.append("\"").append(layer.fileStem()).append("\"");
        }
        for (BiomeMaskLayer maskLayer : maskTargets) {
            if (idx++ > 0) {
                json.append(", ");
            }
            json.append("\"").append(maskLayer.fileStem()).append("\"");
        }
        json.append("],\n");
        json.append("  \"maskTargets\": [");
        int maskIdx = 0;
        for (BiomeMaskLayer maskLayer : maskTargets) {
            if (maskIdx++ > 0) {
                json.append(", ");
            }
            json.append("\"").append(maskLayer.normalizedId()).append("\"");
        }
        json.append("],\n");
        json.append("  \"overlays\": [");
        int overlayIdx = 0;
        for (Overlay overlay : overlays) {
            if (overlayIdx++ > 0) {
                json.append(", ");
            }
            json.append("\"").append(overlay.token()).append("\"");
        }
        json.append("],\n");
        json.append("  \"files\": {\n");
        List<String> fileEntries = new ArrayList<>();
        for (Layer layer : layers) {
            Path path = layerPaths.get(layer);
            fileEntries.add("    \"" + layer.fileStem() + "\": \"" + (path != null ? path.getFileName() : "") + "\"");
        }
        for (BiomeMaskLayer maskLayer : maskTargets) {
            Path path = maskPaths.get(maskLayer);
            fileEntries.add("    \"" + maskLayer.fileStem() + "\": \"" + (path != null ? path.getFileName() : "") + "\"");
        }
        for (int i = 0; i < fileEntries.size(); i++) {
            json.append(fileEntries.get(i));
            if (i + 1 < fileEntries.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  },\n");
        json.append("  \"bands\": [\n");
        LatitudeBands.Band[] bands = LatitudeBands.Band.values();
        for (int i = 0; i < bands.length; i++) {
            LatitudeBands.Band band = bands[i];
            appendBandLegendJson(json, band, band.lowDeg() / 90.0, band.highDeg() / 90.0, i + 1 < bands.length);
        }
        json.append("  ],\n");
        json.append("  \"biomeBands\": {\n");
        List<Map.Entry<String, List<LatitudeBands.Band>>> bandEntries = new ArrayList<>(BiomeBandPolicy.policy().entrySet());
        bandEntries.sort(Map.Entry.comparingByKey());
        for (int i = 0; i < bandEntries.size(); i++) {
            Map.Entry<String, List<LatitudeBands.Band>> entry = bandEntries.get(i);
            json.append("    \"").append(entry.getKey()).append("\": [");
            List<LatitudeBands.Band> allowedBands = entry.getValue();
            for (int j = 0; j < allowedBands.size(); j++) {
                if (j > 0) {
                    json.append(", ");
                }
                json.append("\"").append(allowedBands.get(j).id()).append("\"");
            }
            json.append("]");
            if (i + 1 < bandEntries.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  },\n");
        json.append("  \"overlayStyles\": {\n");
        int overlayStyleIndex = 0;
        for (Overlay overlay : overlays) {
            json.append("    \"").append(overlay.token()).append("\": {\"color\":\"")
                    .append(hexColor(overlayColor(overlay)))
                    .append("\",\"style\":\"dashed\"}");
            if (++overlayStyleIndex < overlays.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  },\n");
        json.append("  \"temperatureScale\": \"-1.0..1.0 (blue->cream->red)\",\n");
        json.append("  \"humidityScale\": \"-1.0..1.0 (tan->pale->teal)\",\n");
        if (layers.contains(Layer.RUGGEDNESS)) {
            json.append("  \"continentalnessScale\": \"-1.0..1.0 (deep-ocean->coast->inland)\",\n");
            json.append("  \"ruggednessScale\": {\"0-5\":\"flat\",\"6-12\":\"rolling\",\"13-20\":\"hilly\",\"21+\":\"rugged/mountain\"}\n");
        } else {
            json.append("  \"continentalnessScale\": \"-1.0..1.0 (deep-ocean->coast->inland)\"\n");
        }
        json.append("}\n");
        Files.writeString(legendPath, json.toString());
    }

    private static void appendBandLegendJson(StringBuilder json,
                                             LatitudeBands.Band band,
                                             double minFrac,
                                             double maxFrac,
                                             boolean trailingComma) {
        json.append("    {\"zone\":\"").append(band.id())
                .append("\",\"color\":\"").append(hexColor(colorForBand(band)))
                .append("\",\"minFrac\":").append(String.format(Locale.ROOT, "%.6f", minFrac))
                .append(",\"maxFrac\":").append(String.format(Locale.ROOT, "%.6f", maxFrac))
                .append("}");
        if (trailingComma) {
            json.append(",");
        }
        json.append("\n");
    }

    private static void applyOverlays(Collection<BufferedImage> images,
                                      EnumSet<Overlay> overlays,
                                      int radiusBlocks,
                                      int zMin,
                                      int stepBlocks) {
        if (images.isEmpty() || overlays.isEmpty()) {
            return;
        }

        int height = images.iterator().next().getHeight();
        Map<Integer, Integer> rowColors = new HashMap<>();

        if (overlays.contains(Overlay.LAT10)) {
            for (int deg = 0; deg <= 90; deg += 10) {
                double frac = deg / 90.0;
                addHorizontalGuidesForAbsFraction(rowColors, frac, radiusBlocks, zMin, stepBlocks, height, overlayColor(Overlay.LAT10));
            }
        }

        if (overlays.contains(Overlay.BAND_EDGES)) {
            double[] edges = {
                    0.0,
                    LatitudeBands.Band.SUBTROPICAL.lowDeg() / 90.0,
                    LatitudeBands.Band.TEMPERATE.lowDeg() / 90.0,
                    LatitudeBands.Band.SUBPOLAR.lowDeg() / 90.0,
                    LatitudeBands.Band.POLAR.lowDeg() / 90.0,
                    1.0
            };
            for (double frac : edges) {
                addHorizontalGuidesForAbsFraction(rowColors, frac, radiusBlocks, zMin, stepBlocks, height, overlayColor(Overlay.BAND_EDGES));
            }
        }

        if (rowColors.isEmpty()) {
            return;
        }

        for (BufferedImage image : images) {
            drawOverlayRows(image, rowColors);
        }
    }

    private static void addHorizontalGuidesForAbsFraction(Map<Integer, Integer> rowColors,
                                                          double absFraction,
                                                          int radiusBlocks,
                                                          int zMin,
                                                          int stepBlocks,
                                                          int height,
                                                          int color) {
        int absZ = (int) Math.round(Mth.clamp(absFraction, 0.0, 1.0) * radiusBlocks);
        addGuideRow(rowColors, absZ, zMin, stepBlocks, height, color);
        if (absZ != 0) {
            addGuideRow(rowColors, -absZ, zMin, stepBlocks, height, color);
        }
    }

    private static void addGuideRow(Map<Integer, Integer> rowColors,
                                    int zBlocks,
                                    int zMin,
                                    int stepBlocks,
                                    int height,
                                    int color) {
        int row = (int) Math.round((zBlocks - zMin) / (double) stepBlocks);
        row = Mth.clamp(row, 0, Math.max(0, height - 1));
        rowColors.put(row, color);
    }

    private static void drawOverlayRows(BufferedImage image, Map<Integer, Integer> rowColors) {
        int width = image.getWidth();
        for (Map.Entry<Integer, Integer> entry : rowColors.entrySet()) {
            int row = entry.getKey();
            int color = entry.getValue();
            for (int x = 0; x < width; x++) {
                int rgb = (x & 1) == 0 ? color : 0x000000;
                image.setRGB(x, row, rgb);
            }
        }
    }

    private static int overlayColor(Overlay overlay) {
        return switch (overlay) {
            case LAT10 -> 0xF6C945;
            case BAND_EDGES -> 0xFF4FD8;
        };
    }

    private static String joinLayers(EnumSet<Layer> layers, List<BiomeMaskLayer> maskLayers) {
        if ((layers == null || layers.isEmpty()) && (maskLayers == null || maskLayers.isEmpty())) {
            return "none";
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        for (Layer layer : layers) {
            if (i++ > 0) {
                out.append(",");
            }
            out.append(layer.fileStem());
        }
        if (maskLayers != null) {
            for (BiomeMaskLayer maskLayer : maskLayers) {
                if (i++ > 0) {
                    out.append(",");
                }
                out.append(maskLayer.token());
            }
        }
        return out.toString();
    }

    private static String joinOverlays(EnumSet<Overlay> overlays) {
        if (overlays == null || overlays.isEmpty()) {
            return "none";
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        for (Overlay overlay : overlays) {
            if (i++ > 0) {
                out.append(",");
            }
            out.append(overlay.token());
        }
        return out.toString();
    }

    private static LatitudeBands.Band bandForBlockZ(int radiusBlocks, int blockZ) {
        if (radiusBlocks <= 0) {
            return LatitudeBands.Band.TROPICAL;
        }
        double absLatDeg = Math.abs((double) blockZ) * 90.0 / (double) radiusBlocks;
        return LatitudeBands.fromAbsoluteLatitudeDeg(absLatDeg);
    }

    private static double normalizeNoise(float noiseValue) {
        return Mth.clamp((noiseValue + 1.0) * 0.5, 0.0, 1.0);
    }

    private static int colorForBand(LatitudeBands.Band band) {
        return switch (band) {
            case TROPICAL -> 0xE8703E;
            case SUBTROPICAL -> 0xE8B63E;
            case TEMPERATE -> 0x7EC460;
            case SUBPOLAR -> 0x60A0DC;
            case POLAR -> 0xB4C8E8;
        };
    }

    private static int colorForBandIndex(int bandIndex) {
        return switch (bandIndex) {
            case 0 -> 0x7A4A28;
            case 1 -> 0xF5A623;
            case 2 -> 0x3FAF5A;
            case 3 -> 0x3D7FC7;
            case 4 -> 0xB7C8E5;
            default -> 0x4A4A4A;
        };
    }

    private static double latitudeDegreesForBlockZ(int radiusBlocks, int blockZ) {
        int safeRadius = Math.max(1, radiusBlocks);
        return Math.min(90.0, (Math.abs((double) blockZ) * 90.0) / (double) safeRadius);
    }

    private static boolean isWarmDryBiomeId(String biomeId) {
        if (biomeId == null) return false;
        return biomeId.contains("savanna")
                || biomeId.contains("desert")
                || biomeId.contains("badlands");
    }

    private static boolean isTemperateBiomeId(String biomeId) {
        if (biomeId == null) return false;
        return biomeId.contains("forest")
                || biomeId.contains("plains")
                || biomeId.contains("meadow")
                || biomeId.contains("hills")
                || biomeId.contains("stony_peaks")
                || biomeId.contains("cherry_grove")
                || biomeId.contains("taiga");
    }

    private static void writeSeamRowSummary(Path summaryPath, Map<Integer, SeamRowSummary> seamRows) throws IOException {
        if (seamRows == null || seamRows.isEmpty()) {
            Files.writeString(summaryPath, "no seam rows in 32..38 latitude window\n");
            return;
        }
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "seam_rows_window=%.1f..%.1f%n", SEAM_LAT_MIN_DEG, SEAM_LAT_MAX_DEG));
        for (Map.Entry<Integer, SeamRowSummary> entry : seamRows.entrySet()) {
            int z = entry.getKey();
            SeamRowSummary row = entry.getValue();
            out.append(String.format(Locale.ROOT,
                    "z=%d latDeg=%.3f chosenSub=%d chosenTemp=%d landSub=%d landTemp=%d finalWarmDry=%d finalTemperate=%d%n",
                    z,
                    row.latDeg,
                    row.chosenSub,
                    row.chosenTemp,
                    row.landSub,
                    row.landTemp,
                    row.finalWarmDry,
                    row.finalTemperate));
        }
        Files.writeString(summaryPath, out.toString());
    }

    private static void writeSeamCropArtifacts(Path outputDir,
                                               BufferedImage biomesImage,
                                               BufferedImage chosenBandsImage,
                                               BufferedImage landBandsImage,
                                               int radiusBlocks,
                                               int zMin,
                                               int stepBlocks) throws IOException {
        if (chosenBandsImage == null || landBandsImage == null) {
            return;
        }
        List<Integer> seamRows = collectSeamRowIndices(chosenBandsImage.getHeight(), radiusBlocks, zMin, stepBlocks);
        BufferedImage seamCropBiomes = buildSeamCropImage(biomesImage, chosenBandsImage.getWidth(), seamRows);
        BufferedImage seamCropChosen = buildSeamCropImage(chosenBandsImage, chosenBandsImage.getWidth(), seamRows);
        BufferedImage seamCropLand = buildSeamCropImage(landBandsImage, landBandsImage.getWidth(), seamRows);

        Path seamBiomesPath = outputDir.resolve("seam_crop_biomes.png");
        if (!ImageIO.write(seamCropBiomes, "png", seamBiomesPath.toFile())) {
            throw new IOException("PNG writer unavailable for seam_crop_biomes");
        }
        Path seamChosenPath = outputDir.resolve("seam_crop_chosen_bands.png");
        if (!ImageIO.write(seamCropChosen, "png", seamChosenPath.toFile())) {
            throw new IOException("PNG writer unavailable for seam_crop_chosen_bands");
        }
        Path seamLandPath = outputDir.resolve("seam_crop_land_bands.png");
        if (!ImageIO.write(seamCropLand, "png", seamLandPath.toFile())) {
            throw new IOException("PNG writer unavailable for seam_crop_land_bands");
        }

        writeSeamBandLegend(outputDir.resolve("seam_band_legend.txt"));
        writeSeamRowMarkers(outputDir.resolve("seam_row_markers.txt"), radiusBlocks, zMin, stepBlocks, chosenBandsImage.getHeight());
    }

    private static List<Integer> collectSeamRowIndices(int imageHeight,
                                                        int radiusBlocks,
                                                        int zMin,
                                                        int stepBlocks) {
        List<Integer> rows = new ArrayList<>();
        for (int imageZ = 0; imageZ < imageHeight; imageZ++) {
            int blockZ = zMin + (imageZ * stepBlocks);
            double latDeg = latitudeDegreesForBlockZ(radiusBlocks, blockZ);
            if (latDeg >= SEAM_LAT_MIN_DEG && latDeg <= SEAM_LAT_MAX_DEG) {
                rows.add(imageZ);
            }
        }
        return rows;
    }

    private static BufferedImage buildSeamCropImage(BufferedImage sourceImage, int fallbackWidth, List<Integer> seamRows) {
        int width = sourceImage != null ? sourceImage.getWidth() : Math.max(1, fallbackWidth);
        int rowCount = Math.max(1, seamRows.size());
        BufferedImage cropped = new BufferedImage(width, rowCount, BufferedImage.TYPE_INT_RGB);
        if (sourceImage == null || seamRows.isEmpty()) {
            return cropped;
        }
        int[] rowBuffer = new int[width];
        for (int dstY = 0; dstY < seamRows.size(); dstY++) {
            int srcY = seamRows.get(dstY);
            sourceImage.getRGB(0, srcY, width, 1, rowBuffer, 0, width);
            cropped.setRGB(0, dstY, width, 1, rowBuffer, 0, width);
        }
        return cropped;
    }

    private static void writeSeamBandLegend(Path legendPath) throws IOException {
        String out = String.format(Locale.ROOT,
                "seam_band_palette%n"
                        + "tropical=#%06X%n"
                        + "subtropical=#%06X%n"
                        + "temperate=#%06X%n"
                        + "subpolar=#%06X%n"
                        + "polar=#%06X%n",
                colorForBandIndex(0) & 0x00FFFFFF,
                colorForBandIndex(1) & 0x00FFFFFF,
                colorForBandIndex(2) & 0x00FFFFFF,
                colorForBandIndex(3) & 0x00FFFFFF,
                colorForBandIndex(4) & 0x00FFFFFF);
        Files.writeString(legendPath, out);
    }

    private static void writeSeamRowMarkers(Path markerPath,
                                            int radiusBlocks,
                                            int zMin,
                                            int stepBlocks,
                                            int imageHeight) throws IOException {
        double[] targets = new double[]{35.00, 35.28, 34.99};
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT,
                "seam_row_markers latWindow=%.1f..%.1f stepBlocks=%d%n",
                SEAM_LAT_MIN_DEG,
                SEAM_LAT_MAX_DEG,
                stepBlocks));
        for (double targetLat : targets) {
            appendRowMarker(out, radiusBlocks, zMin, stepBlocks, imageHeight, targetLat, -1);
            appendRowMarker(out, radiusBlocks, zMin, stepBlocks, imageHeight, targetLat, 1);
        }
        Files.writeString(markerPath, out.toString());
    }

    private static void appendRowMarker(StringBuilder out,
                                        int radiusBlocks,
                                        int zMin,
                                        int stepBlocks,
                                        int imageHeight,
                                        double targetLat,
                                        int hemiSign) {
        int targetBlockZ = (int) Math.round((targetLat / 90.0) * radiusBlocks) * hemiSign;
        int imageZ = Math.round((targetBlockZ - zMin) / (float) stepBlocks);
        boolean inBounds = imageZ >= 0 && imageZ < imageHeight;
        int snappedBlockZ = zMin + (imageZ * stepBlocks);
        double snappedLat = latitudeDegreesForBlockZ(radiusBlocks, snappedBlockZ);
        String hemi = hemiSign < 0 ? "north" : "south";
        out.append(String.format(Locale.ROOT,
                "targetLat=%.2f hemi=%s targetBlockZ=%d imageZ=%d inBounds=%s snappedBlockZ=%d snappedLat=%.3f%n",
                targetLat,
                hemi,
                targetBlockZ,
                imageZ,
                inBounds ? "true" : "false",
                snappedBlockZ,
                snappedLat));
    }

    private static boolean isTemperateShoulderProfileRow(double latDeg, int landBandIndex) {
        return landBandIndex == BAND_INDEX_TEMPERATE
                && latDeg >= TEMPERATE_SHOULDER_MIN_DEG
                && latDeg <= TEMPERATE_SHOULDER_MAX_DEG;
    }

    private static void writeTemperateShoulderComposition(Path outputPath,
                                                          Map<Integer, TemperateShoulderCompositionRow> rows) throws IOException {
        if (rows == null || rows.isEmpty()) {
            Files.writeString(outputPath, String.format(Locale.ROOT,
                    "no snapped temperate rows in %.2f..%.2f latitude window%n",
                    TEMPERATE_SHOULDER_MIN_DEG,
                    TEMPERATE_SHOULDER_MAX_DEG));
            return;
        }
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT,
                "temperate_shoulder_window=%.2f..%.2f (snapped temperate rows only)%n",
                TEMPERATE_SHOULDER_MIN_DEG,
                TEMPERATE_SHOULDER_MAX_DEG));
        for (Map.Entry<Integer, TemperateShoulderCompositionRow> entry : rows.entrySet()) {
            int z = entry.getKey();
            TemperateShoulderCompositionRow row = entry.getValue();
            out.append(String.format(Locale.ROOT,
                    "z=%d latDeg=%.3f total=%d finalTemperate=%d warmDry=%d dominant=%s%n",
                    z,
                    row.latDeg,
                    row.total,
                    row.finalTemperate,
                    row.warmDry,
                    row.dominantBiomesLabel()));
            out.append("  keyCounts:");
            for (String biomeId : TEMPERATE_SHOULDER_KEY_BIOMES) {
                int count = row.biomeCounts.getOrDefault(biomeId, 0);
                out.append(' ').append(shortBiomeId(biomeId)).append('=').append(count);
            }
            out.append('\n');
            out.append("  otherTop: ").append(row.otherTopBiomesLabel()).append('\n');
        }
        Files.writeString(outputPath, out.toString());
    }

    private static String shortBiomeId(String biomeId) {
        if (biomeId == null) {
            return "unknown";
        }
        int idx = biomeId.indexOf(':');
        return idx >= 0 && idx + 1 < biomeId.length() ? biomeId.substring(idx + 1) : biomeId;
    }

    private static final class SeamRowSummary {
        private final double latDeg;
        private int chosenSub;
        private int chosenTemp;
        private int landSub;
        private int landTemp;
        private int finalWarmDry;
        private int finalTemperate;

        private SeamRowSummary(double latDeg) {
            this.latDeg = latDeg;
        }

        private void addChosen(int bandIndex) {
            if (bandIndex == 1) {
                chosenSub++;
            } else if (bandIndex == 2) {
                chosenTemp++;
            }
        }

        private void addLand(int bandIndex) {
            if (bandIndex == 1) {
                landSub++;
            } else if (bandIndex == 2) {
                landTemp++;
            }
        }
    }

    private static final class TemperateShoulderCompositionRow {
        private final double latDeg;
        private int total;
        private int finalTemperate;
        private int warmDry;
        private final Map<String, Integer> biomeCounts = new HashMap<>();

        private TemperateShoulderCompositionRow(double latDeg) {
            this.latDeg = latDeg;
        }

        private void addSample(String biomeId) {
            total++;
            String key = biomeId != null ? biomeId : "unknown";
            biomeCounts.merge(key, 1, Integer::sum);
            if (isTemperateBiomeId(biomeId)) {
                finalTemperate++;
            }
            if (isWarmDryBiomeId(biomeId)) {
                warmDry++;
            }
        }

        private String dominantBiomesLabel() {
            if (biomeCounts.isEmpty()) {
                return "none";
            }
            int max = 0;
            for (int count : biomeCounts.values()) {
                if (count > max) {
                    max = count;
                }
            }
            List<String> dominant = new ArrayList<>();
            for (Map.Entry<String, Integer> e : biomeCounts.entrySet()) {
                if (e.getValue() == max) {
                    dominant.add(String.format(Locale.ROOT, "%s(%d,%.1f%%)",
                            shortBiomeId(e.getKey()),
                            e.getValue(),
                            total > 0 ? (100.0 * e.getValue() / (double) total) : 0.0));
                }
            }
            dominant.sort(String::compareTo);
            return String.join(",", dominant);
        }

        private String otherTopBiomesLabel() {
            List<Map.Entry<String, Integer>> others = new ArrayList<>();
            for (Map.Entry<String, Integer> e : biomeCounts.entrySet()) {
                if (!TEMPERATE_SHOULDER_KEY_BIOMES.contains(e.getKey())) {
                    others.add(e);
                }
            }
            if (others.isEmpty()) {
                return "none";
            }
            others.sort((a, b) -> {
                int byCount = Integer.compare(b.getValue(), a.getValue());
                return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
            });
            int limit = Math.min(5, others.size());
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                if (i > 0) out.append(',');
                Map.Entry<String, Integer> e = others.get(i);
                double pct = total > 0 ? (100.0 * e.getValue() / (double) total) : 0.0;
                out.append(shortBiomeId(e.getKey()))
                        .append('(')
                        .append(e.getValue())
                        .append(',')
                        .append(String.format(Locale.ROOT, "%.1f%%", pct))
                        .append(')');
            }
            return out.toString();
        }
    }

    private static int colorForTemperature(double value01) {
        double t = Mth.clamp(value01, 0.0, 1.0);
        if (t < 0.5) {
            return lerpRgb(0x2C7BB6, 0xFFF3B0, t * 2.0);
        }
        return lerpRgb(0xFFF3B0, 0xD7191C, (t - 0.5) * 2.0);
    }

    private static int colorForHumidity(double value01) {
        double t = Mth.clamp(value01, 0.0, 1.0);
        if (t < 0.5) {
            return lerpRgb(0xB07D3B, 0xE9E7C5, t * 2.0);
        }
        return lerpRgb(0xE9E7C5, 0x2A9D8F, (t - 0.5) * 2.0);
    }

    private static int colorForContinentalness(double valueSigned) {
        double c = Mth.clamp(valueSigned, -1.0, 1.0);
        if (c < 0.0) {
            return lerpRgb(0x0B3D91, 0xE0C097, c + 1.0);
        }
        return lerpRgb(0xE0C097, 0x6B8E23, c);
    }

    private static int colorForRuggedness(int robustDelta) {
        // 4-bin encoding; 0xFF__ prefix ensures opaque alpha in ARGB contexts.
        if (robustDelta <= 5)  return 0xFF8BC34A;  // flat    — light green
        if (robustDelta <= 12) return 0xFFFFB74D;  // rolling — amber
        if (robustDelta <= 20) return 0xFFFF7043;  // hilly   — deep orange
        return                        0xFFAB47BC;  // rugged  — purple
    }

    private static int lerpRgb(int from, int to, double t) {
        double clamped = Mth.clamp(t, 0.0, 1.0);
        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;

        int r = (int) Math.round(fromR + (toR - fromR) * clamped);
        int g = (int) Math.round(fromG + (toG - fromG) * clamped);
        int b = (int) Math.round(fromB + (toB - fromB) * clamped);
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static String hexColor(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0x00FFFFFF);
    }

    private static String biomeId(Registry<Biome> biomeRegistry, Holder<Biome> biome) {
        Identifier id = biomeRegistry.getKey(biome.value());
        if (id != null) {
            return id.toString();
        }
        return biome.unwrapKey().map(key -> key.identifier().toString()).orElse("minecraft:plains");
    }

    private static Integer paletteOverrideFor(String biomeId) {
        if (biomeId == null) {
            return null;
        }
        String normalized = biomeId.toLowerCase(Locale.ROOT);
        Integer override = PALETTE_OVERRIDES.get(normalized);
        if (override != null) {
            return override;
        }
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            String shortId = normalized.substring(colon + 1);
            override = PALETTE_OVERRIDES.get(shortId);
            if (override != null) {
                return override;
            }
        }
        return null;
    }

    static int stableColorForBiomeId(String biomeId) {
        String id = biomeId.toLowerCase(Locale.ROOT);
        Integer override = paletteOverrideFor(id);
        if (override != null) {
            return override;
        }
        if (id.contains("snowy_beach")) {
            return 0xE9E1CC;
        }
        if (id.contains("stony_shore")) {
            return 0x9A9A9A;
        }
        if (id.contains("beach") || id.contains("shore") || id.contains("coast")) {
            return 0xE7D7A5;
        }
        if (id.contains("ocean") || id.contains("river")) {
            return 0x2F6FA8;
        }
        if (id.contains("snow") || id.contains("frozen") || id.contains("ice")) {
            return 0xE6F4FF;
        }
        if (id.contains("desert") || id.contains("badlands")) {
            return 0xD39B4D;
        }
        if (id.contains("swamp") || id.contains("mangrove")) {
            return 0x3C6B43;
        }
        if (id.contains("jungle")) {
            return 0x2E8A57;
        }
        if (id.contains("taiga") || id.contains("forest") || id.contains("grove")) {
            return 0x4A7B4D;
        }
        if (id.contains("plains") || id.contains("savanna") || id.contains("meadow")) {
            return 0x8FBF63;
        }
        if (id.contains("mountain") || id.contains("peak") || id.contains("hills")) {
            return 0x7A7A7A;
        }

        // Neutral fallback for unknown/custom biomes.
        return 0x8A8A8A;
    }

    private static int inventoryDiscoveryStep(int stepBlocks) {
        int configured = Integer.getInteger("latitude.atlas.inventoryStep", DEFAULT_INVENTORY_DISCOVERY_STEP);
        int clamped = Mth.clamp(configured, 8, 512);
        return Math.min(Math.max(1, stepBlocks), clamped);
    }

    private static String normalizeMaskToken(String raw) {
        if (raw == null) {
            return "";
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        if (token.startsWith("biomemask:")) {
            token = token.substring("biomemask:".length()).trim();
        }
        if (token.startsWith("mask=")) {
            token = token.substring("mask=".length()).trim();
        }
        return token;
    }

    private static String maskFileStem(String normalizedToken) {
        String safe = normalizedToken
                .replace(':', '_')
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (safe.isEmpty()) {
            safe = "unknown";
        }
        return "mask_" + safe;
    }

    private static boolean biomeMatchesMask(String biomeId, String normalizedMaskToken) {
        if (biomeId == null || normalizedMaskToken == null || normalizedMaskToken.isEmpty()) {
            return false;
        }
        String candidate = biomeId.trim().toLowerCase(Locale.ROOT);
        if (candidate.isEmpty()) {
            return false;
        }
        if (normalizedMaskToken.contains(":")) {
            return candidate.equals(normalizedMaskToken);
        }
        if (candidate.equals(normalizedMaskToken) || candidate.endsWith(":" + normalizedMaskToken)) {
            return true;
        }
        int colon = candidate.indexOf(':');
        String path = colon >= 0 ? candidate.substring(colon + 1) : candidate;
        return path.equals(normalizedMaskToken) || path.contains(normalizedMaskToken);
    }

    private static boolean isMangroveMaskLayer(BiomeMaskLayer maskLayer) {
        if (maskLayer == null || maskLayer.normalizedId() == null) {
            return false;
        }
        String token = maskLayer.normalizedId();
        return token.equals("mangrove")
                || token.equals("mangrove_swamp")
                || token.equals("minecraft:mangrove_swamp");
    }

    private static Holder<Biome> forceMangroveSwampForAtlas(Registry<Biome> biomeRegistry, Holder<Biome> fallback) {
        Holder.Reference<Biome> mangrove = biomeRegistry.get(MANGROVE_SWAMP_BIOME_ID).orElse(null);
        return mangrove != null ? mangrove : fallback;
    }

    public record BiomeMaskLayer(String normalizedId, String fileStem) {
        public static BiomeMaskLayer fromToken(String raw) {
            String normalized = normalizeMaskToken(raw);
            if (normalized.isEmpty()) {
                return null;
            }
            return new BiomeMaskLayer(normalized, maskFileStem(normalized));
        }

        public boolean matches(String biomeId) {
            return biomeMatchesMask(biomeId, normalizedId);
        }

        public String token() {
            return "biomeMask:" + normalizedId;
        }
    }

    public enum Layer {
        BIOMES("biomes"),
        BANDS("bands"),
        TEMPERATURE("temperature"),
        HUMIDITY("humidity"),
        CONTINENTALNESS("continentalness"),
        RUGGEDNESS("ruggedness");

        private final String fileStem;

        Layer(String fileStem) {
            this.fileStem = fileStem;
        }

        public String fileStem() {
            return fileStem;
        }

        public static Layer fromToken(String raw) {
            if (raw == null) {
                return null;
            }
            String token = raw.trim().toLowerCase(Locale.ROOT);
            return switch (token) {
                case "biome", "biomes" -> BIOMES;
                case "band", "bands", "latitude", "latbands" -> BANDS;
                case "temperature", "temp" -> TEMPERATURE;
                case "humidity", "humid", "moisture" -> HUMIDITY;
                case "continentalness", "continental", "cont" -> CONTINENTALNESS;
                case "ruggedness", "rugged" -> RUGGEDNESS;
                default -> null;
            };
        }
    }

    public enum Overlay {
        LAT10("lat10"),
        BAND_EDGES("bandEdges");

        private final String token;

        Overlay(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        public static Overlay fromToken(String raw) {
            if (raw == null) {
                return null;
            }
            String token = raw.trim().toLowerCase(Locale.ROOT);
            return switch (token) {
                case "lat10", "lat_10", "latitude10", "lat-lines-10" -> LAT10;
                case "bandedges", "band_edges", "band-edges", "bands" -> BAND_EDGES;
                default -> null;
            };
        }
    }

    public record ExportOptions(EnumSet<Layer> layers,
                                EnumSet<Overlay> overlays,
                                List<BiomeMaskLayer> maskLayers,
                                boolean writeLegends,
                                boolean emitBiomeIndex,
                                boolean emitHeight) {
        public ExportOptions {
            if (layers == null) {
                layers = EnumSet.of(Layer.BIOMES);
            } else if (layers.isEmpty()) {
                layers = EnumSet.noneOf(Layer.class);
            } else {
                layers = EnumSet.copyOf(layers);
            }
            if (overlays == null || overlays.isEmpty()) {
                overlays = EnumSet.noneOf(Overlay.class);
            } else {
                overlays = EnumSet.copyOf(overlays);
            }
            if (maskLayers == null || maskLayers.isEmpty()) {
                maskLayers = List.of();
            } else {
                LinkedHashMap<String, BiomeMaskLayer> deduped = new LinkedHashMap<>();
                for (BiomeMaskLayer maskLayer : maskLayers) {
                    if (maskLayer != null && !maskLayer.normalizedId().isEmpty()) {
                        deduped.putIfAbsent(maskLayer.normalizedId(), maskLayer);
                    }
                }
                maskLayers = List.copyOf(deduped.values());
            }
        }

        public static ExportOptions singleBiome() {
            return new ExportOptions(EnumSet.of(Layer.BIOMES), EnumSet.noneOf(Overlay.class), List.of(), false, false, false);
        }

        public static ExportOptions bundle() {
            return new ExportOptions(
                    EnumSet.of(Layer.BIOMES, Layer.BANDS, Layer.TEMPERATURE, Layer.HUMIDITY, Layer.CONTINENTALNESS),
                    EnumSet.noneOf(Overlay.class),
                    List.of(),
                    true,
                    false,
                    false);
        }

        public static ExportOptions from(boolean bundle,
                                         List<Layer> requestedLayers,
                                         List<Overlay> requestedOverlays,
                                         List<String> requestedMasks) {
            EnumSet<Overlay> overlaySet = requestedOverlays == null || requestedOverlays.isEmpty()
                    ? EnumSet.noneOf(Overlay.class)
                    : EnumSet.copyOf(requestedOverlays);
            List<BiomeMaskLayer> maskLayers = parseMaskLayers(requestedMasks);
            boolean hasRequestedLayers = requestedLayers != null && !requestedLayers.isEmpty();
            if (hasRequestedLayers || !maskLayers.isEmpty()) {
                EnumSet<Layer> layerSet = hasRequestedLayers
                        ? EnumSet.copyOf(requestedLayers)
                        : EnumSet.noneOf(Layer.class);
                boolean legends = bundle || layerSet.size() > 1 || !maskLayers.isEmpty();
                return new ExportOptions(layerSet, overlaySet, maskLayers, legends, false, false);
            }
            ExportOptions base = bundle ? bundle() : singleBiome();
            return new ExportOptions(base.layers(), overlaySet, base.maskLayers(), base.writeLegends(), false, false);
        }

        private static List<BiomeMaskLayer> parseMaskLayers(List<String> requestedMasks) {
            if (requestedMasks == null || requestedMasks.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<BiomeMaskLayer> out = new LinkedHashSet<>();
            for (String raw : requestedMasks) {
                BiomeMaskLayer parsed = BiomeMaskLayer.fromToken(raw);
                if (parsed != null) {
                    out.add(parsed);
                }
            }
            return new ArrayList<>(out);
        }

        public String layerCsv() {
            return joinLayers(layers, maskLayers);
        }

        public String overlayCsv() {
            return joinOverlays(overlays);
        }
    }

    public record ExportResult(Path pngPath,
                               Path txtPath,
                               long seed,
                               int radiusBlocks,
                               int stepBlocks,
                               int width,
                               int height,
                               long totalSamples,
                               long durationMs) {
    }
}
