package com.example.globe.dev;

import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BiomePreviewExporter {
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final int TOP_BIOME_COUNT = 20;
    private static final int MASK_MATCH_COLOR = 0xF2F5F8;
    private static final int MASK_MISS_COLOR = 0x11161B;
    private static final long DEFAULT_BUDGET_MS = 10L;
    private static final int DEFAULT_INVENTORY_DISCOVERY_STEP = 32;
    private static final Identifier MANGROVE_SWAMP_BIOME_ID = Identifier.of("minecraft:mangrove_swamp");

    private BiomePreviewExporter() {
    }

    public static ExportResult export(ServerWorld world,
                                      int radiusBlocks,
                                      int stepBlocks,
                                      int y,
                                      Path runDirectory) throws IOException {
        return export(world, radiusBlocks, stepBlocks, y, runDirectory, world.getSeed(), ExportOptions.singleBiome());
    }

    public static ExportResult export(ServerWorld world,
                                      int radiusBlocks,
                                      int stepBlocks,
                                      int y,
                                      Path runDirectory,
                                      ExportOptions options) throws IOException {
        return export(world, radiusBlocks, stepBlocks, y, runDirectory, world.getSeed(), options);
    }

    public static ExportResult export(ServerWorld world,
                                      int radiusBlocks,
                                      int stepBlocks,
                                      int y,
                                      Path runDirectory,
                                      long atlasSeed,
                                      ExportOptions options) throws IOException {
        long startNanos = System.nanoTime();
        ExportOptions effectiveOptions = options != null ? options : ExportOptions.singleBiome();
        EnumSet<Layer> layers = effectiveOptions.layers();
        EnumSet<Overlay> overlays = effectiveOptions.overlays();
        List<BiomeMaskLayer> maskTargets = effectiveOptions.maskLayers();
        boolean emitBiomeIndex = effectiveOptions.emitBiomeIndex();
        boolean emitHeight = effectiveOptions.emitHeight();

        // Batched path for emitHeight to avoid long single ticks. Caller should drive processBudget() across ticks.
        if (emitHeight) {
            HeightStepProcessor processor = HeightStepProcessor.create(
                    world,
                    radiusBlocks,
                    stepBlocks,
                    y,
                    runDirectory,
                    atlasSeed,
                    effectiveOptions);
            long budgetMs = Math.max(1L, Long.getLong("latitude.atlas.heightBudgetMs", DEFAULT_BUDGET_MS));
            return processor.processBudget(budgetMs);
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
        boolean renderBiomeMasks = !maskTargets.isEmpty();
        boolean needsBiomeSampling = renderBiomes || renderBiomeMasks || emitBiomeIndex;

        ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
        BiomeSource biomeSource = generator.getBiomeSource();
        BiomeSource baseSource = biomeSource instanceof LatitudeBiomeSource latitudeSource
                ? latitudeSource.original()
                : biomeSource;
        Registry<Biome> biomeRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
        NoiseConfig noiseConfig = NoiseConfig.create(
                ((net.minecraft.world.gen.chunk.NoiseChunkGenerator) generator).getSettings().value(),
                world.getRegistryManager().getOrThrow(RegistryKeys.NOISE_PARAMETERS),
                atlasSeed);
        MultiNoiseUtil.MultiNoiseSampler sampler = noiseConfig.getMultiNoiseSampler();
        net.minecraft.world.gen.chunk.NoiseChunkGenerator noiseGen =
                generator instanceof net.minecraft.world.gen.chunk.NoiseChunkGenerator ng ? ng : null;
        // Lightweight surface stub for atlas mode: keep sea level from the generator,
        // but skip expensive height probing by leaving noiseConfig/heightView unset.
        net.minecraft.world.gen.chunk.NoiseChunkGenerator gatedNoiseGen = noiseGen;
        NoiseConfig gatedNoiseConfig = null;
        net.minecraft.world.HeightLimitView gatedHeightView = null;

        int noiseY = Math.floorDiv(y, 4);
        for (int imageZ = 0, blockZ = zMin; imageZ < height; imageZ++, blockZ += stepBlocks) {
            int noiseZ = Math.floorDiv(blockZ, 4);
            for (int imageX = 0, blockX = xMin; imageX < width; imageX++, blockX += stepBlocks) {
                int noiseX = Math.floorDiv(blockX, 4);

                String sampledBiomeId = null;
                if (needsBiomeSampling) {
                    RegistryEntry<Biome> base = baseSource.getBiome(noiseX, noiseY, noiseZ, sampler);
                    RegistryEntry<Biome> picked = LatitudeBiomes.pick(
                            biomeRegistry,
                            base,
                            blockX,
                            blockZ,
                            y,
                            radiusBlocks,
                            sampler,
                            "SOURCE",
                            gatedNoiseGen,
                            gatedNoiseConfig,
                            gatedHeightView);
                    RegistryEntry<Biome> out = picked != null ? picked : base;
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

                if (renderBands) {
                    LatitudeBands.Band band = bandForBlockZ(radiusBlocks, blockZ);
                    images.get(Layer.BANDS).setRGB(imageX, imageZ, colorForBand(band));
                    bandCounts.merge(band.id(), 1, Integer::sum);
                }

                if (renderTemperature || renderHumidity || renderContinentalness) {
                    MultiNoiseUtil.NoiseValuePoint point = sampler.sample(noiseX, noiseY, noiseZ);
                    if (renderTemperature) {
                        double temperature01 = normalizeNoise(MultiNoiseUtil.toFloat(point.temperatureNoise()));
                        images.get(Layer.TEMPERATURE).setRGB(imageX, imageZ, colorForTemperature(temperature01));
                    }
                    if (renderHumidity) {
                        double humidity01 = normalizeNoise(MultiNoiseUtil.toFloat(point.humidityNoise()));
                        images.get(Layer.HUMIDITY).setRGB(imageX, imageZ, colorForHumidity(humidity01));
                    }
                    if (renderContinentalness) {
                        double continentalness = MathHelper.clamp(MultiNoiseUtil.toFloat(point.continentalnessNoise()), -1.0, 1.0);
                        images.get(Layer.CONTINENTALNESS).setRGB(imageX, imageZ, colorForContinentalness(continentalness));
                    }
                }
            }
        }

        if (!overlays.isEmpty()) {
            applyOverlays(images.values(), overlays, radiusBlocks, zMin, stepBlocks);
            applyOverlays(maskImages.values(), overlays, radiusBlocks, zMin, stepBlocks);
        }

        long seed = atlasSeed;
        Path outputDir = atlasStepDirectory(defaultAtlasRoot(runDirectory), seed, radiusBlocks, stepBlocks);
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
        if (emitBiomeIndex && biomeIndexImage != null) {
            biomeIndexPath = outputDir.resolve("biome_ids.png");
            boolean wrote = ImageIO.write(biomeIndexImage, "png", biomeIndexPath.toFile());
            if (!wrote) {
                throw new IOException("PNG writer unavailable for biome_ids");
            }
            writeBiomePalette(outputDir.resolve("biome_palette.json"), biomeIndices);
        }

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
        private final ServerWorld world;
        private final int radiusBlocks;
        private final int stepBlocks;
        private final int y;
        private final Path runDirectory;
        private final long atlasSeed;
        private final ExportOptions options;

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

        private final Map<String, Integer> biomeCounts = new HashMap<>();
        private final Map<String, Integer> biomeColors = new HashMap<>();
        private final Map<String, Integer> biomeIndices = new LinkedHashMap<>();
        private final Map<String, Integer> bandCounts = new HashMap<>();

        private final ChunkGenerator generator;
        private final BiomeSource baseSource;
        private final Registry<Biome> biomeRegistry;
        private final NoiseConfig noiseConfig;
        private final MultiNoiseUtil.MultiNoiseSampler sampler;
        private final net.minecraft.world.gen.chunk.NoiseChunkGenerator gatedNoiseGen;
        private final NoiseConfig gatedNoiseConfig;
        private final net.minecraft.world.HeightLimitView gatedHeightView;
        private final int noiseY;

        private int imageX = 0;
        private int imageZ = 0;
        private final long startNanos;
        private ExportResult result;

        private HeightStepProcessor(ServerWorld world,
                                     int radiusBlocks,
                                     int stepBlocks,
                                     int y,
                                     Path runDirectory,
                                     long atlasSeed,
                                     ExportOptions options) {
            this.world = world;
            this.radiusBlocks = radiusBlocks;
            this.stepBlocks = stepBlocks;
            this.y = y;
            this.runDirectory = runDirectory;
            this.atlasSeed = atlasSeed;
            this.options = options != null ? options : ExportOptions.singleBiome();
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

            this.generator = world.getChunkManager().getChunkGenerator();
            BiomeSource biomeSource = generator.getBiomeSource();
            this.baseSource = biomeSource instanceof LatitudeBiomeSource latitudeSource
                    ? latitudeSource.original()
                    : biomeSource;
            this.biomeRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
            this.noiseConfig = NoiseConfig.create(
                    ((net.minecraft.world.gen.chunk.NoiseChunkGenerator) this.generator).getSettings().value(),
                    world.getRegistryManager().getOrThrow(RegistryKeys.NOISE_PARAMETERS),
                    atlasSeed);
            this.sampler = noiseConfig.getMultiNoiseSampler();
            net.minecraft.world.gen.chunk.NoiseChunkGenerator noiseGen =
                    generator instanceof net.minecraft.world.gen.chunk.NoiseChunkGenerator ng ? ng : null;
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
            for (BiomeMaskLayer maskLayer : maskTargets) {
                maskImages.put(maskLayer, new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
            }

            this.startNanos = System.nanoTime();
        }

        static HeightStepProcessor create(ServerWorld world,
                                          int radiusBlocks,
                                          int stepBlocks,
                                          int y,
                                          Path runDirectory,
                                          long atlasSeed,
                                          ExportOptions options) {
            return new HeightStepProcessor(world, radiusBlocks, stepBlocks, y, runDirectory, atlasSeed, options);
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

            while (imageZ < height && System.nanoTime() <= deadline) {
                int blockZ = zMin + (imageZ * stepBlocks);
                int noiseZ = Math.floorDiv(blockZ, 4);
                int blockX = xMin + (imageX * stepBlocks);
                int noiseX = Math.floorDiv(blockX, 4);

                String sampledBiomeId = null;
                if (needsBiomeSampling()) {
                    RegistryEntry<Biome> base = baseSource.getBiome(noiseX, noiseY, noiseZ, sampler);
                    RegistryEntry<Biome> picked = LatitudeBiomes.pick(
                            biomeRegistry,
                            base,
                            blockX,
                            blockZ,
                            y,
                            radiusBlocks,
                            sampler,
                            "SOURCE",
                            gatedNoiseGen,
                            gatedNoiseConfig,
                            gatedHeightView);
                    RegistryEntry<Biome> out = picked != null ? picked : base;
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

                if (layers.contains(Layer.BANDS)) {
                    LatitudeBands.Band band = bandForBlockZ(radiusBlocks, blockZ);
                    images.get(Layer.BANDS).setRGB(imageX, imageZ, colorForBand(band));
                    bandCounts.merge(band.id(), 1, Integer::sum);
                }

                if (layers.contains(Layer.TEMPERATURE) || layers.contains(Layer.HUMIDITY) || layers.contains(Layer.CONTINENTALNESS)) {
                    MultiNoiseUtil.NoiseValuePoint point = sampler.sample(noiseX, noiseY, noiseZ);
                    if (layers.contains(Layer.TEMPERATURE)) {
                        double temperature01 = normalizeNoise(MultiNoiseUtil.toFloat(point.temperatureNoise()));
                        images.get(Layer.TEMPERATURE).setRGB(imageX, imageZ, colorForTemperature(temperature01));
                    }
                    if (layers.contains(Layer.HUMIDITY)) {
                        double humidity01 = normalizeNoise(MultiNoiseUtil.toFloat(point.humidityNoise()));
                        images.get(Layer.HUMIDITY).setRGB(imageX, imageZ, colorForHumidity(humidity01));
                    }
                    if (layers.contains(Layer.CONTINENTALNESS)) {
                        double continentalness = MathHelper.clamp(MultiNoiseUtil.toFloat(point.continentalnessNoise()), -1.0, 1.0);
                        images.get(Layer.CONTINENTALNESS).setRGB(imageX, imageZ, colorForContinentalness(continentalness));
                    }
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
                Path outputDir = atlasStepDirectory(defaultAtlasRoot(runDirectory), seed, radiusBlocks, stepBlocks);
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
                if (emitBiomeIndex && biomeIndexImage != null) {
                    biomeIndexPath = outputDir.resolve("biome_ids.png");
                    boolean wrote = ImageIO.write(biomeIndexImage, "png", biomeIndexPath.toFile());
                    if (!wrote) {
                        throw new IOException("PNG writer unavailable for biome_ids");
                    }
                    writeBiomePalette(outputDir.resolve("biome_palette.json"), biomeIndices);
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

    public static Path atlasStepDirectory(Path atlasRoot, long seed, int radiusBlocks, int stepBlocks) {
        return atlasRoot
                .resolve("seed_" + seed)
                .resolve("R" + radiusBlocks)
                .resolve("step" + stepBlocks);
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
        json.append("  \"continentalnessScale\": \"-1.0..1.0 (deep-ocean->coast->inland)\"\n");
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
        int absZ = (int) Math.round(MathHelper.clamp(absFraction, 0.0, 1.0) * radiusBlocks);
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
        row = MathHelper.clamp(row, 0, Math.max(0, height - 1));
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
        return MathHelper.clamp((noiseValue + 1.0) * 0.5, 0.0, 1.0);
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

    private static int colorForTemperature(double value01) {
        double t = MathHelper.clamp(value01, 0.0, 1.0);
        if (t < 0.5) {
            return lerpRgb(0x2C7BB6, 0xFFF3B0, t * 2.0);
        }
        return lerpRgb(0xFFF3B0, 0xD7191C, (t - 0.5) * 2.0);
    }

    private static int colorForHumidity(double value01) {
        double t = MathHelper.clamp(value01, 0.0, 1.0);
        if (t < 0.5) {
            return lerpRgb(0xB07D3B, 0xE9E7C5, t * 2.0);
        }
        return lerpRgb(0xE9E7C5, 0x2A9D8F, (t - 0.5) * 2.0);
    }

    private static int colorForContinentalness(double valueSigned) {
        double c = MathHelper.clamp(valueSigned, -1.0, 1.0);
        if (c < 0.0) {
            return lerpRgb(0x0B3D91, 0xE0C097, c + 1.0);
        }
        return lerpRgb(0xE0C097, 0x6B8E23, c);
    }

    private static int lerpRgb(int from, int to, double t) {
        double clamped = MathHelper.clamp(t, 0.0, 1.0);
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

    private static String biomeId(Registry<Biome> biomeRegistry, RegistryEntry<Biome> biome) {
        Identifier id = biomeRegistry.getId(biome.value());
        if (id != null) {
            return id.toString();
        }
        return biome.getKey().map(key -> key.getValue().toString()).orElse("minecraft:plains");
    }

    static int stableColorForBiomeId(String biomeId) {
        String id = biomeId.toLowerCase(Locale.ROOT);
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
        int clamped = MathHelper.clamp(configured, 8, 512);
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

    private static RegistryEntry<Biome> forceMangroveSwampForAtlas(Registry<Biome> biomeRegistry, RegistryEntry<Biome> fallback) {
        RegistryEntry.Reference<Biome> mangrove = biomeRegistry.getEntry(MANGROVE_SWAMP_BIOME_ID).orElse(null);
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
        CONTINENTALNESS("continentalness");

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
