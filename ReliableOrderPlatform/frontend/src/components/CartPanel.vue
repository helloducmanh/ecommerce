<script setup>
import { ref } from 'vue'
import { api, money, optText } from '../api'
import { cart, cartTotal, setQuantity, clearCart } from '../store'
import { auth } from '../auth'

const emit = defineEmits(['ordered'])
// Thông tin khách lấy từ tài khoản SSO (backend cũng tự lấy từ token, không tin giá trị gửi lên)
const name = ref(auth.user?.profile.name || auth.user?.profile.preferred_username || '')
const email = ref(auth.user?.profile.email || '')
const busy = ref(false)
const error = ref('')
const info = ref('')

async function checkout() {
  error.value = info.value = ''
  // Key được tạo 1 lần cho mỗi "lần thanh toán" và giữ lại nếu lỗi mạng => bấm lại không bị đặt trùng
  if (!cart.checkoutKey) cart.checkoutKey = crypto.randomUUID()
  busy.value = true
  try {
    const { data, replayed } = await api.createOrder({
      customerName: name.value, customerEmail: email.value,
      items: cart.items.map(i => ({ variantId: i.variantId, quantity: i.quantity }))
    }, cart.checkoutKey)
    if (replayed) info.value = 'Đơn này đã được tạo trước đó (retry an toàn).'
    clearCart()
    emit('ordered', data)
  } catch (e) {
    error.value = e.status ? e.message : 'Lỗi mạng. Bạn có thể bấm đặt hàng lại, hệ thống sẽ không tạo đơn trùng.'
    // Lỗi nghiệp vụ (hết hàng, 4xx) => cho phép key mới ở lần sau
    if (e.status && e.status < 500) cart.checkoutKey = null
  } finally { busy.value = false }
}
</script>

<template>
  <h2>Giỏ hàng</h2>
  <div v-if="!cart.items.length" class="card muted">Giỏ hàng trống.</div>
  <div v-else class="two">
    <div class="card scroll-x">
      <table>
        <thead><tr><th>Sản phẩm</th><th>Đơn giá</th><th>SL</th><th></th></tr></thead>
        <tbody>
          <tr v-for="i in cart.items" :key="i.variantId">
            <td>{{ i.name }}<div class="muted">{{ optText(i.options) }}</div></td>
            <td>{{ money(i.price) }}</td>
            <td style="width: 80px"><input type="number" min="0" :value="i.quantity" @change="setQuantity(i.variantId, +$event.target.value)" /></td>
            <td><button class="btn ghost" @click="setQuantity(i.variantId, 0)">Xoá</button></td>
          </tr>
        </tbody>
      </table>
      <div class="price">Tổng: {{ money(cartTotal) }}</div>
    </div>
    <div class="card">
      <h3>Thông tin khách hàng</h3>
      <label class="f">Họ tên</label><input v-model="name" readonly />
      <label class="f">Email</label><input v-model="email" type="email" readonly />
      <div v-if="error" class="msg err">{{ error }}</div>
      <div v-if="info" class="msg ok">{{ info }}</div>
      <button class="btn" style="margin-top: 12px" :disabled="busy || !name || !email" @click="checkout">
        {{ busy ? 'Đang xử lý...' : 'Đặt hàng' }}
      </button>
      <div class="muted" style="margin-top: 8px">Idempotency-Key: {{ cart.checkoutKey || '(sẽ tạo khi đặt hàng)' }}</div>
    </div>
  </div>
</template>
