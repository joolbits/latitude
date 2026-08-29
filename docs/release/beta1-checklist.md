# Latitude 1.5.1 Beta 3 release checklist

Target identity:

- Mod version: `1.5.1-beta.3+1.21.11`
- Minecraft: `1.21.11`
- Runtime: Fabric, Java 21
- Planned annotated tag: `v1.5.1-beta.3+1.21.11`
- Runtime JAR: `latitude-1.5.1-beta.3+1.21.11.jar`
- Channel: prerelease/Beta

## Local candidate gates

- [x] Public repository hygiene removes the personal Windows shortcut, extracted Mojang data/language
  files, and the generated datapack ZIP from the candidate tree.
- [x] Project version and Fabric metadata agree on the Beta 3 identity.
- [x] Climate-title rollback and bounded retrofit regressions have focused automated coverage.
- [x] The release-artifact policy allows only the operator-confirmed bounded retrofit worker.
- [x] CI uses Java 21 and the Gradle 9.5.1 distribution has its official SHA-256 pinned.
- [x] The public beta testing guide and structured bug-report form pass link, schema, and
  public-content privacy validation. `.github/ISSUE_TEMPLATE/bug_report.yml` is well-formed GitHub
  issue-form YAML with a required privacy-check box; `docs/testing-beta.md` carries its own evidence
  and privacy section; the stale `joolbits` guide/report links in the Beta 1 release text were
  corrected to `peetsamods`.
- [x] README, changelog, Beta 1 release text, and known limitations agree with the final accepted spawn,
  structure-locate, and provider behavior. Checked against every commit of this candidate from `dcf7830ec` onward (nearest-match locate, locate coverage gaps and clickable teleport,
  TerraBlender/badlands surface fix, subpolar-only windswept, and the desert/badlands/savanna/forest
  rebalance) and updated to match; this is a docs-vs-commits check, not a live-tested acceptance of
  that behavior — that remains the unticked live-acceptance gate below.
- [x] Final clean `build check` passes on the completed local candidate commit. Each of the seventeen
  commits on this candidate (the range beginning at `dcf7830ec`, ending at whatever commit the
  release tag is cut from -- deliberately not pinned here, because naming a HEAD in a file that
  is itself committed invalidates the claim on the next commit) records its own
  `gradlew check green` verification in its commit message, 14 PASS markers each, matching that
  commit's own pre-change baseline; no independent re-run was performed in this pass (a headless
  world-gen run was in progress elsewhere and gradle was off-limits here).
- [ ] The remapped runtime JAR passes content and provenance verification and has a recorded SHA-256.
- [ ] One independent final diff/proof review reports no unresolved release blocker.
- [ ] The exact verified JAR passes owner-run live acceptance.

## Publication gates

These are deliberately not authorized by local preparation:

- [ ] **Public-history cleanup — REQUIRED, not optional.** The 2026-08-26 audit found extracted
  vanilla Mojang assets (`assets/minecraft/lang/en_us.json`, three `world_preset` files) in 740
  of this branch's 809 commits, from the root commit onward. The tip tree is clean, so working-
  tree checks report clean; a push transmits reachable history. **This branch must not be
  pushed, PR'd, or tagged on GitHub.** Both public refs were verified uncontaminated, so there
  is no existing exposure. Supported route: content-replay the candidate-only commits onto
  `origin/codex/release/1.5.1-beta.1-1.21.11` (verified clean by path scan and blob-size sweep).
  The S-8 pre-push hook (`tools/hooks/pre-push`) refuses the bad push mechanically — it is a
  backstop, not a substitute for this gate.
- [ ] CurseForge license metadata matches the repository's `GPL-3.0-or-later` license.
- [ ] The release branch/PR destination is explicitly approved before push.
- [ ] The annotated tag is confirmed absent and explicitly approved before creation or push.
- [ ] GitHub, Modrinth, and CurseForge drafts use the same verified runtime JAR bytes.
- [ ] The owner approves publication after exact-JAR live acceptance.

Do not push, tag, upload, alter host metadata, rewrite history, or publish from this checklist alone.
