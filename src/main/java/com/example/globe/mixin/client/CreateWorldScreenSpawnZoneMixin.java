package com.example.globe.mixin.client;

import com.example.globe.GlobePending;
import com.example.globe.client.GlobeWorldSize;
import com.example.globe.client.GlobeWorldSizeSelection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenSpawnZoneMixin extends Screen {
    @Unique
    private static final Identifier GLOBE_WORLD_PRESET_ID = Identifier.fromNamespaceAndPath("globe", "globe");

    @Unique
    private static final String[] GLOBE_ZONES = {
            "TROPICAL", "SUBTROPICAL", "TEMPERATE", "SUBPOLAR", "POLAR", "RANDOM"
    };

    @Shadow
    @Final
    private WorldCreationUiState worldCreator;

    @Unique
    private CycleButton<String> globe$spawnZoneButton;

    @Unique
    private CycleButton<GlobeWorldSize> globe$worldSizeButton;

    @Unique
    private Button globe$startWithCompassButton;

    @Unique
    private AbstractWidget globe$worldTypeWidget;

    protected CreateWorldScreenSpawnZoneMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void globe$initSpawnZone(CallbackInfo ci) {
        this.globe$spawnZoneButton = null;
        this.globe$worldSizeButton = null;
        this.globe$startWithCompassButton = null;
        this.globe$worldTypeWidget = null;

        CreateWorldScreen self = (CreateWorldScreen) (Object) this;
        this.globe$worldTypeWidget = globe$findWorldTypeWidget(self);

        int x;
        int y;
        int w;
        int h;
        if (this.globe$worldTypeWidget != null) {
            x = this.globe$worldTypeWidget.getX();
            y = this.globe$worldTypeWidget.getY() + this.globe$worldTypeWidget.getHeight() + 4;
            w = this.globe$worldTypeWidget.getWidth();
            h = this.globe$worldTypeWidget.getHeight();
        } else {
            x = self.width / 2 - 100;
            y = self.height / 2 + 10;
            w = 200;
            h = 20;
        }

        this.globe$spawnZoneButton = CycleButton.builder(CreateWorldScreenSpawnZoneMixin::globe$zoneLabel, GLOBE_ZONES[0])
                .withValues(GLOBE_ZONES)
                .create(x, y, w, h, Component.literal("Spawn Zone"), (btn, value) -> GlobePending.set(value));

        this.addRenderableWidget(this.globe$spawnZoneButton);

        this.globe$worldSizeButton = CycleButton
                .builder((GlobeWorldSize v) -> v.label, GlobeWorldSizeSelection.get())
                .withValues(GlobeWorldSize.values())
                .create(x, y + h + 4, w, h, Component.literal("World Size"), (btn, value) -> GlobeWorldSizeSelection.set(value));

        this.addRenderableWidget(this.globe$worldSizeButton);

        GlobePending.startWithCompass = true;
        this.globe$startWithCompassButton = Button.builder(globe$startWithCompassLabel(), b -> {
                    GlobePending.startWithCompass = !GlobePending.startWithCompass;
                    b.setMessage(globe$startWithCompassLabel());
                })
                .bounds(x, y + (h + 4) * 2, w, h)
                .build();
        this.addRenderableWidget(this.globe$startWithCompassButton);

        GlobePending.set(GLOBE_ZONES[0]);
        globe$updateEnabledState();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void globe$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        globe$updateEnabledState();
    }

    @Unique
    private void globe$updateEnabledState() {
        if (this.globe$spawnZoneButton == null) {
            return;
        }

        this.globe$spawnZoneButton.visible = true;
        this.globe$spawnZoneButton.active = true;

        if (this.globe$worldSizeButton != null) {
            this.globe$worldSizeButton.visible = true;
            this.globe$worldSizeButton.active = true;
        }

        if (this.globe$startWithCompassButton != null) {
            this.globe$startWithCompassButton.visible = true;
            this.globe$startWithCompassButton.active = true;
            this.globe$startWithCompassButton.setMessage(globe$startWithCompassLabel());
        }
    }

    @Unique
    private static Component globe$startWithCompassLabel() {
        return Component.literal("Start with compass: " + (GlobePending.startWithCompass ? "ON" : "OFF"));
    }

    @Unique
    private static Component globe$zoneLabel(String id) {
        if (id == null) {
            return Component.literal("");
        }

        if (id.equals("RANDOM")) {
            return globe$rainbowRandomText();
        }

        return Component.literal(switch (id) {
            case "TROPICAL" -> "Tropical";
            case "SUBTROPICAL" -> "Subtropical";
            case "TEMPERATE" -> "Temperate";
            case "SUBPOLAR" -> "Subpolar";
            case "POLAR" -> "Polar";
            default -> id;
        });
    }

    private static Component globe$rainbowRandomText() {
        ChatFormatting[] colors = {
                ChatFormatting.RED,
                ChatFormatting.GOLD,
                ChatFormatting.YELLOW,
                ChatFormatting.GREEN,
                ChatFormatting.AQUA,
                ChatFormatting.BLUE,
                ChatFormatting.LIGHT_PURPLE
        };

        String s = "Random";
        MutableComponent out = Component.empty();
        for (int i = 0; i < s.length(); i++) {
            out.append(Component.literal(String.valueOf(s.charAt(i))).withStyle(colors[i % colors.length]));
        }
        return out.withStyle(ChatFormatting.ITALIC);
    }

    @Unique
    private boolean globe$isGlobeSelected() {
        WorldCreationUiState.WorldTypeEntry type = this.worldCreator.getWorldType();
        if (type == null || type.preset() == null) {
            return false;
        }

        return type.preset()
                .unwrapKey()
                .map(ResourceKey::identifier)
                .map(GLOBE_WORLD_PRESET_ID::equals)
                .orElse(false);
    }

    @Unique
    private static AbstractWidget globe$findWorldTypeWidget(CreateWorldScreen self) {
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
