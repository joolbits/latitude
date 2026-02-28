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

<#
    Use a canonical viewer template that lives in the repo so every export gets the same UI.
    If the target already has a viewer and it differs, back it up, then copy the template.
#>
$viewer = Join-Path $rDir.FullName "viewer.html"
$template = Join-Path $PSScriptRoot "tools\atlas\viewer.html"
if (-not (Test-Path $template)) {
  Write-Host "Canonical viewer template missing: $template" -ForegroundColor Red
  exit 1
}

function Get-Sha256($path) {
  if (-not (Test-Path $path)) { return $null }
  return (Get-FileHash -Algorithm SHA256 -Path $path).Hash
}

$templateHash = Get-Sha256 $template
$targetHash = Get-Sha256 $viewer

if (-not $targetHash -or $targetHash -ne $templateHash) {
  if (Test-Path $viewer) {
    Copy-Item $viewer ($viewer + ".bak-prev") -Force
  }
  Copy-Item $template $viewer -Force
  Write-Host "[atlas] injected canonical viewer into $viewer" -ForegroundColor Yellow
}

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
