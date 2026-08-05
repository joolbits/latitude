# Port Kickoff — Latitude 1.5.0 → Minecraft 1.21.1 (thread 3 of 4)

Read `docs/porting/LATITUDE_1_5_BACKPORT_CAMPAIGN.md` first. Largest modern audience of the
campaign (29.4k mods on 1.21.1). Starts AFTER the 1.21.11 port ships — most of that port's client
diffs replay here.

## Working card
- **Objective**: `1.5.0+1.21.1` beta. Root: worktree
  `<home>/CascadeProjects/Latitude-port-1.5-1.21.1`, branch `port/1.5.0-1.21.1` (cut from
  `v1.5.0+26.2`). Mojmap spike per campaign; fallback Yarn `1.21.1+build.3`.
- Toolchain: Java 21, loader 0.17.3, fabric-api 0.115.x+1.21.1, Loom ~1.14.10.

## Deltas BEYOND the 1.21.11 port (1.21.1 < 1.21.6, so these APIs vanish)
- `MouseButtonEvent`/`KeyEvent` → primitive-arg signatures (`mouseClicked(double,double,int)`,
  `keyPressed(int,int,int)`) in both big screens + HUD Studio.
- `client.renderer.fog` package gone → `FogRendererEwMixin` retargets to the older fog path (the
  old 1.21.1 port DELETED most fog plumbing rather than adapt it — decide adapt-vs-simplify
  deliberately, record which).
- `KeyMapping.Category.register(Identifier)` → String category; `BlockTintSources` gone
  (`GlobeModClient` promenade tint compat — guard or drop; Promenade has no 1.21.1 build anyway,
  absent-safe).
- Create-world internals drift vs 1.21.11 — harvest the old branch's 2,514-line Yarn-idiom stack
  (`fd7360b5`) and the ordering rule (`c7fe597a`): init-redirect at `init()` HEAD preempts
  toggle/spawn-zone mixins.
- `LevelLoadingScreen` moved package + refactored on this line (`render`/`getPercentage`/`removed`)
  — the old port left the overlay BLOCKED here; 1.5's port must land it (fail-soft per #7 rule if
  the target proves unstable).
- Networking records/`StreamCodec`: fine (1.20.5+). SavedData NBT form: same as 1.21.11's rewrite.

## STRETCH SLICE (GitHub #3 — only after the core port is fully green)
Lithosphere compatibility. Harvest `compat/1.21.1-lithosphere-runtimebase` @ `621ac4eb` (tag
`save/1.21.1-lithosphere-biome-source-compat-runtimebase`): the biome-source side lets Lithosphere
keep terrain ownership while Latitude wraps banding. Remaining: carry the same gated `mr_lithosphere`
acceptance into `ChunkGeneratorPopulateBiomesMixin`, then the bounded headless proof. **Stop
condition (pre-agreed): no valid true-ocean control set → re-defer, and update issue #3 + the
Modrinth description's "in active development" line honestly.** Do not let this slice delay the
release; it can ship as `1.5.1+1.21.1` later.

## Live lane
Fresh profile `Lat 1.5 - 1.21.1 - TEST`; download 1.21.1 provider jars (BoP, Terralith; CliffTree's
1.21.1 backport needs its companion mod — see old Stage-4 note). Dedicated-server smoke per
campaign. **After release: reply on GitHub #7** — the crash's root cause was a 1.20.1 jar on 1.21.1;
a matching 1.21.1 file now exists. Connector/NeoForge remains unsupported; say so.

## Definition of done
Gates green → the maintainer's acceptance → `1.5.0+1.21.1` beta → tag → binder → #7 and #3 updated →
"diffs learned" appended to `kickoff-1.5-1.20.1.md`.

## Diffs learned from thread 2 (1.21.11)
_(appended by the 1.21.11 thread at closeout)_
