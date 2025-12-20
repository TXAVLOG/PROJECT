# FILE BY TXA
# TXA Demo - Image Processing Script
# Contact: fb.com/vlog.txa.2311
# 
# This script resizes and copies app icons to appropriate Android resource folders
# Requires: Master images in design/master/ folder
#   - ic_launcher.png (512x512)
#   - splash_logo.png (512x512)
#   - ic_notification.png (96x96 monochrome)

param(
    [string]$SourceRoot,
    [string]$LauncherPath,
    [string]$SplashPath,
    [string]$NotificationPath,
    [switch]$SkipResize = $false,
    [switch]$Verbose = $false
)

# Color output functions
function Write-ColorOutput($ForegroundColor) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    if ($args) {
        Write-Host $args
    } else {
        Write-Host ""
    }
    $host.UI.RawUI.ForegroundColor = $fc
}

function Write-Success { Write-ColorOutput Green $args }
function Write-Info { Write-ColorOutput Cyan $args }
function Write-Warning { Write-ColorOutput Yellow $args }
function Write-Error { Write-ColorOutput Red $args }

# Path helpers
function Resolve-AbsolutePath {
    param(
        [string]$PathValue,
        [string]$BaseDirectory
    )
    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $null
    }
    try {
        if (Test-Path $PathValue) {
            return (Resolve-Path $PathValue).Path
        }
    } catch {
        # swallow and try relative resolution
    }
    if ($BaseDirectory) {
        $combinedPath = Join-Path $BaseDirectory $PathValue
        if (Test-Path $combinedPath) {
            return (Resolve-Path $combinedPath).Path
        }
    }
    return $null
}

function Get-ImagePath {
    param(
        [string]$CustomPath,
        [string]$DefaultFileName,
        [string]$DefaultDirectory
    )

    if ($CustomPath) {
        $resolvedCustom = Resolve-AbsolutePath -PathValue $CustomPath -BaseDirectory $ProjectRoot
        if ($resolvedCustom -and (Test-Path $resolvedCustom)) {
            Write-Info "Using custom file for ${DefaultFileName}: $resolvedCustom"
            return $resolvedCustom
        } else {
            Write-Warning "Custom file not found (${CustomPath}). Falling back to default directory."
        }
    }
    return Join-Path $DefaultDirectory $DefaultFileName
}

# Script configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$DefaultMasterDir = Join-Path $ProjectRoot "design\master"

if ($SourceRoot) {
    $ResolvedSourceRoot = Resolve-AbsolutePath -PathValue $SourceRoot -BaseDirectory $ProjectRoot
    if (-not $ResolvedSourceRoot) {
        Write-Error "Specified SourceRoot not found: $SourceRoot"
        exit 1
    }
    $MasterDir = $ResolvedSourceRoot
} else {
    $MasterDir = $DefaultMasterDir
}

$ResDir = Join-Path $ProjectRoot "app\src\main\res"

Write-Info "=========================================="
Write-Info "  TXA Demo - Image Processing Script"
Write-Info "=========================================="
Write-Info ""

# Check if master directory exists (only warn if custom paths provided)
if (-not (Test-Path $MasterDir)) {
    Write-Warning "Master images directory not found: $MasterDir"
    Write-Warning "Provide SourceRoot or explicit file paths to continue."
}

# Image size configurations
$LauncherSizes = @{
    "mdpi"    = 48
    "hdpi"    = 72
    "xhdpi"   = 96
    "xxhdpi"  = 144
    "xxxhdpi" = 192
}

$NotificationSizes = @{
    "mdpi"    = 24
    "hdpi"    = 36
    "xhdpi"   = 48
    "xxhdpi"  = 72
    "xxxhdpi" = 96
}

# Function to check if ImageMagick is installed
function Test-ImageMagick {
    try {
        $null = magick -version 2>&1
        return $true
    } catch {
        return $false
    }
}

# Function to resize image using ImageMagick
function Resize-ImageMagick {
    param(
        [string]$SourcePath,
        [string]$DestPath,
        [int]$Size
    )
    
    $destDir = Split-Path -Parent $DestPath
    if (-not (Test-Path $destDir)) {
        New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    }
    
    try {
        magick convert "$SourcePath" -resize "${Size}x${Size}" "$DestPath" 2>&1 | Out-Null
        return $true
    } catch {
        Write-Error "Failed to resize: $_"
        return $false
    }
}

# Function to resize image using .NET (fallback)
function Resize-ImageDotNet {
    param(
        [string]$SourcePath,
        [string]$DestPath,
        [int]$Size
    )
    
    $destDir = Split-Path -Parent $DestPath
    if (-not (Test-Path $destDir)) {
        New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    }
    
    try {
        Add-Type -AssemblyName System.Drawing
        
        $img = [System.Drawing.Image]::FromFile($SourcePath)
        $bitmap = New-Object System.Drawing.Bitmap($Size, $Size)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        
        $graphics.DrawImage($img, 0, 0, $Size, $Size)
        
        $bitmap.Save($DestPath, [System.Drawing.Imaging.ImageFormat]::Png)
        
        $graphics.Dispose()
        $bitmap.Dispose()
        $img.Dispose()
        
        return $true
    } catch {
        Write-Error "Failed to resize with .NET: $_"
        return $false
    }
}

# Main resize function
function Resize-Image {
    param(
        [string]$SourcePath,
        [string]$DestPath,
        [int]$Size
    )
    
    if ($UseImageMagick) {
        return Resize-ImageMagick -SourcePath $SourcePath -DestPath $DestPath -Size $Size
    } else {
        return Resize-ImageDotNet -SourcePath $SourcePath -DestPath $DestPath -Size $Size
    }
}

# Check for ImageMagick
$UseImageMagick = Test-ImageMagick
if ($UseImageMagick) {
    Write-Success "✓ ImageMagick detected - using for high-quality resizing"
} else {
    Write-Warning "⚠ ImageMagick not found - using .NET fallback (lower quality)"
    Write-Info "  Install ImageMagick for better results: https://imagemagick.org/script/download.php"
}
Write-Info ""

# Process launcher icons
$launcherMaster = Get-ImagePath -CustomPath $LauncherPath -DefaultFileName "ic_launcher.png" -DefaultDirectory $MasterDir
if (Test-Path $launcherMaster) {
    Write-Info "Processing launcher icons..."
    
    foreach ($density in $LauncherSizes.Keys) {
        $size = $LauncherSizes[$density]
        $destDir = Join-Path $ResDir "mipmap-$density"
        $destPath = Join-Path $destDir "ic_launcher.png"
        
        if ($SkipResize -and (Test-Path $destPath)) {
            Write-Info "  ⊳ Skipping mipmap-$density (${size}x${size}) - already exists"
            continue
        }
        
        Write-Info "  → Resizing to mipmap-$density (${size}x${size})..."
        
        if (Resize-Image -SourcePath $launcherMaster -DestPath $destPath -Size $size) {
            Write-Success "    ✓ Created: $destPath"
        } else {
            Write-Error "    ✗ Failed: $destPath"
        }
    }
    
    # Also create round icon
    foreach ($density in $LauncherSizes.Keys) {
        $size = $LauncherSizes[$density]
        $destDir = Join-Path $ResDir "mipmap-$density"
        $destPath = Join-Path $destDir "ic_launcher_round.png"
        
        if ($SkipResize -and (Test-Path $destPath)) {
            continue
        }
        
        if (Resize-Image -SourcePath $launcherMaster -DestPath $destPath -Size $size) {
            if ($Verbose) {
                Write-Success "    ✓ Created round: $destPath"
            }
        }
    }
    
    Write-Success "✓ Launcher icons processed"
    Write-Info ""
} else {
    Write-Warning "⚠ Launcher icon not found: $launcherMaster"
    Write-Info ""
}

# Process splash logo
$splashMaster = Get-ImagePath -CustomPath $SplashPath -DefaultFileName "splash_logo.png" -DefaultDirectory $MasterDir
if (Test-Path $splashMaster) {
    Write-Info "Processing splash logo..."
    
    # Splash logo goes to drawable folders at fixed size (360px for 120dp @ xhdpi)
    $splashSizes = @{
        "mdpi"    = 120
        "hdpi"    = 180
        "xhdpi"   = 240
        "xxhdpi"  = 360
        "xxxhdpi" = 480
    }
    
    foreach ($density in $splashSizes.Keys) {
        $size = $splashSizes[$density]
        $destDir = Join-Path $ResDir "drawable-$density"
        $destPath = Join-Path $destDir "splash_logo.png"
        
        if ($SkipResize -and (Test-Path $destPath)) {
            Write-Info "  ⊳ Skipping drawable-$density (${size}x${size}) - already exists"
            continue
        }
        
        Write-Info "  → Resizing to drawable-$density (${size}x${size})..."
        
        if (Resize-Image -SourcePath $splashMaster -DestPath $destPath -Size $size) {
            Write-Success "    ✓ Created: $destPath"
        } else {
            Write-Error "    ✗ Failed: $destPath"
        }
    }
    
    Write-Success "✓ Splash logo processed"
    Write-Info ""
} else {
    Write-Warning "⚠ Splash logo not found: $splashMaster"
    Write-Info ""
}

# Process notification icon
$notificationMaster = Get-ImagePath -CustomPath $NotificationPath -DefaultFileName "ic_notification.png" -DefaultDirectory $MasterDir
if (Test-Path $notificationMaster) {
    Write-Info "Processing notification icon..."
    
    foreach ($density in $NotificationSizes.Keys) {
        $size = $NotificationSizes[$density]
        $destDir = Join-Path $ResDir "drawable-$density"
        $destPath = Join-Path $destDir "ic_notification.png"
        
        if ($SkipResize -and (Test-Path $destPath)) {
            Write-Info "  ⊳ Skipping drawable-$density (${size}x${size}) - already exists"
            continue
        }
        
        Write-Info "  → Resizing to drawable-$density (${size}x${size})..."
        
        if (Resize-Image -SourcePath $notificationMaster -DestPath $destPath -Size $size) {
            Write-Success "    ✓ Created: $destPath"
        } else {
            Write-Error "    ✗ Failed: $destPath"
        }
    }
    
    Write-Success "✓ Notification icon processed"
    Write-Info ""
} else {
    Write-Warning "⚠ Notification icon not found: $notificationMaster"
    Write-Info ""
}

# Summary
Write-Info "=========================================="
Write-Success "✓ Image processing complete!"
Write-Info "=========================================="
Write-Info ""
Write-Info "Next steps:"
Write-Info "  1. Review generated images in app/src/main/res/"
Write-Info "  2. Update splash layout to use splash_logo drawable"
Write-Info "  3. Build and test the app"
Write-Info ""
Write-Info "Master images location: $MasterDir"
Write-Info "Resource directory: $ResDir"
Write-Info ""
