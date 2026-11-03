import { shallowReactive, computed } from 'vue'
import { UserManager, WebStorageStateStore } from 'oidc-client-ts'

// Cấu hình có thể ghi đè bằng frontend/.env.local (VITE_OIDC_AUTHORITY, VITE_OIDC_CLIENT_ID).
const authority = import.meta.env.VITE_OIDC_AUTHORITY || 'http://localhost:8180/realms/main'
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID || 'reliable-order-platform'

const CALLBACK_PATH = '/auth/callback'

export const userManager = new UserManager({
  authority,
  client_id: clientId,
  redirect_uri: `${window.location.origin}${CALLBACK_PATH}`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: 'code',            // Authorization Code + PKCE (oidc-client-ts tự sinh code_verifier / code_challenge)
  scope: 'openid profile email',
  automaticSilentRenew: true,       // tự đổi refresh_token lấy access token mới trước khi hết hạn
  monitorSession: true,             // phát hiện đã đăng xuất ở app khác (cùng phiên SSO)
  // Token chỉ nằm trong sessionStorage của tab này, đóng tab là mất.
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
})

// shallowReactive: không bọc proxy sâu vào object User của thư viện.
export const auth = shallowReactive({ ready: false, user: null, error: '' })

const setUser = (u) => { auth.user = u && !u.expired ? u : null }

userManager.events.addUserLoaded(setUser)
userManager.events.addUserUnloaded(() => setUser(null))
// Người dùng đăng xuất ở app kia: phiên SSO trên Keycloak đã mất, app này cũng phải thoát.
userManager.events.addUserSignedOut(() => userManager.removeUser())
// Access token hết hạn mà vẫn chưa gia hạn được (refresh thất bại): coi như hết phiên.
userManager.events.addAccessTokenExpired(() => userManager.removeUser())
userManager.events.addSilentRenewError((e) => console.warn('Gia hạn token thất bại:', e))

/** Gọi 1 lần trước khi mount app: xử lý redirect trả về từ Keycloak, hoặc khôi phục phiên đang có. */
export async function initAuth() {
  try {
    if (window.location.pathname === CALLBACK_PATH) {
      const u = await userManager.signinRedirectCallback()   // đổi ?code=... lấy token (có kiểm tra state, nonce, PKCE)
      setUser(u)
      const back = typeof u.state === 'string' ? u.state : '/'
      window.history.replaceState({}, '', back)              // bỏ ?code=&state= khỏi thanh địa chỉ
    } else {
      setUser(await userManager.getUser())
    }
  } catch (e) {
    auth.error = e?.message || String(e)
    window.history.replaceState({}, '', '/')
  } finally {
    auth.ready = true
  }
}

export function login() {
  const here = window.location.pathname + window.location.search
  // state tuỳ chọn: nhớ trang đang xem để quay lại sau khi đăng nhập
  return userManager.signinRedirect({ state: here.startsWith('/auth/') ? '/' : here })
}

export const logout = () => userManager.signoutRedirect()

// ---- Chỉ để ẩn/hiện giao diện. Quyền thật luôn do backend kiểm tra trên token. ----
function jwtPayload(token) {
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(b64).split('').map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0')).join(''),
    )
    return JSON.parse(json)
  } catch {
    return {}
  }
}

/** Role của client này trong access token (Keycloak: resource_access.<clientId>.roles). */
export const roles = computed(() => {
  const token = auth.user?.access_token
  return token ? jwtPayload(token).resource_access?.[clientId]?.roles ?? [] : []
})
export const isAdmin = computed(() => roles.value.includes('admin'))
