<script setup>
import { ref, computed, onMounted } from 'vue'
import { state, notify, vnd, when, statusLabel } from '../store'
import { api } from '../api'

const emit = defineEmits(['changed'])
const orders = ref([])
const detail = ref(null)
const form = ref({ sellerId: '', title: '', amount: 200000 })
const busy = ref(false)

const me = computed(() => state.users.find((u) => u.id === state.userId))
const sellers = computed(() => state.users.filter((u) => u.role === 'seller'))

async function load() {
  try {
    orders.value = await api.get('/api/orders')
    if (detail.value) await open(detail.value.order.id)
  } catch (e) { notify(e.message, 'err') }
}

async function open(id) {
  try { detail.value = await api.get(`/api/orders/${id}`) } catch (e) { notify(e.message, 'err') }
}

async function create() {
  busy.value = true
  try {
    await api.post('/api/orders', { ...form.value, amount: Number(form.value.amount) })
    notify('Đã tạo đơn. Bước tiếp theo: người mua thanh toán để tiền vào escrow.')
    form.value.title = ''
    await load()
  } catch (e) { notify(e.message, 'err') } finally { busy.value = false }
}

async function act(order, action, body) {
  try {
    await api.post(`/api/orders/${order.id}/${action}`, body)
    notify('Thành công')
    await load()
    emit('changed')
  } catch (e) { notify(e.message, 'err') }
}

// Nút nào hiện ra phụ thuộc vào vai trò và trạng thái: đây chính là state machine nhìn từ giao diện.
function actions(o) {
  const list = []
  const buyer = me.value?.id === o.buyerId
  const seller = me.value?.id === o.sellerId
  const admin = me.value?.role === 'admin'
  if (buyer && o.status === 'CREATED') list.push({ k: 'pay', label: 'Thanh toán' }, { k: 'cancel', label: 'Huỷ', ghost: true })
  if (seller && o.status === 'HELD') list.push({ k: 'ship', label: 'Đã giao hàng' }, { k: 'refund', label: 'Hoàn tiền', ghost: true })
  if (buyer && o.status === 'SHIPPED') list.push({ k: 'confirm', label: 'Đã nhận hàng' })
  if (buyer && (o.status === 'HELD' || o.status === 'SHIPPED')) list.push({ k: 'dispute', label: 'Khiếu nại', ghost: true })
  if (admin && o.status === 'DISPUTED') list.push({ k: 'resolve', label: 'Trả người bán', body: { decision: 'release' } }, { k: 'resolve', label: 'Hoàn người mua', body: { decision: 'refund' }, ghost: true })
  return list
}

onMounted(() => { load(); if (sellers.value[0]) form.value.sellerId = sellers.value[0].id })
</script>

<template>
  <section v-if="me?.role === 'buyer'" class="card">
    <h2>Tạo đơn mua hàng</h2>
    <div class="row">
      <label>Người bán
        <select v-model="form.sellerId"><option v-for="s in sellers" :key="s.id" :value="s.id">{{ s.name }}</option></select>
      </label>
      <label>Mô tả hàng hoá<input v-model="form.title" placeholder="Ví dụ: Bàn phím cơ" /></label>
      <label>Số tiền (₫)<input v-model="form.amount" type="number" min="10000" step="10000" /></label>
      <button class="btn" :disabled="busy || !form.title" @click="create">Tạo đơn</button>
    </div>
  </section>

  <section class="card">
    <h2>Đơn hàng</h2>
    <p v-if="!orders.length" class="empty">Chưa có đơn hàng nào.</p>
    <table v-else>
      <thead><tr><th>Hàng hoá</th><th>Người mua</th><th>Người bán</th><th class="num">Số tiền</th><th class="num">Phí</th><th>Trạng thái</th><th></th></tr></thead>
      <tbody>
        <tr v-for="o in orders" :key="o.id" class="clickable" @click="open(o.id)">
          <td>{{ o.title }}</td><td>{{ o.buyerName }}</td><td>{{ o.sellerName }}</td>
          <td class="num">{{ vnd(o.amount) }}</td><td class="num">{{ vnd(o.fee) }}</td>
          <td><span class="badge" :class="o.status">{{ statusLabel(o.status) }}</span></td>
          <td class="actions" @click.stop>
            <button v-for="a in actions(o)" :key="a.label" class="btn small" :class="{ ghost: a.ghost }" @click="act(o, a.k, a.body)">{{ a.label }}</button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>

  <section v-if="detail" class="card">
    <h2>Lịch sử đơn: {{ detail.order.title }}</h2>
    <p class="muted">Hết hạn thanh toán: {{ when(detail.order.expiresAt) }}. Phí {{ vnd(detail.order.fee) }} được trừ vào tiền người bán nhận.</p>
    <ul class="timeline">
      <li v-for="e in detail.events" :key="e.id">
        <span class="badge" :class="e.toStatus">{{ statusLabel(e.toStatus) }}</span>
        {{ e.note }} <span class="muted">· {{ e.actorName || 'Hệ thống' }} · {{ when(e.createdAt) }}</span>
      </li>
    </ul>
  </section>
</template>
