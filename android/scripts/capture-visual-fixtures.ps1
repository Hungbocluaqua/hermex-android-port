[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $OutputDirectory,

    [string] $AvdName,

    [string] $Serial,

    [string] $PackageName = "com.uzairansar.hermex",

    [ValidateSet("dark", "light")]
    [string] $Theme = "dark",

    [ValidatePattern("^\d+x\d+$")]
    [string] $Size = "1080x2400",

    [ValidateRange(120, 640)]
    [int] $Density = 420,

    [switch] $CaptureProductionOnboarding
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,

        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
    }
}

function Get-OnlineDevices {
    param([string] $AdbPath)

    $lines = & $AdbPath devices
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to query adb devices."
    }
    return @(
        $lines |
            Select-String -Pattern "^(\S+)\s+device$" |
            ForEach-Object { $_.Matches[0].Groups[1].Value }
    )
}

function Wait-ForBoot {
    param(
        [string] $AdbPath,
        [string] $DeviceSerial,
        [int] $TimeoutSeconds = 180
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $devices = Get-OnlineDevices -AdbPath $AdbPath
        if ($devices -contains $DeviceSerial) {
            $bootCompleted = (& $AdbPath -s $DeviceSerial shell getprop sys.boot_completed 2>$null).Trim()
            if ($bootCompleted -eq "1") {
                return
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for $DeviceSerial to finish booting."
}

function Find-RunningAvd {
    param(
        [string] $AdbPath,
        [string] $RequestedAvd
    )

    foreach ($device in Get-OnlineDevices -AdbPath $AdbPath) {
        if (-not $device.StartsWith("emulator-", [StringComparison]::OrdinalIgnoreCase)) {
            continue
        }
        $runningName = (& $AdbPath -s $device emu avd name 2>$null | Select-Object -First 1).Trim()
        if ($runningName -eq $RequestedAvd) {
            return $device
        }
    }
    return $null
}

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
$repoPrefix = $repoRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (
    $outputRoot.Equals($repoRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $outputRoot.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)
) {
    throw "OutputDirectory must be outside the repository so screenshots cannot enter tracked sources."
}
[void] (New-Item -ItemType Directory -Path $outputRoot -Force)

$sdkCandidates = @()
if ($env:LOCALAPPDATA) {
    $sdkCandidates += Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
if ($env:ANDROID_SDK_ROOT) {
    $sdkCandidates += $env:ANDROID_SDK_ROOT
}
if ($env:ANDROID_HOME) {
    $sdkCandidates += $env:ANDROID_HOME
}
$sdkRoot = $sdkCandidates |
    Where-Object { Test-Path -LiteralPath (Join-Path $_ "platform-tools\adb.exe") } |
    Select-Object -First 1
if (-not $sdkRoot) {
    throw "Android SDK not found. Expected adb under LOCALAPPDATA\Android\Sdk or an Android SDK environment variable."
}

$adb = Join-Path $sdkRoot "platform-tools\adb.exe"
$emulator = Join-Path $sdkRoot "emulator\emulator.exe"
Invoke-Checked -FilePath $adb -Arguments @("start-server")

if ($Serial) {
    $targetSerial = $Serial
    Wait-ForBoot -AdbPath $adb -DeviceSerial $targetSerial
} elseif ($AvdName) {
    $targetSerial = Find-RunningAvd -AdbPath $adb -RequestedAvd $AvdName
    if (-not $targetSerial) {
        if (-not (Test-Path -LiteralPath $emulator)) {
            throw "Android emulator executable not found at $emulator."
        }
        $before = Get-OnlineDevices -AdbPath $adb
        Start-Process `
            -FilePath $emulator `
            -ArgumentList @(
                "-avd", $AvdName,
                "-no-window",
                "-no-audio",
                "-no-boot-anim",
                "-no-snapshot-save",
                "-gpu", "swiftshader_indirect"
            ) `
            -WindowStyle Hidden
        $deadline = [DateTime]::UtcNow.AddSeconds(90)
        do {
            Start-Sleep -Seconds 2
            $targetSerial = Get-OnlineDevices -AdbPath $adb |
                Where-Object { $_.StartsWith("emulator-") -and ($before -notcontains $_) } |
                Select-Object -First 1
        } while (-not $targetSerial -and [DateTime]::UtcNow -lt $deadline)
        if (-not $targetSerial) {
            throw "Timed out waiting for AVD '$AvdName' to appear in adb."
        }
        Wait-ForBoot -AdbPath $adb -DeviceSerial $targetSerial
    }
} else {
    $onlineDevices = Get-OnlineDevices -AdbPath $adb
    if ($onlineDevices.Count -ne 1) {
        throw "Specify -AvdName or -Serial when adb does not expose exactly one online device."
    }
    $targetSerial = $onlineDevices[0]
    Wait-ForBoot -AdbPath $adb -DeviceSerial $targetSerial
}

Write-Host "Using Android target $targetSerial"
Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "shell", "wm", "size", $Size)
Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "shell", "wm", "density", $Density.ToString())
Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "shell", "settings", "put", "system", "font_scale", "1.0")
Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "shell", "settings", "put", "global", "window_animation_scale", "0")
Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "shell", "settings", "put", "global", "transition_animation_scale", "0")
Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "shell", "settings", "put", "global", "animator_duration_scale", "0")
Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "shell", "settings", "put", "system", "accelerometer_rotation", "0")
Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "shell", "settings", "put", "system", "user_rotation", "0")
Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "shell", "cmd", "uimode", "night", $(if ($Theme -eq "dark") { "yes" } else { "no" }))
Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "shell", "wm", "dismiss-keyguard")

$androidRoot = Join-Path $repoRoot "android"
$gradle = Join-Path $androidRoot "gradlew.bat"
Push-Location $androidRoot
try {
    Invoke-Checked -FilePath $gradle -Arguments @(
        "--no-daemon",
        "--no-configuration-cache",
        ":app:assembleDebug",
        ":app:assembleDebugAndroidTest"
    )
} finally {
    Pop-Location
}

$appApk = Join-Path $androidRoot "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $androidRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
if (-not (Test-Path -LiteralPath $appApk) -or -not (Test-Path -LiteralPath $testApk)) {
    throw "Expected debug and androidTest APKs were not produced."
}

Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "install", "-r", $appApk)
Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "install", "-r", $testApk)
$testClass = "com.uzairansar.hermex.visual.VisualFixtureTest"
$runner = "$PackageName.test/androidx.test.runner.AndroidJUnitRunner"
$instrumentationOutput = & $adb -s $targetSerial shell am instrument -w -r -e class $testClass $runner
if ($LASTEXITCODE -ne 0) {
    throw "Visual fixture instrumentation failed to launch."
}
$instrumentationText = $instrumentationOutput -join [Environment]::NewLine
Write-Host $instrumentationText
if (
    $instrumentationText -match "FAILURES!!!" -or
    $instrumentationText -match "INSTRUMENTATION_FAILED" -or
    $instrumentationText -notmatch "OK \(2 tests\)"
) {
    throw "Visual fixture instrumentation did not complete both tests successfully."
}

$deviceOutput = "/sdcard/Android/data/$PackageName/files/visual-fixtures"
foreach ($fileName in @("onboarding-welcome.png", "frosted-surface.png")) {
    $destination = Join-Path $outputRoot $fileName
    Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "pull", "$deviceOutput/$fileName", $destination)
    if (-not (Test-Path -LiteralPath $destination) -or (Get-Item -LiteralPath $destination).Length -eq 0) {
        throw "Screenshot pull did not produce $destination."
    }
}

if ($CaptureProductionOnboarding) {
    Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "shell", "pm", "clear", $PackageName)
    Invoke-Checked -FilePath $adb -Arguments @(
        "-s", $targetSerial,
        "shell", "am", "start", "-W",
        "-n", "$PackageName/.MainActivity"
    )
    Start-Sleep -Seconds 2
    $deviceOnboarding = "/sdcard/Download/hermex-production-onboarding.png"
    Invoke-Checked -FilePath $adb -Arguments @(
        "-s", $targetSerial,
        "shell", "screencap", "-p", $deviceOnboarding
    )
    $onboardingDestination = Join-Path $outputRoot "production-onboarding.png"
    Invoke-Checked -FilePath $adb -Arguments @("-s", $targetSerial, "pull", $deviceOnboarding, $onboardingDestination)
    if (-not (Test-Path -LiteralPath $onboardingDestination) -or (Get-Item -LiteralPath $onboardingDestination).Length -eq 0) {
        throw "Production onboarding capture did not produce $onboardingDestination."
    }
}

Write-Host "Visual fixture screenshots written to $outputRoot"
