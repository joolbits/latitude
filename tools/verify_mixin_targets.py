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

It is not a substitute for a boot: it cannot prove an injection point exists *inside* a method
(a `@At("INVOKE")` whose target call was removed). Run it first, then boot. And assert the boot
actually reached the screen -- "zero mixin errors" from a run that never loaded the class is
worth nothing. `defaultRequire: 1` remains the backstop.

Usage:
    python3 tools/verify_mixin_targets.py --classpath <remapped classpath> [--source-root .]
"""

from __future__ import annotations

import argparse
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


def main() -> int:
    parser = argparse.ArgumentParser()
    # A full classpath string, not a single file: Minecraft may be split across jars,
    # and letting javap resolve the whole path avoids guessing which one holds a class.
    parser.add_argument("--classpath", required=True)
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

        for owner, method in at_targets(body):
            if owner.startswith("net.minecraft"):
                checked_methods += 1
                found = members(owner)
                if found is None:
                    problems.append(f"{entry}: @At target class not found: {owner}")
                elif method not in found:
                    problems.append(f"{entry}: @At targets '{owner}.{method}', which does not exist")

    if problems:
        print("MIXIN_TARGET_VERIFY_FAIL", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    print(f"MIXIN_TARGET_VERIFY_PASS mixins={len(registered)} "
          f"targetClasses={checked_classes} targetMethods={checked_methods} "
          f"shadowMembers={checked_shadows}")
    print(f" classpathEntries={len(jar.split(':'))}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Failure as error:
        print(f"MIXIN_TARGET_VERIFY_FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
