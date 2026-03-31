param(
  [string]$Seed = "2591890304012655616",
  [string]$Size = "small",
  [int]$Step = 16,
  [switch]$EmitHeight = $false,
  [switch]$NoViewerOpen = $false,
  [switch]$IncludeRuggedness = $false,
  [switch]$GenerateRuggednessOnly = $false,
  [string]$Run = ""
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
$runStart = Get-Date

# Run folder staging + manifest (viewer can use this later)
$finalRunDir = Join-Path $root "run-headless\latdev\atlas-runs\$ts"
$stagingRoot = Join-Path $root "run-headless\latdev\atlas-runs\.staging"
$runDir = Join-Path $stagingRoot $ts
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

@{
  ts = $ts
  branch = $branch
  commit = $commit
  seed = $Seed
  size = $Size
  step = $Step
  emitBiomeIndex = $true
  emitHeight = [bool]$EmitHeight
} | ConvertTo-Json -Depth 5 | Out-File (Join-Path $runDir "run_manifest.json") -Encoding utf8

# Size -> expected radius map
$sizeKey = $Size.ToLower()
$expectedRadius = switch ($sizeKey) {
  "itty" { 3750 }
  "ittybitty" { 3750 }
  "tiny" { 5000 }
  "small" { 7500 }
  "regular" { 10000 }
  "large" { 15000 }
  "ginormous" { 20000 }
  "massive" { 20000 }
  default { throw "Unknown atlas size '$Size'" }
}

function Fail-AtlasStage([string]$reason) {
  $failPath = Join-Path $runDir "FAILED.txt"
  $reason | Out-File $failPath
  throw "[atlas] $reason"
}

# ── Ruggedness-only add-on mode ─────────────────────────────────────────────
if ($GenerateRuggednessOnly) {
  if ([string]::IsNullOrEmpty($Run)) {
    throw "-Run <timestamp-folder> is required with -GenerateRuggednessOnly"
  }
  $existingRunDir = Join-Path $root "run-headless\latdev\atlas-runs\$Run"
  if (-not (Test-Path $existingRunDir)) {
    throw "Run folder not found: $existingRunDir"
  }
  $manifest = Get-Content (Join-Path $existingRunDir "run_manifest.json") | ConvertFrom-Json
  $rSeed = $manifest.seed
  $rSize = $manifest.size
  $rStep = $manifest.step

  Write-Host "[atlas] Adding ruggedness to run $Run (seed=$rSeed size=$rSize step=$rStep)"

  .\gradlew.bat --stop | Out-Null
  .\gradlew.bat compileJava --rerun-tasks --no-daemon

  .\gradlew.bat --no-daemon --info --stacktrace runBiomePreview `
      --args="--seed $rSeed --size $rSize --step $rStep --layers ruggedness"

  # Copy only ruggedness.png into the existing run folder; preserve all other files.
  $rSeedDir = Join-Path $root ("run\latdev\atlas\seed_$rSeed")
  $rStepDir = Get-ChildItem $rSeedDir -Directory -Recurse |
      Where-Object { $_.Name -ieq ("step$rStep") } |
      Sort-Object LastWriteTime |
      Select-Object -Last 1
  if ($rStepDir) {
    $src = Join-Path $rStepDir.FullName "ruggedness.png"
    if (Test-Path $src) {
      $dst = Join-Path $existingRunDir ("step${rStep}_ruggedness.png")
      Copy-Item $src -Destination $dst -Force
      Write-Host "[atlas] Wrote $dst"
    } else {
      Write-Warning "ruggedness.png not found in step dir $($rStepDir.FullName)"
    }
  } else {
    Write-Warning "No step$rStep dir found under $rSeedDir"
  }
  exit 0
}
# ── End ruggedness-only ──────────────────────────────────────────────────────

# Build once (so export reflects current code)
.\gradlew.bat --stop | Out-Null
.\gradlew.bat clean build -x test

# Run exporter
$args = "--seed $Seed --size $Size --step $Step --emitBiomeIndex true --bundle"
if ($IncludeRuggedness) { $args += " --ruggedness true" }
if ($EmitHeight) { $args += " --emitHeight" }

.\gradlew.bat --no-daemon --info --stacktrace runBiomePreview --args="$args"

# --- Collect latest atlas step outputs into the stamped run folder ---
$seedDir = Join-Path $root ("run\latdev\atlas\seed_" + $Seed)
$stepDir = $null
if (Test-Path $seedDir) {
  $stepDirs = Get-ChildItem $seedDir -Directory -Recurse |
    Where-Object { $_.Name -ieq ("step" + $Step) -and $_.LastWriteTime -gt $runStart } |
    Sort-Object LastWriteTime
  $stepDir = $stepDirs | Select-Object -Last 1
}

if (-not $stepDir) {
  Fail-AtlasStage "No fresh atlas step directory found for seed=$Seed step=$Step under $seedDir"
}

$stepPrefix = "step$Step"
$stepFiles = Get-ChildItem $stepDir.FullName -File |
  Where-Object { $_.Extension -in @(".png", ".txt", ".json") }

foreach ($f in $stepFiles) {
  Copy-Item $f.FullName -Destination (Join-Path $runDir ($stepPrefix + "_" + $f.Name)) -Force
}

$requiredFiles = @(
  "$stepPrefix`_biomes.png",
  "$stepPrefix`_legend.json",
  "$stepPrefix`_world_biome_inventory.json"
)
foreach ($req in $requiredFiles) {
  $reqPath = Join-Path $runDir $req
  if (-not (Test-Path $reqPath)) {
    Fail-AtlasStage "Missing required atlas output $req in staging $runDir"
  }
}

$legendPath = Join-Path $runDir "$stepPrefix`_legend.json"
try {
  $legend = Get-Content $legendPath | ConvertFrom-Json
} catch {
  Fail-AtlasStage "Unable to read legend json at $legendPath"
}
if (-not $legend.PSObject.Properties.Name -contains "radiusBlocks") {
  Fail-AtlasStage "Legend missing radiusBlocks field at $legendPath"
}
$producedRadius = [int]$legend.radiusBlocks
if ($producedRadius -ne $expectedRadius) {
  Fail-AtlasStage "Radius mismatch. requested size '$Size' expects radius $expectedRadius but legend reports $producedRadius"
}

# Move staged run into final location only after validation passes
if (Test-Path $finalRunDir) {
  Remove-Item -Recurse -Force $finalRunDir
}
New-Item -ItemType Directory -Force -Path (Split-Path $finalRunDir) | Out-Null
Move-Item -LiteralPath $runDir -Destination $finalRunDir
$runDir = $finalRunDir

if (-not $NoViewerOpen) {
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
}
