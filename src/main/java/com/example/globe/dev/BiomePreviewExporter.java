package com.example.globe.dev;

import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.chunk.ChunkGenerator;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BiomePreviewExporter {
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final int TOP_BIOME_COUNT = 20;

    private BiomePreviewExporter() {
    }

    public static ExportResult export(ServerWorld world,
                                      int radiusBlocks,
                                      int stepBlocks,
                                      int y,
                                      Path runDirectory) throws IOException {
        long startNanos = System.nanoTime();

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

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Map<String, Integer> biomeCounts = new HashMap<>();
        Map<String, Integer> biomeColors = new HashMap<>();

        ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
        BiomeSource biomeSource = generator.getBiomeSource();
        BiomeSource baseSource = biomeSource instanceof LatitudeBiomeSource latitudeSource
                ? latitudeSource.original()
                : biomeSource;
        Registry<Biome> biomeRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
        MultiNoiseUtil.MultiNoiseSampler sampler = world.getChunkManager().getNoiseConfig().getMultiNoiseSampler();

        int noiseY = Math.floorDiv(y, 4);
        for (int imageZ = 0, blockZ = zMin; imageZ < height; imageZ++, blockZ += stepBlocks) {
            int noiseZ = Math.floorDiv(blockZ, 4);
            for (int imageX = 0, blockX = xMin; imageX < width; imageX++, blockX += stepBlocks) {
                int noiseX = Math.floorDiv(blockX, 4);
                RegistryEntry<Biome> base = baseSource.getBiome(noiseX, noiseY, noiseZ, sampler);
                RegistryEntry<Biome> picked = LatitudeBiomes.pick(
                        biomeRegistry,
                        base,
                        blockX,
                        blockZ,
                        radiusBlocks,
                        sampler,
                        "BIOME_PNG");
                RegistryEntry<Biome> out = picked != null ? picked : base;
                String biomeId = biomeId(biomeRegistry, out);
                int rgb = biomeColors.computeIfAbsent(biomeId, BiomePreviewExporter::stableColorForBiomeId);
                image.setRGB(imageX, imageZ, rgb);
                biomeCounts.merge(biomeId, 1, Integer::sum);
            }
        }

        Path outputDir = runDirectory.resolve("latdev").resolve("biome-previews");
        Files.createDirectories(outputDir);

        long seed = world.getSeed();
        String baseName = "biomes_seed-" + seed + "_R" + radiusBlocks + "_step" + stepBlocks;
        Path pngPath = outputDir.resolve(baseName + ".png");
        Path txtPath = outputDir.resolve(baseName + ".txt");

        boolean wrote = ImageIO.write(image, "png", pngPath.toFile());
        if (!wrote) {
            throw new IOException("PNG writer unavailable");
        }

        long totalSamples = (long) width * height;
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        writeSummary(
                txtPath,
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
                biomeCounts);

        return new ExportResult(pngPath, txtPath, width, height, totalSamples, durationMs);
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
                                     Map<String, Integer> biomeCounts) throws IOException {
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

    private static String biomeId(Registry<Biome> biomeRegistry, RegistryEntry<Biome> biome) {
        Identifier id = biomeRegistry.getId(biome.value());
        if (id != null) {
            return id.toString();
        }
        return biome.getKey().map(key -> key.getValue().toString()).orElse("minecraft:plains");
    }

    private static int stableColorForBiomeId(String biomeId) {
        String id = biomeId.toLowerCase(Locale.ROOT);
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

        int hash = biomeId.hashCode();
        hash ^= (hash >>> 16);
        float hue = (hash & 0xFFFF) / 65535.0f;
        float sat = 0.55f + ((hash >>> 16) & 0x1F) / 100.0f;
        float val = 0.65f + ((hash >>> 21) & 0x1F) / 100.0f;
        return Color.HSBtoRGB(hue, Math.min(0.95f, sat), Math.min(0.95f, val)) & 0x00FFFFFF;
    }

    public record ExportResult(Path pngPath, Path txtPath, int width, int height, long totalSamples, long durationMs) {
    }
}
