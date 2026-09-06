#!/usr/bin/env python3
"""Statically verify that every registered mixin's target class and method actually exist.

A green build does not prove a mixin applies. Mixin resolves its targets at class-load time, so a
renamed or moved target is invisible to javac and only surfaces when the game boots -- which on a
port is exactly the failure you want to catch before staging a jar.

This closes most of that gap without a client: it reads `globe.mixins.json`, resolves each mixin's
`@Mixin` target and every injector's `method = "..."` and `@At(target = "L...;name(...)...")`
against the *remapped* Minecraft jar Loom built for this target, and reports anything missing.

It also checks that every `@Shadow` names a member the target class DECLARES. Mixin does not
resolve a shadow through the inheritance chain, so shadowing an inherited member compiles clean,
passes `check`, and then fails at apply time -- the class never loads and the client wedges on a
screen that simply never appears. That gap was described in this file for a while before it was
covered; it cost a frozen test build on the sibling line first.

Scope is per registered CLASS, never per file: one file may hold several top-level mixins with
different targets, and pooling their members lets a member declared on one target satisfy a
`@Shadow`, injector, or `@At` belonging to another. That is a false negative in exactly the shape
above, so `@Mixin` targets, `@Shadow`s, injectors and `@At`s are all read from the class body.

It also checks every injector HANDLER's declared parameters against the shape Mixin demands at
apply time: the target's parameter types plus the injector's own extras (CallbackInfo or
CallbackInfoReturnable, the Operation<> of a wrap, a wrapped call's receiver and arguments). Names
were matched before; descriptors were not, so a handler whose parameter list drifted on a port
passed here and then threw InvalidInjectionException at class load. Explicit descriptors in
`method = "name(...)"` are resolved exactly rather than reduced to the name.

It is not a substitute for a boot: it cannot prove an injection point exists *inside* a method
(a `@At("INVOKE")` whose target call was removed). Run it first, then boot. And assert the boot
actually reached the screen -- "zero mixin errors" from a run that never loaded the class is
worth nothing. `defaultRequire: 1` remains the backstop.

Usage:
    python3 tools/verify_mixin_targets.py --classpath <remapped classpath> [--source-root .]
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass, field
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

    result = subprocess.run(
        [javap_bin, "-classpath", jar, "-p", binary_name],
        check=False, capture_output=True, text=True,
    )
    if result.returncode != 0:
        return None
    names: set[str] = set()
    supertypes: list[str] = []
    for line in result.stdout.splitlines():
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
    """Members DECLARED by a class -- deliberately not the inherited set.

    `@Shadow` is the one case where inheritance must NOT be followed. Mixin resolves a shadow
    against the target class's own members; a shadow of a method the target merely INHERITS
    compiles clean, passes a `check`, and then fails at APPLY time, which kills the class load.
    That is not a crash -- the screen simply never appears and the client wedges.

    Checking the inherited set would pass exactly the case that breaks the game, so this is a
    separate function from `javap_members` rather than a flag on it. (Verified live: a
    `@Shadow` of `addRenderableWidget` -- declared on `Screen`, inherited by `CreateWorldScreen`
    -- froze a test build on the world list with "was not located in the target class".)
    """
    result = subprocess.run(
        [javap_bin, "-classpath", jar, "-p", binary_name],
        check=False, capture_output=True, text=True,
    )
    if result.returncode != 0:
        return None
    names: set[str] = set()
    for line in result.stdout.splitlines():
        stripped = line.strip()
        if re.match(r"^(?:public |private |protected |final |abstract |static )*(?:class|interface) ",
                    stripped):
            continue
        match = re.search(r"([A-Za-z_$][A-Za-z0-9_$]*)\s*[(;]", stripped)
        if match:
            names.add(match.group(1))
    return names


def strip_comments(source: str) -> str:
    """Blank out comments, preserving offsets so slicing stays aligned with the original text.

    Javadoc that merely MENTIONS `@Shadow` -- including a comment explaining why one was removed --
    would otherwise be parsed as a declaration. A guard that reports phantoms gets switched off.
    """
    out = re.sub(r"/\*.*?\*/", lambda m: re.sub(r"[^\n]", " ", m.group(0)), source, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", lambda m: " " * len(m.group(0)), out)


def mixin_classes(source: str) -> dict[str, tuple[str, str]]:
    """{simple class name: (its own @Mixin annotation, its own body)} for every mixin in the file.

    Scoping must be per CLASS, not per FILE. A single file may legally hold several top-level
    mixins with different targets -- this repo has one holding `@Mixin(LevelLoadingScreen.class)`
    and `@Mixin(Minecraft.class)`. Pooling a file's targets and checking members against the union
    lets a member declared on EITHER target satisfy a shadow belonging to the OTHER, which is a
    false negative in precisely the shape described in `javap_declared_members`.
    """
    text = strip_comments(source)
    found: dict[str, tuple[str, str]] = {}
    for annotation in re.finditer(r"@Mixin\s*\(", text):
        open_paren = text.index("(", annotation.start())
        depth = 0
        end = open_paren
        for index in range(open_paren, len(text)):
            if text[index] == "(":
                depth += 1
            elif text[index] == ")":
                depth -= 1
                if depth == 0:
                    end = index
                    break
        declaration = re.search(r"\b(?:class|interface)\s+([A-Za-z_$][\w$]*)", text[end:])
        if not declaration:
            continue
        try:
            body_start = text.index("{", end + declaration.end())
        except ValueError:
            continue
        depth = 0
        body_end = body_start
        for index in range(body_start, len(text)):
            if text[index] == "{":
                depth += 1
            elif text[index] == "}":
                depth -= 1
                if depth == 0:
                    body_end = index
                    break
        found[declaration.group(1)] = (text[annotation.start():end + 1], text[body_start:body_end + 1])
    return found


def shadow_members(body: str) -> list[str]:
    """Every member name claimed by a `@Shadow` in this class body."""
    names: list[str] = []
    for match in re.finditer(r"@Shadow\b([^;{]*)[;{]", body):
        declaration = re.sub(r"@\w+(?:\([^)]*\))?", " ", match.group(1))
        previous = None
        while previous != declaration:                      # nested generics need repeated passes
            previous = declaration
            declaration = re.sub(r"<[^<>]*>", " ", declaration)
        if "(" in declaration:
            method = re.findall(r"([A-Za-z_$][\w$]*)\s*\(", declaration)
            if method:
                names.append(method[0])
            continue
        tokens = re.findall(r"[A-Za-z_$][\w$]*", declaration.split("=")[0])
        if tokens:
            names.append(tokens[-1])
    return names


def resolve_import(simple: str, source: str, mixin_package_hint: str) -> str | None:
    """Map a simple class name to a fully-qualified one using the file's own imports."""
    if "." in simple:
        return simple
    match = re.search(rf"^import\s+(?:static\s+)?([\w.]*\.{re.escape(simple)});", source, re.MULTILINE)
    if match:
        return match.group(1)
    return None


def mixin_targets(header: str, source: str) -> list[str]:
    """Every class named by ONE @Mixin annotation, fully qualified where resolvable.

    `header` is a single annotation; `source` is the whole file, passed separately purely so
    imports still resolve. Slicing a class out of its file leaves the slice without the file's
    `import` lines, so resolving against the slice yields a bare name, javap cannot find it, and
    every shadow in the class is reported missing on a class that does declare them.
    """
    targets: list[str] = []

    # @Mixin(targets = "a.b.Outer$Inner") — already fully qualified.
    for raw in re.findall(r'@Mixin\s*\([^)]*targets\s*=\s*\{?\s*"([^"]+)"', header):
        targets.append(raw)

    # @Mixin(Foo.class) / @Mixin(value = Foo.class, priority = N) / @Mixin({A.class, B.class})
    for block in re.findall(r"@Mixin\s*\(([^)]*)\)", header):
        for simple in re.findall(r"([A-Za-z_$][\w.$]*)\.class", block):
            resolved = resolve_import(simple, source, "")
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


# ── Handler signature verification ──────────────────────────────────────────────────────────
#
# A name match is not an apply. Mixin resolves an injector's `method` selector against the
# target class's OWN methods and then checks the handler's JVM descriptor against the shape its
# injector contract demands. A handler whose parameter list no longer matches -- a parameter
# dropped on a port, CallbackInfo where CallbackInfoReturnable is required, a missing
# Operation<> -- compiles clean, passes every name check above, and throws
# InvalidInjectionException at class load. On the dedicated server that surfaces as "Failed to
# start the minecraft server" with exit code 0, which is how this gap stayed open: the name-only
# pass here reported MIXIN_TARGET_VERIFY_PASS while the headless run died at boot.
#
# The rules below are transcribed from the Mixin 0.8.7 and MixinExtras 0.5 sources, not from
# memory:
#   @Inject            CallbackInjector.Callback.checkDescriptor: (targetArgs..., CI)V or the
#                      short form (CI)V; with `locals =` the captured locals follow CI. With
#                      several candidate targets Mixin only fails when NONE of them matches.
#   @Redirect, @ModifyVariable, @ModifyConstant, @WrapOperation, @WrapWithCondition,
#   @ModifyReturnValue, @ModifyExpressionValue
#                      Injector.validateParams: an exact head (per injector) followed, at most,
#                      by a PREFIX of the target method's own arguments; anything beyond that is
#                      "unexpected additional method arguments".
#   @WrapMethod        WrapMethodInjector.checkSignature: exactly (targetArgs..., Operation)ret.
#   @ModifyArg         ModifyArgInjector: (T)T, or (all invoked args)T.
#   @ModifyArgs        ModifyArgsInjector.verifyTarget: (Args)V or (Args, targetArgs...)V.
# MixinExtras sugar (@Local, @Share, @Cancellable) is stripped before those checks and must be
# trailing; @Coerce widens one position to anything.
#
# Whatever the rules cannot decide from source plus javap -- captured locals, a field redirect
# without an opcode, a `method = "*"` selector, a type variable -- is counted UNVERIFIABLE and
# reported as such, never folded into PASS or FAIL. Handler parameter types are compared by
# fully-qualified name when the file's imports resolve them and by simple name otherwise.

INJECTOR_KINDS = tuple(annotation[1:] for annotation in INJECTOR_ANNOTATIONS)
TARGET_SHAPED_KINDS = ("Inject", "WrapMethod", "ModifyReturnValue", "ModifyArgs", "ModifyVariable")

PRIMITIVE_BY_DESCRIPTOR = {"B": "byte", "C": "char", "D": "double", "F": "float", "I": "int",
                           "J": "long", "S": "short", "Z": "boolean", "V": "void"}
BOXED_PRIMITIVES = {"byte": "java.lang.Byte", "char": "java.lang.Character",
                    "double": "java.lang.Double", "float": "java.lang.Float",
                    "int": "java.lang.Integer", "long": "java.lang.Long",
                    "short": "java.lang.Short", "boolean": "java.lang.Boolean",
                    "void": "java.lang.Void"}
CALLBACK_INFO = "org.spongepowered.asm.mixin.injection.callback.CallbackInfo"
CALLBACK_INFO_RETURNABLE = "org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable"
OPERATION = "com.llamalad7.mixinextras.injector.wrapoperation.Operation"
ARGS = "org.spongepowered.asm.mixin.injection.invoke.arg.Args"
SUGAR_ANNOTATIONS = {"Local", "Share", "Cancellable"}
JAVA_LANG_TYPES = {"Object", "String", "Integer", "Long", "Short", "Byte", "Character", "Boolean",
                   "Float", "Double", "Void", "Number", "Class", "Runnable", "Iterable",
                   "CharSequence", "Comparable", "Thread", "Throwable", "Exception",
                   "RuntimeException", "Error", "StringBuilder", "Enum", "Record"}
MODIFIER_KEYWORDS = {"public", "private", "protected", "static", "final", "synchronized",
                     "abstract", "default", "native", "strictfp"}


@dataclass(frozen=True)
class TypeRef:
    """One Java type as seen from either a descriptor or the handler's source text.

    `name` is a primitive keyword or a class name with '.'-separated packages (nested classes may
    appear with '$' from a descriptor or '.' from source; comparisons normalise both). `resolved`
    is False when only a simple name is known, in which case only simple names are compared.
    `wildcard` accepts any type: @Coerce, a type variable, or '?'.
    """
    name: str
    dims: int = 0
    resolved: bool = True
    wildcard: bool = False

    @property
    def simple(self) -> str:
        return re.split(r"[.$]", self.name)[-1] if self.name else "?"

    def display(self) -> str:
        return ("?" if self.wildcard else self.simple) + "[]" * self.dims


ANY_TYPE = TypeRef("?", resolved=False, wildcard=True)


def type_ref(name: str, dims: int = 0) -> TypeRef:
    return TypeRef(name, dims, True, False)


@dataclass(frozen=True)
class MethodSig:
    """One method as javap -s reports it: JVM descriptor plus the facts the rules need."""
    owner: str
    name: str
    descriptor: str
    is_static: bool

    def arguments(self) -> list[TypeRef]:
        return descriptor_types(self.descriptor)[0]

    def return_type(self) -> TypeRef:
        return descriptor_types(self.descriptor)[1]

    def label(self) -> str:
        return f"{self.owner}.{self.name}{self.descriptor}"


def parse_descriptor_type(descriptor: str, position: int) -> tuple[TypeRef, int]:
    dims = 0
    while descriptor[position] == "[":
        dims += 1
        position += 1
    char = descriptor[position]
    if char == "L":
        end = descriptor.index(";", position)
        return TypeRef(descriptor[position + 1:end].replace("/", "."), dims), end + 1
    if char not in PRIMITIVE_BY_DESCRIPTOR:
        raise ValueError(f"unreadable descriptor {descriptor!r} at offset {position}")
    return TypeRef(PRIMITIVE_BY_DESCRIPTOR[char], dims), position + 1


def descriptor_types(descriptor: str) -> tuple[list[TypeRef], TypeRef]:
    """Argument types and return type of one JVM method descriptor such as "(IJLa/B;)V"."""
    if not descriptor.startswith("(") or ")" not in descriptor:
        raise ValueError(f"not a method descriptor: {descriptor!r}")
    arguments: list[TypeRef] = []
    position = 1
    while descriptor[position] != ")":
        argument, position = parse_descriptor_type(descriptor, position)
        arguments.append(argument)
    return_type, _ = parse_descriptor_type(descriptor, position + 1)
    return arguments, return_type


def boxed(type_: TypeRef) -> TypeRef:
    if type_.dims == 0 and type_.name in BOXED_PRIMITIVES:
        return type_ref(BOXED_PRIMITIVES[type_.name])
    return type_


def is_void(type_: TypeRef) -> bool:
    return type_.dims == 0 and type_.name == "void" and not type_.wildcard


def types_compatible(found: TypeRef, expected: TypeRef) -> bool:
    if found.wildcard or expected.wildcard:
        return True
    if found.dims != expected.dims:
        return False
    if found.resolved and expected.resolved:
        return found.name.replace("$", ".") == expected.name.replace("$", ".")
    return found.simple == expected.simple


def signature_display(arguments: list[TypeRef], return_type: TypeRef) -> str:
    return "(" + ", ".join(argument.display() for argument in arguments) + ") -> " + return_type.display()


def strip_generics(text: str) -> str:
    depth = 0
    out: list[str] = []
    for char in text:
        if char == "<":
            depth += 1
        elif char == ">":
            depth -= 1
        elif depth == 0:
            out.append(char)
    return "".join(out)


# ── javap -s: declared methods with descriptors ──────────────────────────────────────────────

def parse_javap_signatures(output: str, binary_name: str) -> tuple[dict[str, list[MethodSig]], list[str]]:
    """({method name: [MethodSig...]} declared by the class, [supertypes]) from `javap -s -p`.

    Constructors come back under the JVM name `<init>`. Fields are skipped: a `descriptor:` line
    is only paired with the member line above it when that line carries a parameter list.
    """
    declared: dict[str, list[MethodSig]] = {}
    supertypes: list[str] = []
    simple = binary_name.rsplit(".", 1)[-1]
    constructor_names = {simple, simple.rsplit("$", 1)[-1]}
    pending: str | None = None
    for line in output.splitlines():
        stripped = line.strip()
        header = re.match(r"^(?:public |final |abstract |static |sealed |non-sealed )*"
                          r"(?:class|interface|enum|record) ([\w.$]+)(.*)$", stripped)
        if header and stripped.endswith("{"):
            tail = strip_generics(header.group(2).split("{")[0])
            for keyword in ("extends", "implements"):
                clause = re.search(rf"\b{keyword}\s+(.*?)(?=\s+\b(?:extends|implements|permits)\b|$)", tail)
                if not clause:
                    continue
                for supertype in clause.group(1).split(","):
                    supertype = supertype.strip()
                    if supertype and supertype != "java.lang.Object":
                        supertypes.append(supertype)
            continue
        descriptor = re.match(r"^descriptor:\s*(\S+)$", stripped)
        if descriptor:
            if pending is not None and "(" in pending and descriptor.group(1).startswith("("):
                prefix = pending.split("(", 1)[0]
                name_match = re.search(r"([A-Za-z_$][\w$]*)\s*$", prefix)
                if name_match:
                    name = name_match.group(1)
                    if name in constructor_names:
                        name = "<init>"
                    modifiers = strip_generics(prefix[:name_match.start()])
                    is_static = re.search(r"\bstatic\b", modifiers) is not None
                    declared.setdefault(name, []).append(
                        MethodSig(binary_name, name, descriptor.group(1), is_static))
            pending = None
            continue
        pending = stripped if stripped.endswith(";") else None
    return declared, supertypes


class SignatureIndex:
    """Cached `javap -s -p` reads for the descriptor checks; separate from the name caches above,
    which parse `javap -p` output and would misread `descriptor:` lines as members."""

    def __init__(self, javap_bin: str, classpath: str) -> None:
        self.javap_bin = javap_bin
        self.classpath = classpath
        self._cache: dict[str, tuple[dict[str, list[MethodSig]], list[str]] | None] = {}

    def javap_output(self, binary_name: str) -> str | None:
        result = subprocess.run(
            [self.javap_bin, "-classpath", self.classpath, "-p", "-s", binary_name],
            check=False, capture_output=True, text=True,
        )
        return result.stdout if result.returncode == 0 else None

    def _info(self, binary_name: str) -> tuple[dict[str, list[MethodSig]], list[str]] | None:
        if binary_name not in self._cache:
            output = self.javap_output(binary_name)
            self._cache[binary_name] = None if output is None else parse_javap_signatures(output, binary_name)
        return self._cache[binary_name]

    def declared(self, binary_name: str) -> dict[str, list[MethodSig]] | None:
        info = self._info(binary_name)
        return info[0] if info is not None else None

    def find(self, binary_name: str, method: str, _seen: set[str] | None = None) -> list[MethodSig] | None:
        """Overloads of `method` declared on `binary_name` or, failing that, on the nearest
        supertype that declares them; None when the class itself is unreadable."""
        _seen = _seen if _seen is not None else set()
        if binary_name in _seen:
            return []
        _seen.add(binary_name)
        info = self._info(binary_name)
        if info is None:
            return None
        declared, supertypes = info
        if method in declared:
            return list(declared[method])
        for supertype in supertypes:
            inherited = self.find(supertype, method, _seen)
            if inherited:
                return inherited
        return []


# ── Source side: injector annotations and their handler methods ─────────────────────────────

def balanced_span(text: str, open_index: int, open_char: str = "(", close_char: str = ")") -> int:
    """Index of the bracket closing the one at `open_index`, ignoring brackets inside strings."""
    depth = 0
    quote: str | None = None
    index = open_index
    while index < len(text):
        char = text[index]
        if quote:
            if char == "\\":
                index += 1
            elif char == quote:
                quote = None
        elif char in "\"'":
            quote = char
        elif char == open_char:
            depth += 1
        elif char == close_char:
            depth -= 1
            if depth == 0:
                return index
        index += 1
    return -1


def split_top_level(text: str, separator: str = ",") -> list[str]:
    """Split on `separator` outside (), {}, <>, [] and string literals."""
    parts: list[str] = []
    depth = 0
    quote: str | None = None
    current: list[str] = []
    index = 0
    while index < len(text):
        char = text[index]
        if quote:
            current.append(char)
            if char == "\\" and index + 1 < len(text):
                current.append(text[index + 1])
                index += 1
            elif char == quote:
                quote = None
        elif char in "\"'":
            quote = char
            current.append(char)
        elif char in "({<[":
            depth += 1
            current.append(char)
        elif char in ")}>]":
            depth -= 1
            current.append(char)
        elif char == separator and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(char)
        index += 1
    parts.append("".join(current))
    return [part.strip() for part in parts if part.strip()]


def parse_attributes(inner: str) -> dict[str, str]:
    """{attribute: raw value} for one annotation's argument list; a lone value is 'value'."""
    attributes: dict[str, str] = {}
    for part in split_top_level(inner):
        match = re.match(r"^([A-Za-z_]\w*)\s*=\s*(.*)$", part, re.DOTALL)
        if match:
            attributes[match.group(1)] = match.group(2).strip()
        else:
            attributes.setdefault("value", part)
    return attributes


def string_values(raw: str | None) -> list[str]:
    return re.findall(r'"((?:[^"\\]|\\.)*)"', raw) if raw else []


def at_specs(raw: str | None) -> list[dict[str, str]]:
    """Parsed @At(...) annotations inside an `at =` value (single or array)."""
    specs: list[dict[str, str]] = []
    if not raw:
        return specs
    for match in re.finditer(r"@At\s*\(", raw):
        end = balanced_span(raw, match.end() - 1)
        if end < 0:
            continue
        attributes = parse_attributes(raw[match.end():end])
        spec: dict[str, str] = {}
        for key in ("value", "target", "opcode"):
            if key in attributes:
                values = string_values(attributes[key])
                spec[key] = values[0] if values else attributes[key].rsplit(".", 1)[-1]
        specs.append(spec)
    return specs


@dataclass(frozen=True)
class HandlerParam:
    type_text: str
    annotations: tuple[str, ...]
    generic: str | None


@dataclass(frozen=True)
class HandlerMethod:
    name: str
    return_text: str
    params: tuple[HandlerParam, ...]
    type_params: frozenset[str]


@dataclass(frozen=True)
class InjectorSite:
    kind: str
    attributes: dict[str, str]
    handler: HandlerMethod | None


def top_level_generic(text: str) -> str | None:
    """The text between the outermost '<' and '>' of a type, or None."""
    start = text.find("<")
    if start < 0:
        return None
    end = balanced_span(text, start, "<", ">")
    return text[start + 1:end].strip() if end > start else None


def strip_leading_annotations(text: str) -> tuple[list[str], str]:
    """(simple annotation names, remaining text) for the annotations that open `text`."""
    names: list[str] = []
    text = text.lstrip()
    while text.startswith("@"):
        match = re.match(r"@([\w.]+)", text)
        if not match:
            break
        names.append(match.group(1).rsplit(".", 1)[-1])
        rest = text[match.end():]
        if rest.lstrip().startswith("("):
            open_index = len(text) - len(rest.lstrip())
            close_index = balanced_span(text, open_index)
            if close_index < 0:
                break
            text = text[close_index + 1:].lstrip()
        else:
            text = rest.lstrip()
    return names, text


def parse_handler_param(text: str) -> HandlerParam | None:
    annotations, rest = strip_leading_annotations(text)
    rest = re.sub(r"\bfinal\b", " ", rest).strip()
    name_match = re.search(r"([A-Za-z_$][\w$]*)\s*$", rest)
    if not name_match or name_match.start() == 0:
        return None
    type_text = rest[:name_match.start()].strip()
    if not type_text:
        return None
    return HandlerParam(type_text, tuple(annotations), top_level_generic(type_text))


def parse_handler(text: str, start: int) -> HandlerMethod | None:
    """The method declared right after an injector annotation that ends at `start`."""
    _, remainder = strip_leading_annotations(text[start:])
    header_end = len(remainder)
    depth = 0
    for index, char in enumerate(remainder):
        if char in "(<":
            depth += 1
        elif char in ")>":
            depth -= 1
        elif char in "{;" and depth == 0:
            header_end = index
            break
    header = remainder[:header_end]
    open_index = -1
    depth = 0
    for index, char in enumerate(header):
        if char == "<":
            depth += 1
        elif char == ">":
            depth -= 1
        elif char == "(" and depth == 0:
            open_index = index
            break
    if open_index < 0:
        return None
    close_index = balanced_span(header, open_index)
    if close_index < 0:
        return None
    before = header[:open_index].strip()
    name_match = re.search(r"([A-Za-z_$][\w$]*)\s*$", before)
    if not name_match:
        return None
    declaration = before[:name_match.start()].strip()
    modifier = re.compile(r"^(?:" + "|".join(MODIFIER_KEYWORDS) + r")\b\s*")
    while modifier.match(declaration):
        declaration = modifier.sub("", declaration, count=1)
    type_params: set[str] = set()
    if declaration.startswith("<"):
        end = balanced_span(declaration, 0, "<", ">")
        for part in split_top_level(declaration[1:end]):
            type_params.add(part.split()[0])
        declaration = declaration[end + 1:].strip()
    params: list[HandlerParam] = []
    for part in split_top_level(header[open_index + 1:close_index]):
        param = parse_handler_param(part)
        if param is None:
            return None
        params.append(param)
    return HandlerMethod(name_match.group(1), " ".join(declaration.split()), tuple(params), frozenset(type_params))


def injector_sites(block: str) -> list[InjectorSite]:
    """Every injector annotation in one class body, paired with the handler it decorates."""
    text = strip_comments(block)
    sites: list[InjectorSite] = []
    pattern = re.compile(r"@(" + "|".join(INJECTOR_KINDS) + r")\s*\(")
    for match in pattern.finditer(text):
        end = balanced_span(text, match.end() - 1)
        if end < 0:
            continue
        attributes = parse_attributes(text[match.end():end])
        sites.append(InjectorSite(match.group(1), attributes, parse_handler(text, end + 1)))
    return sites


def class_type_params(block: str) -> frozenset[str]:
    text = strip_comments(block)
    match = re.search(r"\b(?:class|interface)\s+[A-Za-z_$][\w$]*\s*<", text)
    if not match:
        return frozenset()
    end = balanced_span(text, match.end() - 1, "<", ">")
    return frozenset(part.split()[0] for part in split_top_level(text[match.end():end]))


def resolve_source_type(type_text: str, source: str, type_params: frozenset[str]) -> TypeRef:
    """A handler parameter or return type as written, mapped onto the descriptor's world."""
    text = strip_generics(type_text).strip()
    dims = text.count("[]") + (1 if "..." in text else 0)
    text = text.replace("[]", "").replace("...", "").strip()
    if not text or text == "?":
        return ANY_TYPE
    if text in PRIMITIVE_BY_DESCRIPTOR.values():
        return TypeRef(text, dims)
    if text in type_params:
        return ANY_TYPE
    if "." in text:
        segments = text.split(".")
        if segments[0][:1].islower():
            for index, segment in enumerate(segments):
                if segment[:1].isupper():
                    return TypeRef(".".join(segments[:index + 1])
                                   + "".join("$" + nested for nested in segments[index + 1:]), dims)
            return TypeRef(text, dims)
        outer = resolve_import(segments[0], source, "")
        if outer:
            return TypeRef(outer + "".join("$" + nested for nested in segments[1:]), dims)
        return TypeRef(text.replace(".", "$"), dims, resolved=False)
    imported = resolve_import(text, source, "")
    if imported:
        return TypeRef(imported, dims)
    if text in JAVA_LANG_TYPES:
        return TypeRef("java.lang." + text, dims)
    return TypeRef(text, dims, resolved=False)


def parse_member_target(target: str) -> tuple[str | None, str, str | None, bool]:
    """(owner, name, descriptor, is_field) from an @At target such as "La/B;name(I)V" or
    "La/B;field:I"; owner is None when the target names no class."""
    text = target.strip()
    owner: str | None = None
    if text.startswith("L") and ";" in text:
        owner_end = text.index(";")
        owner = text[1:owner_end].replace("/", ".")
        text = text[owner_end + 1:]
    if ":" in text and "(" not in text:
        name, descriptor = text.split(":", 1)
        return owner, name, descriptor, True
    if "(" in text:
        name, descriptor = text.split("(", 1)
        return owner, name, "(" + descriptor, False
    return owner, text, None, False


# ── Expected shapes and the comparison ──────────────────────────────────────────────────────

@dataclass(frozen=True)
class Shape:
    """What the injector contract demands of the handler: an exact `head`, its return type, and
    the trailing policy -- a "prefix" of the target method's arguments, "all" of them, "none",
    or "locals" (captured locals after an @Inject callback, not checkable here)."""
    head: tuple[TypeRef, ...]
    return_type: TypeRef
    trailing: str
    label: str = ""


@dataclass
class SignatureReport:
    """Per-class outcome. `trace` holds one line per injector handler so a PASS can be audited
    against the handlers it actually looked at rather than trusted as a bare count."""
    problems: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    trace: list[str] = field(default_factory=list)
    verified: int = 0
    partial: int = 0
    unverifiable: int = 0


def lvt_slots(target: MethodSig) -> dict[int, TypeRef]:
    """Local-variable-table slot -> argument type, the way Mixin's argsOnly discriminator sees it:
    slot 0 is `this` on instance methods and long/double arguments take two slots."""
    slots: dict[int, TypeRef] = {}
    slot = 0 if target.is_static else 1
    if not target.is_static:
        slots[0] = type_ref(target.owner)
    for argument in target.arguments():
        slots[slot] = argument
        slot += 2 if argument.dims == 0 and argument.name in ("long", "double") else 1
    return slots


def invoked_shape(site: InjectorSite, index: SignatureIndex) -> tuple[list[TypeRef], TypeRef, str] | str:
    """([receiver?, args...], return type, label) of the member an invoke-style injector wraps,
    read from its @At, or a reason string when that cannot be determined statically."""
    specs = at_specs(site.attributes.get("at"))
    if len(specs) != 1:
        return "no single @At to derive the wrapped member from"
    spec = specs[0]
    value = spec.get("value", "").upper()
    target = spec.get("target")
    if not target:
        return f"@At({value or '?'}) names no target member"
    owner, name, descriptor, is_field = parse_member_target(target)
    if owner is None:
        return f"@At target {target!r} names no owner class"
    if value == "INVOKE":
        candidates = index.find(owner, name)
        if candidates is None:
            return f"@At owner {owner} is not on the classpath"
        if descriptor:
            candidates = [candidate for candidate in candidates if candidate.descriptor == descriptor]
        if len(candidates) != 1:
            return f"@At target {owner}.{name}{descriptor or ''} resolves to {len(candidates)} methods"
        method = candidates[0]
        arguments, return_type = descriptor_types(method.descriptor)
        receiver = [] if method.is_static else [type_ref(owner)]
        return receiver + arguments, return_type, f"{owner}.{name}{method.descriptor}"
    if value == "FIELD" and is_field and descriptor:
        field_type, _ = parse_descriptor_type(descriptor, 0)
        opcode = spec.get("opcode", "").upper()
        if opcode == "GETFIELD":
            return [type_ref(owner)], field_type, f"GETFIELD {owner}.{name}"
        if opcode == "GETSTATIC":
            return [], field_type, f"GETSTATIC {owner}.{name}"
        if opcode == "PUTFIELD":
            return [type_ref(owner), field_type], type_ref("void"), f"PUTFIELD {owner}.{name}"
        if opcode == "PUTSTATIC":
            return [field_type], type_ref("void"), f"PUTSTATIC {owner}.{name}"
        return f"@At(FIELD) on {owner}.{name} carries no opcode, so the access kind is unknown"
    return f"@At({value or '?'}) is not an invoke-style injection point modelled here"


def expected_shape(site: InjectorSite, target: MethodSig | None, index: SignatureIndex) -> Shape | str:
    """The shape one injector demands, given the enclosing target method where it matters.

    A string is a reason the shape cannot be derived statically (reported UNVERIFIABLE); a string
    starting with '!' is a contract violation that needs no handler to be wrong (reported FAIL)."""
    kind = site.kind
    if kind == "Inject":
        assert target is not None
        callback = CALLBACK_INFO if is_void(target.return_type()) else CALLBACK_INFO_RETURNABLE
        return Shape(tuple(target.arguments()) + (type_ref(callback),), type_ref("void"),
                     "locals" if "locals" in site.attributes else "none", target.label())
    if kind == "WrapMethod":
        assert target is not None
        return Shape(tuple(target.arguments()) + (type_ref(OPERATION),), target.return_type(), "none", target.label())
    if kind == "ModifyReturnValue":
        assert target is not None
        if is_void(target.return_type()):
            return f"!{target.label()} returns void, so there is no return value to modify"
        return Shape((target.return_type(),), target.return_type(), "prefix", target.label())
    if kind == "ModifyArgs":
        return Shape((type_ref(ARGS),), type_ref("void"), "all", target.label() if target else "")
    if kind == "ModifyConstant":
        constant = site.attributes.get("constant", "")
        key = re.search(r"\b(intValue|floatValue|longValue|doubleValue|stringValue|classValue|nullValue)\b", constant)
        constant_type = {"intValue": type_ref("int"), "floatValue": type_ref("float"),
                         "longValue": type_ref("long"), "doubleValue": type_ref("double"),
                         "stringValue": type_ref("java.lang.String"), "classValue": type_ref("java.lang.Class"),
                         "nullValue": type_ref("java.lang.Object")}.get(key.group(1) if key else "", ANY_TYPE)
        return Shape((constant_type,), constant_type, "prefix", "constant")
    if kind == "ModifyVariable":
        assert target is not None
        return Shape((), ANY_TYPE, "prefix", target.label())
    invoked = invoked_shape(site, index)
    if isinstance(invoked, str):
        return invoked
    arguments, return_type, label = invoked
    if kind == "Redirect":
        return Shape(tuple(arguments), return_type, "prefix", label)
    if kind == "WrapOperation":
        return Shape(tuple(arguments) + (type_ref(OPERATION),), return_type, "prefix", label)
    if kind == "WrapWithCondition":
        return Shape(tuple(arguments), type_ref("boolean"), "prefix", label)
    if kind == "ModifyExpressionValue":
        if is_void(return_type):
            return f"!{label} returns void, so there is no value to modify"
        return Shape((return_type,), return_type, "prefix", label)
    if kind == "ModifyArg":
        return Shape(tuple(arguments), return_type, "none", label)
    return f"@{kind} has no signature rule here"


def check_inject(found: list[TypeRef], found_return: TypeRef, shape: Shape) -> tuple[str, str]:
    callback = shape.head[-1]
    expected = signature_display(list(shape.head), shape.return_type)
    actual = signature_display(found, found_return)
    if not is_void(found_return) and not found_return.wildcard:
        return "mismatch", f"handler must return void, found {found_return.display()}"
    if len(found) == 1 and types_compatible(found[0], callback) and shape.trailing != "locals":
        return "ok", "short form"
    if len(found) < len(shape.head):
        return "mismatch", f"expected {expected} (or the short form ({callback.simple}) -> void), found {actual}"
    for position, (have, want) in enumerate(zip(found, shape.head)):
        if types_compatible(have, want):
            continue
        if position == len(shape.head) - 1 and have.simple in ("CallbackInfo", "CallbackInfoReturnable"):
            reason = "the target returns void" if want.simple == "CallbackInfo" else "the target returns a value"
            return "mismatch", f"{want.simple} is required at position {position + 1} because {reason}, found {have.simple}; expected {expected}"
        return "mismatch", (f"parameter {position + 1} is {have.display()} but the target's argument "
                            f"{position + 1} is {want.display()}; expected {expected}, found {actual}")
    if len(found) == len(shape.head):
        return "ok", ""
    if shape.trailing == "locals":
        return "partial", f"{len(found) - len(shape.head)} captured local(s) after the callback are not checked here"
    return "mismatch", (f"{len(found) - len(shape.head)} parameter(s) follow the callback but the injector "
                        f"declares no locals capture; expected {expected}, found {actual}")


def check_modify_arg(found: list[TypeRef], found_return: TypeRef, shape: Shape) -> tuple[str, str]:
    invoked = list(shape.head)
    actual = signature_display(found, found_return)
    if found_return.wildcard:
        return "partial", "handler return type is not resolvable"
    if len(found) == 1:
        if not types_compatible(found[0], found_return):
            return "mismatch", f"@ModifyArg must return its parameter type, found {actual}"
        if not any(types_compatible(found[0], argument) for argument in invoked):
            return "mismatch", (f"no argument of {shape.label} has type {found[0].display()}; "
                                f"its arguments are {signature_display(invoked, shape.return_type)}")
        return "ok", ""
    if len(found) != len(invoked) or not all(types_compatible(have, want) for have, want in zip(found, invoked)):
        return "mismatch", (f"expected one argument or all invoked arguments "
                            f"{signature_display(invoked, found_return)}, found {actual}")
    return "ok", ""


def check_trailing(rest: list[TypeRef], shape: Shape, target: MethodSig | None,
                   head: list[TypeRef], found: list[TypeRef], found_return: TypeRef) -> tuple[str, str]:
    """Parameters after the exact head: none, a prefix of the target's arguments, or all of them."""
    if not rest:
        return "ok", ""
    actual = signature_display(found, found_return)
    if shape.trailing == "none":
        return "mismatch", (f"{len(rest)} unexpected additional parameter(s) after "
                            f"{signature_display(head, shape.return_type)}; found {actual}")
    if target is None:
        return "partial", f"{len(rest)} trailing parameter(s) could not be checked against the enclosing method's arguments"
    arguments = target.arguments()
    if shape.trailing == "all" and len(rest) != len(arguments):
        return "mismatch", (f"trailing parameters must be all {len(arguments)} target arguments "
                            f"{signature_display(arguments, target.return_type())}, found {len(rest)}")
    if len(rest) > len(arguments):
        return "mismatch", f"{len(rest)} trailing parameter(s) exceed the target's {len(arguments)} argument(s); found {actual}"
    for position, (have, want) in enumerate(zip(rest, arguments)):
        if not types_compatible(have, want):
            return "mismatch", (f"trailing parameter {len(head) + position + 1} is {have.display()} but the "
                                f"target's argument {position + 1} is {want.display()} (trailing parameters "
                                f"must be a prefix of {signature_display(arguments, target.return_type())}); found {actual}")
    return "ok", ""


def check_head(kind: str, found: list[TypeRef], found_return: TypeRef, shape: Shape,
               target: MethodSig | None) -> tuple[str, str]:
    """Injector.validateParams: exact head and return type, then the trailing policy."""
    expected = signature_display(list(shape.head), shape.return_type)
    actual = signature_display(found, found_return)
    if kind == "ModifyConstant" and not found:
        return "mismatch", "@ModifyConstant handler needs the constant as its first parameter"
    if not types_compatible(found_return, shape.return_type):
        return "mismatch", (f"handler returns {found_return.display()} but {shape.return_type.display()} "
                            f"is required; expected {expected}, found {actual}")
    if len(found) < len(shape.head):
        return "mismatch", f"expected {expected}, found {actual}"
    for position, (have, want) in enumerate(zip(found, shape.head)):
        if not types_compatible(have, want):
            return "mismatch", (f"parameter {position + 1} is {have.display()} but {want.display()} is "
                                f"required; expected {expected}, found {actual}")
    return check_trailing(found[len(shape.head):], shape, target, list(shape.head), found, found_return)


def check_modify_variable(site: InjectorSite, found: list[TypeRef], found_return: TypeRef,
                          target: MethodSig) -> tuple[str, str]:
    """@ModifyVariable: (T [, target args prefix]) -> T, with T pinned by argsOnly/index."""
    if not found:
        return "mismatch", "@ModifyVariable handler needs the variable as its first parameter"
    if found_return.wildcard or not types_compatible(found_return, found[0]):
        return "mismatch", f"@ModifyVariable must return its first parameter's type, found {signature_display(found, found_return)}"
    variable = found[0]
    arguments = target.arguments()
    args_only = site.attributes.get("argsOnly", "false").strip() == "true"
    index_value = site.attributes.get("index", "").strip()
    ordinal_value = site.attributes.get("ordinal", "").strip()
    status, detail = "ok", ""
    if args_only and index_value.lstrip("-").isdigit():
        slot = int(index_value)
        slots = lvt_slots(target)
        if slot not in slots or slot == 0 and not target.is_static:
            status, detail = "mismatch", f"argsOnly index {slot} is not an argument slot of {target.label()}"
        elif not types_compatible(variable, slots[slot]):
            status, detail = "mismatch", (f"argsOnly index {slot} is a {slots[slot].display()} in {target.label()}, "
                                          f"but the handler modifies a {variable.display()}")
    elif args_only:
        matching = [argument for argument in arguments if types_compatible(variable, argument)]
        wanted = int(ordinal_value) + 1 if ordinal_value.isdigit() else 1
        if len(matching) < wanted:
            status, detail = "mismatch", (f"{target.label()} has {len(matching)} argument(s) of type "
                                          f"{variable.display()}, fewer than the {wanted} the selector needs")
        elif not ordinal_value and len(matching) > 1:
            status, detail = "mismatch", (f"{target.label()} has {len(matching)} arguments of type "
                                          f"{variable.display()}; implicit selection is ambiguous")
    else:
        status, detail = "partial", "the variable is selected from the full local-variable table, which is not visible here"
    if status == "mismatch":
        return status, detail
    trailing_status, trailing_detail = check_trailing(
        found[1:], Shape((variable,), variable, "prefix"), target, [variable], found, found_return)
    if trailing_status != "ok":
        return trailing_status, trailing_detail
    return status, detail


def handler_types(handler: HandlerMethod, source: str,
                  type_params: frozenset[str]) -> tuple[list[TypeRef], TypeRef, list[HandlerParam], str | None]:
    """(non-sugar parameter types, return type, non-sugar params, problem) with sugar stripped."""
    params: list[HandlerParam] = []
    seen_sugar = False
    for param in handler.params:
        if any(annotation in SUGAR_ANNOTATIONS for annotation in param.annotations):
            seen_sugar = True
            continue
        if seen_sugar:
            return [], ANY_TYPE, [], "sugared (@Local/@Share) parameters must be trailing; MixinExtras refuses non-trailing sugar"
        params.append(param)
    scope = type_params | handler.type_params
    types = [ANY_TYPE if "Coerce" in param.annotations else resolve_source_type(param.type_text, source, scope)
             for param in params]
    return types, resolve_source_type(handler.return_text, source, scope), params, None


def generic_argument_problem(params: list[HandlerParam], types: list[TypeRef], wrapper: str,
                             expected_return: TypeRef, source: str, type_params: frozenset[str]) -> str | None:
    """`CallbackInfoReturnable<X>` / `Operation<X>` must carry the boxed return type. A wrong X does
    not fail at apply time -- generics are erased -- but the handler then casts the wrong type."""
    if expected_return.wildcard:
        return None
    for param, type_ in zip(params, types):
        if type_.wildcard or type_.simple != wrapper or not param.generic:
            continue
        generic = resolve_source_type(param.generic, source, type_params)
        if generic.wildcard or not generic.resolved:
            return None
        want = boxed(expected_return)
        if not types_compatible(generic, want):
            return (f"{wrapper}<{generic.display()}> does not match the target's return type "
                    f"{expected_return.display()} (expected {wrapper}<{want.display()}>); this does "
                    f"not fail at apply time but casts the wrong type at runtime")
    return None


def resolve_targets(label: str, selectors: list[str], targets: list[str],
                    index: SignatureIndex) -> tuple[list[MethodSig], list[str]]:
    """Every (target class, selector) resolved to the DECLARED overloads it names.

    Mixin injects only into methods the target class declares. An inherited name satisfies the
    name check above, so it is reported here as a problem rather than silently passed."""
    candidates: list[MethodSig] = []
    problems: list[str] = []
    for target in targets:
        declared = index.declared(target)
        if declared is None:
            continue
        for selector in selectors:
            name, _, descriptor = selector.partition("(")
            name = name.strip()
            descriptor = "(" + descriptor if descriptor else None
            overloads = declared.get(name, [])
            if descriptor:
                exact = [overload for overload in overloads if overload.descriptor == descriptor]
                if not exact:
                    available = ", ".join(sorted(overload.descriptor for overload in overloads)) or "none"
                    inherited = index.find(target, name) or []
                    hint = f"; {target} only inherits it from {inherited[0].owner}" if inherited and not overloads else ""
                    problems.append(f"{label}: method '{name}{descriptor}' is not declared on {target} "
                                    f"(declared descriptors for '{name}': {available}{hint})")
                candidates.extend(exact)
            else:
                if not overloads:
                    inherited = index.find(target, name) or []
                    if inherited:
                        problems.append(f"{label}: method '{name}' is inherited by {target} from "
                                        f"{inherited[0].owner}, not declared on it; Mixin injects only "
                                        f"into declared methods")
                candidates.extend(overloads)
    return candidates, problems


def verify_handler_signatures(entry: str, block: str, source: str, targets: list[str],
                              index: SignatureIndex) -> SignatureReport:
    """Check every injector handler in one registered mixin class against its target's shape."""
    report = SignatureReport()
    type_params = class_type_params(block)
    for site in injector_sites(block):
        label = f"{entry}: @{site.kind}"
        if site.handler is None:
            report.problems.append(f"{label}: could not parse the handler method that follows the annotation")
            report.trace.append(f"MISMATCH {label}: unparseable handler")
            continue
        label += f" handler '{site.handler.name}'"
        found, found_return, params, sugar_problem = handler_types(site.handler, source, type_params)
        if sugar_problem:
            report.problems.append(f"{label}: {sugar_problem}")
            report.trace.append(f"MISMATCH {label}: {sugar_problem}")
            continue

        selectors = string_values(site.attributes.get("method"))
        wildcard_selector = any(selector.strip() == "*" for selector in selectors)
        selectors = [] if wildcard_selector else selectors
        candidates, resolution_problems = resolve_targets(label, selectors, targets, index)
        report.problems.extend(resolution_problems)
        for problem in resolution_problems:
            report.trace.append(f"MISMATCH {problem}")

        if site.kind in TARGET_SHAPED_KINDS:
            if wildcard_selector:
                report.unverifiable += 1
                reason = "method = \"*\" -- the handler shape depends on every method in the class, not checked"
                report.warnings.append(f"{label}: {reason}")
                report.trace.append(f"UNVERIFIABLE {label}: {reason}")
                continue
            if not candidates:
                if not resolution_problems:
                    report.unverifiable += 1
                    report.warnings.append(f"{label}: no target method could be resolved, handler shape not checked")
                    report.trace.append(f"UNVERIFIABLE {label}: no target method resolved")
                continue
            options: list[MethodSig | None] = list(candidates)
        else:
            # Invoke-style injectors take their head from @At; the enclosing method only feeds
            # trailing parameters, so it may legitimately be unknown.
            options = list(candidates) if candidates else [None]

        verdicts: list[tuple[str, str, MethodSig | None]] = []
        unverifiable_reason: str | None = None
        first_shape: Shape | None = None
        for target in options:
            shape = expected_shape(site, target, index)
            if isinstance(shape, str):
                unverifiable_reason = shape
                break
            first_shape = first_shape or shape
            if site.kind == "Inject":
                status, detail = check_inject(found, found_return, shape)
            elif site.kind == "ModifyArg":
                status, detail = check_modify_arg(found, found_return, shape)
            elif site.kind == "ModifyVariable":
                assert target is not None
                status, detail = check_modify_variable(site, found, found_return, target)
            else:
                status, detail = check_head(site.kind, found, found_return, shape, target)
            verdicts.append((status, detail, target))
        if unverifiable_reason is not None:
            if unverifiable_reason.startswith("!"):
                report.problems.append(f"{label}: {unverifiable_reason[1:]}")
                report.trace.append(f"MISMATCH {label}: {unverifiable_reason[1:]}")
            else:
                report.unverifiable += 1
                report.warnings.append(f"{label}: {unverifiable_reason}; handler shape not checked")
                report.trace.append(f"UNVERIFIABLE {label}: {unverifiable_reason}")
            continue

        statuses = [status for status, _, _ in verdicts]
        shown = signature_display(found, found_return)
        against = first_shape.label if first_shape else "?"
        if site.kind == "Inject" and "ok" in statuses:
            # CallbackInjector tries every candidate and only fails when none matches.
            report.verified += 1
            report.trace.append(f"OK {label}: {shown} against {against}")
            if "mismatch" in statuses:
                report.warnings.append(f"{label}: matches {statuses.count('ok')} of {len(statuses)} candidate "
                                       f"targets; Mixin injects into the matching one(s) only")
        elif "mismatch" in statuses:
            for status, detail, target in verdicts[:3]:
                if status == "mismatch":
                    where = target.label() if target else (first_shape.label if first_shape else "the @At target")
                    report.problems.append(f"{label}: on {where}: {detail}")
                    report.trace.append(f"MISMATCH {label}: on {where}: {detail}")
        elif "partial" in statuses:
            report.partial += 1
            detail = next(detail for status, detail, _ in verdicts if status == "partial")
            report.warnings.append(f"{label}: {detail}")
            report.trace.append(f"PARTIAL {label}: {shown} against {against}; {detail}")
        else:
            report.verified += 1
            report.trace.append(f"OK {label}: {shown} against {against}")

        if first_shape is not None and verdicts and verdicts[0][0] != "mismatch":
            wrapper: tuple[str, TypeRef] | None = None
            if site.kind == "Inject" and verdicts[0][2] is not None:
                wrapper = ("CallbackInfoReturnable", verdicts[0][2].return_type())
            elif site.kind in ("WrapOperation", "WrapMethod"):
                wrapper = ("Operation", first_shape.return_type)
            if wrapper:
                problem = generic_argument_problem(params, found, wrapper[0], wrapper[1], source,
                                                   type_params | site.handler.type_params)
                if problem:
                    report.problems.append(f"{label}: {problem}")
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    # A full classpath string, not a single file: Minecraft may be split across jars,
    # and letting javap resolve the whole path avoids guessing which one holds a class.
    parser.add_argument("--classpath", required=True)
    parser.add_argument("--source-root", default=Path("."), type=Path)
    parser.add_argument("--trace", action="store_true",
                        help="print one verdict line per injector handler")
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
    checked_signatures = 0
    partial_signatures = 0
    unverifiable_signatures = 0
    warnings: list[str] = []
    trace_lines: list[str] = []
    signature_index = SignatureIndex(javap_bin, jar)
    member_cache: dict[str, set[str] | None] = {}
    declared_cache: dict[str, set[str] | None] = {}

    def members(binary_name: str) -> set[str] | None:
        if binary_name not in member_cache:
            member_cache[binary_name] = javap_members(javap_bin, jar, binary_name)
        return member_cache[binary_name]

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

        # Scope to THIS registered class, not the whole file -- see mixin_classes().
        simple_name = entry.rsplit(".", 1)[-1]
        classes = mixin_classes(source)
        if simple_name not in classes:
            problems.append(
                f"{entry}: no @Mixin-annotated class named '{simple_name}' found in {path.name}")
            continue
        annotation, body = classes[simple_name]

        targets = mixin_targets(annotation, source)
        if not targets:
            problems.append(f"{entry}: no @Mixin target could be parsed")
            continue

        resolved_members: set[str] = set()
        for target in targets:
            checked_classes += 1
            found = members(target)
            if found is None:
                problems.append(f"{entry}: @Mixin target class not found in the remapped jar: {target}")
            else:
                resolved_members |= found

        # @Shadow resolves against DECLARED members only, and per target rather than pooled.
        for shadow in shadow_members(body):
            checked_shadows += 1
            declared_anywhere = False
            queried: list[str] = []
            for target in targets:
                if target not in declared_cache:
                    declared_cache[target] = javap_declared_members(javap_bin, jar, target)
                declared = declared_cache[target]
                queried.append(target)
                if declared and shadow in declared:
                    declared_anywhere = True
                    break
            if not declared_anywhere:
                # Name the class actually queried. Reporting some other name in scope produces a
                # message that contradicts itself -- "'x' does not exist on <class where x exists>".
                problems.append(
                    f"{entry}: @Shadow '{shadow}' is not DECLARED on {', '.join(queried)} "
                    f"(inherited members do not satisfy @Shadow; it fails at apply time and wedges "
                    f"the class load -- use @Invoker/@Accessor on the declaring class instead)")

        if not resolved_members:
            continue

        for method in injector_methods(body):
            checked_methods += 1
            if method not in resolved_members:
                problems.append(
                    f"{entry}: injector targets '{method}', absent from {', '.join(targets)}")

        # A name that exists is not a handler that applies: resolve explicit descriptors exactly
        # and check every handler's parameter list against the shape its injector demands.
        signatures = verify_handler_signatures(entry, body, source, targets, signature_index)
        problems.extend(signatures.problems)
        warnings.extend(signatures.warnings)
        checked_signatures += signatures.verified
        partial_signatures += signatures.partial
        unverifiable_signatures += signatures.unverifiable
        trace_lines.extend(signatures.trace)

        for owner, method in at_targets(body):
            if owner.startswith("net.minecraft"):
                checked_methods += 1
                found = members(owner)
                if found is None:
                    problems.append(f"{entry}: @At target class not found: {owner}")
                elif method not in found:
                    problems.append(f"{entry}: @At targets '{owner}.{method}', which does not exist")

    if args.trace:
        for line in trace_lines:
            print(f"MIXIN_TARGET_VERIFY_TRACE {line}")
    for warning in warnings:
        print(f"MIXIN_TARGET_VERIFY_NOTE {warning}")

    if problems:
        print("MIXIN_TARGET_VERIFY_FAIL", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    print(f"MIXIN_TARGET_VERIFY_PASS mixins={len(registered)} "
          f"targetClasses={checked_classes} targetMethods={checked_methods} "
          f"shadowMembers={checked_shadows} handlerSignatures={checked_signatures} "
          f"handlerSignaturesPartial={partial_signatures} "
          f"handlerSignaturesUnverifiable={unverifiable_signatures}")
    print(f" classpathEntries={len(jar.split(':'))}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Failure as error:
        print(f"MIXIN_TARGET_VERIFY_FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
