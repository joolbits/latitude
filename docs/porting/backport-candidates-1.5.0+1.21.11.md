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
line, see [Backport status — 26.1.2](#backport-status--2612-landed-2026-08-09) and
[Backport status — 26.2](#backport-status--262-landed-2026-08-09) below.

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
| Ledger-admitted custom biomes generate bare (no decoration) | `58571da4` | **Yes** | **Yes** (was Disputed; **resolved live 2026-08-09**) | `ChunkGeneratorGenerateFeaturesBiomeSetMixin`'s policy list is built from the `lat_*` tags only, in both tags verbatim (zero `BiomeDescriptorLedger` references in the mixin), while both ledgers carry untagged entries. **The 2026-08-07 downgrade to Disputed is now withdrawn.** A live headless boot of `backport/1.21.11-fixes-to-26.2` with the real 26.2 provider stack (BoP `26.2.0.0.24`, TerraBlender `26.2.0.0.2`, GlitchCore `26.2.0.0.0`) measured **0 of 42** registered non-vanilla ledger biomes present in `possibleBiomes()` — `biomesoplenty:overgrown_greens` among them (`registered=true inPossibleBiomes=false`). TerraBlender on 26.2 behaves exactly as on 1.21.11 and 26.1.2: it does not inject into `globe:` noise settings, so `retainAll` drops every ledger-only biome and the tags-only guard never re-adds it. Applying `58571da4` on that branch moved `policyCustomBiomes` 24→42 and `featureTotal`/`featureInIndex` 1085→1904, and turned a real spawn chunk's `afterSize=3 preservedCustom=1` into `afterSize=4 preservedCustom=2`. This does not contradict the maintainer's recollection that `overgrown_greens` *appears* on her 26.2 world — it is client-created, so the profile is captured and the biome places; what is disproven is that its decoration was safe. |
| `/locate biome` cannot find any custom biome (immediate not-found) | `57895f64` | **Yes** | **Yes** (was Disputed; **resolved live 2026-08-09**) | Same underlying mechanism as the row above — both depend on whether TerraBlender's regions reach `possibleBiomes()` for the `globe:overworld` biome source — and the same live measurement settles it: they do not, on 26.2 as everywhere else. 26.1.2 stays **Yes**: nothing has contradicted it there. |
| Fog distance writes clobbered by vanilla (storm + polar walls never render) | `cf55480b` | **No** | **No** | 1.21.11-specific by construction. 26.x's `FogRenderer.setupFog` RETURNS the `FogData`, so the single 26.2 hook mutates it after vanilla is done; the clobber only exists because 1.21.11 moved the distance writes inline after the `FogEnvironment.setupFog` call. Not a backport candidate — but the *lesson* (verify hook position against disassembled bytecode, not source) belongs in every remaining port's diffs-learned. |
| Off-disk world-state reader used a stale path (4 features dead in 100% of worlds) | `cf55480b` | **No** | **No** | 1.21.11-specific: caused by the `SavedDataType` Identifier→String change this port had to make. 26.1.2 and 26.2 still take an Identifier, so their nested path is correct there. The *pattern* — a rename that must move two files at once — is the same family as the CliffTree pair and belongs in diffs-learned. |
| `/latitude retrofit` converts any non-Latitude world (irreversible) | `cf55480b` | **carry with the feature** | **carry with the feature** | Not present in either released tag, because the retrofit feature itself is not. Must travel *with* `69132f9a` if that is ever backported — never without it. |
| Title-screen watermark ships on every public release + paired release-gate checker never actually passed | `23b1be0d` | **N/A** | **N/A** | Downstream of `2a8cc1e1` (TEST-jar remap fix), already N/A for both tags since 26.1.2/26.2 ship unobfuscated -- `jar`/`remapJar` are identical there, so the remap-boundary byte-comparison bug this fix closes cannot exist on those lines. The watermark gate itself (`GlobeMod.isTestOrDevBuild()`) is harmless, generic code that COULD be carried if either line ever adds a similar tester-facing marker, but there is nothing there for it to fix today. |

## Backport status — 26.1.2 (landed 2026-08-09)

Branch `backport/1.21.11-fixes-to-26.1.2`, cut from `port/1.5.0-26.1.2` @ `91423e1a`. Eleven
picks, each a separate commit carrying its source hash via `cherry-pick -x`. Gates green
(`clean check build -PenableInvariantScan=true latitudeInvariantScan`, plus
`verify_phase6_dev_tooling.py`, re-run in full after the 11th pick); headless worldgen smoke
clean. **Not pushed, not tagged, not released** — awaiting the maintainer's live acceptance. Full
account in the notes ledger: `port-1.5-26.1.2/backport-1p21p11-fixes-to-26p1p2-20260809.md`.

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
| World Creation screen negative-space/alignment pass + tabbedMode title intro | `0cee3189` | `1d93675a` | **3 conflicts** — see below |

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

**`0cee3189`** — landed later, at the maintainer's explicit request, from `94eca269` (the merge of
PR #12, `claude/world-creation-aesthetics-1.21.11-febb24`, into `port/1.5.0-1.21.11` on origin).
That merge bundles three commits; only `0cee3189` itself (the UI polish) is in scope — the other
two are that thread's own repo-hygiene chores (untracking `.agents/`/`.claude/`, removing a stale
`.windsurf/` dir), unrelated to world creation, left alone. Three conflicts in
`LatitudeCreateWorldScreen.java`, which has run independently on each line since the fork:
- A local `shortScreen` compact-layout feature (`74ed12b1`, not from this campaign) and upstream's
  tabbedMode header collapse both wanted the same `headerToPanel` variable. Combined:
  `tabbedMode ? scaledUi(6) : (shortScreen ? scaledUi(26) : scaledUi(42))` — tabbedMode always gets
  the tight value (no permanent header exists there to make room for), three-column mode keeps
  `shortScreen`'s own value.
- The other two conflicts were this campaign's own predicted rename boundary
  (`GuiGraphics`→`GuiGraphicsExtractor`, `render`→`extractRenderState`), materializing for real.
  Worse: **most of the rename was silently mismerged with no conflict marker at all** —
  `renderIntroTitle`, `renderSizeLabel`, and `drawTabStrip`'s signatures all landed with the wrong
  1.21.11-era type, because git's 3-way cherry-pick diffs against `0cee3189`'s own parent on the
  1.21.11 line, not any shared ancestor with 26.1.2, so an "unchanged" `GuiGraphics` context line
  inside the pick's hunk boundary just carries through verbatim. Caught only by grepping the whole
  file for every remaining `(GuiGraphics `, bare `.render(context`, and `.renderWidget(` after the
  two marked conflicts were resolved — `compileJava` only went green after that sweep, not after
  the marked conflicts alone. **Lesson for the remaining threads: a clean `cherry-pick --continue`
  on this rename family proves the marked conflicts are gone, not that the file is correct — sweep
  for the unmarked ones by hand.**

This pick is client-only Screen code; no headless instrument (atlas, `verify_phase6_dev_tooling`,
policy suites) touches it. It compiles and gates green but has had zero live eyes on it as of this
writing — squarely in the maintainer's live-acceptance queue, same as items 1 and 5 in the
instrument note below.

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

## Backport status — 26.2 (landed 2026-08-09)

Branch `backport/1.21.11-fixes-to-26.2`, cut from `codex/1.5-mini-launch-26.2` @ `cef5ab29` (past
the `v1.5.0+26.2` release tag). **Sixteen commits** (14 planned + 2 added mid-flight): this thread's fixes PLUS the four pending
26.1.2 live-flight fixes, consolidated into one branch because all four had already been harvested
here, so the two backlogs overlapped. Gates green — `clean check build -PenableInvariantScan=true
latitudeInvariantScan`, 27/27 tasks executed, plus `verify_phase6_dev_tooling.py`. **Not pushed,
not tagged, not released** — awaiting the maintainer's live acceptance. Full account in the notes
ledger: `port-1.5-26.2/backport-1p21p11-and-liveflight-fixes-to-26p2-20260809.md`.

Stream A — from `port/1.5.0-26.1.2`, in that branch's chronological order.

| Fix | Source | 26.2 commit | How it applied |
| --- | --- | --- | --- |
| Fail loud on a degraded world + CompassHud gate | `dece6676` | `24295cb2` | **conflict** — see below |
| Create-screen authority over globe recognition | `4cefb253` | `13951047` | clean |
| Bonus chest anchored to dry land | `803678de` | `bc90ada0` | clean |
| On-demand terrain probe + subtropical land gate | `4b470287` | `e4d197e5` | clean |
| Land-cohesion gate scoped back to temperate | `1d0793ce` (partial) | `e4c10368` | **required correction** — see below |

Stream B — from this branch, chronological.

| Fix | Source | 26.2 commit | How it applied |
| --- | --- | --- | --- |
| ProtoChunk snow guard (cold columns keep vanilla snow) | `3b0b3432` | `0e7508e7` | clean |
| Create-world screen's second tab reads "Settings" | `e66429c2` | `1f855738` | clean |
| Loading overlay activates before vanilla's reload screen | `e93b9e4f` | `977ed591` | clean |
| `savanna_plateau` no longer overrides a low-Y sanitize | `b4f31e36` | `08f6f476` | clean |
| Compass HUD stops shifting with label length | `a00fe7cb` | `b1110e80` | clean |
| Provider-ticket capture for fresh dedicated worlds | `36e69a9e` | `3f437841` | **conflict** — see below |
| Third snow stripper + `seaLevel+27` windswept snow line | `8fa3d351` | `ea0b2fff` | clean |
| Decoration index covers ledger-admitted custom biomes | `58571da4` | `62dd261b` | clean |
| `/locate biome` finds custom biomes | `57895f64` | `b3c4668b` | **re-homed** — see below |

Added mid-flight, from `port/1.5.0-26.1.2`:

| Fix | Source | 26.2 commit | How it applied |
| --- | --- | --- | --- |
| Cancel no longer selects a climate | `09d20e10` | `8d0c0293` | **conflict** — see below. **maintainer-approved live 2026-08-09** |
| World Creation screen negative-space/alignment pass + tabbedMode title intro | `0cee3189` | `285ae887` | **conflict** — see below |

Both snow commits are present, so the pair that must travel together did.

### The world-creation-screen aesthetics pass — pulled forward on request

Landed on this thread (PR #12, merge `94eca269`) *after* the 26.2 backport branch was already in
flight with its own Cancel fix. Maintainer asked explicitly to pull `94eca269`'s UI work into the
26.2 branch. The merge carried three commits: `0cee3189` (the actual UI pass, touches only
`LatitudeCreateWorldScreen.java`) plus two repo-hygiene chores (`b06a2bb4` untrack `.windsurf/`,
`92b0c012` untrack `.agents/`+`.claude/`). **Only `0cee3189` was taken** — the hygiene chores are
out of scope for a UI backport and this thread's tree doesn't carry that debt.

`0cee3189`'s own commit message states it was built with the Cancel-bug fix already in place
("mouseClicked's zone-row/Cancel-button dispatch fix is untouched"), so cherry-picking it after
`09d20e10` on 26.2 reproduces the same stacking order it was authored against — confirmed clean,
no interaction between the two.

**Both conflicts were the render-pipeline rename boundary**, same family as everywhere else in this
backport: `GuiGraphics`→`GuiGraphicsExtractor`, `render`→`extractRenderState`. Verified via `javap`
against the 26.2 jar rather than assumed — `Screen.extractRenderState(GuiGraphicsExtractor,...)` is
confirmed as 26.2's actual override point, and `net.minecraft.client.input.KeyEvent` (the new
`keyPressed` override's parameter) is present on 26.2 unrenamed.

**Worth a note for whichever port thread reaches this next:** a screen's `render`/`GuiGraphics` is
part of the same 1.21.6+ render-pipeline reversal as the HUD's `Gui`/`Hud` split
(`diffs-learned-1.5.0+1.21.11.md` §3) — Screens are not exempt from it just because they're not a
`Gui` subclass.

### ⚠️ `0cee3189` itself ships a bug — found live on 26.2, applies here unchanged

Not a backport-translation defect: the code that has it applied to the 26.2 branch with **zero
conflict**, byte-identical to this thread's own source. The maintainer hit it within one screen-open
of the 26.2 branch: on the first `init()`, tabbedMode's title intro plays once, and when it ends the
Create World / Cancel button row is gone entirely — screenshot-confirmed.

Root cause: `applyIntroVisibility()` force-hides every interactive widget for the intro's duration.
Every OTHER widget it hides recovers because it has its own per-frame layout pass
(`updateLeftLayout`/`updateRightLayout`/`updateSettingsLayout`) that unconditionally reasserts its
own visibility every single frame — that's the mechanism the intro-hide relies on to be temporary.
`createWorldBtn`/`cancelBtn` are screen-level, not panel- or tab-scoped, so they have no such pass.
Once the one-shot hide sets them invisible, nothing on this line's code path ever sets them back.

Fixed on 26.2 as `bc6b1cf7`: `applyIntroVisibility()`'s early-return branch (taken every frame the
intro is inactive) now explicitly restores both buttons, mirroring what the per-frame layout passes
already do for everything else. **This same defect is live on `port/1.5.0-1.21.11` right now** —
nothing about the bug or the fix is 26.2-specific. Worth pulling `bc6b1cf7`'s fix back here.

### The create-screen Cancel bug — new 26.2 row, fixed and accepted

Not previously tracked as a backport candidate; it appears in the 26.2 live-flight kickoff only as a
diagnosed, unfixed item. The maintainer hit it while flying this branch: *"can't activate cancel on
the world creation screen; selects 'subtropical'."*

**Take `09d20e10`, never `3d836199`.** They are the pair `diffs-learned` §6 flags: `3d836199`
("Stop panel-scoped handlers swallowing bottom-row clicks") was a wrong turn, superseded four
minutes later by `09d20e10` ("Stop clipped climate rows stealing clicks from the button row").
Only the latter is the correct final state, and it is written as a *replacement* of the wrong turn's
hunk — so on any line that never had `3d836199` it conflicts, and the resolution is to take the
incoming side whole (the existing `mouseClicked` body becomes `globe$dispatchClick`).

**26.2 shows the wrong-selection symptom without the wrong turn.** On 26.1.2, `3d836199` is what
converted a dead Cancel into a wrong selection. 26.2 never had it and still selects a climate,
because 26.2's analogous guard is *panel-bounded*
(`isInsideSpawnPanel(x,y) && !isInsideSpawnClip(x,y)`) while the button row sits **beneath
`panelBottom`** — outside every panel, so the guard never fires, the click reaches vanilla dispatch,
and a zone row still holding its full rectangle over the button row outranks Cancel. Same root
cause, different route; the fix is correct either way. **Worth checking on the remaining port
targets rather than assuming the 26.1.2 symptom order.**

One comment clause was adjusted on 26.2: upstream states the bug "only reproduced" at GUI scale
5x+, which is a 26.1.2 observation and not what happened here.

`latitudeCreateScreenClipPolicyTest` / `ViewportClipPolicyTest` exercises
`ViewportClipPolicy.acceptsClippedWidgetClick`, which this fix does not touch — **a green suite is
not evidence this fix works.** Upstream added no test either. Verified by the maintainer's own click:
reported *"worked"*, and the client log shows the create screen opening and closing four seconds
later with no exception.

### ⚠️ Stream A's hashes as circulated were pre-rewrite and on no branch

The kickoff for the 26.2 backport listed Stream A as `68716f22` / `4660d45e` / `dde70c88` /
`533dd0e3` and asserted they were post-rewrite on-branch hashes. They are not — they resolve as
objects but `git merge-base --is-ancestor` places them on no branch. Mapped by
`git show <h> | git patch-id --stable` to their on-branch equivalents, every pair **IDENTICAL**:
`68716f22`→`4cefb253`, `4660d45e`→`803678de`, `dde70c88`→`4b470287`, `533dd0e3`→`dece6676`,
`89e9f07b`→`1d0793ce`, and (cited in diffs-learned) `a626c45c`→`3b0b3432`.

Nothing was lost, and cherry-picking an unreachable object would have been safe anyway — but
`diffs-learned-1.5.0+1.21.11.md` still cites the dead hashes throughout, unlike this tracker, which
was re-mapped. **That doc wants a fixup pass** before another thread reads hashes out of it.

### The required correction the kickoff list omitted

`4b470287` widens `isLandGateBand` to include `BAND_SUBTROPICAL`. On the 26.1.2 line that was
reverted 27 minutes later by `1d0793ce`, because routing warm highlands into
`LAT_TEMPERATE_MOUNTAIN` consumed the high columns `minecraft:eroded_badlands` needs and left the
fresh-world coverage plan reporting it unplaceable at `topologyEligible=0` — the trap
`diffs-learned` §6 flags. Harvesting the probe alone ships that regression, so `e4c10368` carries
the `LatitudeBiomes.java` half of `1d0793ce` only.

The **other** half of `1d0793ce` — the general structure-siting guard in
`ExtremePolarVillageStartGuardMixin` — was deliberately **not** taken: it is a feature rather than
a correction to anything in this backport, it is an open design item on the 26.2 live-flight
kickoff, and an over-strict structure guard silently empties the world. Still open on 26.2.

### Conflicts

**`dece6676`** — `CompassHud.java`, rename boundary: 26.1.2 `client.screen` vs 26.2
`client.gui.screen()`. Took the 26.2 identifier, kept the new `GlobeClientState.isGlobeWorld()`
gate.

**`36e69a9e`** — `GlobeMod.java`, the same rename-boundary conflict this fix produced on 26.1.2:
1.21.11's `server.getWorldData().worldGenOptions().seed()` vs 26.2's
`server.getWorldGenSettings().options().seed()`. Took the 26.2 form, kept the whole new capture
gating.

**`57895f64` — re-homed, exactly as on 26.1.2.** Upstream extracts the tag∪ledger union onto
`LatitudeDecorationRetrofit.allPaintableCustomBiomes`, a class created by the **BLOCKED** retrofit
feature `69132f9a`; taking the pick as-is would drag that feature's class in behind the block. The
union now lives in a new neutral `com.example.globe.world.LatitudePaintableCustomBiomes`, which
also owns the `lat_*` tag list formerly private to `ChunkGeneratorGenerateFeaturesBiomeSetMixin`.
**The 26.1.2 backport thread reached this same resolution independently**, so both lines are
shaped alike.

### A conflict git did NOT raise

`dece6676`'s `LatitudeWorldLauncher.java` hunk auto-merged **cleanly** with `client.setScreen(...)`,
which does not exist on 26.2 (`client.gui.setScreen(...)`). Only the compiler caught it. **A
conflict-free cherry-pick across a rename boundary is not evidence of correctness** — compile every
pick. Folded into its own commit with `commit --fixup` + `GIT_SEQUENCE_EDITOR=true git rebase
--autosquash`, so each commit on the branch builds standalone.

Related erratum: the 26.2 kickoff's rename table lists `ResourceKey.identifier()`→`.location()`.
That is **backwards** — `javap` on the 26.2 jar shows `identifier()` and no `location()`.

### `cf55480b` contributes nothing to 26.2 — verified, not assumed

- *Fog clobber*: 26.2's `FogRenderer.setupFog` **returns** `FogData`
  (`public FogData setupFog(Camera, int, DeltaTracker, float, ClientLevel)` per `javap`), and
  Latitude's only fog mixin is `FogRendererEwMixin` at `@Inject(method="setupFog", at=@At("RETURN"))`
  — after every vanilla write and before `updateBuffer`. The clobber cannot occur.
- *`RecreatedWorldMetadata` stale path*: 26.2's `SavedDataType` takes an `Identifier`
  (`globe:latitude_world_state`), so the on-disk path is
  `dimensions/minecraft/overworld/data/globe/latitude_world_state.dat` — exactly what
  `RecreatedWorldMetadata` reads and what `WorldgenAuthorityPolicyTest` asserts.
- *Retrofit gate*: moot, the feature is not on this line.

The one piece worth considering later is the hardening `cf55480b` added alongside the fix — making
the saved-data id a shared constant the reader derives from, so the pair cannot drift again. Not a
bug on 26.2; cheap insurance.

### Instrument note

`36e69a9e` was **live-verified on 26.2** during the same boot that settled the Disputed rows:
`Recorded Globe world: border radius 7500 (fresh dedicated/vanilla-created world)`. That lifts this
thread's recorded "honest limit" that ledger-routed biomes can never place on a headless world —
they can now, which is what makes the remaining decoration link headlessly testable at all.


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
