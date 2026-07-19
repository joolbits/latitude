package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.dev.client.SeamAuditClientBridge;
import com.example.globe.dev.client.audit.SeamAuditHarness;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;

/**
 * Packaged-TEST client initializer. Public and common artifacts never load it.
 */
public final class LatitudeDevTestClientEntrypoint implements ClientModInitializer {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    @Override
    public void onInitializeClient() {
        if (LatitudeDevRuntime.isDevelopmentEnvironment()) {
            return;
        }
        if (!LatitudeDevRuntime.isPackagedTestArtifact()) {
            GlobeMod.LOGGER.error("[latdev] TEST client entrypoint rejected invalid artifact identity");
            return;
        }
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        DevCaptureKeybind.init();
        SeamAuditClientBridge.init();
        SeamAuditHarness.init();
        AutoCreateWorldProbe.maybeRegister();
        GlobeMod.LOGGER.info("[latdev] packaged TEST client tooling initialized sequence={}",
                LatitudeDevRuntime.identity().sequence());
    }
}
