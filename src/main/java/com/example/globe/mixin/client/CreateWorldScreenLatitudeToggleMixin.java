package com.example.globe.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.world.gen.WorldPreset;
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
    private static final Identifier LATITUDE_WORLD_PRESET_ID = Identifier.of("globe", "globe");

    @Unique
    private static final Identifier DEFAULT_WORLD_PRESET_ID = Identifier.of("minecraft", "normal");

    @Shadow
    @Final
    private WorldCreator worldCreator;

    @Unique
    private boolean latitude$enabled = true;

    @Unique
    private @Nullable ButtonWidget latitude$toggleBtn;

    @Unique
    private @Nullable RegistryKey<WorldPreset> latitude$previousPreset;

    @Unique
    private @Nullable ClickableWidget latitude$worldTypeWidget;

    @Unique
    private boolean latitude$didInitDefaults;

    protected CreateWorldScreenLatitudeToggleMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void latitude$initToggle(CallbackInfo ci) {
        this.latitude$toggleBtn = null;
        this.latitude$previousPreset = null;
        this.latitude$worldTypeWidget = latitude$findWorldTypeWidget((CreateWorldScreen) (Object) this);

        WorldCreator.WorldType type = this.worldCreator.getWorldType();
        RegistryEntry<WorldPreset> preset = type != null ? type.preset() : null;
        RegistryKey<WorldPreset> currentKey = preset != null ? preset.getKey().orElse(null) : null;
        Identifier currentId = currentKey != null ? currentKey.getValue() : null;

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

        this.latitude$toggleBtn = ButtonWidget.builder(latitude$toggleLabel(), b -> latitude$toggle())
                .dimensions(x, y, w, h)
                .build();
        this.addDrawableChild(this.latitude$toggleBtn);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"))
    private void latitude$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        latitude$syncFromSelectedPreset();
        if (this.latitude$toggleBtn == null) return;
        if (this.latitude$enabled) return;

        int x = this.latitude$toggleBtn.getX();
        int y = this.latitude$toggleBtn.getY() + this.latitude$toggleBtn.getHeight() + 4;
        context.drawTextWithShadow(this.textRenderer, "Latitude is OFF — this world will use default generation.", x, y, 0xFFCCCCCC);
    }

    @Unique
    private void latitude$toggle() {
        boolean newEnabled = !this.latitude$enabled;

        WorldCreator.WorldType type = this.worldCreator.getWorldType();
        RegistryKey<WorldPreset> currentKey;
        RegistryEntry<WorldPreset> preset = type != null ? type.preset() : null;
        if (preset == null) {
            currentKey = null;
        } else {
            currentKey = preset.getKey().orElse(null);
        }

        if (newEnabled) {
            if (currentKey != null && !LATITUDE_WORLD_PRESET_ID.equals(currentKey.getValue())) {
                this.latitude$previousPreset = currentKey;
            }
            latitude$setWorldPreset(LATITUDE_WORLD_PRESET_ID);
        } else {
            if (this.latitude$previousPreset != null) {
                latitude$setWorldPreset(this.latitude$previousPreset.getValue());
            } else {
                latitude$setWorldPreset(DEFAULT_WORLD_PRESET_ID);
            }
        }

        latitude$syncFromSelectedPreset();
    }

    @Unique
    private void latitude$syncFromSelectedPreset() {
        WorldCreator.WorldType type = this.worldCreator.getWorldType();
        Identifier presetId;
        RegistryEntry<WorldPreset> preset = type != null ? type.preset() : null;
        if (preset == null) {
            presetId = null;
        } else {
            presetId = preset.getKey().map(RegistryKey::getValue).orElse(null);
        }

        boolean enabledNow = presetId != null && presetId.getNamespace().equals("globe");
        this.latitude$enabled = enabledNow;

        if (this.latitude$toggleBtn != null) {
            this.latitude$toggleBtn.setMessage(latitude$toggleLabel());
        }
    }

    @Unique
    private Text latitude$toggleLabel() {
        if (this.latitude$enabled) {
            return Text.literal("Latitude: ON").formatted(Formatting.BOLD, Formatting.GREEN);
        }
        return Text.literal("Latitude: OFF").formatted(Formatting.GRAY);
    }

    @Unique
    private void latitude$setWorldPreset(Identifier presetId) {
        if (this.worldCreator == null) return;

        Registry<WorldPreset> presets = this.worldCreator.getGeneratorOptionsHolder()
                .getCombinedRegistryManager()
                .getOrThrow(RegistryKeys.WORLD_PRESET);

        RegistryKey<WorldPreset> key = RegistryKey.of(RegistryKeys.WORLD_PRESET, presetId);
        presets.getOptional(key).ifPresent(entry -> this.worldCreator.setWorldType(new WorldCreator.WorldType((RegistryEntry<WorldPreset>) entry)));
    }

    @Unique
    private static ClickableWidget latitude$findWorldTypeWidget(CreateWorldScreen self) {
        for (Element e : self.children()) {
            if (e instanceof ClickableWidget w) {
                String label = w.getMessage().getString().toLowerCase();
                if (label.contains("world type")) {
                    return w;
                }
            }
        }
        return null;
    }
}
