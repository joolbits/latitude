package com.example.globe.client;

import com.example.globe.GlobeMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    private static final String EW_SAND_WARN_TEXT =
            "Sandstorms ahead. Consider turning back.";
    private static final String EW_SAND_DANGER_TEXT =
            "It is too dangerous to continue. Turn back.";

    private static long lastZoneUpdateWorldTime = Long.MIN_VALUE;
    private static int lastZoneUpdateX = Integer.MIN_VALUE;
    private static int lastZoneUpdateZ = Integer.MIN_VALUE;

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

    private static String zoneDisplayName(String zoneKey) {
        return switch (zoneKey) {
            case "EQUATOR" -> "Equator";
            case "TROPICAL" -> "Tropics";
            case "SUBTROPICAL" -> "Subtropics";
            case "TEMPERATE" -> "Temperate";
            case "SUBPOLAR" -> "Subpolar";
            case "POLAR" -> "Polar";
            default -> zoneKey;
        };
    }

    private static String biomeName(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return "Unknown";
        }
        var biomeEntry = client.world.getBiome(client.player.getBlockPos());
        var optKey = biomeEntry.getKey();
        if (optKey.isPresent()) {
            String path = optKey.get().getValue().getPath();
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

    private static Text poleTextForStage(GlobeClientState.PolarStage stage) {
        if (stage == null) return null;
        return switch (stage) {
            case WARN_1 -> Text.literal(POLE_WARN_1_TEXT);
            case WARN_2 -> Text.literal(POLE_WARN_2_TEXT);
            case DANGER -> Text.literal(POLE_DANGER_TEXT).formatted(Formatting.RED, Formatting.BOLD);
            case LETHAL -> Text.literal(POLE_LETHAL_TEXT).formatted(Formatting.RED, Formatting.BOLD);
            default -> null;
        };
    }

    private static Text ewTextForStage(GlobeClientState.EwStormStage stage) {
        if (stage == null) return null;
        return switch (stage) {
            case LEVEL_1 -> Text.literal(EW_SAND_WARN_TEXT);
            case LEVEL_2 -> Text.literal(EW_SAND_DANGER_TEXT).formatted(Formatting.RED, Formatting.BOLD);
            default -> null;
        };
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null) {
            return;
        }

        if (!LatitudeConfig.showWarningMessages) {
            return;
        }

        if (client.player == null || client.world == null) {
            return;
        }

        try {
            long worldTime = client.world.getTime();
            if (debugStartWorldTime < 0L || worldTime < debugStartWorldTime) {
                debugStartWorldTime = worldTime;
                lastZoneKey = null;
            }

            var eval = GlobeClientState.evaluate(client);

            int screenW = client.getWindow().getScaledWidth();

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

                var border = client.world.getWorldBorder();
                String zoneKey = com.example.globe.util.LatitudeMath.zoneKey(border, client.player.getZ());
                if (lastZoneKey == null || !lastZoneKey.equals(zoneKey)) {
                    lastZoneKey = zoneKey;
                    if (LatitudeConfig.zoneEnterTitleEnabled) {
                        String titleText = buildZoneEnterTitle(client, zoneKey);
                        int durationTicks = (int) Math.round(clamp(LatitudeConfig.zoneEnterTitleSeconds, 2.0, 10.0) * 20.0);
                        double scale = clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);
                        ZoneEnterTitleOverlay.trigger(titleText, durationTicks, scale);
                    }
                }
            }

            Text bestText = null;
            var state = GlobeClientState.computeWarningState(client.world, client.player);
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
                bestText = ewTextForStage(stage);
            }

            if (bestText == null) {
                return;
            }

            // Draw final warning (no scaling for now to avoid compilation issues)
            int warnY = client.getWindow().getScaledHeight() - 68;
            if (warnY < 18) {
                warnY = 18;
            }
            drawCenteredWarning(ctx, client.textRenderer, bestText, warnY);
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("GlobeWarningOverlay.render crashed", t);
        }
    }

    private static void drawCenteredWarning(DrawContext ctx, TextRenderer tr, Text text, int y) {
        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int w = tr.getWidth(text);
        int x = Math.max(4, (screenW - w) / 2);
        ctx.drawTextWithShadow(tr, text, x, y, 0xFFFFFFFF);
    }

    private static String buildZoneEnterTitle(MinecraftClient client, String zoneKey) {
        String zoneName = zoneDisplayName(zoneKey).toUpperCase();
        if (!LatitudeConfig.showZoneBaseDegreesOnTitle) {
            return zoneName;
        }

        if (client.player == null || client.world == null) {
            return zoneName;
        }
        var border = client.world.getWorldBorder();

        int baseDeg = com.example.globe.util.LatitudeMath.zoneCenterDeg(zoneKey);
        if (baseDeg <= 0) {
            return zoneName + " 0\u00b0";
        }

        char hemi = com.example.globe.util.LatitudeMath.hemisphere(border, client.player.getZ());
        return zoneName + " " + baseDeg + "\u00b0" + hemi;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
