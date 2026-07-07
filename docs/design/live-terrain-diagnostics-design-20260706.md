# Live Terrain Diagnostics — Design (2026-07-06)

`status: DESIGN ONLY — not implemented.` Authored by the Fable 5 overhaul audit (Lane 10, per
`../binder/fable5-overhaul-audit-kickoff-20260706.md`), under Peetsa's explicit decision that this pass
designs but does not build. Written to be implementation-ready: an authorized follow-up session should be
able to build it from this document without re-deriving the approach. Every hook point below was verified
against HEAD `d87537f8` by the audit's code-mapping lane (full map with line quotes:
the audit scratchpad's `lane-10-tooling-map.md`; key facts restated inline so this doc stands alone).

## 1. Problem

Strength tuning and live verification of the Phase 4 terrain wrapper currently run on eyeballed
screenshots and two-launch comparisons. Concretely, during the 2026-07-06 live pass:

- There is NO live way to see `GeoAuthority.land01`, the applied density bias, or the wrapper's install
  state at the player's position — everything requires a headless run and reading a JSON file.
- Seed-0 worlds leave GeoAuthority permanently inert with zero user-visible signal; a live test round was
  lost to exactly this (LESSONS L19 context), and the log's `INSTALLED` line is indistinguishable from
  the pre-fix broken state.
- The measured "detached ceiling slab" failure mode (audit threshold sweeps: slab at Y256–319 appears
  between strength 0.09 and 0.10, all seeds tested) is invisible until a player happens to fly under one.
- Comparing "what would this column look like at strength 0" requires a second game launch, because the
  V2 flags are read once at class-init (`core/LatitudeV2Flags.java:63-72`, all `static final`).

## 2. What exists to build on (verified hook points)

- **Command trees.** Dev-only tree `dev/LatitudeDevCommand.java` (`register()`, lines ~66-139, 19
  subcommands incl. `here`/`explainHere`; reflectively invoked from `GlobeMod.java:212`, gated on
  `FabricLoader.isDevelopmentEnvironment()`), plus the shippable 5-subcommand subset
  `LatitudeDevCommands.registerIfEnabled()`. Both are server-side `CommandSourceStack` trees; output via
  `sendSuccess`/`sendFailure` with a `[latdev]` chat prefix. Neither tree uses `.requires()` gating.
- **Live router access precedent.** `here`/`explainHere` already fetch
  `world.getChunkSource().randomState()` from a running command — one step short of
  `.router().finalDensity()`.
- **Wrapper detection.** No persisted install-state exists; detection must be structural:
  `randomState.router().finalDensity() instanceof GeoTerrainBiasFunction`. (`RandomStateAccessor` mixin
  exists for mutation; plain read access suffices here.)
- **Point sampling technique.** `dev/TerrainProofHarness.java` samples density via a bare 3-int
  `FunctionContext` record (`SimpleCtx`, line ~678) passed to `finalDensity().compute(ctx)`, and takes
  ground truth from `generator.getBaseHeight(...)` (line ~527). Direct `compute()` on the
  RandomState-level graph bypasses per-chunk cache/interpolation nodes; the Phase 4 mechanical proof's
  direct-sample legs established value-equivalence for point probes, and the harness is the accepted
  precedent. One caveat inherited knowingly: chunk-blended edge cases may differ slightly from final
  in-world blocks; this is a diagnostic, not a proof gate.
- **Geography access.** `LatitudeBiomes.geoProviderForTerrain()` (public accessor, field at
  `LatitudeBiomes.java:618`) → `GeoAuthority.sample(int x, int z) → GeoSummary` → `land01()`. Realness
  check idiom: `provider instanceof GeoAuthorityProvider` (as in `GeoTerrainBiasFunction.compute()`
  L142-145).
- **Bias arithmetic is recoverable without a second evaluation.** `compute()` is
  `wrapped = base + S*K*gain*sd` with `sd = signum(d) * smoothstep(|d|)`, `d = 2*land01 - 1`
  (`GeoTerrainBiasFunction.java:167-181`, `K=0.25` private at `:69`). Since `base` is just
  `wrapped − bias`, a command can report the strength-0 baseline from ONE live sample — provided it
  replicates the two short-circuits (`S == 0` and `!(provider instanceof GeoAuthorityProvider)` both mean
  `wrapped == base`, bias 0).
- **One-shot log idiom.** `AlpineSurfaceMixin`'s `AtomicBoolean.compareAndSet(false, true)` CAS latch —
  the codebase's standard "log once then stop" pattern, reused by `TerrainRouterWrapping`'s latches.

## 3. Design A — `/latdev terrainHere` (the centerpiece)

**Registration.** New subcommand `terrainHere` in the dev tree (`LatitudeDevCommand.register()`),
mirroring `explainHere`'s wiring (player position + `ServerLevel` from the command context). Promotion
into the shippable `LatitudeDevCommands` subset is a separate later decision (recommended after first
live validation — it is exactly a tester-facing diagnostic).

**Report layout** (chat, `[latdev]` prefix, ~10 lines, values rounded to 4 decimals; exact fields):

```
[latdev] terrain @ (x, z)  world=<dimension> seed=<worldSeed>
[latdev] gate: wrapper=INSTALLED|NOT-INSTALLED(<why>)  terrainV2=<on/off> S=<S> r=<r> geoV2=<on/off>
[latdev] provider: <SimpleClassName> real=<yes/no>  providerSeed=<seed|n/a> match=<yes/NO — STALE?>
[latdev] geography: land01=<v>  sd=<v>  bias=<S*K*gain*sd> (density units)
[latdev] density @Y={py-16, py-8, py, py+8, py+16}: wrapped=<...>  baseline(S=0)=<...>
[latdev] ground: baseHeight=<generator.getBaseHeight>  (heightmap WORLD_SURFACE_WG)
[latdev] gradient≈<density/block near ground>  predicted shift≈<bias/gradient> blocks
[latdev] ceiling scan Y∈[200,319] step 8: minDensity=<v> @Y=<y>  margin=<|minDensity|>
[latdev] flip check: margin <vs> |bias|  →  OK | WARNING: within <n>x of slab flip
[latdev] (seed-0 world: GeoAuthority permanently inert — geography fields are NoOp)   ← only when true
```

Field semantics and sources:
- **gate**: structural instanceof detection (above); `<why>` from re-evaluating the same conditions
  `TerrainRouterWrapping.installIfArmed` checks (flag off / not globe / error), since `InstallResult` is
  not persisted.
- **provider realness + seed match**: requires ONE new accessor — `GeoAuthority` carries its seed
  (`GeoAuthority.java:38-40`) but exposes no getter. Add `long seed()` (or `describe()` string). This
  same getter is what the stale-provider audit finding (refute-A) needs for any future world-identity
  check, so it pulls double duty. `match=NO` prints a loud `STALE?` marker — that single line would have
  made both the install-timing bug and the stale-provider hazard visible in one glance.
- **density ladder + baseline**: 5 point samples via the harness's `SimpleCtx` technique against the
  world's ACTUAL installed router (unlike the harness, which builds its own `RandomState` — sampling the
  installed one is the point: it sees real state, including staleness). Baseline column =
  `wrapped − bias` with the two short-circuits replicated.
- **gradient / predicted shift**: finite difference of wrapped density across the ladder around
  `baseHeight`; predicted ground shift = `bias / gradient`. This is the number that replaces "does 0.05
  look different from 0.07?" eyeballing. (Audit measurements: real gradients vary 10-100x by terrain
  class, so a per-column prediction is exactly what a flat global rule can't give.)
- **ceiling scan / flip check**: 15 samples Y=200..319 step 8; `margin = min |density|` over the scan.
  The measured slab bracket (bias 0.0225→0.025 flipping Y256-319 solid on every tested seed) means:
  warn when `|bias| ≥ margin` (slab already forming — say so plainly) and when `|bias| ≥ margin/2`
  (within 2x). This makes the audit's headline failure mode visible BEFORE a player flies into a slab.
- **seed-0 notice**: when `worldSeed == 0` (or provider is NoOp with geoV2 on), print the inert-world
  line. This is the user-visible signal the audit found missing everywhere (converged finding).

**Cost/threading**: runs on the server thread inside the command; ~21 `compute()` point samples + 1
`GeoAuthority.sample()` — microseconds-scale, one-shot, no allocation concerns. No NoiseChunk needed.

**Failure behavior**: every branch prints a definite line (no silent returns): non-globe world, flags
off, provider NoOp, wrapper not installed, exception (print the exception class + first frame — do NOT
adopt compute()'s silent-catch pattern; the audit flagged that pattern as a P1 observability hole).

**Implementation shape** (for the follow-up session): 1 new subcommand method in
`LatitudeDevCommand` (~120 LOC incl. formatting); 1 new public static pure helper
`GeoTerrainBiasFunction.biasFor(double land01)` returning `S*K*gain*sd` so the command and `compute()`
share one formula (no drift; K stays private); 1 new `GeoAuthority.seed()` getter; zero mixin changes;
zero behavior changes to worldgen. Proof gate: mechanical — command output vs a same-seed
`terrainProof` JSON on the same column must agree on land01/densities/baseHeight to 1e-9.

## 4. Design B — harness caller-specified probes (closes the instrument gaps the audit hit)

`-Dlatdev.terrainProof.probes=x1,z1;x2,z2;...` (parsed in `TerrainProofHarness`): adds caller columns to
`columnProbes` AND `structuralProbes` alongside the auto-selected three. Plus: extend the fixed density
Y-ladder (`{40..100}`, line ~506) upward to `{...,100,140,180,220,260,300,319}` — the audit's flip
altitude was invisible precisely because sampling stopped at Y=100 while the slab lives at Y256-319.
Multi-strength stays one-JVM-per-strength (flags are `static final` by design; the external ladder loop
is already the established pattern) — document, don't fight it.

## 5. Adjacent one-line observability fixes (bundle with the implementation, per the fix-then-test-once workflow)

Named by the audit as converged findings; listed here so the implementing session bundles them (each is
~1-3 lines, all use the existing CAS-latch idiom):
1. One-shot log in `GeoTerrainBiasFunction.compute()`'s catch block (currently 100% silent; also compute
   `base` once rather than re-invoking the delegate in the catch).
2. One-shot "GeoAuthority remains inert (seed-0/zero-radius world)" log at `rebuildGeoAuthority()`'s
   decline path (`LatitudeBiomes.java:628`).
3. Distinct log line when the wrapper is INSTALLED but the provider is still NoOp at first compute
   (disambiguates "working", "not yet", and "never will" — today all three read identically).

These are fixes, not diagnostics — final authority on doing them lives with the audit report's
Strategic Path Forward, but the design records where they belong so the follow-up doesn't re-derive them.

## 6. Explicitly out of scope

- Any implementation in this pass (Peetsa's design-only decision).
- Client-side rendering/HUD overlays (chat readout only; the HUD is a separate system the audit found
  structurally blind to terrainV2 — do not couple them here).
- A Y-taper for the bias formula itself — that is the audit's headline PREREQUISITE finding for Phase 4
  calibration and gets its own slice; this diagnostic is how that slice's effect will be measured live.
