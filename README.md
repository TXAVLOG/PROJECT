# TXA Demo - Android OTA Update System

## 📱 Thông tin dự án

**Package**: `gc.txa.demo`  
**Target SDK**: 28 (Android 9.0 Pie)  
**Min SDK**: 26 (Android 8.0 Oreo)  
**Version**: Đọc từ `version.properties`

## 🎯 Tính năng chính

### 1. OTA Translation System
- Tự động đồng bộ ngôn ngữ từ API
- Cache translations tại internal storage
- Hỗ trợ 5+ ngôn ngữ: Tiếng Việt, English, 中文, 日本語, 한국어
- Fallback cứng cho 86+ translation keys

### 2. MediaFire Download Resolver
- Tự động crawl và resolve direct download link từ MediaFire
- Sử dụng OkHttp + Regex pattern matching
- Hỗ trợ download với progress tracking real-time

### 3. Force Test Mode
- Luôn trả về update available trong test mode
- Test URL: MediaFire APK link
- Changelog và version info được hardcode cho testing

### 4. Legacy Storage Support
- Lưu trữ APK và logs tại `/storage/emulated/0/Download/TXADEMO/`
- Tương thích với Android 9 (không dùng Scoped Storage)
- Tự động dọn dẹp APK cũ (>7 ngày)

### 5. Background Update Checker
- WorkManager tự động check update mỗi 3 phút
- Ghi log vào public storage
- Không làm gián đoạn user experience

## 🏗️ Kiến trúc

### Core Classes
- **TXAApp**: Application class, quản lý locale và WorkManager
- **TXATranslation**: OTA translation system với 86+ keys
- **TXAFormat**: Format utilities (bytes, speed, ETA, percent)
- **TXAHttp**: OkHttp singleton và logging system

### Update System
- **TXADownload**: Flow-based download với progress tracking
- **TXADownloadUrlResolver**: MediaFire URL resolver
- **TXAUpdateManager**: Update logic với force test mode
- **TXAInstall**: APK installation via FileProvider
- **TXAUpdateWorker**: Background worker (3 phút interval)

### UI Activities
- **TXASplashActivity**: Entry point, permission check, language sync
- **TXADemoNoticeActivity**: Demo version notice screen
- **TXASettingsActivity**: Settings, language change, update check

## 📋 Quy tắc kỹ thuật

### ZERO strings.xml Policy
- ❌ Không khai báo text trong `res/values/strings.xml`
- ✅ Toàn bộ UI text sử dụng `TXATranslation.txa(key)`
- ✅ Fallback về key nếu không tìm thấy translation

### Legacy Storage
- Thư mục: `/storage/emulated/0/Download/TXADEMO/`
- Logs: `logs/txa_YYYY-MM-DD.txt`
- APK: `TXA_[version].apk`

### Permissions
- `READ_EXTERNAL_STORAGE`
- `WRITE_EXTERNAL_STORAGE`
- `REQUEST_INSTALL_PACKAGES` (API 26+)

## 🔧 Cấu hình

### Version Management
File `version.properties`:
```properties
versionCode=1
versionName=1.0.0
```

### API Endpoints
- Locales: `https://soft.nrotxa.online/txademo/api/locales`
- Translation: `https://soft.nrotxa.online/txademo/api/tXALocale/{locale}`

### Force Test Mode
File: `TXAUpdateManager.kt`
```kotlin
private const val FORCE_TEST_MODE = true
private const val TEST_VERSION_NAME = "3.0.0_txa"
private const val TEST_DOWNLOAD_URL = "https://www.mediafire.com/file/..."
```

## 📦 Dependencies

- **AndroidX**: Core, AppCompat, Material, ConstraintLayout
- **Kotlin Coroutines**: Core + Android
- **Lifecycle**: Runtime + ViewModel
- **WorkManager**: Background tasks
- **OkHttp**: HTTP client
- **Gson**: JSON parsing
- **Google Play Services**: App Set ID

## 🚀 Build & Deploy

### Build APK
```bash
./gradlew assembleRelease
```

### Thay đổi version
Chỉnh sửa file `version.properties`:
```properties
versionCode=2
versionName=1.1.0
```

## 📝 Translation Keys

Tổng cộng 86+ keys được định nghĩa trong `translation_keys_en.json`:
- Core App (2 keys)
- Splash (7 keys)
- Demo Notice (8 keys)
- Settings (9 keys)
- Language Names (5 keys)
- Update Flow (22 keys)
- Common Actions (8 keys)
- Common Messages (7 keys)
- Permissions (6 keys)
- Formats (4 keys)

## 🔐 Security

- FileProvider cho APK installation
- Legacy external storage với proper permissions
- No hardcoded API keys
- Secure OkHttp configuration

## 📄 License

TXA Demo - Internal Project

---

**Developed by**: TXAVLOG  
**Repository**: https://github.com/TXAVLOG/PROJECT
