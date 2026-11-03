# SSO dùng chung (Keycloak)

Một Keycloak, một realm `main`, hai client. Đăng nhập ở một ứng dụng thì ứng dụng kia vào thẳng, không hỏi lại mật khẩu.

| Ứng dụng | client_id | Web | API |
|---|---|---|---|
| Reliable Order Platform | `reliable-order-platform` | http://localhost:5173 | http://localhost:5080 |
| EscrowLab | `escrow-lab` | http://localhost:5174 | http://localhost:5081 |

## Chạy

```bash
docker compose up -d
```

- Trang quản trị Keycloak: http://localhost:8180 (tài khoản `admin` / `admin`, chỉ dùng cho dev).
- Realm được import tự động từ `realm-main.json` ở lần chạy đầu. Sửa file này xong phải xoá volume
  (`docker compose down -v`) thì mới import lại.

## Tài khoản demo (mật khẩu `Passw0rd!`)

| Username | Role `escrow-lab` | Role `reliable-order-platform` |
|---|---|---|
| `alice` | buyer | (khách) |
| `bob` | seller | (khách) |
| `carol` | buyer | (khách) |
| `admin` | admin | admin |

Id của 4 user này trùng với id seed trong bảng `users` của EscrowLab, nên đăng nhập là vào đúng dữ liệu có sẵn.

## Cách hai app dùng chung phiên

1. Đăng nhập lần đầu: app redirect sang Keycloak, Keycloak hiện form, bạn nhập mật khẩu,
   Keycloak đặt cookie phiên SSO trên domain `localhost:8180` rồi trả `code` về app.
2. Mở app còn lại: app redirect sang Keycloak, trình duyệt tự gửi cookie phiên đó, Keycloak thấy phiên còn hạn
   nên trả `code` ngay, không hiện form.
3. Mỗi app có client riêng, token riêng (`aud` khác nhau), nên token của app này không dùng được ở API của app kia.

## Cấu hình đã đặt trong realm

- Client là **public client**, chỉ cho phép Authorization Code, bắt buộc PKCE (`S256`).
- Redirect URI khớp chính xác `http://localhost:<cổng>/auth/callback`.
- Mỗi client có *audience mapper* để access token mang `aud` = client_id của chính nó.
- Role khai báo theo từng client (`escrow-lab`: buyer/seller/admin; `reliable-order-platform`: admin). Một người có thể
  là admin ở app này mà chỉ là khách ở app kia.
- Access token sống 5 phút, app tự gia hạn bằng refresh token. Phiên SSO tối đa 10 giờ, nghỉ quá 30 phút thì hết.
- `directAccessGrantsEnabled` đang bật chỉ để `requests.http` / `curl` lấy token thử bằng username và password.
  **Tắt nó** nếu không cần.

## Đưa lên môi trường thật

- Chạy Keycloak ở chế độ `start` (không phải `start-dev`), sau HTTPS, với database riêng; đổi mật khẩu admin.
- Thay `localhost` trong `redirectUris`, `webOrigins`, `post.logout.redirect.uris` bằng domain thật; bỏ user demo.
- Sửa `Auth:Authority` ở hai API và `VITE_OIDC_AUTHORITY` ở hai frontend.
- Với EscrowLab (liên quan tới tiền), nên chuyển sang mô hình BFF: backend giữ token trong cookie HttpOnly,
  trình duyệt không bao giờ cầm token.
