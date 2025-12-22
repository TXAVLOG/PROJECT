# TXA Music – Dynamic Music Player with OTA Updates

> Ứng dụng music player động với hệ thống cập nhật OTA, tải APK qua resolver và dịch đa ngôn ngữ hoàn toàn động cho TXA Music.

## 🧭 Tổng quan

- **Package**: `ms.txams.vv`
- **Target SDK**: 33 (Android 13 – Play requirement 2025)
- **Ngôn ngữ**: Kotlin + XML
- **Kiến trúc**: MVVM + Repository, WorkManager cho background update

## ✨ Tính năng chính

1. **Dynamic Music Player** – Music player với now bar UI và các tính năng hiện đại.
2. **OTA Translation System** – Đồng bộ ngôn ngữ từ API (`/locales`, `/tXALocale/{locale}`) với cache `updated_at`.
3. **Update Resolver** – Hỗ trợ MediaFire, GitHub blob/raw, Google Drive confirm page; lưu APK tại `/storage/emulated/0/Download/TXAMusic/`.
4. **Force Test Mode** – Có thể bật trong `TXAUpdateManager` để luôn trả về bản cập nhật giả.
5. **Music Library UI** – Thư viện bài hát native thay cho File Manager cũ, hỗ trợ mở toàn bộ media trên máy.
6. **Legacy Storage + Logging** – Ghi log + APK tại `/storage/emulated/0/Download/TXAMusic/` để tương thích Android 13 trở xuống.

## 📂 Cấu trúc chính

```
PROJECT-ANDROID/
├── app/src/main/java/ms/txams/vv/
│   ├── core/        # TXAApp, TXATranslation, TXAHttp, TXAFormat, logging helpers
│   ├── data/        # Room entities/DAO + MusicRepository (MediaStore scan)
│   ├── di/          # Hilt modules (DatabaseModule, Repository bindings)
│   ├── download/    # TXADownloadService + notification + PendingIntent
│   ├── service/     # MusicService (Media3 player + MediaSession)
│   ├── ui/          # Splash, Settings, MusicLibraryActivity, fragments
│   └── update/      # Resolver, Downloader, Installer, UpdateManager
├── app/src/main/res/   # Layouts, drawables, themes (không dùng strings.xml)
├── build/              # Script build Windows/Ubuntu (TXAQuickBuild, TXABuild, setup)
├── tools/              # TXAProcessImages.ps1 (xử lý icon/splash/notification)
├── translation_keys_en.json
├── version.properties
├── README.md               # Tài liệu chính (product/devops)
└── README_DEV.md           # Ghi chú nội bộ cho developer
```

## ⚙️ Chuẩn bị môi trường

| Thành phần        | Phiên bản khuyến nghị |
|-------------------|-----------------------|
| JDK               | 17 (Adoptium/OpenJDK) |
| Android SDK       | Compile SDK 34 + Build Tools 34.x |
| Target SDK        | 33 (Android 13)       |
| Gradle Wrapper    | Gradle 8.7 (wrapper đi kèm) |
| ImageMagick (optional) | Để resize icon chất lượng cao |

## 🪟 Build trên Windows

1. **Cài đặt**:
   ```powershell
   winget install GitHub.cli
   winget install GnuPG.Gpg4win
   winget install EclipseAdoptium.Temurin.17.JDK
   Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
   ```
2. **Clone & cấu hình**:
   ```powershell
   git clone https://github.com/TXAVLOG/PROJECT.git
   cd PROJECT-ANDROID
   copy build\.env.example build\.env   # điền thông tin keystore/Git
   ```
   > **Nếu clone về mà chưa có `gradlew`**: cài Gradle rồi tạo wrapper một lần (chỉ cần chạy, không cần commit)
   > ```powershell
   > winget install Gradle.Gradle   # hoặc choco install gradle
   > gradle wrapper --gradle-version 8.7 --distribution-type all
   > ```
3. **Chạy build nhanh** (mặc định debug):
   ```powershell
   .\build\TXAQuickBuild.ps1           # build debug
   .\build\TXAQuickBuild.ps1 -Release  # build release (thêm -Debug nếu muốn ép debug)
   ```
4. **Build đầy đủ với upload**:
   ```powershell
   .\build\TXABuild.ps1                # hỗ trợ GitHub Releases + git push
   ```

## 🐧 Build trên Ubuntu (VPS)

1. **Chuẩn bị**:
   ```bash
   sudo apt update
    sudo apt install git curl unzip openjdk-17-jdk
   git clone https://github.com/TXAVLOG/PROJECT.git
   cd PROJECT-ANDROID
   ```
2. **Thiết lập SDK & Tools**:
   ```bash
   chmod +x build/TXASetupEnvironment.sh
   chmod +x build/*.sh                # cấp quyền cho toàn bộ script trong build/
   ./build/TXASetupEnvironment.sh
   source ~/.bashrc
   cp build/.env.example build/.env     # cập nhật mật khẩu keystore, Git user
   ```
   > **Nếu thiếu file `gradlew`** (chỉ cần tạo wrapper, không cần commit):
   > ```bash
   > sudo apt install gradle -y
   > gradle wrapper --gradle-version 8.7 --distribution-type all
   > ```
3. **Build** (mặc định debug):
   ```bash
   ./build/TXAQuickBuild.sh            # build debug
   ./build/TXAQuickBuild.sh --release   # build release (có thể dùng --debug để ép debug)
   ./build/TXABuild.sh                  # build + upload (nếu cấu hình)
   ```

## 🖼️ Xử lý icon / splash / notification

Script `tools/TXAProcessImages.ps1` hỗ trợ chỉ định đường dẫn bất kỳ (tương đối hoặc tuyệt đối):

```powershell
pwsh -File .\tools\TXAProcessImages.ps1 `
    -LauncherPath ".\logo.png" `
    -SplashPath ".\splash.png" `
    -NotificationPath ".\noti.png"
```

Hoặc dùng `-SourceRoot "C:\Assets\TXA"` nếu tất cả file nằm chung thư mục. Script sẽ tạo đủ mipmap/drawable density trong `app/src/main/res/`.

> **Yêu cầu ImageMagick**: để script resize chất lượng cao, cài ImageMagick trước khi chạy  
> Windows: `winget install ImageMagick.ImageMagick` (hoặc tải từ imagemagick.org và thêm vào PATH)  
> Ubuntu: `sudo apt install imagemagick -y`

## 🔐 Lưu ý bảo mật

- `build/.env`, keystore (`*.jks`, `*.keystore`), thư mục `keystore-backups/` đã nằm trong `.gitignore`.
- `TXABuild.sh` và `.ps1` có cơ chế tự tạo keystore, backup GPG và push GitHub Releases – cần điền chính xác thông tin trước khi chạy.

## 📞 Hỗ trợ

- **Developer**: TXAVLOG
- **Email**: txavlog7@gmail.com
- **Facebook**: https://fb.com/vlog.txa.2311
- **Issues**: mở ticket trên repo GitHub

---

**Last updated:** December 2025 – Force test mode mặc định **ON**; Target SDK = 33 (Android 13). Hãy set `FORCE_TEST_MODE = false` khi build production.
