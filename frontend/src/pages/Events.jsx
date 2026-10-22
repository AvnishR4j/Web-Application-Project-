import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import { byId, describeError, fmt } from './eventUtils'

// Approved upcoming events, with seats left and the viewer's own place (registered or waitlisted).
export default function Events() {
  const [events, setEvents] = useState([])
  const [seats, setSeats] = useState({})
  const [clubs, setClubs] = useState({})
  const [rooms, setRooms] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(null)

  const load = useCallback(() => {
    Promise.all([api('/api/events'), api('/api/events/seats'), api('/api/clubs'), api('/api/resources')])
      .then(([e, s, c, r]) => {
        setEvents(e)
        setSeats(Object.fromEntries(s.map((x) => [x.eventId, x])))
        setClubs(byId(c)); setRooms(byId(r))
      })
      .catch((err) => setError(err.message))
  }, [])
  useEffect(load, [load])

  async function act(id, action) {
    setError(''); setBusy(id)
    try { await api(`/api/events/${id}/${action}`, { method: 'POST' }); load() }
    catch (err) { setError(describeError(err)) }
    finally { setBusy(null) }
  }

  return (
    <div className="card">
      <h1>Upcoming events</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {events.length === 0 ? <p className="muted">No approved events yet.</p> : (
        <div className="stack">
          {events.map((e) => {
            const s = seats[e.id]
            const left = s ? Math.max(0, s.capacity - s.confirmed) : null
            return (
              <article key={e.id} className="notice">
                <h2 style={{ margin: '0 0 4px' }}>{e.title}</h2>
                <p className="muted" style={{ margin: 0 }}>
                  {clubs[e.clubId]?.name || 'Club'} · {fmt(e.start)} to {fmt(e.end)} · {rooms[e.resourceId]?.name || 'Venue'}
                </p>
                {e.description && <p style={{ marginBottom: 0 }}>{e.description}</p>}
                {s && (
                  <div className="row" style={{ marginTop: 12 }}>
                    <span className="muted">
                      {left > 0 ? `${left} of ${s.capacity} seats left` : `Full (${s.capacity} seats)`}
                      {s.waitlisted > 0 && `, ${s.waitlisted} on the waitlist`}
                    </span>
                    {s.myStatus === 'CONFIRMED' && <span className="badge APPROVED">You are registered</span>}
                    {s.myStatus === 'WAITLISTED' && <span className="badge PENDING">Waitlist #{s.myWaitlistPosition}</span>}
                    {(s.myStatus === 'CONFIRMED' || s.myStatus === 'WAITLISTED') ? (
                      <button className="secondary" disabled={busy === e.id} onClick={() => act(e.id, 'unregister')}>
                        {s.myStatus === 'CONFIRMED' ? 'Cancel registration' : 'Leave waitlist'}
                      </button>
                    ) : (
                      <button disabled={busy === e.id} onClick={() => act(e.id, 'register')}>
                        {left > 0 ? 'Register' : 'Join waitlist'}
                      </button>
                    )}
                  </div>
                )}
              </article>
            )
          })}
        </div>
      )}
    </div>
  )
}
