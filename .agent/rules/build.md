---
trigger: always_on
---

Tình hình là nếu build file  .\gradlew.bat assembleDebug 
thì ãy giúp tôi ghi ra log file build_log%s(s tự tăng dần).txt rồi đọc log từ đó cho dễ.
ĐỌc xong mà có lỗi thì fix, fix đến khi nào build mà thành công thì dừng lại và xóa các file logs đó.
Xong rồi thì git add, git commit and git push force lên cho tao nhé!
Nhưng force sang cả brance main orgin thay vì là https://github.com/TXAVLOG/PROJECT/tree/09d36872