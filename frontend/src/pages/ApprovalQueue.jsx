import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'

const fmt = (iso) => new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })

export default function ApprovalQueue() {
  const [items, setItems] = useState([])
  const [names, setNames] = useState({})
  const [rejecting, setRejecting] = useState(null) // booking id being rejected
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [loaded, setLoaded] = useState(false)

  const load = useCallback(() => {
    Promise.all([api('/api/bookings?status=PENDING'), api('/api/resources?includeInactive=true')])
      .then(([bookings, resources]) => {
        // Event venue bookings are approved through Event approvals, not here.
        setItems(bookings.filter((b) => !b.eventId))
        setNames(Object.fromEntries(resources.map((r) => [r.id, r.name])))
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoaded(true))
  }, [])

  useEffect(load, [load])

  async function act(id, action, body) {
    setError('')
    try {
      await api(`/api/bookings/${id}/${action}`, { method: 'PATCH', body })
      setRejecting(null)
      setReason('')
      load()
    } catch (err) {
      setError(err.message)
      load()
    }
  }

  return (
    <div className="card">
      <h1>Pending booking requests</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {!loaded ? <p className="muted">Loading...</p> : items.length === 0 ? <p className="muted">Nothing waiting for review.</p> : (
        <table>
          <thead>
            <tr><th>Resource</th><th>Requested by</th><th>Purpose</th><th>From</th><th>To</th><th>People</th><th /></tr>
          </thead>
          <tbody>
            {items.map((b) => (
              <tr key={b.id}>
                <td>{names[b.resourceId] || b.resourceId}</td>
                <td>{b.requesterEmail}</td>
                <td>{b.title}</td>
                <td>{fmt(b.start)}</td>
                <td>{fmt(b.end)}</td>
                <td>{b.attendees}</td>
                <td>
                  {rejecting === b.id ? (
                    <div className="row">
                      <input placeholder="Reason" value={reason} onChange={(e) => setReason(e.target.value)} />
                      <button className="danger" disabled={!reason.trim()}
                        onClick={() => act(b.id, 'reject', { reason })}>Reject</button>
                      <button className="link" onClick={() => setRejecting(null)}>Back</button>
                    </div>
                  ) : (
                    <div className="row">
                      <button onClick={() => act(b.id, 'approve')}>Approve</button>
                      <button className="secondary" onClick={() => setRejecting(b.id)}>Reject</button>
                    </div>
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
