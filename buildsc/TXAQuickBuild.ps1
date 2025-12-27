# FILE BY TXA - Contact: fb.com/vlog.txa.2311
# TXA Music - Windows Quick Build + GitHub Release
# Usage: .\TXAQuickBuild.ps1 [-Release]
#
# Features:
# - Auto-read CHANGELOG.html for release notes
# - Dynamic version from version.properties  
# - GitHub Release with full description

param([switch]$Release)

$ErrorActionPreference = "Stop"

# Config
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$EnvFile = Join-Path $ScriptDir ".env"
$ChangelogFile = Join-Path $ProjectRoot "app\src\main\assets\CHANGELOG.html"
$BuildType = if ($Release) { "release" } else { "debug" }

# Colors
function Log-Info { Write-Host "[INFO] $args" -ForegroundColor Cyan }
function Log-Success { Write-Host "[SUCCESS] $args" -ForegroundColor Green }
function Log-Error { Write-Host "[ERROR] $args" -ForegroundColor Red }
function Log-Warning { Write-Host "[WARNING] $args" -ForegroundColor Yellow }

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

# Function to extract changelog from HTML
function Get-ChangelogFromHtml {
    if (-not (Test-Path $ChangelogFile)) {
        Log-Warning "CHANGELOG.html not found, using default"
        return "TXA Music v$VersionName - Cập nhật phiên bản mới."
    }
    
    Log-Info "Extracting changelog from CHANGELOG.html..."
    
    $htmlContent = Get-Content $ChangelogFile -Raw
    
    # Extract text from <li> tags
    $items = [regex]::Matches($htmlContent, '(?<=<li>)[^<]+') | ForEach-Object { $_.Value.Trim() } | Select-Object -First 15
    
    if ($items) {
        return $items -join "`n"
    } else {
        # Fallback: Remove all HTML tags
        $plainText = $htmlContent -replace '<[^>]+>', '' -replace '\s+', ' '
        return $plainText.Substring(0, [Math]::Min(500, $plainText.Length))
    }
}

# Function to generate release notes
function Get-ReleaseNotes {
    $notes = "## 📱 TXA Music v$VersionName`n`n"
    
    # Add changelog items
    $changelog = Get-ChangelogFromHtml
    if ($changelog) {
        $notes += "### 📝 Changelog:`n"
        $changelog -split "`n" | ForEach-Object {
            $line = $_.Trim()
            if ($line) {
                $notes += "- $line`n"
            }
        }
        $notes += "`n"
    }
    
    # Add build info
    $gitCommit = (& git rev-parse --short HEAD 2>$null) -join ""
    if (-not $gitCommit) { $gitCommit = "N/A" }
    
    $notes += "### 📦 Build Info:`n"
    $notes += "- Build Type: ``$BuildType```n"
    $notes += "- Version Code: ``$VersionCode```n"
    $notes += "- Build Date: ``$(Get-Date -Format 'yyyy-MM-dd HH:mm')```n"
    $notes += "- Git Commit: ``$gitCommit```n"
    
    return $notes
}

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
    
    # 2. Generate release notes
    $ReleaseNotes = Get-ReleaseNotes
    Log-Info "Generated release notes:"
    Write-Host $ReleaseNotes
    
    # Save to file
    $ReleaseNotesFile = Join-Path $BuildDir "RELEASE_NOTES.md"
    $ReleaseNotes | Out-File -FilePath $ReleaseNotesFile -Encoding UTF8
    
    # 3. GIT PUSH
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
    & git push origin "$Branch`:main" --force
    
    if ($LASTEXITCODE -eq 0) {
        Log-Success "Pushed to branch: $Branch"
    } else {
        Log-Error "Git push failed (continuing...)"
    }
    
    # 4. GITHUB RELEASE
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
    
    # Create or update release with dynamic notes
    $releaseExists = & gh release view $Tag 2>$null
    if ($LASTEXITCODE -eq 0) {
        Log-Info "Updating release: $Tag"
        & gh release edit $Tag --title $Title --notes $ReleaseNotes
    } else {
        Log-Info "Creating release: $Tag"
        & gh release create $Tag --title $Title --notes $ReleaseNotes
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
