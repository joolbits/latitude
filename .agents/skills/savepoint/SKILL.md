---
name: savepoint
description: "Create Git savepoints after a successful change: show status, interactively stage intended hunks, commit with a concise slice summary, tag as save/slug, push commit and tag, then report branch+hash+tag. Use when you need a fast rollback point or to checkpoint a completed slice."
---

# Savepoint

## Quick Workflow
- Run from the repo root; keep working tree clean of unrelated files.
- Show status: `git status -sb`.
- Stage only intended changes: `git add -p` (repeat until ready).
- Commit with a short, descriptive slice summary, e.g., `git commit -m "fix: windswept savanna override survival gate"`.
- Create a savepoint tag with a terse slug: `git tag save/<short-description>` (dashes only, no spaces), e.g., `git tag save/wsav-override-survival`.
- Push commit and tag: `git push` then `git push origin save/<short-description>`.
- Confirm to the user with branch name, commit hash, and tag name.

## Tips
- If staging skips files, rerun `git status -sb` before tagging to ensure cleanliness.
- Keep slugs unique per branch; prefer 2–4 word dash-separated phrases.
- If the commit already exists remotely, push only the tag.
