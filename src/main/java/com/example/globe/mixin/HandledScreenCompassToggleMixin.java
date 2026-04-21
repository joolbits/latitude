package com.example.globe.mixin;

import com.example.globe.client.CompassHudConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenCompassToggleMixin {
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected abstract Slot getSlotAt(double x, double y);

    @Inject(method = "mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z", at = @At("HEAD"), cancellable = true)
    private void globe$altClickToggleCompassHud(MouseButtonEvent click, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if (click == null) return;
        if (click.button() != 0) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return;

        boolean altDown = InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_LALT)
                || InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_RALT);
        if (!altDown) return;

        Slot slot = this.getSlotAt(click.x(), click.y());
        if (slot == null) return;

        ItemStack stack = slot.getItem();
        if (stack == null || stack.isEmpty()) return;

        if (!(stack.is(Items.COMPASS) || containsCompass(stack, 0))) return;

        var cfg = CompassHudConfig.get();
        cfg.enabled = !cfg.enabled;
        CompassHudConfig.saveCurrent();
        cir.setReturnValue(true);
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void globe$drawCompassDisabledX(GuiGraphics ctx, Slot slot, int i, int j, CallbackInfo ci) {
        if (CompassHudConfig.get().enabled) return;
        if (slot == null) return;

        ItemStack stack = slot.getItem();
        if (stack == null || stack.isEmpty()) return;

        if (!(stack.is(Items.COMPASS) || containsCompass(stack, 0))) return;

        int iconX = this.x + slot.x;
        int iconY = this.y + slot.y;

        for (int k = 0; k < 5; k++) {
            int px1 = iconX + 1 + k;
            int py1 = iconY + 1 + k;
            ctx.fill(px1, py1, px1 + 1, py1 + 1, 0xFFFF0000);

            int px2 = iconX + 1 + (4 - k);
            int py2 = iconY + 1 + k;
            ctx.fill(px2, py2, px2 + 1, py2 + 1, 0xFFFF0000);
        }
    }

    private static boolean containsCompass(ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.COMPASS)) return true;

        if (depth >= 6) return false;

        if (stack.is(Items.BUNDLE)) {
            BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (contents != null) {
                for (ItemStack inside : contents.items()) {
                    if (containsCompass(inside, depth + 1)) return true;
                }
            }
        }

        return false;
    }
}
