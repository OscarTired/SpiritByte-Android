param([switch]$SkipNative)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not (Test-Path (Join-Path $projectRoot '.signing/signing.properties'))) {
    throw 'Missing private upload signing configuration: .signing/signing.properties'
}
if (-not $env:JAVA_HOME) {
    $studioJava = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path $studioJava) { $env:JAVA_HOME = $studioJava }
}
Push-Location $projectRoot
try {
    if (-not $SkipNative) { & "$PSScriptRoot/build-native.ps1" }
    & ./gradlew.bat ':app:bundleRelease' ':app:lintRelease' --console=plain
    if ($LASTEXITCODE) { throw 'Android release build failed.' }
    New-Item -ItemType Directory -Force instalador | Out-Null
    Copy-Item 'app/build/outputs/bundle/release/app-release.aab' 'instalador/SpiritByte-Android.aab' -Force
    Write-Output "Google Play bundle: $projectRoot/instalador/SpiritByte-Android.aab"
} finally { Pop-Location }
