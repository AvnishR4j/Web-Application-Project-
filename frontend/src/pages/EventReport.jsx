import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { STATUS_LABEL, fmt } from './eventUtils'

// Registered versus attended, no-shows, and anonymous feedback for one event.
export default function EventReport() {
  const { id } = useParams()
  const [r, setR] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => { api(`/api/events/${id}/report`).then(setR).catch((e) => setError(e.message)) }, [id])

  if (error) return <div className="card"><p className="error">{error}</p></div>
  if (!r) return <div className="card"><p className="muted">Loading...</p></div>
  const maxCount = Math.max(1, ...r.ratings.map((x) => x.count))
  return (
    <div className="card">
      <h1>{r.title}</h1>
      <p className="muted">{STATUS_LABEL[r.status]} · {r.seats} seats</p>
      <div className="grid" style={{ marginBottom: 24 }}>
        <Stat label="Registered" value={r.registered} />
        <Stat label="Attended" value={r.attended} />
        <Stat label="No-shows" value={r.noShows} />
        <Stat label="Attendance" value={r.attendanceRate == null ? '-' : `${r.attendanceRate}%`} />
        <Stat label="Waitlisted" value={r.waitlisted} />
        <Stat label="Average rating" value={r.averageRating == null ? '-' : `${r.averageRating} / 5`} sub={`${r.feedbackCount} responses`} />
      </div>

      <h2>Ratings</h2>
      <table>
        <tbody>
          {[...r.ratings].reverse().map((x) => (
            <tr key={x.rating}>
              <td style={{ width: 70 }}>{x.rating} star{x.rating > 1 ? 's' : ''}</td>
              <td><div style={{ background: 'var(--ok)', height: 12, borderRadius: 3, width: `${(x.count / maxCount) * 100}%`, minWidth: x.count ? 4 : 0 }} /></td>
              <td style={{ width: 40, textAlign: 'right' }}>{x.count}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2>Comments</h2>
      <p className="muted">Shown without names.</p>
      {r.comments.length === 0 ? <p className="muted">No comments yet.</p> : (
        <ul>{r.comments.map((c, i) => <li key={i}><strong>{c.rating}/5</strong> {c.comment} <span className="muted">({fmt(c.at)})</span></li>)}</ul>
      )}

      <h2>Checked in</h2>
      {r.attendees.length === 0 ? <p className="muted">Nobody has checked in yet.</p> : (
        <ol>{r.attendees.map((a) => <li key={a.id}>{a.userEmail} <span className="muted">at {fmt(a.checkedInAt)}</span></li>)}</ol>
      )}
      <p><Link to="/my-events">Back to My events</Link></p>
    </div>
  )
}

function Stat({ label, value, sub }) {
  return (
    <div className="notice">
      <div className="muted">{label}</div>
      <div style={{ fontSize: '1.8rem', fontWeight: 600 }}>{value}</div>
      {sub && <div className="muted">{sub}</div>}
    </div>
  )
}
