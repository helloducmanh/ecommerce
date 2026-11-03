<script setup>
import { ref, computed, onMounted } from 'vue'
import { state, notify, vnd, when } from '../store'
import { api } from '../api'

const entries = ref([])
const accounts = ref([])
const code = ref('')
const isAdmin = computed(() => state.users.find((u) => u.id === state.userId)?.role === 'admin')

async function load() {
  try {
    if (isAdmin.value && !accounts.value.length) accounts.value = await api.get('/api/admin/accounts')
    const q = isAdmin.value && code.value ? `&accountCode=${encodeURIComponent(code.value)}` : ''
    entries.value = await api.get(`/api/ledger?limit=100${q}`)
  } catch (e) { notify(e.message, 'err') }
}
onMounted(load)
</script>

<template>
  <section class="card">
    <h2>Sổ cái</h2>
    <p class="muted">Mỗi giao dịch gồm ít nhất hai dòng cùng transaction id và tổng luôn bằng 0. Số âm là tiền rời khỏi tài khoản, số dương là tiền vào.</p>
    <div class="row" style="margin:.75rem 0">
      <label v-if="isAdmin">Tài khoản
        <select v-model="code" @change="load">
          <option value="">Tất cả</option>
          <option v-for="a in accounts" :key="a.id" :value="a.code">{{ a.ownerName || a.code }} ({{ a.type }})</option>
        </select>
      </label>
      <button class="btn ghost" @click="load">Tải lại</button>
    </div>
    <p v-if="!entries.length" class="empty">Chưa có bút toán nào. Thử nạp tiền ở tab Ví.</p>
    <table v-else>
      <thead><tr><th>Giao dịch</th><th>Loại</th><th>Tài khoản</th><th class="num">Số tiền</th><th class="num">Số dư sau</th><th>Lúc</th></tr></thead>
      <tbody>
        <tr v-for="e in entries" :key="e.id">
          <td :title="e.transactionId">{{ e.transactionId.slice(0, 8) }}</td>
          <td>{{ e.type }}</td>
          <td>{{ e.accountCode.startsWith('WALLET:') ? 'Ví ' + e.accountCode.slice(7, 15) : e.accountCode }}</td>
          <td class="num" :class="e.amount > 0 ? 'in' : 'out'">{{ e.amount > 0 ? '+' : '' }}{{ vnd(e.amount) }}</td>
          <td class="num">{{ vnd(e.balanceAfter) }}</td>
          <td>{{ when(e.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
