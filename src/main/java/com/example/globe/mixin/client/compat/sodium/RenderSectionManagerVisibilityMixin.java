package com.example.globe.mixin.client.compat.sodium;

import com.example.globe.client.GlobeClientState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public class RenderSectionManagerVisibilityMixin {

    // NOTE (Slice B, audit P1-2): a dead `latitude$lastLogMs` throttle field lived here -- logging was
    // intended but never wired, and a mixin body cannot log its own NON-application anyway (require = 0
    // skips at apply time). The user-visible "this optimization is inactive on your Sodium version" warn
    // therefore lives in GlobeModClient.warnIfSodiumCullHookInactive(), a client-init reflection check.

    // require = 0: this hook is a render-distance/performance nicety (E-W edge section culling), not a
    // correctness-critical feature. globe.mixins.json sets "defaultRequire": 1, so without this override a
    // Sodium version whose internal RenderSectionManager no longer has isSectionVisible(III)Z (confirmed:
    // Sodium 0.9.0+mc26.2 removed/renamed it as part of an internal culling refactor -- no stable drop-in
    // replacement signature exists) makes mixin application fail CRITICALLY, crashing the whole client on
    // world load. require = 0 makes this specific injection tolerate a missing target: on a Sodium version
    // where it doesn't apply, this optimization is silently skipped (culling reverts to Sodium's own,
    // unmodified default) instead of taking the whole client down. See the 2026-07-05 Sodium crash report.
    @Inject(method = "isSectionVisible(III)Z", at = @At("RETURN"), cancellable = true, require = 0)
    private void globe$ewCullSections(int sectionX, int sectionY, int sectionZ, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (!FabricLoader.getInstance().isModLoaded("sodium")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;

        double sectionCenterX = (sectionX << 4) + 8.0;
        double sectionCenterZ = (sectionZ << 4) + 8.0;

        int baseChunks;
        try {
            baseChunks = mc.options.renderDistance().get();
        } catch (Throwable t) {
            return;
        }

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        int cappedChunks = GlobeClientState.ewRenderDistanceChunks(baseChunks, px);
        int cappedBlocks = Math.max(0, cappedChunks * 16);

        double dx = sectionCenterX - px;
        double dz = sectionCenterZ - pz;
        double distSq = (dx * dx) + (dz * dz);
        double capSq = (double) cappedBlocks * (double) cappedBlocks;

        if (distSq > capSq) {
            cir.setReturnValue(false);
        }
    }
}
