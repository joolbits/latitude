from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import threading
from collections import Counter
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RUNS_ROOT = ROOT / "run-headless" / "latdev" / "atlas-runs"
VIEWER_ROOT = ROOT / "tools" / "atlas" / "viewer"
ATLAS_PS1 = ROOT / "tools" / "atlas" / "Atlas.ps1"

STEP_RE = re.compile(r"step(\d+)", re.IGNORECASE)
HEX_RE = re.compile(r"^#?([0-9a-fA-F]{6})$")

# Fallback name mapping for legacy runs that only have biome PNG + txt.
# These RGB values are the canonical stable colors emitted by BiomePreviewExporter.
CANONICAL_COLOR_TO_BIOME_ID = {
    "47,111,168": "minecraft:ocean",
    "230,244,255": "minecraft:snowy_slopes",
    "143,191,99": "minecraft:plains",
    "46,138,87": "minecraft:jungle",
    "211,155,77": "minecraft:desert",
    "235,72,63": "minecraft:beach",
    "74,123,77": "minecraft:forest",
    "60,107,67": "minecraft:swamp",
    "122,122,122": "minecraft:stony_shore",
    "232,225,204": "minecraft:snowy_beach",
    "231,215,165": "minecraft:beach",
    "189,182,74": "minecraft:savanna",
    "142,59,204": "minecraft:mushroom_fields",
    "58,240,218": "minecraft:warm_ocean",
    "154,154,154": "minecraft:stony_shore",
}

BIOME_DISPLAY_COLOR_OVERRIDE = {
    "minecraft:beach": [231, 215, 165],       # #E7D7A5 sandy tan
    "minecraft:snowy_beach": [233, 225, 204], # #E9E1CC off-white sand
    "minecraft:stony_shore": [154, 154, 154], # #9A9A9A stone gray
}

GENERATION_LOCK = threading.Lock()
GENERATION_PROC: subprocess.Popen | None = None
GENERATION_STATE: dict[str, object | None] = {
    "active": False,
    "step": None,
    "started_at": None,
    "finished_at": None,
    "exit_code": None,
    "message": "",
}


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def generation_status() -> dict:
    with GENERATION_LOCK:
        return {
            "active": bool(GENERATION_STATE.get("active")),
            "step": GENERATION_STATE.get("step"),
            "started_at": GENERATION_STATE.get("started_at"),
            "finished_at": GENERATION_STATE.get("finished_at"),
            "exit_code": GENERATION_STATE.get("exit_code"),
            "message": GENERATION_STATE.get("message") or "",
        }


def _watch_generation(proc: subprocess.Popen, step: int):
    global GENERATION_PROC
    code = proc.wait()
    with GENERATION_LOCK:
        if GENERATION_PROC is proc:
            GENERATION_PROC = None
        GENERATION_STATE["active"] = False
        GENERATION_STATE["finished_at"] = utc_now_iso()
        GENERATION_STATE["exit_code"] = int(code)
        if code == 0:
            GENERATION_STATE["message"] = f"Generation complete (step {step})."
        else:
            GENERATION_STATE["message"] = f"Generation failed (step {step}, exit {code})."


def start_generation(step: int) -> tuple[bool, dict]:
    global GENERATION_PROC

    if step <= 0 or step > 4096:
        raise ValueError("step must be in range 1..4096")
    if not ATLAS_PS1.exists():
        raise FileNotFoundError(f"Atlas launcher not found: {ATLAS_PS1}")

    with GENERATION_LOCK:
        if GENERATION_STATE.get("active"):
            return (
                False,
                {
                    "active": bool(GENERATION_STATE.get("active")),
                    "step": GENERATION_STATE.get("step"),
                    "started_at": GENERATION_STATE.get("started_at"),
                    "finished_at": GENERATION_STATE.get("finished_at"),
                    "exit_code": GENERATION_STATE.get("exit_code"),
                    "message": GENERATION_STATE.get("message") or "",
                },
            )

        powershell = shutil.which("powershell.exe") or shutil.which("pwsh") or shutil.which("powershell")
        if not powershell:
            raise RuntimeError("PowerShell not found on PATH.")

        creationflags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        proc = subprocess.Popen(
            [powershell, "-ExecutionPolicy", "Bypass", "-File", str(ATLAS_PS1), "-Step", str(step), "-NoViewerOpen"],
            cwd=str(ROOT),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            creationflags=creationflags,
        )
        GENERATION_PROC = proc
        GENERATION_STATE["active"] = True
        GENERATION_STATE["step"] = int(step)
        GENERATION_STATE["started_at"] = utc_now_iso()
        GENERATION_STATE["finished_at"] = None
        GENERATION_STATE["exit_code"] = None
        GENERATION_STATE["message"] = f"Generating atlas run (step {step})..."

    watcher = threading.Thread(target=_watch_generation, args=(proc, int(step)), daemon=True)
    watcher.start()
    return True, generation_status()


def run_path(run_id: str) -> Path:
    return RUNS_ROOT / run_id


def step_num(layer_id: str) -> int:
    m = STEP_RE.search(layer_id or "")
    return int(m.group(1)) if m else 10**9


def hex_to_rgb(value: str) -> list[int]:
    m = HEX_RE.match(value or "")
    if not m:
        return [138, 138, 138]
    n = int(m.group(1), 16)
    return [(n >> 16) & 0xFF, (n >> 8) & 0xFF, n & 0xFF]


def biome_name_from_id(biome_id: str) -> str:
    raw = (biome_id or "unknown").split(":")[-1].replace("_", " ").strip()
    return raw.title() if raw else "Unknown"


def rgb_key(rgb: tuple[int, int, int]) -> str:
    return f"{rgb[0]},{rgb[1]},{rgb[2]}"


def read_json_file(path: Path):
    text = path.read_text(encoding="utf-8-sig")
    if text.startswith("\ufeff"):
        text = text.lstrip("\ufeff")
    return json.loads(text)


def _collect_image_color_stats(image_path: Path) -> tuple[Counter[tuple[int, int, int]], int]:
    counts: Counter[tuple[int, int, int]] = Counter()
    with Image.open(image_path).convert("RGB") as img:
        data = img.getdata()
        counts.update(data)
    total = sum(counts.values())
    return counts, total


def _derive_biomes_from_image_only(image_path: Path) -> list[dict]:
    color_counts, total = _collect_image_color_stats(image_path)
    out = []
    for (r, g, b), count in color_counts.most_common():
        key = rgb_key((r, g, b))
        mapped = CANONICAL_COLOR_TO_BIOME_ID.get(key)
        biome_id = mapped if mapped else f"color:{r:02x}{g:02x}{b:02x}"
        biome_name = biome_name_from_id(biome_id) if mapped else f"Color {r:02X}{g:02X}{b:02X}"
        display_color = BIOME_DISPLAY_COLOR_OVERRIDE.get(biome_id, [r, g, b])
        out.append(
            {
                "id": biome_id,
                "name": biome_name,
                "color": display_color,
                "pct": round((count * 100.0) / max(total, 1), 4),
            }
        )
    return out


def _biomes_from_palette_and_ids(
    image_path: Path,
    ids_path: Path,
    palette_entries: list[dict],
) -> list[dict]:
    # Build exact index -> RGB mapping from the image + ids grid so returned colors
    # always match displayed pixels byte-for-byte.
    idx_to_color_counts: dict[int, Counter[tuple[int, int, int]]] = {}
    idx_counts: Counter[int] = Counter()

    with Image.open(image_path).convert("RGB") as biomes_img, Image.open(ids_path).convert("RGB") as ids_img:
        if biomes_img.size != ids_img.size:
            return []
        for biome_rgb, idx_rgb in zip(biomes_img.getdata(), ids_img.getdata()):
            r, g, b = idx_rgb
            idx = r if (r == g == b) else ((r & 0xFF) | ((g & 0xFF) << 8) | ((b & 0xFF) << 16))
            idx_counts[idx] += 1
            if idx not in idx_to_color_counts:
                idx_to_color_counts[idx] = Counter()
            idx_to_color_counts[idx][biome_rgb] += 1

    total = sum(idx_counts.values()) or 1
    out: list[dict] = []

    for e in palette_entries:
        if not isinstance(e, dict):
            continue
        idx = e.get("index")
        biome_id = e.get("biome_id")
        if not isinstance(idx, int) or not isinstance(biome_id, str):
            continue
        count = idx_counts.get(idx, 0)
        dominant_rgb = (138, 138, 138)
        if idx in idx_to_color_counts and idx_to_color_counts[idx]:
            dominant_rgb = idx_to_color_counts[idx].most_common(1)[0][0]
        if count <= 0:
            continue
        row = {
            "index": idx,
            "id": biome_id,
            "name": biome_name_from_id(biome_id),
            "color": [dominant_rgb[0], dominant_rgb[1], dominant_rgb[2]],
            "pct": round((count * 100.0) / total, 4),
        }
        out.append(row)
    out.sort(key=lambda b: b.get("pct", 0.0), reverse=True)
    return out


def layers_for_run(run_dir: Path) -> list[str]:
    layers: set[str] = set()
    for f in run_dir.glob("*.png"):
        name = f.name
        m = re.match(r"^(step\d+)_biomes\.png$", name, re.IGNORECASE)
        if m:
            layers.add(m.group(1).lower())
            continue
        m2 = re.match(r"^biomes_.*_(step\d+)\.png$", name, re.IGNORECASE)
        if m2:
            layers.add(m2.group(1).lower())
    return sorted(layers, key=step_num)


def layer_file(run_dir: Path, layer: str, suffix: str) -> Path | None:
    direct = run_dir / f"{layer}_{suffix}"
    if direct.exists():
        return direct

    if suffix == "biomes.png":
        legacy = next(iter(sorted(run_dir.glob(f"biomes_*_{layer}.png"))), None)
        if legacy:
            return legacy
    if suffix == "biome_palette.json":
        legacy = next(iter(sorted(run_dir.glob(f"*{layer}*biome_palette.json"))), None)
        if legacy:
            return legacy
    if suffix == "biome_ids.png":
        legacy = next(iter(sorted(run_dir.glob(f"*{layer}*biome_ids.png"))), None)
        if legacy:
            return legacy

    return None


class Handler(BaseHTTPRequestHandler):
    server_version = "AtlasViewerAPI/1.0"

    def _send_json(self, payload, status=HTTPStatus.OK):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()
        self.wfile.write(body)

    def _send_file(self, path: Path, content_type: str):
        if not path.exists() or not path.is_file():
            self._send_text("not found", HTTPStatus.NOT_FOUND)
            return

        data = path.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.end_headers()
        self.wfile.write(data)

    def _send_text(self, text: str, status=HTTPStatus.OK):
        data = text.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.end_headers()
        self.wfile.write(data)

    def do_OPTIONS(self):
        self.send_response(HTTPStatus.NO_CONTENT)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()

    def do_POST(self):
        parsed = urlparse(self.path)
        path = unquote(parsed.path)

        if path == "/api/generate":
            self.handle_generate()
            return

        self._send_text("not found", HTTPStatus.NOT_FOUND)

    def do_GET(self):
        parsed = urlparse(self.path)
        path = unquote(parsed.path)

        # API routes
        if path == "/api/runs":
            self.handle_runs()
            return

        if path == "/api/generation-status":
            self.handle_generation_status()
            return

        m = re.match(r"^/api/runs/([^/]+)/manifest$", path)
        if m:
            self.handle_manifest(m.group(1))
            return

        m = re.match(r"^/api/runs/([^/]+)/layers$", path)
        if m:
            self.handle_layers(m.group(1))
            return

        m = re.match(r"^/api/runs/([^/]+)/layers/([^/]+)/biomes$", path)
        if m:
            self.handle_biomes(m.group(1), m.group(2))
            return

        m = re.match(r"^/api/runs/([^/]+)/layers/([^/]+)/inventory$", path)
        if m:
            self.handle_inventory(m.group(1), m.group(2))
            return

        m = re.match(r"^/api/runs/([^/]+)/layers/([^/]+)/image$", path)
        if m:
            self.handle_image(m.group(1), m.group(2))
            return

        m = re.match(r"^/api/runs/([^/]+)/layers/([^/]+)/ids-image$", path)
        if m:
            self.handle_ids_image(m.group(1), m.group(2))
            return

        # static viewer
        if path == "/":
            static_path = VIEWER_ROOT / "index.html"
        else:
            static_path = (VIEWER_ROOT / path.lstrip("/")).resolve()
            if VIEWER_ROOT.resolve() not in static_path.parents and static_path != VIEWER_ROOT.resolve():
                self._send_text("forbidden", HTTPStatus.FORBIDDEN)
                return

        if static_path.suffix.lower() == ".html":
            ctype = "text/html; charset=utf-8"
        elif static_path.suffix.lower() == ".js":
            ctype = "application/javascript; charset=utf-8"
        elif static_path.suffix.lower() == ".css":
            ctype = "text/css; charset=utf-8"
        else:
            ctype = "application/octet-stream"
        self._send_file(static_path, ctype)

    def handle_runs(self):
        if not RUNS_ROOT.exists():
            self._send_json([])
            return
        runs = [d.name for d in RUNS_ROOT.iterdir() if d.is_dir()]
        runs.sort(reverse=True)
        self._send_json(runs)

    def handle_generation_status(self):
        self._send_json(generation_status())

    def handle_generate(self):
        length = int(self.headers.get("Content-Length", "0") or "0")
        raw = self.rfile.read(length) if length > 0 else b"{}"
        try:
            payload = json.loads(raw.decode("utf-8")) if raw else {}
        except Exception:
            self._send_json({"error": "invalid JSON body"}, HTTPStatus.BAD_REQUEST)
            return

        step_raw = payload.get("step", 16) if isinstance(payload, dict) else 16
        try:
            step = int(step_raw)
            started, status = start_generation(step)
        except ValueError as e:
            self._send_json({"error": str(e)}, HTTPStatus.BAD_REQUEST)
            return
        except FileNotFoundError as e:
            self._send_json({"error": str(e)}, HTTPStatus.NOT_FOUND)
            return
        except RuntimeError as e:
            self._send_json({"error": str(e)}, HTTPStatus.INTERNAL_SERVER_ERROR)
            return
        except Exception as e:
            self._send_json({"error": str(e)}, HTTPStatus.INTERNAL_SERVER_ERROR)
            return

        self._send_json(status, HTTPStatus.ACCEPTED if started else HTTPStatus.CONFLICT)

    def handle_manifest(self, run: str):
        run_dir = run_path(run)
        if not run_dir.exists():
            self._send_json({}, HTTPStatus.NOT_FOUND)
            return
        manifest = run_dir / "run_manifest.json"
        if not manifest.exists():
            self._send_json({})
            return
        try:
            data = read_json_file(manifest)
            self._send_json(data if isinstance(data, dict) else {})
        except Exception:
            self._send_json({})

    def handle_layers(self, run: str):
        run_dir = run_path(run)
        if not run_dir.exists():
            self._send_json([], HTTPStatus.NOT_FOUND)
            return
        self._send_json(layers_for_run(run_dir))

    def handle_biomes(self, run: str, layer: str):
        run_dir = run_path(run)
        if not run_dir.exists():
            self._send_json([], HTTPStatus.NOT_FOUND)
            return

        image_path = layer_file(run_dir, layer, "biomes.png")
        palette_path = layer_file(run_dir, layer, "biome_palette.json")
        ids_path = layer_file(run_dir, layer, "biome_ids.png")
        if not image_path:
            self._send_json([], HTTPStatus.NOT_FOUND)
            return

        try:
            if palette_path and ids_path:
                palette_json = read_json_file(palette_path)
                entries = palette_json.get("biomes", []) if isinstance(palette_json, dict) else []
                entries = entries if isinstance(entries, list) else []
                out = _biomes_from_palette_and_ids(image_path, ids_path, entries)
                if out:
                    self._send_json(out)
                    return
            # Fallback for older runs with no ids/palette.
            self._send_json(_derive_biomes_from_image_only(image_path))
        except Exception:
            self._send_json([])

    def handle_inventory(self, run: str, layer: str):
        run_dir = run_path(run)
        if not run_dir.exists():
            self._send_json([], HTTPStatus.NOT_FOUND)
            return

        inventory_path = layer_file(run_dir, layer, "world_biome_inventory.json")
        if not inventory_path or not inventory_path.exists():
            self._send_json([])
            return

        try:
            payload = read_json_file(inventory_path)
            rows = payload.get("biomes", []) if isinstance(payload, dict) else []
            if not isinstance(rows, list):
                rows = []
            out = []
            for row in rows:
                if not isinstance(row, dict):
                    continue
                biome_id = row.get("biome_id")
                if not isinstance(biome_id, str) or not biome_id:
                    continue
                display_color = row.get("displayColor")
                out.append(
                    {
                        "id": biome_id,
                        "name": row.get("biome_name") or biome_name_from_id(biome_id),
                        "color": hex_to_rgb(display_color) if isinstance(display_color, str) else [138, 138, 138],
                        "present_in_world": bool(row.get("present_in_world", True)),
                        "first_seen_x": row.get("first_seen_x"),
                        "first_seen_z": row.get("first_seen_z"),
                        "latitude_label": row.get("latitude_label") or "",
                        "discovery_step_used": row.get("discovery_step_used"),
                        "discovery_hits": row.get("discovery_hits"),
                    }
                )
            self._send_json(out)
        except Exception:
            self._send_json([])

    def handle_image(self, run: str, layer: str):
        run_dir = run_path(run)
        if not run_dir.exists():
            self._send_text("run not found", HTTPStatus.NOT_FOUND)
            return
        image_path = layer_file(run_dir, layer, "biomes.png")
        if not image_path:
            self._send_text("layer image not found", HTTPStatus.NOT_FOUND)
            return
        self._send_file(image_path, "image/png")

    def handle_ids_image(self, run: str, layer: str):
        run_dir = run_path(run)
        if not run_dir.exists():
            self._send_text("run not found", HTTPStatus.NOT_FOUND)
            return
        ids_path = layer_file(run_dir, layer, "biome_ids.png")
        if not ids_path:
            self._send_text("layer ids image not found", HTTPStatus.NOT_FOUND)
            return
        self._send_file(ids_path, "image/png")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Latitude Atlas Viewer API server")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5000)
    args = parser.parse_args()

    host = args.host
    port = args.port
    server = ThreadingHTTPServer((host, port), Handler)
    print(f"Serving Atlas viewer + API on http://{host}:{port}")
    server.serve_forever()
