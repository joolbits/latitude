Windsurf — implement **`/cc` = “continue conversation / context handover”**.

## What `/cc` means

When the user types **`/cc`**, output a **concise, copy-pasteable “FULL CONTEXT HANDOVER”** that they can drop into a brand-new chat so the new assistant can pick up instantly.

### Hard requirements

1. **Do NOT ask questions first.** Produce the handover immediately.
2. **Include the `wsf` convention** in the handover:

   * `wsf` = respond in Windsurf step-by-step style (direct instructions, exact paths/commands, “Done when…” checks, rollback steps).
3. Keep it **structured and scannable** (YAML preferred).
4. Include **only facts known from this chat/project**. If uncertain, label `uncertain:`.

---

## What the `/cc` handover must contain (minimum fields)

Use YAML like this:

```yaml
# /cc — Latitude Mod (Minecraft) — CONTEXT HANDOVER
# NOTE: wsf convention = step-by-step (exact paths/commands + done-when checks + rollback)

project:
  name_public: "Latitude"
  former_name: "Globe"
  mc_version: "1.21.x"
  loader: "Fabric"
  java: "21"
  branding_non_negotiable:
    - "Latitude" only (never "Globe" or "Latitude (Globe)")
    - display credit must be "By Peetsa"

repo:
  repo_url: "https://github.com/joolbits/latitude.git"
  branch_in_play: "<current branch>"
  last_known_good_release: "v1.2.2"
  savepoints_tags:
    - "<tag1>"
    - "<tag2>"
  working_tree: "clean | dirty"
  last_build_status: "BUILD SUCCESSFUL | BUILD FAILED"
  last_build_errors:
    - "<compiler error lines if any>"

current_goals:
  - "Deterministic globe-like latitude biome bands"
  - "Band-edge blending like v1.2.2 (feathered overlaps, not ruler-straight seams)"
  - "No flower_forest in equator"
  - "Subpolar is strongly snowy; no plains; rivers should be frozen_river"
  - "Polar is treeless; no forest/grove"
  - "Mangrove should be lowland + patchy (not mountainous)"

current_regressions_observed:
  - "<what user is seeing right now>"
  - "<e.g., forest/grove in polar, plains/river in subpolar, flower_forest in equator>"
  - "<harsh band lines still present>"

recent_work_summary:
  - "<what changed recently (files + intent)>"
  - "<e.g., sampler plumbing, mangrove gating, attempted 1.2.2-style band blending>"

critical_guardrails:
  - "Do not touch oceans/shore/rivers unless explicitly in task"
  - "Do not change terrain/noise unless proven"
  - "Checkpoint discipline: tag/commit frequently; rollback if drift"

next_actions_priority:
  - "<1> Restore correct band-edge blending WITHOUT biome leakage"
  - "<2> Re-verify tag pools actually used at runtime (not just printed once)"
  - "<3> Verify biome selection path used by land sampling vs cave/structure paths"
  - "<4> Run IB + Ginormous new-world validation protocol"
validation_protocol:
  - "Create NEW worlds: Itty Bitty + Ginormous!"
  - "F3 spot checks: equator, subpolar, polar"
  - "Collect logs: latest.log filters for LAT_PICK / LAT_BLEND / band/tag prints"

wsf_instruction:
  - "Speak directly to Windsurf"
  - "Numbered steps, exact paths/commands"
  - "After each step: Done when…"
  - "Include rollback commands"
```

---

## Extra: what to include when the situation is “things got worse”

If the latest change caused obvious regressions (e.g., taiga/meadows in equator, forest/rivers in polar), the `/cc` handover must also include:

* `suspected_cause:` (e.g., blend helper now selecting neighbor band incorrectly; tag pools not actually applied in the land path; incorrect registry type usage; cave/structure repick bypassing band filters)
* `recommended_rollback:` a safe git revert/restore plan (tag current state first, then reset to last good tag/commit)

Example rollback block to include:

```yaml
recommended_rollback:
  - "git status -sb"
  - "git tag -a vNEXT-bad-state-before-rollback -m \"Before rollback\""
  - "git reset --hard <last_good_commit_or_tag>"
```

---

## One-line instruction you should follow when user types `/cc`

**Output the YAML handover only** (no commentary, no preamble), unless the user explicitly asks for explanation.

That’s it — implement `/cc` as an always-available “export project state” command.
