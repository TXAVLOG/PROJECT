# FILE BY TXA
# Contact: https://fb.com/vlog.txa.2311
# TXA Demo - Windows PowerShell GitHub Releases Upload Script

# Error handling
$ErrorActionPreference = "Stop"

# Colors for output
function Write-ColorOutput($ForegroundColor) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    if ($args) {
        Write-Output $args
    }
    $host.UI.RawUI.ForegroundColor = $fc
}

function Write-Success { Write-ColorOutput Green $args }
function Write-Info { Write-ColorOutput Cyan $args }
function Write-Error { Write-ColorOutput Red $args }

# Configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$BuildDir = Join-Path $ProjectRoot "TXABUILD"
$EnvFile = Join-Path $ScriptDir ".env"

# Load environment
if (-not (Test-Path $EnvFile)) {
    Write-Error "Environment file not found: $EnvFile"
    exit 1
}

# Read environment variables
$EnvContent = Get-Content $EnvFile | Where-Object { $_ -match "^(?!#).*=" }
foreach ($line in $EnvContent) {
    if ($line -match "^([^=]+)=(.*)$") {
        [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
    }
}

# Get version info
$VersionFile = Join-Path $ProjectRoot "version.properties"
$VersionName = ((Get-Content $VersionFile | Where-Object { $_ -match "^versionName=" }) -replace "versionName=", "").Trim()
$VersionCode = ((Get-Content $VersionFile | Where-Object { $_ -match "^versionCode=" }) -replace "versionCode=", "").Trim()

# Check if GitHub CLI is installed
try {
    & gh --version > $null 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub CLI not found"
    }
} catch {
    Write-Error "GitHub CLI (gh) not installed"
    Write-Info "Install with: winget install GitHub.cli"
    exit 1
}

# Check authentication
try {
    & gh auth status > $null 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub CLI not authenticated"
    }
} catch {
    Write-Error "GitHub CLI not authenticated"
    Write-Info "Run: gh auth login"
    exit 1
}

# Function to upload APK to GitHub Release
function Publish-GitHubRelease {
    param([string]$APKFile, [string]$BuildType)
    
    Write-Info "Uploading to GitHub Release..."
    
    # Create or update release
    $ReleaseTag = "v$VersionName"
    $ReleaseName = "TXA Demo v$VersionName ($BuildType)"
    $ReleaseNotes = @"
TXA Demo v$VersionName ($BuildType) build

Version: $VersionName
Build Code: $VersionCode
Type: $BuildType
Built: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')

Features:
- OTA Translation System
- Extended URL Resolver (MediaFire, GitHub, Google Drive)
- File Manager UI
- Legacy Storage Support (Android 9)
- Multi-language Support
"@
    
    # Check if release exists
    try {
        & gh release view $ReleaseTag > $null 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Info "Updating existing release: $ReleaseTag"
            & gh release edit $ReleaseTag --title $ReleaseName --notes $ReleaseNotes
        }
    } catch {
        Write-Info "Creating new release: $ReleaseTag"
        & gh release create $ReleaseTag --title $ReleaseName --notes $ReleaseNotes
    }
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to create/update release"
        exit 1
    }
    
    # Upload APK
    $APKBasename = Split-Path $APKFile -Leaf
    Write-Info "Uploading $APKBasename..."
    
    & gh release upload $ReleaseTag $APKFile --clobber
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "APK uploaded to GitHub Release"
        Write-Info "Download URL: https://github.com/TXAVLOG/PROJECT/releases/download/$ReleaseTag/$APKBasename"
    } else {
        Write-Error "Failed to upload APK"
        exit 1
    }
}

# Main execution
try {
    Write-Info "GitHub Release Upload for TXA Demo v$VersionName"
    
    # Find APK files
    if (-not (Test-Path $BuildDir)) {
        Write-Error "Build directory not found: $BuildDir"
        exit 1
    }
    
    # Upload all APK files
    $APKFiles = Get-ChildItem -Path $BuildDir -Filter "*.apk"
    if ($APKFiles.Count -eq 0) {
        Write-Error "No APK files found in $BuildDir"
        exit 1
    }
    
    foreach ($APKFile in $APKFiles) {
        $Filename = $APKFile.Name
        if ($Filename -match "-(debug|release)\.apk$") {
            $BuildType = $matches[1]
            Write-Info "Processing: $Filename ($BuildType)"
            Publish-GitHubRelease -APKFile $APKFile.FullName -BuildType $BuildType
        }
    }
    
    Write-Success "All APKs uploaded to GitHub Releases"
} catch {
    Write-Error "Upload process failed: $($_.Exception.Message)"
    exit 1
}
