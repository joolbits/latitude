package com.example.globe.mixin.client;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code Screen.addRenderableWidget}, declared on {@code Screen} and {@code protected}, to
 * a mixin targeting a subclass that never overrides it.
 *
 * <p>The method exists here rather than as a {@code @Shadow} on the subclass mixin because
 * {@code @Shadow} does not resolve through the inheritance chain: shadowing a member the target
 * class merely INHERITS compiles clean, passes every static check, and then fails at mixin APPLY
 * time, silently killing the class load. That is not a crash a player can report -- the screen that
 * would have shown simply never appears. This is the fix that closed exactly that failure after it
 * froze a live client on the world list.</p>
 */
@Mixin(Screen.class)
public interface ScreenAddRenderableWidgetInvoker {
    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T globe$addRenderableWidget(T widget);
}
