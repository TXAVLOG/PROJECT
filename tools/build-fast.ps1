# ╔════════════════════════════════════════════════════════════════════════════╗
# ║  TXA BUILD FAST - Build & Release Script                                   ║
# ║  Author: TXA Team                                                          ║
# ╚════════════════════════════════════════════════════════════════════════════╝

param(
    [string]$BuildTypeOverride  # Optional: Override BUILD_TYPE from .env
)

$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot
$repoRoot = Resolve-Path (Join-Path $scriptDir "..")

# ═══════════════════════════════════════════════════════════════════════════════
# Helper Functions
# ═══════════════════════════════════════════════════════════════════════════════
function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host "  [BUILD] $Message" -ForegroundColor Yellow
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
}

function Write-Error-Exit([string]$Message) {
    Write-Host ""
    Write-Host "  [ERROR] $Message" -ForegroundColor Red
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
Write-Step "Kiểm tra file cấu hình .env"

$envFile = Join-Path $scriptDir ".env"
$envExample = Join-Path $scriptDir ".env.example"

if (-not (Test-Path $envFile)) {
    Write-Host ""
    Write-Host "  [CẢNH BÁO] Không tìm thấy file .env!" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Vui lòng copy file .env.example sang .env và cấu hình:" -ForegroundColor Yellow
    Write-Host "    Copy-Item '$envExample' '$envFile'" -ForegroundColor White
    Write-Host ""
    Write-Host "  Sau đó chỉnh sửa các giá trị trong file .env" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

# Parse .env file
$envConfig = @{}
Get-Content $envFile | ForEach-Object {
    if ($_ -match "^\s*([^#][^=]+)\s*=\s*(.*)$") {
        $key = $matches[1].Trim()
        $value = $matches[2].Split('#')[0].Trim()  # Remove inline comments
        $envConfig[$key] = $value
    }
}

Write-Success "Đã đọc file .env"

# ═══════════════════════════════════════════════════════════════════════════════
# Step 2: Check Git Configuration
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step "Kiểm tra cấu hình Git"

$gitEmail = $envConfig["GIT_EMAIL"]
$gitName = $envConfig["GIT_NAME"]

if ([string]::IsNullOrWhiteSpace($gitEmail) -or [string]::IsNullOrWhiteSpace($gitName)) {
    Write-Host ""
    Write-Host "  [CẢNH BÁO] Chưa cấu hình Git trong file .env!" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Vui lòng mở file .env và điền:" -ForegroundColor Yellow
    Write-Host "    GIT_EMAIL=your-email@example.com" -ForegroundColor White
    Write-Host "    GIT_NAME=YourGitHubUsername" -ForegroundColor White
    Write-Host ""
    exit 1
}

# Configure git if needed
$currentGitEmail = git config --global user.email 2>$null
$currentGitName = git config --global user.name 2>$null

if ($currentGitEmail -ne $gitEmail) {
    git config --global user.email $gitEmail
    Write-Info "Đã cấu hình Git email: $gitEmail"
}
if ($currentGitName -ne $gitName) {
    git config --global user.name $gitName
    Write-Info "Đã cấu hình Git name: $gitName"
}

Write-Success "Git đã được cấu hình: $gitName <$gitEmail>"

# ═══════════════════════════════════════════════════════════════════════════════
# Step 3: Read Build Configuration
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step "Đọc cấu hình build"

# Build type (can be overridden by parameter)
$buildType = if ($BuildTypeOverride) { $BuildTypeOverride } else { $envConfig["BUILD_TYPE"] }
if ([string]::IsNullOrWhiteSpace($buildType)) { $buildType = "debug" }
$buildType = (Get-Culture).TextInfo.ToTitleCase($buildType.ToLower())  # Capitalize: debug -> Debug

$cleanBuild = $envConfig["CLEAN_BUILD"] -eq "true"
$uploadToRepo = $envConfig["UPLOAD_TO_REPO"] -eq "true"
$uploadToGitHub = $envConfig["UPLOAD_TO_GITHUB"] -eq "true"

Write-Info "Build Type: $buildType"
Write-Info "Clean Build: $cleanBuild"
Write-Info "Upload to Repo: $uploadToRepo"
Write-Info "Upload to GitHub Release: $uploadToGitHub"

# ═══════════════════════════════════════════════════════════════════════════════
# Step 4: Read Version
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step "Đọc phiên bản từ version.properties"

$versionPropsPath = Join-Path $repoRoot "version.properties"
if (-not (Test-Path $versionPropsPath)) {
    Write-Error-Exit "Thiếu file version.properties"
}

$versionCode = ""
$versionName = ""
Get-Content $versionPropsPath | ForEach-Object {
    if ($_ -match "^versionCode\s*=\s*(.+)$") { $versionCode = $matches[1].Trim() }
    if ($_ -match "^versionName\s*=\s*(.+)$") { $versionName = $matches[1].Trim() }
}

if ([string]::IsNullOrWhiteSpace($versionName)) {
    Write-Error-Exit "Không đọc được versionName trong version.properties"
}

Write-Success "Version: $versionName (code: $versionCode)"

# ═══════════════════════════════════════════════════════════════════════════════
# Step 5: Clean Build (if enabled)
# ═══════════════════════════════════════════════════════════════════════════════
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
if (-not (Test-Path $gradleWrapper)) {
    Write-Error-Exit "Không tìm thấy gradlew.bat"
}

if ($cleanBuild) {
    Write-Step "Clean project"
    $cleanProcess = Start-Process -FilePath $gradleWrapper -ArgumentList "clean" -WorkingDirectory $repoRoot -NoNewWindow -PassThru -Wait
    if ($cleanProcess.ExitCode -ne 0) {
        Write-Error-Exit "Clean thất bại"
    }
    Write-Success "Clean hoàn tất"
}

# ═══════════════════════════════════════════════════════════════════════════════
# Step 6: Build APK
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step "Build APK ($buildType)"

$buildTask = "assemble$buildType"
$buildProcess = Start-Process -FilePath $gradleWrapper -ArgumentList $buildTask -WorkingDirectory $repoRoot -NoNewWindow -PassThru -Wait

if ($buildProcess.ExitCode -ne 0) {
    Write-Host ""
    Write-Host "  ╔════════════════════════════════════════════════════════════╗" -ForegroundColor Red
    Write-Host "  ║  BUILD THẤT BẠI!                                           ║" -ForegroundColor Red
    Write-Host "  ║  Vui lòng kiểm tra lỗi ở trên và thử lại.                  ║" -ForegroundColor Red
    Write-Host "  ╚════════════════════════════════════════════════════════════╝" -ForegroundColor Red
    Write-Host ""
    exit 1
}

Write-Success "Build thành công!"

# ═══════════════════════════════════════════════════════════════════════════════
# Step 7: Copy APK to TXABUILD
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step "Copy APK vào thư mục TXABUILD"

$apkDir = Join-Path $repoRoot "app\build\outputs\apk\$($buildType.ToLower())"
if (-not (Test-Path $apkDir)) {
    Write-Error-Exit "Không tìm thấy thư mục APK: $apkDir"
}

$latestApk = Get-ChildItem -Path $apkDir -Filter "*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $latestApk) {
    Write-Error-Exit "Không có file APK nào trong $apkDir"
}

$targetDir = Join-Path $repoRoot "TXABUILD"
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir | Out-Null
}

$targetName = "TXAMUSIC_v$versionName.apk"
$targetPath = Join-Path $targetDir $targetName

Copy-Item -Path $latestApk.FullName -Destination $targetPath -Force
Write-Success "APK đã copy tới: $targetPath"

# ═══════════════════════════════════════════════════════════════════════════════
# Step 8: Upload to Git Repository (if enabled)
# ═══════════════════════════════════════════════════════════════════════════════
if ($uploadToRepo) {
    Write-Step "Đẩy APK lên Git Repository"
    
    Set-Location $repoRoot
    git add $targetPath -f
    git commit -m "Release: TXAMUSIC v$versionName"
    git push
    
    Write-Success "Đã push APK lên repository"
}

# ═══════════════════════════════════════════════════════════════════════════════
# Step 9: Create GitHub Release (if enabled)
# ═══════════════════════════════════════════════════════════════════════════════
if ($uploadToGitHub) {
    Write-Step "Tạo GitHub Release"
    
    # Check if gh CLI is installed
    $ghInstalled = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $ghInstalled) {
        Write-Host ""
        Write-Host "  [CẢNH BÁO] Chưa cài đặt GitHub CLI (gh)" -ForegroundColor Yellow
        Write-Host "  Để tạo Release tự động, vui lòng cài đặt:" -ForegroundColor Yellow
        Write-Host "    winget install GitHub.cli" -ForegroundColor White
        Write-Host ""
        Write-Host "  Hoặc tải tại: https://cli.github.com/" -ForegroundColor Gray
        Write-Host ""
    } else {
        Set-Location $repoRoot
        
        $tagName = "v$versionName"
        $releaseTitle = "TXAMUSIC $tagName"
        $releaseNotes = "Mô tả bản cập nhật? Vào app sẽ có! 🎵"
        
        # Create tag
        git tag -a $tagName -m "Release $tagName" 2>$null
        git push origin $tagName 2>$null
        
        # Create release with APK
        Write-Info "Đang tạo release $tagName ..."
        gh release create $tagName $targetPath --title $releaseTitle --notes $releaseNotes 2>$null
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Đã tạo GitHub Release: $tagName"
        } else {
            Write-Host "  [CẢNH BÁO] Có thể release đã tồn tại hoặc cần đăng nhập gh" -ForegroundColor Yellow
            Write-Host "  Chạy lệnh: gh auth login" -ForegroundColor Gray
        }
    }
}

# ═══════════════════════════════════════════════════════════════════════════════
# Done!
# ═══════════════════════════════════════════════════════════════════════════════
Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║                                                                            ║" -ForegroundColor Green
Write-Host "║   ✅ BUILD HOÀN TẤT!                                                       ║" -ForegroundColor Green
Write-Host "║                                                                            ║" -ForegroundColor Green
Write-Host "║   APK: $targetPath" -ForegroundColor Green
Write-Host "║   Version: $versionName                                                    ║" -ForegroundColor Green
Write-Host "║                                                                            ║" -ForegroundColor Green
Write-Host "╚════════════════════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
