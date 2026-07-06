package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.adapter.geo.GeoSummaryProvider;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.geo.GeoAuthority;
import com.example.globe.terrain.TerrainRouterWrapping;
import com.example.globe.terrain.TerrainRouterWrapping.InstallResult;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4 (Terrain Integration Spike) mechanical proof harness -- design §6.2's "small headless probe
 * harness," plus the §6.1 byte-identity legs and the §6.4 vanilla-only leg. Design of record:
 * {@code docs/design/terrain-wrapper-design-20260705.md}.
 *
 * <p><b>Why a dev headless tool, not a JUnit test.</b> This project's {@code src/test} suite is
 * deliberately pure-JVM with no Minecraft imports (see {@code build.gradle}: "Pure-JVM unit tests for
 * the Core Logic layer"). Building a real {@code RandomState} for {@code globe:overworld} requires a
 * bootstrapped Minecraft registry environment (biomes, noise settings, density functions), which only
 * exists inside a running (headless) server -- exactly the environment
 * {@code com.example.globe.dev.BiomePreviewHeadlessRunner} already provides for the atlas tooling. This
 * class follows that same established pattern: a dev-only class, registered reflectively in
 * development environments only, dispatched from a system property checked in
 * {@code BiomePreviewHeadlessRunner.onServerStarted}.
 *
 * <p><b>Why one JVM run answers only PART of the picture.</b> Every {@link LatitudeV2Flags} field is
 * {@code static final}, read once from {@code System.getProperty} at class-init (see that class's own
 * "Testing note" javadoc, sweeper audit #2 finding #27): a single running JVM can only ever exercise
 * ONE flag configuration. So this harness does not itself compare "flag off" vs "flag on" -- each
 * invocation writes a full raw-data JSON report reflecting whatever {@code -D} flags THIS JVM was
 * launched with, and the actual byte-identity / shift-direction comparisons across configurations are
 * done externally (a small Python diff script) by comparing two (or more) reports written by separate
 * forked-JVM invocations -- mirroring exactly how the existing headless atlas byte-identity proofs work.
 *
 * <p>Trigger: {@code -Dlatdev.terrainProof=true}. Config (all via {@code -D} system properties):
 * <ul>
 *   <li>{@code latdev.terrainProof.seed} (default a fixed test seed)</li>
 *   <li>{@code latdev.terrainProof.radius} (default 10000, "regular" globe size)</li>
 *   <li>{@code latdev.terrainProof.shape} ({@code classic}|{@code mercator}, default {@code classic})</li>
 *   <li>{@code latdev.terrainProof.out} (output JSON path; default {@code run-headless/latdev/terrain-proof-report.json})</li>
 * </ul>
 * All other configuration (whether the wrapper is armed at all) is via the actual
 * {@link LatitudeV2Flags} JVM properties ({@code latitude.terrainV2.enabled},
 * {@code latitude.terrainV2.strength}, {@code latitude.terrainV2.oceanStrengthRatio},
 * {@code latitude.geoV2.enabled}) -- this harness does not re-invent flag plumbing, it reads the same
 * flags the shipped code reads.
 *
 * <p><b>Honest coverage-gap statement (sweeper finding #10) -- CONFIRMED LIVE, 2026-07-06.</b> This harness
 * calls {@link TerrainRouterWrapping#installIfArmed(RandomState, ServerLevel)} DIRECTLY (the same helper the
 * dev/atlas {@code BiomePreviewExporter} path uses), so it proves the WRAPPER LOGIC end-to-end: the triple
 * gate, the per-call NoOp-provider no-op in {@code GeoTerrainBiasFunction.compute()}, the 15-field router
 * rebuild, the bias formula, structure-preserving {@code mapChildren}, and the byte-identity legs. It does
 * **NOT** prove that the shipped REAL-GAMEPLAY {@code RandomStateRouterTerrainMixin} on {@code ChunkMap}
 * fires with the SAME provider-readiness timing a live create-world flow has -- and this gap was not
 * theoretical: it is exactly how a real ordering bug slipped through every headless proof gate here and
 * only surfaced on Peetsa's first live create-world test. This harness's own dev/atlas {@code ServerLevel}
 * always boots with a fully-initialized {@code GEO_V2_PROVIDER} already in place (the exporter runs well
 * after world load completes), so it can never reproduce "install fires before the real provider exists" --
 * only a genuine client/server create-world boot through {@code ChunkMap} construction can. That requires a
 * real client/server world boot through {@code ChunkMap} construction, which is the live-pass
 * territory, not a headless probe. (The two paths are additionally indistinguishable at the log level because
 * the install log is a once-per-JVM latch.) The definitive {@code installResult}/{@code wrapperInstalled}
 * fields this harness records are for the direct-helper call it makes; the ChunkMap-mixin firing is asserted
 * separately by the live pass. This is a documented, accepted coverage boundary, not a hidden gap.
 */
public final class TerrainProofHarness {

    private static final String TRIGGER_PROP = "latdev.terrainProof";
    private static final String RADIUS_PROP = "latdev.terrainProof.radius";
    private static final String SHAPE_PROP = "latdev.terrainProof.shape";
    private static final String OUT_PROP = "latdev.terrainProof.out";
    private static final String NONGLOBE_PROP = "latdev.terrainProof.nonGlobe";

    // NOTE (sweeper findings #14/#21): there is deliberately NO {@code latdev.terrainProof.seed} read here.
    // The harness always uses the BOOTED world's own seed ({@code world.getSeed()}), because the shipped
    // GEO_V2_PROVIDER / GeoAuthority are armed for THAT seed by GlobeMod.initLatitudeBiomesForWorld; re-driving
    // a different requested seed here would test a GeoAuthority the shipped provider is NOT actually using
    // (see runAll's own comment). The old dead {@code SEED_PROP}/{@code DEFAULT_SEED} constants were removed.
    // {@code latdev.terrainProof.radius} is used ONLY as a fallback for CHOOSING probe columns on the
    // non-globe leg (where the real radius is legitimately 0); it never overrides the booted world's radius.
    private static final int DEFAULT_RADIUS = 10000;

    private TerrainProofHarness() {
    }

    /**
     * Parse an {@code int} system property that may arrive as an EMPTY STRING (not just absent). Sweeper
     * finding #1: build.gradle forwards {@code -Dlatdev.terrainProof.radius=${System.getProperty(...,'')}} so
     * an un-passed radius reaches the JVM as {@code -Dlatdev.terrainProof.radius=} (present-but-empty). The
     * two-arg {@link System#getProperty(String, String)} default only fires when the property is ABSENT, so
     * a naive {@code Integer.parseInt(getProperty(k,"10000").trim())} throws {@link NumberFormatException} on
     * the empty string -- which is exactly the case for the non-globe/globe-gate-isolation leg (radius 0).
     * This helper treats null-OR-blank (after trim) as "use the default".
     */
    private static int intPropOrDefault(String key, int fallback) {
        String raw = System.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        raw = raw.trim();
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static boolean isTriggered() {
        return Boolean.parseBoolean(System.getProperty(TRIGGER_PROP, "false"));
    }

    static void runAndStop(MinecraftServer server) {
        ServerLevel world = server.overworld();
        try {
            if (world == null) {
                GlobeMod.LOGGER.error("[latdev][terrainProof] no overworld available; stopping server");
                return;
            }
            Report report = new Report();
            try {
                runAll(world, report);
            } catch (Throwable t) {
                report.harnessFailure = describeThrowable(t);
                GlobeMod.LOGGER.error("[latdev][terrainProof] harness failed", t);
            }
            Path outPath = resolveOutPath(server);
            Files.createDirectories(outPath.getParent());
            Files.write(outPath, report.toJson().getBytes(StandardCharsets.UTF_8));
            GlobeMod.LOGGER.info("[latdev][terrainProof] wrote report to {}", outPath);
        } catch (IOException e) {
            GlobeMod.LOGGER.error("[latdev][terrainProof] failed to write report", e);
        } finally {
            server.halt(false);
        }
    }

    private static Path resolveOutPath(MinecraftServer server) {
        String raw = System.getProperty(OUT_PROP, "").trim();
        if (!raw.isEmpty()) {
            return Path.of(raw);
        }
        return server.getServerDirectory().resolve("latdev").resolve("terrain-proof-report.json");
    }

    // ------------------------------------------------------------------------------------------------
    // Report model
    // ------------------------------------------------------------------------------------------------

    /** Raw-data report for ONE JVM's flag configuration. External scripts diff two reports. */
    static final class Report {
        String harnessFailure;

        // Echo of this JVM's flag configuration, so a comparison script can sanity-check it is
        // diffing the runs it thinks it is.
        boolean terrainV2Enabled = LatitudeV2Flags.TERRAIN_V2_ENABLED;
        double terrainV2Strength = LatitudeV2Flags.TERRAIN_V2_STRENGTH;
        double terrainV2OceanRatio = LatitudeV2Flags.TERRAIN_V2_OCEAN_STRENGTH_RATIO;
        boolean geoV2Enabled = LatitudeV2Flags.GEO_V2_ENABLED;
        long seed;
        int radius;
        String shape;
        boolean nonGlobeWorld;

        // Definitive install-status (sweeper findings #2-6/#8): what the shipped install helper actually
        // decided for THIS RandomState, asserted directly (not scraped from a once-per-JVM install log that
        // cannot tell "did not install" from "already logged earlier in this JVM"). A comparator can assert
        // e.g. wrapperInstalled==false on the non-globe leg and the geoV2-off leg, ==true on the armed legs.
        String installResult;      // TerrainRouterWrapping.InstallResult name
        boolean wrapperInstalled;  // installResult == INSTALLED
        // The actual runtime provider class at report-write time (GeoAuthorityProvider vs
        // NoOpGeoSummaryProvider). NOTE: since the 2026-07-06 live-ordering fix, wrapperInstalled==true is
        // expected even on a NoOp-provider (e.g. seed-0) world -- the wrapper always installs structurally
        // once the flag/globe gates pass; GeoTerrainBiasFunction.compute() is what no-ops per call when this
        // field shows NoOpGeoSummaryProvider. Assert on ACTUAL DENSITY VALUES being unchanged for the seed-0
        // no-op case, not on installResult/wrapperInstalled -- those no longer distinguish the NoOp case.
        String geoProviderClass;
        // Non-globe leg self-verification (sweeper findings #2-6/#17): whether the booted world was ACTUALLY
        // a genuine non-globe world (radius never armed AND generator is not a globe preset). If the harness
        // was told nonGlobe=true but the boot came up armed (stale server.properties), this is false and
        // harnessFailure is set -- so the leg can NEVER pass for the wrong reason by testing an armed world.
        boolean nonGlobeBootVerified;
        int activeRadiusAtBoot;    // LatitudeBiomes.getActiveRadiusBlocks() as actually booted
        boolean generatorIsGlobePreset;

        // §6.2 direct density-output numeric probe: one entry per probed column.
        final List<ColumnProbe> columnProbes = new ArrayList<>();

        // §6.2 Lipschitz/smoothness probe: one entry per transect.
        final List<TransectProbe> transects = new ArrayList<>();

        // §6.2 land-fraction / veto-flip assertion.
        LandFractionProbe landFraction;

        // §6.1(b)(ii) structural NoiseChunk-build check (getBaseColumn), one per probed column.
        final List<StructuralColumnProbe> structuralProbes = new ArrayList<>();

        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"harnessFailure\": ").append(jsonStringOrNull(harnessFailure)).append(",\n");
            sb.append("  \"terrainV2Enabled\": ").append(terrainV2Enabled).append(",\n");
            sb.append("  \"terrainV2Strength\": ").append(terrainV2Strength).append(",\n");
            sb.append("  \"terrainV2OceanRatio\": ").append(terrainV2OceanRatio).append(",\n");
            sb.append("  \"geoV2Enabled\": ").append(geoV2Enabled).append(",\n");
            sb.append("  \"seed\": ").append(seed).append(",\n");
            sb.append("  \"radius\": ").append(radius).append(",\n");
            sb.append("  \"shape\": \"").append(shape).append("\",\n");
            sb.append("  \"nonGlobeWorld\": ").append(nonGlobeWorld).append(",\n");
            sb.append("  \"installResult\": ").append(jsonStringOrNull(installResult)).append(",\n");
            sb.append("  \"wrapperInstalled\": ").append(wrapperInstalled).append(",\n");
            sb.append("  \"geoProviderClass\": ").append(jsonStringOrNull(geoProviderClass)).append(",\n");
            sb.append("  \"nonGlobeBootVerified\": ").append(nonGlobeBootVerified).append(",\n");
            sb.append("  \"activeRadiusAtBoot\": ").append(activeRadiusAtBoot).append(",\n");
            sb.append("  \"generatorIsGlobePreset\": ").append(generatorIsGlobePreset).append(",\n");
            sb.append("  \"columnProbes\": [\n");
            for (int i = 0; i < columnProbes.size(); i++) {
                sb.append(columnProbes.get(i).toJson());
                sb.append(i < columnProbes.size() - 1 ? ",\n" : "\n");
            }
            sb.append("  ],\n");
            sb.append("  \"transects\": [\n");
            for (int i = 0; i < transects.size(); i++) {
                sb.append(transects.get(i).toJson());
                sb.append(i < transects.size() - 1 ? ",\n" : "\n");
            }
            sb.append("  ],\n");
            sb.append("  \"landFraction\": ").append(landFraction == null ? "null" : landFraction.toJson()).append(",\n");
            sb.append("  \"structuralProbes\": [\n");
            for (int i = 0; i < structuralProbes.size(); i++) {
                sb.append(structuralProbes.get(i).toJson());
                sb.append(i < structuralProbes.size() - 1 ? ",\n" : "\n");
            }
            sb.append("  ]\n");
            sb.append("}\n");
            return sb.toString();
        }
    }

    static final class ColumnProbe {
        String label;
        int x;
        int z;
        double land01;
        // Per-Y sampled finalDensity values (Y -> value), and the derived surface Y (highest Y whose
        // density crosses from >=0 to <0 scanning downward), plus the vanilla getBaseHeight() at (x,z).
        final List<double[]> samples = new ArrayList<>(); // {y, density}
        int surfaceY;
        int baseHeight;

        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("    {\"label\": \"").append(label).append("\", \"x\": ").append(x)
                    .append(", \"z\": ").append(z).append(", \"land01\": ").append(land01)
                    .append(", \"surfaceY\": ").append(surfaceY).append(", \"baseHeight\": ").append(baseHeight)
                    .append(", \"samples\": [");
            for (int i = 0; i < samples.size(); i++) {
                double[] s = samples.get(i);
                sb.append("[").append((int) s[0]).append(", ").append(s[1]).append("]");
                sb.append(i < samples.size() - 1 ? ", " : "");
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    static final class TransectProbe {
        String label;
        int fixedZ;
        int xStart;
        int xEnd;
        int step;
        int y;
        double maxAbsFirstDifference;
        // Full series kept for external diffing / debugging, but kept compact (density only).
        final List<Double> densities = new ArrayList<>();

        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("    {\"label\": \"").append(label).append("\", \"fixedZ\": ").append(fixedZ)
                    .append(", \"xStart\": ").append(xStart).append(", \"xEnd\": ").append(xEnd)
                    .append(", \"step\": ").append(step).append(", \"y\": ").append(y)
                    .append(", \"maxAbsFirstDifference\": ").append(maxAbsFirstDifference)
                    .append(", \"densities\": [");
            for (int i = 0; i < densities.size(); i++) {
                sb.append(densities.get(i));
                sb.append(i < densities.size() - 1 ? ", " : "");
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    static final class LandFractionProbe {
        int totalColumns;
        int landColumns; // realHeight >= seaLevel
        double landFraction;
        // Columns sampled, with land01 and whether realHeight >= seaLevel, for external flip-comparison.
        final List<double[]> columns = new ArrayList<>(); // {x, z, land01, isLandHeight(0/1)}

        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"totalColumns\": ").append(totalColumns).append(", \"landColumns\": ").append(landColumns)
                    .append(", \"landFraction\": ").append(landFraction).append(", \"columns\": [\n");
            for (int i = 0; i < columns.size(); i++) {
                double[] c = columns.get(i);
                sb.append("      [").append((int) c[0]).append(", ").append((int) c[1]).append(", ")
                        .append(c[2]).append(", ").append((int) c[3]).append("]");
                sb.append(i < columns.size() - 1 ? ",\n" : "\n");
            }
            sb.append("    ]}");
            return sb.toString();
        }
    }

    static final class StructuralColumnProbe {
        String label;
        int x;
        int z;
        // Block state name (registry id string) per Y, from generator.getBaseColumn(...). Compact
        // representation: only the range around the surface is recorded to keep reports small.
        final List<String> blockNames = new ArrayList<>();
        int minY;

        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("    {\"label\": \"").append(label).append("\", \"x\": ").append(x)
                    .append(", \"z\": ").append(z).append(", \"minY\": ").append(minY)
                    .append(", \"blockNames\": [");
            for (int i = 0; i < blockNames.size(); i++) {
                sb.append("\"").append(blockNames.get(i)).append("\"");
                sb.append(i < blockNames.size() - 1 ? ", " : "");
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    private static String jsonStringOrNull(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String describeThrowable(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString());
        for (StackTraceElement e : t.getStackTrace()) {
            sb.append(" at ").append(e);
            if (sb.length() > 2000) {
                sb.append(" ...(truncated)");
                break;
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------
    // Core harness logic
    // ------------------------------------------------------------------------------------------------

    private static void runAll(ServerLevel world, Report report) throws Exception {
        String shapeRaw = System.getProperty(SHAPE_PROP, "classic").trim();
        boolean nonGlobe = Boolean.parseBoolean(System.getProperty(NONGLOBE_PROP, "false"));
        LatitudeBiomes.GlobeShape shape = LatitudeBiomes.shapeFromString(shapeRaw);

        // Use the REAL booted world's own seed/radius, already correctly armed by
        // GlobeMod.initLatitudeBiomesForWorld (fires on ServerLevelEvents.LOAD, before SERVER_STARTED --
        // see that method's own comment: "before Minecraft pre-generates spawn chunks"). Re-driving
        // setWorldSeed/setActiveRadiusBlocks here from a hardcoded/requested value would FIGHT that real
        // initialization (e.g. the "regular" preset's actual border radius is 7500, not a guessed
        // round number) and risk silently testing against the wrong radius. Only GlobeShape is not
        // stamped by a headless boot (that normally only happens via the create-world UI flow), so it is
        // still set explicitly here for the Classic/Mercator comparison this task requires.
        long seed = world.getSeed();
        int radius = LatitudeBiomes.getActiveRadiusBlocks();

        report.seed = seed;
        report.nonGlobeWorld = nonGlobe;

        ChunkGenerator genRaw = world.getChunkSource().getGenerator();
        if (!(genRaw instanceof NoiseBasedChunkGenerator generator)) {
            report.harnessFailure = "world generator is not a NoiseBasedChunkGenerator: " + genRaw;
            return;
        }

        // Definitively classify the booted world (sweeper findings #2-6/#17). generatorIsGlobePreset is the
        // real positive globe check the shipped mixin uses; activeRadiusAtBoot is the dev/atlas mechanism.
        boolean generatorIsGlobePreset = GlobeMod.isGlobeNoiseGenerator(generator);
        report.generatorIsGlobePreset = generatorIsGlobePreset;
        report.activeRadiusAtBoot = radius;

        if (nonGlobe) {
            // Globe-gate-isolation leg: the world MUST have booted as a genuine non-globe world (plain
            // minecraft:normal level-type -- driven by -Dlatdev.preview.levelType=minecraft:normal in the
            // gradle invocation). A genuine non-globe boot leaves the radius unarmed (0) AND the generator is
            // not one of the six globe:overworld* presets, so NEITHER half of the two-mechanism globe check
            // can fire. We ASSERT that here rather than trusting the label: if the boot came up armed (e.g.
            // stale run-headless/server.properties still on a globe level-type), the leg would test an armed
            // world and pass for the wrong reason -- so instead we set harnessFailure, which a comparator is
            // required to treat as INVALID data (finding #12). This closes findings #2-6/#17.
            boolean bootIsGenuinelyNonGlobe = (radius <= 0) && !generatorIsGlobePreset && !GlobeMod.isGlobeOverworld(world);
            report.nonGlobeBootVerified = bootIsGenuinelyNonGlobe;
            report.radius = radius; // expected 0
            report.shape = LatitudeBiomes.shapeToString(LatitudeBiomes.getGlobeShape());
            GlobeMod.LOGGER.info(
                    "[latdev][terrainProof] non-globe leg: activeRadius={} generatorIsGlobePreset={} isGlobeOverworld={} (all must be 0/false)",
                    radius, generatorIsGlobePreset, GlobeMod.isGlobeOverworld(world));
            if (!bootIsGenuinelyNonGlobe) {
                report.harnessFailure = "non-globe leg booted an ARMED/globe world (radius=" + radius
                        + ", generatorIsGlobePreset=" + generatorIsGlobePreset
                        + ", isGlobeOverworld=" + GlobeMod.isGlobeOverworld(world)
                        + "); this leg is INVALID -- boot a real minecraft:normal world "
                        + "(-Dlatdev.preview.levelType=minecraft:normal) so the globe gate is genuinely exercised.";
                GlobeMod.LOGGER.error("[latdev][terrainProof] {}", report.harnessFailure);
                return;
            }
        } else {
            // Armed leg: MUST have booted a real globe world, else the "armed" probes below would silently
            // run against a non-globe world and prove nothing. Assert it (finding #2-6 for the positive side).
            LatitudeBiomes.setGlobeShape(shape);
            report.nonGlobeBootVerified = false; // n/a for the armed leg
            report.radius = radius;
            report.shape = LatitudeBiomes.shapeToString(shape);
            if (!generatorIsGlobePreset && radius <= 0 && !GlobeMod.isGlobeOverworld(world)) {
                report.harnessFailure = "armed leg expected a real globe world but booted a non-globe one "
                        + "(radius=" + radius + ", generatorIsGlobePreset=false); boot a globe level-type "
                        + "(-Dlatdev.preview.levelType=globe:globe_regular) so the wrapper can actually arm.";
                GlobeMod.LOGGER.error("[latdev][terrainProof] {}", report.harnessFailure);
                return;
            }
        }

        RandomState randomState = RandomState.create(
                generator.generatorSettings().value(),
                world.registryAccess().lookupOrThrow(Registries.NOISE),
                seed);

        // Reuse the EXACT same install call the real dev/atlas tooling path uses (see
        // com.example.globe.dev.BiomePreviewExporter) so this harness proves the actual shipped
        // integration point, not a hand-rolled substitute -- and capture its DEFINITIVE result (findings
        // #2-6/#8) so a comparator can assert wrapperInstalled directly, not scrape a once-per-JVM log.
        InstallResult installResult = TerrainRouterWrapping.installIfArmed(randomState, world);
        report.installResult = installResult.name();
        report.wrapperInstalled = (installResult == InstallResult.INSTALLED);
        GeoSummaryProvider bootProvider = LatitudeBiomes.geoProviderForTerrain();
        report.geoProviderClass = (bootProvider == null) ? "null" : bootProvider.getClass().getSimpleName();
        GlobeMod.LOGGER.info(
                "[latdev][terrainProof] install result={} wrapperInstalled={} geoProvider={}",
                installResult, report.wrapperInstalled, report.geoProviderClass);

        DensityFunction finalDensity = randomState.router().finalDensity();

        int xRadius = LatitudeBiomes.getActiveXRadiusBlocks();
        int zRadius = LatitudeBiomes.getActiveRadiusBlocks();

        // If this is the non-globe leg, LatitudeBiomes' own radius accessors report 0 (never armed, by
        // design -- see above), so derive local xRadius/zRadius purely for CHOOSING probe columns from
        // the requested config radius/shape instead (columns are otherwise picked the same way; the
        // ASSERTION under test on the non-globe leg is simply "does finalDensity change AT ALL", so
        // exact geography doesn't matter here -- it must NOT change, full stop).
        if (zRadius <= 0) {
            // Empty-string-safe parse (sweeper finding #1): the forwarded -D can be present-but-empty.
            int fallbackRadius = intPropOrDefault(RADIUS_PROP, DEFAULT_RADIUS);
            zRadius = fallbackRadius;
            xRadius = shape == LatitudeBiomes.GlobeShape.MERCATOR
                    ? (int) Math.round(fallbackRadius * LatitudeBiomes.MERCATOR_ASPECT)
                    : fallbackRadius;
        }

        // A standalone GeoAuthority (pure Java, no Minecraft dependency) lets us pick columns spanning
        // land01 by direct computation, independent of whether GEO_V2_ENABLED armed the shipped
        // GEO_V2_PROVIDER for this run (needed for the geoV2-off and non-globe legs, where the shipped
        // provider is intentionally the NEUTRAL no-op).
        GeoAuthority referenceAuthority = new GeoAuthority(seed, zRadius, xRadius);

        runColumnProbes(report, finalDensity, referenceAuthority, generator, randomState, world, zRadius, xRadius);
        runTransectProbes(report, finalDensity, referenceAuthority, zRadius, xRadius);
        runLandFractionProbe(report, generator, randomState, world, referenceAuthority, zRadius, xRadius);
        runStructuralProbes(report, generator, randomState, world, referenceAuthority, zRadius, xRadius);
    }

    // --- §6.2 direct density-output numeric probe ----------------------------------------------------

    private static void runColumnProbes(Report report, DensityFunction finalDensity, GeoAuthority authority,
                                         NoiseBasedChunkGenerator generator, RandomState randomState, ServerLevel world,
                                         int zRadius, int xRadius) {
        int[] ys = {40, 48, 56, 60, 62, 64, 66, 70, 80, 100};
        for (ColumnPick pick : findColumnsByLand01(authority, zRadius, xRadius)) {
            ColumnProbe probe = new ColumnProbe();
            probe.label = pick.label;
            probe.x = pick.x;
            probe.z = pick.z;
            probe.land01 = pick.land01;
            int surfaceY = Integer.MIN_VALUE;
            for (int y : ys) {
                double d = finalDensity.compute(ctx(pick.x, y, pick.z));
                probe.samples.add(new double[]{y, d});
                if (d >= 0.0) {
                    surfaceY = Math.max(surfaceY, y);
                }
            }
            probe.surfaceY = surfaceY;
            // The real "spawn/heightmap agrees with the rendered surface" evidence (design §6.2): both
            // getBaseHeight() and the density-crossing surfaceY above resolve through the SAME field,
            // finalDensity -- so they should track each other (getBaseHeight need not equal surfaceY
            // exactly, since it walks the full interpolated column rather than this probe's coarse Y
            // list, but the two must move in the same direction across configurations).
            probe.baseHeight = generator.getBaseHeight(pick.x, pick.z, Heightmap.Types.WORLD_SURFACE_WG, world, randomState);
            report.columnProbes.add(probe);
        }
    }

    /** Finds representative columns for land01 near 1.0, near 0.0, and near 0.5, by direct search. */
    private static List<ColumnPick> findColumnsByLand01(GeoAuthority authority, int zRadius, int xRadius) {
        List<ColumnPick> picks = new ArrayList<>();
        ColumnPick bestLand = null, bestOcean = null, bestNeutral = null;
        double bestLandScore = -1, bestOceanScore = -1, bestNeutralScore = Double.MAX_VALUE;
        // Interior-only search (well inside the edge/pole ramp) so these three reference columns are
        // NOT contaminated by the edge/pole ocean ramp -- that is exercised deliberately by the
        // transect probes instead.
        int limit = (int) (Math.min(zRadius, xRadius) * 0.5);
        int step = Math.max(32, limit / 60);
        for (int x = -limit; x <= limit; x += step) {
            for (int z = -limit; z <= limit; z += step) {
                double land01 = authority.sample(x, z).land01();
                if (land01 > bestLandScore) {
                    bestLandScore = land01;
                    bestLand = new ColumnPick("land(~1.0)", x, z, land01);
                }
                if (land01 < bestOceanScore || bestOceanScore < 0) {
                    bestOceanScore = land01;
                    bestOcean = new ColumnPick("ocean(~0.0)", x, z, land01);
                }
                double distFromHalf = Math.abs(land01 - 0.5);
                if (distFromHalf < bestNeutralScore) {
                    bestNeutralScore = distFromHalf;
                    bestNeutral = new ColumnPick("neutral(~0.5)", x, z, land01);
                }
            }
        }
        if (bestLand != null) picks.add(bestLand);
        if (bestOcean != null) picks.add(bestOcean);
        if (bestNeutral != null) picks.add(bestNeutral);
        return picks;
    }

    private record ColumnPick(String label, int x, int z, double land01) {
    }

    // --- §6.2 three-transect Lipschitz / smoothness probe ---------------------------------------------

    private static void runTransectProbes(Report report, DensityFunction finalDensity, GeoAuthority authority,
                                           int zRadius, int xRadius) {
        int y = 64;

        // (i) an ordinary interior coast: sweep x through the interior at z=0, dense step.
        addTransect(report, finalDensity, "interior-coast", 0, -(int) (xRadius * 0.4), (int) (xRadius * 0.4), 4, y);

        // (ii) a transect through the EDGE_START..1.0 edge band (per §0: EDGE_START=0.80) and the POLE
        // band (POLE_START=0.92) -- sweep x from just inside EDGE_START*xRadius out past the border, at
        // a z near the pole band, so both ramps are exercised in one transect.
        int edgeXStart = (int) (xRadius * 0.75);
        int edgeXEnd = (int) (xRadius * 1.02);
        int poleZ = (int) (zRadius * 0.94);
        addTransect(report, finalDensity, "edge-pole-band", poleZ, edgeXStart, edgeXEnd, 4, y);

        // (iii) a transect swept across several (int)Math.round domain-warp snap crossings: a fine
        // single-block sweep over a modest span is enough to cross multiple integer-rounding snaps of
        // the warp field (the warp amplitude/period are geometry constants scaled off zRadius, so a
        // span on the order of a few hundred blocks at step=1 reliably crosses several).
        addTransect(report, finalDensity, "warp-snap-crossings", 0, -400, 400, 1, y);
    }

    private static void addTransect(Report report, DensityFunction finalDensity, String label,
                                     int fixedZ, int xStart, int xEnd, int step, int y) {
        TransectProbe probe = new TransectProbe();
        probe.label = label;
        probe.fixedZ = fixedZ;
        probe.xStart = xStart;
        probe.xEnd = xEnd;
        probe.step = step;
        probe.y = y;
        double prev = Double.NaN;
        double maxAbsDiff = 0.0;
        for (int x = xStart; x <= xEnd; x += step) {
            double d = finalDensity.compute(ctx(x, y, fixedZ));
            probe.densities.add(d);
            if (!Double.isNaN(prev)) {
                maxAbsDiff = Math.max(maxAbsDiff, Math.abs(d - prev));
            }
            prev = d;
        }
        probe.maxAbsFirstDifference = maxAbsDiff;
        report.transects.add(probe);
    }

    // --- §6.2 / §6.3 land-fraction and ocean-authority-veto-flip assertion -----------------------------

    private static void runLandFractionProbe(Report report, NoiseBasedChunkGenerator generator, RandomState randomState,
                                              ServerLevel world, GeoAuthority authority, int zRadius, int xRadius) {
        int seaLevel = generator.generatorSettings().value().seaLevel();
        int limit = (int) (Math.min(zRadius, xRadius) * 0.6);
        // Grid density kept modest (~21x21 ~ 441 columns): each getBaseHeight() call walks a real
        // noise-interpolated column, and when the wrapper is armed each finalDensity sample additionally
        // queries GeoAuthority -- a dense grid here dominates the harness's runtime. 441 columns is still
        // a statistically meaningful land-fraction/monotonicity sample.
        int step = Math.max(48, limit / 10);
        LandFractionProbe probe = new LandFractionProbe();
        int total = 0;
        int land = 0;
        for (int x = -limit; x <= limit; x += step) {
            for (int z = -limit; z <= limit; z += step) {
                double land01 = authority.sample(x, z).land01();
                int realHeight = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, world, randomState);
                boolean isLandHeight = realHeight >= seaLevel;
                probe.columns.add(new double[]{x, z, land01, isLandHeight ? 1 : 0});
                total++;
                if (isLandHeight) {
                    land++;
                }
            }
        }
        probe.totalColumns = total;
        probe.landColumns = land;
        probe.landFraction = total == 0 ? 0.0 : (double) land / total;
        report.landFraction = probe;
    }

    // --- §6.1(b)(ii) structural NoiseChunk-build check -------------------------------------------------

    private static void runStructuralProbes(Report report, NoiseBasedChunkGenerator generator, RandomState randomState,
                                             ServerLevel world, GeoAuthority authority, int zRadius, int xRadius) {
        for (ColumnPick pick : findColumnsByLand01(authority, zRadius, xRadius)) {
            StructuralColumnProbe probe = new StructuralColumnProbe();
            probe.label = pick.label;
            probe.x = pick.x;
            probe.z = pick.z;
            NoiseColumn column = generator.getBaseColumn(pick.x, pick.z, world, randomState);
            probe.minY = world.getMinY();
            int minY = world.getMinY();
            int maxY = world.getMaxY();
            for (int y = minY; y <= maxY; y++) {
                var state = column.getBlock(y);
                if (state == null) {
                    probe.blockNames.add("null");
                    continue;
                }
                var key = state.getBlock().builtInRegistryHolder().unwrapKey();
                probe.blockNames.add(key.isPresent() ? key.get().toString() : state.toString());
            }
            report.structuralProbes.add(probe);
        }
    }

    private static DensityFunction.FunctionContext ctx(int x, int y, int z) {
        return new SimpleCtx(x, y, z);
    }

    private record SimpleCtx(int blockX, int blockY, int blockZ) implements DensityFunction.FunctionContext {
    }
}
