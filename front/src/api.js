// 统一封装的 fetch：自动携带令牌、处理 401/错误结构
const TOKEN_KEY = 'token'
const USER_KEY = 'user'

export function saveAuth(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY))
  } catch {
    return null
  }
}

/**
 * 请求后端接口（相对路径 /api/...，由 vite 代理到后端）
 * - 自动带 Authorization: Bearer <token>
 * - 401：清除本地登录态并跳转登录页
 * - 非 2xx：抛出带 status 属性的 Error，message 取后端 ApiError.message
 */
export async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...options.headers }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(path, { ...options, headers })

  if (response.status === 401) {
    clearAuth()
    // 已在登录页则不跳转，避免循环
    if (window.location.pathname !== '/login') window.location.href = '/login'
    throw Object.assign(new Error('登录已失效，请重新登录'), { status: 401 })
  }

  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw Object.assign(new Error(error.message || `请求失败 (${response.status})`), { status: response.status })
  }

  return response.status === 204 ? null : response.json()
}
