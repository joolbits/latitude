package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dev-only biome distribution census, built to answer one question the ordinary atlas census
 * cannot: not just how much desert exists, but WHERE it fails to exist — per latitude band, per
 * humidity province, and against the arid-hotspot area that is supposed to be producing it.
 *
 * <p>Deliberately a separate tool rather than more counters inside the atlas exporter: that
 * exporter feeds other frozen proofs, and this measurement needs a different sample vocabulary.
 * It samples the world's own biome source, so what it counts is what generation produces —
 * including the full provider stack when the preview run is synced with mods.
 *
 * <p>Emits counts only. The structure atlas learned the per-sample lesson the expensive way; a
 * census needs distributions, not rows.
 */
public final class DistributionCensusExporter {

    private static final String PROP_KEY = "latdev.distributionCensus";
    private static final String STEP_KEY = "latdev.distributionCensus.step";

    private DistributionCensusExporter() {
    }

    public static void register() {
        if (System.getProperty(PROP_KEY) == null) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTED.register(DistributionCensusExporter::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        String property = System.getProperty(PROP_KEY);
        if (property == null) {
            return;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            GlobeMod.LOGGER.error("[Latitude] distribution census REFUSED: no overworld");
            return;
        }
        ChunkGenerator generator = overworld.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noise)
                || !GlobeMod.shouldApplyLatitudeWorldgen(noise)) {
            // Same discipline as the structure atlas: a vanilla world would answer with vanilla's
            // distribution dressed up as Latitude's.
            GlobeMod.LOGGER.error("[Latitude] distribution census REFUSED: Latitude does not own "
                    + "worldgen in this overworld — create the preview world with a globe world "
                    + "preset (level-type), not minecraft:normal");
            return;
        }

        int step = Integer.getInteger(STEP_KEY, 64);
        int radius = GlobeMod.borderRadiusForNoiseGenerator(noise);
        BiomeSource source = generator.getBiomeSource();
        Climate.Sampler sampler = overworld.getChunkSource().randomState().sampler();
        int quartY = QuartPos.fromBlock(LatitudeBiomes.SURFACE_CLASSIFY_Y);

        // key: band | province | biomeId
        Map<String, Integer> cells = new TreeMap<>();
        Map<String, Integer> biomeTotals = new TreeMap<>();
        Map<String, Integer> bandTotals = new TreeMap<>();
        Map<String, Integer> provinceTotals = new TreeMap<>();
        Map<String, Integer> hotspotByBand = new TreeMap<>();
        Map<String, Integer> hotspotBiomes = new TreeMap<>();
        int samples = 0;
        int hotspotSamples = 0;

        long startNanos = System.nanoTime();
        for (int blockZ = -radius; blockZ < radius; blockZ += step) {
            for (int blockX = -radius; blockX < radius; blockX += step) {
                Holder<Biome> holder = source.getNoiseBiome(
                        QuartPos.fromBlock(blockX), quartY, QuartPos.fromBlock(blockZ), sampler);
                String biomeId = LatitudeBiomes.biomeIdPublic(holder);
                if (biomeId == null) {
                    continue;
                }
                String band = bandName(
                        LatitudeBiomes.authoritativeLandBandIndex(blockX, blockZ, radius));
                Object province = LatitudeBiomes.classifyProvince(blockX, blockZ);
                String provinceName = province != null ? province.toString() : "NONE";
                boolean hotspot = LatitudeBiomes.debugAridHotspot(blockX, blockZ);

                samples++;
                biomeTotals.merge(biomeId, 1, Integer::sum);
                bandTotals.merge(band, 1, Integer::sum);
                provinceTotals.merge(provinceName, 1, Integer::sum);
                cells.merge(band + "|" + provinceName + "|" + biomeId, 1, Integer::sum);
                if (hotspot) {
                    hotspotSamples++;
                    hotspotByBand.merge(band, 1, Integer::sum);
                    hotspotBiomes.merge(biomeId, 1, Integer::sum);
                }
            }
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

        Path outputRoot = "true".equalsIgnoreCase(property) || property.isBlank()
                ? server.getServerDirectory().resolve("latdev-distribution")
                : Path.of(property);
        String levelName = server.getWorldData().getLevelName().replaceAll("[^A-Za-z0-9._-]", "_");
        try {
            Files.createDirectories(outputRoot);
            Path out = outputRoot.resolve(levelName + "_R" + radius + "_step" + step + ".json");
            Files.writeString(out, toJson(
                    overworld, radius, step, samples, hotspotSamples, elapsedMs,
                    biomeTotals, bandTotals, provinceTotals, hotspotByBand, hotspotBiomes, cells),
                    StandardCharsets.UTF_8);
            GlobeMod.LOGGER.info(
                    "[Latitude] distribution census: {} samples, {} biomes, hotspot {} ({}%) in "
                            + "{} ms -> {}",
                    samples, biomeTotals.size(), hotspotSamples,
                    samples == 0 ? "0" : String.format(java.util.Locale.ROOT, "%.2f",
                            100.0 * hotspotSamples / samples),
                    elapsedMs, out.toAbsolutePath());
        } catch (IOException | RuntimeException failure) {
            GlobeMod.LOGGER.error("[Latitude] distribution census failed", failure);
        }

        if (System.getProperty("latdev.biomePng") == null
                && System.getProperty("latdev.biomeSearch") == null
                && System.getProperty("latdev.structureAtlas") == null) {
            server.halt(false);
        }
    }

    private static String bandName(int landBandIndex) {
        return switch (landBandIndex) {
            case 0 -> "TROPICAL";
            case 1 -> "SUBTROPICAL";
            case 2 -> "TEMPERATE";
            case 3 -> "SUBPOLAR";
            default -> "POLAR";
        };
    }

    private static String toJson(
            ServerLevel level,
            int radius,
            int step,
            int samples,
            int hotspotSamples,
            long elapsedMs,
            Map<String, Integer> biomeTotals,
            Map<String, Integer> bandTotals,
            Map<String, Integer> provinceTotals,
            Map<String, Integer> hotspotByBand,
            Map<String, Integer> hotspotBiomes,
            Map<String, Integer> cells) {
        StringBuilder out = new StringBuilder(cells.size() * 48 + 4096);
        out.append("{\n");
        out.append("  \"levelName\": \"")
                .append(level.getServer().getWorldData().getLevelName()
                        .replaceAll("[^A-Za-z0-9._ -]", "_")).append("\",\n");
        out.append("  \"seed\": ").append(level.getSeed()).append(",\n");
        out.append("  \"radius\": ").append(radius).append(",\n");
        out.append("  \"step\": ").append(step).append(",\n");
        out.append("  \"samples\": ").append(samples).append(",\n");
        out.append("  \"hotspotSamples\": ").append(hotspotSamples).append(",\n");
        out.append("  \"elapsedMs\": ").append(elapsedMs).append(",\n");
        appendMap(out, "biomeTotals", biomeTotals, true);
        appendMap(out, "bandTotals", bandTotals, true);
        appendMap(out, "provinceTotals", provinceTotals, true);
        appendMap(out, "hotspotByBand", hotspotByBand, true);
        appendMap(out, "hotspotBiomes", hotspotBiomes, true);
        appendMap(out, "bandProvinceBiome", cells, false);
        out.append("}\n");
        return out.toString();
    }

    private static void appendMap(
            StringBuilder out, String name, Map<String, Integer> map, boolean trailingComma) {
        out.append("  \"").append(name).append("\": {");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : new LinkedHashMap<>(map).entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append("\n    \"").append(entry.getKey()).append("\": ").append(entry.getValue());
        }
        out.append(map.isEmpty() ? "}" : "\n  }").append(trailingComma ? ",\n" : "\n");
    }
}
