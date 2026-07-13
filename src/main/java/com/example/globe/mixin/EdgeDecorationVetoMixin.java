package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.MirrorGeometry;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 5 Slice B-6 (Teleport-Evator) P1-PREP: the SYMMETRIC decoration strip. The mirror-band terrain matches
 * across the seam, but per-chunk feature/decoration placement seeds off the REAL chunk origin and cannot be
 * cleanly reflected (a tree born on a west hill would land in an east valley) -- so both mechanisms (leaf-
 * reflect or copy-and-flip) need the band left BARREN of surface features. Design amendment 3/4 makes this
 * SYMMETRIC (both the east {@code +x} and west {@code -x} bands): a barren east mirroring a forested west would
 * be visibly broken. A wind-scoured, fog-drowned edge reads naturally and pairs with the shipped structure veto.
 *
 * <p><b>The seam.</b> Same {@code applyBiomeDecoration} chokepoint the shipping
 * {@link ChunkGeneratorGenerateFeaturesBiomeSetMixin} already binds (bytecode-verified: that mixin loads under
 * {@code required:true}, so the descriptor is live). Whole-chunk veto at HEAD, cancellable -- a chunk is 16
 * blocks and the band is &ge;104 blocks even on Itty-Bitty, so per-chunk granularity is ample. A separate
 * mixin (not folded into the retainAll indexer) keeps the concern clean; the indexer's own HEAD inject is
 * idempotent and harmless if this cancels first.
 *
 * <p><b>Gated on the PER-WORLD captured evator ({@link LatitudeBiomes#isEvatorActive()}), NOT the raw global
 * flag</b> -- a non-evator world is byte-identical even with {@code latitude.evatorV2.enabled} on. Anchored to
 * the intended X radius (immune to a lerping border, same thesis as {@link ChunkGeneratorGenerateFeaturesBiomeSetMixin}'s
 * sibling {@code EdgeStructureVetoMixin}) and bounded to the small globe border so a vanilla ~30M border never
 * trips. Fails OPEN on any uncertainty (allow decoration). Flag-off / capture-off is byte-identical: the first
 * check returns immediately.
 */
@Mixin(ChunkGenerator.class)
public class EdgeDecorationVetoMixin {

    @Inject(
            method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void globe$stripMirrorBandDecoration(WorldGenLevel world, ChunkAccess chunk,
                                                 StructureManager structureManager, CallbackInfo ci) {
        // Per-world capture off (or global flag off, which can never capture on): byte-identical.
        if (!LatitudeV2Flags.EVATOR_V2_ENABLED || !LatitudeBiomes.isEvatorActive()) {
            return;
        }
        try {
            WorldBorder border = world.getWorldBorder();
            double xRadius = com.example.globe.util.LatitudeMath.intendedXRadius(border);
            // Globe worlds only (small border). A vanilla ~30M border maps everything near center; never trip.
            if (!(xRadius > 0.0 && xRadius < 1_000_000.0)) {
                return;
            }
            int centerBlockX = chunk.getPos().getMiddleBlockX();
            if (MirrorGeometry.inBand(centerBlockX, border.getCenterX(), xRadius)) {
                ci.cancel(); // barren, fog-masked mirror band -- no features either side of the seam.
            }
        } catch (Throwable ignored) {
            // Border/registry unavailable -- fail open (allow decoration).
        }
    }
}
