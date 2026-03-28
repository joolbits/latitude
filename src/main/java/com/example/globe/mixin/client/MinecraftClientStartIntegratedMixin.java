package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.SaveLoader;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientStartIntegratedMixin {

    @Inject(method = "startIntegratedServer", at = @At("HEAD"))
    private void globe$markLatitudeLoad(LevelStorage.Session session,
                                        ResourcePackManager dataPackManager,
                                        SaveLoader saveLoader,
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

    private static boolean globe$isLatitudeWorld(LevelStorage.Session session) {
        try {
            Path levelDat = session.getDirectory(WorldSavePath.ROOT).resolve("level.dat");
            if (!Files.exists(levelDat)) {
                return false;
            }
            NbtCompound root = NbtIo.readCompressed(levelDat, NbtSizeTracker.ofUnlimitedBytes());
            Optional<NbtCompound> dataOpt = root.getCompound("Data");
            if (dataOpt.isEmpty()) return false;
            Optional<NbtCompound> wgsOpt = dataOpt.get().getCompound("WorldGenSettings");
            if (wgsOpt.isEmpty()) return false;
            Optional<NbtCompound> dimsOpt = wgsOpt.get().getCompound("dimensions");
            if (dimsOpt.isEmpty()) return false;
            Optional<NbtCompound> overworldOpt = dimsOpt.get().getCompound("minecraft:overworld");
            if (overworldOpt.isEmpty()) return false;
            Optional<NbtCompound> generatorOpt = overworldOpt.get().getCompound("generator");
            if (generatorOpt.isEmpty()) return false;
            NbtCompound generator = generatorOpt.get();

            Optional<String> settingsId = generator.getString("settings");
            if (settingsId.isPresent() && settingsId.get().startsWith("globe:")) {
                return true;
            }
            Optional<NbtCompound> settingsTag = generator.getCompound("settings");
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
