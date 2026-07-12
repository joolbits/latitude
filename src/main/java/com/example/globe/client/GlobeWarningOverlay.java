package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.example.globe.core.HemisphereCrossing;
import com.example.globe.core.ui.PolarWarningVignette;
import com.example.globe.util.LatitudeBands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

public final class GlobeWarningOverlay {
    private static long debugStartWorldTime = -1L;
    private static String lastZoneKey;

    // Polar warning ladder (B-3-P3), anchored to LatitudeMath.POLAR_STAGE_*_PROGRESS:
    // WARN_1 @85 deg (snow onset), WARN_2 @87 deg (blizzard building), DANGER @89 deg, LETHAL @89.7 deg.
    // The ladder DEGREE constants (POLAR_STAGE_*) STAY PUT -- they are SHARED with the EW storm axis (B-3-P3
    // KEEP-SHARED coupling) -- so they do NOT move when the player-affecting hazard onset moves. TEST 76
    // moved that onset 88.5 -> 87.5 (see PolarHazardWindow): slowness now begins just 0.5 deg poleward of
    // WARN_2 (87 -> 87.5), so WARN_2 was reworded to foreshadow it HONESTLY in the FUTURE tense ("the cold
    // WILL slow you"). At the instant WARN_2 fires (87 deg) slowness is not yet active, so a present-tense
    // claim would break the rule that a warning must never claim a mechanic that isn't true when shown; a
    // future/imminent claim is fine (slowness is one boundary-step away). Copy (Peetsa's blizzard register):
    // T1/T2 carry the expedition-approach flavor; DANGER is Peetsa's line verbatim; LETHAL is the plainer,
    // grimmer "Hypothermia sets in.". NB: the polar warnings are drawn NON-bold (DANGER/LETHAL are RED, not
    // RED+BOLD) -- MC's fake-bold double-stroke fought the dark keyline (see drawCenteredWarning); the crisp
    // outline + RED + LETHAL's 1.15x scale + the ember vignette carry the escalation instead of bold.
    private static final String POLE_WARN_1_TEXT =
            "Snow begins to fall. Blizzard conditions ahead -- consider turning back.";
    private static final String POLE_WARN_2_TEXT =
            "Blizzard conditions worsening -- the cold will slow you. Turn back while you can.";
    private static final String POLE_DANGER_TEXT =
            "DANGER: Lethal blizzard conditions ahead. Turn back.";
    private static final String POLE_LETHAL_TEXT =
            "Hypothermia sets in.";

    // CD finding F1 / R1 -- dark KEYLINE behind the polar warning lines. Minecraft RED (0xFF5555) on the
    // near-white whiteout fill is ~2.7:1 contrast; a 1px near-black outline (the zone-title outline idiom,
    // TitleStyle.OUTLINE_OFFSETS_8) makes the text pop on the brightest screen the game ever draws. All four
    // tiers get it (pure white on a whiteout needs it as much as the red lines do). Near-black with a faint
    // cold cast, matching the storm. CRITICAL (TEST 76): the keyline is stamped from a PLAIN, styleless copy
    // of the line, never the styled fill -- MC's font renderer keeps a component's OWN style color over the
    // passed keyline color (so a RED line would stamp a RED, not dark, keyline -> a red-on-red halo, the real
    // "smear") and fake-bold-DOUBLES every stamp. See drawCenteredWarning for the full mechanism + proof.
    private static final int POLE_KEYLINE_RGB = 0x080609;
    // CD finding F3 -- LETHAL renders slightly larger than DANGER so the final rung reads as a distinct, worse
    // beat rather than a 0.7 deg double-tap of the same red flash (pairs with LETHAL's deeper vignette).
    private static final float LETHAL_TEXT_SCALE = 1.15f;

    // Approach tier (LEVEL_1) — generic, mentions both storm + reduced visibility. Escalates to a
    // climate-specific tier-2 line below. Simplified from "...Head <direction> to turn back/immediately." to a
    // plain "Turn back." per live feedback -- smoother read, and the player already knows which way is back
    // (they just came from there); naming the escape direction was redundant.
    private static final String EW_STORM_WARN_TEMPLATE =
            "Storms and low visibility to the %s. Turn back.";
    // Near-edge tier (LEVEL_2), climate-specific: whiteout in the cold bands, blinding sandstorm elsewhere.
    private static final String EW_WHITEOUT_DANGER_TEMPLATE =
            "Whiteout conditions to the %s. Turn back.";
    private static final String EW_SANDSTORM_DANGER_TEMPLATE =
            "Blinding sandstorm to the %s. Turn back.";

    private static final boolean DEBUG_ENTRY_TITLES = Boolean.getBoolean("latitude.debugEntryTitles");

    private static long lastZoneUpdateWorldTime = Long.MIN_VALUE;
    private static int lastZoneUpdateX = Integer.MIN_VALUE;
    private static int lastZoneUpdateZ = Integer.MIN_VALUE;
    // Per-axis hemisphere-crossing tracking (debounce state persisted between ticks; the pure decision
    // lives in core.HemisphereCrossing). Z axis = N/S at the equator; X axis = E/W at the prime meridian.
    // side: -1 negative-of-center / +1 positive-of-center / 0 unseeded; observed: last confident coord
    // (NaN = unseeded); fireMs: wall time of the last fire on that axis (per-axis cooldown).
    private static int lastStableSideZ = 0;
    private static double lastObservedZ = Double.NaN;
    // B-4 item 2: raw per-tick coordinate (advanced every sample incl. inside the dead zone) feeding the
    // teleport guard, kept distinct from the held confident observed so a walked meridian/equator crossing
    // across the dead band is never mistaken for a jump. NaN = unseeded.
    private static double lastRawZ = Double.NaN;
    private static long lastHemiFireZMs = Long.MIN_VALUE;
    private static int lastStableSideX = 0;
    private static double lastObservedX = Double.NaN;
    private static double lastRawX = Double.NaN;
    private static long lastHemiFireXMs = Long.MIN_VALUE;
    // B-4 round-2 anti-spam: per-hemisphere FULL titles. Each SIDE of the line gets the big title ONCE per
    // visit-episode; these flags track which sides have already been FULL-announced since the player last
    // LEFT the band (neg = North/West, pos = South/East). Both start un-announced so the first crossing to
    // EITHER side is FULL; leaving the band re-arms both. Persisted between ticks per axis.
    private static boolean hemiNegAnnouncedZ = false;
    private static boolean hemiPosAnnouncedZ = false;
    private static boolean hemiNegAnnouncedX = false;
    private static boolean hemiPosAnnouncedX = false;
    // B-4 zone-title anti-spam: whether the FULL (big) climate-band title is armed. Starts true (the first
    // zone entry fires the big title once); a zone flip while still within one band-width (3 deg latitude)
    // of the boundary just crossed shows only the small action-bar message; the big title re-arms once the
    // player settles deep inside a zone. Latitude(Z)-axis only, so a single flag (pure logic in core.ZoneTitleBanding).
    private static boolean zoneFullArmed = true;
    // B-4 item 3: EPISODIC polar warning ladder. Each of the 4 tiers fires ONCE when crossed, shows ~10 s,
    // then fades out over ~1 s, and does NOT re-show while the player stays poleward -- the whole ladder
    // re-arms only on a full retreat below 84 deg (pure decision in core.PolarWarningEpisode). Highest tier
    // fired since the last retreat + the active on-screen message and its world-tick display window.
    private static final long POLE_WARN_HOLD_TICKS = 200L;   // ~10 s at 20 tps
    private static final long POLE_WARN_FADE_TICKS = 20L;    // ~1 s alpha ramp (in and out)
    private static int poleWarnHighestTier = 0;
    private static Component poleWarnText;                    // null = nothing showing
    private static long poleWarnStartTick = Long.MIN_VALUE;
    private static long poleWarnEndTick = Long.MIN_VALUE;
    // Understudy SWING: the DANGER/LETHAL warning text and the atmosphere pulse as ONE moment. When a serious
    // tier (3=DANGER / 4=LETHAL) fires, arm a subtle edge-darkening VIGNETTE synced to the text's appearance.
    // WALL-CLOCK armed (System.currentTimeMillis) -- the tick-clock lesson is law, so PolarVignetteOverlayHud
    // animates on wall time and never rubber-bands on a teleport tick stall. 0 = no vignette armed (the two
    // mild tiers earn nothing); cleared on a full retreat re-arm so the feature is a provable no-op when idle.
    private static int poleVignetteTier = 0;
    private static long poleVignetteStartMs = Long.MIN_VALUE;
    private static long lastWarningDebugWorldTime = Long.MIN_VALUE;
    private static String lastWarningDebugText;

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
        // Canonical source: LatitudeBands.displayNameForZoneKey (this IS the vocabulary the other surfaces
        // (CompassHud) were unified onto -- title's word set won: Tropical/Subtropical/Temperate/Subpolar/Polar.
        return LatitudeBands.displayNameForZoneKey(canonicalKey);
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
            // RED, NOT RED+BOLD: MC fakes bold by drawing every glyph twice (BakedSheetGlyph.renderChar,
            // +getBoldOffset()==1px), which doubled every dark keyline stamp into a smeared halo AND widened
            // the glyph advances so a non-bold keyline would drift out of registration. Non-bold keeps the
            // fill and its crisp dark outline perfectly aligned; the outline + red + scale carry the weight.
            case DANGER -> Component.literal(POLE_DANGER_TEXT).withStyle(ChatFormatting.RED);
            case LETHAL -> Component.literal(POLE_LETHAL_TEXT).withStyle(ChatFormatting.RED);
            default -> null;
        };
    }

    private static Component poleTextForTier(int tier) {
        GlobeClientState.PolarStage stage = switch (tier) {
            case 1 -> GlobeClientState.PolarStage.WARN_1;
            case 2 -> GlobeClientState.PolarStage.WARN_2;
            case 3 -> GlobeClientState.PolarStage.DANGER;
            case 4 -> GlobeClientState.PolarStage.LETHAL;
            default -> null;
        };
        return poleTextForStage(stage);
    }

    // B-4 item 3: run the pure episode ladder each throttled sample. A tier crossing arms a fresh ~10 s
    // display window; the ladder re-arms only on a full retreat below 84 deg (handled inside evaluate).
    private static void maybeTriggerPoleWarning(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }
        var border = client.level.getWorldBorder();
        double absLatDeg = com.example.globe.util.LatitudeMath.absLatDegExact(border, client.player.getZ());
        com.example.globe.core.PolarWarningEpisode.Step step =
                com.example.globe.core.PolarWarningEpisode.evaluate(absLatDeg, poleWarnHighestTier);
        poleWarnHighestTier = step.nextHighestFired();
        // A full retreat below 84 deg re-arms the whole ladder (nextHighestFired == 0) -- clear any armed
        // vignette so the swing is a provable no-op once the player has left the hazard region.
        if (poleWarnHighestTier == 0) {
            poleVignetteTier = 0;
            poleVignetteStartMs = Long.MIN_VALUE;
        }
        if (step.fireTier() > 0) {
            Component txt = poleTextForTier(step.fireTier());
            if (txt != null) {
                poleWarnText = txt;
                long now = client.level.getGameTime();
                poleWarnStartTick = now;
                poleWarnEndTick = now + POLE_WARN_HOLD_TICKS;
                // Only the two serious tiers earn atmosphere: arm the vignette pulse in the SAME instant the
                // DANGER/LETHAL text appears (a deeper tier crossing re-arms it, punctuating the new line).
                if (step.fireTier() == PolarWarningVignette.TIER_DANGER
                        || step.fireTier() == PolarWarningVignette.TIER_LETHAL) {
                    poleVignetteTier = step.fireTier();
                    poleVignetteStartMs = System.currentTimeMillis();
                }
                logEntryTitle("pole_warn_tier" + step.fireTier(), txt.getString(),
                        client, client.player.getX(), client.player.getZ());
            }
        }
    }

    /** The armed vignette tier (3=DANGER / 4=LETHAL, or 0 = none). Read by {@link PolarVignetteOverlayHud}. */
    static int poleVignetteTier() {
        return poleVignetteTier;
    }

    /** Wall-clock ms at which the current vignette pulse armed, or {@code Long.MIN_VALUE} if none. */
    static long poleVignetteStartMs() {
        return poleVignetteStartMs;
    }

    // Draws the active polar warning episode with a fade-in/hold/fade-out envelope. Returns true if it drew
    // (and therefore owns the warning line this frame), false if no episode is currently showing.
    private static boolean renderPoleWarningEpisode(GuiGraphicsExtractor ctx, Minecraft client, int warnY) {
        if (poleWarnText == null || client.level == null) {
            return false;
        }
        long now = client.level.getGameTime();
        // World-time reset / rejoin: a stale window can't outlive the world clock.
        if (now < poleWarnStartTick || now >= poleWarnEndTick) {
            return false;
        }
        long age = now - poleWarnStartTick;
        long remaining = poleWarnEndTick - now;
        float alpha = 1.0f;
        if (age < POLE_WARN_FADE_TICKS) {
            alpha = (float) age / (float) POLE_WARN_FADE_TICKS;
        } else if (remaining < POLE_WARN_FADE_TICKS) {
            alpha = (float) remaining / (float) POLE_WARN_FADE_TICKS;
        }
        if (alpha <= 0.001f) {
            return false;
        }
        TextColor styleColor = poleWarnText.getStyle().getColor();
        int rgb = styleColor != null ? styleColor.getValue() : 0xFFFFFF;
        int a = (int) Mth.clamp(alpha * 255.0f, 0.0f, 255.0f);
        int color = (a << 24) | (rgb & 0x00FFFFFF);
        // CD F1/F3 + TEST 75: ALL FOUR polar tiers get the dark keyline so both the red (DANGER/LETHAL) and
        // the plain-white (WARN_1/WARN_2) lines read on the whiteout; LETHAL additionally renders slightly
        // larger than DANGER (via poleWarnHighestTier == 4) so the final rung is a visibly distinct beat.
        boolean keyline = true;
        float scale = poleWarnHighestTier == 4 ? LETHAL_TEXT_SCALE : 1.0f;
        drawCenteredWarning(ctx, client.font, poleWarnText, warnY, color, keyline, scale);
        return true;
    }

    private static Component ewTextForStage(GlobeClientState.EwStormStage stage, boolean cold) {
        if (stage == null) return null;
        return switch (stage) {
            case LEVEL_1 -> Component.literal(EW_STORM_WARN_TEMPLATE);
            case LEVEL_2 -> Component.literal(cold ? EW_WHITEOUT_DANGER_TEMPLATE : EW_SANDSTORM_DANGER_TEMPLATE)
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            default -> null;
        };
    }

    private static boolean ewIsColdBand(net.minecraft.world.level.border.WorldBorder border, double playerZ) {
        double absDeg = Math.abs(com.example.globe.util.LatitudeMath.degreesFromZ(border, playerZ));
        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
        return band == LatitudeBands.Band.SUBPOLAR || band == LatitudeBands.Band.POLAR;
    }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
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
                resetWorldEntryState(worldTime);
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
                boolean zoneChanged = lastZoneKey == null || !lastZoneKey.equals(canonicalZoneKey);

                // B-4 zone-title anti-spam: mirror the hemisphere band hysteresis. The band is one band-width
                // (3 deg of latitude) in blocks == latitudeRadius/30, floored at DEAD_ZONE+32 exactly like the
                // hemisphere bands (so it can never fall below the on-the-line dead zone on tiny worlds). The
                // linger radius is measured as latitude distance to the nearest climate-band boundary,
                // converted to blocks (90 deg over the latitude radius).
                double latRadius = com.example.globe.util.LatitudeMath.latitudeRadius(border);
                double zoneBand = Math.max(latRadius / 30.0, HemisphereCrossing.DEAD_ZONE_BLOCKS + 32.0);
                double absLatDeg = com.example.globe.util.LatitudeMath.absLatDegExact(border, client.player.getZ());
                double distDegToBoundary = com.example.globe.core.ZoneTitleBanding.nearestBoundaryDistanceDeg(absLatDeg);
                double distBlocksToBoundary = latRadius > 0.0
                        ? (distDegToBoundary / 90.0) * latRadius : Double.MAX_VALUE;
                com.example.globe.core.ZoneTitleBanding.Result zoneEval =
                        com.example.globe.core.ZoneTitleBanding.evaluate(
                                zoneChanged, zoneFullArmed, distBlocksToBoundary, zoneBand);

                if (zoneChanged) {
                    lastZoneKey = canonicalZoneKey;
                    if (LatitudeConfig.zoneEnterTitleEnabled) {
                        String titleText = buildZoneEnterTitle(client, canonicalZoneKey);
                        if (zoneEval.fire() == com.example.globe.core.ZoneTitleBanding.Fire.FULL) {
                            int durationTicks = (int) Math.round(clamp(LatitudeConfig.zoneEnterTitleSeconds, 2.0, 10.0) * 20.0);
                            double scale = clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);
                            logEntryTitle("zone_trigger", titleText, client, client.player.getX(), client.player.getZ());
                            ZoneEnterTitleOverlay.trigger(titleText, durationTicks, scale);
                        } else {
                            // Lingering near the boundary just crossed: unobtrusive action-bar message, no big title.
                            showActionBarMessage(client, titleText);
                            logEntryTitle("zone_actionbar", titleText, client, client.player.getX(), client.player.getZ());
                        }
                    }
                }
                // Advance the armed state every throttled sample (even when unchanged / disabled) so the
                // re-arm happens as the player walks deep into a zone, and a re-enable can't replay a title.
                zoneFullArmed = zoneEval.nextFullArmed();

                maybeTriggerHemisphereTitles(client, client.player.getX(), client.player.getZ());
                maybeTriggerPoleWarning(client);
            }

            // B-4 round 3 item 6: F1 (hud hidden) suppresses the VISIBLE warning line, but the zone /
            // hemisphere / pole tracking above still runs every frame -- so a boundary crossed while the HUD
            // is hidden isn't silently swallowed, and re-showing the HUD can't replay a stale crossing.
            if (client.gui != null && client.gui.hud != null && client.gui.hud.isHidden()) {
                return;
            }

            int warnY = client.getWindow().getGuiScaledHeight() - 68;
            if (warnY < 18) {
                warnY = 18;
            }

            // B-4 item 3: the episodic polar warning owns the warning line while its ~10 s window is active
            // (rendered every frame for a smooth fade). When it's not showing, fall through to the
            // persistent E/W storm warning (left untouched this pass).
            if (renderPoleWarningEpisode(ctx, client, warnY)) {
                return;
            }

            GlobeClientState.EwStormStage stormStage =
                    GlobeClientState.computeEwStormStage(client.level, client.player);
            if (stormStage == GlobeClientState.EwStormStage.NONE) {
                return;
            }
            String dir = ewDangerDirection(client.level.getWorldBorder(), client.player.getX());
            boolean cold = ewIsColdBand(client.level.getWorldBorder(), client.player.getZ());
            Component base = ewTextForStage(stormStage, cold);
            if (base == null) {
                return;
            }
            Component bestText = Component.literal(String.format(base.getString(), dir.toLowerCase()))
                    .setStyle(base.getStyle());
            maybeLogWarningRender(client,
                    new GlobeClientState.WarningState(GlobeClientState.WarningType.STORM, stormStage, 0),
                    bestText);
            int color = warningColorWithPulse(bestText, client, tickCounter);
            drawCenteredWarning(ctx, client.font, bestText, warnY, color, false, 1.0f);
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

    // M2 (GUI-scale parity audit) -- wrap + center a warning line. A long pole/storm warning at a narrow
    // effective GUI resolution (down to the 320-px floor) used to run off the right edge; wrap it to the
    // screen width and stack the lines UPWARD from `y` so the bottom line keeps the original anchor and the
    // block grows into the free space above. A short warning wraps to a single line, so the common case is
    // byte-identical to the old single-line draw. Style (bold/color) is preserved by rebuilding each line
    // with the source component's style -- which also makes the wrap measurer bold-accurate.
    private static void drawCenteredWarning(GuiGraphicsExtractor ctx, Font tr, Component text, int y,
                                            int argbColor, boolean keyline, float scale) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        // Wrap against the scaled width so a >1.0 LETHAL line still fits (the whole block is drawn under a
        // matrix scale about the anchor, so the effective usable width shrinks by `scale`).
        int maxW = Math.max(1, (int) ((screenW - 8) / Math.max(0.01f, scale)));
        java.util.List<String> lines = com.example.globe.core.ui.OverlayLayout.wrap(
                text.getString(), maxW, s -> tr.width(Component.literal(s).setStyle(text.getStyle())));
        int lineH = tr.lineHeight + 1;
        int n = lines.size();

        boolean scaled = Math.abs(scale - 1.0f) > 1e-3f;
        var m = ctx.pose();
        if (scaled) {
            // Scale the whole warning block about its horizontal center and its bottom anchor `y`, so a larger
            // LETHAL line grows in place (stays centered, keeps its bottom edge) rather than drifting.
            float cx = screenW / 2.0f;
            m.pushMatrix();
            m.translate(cx, (float) y);
            m.scale(scale, scale);
            m.translate(-cx, (float) -y);
        }
        int keyArgb = ((argbColor >>> 24) << 24) | (POLE_KEYLINE_RGB & 0x00FFFFFF);
        for (int i = 0; i < n; i++) {
            Component lineC = Component.literal(lines.get(i)).setStyle(text.getStyle());
            int w = tr.width(lineC);
            int x = Math.max(4, (screenW - w) / 2);
            int lineY = Math.max(2, y - (n - 1 - i) * lineH);
            // TEST 76 SMEAR FIX (the real root cause). The keyline MUST be a PLAIN, STYLELESS silhouette --
            // NOT the styled fill component -- for two reasons proven against the 26.2 font renderer:
            //  1) COLOR: Font.getTextColor honors the passed color arg ONLY when the component has no style
            //     color; a component WITH one (the RED DANGER/LETHAL lines) uses its OWN color and keeps just
            //     the arg's ALPHA. So stamping the styled lineC with keyArgb drew a RED keyline around a RED
            //     fill -- a red-on-red halo with no dark backing at all (the "smear" Peetsa saw, worse once
            //     the fill's own drop shadow was removed). A styleless literal has no color, so the near-black
            //     keyArgb is finally honored and the outline is actually dark.
            //  2) BOLD: MC fakes bold by drawing every glyph TWICE, offset +1px (BakedSheetGlyph.renderChar).
            //     A bold keyline stamp is silently doubled, so the 8-way outline became 16 offset strokes --
            //     a lopsided, blurred halo that got worse the more we stamped. A styleless literal is non-bold.
            // Since the polar warnings are now non-bold at the FILL too, the plain keyline's glyph advances
            // match the fill exactly, so the outline stays registered on long/wrapped lines (a non-bold
            // keyline under a BOLD fill would drift ~1px/char). No shadow on the stamps -- they ARE the dark
            // backing; they fade with the text via the shared alpha.
            if (keyline) {
                Component keyC = Component.literal(lines.get(i));
                for (int[] off : com.example.globe.core.ui.TitleStyle.OUTLINE_OFFSETS_8) {
                    ctx.text(tr, keyC, x + off[0], lineY + off[1], keyArgb, false);
                }
            }
            // The keyline is the only dark backing, so draw the fill WITHOUT the vanilla drop shadow when a
            // keyline is present (a shadow would add a 9th offset dark stamp). The non-keyline path (EW storm
            // warnings) keeps its shadow, so it stays byte-identical to before.
            ctx.text(tr, lineC, x, lineY, argbColor, !keyline);
        }
        if (scaled) {
            m.popMatrix();
        }
    }

    private static String ewDangerDirection(net.minecraft.world.level.border.WorldBorder border, double playerX) {
        double distWest = Math.abs(playerX - border.getMinX());
        double distEast = Math.abs(border.getMaxX() - playerX);
        return distWest <= distEast ? "West" : "East";
    }

    private static void resetWorldEntryState(long worldTime) {
        debugStartWorldTime = worldTime;
        lastZoneKey = null;
        lastZoneUpdateWorldTime = Long.MIN_VALUE;
        lastZoneUpdateX = Integer.MIN_VALUE;
        lastZoneUpdateZ = Integer.MIN_VALUE;
        lastStableSideZ = 0;
        lastObservedZ = Double.NaN;
        lastRawZ = Double.NaN;
        lastHemiFireZMs = Long.MIN_VALUE;
        lastStableSideX = 0;
        lastObservedX = Double.NaN;
        lastRawX = Double.NaN;
        lastHemiFireXMs = Long.MIN_VALUE;
        hemiNegAnnouncedZ = false;
        hemiPosAnnouncedZ = false;
        hemiNegAnnouncedX = false;
        hemiPosAnnouncedX = false;
        zoneFullArmed = true;
        poleWarnHighestTier = 0;
        poleWarnText = null;
        poleWarnStartTick = Long.MIN_VALUE;
        poleWarnEndTick = Long.MIN_VALUE;
        poleVignetteTier = 0;
        poleVignetteStartMs = Long.MIN_VALUE;
        lastWarningDebugWorldTime = Long.MIN_VALUE;
        lastWarningDebugText = null;
        if (DEBUG_ENTRY_TITLES) {
            GlobeMod.LOGGER.info("[LAT][ENTRY_TITLE] action=reset worldTime={}", worldTime);
        }
    }

    // Fires the shared HEMISPHERE-TITLE channel on the two hemisphere crossings: N/S at the equator
    // (Z vs centerZ) and E/W at the prime meridian (X vs centerX). Both axes run the SAME pure debounce
    // (core.HemisphereCrossing: dead zone on the line, real-crossing requirement, teleport guard,
    // per-axis cooldown). Each fire sets its axis's line in the ONE HemisphereTitleOverlay slot, so a
    // diagonal 0deg,0deg crossing renders a single stacked N/S-over-E/W title -- never two competing
    // single-line titles. Gated on the same zoneEnterTitle* config as the zone title (the only
    // hemisphere-title gate that exists today), but its OWN channel/position, so it cannot slot-stomp
    // or overlap the zone-enter title.
    private static void maybeTriggerHemisphereTitles(Minecraft client, double playerX, double playerZ) {
        var border = client.level != null ? client.level.getWorldBorder() : null;
        long nowMs = System.currentTimeMillis();
        double centerZ = border != null ? border.getCenterZ() : 0.0;
        double centerX = border != null ? border.getCenterX() : 0.0;

        // 3-deg hysteresis band per axis, derived from the world radius (never a magic block count):
        // Z (N/S) spans 90 deg over the latitude radius, so 3 deg == latitudeRadius/30; X (E/W) spans
        // 180 deg over the X radius, so 3 deg == xRadius/60. (LatitudeMath.worldRadiusBlocks == halfSize.)
        // WHY floor (sweeper find): on xsmall Classic (radius 3750) bandX = 3750/60 = 62.5 < DEAD_ZONE 64,
        // so every resolved crossing already sits "outside the band" and instantly re-arms FULL -- the
        // SMALL path becomes unreachable on that axis. Floor both bands above the dead zone (+32 margin)
        // so the hysteresis gap always exists regardless of world size.
        double minBand = HemisphereCrossing.DEAD_ZONE_BLOCKS + 32.0;
        double bandZ = Math.max(com.example.globe.util.LatitudeMath.latitudeRadius(border) / 30.0, minBand);
        double bandX = Math.max(com.example.globe.util.LatitudeMath.worldRadiusBlocks(border) / 60.0, minBand);

        HemisphereCrossing.BandedResult rz = evalHemisphereAxis(
                playerZ, centerZ, lastObservedZ, lastRawZ, lastStableSideZ,
                hemiNegAnnouncedZ, hemiPosAnnouncedZ, bandZ, lastHemiFireZMs, nowMs);
        HemisphereCrossing.BandedResult rx = evalHemisphereAxis(
                playerX, centerX, lastObservedX, lastRawX, lastStableSideX,
                hemiNegAnnouncedX, hemiPosAnnouncedX, bandX, lastHemiFireXMs, nowMs);

        boolean enabled = LatitudeConfig.zoneEnterTitleEnabled;

        // N/S line: side -1 = North (z<center), +1 = South. Natural case; applyCase() at render controls
        // final casing (same convention as buildZoneEnterTitle()).
        if (enabled && rz.fire() != HemisphereCrossing.Fire.NONE) {
            String line = rz.newStableSide() < 0 ? "Northern Hemisphere" : "Southern Hemisphere";
            if (rz.fire() == HemisphereCrossing.Fire.FULL) {
                fireHemisphereLine(false, line);
                logEntryTitle("hemisphere_trigger_ns", line, client, playerX, playerZ);
            } else {
                showActionBarMessage(client, line);
                logEntryTitle("hemisphere_actionbar_ns", line, client, playerX, playerZ);
            }
            lastHemiFireZMs = nowMs;
        }
        // E/W line: side -1 = West (x<center), +1 = East -- matches LatitudeMath.hemisphereEW.
        if (enabled && rx.fire() != HemisphereCrossing.Fire.NONE) {
            String line = rx.newStableSide() < 0 ? "Western Hemisphere" : "Eastern Hemisphere";
            if (rx.fire() == HemisphereCrossing.Fire.FULL) {
                fireHemisphereLine(true, line);
                logEntryTitle("hemisphere_trigger_ew", line, client, playerX, playerZ);
            } else {
                showActionBarMessage(client, line);
                logEntryTitle("hemisphere_actionbar_ew", line, client, playerX, playerZ);
            }
            lastHemiFireXMs = nowMs;
        }

        // Advance tracking state every tick (even when disabled) so re-enabling can never replay a
        // crossing that already happened -- the sample simply moves past center unremarked.
        lastStableSideZ = rz.newStableSide();
        lastObservedZ = rz.newObserved();
        lastRawZ = rz.newRawObserved();
        lastStableSideX = rx.newStableSide();
        lastObservedX = rx.newObserved();
        lastRawX = rx.newRawObserved();
        hemiNegAnnouncedZ = rz.nextNegSideAnnounced();
        hemiPosAnnouncedZ = rz.nextPosSideAnnounced();
        hemiNegAnnouncedX = rx.nextNegSideAnnounced();
        hemiPosAnnouncedX = rx.nextPosSideAnnounced();

        if (!enabled) {
            logEntryTitle("hemisphere_disabled", "", client, playerX, playerZ);
        }
    }

    private static HemisphereCrossing.BandedResult evalHemisphereAxis(double coord, double center,
                                                                      double observed, double raw, int side,
                                                                      boolean negAnnounced, boolean posAnnounced,
                                                                      double band, long fireMs, long nowMs) {
        return HemisphereCrossing.evaluateBanded(coord, center, observed, raw, side,
                negAnnounced, posAnnounced, nowMs, fireMs,
                HemisphereCrossing.DEAD_ZONE_BLOCKS, band, HemisphereCrossing.MAX_STEP_BLOCKS,
                HemisphereCrossing.COOLDOWN_MS);
    }

    private static void fireHemisphereLine(boolean eastWestAxis, String line) {
        int durationTicks = (int) Math.round(clamp(LatitudeConfig.zoneEnterTitleSeconds, 2.0, 10.0) * 20.0);
        double scale = clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);
        HemisphereTitleOverlay.trigger(eastWestAxis, line, durationTicks, scale);
    }

    // B-4 anti-spam: a re-announcement while still lingering near the boundary just crossed (a hemisphere
    // line OR a climate-band edge) shows only a small, unobtrusive WHISPER instead of re-firing the big
    // center-screen title. B-4 round 3 item 5 swapped the renderer from vanilla's stark full-opacity
    // action-bar (Hud.setOverlayMessage) to LatitudeWhisperOverlay -- a translucent, italic, fade-in/hold/
    // fade-out line. The trigger sites (the linger branches above) are unchanged; only the renderer moved.
    private static void showActionBarMessage(Minecraft client, String line) {
        LatitudeWhisperOverlay.trigger(line);
    }

    private static void logEntryTitle(String action, String title, Minecraft client, double playerX, double playerZ) {
        if (!DEBUG_ENTRY_TITLES || client == null || client.level == null || client.player == null) {
            return;
        }
        var border = client.level.getWorldBorder();
        double latDeg = com.example.globe.util.LatitudeMath.degreesFromZ(border, playerZ);
        int lonDeg = com.example.globe.util.LatitudeMath.longitudeDegrees(border, playerX);
        GlobeMod.LOGGER.info("[LAT][ENTRY_TITLE] action={} title=\"{}\" x={} z={} centerX={} centerZ={} worldTime={} latDeg={} lonDeg={} sideZ={} sideX={} observedZ={} observedX={}",
                action,
                title,
                playerX,
                playerZ,
                border.getCenterX(),
                border.getCenterZ(),
                client.level.getGameTime(),
                latDeg,
                lonDeg,
                lastStableSideZ,
                lastStableSideX,
                Double.isNaN(lastObservedZ) ? "nan" : Double.toString(lastObservedZ),
                Double.isNaN(lastObservedX) ? "nan" : Double.toString(lastObservedX));
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

    /**
     * Dev/test convenience (fired by {@code /latdev title}): shows the real big zone-enter title overlay for
     * the zone the player is CURRENTLY standing in, on demand, without walking across a zone boundary.
     *
     * <p>This DELIBERATELY bypasses the {@link com.example.globe.core.ZoneTitleBanding} anti-spam / hysteresis
     * that governs real gameplay crossings -- an explicit request to see the title should always show the FULL
     * big title, never the quiet action-bar fallback. It uses the SAME duration/scale derivation as the real
     * crossing-trigger site ({@code render()}), so the preview matches what a real crossing would show. It does
     * NOT touch {@code lastZoneKey} / {@code zoneFullArmed} (the real crossing-detection state machine is left
     * completely alone), so this command has no side effect on subsequent real crossings.
     *
     * @return the rendered title text (for command feedback), or {@code null} if player/level are unavailable.
     */
    public static String debugFireZoneTitleNow(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return null;
        }
        var border = client.level.getWorldBorder();
        String canonicalZoneKey = canonicalTitleZoneKey(border, client.player.getZ());
        String titleText = buildZoneEnterTitle(client, canonicalZoneKey);
        int durationTicks = (int) Math.round(clamp(LatitudeConfig.zoneEnterTitleSeconds, 2.0, 10.0) * 20.0);
        double scale = clamp(LatitudeConfig.zoneEnterTitleScale, 1.0, 3.0);
        logEntryTitle("zone_debug_command", titleText, client, client.player.getX(), client.player.getZ());
        ZoneEnterTitleOverlay.trigger(titleText, durationTicks, scale);
        return titleText;
    }

    private static String buildZoneEnterTitle(Minecraft client, String canonicalZoneKey) {
        // Natural case -- ZoneEnterTitleOverlay's applyCase() applies the player's chosen title-case option
        // (Normal/UPPERCASE/lowercase/Mocking) at render time, so forcing uppercase here would make "Normal"
        // indistinguishable from "UPPERCASE".
        String zoneName = zoneDisplayName(canonicalZoneKey);
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
