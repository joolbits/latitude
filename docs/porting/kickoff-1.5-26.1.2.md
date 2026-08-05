# Port Kickoff — Latitude 1.5.0 → Minecraft 26.1.2 (warm-up, thread 1 of 4)

Read `docs/porting/LATITUDE_1_5_BACKPORT_CAMPAIGN.md` first; it carries the method, gates, and
conventions. This doc is only what is specific to 26.1.2.

## Working card
- **Objective**: `1.5.0+26.1.2` beta, feature-identical to `1.5.0+26.2`.
- **Root**: worktree `<home>/CascadeProjects/Latitude-port-1.5-26.1.2`, branch
  `port/1.5.0-26.1.2` (already cut from tag `v1.5.0+26.2` — verify `git diff v1.5.0+26.2` is empty).
- **Allowed work**: exactly the reversal of the Phase-5 26.1x→26.2 port, plus whatever a named RED
  demands. **Forbidden**: everything on the Phase-5 denylist; 2.0 anything; scope creep.
- **Why warm-up first**: cheapest port possible; validates the whole per-port pipeline
  (branch → gates → profile → TEST → release) before the real gaps.
- **Model/effort**: Opus, high (Sonnet/medium defensible — bounded work; this is the pipeline's first run). See the campaign doc's model section; state your path at each slice.

## The map is exact: reverse these commits
The Phase-5 port (26.1x→26.2) was 6 commits, 33 files, +182/−173 — reverse their intent from the
1.5 side (do NOT `git revert`; retarget forward):
- `33623c7f` port: compile Latitude 1.5 on Minecraft 26.2 (26 files) — the compile ladder in reverse
- `2b7f1c8c` port: adapt Latitude runtime hooks for 26.2 (6 files)
- `738a55f5` frozen river stripping (1 file, 3 lines)
- `10e4970f` Sodium visibility-hook tolerance (1 line) — 26.1.2 Sodium HAS the hook; restore `require`
- Toolchain: Loom/loader/fabric-api per `codex/…26.1x` history; fence `>=26.1.2 <26.2`; Java 25 OK.
Note: work landed on 1.5 AFTER Phase 5 (TEST 15–36: provider ticket v4, tools/ extraction,
loading-screen zone label, world-list badge) has never existed on 26.1.x — expect a handful of new
REDs beyond the 33 files, each admitted by name.

## Target-specific cautions
- The Modrinth profile `Lat 1.4+26.1.2` already contains a mystery `latitude-1.5.0+26.1.jar`
  (probably built from pre-Phase-5 `codex/1.5-mini-launch-26.1x` = 1.5 phases 0–4 only). Do not
  confuse artifacts. Make a fresh profile `Lat 1.5 - 26.1.2 - TEST`; provider jars for 26.1.2 exist
  in the old profile (BoP 26.1.2.0.2, TerraBlender, GlitchCore, CliffTree 3.2.1-26.1).
- Speleothem features are 26.x renames of dripstone features — check which name 26.1.2 uses.
- `archive/codex-1.5-mini-launch-26.1x-20260805` tag preserves the old 26.1x line if reference is
  needed (branch itself still exists too — dirty worktree kept it alive).

## Extra gate owned by this thread (GitHub #8)
Run the campaign's **dedicated-server smoke first here**: fabric server jar + Latitude, fresh world,
verify globe worldgen server-side, join with a Latitude client, confirm `GlobeStatePayload`
handshake + zone titles + HUD. Whatever the result, post it to issue #8 with the 1.5 release links
and the existing-worlds documentation. If the smoke fails, that is a REAL 26.2-line bug too —
report before proceeding.

## Definition of done
Campaign gates green → TEST accepted by Maintainer → `1.5.0+26.1.2` beta on Modrinth → tag
`v1.5.0+26.1.2` → binder rows → #8 updated → "diffs learned" section appended to
`kickoff-1.5-1.21.11.md`.
