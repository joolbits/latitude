# Port Kickoff — Latitude 1.5.0 → Minecraft 1.20.1 (thread 4 of 4, hardest last)

Read `docs/porting/LATITUDE_1_5_BACKPORT_CAMPAIGN.md` first. The long-tail king (33.3k mods).
Starts after 1.21.1 ships, carrying both prior ports' diffs.

## Working card
- **Objective**: `1.5.0+1.20.1` beta. Root: worktree
  `<home>/CascadeProjects/Latitude-port-1.5-1.20.1`, branch `port/1.5.0-1.20.1` (cut from
  `v1.5.0+26.2`). Mojmap spike per campaign (Loom supports mojmap on 1.20.1); fallback Yarn
  `1.20.1+build.10`.
- Toolchain: **Java 17** (`options.release = 17`, `sourceCompatibility 17`), mixin
  `compatibilityLevel: JAVA_17`, loader 0.16.14, fabric-api 0.92.x+1.20.1, Loom ~1.9.2, older
  Gradle wrapper (match the old branch). Gradle itself may still run on a newer JDK
  (`org.gradle.java.home` pattern from the old branch — never commit a machine path).
- Java-language audit already done: **nothing above 17 in use anywhere.** No de-sugaring.
- **Model/effort**: Opus, high (escalate for the networking rewrite if the port-1.20.1 corpus doesn't cover it). See the campaign doc's model section; state your path at each slice.

## Deltas BEYOND the 1.21.1 port
- **Networking rewrite (the one genuinely new chunk):** `GlobeNet`'s 3 `CustomPacketPayload`
  records + `StreamCodec.composite` (1.20.5+) → 1.20.1's
  `ServerPlayNetworking.send(player, Identifier, PacketByteBuf)` style, both send and receive
  sides (`GlobeMod`, `GlobeModClient`). Small surface (1 file each side) but a real API-generation
  rewrite.
- **Refmap regime:** `globe.mixins.json` must name `latitude-refmap.json` (commit `764887d5` — one
  line that otherwise costs a runtime debugging cycle). Verify the refmap embeds in the jar
  (~12 KB, ~23 classes on the old line).
- `ChunkStatus` package: `world.level.chunk.status.ChunkStatus` (1.20.2+) →
  `world.level.chunk.ChunkStatus`.
- Resources: `data/globe/tags/block/polar_foliage.json` → `tags/blocks/` (the tree's ONLY
  per-version resource path rename); worldgen JSON needs the ~118-line
  `preliminary_surface_level` patch (`find_top_surface` doesn't exist on 1.20.1) — **already
  written** on `port/1.4.0-beta-1.20.1`'s `noise_settings/*.json`; transplant it.
- `ConventionalBiomeTags` v2 (1.20.5+) → v1 module (`ChunkGeneratorPopulateBiomesMixin`,
  `LatitudeBiomeSource`).
- `DataComponents`/`BundleContents` (1.20.5+) → 1.20.1 NBT-based bundle inspection or drop the
  compass-in-bundle nicety (decide, record).
- `BiomeSourceAccessor.codec()`: returns `Codec` here, `MapCodec` on newer — adjust the invoker.
- Old-line warnings to treat as real: the 1.20.1 build gate warned
  `InGameHudMixin`/`ExistingWorldLoadingOverlayStartMixin` target-miss — with the #7 fail-soft
  rule, every overlay/HUD mixin here gets an explicit apply-proof (`require` honest, not silenced).

## Harvest pointers (richest of all targets)
`port/1.4.0-beta-1.20.1` worktree: the `docs/porting/port-1.20.1-*.txt` corpus (~25 one-file
API-delta notes — read the matching note BEFORE hand-solving any RED: registry/Identifier helpers,
PersistentState form, BiomeSource codec shape, create-world flow, HUD widgets, loading-screen text
path); bootstrap diff `169fbda9` (30 files) for the full two-generation drop.

## Live lane
Fresh profile `Lat 1.5 - 1.20.1 - TEST`; download 1.20.1 provider jars (BoP 1.20.1 line, Terralith
1.20.x, TerraBlender/GlitchCore equivalents). Dedicated-server smoke per campaign. This is the
version most exposed to GitHub #7's wrong-file trap — the strict `>=1.20.1 <1.20.2` fence and the
release notes' version table absence make Modrinth's own version filter the guard; state the
matching-file requirement in the release notes.

## Definition of done
Gates green → the maintainer's acceptance → `1.5.0+1.20.1` beta → tag → binder → campaign completion pass
(all four betas live; archive `port/1.4.0-beta-*` branches; final binder row closing the campaign).

## Diffs learned from thread 3 (1.21.1)
_(appended by the 1.21.1 thread at closeout)_
