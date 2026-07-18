package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.example.globe.util.LatitudeMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * S17(a) ENTITY FOG CULLING (owner, TEST 107 video: entities kept rendering PAST the polar fog wall, reading as
 * figures floating in the white void over terrain that had already faded into fog / gone unloaded). Nothing may
 * be visible beyond the wall of white.
 *
 * <h2>The seam (26.2 javap receipts)</h2>
 * {@link EntityRenderDispatcher#shouldRender(Entity, Frustum, double, double, double)} --
 * {@code public <E extends Entity> boolean shouldRender(E, Frustum, double camX, double camY, double camZ)} --
 * is the SINGLE gate {@code LevelRenderer} calls, once per entity, to decide whether an entity is submitted for
 * rendering at all (it runs the per-renderer {@code EntityRenderer.shouldRender} frustum/bounds logic inside).
 * Intercepting HERE (one hook, at {@code RETURN}, override true->false only) covers every entity type at once and
 * NEVER forces an entity to render that vanilla culled -- it can only ADD a cull, so all existing shouldRender
 * logic outside the fog band is preserved bit-for-bit. The pure distance decision lives in
 * {@link com.example.globe.core.PolarFogLaw#cullEntityBeyondFog} (unit-tested in a plain JVM); this hook is thin.
 *
 * <h2>Gating</h2>
 * Un-gated by any dedicated feature flag, exactly like the depth fog it mirrors (S11f precedent): it fires only
 * for globe worlds and honours the same debug fog kill switches ({@code DEBUG_DISABLE_FOG} /
 * {@code DEBUG_DISABLE_WARNINGS}). Below the 80-deg fog onset {@code PolarFogLaw.fogEndCapBlocks} is
 * {@code Float.MAX_VALUE}, so the law is a provable no-op outside polar country, and even at the 80-deg entry the
 * cap (512 blocks) sits beyond every render distance -- the cull only ever bites once the fog has closed in.
 *
 * <p><b>The camera entity is never culled</b> ({@code entity == mc.getCameraEntity()}): the player's own body in
 * third person, and whatever a spectator is possessing, must never blink out (they are AT distance 0 anyway, but
 * the explicit guard is defensive and documents intent -- "never affects the player themself / first person").
 * SPECTATORS are otherwise treated like any other viewer: a free-flying spectator sees the same wall-of-white fog
 * as a survival player, so culling entities beyond it is consistent for them too (simplicity over a spectator
 * special-case -- they are looking at the same storm).
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherPolarCullMixin {

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void globe$polarEntityCull(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return; // vanilla already culled it (frustum / renderer) -- leave that decision alone.
        }
        if (GlobeClientState.DEBUG_DISABLE_FOG || GlobeClientState.DEBUG_DISABLE_WARNINGS) {
            return;
        }
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (entity == mc.getCameraEntity()) {
            return; // never cull the player's own body / a spectator's camera entity.
        }
        // Camera latitude drives the fog band (same source the depth fog uses: the player's Z on the border).
        double absLatDeg = LatitudeMath.absLatDegExact(mc.level.getWorldBorder(), mc.player.getZ());
        double dx = entity.getX() - camX;
        double dz = entity.getZ() - camZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (com.example.globe.core.PolarFogLaw.cullEntityBeyondFog(absLatDeg, horizontalDist)) {
            cir.setReturnValue(false); // beyond the wall of white: do not render.
        }
    }
}
