# Savepoint Guard Examples

## $savepoint-guard

Use for a read-only pre-flight check before creating a savepoint.

Expected outcome:

- inspect repo root, branch, HEAD, staged files, unstaged files, and diff stats
- decide safe or stop
- never mutate Git state

## $savepoint-guard check

Use when you want the guard decision in a concise form.

Expected outcome:

- return the required summary
- end with one decision line

## $savepoint-guard status

Use when you want the same read-only pre-flight check with a status-style summary.

Expected outcome:

- show whether runtime/generated junk is present
- show whether the slice appears single-axis
- if safe, say: `Proceed with the savepoint skill.`

## Stop Examples

Stop when:

- source edits are mixed with runtime outputs or generated artifacts
- unrelated subsystems are touched at once
- staged and unstaged work are interleaved in a confusing way
- the diff is too broad to describe as one slice
