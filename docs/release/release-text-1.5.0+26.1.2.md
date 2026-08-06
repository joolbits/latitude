# Latitude 1.5.0+26.1.2 — release text

Drafts for publication. Nothing here is authorized for upload; Maintainer decides channel and timing.
Adapted from `release-text-1.5.0.md` (the 26.2 release text) per the campaign method's Phase-8 step:
re-frame "before you update" for this target rather than reuse the 26.2 wording verbatim.

Artifact: `latitude-1.5.0+26.1.2.jar` · SHA-256 to be filled in from the actual release build (current
`TEST 6` build SHA is `c73a7cc2e9ebec36847dd0d7218260b3e8e374794852206bd87a241ae72b62f4`, but the real
release build should be freshly built and re-hashed at Phase 8, not assumed identical to a TEST jar).
Minecraft 26.1.2 · Fabric · Java 25 · requires Fabric API

---

## Modrinth / CurseForge version description

### Latitude 1.5.0 — for Minecraft 26.1.2

**The first Latitude release on the mod platforms for the 26.1.2 line.** Latitude 1.3 was published on
Minecraft 1.21.11; Latitude 1.4 shipped on 26.1.2 but was only ever tagged on GitHub, never published
to Modrinth or CurseForge. This build brings the 1.4 "Cohesive Horizons" worldgen and custom-biome
overhaul to the platforms for the first time, together with the entire 1.5 polish campaign on top of it.

If you played 1.4 from GitHub, you already have most of this — 1.5 is the polish pass, not a rebuild.
If you're coming from 1.3, this will not feel like a point release.

#### Before you update

**If you're already on Latitude 1.4 (26.1.2), this is a same-Minecraft-version upgrade** — 1.4 and 1.5
both target 26.1.2, so there is no save-format conversion to cross going from one to the other.

**If you're coming from an older line (1.3 on 1.21.11, or earlier),** you're changing Minecraft
versions, not just Latitude versions — back up your world before updating, the way you would for any
Minecraft version jump.

[Sections below reused near-verbatim from the 26.2 draft, since the underlying worldgen/climate model,
custom-biome support, poles, create-screen, HUD, operator commands, and known limitation are the same
1.5 feature set — only the version-crossing framing above and the artifact/version lines differ. Copy
forward from `release-text-1.5.0.md` at actual Phase-8 writing time rather than duplicating it here now,
so the two drafts don't drift out of sync while both are still moving.]

#### Help us catch biome anomalies

If you notice something that looks wrong for its surroundings — a flat biome like plains generating on
unusually steep or rocky terrain, an expected feature like a mushroom island not appearing where the
biome says it should, a biome that seems out of place for its latitude, or anything else that looks
like a placement bug rather than natural variety — please report it with your seed, coordinates, and
which packs you have installed.

---

## GitHub release body

**Latitude 1.5.0 (Minecraft 26.1.2)**

First platform release on the 26.1.2 line. Brings the previously GitHub-only 1.4 "Cohesive Horizons"
line to Modrinth/CurseForge for the first time, plus the full 1.5 polish campaign.

[Highlights list: reuse `release-text-1.5.0.md`'s GitHub release body highlights verbatim — same 1.5
feature set, no 26.1.2-specific behavior differences to call out beyond the target version itself.]

Full notes: see `CHANGELOG.md`.

---

## Suggested tag

```
v1.5.0+26.1.2
```

Matches the existing convention (`v1.4.0+26.1.2`, `v1.3.0+1.21.11`, `v1.5.0+26.2`).

---

## GitHub issue #8 follow-up (held per the maintainer's instruction, 2026-08-06)

Post-release, once this artifact is live: post the dedicated-server smoke result to issue #8 with a
link to this release. Draft already prepared and shown to Maintainer this change; not posted yet.
