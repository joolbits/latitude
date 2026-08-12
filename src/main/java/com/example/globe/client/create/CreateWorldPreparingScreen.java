package com.example.globe.client.create;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Quiet loading surface shown while the create-world datapacks are prepared. It replaces vanilla's
 * visible preparation message with the same title and clock used by the completed Latitude screen.
 */
final class CreateWorldPreparingScreen extends GenericMessageScreen {
    private boolean introClockClaimed;

    CreateWorldPreparingScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        super.init();
        if (!introClockClaimed) {
            introClockClaimed = true;
            CreateWorldIntroClock.beginForOwner(this, Util.getMillis());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        CreateWorldIntroClock.advance(Util.getMillis());
        super.extractRenderState(context, mouseX, mouseY, delta);
        CreateWorldIntroTitle.render(context, this.font, this.width, this.height);
    }
}
