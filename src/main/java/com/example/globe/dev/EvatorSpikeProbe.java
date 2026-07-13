package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.terrain.EvatorSpike;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================ SPIKE — THROWAWAY PROOF CODE (B-6 P1 STEP 0) ============================
 *
 * <p>The make-or-break B-6 terrain-mirror probe. Question: does a {@code finalDensity}-seam context-remap
 * (mechanism "a", {@link EvatorSpike}/{@link com.example.globe.terrain.EvatorMirrorSpikeFunction}) produce
 * an EAST band whose terrain SHAPE is the mirror of the canonical WEST band on the REAL chunk-fill path
 * (batched {@code NoiseChunk}), and NOT just on the single-column direct-{@code compute} path?
 *
 * <p>The identity under test (reflection about world X = 0, whole east half): for a set of samples
 * {@code (x>0, z)}, {@code fill(+x, y, z)} should equal {@code fill(-x, y, z)}.
 *
 * <p>Four independent measurements in ONE JVM (spike ON):
 * <ul>
 *   <li><b>A0 direct/plain</b> — {@code plainFinalDensity.compute} at {@code +x} vs {@code -x}, NO wrapper.
 *       Expected MISMATCH — proves the metric sees genuine terrain asymmetry (so a later MATCH is caused
 *       by the reflection, not by symmetric terrain).</li>
 *   <li><b>A1 direct/spike</b> — {@code mirrorFinalDensity.compute} at {@code +x} vs {@code -x}. This is the
 *       marker-transparent single-column path the DESIGN's atlas proof would use. Expected MATCH — i.e. the
 *       designed proof would GREEN here.</li>
 *   <li><b>B fill/spike (getBaseColumn)</b> — the same wrapper, but read through {@code getBaseColumn},
 *       which (verified 26.2) builds a {@code NoiseChunk} and reads {@code getInterpolatedState()} — the
 *       batched interpolation path. Expected: the real answer.</li>
 *   <li><b>C fill/spike (live getChunk NOISE)</b> — swap the WORLD's {@code RandomState} router to the
 *       wrapper and generate genuine chunks through {@code ServerChunkCache.getChunk(.., NOISE, ..)} =
 *       {@code NoiseBasedChunkGenerator.fillFromNoise} = {@code doFill}. The unarguable live-gameplay fill
 *       path.</li>
 * </ul>
 * Plus an <b>interpolation-error control</b>: on each canonical WEST column, direct-raw surfaceY vs
 * getBaseColumn-interpolated surfaceY — isolates how much the raw-vs-interpolated asymmetry (which the
 * wrapper's reflected-context fallback forces onto the east side) can move a surface.
 *
 * <p>Trigger {@code -Dlatitude.evatorSpike.probe=true} (requires {@code -Dlatitude.evatorSpike=true} for
 * the wrapper to reflect). Writes {@code -Dlatitude.evatorSpike.out} (or a default) and logs a summary.
 */
public final class EvatorSpikeProbe {

    private EvatorSpikeProbe() {
    }

    private static final int TOP_N = 16;

    public static boolean isTriggered() {
        return Boolean.parseBoolean(System.getProperty("latitude.evatorSpike.probe", "false"));
    }

    private record SimpleCtx(int blockX, int blockY, int blockZ) implements DensityFunction.FunctionContext {
    }

    private record Pair(int d, int z, int eastX, int westX) {
    }

    /** Result of comparing one east/west column pair: surface Ys and whether the top-N block stacks match. */
    private static final class Cmp {
        int d;
        int z;
        int eastX;
        int westX;
        int eastSurfaceY;
        int westSurfaceY;
        boolean surfaceMatch;
        boolean stackMatch;
        String eastTop;
        String westTop;
    }

    public static void runAndStop(MinecraftServer server) {
        StringBuilder json = new StringBuilder();
        try {
            ServerLevel world = server.overworld();
            if (world == null) {
                GlobeMod.LOGGER.error("[evatorSpike] no overworld; stopping");
                return;
            }
            boolean armed = EvatorSpike.armed();
            long seed = world.getSeed();
            ChunkGenerator genRaw = world.getChunkSource().getGenerator();
            if (!(genRaw instanceof NoiseBasedChunkGenerator generator)) {
                GlobeMod.LOGGER.error("[evatorSpike] generator is not NoiseBasedChunkGenerator: {}", genRaw);
                return;
            }
            int xRadius = LatitudeBiomes.getActiveXRadiusBlocks();
            if (xRadius <= 0) {
                xRadius = 7500; // headless minecraft:normal may not arm a radius; pick a representative one
                GlobeMod.LOGGER.warn("[evatorSpike] activeXRadius<=0; using fallback xRadius={}", xRadius);
            }

            int minY = world.getMinY();
            int maxY = world.getMaxY();

            List<Pair> pairs = new ArrayList<>();
            int[] ds = {16, 40, 80, 140, 220};
            int[] zs = {0, 640, -640};
            for (int d : ds) {
                for (int z : zs) {
                    int ex = xRadius - d;      // east half (>0), reflected by the wrapper
                    int wx = -(xRadius - d);   // west half (<0), canonical
                    pairs.add(new Pair(d, z, ex, wx));
                }
            }

            GlobeMod.LOGGER.info("[evatorSpike] START armed={} seed={} xRadius={} minY={} maxY={} pairs={}",
                    armed, seed, xRadius, minY, maxY, pairs.size());

            // --- Standalone RandomStates -------------------------------------------------------------
            RandomState rsPlain = RandomState.create(
                    generator.generatorSettings().value(),
                    world.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            DensityFunction plainFinal = rsPlain.router().finalDensity();

            RandomState rsSpike = RandomState.create(
                    generator.generatorSettings().value(),
                    world.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            DensityFunction mirrorFinal = EvatorSpike.installMirror(rsSpike); // wraps rsSpike's finalDensity

            // ================= A0: direct compute, PLAIN (expect MISMATCH) ==========================
            List<Cmp> a0 = new ArrayList<>();
            for (Pair p : pairs) {
                a0.add(compareDirect(plainFinal, p, minY, maxY));
            }
            logSection("A0 direct/plain  (expect MISMATCH: terrain is asymmetric)", a0);

            // ================= A1: direct compute, MIRROR (expect MATCH = design false-green) =======
            List<Cmp> a1 = new ArrayList<>();
            for (Pair p : pairs) {
                a1.add(compareDirect(mirrorFinal, p, minY, maxY));
            }
            logSection("A1 direct/spike  (marker-transparent single-column path; the DESIGN proof path)", a1);

            // ================= B: getBaseColumn fill path, MIRROR ===================================
            List<Cmp> b = new ArrayList<>();
            for (Pair p : pairs) {
                b.add(compareColumn(
                        readGenBaseColumn(generator, world, rsSpike, p.eastX(), p.z(), minY, maxY),
                        readGenBaseColumn(generator, world, rsSpike, p.westX(), p.z(), minY, maxY),
                        p));
            }
            logSection("B  fill/spike getBaseColumn (NoiseChunk getInterpolatedState — batched fill path)", b);

            // ================= interpolation-error control (canonical WEST only) ====================
            int interpDeltas = 0;
            int interpMax = 0;
            StringBuilder interpEx = new StringBuilder();
            for (Pair p : pairs) {
                int raw = directSurfaceY(plainFinal, p.westX(), p.z(), minY, maxY);
                int interp = surfaceY(readGenBaseColumn(generator, world, rsPlain, p.westX(), p.z(), minY, maxY), minY);
                int delta = Math.abs(raw - interp);
                if (delta != 0) {
                    interpDeltas++;
                    interpMax = Math.max(interpMax, delta);
                    if (interpEx.length() < 300) {
                        interpEx.append(String.format("(wx=%d,z=%d raw=%d interp=%d d=%d) ",
                                p.westX(), p.z(), raw, interp, delta));
                    }
                }
            }
            GlobeMod.LOGGER.info("[evatorSpike] INTERP-CONTROL (west canonical: raw-vs-interpolated surfaceY): "
                    + "nonzeroDeltas={}/{} maxDelta={} ex={}", interpDeltas, pairs.size(), interpMax, interpEx);

            // ================= C: LIVE getChunk(NOISE) fill path, MIRROR ============================
            List<Cmp> c = new ArrayList<>();
            String cError = null;
            try {
                RandomState liveRs = world.getChunkSource().randomState();
                EvatorSpike.installMirror(liveRs); // swap the WORLD router (affects future chunk gen)
                int[] cds = {40, 140};
                int[] czs = {0, 640};
                for (int d : cds) {
                    for (int z : czs) {
                        int ex = xRadius - d;
                        int wx = -(xRadius - d);
                        Pair p = new Pair(d, z, ex, wx);
                        int[] eCol = readLiveColumn(world, ex, z, minY, maxY);
                        int[] wCol = readLiveColumn(world, wx, z, minY, maxY);
                        c.add(compareColumn(eCol, wCol, p));
                    }
                }
                logSection("C  fill/spike LIVE getChunk(NOISE) (ServerChunkCache -> fillFromNoise -> doFill)", c);
            } catch (Throwable t) {
                cError = TerrainProofHarnessFmt.describe(t);
                GlobeMod.LOGGER.error("[evatorSpike] measurement C (live getChunk) failed", t);
            }

            // --- Verdict summary ---------------------------------------------------------------------
            int a1Match = countSurfaceMatch(a1);
            int bMatch = countSurfaceMatch(b);
            int cMatch = countSurfaceMatch(c);
            int a1Stack = countStackMatch(a1);
            int bStack = countStackMatch(b);
            int cStack = countStackMatch(c);
            GlobeMod.LOGGER.info("[evatorSpike] ===== VERDICT DATA =====");
            GlobeMod.LOGGER.info("[evatorSpike] A0 direct/plain  surfaceMatch={}/{}", countSurfaceMatch(a0), a0.size());
            GlobeMod.LOGGER.info("[evatorSpike] A1 direct/spike  surfaceMatch={}/{}  stackMatch={}/{}", a1Match, a1.size(), a1Stack, a1.size());
            GlobeMod.LOGGER.info("[evatorSpike] B  fill/spike    surfaceMatch={}/{}  stackMatch={}/{}", bMatch, b.size(), bStack, b.size());
            GlobeMod.LOGGER.info("[evatorSpike] C  live/spike    surfaceMatch={}/{}  stackMatch={}/{}  err={}",
                    cMatch, c.size(), cStack, c.size(), cError);

            json.append("{\n");
            json.append("  \"armed\": ").append(armed).append(",\n");
            json.append("  \"seed\": ").append(seed).append(",\n");
            json.append("  \"xRadius\": ").append(xRadius).append(",\n");
            json.append("  \"a0_direct_plain\": ").append(sectionJson(a0)).append(",\n");
            json.append("  \"a1_direct_spike\": ").append(sectionJson(a1)).append(",\n");
            json.append("  \"b_fill_getBaseColumn_spike\": ").append(sectionJson(b)).append(",\n");
            json.append("  \"c_live_getChunk_spike\": ").append(sectionJson(c)).append(",\n");
            json.append("  \"c_error\": ").append(cError == null ? "null" : ("\"" + cError.replace("\"", "'") + "\"")).append(",\n");
            json.append("  \"interp_control_nonzero\": ").append(interpDeltas).append(",\n");
            json.append("  \"interp_control_max\": ").append(interpMax).append("\n");
            json.append("}\n");

            Path out = resolveOut(server);
            Files.createDirectories(out.getParent());
            Files.write(out, json.toString().getBytes(StandardCharsets.UTF_8));
            GlobeMod.LOGGER.info("[evatorSpike] wrote report {}", out);
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[evatorSpike] probe failed", t);
        } finally {
            server.halt(false);
        }
    }

    // ---- comparison helpers -------------------------------------------------------------------------

    private static Cmp compareDirect(DensityFunction fd, Pair p, int minY, int maxY) {
        Cmp c = new Cmp();
        c.d = p.d(); c.z = p.z(); c.eastX = p.eastX(); c.westX = p.westX();
        c.eastSurfaceY = directSurfaceY(fd, p.eastX(), p.z(), minY, maxY);
        c.westSurfaceY = directSurfaceY(fd, p.westX(), p.z(), minY, maxY);
        c.surfaceMatch = c.eastSurfaceY == c.westSurfaceY;
        // density "stack" signature: sign bits over a coarse ladder around the surface
        c.eastTop = directSignSig(fd, p.eastX(), p.z(), minY, maxY);
        c.westTop = directSignSig(fd, p.westX(), p.z(), minY, maxY);
        c.stackMatch = c.eastTop.equals(c.westTop);
        return c;
    }

    private static Cmp compareColumn(int[] eastCol, int[] westCol, Pair p) {
        // Columns are encoded as {surfaceY, hash(topN ids)} by readers; we also keep the id string for logs.
        Cmp c = new Cmp();
        c.d = p.d(); c.z = p.z(); c.eastX = p.eastX(); c.westX = p.westX();
        c.eastSurfaceY = eastCol[0];
        c.westSurfaceY = westCol[0];
        c.surfaceMatch = c.eastSurfaceY == c.westSurfaceY;
        c.stackMatch = eastCol[1] == westCol[1];
        c.eastTop = "y" + eastCol[0] + "/h" + eastCol[1];
        c.westTop = "y" + westCol[0] + "/h" + westCol[1];
        return c;
    }

    // ---- direct-compute column sampling -------------------------------------------------------------

    private static int directSurfaceY(DensityFunction fd, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            if (fd.compute(new SimpleCtx(x, y, z)) >= 0.0) {
                return y;
            }
        }
        return minY - 1;
    }

    private static String directSignSig(DensityFunction fd, int x, int z, int minY, int maxY) {
        int s = directSurfaceY(fd, x, z, minY, maxY);
        StringBuilder sb = new StringBuilder();
        for (int y = s; y > s - TOP_N; y--) {
            sb.append(fd.compute(new SimpleCtx(x, y, z)) >= 0.0 ? '1' : '0');
        }
        return sb.toString();
    }

    // ---- real fill-path column readers (return {surfaceY, topN-id-hash}) ----------------------------

    private static int[] readGenBaseColumn(NoiseBasedChunkGenerator gen, ServerLevel world, RandomState rs,
                                           int x, int z, int minY, int maxY) {
        NoiseColumn col = gen.getBaseColumn(x, z, world, rs);
        return columnSignature(y -> col.getBlock(y), minY, maxY);
    }

    private static int[] readLiveColumn(ServerLevel world, int x, int z, int minY, int maxY) {
        int cx = Math.floorDiv(x, 16);
        int cz = Math.floorDiv(z, 16);
        ChunkAccess chunk = world.getChunkSource().getChunk(cx, cz, ChunkStatus.NOISE, true);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return columnSignature(y -> chunk.getBlockState(pos.set(x, y, z)), minY, maxY);
    }

    private interface YBlock {
        BlockState at(int y);
    }

    private static int[] columnSignature(YBlock col, int minY, int maxY) {
        int surface = minY - 1;
        for (int y = maxY; y >= minY; y--) {
            BlockState s = col.at(y);
            if (s != null && !s.isAir() && s.getFluidState().isEmpty()) {
                surface = y;
                break;
            }
        }
        int hash = 1;
        if (surface >= minY) {
            for (int y = surface; y > surface - TOP_N && y >= minY; y--) {
                BlockState s = col.at(y);
                String id = (s == null) ? "null" : s.getBlock().builtInRegistryHolder()
                        .unwrapKey().map(Object::toString).orElseGet(s::toString);
                hash = 31 * hash + id.hashCode();
            }
        }
        return new int[]{surface, hash};
    }

    private static int surfaceY(int[] sig, int minY) {
        return sig[0];
    }

    // ---- reporting ----------------------------------------------------------------------------------

    private static void logSection(String title, List<Cmp> rows) {
        int sm = countSurfaceMatch(rows);
        int stm = countStackMatch(rows);
        GlobeMod.LOGGER.info("[evatorSpike] --- {} : surfaceMatch={}/{} stackMatch={}/{}",
                title, sm, rows.size(), stm, rows.size());
        int shown = 0;
        for (Cmp c : rows) {
            if ((!c.surfaceMatch || !c.stackMatch) && shown < 6) {
                GlobeMod.LOGGER.info("[evatorSpike]      MISS d={} z={} eastX={} westX={} eSurf={} wSurf={} east={} west={}",
                        c.d, c.z, c.eastX, c.westX, c.eastSurfaceY, c.westSurfaceY, c.eastTop, c.westTop);
                shown++;
            }
        }
    }

    private static int countSurfaceMatch(List<Cmp> rows) {
        int n = 0;
        for (Cmp c : rows) if (c.surfaceMatch) n++;
        return n;
    }

    private static int countStackMatch(List<Cmp> rows) {
        int n = 0;
        for (Cmp c : rows) if (c.stackMatch) n++;
        return n;
    }

    private static String sectionJson(List<Cmp> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            Cmp c = rows.get(i);
            sb.append("{\"d\":").append(c.d).append(",\"z\":").append(c.z)
                    .append(",\"eastX\":").append(c.eastX).append(",\"westX\":").append(c.westX)
                    .append(",\"eSurf\":").append(c.eastSurfaceY).append(",\"wSurf\":").append(c.westSurfaceY)
                    .append(",\"surfaceMatch\":").append(c.surfaceMatch)
                    .append(",\"stackMatch\":").append(c.stackMatch).append("}");
            if (i < rows.size() - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    private static Path resolveOut(MinecraftServer server) {
        String raw = System.getProperty("latitude.evatorSpike.out", "").trim();
        if (!raw.isEmpty()) {
            return Path.of(raw);
        }
        return server.getServerDirectory().resolve("latdev").resolve("evator-spike-report.json");
    }

    /** Tiny throwable formatter (avoid pulling TerrainProofHarness's private one). */
    static final class TerrainProofHarnessFmt {
        static String describe(Throwable t) {
            StringBuilder sb = new StringBuilder(t.toString());
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(6, st.length); i++) {
                sb.append(" at ").append(st[i]);
            }
            return sb.toString();
        }
    }
}
