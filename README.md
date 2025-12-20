# TXA Demo – Android OTA Update System

> Ứng dụng demo showcase hệ thống cập nhật OTA, tải APK qua resolver và dịch đa ngôn ngữ hoàn toàn động.

## 🧭 Tổng quan

- **Package**: `gc.txa.demo`
- **Target SDK**: 28 (Android 9 – legacy storage)
- **Ngôn ngữ**: Kotlin + XML
- **Kiến trúc**: MVVM + Repository, WorkManager cho background update

## ✨ Tính năng chính

1. **OTA Translation System** – Đồng bộ ngôn ngữ từ API (`/locales`, `/tXALocale/{locale}`) với cache `updated_at`.
2. **Update Resolver** – Hỗ trợ MediaFire, GitHub blob/raw, Google Drive confirm page; lưu APK tại `/storage/emulated/0/Download/TXADEMO/`.
3. **Force Test Mode** – Có thể bật trong `TXAUpdateManager` để luôn trả về bản cập nhật giả.
4. **File Manager UI** – Liệt kê, cài đặt, xoá APK tải về.
5. **Legacy Storage + Logging** – Phù hợp Android 8/9, ghi log vào thư mục tải xuống.

## 📂 Cấu trúc chính

```
PROJECT-ANDROID/
├── app/src/main/java/gc/txa/demo/
│   ├── core/              # TXATranslation, TXAHttp, TXAFormat
│   ├── update/            # Resolver, Download, Install, UpdateManager
│   └── ui/                # Splash, DemoNotice, Settings, FileManager
├── app/src/main/res/      # Layouts, drawables, themes (không dùng strings.xml)
├── build/                 # Script build Windows/Ubuntu
├── tools/                 # TXAProcessImages.ps1 (xử lý icon)
├── translation_keys_en.json
├── version.properties
└── README.md (file này)
```

## ⚙️ Chuẩn bị môi trường

| Thành phần        | Phiên bản khuyến nghị |
|-------------------|-----------------------|
| JDK               | 11                    |
| Android SDK       | API 28 + Build Tools 28.0.3 |
| Gradle Wrapper    | Gradle 7.6 (wrapper đi kèm) |
| ImageMagick (optional) | Để resize icon chất lượng cao |

## 🪟 Build trên Windows

1. **Cài đặt**:
   ```powershell
   winget install GitHub.cli
   winget install GnuPG.Gpg4win
   winget install OpenJDK.11
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
   > gradle wrapper --gradle-version 7.6 --distribution-type all
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
   sudo apt install git curl unzip openjdk-11-jdk
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
   > gradle wrapper --gradle-version 7.6 --distribution-type all
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

**Last updated:** December 2025 – Force test mode mặc định **ON** để thuận tiện kiểm thử; hãy set `FORCE_TEST_MODE = false` khi build production.
