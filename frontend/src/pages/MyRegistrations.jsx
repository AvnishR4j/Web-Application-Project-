import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import { STATUS_LABEL, describeError, fmt } from './eventUtils'

const REG_LABEL = { CONFIRMED: 'Registered', WAITLISTED: 'Waitlisted', CANCELLED: 'Cancelled' }
const REG_BADGE = { CONFIRMED: 'APPROVED', WAITLISTED: 'PENDING', CANCELLED: 'CANCELLED' }

export default function MyRegistrations() {
  const [items, setItems] = useState([])
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api('/api/registrations/mine').then(setItems).catch((e) => setError(e.message))
  }, [])
  useEffect(load, [load])

  async function cancel(eventId) {
    setError('')
    try { await api(`/api/events/${eventId}/unregister`, { method: 'POST' }); load() }
    catch (err) { setError(describeError(err)) }
  }

  return (
    <div className="card">
      <h1>My registrations</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {items.length === 0 ? <p className="muted">You have not registered for any events yet.</p> : (
        <table>
          <thead><tr><th>Event</th><th>When</th><th>My place</th><th /></tr></thead>
          <tbody>
            {items.map(({ registration: r, eventTitle, start, eventStatus, waitlistPosition }) => (
              <tr key={r.id}>
                <td>{eventTitle}
                  {eventStatus !== 'APPROVED' && <div className="muted">Event is {STATUS_LABEL[eventStatus]?.toLowerCase()}</div>}
                </td>
                <td>{fmt(start)}</td>
                <td>
                  <span className={`badge ${REG_BADGE[r.status]}`}>
                    {REG_LABEL[r.status]}{r.status === 'WAITLISTED' && ` #${waitlistPosition}`}
                  </span>
                  {r.promoted && r.status === 'CONFIRMED' && <div className="muted">Moved up from the waitlist</div>}
                </td>
                <td>
                  {r.status !== 'CANCELLED' && eventStatus === 'APPROVED' && new Date(start) > new Date() && (
                    <button className="secondary" onClick={() => cancel(r.eventId)}>
                      {r.status === 'CONFIRMED' ? 'Cancel' : 'Leave waitlist'}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
