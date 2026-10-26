import { reactive, computed, watch } from 'vue'

const KEY = 'ro_cart_v1'
let saved = { items: [], checkoutKey: null }
try { saved = JSON.parse(localStorage.getItem(KEY)) || saved } catch {}

export const cart = reactive({ items: saved.items, checkoutKey: saved.checkoutKey })
watch(cart, v => localStorage.setItem(KEY, JSON.stringify(v)), { deep: true })

export const cartTotal = computed(() => cart.items.reduce((s, i) => s + i.price * i.quantity, 0))
export const cartCount = computed(() => cart.items.reduce((s, i) => s + i.quantity, 0))

// Mọi thay đổi giỏ hàng đều đổi Idempotency-Key (vì nội dung request đã khác)
export function addToCart(product, variant, quantity = 1) {
  const line = cart.items.find(i => i.variantId === variant.id)
  if (line) line.quantity += quantity
  else cart.items.push({
    variantId: variant.id, name: product.name, sku: variant.sku,
    options: variant.optionValues, price: variant.price, quantity
  })
  cart.checkoutKey = null
}
export function setQuantity(variantId, q) {
  const line = cart.items.find(i => i.variantId === variantId)
  if (!line) return
  if (q <= 0) cart.items.splice(cart.items.indexOf(line), 1)
  else line.quantity = q
  cart.checkoutKey = null
}
export function clearCart() { cart.items.splice(0); cart.checkoutKey = null }
