# 📋 TXA Music - API Backend Handbook

## 🎯 Mục tiêu
Tài liệu này mô tả các API endpoint mà TXA Music App cần từ backend.

---

## 📡 API Backend

### Base URL
```
https://soft.nrotxa.online/txamusic/api/
```

---

## 📄 Endpoints & Response Format

### 1. `GET /locales` - Lấy danh sách ngôn ngữ hỗ trợ

**Request:** Không cần body

**Response:** Array đơn giản các locale codes
```json
["en", "vi"]
```

---

### 2. `GET /tXALocale/{code}` - Lấy file dịch theo ngôn ngữ

**Request:** Không cần body

**URL Examples:**
- `GET /tXALocale/en` - Lấy tiếng Anh
- `GET /tXALocale/vi` - Lấy tiếng Việt

**Response:** JSON object với tất cả translation keys và `updated_at` cho sync
```json
{
    "txamusic_app_name": "TXA Music",
    "txamusic_app_description": "TXA Music – Dynamic music player with OTA updates and now bar UI",
    "txamusic_splash_checking_permissions": "Checking permissions...",
    "txamusic_splash_requesting_permissions": "Requesting permissions...",
    "txamusic_splash_checking_language": "Checking language updates...",
    "txamusic_splash_downloading_language": "Downloading translations...",
    "txamusic_splash_language_updated": "Language updated successfully",
    "txamusic_splash_language_failed": "Failed to update language",
    "txamusic_splash_initializing": "Initializing application...",
    "txamusic_splash_loading_data": "Loading language data...",
    "txamusic_splash_checking_data": "Checking data...",
    "txamusic_splash_entering_app": "Entering app...",
    "txamusic_splash_connection_error": "Connection error, using fallback data",
    "txamusic_settings_title": "Settings",
    "txamusic_settings_app_info": "Application Information",
    "txamusic_settings_version": "Version",
    "... (all other txamusic_* keys) ...": "...",
    "updated_at": "2025-12-26T23:30:00Z"
}
```

**Lưu ý:**
- Response phải là JSON object (không phải array)
- Phải có trường `updated_at` ở cuối để app sync
- Server tự merge fallback từ `en.json` nếu locale thiếu khóa

---

### 3. `POST /update/check` - Kiểm tra cập nhật app

**Request:** JSON body với thông tin client
```json
{
    "packageId": "ms.txams.vv",
    "versionCode": 90,
    "versionName": "0.9.0",
    "locale": "en",
    "platform": "android",
    "debug": false
}
```

**Response khi có update:**
```json
{
    "ok": true,
    "source": "txamusic-api",
    "api_version": "2.0",
    "update_available": true,
    "force_update": false,
    "client": {
        "packageId": "ms.txams.vv",
        "versionCode": 90,
        "versionName": "0.9.0"
    },
    "latest": {
        "packageId": "ms.txams.vv",
        "versionCode": 100,
        "versionName": "1.0.0_txa",
        "downloadUrl": "https://soft.nrotxa.online/Download/TXAMusic/TXAMusic_1.0.0_txa.apk",
        "downloadSizeBytes": 52323968,
        "checksum": {
            "type": "sha256",
            "value": "b3cfe26c1f32a3d8614d46b6dd4c3e8efc9c30c575f53a03c0d8e0f4e4d5b91f"
        },
        "releaseDate": "2025-12-23",
        "mandatory": false,
        "changelog": "<style>...</style><div class='changelog'>...</div>"
    }
}
```

**Response khi không có update:**
```json
{
    "ok": true,
    "update_available": false,
    "client": {
        "packageId": "ms.txams.vv",
        "versionCode": 100,
        "versionName": "1.0.0"
    }
}
```

**Response khi lỗi:**
```json
{
    "ok": false,
    "error_code": "INVALID_REQUEST",
    "message": "Invalid request format"
}
```

**Fields quan trọng:**
| Field | Type | Mô tả |
|-------|------|-------|
| `ok` | boolean | Trạng thái request thành công |
| `update_available` | boolean | Có bản cập nhật mới không |
| `force_update` | boolean | Bắt buộc cập nhật (hiển thị dialog không cho skip) |
| `latest.versionCode` | int | Version code của bản mới |
| `latest.versionName` | string | Version name của bản mới |
| `latest.downloadUrl` | string | URL download APK trực tiếp |
| `latest.downloadSizeBytes` | long | Kích thước file APK (bytes) |
| `latest.checksum.type` | string | Loại hash: "sha256", "md5", "sha1" |
| `latest.checksum.value` | string | Giá trị hash để validate sau download |
| `latest.releaseDate` | string | Ngày phát hành (YYYY-MM-DD) |
| `latest.mandatory` | boolean | Alias của force_update trong latest object |
| `latest.changelog` | string | HTML changelog có style |

**Checksum Validation:**
- App sẽ tính hash file APK sau khi download xong
- So sánh với `checksum.value` từ server
- Nếu KHÔNG khớp → hiển thị thông báo "App integrity check failed. Please reinstall the app." (đa ngôn ngữ)
- Người dùng cần gỡ app và cài lại từ nguồn chính thức

---

### ⚠️ CÁC LỖI THƯỜNG GẶP (Backend Warning)

Dựa trên Logcat, hiện tại server đang gặp lỗi `invalid_metadata`. Vui lòng kiểm tra các điểm sau:

1. **Trường `downloadSizeBytes`**: Phải viết đúng camelCase là `downloadSizeBytes`, không phải `size_bytes`.
2. **Trường `versionCode` và `versionName`**: Phải nằm trong object `latest`. Không được dùng `code` hay `name` đơn lẻ.
3. **Checksum Object**: Phải là một object `{"type": "sha256", "value": "..."}`, không được để chuỗi string trực tiếp vào `checksum_sha256`.
4. **Encoding**: Đảm bảo JSON metadata không chứa các ký tự điều khiển (Control characters) gây lỗi parse.

**Cấu trúc Latest Object chuẩn mà App mong đợi:**
```json
"latest": {
    "versionCode": 100,
    "versionName": "1.2.0_txa",
    "downloadUrl": "https://...",
    "downloadSizeBytes": 52428800,
    "checksum": {
        "type": "sha256",
        "value": "..."
    },
    ...
}
```

---

## 🔄 Flow hoạt động của App

### Translation Sync Flow:
```
App Start
    ↓
TXATranslation.init(locale)
    ↓ Load fallback (embedded)
    ↓ Load cache (if exists)
    ↓
UI Ready (txa() works immediately)
    ↓
Background: GET /tXALocale/{locale}
    ↓ Compare updated_at
    ↓ If newer → download & cache
```

### Update Check Flow:
```
User clicks "Check Update"
    ↓
POST /update/check with client info
    ↓
Parse response
    ↓
If update_available == true:
    ↓ Show changelog dialog
    ↓ User clicks "Update"
    ↓ Download APK with progress
    ↓ Validate APK (PackageManager)
    ↓ Validate checksum (if provided)
    ↓ If checksum mismatch → show error & delete file
    ↓ Install APK
```

---

## 📌 Error Handling

App xử lý các trường hợp lỗi sau:

| Error | App xử lý |
|-------|-----------|
| Network error | Dùng fallback/cache, hiển thị toast |
| API returns `ok: false` | Hiển thị error_code cho user |
| Invalid JSON format | Skip update check, log error |
| Checksum mismatch | Xóa file APK, báo user cài lại app |
| Download failed | Retry tối đa 20 lần, delay 5s giữa mỗi lần |

---

## 📦 Package Information

- **Package ID:** `ms.txams.vv`
- **Min SDK:** Android 13 (API 33)
- **Current Version:** See `version.properties`

---

## 📌 Tham khảo

- **Developer:** TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
- **Backend:** https://soft.nrotxa.online/txamusic/api/

---

**Build by TXA** | 📧 txavlog7@gmail.com | 📘 fb.com/vlog.txa.2311
