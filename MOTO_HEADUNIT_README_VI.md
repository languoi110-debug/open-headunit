# Moto Headunit — Samsung J2 Prime SM-G532G/DS

Bản tùy biến này dựa trên Open Headunit và nhắm tới:

- Máy chính: Samsung Galaxy S24 FE
- Màn hình/headunit: Samsung Galaxy J2 Prime SM-G532G/DS
- Mục đích: dùng Android Auto trên xe máy, ưu tiên Native AA / Wi‑Fi Direct.

## Preset dành cho J2 Prime

- Android Auto vehicle type: MOTORCYCLE (3)
- Head-unit microphone: OFF → S24 FE hoặc intercom/headset ghép với S24 FE đảm nhiệm mic
- Video: 800×480 (480p)
- FPS: 30
- Codec: H.264
- Renderer mặc định: GLES
- Tên headunit: Moto Headunit
- Package riêng: `com.motoheadunit.j2prime`

Open Headunit hiện đã có hỗ trợ `VEHICLE_TYPE_MOTORCYCLE` trong giao thức Android Auto. Bản này đặt Motorcycle làm mặc định và tắt mic headunit để Android Auto dùng mic phía điện thoại/intercom.

## Build APK

Nhánh `moto-j2prime` chứa workflow `Build Moto Headunit APK`. Mỗi lần đẩy thay đổi lên nhánh này, GitHub Actions sẽ tự áp `moto/apply_moto_patch.py`, build biến thể `githubDebug`, rồi tải APK thành artifact `Moto-Headunit-J2Prime-APK`.

APK dự kiến có tên:

`com.motoheadunit.j2prime_0.1.0-j2prime_debug.apk`

## Thiết lập trên S24 FE

1. Cập nhật Android Auto.
2. Bật Bluetooth và Wi‑Fi.
3. Ghép Bluetooth với J2 Prime nếu Native AA yêu cầu thiết bị ghép đôi để wake/handshake.
4. Với Waze, chọn Vehicle type → Motorcycle.
5. Nếu dùng intercom Bluetooth, ghép intercom với S24 FE.

## Thiết lập trên J2 Prime

1. Cài APK Moto Headunit.
2. Cấp các quyền ứng dụng yêu cầu.
3. Trong phần Connection/Wi‑Fi chọn Native AA nếu chưa được chọn.
4. Chọn S24 FE ở phần thiết bị Bluetooth dùng cho Native AA.
5. Khi kết nối lần đầu, xác nhận Android Auto trên S24 FE.

## Nếu bị màn hình đen hoặc giật

- Giữ 480p / 30 FPS / H.264.
- Thử đổi renderer giữa GLES / Texture / Surface.
- Nếu wireless không ổn định, thử USB trước để xác nhận phần projection/video hoạt động.

## Giấy phép

Open Headunit dùng AGPL-3.0. Khi phân phối APK/bản sửa, cần tuân thủ AGPL-3.0 và cung cấp mã nguồn tương ứng.

Upstream: https://github.com/andreknieriem/open-headunit
