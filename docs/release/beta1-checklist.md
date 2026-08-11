# Latitude 1.5.1 Beta 1 release checklist

Target identity:

- Mod version: `1.5.1-beta.1+1.21.11`
- Minecraft: `1.21.11`
- Runtime: Fabric, Java 21
- Planned annotated tag: `v1.5.1-beta.1+1.21.11`
- Runtime JAR: `latitude-1.5.1-beta.1+1.21.11.jar`
- Channel: prerelease/Beta

## Local candidate gates

- [x] Public repository hygiene removes the personal Windows shortcut, extracted Mojang data/language
  files, and the generated datapack ZIP from the candidate tree.
- [x] Project version and Fabric metadata agree on the Beta 1 identity.
- [x] Climate-title rollback and bounded retrofit regressions have focused automated coverage.
- [x] The release-artifact policy allows only the operator-confirmed bounded retrofit worker.
- [x] CI uses Java 21 and the Gradle 9.5.1 distribution has its official SHA-256 pinned.
- [ ] Final clean `build check` passes on the completed local candidate commit.
- [ ] The remapped runtime JAR passes content and provenance verification and has a recorded SHA-256.
- [ ] One independent final diff/proof review reports no unresolved release blocker.
- [ ] The exact verified JAR passes owner-run live acceptance.

## Publication gates

These are deliberately not authorized by local preparation:

- [ ] Public-history cleanup is separately approved and verified, if still required.
- [ ] CurseForge license metadata matches the repository's `GPL-3.0-or-later` license.
- [ ] The release branch/PR destination is explicitly approved before push.
- [ ] The annotated tag is confirmed absent and explicitly approved before creation or push.
- [ ] GitHub, Modrinth, and CurseForge drafts use the same verified runtime JAR bytes.
- [ ] The owner approves publication after exact-JAR live acceptance.

Do not push, tag, upload, alter host metadata, rewrite history, or publish from this checklist alone.
