import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// strictPort: nếu cổng bận thì báo lỗi, vì redirect URI đã đăng ký trên Keycloak gắn với đúng cổng này.
export default defineConfig({
  plugins: [vue()],
  server: { port: 5173, strictPort: true, proxy: { '/api': 'http://localhost:5080' } }
})
