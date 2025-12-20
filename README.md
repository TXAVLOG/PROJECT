# 🚀 TXA Demo - Android OTA Update System

> Ứng dụng Android demo với hệ thống cập nhật OTA tự động, hỗ trợ đa ngôn ngữ và MediaFire download resolver.

[![Android](https://img.shields.io/badge/Android-9.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.20-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Private-red.svg)](https://github.com/TXAVLOG/PROJECT)

## 📱 Thông tin dự án

**Package**: `gc.txa.demo`  
**Target SDK**: 28 (Android 9.0 Pie)  
**Min SDK**: 26 (Android 8.0 Oreo)  
**Version**: Đọc từ `version.properties`  
**Language**: Kotlin + XML  
**Architecture**: MVVM + Repository Pattern

## 🎯 Tính năng chính

### 1. 🌐 OTA Translation System
Hệ thống đa ngôn ngữ tự động cập nhật qua mạng:
- ✅ Tự động đồng bộ ngôn ngữ từ API REST
- ✅ Cache translations tại internal storage (`/data/user/0/{package}/files/languages/`)
- ✅ Hỗ trợ 5+ ngôn ngữ: 🇻🇳 Tiếng Việt, 🇬🇧 English, 🇨🇳 中文, 🇯🇵 日本語, 🇰🇷 한국어
- ✅ Fallback cứng cho 86+ translation keys (không cần internet)
- ✅ **ZERO strings.xml policy** - Toàn bộ text dùng `TXATranslation.txa(key)`

**API Endpoints:**
```
GET https://soft.nrotxa.online/txademo/api/locales
GET https://soft.nrotxa.online/txademo/api/tXALocale/{locale}
```

### 2. 📥 MediaFire Download Resolver
Tự động giải quyết link download từ MediaFire:
- ✅ Crawl HTML page và extract direct download link
- ✅ Sử dụng OkHttp + Regex pattern: `https://download[0-9]+\.mediafire\.com/[^"'\s>]*\.apk`
- ✅ Hỗ trợ download với progress tracking real-time (speed, ETA, percent)
- ✅ Flow-based architecture cho reactive updates

### 3. 🧪 Force Test Mode
Chế độ test tích hợp sẵn:
- ✅ Luôn trả về `update_available = true`
- ✅ Test version: `3.0.0_txa`
- ✅ Test URL: `https://www.mediafire.com/file/jdy9nl8o6uqoyvq/TXA_AUTHENTICATOR_3.0.0_txa.apk/file`
- ✅ Changelog mô phỏng: "Phiên bản thử nghiệm 3.0.0_txa..."

**Bật/tắt test mode:**
```kotlin
// File: TXAUpdateManager.kt
private const val FORCE_TEST_MODE = true  // Set false để dùng API thật
```

### 4. 💾 Legacy Storage Support
Tương thích với Android 9 (không dùng Scoped Storage):
- ✅ Lưu trữ APK tại: `/storage/emulated/0/Download/TXADEMO/`
- ✅ Logs tại: `/storage/emulated/0/Download/TXADEMO/logs/txa_YYYY-MM-DD.txt`
- ✅ Tự động dọn dẹp APK cũ (>7 ngày)
- ✅ FileProvider cho APK installation (Android 7.0+)

### 5. ⏰ Background Update Checker
WorkManager tự động kiểm tra cập nhật:
- ✅ Chạy mỗi 3 phút (configurable)
- ✅ Ghi log chi tiết vào public storage
- ✅ Không làm gián đoạn user experience
- ✅ Auto-retry khi thất bại

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

## 🚀 Hướng dẫn sử dụng

### Cho người dùng (End Users)

#### Cài đặt
1. Download APK từ [Releases](https://github.com/TXAVLOG/PROJECT/releases)
2. Cấp quyền cài đặt từ nguồn không xác định
3. Cài đặt và mở ứng dụng
4. Cấp quyền storage khi được yêu cầu

#### Sử dụng
1. **Splash Screen**: Tự động kiểm tra và tải ngôn ngữ
2. **Demo Notice**: Đọc thông báo và nhấn "Confirm"
3. **Settings**: 
   - Xem thông tin phiên bản
   - Đổi ngôn ngữ (5+ ngôn ngữ)
   - Kiểm tra cập nhật thủ công

#### Cập nhật
- Tự động: Ứng dụng tự kiểm tra mỗi 3 phút
- Thủ công: Settings → Check for Updates
- Download progress hiển thị real-time (speed, ETA)
- Nhấn "Install" sau khi download xong

### Cho Developer

#### Build APK
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install to device
./gradlew installDebug
```

#### Thay đổi version
Chỉnh sửa file `version.properties`:
```properties
versionCode=2
versionName=1.1.0
```

#### Cấu hình Test Mode
File `app/src/main/java/gc/txa/demo/update/TXAUpdateManager.kt`:
```kotlin
private const val FORCE_TEST_MODE = true  // false = production mode
private const val TEST_VERSION_NAME = "3.0.0_txa"
private const val TEST_DOWNLOAD_URL = "https://www.mediafire.com/..."
```

#### Thêm Translation Key
1. Thêm key vào `TXATranslation.kt` → `fallbackTranslations`
2. Sử dụng: `TXATranslation.txa("your_key")`
3. Update API endpoint với translation mới

### Cho AI Assistant

#### Cấu trúc Project
```
PROJECT-ANDROID/
├── app/src/main/
│   ├── java/gc/txa/demo/
│   │   ├── TXAApp.kt                    # Application entry point
│   │   ├── core/
│   │   │   ├── TXATranslation.kt        # OTA translation system (86+ keys)
│   │   │   ├── TXAFormat.kt             # Format utilities
│   │   │   └── TXAHttp.kt               # HTTP client + logging
│   │   ├── update/
│   │   │   ├── TXADownload.kt           # Flow-based download
│   │   │   ├── TXADownloadUrlResolver.kt # MediaFire resolver
│   │   │   ├── TXAUpdateManager.kt      # Update logic (FORCE_TEST_MODE)
│   │   │   ├── TXAInstall.kt            # APK installation
│   │   │   └── TXAUpdateWorker.kt       # Background worker (3min)
│   │   └── ui/
│   │       ├── TXASplashActivity.kt     # Entry + permissions
│   │       ├── TXADemoNoticeActivity.kt # Demo notice
│   │       └── TXASettingsActivity.kt   # Settings + update
│   ├── res/
│   │   ├── layout/                      # 3 activity layouts
│   │   ├── values/                      # themes, colors (NO strings.xml)
│   │   └── xml/                         # file_paths, backup_rules
│   └── AndroidManifest.xml              # Legacy permissions + FileProvider
├── version.properties                   # Version management
├── translation_keys_en.json             # 86+ translation keys
└── README.md                            # This file
```

#### Key Technical Points
1. **Package**: `gc.txa.demo` (NOT com.txademo.app)
2. **Target SDK 28**: Legacy storage, no Scoped Storage
3. **ZERO strings.xml**: All text via `TXATranslation.txa(key)`
4. **Storage Path**: `/storage/emulated/0/Download/TXADEMO/`
5. **Force Test Mode**: Always returns update available
6. **MediaFire Resolver**: Regex pattern for direct links
7. **WorkManager**: 3-minute periodic updates
8. **View Binding**: Enabled in all activities

#### Common Modifications
```kotlin
// Change update check interval
val updateWorkRequest = PeriodicWorkRequestBuilder<TXAUpdateWorker>(
    5, TimeUnit.MINUTES  // Change from 3 to 5 minutes
).build()

// Add new translation key
private val fallbackTranslations = mapOf(
    "new_key" to "New Value",
    // ... existing keys
)

// Change storage path
val downloadDir = File("/storage/emulated/0/Download/MYAPP")
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

## ⚠️ Lưu ý quan trọng

### Permissions Required
- `READ_EXTERNAL_STORAGE` - Đọc file từ storage
- `WRITE_EXTERNAL_STORAGE` - Ghi APK và logs
- `REQUEST_INSTALL_PACKAGES` - Cài đặt APK (API 26+)
- `INTERNET` - Download updates và translations
- `ACCESS_NETWORK_STATE` - Kiểm tra kết nối

### Known Issues
- MediaFire resolver có thể fail nếu MediaFire thay đổi HTML structure
- WorkManager có thể bị delay bởi Android battery optimization
- Legacy storage không hoạt động trên Android 11+ (cần Scoped Storage)

### Roadmap
- [ ] Hỗ trợ Android 11+ với Scoped Storage
- [ ] Thêm notification cho background updates
- [ ] Hỗ trợ thêm download sources (Google Drive, Dropbox)
- [ ] In-app changelog viewer
- [ ] Update rollback mechanism

## 🤝 Contributing

Dự án này là internal project. Nếu bạn muốn contribute:
1. Fork repository
2. Tạo feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to branch (`git push origin feature/AmazingFeature`)
5. Open Pull Request

## 📞 Contact & Support

- **Developer**: TXAVLOG
- **Facebook**: [fb.com/vlog.txa.2311](https://fb.com/vlog.txa.2311)
- **Email**: viptretrauc@gmail.com
- **Repository**: [github.com/TXAVLOG/PROJECT](https://github.com/TXAVLOG/PROJECT)
- **Issues**: [GitHub Issues](https://github.com/TXAVLOG/PROJECT/issues)

## 📄 License

TXA Demo - Internal Project  
© 2024 TXAVLOG. All rights reserved.

---

## 🎓 For AI Assistants

### Quick Context
This is a **complete Android OTA update system** targeting **Android 9 (API 28)** with:
- **Force test mode** for easy testing
- **MediaFire resolver** using regex HTML parsing
- **OTA translation system** with 86+ keys
- **Legacy storage** at `/storage/emulated/0/Download/TXADEMO/`
- **WorkManager** background updates every 3 minutes

### When modifying this project:
1. ✅ Maintain ZERO strings.xml policy
2. ✅ Keep Target SDK at 28 (legacy storage)
3. ✅ Use `TXATranslation.txa(key)` for all UI text
4. ✅ Follow TXA naming convention (TXA prefix)
5. ✅ Add file header comment: `// FILE BY TXA`

### Critical files:
- `TXAUpdateManager.kt` - Update logic & test mode
- `TXATranslation.kt` - Translation system
- `TXADownloadUrlResolver.kt` - MediaFire resolver
- `version.properties` - Version source of truth

**Last Updated**: December 2024  
**Build Status**: ✅ Ready for production  
**Test Status**: ✅ Force test mode active
