package com.example.globe.mixin;

import net.minecraft.world.biome.source.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BiomeSource.class)
public interface BiomeSourceAccessor {
    // In 1.20.1, the method is called "getCodec" and returns Codec, not MapCodec.
    @Invoker("getCodec")
    com.mojang.serialization.Codec<? extends BiomeSource> globe$invokeGetCodec();
}
