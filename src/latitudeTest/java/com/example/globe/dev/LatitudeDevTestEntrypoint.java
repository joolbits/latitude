package com.example.globe.dev;

import com.example.globe.GlobeMod;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Packaged-TEST common initializer. This class has no client references.
 */
public final class LatitudeDevTestEntrypoint implements ModInitializer {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    @Override
    public void onInitialize() {
        if (LatitudeDevRuntime.isDevelopmentEnvironment()) {
            return;
        }
        if (!LatitudeDevRuntime.isPackagedTestArtifact()) {
            GlobeMod.LOGGER.error("[latdev] TEST common entrypoint rejected invalid artifact identity");
            return;
        }
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        LatitudeDevCommand.register(dispatcher));
        BiomePreviewHeadlessRunner.register();
        GlobeMod.LOGGER.info("[latdev] packaged TEST common tooling initialized sequence={}",
                LatitudeDevRuntime.identity().sequence());
    }
}
