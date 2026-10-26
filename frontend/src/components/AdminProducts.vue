<script setup>
import { ref, computed, onMounted } from 'vue'
import { api, money } from '../api'

const products = ref([])
const error = ref('')
const ok = ref('')

const form = ref(blank())
function blank() {
  return { name: '', description: '', basePrice: 0, initialStock: 10, attributes: [{ k: '', v: '' }], options: [{ name: '', values: '' }] }
}

const parsedOptions = computed(() => form.value.options
  .map(o => ({ name: o.name.trim(), values: o.values.split(',').map(s => s.trim()).filter(Boolean) }))
  .filter(o => o.name && o.values.length))
const variantCount = computed(() => parsedOptions.value.reduce((n, o) => n * o.values.length, 1))

onMounted(load)
async function load() {
  try { products.value = await api.products() } catch (e) { error.value = e.message }
}

async function create() {
  error.value = ok.value = ''
  try {
    const attributes = Object.fromEntries(form.value.attributes.filter(a => a.k.trim()).map(a => [a.k.trim(), a.v]))
    const p = await api.createProduct({
      name: form.value.name, description: form.value.description, basePrice: +form.value.basePrice,
      initialStock: +form.value.initialStock, attributes, options: parsedOptions.value
    })
    ok.value = `Đã tạo "${p.name}" với ${p.variants.length} biến thể.`
    form.value = blank()
    await load()
  } catch (e) { error.value = e.message }
}

async function saveVariant(v) {
  error.value = ok.value = ''
  try { await api.updateVariant(v.id, { price: +v.price, stock: +v.stock, isActive: v.isActive }); ok.value = `Đã lưu ${v.sku}` }
  catch (e) { error.value = e.message }
}
async function remove(p) {
  if (!confirm(`Ngừng bán "${p.name}"?`)) return
  await api.deleteProduct(p.id); await load()
}
</script>

<template>
  <h2>Quản trị sản phẩm</h2>
  <div v-if="error" class="msg err">{{ error }}</div>
  <div v-if="ok" class="msg ok">{{ ok }}</div>

  <div class="card" style="margin-bottom: 20px">
    <h3>Tạo sản phẩm động</h3>
    <div class="two">
      <div>
        <label class="f">Tên sản phẩm</label><input v-model="form.name" />
        <label class="f">Mô tả</label><input v-model="form.description" />
        <div class="row">
          <div><label class="f">Giá gốc (VND)</label><input type="number" min="0" v-model="form.basePrice" /></div>
          <div><label class="f">Tồn kho mỗi biến thể</label><input type="number" min="0" v-model="form.initialStock" /></div>
        </div>
        <label class="f">Thuộc tính tuỳ ý (ví dụ Chất liệu = Cotton)</label>
        <div class="row" v-for="(a, i) in form.attributes" :key="'a' + i" style="margin-bottom: 6px">
          <input placeholder="Tên" v-model="a.k" /><input placeholder="Giá trị" v-model="a.v" />
        </div>
        <button class="btn ghost" @click="form.attributes.push({ k: '', v: '' })">+ Thêm thuộc tính</button>
      </div>
      <div>
        <label class="f">Tuỳ chọn biến thể (các giá trị cách nhau bằng dấu phẩy)</label>
        <div class="row" v-for="(o, i) in form.options" :key="'o' + i" style="margin-bottom: 6px">
          <input placeholder="Tên, vd: Màu sắc" v-model="o.name" /><input placeholder="Đỏ, Xanh, Vàng" v-model="o.values" />
        </div>
        <button class="btn ghost" @click="form.options.push({ name: '', values: '' })">+ Thêm tuỳ chọn</button>
        <p class="muted">Sẽ sinh <b>{{ variantCount }}</b> biến thể (tích các tuỳ chọn).</p>
        <button class="btn" :disabled="!form.name || variantCount > 200" @click="create">Tạo sản phẩm</button>
      </div>
    </div>
  </div>

  <div class="card" v-for="p in products" :key="p.id" style="margin-bottom: 14px">
    <div class="row"><h3>{{ p.name }} <span class="muted">· giá gốc {{ money(p.basePrice) }}</span></h3>
      <button class="btn danger fit" @click="remove(p)">Ngừng bán</button></div>
    <div class="scroll-x">
      <table>
        <thead><tr><th>SKU</th><th v-for="o in p.options" :key="o.name">{{ o.name }}</th><th>Giá</th><th>Tồn kho</th><th>Bán</th><th></th></tr></thead>
        <tbody>
          <tr v-for="v in p.variants" :key="v.id">
            <td class="muted">{{ v.sku }}</td>
            <td v-for="o in p.options" :key="o.name">{{ v.optionValues[o.name] }}</td>
            <td><input type="number" min="0" v-model="v.price" /></td>
            <td><input type="number" min="0" v-model="v.stock" /></td>
            <td><input type="checkbox" v-model="v.isActive" style="width: auto" /></td>
            <td><button class="btn ghost" @click="saveVariant(v)">Lưu</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
