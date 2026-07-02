package com.example.globe.mixin.client;

import com.example.globe.client.EwStormWallRenderer;
import com.example.globe.client.GlobeClientState;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// DEFERRED (26.1): not registered in globe.mixins.json. Target method
// LevelRenderer.pushEntityRenders no longer exists in 26.1 (render pipeline
// refactored to extract/render split). Render call body is also disabled.
// Re-enabling requires (a) finding a 26.1 injection point for custom-geometry
// submission and (b) re-adding "client.EwStormWallRendererMixin" to the config.
@Mixin(LevelRenderer.class)
public abstract class EwStormWallRendererMixin {

    private static boolean latitude$logged = false;
    private static long latitude$lastLogTick = Long.MIN_VALUE;

    @Inject(method = "pushEntityRenders", at = @At("HEAD"))
    private void latitude$injectRenderWall(PoseStack matrices, LevelRenderState renderStates, SubmitNodeCollector queue, CallbackInfo ci) {
        if (queue == null || matrices == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameRenderer == null) {
            return;
        }

        Vec3 camPos = client.gameRenderer.mainCamera().position();
        double westX = GlobeClientState.ewWestX();
        double eastX = GlobeClientState.ewEastX();
        double dist = GlobeClientState.ewDistToBorder(camPos.x);

        if (Double.isInfinite(dist)) {
            return;
        }

        queue.submitCustomGeometry(matrices, RenderTypes.debugQuads(), (entry, vc) -> {
            var world = client.level;
            // 26.2: GlStateManager._enableBlend/_disableBlend now take an int context/binding index; 0 = default
            // context, matching the prior zero-arg behavior. (This mixin is deferred/unregistered dead code.)
            GlStateManager._enableBlend(0);
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager._enableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._polygonOffset(-1.0f, -10.0f);

            try {
                // EwStormWallRenderer.renderWall(entry, vc, camPos.x, camPos.z, westX, eastX, dist); // TEMP: wall disabled
            } finally {
                GlStateManager._disablePolygonOffset();
                GlStateManager._depthMask(true);
                GlStateManager._disableBlend(0);
                GlStateManager._enableCull();
                GlStateManager._enableDepthTest();
            }
        });
    }
}
