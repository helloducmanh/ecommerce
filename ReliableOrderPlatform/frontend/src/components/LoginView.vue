<script setup>
import { ref } from 'vue'
import { auth, login } from '../auth'

const busy = ref(false)
const demo = import.meta.env.DEV   // chỉ hiện gợi ý tài khoản demo khi chạy `npm run dev`

function go() {
  busy.value = true
  login()   // chuyển trình duyệt sang trang đăng nhập của Keycloak
}
</script>

<template>
  <main class="container login">
    <form class="card login-card" @submit.prevent="go">
      <h2>Đăng nhập Reliable Order Platform</h2>
      <p class="muted">
        Bạn sẽ được chuyển sang trang đăng nhập SSO. Nếu đã đăng nhập ở ứng dụng khác dùng chung SSO
        (ví dụ EscrowLab), bạn vào thẳng mà không phải nhập lại mật khẩu.
      </p>
      <div v-if="auth.error" class="msg err" role="alert">Đăng nhập không thành công: {{ auth.error }}</div>
      <button class="btn" type="submit" :disabled="busy">{{ busy ? 'Đang chuyển hướng...' : 'Đăng nhập bằng SSO' }}</button>
      <p v-if="demo" class="muted">
        Tài khoản demo: <code>alice</code> (khách), <code>admin</code> (quản trị). Mật khẩu: <code>Passw0rd!</code>
      </p>
    </form>
  </main>
</template>
