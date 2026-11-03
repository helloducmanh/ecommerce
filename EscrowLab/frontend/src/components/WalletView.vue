<script setup>
import { ref, onMounted } from 'vue'
import { notify, vnd, when, statusLabel } from '../store'
import { api } from '../api'

const emit = defineEmits(['changed'])
const deposits = ref([])
const withdrawals = ref([])
const depositAmount = ref(500000)
const wd = ref({ amount: 100000, bankAccount: '' })
const transfer = ref(null)   // thông tin chuyển khoản đang hiển thị (kèm mã QR)

async function load() {
  try {
    deposits.value = await api.get('/api/deposits')
    withdrawals.value = await api.get('/api/withdrawals')
  } catch (e) { notify(e.message, 'err') }
}

async function deposit() {
  try {
    const d = await api.post('/api/deposits', { amount: Number(depositAmount.value) })
    transfer.value = d.transfer
    notify(`Đã tạo yêu cầu nạp ${d.providerRef}. Hãy chuyển khoản đúng số tiền và nội dung.`)
    await load()
  } catch (e) { notify(e.message, 'err') }
}

async function withdraw() {
  try {
    await api.post('/api/withdrawals', { amount: Number(wd.value.amount), bankAccount: wd.value.bankAccount })
    notify('Đã gửi yêu cầu rút tiền. Tiền được giữ lại chờ admin xử lý.')
    await load()
    emit('changed')
  } catch (e) { notify(e.message, 'err') }
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(String(text))
    notify('Đã sao chép')
  } catch { notify('Không sao chép được, hãy chép tay', 'err') }
}

onMounted(load)
</script>

<template>
  <div class="grid2">
    <section class="card">
      <h2>Nạp tiền vào ví</h2>
      <div class="row">
        <label>Số tiền (₫)<input v-model="depositAmount" type="number" min="10000" step="10000" /></label>
        <button class="btn" @click="deposit">Tạo yêu cầu nạp</button>
      </div>
      <p class="muted">Ví chỉ được cộng tiền khi gateway gửi webhook hợp lệ, không phải khi bạn bấm nút này.</p>
    </section>
    <section class="card">
      <h2>Rút tiền về ngân hàng</h2>
      <div class="row">
        <label>Số tiền (₫)<input v-model="wd.amount" type="number" min="10000" step="10000" /></label>
        <label>Số tài khoản<input v-model="wd.bankAccount" placeholder="0123456789" /></label>
        <button class="btn" :disabled="!wd.bankAccount" @click="withdraw">Gửi yêu cầu</button>
      </div>
    </section>
  </div>

  <section v-if="transfer" class="card">
    <h2>Chuyển khoản để nạp tiền</h2>
    <div class="grid2">
      <div class="qr-box"><img :src="transfer.qrUrl" alt="Mã QR chuyển khoản" width="240" height="240" /></div>
      <dl class="kv">
        <dt>Ngân hàng</dt><dd>{{ transfer.bankName }}</dd>
        <dt>Chủ tài khoản</dt><dd>{{ transfer.accountHolder }}</dd>
        <dt>Số tài khoản</dt><dd>{{ transfer.accountNumber }} <button class="btn small ghost" @click="copy(transfer.accountNumber)">Chép</button></dd>
        <dt>Số tiền</dt><dd>{{ vnd(transfer.amount) }} <button class="btn small ghost" @click="copy(transfer.amount)">Chép</button></dd>
        <dt>Nội dung</dt><dd><strong>{{ transfer.content }}</strong> <button class="btn small ghost" @click="copy(transfer.content)">Chép</button></dd>
      </dl>
    </div>
    <p class="muted">Phải ghi đúng nội dung để hệ thống nhận ra khoản tiền. Ở SePay Test mode, mã QR chỉ để xem hình; hãy dùng chức năng mô phỏng chuyển khoản trên my.dev.sepay.vn, hoặc nút "Webhook kiểu SePay" ở tab Giả lập gateway.</p>
    <div class="row"><button class="btn ghost small" @click="transfer = null">Đóng</button></div>
  </section>

  <section class="card">
    <h2>Lịch sử nạp tiền</h2>
    <p v-if="!deposits.length" class="empty">Bạn chưa nạp tiền lần nào.</p>
    <table v-else>
      <thead><tr><th>Mã nạp tiền</th><th class="num">Số tiền</th><th>Trạng thái</th><th>Tạo lúc</th><th></th></tr></thead>
      <tbody>
        <tr v-for="d in deposits" :key="d.id">
          <td>{{ d.providerRef }}</td><td class="num">{{ vnd(d.amount) }}</td>
          <td><span class="badge" :class="d.status">{{ statusLabel(d.status) }}</span></td><td>{{ when(d.createdAt) }}</td>
          <td><button v-if="d.transfer" class="btn small ghost" @click="transfer = d.transfer">Xem QR</button></td>
        </tr>
      </tbody>
    </table>
  </section>

  <section class="card">
    <h2>Lịch sử rút tiền</h2>
    <p v-if="!withdrawals.length" class="empty">Bạn chưa rút tiền lần nào.</p>
    <table v-else>
      <thead><tr><th>Tài khoản nhận</th><th class="num">Số tiền</th><th>Trạng thái</th><th>Tạo lúc</th></tr></thead>
      <tbody>
        <tr v-for="w in withdrawals" :key="w.id">
          <td>{{ w.bankAccount }}</td><td class="num">{{ vnd(w.amount) }}</td>
          <td><span class="badge" :class="w.status">{{ statusLabel(w.status) }}</span></td><td>{{ when(w.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
