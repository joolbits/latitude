Latitude 1.3.0+1.20.1-r1 hotfix

Fixed:
- Corrected the packaged mixin refmap name for the 1.20.1 jar.
- Addresses startup crashes with `No refMap loaded` and `removed` target resolution errors.
- Set the packaged mixin compatibility level to Java 17 for 1.20.1 launcher profiles.
- Removed a release-jar reference to excluded debug stats that could crash during warm-band world generation.

Unchanged:
- No gameplay or worldgen behavior changes.
- Modrinth and CurseForge upload remains manual.
