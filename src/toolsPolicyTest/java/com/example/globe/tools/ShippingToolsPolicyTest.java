package com.example.globe.tools;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private static final Set<String> PERMITTED_SUBCOMMANDS = Set.of(
            "help", "here", "explainHere", "probe", "tpLat", "tpBand", "flyspeed", "retrofit");

    private static final Set<String> PERMITTED_ARGUMENTS = Set.of(
            "level", "signedDegrees", "x", "band", "edge", "radiusBlocks", "samples");

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
        System.out.println("SHIPPING_TOOLS_POLICY_TEST_PASS assertions=" + assertions);
    }

    private static void shippingTreeIsExactlyThePermittedOperatorSet() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        LatitudeToolsCommand.register(dispatcher);

        // S1: exactly one root literal, named for the shipping verb.
        List<CommandNode<CommandSourceStack>> roots =
                new ArrayList<>(dispatcher.getRoot().getChildren());
        expectEquals(1, roots.size(), "shipping registration adds exactly one root command");
        CommandNode<CommandSourceStack> root = roots.get(0);
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
