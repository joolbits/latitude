param(
  [Parameter(Mandatory = $true)]
  [long]$Seed,
  [int]$R = 5000,
  [int]$Port = 8000
)

# Resolved from this script's own location rather than a hardcoded checkout path, so the script
# works from any clone and carries no assumption about where the repository lives.
$root = Join-Path $PSScriptRoot "run\latdev\atlas\seed_$Seed\R$R"
if (!(Test-Path $root)) {
  Write-Host "Atlas folder not found: $root" -ForegroundColor Red
  exit 1
}

Set-Location $root
Start-Process "http://127.0.0.1:$Port/viewer.html"
py -m http.server $Port
