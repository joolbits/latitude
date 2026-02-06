---
description: Latitude release checklist
---
1) Verify code/tests/build locally
   - ./gradlew clean build

2) Tag + push
   - git status (ensure clean)
   - git add -A && git commit -m "<message>"
   - git tag <tag>
   - git push && git push origin <tag>

3) Backup release artifact
   - ./gradlew backupJar
   - (optional) override dest root: ./gradlew backupJar -PbackupDir="D:\Backups"

4) Smoke test in client/server as needed

5) Publish/upload as per release channel
