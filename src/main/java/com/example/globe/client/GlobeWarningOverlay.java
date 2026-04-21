package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

public final class GlobeWarningOverlay {
    private static long debugStartWorldTime = -1L;
    private static String lastZoneKey;

    private static final String POLE_WARN_1_TEXT =
            "The air is turning bitterly cold. You should consider turning back.";
    private static final String POLE_WARN_2_TEXT =
            "The cold seeps into your body. Movement is becoming difficult.";
    private static final String POLE_DANGER_TEXT =
            "DANGER! You are entering a lethal cold zone. Turn back immediately.";
    private static final String POLE_LETHAL_TEXT =
            "The cold overwhelms you.";

    private static final String EW_SAND_WARN_TEMPLATE =
            "Sandstorms to the %s. Head %s to turn back.";
    private static final String EW_SAND_DANGER_TEMPLATE =
            "Extreme danger to the %s. Head %s immediately.";

    private static final int EQUATOR_STABLE_DIST = 64;
    private static final long HEMISPHERE_TITLE_COOLDOWN_MS = 15_000L;

    private static long lastZoneUpdateWorldTime = Long.MIN_VALUE;
    private static int lastZoneUpdateX = Integer.MIN_VALUE;
    private static int lastZoneUpdateZ = Integer.MIN_VALUE;
    private static char lastStableHemisphere = '\0';
    private static double lastObservedZ = Double.NaN;
    private static long lastHemisphereTitleAtMs = Long.MIN_VALUE;

    private static boolean registered;

    private GlobeWarningOverlay() {
    }

    public static void init() {
        if (registered) {
            return;
        }
        GlobeMod.LOGGER.info("Globe overlay init OK");
        // HudRenderCallback is dead. We rely on InGameHudMixin.
        registered = true;
    }

    private static String zoneDisplayName(String canonicalKey) {
        return switch (canonicalKey) {
            case "TROPICAL" -> "Tropical";
            case "SUBTROPICAL" -> "Subtropical";
            case "TEMPERATE" -> "Temperate";
            case "SUBPOLAR" -> "Subpolar";
            case "POLAR" -> "Polar";
            default -> canonicalKey;
        };
    }

    private static String biomeName(Minecraft client) {
        if (client.level == null || client.player == null) {
            return "Unknown";
        }
        var biomeEntry = client.level.getBiome(client.player.blockPosition());
        var optKey = biomeEntry.unwrapKey();
        if (optKey.isPresent()) {
            String path = optKey.get().identifier().getPath();
            return titleCase(path);
        }
        return "Unknown";
    }

    private static String titleCase(String s) {
        String[] parts = s.split("[_/]");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                out.append(p.substring(1));
            }
        }
        return out.length() == 0 ? s : out.toString();
    }

    private static Component poleTextForStage(GlobeClientState.PolarStage stage) {
        if (stage == null) return null;
        return switch (stage) {
            case WARN_1 -> Component.literal(POLE_WARN_1_TEXT);
            case WARN_2 -> Component.literal(POLE_WARN_2_TEXT);
            case DANGER -> Component.literal(POLE_DANGER_TEXT).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            case LETHAL -> Component.literal(POLE_LETHAL_TEXT).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            default -> null;
        };
    }

    private static Component ewTextForStage(GlobeClientState.EwStormStage stage) {
        if (stage == null) return null;
        return switch (stage) {
            case LEVEL_1 -> Component.literal(EW_SAND_WARN_TEMPLATE);
            case LEVEL_2 -> Component.literal(EW_SAND_DANGER_TEMPLATE).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            default -> null;
        };
    }

    public static void render(GuiGraphics ctx, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();

        if (client == null) {
            return;
        }

        if (!LatitudeConfig.showWarningMessages) {
            return;
        }

        if (client.player == null || client.level == null) {
            return;
        }

        try {
            long worldTime = client.level.getGameTime();
            if (debugStartWorldTime < 0L || worldTime < debugStartWorldTime) {
                debugStartWorldTime = worldTime;
                lastZoneKey = null;
            }

            var eval = GlobeClientState.evaluate(client);

            int screenW = client.getWindow().getGuiScaledWidth();

            if (!eval.active()) {
                return;
            }

            if (!eval.surfaceOk()) {
                return;
            }

            int px = client.player.getBlockX();
            int pz = client.player.getBlockZ();

            boolean movedFar = lastZoneUpdateX == Integer.MIN_VALUE
                    || Math.abs(px - lastZoneUpdateX) > 16
                    || Math.abs(pz - lastZoneUpdateZ) > 16;

            if (lastZoneUpdateWorldTime == Long.MIN_VALUE || movedFar || (worldTime % 10L) == 0L) {
                lastZoneUpdateWorldTime = worldTime;
                lastZoneUpdateX = px;
                lastZoneUpdateZ = pz;

                var border = client.level.getWorldBorder();
                String canonicalZoneKey = canonicalTitleZoneKey(border, client.player.getZ());
                if (lastZoneKey == null || !lastZoneKey.equals(canonicalZoneKey)) {
                    lastZoneKey = canonicalZoneKey;
                    if (LatitudeConfig.zoneEnterTitleEnabled) {
                        String titleText = buildZoneEnterTitle(client, canonicalZoneKey);
                        int durationTicks = (int) Math.round(clamp(LatitudeConfig.zoneEnterTitleSeconds, 2.0, 10.0) * 20.0);
                        double scale = clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);
                        ZoneEnterTitleOverlay.trigger(titleText, durationTicks, scale);
                    }
                }

                maybeTriggerHemisphereTitle(client, client.player.getZ());
            }

            Component bestText = null;
            var state = GlobeClientState.computeWarningState(client.level, client.player);
            if (state.type() == GlobeClientState.WarningType.NONE) {
                return;
            }

            // Stable precedence (corners):
            // 1) polar lethal
            // 2) ew level 2
            // 3) polar stage (warn/danger)
            // 4) ew level 1
            if (state.type() == GlobeClientState.WarningType.POLAR) {
                GlobeClientState.PolarStage stage = (GlobeClientState.PolarStage) state.stage();
                bestText = poleTextForStage(stage);
            } else if (state.type() == GlobeClientState.WarningType.STORM) {
                GlobeClientState.EwStormStage stage = (GlobeClientState.EwStormStage) state.stage();
                String dir = ewDangerDirection(client.level.getWorldBorder(), client.player.getX());
                String escapeDir = oppositeDirection(dir);
                Component base = ewTextForStage(stage);
                if (base != null) {
                    bestText = Component.literal(String.format(base.getString(), dir.toLowerCase(), escapeDir.toLowerCase())).setStyle(base.getStyle());
                }
            }

            if (bestText == null) {
                return;
            }

            // Draw final warning (no scaling for now to avoid compilation issues)
            int warnY = client.getWindow().getGuiScaledHeight() - 68;
            if (warnY < 18) {
                warnY = 18;
            }
            int color = warningColorWithPulse(bestText, client, tickCounter);
            drawCenteredWarning(ctx, client.font, bestText, warnY, color);
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("GlobeWarningOverlay.render crashed", t);
        }
    }

    private static int warningColorWithPulse(Component text, Minecraft client, DeltaTracker tickCounter) {
        TextColor styleColor = text.getStyle().getColor();
        int rgb = styleColor != null ? styleColor.getValue() : 0xFFFFFF;
        long worldTime = client.level != null ? client.level.getGameTime() : 0L;
        double phase = worldTime * 0.04; // gentle ~7.8s period
        float pulse = 0.55f + 0.45f * (float) ((Math.sin(phase) + 1.0) * 0.5);
        int alpha = (int) Mth.clamp(pulse * 255.0f, 0.0f, 255.0f);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static void drawCenteredWarning(GuiGraphics ctx, Font tr, Component text, int y, int argbColor) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int w = tr.width(text);
        int x = Math.max(4, (screenW - w) / 2);
        ctx.drawString(tr, text, x, y, argbColor);
    }

    private static String ewDangerDirection(net.minecraft.world.level.border.WorldBorder border, double playerX) {
        double distWest = Math.abs(playerX - border.getMinX());
        double distEast = Math.abs(border.getMaxX() - playerX);
        return distWest <= distEast ? "West" : "East";
    }

    private static String oppositeDirection(String direction) {
        return "West".equals(direction) ? "East" : "West";
    }

    private static void maybeTriggerHemisphereTitle(Minecraft client, double playerZ) {
        char stableHemisphere = stableHemisphere(playerZ);
        boolean titleActive = ZoneEnterTitleOverlay.isActive();
        boolean updateSample = Math.abs(playerZ) >= EQUATOR_STABLE_DIST || Double.isNaN(lastObservedZ);

        if (!LatitudeConfig.zoneEnterTitleEnabled) {
            if (stableHemisphere != '\0') {
                lastStableHemisphere = stableHemisphere;
            }
            if (updateSample) {
                lastObservedZ = playerZ;
            }
            return;
        }

        if (titleActive) {
            if (stableHemisphere != '\0') {
                lastStableHemisphere = stableHemisphere;
            }
            if (updateSample) {
                lastObservedZ = playerZ;
            }
            return;
        }

        if (Double.isNaN(lastObservedZ) && updateSample) {
            lastObservedZ = playerZ;
        }

        if (stableHemisphere == '\0') {
            if (updateSample) {
                lastObservedZ = playerZ;
            }
            return;
        }

        if (lastStableHemisphere == '\0') {
            lastStableHemisphere = stableHemisphere;
            if (updateSample) {
                lastObservedZ = playerZ;
            }
            return;
        }

        boolean crossedNorth = lastObservedZ < 0 && playerZ > 0;
        boolean crossedSouth = lastObservedZ > 0 && playerZ < 0;
        boolean changedHemisphere = stableHemisphere != lastStableHemisphere && (crossedNorth || crossedSouth);

        if (changedHemisphere && canFireHemisphereTitle()) {
            String hemisphereTitle = crossedNorth ? "NORTHERN HEMISPHERE" : "SOUTHERN HEMISPHERE";
            int durationTicks = (int) Math.round(clamp(LatitudeConfig.zoneEnterTitleSeconds, 2.0, 10.0) * 20.0);
            double scale = clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);
            ZoneEnterTitleOverlay.trigger(hemisphereTitle, durationTicks, scale);
            lastHemisphereTitleAtMs = System.currentTimeMillis();
        }

        lastStableHemisphere = stableHemisphere;
        if (updateSample) {
            lastObservedZ = playerZ;
        }
    }

    private static boolean canFireHemisphereTitle() {
        if (lastHemisphereTitleAtMs == Long.MIN_VALUE) {
            return true;
        }
        long now = System.currentTimeMillis();
        return (now - lastHemisphereTitleAtMs) >= HEMISPHERE_TITLE_COOLDOWN_MS;
    }

    private static char stableHemisphere(double z) {
        if (Math.abs(z) < EQUATOR_STABLE_DIST) {
            return '\0';
        }
        return z > 0 ? 'N' : 'S';
    }

    private static String buildZoneEnterTitle(Minecraft client, String canonicalZoneKey) {
        String zoneName = zoneDisplayName(canonicalZoneKey).toUpperCase();
        if (!LatitudeConfig.showZoneBaseDegreesOnTitle) {
            return zoneName;
        }

        if (client.player == null || client.level == null) {
            return zoneName;
        }
        var border = client.level.getWorldBorder();

        String degText = com.example.globe.util.LatitudeMath.formatLatitudeDeg(border, client.player.getZ());
        return zoneName + " " + degText;
    }

    private static String canonicalTitleZoneKey(net.minecraft.world.level.border.WorldBorder border, double z) {
        double absDeg = Math.abs(com.example.globe.util.LatitudeMath.degreesFromZ(border, z));
        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
        return band.name();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
