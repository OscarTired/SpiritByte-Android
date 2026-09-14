param([switch]$SkipNative)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $env:JAVA_HOME) {
    $studioJava = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path $studioJava) { $env:JAVA_HOME = $studioJava }
}
Push-Location $projectRoot
try {
    if (-not $SkipNative) { & "$PSScriptRoot/build-native.ps1" }
    & ./gradlew.bat :app:assembleDebug :app:lintDebug
    if ($LASTEXITCODE) { throw 'Android build failed.' }
    New-Item -ItemType Directory -Force instalador | Out-Null
    Copy-Item app/build/outputs/apk/debug/app-debug.apk instalador/SpiritByte-Android.apk -Force
    Write-Output "Instalador: $projectRoot/instalador/SpiritByte-Android.apk"
} finally { Pop-Location }
