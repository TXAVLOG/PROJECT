# FILE BY TXA
# Contact: https://fb.com/vlog.txa.2311
# TXA Demo - Windows PowerShell Quick Build Script

param(
    [string]$BuildType = "debug"
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
function Write-Error { Write-ColorOutput Red $args }

# Validate build type
if ($BuildType -notin @("debug", "release")) {
    Write-Error "Invalid build type: $BuildType. Use 'debug' or 'release'"
    exit 1
}

# Configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
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

# Get version
$VersionFile = Join-Path $ProjectRoot "version.properties"
$VersionName = ((Get-Content $VersionFile | Where-Object { $_ -match "^versionName=" }) -replace "versionName=", "").Trim()
$VersionCode = ((Get-Content $VersionFile | Where-Object { $_ -match "^versionCode=" }) -replace "versionCode=", "").Trim()

Write-Info "Quick Build: TXA Demo v$VersionName ($VersionCode) - $BuildType"

# Pre-flight checks
function Test-Prerequisites {
    Write-Info "Running pre-flight checks..."
    
    # Check Java
    try {
        & java -version > $null 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Java not found"
        }
        Write-Success "✓ Java found"
    } catch {
        Write-Error "✗ Java not installed"
        exit 1
    }
    
    # Check Gradle wrapper
    $GradlewPath = Join-Path $ProjectRoot "gradlew.bat"
    if (-not (Test-Path $GradlewPath)) {
        Write-Error "✗ Gradle wrapper not found"
        exit 1
    }
    Write-Success "✓ Gradle wrapper found"
    
    Write-Success "Pre-flight checks passed"
}

# Kill processes
function Stop-Processes {
    Write-Info "Stopping background processes..."
    Set-Location $ProjectRoot
    & .\gradlew.bat --stop > $null 2>&1
    Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
    Get-Process -Name "gradle*" -ErrorAction SilentlyContinue | Stop-Process -Force
}

# Build APK
function New-APK {
    Write-Info "Building $BuildType APK..."
    Set-Location $ProjectRoot
    
    switch ($BuildType.ToLower()) {
        "debug" {
            & .\gradlew.bat clean assembleDebug
            $APKFile = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
        }
        "release" {
            & .\gradlew.bat clean assembleRelease
            $APKFile = Join-Path $ProjectRoot "app\build\outputs\apk\release\app-release.apk"
        }
    }
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Build failed"
        exit 1
    }
    
    if (-not (Test-Path $APKFile)) {
        Write-Error "Build failed - APK not found: $APKFile"
        exit 1
    }
    
    return $APKFile
}

# Copy to output
function Copy-APK {
    param([string]$SourceAPK)
    
    $BuildDir = Join-Path $ProjectRoot "TXABUILD"
    if (-not (Test-Path $BuildDir)) {
        New-Item -ItemType Directory -Path $BuildDir -Force | Out-Null
    }
    
    $OutputFile = Join-Path $BuildDir "TXADEMO-$VersionName-$BuildType.apk"
    Copy-Item $SourceAPK $OutputFile -Force
    
    return $OutputFile
}

# Git upload
function Publish-Repository {
    param([string]$APKFile)
    
    $GitEmail = [System.Environment]::GetEnvironmentVariable("GIT_EMAIL")
    $GitName = [System.Environment]::GetEnvironmentVariable("GIT_NAME")
    
    Set-Location $ProjectRoot
    & git config user.email $GitEmail
    & git config user.name $GitName
    
    & git add $APKFile
    & git commit -m "build: TXADEMO-$VersionName-$BuildType" > $null 2>&1
    & git push origin main > $null 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Uploaded to repository"
    }
}

# Main execution
try {
    Test-Prerequisites
    Stop-Processes
    $BuiltAPK = New-APK
    $OutputAPK = Copy-APK $BuiltAPK
    Publish-Repository $OutputAPK
    
    Write-Success "Build completed!"
    Write-Info "Output: $OutputAPK"
    Write-Info "Size: $([math]::Round((Get-Item $OutputAPK).Length/1MB, 2)) MB"
} catch {
    Write-Error "Build failed: $($_.Exception.Message)"
    exit 1
}
