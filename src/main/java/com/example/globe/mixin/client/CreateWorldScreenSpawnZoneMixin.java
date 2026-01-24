package com.example.globe.mixin.client;

import com.example.globe.GlobePending;
import com.example.globe.client.GlobeWorldSize;
import com.example.globe.client.GlobeWorldSizeSelection;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
    private static final Identifier GLOBE_WORLD_PRESET_ID = Identifier.of("globe", "globe");

    @Unique
    private static final String[] GLOBE_ZONES = {
            "EQUATOR", "TROPICAL", "SUBTROPICAL", "TEMPERATE", "SUBPOLAR", "POLAR"
    };

    @Shadow
    @Final
    private WorldCreator worldCreator;

    @Unique
    private CyclingButtonWidget<String> globe$spawnZoneButton;

    @Unique
    private CyclingButtonWidget<GlobeWorldSize> globe$worldSizeButton;

    @Unique
    private ClickableWidget globe$worldTypeWidget;

    protected CreateWorldScreenSpawnZoneMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void globe$initSpawnZone(CallbackInfo ci) {
        this.globe$spawnZoneButton = null;
        this.globe$worldSizeButton = null;
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

        this.globe$spawnZoneButton = CyclingButtonWidget.builder(Text::literal, GLOBE_ZONES[0])
                .values(GLOBE_ZONES)
                .build(x, y, w, h, Text.literal("Spawn Zone"), (btn, value) -> GlobePending.set(value));

        this.addDrawableChild(this.globe$spawnZoneButton);

        this.globe$worldSizeButton = CyclingButtonWidget
                .builder((GlobeWorldSize v) -> v.label, GlobeWorldSizeSelection.get())
                .values(GlobeWorldSize.values())
                .build(x, y + h + 4, w, h, Text.literal("World Size"), (btn, value) -> GlobeWorldSizeSelection.set(value));

        this.addDrawableChild(this.globe$worldSizeButton);

        GlobePending.set(GLOBE_ZONES[0]);
        globe$updateEnabledState();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void globe$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        globe$updateEnabledState();
    }

    @Unique
    private void globe$updateEnabledState() {
        if (this.globe$spawnZoneButton == null) {
            return;
        }

        boolean globeSelected = globe$isGlobeSelected();
        this.globe$spawnZoneButton.visible = globeSelected;
        this.globe$spawnZoneButton.active = globeSelected;

        if (this.globe$worldSizeButton != null) {
            this.globe$worldSizeButton.visible = globeSelected;
            this.globe$worldSizeButton.active = globeSelected;
        }
    }

    @Unique
    private boolean globe$isGlobeSelected() {
        WorldCreator.WorldType type = this.worldCreator.getWorldType();
        if (type == null || type.preset() == null) {
            return false;
        }

        return type.preset()
                .getKey()
                .map(RegistryKey::getValue)
                .map(GLOBE_WORLD_PRESET_ID::equals)
                .orElse(false);
    }

    @Unique
    private static ClickableWidget globe$findWorldTypeWidget(CreateWorldScreen self) {
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
