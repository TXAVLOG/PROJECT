# 🎨 TXA Demo App - Image Design Requirements for Nano Banna

## 📱 App Information
- **App Name**: TXA Demo
- **Package**: gc.txa.demo
- **Theme**: Dark modern tech style
- **Primary Color**: #00D9FF (Cyan Blue)
- **Secondary Color**: #FF6B6B (Red)
- **Background**: #1A1A2E (Dark Navy)
- **Accent**: #252540 (Dark Purple)

---

## 🖼️ Image Requirements

### 1. App Launcher Icon (ic_launcher.png)
**Purpose**: Main app icon displayed on device home screen

**Design Requirements**:
- Modern, tech-focused design
- Include "TXA" text or logo
- Gradient from #00D9FF to #0099CC
- Dark background (#1A1A2E)
- Clean, minimalist style
- Professional appearance

**Sizes Needed**:
- `mipmap-mdpi`: 48x48 px
- `mipmap-hdpi`: 72x72 px
- `mipmap-xhdpi`: 96x96 px
- `mipmap-xxhdpi`: 144x144 px
- `mipmap-xxxhdpi`: 192x192 px

**Design Style**:
```
- Rounded square shape with slight gradient
- "TXA" text in bold, modern font
- Optional: Circuit board pattern or download arrow icon
- Colors: Cyan (#00D9FF) on dark navy (#1A1A2E)
```

---

### 2. Splash Screen Logo (splash_logo.png)
**Purpose**: Centered logo displayed during app initialization

**Design Requirements**:
- Larger, more detailed version of app icon
- Can include tagline: "OTA Update System"
- Transparent background (PNG with alpha)
- Optimized for dark background display
- Should look good with progress bar below

**Size**: 
- **512x512 px** (will be scaled down to 120dp in layout)
- Export as high-quality PNG with transparency

**Design Style**:
```
- Circular or rounded square badge
- "TXA" in large, bold letters
- Glowing effect around edges (#00D9FF glow)
- Optional: Download/update icon integrated
- Subtitle: "DEMO" in smaller text
- Modern, tech aesthetic with gradients
```

**Layout Context**:
```
┌─────────────────────┐
│                     │
│                     │
│    [SPLASH LOGO]    │ ← Your logo here (120dp = ~360px)
│                     │
│  "Initializing..."  │ ← Text below logo
│   [Progress Bar]    │ ← Progress indicator
│                     │
└─────────────────────┘
```

---

### 3. Notification Icon (ic_notification.png)
**Purpose**: Small icon shown in Android status bar for update notifications

**Design Requirements**:
- **MUST BE MONOCHROME** (single color, transparent background)
- Simple, recognizable silhouette
- Android will tint this icon automatically
- No gradients, no colors (only white/transparent)
- Clear at small sizes (24x24 dp)

**Sizes Needed**:
- `drawable-mdpi`: 24x24 px
- `drawable-hdpi`: 36x36 px
- `drawable-xhdpi`: 48x48 px
- `drawable-xxhdpi`: 72x72 px
- `drawable-xxxhdpi`: 96x96 px

**Design Style**:
```
- Simple download arrow icon
- Or: "TXA" letters in simple outline
- Or: Update/sync circular arrows
- WHITE silhouette on TRANSPARENT background
- No shadows, no gradients, no colors
- Must be recognizable at 24x24 px
```

---

## 🎨 Design Guidelines

### Color Palette
```css
Primary:     #00D9FF (Cyan Blue)
Primary Dark: #0099CC
Secondary:   #FF6B6B (Coral Red)
Background:  #1A1A2E (Dark Navy)
Card BG:     #252540 (Dark Purple)
Text:        #FFFFFF (White)
Text Dim:    #AAAAAA (Gray)
```

### Typography Style
- **Modern, Tech-focused fonts**
- Examples: Orbitron, Exo 2, Rajdhani, Audiowide
- Bold weights for "TXA"
- Clean, readable

### Visual Style
- **Dark theme** with neon accents
- **Minimalist** and professional
- **Tech/Cyber aesthetic**
- Subtle gradients and glows
- No overly complex details

---

## 📐 Technical Specifications

### File Format
- **PNG** with transparency (32-bit RGBA)
- **High quality** (no compression artifacts)
- **Optimized** file size

### Naming Convention
```
ic_launcher.png       → App launcher icon
splash_logo.png       → Splash screen logo (512x512)
ic_notification.png   → Notification icon (monochrome)
```

### Export Settings
- **DPI**: 72 or 96
- **Color Space**: sRGB
- **Bit Depth**: 32-bit (RGBA)
- **Compression**: PNG-8 or PNG-24 with alpha

---

## 💡 Design Inspiration

### App Icon Concept
```
┌─────────────┐
│ ╔═══════╗   │
│ ║  TXA  ║   │  ← Bold text
│ ║ ▼ ▼ ▼ ║   │  ← Download arrows
│ ╚═══════╝   │
└─────────────┘
Dark navy background with cyan gradient border
```

### Splash Logo Concept
```
     ╔═══════════╗
     ║           ║
     ║    TXA    ║  ← Large, glowing
     ║           ║
     ║   DEMO    ║  ← Smaller subtitle
     ║           ║
     ╚═══════════╝
  Cyan glow effect around edges
```

### Notification Icon Concept
```
    ↓
   ╱ ╲
  ╱   ╲
 ╱_____╲
Simple download arrow
White on transparent
```

---

## 📦 Deliverables

Please provide:

1. **ic_launcher.png** (512x512 px master file)
2. **splash_logo.png** (512x512 px with transparency)
3. **ic_notification.png** (96x96 px monochrome master file)

All in **PNG format** with **transparent backgrounds** (except launcher icon can have background).

**Master files** should be high resolution (512x512 or larger) - the PowerShell script will resize them automatically.

---

## 🎯 Summary for Nano Banna

**Create 3 images for TXA Demo Android app:**

1. **App Icon** (512x512): Modern "TXA" logo, cyan (#00D9FF) on dark navy (#1A1A2E), tech style
2. **Splash Logo** (512x512): Larger version with glow effect, "TXA DEMO" text, transparent background
3. **Notification Icon** (96x96): Simple monochrome download arrow, white on transparent, no gradients

**Style**: Dark theme, neon cyan accents, minimalist tech aesthetic, professional

**Format**: PNG with transparency, high quality, optimized

---

**Contact**: TXAVLOG  
**Facebook**: fb.com/vlog.txa.2311  
**Project**: https://github.com/TXAVLOG/PROJECT
