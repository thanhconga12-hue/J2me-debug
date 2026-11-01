# Bản vá tiến trình Treo đã áp dụng

Đã áp dụng vào source archive `J2ME_NST_FPS_Rebuild_v3_Source.zip` bằng cách sửa:

- `app/src/debug/AndroidManifest.xml`: giữ `MicroActivity` trong tiến trình `:midlet`.
- `app/src/midlet/AndroidManifest.xml`: giữ `MicroActivity` trong tiến trình `:midlet`.

Mục đích: khi tích hợp Treo Service, Activity và Service phải nằm cùng tiến trình để trạng thái `static` của `TreoOnlineService` được nhìn thấy thống nhất.

## Phạm vi và giới hạn

Gói source gốc được cung cấp ở đây không chứa mã nguồn Java của `TreoOnlineService`, `TreoBootReceiver` hoặc `TreoTileService`; các lớp này xuất hiện trong DEX của APK TREO v3 đã build. Vì vậy gói này chứa bản vá Manifest đã áp dụng lên source archive, nhưng không thể tự nó tái tạo đầy đủ APK TREO v3 nếu thiếu phần source Treo tương ứng.

Không chỉnh sửa APK NST gốc, DEX, dữ liệu game, RMS hoặc logic game. Chưa build APK và chưa thử runtime trên thiết bị.
