import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { STATUS_LABEL, fmt } from './eventUtils'

// The audit log for one event: who did what, when, and why.
export default function EventHistory({ eventId }) {
  const [rows, setRows] = useState([])
  const [error, setError] = useState('')
  useEffect(() => {
    api(`/api/events/${eventId}/history`).then(setRows).catch((e) => setError(e.message))
  }, [eventId])
  return (
    <div className="notice" style={{ marginBottom: 24 }}>
      <strong>History</strong>
      {error && <p className="error">{error}</p>}
      <ul>
        {rows.map((r) => (
          <li key={r.id}>
            {fmt(r.at)}: {r.action.replaceAll('_', ' ').toLowerCase()} by {r.actorEmail}
            {r.toStatus && <> (now {STATUS_LABEL[r.toStatus]})</>}
            {r.reason && <> - {r.reason}</>}
          </li>
        ))}
      </ul>
    </div>
  )
}
