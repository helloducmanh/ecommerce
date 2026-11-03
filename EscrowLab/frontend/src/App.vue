<script setup>
import { ref, computed, watch } from 'vue'
import { state, notify, vnd } from './store'
import { api } from './api'
import { auth, logout } from './auth'
import LoginView from './components/LoginView.vue'
import OrdersView from './components/OrdersView.vue'
import WalletView from './components/WalletView.vue'
import LedgerView from './components/LedgerView.vue'
import AdminView from './components/AdminView.vue'
import SimulatorView from './components/SimulatorView.vue'

const tab = ref('orders')
const me = computed(() => state.users.find((u) => u.id === state.userId))
const isAdmin = computed(() => me.value?.role === 'admin')

const tabs = computed(() => {
  const list = [{ id: 'orders', label: 'Đơn hàng' }]
  if (!isAdmin.value) list.push({ id: 'wallet', label: 'Ví' })
  list.push({ id: 'ledger', label: 'Sổ cái' })
  if (isAdmin.value) list.push({ id: 'admin', label: 'Quản trị & đối soát' })
  list.push({ id: 'sim', label: 'Giả lập gateway' })
  return list
})
const views = { orders: OrdersView, wallet: WalletView, ledger: LedgerView, admin: AdminView, sim: SimulatorView }

async function loadWallet() {
  try {
    state.wallet = await api.get('/api/me/wallet')
  } catch (e) {
    notify(e.message, 'err')
  }
}

// Sau khi đăng nhập: nạp danh sách user (lần gọi đầu tiên cũng tự tạo user + ví trong DB) rồi nạp ví.
async function boot() {
  try {
    state.users = await api.get('/api/users')
    await loadWallet()
  } catch (e) {
    if (auth.user) notify('Không kết nối được backend (http://localhost:5081). ' + e.message, 'err')
  }
}

// state.userId = sub của token = users.id trong DB, nên các màn hình cũ (OrdersView, LedgerView...) chạy không cần sửa.
watch(
  () => auth.user?.profile.sub,
  (sub) => {
    state.userId = sub || ''
    tab.value = 'orders'
    state.wallet = null
    if (sub) boot()
  },
  { immediate: true },
)
</script>

<template>
  <template v-if="auth.user">
    <header class="topbar">
      <span class="brand">EscrowLab</span>
      <div class="who">
        <small>Đăng nhập với</small>
        <strong>{{ me?.name || auth.user.profile.name || auth.user.profile.preferred_username }}</strong>
        <span v-if="me" class="role">{{ me.role }}</span>
      </div>
      <button class="btn ghost small" @click="logout">Đăng xuất</button>
      <span class="spacer"></span>
      <div v-if="state.wallet?.hasWallet" class="balance">
        <small>Số dư ví</small>
        <strong>{{ vnd(state.wallet.balance) }}</strong>
      </div>
    </header>

    <nav class="tabs">
      <button v-for="t in tabs" :key="t.id" :class="{ active: tab === t.id }" @click="tab = t.id">{{ t.label }}</button>
    </nav>

    <main>
      <component :is="views[tab]" v-if="me" :key="state.userId + tab" @changed="loadWallet" />
    </main>
  </template>

  <LoginView v-else />

  <div v-if="state.toast" class="toast" :class="{ err: state.toast.type === 'err' }" role="status">{{ state.toast.msg }}</div>
</template>
