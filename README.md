# Reliable Order Platform (base project)

Backend **C# / ASP.NET Core 8** (Controller + EF Core + SQLite) và frontend **Vue 3 + Vite**.

## Chạy thử

Yêu cầu: .NET 8 SDK, Node.js 18+.

```bash
# Terminal 1 - backend (http://localhost:5080, Swagger tại /swagger)
cd backend/ReliableOrder.Api
dotnet run

# Terminal 2 - frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

Lần chạy đầu tiên backend tự tạo file `reliable-orders.db` và seed 3 sản phẩm mẫu.

## Cấu trúc backend

```
Controllers/   ProductsController, VariantsController, OrdersController  (nhận request, trả response)
Services/      OrderService (logic đặt hàng), ProductFactory (sinh biến thể), Outbox, Dtos
Domain/        Entities (Product, ProductVariant, Order, ...)
Data/          AppDbContext, Seed
Program.cs     Đăng ký service và khởi động
```

Luồng: `Controller` -> `OrderService` -> `AppDbContext` (database).

## Sản phẩm động (Product + Variant)

- `Product`: thông tin chung + `Attributes` (cặp key/value tuỳ ý, lưu JSON).
- `ProductOption`: các chiều biến thể do bạn tự định nghĩa (Màu sắc, Size, Dung lượng, ...).
- `ProductVariant`: từng SKU bán được, có giá và tồn kho riêng; `OptionValues` là JSON.
- Tạo sản phẩm với N option thì hệ thống tự sinh tích Descartes các biến thể (tối đa 200).
- Sản phẩm không có option sẽ có 1 biến thể mặc định.
- Thêm loại sản phẩm mới (giày, laptop, ...) **không cần sửa schema hay code**.

## Các cơ chế đáng tin cậy

| Vấn đề | Cách xử lý | Vị trí |
|---|---|---|
| Client retry gây đơn trùng | Header `Idempotency-Key`; cùng key + cùng body trả lại kết quả cũ, khác body trả 422 | `OrderService.CreateAsync` |
| Hai request cùng key chạy song song | PK của `IdempotencyRecord` chặn, request sau replay kết quả | `catch (DbUpdateException)` |
| Bán quá tồn kho | `UPDATE ... WHERE Stock >= qty` nguyên tử, không bao giờ âm kho | `OrderService.CreateAsync` |
| Mất sự kiện khi lỗi giữa chừng | Transactional outbox: đơn + sự kiện + idempotency ghi trong 1 transaction | `OutboxMessage` |
| Broker lỗi tạm thời | Worker retry exponential backoff, quá 5 lần vào dead-letter | `OutboxProcessor` |
| Huỷ đơn bấm nhiều lần | Huỷ/xác nhận là idempotent, huỷ sẽ hoàn kho | `CancelAsync` |
| Đổi giá sau khi đặt | `OrderItem` lưu snapshot tên/SKU/giá/tuỳ chọn | `OrderItem` |

## API chính

```
GET  /api/products                  POST /api/products
GET  /api/products/{id}             DELETE /api/products/{id}   (soft delete)
PUT  /api/variants/{id}             (giá, tồn kho, bật/tắt bán)
GET  /api/orders                    GET /api/orders/{id}
POST /api/orders                    (bắt buộc header Idempotency-Key)
POST /api/orders/{id}/confirm       POST /api/orders/{id}/cancel
```

Thử retry an toàn:

```bash
curl -X POST http://localhost:5080/api/orders -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-123" \
  -d '{"customerName":"A","customerEmail":"a@x.vn","items":[{"variantId":"<id>","quantity":1}]}'
# Chạy lại đúng lệnh trên: nhận cùng đơn, header Idempotent-Replayed: true, kho chỉ trừ 1 lần
```

## Hướng mở rộng tiếp theo

1. Đổi SQLite sang PostgreSQL/SQL Server (đổi provider trong `Program.cs`) và dùng EF Migrations thay `EnsureCreated`.
2. Thay `LoggingMessagePublisher` bằng RabbitMQ/Kafka/Azure Service Bus; consumer dedupe theo `OutboxMessage.Id`.
3. Thêm thanh toán và giữ chỗ tồn kho có hết hạn (saga: đặt hàng, thanh toán, xác nhận, bù trừ).
4. Thêm token concurrency (`Version`) cho `Order` khi chạy nhiều instance.
5. Xác thực/phân quyền (JWT), tách tab quản trị khỏi storefront, dọn `IdempotencyRecord` cũ theo TTL.
6. Viết test cho `OrderService` (đặt song song, retry cùng key, hết hàng).
