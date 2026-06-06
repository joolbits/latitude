# Latitude repository instructions

These rules apply to every human or automated contributor.

## Privacy and legal safety

Treat every Git object, branch, tag, build report, and artifact as public.

Never place these categories in tracked content, Git metadata, names, artifacts,
or public communication:

- personal identity, email addresses, machine usernames, device names, or
  absolute local paths;
- operator-only records, conversational chronology, temporary proof output,
  worlds, crash output, or local run state;
- credentials, tokens, cookies, private keys, or credential-bearing URLs;
- decompiled or extracted Minecraft source, mappings dumps, or proprietary
  reference material;
- third-party code, data, text, or assets without documented provenance and
  redistribution permission.

Use Maintainer or Tester for roles. Use Peetsa only when public authorship is
necessary. Store operator-only material in the established external notes tree.

Before every commit, tag, push, release, or publication, run the private
repository safety gate configured by repoSafety.scanner and repoSafety.patterns.
If the gate or private pattern file is unavailable, stop. Never use
--no-verify and never weaken a rule to make a change pass.

## Repository preflight

Before editing, record:

    git rev-parse --show-toplevel
    git status -sb
    git branch --show-current
    git rev-parse HEAD
    git tag --points-at HEAD
    git config --get core.hooksPath

Existing or unexplained changes are protected. Do not edit, stage, restore,
stash, delete, or absorb them. Stop when root, branch, commit, tags, hook wiring,
or protected state differs from the task boundary.

## Product and proof authority

Read README.md, docs/porting/PORTING.md for version migration work, and only the
design or release policy directly relevant to the task. Product documentation
belongs in Git. Operator records do not.

Use the branch-native build and test tasks. Compilation, pure-math tests,
headless world generation, staged artifacts, profiles, and live observations
are separate proof surfaces. Never promote one into another.

Before treating a JAR or source archive as shareable, verify its embedded
identity, provenance, licenses, archive contents, and complete privacy scan.

## Branches and worktrees

Use stable names:

- port branch: port/<minecraft>-<loader>
- fix branch: fix/<minecraft>-<loader>/<purpose>
- compatibility branch: compat/<minecraft>-<loader>/<purpose>
- release branch: release/<approved-version>+<minecraft>-<loader>
- worktree folder: Latitude-<minecraft>-<loader>-<purpose>

Keep one canonical checkout and one worktree for each genuinely active task.
Do not put a product version in an ordinary port or worktree name. Do not create
a duplicate-purpose worktree. Record ownership and intended lifetime outside
Git. No worktree, branch, tag, or saved change is removed automatically.

## External actions

Commits, tags, pushes, releases, history rewrites, ruleset changes, and
deletions are separate authorization boundaries. Successful proof does not
authorize the next boundary.
