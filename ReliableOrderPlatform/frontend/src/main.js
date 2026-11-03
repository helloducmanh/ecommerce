import { createApp } from 'vue'
import App from './App.vue'
import './style.css'
import { initAuth } from './auth'

// Xử lý đăng nhập (callback từ Keycloak) xong rồi mới dựng giao diện.
initAuth().then(() => createApp(App).mount('#app'))
