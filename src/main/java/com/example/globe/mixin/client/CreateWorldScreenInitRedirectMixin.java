package com.example.globe.mixin.client;

import com.example.globe.client.create.LatitudeCreateWorldScreen;
import com.example.globe.client.create.RecreatedWorldPresetCarrier;
import com.example.globe.client.create.VanillaCreateWorldHandoff;
import com.example.globe.client.create.VanillaFooterLayoutPolicy;
import com.example.globe.client.create.VanillaOnlyWorldCreationState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenInitRedirectMixin {
    @Unique
    private static final Component GLOBE_BACK_TO_LATITUDE = Component.literal("Back to Latitude");

    @Shadow
    private boolean recreated;

    /** The Screen this screen was constructed with; the handoff's claim key on 1.21.1. */
    @Shadow
    @Final
    private Screen lastScreen;

    /**
     * Set once this screen has claimed a vanilla handoff. {@code init} runs again on every window
     * resize, and the payload is one-shot, so without this the second init would find nothing
     * waiting and hand the screen straight back to Latitude's own create screen mid-session.
     */
    @Unique
    private boolean globe$vanillaSession;

    /** Abandons the whole create-world flow. Populated from the handoff payload on claim. */
    @Unique
    private Runnable globe$exitCreateFlow;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void globe$redirectRecreateSafely(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.screen != (Object) this) {
            return;
        }

        CreateWorldScreenMixin self = (CreateWorldScreenMixin) (Object) this;
        if (this.globe$vanillaSession) {
            return;
        }

        var handoff = VanillaCreateWorldHandoff.claimNext(this.lastScreen);
        if (handoff.isPresent()) {
            VanillaCreateWorldHandoff.Payload payload = handoff.orElseThrow();
            // Fields are individually nullable: armNextWithoutReturn (the world-list door) carries
            // none of them. Writing an empty string here would BLANK vanilla's own default name
            // rather than leave it alone, so the write is skipped entirely rather than defaulted.
            if (payload.worldName() != null) {
                self.getUiState().setName(payload.worldName());
            }
            if (payload.seed() != null) {
                self.getUiState().setSeed(payload.seed());
            }
            ((VanillaOnlyWorldCreationState) (Object) self.getUiState()).globe$setVanillaOnly(true);
            // The hatch reaches this screen via createFromExisting, which unconditionally marks the
            // session `recreated` -- that call exists for Re-Create. A world made through the hatch
            // is brand new, not a re-creation, and vanilla reads this flag in exactly one place:
            // onCreate computes `!recreated && lifecycle == stable()` and passes it to
            // confirmWorldCreation as the may-skip-the-warning argument. Left true, a player
            // creating an ordinary stable world here would be shown an experimental-content
            // confirmation that vanilla's own fresh path does not show.
            this.recreated = false;
            this.globe$vanillaSession = true;
            this.globe$exitCreateFlow = payload.exitCreateFlow();
            return;
        }

        // 1.21.1 keeps exactly one back-reference, lastScreen, so it is both the parent and the
        // claim key; the reflective "parent" lookup the newer lines need has nothing to find here.
        Screen parent = this.lastScreen;
        Runnable returnToParent = () -> client.setScreen(parent);

        WorldCreationUiState initialState = self.getUiState();
        String recreatedPresetId = ((RecreatedWorldPresetCarrier) this).globe$getRecreatedWorldPresetId();
        if (!LatitudeCreateWorldScreen.canRepresent(initialState, this.recreated, recreatedPresetId)) {
            return;
        }

        LatitudeCreateWorldScreen.openLoaded(
                client, returnToParent, parent, initialState, this.recreated, recreatedPresetId);
        ci.cancel();
    }

    /**
     * The vanilla button that is NOT Cancel, in the original two-button row. Cached alongside
     * {@link #globe$cancelWidget} so the row never needs to be rediscovered by message after the
     * first successful layout -- see {@link #globe$layOutEscapeHatchFooter()} for why rediscovery
     * would silently fail on every call after the first.
     */
    @Unique
    private Button globe$otherRowWidget;

    /** Vanilla's own Cancel button, relabelled in place rather than replaced. */
    @Unique
    private Button globe$cancelWidget;

    /** The new, genuinely additional exit button this feature adds. */
    @Unique
    private Button globe$exitWidget;

    @Inject(method = "init", at = @At("TAIL"))
    private void globe$onInit(CallbackInfo ci) {
        globe$layOutEscapeHatchFooter();
    }

    /**
     * Reported live (on the sibling world-list door, same root cause applies here): a hook placed
     * only at {@code init}'s TAIL fires exactly once per screen instance, because {@code
     * Screen.resize(int,int)} calls only {@code repositionElements()}, never {@code init()} again
     * (verified in bytecode; a hidden {@code initialized} flag routes every call after the first
     * away from the {@code init()} path entirely). Without this, the footer's relabel and third
     * button would go stale the moment the window is resized: vanilla's own {@code arrangeElements}
     * DOES re-centre the two ORIGINAL buttons on every resize (they are members of its own layout
     * tree), but our added third button is not, so the two would drift back toward vanilla's own
     * two-button spacing while the third stayed frozen at its original coordinates -- not invisible
     * like the door's failure mode, but visibly misaligned or overlapping after any resize.
     */
    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void globe$onReposition(CallbackInfo ci) {
        globe$layOutEscapeHatchFooter();
    }

    /**
     * Relabels vanilla's own Cancel button and adds a second, distinct exit button, so the vanilla
     * screen reached through the hatch names both of its exits instead of leaving Cancel's
     * destination unstated. Reported live: "not obvious that Cancel is what brings you back to
     * Latitude", and leaving vanilla via Cancel then having to Cancel a SECOND time out of
     * Latitude's own screen to actually leave -- two exits for one intent.
     *
     * <p>Discovery (finding the row by scanning for vanilla's own Cancel MESSAGE) can only ever work
     * ONCE: the very first successful run relabels Cancel to "Back to Latitude", so a second attempt
     * at the same message-based search would find nothing and silently no-op. Widget REFERENCES are
     * cached after the first success specifically so every later call can skip discovery and just
     * reposition the same three buttons -- which must happen on every resize, not just once, to stay
     * aligned with vanilla's own row as it re-centres.</p>
     *
     * <p>The two early returns during discovery are deliberate, not defensive filler: an unrecognised
     * footer shape (Mojang changes the layout, or some other mod already altered it) leaves vanilla's
     * screen completely untouched rather than mangling a footer this code does not actually
     * understand.</p>
     */
    @Unique
    private void globe$layOutEscapeHatchFooter() {
        Runnable exit = this.globe$exitCreateFlow;
        if (!this.globe$vanillaSession || exit == null) {
            return;
        }
        Screen self = (Screen) (Object) this;

        if (this.globe$cancelWidget == null) {
            Button cancel = null;
            for (GuiEventListener child : self.children()) {
                if (child instanceof Button button && CommonComponents.GUI_CANCEL.equals(button.getMessage())) {
                    cancel = button;
                    break;
                }
            }
            if (cancel == null) {
                return;
            }

            int rowY = cancel.getY();
            int rowHeight = cancel.getHeight();
            List<Button> row = new ArrayList<>();
            for (GuiEventListener child : self.children()) {
                if (child instanceof Button button && button.getY() == rowY && button.getHeight() == rowHeight) {
                    row.add(button);
                }
            }
            if (row.size() != 2) {
                return;
            }

            this.globe$cancelWidget = cancel;
            this.globe$otherRowWidget = row.get(0) == cancel ? row.get(1) : row.get(0);
            cancel.setMessage(GLOBE_BACK_TO_LATITUDE);

            this.globe$exitWidget = Button.builder(CommonComponents.GUI_CANCEL, button -> exit.run()).build();
            ((ScreenAddRenderableWidgetInvoker) self).globe$addRenderableWidget(this.globe$exitWidget);
        }

        // Reposition all three every call -- covers both the first layout and every later resize,
        // reading vanilla's freshly re-centred Y/height rather than assuming they never change.
        List<Button> row = new ArrayList<>(List.of(this.globe$cancelWidget, this.globe$otherRowWidget));
        row.sort(Comparator.comparingInt(AbstractWidget::getX));

        int count = 3;
        // Clamped to the screen. Unlike the world-list door this envelope is a CONSTANT, never
        // re-read from the buttons it resizes, so there is no feedback loop here and never was --
        // which is why this footer stayed correct live while the door collapsed on its first
        // flight. What it did have is a reachable crash: 320 is not a minimum scaled width.
        // Window.calculateScale tests >= 320 only when deciding whether to INCREASE the GUI scale,
        // so a window narrower than 320 never enters that loop, stays at scale 1, and reports a
        // scaled width equal to the window width. Below 307 the old centred row began at a
        // negative x, and these buttons are narrow enough that their labels need a scissor, which
        // the renderer refuses outright at a negative x rather than merely clipping.
        int envelope = VanillaFooterLayoutPolicy.fittedEnvelope(self.width);
        int width = VanillaFooterLayoutPolicy.buttonWidth(count, envelope);
        int left = VanillaFooterLayoutPolicy.rowLeft(count, width, self.width);
        int rowY = this.globe$cancelWidget.getY();
        int rowHeight = this.globe$cancelWidget.getHeight();
        for (int i = 0; i < row.size(); i++) {
            Button button = row.get(i);
            button.setWidth(width);
            button.setX(VanillaFooterLayoutPolicy.buttonXFrom(left, width, i));
            button.setY(rowY);
        }
        this.globe$exitWidget.setWidth(width);
        this.globe$exitWidget.setHeight(rowHeight);
        this.globe$exitWidget.setX(VanillaFooterLayoutPolicy.buttonXFrom(left, width, count - 1));
        this.globe$exitWidget.setY(rowY);
    }

}
