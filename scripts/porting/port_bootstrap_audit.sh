#!/usr/bin/env bash
set -euo pipefail

# Read-only bootstrap audit for Latitude ports.
# The only file this script writes is the compact audit report in docs/porting.

expected_root="<checkout>"
repo_root="$(git rev-parse --show-toplevel 2>/dev/null || true)"

if [[ "$repo_root" != "$expected_root" ]]; then
  printf 'Canonical root mismatch.\nExpected: %s\nActual:   %s\n' "$expected_root" "${repo_root:-<not a git repo>}" >&2
  exit 1
fi

report_file="$repo_root/docs/porting/latest-port-bootstrap-audit.txt"
mkdir -p "$(dirname "$report_file")"

# Mirror the audit to stdout and to the requested report file.
exec > >(tee "$report_file") 2>&1

show_section() {
  printf '\n== %s ==\n' "$1"
}

show_section "Repo State"
printf 'Root:  %s\n' "$repo_root"
printf 'Branch: '
git branch --show-current
printf 'HEAD:  '
git rev-parse HEAD
printf 'Status:\n'
git status -sb
printf 'Tags at HEAD:\n'
git tag --points-at HEAD || true

show_section "Dependency / Mapping Scout"
rg -n --no-heading \
  '^(minecraft_version|yarn_mappings|loader_version|fabric_api_version|mod_version|maven_group|archives_base_name|fabric_version|tectonic_version|lithostitched_version|modmenu_version|placeholder_api_version|cloth_config_version|org\.gradle\.java\.home)\s*=' \
  gradle.properties || true
rg -n --no-heading 'version\s*=|id\s+"fabric-loom"|id\s+"maven-publish"|dependencies\s*\{' build.gradle || true
rg -n --no-heading '"version"\s*:|"entrypoints"\s*:|"mixins"\s*:|"depends"\s*:' src/main/resources/fabric.mod.json || true

show_section "Mixin Manifest Entries"
python3 - <<'PY'
import json
from pathlib import Path

path = Path("src/main/resources/globe.mixins.json")
data = json.loads(path.read_text())
for key in ("mixins", "client"):
    print(f"{key}:")
    for item in data.get(key, []):
        print(f"  - {item}")
PY

show_section "Known Port-Risk Symbols"
rg -n --no-heading --hidden --glob '!**/build/**' \
  'populateBiomes|fillBiomesFromNoise|doCreateBiomes|LevelLoadingScreen|DownloadingTerrainScreen|CreateWorldScreen|BackgroundRenderer|ProcessBuilder|Runtime\.exec|Desktop\.getDesktop|modLocalRuntime|pale_garden|Equator' \
  src/main/java src/main/resources build.gradle gradle.properties || true

show_section "Process-Launch APIs"
rg -n --no-heading --hidden --glob '!**/build/**' \
  'ProcessBuilder|Runtime\.exec|Desktop\.getDesktop' \
  src/main/java src/main/resources build.gradle gradle.properties || true

show_section "Dev / Tool / Headless Packages"
find src/main/java -type d \( -path '*/dev' -o -path '*/tool*' -o -path '*/headless*' \) | sort || true

show_section "Next Action"
printf 'Work the first failing gate in order: dependency/mapping -> mixins -> UI hooks -> live biome proof -> timing -> /latdev -> artifact purity -> release gate.\n'
printf 'Build success is not release proof; fresh runtime proof always wins over Atlas or direct preview evidence.\n'
