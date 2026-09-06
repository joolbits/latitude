package com.example.globe.client.create;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The escape hatch must not pay for a datapack reload it does not owe, and must not drift from the
 * settings the Latitude screen would have used.
 *
 * <p>Static source assertions, because the thing being protected is a call-site choice whose only
 * symptom is a visible freeze -- nothing throws, nothing fails, the screen simply takes a second
 * longer and a human has to notice. That is precisely the kind of regression a test should hold.</p>
 */
public final class VanillaHandoffCostPolicyTest {
    private static final String SCREEN =
            "src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java";
    private static final String REDIRECT_MIXIN =
            "src/main/java/com/example/globe/mixin/client/CreateWorldScreenInitRedirectMixin.java";
    private static final String HANDOFF =
            "src/main/java/com/example/globe/client/create/VanillaCreateWorldHandoff.java";
    private static final String DOOR_MIXIN =
            "src/main/java/com/example/globe/mixin/client/SelectWorldScreenVanillaDoorMixin.java";

    private static int assertions;

    private VanillaHandoffCostPolicyTest() {
    }

    public static void run() throws IOException {
        hatchReusesTheLoadedContextInsteadOfReloading();
        hatchCarriesTheSameGameModeMappingAsWorldCreation();
        handoffClearsTheRecreatedFlag();
        returnPathReadsVanillaStateBackLive();
        noOrdinaryCodeCastsToAMixinClass();
        exitCallbackIsThreadedThroughTheHandoff();
        exitCallbackIsCapturedLiveNotAtOutboundTime();
        footerRelabelsCancelAndAddsADistinctExitButton();
        footerBailsOutOnEitherUnrecognisedShape();
        onCloseAndTheHatchExitShareOneGuardedImplementation();
        screenAddRenderableWidgetInvokerIsAnInterface();
        doorArmsWithoutAPayloadRatherThanEmptyStrings();
        redirectSkipsNameAndSeedWritesWhenPayloadFieldsAreNull();
        doorOpensFreshRatherThanReusingAContext();
        doorNeverWritesToTheEscapeHatchFooter();
        doorNarrowsAllThreeButtonsRatherThanRefusing();
        doorAnchorsOnlyToWidgetsItNeverMutates();
        doorFindsPlayByFieldNotByLabel();
        bothFootersClampToTheScreenRatherThanAssumingAFloor();
        doorHidesOnAnUnrecognisedFooterShape();
        doorAlsoLaysOutOnRepositionNotJustInit();
        doorWidgetIsCachedNotReAdded();
        footerCachesWidgetsRatherThanRediscoveringByMessage();
        footerRepositionsAllThreeButtonsUnconditionally();
        footerAlsoLaysOutOnRepositionNotJustInit();
        everyGuardInThisFileIsActuallyRun();
        System.out.println("PASS VanillaHandoffCostPolicyTest assertions=" + assertions);
    }

    /**
     * The round trip must be two-way. The outbound carry is a snapshot taken when the button is
     * pressed and cannot see anything the player changes on vanilla's own controls afterwards, so
     * without a read-back their edits are silently discarded on return -- reported live as
     * "Hardcore does not survive". Reading must happen in the return callback, where vanilla's
     * screen is still showing, not eagerly at capture time.
     */
    private static void returnPathReadsVanillaStateBackLive() throws IOException {
        String hatch = methodSection(read(SCREEN), "private void openOtherWorldTypes()");
        assertTrue(hatch.contains("absorbVanillaCreateState("),
                "the return path takes vanilla's state back");
        assertTrue(hatch.contains("instanceof VanillaCreateWorldUiStateCarrier"),
                "it reaches that state through the carrier interface");
        int read = hatch.indexOf("absorbVanillaCreateState(");
        int handBack = hatch.indexOf("setScreen(this)");
        assertTrue(read >= 0 && handBack > read,
                "the read-back happens BEFORE control returns to Latitude, while vanilla's screen "
                        + "is still the live one");

        String absorb = methodSection(read(SCREEN), "private void absorbVanillaCreateState(");
        for (String field : new String[]{
                "worldNameInput", "seedInput", "allowCommands", "selectedDifficulty",
                "bonusChest", "generateStructures", "gameRules", "selectedModeIdx"}) {
            assertTrue(absorb.contains(field),
                    "every field carried outbound is also read back: " + field);
        }
    }

    /**
     * Casting to a registered {@code @Mixin} CLASS from ordinary code throws
     * {@code IllegalClassLoadError} the first time the line runs -- a clean compile proves nothing.
     * The same cast is legal inside another mixin, which is what makes it easy to introduce by
     * copying a line that works. Interface mixins are exempt: accessor/carrier interfaces exist
     * precisely to be cast to from outside.
     *
     * <p>Scope is DISCOVERED by walking the mixin tree, never a hand-written list, and carries a
     * floor plus a positive control so a walk that finds nothing cannot report a pass.</p>
     */
    private static void noOrdinaryCodeCastsToAMixinClass() throws IOException {
        Path mixinRoot = Path.of("src/main/java/com/example/globe/mixin");
        java.util.Set<String> mixinClasses = new java.util.TreeSet<>();
        try (var walk = Files.walk(mixinRoot)) {
            for (Path file : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String body = read(file.toString());
                if (!body.contains("@Mixin")) continue;
                // Interfaces are the SAFE form -- they are what this rule tells you to use instead.
                if (java.util.regex.Pattern.compile("\\binterface\\s+\\w+").matcher(body).find()) continue;
                mixinClasses.add(file.getFileName().toString().replace(".java", ""));
            }
        }
        assertTrue(mixinClasses.size() >= 20,
                "mixin-class walk found implausibly few classes (" + mixinClasses.size()
                        + "); a broken walk would vacuously pass this guard");
        assertTrue(mixinClasses.contains("CreateWorldScreenMixin"),
                "positive control: a known @Mixin class is in the discovered set, so a miss here "
                        + "would be a real finding rather than an empty scan");

        java.util.List<String> offenders = new java.util.ArrayList<>();
        int scanned = 0;
        int excluded = 0;
        try (var walk = Files.walk(Path.of("src/main/java"))) {
            for (Path file : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                // Mixin-to-mixin casts are legal and this codebase relies on one, so the mixin tree
                // is exempt -- but see the floor below.
                if (file.toString().contains("/mixin/")) {
                    excluded++;
                    continue;
                }
                scanned++;
                String body = read(file.toString());
                for (String mixin : mixinClasses) {
                    // Allow an optional package prefix. Matching the simple name alone misses
                    // `((com.example.globe.mixin.client.FooMixin) x)` entirely -- verified by
                    // planting exactly that and watching the first version of this guard pass it.
                    if (java.util.regex.Pattern
                            .compile("\\(\\s*(?:[\\w.]*\\.)?" + mixin + "\\s*\\)")
                            .matcher(body).find()) {
                        offenders.add(file.getFileName() + " casts to " + mixin);
                    }
                }
            }
        }
        assertTrue(scanned >= 50,
                "ordinary-source walk scanned implausibly few files (" + scanned + ")");
        // An exclusion that matches nothing fails OPEN, and nothing about a passing run reveals it:
        // the guard simply looks thorough. Floor the exclusion the same way the walk is floored, so
        // a path that stops matching is a failure rather than a silent widening.
        assertTrue(excluded >= 20,
                "the mixin-directory exclusion excluded implausibly few files (" + excluded
                        + "); an exclusion resolving to nothing would make this guard look "
                        + "thorough while checking the wrong set");
        assertTrue(offenders.isEmpty(),
                "ordinary code must not cast to a @Mixin class -- it is an IllegalClassLoadError at "
                        + "runtime, not a compile error. Use a carrier interface the mixin "
                        + "implements. Offenders: " + offenders);
    }

    /**
     * openFresh runs a full WorldLoader pass and applies every recipe and advancement on the render
     * thread. The hatch already holds the context that load would rebuild, so it must not ask for
     * another one.
     */
    private static void hatchReusesTheLoadedContextInsteadOfReloading() throws IOException {
        String hatch = methodSection(read(SCREEN), "private void openOtherWorldTypes()");
        assertTrue(hatch.contains("CreateWorldScreen.createFromExisting("),
                "the hatch reuses the already-loaded world creation context");
        assertTrue(!hatch.contains("openFresh"),
                "the hatch must not call openFresh -- it re-runs the whole datapack load and "
                        + "applies recipes and advancements on the render thread, which is a "
                        + "visible freeze the screen does not owe");
        assertTrue(hatch.contains("this.holder"),
                "the context handed over is this screen's own, not a freshly built one");
    }

    /**
     * Two call sites now translate the mode stepper into vanilla types. They must agree: a drift
     * would silently hand the vanilla screen a different game mode than the one on display.
     */
    private static void hatchCarriesTheSameGameModeMappingAsWorldCreation() throws IOException {
        String source = read(SCREEN);
        String hatch = methodSection(source, "private void openOtherWorldTypes()");
        String creation = methodSection(source, "private void beginExpedition()");

        for (String rule : new String[]{
                "selectedModeIdx == 2 ? GameType.CREATIVE : GameType.SURVIVAL",
                "selectedModeIdx == 1",
        }) {
            assertTrue(normalize(hatch).contains(normalize(rule)),
                    "the hatch derives game mode with: " + rule);
            assertTrue(normalize(creation).contains(normalize(rule)),
                    "world creation derives game mode with the SAME rule: " + rule);
        }
        assertTrue(normalize(hatch).contains(normalize("hardcore ? Difficulty.HARD"))
                        && normalize(creation).contains(normalize("hardcore ? Difficulty.HARD")),
                "both paths force hard difficulty for hardcore identically");
    }

    /**
     * createFromExisting exists for Re-Create and marks the session recreated unconditionally.
     * Vanilla reads that flag once, in onCreate, to decide whether world creation may skip its
     * confirmation. A world made through the hatch is new, so leaving it set would show a prompt
     * vanilla's own fresh path does not.
     */
    private static void handoffClearsTheRecreatedFlag() throws IOException {
        String mixin = read(REDIRECT_MIXIN);
        String claim = mixin.substring(mixin.indexOf("if (handoff.isPresent())"));
        claim = claim.substring(0, claim.indexOf("return;"));
        assertTrue(normalize(claim).contains("this.recreated = false;"),
                "claiming the handoff clears the recreated flag that createFromExisting sets");
    }

    /**
     * The full one-click exit needs a second thing carried alongside name/seed: a way to abandon
     * the whole flow from vanilla's screen. Without it the escape hatch can only ever return to
     * Latitude, never leave create-world entirely in one step.
     */
    private static void exitCallbackIsThreadedThroughTheHandoff() throws IOException {
        String hatch = methodSection(read(SCREEN), "private void openOtherWorldTypes()");
        assertTrue(hatch.contains("exitCreateFlow"),
                "the hatch builds an exit-the-whole-flow callback");
        int exitBuilt = hatch.indexOf("Runnable exitCreateFlow");
        int armed = hatch.indexOf("VanillaCreateWorldHandoff.armNext(");
        assertTrue(exitBuilt >= 0 && armed > exitBuilt,
                "the exit callback is built before it is armed into the handoff");
        // 1.21.1's CreateWorldScreen takes a Screen, so the claim key is the RunnableReturnScreen
        // that wraps returnToLatitude rather than the callback itself. The exit callback's position
        // is what this guards, and that is unchanged.
        assertTrue(hatch.substring(armed).startsWith("VanillaCreateWorldHandoff.armNext(returnScreen, "
                        + "this.worldNameInput, this.seedInput, exitCreateFlow);"),
                "armNext receives the exit callback as its fourth argument");

        String mixin = read(REDIRECT_MIXIN);
        String claim = mixin.substring(mixin.indexOf("if (handoff.isPresent())"));
        claim = claim.substring(0, claim.indexOf("return;"));
        assertTrue(normalize(claim).contains("globe$exitCreateFlow = payload.exitCreateFlow()"),
                "claiming the handoff stashes the exit callback for the footer to use later");
    }

    /**
     * The exit callback must read {@code minecraft.screen} at the moment it RUNS (click time), not
     * at the moment {@code openOtherWorldTypes} builds it -- at build time the showing screen is
     * still Latitude's own, and passing that as {@code expectedCurrent} would make
     * {@code leaveCreateFlowFrom}'s guard compare vanilla's screen against Latitude's, which never
     * matches. Guarded by requiring the read to appear inside a lambda body, not before it.
     */
    private static void exitCallbackIsCapturedLiveNotAtOutboundTime() throws IOException {
        String hatch = methodSection(read(SCREEN), "private void openOtherWorldTypes()");
        int lambdaArrow = hatch.indexOf("exitCreateFlow = () ->");
        assertTrue(lambdaArrow >= 0, "the exit callback is a lambda, not a precomputed value");
        int screenRead = hatch.indexOf("this.minecraft.screen", lambdaArrow);
        assertTrue(screenRead > lambdaArrow,
                "minecraft.screen is read INSIDE the lambda body, i.e. at click time");
    }

    /**
     * The footer itself: Cancel is relabelled rather than rewired (it already returns to Latitude,
     * it just never said so), and a genuinely new button carries the full exit.
     */
    private static void footerRelabelsCancelAndAddsADistinctExitButton() throws IOException {
        String footer = methodSection(read(REDIRECT_MIXIN), "private void globe$layOutEscapeHatchFooter(");
        assertTrue(footer.contains("cancel.setMessage(GLOBE_BACK_TO_LATITUDE)"),
                "vanilla's own Cancel button is relabelled, not replaced");
        assertTrue(footer.contains("globe$addRenderableWidget("),
                "a genuinely new widget is added for the full exit");
        String newButton = footer.substring(footer.indexOf("Button.builder(CommonComponents.GUI_CANCEL"));
        assertTrue(newButton.contains("exit.run()"),
                "the new button's press handler runs the exit-the-whole-flow callback");
        assertTrue(footer.contains("VanillaFooterLayoutPolicy.buttonWidth(")
                        && footer.contains("VanillaFooterLayoutPolicy.buttonXFrom("),
                "widths and positions come from the shared, unit-tested layout policy");
    }

    /**
     * An unrecognised footer shape must leave vanilla's screen untouched rather than mangling a
     * layout this code does not actually understand -- Mojang changing the footer, or another mod
     * having already altered it. Two distinct guards, two distinct failure modes: no Cancel button
     * found at all, or a footer that isn't exactly the two-button row this code expects.
     */
    private static void footerBailsOutOnEitherUnrecognisedShape() throws IOException {
        String footer = methodSection(read(REDIRECT_MIXIN), "private void globe$layOutEscapeHatchFooter(");
        assertTrue(footer.contains("if (cancel == null) {\n                return;\n            }"),
                "bails out if vanilla's own Cancel button cannot be found at all");
        assertTrue(footer.contains("if (row.size() != 2) {\n                return;\n            }"),
                "bails out if the button row is not exactly the two-button shape this code expects");
    }

    /**
     * Discovery (finding the row by vanilla's Cancel MESSAGE) can only succeed once: the very first
     * successful run relabels Cancel, so its own message would never match a second search. Widget
     * references must be cached specifically so repositioning on later resizes does not depend on
     * rediscovering a row that has already changed its own search key.
     */
    private static void footerCachesWidgetsRatherThanRediscoveringByMessage() throws IOException {
        String footer = methodSection(read(REDIRECT_MIXIN), "private void globe$layOutEscapeHatchFooter(");
        assertTrue(footer.contains("if (this.globe$cancelWidget == null) {"),
                "discovery by message runs only when nothing has been cached yet");
        int discoveryStart = footer.indexOf("if (this.globe$cancelWidget == null) {");
        int discoveryDepth = 0;
        int discoveryEnd = discoveryStart;
        for (int i = footer.indexOf('{', discoveryStart); i < footer.length(); i++) {
            char c = footer.charAt(i);
            if (c == '{') discoveryDepth++;
            else if (c == '}' && --discoveryDepth == 0) {
                discoveryEnd = i + 1;
                break;
            }
        }
        String discovery = footer.substring(discoveryStart, discoveryEnd);
        assertTrue(discovery.contains("this.globe$cancelWidget = cancel;")
                        && discovery.contains("this.globe$otherRowWidget ="),
                "both row widgets are cached once discovery succeeds");
        String afterDiscovery = footer.substring(discoveryEnd);
        assertTrue(!afterDiscovery.contains("CommonComponents.GUI_CANCEL.equals(button.getMessage())"),
                "nothing after the cached-or-discover block re-searches for Cancel by message");
    }

    /**
     * The footer's own version of {@link #doorAlsoLaysOutOnRepositionNotJustInit}: the same root
     * cause (a hook placed only at init's TAIL fires exactly once per screen instance) applies to
     * this mixin identically, and it must be guarded the same way.
     */
    private static void footerAlsoLaysOutOnRepositionNotJustInit() throws IOException {
        String source = read(REDIRECT_MIXIN);
        assertTrue(source.contains("@Inject(method = \"init\", at = @At(\"TAIL\"))"),
                "still hooks init's TAIL, for the screen's first construction");
        assertTrue(source.contains("@Inject(method = \"repositionElements\", at = @At(\"TAIL\"))"),
                "ALSO hooks repositionElements's TAIL -- the path every actual window resize takes");

        String initInject = methodSection(source, "private void globe$onInit(");
        String repositionInject = methodSection(source, "private void globe$onReposition(");
        assertTrue(normalize(initInject).contains("globe$layOutEscapeHatchFooter();")
                        && normalize(repositionInject).contains("globe$layOutEscapeHatchFooter();"),
                "both entry points delegate to the SAME layout method, not two diverging copies");
    }

    /**
     * All three buttons must be repositioned on EVERY call, not only the first -- vanilla's own
     * {@code arrangeElements} re-centres the two ORIGINAL buttons on every resize (they are part of
     * its layout tree), so a one-time-only reposition would let them drift back toward vanilla's own
     * spacing while the third, non-tree-managed button stayed frozen at stale coordinates.
     */
    private static void footerRepositionsAllThreeButtonsUnconditionally() throws IOException {
        String footer = methodSection(read(REDIRECT_MIXIN), "private void globe$layOutEscapeHatchFooter(");
        int cachedBlockEnd = footer.indexOf("this.globe$exitWidget = Button.builder");
        assertTrue(cachedBlockEnd >= 0, "the exit widget is constructed once, during discovery");
        String afterCaching = footer.substring(footer.indexOf('}', cachedBlockEnd));
        assertTrue(afterCaching.contains("VanillaFooterLayoutPolicy.buttonWidth(")
                        && afterCaching.contains("VanillaFooterLayoutPolicy.buttonXFrom("),
                "geometry is recomputed after the cached-or-discover block, not only inside it");
        assertTrue(afterCaching.contains("this.globe$exitWidget.setX(")
                        && afterCaching.contains("this.globe$exitWidget.setY("),
                "the exit widget's own position is refreshed on every call, not just when created");
    }

    /**
     * {@link LatitudeCreateWorldScreen#onClose()} and the hatch's exit callback must share one
     * implementation. Two independent copies of "leave the create flow" is exactly the kind of
     * drift that let the round-trip bug happen in the first place -- one path updated, the other
     * quietly left behind.
     */
    private static void onCloseAndTheHatchExitShareOneGuardedImplementation() throws IOException {
        String source = read(SCREEN);
        String onClose = methodSection(source, "public void onClose()");
        assertTrue(normalize(onClose).contains("leaveCreateFlowFrom(this)"),
                "onClose() delegates to the shared leave-the-flow method, passing itself as the "
                        + "expected current screen");

        String leave = methodSection(source, "private void leaveCreateFlowFrom(");
        assertTrue(leave.contains("this.minecraft.screen == expectedCurrent")
                        && leave.contains("this.minecraft.screen == null"),
                "the shared method only navigates if the expected screen is still showing, or "
                        + "nothing is -- otherwise it would fight over minecraft.screen with "
                        + "whatever took over in the meantime");

        String hatch = methodSection(source, "private void openOtherWorldTypes()");
        assertTrue(hatch.contains("leaveCreateFlowFrom(this.minecraft == null ? null : this.minecraft.screen)"),
                "the hatch's exit callback also delegates to the shared method, passing WHATEVER "
                        + "is showing at click time -- not `this`, since Latitude's screen is not "
                        + "what's showing when this callback actually runs");
    }

    /**
     * The whole reason this cast is safe from ordinary code: it targets an INTERFACE, not the
     * registered @Mixin class. Casting to the class would compile identically and throw
     * IllegalClassLoadError at runtime -- see noOrdinaryCodeCastsToAMixinClass, which this pin
     * complements by asserting the safe form is what actually exists.
     */
    private static void screenAddRenderableWidgetInvokerIsAnInterface() throws IOException {
        String source = read(
                "src/main/java/com/example/globe/mixin/client/ScreenAddRenderableWidgetInvoker.java");
        assertTrue(java.util.regex.Pattern.compile("\\binterface\\s+ScreenAddRenderableWidgetInvoker\\b")
                        .matcher(source).find(),
                "ScreenAddRenderableWidgetInvoker must be an interface, not a class -- that is what "
                        + "makes casting to it from the mixin (or anywhere else) safe");
    }

    /**
     * The world-list door must arm with genuinely absent name/seed, not empty strings. Empty
     * strings are not a harmless default here: the redirect writes them unconditionally unless the
     * field is null, and vanilla's create screen already has its own default name filled in by the
     * time that write would run -- an empty string would BLANK it rather than leave it alone.
     */
    private static void doorArmsWithoutAPayloadRatherThanEmptyStrings() throws IOException {
        String door = methodSection(read(DOOR_MIXIN), "private static void globe$openVanillaDoor(");
        assertTrue(door.contains("VanillaCreateWorldHandoff.armNextWithoutReturn("),
                "the door arms through the no-payload entry point");
        assertTrue(!door.contains("armNext(") || door.contains("armNextWithoutReturn("),
                "the door must not call the name/seed/exit-carrying armNext -- it has none of those");
        assertTrue(door.contains("VanillaCreateWorldHandoff.cancelNext();"),
                "a failed open cancels the claim rather than leaving it armed for the next unrelated "
                        + "create screen");
    }

    /**
     * The read side of the same contract: a null field must actually be skipped, not written as
     * whatever null happens to render as.
     */
    private static void redirectSkipsNameAndSeedWritesWhenPayloadFieldsAreNull() throws IOException {
        String mixin = read(REDIRECT_MIXIN);
        String claim = mixin.substring(mixin.indexOf("if (handoff.isPresent())"));
        claim = claim.substring(0, claim.indexOf("return;"));
        String normalized = normalize(claim);
        assertTrue(normalized.contains("if (payload.worldName() != null) { self.getUiState().setName(payload.worldName()); }"),
                "the name write is skipped, not defaulted, when the payload carries no name");
        assertTrue(normalized.contains("if (payload.seed() != null) { self.getUiState().setSeed(payload.seed()); }"),
                "the seed write is skipped, not defaulted, when the payload carries no seed");

        String handoff = read(HANDOFF);
        String armWithoutReturn = methodSection(handoff, "public static void armNextWithoutReturn(");
        assertTrue(normalize(armWithoutReturn).contains("arm(claimKey, null, null, null)"),
                "arming without a return genuinely passes null, not empty strings, for every field");
    }

    /**
     * The door is the FIRST entry into world creation for this session -- unlike the hatch, there is
     * no already-loaded WorldCreationContext to reuse, so it must load fresh. Calling
     * createFromExisting here would be wrong, not merely slower: there is nothing to reuse.
     */
    private static void doorOpensFreshRatherThanReusingAContext() throws IOException {
        String door = methodSection(read(DOOR_MIXIN), "private static void globe$openVanillaDoor(");
        // 1.21.1's openFresh takes the Screen to return to, so the door hands it the world list
        // directly instead of a callback that would re-open it.
        assertTrue(door.contains("CreateWorldScreen.openFresh(client, worldList)"),
                "the door opens vanilla fresh -- there is no context of its own to reuse");
        assertTrue(!door.contains("createFromExisting"),
                "the door must not call createFromExisting; unlike the hatch it has nothing to carry");
    }

    /**
     * The trap this whole feature exists to avoid: "Back to Latitude" pointing at nothing, because
     * a session opened from the world list has no Latitude screen behind it. The footer method
     * itself already guards on {@code exit == null} and the door never sets an exit callback, so
     * this pins that the door does not need to -- and never gains -- its own copy of that guard.
     */
    private static void doorNeverWritesToTheEscapeHatchFooter() throws IOException {
        String door = read(DOOR_MIXIN);
        assertTrue(!door.contains("globe$layOutEscapeHatchFooter") && !door.contains("BACK_TO_LATITUDE"),
                "the door does not touch the escape-hatch footer at all -- it relies on that "
                        + "method's existing exit==null guard rather than adding a second one");
    }

    /**
     * The redesign this whole file's naming still remembers: the old appended-with-refuse
     * approach was reported live as absent at a window that looked perfectly ordinary on screen
     * (a manually-set high GUI Scale reaches "no room" far earlier than the window's own on-screen
     * size suggests). Replaced with narrowing all three buttons to share the row's OWN existing
     * footprint -- reusing {@code VanillaFooterLayoutPolicy}'s already-tested arithmetic rather than
     * a second, door-specific copy of the same idea.
     */
    private static void doorNarrowsAllThreeButtonsRatherThanRefusing() throws IOException {
        String door = methodSection(read(DOOR_MIXIN), "private void globe$layOutVanillaDoor(");
        assertTrue(!door.contains("createXForFit"),
                "the old refuse-based fit-check is gone, not merely unreachable");
        assertTrue(door.contains("VanillaFooterLayoutPolicy.buttonWidth(3, envelope)"),
                "narrows to a 3-way split of the row's own envelope, the same policy the "
                        + "escape-hatch footer already uses for its own 2-to-3 narrowing");
        assertTrue(door.contains("play.setWidth(width)") && door.contains("create.setWidth(width)"),
                "BOTH Play and Create are narrowed, not just Create -- a real behaviour change from "
                        + "the earlier design, which never touched Play at all");
    }

    /**
     * THE REGRESSION PIN. An earlier version of this guard asserted the exact opposite -- that the
     * envelope be re-measured live on every pass -- and it passed while shipping the defect it was
     * meant to prevent.
     *
     * <p>Vanilla's footer is a {@code GridLayout} ({@code createFooterButtons} ->
     * {@code createRowHelper(4)}, Play and Create added as 2-column-span children), and a
     * GridLayout derives its column widths FROM its children's current widths, re-arranging on
     * every {@code repositionElements()}. Narrowing Play and Create therefore feeds back into
     * vanilla's own arrangement, so a live re-measure reads a row this code shrank on the pass
     * before: 308 -> 202 -> 132 downward. Reported live as a clipped "Play Selected World" label
     * and a row visibly misaligned with the four buttons beneath it.</p>
     *
     * <p>The general law, which is what this pin is really protecting: <b>a layout pass that
     * derives its input from widgets it also mutates is unsound regardless of its bounds
     * checking.</b> No clamp closes it, because the clamped value is itself the previous clamped
     * pass's output.</p>
     */
    private static void doorAnchorsOnlyToWidgetsItNeverMutates() throws IOException {
        String door = methodSection(read(DOOR_MIXIN), "private void globe$layOutVanillaDoor(");
        assertTrue(door.contains(
                        "int envelope = VanillaFooterLayoutPolicy.fittedEnvelope(otherRowRight - otherRowLeft, self.width);"),
                "the row's span comes from the FOUR-button row below, which this method never writes to");
        assertTrue(door.contains("int left = Math.max(VanillaFooterLayoutPolicy.SCREEN_MARGIN, otherRowLeft);"),
                "and so does its left edge, which is what makes the two rows share an edge by "
                        + "construction instead of by arithmetic");
        assertTrue(door.contains("button != this.globe$doorButton"),
                "the scan for that row excludes our own door, or the door would help define the "
                        + "envelope it is then laid out inside -- reintroducing the feedback loop");

        // THE LOAD-BEARING NEGATIVE, and the one that would have caught BOTH live failures.
        assertTrue(!door.contains("play.getX()") && !door.contains("create.getX()"),
                "NOTHING is measured off Play or Create. Vanilla's footer is a GridLayout whose "
                        + "column widths derive from its children's widths, and this method resizes "
                        + "both of those children -- so any measurement taken from them is an output "
                        + "of the previous pass, not an input. Measuring the envelope that way "
                        + "collapsed the row (308 -> 202 -> 132); anchoring the left edge to "
                        + "play.getX() made it drift during a resize, because the grid centres a "
                        + "narrowed child inside its two-column slot");
        assertTrue(!door.contains("centreX"),
                "and no computed screen centre either -- that was the version whose left edge could "
                        + "go negative");
        assertTrue(door.contains("VanillaFooterLayoutPolicy.buttonWidth(3, envelope)"),
                "still narrows to a 3-way split, so the door stays always-present rather than "
                        + "reverting to appending a fourth button past Create's edge");
    }

    /**
     * Play must come from vanilla's own field, never from matching its label. Reported live as the
     * door vanishing while the narrowing stayed put; a runtime probe showed the first pass laying
     * the door out correctly and the next reporting {@code play=false, create=true}, so the door
     * hid itself while the already-narrowed buttons kept their new widths.
     *
     * <p>{@code SelectWorldScreen.updateButtonStatus} calls {@code selectButton.setMessage(...)}
     * every time the world selection changes, so a label match on that button is guaranteed to stop
     * matching the moment a world is selected. Create's label is never rewritten, which is why
     * matching IT by message is still sound -- the asymmetry is the point. <b>Match a widget by
     * message only where nothing rewrites that message.</b></p>
     */
    private static void doorFindsPlayByFieldNotByLabel() throws IOException {
        String source = read(DOOR_MIXIN);
        String door = methodSection(source, "private void globe$layOutVanillaDoor(");
        assertTrue(source.contains("@Shadow") && source.contains("private Button selectButton;"),
                "vanilla's own Play field is shadowed rather than rediscovered");
        assertTrue(door.contains("Button play = this.selectButton;"),
                "and it is what the layout uses for Play");
        assertTrue(!door.contains("PLAY_WORLD"),
                "the label match on Play is GONE, not merely supplemented -- it is unconditionally "
                        + "broken by vanilla's own selection handling");
        assertTrue(door.contains("Component.translatable(\"selectWorld.create\").equals(button.getMessage())"),
                "Create is still found by message, which is sound because nothing rewrites it");
    }

    /**
     * 320 is not a minimum scaled width, and both footer features were once proved correct against
     * the claim that it is. {@code Window.calculateScale} tests {@code >= 320} only inside the
     * guard on INCREASING the GUI scale, so a window narrower than 320 never enters that loop, the
     * scale stays 1, and the scaled width equals the window width. Both layouts must therefore
     * clamp to the actual screen rather than assume a floor.
     */
    private static void bothFootersClampToTheScreenRatherThanAssumingAFloor() throws IOException {
        String door = methodSection(read(DOOR_MIXIN), "private void globe$layOutVanillaDoor(");
        String footer = methodSection(read(REDIRECT_MIXIN), "private void globe$layOutEscapeHatchFooter(");
        assertTrue(door.contains("VanillaFooterLayoutPolicy.fittedEnvelope("),
                "the world-list door clamps its envelope to the screen");
        assertTrue(footer.contains("VanillaFooterLayoutPolicy.fittedEnvelope(self.width)"),
                "the escape-hatch footer clamps its envelope to the screen -- this is the site with "
                        + "the genuinely reachable crash, since its row width was a fixed constant");
        assertTrue(footer.contains("VanillaFooterLayoutPolicy.rowLeft(count, width, self.width)"),
                "and its left edge cannot go negative, which is what the renderer rejects outright "
                        + "once a narrowed button's label needs a scissor");
        assertTrue(!footer.contains("self.width / 2"),
                "the unclamped centre that produced the negative left edge is gone, not merely "
                        + "guarded downstream");
    }

    /**
     * An unrecognised footer shape (Play or Create not found by message) must hide the door and
     * leave vanilla's own row untouched, exactly like every other unrecognised-shape bail-out in
     * this codebase -- rather than narrowing a layout this code does not actually understand.
     */
    private static void doorHidesOnAnUnrecognisedFooterShape() throws IOException {
        String door = methodSection(read(DOOR_MIXIN), "private void globe$layOutVanillaDoor(");
        assertTrue(door.contains("if (play == null || create == null) {\n            globe$hideDoor();\n            return;\n        }"),
                "missing either vanilla button hides the door rather than narrowing around a "
                        + "layout this code cannot actually confirm");
    }

    /**
     * Reported live: the door failed to reappear after the window was narrowed and then widened
     * back out. Root cause: {@code Screen.resize(int,int)} calls only {@code repositionElements()},
     * never {@code init()} again (a hidden {@code initialized} flag routes every call after the
     * first away from init entirely) -- so a hook placed only at init's TAIL fires exactly once per
     * screen instance and is blind to every later resize.
     */
    private static void doorAlsoLaysOutOnRepositionNotJustInit() throws IOException {
        String source = read(DOOR_MIXIN);
        assertTrue(source.contains("@Inject(method = \"init\", at = @At(\"TAIL\"))"),
                "still hooks init's TAIL, for the screen's first construction");
        assertTrue(source.contains("@Inject(method = \"repositionElements\", at = @At(\"TAIL\"))"),
                "ALSO hooks repositionElements's TAIL -- the path every actual window resize takes");

        String initInject = methodSection(source, "private void globe$onInit(");
        String repositionInject = methodSection(source, "private void globe$onReposition(");
        assertTrue(normalize(initInject).contains("globe$layOutVanillaDoor();")
                        && normalize(repositionInject).contains("globe$layOutVanillaDoor();"),
                "both entry points delegate to the SAME layout method, not two diverging copies");
    }

    /**
     * The door must be reused, not re-added, on every layout pass -- naively calling
     * {@code addRenderableWidget} again on every resize would pile up a fresh button behind the
     * last one, since {@code repositionElements} fires on every resize (see
     * doorAlsoLaysOutOnRepositionNotJustInit) and nothing else would ever remove the earlier ones.
     */
    private static void doorWidgetIsCachedNotReAdded() throws IOException {
        String source = read(DOOR_MIXIN);
        assertTrue(source.contains("private Button globe$doorButton;"),
                "the door widget reference is cached in a field");
        String layout = methodSection(source, "private void globe$layOutVanillaDoor(");
        assertTrue(layout.contains("if (this.globe$doorButton == null) {"),
                "addRenderableWidget is called only when nothing has been cached yet");
        assertTrue(layout.contains("globe$doorButton.setX(doorX)") && layout.contains("globe$doorButton.visible = true"),
                "an already-cached door is repositioned and re-shown, not reconstructed");

        String hide = methodSection(source, "private void globe$hideDoor(");
        assertTrue(hide.contains("visible = false") && hide.contains("active = false"),
                "hiding sets both visible and active, matching this codebase's own established "
                        + "idiom for toggling a widget rather than removing it");
    }

    /**
     * Self-audit: every {@code the*()}-style guard method declared in this file must actually be
     * called from {@link #run()}. A guard that is declared, forgotten, and never wired reports a
     * PASS while checking nothing -- the assertion count is the only thing that would look off, and
     * only if someone happens to compare it to the count before. Recurred once already today on the
     * sibling line; closing it here before it recurs a second time.
     */
    private static void everyGuardInThisFileIsActuallyRun() throws IOException {
        String source = read("src/clipPolicyTest/java/com/example/globe/client/create/VanillaHandoffCostPolicyTest.java");
        // The FULL run() body, not truncated at this method's own call site -- a guard call added
        // AFTER this one in source order must still be seen, or the audit has the same blind spot
        // it exists to close.
        String runBody = methodSection(source, "public static void run() throws IOException {");

        java.util.regex.Matcher declarations = java.util.regex.Pattern
                .compile("private static void (\\w+)\\(\\) throws IOException \\{")
                .matcher(source);
        java.util.Set<String> guards = new java.util.TreeSet<>();
        while (declarations.find()) {
            String name = declarations.group(1);
            if (!name.equals("run") && !name.equals("everyGuardInThisFileIsActuallyRun")) {
                guards.add(name);
            }
        }
        assertTrue(guards.size() >= 10,
                "guard discovery found implausibly few methods (" + guards.size()
                        + "); a broken discovery pattern would vacuously pass this check");

        java.util.List<String> unwired = new java.util.ArrayList<>();
        for (String guard : guards) {
            if (!runBody.contains(guard + "();")) {
                unwired.add(guard);
            }
        }
        assertTrue(unwired.isEmpty(),
                "guard(s) declared but never called from run(), reporting a pass while checking "
                        + "nothing: " + unwired);
    }

    /**
     * Reads source with comments blanked out.
     *
     * <p>Not optional. The comment at the hatch explains why it no longer calls {@code openFresh},
     * so a check for that name's ABSENCE matches the explanation and fails on correct code. Caught
     * exactly that way on first run. Blanking rather than deleting keeps offsets aligned so brace
     * matching still finds the same method bodies.</p>
     */
    private static String read(String relativePath) throws IOException {
        String source = Files.readString(Path.of(relativePath));
        StringBuilder out = new StringBuilder(source.length());
        int mode = 0;                                    // 0 code, 1 line comment, 2 block comment
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (mode == 0 && c == '/' && next == '/') {
                mode = 1;
            } else if (mode == 0 && c == '/' && next == '*') {
                mode = 2;
            }
            if (mode == 0) {
                out.append(c);
                continue;
            }
            out.append(c == '\n' ? '\n' : ' ');
            if (mode == 1 && c == '\n') {
                mode = 0;
            } else if (mode == 2 && source.charAt(i) == '/' && i > 0 && source.charAt(i - 1) == '*') {
                mode = 0;
            }
        }
        return out.toString();
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ");
    }

    private static String methodSection(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "method is present: " + signature);
        int openBrace = source.indexOf('{', start);
        assertTrue(openBrace >= 0, "method body is present: " + signature);
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(start, i + 1);
            }
        }
        throw new AssertionError("method body is balanced: " + signature);
    }

    private static void assertTrue(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
