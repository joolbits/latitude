package com.example.globe.client;

import com.example.globe.core.PassageAxis;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Phase 5 Slice B-5-P2 (Hemisphere Passage) -- the two-button "Heavy fog advisory" prompt. A minimal, bespoke
 * {@link Screen} in the create-screen visual family (parchment browns + gold), opened from
 * {@link HemispherePassageClient#clientTick} exactly once when the player reaches the E/W-edge prompt band.
 *
 * <p><b>Answer wiring.</b> "Pass through" sends {@code PassageAnswerPayload(true)}, raises the crossing curtain
 * ({@link HemispherePassageClient#raiseCurtain}) and closes; "Turn back" sends {@code false} and closes (the
 * server does the gentle inland nudge). ESC == turn back (the design's dismiss-is-turn-back), routed through
 * {@link #onClose()}. Both buttons are disabled after the first click and a re-entrancy guard blocks any second
 * send (sweep HIGH-2: the server has a cooldown, but the client must never double-send).
 *
 * <p><b>Not a pause.</b> {@link #isPauseScreen()} returns false so the integrated server keeps ticking and can
 * process the answer + teleport while the prompt (and then the curtain) is up. Being a {@link Screen} already
 * swallows keybinds, so E/inventory can't leak through to the world.
 */
public final class HemispherePassagePromptScreen extends Screen {

    // Create-screen theme constants (mirrors LatitudeCreateWorldScreen so the prompt reads as the same family).
    private static final int PANEL_BG = 0xF03A302A;     // parchment brown, slightly translucent
    private static final int PANEL_BORDER = 0xFF5C4A3A;
    private static final int GOLD = 0xFFD4A74A;
    private static final int WARM_WHITE = 0xFFEDE0D0;
    private static final int SCRIM = 0x99100C0A;        // soft dim behind the panel

    private static final int PAD = 14;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 8;
    private static final int TITLE_GAP = 10;
    private static final int BODY_GAP = 16;

    private final String bodyLine;
    /** Which edge this prompt belongs to -- selects the answer axis + (POLE) the faster curtain fade-in. */
    private final PassageAxis axis;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int titleY;
    private int bodyY;

    private Button passButton;
    private Button turnButton;

    /** Guards against a double answer (double-click, or ESC after a button): the server also has a cooldown, but
     *  the client must never send two payloads for one prompt. */
    private boolean answered;

    /**
     * B-5 E/W-edge variant: the "Heavy fog advisory" antimeridian crossing prompt.
     *
     * @param beyondEast true if the Eastern Hemisphere lies BEYOND the fog (the crossing mirrors X, so this is
     *                   the opposite hemisphere to the one the player is standing in).
     */
    public HemispherePassagePromptScreen(boolean beyondEast) {
        this("Heavy fog advisory",
                "Beyond this fog lies the " + (beyondEast ? "Eastern" : "Western") + " Hemisphere.",
                PassageAxis.EW);
    }

    /**
     * B-7 pole variant: the "Blizzard advisory" over-the-pole crossing prompt. S10d (owner, TEST 99 crossing
     * legibility): the body names the DESTINATION -- the computed far meridian -- instead of the pole itself,
     * selling the polar-route value ("the far side of the world" is the point of the trip). The label is
     * computed by the CALLER from the player's current X through the SAME shared paths the crossing itself
     * uses ({@code PoleArrivalSearch.antipodalX} -> {@code HemispherePassage.farMeridianLabel}) -- never
     * re-derived math -- so same formula at open-time X; the arrival subtitle names the final truth (sweep #6 LOW: corner-clamp landings can sit ~2-3 deg inward of a promised 180).
     *
     * @param farMeridianLabel the destination meridian label, e.g. {@code "167°W"}
     */
    public static HemispherePassagePromptScreen forPole(String farMeridianLabel) {
        // S10d copy (design-verbatim shape): "...the far side of the world — {deg}."
        return new HemispherePassagePromptScreen("Blizzard advisory",
                "Beyond the whiteout lies the far side of the world — " + farMeridianLabel + ".",
                PassageAxis.POLE);
    }

    private HemispherePassagePromptScreen(String title, String bodyLine, PassageAxis axis) {
        super(Component.literal(title));
        this.bodyLine = bodyLine;
        this.axis = axis;
    }

    @Override
    protected void init() {
        int fh = this.font.lineHeight;
        int titleW = this.font.width(this.getTitle().getString());
        int bodyW = this.font.width(this.bodyLine);

        int passW = Math.max(96, this.font.width("Pass through") + 24);
        // S16(d) COPY SWEEP (owner, TEST 106: "remove ALL 'Turn back' phrasing -- exploration is never scolded").
        // The DECLINE label drops "Turn back" for the neutral "Not now"; the internal axis + one-shot server
        // NUDGE are still named "turn back" (turnButton / the `false` answer / GlobeNet's turn-back nudge) --
        // that vocabulary is code-internal, not player-facing, and stays consistent across client + server.
        int turnW = Math.max(84, this.font.width("Not now") + 24);
        int buttonsW = passW + BTN_GAP + turnW;

        int contentW = Math.max(Math.max(titleW, bodyW), buttonsW);
        panelW = contentW + PAD * 2;
        panelH = PAD + fh + TITLE_GAP + fh + BODY_GAP + BTN_H + PAD;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        titleY = panelY + PAD;
        bodyY = titleY + fh + TITLE_GAP;

        int buttonsY = bodyY + fh + BODY_GAP;
        int buttonsX = panelX + (panelW - buttonsW) / 2;

        this.passButton = Button.builder(Component.literal("Pass through"), b -> answer(true))
                .bounds(buttonsX, buttonsY, passW, BTN_H)
                .build();
        this.turnButton = Button.builder(Component.literal("Not now"), b -> answer(false))
                .bounds(buttonsX + passW + BTN_GAP, buttonsY, turnW, BTN_H)
                .build();
        this.addRenderableWidget(this.passButton);
        this.addRenderableWidget(this.turnButton);
    }

    /** Send the answer once, disable both buttons, and close. "Pass through" also raises the crossing curtain. */
    private void answer(boolean cross) {
        if (answered) {
            return;
        }
        answered = true;
        if (this.passButton != null) this.passButton.active = false;
        if (this.turnButton != null) this.turnButton.active = false;
        HemispherePassageClient.sendAnswer(cross, axis);
        if (cross) {
            HemispherePassageClient.raiseCurtain(axis);
        }
        // Close via onClose -- the `answered` guard above is already set, so onClose's turn-back-on-dismiss path
        // is a no-op and cannot double-send; super.onClose() does the actual screen teardown.
        this.onClose();
    }

    @Override
    public void onClose() {
        // ESC / dismiss == turn back (design: dismiss-is-turn-back). Only if no button was clicked.
        if (!answered) {
            answered = true;
            HemispherePassageClient.sendAnswer(false, axis);
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // keep the server ticking so it can process the answer + teleport under the curtain.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Soft dim behind the panel (belt + suspenders over the harness's own in-game background).
        context.fill(0, 0, this.width, this.height, SCRIM);

        // Panel + 1px gold-brown frame.
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        context.fill(panelX, panelY, panelX + panelW, panelY + 1, PANEL_BORDER);
        context.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, PANEL_BORDER);
        context.fill(panelX, panelY, panelX + 1, panelY + panelH, PANEL_BORDER);
        context.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, PANEL_BORDER);

        int cx = panelX + panelW / 2;
        String title = this.getTitle().getString();
        context.text(this.font, title, cx - this.font.width(title) / 2, titleY, GOLD, true);
        context.text(this.font, this.bodyLine, cx - this.font.width(this.bodyLine) / 2, bodyY, WARM_WHITE, false);

        // Renders the two buttons (added via addRenderableWidget).
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
