# FILE BY TXA - Contact: fb.com/vlog.txa.2311
# TXA Music - Windows Quick Build + GitHub Release
# Usage: .\TXAQuickBuild.ps1 [-Release]

param([switch]$Release)

$ErrorActionPreference = "Stop"

# Config
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$EnvFile = Join-Path $ScriptDir ".env"
$BuildType = if ($Release) { "release" } else { "debug" }

# Colors
function Log-Info { Write-Host "[INFO] $args" -ForegroundColor Cyan }
function Log-Success { Write-Host "[SUCCESS] $args" -ForegroundColor Green }
function Log-Error { Write-Host "[ERROR] $args" -ForegroundColor Red }

# Load .env
if (Test-Path $EnvFile) {
    Get-Content $EnvFile | Where-Object { $_ -match "^[^#].*=" } | ForEach-Object {
        if ($_ -match "^([^=]+)=(.*)$") {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
        }
    }
} else {
    Log-Error ".env not found: $EnvFile"
    exit 1
}

# Get version
$VersionFile = Join-Path $ProjectRoot "version.properties"
$VersionName = (Get-Content $VersionFile | Where-Object { $_ -match "^versionName=" }) -replace "versionName=", ""
$VersionCode = (Get-Content $VersionFile | Where-Object { $_ -match "^versionCode=" }) -replace "versionCode=", ""

Log-Info "=== TXA Music v$VersionName ($VersionCode) - $BuildType ==="

try {
    # 1. BUILD
    Log-Info "Building $BuildType APK..."
    Set-Location $ProjectRoot
    
    if ($BuildType -eq "release") {
        & .\gradlew.bat clean assembleRelease
        $APKSource = "app\build\outputs\apk\release\app-release.apk"
    } else {
        & .\gradlew.bat clean assembleDebug
        $APKSource = "app\build\outputs\apk\debug\app-debug.apk"
    }
    
    if ($LASTEXITCODE -ne 0) { throw "Build failed" }
    
    $APKPath = Join-Path $ProjectRoot $APKSource
    if (-not (Test-Path $APKPath)) { throw "APK not found: $APKPath" }
    
    # Copy to TXABUILD
    $BuildDir = Join-Path $ProjectRoot "TXABUILD"
    New-Item -ItemType Directory -Path $BuildDir -Force | Out-Null
    $OutputFile = Join-Path $BuildDir "TXAMusic-$VersionName-$BuildType.apk"
    Copy-Item $APKPath $OutputFile -Force
    
    $APKSize = [math]::Round((Get-Item $OutputFile).Length / 1MB, 2)
    Log-Success "Build successful! Size: $APKSize MB"
    
    # 2. GIT PUSH
    Log-Info "Pushing to Git..."
    $GitEmail = $env:GIT_EMAIL
    $GitName = $env:GIT_NAME
    
    if ($GitEmail -and $GitName) {
        & git config user.email $GitEmail
        & git config user.name $GitName
    }
    
    & git add .
    & git commit -m "build: TXAMusic-$VersionName-$BuildType" 2>$null
    
    $Branch = (& git rev-parse --abbrev-ref HEAD).Trim()
    & git push origin $Branch
    
    if ($LASTEXITCODE -eq 0) {
        Log-Success "Pushed to branch: $Branch"
    } else {
        Log-Error "Git push failed (continuing...)"
    }
    
    # 3. GITHUB RELEASE
    Log-Info "Uploading to GitHub Release..."
    
    # Check gh CLI
    $ghExists = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $ghExists) {
        Log-Error "GitHub CLI not installed. Install: winget install GitHub.cli"
        exit 1
    }
    
    & gh auth status 2>$null
    if ($LASTEXITCODE -ne 0) {
        Log-Error "GitHub CLI not authenticated. Run: gh auth login"
        exit 1
    }
    
    $Tag = "v$VersionName"
    $Title = "TXA Music v$VersionName"
    $Notes = @"
TXA Music v$VersionName

- Build Type: $BuildType
- Version Code: $VersionCode
- Built: $(Get-Date -Format 'yyyy-MM-dd HH:mm')

Features:
- Music Player with Media3
- OTA Translation System
- Auto Update System
"@

    # Create or update release
    $releaseExists = & gh release view $Tag 2>$null
    if ($LASTEXITCODE -eq 0) {
        Log-Info "Updating release: $Tag"
        & gh release edit $Tag --title $Title --notes $Notes
    } else {
        Log-Info "Creating release: $Tag"
        & gh release create $Tag --title $Title --notes $Notes
    }
    
    # Upload APK
    $APKName = Split-Path $OutputFile -Leaf
    & gh release upload $Tag $OutputFile --clobber
    
    if ($LASTEXITCODE -eq 0) {
        Log-Success "Uploaded: $APKName"
        Log-Info "Download: https://github.com/TXAVLOG/PROJECT/releases/download/$Tag/$APKName"
    } else {
        Log-Error "Upload failed!"
        exit 1
    }
    
    Log-Success "=== BUILD COMPLETE ==="
    
} catch {
    Log-Error $_.Exception.Message
    exit 1
}
