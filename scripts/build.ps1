param([switch]$SkipNative, [switch]$Lightweight)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $env:JAVA_HOME) {
    $studioJava = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path $studioJava) { $env:JAVA_HOME = $studioJava }
}
Push-Location $projectRoot
try {
    if (-not $SkipNative) { & "$PSScriptRoot/build-native.ps1" }
    $variant = if ($Lightweight) { 'lightweight' } else { 'debug' }
    $taskVariant = if ($Lightweight) { 'Lightweight' } else { 'Debug' }
    & ./gradlew.bat ":app:assemble$taskVariant" ":app:lint$taskVariant"
    if ($LASTEXITCODE) { throw 'Android build failed.' }
    New-Item -ItemType Directory -Force instalador | Out-Null
    Copy-Item "app/build/outputs/apk/$variant/app-$variant.apk" instalador/SpiritByte-Android.apk -Force
    Write-Output "Instalador: $projectRoot/instalador/SpiritByte-Android.apk"
} finally { Pop-Location }
