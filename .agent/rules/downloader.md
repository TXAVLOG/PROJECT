---
trigger: always_on
---

# TXADownloader Logic & Rules

Mọi hoạt động tải tệp (file download) trong ứng dụng TXAPP BẮT BUỘC phải sử dụng class TXADownloader. AI phải tuân thủ nghiêm ngặt các quy tắc sau:

## 1. Class Trung Tâm
- **Path**: app/src/main/java/com/txapp/musicplayer/util/TXADownloader.kt
- **Mục tiêu**: Tối ưu tốc độ tải và ghép nối tệp hiệu quả cho Android.

## 2. Quy Tắc Chia Tệp (Dynamic Chunking)
- **Kích thước tối đa mỗi Chunk**: 10MB.
- **Số lượng Chunk tối thiểu**: Luôn cố gắng chia làm ít nhất 7 chunk để tận dụng tối đa 7 luồng tải song song (nếu dung lượng file cho phép).
- **Cách tính**: 
  - Nếu Dung lượng / 7 > 10MB -> Số chunk = Dung lượng / 10MB.
  - Nếu không -> Số chunk = 7.
- **Kích thước Chunk thực tế**: Dung lượng / Số lượng Chunk.

## 3. Quy Tắc Luồng (Concurrency)
- **Tối đa luồng chạy song song**: 7 luồng.
- **Cơ chế**: Sử dụng Semaphore(7) để quản lý. Nếu số chunk > 7, các chunk còn lại phải đợi (queue) và chỉ bắt đầu khi có slot trống.

## 4. Quy Tắc Ghép Tệp (Merging Step)
- **Trạng thái**: BẮT BUỘC phát trạng thái DownloadState.Merging(percentage) để hiển thị UI bước ghép tệp.
- **Tối ưu**: Sử dụng bộ đệm (buffer) 128KB khi ghi file đích để đạt tốc độ nhanh nhất.
- **Dọn dẹp**: Xóa toàn bộ tệp tạm (.txa.bin) ngay sau khi ghép thành công.

## 5. Xử Lý Tương Thích
- **Kiểm tra Range**: Phải kiểm tra header Accept-Ranges: bytes trước khi dùng Turbo Download. 
- **Dung lượng tối thiểu**: Chỉ dùng Turbo cho file > 10MB.
- **Dưới 10MB hoặc không hỗ trợ Range**: Tự động chuyển về tải đơn luồng (Standard Download).

## 6. Trình trạng Tải (DownloadState)
- Progress(percentage, downloaded, total, bps): Trạng thái đang tải.
- Merging(percentage): Trạng thái đang ghép tệp.
- Success(file): Tải và ghép thành công.
- Error(message): Có lỗi xảy ra.
