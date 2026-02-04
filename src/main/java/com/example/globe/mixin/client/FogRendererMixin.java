package com.example.globe.mixin.client;

import com.example.globe.GlobeMod;
import com.example.globe.client.GlobeClientState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = FogRenderer.class, priority = 2000)
public class FogRendererMixin {
    private static final boolean DEBUG_EW_FOG = Boolean.getBoolean("latitude.debugEwFog");
    private static long lastScreamMs = 0L;
    private static long lastModifyMs = 0L;
    private static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");

    private static final ThreadLocal<Boolean> TL_GLOBE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Float> TL_EW_END = ThreadLocal.withInitial(() -> 0.0f);
    private static final ThreadLocal<String> TL_FOG_TYPE = ThreadLocal.withInitial(() -> "n/a");

    private static boolean shouldLog1Hz(long now, boolean scream) {
        if (scream) {
            if (now - lastScreamMs < 1000L) return false;
            lastScreamMs = now;
            return true;
        }

        if (now - lastModifyMs < 1000L) return false;
        lastModifyMs = now;
        return true;
    }

    @Inject(method = "applyFog", at = @At("HEAD"))
    private void latitude$scream(Camera camera, int viewDistance, RenderTickCounter tickCounter, float tickDelta,
                                 ClientWorld world, CallbackInfoReturnable<Vector4f> cir) {
        if (false) {
            boolean globe = GlobeClientState.isGlobeWorld();
            double camX = camera.getCameraPos().x;
            float ewEnd = globe ? GlobeClientState.computeEwFogEnd(camX) : 0.0f;

            TL_GLOBE.set(globe);
            TL_EW_END.set(ewEnd);
            TL_FOG_TYPE.set("n/a");

            if (!DEBUG_EW_FOG) {
                return;
            }

            long now = System.currentTimeMillis();
            if (!shouldLog1Hz(now, true)) {
                return;
            }

            GlobeMod.LOGGER.info("[LAT_EW_FOG_SCREAM] hook=FogRenderer#applyFog HEAD camX={} globe={} ewEnd={} fogType={} sodium={}",
                    camX, globe, ewEnd, TL_FOG_TYPE.get(), SODIUM_LOADED);
        }
    }

    @ModifyArgs(
            method = "applyFog",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/fog/FogRenderer;method_71110(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
            ),
            require = 0
    )
    private void latitude$modifyFogArgs(Args args) {
        if (false) {
            if (!Boolean.getBoolean("latitude.ewFogProof")) {
                return;
            }
            boolean globe = TL_GLOBE.get();
            float ewEnd = TL_EW_END.get();
            if (!globe || ewEnd <= 0.0f) {
                return;
            }

            // HARD PROOF MODE: force a wall if this hook fires.
            float proofEnd = 12.0f;

            for (int i = 3; i <= 8; i++) {
                float v = args.get(i);
                if (v > proofEnd) args.set(i, proofEnd);
            }

            long now = System.currentTimeMillis();
            if (now - lastModifyMs >= 1000L) {
                lastModifyMs = now;
                double camX = -9999.0;
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.player != null) {
                    camX = mc.player.getX();
                }
                GlobeMod.LOGGER.info("[LAT_EW_FOG_PROOF] clamp fired: camX={} ewEnd={} forcedEnd={}", camX, ewEnd, proofEnd);
            }

            float environmentalEnd = args.get(4);
            float renderEnd = args.get(6);

            float clampedEwEnd = Math.max(8.0f, ewEnd);
            float finalEnd = Math.min(Math.min(environmentalEnd, renderEnd), clampedEwEnd);
            args.set(3, 0.0f);
            args.set(4, finalEnd);
            args.set(5, 0.0f);
            args.set(6, finalEnd);

            if (!DEBUG_EW_FOG) {
                return;
            }

            long logNow = System.currentTimeMillis();
            if (!shouldLog1Hz(logNow, false)) {
                return;
            }

            GlobeMod.LOGGER.info("[LAT_EW_FOG_MODIFY] end env={} render={} ew={} final={} fogType={} sodium={}",
                    environmentalEnd, renderEnd, ewEnd, finalEnd, TL_FOG_TYPE.get(), SODIUM_LOADED);
        }
    }
}
