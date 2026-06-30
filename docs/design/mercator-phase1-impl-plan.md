# Mercator World Type — Phase 1 Implementation Plan (canonical 26.1.2)

> ## ⚠️ IMPLEMENTATION NOTE (2026-06-23) — pivoted to NO-STRETCH; this plan's Step (d) was DROPPED
> Peetsa chose **"wider world, more biomes"** over the literal coordinate stretch. The coordinate
> transform (`effectiveX = blockX/2`, Step (d) below) would have shown the *same* biomes drawn 2× wide
> (a Mercator-projection look) — it does NOT add biomes per band. Sampling the biome map at **real X**
> over a 2× wider world is what yields ~2× more biomes per band, which is the actual goal. So:
> - **Step (d) coordinate transform: NOT implemented.** `pick()` is unchanged; `effectiveX` was removed.
>   A Mercator world produces the *same biome at any (X,Z)* as Classic — it is simply a bigger world.
> - **Step (g) ODF: trivially native** (nothing is transformed).
> - **Steps (a) UI/state, (c) X-radius accessor, (e) border+pole, (f) spawn: implemented as written.**
> - **NEW work the plan under-scoped — the latitude-denominator decoupling:** because the square border is
>   sized to the X axis (`2·X_RADIUS`), `util.LatitudeMath`'s Z-latitude functions (which divided by the
>   border half) would report half-latitude in Mercator. Fixed with a `latitudeZRadiusOverride` in
>   `util.LatitudeMath` (0 ⇒ use border half ⇒ Classic byte-identical), pushed by the server
>   (`GlobeMod.setGlobeBorder`) and synced to the client by adding `int latitudeZRadius` to
>   `GlobeNet.GlobeStatePayload`. Pole hazard uses a new `hazardProgressZ`; EW (X) hazard unchanged.
> Sections below are retained for the design record; read this banner first where they conflict.


> **Scope (LOCKED):** Phase 1 only — Mercator *horizontal stretch*, **no** continent classifier.
> `ASPECT = 2.0` fixed. World types are the pair **Classic** / **Mercator**. The world is 2:1
> (X half-extent = 2× Z half-extent). **Latitude stays a function of `|Z|` only.** Existing
> Classic/legacy worlds must be **byte-identical** (every Mercator code path is gated).
>
> **Governance:** Art II (canonical 26.1.2 first, port later). Art VI (no `floorDiv`/cell-hash *inside*
> biome placement; the X transform is applied UPSTREAM, feeding pre-transformed coords — never inside
> `ValueNoise2D`). Art X (no monoculture). Determinism + scale-invariance. Map-proof via headless atlas;
> **the identity check `ASPECT=1.0 ⇒ byte-identical to Classic` is the key safety gate.**
>
> **Why a single transform works (recon §3):** `blockX` is *never re-derived from a global* inside the
> selection path — it always arrives as a method parameter. The only non-parameter mention,
> `LatitudeBiomes.java:1461` `snapshot.blockX == blockX`, is a cache-key equality check, not a derivation.
> So one named local introduced at the top of `pick()` (shadowing the parameter) propagates transparently
> to every ~70 downstream helper call by value.

All paths under `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/`.

---

## 1. Resolved geometry — the concrete 2:1 mechanism

Minecraft's vanilla `WorldBorder` is **square only** (one `size`/`getSize()` diameter used for both axes;
`GlobeMod.setGlobeBorder` L300–304 calls `setCenter(0,0)` + `setSize(diameter)`). A literal rectangular
border is **impossible**. The design doc's "rectangular WorldBorder (width=X·2, height=Z·2)"
(`revamped-world-shape.md:54`) is therefore **not implementable** and is explicitly rejected.

**FINAL DECISION — Option A: square border sized to the WIDER (X) axis.**

Define two radii (Classic: both equal; Mercator: X = 2× Z):

```
Z_RADIUS = the latitude/pole authority (unchanged value from the preset/persisted radius)
X_RADIUS = (worldShape == MERCATOR) ? round(Z_RADIUS * ASPECT) : Z_RADIUS     // ASPECT = 2.0
```

Then:

1. **Square WorldBorder diameter = `2 * X_RADIUS`** (= `4 * Z_RADIUS` in Mercator). The vanilla
   square border's edge lands at `|X| = X_RADIUS` and `|Z| = X_RADIUS`. This is the only hard physical stop.

2. **E-W "storm wall" — UNCHANGED MECHANISM, fires at the true east-west edge automatically.**
   The E-W warning/fog/storm subsystem keys off `border.getSize()*0.5` (= `X_RADIUS` after step 1):
   - `GlobeClientState.distanceToEwBorderBlocks(border, camX)` = `getSize()*0.5 - |camX|`
     (`client/GlobeClientState.java:245-248`)
   - `computeEwStormStage` → `hazardProgress(border, getX)` + `LatitudeMath.hazardStageIndexEW(progress)`
     (`GlobeClientState.java:239-242`; `util/LatitudeMath.java:106-112`)
   Because the border half-size is now `X_RADIUS`, all E-W distances stay correct with **zero code change**
   to the EW renderers (`EwStormWallRenderer`, `EwSandstormOverlayRenderer/Hud`, `FogRendererEwMixin`,
   `WorldRendererWorldBorderMixin`). The vanilla border's own visuals remain suppressed by
   `WorldRendererWorldBorderMixin` (`DEBUG_EW_SUPPRESS_VANILLA_BORDER`, `GlobeClientState.java:13`).

3. **N-S beyond-pole zone `|Z| ∈ [Z_RADIUS, X_RADIUS]` — the ONE place math must change.**
   With the square border at `X_RADIUS`, the player can physically walk to `|Z| = X_RADIUS = 2·Z_RADIUS`,
   i.e. *past* the geographic pole. The pole hazard must therefore fire at `|Z| = Z_RADIUS`, not at the
   border edge. Today the pole path divides by the **border half-size** (`LatitudeMath.hazardProgress`
   = `|z|/half`, L88), which after step 1 only reaches 1.0 at `2·Z_RADIUS` — too far. **The single most
   important correction:** the pole/latitude denominator must be pinned to **`Z_RADIUS`**, not the border
   half-size. The existing graduated hazard ramp (`GlobeMod.borderUxTick` L323–386:
   IMPAIR→HOSTILE→WHITEOUT→LETHAL via `setTicksFrozen`) then becomes the de-facto N-S fence: the LETHAL
   band sits at `|Z| → Z_RADIUS`, well inside the square border, so **no new wall mechanism is required** —
   only re-denomination, plus (belt-and-braces) an optional hard turn-back at `|Z| ≥ Z_RADIUS`.

**Net:** one square border at `2·X_RADIUS`; E-W reuses existing UX verbatim; N-S reuses the existing pole
ramp but re-denominated to `Z_RADIUS`; latitude math stays `|Z|/Z_RADIUS` so poles trigger exactly at the
geographic pole. Both axes already have **independent** hazard subsystems (pole vs EW) — they diverge only
because they read `getZ()` vs `getX()`; Phase 1 simply gives them different denominators.

---

## 2. Ordered implementation steps (build never breaks between steps)

### Step (a) — `WorldShape` enum + `LatitudeWorldState.globe_shape` field (foundation)

**Why first:** everything downstream gates on the shape flag; adding it first (defaulting to CLASSIC,
ASPECT path dormant) is a no-op that compiles and is byte-identical.

**a1. New enum** — add to `world/LatitudeBiomes.java` near the worldgen-policy cache (`L376`):

```java
public enum GlobeShape { CLASSIC, MERCATOR }
private static volatile GlobeShape ACTIVE_GLOBE_SHAPE = GlobeShape.CLASSIC;
public static final double MERCATOR_ASPECT = 2.0;   // Phase 1 fixed
public static final int    MERCATOR_ASPECT_INT = 2; // integer floorDiv divisor

public static void setGlobeShape(GlobeShape s) { ACTIVE_GLOBE_SHAPE = (s == null) ? GlobeShape.CLASSIC : s; }
public static GlobeShape getGlobeShape() { return ACTIVE_GLOBE_SHAPE; }
public static boolean isMercator() { return ACTIVE_GLOBE_SHAPE == GlobeShape.MERCATOR; }

// String<->enum, null/blank-safe, default CLASSIC (mirrors WORLDGEN_POLICY_CODEC handling)
public static GlobeShape shapeFromString(String s) {
    if (s == null || s.isBlank()) return GlobeShape.CLASSIC;
    try { return GlobeShape.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT)); }
    catch (IllegalArgumentException e) { return GlobeShape.CLASSIC; }
}
public static String shapeToString(GlobeShape s) { return (s == null ? GlobeShape.CLASSIC : s).name().toLowerCase(java.util.Locale.ROOT); }
```

This mirrors the existing volatile-cache pattern: `ACTIVE_WORLDGEN_POLICY` (`L376`) +
`setWorldgenPolicy`/`getWorldgenPolicy`/`useLegacyWorldgenPolicy` (`L2136-2146`).

**a2. Persisted field** — `world/LatitudeWorldState.java`. The real codec is a `RecordCodecBuilder`
3-tuple (L26–34) with save key `Identifier.fromNamespaceAndPath("globe", "latitude_world_state")` (L24).
Add a 4th `optionalFieldOf` exactly mirroring `globe_radius` (L31):

```java
// field, next to L40 `private int globeRadius;`
private String globeShape;

// codec entry, appended inside instance.group(...) after the globe_radius entry (L31-32):
Codec.STRING.optionalFieldOf("globe_shape", "classic")
        .forGetter(LatitudeWorldState::getGlobeShape)

// .apply lambda (L33) -> 4 params:
(spawnPickerDismissed, worldgenPolicy, globeRadius, globeShape) ->
        new LatitudeWorldState(spawnPickerDismissed, worldgenPolicy, globeRadius, globeShape)

// private ctor (L46-50): add String globeShape param, store with default-guard:
this.globeShape = (globeShape == null || globeShape.isBlank()) ? "classic" : globeShape;

// public no-arg ctor (L42-44): pass "classic"
// getter/setter mirroring getGlobeRadius()/setGlobeRadius() (L88-98):
public String getGlobeShape() { return globeShape; }
public void setGlobeShape(String s) {
    String v = (s == null || s.isBlank()) ? "classic" : s;
    if (!v.equals(this.globeShape)) { this.globeShape = v; setDirty(); }
}
```

`optionalFieldOf("globe_shape", "classic")` means **every existing save deserializes to `"classic"`** — no
DataFixer, no `level.dat` change. Identical to how `globe_radius` was introduced.

**a3. WorldState → cache push** — mirror the worldgen-policy wiring. `LatitudeWorldState.get(world)` already
calls `ensureWorldgenPolicy(world)` (L58, L100-106) which pushes `LatitudeBiomes.setWorldgenPolicy(...)`
(L81,84,105). Add a sibling `ensureGlobeShape(world)` called from `get(world)` (L58) that pushes
`LatitudeBiomes.setGlobeShape(LatitudeBiomes.shapeFromString(state.getGlobeShape()))`.

> **After Step (a):** compiles; `ACTIVE_GLOBE_SHAPE` is always CLASSIC (no UI yet, default persists);
> byte-identical to today.

---

### Step (b) — UI toggle + launcher plumbing (cross-thread bridge)

**Why second:** lets the value flow UI→persist while still dormant in worldgen (shape consumed nowhere yet).

> **Doc correction (recon §UI):** the prompt says "CycleButton", but the **bespoke** create screen
> (`client/create/LatitudeCreateWorldScreen.java`) does **not** use `CycleButton` — World Size is a
> two-button **◀/▶ stepper** with a custom-rendered label. (A `CycleButton` for size exists only in the
> legacy/vanilla fallback `mixin/client/CreateWorldScreenSpawnZoneMixin.java:90-92`.) **Mirror the stepper**
> for visual consistency. Default value Peetsa locked is **`"classic"`** (not the doc's `"legacy"`).

**b1. Static cross-thread bridge** — `GlobePending.java`. Mirror `pendingGlobeRadius` (L15):
```java
public static volatile String pendingGlobeShape = "classic";
```
Required because `level.dat` does not carry the globe settings key (per MEMORY); the client UI thread hands
off to the server load thread through these volatiles.

**b2. UI state + stepper row** — `client/create/LatitudeCreateWorldScreen.java`:
- Add state field near `selectedSize` (L123): `private GlobeShapeChoice selectedShape = GlobeShapeChoice.CLASSIC;`
  (a small 2-value client enum, or reuse a String).
- Add a ◀/▶ stepper row **directly below the Size row** (matches design intent), mirroring the size
  stepper construction at L389-401 with a new `shapeFieldY = sizeFieldY + fieldGap2;`:
  ```java
  shapePrevBtn = Button.builder(Component.literal("◀"), b -> cycleShape(-1))
          .bounds(inputX, shapeFieldY, stepperBtnW, btnH).build();
  this.addRenderableWidget(shapePrevBtn);
  shapeNextBtn = Button.builder(Component.literal("▶"), b -> cycleShape(1))
          .bounds(inputX + inputW - stepperBtnW, shapeFieldY, stepperBtnW, btnH).build();
  this.addRenderableWidget(shapeNextBtn);
  ```
- Add `cycleShape(int delta)` mirroring `cycleSize` (L493-503): toggle the 2-value enum, then `rebuildWidgets()`.
- Header + flavor label: mirror `drawBoundedText(... "World Size" ...)` (L1099-1100) and
  `renderSizeLabel(...)` (L1219-1227) → a "World Shape" header + a `renderShapeLabel` showing
  "Classic / Mercator" + a 2–3 line description (Classic = square 1:1; Mercator = 2:1 horizontal stretch,
  wider biome bands).
- Replicate the four `sizeFieldY` layout/scroll/visibility call-sites for `shapeFieldY`:
  positioning L639-647, scroll baseline L667-689, tab visibility L832-833.
- Update the frozen tab-order comment (L366-372) to insert "5. Shape ◀ / 6. Shape ▶".

**b3. UI → launcher** — pass `this.selectedShape` into the launcher call at L915-918 (`beginExpedition(...)`).

**b4. Launcher** — `client/create/LatitudeWorldLauncher.java`:
- Add `GlobeShapeChoice globeShape` param to `beginExpedition` (signature L63-70).
- In the async world-create block, alongside L211-212, set the bridge:
  ```java
  GlobePending.pendingGlobeRadius = size.borderRadiusBlocks;
  GlobePending.pendingGlobeShape  = globeShape.name().toLowerCase(java.util.Locale.ROOT);
  GlobeWorldSizeSelection.set(size);
  ```
- Reset in the rollback block (L231-232): `GlobePending.pendingGlobeShape = "classic";`

**b5. Bridge → persist (consume-once)** — `GlobeMod.initLatitudeBiomesForWorld` (L256-262), mirror the
radius consume-once guard:
```java
String pendingShape = GlobePending.pendingGlobeShape;
GlobePending.pendingGlobeShape = "classic";                 // consume-once
if ("classic".equals(worldState.getGlobeShape()) && pendingShape != null
        && !pendingShape.isBlank() && world.getGameTime() < 100L) {
    worldState.setGlobeShape(pendingShape);                 // persist into LatitudeWorldState
}
// after radius/seed seeding, fill the live cache:
LatitudeBiomes.setGlobeShape(LatitudeBiomes.shapeFromString(worldState.getGlobeShape()));
```
The `"classic".equals(...)` + `getGameTime() < 100L` guard = "new world only, write once" — existing saves
keep their persisted (or defaulted-classic) shape.

> **After Step (b):** Mercator is selectable + persisted, but `isMercator()` is read nowhere in worldgen
> yet → still byte-identical regardless of selection. Safe to commit.

---

### Step (c) — radius split (`Z_RADIUS` / `X_RADIUS`) + accessor

**Why third:** introduce the X radius *before* anything consumes it; in Classic it equals Z so behavior
is unchanged. No transform yet.

**c1.** `world/LatitudeBiomes.java`. Keep `ACTIVE_RADIUS_BLOCKS` (L377) as the **Z authority** (the
latitude/pole authority — consumed as `|Z|/radius` at L2548, L209-210, L228, `ProvinceAuthority.java:155`,
`LatitudeMath`, `GlobeMod.java:316`). **Do NOT multiply it by ASPECT.** Add a sibling X radius:

```java
public static int getActiveRadiusBlocks() { return ACTIVE_RADIUS_BLOCKS; }   // existing L613 = Z_RADIUS
// NEW: X authority (border + spawn + EW). Derived, not separately stored.
public static int getActiveXRadiusBlocks() {
    int z = ACTIVE_RADIUS_BLOCKS;
    return isMercator() ? (int) Math.round(z * MERCATOR_ASPECT) : z;
}
```

Leave `setRadius`/`setActiveRadiusBlocks` (L603-611) as-is (they set the Z authority + call
`rebuildProvinceAuthority()`). `ProvinceAuthority` continues to cache the Z radius as `effectiveRadius`
(`ProvinceAuthority.java:113,138,155`) — correct, latitude is Z-only.

> **After Step (c):** `getActiveXRadiusBlocks() == getActiveRadiusBlocks()` in Classic; nothing reads the
> new accessor yet. Byte-identical.

---

### Step (d) — coordinate transform in the selection path (single upstream injection)

**Why fourth:** this is the core Art-VI-compliant move. It needs the shape flag (a) and is independent of
border/spawn (e/f). Done before border sizing so the biome map is already stretch-aware when the world
widens.

**The contract:** transform X **once, integer, upstream of every noise call**, by *shadowing* the parameter.
Never inside `ValueNoise2D`. `blockZ` is **never** touched.

**d1. Tiny pure helper** (place near `getActiveXRadiusBlocks`, used by all entry points):
```java
public static int effectiveX(int rawBlockX) {
    return isMercator() ? Math.floorDiv(rawBlockX, MERCATOR_ASPECT_INT) : rawBlockX;
}
```
`floorDiv` here is **upstream coordinate prep**, not in-placement cell-hash — it produces the pre-transformed
coordinate fed to noise, satisfying Art VI (the prohibition is on `floorDiv`/cell-hash *inside* the
selection decision, e.g. inside `ValueNoise2D`).

**d2. Registry core `pick` (L2521-3164).** Rename the parameter `int blockX` → `int rawBlockX` in the full
signature (L2521), then immediately after the `effectiveRadius` guard (after L2537, before
`clearSelectionState()` at L2538) introduce the shadowing local:
```java
final int blockX = effectiveX(rawBlockX);
```
Because all ~70 downstream sites take `blockX` **by value**, this single line covers every consumer:
`applyBoundaryJitter` (L2549), `latitudeBandIndexWithBlend` (L2562), `pickFromWeightedTags` (L2705,2714),
`pickTropicalGradient` (L2707), the 52 `ValueNoise2D.sampleBlocks(...blockX...)` calls, `surfaceDecisionY`
(L2524 — note: this is *above* the rename; see d4), `previewTerrain`/`previewHeight`, `isMountainLike`,
`oceanDistanceBlocks` (L2610 — see Step (g), ODF stays native, so it must receive **rawBlockX**),
`enforceLandBandPool` (L2934), the gate family (L3089-3095), and all `debugPick`/log/trace sites (which
should receive the transformed `blockX` for trace consistency).

> **Art VI note:** keep `blockZ` as the parameter name untouched. The transform must remain X-only.

**d3. Collection-mirror `pick` (L3170-3746).** Apply the identical rename + shadow at the matching point
(after the Collection body's `effectiveRadius` guard). This body is the atlas/source/headless parity path;
it must transform identically or the identity check (ASPECT=1.0) and the 2× atlas will diverge from live.

**d4. The two pre-transform consumers above the shadow line.** `surfaceDecisionY(...blockX,blockZ)` (L2524)
and the `RADIUS MISMATCH` log run *before* the shadow. Decide per-call:
- **`surfaceDecisionY` / terrain probes** sample the **real** generated terrain (which is in real block
  space — Phase 1 does not stretch terrain). They must use **`rawBlockX`**. Since the parameter is renamed
  to `rawBlockX`, L2524 already passes the raw value correctly — **leave as-is** (no edit needed there).
- This is the deliberate split: **biome-map/province/band noise uses `effectiveX` (stretched); real-terrain
  height probes and the vanilla climate sampler use `rawBlockX` (native).** See Step (g) for the same
  principle applied to ODF and the climate read at L887-889.

**d5. Standalone public spatial entries (also reachable OUTSIDE `pick`).** These are called by
`ProvinceAuthority`, atlas tooling, and `/latdev`, so the `pick`-local shadow does not cover them. Apply
`effectiveX(...)` at each, gated by `isMercator()`:
- `authoritativeLandBandIndex(blockX, blockZ, ...)` (`LatitudeBiomes.java:202`) and
  `authoritativeChosenBandIndex` (`:221`) — called from `ProvinceAuthority.classify`
  (`ProvinceAuthority.java:113,138`).
- `classifyProvince(blockX, blockZ)` (`LatitudeBiomes.java:724`).
  Inside each, transform X at the top: `blockX = effectiveX(blockX);` (rename param to `rawBlockX` +
  shadow, same pattern) so worldgen, atlas, and diagnostics all agree.
- **NOT** `oceanDistanceBlocks` (`L740`) → see Step (g): ODF stays native.

> **After Step (d):** in Classic `effectiveX` is identity → byte-identical. In Mercator the biome/province
> map is 2× wider in X. Border is still square-at-Z (from c), so Mercator worlds are *narrower than
> intended* until Step (e) — but they compile and are internally consistent. Atlas can already prove the
> 2× band widening here.

---

### Step (e) — world border sizing (to `2 * X_RADIUS`) + pole denominator pin

**Why fifth:** widen the physical world to match the now-stretched biome map, and pin the pole hazard to
`Z_RADIUS`.

**e1. `GlobeMod.setGlobeBorder` (L299-321).** Size the square border to the wider axis and pin pole start
to `Z_RADIUS`:
```java
private static void setGlobeBorder(ServerLevel overworld, int borderRadiusBlocks) {
    WorldBorder border = overworld.getWorldBorder();
    int zRadius = borderRadiusBlocks;                                  // latitude authority (Z)
    int xRadius = LatitudeBiomes.isMercator()
            ? (int) Math.round(zRadius * LatitudeBiomes.MERCATOR_ASPECT)
            : zRadius;                                                 // border/EW authority (X)
    double diameter = xRadius * 2.0;                                   // square border = 2 * X_RADIUS
    border.setCenter(0.0, 0.0);
    border.setSize(diameter);

    // Z authority pushed to LatitudeBiomes (UNCHANGED — latitude must stay |Z|/Z_RADIUS):
    LatitudeBiomes.setRadius(zRadius);
    LatitudeBiomes.setActiveRadiusBlocks(zRadius);
    // ... existing log lines, but log both zRadius and xRadius ...

    // POLE START PINNED TO Z_RADIUS (was: activeRadius from border) — critical correction:
    activePoleBandStartAbsZ = (int) Math.round(zRadius * com.example.globe.util.LatitudeMath.POLAR_START_FRAC);
    POLAR_SCRUBBER = ENABLE_POLAR_SCRUBBER ? new PolarCapScrubber(zRadius, activePoleBandStartAbsZ) : null;
}
```
**Key change vs today (L306-316):** previously `activeRadius` was derived from `border.getSize()/2` and used
for *both* the LatitudeBiomes radius AND `activePoleBandStartAbsZ`. Now the LatitudeBiomes radius and pole
start are driven by **`zRadius`**, while the border diameter uses **`xRadius`**. In Classic
(`xRadius == zRadius`) this is byte-identical to the old L306-316.

**e2. `LatitudeMath` pole/latitude denominators.** Today `hazardProgress(border, z)` = `|z|/halfSize(border)`
(L88) and `hazardStageIndex` (L95) read `halfSize(border)` (L42 = `getSize()*0.5`). After (e1) the border
half is `X_RADIUS`, so `|z|/halfSize` only reaches 1.0 at `2·Z_RADIUS` — wrong. The pole/latitude family
must divide `z` by **`Z_RADIUS`**, while the EW family keeps dividing `x` by **`halfSize(border)` (=
X_RADIUS)**. Minimal-risk approach: add Z-radius-parameterized overloads and have the **pole** callers pass
`Z_RADIUS` while **EW** callers keep using the border:
```java
// NEW overloads (keep existing border-based ones for EW):
public static double hazardProgressZ(int zRadius, double z)       { return Math.min(1.0, Math.abs(z) / (double) zRadius); }
public static int    hazardStageIndexZ(int zRadius, double z)     { return stageIndexForProgress(hazardProgressZ(zRadius, z)); }
```
Pole-side latitude helpers that read the border (`latNormFromZ` L50, `degreesFromZ` L57, `zoneForRadius`
L159, `zForLatitudeDeg` L120, `poleRemaining*` L60-85) should be sourced from **`Z_RADIUS`**
(`LatitudeBiomes.getActiveRadiusBlocks()`), not `halfSize(border)`. The EW path
(`hazardStageIndexEW` L106, `GlobeClientState` EW chain) keeps `halfSize(border)` and needs **no change**.

**e3. `GlobeMod.borderUxTick` (L323-386).** The Z-gated pole ramp must fire at `|Z| = Z_RADIUS`. Switch its
progress source from the border-based `hazardProgress(border, getZ)` / `hazardStageIndex(border, z, ...)`
to the Z-radius overloads:
```java
int zRadius = LatitudeBiomes.getActiveRadiusBlocks();
double progressZ = LatitudeMath.hazardProgressZ(zRadius, player.getZ());
int stageIndex   = LatitudeMath.hazardStageIndexZ(zRadius, player.getZ());
// gate unchanged: if (Math.abs(player.getZ()) < activePoleBandStartAbsZ) continue;  (L349)
```
Optional belt-and-braces hard stop: at `Math.abs(player.getZ()) >= zRadius`, nudge the player back inside
(teleport `z` to `±(zRadius - 1)`). Not strictly required because LETHAL/freeze already makes the beyond-pole
margin impassable, but it guarantees no walk-through. The square border at `2·X_RADIUS` is the outer fence;
the LETHAL band at `Z_RADIUS` is the effective N-S fence.

> **After Step (e):** Mercator worlds are physically 2:1, EW wall fires at `|X|=X_RADIUS` (free, via
> existing UX), poles fire at `|Z|=Z_RADIUS`. Classic byte-identical (`xRadius==zRadius`, pole denom ==
> border half).

---

### Step (f) — spawn finder (decouple X bound from Z/latitude bound)

**Why sixth:** spawn must search the wider X extent while keeping latitude targeting on `Z_RADIUS`.

> **Doc correction (recon §SPAWN):** the doc says "search an ellipse." The **actual** code is a square
> X-strip (`x ∈ [-max,max]`, `z ≈ targetZ`). The real, minimal fix is **decoupling the X bound (→X_RADIUS)
> from the Z/latitude bound (→Z_RADIUS)** — NOT introducing ellipse geometry.

`GlobeMod.java`. `findLandSpawn` (L643) is reached via `trySetInitialLatitudeSpawn` (L455) →
`resolveSpawnChoice` (L546) and is also reused by `applySpawnChoice` (L516, join-teleport L532), so both
spawn paths are fixed together.

**f1. `resolveSpawnChoice` (L558-595).** `radius` (L558) is **Z_RADIUS** — keep it for the latitude/Z math
at L567 (`z = round(radius * spawnAbsLatFrac)`) and the pole clamp L572-574 (`warnStartZ`, `maxAbsZ`). These
stay in Z and are correct as-is. Add an X bound and use it for the finder + EW clamp:
```java
int zRadius = LatitudeBiomes.getActiveRadiusBlocks();      // existing `radius` at L558
int xRadius = LatitudeBiomes.getActiveXRadiusBlocks();     // NEW
...
// L583 finder call gains the X bound:
BlockPos spawnPos = findLandSpawn(world, template, sampler, xRadius, zRadius, targetZ, seed);
// L594 EW clamp MUST use X_RADIUS, else every spawn is pulled back to ~Z_RADIUS-564:
spawnPos = clampSpawnAwayFromEwWarning(spawnPos, xRadius);
```

**f2. `findLandSpawn` (L643-679).** Split the single `borderHalf`/`max` into X and Z bounds:
```java
private static BlockPos findLandSpawn(ServerLevel world, SamplerTemplate template, Climate.Sampler sampler,
                                      int xRadius, int zRadius, int targetZ, long seed) {
    final int margin = 320;
    final int maxX = Math.max(0, xRadius - margin);   // X drawn over the WIDER extent (L663)
    final int maxZ = Math.max(0, zRadius - margin);   // Z-jitter clamp on the latitude extent (L666)
    ...
    int x = rng.nextIntBetweenInclusive(-maxX, maxX);          // was -max..max (L663)
    // pass 1 z jitter clamp:
    z = Mth.clamp(targetZ + zJitter, -maxZ, maxZ);             // was clamp to max (L666)
    ...
    // isLandBiome (L668) radius arg MUST stay Z_RADIUS so pick()'s t = |blockZ|/effectiveRadius is correct:
    isLandBiome(template, sampler, x, z, classifyY, zRadius);
}
```
`isLandBiome` (L685) passes **raw `blockX`** into `pick` (L694) — leave it; `pick` applies the Mercator
de-stretch (Step d) the same way it does for worldgen, keeping spawn and terrain consistent. `placeSafeY`
(L708) needs no change. `setWorldSeed(seed)` (L657) unaffected.

**f3. `clampSpawnAwayFromEwWarning` (L628-641).** `safeMaxAbsX` (L634) must be computed from **X_RADIUS**:
```java
int safeMaxAbsX = Math.max(0, xRadiusBlocks - EW_WARNING_DISTANCE_BLOCKS - EW_SPAWN_PADDING_BLOCKS);
```
(rename the param to `xRadiusBlocks` for clarity; EW constants L75-76 unchanged).

> **After Step (f):** X sampled over `[-(X_RADIUS-320), +(X_RADIUS-320)]` (2× wider in Mercator), EW clamp
> respects X_RADIUS, latitude targeting + pole clamp + the radius fed to `pick` stay on Z_RADIUS. Classic
> byte-identical (`xRadius==zRadius`).

---

### Step (g) — ODF handling: **LEAVE NATIVE** (no transform in Phase 1)

**Decision (recon §ODF, definitive): do NOT transform ODF coordinates in Phase 1.** Call
`OceanDistanceField` with **raw `blockX, blockZ`**.

**Why:** ODF measures *physical* block-space ocean distance derived from the **vanilla continentalness**
sampler at real block coords (`OceanDistanceField.isOceanCell` L97-106, `oceanDistanceBlocks` L34-54;
`CELL_SIZE=256`, `OCEAN_LIKE_THRESHOLD=-0.19`, `MAX_SEARCH_CELLS=64`). In Phase 1 there is **no continent
classifier** — land/ocean shape and the real terrain are whatever vanilla continentalness produces in real
block space, identical to Classic. The climate sampler is **also not transformed** (the climate read at
`LatitudeBiomes.java:887-889` uses raw `blockX >> 2`; `LatitudeBiomeSource.getNoiseBiome` hands `pick` real
`blockX`). The coastal gates that consume ODF are physical-distance gates that must align with the *actual*
coastline the player walks:
- mangrove `MANGROVE_COASTAL_MAX_BLOCKS=384` (L2379), subtropical swamp `=192` (L2250),
  pale-garden inland min `=384` (L2191), polar/flat shelf `<= 64` (L4703,4865,5973),
  deep-water `oceanAuthority = oceanDistance == 0` (L2612), mushroom `isGenuineOpenOcean → <= 0` (L4086-4090).

Transforming ODF would halve every X-axis distance → mangroves firing at 768 real blocks of E-W coast,
`oceanAuthority`/mushroom `==0` keyed to a cell grid that no longer aligns with the real continentalness
coastline → **regression**. The design doc's "ODF receives transformed coords" (§5.1, R2, §2.1) is correct
**only for Phase 2** (continent classifier injected into `isOceanCell`, evaluated in stretched/geographic
space). Phase 1 excludes the classifier (locked), so that premise does not hold.

**Concrete requirement in code:** at the `pick` ODF call (`LatitudeBiomes.java:2610` Registry, `:3254`
Collection), the call must use **`rawBlockX`**, not the shadowed `blockX`. Because Step (d) shadows
`blockX = effectiveX(rawBlockX)`, the ODF call sites are the **one place** that must explicitly pass
`rawBlockX`:
```java
// at L2610 / L3254, and the LatitudeBiomes.oceanDistanceBlocks wrapper (L740-752):
int oceanDistance = oceanDistanceBlocks(rawBlockX, blockZ, sampler);
```
Do **not** add `effectiveX` to `LatitudeBiomes.oceanDistanceBlocks` (L740) or
`OceanDistanceField.oceanDistanceBlocks` (L34). No BFS/cell-size rescale (the 16384-block horizon already
dwarfs the 384-block max gate).

> **After Step (g):** coastal gates fire at real-space distances (unchanged on the X axis) against the real
> (unchanged) coast — exactly right. Provinces are 2× wider in X but sit against the same real coast (the
> intended "more biomes per band" outcome).

---

## 3. Call-site sweep checklist (design-doc R1)

Run from repo root. The goal: confirm every `blockX` consumer in the selection path is covered by the
shadow, and that the **deliberate exceptions** (terrain probes + ODF + climate sampler) explicitly use
`rawBlockX`.

```bash
ROOT=<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2
F=$ROOT/src/main/java/com/example/globe/world/LatitudeBiomes.java

# 1. Count blockX refs in each pick body (recon baseline: Registry 93, Collection 95).
grep -n 'blockX' "$F" | awk -F: '$1>=2521 && $1<=3164' | wc -l    # expect ~93
grep -n 'blockX' "$F" | awk -F: '$1>=3170 && $1<=3746' | wc -l    # expect ~95

# 2. After the edit: confirm the shadow exists in BOTH bodies and the param renamed to rawBlockX.
grep -n 'final int blockX = effectiveX(rawBlockX);' "$F"          # expect 2 hits (Registry + Collection)
grep -n 'int rawBlockX' "$F"                                       # expect the 2 full pick signatures + 3 standalone entries

# 3. The DELIBERATE rawBlockX exceptions must remain raw (must NOT be the shadowed blockX):
grep -n 'surfaceDecisionY' "$F"                                    # L2524 — terrain probe, rawBlockX
grep -n 'oceanDistanceBlocks(' "$F"                                # L2610/L3254 callers must pass rawBlockX; wrapper L740 native
grep -n 'sampler.sample' "$F" | sed -n '1,5p'                      # L887-889 climate read — rawBlockX (>>2)

# 4. Standalone public spatial entries also reachable outside pick() — must apply effectiveX:
grep -n 'authoritativeLandBandIndex' "$F"                          # L202 def
grep -n 'authoritativeChosenBandIndex' "$F"                        # L221 def
grep -n 'classifyProvince' "$F"                                    # L724 def
# verify ProvinceAuthority/atlas callers route through the transformed entries:
grep -n 'authoritativeLandBandIndex\|authoritativeChosenBandIndex\|classifyProvince' \
    "$ROOT/src/main/java/com/example/globe/world/ProvinceAuthority.java"   # L113,138

# 5. Art VI guard — confirm NO new floorDiv/cell-hash got introduced INSIDE placement/noise.
#    (effectiveX's floorDiv is the ONLY allowed one and it is upstream of all noise.)
grep -n 'floorDiv' "$F"                                            # review each hit; new ones only inside effectiveX()
grep -n 'ValueNoise2D' "$F" | wc -l                                # 52 calls — none should wrap blockX in floorDiv inline

# 6. External callers of pick() (verify they still pass RAW world coords; pick does the transform):
grep -rn 'LatitudeBiomes.pick(' "$ROOT/src/main/java/com/example/globe/"  # mixin L307/L351, LatitudeBiomeSource L113/L117, GlobeMod L694

# 7. Radius split — every |blockZ|/radius latitude site must read Z_RADIUS (getActiveRadiusBlocks), not X.
grep -n 'getActiveRadiusBlocks\|getActiveXRadiusBlocks\|ACTIVE_RADIUS_BLOCKS' "$F"
grep -rn 'halfSize(border)' "$ROOT/src/main/java/com/example/globe/util/LatitudeMath.java"  # pole-side must move to Z_RADIUS; EW keeps border

# 8. Shape flag is read in worldgen exactly where intended (pick transform + radius accessor + border + spawn).
grep -rn 'isMercator()\|getGlobeShape()\|ACTIVE_GLOBE_SHAPE' "$ROOT/src/main/java/com/example/globe/"
```

**Manual review list — the spatial helpers the stretch actually affects (must resolve to transformed
`blockX` via the shadow):** `applyBoundaryJitter` (2549), `latitudeBandIndexWithBlend` (2562),
`pickFromWeightedTags` (2705,2714), `pickTropicalGradient` (2707), `wetlandNoiseSymmetric` (2684),
`swampPatchHere` (2682), `isAridTropicalStepSymmetric` (2681), `paleGardenRegionHit` (2738), dark-forest
density `ValueNoise2D` (2739), snowy ramp (2971), `enforceLandBandPool` (2934), `gateDryWarmIdentity`
(3091), the gate/enforce family (3089-3095), `previewTerrain`/`previewHeight` (2574,2618,2758,2892),
`isMountainLike` (2565,2586,2899,3127), plus all `debugPick`/log/trace sites.

---

## 4. Verification plan

**4.1 Compile.**
```bash
cd <home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew compileJava --console=plain
```

**4.2 Identity gate — ASPECT=1.0 ⇒ byte-identical to Classic (KEY SAFETY GATE).**
Two equivalent checks; run both:
- **Classic-shape atlas == pre-change atlas.** With the world in CLASSIC shape, `effectiveX` is identity,
  `getActiveXRadiusBlocks()==getActiveRadiusBlocks()`, border math reduces to today's. Render a headless
  atlas at a fixed seed and diff against a baseline rendered on the parent commit:
  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew runBiomePreview --console=plain   # CLASSIC seed, baseline
  # ... apply changes, re-run ...
  ```
  Diff the two PNG/atlas outputs — must be **byte-identical**.
- **Mercator-with-ASPECT=1.0 == Classic.** Temporarily set `MERCATOR_ASPECT=1.0`/`MERCATOR_ASPECT_INT=1`
  (or a test toggle), render a Mercator atlas, diff vs the Classic atlas → must be byte-identical. This
  proves the transform plumbing itself is a pure no-op at unit aspect (catches off-by-one `floorDiv`
  asymmetry). Restore `2.0` after.

**4.3 ASPECT=2.0 behavior atlas.** Render a Mercator atlas (ASPECT=2.0) at the same seed:
- **Expect each latitude band to be ~2× wider in X** (province cells stretched horizontally; band *heights*
  in Z unchanged — latitude is Z-only).
- **Expect more distinct biomes visible per band** (a band twice as long in X exposes more of the band pool
  — the intended "more representation per band" outcome).
- Pole boundary in Z must be at the **same** `|Z|` as Classic (latitude unchanged): verify the polar band
  starts at the same Z row in both atlases.

**4.4 Art X (no monoculture).** On the ASPECT=2.0 atlas, run the per-band share metrics and confirm no band
collapses to a single biome (the X-stretch must not create an X-monoculture). Reuse:
```bash
python3 tools/atlas/perbiome_sublat.py     # per-sublatitude per-biome shares
python3 tools/atlas/sublatitude_dry_wet.py # dry/wet balance per band
```
Confirm equator stays humid-dominant with rare coherent arid (the v1.4 "Cohesive Horizons" invariant),
unchanged from Classic.

**4.5 Art VI (no floorDiv/cell-hash in placement).** Sweep #5 above. The only `floorDiv` touching X for
biome placement is `effectiveX`, applied **once, upstream of all noise** — confirm no inline
`floorDiv`/cell-hash was introduced inside any `ValueNoise2D`-adjacent expression.

**4.6 Policy scripts.**
```bash
python3 tools/check-biome-tuning-policy.py        # biome tuning policy gate
bash    tools/check_tree_line_port.sh             # only if tree-line code is touched — it is NOT in Phase 1, run as a no-regression sanity
```
Tree line / alpine are untouched by Phase 1, so `check_tree_line_port.sh` should report no change; run it as
a guard that the radius split didn't perturb the snow/tree-line `|Z|/radius` reads.

**4.7 Live (Modrinth App only, HARD RULE).** Per MEMORY, terrain/spawn/border are only truly verifiable in
a running client, and **only via the Modrinth App** (`Lat 1.4+26.1.2` staging profile) — never the Mojang
launcher. Manual eyeball after the atlas gates pass:
- Create a **Mercator** world: confirm 2:1 face, EW storm wall at the east-west edge, pole hazard ramp at
  the geographic pole (not the far border), spawn lands on land within the wider X strip at the right
  latitude.
- Create a **Classic** world: confirm visually unchanged.
- Load a **pre-existing save**: confirm it opens as Classic, byte-identical, no border resize.

---

## 5. Biome-mod test expansion (PARALLEL test-infra task — NOT blocking the code)

> Flagged separately. The Mercator code does not depend on any of these; this broadens worldgen injection
> coverage for the atlas (more custom biomes per band makes the 2× widening visually obvious).

**How the test runtime wires biome mods (recon §BIOME-MOD):** they are **loose jars** in `run/mods/`,
copied to `run-headless/mods/` by the Gradle `Copy` task `syncBiomePreviewMods` (`build.gradle:274-289`).
Workflow: drop jar in `run/mods/`, run `./gradlew runBiomePreview -Platdev.syncPreviewMods=true`. Both dirs
are gitignored (`.gitignore:6,47`). A gradle-managed alternative is available — the
`maven.modrinth`/`cursemaven` repos are already configured (`build.gradle:100-110`) so
`modRuntimeOnly "maven.modrinth:<slug>:<version-id>"` lines can be added to the deps block
(`build.gradle:112-127`).

**Already installed (26.1.2):** BiomesOPlenty `26.1.2.0.2`, Terralith `2.6.2`, Promenade `5.5.0`
(+ TerraBlender, GlitchCore, Lithostitched deps).

**Shortlist — 5 mods spanning different injection paths, all confirmed 26.1.2 (verified live against
Modrinth API):**

| # | Mod | Slug | Version ID | Kind / why | Deps |
|---|---|---|---|---|---|
| 1 | KS Biomes | `ks-biomes` | `PQifwC6D` | small pack, 6 biomes, **zero deps** | none |
| 2 | Improved/More Biomes | `improved-more-biomes` | `qVMI4XMC` | light pack, datapack-style registration | none |
| 3 | Geophilic | `geophilic` | `jDzSPLta` | vanilla-biome **overhaul** (no new IDs → tests tags on vanilla keys) | none |
| 4 | Wilder Wild | `wilder-wild` | `QluBtU4i` | big overhaul + new biomes, exercises **FrozenLib** injection lib | FrozenLib (`9KawNmQc`), Fabric API |
| 5 | Tectonic | `tectonic` | `SlA5rCvn` | terrain + a few biomes, exercises **Lithostitched** (already a dep) | Lithostitched 1.7.2 (present) |

Optional 6th (Biolith injection-path coverage): **Terrestria** `8.0.0-alpha.1` (version `HqkksHnv`, embeds
Biolith) — alpha channel only.

**Gradle-managed lines (add to `build.gradle:112-127`):**
```gradle
modRuntimeOnly "maven.modrinth:ks-biomes:PQifwC6D"
modRuntimeOnly "maven.modrinth:improved-more-biomes:qVMI4XMC"
modRuntimeOnly "maven.modrinth:geophilic:jDzSPLta"
modRuntimeOnly "maven.modrinth:wilder-wild:QluBtU4i"
modRuntimeOnly "maven.modrinth:frozenlib:9KawNmQc"
modRuntimeOnly "maven.modrinth:tectonic:SlA5rCvn"
```
(Note: the project hasn't used `modRuntimeOnly` before; loom is present but this needs the loom
`modRuntimeOnly` config wired. The project's *actual practice* is the loose-jar method below.)

**Loose-jar method (matches existing setup, recommended):** download into
`<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/run/mods/`:
```
https://cdn.modrinth.com/data/PSZVP497/versions/PQifwC6D/ks-biomes-26.1.jar
https://cdn.modrinth.com/data/IZgBWgkV/versions/qVMI4XMC/improved-more-biomes-1.7.jar
https://cdn.modrinth.com/data/hl5OLM95/versions/jDzSPLta/Geophilic%20v3.6.mod.jar
https://cdn.modrinth.com/data/AtHRJSUW/versions/QluBtU4i/WilderWild-4.2.9-mc26.1.jar   (+ FrozenLib jar)
https://cdn.modrinth.com/data/lWDHr9jE/versions/SlA5rCvn/tectonic-3.0.25-fabric-26.1.jar
```
For the Modrinth App profile, drop in `~/Library/Application Support/ModrinthApp/profiles/Lat 1.4+26.1.2/mods/`.

**Do NOT attempt (no 26.1.2 build):** BYG (`biomesyougo`, stalled at 1.19.4), Regions Unexplored
(max 1.21.1), Nature's Spirit (max 1.21.1), Biome Makeover, William Wythers/WWOO.

---

## 6. Risks & gotchas

- **R1 — missed `blockX` consumer (the headline risk).** ~188 textual `blockX` refs across the two pick
  bodies (~70 distinct sites each). Mitigation: the **shadow** (`final int blockX = effectiveX(rawBlockX);`
  after the `effectiveRadius` guard) covers all by-value consumers in one line per body; the sweep in §3
  proves it. **The trap is the reverse** — the deliberate `rawBlockX` exceptions (terrain probes
  `surfaceDecisionY` L2524, ODF callers L2610/L3254, climate read L887-889) must NOT accidentally pick up
  the shadowed `blockX`. The identity gate (§4.2) and the ASPECT=1.0-as-Mercator check catch this class.

- **R2 — ODF / climate / terrain consistency.** ODF, the vanilla climate sampler, and the real terrain are
  all **native** in Phase 1; only the province/band biome map is stretched. If ODF were transformed,
  coastal gates would fire ~2× too far inland on the E-W axis and `oceanAuthority`/mushroom `==0` would
  desync from the real coast — a regression. Hard rule: ODF stays native (Step g). This is a Phase-2
  coupling, not Phase 1.

- **R3 — spawn zone drift.** If `clampSpawnAwayFromEwWarning` keeps using `Z_RADIUS` (the old `radius`),
  every Mercator spawn is yanked back to ~`Z_RADIUS-564`, defeating the wider face and biasing spawns
  toward the meridian. Must use **X_RADIUS** (Step f3). Conversely, the latitude target and the radius fed
  to `pick` must stay **Z_RADIUS** or spawn latitude diverges from terrain latitude.

- **R4 — pole denominator (the doc's biggest error).** The design doc claims `activePoleBandStartAbsZ`
  "stays unchanged." It does NOT: once the square border is sized to `X_RADIUS`, `|z|/halfSize` no longer
  reaches 1.0 at the pole, so the pole start AND the hazard progress denominator must move to **`Z_RADIUS`**
  (Steps e1/e2/e3). Without this, poles are unreachable / hazard never escalates in Mercator.

- **R5 — border visual mismatch.** The square border at `2·X_RADIUS` leaves a beyond-pole N-S margin
  (`|Z| ∈ [Z_RADIUS, X_RADIUS]`) inside the physical border. This is intentional: the LETHAL pole band is
  the de-facto N-S fence. The vanilla border ring visual is already suppressed
  (`WorldRendererWorldBorderMixin`), so players see the storm/pole UX, not a square outline at the wrong
  place. If suppression is ever disabled, the square outline would appear at the X edge on all four sides —
  acceptable but note it.

- **R6 — existing-save safety (Art II / legacy-pin).** `optionalFieldOf("globe_shape","classic")` +
  the `"classic".equals(...)` / `getGameTime()<100L` consume-once guard guarantee existing saves load as
  Classic with no `level.dat` change and no border resize. The radius split is value-preserving in Classic
  (`X_RADIUS==Z_RADIUS`). The identity atlas gate (§4.2) is the machine proof. Do NOT retro-apply Mercator
  to existing saves.

- **R7 — cached samplers assuming square.** ODF has no persistent cross-call cache that assumes square
  (`distanceCache`/`oceanFlagCache` are keyed by real cell coords and stay native). `ProvinceAuthority`
  caches `effectiveRadius` = Z_RADIUS (correct). `RandomState`/`Climate.Sampler` are vanilla and native.
  No square-assuming cache survives the change.

- **R8 — atlas-vs-live parity (Collection mirror).** The Collection-body `pick` (L3170-3746) is the
  atlas/headless path. If its shadow is omitted or placed at a different point than the Registry body, the
  atlas will not match live worldgen and the identity gate gives a false pass/fail. Both bodies must apply
  the identical rename+shadow at the analogous point (Step d3).

- **R9 — `MERCATOR_ASPECT_INT` integer division asymmetry.** `Math.floorDiv(rawBlockX, 2)` is asymmetric
  about 0 by design (floor, not truncate) — `floorDiv(-1,2) == -1`, matching the existing `floorDiv`
  convention used elsewhere (e.g. ODF L39, `isLandBiome` L689-690). Do not substitute `/2` (truncation
  toward 0) — that would break determinism symmetry across the meridian. The ASPECT=1.0 identity check
  (`floorDiv(x,1)==x`) confirms the unit case; eyeball a few negative-X atlas columns for the 2.0 case.

- **R10 — UI tab order / scroll plumbing.** The bespoke create screen freezes tab order and references
  `sizeFieldY` in four places (L639-647, L667-689, L832-833) plus the comment (L366-372). A new
  `shapeFieldY` row that misses any of those four call-sites will render misaligned or skip in
  tab-navigation. Replicate all four. Mirror the **◀/▶ stepper**, not a `CycleButton` (doc error).
