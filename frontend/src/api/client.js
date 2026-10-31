// Small fetch wrapper: adds the JWT, parses JSON, and throws an Error carrying the API's message.
const TOKEN_KEY = 'campus.token'

export const tokenStore = {
  get: () => sessionStorage.getItem(TOKEN_KEY),
  set: (t) => sessionStorage.setItem(TOKEN_KEY, t),
  clear: () => sessionStorage.removeItem(TOKEN_KEY),
}

export class ApiError extends Error {
  constructor(status, message, fields = {}) {
    super(message)
    this.status = status
    this.fields = fields
  }
}

// Recent GET results, shared between components that ask for the same thing on one page (the home page asks
// for rooms and events from several places). Identical requests in flight are merged, results are reused for a
// few seconds, and any change (POST, PUT, PATCH, DELETE) clears everything so nothing stale is shown.
const FRESH_MS = 10000
const recent = new Map()

export function api(path, options = {}) {
  const method = options.method || 'GET'
  if (method !== 'GET' || options.fresh) {
    if (method !== 'GET') recent.clear()
    return request(path, options)
  }
  const key = `${tokenStore.get() || ''} ${path}`
  const hit = recent.get(key)
  if (hit && Date.now() - hit.at < FRESH_MS) return hit.promise
  const promise = request(path, options)
  recent.set(key, { at: Date.now(), promise })
  promise.catch(() => recent.delete(key))
  return promise
}

const OFFLINE = "Couldn't reach the server. If the site was idle it can take up to a minute to wake up, so please try again."

async function request(path, { method = 'GET', body, headers: extra = {} } = {}, attempt = 1) {
  const headers = { 'Content-Type': 'application/json', ...extra }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`

  let res
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    // The browser got no answer at all (Safari says "Load failed", Chrome "Failed to fetch").
    // Reads are safe to repeat once; anything that changes data is left for the person to retry.
    if (method === 'GET' && attempt === 1) {
      await new Promise((r) => setTimeout(r, 2500))
      return request(path, { method, body, headers: extra }, 2)
    }
    throw new ApiError(0, OFFLINE)
  }

  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    // A gateway error page (HTML) instead of our JSON: the server is restarting or overloaded.
    data = null
  }
  if (!res.ok) {
    const fallback = res.status >= 502 && res.status <= 504 ? OFFLINE : res.statusText || 'Something went wrong'
    throw new ApiError(res.status, data?.message || fallback, data?.fields || {})
  }
  return data
}
