package com.example.globe.mixin.client;

import net.minecraft.client.render.BackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BackgroundRenderer.class, priority = 2000)
public class FogRendererMixin {
}
