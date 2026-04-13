# Workspace Sync Handoff Examples

## I’m leaving my Mac

Use the leave-this-machine workflow.

- Inspect repo status first.
- If dirty, choose either a handoff commit or a labeled stash.
- Fetch, rebase only if safe, then push the current branch.
- End with branch, commit, push result, and stash state.
- Use this when you are done on the Mac and want the same repo ready on the laptop.

## I’m arriving on my laptop

Use the arrive-on-other-machine workflow.

- Inspect repo status first.
- Fetch all remotes with pruning.
- Pull with `--rebase` if the branch tracks `origin` and the history is linear enough to replay safely.
- Stop if divergence or conflict risk appears.
- Use this when you open the same repo on the laptop after leaving the Mac.

## I have uncommitted changes

Use the safe path selection rule.

- Prefer a handoff commit if the changes are ready to preserve in history.
- Prefer a labeled stash if the work is unfinished or experimental.
- Never overwrite local work silently.
- This is a handoff decision, not a cleanup or branch-rewrite decision.

## Both machines changed

Treat this as a potential divergence case.

- Fetch first.
- Compare local and remote commit tips.
- If both sides advanced, stop and explain the divergence before trying to reconcile.
- Do not force-push or hard-reset.
- This usually means the same repo moved on both devices and needs a careful handoff instead of a blind sync.
