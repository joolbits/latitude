package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LatitudeConfig {
    public static boolean enableWarningParticles = true;
    public static boolean showWarningMessages = true;

    public enum ZoneEntryNotifyMode { OFF, TOAST, TITLE }
    public static ZoneEntryNotifyMode zoneEntryNotifyMode = ZoneEntryNotifyMode.TITLE;
    public static boolean showLatitudeDegrees = true;
    public static boolean latitudeDegreesOnCompass = true;

    public static boolean zoneEnterTitleEnabled = true;
    public static double zoneEnterTitleSeconds = 6.0;
    public static double zoneEnterTitleScale = 1.8;

    public static int zoneEnterTitleOffsetX = 0;
    public static int zoneEnterTitleOffsetY = -40;
    public static boolean zoneEnterTitleDraggable = true;

    public static boolean hudSnapEnabled = true;
    public static int hudSnapPixels = 8;

    public static boolean showLatitudeDegreesOnCompass = false;
    public static boolean showZoneBaseDegreesOnTitle = true;

    public static boolean latitudeBandBlendingEnabled = true;
    public static double latitudeBandBlendWidthFrac = 0.08;
    public static double latitudeBandBoundaryWarpFrac = 0.06;

    public static boolean debugLatitudeBlend = false;

    private boolean enableWarningParticlesValue = true;
    private boolean showWarningMessagesValue = true;
    private ZoneEntryNotifyMode zoneEntryNotifyModeValue = ZoneEntryNotifyMode.TITLE;
    private boolean showLatitudeDegreesValue = true;
    private boolean latitudeDegreesOnCompassValue = true;

    private boolean zoneEnterTitleEnabledValue = true;
    private double zoneEnterTitleSecondsValue = 6.0;
    private double zoneEnterTitleScaleValue = 1.8;

    private int zoneEnterTitleOffsetXValue = 0;
    private int zoneEnterTitleOffsetYValue = -40;
    private boolean zoneEnterTitleDraggableValue = true;

    private boolean hudSnapEnabledValue = true;
    private int hudSnapPixelsValue = 8;

    private boolean showLatitudeDegreesOnCompassValue = false;
    private boolean showZoneBaseDegreesOnTitleValue = true;

    private boolean latitudeBandBlendingEnabledValue = true;
    private double latitudeBandBlendWidthFracValue = 0.08;
    private double latitudeBandBoundaryWarpFracValue = 0.06;

    private boolean debugLatitudeBlendValue = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("globe_latitude.json");

    private static LatitudeConfig INSTANCE;

    private LatitudeConfig() {
    }

    public static LatitudeConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static void reload() {
        INSTANCE = load();
    }

    public static void saveCurrent() {
        save(get());
    }

    private static LatitudeConfig load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader r = Files.newBufferedReader(PATH)) {
                    LatitudeConfig cfg = GSON.fromJson(r, LatitudeConfig.class);
                    if (cfg != null) {
                        cfg.sanitize();
                        enableWarningParticles = cfg.enableWarningParticlesValue;
                        showWarningMessages = cfg.showWarningMessagesValue;
                        zoneEntryNotifyMode = cfg.zoneEntryNotifyModeValue;
                        showLatitudeDegrees = cfg.showLatitudeDegreesValue;
                        latitudeDegreesOnCompass = cfg.latitudeDegreesOnCompassValue;

                        zoneEnterTitleEnabled = cfg.zoneEnterTitleEnabledValue;
                        zoneEnterTitleSeconds = cfg.zoneEnterTitleSecondsValue;
                        zoneEnterTitleScale = cfg.zoneEnterTitleScaleValue;

                        zoneEnterTitleOffsetX = cfg.zoneEnterTitleOffsetXValue;
                        zoneEnterTitleOffsetY = cfg.zoneEnterTitleOffsetYValue;
                        zoneEnterTitleDraggable = cfg.zoneEnterTitleDraggableValue;

                        hudSnapEnabled = cfg.hudSnapEnabledValue;
                        hudSnapPixels = cfg.hudSnapPixelsValue;

                        showLatitudeDegreesOnCompass = cfg.showLatitudeDegreesOnCompassValue;
                        showZoneBaseDegreesOnTitle = cfg.showZoneBaseDegreesOnTitleValue;

                        latitudeBandBlendingEnabled = cfg.latitudeBandBlendingEnabledValue;
                        latitudeBandBlendWidthFrac = cfg.latitudeBandBlendWidthFracValue;
                        latitudeBandBoundaryWarpFrac = cfg.latitudeBandBoundaryWarpFracValue;

                        debugLatitudeBlend = cfg.debugLatitudeBlendValue;
                        return cfg;
                    }
                }
            }
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("Failed to read latitude config; using defaults", e);
        }

        LatitudeConfig fresh = new LatitudeConfig();
        enableWarningParticles = fresh.enableWarningParticlesValue;
        showWarningMessages = fresh.showWarningMessagesValue;
        zoneEntryNotifyMode = fresh.zoneEntryNotifyModeValue;
        showLatitudeDegrees = fresh.showLatitudeDegreesValue;
        latitudeDegreesOnCompass = fresh.latitudeDegreesOnCompassValue;

        zoneEnterTitleEnabled = fresh.zoneEnterTitleEnabledValue;
        zoneEnterTitleSeconds = fresh.zoneEnterTitleSecondsValue;
        zoneEnterTitleScale = fresh.zoneEnterTitleScaleValue;

        zoneEnterTitleOffsetX = fresh.zoneEnterTitleOffsetXValue;
        zoneEnterTitleOffsetY = fresh.zoneEnterTitleOffsetYValue;
        zoneEnterTitleDraggable = fresh.zoneEnterTitleDraggableValue;

        hudSnapEnabled = fresh.hudSnapEnabledValue;
        hudSnapPixels = fresh.hudSnapPixelsValue;

        showLatitudeDegreesOnCompass = fresh.showLatitudeDegreesOnCompassValue;
        showZoneBaseDegreesOnTitle = fresh.showZoneBaseDegreesOnTitleValue;

        latitudeBandBlendingEnabled = fresh.latitudeBandBlendingEnabledValue;
        latitudeBandBlendWidthFrac = fresh.latitudeBandBlendWidthFracValue;
        latitudeBandBoundaryWarpFrac = fresh.latitudeBandBoundaryWarpFracValue;

        debugLatitudeBlend = fresh.debugLatitudeBlendValue;
        save(fresh);
        return fresh;
    }

    private static void save(LatitudeConfig cfg) {
        try {
            cfg.enableWarningParticlesValue = enableWarningParticles;
            cfg.showWarningMessagesValue = showWarningMessages;
            cfg.zoneEntryNotifyModeValue = zoneEntryNotifyMode;
            cfg.showLatitudeDegreesValue = showLatitudeDegrees;
            cfg.latitudeDegreesOnCompassValue = showLatitudeDegrees && latitudeDegreesOnCompass;

            cfg.zoneEnterTitleEnabledValue = zoneEnterTitleEnabled;
            cfg.zoneEnterTitleSecondsValue = zoneEnterTitleSeconds;
            cfg.zoneEnterTitleScaleValue = zoneEnterTitleScale;

            cfg.zoneEnterTitleOffsetXValue = zoneEnterTitleOffsetX;
            cfg.zoneEnterTitleOffsetYValue = zoneEnterTitleOffsetY;
            cfg.zoneEnterTitleDraggableValue = zoneEnterTitleDraggable;

            cfg.hudSnapEnabledValue = hudSnapEnabled;
            cfg.hudSnapPixelsValue = hudSnapPixels;

            cfg.showLatitudeDegreesOnCompassValue = showLatitudeDegreesOnCompass;
            cfg.showZoneBaseDegreesOnTitleValue = showZoneBaseDegreesOnTitle;

            cfg.latitudeBandBlendingEnabledValue = latitudeBandBlendingEnabled;
            cfg.latitudeBandBlendWidthFracValue = latitudeBandBlendWidthFrac;
            cfg.latitudeBandBoundaryWarpFracValue = latitudeBandBoundaryWarpFrac;

            cfg.debugLatitudeBlendValue = debugLatitudeBlend;
            Files.createDirectories(PATH.getParent());
            try (Writer w = Files.newBufferedWriter(PATH)) {
                GSON.toJson(cfg, w);
            }
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("Failed to write latitude config", e);
        }
    }

    private void sanitize() {
        if (zoneEntryNotifyModeValue == null) zoneEntryNotifyModeValue = ZoneEntryNotifyMode.TITLE;
        if (!showLatitudeDegreesValue) latitudeDegreesOnCompassValue = false;

        zoneEnterTitleSecondsValue = clamp(zoneEnterTitleSecondsValue, 2.0, 10.0);
        zoneEnterTitleScaleValue = clamp(zoneEnterTitleScaleValue, 1.0, 3.0);

        hudSnapPixelsValue = clampInt(hudSnapPixelsValue, 1, 64);

        latitudeBandBlendWidthFracValue = clamp(latitudeBandBlendWidthFracValue, 0.0, 1.0);
        latitudeBandBoundaryWarpFracValue = clamp(latitudeBandBoundaryWarpFracValue, 0.0, 1.0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
