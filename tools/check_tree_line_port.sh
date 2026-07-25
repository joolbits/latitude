#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$root"

fail=0

require_grep() {
  local pattern="$1"
  local file="$2"
  local label="$3"
  if ! grep -Eq "$pattern" "$file"; then
    printf 'missing: %s (%s in %s)\n' "$label" "$pattern" "$file" >&2
    fail=1
  fi
}

require_file() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    printf 'missing file: %s\n' "$file" >&2
    fail=1
  fi
}

require_grep 'TREE_LINE_Y' src/main/java/com/example/globe/world/LatitudeBiomes.java "tree-line constant"
require_grep 'treeLineSuppression' src/main/java/com/example/globe/world/LatitudeBiomes.java "tree-line suppression function"
require_grep 'ALPINE_ROCK_Y' src/main/java/com/example/globe/world/LatitudeBiomes.java "alpine rock constant"
require_grep 'alpineSurfaceKind' src/main/java/com/example/globe/world/LatitudeBiomes.java "alpine surface classifier"
require_grep 'ALPINE_NOISE_SALT' src/main/java/com/example/globe/world/LatitudeBiomes.java "alpine coherent noise salt"

require_file src/main/java/com/example/globe/mixin/TreeLineVegetationGuardMixin.java
require_file src/main/java/com/example/globe/mixin/AlpineSurfaceMixin.java
require_grep 'FeaturePlaceContext<\?>' src/main/java/com/example/globe/mixin/TreeLineVegetationGuardMixin.java "26.1.2 FeaturePlaceContext adaptation"
require_grep 'TreeFeature\.class' src/main/java/com/example/globe/mixin/TreeLineVegetationGuardMixin.java "tree feature target"
require_grep 'ProtoChunk\.class' src/main/java/com/example/globe/mixin/AlpineSurfaceMixin.java "proto chunk target"
require_grep 'defaultBlockState\(\)' src/main/java/com/example/globe/mixin/AlpineSurfaceMixin.java "Mojang block state factory"
require_grep 'alpineSurfaceKind' src/main/java/com/example/globe/mixin/AlpineSurfaceMixin.java "alpine surface classifier use"

require_grep '"TreeLineVegetationGuardMixin"' src/main/resources/globe.mixins.json "tree-line mixin registration"
require_grep '"AlpineSurfaceMixin"' src/main/resources/globe.mixins.json "alpine surface mixin registration"
require_grep 'pos\.getY\(\) >= LatitudeBiomes\.ALPINE_ROCK_Y' src/main/java/com/example/globe/mixin/ProtoChunkSnowBlockGuardMixin.java "snow guard alpine cap allowance"
require_grep 'pos\.getY\(\) >= LatitudeBiomes\.ALPINE_ROCK_Y' src/main/java/com/example/globe/mixin/ChunkRegionWarmSnowTrapMixin.java "chunk-region snow trap alpine cap allowance"

if (( fail )); then
  exit 1
fi

echo "tree-line/alpine structural proof passed"
