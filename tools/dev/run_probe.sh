#!/usr/bin/env bash
# Turnkey wrapper for the generic headless function probe (BiomePreviewHeadlessRunner's
# `latdev.probe` mode). Boots a real globe world headlessly, reflectively sweeps a static
# method across a parameter grid, writes a CSV — no new Java needed for most questions.
# See docs/binder/headless-verification-playbook.md for when to reach for this.
#
# Usage:
#   tools/dev/run_probe.sh \
#     --target com.example.globe.world.LatitudeBiomes#alpineSurfaceKind \
#     --types  int,int,int,int \
#     --names  x,y,z,radius \
#     --grid   "x=0..3000:30;y=150..200;z=556;radius=5000" \
#     [--out run-headless/latdev/probe-report.csv] \
#     [--radius 10000] \
#     [--level-type globe:globe_small] \
#     [--seed 2591890304012655616] \
#     [--max-combos 200000]
#
# `--radius` is the radius fed to the target class's setActiveRadiusBlocks(int) during init
# (if it has one) — independent of any grid dimension you also choose to name "radius".
# `--level-type`/`--seed` control the booted globe world preset (defaults: globe:globe_small,
# a fixed dev seed) — irrelevant unless your probed method reads world seed/radius state.
#
# Output: CSV at --out (default run-headless/latdev/probe-report.csv), one row per grid
# combination, columns = your --names plus a trailing `result` column.

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

print_usage() {
  sed -n '2,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

if [[ $# -eq 0 || "$1" == "-h" || "$1" == "--help" ]]; then
  print_usage
  exit "$([[ $# -eq 0 ]] && echo 2 || echo 0)"
fi

TARGET="" TYPES="" NAMES="" GRID="" OUT="" RADIUS="" MAXCOMBOS=""
LEVEL_TYPE="globe:globe_small"
SEED="2591890304012655616"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target) TARGET="$2"; shift 2 ;;
    --types) TYPES="$2"; shift 2 ;;
    --names) NAMES="$2"; shift 2 ;;
    --grid) GRID="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --radius) RADIUS="$2"; shift 2 ;;
    --level-type) LEVEL_TYPE="$2"; shift 2 ;;
    --seed) SEED="$2"; shift 2 ;;
    --max-combos) MAXCOMBOS="$2"; shift 2 ;;
    -h|--help) print_usage; exit 0 ;;
    *) echo "unknown arg: $1" >&2; print_usage; exit 2 ;;
  esac
done

if [[ -z "$TARGET" || -z "$TYPES" || -z "$NAMES" || -z "$GRID" ]]; then
  echo "error: --target, --types, --names, --grid are required." >&2
  print_usage
  exit 2
fi

if [[ -z "$OUT" ]]; then
  OUT="run-headless/latdev/probe-report.csv"
fi
# The headless server's working directory is run-headless/ itself (the Loom runDir), so a
# relative --out would otherwise resolve to run-headless/run-headless/... Always pass the
# server an absolute path so this is unambiguous regardless of caller cwd.
case "$OUT" in
  /*) : ;;
  *) OUT="$(pwd)/${OUT}" ;;
esac

JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export JAVA_HOME
unset JAVA_TOOL_OPTIONS || true

LEVEL_NAME="latdev-probe-$(date +%Y%m%d%H%M%S 2>/dev/null || echo run)"

echo "[run_probe] target=$TARGET grid=$GRID -> $OUT" >&2

./gradlew --no-daemon --console plain \
  "-Platdev.preview.levelName=${LEVEL_NAME}" \
  "-Platdev.preview.levelSeed=${SEED}" \
  "-Platdev.preview.levelType=${LEVEL_TYPE}" \
  -Dlatdev.biomePng=disabled \
  -Dlatdev.probe=true \
  "-Dlatdev.probe.target=${TARGET}" \
  "-Dlatdev.probe.types=${TYPES}" \
  "-Dlatdev.probe.names=${NAMES}" \
  "-Dlatdev.probe.grid=${GRID}" \
  "-Dlatdev.probe.out=${OUT}" \
  "-Dlatdev.probe.radius=${RADIUS}" \
  "-Dlatdev.probe.maxCombos=${MAXCOMBOS}" \
  runBiomePreview

echo "" >&2
echo "[run_probe] report: ${OUT}" >&2
if [[ -f "$OUT" ]]; then
  echo "[run_probe] $(wc -l < "$OUT") line(s) (incl. header). First few rows:" >&2
  head -5 "$OUT" >&2
fi
