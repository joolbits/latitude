package com.example.globe.client.create;

/**
 * Narrows vanilla's own two-button create-world footer to fit a third button, without changing the
 * row's overall footprint.
 *
 * <p>Vanilla's footer is {@code LinearLayout.horizontal().spacing(8)} holding two buttons at the
 * builder default width of 150 -- an envelope of {@code 150 + 8 + 150 = 308}. Adding a third button
 * at that same 150 width would need {@code 3*150 + 2*8 = 466}, which overflows any ordinary window.
 * Narrowing all three buttons to share vanilla's existing 308 envelope keeps the row's footprint
 * identical to vanilla's own.</p>
 *
 * <p><b>320 is not a floor.</b> An earlier version of this note claimed Minecraft's window "floors
 * its GUI scale so the scaled width never drops below 320", and both the escape hatch and the
 * world-list door were proved correct against that claim. It is false, and the claim being wrong
 * cost two live failures and three separate wrong explanations of the same crash. In
 * {@code Window.calculateScale}, the {@code >= 320} test sits INSIDE the guard on incrementing the
 * scale, so it only ever stops the scale climbing. A window already narrower than 320 never enters
 * the loop at all: the scale stays 1 and the scaled width is simply the window width. So callers
 * must clamp against the actual screen width rather than assume a minimum -- see
 * {@link #fittedEnvelope(int)}. Pinned as an executable check, not a comment, in
 * {@code VanillaFooterLayoutPolicyTest}: a premise stated only in prose is never consumed by
 * anything, so nothing can falsify it.</p>
 */
public final class VanillaFooterLayoutPolicy {
    public static final int SPACING = 8;
    public static final int VANILLA_ROW_WIDTH = 308;

    /** Gap kept between the row and each screen edge when the screen is too narrow for the row. */
    public static final int SCREEN_MARGIN = 4;

    /**
     * The envelope to actually lay out in, given the screen's current width: vanilla's own row
     * width, or as much of it as fits between the margins when the screen is narrower than that.
     *
     * <p>Clamping against {@code screenWidth} is safe in a way that clamping against the buttons
     * would not be: the screen width is an input this code never writes, so it cannot become a
     * function of a previous pass's output.</p>
     */
    public static int fittedEnvelope(int desiredEnvelope, int screenWidth) {
        return Math.min(desiredEnvelope, screenWidth - 2 * SCREEN_MARGIN);
    }

    /** As {@link #fittedEnvelope(int, int)}, for a row that wants vanilla's own full width. */
    public static int fittedEnvelope(int screenWidth) {
        return fittedEnvelope(VANILLA_ROW_WIDTH, screenWidth);
    }

    private VanillaFooterLayoutPolicy() {
    }

    /**
     * Width for each of {@code count} equal buttons sharing {@code envelope} total width. Rounds
     * down deliberately: {@code count * width + (count - 1) * SPACING} must never exceed
     * {@code envelope}, so the row can never grow past vanilla's own footprint.
     */
    public static int buttonWidth(int count, int envelope) {
        if (count <= 0) {
            throw new IllegalArgumentException("button count must be positive");
        }
        return Math.max(1, (envelope - (count - 1) * SPACING) / count);
    }

    /** Left edge of button {@code index} (0-based) in a {@code count}-button row centred on {@code centreX}. */
    public static int buttonX(int centreX, int count, int width, int index) {
        int rowWidth = count * width + (count - 1) * SPACING;
        return (centreX - rowWidth / 2) + index * (width + SPACING);
    }

    /**
     * Left edge of a {@code count}-button row centred on the screen but never pushed off it.
     *
     * <p>The clamp is the whole point. Centring alone yields a negative left edge whenever the row
     * is wider than the screen, and that is reachable -- see the class note on why 320 is not a
     * floor. A negative left edge is not merely ugly: a narrowed button whose label no longer fits
     * enables a scissor, and the renderer rejects a scissor at a negative x outright.</p>
     */
    public static int rowLeft(int count, int width, int screenWidth) {
        int rowWidth = count * width + (count - 1) * SPACING;
        return Math.max(SCREEN_MARGIN, (screenWidth - rowWidth) / 2);
    }

    /** Left edge of button {@code index} (0-based) in a row whose own left edge is {@code left}. */
    public static int buttonXFrom(int left, int width, int index) {
        return left + index * (width + SPACING);
    }
}
