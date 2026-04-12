param(
  [long]$Seed = 4303535158216281382,
  [int]$R = 5000,
  [int]$Port = 8000
)

$root = "$HOME\CascadeProjects\Latitude (Globe)\run\latdev\atlas\seed_$Seed\R$R"
if (!(Test-Path $root)) {
  Write-Host "Atlas folder not found: $root" -ForegroundColor Red
  exit 1
}

Set-Location $root
Start-Process "http://127.0.0.1:$Port/viewer.html"
py -m http.server $Port
