# Build APK qua GitHub Actions (không cần cài Android Studio trên điện thoại)

1. Tải `J2ME_NST_FPS_Rebuild.zip` và giải nén trên máy tính.
2. Tạo một repository GitHub mới, tải toàn bộ nội dung thư mục đã giải nén lên repository (bao gồm thư mục `.github`).
3. Mở tab **Actions** của repository và bật Actions nếu GitHub yêu cầu.
4. Chọn workflow **Build J2ME NST FPS (debug)**.
5. Nhấn **Run workflow** và chờ workflow hoàn tất.
6. Trong kết quả chạy workflow, tải artifact `J2ME-NST-FPS-debug`. Bên trong là APK debug được ký bằng debug key tự động của Android Gradle Plugin.

Workflow sẽ checkout submodule, cài JDK 17, Android SDK 34, NDK 22.1.7171670, tải TinySoundFont và chạy Gradle build.

Lưu ý: workflow này chưa được thực thi trong môi trường tạo source. Nó là lối build từ GitHub Actions; cần kiểm tra kết quả build thực tế trước khi cài APK lên máy chính. APK có package riêng `com.nst.j2me1.fpsboost`, không cập nhật đè lên NST cũ.
