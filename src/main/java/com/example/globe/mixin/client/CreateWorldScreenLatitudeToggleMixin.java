package com.example.globe.mixin.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenLatitudeToggleMixin extends Screen {
    @Unique
    private static final Identifier LATITUDE_WORLD_PRESET_ID = Identifier.fromNamespaceAndPath("globe", "globe");

    @Unique
    private static final Identifier DEFAULT_WORLD_PRESET_ID = Identifier.fromNamespaceAndPath("minecraft", "normal");

    @Shadow
    @Final
    private WorldCreationUiState worldCreator;

    @Unique
    private boolean latitude$enabled = true;

    @Unique
    private @Nullable Button latitude$toggleBtn;

    @Unique
    private @Nullable ResourceKey<WorldPreset> latitude$previousPreset;

    @Unique
    private @Nullable AbstractWidget latitude$worldTypeWidget;

    @Unique
    private boolean latitude$didInitDefaults;

    protected CreateWorldScreenLatitudeToggleMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void latitude$initToggle(CallbackInfo ci) {
        this.latitude$toggleBtn = null;
        this.latitude$previousPreset = null;
        this.latitude$worldTypeWidget = latitude$findWorldTypeWidget((CreateWorldScreen) (Object) this);

        WorldCreationUiState.WorldTypeEntry type = this.worldCreator.getWorldType();
        Holder<WorldPreset> preset = type != null ? type.preset() : null;
        ResourceKey<WorldPreset> currentKey = preset != null ? preset.unwrapKey().orElse(null) : null;
        Identifier currentId = currentKey != null ? currentKey.identifier() : null;

        if (!this.latitude$didInitDefaults) {
            this.latitude$didInitDefaults = true;
            if (currentId == null || !currentId.getNamespace().equals("globe")) {
                if (currentKey != null) {
                    this.latitude$previousPreset = currentKey;
                }
                latitude$setWorldPreset(LATITUDE_WORLD_PRESET_ID);
            }
        }

        latitude$syncFromSelectedPreset();

        int x;
        int y;
        int w;
        int h;
        if (this.latitude$worldTypeWidget != null) {
            x = this.latitude$worldTypeWidget.getX();
            y = this.latitude$worldTypeWidget.getY() - (this.latitude$worldTypeWidget.getHeight() + 4);
            w = this.latitude$worldTypeWidget.getWidth();
            h = this.latitude$worldTypeWidget.getHeight();
        } else {
            x = this.width / 2 - 100;
            y = 48;
            w = 200;
            h = 20;
        }

        this.latitude$toggleBtn = Button.builder(latitude$toggleLabel(), b -> latitude$toggle())
                .bounds(x, y, w, h)
                .build();
        this.addRenderableWidget(this.latitude$toggleBtn);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"))
    private void latitude$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        latitude$syncFromSelectedPreset();
        if (this.latitude$toggleBtn == null) return;
        if (this.latitude$enabled) return;

        int x = this.latitude$toggleBtn.getX();
        int y = this.latitude$toggleBtn.getY() + this.latitude$toggleBtn.getHeight() + 4;
        context.text(this.font, "Latitude is OFF — this world will use default generation.", x, y, 0xFFCCCCCC);
    }

    @Unique
    private void latitude$toggle() {
        boolean newEnabled = !this.latitude$enabled;

        WorldCreationUiState.WorldTypeEntry type = this.worldCreator.getWorldType();
        ResourceKey<WorldPreset> currentKey;
        Holder<WorldPreset> preset = type != null ? type.preset() : null;
        if (preset == null) {
            currentKey = null;
        } else {
            currentKey = preset.unwrapKey().orElse(null);
        }

        if (newEnabled) {
            if (currentKey != null && !LATITUDE_WORLD_PRESET_ID.equals(currentKey.identifier())) {
                this.latitude$previousPreset = currentKey;
            }
            latitude$setWorldPreset(LATITUDE_WORLD_PRESET_ID);
        } else {
            if (this.latitude$previousPreset != null) {
                latitude$setWorldPreset(this.latitude$previousPreset.identifier());
            } else {
                latitude$setWorldPreset(DEFAULT_WORLD_PRESET_ID);
            }
        }

        latitude$syncFromSelectedPreset();
    }

    @Unique
    private void latitude$syncFromSelectedPreset() {
        WorldCreationUiState.WorldTypeEntry type = this.worldCreator.getWorldType();
        Identifier presetId;
        Holder<WorldPreset> preset = type != null ? type.preset() : null;
        if (preset == null) {
            presetId = null;
        } else {
            presetId = preset.unwrapKey().map(ResourceKey::identifier).orElse(null);
        }

        boolean enabledNow = presetId != null && presetId.getNamespace().equals("globe");
        this.latitude$enabled = enabledNow;

        if (this.latitude$toggleBtn != null) {
            this.latitude$toggleBtn.setMessage(latitude$toggleLabel());
        }
    }

    @Unique
    private Component latitude$toggleLabel() {
        if (this.latitude$enabled) {
            return Component.literal("Latitude: ON").withStyle(ChatFormatting.BOLD, ChatFormatting.GREEN);
        }
        return Component.literal("Latitude: OFF").withStyle(ChatFormatting.GRAY);
    }

    @Unique
    private void latitude$setWorldPreset(Identifier presetId) {
        if (this.worldCreator == null) return;

        Registry<WorldPreset> presets = this.worldCreator.getSettings()
                .worldgenLoadContext()
                .lookupOrThrow(Registries.WORLD_PRESET);

        ResourceKey<WorldPreset> key = ResourceKey.create(Registries.WORLD_PRESET, presetId);
        presets.get(key).ifPresent(entry -> this.worldCreator.setWorldType(new WorldCreationUiState.WorldTypeEntry((Holder<WorldPreset>) entry)));
    }

    @Unique
    private static AbstractWidget latitude$findWorldTypeWidget(CreateWorldScreen self) {
        for (GuiEventListener e : self.children()) {
            if (e instanceof AbstractWidget w) {
                String label = w.getMessage().getString().toLowerCase();
                if (label.contains("world type")) {
                    return w;
                }
            }
        }
        return null;
    }
}
