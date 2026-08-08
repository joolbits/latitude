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
| Windswept forest/hills snow stripping (ProtoChunk guard) | `a626c45c` | **Yes** | **Yes** | `ProtoChunkSnowBlockGuardMixin` gates purely on `globe$isWarmBand` with no `coldEnoughToSnow` check, in both tags verbatim. **INCOMPLETE ALONE — see the trap row below; backport them together.** |
| Windswept snow stripping, third stripper (warm-snow trap) | `709a79ec` | **Yes** | **Yes** | `ChunkRegionWarmSnowTrapMixin` present in both tags with zero `coldEnoughToSnow` refs — rewrites all warm-band snow writes to AIR/STONE at the `WorldGenRegion.setBlock` layer, mass-producing `grass_block[snowy=true]` orphans in temperate windswept (measured 159 snowy grass : 13 layers). The half `a626c45c` missed. |
| *(design fix)* Latitude snow line for windswept (`seaLevel+27`) | `709a79ec` | **carry** | **carry** | Not a bug in the released code — a Latitude-design remedy for windswept painting mostly below vanilla's ~`seaLevel+57` snow line and reading bare. `WindsweptSnowLinePolicy` + `SnowAndFreezeWindsweptSnowLineMixin` + exemptions at both guards; carry with the two stripper fixes so windswept reads as windswept. |
| `savanna_plateau` overriding a low-Y sanitize result | `db5fe2c2` | **Yes** | **Yes** | `preserveSavannaPlateauAtSanitize`, byte-identical in both tags, both call sites. Introducing commit `aefad5b4` predates the 1.5 campaign entirely (2026-03-08) and is an ancestor of both. |
| Reload shows vanilla before the bespoke loading screen | `8695216b` | **Yes** | **Yes** | Neither tag has an early-activation hook (nothing targets `WorldOpenFlows.openWorld`); both have only the same three late `activateLatitudeLoading()` call sites this thread had before the fix. |
| `/locate structure` teleports to Y=0 (bedrock/deep dark) | `8cf9ab91` | **Yes** | **N/A** | `LatitudeStructureLocateService` doesn't exist in `v1.5.0+26.2` at all (thread 1 added it post-tag). In `v1.5.0+26.1.2` it calls `showLocateResult(..., true, ...)` — same bug, same fix would apply. |
| `/locate structure` false-reports villages (guard mismatch) | `2becc0a0` | **Yes** | **N/A** | Same reasoning as above — 26.1.2's `LatitudeStructureLocateService` only ever checks the shared biome-tag condition, never the four village-specific `ExtremePolarVillageStartGuardMixin` conditions. |
| `/locate structure` blocks the server thread | `2becc0a0` | **Yes** | **N/A** | Same file, same reasoning — 26.1.2's search runs synchronously. |
| Create-world screen's second tab read "Rules" | `b14f2b3b` | **No** | **Yes** | Already fixed in 26.1.2 by thread 1's own `a6146016` ("Settings tab, tighter header, Atlas in immediate view") — that commit's *other* changes (header tightening, Atlas visibility) were deliberately left un-harvested on this thread per the maintainer's call in Slice F, but the label itself already matched what she wanted, so `b14f2b3b` reached the same end state narrowly. `v1.5.0+26.2` (pre-dating `a6146016`) still says `{"World", "Rules"}`. |
| TEST-jar staged from unmapped (`jar`, not `remapJar`) classes | `ed65dfa6` | **N/A** | **N/A** | 26.1.2 and 26.2 both ship *unobfuscated* — `jar` and `remapJar` produce identical output there, so this bug cannot manifest regardless of the packaging task. 1.21.11-specific by construction, not a backport candidate. |
| Compass HUD shifts when the location-detail label's length changes | `1f14fbb0` | **Yes** | **Yes** | Same `boxW` (includes the location-detail segment) fed into `anchoredX`/`anchoredY`, verbatim, at all 6 call sites in both tags. |
| *(remedy feature)* `/latitude retrofit` — opt-in retro-decoration + profile adoption for legacy worlds | `a3cb4baa` | **carry** | **carry** | Not a bug row: the player-facing remedy for the two rows above. When the ledger-decoration and profile-capture fixes are backported, this feature should travel with them — released-version players are exactly the legacy-world population it exists for. |
| Dedicated-server worlds never capture the provider-ticket profile | `23901e5a` | **Yes** | **Yes** | Capture gated on `pendingRadius > 0` in both tags (26.2 at its pre-`68716f22` shape, 26.1.2 post) — ledger-routed biomes never place on server-created worlds. Fix = capture for any fresh globe overworld in the creation window + radius fixpoint persistence + parity escape hatch. Verified end-to-end incl. region-file ground truth. |
| Ledger-admitted custom biomes generate bare (no decoration) | `353feb26` | **Yes** | **Yes** | `ChunkGeneratorGenerateFeaturesBiomeSetMixin`'s policy list is built from the `lat_*` tags only, in both tags verbatim (zero `BiomeDescriptorLedger` references in the mixin), while both ledgers carry untagged entries — 39 biomes exposed (16 BoP + 23 Terralith incl. all Terralith caves). Fix = union the ledger into the policy list; verified live on a real BoP stack (policyCustomBiomes 26→42, index 1904/1904, retainAll re-add firing). |

## Still to check

Nothing outstanding as of this writing — every fix landed on this thread so far has been checked
against both tags. Add a row here immediately when a new fix lands, before moving on.

## Observed on this thread, NOT yet fixed anywhere (all versions affected)

Both found while verifying the ledger-decoration fix — see
[`…ledger-decoration-fix-20260807.md`](../binder/latitude-1-5-port-1p21p11-ledger-decoration-fix-20260807.md)
for the evidence. Architecture-level, present in this port and (by the same architecture) in both
released tags; neither is fixed on any version yet.

- **`/locate biome` cannot see Latitude's repaint.** A biome physically present ~2.5k blocks from
  the search origin (well inside vanilla's 6400 radius) returns not-found — the command's query path
  runs outside the worldgen scope where the repaint applies. Same family as the `/locate structure`
  fixes above.
- **Unscoped biome queries return picks inconsistent with real generation** (stronger form of the
  `/locate biome` entry above, measured while verifying the profile-capture fix): `getBiome` over
  unloaded columns — which is nearly everything on a quiet 1.20.5+ server, spawn chunks no longer
  stay loaded — fed `execute if biome` probes that reported 18 distinct biomes over an area whose
  stored region-file truth holds exactly 2. Region-file parsing is the trustworthy instrument.
  (The dedicated-server profile-capture gap formerly listed here was FIXED on this thread —
  `23901e5a`, see the table above.)
