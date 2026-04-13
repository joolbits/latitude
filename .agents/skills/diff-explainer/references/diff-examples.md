# Diff Explainer Examples

## $diff-explainer status

Use for a quick read of the current tree or a concise diff explanation.

Expected output:

- repo root
- branch
- HEAD short hash
- changed files
- subsystem grouping
- risk level
- scope read

## $diff-explainer working

Use for unstaged changes in the working tree.

Expected output:

- explain what changed since `HEAD`
- distinguish behavior changes from tooling, docs, or config changes
- flag likely scope creep

## $diff-explainer staged

Use for staged changes only.

Expected output:

- explain what is ready to commit
- mention whether the slice looks narrow or broad
- call out mixed subsystems if present

## $diff-explainer compare HEAD~1 HEAD

Use for comparing two refs directly.

Expected output:

- summarize the ref-to-ref delta in plain English
- group by subsystem
- identify risk areas

## $diff-explainer compare main fix/terrain-regression-triage

Use for branch-to-branch comparison.

Expected output:

- list changed files
- explain the overall shape of the branch delta
- state whether the scope is narrow or broad

## Decision Hints

- Narrow: one subsystem, one sentence can describe the slice
- Broad: several subsystems, unrelated files, or mixed behavior and runtime junk
- Risky: mixins, fallback logic, clamp logic, rendering order, or config/build files
