<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import ProductCard from './ProductCard.vue'

defineEmits(['go-cart'])
const products = ref([])
const error = ref('')
const toast = ref('')

onMounted(load)
async function load() {
  try { products.value = await api.products() } catch (e) { error.value = e.message }
}
function added() {
  toast.value = 'Đã thêm vào giỏ hàng'
  setTimeout(() => (toast.value = ''), 1800)
}
defineExpose({ load })
</script>

<template>
  <div class="row" style="margin-bottom: 8px">
    <h2>Sản phẩm</h2>
    <button class="btn ghost fit" @click="load">Tải lại</button>
    <button class="btn fit" @click="$emit('go-cart')">Xem giỏ hàng</button>
  </div>
  <div v-if="error" class="msg err">{{ error }} — hãy chắc chắn backend đang chạy ở cổng 5080.</div>
  <div v-if="toast" class="msg ok">{{ toast }}</div>
  <div class="grid">
    <ProductCard v-for="p in products" :key="p.id" :product="p" @added="added" />
  </div>
</template>
