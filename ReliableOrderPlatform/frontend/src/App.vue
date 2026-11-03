<script setup>
import { ref, computed, watch } from 'vue'
import Storefront from './components/Storefront.vue'
import CartPanel from './components/CartPanel.vue'
import OrdersView from './components/OrdersView.vue'
import AdminProducts from './components/AdminProducts.vue'
import LoginView from './components/LoginView.vue'
import { cartCount, clearCart } from './store'
import { auth, logout, isAdmin } from './auth'

const tab = ref('shop')
// Tab quản trị chỉ hiện với admin (chỉ để gọn giao diện; backend mới là nơi chặn quyền thật).
const tabs = computed(() => [
  { id: 'shop', label: 'Cửa hàng' },
  { id: 'cart', label: 'Giỏ hàng' },
  { id: 'orders', label: 'Đơn hàng' },
  ...(isAdmin.value ? [{ id: 'admin', label: 'Quản trị sản phẩm' }] : [])
])
const displayName = computed(() => auth.user?.profile.name || auth.user?.profile.preferred_username || '')

// Đăng nhập người khác / đăng xuất thì về tab đầu
watch(() => auth.user?.profile.sub, () => { tab.value = 'shop' })

async function doLogout() {
  clearCart()   // giỏ hàng lưu ở localStorage: không để lại cho người dùng kế tiếp trên cùng trình duyệt
  await logout()
}
</script>

<template>
  <template v-if="auth.user">
    <header class="top">
      <div class="container">
        <span class="brand">Reliable Order Platform</span>
        <nav>
          <button v-for="t in tabs" :key="t.id" :class="{ active: tab === t.id }" @click="tab = t.id">
            {{ t.label }}<template v-if="t.id === 'cart' && cartCount"> ({{ cartCount }})</template>
          </button>
        </nav>
        <div class="who">
          <span>{{ displayName }}</span>
          <span v-if="isAdmin" class="role">admin</span>
          <button class="btn ghost" @click="doLogout">Đăng xuất</button>
        </div>
      </div>
    </header>
    <main class="container">
      <Storefront v-if="tab === 'shop'" @go-cart="tab = 'cart'" />
      <CartPanel v-else-if="tab === 'cart'" @ordered="tab = 'orders'" />
      <OrdersView v-else-if="tab === 'orders'" />
      <AdminProducts v-else-if="tab === 'admin' && isAdmin" />
    </main>
  </template>

  <LoginView v-else />
</template>
