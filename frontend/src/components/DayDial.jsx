import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/client'

/*
 * The Day Dial: the campus day (8am to 8pm) drawn on a real clock face, so 3pm sits at the 3.
 * Each room is a ring; bookings are arcs on it. Sand = confirmed, dashed sand = waiting for approval,
 * sage = happening right now. The hand shows the current time.
 */

const OPEN = 8
const CLOSE = 20
const C = 300
const OUTER = 236
const INNER = 128

const angleOf = (h) => ((h % 12) / 12) * Math.PI * 2 - Math.PI / 2
const point = (r, h) => [C + r * Math.cos(angleOf(h)), C + r * Math.sin(angleOf(h))]
const f = (n) => n.toFixed(1)

function arcPath(r, h1, h2) {
  const [x1, y1] = point(r, h1)
  const [x2, y2] = point(r, h2)
  const large = h2 - h1 > 6 ? 1 : 0
  return `M${f(x1)} ${f(y1)} A${r} ${r} 0 ${large} 1 ${f(x2)} ${f(y2)}`
}

const hoursOf = (d) => d.getHours() + d.getMinutes() / 60
const sameDay = (a, b) => a.toDateString() === b.toDateString()
const clock = (d) => d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })

/** Rooms plus what is booked in each on one day. People who book see all bookings; others see published events. */
export function useDay(day, canSeeBookings) {
  const [state, setState] = useState({ rooms: null, items: [], error: '' })
  useEffect(() => {
    let cancelled = false
    const from = day.toISOString()
    const to = new Date(day.getTime() + 86400000).toISOString()
    async function load() {
      const rooms = await api('/api/resources')
      const [open, mine] = await Promise.all([api('/api/events').catch(() => []), api('/api/events/mine').catch(() => [])])
      const events = [...open, ...mine.filter((m) => !open.some((o) => o.id === m.id))]
      let items
      if (canSeeBookings) {
        const lists = await Promise.all(rooms.map((r) =>
          api(`/api/bookings/schedule?resourceId=${r.id}&from=${from}&to=${to}`)
            .then((slots) => slots.map((s) => ({ ...s, resourceId: r.id }))).catch(() => [])))
        items = lists.flat().map((s) => {
          const ev = events.find((e) => e.resourceId === s.resourceId && new Date(e.start).getTime() === new Date(s.start).getTime())
          return { ...s, label: ev ? ev.title : s.status === 'PENDING' ? 'Booking request' : 'Booked', club: ev?.clubId }
        })
      } else {
        items = open.map((e) => ({ resourceId: e.resourceId, start: e.start, end: e.end, status: 'APPROVED', label: e.title }))
      }
      // Keep only what overlaps this day (published events can be on any date).
      {
        const dayStart = day.getTime()
        const dayEnd = dayStart + 86400000
        items = items.filter((b) => new Date(b.start).getTime() < dayEnd && new Date(b.end).getTime() > dayStart)
      }
      if (!cancelled) setState({ rooms, items, error: '' })
    }
    load().catch((e) => !cancelled && setState((s) => ({ ...s, rooms: s.rooms || [], error: e.message })))
    return () => { cancelled = true }
  }, [day, canSeeBookings])
  return state
}

export default function DayDial({ day, rooms, items, now }) {
  const [tip, setTip] = useState(null)
  const isToday = sameDay(day, now)
  const nowH = hoursOf(now)
  const showHand = isToday && nowH >= OPEN && nowH <= CLOSE

  const ringR = useMemo(() => {
    const n = Math.max(1, rooms.length)
    const band = (OUTER - INNER) / Math.max(n - 1, 1)
    return rooms.map((_, i) => (n === 1 ? (OUTER + INNER) / 2 : OUTER - i * band))
  }, [rooms])
  const stroke = Math.max(6, Math.min(18, ((OUTER - INNER) / Math.max(rooms.length, 1)) * 0.62))

  const arcs = []
  rooms.forEach((r, i) => {
    items.filter((b) => b.resourceId === r.id).forEach((b) => {
      const s = new Date(b.start)
      const e = new Date(b.end)
      const h1 = Math.max(OPEN, sameDay(s, day) ? hoursOf(s) : 0)
      const h2 = Math.min(CLOSE, sameDay(e, day) ? hoursOf(e) : 24)
      if (h2 <= h1) return
      const live = isToday && b.status === 'APPROVED' && now >= s && now < e
      const mid = point(ringR[i], (h1 + h2) / 2)
      arcs.push({
        key: `${r.id}-${b.start}`, d: arcPath(ringR[i], h1 + 0.04, h2 - 0.04), cls: live ? 'NOW' : b.status,
        room: r.name, label: b.label, s, e, live, x: (mid[0] / 600) * 100, y: (mid[1] / 600) * 100,
      })
    })
  })

  const hours = []
  for (let h = OPEN; h < CLOSE; h++) {
    const [tx1, ty1] = point(OUTER + 14, h)
    const [tx2, ty2] = point(OUTER + 22, h)
    const [lx, ly] = point(OUTER + 40, h)
    const label = h === 12 ? '12' : String(h % 12)
    hours.push({ h, tx1, ty1, tx2, ty2, lx, ly, label, major: h % 3 === 0 })
  }
  const [gx, gy] = point(OUTER + 40, OPEN)
  // The hand starts outside the centre read-out, so it never crosses the time.
  const [hx, hy] = point(OUTER + 10, nowH)
  const [hsx, hsy] = point(INNER - 10, nowH)
  const busyNow = new Set(arcs.filter((a) => a.live).map((a) => a.room)).size

  const describe = (a) => `${a.label}, ${a.room}, ${clock(a.s)} to ${clock(a.e)}${a.cls === 'PENDING' ? ', waiting for approval' : a.live ? ', happening now' : ''}`

  return (
    <div className="dial">
      <svg viewBox="0 0 600 600" role="img" aria-label={`Bookings on ${day.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })} from 8am to 8pm, one ring per room`}>
        <circle className="d-face" cx={C} cy={C} r={INNER - 26} />
        {ringR.map((r, i) => (
          <path key={i} className="d-track" d={arcPath(r, OPEN + 0.08, CLOSE - 0.08)} strokeWidth={stroke} strokeLinecap="round" />
        ))}
        {hours.map((t) => (
          <g key={t.h}>
            <line className={`d-tick${t.major ? ' major' : ''}`} x1={f(t.tx1)} y1={f(t.ty1)} x2={f(t.tx2)} y2={f(t.ty2)} />
            {t.h !== OPEN && <text className="d-hour" x={f(t.lx)} y={f(t.ly)} textAnchor="middle" dominantBaseline="middle">{t.label}</text>}
          </g>
        ))}
        <text className="d-gap" x={f(gx)} y={f(gy - 7)} textAnchor="middle">8</text>
        <text className="d-gap" x={f(gx)} y={f(gy + 9)} textAnchor="middle">open/close</text>
        {ringR.map((r, i) => {
          const [x, y] = point(r, OPEN)
          return (
            <g key={`n${i}`} aria-hidden="true">
              <circle cx={f(x)} cy={f(y)} r="9" fill="var(--bg)" stroke="var(--line-strong)" />
              <text className="d-room" x={f(x)} y={f(y)} textAnchor="middle" dominantBaseline="central" style={{ fontSize: 10 }}>{i + 1}</text>
            </g>
          )
        })}
        {arcs.map((a) => (
          <path key={a.key} className={`d-arc ${a.cls}`} d={a.d} strokeWidth={stroke} tabIndex={0}
            aria-label={describe(a)}
            onMouseEnter={() => setTip(a)} onMouseLeave={() => setTip(null)}
            onFocus={() => setTip(a)} onBlur={() => setTip(null)}>
            <title>{describe(a)}</title>
          </path>
        ))}
        {showHand && (
          <g aria-hidden="true">
            <line className="d-hand" x1={f(hsx)} y1={f(hsy)} x2={f(hx)} y2={f(hy)} />
            <circle className="d-pin" cx={f(hsx)} cy={f(hsy)} r="4" />
            <circle className="d-pin" cx={f(hx)} cy={f(hy)} r="3" />
          </g>
        )}
      </svg>
      <div className="dial-center" aria-hidden={tip ? 'true' : undefined}>
        {isToday ? (
          <>
            <div className="t">{now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })}</div>
            <div className="s">{nowH < OPEN || nowH > CLOSE ? 'Rooms are closed' : busyNow === 0 ? 'Every room is free' : `${busyNow} room${busyNow > 1 ? 's' : ''} in use`}</div>
          </>
        ) : (
          <>
            <div className="t">{day.toLocaleDateString([], { day: 'numeric', month: 'short' })}</div>
            <div className="s">{arcs.length === 0 ? 'Nothing booked' : `${arcs.length} booking${arcs.length > 1 ? 's' : ''}`}</div>
          </>
        )}
      </div>
      {tip && (
        <div className="dial-tip" style={{ left: `${tip.x}%`, top: `${tip.y}%` }} role="status">
          <strong>{tip.label}</strong>
          {tip.room}, {clock(tip.s)} to {clock(tip.e)}
          {tip.cls === 'PENDING' && <span className="muted">, waiting</span>}
          {tip.live && <span style={{ color: 'var(--sage)' }}>, on now</span>}
        </div>
      )}
    </div>
  )
}

/** Today's rooms as a list: the dial's legend and its accessible text version. */
export function RoomsToday({ day, rooms, items, now }) {
  const isToday = sameDay(day, now)
  return (
    <div className="rooms-today">
      {rooms.map((r, i) => {
        const mine = items.filter((b) => b.resourceId === r.id).sort((a, b) => new Date(a.start) - new Date(b.start))
        const current = isToday && mine.find((b) => b.status === 'APPROVED' && now >= new Date(b.start) && now < new Date(b.end))
        const next = mine.find((b) => new Date(b.start) > (isToday ? now : day))
        return (
          <div className="rt" key={r.id}>
            <span className="ring-no" aria-label={`Ring ${i + 1}`}>{i + 1}</span>
            <div style={{ minWidth: 0 }}>
              <strong>{r.name}</strong>
              <span>
                {current ? `${current.label} until ${clock(new Date(current.end))}`
                  : next ? `Next: ${next.label} at ${clock(new Date(next.start))}`
                  : r.type === 'EQUIPMENT' ? 'Equipment' : `${r.capacity} seats`}
              </span>
            </div>
            <span className={`state ${current ? 'busy' : 'free'}`}>{current ? 'In use' : mine.length ? `${mine.length} booked` : 'Free'}</span>
          </div>
        )
      })}
    </div>
  )
}
