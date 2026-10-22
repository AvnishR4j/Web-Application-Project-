import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { ROLES, useAuth } from '../auth/AuthContext'
import DayDial, { RoomsToday, useDay } from '../components/DayDial'

const BOOKERS = [ROLES.CLUB_ORGANIZER, ROLES.RESOURCE_ADMIN, ROLES.SUPER_ADMIN]
const clock = (iso) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
const dayName = (iso) => new Date(iso).toLocaleDateString([], { weekday: 'short', day: 'numeric', month: 'short' })

function startOfToday() { const d = new Date(); d.setHours(0, 0, 0, 0); return d }

function until(iso, now) {
  const mins = Math.round((new Date(iso) - now) / 60000)
  if (mins <= 0) return 'on now'
  if (mins < 60) return `in ${mins} min`
  const h = Math.floor(mins / 60)
  if (h < 24) return `in ${h} h ${mins % 60} min`
  const d = Math.round(h / 24)
  return `in ${d} day${d > 1 ? 's' : ''}`
}

/** What needs doing next, by role. Each line links to where it gets done. */
function useTodo(role) {
  const [items, setItems] = useState(null)
  useEffect(() => {
    const safe = (p) => api(p).catch(() => [])
    async function load() {
      const out = []
      if (role === ROLES.CLUB_ORGANIZER || role === ROLES.SUPER_ADMIN) {
        const ev = await safe('/api/events/mine')
        const drafts = ev.filter((e) => e.status === 'DRAFT' || e.status === 'REJECTED').length
        const waiting = ev.filter((e) => e.status === 'SUBMITTED' || e.status === 'ADVISOR_APPROVED').length
        out.push({ to: '/my-events', count: drafts, text: drafts === 1 ? 'draft or returned event to finish' : 'drafts or returned events to finish' })
        out.push({ to: '/my-events', count: waiting, text: waiting === 1 ? 'event waiting for approval' : 'events waiting for approval' })
      }
      if (role === ROLES.FACULTY_ADVISOR || role === ROLES.SUPER_ADMIN) {
        const q = await safe('/api/events/advisor-queue')
        out.push({ to: '/advisor', count: q.length, text: q.length === 1 ? 'event from your clubs to review' : 'events from your clubs to review' })
      }
      if (role === ROLES.RESOURCE_ADMIN || role === ROLES.SUPER_ADMIN) {
        const q = await safe('/api/events/manager-queue')
        const b = (await safe('/api/bookings?status=PENDING')).filter((x) => !x.eventId)
        out.push({ to: '/event-approvals', count: q.length, text: q.length === 1 ? 'event needing final approval' : 'events needing final approval' })
        out.push({ to: '/approvals', count: b.length, text: b.length === 1 ? 'room request to decide' : 'room requests to decide' })
      }
      setItems(out)
    }
    load()
  }, [role])
  return items
}

/** The soonest published event that has not ended, and the viewer's next ticket. */
function useNext() {
  const [state, setState] = useState(null)
  useEffect(() => {
    Promise.all([api('/api/events').catch(() => []), api('/api/events/seats').catch(() => []),
      api('/api/registrations/mine').catch(() => []), api('/api/resources').catch(() => [])])
      .then(([events, seats, mine, rooms]) => {
        const upcoming = [...events].sort((a, b) => new Date(a.start) - new Date(b.start))
        const tickets = mine.filter((m) => (m.eventStatus === 'APPROVED') && new Date(m.end) > new Date() && m.registration.status !== 'CANCELLED')
          .sort((a, b) => new Date(a.start) - new Date(b.start))
        setState({
          events: upcoming,
          seats: Object.fromEntries(seats.map((s) => [s.eventId, s])),
          ticket: tickets[0] || null,
          rooms: Object.fromEntries(rooms.map((r) => [r.id, r.name])),
        })
      })
  }, [])
  return state
}

export default function Home() {
  const { user } = useAuth()
  const canBook = BOOKERS.includes(user.role)
  const [day, setDay] = useState(startOfToday)
  const [now, setNow] = useState(new Date())
  const { rooms, items, error } = useDay(day, canBook)
  const todo = useTodo(user.role)
  const next = useNext()

  useEffect(() => { const t = setInterval(() => setNow(new Date()), 30000); return () => clearInterval(t) }, [])
  const move = (n) => setDay((d) => new Date(d.getTime() + n * 86400000))
  const isToday = day.getTime() === startOfToday().getTime()

  const lead = next?.events[0]
  const leadSeats = lead && next.seats[lead.id]
  const leadLeft = leadSeats ? Math.max(0, leadSeats.capacity - leadSeats.confirmed) : null
  const later = next?.events.slice(1, 3) || []

  return (
    <div className="home">
      <div className="home-main">
        <div className="page-head" style={{ marginBottom: 0 }}>
          <div>
            <p className="eyebrow">{day.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })}</p>
            <h1>{isToday ? 'The campus, around the clock' : 'The campus that day'}</h1>
          </div>
          <div className="dial-nav">
            <button className="secondary" onClick={() => move(-1)} aria-label="Previous day">Previous</button>
            <button className="secondary" onClick={() => setDay(startOfToday())} disabled={isToday}>Today</button>
            <button className="secondary" onClick={() => move(1)} aria-label="Next day">Next</button>
          </div>
        </div>
        {error && <p className="notice bad" role="alert">{error}</p>}
        <div className="dial-wrap">
          {rooms ? <DayDial day={day} rooms={rooms} items={items} now={now} /> : <div className="dial"><div className="skeleton" style={{ height: '100%', borderRadius: '50%' }} /></div>}
          <div className="dial-key" aria-label="Key">
            <span className="k"><i className="k-ok" />{canBook ? 'Confirmed booking' : 'Event'}</span>
            {canBook && <span className="k"><i className="k-wait" />Waiting for approval</span>}
            <span className="k"><i className="k-now" />Happening now</span>
            <span className="muted" style={{ fontSize: '0.86rem', marginTop: 4 }}>Hover or tab to an arc for details. The gap at 8 is when rooms open and close.</span>
          </div>
        </div>
        <section className="panel" aria-label="Rooms">
          <h2 style={{ marginTop: 0, marginBottom: 6 }}>Rooms, ring by ring</h2>
          {rooms ? <RoomsToday day={day} rooms={rooms} items={items} now={now} /> : <div className="skeleton" />}
        </section>
      </div>

      <aside className="home-side" aria-label="Happening next">
        <p className="side-title">Happening next</p>
        {!next ? <div className="next-card"><div className="skeleton" /></div> : lead ? (
          <div className="next-card">
            <div className="when"><span>{clock(lead.start)}, {next.rooms[lead.resourceId] || 'Campus'}</span><span>{until(lead.start, now)}</span></div>
            <h3>{lead.title}</h3>
            <p>
              {dayName(lead.start)}.{' '}
              {leadSeats && (leadSeats.myStatus === 'CONFIRMED' ? 'You are in.' : leadSeats.myStatus === 'WAITLISTED' ? `You are #${leadSeats.myWaitlistPosition} on the waitlist.` : leadLeft > 0 ? `${leadLeft} of ${leadSeats.capacity} seats left.` : `Full, ${leadSeats.waitlisted} on the waitlist.`)}
            </p>
            {leadSeats && !leadSeats.myStatus && <Link to="/events"><button>{leadLeft > 0 ? 'Register' : 'Join waitlist'}</button></Link>}
          </div>
        ) : (
          <div className="next-card"><h3>Nothing scheduled</h3><p>Approved club events show up here.</p></div>
        )}
        {later.map((e) => (
          <Link key={e.id} to="/events" className="mini-card">
            <div className="k">{dayName(e.start)}, {clock(e.start)}</div>
            <div className="v">{e.title}</div>
            <div className="k">{next.rooms[e.resourceId]}</div>
          </Link>
        ))}
        {next?.ticket && (
          <Link to="/registrations" className="mini-card" style={{ borderColor: 'var(--line-strong)' }}>
            <div className="k">Your ticket, {dayName(next.ticket.start)}</div>
            <div className="v">{next.ticket.eventTitle}</div>
            <span className={`badge ${next.ticket.checkedIn ? 'CHECKED_IN' : next.ticket.registration.status}`}>
              {next.ticket.checkedIn ? 'Checked in' : next.ticket.registration.status === 'CONFIRMED' ? 'You are in' : `Waitlist #${next.ticket.waitlistPosition}`}
            </span>
          </Link>
        )}
        {todo && todo.length > 0 && (<>
          <p className="side-title">Your desk</p>
          <div className="todo">
            {todo.map((t, i) => (
              <Link key={i} to={t.to}>
                <span className={`count num${t.count === 0 ? ' zero' : ''}`}>{t.count}</span>
                <span>{t.text}</span>
              </Link>
            ))}
          </div>
        </>)}
        {canBook && <Link to="/book"><button style={{ width: '100%' }}>Book a room</button></Link>}
      </aside>
    </div>
  )
}
