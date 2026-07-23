#!/usr/bin/env python3
"""Verify the bounded Latitude 1.5 HUD/loading acceptance contract."""

from pathlib import Path
import ast
import hashlib
import json
import re
import sys
from typing import Optional


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "src/main/java/com/example/globe/client/CompassHudConfig.java"
STUDIO = ROOT / "src/main/java/com/example/globe/client/LatitudeHudStudioScreen.java"
LEGACY_SETTINGS = ROOT / "src/main/java/com/example/globe/client/LatitudeSettingsScreen.java"
LOADING = ROOT / (
    "src/main/java/com/example/globe/mixin/client/"
    "LevelLoadingScreenLatitudeOverlayMixin.java"
)
MIXIN_CONFIG = ROOT / "src/main/resources/globe.mixins.json"
FABRIC_METADATA = ROOT / "src/main/resources/fabric.mod.json"
LOADING_MIXIN = "client.LevelLoadingScreenLatitudeOverlayMixin"
MIXIN_CONFIG_NAME = "globe.mixins.json"
IMMUTABLE_LOADING_CANDIDATE_SHA256 = (
    "20e2f8fcdc7a111609eb9c043b1379bb269b44d7f1f0c595692356149684fe6a"
)

IMMUTABLE_CANDIDATE_FAILURE = (
    "loading overlay source must match the independently reviewed immutable candidate bytes"
)
ACTIVE_INVOCATION_FAILURE = (
    "loading overlay render path must invoke the version-label helper exactly once"
)
TEXT_DRAW_FAILURE = "version-label helper must issue the actual scaled text draw"
METADATA_AUTHORITY_FAILURE = (
    "loading version label must derive from Latitude's Fabric metadata version"
)
MIXIN_REGISTRATION_FAILURE = (
    "loading overlay must remain registered exactly once as a client mixin"
)
MIXIN_REQUIRED_FAILURE = "globe.mixins.json must remain required"
FABRIC_MIXIN_REGISTRATION_FAILURE = (
    "fabric.mod.json must register globe.mixins.json exactly once"
)
MIXIN_TARGET_FAILURE = (
    "loading overlay mixin must target LevelLoadingScreen directly"
)
INJECT_HOOK_FAILURE = (
    "loading overlay handler must use the required extractRenderState TAIL injection"
)
REACHABILITY_FAILURE = (
    "loading overlay version-label invocation must remain reachable"
)
DRAW_REACHABILITY_FAILURE = (
    "loading overlay version-label text draw must remain reachable"
)
SCALE_PATH_FAILURE = (
    "loading overlay version-label draw must use exactly one accepted nonzero scale"
)
VISIBLE_COLOR_FAILURE = (
    "loading overlay MUTED color must remain the visible accepted color 0xFF8C8078"
)


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def java_code_mask(source: str) -> str:
    """Blank comments and literals while preserving Java token positions."""
    masked = list(source)
    state = "code"
    quote = ""
    index = 0
    while index < len(source):
        char = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if state == "code":
            if char == "/" and following == "/":
                masked[index] = masked[index + 1] = " "
                index += 2
                state = "line_comment"
                continue
            if char == "/" and following == "*":
                masked[index] = masked[index + 1] = " "
                index += 2
                state = "block_comment"
                continue
            if char in ('"', "'"):
                quote = char
                masked[index] = " "
                index += 1
                state = "literal"
                continue
            index += 1
            continue
        if state == "line_comment":
            if char == "\n":
                state = "code"
            else:
                masked[index] = " "
            index += 1
            continue
        if state == "block_comment":
            if char == "*" and following == "/":
                masked[index] = masked[index + 1] = " "
                index += 2
                state = "code"
            else:
                if char != "\n":
                    masked[index] = " "
                index += 1
            continue
        if state == "literal":
            if char == "\\" and following:
                masked[index] = masked[index + 1] = " "
                index += 2
                continue
            masked[index] = " " if char != "\n" else "\n"
            index += 1
            if char == quote:
                state = "code"
    return "".join(masked)


def find_matching_brace(masked: str, open_index: int) -> Optional[int]:
    depth = 0
    for index in range(open_index, len(masked)):
        if masked[index] == "{":
            depth += 1
        elif masked[index] == "}":
            depth -= 1
            if depth == 0:
                return index
    return None


def find_matching_delimiter(
    masked: str,
    open_index: int,
    opening: str,
    closing: str,
) -> Optional[int]:
    depth = 0
    for index in range(open_index, len(masked)):
        if masked[index] == opening:
            depth += 1
        elif masked[index] == closing:
            depth -= 1
            if depth == 0:
                return index
    return None


def method_match_and_body_bounds(
    source: str,
    method_name: str,
) -> Optional[tuple[re.Match[str], int, int]]:
    masked = java_code_mask(source)
    matches = list(
        re.finditer(
            rf"\bprivate\s+void\s+{re.escape(method_name)}\s*\(",
            masked,
        )
    )
    if len(matches) != 1:
        return None
    open_index = masked.find("{", matches[0].end())
    semicolon_index = masked.find(";", matches[0].end())
    if open_index < 0 or (0 <= semicolon_index < open_index):
        return None
    close_index = find_matching_brace(masked, open_index)
    if close_index is None:
        return None
    return matches[0], open_index, close_index


def extract_method_body(source: str, method_name: str) -> Optional[str]:
    match_and_bounds = method_match_and_body_bounds(source, method_name)
    if match_and_bounds is None:
        return None
    _, open_index, close_index = match_and_bounds
    return source[open_index + 1 : close_index]


def annotation_directly_before(
    source: str,
    declaration_start: int,
    annotation_name: str,
) -> Optional[str]:
    masked = java_code_mask(source)
    annotation_start = masked.rfind(f"@{annotation_name}", 0, declaration_start)
    if annotation_start < 0:
        return None
    open_index = masked.find("(", annotation_start, declaration_start)
    if open_index < 0:
        return None
    close_index = find_matching_delimiter(masked, open_index, "(", ")")
    if close_index is None or close_index >= declaration_start:
        return None
    if masked[close_index + 1 : declaration_start].strip():
        return None
    return source[annotation_start : close_index + 1]


def insert_method_body_prefix(
    source: str,
    method_name: str,
    prefix: str,
) -> tuple[str, int]:
    match_and_bounds = method_match_and_body_bounds(source, method_name)
    if match_and_bounds is None:
        return source, 0
    _, open_index, _ = match_and_bounds
    return source[: open_index + 1] + prefix + source[open_index + 1 :], 1


def extract_class_body(source: str) -> Optional[str]:
    masked = java_code_mask(source)
    class_match = re.search(
        r"\bclass\s+LevelLoadingScreenLatitudeOverlayMixin\b",
        masked,
    )
    if class_match is None:
        return None
    open_index = masked.find("{", class_match.end())
    if open_index < 0:
        return None
    close_index = find_matching_brace(masked, open_index)
    if close_index is None:
        return None
    return source[open_index + 1 : close_index]


def top_level_statements(body: str) -> list[str]:
    """Return semicolon statements that execute directly at this block depth."""
    masked = java_code_mask(body)
    statements: list[str] = []
    depth = 0
    start = 0
    for index, char in enumerate(masked):
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                start = index + 1
        elif char == ";" and depth == 0:
            statement = body[start : index + 1].strip()
            if statement:
                statements.append(statement)
            start = index + 1
    return statements


def normalize_java(statement: str) -> str:
    return re.sub(r"\s+", "", statement)


def java_constant_boolean(expression: str) -> Optional[bool]:
    """Evaluate a deliberately small, identifier-free Java boolean subset."""
    translated = expression.strip()
    if not translated:
        return None
    translated = re.sub(r"\btrue\b", "True", translated)
    translated = re.sub(r"\bfalse\b", "False", translated)
    translated = translated.replace("&&", " and ").replace("||", " or ")
    translated = re.sub(r"!(?!=)", " not ", translated)
    if re.search(r"\b(?!True\b|False\b)[A-Za-z_$][A-Za-z0-9_$]*\b", translated):
        return None
    try:
        parsed = ast.parse(translated, mode="eval")
    except SyntaxError:
        return None
    allowed_nodes = (
        ast.Expression,
        ast.Constant,
        ast.BoolOp,
        ast.And,
        ast.Or,
        ast.UnaryOp,
        ast.Not,
        ast.UAdd,
        ast.USub,
        ast.Invert,
        ast.BinOp,
        ast.Add,
        ast.Sub,
        ast.Mult,
        ast.Div,
        ast.FloorDiv,
        ast.Mod,
        ast.BitAnd,
        ast.BitOr,
        ast.BitXor,
        ast.LShift,
        ast.RShift,
        ast.Compare,
        ast.Eq,
        ast.NotEq,
        ast.Lt,
        ast.LtE,
        ast.Gt,
        ast.GtE,
    )
    if any(not isinstance(node, allowed_nodes) for node in ast.walk(parsed)):
        return None
    try:
        value = eval(compile(parsed, "<java-constant>", "eval"), {"__builtins__": {}}, {})
    except (ArithmeticError, ValueError, TypeError):
        return None
    return value if isinstance(value, bool) else None


def has_top_level_short_circuit_before(
    body: str,
    marker_start: int,
) -> bool:
    """Reject direct exits and constant-true exits before an active marker."""
    prefix = body[:marker_start]
    for statement in top_level_statements(prefix):
        normalized = normalize_java(java_code_mask(statement))
        if normalized.startswith("return") or normalized.startswith("throw"):
            return True
        if normalized.startswith("if(true)return") or normalized.startswith("if(true)throw"):
            return True

    masked = java_code_mask(prefix)
    exit_conditions: set[str] = set()
    depth = 0
    index = 0
    while index < len(masked):
        if masked[index] == "{":
            depth += 1
            index += 1
            continue
        if masked[index] == "}":
            depth -= 1
            index += 1
            continue
        if depth == 0 and re.match(r"if\b", masked[index:]):
            condition_open = masked.find("(", index)
            if condition_open < 0:
                return True
            condition_close = find_matching_delimiter(
                masked,
                condition_open,
                "(",
                ")",
            )
            if condition_close is None:
                return True
            condition = masked[condition_open + 1 : condition_close]
            after_condition = condition_close + 1
            while after_condition < len(masked) and masked[after_condition].isspace():
                after_condition += 1
            exits = False
            next_index = condition_close + 1
            if after_condition < len(masked) and masked[after_condition] == "{":
                close_index = find_matching_brace(masked, after_condition)
                if close_index is None:
                    return True
                inner = prefix[after_condition + 1 : close_index]
                for statement in top_level_statements(inner):
                    normalized = normalize_java(java_code_mask(statement))
                    if normalized.startswith("return") or normalized.startswith("throw"):
                        exits = True
                        break
                next_index = close_index + 1
            else:
                statement_end = masked.find(";", after_condition)
                if statement_end < 0:
                    return True
                normalized = normalize_java(
                    java_code_mask(prefix[after_condition : statement_end + 1])
                )
                exits = normalized.startswith("return") or normalized.startswith("throw")
                next_index = statement_end + 1

            if exits and java_constant_boolean(condition) is True:
                return True
            if exits:
                normalized_condition = normalize_java(condition)
                while normalized_condition.startswith("(") and normalized_condition.endswith(")"):
                    close = find_matching_delimiter(
                        normalized_condition,
                        0,
                        "(",
                        ")",
                    )
                    if close != len(normalized_condition) - 1:
                        break
                    normalized_condition = normalized_condition[1:-1]
                exit_conditions.add(normalized_condition)
            index = next_index
            continue
        index += 1

    for condition in exit_conditions:
        if condition.startswith("!"):
            positive = condition[1:]
            while positive.startswith("(") and positive.endswith(")"):
                close = find_matching_delimiter(positive, 0, "(", ")")
                if close != len(positive) - 1:
                    break
                positive = positive[1:-1]
            if positive in exit_conditions:
                return True
        elif f"!{condition}" in exit_conditions or f"!({condition})" in exit_conditions:
            return True
    return False


def muted_color_is_visible_and_exact(loading: str) -> bool:
    class_body = extract_class_body(loading)
    if class_body is None:
        return False
    muted_fields = [
        statement
        for statement in top_level_statements(class_body)
        if re.search(r"\bMUTED\s*=", java_code_mask(statement))
    ]
    if len(muted_fields) != 1:
        return False
    match = re.search(r"\bMUTED\s*=\s*(0x[0-9A-Fa-f]{8})\s*;", muted_fields[0])
    if match is None:
        return False
    color = int(match.group(1), 16)
    return color == 0xFF8C8078 and ((color >> 24) & 0xFF) > 0


def version_field_statements(loading: str) -> list[str]:
    class_body = extract_class_body(loading)
    if class_body is None:
        return []
    return [
        statement
        for statement in top_level_statements(class_body)
        if re.search(r"\bglobe\$VERSION_LABEL\s*=", java_code_mask(statement))
    ]


def loading_activation_failures(
    loading: str,
    fabric_metadata: dict,
    mixin_config: dict,
) -> list[str]:
    """Prove the metadata-to-mixin-to-render-to-draw activation chain."""
    failures: list[str] = []

    # The accepted loading overlay is a single, independently reviewed source
    # artifact.  Treat its bytes as immutable here instead of attempting to
    # prove arbitrary Java control/data flow with an open-ended text parser.
    # The caller passes the already-read source; this check never re-reads the
    # file from disk, so in-memory hostile fixtures exercise the same boundary.
    require(
        hashlib.sha256(loading.encode("utf-8")).hexdigest()
        == IMMUTABLE_LOADING_CANDIDATE_SHA256,
        IMMUTABLE_CANDIDATE_FAILURE,
        failures,
    )

    masked_loading = java_code_mask(loading)
    class_match = re.search(
        r"\bpublic\s+abstract\s+class\s+LevelLoadingScreenLatitudeOverlayMixin\b",
        masked_loading,
    )
    class_annotation = (
        annotation_directly_before(loading, class_match.start(), "Mixin")
        if class_match is not None
        else None
    )
    require(
        class_annotation is not None
        and normalize_java(class_annotation) == "@Mixin(LevelLoadingScreen.class)",
        MIXIN_TARGET_FAILURE,
        failures,
    )

    render_match_and_bounds = method_match_and_body_bounds(
        loading,
        "globe$renderLatitudeOverlay",
    )
    render_annotation = (
        annotation_directly_before(
            loading,
            render_match_and_bounds[0].start(),
            "Inject",
        )
        if render_match_and_bounds is not None
        else None
    )
    normalized_render_annotation = normalize_java(render_annotation or "")
    hook_method_exact = (
        re.search(r'method="extractRenderState"(?:,|\))', normalized_render_annotation)
        is not None
    )
    hook_tail_exact = 'at=@At("TAIL")' in normalized_render_annotation
    require_assignments = len(re.findall(r"(?:,|\()require=", normalized_render_annotation))
    explicit_positive_require = re.search(
        r"(?:,|\()require=([1-9]\d*)(?:,|\))",
        normalized_render_annotation,
    )
    require_is_nonzero = (
        require_assignments == 0
        or (require_assignments == 1 and explicit_positive_require is not None)
    )
    injectors = mixin_config.get("injectors", {})
    default_require = injectors.get("defaultRequire") if isinstance(injectors, dict) else None
    default_require_exact = (
        type(default_require) is int and default_require == 1
    )
    require(
        render_annotation is not None
        and normalized_render_annotation.startswith("@Inject(")
        and hook_method_exact
        and hook_tail_exact
        and require_is_nonzero
        and default_require_exact,
        INJECT_HOOK_FAILURE,
        failures,
    )

    invocation = "globe$drawVersionLabel(context,paneX,paneY,paneW,paneH);"
    render_body = extract_method_body(loading, "globe$renderLatitudeOverlay")
    render_statements = top_level_statements(render_body) if render_body is not None else []
    active_invocations = sum(
        normalize_java(java_code_mask(statement)) == invocation
        for statement in render_statements
    )
    all_invocations = len(
        re.findall(
            r"globe\$drawVersionLabel\(\s*context\s*,\s*paneX\s*,\s*paneY\s*,"
            r"\s*paneW\s*,\s*paneH\s*\)\s*;",
            java_code_mask(render_body or ""),
        )
    )
    require(
        active_invocations == 1 and all_invocations == 1,
        ACTIVE_INVOCATION_FAILURE,
        failures,
    )
    invocation_matches = list(
        re.finditer(
            r"globe\$drawVersionLabel\(\s*context\s*,\s*paneX\s*,\s*paneY\s*,"
            r"\s*paneW\s*,\s*paneH\s*\)\s*;",
            java_code_mask(render_body or ""),
        )
    )
    require(
        len(invocation_matches) == 1
        and render_body is not None
        and not has_top_level_short_circuit_before(
            render_body,
            invocation_matches[0].start(),
        ),
        REACHABILITY_FAILURE,
        failures,
    )

    draw = (
        "context.text(this.font,globe$VERSION_LABEL,Math.round(drawX),"
        "Math.round(drawY),MUTED,false);"
    )
    version_body = extract_method_body(loading, "globe$drawVersionLabel")
    version_statements = top_level_statements(version_body) if version_body is not None else []
    active_draws = sum(
        normalize_java(java_code_mask(statement)) == draw
        for statement in version_statements
    )
    all_draws = len(
        re.findall(
            r"context\.text\(\s*this\.font\s*,\s*globe\$VERSION_LABEL\s*,"
            r"\s*Math\.round\(\s*drawX\s*\)\s*,\s*Math\.round\(\s*drawY\s*\)\s*,"
            r"\s*MUTED\s*,\s*false\s*\)\s*;",
            java_code_mask(version_body or ""),
        )
    )
    require(
        active_draws == 1 and all_draws == 1,
        TEXT_DRAW_FAILURE,
        failures,
    )
    draw_matches = list(
        re.finditer(
            r"context\.text\(\s*this\.font\s*,\s*globe\$VERSION_LABEL\s*,"
            r"\s*Math\.round\(\s*drawX\s*\)\s*,\s*Math\.round\(\s*drawY\s*\)\s*,"
            r"\s*MUTED\s*,\s*false\s*\)\s*;",
            java_code_mask(version_body or ""),
        )
    )
    require(
        len(draw_matches) == 1
        and version_body is not None
        and not has_top_level_short_circuit_before(
            version_body,
            draw_matches[0].start(),
        ),
        DRAW_REACHABILITY_FAILURE,
        failures,
    )
    expected_scale = (
        "matrices.scale(globe$VERSION_LABEL_SCALE,globe$VERSION_LABEL_SCALE);"
    )
    version_statements_normalized = [
        normalize_java(java_code_mask(statement))
        for statement in version_statements
    ]
    all_scale_calls = len(
        re.findall(
            r"\bmatrices\.scale\s*\(",
            java_code_mask(version_body or ""),
        )
    )
    require(
        version_statements_normalized.count(expected_scale) == 1
        and all_scale_calls == 1,
        SCALE_PATH_FAILURE,
        failures,
    )

    field_statements = version_field_statements(loading)
    assignment_count = len(
        re.findall(
            r"\bglobe\$VERSION_LABEL\s*=",
            java_code_mask(loading),
        )
    )
    expected_initializer = (
        'FabricLoader.getInstance().getModContainer(GlobeMod.MOD_ID)'
        '.map(container->"v"+container.getMetadata().getVersion().getFriendlyString())'
        '.orElse("")'
    )
    actual_initializer = ""
    if len(field_statements) == 1 and "=" in field_statements[0]:
        actual_initializer = normalize_java(
            field_statements[0].split("=", 1)[1].rsplit(";", 1)[0]
        )
    metadata_authoritative = (
        len(field_statements) == 1
        and assignment_count == 1
        and actual_initializer == expected_initializer
        and fabric_metadata.get("id") == "globe"
        and fabric_metadata.get("version") == "${version}"
    )
    require(metadata_authoritative, METADATA_AUTHORITY_FAILURE, failures)

    require(
        muted_color_is_visible_and_exact(loading),
        VISIBLE_COLOR_FAILURE,
        failures,
    )

    registered_configs = fabric_metadata.get("mixins", [])
    require(
        isinstance(registered_configs, list)
        and registered_configs.count(MIXIN_CONFIG_NAME) == 1,
        FABRIC_MIXIN_REGISTRATION_FAILURE,
        failures,
    )

    client_mixins = mixin_config.get("client", [])
    require(
        isinstance(client_mixins, list) and client_mixins.count(LOADING_MIXIN) == 1,
        MIXIN_REGISTRATION_FAILURE,
        failures,
    )
    require(
        mixin_config.get("required") is True,
        MIXIN_REQUIRED_FAILURE,
        failures,
    )

    return failures


def require_loading_negative_controls(
    loading: str,
    fabric_metadata: dict,
    mixin_config: dict,
    failures: list[str],
) -> int:
    """Prove all historical and immutable-candidate fixtures turn RED."""
    invocation_pattern = (
        r"globe\$drawVersionLabel\(\s*context\s*,\s*paneX\s*,\s*paneY\s*,"
        r"\s*paneW\s*,\s*paneH\s*\)\s*;"
    )
    draw_pattern = (
        r"context\.text\(\s*this\.font\s*,\s*globe\$VERSION_LABEL\s*,"
        r"\s*Math\.round\(\s*drawX\s*\)\s*,\s*Math\.round\(\s*drawY\s*\)\s*,"
        r"\s*MUTED\s*,\s*false\s*\)\s*;"
    )
    invocation_removed, invocation_remove_edits = re.subn(
        invocation_pattern, "", loading, count=1
    )
    invocation_dead, invocation_dead_edits = re.subn(
        invocation_pattern,
        "if (false) { globe$drawVersionLabel(context, paneX, paneY, paneW, paneH); }",
        loading,
        count=1,
    )
    draw_removed, draw_remove_edits = re.subn(draw_pattern, "", loading, count=1)
    draw_dead, draw_dead_edits = re.subn(
        draw_pattern,
        (
            "if (false) { context.text(this.font, globe$VERSION_LABEL, "
            "Math.round(drawX), Math.round(drawY), MUTED, false); }"
        ),
        loading,
        count=1,
    )
    metadata_removed, metadata_remove_edits = re.subn(
        r"FabricLoader\.getInstance\(\)", "", loading, count=1
    )

    authoritative_fields = version_field_statements(loading)
    metadata_override = loading
    metadata_override_edits = 0
    if len(authoritative_fields) == 1 and '.orElse("")' in authoritative_fields[0]:
        authoritative_field = authoritative_fields[0]
        overridden_field = authoritative_field.replace(
            '.orElse("")', '.map(ignored -> "vFAKE").orElse("")', 1
        )
        metadata_override = loading.replace(authoritative_field, overridden_field, 1)
        metadata_override_edits = int(metadata_override != loading)

    client_removed = dict(mixin_config)
    original_client_mixins = mixin_config.get("client", [])
    if isinstance(original_client_mixins, list):
        client_removed["client"] = [
            entry for entry in original_client_mixins if entry != LOADING_MIXIN
        ]
    client_remove_edits = (
        len(original_client_mixins) - len(client_removed.get("client", []))
        if isinstance(original_client_mixins, list)
        else 0
    )

    fabric_removed = dict(fabric_metadata)
    original_fabric_mixins = fabric_metadata.get("mixins", [])
    if isinstance(original_fabric_mixins, list):
        fabric_removed["mixins"] = [
            entry for entry in original_fabric_mixins if entry != MIXIN_CONFIG_NAME
        ]
    fabric_remove_edits = (
        len(original_fabric_mixins) - len(fabric_removed.get("mixins", []))
        if isinstance(original_fabric_mixins, list)
        else 0
    )

    hook_pattern = (
        r'@Inject\s*\(\s*method\s*=\s*"extractRenderState"\s*,\s*'
        r'at\s*=\s*@At\s*\(\s*"TAIL"\s*\)\s*\)'
    )
    hook_removed, hook_remove_edits = re.subn(
        hook_pattern,
        "",
        loading,
        count=1,
    )
    hook_retargeted, hook_retarget_edits = re.subn(
        hook_pattern,
        (
            '@Inject(method = "globe$definitelyMissingRenderHook", '
            'at = @At("TAIL"), require = 0)'
        ),
        loading,
        count=1,
    )
    reachability_blocked, reachability_block_edits = insert_method_body_prefix(
        loading,
        "globe$renderLatitudeOverlay",
        "\n        if (true) { return; }",
    )
    muted_transparent, muted_transparent_edits = re.subn(
        r"\bMUTED\s*=\s*0xFF8C8078\s*;",
        "MUTED = 0x008C8078;",
        loading,
        count=1,
    )
    parenthesized_optional, parenthesized_optional_edits = re.subn(
        hook_pattern,
        (
            '@Inject(method = "extractRenderState", '
            'at = @At("TAIL"), require = (0))'
        ),
        loading,
        count=1,
    )
    numeric_true_blocked, numeric_true_block_edits = insert_method_body_prefix(
        loading,
        "globe$renderLatitudeOverlay",
        "\n        if (1 == 1) { return; }",
    )
    draw_blocked, draw_block_edits = insert_method_body_prefix(
        loading,
        "globe$drawVersionLabel",
        "\n        if (true) { return; }",
    )
    exhaustive_state_blocked, exhaustive_state_block_edits = insert_method_body_prefix(
        loading,
        "globe$renderLatitudeOverlay",
        (
            "\n        if (LatitudeClientState.isLatitudeWorldLoading()) { return; }"
            "\n        if (!LatitudeClientState.isLatitudeWorldLoading()) { return; }"
        ),
    )
    zero_scale, zero_scale_edits = re.subn(
        draw_pattern,
        (
            "matrices.scale(0.0f, 0.0f);\n        "
            "context.text(this.font, globe$VERSION_LABEL, Math.round(drawX), "
            "Math.round(drawY), MUTED, false);"
        ),
        loading,
        count=1,
    )
    mixins_optional = dict(mixin_config)
    mixins_optional["required"] = False
    mixins_optional_edits = int(mixin_config.get("required") is True)

    boxed_true_render, boxed_true_render_edits = insert_method_body_prefix(
        loading,
        "globe$renderLatitudeOverlay",
        "\n        if (Boolean.TRUE) { return; }",
    )
    boxed_true_helper, boxed_true_helper_edits = insert_method_body_prefix(
        loading,
        "globe$drawVersionLabel",
        "\n        if (Boolean.TRUE) { return; }",
    )
    equivalent_exhaustive, equivalent_exhaustive_edits = insert_method_body_prefix(
        loading,
        "globe$renderLatitudeOverlay",
        "\n        if (LatitudeClientState.isLatitudeWorldLoading() == true) { return; }",
    )
    throwing_render, throwing_render_edits = insert_method_body_prefix(
        loading,
        "globe$renderLatitudeOverlay",
        "\n        java.util.Objects.requireNonNull(null);",
    )
    throwing_helper, throwing_helper_edits = insert_method_body_prefix(
        loading,
        "globe$drawVersionLabel",
        "\n        java.util.Objects.requireNonNull(null);",
    )
    alternate_zero_scale, alternate_zero_scale_edits = re.subn(
        draw_pattern,
        (
            "context.pose().scale(0.0f, 0.0f);\n        "
            "context.text(this.font, globe$VERSION_LABEL, Math.round(drawX), "
            "Math.round(drawY), MUTED, false);"
        ),
        loading,
        count=1,
    )
    offscreen_draw_y, offscreen_draw_y_edits = re.subn(
        (
            r"float\s+drawY\s*=\s*y\s*/\s*"
            r"globe\$VERSION_LABEL_SCALE\s*;"
        ),
        (
            "float drawY = y / globe$VERSION_LABEL_SCALE;\n"
            "        drawY = -10000.0f;"
        ),
        loading,
        count=1,
    )
    arbitrary_byte_mutation = loading + " "
    arbitrary_byte_mutation_edits = int(arbitrary_byte_mutation != loading)

    controls = (
        ("active invocation removal", invocation_remove_edits, invocation_removed, fabric_metadata, mixin_config, ACTIVE_INVOCATION_FAILURE),
        ("actual text-draw removal", draw_remove_edits, draw_removed, fabric_metadata, mixin_config, TEXT_DRAW_FAILURE),
        ("Fabric metadata authority removal", metadata_remove_edits, metadata_removed, fabric_metadata, mixin_config, METADATA_AUTHORITY_FAILURE),
        ("client mixin registration removal", client_remove_edits, loading, fabric_metadata, client_removed, MIXIN_REGISTRATION_FAILURE),
        ("active invocation dead-code move", invocation_dead_edits, invocation_dead, fabric_metadata, mixin_config, ACTIVE_INVOCATION_FAILURE),
        ("actual text-draw dead-code move", draw_dead_edits, draw_dead, fabric_metadata, mixin_config, TEXT_DRAW_FAILURE),
        ("metadata override", metadata_override_edits, metadata_override, fabric_metadata, mixin_config, METADATA_AUTHORITY_FAILURE),
        ("top-level Fabric mixin registration removal", fabric_remove_edits, loading, fabric_removed, mixin_config, FABRIC_MIXIN_REGISTRATION_FAILURE),
        ("active render hook removal", hook_remove_edits, hook_removed, fabric_metadata, mixin_config, INJECT_HOOK_FAILURE),
        ("optional missing-hook retarget", hook_retarget_edits, hook_retargeted, fabric_metadata, mixin_config, INJECT_HOOK_FAILURE),
        ("constant-true pre-invocation return", reachability_block_edits, reachability_blocked, fabric_metadata, mixin_config, REACHABILITY_FAILURE),
        ("transparent MUTED color", muted_transparent_edits, muted_transparent, fabric_metadata, mixin_config, VISIBLE_COLOR_FAILURE),
        ("parenthesized optional require", parenthesized_optional_edits, parenthesized_optional, fabric_metadata, mixin_config, INJECT_HOOK_FAILURE),
        ("numeric constant-true render return", numeric_true_block_edits, numeric_true_blocked, fabric_metadata, mixin_config, REACHABILITY_FAILURE),
        ("constant-true helper return", draw_block_edits, draw_blocked, fabric_metadata, mixin_config, DRAW_REACHABILITY_FAILURE),
        ("mutually exhaustive loading-state returns", exhaustive_state_block_edits, exhaustive_state_blocked, fabric_metadata, mixin_config, REACHABILITY_FAILURE),
        ("zero scale before text draw", zero_scale_edits, zero_scale, fabric_metadata, mixin_config, SCALE_PATH_FAILURE),
        ("optional globe.mixins.json", mixins_optional_edits, loading, fabric_metadata, mixins_optional, MIXIN_REQUIRED_FAILURE),
        ("boxed constant-true render return", boxed_true_render_edits, boxed_true_render, fabric_metadata, mixin_config, IMMUTABLE_CANDIDATE_FAILURE),
        ("boxed constant-true helper return", boxed_true_helper_edits, boxed_true_helper, fabric_metadata, mixin_config, IMMUTABLE_CANDIDATE_FAILURE),
        ("equivalent exhaustive loading-state returns", equivalent_exhaustive_edits, equivalent_exhaustive, fabric_metadata, mixin_config, IMMUTABLE_CANDIDATE_FAILURE),
        ("always-throwing render call", throwing_render_edits, throwing_render, fabric_metadata, mixin_config, IMMUTABLE_CANDIDATE_FAILURE),
        ("always-throwing helper call", throwing_helper_edits, throwing_helper, fabric_metadata, mixin_config, IMMUTABLE_CANDIDATE_FAILURE),
        ("alternate-receiver zero scale", alternate_zero_scale_edits, alternate_zero_scale, fabric_metadata, mixin_config, IMMUTABLE_CANDIDATE_FAILURE),
        ("off-screen drawY reassignment", offscreen_draw_y_edits, offscreen_draw_y, fabric_metadata, mixin_config, IMMUTABLE_CANDIDATE_FAILURE),
        ("arbitrary source-byte mutation", arbitrary_byte_mutation_edits, arbitrary_byte_mutation, fabric_metadata, mixin_config, IMMUTABLE_CANDIDATE_FAILURE),
    )
    for label, edit_count, mutated_loading, mutated_fabric, mutated_mixins, expected_failure in controls:
        require(
            edit_count == 1,
            f"negative control fixture must apply exactly one {label}",
            failures,
        )
        mutated_failures = loading_activation_failures(
            mutated_loading,
            mutated_fabric,
            mutated_mixins,
        )
        require(
            expected_failure in mutated_failures,
            f"negative control must turn RED after {label}",
            failures,
        )
    return len(controls)


def main() -> int:
    config = CONFIG.read_text()
    studio = STUDIO.read_text()
    # Read the candidate once, preserving its exact UTF-8 bytes for the
    # immutable identity boundary used by every activation check and fixture.
    loading = LOADING.read_bytes().decode("utf-8")
    try:
        mixin_config = json.loads(MIXIN_CONFIG.read_text())
        mixin_parse_error = None
    except (OSError, json.JSONDecodeError) as exc:
        mixin_config = {}
        mixin_parse_error = exc
    try:
        fabric_metadata = json.loads(FABRIC_METADATA.read_text())
        fabric_parse_error = None
    except (OSError, json.JSONDecodeError) as exc:
        fabric_metadata = {}
        fabric_parse_error = exc
    failures: list[str] = []

    require(
        mixin_parse_error is None,
        f"client mixin config must be valid JSON: {mixin_parse_error}",
        failures,
    )
    require(
        fabric_parse_error is None,
        f"Fabric metadata must be valid JSON: {fabric_parse_error}",
        failures,
    )

    require(
        "DEFAULT_COMPASS_STYLE = CompassStyle.ANALOG" in config,
        "fresh compass style must be centralized as ANALOG",
        failures,
    )
    require(
        "DEFAULT_ANALOG_SIZE = 32.0f" in config,
        "fresh analog size must be centralized as 32 px",
        failures,
    )
    require(
        "ANALOG_SIZE_STUDIO_MIN = 16" in config
        and "ANALOG_SIZE_STUDIO_MAX = 72" in config,
        "Studio analog range must be centralized as 16–72 px",
        failures,
    )
    require(
        "ANALOG_SIZE_SAVED_MAX = 128.0f" in config
        and re.search(
            r"analogSize\s*>\s*ANALOG_SIZE_SAVED_MAX\)\s*"
            r"analogSize\s*=\s*ANALOG_SIZE_SAVED_MAX",
            config,
        )
        is not None,
        "sanitizer must preserve explicit saved values through 128 px",
        failures,
    )
    require(
        "public void resetToDefaults()" in config,
        "HUD reset defaults must have one config-owned implementation",
        failures,
    )
    require(
        studio.count("cfg.resetToDefaults();") == 1
        and "private static void applyDefaults(CompassHudConfig cfg)" not in studio,
        "HUD Studio reset must use the centralized defaults",
        failures,
    )
    require(
        not LEGACY_SETTINGS.exists(),
        "the superseded standalone settings screen must stay removed",
        failures,
    )
    require(
        'TAB_NAMES = {"Compass", "Title", "Settings"}' in studio
        and 'Component.literal("Show HUD")' in studio
        and 'Component.literal("Display When")' in studio
        and 'Component.literal("Warning Messages")' in studio,
        "the consolidated Studio Settings tab must own the former first-screen controls",
        failures,
    )
    require(
        re.search(
            r"new IntSlider\([^;]+Component\.literal\(\"Compass Size\"\),\s*"
            r"CompassHudConfig\.ANALOG_SIZE_STUDIO_MIN,\s*"
            r"CompassHudConfig\.ANALOG_SIZE_STUDIO_MAX,\s*"
            r"Math\.round\(cfg\.analogSize\),\s*\" px\"",
            studio,
        )
        is not None,
        "Studio must expose a whole-pixel 'Compass Size: N px' slider",
        failures,
    )
    require(
        "this.legacyDisplayValue = initial > max ? initial : null;" in studio,
        "saved sizes above the Studio range must remain truthful until an edit",
        failures,
    )
    require(
        "legacyDisplayValue = null;" in studio
        and re.search(
            r"protected void applyValue\(\) \{\s*"
            r"legacyDisplayValue = null;\s*"
            r"onChange\.accept\(getValue\(\)\);\s*"
            r"updateMessage\(\);",
            studio,
        )
        is not None,
        "the first real slider edit must enter the supported range and refresh its label",
        failures,
    )
    require(
        "return Mth.clamp((double) (v - min) / (double) (max - min), 0.0, 1.0);"
        in studio,
        "slider geometry must clamp legacy saved values to the visible track",
        failures,
    )

    # Negative control for the accepted migration contract: a saved 128 px compass
    # remains 128 px until the player actually edits the 16–72 px control.
    slider_min = 16
    slider_max = 72
    saved = 128
    initial_norm = max(0.0, min(1.0, (saved - slider_min) / (slider_max - slider_min)))
    legacy_label = saved
    first_edit = round(slider_min + (slider_max - slider_min) * initial_norm)
    require(initial_norm == 1.0, "saved 128 px thumb must stay on-track at 1.0", failures)
    require(legacy_label == saved, "saved 128 px label must remain truthful before edit", failures)
    require(
        slider_min <= first_edit <= slider_max,
        "first edit must produce a supported 16–72 px value",
        failures,
    )

    scale_match = re.search(
        r"globe\$VERSION_LABEL_SCALE\s*=\s*([0-9.]+)f;",
        loading,
    )
    version_scale = float(scale_match.group(1)) if scale_match else None
    require(
        version_scale is not None
        and 0.60 <= version_scale <= 0.67
        and 2.9 <= 9.0 * (1.0 - version_scale) <= 4.0,
        "loading version label must be about 3–4 pixels smaller than the 9 px base font",
        failures,
    )

    gap_match = re.search(r"globe\$VERSION_LABEL_GAP\s*=\s*(\d+);", loading)
    margin_match = re.search(
        r"globe\$VERSION_LABEL_SCREEN_MARGIN\s*=\s*(\d+);",
        loading,
    )
    version_gap = int(gap_match.group(1)) if gap_match else None
    screen_margin = int(margin_match.group(1)) if margin_match else None
    require(
        version_gap is not None and 1 <= version_gap <= 3,
        "loading version label must sit immediately below the pane with a 1–3 px gap",
        failures,
    )
    require(
        screen_margin is not None and 1 <= screen_margin <= 4,
        "loading version label must keep a small explicit screen-bottom margin",
        failures,
    )

    method_match = re.search(
        r"private void globe\$drawVersionLabel\([^)]*\)\s*\{(?P<body>.*?)\n\s*\}\n\n\s*@Unique",
        loading,
        re.DOTALL,
    )
    version_body = method_match.group("body") if method_match else ""
    require(
        re.search(
            r"scaledWidth\s*=\s*this\.font\.width\(globe\$VERSION_LABEL\)\s*"
            r"\*\s*globe\$VERSION_LABEL_SCALE\s*;",
            version_body,
        )
        is not None
        and re.search(r"paneRight\s*=\s*paneX\s*\+\s*paneW\s*;", version_body)
        is not None
        and re.search(r"x\s*=\s*paneRight\s*-\s*scaledWidth\s*;", version_body)
        is not None,
        "loading version label must be right-aligned exactly to the pane's right edge",
        failures,
    )
    require(
        re.search(r"paneBottom\s*=\s*paneY\s*\+\s*paneH\s*;", version_body)
        is not None
        and re.search(
            r"preferredY\s*=\s*paneBottom\s*\+\s*globe\$VERSION_LABEL_GAP\s*;",
            version_body,
        )
        is not None
        and re.search(
            r"maxY\s*=\s*context\.guiHeight\(\)\s*-\s*scaledHeight\s*-\s*"
            r"globe\$VERSION_LABEL_SCREEN_MARGIN\s*;",
            version_body,
        )
        is not None
        and re.search(r"y\s*=\s*Math\.min\(\s*preferredY\s*,\s*maxY\s*\)\s*;", version_body)
        is not None,
        "loading version label must render below the pane and clamp to the screen bottom",
        failures,
    )
    require(
        re.search(r"matrices\.pushMatrix\(\)\s*;", version_body) is not None
        and re.search(
            r"matrices\.scale\(\s*globe\$VERSION_LABEL_SCALE\s*,\s*"
            r"globe\$VERSION_LABEL_SCALE\s*\)\s*;",
            version_body,
        )
        is not None
        and re.search(r"matrices\.popMatrix\(\)\s*;", version_body) is not None,
        "loading label must use the 26.2 pose scale API",
        failures,
    )
    require(
        re.search(r"drawX\s*=\s*x\s*/\s*globe\$VERSION_LABEL_SCALE\s*;", version_body)
        is not None
        and re.search(r"drawY\s*=\s*y\s*/\s*globe\$VERSION_LABEL_SCALE\s*;", version_body)
        is not None,
        "scaled loading text coordinates must preserve the computed lower-right anchor",
        failures,
    )

    failures.extend(
        loading_activation_failures(loading, fabric_metadata, mixin_config)
    )
    negative_control_count = require_loading_negative_controls(
        loading,
        fabric_metadata,
        mixin_config,
        failures,
    )

    if version_scale is not None and version_gap is not None and screen_margin is not None:
        scaled_height = 9.0 * version_scale
        for screen_height in (60, 80, 120, 160, 240, 480):
            pane_height = min(screen_height - 40, 200)
            pane_y = (screen_height - pane_height) / 2.0
            pane_bottom = pane_y + pane_height
            preferred_y = pane_bottom + version_gap
            max_y = screen_height - scaled_height - screen_margin
            actual_y = min(preferred_y, max_y)
            require(
                actual_y >= pane_bottom
                and actual_y + scaled_height <= screen_height - screen_margin + 0.001,
                f"loading version label must stay below-pane and on-screen at height {screen_height}",
                failures,
            )

    if failures:
        print("HUD/loading acceptance verifier: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("HUD/loading acceptance verifier: PASS")
    print("- fresh/reset compass: Analog, 32 px")
    print("- Studio compass-size control: 16–72 whole pixels")
    print("- saved analog-size sanitizer: 16–128 px (no forced migration)")
    print("- loading version label: ~6 px, below-pane, right-aligned, screen-bounded")
    print("- loading activation: required hook, reachable draw, visible metadata-backed label")
    print("- loading source identity: immutable independently reviewed candidate bytes")
    print(
        f"- loading negative controls: {negative_control_count}/{negative_control_count} "
        "removal, bypass, and byte-mutation discriminators turn RED"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
