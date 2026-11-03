# EscrowLab

Dự án học domain thanh toán: **escrow (giữ tiền trung gian)** + ví + nạp/rút tiền + đối soát.
Backend C# (.NET) + PostgreSQL, frontend Vue 3. Chạy hoàn toàn bằng tiền giả, không giữ tiền thật.

> Đây là công cụ học tập. Không dùng để giữ tiền thật của người khác: ở Việt Nam đó là hoạt động
> cần giấy phép trung gian thanh toán của Ngân hàng Nhà nước.

Đăng nhập dùng **SSO (OpenID Connect) qua Keycloak**, dùng chung với Reliable Order Platform:
đăng nhập một lần là vào được cả hai ứng dụng. Xem mục "Đăng nhập SSO" bên dưới.

## Cấu trúc

Cùng kiến trúc với Reliable Order Platform: `Controller -> Service -> AppDbContext (EF Core)`.

```
EscrowLab/
├─ docker-compose.yml            PostgreSQL 16 (cổng 5433)
├─ backend/
│  ├─ Directory.Build.props      net8.0
│  ├─ requests.http              Gọi API thử bằng VS Code REST Client
│  ├─ EscrowLab.Api/
│  │  ├─ Controllers/            Orders, Deposits, Withdrawals, Ledger, Reconciliation, Webhooks, Users, Dev (nhận request, trả response)
│  │  ├─ Services/               Ledger, Order, Payment(webhook), Withdrawal, Reconciliation, Dtos, worker nền
│  │  ├─ Domain/                 Entities, state machine, tính phí, EscrowOptions, DomainException
│  │  ├─ Data/                   AppDbContext, Seed, schema.sql (bảng + trigger bảo vệ sổ cái)
│  │  ├─ Auth/                   SsoAuth (kiểm tra JWT từ Keycloak, ánh xạ user)
│  │  └─ Program.cs              Đăng ký service và khởi động
│  └─ tests/EscrowLab.Tests/     xUnit (unit + tích hợp PostgreSQL)
└─ frontend/                     Vue 3 + Vite
```

Luồng: `Controller` -> `OrderService` / `PaymentService` / ... -> `LedgerService` -> `AppDbContext` (PostgreSQL).

**Vì sao vẫn là PostgreSQL chứ không phải SQLite như Reliable Order?** Sổ cái dựa vào những thứ SQLite không có:
khoá dòng `SELECT ... FOR UPDATE` theo thứ tự id (chống rút quá số dư khi chạy song song và chống deadlock),
trigger chặn sửa/xoá sổ cái và trigger kiểm tra mỗi giao dịch cân bằng lúc COMMIT. Vì vậy:
- Bảng và trigger vẫn do `Data/schema.sql` tạo (EF `EnsureCreated` không sinh được trigger). `AppDbContext` chỉ ánh xạ lại các bảng đó.
- Các câu lệnh cần khoá hoặc `ON CONFLICT DO NOTHING` vẫn là SQL thô, chạy qua `DbContext` (`Database.ExecuteSqlAsync`), trong cùng transaction với phần EF.
- Mọi thao tác ghi tiền đều đi qua `db.InTransactionAsync(...)` (tự retry khi deadlock).

## Chạy

Cần: Docker, .NET 8 SDK, Node 18+.

```bash
# 0) Keycloak (SSO), chạy ở http://localhost:8180. Thư mục sso/ nằm cạnh project này
cd ../sso && docker compose up -d && cd -

# 1) Database
docker compose up -d

# 2) Backend (tự tạo bảng và dữ liệu mẫu), chạy ở http://localhost:5081
cd backend
dotnet run --project EscrowLab.Api

# 3) Frontend, mở http://localhost:5174
cd frontend
npm install
npm run dev
```

Cổng của EscrowLab là 5081 (API) và 5174 (web) để chạy song song được với Reliable Order Platform (5080 và 5173).

## Đăng nhập SSO

- Frontend là public client dùng **Authorization Code + PKCE** (thư viện `oidc-client-ts`, xem `frontend/src/auth.js`).
  Bấm "Đăng nhập bằng SSO" sẽ chuyển sang Keycloak, đăng nhập xong quay về `/auth/callback` và nhận token.
- Mọi request tới `/api` gửi `Authorization: Bearer <access_token>`. Backend kiểm tra chữ ký (JWKS), issuer,
  audience `escrow-lab` và hạn dùng (`EscrowLab.Api/Auth/SsoAuth.cs`). Mặc định mọi endpoint đều yêu cầu đăng nhập;
  riêng hai webhook thanh toán là công khai vì có cơ chế xác thực riêng (HMAC, API key).
- Danh tính: `users.id` = claim `sub` của Keycloak. Lần đầu một người đăng nhập, backend tự tạo dòng `users` và ví.
- Vai trò (`buyer`, `seller`, `admin`) lấy từ client role `escrow-lab` trên Keycloak, được đồng bộ vào bảng `users`
  mỗi khi khác. Không có role nào thì mặc định là `buyer`.
- Tài khoản demo (mật khẩu `Passw0rd!`): `alice` (buyer), `bob` (seller), `carol` (buyer), `admin`.
  Id của họ trùng với dữ liệu seed, nên vào là có sẵn Alice/Bob/Carol/Admin.
- Không còn ô "đang đóng vai" để chuyển người dùng. Muốn đóng nhiều vai cùng lúc, mở thêm cửa sổ ẩn danh
  hoặc trình duyệt khác (mỗi trình duyệt có một phiên SSO riêng).

Test:

```bash
cd backend
dotnet test tests/EscrowLab.Tests                     # chỉ unit test
ESCROW_TEST_DB="Host=localhost;Port=5433;Database=escrowlab;Username=escrow;Password=escrow" \
  dotnet test tests/EscrowLab.Tests                   # thêm test đua tranh trên PostgreSQL thật
```

## Kịch bản học (làm theo thứ tự)

1. **Nạp tiền**: đăng nhập bằng Alice, tab Ví, tạo yêu cầu nạp. Sang tab Giả lập gateway, bấm "Thanh toán thành công".
   Ví tăng. Mở tab Sổ cái để xem bút toán `EXTERNAL_BANK -X / WALLET +X`.
2. **Idempotency**: tạo yêu cầu nạp khác, tick "Gửi 2 lần", bấm thanh toán. Lần 2 trả `duplicate`, ví chỉ tăng một lần.
3. **Chữ ký**: tick "Ký sai" rồi bấm. Webhook bị từ chối `INVALID_SIGNATURE`, ví không đổi.
4. **Số tiền lệch**: nhập "Ghi đè số tiền" khác số gốc. Giao dịch chuyển sang `REVIEW`, không tự cộng tiền.
5. **Escrow**: Alice tạo đơn cho Bob, thanh toán (tiền vào `ESCROW`). Đăng nhập Bob (cửa sổ ẩn danh): giao hàng.
   Về Alice: xác nhận, Bob nhận tiền trừ 2% phí vào `FEE_REVENUE`.
6. **Tranh chấp**: Alice khiếu nại đơn đã giao, đăng nhập Admin để phán quyết trả hoặc hoàn tiền.
7. **Rút tiền 2 pha**: Bob rút tiền (tiền vào `WITHDRAWAL_HOLD`), Admin hoàn tất hoặc từ chối.
8. **Hết hạn / tự giải ngân**: tab Giả lập gateway, tua thời gian của đơn rồi chạy việc nền.
9. **Đối soát**: Admin, lấy sao kê "Thiếu một dòng", "Sai số tiền", "Có dòng lạ" rồi chạy đối soát để xem báo cáo.

## Tích hợp SePay (Test mode) và mã QR chuyển khoản

Luồng: người dùng tạo yêu cầu nạp, hệ thống sinh mã `EL…` và ảnh QR (`https://qr.sepay.vn/img?acc=..&bank=..&amount=..&des=..`).
Khi SePay phát hiện tiền vào, họ gọi `POST /api/webhooks/sepay`, hệ thống xác thực API key, chống trùng, khớp mã, đối chiếu số tiền rồi cộng ví.

**Thử ngay không cần SePay:** tab Giả lập gateway, bấm "Webhook kiểu SePay" trên một yêu cầu nạp. Backend tự dựng payload giống SePay và gọi chính bộ xử lý thật.

**Thử với SePay Test mode (làm theo thứ tự):**

1. Đăng ký tại my.dev.sepay.vn, liên hệ SePay để họ kích hoạt tài khoản Test mode.
2. Trong Test mode, tạo tài khoản ngân hàng test. Lấy tên ngắn của ngân hàng (xem `qr.sepay.vn/banks.json`) và số tài khoản, điền vào `backend/EscrowLab.Api/appsettings.json`:
   `BankName`, `BankAccountNumber`, `AccountHolder`.
3. Mở đường hầm công khai tới cổng 5081, ví dụ `ngrok http 5081` hoặc `cloudflared tunnel --url http://localhost:5081`.
4. Trong Test mode, tạo webhook: URL là `https://<địa-chỉ-tunnel>/api/webhooks/sepay`, chứng thực **API Key**, kiểu nội dung JSON.
   Đặt API key cho backend bằng biến môi trường cùng giá trị đó, không ghi vào file:
   ```bash
   export Escrow__SePayApiKey="khoa-ban-vua-dat"      # PowerShell: $env:Escrow__SePayApiKey="..."
   ```
5. (Khuyến nghị) Cấu hình cấu trúc mã thanh toán trên SePay với tiền tố `EL`. Không bắt buộc, vì backend vẫn tự tìm mã `EL…` trong nội dung chuyển khoản.
6. Tạo yêu cầu nạp trong tab Ví, thấy QR và nội dung chuyển khoản. Trên SePay Test mode, mô phỏng giao dịch **Tiền vào** với đúng số tiền và nội dung là mã `EL…`. Webhook bay về, ví được cộng.

Kết quả webhook: `processed`, `duplicate` (gửi trùng), `ignored`, `needs_review` (lệch số tiền), `unmatched` (tiền về nhưng không khớp mã nào).
Hai trạng thái cuối nghĩa là tiền đã vào ngân hàng mà chưa vào ví, ở bản thật cần một màn hình cho admin xử lý.

Ở Test mode, ảnh QR chỉ để xem hình hiển thị, không dùng app ngân hàng thật để quét.

## Mỗi khái niệm nằm ở đâu

| Khái niệm | File |
|---|---|
| Sổ cái ghi sổ kép, khoá theo thứ tự, kiểm tra số dư dưới khoá | `Services/LedgerService.cs` |
| Controller mỏng, gọi service | `Controllers/*.cs` |
| Ledger bất biến và luôn cân bằng ở tầng DB | `Data/schema.sql` (trigger) |
| State machine của đơn | `Domain/OrderStateMachine.cs` |
| Một thao tác = khoá dòng + kiểm quyền + ghi sổ + đổi trạng thái + audit trong 1 transaction | `OrderService.TransitionAsync` |
| Idempotency key | `LedgerService`, khoá `order:{id}:{status}`, `deposit:{id}` |
| Webhook: HMAC, chống replay, chống trùng event | `WebhookSigner.cs`, `PaymentService.HandleWebhookAsync` |
| Webhook SePay, sinh QR, khớp mã nạp tiền | `Services/SePay.cs`, `PaymentService.HandleSePayAsync` |
| Retry khi deadlock | `Data/TransactionExtensions.cs` (`InTransactionAsync`) |
| Đối soát ngoài + toàn vẹn nội bộ | `ReconciliationService.cs` |
| Việc nền | `MaintenanceWorker.cs` |

## API chính

| Method | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/api/users`, `/api/me/wallet` | `Authorization: Bearer <token>` (mọi endpoint trừ health và webhook) |
| GET/POST | `/api/orders` | tạo đơn: `{sellerId,title,amount}` |
| POST | `/api/orders/{id}/pay`, `ship`, `confirm`, `cancel`, `dispute`, `refund` | |
| POST | `/api/orders/{id}/resolve` | admin: `{decision:"release"|"refund"}` |
| GET/POST | `/api/deposits` | |
| POST | `/api/webhooks/gateway` | header `X-Gateway-Signature: t=...,v1=...` |
| POST | `/api/webhooks/sepay` | header `Authorization: Apikey <khoá>` |
| GET/POST | `/api/withdrawals`, `/{id}/complete`, `/{id}/reject` | |
| GET | `/api/ledger`, `/api/admin/accounts` | |
| POST | `/api/reconciliation/run` | admin |
| * | `/api/dev/...` | công cụ học, tắt bằng `Escrow:EnableDevEndpoints=false` |

## Bài tập mở rộng

- Thêm hoàn tiền một phần khi tranh chấp (tách `amount` giữa hai bên).
- Thêm bảng `outbox` rồi gửi webhook ra cho merchant với retry và exponential backoff.
- Chuyển sang mô hình BFF (cookie HttpOnly phía backend giữ token) để token không nằm trong trình duyệt.
- Hỗ trợ nhiều loại tiền (`currency`), tỷ giá và bút toán chênh lệch.
- Thêm bảng giới hạn rút tiền theo ngày và chấm điểm rủi ro đơn giản.
- Chạy test song song 50 request để tìm điểm nghẽn ở tài khoản `ESCROW` (hot row), rồi nghĩ cách sharding.
