package com.example.globe.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.world.biome.source.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BiomeSource.class)
public interface BiomeSourceAccessor {
    @Invoker("getCodec")
    Codec<? extends BiomeSource> globe$invokeGetCodec();
}
