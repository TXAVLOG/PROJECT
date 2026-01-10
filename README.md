# 🎵 TXAPP
# TXA Music Player

Ứng dụng nghe nhạc mạnh mẽ với thiết kế hiện đại, hỗ trợ nhiều định dạng và tính năng nâng cao.

## Hỗ trợ & Đóng góp
Nếu bạn có thắc mắc hoặc gặp lỗi, vui lòng:
- Gửi yêu cầu kéo (Pull Request) tại repository này.
- Hoặc liên hệ qua Facebook: [![Facebook](https://img.shields.io/badge/Facebook-1877F2?style=flat-square&logo=facebook&logoColor=white)](https://fb.com/vlog.txa.2311)

### Thông tin File
- **Phiên bản**: v2.5.3
- **Kích thước**: ~183 MB


Chào mừng bạn đến với **TXA Music Player** - ứng dụng nghe nhạc hiện đại, giao diện đẹp mắt và nhiều tính năng tùy biến mạnh mẽ dành cho Android.

![Version](https://img.shields.io/badge/Version-2.5.3__txa-blue?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-Active-success?style=for-the-badge)
![License](https://img.shields.io/badge/License-GPL--3.0-orange?style=for-the-badge)

## 📌 Tổng Quan

TXAPP Music Player được xây dựng dựa trên kiến trúc hiện đại, tập trung vào trải nghiệm người dùng với các tính năng nổi bật:
- **Synced Lyrics**: Hiển thị lời bài hát đồng bộ (giống ZingMP3).
- **Mini Player**: Trình phát thu nhỏ tiện lợi với đầy đủ điều khiển.
- **Material You**: Giao diện thích ứng theo màu sắc hệ thống.
- **Tag Editor**: Chỉnh sửa thông tin bài hát trực tiếp.

> **Phiên bản hiện tại:** <!-- VERSION_START -->2.5.3_txa<!-- VERSION_END -->
> *(Được cập nhật tự động từ [version.properties](version.properties))*

## 📦 Thông Tin Build (Latest Build Info)

| Check | Value |
| :--- | :--- |
| **File Name** | `TXAMUSIC_v2.5.3_txa.apk` |
| **Version** | v2.5.3_txa |
| **Build Date** | 10/01/2026 |
| **Size** | ~183 MB |
| **Status** | Stable / Signed |

**SHA-256 Checksum:**
```text
B66E5944084BC082A586258173EF7AA6B13C8D0804F42724045FBFC9F5917A16
```

> **Lưu ý:** Hãy kiểm tra mã SHA-256 sau khi tải về để đảm bảo file gốc không bị chỉnh sửa.

---

## 📖 Hướng Dẫn Sử Dụng (Cho Người Dùng)


1. **Cài đặt**:
   - Tải file APK mới nhất từ thư mục `TXABUILD/` hoặc từ trang Releases.
   - Cài đặt vào thiết bị Android (Yêu cầu Android 9.0+).

2. **Cấp quyền**:
   - Mở ứng dụng và cấp quyền truy cập bộ nhớ (Storage) để app có thể quét nhạc.
   - Với tính năng "Lời bài hát nổi" (Floating Lyrics), vui lòng cấp quyền "Hiển thị trên ứng dụng khác" trong cài đặt.

3. **Tính năng**:
   - **Quét nhạc**: Vào Cài đặt -> Quét thư viện để cập nhật bài hát mới.
   - **Lời bài hát**: Nhấn vào icon Lyrics ở màn hình chơi nhạc để xem hoặc tải lời bài hát.

---

## 👨‍💻 Hướng Dẫn Phát Triển (Cho Developer)

Nếu bạn muốn đóng góp hoặc tự build ứng dụng, hãy làm theo các bước sau:

### 1. Yêu cầu môi trường
- **OS**: Windows, Linux, hoặc macOS.
- **Java JDK**: 17 (Bắt buộc).
- **Android Studio**: Phiên bản mới nhất (Koala/Ladybug...).
- **Git**: Đã cài đặt và cấu hình.

### 2. Clone Repository
Mở terminal và chạy lệnh sau để tải mã nguồn về:

```bash
git clone https://github.com/TXAVLOG/PROJECT.git
cd PROJECT
```

### 3. Cài đặt Dependencies
Dự án sử dụng Gradle để quản lý thư viện. Bạn không cần tải thủ công các gói. Tuy nhiên, hãy đảm bảo Internet ổn định để Gradle tải về lần đầu:
- Android SDK Build-Tools 34.0.0
- Kotlin Gradle Plugin
- Hilt, Room, Coil, ExoPlayer...

### 4. Build Ứng Dụng

#### Cách 1: Sử dụng Script tự động (Khuyên dùng)
Dự án cung cấp script PowerShell giúp dọn dẹp và build nhanh bản Debug:

**Chạy trên Windows (PowerShell):**
```powershell
.\tools\build-fast.ps1
```
*Script này sẽ tự động Clean Project và chạy AssembleDebug.*

#### Cách 2: Build thủ công
Nếu bạn muốn build thủ công hoặc dùng trên Linux/Mac:

```bash
# Windows
.\gradlew.bat assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

File APK sau khi build sẽ nằm trong `app/build/outputs/apk/debug/`.

### 5. Công Cụ Hỗ Trợ (Tools)

#### Backup Keystore
Để bảo mật và sao lưu Keystore (chữ ký ứng dụng), hãy sử dụng script có sẵn. Script này sẽ nén toàn bộ các file `.jks` và `.keystore` vào thư mục an toàn.

**Windows:**
```powershell
.\tools\TXABackupKeystore.ps1
```

**Linux/Mac:**
```bash
chmod +x tools/TXABackupKeystore.sh
./tools/TXABackupKeystore.sh
```

---

## ✨ Contributors (Những người đóng góp)

Cảm ơn tất cả các bạn đã tham gia phát triển dự án này!

<a href="https://github.com/TXAVLOG/PROJECT/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=TXAVLOG/PROJECT" />
</a>

---

## 🔗 Tài liệu tham khảo
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Android Jetpack Guide](https://developer.android.com/jetpack)
- [Material Design 3](https://m3.material.io/)
- [ExoPlayer](https://github.com/google/ExoPlayer)

---
© 2024-2026 TXA Team. Developed with ❤️.
