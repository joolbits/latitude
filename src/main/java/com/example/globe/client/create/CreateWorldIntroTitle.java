package com.example.globe.client.create;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws the "L A T I T U D E / New World" fade title that opens the bespoke create-world screen in
 * tabbedMode. {@link CreateWorldIntroClock} owns the timing; this class owns only the pixels, so
 * any surface that paints this title stays pixel-identical to every other.
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
    public static void render(GuiGraphicsExtractor context, Font font, int width, int height) {
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
        matrices.pushMatrix();
        matrices.translate((float) (cx - titleWidth / 2), (float) startY);
        matrices.scale(SCALE, SCALE);
        context.text(font, TEXT, 0, 0, goldA, true);
        matrices.popMatrix();

        int subtitleWidth = font.width(SUBTITLE);
        int subtitleY = startY + titleHeight + SUBTITLE_GAP;
        context.text(font, SUBTITLE, cx - subtitleWidth / 2, subtitleY, warmA, true);
    }
}
