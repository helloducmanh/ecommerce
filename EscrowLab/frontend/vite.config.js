import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Mọi request /api được chuyển tiếp tới backend .NET, nên không cần cấu hình CORS khi dev.
// Cổng 5174 (không phải 5173) để chạy song song với Reliable Order Platform.
// strictPort: nếu cổng bận thì báo lỗi, vì redirect URI đã đăng ký trên Keycloak gắn với đúng cổng này.
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    strictPort: true,
    proxy: { '/api': 'http://localhost:5081' },
  },
})
