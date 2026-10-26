<script setup>
import { computed, reactive } from 'vue'
import { money, optText } from '../api'
import { addToCart } from '../store'

const props = defineProps({ product: { type: Object, required: true } })
const emit = defineEmits(['added'])

// selected[optionName] = value đang chọn
const selected = reactive({})
const options = computed(() => props.product.options)
const activeVariants = computed(() => props.product.variants.filter(v => v.isActive))

// Variant khớp đủ các option đã chọn (sản phẩm không có option => variant mặc định)
const variant = computed(() => {
  if (!options.value.length) return activeVariants.value[0]
  if (options.value.some(o => !selected[o.name])) return null
  return activeVariants.value.find(v => options.value.every(o => v.optionValues[o.name] === selected[o.name]))
})

// Giá trị bị vô hiệu nếu không có variant còn hàng khớp với các lựa chọn còn lại
function available(optName, value) {
  return activeVariants.value.some(v =>
    v.stock > 0 && v.optionValues[optName] === value &&
    Object.entries(selected).every(([k, val]) => k === optName || !val || v.optionValues[k] === val))
}
function pick(optName, value) { selected[optName] = selected[optName] === value ? null : value }

const priceText = computed(() => {
  if (variant.value) return money(variant.value.price)
  const prices = activeVariants.value.map(v => v.price)
  if (!prices.length) return '—'
  const [min, max] = [Math.min(...prices), Math.max(...prices)]
  return min === max ? money(min) : `${money(min)} – ${money(max)}`
})

function add() {
  addToCart(props.product, variant.value)
  emit('added')
}
</script>

<template>
  <div class="card">
    <h3>{{ product.name }}</h3>
    <div class="muted">{{ product.description }}</div>
    <div class="muted" v-if="Object.keys(product.attributes).length">{{ optText(product.attributes) }}</div>

    <div class="opt" v-for="o in options" :key="o.name">
      <div class="label">{{ o.name }}</div>
      <div class="chips">
        <button v-for="val in o.values" :key="val" class="chip" :class="{ on: selected[o.name] === val }"
                :disabled="!available(o.name, val)" @click="pick(o.name, val)">{{ val }}</button>
      </div>
    </div>

    <div class="price">{{ priceText }}</div>
    <div class="muted" v-if="variant">SKU: {{ variant.sku }} · Còn {{ variant.stock }}</div>
    <div class="muted" v-else>Chọn đủ tuỳ chọn để thêm vào giỏ</div>
    <button class="btn" style="margin-top: 10px" :disabled="!variant || variant.stock <= 0" @click="add">
      {{ variant && variant.stock <= 0 ? 'Hết hàng' : 'Thêm vào giỏ' }}
    </button>
  </div>
</template>
