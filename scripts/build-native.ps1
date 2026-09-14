param([string]$SdkRoot = $env:ANDROID_SDK_ROOT)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $SdkRoot -or -not (Test-Path (Join-Path $SdkRoot 'ndk/28.2.13676358'))) {
    $SdkRoot = Join-Path $projectRoot '.tools/android-sdk'
}
$ndkPath = Join-Path $SdkRoot 'ndk/28.2.13676358'
if (-not (Test-Path $ndkPath)) { throw 'Install Android NDK 28.2.13676358 or pass -SdkRoot.' }
$env:ANDROID_NDK_HOME = $ndkPath
Push-Location (Join-Path $projectRoot 'native')
try {
    cargo build --locked --lib
    if ($LASTEXITCODE) { throw 'Host Rust build failed.' }
    cargo run --locked --features bindgen --bin uniffi-bindgen -- generate --library target/debug/spiritbyte_mobile.dll --language kotlin --config uniffi.toml --out-dir ../app/src/main/java --no-format
    if ($LASTEXITCODE) { throw 'Kotlin binding generation failed.' }
    cargo ndk -t arm64-v8a -t x86_64 --platform 26 -o ../app/src/main/jniLibs build --locked --release --lib
    if ($LASTEXITCODE) { throw 'Android Rust build failed.' }
} finally { Pop-Location }
