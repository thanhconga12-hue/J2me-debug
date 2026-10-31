# J2ME NST FPS — bản source tái dựng

Đây là dự án source mới dựa trên JL-Mod-dev, có tích hợp FPS Booster và một số dữ liệu tương thích lấy từ APK NST mà bạn gửi. **Đây chưa phải APK đã build hoặc kiểm thử trên điện thoại.**

## Phần đã mang từ NST vào

- Tên ứng dụng: `J2ME NST FPS`.
- Package mới: `com.nst.j2me1.fpsboost` (cài song song, không ghi đè package NST cũ `com.nst.j2me1`).
- Thư mục dữ liệu mặc định: `/J2ME-NST`.
- `system.props` và `Roboto-Regular.ttf` lấy từ APK NST.
- Bộ máy giả lập Java/Kotlin và native dùng source JL-Mod-dev để tránh ghép nhầm thư viện `.so` khác phiên bản.

## Tương thích game NST đang có

- Bộ quét game chấp nhận cache cũ `converted.zip` / `converted.dex` hoặc `res.jar` + manifest, không xóa game chỉ vì cache FPS Booster chưa tồn tại.
- Bản mới ghi cache riêng `converted_nstfps.zip` / `converted_nstfps.dex`, không ghi đè cache DEX của NST cũ.
- Khi mở game, app tính SHA-256 của `res.jar`; nếu JAR thay đổi thì tự chuyển đổi lại cache riêng.
- Cả hai app dùng chung `/J2ME-NST` và tên database `J2ME-apps.db`. Bảng `apps` trong DEX NST có các trường khớp model `AppItem` của source, nhưng migration chưa được kiểm tra trên app đã build. Hãy sao lưu thư mục này và chỉ mở một app tại một thời điểm để tránh hai tiến trình cùng ghi RMS/database.
- Nếu NST cũ đang dùng thư mục làm việc tùy chỉnh, hãy chọn cùng đường dẫn đó trong cài đặt của app mới. Cài đặt riêng tư theo package không tự chia sẻ giữa hai app.

## FPS Booster

- Mục tiêu kết xuất: không giới hạn, 30, 60, 90 hoặc 120 FPS. Đây là giới hạn trần, không tự sinh frame; FPS thực tế phụ thuộc game và phần cứng.
- Ép logic: 1.5×, 2×, 3×, 4×; mặc định tắt.
- Tác động các API thời gian phổ biến trong bytecode game đã chuyển đổi: `Thread.sleep`, timed `join`, `Object.wait`, `TimeUnit.sleep`, `LockSupport.parkNanos/parkUntil`, `System.currentTimeMillis/nanoTime`, và `Timer`.
- `Thread.yield()` vẫn giữ nguyên ý nghĩa nhường lịch CPU.
- Không thể bảo đảm tương thích với mọi nguồn thời gian riêng, JNI/native, busy-loop, đồng hồ âm thanh hay thời gian máy chủ. Game online có nguy cơ lệch đồng bộ. Chỉ bật Logic Speed để thử nghiệm từng game.

## Chức năng NST chưa được chuyển

APK NST có các lớp đã biên dịch nhưng không có source tương ứng trong JL-Mod-dev ZIP: `LauncherRouterActivity`, nhóm `BubbleService`/`BubbleSettings*`, nhóm `Treo*` và `DocumentProvider`. APK còn có bộ điều phối FPS nền/thích ứng riêng (các symbol `backgroundFps`, `effectiveFpsLimit`, `allowFrame`, `acceptedFrames`, `droppedFrames`) không có mã nguồn tương ứng trong repository gửi lên. Icon NST cũng chưa được ánh xạ chắc chắn từ bảng tài nguyên đã biên dịch nên source giữ icon JL-Mod. Vì vậy source này **chưa phải bản thay thế đầy đủ mọi chức năng NST**.

## Cách build

Yêu cầu JDK 17, Android SDK Platform 34, Build Tools 34.0.0, NDK `22.1.7171670`, Gradle Wrapper 8.13 và kết nối mạng để tải dependency.

```sh
./scripts/fetch-tinysoundfont.sh
./gradlew :app:assembleEmulatorDebug
```

APK dự kiến:

`app/build/outputs/apk/emulator/debug/J2ME-NST-FPS_3.0-nst-fps-emulator-debug.apk`

Có workflow build debug thủ công tại `.github/workflows/nst-fps-debug.yml`.

Bản build tại môi trường hiện tại bị chặn vì không phân giải được `services.gradle.org`; Android SDK/NDK cũng không có sẵn. Do đó chưa có APK cài đặt để gửi. Xem `NST_INTEGRATION_NOTES.md` để biết đầy đủ giới hạn và trạng thái kiểm tra.
