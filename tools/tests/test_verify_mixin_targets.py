"""Unit tests for the handler-signature lane of tools/verify_mixin_targets.py.

They run without Gradle or a Minecraft jar: `javap -s -p` output is canned per class and fed
through a SignatureIndex subclass, and the mixin sources are small synthetic classes. The
central case is the one the verifier used to miss -- an @Inject handler whose parameter list no
longer matches its target passed the name-only check and crashed the dedicated server at class
load with InvalidInjectionException.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import verify_mixin_targets as vmt  # noqa: E402

CLASSES = {
    "a.b.Foo": '''Compiled from "Foo.java"
public class a.b.Foo extends a.b.Base implements java.lang.Runnable {
  private a.b.State state;
    descriptor: La/b/State;
  public a.b.Foo(a.b.Source);
    descriptor: (La/b/Source;)V

  public void render(a.b.Gui, int);
    descriptor: (La/b/Gui;I)V

  public void render(a.b.Gui);
    descriptor: (La/b/Gui;)V

  public a.b.State setState(a.b.Pos, a.b.State, int);
    descriptor: (La/b/Pos;La/b/State;I)La/b/State;

  public static a.b.Foo create(java.lang.String);
    descriptor: (Ljava/lang/String;)La/b/Foo;

  public int count();
    descriptor: ()I

  public void tick();
    descriptor: ()V

  public void withLong(long, int);
    descriptor: (JI)V

  public <T> java.util.List<T> items(java.util.function.Predicate<T>);
    descriptor: (Ljava/util/function/Predicate;)Ljava/util/List;

  private static void lambda$tick$0(a.b.Foo);
    descriptor: (La/b/Foo;)V
}
''',
    "a.b.Base": '''Compiled from "Base.java"
public abstract class a.b.Base {
  public a.b.Base();
    descriptor: ()V

  public void inherited(int);
    descriptor: (I)V
}
''',
    "a.b.Helper": '''Compiled from "Helper.java"
public class a.b.Helper {
  public a.b.Helper();
    descriptor: ()V

  public boolean check(a.b.Pos);
    descriptor: (La/b/Pos;)Z

  public static void util(long);
    descriptor: (J)V
}
''',
    "a.b.Sub": '''Compiled from "Sub.java"
public class a.b.Sub extends a.b.Helper {
  public a.b.Sub();
    descriptor: ()V
}
''',
}

IMPORTS = """package com.example.globe.mixin;

import a.b.Foo;
import a.b.Gui;
import a.b.Helper;
import a.b.Pos;
import a.b.Source;
import a.b.State;
import a.b.Sub;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
"""


class FakeIndex(vmt.SignatureIndex):
    """javap replaced by the canned outputs above."""

    def __init__(self) -> None:
        super().__init__("javap", "unused.jar")

    def javap_output(self, binary_name: str) -> str | None:
        return CLASSES.get(binary_name)


def mixin_source(body: str) -> str:
    return IMPORTS + "\n@Mixin(Foo.class)\npublic abstract class FooMixin {\n" + body + "\n}\n"


def verify(body: str, targets: tuple[str, ...] = ("a.b.Foo",)) -> vmt.SignatureReport:
    source = mixin_source(body)
    return vmt.verify_handler_signatures("FooMixin", source, source, list(targets), FakeIndex())


class InjectTest(unittest.TestCase):
    def test_full_form_matches_target_arguments_plus_callback(self) -> None:
        report = verify('''
    @Inject(method = "render(La/b/Gui;I)V", at = @At("HEAD"))
    private void onRender(Gui gui, int x, CallbackInfo ci) {}
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)

    def test_short_form_takes_only_the_callback(self) -> None:
        report = verify('''
    @Inject(method = "render(La/b/Gui;I)V", at = @At("HEAD"))
    private void onRender(CallbackInfo ci) {}
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)

    def test_dropped_parameter_is_reported_with_expected_and_found(self) -> None:
        # The mutation that used to pass: same `method =`, one target argument missing.
        report = verify('''
    @Inject(method = "render(La/b/Gui;I)V", at = @At("HEAD"))
    private void onRender(Gui gui, CallbackInfo ci) {}
''')
        self.assertEqual(report.verified, 0)
        self.assertEqual(len(report.problems), 1)
        self.assertIn("expected (Gui, int, CallbackInfo) -> void", report.problems[0])
        self.assertIn("found (Gui, CallbackInfo) -> void", report.problems[0])
        self.assertIn("onRender", report.problems[0])

    def test_wrong_parameter_type_names_the_position(self) -> None:
        report = verify('''
    @Inject(method = "render(La/b/Gui;I)V", at = @At("HEAD"))
    private void onRender(Gui gui, long x, CallbackInfo ci) {}
''')
        self.assertEqual(len(report.problems), 1)
        self.assertIn("parameter 2 is long but the target's argument 2 is int", report.problems[0])

    def test_returnable_callback_is_required_for_value_targets(self) -> None:
        report = verify('''
    @Inject(method = "count", at = @At("RETURN"))
    private void onCount(CallbackInfo ci) {}
''')
        self.assertEqual(len(report.problems), 1)
        self.assertIn("CallbackInfoReturnable is required", report.problems[0])

    def test_returnable_generic_must_be_the_boxed_return_type(self) -> None:
        report = verify('''
    @Inject(method = "setState", at = @At("HEAD"), cancellable = true)
    private void onSet(Pos pos, State state, int flags, CallbackInfoReturnable<Integer> cir) {}
''')
        self.assertEqual(len(report.problems), 1)
        self.assertIn("CallbackInfoReturnable<Integer>", report.problems[0])
        self.assertIn("expected CallbackInfoReturnable<State>", report.problems[0])
        ok = verify('''
    @Inject(method = "count", at = @At("RETURN"))
    private void onCount(CallbackInfoReturnable<Integer> cir) {}
''')
        self.assertEqual(ok.problems, [])

    def test_explicit_descriptor_must_be_declared_on_the_target(self) -> None:
        report = verify('''
    @Inject(method = "render(La/b/Gui;J)V", at = @At("HEAD"))
    private void onRender(Gui gui, long x, CallbackInfo ci) {}
''')
        self.assertEqual(report.verified, 0)
        self.assertTrue(any("render(La/b/Gui;J)V' is not declared on a.b.Foo" in p for p in report.problems))
        self.assertTrue(any("(La/b/Gui;)V, (La/b/Gui;I)V" in p for p in report.problems))

    def test_explicit_descriptor_selects_one_overload_exactly(self) -> None:
        report = verify('''
    @Inject(method = "render(La/b/Gui;)V", at = @At("HEAD"))
    private void onRender(Gui gui, CallbackInfo ci) {}
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.warnings, [])
        self.assertEqual(report.verified, 1)

    def test_bare_name_passes_when_any_overload_matches(self) -> None:
        # CallbackInjector only fails when no candidate target accepts the handler.
        report = verify('''
    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(Gui gui, int x, CallbackInfo ci) {}
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)
        self.assertTrue(any("matches 1 of 2 candidate targets" in w for w in report.warnings))

    def test_bare_name_fails_when_no_overload_matches(self) -> None:
        report = verify('''
    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(Gui gui, long x, CallbackInfo ci) {}
''')
        self.assertEqual(report.verified, 0)
        self.assertEqual(len(report.problems), 2)

    def test_locals_capture_allows_unchecked_trailing_params(self) -> None:
        report = verify('''
    @Inject(method = "tick", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void onTick(CallbackInfo ci, int local) {}
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.partial, 1)
        self.assertTrue(any("captured local" in w for w in report.warnings))

    def test_trailing_params_without_locals_capture_fail(self) -> None:
        report = verify('''
    @Inject(method = "tick", at = @At("RETURN"))
    private void onTick(CallbackInfo ci, int local) {}
''')
        self.assertEqual(len(report.problems), 1)
        self.assertIn("declares no locals capture", report.problems[0])

    def test_constructor_targets_resolve_as_init(self) -> None:
        report = verify('''
    @Inject(method = "<init>(La/b/Source;)V", at = @At("TAIL"))
    private void onInit(Source source, CallbackInfo ci) {}
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)

    def test_inherited_method_is_not_an_injection_target(self) -> None:
        report = verify('''
    @Inject(method = "inherited", at = @At("HEAD"))
    private void onInherited(int x, CallbackInfo ci) {}
''')
        self.assertEqual(report.verified, 0)
        self.assertTrue(any("inherited by a.b.Foo from a.b.Base" in p for p in report.problems))

    def test_lambda_bodies_are_declared_methods(self) -> None:
        report = verify('''
    @Inject(method = {"tick", "lambda$tick$0"}, at = @At("HEAD"))
    private static void onTick(CallbackInfo ci) {}
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)


class SugarAndCoerceTest(unittest.TestCase):
    def test_sugar_parameters_are_stripped_before_the_check(self) -> None:
        report = verify('''
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "La/b/Helper;check(La/b/Pos;)Z"))
    private boolean wrap(Helper helper, Pos pos, Operation<Boolean> original,
                         @Local State state, @Local(ordinal = 1) int depth) {
        return original.call(helper, pos);
    }
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)

    def test_sugar_must_be_trailing(self) -> None:
        report = verify('''
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "La/b/Helper;check(La/b/Pos;)Z"))
    private boolean wrap(Helper helper, @Local State state, Pos pos, Operation<Boolean> original) {
        return original.call(helper, pos);
    }
''')
        self.assertEqual(len(report.problems), 1)
        self.assertIn("must be trailing", report.problems[0])

    def test_coerce_widens_one_position(self) -> None:
        report = verify('''
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "La/b/Helper;check(La/b/Pos;)Z"))
    private boolean redirect(@Coerce Object helper, Pos pos) { return true; }
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)


class RedirectTest(unittest.TestCase):
    def test_instance_target_takes_the_receiver_first(self) -> None:
        report = verify('''
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "La/b/Helper;check(La/b/Pos;)Z"))
    private boolean redirect(Helper helper, Pos pos) { return true; }
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)

    def test_missing_receiver_is_reported(self) -> None:
        report = verify('''
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "La/b/Helper;check(La/b/Pos;)Z"))
    private boolean redirect(Pos pos) { return true; }
''')
        self.assertEqual(len(report.problems), 1)
        self.assertIn("expected (Helper, Pos) -> boolean, found (Pos) -> boolean", report.problems[0])
        swapped = verify('''
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "La/b/Helper;check(La/b/Pos;)Z"))
    private boolean redirect(Pos pos, Helper helper) { return true; }
''')
        self.assertEqual(len(swapped.problems), 1)
        self.assertIn("parameter 1 is Pos but Helper is required", swapped.problems[0])

    def test_static_target_takes_no_receiver(self) -> None:
        report = verify('''
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "La/b/Helper;util(J)V"))
    private void redirect(long value) {}
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)

    def test_owner_method_resolves_through_the_hierarchy(self) -> None:
        report = verify('''
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "La/b/Sub;check(La/b/Pos;)Z"))
    private boolean redirect(Sub sub, Pos pos) { return true; }
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)

    def test_trailing_params_must_prefix_the_target_arguments(self) -> None:
        ok = verify('''
    @Redirect(method = "render(La/b/Gui;I)V", at = @At(value = "INVOKE", target = "La/b/Helper;check(La/b/Pos;)Z"))
    private boolean redirect(Helper helper, Pos pos, Gui gui) { return true; }
''')
        self.assertEqual(ok.problems, [])
        bad = verify('''
    @Redirect(method = "render(La/b/Gui;I)V", at = @At(value = "INVOKE", target = "La/b/Helper;check(La/b/Pos;)Z"))
    private boolean redirect(Helper helper, Pos pos, int x) { return true; }
''')
        self.assertEqual(len(bad.problems), 1)
        self.assertIn("trailing parameter 3 is int but the target's argument 1 is Gui", bad.problems[0])

    def test_wildcard_selector_checks_the_head_only(self) -> None:
        report = verify('''
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "La/b/Helper;check(La/b/Pos;)Z"))
    private boolean redirect(Helper helper, Pos pos) { return true; }
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)
        inject = verify('''
    @Inject(method = "*", at = @At("HEAD"))
    private void any(CallbackInfo ci) {}
''')
        self.assertEqual(inject.unverifiable, 1)
        self.assertEqual(inject.problems, [])

    def test_field_redirect_without_opcode_is_unverifiable_not_passed(self) -> None:
        report = verify('''
    @Redirect(method = "tick", at = @At(value = "FIELD", target = "La/b/Foo;state:La/b/State;"))
    private State readState(Foo self) { return null; }
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 0)
        self.assertEqual(report.unverifiable, 1)
        self.assertTrue(any("no opcode" in w for w in report.warnings))
        with_opcode = verify('''
    @Redirect(method = "tick", at = @At(value = "FIELD", target = "La/b/Foo;state:La/b/State;", opcode = Opcodes.GETFIELD))
    private State readState(Foo self) { return null; }
''')
        self.assertEqual(with_opcode.problems, [])
        self.assertEqual(with_opcode.verified, 1)


class WrapTest(unittest.TestCase):
    def test_wrap_operation_shape(self) -> None:
        report = verify('''
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "La/b/Helper;check(La/b/Pos;)Z"))
    private boolean wrap(Helper helper, Pos pos, Operation<Boolean> original) { return original.call(helper, pos); }
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)

    def test_wrap_operation_missing_operation_parameter(self) -> None:
        report = verify('''
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "La/b/Helper;check(La/b/Pos;)Z"))
    private boolean wrap(Helper helper, Pos pos) { return true; }
''')
        self.assertEqual(len(report.problems), 1)
        self.assertIn("expected (Helper, Pos, Operation) -> boolean", report.problems[0])

    def test_operation_generic_must_be_the_boxed_return_type(self) -> None:
        report = verify('''
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "La/b/Helper;check(La/b/Pos;)Z"))
    private boolean wrap(Helper helper, Pos pos, Operation<Integer> original) { return true; }
''')
        self.assertEqual(len(report.problems), 1)
        self.assertIn("Operation<Integer>", report.problems[0])
        self.assertIn("expected Operation<Boolean>", report.problems[0])

    def test_wrap_method_is_exactly_target_arguments_plus_operation(self) -> None:
        ok = verify('''
    @WrapMethod(method = "setState")
    private State wrap(Pos pos, State state, int flags, Operation<State> original) { return original.call(pos, state, flags); }
''')
        self.assertEqual(ok.problems, [])
        self.assertEqual(ok.verified, 1)
        missing = verify('''
    @WrapMethod(method = "setState")
    private State wrap(Pos pos, State state, int flags) { return state; }
''')
        self.assertEqual(len(missing.problems), 1)
        self.assertIn("expected (Pos, State, int, Operation) -> State", missing.problems[0])
        extra = verify('''
    @WrapMethod(method = "setState")
    private State wrap(Pos pos, State state, int flags, Operation<State> original, Gui gui) { return state; }
''')
        self.assertEqual(len(extra.problems), 1)
        self.assertIn("unexpected additional parameter", extra.problems[0])


class ModifyTest(unittest.TestCase):
    def test_modify_variable_args_only_index_pins_the_type(self) -> None:
        ok = verify('''
    @ModifyVariable(method = "setState", at = @At("HEAD"), argsOnly = true, index = 2)
    private State swap(State state, Pos pos) { return state; }
''')
        self.assertEqual(ok.problems, [])
        self.assertEqual(ok.verified, 1)
        wrong = verify('''
    @ModifyVariable(method = "setState", at = @At("HEAD"), argsOnly = true, index = 1)
    private State swap(State state, Pos pos) { return state; }
''')
        self.assertEqual(len(wrong.problems), 1)
        self.assertIn("argsOnly index 1 is a Pos", wrong.problems[0])

    def test_modify_variable_index_counts_two_slot_arguments(self) -> None:
        report = verify('''
    @ModifyVariable(method = "withLong", at = @At("HEAD"), argsOnly = true, index = 3)
    private int bump(int value) { return value + 1; }
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)

    def test_modify_variable_full_frame_is_partial(self) -> None:
        report = verify('''
    @ModifyVariable(method = "setState", at = @At("STORE"), ordinal = 0)
    private State swap(State state) { return state; }
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.partial, 1)

    def test_modify_return_value(self) -> None:
        ok = verify('''
    @ModifyReturnValue(method = "count()I", at = @At("RETURN"))
    private int adjust(int original) { return original; }
''')
        self.assertEqual(ok.problems, [])
        self.assertEqual(ok.verified, 1)
        wrong = verify('''
    @ModifyReturnValue(method = "count()I", at = @At("RETURN"))
    private long adjust(long original) { return original; }
''')
        self.assertEqual(len(wrong.problems), 1)
        self.assertIn("handler returns long but int is required", wrong.problems[0])
        void_target = verify('''
    @ModifyReturnValue(method = "tick", at = @At("RETURN"))
    private int adjust(int original) { return original; }
''')
        self.assertEqual(len(void_target.problems), 1)
        self.assertIn("returns void", void_target.problems[0])

    def test_modify_expression_value_with_trailing_prefix(self) -> None:
        report = verify('''
    @ModifyExpressionValue(method = "render(La/b/Gui;I)V", at = @At(value = "INVOKE", target = "La/b/Foo;count()I"))
    private int adjust(int original, Gui gui) { return original; }
''')
        self.assertEqual(report.problems, [])
        self.assertEqual(report.verified, 1)


class ParsingTest(unittest.TestCase):
    def test_javap_signatures(self) -> None:
        declared, supertypes = vmt.parse_javap_signatures(CLASSES["a.b.Foo"], "a.b.Foo")
        self.assertEqual(supertypes, ["a.b.Base", "java.lang.Runnable"])
        self.assertEqual([m.descriptor for m in declared["<init>"]], ["(La/b/Source;)V"])
        self.assertTrue(declared["create"][0].is_static)
        self.assertFalse(declared["render"][0].is_static)
        self.assertEqual(len(declared["render"]), 2)
        self.assertNotIn("state", declared)
        self.assertIn("items", declared)

    def test_descriptor_types(self) -> None:
        arguments, return_type = vmt.descriptor_types("(La/b/Pos;[IJ)Ljava/util/List;")
        self.assertEqual([a.display() for a in arguments], ["Pos", "int[]", "long"])
        self.assertEqual(return_type.name, "java.util.List")

    def test_resolve_source_type(self) -> None:
        source = mixin_source("")
        nested = vmt.resolve_source_type("Foo.Inner", source, frozenset())
        self.assertEqual((nested.name, nested.resolved), ("a.b.Foo$Inner", True))
        inline = vmt.resolve_source_type("java.nio.file.Path", source, frozenset())
        self.assertEqual((inline.name, inline.resolved), ("java.nio.file.Path", True))
        array = vmt.resolve_source_type("String[]", source, frozenset())
        self.assertEqual((array.name, array.dims), ("java.lang.String", 1))
        generic = vmt.resolve_source_type("Operation<Stream<Holder<Pos>>>", source, frozenset())
        self.assertEqual(generic.name, vmt.OPERATION)
        unknown = vmt.resolve_source_type("Unknown", source, frozenset())
        self.assertFalse(unknown.resolved)
        self.assertTrue(vmt.resolve_source_type("T", source, frozenset({"T"})).wildcard)
        self.assertTrue(vmt.types_compatible(unknown, vmt.type_ref("x.y.Unknown")))
        self.assertFalse(vmt.types_compatible(vmt.type_ref("a.b.State"), vmt.type_ref("c.d.State")))

    def test_multiline_annotation_and_handler_parse(self) -> None:
        source = mixin_source('''
    @Redirect(
            require = 0,
            expect = 1,
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "La/b/Helper;check(La/b/Pos;)Z"))
    @SuppressWarnings("unused")
    private static <T> boolean redirect(
            final Helper helper, java.util.List<T> items, Pos... positions) {
        return true;
    }
''')
        sites = vmt.injector_sites(source)
        self.assertEqual(len(sites), 1)
        site = sites[0]
        self.assertEqual(site.kind, "Redirect")
        self.assertEqual(vmt.at_specs(site.attributes["at"])[0]["target"], "La/b/Helper;check(La/b/Pos;)Z")
        self.assertEqual(site.handler.name, "redirect")
        self.assertEqual([p.type_text for p in site.handler.params], ["Helper", "java.util.List<T>", "Pos..."])
        self.assertEqual(site.handler.type_params, frozenset({"T"}))

    def test_trace_has_one_line_per_handler(self) -> None:
        report = verify('''
    @Inject(method = "tick", at = @At("HEAD"))
    private void a(CallbackInfo ci) {}

    @Inject(method = "count", at = @At("HEAD"))
    private void b(CallbackInfo ci) {}

    @Inject(method = "*", at = @At("HEAD"))
    private void c(CallbackInfo ci) {}
''')
        self.assertEqual(len(report.trace), 3)
        self.assertEqual(sorted(line.split()[0] for line in report.trace), ["MISMATCH", "OK", "UNVERIFIABLE"])
        self.assertEqual(report.verified + report.unverifiable + len(report.problems), 3)


if __name__ == "__main__":
    unittest.main()
