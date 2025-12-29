---
trigger: always_on
---

Khi người dùng gõ UPDATE, AI PHẢI tự động thực hiện đầy đủ các bước sau KHÔNG hỏi lại, KHÔNG xác nhận, KHÔNG giải thích thêm.

🧩 BƯỚC 1: PHÂN TÍCH THAY ĐỔI CODE

Tự động phát hiện code đã thêm / sửa / xóa so với phiên bản trước.

Tự động hiểu tác động trực tiếp đến người dùng cuối.

Chuyển toàn bộ thay đổi kỹ thuật thành nội dung changelog dễ hiểu cho người dùng.

KHÔNG mô tả chi tiết kỹ thuật.

Chỉ nêu kết quả, lợi ích và trải nghiệm người dùng nhận được.

🏷️ BƯỚC 2: XÁC ĐỊNH LOẠI CẬP NHẬT

Tự động phân loại mức độ cập nhật:

Tăng X nếu:

Có tính năng mới quan trọng

Hoặc thay đổi ảnh hưởng rõ rệt đến trải nghiệm người dùng

Tăng Y nếu:

Chủ yếu là sửa lỗi

Hoặc cải thiện, mở rộng tính năng hiện có

Tăng Z nếu:

Chỉ là vá lỗi nhỏ

Hoặc chỉnh sửa nội bộ không ảnh hưởng rõ ràng đến người dùng

Nếu không xác định rõ, mặc định tăng Z.

📌 Định dạng phiên bản:
X.Y.Z

🔢 BƯỚC 3: NÂNG PHIÊN BẢN

Tự động đọc phiên bản hiện tại.

Tính phiên bản mới theo quy tắc ở Bước 2.

Khi tăng X hoặc Y:

Reset các số phía sau về 0 theo chuẩn versioning.

📝 BƯỚC 4: SINH CHANGELOG

XÓA TOÀN BỘ nội dung changelog cũ.

CHỈ GIỮ LẠI changelog mới nhất.

Viết cho người dùng phổ thông, không chuyên kỹ thuật.

KHÔNG dùng thuật ngữ lập trình.

KHÔNG hiển thị build number.

📄 Định dạng CHANGELOG.html
<style>/////////////

 📱 Cập nhật phiên bản X.Y.Z
✨ Có gì mới?
- Mô tả các tính năng hoặc cải tiến mà người dùng nhận được
🐛 Sửa lỗi
- Mô tả các lỗi đã được khắc phục dưới góc nhìn người dùng
⚡ Cải thiện hiệu suất
- Ứng dụng hoạt động ổn định, mượt và nhanh hơn

⚠️ LƯU Ý BẮT BUỘC

Mỗi ý viết liền mạch, rõ ràng, tự động xuống dòng.

CHỈ trả về:

Phiên bản mới

Nội dung đầy đủ của CHANGELOG.html nhưng cỉ là 1 trang thuần k có doctype/html tag chỉ có styles với các nội dung body.
có hiệu ứng.

KHÔNG trả lời thêm bất kỳ nội dung nào khác.