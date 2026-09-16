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

## Build APK bằng GitHub Actions

1. Đưa toàn bộ thư mục mã nguồn này lên một repository GitHub của bạn.
2. Mở tab **Actions** → **Build MapLink J2 APK** → **Run workflow**.
3. Khi tác vụ hoàn tất, tải artifact **maplink-j2-debug-apk**.
4. Giải nén và chép APK sang J2 Prime để cài. Bật **Cho phép cài ứng dụng không rõ nguồn gốc** nếu Android yêu cầu.

Build thủ công dùng lệnh:

```bash
./gradlew :app:assembleGithubDebug
```

APK nằm trong `app/build/outputs/apk/github/debug/`.

## An toàn và giấy phép

Chỉ thao tác khi xe đang dừng. MapLink J2 kế thừa mã nguồn Open Headunit và giấy phép AGPL‑3.0; khi phân phối APK đã sửa đổi, cần cung cấp mã nguồn tương ứng theo giấy phép.
