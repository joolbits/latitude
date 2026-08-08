package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Opt-in retrofit for worlds whose already-generated chunks predate the ledger-decoration fix.
 *
 * <p>Before the fix, a biome admitted only through the provider-ticket ledger (no {@code lat_*}
 * policy-tag membership) generated with terrain and biome identity but none of its own features:
 * vanilla's decoration narrowed the chunk's biome set against the raw source's
 * {@code possibleBiomes} and consulted a feature index that had never seen the biome. The visible
 * result is a bare biome — the maintainer's Overgrown Greens. Chunks generated after the fix are correct;
 * chunks generated before it stay bare forever, because decoration is a generation-time,
 * once-per-chunk event.
 *
 * <p>This engine re-runs exactly what was dropped and nothing else: for a chunk whose stored
 * section palettes contain a retrofit-eligible biome, it replays that biome's
 * {@code VEGETAL_DECORATION} placed features through the normal runtime placement pipeline
 * ({@link PlacedFeature#placeWithBiomeCheck} — the same operation vanilla's {@code /place} command
 * performs). Safety comes from the features' own machinery, not from this class: every vegetal
 * feature carries biome and survivability filters, so placements self-limit to columns whose
 * stored biome matches and to positions that are genuinely plantable (air over valid ground).
 * Columns of neighboring biomes already declined these features at generation time via the same
 * filters, so the replay cannot double anything that already ran. Only the vegetal step is
 * replayed: shared underground content (ores, springs, carvers) ran at generation through the
 * chunk's other biomes and does not need repair.
 *
 * <p>Eligibility is deliberately historical: a biome qualifies when the provider-ticket ledger
 * routes it but none of {@link #DECORATION_POLICY_TAG_PATHS} contains it — the exact membership
 * test the pre-fix decoration guard applied. Biomes that were in those tags were protected all
 * along and never generated bare; replaying them would double-decorate. For the same reason the
 * tag list below must stay APPEND-ONLY history: removing an entry would widen retrofit onto
 * chunks that were never bare.
 *
 * <p>Chunks are marked with a persistent {@code globe:retrofit_decorated} attachment once handled
 * — and every newly generated chunk is marked at decoration time by
 * {@code ChunkGeneratorGenerateFeaturesBiomeSetMixin} — so no chunk is ever processed twice, and
 * post-fix chunks are never touched at all. Work is throttled to a few chunks per server tick.
 *
 * <p>On a world that never captured a provider-ticket profile (created by a dedicated server
 * before that fix), enabling the retrofit also adopts a profile so NEW chunks gain the full
 * roster. That world's old chunks contain no ledger biomes to retrofit; the seam between old and
 * new regions is inherent to what is being asked for, and the command's confirmation warning says
 * so.
 */
public final class LatitudeDecorationRetrofit {

    /**
     * The decoration guard's policy-tag membership list. Shared with
     * {@code ChunkGeneratorGenerateFeaturesBiomeSetMixin} (mixin classes must not be referenced
     * from ordinary code, so the constant lives here). APPEND-ONLY: retrofit eligibility is
     * defined as "ledger-routed but absent from these tags at the time the bare chunks were
     * generated" — see the class javadoc.
     */
    public static final String[] DECORATION_POLICY_TAG_PATHS = {
            "lat_tropics_primary",
            "lat_tropics_secondary",
            "lat_tropics_accent",
            "lat_arid_primary",
            "lat_arid_secondary",
            "lat_arid_accent",
            "lat_trans_arid_tropics_1_primary",
            "lat_trans_arid_tropics_1_secondary",
            "lat_trans_arid_tropics_1_accent",
            "lat_trans_arid_tropics_2_primary",
            "lat_trans_arid_tropics_2_secondary",
            "lat_trans_arid_tropics_2_accent",
            "lat_subtropical_humid_primary",
            "lat_subtropical_humid_secondary",
            "lat_subtropical_humid_accent",
            "lat_temperate_primary",
            "lat_temperate_secondary",
            "lat_temperate_accent",
            "lat_temperate_mountain",
            "lat_subpolar_primary",
            "lat_subpolar_secondary",
            "lat_subpolar_accent",
            "lat_polar_primary",
            "lat_polar_secondary",
            "lat_polar_accent",
            "lat_ocean_tropical",
            "lat_ocean_temperate",
            "lat_ocean_subpolar",
            "lat_ocean_polar"
    };

    /** Present (true) on any chunk whose eligible biomes were decorated under the fixed index. */
    public static final AttachmentType<Boolean> DECORATED_UNDER_FIXED_INDEX =
            AttachmentRegistry.createPersistent(
                    Identifier.fromNamespaceAndPath("globe", "retrofit_decorated"), Codec.BOOL);

    private static final int CHUNKS_PER_TICK = 2;
    private static final int ENABLE_SWEEP_RADIUS_CHUNKS = 10;
    private static final long CONFIRM_WINDOW_MS = 60_000L;

    // All mutated on the server thread only (commands, chunk-load events, end-of-tick).
    private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static volatile long pendingConfirmDeadlineMs;
    private static final AtomicInteger CHUNKS_SCANNED = new AtomicInteger();
    private static final AtomicInteger CHUNKS_RETROFITTED = new AtomicInteger();
    private static final AtomicInteger FEATURES_PLACED = new AtomicInteger();
    private static volatile List<Identifier> eligibleCache;

    private LatitudeDecorationRetrofit() {
    }

    public static void init() {
        // Touch the attachment type so registration happens during mod init, before any world
        // exists; the field initializer performs the actual registration.
        Identifier registered = DECORATED_UNDER_FIXED_INDEX.identifier();
        GlobeMod.LOGGER.debug("[Latitude] retrofit chunk marker registered: {}", registered);

        ServerChunkEvents.CHUNK_LOAD.register(LatitudeDecorationRetrofit::onChunkLoad);
        ServerTickEvents.END_SERVER_TICK.register(LatitudeDecorationRetrofit::onEndTick);
    }

    /** Marks a chunk as decorated under the fixed index; called from the decoration mixin. */
    public static void markDecoratedUnderFixedIndex(ChunkAccess chunk) {
        chunk.setAttached(DECORATED_UNDER_FIXED_INDEX, Boolean.TRUE);
    }

    public static boolean isEnabled(ServerLevel world) {
        LatitudeWorldState state = LatitudeWorldState.getIfPresent(world);
        return state != null && state.isRetrofitEnabled();
    }

    /** First step of the two-step enable; returns the warning lines to show the operator. */
    public static List<String> requestEnable(ServerLevel world) {
        pendingConfirmDeadlineMs = System.currentTimeMillis() + CONFIRM_WINDOW_MS;
        LatitudeWorldState state = LatitudeWorldState.get(world);
        boolean needsProfileAdoption = state.getProviderTicketProfile().isEmpty();
        List<String> lines = new ArrayList<>();
        lines.add("Latitude retrofit will re-run the missing plant decoration for provider biomes "
                + "that generated bare in already-explored chunks (only where the stored biome "
                + "matches; placements go into open air over valid ground).");
        if (needsProfileAdoption) {
            lines.add("This world has no provider-biome roster yet (it was created before Latitude "
                    + "captured one). Enabling will adopt a roster NOW: newly generated chunks will "
                    + "place provider biomes that older chunks do not have, so terrain generated "
                    + "before and after this switch will differ at the boundary.");
        }
        lines.add("BACK UP YOUR WORLD FIRST. This changes world data and cannot be undone by "
                + "flipping the switch back off.");
        lines.add("To proceed, run: /latitude retrofit confirm (within 60 seconds)");
        return lines;
    }

    /** Second step; performs the actual enable. Returns feedback lines. */
    public static List<String> confirmEnable(MinecraftServer server, ServerLevel world) {
        List<String> lines = new ArrayList<>();
        if (System.currentTimeMillis() > pendingConfirmDeadlineMs) {
            lines.add("No pending retrofit request (or it expired). Run /latitude retrofit enable first.");
            return lines;
        }
        pendingConfirmDeadlineMs = 0L;
        LatitudeWorldState state = LatitudeWorldState.get(world);
        if (state.getProviderTicketProfile().isEmpty()) {
            GlobeMod.adoptProviderTicketProfile(server, world, "retrofit adoption");
            lines.add("Provider-biome roster adopted: newly generated chunks will now place the "
                    + "full provider set.");
        }
        state.setRetrofitEnabled(true);
        eligibleCache = null;
        int seeded = sweepAroundPlayers(world);
        lines.add("Retrofit enabled. Already-loaded terrain near players has been queued ("
                + seeded + " chunks); other affected chunks are handled as they load.");
        return lines;
    }

    public static List<String> disable(ServerLevel world) {
        LatitudeWorldState state = LatitudeWorldState.get(world);
        state.setRetrofitEnabled(false);
        QUEUE.clear();
        QUEUED.clear();
        return List.of("Retrofit disabled. Chunks already retrofitted keep their decoration; "
                + "unprocessed chunks stay as they are.");
    }

    public static List<String> status(ServerLevel world) {
        LatitudeWorldState state = LatitudeWorldState.getIfPresent(world);
        boolean enabled = state != null && state.isRetrofitEnabled();
        boolean hasProfile = state != null && state.getProviderTicketProfile().isPresent();
        return List.of(
                "Retrofit: " + (enabled ? "ENABLED" : "disabled")
                        + " | provider roster: " + (hasProfile ? "present" : "absent"),
                "Queued: " + QUEUE.size() + " | scanned: " + CHUNKS_SCANNED.get()
                        + " | retrofitted: " + CHUNKS_RETROFITTED.get()
                        + " | features placed: " + FEATURES_PLACED.get(),
                "Eligible biomes: " + eligibleSummary(world));
    }

    // ── Event plumbing ──

    private static void onChunkLoad(ServerLevel world, LevelChunk chunk) {
        if (world != world.getServer().overworld() || !isEnabled(world)) {
            return;
        }
        if (Boolean.TRUE.equals(chunk.getAttached(DECORATED_UNDER_FIXED_INDEX))) {
            return;
        }
        enqueue(chunk.getPos());
    }

    private static void onEndTick(MinecraftServer server) {
        if (QUEUE.isEmpty()) {
            return;
        }
        ServerLevel world = server.overworld();
        if (world == null || !isEnabled(world)) {
            QUEUE.clear();
            QUEUED.clear();
            return;
        }
        for (int i = 0; i < CHUNKS_PER_TICK && !QUEUE.isEmpty(); i++) {
            long packed = QUEUE.pollFirst();
            QUEUED.remove(packed);
            processChunk(world, new ChunkPos(packed));
        }
    }

    private static void enqueue(ChunkPos pos) {
        long packed = pos.toLong();
        if (QUEUED.add(packed)) {
            QUEUE.addLast(packed);
        }
    }

    private static int sweepAroundPlayers(ServerLevel world) {
        int seeded = 0;
        for (ServerPlayer player : world.players()) {
            ChunkPos center = player.chunkPosition();
            for (int dx = -ENABLE_SWEEP_RADIUS_CHUNKS; dx <= ENABLE_SWEEP_RADIUS_CHUNKS; dx++) {
                for (int dz = -ENABLE_SWEEP_RADIUS_CHUNKS; dz <= ENABLE_SWEEP_RADIUS_CHUNKS; dz++) {
                    ChunkAccess chunk = world.getChunkSource()
                            .getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
                    if (chunk instanceof LevelChunk level
                            && !Boolean.TRUE.equals(level.getAttached(DECORATED_UNDER_FIXED_INDEX))) {
                        enqueue(level.getPos());
                        seeded++;
                    }
                }
            }
        }
        return seeded;
    }

    // ── The actual work ──

    private static void processChunk(ServerLevel world, ChunkPos pos) {
        ChunkAccess access = world.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        if (!(access instanceof LevelChunk chunk)) {
            return;
        }
        if (Boolean.TRUE.equals(chunk.getAttached(DECORATED_UNDER_FIXED_INDEX))) {
            return;
        }
        CHUNKS_SCANNED.incrementAndGet();
        try {
            List<Holder<Biome>> present = eligibleBiomesIn(world, chunk);
            if (!present.isEmpty()) {
                int placed = replayVegetalFeatures(world, pos, present);
                FEATURES_PLACED.addAndGet(placed);
                CHUNKS_RETROFITTED.incrementAndGet();
                GlobeMod.LOGGER.info("[Latitude] retrofit decorated chunk {},{}: biomes={} featuresPlaced={}",
                        pos.x, pos.z,
                        present.stream().map(h -> h.unwrapKey().map(k -> k.identifier().toString()).orElse("?")).toList(),
                        placed);
            }
        } catch (Exception e) {
            // Never crash the server for cosmetic repair; mark handled so a poison chunk cannot
            // wedge the queue by re-entering it on every load.
            GlobeMod.LOGGER.warn("[Latitude] retrofit failed for chunk {},{} (marking handled)", pos.x, pos.z, e);
        }
        markDecoratedUnderFixedIndex(chunk);
        chunk.markUnsaved();
    }

    private static List<Holder<Biome>> eligibleBiomesIn(ServerLevel world, LevelChunk chunk) {
        List<Identifier> eligible = eligibleBiomeIds(world);
        if (eligible.isEmpty()) {
            return List.of();
        }
        Registry<Biome> registry = world.registryAccess().lookupOrThrow(Registries.BIOME);
        Map<Identifier, Holder<Biome>> found = new LinkedHashMap<>();
        for (LevelChunkSection section : chunk.getSections()) {
            section.getBiomes().getAll(holder -> holder.unwrapKey().ifPresent(key -> {
                Identifier id = key.identifier();
                if (eligible.contains(id)) {
                    found.putIfAbsent(id, holder);
                }
            }));
        }
        if (found.isEmpty()) {
            return List.of();
        }
        // Re-resolve through the registry so downstream feature lookups use canonical holders.
        List<Holder<Biome>> out = new ArrayList<>();
        for (Identifier id : found.keySet()) {
            registry.get(id).ifPresent(out::add);
        }
        return out;
    }

    private static int replayVegetalFeatures(ServerLevel world, ChunkPos pos, List<Holder<Biome>> biomes) {
        int step = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
        BlockPos origin = new BlockPos(pos.getMinBlockX(), world.getMinY(), pos.getMinBlockZ());
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(world.getSeed()));
        long decorationSeed = random.setDecorationSeed(world.getSeed(), origin.getX(), origin.getZ());
        int placed = 0;
        for (Holder<Biome> biome : biomes) {
            List<HolderSet<PlacedFeature>> features = biome.value().getGenerationSettings().features();
            if (step >= features.size()) {
                continue;
            }
            int index = 0;
            for (Holder<PlacedFeature> feature : features.get(step)) {
                random.setFeatureSeed(decorationSeed, index++, step);
                try {
                    // placeWithBiomeCheck consults the STORED biome at each candidate position and
                    // only places where that biome actually carries this feature — the runtime
                    // equivalent of generation's BiomeFilter, and what keeps the replay from
                    // spilling into neighboring columns.
                    if (feature.value().placeWithBiomeCheck(
                            world, world.getChunkSource().getGenerator(), random, origin)) {
                        placed++;
                    }
                } catch (Exception e) {
                    GlobeMod.LOGGER.warn("[Latitude] retrofit feature failed in chunk {},{} ({}): {}",
                            pos.x, pos.z, feature.unwrapKey().map(k -> k.identifier().toString()).orElse("?"),
                            e.toString());
                }
            }
        }
        return placed;
    }

    // ── Eligibility ──

    /**
     * Every custom biome Latitude can paint: the {@link #DECORATION_POLICY_TAG_PATHS} union
     * {@link BiomeDescriptorLedger}, registry-resolved. This is the full producible set — unlike
     * {@link #eligibleBiomeIds}, which deliberately excludes tagged biomes to scope retrofit
     * replay. Shared with {@code ChunkGeneratorGenerateFeaturesBiomeSetMixin} (the decoration-index
     * protection) and {@code LatitudeBiomeSource} (the {@code /locate biome} candidate pool):
     * both need "everything Latitude could have placed here," not a generation-order-scoped
     * subset. {@code minecraft:} entries are skipped — they are always in the raw source's own
     * {@code possibleBiomes()} already; absent optional mods simply fail the registry lookup and
     * are skipped.
     */
    public static List<Holder<Biome>> allPaintableCustomBiomes(Registry<Biome> biomeRegistry) {
        Map<Identifier, Holder<Biome>> out = new LinkedHashMap<>();
        for (String tagPath : DECORATION_POLICY_TAG_PATHS) {
            TagKey<Biome> tag = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("globe", tagPath));
            for (Holder<Biome> holder : biomeRegistry.getTagOrEmpty(tag)) {
                holder.unwrapKey().ifPresent(key -> {
                    Identifier id = key.identifier();
                    if (!"minecraft".equals(id.getNamespace())) {
                        out.putIfAbsent(id, holder);
                    }
                });
            }
        }
        for (BiomeDescriptorLedger.Descriptor descriptor : BiomeDescriptorLedger.descriptors()) {
            Identifier id = Identifier.tryParse(descriptor.biomeId());
            if (id == null || "minecraft".equals(id.getNamespace()) || out.containsKey(id)) {
                continue;
            }
            biomeRegistry.get(id).ifPresent(holder -> out.putIfAbsent(id, holder));
        }
        return List.copyOf(out.values());
    }

    private static List<Identifier> eligibleBiomeIds(ServerLevel world) {
        List<Identifier> cached = eligibleCache;
        if (cached != null) {
            return cached;
        }
        Registry<Biome> registry = world.registryAccess().lookupOrThrow(Registries.BIOME);
        Set<Identifier> tagged = new HashSet<>();
        for (String tagPath : DECORATION_POLICY_TAG_PATHS) {
            TagKey<Biome> tag = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("globe", tagPath));
            for (Holder<Biome> holder : registry.getTagOrEmpty(tag)) {
                holder.unwrapKey().ifPresent(key -> tagged.add(key.identifier()));
            }
        }
        List<Identifier> out = new ArrayList<>();
        for (BiomeDescriptorLedger.Descriptor descriptor : BiomeDescriptorLedger.descriptors()) {
            Identifier id = Identifier.tryParse(descriptor.biomeId());
            if (id == null || "minecraft".equals(id.getNamespace()) || tagged.contains(id)) {
                continue;
            }
            if (registry.get(id).isPresent()) {
                out.add(id);
            }
        }
        cached = List.copyOf(out);
        eligibleCache = cached;
        return cached;
    }

    private static String eligibleSummary(ServerLevel world) {
        List<Identifier> ids = eligibleBiomeIds(world);
        if (ids.isEmpty()) {
            return "none present (no eligible provider mods installed)";
        }
        return ids.size() + " (" + ids.stream().map(Identifier::toString)
                .limit(6).reduce((a, b) -> a + ", " + b).orElse("") + (ids.size() > 6 ? ", …" : "") + ")";
    }
}
