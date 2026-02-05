import json
from pathlib import Path

root = Path("src/main/resources/data/globe/worldgen/noise_settings")
targets = sorted(root.glob("overworld*.json"))

needle = ["minecraft:swamp"]
replacement = ["minecraft:swamp", "minecraft:mangrove_swamp"]

def transform(obj):
    changed = False
    if isinstance(obj, list):
        out = []
        for v in obj:
            nv, ch = transform(v)
            changed |= ch
            out.append(nv)
        return out, changed
    if isinstance(obj, dict):
        out = {}
        for k, v in obj.items():
            if k == "biome_is" and isinstance(v, list) and v == needle:
                out[k] = replacement
                changed = True
            else:
                nv, ch = transform(v)
                changed |= ch
                out[k] = nv
        return out, changed
    return obj, False

edited = []
for path in targets:
    data = json.loads(path.read_text(encoding="utf-8"))
    new_data, changed = transform(data)
    if changed:
        path.write_text(json.dumps(new_data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        edited.append(str(path))

print("UPDATED FILES:")
for p in edited:
    print(p)
print(f"Updated {len(edited)} file(s).")
