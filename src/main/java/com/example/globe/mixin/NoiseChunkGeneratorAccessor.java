package com.example.globe.mixin;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseChunkGenerator.class)
public interface NoiseChunkGeneratorAccessor {
    @Accessor("settings")
    @Nullable
    RegistryEntry<ChunkGeneratorSettings> globe$getSettings();
}
