<script setup>
import { ref } from 'vue'
import Storefront from './components/Storefront.vue'
import CartPanel from './components/CartPanel.vue'
import OrdersView from './components/OrdersView.vue'
import AdminProducts from './components/AdminProducts.vue'
import { cartCount } from './store'

const tab = ref('shop')
const tabs = [
  { id: 'shop', label: 'Cửa hàng' },
  { id: 'cart', label: 'Giỏ hàng' },
  { id: 'orders', label: 'Đơn hàng' },
  { id: 'admin', label: 'Quản trị sản phẩm' }
]
</script>

<template>
  <header class="top">
    <div class="container">
      <span class="brand">Reliable Order Platform</span>
      <nav>
        <button v-for="t in tabs" :key="t.id" :class="{ active: tab === t.id }" @click="tab = t.id">
          {{ t.label }}<template v-if="t.id === 'cart' && cartCount"> ({{ cartCount }})</template>
        </button>
      </nav>
    </div>
  </header>
  <main class="container">
    <Storefront v-if="tab === 'shop'" @go-cart="tab = 'cart'" />
    <CartPanel v-else-if="tab === 'cart'" @ordered="tab = 'orders'" />
    <OrdersView v-else-if="tab === 'orders'" />
    <AdminProducts v-else />
  </main>
</template>
