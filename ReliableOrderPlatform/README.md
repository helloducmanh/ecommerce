# Reliable Order Platform (base project)

Backend **C# / ASP.NET Core 8** (Controller + EF Core + SQLite) và frontend **Vue 3 + Vite**.

## Chạy thử

Yêu cầu: .NET 8 SDK, Node.js 18+, Docker (cho Keycloak).

```bash
# Terminal 0 - Keycloak SSO (http://localhost:8180). Thư mục sso/ nằm cạnh project này
cd ../sso
docker compose up -d

# Terminal 1 - backend (http://localhost:5080, Swagger tại /swagger)
cd backend/ReliableOrder.Api
dotnet run

# Terminal 2 - frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

Lần chạy đầu tiên backend tự tạo file `reliable-orders.db` và seed 3 sản phẩm mẫu.

## Đăng nhập SSO

Đăng nhập dùng **OpenID Connect qua Keycloak**, dùng chung với EscrowLab: đăng nhập một lần là vào được cả hai ứng dụng.

- Frontend là public client **Authorization Code + PKCE** (`oidc-client-ts`, xem `frontend/src/auth.js`).
  Chưa đăng nhập thì chỉ thấy màn hình "Đăng nhập bằng SSO".
- Mọi request tới `/api` gửi `Authorization: Bearer <access_token>`. Backend kiểm tra chữ ký (JWKS), issuer,
  audience `reliable-order-platform`, hạn dùng (`Auth/SsoAuth.cs`). Mặc định mọi endpoint đều yêu cầu đăng nhập.
- Phân quyền:
  - `GET /api/products`: công khai.
  - Đặt hàng, xem đơn: cần đăng nhập. Tên và email khách lấy từ token (không tin dữ liệu client gửi lên);
    khách chỉ thấy/huỷ/xác nhận đơn có email của mình, admin thấy tất cả.
  - Tạo/xoá sản phẩm, sửa biến thể: chỉ client role `admin` của `reliable-order-platform`.
- Tài khoản demo (mật khẩu `Passw0rd!`): `alice` (khách), `admin` (quản trị).
- Đơn tạo trước khi có SSO có email do người dùng tự nhập, nên chỉ admin xem được.
- Cấu hình ở `appsettings.json`, mục `Auth` (`Authority`, `ClientId`).

## Cấu trúc backend

```
Controllers/   ProductsController, VariantsController, OrdersController  (nhận request, trả response)
Auth/          SsoAuth (kiểm tra JWT từ Keycloak, đọc role)
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

(trừ `GET /api/products*`, mọi endpoint cần header `Authorization: Bearer <access_token>`)

```
GET  /api/products                  POST /api/products
GET  /api/products/{id}             DELETE /api/products/{id}   (soft delete)
PUT  /api/variants/{id}             (giá, tồn kho, bật/tắt bán)
GET  /api/orders                    GET /api/orders/{id}
POST /api/orders                    (bắt buộc header Idempotency-Key)
POST /api/orders/{id}/confirm       POST /api/orders/{id}/cancel
```

Thử retry an toàn (lấy token của alice bằng password grant, chỉ dùng cho dev):

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/main/protocol/openid-connect/token \
  -d grant_type=password -d client_id=reliable-order-platform -d username=alice -d 'password=Passw0rd!' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')

curl -X POST http://localhost:5080/api/orders -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: demo-123" \
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
