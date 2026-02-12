---
description: guardrailed workflow for Latitude client-visual/regression fixes
---

1. Confirm workspace path is `C:\Users\jscho\CascadeProjects\Latitude (Globe)`.
2. STOP if current path includes `.windsurf\worktrees`.
3. One client-visual subsystem per commit (e.g., fog geometry, HUD overlays, sky tint, world border haze). Split changes if crossing subsystems.
4. Tag a savepoint before risky client-visual experiments (e.g., `git tag -a save/pre-experiment -m "pre client-visual change"`).
5. `runClient` is Windsurf-owned—do not modify its launch args or configs unless explicitly requested.
6. Apply shared guardrails in `._shared-guardrails.md` (do not bypass).
