package com.example.globe.dev;

import com.example.globe.core.climate.ClimateAuthority;
import com.example.globe.core.climate.ClimateClass;
import com.example.globe.core.climate.ClimateSummary;
import com.example.globe.core.geo.GeoAuthority;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Phase 3 ClimateAuthority Proof Tool — pure-Java, NO Minecraft server, loads no biome packs (so it is
 * inherently the vanilla-only measurement). Rasterizes the ClimateAuthority fields over a whole world
 * (temperature / precipitation / climateClass), writes PNGs + a per-band climateClass distribution, and
 * measures a few Earth-acceptance checks (rainforest concentrated at the equator, deserts in the
 * subtropics, ice caps at the poles). This is the "visible in Atlas before it changes biome selection"
 * deliverable — it exercises and measures climate WITHOUT touching {@code LatitudeBiomes.pick}.
 *
 * Usage (via the {@code climateAtlas} Gradle task):
 *   ./gradlew climateAtlas --args="seed=42 size=small step=16 aspect=2.0 out=/tmp/clim"
 */
public final class ClimateAtlas {

    private static final Map<String, Integer> SIZE_TO_RADIUS = Map.of(
            "itty", 3750, "small", 7500, "regular", 10000, "large", 15000, "massive", 20000);

    public static void main(String[] args) throws IOException {
        long seed = 42L;
        int zRadius = 7500;
        int step = 24;
        double aspect = 2.0;
        String out = "run-headless/climate-atlas";
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
        System.out.printf("[climAtlas] seed=%d zRadius=%d xRadius=%d step=%d%n", seed, zRadius, xRadius, step);

        GeoAuthority geo = new GeoAuthority(seed, zRadius, xRadius);
        ClimateAuthority clim = new ClimateAuthority(geo);

        int w = (2 * xRadius) / step + 1, h = (2 * zRadius) / step + 1;
        int xMin = -xRadius, zMin = -zRadius;
        BufferedImage tempImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage precipImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage classImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        // Per-band climateClass histograms + a few acceptance counters.
        String[] bands = {"tropical", "subtropical", "temperate", "subpolar", "polar"};
        @SuppressWarnings("unchecked")
        Map<ClimateClass, Long>[] bandHist = new Map[5];
        for (int i = 0; i < 5; i++) bandHist[i] = new EnumMap<>(ClimateClass.class);
        long landCols = 0;

        long t0 = System.nanoTime();
        for (int r = 0; r < h; r++) {
            int z = zMin + r * step;
            double a = Math.abs((double) z) / zRadius * 90.0;
            int bandIdx = a < 23.5 ? 0 : a < 35 ? 1 : a < 50 ? 2 : a < 66.5 ? 3 : 4;
            for (int c = 0; c < w; c++) {
                int x = xMin + c * step;
                ClimateSummary s = clim.sample(x, z);
                ClimateClass cc = ClimateClass.valueOf(s.climateClass());
                tempImg.setRGB(c, r, heat(s.temperature01()));
                precipImg.setRGB(c, r, blueScale(s.precipitation01()));
                classImg.setRGB(c, r, classColor(cc));
                if (!cc.isOcean()) {
                    landCols++;
                    bandHist[bandIdx].merge(cc, 1L, Long::sum);
                }
            }
        }
        long buildMs = (System.nanoTime() - t0) / 1_000_000L;

        Path dir = Path.of(out, String.format("clim_seed%d_R%d_step%d", seed, zRadius, step));
        Files.createDirectories(dir);
        ImageIO.write(tempImg, "png", dir.resolve("temperature.png").toFile());
        ImageIO.write(precipImg, "png", dir.resolve("precipitation.png").toFile());
        ImageIO.write(classImg, "png", dir.resolve("climate_class.png").toFile());

        StringBuilder rep = new StringBuilder();
        rep.append(String.format("seed=%d zRadius=%d xRadius=%d step=%d landColumns=%d buildMs=%d%n%n",
                seed, zRadius, xRadius, step, landCols, buildMs));
        for (int i = 0; i < 5; i++) {
            long bandTotal = bandHist[i].values().stream().mapToLong(Long::longValue).sum();
            rep.append(String.format("--- %s (land=%d) ---%n", bands[i], bandTotal));
            final long bt = bandTotal;
            bandHist[i].entrySet().stream()
                    .sorted(Map.Entry.<ClimateClass, Long>comparingByValue().reversed())
                    .forEach(e -> rep.append(String.format("  %-22s %5.1f%%%n",
                            e.getKey().name(), bt > 0 ? e.getValue() * 100.0 / bt : 0.0)));
            rep.append('\n');
        }
        System.out.print(rep);
        Files.writeString(dir.resolve("class_distribution.txt"), rep.toString(), StandardCharsets.UTF_8);
        System.out.println("[climAtlas] wrote " + dir.toAbsolutePath());
    }

    private static int heat(double t) {
        // blue(cold) -> green -> yellow -> red(hot)
        int r = (int) (255 * clamp01(1.4 * t - 0.3));
        int g = (int) (255 * clamp01(1.0 - Math.abs(t - 0.5) * 1.6));
        int b = (int) (255 * clamp01(1.0 - 1.6 * t));
        return (r << 16) | (g << 8) | b;
    }

    private static int blueScale(double p) {
        // pale (dry) -> deep blue (wet)
        double v = clamp01(p);
        int r = (int) (210 - 180 * v);
        int g = (int) (210 - 110 * v);
        int b = (int) (170 + 70 * v);
        return (r << 16) | (g << 8) | b;
    }

    private static int classColor(ClimateClass c) {
        return switch (c) {
            case TROPICAL_RAINFOREST -> 0x0A5F0A;
            case TROPICAL_MONSOON -> 0x2E8B2E;
            case TROPICAL_SAVANNA, SAVANNA -> 0xC2B24A;
            case HOT_DESERT -> 0xE6C86e;
            case COOL_DESERT -> 0xC29A5B;
            case HUMID_SUBTROPICAL -> 0x6FBF5A;
            case MEDITERRANEAN -> 0xAEB24A;
            case TEMPERATE_OCEANIC -> 0x4C9E6F;
            case HUMID_CONTINENTAL -> 0x5B8C5A;
            case BOREAL -> 0x2E6B57;
            case COLD_STEPPE -> 0x9BB0A0;
            case TUNDRA -> 0xBFC9C2;
            case ICE_CAP -> 0xEAF4FF;
            case OCEAN_WARM -> 0x2E6FB0;
            case OCEAN_LUKEWARM -> 0x2A5F9E;
            case OCEAN -> 0x244F86;
            case OCEAN_FROZEN -> 0xB9D0E8;
        };
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private ClimateAtlas() {
    }
}
