// The light on the page follows the real sun over campus (Patiala, Punjab).
// Shadows lengthen and swing from right to left through the day, window light slides across the desk,
// the paper warms towards sunset, and after dark a desk lamp takes over.
// Everything is CSS variables on <html>, so any element can use the shadow with var(--sun-shadow).

const LAT = 30.34
const LON = 76.39
const TZ_HOURS = 5.5 // India Standard Time

const rad = (d) => (d * Math.PI) / 180

/** Sunrise and sunset in IST hours (NOAA approximation, good to a minute or two). */
export function sunTimes(date = new Date()) {
  const start = Date.UTC(date.getUTCFullYear(), 0, 0)
  const day = Math.floor((date.getTime() - start) / 86400000)
  const g = ((2 * Math.PI) / 365) * (day - 1)
  const eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g))
  const decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g)
  const ha = Math.acos(Math.cos(rad(90.833)) / (Math.cos(rad(LAT)) * Math.cos(decl)) - Math.tan(rad(LAT)) * Math.tan(decl)) * (180 / Math.PI)
  const rise = (720 - 4 * (LON + ha) - eq) / 60 + TZ_HOURS
  const set = (720 - 4 * (LON - ha) - eq) / 60 + TZ_HOURS
  return { rise, set }
}

/** The current time on campus, as hours since midnight IST. */
export function campusHour(now = Date.now()) {
  return (((now / 3600000 + TZ_HOURS) % 24) + 24) % 24
}

export function hhmm(h) {
  const m = Math.floor((h % 1) * 60)
  return `${String(Math.floor(h)).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * Math.min(1, Math.max(0, t)))
const clamp01 = (x) => Math.min(1, Math.max(0, x))

// Desk colours through the day.
const DAWN = [228, 216, 205]
const DAY = [236, 230, 219]
const GOLD = [240, 226, 204]
const DUSK = [228, 211, 194]
const LAMP = [216, 204, 188]

function deskColour(h, rise, set) {
  if (h < rise - 0.6 || h > set + 0.8) return LAMP
  if (h < rise) return mix(LAMP, DAWN, (h - (rise - 0.6)) / 0.6)
  if (h < rise + 1.6) return mix(DAWN, DAY, (h - rise) / 1.6)
  if (h < set - 2) return DAY
  if (h < set - 0.4) return mix(DAY, GOLD, (h - (set - 2)) / 1.6)
  if (h < set + 0.3) return mix(GOLD, DUSK, (h - (set - 0.4)) / 0.7)
  return mix(DUSK, LAMP, (h - (set + 0.3)) / 0.5)
}

const listeners = new Set()
let shown = campusHour()
let playing = false
let timer = 0

/** Apply the light for a given campus hour. */
export function applyLight(h) {
  shown = h
  const { rise, set } = sunTimes()
  const root = document.documentElement
  const k = (h - rise) / (set - rise) // 0 at sunrise, 1 at sunset
  const day = k > -0.03 && k < 1.03
  const daylight = clamp01(Math.min((h - (rise - 0.4)) / 0.8, (set + 0.4 - h) / 0.8)) // 0 at night, 1 in the day

  // Sun shadow: long and soft when the sun is low, short and crisp at noon.
  const az = Math.PI * clamp01(k)
  const elev = Math.max(0.08, Math.sin(az))
  const len = 4 + (1 - elev) * 22
  const sunX = Math.cos(az) * len
  const sunY = 6 + (1 - elev) * 7
  const sunBlur = 10 + (1 - elev) * 20
  const warm = 1 - elev
  // Lamp shadow (night): straight down from a lamp above the page.
  const sx = sunX * daylight
  const sy = sunY * daylight + 13 * (1 - daylight)
  const sb = sunBlur * daylight + 26 * (1 - daylight)
  const alpha = (0.15 + warm * 0.12) * daylight + 0.26 * (1 - daylight)
  root.style.setProperty('--sx', `${sx.toFixed(1)}px`)
  root.style.setProperty('--sy', `${sy.toFixed(1)}px`)
  root.style.setProperty('--sb', `${sb.toFixed(1)}px`)
  root.style.setProperty('--sc', `rgba(${Math.round(72 + warm * 36 * daylight)}, ${Math.round(54 + warm * 8)}, 32, ${alpha.toFixed(3)})`)
  const [r, g, b] = deskColour(h, rise, set)
  root.style.setProperty('--desk', `rgb(${Math.round(r)}, ${Math.round(g)}, ${Math.round(b)})`)
  root.style.setProperty('--lamp', (1 - daylight).toFixed(3))

  // Window light: slides across the desk opposite the sun, stretched when the sun is low.
  const gobo = document.getElementById('gobo')
  if (gobo) {
    const W = window.innerWidth
    const x = W * (0.72 - clamp01(k) * 0.95)
    const y = 90 + elev * 70
    gobo.style.transform = `translate3d(${x.toFixed(0)}px, ${y.toFixed(0)}px, 0) skewX(${((0.5 - clamp01(k)) * 48).toFixed(1)}deg) scaleY(${(0.75 + (1 - elev) * 0.35).toFixed(3)})`
    gobo.style.opacity = day ? (0.4 + (1 - elev) * 0.45).toFixed(2) : '0'
  }
  listeners.forEach((fn) => fn(h))
}

/** Start following the real clock. Safe to call more than once. */
export function startSunlight() {
  if (timer) return
  applyLight(campusHour())
  timer = window.setInterval(() => { if (!playing) applyLight(campusHour()) }, 60000)
  window.addEventListener('resize', () => applyLight(shown))
}

export function onLight(fn) {
  listeners.add(fn)
  fn(shown)
  return () => listeners.delete(fn)
}

export const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

/** Time-lapse: the whole day from just before sunrise to after dusk, then back to now. */
export function playDay(duration = 9000) {
  if (playing) return Promise.resolve()
  playing = true
  const { rise, set } = sunTimes()
  const from = rise - 0.5
  const to = set + 1.2
  const ease = (x) => (x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2)
  return new Promise((resolve) => {
    const t0 = performance.now()
    const forward = (t) => {
      const p = Math.min(1, (t - t0) / duration)
      applyLight(from + ease(p) * (to - from))
      if (p < 1) return requestAnimationFrame(forward)
      const t1 = performance.now()
      const back = (tt) => {
        const q = Math.min(1, (tt - t1) / 1600)
        applyLight(to + (campusHour() - to) * ease(q))
        if (q < 1) requestAnimationFrame(back)
        else { playing = false; resolve() }
      }
      requestAnimationFrame(back)
    }
    requestAnimationFrame(forward)
  })
}
