<script setup>
import { ref, onMounted } from 'vue'
import { api, money, optText } from '../api'

const orders = ref([])
const error = ref('')
onMounted(load)

async function load() {
  try { orders.value = await api.orders() } catch (e) { error.value = e.message }
}
async function act(fn, id) {
  error.value = ''
  try { await fn(id); await load() } catch (e) { error.value = e.message }
}
</script>

<template>
  <div class="row"><h2>Đơn hàng</h2><button class="btn ghost fit" @click="load">Tải lại</button></div>
  <div v-if="error" class="msg err">{{ error }}</div>
  <div v-if="!orders.length" class="card muted">Chưa có đơn hàng.</div>
  <div class="card" v-for="o in orders" :key="o.id" style="margin-bottom: 12px">
    <div class="row">
      <div><b>{{ o.orderNumber }}</b> <span class="badge" :class="o.status">{{ o.status }}</span>
        <div class="muted">{{ o.customerName }} · {{ o.customerEmail }} · {{ new Date(o.createdAt + 'Z').toLocaleString('vi-VN') }}</div></div>
      <div class="fit price">{{ money(o.total) }}</div>
    </div>
    <ul>
      <li v-for="i in o.items" :key="i.sku">{{ i.quantity }} × {{ i.productName }}
        <span class="muted" v-if="Object.keys(i.optionValues).length">({{ optText(i.optionValues) }})</span> — {{ money(i.unitPrice) }}</li>
    </ul>
    <div class="row" style="justify-content: flex-start">
      <button class="btn fit" v-if="o.status === 'Pending'" @click="act(api.confirmOrder, o.id)">Xác nhận</button>
      <button class="btn danger fit" v-if="o.status !== 'Cancelled'" @click="act(api.cancelOrder, o.id)">Huỷ &amp; hoàn kho</button>
    </div>
  </div>
</template>
