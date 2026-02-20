package com.example.globe.mixin.client;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("guiRenderer")
    GuiRenderer globe$getGuiRenderer();

    @Accessor("guiState")
    GuiRenderState globe$getGuiState();
}
