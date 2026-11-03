import { reactive } from 'vue'

export const state = reactive({
  users: [],
  userId: '',   // = claim sub của người đang đăng nhập (được gán trong App.vue)
  wallet: null,
  toast: null,
})

let toastTimer
export function notify(msg, type = 'ok') {
  state.toast = { msg, type }
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (state.toast = null), 4000)
}

export const vnd = (n) => new Intl.NumberFormat('vi-VN').format(n ?? 0) + ' ₫'
export const when = (iso) => (iso ? new Date(iso).toLocaleString('vi-VN') : '')

const STATUS = {
  CREATED: 'Chờ thanh toán',
  HELD: 'Tiền đang được giữ',
  SHIPPED: 'Đã giao hàng',
  DISPUTED: 'Đang tranh chấp',
  RELEASED: 'Đã giải ngân',
  REFUNDED: 'Đã hoàn tiền',
  CANCELLED: 'Đã huỷ',
  EXPIRED: 'Hết hạn',
  PENDING: 'Đang chờ',
  SUCCEEDED: 'Thành công',
  FAILED: 'Thất bại',
  REVIEW: 'Cần xem xét',
  COMPLETED: 'Đã hoàn tất',
  REJECTED: 'Bị từ chối',
}
export const statusLabel = (s) => STATUS[s] || s
