# Khôi phục phần Treo cho NST FPS v3

## Đã ghép vào source

1. `TreoOnlineService.java`: Foreground Service chạy trong tiến trình `:midlet`; tạo notification channel; giữ `PARTIAL_WAKE_LOCK`, `WifiLock` và `MulticastLock`; nhả khóa khi dừng hoặc khi khởi tạo thất bại.
2. `MicroActivity.java`: thêm mục bật/tắt **Treo trực tuyến** trong menu; chờ Service xác nhận hoạt động tối đa khoảng 2 giây rồi mới đưa task xuống nền.
3. `MidletThread.java`: khi Activity nhận `ON_STOP`, chỉ gọi `pauseApp()` nếu Treo không được bật. Khi Service dừng hoặc gặp lỗi, MIDlet được pause trở lại nếu Activity vẫn đang ở nền.
4. `AndroidManifest.xml`: thêm các quyền WakeLock/Foreground Service/Wi-Fi; khai báo Service với `foregroundServiceType="specialUse"`, subtype property của Android 14 và `process=":midlet"`.
5. Cả ba Manifest (main/debug/midlet) đều đặt `MicroActivity` ở `:midlet`, đồng nhất với Treo Service.
6. Thêm chuỗi tiếng Anh mặc định và bản dịch tiếng Việt cho menu/thông báo.

## Cơ sở đối chiếu và phạm vi

Mã Java Treo của gói source v3 không có trong source archive ban đầu, nhưng các lớp `TreoOnlineService`, `MicroActivity` và `MidletThread` có trong DEX của gói APK debug TREO v3. Phần source trong gói này được dựng lại theo luồng và các phương thức quan sát được từ DEX v3.

Đây **không phải bản sao đầy đủ của bộ điều khiển Treo trong NST gốc**. APK gốc còn có `TreoBootReceiver`, `TreoTileService` và bộ điều khiển nhiều chế độ dựa trên các lớp/helper obfuscate. Chúng chưa được ghép vào nhánh source hiện tại vì không có source tương ứng đáng tin cậy để phục hồi an toàn. Bản ghép này bao gồm cơ chế nền cốt lõi đã có trong TREO v3: Foreground Service + CPU/Wi-Fi locks + giữ MIDlet khỏi `pauseApp()` khi Activity xuống nền.

## Kiểm tra đã chạy

- Parse XML thành công: 104 XML tài nguyên và 3 Android Manifest.
- Structural assertions: Service được khai báo đúng tiến trình `:midlet`, đúng foreground type `specialUse`; MicroActivity ở cùng tiến trình; đủ các quyền bắt buộc; menu Treo tồn tại.
- Static source assertions: có lệnh khởi chạy Foreground Service, CPU WakeLock, Wi-Fi/Multicast locks, release locks, nhánh lifecycle Treo và thao tác bật/tắt menu.

## Chưa thể xác nhận

- Chưa build được APK trong môi trường này. Gradle wrapper cần tải Gradle 8.13 từ `services.gradle.org` nhưng phân giải DNS thất bại (`UnknownHostException`). Android SDK Platform 34/Build Tools và NDK 22.1.7171670 cũng chưa được cài trong workspace; TinySoundFont source submodule còn rỗng.
- Chưa cài hoặc chạy thử trên điện thoại; chưa xác minh thực tế với màn hình tắt/thu nhỏ, Wi-Fi/mobile data hoặc các chế độ tiết kiệm pin của hãng.
- WakeLock/Foreground Service tăng khả năng tiếp tục chạy nền, không thể bảo đảm máy chủ không đóng kết nối hoặc hệ điều hành không chấm dứt tiến trình trong mọi hoàn cảnh.

## Kiểm tra source

Chạy từ thư mục gốc project:

```sh
python3 tools/verify-treo-integration.py
```

## Bảo toàn

`J2ME_NST.apk` gốc không bị chỉnh sửa. Gói này chỉ thay đổi source FPS Rebuild v3.
