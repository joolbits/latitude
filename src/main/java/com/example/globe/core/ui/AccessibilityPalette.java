package com.example.globe.core.ui;

import com.example.globe.core.config.LatitudeConfigData.AccessibilityMode;

/**
 * Pure, Minecraft-free color/opacity math that turns the player's {@link AccessibilityMode} choice into
 * concrete rendering decisions. Every HUD/menu surface (compass, world-creation screen, rule icons) routes
 * its configured colors and alphas through these helpers each frame, so flipping the Accessibility dropdown
 * visibly changes the whole UI without any per-surface special-casing.
 *
 * <p>The three modes honor the spec in {@code LatitudeConfigData.AccessibilityMode}:
 * <ul>
 *   <li><b>STANDARD</b> — every function here is the identity; the look is exactly today's.</li>
 *   <li><b>HIGH_CONTRAST</b> — legibility over aesthetics: text alpha is floored to fully opaque, panel
 *       backgrounds are floored to a near-solid alpha (no barely-there see-through), dim/muted tones are
 *       lifted to a minimum luminance so they never disappear against terrain, and signal colors are pushed
 *       to full brightness. A non-zero {@link #outlineStrength} tells surfaces to paint a dark backing plate
 *       behind glyphs and icons.</li>
 *   <li><b>COLORBLIND_FRIENDLY</b> — meaning never rides on a red-vs-green pairing. Red-reliant signals are
 *       remapped to an unambiguous blue / gold / white palette ({@link #adjustSignalColor}); thematic and
 *       decorative colors that already read safely (cyan, emerald, gold themes, parchment) are left alone.</li>
 * </ul>
 *
 * <p>All brightness lifts are implemented as luminance <i>floors</i> (raise up to a minimum, never scale a
 * value that is already bright), which makes every function idempotent: applying it twice yields the same
 * result as once, so callers can route colors through it at more than one layer without compounding.
 */
public final class AccessibilityPalette {

    private AccessibilityPalette() {}

    /** What a colored element MEANS, so {@link #adjustSignalColor} can remap only the color-coded signals
     *  (and only the red/green-reliant ones) while leaving thematic/decorative colors untouched. */
    public enum SignalRole {
        /** The compass north pointer — traditionally red, the classic colorblind trap. */
        NEEDLE_NORTH,
        /** A danger/hazard color (traditionally red). */
        WARNING,
        /** An "on / good / go" color (traditionally green). */
        POSITIVE,
        /** An "off / bad / stop" color (traditionally red). */
        NEGATIVE,
        /** A color that carries no red/green meaning; only brightened under HIGH_CONTRAST. */
        NEUTRAL
    }

    // Luminance floors (perceived, Rec. 601). Text needs to be brighter than chrome to read cleanly.
    private static final int TEXT_LUM_FLOOR = 190;
    private static final int MUTED_LUM_FLOOR = 170;
    private static final int SIGNAL_LUM_FLOOR = 200;

    // Opaque floors for HIGH_CONTRAST.
    private static final int TEXT_ALPHA_FLOOR = 255;
    private static final int BG_ALPHA_FLOOR = 232;

    // Colorblind-safe replacements (all bright, all distinguishable across the common CVDs).
    private static final int SAFE_NORTH = 0xFF4FD8FF;   // bright cyan — the north pop, distinct from gold rims
    private static final int SAFE_WARNING = 0xFFFFB000;  // amber/gold instead of hazard red
    private static final int SAFE_POSITIVE = 0xFFE8B64A; // gold "on" instead of green
    private static final int SAFE_NEGATIVE = 0xFF9A9A9A; // neutral grey "off" instead of red

    private static boolean isHighContrast(AccessibilityMode mode) {
        return mode == AccessibilityMode.HIGH_CONTRAST;
    }

    private static boolean isColorblind(AccessibilityMode mode) {
        return mode == AccessibilityMode.COLORBLIND_FRIENDLY;
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int alpha(int argb) { return (argb >>> 24) & 0xFF; }
    private static int red(int argb)   { return (argb >>> 16) & 0xFF; }
    private static int green(int argb) { return (argb >>> 8) & 0xFF; }
    private static int blue(int argb)  { return argb & 0xFF; }

    private static int pack(int a, int r, int g, int b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    /** Perceived luminance of the RGB channels (ignores alpha), Rec. 601 weights. */
    static int luminance(int argb) {
        return Math.round(0.299f * red(argb) + 0.587f * green(argb) + 0.114f * blue(argb));
    }

    /** True when a color's meaning leans on RED — R clearly dominates G and B. Cyan/emerald/gold theme
     *  needles are NOT red-reliant, so they pass through {@link #adjustSignalColor} unchanged. */
    static boolean isRedReliant(int argb) {
        int r = red(argb), g = green(argb), b = blue(argb);
        return r >= 120 && r > g * 13 / 10 && r > b * 13 / 10;
    }

    /** Raise a color's luminance to at least {@code floorLum} by blending toward white. Because luminance
     *  is a linear combination of the channels and white has luminance 255, the blend factor
     *  {@code t = (floor - lum) / (255 - lum)} always reaches the target exactly (unlike uniform channel
     *  scaling, which stalls once a channel clips). The white portion is rounded up so integer rounding can
     *  only ever meet-or-exceed the floor, never fall a hair short. Already-bright colors are returned
     *  unchanged, so this is idempotent. Alpha is preserved. */
    static int liftLuminanceTo(int argb, int floorLum) {
        int lum = luminance(argb);
        if (lum >= floorLum) return argb;
        int target = Math.min(255, floorLum);
        float t = (target - lum) / (float) (255 - lum);
        return pack(alpha(argb),
                red(argb) + (int) Math.ceil(t * (255 - red(argb))),
                green(argb) + (int) Math.ceil(t * (255 - green(argb))),
                blue(argb) + (int) Math.ceil(t * (255 - blue(argb))));
    }

    // ── Alpha floors ─────────────────────────────────────────────────────────────────────────────────

    /** Text opacity byte to actually use. HIGH_CONTRAST ignores the player's slider and forces fully
     *  opaque; other modes return the configured value untouched. */
    public static int textAlpha(AccessibilityMode mode, int configured) {
        return isHighContrast(mode) ? TEXT_ALPHA_FLOOR : clamp255(configured);
    }

    /** Panel/background opacity byte to actually use. HIGH_CONTRAST floors it to near-solid so nothing is
     *  barely-there; other modes return the configured value untouched. */
    public static int backgroundAlpha(AccessibilityMode mode, int configured) {
        return isHighContrast(mode) ? Math.max(clamp255(configured), BG_ALPHA_FLOOR) : clamp255(configured);
    }

    /** Alpha (0..255) for a dark backing plate / outline behind glyphs and icons; 0 means "draw none".
     *  Non-zero only under HIGH_CONTRAST, which is the signal for surfaces to add a solid backing. */
    public static int outlineStrength(AccessibilityMode mode) {
        return isHighContrast(mode) ? 210 : 0;
    }

    /** Convenience for panel/HUD body text: floors alpha to opaque AND lifts dim greys to a legible
     *  minimum luminance under HIGH_CONTRAST. Identity for STANDARD and COLORBLIND_FRIENDLY (words already
     *  carry their own meaning; text color is not a color-only signal). Idempotent. */
    public static int adjustPanelText(AccessibilityMode mode, int argb) {
        if (!isHighContrast(mode)) return argb;
        int lifted = liftLuminanceTo(argb, TEXT_LUM_FLOOR);
        return pack(TEXT_ALPHA_FLOOR, red(lifted), green(lifted), blue(lifted));
    }

    /** Convenience for muted/secondary chrome (dial ticks, subtitles, frame hairlines): a gentler
     *  luminance floor than body text, alpha preserved. Identity outside HIGH_CONTRAST. Idempotent. */
    public static int adjustMuted(AccessibilityMode mode, int argb) {
        return isHighContrast(mode) ? liftLuminanceTo(argb, MUTED_LUM_FLOOR) : argb;
    }

    /** Clamp a decorative animated color (e.g. the Aurora/rainbow cycle) so its luminance never dips below
     *  {@code minLum} — keeps the flow going while guaranteeing text/needles stay readable in HIGH_CONTRAST.
     *  Identity outside HIGH_CONTRAST, so Aurora still dips to its full range normally. Idempotent. */
    public static int clampDecorativeBrightness(AccessibilityMode mode, int argb, int minLum) {
        return isHighContrast(mode) ? liftLuminanceTo(argb, minLum) : argb;
    }

    // ── Signal remap ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Remap a color-coded status signal for the current mode.
     * <ul>
     *   <li>STANDARD — identity.</li>
     *   <li>HIGH_CONTRAST — keep the hue but force full opacity and lift to a bright, punchy luminance.</li>
     *   <li>COLORBLIND_FRIENDLY — replace red/green-reliant signals with a blue/gold/white-safe color
     *       ({@link SignalRole} decides which). A NEEDLE_NORTH/WARNING/NEGATIVE that is <i>not</i> actually
     *       red-reliant (e.g. a cyan-theme needle) is left as-is so a theme's aesthetic survives; POSITIVE
     *       always remaps off green.</li>
     * </ul>
     * Alpha of the incoming color is preserved for the HIGH_CONTRAST and identity paths; the safe
     * replacements are fully opaque by design.
     */
    public static int adjustSignalColor(AccessibilityMode mode, int argb, SignalRole role) {
        if (isColorblind(mode)) {
            switch (role) {
                case NEEDLE_NORTH:
                    return isRedReliant(argb) ? SAFE_NORTH : argb;
                case WARNING:
                    return isRedReliant(argb) ? SAFE_WARNING : argb;
                case NEGATIVE:
                    return isRedReliant(argb) ? SAFE_NEGATIVE : argb;
                case POSITIVE:
                    return SAFE_POSITIVE;
                case NEUTRAL:
                default:
                    return argb;
            }
        }
        if (isHighContrast(mode)) {
            int lifted = liftLuminanceTo(argb, SIGNAL_LUM_FLOOR);
            return pack(255, red(lifted), green(lifted), blue(lifted));
        }
        return argb;
    }
}
