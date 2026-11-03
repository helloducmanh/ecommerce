<script setup>
import { ref, onMounted } from 'vue'
import { notify, vnd, statusLabel } from '../store'
import { api } from '../api'

const emit = defineEmits(['changed'])
const deposits = ref([])
const orders = ref([])
const sendTwice = ref(false)
const badSignature = ref(false)
const amountOverride = ref('')
const log = ref('')
const hours = ref(2)

async function load() {
  try {
    deposits.value = await api.get('/api/deposits')
    orders.value = await api.get('/api/orders')
  } catch (e) { notify(e.message, 'err') }
}

async function simulate(d, outcome) {
  try {
    const body = {
      providerRef: d.providerRef, outcome, sendTwice: sendTwice.value, badSignature: badSignature.value,
      amountOverride: amountOverride.value ? Number(amountOverride.value) : null,
    }
    const res = await api.post('/api/dev/gateway/simulate', body)
    log.value = `POST /api/webhooks/gateway  (${d.providerRef}, ${outcome})\n` + JSON.stringify(res, null, 2)
    await load()
    emit('changed')
  } catch (e) { notify(e.message, 'err') }
}

async function simulateSePay(d) {
  try {
    const body = { providerRef: d.providerRef, sendTwice: sendTwice.value,
                   amountOverride: amountOverride.value ? Number(amountOverride.value) : null }
    const res = await api.post('/api/dev/sepay/simulate', body)
    log.value = `POST /api/webhooks/sepay  (${d.providerRef}, payload kiểu SePay)\n` + JSON.stringify(res, null, 2)
    await load()
    emit('changed')
  } catch (e) { notify(e.message, 'err') }
}

async function timeTravel(o) {
  try {
    await api.post(`/api/dev/orders/${o.id}/time-travel`, { hours: Number(hours.value) })
    notify(`Đã tua đơn "${o.title}" lùi ${hours.value} giờ`)
  } catch (e) { notify(e.message, 'err') }
}

async function runMaintenance() {
  try {
    const r = await api.post('/api/dev/maintenance/run')
    notify(`Hết hạn: ${r.expired} đơn, tự giải ngân: ${r.autoReleased} đơn`)
    await load()
    emit('changed')
  } catch (e) { notify(e.message, 'err') }
}

onMounted(load)
</script>

<template>
  <section class="card">
    <h2>Giả lập gateway gửi webhook</h2>
    <p class="muted">Thay cho VNPay/MoMo thật: backend tự ký payload bằng HMAC rồi gọi bộ xử lý webhook như một gateway thật. Thử các tuỳ chọn dưới đây để thấy cách hệ thống tự bảo vệ.</p>
    <div class="row" style="margin:.75rem 0">
      <label class="check"><input type="checkbox" v-model="sendTwice" /> Gửi 2 lần (kiểm tra idempotency)</label>
      <label class="check"><input type="checkbox" v-model="badSignature" /> Ký sai (kiểm tra xác thực)</label>
      <label>Ghi đè số tiền<input v-model="amountOverride" type="number" placeholder="để trống = đúng số tiền" /></label>
    </div>
    <p v-if="!deposits.length" class="empty">Chưa có yêu cầu nạp tiền. Tạo ở tab Ví trước.</p>
    <table v-else>
      <thead><tr><th>Mã gateway</th><th class="num">Số tiền</th><th>Trạng thái</th><th></th></tr></thead>
      <tbody>
        <tr v-for="d in deposits" :key="d.id">
          <td>{{ d.providerRef }}</td><td class="num">{{ vnd(d.amount) }}</td>
          <td><span class="badge" :class="d.status">{{ statusLabel(d.status) }}</span></td>
          <td class="actions">
            <button class="btn small" @click="simulate(d, 'succeeded')">Thanh toán thành công</button>
            <button class="btn small ghost" @click="simulate(d, 'failed')">Thất bại</button>
            <button class="btn small ghost" @click="simulateSePay(d)">Webhook kiểu SePay</button>
          </td>
        </tr>
      </tbody>
    </table>
    <pre v-if="log" class="log">{{ log }}</pre>
  </section>

  <section class="card">
    <h2>Công cụ thời gian</h2>
    <p class="muted">Đơn chưa thanh toán sẽ hết hạn sau 60 phút, đơn đã giao sẽ tự giải ngân sau 7 ngày. Tua thời gian rồi chạy việc nền để thấy hiệu ứng ngay.</p>
    <div class="row" style="margin:.75rem 0">
      <label>Tua lùi (giờ)<input v-model="hours" type="number" min="1" /></label>
      <button class="btn ghost" @click="runMaintenance">Chạy việc nền ngay</button>
    </div>
    <p v-if="!orders.length" class="empty">Chưa có đơn hàng.</p>
    <table v-else>
      <thead><tr><th>Hàng hoá</th><th>Trạng thái</th><th></th></tr></thead>
      <tbody>
        <tr v-for="o in orders" :key="o.id">
          <td>{{ o.title }}</td>
          <td><span class="badge" :class="o.status">{{ statusLabel(o.status) }}</span></td>
          <td><button class="btn small ghost" @click="timeTravel(o)">Tua đơn này lùi</button></td>
        </tr>
      </tbody>
    </table>
    <p class="muted">Gợi ý: tua 24 giờ cho đơn "Chờ thanh toán" để nó hết hạn; tua 200 giờ (~8 ngày) cho đơn "Đã giao" để tự giải ngân.</p>
  </section>
</template>
