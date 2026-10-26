async function request(path, options = {}) {
  const res = await fetch('/api' + path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    const err = new Error(data?.error || `Lỗi ${res.status}`)
    err.status = res.status
    throw err
  }
  return { data, replayed: res.headers.get('Idempotent-Replayed') === 'true' }
}

export const api = {
  products: () => request('/products').then(r => r.data),
  createProduct: body => request('/products', { method: 'POST', body: JSON.stringify(body) }).then(r => r.data),
  updateVariant: (id, body) => request(`/variants/${id}`, { method: 'PUT', body: JSON.stringify(body) }).then(r => r.data),
  deleteProduct: id => request(`/products/${id}`, { method: 'DELETE' }),
  orders: () => request('/orders').then(r => r.data),
  // Idempotency-Key giữ nguyên khi retry => không bao giờ tạo đơn trùng
  createOrder: (body, key) => request('/orders', { method: 'POST', body: JSON.stringify(body), headers: { 'Idempotency-Key': key } }),
  confirmOrder: id => request(`/orders/${id}/confirm`, { method: 'POST' }).then(r => r.data),
  cancelOrder: id => request(`/orders/${id}/cancel`, { method: 'POST' }).then(r => r.data)
}

export const money = n => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(n)
export const optText = o => Object.entries(o || {}).map(([k, v]) => `${k}: ${v}`).join(' · ')
