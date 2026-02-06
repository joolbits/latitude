# Latitude — Backup Release Artifacts

## Goal
After building a release jar, back it up to:
C:\Users\jscho\CascadeProjects\Backups\<mod_version>\mc<minecraft_version>\

## How
Use Gradle task:
./gradlew backupJar

Override root:
./gradlew backupJar -PbackupDir="D:\Backups"

## Output
- Copies the primary jar (excludes -sources/-javadoc)
- Writes buildinfo.txt with version, mc, git hash, timestamp

## When to run
After successful build + test, and after commit/tag (preferred), run:
./gradlew backupJar
