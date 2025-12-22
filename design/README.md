# 🎨 TXA Music - Design Assets (Target SDK 33 / Android 13)

## 📁 Folder Structure

```
design/
├── master/                    # Master high-resolution images (place here)
│   ├── ic_launcher.png       # 512x512 - App launcher icon
│   ├── splash_logo.png       # 512x512 - Splash screen logo
│   └── ic_notification.png   # 96x96 - Notification icon (monochrome)
│
├── PROMPT_FOR_NANO_BANNA.md  # Design requirements for designer
└── README.md                 # This file
```

## 🚀 Quick Start

### 1. Get Master Images from Designer

Send `PROMPT_FOR_NANO_BANNA.md` to your designer (Nano Banna) to get:
- **ic_launcher.png** (512x512) - App icon
- **splash_logo.png** (512x512) - Splash logo with transparency
- **ic_notification.png** (96x96) - Monochrome notification icon

### 2. Place Master Images

Put the received images in `design/master/` folder:
```
design/master/ic_launcher.png
design/master/splash_logo.png
design/master/ic_notification.png
```

### 3. Run Processing Script

Execute PowerShell script to resize and copy images:

```powershell
# From project root
.\tools\TXAProcessImages.ps1

# Or with verbose output
.\tools\TXAProcessImages.ps1 -Verbose

# Skip existing files
.\tools\TXAProcessImages.ps1 -SkipResize
```

### 4. Verify Results

Check generated images in:
```
app/src/main/res/
├── mipmap-mdpi/ic_launcher.png (48x48)
├── mipmap-hdpi/ic_launcher.png (72x72)
├── mipmap-xhdpi/ic_launcher.png (96x96)
├── mipmap-xxhdpi/ic_launcher.png (144x144)
├── mipmap-xxxhdpi/ic_launcher.png (192x192)
├── drawable-mdpi/splash_logo.png (120x120)
├── drawable-hdpi/splash_logo.png (180x180)
├── drawable-xhdpi/splash_logo.png (240x240)
├── drawable-xxhdpi/splash_logo.png (360x360)
├── drawable-xxxhdpi/splash_logo.png (480x480)
├── drawable-mdpi/ic_notification.png (24x24)
├── drawable-hdpi/ic_notification.png (36x36)
├── drawable-xhdpi/ic_notification.png (48x48)
├── drawable-xxhdpi/ic_notification.png (72x72)
└── drawable-xxxhdpi/ic_notification.png (96x96)
```

## 📐 Image Specifications

### App Launcher Icon (ic_launcher.png)
- **Master Size**: 512x512 px
- **Format**: PNG
- **Style**: Modern tech, "TXA" logo, cyan (#00D9FF) on dark navy (#1A1A2E)
- **Background**: Can have background color
- **Generated Sizes**: mdpi (48), hdpi (72), xhdpi (96), xxhdpi (144), xxxhdpi (192)

### Splash Logo (splash_logo.png)
- **Master Size**: 512x512 px
- **Format**: PNG with transparency
- **Style**: Larger detailed logo with glow effect
- **Background**: Transparent
- **Usage**: Centered on splash screen with progress bar below
- **Generated Sizes**: mdpi (120), hdpi (180), xhdpi (240), xxhdpi (360), xxxhdpi (480)

### Notification Icon (ic_notification.png)
- **Master Size**: 96x96 px
- **Format**: PNG with transparency
- **Style**: **MONOCHROME** - white silhouette on transparent
- **Requirements**: No gradients, no colors, simple shape
- **Usage**: Android status bar notifications
- **Generated Sizes**: mdpi (24), hdpi (36), xhdpi (48), xxhdpi (72), xxxhdpi (96)

## 🎨 Design Guidelines

### Color Palette
```css
Primary:      #00D9FF (Cyan Blue)
Primary Dark: #0099CC
Secondary:    #FF6B6B (Coral Red)
Background:   #1A1A2E (Dark Navy)
Card BG:      #252540 (Dark Purple)
Text:         #FFFFFF (White)
Text Dim:     #AAAAAA (Gray)
```

### Visual Style
- Dark theme with neon accents
- Minimalist and professional
- Tech/Cyber aesthetic
- Subtle gradients and glows

## 🛠️ Tools

### PowerShell Script Features
- Automatic resizing for all Android densities
- Supports ImageMagick (high quality) or .NET fallback
- Creates all necessary folders
- Preserves PNG transparency
- Batch processing

### Requirements
- **Windows PowerShell** 5.1 or later
- **Optional**: ImageMagick for better quality
  - Download: https://imagemagick.org/script/download.php
  - Add to PATH after installation

### Script Usage
```powershell
# Basic usage (mặc định lấy ảnh trong design/master)
.\tools\TXAProcessImages.ps1

# Chỉ định thư mục nguồn khác (tương đối hoặc tuyệt đối)
.\tools\TXAProcessImages.ps1 -SourceRoot "C:\Assets\TXA"

# Chỉ định file riêng lẻ (ưu tiên hơn SourceRoot)
.\tools\TXAProcessImages.ps1 `
    -LauncherPath ".\logo.png" `
    -SplashPath "D:\Design\splash.png" `
    -NotificationPath "..\noti.png"

# Show detailed output
.\tools\TXAProcessImages.ps1 -Verbose

# Skip files that already exist
.\tools\TXAProcessImages.ps1 -SkipResize

# Get help
Get-Help .\tools\TXAProcessImages.ps1 -Full
```

## 📝 Notes

1. **Master images** should be high resolution (512x512 or larger)
2. **Notification icon** MUST be monochrome (white on transparent)
3. **Splash logo** should have transparent background
4. Script automatically creates all required Android densities
5. Run script whenever master images are updated

## 🔗 Resources

- **Design Prompt**: `PROMPT_FOR_NANO_BANNA.md`
- **Processing Script**: `../tools/TXAProcessImages.ps1`
- **Android Icon Guidelines**: https://developer.android.com/guide/practices/ui_guidelines/icon_design_launcher
- **Material Design Icons**: https://material.io/design/iconography

---

**Contact**: TXAVLOG  
**Facebook**: fb.com/vlog.txa.2311  
**Project**: https://github.com/TXAVLOG/PROJECT
