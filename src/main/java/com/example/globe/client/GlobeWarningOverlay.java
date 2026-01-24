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

    private static String zoneKeyFor(double z) {
        int absZ = (int) Math.floor(Math.abs(z));
        if (absZ < 1500) {
            return "EQUATOR";
        } else if (absZ < 4500) {
            return "TROPICAL";
        } else if (absZ < 7500) {
            return "SUBTROPICAL";
        } else if (absZ < 10000) {
            return "TEMPERATE";
        } else if (absZ < 11750) {
            return "SUBPOLAR";
        } else {
            return "POLAR";
        }
    }

    private static String zoneKeyFor(double z, int radius) {
        if (radius <= 0) {
            return zoneKeyFor(z);
        }

        int absZ = (int) Math.floor(Math.abs(z));
        double t = absZ / (double) radius;
        if (t < 0.10) {
            return "EQUATOR";
        } else if (t < 0.30) {
            return "TROPICAL";
        } else if (t < 0.50) {
            return "SUBTROPICAL";
        } else if (t < 0.666) {
            return "TEMPERATE";
        } else if (t < 0.783) {
            return "SUBPOLAR";
        } else {
            return "POLAR";
        }
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

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null) {
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

                int radius = (int) Math.round(client.world.getWorldBorder().getSize() * 0.5);
                String zoneKey = zoneKeyFor(client.player.getZ(), radius);
                if (lastZoneKey == null || !lastZoneKey.equals(zoneKey)) {
                    lastZoneKey = zoneKey;
                    if (LatitudeConfig.zoneEnterTitleEnabled) {
                        String titleText = buildZoneEnterTitle(client, zoneKey, radius);
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

            if (state.type() == GlobeClientState.WarningType.POLAR) {
                GlobeClientState.PolarStage stage = (GlobeClientState.PolarStage) state.stage();
                bestText = switch (stage) {
                    case UNEASE -> Text.literal("The air is turning bitterly cold. You should consider turning back.");
                    case EFFECTS_I -> Text.literal("The cold seeps into your body. Movement is becoming difficult.");
                    case EFFECTS_II -> Text.literal("The wind howls across the ice. Exposed skin begins to numb.");
                    case WHITEOUT_APPROACH -> Text.literal("Whiteout conditions ahead. Visibility is rapidly dropping.");
                    case LETHAL -> Text.literal("DANGER! You are entering a lethal cold zone. Turn back immediately.")
                            .formatted(Formatting.RED, Formatting.BOLD);
                    case HOPELESS -> Text.literal("The cold overwhelms you.")
                            .formatted(Formatting.RED, Formatting.BOLD);
                    default -> null;
                };
            } else {
                GlobeClientState.StormStage stage = (GlobeClientState.StormStage) state.stage();
                bestText = switch (stage) {
                    case WARNING -> Text.literal("The wind is rising. The horizon ahead looks wrong.");
                    case DANGER -> Text.literal("DANGER! Catastrophic storms ahead. Turn back.")
                            .formatted(Formatting.RED, Formatting.BOLD);
                    case EDGE_ABSOLUTE -> Text.literal("DANGER! The world edge is tearing the air apart. You can’t survive this.")
                            .formatted(Formatting.RED, Formatting.BOLD);
                    default -> null;
                };
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

    private static String buildZoneEnterTitle(MinecraftClient client, String zoneKey, int radius) {
        String zoneName = zoneDisplayName(zoneKey).toUpperCase();
        if (!LatitudeConfig.showZoneBaseDegreesOnTitle) {
            return zoneName;
        }

        if (client.player == null || client.world == null) {
            return zoneName;
        }
        String degText = LatitudeMath.formatLatitudeDeg(client.player.getZ(), client.world.getWorldBorder());
        return zoneName + " " + degText;
    }

    private static int zoneBaseDegrees(String zoneKey, int radius) {
        double lo;
        double hi;

        if (radius <= 0) {
            return 0;
        }

        switch (zoneKey) {
            case "EQUATOR" -> {
                return 0;
            }
            case "TROPICAL" -> {
                lo = 0.10;
                hi = 0.30;
            }
            case "SUBTROPICAL" -> {
                lo = 0.30;
                hi = 0.50;
            }
            case "TEMPERATE" -> {
                lo = 0.50;
                hi = 0.666;
            }
            case "SUBPOLAR" -> {
                lo = 0.666;
                hi = 0.783;
            }
            case "POLAR" -> {
                lo = 0.783;
                hi = 1.0;
            }
            default -> {
                return 0;
            }
        }

        double mid = (lo + hi) * 0.5;
        int deg = (int) Math.round(mid * 90.0);
        if (deg < 0) deg = 0;
        if (deg > 90) deg = 90;
        return deg;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
