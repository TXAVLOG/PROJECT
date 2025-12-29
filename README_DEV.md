# TXA Music - Developer Documentation

> Tài liệu kỹ thuật chi tiết cho developers làm việc với TXA Music codebase.

## 📐 Kiến trúc hệ thống

```
App Start
  ↓
TXAApp.onCreate()
  ├── TXALogger.init() ─────────── Crash logging available
  ├── Version Check ────────────── < Android 13? → Show dialog & exit
  └── TXATranslation.init() ────── Read cache → Apply → Background sync
  ↓
TXASplashActivity
  ├── Integrity Check (intro_txa.mp3)
  └── Navigate to Main
  ↓
TXAMainActivity (UI Ready)
  ↓
Background: TXAUpdateWorker (every 3 min)
```

## 🗂 Module Structure

### core/
| File | Chức năng |
|------|-----------|
| `TXAApp.kt` | Application class, init logger + translation |
| `TXATranslation.kt` | Đa ngôn ngữ với 3-layer fallback (OTA → Hardcoded → Key) |
| `TXALogger.kt` | Logging với daily rotation, 1MB max, crash handler |
| `TXAHttp.kt` | Singleton OkHttpClient + Kotlinx Json config |
| `TXAFormat.kt` | Format bytes, speed, ETA, date utilities |

### update/
| File | Chức năng |
|------|-----------|
| `TXAUpdateManager.kt` | Check update API, download flow với retry |
| `TXADownload.kt` | Stream APK to disk với progress Flow |
| `TXADownloadUrlResolver.kt` | Resolve MediaFire, Drive, GitHub → direct URL |
| `TXAInstall.kt` | Install APK via FileProvider |
| `TXAUpdateWorker.kt` | Background check mỗi 3 phút |

### ui/
| File | Chức năng |
|------|-----------|
| `TXASplashActivity.kt` | Version check, integrity check, init |
| `TXAMainActivity.kt` | Main screen với bottom nav |
| `TXASettingsActivity.kt` | Language, Update, Logs |
| `TXAMusicLibraryActivity.kt` | Music library từ MediaStore |

### service/
| File | Chức năng |
|------|-----------|
| `MusicService.kt` | Media3 ExoPlayer + MediaSession |

## 🌐 Translation System

### Architecture
```
TXATranslation.init(context, locale)
  ↓
readLocalLocale() → có cache?
  ├── YES: applyPayload(cache)
  └── NO: use fallbackStrings
  ↓
UI Ready (txa() luôn trả về text)
  ↓
Background: syncIfNewer()
  ↓ GET /locales → compare updated_at
  ├── remoteTs > localTs: download + cache + apply
  └── equal: keep current
```

### API Endpoints
- `GET /txamusic/api/locales` - List available locales
- `GET /txamusic/api/locale/{code}` - Get translations for locale

### Usage
```kotlin
// Extension function
"txamusic_settings_title".txa()

// Direct call
TXATranslation.txa("txamusic_settings_title")

// Force sync
TXATranslation.forceSync("vi")

// Get available locales
TXATranslation.getAvailableLocales()
```

## 🔄 Update System

### Check Flow
```
TXAUpdateManager.checkForUpdate()
  ↓
POST /txamusic/api/update/check
{
  "packageId": "ms.txams.vv",
  "versionCode": 1,
  "versionName": "1.0.0_txa",
  "locale": "en"
}
  ↓
Response: UpdateCheckResult
  ├── UpdateAvailable(UpdateInfo)
  ├── NoUpdate(currentVersion)
  └── Error(message)
```

### Download Flow
```
TXAUpdateManager.downloadUpdate(context, updateInfo)
  ↓ emit TXAUpdatePhase
  ├── Starting
  ├── Resolving (URL resolver)
  ├── Connecting
  ├── Downloading(bytes, total, speed, eta)
  ├── Retrying(attempt, max)
  ├── Validating
  ├── ReadyToInstall(file)
  └── Error(message)
```

### URL Resolver
```kotlin
TXADownloadUrlResolver.resolve(url) → ResolveResult
  ├── DIRECT (.apk) → return as-is
  ├── MEDIAFIRE → parse HTML for download button
  ├── GOOGLE_DRIVE → extract fileId → direct link
  ├── GITHUB → /blob/→/raw/, follow redirects
  └── UNKNOWN → follow redirects (max 10)
```

## 📝 Logging System

### Log Types
| Type | ADB Tag | File Prefix | Use Case |
|------|---------|-------------|----------|
| CRASH | `TXACRASH` | `TXA_crash_` | Uncaught exceptions |
| APP | `TXAAPP` | `TXA_app_` | General app logging |
| API | `TXAAPI` | `TXA_api_` | Network requests |
| DOWNLOAD | `TXADOWNLOAD` | `TXA_download_` | Download progress |

### Usage
```kotlin
// Shortcut methods
TXALogger.appI("Info message")
TXALogger.apiD("Debug API call")
TXALogger.downloadE("Error", exception)
TXALogger.crash("Crash!", throwable)

// Full methods
TXALogger.d(LogType.APP, "Debug")
TXALogger.e(LogType.API, "Error", exception)
```

### Storage
```
Android/data/ms.txams.vv/files/
├── logs/
│   ├── TXA_app_27-12-2024.log
│   ├── TXA_api_27-12-2024.log
│   └── TXA_crash_27-12-2024.log
└── cache/
    └── lang/
        ├── lang_en.json
        └── lang_en_updated_at.txt
```

### ADB Commands
```bash
# View all TXA logs
adb logcat -s TXAAPP TXAAPI TXADOWNLOAD TXACRASH

# View specific type
adb logcat -s TXAAPP

# Pull log files
adb pull /storage/emulated/0/Android/data/ms.txams.vv/files/logs/ ./logs/
```

## 🔧 Build System

### Version Management
```properties
# version.properties
versionCode=1
versionName=1.0.0_txa
```

### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Clean + Build
./gradlew clean assembleDebug

# Run unit tests
./gradlew test
```

### Build Scripts
| Script | Platform | Function |
|--------|----------|----------|
| `buildsc/TXAQuickBuild.ps1` | Windows | Quick debug/release build |
| `buildsc/TXAQuickBuild.sh` | Linux | Quick debug/release build |
| `buildsc/TXABuild.ps1` | Windows | Full build + upload |
| `buildsc/TXABuild.sh` | Linux | Full build + upload |

## 📦 Dependencies

### Core
- `androidx.core.ktx`
- `androidx.appcompat`
- `com.google.android.material`
- `dagger.hilt.android` (DI)
- `androidx.room` (Database)

### Media
- `androidx.media3.exoplayer`
- `androidx.media3.session`
- `androidx.media3.ui`

### Network
- `com.squareup.okhttp3`
- `org.jetbrains.kotlinx.serialization.json`

### Background Work
- `androidx.work.runtime.ktx`

## 🔐 Security Notes

- `.env` files, keystores, GPG keys are gitignored
- APK files are stored in app-specific directory (no extra permission needed)
- FileProvider used for APK installation
- No sensitive data in logs

## 📞 Contact

- **Developer**: TXAVLOG
- **Email**: txavlog7@gmail.com
- **Facebook**: [fb.com/vlog.txa.2311](https://fb.com/vlog.txa.2311)

---

**Last updated:** December 2025
