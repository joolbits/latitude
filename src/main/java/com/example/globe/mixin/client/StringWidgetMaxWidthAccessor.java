package com.example.globe.mixin.client;

import net.minecraft.client.gui.components.StringWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes a text widget's ALLOTTED width -- the cap vanilla clips against -- which is otherwise
 * unreachable.
 *
 * <p>{@code StringWidget.getWidth()} is not a substitute and cannot be made one: its body is
 * {@code Math.min(maxWidth, font.width(text))}, so for any text shorter than the row it reports the
 * text's own width. Anything reasoning about "the row's right margin" from {@code getWidth()}
 * therefore resolves to a point just past the visible text instead, which is precisely how an
 * earlier right-aligned overlay ended up drawn on top of vanilla's own text.</p>
 */
@Mixin(StringWidget.class)
public interface StringWidgetMaxWidthAccessor {
    @Accessor("maxWidth")
    int globe$getMaxWidth();
}
