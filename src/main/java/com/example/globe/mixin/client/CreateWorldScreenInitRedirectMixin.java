package com.example.globe.mixin.client;

import com.example.globe.client.create.LatitudeCreateWorldScreen;
import com.example.globe.client.create.RecreatedWorldPresetCarrier;
import com.example.globe.client.create.VanillaCreateWorldHandoff;
import com.example.globe.client.create.VanillaFooterLayoutPolicy;
import com.example.globe.client.create.VanillaOnlyWorldCreationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
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
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenInitRedirectMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("globe");
    // [LAT][CWPATH] fires on every ordinary create-screen open; opt-in only (maintainer ruling, 2026-08-18).
    private static final boolean DEBUG_CWPATH = Boolean.getBoolean("latitude.debugCwPath");

    /**
     * Names the destination vanilla's generic "Cancel" never did. Literal rather than a lang key to
     * match {@code LatitudeCreateWorldScreen}'s own footer, which builds its buttons the same way.
     */
    private static final Component BACK_TO_LATITUDE = Component.literal("Back to Latitude");

    @Shadow
    private boolean recreated;

    /**
     * This screen's own close callback, used as the handoff claim key. Latitude passes the very
     * same {@code Runnable} to {@code CreateWorldScreen.openFresh}, so only the screen Latitude
     * actually opened can claim the pending handoff — an unrelated create-world screen opened by
     * some other path carries a different callback and leaves the handoff alone.
     */
    @Shadow
    @Final
    private Runnable onClose;

    /**
     * Marks a screen that already claimed a handoff, so a later {@code init()} cannot find no
     * pending handoff and fall through to Latitude's redirect — which would bounce the player out
     * of the vanilla screen they deliberately asked for.
     *
     * <p>CORRECTION (2026-08-28): this comment previously said "init runs again on every resize".
     * It does not. {@code Screen.init(int,int)} runs the overridable {@code init()} only on its
     * FIRST call per instance; a private {@code initialized} flag routes every later call, resizes
     * included, through {@code repositionElements()}. The guard is still right, the stated reason
     * was not — and believing it is why the footer below was hooked only at {@code init}.</p>
     */
    @Unique
    private boolean globe$vanillaSession;

    /**
     * Abandons the whole create-world flow in one click, claimed alongside the name and seed.
     * Survives the re-{@code init} on every resize because the footer is rebuilt from it each time.
     */
    @Unique
    private Runnable globe$exitCreateFlow;

    /**
     * Footer widgets cached after the first successful layout.
     *
     * <p>Re-discovery on a later pass would fail: the search keys off vanilla's original
     * {@code GUI_CANCEL} message, and the first pass RELABELS that very button to "Back to
     * Latitude". A second search finds nothing and silently leaves the footer half-laid-out.</p>
     */
    @Unique
    private Button globe$footerCancel;

    @Unique
    private List<Button> globe$footerRow;

    @Unique
    private Button globe$footerExit;

    /**
     * Gives the escape-hatch footer a named way back and a one-click way out.
     *
     * <p>Vanilla's footer is {@code LinearLayout.horizontal().spacing(8)} holding two default-width
     * buttons, and it has already been arranged and registered by the time this runs -- so the
     * buttons carry final coordinates and can simply be re-placed, rather than us re-entering the
     * layout and having to re-arrange and re-register everything. Three default-width buttons would
     * be 466px against a 320px narrowest scaled GUI, so all three narrow to share the width vanilla
     * already used ({@link VanillaFooterLayoutPolicy}).</p>
     *
     * <p>Vanilla's own Cancel already returns to Latitude -- it just never said so -- so it is
     * relabelled rather than rewired, and the genuinely new behaviour is the added button.
     * Everything here is gated on {@link #globe$vanillaSession}: a create-world screen opened by any
     * other path never claims a handoff and is left exactly as vanilla built it.</p>
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void globe$layOutEscapeHatchFooterOnInit(CallbackInfo ci) {
        // Do NOT clear blindly. init() calls repositionElements() before it returns (26.2
        // bytecode, offset 159), and the session gate is already open by then because it is set at
        // init's HEAD -- so the resize hook has ALREADY laid this footer out once by the time this
        // TAIL runs. Dropping the cache here made the re-discovery below match our own exit button
        // (it carries GUI_CANCEL, and vanilla's Cancel no longer does once relabelled), after which
        // the row held three buttons, failed its two-button shape check, and left globe$footerExit
        // null -- so every later resize bailed and the footer froze while vanilla re-centred its own
        // two buttons around it. Precisely the drift this hook pair exists to prevent.
        //
        // THE FIX IS NOT CLEARING. The condition below is defence-in-depth and, on 26.2, is
        // unreachable: neither CreateWorldScreen nor SelectWorldScreen (nor any of their inner
        // classes) calls rebuildWidgets() or clearWidgets() anywhere -- zero call sites, checked by
        // javap over the whole class family -- so once our widget is registered it is never
        // discarded and contains() is always true. It is kept because a future version adding a
        // rebuild (a tab switch is the plausible site) would otherwise leave this cache pointing at
        // dead widgets, and it costs one list lookup per init. Do not read it as the thing that
        // stops the duplicate/freeze bug: removing the UNCONDITIONAL clear is what does that.
        // Confirmed independently on the 1.21.11 line, which has no call sites either and whose
        // hatch never clears at all -- so this is a shared property of both, not a divergence.
        Screen self = (Screen) (Object) this;
        if (this.globe$footerExit != null && !self.children().contains(this.globe$footerExit)) {
            this.globe$footerCancel = null;
            this.globe$footerRow = null;
            this.globe$footerExit = null;
        }
        globe$layOutEscapeHatchFooter();
    }

    /**
     * Re-lays the footer on every resize.
     *
     * <p>Vanilla's {@code arrangeElements()} re-centres its OWN two buttons back to their default
     * width and spacing on resize. Without this hook the added third button keeps its first-open
     * coordinates while those two drift, leaving the row visibly misaligned or overlapping — and
     * the narrowing applied here undone.</p>
     */
    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void globe$layOutEscapeHatchFooterOnResize(CallbackInfo ci) {
        globe$layOutEscapeHatchFooter();
    }

    @Unique
    private void globe$layOutEscapeHatchFooter() {
        Runnable exit = this.globe$exitCreateFlow;
        if (!this.globe$vanillaSession || exit == null) {
            return;
        }
        Screen self = (Screen) (Object) this;

        Button cancel = this.globe$footerCancel;
        if (cancel == null) {
            for (GuiEventListener child : self.children()) {
                // Never match our own exit button: it deliberately carries GUI_CANCEL, and once
                // vanilla's Cancel is relabelled ours is the ONLY thing this label still finds.
                // Match a widget by message only where nothing rewrites that message -- here this
                // code rewrites it itself, so the reference cache above is what makes the lookup
                // sound, and this exclusion is what makes a re-discovery sound.
                if (child instanceof Button button && button != this.globe$footerExit
                        && CommonComponents.GUI_CANCEL.equals(button.getMessage())) {
                    cancel = button;
                    break;
                }
            }
            if (cancel == null) {
                return; // vanilla's footer is not the shape we know; leave it untouched
            }
            this.globe$footerCancel = cancel;
        }

        int rowY = cancel.getY();
        int rowHeight = cancel.getHeight();
        List<Button> row = this.globe$footerRow;
        if (row == null) {
            row = new ArrayList<>();
            for (GuiEventListener child : self.children()) {
                // Excluded for the same reason as in the door mixin: our button shares the row's
                // Y and height, so counting it makes the two-button shape check fail.
                if (child instanceof Button button && button != this.globe$footerExit
                        && button.getY() == rowY && button.getHeight() == rowHeight) {
                    row.add(button);
                }
            }
            if (row.size() != 2) {
                return; // only the known two-button footer is safe to re-lay
            }
            row.sort(Comparator.comparingInt(AbstractWidget::getX));
            this.globe$footerRow = row;
        }

        int count = row.size() + 1;
        // Same clamp as the world-list door: a manual GUI scale can put self.width well below the
        // row's natural width, and centring then places the first button at a negative x -- an
        // out-of-bounds scissor crash on the next draw, not a cosmetic issue.
        int envelope = VanillaFooterLayoutPolicy.fittedEnvelope(
                VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH, self.width,
                VanillaFooterLayoutPolicy.EDGE_MARGIN);
        int width = VanillaFooterLayoutPolicy.buttonWidth(count, envelope);
        int centreX = VanillaFooterLayoutPolicy.clampedRowLeft(
                self.width, envelope, VanillaFooterLayoutPolicy.EDGE_MARGIN) + envelope / 2;
        for (int i = 0; i < row.size(); i++) {
            Button button = row.get(i);
            button.setWidth(width);
            button.setX(VanillaFooterLayoutPolicy.buttonX(centreX, count, width, i));
        }
        cancel.setMessage(BACK_TO_LATITUDE);

        int exitX = VanillaFooterLayoutPolicy.buttonX(centreX, count, width, count - 1);
        if (this.globe$footerExit == null) {
            // Via the invoker on Screen, which DECLARES this method -- an @Shadow does not resolve.
            Button exitButton = Button.builder(CommonComponents.GUI_CANCEL, button -> exit.run())
                    .bounds(exitX, rowY, width, rowHeight)
                    .build();
            ((ScreenAddRenderableWidgetInvoker) self).globe$addRenderableWidget(exitButton);
            this.globe$footerExit = exitButton;
        } else {
            // Re-place the SAME widget; rebuilding would stack one per resize.
            this.globe$footerExit.setX(exitX);
            this.globe$footerExit.setY(rowY);
            this.globe$footerExit.setWidth(width);
        }

        if (DEBUG_CWPATH) {
            LOGGER.info("[LAT][CWPATH] escape-hatch footer laid out: {} buttons at {}px", count, width);
        }
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void globe$redirectRecreateSafely(CallbackInfo ci) {
        if (DEBUG_CWPATH) {
            LOGGER.info("[LAT][CWPATH] CreateWorldScreenInitRedirectMixin.init screen={}", this.getClass().getName());
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.screen != (Object) this) {
            return;
        }

        // A session the player reached through Latitude's escape hatch stays vanilla: carry the
        // name and seed across, withhold the Globe preset, and never redirect back.
        if (this.globe$vanillaSession) {
            return;
        }
        CreateWorldScreenMixin self = (CreateWorldScreenMixin) (Object) this;
        var handoff = VanillaCreateWorldHandoff.claimNext(this.onClose);
        if (handoff.isPresent()) {
            VanillaCreateWorldHandoff.Claim claim = handoff.orElseThrow();
            VanillaCreateWorldHandoff.Payload payload = claim.payload();
            // A null payload means the session was armed from the Select World screen, which has no
            // typed inputs to forward. Vanilla keeps its own default name and seed -- writing empty
            // strings here would blank them, which is not the same as leaving them alone.
            if (payload != null) {
                self.getUiState().setName(payload.worldName());
                self.getUiState().setSeed(payload.seed());
            }
            ((VanillaOnlyWorldCreationState) (Object) self.getUiState()).globe$setVanillaOnly(true);
            this.globe$vanillaSession = true;
            this.globe$exitCreateFlow = claim.exitCreateFlow();
            // createFromExisting sets recreated=true unconditionally -- it exists for Re-Create.
            // A world made through the hatch is NEW, not re-created, and vanilla reads this flag in
            // exactly one place: onCreate computes `!recreated && lifecycle == stable()` and passes
            // it as confirmWorldCreation's may-skip-the-warning argument. Left set, an ordinary
            // stable world would show an experimental-content confirmation vanilla's own fresh path
            // never shows. Cleared here, on the claim branch, which returns before anything reads it.
            this.recreated = false;
            if (DEBUG_CWPATH) {
                LOGGER.info("[LAT][CWPATH] Claimed vanilla create-world handoff for screen={}",
                        this.getClass().getName());
            }
            return;
        }

        Screen parent = globe$getParentSafe((Object) this);
        // Named apart from the shadowed onClose field above: that one is vanilla's own callback
        // and doubles as the handoff claim key, this one is where Latitude's screen goes on close.
        Runnable returnToParent = () -> client.setScreen(parent);

        WorldCreationUiState initialState = self.getUiState();
        String recreatedPresetId = ((RecreatedWorldPresetCarrier) this).globe$getRecreatedWorldPresetId();
        if (!LatitudeCreateWorldScreen.canRepresent(initialState, this.recreated, recreatedPresetId)) {
            if (DEBUG_CWPATH) {
                LOGGER.info("[LAT][CWPATH] leaving unsupported create-world preset on vanilla screen: {}",
                        initialState.getWorldType());
            }
            return;
        }

        LatitudeCreateWorldScreen.openLoaded(
                client, returnToParent, parent, initialState, this.recreated, recreatedPresetId);
        ci.cancel();
    }

    private static Screen globe$getParentSafe(Object self) {
        try {
            Field parentField = self.getClass().getDeclaredField("parent");
            parentField.setAccessible(true);
            Object value = parentField.get(self);
            return value instanceof Screen ? (Screen) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
