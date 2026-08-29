package com.example.globe.client.create;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pins the escape hatch to the no-reload open path.
 *
 * <p>Measured on this line: vanilla's fresh-open blocks the render thread ~2.4s building a
 * PackRepository and running WorldLoader, and the hatch was paying it a second time for a context
 * already in memory. Reverting to the fresh path would restore that cost silently -- nothing fails,
 * the screen just takes another two and a half seconds -- so it is pinned in source.
 */
final class EscapeHatchSourcePolicyTest {
    private static final String SCREEN = "src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java";
    private static final String REDIRECT = "src/main/java/com/example/globe/mixin/client/CreateWorldScreenInitRedirectMixin.java";
    private static final String DOOR = "src/main/java/com/example/globe/mixin/client/SelectWorldScreenVanillaDoorMixin.java";
    private static final String SELF = "src/clipPolicyTest/java/com/example/globe/client/create/EscapeHatchSourcePolicyTest.java";

    private static int assertions;

    private EscapeHatchSourcePolicyTest() {
    }

    static int run() throws Exception {
        assertions = 0;
        theCommentStripperActuallyStrips();
        theHatchDoesNotReloadDatapacks();
        theRecreatedFlagIsCleared();
        theModeMappingIsNotDuplicated();
        theReturnLegReadsBackVanillaState();
        theReadBackNeverCastsToTheMixinClassDirectly();
        theWorldListDoorArmsWithoutAWayBack();
        theWorldListDoorKeepsItsSeparation();
        bothScreensRelayOutOnResize();
        theDoorLayoutIsIdempotent();
        neitherScreenIsBlindedByItsOwnFirstPass();
        everyGuardInThisFileIsActuallyRun();
        return assertions;
    }

    /**
     * The carry into the vanilla screen is a one-time snapshot taken when the button is pressed; it
     * cannot see anything the player changes afterwards on that screen's own controls. Without a
     * read-back on the way out, a player who toggles Hardcore there finds it reverted on return
     * (maintainer report, 2026-08-27: "Hardcore does not survive; have to change it again when you
     * go back"). This pins that the return leg reads live state rather than only carrying the
     * outbound snapshot back untouched.
     */
    private static void theReturnLegReadsBackVanillaState() throws Exception {
        String code = strip(read(SCREEN));
        expectTrue(code.contains("globe$getUiState()"),
                "returning to Latitude must read the vanilla screen's CURRENT state");
        expectTrue(code.contains("this.selectedModeIdx = switch (vanillaState.getGameMode())"),
                "game mode must be read back, not left as whatever it was before the detour");
        expectTrue(code.contains("this.selectedDifficulty = vanillaState.getDifficulty()"),
                "difficulty must be read back");
        expectTrue(code.contains("this.allowCommands = vanillaState.isAllowCommands()"),
                "allowCommands must be read back");
    }

    /**
     * A {@code @Mixin}-annotated class cannot be cast to from ordinary code -- it compiles cleanly
     * and then crashes at runtime with {@code IllegalClassLoadError} the first time the path
     * actually executes, which is exactly what happened building this fix. Pinned so a future edit
     * cannot reintroduce a cast this project's own dev client has already proven fatal.
     */
    private static void theReadBackNeverCastsToTheMixinClassDirectly() throws Exception {
        String code = strip(read(SCREEN));
        expectFalse(code.contains("CreateWorldScreenMixin)"),
                "must cast to the plain VanillaCreateWorldUiStateCarrier interface, never to the "
                        + "@Mixin class itself -- that cast is a runtime IllegalClassLoadError, "
                        + "caught live on 2026-08-27, not a compile error");
        expectTrue(code.contains("VanillaCreateWorldUiStateCarrier) (Object) current"),
                "the working form: cast through Object to the plain interface");
    }

    /**
     * Self-contained control, deliberately not coupled to any real comment.
     *
     * <p>The assertion below scans stripped source, so a broken stripper would let commented-out
     * text satisfy or violate it by accident. Checking the stripper against strings built here means
     * this control cannot be invalidated by someone legitimately rewording a comment -- and the
     * comment explaining WHY openFresh was removed necessarily contains the word, which is exactly
     * how this guard would fail on correct code if it scanned raw source.
     */
    private static void theCommentStripperActuallyStrips() {
        expectFalse(strip("int a; /* openFresh */ int b;").contains("openFresh"),
                "block comments must be stripped");
        expectFalse(strip("int a; // openFresh\nint b;").contains("openFresh"),
                "line comments must be stripped -- a separate code path from block comments");
        expectFalse(strip("/** javadoc openFresh */\nint a;").contains("openFresh"),
                "javadoc must be stripped");
        expectTrue(strip("callSite(openFresh);").contains("openFresh"),
                "real code must survive stripping, or every assertion below passes vacuously");
    }

    private static void theHatchDoesNotReloadDatapacks() throws Exception {
        String code = strip(read(SCREEN));
        expectTrue(code.length() > 20_000,
                "implausibly small source after stripping (" + code.length() + "); a broken read "
                        + "would pass every assertion below vacuously");
        expectTrue(code.contains("CreateWorldScreen.createFromExisting("),
                "the hatch must reuse the loaded context");
        expectFalse(code.contains("CreateWorldScreen.openFresh("),
                "the hatch must not call the fresh-open path -- it reloads every datapack on the "
                        + "render thread (~2.4s measured) for data already held");
    }

    private static void theRecreatedFlagIsCleared() throws Exception {
        String code = strip(read(REDIRECT));
        expectTrue(code.contains("this.recreated = false;"),
                "createFromExisting sets recreated=true for Re-Create; a hatch world is new, and "
                        + "leaving it set shows an experimental-content confirmation on ordinary worlds");
    }

    /**
     * The hatch and beginExpedition read the same selector; two copies of the mapping would diverge
     * the first time either was edited, and a world made through the hatch would silently arrive in
     * the wrong mode.
     */
    private static void theModeMappingIsNotDuplicated() throws Exception {
        String code = strip(read(SCREEN));
        // The MAPPING specifically, not every mention of the selector. A bare `selectedModeIdx == 2`
        // count also matches cycleMode's unrelated "creative implies allowCommands" rule, which is a
        // different decision that legitimately reads the same field -- the first cut of this guard
        // failed on correct code for exactly that reason.
        int copies = countOccurrences(code, "selectedModeIdx == 2 ? GameType.CREATIVE");
        expectTrue(copies == 1,
                "the game-mode mapping must exist exactly once, found " + copies);
        expectTrue(code.contains("selectedGameType()") && code.contains("selectedHardcore()"),
                "both call sites must go through the shared mapping");
    }

    /**
     * The subtle one. Arming the door with the ordinary four-argument call would compile, run, and
     * look right -- and then the escape-hatch footer would relabel vanilla's Cancel to "Back to
     * Latitude" on a screen reached from the world list, where no Latitude screen was ever open. The
     * button would point at nothing.
     */
    private static void theWorldListDoorArmsWithoutAWayBack() throws Exception {
        String code = strip(read(DOOR));
        expectTrue(code.contains("VanillaCreateWorldHandoff.armNextWithoutReturn("),
                "the world-list door must arm without a return callback");
        expectFalse(code.contains("VanillaCreateWorldHandoff.armNext("),
                "arming the ordinary way would relabel vanilla's Cancel to a screen that was never open");
    }

    /**
     * The gap is the mis-click defence the design was chosen for; a flush button is a different
     * design. Also pins that the door SHARES the row rather than appending past it -- appending is
     * what made the button vanish at higher GUI scales, which was rejected live.
     */
    private static void theWorldListDoorKeepsItsSeparation() throws Exception {
        String code = strip(read(DOOR));
        expectTrue(code.contains("GLOBE_GAP = 10") && code.contains("GLOBE_INNER_GAP = 8"),
                "the door must keep a wider separation than the neighbours' own spacing");
        expectTrue(matches(code, "sharedWidthForThree\\(\\w+, GLOBE_INNER_GAP, GLOBE_GAP\\)"),
                "the gap must actually be applied to the layout, not merely declared. Matched by "
                        + "SHAPE, not verbatim: the claim is that both gap constants reach the "
                        + "width split, and it must not go red merely because a local was renamed");
        expectFalse(code.contains("fitsWithAppendedButton"),
                "the door must not append-and-refuse -- that is what made it absent at higher GUI "
                        + "scales, where Minecraft's internal width shrinks below the window's look");
    }

    /**
     * A guard that exists but is never called reports a pass while checking nothing -- the most
     * dangerous shape in this family, because it converts "unverified" into "verified" with no
     * failure anywhere. It happened in this very file: two guards were added and left unwired, and
     * only the assertion COUNT not moving gave it away. That is too quiet a signal to rely on twice,
     * so it is now an assertion.
     */
    private static void everyGuardInThisFileIsActuallyRun() throws Exception {
        String source = read(SELF);
        int runStart = source.indexOf("static int run()");
        int runEnd = source.indexOf("return assertions;", runStart);
        expectTrue(runStart >= 0 && runEnd > runStart, "could not locate run() to audit");
        String runBody = source.substring(runStart, runEnd);

        java.util.List<String> declared = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("private static void (the[A-Za-z]+)\\(").matcher(strip(source));
        while (m.find()) {
            declared.add(m.group(1));
        }
        expectTrue(declared.size() >= 6,
                "implausibly few guards discovered (" + declared.size() + "); a broken scan would "
                        + "vacuously pass this audit");
        for (String guard : declared) {
            expectTrue(runBody.contains(guard + "()"),
                    "guard '" + guard + "' is declared but never called by run() -- it reports a "
                            + "pass while checking nothing");
        }
    }

    /**
     * Both added widgets must be re-laid on resize, and this is pinned SEPARATELY for each mixin.
     *
     * <p>{@code Screen.init(int,int)} runs the overridable {@code init()} only on its first call per
     * instance; a private {@code initialized} flag sends every later call, resizes included, to
     * {@code repositionElements()}. A hook only at {@code init}'s TAIL therefore fires once ever, so
     * the added button freezes at its first-open coordinates while vanilla's {@code arrangeElements()}
     * keeps re-centring its own. Reported live on the sibling line: narrow the window, the door
     * hides, widen back, it never returns.</p>
     *
     * <p>Pinned per-mixin deliberately. The sibling line pinned only ONE of its two mixins and the
     * identical missing-hook regression on the other would have shipped silently -- a guard proving
     * the layout is correct when it runs says nothing about whether it runs.</p>
     */
    private static void bothScreensRelayOutOnResize() throws Exception {
        for (String file : new String[] {DOOR, REDIRECT}) {
            String code = strip(read(file));
            expectTrue(code.contains("@Inject(method = \"repositionElements\""),
                    file + " must hook repositionElements -- init() fires once per screen instance, "
                            + "so an init-only hook is blind to every resize");
            expectTrue(code.contains("@Inject(method = \"init\""),
                    file + " must still hook init for the first layout");
        }
        // Rebuilding instead of re-placing would stack a fresh widget on every resize. Assert the
        // RE-PLACE branch exists and that registration happens exactly once -- an earlier form of
        // this guard matched a token that also appears in the re-place branch, so deleting the
        // null-guard left it green. Match the statements, not a substring that co-occurs with them.
        String door = strip(read(DOOR));
        expectEquals(1, countOccurrences(door, "globe$addRenderableWidget(door)"),
                "the door must be registered exactly once, not once per resize");
        expectTrue(matches(door, "this\\.globe\\$door\\.setX\\("),
                "the door must have a re-place path; without it every resize builds a new button");

        String redirect = strip(read(REDIRECT));
        expectEquals(1, countOccurrences(redirect, "globe$addRenderableWidget(exitButton)"),
                "the footer's added button must be registered exactly once");
        expectTrue(matches(redirect, "this\\.globe\\$footerExit\\.setX\\("),
                "the footer's added button must have a re-place path");
        // Re-discovery after the relabel would search for a message that no longer exists.
        expectTrue(matches(redirect, "Button \\w+ = this\\.globe\\$footerCancel"),
                "the footer must READ its cached Cancel -- the first pass relabels that button, so "
                        + "a second search for GUI_CANCEL finds nothing and the footer half-lays-out");
    }

    /**
     * Neither mixin may be defeated by the layout it performed a moment earlier.
     *
     * <p>Both screens call {@code repositionElements()} from inside {@code init()} before it returns
     * (26.2 bytecode: SelectWorldScreen offset 265, CreateWorldScreen offset 159), and the escape
     * hatch's gate is already open by then because it is armed at {@code init}'s HEAD. So the resize
     * hook lays the footer out ONCE ALREADY, and the {@code init} TAIL hook is a SECOND pass over a
     * screen this code has visibly modified. That second pass is where both live defects came from:
     * the world list built a duplicate door, and the escape hatch re-found GUI_CANCEL on its own
     * exit button (vanilla's Cancel no longer carries that label by then), counted three buttons on
     * the row, bailed, and left the footer frozen for every later resize.</p>
     *
     * <p>The collision is DERIVED here rather than pinned as a magic string: the guard reads whether
     * the source gives its own added button the same label it later searches for, and only then
     * requires the exclusion. If the label ever changes so the collision cannot happen, this guard
     * stops demanding a defence that is no longer needed instead of going stale.</p>
     */
    private static void neitherScreenIsBlindedByItsOwnFirstPass() throws Exception {
        String redirect = strip(read(REDIRECT));

        boolean exitCarriesCancelLabel =
                redirect.contains("Button.builder(CommonComponents.GUI_CANCEL, button -> exit.run())");
        boolean searchesForCancelLabel =
                redirect.contains("CommonComponents.GUI_CANCEL.equals(button.getMessage())");
        expectTrue(exitCarriesCancelLabel && searchesForCancelLabel,
                "this guard assumes the hatch both LABELS its exit button GUI_CANCEL and SEARCHES "
                        + "for GUI_CANCEL; if either changed, re-derive the collision below rather "
                        + "than deleting the guard");

        // Given the two facts above, EVERY widget search over children() must exclude our own
        // button by reference. Checked per-loop rather than by a whole-file count, so adding a
        // third unguarded loop later fails here instead of being absorbed into a total.
        String loopHeader = "for (GuiEventListener child : self.children())";
        int loops = countOccurrences(redirect, loopHeader);
        expectEquals(2, loops,
                "expected exactly two child-scanning loops in the hatch (Cancel, then the row); "
                        + "found " + loops + " -- re-derive this guard rather than widening it");
        int from = 0;
        for (int i = 0; i < loops; i++) {
            int start = redirect.indexOf(loopHeader, from);
            int end = redirect.indexOf("}", start);
            String body = redirect.substring(start, end);
            expectTrue(body.contains("button != this.globe$footerExit"),
                    "child-scanning loop #" + (i + 1) + " must exclude our own exit button: it is "
                            + "the only widget still carrying GUI_CANCEL once vanilla's Cancel has "
                            + "been relabelled, and it sits on the row it would be counted in");
            from = end;
        }

        // The load-bearing property is that NO init hook clears its caches unconditionally.
        //
        // Pinned as "the clear is guarded", NOT as "the guard is !children().contains(...)". On
        // 26.2 that particular condition can never fire -- neither screen calls rebuildWidgets()
        // or clearWidgets() anywhere, so the widget is never discarded -- and the sibling line's
        // hatch, which has the same zero call sites, simply never clears at all and is equally
        // correct. Pinning the unreachable expression would assert a defence against a path that
        // does not exist, and would fail a future rewrite that fixed the bug the other, simpler
        // way. So: assert every cache-clearing assignment sits inside a conditional.
        for (String file : new String[] {DOOR, REDIRECT}) {
            String code = strip(read(file));
            int hookStart = code.indexOf("@Inject(method = \"init\", at = @At(\"TAIL\"))");
            expectTrue(hookStart >= 0, file + " must still hook init's TAIL");
            int bodyStart = code.indexOf("{", code.indexOf(")", code.indexOf("private void", hookStart)));
            String hookBody = code.substring(bodyStart, code.indexOf("\n    }", bodyStart));
            boolean clears = hookBody.contains("= null;");
            if (clears) {
                expectTrue(hookBody.contains("if ("),
                        file + "'s init hook clears cached widgets but does so unconditionally. "
                                + "init() calls repositionElements() before returning, so the "
                                + "resize hook has ALREADY laid this out -- a blind clear makes the "
                                + "second pass re-discover over this code's own output, which built "
                                + "a duplicate door on one screen and froze the footer on the other");
            } else {
                expectTrue(true, file + "'s init hook never clears, which is also correct");
            }
        }
        expectFalse(redirect.contains("// init() rebuilds every widget, so cached references are stale here."),
                "that premise is false: init() is re-run only via rebuildWidgets(), and neither "
                        + "screen calls it at all -- so the widgets are still there on pass two");
    }

    /**
     * The layout must not measure its own previous output.
     *
     * <p>Deriving the row's envelope from the live buttons looked correct and was not: this layout
     * NARROWS those buttons, so the next pass measured the narrowed span and the row ratcheted
     * smaller on every resize -- observed live as 308 -> 200 -> 218 -> 227. Anchoring to vanilla's
     * original button width, captured before the first narrowing, makes the same screen width always
     * produce the same result however many times it runs.</p>
     */
    private static void theDoorLayoutIsIdempotent() throws Exception {
        String code = strip(read(DOOR));
        expectTrue(matches(code, "fittedEnvelope\\([^;]*globe\\$originalButtonWidth"),
                "the envelope must be computed FROM vanilla's original button width, not from the "
                        + "live (already narrowed) buttons. Matched as 'the captured original is an "
                        + "input to fittedEnvelope', so re-associating the arithmetic stays green");
        expectTrue(matches(code, "Math\\.max\\((this\\.globe\\$originalButtonWidth, create\\.getWidth\\(\\)"
                        + "|create\\.getWidth\\(\\), this\\.globe\\$originalButtonWidth)\\)"),
                "the captured original must be monotonic -- a re-capture taken while the buttons "
                        + "still carry a width this layout applied would ratchet the row smaller. "
                        + "Math.max is commutative, so BOTH argument orders satisfy the claim and "
                        + "neither may be failed for being spelled the other way round");
        expectFalse(code.contains("row.get(1).getX() + row.get(1).getWidth()"),
                "deriving the envelope from the live row measures this layout's own previous output "
                        + "and shrinks the row on every resize");
        expectTrue(code.contains("button != this.globe$door"),
                "row discovery must exclude our own door, or the two-button shape check fails once "
                        + "the door exists and the layout silently stops running");
    }

    /**
     * Regex search over stripped source.
     *
     * <p>Exists so guards can assert a RELATIONSHIP -- which identifiers reach which call -- rather
     * than a verbatim statement. Pinning source text verbatim satisfies "the guard can fail" while
     * violating "the guard must not fail a different correct implementation": renaming a local,
     * re-wrapping a long line, or swapping the arguments of a commutative call all redden a method
     * that is still right. Mutation testing cannot catch this, because it perturbs the
     * implementation that exists and never the reasonable alternative (sibling-line finding,
     * 2026-08-28).</p>
     */
    private static boolean matches(String source, String regex) {
        return java.util.regex.Pattern.compile(regex).matcher(source).find();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    /** Blanks comments while preserving length, so any offset-based reasoning stays aligned. */
    private static String strip(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int mode = 0; // 0 = code, 1 = line comment, 2 = block comment
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
            } else {
                out.append(c == '\n' ? '\n' : ' ');
            }
            if (mode == 1 && c == '\n') {
                mode = 0;
            } else if (mode == 2 && c == '*' && next == '/') {
                out.append(' ');
                i++;
                mode = 0;
            }
        }
        return out.toString();
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void expectTrue(boolean condition, String label) {
        assertions++;
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void expectEquals(int expected, int actual, String label) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void expectFalse(boolean condition, String label) {
        expectTrue(!condition, label);
    }
}
