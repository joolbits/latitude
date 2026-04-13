#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass
class RepoState:
    root: str
    branch: str
    head: str
    status: str
    dirty: bool
    origin_exists: bool
    ahead: int | None
    behind: int | None
    relation: str
    stash_entries: list[str]
    recommendation: str


def run_git(args: list[str], cwd: Path) -> str:
    return subprocess.check_output(["git", *args], cwd=cwd, text=True).strip()


def detect_repo(start: Path) -> Path:
    try:
        root = run_git(["rev-parse", "--show-toplevel"], start)
    except subprocess.CalledProcessError:
        raise SystemExit("Not inside a Git repository. Run this from a repo working tree.")
    return Path(root)


def parse_ahead_behind(branch: str, cwd: Path) -> tuple[int | None, int | None]:
    upstream = f"origin/{branch}"
    try:
        counts = run_git(["rev-list", "--left-right", "--count", f"{branch}...{upstream}"], cwd)
        ahead_s, behind_s = counts.split()
        return int(ahead_s), int(behind_s)
    except subprocess.CalledProcessError:
        return None, None
    except ValueError:
        return None, None


def origin_branch_exists(branch: str, cwd: Path) -> bool:
    ref = f"origin/{branch}"
    try:
        run_git(["show-ref", "--verify", "--quiet", f"refs/remotes/{ref}"], cwd)
        return True
    except subprocess.CalledProcessError:
        return False


def get_stash_entries(cwd: Path, limit: int = 3) -> list[str]:
    try:
        stash_list = run_git(["stash", "list", f"--max-count={limit}"], cwd)
    except subprocess.CalledProcessError:
        return []
    if not stash_list:
        return []
    return stash_list.splitlines()


def describe_relation(origin_exists: bool, dirty: bool, ahead: int | None, behind: int | None) -> str:
    if dirty:
        return "local changes exist"
    if not origin_exists:
        return "remote branch missing"
    if ahead is None or behind is None:
        return "unknown"
    if ahead > 0 and behind > 0:
        return "diverged"
    if ahead > 0:
        return "ahead of origin"
    if behind > 0:
        return "behind origin"
    return "even with origin"


def collect_state(root: Path) -> RepoState:
    branch = run_git(["rev-parse", "--abbrev-ref", "HEAD"], root)
    head = run_git(["rev-parse", "--short", "HEAD"], root)
    status = run_git(["status", "-sb"], root)
    dirty = bool(run_git(["status", "--porcelain"], root))
    origin_exists = origin_branch_exists(branch, root)
    ahead, behind = parse_ahead_behind(branch, root) if origin_exists else (None, None)
    stash_entries = get_stash_entries(root)
    relation = describe_relation(origin_exists, dirty, ahead, behind)
    if dirty:
        recommendation = "stop: local changes exist"
    elif relation == "diverged":
        recommendation = "stop: branch divergence"
    elif relation == "behind origin":
        recommendation = "safe to arrive"
    else:
        recommendation = "safe to leave"
    return RepoState(
        root=str(root),
        branch=branch,
        head=head,
        status=status,
        dirty=dirty,
        origin_exists=origin_exists,
        ahead=ahead,
        behind=behind,
        relation=relation,
        stash_entries=stash_entries,
        recommendation=recommendation,
    )


def print_state(state: RepoState) -> None:
    print(f"repo_root: {state.root}")
    print(f"branch: {state.branch}")
    print(f"head: {state.head}")
    print(f"working_tree: {'dirty' if state.dirty else 'clean'}")
    print(f"origin_branch_exists: {'yes' if state.origin_exists else 'no'}")
    if state.ahead is not None and state.behind is not None:
        print(f"origin_relation: {state.relation} (+{state.ahead} / -{state.behind})")
    else:
        print(f"origin_relation: {state.relation}")
    if state.stash_entries:
        print("newest_stashes:")
        for entry in state.stash_entries:
            print(f"  - {entry}")
    else:
        print("newest_stashes: none")
    print(f"status: {state.status}")
    print(f"recommendation: {state.recommendation}")


def main(argv: list[str]) -> int:
    if len(argv) < 2 or argv[1] != "status":
        print("Usage: workspace_sync_handoff.py status", file=sys.stderr)
        return 2
    root = detect_repo(Path.cwd())
    state = collect_state(root)
    print_state(state)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
