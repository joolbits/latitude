package com.example.globe.mixin.client;

import net.minecraft.SystemReport;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SystemReport.class)
public class SystemDetailsHardwareProbeMixin {
    private static final boolean LATITUDE_SKIP_SYSTEM_DETAILS_HARDWARE = Boolean.parseBoolean(
            System.getProperty("latitude.skipSystemDetailsHardware", "true"));
}
