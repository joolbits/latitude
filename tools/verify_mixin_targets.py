#!/usr/bin/env python3
"""Statically verify that every registered mixin's target class and method actually exist.

A green build does not prove a mixin applies. Mixin resolves its targets at class-load time, so a
renamed or moved target is invisible to javac and only surfaces when the game boots -- which on a
port is exactly the failure you want to catch before staging a jar.

This closes most of that gap without a client: it reads `globe.mixins.json`, resolves each mixin's
`@Mixin` target and every injector's `method = "..."` and `@At(target = "L...;name(...)...")`
against the *remapped* Minecraft jar Loom built for this target, and reports anything missing.

It also checks that every `@Shadow` member is DECLARED on the target class rather than inherited
from a supertype -- a shadow of an inherited member compiles fine, passes every other check here,
and then kills the class at load with "was not located in the target class". That hole shipped a
frozen world-list screen on 2026-08-26, which is why it is now covered.

It is not a substitute for a boot: it still cannot prove an injection point exists *inside* a method
(a `@At("INVOKE")` whose target call was removed). Run it first, then boot. `defaultRequire: 1`
remains the backstop.

Usage:
    python3 tools/verify_mixin_targets.py --classpath <remapped classpath> [--source-root .]
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

MIXIN_CONFIG = Path("src/main/resources/globe.mixins.json")
SOURCE_ROOT = Path("src/main/java")

# Both Mixin's own injectors and MixinExtras'. Omitting the MixinExtras ones is not a small gap:
# @ModifyReturnValue and @ModifyExpressionValue are used throughout this codebase, and a missing
# entry here means the verifier passes a mixin that cannot apply.
INJECTOR_ANNOTATIONS = ("@Inject", "@Redirect", "@ModifyArg", "@ModifyArgs",
                        "@ModifyVariable", "@ModifyConstant", "@WrapOperation", "@WrapWithCondition",
                        "@ModifyReturnValue", "@ModifyExpressionValue", "@WrapMethod")

# Injectors whose `method` names Latitude owns rather than Minecraft (handler names, not targets).
IGNORED_METHOD_TARGETS = {"<init>", "<clinit>"}


class Failure(RuntimeError):
    pass


@dataclass(frozen=True)
class ExplicitInjectSpec:
    target_method: str
    target_descriptor: str
    handler_method: str


JAVAP_OUTPUT_CACHE: dict[tuple[str, str, str], str | None] = {}


def javap_output(javap_bin: str, classpath: str, binary_name: str) -> str | None:
    """One cached javap read per classpath/class pair for all verifier lanes."""
    key = (javap_bin, classpath, binary_name)
    if key not in JAVAP_OUTPUT_CACHE:
        result = subprocess.run(
            [javap_bin, "-classpath", classpath, "-p", "-s", binary_name],
            check=False, capture_output=True, text=True,
        )
        JAVAP_OUTPUT_CACHE[key] = result.stdout if result.returncode == 0 else None
    return JAVAP_OUTPUT_CACHE[key]


def javap_members(javap_bin: str, jar: str, binary_name: str,
                  _seen: set[str] | None = None) -> set[str] | None:
    """Method names declared by a class *or inherited from its supertypes*, or None if absent.

    Inheritance matters here. `BlockState.canSurvive` is a perfectly valid injection target even
    though the method is declared on `BlockBehaviour$BlockStateBase` -- the JVM resolves it through
    the hierarchy, and so does Mixin. A declared-only check would report false positives, and the
    same blind spot is what lets a `@Shadow` on an inherited method slip through review.
    """
    _seen = _seen if _seen is not None else set()
    if binary_name in _seen:
        return set()
    _seen.add(binary_name)

    output = javap_output(javap_bin, jar, binary_name)
    if output is None:
        return None
    names: set[str] = set()
    supertypes: list[str] = []
    for line in output.splitlines():
        stripped = line.strip()
        header = re.match(r"^(?:public |final |abstract |static )*(?:class|interface) ([\w.$]+)(.*)$",
                          stripped)
        if header:
            # Split on the keywords rather than matching greedily. Loom's dev jar carries Fabric's
            # injected interfaces, so a real header reads
            #   class BlockState extends BlockBehaviour$BlockStateBase implements FabricBlockState
            # and a greedy capture after `extends` swallows the `implements` clause into one bogus
            # type name, silently losing the whole inherited hierarchy.
            tail = header.group(2).split("{")[0]
            for keyword in ("extends", "implements"):
                clause = re.search(
                    rf"\b{keyword}\s+(.*?)(?=\s+\b(?:extends|implements)\b|$)", tail)
                if not clause:
                    continue
                for supertype in clause.group(1).split(","):
                    supertype = re.sub(r"<.*?>", "", supertype).strip()
                    if supertype and supertype.startswith("net.minecraft"):
                        supertypes.append(supertype)
            continue
        match = re.search(r"([A-Za-z_$][A-Za-z0-9_$]*)\s*\(", stripped)
        if match:
            names.add(match.group(1))

    for supertype in supertypes:
        inherited = javap_members(javap_bin, jar, supertype, _seen)
        if inherited:
            names |= inherited
    return names


def javap_declared_members(javap_bin: str, jar: str, binary_name: str) -> set[str] | None:
    """Member names DECLARED by this class -- methods and fields, no inheritance.

    Deliberately not `javap_members`. Injectors resolve through the hierarchy, so inherited names
    are legitimate targets there. `@Shadow` does not: Mixin looks the member up on the target class
    itself, and a shadow of an inherited member fails at APPLY with "was not located in the target
    class", taking the whole class-load down with it. Checking a shadow against the inherited set
    would therefore pass exactly the case that breaks the game.
    """
    output = javap_output(javap_bin, jar, binary_name)
    if output is None:
        return None
    names: set[str] = set()
    for line in output.splitlines():
        stripped = line.strip()
        if not stripped.endswith(";") or stripped.startswith(("public class", "class", "interface")):
            continue
        if re.match(r"^(?:public |final |abstract |static )*(?:class|interface) ", stripped):
            continue
        method = re.search(r"([A-Za-z_$][A-Za-z0-9_$]*)\s*\(", stripped)
        if method:
            names.add(method.group(1))
            continue
        # A field: "private final java.lang.Runnable onClose;"
        field = re.search(r"([A-Za-z_$][A-Za-z0-9_$]*)\s*;$", stripped)
        if field:
            names.add(field.group(1))
    return names


def javap_method_descriptors(javap_bin: str, classpath: str,
                             binary_name: str) -> dict[str, set[str]] | None:
    """JVM descriptors for methods declared by one class, keyed by method name."""
    output = javap_output(javap_bin, classpath, binary_name)
    if output is None:
        return None
    descriptors: dict[str, set[str]] = {}
    current_method: str | None = None
    for line in output.splitlines():
        stripped = line.strip()
        method = re.search(r"([A-Za-z_$][A-Za-z0-9_$]*)\s*\(", stripped)
        if method and stripped.endswith(";"):
            current_method = jvm_method_name(method.group(1), binary_name)
            continue
        descriptor = re.match(r"descriptor:\s*(\(.*\).+)$", stripped)
        if descriptor and current_method is not None:
            descriptors.setdefault(current_method, set()).add(descriptor.group(1))
            current_method = None
    return descriptors


def jvm_method_name(javap_name: str, binary_name: str) -> str:
    """Translate javap's constructor spelling back to the JVM's <init> name."""
    binary_simple = binary_name.rsplit(".", 1)[-1]
    constructor_names = {binary_simple, binary_simple.rsplit("$", 1)[-1]}
    return "<init>" if javap_name in constructor_names else javap_name


def annotation_block(source: str, open_paren: int) -> tuple[str, int] | None:
    """Return one balanced annotation argument block, ignoring parentheses inside strings."""
    depth = 0
    quote = False
    escaped = False
    for index in range(open_paren, len(source)):
        char = source[index]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quote = False
            continue
        if char == '"':
            quote = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return source[open_paren + 1:index], index + 1
    return None


def explicit_inject_specs(source: str) -> list[ExplicitInjectSpec]:
    """Explicit @Inject target descriptors paired with their Java handler method names."""
    clean = strip_comments(source)
    specs: list[ExplicitInjectSpec] = []
    for match in re.finditer(r"@Inject\s*\(", clean):
        block_result = annotation_block(clean, clean.find("(", match.start()))
        if block_result is None:
            continue
        block, end = block_result
        method_value = re.search(r'method\s*=\s*"([^"]+)"', block)
        if method_value is None or "(" not in method_value.group(1):
            continue
        encoded = method_value.group(1)
        target_method, target_descriptor = encoded.split("(", 1)
        target_descriptor = "(" + target_descriptor
        declaration = re.search(
            r"\b(?:private|protected|public)\s+(?:static\s+)?[\w.$<>?, \[\]]+\s+"
            r"([A-Za-z_$][A-Za-z0-9_$]*)\s*\(",
            clean[end:])
        if declaration is None:
            continue
        specs.append(ExplicitInjectSpec(
            target_method.strip(), target_descriptor, declaration.group(1)))
    return specs


def expected_inject_handler_descriptor(target_descriptor: str) -> str:
    """Mixin @Inject handler descriptor for one exact target method descriptor."""
    close = target_descriptor.find(")")
    if close < 0:
        raise ValueError(f"invalid JVM method descriptor: {target_descriptor}")
    callback = ("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;"
                if target_descriptor[close + 1:] == "V"
                else "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;")
    return target_descriptor[:close] + callback + ")V"


def explicit_inject_descriptor_errors(spec: ExplicitInjectSpec,
                                      target_descriptors: dict[str, set[str]],
                                      handler_descriptors: dict[str, set[str]]) -> list[str]:
    """Return exact-target and handler-shape mismatches for one explicit @Inject."""
    errors: list[str] = []
    if spec.target_descriptor not in target_descriptors.get(spec.target_method, set()):
        errors.append(
            f"target '{spec.target_method}{spec.target_descriptor}' is absent")
    expected_handler = expected_inject_handler_descriptor(spec.target_descriptor)
    if expected_handler not in handler_descriptors.get(spec.handler_method, set()):
        errors.append(
            f"handler '{spec.handler_method}' must have descriptor {expected_handler}")
    return errors


def class_block(source: str, simple_name: str) -> str | None:
    """The one top-level @Mixin class named `simple_name`, from a possibly multi-class file.

    Java permits several top-level classes per file and this codebase uses it:
    `LevelLoadingScreenLatitudeOverlayMixin.java` holds `@Mixin(LevelLoadingScreen.class)` AND
    `@Mixin(Minecraft.class)`, each registered separately in globe.mixins.json.

    Scoping matters specifically for @Shadow. Checking a file's shadows against the UNION of every
    target in it does not raise a false alarm -- but it silently MISSES a real one, because a shadow
    of a member the enclosing class inherits still resolves against the *other* class's declared set.
    `level` and `player` are declared on Minecraft and not on LevelLoadingScreen; under a union check
    both pass no matter which class actually declares them, so a genuine inherited-shadow defect in
    either class would be masked by the other. A false negative here is the expensive direction: it
    is the shape that froze the world list.
    """
    segments = re.split(r"(?m)^(?=@Mixin\b)", source)
    pattern = re.compile(rf"\bclass\s+{re.escape(simple_name)}\b|\binterface\s+{re.escape(simple_name)}\b")
    for segment in segments:
        if segment.lstrip().startswith("@Mixin") and pattern.search(segment):
            return segment
    return None


def strip_comments(source: str) -> str:
    """Remove block/javadoc and line comments.

    Not optional for @Shadow parsing. Mixin code discusses `@Shadow` in prose constantly -- this
    file's own comments do -- and an unstripped scan matches the word in a javadoc, then runs to the
    next ';' and reports whatever identifier it lands on as a missing shadow. That produced two false
    positives on first run, one of them from the very comment explaining why the shadow was removed.
    A guard that cries wolf gets switched off, so it has to parse code only.
    """
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", source)


def shadow_members(source: str) -> list[str]:
    """Every member name claimed by an @Shadow, whether field or method.

    @Shadow may be followed by further annotations (@Final, @Mutable) before the declaration, and
    the declaration itself may carry generics whose bounds contain no parentheses -- so the first
    '(' after the declaration still belongs to the method name.
    """
    names: list[str] = []
    for match in re.finditer(r"@Shadow\b[^\n]*\n((?:\s*@\w+[^\n]*\n)*)([^;{]*)[;{]",
                             strip_comments(source)):
        declaration = " ".join(match.group(2).split())
        if not declaration:
            continue
        method = re.search(r"([A-Za-z_$][A-Za-z0-9_$]*)\s*\(", declaration)
        if method:
            names.append(method.group(1))
            continue
        field = re.search(r"([A-Za-z_$][A-Za-z0-9_$]*)\s*$", declaration)
        if field:
            names.append(field.group(1))
    return names


def is_declared_as_class(block: str, simple_name: str) -> bool:
    """True if `block` declares `simple_name` as a class, false if it's an interface (or absent).

    The distinction is the whole point of the check this feeds: an interface @Mixin is the CORRECT,
    safe form to cast to from ordinary code -- Mixin weaves it in as a real implemented interface,
    and this codebase already relies on that (RecreatedWorldPresetCarrier, VanillaOnlyWorldCreationState).
    A @Mixin CLASS is different: Mixin's transformer refuses to classload it if ordinary code casts
    to it directly, throwing IllegalClassLoadError at runtime despite a clean compile. Flagging
    interface casts here would be a false positive on the exact pattern the codebase should be using.
    """
    stripped = strip_comments(block)
    if re.search(rf"\binterface\s+{re.escape(simple_name)}\b", stripped):
        return False
    return bool(re.search(rf"\bclass\s+{re.escape(simple_name)}\b", stripped))


def scan_illegal_mixin_class_casts(root: Path, package: str,
                                    mixin_class_names: set[str]) -> list[str]:
    """Every non-mixin source file that casts to a @Mixin CLASS name, which is fatal at runtime.

    Caught live on 2026-08-27: such a cast compiles cleanly and then crashes the dev client on the
    very first tick that exercises it with `IllegalClassLoadError: ... cannot be referenced directly`.
    No amount of javac or the checks above catches it -- only actually booting did, until this.

    Matches on the bare substring `f"{name})"`, not an anchored `\(\s*name\s*\)` regex. That
    anchored form has a real gap: it cannot see a fully-qualified cast like
    `(com.example.globe.mixin.client.FooMixin)`, because the qualifying prefix sits between the `(`
    and the name. The bare substring does not care what precedes it, so it catches the qualified form
    the anchored form misses -- verified by planting exactly that qualified cast and confirming this
    form (not the anchored one) still fires.
    """
    if not mixin_class_names:
        return []
    # `package` is already "com.example.globe.mixin" -- its own last segment IS "mixin". Appending
    # another "/mixin" here built a directory that does not exist, so `is_relative_to` was never
    # true and every mixin source file got scanned as if it were ordinary code: the very first real
    # run flagged CreateWorldScreenInitRedirectMixin's own legitimate mixin-to-mixin cast.
    mixin_root = root / SOURCE_ROOT / package.replace(".", "/")
    offenders: list[str] = []
    for path in sorted((root / SOURCE_ROOT).rglob("*.java")):
        try:
            if path.is_relative_to(mixin_root):
                continue
        except ValueError:
            pass
        text = strip_comments(path.read_text())
        for name in mixin_class_names:
            if f"{name})" in text:
                rel = path.relative_to(root)
                offenders.append(f"{rel}: casts to @Mixin CLASS '{name}' from ordinary code -- "
                                  f"this compiles but throws IllegalClassLoadError at runtime; cast "
                                  f"to a plain interface the mixin implements instead")
    return offenders


def resolve_import(simple: str, source: str, mixin_package_hint: str) -> str | None:
    """Map a simple class name to a fully-qualified one using the file's own imports."""
    if "." in simple:
        return simple
    match = re.search(rf"^import\s+(?:static\s+)?([\w.]*\.{re.escape(simple)});", source, re.MULTILINE)
    if match:
        return match.group(1)
    return None


def mixin_targets(source: str, imports_from: str | None = None) -> list[str]:
    """Every class named by the @Mixin annotation, fully qualified where resolvable.

    `imports_from` exists because a single top-level class may be sliced out of a multi-class file
    (see class_block) and that slice carries no `import` lines. Resolving against the slice yields a
    bare simple name, javap cannot find it, and every @Shadow in that class is then reported missing
    -- a false-positive wave whose message names the correctly-resolved class, hiding the cause.
    """
    header = source
    imports = imports_from if imports_from is not None else source
    targets: list[str] = []

    # @Mixin(targets = "a.b.Outer$Inner") — already fully qualified.
    for raw in re.findall(r'@Mixin\s*\([^)]*targets\s*=\s*\{?\s*"([^"]+)"', header):
        targets.append(raw)

    # @Mixin(Foo.class) / @Mixin(value = Foo.class, priority = N) / @Mixin({A.class, B.class})
    for block in re.findall(r"@Mixin\s*\(([^)]*)\)", header):
        for simple in re.findall(r"([A-Za-z_$][\w.$]*)\.class", block):
            resolved = resolve_import(simple, imports, "")
            targets.append(resolved or simple)
    return targets


def injector_methods(source: str) -> list[str]:
    """Every Minecraft method name an injector claims to target."""
    names: list[str] = []
    for annotation in INJECTOR_ANNOTATIONS:
        for block in re.findall(rf"{re.escape(annotation)}\s*\((.*?)\)\s*(?:\n|$)", source, re.DOTALL):
            match = re.search(r'method\s*=\s*(\{[^}]*\}|"[^"]*")', block)
            if not match:
                continue
            for name in re.findall(r'"([^"]+)"', match.group(1)):
                # Strip any explicit descriptor: "render(Lnet/minecraft/...;)V"
                # A bare "*" deliberately matches every method in the target class; there is
                # nothing to resolve, and the @At target carries the real selection.
                if name.strip() == "*":
                    continue
                bare = name.split("(")[0].split("*")[0].strip()
                if bare and bare not in IGNORED_METHOD_TARGETS:
                    names.append(bare)
    return names


def at_targets(source: str) -> list[tuple[str, str]]:
    """(binary class name, method name) pairs named by @At(target = "L...;name(...)...")."""
    found: list[tuple[str, str]] = []
    for raw in re.findall(r'target\s*=\s*"L([^;]+);([A-Za-z_$<][\w$<>]*)\(', source):
        found.append((raw[0].replace("/", "."), raw[1]))
    return found


def main() -> int:
    parser = argparse.ArgumentParser()
    # A full classpath string, not a single file: Minecraft may be split across jars,
    # and letting javap resolve the whole path avoids guessing which one holds a class.
    parser.add_argument("--classpath", required=True)
    parser.add_argument("--mixin-classpath", required=True)
    parser.add_argument("--source-root", default=Path("."), type=Path)
    args = parser.parse_args()

    jar = args.classpath
    root = args.source_root.resolve()
    if not any(Path(part).is_file() for part in jar.split(":")):
        raise Failure("no readable jar on the supplied classpath")
    javap_bin = shutil.which("javap")
    if javap_bin is None:
        raise Failure("javap is unavailable")

    config = json.loads((root / MIXIN_CONFIG).read_text())
    package = config["package"]
    registered = list(config.get("mixins", [])) + list(config.get("client", []))

    problems: list[str] = []
    checked_classes = 0
    checked_methods = 0
    checked_shadows = 0
    mixin_class_names: set[str] = set()
    member_cache: dict[str, set[str] | None] = {}
    declared_cache: dict[str, set[str] | None] = {}
    descriptor_cache: dict[str, dict[str, set[str]] | None] = {}
    mixin_descriptor_cache: dict[str, dict[str, set[str]] | None] = {}
    checked_descriptors = 0

    def members(binary_name: str) -> set[str] | None:
        if binary_name not in member_cache:
            member_cache[binary_name] = javap_members(javap_bin, jar, binary_name)
        return member_cache[binary_name]

    def declared_members(binary_name: str) -> set[str] | None:
        if binary_name not in declared_cache:
            declared_cache[binary_name] = javap_declared_members(javap_bin, jar, binary_name)
        return declared_cache[binary_name]

    def descriptors(binary_name: str) -> dict[str, set[str]] | None:
        if binary_name not in descriptor_cache:
            descriptor_cache[binary_name] = javap_method_descriptors(
                javap_bin, jar, binary_name)
        return descriptor_cache[binary_name]

    def mixin_descriptors(binary_name: str) -> dict[str, set[str]] | None:
        if binary_name not in mixin_descriptor_cache:
            mixin_descriptor_cache[binary_name] = javap_method_descriptors(
                javap_bin, args.mixin_classpath, binary_name)
        return mixin_descriptor_cache[binary_name]

    for entry in registered:
        rel = (package + "." + entry).replace(".", "/") + ".java"
        path = root / SOURCE_ROOT / rel
        if not path.is_file():
            # A mixin may be declared as a secondary (non-public) top-level class inside another
            # file, which is legal Java -- so fall back to searching for its declaration.
            simple = entry.rsplit(".", 1)[-1]
            pattern = re.compile(rf"^\s*(?:public\s+|final\s+|abstract\s+)*class\s+{re.escape(simple)}\b",
                                 re.MULTILINE)
            path = next((candidate for candidate in
                         sorted((root / SOURCE_ROOT).rglob("*.java"))
                         if pattern.search(candidate.read_text())), None)
            if path is None:
                problems.append(f"{entry}: registered in globe.mixins.json but no source declares it")
                continue
        source = path.read_text()

        targets = mixin_targets(source)
        if not targets:
            problems.append(f"{entry}: no @Mixin target could be parsed")
            continue

        # Everything below is scoped to THIS registered class, never the file. A file may hold
        # several top-level @Mixin classes (see class_block); checking any of it against the union
        # of every target in the file cross-attributes members between unrelated classes, which
        # masks a real defect rather than raising a false one.
        block = class_block(source, entry.rsplit(".", 1)[-1]) or source
        targets = mixin_targets(block, source) or targets

        resolved_members: set[str] = set()
        declared: set[str] = set()
        for target in targets:
            checked_classes += 1
            found = members(target)
            if found is None:
                problems.append(f"{entry}: @Mixin target class not found in the remapped jar: {target}")
            else:
                resolved_members |= found
            own = declared_members(target)
            if own is not None:
                declared |= own

        if not resolved_members:
            continue

        for method in injector_methods(block):
            checked_methods += 1
            if method not in resolved_members:
                problems.append(
                    f"{entry}: injector targets '{method}', absent from {', '.join(targets)}")

        explicit_specs = explicit_inject_specs(block)
        if explicit_specs:
            mixin_binary_name = package + "." + entry
            compiled_handlers = mixin_descriptors(mixin_binary_name)
            if compiled_handlers is None:
                problems.append(
                    f"{entry}: compiled mixin class not found for descriptor verification")
            else:
                for explicit in explicit_specs:
                    checked_descriptors += 1
                    for target in targets:
                        target_methods = descriptors(target)
                        if target_methods is None:
                            continue
                        for error in explicit_inject_descriptor_errors(
                                explicit, target_methods, compiled_handlers):
                            problems.append(f"{entry}: {error} on {target}")

        # Collected regardless of whether this entry has any @Shadow: the cast-safety scan below
        # cares about every @Mixin CLASS, not just ones this codebase currently shadows a member of.
        simple_name = entry.rsplit(".", 1)[-1]
        if is_declared_as_class(block, simple_name):
            mixin_class_names.add(simple_name)

        # @Shadow resolves against the target class's OWN members, never an inherited one.
        block_targets = targets
        inherited_only = resolved_members - declared
        for shadowed in shadow_members(block):
            checked_shadows += 1
            if shadowed in declared:
                continue
            if shadowed in inherited_only:
                problems.append(
                    f"{entry}: @Shadow '{shadowed}' is INHERITED by {', '.join(block_targets)}, not declared "
                    f"on it -- Mixin fails at APPLY with 'was not located in the target class' and "
                    f"the class never loads. Use @Invoker/@Accessor on the declaring supertype instead")
            else:
                problems.append(
                    f"{entry}: @Shadow '{shadowed}' does not exist on {', '.join(block_targets)} "
                    f"or any supertype")

        for owner, method in at_targets(block):
            if owner.startswith("net.minecraft"):
                checked_methods += 1
                found = members(owner)
                if found is None:
                    problems.append(f"{entry}: @At target class not found: {owner}")
                elif method not in found:
                    problems.append(f"{entry}: @At targets '{owner}.{method}', which does not exist")

    cast_offenders = scan_illegal_mixin_class_casts(root, package, mixin_class_names)
    problems.extend(cast_offenders)

    if problems:
        print("MIXIN_TARGET_VERIFY_FAIL", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    print(f"MIXIN_TARGET_VERIFY_PASS mixins={len(registered)} "
          f"targetClasses={checked_classes} targetMethods={checked_methods} "
          f"targetDescriptors={checked_descriptors} shadowMembers={checked_shadows} "
          f"mixinClasses={len(mixin_class_names)}")
    print(f" classpathEntries={len(jar.split(':'))}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Failure as error:
        print(f"MIXIN_TARGET_VERIFY_FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
