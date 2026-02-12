---
description: shared guardrails for Latitude client-visual and regression work
---

- Canonical workspace only: `C:\Users\jscho\CascadeProjects\Latitude (Globe)`.
- Stop immediately if running inside `.windsurf\worktrees` or any non-canonical path.
- One client-visual subsystem per commit (fog geometry, HUD overlay, sky tint, etc.).
- Pre-experiment savepoint tag before risky client-visual changes.
- `runClient` is Windsurf-owned; do not change its launch args or config outside requests.
