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
  <main class="login">
    <form class="card login-card" @submit.prevent="go">
      <h2>Đăng nhập EscrowLab</h2>
      <p class="muted">
        Bạn sẽ được chuyển sang trang đăng nhập SSO. Nếu đã đăng nhập ở ứng dụng khác dùng chung SSO
        (ví dụ Reliable Order Platform), bạn vào thẳng mà không phải nhập lại mật khẩu.
      </p>
      <p v-if="auth.error" class="login-error" role="alert">Đăng nhập không thành công: {{ auth.error }}</p>
      <button class="btn" type="submit" :disabled="busy">{{ busy ? 'Đang chuyển hướng...' : 'Đăng nhập bằng SSO' }}</button>
      <p v-if="demo" class="muted login-hint">
        Tài khoản demo: <code>alice</code> (mua), <code>bob</code> (bán), <code>carol</code> (mua), <code>admin</code>.
        Mật khẩu: <code>Passw0rd!</code>
      </p>
    </form>
  </main>
</template>
