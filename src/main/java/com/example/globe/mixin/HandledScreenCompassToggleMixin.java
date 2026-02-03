package com.example.globe.mixin;

import com.example.globe.client.CompassHudConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenCompassToggleMixin {
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected abstract Slot getSlotAt(double x, double y);

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void globe$altClickToggleCompassHud(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;

        boolean altDown = InputUtil.isKeyPressed(client.getWindow().getHandle(), InputUtil.GLFW_KEY_LEFT_ALT)
                || InputUtil.isKeyPressed(client.getWindow().getHandle(), InputUtil.GLFW_KEY_RIGHT_ALT);
        if (!altDown) return;

        Slot slot = this.getSlotAt(mouseX, mouseY);
        if (slot == null) return;

        ItemStack stack = slot.getStack();
        if (stack == null || stack.isEmpty()) return;

        if (!(stack.isOf(Items.COMPASS) || containsCompass(stack, 0))) return;

        var cfg = CompassHudConfig.get();
        cfg.enabled = !cfg.enabled;
        CompassHudConfig.saveCurrent();
        cir.setReturnValue(true);
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void globe$drawCompassDisabledX(DrawContext ctx, Slot slot, int i, int j, CallbackInfo ci) {
        if (CompassHudConfig.get().enabled) return;
        if (slot == null) return;

        ItemStack stack = slot.getStack();
        if (stack == null || stack.isEmpty()) return;

        if (!(stack.isOf(Items.COMPASS) || containsCompass(stack, 0))) return;

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
        if (stack.isOf(Items.COMPASS)) return true;

        if (depth >= 6) return false;

        if (stack.isOf(Items.BUNDLE)) {
            BundleContentsComponent contents = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
            if (contents != null) {
                for (ItemStack inside : contents.iterate()) {
                    if (containsCompass(inside, depth + 1)) return true;
                }
            }
        }

        return false;
    }
}
