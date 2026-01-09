# ╔════════════════════════════════════════════════════════════════════════════╗
# ║  TXA BUILD FAST - Build & Release Script                                   ║
# ║  Author: TXA Team                                                          ║
# ╚════════════════════════════════════════════════════════════════════════════╝

param(
    [string]$BuildTypeOverride  # Tuy chon: Ghi de BUILD_TYPE tu .env
)

$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot
$repoRoot = Resolve-Path (Join-Path $scriptDir "..")

# ═══════════════════════════════════════════════════════════════════════════════
# Helper Functions
# ═══════════════════════════════════════════════════════════════════════════════
function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "══════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host "  [BUILD] $Message" -ForegroundColor Yellow
    Write-Host "══════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
}

function Write-Error-Exit([string]$Message) {
    Write-Host ""
    Write-Host "  [LOI] $Message" -ForegroundColor Red
    Write-Host ""
    exit 1
}

function Write-Success([string]$Message) {
    Write-Host "  [OK] $Message" -ForegroundColor Green
}

function Write-Info([string]$Message) {
    Write-Host "  [INFO] $Message" -ForegroundColor Gray
}

# ═══════════════════════════════════════════════════════════════════════════════
# Step 1: Check .env file
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step "Kiem tra file cau hinh .env"

$envFile = Join-Path $scriptDir ".env"
$envExample = Join-Path $scriptDir ".env.example"

if (-not (Test-Path $envFile)) {
    Write-Host ""
    Write-Host "  [CANH BAO] Khong tim thay file .env!" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Vui long copy file .env.example sang .env va cau hinh:" -ForegroundColor Yellow
    Write-Host "    Copy-Item '$envExample' '$envFile'" -ForegroundColor White
    Write-Host ""
    Write-Host "  Sau do chinh sua cac gia tri trong file .env" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

# Parse .env file
$envConfig = @{}
Get-Content $envFile | ForEach-Object {
    if ($_ -match "^\s*([^#][^=]+)\s*=\s*(.*)$") {
        $key = $matches[1].Trim()
        $value = $matches[2].Split('#')[0].Trim()
        $envConfig[$key] = $value
    }
}

Write-Success "Da doc file .env"

# ═══════════════════════════════════════════════════════════════════════════════
# Step 2: Check Version & Build Config
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step "Thong tin phien ban & cau hinh"

$versionPropsPath = Join-Path $repoRoot "version.properties"
$versionCode = "0"
$versionName = "0.0.0"
if (Test-Path $versionPropsPath) {
    Get-Content $versionPropsPath | ForEach-Object {
        if ($_ -match "^versionCode\s*=\s*(.+)$") { $versionCode = $matches[1].Trim() }
        if ($_ -match "^versionName\s*=\s*(.+)$") { $versionName = $matches[1].Trim() }
    }
}

$buildType = if ($BuildTypeOverride) { $BuildTypeOverride } else { $envConfig["BUILD_TYPE"] }
if ([string]::IsNullOrWhiteSpace($buildType)) { $buildType = "debug" }
$buildTypeDisplay = (Get-Culture).TextInfo.ToTitleCase($buildType.ToLower())

$cleanBuild = $envConfig["CLEAN_BUILD"] -eq "true"
$uploadToRepo = $envConfig["UPLOAD_TO_REPO"] -eq "true"
$uploadToGitHub = $envConfig["UPLOAD_TO_GITHUB"] -eq "true"

Write-Host "  ╔══════════════════════════════════════════════════════╗" -ForegroundColor White
Write-Host "  ║ PHIEN BAN: v$versionName ($versionCode)" -ForegroundColor White
Write-Host "  ║ LOAI BUILD: $buildTypeDisplay" -ForegroundColor White
Write-Host "  ║ CLEAN BUILD: $cleanBuild" -ForegroundColor White
Write-Host "  ╚══════════════════════════════════════════════════════╝" -ForegroundColor White

# ═══════════════════════════════════════════════════════════════════════════════
# Step 3: Check Git Configuration
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step "Kiem tra cau hinh Git"

$gitEmail = $envConfig["GIT_EMAIL"]
$gitName = $envConfig["GIT_NAME"]

if ([string]::IsNullOrWhiteSpace($gitEmail) -or [string]::IsNullOrWhiteSpace($gitName)) {
    Write-Error-Exit "Chua cau hinh Git trong .env!"
}

# Configure git if needed
$currentGitEmail = git config --global user.email 2>$null
$currentGitName = git config --global user.name 2>$null

if ($currentGitEmail -ne $gitEmail) {
    git config --global user.email $gitEmail
}
if ($currentGitName -ne $gitName) {
    git config --global user.name $gitName
}

Write-Success "Git: $gitName <$gitEmail>"

# ═══════════════════════════════════════════════════════════════════════════════
# Step 4: Clean Build (if enabled)
# ═══════════════════════════════════════════════════════════════════════════════
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
if (-not (Test-Path $gradleWrapper)) {
    Write-Error-Exit "Khong tim thay gradlew.bat"
}

if ($cleanBuild) {
    Write-Step "Dang lam sach du an (Clean)..."
    $cleanProcess = Start-Process -FilePath $gradleWrapper -ArgumentList "clean" -WorkingDirectory $repoRoot -NoNewWindow -PassThru -Wait
    if ($cleanProcess.ExitCode -ne 0) {
        Write-Error-Exit "Clean that bai"
    }
    Write-Success "Da lam sach du an"
}

# ═══════════════════════════════════════════════════════════════════════════════
# Step 5: Build APK
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step "Dang bat dau Build APK ($buildTypeDisplay)..."

$buildTask = "assemble$buildType"
$buildProcess = Start-Process -FilePath $gradleWrapper -ArgumentList $buildTask -WorkingDirectory $repoRoot -NoNewWindow -PassThru -Wait

if ($buildProcess.ExitCode -ne 0) {
    Write-Host ""
    Write-Host "  ╔════════════════════════════════════════════════════╗" -ForegroundColor Red
    Write-Host "  ║ BUILD THAT BAI!                                    ║" -ForegroundColor Red
    Write-Host "  ╚════════════════════════════════════════════════════╝" -ForegroundColor Red
    exit 1
}

Write-Success "Build hoan tat!"

# ═══════════════════════════════════════════════════════════════════════════════
# Step 6: Process APK Output
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step "Xu ly tep APK dau ra"

$apkDir = Join-Path $repoRoot "app\build\outputs\apk\$($buildType.ToLower())"
$latestApk = Get-ChildItem -Path $apkDir -Filter "*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not $latestApk) {
    Write-Error-Exit "Khong tim thay tep APK sau khi build!"
}

$targetDir = Join-Path $repoRoot "TXABUILD"
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir | Out-Null
}

$targetName = "TXAMUSIC_v$versionName.apk"
$targetPath = Join-Path $targetDir $targetName

Copy-Item -Path $latestApk.FullName -Destination $targetPath -Force
Write-Success "Tep APK da duoc luu tai: $targetPath"

# ═══════════════════════════════════════════════════════════════════════════════
# Step 7: Release Actions
# ═══════════════════════════════════════════════════════════════════════════════
if ($uploadToRepo) {
    Write-Step "Dang day APK len Git Repository..."
    git add $targetPath -f
    git commit -m "Release: TXAMUSIC v$versionName"
    git push
    Write-Success "Da day len Repository"
}

if ($uploadToGitHub) {
    Write-Step "Dang tạo GitHub Release..."
    if (Get-Command gh -ErrorAction SilentlyContinue) {
        $tagName = "v$versionName"
        git tag -a $tagName -m "Release $tagName" 2>$null
        git push origin $tagName 2>$null
        
        $releaseNotes = "# 🚀 TXA Music Update v$versionName`n`n> 📝 *Mo ta ban cap nhat? Vao app se co nhe khoi phai xem o day!* 🎵`n`n---`n*Enjoy the music!* 🎧"
        gh release create $tagName $targetPath --title "TXAMUSIC $tagName" --notes "$releaseNotes" 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Da tao GitHub Release: $tagName"
        } else {
            Write-Info "Release co the da ton tai hoac loi ket noi."
        }
    } else {
        Write-Info "Bo qua tạo Release vi chua cai GitHub CLI (gh)."
    }
}

# ═══════════════════════════════════════════════════════════════════════════════
# Final Summary
# ═══════════════════════════════════════════════════════════════════════════════
Write-Host ""
Write-Host "  ╔══════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "  ║  CHUC MUNG! BUILD THANH CONG.                                ║" -ForegroundColor Green
Write-Host "  ║  Tep: $targetName" -ForegroundColor Green
Write-Host "  ╚══════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
