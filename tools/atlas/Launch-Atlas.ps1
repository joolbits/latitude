param(
  [string]$Seed = "2591890304012655616",
  [string]$Size = "small",
  [int]$Step = 16,
  [switch]$EmitHeight = $false,
  [switch]$NoViewerOpen = $false,
  [switch]$IncludeRuggedness = $false,
  [switch]$GenerateRuggednessOnly = $false,
  [string]$Run = "",
  [int]$RuggednessPreviewStep = 0
)

$ErrorActionPreference = 'Stop'

# Strip inherited JVM debug flags — these pollute every Gradle/Java process and
# produce "Picked up JAVA_TOOL_OPTIONS" noise or worse, corrupt JVM args.
Remove-Item Env:JAVA_TOOL_OPTIONS  -ErrorAction SilentlyContinue
Remove-Item Env:_JAVA_OPTIONS      -ErrorAction SilentlyContinue

# Point Gradle at the local JDK using plain env vars only.
# Do NOT put the path in GRADLE_OPTS: Windows paths with spaces (e.g. "Program Files")
# cause Gradle to split the token and fail with ClassNotFoundException: Files\Eclipse.
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
if (Test-Path (Join-Path $jdk 'bin\java.exe')) {
  $env:JAVA_HOME            = $jdk
  $env:ORG_GRADLE_JAVA_HOME = $jdk
}

# Delegate to Atlas.ps1 in the same folder, forwarding all parameters.
# Build args array explicitly — do NOT pass empty strings for optional params like -Run
# because powershell.exe -File treats them as a missing value and throws.
$atlasPs1 = Join-Path $PSScriptRoot 'Atlas.ps1'
$atlasArgs = @('-Seed', $Seed, '-Size', $Size, '-Step', $Step)
if ($EmitHeight)              { $atlasArgs += '-EmitHeight' }
if ($NoViewerOpen)            { $atlasArgs += '-NoViewerOpen' }
if ($IncludeRuggedness)       { $atlasArgs += '-IncludeRuggedness' }
if ($GenerateRuggednessOnly)  { $atlasArgs += '-GenerateRuggednessOnly' }
if ($Run)                     { $atlasArgs += @('-Run', $Run) }
if ($RuggednessPreviewStep -gt 0) { $atlasArgs += @('-RuggednessPreviewStep', $RuggednessPreviewStep) }

& powershell -NoExit -ExecutionPolicy Bypass -File $atlasPs1 @atlasArgs
