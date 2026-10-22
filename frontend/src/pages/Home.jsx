import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { ROLES, useAuth } from '../auth/AuthContext'
import DayStrip from '../components/DayStrip'
import { fmt } from './eventUtils'

const BOOKERS = [ROLES.CLUB_ORGANIZER, ROLES.RESOURCE_ADMIN, ROLES.SUPER_ADMIN]

function greeting(d = new Date()) {
  const h = d.getHours()
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'
}

/** What needs doing next, by role. Each line links to the page that does it. */
function useTodo(role) {
  const [items, setItems] = useState(null)
  useEffect(() => {
    const safe = (p) => api(p).catch(() => [])
    async function load() {
      const out = []
      if (role === ROLES.STUDENT || role === ROLES.CLUB_ORGANIZER || role === ROLES.FACULTY_ADVISOR) {
        const mine = await safe('/api/registrations/mine')
        const upcoming = mine.filter((m) => m.eventStatus === 'APPROVED' && new Date(m.end) > new Date() && m.registration.status !== 'CANCELLED')
        out.push({ to: '/registrations', count: upcoming.length, text: upcoming.length === 1 ? 'upcoming event you are going to' : 'upcoming events you are going to' })
        const open = await safe('/api/events')
        out.push({ to: '/events', count: open.length, text: open.length === 1 ? 'event open for registration' : 'events open for registration' })
      }
      if (role === ROLES.CLUB_ORGANIZER || role === ROLES.SUPER_ADMIN) {
        const ev = await safe('/api/events/mine')
        const drafts = ev.filter((e) => e.status === 'DRAFT' || e.status === 'REJECTED').length
        const waiting = ev.filter((e) => e.status === 'SUBMITTED' || e.status === 'ADVISOR_APPROVED').length
        out.push({ to: '/my-events', count: drafts, text: drafts === 1 ? 'draft or rejected event to finish' : 'drafts or rejected events to finish' })
        out.push({ to: '/my-events', count: waiting, text: waiting === 1 ? 'event waiting for approval' : 'events waiting for approval' })
      }
      if (role === ROLES.FACULTY_ADVISOR || role === ROLES.SUPER_ADMIN) {
        const q = await safe('/api/events/advisor-queue')
        out.push({ to: '/advisor', count: q.length, text: q.length === 1 ? 'event from your clubs to review' : 'events from your clubs to review' })
      }
      if (role === ROLES.RESOURCE_ADMIN || role === ROLES.SUPER_ADMIN) {
        const q = await safe('/api/events/manager-queue')
        const b = (await safe('/api/bookings?status=PENDING')).filter((x) => !x.eventId)
        out.push({ to: '/event-approvals', count: q.length, text: q.length === 1 ? 'event waiting for final approval' : 'events waiting for final approval' })
        out.push({ to: '/approvals', count: b.length, text: b.length === 1 ? 'room booking request' : 'room booking requests' })
      }
      setItems(out)
    }
    load()
  }, [role])
  return items
}

function NextTicket() {
  const [next, setNext] = useState(undefined)
  useEffect(() => {
    api('/api/registrations/mine').then((list) => {
      const upcoming = list
        .filter((m) => m.eventStatus === 'APPROVED' && new Date(m.end) > new Date() && m.registration.status !== 'CANCELLED')
        .sort((a, b) => new Date(a.start) - new Date(b.start))
      setNext(upcoming[0] || null)
    }).catch(() => setNext(null))
  }, [])
  if (next === undefined) return <div className="panel"><div className="skeleton" style={{ width: '60%' }} /></div>
  if (!next) return null
  const r = next.registration
  return (
    <div className="panel">
      <h3 style={{ marginBottom: 2 }}>Your next event</h3>
      <p style={{ margin: '0 0 10px' }}><strong>{next.eventTitle}</strong><br /><span className="muted">{fmt(next.start)}</span></p>
      <span className={`badge ${r.status}`}>{r.status === 'CONFIRMED' ? (next.checkedIn ? 'Checked in' : 'Registered') : `Waitlist #${next.waitlistPosition}`}</span>
    </div>
  )
}

export default function Home() {
  const { user } = useAuth()
  const todo = useTodo(user.role)
  const firstName = user.name.split(' ')[0]
  return (
    <div className="card">
      <div className="page-head">
        <div>
          <h1>{greeting()}, {firstName}</h1>
          <p>{new Date().toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</p>
        </div>
        {BOOKERS.includes(user.role) && <Link to="/book"><button>Book a room</button></Link>}
      </div>

      <DayStrip canSeeBookings={BOOKERS.includes(user.role)} />

      <div className="home-grid">
        <section>
          <h2 style={{ marginTop: 0 }}>Next up for you</h2>
          {!todo ? <div className="panel"><div className="skeleton" /></div> : (
            <div className="todo">
              {todo.map((t, i) => (
                <Link key={i} to={t.to}>
                  <span className={`count num${t.count === 0 ? ' zero' : ''}`}>{t.count}</span>
                  <span>{t.text}</span>
                </Link>
              ))}
            </div>
          )}
        </section>
        <aside className="stack">
          {user.role !== ROLES.RESOURCE_ADMIN && <NextTicket />}
          <div className="panel">
            <h3>How this board works</h3>
            <p className="muted" style={{ margin: 0 }}>
              {BOOKERS.includes(user.role)
                ? 'Solid strokes are confirmed bookings, dashed ones are waiting for approval. Hatched hours are when a room is closed. A request that clashes with anything on the board is refused before it is saved.'
                : 'Each stroke is an approved event in that room. Register from Events; if it is full you join the waitlist and move up automatically when someone cancels.'}
            </p>
          </div>
        </aside>
      </div>
    </div>
  )
}
