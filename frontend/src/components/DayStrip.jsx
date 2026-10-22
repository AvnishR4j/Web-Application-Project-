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

/**
 * "Campus today": rooms as rows, 08:00 to 20:00 across, bookings drawn as marker strokes and a line for now.
 * People who book see every booking (approved solid, pending dashed). Everyone else sees published events.
 */
export default function DayStrip({ canSeeBookings, resourceFilter, title = 'Campus today', compact = false }) {
  const [day, setDay] = useState(() => { const d = new Date(); d.setHours(0, 0, 0, 0); return d })
  const [rooms, setRooms] = useState(null)
  const [blocks, setBlocks] = useState([])
  const [error, setError] = useState('')
  const [now, setNow] = useState(new Date())

  useEffect(() => { const t = setInterval(() => setNow(new Date()), 60000); return () => clearInterval(t) }, [])

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
                <div className="ds-track">
                  {open != null && open > FIRST_HOUR * 60 && <i className="ds-closed" style={{ left: 0, width: pct(open) }} title="Closed" />}
                  {close != null && close < LAST_HOUR * 60 && <i className="ds-closed" style={{ left: pct(close), right: 0 }} title="Closed" />}
                  {items.map((b, bi) => {
                    const left = pct(Math.max(b.startM, FIRST_HOUR * 60))
                    const right = pct(Math.min(b.endM, LAST_HOUR * 60))
                    return (
                      <div key={bi} className={`ds-block ${b.status}`} style={{ left, width: `calc(${right} - ${left})`, '--i': ri * 3 + bi }}
                        title={`${b.label}: ${time(b.s)} to ${time(b.e)}${b.status === 'PENDING' ? ' (waiting for approval)' : ''}`}>
                        {b.label}
                      </div>
                    )
                  })}
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
      <div className="daystrip-head" style={{ borderBottom: 0, borderTop: '1px solid var(--line)' }}>
        <div className="ds-legend">
          <span><i className="k-approved" />{canSeeBookings ? 'Confirmed booking' : 'Event'}</span>
          {canSeeBookings && <span><i className="k-pending" />Waiting for approval</span>}
          <span><i className="k-closed" />Closed</span>
        </div>
        {rooms && visibleCount === 0 && <span className="muted">{canSeeBookings ? 'Every room is free between 8am and 8pm.' : 'No events on this day.'}</span>}
      </div>
    </section>
  )
}
