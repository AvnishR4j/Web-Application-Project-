import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import Ticket, { byDay, dayLabel } from '../components/Ticket'
import { byId, describeError } from './eventUtils'

// Approved upcoming events as admit tickets, with seats left and the viewer's own place.
export default function Events() {
  const [events, setEvents] = useState([])
  const [seats, setSeats] = useState({})
  const [clubs, setClubs] = useState({})
  const [rooms, setRooms] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(null)
  const [loaded, setLoaded] = useState(false)
  const [query, setQuery] = useState('')

  const load = useCallback(() => {
    Promise.all([api('/api/events'), api('/api/events/seats'), api('/api/clubs'), api('/api/resources')])
      .then(([e, s, c, r]) => {
        setEvents(e)
        setSeats(Object.fromEntries(s.map((x) => [x.eventId, x])))
        setClubs(byId(c)); setRooms(byId(r))
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoaded(true))
  }, [])
  useEffect(load, [load])

  async function act(id, action) {
    setError(''); setBusy(id)
    try { await api(`/api/events/${id}/${action}`, { method: 'POST' }); load() }
    catch (err) { setError(describeError(err)) }
    finally { setBusy(null) }
  }

  const q = query.trim().toLowerCase()
  const shown = events.filter((e) => !q || [e.title, e.description, clubs[e.clubId]?.name, rooms[e.resourceId]?.name]
    .some((v) => v && v.toLowerCase().includes(q)))

  function stub(e) {
    const s = seats[e.id]
    if (!s) return null
    const left = Math.max(0, s.capacity - s.confirmed)
    if (s.myStatus === 'CONFIRMED') {
      return (<>
        <span className="stub-status CONFIRMED">You are in</span>
        <button className="secondary" disabled={busy === e.id} onClick={() => act(e.id, 'unregister')}>Cancel my place</button>
      </>)
    }
    if (s.myStatus === 'WAITLISTED') {
      return (<>
        <span className="stub-status WAITLISTED">Waitlist #{s.myWaitlistPosition}</span>
        <span className="muted" style={{ fontSize: '0.85rem' }}>You move up automatically if someone cancels.</span>
        <button className="secondary" disabled={busy === e.id} onClick={() => act(e.id, 'unregister')}>Leave waitlist</button>
      </>)
    }
    return (<>
      <span className="stub-status NONE">{left > 0 ? 'Seats available' : 'Full'}</span>
      <button disabled={busy === e.id} onClick={() => act(e.id, 'register')}>{left > 0 ? 'Register' : 'Join waitlist'}</button>
    </>)
  }

  return (
    <div className="card">
      <div className="page-head">
        <div>
          <h1>Events</h1>
          <p>Approved events from every club. Registering holds a seat; when an event is full you join its waitlist.</p>
        </div>
        <label style={{ minWidth: 240 }}>Search
          <input type="search" placeholder="Title, club or room" value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
      </div>
      {error && <p className="notice bad" role="alert" style={{ marginBottom: 16 }}>{error}</p>}
      {!loaded ? (
        <div className="tickets">{[0, 1].map((i) => <div key={i} className="panel"><div className="skeleton" style={{ width: '50%', marginBottom: 10 }} /><div className="skeleton" /></div>)}</div>
      ) : shown.length === 0 ? (
        <div className="empty"><p>{q ? 'No events match that search.' : 'No events are open yet. Approved club events appear here.'}</p></div>
      ) : byDay(shown, (e) => e.start).map((g) => (
        <section key={g.key}>
          <h2 className="date-group">{dayLabel(g.date)}</h2>
          <div className="tickets">
            {g.items.map((e) => {
              const s = seats[e.id]
              return (
                <Ticket key={e.id} start={e.start} end={e.end} title={e.title} description={e.description}
                  club={clubs[e.clubId]?.name} venue={rooms[e.resourceId]?.name}
                  seats={s && { capacity: s.capacity, confirmed: s.confirmed, waitlisted: s.waitlisted, left: Math.max(0, s.capacity - s.confirmed) }}
                  stub={stub(e)} />
              )
            })}
          </div>
        </section>
      ))}
    </div>
  )
}
