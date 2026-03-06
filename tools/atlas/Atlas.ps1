param(
  [string]$Seed = "2591890304012655616",
  [string]$Size = "small",
  [int]$Step = 128,
  [switch]$EmitHeight = $false
)

$ErrorActionPreference = "Stop"

# Canonical root guard
$root = (git rev-parse --show-toplevel).Trim()
$normalizedRoot = [System.IO.Path]::GetFullPath($root)
$expectedRoot = [System.IO.Path]::GetFullPath("$HOME\CascadeProjects\Latitude (Globe)")
if ($normalizedRoot -ne $expectedRoot) {
  throw "Not in canonical repo root. Found: $root"
}

# Provenance stamp
$branch = (git rev-parse --abbrev-ref HEAD).Trim()
$commit = (git rev-parse --short HEAD).Trim()
$ts = Get-Date -Format "yyyyMMdd-HHmmss"

# Run folder + manifest (viewer can use this later)
$runDir = Join-Path $root "run-headless\latdev\atlas-runs\$ts"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

@{
  ts = $ts
  branch = $branch
  commit = $commit
  seed = $Seed
  size = $Size
  step = $Step
  emitHeight = [bool]$EmitHeight
} | ConvertTo-Json -Depth 5 | Out-File (Join-Path $runDir "run_manifest.json") -Encoding utf8

# Build once (so export reflects current code)
.\gradlew.bat --stop | Out-Null
.\gradlew.bat clean build -x test

# Run exporter
$args = "--seed $Seed --size $Size --step $Step"
if ($EmitHeight) { $args += " --emitHeight" }

.\gradlew.bat --no-daemon --info --stacktrace runBiomePreview --args="$args"

# --- Collect latest export outputs into the stamped run folder ---
$src = Join-Path $root "run-headless\latdev\biome-previews"
if (Test-Path $src) {
  # copy only the newest matching outputs (png + txt) into this run folder
  $latestPng = Get-ChildItem $src -File -Filter "*.png" | Sort-Object LastWriteTime | Select-Object -Last 1
  if ($latestPng) {
    Copy-Item $latestPng.FullName -Destination $runDir -Force
    $maybeTxt = [System.IO.Path]::ChangeExtension($latestPng.FullName, ".txt")
    if (Test-Path $maybeTxt) { Copy-Item $maybeTxt -Destination $runDir -Force }
  }
}

# --- Serve + open viewer (MVP) ---
$viewerIndex = Join-Path $root "tools\atlas\viewer\index.html"
if (Test-Path $viewerIndex) {
  $port = 8000
  while (Test-NetConnection -ComputerName "127.0.0.1" -Port $port -InformationLevel Quiet) { $port++ }

  # Serve the repo root so /run-headless/... URLs work
  Push-Location $root
  Start-Process -WindowStyle Hidden -FilePath "python" -ArgumentList @("-m","http.server",$port) | Out-Null
  Pop-Location

  Start-Process "http://127.0.0.1:$port/tools/atlas/viewer/index.html"
}
