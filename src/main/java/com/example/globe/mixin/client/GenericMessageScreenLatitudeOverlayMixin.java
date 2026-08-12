package com.example.globe.mixin.client;

import com.example.globe.client.create.CreateWorldIntroClock;
import com.example.globe.client.create.CreateWorldIntroTitle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's short Create World preparation message with the Latitude title while its
 * datapacks load. Other generic message screens retain their vanilla text and behaviour.
 */
@Mixin(GenericMessageScreen.class)
public abstract class GenericMessageScreenLatitudeOverlayMixin {
    @Unique
    private static final String CREATE_WORLD_PREPARING_KEY = "createWorld.preparing";

    @Shadow
    private FocusableTextWidget textWidget;

    @Inject(method = "init", at = @At("TAIL"))
    private void globe$hideCreateWorldPreparingText(CallbackInfo ci) {
        if (globe$isCreateWorldPreparing() && textWidget != null) {
            textWidget.visible = false;
        }
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void globe$renderCreateWorldTitle(GuiGraphicsExtractor context, int mouseX, int mouseY,
                                               float delta, CallbackInfo ci) {
        if (!globe$isCreateWorldPreparing()) {
            return;
        }
        long now = Util.getMillis();
        CreateWorldIntroClock.beginForOwner(this, now);
        CreateWorldIntroClock.advance(now);
        GenericMessageScreen screen = (GenericMessageScreen) (Object) this;
        CreateWorldIntroTitle.render(context, Minecraft.getInstance().font, screen.width, screen.height);
    }

    @Unique
    private boolean globe$isCreateWorldPreparing() {
        GenericMessageScreen screen = (GenericMessageScreen) (Object) this;
        return screen.getTitle().getContents() instanceof TranslatableContents translatable
                && CREATE_WORLD_PREPARING_KEY.equals(translatable.getKey());
    }
}
