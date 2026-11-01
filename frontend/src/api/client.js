// Small fetch wrapper: adds the JWT, parses JSON, and throws an Error carrying the API's message.
const TOKEN_KEY = 'campus.token'

export const tokenStore = {
  get: () => { try { return sessionStorage.getItem(TOKEN_KEY) } catch { return null } },
  set: (t) => { forgetAll(); try { sessionStorage.setItem(TOKEN_KEY, t) } catch { /* private mode */ } },
  clear: () => { forgetAll(); try { sessionStorage.removeItem(TOKEN_KEY) } catch { /* private mode */ } },
}

// Last-seen answers, kept for this browser tab. Pages draw these at once and refresh in the background,
// so moving between pages never waits on the server. Cleared on sign-in and sign-out.
const CACHE = 'campus.cache:'
const seen = new Map()

/** The last answer for a GET path, if there is one (may be a little old). */
export function peek(path) {
  if (seen.has(path)) return seen.get(path)
  try {
    const raw = sessionStorage.getItem(CACHE + path)
    if (raw) { const v = JSON.parse(raw); seen.set(path, v); return v }
  } catch { /* storage unavailable */ }
  return undefined
}

/** Store an answer we already have (for example the user from a sign-in response). */
export function seed(path, data) { remember(path, data) }

function remember(path, data) {
  seen.set(path, data)
  try { sessionStorage.setItem(CACHE + path, JSON.stringify(data)) } catch { /* full or unavailable */ }
}

function forgetAll() {
  seen.clear()
  recent.clear()
  try { Object.keys(sessionStorage).filter((k) => k.startsWith(CACHE)).forEach((k) => sessionStorage.removeItem(k)) } catch { /* unavailable */ }
}

/** Fetch GETs ahead of time so the next page opens instantly. */
export function prefetch(paths) {
  paths.forEach((p) => { api(p).catch(() => {}) })
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
  if (method !== 'GET') {
    recent.clear()
    return request(path, options)
  }
  if (options.fresh) return request(path, options).then((d) => { remember(path, d); return d })
  const key = `${tokenStore.get() || ''} ${path}`
  const hit = recent.get(key)
  if (hit && Date.now() - hit.at < FRESH_MS) return hit.promise
  const promise = request(path, options).then((d) => { remember(path, d); return d })
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
