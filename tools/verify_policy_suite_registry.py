#!/usr/bin/env python3
"""Policy-suite registry gate — the silent-skip closer.

The policy suites are dependency-free main() classes in sibling source sets, each launched by a
hand-wired JavaExec in build.gradle, with additional classes reached only by hand-edited calls
from an aggregator. Add a new test class and forget the wiring -> it compiles, never runs, and
`check` stays green. This gate enumerates every candidate test class under src/*PolicyTest and
asserts each is REACHABLE: either a JavaExec mainClass, or invoked (Class.main / Class.run /
Class.verify) from a class that is itself reachable, transitively.

Wired into `check` via verifyPolicySuiteRegistry in build.gradle. Fails naming the orphans.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    gradle = (ROOT / "build.gradle").read_text(encoding="utf-8")
    roots = set(re.findall(r"mainClass = '([\w.]+)'", gradle))

    # candidate classes: every .java under a *PolicyTest source set
    candidates: dict[str, Path] = {}
    for src_set in ROOT.glob("src/*PolicyTest/java"):
        for f in src_set.rglob("*.java"):
            pkg_m = re.search(r"^package ([\w.]+);", f.read_text(encoding="utf-8"), re.M)
            fqn = (pkg_m.group(1) + "." if pkg_m else "") + f.stem
            candidates[fqn] = f

    # reachability: BFS from the JavaExec roots through simple-name invocations
    simple_to_fqn = {fqn.rsplit(".", 1)[1]: fqn for fqn in candidates}
    reached = set()
    frontier = [fqn for fqn in roots if fqn in candidates]
    while frontier:
        fqn = frontier.pop()
        if fqn in reached:
            continue
        reached.add(fqn)
        src = candidates[fqn].read_text(encoding="utf-8")
        for simple, target in simple_to_fqn.items():
            if target in reached or target == fqn:
                continue
            # any static call into the class counts — an aggregator invoking Foo.anything() runs Foo
            if re.search(rf"\b{simple}\.\w+\s*\(", src):
                frontier.append(target)

    orphans = sorted(set(candidates) - reached)
    if orphans:
        print("verify_policy_suite_registry: FAIL — test classes exist that NOTHING executes "
              "(compiles green, never runs — the silent-skip class):")
        for o in orphans:
            print(f"  - {o}  ({candidates[o].relative_to(ROOT)})")
        print("Wire each into build.gradle as a JavaExec mainClass, or call it from a reachable "
              "aggregator, or delete it deliberately.")
        return 1
    print(f"verify_policy_suite_registry: OK — {len(candidates)} policy-test classes, all "
          f"reachable from {len(roots & set(candidates))} JavaExec roots.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
