<script setup>
import { ref, onMounted } from 'vue'
import { notify, vnd, when, statusLabel } from '../store'
import { api } from '../api'

const accounts = ref([])
const withdrawals = ref([])
const statementText = ref('[]')
const report = ref(null)

async function load() {
  try {
    accounts.value = await api.get('/api/admin/accounts')
    withdrawals.value = await api.get('/api/withdrawals')
  } catch (e) { notify(e.message, 'err') }
}

async function finish(w, action) {
  try {
    await api.post(`/api/withdrawals/${w.id}/${action}`)
    notify(action === 'complete' ? 'Đã chuyển tiền ra ngân hàng' : 'Đã từ chối và hoàn tiền về ví')
    await load()
  } catch (e) { notify(e.message, 'err') }
}

async function loadStatement(tamper) {
  try {
    const lines = await api.get(`/api/dev/gateway/statement?tamper=${tamper}`)
    statementText.value = JSON.stringify(lines, null, 2)
    report.value = null
  } catch (e) { notify(e.message, 'err') }
}

async function reconcile() {
  try {
    const lines = JSON.parse(statementText.value)
    report.value = await api.post('/api/reconciliation/run', { lines })
  } catch (e) { notify(e instanceof SyntaxError ? 'JSON sao kê không hợp lệ' : e.message, 'err') }
}

onMounted(load)
</script>

<template>
  <section class="card">
    <h2>Tài khoản trong hệ thống</h2>
    <p class="muted">Cộng tất cả số dư lại luôn ra 0. EXTERNAL_BANK âm nghĩa là có bấy nhiêu tiền thật đã đi vào hệ thống.</p>
    <table>
      <thead><tr><th>Mã</th><th>Loại</th><th>Chủ sở hữu</th><th class="num">Số dư</th></tr></thead>
      <tbody>
        <tr v-for="a in accounts" :key="a.id">
          <td>{{ a.code.startsWith('WALLET:') ? 'WALLET:' + a.code.slice(7, 15) : a.code }}</td>
          <td>{{ a.type }}</td><td>{{ a.ownerName || '' }}</td><td class="num">{{ vnd(a.balance) }}</td>
        </tr>
      </tbody>
    </table>
  </section>

  <section class="card">
    <h2>Yêu cầu rút tiền</h2>
    <p v-if="!withdrawals.length" class="empty">Chưa có yêu cầu rút tiền.</p>
    <table v-else>
      <thead><tr><th>Người dùng</th><th>Tài khoản nhận</th><th class="num">Số tiền</th><th>Trạng thái</th><th>Lúc</th><th></th></tr></thead>
      <tbody>
        <tr v-for="w in withdrawals" :key="w.id">
          <td>{{ w.userName }}</td><td>{{ w.bankAccount }}</td><td class="num">{{ vnd(w.amount) }}</td>
          <td><span class="badge" :class="w.status">{{ statusLabel(w.status) }}</span></td><td>{{ when(w.createdAt) }}</td>
          <td class="actions">
            <template v-if="w.status === 'PENDING'">
              <button class="btn small" @click="finish(w, 'complete')">Đã chuyển tiền</button>
              <button class="btn small ghost" @click="finish(w, 'reject')">Từ chối</button>
            </template>
          </td>
        </tr>
      </tbody>
    </table>
  </section>

  <section class="card">
    <h2>Đối soát với sao kê gateway</h2>
    <p class="muted">Lấy sao kê mẫu rồi chạy đối soát. Thử cố ý làm sai sao kê để xem hệ thống phát hiện chênh lệch thế nào.</p>
    <div class="row" style="margin:.75rem 0">
      <button class="btn ghost small" @click="loadStatement('none')">Sao kê khớp</button>
      <button class="btn ghost small" @click="loadStatement('drop')">Thiếu một dòng</button>
      <button class="btn ghost small" @click="loadStatement('alter')">Sai số tiền</button>
      <button class="btn ghost small" @click="loadStatement('phantom')">Có dòng lạ</button>
    </div>
    <textarea v-model="statementText" spellcheck="false"></textarea>
    <div class="row" style="margin-top:.6rem"><button class="btn" @click="reconcile">Chạy đối soát</button></div>

    <div v-if="report" style="margin-top:1rem">
      <p><span class="badge" :class="report.allGood ? 'SUCCEEDED' : 'FAILED'">{{ report.allGood ? 'Mọi thứ khớp' : 'Có chênh lệch' }}</span>
        &nbsp;{{ report.matched }}/{{ report.ourSucceeded }} giao dịch khớp, sao kê có {{ report.statementLines }} dòng.</p>
      <p v-if="report.missingInLedger.length" class="out">Gateway có nhưng sổ ta không có: {{ report.missingInLedger.join(', ') }}</p>
      <p v-if="report.missingInStatement.length" class="out">Sổ ta có nhưng gateway không có: {{ report.missingInStatement.join(', ') }}</p>
      <p v-for="m in report.amountMismatches" :key="m.providerRef" class="out">{{ m.providerRef }}: ta ghi {{ vnd(m.ours) }}, gateway ghi {{ vnd(m.theirs) }}</p>
      <p>Tổng toàn bộ sổ cái: <strong>{{ vnd(report.ledgerTotal) }}</strong> (phải bằng 0). Tài khoản lệch số dư cache: {{ report.balanceDrift.length }}. Giao dịch mất cân bằng: {{ report.unbalancedTransactions.length }}.</p>
    </div>
  </section>
</template>
