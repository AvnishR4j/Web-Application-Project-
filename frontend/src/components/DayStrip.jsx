import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/client'

const FIRST_HOUR = 8
const LAST_HOUR = 20
const SPAN = (LAST_HOUR - FIRST_HOUR) * 60

const minutesOfDay = (d) => d.getHours() * 60 + d.getMinutes()
const pct = (mins) => `${Math.min(100, Math.max(0, ((mins - FIRST_HOUR * 60) / SPAN) * 100))}%`
const hm = (s) => { const [h, m] = s.split(':').map(Number); return h * 60 + m }
const time = (d) => d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
const sameDay = (a, b) => a.toDateString() === b.toDateString()
const clock = (mins) => {
  const h = Math.floor(mins / 60)
  const m = mins % 60
  return `${h % 12 === 0 ? 12 : h % 12}:${String(m).padStart(2, '0')} ${h < 12 ? 'am' : 'pm'}`
}
const span = (s, e) => `${time(s)} to ${time(e)}`
// Short form for inside a block: "10:30–11:20 am", or "11:30 am–12:30 pm" when it crosses noon.
const range = (s, e) => {
  const part = (d) => `${d.getHours() % 12 || 12}:${String(d.getMinutes()).padStart(2, '0')}`
  const ap = (d) => (d.getHours() < 12 ? 'am' : 'pm')
  return ap(s) === ap(e) ? `${part(s)}–${part(e)} ${ap(e)}` : `${part(s)} ${ap(s)}–${part(e)} ${ap(e)}`
}

/**
 * "Campus today": rooms as rows, 08:00 to 20:00 across, bookings drawn as marker strokes and a line for now.
 * People who book see every booking (approved solid, pending dashed). Everyone else sees published events.
 */
/**
 * Optional for the Book page: `selection` ({ start, end } as Dates) draws the request being made, and `onPick`
 * (start, end) is called when someone clicks a free spot, to fill in the form.
 */
export default function DayStrip({ canSeeBookings, resourceFilter, title = 'Campus today', compact = false, selection, onPick }) {
  const [day, setDay] = useState(() => { const d = new Date(); d.setHours(0, 0, 0, 0); return d })
  const [rooms, setRooms] = useState(null)
  const [blocks, setBlocks] = useState([])
  const [error, setError] = useState('')
  const [now, setNow] = useState(new Date())

  const [hover, setHover] = useState(null) // minutes of day under the pointer, snapped to 15

  useEffect(() => { const t = setInterval(() => setNow(new Date()), 60000); return () => clearInterval(t) }, [])

  // When the form's start moves to another day, show that day.
  const selStart = selection?.start && !Number.isNaN(selection.start.getTime()) ? selection.start : null
  const selEnd = selection?.end && !Number.isNaN(selection.end.getTime()) ? selection.end : null
  const selDayKey = selStart ? selStart.toDateString() : ''
  useEffect(() => {
    if (!selStart) return
    const d = new Date(selStart); d.setHours(0, 0, 0, 0)
    setDay((cur) => (cur.getTime() === d.getTime() ? cur : d))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selDayKey])

  useEffect(() => {
    let cancelled = false
    setError('')
    const from = day.toISOString()
    const to = new Date(day.getTime() + 86400000).toISOString()
    async function load() {
      const all = await api('/api/resources')
      const list = resourceFilter ? all.filter((r) => r.id === resourceFilter) : all
      // Published events, plus the viewer's own club events (which include ones that already ended today).
      const [open, mine] = await Promise.all([api('/api/events').catch(() => []), api('/api/events/mine').catch(() => [])])
      const events = [...open, ...mine.filter((m) => !open.some((o) => o.id === m.id))]
      let found = []
      if (canSeeBookings) {
        const schedules = await Promise.all(list.map((r) =>
          api(`/api/bookings/schedule?resourceId=${r.id}&from=${from}&to=${to}`)
            .then((slots) => slots.map((s) => ({ ...s, resourceId: r.id })))
            .catch(() => [])))
        found = schedules.flat().map((s) => {
          const ev = events.find((e) => e.resourceId === s.resourceId && new Date(e.start).getTime() === new Date(s.start).getTime())
          return { ...s, label: ev ? ev.title : s.status === 'PENDING' ? 'Requested' : 'Booked' }
        })
      } else {
        found = events.filter((e) => list.some((r) => r.id === e.resourceId))
          .map((e) => ({ resourceId: e.resourceId, start: e.start, end: e.end, status: 'APPROVED', label: e.title }))
      }
      if (!cancelled) { setRooms(list); setBlocks(found) }
    }
    load().catch((e) => !cancelled && setError(e.message))
    return () => { cancelled = true }
  }, [day, canSeeBookings, resourceFilter])

  const isToday = sameDay(day, now)
  const nowMins = minutesOfDay(now)
  const showNow = isToday && nowMins >= FIRST_HOUR * 60 && nowMins <= LAST_HOUR * 60
  const hours = useMemo(() => Array.from({ length: LAST_HOUR - FIRST_HOUR + 1 }, (_, i) => FIRST_HOUR + i), [])
  const move = (n) => setDay((d) => new Date(d.getTime() + n * 86400000))

  const dayBlocks = (roomId) => blocks.filter((b) => b.resourceId === roomId).map((b) => {
    const s = new Date(b.start)
    const e = new Date(b.end)
    const startM = sameDay(s, day) ? minutesOfDay(s) : 0
    const endM = sameDay(e, day) ? minutesOfDay(e) : 24 * 60
    return { ...b, startM, endM, s, e }
  }).filter((b) => b.endM > FIRST_HOUR * 60 && b.startM < LAST_HOUR * 60)

  const visibleCount = rooms ? rooms.reduce((n, r) => n + dayBlocks(r.id).length, 0) : 0
  const halfHours = useMemo(() => Array.from({ length: (LAST_HOUR - FIRST_HOUR) * 2 }, (_, i) => FIRST_HOUR * 60 + i * 30 + 30)
    .filter((m) => m % 60 !== 0 && m < LAST_HOUR * 60), [])

  const pointerMinutes = (ev) => {
    const box = ev.currentTarget.getBoundingClientRect()
    const raw = FIRST_HOUR * 60 + ((ev.clientX - box.left) / box.width) * SPAN
    return Math.min(LAST_HOUR * 60 - 15, Math.max(FIRST_HOUR * 60, Math.round(raw / 15) * 15))
  }
  const pick = (ev, items) => {
    if (!onPick) return
    const m = pointerMinutes(ev)
    if (items.some((b) => m >= b.startM && m < b.endM)) return
    const start = new Date(day); start.setMinutes(m)
    const end = new Date(start.getTime() + 60 * 60000)
    onPick(start, end)
  }

  // The request being made, on this day, and whether it overlaps something already there.
  const sel = selStart && selEnd && selEnd > selStart && sameDay(selStart, day)
    ? { startM: minutesOfDay(selStart), endM: sameDay(selEnd, day) ? minutesOfDay(selEnd) : 24 * 60 } : null
  const listed = rooms ? rooms.flatMap((r) => dayBlocks(r.id).map((b) => ({ ...b, room: r.name })))
    .sort((a, b) => a.s - b.s) : []

  return (
    <section className="daystrip" aria-label={title}>
      <div className="daystrip-head">
        <div>
          <h2>{title}</h2>
          <span className="muted">{day.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })}</span>
        </div>
        <div className="row">
          <button className="secondary" onClick={() => move(-1)} aria-label="Previous day">Previous</button>
          <button className="secondary" onClick={() => { const d = new Date(); d.setHours(0, 0, 0, 0); setDay(d) }} disabled={isToday}>Today</button>
          <button className="secondary" onClick={() => move(1)} aria-label="Next day">Next</button>
        </div>
      </div>
      {error && <p className="error" style={{ padding: '12px 18px', margin: 0 }}>{error}</p>}
      <div className="daystrip-scroll">
        <div className="daystrip-grid">
          <div className="ds-hours" aria-hidden="true">
            <div />
            <div className="ds-track">
              {hours.map((h, i) => (
                <span key={h} className={`ds-hour${i === 0 ? ' first' : i === hours.length - 1 ? ' last' : ''}`} style={{ left: pct(h * 60) }}>
                  {h <= 12 ? h : h - 12}{h < 12 ? 'am' : 'pm'}
                </span>
              ))}
              {halfHours.map((m) => <i key={m} className="ds-tick" style={{ left: pct(m) }} />)}
              {hover != null && <span className="ds-hover-time" style={{ left: pct(hover) }}>{clock(hover)}</span>}
            </div>
          </div>
          {!rooms ? (
            [0, 1, 2].map((i) => (
              <div className="ds-row" key={i}><div className="ds-label"><div className="skeleton" style={{ width: '70%' }} /></div><div className="ds-track" /></div>
            ))
          ) : rooms.map((r, ri) => {
            const items = dayBlocks(r.id)
            const open = r.openTime ? hm(r.openTime) : null
            const close = r.closeTime ? hm(r.closeTime) : null
            return (
              <div className="ds-row" key={r.id}>
                <div className="ds-label">
                  <strong>{r.name}</strong>
                  <span>{r.type === 'EQUIPMENT' ? 'Equipment' : `${r.capacity} seats`}{r.location ? `, ${r.location}` : ''}</span>
                </div>
                <div className={`ds-track${onPick ? ' pickable' : ''}`}
                  onMouseMove={(ev) => setHover(pointerMinutes(ev))} onMouseLeave={() => setHover(null)}
                  onClick={(ev) => pick(ev, items)}>
                  {open != null && open > FIRST_HOUR * 60 && <i className="ds-closed" style={{ left: 0, width: pct(open) }} title="Closed" />}
                  {close != null && close < LAST_HOUR * 60 && <i className="ds-closed" style={{ left: pct(close), right: 0 }} title="Closed" />}
                  {items.map((b, bi) => {
                    const left = pct(Math.max(b.startM, FIRST_HOUR * 60))
                    const right = pct(Math.min(b.endM, LAST_HOUR * 60))
                    return (
                      <div key={bi} className={`ds-block ${b.status}`} style={{ left, width: `calc(${right} - ${left})`, '--i': ri * 3 + bi }}
                        title={`${b.label}: ${span(b.s, b.e)}${b.status === 'PENDING' ? ' (waiting for approval)' : ''}`}>
                        <strong className="num">{range(b.s, b.e)}</strong>
                        <span>{b.label}</span>
                      </div>
                    )
                  })}
                  {sel && sel.endM > FIRST_HOUR * 60 && sel.startM < LAST_HOUR * 60 && (() => {
                    const clash = items.some((b) => sel.startM < b.endM && sel.endM > b.startM)
                    const left = pct(Math.max(sel.startM, FIRST_HOUR * 60))
                    const right = pct(Math.min(sel.endM, LAST_HOUR * 60))
                    return (
                      <div className={`ds-sel${clash ? ' clash' : ''}`} style={{ left, width: `calc(${right} - ${left})` }}
                        title={`Your request: ${span(selStart, selEnd)}${clash ? ', overlaps a booking' : ''}`}>
                        <span>{clash ? 'Clashes' : 'Your request'}</span>
                      </div>
                    )
                  })()}
                  {hover != null && <i className="ds-cursor" style={{ left: pct(hover) }} />}
                  {items.length === 0 && !compact && <span className="sr-only">Free all day</span>}
                </div>
              </div>
            )
          })}
          {showNow && (
            <div className="ds-now" style={{ left: `calc(var(--label) + (100% - var(--label)) * ${(nowMins - FIRST_HOUR * 60) / SPAN})` }}
              data-time={`Now ${time(now)}`} aria-label={`Now, ${time(now)}`} />
          )}
        </div>
      </div>
      {rooms && listed.length > 0 && (
        <ul className="ds-list" aria-label="Exact times">
          {listed.map((b, i) => (
            <li key={i}>
              <span className="when num">{time(b.s)} to {time(b.e)}</span>
              <span className="what">{b.label}{!resourceFilter && <span className="muted">, {b.room}</span>}</span>
              <span className={`badge ${b.status}`}>{b.status === 'PENDING' ? 'Waiting for approval' : canSeeBookings ? 'Confirmed' : 'Event'}</span>
            </li>
          ))}
        </ul>
      )}
      <div className="daystrip-head" style={{ borderBottom: 0, borderTop: '1px solid var(--line)' }}>
        <div className="ds-legend">
          <span><i className="k-approved" />{canSeeBookings ? 'Confirmed booking' : 'Event'}</span>
          {canSeeBookings && <span><i className="k-pending" />Waiting for approval</span>}
          <span><i className="k-closed" />Closed</span>
          {selection && <span><i className="k-sel" />Your request</span>}
        </div>
        {onPick && <span className="muted">Click a free spot to fill in the time.</span>}
        {rooms && visibleCount === 0 && <span className="muted">{canSeeBookings ? 'Every room is free between 8am and 8pm.' : 'No events on this day.'}</span>}
      </div>
    </section>
  )
}
