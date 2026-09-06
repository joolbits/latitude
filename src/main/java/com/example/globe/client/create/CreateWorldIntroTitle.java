package com.example.globe.client.create;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the "L A T I T U D E / New World" fade title that opens the bespoke create-world screen in
 * tabbedMode. {@link CreateWorldIntroClock} owns the timing; this class owns only the pixels, so
 * the two screens that paint this title cannot drift apart.
 *
 * <p>Vanilla shows its own "Preparing for world creation" {@code GenericMessageScreen} while
 * {@code CreateWorldScreen.show()} loads datapacks, before {@code LatitudeCreateWorldScreen} ever
 * exists to intercept anything. This title's clock used to start fresh in that bespoke screen's
 * {@code init()}, so the vanilla text always got to flash first, no matter how long the load took
 * (maintainer report, 2026-08-10: "the vanilla message steals time from the Latitude title"). A
 * mixin on {@code GenericMessageScreen} now paints this same title over that wait instead, sharing
 * the clock, so the title fades in immediately and its time on screen is the intended duration
 * rather than an unpredictable load plus the animation.
 */
public final class CreateWorldIntroTitle {
    private CreateWorldIntroTitle() {
    }

    private static final int GOLD = 0xFFD4A74A;
    private static final int WARM_WHITE = 0xFFEDE0D0;
    private static final String TEXT = "L A T I T U D E";
    private static final String SUBTITLE = "New World";
    private static final float SCALE = 13.0f / 9.0f;
    private static final int SUBTITLE_GAP = 10;

    /** Draws the centered, alpha-faded title/subtitle pair into a width x height viewport, at
     *  whatever alpha the shared clock currently reads. No-op once that alpha reaches zero, so
     *  callers can invoke this unconditionally every frame. */
    public static void render(GuiGraphics context, Font font, int width, int height) {
        float alpha = CreateWorldIntroClock.alpha();
        if (alpha <= 0f) {
            return;
        }
        int a = Math.round(alpha * 255f) << 24;
        int goldA = (GOLD & 0x00FFFFFF) | a;
        int warmA = (WARM_WHITE & 0x00FFFFFF) | a;

        int titleWidth = Math.round(font.width(TEXT) * SCALE);
        int titleHeight = Math.round(font.lineHeight * SCALE);
        int blockHeight = titleHeight + SUBTITLE_GAP + font.lineHeight;
        int startY = (height - blockHeight) / 2;
        int cx = width / 2;

        var matrices = context.pose();
        matrices.pushPose();
        matrices.translate((float) (cx - titleWidth / 2), (float) startY, 0.0F);
        matrices.scale(SCALE, SCALE, 1.0F);
        context.drawString(font, TEXT, 0, 0, goldA, true);
        matrices.popPose();

        int subtitleWidth = font.width(SUBTITLE);
        int subtitleY = startY + titleHeight + SUBTITLE_GAP;
        context.drawString(font, SUBTITLE, cx - subtitleWidth / 2, subtitleY, warmA, true);
    }
}
