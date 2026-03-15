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
  emitBiomeIndex = $true
  emitHeight = [bool]$EmitHeight
} | ConvertTo-Json -Depth 5 | Out-File (Join-Path $runDir "run_manifest.json") -Encoding utf8

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
    Where-Object { $_.Name -ieq ("step" + $Step) } |
    Sort-Object LastWriteTime
  $stepDir = $stepDirs | Select-Object -Last 1
}

if ($stepDir) {
  $stepPrefix = "step$Step"
  $stepFiles = Get-ChildItem $stepDir.FullName -File |
    Where-Object { $_.Extension -in @(".png", ".txt", ".json") }

  foreach ($f in $stepFiles) {
    Copy-Item $f.FullName -Destination (Join-Path $runDir ($stepPrefix + "_" + $f.Name)) -Force
  }
} else {
  Write-Warning "No atlas step directory found for seed=$Seed step=$Step under $seedDir"
}

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
