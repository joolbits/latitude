package com.example.globe.mixin;

import com.example.globe.core.EdgeStructureVeto;
import com.example.globe.core.LatitudeV2Flags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 5 Slice B-5 (Hemisphere Passage polish, item 1): keep GENERATED STRUCTURES out of the E/W-edge band
 * (Peetsa saw one at the border, TEST 83). Same PROVEN chokepoint as {@link ExtremePolarVillageGuardMixin} and
 * {@link StructureBiomeMatchGuardMixin} -- {@code StructureStart.placeInChunk} at HEAD, {@code cancellable}:
 * vetoing here runs BEFORE any block of the structure is written, so there are never half-built structures.
 *
 * <p><b>Keyed on the structure's ANCHOR chunk ({@code getChunkPos}), not the per-call {@code chunkPos}.</b>
 * A structure can span several chunks; {@code placeInChunk} is called once per overlapping chunk with the same
 * {@link StructureStart}. Judging the anchor makes the decision uniform across all of them (all-or-nothing per
 * structure) and fully deterministic -- same seed + flag -> same vetoed anchors.
 *
 * <p><b>SURFACE structures only.</b> The band test is a purely horizontal (X) check, so an underground
 * structure whose anchor sits in the band would also match; we deliberately skip those ({@code step() !=
 * SURFACE_STRUCTURES}). Two reasons: mineshafts et al. are invisible from the surface (Peetsa's complaint was
 * VISUAL), and STRONGHOLDS carry the End portal -- vetoing one could strand End access. Underground structures
 * at the edge are harmless and left alone; the visible frontier is what gets cleared (and that is all B-6's
 * mirror-band seam needs).
 *
 * <p>Gated on {@link LatitudeV2Flags#EDGE_STRUCTURE_VETO_ENABLED} (its OWN worldgen flag -- default TRUE,
 * byte-identical when explicitly off: the first check returns immediately) and on the small globe world border
 * (a vanilla ~30M border must never trip this). Fails OPEN on any uncertainty (allow placement), matching the
 * sibling guards. No {@code require = 0}: the target descriptor is the one two shipping mixins already bind, so
 * a rename fails loudly at load rather than silently dying.
 */
@Mixin(StructureStart.class)
public abstract class EdgeStructureVetoMixin {

    @Shadow
    public abstract Structure getStructure();

    @Shadow
    public abstract ChunkPos getChunkPos();

    @Inject(method = "placeInChunk(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At("HEAD"), cancellable = true)
    private void globe$vetoEdgeStructures(WorldGenLevel world,
                                          StructureManager structureAccessor,
                                          ChunkGenerator chunkGenerator,
                                          RandomSource random,
                                          BoundingBox chunkBox,
                                          ChunkPos chunkPos,
                                          CallbackInfo ci) {
        if (!LatitudeV2Flags.EDGE_STRUCTURE_VETO_ENABLED) {
            return; // worldgen flag off: byte-identical (structure placement untouched).
        }
        try {
            // Surface structures only: leave underground mineshafts (invisible) and strongholds (End access) be.
            if (this.getStructure().step() != GenerationStep.Decoration.SURFACE_STRUCTURES) {
                return;
            }
            var border = world.getWorldBorder();
            double xRadius = com.example.globe.util.LatitudeMath.intendedXRadius(border);
            // (sweeper 2026-07-12: intended radius, not the live border half -- placement determinism
            // must be immune to a transiently lerping border, same thesis as the feature-line anchoring.)
            // Globe worlds only (small border). A vanilla ~30M border maps everything near center; never trip.
            if (!(xRadius > 0.0 && xRadius < 1_000_000.0)) {
                return;
            }
            ChunkPos anchor = this.getChunkPos();
            // TEST 89: the band is degree-anchored now (173-deg fan-out anchor, floored), NOT a fixed 500 --
            // so on wide worlds it covers the full visible storm band (177.5 deg) plus a village's fan-out.
            if (EdgeStructureVeto.inEdgeBand(anchor.getMiddleBlockX(), border.getCenterX(), xRadius,
                    EdgeStructureVeto.bandBlocks(xRadius))) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // Border/registry unavailable -- fail open (allow placement).
        }
    }
}
