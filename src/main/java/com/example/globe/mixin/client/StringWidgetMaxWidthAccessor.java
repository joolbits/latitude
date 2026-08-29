package com.example.globe.mixin.client;

import net.minecraft.client.gui.components.StringWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the row allotment a {@link StringWidget} was built with.
 *
 * <p>{@code getWidth()} cannot serve here, and the reason is worth stating because it defeated an
 * earlier attempt at this: with a max width set, that method returns
 * {@code min(maxWidth, font.width(text))}. For text that already overflows it happens to equal the
 * allotment, but for text that fits it returns the SHORT actual-text width — so it reports the row's
 * true right margin only in the case where the answer is not needed. The field itself is the only
 * honest source.</p>
 */
@Mixin(StringWidget.class)
public interface StringWidgetMaxWidthAccessor {
    @Accessor("maxWidth")
    int globe$getMaxWidth();
}
