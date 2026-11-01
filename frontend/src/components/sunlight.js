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
const smooth = (t) => t * t * (3 - 2 * t)
const rgb = (c) => `${Math.round(c[0])}, ${Math.round(c[1])}, ${Math.round(c[2])}`

/*
 * The room through the day. Each moment sets:
 *   desk     colour of the desk (page background)
 *   tint     colour the whole scene is multiplied by (the colour of the light in the room)
 *   beam     colour and strength of the sun shafts through the window
 *   pane     colour and strength of the window light landing on the desk
 *   vig      how dark the corners of the room are
 *   lamp     how much the desk lamp is lighting the page
 *   paper    colour of the ticket stock in that light
 */
const ROOM = {
  night:    { desk: [44, 38, 33],    tint: [232, 222, 206], beam: [180, 200, 240, 0],    pane: [170, 192, 236, 0.10], vig: 0.62, lamp: 1,    paper: [238, 228, 210] },
  blue:     { desk: [184, 186, 196], tint: [226, 230, 240], beam: [190, 205, 235, 0.06], pane: [200, 212, 238, 0.22], vig: 0.32, lamp: 0.3,  paper: [242, 241, 238] },
  dawn:     { desk: [212, 192, 184], tint: [250, 232, 226], beam: [255, 196, 170, 0.42], pane: [255, 210, 186, 0.6],  vig: 0.24, lamp: 0,    paper: [251, 243, 236] },
  morning:  { desk: [233, 226, 213], tint: [255, 251, 244], beam: [255, 238, 208, 0.5],  pane: [255, 247, 228, 0.85], vig: 0.12, lamp: 0,    paper: [251, 248, 241] },
  noon:     { desk: [238, 233, 224], tint: [255, 255, 252], beam: [255, 250, 238, 0.22], pane: [255, 252, 244, 0.7],  vig: 0.07, lamp: 0,    paper: [251, 248, 241] },
  after:    { desk: [237, 229, 214], tint: [255, 250, 240], beam: [255, 234, 196, 0.42], pane: [255, 242, 214, 0.85], vig: 0.1,  lamp: 0,    paper: [251, 247, 238] },
  golden:   { desk: [238, 218, 186], tint: [255, 240, 214], beam: [255, 192, 112, 0.85], pane: [255, 204, 132, 0.95], vig: 0.2,  lamp: 0,    paper: [252, 242, 224] },
  sunset:   { desk: [224, 186, 164], tint: [252, 228, 214], beam: [255, 142, 92, 0.6],   pane: [255, 156, 108, 0.75], vig: 0.32, lamp: 0.05, paper: [248, 234, 222] },
  dusk:     { desk: [184, 168, 182], tint: [238, 228, 236], beam: [210, 160, 210, 0.08], pane: [200, 170, 214, 0.18], vig: 0.36, lamp: 0.35, paper: [244, 236, 232] },
}

/** Where each moment falls today, from sunrise (r) and sunset (s). */
function timeline(r, s) {
  const m = (r + s) / 2
  return [
    [r - 1.6, ROOM.night], [r - 0.6, ROOM.blue], [r, ROOM.dawn], [r + 1.4, ROOM.morning],
    [m, ROOM.noon], [s - 2.4, ROOM.after], [s - 0.9, ROOM.golden], [s, ROOM.sunset],
    [s + 0.45, ROOM.dusk], [s + 1.3, ROOM.night],
  ]
}

function roomAt(h, r, s) {
  const tl = timeline(r, s)
  if (h <= tl[0][0] || h >= tl[tl.length - 1][0]) return ROOM.night
  for (let i = 0; i < tl.length - 1; i++) {
    const [h0, a] = tl[i]
    const [h1, b] = tl[i + 1]
    if (h <= h1) {
      const t = smooth((h - h0) / (h1 - h0))
      const out = {}
      for (const k of Object.keys(a)) out[k] = Array.isArray(a[k]) ? mix(a[k], b[k], t) : a[k] + (b[k] - a[k]) * t
      return out
    }
  }
  return ROOM.night
}

const INK = [29, 27, 23]
const CREAM = [238, 228, 212]
const lum = (c) => 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]

const listeners = new Set()
let shown = campusHour()
let playing = false
let timer = 0

/** Apply the light for a given campus hour. */
export function applyLight(h) {
  shown = h
  const { rise, set } = sunTimes()
  const root = document.documentElement
  const W = window.innerWidth
  const H = window.innerHeight
  const room = roomAt(h, rise, set)
  const k = clamp01((h - rise) / (set - rise)) // 0 at sunrise, 1 at sunset
  const up = h > rise - 0.2 && h < set + 0.2
  const elev = Math.max(0.06, Math.sin(Math.PI * k)) // how high the sun is
  const daylight = 1 - room.lamp

  // Ink on the desk turns light when the room gets dark; paper always keeps dark ink.
  const dl = lum(room.desk)
  const t = smooth(clamp01((150 - dl) / 60)) // 0 = dark ink, 1 = light ink
  const ink = mix(INK, CREAM, t)
  const soft = mix([63, 58, 50], [214, 202, 184], t)
  const muted = mix([107, 100, 88], [170, 158, 142], t)
  const rule = mix([201, 191, 173], [104, 94, 82], t)
  const set3 = (k3, v) => root.style.setProperty(k3, v)
  set3('--desk', `rgb(${rgb(room.desk)})`)
  set3('--ink', `rgb(${rgb(ink)})`)
  set3('--ink-soft', `rgb(${rgb(soft)})`)
  set3('--muted', `rgb(${rgb(muted)})`)
  set3('--line-strong', `rgb(${rgb(rule)})`)
  set3('--on-sand', `rgb(${rgb(t > 0.5 ? room.desk : [251, 248, 241])})`)
  set3('--surface', `rgb(${rgb(room.paper)})`)
  set3('--raised', `rgb(${rgb(mix(room.paper, [255, 255, 255], 0.35))})`)
  set3('--tint', `rgb(${rgb(room.tint)})`)
  set3('--vig', room.vig.toFixed(3))
  set3('--lamp', room.lamp.toFixed(3))
  set3('--beam', `rgba(${rgb(room.beam)}, ${room.beam[3].toFixed(3)})`)
  set3('--beam-a', room.beam[3].toFixed(3))
  set3('--beam-c', rgb(room.beam))
  set3('--pane', `rgba(${rgb(room.pane)}, ${room.pane[3].toFixed(3)})`)
  set3('--dark', t.toFixed(3))

  // Shadows: from the sun by day (long and warm when low), straight down from the lamp at night.
  const az = Math.PI * k
  const len = 4 + (1 - elev) * 24
  const sx = Math.cos(az) * len * daylight
  const sy = (6 + (1 - elev) * 8) * daylight + 14 * (1 - daylight)
  const sb = (10 + (1 - elev) * 22) * daylight + 28 * (1 - daylight)
  const alpha = (0.15 + (1 - elev) * 0.14) * daylight + 0.42 * (1 - daylight)
  set3('--sx', `${sx.toFixed(1)}px`)
  set3('--sy', `${sy.toFixed(1)}px`)
  set3('--sb', `${sb.toFixed(1)}px`)
  set3('--sc', `rgba(${Math.round(70 + (1 - elev) * 40 * daylight)}, ${Math.round(52 + (1 - elev) * 8)}, 30, ${alpha.toFixed(3)})`)

  // The window is above the page. Its light lands on the desk opposite the sun (the panes) and
  // is visible in the air as shafts running from the window to the panes (the beams).
  const landX = W * (0.78 - k * 0.9)          // where the window light lands (morning: right, evening: left)
  const landY = H * (0.42 + (1 - elev) * 0.12)
  const winX = W * (0.02 + k * 0.96)          // where the window is (towards the sun)
  const winY = -H * (0.15 + elev * 0.55)      // high sun: window far above, steep beams
  const dx = landX - winX
  const dy = landY - winY
  const angle = (Math.atan2(-dx, dy) * 180) / Math.PI
  const length = Math.hypot(dx, dy) * 1.25
  const panes = document.getElementById('gobo')
  if (panes) {
    panes.style.transform = `translate3d(${(landX - 500).toFixed(0)}px, ${(landY - 260).toFixed(0)}px, 0) skewX(${((0.5 - k) * 46).toFixed(1)}deg) scaleY(${(0.7 + (1 - elev) * 0.4).toFixed(3)})`
    panes.style.opacity = up ? '1' : '0.9'
  }
  const beams = document.getElementById('beams')
  if (beams) {
    beams.style.transform = `translate3d(${winX.toFixed(0)}px, ${winY.toFixed(0)}px, 0) rotate(${angle.toFixed(2)}deg)`
    beams.style.height = `${length.toFixed(0)}px`
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
export function playDay(duration = 12000) {
  if (playing) return Promise.resolve()
  playing = true
  const { rise, set } = sunTimes()
  const from = rise - 1.2
  const to = set + 1.6
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

/** While someone drags the sun: show that hour, and hold off the clock. */
let returning = 0
export function scrubTo(h) {
  cancelAnimationFrame(returning)
  playing = true
  applyLight(h)
}

/** Let go: the light glides back to the real time. */
export function releaseScrub(duration = 1400) {
  const from = shown
  const ease = (x) => 1 - Math.pow(1 - x, 3)
  const t0 = performance.now()
  const step = (t) => {
    const q = Math.min(1, (t - t0) / duration)
    applyLight(from + (campusHour() - from) * ease(q))
    if (q < 1) returning = requestAnimationFrame(step)
    else playing = false
  }
  returning = requestAnimationFrame(step)
}
