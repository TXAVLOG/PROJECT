# Script to resize and copy app icons
# Requires magick (ImageMagick) installed and in PATH

$sourceLogo = "tools/logo_source.png"
$resDir = "app/src/main/res"

if (-not (Test-Path $sourceLogo)) {
    Write-Host "Source logo not found at $sourceLogo"
    exit 1
}

# Android Icon Sizes
$sizes = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

# Round Icon Sizes
$roundSizes = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

# Ensure directories exist
foreach ($folder in $sizes.Keys) {
    if (-not (Test-Path "$resDir/$folder")) {
        New-Item -ItemType Directory -Path "$resDir/$folder" | Out-Null
    }
}

# Process Rectangular Icons (launcher)
Write-Host "Processing Standard Icons..."
foreach ($entry in $sizes.GetEnumerator()) {
    $folder = $entry.Key
    $size = $entry.Value
    $targetPath = "$resDir/$folder/ic_launcher.png"
    
    # Use magick to resize
    magick "$sourceLogo" -resize "${size}x${size}" "$targetPath"
    Write-Host "Created $targetPath ($size px)"
}

# Process Round Icons (launcher_round)
# Simplified simple resize to avoid Powershell parsing issues with parens
Write-Host "Processing Round Icons..."
foreach ($entry in $roundSizes.GetEnumerator()) {
    $folder = $entry.Key
    $size = $entry.Value
    $targetPath = "$resDir/$folder/ic_launcher_round.png"
    
    # Just resize for now, circular mask is tricky in PS one-liner without specific syntax
    magick "$sourceLogo" -resize "${size}x${size}" "$targetPath"
    
    Write-Host "Created $targetPath ($size px)"
}

# Create a large png for drawable (fallback)
magick "$sourceLogo" -resize "512x512" "app/src/main/res/drawable/ic_launcher.png"
magick "$sourceLogo" -resize "512x512" "app/src/main/res/drawable/ic_launcher_round.png"

Write-Host "Icon processing complete."
