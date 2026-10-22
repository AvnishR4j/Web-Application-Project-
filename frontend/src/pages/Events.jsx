import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { byId, fmt } from './eventUtils'

// Approved upcoming events: everything a student sees.
export default function Events() {
  const [events, setEvents] = useState([])
  const [clubs, setClubs] = useState({})
  const [rooms, setRooms] = useState({})
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([api('/api/events'), api('/api/clubs'), api('/api/resources')])
      .then(([e, c, r]) => { setEvents(e); setClubs(byId(c)); setRooms(byId(r)) })
      .catch((err) => setError(err.message))
  }, [])

  return (
    <div className="card">
      <h1>Upcoming events</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {events.length === 0 ? <p className="muted">No approved events yet.</p> : (
        <div className="stack">
          {events.map((e) => (
            <article key={e.id} className="notice">
              <h2 style={{ margin: '0 0 4px' }}>{e.title}</h2>
              <p className="muted" style={{ margin: 0 }}>
                {clubs[e.clubId]?.name || 'Club'} · {fmt(e.start)} to {fmt(e.end)} · {rooms[e.resourceId]?.name || 'Venue'}
              </p>
              {e.description && <p style={{ marginBottom: 0 }}>{e.description}</p>}
            </article>
          ))}
        </div>
      )}
    </div>
  )
}
