import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { ROLES, useAuth } from '../auth/AuthContext'
import Inked from '../components/Inked'
import SunRuler from '../components/SunRuler'
import useProgramme from '../components/useProgramme'

const BOOKERS = [ROLES.CLUB_ORGANIZER, ROLES.RESOURCE_ADMIN, ROLES.SUPER_ADMIN]

/** What needs doing next, by role, as notes on the desk. */
function useDesk(role) {
  const [items, setItems] = useState([])
  useEffect(() => {
    const safe = (p) => api(p).catch(() => [])
    async function load() {
      const out = []
      if (role === ROLES.CLUB_ORGANIZER || role === ROLES.SUPER_ADMIN) {
        const ev = await safe('/api/events/mine')
        const drafts = ev.filter((e) => e.status === 'DRAFT' || e.status === 'REJECTED').length
        const waiting = ev.filter((e) => e.status === 'SUBMITTED' || e.status === 'ADVISOR_APPROVED').length
        out.push({ to: '/my-events', count: drafts, text: drafts === 1 ? 'draft to finish' : 'drafts to finish' })
        out.push({ to: '/my-events', count: waiting, text: waiting === 1 ? 'event awaiting approval' : 'events awaiting approval' })
      }
      if (role === ROLES.FACULTY_ADVISOR || role === ROLES.SUPER_ADMIN) {
        const q = await safe('/api/events/advisor-queue')
        out.push({ to: '/advisor', count: q.length, text: q.length === 1 ? 'event to review' : 'events to review' })
      }
      if (role === ROLES.RESOURCE_ADMIN || role === ROLES.SUPER_ADMIN) {
        const q = await safe('/api/events/manager-queue')
        const b = (await safe('/api/bookings?status=PENDING')).filter((x) => !x.eventId)
        out.push({ to: '/event-approvals', count: q.length, text: q.length === 1 ? 'event for final approval' : 'events for final approval' })
        out.push({ to: '/approvals', count: b.length, text: b.length === 1 ? 'room request' : 'room requests' })
      }
      setItems(out)
    }
    load()
  }, [role])
  return items
}

export default function Home() {
  const { user } = useAuth()
  const canBook = BOOKERS.includes(user.role)
  const desk = useDesk(user.role)
  const { events, loaded, error, ticketFor } = useProgramme()

  const todayKey = new Date().toDateString()
  const now = Date.now()
  const today = events.filter((e) => new Date(e.start).toDateString() === todayKey || (new Date(e.start) <= now && new Date(e.end) > now))
  const upcoming = today.length ? [] : events.slice(0, 6)
  const first = user.name.split(' ')[0]

  return (
    <div>
      <section className="today-hero">
        <div>
          <p className="eyebrow">{new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' })} · Good {greeting()}, {first}</p>
          <h1><Inked>{['What’s on, ', <em key="t">today.</em>]}</Inked></h1>
          <p className="lede">
            {today.length
              ? `${today.length} ${today.length === 1 ? 'event' : 'events'} on campus today. Pick a ticket and tear off the stub; your seat is kept.`
              : 'A quiet day on campus. Here is what is coming up next; take a seat early, they go quickly.'}
          </p>
          <div className="hero-actions">
            <Link to="/events"><button className="secondary">The full programme</button></Link>
            {canBook && <Link to="/book"><button>Book a room</button></Link>}
          </div>
        </div>
        <SunRuler />
      </section>

      {desk.length > 0 && (
        <nav className="desk-notes" aria-label="Your desk">
          {desk.map((d, i) => (
            <Link key={i} to={d.to} className={`desk-note${d.count === 0 ? ' zero' : ''}`}><b>{d.count}</b><span>{d.text}</span></Link>
          ))}
        </nav>
      )}

      {error && <p className="notice bad" role="alert" style={{ marginBottom: 20 }}>{error}</p>}

      {!loaded ? (
        <div className="ticket-grid">{[0, 1, 2].map((i) => <div key={i} className="tk-ghost" />)}</div>
      ) : today.length ? (
        <div className="ticket-grid">{today.map((e, i) => ticketFor(e, i))}</div>
      ) : upcoming.length ? (
        <>
          <h2 className="date-group">Coming up</h2>
          <div className="ticket-grid">{upcoming.map((e, i) => ticketFor(e, i, { dated: true }))}</div>
        </>
      ) : (
        <div className="empty"><p>Nothing is scheduled yet.</p><span>Approved club events will be printed here.</span></div>
      )}
    </div>
  )
}

function greeting() {
  const h = new Date().getHours()
  return h < 12 ? 'morning' : h < 17 ? 'afternoon' : 'evening'
}
