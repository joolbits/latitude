# Backport candidates — fixes found on the 1.21.11 thread, checked against the release tags

`date: 2026-08-07` · `owner: Maintainer (she/her)` · `branch: port/1.5.0-1.21.11` · `status: living document`

Running list of every fix landed on this port thread that turned out to be a genuine 1.5-wide bug
(not a port artifact), checked against `v1.5.0+26.1.2` and `v1.5.0+26.2` directly via
`git show <tag>:<path>` / `git merge-base --is-ancestor` in this repo's own object store — read-only,
never touches either protected checkout. Updated as new findings land. Backporting itself is the maintainer's
call; this only tracks what's confirmed and where.

## How to read this table

**In 26.1.2 / In 26.2** — whether the *bug* is present in that released tag: **Yes** (confirmed
present, backport candidate), **No** (already fixed there, nothing to do), **N/A** (the feature the
bug lives in doesn't exist in that tag at all — not a bug there, just absent).

| Fix (this thread) | Commit | In 26.1.2 | In 26.2 | Note |
| --- | --- | --- | --- | --- |
| Windswept forest/hills snow stripping | `a626c45c` | **Yes** | **Yes** | `ProtoChunkSnowBlockGuardMixin` gates purely on `globe$isWarmBand` (Tropical/Subtropical/Temperate) with no `coldEnoughToSnow` check, in both tags verbatim. Thread 1's own doc called this "diagnosed, not yet fixed" — confirmed still true in both releases. |
| `savanna_plateau` overriding a low-Y sanitize result | `db5fe2c2` | **Yes** | **Yes** | `preserveSavannaPlateauAtSanitize`, byte-identical in both tags, both call sites. Introducing commit `aefad5b4` predates the 1.5 campaign entirely (2026-03-08) and is an ancestor of both. |
| Reload shows vanilla before the bespoke loading screen | `8695216b` | **Yes** | **Yes** | Neither tag has an early-activation hook (nothing targets `WorldOpenFlows.openWorld`); both have only the same three late `activateLatitudeLoading()` call sites this thread had before the fix. |
| `/locate structure` teleports to Y=0 (bedrock/deep dark) | `8cf9ab91` | **Yes** | **N/A** | `LatitudeStructureLocateService` doesn't exist in `v1.5.0+26.2` at all (thread 1 added it post-tag). In `v1.5.0+26.1.2` it calls `showLocateResult(..., true, ...)` — same bug, same fix would apply. |
| `/locate structure` false-reports villages (guard mismatch) | `2becc0a0` | **Yes** | **N/A** | Same reasoning as above — 26.1.2's `LatitudeStructureLocateService` only ever checks the shared biome-tag condition, never the four village-specific `ExtremePolarVillageStartGuardMixin` conditions. |
| `/locate structure` blocks the server thread | `2becc0a0` | **Yes** | **N/A** | Same file, same reasoning — 26.1.2's search runs synchronously. |
| Create-world screen's second tab read "Rules" | `b14f2b3b` | **No** | **Yes** | Already fixed in 26.1.2 by thread 1's own `a6146016` ("Settings tab, tighter header, Atlas in immediate view") — that commit's *other* changes (header tightening, Atlas visibility) were deliberately left un-harvested on this thread per the maintainer's call in Slice F, but the label itself already matched what she wanted, so `b14f2b3b` reached the same end state narrowly. `v1.5.0+26.2` (pre-dating `a6146016`) still says `{"World", "Rules"}`. |
| TEST-jar staged from unmapped (`jar`, not `remapJar`) classes | `ed65dfa6` | **N/A** | **N/A** | 26.1.2 and 26.2 both ship *unobfuscated* — `jar` and `remapJar` produce identical output there, so this bug cannot manifest regardless of the packaging task. 1.21.11-specific by construction, not a backport candidate. |
| Compass HUD shifts when the location-detail label's length changes | `1f14fbb0` | **Yes** | **Yes** | Same `boxW` (includes the location-detail segment) fed into `anchoredX`/`anchoredY`, verbatim, at all 6 call sites in both tags. |

## Still to check

Nothing outstanding as of this writing — every fix landed on this thread so far has been checked
against both tags. Add a row here immediately when a new fix lands, before moving on.

## Open, not yet a landed fix

- **BiomesOPlenty decoration gap** — see [`…biomesoplenty-decoration-gap-20260807.md`](../binder/latitude-1-5-port-1p21p11-biomesoplenty-decoration-gap-20260807.md). Root cause understood, fix not yet written or scoped with Maintainer. Once a fix lands, check it against both tags like everything else here.
