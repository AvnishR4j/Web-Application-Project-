import { useSyncExternalStore } from 'react'

// Small fetch wrapper: adds the JWT, parses JSON, and throws an Error carrying the API's message.
const TOKEN_KEY = 'campus.token'

// The sign-in lives in localStorage, so it survives closing the tab or the browser and opening a link later.
// (An older sign-in kept in sessionStorage is still honoured until it ends.)
export const tokenStore = {
  get: () => {
    try { return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) } catch { return null }
  },
  set: (t) => {
    forgetAll()
    try { localStorage.setItem(TOKEN_KEY, t); sessionStorage.removeItem(TOKEN_KEY) } catch {
      try { sessionStorage.setItem(TOKEN_KEY, t) } catch { /* private mode */ }
    }
  },
  clear: () => {
    forgetAll()
    try { localStorage.removeItem(TOKEN_KEY) } catch { /* unavailable */ }
    try { sessionStorage.removeItem(TOKEN_KEY) } catch { /* unavailable */ }
  },
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

// While the free backend wakes from sleep, requests are retried quietly and the page can say so.
const RETRY_AFTER_MS = [2500, 4000, 6000, 8000, 10000, 10000]
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
let waking = false
const wakingListeners = new Set()
function setWaking(v) {
  if (waking === v) return
  waking = v
  wakingListeners.forEach((f) => f())
}
/** True while a request is being retried because the server has not answered yet. */
export function useWaking() {
  return useSyncExternalStore((f) => { wakingListeners.add(f); return () => wakingListeners.delete(f) }, () => waking)
}
/** Poke the backend as soon as the site opens, so it is awake by the time someone signs in. */
export function warmUp() {
  fetch('/api/health', { cache: 'no-store' }).catch(() => {})
}

async function request(path, { method = 'GET', body, headers: extra = {} } = {}) {
  const headers = { 'Content-Type': 'application/json', ...extra }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`
  // Reads and sign-in calls are safe to repeat when the server never answered; other writes are left to the person.
  const retriable = method === 'GET' || path.startsWith('/api/auth/')

  let res
  for (let attempt = 0; ; attempt++) {
    const more = retriable && attempt < RETRY_AFTER_MS.length
    try {
      res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
    } catch {
      // The browser got no answer at all (Safari says "Load failed", Chrome "Failed to fetch").
      if (more) { setWaking(true); await sleep(RETRY_AFTER_MS[attempt]); continue }
      setWaking(false)
      throw new ApiError(0, OFFLINE)
    }
    // A gateway error means the server is starting up or restarting; 504 may mean the work ran, so only reads retry it.
    if (more && (res.status === 502 || res.status === 503 || (res.status === 504 && method === 'GET'))) {
      setWaking(true)
      await sleep(RETRY_AFTER_MS[attempt])
      continue
    }
    break
  }
  setWaking(false)

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
