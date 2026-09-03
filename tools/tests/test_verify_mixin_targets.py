import importlib.util
from pathlib import Path
import sys
import unittest


TOOL_PATH = Path(__file__).resolve().parents[1] / "verify_mixin_targets.py"
SPEC = importlib.util.spec_from_file_location("verify_mixin_targets", TOOL_PATH)
verifier = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = verifier
SPEC.loader.exec_module(verifier)


class VerifyMixinTargetsTest(unittest.TestCase):
    TARGET = (
        "(Lnet/minecraft/client/renderer/state/level/WorldBorderRenderState;"
        "Lcom/mojang/renderpearl/api/commands/RenderPass;"
        "Lnet/minecraft/world/phys/Vec3;D)V"
    )
    CORRECT_HANDLER = (
        "(Lnet/minecraft/client/renderer/state/level/WorldBorderRenderState;"
        "Lcom/mojang/renderpearl/api/commands/RenderPass;"
        "Lnet/minecraft/world/phys/Vec3;D"
        "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"
    )
    OLD_HANDLER = (
        "(Lnet/minecraft/client/renderer/state/level/WorldBorderRenderState;"
        "Lnet/minecraft/world/phys/Vec3;DD"
        "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"
    )

    def test_parses_multiline_explicit_inject(self):
        source = f'''\n@Inject(\n        method = "render{self.TARGET}",\n        at = @At("HEAD"),\n        cancellable = true)\nprivate void globe$cancelVanillaWorldBorder(Object state, Object pass,\n                                             Object camera, double distance,\n                                             CallbackInfo ci) {{\n}}\n'''
        self.assertEqual(
            [verifier.ExplicitInjectSpec(
                "render", self.TARGET, "globe$cancelVanillaWorldBorder")],
            verifier.explicit_inject_specs(source))

    def test_javap_constructor_name_maps_to_init(self):
        self.assertEqual(
            "<init>",
            verifier.jvm_method_name(
                "ChunkGenerator", "net.minecraft.world.level.chunk.ChunkGenerator"))

    def test_old_26_2_handler_shape_is_rejected(self):
        spec = verifier.ExplicitInjectSpec(
            "render", self.TARGET, "globe$cancelVanillaWorldBorder")
        errors = verifier.explicit_inject_descriptor_errors(
            spec,
            {"render": {self.TARGET}},
            {"globe$cancelVanillaWorldBorder": {self.OLD_HANDLER}})
        self.assertEqual(1, len(errors))
        self.assertIn("handler", errors[0])

    def test_26_3_handler_shape_is_accepted(self):
        spec = verifier.ExplicitInjectSpec(
            "render", self.TARGET, "globe$cancelVanillaWorldBorder")
        errors = verifier.explicit_inject_descriptor_errors(
            spec,
            {"render": {self.TARGET}},
            {"globe$cancelVanillaWorldBorder": {self.CORRECT_HANDLER}})
        self.assertEqual([], errors)


if __name__ == "__main__":
    unittest.main()
