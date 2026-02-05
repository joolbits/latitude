import json
from pathlib import Path

TARGET = "minecraft:mangrove_swamp"

allowed = {
    Path("src/main/resources/data/minecraft/worldgen/biome/mangrove_swamp.json").resolve(),
    Path("src/main/resources/data/globe/tags/worldgen/biome/lat_mangrove_candidate.json").resolve(),
}

def scrub(obj):
    changed = False

    if isinstance(obj, list):
        new_list = []
        for v in obj:
            if v == TARGET:
                changed = True
                continue
            v2, ch = scrub(v)
            changed = changed or ch
            new_list.append(v2)
        return new_list, changed

    if isinstance(obj, dict):
        new_d = {}
        for k, v in obj.items():
            v2, ch = scrub(v)
            changed = changed or ch
            new_d[k] = v2
        return new_d, changed

    return obj, False

root = Path("src/main/resources").resolve()
edited = []

for path in root.rglob("*.json"):
    rp = path.resolve()
    if rp in allowed:
        continue

    try:
        text = path.read_text(encoding="utf-8")
    except Exception:
        continue

    if TARGET not in text:
        continue

    try:
        data = json.loads(text)
    except Exception:
        # If JSON is malformed, skip; you'll need to fix that file manually.
        print(f"SKIP (invalid JSON): {path}")
        continue

    new_data, changed = scrub(data)
    if changed:
        path.write_text(json.dumps(new_data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        edited.append(str(path))

print("EDITED FILES:")
for p in edited:
    print(p)
print(f"Edited {len(edited)} files.")
