package com.example.globe.mixin.client.compat.sodium;

import com.example.globe.client.GlobeClientState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public class RenderSectionManagerVisibilityMixin {

    @Unique private static long latitude$lastLogMs = 0L;

    @Inject(method = "isSectionVisible(III)Z", at = @At("RETURN"), cancellable = true)
    private void globe$ewCullSections(int sectionX, int sectionY, int sectionZ, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (!FabricLoader.getInstance().isModLoaded("sodium")) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;

        double sectionCenterX = (sectionX << 4) + 8.0;
        double sectionCenterZ = (sectionZ << 4) + 8.0;

        int baseChunks;
        try {
            baseChunks = mc.options.getViewDistance().getValue();
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

        long now = System.currentTimeMillis();
        if (now - latitude$lastLogMs >= 1000L) {
            latitude$lastLogMs = now;
            double intensity = GlobeClientState.ewIntensity01(px);
            System.out.println("[Latitude] EW cull base=" + baseChunks + " capped=" + cappedChunks
                    + " blocks=" + cappedBlocks + " i=" + intensity + " px=" + px + " pz=" + pz);
        }
    }
}
