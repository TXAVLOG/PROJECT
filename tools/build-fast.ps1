param(
    [ValidateSet("Debug", "Release")]
    [string]$BuildType = "Debug",
    [switch]$UseDaemon
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host "[BUILD] $Message"
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
if (-not (Test-Path $gradleWrapper)) {
    throw "Không tìm thấy gradlew.bat tại $gradleWrapper"
}

Write-Step "Khởi động build $BuildType ..."
$gradleArgs = @()
if (-not $UseDaemon) {
    $gradleArgs += "--no-daemon"
}
$gradleArgs += "assemble$BuildType"
$process = Start-Process -FilePath $gradleWrapper -ArgumentList $gradleArgs -WorkingDirectory $repoRoot -NoNewWindow -PassThru -Wait
if ($process.ExitCode -ne 0) {
    throw "Build ${BuildType} thất bại với mã $($process.ExitCode)"
}

$apkDir = Join-Path $repoRoot "app\build\outputs\apk\$($BuildType.ToLower())"
if (-not (Test-Path $apkDir)) {
    throw "Không tìm thấy thư mục APK: $apkDir"
}

$latestApk = Get-ChildItem -Path $apkDir -Filter "*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $latestApk) {
    throw "Không có file APK nào trong $apkDir"
}

$versionPropsPath = Join-Path $repoRoot "version.properties"
if (-not (Test-Path $versionPropsPath)) {
    throw "Thiếu file version.properties"
}

$versionMatch = Select-String -Path $versionPropsPath -Pattern "^versionName\s*=\s*(.+)$"
if (-not $versionMatch) {
    throw "Không đọc được versionName trong version.properties"
}
$versionName = $versionMatch.Matches[0].Groups[1].Value.Trim()

$targetDir = Join-Path $repoRoot "TXABUILD"
if (-not (Test-Path $targetDir)) {
    Write-Step "Tạo thư mục TXABUILD"
    New-Item -ItemType Directory -Path $targetDir | Out-Null
}

$targetName = "TXAMUSIC ($versionName).apk"
$targetPath = Join-Path $targetDir $targetName

Copy-Item -Path $latestApk.FullName -Destination $targetPath -Force

Write-Step "Build thành công! APK đã được copy tới: $targetPath"
