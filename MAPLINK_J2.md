# MapLink J2

MapLink J2 là bản giao diện chuyên dụng dựa trên Open Headunit để dùng **Samsung S24 FE** làm máy chính chạy Google Maps/Android Auto và **Samsung J2 Prime** làm màn hình phụ.

## Cấu hình đã tối ưu

- Android tối thiểu: 4.1; phù hợp Android 6.0.1 trên J2 Prime.
- Màn hình ngang, toàn màn hình.
- Video 800 × 480, H.264, giới hạn 30 FPS.
- SurfaceView để giảm tải GPU/RAM trên máy cũ.
- GPS, micro và âm thanh mặc định lấy từ S24 FE; J2 Prime tập trung giải mã hình ảnh.
- Nút chính dùng Headunit Server qua Wi‑Fi; USB OTG là phương án dự phòng.
- Sau lần kết nối thành công, ứng dụng tự thử nối lại phiên gần nhất.

## Cách kết nối Wi‑Fi

1. Trên S24 FE, mở cài đặt **Android Auto**.
2. Chạm vào mục **Phiên bản** 10 lần để bật chế độ nhà phát triển.
3. Mở menu ba chấm, chọn **Start headunit server**.
4. Cho S24 FE và J2 Prime vào cùng mạng Wi‑Fi. Có thể bật Điểm truy cập di động trên S24 FE rồi cho J2 Prime kết nối vào.
5. Mở MapLink J2 trên J2 Prime, chấp nhận cảnh báo an toàn và các quyền cần thiết trong lần đầu.
6. Nhấn **KẾT NỐI BẢN ĐỒ**. Khi Android Auto xuất hiện, mở Google Maps và chọn hành trình trên S24 FE.

Lưu ý: với Android Auto 17.4 trở lên, phần lớn trình kích hoạt không dây của bên thứ ba không còn tự khởi chạy được. Vì vậy có thể phải chọn **Start headunit server** lại sau khi khởi động điện thoại.

## Cách kết nối USB OTG

1. Kiểm tra đúng mã J2 Prime. USB OTG được ghi nhận trên một số biến thể như SM‑G532F/SM‑G532G; các biến thể khác cần thử thực tế.
2. Cắm đầu OTG micro‑USB vào J2 Prime, rồi nối cáp dữ liệu USB‑A sang USB‑C tới S24 FE.
3. Mở MapLink J2 và nhấn **USB OTG**.
4. Chấp nhận hộp thoại quyền USB trên cả hai máy nếu được hỏi.

USB thường ổn định hơn Wi‑Fi nhưng J2 Prime có thể không vừa làm USB host vừa tự sạc với cáp OTG thường. Nếu dùng lâu trên xe, cần bộ chia/Y‑cable OTG có nguồn và phải kiểm tra khả năng tương thích trước.

## Chế độ soi bản đồ xe máy (MapLink Mirror)

Google Maps không cho dùng tuyến xe máy trong giao diện Android Auto. MapLink Mirror truyền trực tiếp màn hình S24 FE sang J2 Prime nên vẫn giữ được chế độ xe máy.

1. Cài APK **MapLink J2 Receiver** trên J2 Prime và APK **MapLink S24 Sender** trên S24 FE.
2. Bật điểm truy cập di động 2,4 GHz trên J2 Prime, sau đó cho S24 FE kết nối vào mạng này.
3. Trên J2 Prime, mở MapLink J2 và chọn **SOI MAP XE MÁY**. Giữ màn hình chờ mở.
4. Trên S24 FE, mở MapLink S24 Sender và chọn **BẮT ĐẦU TRUYỀN MÀN HÌNH**.
5. Trong hộp thoại hệ thống, cho phép chia sẻ **toàn bộ màn hình**. Ứng dụng tự tìm J2 Prime và mở Google Maps.
6. Chọn tuyến xe máy trên S24 FE. Hình ảnh được truyền H.264 thích ứng theo tỷ lệ màn hình, 24 FPS sang J2 Prime; âm thanh chỉ đường phát từ S24 FE.

Từ bản Receiver 1.2.0, J2 Prime tự mở màn hình nhận khi kết nối Wi‑Fi, tự ẩn thanh trạng thái/nút điều khiển và phóng cắt giữa để phủ kín màn hình. Chạm một lần lên hình ảnh để hiện lại nút thoát. Sender 1.1.0 tự mở Google Maps sau khi người dùng xác nhận quyền chia sẻ màn hình.

Chế độ này chỉ truyền hình ảnh, không điều khiển ngược S24 FE từ J2 Prime. Mỗi lần khởi động truyền, Android yêu cầu xác nhận quyền quay màn hình để bảo vệ riêng tư.

## Build APK bằng GitHub Actions

1. Đưa toàn bộ thư mục mã nguồn này lên một repository GitHub của bạn.
2. Mở tab **Actions** → **Build MapLink J2 APK** → **Run workflow**.
3. Khi tác vụ hoàn tất, tải hai artifact **maplink-j2-debug-apk** và **maplink-s24-sender-debug-apk**.
4. Cài APK Receiver trên J2 Prime và Sender trên S24 FE. Bật **Cho phép cài ứng dụng không rõ nguồn gốc** nếu Android yêu cầu.

Build thủ công dùng lệnh:

```bash
./gradlew :app:assembleGithubDebug :sender:assembleDebug
```

APK nằm trong `app/build/outputs/apk/github/debug/`.

## An toàn và giấy phép

Chỉ thao tác khi xe đang dừng. MapLink J2 kế thừa mã nguồn Open Headunit và giấy phép AGPL‑3.0; khi phân phối APK đã sửa đổi, cần cung cấp mã nguồn tương ứng theo giấy phép.
