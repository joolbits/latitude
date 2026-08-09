# Backport candidates — fixes found on the 1.21.11 thread, checked against the release tags

`date: 2026-08-07` · `owner: Maintainer (she/her)` · `branch: port/1.5.0-1.21.11` · `status: living document`

Running list of every fix landed on this port thread that turned out to be a genuine 1.5-wide bug
(not a port artifact), checked against `v1.5.0+26.1.2` and `v1.5.0+26.2` directly via
`git show <tag>:<path>` / `git merge-base --is-ancestor` in this repo's own object store — read-only,
never touches either protected checkout. Updated as new findings land. Backporting itself is the maintainer's
call; this only tracks what's confirmed and where.


## ⚠️ Commit hashes were re-mapped after the 2026-08-07 history rewrite

Every hash in the table below is a **post-rewrite, on-branch** hash. The hashes originally
recorded here were pre-rewrite and are **no longer on any branch** — they resolve only as
unreachable objects, and their trees still contain the 13,326 purged files (`_mcsrc*`,
`<home>/CascadeProjects/Latitude-notes/port-1.5-1.21.11/binder-recovered-20260807/**`). Never branch from, merge, or `rebase --onto` a pre-rewrite hash: cherry-pick
applies only a diff and is safe, but the other three would reintroduce forbidden content into a
PUBLIC repository. The pre-rewrite tip is preserved locally as tag
`archive/pre-rewrite-1p21p11-session-20260807` (local only — do not push).

## How to read this table

**In 26.1.2 / In 26.2** — whether the *bug* is present in that released tag: **Yes** (confirmed
present, backport candidate), **No** (already fixed there, nothing to do), **N/A** (the feature the
bug lives in doesn't exist in that tag at all — not a bug there, just absent).

These columns describe the *bug*, not the backport. For what has actually been **landed** on a
line, see [Backport status — 26.1.2](#backport-status--2612-landed-2026-08-09) below.

| Fix (this thread) | Commit | In 26.1.2 | In 26.2 | Note |
| --- | --- | --- | --- | --- |
| Windswept forest/hills snow stripping (ProtoChunk guard) | `3b0b3432` | **Yes** | **Yes** | `ProtoChunkSnowBlockGuardMixin` gates purely on `globe$isWarmBand` with no `coldEnoughToSnow` check, in both tags verbatim. **INCOMPLETE ALONE — see the trap row below; backport them together.** |
| Windswept snow stripping, third stripper (warm-snow trap) | `8fa3d351` | **Yes** | **Yes** | `ChunkRegionWarmSnowTrapMixin` present in both tags with zero `coldEnoughToSnow` refs — rewrites all warm-band snow writes to AIR/STONE at the `WorldGenRegion.setBlock` layer, mass-producing `grass_block[snowy=true]` orphans in temperate windswept (measured 159 snowy grass : 13 layers). The half `3b0b3432` missed. |
| *(design fix)* Latitude snow line for windswept (`seaLevel+27`) | `8fa3d351` | **carry** | **carry** | Not a bug in the released code — a Latitude-design remedy for windswept painting mostly below vanilla's ~`seaLevel+57` snow line and reading bare. `WindsweptSnowLinePolicy` + `SnowAndFreezeWindsweptSnowLineMixin` + exemptions at both guards; carry with the two stripper fixes so windswept reads as windswept. |
| `savanna_plateau` overriding a low-Y sanitize result | `b4f31e36` | **Yes** | **Yes** | `preserveSavannaPlateauAtSanitize`, byte-identical in both tags, both call sites. Introducing commit `aefad5b4` predates the 1.5 campaign entirely (2026-03-08) and is an ancestor of both. |
| Reload shows vanilla before the bespoke loading screen | `e93b9e4f` | **Yes** | **Yes** | Neither tag has an early-activation hook (nothing targets `WorldOpenFlows.openWorld`); both have only the same three late `activateLatitudeLoading()` call sites this thread had before the fix. |
| `/locate structure` teleports to Y=0 (bedrock/deep dark) | `f19a7f96` | **Yes** | **N/A** | `LatitudeStructureLocateService` doesn't exist in `v1.5.0+26.2` at all (thread 1 added it post-tag). In `v1.5.0+26.1.2` it calls `showLocateResult(..., true, ...)` — same bug, same fix would apply. |
| `/locate structure` false-reports villages (guard mismatch) | `11548378` | **Yes** | **N/A** | Same reasoning as above — 26.1.2's `LatitudeStructureLocateService` only ever checks the shared biome-tag condition, never the four village-specific `ExtremePolarVillageStartGuardMixin` conditions. |
| `/locate structure` blocks the server thread | `11548378` | **Yes** | **N/A** | Same file, same reasoning — 26.1.2's search runs synchronously. |
| Create-world screen's second tab read "Rules" | `e66429c2` | **No** | **Yes** | Already fixed in 26.1.2 by thread 1's own `a6146016` ("Settings tab, tighter header, Atlas in immediate view") — that commit's *other* changes (header tightening, Atlas visibility) were deliberately left un-harvested on this thread per the maintainer's call in Slice F, but the label itself already matched what she wanted, so `e66429c2` reached the same end state narrowly. `v1.5.0+26.2` (pre-dating `a6146016`) still says `{"World", "Rules"}`. |
| TEST-jar staged from unmapped (`jar`, not `remapJar`) classes | `2a8cc1e1` | **N/A** | **N/A** | 26.1.2 and 26.2 both ship *unobfuscated* — `jar` and `remapJar` produce identical output there, so this bug cannot manifest regardless of the packaging task. 1.21.11-specific by construction, not a backport candidate. |
| Compass HUD shifts when the location-detail label's length changes | `a00fe7cb` | **Yes** | **Yes** | Same `boxW` (includes the location-detail segment) fed into `anchoredX`/`anchoredY`, verbatim, at all 6 call sites in both tags. |
| *(remedy feature)* `/latitude retrofit` — opt-in retro-decoration + profile adoption for legacy worlds | `69132f9a` + `cf55480b` | **carry — BLOCKED** | **carry — BLOCKED** | Not a bug row: the player-facing remedy for the two rows above. **DO NOT BACKPORT YET.** The 2026-08-09 sweep found the shipped feature converts ANY non-Latitude overworld into a globe world, irreversibly (`cf55480b` gates that), and separately that the replay is not scoped to the repaired biome, uses a per-biome rather than vanilla's global feature index, and runs with `LatitudeWorldgenScope` inactive so Latitude's own generation guards are inert. The gate is fixed; the replay defects are NOT. Carry only once the replay cluster is closed — otherwise both released lines inherit them. |
| Dedicated-server worlds never capture the provider-ticket profile | `36e69a9e` | **Yes** | **Yes** | Capture gated on `pendingRadius > 0` in both tags (26.2 at its pre-`68716f22` shape, 26.1.2 post) — ledger-routed biomes never place on server-created worlds. Fix = capture for any fresh globe overworld in the creation window + radius fixpoint persistence + parity escape hatch. Verified end-to-end incl. region-file ground truth. |
| Ledger-admitted custom biomes generate bare (no decoration) | `58571da4` | **Yes** | **Disputed — see note** | `ChunkGeneratorGenerateFeaturesBiomeSetMixin`'s policy list is built from the `lat_*` tags only, in both tags verbatim (zero `BiomeDescriptorLedger` references in the mixin), while both ledgers carry untagged entries. **26.2 column downgraded 2026-08-07**: Maintainer recalls seeing `overgrown_greens` decorate correctly live on 26.2. The Latitude-side wrap code that determines whether TerraBlender's regions reach `possibleBiomes()` (`ChunkGeneratorBiomeSourceMixin`) is byte-identical between this port and `v1.5.0+26.1.2` — so if 26.2 genuinely differs, the cause is TerraBlender's own version-dependent behavior (unpinned, outside Latitude's control), not a Latitude code difference this thread can see by reading source. Static comparison cannot settle this; only a live boot of the actual `v1.5.0+26.2` tag with real BoP+TerraBlender jars can. Not yet done — cost/priority is the maintainer's call. |
| `/locate biome` cannot find any custom biome (immediate not-found) | `57895f64` | **Yes** | **Disputed — see note above** | Same underlying mechanism as the row above (both depend on whether TerraBlender's regions reach `possibleBiomes()` for the `globe:overworld` biome source), so the same doubt applies to 26.2 pending a live check. 26.1.2 stays **Yes**: nothing has contradicted it there. |
| Fog distance writes clobbered by vanilla (storm + polar walls never render) | `cf55480b` | **No** | **No** | 1.21.11-specific by construction. 26.x's `FogRenderer.setupFog` RETURNS the `FogData`, so the single 26.2 hook mutates it after vanilla is done; the clobber only exists because 1.21.11 moved the distance writes inline after the `FogEnvironment.setupFog` call. Not a backport candidate — but the *lesson* (verify hook position against disassembled bytecode, not source) belongs in every remaining port's diffs-learned. |
| Off-disk world-state reader used a stale path (4 features dead in 100% of worlds) | `cf55480b` | **No** | **No** | 1.21.11-specific: caused by the `SavedDataType` Identifier→String change this port had to make. 26.1.2 and 26.2 still take an Identifier, so their nested path is correct there. The *pattern* — a rename that must move two files at once — is the same family as the CliffTree pair and belongs in diffs-learned. |
| `/latitude retrofit` converts any non-Latitude world (irreversible) | `cf55480b` | **carry with the feature** | **carry with the feature** | Not present in either released tag, because the retrofit feature itself is not. Must travel *with* `69132f9a` if that is ever backported — never without it. |
| Title-screen watermark ships on every public release + paired release-gate checker never actually passed | `23b1be0d` | **N/A** | **N/A** | Downstream of `2a8cc1e1` (TEST-jar remap fix), already N/A for both tags since 26.1.2/26.2 ship unobfuscated -- `jar`/`remapJar` are identical there, so the remap-boundary byte-comparison bug this fix closes cannot exist on those lines. The watermark gate itself (`GlobeMod.isTestOrDevBuild()`) is harmless, generic code that COULD be carried if either line ever adds a similar tester-facing marker, but there is nothing there for it to fix today. |

## Backport status — 26.1.2 (landed 2026-08-09)

Branch `backport/1.21.11-fixes-to-26.1.2`, cut from `port/1.5.0-26.1.2` @ `91423e1a`. Ten picks,
each a separate commit carrying its source hash via `cherry-pick -x`. Gates green
(`clean check build -PenableInvariantScan=true latitudeInvariantScan`, plus
`verify_phase6_dev_tooling.py`); headless worldgen smoke clean. **Not pushed, not tagged, not
released** — awaiting the maintainer's live acceptance. Full account in the notes ledger:
`port-1.5-26.1.2/backport-1p21p11-fixes-to-26p1p2-20260809.md`.

| Fix | Source | 26.1.2 commit | How it applied |
| --- | --- | --- | --- |
| ProtoChunk snow guard (cold columns keep vanilla snow) | `3b0b3432` | `74994ecc` | clean |
| Third snow stripper + `seaLevel+27` windswept snow line | `8fa3d351` | `9cbba932` | clean |
| `savanna_plateau` no longer overrides a low-Y sanitize | `b4f31e36` | `09975255` | clean |
| Loading overlay activates before vanilla's reload screen | `e93b9e4f` | `739ce766` | clean |
| `/locate structure` no longer teleports into bedrock | `f19a7f96` | `68ee7143` | clean |
| `/locate structure` async + no false village reports | `11548378` | `bbfdc16e` | clean |
| Compass HUD stops shifting with label length | `a00fe7cb` | `423949bc` | clean |
| Provider-ticket capture for fresh dedicated worlds | `36e69a9e` | `7a6d7bf5` | **conflict** — see below |
| Decoration index covers ledger-admitted custom biomes | `58571da4` | `2166ab4d` | clean |
| `/locate biome` finds custom biomes | `57895f64` | `65cd2e2f` | **re-homed** — see below |

**`36e69a9e`** — the campaign's only true rename-boundary conflict on this line, in `GlobeMod.java`:
1.21.11's `server.getWorldData().worldGenOptions().seed()` vs 26.1.2's
`server.getWorldGenSettings().options().seed()`. Resolved by the standing rule — 26.x identifier,
1.21.11 logic. `BiomeProviderSelectionPolicyTest` is a source-scan test; all three of its literals
were confirmed present in the resolved source.

**`57895f64`** — upstream extracts the shared tag+ledger union into
`LatitudeDecorationRetrofit.allPaintableCustomBiomes`, a file created by the BLOCKED `69132f9a`.
On 26.1.2 the union lands instead in a new neutral `LatitudePaintableCustomBiomes`, which also
takes ownership of the `lat_*` tag path list formerly private to
`ChunkGeneratorGenerateFeaturesBiomeSetMixin`. Same single source of truth, no blocked feature.

**Not backported to 26.1.2**, and why: `e66429c2` (26.1.2 reached that end state via its own
`a6146016`); `2a8cc1e1` and `23b1be0d` (26.1.2 ships unobfuscated — not applicable by
construction); `69132f9a` + its `cf55480b` gate (retrofit replay defects still unfixed upstream).

**`cf55480b` contributes nothing to 26.1.2 — confirmed, including the claim flagged for
verification.** The fog hook reposition is 1.21.11-specific as recorded. The
`RecreatedWorldMetadata` save-path fix was verified rather than trusted: 26.1.2's
`LatitudeWorldState` `SavedDataType` takes an Identifier (not 1.21.11's String), and real 26.1.2
saves on disk do write the nested `dimensions/minecraft/overworld/data/globe/…` path the reader
expects. Reader path is correct on that line; nothing to carry.

**Instrument note for the remaining threads.** A seed-matched atlas A/B against the pre-backport
base showed `biomes.png` byte-identical, with exactly one zero-sum census move —
`savanna_plateau` 931→5, `savanna` 25447→26373 — which is `b4f31e36` working (both biomes share
the atlas colour `#8FBF63`, so the image is blind to it and only the census sees it). The atlas is
**not** a valid instrument for the other eight fixes: the snow picks act on block writes
(`ProtoChunk`/`WorldGenRegion.setBlock`) rather than biome selection, the ledger pick acts on the
decoration feature index, and `36e69a9e` never engages on the atlas server at all
(`level-type=minecraft:normal`, so `isGlobeOverworld` fails). Do not read atlas stability as
evidence those fixes work.

## Still to check

Nothing outstanding as of this writing — every fix landed on this thread so far has been checked
against both tags. Add a row here immediately when a new fix lands, before moving on.

## Observed on this thread, NOT yet fixed anywhere (all versions affected)

Found while verifying the ledger-decoration fix — see
[`…ledger-decoration-fix-20260807.md`](../../../Latitude-notes/port-1.5-1.21.11/binder-recovered-20260807/latitude-1-5-port-1p21p11-ledger-decoration-fix-20260807.md)
for the evidence. Architecture-level, present in this port and (by the same architecture) in both
released tags; not fixed on any version yet.

- **Unscoped biome queries return picks inconsistent with real generation** (a related but distinct
  command from `/locate biome`, fixed above): `getBiome` over unloaded columns — which is nearly
  everything on a quiet 1.20.5+ server, spawn chunks no longer stay loaded — fed `execute if biome`
  probes that reported 18 distinct biomes over an area whose stored region-file truth holds exactly
  2. Region-file parsing is the trustworthy instrument.
  (The `/locate biome` immediate-not-found issue formerly listed here, and the dedicated-server
  profile-capture gap before it, were both FIXED on this thread — `57895f64` and `36e69a9e`, see
  the table above.)
