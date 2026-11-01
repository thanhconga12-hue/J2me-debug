# Báo cáo sửa lỗi TreoOnlineService dừng tiến trình

## Kết quả điều tra

Crash log `crash.txt` ghi nhận lỗi:

`TreoOnlineService.onDestroy -> MidletThread.pauseIfBackground -> ContextHolder.getActivity -> WeakReference.get()`

`ContextHolder.currentActivity` có thể là `null`, nên gọi trực tiếp `currentActivity.get()` gây `NullPointerException`. Stack trace còn cho thấy APK gặp lỗi đang chạy nhánh Java cũ (`pauseIfBackground`), không khớp với bản source phục hồi trước đó vốn đã có `pauseIfActivityStopped`. Cần cài đúng APK được build từ source mới để xác nhận lỗi trên thiết bị.

## Thay đổi đã thực hiện

- `ContextHolder.currentActivity` được công bố an toàn giữa các luồng bằng `volatile`.
- `ContextHolder.getActivity()` lấy WeakReference vào biến cục bộ và trả `null` an toàn nếu holder chưa được tạo hoặc đã bị clear.
- Hai hàm xin quyền chuyển sang dùng `getActivity()` thay vì gọi thẳng `currentActivity.get()`.
- `MidletThread.start()` kiểm tra Activity trước khi đăng ký lifecycle observer.
- `MidletThread` bảo vệ các lần truy cập handler/instance; trạng thái được đánh dấu `volatile`.
- `pauseIfActivityStopped()` chỉ xếp PAUSE khi MIDlet còn STARTED và Activity vẫn ở nền; không gọi `ContextHolder` hay truy cập Activity trong lúc Service bị hủy.
- PAUSE cũ được bỏ qua nếu Activity đã trở lại foreground trước khi message được xử lý.
- Service tiếp tục gọi `pauseIfActivityStopped()` sau khi giải phóng wake locks và hoàn tất `super.onDestroy()`.

## Kiểm tra đã chạy

- PASS: 104 resource XML và 3 AndroidManifest.xml phân tích được.
- PASS: Activity và Treo Service cùng khai báo tiến trình `:midlet`.
- PASS: quyền và khai báo foreground service `specialUse` có mặt.
- PASS: menu Treo, CPU/Wi-Fi locks và lifecycle integration có mặt.
- PASS: `ContextHolder` không còn dereference `currentActivity.get()` trực tiếp.
- PASS: nhánh `TreoOnlineService.onDestroy()` không truy cập Activity.
- PASS: không còn `pauseIfBackground` trong `MidletThread`.

## Giới hạn build

Chưa tạo được APK trong môi trường hiện tại. `./gradlew --version` dừng ở lỗi DNS khi tải Gradle 8.13 từ `services.gradle.org`; Android SDK (`android.jar`) cũng không có sẵn trong runtime. Vì vậy báo cáo này không tuyên bố APK đã build hoặc đã chạy trên thiết bị. Build cần chạy trong môi trường có JDK 17, Gradle 8.13, Android platform 34, build-tools 34.0.0 và NDK 22.1.7171670.
