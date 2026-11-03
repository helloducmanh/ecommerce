import { auth, userManager } from './auth'

async function call(method, url, body) {
  const token = auth.user?.access_token
  const res = await fetch(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (res.status === 401) {
    // Token bị từ chối: xoá phiên local để giao diện quay về màn hình đăng nhập.
    await userManager.removeUser()
    throw new Error('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.')
  }
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new Error(data?.message || `Lỗi HTTP ${res.status}`)
  return data
}

export const api = {
  get: (url) => call('GET', url),
  post: (url, body = {}) => call('POST', url, body),
}
