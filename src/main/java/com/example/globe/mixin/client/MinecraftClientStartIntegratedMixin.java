package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Mixin(Minecraft.class)
public abstract class MinecraftClientStartIntegratedMixin {

    @Inject(method = "startIntegratedServer", at = @At("HEAD"))
    private void globe$markLatitudeLoad(LevelStorageSource.LevelStorageAccess session,
                                        PackRepository dataPackManager,
                                        WorldStem saveLoader,
                                        boolean newWorld,
                                        CallbackInfo ci) {
        if (LatitudeClientState.isLatitudeWorldLoading()) {
            return;
        }
        if (globe$isLatitudeWorld(session)) {
            LatitudeClientState.activateLatitudeLoading();
            LatitudeClientState.firstWorldLoad = false;
        }
    }

    private static boolean globe$isLatitudeWorld(LevelStorageSource.LevelStorageAccess session) {
        try {
            Path levelDat = session.getLevelPath(LevelResource.ROOT).resolve("level.dat");
            if (!Files.exists(levelDat)) {
                return false;
            }
            CompoundTag root = NbtIo.readCompressed(levelDat, NbtAccounter.unlimitedHeap());
            Optional<CompoundTag> dataOpt = root.getCompound("Data");
            if (dataOpt.isEmpty()) return false;
            Optional<CompoundTag> wgsOpt = dataOpt.get().getCompound("WorldGenSettings");
            if (wgsOpt.isEmpty()) return false;
            Optional<CompoundTag> dimsOpt = wgsOpt.get().getCompound("dimensions");
            if (dimsOpt.isEmpty()) return false;
            Optional<CompoundTag> overworldOpt = dimsOpt.get().getCompound("minecraft:overworld");
            if (overworldOpt.isEmpty()) return false;
            Optional<CompoundTag> generatorOpt = overworldOpt.get().getCompound("generator");
            if (generatorOpt.isEmpty()) return false;
            CompoundTag generator = generatorOpt.get();

            Optional<String> settingsId = generator.getString("settings");
            if (settingsId.isPresent() && settingsId.get().startsWith("globe:")) {
                return true;
            }
            Optional<CompoundTag> settingsTag = generator.getCompound("settings");
            if (settingsTag.isPresent()) {
                Optional<String> preset = settingsTag.get().getString("preset");
                if (preset.isPresent() && preset.get().startsWith("globe:")) {
                    return true;
                }
            }
            Optional<String> typeId = generator.getString("type");
            return typeId.isPresent() && typeId.get().startsWith("globe:");
        } catch (Exception ignored) {
            return false;
        }
    }
}
