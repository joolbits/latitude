package com.example.globe.mixin.client;

import com.example.globe.client.create.VanillaCreateWorldHandoff;
import com.example.globe.client.create.VanillaFooterLayoutPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A one-click door from the world list straight to vanilla's create-world screen.
 *
 * <p>Latitude's own screen owns "Create New World", so vanilla's was two clicks deep -- through
 * Latitude's screen and out its escape hatch. This puts it one click away without taking anything
 * away from the Latitude route (maintainer request, 2026-08-28).</p>
 *
 * <p><b>The design is a deliberate balance between two rules that pull apart:</b> it must read as
 * vanilla before it is pressed, and it must not be pressed by accident by someone heading for
 * Latitude. Hence a narrow button set off by a real {@link #GLOBE_GAP}-wide gap rather than sitting
 * flush against the button everyone aims for, dimmed so it reads as secondary before the label is
 * even read, and carrying a tooltip that names the destination in full. A chevron fused to "Create
 * New World" was considered and rejected: it is the worst possible position for a stray click.</p>
 *
 * <p><b>Read only what this layout never writes.</b> Vanilla's footer grid is
 * {@code createRowHelper(4)} with {@code defaultCellSetting().alignHorizontallyCenter()}, and
 * {@code AbstractChildWrapper.setX} places a child at
 * {@code cellLeft + (cellWidth - childWidth) / 2}. Play and Create are built at the default 150
 * and their two-column cells are 71+8+71 = 150, so vanilla's own inset is zero -- but only until
 * this layout narrows them, after which their x carries a ~27px inset that is this code's own
 * output read back. So the left edge here is derived from {@code self.width}, never from a
 * narrowed button's x. Reading a coordinate this method writes is the feedback loop that
 * collapsed the sibling line's footer; the invariant is about which values are written, not about
 * the arithmetic happening to come out even at 150.</p>
 *
 * <p>Height and Y still come from Create, which nothing here or in vanilla rewrites, and the row
 * is re-placed from both {@code init} and {@code repositionElements}: {@code Screen.resize} calls
 * {@code repositionElements()} alone and never re-runs {@code init()}, so an {@code init}-only
 * hook would freeze the door at its first-open coordinates.</p>
 */
@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenVanillaDoorMixin {
    @Unique
    private static final Component GLOBE_VANILLA_LABEL = Component.literal("Vanilla...");

    /**
     * Names the destination in full, because the label alone could be misread as a world TYPE rather
     * than a different screen.
     */
    @Unique
    private static final Component GLOBE_VANILLA_TOOLTIP =
            Component.literal("Open Minecraft's own world creation screen, skipping Latitude's");

    /** Separation from "Create New World". This gap IS the mis-click defence -- not decoration. */
    @Unique
    private static final int GLOBE_GAP = 10;

    /** Vanilla's own spacing between the two original buttons, preserved between them. */
    @Unique
    private static final int GLOBE_INNER_GAP = 8;

    /** Dimmed enough to read as secondary, not so far that it reads as disabled. */
    @Unique
    private static final float GLOBE_DOOR_ALPHA = 0.7F;

    /**
     * Cached across resizes, and that is load-bearing rather than an optimisation.
     *
     * <p>{@code Screen.init(int,int)} runs the overridable {@code init()} only on its FIRST call per
     * screen instance -- a private {@code initialized} flag routes every later call, including every
     * resize, through {@code repositionElements()} instead. A hook on {@code init}'s TAIL therefore
     * fires exactly once, ever. Re-running the whole layout on each resize would call
     * {@code addRenderableWidget} again and stack a fresh button behind the last one forever, so the
     * door is built once and merely re-placed afterwards.</p>
     */
    @Unique
    private Button globe$door;

    @Unique
    private Button globe$create;

    @Unique
    private List<Button> globe$row;

    /**
     * Vanilla's own button width, captured BEFORE the first narrowing.
     *
     * <p>The envelope must not be re-derived from the live buttons: this layout narrows them, so
     * reading their span on the next pass measures its own previous output and the row ratchets
     * smaller on every resize (observed: 308 -> 200 -> 218 -> 227). Anchoring to the original width
     * makes the layout idempotent -- the same screen width always produces the same result, however
     * many times it runs.</p>
     */
    @Unique
    private int globe$originalButtonWidth;

    @Inject(method = "init", at = @At("TAIL"))
    private void globe$addVanillaDoorOnInit(CallbackInfo ci) {
        // Do NOT blindly clear here. The mechanism, now read off the 26.2 bytecode rather than
        // inferred from the symptom: init() calls repositionElements() before it returns (offset
        // 265), so the resize hook has ALREADY built and placed the door by the time this TAIL
        // runs. Clearing the cache here therefore built a SECOND door -- after which row discovery
        // found three buttons on the row, failed its two-button shape check, and silently stopped
        // laying anything out on every subsequent resize.
        //
        // (An earlier note here blamed "init() running twice without clearing widgets". That is
        // false: Screen.init(int,int) runs init() only when !initialized, and the one path that
        // re-runs it, rebuildWidgets(), calls clearWidgets() first. The guard below is right under
        // either reading, but the stated reason was not.)
        //
        // Instead: keep the cache when our widget is still a live child, and rebuild only when
        // vanilla really has discarded it.
        Screen self = (Screen) (Object) this;
        if (this.globe$door != null && !self.children().contains(this.globe$door)) {
            this.globe$door = null;
            this.globe$create = null;
            this.globe$row = null;
        }
        globe$layOutVanillaDoor();
    }

    /**
     * Re-places the door on every resize.
     *
     * <p>Without this the door freezes at its first-open coordinates while vanilla's own
     * {@code arrangeElements()} re-centres the buttons around it -- so narrowing the window hides it
     * and widening never brings it back. Reported live on the sibling line; the same hook shape is
     * required here.</p>
     */
    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void globe$replaceVanillaDoorOnResize(CallbackInfo ci) {
        globe$layOutVanillaDoor();
    }

    @Unique
    private void globe$layOutVanillaDoor() {
        Screen self = (Screen) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        Button create = this.globe$create;
        if (create == null) {
            Component createLabel = Component.translatable("selectWorld.create");
            for (GuiEventListener child : self.children()) {
                if (child instanceof Button button && createLabel.equals(button.getMessage())) {
                    create = button;
                    break;
                }
            }
            if (create == null) {
                return; // vanilla's footer is not the shape we know; leave it untouched
            }
            this.globe$create = create;
        }

        // Share the row's EXISTING footprint three ways rather than appending past its edge.
        //
        // The previous form asked for width beyond what vanilla's row already claimed and refused
        // when there was none -- which meant the button was simply absent on ordinary windows at a
        // manually-raised GUI scale, where Minecraft's internal width shrinks well below what the
        // window looks like on screen. Reported live and rejected: a control that vanishes is worse
        // than a slightly narrower one. Nothing below is ever requested beyond the row's own width,
        // so there is no window size at which the door can fail to appear.
        List<Button> row = this.globe$row;
        if (row == null) {
            row = new ArrayList<>();
            for (GuiEventListener child : self.children()) {
                // Never count our own door: it shares the row's Y and height, so including it makes
                // the two-button shape check fail and abandons the layout.
                if (child instanceof Button button && button != this.globe$door
                        && button.getY() == create.getY() && button.getHeight() == create.getHeight()) {
                    row.add(button);
                }
            }
            if (row.size() != 2) {
                return; // only the known two-button footer is safe to re-lay
            }
            row.sort(Comparator.comparingInt(AbstractWidget::getX));
            this.globe$row = row;
            // Captured before anything is narrowed; see the field's note on idempotence.
            //
            // MONOTONIC deliberately. If the cache is ever dropped while vanilla's buttons still
            // carry a width THIS layout applied, a plain assignment would capture the narrowed
            // value and the row would ratchet smaller on every such cycle -- the feedback loop that
            // collapsed the sibling line's footer, arriving by a different door. Never accepting a
            // smaller original makes that unreachable rather than merely unobserved.
            this.globe$originalButtonWidth =
                    Math.max(this.globe$originalButtonWidth, create.getWidth());
        }

        // Clamped to the screen: a manual GUI scale can drive self.width far below the 320 that
        // Window.calculateScale only guarantees on Auto, and centring a wider row in it produces a
        // negative left edge, which crashes the first draw with an out-of-bounds scissor.
        int envelope = VanillaFooterLayoutPolicy.fittedEnvelope(
                2 * this.globe$originalButtonWidth + GLOBE_INNER_GAP,
                self.width, VanillaFooterLayoutPolicy.EDGE_MARGIN);
        int rowLeft = VanillaFooterLayoutPolicy.clampedRowLeft(
                self.width, envelope, VanillaFooterLayoutPolicy.EDGE_MARGIN);
        int width = VanillaFooterLayoutPolicy.sharedWidthForThree(envelope, GLOBE_INNER_GAP, GLOBE_GAP);
        int[] xs = VanillaFooterLayoutPolicy.threeButtonXs(rowLeft, width, GLOBE_INNER_GAP, GLOBE_GAP);

        for (int i = 0; i < row.size(); i++) {
            Button button = row.get(i);
            button.setWidth(width);
            button.setX(xs[i]);
        }

        int doorX = xs[2];
        if (this.globe$door == null) {
            Button door = Button.builder(GLOBE_VANILLA_LABEL, button -> globe$openVanillaDirectly(client, self))
                    .bounds(doorX, create.getY(), width, create.getHeight())
                    .tooltip(Tooltip.create(GLOBE_VANILLA_TOOLTIP))
                    .build();
            door.setAlpha(GLOBE_DOOR_ALPHA);
            // Via the invoker on Screen, which DECLARES addRenderableWidget. An @Shadow of it here
            // does not resolve and kills the class load -- that froze this very screen once already.
            ((ScreenAddRenderableWidgetInvoker) self).globe$addRenderableWidget(door);
            this.globe$door = door;
        } else {
            // Re-place the SAME widget. Building another would stack one behind the last on every
            // resize, and re-registering it would leak a widget per resize.
            this.globe$door.setX(doorX);
            this.globe$door.setY(create.getY());
            this.globe$door.setWidth(width);
            this.globe$door.visible = true;
            this.globe$door.active = true;
        }
    }

    /**
     * Opens vanilla's screen and arms a claim so Latitude's redirect leaves it alone.
     *
     * <p>Without the claim the redirect would fire on {@code init} and bounce the player straight
     * back into Latitude's screen -- the exact opposite of pressing this button. The claim carries
     * neither inputs nor a return callback, so vanilla keeps its own default name and seed and its
     * own Cancel: there is no Latitude screen behind this one to offer a way back to.</p>
     *
     * <p>The runnable is both the claim key and the close callback, the same discipline the escape
     * hatch uses -- only the screen opened here can consume the claim.</p>
     */
    @Unique
    private void globe$openVanillaDirectly(Minecraft client, Screen worldList) {
        Runnable backToWorldList = () -> client.setScreenAndShow(worldList);
        VanillaCreateWorldHandoff.armNextWithoutReturn(backToWorldList);
        try {
            CreateWorldScreen.openFresh(client, backToWorldList);
        } catch (RuntimeException exception) {
            // openFresh can throw before showing anything; without this the claim would sit armed
            // for its full TTL and could be consumed by an unrelated later screen.
            VanillaCreateWorldHandoff.cancelNext();
            throw exception;
        }
    }
}
