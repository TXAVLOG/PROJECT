# FILE BY TXA
# Contact: https://fb.com/vlog.txa.2311
# TXA Music - Windows PowerShell Build Script
# Cross-platform build system with pre-flight checks

param(
    [string]$BuildType = "release",
    [switch]$SkipUpload = $false,
    [switch]$Force = $false
)

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
function Write-Warning { Write-ColorOutput Yellow $args }
function Write-Error { Write-ColorOutput Red $args }

# Configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$EnvFile = Join-Path $ScriptDir ".env"
$KeyStoreFile = Join-Path $ProjectRoot "app\txamusic.keystore"
$BuildOutputDir = Join-Path $ProjectRoot "TXABUILD"

# Load environment variables
if (-not (Test-Path $EnvFile)) {
    Write-Error "Environment file not found: $EnvFile"
    Write-Error "Please copy .env.example to .env and update values"
    exit 1
}

# Read environment variables
$EnvContent = Get-Content $EnvFile | Where-Object { $_ -match "^(?!#).*=" }
foreach ($line in $EnvContent) {
    if ($line -match "^([^=]+)=(.*)$") {
        [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
    }
}

# Validate required environment variables
$RequiredVars = @("KEYSTORE_PASSWORD", "KEY_PASSWORD", "GIT_EMAIL", "GIT_NAME")
foreach ($var in $RequiredVars) {
    if (-not [System.Environment]::GetEnvironmentVariable($var)) {
        Write-Error "Required environment variable not set: $var"
        exit 1
    }
}

# Get version from version.properties
$VersionFile = Join-Path $ProjectRoot "version.properties"
if (-not (Test-Path $VersionFile)) {
    Write-Error "Version file not found: $VersionFile"
    exit 1
}

$VersionContent = Get-Content $VersionFile
$VersionCode = ($VersionContent | Where-Object { $_ -match "^versionCode=" }) -replace "versionCode=", ""
$VersionName = ($VersionContent | Where-Object { $_ -match "^versionName=" }) -replace "versionName=", ""

if (-not $VersionCode -or -not $VersionName) {
    Write-Error "Failed to read version from $VersionFile"
    exit 1
}

Write-Info "Building TXA Music v$VersionName ($VersionCode) - $BuildType"

# Pre-flight checks
function Test-Prerequisites {
    Write-Info "Running pre-flight checks..."
    
    # Check Java
    try {
        $JavaVersion = & java -version 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Java not found"
        }
        Write-Success "✓ Java found: $($JavaVersion[0])"
    } catch {
        Write-Error "✗ Java not installed or not in PATH"
        Write-Error "Please install Java 11 or higher"
        exit 1
    }
    
    # Check Gradle wrapper
    $GradlewPath = Join-Path $ProjectRoot "gradlew.bat"
    if (-not (Test-Path $GradlewPath)) {
        Write-Error "✗ Gradle wrapper not found: $GradlewPath"
        exit 1
    }
    Write-Success "✓ Gradle wrapper found"
    
    # Check Git
    try {
        & git --version > $null 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Git not found"
        }
        Write-Success "✓ Git found"
    } catch {
        Write-Error "✗ Git not installed or not in PATH"
        exit 1
    }
    
    # Check disk space (5GB minimum)
    $Drive = Get-PSDrive -Name C
    $FreeSpaceGB = [math]::Round($Drive.Free / 1GB, 2)
    if ($FreeSpaceGB -lt 5) {
        Write-Error "✗ Insufficient disk space. Required: 5GB, Available: $FreeSpaceGB GB"
        exit 1
    }
    Write-Success "✓ Disk space check passed: $FreeSpaceGB GB available"
    
    Write-Success "All pre-flight checks passed"
}

# Function to kill processes
function Stop-Processes {
    Write-Info "Stopping Java processes..."
    Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
    
    Write-Info "Stopping Gradle processes..."
    Get-Process -Name "gradle*" -ErrorAction SilentlyContinue | Stop-Process -Force
    
    # Stop Gradle daemon
    Set-Location $ProjectRoot
    & .\gradlew.bat --stop > $null 2>&1
}

# Function to clean Gradle cache
function Clear-GradleCache {
    Write-Info "Cleaning Gradle cache..."
    Set-Location $ProjectRoot
    
    # Clean project
    if ($Force) {
        Write-Info "Performing clean build..."
        & .\gradlew.bat clean
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Gradle clean failed"
            exit 1
        }
    }
    
    # Clean Gradle cache
    $UserHome = $env:USERPROFILE
    Remove-Item -Path "$UserHome\.gradle\caches" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -Path "$ProjectRoot\.gradle" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -Path "$ProjectRoot\build" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -Path "$ProjectRoot\app\build" -Recurse -Force -ErrorAction SilentlyContinue
    
    Write-Success "Gradle cache cleaned"
}

# Function to generate keystore
function New-Keystore {
    if (-not (Test-Path $KeyStoreFile)) {
        Write-Info "Generating keystore: $KeyStoreFile"
        
        $KeyStorePassword = [System.Environment]::GetEnvironmentVariable("KEYSTORE_PASSWORD")
        $KeyPassword = [System.Environment]::GetEnvironmentVariable("KEY_PASSWORD")
        
        & keytool -genkey `
            -v `
            -keystore $KeyStoreFile `
            -alias txamusic `
            -keyalg RSA `
            -keysize 2048 `
            -validity 10000 `
            -storepass $KeyStorePassword `
            -keypass $KeyPassword `
            -dname "CN=TXA Music, OU=TXA, O=TXAVLOG, L=Ho Chi Minh, ST=HCM, C=VN" `
            -noprompt
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Keystore generated successfully"
            
            # Create backup
            Write-Info "Creating initial keystore backup..."
            & (Join-Path $ScriptDir "TXABackupKeystore.ps1")
        } else {
            Write-Error "Failed to generate keystore"
            exit 1
        }
    } else {
        Write-Info "Keystore already exists: $KeyStoreFile"
    }
    
    # Verify keystore alias
    Test-KeystoreAlias
}

# Function to verify keystore alias
function Test-KeystoreAlias {
    if (Test-Path $KeyStoreFile) {
        Write-Info "Verifying keystore alias..."
        
        $KeyStorePassword = [System.Environment]::GetEnvironmentVariable("KEYSTORE_PASSWORD")
        
        $AliasCheck = & keytool -list -keystore $KeyStoreFile -storepass $KeyStorePassword -alias txamusic 2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Keystore alias verified: txamusic"
        } else {
            Write-Error "Keystore alias 'txamusic' not found or invalid password"
            Write-Error "Please check keystore file or regenerate with correct alias"
            exit 1
        }
    }
}

# Function to configure git
function Set-GitConfig {
    Write-Info "Configuring Git..."
    Set-Location $ProjectRoot
    
    $GitEmail = [System.Environment]::GetEnvironmentVariable("GIT_EMAIL")
    $GitName = [System.Environment]::GetEnvironmentVariable("GIT_NAME")
    
    & git config user.email $GitEmail
    & git config user.name $GitName
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Git configured"
    } else {
        Write-Error "Failed to configure Git"
        exit 1
    }
}

# Function to build APK
function New-APK {
    Write-Info "Building $BuildType APK..."
    Set-Location $ProjectRoot
    
    switch ($BuildType.ToLower()) {
        "debug" {
            & .\gradlew.bat assembleDebug
            $APKFile = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
        }
        "release" {
            & .\gradlew.bat assembleRelease
            $APKFile = Join-Path $ProjectRoot "app\build\outputs\apk\release\app-release.apk"
        }
        default {
            Write-Error "Invalid build type: $BuildType"
            exit 1
        }
    }
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Build failed"
        exit 1
    }
    
    if (-not (Test-Path $APKFile)) {
        Write-Error "APK file not found: $APKFile"
        exit 1
    }
    
    # Validate APK
    Test-APK $APKFile
    
    Write-Success "APK built and validated: $APKFile"
    return $APKFile
}

# Function to validate APK
function Test-APK {
    param([string]$APKFile)
    
    if (-not (Test-Path $APKFile)) {
        Write-Error "APK file not found: $APKFile"
        exit 1
    }
    
    # Check file size (should be at least 1MB)
    $FileSize = (Get-Item $APKFile).Length
    $MinSize = 1MB
    
    if ($FileSize -lt $MinSize) {
        Write-Error "APK file too small: $FileSize bytes (minimum: $MinSize bytes)"
        exit 1
    }
    
    # Check if it's a valid APK (ZIP archive)
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::OpenRead($APKFile) | Out-Null
        Write-Success "APK validation passed: $([math]::Round($FileSize/1MB, 2)) MB"
    } catch {
        Write-Error "APK file is not a valid ZIP archive"
        exit 1
    }
}

# Function to copy APK to output directory
function Copy-APK {
    param([string]$SourceAPK)
    
    Write-Info "Creating output directory..."
    if (-not (Test-Path $BuildOutputDir)) {
        New-Item -ItemType Directory -Path $BuildOutputDir -Force | Out-Null
    }
    
    $OutputFile = Join-Path $BuildOutputDir "TXAMusic-$VersionName-$BuildType.apk"
    
    Write-Info "Copying APK to: $OutputFile"
    Copy-Item $SourceAPK $OutputFile -Force
    
    if (Test-Path $OutputFile) {
        Write-Success "APK copied to: $OutputFile"
        return $OutputFile
    } else {
        Write-Error "Failed to copy APK"
        exit 1
    }
}

# Function to upload to repository
function Publish-Repository {
    param([string]$APKFile)
    
    $UploadToRepo = [System.Environment]::GetEnvironmentVariable("UPLOAD_TO_REPO")
    $UploadToGitHub = [System.Environment]::GetEnvironmentVariable("UPLOAD_TO_GITHUB")
    
    if ($SkipUpload -or $UploadToRepo -ne "true") {
        Write-Info "Skipping git repository upload"
    } else {
        Write-Info "Uploading to git repository..."
        Set-Location $ProjectRoot
        
        & git add TXABUILD\*.apk
        
        # Check if there are changes to commit
        $GitStatus = & git status --porcelain TXABUILD\*.apk
        if ($GitStatus) {
            & git commit -m "build: TXAMusic-$VersionName-$BuildType"
            
            & git push origin main
            
            if ($LASTEXITCODE -eq 0) {
                Write-Success "Uploaded to git repository"
            } else {
                Write-Error "Failed to upload to git repository"
                exit 1
            }
        } else {
            Write-Info "No changes to commit"
        }
    }
    
    # Upload to GitHub Releases
    if (-not $SkipUpload -and $UploadToGitHub -eq "true") {
        Write-Info "Uploading to GitHub Releases..."
        & (Join-Path $ScriptDir "TXAUploadToGitHub.ps1")
    }
}

# Function to display build summary
function Show-BuildSummary {
    param([string]$APKFile)
    
    Write-Success "=== BUILD COMPLETED ==="
    Write-Info "Version: $VersionName ($VersionCode)"
    Write-Info "Type: $BuildType"
    Write-Info "Output: $APKFile"
    Write-Info "Size: $([math]::Round((Get-Item $APKFile).Length/1MB, 2)) MB"
    
    if (-not $SkipUpload) {
        Write-Info "Repository: Updated"
    }
    
    Write-Success "======================"
}

# Main execution
try {
    Write-Info "Starting TXA Music build process..."
    
    # Check if we're in the right directory
    if (-not (Test-Path (Join-Path $ProjectRoot "build.gradle.kts"))) {
        Write-Error "Not in a valid Android project directory"
        exit 1
    }
    
    # Execute build steps
    Test-Prerequisites
    Stop-Processes
    Clear-GradleCache
    New-Keystore
    Set-GitConfig
    $BuiltAPK = New-APK
    $OutputAPK = Copy-APK $BuiltAPK
    Publish-Repository $OutputAPK
    Show-BuildSummary $OutputAPK
    
    Write-Success "Build process completed successfully!"
} catch {
    Write-Error "Build process failed: $($_.Exception.Message)"
    exit 1
}
