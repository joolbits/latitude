param(
  [long]$Seed = 0,
  [ValidateSet("itty","tiny","small","regular","large","ginormous")][string]$Size="small",
  [string]$Steps="128,64,32",
  [int]$Y=64,
  [string]$Layers="biomes,bands,temperature,humidity",
  [string]$Overlays="lat10,bandEdges",
  [string]$Masks="swamp,mangrove",
  [switch]$HighResStep16,
  [int]$Port = 8000
)

if ($HighResStep16) {
  if (-not $PSBoundParameters.ContainsKey("Steps")) { $Steps = "16" }
  if (-not $PSBoundParameters.ContainsKey("Size")) { $Size = "regular" }
}

if ($Seed -eq 0) {
  $Seed = Get-Random -Minimum 1 -Maximum 9223372036854775807
}

Set-Location $PSScriptRoot
$env:GRADLE_USER_HOME = "$PSScriptRoot\.gradle"

$maskList = @()
foreach ($m in ($Masks -split '[,; ]+' | Where-Object { $_ -and $_.Trim().Length -gt 0 })) {
  $maskList += $m.Trim()
}

$argsParts = @(
  "--latdevBiomePng",
  "--size", $Size,
  "--steps", $Steps,
  "--y", "$Y",
  "--seed", "$Seed",
  "--layers", $Layers,
  "--overlay", $Overlays
)
foreach ($m in $maskList) {
  $argsParts += @("--mask", $m)
}
$argsString = $argsParts -join " "

Write-Host "Running Atlas export with args: $argsString" -ForegroundColor Cyan
& "$PSScriptRoot\gradlew.bat" --no-daemon runBiomePreview --args="$argsString"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$seedDir = Join-Path $PSScriptRoot "run\latdev\atlas\seed_$Seed"
$rDir = Get-ChildItem $seedDir -Directory -Filter "R*" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $rDir) {
  Write-Host "No R folder found under: $seedDir" -ForegroundColor Red
  exit 1
}

# Always refresh atlas_index.json for the selected R folder.
$stepDirs = Get-ChildItem $rDir.FullName -Directory -Filter "step*" | Sort-Object Name
$index = foreach ($s in $stepDirs) {
  $files = Get-ChildItem $s.FullName -File | Select-Object -ExpandProperty Name
  [pscustomobject]@{
    step  = $s.Name
    files = $files
  }
}
$atlasIndexPath = Join-Path $rDir.FullName "atlas_index.json"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$indexArray = @($index)
if ($indexArray.Count -eq 1) {
  $indexJson = "[`r`n" + (ConvertTo-Json -InputObject $indexArray[0] -Depth 5) + "`r`n]"
} else {
  $indexJson = ConvertTo-Json -InputObject $indexArray -Depth 5
}
[System.IO.File]::WriteAllText($atlasIndexPath, $indexJson, $utf8NoBom)

# Always write a viewer so each run has a predictable UI.
$viewer = Join-Path $rDir.FullName "viewer.html"
$viewerHtml = @'
<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>Atlas Viewer</title>
  <style>
    body { font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; margin: 16px; }
    .row { display:flex; gap:12px; align-items:center; flex-wrap:wrap; margin-bottom:12px; }
    select, input { padding:6px 8px; }
    .wrap { display:flex; gap:16px; align-items:flex-start; flex-wrap:wrap; }
    .pane { border:1px solid #ddd; padding:12px; border-radius:10px; }
    .stage { position:relative; display:inline-block; border:1px solid #eee; }
    .stage img { image-rendering: pixelated; position:absolute; left:0; top:0; }
    pre { max-width: 520px; white-space: pre-wrap; word-break: break-word; margin:0; }
    .muted { color:#666; font-size:12px; }
  </style>
</head>
<body>
  <div class="row">
    <label>Step <select id="step"></select></label>
    <label>Overlay <select id="overlay"></select></label>
    <label>Opacity <input id="opacity" type="range" min="0" max="100" value="40" /></label>
    <label><input id="showOverlay" type="checkbox" checked /> Show</label>
    <label>Zoom <input id="zoom" type="number" value="1" min="1" max="8" /></label>
    <button id="reload" type="button">reload legend</button>
    <span class="muted" id="status"></span>
  </div>

  <div class="wrap">
    <div class="pane">
      <div class="stage" id="stage">
        <img id="baseImg" alt="biomes" />
        <img id="overlayImg" alt="overlay" style="pointer-events:none;" />
      </div>
    </div>
    <div class="pane" style="min-width:340px;">
      <strong>Legend (human-readable)</strong>
      <pre id="legendHuman">Loading...</pre>
      <details style="margin-top:10px;">
        <summary>Show raw legend.json</summary>
        <pre id="legendRaw" style="margin-top:8px;">(raw legend loads after first step is selected)</pre>
      </details>
    </div>
  </div>

<script>
const stepSel = document.getElementById('step');
const overlaySel = document.getElementById('overlay');
const baseImg = document.getElementById('baseImg');
const overlayImg = document.getElementById('overlayImg');
const opacity = document.getElementById('opacity');
const showOverlay = document.getElementById('showOverlay');
const zoom = document.getElementById('zoom');
const legendHumanEl = document.getElementById('legendHuman');
const legendRawEl = document.getElementById('legendRaw');
const status = document.getElementById('status');
const stage = document.getElementById('stage');

let index = [];
const preferredOrder = ["bands.png","temperature.png","humidity.png","continentalness.png","erosion.png","weirdness.png","biomes.png"];

const layerNames = {
  biomes: "Biome map",
  bands: "Latitude bands",
  temperature: "Temperature",
  humidity: "Humidity",
  continentalness: "Continentalness",
  erosion: "Erosion",
  weirdness: "Weirdness"
};

function toTitle(text) {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function layerLabel(name) {
  if (layerNames[name]) return layerNames[name];
  if (name.startsWith("mask_")) return `Mask: ${toTitle(name.replace("mask_",""))}`;
  return toTitle(name.replace(/[_-]/g, " "));
}

function updateOverlayVisibility() {
  overlayImg.style.opacity = (parseInt(opacity.value || "40", 10) / 100).toString();
  overlayImg.style.display = showOverlay.checked && overlaySel.value !== "(none)" ? "block" : "none";
}

function applyZoom() {
  const z = Math.max(1, Math.min(8, parseInt(zoom.value || "1", 10)));
  const w = Math.max(1, baseImg.naturalWidth || 640);
  const h = Math.max(1, baseImg.naturalHeight || 640);
  stage.style.width = `${Math.round(w * z)}px`;
  stage.style.height = `${Math.round(h * z)}px`;
  baseImg.style.width = "100%";
  baseImg.style.height = "100%";
  overlayImg.style.width = "100%";
  overlayImg.style.height = "100%";
}

function setImages(step, overlayFile) {
  baseImg.src = `${step}/biomes.png?t=${Date.now()}`;
  if (overlayFile && overlayFile !== "(none)") {
    overlayImg.src = `${step}/${overlayFile}?t=${Date.now()}`;
  } else {
    overlayImg.removeAttribute("src");
  }
  updateOverlayVisibility();
  status.textContent = `Base: ${step}/biomes.png | Overlay: ${overlayFile}`;
}

function pct(frac) {
  return `${(Number(frac) * 100).toFixed(1)}%`;
}

function renderLegendHuman(j) {
  const lines = [];
  lines.push(`Seed: ${j.seed ?? "?"}`);
  lines.push(`Radius: ${j.radiusBlocks ?? "?"} blocks`);
  lines.push(`Step: ${j.stepBlocks ?? "?"} blocks`);
  lines.push(`Y level: ${j.y ?? "?"}`);
  if (Array.isArray(j.layers)) {
    lines.push(`Layers: ${j.layers.map(layerLabel).join(", ")}`);
  }
  if (Array.isArray(j.maskTargets) && j.maskTargets.length) {
    lines.push(`Masks: ${j.maskTargets.join(", ")}`);
  }
  if (Array.isArray(j.overlays) && j.overlays.length) {
    lines.push(`Overlay lines baked into PNGs: ${j.overlays.join(", ")}`);
  }
  if (j.temperatureScale) lines.push(`Temperature scale: ${j.temperatureScale}`);
  if (j.humidityScale) lines.push(`Humidity scale: ${j.humidityScale}`);

  if (Array.isArray(j.bands) && j.bands.length) {
    lines.push("");
    lines.push("Latitude bands:");
    for (const b of j.bands) {
      lines.push(`- ${toTitle(String(b.zone || "?"))}: ${pct(b.minFrac)} to ${pct(b.maxFrac)} (${b.color || "n/a"})`);
    }
  }

  if (j.files && typeof j.files === "object") {
    lines.push("");
    lines.push("Image files:");
    for (const [key, value] of Object.entries(j.files)) {
      lines.push(`- ${layerLabel(key)} -> ${value}`);
    }
  }

  legendHumanEl.textContent = lines.join("\\n");
}

async function loadLegend(step) {
  try {
    const j = await fetchJsonClean(`${step}/legend.json?t=${Date.now()}`);
    renderLegendHuman(j);
    legendRawEl.textContent = JSON.stringify(j, null, 2);
  } catch (e) {
    legendHumanEl.textContent = `No legend.json in this step folder. (${e.message || e})`;
    legendRawEl.textContent = "(missing)";
  }
}

function sortSteps(steps) {
  return [...steps].sort((a, b) => {
    const an = parseInt((a.match(/\d+/) || ["0"])[0], 10);
    const bn = parseInt((b.match(/\d+/) || ["0"])[0], 10);
    return an - bn;
  });
}

function populateOverlays(stepName) {
  const step = index.find(x => x.step === stepName);
  const pngs = (step?.files || []).filter(f => f.toLowerCase().endsWith(".png"));
  const found = [];
  for (const f of preferredOrder) if (pngs.includes(f) && f !== "biomes.png") found.push(f);
  for (const f of pngs) if (f !== "biomes.png" && !found.includes(f)) found.push(f);

  overlaySel.innerHTML = "";
  const noneOpt = document.createElement("option");
  noneOpt.value = "(none)";
  noneOpt.textContent = "(none)";
  overlaySel.appendChild(noneOpt);
  for (const f of found) {
    const opt = document.createElement("option");
    opt.value = f;
    opt.textContent = f.replace(".png","");
    overlaySel.appendChild(opt);
  }

  setImages(stepName, overlaySel.value);
  loadLegend(stepName);
}

async function init() {
  const rawIndex = await fetchJsonClean(`atlas_index.json?t=${Date.now()}`);
  if (Array.isArray(rawIndex)) {
    index = rawIndex;
  } else if (rawIndex && typeof rawIndex === "object" && rawIndex.step) {
    index = [rawIndex];
  } else {
    throw new Error("atlas_index.json has unexpected shape");
  }
  const steps = sortSteps(index.map(x => x.step));
  if (!steps.length) {
    status.textContent = "No step folders found.";
    return;
  }

  stepSel.innerHTML = "";
  for (const s of steps) {
    const opt = document.createElement("option");
    opt.value = s;
    opt.textContent = s;
    stepSel.appendChild(opt);
  }

  const preferred = steps.includes("step16") ? "step16" : steps[0];
  stepSel.value = preferred;
  populateOverlays(preferred);

  stepSel.addEventListener("change", () => populateOverlays(stepSel.value));
  overlaySel.addEventListener("change", () => setImages(stepSel.value, overlaySel.value));
  opacity.addEventListener("input", updateOverlayVisibility);
  showOverlay.addEventListener("change", updateOverlayVisibility);
  zoom.addEventListener("input", applyZoom);
  document.getElementById("reload").addEventListener("click", () => loadLegend(stepSel.value));
  baseImg.addEventListener("load", applyZoom);

  applyZoom();
}

async function fetchJsonClean(url) {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  const txt = await r.text();
  return JSON.parse(txt.replace(/^\uFEFF/, ""));
}

init().catch((e) => {
  status.textContent = `Failed to load atlas_index.json. ${e.message || e}`;
});
</script>
</body>
</html>
'@
[System.IO.File]::WriteAllText($viewer, $viewerHtml, $utf8NoBom)

# Replace stale server on the target port so viewer always points to this run's R folder.
$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
  $proc = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
  if ($proc -and ($proc.ProcessName -in @("python","py"))) {
    Stop-Process -Id $proc.Id -Force
    Start-Sleep -Milliseconds 300
  }
}

Start-Process -FilePath "py" -ArgumentList "-m","http.server","$Port" -WorkingDirectory $rDir.FullName | Out-Null

$url = "http://127.0.0.1:$Port/viewer.html"
for ($i = 0; $i -lt 20; $i++) {
  try {
    $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2
    if ($resp.StatusCode -eq 200) { break }
  } catch { }
  Start-Sleep -Milliseconds 250
}

Start-Process $url
Write-Host "Viewer: $url" -ForegroundColor Green
