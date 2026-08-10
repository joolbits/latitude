package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

public final class GlobeWarningOverlay {
    private static long debugStartWorldTime = -1L;
    private static ClientLevel lastWarningLevel;
    private static long lastWarningWorldTime = Long.MIN_VALUE;
    private static String lastZoneKey;
    /** Absolute latitude at which the last zone-entry notification fired; NaN = none this session. */
    private static double lastZoneAnnounceLatDeg = Double.NaN;
    /** World time of the last zone-entry notification; used only to rate-limit repeats. */
    private static long lastZoneAnnounceWorldTime = Long.MIN_VALUE;
    // Zone identity and the zone-entry notification rate limit both live in ZoneTitlePolicy, which
    // is pure and directly testable. This class only wires it to the client.

    private static final String POLE_WARN_1_TEXT =
            "The air is turning bitterly cold. You should consider turning back.";
    private static final String POLE_WARN_2_TEXT =
            "The cold seeps into your body. Movement is becoming difficult.";
    private static final String POLE_DANGER_TEXT =
            "DANGER! You are entering a lethal cold zone. Turn back immediately.";
    private static final String POLE_LETHAL_TEXT =
            "The cold overwhelms you.";
    private static final int POLAR_KEYLINE_RGB = 0x080609;

    private static final String EW_VISIBILITY_WARN_TEXT =
            "Storms ahead. Low visibility; consider turning back.";
    private static final String EW_VISIBILITY_DANGER_TEXT =
            "Zero visibility ahead. Turn around.";
    private static final int EW_KEYLINE_RGB = 0x080609;

    private static final boolean DEBUG_ENTRY_TITLES = Boolean.getBoolean("latitude.debugEntryTitles");
    private static final int EQUATOR_STABLE_DIST = 64;
    private static final int HEMISPHERE_TITLE_MAX_STEP_BLOCKS = 256;
    private static final long HEMISPHERE_TITLE_COOLDOWN_MS = 15_000L;

    private static long lastZoneUpdateWorldTime = Long.MIN_VALUE;
    private static int lastZoneUpdateX = Integer.MIN_VALUE;
    private static int lastZoneUpdateZ = Integer.MIN_VALUE;
    private static char lastStableHemisphere = '\0';
    private static double lastObservedZ = Double.NaN;
    private static long lastHemisphereTitleAtMs = Long.MIN_VALUE;
    private static long lastWarningDebugWorldTime = Long.MIN_VALUE;
    private static String lastWarningDebugText;
    private static final PolarPresentationPolicy.PolarWarningEpisode POLAR_WARNING_EPISODE =
            new PolarPresentationPolicy.PolarWarningEpisode();
    private static final EwPresentationPolicy.WarningEpisode EW_WARNING_EPISODE =
            new EwPresentationPolicy.WarningEpisode();

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
            case DANGER -> Component.literal(POLE_DANGER_TEXT).withStyle(ChatFormatting.RED);
            case LETHAL -> Component.literal(POLE_LETHAL_TEXT).withStyle(ChatFormatting.RED);
            default -> null;
        };
    }

    private static Component ewTextForStage(GlobeClientState.EwStormStage stage) {
        if (stage == null) return null;
        return switch (stage) {
            case LEVEL_1 -> Component.literal(EW_VISIBILITY_WARN_TEXT);
            case LEVEL_2 -> Component.literal(EW_VISIBILITY_DANGER_TEXT);
            default -> null;
        };
    }

    public static void render(GuiGraphics ctx, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();

        if (client == null) {
            clearWarningWorldState();
            return;
        }

        if (client.player == null || client.level == null) {
            clearWarningWorldState();
            return;
        }

        if (GlobeClientState.DEBUG_DISABLE_WARNINGS) {
            return;
        }

        try {
            long worldTime = client.level.getGameTime();
            boolean levelChanged = lastWarningLevel != client.level;
            boolean timeRolledBack = lastWarningWorldTime != Long.MIN_VALUE
                    && worldTime < lastWarningWorldTime;
            if (levelChanged) {
                resetWorldEntryState(worldTime);
            } else if (timeRolledBack) {
                resyncWorldClock(worldTime);
            }
            lastWarningLevel = client.level;
            lastWarningWorldTime = worldTime;

            var eval = GlobeClientState.evaluate(client);

            int screenW = client.getWindow().getGuiScaledWidth();

            if (!eval.active()) {
                return;
            }

            int px = client.player.getBlockX();
            int pz = client.player.getBlockZ();

            boolean movedFar = lastZoneUpdateX == Integer.MIN_VALUE
                    || Math.abs(px - lastZoneUpdateX) > 16
                    || Math.abs(pz - lastZoneUpdateZ) > 16;

            if (eval.surfaceOk()
                    && (lastZoneUpdateWorldTime == Long.MIN_VALUE || movedFar || (worldTime % 10L) == 0L)) {
                // Captured BEFORE the sample is overwritten: a teleport must never be rate-limited
                // as if it were someone loitering on a boundary.
                int previousSampleZ = lastZoneUpdateZ;
                lastZoneUpdateWorldTime = worldTime;
                lastZoneUpdateX = px;
                lastZoneUpdateZ = pz;

                var border = client.level.getWorldBorder();
                // Zone identity is the RAW band for this latitude, so the reported zone and the
                // displayed title change at the exact boundary and always agree with the world.
                // Repeat NOTIFICATIONS while lingering on that boundary are rate-limited separately,
                // below -- suppressing the announcement must never mean misreporting the zone.
                double absLatDeg = com.example.globe.util.LatitudeMath.absLatDegExact(border, client.player.getZ());
                String canonicalZoneKey = LatitudeBands.Band.values()[ZoneTitlePolicy.segmentFor(absLatDeg)].name();
                if (lastZoneKey == null || !lastZoneKey.equals(canonicalZoneKey)) {
                    boolean firstZoneOfSession = lastZoneKey == null;
                    lastZoneKey = canonicalZoneKey;
                    boolean teleported = previousSampleZ != Integer.MIN_VALUE
                            && ZoneTitlePolicy.isTeleportStep(previousSampleZ, pz);
                    if (LatitudeConfig.zoneEnterTitleEnabled
                            && ZoneTitlePolicy.shouldAnnounce(absLatDeg, worldTime,
                                    lastZoneAnnounceLatDeg, lastZoneAnnounceWorldTime,
                                    firstZoneOfSession, teleported)) {
                        lastZoneAnnounceLatDeg = absLatDeg;
                        lastZoneAnnounceWorldTime = worldTime;
                        String titleText = buildZoneEnterTitle(client, canonicalZoneKey);
                        int durationTicks = (int) Math.round(clamp(LatitudeConfig.zoneEnterTitleSeconds, 2.0, 10.0) * 20.0);
                        double scale = clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);
                        logEntryTitle("zone_trigger", titleText, client, client.player.getZ(), '\0', 0.0);
                        ZoneEnterTitleOverlay.trigger(titleText, durationTicks, scale);
                    } else {
                        logEntryTitle("zone_reannounce_suppressed", canonicalZoneKey,
                                client, client.player.getZ(), '\0', 0.0);
                    }
                }

                maybeTriggerHemisphereTitle(client, client.player.getZ());
            }

            var polarStage = GlobeClientState.computePolarStage(client.level, client.player);
            double absoluteLatitude = GlobeClientState.absoluteLatitudeDegrees(
                    client.level.getWorldBorder(),
                    client.player.getZ());
            if (eval.surfaceOk()) {
                POLAR_WARNING_EPISODE.update(polarRank(polarStage), absoluteLatitude, worldTime);
            }
            var activePolarStage = polarStageForRank(POLAR_WARNING_EPISODE.activeStageRank(worldTime));
            double distanceToEwBorder = GlobeClientState.distanceToEwBorderBlocks(client.player.getX());
            var ewTextStage = GlobeClientState.computeEwTextStage(client.level, client.player);
            EW_WARNING_EPISODE.update(
                    ewRank(ewTextStage),
                    distanceToEwBorder,
                    worldTime,
                    GlobeClientState.ewEpisodePaused());
            var activeEwStage = ewStageForRank(EW_WARNING_EPISODE.activeStageRank(worldTime));
            var state = GlobeClientState.arbitrateWarning(activePolarStage, activeEwStage);

            // Stable precedence (corners):
            // 1) active polar lethal
            // 2) ew level 2
            // 3) active polar stage (warn/danger)
            // 4) ew level 1
            Component bestText;
            if (state.type() == GlobeClientState.WarningType.STORM) {
                GlobeClientState.EwStormStage stage = (GlobeClientState.EwStormStage) state.stage();
                bestText = ewTextForStage(stage);
            } else {
                bestText = poleTextForStage(activePolarStage);
            }

            if (bestText == null) {
                return;
            }

            if (!LatitudeConfig.showWarningMessages) {
                return;
            }

            // Draw final warning (no scaling for now to avoid compilation issues)
            int warnY = client.getWindow().getGuiScaledHeight() - 68;
            if (warnY < 18) {
                warnY = 18;
            }
            maybeLogWarningRender(client, state, bestText);
            if (state.type() == GlobeClientState.WarningType.STORM) {
                float alpha = EW_WARNING_EPISODE.alpha(worldTime)
                        * GlobeClientState.ewPresentationVisibility();
                int color = warningColorWithAlpha(bestText, alpha);
                drawCenteredEwWarning(ctx, client.font, bestText, warnY, color);
            } else {
                int color = warningColorWithAlpha(bestText, POLAR_WARNING_EPISODE.alpha(worldTime));
                drawCenteredPolarWarning(ctx, client.font, bestText, warnY, color);
            }
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("GlobeWarningOverlay.render crashed", t);
        }
    }

    private static int warningColorWithAlpha(Component text, float alpha01) {
        TextColor styleColor = text.getStyle().getColor();
        int rgb = styleColor != null ? styleColor.getValue() : 0xFFFFFF;
        int alpha = (int) Mth.clamp(alpha01 * 255.0f, 0.0f, 255.0f);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static void drawCenteredEwWarning(GuiGraphics ctx, Font tr, Component text, int y, int argbColor) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int w = tr.width(text);
        int x = Math.max(4, (screenW - w) / 2);
        int alpha = argbColor & 0xFF000000;
        int keylineColor = alpha | EW_KEYLINE_RGB;
        Component keylineText = Component.literal(text.getString());

        for (int[] offset : EwPresentationPolicy.outlineOffsets()) {
            ctx.drawString(tr, keylineText, x + offset[0], y + offset[1], keylineColor, false);
        }
        ctx.drawString(tr, text, x, y, argbColor, false);
    }

    private static void drawCenteredPolarWarning(GuiGraphics ctx, Font tr, Component text, int y, int argbColor) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int w = tr.width(text);
        int x = Math.max(4, (screenW - w) / 2);
        int alpha = argbColor & 0xFF000000;
        int keylineColor = alpha | POLAR_KEYLINE_RGB;
        Component keylineText = Component.literal(text.getString());

        for (int[] offset : PolarPresentationPolicy.outlineOffsets()) {
            ctx.drawString(tr, keylineText, x + offset[0], y + offset[1], keylineColor, false);
        }
        ctx.drawString(tr, text, x, y, argbColor, false);
    }

    private static int polarRank(GlobeClientState.PolarStage stage) {
        return switch (stage) {
            case WARN_1 -> 1;
            case WARN_2 -> 2;
            case DANGER -> 3;
            case LETHAL -> 4;
            default -> 0;
        };
    }

    private static GlobeClientState.PolarStage polarStageForRank(int rank) {
        return switch (rank) {
            case 1 -> GlobeClientState.PolarStage.WARN_1;
            case 2 -> GlobeClientState.PolarStage.WARN_2;
            case 3 -> GlobeClientState.PolarStage.DANGER;
            case 4 -> GlobeClientState.PolarStage.LETHAL;
            default -> GlobeClientState.PolarStage.NONE;
        };
    }

    private static int ewRank(GlobeClientState.EwStormStage stage) {
        return switch (stage) {
            case LEVEL_1 -> 1;
            case LEVEL_2 -> 2;
            default -> 0;
        };
    }

    private static GlobeClientState.EwStormStage ewStageForRank(int rank) {
        return switch (rank) {
            case 1 -> GlobeClientState.EwStormStage.LEVEL_1;
            case 2 -> GlobeClientState.EwStormStage.LEVEL_2;
            default -> GlobeClientState.EwStormStage.NONE;
        };
    }

    private static void resetWorldEntryState(long worldTime) {
        debugStartWorldTime = worldTime;
        lastZoneKey = null;
        lastZoneAnnounceLatDeg = Double.NaN;
        lastZoneAnnounceWorldTime = Long.MIN_VALUE;
        lastZoneUpdateWorldTime = Long.MIN_VALUE;
        lastZoneUpdateX = Integer.MIN_VALUE;
        lastZoneUpdateZ = Integer.MIN_VALUE;
        lastStableHemisphere = '\0';
        lastObservedZ = Double.NaN;
        lastWarningDebugWorldTime = Long.MIN_VALUE;
        lastWarningDebugText = null;
        POLAR_WARNING_EPISODE.reset();
        EW_WARNING_EPISODE.reset();
        ZoneEnterTitleOverlay.reset();
        GlobeClientState.resetEwPresentationState();
        if (DEBUG_ENTRY_TITLES) {
            GlobeMod.LOGGER.info("[LAT][ENTRY_TITLE] action=reset worldTime={}", worldTime);
        }
    }

    private static void resyncWorldClock(long worldTime) {
        long deltaTicks = worldTime - lastWarningWorldTime;
        POLAR_WARNING_EPISODE.shiftClock(deltaTicks);
        EW_WARNING_EPISODE.shiftClock(deltaTicks);
        ZoneEnterTitleOverlay.shiftClock(deltaTicks);
        if (debugStartWorldTime >= 0L) {
            debugStartWorldTime += deltaTicks;
        }
        if (lastZoneUpdateWorldTime != Long.MIN_VALUE) {
            lastZoneUpdateWorldTime += deltaTicks;
        }
        if (lastWarningDebugWorldTime != Long.MIN_VALUE) {
            lastWarningDebugWorldTime += deltaTicks;
        }
        if (DEBUG_ENTRY_TITLES) {
            GlobeMod.LOGGER.info(
                    "[LAT][ENTRY_TITLE] action=clock_resync worldTime={} deltaTicks={}",
                    worldTime,
                    deltaTicks);
        }
    }

    private static void clearWarningWorldState() {
        if (lastWarningLevel == null && lastWarningWorldTime == Long.MIN_VALUE) {
            return;
        }
        resetForDisconnect();
    }

    public static void resetForDisconnect() {
        lastWarningLevel = null;
        lastWarningWorldTime = Long.MIN_VALUE;
        resetWorldEntryState(-1L);
    }

    private static void maybeTriggerHemisphereTitle(Minecraft client, double playerZ) {
        var border = client.level != null ? client.level.getWorldBorder() : null;
        double centerZ = border != null ? border.getCenterZ() : 0.0;
        char stableHemisphere = stableHemisphere(border, playerZ);
        boolean titleActive = ZoneEnterTitleOverlay.isActive();
        boolean updateSample = Math.abs(playerZ - centerZ) >= EQUATOR_STABLE_DIST || Double.isNaN(lastObservedZ);

        if (!LatitudeConfig.zoneEnterTitleEnabled) {
            if (stableHemisphere != '\0') {
                lastStableHemisphere = stableHemisphere;
            }
            if (updateSample) {
                lastObservedZ = playerZ;
            }
            logEntryTitle("hemisphere_disabled", "", client, playerZ, stableHemisphere, 0.0);
            return;
        }

        if (titleActive) {
            if (stableHemisphere != '\0') {
                lastStableHemisphere = stableHemisphere;
            }
            if (updateSample) {
                lastObservedZ = playerZ;
            }
            logEntryTitle("hemisphere_suppressed_title_active", "", client, playerZ, stableHemisphere, 0.0);
            return;
        }

        if (Double.isNaN(lastObservedZ) && updateSample) {
            lastObservedZ = playerZ;
            logEntryTitle("hemisphere_seed_observed_z", "", client, playerZ, stableHemisphere, 0.0);
        }

        if (stableHemisphere == '\0') {
            if (updateSample) {
                lastObservedZ = playerZ;
            }
            logEntryTitle("hemisphere_suppressed_equator", "", client, playerZ, stableHemisphere, 0.0);
            return;
        }

        if (lastStableHemisphere == '\0') {
            lastStableHemisphere = stableHemisphere;
            if (updateSample) {
                lastObservedZ = playerZ;
            }
            logEntryTitle("hemisphere_seed_stable", "", client, playerZ, stableHemisphere, 0.0);
            return;
        }

        double stepBlocks = Math.abs(playerZ - lastObservedZ);
        if (stepBlocks > HEMISPHERE_TITLE_MAX_STEP_BLOCKS) {
            lastStableHemisphere = stableHemisphere;
            if (updateSample) {
                lastObservedZ = playerZ;
            }
            logEntryTitle("hemisphere_suppressed_large_step", "", client, playerZ, stableHemisphere, stepBlocks);
            return;
        }

        boolean crossedNorth = lastObservedZ > centerZ && playerZ < centerZ;
        boolean crossedSouth = lastObservedZ < centerZ && playerZ > centerZ;
        boolean changedHemisphere = stableHemisphere != lastStableHemisphere && (crossedNorth || crossedSouth);

        if (changedHemisphere && canFireHemisphereTitle()) {
            String hemisphereTitle = crossedNorth ? "NORTHERN HEMISPHERE" : "SOUTHERN HEMISPHERE";
            int durationTicks = (int) Math.round(clamp(LatitudeConfig.zoneEnterTitleSeconds, 2.0, 10.0) * 20.0);
            double scale = clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);
            logEntryTitle("hemisphere_trigger", hemisphereTitle, client, playerZ, stableHemisphere, stepBlocks);
            ZoneEnterTitleOverlay.trigger(hemisphereTitle, durationTicks, scale);
            lastHemisphereTitleAtMs = System.currentTimeMillis();
        } else {
            logEntryTitle("hemisphere_no_trigger", "", client, playerZ, stableHemisphere, stepBlocks);
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

    private static char stableHemisphere(net.minecraft.world.level.border.WorldBorder border, double z) {
        double centerZ = border != null ? border.getCenterZ() : 0.0;
        if (Math.abs(z - centerZ) < EQUATOR_STABLE_DIST) {
            return '\0';
        }
        return com.example.globe.util.LatitudeMath.hemisphere(border, z);
    }

    private static void logEntryTitle(String action, String title, Minecraft client, double playerZ, char stableHemisphere, double stepBlocks) {
        if (!DEBUG_ENTRY_TITLES || client == null || client.level == null || client.player == null) {
            return;
        }
        var border = client.level.getWorldBorder();
        String canonicalZoneKey = canonicalTitleZoneKey(border, playerZ);
        char canonicalHemisphere = com.example.globe.util.LatitudeMath.hemisphere(border, playerZ);
        double latDeg = com.example.globe.util.LatitudeMath.degreesFromZ(border, playerZ);
        GlobeMod.LOGGER.info("[LAT][ENTRY_TITLE] action={} title=\"{}\" x={} z={} centerZ={} worldTime={} zone={} latDeg={} canonicalHemisphere={} stableHemisphere={} lastStableHemisphere={} lastObservedZ={} stepBlocks={}",
                action,
                title,
                client.player.getX(),
                playerZ,
                border.getCenterZ(),
                client.level.getGameTime(),
                canonicalZoneKey,
                latDeg,
                canonicalHemisphere,
                stableHemisphere == '\0' ? "none" : Character.toString(stableHemisphere),
                lastStableHemisphere == '\0' ? "none" : Character.toString(lastStableHemisphere),
                Double.isNaN(lastObservedZ) ? "nan" : Double.toString(lastObservedZ),
                stepBlocks);
    }

    private static void maybeLogWarningRender(Minecraft client, GlobeClientState.WarningState state, Component bestText) {
        if (!Boolean.getBoolean("latitude.debugEwWarn") || client == null || client.level == null || client.player == null || bestText == null
                || state.type() != GlobeClientState.WarningType.STORM) {
            return;
        }
        long worldTime = client.level.getGameTime();
        String text = bestText.getString();
        if (text.equals(lastWarningDebugText) && lastWarningDebugWorldTime != Long.MIN_VALUE && worldTime - lastWarningDebugWorldTime < 20L) {
            return;
        }
        lastWarningDebugWorldTime = worldTime;
        lastWarningDebugText = text;
        var border = client.level.getWorldBorder();
        double x = client.player.getX();
        double distWest = Math.abs(x - border.getMinX());
        double distEast = Math.abs(border.getMaxX() - x);
        GlobeMod.LOGGER.info("[LAT][WARNING_RENDER] text=\"{}\" type={} stage={} x={} z={} worldTime={} distWest={} distEast={} borderWest={} borderEast={} titleActive={} zoneTitleEnabled={}",
                text,
                state.type(),
                state.stage(),
                x,
                client.player.getZ(),
                worldTime,
                distWest,
                distEast,
                border.getMinX(),
                border.getMaxX(),
                ZoneEnterTitleOverlay.isActive(),
                LatitudeConfig.zoneEnterTitleEnabled);
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

    /**
     * Which of the five climate-band segments {@code absLatDeg} falls in, with NO buffer: 0 = deep
     * tropical, 1 = subtropical, 2 = temperate, 3 = subpolar, 4 = polar. Segment index order matches
     * {@code LatitudeBands.Band}'s declared enum order, so a caller can index {@code Band.values()}
     * directly. A pure raw lookup, mirrors {@code LatitudeBands.fromAbsoluteLatitudeDeg}.
     */
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
