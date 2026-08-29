package com.example.globe.mixin.client;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches {@code Screen.addRenderableWidget}, which is protected and therefore uncallable from this
 * package.
 *
 * <p>An {@code @Shadow} on the subclass does NOT work and is not a style preference: the method is
 * declared on {@code Screen}, so shadowing it from a {@code CreateWorldScreen} mixin fails at APPLY
 * with "was not located in the target class" and takes the whole class-load down -- which shipped as
 * a frozen world list in TEST 11, because {@code SelectWorldScreen.init} loads
 * {@code CreateWorldScreen} and inherited the failure. {@code @Invoker} on the DECLARING class is
 * the working form. {@code tools/verify_mixin_targets.py} now fails the build on the broken shape.</p>
 */
@Mixin(Screen.class)
public interface ScreenAddRenderableWidgetInvoker {
    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T globe$addRenderableWidget(T widget);
}
