import { useCallback, useEffect, useState } from 'react'
import { api, peek } from '../api/client'

const fmt = (iso) => new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })

export default function MyBookings() {
  const [items, setItems] = useState(() => peek('/api/bookings/mine') || [])
  const [names, setNames] = useState(() => Object.fromEntries((peek('/api/resources?includeInactive=true') || []).map((r) => [r.id, r.name])))
  const [error, setError] = useState('')
  const [note, setNote] = useState('')
  const [loaded, setLoaded] = useState(() => Boolean(peek('/api/bookings/mine')))

  const load = useCallback(() => {
    Promise.all([api('/api/bookings/mine'), api('/api/resources?includeInactive=true')])
      .then(([bookings, resources]) => {
        setItems(bookings)
        setNames(Object.fromEntries(resources.map((r) => [r.id, r.name])))
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoaded(true))
  }, [])

  useEffect(load, [load])

  async function cancel(id) {
    setError('')
    setNote('')
    try {
      const b = await api(`/api/bookings/${id}/cancel`, { method: 'PATCH' })
      if (b.lateCancellation) {
        setNote('Cancelled. This was less than 72 hours before the start, which counts as a late cancellation.')
      }
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="card">
      <h1>My booking requests</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {note && <p className="notice" role="status">{note}</p>}
      {!loaded ? <p className="muted">Loading...</p> : items.length === 0 ? <p className="muted">No requests yet.</p> : (
        <table>
          <thead>
            <tr><th>Resource</th><th>Purpose</th><th>From</th><th>To</th><th>Status</th><th /></tr>
          </thead>
          <tbody>
            {items.map((b) => (
              <tr key={b.id}>
                <td>{names[b.resourceId] || b.resourceId}</td>
                <td>{b.title}</td>
                <td>{fmt(b.start)}</td>
                <td>{fmt(b.end)}</td>
                <td>
                  <span className={`badge ${b.status}`}>{b.status}</span>
                  {b.decisionReason && <div className="muted">{b.decisionReason}</div>}
                </td>
                <td>
                  {b.eventId ? <span className="muted">Managed by its event</span> : (b.status === 'PENDING' || b.status === 'APPROVED') && new Date(b.start) > new Date() && (
                    <button className="secondary" onClick={() => cancel(b.id)}>Cancel</button>
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
