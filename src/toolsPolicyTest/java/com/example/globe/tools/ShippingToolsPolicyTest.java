package com.example.globe.tools;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;

/**
 * Proves the shipping operator-command tree is exactly the permitted set, and that the shipping
 * tools package carries no recording, sentinel, or auto-harness coupling.
 *
 * <p>This suite is the build-enforced half of {@code docs/release/artifact-content-policy.md}.
 * The source-level and jar-level halves live in {@code tools/verify_phase6_dev_tooling.py}. All
 * three must stay green: a widening that slips past one should be caught by another.</p>
 *
 * <p>Assertions S1-S5 instantiate the real Brigadier tree and therefore need Minecraft's
 * {@code Commands} class to initialize outside a running server. If that ever stops working, the
 * correct response is to fail loudly and record the downgrade, never to silently skip them - a
 * degraded suite that still prints PASS is worse than no suite.</p>
 */
public final class ShippingToolsPolicyTest {
    private static final Set<String> PERMITTED_ROOTS = Set.of(
            "latitude", "latitude_locate_teleport");

    private static final Set<String> PERMITTED_SUBCOMMANDS = Set.of(
            "help", "here", "explainHere", "probe", "tpLat", "tpBand", "flyspeed", "retrofit");

    private static final Set<String> PERMITTED_ARGUMENTS = Set.of(
            "level", "signedDegrees", "x", "band", "edge", "radiusBlocks", "samples");

    private static final Set<String> PERMITTED_LOCATE_ACTION_ARGUMENTS = Set.of("token");

    private static final Set<String> ALLOWED_FIELD_TYPES = Set.of(
            "boolean", "byte", "char", "short", "int", "long", "float", "double",
            "java.lang.String", "java.util.List", "java.util.Set", "java.util.Map");

    private static final List<String> FORBIDDEN_TYPE_PREFIXES = List.of(
            "com.example.globe.dev.",
            "com.example.globe.debug.",
            "java.nio.file.",
            "java.io.");

    private static int assertions;

    private ShippingToolsPolicyTest() {
    }

    public static void main(String[] args) throws Exception {
        // Building the real Brigadier tree touches Minecraft's built-in registries, which refuse
        // to initialize outside a bootstrapped runtime. Bootstrap here rather than weakening the
        // suite: S1-S5 are the assertions that actually pin the shipped command surface.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        shippingTreeIsExactlyThePermittedOperatorSet();
        shippingClassCarriesNoExcludedCoupling();
        retrofitWorkerIsTheSingleBoundedOperatorConfirmedException();
        System.out.println("SHIPPING_TOOLS_POLICY_TEST_PASS assertions=" + assertions);
    }

    private static void shippingTreeIsExactlyThePermittedOperatorSet() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        LatitudeToolsCommand.register(dispatcher);

        // S1: exactly the operator root and its token-bound locate action are shipped.
        List<CommandNode<CommandSourceStack>> roots =
                new ArrayList<>(dispatcher.getRoot().getChildren());
        Set<String> rootNames = new LinkedHashSet<>();
        for (CommandNode<CommandSourceStack> candidate : roots) {
            rootNames.add(candidate.getName());
        }
        expectEquals(PERMITTED_ROOTS, rootNames, "shipping roots are exactly the permitted set");
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("latitude");
        expectEquals("latitude", root.getName(), "shipping root literal is /latitude");

        // S2: the child set is exactly the permitted operator commands - no more, no fewer.
        Set<String> children = new LinkedHashSet<>();
        for (CommandNode<CommandSourceStack> child : root.getChildren()) {
            children.add(child.getName());
        }
        expectEquals(PERMITTED_SUBCOMMANDS, children, "shipping subcommands are exactly the permitted set");

        // S3: executable-node count. Seeded from the permitted subtree; if this changes, a
        // subcommand was added or removed and the policy record must be updated deliberately.
        // 14 = the historical 10 plus the four /latitude retrofit nodes (status root, enable,
        // confirm, disable) added deliberately with the ledger-decoration retrofit.
        expectEquals(14, countExecutables(root), "shipping tree exposes exactly 14 executable nodes");

        // S4: argument names are exactly those the permitted commands declare.
        Set<String> arguments = new LinkedHashSet<>();
        collectArgumentNames(root, arguments);
        expectEquals(PERMITTED_ARGUMENTS, arguments, "shipping arguments are exactly the permitted set");

        // S5: operator gating is applied once, at the root, so no child can be reached without it.
        // Brigadier resolves a node's usability by walking its parents, so a gate at the root
        // covers the whole tree; a child carrying its own gate would mean the shape changed.
        expectTrue(!isTrivialRequirement(root), "the shipping root carries a real permission gate");
        expectEquals(0, countGatedDescendants(root), "no shipping child carries its own gate");

        // S8: the clickable locate action. It is intentionally non-elevated so Minecraft does not
        // show its run-command warning on a coordinate any player may legitimately click; the
        // unguessable, player-bound, expiring one-time token is the authorization boundary.
        CommandNode<CommandSourceStack> locateAction =
                dispatcher.getRoot().getChild("latitude_locate_teleport");
        expectTrue(isTrivialRequirement(locateAction), "locate action has no elevated permission gate");
        expectEquals(1, countExecutables(locateAction), "locate action exposes one executable token path");
        Set<String> locateArguments = new LinkedHashSet<>();
        collectArgumentNames(locateAction, locateArguments);
        expectEquals(PERMITTED_LOCATE_ACTION_ARGUMENTS, locateArguments,
                "locate action accepts only its one-time token");
    }

    private static void shippingClassCarriesNoExcludedCoupling() throws Exception {
        Class<?> clazz = LatitudeToolsCommand.class;

        // S6: every declared field is a static final constant of a benign type. A mutable field
        // would be the first step toward an accumulating record.
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isSynthetic() || field.getName().startsWith("$")) {
                continue;
            }
            String name = field.getName();
            expectTrue(java.lang.reflect.Modifier.isStatic(field.getModifiers()),
                    "shipping field is static: " + name);
            expectTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()),
                    "shipping field is final: " + name);
            expectTrue(ALLOWED_FIELD_TYPES.contains(field.getType().getName()),
                    "shipping field type is allowed: " + name + " (" + field.getType().getName() + ")");
        }

        // S7: no declared method signature touches an excluded package or filesystem type.
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            expectTrue(typeIsAllowed(method.getReturnType().getName()),
                    "shipping method return type is allowed: " + method.getName());
            for (Class<?> parameter : method.getParameterTypes()) {
                expectTrue(typeIsAllowed(parameter.getName()),
                        "shipping method parameter type is allowed: " + method.getName()
                                + " (" + parameter.getName() + ")");
            }
        }
    }

    /**
     * Retrofit is the sole deliberately shipped background worker. Its queue must stay bounded,
     * retry-safe, operator-confirmed, and disposable; this is source policy rather than a fake
     * Minecraft server simulation.
     */
    private static void retrofitWorkerIsTheSingleBoundedOperatorConfirmedException() throws Exception {
        String retrofit = Files.readString(Path.of(
                "src/main/java/com/example/globe/world/LatitudeDecorationRetrofit.java"));
        String command = Files.readString(Path.of(
                "src/main/java/com/example/globe/tools/LatitudeToolsCommand.java"));
        String globe = Files.readString(Path.of("src/main/java/com/example/globe/GlobeMod.java"));

        expectTrue(retrofit.contains("private static final int MAX_PENDING_CHUNKS = 2048;"),
                "retrofit queue is capped at exactly 2048 chunks");
        expectTrue(retrofit.contains("private static final int CHUNKS_PER_TICK = 2;"),
                "retrofit processes exactly two chunks per server tick");
        expectTrue(retrofit.contains("private static boolean enqueue(ChunkPos pos)"),
                "retrofit enqueue reports acceptance so callers can preserve retry safety");
        expectTrue(retrofit.contains("if (QUEUE.size() >= MAX_PENDING_CHUNKS)"),
                "retrofit rejects a new queue entry when its cap is reached");
        expectTrue(retrofit.contains("return false;"),
                "full retrofit queue has an explicit rejection path");
        expectTrue(retrofit.contains("if (!enqueue(chunk.getPos()))"),
                "chunk-load rejection is distinct from successful enqueue");
        int onLoad = retrofit.indexOf("private static void onChunkLoad(ServerLevel world, LevelChunk chunk)");
        int onLoadEnd = retrofit.indexOf("private static void onEndTick", onLoad);
        expectTrue(onLoad >= 0 && onLoadEnd > onLoad
                        && !retrofit.substring(onLoad, onLoadEnd)
                                .contains("markDecoratedUnderFixedIndex"),
                "a rejected chunk is never marked handled and can retry on a later load");
        expectTrue(retrofit.contains("private static final long OVERFLOW_WARNING_INTERVAL_MS = 60_000L;"),
                "overflow warning is limited to at most once per 60 seconds");
        expectTrue(retrofit.contains("GlobeMod.LOGGER.warn"),
                "queue overflow produces an operator-visible warning");
        expectTrue(retrofit.contains("GlobeMod.LOGGER.debug(\"[Latitude] retrofit decorated chunk"),
                "per-chunk retrofit success is DEBUG rather than noisy INFO");
        expectTrue(retrofit.contains("deferred:"),
                "status reports deferred chunks separately from the active queue");
        expectTrue(retrofit.contains("features placed:"),
                "status retains feature-placement work accounting");
        expectTrue(retrofit.contains("ServerLifecycleEvents.SERVER_STOPPED.register"),
                "server stop clears the exceptional worker state");
        expectTrue(retrofit.contains("private static void clearQueueState()"),
                "queue cleanup has one shared owner");
        int disable = retrofit.indexOf("public static List<String> disable(ServerLevel world)");
        int disableEnd = retrofit.indexOf("// ── Event plumbing", disable);
        expectTrue(disable >= 0 && disableEnd > disable
                        && retrofit.substring(disable, disableEnd).contains("clearQueueState();"),
                "disable clears queued and deferred work immediately");

        expectTrue(command.contains("Commands.literal(\"retrofit\")"),
                "the only shipping exception remains under /latitude retrofit");
        expectTrue(command.contains("LatitudeDecorationRetrofit.confirmEnable("),
                "activation requires the explicit retrofit confirm command");
        expectTrue(retrofit.contains("pendingConfirmDeadlineMs"),
                "the worker retains a bounded confirmation window rather than auto-activating");
        expectTrue(retrofit.contains("state.setRetrofitEnabled(true);"),
                "only confirmed activation enables retrofit processing");
        expectTrue(globe.contains("LatitudeDecorationRetrofit.init();"),
                "the registered worker is the exact Latitude retrofit implementation");
    }

    private static boolean typeIsAllowed(String typeName) {
        for (String forbidden : FORBIDDEN_TYPE_PREFIXES) {
            if (typeName.startsWith(forbidden)) {
                return false;
            }
        }
        return true;
    }

    private static int countExecutables(CommandNode<CommandSourceStack> node) {
        int count = node.getCommand() != null ? 1 : 0;
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            count += countExecutables(child);
        }
        return count;
    }

    private static void collectArgumentNames(CommandNode<CommandSourceStack> node, Set<String> out) {
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            if (child instanceof com.mojang.brigadier.tree.ArgumentCommandNode<?, ?> argument) {
                out.add(argument.getName());
            }
            collectArgumentNames(child, out);
        }
    }

    private static int countGatedDescendants(CommandNode<CommandSourceStack> node) {
        int count = 0;
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            count += (isTrivialRequirement(child) ? 0 : 1) + countGatedDescendants(child);
        }
        return count;
    }

    /**
     * Probes a node's requirement by behaviour rather than by class identity.
     *
     * <p>Brigadier's default requirement is {@code source -> true}, which ignores its argument and
     * so answers {@code true} even for a null source. A real permission check dereferences the
     * source and throws. Identity checks against lambda class names are brittle and were wrong
     * here; this asks the predicate what it actually does.</p>
     */
    private static boolean isTrivialRequirement(CommandNode<CommandSourceStack> node) {
        if (node.getRequirement() == null) {
            return true;
        }
        try {
            return node.getRequirement().test(null);
        } catch (RuntimeException expected) {
            return false;
        }
    }

    private static void expectTrue(boolean condition, String label) {
        assertions++;
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void expectEquals(Object expected, Object actual, String label) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
