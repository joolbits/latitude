package com.example.globe.mixin.client;

import com.example.globe.client.EwStormWallRenderer;
import com.example.globe.client.GlobeClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class EwStormWallRendererMixin {

    private static boolean latitude$logged = false;
    private static long latitude$lastLogTick = Long.MIN_VALUE;

    @Inject(method = "pushEntityRenders", at = @At("HEAD"))
    private void latitude$injectRenderWall(MatrixStack matrices, WorldRenderState renderStates, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        if (!latitude$logged) {
            latitude$logged = true;
            System.out.println("[Latitude] EW wall hook alive");
        }

        if (queue == null || matrices == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return;
        }

        Vec3d camPos = client.gameRenderer.getCamera().getCameraPos();
        double westX = GlobeClientState.ewWestX();
        double eastX = GlobeClientState.ewEastX();
        double dist = GlobeClientState.ewDistToBorder(camPos.x);

        if (Double.isInfinite(dist)) {
            return;
        }

        queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, vc) -> {
            var world = client.world;
            if (world != null) {
                long t = world.getTime();
                if (t - latitude$lastLogTick >= 60L) {
                    latitude$lastLogTick = t;
                    System.out.println("[Latitude EW WALL] camX=" + camPos.x + " dist=" + dist + " west=" + westX + " east=" + eastX);
                }
            }

            EwStormWallRenderer.renderWall(entry, vc, camPos, westX, eastX, dist);
        });
    }
}
