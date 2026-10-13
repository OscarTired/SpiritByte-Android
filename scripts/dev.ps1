param(
    [switch]$Watch,
    [string]$Avd = 'Pixel_8_Pro_API_36',
    [ValidatePattern('^emulator-\d+$')][string]$Device,
    [switch]$RebuildNative
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$adbPath = (Get-Command adb -ErrorAction Stop).Source
$emulatorPath = (Get-Command emulator -ErrorAction Stop).Source
if (-not $env:JAVA_HOME) {
    $studioJava = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path -LiteralPath $studioJava) { $env:JAVA_HOME = $studioJava }
}

function Find-Emulator {
    foreach ($line in (& $adbPath devices)) {
        if ($line -match '^(emulator-\d+)\s+device\b') {
            $serial = $Matches[1]
            $avdName = (& $adbPath -s $serial emu avd name | Select-Object -First 1).Trim()
            if ($avdName -eq $Avd) { return $serial }
        }
    }
}

if (-not $Device) {
    $Device = Find-Emulator
    if (-not $Device) {
        if ($Avd -notin @(& $emulatorPath -list-avds)) { throw "No existe el AVD '$Avd'." }
        # A visible emulator is intentional for this interactive development command.
        Start-Process -FilePath $emulatorPath -ArgumentList @('-avd', $Avd) | Out-Null
        $deadline = [DateTime]::UtcNow.AddMinutes(2)
        do {
            Start-Sleep -Milliseconds 750
            $Device = Find-Emulator
        } while (-not $Device -and [DateTime]::UtcNow -lt $deadline)
        if (-not $Device) { throw 'El emulador no se conectó a ADB en dos minutos.' }
    }
}
$deadline = [DateTime]::UtcNow.AddMinutes(2)
while ((& $adbPath -s $Device shell getprop sys.boot_completed).Trim() -ne '1') {
    if ([DateTime]::UtcNow -gt $deadline) { throw 'El emulador no terminó de iniciar.' }
    Start-Sleep -Milliseconds 750
}

function Deploy-Debug {
    & (Join-Path $projectRoot 'gradlew.bat') :app:assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'La compilación debug falló.' }
    & $adbPath -s $Device install -r (Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk')
    if ($LASTEXITCODE -ne 0) { throw 'La instalación falló. No se desinstaló ni se borraron datos.' }
    & $adbPath -s $Device shell am force-stop com.spiritbyte.android
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo reiniciar la sesión de desarrollo.' }
    & $adbPath -s $Device shell am start -W -n com.spiritbyte.android/.MainActivity
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo abrir SpiritByte.' }
}

$watchPaths = @('app/src/main', 'app/build.gradle.kts', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties') |
    ForEach-Object { Join-Path $projectRoot $_ }
function Source-Stamp {
    (Get-ChildItem -LiteralPath $watchPaths -Recurse -File | Sort-Object FullName |
        ForEach-Object { "$($_.FullName)|$($_.LastWriteTimeUtc.Ticks)|$($_.Length)" }) -join "`n"
}

Push-Location $projectRoot
try {
    if ($RebuildNative) { & (Join-Path $PSScriptRoot 'build-native.ps1') }
    $lastObservedStamp = Source-Stamp
    Deploy-Debug
    if ($Watch) {
        Write-Output 'Desarrollo activo: guarda cambios Kotlin/recursos para actualizar el emulador. Ctrl+C para terminar.'
        Write-Output 'Cada actualización reinicia la app y conserva la bóveda. Para Live Edit sin reinicio, usa Android Studio.'
        Write-Output 'Los cambios Rust requieren ejecutar este comando con -RebuildNative.'
        while ($true) {
            Start-Sleep -Milliseconds 750
            $currentStamp = Source-Stamp
            if ($currentStamp -ne $lastObservedStamp) {
                # Retain the pre-build stamp so edits during a build trigger another deployment.
                $lastObservedStamp = $currentStamp
                try { Deploy-Debug } catch { Write-Warning $_.Exception.Message }
            }
        }
    }
} finally { Pop-Location }
