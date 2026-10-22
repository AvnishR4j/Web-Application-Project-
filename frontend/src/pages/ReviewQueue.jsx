import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import EventHistory from './EventHistory'
import { byId, describeError, fmt } from './eventUtils'

// Shared by the advisor step and the space manager step: list, approve, or reject with a reason.
export default function ReviewQueue({ title, intro, queuePath, approveAction, rejectAction, empty }) {
  const [events, setEvents] = useState([])
  const [clubs, setClubs] = useState({})
  const [rooms, setRooms] = useState({})
  const [rejecting, setRejecting] = useState(null)
  const [reason, setReason] = useState('')
  const [historyFor, setHistoryFor] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    Promise.all([api(queuePath), api('/api/clubs'), api('/api/resources?includeInactive=true')])
      .then(([e, c, r]) => { setEvents(e); setClubs(byId(c)); setRooms(byId(r)) })
      .catch((err) => setError(err.message))
  }, [queuePath])
  useEffect(load, [load])

  async function act(id, action, body) {
    setError('')
    try {
      await api(`/api/events/${id}/${action}`, { method: 'POST', body })
      setRejecting(null); setReason(''); load()
    } catch (err) { setError(describeError(err)); load() }
  }

  return (
    <div className="card">
      <h1>{title}</h1>
      <p className="muted">{intro}</p>
      {error && <p className="error" role="alert">{error}</p>}
      {events.length === 0 ? <p className="muted">{empty}</p> : (
        <table>
          <thead><tr><th>Event</th><th>Club</th><th>When</th><th>Venue</th><th>People</th><th /></tr></thead>
          <tbody>
            {events.map((ev) => (
              <tr key={ev.id}>
                <td>{ev.title}{ev.description && <div className="muted">{ev.description}</div>}
                  <button className="link" onClick={() => setHistoryFor(historyFor === ev.id ? null : ev.id)}>History</button></td>
                <td>{clubs[ev.clubId]?.name}</td>
                <td>{fmt(ev.start)}<div className="muted">to {fmt(ev.end)}</div></td>
                <td>{rooms[ev.resourceId]?.name}</td>
                <td>{ev.expectedAttendees}</td>
                <td>
                  {rejecting === ev.id ? (
                    <div className="row">
                      <input placeholder="Reason" value={reason} onChange={(e) => setReason(e.target.value)} />
                      <button className="danger" disabled={!reason.trim()} onClick={() => act(ev.id, rejectAction, { reason })}>Reject</button>
                      <button className="link" onClick={() => setRejecting(null)}>Back</button>
                    </div>
                  ) : (
                    <div className="row">
                      <button onClick={() => act(ev.id, approveAction)}>Approve</button>
                      <button className="secondary" onClick={() => setRejecting(ev.id)}>Reject</button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {historyFor && <EventHistory eventId={historyFor} />}
    </div>
  )
}
