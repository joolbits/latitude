package com.example.globe.mixin.client;

import com.example.globe.client.create.VanillaCreateWorldHandoff;
import com.example.globe.client.create.VanillaFooterLayoutPolicy;
import com.example.globe.client.create.VanillaWorldListDoorPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a small "Vanilla" door beside Create New World on the Select World screen: a one-click route
 * straight to vanilla's own create-world screen, skipping Latitude's. Today that path is two clicks
 * (Create New World -> Latitude's screen -> Other World Types); this is the direct door. Both
 * existing routes stay -- nothing is replaced.
 *
 * <p>Explicitly not a split-button or chevron on Create New World itself: that button is the one
 * everyone aims for, which makes it the worst possible place to add a target for a stray click, and
 * dropdown menus are not a Minecraft idiom.</p>
 *
 * <p>Play is taken from vanilla's own {@code selectButton} field; Create has no field
 * ({@code selectWorld.create} is built inline) and is found by message among
 * {@link Screen#children()}. That asymmetry is deliberate and was paid for: an earlier version
 * matched BOTH by message "for one lookup loop instead of two", and vanilla rewrites the Play
 * button's label through {@code updateButtonStatus} on every selection change, so the match broke
 * the moment a world was selected and the door hid itself. Create's label is never rewritten, so
 * matching it by message is sound. <b>Match a widget by message only where nothing rewrites that
 * message</b> -- and prefer a field wherever vanilla offers one.</p>
 *
 * <p>Runs from BOTH {@code init}'s TAIL and {@code repositionElements}'s TAIL, not init alone --
 * verified in bytecode that {@code Screen.resize(int,int)} calls only {@code repositionElements()},
 * and that {@code init(int,int)} itself runs the full {@code init()} exactly once per screen instance
 * (a hidden {@code initialized} flag routes every later call to {@code repositionElements()} instead).
 * A hook placed only at init's TAIL fires once and never again: reported live as the door failing to
 * reappear after the window was narrowed and then widened back out. Either entry point runs after
 * vanilla has (re)arranged its own footer, so Play and Create's coordinates are always final by the
 * time this reads them.</p>
 *
 * <p>Play, Create, and the door are narrowed together to share the span of the FOUR-button row
 * below them (Edit / Delete / Re-Create / Back) -- not appended past Create's edge with a
 * refuse-if-no-room fallback, which is what an earlier version of this door did and which was
 * reported live as absent at a window that looked perfectly ordinary on screen. Narrowing has no
 * such threshold: nothing beyond what the footer already claims is ever requested, so the door is
 * present whenever Play and Create are.</p>
 *
 * <p>Both the span and the left edge come from that lower row specifically, and that is the whole
 * design. This code resizes Play and Create, and vanilla's footer GridLayout derives its own column
 * widths from its children's widths, so ANY measurement taken off Play or Create is an output of
 * this method's previous pass rather than an input -- which is exactly how two earlier versions
 * failed live, one collapsing the row and one letting it drift during a resize. The four buttons
 * below are never written to here, so they are the only trustworthy reference on this screen, and
 * aligning to them also makes the two rows share an edge by construction rather than by
 * arithmetic.</p>
 */
@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenVanillaDoorMixin {
    /** Vanilla's own Play button. A field, so it cannot be lost to a label rewrite. */
    @Shadow
    private Button selectButton;

    /**
     * The door widget, once constructed. Kept rather than re-added on every layout pass: {@code
     * repositionElements} runs on EVERY window resize (verified in bytecode -- {@code
     * Screen.resize(int,int)} calls only {@code repositionElements()}, never {@code init()} again;
     * a hidden {@code initialized} flag guards the {@code init()} path to a single first call per
     * screen instance), so a naive re-add on every pass would pile up a fresh button behind the
     * last one every time the window is resized.
     */
    @Unique
    private Button globe$doorButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void globe$onInit(CallbackInfo ci) {
        globe$layOutVanillaDoor();
    }

    /**
     * Reported live: the door did not reappear after narrowing the window and then widening it back
     * out. Root cause, found by reading what {@code resize} actually calls rather than assuming it
     * re-runs {@code init} (the assumption an earlier version of this comment made): resizing this
     * screen goes through {@code repositionElements}, not {@code init} -- so a hook placed only at
     * {@code init}'s TAIL fires exactly once per screen instance, and every later resize is
     * invisible to it. This mirrors {@code globe$onInit} at the one place that matters (renarrowing
     * relative to Play and Create's freshly-arranged positions, which vanilla's own layout DOES
     * correctly update on every resize) so the door's presence and geometry track every resize, not
     * just the first one.
     */
    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void globe$onReposition(CallbackInfo ci) {
        globe$layOutVanillaDoor();
    }

    @Unique
    private void globe$layOutVanillaDoor() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        Screen self = (Screen) (Object) this;

        // Play comes from vanilla's OWN field, not from matching its label. Reported live as the
        // door vanishing while the narrowing stayed; the runtime probe showed pass 1 laying the
        // door out correctly and pass 2 failing to find Play at all (create=true, play=false), so
        // the door was hidden while the already-narrowed buttons kept their new widths.
        //
        // The cause is that this button's label is not stable: updateButtonStatus() calls
        // selectButton.setMessage(...) every time the world selection changes, so ANY label-based
        // match on it is guaranteed to stop matching the moment a world is selected. A field
        // lookup cannot drift that way. This is the same discovery-by-message defect already fixed
        // once on the escape-hatch footer, which relabels the button it searches for -- there the
        // fix was to cache the reference; here vanilla hands us a field, which is better still.
        Button play = this.selectButton;
        Button create = null;
        for (GuiEventListener child : self.children()) {
            // Create genuinely has no field, and vanilla never rewrites its label, so matching it
            // by message is sound in a way that matching Play never was.
            if (child instanceof Button button
                    && Component.translatable("selectWorld.create").equals(button.getMessage())) {
                create = button;
                break;
            }
        }
        // An unrecognised footer shape (Mojang changes the layout, or a shadowed-field port finds a
        // different button entirely) leaves the screen untouched rather than narrowing a row this
        // code does not actually understand.
        if (play == null || create == null) {
            globe$hideDoor();
            return;
        }

        int rowY = create.getY();
        int rowHeight = create.getHeight();

        // THE ANCHOR: the row of buttons BELOW this one (Edit / Delete / Re-Create / Back), which
        // this code never writes to. Both the row's span and its left edge are taken from there, so
        // the narrowed row lines up with it exactly at every window size, and -- more importantly --
        // every input to this layout comes from widgets this method never mutates.
        //
        // Two earlier anchors were tried and both failed live. Measuring the envelope from Play and
        // Create themselves fed back into vanilla's own arrangement (see below) and collapsed the
        // row. Anchoring the left edge to play.getX() then drifted, reported as the row "travelling"
        // during a resize: the footer GridLayout's defaultCellSetting is alignHorizontallyCenter, so
        // once Play is narrower than its two-column slot vanilla CENTRES it inside that slot, and
        // its x sits inset from the grid's edge by an amount that changes with the window.
        //
        // Why the feedback existed at all: vanilla's footer is a GridLayout (createFooterButtons ->
        // createRowHelper(4), Play and Create added as 2-column-span children) which derives its
        // column widths FROM its children's current widths, re-arranging on every
        // repositionElements(). Anything this code reads off a button it also resizes is therefore
        // an output of its own previous pass. The four buttons below are 1-span and untouched, so
        // they are the one part of this footer that is a genuine input.
        int otherRowLeft = Integer.MAX_VALUE;
        int otherRowRight = Integer.MIN_VALUE;
        for (GuiEventListener child : self.children()) {
            if (child instanceof Button button
                    && button.getY() != rowY
                    && button.getHeight() == rowHeight
                    && button != this.globe$doorButton) {
                otherRowLeft = Math.min(otherRowLeft, button.getX());
                otherRowRight = Math.max(otherRowRight, button.getX() + button.getWidth());
            }
        }
        if (otherRowLeft == Integer.MAX_VALUE) {
            globe$hideDoor();
            return;
        }

        // Retained ONLY as the historical note on why the previous anchor was unsound. Vanilla's
        // footer is a
        // GridLayout (createFooterButtons -> createRowHelper(4), with Play and Create added as
        // 2-column-span children), and a GridLayout derives its column widths FROM its children's
        // current widths -- re-running arrangeElements() on every repositionElements(). Narrowing
        // Play and Create therefore feeds straight back into vanilla's own arrangement. Measuring
        // the envelope live, as the previous version did, measures a row this code shrank on the
        // pass before: 308 -> 202 -> 132 and downward, reported live as a clipped "Play Selected
        // World" label and a row visibly misaligned with the four buttons beneath it.
        //
        // So the envelope is captured from vanilla's untouched row on the first pass and may only
        // ever grow. Math.max is not decoration: if this capture ever ran again while the buttons
        // still carried a width applied here, a plain assignment would latch the narrowed value and
        // ratchet downward from then on -- the same defect arriving through a different door.
        // Clamped to the SCREEN as well, which this method also never writes. Necessary because 320
        // is not a minimum scaled width: Window.calculateScale tests >= 320 only when deciding
        // whether to INCREASE the scale, so a window narrower than 320 stays at scale 1 and the
        // scaled width is just the window width. Unclamped, the row can start at a negative x, and
        // a narrowed button whose label no longer fits enables a scissor at that negative x, which
        // the renderer rejects outright rather than clipping.
        int envelope = VanillaFooterLayoutPolicy.fittedEnvelope(otherRowRight - otherRowLeft, self.width);
        int width = VanillaFooterLayoutPolicy.buttonWidth(3, envelope);
        int left = Math.max(VanillaFooterLayoutPolicy.SCREEN_MARGIN, otherRowLeft);

        play.setWidth(width);
        play.setX(left);
        play.setY(rowY);
        create.setWidth(width);
        create.setX(VanillaFooterLayoutPolicy.buttonXFrom(left, width, 1));
        create.setY(rowY);

        int doorX = VanillaFooterLayoutPolicy.buttonXFrom(left, width, 2);
        if (this.globe$doorButton == null) {
            this.globe$doorButton = Button.builder(
                            Component.literal("Vanilla..."), button -> globe$openVanillaDoor(client, self))
                    .bounds(doorX, rowY, width, rowHeight)
                    .tooltip(Tooltip.create(Component.literal(
                            "Open Minecraft's own world creation screen, skipping Latitude's")))
                    .build();
            this.globe$doorButton.setAlpha(VanillaWorldListDoorPolicy.ALPHA);
            ((ScreenAddRenderableWidgetInvoker) self).globe$addRenderableWidget(this.globe$doorButton);
        } else {
            this.globe$doorButton.setX(doorX);
            this.globe$doorButton.setY(rowY);
            this.globe$doorButton.setWidth(width);
            this.globe$doorButton.setHeight(rowHeight);
            this.globe$doorButton.visible = true;
            this.globe$doorButton.active = true;
        }
    }

    @Unique
    private void globe$hideDoor() {
        if (this.globe$doorButton != null) {
            this.globe$doorButton.visible = false;
            this.globe$doorButton.active = false;
        }
    }

    @Unique
    private static void globe$openVanillaDoor(Minecraft client, Screen worldList) {
        // The claim key is the Screen the next CreateWorldScreen is constructed with -- same
        // discipline as the escape hatch, so only the screen this door actually opens can claim it.
        // 1.21.1 takes the world list itself rather than a callback that would re-open it.
        VanillaCreateWorldHandoff.armNextWithoutReturn(worldList);
        try {
            CreateWorldScreen.openFresh(client, worldList);
        } catch (RuntimeException exception) {
            VanillaCreateWorldHandoff.cancelNext();
            client.setScreen(worldList);
            throw exception;
        }
    }
}
