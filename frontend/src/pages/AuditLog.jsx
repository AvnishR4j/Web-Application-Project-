import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { STATUS_LABEL, fmt } from './eventUtils'

// Every recorded change to every event, newest first.
export default function AuditLog() {
  const [rows, setRows] = useState(null)
  const [filter, setFilter] = useState('')
  const [error, setError] = useState('')
  useEffect(() => { api('/api/admin/audit?limit=300').then(setRows).catch((e) => setError(e.message)) }, [])

  const q = filter.trim().toLowerCase()
  const shown = (rows || []).filter(({ entry, eventTitle }) => !q
    || [eventTitle, entry.actorEmail, entry.action, entry.reason].some((v) => v && v.toLowerCase().includes(q)))

  return (
    <div className="card">
      <h1>Audit log</h1>
      <p className="muted">Who changed which event, when, and why. Newest first.</p>
      <label>Filter<input placeholder="event, person or action" value={filter} onChange={(e) => setFilter(e.target.value)} /></label>
      {error && <p className="error" role="alert">{error}</p>}
      {!rows ? <p className="muted">Loading...</p> : shown.length === 0 ? <p className="muted">Nothing found.</p> : (
        <table style={{ marginTop: 16 }}>
          <thead><tr><th>When</th><th>Event</th><th>What</th><th>By</th><th>Reason</th></tr></thead>
          <tbody>
            {shown.map(({ entry: a, eventTitle }) => (
              <tr key={a.id}>
                <td>{fmt(a.at)}</td>
                <td>{eventTitle}</td>
                <td>{a.action.replaceAll('_', ' ').toLowerCase()}{a.toStatus && <div className="muted">now {STATUS_LABEL[a.toStatus]}</div>}</td>
                <td>{a.actorEmail}</td>
                <td>{a.reason || ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
