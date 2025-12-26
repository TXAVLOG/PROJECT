# TXA Music

> 🎵 Dynamic music player với OTA updates, đa ngôn ngữ và giao diện hiện đại.

[![Android](https://img.shields.io/badge/Android-13%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg)](https://kotlinlang.org)

## 📱 Yêu cầu hệ thống

- **Android 13** (API 33) trở lên
- **Bộ nhớ trống**: ~50MB

## 🚀 Tải và cài đặt

### Cách 1: Tải APK có sẵn

1. Vào [Releases](https://github.com/TXAVLOG/PROJECT/releases) trên GitHub
2. Tải file `TXAMusic-x.x.x_txa-debug.apk` mới nhất
3. Cài đặt APK trên điện thoại

### Cách 2: Build từ source

#### Windows

```powershell
# 1. Clone repo
git clone https://github.com/TXAVLOG/PROJECT.git
cd PROJECT-ANDROID

# 2. Build debug APK
.\gradlew.bat assembleDebug

# 3. APK nằm tại: app\build\outputs\apk\debug\
```

#### Linux/macOS

```bash
# 1. Clone repo
git clone https://github.com/TXAVLOG/PROJECT.git
cd PROJECT-ANDROID

# 2. Cấp quyền và build
chmod +x gradlew
./gradlew assembleDebug

# 3. APK nằm tại: app/build/outputs/apk/debug/
```

## ✨ Tính năng chính

| Tính năng | Mô tả |
|-----------|-------|
| 🎵 **Music Player** | Phát nhạc với Media3 ExoPlayer, hỗ trợ notification |
| 🌐 **Đa ngôn ngữ** | EN, VI, JA, ZH, KO - tự động cập nhật từ API |
| 🔄 **OTA Updates** | Tự động check và tải bản cập nhật mới |
| 🎨 **Material 3** | Giao diện hiện đại với Glassmorphism |
| 📁 **Music Library** | Quét và hiển thị toàn bộ nhạc trên máy |

## 📂 Cấu trúc thư mục

```
TXA Music/
├── core/        # TXAApp, TXATranslation, TXALogger, TXAHttp
├── ui/          # Splash, Main, Settings, MusicLibrary
├── update/      # TXAUpdateManager, TXADownload, TXAInstall
├── service/     # MusicService (Media3)
└── data/        # Room DB, MusicRepository
```

## ⚙️ Cấu hình build

| Thành phần | Phiên bản |
|------------|-----------|
| JDK | 17 |
| Gradle | 8.7 |
| Kotlin | 2.1.0 |
| Compile SDK | 35 |
| Target SDK | 34 |
| Min SDK | 33 (Android 13) |

## 📞 Liên hệ

- **Developer**: TXAVLOG
- **Email**: txavlog7@gmail.com
- **Facebook**: [fb.com/vlog.txa.2311](https://fb.com/vlog.txa.2311)
- **GitHub Issues**: [Tạo issue mới](https://github.com/TXAVLOG/PROJECT/issues)

---

**© 2025 TXA - All rights reserved**
