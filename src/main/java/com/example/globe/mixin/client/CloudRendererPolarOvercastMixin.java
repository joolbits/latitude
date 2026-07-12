package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.example.globe.core.PolarCloudDeck;
import com.example.globe.core.PolarHazardWindow;
import com.example.globe.util.LatitudeMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * TEST 79 item 2: OVERCAST polar cloud deck. Peetsa's pole screenshot shows vanilla's scattered bright-white
 * cloud puffs with clear sky between/beyond them, which reads as fair weather over his blizzard. This darkens
 * the whole deck toward a heavy storm grey (and toward opaque) as the pole approaches, so the clouds read as a
 * continuous stormy overcast instead of bright clumps.
 *
 * <p><b>Injection point (bytecode-verified against the mapped 26.2 client jar).</b> In 26.2 the cloud tint is a
 * single ARGB int computed from {@code EnvironmentAttributes.CLOUD_COLOR_VISUAL} and handed to
 * {@code CloudRenderer.render(int color, CloudStatus, float, int, Vec3, long, float)} as its first (index-1)
 * argument; {@code render}'s bytecode feeds exactly that arg into {@code ARGB.vector4fFromARGB32} -> the cloud
 * UBO, tinting the entire deck. So a HEAD {@link ModifyVariable} on that arg recolours the whole deck with no
 * geometry or pipeline changes. The descriptor is pinned in the {@code method} selector; {@code require} stays
 * at the default 1 (fail loud at load if {@code render} is ever renamed -- the discipline that would have caught
 * this project's earlier silently-dead fog mixin), never {@code require = 0}.
 *
 * <p><b>Scope / seam.</b> Globe worlds only, client player's own {@link ClientLevel} only, and only once the
 * ambient [85,90] envelope is open ({@link PolarHazardWindow#ambientProgress} &gt; 0) -- below 85 deg, off-globe,
 * or in another dimension the arg is returned verbatim (byte-identical vanilla render path). Cloud video setting
 * OFF never calls {@code render}, so off stays off; FAST and FANCY both flow through this single colour arg.
 * Honours the {@code latitude.debugDisableWarnings} kill switch alongside the other polar-atmosphere layers.
 */
@Mixin(CloudRenderer.class)
public class CloudRendererPolarOvercastMixin {

    @ModifyVariable(
            method = "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private int globe$polarOvercastCloudColor(int color) {
        if (GlobeClientState.DEBUG_DISABLE_WARNINGS) {
            return color;
        }
        if (!GlobeClientState.isGlobeWorld()) {
            return color;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || !(mc.level instanceof ClientLevel level)) {
            return color;
        }
        double absLatDeg = LatitudeMath.absLatDegExact(level.getWorldBorder(), mc.player.getZ());
        double ambient = PolarHazardWindow.ambientProgress(absLatDeg);
        if (ambient <= 0.0) {
            return color; // below ~85 deg -- vanilla cloud tint untouched (seam-free)
        }
        return PolarCloudDeck.stormCloudColor(color, ambient);
    }
}
