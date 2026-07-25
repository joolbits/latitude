package com.example.globe.dev;

import com.example.globe.core.geo.GeoAuthority;
import com.example.globe.core.geo.GeoIdLabeling;
import com.example.globe.core.geo.GeoIdTable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 2 GeoAuthority Proof Tool — pure-Java, NO Minecraft server. Rasterizes the GeoAuthority
 * macro-geography field over a whole world and writes analyzer-compatible artifacts so the trusted
 * {@code tools/atlas/geography_analyzer.py} measures GeoAuthority's continents/basins with the same
 * connected-component code path used to characterize the "current red".
 *
 * <p>This is the "visible in Atlas before it changes biome selection" deliverable: it exercises and
 * measures the geography WITHOUT touching {@code LatitudeBiomes.pick}. It is dev-only (the
 * {@code com/example/globe/dev/} package is excluded from the release jar) and loads no biome packs,
 * so it is inherently the vanilla-only measurement.
 *
 * <p>Emits per run dir: {@code biome_ids.png} + {@code biome_palette.json} + {@code biomes.txt}
 * (land=plains / ocean=ocean, analyzer-ready), {@code continents.png} / {@code basins.png} (flood-fill
 * component visuals), and {@code metrics.json}.
 *
 * Usage (via the {@code geoAuthorityAtlas} Gradle task):
 *   ./gradlew geoAuthorityAtlas --args="seed=42 size=small step=16 aspect=2.0 out=/tmp/geo"
 */
public final class GeoAuthorityAtlas {

    private static final Map<String, Integer> SIZE_TO_RADIUS = Map.of(
            "itty", 3750, "small", 7500, "regular", 10000, "large", 15000, "massive", 20000);

    public static void main(String[] args) throws IOException {
        long seed = 42L;
        int zRadius = 7500;
        int step = 16;
        double aspect = 2.0;
        String out = "run-headless/geo-atlas";
        for (String a : args) {
            int eq = a.indexOf('=');
            if (eq < 0) continue;
            String k = a.substring(0, eq), v = a.substring(eq + 1);
            switch (k) {
                case "seed" -> seed = Long.parseLong(v);
                case "size" -> zRadius = SIZE_TO_RADIUS.getOrDefault(v.toLowerCase(), zRadius);
                case "zRadius", "radius" -> zRadius = Integer.parseInt(v);
                case "step" -> step = Integer.parseInt(v);
                case "aspect" -> aspect = Double.parseDouble(v);
                case "out" -> out = v;
                default -> { }
            }
        }
        int xRadius = (int) Math.round(zRadius * aspect);

        System.out.printf("[geoAtlas] seed=%d zRadius=%d xRadius=%d step=%d aspect=%.2f%n",
                seed, zRadius, xRadius, step, aspect);

        GeoAuthority geo = new GeoAuthority(seed, zRadius, xRadius);
        long t0 = System.nanoTime();
        GeoIdLabeling labeling = GeoIdLabeling.build(geo, step);
        geo.attachIdTable(labeling.table());
        long buildMs = (System.nanoTime() - t0) / 1_000_000L;

        int w = labeling.width(), h = labeling.height();
        Path dir = Path.of(out, String.format("geo_seed%d_R%d_step%d", seed, zRadius, step));
        Files.createDirectories(dir);

        // 1. analyzer-compatible land/ocean raster (land=index0=plains, ocean=index1=ocean).
        BufferedImage biomeIds = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage continents = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage basins = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                boolean land = labeling.isLand(r, c);
                int idx = land ? 0 : 1;
                biomeIds.setRGB(c, r, (idx & 0xFF) * 0x010101);
                int root = labeling.rootAt(r, c);
                if (land) {
                    continents.setRGB(c, r, idColor(root));
                    basins.setRGB(c, r, 0x101018);
                } else {
                    basins.setRGB(c, r, idColor(root));
                    continents.setRGB(c, r, 0x081018);
                }
            }
        }
        ImageIO.write(biomeIds, "png", dir.resolve("biome_ids.png").toFile());
        ImageIO.write(continents, "png", dir.resolve("continents.png").toFile());
        ImageIO.write(basins, "png", dir.resolve("basins.png").toFile());

        // 2. palette + summary sidecar (mirrors BiomePreviewExporter's format).
        writePalette(dir.resolve("biome_palette.json"));
        int xMin = -xRadius, zMin = -zRadius;
        int xMax = xMin + (w - 1) * step, zMax = zMin + (h - 1) * step;
        writeSummary(dir.resolve("biomes.txt"), seed, zRadius, step, xMin, xMax, zMin, zMax, w, h);

        // 3. metrics json.
        GeoIdTable.Metrics m = labeling.table().metrics();
        writeMetrics(dir.resolve("metrics.json"), seed, zRadius, xRadius, step, buildMs,
                labeling.table().size(), m);

        System.out.printf("[geoAtlas] land=%.1f%%  landComps=%d  largestLand=%.1f%%  majorContinents=%d%n",
                m.landFraction() * 100, m.landComponentCount(), m.largestLandComponentShare() * 100,
                m.majorContinentCount());
        System.out.printf("[geoAtlas] oceanBasins=%d  largestBasin=%.1f%%  dominantBasins(>=5%%)=%d  idTableSize=%d  buildMs=%d%n",
                m.oceanBasinCount(), m.largestOceanBasinShare() * 100, m.dominantOceanBasinCount(),
                labeling.table().size(), buildMs);
        System.out.println("[geoAtlas] wrote " + dir.toAbsolutePath());
    }

    private static int idColor(int id) {
        int hsh = com.example.globe.core.geo.GeoNoise.mix64toInt(id * 0x9E3779B97F4A7C15L);
        int rr = 60 + (hsh & 0x7F) + ((hsh >> 20) & 0x3F);
        int gg = 60 + ((hsh >> 7) & 0x7F) + ((hsh >> 24) & 0x3F);
        int bb = 60 + ((hsh >> 14) & 0x7F) + ((hsh >> 26) & 0x3F);
        return ((rr & 0xFF) << 16) | ((gg & 0xFF) << 8) | (bb & 0xFF);
    }

    private static void writePalette(Path path) throws IOException {
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("{\n  \"biomes\": [\n");
            w.write("    {\"index\": 0, \"biome_id\": \"minecraft:plains\"},\n");
            w.write("    {\"index\": 1, \"biome_id\": \"minecraft:ocean\"}\n");
            w.write("  ]\n}\n");
        }
    }

    private static void writeSummary(Path path, long seed, int zRadius, int step,
                                     int xMin, int xMax, int zMin, int zMax, int w, int h) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("seed=").append(seed).append('\n');
        sb.append("radiusBlocks=").append(zRadius).append('\n');
        sb.append("stepBlocks=").append(step).append('\n');
        sb.append("y=64\n");
        sb.append("blockBounds=x[").append(xMin).append("..").append(xMax)
                .append("],z[").append(zMin).append("..").append(zMax).append("]\n");
        sb.append("image=").append(w).append('x').append(h).append('\n');
        sb.append("source=GeoAuthority (Phase 2 geoV2 offline proof; land=plains ocean=ocean)\n");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void writeMetrics(Path path, long seed, int zRadius, int xRadius, int step,
                                     long buildMs, int idTableSize, GeoIdTable.Metrics m) throws IOException {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("seed", seed);
        j.put("zRadius", zRadius);
        j.put("xRadius", xRadius);
        j.put("step", step);
        j.put("buildMs", buildMs);
        j.put("idTableSize", idTableSize);
        j.put("landFraction", m.landFraction());
        j.put("landComponentCount", m.landComponentCount());
        j.put("largestLandComponentShare", m.largestLandComponentShare());
        j.put("majorContinentCount", m.majorContinentCount());
        j.put("oceanBasinCount", m.oceanBasinCount());
        j.put("largestOceanBasinShare", m.largestOceanBasinShare());
        j.put("dominantOceanBasinCount", m.dominantOceanBasinCount());
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, Object> e : j.entrySet()) {
            sb.append("  \"").append(e.getKey()).append("\": ").append(e.getValue());
            if (++i < j.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("}\n");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private GeoAuthorityAtlas() {
    }
}
