package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MixinSanityPing {
    private static boolean logged = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void latitude$sanityPing(CallbackInfo ci) {
        if (!logged) {
            logged = true;
            GlobeMod.LOGGER.info("[MIXIN_PING] MinecraftServer.tick reached");
        }
    }
}
